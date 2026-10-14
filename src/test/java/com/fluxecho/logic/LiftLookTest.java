package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.LiftShaft.Cell;

/**
 * The light lift's look and feel (0.12.0, CITY.md §2.5): the beams run exactly where the rides go (root island ↔ dais ↔
 * pod on a campus's axis, none over a capped shaft, a stub up to what a ride would bump), a ride gathers speed and
 * settles exactly onto its pad without passing it, the view widens with the speed, and the hoops a rider rushes past
 * stay on the beam.
 */
class LiftLookTest {

    private static final class Col implements LiftShaft.Column {

        final Map<Integer, Cell> cells = new HashMap<>();

        Col fill(int y0, int y1, Cell c) {
            for (int y = y0; y <= y1; y++) cells.put(y, c);
            return this;
        }

        @Override
        public Cell at(int y) {
            return cells.getOrDefault(y, Cell.OPEN);
        }
    }

    private static final int Y0 = 64;

    /** A phase-II campus's axis with the Tidal Well and the Dome Generator: three pads, the pod's shaft capped. */
    private static Col axis() {
        Col c = new Col().fill(0, Y0 - 27, Cell.SOLID);
        // the well: its floor slab, the island's pad, open water and air up through the lid's open centre
        c.fill(Y0 - 25, Y0 - 25, Cell.PAD);
        c.fill(Y0, Y0, Cell.PAD);
        c.fill(Y0 + 1, Y0 + 4, Cell.OURS)
            .fill(Y0 + 6, Y0 + 9, Cell.OURS);
        c.fill(Y0 + 40, Y0 + 40, Cell.PAD);
        c.fill(Y0 + 46, Y0 + 48, Cell.SOLID)
            .fill(Y0 + 49, Y0 + 49, Cell.OURS);
        return c;
    }

    @Test
    void theAxisBeamsAreItsRides() {
        List<LiftLook.Beam> b = LiftLook.beams(axis(), new int[] { Y0 + 40, Y0, Y0 - 25, Y0 });
        assertEquals(2, b.size(), b.toString());
        assertEquals(Y0 - 25, b.get(0).pad);
        assertEquals(Y0 - 24, b.get(0).lo, 1e-9);
        assertEquals(Y0, b.get(0).hi, 1e-9, "up to the dais pad's foot");
        assertFalse(b.get(0).blocked);
        assertEquals(Y0 + 1, b.get(1).lo, 1e-9);
        assertEquals(Y0 + 40, b.get(1).hi, 1e-9, "through the nexus's column to the pod's pad");
        // each beam is exactly the ride up from its pad
        for (LiftLook.Beam beam : b) {
            LiftShaft.Ride r = LiftShaft.plan(axis(), beam.pad, 1);
            assertEquals(r.to - 1, beam.hi, 1e-9);
        }
    }

    @Test
    void aBlockedRideIsAStubAndAPadPassedOverAddsNoBeam() {
        Col c = new Col().fill(10, 10, Cell.PAD)
            .fill(12, 12, Cell.PAD)
            .fill(20, 20, Cell.PAD);
        List<LiftLook.Beam> b = LiftLook.beams(c, new int[] { 10, 12, 20 });
        assertEquals(1, b.size(), "the pad two over the lowest rides to the same pad: " + b);
        assertEquals(10, b.get(0).pad);
        assertEquals(20, b.get(0).hi, 1e-9);
        c.fill(16, 16, Cell.SOLID);
        b = LiftLook.beams(c, new int[] { 10, 12, 20 });
        assertTrue(b.get(0).blocked);
        assertEquals(16, b.get(0).hi, 1e-9, "up to the block in the way");
        assertTrue(
            LiftLook.beams(new Col().fill(5, 5, Cell.PAD), new int[] { 5 })
                .isEmpty(),
            "a lone pad: no beam");
    }

