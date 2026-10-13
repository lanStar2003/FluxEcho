package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The Dual-foil Vault's drawing (0.14.0, CITY.md §4.2 Visuals): the collage's tiles lie on the facade canvas, mirrored
 * about the axis, the most frequent item in the large middle one; the skylight shafts fall through the nave's air and
 * pool on the floor or deck they land on, as bright as the day; the foil's rim runs round its canvas inside its lit
 * frame; the facade's sweep and frame lie on the facade; the vault keeps CITY.md §1.3's budgets with every canvas
 * painted or gridded.
 */
class VaultLookTest {

    private static final CityBuild B = DualFoilVaultShape.BUILD;

    @Test
    void theCollageLiesOnTheFacadeCanvas() {
        assertEquals(17, VaultLook.FIELD_W);
        assertEquals(12, VaultLook.FIELD_H);
        double last = Double.MAX_VALUE;
        for (int r = 0; r < VaultLook.COLLAGE; r++) {
            double[] t = VaultLook.tile(r);
            double side = t[2] - t[0];
            assertEquals(side, t[3] - t[1], 1e-12, "square");
            assertTrue(side <= last, "never larger than a more frequent item's");
            last = side;
            // on the canvas cells of the front wall, inside its lit frame
            for (double x = t[0] + 0.01; x < t[2]; x += 0.25)
                for (double y = t[1] + 0.01; y < t[3]; y += 0.25) assertEquals(
                    'Q',
                    B.cell((int) Math.floor(x), (int) Math.floor(y), 0),
                    "tile " + r + " at " + x + "," + y);
            for (int q = 0; q < r; q++) {
                double[] o = VaultLook.tile(q);
                boolean apart = t[2] <= o[0] || o[2] <= t[0] || t[3] <= o[1] || o[3] <= t[1];
                assertTrue(apart, "tiles " + q + " and " + r + " overlap");
            }
            CanvasLook.Field f = VaultLook.tileField(r);
            assertArrayEquals(new double[] { t[0], t[1], VaultLook.FRONT }, f.at(0, 0), 1e-12);
            assertArrayEquals(new double[] { t[2], t[3], VaultLook.FRONT }, f.at(side, side), 1e-12);
            assertTrue(f.whole());
        }
        // the largest in the middle, the rest in pairs mirrored about the axis (x 13.5)
        double[] first = VaultLook.tile(0);
        assertEquals(DualFoilVaultShape.MID + 0.5, (first[0] + first[2]) / 2, 1e-12);
        for (int r = 1; r + 1 < VaultLook.COLLAGE; r += 2) {
            double[] a = VaultLook.tile(r), b = VaultLook.tile(r + 1);
            assertEquals(2 * (DualFoilVaultShape.MID + 0.5), a[0] + b[2], 1e-12, "tiles " + r + " and " + (r + 1));
            assertEquals(a[1], b[1], 1e-12);
        }
        assertTrue(VaultLook.FRONT < 0 && VaultLook.FRONT > -0.05, "a hair in front of the front wall");
    }

