package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BloodRatesTest {

    private static final int[] TABLE = { 4, 8, 16, 32, 48, 64 };

    @Test
    void orbLevelPicksTheRate() {
        assertEquals(4, BloodRates.lpPerTick(TABLE, 1));
        assertEquals(32, BloodRates.lpPerTick(TABLE, 4));
        assertEquals(64, BloodRates.lpPerTick(TABLE, 6));
        // addon orbs above 6 and broken ones below 1 are clamped
        assertEquals(64, BloodRates.lpPerTick(TABLE, 9));
        assertEquals(4, BloodRates.lpPerTick(TABLE, 0));
        assertEquals(0, BloodRates.lpPerTick(new int[0], 3));
    }

    @Test
    void highestRateStaysWithinMv() {
        assertEquals(128, BloodRates.lpPerTick(TABLE, 6) * 2);
    }

    @Test
    void meatCoversWhatTheCreditDoesNot() {
        assertEquals(0, BloodRates.meatNeeded(500, 400, 2000));
        assertEquals(1, BloodRates.meatNeeded(0, 80, 2000));
        assertEquals(1, BloodRates.meatNeeded(1990, 2000, 2000));
        assertEquals(2, BloodRates.meatNeeded(0, 2001, 2000));
    }

    @Test
    void altarShareLeavesRoomForTheRunes() {
        assertEquals(1000, BloodRates.altarShare(1000, 5000, 0f));
        // 12% self-sacrifice bonus: 1000 LP would become 1120
        assertEquals(892, BloodRates.altarShare(1000, 1000, 0.12f));
        assertEquals(0, BloodRates.altarShare(1000, 0, 0f));
        assertEquals(0, BloodRates.altarShare(0, 1000, 0f));
        // a nearly full altar still gets the last drop
        assertEquals(1, BloodRates.altarShare(1000, 1, 0.5f));
    }

    @Test
    void parsesTheConfigList() {
        assertArrayEquals(new int[] { 4, 8, 0, 16 }, BloodRates.parse(new String[] { "4", " 8 ", "x", "16" }));
    }
}
