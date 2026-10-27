package com.fluxecho.logic.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.ui.Lay.Frame;
import com.fluxecho.logic.ui.Lay.Rect;
import com.fluxecho.logic.ui.Lay.Tier;

/**
 * The one breakpoint function and the console frame (ui/FINAL.md section 1.5.2, 2.5): S below 400 wide, L from 600 x
 * 300, M otherwise; M = 427 x 240 is the design canvas (the user's 854 x 480 window at factor 2).
 */
class LayTest {

    /** The 17 sizes of the LayoutFitTest table in 2.5, with their tiers. */
    static final Object[][] SIZES = { { 320, 240, Tier.S }, { 426, 240, Tier.M }, { 427, 240, Tier.M },
        { 399, 240, Tier.S }, { 400, 240, Tier.M }, { 480, 270, Tier.M }, { 512, 288, Tier.M }, { 599, 384, Tier.M },
        { 600, 299, Tier.M }, { 600, 300, Tier.L }, { 640, 360, Tier.L }, { 683, 384, Tier.L }, { 800, 450, Tier.L },
        { 853, 480, Tier.L }, { 960, 540, Tier.L }, { 1280, 720, Tier.L }, { 1920, 1080, Tier.L } };

    @Test
    void breakpointsOfTheSizeTable() {
        assertEquals(17, SIZES.length);
        for (Object[] s : SIZES) assertEquals(s[2], Lay.breakpoint((int) s[0], (int) s[1]), s[0] + "x" + s[1]);
        // the rest of 1.5.2's table
        assertEquals(Tier.M, Lay.breakpoint(Lay.M_W, Lay.M_H));
        assertEquals(427, Lay.M_W);
        assertEquals(240, Lay.M_H);
        assertEquals(Tier.M, Lay.breakpoint(1000, 299), "wide but short is M");
        assertEquals(Tier.S, Lay.breakpoint(399, 1000), "narrow is S whatever the height");
    }

    /** Bars, rail and drawer per tier (1.5.2). */
    @Test
    void barsPerTier() {
        assertEquals(18, Lay.topBar(Tier.S));
        assertEquals(18, Lay.topBar(Tier.M));
        assertEquals(22, Lay.topBar(Tier.L));
        assertEquals(24, Lay.rail(Tier.S));
        assertEquals(60, Lay.rail(Tier.M));
        assertEquals(84, Lay.rail(Tier.L));
        assertEquals(14, Lay.bottomBar(Tier.S));
        assertEquals(14, Lay.bottomBar(Tier.M));
        assertEquals(16, Lay.bottomBar(Tier.L));
        assertEquals(152, Lay.drawer(Tier.S));
        assertEquals(152, Lay.drawer(Tier.M));
        assertEquals(196, Lay.drawer(Tier.L));
    }

    /** The content-area examples of 1.5.2: x0 = rail + 4, w = W - rail - 8; y0 = top + 4, h = H - top - bottom - 8. */
    @Test
    void contentAreaExamples() {
        content(320, 240, 28, 22, 288, 200);
        content(427, 240, 64, 22, 359, 200);
        content(480, 270, 64, 22, 412, 230);
        content(640, 360, 88, 26, 548, 314);
        content(683, 384, 88, 26, 591, 338);
    }

    static void content(int w, int h, int x, int y, int cw, int ch) {
        Rect c = Lay.console(w, h).content;
        assertEquals(new Rect(x, y, cw, ch), c, w + "x" + h);
    }

    @Test
    void framePiecesTileTheScreen() {
        for (Object[] s : SIZES) {
            int w = (int) s[0], h = (int) s[1];
            Frame f = Lay.console(w, h);
            assertEquals(s[2], f.tier);
            assertEquals(new Rect(0, 0, w, Lay.topBar(f.tier)), f.top);
            assertEquals(new Rect(0, h - Lay.bottomBar(f.tier), w, Lay.bottomBar(f.tier)), f.bottom);
            assertEquals(new Rect(0, f.top.h, Lay.rail(f.tier), h - f.top.h - f.bottom.h), f.rail);
            assertEquals(
                new Rect(w - Lay.drawer(f.tier), f.top.h, Lay.drawer(f.tier), h - f.top.h - f.bottom.h),
                f.drawer);
            assertTrue(f.content.w > 0 && f.content.h > 0, w + "x" + h);
            // the drawer covers the content in S and M; in L it stays open and the content gives it 200 px
            assertEquals(f.tier != Tier.L, f.drawerOverlays());
            Rect beside = f.contentBesideDrawer();
            if (f.tier == Tier.L) assertEquals(f.content.w - 200, beside.w);
            else assertEquals(f.content, beside);
        }
    }

    /** 4-column grid in the content area: column = (W - 15) / 4, 86 at M (2.5). */
    @Test
    void fourColumnGrid() {
        assertEquals(86, Lay.columnWidth(359));
        assertEquals(5, Lay.GUTTER);
        Rect content = Lay.console(427, 240).content;
        Rect first = Lay.column(content, 0, 1), last = Lay.column(content, 3, 1), all = Lay.column(content, 0, 4);
        assertEquals(new Rect(64, 22, 86, 200), first);
        assertEquals(64 + 3 * (86 + 5), last.x);
        assertEquals(86, last.w);
        assertEquals(4 * 86 + 3 * 5, all.w, "spanning keeps the gutters");
        assertTrue(last.right() <= content.right());
    }

    @Test
    void fixedWindowsAreCentredAndKeptOnScreen() {
        assertEquals(new Rect(85, 4, 256, 232), Lay.objectWindow(427, 240), "232 high fits 240 (1.5.3)");
        assertEquals(new Rect(32, 4, 256, 232), Lay.objectWindow(320, 240));
        assertEquals(new Rect(0, 0, 256, 232), Lay.objectWindow(200, 200), "clamped, never off the left/top");
        assertEquals(new Rect(125, 10, 176, 220), Lay.machineWindow(427, 240));
        // 85 px of NEI room each side at M (1.5.3)
        assertEquals(85, (427 - 256) / 2);
    }

    @Test
    void snapToPhysicalPixels() {
        assertEquals(1.5f, Lay.snap(1.4f, 2));
        assertEquals(1.0f, Lay.snap(1.2f, 2));
        assertEquals(1.25f, Lay.snap(1.3f, 4));
        assertEquals(1f, Lay.snap(1.4f, 1));
        assertEquals(0.5f, Tok.hairline(2), "a hairline is one physical pixel at factor 2");
    }

    @Test
    void rects() {
        Rect a = new Rect(0, 0, 10, 10), b = new Rect(5, 5, 10, 10);
        assertEquals(new Rect(5, 5, 5, 5), a.intersect(b));
        assertEquals(0, a.intersect(new Rect(20, 20, 2, 2)).w);
        assertTrue(a.contains(9, 9));
        assertFalse(a.contains(10, 10));
        assertEquals(10, a.right());
        assertEquals(10, a.bottom());
        assertEquals(new Rect(2, 2, 6, 6), a.inset(2));
    }
}
