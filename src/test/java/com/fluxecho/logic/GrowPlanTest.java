package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.BuildPlan.Plan;
import com.fluxecho.logic.BuildPlan.Step;

/**
 * The nexus grows (0.12.0, CITY.md §3): the growth job of each phase holds exactly the cells that phase's blueprint
 * adds to the phase below, bottom-up, the lift pad last at phase II (where it replaces the lit hub); its clearing is
 * the base's columns over the new stage, never graded; its keys parse back; and the phase-I establish plan is the one
 * of 0.11.0 (no pad in it).
 */
class GrowPlanTest {

    private static final int[][] FACINGS = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
    private static final int CX = 40, Y0 = 70, CZ = 12;

    /** The controller of a nexus centred at (CX, Y0, CZ) facing f: on the front rim of the base, two above it. */
    private static int[] ctrl(int[] f) {
        return new int[] { CX + NexusShape.RADIUS * f[0], Y0 + NexusShape.CONTROLLER_UP,
            CZ + NexusShape.RADIUS * f[1] };
    }

    private static long pos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static Set<Long> cells(Blueprint b, int[] c, int[] f) {
        Set<Long> out = new HashSet<>();
        for (Blueprint.Cell cell : b.cells()) {
            if (cell.ch == '~') continue;
            int[] w = b.world(cell.a, cell.b, cell.c, c[0], c[1], c[2], f[0], f[1]);
            out.add(pos(w[0], w[1], w[2]) * 128 + cell.ch);
        }
        return out;
    }

    @Test
    void eachGrowthAddsItsStage() {
        for (int[] f : FACINGS) for (int p = 2; p <= NexusShape.PHASES; p++) {
            int[] c = ctrl(f);
            Plan plan = BuildPlan.grow(p, CX, Y0, CZ, f[0], f[1], c[0], c[1], c[2]);
            assertEquals(BuildPlan.growKey(p), plan.key);
            assertEquals(BuildPlan.E_STAGES, plan.stages);
            assertEquals(plan.steps.size(), plan.count(BuildPlan.E_CORE), "every step in CORE");
            // what the phase's blueprint has that the phase below has not, cell and character
            Set<Long> want = cells(NexusShape.phase(p), c, f), below = cells(NexusShape.phase(p - 1), c, f);
            want.removeAll(below);
            Set<Long> got = new HashSet<>();
            Set<Long> at = new HashSet<>();
            for (Step s : plan.steps) {
                assertEquals(BuildPlan.HARD, s.kind);
                assertTrue(at.add(pos(s.x, s.y, s.z)), "a cell twice");
                char ch = s.part == Parts.fitting(Parts.F_LIFT) ? NexusShape.LIFT : charOf(s.part);
                got.add(pos(s.x, s.y, s.z) * 128 + ch);
            }
            assertEquals(want, got, "phase " + p + " front " + f[0] + "," + f[1]);
            // bottom-up, the pad last at phase II
            Step last = plan.steps.get(plan.steps.size() - 1);
            if (p == NexusShape.LIFT_FROM) {
                assertEquals(Parts.fitting(Parts.F_LIFT), last.part);
                assertTrue(last.at(CX, Y0, CZ), "the pad in the middle of the base");
            }
            int y = Integer.MIN_VALUE;
            for (Step s : plan.steps) {
                if (s.part == Parts.fitting(Parts.F_LIFT)) continue;
                assertTrue(s.y >= y);
                y = s.y;
            }
            // clearing: the base's columns over the stage and two more, not graded
            assertTrue(!plan.clear.isEmpty());
            for (BuildPlan.Column col : plan.clear) {
                assertTrue(!col.grade);
                assertEquals(Y0 + NexusShape.STAGE * (p - 1) + 1, col.yFrom);
                assertEquals(Y0 + NexusShape.STAGE * p + 2, col.yTo);
                int[] l = CampusPlan.toLocal(col.x - CX, col.z - CZ, f[0], f[1]);
                assertTrue(CampusPlan.inBase(l[0], l[1]));
            }
        }
    }

    private static char charOf(int part) {
        if (part == Parts.frame(Parts.FR_PILLAR)) return NexusShape.PILLAR;
        if (part == Parts.frame(Parts.FR_CONDUIT)) return NexusShape.CONDUIT;
        if (part == Parts.frame(Parts.FR_RING)) return NexusShape.RING;
        if (part == Parts.frame(Parts.FR_SEAT)) return NexusShape.SEAT;
        if (part == Parts.frame(Parts.FR_BASE)) return NexusShape.BASE;
        if (part == Parts.frame(Parts.FR_BASE_LIT)) return NexusShape.LIT;
        if (part == Parts.frame(Parts.FR_CONSOLE)) return NexusShape.CONSOLE;
        throw new AssertionError("part " + part);
    }

    @Test
    void theKeys() {
        assertEquals(BuildPlan.ESTABLISH, BuildPlan.establishKey(1));
        assertEquals("establish:2", BuildPlan.establishKey(2));
        assertEquals("establish:5", BuildPlan.establishKey(9));
        assertEquals(1, BuildPlan.establishPhase(BuildPlan.ESTABLISH));
        assertEquals(3, BuildPlan.establishPhase("establish:3"));
        assertEquals(0, BuildPlan.establishPhase(BuildPlan.FORUM));
        assertEquals(0, BuildPlan.establishPhase("grow:2"));
        assertEquals("grow:2", BuildPlan.growKey(2));
        assertEquals(2, BuildPlan.growPhase("grow:2"));
        assertEquals(0, BuildPlan.growPhase("grow:1"));
        assertEquals(0, BuildPlan.growPhase(BuildPlan.ESTABLISH));
        assertEquals(0, BuildPlan.growPhase(null));
        assertEquals(0, BuildPlan.growPhase("grow:x"));
    }

    @Test
    void phaseOneIsUnchanged() {
        assertEquals(0, NexusShape.PHASE_1.count(NexusShape.LIFT), "the lift pad is only in phase II and up");
        assertTrue(NexusShape.PHASE_1 == NexusShape.phase(1));
        assertThrows(IllegalArgumentException.class, () -> BuildPlan.grow(1, CX, Y0, CZ, 0, -1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> BuildPlan.grow(6, CX, Y0, CZ, 0, -1, 0, 0, 0));
    }
}
