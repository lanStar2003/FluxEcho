package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VisChargeTest {

    @Test
    void fillsEverythingWhenTheBudgetAllows() {
        VisCharge c = VisCharge.plan(new int[] { 1000, 0, 500, 2500, 0, 0 }, 1, 128, 2400);
        assertArrayEquals(new int[] { 1000, 0, 500, 2500, 0, 0 }, c.add);
        assertEquals(4000, c.eu);
        assertEquals(32, c.ticks); // 4000 EU at 128 EU/t
        assertTrue(c.eut <= 128);
        assertTrue((long) c.eut * c.ticks >= c.eu);
    }

    @Test
    void sharesACappedBudgetEvenly() {
        // budget 100 * 10 / 1 = 1000 cv over three primals
        VisCharge c = VisCharge.plan(new int[] { 100, 5000, 5000 }, 1, 100, 10);
        assertArrayEquals(new int[] { 100, 450, 450 }, c.add);
        assertEquals(1000, c.total());
        assertEquals(10, c.ticks);
    }

    @Test
    void euPerCentivisScalesTheCost() {
        VisCharge c = VisCharge.plan(new int[] { 300 }, 4, 128, 2400);
        assertEquals(1200, c.eu);
        assertEquals(10, c.ticks);
        assertEquals(120, c.eut);
    }

    @Test
    void fullWandNeedsNoEnergy() {
        VisCharge c = VisCharge.plan(new int[] { 0, 0, 0, 0, 0, 0 }, 1, 128, 2400);
        assertEquals(0, c.total());
        assertEquals(0, c.eu);
        assertEquals(1, c.ticks);
    }
}
