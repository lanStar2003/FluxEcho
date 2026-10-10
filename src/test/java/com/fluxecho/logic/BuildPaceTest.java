package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BuildPaceTest {

    @Test
    void refillIsRateTimesPaceAndCapped() {
        BuildPace p = new BuildPace();
        assertEquals(0, p.budget());
        p.refill(2.0, 1.0);
        assertEquals(2.0, p.budget());
        p.refill(2.0, 1.5);
        assertEquals(5.0, p.budget());
        for (int t = 0; t < 100; t++) p.refill(2.0, 1.0);
        assertEquals(BuildPace.CAP, p.budget(), "a long stall never becomes a burst");
        assertEquals(24.0, BuildPace.CAP);
    }

    @Test
    void badRatesAddNothing() {
        BuildPace p = new BuildPace();
        p.refill(-2, 1);
        p.refill(2, 0);
        p.refill(Double.NaN, 1);
        p.refill(Double.POSITIVE_INFINITY, 1);
        p.refillClear(-1);
        p.refillClear(Double.NaN);
        assertEquals(0, p.budget());
        assertEquals(0, p.clearBudget());
    }

    @Test
    void aStepCostsOne() {
        BuildPace p = new BuildPace();
        p.refill(2.0, 0.25); // the ceremonial core: half a step per tick
        assertFalse(p.canStep());
        assertFalse(p.tryStep());
        assertEquals(0.5, p.budget(), "a failed launch spends nothing");
        p.refill(2.0, 0.25);
        assertTrue(p.tryStep());
        assertEquals(0, p.budget());
        // two per tick at pace 1: two steps, then none
        p.refill(2.0, 1.0);
        assertTrue(p.tryStep());
        assertTrue(p.tryStep());
        assertFalse(p.tryStep());
    }

    @Test
    void roundingNeverStarves() {
        BuildPace p = new BuildPace();
        for (int t = 0; t < 10; t++) p.refill(0.1, 1.0);
        assertTrue(p.tryStep(), "ten tenths make a step");
    }

    @Test
    void aGroupLaunchesOnlyWhenItFits() {
        BuildPace p = new BuildPace();
        for (int t = 0; t < 7; t++) p.refill(2.0, 1.0);
        assertEquals(14.0, p.budget());
        assertFalse(p.canGroup(15));
        assertFalse(p.tryGroup(15));
        assertEquals(14.0, p.budget(), "a group that does not fit spends nothing");
        assertTrue(p.tryStep(), "a single step still fits");
        p.refill(2.0, 1.0);
        p.refill(2.0, 1.0);
        assertEquals(17.0, p.budget());
        assertTrue(p.tryGroup(15));
        assertEquals(2.0, p.budget());
        assertTrue(p.tryGroup(0), "an empty group is free");
        assertEquals(2.0, p.budget());
    }

    @Test
    void groupsKeepTheAverageRate() {
        BuildPace p = new BuildPace();
        int launched = 0;
        for (int t = 0; t < 120; t++) {
            p.refill(2.0, 1.0);
            if (p.tryGroup(15)) launched++;
        }
        assertEquals(16, launched, "240 budget over 120 ticks makes 16 units of 15");
    }

    @Test
    void aGroupLargerThanTheCapWaitsForAFullBudget() {
        BuildPace p = new BuildPace();
        for (int t = 0; t < 11; t++) p.refill(2.0, 1.0);
        assertFalse(p.canGroup(30));
        p.refill(2.0, 1.0);
        assertEquals(BuildPace.CAP, p.budget());
        assertTrue(p.tryGroup(30), "it would never fit, so a full budget is enough");
        assertEquals(-6.0, p.budget(), "the rest is paid back by later refills");
        assertFalse(p.canStep());
        for (int t = 0; t < 3; t++) p.refill(2.0, 1.0);
        assertFalse(p.canStep());
        p.refill(2.0, 1.0);
        assertTrue(p.canStep());
    }

    @Test
    void clearingHasItsOwnBudget() {
        BuildPace p = new BuildPace();
        p.refillClear(8);
        assertEquals(0, p.budget(), "clearing does not feed placing");
        assertFalse(p.canStep());
        int cleared = 0;
        while (p.tryClear()) cleared++;
        assertEquals(8, cleared);
        assertFalse(p.canClear());
        p.refill(2.0, 1.0);
        assertTrue(p.tryStep());
        assertTrue(p.tryStep());
        assertEquals(0, p.clearBudget(), "placing does not spend the clear budget");
        for (int t = 0; t < 10; t++) p.refillClear(8);
        assertEquals(BuildPace.CAP, p.clearBudget());
        BuildPace fast = new BuildPace();
        for (int t = 0; t < 3; t++) fast.refillClear(32);
        assertEquals(32, fast.clearBudget(), "a rate above the cap still clears that many per tick");
    }

    @Test
    void loadAndReset() {
        BuildPace p = new BuildPace();
        p.load(10.5, 3);
        assertEquals(10.5, p.budget());
        assertEquals(3, p.clearBudget());
        p.load(100, 0);
        assertEquals(BuildPace.CAP, p.budget(), "a saved budget is capped too");
        p.reset();
        assertEquals(0, p.budget());
        assertEquals(0, p.clearBudget());
    }

    @Test
    void stagePaces() {
        assertEquals(0.25, BuildPace.establishPace(1), "the nexus core is laid ceremonially");
        assertEquals(1.5, BuildPace.establishPace(3), "the floor ripples out");
        for (int stage : new int[] { 0, 2, 4, 5, -1 }) assertEquals(1.0, BuildPace.establishPace(stage));
        assertEquals(1.5, BuildPace.modulePace(1), "the module's floor");
        for (int stage : new int[] { 0, 2, 3, 4, 5, 6, 7, 8, 9 }) assertEquals(1.0, BuildPace.modulePace(stage));
    }

    @Test
    void flight() {
        assertEquals(14, BuildPace.flightTicks(0));
        assertEquals(14, BuildPace.flightTicks(5.9));
        assertEquals(14, BuildPace.flightTicks(12));
        assertEquals(15, BuildPace.flightTicks(18));
        assertEquals(18, BuildPace.flightTicks(36));
        assertEquals(23, BuildPace.flightTicks(71.9));
        assertEquals(24, BuildPace.flightTicks(72));
        assertEquals(30, BuildPace.flightTicks(108));
        assertEquals(30, BuildPace.flightTicks(1000));
        assertEquals(14, BuildPace.flightTicks(-3));
        assertEquals(14, BuildPace.flightTicks(Double.NaN));
        assertEquals(30, BuildPace.flightTicks(Double.POSITIVE_INFINITY));
        assertEquals(18, BuildPace.flightTicks(36, 0, 0));
        assertEquals(20, BuildPace.flightTicks(0, -48, 0));
        assertEquals(14, BuildPace.flightTicks(3, 4, 0));
        for (double d = 0; d < 200; d += 0.25) {
            int t = BuildPace.flightTicks(d);
            assertTrue(t >= BuildPace.MIN_FLIGHT && t <= BuildPace.MAX_FLIGHT, "distance " + d);
            assertTrue(t >= BuildPace.flightTicks(d - 0.25), "never shorter for a farther cell");
        }
    }

    @Test
    void eta() {
        assertEquals(268, BuildPace.etaTicks(134, 2.0, 0.25), "the nexus core: about 13 s");
        assertEquals(1, BuildPace.etaTicks(3, 2.0, 1.5));
        assertEquals(2, BuildPace.etaTicks(4, 2.0, 1.5));
        assertEquals(10, BuildPace.etaTicks(1, 0.1, 1.0));
        assertEquals(0, BuildPace.etaTicks(0, 2.0, 1.0));
        assertEquals(0, BuildPace.etaTicks(-5, 0, 1.0), "nothing left needs no rate");
        assertEquals(-1, BuildPace.etaTicks(1, 0, 1.0), "a stopped rate never finishes");
        assertEquals(-1, BuildPace.etaTicks(1, Double.NaN, 1.0));
    }

    @Test
    void etaOfAWholeJob() {
        int[] establish = { 0, 134, 0, 925, 13 };
        // 268 (core) + 309 (floor at 3/t) + 7 (fixtures at 2/t) + 13 (100 clears at 8/t)
        assertEquals(597, BuildPace.etaTicks(establish, true, 2.0, 100, 8));
        int[] module = { 0, 1165, 1200 };
        // 389 (floor at 3/t) + 600 (shell at 2/t), nothing to clear
        assertEquals(989, BuildPace.etaTicks(module, false, 2.0, 0, 0));
        assertEquals(-1, BuildPace.etaTicks(module, false, 0, 0, 8), "no placing rate");
        assertEquals(-1, BuildPace.etaTicks(new int[0], true, 2.0, 5, 0), "no clearing rate");
        assertEquals(0, BuildPace.etaTicks(null, true, 2.0, 0, 0));
    }
}
