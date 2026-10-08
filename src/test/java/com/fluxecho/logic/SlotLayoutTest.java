package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SlotLayoutTest {

    /** The GUI's work area: y 24 to 83, inputs from x 31, outputs up to x 145, the machine's work between. */
    private static final int TOP = 24, HEIGHT = 59, IN_X = 31, OUT_X = 145;

    @Test
    void singleSlotsAreCentred() {
        assertArrayEquals(new int[] { 31, 44 }, SlotLayout.grid(1, IN_X, TOP, HEIGHT, false)[0]);
        assertArrayEquals(new int[] { 127, 44 }, SlotLayout.grid(1, OUT_X, TOP, HEIGHT, true)[0]);
    }

    @Test
    void everyMachineFitsWithRoomForItsWork() {
        // inputs and outputs of the echo machines: imprinters, incubator, assembler, essentia, prey, infusion
        int[][] machines = { { 1, 1 }, { 1, 2 }, { 9, 1 }, { 2, 1 }, { 1, 4 } };
        for (int[] m : machines) {
            int[][] in = SlotLayout.grid(m[0], IN_X, TOP, HEIGHT, false);
            int[][] out = SlotLayout.grid(m[1], OUT_X, TOP, HEIGHT, true);
            for (int[][] slots : new int[][][] { in, out }) for (int[] s : slots) {
                assertTrue(s[1] >= TOP - 1 && s[1] + SlotLayout.SLOT <= TOP + HEIGHT + 1, "inside the area");
                for (int[] o : slots)
                    if (o != s) assertTrue(Math.abs(o[0] - s[0]) >= 18 || Math.abs(o[1] - s[1]) >= 18, "no overlap");
            }
            assertTrue(SlotLayout.left(out) - SlotLayout.right(in) >= 30, m[0] + " to " + m[1] + ": room for the work");
            assertTrue(SlotLayout.right(out) <= OUT_X, "outputs stay left of the buttons");
        }
    }
}
