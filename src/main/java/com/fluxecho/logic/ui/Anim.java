package com.fluxecho.logic.ui;

/**
 * Motion tokens and easings of the new UI (ui/FINAL.md section 1.7). Every animation is a pure function of the clock
 * ({@code Minecraft.getSystemTime()} in milliseconds, or world time in the world): nothing counts frames, so drawing a
 * frame twice (a light gate draws the world again) gives the same picture. Enter and leave animations need the time an
 * element appeared, which the screen keeps; loops need nothing.
 */
public final class Anim {

    private Anim() {}

    /** Hover colour, ink card fade-in. */
    public static final int MICRO = 90;
    /** Press, toggles, tab underline, in-page switches, the hold button letting go. */
    public static final int SHORT = 160;
    /** Panels opening, drawers, toasts coming in, number rolls. */
    public static final int MEDIUM = 240;
    /** Flying the star chart to a star, making room, page changes. */
    public static final int LONG = 420;
    /** Lighting a star, phase growth, an elevation growing, leap beats. */
    public static final int EPIC = 900;
    /**
     * Loops: breathing (researchable stars, slots that take the held item, waiting lights), the running light's
     * pulse, the scan along a busy card.
     */
    public static final int BREATHE = 2400, PULSE = 1200, SCAN = 3000;
    /** Filling a hold-to-confirm button. */
    public static final int HOLD = 2000;
    /** One step of an unlocked digit of the homeward coordinates. */
    public static final int DECODE = 120;
    /** Flow along star lines and energy ribbons, px per second (the slowest; faster flows scale it). */
    public static final float FLOW = 40;
    /** Hover time before the ink card shows (C07), caret blink (C24). */
    public static final int TOOLTIP_DELAY = 250, CARET = 530;
    /** The window opening: line, then panel, with the content fading in from the end of the line (C01). */
    public static final int OPEN_LINE = 90, OPEN_PANEL = 150, OPEN_CONTENT = 120, OPEN_TOTAL = OPEN_LINE + OPEN_PANEL;

    // ------------------------------------------------------------------ easings, t in 0..1 (clamped)

    private static float clamp(float t) {
        return t <= 0 ? 0 : t >= 1 ? 1 : t;
    }

    public static float linear(float t) {
        return clamp(t);
    }

    /** {@code out}: cubic-bezier(0.2, 0.8, 0.2, 1), fast then settling. */
    public static float out(float t) {
        return bezier(clamp(t), 0.2f, 0.8f, 0.2f, 1f);
    }

    /** {@code inOut}: sine, symmetric. */
    public static float inOut(float t) {
        return (float) (0.5 - 0.5 * Math.cos(Math.PI * clamp(t)));
    }

    /** {@code back}: overshoots a little, then settles at 1 (the "out + back" of the epic token). */
    public static float back(float t) {
        float x = clamp(t) - 1;
        float s = 1.70158f;
        return 1 + x * x * ((s + 1) * x + s);
    }

    /** {@code step}: 0 until the end, then 1. */
    public static float step(float t) {
        return t >= 1 ? 1 : 0;
    }

