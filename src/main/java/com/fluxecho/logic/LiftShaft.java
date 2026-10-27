package com.fluxecho.logic;

/**
 * 光梯 The light lift (0.12.0, CITY.md §2.5): light-lift pads (fitting LIFT) stacked in one column form a shaft. A
 * player standing on a pad who jumps rises to the next pad up, one who sneaks sinks to the next pad down, at
 * {@link #SPEED} blocks a tick. While riding the player passes through the pads and through our frame and fitting
 * blocks on the shaft line (the nexus's middle column, whether the nexus is formed or not), and (0.17.0, critique
 * feasibility #19) through the new material families' blocks, hull, footing and skins, so a shaft that runs through a
 * hull floor or a footing course still rides; it stops below anything else; a ride takes no fall damage, nor does
 * anything for {@link #GRACE} ticks after it.
 * <p>
 * This is the rule both sides share: what a ride does ({@link #plan}), where the player is moved each tick
 * ({@link #motion}, {@link #ease}), when the ride ends ({@link #ended}; since 0.14.1 exactly on its pad,
 * {@link #settle}), and which blocks a rider passes ({@link #passes}). The server plans the ride and tells the client,
 * which moves the player; both skip the same blocks' collision while the ride (and its grace) lasts, so the server's
 * check of the client's moves agrees with them and nothing rubber-bands.
 * <p>
 * Heights: a pad at block {@code y} carries a standing player's feet at {@code y + 1}; a player is
 * {@link #BODY} high, so the cells {@code y + 1} and {@code y + 2} must be free to stand there (our blocks in them let
 * players through, {@link #boardable}).
 * <ul>
 * <li>Up: the cells over the pad are looked at upward, at most {@link #REACH}. The first pad at least {@link #MIN_GAP}
 * above is the target, and the ride needs the cells from the start up to the two over the target free of anything
 * solid (pads and our blocks are not solid to a rider). When something solid is in the way, the ride rises until the
 * head is just under it ({@link Ride#peak}) and sinks back to the pad it started from: the player bumps the ceiling.
 * With no pad above within reach there is no ride (a capped shaft, such as the one over the zenith's pod until the
 * orbit is built, gives an ordinary jump).</li>
 * <li>Down: the cells under the pad are looked at downward, at most {@link #REACH}. The first pad at least
 * {@link #MIN_GAP} below is the target. When something solid comes first, the ride stops standing on it if there is
 * room under the pad for a player ({@link #MIN_GAP} or more below it), and is refused if not.</li>
 * <li>Nothing within reach either way (no pad, nothing solid): no ride.</li>
 * </ul>
 * Pads closer than {@link #MIN_GAP} to the start are passed over: a player standing on one would have its head in
 * the other.
 */
public final class LiftShaft {

    /** How fast a ride moves: 0.6 blocks a tick (12 a second). */
    public static final double SPEED = 0.6;
    /** How long after a ride a fall does no damage: 2 seconds. */
    public static final int GRACE = 40;
    /** How far a shaft is looked along for the next pad. */
    public static final int REACH = 64;
    /** The least distance between the start and a pad the ride stops on. */
    public static final int MIN_GAP = 3;
    /** A standing player's height. */
    public static final double BODY = 1.8;
    /** How close the feet must come to a goal to have reached it. */
    public static final double ARRIVED = 0.05;
    /** How far off the column's middle a rider may drift before the ride ends. */
    public static final double DRIFT = 0.75;
    /** How strongly a rider is steered back to the column's middle each tick, and the most it is moved sideways. */
    public static final double STEER = 0.5, STEER_MAX = 0.2;

    /** What a cell of the shaft holds, as far as a rider is concerned. */
    public enum Cell {
        /** Nothing a player collides with (air, a plant, water). */
        OPEN,
        /** One of our frame or fitting blocks, or (0.17.0) of the new material families: a rider passes through it. */
        OURS,
        /** A light-lift pad: a rider passes through it, a ride stops standing on it. */
        PAD,
        /** Anything else a player collides with: a ride stops below it (or standing on it). */
        SOLID
    }

    /** The cells of the shaft's column, by height. */
    public interface Column {

        Cell at(int y);
    }

    /** A planned ride: its direction and the feet heights it goes between. */
    public static final class Ride {

        /** +1 up, -1 down. */
        public final int dir;
        /** The feet's height at the start (on the start pad) and at the end. */
        public final double from, to;
        /**
         * Where the feet turn back on a blocked ride (the head just under what is in the way); {@link #to} otherwise.
         */
        public final double peak;
        /** Whether the ride bumps the ceiling and sinks back to {@link #from}. */
        public final boolean blocked;

        public Ride(int dir, double from, double to, double peak, boolean blocked) {
            this.dir = dir;
            this.from = from;
            this.to = to;
            this.peak = peak;
            this.blocked = blocked;
        }