    @Test
    void aRideGathersSpeedAndSettles() {
        double feet = Y0 + 1, goal = Y0 + 41;
        double prev = 0;
        int ticks = 0;
        boolean full = false;
        while (Math.abs(goal - feet) > LiftShaft.ARRIVED) {
            double m = LiftShaft.ease(feet, goal, ticks, 0);
            assertTrue(m > 0 && m <= LiftShaft.SPEED + 1e-12, "up, never faster than the speed: " + m);
            assertTrue(feet + m <= goal + 1e-12, "never past the goal");
            if (ticks < 5) assertTrue(m >= prev - 1e-12, "gathering speed");
            full |= m == LiftShaft.SPEED;
            prev = m;
            feet += m;
            ticks++;
            assertTrue(ticks < 200, "it arrives");
        }
        assertTrue(full, "at full speed in the middle");
        assertTrue(prev < 0.2, "settling slowly onto the pad: " + prev);
        assertEquals(goal, feet, 0.0, "exactly onto the pad's top, not into it (0.14.1)");
        LiftShaft.Ride r = LiftShaft.plan(axis(), Y0, 1);
        assertTrue(ticks > r.ticks() && ticks <= r.ticks() + 12, "a little longer than at full speed: " + ticks);
        assertTrue(ticks < r.ticks() + LiftShaft.GRACE, "well within the grace both sides allow");
        // a jump's speed carries on into the ride; down goes down
        assertEquals(0.4, LiftShaft.ease(Y0 + 1, Y0 + 41, 0, 0.4), 1e-12);
        assertTrue(LiftShaft.ease(Y0 + 1, Y0 - 24, 0, 0) < 0);
        assertEquals(0, LiftShaft.ease(5, 5, 3, 0), 1e-12);
        assertEquals(0.03, LiftShaft.ease(5, 5.03, 3, 0), 1e-12, "the last bit exactly");
    }

    @Test
    void theViewWidensWithTheSpeed() {
        assertEquals(1, LiftLook.fov(0), 1e-12);
        assertEquals(1 + LiftLook.FOV, LiftLook.fov(LiftShaft.SPEED), 1e-12);
        assertEquals(1 + LiftLook.FOV, LiftLook.fov(-2), 1e-12);
        assertTrue(LiftLook.fov(0.3) < LiftLook.fov(0.5));
    }

    @Test
    void theHoopsStayOnTheBeam() {
        double[] h = LiftLook.hoops(10, 50, 30.2);
        assertEquals(2 * LiftLook.HOOPS + 1, h.length);
        for (double y : h) {
            assertTrue(y >= 10 && y <= 50);
            assertEquals(0, (y - 10) / LiftLook.HOOP_STEP % 1, 1e-9, "fixed to the shaft");
            assertTrue(Math.abs(y - 30.2) <= (LiftLook.HOOPS + 1) * LiftLook.HOOP_STEP);
        }
        assertTrue(LiftLook.hoops(10, 50, 11).length < h.length, "cut at the beam's foot");
        assertEquals(0, LiftLook.hoops(10, 20, 100).length);
    }

    /**
     * The deep root's lift (0.16.0, CITY.md §2.5): a chamber's pad under the island's adds the ride down to it, from
     * the chamber's floor (Y0 - 39 under the full chamber, Y0 - 33 under the 7-layer one) up to the island's pad
     * (a gap of 14 or 8), through the open centre of the well's floor slab; an in-well form adds none. The axis is read
     * off each form's own cells.
     */
    @Test
    void theDeepRootsChamberAddsItsRide() {
        for (RootShape.Deep d : RootShape.deeps()) {
            int base = Y0 - d.well.depth - d.h;
            Col c = new Col().fill(0, base - 1, Cell.SOLID);
            java.util.List<Integer> pads = new java.util.ArrayList<>();
            for (int y = 0; y < d.shape.H; y++) {
                char ch = d.cell(12, y, 12);
                Cell k = ch == '^' ? Cell.PAD : ch == ' ' || ch == '-' || ch == '=' ? Cell.OPEN : Cell.OURS;
                c.cells.put(base + y, k);
                if (ch == '^') pads.add(base + y);
            }
            // the dais's pad over the lid's open centre
            c.fill(Y0, Y0, Cell.PAD);
            pads.add(Y0);
            int[] at = new int[pads.size()];
            for (int i = 0; i < at.length; i++) at[i] = pads.get(i);
            List<LiftLook.Beam> b = LiftLook.beams(c, at);
            int island = Y0 - d.well.depth + 1;
            assertEquals(d.h > 0 ? 2 : 1, b.size(), d.name + ": " + b);
            LiftLook.Beam up = b.get(b.size() - 1);
            assertEquals(island, up.pad, d.name + ": the island's ride to the dais");
            assertEquals(Y0, up.hi, 1e-9);
            if (d.h == 0) continue;
            LiftLook.Beam down = b.get(0);
            assertEquals(Y0 - 26 - d.h, down.pad, d.name + ": the chamber's floor pad");
            assertEquals(down.pad + 1, down.lo, 1e-9);
            assertEquals(island, down.hi, 1e-9, d.name + ": up to the island's pad");
            assertEquals(d.h == 13 ? 14 : 8, island - down.pad, "the gap");
            assertFalse(down.blocked);
            LiftShaft.Ride r = LiftShaft.plan(c, down.pad, 1);
            // a ride ends with the feet on the pad's top
            assertEquals(island + 1, r.to, 1e-9, d.name + ": the ride up lands on the island");
            assertEquals(
                down.pad + 1,
                LiftShaft.plan(c, island, -1).to,
                1e-9,
                d.name + ": and down on the chamber's floor");
        }
    }
}
