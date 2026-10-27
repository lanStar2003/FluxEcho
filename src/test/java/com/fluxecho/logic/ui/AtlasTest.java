package com.fluxecho.logic.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fluxecho.Tools;

/**
 * The UI atlas (ui/FINAL.md section 2.2): {@code tools/UiAtlas.java} draws {@code atlas@4x.png} (1024 x 1024) and
 * {@code atlas@2x.png} (512 x 512) and writes {@code AtlasMap.java}, the texel rectangles of every sprite and glyph.
 * This keeps the committed map and pictures the tool's output, every sprite inside its region and clear of the others,
 * and every glyph the formats use present.
 */
class AtlasTest {

    static final String DIR = "src/main/resources/assets/fluxecho/textures/gui/ui/";
    static final String MAP = "src/main/java/com/fluxecho/logic/ui/AtlasMap.java";

    static BufferedImage png(String name) throws IOException {
        return ImageIO.read(new File(DIR + name));
    }

    /** Slow: compiles the tool (about a second). */
    @Test
    @Tag("slow")
    void theMapIsTheToolsOutput() throws IOException {
        String expected = (String) Tools.call("UiAtlas", "source", new Class<?>[0]);
        String actual = new String(Files.readAllBytes(Paths.get(MAP)), StandardCharsets.UTF_8);
        assertEquals(expected, actual, "AtlasMap.java is stale: run java tools/UiAtlas.java");
    }

    /** The regions of 2.2's table, plus the one strip the small FluxMono set spills into (108 glyphs, not 100). */
    @Test
    void regionsAreTheDesignedOnes() {
        assertEquals(1024, AtlasMap.SIZE);
        assertArrayEquals(new int[] { 0, 0, 1024, 384 }, AtlasMap.region(AtlasMap.R_ICONS));
        assertArrayEquals(new int[] { 0, 384, 640, 32 }, AtlasMap.region(AtlasMap.R_MICRO));
        assertArrayEquals(new int[] { 0, 416, 512, 128 }, AtlasMap.region(AtlasMap.R_MONO_SMALL));
        assertArrayEquals(new int[] { 512, 416, 512, 288 }, AtlasMap.region(AtlasMap.R_MONO_LARGE));
        assertArrayEquals(new int[] { 0, 544, 256, 128 }, AtlasMap.region(AtlasMap.R_NINE));
        assertArrayEquals(new int[] { 256, 544, 256, 128 }, AtlasMap.region(AtlasMap.R_GLOW));
        assertArrayEquals(new int[] { 0, 672, 512, 32 }, AtlasMap.region(AtlasMap.R_MONO_SMALL_MORE));
        assertArrayEquals(new int[] { 0, 704, 1024, 128 }, AtlasMap.region(AtlasMap.R_TEXTURE));
        assertArrayEquals(new int[] { 0, 832, 1024, 192 }, AtlasMap.region(AtlasMap.R_RESERVE));
        List<int[]> regions = new ArrayList<>();
        for (int r = 0; r < AtlasMap.REGIONS; r++) {
            int[] a = AtlasMap.region(r);
            inside(a, "region " + r);
            for (int[] b : regions) assertFalse(overlap(a, b), "regions " + r + " overlap");
            regions.add(a);
        }
    }

    static void inside(int[] r, String what) {
        assertTrue(r[0] >= 0 && r[1] >= 0 && r[2] > 0 && r[3] > 0, what);
        assertTrue(r[0] + r[2] <= AtlasMap.SIZE && r[1] + r[3] <= AtlasMap.SIZE, what);
    }

    static boolean overlap(int[] a, int[] b) {
        return a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
    }

    static boolean within(int[] in, int[] r) {
        return in[0] >= r[0] && in[1] >= r[1] && in[0] + in[2] <= r[0] + r[2] && in[1] + in[3] <= r[1] + r[3];
    }

    static int[] rect(int id) {
        return new int[] { AtlasMap.x(id), AtlasMap.y(id), AtlasMap.w(id), AtlasMap.h(id) };
    }

