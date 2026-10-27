package com.fluxecho.client.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.ui.Xform;

/**
 * Clipping follows the current transform and nests by intersection (ui/FINAL.md section 2.3.2): a clip rectangle is
 * mapped through the 2D transform stack to the screen, intersected with the clip below it, and turned into a scissor
 * box in physical pixels with a lower-left origin. McFluxCanvas and the Java2D preview canvas share this arithmetic
 * ({@link Xform}), so the preview shows the cut the game makes.
 */
class CanvasClipTest {

    /** Factor 2 on an 854 x 480 window: the GUI is 427 x 240. */
    static final int F = 2, DISPLAY_H = 480;

    static int[] scissor(Xform x) {
        return Xform.scissor(x.clipBox(), F, DISPLAY_H);
    }

    @Test
    void translationMovesTheClip() {
        Xform x = new Xform();
        x.push(10, 20, 1);
        x.clip(0, 0, 50, 30);
        assertArrayEquals(new float[] { 10, 20, 60, 50 }, x.clipBox());
        assertArrayEquals(new int[] { 20, 380, 100, 60 }, scissor(x));
    }

    @Test
    void scaleScalesTheClip() {
        Xform x = new Xform();
        x.push(100, 50, 2);
        x.clip(5, 5, 20, 10);
        assertArrayEquals(new float[] { 110, 60, 150, 80 }, x.clipBox());
        assertArrayEquals(new int[] { 220, 320, 80, 40 }, scissor(x));
        // a second push composes: translate in the scaled space, scale again
        x.push(10, 0, 0.5f);
        assertEquals(120, x.toScreenX(0), 1e-6);
        assertEquals(125, x.toScreenX(5), 1e-6);
        assertEquals(1f, x.scale(), 1e-6);
    }

    @Test
    void nestedClipsIntersect() {
        Xform x = new Xform();
        x.clip(0, 0, 100, 100);
        x.push(50, 50, 1);
        x.clip(0, 0, 100, 100);
        assertArrayEquals(new float[] { 50, 50, 100, 100 }, x.clipBox());
        assertArrayEquals(new int[] { 100, 280, 100, 100 }, scissor(x));
        x.unclip();
        assertArrayEquals(new int[] { 0, 280, 200, 200 }, scissor(x), "back to the outer clip");
        x.unclip();
        assertNull(x.clipBox(), "no clip: the scissor test goes off");
        assertEquals(0, x.clipDepth());
    }

    @Test
    void disjointClipsLeaveNothing() {
        Xform x = new Xform();
        x.clip(0, 0, 10, 10);
        x.clip(20, 20, 5, 5);
        int[] s = scissor(x);
        assertEquals(0, s[2]);
        assertEquals(0, s[3]);
    }

    @Test
    void edgesRoundToThePixelGrid() {
        Xform x = new Xform();
        x.push(0.5f, 0, 1);
        x.clip(0, 0, 10.2f, 5);
        // factor 3: left 1.5 -> 2, right 10.7 * 3 = 32.1 -> 32
        assertArrayEquals(new int[] { 2, 300 - 15, 30, 15 }, Xform.scissor(x.clipBox(), 3, 300));
        // two clips side by side share their edge: no gap and no overlap
        Xform a = new Xform(), b = new Xform();
        a.clip(0, 0, 10.3f, 4);
        b.clip(10.3f, 0, 10, 4);
        int[] sa = Xform.scissor(a.clipBox(), 3, 300), sb = Xform.scissor(b.clipBox(), 3, 300);
        assertEquals(sa[0] + sa[2], sb[0]);
    }

    @Test
    void transformAndClipStacksAreIndependent() {
        Xform x = new Xform();
        x.push(10, 10, 1);
        x.clip(0, 0, 20, 20);
        x.pop();
        assertArrayEquals(new float[] { 10, 10, 30, 30 }, x.clipBox(), "popping the transform keeps the clip");
        x.push(100, 100, 3);
        assertArrayEquals(new float[] { 10, 10, 30, 30 }, x.clipBox(), "pushing a transform keeps the clip");
        assertEquals(1, x.depth());
        x.pop();
        assertEquals(0, x.depth());
        assertThrows(IllegalStateException.class, x::pop, "more pops than pushes is a bug in the page");
        x.unclip();
        assertThrows(IllegalStateException.class, x::unclip);
    }

    @Test
    void localPointsMapToTheScreen() {
        Xform x = new Xform();
        assertEquals(7, x.toScreenX(7), 0);
        x.push(10, 20, 2);
        assertEquals(10 + 2 * 3, x.toScreenX(3), 1e-6);
        assertEquals(20 + 2 * 4, x.toScreenY(4), 1e-6);
        x.reset();
        assertEquals(0, x.depth());
        assertEquals(3, x.toScreenX(3), 0);
    }

    // ------------------------------------------------------------------ the Java2D canvas cuts the same way

    static boolean painted(J2dFluxCanvas c, float gx, float gy) {
        return (c.image.getRGB((int) (gx * c.factor()), (int) (gy * c.factor())) >>> 24) != 0;
    }

    @Test
    void previewCanvasClipsThroughTheTransform() {
        J2dFluxCanvas c = new J2dFluxCanvas(100, 100, F, null);
        c.push(10, 10, 1);
        c.clip(0, 0, 20, 20);
        c.fill(-100, -100, 1000, 1000, 0xFFFF0000);
        c.unclip();
        c.pop();
        assertEquals(true, painted(c, 15, 15));
        assertEquals(false, painted(c, 5, 5));
        assertEquals(false, painted(c, 31, 31));
    }

    @Test
    void previewCanvasNestsAndScales() {
        J2dFluxCanvas c = new J2dFluxCanvas(100, 100, F, null);
        c.clip(0, 0, 55, 100);
        c.push(40, 40, 2);
        c.clip(0, 0, 10, 10); // screen 40..60, cut at 55 by the outer clip
        c.fill(0, 0, 100, 100, 0xFF00FF00);
        c.unclip();
        c.pop();
        c.unclip();
        assertEquals(true, painted(c, 45, 45));
        assertEquals(true, painted(c, 54.5f, 59.5f));
        assertEquals(false, painted(c, 56, 50), "outside the outer clip");
        assertEquals(false, painted(c, 45, 61), "outside the inner clip");
        assertEquals(false, painted(c, 39, 45));
        // with every clip gone, drawing reaches the whole canvas again
        c.fill(0, 0, 100, 100, 0xFF0000FF);
        assertEquals(true, painted(c, 99, 99));
    }
}
