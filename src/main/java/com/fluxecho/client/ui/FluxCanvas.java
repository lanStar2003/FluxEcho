package com.fluxecho.client.ui;

import net.minecraft.item.ItemStack;

import com.fluxecho.logic.ui.Lay;
import com.fluxlite.gui.ui.Canvas;

/**
 * The drawing surface of the new UI (ui/FINAL.md section 2.3): FluxLite's {@link Canvas} plus the shapes the
 * "clear-sky astrolabe" design is made of (cut-corner panels, ring segments, radial glows, dashes), atlas sprites and
 * nine-slices, FluxMono text, items, blend modes, a 2D transform stack and the overlay layer. Pages draw only through
 * this interface and their own data, never through Minecraft's client classes, so every page also renders in tests
 * with the Java2D canvas to a PNG preview.
 * <p>
 * Two implementations: {@code McFluxCanvas} draws in the game with the Tessellator, quads only; {@code J2dFluxCanvas}
 * (tests) draws with Java2D. Both keep the transform and clip stacks in {@code logic.ui.Xform}, so
 * {@link #clip} follows the current transform and nested clips intersect (2.3.2), the same arithmetic in both.
 * <p>
 * Angles are degrees clockwise from twelve o'clock (a progress ring starts at the top and runs clockwise). Colours
 * are ARGB. Sizes and positions are GUI pixels in the current transform.
 */
public interface FluxCanvas extends Canvas {

    /** How the next shapes mix with what is below. Multiply premixes the colour towards white by its alpha. */
    enum Blend {
        NORMAL,
        ADD,
        MULTIPLY
    }

    enum Align {
        LEFT,
        CENTER,
        RIGHT
    }

    /** The GUI scale factor (physical pixels per GUI pixel). */
    int factor();

    /** A position on the physical pixel grid. */
    default float snap(float v) {
        return Lay.snap(v, factor());
    }

    /**
     * A panel with its top-right corner cut at 45 degrees by {@code cut} and the other three corners bevelled by 1 px
     * (they read as rounded): three quads.
     */
    void chamfer(float x, float y, float w, float h, float cut, int argb);

    /** {@link #chamfer} with a vertical gradient. */
    void chamferGradient(float x, float y, float w, float h, float cut, int top, int bottom);

    /** The outline of a {@link #chamfer} panel, {@code t} thick, inside its edge: eight quads. */
    void chamferStroke(float x, float y, float w, float h, float cut, float t, int argb);

    /**
     * A ring segment from radius {@code r0} to {@code r1}, angle {@code a0} to {@code a1} (degrees clockwise from
     * twelve o'clock), colour running from {@code c0} to {@code c1} along it: one quad per 6 degrees. A full disc is
     * {@code r0 = 0}.
     */
    void arc(float cx, float cy, float r0, float r1, float a0, float a1, int c0, int c1);

    /** A disc fading from {@code center} to {@code edge}: 24 quads. */
    void radial(float cx, float cy, float r, int center, int edge);

    /**
     * A dashed polyline: dashes {@code on} long with {@code off} gaps, the pattern shifted by {@code phase} (a flowing
     * dash takes its phase from the clock, {@code Anim.flow}).
     */
    void dash(float[] xs, float[] ys, float width, float on, float off, float phase, int argb);

    /** An atlas sprite ({@code AtlasMap} id) stretched to the box, tinted. */
    void sprite(int id, float x, float y, float w, float h, int argb);

    /** A nine-slice atlas sprite: corners keep their size (border / 4 GUI px), edges and middle stretch. */
    void nine(int id, float x, float y, float w, float h, int argb);

    /** The blend mode for what follows. Always set it back to {@link Blend#NORMAL}. */
    void blend(Blend mode);

    /**
     * FluxMono text at one of its sizes (6, 8, 12, 16, 24), {@code y} the top of the glyphs; returns the width. A
     * character FluxMono lacks advances without drawing.
     */
    default float mono(String s, float x, float y, int size, int argb, Align align) {
        return mono(s, x, y, size, argb, align, 0);
    }

    /** FluxMono text with {@code tracking} px between glyphs (eyebrows use +1). */
    float mono(String s, float x, float y, int size, int argb, Align align, int tracking);

    /**
     * An item icon {@code size} px wide at (x, y), with its count and durability bar when {@code overlay}; the item is
     * drawn at z 100 and its count at z 200, under the overlay layer.
     */
    void item(ItemStack stack, float x, float y, float size, boolean overlay);

    /** Translates by (dx, dy), then scales by {@code s}; clips set after this follow it. */
    void push(float dx, float dy, float s);

    /** Undoes the last {@link #push}. */
    void pop();

    /**
     * Draws {@code layer} as an overlay (ink card, drawer, menu, toast): lifted to z 300 with its panels writing depth,
     * so page text the font batcher defers cannot show through it (1.6.7). Depth testing stays on to the end of the
     * frame.
     */
    void overlay(Runnable layer);
}
