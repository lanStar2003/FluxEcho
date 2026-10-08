package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ManaSpringTest {

    @Test
    void fourTimesPerTier() {
        assertEquals(16, ManaSpring.manaPerTick(16, 1));
        assertEquals(64, ManaSpring.manaPerTick(16, 2));
        assertEquals(16384, ManaSpring.manaPerTick(16, 6));
        assertEquals(0, ManaSpring.manaPerTick(16, 0));
        assertEquals(0, ManaSpring.manaPerTick(16, 7));
    }

    @Test
    void euFillsTheTierAtTwoPerMana() {
        for (int t = 1; t <= 6; t++) {
            long eu = ManaSpring.euPerTick(16, 2, t);
            assertEquals(ManaSpring.voltage(t), eu, "tier " + t);
            assertTrue(eu > ManaSpring.voltage(t - 1), "tier " + t + " needs its own voltage");
        }
    }

    @Test
    void petalsGoAtTheSamePaceAtEveryTier() {
        for (int t = 1; t <= 6; t++) {
            double perSecond = ManaSpring.manaPerTick(16, t) * 20.0 / ManaSpring.manaPerPetal(1250, t);
            assertEquals(0.256, perSecond, 1e-9, "tier " + t);
        }
        assertEquals(15.36, ManaSpring.petalsPerMinute(16, 1250), 1e-9);
        assertEquals(0, ManaSpring.petalsPerMinute(16, 0));
    }

    @Test
    void reachGrowsWithTheTier() {
        assertEquals(4, ManaSpring.range(4, 1));
        assertEquals(9, ManaSpring.range(4, 6));
        assertEquals(2, ManaSpring.height(2, 1));
        assertEquals(2, ManaSpring.height(2, 2));
        assertEquals(4, ManaSpring.height(2, 6));
    }

    @Test
    void petalsNeeded() {
        assertEquals(0, ManaSpring.petalsNeeded(500, 320, 1250));
        assertEquals(1, ManaSpring.petalsNeeded(0, 320, 1250));
        assertEquals(2, ManaSpring.petalsNeeded(100, 2600, 1250));
        assertEquals(3, ManaSpring.petalsNeeded(100, 2601, 1250));
        assertEquals(5, ManaSpring.petalsNeeded(0, 5, 0));
    }
}
