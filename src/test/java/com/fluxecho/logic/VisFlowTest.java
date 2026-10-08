package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VisFlowTest {

    @Test
    void eachPrimalTakesUpToTheRate() {
        VisFlow f = VisFlow.step(new int[] { 1000, 10, 0, 500, 25, 1000 }, 25, 1, 1_000_000);
        assertArrayEquals(new int[] { 25, 10, 0, 25, 25, 25 }, f.add);
        assertEquals(110, f.eu);
    }

    @Test
    void euPerCentivisScalesTheCost() {
        VisFlow f = VisFlow.step(new int[] { 100, 100 }, 50, 3, 1_000_000);
        assertEquals(100, f.total());
        assertEquals(300, f.eu);
    }

    @Test
    void shortOfEnergyTheFlowIsSharedEvenly() {
        // 60 EU at 1 EU/cv: 6 primals wanting 25 each get 10 each
        VisFlow f = VisFlow.step(new int[] { 1000, 1000, 1000, 1000, 1000, 1000 }, 25, 1, 60);
        assertArrayEquals(new int[] { 10, 10, 10, 10, 10, 10 }, f.add);
        assertEquals(60, f.eu);
    }

    @Test
    void anAlmostFullPrimalLeavesItsShareToTheOthers() {
        VisFlow f = VisFlow.step(new int[] { 2, 1000, 1000 }, 25, 1, 32);
        assertArrayEquals(new int[] { 2, 15, 15 }, f.add);
        assertEquals(32, f.eu);
    }

    @Test
    void noEnergyNoFlow() {
        VisFlow f = VisFlow.step(new int[] { 1000, 1000 }, 25, 1, 0);
        assertEquals(0, f.total());
        assertEquals(0, f.eu);
    }

    @Test
    void fullWandTakesNothing() {
        VisFlow f = VisFlow.step(new int[] { 0, 0, 0, 0, 0, 0 }, 25, 1, 1000);
        assertEquals(0, f.total());
        assertEquals(0, f.eu);
    }

    @Test
    void neverSpendsMoreThanItHas() {
        for (long have = 0; have < 400; have += 7) {
            VisFlow f = VisFlow.step(new int[] { 90, 3, 1000, 47, 0, 25 }, 50, 2, have);
            assertEquals(true, f.eu <= have);
        }
    }

    @Test
    void rateAddsModules() {
        assertEquals(25, VisFlow.rate(25, 50, 0));
        assertEquals(225, VisFlow.rate(25, 50, 4));
        assertEquals(Integer.MAX_VALUE, VisFlow.rate(Integer.MAX_VALUE, Integer.MAX_VALUE, 4));
    }
}