    @Test
    void spritesStayInTheirRegionsAndApart() {
        List<int[]> seen = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int id = 0; id < AtlasMap.COUNT; id++) {
            int[] r = rect(id);
            inside(r, AtlasMap.name(id));
            boolean icon = id < AtlasMap.ICON_COUNT;
            boolean micro = !icon && id < AtlasMap.ICON_COUNT + AtlasMap.MICRO_COUNT;
            if (icon) {
                assertEquals(64, r[2]);
                assertEquals(64, r[3]);
                assertTrue(
                    within(r, AtlasMap.region(AtlasMap.R_ICONS)) || within(r, AtlasMap.region(AtlasMap.R_RESERVE)),
                    AtlasMap.name(id));
            } else if (micro) {
                assertEquals(32, r[2]);
                assertTrue(within(r, AtlasMap.region(AtlasMap.R_MICRO)), AtlasMap.name(id));
            } else {
                assertTrue(
                    within(r, AtlasMap.region(AtlasMap.R_NINE)) || within(r, AtlasMap.region(AtlasMap.R_GLOW))
                        || within(r, AtlasMap.region(AtlasMap.R_TEXTURE)),
                    AtlasMap.name(id));
            }
            for (int[] o : seen) assertFalse(overlap(r, o), AtlasMap.name(id) + " overlaps another sprite");
            seen.add(r);
            assertTrue(names.add(AtlasMap.name(id)), "two sprites called " + AtlasMap.name(id));
            assertTrue(AtlasMap.border(id) * 2 <= Math.min(r[2], r[3]), AtlasMap.name(id) + " nine-slice border");
        }
        // the first 96 icons fill the icon grid, the rest go to the reserve (the 97th icon, 2.2)
        assertTrue(within(rect(95), AtlasMap.region(AtlasMap.R_ICONS)));
        assertTrue(within(rect(96), AtlasMap.region(AtlasMap.R_RESERVE)));
    }

    /** 1.8: system and domain icons, the 23 building sigils, 20 micro icons. */
    @Test
    void iconSets() {
        assertEquals(37 + 43 + 23, AtlasMap.ICON_COUNT);
        assertEquals(20, AtlasMap.MICRO_COUNT);
        assertEquals("close", AtlasMap.name(AtlasMap.I_CLOSE));
        assertEquals("sigil.nexus", AtlasMap.name(AtlasMap.I_SIGIL_NEXUS));
        Set<Integer> sigils = new HashSet<>();
        for (Tok.Building b : Tok.buildings()) {
            assertTrue(sigils.add(b.sigil), "two buildings share a sigil: " + b.key + " " + b.form);
            assertTrue(
                AtlasMap.name(b.sigil)
                    .startsWith("sigil."),
                b.key);
        }
        assertEquals(23, sigils.size());
        assertNotEquals(Tok.building("nexus").sigil, Tok.building("drift_capsule").sigil);
    }

    @Test
    void glyphCellsAreInTheirRegions() {
        assertEquals(20, AtlasMap.SMALL_W);
        assertEquals(32, AtlasMap.SMALL_H);
        assertEquals(48, AtlasMap.LARGE_W);
        assertEquals(96, AtlasMap.LARGE_H);
        assertEquals(FluxMono.SMALL, AtlasMap.SMALL);
        assertEquals(FluxMono.LARGE, AtlasMap.LARGE);
        List<int[]> cells = new ArrayList<>();
        for (int i = 0; i < AtlasMap.SMALL.length(); i++) {
            int[] c = { AtlasMap.smallX(i), AtlasMap.smallY(i), AtlasMap.SMALL_W, AtlasMap.SMALL_H };
            assertTrue(
                within(c, AtlasMap.region(AtlasMap.R_MONO_SMALL))
                    || within(c, AtlasMap.region(AtlasMap.R_MONO_SMALL_MORE)),
                "small glyph " + i);
            for (int[] o : cells) assertFalse(overlap(c, o), "small glyph " + i);
            cells.add(c);
        }
        for (int i = 0; i < AtlasMap.LARGE.length(); i++) {
            int[] c = { AtlasMap.largeX(i), AtlasMap.largeY(i), AtlasMap.LARGE_W, AtlasMap.LARGE_H };
            assertTrue(within(c, AtlasMap.region(AtlasMap.R_MONO_LARGE)), "large glyph " + i);
            for (int[] o : cells) assertFalse(overlap(c, o), "large glyph " + i);
            cells.add(c);
        }
        assertEquals(0, FluxMono.smallIndex(' '));
        assertEquals('A' - 0x20, FluxMono.smallIndex('A'));
        assertEquals(95, FluxMono.smallIndex('·'));
        assertEquals(-1, FluxMono.smallIndex('中'));
        assertEquals(FluxMono.largeIndex('−'), FluxMono.largeIndex('-'));
        assertEquals(-1, FluxMono.largeIndex('E'));
    }

    // ------------------------------------------------------------------ the pictures

    @Test
    void picturesHaveTheirSizesAndFilters() throws IOException {
        assertEquals(1024, png("atlas@4x.png").getWidth());
        assertEquals(1024, png("atlas@4x.png").getHeight());
        assertEquals(512, png("atlas@2x.png").getWidth());
        assertEquals(512, png("atlas@2x.png").getHeight());
        for (String n : new String[] { "atlas@4x.png.mcmeta", "atlas@2x.png.mcmeta" }) {
            String meta = new String(Files.readAllBytes(Paths.get(DIR + n)), StandardCharsets.UTF_8)
                .replaceAll("\\s", "");
            assertEquals("{\"texture\":{\"blur\":true,\"clamp\":true}}", meta, n);
        }
    }

    static int ink(BufferedImage img, int x, int y, int w, int h) {
        int n = 0;
        for (int j = y; j < y + h; j++) for (int i = x; i < x + w; i++) if ((img.getRGB(i, j) >>> 24) > 32) n++;
        return n;
    }

    /** Every icon and glyph has something drawn in its cell (the space excepted); unused cells stay empty. */
    @Test
    void everyCellIsDrawn() throws IOException {
        BufferedImage img = png("atlas@4x.png");
        for (int id = 0; id < AtlasMap.COUNT; id++) {
            int[] r = rect(id);
            assertTrue(ink(img, r[0], r[1], r[2], r[3]) > 4, AtlasMap.name(id) + " is empty");
        }
        for (int i = 1; i < AtlasMap.SMALL.length(); i++) assertTrue(
            ink(img, AtlasMap.smallX(i), AtlasMap.smallY(i), AtlasMap.SMALL_W, AtlasMap.SMALL_H) > 4,
            "small glyph '" + AtlasMap.SMALL.charAt(i) + "'");
        assertEquals(0, ink(img, AtlasMap.smallX(0), AtlasMap.smallY(0), AtlasMap.SMALL_W, AtlasMap.SMALL_H));
        for (int i = 0; i < AtlasMap.LARGE.length(); i++) assertTrue(
            ink(img, AtlasMap.largeX(i), AtlasMap.largeY(i), AtlasMap.LARGE_W, AtlasMap.LARGE_H) > 4,
            "large glyph '" + AtlasMap.LARGE.charAt(i) + "'");
        // the reserve past the last icon is empty
        int[] last = rect(AtlasMap.ICON_COUNT - 1);
        int[] reserve = AtlasMap.region(AtlasMap.R_RESERVE);
        assertEquals(0, ink(img, last[0] + 64, last[1], reserve[2] - last[0] - 64, 64));
        assertEquals(0, ink(img, 0, last[1] + 64, reserve[2], reserve[1] + reserve[3] - last[1] - 64));
    }

    /**
     * The committed pictures are what the tool draws now. Java2D's antialiasing may differ by a level or two between
     * JDK builds, so this compares with a small tolerance rather than byte for byte.
     */
    @Test
    @Tag("slow")
    void picturesAreTheToolsOutput() throws IOException {
        BufferedImage four = (BufferedImage) Tools.call("UiAtlas", "atlas4x", new Class<?>[0]);
        BufferedImage two = (BufferedImage) Tools.call("UiAtlas", "half", new Class<?>[] { BufferedImage.class }, four);
        same(four, png("atlas@4x.png"), "atlas@4x.png");
        same(two, png("atlas@2x.png"), "atlas@2x.png");
    }

    static void same(BufferedImage expected, BufferedImage actual, String what) {
        assertEquals(expected.getWidth(), actual.getWidth(), what);
        assertEquals(expected.getHeight(), actual.getHeight(), what);
        int off = 0;
        for (int y = 0; y < expected.getHeight(); y++) for (int x = 0; x < expected.getWidth(); x++) {
            int a = expected.getRGB(x, y), b = actual.getRGB(x, y);
            if (Math.abs((a >>> 24) - (b >>> 24)) > 24) off++;
        }
        assertTrue(
            off <= expected.getWidth() * expected.getHeight() / 1000,
            what + ": " + off + " pixels differ; " + "run java tools/UiAtlas.java");
    }
}
