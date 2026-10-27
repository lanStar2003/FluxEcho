package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The formed nexus walked now that its ribs and rings are solid (0.17.0, style §4.1: the white ribs): from the plaza
 * round the dais, flush with its top, someone walks onto the base, reaches the controller at eye height and stands on
 * the light lift's pad in the middle; no part of the base is closed in by the ribs; and the lift's column in the middle
 * is clear from the base to the top of the fifth stage, so the lift still rides through the middle.
 * <p>
 * A cell lets a player through if it is empty or dissolves once the nexus forms ({@link NexusShape#dissolves}); every
 * other part blocks a whole cell (the rib posts are thinner, so this is the stricter reading).
 */
class NexusWalkTest {

    private static final int R = NexusShape.RADIUS;

    /** Whether a player passes through the part at a point of a phase (outside the blueprint: open air). */
    private static boolean passes(int phase, int y, int dx, int dz) {
        if (Math.abs(dx) > R || Math.abs(dz) > R || y < 0) return true;
        char ch = NexusShape.cell(phase, y, dx, dz);
        return ch == ' ' || NexusShape.dissolves(ch);
    }

    /** Whether someone can stand with their feet at y 1 (on the base or on the plaza flush with it) at a point. */
    private static boolean stands(int phase, int dx, int dz) {
        return passes(phase, 1, dx, dz) && passes(phase, 2, dx, dz);
    }

    /** Every point (dx, dz) reached walking at the base's top from the plaza one block round it. */
    private static Set<Long> walk(int phase) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> open = new ArrayDeque<>();
        for (int dz = -R - 1; dz <= R + 1; dz++) for (int dx = -R - 1; dx <= R + 1; dx++) {
            if (NexusShape.inBase(dx, dz)) continue;
            if (seen.add(key(dx, dz))) open.add(new int[] { dx, dz });
        }
        int[][] steps = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        while (!open.isEmpty()) {
            int[] p = open.poll();
            for (int[] s : steps) {
                int x = p[0] + s[0], z = p[1] + s[1];
                if (Math.abs(x) > R + 1 || Math.abs(z) > R + 1 || !stands(phase, x, z)) continue;
                if (seen.add(key(x, z))) open.add(new int[] { x, z });
            }
        }
        return seen;
    }

    private static long key(int dx, int dz) {
        return (long) dx << 32 | (dz & 0xFFFFFFFFL);
    }

    @Test
    void theRibsAndRingsAreSolidNow() {
        for (int p = 1; p <= NexusShape.PHASES; p++) {
            assertFalse(stands(p, 3, 3), "phase " + p + ": a rib stands on the diagonal");
            assertFalse(passes(p, 3, 2, 2), "phase " + p + ": the rib leans in");
            assertFalse(passes(p, 5, 3, 0), "phase " + p + ": the ring");
        }
    }

    @Test
    void fromTheDaisEdgeToTheControllerAndTheLift() {
        for (int p = 1; p <= NexusShape.PHASES; p++) {
            Set<Long> reached = walk(p);
            // the controller over its console at the front (0, -5), at eye height: stood beside from the base
            boolean controller = reached.contains(key(-1, -5)) || reached.contains(key(1, -5))
                || reached.contains(key(0, -4));
            assertTrue(controller, "phase " + p + ": the controller is reached from the base");
            assertTrue(reached.contains(key(0, 0)), "phase " + p + ": the middle, the lift's pad from phase II");
            // nothing on the base is closed in: every point one can stand on is reached
            List<String> pockets = new ArrayList<>();
            for (int dz = -R; dz <= R; dz++) for (int dx = -R; dx <= R; dx++) {
                if (NexusShape.inBase(dx, dz) && stands(p, dx, dz) && !reached.contains(key(dx, dz)))
                    pockets.add(dx + "," + dz);
            }
            assertTrue(pockets.isEmpty(), "phase " + p + ": closed in by the ribs: " + pockets);
        }
    }

    @Test
    void theLiftsColumnIsClearToTheTop() {
        int p = NexusShape.PHASES;
        assertEquals(NexusShape.LIFT, NexusShape.cell(p, 0, 0, 0), "the pad in the base's middle");
        for (int y = 1; y <= NexusShape.STAGE * p; y++) assertTrue(passes(p, y, 0, 0), "the column at y " + y);
    }
}
