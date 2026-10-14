package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The Weather Station's drawing (0.16.0, CITY.md §4.12 Visuals): the cloud thickens before the rain the server has
 * coming and is darkest in a storm; the forecast the station sends stays the same while the countdown runs; the vanes
 * turn faster in worse weather, by the world time alone; the mini lightning flickers rarely, more often as rain
 * gathers, the same however often a frame is drawn; the cloud floats over the mast and its anemometer, the bolts strike
 * the mast's top or run inside the cloud; and the counts are the drawing's.
 */
class WeatherLookTest {

    private static final CityBuild B = WeatherStationShape.BUILD;

    @Test
    void theCloudThickensBeforeTheRain() {
        double fair = WeatherLook.density(0, 0, -1);
        assertEquals(WeatherLook.FAIR, fair, 1e-12);
        // nothing known yet, or the rain far off: fair
        assertEquals(fair, WeatherLook.density(0, 0, WeatherLook.LEAD * 3), 1e-12);
        // thickening steadily over the lead before the rain
        double last = fair;
        for (int left = WeatherLook.LEAD; left >= 0; left -= WeatherLook.LEAD / 8) {
            double d = WeatherLook.density(0, 0, left);
            assertTrue(d >= last - 1e-12, left + " ticks before the rain");
            last = d;
        }
        assertTrue(last > fair + 0.4, "thick just before the rain: " + last);
        // thicker still while it rains, darkest in a thunderstorm, never past full
        double rain = WeatherLook.density(1, 0, -1), storm = WeatherLook.density(1, 1, -1);
        assertTrue(rain > last && storm > rain && storm <= 1, last + " < " + rain + " < " + storm);
        assertEquals(2, WeatherLook.wind(storm));
        assertEquals(0, WeatherLook.wind(fair));
        assertEquals(1, WeatherLook.wind(WeatherLook.BRISK));
    }

    @Test
    void theForecastStaysPutWhileTheCountdownRuns() {
        long at = WeatherLook.rainAt(false, 1000, 5000);
        assertEquals(6000, at);
        // both move a tick a tick: the same answer, nothing to send again
        for (int k = 0; k < 4000; k += 20) {
            long now = WeatherLook.rainAt(false, 1000 + k, 5000 - k);
            assertEquals(at, now);
            assertFalse(WeatherLook.forecastChanged(at, now));
        }
        assertEquals(-1, WeatherLook.rainAt(true, 1000, 5000), "raining: no rain is due");
        assertTrue(WeatherLook.forecastChanged(at, -1), "the rain came");
        assertTrue(WeatherLook.forecastChanged(-1, 9000), "the rain stopped");
        assertFalse(WeatherLook.forecastChanged(-1, -1));
        assertTrue(WeatherLook.forecastChanged(at, at + 24000), "a night slept through");
        assertFalse(WeatherLook.forecastChanged(at, at + 20), "a second or so is nothing");
    }

    @Test
    void theVanesTurnByTheTimeAlone() {
        for (double d : new double[] { 0.2, 0.6, 0.95 }) {
            int period = WeatherLook.TURN_T[WeatherLook.wind(d)];
            for (double t : new double[] { 0, 12.5, 4321.75 }) {
                assertEquals(WeatherLook.rotor(t, d), WeatherLook.rotor(t + period, d), 1e-6);
                double step = WeatherLook.rotor(t + 1, d) - WeatherLook.rotor(t, d);
                step = Math.IEEEremainder(step, 2 * Math.PI);
                assertTrue(step > 0 && step < 2 * Math.PI / 10, "a smooth turn: " + step);
                // the pylons the other way
                double ps = Math
                    .IEEEremainder(WeatherLook.pylonRotor(t + 1, d, 0) - WeatherLook.pylonRotor(t, d, 0), 2 * Math.PI);
                assertTrue(ps < 0);
            }
        }
        assertTrue(WeatherLook.TURN_T[2] < WeatherLook.TURN_T[1] && WeatherLook.TURN_T[1] < WeatherLook.TURN_T[0]);
        // the vane swings within bounds, never jerks
        for (double t = 0; t < 5000; t += 7) {
            double a = WeatherLook.vaneYaw(t, 0.9), b = WeatherLook.vaneYaw(t + 1, 0.9);
            assertTrue(Math.abs(b - a) < 0.5, "at " + t);
            assertTrue(Math.abs(a - 0.8) <= 0.7 + 0.3 + 0.18 + 1e-9);
        }
    }

