package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KillCostTest {

    @Test
    void anEndermanTakesTheMinimumTimeAtLv() {
        // 40 health x 128 EU = 5120 EU; at 32 EU/t that is 160 ticks, below the 200 tick minimum
        KillCost c = KillCost.of(40, 128, 1, 32, 200);
        assertEquals(5120, c.eu);
        assertEquals(200, c.ticks);
        assertEquals(26, c.eut);
        assertTrue((long) c.eut * c.ticks >= c.eu);
    }

    @Test
    void aBossCostsTheMultiplierAndNeverExceedsTheVoltage() {
        // the Wither: 300 health x 128 EU x 50
        KillCost c = KillCost.of(300, 128, 50, 32, 200);
        assertEquals(1_920_000, c.eu);
        assertEquals(60_000, c.ticks);
        assertEquals(32, c.eut);
    }

    @Test
    void zeroHealthStillCostsOnePoint() {
        KillCost c = KillCost.of(0, 128, 1, 32, 1);
        assertEquals(128, c.eu);
        assertEquals(4, c.ticks);
        assertEquals(32, c.eut);
    }
}
