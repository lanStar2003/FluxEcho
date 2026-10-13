package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.LiftShaft.Cell;
import com.fluxecho.logic.LiftShaft.End;
import com.fluxecho.logic.LiftShaft.Ride;

/**
 * The light lift ridden end to end (0.14.1, M11.1 §1): the user's rides "did not stop on top". A test-only model of
 * one player on a 1.7.10 server and its own client, tick by tick, riding the real {@link LiftShaft} and
 * {@link LiftInput}:
 * <ul>
 * <li>vanilla's body: gravity and drag, the jump, the motion under 0.005 dropped, and a block that stops a fall only
 * when the feet are at or above its top (a body already inside a block falls through it);</li>
 * <li>the client: the server's answers handed over at the start of its tick, then {@code Lift.clientTick} (eased,
 * ended, the feet set onto the pad) and {@code LiftClient}'s keys, the move with the column's collision boxes, and the
 * movement packet (a position only when the feet moved more than 0.03, or every 20 ticks);</li>
 * <li>the server: the ride requests at the start of its tick ({@code Lift.request}), then each movement packet as
 * {@code NetHandlerPlayServer.processPlayer} handles it: the player's own tick with {@code Lift.serverStart}, the
 * server's own physics step and {@code Lift.serverTick} inside it, the reset to the last reported position, its
 * check of the move against the boxes shrunk by 0.0625 and the set-back when the move went into one, the fall
 * damage;</li>
 * <li>a latency each way, in ticks.</li>
 * </ul>
 * The player stands in the middle of the shaft's column, so only that column's cells matter. A ride must end with the
 * feet exactly on its pad and keep them there; the server must end it no earlier than the client and soon after, on
 * the feet the client reported. The 0.14.0 server and client are kept as switches of the model, to show what they did.
 */
class LiftRideTest {

    private static final double GRAVITY = 0.08, DRAG = 0.9800000190734863, JUMP = 0.41999998688697815;
    private static final double H = LiftShaft.BODY, CONTRACT = 0.0625, MIDDLE = 0.5;
    /** The vertical motion of a player standing still on the ground at the start of its tick. */
    private static final double STANDING = -GRAVITY * DRAG;
    private static final int Y0 = 64;

    /** One side's ride, as {@code Lift.State} keeps it. */
    private static final class Side {

        final Ride ride;
        final int lo, hi;
        final long start;
        long legStart, graceUntil;
        boolean riding = true, turned;
        double carried = Double.NaN, seenFeet = Double.NaN;

        Side(Ride ride, long now) {
            this.ride = ride;
            int[] span = ride.span();
            lo = span[0];
            hi = span[1];
            start = legStart = now;
        }

        double goal() {
            return ride.goal(turned);
        }

        void end(long now) {
            riding = false;
            graceUntil = now + LiftShaft.GRACE;
        }
    }

    private enum Kind {
        LIFT,
        RIDE,
        MOVE,
        SETBACK
    }

    /** A packet on its way: due at a tick. */
    private static final class Msg {

        final long at;
        final Kind kind;
        final double y;
        final boolean onGround, hasPos;
        final int[] ask;
        final Ride ride;

        Msg(long at, Kind kind, double y, boolean onGround, boolean hasPos, int[] ask, Ride ride) {
            this.at = at;
            this.kind = kind;
            this.y = y;
            this.onGround = onGround;
            this.hasPos = hasPos;
            this.ask = ask;
            this.ride = ride;
        }
    }

    /** One player, its client and the server, over a shaft's column. */
    private static final class Sim {

        final Map<Integer, Cell> col = new HashMap<>();
        /** Our frame blocks of a formed multiblock: every player passes them. */
        final Set<Integer> dissolved = new HashSet<>();
        final int up, down;
        /** The 0.14.0 server: it judges the ride after its own physics step and keeps its fall. */
        boolean server0140;
        /** The 0.14.0 client: it eases as 0.14.0 did and leaves the feet where they are when the ride ends. */
        boolean client0140;

        final List<Msg> toServer = new ArrayList<>(), toClient = new ArrayList<>();
        final boolean[] jumpKey = new boolean[2000], sneakKey = new boolean[2000];
        final double[] feetAt = new double[2000];
        long now;

