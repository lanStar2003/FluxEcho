package com.fluxecho.config;

import net.minecraftforge.common.config.Configuration;

/**
 * The style section of config/fluxecho.cfg (0.17.0): the player's own say over the city's colours. The pearl-white
 * hull is drawn with its brightest pixel at most 0.94 (style FINAL 8.4 #7); {@code hullBrightness} lets a player whose
 * shader pack still makes it glare at noon turn it down, between 0.85 and 1.0. The palette itself has no switch
 * (DECISIONS B10).
 */
public final class StyleConfig {

    public static final String CATEGORY = "style";
    public static final double HULL_MIN = 0.85, HULL_MAX = 1.0;

    /** Client: the factor on the hull textures' brightness, {@link #HULL_MIN} to {@link #HULL_MAX}. */
    public static double hullBrightness = 1.0;

    private StyleConfig() {}

    public static void load(Configuration c) {
        c.setCategoryComment(CATEGORY, "The look of the city.");
        hullBrightness = clamp(
            c.get(
                CATEGORY,
                "hullBrightness",
                hullBrightness,
                "Client: how bright the pearl-white hull of the city's buildings is drawn (1.0 as designed); turn it down if it glares at noon with your shader pack. [range: "
                    + HULL_MIN
                    + " ~ "
                    + HULL_MAX
                    + ", default: 1.0]",
                HULL_MIN,
                HULL_MAX)
                .getDouble());
    }

    /** The brightness kept within its bounds (a hand-edited file may hold anything). */
    static double clamp(double v) {
        return Double.isNaN(v) ? HULL_MAX : Math.max(HULL_MIN, Math.min(HULL_MAX, v));
    }
}
