package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * How a unit made of canvas paints its item (0.14.0, CITY.md §1.4 and §4.2 Visuals): every canvas unit of the
 * Dual-foil Vault has a field on its canvas cells, looking into the nave, a hair in front of the blocks; the picture
 * sits square in its middle over a ground; an empty one's grid runs along its cells; a painting is the icon posterised
 * to a few flat tones, brushed and outlined, the same every time.
 */
class CanvasLookTest {

    private static final CityBuild B = DualFoilVaultShape.BUILD;
    private static final List<CanvasLook.Field> FIELDS = CanvasLook.fields(B, DualFoilVaultShape.UNITS);

    @Test
    void everyCanvasUnitHasAField() {
        assertEquals(43, FIELDS.size(), "42 canvases and the foil");
        for (int i = 0; i < FIELDS.size(); i++) {
            CanvasLook.Field f = FIELDS.get(i);
            UnitLayout.Unit u = DualFoilVaultShape.UNITS.unit(f.unit);
            assertEquals(i, f.unit, "in unit order");
            assertEquals(u.firstSlot, f.slot);
            assertEquals(u.kind, f.kind);
            if (i < 42) {
                assertEquals("canvas", f.kind);
                assertEquals(3, f.width, u.name);
                assertEquals(4, f.height, u.name);
                assertEquals(12, f.cells);
                assertTrue(f.whole());
            } else {
                assertEquals("foil", f.kind);
                assertEquals(13, f.width);
                assertEquals(11, f.height);
                assertEquals(131, f.cells, "the niche's twelve are no cells of the foil");
                assertFalse(f.whole());
            }
        }
        // the other buildings have no canvas
        assertTrue(
            CanvasLook.fields(EcoDomeShape.BUILD, EcoDomeShape.UNITS)
                .isEmpty());
        assertTrue(
            CanvasLook.fields(ResonanceGateShape.BUILD, ResonanceGateShape.UNITS)
                .isEmpty());
    }

    @Test
    void aFieldLiesOnItsCanvasLookingIntoTheNave() {
        for (CanvasLook.Field f : FIELDS) {
            UnitLayout.Unit u = DualFoilVaultShape.UNITS.unit(f.unit);
            int covered = 0;
            for (int a = 0; a < f.width; a++) for (int v = 0; v < f.height; v++) {
                double[] p = f.at(a + 0.5, v + 0.5);
                // the cell behind the face and the one in front of it
                int[] behind = cell(p, f.face, -(CanvasLook.OFF + 0.5)), front = cell(p, f.face, 0.5 - CanvasLook.OFF);
                char ch = B.cell(behind[0], behind[1], behind[2]);
                if (DualFoilVaultShape.UNITS.unitOfCell(behind[0], behind[1], behind[2]) == f.unit) {
                    assertEquals(CanvasLook.CANVAS, ch, "a canvas cell of " + u.name);
                    covered++;
                } else assertEquals('$', ch, "the foil's field holds the niche's soft canvas");
                char open = B.cell(front[0], front[1], front[2]);
                assertTrue(open == ShapeKit.NONE || open == ShapeKit.AIR, u.name + " looks into the open: " + open);
            }
            assertEquals(f.cells, covered, "every canvas cell of " + u.name + " under its field");
            // a hair in front of the wall's face
            double plane = f.face[0] != 0 ? f.origin[0] : f.origin[2];
            double face = Math.rint(plane);
            assertEquals(CanvasLook.OFF, Math.abs(plane - face), 1e-9);
            assertTrue((plane - face) * (f.face[0] + f.face[1]) > 0, "out of the wall, not into it");
        }
        // someone in the nave facing the left wall has the back of the hall on the right; facing the foil, the right
        // wall
        CanvasLook.Field left = FIELDS.get(0), foil = FIELDS.get(42);
        assertArrayEquals(new int[] { 1, 0 }, left.face);
        assertTrue(left.at(left.width, 0)[2] > left.at(0, 0)[2]);
        assertArrayEquals(new int[] { 0, -1 }, foil.face);
        assertTrue(foil.at(foil.width, 0)[0] > foil.at(0, 0)[0]);
        CanvasLook.Field right = FIELDS.get(7);
        assertEquals("GR-1", DualFoilVaultShape.UNITS.unit(right.unit).name);
        assertTrue(right.at(right.width, 0)[2] < right.at(0, 0)[2], "on the right wall the front is on the right");
    }

