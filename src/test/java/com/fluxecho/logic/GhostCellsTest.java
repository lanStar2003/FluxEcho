package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class GhostCellsTest {

    private static final int DECK = Parts.deck(Parts.D_DECK);

    /** Cells from rows {x, y, z, part, stage, kind}. */
    private static GhostCells.Cells cells(int[]... rows) {
        int n = rows.length;
        int[] x = new int[n], y = new int[n], z = new int[n], part = new int[n], stage = new int[n];
        byte[] kind = new byte[n];
        for (int i = 0; i < n; i++) {
            x[i] = rows[i][0];
            y[i] = rows[i][1];
            z[i] = rows[i][2];
            part[i] = rows[i][3];
            stage[i] = rows[i][4];
            kind[i] = (byte) rows[i][5];
        }
        return new GhostCells.Cells(x, y, z, part, stage, kind);
    }

    private static int[] hard(int x, int y, int z) {
        return new int[] { x, y, z, DECK, 0, BuildPlan.HARD };
    }

    /** A plan-sized random scatter of cells over a 129 x 129 disc, 0 to 15 high, no two at one position. */
    private static GhostCells.Cells big(int n, long seed) {
        Random r = new Random(seed);
        Set<Long> seen = new HashSet<>();
        List<int[]> rows = new ArrayList<>();
        while (rows.size() < n) {
            int x = r.nextInt(129) - 64, y = 64 + r.nextInt(16), z = r.nextInt(129) - 64;
            if (!seen.add(GhostCells.pos(x, y, z))) continue;
            int kind = r.nextInt(50) == 0 ? BuildPlan.AIR : r.nextInt(3) == 0 ? BuildPlan.SOFT : BuildPlan.HARD;
            rows.add(new int[] { x, y, z, kind == BuildPlan.AIR ? Parts.AIR : DECK, r.nextInt(9), kind });
        }
        return cells(rows.toArray(new int[0][]));
    }

    @Test
    void nearestFirstWithTiesByIndex() {
        // viewer at the centre of cell (0,0,0): cells 1 and 3 are equally far, so is the pair 0 and 4
        GhostCells.Cells c = cells(hard(3, 0, 0), hard(0, 0, 1), hard(0, 0, 0), hard(-1, 0, 0), hard(0, 0, -3));
        int[] got = GhostCells.select(c, null, 0.5, 0.5, 0.5, 100, 100);
        assertArrayEquals(new int[] { 2, 1, 3, 0, 4 }, got);
    }

    @Test
    void theCapKeepsTheNearest() {
        GhostCells.Cells c = cells(hard(5, 0, 0), hard(1, 0, 0), hard(4, 0, 0), hard(2, 0, 0), hard(3, 0, 0));
        assertArrayEquals(new int[] { 1, 3 }, GhostCells.select(c, null, 0.5, 0.5, 0.5, 100, 2));
        assertArrayEquals(new int[] { 1 }, GhostCells.select(c, null, 0.5, 0.5, 0.5, 100, 1));
        assertEquals(0, GhostCells.select(c, null, 0.5, 0.5, 0.5, 100, 0).length);
        assertEquals(0, GhostCells.select(c, null, 0.5, 0.5, 0.5, 100, -5).length);
    }

    @Test
    void theRangeIsToTheCellCentre() {
        GhostCells.Cells c = cells(hard(2, 0, 0), hard(3, 0, 0), hard(10, 0, 0));
        // centres at 2.5, 3.5, 10.5 from a viewer at 0.5 across
        assertArrayEquals(new int[] { 0, 1 }, GhostCells.select(c, null, 0.5, 0.5, 0.5, 3.0, 10));
        assertArrayEquals(new int[] { 0 }, GhostCells.select(c, null, 0.5, 0.5, 0.5, 2.99, 10));
        assertEquals(0, GhostCells.select(c, null, 0.5, 0.5, 0.5, 1.0, 10).length);
        assertEquals(0, GhostCells.select(c, null, 0.5, 0.5, 0.5, Double.NaN, 10).length);
    }

    @Test
    void doneCellsAndAirAreLeftOut() {
        GhostCells.Cells c = cells(
            hard(0, 0, 0),
            new int[] { 1, 0, 0, Parts.AIR, 0, BuildPlan.AIR },
            new int[] { 2, 0, 0, DECK, 0, BuildPlan.AIR },
            new int[] { 3, 0, 0, Parts.AIR, 0, BuildPlan.HARD },
            new int[] { 4, 0, 0, DECK, 0, BuildPlan.SOFT },
            hard(5, 0, 0));
        BitSet done = new BitSet();
        done.set(5);
        assertArrayEquals(new int[] { 0, 4 }, GhostCells.select(c, done, 0.5, 0.5, 0.5, 100, 100));
        assertArrayEquals(new int[] { 0, 4, 5 }, GhostCells.order(c, 0.5, 0.5, 0.5));
        assertFalse(c.drawable(1));
        assertFalse(c.drawable(2));
        assertFalse(c.drawable(3));
        assertTrue(c.drawable(4));
    }

    @Test
    void twoTouchingGhostsShareNoFace() {
        GhostCells.Cells c = cells(hard(0, 0, 0), hard(1, 0, 0));
        int[] sel = GhostCells.select(c, null, 0.5, 0.5, 0.5, 100, 100);
        int[] faces = GhostCells.faces(c, sel, null);
        assertEquals(10, faces.length);
        for (int f : faces) {
            int i = sel[GhostCells.cell(f)], side = GhostCells.side(f);
            // the east face of cell 0 and the west face of cell 1 are the shared ones
            assertFalse(i == 0 && side == 5, "cell 0 east");
            assertFalse(i == 1 && side == 4, "cell 1 west");
        }
        // a lone cell shows all six; three in a row show 14
        assertEquals(6, GhostCells.faces(c, new int[] { 0 }, null).length);
        GhostCells.Cells row = cells(hard(0, 0, 0), hard(1, 0, 0), hard(2, 0, 0));
        assertEquals(14, GhostCells.faces(row, new int[] { 0, 1, 2 }, null).length);
    }

    @Test
    void aFaceAgainstAnUnselectedGhostStays() {
        GhostCells.Cells c = cells(hard(0, 0, 0), hard(1, 0, 0));
        BitSet done = new BitSet();
        done.set(1);
        int[] sel = GhostCells.select(c, done, 0.5, 0.5, 0.5, 100, 100);
        assertArrayEquals(new int[] { 0 }, sel);
        assertEquals(6, GhostCells.faces(c, sel, null).length);
    }

    @Test
    void opaqueNeighboursHideFaces() {
        GhostCells.Cells c = cells(hard(0, 0, 0));
        // the ground under it and a wall to the north
        int[] faces = GhostCells.faces(c, new int[] { 0 }, (x, y, z) -> y < 0 || z < 0);
        assertEquals(4, faces.length);
        Set<Integer> sides = new HashSet<>();
        for (int f : faces) sides.add(GhostCells.side(f));
        assertEquals(new HashSet<>(Arrays.asList(1, 3, 4, 5)), sides);
        // the predicate is asked about neighbours only, never about a selected cell
        GhostCells.Cells two = cells(hard(0, 0, 0), hard(0, 1, 0));
        GhostCells.faces(two, new int[] { 0, 1 }, (x, y, z) -> {
            assertFalse(x == 0 && z == 0 && (y == 0 || y == 1), "asked about a ghost cell");
            return false;
        });
    }

    @Test
    void facesComeCellByCellInSideOrder() {
        GhostCells.Cells c = cells(hard(0, 0, 0), hard(5, 0, 0));
        int[] faces = GhostCells.faces(c, new int[] { 1, 0 }, null);
        assertEquals(12, faces.length);
        for (int k = 0; k < 12; k++) {
            assertEquals(k / 6, GhostCells.cell(faces[k]));
            assertEquals(k % 6, GhostCells.side(faces[k]));
        }
    }

    @Test
    void theLowestLayerOfAStage() {
        GhostCells.Cells c = cells(
            new int[] { 0, 3, 0, DECK, 1, BuildPlan.HARD },
            new int[] { 4, 2, 0, DECK, 1, BuildPlan.HARD },
            new int[] { 1, 2, 0, DECK, 1, BuildPlan.HARD },
            new int[] { 9, 1, 0, DECK, 2, BuildPlan.HARD },
            new int[] { 2, 2, 0, DECK, 1, BuildPlan.HARD },
            new int[] { 0, 0, 0, Parts.AIR, 1, BuildPlan.AIR },
            new int[] { 7, 1, 0, DECK, 1, BuildPlan.SOFT });
        BitSet done = new BitSet();
        done.set(6);
        GhostCells.Layer l = GhostCells.lowestLayer(c, done, 1, 0.5, 2.5, 0.5, 512);
        assertTrue(l.exists());
        assertEquals(2, l.y);
        assertArrayEquals(new int[] { 2, 4, 1 }, l.cells);
        assertArrayEquals(new int[] { 2, 4 }, GhostCells.lowestLayer(c, done, 1, 0.5, 2.5, 0.5, 2).cells);
        // without the done set the SOFT cell at y 1 is the layer
        GhostCells.Layer all = GhostCells.lowestLayer(c, null, 1, 0.5, 2.5, 0.5, 512);
        assertEquals(1, all.y);
        assertArrayEquals(new int[] { 6 }, all.cells);
        assertEquals(1, GhostCells.lowestLayer(c, done, 2, 0, 0, 0, 512).y);
        GhostCells.Layer none = GhostCells.lowestLayer(c, done, 5, 0, 0, 0, 512);
        assertFalse(none.exists());
        assertEquals(Integer.MIN_VALUE, none.y);
        assertEquals(0, none.cells.length);
    }

    @Test
    void boundsOfTheDrawableCells() {
        GhostCells.Cells c = cells(
            new int[] { -3, 5, 2, DECK, 0, BuildPlan.HARD },
            new int[] { 4, 7, -1, DECK, 0, BuildPlan.SOFT },
            new int[] { 10, 9, 10, DECK, 2, BuildPlan.HARD },
            new int[] { 50, 0, 50, Parts.AIR, 2, BuildPlan.AIR },
            new int[] { 1, 1, 1, DECK, 7, BuildPlan.HARD });
        GhostCells.Bounds b = GhostCells.bounds(c, 3);
        assertArrayEquals(new int[] { -3, 1, -1, 10, 9, 10 }, b.all);
        assertEquals(3, b.stages.length);
        assertArrayEquals(new int[] { -3, 5, -1, 4, 7, 2 }, b.stages[0]);
        assertNull(b.stages[1]);
        assertArrayEquals(new int[] { 10, 9, 10, 10, 9, 10 }, b.stages[2]);
        GhostCells.Bounds empty = GhostCells.bounds(cells(), 2);
        assertNull(empty.all);
        assertNull(empty.stages[0]);
    }

    @Test
    void theOutlineOfAFootprint() {
        // a 2 x 2 square: four sides of two
        int[] sq = GhostCells.edge(new int[] { 0, 1, 0, 1 }, new int[] { 0, 0, 1, 1 });
        assertArrayEquals(new int[] { 0, 0, 2, 0, 0, 2, 2, 2, 0, 0, 0, 2, 2, 0, 2, 2 }, sq);
        // repeats change nothing
        assertArrayEquals(sq, GhostCells.edge(new int[] { 0, 1, 0, 1, 1, 0 }, new int[] { 0, 0, 1, 1, 0, 1 }));
        // a 3 x 3 ring round a hole: the outer square and the hole's
        List<Integer> xs = new ArrayList<>(), zs = new ArrayList<>();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            if (x == 0 && z == 0) continue;
            xs.add(x);
            zs.add(z);
        }
        int[] ring = GhostCells.edge(
            xs.stream()
                .mapToInt(Integer::intValue)
                .toArray(),
            zs.stream()
                .mapToInt(Integer::intValue)
                .toArray());
        assertEquals(8 * 4, ring.length);
        int length = 0;
        for (int k = 0; k < ring.length; k += 4) {
            assertTrue(ring[k] <= ring[k + 2] && ring[k + 1] <= ring[k + 3]);
            length += ring[k + 2] - ring[k] + ring[k + 3] - ring[k + 1];
        }
        assertEquals(4 * 3 + 4 * 1, length);
        assertEquals(0, GhostCells.edge(new int[0], new int[0]).length);
    }

    @Test
    void anOutlineRunsAcrossZeroAsOneSegment() {
        int[] xs = new int[6], zs = new int[6];
        for (int i = 0; i < 6; i++) xs[i] = i - 3;
        int[] e = GhostCells.edge(xs, zs);
        assertArrayEquals(new int[] { -3, 0, 3, 0, -3, 1, 3, 1, -3, 0, -3, 1, 3, 0, 3, 1 }, e);
    }

    @Test
    void theIndexFindsSteps() {
        GhostCells.Cells c = cells(hard(0, 64, 0), hard(-30000000, 255, 29999999), hard(5, -1, -5));
        GhostCells.Index idx = new GhostCells.Index(c);
        assertEquals(0, idx.find(0, 64, 0));
        assertEquals(1, idx.find(-30000000, 255, 29999999));
        assertEquals(2, idx.find(5, -1, -5));
        assertEquals(-1, idx.find(0, 65, 0));
        assertEquals(-1, idx.find(5, -1, 5));
    }

    @Test
    void theSelectionMatchesAFullSort() {
        Random r = new Random(7);
        for (int round = 0; round < 300; round++) {
            int n = 1 + r.nextInt(400);
            long[] a = new long[n];
            for (int i = 0; i < n; i++) a[i] = GhostCells.key(r.nextInt(20), i);
            long[] sorted = a.clone();
            Arrays.sort(sorted);
            int k = 1 + r.nextInt(n);
            GhostCells.selectSmallest(a, n, k);
            long[] front = Arrays.copyOf(a, k);
            Arrays.sort(front);
            assertArrayEquals(Arrays.copyOf(sorted, k), front, "round " + round);
        }
    }

    @Test
    void theSameInputGivesTheSameOutput() {
        GhostCells.Cells c = big(5000, 3);
        BitSet done = new BitSet();
        for (int i = 0; i < 5000; i += 3) done.set(i);
        int[] a = GhostCells.select(c, done, 3.2, 70.1, -8.7, 48, 900);
        int[] b = GhostCells.select(c, done, 3.2, 70.1, -8.7, 48, 900);
        assertArrayEquals(a, b);
        assertEquals(900, a.length);
        GhostCells.Opaque some = (x, y, z) -> (x ^ z) % 5 == 0;
        assertArrayEquals(GhostCells.faces(c, a, some), GhostCells.faces(c, b, some));
        // the capped selection is the head of the uncapped one
        int[] all = GhostCells.select(c, done, 3.2, 70.1, -8.7, 48, Integer.MAX_VALUE);
        assertArrayEquals(Arrays.copyOf(all, 900), a);
        for (int k = 1; k < all.length; k++) {
            double d0 = d2(c, all[k - 1], 3.2, 70.1, -8.7), d1 = d2(c, all[k], 3.2, 70.1, -8.7);
            assertTrue(d0 <= d1 + 1.0 / GhostCells.DIST_SCALE, "nearest first at " + k);
        }
    }

    private static double d2(GhostCells.Cells c, int i, double vx, double vy, double vz) {
        double dx = c.x[i] + 0.5 - vx, dy = c.y[i] + 0.5 - vy, dz = c.z[i] + 0.5 - vz;
        return dx * dx + dy * dy + dz * dz;
    }

    /** The creases of every cell selected, as geometric unit edges "x0,y0,z0>x1,y1,z1"; fails on a repeated edge. */
    private static Set<String> creaseEdges(GhostCells.Cells c, GhostCells.Opaque opaque) {
        int[] sel = new int[c.size()];
        for (int i = 0; i < sel.length; i++) sel[i] = i;
        int[] faces = GhostCells.faces(c, sel, opaque);
        int[] creases = GhostCells.creases(c, sel, faces);
        Set<String> out = new HashSet<>();
        float[] e = new float[6];
        for (int k : creases) {
            int f = faces[GhostCells.creaseFace(k)], i = sel[GhostCells.cell(f)];
            GhostCells.creaseEnds(c.x[i], c.y[i], c.z[i], GhostCells.side(f), GhostCells.creaseDir(k), e, 0);
            float len = Math.abs(e[3] - e[0]) + Math.abs(e[4] - e[1]) + Math.abs(e[5] - e[2]);
            assertEquals(1f, len, 1e-6f, "a crease is one block long");
            String key = (int) e[0] + ","
                + (int) e[1]
                + ","
                + (int) e[2]
                + ">"
                + (int) e[3]
                + ","
                + (int) e[4]
                + ","
                + (int) e[5];
            assertTrue(out.add(key), "the edge " + key + " is given once");
        }
        return out;
    }

    @Test
    void aLoneCellHasTwelveCreases() {
        Set<String> e = creaseEdges(cells(hard(0, 0, 0)), null);
        assertEquals(12, e.size());
        assertTrue(e.contains("0,1,0>1,1,0"), "the top's north edge");
        assertTrue(e.contains("1,0,1>1,1,1"), "the south-east upright");
    }

    @Test
    void creasesSkipTheSeamOfAFlatRun() {
        // a 2 x 1 x 1 bar: its four long edges in two pieces each and the eight edges of its ends; not the seam
        Set<String> e = creaseEdges(cells(hard(0, 0, 0), hard(1, 0, 0)), null);
        assertEquals(16, e.size());
        assertFalse(e.contains("1,1,0>1,1,1"), "the seam across the top runs on flat");
        assertFalse(e.contains("1,0,0>1,1,0"), "the seam down the north side runs on flat");
        assertTrue(e.contains("1,1,0>2,1,0"));
    }

    @Test
    void aConcaveCornerIsOneCrease() {
        // an L of three cells standing in the x-y plane: an L-shaped outline front and back (8 edges each) and six
        // uprights along z, the inner corner's among them
        Set<String> e = creaseEdges(cells(hard(1, 1, 0), hard(0, 0, 0), hard(1, 0, 0)), null);
        assertEquals(22, e.size());
        assertTrue(e.contains("1,1,0>1,1,1"), "the inner corner");
    }

    @Test
    void aFloorOnTheGroundIsOutlinedRoundItsRim() {
        // a 3 x 3 floor lying on opaque ground (so no bottom faces): the rim of its top (12), the foot of its sides
        // against the ground (12) and its four uprights; nothing inside
        List<int[]> rows = new ArrayList<>();
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) rows.add(hard(x, 0, z));
        Set<String> e = creaseEdges(cells(rows.toArray(new int[0][])), (x, y, z) -> y < 0);
        assertEquals(12 + 12 + 4, e.size());
        assertFalse(e.contains("1,1,1>2,1,1"), "nothing across the middle of the floor");
        assertTrue(e.contains("0,0,0>1,0,0"), "the foot of the north side, against the ground");
    }

    @Test
    void thirtyThousandCellsAreQuick() {
        GhostCells.Cells c = big(30_000, 11);
        BitSet done = new BitSet();
        for (int i = 0; i < 30_000; i += 7) done.set(i);
        GhostCells.Opaque ground = (x, y, z) -> y < 64;
        // warm up, then time the best of a few runs (a slow test machine must not fail it)
        long best = Long.MAX_VALUE;
        int[] sel = null;
        for (int run = 0; run < 8; run++) {
            long t0 = System.nanoTime();
            sel = GhostCells.select(c, done, 0.5, 70, 0.5, 96, 6000);
            GhostCells.creases(c, sel, GhostCells.faces(c, sel, ground));
            GhostCells.lowestLayer(c, done, 3, 0.5, 70, 0.5, 512);
            best = Math.min(best, System.nanoTime() - t0);
        }
        assertEquals(6000, sel.length);
        assertTrue(best < 250_000_000L, "selecting from 30,000 cells took " + best / 1_000_000 + " ms");
        long t0 = System.nanoTime();
        GhostCells.order(c, 0.5, 70, 0.5);
        new GhostCells.Index(c);
        GhostCells.bounds(c, 9);
        assertTrue(System.nanoTime() - t0 < 500_000_000L, "ordering and indexing 30,000 cells took too long");
    }
}
