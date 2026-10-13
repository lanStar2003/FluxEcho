package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.LiftShaft.Cell;
import com.fluxecho.logic.LiftShaft.Ride;

/**
 * The light lift's rule (0.12.0, CITY.md §2.5): which pad a ride goes to, where it stops below something else, what a
 * rider passes through, how it is moved, and (0.14.1) that it lands exactly on its pad and when it ends, on the axis
 * shaft of a campus (dais pad at Y0, the nexus's column, the pod's pad at Y0 + 40) and on made-up columns.
 */
class LiftShaftTest {

    /** A column of cells by height; anything not set is open air. */
    private static final class Col implements LiftShaft.Column {

        final Map<Integer, Cell> cells = new HashMap<>();

        Col set(int y, Cell c) {
            cells.put(y, c);
            return this;
        }

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

    /** The axis of a phase-II campus: the ground, the dais pad, the nexus's middle column, the pod with its pad. */
    private static Col axis() {
        Col c = new Col().fill(0, Y0 - 1, Cell.SOLID)
            .set(Y0, Cell.PAD);
        // conduits y1..3, a seat y4, conduits y6..8, a seat y9 (frame blocks: ours)
        c.fill(Y0 + 1, Y0 + 4, Cell.OURS)
            .fill(Y0 + 6, Y0 + 9, Cell.OURS);
        c.set(Y0 + 40, Cell.PAD);
        // the shaft's soft cap over the pod's pad: deck panels (not ours as a rider sees them) and glaze (a fitting)
        c.fill(Y0 + 46, Y0 + 48, Cell.SOLID)
            .set(Y0 + 49, Cell.OURS);
        return c;
    }

    @Test
    void upTheAxisToThePod() {
        Ride r = LiftShaft.plan(axis(), Y0, 1);
        assertNotNull(r);
        assertEquals(1, r.dir);
        assertFalse(r.blocked);
        assertEquals(Y0 + 1, r.from);
        assertEquals(Y0 + 41, r.to, "standing on the pod's pad");
        assertEquals(r.to, r.peak);
        assertEquals(67, r.ticks(), "40 blocks at 0.6 a tick");
        assertArrayEquals(new int[] { Y0, Y0 + 43 }, r.span());
    }

    @Test
    void downFromThePodToTheDais() {
        Ride r = LiftShaft.plan(axis(), Y0 + 40, -1);
        assertNotNull(r);
        assertEquals(-1, r.dir);
        assertEquals(Y0 + 41, r.from);
        assertEquals(Y0 + 1, r.to, "standing on the dais pad, inside the nexus's column");
        assertArrayEquals(new int[] { Y0, Y0 + 43 }, r.span());
    }

    @Test
    void noPadUpOrDownMeansNoRide() {
        Col c = new Col().fill(0, 9, Cell.SOLID)
            .set(10, Cell.PAD);
        assertNull(LiftShaft.plan(c, 10, 1), "open sky and no pad: an ordinary jump");
        assertNull(LiftShaft.plan(c, 10, -1), "solid right under the pad: nowhere to sink");
        // the bottom pad of the axis cannot sink, the pod's has no pad over it (its shaft is capped until the orbit)
        assertNull(LiftShaft.plan(axis(), Y0, -1));
        assertNull(LiftShaft.plan(axis(), Y0 + 40, 1));
        Col capped = new Col().set(10, Cell.PAD)
            .set(20, Cell.SOLID);
        assertNull(LiftShaft.plan(capped, 10, 1), "a ceiling and no pad over it: an ordinary jump");
    }

    @Test
    void somethingSolidInTheWayIsBumpedAndTheRideSinksBack() {
        Col c = new Col().fill(0, 9, Cell.SOLID)
            .set(10, Cell.PAD)
            .set(20, Cell.SOLID)
            .set(30, Cell.PAD);
        Ride r = LiftShaft.plan(c, 10, 1);
        assertNotNull(r);
        assertTrue(r.blocked);
        assertEquals(11, r.from);
        assertEquals(11, r.to, "back to the start");
        assertEquals(20 - LiftShaft.BODY, r.peak, 1e-9, "the head just under the block");
        assertEquals(r.peak, r.goal(false), 1e-9);
        assertEquals(r.to, r.goal(true), 1e-9);
        assertEquals((int) Math.ceil(2 * (r.peak - 11) / LiftShaft.SPEED), r.ticks());
        // nothing over the target pad to stand in: bumped there instead
        Col low = new Col().set(10, Cell.PAD)
            .set(30, Cell.PAD)
            .set(32, Cell.SOLID);
        Ride b = LiftShaft.plan(low, 10, 1);
        assertTrue(b.blocked);
        assertEquals(32 - LiftShaft.BODY, b.peak, 1e-9);
        // no room to rise at all
        Col tight = new Col().set(10, Cell.PAD)
            .set(13, Cell.SOLID);
        assertNull(LiftShaft.plan(tight, 10, 1));
    }

