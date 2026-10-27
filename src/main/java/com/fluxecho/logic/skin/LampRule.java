package com.fluxecho.logic.skin;

/**
 * The lamp windows' one condition (style G6, §3.9.5): a window is lit when its building is awake (the box table says
 * {@link SkinState#awake}), has power, and it is night by the client's own clock, world time mod 24,000 in [12,500,
 * 23,500). Dusk and dawn reach a building late by {@code d / 32} seconds, {@code d} its distance from the nexus across
 * the ground: the city lights as a wave from the middle out at 32 blocks a second, the speed of the keel's heartbeat
 * (§6.5). The blocks never change for it: the client draws or leaves out the glowing overlay.
 * <p>
 * For the old buildings ({@link SkinProfiles#LEGACY}, refactor §5.6) the lamps are their pilasters, cornices and
 * fittings ({@link SkinProfiles#isLamp}); what a renderer does with a part is {@link #glow}.
 */
public final class LampRule {

    /** A day of world time, and the night's window in it. */
    public static final int DAY = 24_000, DUSK = 12_500, DAWN = 23_500;
    /** How fast dusk spreads from the nexus, in blocks a second. */
    public static final int WAVE = 32;
    /** {@link #glow}: draw the part's glow overlay as 0.16 did (no lamp, or no entry). */
    public static final int UNTOUCHED = -2;
    /** {@link #glow}: leave the part's glow overlay out (a lamp window by day, asleep or without power). */
    public static final int DARK = -1;

    private LampRule() {}

    /** Whether it is night at the world time (the world's day time, any number of days on, mod 24,000). */
    public static boolean night(long worldTime) {
        long t = Math.floorMod(worldTime, (long) DAY);
        return t >= DUSK && t < DAWN;
    }

    /** How many ticks late dusk and dawn reach a building {@code d} blocks from its nexus: {@code 20 d / 32}. */
    public static int delayTicks(double d) {
        return d <= 0 ? 0 : (int) Math.floor(d * 20 / WAVE);
    }

    /** Whether a building's lamp windows are lit. */
    public static boolean lit(SkinState state, boolean powered, long worldTime, double d) {
        return state.awake() && powered && night(worldTime - delayTicks(d));
    }

    /** Whether an entry's lamp windows are lit, the wave reaching it by its distance from its nexus. */
    public static boolean lit(SkinEntry e, long worldTime) {
        return lit(e.state, e.powered, worldTime, e.distance());
    }

    /**
     * Whether a building is awake, version 0 (refactor §4.2: formed, docked and powered; 0.19's
     * {@code TileCityModule.isAwake} adds the holo table's dispatch). The power bit the server debounces is this.
     */
    public static boolean isAwake(boolean formed, boolean docked, boolean powered) {
        return formed && docked && powered;
    }

    /**
     * What a renderer draws of a part's glow overlay in the entry's building at the world time: {@link #UNTOUCHED} for
     * a part that is no lamp of the building, {@link #DARK} for an unlit lamp, else the vertex colour of the lit
     * overlay, the building's accent for the parts the accent tints ({@link SkinProfiles#tinted}), white for the parts
     * a building took into its mask besides.
     */
    public static int glow(SkinEntry e, int part, long worldTime) {
        return glow(e, part, lit(e, worldTime));
    }

    /** {@link #glow} with the lamps' state already worked out. */
    public static int glow(SkinEntry e, int part, boolean lit) {
        if (e == null || !SkinProfiles.isLamp(e.key(), part)) return UNTOUCHED;
        if (!lit) return DARK;
        return SkinProfiles.tinted(part) ? e.accent : 0xFFFFFF;
    }
}
