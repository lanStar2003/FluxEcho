package com.fluxecho.logic;

/**
 * 回响潮汐 The echo tide's curve (BLUEPRINT 3.11), from the world's clock alone: one rise and fall a game day, full at
 * midnight and lowest at noon, its swing set by the moon (an eight-day round: the spring tide at full moon,
 * {@link #SPRING} each way, the neap tide at new moon, {@link #NEAP}, the phases between in proportion), so that a
 * whole day averages to exactly the mean.
 * <p>
 * The tides have no effect yet (0.19.0 brings them); the Tidal Well already draws its rings rising and falling and its
 * pool and grate glowing with this curve (0.12.0, CITY.md §4.9), and it eats the tide twice over ({@link #WELL}).
 * Pure arithmetic over the world's day time ({@code World.getWorldTime()}, which {@code /time set} moves and the
 * daylight cycle stops), so every frame of every client draws the same tide.
 */
public final class Tide {

    /** A game day in ticks; the day time of the full tide (midnight). */
    public static final int DAY = 24000, FULL = 18000;
    /** The moon's round in days. */
    public static final int MOON = 8;
    /** The swing at the spring tide (full moon) and at the neap tide (new moon): +-20% and +-5%. */
    public static final double SPRING = 0.20, NEAP = 0.05;
    /** How much more the Tidal Well swings: it eats the tide (+-40% at the spring tide). */
    public static final double WELL = 2;

    private Tide() {}

    /**
     * The moon's phase on a world time, as Minecraft works it out ({@code WorldProvider.getMoonPhase}): 0 the full
     * moon, 4 the new moon.
     */
    public static int moonPhase(long worldTime) {
        return (int) (worldTime / DAY % MOON + MOON) % MOON;
    }

    /**
     * The tide's swing on a day of the moon's phase: {@link #SPRING} at full moon down to {@link #NEAP} at new moon.
     */
    public static double amplitude(int moonPhase) {
        int p = Math.floorMod(moonPhase, MOON), fromFull = Math.min(p, MOON - p);
        return SPRING + (NEAP - SPRING) * fromFull / (MOON / 2.0);
    }

    /**
     * How far the tide stands from the mean at a world time (fractional ticks): from {@code -amplitude} at noon to
     * {@code +amplitude} at midnight. A day changes its moon at sunrise (day time 0), where the curve crosses the mean,
     * so it never jumps.
     */
    public static double swing(double worldTime) {
        long day = (long) Math.floor(worldTime);
        double inDay = worldTime - Math.floorDiv(day, (long) DAY) * (double) DAY;
        return amplitude(moonPhase(day)) * Math.cos(2 * Math.PI * (inDay - FULL) / DAY);
    }

    /** The tide as a share of the mean: 1 + {@link #swing} (0.8 to 1.2). */
    public static double level(double worldTime) {
        return 1 + swing(worldTime);
    }

    /** The swing scaled to the spring tide's: -1 (the lowest a tide falls) to 1 (the highest it rises). */
    public static double norm(double worldTime) {
        return swing(worldTime) / SPRING;
    }
}