    @Test
    void sinkingStopsOnWhatIsBelowWhenAPlayerFits() {
        Col c = new Col().fill(0, 20, Cell.SOLID)
            .set(30, Cell.PAD);
        Ride r = LiftShaft.plan(c, 30, -1);
        assertNotNull(r);
        assertFalse(r.blocked);
        assertEquals(21, r.to, "standing on the ground under the shaft");
        Col close = new Col().fill(0, 28, Cell.SOLID)
            .set(30, Cell.PAD);
        assertNull(LiftShaft.plan(close, 30, -1), "one free cell under the pad is no room");
        Col fits = new Col().fill(0, 27, Cell.SOLID)
            .set(30, Cell.PAD);
        assertEquals(28, LiftShaft.plan(fits, 30, -1).to);
    }

    @Test
    void padsTooCloseArePassedOverAndReachIsLimited() {
        Col c = new Col().set(10, Cell.PAD)
            .set(12, Cell.PAD)
            .set(20, Cell.PAD);
        assertEquals(21, LiftShaft.plan(c, 10, 1).to, "the pad two above is in the head: passed over");
        assertEquals(13, LiftShaft.plan(c, 20, -1).to, "eight below is a stop");
        Col far = new Col().set(10, Cell.PAD)
            .set(10 + LiftShaft.REACH + 1, Cell.PAD);
        assertNull(LiftShaft.plan(far, 10, 1), "out of reach");
        Col edge = new Col().set(10, Cell.PAD)
            .set(10 + LiftShaft.REACH, Cell.PAD);
        assertNotNull(LiftShaft.plan(edge, 10, 1));
        assertThrows(IllegalArgumentException.class, () -> LiftShaft.plan(c, 10, 0));
    }

    @Test
    void ourBlocksAndPadsArePassedOnlyWhileRiding() {
        assertTrue(LiftShaft.passes(Cell.PAD, true, false));
        assertFalse(LiftShaft.passes(Cell.PAD, false, true), "a ride ends standing on its pad");
        assertFalse(LiftShaft.passes(Cell.PAD, false, false));
        assertTrue(LiftShaft.passes(Cell.OURS, true, false));
        assertTrue(LiftShaft.passes(Cell.OURS, false, true), "the grace lets a rider out of the nexus's column");
        assertFalse(LiftShaft.passes(Cell.OURS, false, false));
        for (boolean riding : new boolean[] { true, false }) {
            assertFalse(LiftShaft.passes(Cell.SOLID, riding, true));
            assertFalse(LiftShaft.passes(Cell.OPEN, riding, true), "air has nothing to pass");
        }
    }

