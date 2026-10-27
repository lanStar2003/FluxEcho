package com.fluxecho.logic.ui;

/**
 * Layout of the new UI (ui/FINAL.md section 1.5.2, 1.5.3, 2.5): the one breakpoint function, the console frame's
 * pieces at each tier, the content grid, and the fixed-size windows. Every page asks this for its regions instead of
 * carrying its own numbers.
 * <p>
 * In zh_CN the GUI scale factor is 1, 2, 4 or 6 and the GUI is never smaller than 320 x 240. Three tiers: S below 400
 * wide (one column, the rail folds to icons, drawers cover the content), L from 600 x 300 (three columns or wide
 * charts, the drawer stays open), M otherwise; M = 427 x 240 is the design canvas, the user's 854 x 480 launcher
 * window at factor 2. Integer GUI pixels; pure functions.
 */
public final class Lay {

    private Lay() {}

    public enum Tier {
        S,
        M,
        L
    }

    /** The design canvas. */
    public static final int M_W = 427, M_H = 240;
    /** Gutter of the content grid. */
    public static final int GUTTER = 5;
    /** Inset of the content area from the bars and the rail. */
    public static final int INSET = 4;
    /** What the content gives up for the drawer when it stays open (L). */
    public static final int DRAWER_ROOM = 200;
    /** The object window (frame A, MUI): 256 x 232 at every tier; the GT machine window (frame C) 176 x 220. */
    public static final int OBJECT_W = 256, OBJECT_H = 232, MACHINE_W = 176, MACHINE_H = 220;

    /** S below 400 wide; L from 600 wide and 300 high; M otherwise. */
    public static Tier breakpoint(int w, int h) {
        if (w < 400) return Tier.S;
        if (w >= 600 && h >= 300) return Tier.L;
        return Tier.M;
    }

    public static int topBar(Tier t) {
        return t == Tier.L ? 22 : 18;
    }

    public static int rail(Tier t) {
        return t == Tier.S ? 24 : t == Tier.M ? 60 : 84;
    }

    public static int bottomBar(Tier t) {
        return t == Tier.L ? 16 : 14;
    }

    public static int drawer(Tier t) {
        return t == Tier.L ? 196 : 152;
    }

    /** An integer rectangle in GUI pixels. */
    public static final class Rect {

        public final int x, y, w, h;

        public Rect(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public boolean contains(float px, float py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }

        /** The overlap, or an empty rectangle at this one's corner. */
        public Rect intersect(Rect o) {
            int x0 = Math.max(x, o.x), y0 = Math.max(y, o.y), x1 = Math.min(right(), o.right()),
                y1 = Math.min(bottom(), o.bottom());
            if (x1 <= x0 || y1 <= y0) return new Rect(x, y, 0, 0);
            return new Rect(x0, y0, x1 - x0, y1 - y0);
        }

        public Rect inset(int d) {
            return new Rect(x + d, y + d, Math.max(0, w - 2 * d), Math.max(0, h - 2 * d));
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Rect r && r.x == x && r.y == y && r.w == w && r.h == h;
        }

        @Override
        public int hashCode() {
            return ((x * 31 + y) * 31 + w) * 31 + h;
        }

        @Override
        public String toString() {
            return "(" + x + "," + y + " " + w + "x" + h + ")";
        }
    }

    /** The console's pieces on one screen: top bar, rail, bottom bar, content area, drawer. */
    public static final class Frame {

        public final Tier tier;
        public final Rect top, rail, bottom, content, drawer;

        Frame(Tier tier, Rect top, Rect rail, Rect bottom, Rect content, Rect drawer) {
            this.tier = tier;
            this.top = top;
            this.rail = rail;
            this.bottom = bottom;
            this.content = content;
            this.drawer = drawer;
        }

        /** In S and M an open drawer lies over the content; in L it stays open beside it. */
        public boolean drawerOverlays() {
            return tier != Tier.L;
        }

        /** The content area left when the drawer is open: all of it in S and M, 200 px less in L. */
        public Rect contentBesideDrawer() {
            if (drawerOverlays()) return content;
            return new Rect(content.x, content.y, Math.max(0, content.w - DRAWER_ROOM), content.h);
        }
    }

    /** The console frame (frame B) on a screen of {@code w x h}. */
    public static Frame console(int w, int h) {
        Tier t = breakpoint(w, h);
        int top = topBar(t), bottom = bottomBar(t), rail = rail(t), drawer = drawer(t);
        int middle = Math.max(0, h - top - bottom);
        Rect content = new Rect(
            rail + INSET,
            top + INSET,
            Math.max(0, w - rail - 2 * INSET),
            Math.max(0, h - top - bottom - 2 * INSET));
        return new Frame(
            t,
            new Rect(0, 0, w, top),
            new Rect(0, top, rail, middle),
            new Rect(0, h - bottom, w, bottom),
            content,
            new Rect(w - drawer, top, drawer, middle));
    }

    /** Column width of the 4-column content grid: (W - 15) / 4, 86 at M. */
    public static int columnWidth(int contentW) {
        return Math.max(0, (contentW - 3 * GUTTER) / 4);
    }

    /** Columns {@code first} to {@code first + span - 1} of the content grid. */
    public static Rect column(Rect content, int first, int span) {
        int cw = columnWidth(content.w);
        return new Rect(content.x + first * (cw + GUTTER), content.y, span * cw + (span - 1) * GUTTER, content.h);
    }

    /** The object window, centred and kept on the screen (never off the left or top edge). */
    public static Rect objectWindow(int screenW, int screenH) {
        return centred(OBJECT_W, OBJECT_H, screenW, screenH);
    }

    /** The GT machine window, centred and kept on the screen. */
    public static Rect machineWindow(int screenW, int screenH) {
        return centred(MACHINE_W, MACHINE_H, screenW, screenH);
    }

    private static Rect centred(int w, int h, int screenW, int screenH) {
        return new Rect(Math.max(0, (screenW - w) / 2), Math.max(0, (screenH - h) / 2), w, h);
    }

    /** A position on the physical pixel grid: round(v x f) / f. */
    public static float snap(float v, int factor) {
        int f = Math.max(1, factor);
        return Math.round(v * f) / (float) f;
    }
}
