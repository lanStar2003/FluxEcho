package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Which modules a nexus docks ({@link DockOrder}, the rank loop of {@code TileNexus.refreshDock}): a module on a
 * campus site always docks (0.14.1; up to 0.14.0 the fifth ring module of a phase-II campus stood undocked as
 * "ring_full"), a 0.9.2 module on the old ring only while the ring has room, a number is taken by the first module on
 * it, and the decisions go rank by rank, each rank in number order.
 */
class DockOrderTest {

    private static final int R = DockOrder.RECORDED, A = DockOrder.ADOPTED, L = DockOrder.LEGACY;

    /** The reasons by module index (null: it docks). */
    private static String[] reasons(int[] numbers, int[] ranks, int open) {
        String[] out = new String[numbers.length];
        boolean[] seen = new boolean[numbers.length];
        for (DockOrder.Verdict v : DockOrder.decide(numbers, ranks, open)) {
            assertTrue(!seen[v.index], "one decision a module");
            seen[v.index] = true;
            out[v.index] = v.reject;
        }
        for (boolean s : seen) assertTrue(s, "every module decided");
        return out;
    }

    @Test
    void campusModulesAlwaysDock() {
        // the whole city of 0.14.1 at phase II: eight ring sites and the two axis sites, all recorded
        int[] numbers = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 }, ranks = new int[10];
        for (int open : new int[] { 0, 3, 4, 8 }) {
            String[] why = reasons(numbers, ranks, open);
            for (int i = 0; i < why.length; i++) assertNull(why[i], "site " + numbers[i] + " with room " + open);
        }
        // the user's 0.13.0 campus (sites 2, 4, 5, 7 and the axis) once its hunting ground and mana fountain stand:
        // under 0.14.0's room of four (min(RingSlots.open(2), 7)) the two new pavilions were turned away
        int[] user = { 1, 2, 3, 4, 5, 7, 8, 9 }, userRanks = { A, R, A, R, R, R, R, R };
        String[] why = reasons(user, userRanks, Math.min(RingSlots.open(2), 7));
        for (int i = 0; i < why.length; i++) assertNull(why[i], "site " + user[i]);
    }

    @Test
    void aLegacyRingModuleWaitsForRoomAfterTheCampusModules() {
        // three campus ring modules and a 0.9.2 hall on slot 3: the ring's room is asked of the hall alone
        int[] numbers = { 3, 2, 4, 6 }, ranks = { L, R, R, R };
        assertEquals(DockOrder.RING_FULL, reasons(numbers, ranks, 3)[0]);
        assertNull(reasons(numbers, ranks, 4)[0]);
        assertEquals(DockOrder.RING_CLOSED, reasons(new int[] { 3 }, new int[] { L }, 0)[0]);
        // an axis module takes no room
        assertNull(reasons(new int[] { 8, 9, 1 }, new int[] { R, R, L }, 1)[2]);
    }

    @Test
    void theFirstModuleOnANumberTakesIt() {
        // a recorded module and a 0.9.2 module on the same number: the recorded one docks, the other is turned away
        String[] why = reasons(new int[] { 4, 4 }, new int[] { L, R }, 8);
        assertEquals(DockOrder.SLOT_TAKEN, why[0]);
        assertNull(why[1]);
        // the order of the decisions: rank by rank, each in number order
        List<DockOrder.Verdict> order = DockOrder.decide(new int[] { 7, 1, 5, 0 }, new int[] { L, A, R, A }, 8);
        int[] want = { 2, 3, 1, 0 };
        for (int i = 0; i < want.length; i++) assertEquals(want[i], order.get(i).index);
        assertThrows(IllegalArgumentException.class, () -> DockOrder.decide(new int[] { 1 }, new int[0], 8));
    }
}