    /** Ticking the motion the client applies takes the feet to the goal in the ride's own tick count. */
    @Test
    void theMotionReachesTheGoalInTime() {
        Ride r = LiftShaft.plan(axis(), Y0, 1);
        double feet = r.from;
        int ticks = 0;
        boolean turned = false;
        while (!(LiftShaft.arrived(feet, r.to) && (!r.blocked || turned)) && ticks < 1000) {
            double m = LiftShaft.motion(feet, r.goal(turned));
            assertTrue(Math.abs(m) <= LiftShaft.SPEED + 1e-9);
            feet += m;
            ticks++;
            if (r.blocked && !turned && LiftShaft.arrived(feet, r.peak)) turned = true;
        }
        assertEquals(r.ticks(), ticks);
        Col c = new Col().set(10, Cell.PAD)
            .set(20, Cell.SOLID)
            .set(30, Cell.PAD);
        Ride b = LiftShaft.plan(c, 10, 1);
        feet = b.from;
        ticks = 0;
        turned = false;
        double top = feet;
        while (!(LiftShaft.arrived(feet, b.to) && turned) && ticks < 1000) {
            feet += LiftShaft.motion(feet, b.goal(turned));
            top = Math.max(top, feet);
            ticks++;
            if (!turned && LiftShaft.arrived(feet, b.peak)) turned = true;
        }
        assertEquals(b.peak, top, 1e-9, "up to the bump");
        assertTrue(ticks <= b.ticks() + 1);
        assertEquals(LiftShaft.STEER_MAX, LiftShaft.steer(0, 10), 1e-9);
        assertEquals(-0.05, LiftShaft.steer(10.1, 10), 1e-9);
        assertTrue(LiftShaft.onColumn(5.5, 7.5, 5, 7));
        assertTrue(LiftShaft.onColumn(6.2, 7.5, 5, 7));
        assertFalse(LiftShaft.onColumn(6.3, 7.5, 5, 7));
        assertEquals(40, LiftShaft.GRACE, "two seconds");
        assertEquals(0.6, LiftShaft.SPEED, 1e-9);
    }

    @Test
    void theUnformedDaisPadCanBeBoarded() {
        // a phase-II nexus that came apart: its middle column's conduits stand over the dais pad as frame blocks, and
        // every player passes the two the standing room takes (formed, they let everyone through anyway)
        Col c = axis();
        assertTrue(LiftShaft.boardable(c.at(Y0 + 1), c.at(Y0), c.at(Y0 - 1)), "the conduit on the pad");
        assertTrue(LiftShaft.boardable(c.at(Y0 + 2), c.at(Y0 + 1), c.at(Y0)), "the one over it, the head's");
        assertFalse(LiftShaft.boardable(c.at(Y0 + 3), c.at(Y0 + 2), c.at(Y0 + 1)), "over the head: still solid");
        assertFalse(LiftShaft.boardable(c.at(Y0 + 4), c.at(Y0 + 3), c.at(Y0 + 2)));
        // standing there, a jump rides up the unformed column to the pod
        Ride r = LiftShaft.plan(c, Y0, 1);
        assertNotNull(r);
        assertEquals(Y0 + 41, r.to);
        assertFalse(r.blocked);
        // nothing but our blocks over a pad: not the pad itself, not a pad over a pad, not what is no one's
        assertFalse(LiftShaft.boardable(Cell.PAD, Cell.PAD, Cell.SOLID));
        assertFalse(LiftShaft.boardable(Cell.SOLID, Cell.PAD, Cell.SOLID));
        assertFalse(LiftShaft.boardable(Cell.OURS, Cell.SOLID, Cell.PAD));
        assertFalse(LiftShaft.boardable(Cell.OURS, Cell.OURS, Cell.OURS), "our blocks with no pad under them");
        assertTrue(LiftShaft.boardable(Cell.OURS, Cell.OPEN, Cell.PAD), "the head's cell over an empty one");
        assertFalse(LiftShaft.boardable(Cell.OURS, Cell.OPEN, Cell.OPEN), "nothing to stand on");
        assertFalse(LiftShaft.boardable(Cell.OURS, Cell.SOLID, Cell.PAD), "no one stands in a solid block");

        // the conduit on the pad is the block missing (broken, or not yet put back by a repair): the one over it,
        // where the head goes, is passed, the one over that still not, and a jump still rides up to the pod
        Col gap = axis().set(Y0 + 1, Cell.OPEN);
        assertTrue(LiftShaft.boardable(gap.at(Y0 + 2), gap.at(Y0 + 1), gap.at(Y0)), "the head's conduit");
        assertFalse(LiftShaft.boardable(gap.at(Y0 + 3), gap.at(Y0 + 2), gap.at(Y0 + 1)), "over the head");
        assertFalse(LiftShaft.boardable(gap.at(Y0 + 4), gap.at(Y0 + 3), gap.at(Y0 + 2)));
        Ride up = LiftShaft.plan(gap, Y0, 1);
        assertNotNull(up);
        assertEquals(Y0 + 41, up.to);
        assertFalse(up.blocked);
    }

    // ---- the landing (0.14.1, M11.1 §1)

