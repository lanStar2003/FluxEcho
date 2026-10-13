package com.fluxecho.logic;

/**
 * The band of heights a nexus campus may be laid out at (0.14.1, CITY.md §1.3 Vertical): its base layer Y0 from
 * {@link #LOWEST} to {@link #HIGHEST}. Inside it every building of the city stands, in its full form or in the form it
 * adapts to: the zenith's pod lower on shorter legs over high ground ({@link DomeGeneratorShape#datum}), the Tidal Well
 * shallower over low ground ({@link RootShape#depth}), and the cells of a spire over the world's top left out.
 * <ul>
 * <li>The top is the nexus's own headroom, as before 0.14.1: it grows to phase V, Y0 + 25, and its growth clears to
 * Y0 + 27, so a base over 225 is refused ({@code build.too_high}).</li>
 * <li>The bottom is new: under Y0 14 not even the shallowest well (8 deep) keeps its floor slab over the bedrock
 * ({@code build.too_low}).</li>
 * </ul>
 * A core put back where it stood ({@link #putBack}) is never refused: its campus was laid out there before the band
 * (a 0.10 to 0.14 campus may lie under it), and refusing it would leave that campus without its core. The item
 * ({@code ItemBlockNexusCore}), its placement preview ({@code Masterplan}) and 铺设广场 on a legacy nexus
 * ({@code Campus.layForum}) all ask this one rule.
 */
public final class CampusBand {

    /** Blocks over the base the nexus needs under the top of the world: its phase V and the growth's clear box. */
    public static final int HEADROOM = 30;
    /** The highest base layer, 255 - {@link #HEADROOM}: 225. */
    public static final int HIGHEST = 255 - HEADROOM;
    /** The lowest base layer: the shallowest Tidal Well's, whose floor slab stays at y 6 ({@link RootShape#MIN_Y0}). */
    public static final int LOWEST = RootShape.MIN_Y0;
    /** The reasons, lang keys under {@code fluxecho.}. */
    public static final String TOO_HIGH = "build.too_high", TOO_LOW = "build.too_low";

    private CampusBand() {}

    /**
     * Why a campus cannot be laid out with its base layer at {@code y0} ({@link #TOO_HIGH} over {@link #HIGHEST},
     * {@link #TOO_LOW} under {@link #LOWEST}), or null when it can; always null for a core put back where it stood.
     */
    public static String refusal(int y0, boolean putBack) {
        if (putBack) return null;
        if (y0 > HIGHEST) return TOO_HIGH;
        if (y0 < LOWEST) return TOO_LOW;
        return null;
    }

    /**
     * Whether a core is being put back where it stood: the campus it carries was broken off the core at
     * {@code carriedPos} {x, y, z} in the dimension {@code carriedDim}, and it is placed at (x, y, z) in {@code dim}.
     * False when it carries no campus ({@code carriedPos} null or not three numbers).
     */
    public static boolean putBack(int carriedDim, int[] carriedPos, int dim, int x, int y, int z) {
        return carriedPos != null && carriedPos.length == 3
            && carriedDim == dim
            && carriedPos[0] == x
            && carriedPos[1] == y
            && carriedPos[2] == z;
    }
}
