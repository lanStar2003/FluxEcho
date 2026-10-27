package com.fluxecho.client.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fluxecho.Tools;
import com.fluxecho.logic.shape.Family;
import com.fluxecho.logic.skin.SkinPalette;

/**
 * The textures of the new material families (0.17.0, style FINAL §3.5, §3.6), as {@code tools/HullTextures.java} and
 * {@code tools/SkinTextures.java} draw them and the blocks register them ({@link SkinIcons}):
 * <ul>
 * <li>every sprite a block asks for is there, 32 pixels square, with binary alpha, and nothing else is in
 * {@code textures/blocks/skin/};</li>
 * <li>every glow is two layers: a core of white (lightness at least {@link SkinPalette#GLOW_CORE_MIN_LIGHTNESS}),
 * drawn untinted, and an edge of greys from {@link SkinPalette#GLOW_EDGE_INNER} next to the core to
 * {@link SkinPalette#GLOW_EDGE_OUTER}, never more than {@link SkinPalette#GLOW_EDGE_PX} pixels from it, which the
 * renderer tints with the accent;</li>
 * <li>a light seam's line is two pixels through the middle of the face from edge to edge (so it runs on), a step's
 * nose the top two rows of its face, a keel's strip two pixels up the middle;</li>
 * <li>the hull is never brighter than its base {@code #EDF1F3} (style FINAL §2 G4 table, §8.4 #7);</li>
 * <li>a pattern over several cells changes from cell to cell (or the span would be pointless);</li>
 * <li>(slow) the committed textures are what the generators draw, and the generators' light is the palette's.</li>
 * </ul>
 */
class GlowTextureTest {

    private static final Path SKIN = Paths.get("src/main/resources/assets/fluxecho/textures/blocks/skin");
    private static final int CELL = 32, HULL = 0xEDF1F3;
    private static final Set<String> NAMES = new TreeSet<>();

    /** Collects the sprite names the blocks register, as the texture atlas would get them. */
    @BeforeAll
    static void register() {
        IIconRegister r = new IIconRegister() {

            @Override
            public IIcon registerIcon(String name) {
                assertTrue(name.startsWith("fluxecho:skin/"), name);
                NAMES.add(name.substring("fluxecho:skin/".length()));
                return null;
            }
        };
        for (Family f : Family.values()) SkinIcons.register(r, f);
        SkinIcons.registerBed(r);
        SkinIcons.registerHullNumber(r);
    }

    private static BufferedImage read(String name) throws IOException {
        File f = SKIN.resolve(name + ".png")
            .toFile();
        assertTrue(f.isFile(), name + ".png is drawn");
        return ImageIO.read(f);
    }

