package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The Tidal Well's drawing (0.12.0, CITY.md §4.9 Visuals): the tide rings move within the shaft, clear of every head
 * on the island and the bridges and of the ramp; the ribs' lines lie on the wall's inner face and stop at the bands;
 * the bands' lines face into the well; the grate band glowing from the surface is the forum's grate.
 */
class RootLookTest {

    @Test
    void theRingsStayClearOfThePeople() {
        for (int i = 0; i < RootShape.RING_Y.length; i++) {
            assertTrue(
                RootLook.ringLowest(i) >= RootLook.headUnder(i) + RootLook.CLEAR,
                "ring " + i + " comes down to " + RootLook.ringLowest(i) + ", a head reaches " + RootLook.headUnder(i));
            assertTrue(RootLook.ringHighest(i) < RootShape.LID_Y, "under the lid");
            if (i > 0) assertTrue(RootLook.ringLowest(i) > RootLook.ringHighest(i - 1) - 1, "rings do not pass");
            // the ramp's rail stands on radius 8: the rings stay well inside it
            assertTrue(RootShape.RING_R[i] + 1 < 7, "inside the pool's rim");
        }
        assertEquals(RootLook.headUnder(0), RootShape.FLOOR_Y + 1 + RootLook.HEAD, 1e-9, "the island and its bridges");
    }

    @Test
    void theRingsFollowTheTide() {
        double midnight = Tide.FULL, noon = 6000;
        assertEquals(RootLook.RISE, RootLook.ringRise(0, midnight), 1e-9, "up at the full tide");
        assertEquals(-RootLook.RISE, RootLook.ringRise(0, noon), 1e-9, "down at the ebb");
        assertTrue(RootLook.ringRise(1, midnight) < RootLook.ringRise(0, midnight), "the upper ring lags");
        double a = RootLook.ringTurn(0, 1000, midnight), b = RootLook.ringTurn(0, 1100, midnight);
        assertTrue(b > a, "turning");
        assertTrue(RootLook.ringTurn(1, 1100, midnight) < RootLook.ringTurn(1, 1000, midnight), "the middle one back");
        assertEquals(0, RootLook.crest(0.5), 0);
        assertEquals(RootLook.CREST_HIGH, RootLook.crest(1), 1e-12);
        assertTrue(RootLook.crest(0.9) > 0 && RootLook.crest(0.9) < RootLook.CREST_HIGH);
    }

    @Test
    void theRibLinesLieOnTheWall() {
        double[][] ribs = RootLook.ribs();
        assertEquals(8, ribs.length);
        double c = RootShape.MID + 0.5;
        for (int i = 0; i < 8; i++) {
            double[] r = ribs[i];
            int x = (int) Math.floor(r[0] + r[2] * 0.02), z = (int) Math.floor(r[1] + r[3] * 0.02);
            assertTrue(RootShape.wall(x, z), "rib " + i + " enters the wall");
            int bx = (int) Math.floor(r[0] - r[2] * 0.02), bz = (int) Math.floor(r[1] - r[3] * 0.02);
            assertFalse(RootShape.wall(bx, bz), "just in front of it, the well");
            double d = Math.hypot(r[0] - c, r[1] - c);
            assertTrue(d > 9 && d < 13, "on the wall at radius " + d);
            List<int[]> runs = RootLook.ribRuns(i);
            assertEquals(4, runs.size(), "four stretches between the three bands");
            for (int[] run : runs) for (int y = run[0]; y < run[1]; y++) {
                assertEquals('L', RootShape.cell(x, y, z), "lit at " + y);
                for (int band : RootShape.BANDS) assertTrue(y != band && y != band + 1, "not across a band");
            }
            assertEquals(RootShape.FLOOR_Y, runs.get(0)[0]);
            assertEquals(RootShape.LID_Y, runs.get(3)[1]);
        }
    }

    @Test
    void theBandsFaceIntoTheWell() {
        for (int y : RootShape.BANDS) {
            List<int[]> f = RootLook.bandFaces(y);
            assertTrue(f.size() > 40, "a ring of faces at " + y + ": " + f.size());
            for (int[] face : f) {
                assertEquals(ModuleLook.COURSE, RootShape.cell(face[0], y, face[1]));
                assertTrue(RootShape.inside(face[0] + face[2], y, face[1] + face[3]));
            }
        }
    }

    @Test
    void theGrateBandIsTheForumsGrate() {
        Set<Long> want = new HashSet<>();
        for (long seed : new long[] { 1, 42, Mix.seed(100, 66, -200) }) {
            Map<Long, Integer> floor = new CampusPlan(seed).establishFloor();
            Set<Long> grate = new HashSet<>();
            for (Map.Entry<Long, Integer> e : floor.entrySet()) {
                int a = CampusPlan.keyA(e.getKey()), r = CampusPlan.keyR(e.getKey());
                if (Math.abs(a) <= RootShape.MID && Math.abs(r) <= RootShape.MID
                    && e.getValue() == Parts.deck(Parts.D_GRATE)) grate.add(e.getKey());
            }
            if (want.isEmpty()) want.addAll(grate);
            assertEquals(want, grate, "the same for every campus");
        }
        List<int[]> cells = RootLook.grate();
        assertEquals(want.size(), cells.size());
        for (int[] c : cells) {
            int[] campus = RootShape.toCampus(c[0], RootLook.SURFACE_Y, c[1]);
            assertEquals(0, campus[2], "on the forum's layer, Y0");
            assertTrue(want.contains(CampusPlan.key(campus[0], campus[1])));
            assertTrue(RootShape.inWell(c[0], c[1]), "over the well");
        }
    }

