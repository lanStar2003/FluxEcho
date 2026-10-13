package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The Mana Fountain Array's drawing (0.13.0, CITY.md §4.6 Visuals): the spout winds up the obelisk's dissolved column
 * under the name plate; the jets arc from the fountains' sockets to the spout through the court's air; the magic
 * circle turns on the open floor, passing under the basins and the urns; the sparkles drift out through the
 * colonnade's openings into the garden; every motion repeats; the array keeps CITY.md §1.3's budgets.
 */
class ManaLookTest {

    private static final CityBuild B = ManaFountainShape.BUILD;
    private static final double AXIS = ManaLook.AXIS;
    private static final String OPEN = LookCells.OPEN + ManaFountainShape.DISSOLVES;

    @Test
    void theSpoutSpiralsUpTheDissolvedObelisk() {
        assertTrue(ManaFountainShape.DISSOLVES.contains("C") && ManaFountainShape.DISSOLVES.contains("Z"));
        for (int y = ManaFountainShape.OBELISK_FROM; y <= ManaFountainShape.OBELISK_TO; y++) assertTrue(
            ManaFountainShape.DISSOLVES.indexOf(B.cell(ManaFountainShape.MID, y, ManaFountainShape.MID)) >= 0,
            "the obelisk at y" + y);
        double plate = ModuleLook.plates(B)
            .get(0)[1];
        assertTrue(ManaLook.SPOUT_TO + 0.1 < plate - 0.5, "under the name plate at " + plate);
        for (int n : new int[] { ManaLook.SPOUT_MOTES[ModuleLook.NEAR], ManaLook.SPOUT_MOTES[ModuleLook.MID] })
            for (int k = 0; k < n; k++) for (int j = 0; j < 100; j++) {
                double t = j * 2.3;
                double[] m = ManaLook.spoutMote(k, n, t);
                double r = Math.hypot(m[0] - AXIS, m[2] - AXIS);
                assertTrue(r <= ManaLook.SPOUT_R * 1.3 + 1e-9, "round the axis: " + r);
                assertTrue(m[1] >= ManaLook.SPOUT_FROM - 1e-9 && m[1] <= ManaLook.SPOUT_TO + 1e-9);
                assertTrue(m[3] >= 0 && m[3] <= 1);
                assertTrue(LookCells.clearance(B, m[0], m[1], m[2], OPEN, 1) >= 0.1, "over the controller's top");
            }
        // two strands, half a turn apart
        double[] a = ManaLook.spoutMote(0, 32, 40), b = ManaLook.spoutMote(1, 32, 40 - ManaLook.SPOUT_T * 0.5 / 16);
        assertEquals(a[1], b[1], 1e-9, "at the same height");
        assertEquals(2 * AXIS - a[0], b[0], 1e-6, "on the far side of the axis");
        assertEquals(2 * AXIS - a[2], b[2], 1e-6);
    }

    @Test
    void theJetsArcFromTheSocketsToTheSpout() {
        List<int[]> fountains = ManaLook.fountains();
        assertEquals(8, fountains.size());
        for (int i = 0; i < 8; i++) {
            int[] s = fountains.get(i);
            assertEquals('o', B.cell(s[0], s[1], s[2]), "a socket");
            assertEquals('=', B.cell(s[0], s[1] - 1, s[2]), "over its basin's water");
            double[] start = ManaLook.jetPoint(i, 0), end = ManaLook.jetPoint(i, 1);
            assertArrayEquals(new double[] { s[0] + 0.5, ManaLook.JET_FROM_Y, s[2] + 0.5 }, start, 1e-9);
            assertTrue(start[1] > s[1] + 1, "over the socket's top");
            assertEquals(ManaLook.JET_TO_R, Math.hypot(end[0] - AXIS, end[2] - AXIS), 1e-9, "at the spout");
            assertTrue(end[1] > ManaLook.SPOUT_FROM && end[1] < ManaLook.SPOUT_TO, "into the column");
            double top = 0;
            for (int j = 1; j <= 100; j++) {
                double u = j / 100.0;
                double[] p = ManaLook.jetPoint(i, u);
                top = Math.max(top, p[1]);
                if (u < 0.05) continue;
                // the halo (three and a half times the core) in the air too
                double room = LookCells.clearance(B, p[0], p[1], p[2], OPEN, 1.5);
                assertTrue(room >= 3.5 * ManaLook.jetHalf(u), "jet " + i + " at " + u + ": " + room);
            }
            assertTrue(top > end[1] + 0.3, "an arc, over its end");
        }
        for (int j = 0; j < 100; j++) {
            double u = ManaLook.jetDrop(3, 1, j * 1.7);
            assertTrue(u >= 0 && u < 1);
        }
    }