    @Test
    void theLightningIsRareTheSameEachFrameAndMoreOftenInAStorm() {
        long seed = Mix.seed(120, 104, -340);
        int calm = windows(seed, 0.2, 0), gather = windows(seed, 0.7, 0), storm = windows(seed, 1, 1);
        int all = 20000;
        assertTrue(calm > 0 && calm < all * 0.06, "calm " + calm);
        assertTrue(gather > calm && storm > gather, calm + " < " + gather + " < " + storm);
        assertTrue(storm < all * 0.5, "storm " + storm);
        // the same answer each time a frame asks
        for (double t = 0; t < 40000; t += 3.25)
            assertEquals(WeatherLook.flash(seed, t, 1, 1), WeatherLook.flash(seed, t, 1, 1));
        // a flash lies within its window and lasts no longer than its flicker
        for (double t = 0; t < 40000; t += 1) {
            long w = WeatherLook.flash(seed, t, 1, 1);
            if (w < 0) continue;
            assertEquals((long) Math.floor(t / WeatherLook.FLASH_WINDOW), w);
            int lit = 0;
            for (int k = 0; k < WeatherLook.FLASH_WINDOW; k++)
                if (WeatherLook.flash(seed, w * WeatherLook.FLASH_WINDOW + k, 1, 1) >= 0) lit++;
            assertTrue(lit > 0 && lit <= WeatherLook.FLASH_TICKS, "window " + w + ": " + lit);
        }
    }

    /** The windows of {@code all} = 20,000 with a flash in them at a density and thunder. */
    private static int windows(long seed, double d, double thunder) {
        int n = 0;
        for (long w = 0; w < 20000; w++) {
            boolean any = false;
            for (int k = 0; k < WeatherLook.FLASH_WINDOW && !any; k++)
                any = WeatherLook.flash(seed, w * WeatherLook.FLASH_WINDOW + k, d, thunder) >= 0;
            if (any) n++;
        }
        return n;
    }

    @Test
    void theBoltsStrikeTheMastsTopOrRunInsideTheCloud() {
        long seed = Mix.seed(-77, 90, 4096);
        int strikes = 0, across = 0;
        for (long w = 0; w < 400; w++) {
            double[][] bolt = WeatherLook.bolt(seed, w);
            assertEquals(WeatherLook.BOLT_PIECES + 1, bolt.length);
            assertArrayEquals(bolt, WeatherLook.bolt(seed, w));
            double[] from = bolt[0], to = bolt[bolt.length - 1];
            assertEquals(WeatherLook.CLOUD_Y - 0.4, from[1], 1e-12, "from inside the cloud");
            assertTrue(Math.hypot(from[0] - WeatherLook.MAST_X, from[2] - WeatherLook.MAST_Z) <= WeatherLook.CLOUD_R);
            if (to[0] == WeatherLook.MAST_X && to[2] == WeatherLook.MAST_Z) {
                strikes++;
                assertEquals(WeatherLook.MAST_TOP + 0.15, to[1], 1e-12);
            } else {
                across++;
                assertTrue(to[1] > WeatherLook.CLOUD_Y - 1.3 && to[1] < WeatherLook.CLOUD_Y, "inside the cloud");
            }
            for (double[] p : bolt) {
                assertTrue(p[1] >= WeatherLook.MAST_TOP, "never under the mast's top");
                assertTrue(p[0] >= 0 && p[0] <= SkyRing.SIZE && p[2] >= 0 && p[2] <= SkyRing.SIZE, "over the platform");
            }
        }
        assertTrue(strikes > across && across > 0, strikes + " strikes, " + across + " across");
    }

