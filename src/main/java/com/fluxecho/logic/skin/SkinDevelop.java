package com.fluxecho.logic.skin;

/**
 * Developing (style §3.9.6): while a building is built for the first time its new materials show their blueprint
 * colours (the accent at half its saturation; the hull is white from the start), and when it first forms the existing
 * two-second forming sweep runs up from its bottom, every layer it passes developing into its colours. A client that
 * comes later sees it developed ({@link SkinEntry#formedAt} is the world tick it formed), and a building that stood
 * before the table knew it never sweeps. The old buildings ({@link SkinProfiles#LEGACY}) never develop.
 */
public final class SkinDevelop {

    /** The sweep's length in ticks: {@code TileMultiblock.SWEEP_MS}, two seconds. */
    public static final int SWEEP_TICKS = 40;

    private SkinDevelop() {}

    /**
     * The height the building has developed up to at a world tick: every layer below it shows its colours.
     * {@link Double#NEGATIVE_INFINITY} while it is built (all blueprint), {@link Double#POSITIVE_INFINITY} once
     * developed or for a look that never develops.
     */
    public static double developedTo(SkinEntry e, Profile p, long now) {
        if (!p.develops) return Double.POSITIVE_INFINITY;
        if (e.state == SkinState.BUILDING) return Double.NEGATIVE_INFINITY;
        if (e.formedAt <= 0) return Double.POSITIVE_INFINITY;
        long t = now - e.formedAt;
        if (t >= SWEEP_TICKS) return Double.POSITIVE_INFINITY;
        double f = Math.max(0, t) / (double) SWEEP_TICKS;
        return e.box[1] + f * (e.box[4] - e.box[1] + 1);
    }

    /** Whether the layer at height {@code y} shows its colours: the sweep has passed its top. */
    public static boolean developed(SkinEntry e, Profile p, int y, long now) {
        return y + 1 <= developedTo(e, p, now);
    }

    /** A colour as a blueprint shows it: its HSV saturation halved, its hue and value kept. */
    public static int blueprintColour(int rgb) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        int max = Math.max(r, Math.max(g, b));
        return half(r, max) << 16 | half(g, max) << 8 | half(b, max);
    }

    /** A channel moved half way to the brightest one: the saturation halves, the hue stays. */
    private static int half(int c, int max) {
        return (int) Math.round(max - (max - c) * 0.5);
    }
}
