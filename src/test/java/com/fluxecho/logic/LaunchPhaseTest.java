package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LaunchPhaseTest {

    private static final double EPS = 1e-9;

    @Test
    void aLongFlightTravelsThenPrints() {
        // 16 ticks, the longest flight the builder sends: 8 ticks of travel, then 8 of print
        double start = 100, land = 116;
        assertEquals(16, LaunchPhase.flight(start, land), EPS);
        assertEquals(8, LaunchPhase.travelTicks(start, land), EPS);
        assertEquals(108, LaunchPhase.arrival(start, land), EPS);
        assertEquals(108, LaunchPhase.printStart(start, land), EPS, "the print begins where the travel ends");
        assertEquals(0, LaunchPhase.travel(start, land, 100), EPS);
        assertEquals(0.5, LaunchPhase.travel(start, land, 104), EPS);
        assertEquals(1, LaunchPhase.travel(start, land, 108), EPS);
        assertEquals(0, LaunchPhase.print(start, land, 108), EPS);
        assertEquals(0.5, LaunchPhase.print(start, land, 112), EPS);
        assertEquals(1, LaunchPhase.print(start, land, 116), EPS);
    }

    @Test
    void aShortFlightTravelsHalfAndPrintsHalf() {
        // 16 ticks and up print over the last 8; shorter flights split in two
        assertEquals(8, LaunchPhase.printTicks(0, 16), EPS);
        assertEquals(8, LaunchPhase.printTicks(0, 30), EPS);
        assertEquals(6, LaunchPhase.printTicks(0, 12), EPS);
        assertEquals(6, LaunchPhase.travelTicks(0, 12), EPS);
        assertEquals(6, LaunchPhase.printStart(0, 12), EPS, "the print begins where the travel ends");
        assertEquals(2, LaunchPhase.travelTicks(0, 4), EPS);
        assertEquals(0.5, LaunchPhase.travel(0, 4, 1), EPS);
        assertEquals(0.5, LaunchPhase.print(0, 4, 3), EPS);
        assertEquals(0, LaunchPhase.print(0, 4, 2), EPS, "no print before the cargo arrives");
    }

    @Test
    void aLaunchSeenOnTimeKeepsItsTicks() {
        // the longest flight the builder sends, seen the tick it left
        assertEquals(100, LaunchPhase.shownStart(100, 130, 100));
        assertEquals(130, LaunchPhase.shownLand(100, 130, 100));
        // a client whose clock is behind the server's sees it before it leaves
        assertEquals(100, LaunchPhase.shownStart(100, 130, 97));
        assertEquals(130, LaunchPhase.shownLand(100, 130, 97));
    }

    @Test
    void aLaunchSeenLateFliesFasterAndLandsWithItsBlock() {
        // the shortest flight (14 ticks) reaching the client 4 ticks after it left (the server's 4-tick batches)
        assertEquals(304, LaunchPhase.shownStart(300, 314, 304));
        assertEquals(314, LaunchPhase.shownLand(300, 314, 304), "lands the tick its block appears");
        double s = LaunchPhase.shownStart(300, 314, 304), l = LaunchPhase.shownLand(300, 314, 304);
        assertEquals(5, LaunchPhase.travelTicks(s, l), EPS, "ten shown ticks: five flying, five printing");
        // every launch seen before it lands is shown from then and lands with its block, never before or after
        for (long seen = 290; seen <= 314; seen++) {
            long st = LaunchPhase.shownStart(300, 314, seen), ld = LaunchPhase.shownLand(300, 314, seen);
            assertTrue(st >= 300 && st >= seen, "not shown before it was seen: " + seen);
            assertEquals(314, ld, "lands with its block: " + seen);
        }
    }

    @Test
    void aLaunchSeenAfterItLandedOnlyFlashes() {
        // seen 3 ticks after it landed: nothing flies, the flash shows at once
        assertEquals(317, LaunchPhase.shownStart(300, 314, 317));
        assertEquals(317, LaunchPhase.shownLand(300, 314, 317));
        assertEquals(0, LaunchPhase.travelTicks(317, 317), EPS);
        assertEquals(1, LaunchPhase.flash(317, 317), EPS);
        // seen long after: it is over
        long seen = 314 + LaunchPhase.MAX_LATE + 1;
        assertEquals(300, LaunchPhase.shownStart(300, 314, seen));
        assertEquals(314, LaunchPhase.shownLand(300, 314, seen));
        assertTrue(LaunchPhase.done(LaunchPhase.shownLand(300, 314, seen), seen), "nothing left to show");
        // a landing before the launch is taken as landing at the launch
        assertEquals(501, LaunchPhase.shownStart(500, 490, 501));
        assertEquals(501, LaunchPhase.shownLand(500, 490, 501));
    }

    @Test
    void flightsOfNoLengthArriveAndPrintAtOnce() {
        assertEquals(0, LaunchPhase.travelTicks(5, 5), EPS);
        assertEquals(0, LaunchPhase.travel(5, 5, 4.9), EPS);
        assertEquals(1, LaunchPhase.travel(5, 5, 5), EPS);
        assertEquals(0, LaunchPhase.print(5, 5, 4.9), EPS);
        assertEquals(1, LaunchPhase.print(5, 5, 5), EPS);
        // a landing before the launch counts as landing at the launch
        assertEquals(0, LaunchPhase.flight(5, 3), EPS);
        assertEquals(0, LaunchPhase.travelTicks(5, 3), EPS);
        assertEquals(5, LaunchPhase.printStart(5, 3), EPS);
        assertEquals(1, LaunchPhase.print(5, 3, 5), EPS);
    }

    @Test
    void fractionsAreClampedAndNeverGoBack() {
        for (int d = 0; d <= 20; d++) {
            double start = 1000, land = start + d;
            double lastTravel = -1, lastPrint = -1;
            for (double now = start - 30; now <= land + 30; now += 0.25) {
                double tr = LaunchPhase.travel(start, land, now), pr = LaunchPhase.print(start, land, now);
                assertTrue(tr >= 0 && tr <= 1, "travel in 0..1 for flight " + d + " at " + now);
                assertTrue(pr >= 0 && pr <= 1, "print in 0..1 for flight " + d + " at " + now);
                assertTrue(tr >= lastTravel, "travel never goes back for flight " + d + " at " + now);
                assertTrue(pr >= lastPrint, "print never goes back for flight " + d + " at " + now);
                lastTravel = tr;
                lastPrint = pr;
            }
            assertEquals(0, LaunchPhase.travel(start, land, start - 1), EPS);
            assertEquals(1, LaunchPhase.travel(start, land, land + 1), EPS);
            assertEquals(1, LaunchPhase.print(start, land, land + 100), EPS);
            assertTrue(LaunchPhase.printStart(start, land) >= start, "no print before the launch");
            assertTrue(land - LaunchPhase.printStart(start, land) <= LaunchPhase.PRINT + EPS, "at most 8 ticks");
            assertTrue(LaunchPhase.arrival(start, land) <= land + EPS, "the cargo arrives by the landing");
        }
    }

    @Test
    void theFlashFadesOverSixTicksAfterLanding() {
        assertEquals(0, LaunchPhase.flash(50, 49.9), EPS);
        assertEquals(1, LaunchPhase.flash(50, 50), EPS);
        assertEquals(0.5, LaunchPhase.flash(50, 53), EPS);
        assertEquals(0, LaunchPhase.flash(50, 56), EPS);
        assertEquals(0, LaunchPhase.flash(50, 80), EPS);
        assertFalse(LaunchPhase.done(50, 55.9));
        assertTrue(LaunchPhase.done(50, 56));
    }

    @Test
    void flyingIsLaunchedAndNotArrived() {
        assertFalse(LaunchPhase.flying(10, 26, 9.99));
        assertTrue(LaunchPhase.flying(10, 26, 10));
        assertTrue(LaunchPhase.flying(10, 26, 17.99));
        assertFalse(LaunchPhase.flying(10, 26, 18));
    }

    @Test
    void theArcRunsFromTheSourceToTheTarget() {
        LaunchPhase.Arc a = new LaunchPhase.Arc(0.5, 70.5, 0.5, 30.5, 64.5, -10.5);
        double[] p0 = a.point(0), p1 = a.point(1);
        assertEquals(0.5, p0[0], EPS);
        assertEquals(70.5, p0[1], EPS);
        assertEquals(0.5, p0[2], EPS);
        assertEquals(30.5, p1[0], EPS);
        assertEquals(64.5, p1[1], EPS);
        assertEquals(-10.5, p1[2], EPS);
        // clamped past the ends
        assertEquals(p0[1], a.point(-1)[1], EPS);
        assertEquals(p1[0], a.point(2)[0], EPS);
        // the control point sits over the middle
        assertEquals(15.5, a.mx, EPS);
        assertEquals(-5, a.mz, EPS);
    }

    @Test
    void theApexRisesWithTheHorizontalDistanceAboveTheHigherEnd() {
        LaunchPhase.Arc a = new LaunchPhase.Arc(0, 70, 0, 30, 64, 40);
        double h = 50; // a 30-40-50 triangle
        assertEquals(70 + 3 + 0.35 * h, a.apex, EPS);
        double top = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= 10000; i++) top = Math.max(top, a.point(i / 10000.0)[1]);
        assertEquals(a.apex, top, 1e-6, "the curve's highest point is the apex");
        assertEquals(a.apex, a.point(a.apexAt())[1], 1e-9);
        assertEquals(0, a.tangent(a.apexAt())[1], 1e-9, "level at the apex");
        // a vertical launch still rises the base three blocks over the higher end
        LaunchPhase.Arc up = new LaunchPhase.Arc(5, 60, 5, 5, 64, 5);
        assertEquals(67, up.apex, EPS);
        assertEquals(LaunchPhase.APEX_RISE, LaunchPhase.apexRise(0), EPS);
        assertEquals(LaunchPhase.APEX_RISE, LaunchPhase.apexRise(-4), EPS, "no negative distance");
    }

    @Test
    void theTangentLeadsFromTheSourceToTheTarget() {
        LaunchPhase.Arc a = new LaunchPhase.Arc(0, 64, 0, 20, 64, 0);
        double[] t0 = a.tangent(0), t1 = a.tangent(1);
        assertTrue(t0[0] > 0 && t0[1] > 0, "it leaves up and out");
        assertTrue(t1[0] > 0 && t1[1] < 0, "it comes down onto the cell");
        // the derivative matches a finite difference
        double[] p = a.point(0.3), q = a.point(0.3 + 1e-6), d = a.tangent(0.3);
        for (int k = 0; k < 3; k++) assertEquals(d[k], (q[k] - p[k]) / 1e-6, 1e-3);
    }

    @Test
    void theLengthIsAtLeastTheChord() {
        LaunchPhase.Arc a = new LaunchPhase.Arc(0, 64, 0, 40, 60, 0);
        double chord = Math.sqrt(40 * 40 + 4 * 4);
        assertTrue(a.length(32) > chord);
        assertTrue(a.length(64) >= a.length(8) - EPS, "finer pieces never measure shorter");
    }
}