    /** A CSS cubic-bezier from (0,0) to (1,1) through control points (x1, y1), (x2, y2), evaluated at x = t. */
    static float bezier(float t, float x1, float y1, float x2, float y2) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        // solve x(u) = t for u by Newton's method, falling back to bisection
        double u = t;
        for (int i = 0; i < 8; i++) {
            double x = cubic(u, x1, x2) - t, dx = slope(u, x1, x2);
            if (Math.abs(x) < 1e-6) break;
            if (Math.abs(dx) < 1e-6) break;
            u -= x / dx;
        }
        if (u < 0 || u > 1 || Math.abs(cubic(u, x1, x2) - t) > 1e-5) {
            double lo = 0, hi = 1;
            u = t;
            for (int i = 0; i < 40; i++) {
                if (cubic(u, x1, x2) < t) lo = u;
                else hi = u;
                u = (lo + hi) / 2;
            }
        }
        return (float) cubic(u, y1, y2);
    }

    private static double cubic(double u, double p1, double p2) {
        double v = 1 - u;
        return 3 * v * v * u * p1 + 3 * v * u * u * p2 + u * u * u;
    }

    private static double slope(double u, double p1, double p2) {
        double v = 1 - u;
        return 3 * v * v * p1 + 6 * v * u * (p2 - p1) + 3 * u * u * (1 - p2);
    }

    // ------------------------------------------------------------------ time

    /** How far {@code now} is through an animation that started at {@code start}, 0..1. */
    public static float progress(long now, long start, int duration) {
        if (duration <= 0) return 1;
        return clamp((now - start) / (float) duration);
    }

    /** Breathing: 0 -> 1 -> 0 over 2.4 s, a sine. */
    public static float breathe(long now) {
        double phase = Math.floorMod(now, (long) BREATHE) / (double) BREATHE;
        return (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * phase));
    }

    /** The running light's ring: 0 -> 1 over 1.2 s, eased out, then again. */
    public static float pulse(long now) {
        return out(Math.floorMod(now, (long) PULSE) / (float) PULSE);
    }

    /** The scan along a busy card: 0 -> 1 over 3 s, linear, then again. */
    public static float scan(long now) {
        return Math.floorMod(now, (long) SCAN) / (float) SCAN;
    }

    /** Offset of a flow moving at {@code speed} px/s, wrapped to {@code period} px (a dash pattern's length). */
    public static float flow(long now, float speed, float period) {
        if (period <= 0) return 0;
        double d = now / 1000.0 * speed;
        return (float) (d - Math.floor(d / period) * period);
    }

    /** The digit an unlocked position of the homeward coordinates shows at {@code now}: changes every 120 ms. */
    public static int decodeDigit(int position, long now) {
        long step = Math.floorDiv(now, (long) DECODE);
        long h = step * 0x9E3779B97F4A7C15L + position * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (int) Math.floorMod(h, 10L);
    }

    // ------------------------------------------------------------------ the window opening (C01, 1.7.2 rule 6)

    /** Where a window is in its opening: the line, the panel's height, the content's opacity, each 0..1. */
    public static final class Opening {

        public final float line, panel, content;

        Opening(float line, float panel, float content) {
            this.line = line;
            this.panel = panel;
            this.content = content;
        }

        /** The panel's height: it grows from 2 px to {@code full}. */
        public float height(float full) {
            return 2 + (full - 2) * panel;
        }
    }

    /**
     * The opening {@code elapsed} ms after it began: a bright line sweeps across (0-90 ms), the panel grows from 2 px
     * to full height (90-240 ms), the content fades in (90-210 ms). FluxLite's {@code HoloPanel} and the world cards
     * use the same three steps.
     */
    public static Opening open(long elapsed) {
        float line = out(elapsed / (float) OPEN_LINE);
        float panel = out((elapsed - OPEN_LINE) / (float) OPEN_PANEL);
        float content = out((elapsed - OPEN_LINE) / (float) OPEN_CONTENT);
        return new Opening(line, panel, content);
    }

    // ------------------------------------------------------------------ number rolls (1.7.2 rule 5)

    /**
     * A number that rolls to a new value over 240 ms, or jumps when the change is more than ten times. The screen keeps
     * one per animated number; {@link #value} is a pure function of the stored change and the clock.
     */
    public static final class Roll {

        private double from, to;
        private long since = Long.MIN_VALUE;

        /** Sets the target at {@code now}. The first value, and a jump of more than ten times, are shown at once. */
        public void set(double target, long now) {
            if (since == Long.MIN_VALUE || jump(value(now), target)) {
                from = to = target;
                since = now;
                return;
            }
            if (target == to) return;
            from = value(now);
            to = target;
            since = now;
        }

        /** The number to draw at {@code now}. */
        public double value(long now) {
            if (since == Long.MIN_VALUE) return 0;
            return from + (to - from) * out(progress(now, since, MEDIUM));
        }

        private static boolean jump(double a, double b) {
            double x = Math.abs(a), y = Math.abs(b);
            if (x == 0 || y == 0) return x != y;
            return Math.max(x, y) / Math.min(x, y) > 10;
        }
    }
}