        // the client
        double cY, cMotion, oldMinY;
        boolean cGround = true;
        int jumpTicks, ticksSince;
        Side client;
        final LiftInput input = new LiftInput();
        long waitUntil = Long.MIN_VALUE;

        // the server
        double sY, sMotion, lastPosY;
        boolean sGround = true, hasMoved = true;
        Side server;
        double fallDistance;
        int damage;

        // what happened
        final List<long[]> clientEnds = new ArrayList<>(), serverEnds = new ArrayList<>();
        final List<Double> serverSaw = new ArrayList<>();
        int starts, refused, setBacks, backstops, cut;

        Sim(int up, int down) {
            this.up = up;
            this.down = down;
        }

        Cell at(int y) {
            if (y < 0) return Cell.SOLID;
            return col.getOrDefault(y, Cell.OPEN);
        }

        Sim fill(int y0, int y1, Cell c) {
            for (int y = y0; y <= y1; y++) col.put(y, c);
            return this;
        }

        /** The player stands on the pad at {@code padY}. */
        Sim standOn(int padY) {
            cY = sY = lastPosY = oldMinY = padY + 1;
            cMotion = sMotion = STANDING;
            return this;
        }

        /** Jump (+1) or sneak (-1) held from {@code at} for {@code hold} ticks. */
        Sim press(int dir, long at, int hold) {
            for (long t = at; t < at + hold; t++) (dir > 0 ? jumpKey : sneakKey)[(int) t] = true;
            return this;
        }

        Sim run(int ticks) {
            for (int i = 0; i < ticks; i++) {
                clientTick(now);
                serverTick(now);
                feetAt[(int) now] = cY;
                now++;
            }
            return this;
        }

        // ---- the collision boxes (BlockFitting / BlockFrame asking Lift.passes and Lift.boardable)

        boolean passes(Side s, long t, int y, boolean pad) {
            if (s == null || y < s.lo || y > s.hi) return false;
            return LiftShaft.passes(pad ? Cell.PAD : Cell.OURS, s.riding, !s.riding && t <= s.graceUntil);
        }

        boolean boardable(int y) {
            Cell below = at(y - 1);
            if (below != Cell.PAD && below != Cell.OURS && below != Cell.OPEN) return false;
            return LiftShaft.boardable(at(y), below, at(y - 2));
        }

        boolean solid(Side s, long t, int y) {
            Cell c = at(y);
            if (c == Cell.OPEN) return false;
            if (c == Cell.SOLID) return true;
            if (c == Cell.OURS && dissolved.contains(y)) return false;
            if (passes(s, t, y, c == Cell.PAD)) return false;
            return !(c == Cell.OURS && boardable(y));
        }

        /**
         * Vanilla's vertical move ({@code AxisAlignedBB.calculateYOffset}): a box stops the body only when the body
         * is wholly on one side of it; one it is already inside does not. Returns {the new feet, 1 if stopped}.
         */
        double[] moveY(Side s, long t, double feet, double dy) {
            double lo = Math.min(feet, feet + dy), hi = Math.max(feet + H, feet + H + dy), y = dy;
            for (int b = (int) Math.floor(lo) - 1; b <= (int) Math.floor(hi) + 1; b++) {
                if (!solid(s, t, b) || !(b + 1 > lo && b < hi)) continue;
                if (y > 0 && feet + H <= b) y = Math.min(y, b - (feet + H));
                if (y < 0 && feet >= b + 1) y = Math.max(y, b + 1 - feet);
            }
            return new double[] { feet + y, y != dy ? 1 : 0 };
        }

        boolean collidesShrunk(Side s, long t, double feet) {
            double lo = feet + CONTRACT, hi = feet + H - CONTRACT;
            for (int b = (int) Math.floor(lo) - 1; b <= (int) Math.floor(hi) + 1; b++)
                if (solid(s, t, b) && b + 1 > lo && b < hi) return true;
            return false;
        }

        // ---- the client

        int padUnder() {
            if (!cGround) return Integer.MIN_VALUE;
            int y = (int) Math.floor(cY - 0.01);
            return at(y) == Cell.PAD ? y : Integer.MIN_VALUE;
        }