    @Test
    void theCollageRanksTheMostFrequentFirst() {
        assertArrayEquals(
            new int[] { 2, 0, 4 },
            VaultLook.ranking(Arrays.asList("a", null, "b", "b", "c", "a", "b")),
            "b in three slots, a in two, c in one");
        assertArrayEquals(new int[] { 1, 0 }, VaultLook.ranking(Arrays.asList("x", "y", "y", "x", null, "y")));
        assertArrayEquals(new int[] { 0, 1 }, VaultLook.ranking(Arrays.asList("x", "y")), "a tie by the first slot");
        assertEquals(0, VaultLook.ranking(Arrays.asList(null, null)).length);
        List<Integer> many = new ArrayList<>();
        for (int i = 0; i < 43; i++) many.add(i % 20);
        int[] r = VaultLook.ranking(many);
        assertEquals(VaultLook.COLLAGE, r.length, "nine at most");
        assertArrayEquals(new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8 }, r, "0..2 three times, the rest twice");
        assertEquals(0, VaultLook.ranking(Collections.<String>emptyList()).length);
    }

    @Test
    void theShaftsFallThroughTheNavesAir() {
        List<double[]> shafts = VaultLook.shafts();
        double[] strips = shafts.stream()
            .mapToDouble(s -> s[2])
            .distinct()
            .toArray();
        assertArrayEquals(new double[] { 3, 9, 15, 21, 27, 33 }, strips, 0, "one shaft a skylight strip");
        for (double z : strips) {
            // the strip's pieces run on from one to the next across it
            double x = 4 + VaultLook.SHAFT_IN;
            for (double[] s : shafts) {
                if (s[2] != z) continue;
                assertEquals(x, s[0], 1e-12, "strip " + z);
                x = s[1];
            }
            assertEquals(23 - VaultLook.SHAFT_IN, x, 1e-12);
            assertEquals('G', B.cell(4, DualFoilVaultShape.ROOF_Y, (int) z), "under the glass");
        }
        int pools = 0, floor = 0;
        for (double[] s : shafts) {
            double top = VaultLook.SHAFT_TOP, bottom = s[3];
            assertTrue(bottom < top - 3, "a shaft falls: " + Arrays.toString(s));
            for (double y = bottom + 0.01; y < top; y += 0.1) {
                double front = VaultLook.shaftFront(s[2], y);
                for (int i = 0; i <= 10; i++) for (int j = 0; j <= 5; j++) {
                    double x = Math.min(s[0] + (s[1] - s[0]) * i / 10, s[1] - 1e-6),
                        z = Math.min(front + j / 5.0, front + 1 - 1e-6);
                    char ch = LookCells.at(B, x, y, z);
                    assertTrue(LookCells.OPEN.indexOf(ch) >= 0, "in the air at " + x + "," + y + "," + z + ": " + ch);
                }
            }
            if (s[4] > 0) {
                pools++;
                double front = VaultLook.shaftFront(s[2], bottom);
                assertEquals(Math.rint(bottom), bottom, 1e-12, "on the top of a block");
                for (int i = 0; i <= 10; i++) for (int j = 0; j <= 5; j++) {
                    double x = Math.min(s[0] + (s[1] - s[0]) * i / 10, s[1] - 1e-6),
                        z = Math.min(front + j / 5.0, front + 1 - 1e-6);
                    assertTrue(LookCells.at(B, x, bottom - 0.5, z) != ShapeKit.NONE, "a floor under the pool");
                }
                if (bottom == 1) floor++;
            }
        }
        assertTrue(floor >= 3, "the middle strips reach the nave's floor: " + floor);
        assertTrue(pools >= floor);
        assertEquals(VaultLook.shaftQuads(), shafts.size() * 2 + pools + 2 * strips.length);
    }

    @Test
    void theShaftsAreAsBrightAsTheDay() {
        assertEquals(1, VaultLook.daylight(6000), 1e-12, "noon");
        assertEquals(VaultLook.NIGHT, VaultLook.daylight(18000), 1e-12, "midnight");
        assertEquals(VaultLook.daylight(6000), VaultLook.daylight(6000 + 24000 * 7), 1e-12, "every day the same");
        assertEquals(VaultLook.daylight(-18000), VaultLook.daylight(6000), 1e-12);
        double was = VaultLook.daylight(22000);
        for (long t = 22100; t <= 30000; t += 100) {
            double d = VaultLook.daylight(t);
            assertTrue(d >= was - 1e-12, "rising at dawn");
            was = d;
        }
        assertTrue(VaultLook.daylight(0) > VaultLook.NIGHT && VaultLook.daylight(0) < 1, "sunrise");
    }

    @Test
    void theRimRunsRoundTheFoilInsideItsFrame() {
        assertEquals(48, VaultLook.PERIMETER);
        assertArrayEquals(new double[] { 7, 3 }, VaultLook.rimPoint(0), 1e-12);
        assertArrayEquals(new double[] { 20, 3 }, VaultLook.rimPoint(13), 1e-12);
        assertArrayEquals(new double[] { 20, 14 }, VaultLook.rimPoint(24), 1e-12);
        assertArrayEquals(new double[] { 7, 14 }, VaultLook.rimPoint(37), 1e-12);
        assertArrayEquals(VaultLook.rimPoint(5), VaultLook.rimPoint(53), 1e-12);
        int z = DualFoilVaultShape.NICHE_Z;
        double cx = VaultLook.FOIL_X + VaultLook.FOIL_W / 2.0, cy = VaultLook.FOIL_Y + VaultLook.FOIL_H / 2.0;
        for (int i = 0; i < VaultLook.RIM_PIECES; i++) {
            double[] a = VaultLook.rimPoint(i), b = VaultLook.rimPoint(i + 1);
            assertEquals(1, Math.hypot(b[0] - a[0], b[1] - a[1]), 1e-12, "a block a piece, on one side");
            // the canvas inside the rim, the lit frame outside it
            double mx = (a[0] + b[0]) / 2, my = (a[1] + b[1]) / 2;
            boolean level = a[1] == b[1];
            double ix = level ? 0 : Math.signum(cx - mx) * 0.3, iy = level ? Math.signum(cy - my) * 0.3 : 0;
            char inner = LookCells.at(B, mx + ix, my + iy, z + 0.5), outer = LookCells.at(B, mx - ix, my - iy, z + 0.5);
            assertTrue(inner == 'Q' || inner == '$', "canvas inside at " + mx + "," + my + ": " + inner);
            assertEquals('L', outer, "the lit frame outside at " + mx + "," + my);
        }
        assertEquals(z - CanvasLook.OFF, VaultLook.BACK, 1e-12, "a hair in front of the back wall");
        // the bright arc runs round once every RIM_T ticks
        double best = -1, at = 0;
        for (int i = 0; i < 480; i++) {
            double s = i * 0.1, h = VaultLook.horizon(s, 40);
            assertTrue(h >= 0 && h <= 1);
            assertEquals(h, VaultLook.horizon(s, 40 + VaultLook.RIM_T), 1e-9);
            if (h > best) {
                best = h;
                at = s;
            }
        }
        assertTrue(best > 0.85, "a bright arc");
        assertEquals(40.0 / VaultLook.RIM_T * VaultLook.PERIMETER % VaultLook.PERIMETER, at, 1.5);
    }

    @Test
    void theSweepAndTheFrameLieOnTheFacade() {
        assertNull(VaultLook.sweep(3), "below the facade");
        assertNull(VaultLook.sweep(DualFoilVaultShape.FACADE_Y1 + 1 + VaultLook.SWEEP_TRAIL + 0.5), "past it");
        double[] mid = VaultLook.sweep(14);
        assertArrayEquals(new double[] { 11, 14 }, mid, 1e-12);
        assertArrayEquals(new double[] { 7, 8 }, VaultLook.sweep(8), 1e-12, "it starts at the frame's foot");
        assertArrayEquals(new double[] { 20, 21 }, VaultLook.sweep(23), 1e-12, "the wash fades out over the top");
        for (double[] l : VaultLook.frame()) for (int k = 0; k <= 20; k++) {
            double x = l[0] + (l[2] - l[0]) * k / 20, y = l[1] + (l[3] - l[1]) * k / 20;
            char ch = LookCells.at(B, x, y, 0.5);
            assertTrue(ch == 'L' || ch == 'A', "on the lit frame at " + x + "," + y + ": " + ch);
        }
        for (int k = 0; k < 100; k++) {
            double b = VaultLook.breath(k * 3.7), g = VaultLook.glint(k * 3.7);
            assertTrue(b >= 0 && b <= 1 && g >= 0 && g < 1);
            assertEquals(b, VaultLook.breath(k * 3.7 + VaultLook.BREATH_T), 1e-9);
        }
    }

    @Test
    void theVaultKeepsItsBudget() {
        List<CanvasLook.Field> fields = CanvasLook.fields(B, DualFoilVaultShape.UNITS);
        int[] budget = { 2000, 300, 64 };
        for (int tier = ModuleLook.NEAR; tier <= ModuleLook.FAR; tier++) {
            int base = ModuleLook.baseQuads(B, DualFoilVaultShape.UNITS, tier) + CanvasLook.quads(fields, tier);
            for (boolean docked : new boolean[] { false, true })
                for (boolean on : new boolean[] { false, true }) for (boolean forming : new boolean[] { false, true }) {
                    if (on && !docked) continue;
                    int own = VaultLook.quads(tier, docked, on, forming);
                    assertTrue(
                        base + own <= budget[tier],
                        "tier " + tier
                            + (docked ? " docked" : "")
                            + (on ? " powered" : "")
                            + ": "
                            + base
                            + " + "
                            + own);
                }
        }
        // the shared drawing: the sweep through the 27 x 35 box, one plate, the 51 faces of the plinth course
        assertEquals(19, ModuleLook.sweepQuads(B));
        assertEquals(19 + 80 + 51, ModuleLook.baseQuads(B, DualFoilVaultShape.UNITS, ModuleLook.MID));
        // far: the collage's nine patches and the sweep while it forms
        assertEquals(VaultLook.COLLAGE + VaultLook.sweepQuads(), VaultLook.quads(ModuleLook.FAR, true, true, true));
        assertEquals(4, VaultLook.sweepQuads(), "the wash and the line in three pieces");
        // near and powered: the rim a line and a halo a block and four bands of dark
        assertEquals(2 * 48 + 4, VaultLook.rimQuads(ModuleLook.NEAR, true, true));
        assertEquals(48, VaultLook.rimQuads(ModuleLook.NEAR, true, false));
        assertEquals(16, VaultLook.rimQuads(ModuleLook.MID, true, true));
        assertEquals(0, VaultLook.rimQuads(ModuleLook.MID, false, false));
        assertEquals(20, VaultLook.frameQuads(ModuleLook.NEAR));
        assertEquals(10, VaultLook.frameQuads(ModuleLook.MID));
    }
}