    /** Vanilla's jump: the feet's height over the pad's top and the vertical motion at the start of the k-th tick. */
    private static double[] jump(int k, double ceiling) {
        double h = 0, v = 0.41999998688697815;
        for (int i = 0; i < k; i++) {
            if (Math.abs(v) < 0.005) v = 0;
            double want = v, next = Math.max(0, Math.min(ceiling, h + v));
            if (next != h + want) v = 0;
            h = next;
            v = (v - 0.08) * 0.9800000190734863;
        }
        return new double[] { h, v };
    }

    /**
     * The feet where the client's eased ride arrives, as Lift.clientTick rides it: the speed the player had towards the
     * goal carries on, and each tick the feet move by the eased motion (which vanilla drops when it is under 0.005)
     * until they arrived.
     */
    private static double eased(double from, double to, double feet, double motion) {
        double carried = Math.max(0, Math.signum(to - feet) * motion);
        int t = 0;
        while (!LiftShaft.arrived(feet, to)) {
            double m = LiftShaft.ease(feet, to, t, carried);
            assertTrue(Math.abs(m) <= LiftShaft.SPEED + 1e-12, "never faster than the speed: " + m);
            assertTrue(Math.signum(to - from) * (to - feet - m) >= -1e-12, "never past the goal");
            if (Math.abs(m) < 0.005) m = 0;
            feet += m;
            t++;
            assertTrue(t <= (int) Math.ceil(Math.abs(to - from) / LiftShaft.SPEED) + 12, "it arrives in time");
        }
        return feet;
    }

    /**
     * Every eased ride ends exactly on its stop height (0.14.1): every gap the lift rides (3..64), both ways, up from
     * where vanilla's jump has the feet when the server's answer comes 1..8 ticks after the press (under open air, and
     * under the unformed nexus's conduit that stops the head 1.2 over the pad), up from standing, and down from a
     * standing sneak. In 0.14.0 the last step could stop up to {@link LiftShaft#ARRIVED} short, inside the pad, and the
     * rider fell through it (the dais to the pod with the answer three ticks late: 0.04812 short).
     */
    @Test
    void everyEasedRideEndsExactlyOnItsStopHeight() {
        int rides = 0;
        for (int gap = LiftShaft.MIN_GAP; gap <= LiftShaft.REACH; gap++) {
            double low = 101, high = 101 + gap;
            for (double ceiling : new double[] { 99, 0.2 }) {
                for (int k = 1; k <= 8; k++) {
                    double[] j = jump(k, ceiling);
                    double feet = eased(low, high, low + j[0], j[1]);
                    assertEquals(high, feet, 0.0, "up " + gap + ", the answer " + k + " ticks after the press");
                    rides++;
                }
            }
            assertEquals(high, eased(low, high, low, -0.0784), 0.0, "up " + gap + " from standing");
            assertEquals(low, eased(high, low, high, -0.0784), 0.0, "down " + gap + " from a sneak");
            rides += 2;
        }
        assertEquals(62 * 18, rides);
        // the user's rides on a phase-II campus: the dais to the pod and the root island to the dais
        double[] j3 = jump(3, 99), j4 = jump(4, 99);
        assertEquals(Y0 + 41, eased(Y0 + 1, Y0 + 41, Y0 + 1 + j3[0], j3[1]), 0.0);
        assertEquals(Y0 + 1, eased(Y0 - 24, Y0 + 1, Y0 - 24 + j4[0], j4[1]), 0.0);
    }

    /** A blocked ride rises until the head touches what is in the way, exactly, and sinks back exactly onto its pad. */
    @Test
    void aBlockedRideTurnsExactlyAtItsPeakAndEndsOnItsPad() {
        for (int solid = 15; solid <= 60; solid++) {
            Ride r = LiftShaft.plan(
                new Col().set(10, Cell.PAD)
                    .set(solid, Cell.SOLID)
                    .set(70, Cell.PAD),
                10,
                1);
            assertTrue(r.blocked);
            double[] j = jump(2, 99);
            double feet = r.from + j[0], top = feet, carried = Math.max(0, j[1]);
            boolean turned = false;
            long legStart = 0;
            LiftShaft.End end = LiftShaft.End.RIDING;
            for (long t = 0; t < 400; t++) {
                if (LiftShaft.turns(r, turned, feet)) {
                    turned = true;
                    legStart = t;
                    carried = 0;
                }
                end = LiftShaft.ended(r, turned, feet, 0.5, 0.5, 0, 0, t);
                if (end != LiftShaft.End.RIDING) break;
                feet += LiftShaft.ease(feet, r.goal(turned), (int) (t - legStart), carried);
                top = Math.max(top, feet);
            }
            assertEquals(LiftShaft.End.ARRIVED, end);
            assertEquals(r.peak, top, 0.0, "the head just touches the block at " + solid);
            assertEquals(solid, top + LiftShaft.BODY, 1e-9);
            assertEquals(r.to, feet, 0.0, "back on the pad");
        }
    }