        /** {@code Lift.clientTick}. */
        boolean clientRide(long t) {
            Side c = client;
            if (c == null) return false;
            if (!c.riding) {
                if (t > c.graceUntil) client = null;
                return false;
            }
            double feet = cY;
            if (LiftShaft.turns(c.ride, c.turned, feet)) {
                c.turned = true;
                c.legStart = t;
                c.carried = 0;
            }
            End end = LiftShaft.ended(c.ride, c.turned, feet, MIDDLE, MIDDLE, 0, 0, t - c.start);
            if (end != End.RIDING) {
                if (end == End.ARRIVED && !client0140) cY += LiftShaft.settle(feet, c.ride.to) - feet;
                c.end(t);
                cMotion = 0;
                clientEnds.add(new long[] { t, end.ordinal() });
                return false;
            }
            if (Double.isNaN(c.carried)) c.carried = Math.max(0, Math.signum(c.goal() - feet) * cMotion);
            int age = (int) (t - c.legStart);
            cMotion = client0140 ? ease0140(feet, c.goal(), age, c.carried)
                : LiftShaft.ease(feet, c.goal(), age, c.carried);
            return true;
        }

        void clientTick(long t) {
            // the pump at the start of the client's tick: the server's answers (Lift.receive)
            for (Iterator<Msg> it = toClient.iterator(); it.hasNext();) {
                Msg m = it.next();
                if (m.at > t || m.kind != Kind.RIDE) continue;
                it.remove();
                waitUntil = Long.MIN_VALUE;
                if (m.ride != null) {
                    client = new Side(m.ride, t);
                    starts++;
                } else if (client != null && client.riding) {
                    // the server ended the ride before the client did
                    cut++;
                    if (!client0140) cY += LiftShaft.settle(cY, client.ride.to) - cY;
                    client.end(t);
                }
            }
            // the start of the player's tick: the ride, then the keys (LiftClient)
            boolean riding = clientRide(t);
            boolean free = !riding && !(client != null && client.riding)
                && (waitUntil == Long.MIN_VALUE || t >= waitUntil);
            int pad = padUnder();
            int[] ask = input.tick(
                t,
                jumpKey[(int) t],
                sneakKey[(int) t],
                pad == Integer.MIN_VALUE ? null : new int[] { 0, pad, 0 },
                MIDDLE,
                cY,
                MIDDLE,
                free);
            if (ask != null) {
                toServer.add(new Msg(t + up, Kind.LIFT, 0, false, false, ask, null));
                waitUntil = t + 20;
            }
            // the living update: the motion under 0.005 dropped, the jump
            if (jumpTicks > 0) jumpTicks--;
            if (Math.abs(cMotion) < 0.005) cMotion = 0;
            if (jumpKey[(int) t]) {
                if (cGround && jumpTicks == 0) {
                    cMotion = JUMP;
                    jumpTicks = 10;
                }
            } else jumpTicks = 0;
            // the move, then gravity and drag
            double want = cMotion;
            double[] r = moveY(client, t, cY, want);
            cY = r[0];
            cGround = r[1] != 0 && want < 0;
            if (r[1] != 0) cMotion = 0;
            cMotion = (cMotion - GRAVITY) * DRAG;
            // the movement packet
            ticksSince++;
            double d = cY - oldMinY;
            if (d * d > 9.0E-4 || ticksSince >= 20) {
                toServer.add(new Msg(t + up, Kind.MOVE, cY, cGround, true, null, null));
                oldMinY = cY;
                ticksSince = 0;
            } else toServer.add(new Msg(t + up, Kind.MOVE, 0, cGround, false, null, null));
            // a set-back from the server: the client takes the position as it is told and answers with it
            for (Iterator<Msg> it = toClient.iterator(); it.hasNext();) {
                Msg m = it.next();
                if (m.at > t || m.kind != Kind.SETBACK) continue;
                it.remove();
                cMotion = 0;
                cY = (m.y + 1.6200000047683716) - (double) 1.62f;
                toServer.add(new Msg(t + up, Kind.MOVE, cY, false, true, null, null));
            }
        }

        // ---- the server