    /**
     * The cloud floats over the mast: every puff's middle stays well over the anemometer and its dense half clear of
     * it, round the mast, over the platform; more puffs show and they darken as the weather worsens.
     */
    @Test
    void theCloudFloatsOverTheMast() {
        for (int i = 0; i < WeatherLook.PUFFS[0]; i++)
            for (double d : new double[] { 0.2, 0.6, 1 }) for (double t : new double[] { 0, 777, 31415.9 }) {
                double[] p = WeatherLook.puff(i, t, d);
                assertTrue(p[1] > WeatherLook.MAST_TOP + 2, "puff " + i + " at " + p[1]);
                assertTrue(p[1] - p[3] / 2 > WeatherLook.MAST_TOP, "puff " + i + " dense half over the mast");
                assertTrue(Math.hypot(p[0] - WeatherLook.MAST_X, p[2] - WeatherLook.MAST_Z) <= WeatherLook.CLOUD_R);
                assertTrue(p[4] >= 0 && p[4] <= 1 && p[5] >= 0 && p[5] <= 1);
            }
        assertTrue(shown(0.2) < shown(1), "more puffs as it thickens");
        assertTrue(WeatherLook.puff(3, 100, 1)[5] > WeatherLook.puff(3, 100, 0.2)[5], "darker");
        // by the world time alone: the same each time a frame asks, and turning as time goes on
        assertArrayEquals(WeatherLook.puff(5, 200, 0.5), WeatherLook.puff(5, 200, 0.5), 0);
        assertTrue(WeatherLook.puff(5, 200, 0.5)[0] != WeatherLook.puff(5, 800, 0.5)[0]);
    }

    private static int shown(double d) {
        int n = 0;
        for (int i = 0; i < WeatherLook.PUFFS[0]; i++) if (WeatherLook.puff(i, 50, d)[4] > 0.01) n++;
        return n;
    }

    /**
     * The rotors turn in the air: the anemometer's cups round the dissolved mast over the hut, the pylons' blades over
     * their rings, every point clear of the station's blocks that stay.
     */
    @Test
    void theRotorsTurnInTheAir() {
        String open = LookCells.OPEN + WeatherStationShape.DISSOLVES;
        for (double turn = 0; turn < 2 * Math.PI; turn += 0.1) {
            for (double[] c : WeatherLook.cups(turn)) {
                assertEquals(WeatherLook.ARM, Math.hypot(c[0] - WeatherLook.MAST_X, c[1] - WeatherLook.MAST_Z), 1e-9);
                assertTrue(LookCells.clearance(B, c[0], WeatherLook.ROTOR_Y, c[1], open, 0.2) >= 0.2, "a cup");
            }
            for (int p = 0; p < WeatherLook.PYLON_X.length; p++) for (double[] b : WeatherLook.blades(p, turn)) {
                assertTrue(
                    LookCells.clearance(B, b[0], WeatherLook.PYLON_ROTOR_Y, b[1], open, 0.15) >= 0.15,
                    "a blade");
            }
        }
    }

    @Test
    void theCountsAreTheDrawing() {
        assertEquals(0, WeatherLook.mastQuads(ModuleLook.FAR));
        assertEquals(
            WeatherLook.STRUT_QUADS + DomeLook.pieces(WeatherLook.MAST_TOP - WeatherLook.MAST_FOOT)
                + 2 * WeatherLook.CUPS
                + 1
                + 2
                + 2 * (WeatherLook.BLADES + 1),
            WeatherLook.mastQuads(ModuleLook.NEAR));
        assertEquals(18 * 8, WeatherLook.cloudQuads(ModuleLook.NEAR));
        assertEquals(6 * 4, WeatherLook.cloudQuads(ModuleLook.FAR));
        assertEquals(WeatherLook.BOLT_PIECES * 2 + 1, WeatherLook.flashQuads(ModuleLook.NEAR));
        assertEquals(WeatherLook.BOLT_PIECES + 1, WeatherLook.flashQuads(ModuleLook.FAR));
        assertEquals(
            WeatherLook.cloudQuads(ModuleLook.FAR) + WeatherLook.flashQuads(ModuleLook.FAR),
            WeatherLook.landmarkQuads());
    }
}