        /** The lowest and the highest block a rider's body takes on the ride: {lo, hi}. */
        public int[] span() {
            double lo = Math.min(from, Math.min(to, peak)), hi = Math.max(from, Math.max(to, peak)) + BODY;
            // the pad the ride starts from (down) or ends on (up) is part of it
            return new int[] { (int) Math.floor(lo) - 1, (int) Math.ceil(hi) };
        }

        /** How many ticks the ride takes at {@link #SPEED}. */
        public int ticks() {
            double way = blocked ? 2 * Math.abs(peak - from) : Math.abs(to - from);
            return (int) Math.ceil(way / SPEED);
        }

        /** Where the feet go now: the peak until it was reached (on a blocked ride), then the end. */
        public double goal(boolean turned) {
            return blocked && !turned ? peak : to;
        }
    }

    private LiftShaft() {}

    /**
     * The ride from the pad at {@code padY} in direction {@code dir} (+1 up, -1 down), or null when there is none.
     *
     * @throws IllegalArgumentException for a direction other than +1 or -1
     */
    public static Ride plan(Column col, int padY, int dir) {
        if (dir != 1 && dir != -1) throw new IllegalArgumentException("a ride goes up (+1) or down (-1): " + dir);
        double start = padY + 1;
        if (dir > 0) {
            // the first solid cell on the way, if any; the two cells the player stands in are free, or it would not
            // stand there
            int solid = Integer.MIN_VALUE;
            for (int y = padY + 1; y <= padY + REACH; y++) {
                Cell c = col.at(y);
                if (c == Cell.SOLID && solid == Integer.MIN_VALUE) solid = y;
                if (c != Cell.PAD || y - padY < MIN_GAP) continue;
                // the target: is there room over it to stand?
                for (int k = 1; k <= 2 && solid == Integer.MIN_VALUE; k++)
                    if (col.at(y + k) == Cell.SOLID) solid = y + k;
                return solid == Integer.MIN_VALUE ? new Ride(1, start, y + 1, y + 1, false) : bump(start, solid);
            }
            return null;
        }
        for (int y = padY - 1; y >= padY - REACH; y--) {
            Cell c = col.at(y);
            if (c == Cell.SOLID) {
                // stand on it, when a player fits between it and the pad
                return padY - y >= MIN_GAP ? new Ride(-1, start, y + 1, y + 1, false) : null;
            }
            if (c == Cell.PAD && padY - y >= MIN_GAP) return new Ride(-1, start, y + 1, y + 1, false);
        }
        return null;
    }

    /** A ride up that bumps what is in the way at {@code solidY}, or null when there is no room to rise at all. */
    private static Ride bump(double start, int solidY) {
        double peak = solidY - BODY;
        if (peak - start < SPEED) return null;
        return new Ride(1, start, start, peak, true);
    }

    /** The vertical motion that takes the feet from {@code feet} towards {@code goal} this tick. */
    public static double motion(double feet, double goal) {
        return Math.max(-SPEED, Math.min(SPEED, goal - feet));
    }

    /**
     * How a ride gathers speed (0.12.0, the client's feel): its speed limit on the first tick of a leg, and how much
     * the limit grows each tick after, up to {@link #SPEED} on the sixth tick.
     */
    public static final double START = 0.15, ACCEL = 0.09;
    /**
     * How a ride settles onto its goal: its speed is at most this share of the way left (full speed until a block and a
     * half from the goal), but never under {@link #CREEP}, so it glides onto the pad instead of stopping dead.
     */
    public static final double BRAKE = 0.4, CREEP = 0.12;

    /**
     * The vertical motion this tick of a ride whose current leg (from the start, or from where a blocked ride turned)
     * is {@code ticks} ticks old, the feet at {@code feet} going to {@code goal}, still carrying {@code carried} (its
     * speed towards the goal when the leg began; a jump's, say): it gathers speed from that (or from {@link #START})
     * by {@link #ACCEL} a tick up to {@link #SPEED}, brakes over the last block and a half ({@link #BRAKE}, at least
     * {@link #CREEP}), and never passes the goal. A ride eased so takes about eight ticks longer than one at full speed
     * all the way, well within the {@link #GRACE} both sides allow it.
     * <p>
     * It lands exactly (0.14.1): a step that would leave the feet closer to the goal than {@link #ARRIVED} takes the
     * whole way left instead, so every eased leg ends exactly on its goal. In 0.14.0 such a step left the feet a few
     * hundredths short; the ride counted that as arrived, and on a ride up the feet stayed inside the pad, which is
     * solid again once the ride ends, so the rider fell through it back down the shaft (vanilla stops a fall only for
     * feet at or above a block's top). The last step is at most about 0.17 and the ride takes as many ticks as before.
     */
    public static double ease(double feet, double goal, int ticks, double carried) {
        double way = goal - feet, left = Math.abs(way);
        if (left < 1e-9) return 0;
        double gather = START + ACCEL * Math.max(0, ticks);
        double cap = Math.min(SPEED, Math.max(gather, Math.max(0, carried)));
        cap = Math.min(cap, Math.max(CREEP, BRAKE * left));
        double step = Math.min(cap, left);
        // what would be left is too little to ride on: take it now, so the leg ends exactly on its goal
        if (left - step < ARRIVED) step = left;
        return Math.copySign(step, way);
    }

