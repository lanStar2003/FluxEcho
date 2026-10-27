package com.fluxecho.logic.ui;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The 2D transform stack and the clip stack of a {@code FluxCanvas} (ui/FINAL.md section 2.3.2). Transforms are a
 * translation and a uniform scale ({@code push(dx, dy, s)} is {@code glTranslatef(dx, dy, 0); glScalef(s, s, 1)}):
 * they move drawers sliding in, pages changing and the zoomed star chart. A clip rectangle is given in the current
 * local coordinates, mapped to the screen through the current transform, and intersected with the clip below it, so
 * a list inside a sliding drawer is cut where the drawer is, not where it would be without the slide. The two stacks
 * are independent: popping a transform leaves the clip where it was put.
 * <p>
 * The game canvas sends the top clip to {@code glScissor} ({@link #scissor}: physical pixels, lower-left origin); the
 * Java2D preview canvas uses the same box, so a preview shows the cut the game makes. Pure arithmetic, no GL.
 */
public final class Xform {

    /** Current transform: screen = (tx, ty) + s * local. */
    private float tx, ty, s = 1;
    private final Deque<float[]> saved = new ArrayDeque<>();
    /** Screen-space clip boxes {x0, y0, x1, y1}, innermost on top. */
    private final Deque<float[]> clips = new ArrayDeque<>();

    /** Translates by (dx, dy) in the current space, then scales by {@code scale}. */
    public void push(float dx, float dy, float scale) {
        saved.push(new float[] { tx, ty, s });
        tx += dx * s;
        ty += dy * s;
        s *= scale;
    }

    /** Undoes the last {@link #push}. */
    public void pop() {
        if (saved.isEmpty()) throw new IllegalStateException("pop without push");
        float[] p = saved.pop();
        tx = p[0];
        ty = p[1];
        s = p[2];
    }

    /** How many transforms are pushed. */
    public int depth() {
        return saved.size();
    }

    public float scale() {
        return s;
    }

    public float toScreenX(float x) {
        return tx + x * s;
    }

    public float toScreenY(float y) {
        return ty + y * s;
    }

    /**
     * Clips to a rectangle in local coordinates, intersected with the current clip; returns the new screen box
     * {@code {x0, y0, x1, y1}} (empty when they do not meet: x1 = x0, y1 = y0).
     */
    public float[] clip(float x, float y, float w, float h) {
        float x0 = toScreenX(x), y0 = toScreenY(y), x1 = toScreenX(x + w), y1 = toScreenY(y + h);
        if (x1 < x0) {
            float t = x0;
            x0 = x1;
            x1 = t;
        }
        if (y1 < y0) {
            float t = y0;
            y0 = y1;
            y1 = t;
        }
        float[] top = clips.peek();
        if (top != null) {
            x0 = Math.max(x0, top[0]);
            y0 = Math.max(y0, top[1]);
            x1 = Math.min(x1, top[2]);
            y1 = Math.min(y1, top[3]);
        }
        if (x1 < x0) x1 = x0;
        if (y1 < y0) y1 = y0;
        float[] box = { x0, y0, x1, y1 };
        clips.push(box);
        return box.clone();
    }

    /** Undoes the last {@link #clip}. */
    public void unclip() {
        if (clips.isEmpty()) throw new IllegalStateException("unclip without clip");
        clips.pop();
    }

    /** The current screen clip box {@code {x0, y0, x1, y1}}, or null when nothing clips. */
    public float[] clipBox() {
        float[] top = clips.peek();
        return top == null ? null : top.clone();
    }

    public int clipDepth() {
        return clips.size();
    }

    /** Back to no transform and no clip (start of a frame). */
    public void reset() {
        saved.clear();
        clips.clear();
        tx = ty = 0;
        s = 1;
    }

    /**
     * A screen box in GUI units as a {@code glScissor} box {x, y, w, h} in physical pixels with a lower-left origin.
     * Edges round to the nearest pixel, so boxes that share an edge share it exactly.
     */
    public static int[] scissor(float[] box, int factor, int displayHeight) {
        int f = Math.max(1, factor);
        int x0 = Math.round(box[0] * f), y0 = Math.round(box[1] * f);
        int x1 = Math.round(box[2] * f), y1 = Math.round(box[3] * f);
        return new int[] { x0, displayHeight - y1, Math.max(0, x1 - x0), Math.max(0, y1 - y0) };
    }

    /** A screen box as a device rectangle {x, y, w, h} with a top-left origin (the Java2D preview). */
    public static int[] device(float[] box, int factor) {
        int f = Math.max(1, factor);
        int x0 = Math.round(box[0] * f), y0 = Math.round(box[1] * f);
        int x1 = Math.round(box[2] * f), y1 = Math.round(box[3] * f);
        return new int[] { x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0) };
    }
}
