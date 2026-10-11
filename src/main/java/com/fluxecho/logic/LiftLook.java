package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * What the light lift shows of itself (0.12.0, CITY.md §2.5: "the beam runs between pads in cyan with rising
 * motes"), worked out from a column's pads alone so the drawing never claims a ride the lift would not give: a beam
 * runs exactly where a ride up from a pad goes ({@link LiftShaft#plan}), from the top of that pad to the bottom of the
 * pad it stops on. A ride that would bump what is in its way shows as a short, dim stub up to it; a pad with no ride
 * up (a capped shaft, such as the pod's until the orbit is built) shows only its iris. Pure, so the tests hold the
 * beams of a campus's axis to its rides.
 * <p>
 * Also the rider's view: how wide the field of view opens with the ride's speed.
 */
public final class LiftLook {

    /** How much wider the field of view grows at full speed (a share of the normal). */
    public static final double FOV = 0.07;
    /** How far apart the hoops a rider rushes past stand, and how many stand round the camera each way. */
    public static final double HOOP_STEP = 1.5;
    public static final int HOOPS = 6;

    private LiftLook() {}

    /** One beam of a shaft: from {@code lo} (the top of the pad it leaves) up to {@code hi}; a stub when blocked. */
    public static final class Beam {

        public final double lo, hi;
        /** Whether the ride up bumps something in the way at {@code hi} (a dim stub) rather than reaching a pad. */
        public final boolean blocked;
        /** The pad it leaves (its block's y). */
        public final int pad;

        public Beam(int pad, double lo, double hi, boolean blocked) {
            this.pad = pad;
            this.lo = lo;
            this.hi = hi;
            this.blocked = blocked;
        }

        @Override
        public String toString() {
            return "Beam[" + pad + ": " + lo + ".." + hi + (blocked ? " blocked" : "") + "]";
        }
    }

    /**
     * The beams of a column whose pads are at {@code pads} (block heights, any order, repeats ignored): for each pad,
     * the ride up from it. Two pads whose rides end at the same pad (a pad closer than {@link LiftShaft#MIN_GAP} over
     * another is passed over) give one beam, from the lower. Lowest first.
     */
    public static List<Beam> beams(LiftShaft.Column col, int[] pads) {
        int[] p = pads.clone();
        Arrays.sort(p);
        List<Beam> out = new ArrayList<>();
        double lastTop = Double.NaN;
        for (int i = 0; i < p.length; i++) {
            if (i > 0 && p[i] == p[i - 1]) continue;
            LiftShaft.Ride r = LiftShaft.plan(col, p[i], 1);
            if (r == null) continue;
            double top = r.blocked ? r.peak + LiftShaft.BODY : r.to - 1;
            // a pad passed over by the ride from the pad below it rides to the same top
            if (!out.isEmpty() && top == lastTop) continue;
            out.add(new Beam(p[i], p[i] + 1, top, r.blocked));
            lastTop = top;
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * The field of view while riding, as a factor of the normal: wider with the speed ({@code speed} blocks a tick,
     * either way), {@link #FOV} more at {@link LiftShaft#SPEED}.
     */
    public static double fov(double speed) {
        double f = Math.min(1, Math.abs(speed) / LiftShaft.SPEED);
        return 1 + FOV * f * f;
    }

    /**
     * Where the hoops a rider rushes past stand this frame: the heights of the {@code 2 * HOOPS + 1} hoops nearest the
     * camera's {@code eye}, every {@link #HOOP_STEP} from the beam's foot {@code lo}, those outside {@code lo..hi} left
     * out. They are fixed to the shaft, so they stream past as the rider moves.
     */
    public static double[] hoops(double lo, double hi, double eye) {
        double k = Math.floor((eye - lo) / HOOP_STEP);
        double[] out = new double[2 * HOOPS + 1];
        int n = 0;
        for (int i = -HOOPS; i <= HOOPS; i++) {
            double y = lo + (k + i) * HOOP_STEP;
            if (y < lo || y > hi) continue;
            out[n++] = y;
        }
        return Arrays.copyOf(out, n);
    }
}
