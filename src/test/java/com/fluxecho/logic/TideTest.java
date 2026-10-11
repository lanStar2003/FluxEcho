package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The echo tide's curve (BLUEPRINT 3.11), as the Tidal Well draws it (0.12.0): full at midnight and lowest at noon,
 * +-20% at the full moon and +-5% at the new moon, the mean over a day exactly 100%, and no jump where one day's moon
 * gives way to the next.
 */
class TideTest {

    private static final double EPS = 1e-9;

    @Test
    void fullAtMidnightLowestAtNoon() {
        // day 0 is a full moon
        assertEquals(Tide.SPRING, Tide.swing(Tide.FULL), EPS);
        assertEquals(-Tide.SPRING, Tide.swing(6000), EPS);
        assertEquals(1.2, Tide.level(18000), EPS);
        assertEquals(0.8, Tide.level(6000), EPS);
        assertEquals(1, Tide.norm(18000), EPS);
        assertEquals(-1, Tide.norm(6000), EPS);
        // sunrise and sunset cross the mean
        assertEquals(0, Tide.swing(0), EPS);
        assertEquals(0, Tide.swing(12000), EPS);
    }

    @Test
    void theMoonSetsTheSwing() {
        assertEquals(0, Tide.moonPhase(0));
        assertEquals(4, Tide.moonPhase(4 * Tide.DAY + 100));
        assertEquals(0, Tide.moonPhase(8 * Tide.DAY));
        assertEquals(Tide.SPRING, Tide.amplitude(0), EPS);
        assertEquals(Tide.NEAP, Tide.amplitude(4), EPS);
        assertEquals((Tide.SPRING + Tide.NEAP) / 2, Tide.amplitude(2), EPS);
        assertEquals(Tide.amplitude(1), Tide.amplitude(7), EPS);
        assertEquals(Tide.NEAP, Tide.swing(4 * Tide.DAY + Tide.FULL), EPS, "a neap tide at the new moon's midnight");
        assertEquals(Tide.NEAP / Tide.SPRING, Tide.norm(4 * Tide.DAY + Tide.FULL), EPS);
        // as Minecraft counts it, time before 0 still in phase 0 for the first day back
        assertEquals(0, Tide.moonPhase(-1));
    }

    @Test
    void aDayAveragesToTheMean() {
        for (int day = 0; day < 8; day++) {
            double sum = 0;
            int n = 2400;
            for (int i = 0; i < n; i++) sum += Tide.level(day * (double) Tide.DAY + i * (Tide.DAY / (double) n));
            assertEquals(1, sum / n, 1e-9, "day " + day);
        }
    }

    @Test
    void theCurveNeverJumps() {
        double step = 0.5;
        for (int day = 1; day < 17; day++) {
            double t = day * (double) Tide.DAY;
            assertEquals(Tide.swing(t - step), Tide.swing(t + step), 1e-4, "at the turn of day " + day);
        }
        for (double t = 0; t < 8 * Tide.DAY; t += 37) {
            assertTrue(Math.abs(Tide.swing(t + 1) - Tide.swing(t)) < 1e-4, "smooth at " + t);
            assertTrue(Math.abs(Tide.norm(t)) <= 1 + EPS);
        }
    }
}
