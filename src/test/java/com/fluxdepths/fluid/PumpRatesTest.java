package com.fluxdepths.fluid;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fluxdepths.shard.DrillHead;

class PumpRatesTest {

    @Test
    void shareOfThePristineAmountRoundedDown() {
        assertEquals(100, PumpRates.perCycle(1000, 0.1));
        assertEquals(312, PumpRates.perCycle(1250, 0.25));
        assertEquals(625, PumpRates.perCycle(1250, 0.5));
    }

    @Test
    void atLeastOneLitreWhileThereIsFluid() {
        assertEquals(1, PumpRates.perCycle(3, 0.1));
        assertEquals(0, PumpRates.perCycle(0, 0.5));
        assertEquals(0, PumpRates.perCycle(-5, 0.5));
        assertEquals(0, PumpRates.perCycle(1000, 0));
    }

    @Test
    void hugeSharesDoNotOverflow() {
        assertEquals(Integer.MAX_VALUE, PumpRates.perCycle(Integer.MAX_VALUE, 10));
    }

    @Test
    void parseKeepsDefaultsForMissingOrBadEntries() {
        double[] d = { 0.1, 0.25, 0.5 };
        assertArrayEquals(new double[] { 0.2, 0.25, 0.5 }, PumpRates.parse(new String[] { " 0.2 " }, d));
        assertArrayEquals(d, PumpRates.parse(new String[] { "x", "-1", "NaN" }, d));
        assertArrayEquals(d, PumpRates.parse(null, d));
        assertArrayEquals(new double[] { 0, 1, 2 }, PumpRates.parse(new String[] { "0", "1", "2", "3" }, d));
    }

    @Test
    void parseDoesNotTouchTheDefaults() {
        double[] d = { 0.1, 0.25, 0.5 };
        PumpRates.parse(new String[] { "9", "9", "9" }, d);
        assertArrayEquals(new double[] { 0.1, 0.25, 0.5 }, d);
    }

    @Test
    void tiersGetFasterAndNeedBetterHeads() {
        PumpTier[] t = PumpTier.values();
        for (int i = 1; i < t.length; i++) {
            assertTrue(t[i].defaultShare > t[i - 1].defaultShare);
            assertTrue(t[i].energy > t[i - 1].energy);
            assertTrue(t[i].takes(t[i].minHead));
            assertTrue(!t[i].takes(t[i - 1].minHead) || t[i].minHead == t[i - 1].minHead);
        }
        assertTrue(PumpTier.LV.takes(DrillHead.TUNGSTEN_STEEL));
        assertTrue(!PumpTier.LV.takes(DrillHead.BRONZE));
        assertTrue(!PumpTier.LV.takes(null));
        assertEquals(256 * 15, PumpTier.seconds(DrillHead.STEEL));
    }
}
