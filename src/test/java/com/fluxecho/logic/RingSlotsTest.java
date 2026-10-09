package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RingSlotsTest {

    @Test
    void slotZeroIsAheadAndTheRestGoClockwise() {
        // facing north (0, -1): slot 0 north, slot 2 east, slot 4 south, slot 6 west
        assertArrayEquals(new int[] { 0, -32 }, RingSlots.offset(0, 32, 0, -1));
        assertArrayEquals(new int[] { 32, 0 }, RingSlots.offset(2, 32, 0, -1));
        assertArrayEquals(new int[] { 0, 32 }, RingSlots.offset(4, 32, 0, -1));
        assertArrayEquals(new int[] { -32, 0 }, RingSlots.offset(6, 32, 0, -1));
        assertArrayEquals(new int[] { 23, -23 }, RingSlots.offset(1, 32, 0, -1), "north-east");
        // facing east: slot 0 east, slot 2 south
        assertArrayEquals(new int[] { 32, 0 }, RingSlots.offset(0, 32, 1, 0));
        assertArrayEquals(new int[] { 0, 32 }, RingSlots.offset(2, 32, 1, 0));
    }

    @Test
    void everySlotFindsItself() {
        int[][] fronts = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
        for (int[] f : fronts) for (int k = 0; k < RingSlots.SLOTS; k++) {
            int[] o = RingSlots.offset(k, 32, f[0], f[1]);
            assertEquals(k, RingSlots.slotAt(o[0], 0, o[1], 32, f[0], f[1]));
            assertEquals(k, RingSlots.slotAt(o[0] + 3, -6, o[1] - 3, 32, f[0], f[1]), "within the tolerance");
            assertEquals(-1, RingSlots.slotAt(o[0] + 4, 0, o[1], 32, f[0], f[1]), "too far off");
            assertEquals(-1, RingSlots.slotAt(o[0], 7, o[1], 32, f[0], f[1]), "too high");
        }
        assertEquals(-1, RingSlots.slotAt(0, 0, 0, 32, 0, -1), "the nexus itself");
    }

    @Test
    void slotsKeepApartAndClearTheNexus() {
        for (int k = 0; k < RingSlots.SLOTS; k++) {
            int[] a = RingSlots.offset(k, 32, 0, -1), b = RingSlots.offset(k + 1, 32, 0, -1);
            // modules up to 21 wide (10 either side of the centre) on neighbouring slots do not overlap
            double gap = Math.max(Math.abs(a[0] - b[0]), Math.abs(a[1] - b[1]));
            assertTrue(gap >= 21, "slots " + k + " and " + (k + 1) + ": " + gap);
            double clear = Math.max(Math.abs(a[0]), Math.abs(a[1])) - 10 - RingSlots.TOLERANCE;
            assertTrue(clear > NexusShape.RADIUS + 1, "slot " + k + " keeps clear of the base");
        }
    }

    @Test
    void openSlotsByPhase() {
        assertEquals(0, RingSlots.open(0));
        assertEquals(2, RingSlots.open(1));
        assertEquals(4, RingSlots.open(2));
        assertEquals(8, RingSlots.open(3));
    }
}