        void serverTick(long t) {
            // the pump at the start of the server's tick: the ride requests (Lift.request)
            for (Iterator<Msg> it = toServer.iterator(); it.hasNext();) {
                Msg m = it.next();
                if (m.at > t || m.kind != Kind.LIFT) continue;
                it.remove();
                int y = m.ask[1], dir = m.ask[3];
                boolean ok = (server == null || !server.riding) && at(y) == Cell.PAD
                    && LiftInput.boards(MIDDLE, sY, MIDDLE, 0, y, 0);
                Ride ride = ok ? LiftShaft.plan(this::at, y, dir) : null;
                if (ride == null) refused++;
                else {
                    server = new Side(ride, t);
                    fallDistance = 0;
                }
                toClient.add(new Msg(t + 1 + down, Kind.RIDE, 0, false, false, null, ride));
            }
            // the network: each movement packet that came, in order
            for (Iterator<Msg> it = toServer.iterator(); it.hasNext();) {
                Msg m = it.next();
                if (m.at > t || m.kind != Kind.MOVE) continue;
                it.remove();
                processPlayer(t, m);
            }
        }

        void setPlayerLocation(long t, double y) {
            hasMoved = false;
            lastPosY = y;
            sY = y;
            toClient.add(new Msg(t + 1 + down, Kind.SETBACK, y, false, true, null, null));
        }

        /** {@code Lift.serverStart}. */
        void liftServerStart() {
            Side s = server;
            if (server0140 || s == null || !s.riding) return;
            s.seenFeet = sY;
            sMotion = 0;
        }

        /** {@code Lift.serverTick}. */
        void liftServerTick(long t, double prevFeet) {
            Side s = server;
            if (s == null) return;
            if (!s.riding) {
                if (t > s.graceUntil) server = null;
                else fallDistance = 0;
                return;
            }
            fallDistance = 0;
            double feet = server0140 ? sY : Double.isNaN(s.seenFeet) ? prevFeet : s.seenFeet;
            s.seenFeet = Double.NaN;
            if (LiftShaft.turns(s.ride, s.turned, feet)) s.turned = true;
            End end = LiftShaft.ended(s.ride, s.turned, feet, MIDDLE, MIDDLE, 0, 0, t - s.start);
            if (end == End.RIDING) return;
            s.end(t);
            serverEnds.add(new long[] { t, end.ordinal() });
            serverSaw.add(feet);
            if (!server0140 && end == End.ARRIVED && LiftShaft.sunk(feet, s.ride.to)) {
                backstops++;
                setPlayerLocation(t, s.ride.to);
            }
            toClient.add(new Msg(t + 1 + down, Kind.RIDE, 0, false, false, null, null));
        }

        /** {@code NetHandlerPlayServer.processPlayer}, the vertical part. */
        void processPlayer(long t, Msg m) {
            if (!hasMoved && m.hasPos && (m.y - lastPosY) * (m.y - lastPosY) < 0.01) hasMoved = true;
            if (!hasMoved) return;
            double d0 = sY, d2 = m.hasPos ? m.y : sY;
            lastPosY = sY;
            // the player's own tick: its start, the server's own physics step, its end
            liftServerStart();
            if (Math.abs(sMotion) < 0.005) sMotion = 0;
            double want = sMotion;
            double[] r = moveY(server, t, sY, want);
            sY = r[0];
            sGround = r[1] != 0 && want < 0;
            if (r[1] != 0) sMotion = 0;
            sMotion = (sMotion - GRAVITY) * DRAG;
            liftServerTick(t, lastPosY);
            // back to the last reported position, then the client's move checked from there
            sY = lastPosY;
            if (!hasMoved) return;
            double d5 = d2 - sY;
            boolean clear = !collidesShrunk(server, t, sY);
            if (sGround && !m.onGround && d5 > 0) sMotion = JUMP;
            r = moveY(server, t, sY, d5);
            if (r[1] != 0) sMotion = 0;
            sY = d2;
            if (clear && collidesShrunk(server, t, sY)) {
                setBacks++;
                setPlayerLocation(t, lastPosY);
                return;
            }
            sGround = m.onGround;
            double dy = sY - d0;
            if (sGround) {
                boolean safe = server != null && (server.riding || t <= server.graceUntil);
                if (fallDistance > 3 && !safe) damage += (int) Math.ceil(fallDistance - 3);
                fallDistance = 0;
            } else if (dy < 0) fallDistance -= dy;
        }