    /** The opaque pixels of an image, as a mask; fails on an alpha other than 0 or 255. */
    private static boolean[][] mask(BufferedImage img, String name) {
        boolean[][] m = new boolean[img.getHeight()][img.getWidth()];
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) {
            int a = img.getRGB(x, y) >>> 24;
            assertTrue(a == 0 || a == 255, name + ": binary alpha at " + x + "," + y);
            m[y][x] = a == 255;
        }
        return m;
    }

    @Test
    void everySpriteIsDrawnAndNothingElse() throws IOException {
        assertTrue(NAMES.size() > 150, "the blocks register " + NAMES.size() + " sprites");
        for (String n : NAMES) {
            BufferedImage img = read(n);
            assertEquals(CELL, img.getWidth(), n);
            assertEquals(CELL, img.getHeight(), n);
            mask(img, n);
        }
        Set<String> committed = new TreeSet<>();
        try (Stream<Path> s = Files.walk(SKIN)) {
            for (Path p : s.filter(Files::isRegularFile)
                .collect(Collectors.toList())) {
                String rel = SKIN.relativize(p)
                    .toString()
                    .replace(File.separatorChar, '/');
                assertTrue(rel.endsWith(".png"), rel + " is a texture");
                committed.add(rel.substring(0, rel.length() - 4));
            }
        }
        committed.removeAll(NAMES);
        assertEquals(new TreeSet<String>(), committed, "textures no block asks for");
    }

    @Test
    void everyGlowIsAWhiteCoreAndAGreyEdge() throws IOException {
        int glows = 0;
        for (String n : NAMES) {
            if (!n.endsWith("_glow_core")) continue;
            String base = n.substring(0, n.length() - "_core".length());
            assertTrue(NAMES.contains(base + "_edge"), base + " has its edge");
            BufferedImage core = read(n), edge = read(base + "_edge");
            boolean[][] c = mask(core, n), e = mask(edge, base + "_edge");
            int coreN = 0, edgeN = 0;
            for (int y = 0; y < CELL; y++) for (int x = 0; x < CELL; x++) {
                if (c[y][x]) {
                    coreN++;
                    assertTrue(
                        SkinPalette.lightness(core.getRGB(x, y) & 0xFFFFFF) >= SkinPalette.GLOW_CORE_MIN_LIGHTNESS,
                        n + ": a white core at " + x + "," + y);
                    assertFalse(e[y][x], base + ": core and edge overlap at " + x + "," + y);
                }
                if (!e[y][x]) continue;
                edgeN++;
                int rgb = edge.getRGB(x, y) & 0xFFFFFF, g = rgb & 255;
                assertTrue(
                    (rgb >> 16 & 255) == g && (rgb >> 8 & 255) == g,
                    base + ": the edge is grey (the accent tints it) at " + x + "," + y);
                assertTrue(
                    g <= (SkinPalette.GLOW_EDGE_INNER & 255) && g >= (SkinPalette.GLOW_EDGE_OUTER & 255),
                    base + ": on the edge's ramp at " + x + "," + y);
                int d = distance(c, x, y);
                assertTrue(d >= 1 && d <= SkinPalette.GLOW_EDGE_PX, base + ": edge within reach of the core");
                assertEquals(
                    SkinPalette.glowEdgeGrey(d - 1),
                    rgb,
                    base + ": the ramp's grey " + d + " out at " + x + "," + y);
            }
            assertTrue(coreN > 0 && edgeN > 0, base + " has both layers");
            glows++;
        }
        // hull 5, footing 3, each skin material 2, the bed's top and side
        assertEquals(5 + 3 + 4 * 2 + 2, glows);
    }

    /** The nearest core pixel by the larger of the two offsets, up to three away (4 when there is none so near). */
    private static int distance(boolean[][] core, int x, int y) {
        int best = 4;
        for (int dy = -3; dy <= 3; dy++) for (int dx = -3; dx <= 3; dx++) {
            int nx = x + dx, ny = y + dy;
            if (nx < 0 || ny < 0 || nx >= CELL || ny >= CELL || !core[ny][nx]) continue;
            best = Math.min(best, Math.max(Math.abs(dx), Math.abs(dy)));
        }
        return best;
    }

    @Test
    void linesNosesAndKeels() throws IOException {
        for (String mat : new String[] { "hull", "footing", "moss", "marble", "ink", "paper" }) {
            boolean[][] line = mask(read(mat + "/light_seam_glow_core"), mat);
            for (int y = 0; y < CELL; y++) for (int x = 0; x < CELL; x++)
                assertEquals(y == 15 || y == 16, line[y][x], mat + "'s line at " + x + "," + y);
        }
        for (String mat : new String[] { "hull", "footing" }) {
            boolean[][] nose = mask(read(mat + "/nose_glow_core"), mat);
            for (int y = 0; y < CELL; y++) for (int x = 0; x < CELL; x++)
                assertEquals(y == 16 || y == 17, nose[y][x], mat + "'s nose: the step face's top rows");
            boolean[][] edge = mask(read(mat + "/nose_glow_edge"), mat);
            for (int x = 0; x < CELL; x++) assertFalse(edge[15][x], "nothing over the step's face");
        }
        boolean[][] keel = mask(read("hull/keel_glow_core"), "keel");
        for (int y = 0; y < CELL; y++) for (int x = 0; x < CELL; x++)
            assertEquals(x == 15 || x == 16, keel[y][x], "the keel's strip at " + x + "," + y);
        boolean[][] tip = mask(read("hull/keel_tip_glow_core"), "keel tip");
        assertFalse(tip[0][15], "the tip ends below the cell's top");
        assertTrue(tip[CELL - 1][15], "and runs on down into the keel");
    }

    @Test
    void theHullIsNeverBrighterThanItsBase() throws IOException {
        double max = SkinPalette.lightness(HULL), brightest = 0;
        for (String n : NAMES) {
            if (!n.startsWith("hull/") || n.contains("_glow_")) continue;
            BufferedImage img = read(n);
            for (int y = 0; y < CELL; y++) for (int x = 0; x < CELL; x++) {
                int p = img.getRGB(x, y);
                if (p >>> 24 == 0) continue;
                double l = SkinPalette.lightness(p & 0xFFFFFF);
                brightest = Math.max(brightest, l);
                assertTrue(l <= max + 1e-9, n + " at " + x + "," + y + " is brighter than #EDF1F3");
            }
        }
        assertEquals(max, brightest, 1e-9, "the base itself is there");
    }

    @Test
    void aPatternChangesFromCellToCell() throws IOException {
        int patterns = 0;
        for (String n : NAMES) {
            if (!n.endsWith("_0_0")) continue;
            String base = n.substring(0, n.length() - 4);
            BufferedImage a = read(n), b = read(base + "_1_0"), c = read(base + "_0_1");
            assertFalse(same(a, b) && same(a, c), base + " repeats every cell");
            patterns++;
        }
        assertTrue(patterns >= 15, patterns + " patterns over several cells");
    }

    private static boolean same(BufferedImage a, BufferedImage b) {
        for (int y = 0; y < CELL; y++)
            for (int x = 0; x < CELL; x++) if (a.getRGB(x, y) != b.getRGB(x, y)) return false;
        return true;
    }

    /** The committed textures are what the generators draw (both run into a scratch folder). Slow: it compiles them. */
    @Test
    @Tag("slow")
    void theCommittedTexturesAreTheGenerators(@TempDir Path out) throws IOException {
        for (String tool : new String[] { "HullTextures", "SkinTextures" }) {
            Path dir = out.resolve(tool);
            Tools.call(tool, "main", new Class<?>[] { String[].class }, (Object) new String[] { dir + File.separator });
            List<String> differ = new ArrayList<>();
            int n = 0;
            try (Stream<Path> s = Files.walk(dir)) {
                for (Path p : s.filter(Files::isRegularFile)
                    .collect(Collectors.toList())) {
                    n++;
                    String rel = dir.relativize(p)
                        .toString()
                        .replace(File.separatorChar, '/');
                    Path committed = Paths.get("src/main/resources/assets/fluxecho/textures")
                        .resolve(rel);
                    if (!Files.isRegularFile(committed)) differ.add(rel + " is not committed");
                    else if (!samePixels(ImageIO.read(p.toFile()), ImageIO.read(committed.toFile()))) differ.add(rel);
                }
            }
            assertTrue(n > 40, tool + " drew " + n);
            assertEquals(new ArrayList<String>(), differ, tool + ": committed textures that differ from what it draws");
        }
    }

    /** The generators' light is the palette's: the white core, the keel's core, the edge's ramp and widths. */
    @Test
    @Tag("slow")
    void theGeneratorsLightIsThePalettes() throws ReflectiveOperationException {
        for (String tool : new String[] { "HullTextures", "SkinTextures" }) {
            assertEquals(SkinPalette.GLOW_CORE, constant(tool, "GLOW_CORE"), tool);
            assertEquals(SkinPalette.GLOW_EDGE_INNER, constant(tool, "EDGE_INNER"), tool);
            assertEquals(SkinPalette.GLOW_EDGE_OUTER, constant(tool, "EDGE_OUTER"), tool);
            assertEquals(SkinPalette.GLOW_CORE_PX, constant(tool, "CORE_PX"), tool);
            assertEquals(SkinPalette.GLOW_EDGE_PX, constant(tool, "EDGE_PX"), tool);
            assertEquals(CELL, constant(tool, "CELL"), tool);
        }
        assertEquals(SkinPalette.KEEL_CORE, constant("HullTextures", "KEEL_CORE"));
        // style FINAL §3.5's base colours
        assertEquals(HULL, constant("HullTextures", "HULL"), "M1");
        assertEquals(0x36312D, constant("HullTextures", "FOOTING"), "M2");
        assertEquals(0x4E8C7B, constant("SkinTextures", "VERDIGRIS"), "M3");
        assertEquals(0xEFEAE1, constant("SkinTextures", "MARBLE"), "M4");
        assertEquals(0x16181D, constant("SkinTextures", "INK"), "M5");
        assertEquals(0xF1E8D8, constant("SkinTextures", "PAPER"), "M6");
    }

    private static int constant(String tool, String name) throws ReflectiveOperationException {
        Field f = Tools.load(tool)
            .getDeclaredField(name);
        f.setAccessible(true);
        return f.getInt(null);
    }

    private static boolean samePixels(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;
        for (int y = 0; y < a.getHeight(); y++) for (int x = 0; x < a.getWidth(); x++) {
            int p = a.getRGB(x, y), q = b.getRGB(x, y);
            if (p >>> 24 == 0 && q >>> 24 == 0) continue;
            if (p != q) return false;
        }
        return true;
    }
}