    /**
     * When a ride ends (the rule both sides share since 0.14.1): at its end, a blocked one only once it turned; lost
     * off the column or past its time; and feet that reached the end go onto it, while feet left inside the pad are
     * what the server's backstop sets onto it.
     */
    @Test
    void aRideEndsOnItsPadAndTheFeetGoOntoItsTop() {
        Ride r = LiftShaft.plan(axis(), Y0, 1);
        double to = r.to;
        assertEquals(LiftShaft.End.ARRIVED, LiftShaft.ended(r, false, to, 0.5, 0.5, 0, 0, 70));
        assertEquals(LiftShaft.End.ARRIVED, LiftShaft.ended(r, false, to - 0.049, 0.5, 0.5, 0, 0, 70));
        assertEquals(LiftShaft.End.RIDING, LiftShaft.ended(r, false, to - 0.051, 0.5, 0.5, 0, 0, 70));
        assertEquals(LiftShaft.End.RIDING, LiftShaft.ended(r, false, r.from, 0.5, 0.5, 0, 0, 0));
        assertEquals(LiftShaft.End.ARRIVED, LiftShaft.ended(r, false, to, 3, 0.5, 0, 0, 70), "arriving comes first");
        assertEquals(LiftShaft.End.LOST, LiftShaft.ended(r, false, Y0 + 20, 1.3, 0.5, 0, 0, 30), "off the column");
        long last = r.ticks() + LiftShaft.GRACE;
        assertEquals(LiftShaft.End.RIDING, LiftShaft.ended(r, false, Y0 + 20, 0.5, 0.5, 0, 0, last));
        assertEquals(LiftShaft.End.LOST, LiftShaft.ended(r, false, Y0 + 20, 0.5, 0.5, 0, 0, last + 1), "past its time");
        assertFalse(LiftShaft.turns(r, false, to), "a ride that is not blocked never turns");

        Ride b = LiftShaft.plan(
            new Col().set(10, Cell.PAD)
                .set(20, Cell.SOLID)
                .set(30, Cell.PAD),
            10,
            1);
        assertEquals(
            LiftShaft.End.RIDING,
            LiftShaft.ended(b, false, b.from, 0.5, 0.5, 0, 0, 0),
            "it starts on its end");
        assertFalse(LiftShaft.turns(b, false, b.peak - 0.06));
        assertTrue(LiftShaft.turns(b, false, b.peak - 0.04));
        assertFalse(LiftShaft.turns(b, true, b.peak), "once");
        assertEquals(LiftShaft.End.ARRIVED, LiftShaft.ended(b, true, b.from, 0.5, 0.5, 0, 0, 20));

        // the client sets feet that reached the end onto it; the server sets feet left inside the pad onto it
        assertEquals(to, LiftShaft.settle(to - 0.04812, to), 0.0, "out of the pad onto its top");
        assertEquals(to, LiftShaft.settle(to + 0.0084, to), 0.0, "a hair over it: onto it as well");
        assertEquals(to, LiftShaft.settle(to, to), 0.0);
        assertEquals(to - 0.051, LiftShaft.settle(to - 0.051, to), 0.0, "not there yet: where they are");
        assertEquals(to + 2, LiftShaft.settle(to + 2, to), 0.0);
        assertTrue(LiftShaft.sunk(to - 0.04812, to));
        assertTrue(LiftShaft.sunk(to - 1e-9, to));
        assertFalse(LiftShaft.sunk(to, to), "on its top");
        assertFalse(LiftShaft.sunk(to + 0.01, to), "over it: it falls onto it");
        assertFalse(LiftShaft.sunk(to - 0.051, to), "too deep for a ride that arrived");
    }
}
