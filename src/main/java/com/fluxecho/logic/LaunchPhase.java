package com.fluxecho.logic;

/**
 * The timing and the path of one launch of the campus builder as the client draws it (0.10.0 「营造」): a cargo cube
 * flies from the nexus core to its cell, the cell prints in bottom-up, and its outline flashes once it has landed.
 * The server only sends when a launch left the core and when it lands ({@code BUILD_FX}); everything in between is
 * worked out here from those two ticks and the time now, so the drawing needs no state and can be repeated as often
 * as a frame asks (the light gates draw the world again).
 * <p>
 * <b>What is shown.</b> The client learns of a launch late: the server sends its launches in batches every four
 * ticks, stamped with the tick each left the core, and the client handles the packet in its next tick. So each launch
 * is drawn on a timeline of its own ({@link #shownStart}, {@link #shownLand}): from the later of its launch and the
 * tick the client saw it, to its own landing. The cargo then flies a little faster than it left, but it always
 * arrives, and the cell prints in, by the tick the block appears (the builder flies every cell for at least 14 ticks,
 * {@code BuildPace.flightTicks}, so most of the flight is still seen). A launch seen after it landed shows only its
 * flash; one seen more than {@link #MAX_LATE} ticks after it landed is long over.
 * <p>
 * The phases, for a launch leaving at {@code start} and landing at {@code land} (world ticks, fractional with the
 * partial tick; the shown ones), its flight {@code D = land - start}:
 * <ul>
 * <li><b>Travel</b>: the cargo flies for {@code D - P} ticks, {@code P} the print below.</li>
 * <li><b>Print</b>: the last {@code P = min(PRINT, D / 2)} ticks before landing, right after the cargo arrives: a
 * long flight prints over its last {@link #PRINT} ticks, a short one over its second half.</li>
 * <li><b>Flash</b>: from landing, {@link #FLASH} ticks fading from 1 to 0; after that the launch is {@link #done}.</li>
 * </ul>
 * A launch whose landing tick lies before its start (a clock mix-up) is taken as landing when it starts. The arc
 * ({@link Arc}) is a quadratic Bézier curve whose highest point rises {@code APEX_RISE + APEX_SLOPE * horizontal
 * distance} above the higher of its two ends.
 */
public final class LaunchPhase {

    /**
     * Ticks a cell prints in before it lands, at most: a flight shorter than twice this prints over its second half.
     */
    public static final int PRINT = 8;
    /** How many ticks after its landing a launch may reach the client and still show its landing flash. */
    public static final int MAX_LATE = 8;
    /** Ticks the landing flash lasts. */
    public static final int FLASH = 6;
    /** The arc's apex: this many blocks above the higher end, and this many more per block of horizontal distance. */
    public static final double APEX_RISE = 3, APEX_SLOPE = 0.35;

    private LaunchPhase() {}

    // ---- the shown timeline

    /**
     * The tick a launch is shown leaving the core: the later of its launch and {@code seen}, the client tick it learned
     * of the launch; its own launch tick when it was seen more than {@link #MAX_LATE} ticks after it landed.
     */
    public static long shownStart(long start, long land, long seen) {
        if (seen > Math.max(start, land) + MAX_LATE) return start;
        return Math.max(start, seen);
    }

    /**
     * The tick a launch is shown landing: its own landing, the tick its block appears (never later, so the cargo is
     * never seen arriving at a block that is already there); {@link #shownStart} for a landing before that (a clock
     * mix-up, or a launch seen after it landed: nothing flies, the flash shows at once).
     */
    public static long shownLand(long start, long land, long seen) {
        if (seen > Math.max(start, land) + MAX_LATE) return land;
        return Math.max(land, shownStart(start, land, seen));
    }

    // ---- the phases

    /** The flight in ticks, {@code land - start}; never negative. */
    public static double flight(double start, double land) {
        double d = land - start;
        return d > 0 ? d : 0;
    }

    /** Ticks the cell prints in: {@code min(PRINT, flight / 2)}. */
    public static double printTicks(double start, double land) {
        return Math.min(PRINT, flight(start, land) / 2);
    }

    /** Ticks the cargo travels: the flight less its print. */
    public static double travelTicks(double start, double land) {
        return flight(start, land) - printTicks(start, land);
    }

    /** The tick the cargo arrives at its cell: {@code start + travelTicks}. */
    public static double arrival(double start, double land) {
        return start + travelTicks(start, land);
    }

    /**
     * How far the cargo has travelled, 0 at the launch (and before it) to 1 on arrival (and after it). A flight of
     * no length counts as arrived from its start on.
     */
    public static double travel(double start, double land, double now) {
        double ticks = travelTicks(start, land);
        if (!(ticks > 0)) return now >= start ? 1 : 0;
        return clamp((now - start) / ticks);
    }