    @Test
    void theMagicCircleTurnsOnTheOpenFloor() {
        for (int tier : new int[] { ModuleLook.NEAR, ModuleLook.MID }) {
            double outer = pieces(ManaLook.OUTER_R, ManaLook.OUTER_PIECES[tier], ManaLook.outerPieces(tier)),
                inner = pieces(ManaLook.INNER_R, ManaLook.INNER_PIECES[tier], ManaLook.innerPieces(tier));
            assertTrue(outer >= 0.85 * 2 * Math.PI, "the outer circle mostly drawn: " + outer);
            assertTrue(outer < 2 * Math.PI - 0.2, "under the basins and the urns it is not: " + outer);
            assertEquals(2 * Math.PI, inner, 1e-9, "the inner circle lies open all round");
            assertEquals(
                ManaLook.INNER_PIECES[tier],
                ManaLook.innerPieces(tier)
                    .size());
        }
        // the outer circle runs inside the colonnade, out past the fountains
        assertTrue(ManaLook.OUTER_R < 8.5 && ManaLook.OUTER_R > 6 + 1.5);
        boolean hidden = false;
        int[] shown = new int[ManaLook.RUNES];
        for (int j = 0; j < 300; j++) {
            double t = j * ManaLook.OUTER_TURN_T / 300.0 + 0.3;
            for (int i = 0; i < ManaLook.RUNES; i++) {
                double[][] strokes = ManaLook.rune(i, t);
                if (strokes == null) {
                    hidden = true;
                    continue;
                }
                shown[i]++;
                for (double[] s : strokes) {
                    open(s[0], s[1]);
                    open(s[2], s[3]);
                }
            }
            for (int k = 0; k < ManaLook.TICKS; k++) {
                double[] s = ManaLook.tick(k, t);
                for (int h = 0; h <= 4; h++) open(s[0] + (s[2] - s[0]) * h / 4, s[1] + (s[3] - s[1]) * h / 4);
            }
        }
        assertTrue(hidden, "a rune passes under an urn or a basin");
        for (int i = 0; i < ManaLook.RUNES; i++) assertTrue(shown[i] > 150, "rune " + i + " mostly shown");
        assertTrue(ManaLook.CIRCLE_Y > 1 && ManaLook.CIRCLE_Y < 1.05, "just over the floor's top");
        assertTrue(ManaLook.innerTurn(10) < 0 && ManaLook.outerTurn(10) > 0, "the two turn opposite ways");
    }

    /**
     * Every drawn piece of a circle lies over the open floor all along its line, and none is longer than a whole
     * circle's piece; returns the angle they cover together.
     */
    private static double pieces(double r, int n, List<double[]> drawn) {
        double covered = 0, last = -1;
        for (double[] a : drawn) {
            assertTrue(a[1] > a[0] && a[0] >= last - 1e-12, "in order round, each forward");
            assertTrue(a[1] - a[0] <= 2 * Math.PI / n + 1e-9, "no longer than a piece");
            covered += a[1] - a[0];
            last = a[1];
            double[] p = ManaLook.onCircle(r, a[0]), q = ManaLook.onCircle(r, a[1]);
            for (int h = 0; h <= 10; h++) open(p[0] + (q[0] - p[0]) * h / 10, p[1] + (q[1] - p[1]) * h / 10);
        }
        return covered;
    }

    /** A point of the floor's top with a soft tile under it and nothing standing on it. */
    private static void open(double x, double z) {
        char floor = LookCells.at(B, x, 0, z), over = LookCells.at(B, x, 1, z);
        assertTrue(ShapeKit.soft(floor), "on the soft floor at " + x + "," + z + ": '" + floor + "'");
        assertEquals(ShapeKit.NONE, over, "nothing standing on it at " + x + "," + z);
        assertTrue(ManaLook.openFloor(x, z));
    }

