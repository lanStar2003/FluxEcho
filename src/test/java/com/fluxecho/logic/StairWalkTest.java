package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Walks both spiral stairs of the Echo Archive the way a player would: from the ground floor up the twelve steps in
 * order onto the gallery deck. Cells are modelled by their collision boxes (a tread is a lower half slab, a rail 1.5
 * high, everything else that is not air a full block). Each step may rise at most half a block (the player's auto
 * step), must leave at least two blocks of headroom above where the player stands, and, because the 1.8-high player
 * box still overlaps the lower column while it steps up, the lower column must also be clear from the new height for
 * the box's full height.
 */
class StairWalkTest {

    /** The player's collision box height. */
    private static final double PLAYER = 1.8;
    /** The cells around a stair post in walking order, as the generator lays the left stair. */
    private static final int[][] RING = { { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 }, { -1, 0 }, { -1, -1 }, { 0, -1 },
        { 1, -1 } };
    private static final int[][] POSTS = { { 2, 2 }, { 24, 2 } };
    private static final int STEPS = 12;
    /** The walking level of the ground floor and of the gallery deck. */
    private static final double FLOOR = 1.0, DECK = ArchiveShape.DECK_Y + 1.0;
    /** The layer of the ground floor's paving. */
    private static final int FLOOR_Y = 0;

    /** The steps of a stair as the generator places them, {x, y, z}; the right stair mirrors the left across x. */
    private static int[][] steps(int stair) {
        int cx = POSTS[stair][0], cz = POSTS[stair][1], mirror = cx > ArchiveShape.MID ? -1 : 1;
        int[][] out = new int[STEPS][];
        for (int i = 0; i < STEPS; i++) {
            double top = 1 + 0.5 * (i + 1);
            int y = top == Math.floor(top) ? (int) top - 1 : (int) top;
            out[i] = new int[] { cx + RING[i % 8][0] * mirror, y, cz + RING[i % 8][1] };
        }
        return out;
    }

    /** The collision span of a cell within its own block, {from, to} above its bottom; null for none. */
    private static double[] solid(char ch) {
        switch (ch) {
            case ' ':
            case '-':
                return null;
            case 't':
                return new double[] { 0, 0.5 };
            case 'r':
                return new double[] { 0, 1.5 };
            default:
                return new double[] { 0, 1 };
        }
    }

    /** Whether nothing solid overlaps the column {@code (x, z)} between the heights {@code lo} and {@code hi}. */
    private static boolean free(int x, int z, double lo, double hi) {
        for (int y = (int) Math.floor(lo) - 2; y <= (int) Math.ceil(hi); y++) {
            double[] s = solid(ArchiveShape.cell(x, y, z));
            if (s != null && y + s[0] < hi && y + s[1] > lo) return false;
        }
        return true;
    }

    /** Where a player stands in a column at the given block level: the top of its solid, or NaN when it cannot. */
    private static double standing(int x, int y, int z) {
        double[] s = solid(ArchiveShape.cell(x, y, z));
        return s == null ? Double.NaN : y + s[1];
    }

    @Test
    void theStairsAreWhereTheGeneratorPutsThem() {
        List<int[]> api = ArchiveShape.stairSteps();
        for (int stair = 0; stair < 2; stair++) {
            int[][] st = steps(stair);
            for (int i = 0; i < STEPS; i++) {
                assertArrayEquals(st[i], api.get(stair * STEPS + i));
                assertEquals(
                    i % 2 == 0 ? 't' : 'W',
                    ArchiveShape.cell(st[i][0], st[i][1], st[i][2]),
                    "step " + i + " of stair " + stair + ": treads and full blocks alternate");
            }
        }
    }

    @Test
    void leftStairIsWalkable() {
        walk(0);
    }

    @Test
    void rightStairIsWalkable() {
        walk(1);
    }

    private static void walk(int stair) {
        int[][] st = steps(stair);
        String name = stair == 0 ? "left stair" : "right stair";
        // the way on: a floor cell beside the first step that the nave connects to
        int[] from = approach(st[0]);
        assertNotNull(from, name + ": no floor cell beside the first step");
        double prev = FLOOR;
        for (int i = 0; i < STEPS; i++) {
            int[] c = st[i];
            String at = name + " step " + i + " at " + c[0] + "," + c[1] + "," + c[2];
            assertEquals(1, Math.abs(c[0] - from[0]) + Math.abs(c[2] - from[2]), at + ": a step beside the last");
            double h = standing(c[0], c[1], c[2]);
            assertEquals(1.5 + 0.5 * i, h, at);
            assertTrue(h - prev > 0 && h - prev <= 0.5, at + ": rises " + (h - prev));
            assertTrue(free(c[0], c[2], h, h + 2), at + ": less than two blocks of headroom");
            assertTrue(free(from[0], from[2], h, h + PLAYER), at + ": the player box hits the ceiling stepping up");
            prev = h;
            from = c;
        }
        assertEquals(DECK, prev, name + ": the last step is level with the gallery deck");
        // and off onto the deck
        boolean off = false;
        for (int[] d : RING) {
            if (d[0] != 0 && d[1] != 0) continue;
            int x = from[0] + d[0], z = from[2] + d[1];
            char ch = ArchiveShape.cell(x, ArchiveShape.DECK_Y, z);
            if ((ch == 'd' || ch == 'e') && free(x, z, DECK, DECK + 2)) off = true;
        }
        assertTrue(off, name + ": no gallery deck beside the last step");
    }

    /** A floor cell beside the first step, reachable over the ground floor from the nave, or null. */
    private static int[] approach(int[] first) {
        int w = ArchiveShape.WIDTH, d = ArchiveShape.DEPTH;
        boolean[] seen = new boolean[w * d];
        Deque<int[]> todo = new ArrayDeque<>();
        todo.add(new int[] { ArchiveShape.MID, 15 });
        seen[15 * w + ArchiveShape.MID] = true;
        while (!todo.isEmpty()) {
            int[] c = todo.poll();
            if (Math.abs(c[0] - first[0]) + Math.abs(c[1] - first[2]) == 1) {
                double h = standing(first[0], first[1], first[2]);
                if (free(c[0], c[1], h, h + PLAYER)) return new int[] { c[0], FLOOR_Y, c[1] };
            }
            for (int[] n : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
                int x = c[0] + n[0], z = c[1] + n[1];
                if (x < 0 || z < 0 || x >= w || z >= d || seen[z * w + x]) continue;
                if (standing(x, FLOOR_Y, z) != FLOOR || !free(x, z, FLOOR, FLOOR + 2)) continue;
                seen[z * w + x] = true;
                todo.add(new int[] { x, z });
            }
        }
        return null;
    }

    @Test
    void headroomOverEveryStepIsAtLeastTwo() {
        for (int stair = 0; stair < 2; stair++) for (int[] c : steps(stair)) {
            double h = standing(c[0], c[1], c[2]);
            int y = c[1] + 1;
            while (y < ArchiveShape.HEIGHT && solid(ArchiveShape.cell(c[0], y, c[2])) == null) y++;
            assertTrue(y - h >= 2, "headroom " + (y - h) + " at " + c[0] + "," + c[1] + "," + c[2]);
        }
    }
}
