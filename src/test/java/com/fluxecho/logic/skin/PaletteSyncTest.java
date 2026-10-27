package com.fluxecho.logic.skin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.fluxecho.Tools;

/**
 * The seven-row recolouring as committed (0.17.0, style §7.1): every texture of the seven rows changed together, each
 * one's mean lightness in the band its row asks for (the frame and the fittings the hull's white, the floor the
 * silver-grey rock at L* about 63, the panels a pale grey, the plinth course, the grate and the basin rims the
 * footing's warm black, the canvas rice paper); every glow overlay of those rows draws a white core in every frame of
 * its animation and an edge in its light's colour, the nexus cyan or warm white (G6, style §3.6); and the two
 * generators draw their light in {@link SkinPalette}'s colours. The textures are only ever drawn by
 * {@code tools/NexusTextures.java} and {@code tools/CampusTextures.java} ({@code GeneratedTexturesTest} checks the
 * committed files are what they draw).
 */
class PaletteSyncTest {

    private static final String BLOCKS = "src/main/resources/assets/fluxecho/textures/blocks/";

    /** The mean L* of a texture's opaque pixels in its first frame. */
    private static double meanLightness(String path) throws IOException {
        BufferedImage img = read(path);
        double sum = 0;
        int n = 0;
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int p = img.getRGB(x, y);
            if (p >>> 24 == 0) continue;
            sum += 100 * SkinPalette.lightness(p & 0xFFFFFF);
            n++;
        }
        assertTrue(n > 0, path + " is empty");
        return sum / n;
    }

    private static BufferedImage read(String path) throws IOException {
        File f = new File(BLOCKS + path + ".png");
        assertTrue(f.isFile(), "missing " + f);
        return ImageIO.read(f);
    }

    /** Each row's textures and the band of mean L* each must fall in. */
    @Test
    void theSevenRowsChangedColour() throws IOException {
        Map<String, double[]> want = new LinkedHashMap<>();
        double[] hull = { 80, 100 }, footing = { 10, 30 };
        // row 1: frame 0-6 and 8-10, the hull's white (the console's top keeps its dark glass, the seat its recess)
        for (String p : new String[] { "base_top", "base_side", "base_lit_top", "base_lit_side", "pillar_top",
            "pillar_side", "conduit_top", "conduit_side", "ring_top", "ring_side", "seat_side", "foundation_top",
            "foundation_side", "console_side", "anchor_top", "anchor_side", "float_top", "float_side", "float_bottom" })
            want.put("frame/" + p, hull);
        want.put("frame/console_top", new double[] { 65, 80 });
        want.put("frame/seat_top", new double[] { 70, 85 });
        // row 2: the shelf's frame white, its books dark as they were
        want.put("frame/shelf_top", hull);
        want.put("frame/shelf_board", hull);
        want.put("frame/shelf_side", new double[] { 30, 50 });
        // row 3: the floor between the footing and the hull (L* about 63), the trim a shade lighter, the footing
        want.put("deck/deck", new double[] { 60, 66 });
        want.put("deck/trim", new double[] { 67, 75 });
        want.put("deck/grate", footing);
        want.put("deck/dark", footing);
        // row 4: the flux channel in the rock, the light well's footing bed
        want.put("deck/lit", new double[] { 55, 64 });
        want.put("deck/lit_side", new double[] { 55, 64 });
        want.put("deck/well", footing);
        // row 5: the skirt's rock face, the chevron in the trim
        want.put("deck/skirt", new double[] { 52, 64 });
        want.put("deck/chevron", new double[] { 60, 72 });
        // row 6: the pale panels, the plinth course in the footing, the cornice white
        want.put("deck/panel", new double[] { 83, 89 });
        want.put("deck/panel_lit", new double[] { 80, 88 });
        want.put("deck/panel_dark", footing);
        want.put("deck/cornice", hull);
        // row 7: the canvas rice paper, every fitting the hull's white but the basin rim's footing
        want.put("deck/canvas", new double[] { 80, 95 });
        for (String p : new String[] { "post", "plinth", "crown", "rail", "tread", "glaze", "pedestal", "socket",
            "lift" }) {
            want.put("fitting/" + p, hull);
            want.put("fitting/" + p + "_top", hull);
        }
        want.put("fitting/basin", footing);
        want.put("fitting/basin_top", footing);
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, double[]> e : want.entrySet()) {
            double l = meanLightness(e.getKey());
            if (l < e.getValue()[0] || l > e.getValue()[1]) wrong
                .add(String.format("%s %.1f not in [%.0f, %.0f]", e.getKey(), l, e.getValue()[0], e.getValue()[1]));
        }
        assertTrue(wrong.isEmpty(), String.valueOf(wrong));
        assertEquals(58, want.size(), "the seven rows' textures");
    }

    /** Every glow overlay of the seven rows, and the colour of its edge. */
    private static Map<String, Integer> glows() {
        Map<String, Integer> g = new LinkedHashMap<>();
        int cyan = SkinPalette.NEXUS_CYAN, warm = SkinPalette.WARM_WHITE;
        for (String p : new String[] { "base_lit_top_glow", "ring_side_glow", "foundation_top_glow", "console_top_glow",
            "console_side_glow", "anchor_top_glow", "anchor_side_glow", "float_bottom_glow", "float_side_glow" })
            g.put("frame/" + p, cyan);
        g.put("deck/lit_glow", cyan);
        g.put("deck/lit_side_glow", cyan);
        g.put("deck/well_glow", cyan);
        for (String p : new String[] { "skirt_glow", "chevron_glow", "panel_lit_glow", "panel_dark_glow",
            "cornice_glow" }) g.put("deck/" + p, warm);
        g.put("fitting/lift_glow", cyan);
        for (String p : new String[] { "post", "plinth", "crown", "rail", "tread", "pedestal", "socket", "basin" })
            g.put("fitting/" + p + "_glow", warm);
        return g;
    }

    /** Whether a pixel is a glow's core: as light as the palette's core must be, and white, not tinted. */
    private static boolean core(int rgb) {
        double[] lab = SkinPalette.lab(rgb);
        return lab[0] / 100 >= SkinPalette.GLOW_CORE_MIN_LIGHTNESS && Math.hypot(lab[1], lab[2]) <= 4;
    }

    /**
     * G6 and style §3.6: in every frame of every glow of the seven rows a white core shows, and the rest of the glow
     * (its edge, lifted towards white as the swell passes) is on average its light's colour: the nexus cyan on the
     * frame, the flux channels and the lift, warm white on everything at a person's scale.
     */
    @Test
    void everyGlowHasAWhiteCoreAndAnEdgeOfItsLight() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, Integer> e : glows().entrySet()) {
            BufferedImage img = read(e.getKey());
            int frames = img.getHeight() / 16;
            double r = 0, g = 0, b = 0;
            int edge = 0;
            for (int f = 0; f < frames; f++) {
                int cores = 0;
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                    int p = img.getRGB(x, f * 16 + y);
                    if (p >>> 24 == 0) continue;
                    if (core(p & 0xFFFFFF)) cores++;
                    else {
                        r += p >> 16 & 255;
                        g += p >> 8 & 255;
                        b += p & 255;
                        edge++;
                    }
                }
                if (cores == 0) wrong.add(e.getKey() + ": no white core in frame " + f);
            }
            if (edge == 0) {
                wrong.add(e.getKey() + ": no edge");
                continue;
            }
            int mean = (int) Math.round(r / edge) << 16 | (int) Math.round(g / edge) << 8 | (int) Math.round(b / edge);
            double d = SkinPalette.deltaE00(mean, e.getValue());
            if (d > 10)
                wrong.add(String.format("%s: its edge #%06X is %.1f from #%06X", e.getKey(), mean, d, e.getValue()));
        }
        assertTrue(wrong.isEmpty(), String.valueOf(wrong));
        assertEquals(26, glows().size());
    }

    /** The generators draw their light in the palette's colours (they run alone, so they hold copies). */
    @Test
    void theGeneratorsLightIsThePalettes() throws ReflectiveOperationException {
        assertEquals(SkinPalette.GLOW_CORE, constant("CampusTextures", "GLOW_CORE"));
        assertEquals(SkinPalette.NEXUS_CYAN, constant("CampusTextures", "NEXUS_CYAN"));
        assertEquals(SkinPalette.WARM_WHITE, constant("CampusTextures", "WARM_WHITE"));
        assertEquals(SkinPalette.GLOW_CORE, constant("NexusTextures", "GLOW_CORE"));
        assertEquals(SkinPalette.NEXUS_CYAN, constant("NexusTextures", "CYAN"));
        // the hull and the footing, the same in both
        for (String c : new String[] { "HULL", "HULL_SEAM", "HULL_RIVET", "HULL_LIT", "HULL_SHADE", "HULL_DEEP" })
            assertEquals(constant("CampusTextures", c), constant("NexusTextures", c), c);
        assertEquals(0xEDF1F3, constant("NexusTextures", "HULL"), "style §3.5 M1");
        assertEquals(0xC3CCD3, constant("NexusTextures", "HULL_SEAM"), "style §3.5 M1");
        assertEquals(0x36312D, constant("CampusTextures", "FOOTING"), "style §3.5 M2");
        assertEquals(0x9C9892, constant("CampusTextures", "ROCK"), "style §7.1 row 3");
        assertEquals(0xB3AFA8, constant("CampusTextures", "ROCK_TRIM"), "style §7.1 row 3");
        assertEquals(0xDAD9D4, constant("CampusTextures", "PANEL_PALE"), "style §7.1 row 6");
    }

    private static int constant(String tool, String name) throws ReflectiveOperationException {
        Field f = Tools.load(tool)
            .getDeclaredField(name);
        f.setAccessible(true);
        return f.getInt(null);
    }
}