        // ---- what came of it

        /** The client's first ride: when it ended and how; null when it never rode. */
        long[] clientEnd() {
            return clientEnds.isEmpty() ? null : clientEnds.get(0);
        }
    }

    /** 0.14.0's {@code LiftShaft.ease}: its last step could stop short of the goal by less than {@code ARRIVED}. */
    private static double ease0140(double feet, double goal, int ticks, double carried) {
        double way = goal - feet, left = Math.abs(way);
        if (left < 1e-9) return 0;
        double gather = LiftShaft.START + LiftShaft.ACCEL * Math.max(0, ticks);
        double cap = Math.min(LiftShaft.SPEED, Math.max(gather, Math.max(0, carried)));
        cap = Math.min(cap, Math.max(LiftShaft.CREEP, LiftShaft.BRAKE * left));
        return Math.copySign(Math.min(cap, left), way);
    }

    /** A shaft of two pads {@code gap} apart over solid ground. */
    private static Sim shaft(int gap, int up, int down) {
        return new Sim(up, down).fill(0, 99, Cell.SOLID)
            .fill(100, 100, Cell.PAD)
            .fill(100 + gap, 100 + gap, Cell.PAD);
    }

    /**
     * The axis of a phase-II campus: the Tidal Well's island pad at Y0 - 25 in its open shaft, the dais pad at Y0
     * under the nexus's middle column (our frame blocks, passed by everyone when the nexus is formed), the pod's pad at
     * Y0 + 40 under its capped shaft.
     */
    private static Sim axis(boolean formed, int up, int down) {
        Sim s = new Sim(up, down).fill(0, Y0 - 26, Cell.SOLID)
            .fill(Y0 - 25, Y0 - 25, Cell.PAD)
            .fill(Y0, Y0, Cell.PAD)
            .fill(Y0 + 1, Y0 + 4, Cell.OURS)
            .fill(Y0 + 6, Y0 + 9, Cell.OURS)
            .fill(Y0 + 40, Y0 + 40, Cell.PAD)
            .fill(Y0 + 46, Y0 + 48, Cell.SOLID)
            .fill(Y0 + 49, Y0 + 49, Cell.OURS);
        if (formed) for (int y = Y0 + 1; y <= Y0 + 9; y++) if (s.at(y) == Cell.OURS) s.dissolved.add(y);
        return s;
    }

    /**
     * The player's ride ended standing on the pad at {@code to} and stayed there for 60 more ticks; the server ended it
     * on the feet the client reported, there, no earlier than the client and no later than the latency up after it;
     * nothing was cut short, set back or hurt.
     */
    private static void assertLandsAndStays(Sim s, int to, String what) {
        double feet = to + 1;
        long[] c = s.clientEnd();
        assertNotNull(c, what + ": it rides");
        assertEquals(1, s.starts, what);
        assertEquals(End.ARRIVED.ordinal(), c[1], what + ": the client's ride arrived");
        assertTrue(c[0] + 60 < s.now, what + ": watched for 60 ticks after");
        for (long t = c[0]; t < s.now; t++)
            assertEquals(feet, s.feetAt[(int) t], 0.0, what + ": on the pad's top at tick " + t);
        assertTrue(s.cGround, what + ": standing");
        assertEquals(1, s.serverEnds.size(), what);
        long[] e = s.serverEnds.get(0);
        assertEquals(End.ARRIVED.ordinal(), e[1], what + ": the server saw it arrive");
        assertEquals(feet, s.serverSaw.get(0), 0.0, what + ": on the feet the client reported");
        assertTrue(e[0] >= c[0] && e[0] - c[0] <= s.up, what + ": the server ended " + (e[0] - c[0]) + " ticks after");
        assertEquals(feet, s.sY, 0.0, what + ": the server has the player there too");
        assertEquals(0, s.cut, what + ": not cut short in mid-air");
        assertEquals(0, s.setBacks + s.backstops, what + ": never set back");
        assertEquals(0, s.damage, what);
        assertEquals(0, s.refused, what);
    }

