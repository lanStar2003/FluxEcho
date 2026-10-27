package com.fluxecho.logic.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * The outlines the new UI's shapes are cut from, shared by the game canvas and the Java2D preview canvas so that both
 * draw the same geometry (ui/FINAL.md section 2.3.1): the cut-corner panel and its stroke, rounded rectangles, ring
 * segments and dash patterns. Outlines are flat {x0, y0, x1, y1, ...} arrays, clockwise on the screen (y down). Pure
 * arithmetic.
 */
public final class UiGeo {

    private UiGeo() {}

    /** How much a 45-degree corner cut shrinks when its outline is moved inwards by 1 (2 - sqrt 2). */
    static final float CUT_INSET = (float) (2 - Math.sqrt(2));

    /**
     * The eight corners of a cut-corner panel: the top-right corner cut by {@code cut} at 45 degrees, the other three
     * by {@code bevel}; both are kept within half the smaller side. Clockwise from the top edge's left end.
     */
    public static float[] chamfer(float x, float y, float w, float h, float cut, float bevel) {
        float half = Math.max(0, Math.min(w, h) / 2);
        float c = Math.max(0, Math.min(cut, half)), b = Math.max(0, Math.min(bevel, half));
        return new float[] { x + b, y, x + w - c, y, x + w, y + c, x + w, y + h - b, x + w - b, y + h, x + b, y + h, x,
            y + h - b, x, y + b };
    }

    /** The outline of the same panel moved inwards by {@code t}: the inner edge of a {@code t}-thick stroke. */
    public static float[] chamferInner(float x, float y, float w, float h, float cut, float bevel, float t) {
        float half = Math.max(0, Math.min(w, h) / 2);
        float c = Math.max(0, Math.min(cut, half)), b = Math.max(0, Math.min(bevel, half));
        return chamfer(
            x + t,
            y + t,
            Math.max(0, w - 2 * t),
            Math.max(0, h - 2 * t),
            Math.max(0, c - t * CUT_INSET),
            Math.max(0, b - t * CUT_INSET));
    }

    /** Corner segments of a rounded corner of radius {@code r} at a GUI scale factor: 2 to 14. */
    public static int segments(float r, int factor) {
        return Math.max(2, Math.min(14, (int) (r * Math.max(1, factor) / 1.5f)));
    }

    /** The outline of a rounded rectangle, clockwise from the top edge, {@code seg} segments per corner. */
    public static float[] rounded(float x, float y, float w, float h, float r, int seg) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        float[][] centres = { { x + w - r, y + r }, { x + w - r, y + h - r }, { x + r, y + h - r }, { x + r, y + r } };
        float[] out = new float[4 * (seg + 1) * 2];
        int n = 0;
        for (int c = 0; c < 4; c++) {
            double start = Math.toRadians(-90 + c * 90);
            for (int i = 0; i <= seg; i++) {
                double a = start + Math.toRadians(90.0 * i / seg);
                out[n++] = centres[c][0] + (float) Math.cos(a) * r;
                out[n++] = centres[c][1] + (float) Math.sin(a) * r;
            }
        }
        return out;
    }

    /** A point at {@code deg} degrees clockwise from twelve o'clock, radius {@code r}: {x, y}. */
    public static float[] polar(float cx, float cy, float r, float deg) {
        double a = Math.toRadians(deg);
        return new float[] { cx + (float) (r * Math.sin(a)), cy - (float) (r * Math.cos(a)) };
    }

    /** Pieces of an arc: one per 6 degrees, at least one. */
    public static int arcSteps(float a0, float a1) {
        return Math.max(1, (int) Math.ceil(Math.abs(a1 - a0) / 6f));
    }

    /**
     * The dashes along a polyline as segments {x0, y0, x1, y1}: {@code on} drawn, {@code off} skipped, the pattern
     * moved forward by {@code phase} (so a growing phase makes the dashes flow from the first point to the last).
     */
    public static List<float[]> dashes(float[] xs, float[] ys, float on, float off, float phase) {
        List<float[]> out = new ArrayList<>();
        int n = Math.min(xs.length, ys.length);
        float period = on + off;
        if (n < 2 || on <= 0) return out;
        if (period <= 0 || off <= 0) {
            for (int i = 0; i + 1 < n; i++) out.add(new float[] { xs[i], ys[i], xs[i + 1], ys[i + 1] });
            return out;
        }
        // distance along the line where the pattern starts: a dash begins at every k * period + phase
        float pos = phase % period;
        if (pos > 0) pos -= period;
        float walked = 0;
        for (int i = 0; i + 1 < n; i++) {
            float dx = xs[i + 1] - xs[i], dy = ys[i + 1] - ys[i];
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len <= 0) continue;
            float s0 = walked, s1 = walked + len;
            // every dash [d, d + on] that meets [s0, s1]
            float d = pos + (float) Math.floor((s0 - pos) / period) * period;
            for (; d < s1; d += period) {
                float a = Math.max(d, s0), b = Math.min(d + on, s1);
                if (b <= a) continue;
                float ta = (a - s0) / len, tb = (b - s0) / len;
                out.add(new float[] { xs[i] + dx * ta, ys[i] + dy * ta, xs[i] + dx * tb, ys[i] + dy * tb });
            }
            walked = s1;
        }
        return out;
    }
}