    @Test
    void theSparklesDriftIntoTheGarden() {
        List<int[]> ways = ManaLook.sparkleWays();
        assertEquals(40, ways.size(), "twelve openings, one to five soil cells beyond each");
        for (int[] w : ways) {
            assertTrue(ManaFountainShape.onColonnade(w[0], w[1]), "an opening of the colonnade");
            for (int y = 1; y < ManaFountainShape.ARCHITRAVE_Y; y++)
                assertEquals(ShapeKit.NONE, B.cell(w[0], y, w[1]), "between the posts at y" + y);
            assertEquals(',', B.cell(w[2], 0, w[3]), "garden soil");
            assertEquals(ShapeKit.NONE, B.cell(w[2], 1, w[3]), "open over it");
            assertTrue(B.cell(w[2], ManaFountainShape.ROOF_Y, w[3]) != ShapeKit.NONE, "under the glass roof");
            for (int j = 0; j <= 200; j++) {
                double u = j / 200.0;
                double[] p = ManaLook.sparklePath(w, u);
                double room = LookCells.clearance(B, p[0], p[1], p[2], OPEN, 1);
                assertTrue(room >= 0.12, "a sparkle's way " + java.util.Arrays.toString(w) + " at " + u + ": " + room);
            }
            double[] end = ManaLook.sparklePath(w, 1);
            assertArrayEquals(new double[] { w[2] + 0.5, ManaLook.SPARKLE_TO_Y, w[3] + 0.5 }, end, 1e-9);
        }
        for (int k = 0; k < ManaLook.SPARKLES; k++) for (int j = 0; j < 200; j++) {
            double t = j * 7.3;
            double[] s = ManaLook.sparkle(k, t), again = ManaLook.sparkle(k, t);
            assertArrayEquals(s, again, 0, "the same time, the same frame");
            assertTrue(s[3] >= 0 && s[3] <= 1);
        }
    }

    @Test
    void theMotionsRepeat() {
        for (double t : new double[] { 0, 123.5, 98765.25 }) {
            for (int k = 0; k < 32; k++)
                assertArrayEquals(ManaLook.spoutMote(k, 32, t), ManaLook.spoutMote(k, 32, t + ManaLook.SPOUT_T), 1e-9);
            for (int i = 0; i < 8; i++) for (int k = 0; k < ManaLook.JET_DROPS; k++)
                assertEquals(ManaLook.jetDrop(i, k, t), ManaLook.jetDrop(i, k, t + ManaLook.JET_T), 1e-9);
            for (double a : new double[] { 0, 1, 4 }) {
                double d = ManaLook.dash(a, t);
                assertTrue(d >= 0 && d <= 1);
                assertEquals(d, ManaLook.dash(a, t + ManaLook.OUTER_TURN_T), 1e-9);
            }
            for (int j = 0; j < ManaLook.TICKS; j++)
                assertArrayEquals(ManaLook.tick(j, t), ManaLook.tick(j, t + ManaLook.INNER_TURN_T), 1e-9);
            for (int i = 0; i < ManaLook.RUNES; i++) {
                double[][] a = ManaLook.rune(i, t), b = ManaLook.rune(i, t + ManaLook.OUTER_TURN_T);
                assertEquals(a == null, b == null);
                if (a != null) for (int k = 0; k < a.length; k++) assertArrayEquals(a[k], b[k], 1e-9);
            }
        }
    }

    @Test
    void theArrayKeepsItsBudget() {
        // the shared drawing as drawn: the forming sweep through the 25 x 25 box is a plane and four rims of four
        // pieces
        assertEquals(17, ModuleLook.sweepQuads(B));
        assertEquals(17, ModuleLook.baseQuads(B, ManaFountainShape.UNITS, ModuleLook.FAR));
        int[] budget = { 2000, 300, 64 };
        for (int tier = ModuleLook.NEAR; tier <= ModuleLook.FAR; tier++) {
            int base = ModuleLook.baseQuads(B, ManaFountainShape.UNITS, tier);
            for (boolean docked : new boolean[] { false, true }) for (boolean on : new boolean[] { false, true }) {
                if (on && !docked) continue;
                int own = ManaLook.quads(tier, docked, on);
                assertTrue(
                    base + own <= budget[tier],
                    "tier " + tier + (docked ? " docked" : "") + (on ? " powered" : "") + ": " + base + " + " + own);
            }
        }
        // far: the spout's glow and the jets in two pieces each
        assertEquals(1 + 8 * 2, ManaLook.quads(ModuleLook.FAR, true, true));
        assertEquals(1, ManaLook.quads(ModuleLook.FAR, false, false));
        assertEquals(
            ManaLook.quads(ModuleLook.NEAR, true, false) + ManaLook.SPARKLES,
            ManaLook.quads(ModuleLook.NEAR, true, true));
    }
}