    /**
     * The user's rides on a phase-II campus, formed or not: the dais up to the pod, the pod down to the dais, the root
     * island up to the dais and back down, the answer coming 0..3 ticks after the press each way, a tap or a held key.
     * In 0.14.0 about four in ten rides up ended inside the pad and fell back down the shaft to the start.
     */
    @Test
    void theAxisRidesLandOnTheirPadsAndStay() {
        int[][] rides = { { Y0, Y0 + 40 }, { Y0 + 40, Y0 }, { Y0 - 25, Y0 }, { Y0, Y0 - 25 } };
        for (boolean formed : new boolean[] { true, false }) for (int up = 0; up <= 3; up++)
            for (int down = 0; down <= 3; down++) for (int[] r : rides) for (int hold : new int[] { 1, 3 }) {
                int dir = r[1] > r[0] ? 1 : -1;
                Sim s = axis(formed, up, down).standOn(r[0])
                    .press(dir, 5, hold)
                    .run(260);
                assertLandsAndStays(
                    s,
                    r[1],
                    (formed ? "formed " : "unformed ") + r[0] + " -> " + r[1] + ", latency " + up + "/" + down);
            }
    }

    /**
     * Every gap the lift rides, both ways, every latency 0..3 each way (the sweep of the 0.14.1 investigation: 992
     * rides each way). In 0.14.0, 395 rides up fell back to the start pad and 256 rides down were cut short in mid-air
     * by the server.
     */
    @Test
    void everyGapEitherWayLandsAndStays() {
        int n = 0;
        for (int up = 0; up <= 3; up++) for (int down = 0; down <= 3; down++)
            for (int gap = LiftShaft.MIN_GAP; gap <= LiftShaft.REACH; gap++) for (int dir = -1; dir <= 1; dir += 2) {
                int from = dir > 0 ? 100 : 100 + gap, to = dir > 0 ? 100 + gap : 100;
                Sim s = shaft(gap, up, down).standOn(from)
                    .press(dir, 5, 1)
                    .run(300);
                assertLandsAndStays(s, to, "gap " + gap + (dir > 0 ? " up" : " down") + ", latency " + up + "/" + down);
                n++;
            }
        assertEquals(1984, n);
    }

    /**
     * A ride up that bumps something in the way rises until the head touches it and sinks back onto its pad; the
     * server sees it turn and arrive on the feet the client reported, so it ends the ride there too, not by the
     * timeout.
     */
    @Test
    void aBlockedRideBumpsAndLandsBackOnItsPad() {
        for (int lag = 0; lag <= 3; lag++) for (int solid = 104; solid <= 160; solid += 4) {
            Sim s = shaft(64, lag, lag).fill(solid, solid, Cell.SOLID)
                .standOn(100)
                .press(1, 5, 1)
                .run(300);
            assertLandsAndStays(s, 100, "a block at " + solid + ", latency " + lag);
        }
    }

    /**
     * Why the feet must be exactly on the pad: a body whose feet are a hair inside a pad that is solid for it (its ride
     * ended) falls through it, as the riders of 0.14.0 did, while one on its top stays. The model's collision is
     * vanilla's.
     */
    @Test
    void feetInsideAPadFallThroughIt() {
        Sim w = shaft(40, 0, 0);
        for (double start : new double[] { 141 - 0.04812, 141 - 0.015720, 141 - 1e-9 }) {
            double feet = start, motion = 0;
            for (int t = 0; t < 80; t++) {
                double[] r = w.moveY(null, t, feet, motion);
                feet = r[0];
                if (r[1] != 0) motion = 0;
                motion = (motion - GRAVITY) * DRAG;
            }
            assertEquals(101, feet, 0.0, "through the pad and down the shaft onto the pad below, from " + start);
        }
        double feet = 141, motion = 0;
        for (int t = 0; t < 80; t++) {
            double[] r = w.moveY(null, t, feet, motion);
            feet = r[0];
            if (r[1] != 0) motion = 0;
            motion = (motion - GRAVITY) * DRAG;
        }
        assertEquals(141, feet, 0.0, "on its top it stays");
    }