    /** The tick the print begins: when the cargo arrives, {@link #printTicks} before landing. */
    public static double printStart(double start, double land) {
        return arrival(start, land);
    }

    /**
     * How far the cell has printed in, 0 until {@link #printStart} to 1 on landing (and after it). A flight of no
     * length prints at once when it lands.
     */
    public static double print(double start, double land, double now) {
        double from = printStart(start, land), end = start + flight(start, land), ticks = end - from;
        if (!(ticks > 0)) return now >= end ? 1 : 0;
        return clamp((now - from) / ticks);
    }

    /** The landing flash: 1 when the cell lands, fading evenly to 0 {@link #FLASH} ticks later; 0 before landing. */
    public static double flash(double land, double now) {
        double d = now - land;
        if (d < 0 || d >= FLASH) return 0;
        return 1 - d / FLASH;
    }

    /** Whether the cargo is in the air: launched and not yet arrived. */
    public static boolean flying(double start, double land, double now) {
        return now >= start && now < arrival(start, land);
    }

    /** Whether the launch has nothing left to show: its flash is over. */
    public static boolean done(double land, double now) {
        return now >= land + FLASH;
    }

    /** How far above the higher end the arc's apex rises for a launch covering this horizontal distance. */
    public static double apexRise(double horizontal) {
        return APEX_RISE + APEX_SLOPE * Math.max(0, horizontal);
    }

    private static double clamp(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    /**
     * The path of a launch: a quadratic Bézier curve from the source (the core) to the target (the cell's centre),
     * its control point straight above the middle of the two, at the height that puts the curve's highest point
     * exactly at {@link #apex}. For end heights {@code a} and {@code b} and a control height {@code m} the curve's top
     * is {@code (m^2 - ab) / (2m - a - b)}; setting it to {@code H} gives {@code m = H + sqrt((H - a)(H - b))}.
     */
    public static final class Arc {

        /** The source, the control point and the target. */
        public final double x0, y0, z0, mx, my, mz, x1, y1, z1;
        /** The height of the curve's highest point. */
        public final double apex;

        public Arc(double x0, double y0, double z0, double x1, double y1, double z1) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            apex = Math.max(y0, y1) + apexRise(Math.hypot(x1 - x0, z1 - z0));
            mx = (x0 + x1) / 2;
            mz = (z0 + z1) / 2;
            my = apex + Math.sqrt((apex - y0) * (apex - y1));
        }

        /** The point {@code t} along the curve (clamped to 0..1), written into {@code out} {x, y, z} and returned. */
        public double[] point(double t, double[] out) {
            double f = clamp(t), g = 1 - f;
            double a = g * g, b = 2 * g * f, c = f * f;
            out[0] = a * x0 + b * mx + c * x1;
            out[1] = a * y0 + b * my + c * y1;
            out[2] = a * z0 + b * mz + c * z1;
            return out;
        }

        /** The point {@code t} along the curve, a new {x, y, z}. */
        public double[] point(double t) {
            return point(t, new double[3]);
        }

        /**
         * The curve's derivative at {@code t} (clamped to 0..1), the way it heads there (not normalised), written into
         * {@code out} {x, y, z} and returned.
         */
        public double[] tangent(double t, double[] out) {
            double f = clamp(t), g = 1 - f;
            out[0] = 2 * g * (mx - x0) + 2 * f * (x1 - mx);
            out[1] = 2 * g * (my - y0) + 2 * f * (y1 - my);
            out[2] = 2 * g * (mz - z0) + 2 * f * (z1 - mz);
            return out;
        }

        /** The curve's derivative at {@code t}, a new {x, y, z}. */
        public double[] tangent(double t) {
            return tangent(t, new double[3]);
        }

        /** Where along the curve (0..1) it is highest. */
        public double apexAt() {
            double d = 2 * my - y0 - y1;
            return d > 0 ? clamp((my - y0) / d) : 0.5;
        }

        /** The curve's length, measured over {@code segments} straight pieces. */
        public double length(int segments) {
            int n = Math.max(1, segments);
            double[] p = point(0), q = new double[3];
            double len = 0;
            for (int i = 1; i <= n; i++) {
                point(i / (double) n, q);
                double dx = q[0] - p[0], dy = q[1] - p[1], dz = q[2] - p[2];
                len += Math.sqrt(dx * dx + dy * dy + dz * dz);
                double[] s = p;
                p = q;
                q = s;
            }
            return len;
        }
    }
}