    private static int[] cell(double[] p, int[] face, double out) {
        return new int[] { (int) Math.floor(p[0] + face[0] * out), (int) Math.floor(p[1]),
            (int) Math.floor(p[2] + face[1] * out) };
    }

    @Test
    void theFoilsFieldIsItsFramesInside() {
        CanvasLook.Field foil = FIELDS.get(42);
        double[] a = foil.at(0, 0), b = foil.at(foil.width, foil.height);
        assertEquals(DualFoilVaultShape.FOIL_X0 + 1, a[0], 1e-9);
        assertEquals(DualFoilVaultShape.FOIL_X1, b[0], 1e-9);
        assertEquals(DualFoilVaultShape.FOIL_Y0 + 1, a[1], 1e-9);
        assertEquals(DualFoilVaultShape.FOIL_Y1, b[1], 1e-9);
        assertEquals(DualFoilVaultShape.NICHE_Z - CanvasLook.OFF, a[2], 1e-9);
    }

    @Test
    void aPictureSitsSquareInTheMiddleOfItsGround() {
        for (CanvasLook.Field f : FIELDS) {
            double[] p = CanvasLook.picture(f), g = CanvasLook.ground(f);
            double side = p[2] - p[0];
            assertEquals(side, p[3] - p[1], 1e-9, "square");
            assertEquals(
                Math.min(CanvasLook.PICTURE_MAX, Math.min(f.width, f.height) * CanvasLook.PICTURE),
                side,
                1e-9);
            assertEquals(f.width / 2.0, (p[0] + p[2]) / 2, 1e-9, "in the middle");
            assertEquals(f.height / 2.0, (p[1] + p[3]) / 2, 1e-9);
            assertTrue(g[0] <= p[0] && g[1] <= p[1] && g[2] >= p[2] && g[3] >= p[3], "over its ground");
            assertTrue(g[0] >= 0 && g[1] >= 0 && g[2] <= f.width && g[3] <= f.height, "within the field");
            if (f.whole()) assertArrayEquals(new double[] { 0, 0, f.width, f.height }, g, 1e-12);
        }
        // a canvas paints large: most of its width
        double[] canvas = CanvasLook.picture(FIELDS.get(0));
        assertTrue(canvas[2] - canvas[0] > 2.5);
        // the foil keeps its dark canvas round its painting
        double[] g = CanvasLook.ground(FIELDS.get(42));
        assertEquals(CanvasLook.PICTURE_MAX + 2 * CanvasLook.GROUND_MARGIN, g[2] - g[0], 1e-9);
    }

    @Test
    void theGridRunsAlongTheCanvasCells() {
        CanvasLook.Field canvas = FIELDS.get(0), foil = FIELDS.get(42);
        List<double[]> lines = CanvasLook.grid(canvas);
        assertEquals(4 + 5, lines.size(), "four column edges, five row edges");
        for (double[] l : lines) {
            for (double v : l) assertEquals(Math.rint(v), v, 1e-12, "on a cell's edge");
            assertTrue(l[0] == l[2] || l[1] == l[3], "upright or level");
        }
        assertEquals(9, CanvasLook.gridQuads(canvas), "each line within a piece");
        // the foil's longer lines take two pieces each
        assertEquals(14 * 2 + 12 * 2, CanvasLook.gridQuads(foil));
        assertEquals(CanvasLook.GROUND_QUADS + CanvasLook.PASSES, CanvasLook.paintingQuads());
        assertEquals(42 * 9 + 52, CanvasLook.quads(FIELDS, ModuleLook.NEAR), "a canvas's grid outweighs its painting");
        assertEquals(0, CanvasLook.quads(FIELDS, ModuleLook.MID));
        assertEquals(0, CanvasLook.quads(FIELDS, ModuleLook.FAR));
    }