    /**
     * A ride back right after landing is taken: the server ends a ride about one packet after the client, where in
     * 0.14.0 it rode on until its timeout (about 36 ticks after the client) and refused a new one meanwhile.
     */
    @Test
    void aReturnTripRightAfterLandingIsTaken() {
        for (boolean old : new boolean[] { false, true }) for (int lag = 0; lag <= 3; lag++) {
            Sim s = axis(true, lag, lag).standOn(Y0)
                .press(1, 5, 1);
            s.server0140 = old;
            while (s.clientEnd() == null && s.now < 400) s.run(1);
            assertNotNull(s.clientEnd(), "it rides up");
            long landed = s.clientEnd()[0];
            s.press(-1, landed + 2 * lag + 4, 1)
                .run(240);
            String what = (old ? "0.14.0's server" : "lag ") + lag;
            if (old) {
                assertEquals(1, s.refused, what + ": still riding up on the server, it refuses the ride back");
                continue;
            }
            assertEquals(2, s.starts, what + ": the ride back starts");
            assertEquals(0, s.refused, what);
            assertEquals(2, s.clientEnds.size(), what);
            long back = s.clientEnds.get(1)[0];
            assertTrue(back + 60 < s.now, what);
            for (long t = back; t < s.now; t++)
                assertEquals(Y0 + 1, s.feetAt[(int) t], 0.0, what + ": back on the dais");
            assertEquals(0, s.setBacks + s.backstops + s.cut + s.damage, what);
        }
    }

    /**
     * The server's backstop: a client that still leaves the feet inside the pad (0.14.0's, here) is set onto the pad
     * by the server, which now judges the feet the client reported: one set-back, then it stands there. With 0.14.0's
     * server as well it falls back down the shaft, as the user saw.
     */
    @Test
    void theServerSetsFeetLeftInsideThePadOntoIt() {
        int caught = 0;
        for (int up = 0; up <= 3; up++) for (int down = 0; down <= 3; down++) for (int at = 5; at <= 8; at++) {
            Sim s = axis(true, up, down).standOn(Y0)
                .press(1, at, 1);
            s.client0140 = true;
            s.run(300);
            String what = "latency " + up + "/" + down + ", press at " + at;
            assertEquals(Y0 + 41, s.cY, 0.0, what + ": on the pod's pad");
            assertTrue(s.cGround, what);
            // the backstop, and at most one more set-back when a packet of the fall that followed is taken for the
            // client's answer to it (vanilla takes any within 0.1)
            assertTrue(s.backstops <= 1 && s.setBacks <= s.backstops, what);
            assertEquals(0, s.damage, what);
            caught += s.backstops;
        }
        assertTrue(caught > 0, "some of 0.14.0's rides end inside the pad: " + caught);

        // the user's ride as 0.14.0 rode it: the answer three ticks after the press, short by 0.04812
        Sim old = axis(true, 1, 1).standOn(Y0)
            .press(1, 5, 1);
        old.client0140 = true;
        old.server0140 = true;
        old.run(300);
        assertEquals(Y0 + 1, old.cY, 0.0, "back on the dais");
        assertEquals(0, old.backstops);
    }

    /**
     * 0.14.0's server judged the ride after its own physics step, whose gravity it let fall through the passed pads:
     * rides down were cut short in mid-air and rides up ended only by the timeout. Judging the reported feet ends
     * every ride on its pad.
     */
    @Test
    void theOldServerJudgementCutRidesShort() {
        int cut = 0, timedOut = 0;
        for (int gap = LiftShaft.MIN_GAP; gap <= LiftShaft.REACH; gap++) for (int dir = -1; dir <= 1; dir += 2) {
            Sim s = shaft(gap, 1, 1).standOn(dir > 0 ? 100 : 100 + gap)
                .press(dir, 5, 1);
            s.server0140 = true;
            s.run(300);
            cut += s.cut;
            if (!s.serverEnds.isEmpty() && s.serverEnds.get(0)[1] == End.LOST.ordinal()) timedOut++;
        }
        assertTrue(cut > 0, "rides down cut short in mid-air");
        assertTrue(timedOut > 0, "rides up that ended only by the timeout");
    }
}
