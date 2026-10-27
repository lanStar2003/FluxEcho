package com.fluxecho.logic.skin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fluxecho.logic.Parts;

/**
 * The gate on "the old town's windows light one by one at dusk" (refactor §5.6): for every shape of 0.16.0, the glowing
 * area of its formed building by day may be at most 3% of its outer surface (G6's accent budget; 0.16's always-lit parts
 * count) and at night, with its lamp windows lit, at most 12%. A shape over 3% by day takes its always-lit parts into
 * its mask, largest first, until it is not ({@link LegacyMaskGen#EXTRA}); the masks the runtime makes are the
 * generator's, cell for cell, and their checksums are pinned here.
 * <p>
 * How it is measured, cell by cell over the blueprint (the formed building: cells that give way to its drawing are not
 * there): a face of a part is outer surface when what lies beyond it is no opaque whole block of the building (air, a
 * space, must-be-air, water, glass, a slim part, a cell given way, the outside of the box); its area is the face of the
 * part's own box (a whole block 1, a post 12/16 wide, a tread half high...). A face glows where the part's renderer
 * draws a glow overlay on it ({@code DeckRender}, {@code FittingRender}, {@code FrameRender}, with 0.16's rules: the lit
 * strip not underneath, a fitting's line round its sides, the lift's iris on top, the frame's tops and sides as its
 * icons have them, the conduit's core and the seat's crystal whole), and its glowing area is the face's area times the
 * share of lit pixels in the overlay's texture (all frames of its animation). World-drawn light (③, the module's own
 * render) is not a block and has its own budget.
 */
@Tag("slow")
class LegacyGlowShareTest {

    static final double DAY = 0.03, NIGHT = 0.12;

    /**
     * The masks of the old shapes as the 0.16.0 code gives them: shape, cells, CRC-32 of the cells
     * ({@link LegacyMaskGen#crc}).
     */
    static final String[][] MASKS = {};

    // ---- the masks

    @Test
    void theOldShapesAreTheLegacyProfilesShapes() {
        Set<String> ids = new TreeSet<>();
        for (OldShapes.Shape s : OldShapes.all()) {
            ids.add(s.id());
            assertSame(SkinProfiles.LEGACY, SkinProfiles.profile(s.key, s.name), s.id());
        }
        assertEquals(35, ids.size(), "the 35 shapes of 0.16.0");
        assertEquals(ids, new TreeSet<>(SkinProfiles.OLD_SHAPES));
    }

    @Test
    void theRuntimeMasksAreTheGenerators() {
        StringBuilder table = new StringBuilder();
        List<String> wrong = new ArrayList<>();
        Map<String, String[]> pinned = new HashMap<>();
        for (String[] m : MASKS) pinned.put(m[0], m);
        for (OldShapes.Shape s : OldShapes.all()) {
            List<int[]> gen = LegacyMaskGen.cells(s);
            SkinProfiles.Mask run = SkinProfiles.legacyMask(s.key, s.name, s.bp);
            if (run == null) {
                wrong.add(s.id() + ": no runtime mask");
                continue;
            }
            List<int[]> got = new ArrayList<>();
            for (int i = 0; i < run.size(); i++) got.add(run.cell(i));
            if (!same(gen, got)) wrong.add(s.id() + ": the runtime mask has other cells");
            long crc = LegacyMaskGen.crc(gen);
            assertEquals(crc, run.crc(), s.id());
            table.append(String.format("{ \"%s\", \"%d\", \"%x\" }, ", s.id(), gen.size(), crc));
            String[] p = pinned.get(s.id());
            if (p == null) wrong.add(s.id() + ": not pinned");
            else if (Integer.parseInt(p[1]) != gen.size() || Long.parseLong(p[2], 16) != crc)
                wrong.add(s.id() + ": was " + p[1] + " cells " + p[2] + ", is " + gen.size() + " " + Long.toHexString(crc));
            // the box the client marks for a rebuild holds every lamp cell
            if (!gen.isEmpty()) {
                int[] b = run.bounds();
                for (int[] c : gen) for (int i = 0; i < 3; i++)
                    assertTrue(c[i] >= b[i] && c[i] <= b[i + 3], s.id() + " lamp box");
            }
        }
        System.out.println("LEGACY_MASKS: " + table);
        if (!wrong.isEmpty()) fail(String.join("\n", wrong));
    }