    /**
     * A shallower well's drawing (0.14.1): its rings (those it has) move under its lid and clear of every head, its
     * ribs' lines run from the floor to its lid broken only by its own bands, its bands face into it, and the forum
     * over its lid is its depth up in its frame.
     */
    @Test
    void aShallowerWellIsDrawnUnderItsOwnLid() {
        for (RootShape.Well w : RootShape.wells()) {
            String at = " at depth " + w.depth;
            for (int i = 0; i < w.ringY().length; i++) {
                // the drawn ring (its steel a fifth of a block each way of its layer's middle, risen by the tide)
                // stays under the lid; 14 deep the upper ring's cell risen whole would touch it, its steel does not
                assertTrue(
                    RootShape.RING_Y[i] + 0.5 + RootLook.RISE + 0.2 < w.lidY,
                    "ring " + i + " under the lid" + at);
                assertTrue(RootLook.ringLowest(i) >= RootLook.headUnder(i, w) + RootLook.CLEAR, "ring " + i + at);
            }
            assertEquals(w.depth, RootLook.surfaceY(w));
            for (int rib = 0; rib < 8; rib++) {
                double[] r = RootLook.ribs()[rib];
                int x = (int) Math.floor(r[0] + r[2] * 0.02), z = (int) Math.floor(r[1] + r[3] * 0.02);
                List<int[]> runs = RootLook.ribRuns(rib, w);
                assertEquals(w.bands().length + 1, runs.size(), "stretches between the bands" + at);
                assertEquals(RootShape.FLOOR_Y, runs.get(0)[0]);
                assertEquals(w.lidY, runs.get(runs.size() - 1)[1], "up to the lid" + at);
                for (int[] run : runs) for (int y = run[0]; y < run[1]; y++) assertEquals('L', w.cell(x, y, z));
            }
            for (int y : w.bands()) {
                List<int[]> f = RootLook.bandFaces(y, w), full = RootLook.bandFaces(y);
                assertEquals(full.size(), f.size(), "the same course as the full well's" + at);
                for (int i = 0; i < f.size(); i++) assertArrayEquals(full.get(i), f.get(i));
                for (int[] face : f) assertTrue(w.inside(face[0] + face[2], y, face[1] + face[3]));
            }
        }
        assertEquals(RootLook.SURFACE_Y, RootLook.surfaceY(RootShape.well(26)));
    }

    /**
     * The well's drawing over every form of the deep root (0.16.0): drawn in the well's own frame, which lies
     * {@link RootShape#base} layers up in the form's (the chamber's height, 0 in-well), its ribs' lines still lie on
     * rib cells, its bands' faces on their course and in front of what the well has there, and its rings stay clear of
     * every head, the bays' and the conduits' new cells counted (players pass through a conduit). The well's frame
     * keeps the controller where the form has it.
     */
    @Test
    void theWellsDrawingHoldsOverEveryDeepForm() {
        for (RootShape.Deep d : RootShape.deeps()) {
            RootShape.Well w = d.well;
            int b = RootShape.base(d.name);
            String at = " in " + d.name;
            assertEquals(d.h, b);
            int[] wc = w.build.controller(), dc = d.build.controller();
            assertArrayEquals(new int[] { wc[0], wc[1] + b, wc[2] }, dc, "the controller" + at);
            for (int rib = 0; rib < 8; rib++) {
                double[] r = RootLook.ribs()[rib];
                int x = (int) Math.floor(r[0] + r[2] * 0.02), z = (int) Math.floor(r[1] + r[3] * 0.02);
                for (int[] run : RootLook.ribRuns(rib, w))
                    for (int y = run[0]; y < run[1]; y++) assertEquals('L', d.cell(x, y + b, z), "rib " + rib + at);
            }
            for (int y : w.bands()) for (int[] f : RootLook.bandFaces(y, w)) {
                assertEquals(ModuleLook.COURSE, d.cell(f[0], y + b, f[1]), "a band" + at);
                int nx = f[0] + f[2], nz = f[1] + f[3];
                assertEquals(w.cell(nx, y, nz), d.cell(nx, y + b, nz), "in front of a band" + at);
            }
            for (int i = 0; i < w.ringY().length; i++) assertTrue(
                RootLook.ringLowest(i) >= headUnder(i, d) + RootLook.CLEAR,
                "ring " + i + at + ": a head reaches " + headUnder(i, d));
        }
    }

    /** {@link RootLook#headUnder(int, RootShape.Well)} over a deep root's form, in its well's frame. */
    private static double headUnder(int ring, RootShape.Deep d) {
        double outer = RootShape.RING_R[ring] + 1, c = RootShape.MID;
        double top = RootShape.FLOOR_Y + 1;
        for (int x = 0; x < RootShape.SIZE; x++) for (int z = 0; z < RootShape.SIZE; z++) {
            if (Math.hypot(x - c, z - c) > outer + 0.75) continue;
            for (int y = 0; y < RootShape.RING_Y[ring]; y++) {
                char ch = d.cell(x, y + d.h, z);
                if (ch == ShapeKit.NONE || ch == ShapeKit.AIR
                    || ch == '='
                    || ch == RootShape.RING
                    || ch == RootShape.CONDUIT
                    || ch == ShapeKit.CONTROLLER) continue;
                char up = d.cell(x, y + 1 + d.h, z);
                if (up == ShapeKit.NONE || up == ShapeKit.AIR || up == RootShape.CONDUIT)
                    top = Math.max(top, y + 1 + RootLook.HEAD);
            }
        }
        return top;
    }
}