    /** Whether the feet are at the goal. */
    public static boolean arrived(double feet, double goal) {
        return Math.abs(goal - feet) < ARRIVED;
    }

    /** How a ride stands after a look at where its rider is ({@link #ended}). */
    public enum End {
        /** It goes on. */
        RIDING,
        /** Its feet reached its end: it ends there, the feet set onto it ({@link #settle}). */
        ARRIVED,
        /** Its rider left the column, or it lasted {@link #GRACE} ticks longer than it should: it ends where it is. */
        LOST
    }

    /** Whether a blocked ride turns back now: it has not turned yet and its feet reached the peak. */
    public static boolean turns(Ride ride, boolean turned, double feet) {
        return ride.blocked && !turned && arrived(feet, ride.peak);
    }

    /**
     * Whether a ride ends now (0.14.1, the rule both sides share): it arrived when its feet reached its end (a blocked
     * ride only once it turned, {@link #turns}), it is lost when the rider at {@code (x, z)} left the column of the
     * block {@code (bx, bz)} ({@link #onColumn}) or the ride is {@code age} ticks old, {@link #GRACE} more than it
     * takes. The client judges where it moved its player; the server judges the position the client reported last,
     * never one its own physics step has already moved (in 0.14.0 that step's gravity hid the arrival of a ride up
     * until the timeout and cut rides down short in mid-air).
     */
    public static End ended(Ride ride, boolean turned, double feet, double x, double z, int bx, int bz, long age) {
        if ((!ride.blocked || turned) && arrived(feet, ride.to)) return End.ARRIVED;
        if (!onColumn(x, z, bx, bz) || age > ride.ticks() + GRACE) return End.LOST;
        return End.RIDING;
    }

    /**
     * Where the feet of a ride that reached its end at {@code to} go (0.14.1): exactly onto it when they are within
     * {@link #ARRIVED} of it, else where they are. The pad a ride ends on is solid again for its rider from then on,
     * and vanilla lets feet even a little inside a block fall through it, so the client sets them onto the pad's top
     * before the ride ends.
     */
    public static double settle(double feet, double to) {
        return arrived(feet, to) ? to : feet;
    }

    /**
     * Whether feet at {@code feet} ended a ride inside the block it stops on: less than {@link #ARRIVED} under its end
     * {@code to} (0.14.1). They would fall through it, so the server sets such a rider onto it, a backstop for whatever
     * kept the client from landing exactly; an eased ride never ends so ({@link #ease}).
     */
    public static boolean sunk(double feet, double to) {
        return feet < to && arrived(feet, to);
    }

    /** The sideways motion that steers a rider at {@code pos} back to the column's middle {@code mid}. */
    public static double steer(double pos, double mid) {
        return Math.max(-STEER_MAX, Math.min(STEER_MAX, (mid - pos) * STEER));
    }

    /** Whether a rider at {@code (x, z)} is still on the column whose block is {@code (bx, bz)}. */
    public static boolean onColumn(double x, double z, int bx, int bz) {
        return Math.abs(x - (bx + 0.5)) <= DRIFT && Math.abs(z - (bz + 0.5)) <= DRIFT;
    }

    /**
     * Whether a player passes through a cell of the ride's column: pads only while riding (so the ride ends standing
     * on one), our frame and fitting blocks while riding and during the grace after it (so a ride that ends inside the
     * nexus's column, unformed, does not trap anyone), nothing else ever.
     */
    public static boolean passes(Cell c, boolean riding, boolean grace) {
        if (c == Cell.PAD) return riding;
        if (c == Cell.OURS) return riding || grace;
        return false;
    }

    /**
     * Whether a player passes through a cell when it boards a pad, ridden or not: one of our frame or fitting blocks
     * (not a pad) directly over a pad, or the one over that when the pad is two below and the cell between is ours or
     * empty: the two cells a player standing on the pad takes. So a pad whose standing room our blocks fill can still
     * be boarded: the dais pad of a grown nexus under the conduits of its middle column while the nexus is unformed
     * (formed, they let players through anyway), also when the conduit on the pad is the block missing and the one
     * over it would stand in the head's way. The rule is the world's blocks alone, the same on both sides, so nothing
     * rubber-bands; the cell over those two stays solid, over the head of a player standing on the pad.
     *
     * @param self   the cell itself
     * @param below  the cell under it
     * @param below2 the cell two under it
     */
    public static boolean boardable(Cell self, Cell below, Cell below2) {
        if (self != Cell.OURS) return false;
        return below == Cell.PAD || (below == Cell.OURS || below == Cell.OPEN) && below2 == Cell.PAD;
    }
}