    private static boolean same(List<int[]> a, List<int[]> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!java.util.Arrays.equals(a.get(i), b.get(i))) return false;
        return true;
    }

    /** The parts taken in are the fewest that do: derived again here, largest day glow first. */
    @Test
    void theTakenInPartsAreTheLeastThatBringEveryShapeUnderThreePercent() {
        Map<String, List<OldShapes.Shape>> byKey = new LinkedHashMap<>();
        for (OldShapes.Shape s : OldShapes.all()) byKey.computeIfAbsent(s.key, k -> new ArrayList<>())
            .add(s);
        Map<String, Set<Integer>> derived = new TreeMap<>();
        for (Map.Entry<String, List<OldShapes.Shape>> e : byKey.entrySet()) {
            Set<Integer> taken = new TreeSet<>();
            while (true) {
                Map<Integer, Double> left = new TreeMap<>();
                for (OldShapes.Shape s : e.getValue()) {
                    Share sh = share(s, p -> LegacyMaskGen.LAMPS.contains(p) || taken.contains(p));
                    if (sh.day / sh.area <= DAY) continue;
                    for (Map.Entry<Integer, Double> g : sh.dayByPart.entrySet()) left.merge(g.getKey(), g.getValue(), Double::sum);
                }
                if (left.isEmpty()) break;
                int most = -1;
                double max = -1;
                for (Map.Entry<Integer, Double> g : left.entrySet()) if (g.getValue() > max) {
                    max = g.getValue();
                    most = g.getKey();
                }
                taken.add(most);
            }
            if (!taken.isEmpty()) derived.put(e.getKey(), taken);
        }
        Map<String, Set<Integer>> table = new TreeMap<>();
        for (Map.Entry<String, int[]> e : LegacyMaskGen.EXTRA.entrySet()) {
            Set<Integer> s = new TreeSet<>();
            for (int p : e.getValue()) s.add(p);
            table.put(e.getKey(), s);
        }
        assertEquals(derived, table, "the generator's taken-in parts");
        Map<String, Set<Integer>> runtime = new TreeMap<>();
        for (String key : SkinKeys.keys()) {
            Set<Integer> s = new TreeSet<>();
            for (int p = 0; p < 256; p++)
                if (SkinProfiles.isLamp(key, p) && !LegacyMaskGen.LAMPS.contains(p)) s.add(p);
            if (!s.isEmpty()) runtime.put(key, s);
        }
        assertEquals(derived, runtime, "the runtime's taken-in parts");
        for (int p = 0; p < 256; p++)
            assertEquals(LegacyMaskGen.LAMPS.contains(p), SkinProfiles.isLamp("eco_dome", p), "lamp part " + p);
    }

    // ---- the shares

    @Test
    void everyOldShapeGlowsAtMostThreePercentByDayAndTwelveAtNight() {
        List<String> over = new ArrayList<>();
        for (OldShapes.Shape s : OldShapes.all()) {
            Share sh = share(s, p -> SkinProfiles.isLamp(s.key, p));
            double day = sh.day / sh.area, night = sh.night / sh.area;
            System.out.printf(
                "%-28s outer %8.1f  day %5.2f%%  night %5.2f%%  lamp cells %d%n",
                s.id(),
                sh.area,
                100 * day,
                100 * night,
                sh.lampCells);
            if (day > DAY) over.add(String.format("%s by day %.2f%%", s.id(), 100 * day));
            if (night > NIGHT) over.add(String.format("%s at night %.2f%%", s.id(), 100 * night));
            assertTrue(sh.lampCells > 0 || s.key.equals("library"), s.id() + " has lamps to light");
        }
        if (!over.isEmpty()) fail("over G6's budget:\n  " + String.join("\n  ", over));
    }

    /** Without the parts taken in, the shapes that needed them really were over (so the table is no wider). */
    @Test
    void withoutTheTakenInPartsTheyWereOver() {
        for (OldShapes.Shape s : OldShapes.all()) {
            if (!LegacyMaskGen.EXTRA.containsKey(s.key)) continue;
            Share base = share(s, LegacyMaskGen.LAMPS::contains);
            boolean anyOver = false;
            for (OldShapes.Shape o : OldShapes.all())
                if (o.key.equals(s.key)) anyOver |= share(o, LegacyMaskGen.LAMPS::contains).day
                    / share(o, LegacyMaskGen.LAMPS::contains).area > DAY;
            assertTrue(anyOver, s.key + " needs no parts taken in");
            assertTrue(base.day >= share(s, p -> SkinProfiles.isLamp(s.key, p)).day);
        }
    }

    // ---- the model

    /** The glow of one shape: outer area, glowing area by day and at night, by day per part. */
    static final class Share {

        double area, day, night;
        int lampCells;
        final Map<Integer, Double> dayByPart = new TreeMap<>();
    }

    interface Lamp {

        boolean test(int part);
    }

    static final int[][] OFFSET = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 }, { 1, 0, 0 } };

    static Share share(OldShapes.Shape s, Lamp lamp) {
        Share out = new Share();
        int w = s.bp.width(), h = s.bp.height(), d = s.bp.depth();
        for (int a = 0; a < w; a++) for (int b = 0; b < h; b++) for (int c = 0; c < d; c++) {
            int p = s.part[a][b][c];
            if (!solid(p) || s.hidden[a][b][c]) continue;
            boolean isLamp = lamp.test(p);
            if (isLamp) out.lampCells++;
            for (int side = 0; side < 6; side++) {
                // the blueprint counts its layers from the top: up is one layer less
                int na = a + OFFSET[side][0], nb = b - OFFSET[side][1], nc = c + OFFSET[side][2];
                boolean outer = na < 0 || nb < 0 || nc < 0 || na >= w || nb >= h || nc >= d || !solid(s.part[na][nb][nc])
                    || s.hidden[na][nb][nc] || !opaque(s.part[na][nb][nc]);
                if (!outer) continue;
                out.area += area(p, side);
                double g = glow(p, side);
                if (g <= 0) continue;
                out.night += g;
                if (!isLamp) {
                    out.day += g;
                    out.dayByPart.merge(p, g, Double::sum);
                }
            }
        }
        return out;
    }

    static boolean solid(int p) {
        return p == OldShapes.CORE || p >= 0 && p != Parts.AIR && p != Parts.WATER;
    }

    /** Whole opaque blocks: they hide a neighbour's face. */
    static boolean opaque(int p) {
        if (p == OldShapes.CORE || p == Parts.GRASS || p == Parts.DIRT || Parts.isDeck(p)) return true;
        if (Parts.isFrame(p)) {
            int m = p - Parts.FRAME;
            return m == Parts.FR_BASE || m == Parts.FR_BASE_LIT
                || m == Parts.FR_FOUNDATION
                || m == Parts.FR_SHELF
                || m == Parts.FR_ANCHOR
                || m == Parts.FR_FLOAT;
        }
        if (Parts.isFitting(p)) {
            int m = p - Parts.FITTING;
            return m == Parts.F_PLINTH || m == Parts.F_CROWN || m == Parts.F_LIFT;
        }
        return p >= 48;
    }

    /** The area of a part's face on a side: its own box's (BlockFrame.box, FittingRender's shapes). */
    static double area(int p, int side) {
        boolean flat = side < 2;
        if (opaque(p)) return 1;
        if (Parts.isFrame(p)) switch (p - Parts.FRAME) {
            case Parts.FR_PILLAR:
                return flat ? 0.375 * 0.375 : 0.375;
            case Parts.FR_CONDUIT:
                return flat ? 0.25 : 0.5;
            case Parts.FR_RING:
                return flat ? 1 : 0.375;
            case Parts.FR_SEAT:
                return flat ? 1 : 0.5;
            case Parts.FR_CONSOLE:
                return flat ? 0.766 : 0.6;
            default:
                return 1;
        }
        if (Parts.isFitting(p)) switch (p - Parts.FITTING) {
            case Parts.F_POST:
            case Parts.F_SOCKET:
                return flat ? 0.5625 : 0.75;
            case Parts.F_RAIL:
                return flat ? 0.0625 : 1;
            case Parts.F_TREAD:
                return flat ? 1 : 0.5;
            case Parts.F_PEDESTAL:
                return flat ? 0.39 : 0.47;
            case Parts.F_BASIN:
                return flat ? 1 : 0.625;
            default:
                return 1;
        }
        return 1;
    }

    /** The glowing area of a part's face on a side: the overlay's face times its texture's lit share. */
    static double glow(int p, int side) {
        if (Parts.isDeck(p)) {
            int m = p - Parts.DECK;
            if (Parts.DECK_LIGHT[m] == 0) return 0;
            if (m == Parts.D_LIT) return side == 1 ? lit("deck/lit_glow") : side == 0 ? 0 : lit("deck/lit_side_glow");
            return lit("deck/" + Parts.DECK_NAMES[m] + "_glow");
        }
        if (Parts.isFitting(p)) {
            int m = p - Parts.FITTING;
            if (Parts.FITTING_LIGHT[m] == 0) return 0;
            double t = lit("fitting/" + Parts.FITTING_NAMES[m] + "_glow");
            switch (m) {
                case Parts.F_LIFT:
                    return side == 1 ? t : 0;
                case Parts.F_BASIN:
                    // the lip: 4/16 wide along the rim on top, 2/16 high on its sides
                    return side == 0 ? 0 : side == 1 ? 0.25 * t : 0.125 * t;
                case Parts.F_TREAD:
                    return side < 2 ? 0 : 0.5 * t;
                case Parts.F_POST:
                case Parts.F_SOCKET:
                    return side < 2 ? 0 : 0.75 * t;
                case Parts.F_PEDESTAL:
                    return side < 2 ? 0 : 0.47 * t;
                default:
                    return side < 2 ? 0 : t;
            }
        }
        if (Parts.isFrame(p)) switch (p - Parts.FRAME) {
            case Parts.FR_BASE_LIT:
                return side == 1 ? lit("frame/base_lit_top_glow") : 0;
            case Parts.FR_FOUNDATION:
                return side == 1 ? lit("frame/foundation_top_glow") : 0;
            case Parts.FR_RING:
                return side >= 2 ? 0.375 * lit("frame/ring_side_glow") : 0;
            case Parts.FR_SHELF:
                return side >= 2 ? lit("frame/shelf_side_glow") : 0;
            case Parts.FR_CONSOLE:
                return side == 1 ? lit("frame/console_top_glow") : side >= 2 ? 0.6 * lit("frame/console_side_glow") : 0;
            case Parts.FR_ANCHOR:
                return side == 1 ? lit("frame/anchor_top_glow") : side >= 2 ? lit("frame/anchor_side_glow") : 0;
            case Parts.FR_FLOAT:
                return side == 0 ? lit("frame/float_bottom_glow") : side >= 2 ? lit("frame/float_side_glow") : 0;
            case Parts.FR_CONDUIT:
                // the core, 3/16 across, glows whole
                return side >= 2 ? 3 / 16.0 : 0;
            case Parts.FR_SEAT:
                // the crystal, 6/16 across and 7/16 high, glows whole
                return side >= 2 ? 6 / 16.0 * 7 / 16.0 : 6 / 16.0 * 6 / 16.0;
            default:
                return 0;
        }
        return 0;
    }

    private static final Map<String, Double> LIT = new HashMap<>();

    /** The share of lit pixels in a texture, over all frames of its animation. */
    static synchronized double lit(String texture) {
        return LIT.computeIfAbsent(texture, t -> {
            try {
                BufferedImage im = ImageIO
                    .read(new File("src/main/resources/assets/fluxecho/textures/blocks/" + t + ".png"));
                int w = im.getWidth(), frames = im.getHeight() / w;
                long n = 0;
                for (int y = 0; y < im.getHeight(); y++)
                    for (int x = 0; x < w; x++) if (im.getRGB(x, y) >>> 24 != 0) n++;
                return n / (double) (w * w * frames);
            } catch (IOException e) {
                throw new IllegalStateException(t, e);
            }
        });
    }
}