    // ---- the painting

    /** A 16 x 16 icon: a smooth wheel of colours inside a ring, clear round it. */
    private static CanvasLook.Image wheel() {
        int[] px = new int[16 * 16];
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double dx = x - 7.5, dy = y - 7.5, r = Math.hypot(dx, dy);
            if (r > 6.5) continue;
            int rgb = CanvasLook.hsv((Math.atan2(dy, dx) / (2 * Math.PI) + 1) % 1, r / 6.5, 0.3 + 0.7 * (x / 15.0));
            px[y * 16 + x] = 0xFF000000 | rgb;
        }
        return new CanvasLook.Image(16, 16, px);
    }

    @Test
    void aPaintingIsPosterisedToItsTones() {
        CanvasLook.Image icon = wheel(), painted = CanvasLook.paint(icon);
        assertEquals(32, painted.width);
        assertEquals(32, painted.height);
        Set<Integer> tones = new HashSet<>(), source = new HashSet<>(), values = new HashSet<>(),
            sourceValues = new HashSet<>();
        for (int p : icon.argb) if (p >>> 24 >= CanvasLook.SOLID_ALPHA) {
            source.add(p & 0xFFFFFF);
            sourceValues.add(Math.max(p >> 16 & 0xFF, Math.max(p >> 8 & 0xFF, p & 0xFF)));
        }
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
            int p = painted.get(x, y), src = icon.get(x / 2, y / 2);
            boolean solid = src >>> 24 >= CanvasLook.SOLID_ALPHA;
            if (!solid) continue;
            assertEquals(0xFF, p >>> 24, "a solid pixel is opaque");
            int rgb = p & 0xFFFFFF;
            tones.add(rgb);
            values.add(Math.max(rgb >> 16 & 0xFF, Math.max(rgb >> 8 & 0xFF, rgb & 0xFF)));
            double max = Math.max(rgb >> 16 & 0xFF, Math.max(rgb >> 8 & 0xFF, rgb & 0xFF)) / 255.0;
            double level = max * (CanvasLook.VALUE_LEVELS - 1);
            assertEquals(Math.rint(level), level, 0.01, "its value is one of the steps: " + max);
            double min = Math.min(rgb >> 16 & 0xFF, Math.min(rgb >> 8 & 0xFF, rgb & 0xFF)) / 255.0;
            if (max > 0) {
                double sat = (max - min) / max * (CanvasLook.SATURATION_LEVELS - 1);
                assertEquals(Math.rint(sat), sat, 0.03, "its saturation is one of the steps");
            }
        }
        assertTrue(tones.size() < source.size(), "fewer tones: " + tones.size() + " of " + source.size());
        assertTrue(values.size() <= CanvasLook.VALUE_LEVELS, "values: " + values);
        assertTrue(values.size() < sourceValues.size() / 2, "far fewer values than the icon's");
        // the same icon, the same painting
        assertArrayEquals(painted.argb, CanvasLook.paint(wheel()).argb);
    }

    @Test
    void aToneIsStepped() {
        assertEquals(0xFFFFFF, CanvasLook.tone(0xFFFFFF, 0));
        assertEquals(0, CanvasLook.tone(0x000000, 0));
        assertEquals(0xFF0000, CanvasLook.tone(0xF01008, 0), "a strong red stays red");
        // a dab lifts or lowers the value by up to DAB, which may step it to the next tone
        assertEquals(CanvasLook.tone(0x9C9C9C, 1), 0xBFBFBF);
        assertEquals(CanvasLook.tone(0x9C9C9C, -1), 0x808080);
        for (int x = 0; x < 50; x++) for (int y = 0; y < 50; y++) {
            double d = CanvasLook.dab(x, y);
            assertTrue(d >= -1 && d < 1);
        }
        // dabs: a stroke three across and five along shares its dab
        assertEquals(CanvasLook.dab(3, 0), CanvasLook.dab(4, 0), 0);
    }

    @Test
    void anOutlineRingsTheShape() {
        int[] px = new int[16 * 16];
        for (int y = 6; y < 10; y++) for (int x = 6; x < 10; x++) px[y * 16 + x] = 0xFF40C0A0;
        CanvasLook.Image painted = CanvasLook.paint(new CanvasLook.Image(16, 16, px));
        // the square covers painting pixels 12..19; one round it is the outline, two round it clear
        for (int y = 12; y < 20; y++) {
            int inside = painted.get(12, y), edge = painted.get(11, y), clear = painted.get(10, y);
            assertEquals(0xFF, inside >>> 24);
            assertEquals(0xFF, edge >>> 24, "the outline is opaque");
            assertEquals(0, clear, "and only one painting pixel wide");
            assertTrue(brightness(edge) < brightness(inside) * 0.5, "darker than the paint beside it");
            assertEquals(CanvasLook.scale(inside & 0xFFFFFF, CanvasLook.OUTLINE), edge & 0xFFFFFF);
        }
        assertEquals(0, painted.get(11, 11), "no outline across a corner");
    }

    private static int brightness(int p) {
        return (p >> 16 & 0xFF) + (p >> 8 & 0xFF) + (p & 0xFF);
    }

    @Test
    void anIconIsItsFirstFrameAtMostThirtyTwoASide() {
        int[] strip = new int[16 * 48];
        for (int i = 0; i < strip.length; i++) strip[i] = i < 16 * 16 ? 0xFFFF0000 : 0xFF0000FF;
        CanvasLook.Image frame = CanvasLook.icon(new CanvasLook.Image(16, 48, strip));
        assertEquals(16, frame.width);
        assertEquals(16, frame.height);
        for (int p : frame.argb) assertEquals(0xFFFF0000, p, "the first frame only");
        // a 64 x 64 icon is averaged down to 32 x 32: four pixels to one, weighted by their alpha
        int[] big = new int[64 * 64];
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) big[y * 64 + x] = (x + y) % 2 == 0 ? 0xFFFFFFFF : 0x00000000;
        CanvasLook.Image small = CanvasLook.icon(new CanvasLook.Image(64, 64, big));
        assertEquals(32, small.width);
        assertEquals(32, small.height);
        for (int p : small.argb) assertEquals(0x7FFFFFFF, p, "half covered, all white");
        assertEquals(16, CanvasLook.icon(new CanvasLook.Image(16, 16, new int[256])).width);
        assertEquals(CanvasLook.ICON_MAX * CanvasLook.SCALE, CanvasLook.paint(small).width, "the largest painting");
    }

    @Test
    void theAverageColourIsTheSolidPixels() {
        int[] px = { 0xFFFF0000, 0xFF0000FF, 0x10FFFFFF, 0 };
        assertEquals(0x7F007F, CanvasLook.average(new CanvasLook.Image(2, 2, px)));
        assertEquals(-1, CanvasLook.average(new CanvasLook.Image(2, 2, new int[4])), "nothing solid");
        assertNotEquals(-1, CanvasLook.average(wheel()));
    }

    @Test
    void aTileIsAWholeFieldOfNoUnit() {
        CanvasLook.Field t = CanvasLook.rect(new double[] { 5, 8, -0.02 }, new int[] { 0, -1 }, 4, 4);
        assertTrue(t.whole());
        assertEquals(-1, t.unit);
        assertArrayEquals(new double[] { 9, 12, -0.02 }, t.at(4, 4), 1e-12, "right along +x looking at the front");
    }
}
