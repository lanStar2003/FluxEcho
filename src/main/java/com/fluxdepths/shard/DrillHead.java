package com.fluxdepths.shard;

import java.util.Locale;

/**
 * GT drill heads ({@code toolHeadDrill}) used as the tip that opens the pinhole into the depths. A head stays in the
 * input slot and wears out by chance: each ore has a 1 in {@link #ores} chance to use it up, so it lasts that many
 * ores on average. The shard collector takes any GT drill head: these six have fixed values, any other material lasts
 * {@link #derivedUses half its tool durability}; the fluid pumps still ask for a minimum one.
 */
public enum DrillHead {

    BRONZE("Bronze", 128),
    STEEL("Steel", 256),
    ALUMINIUM("Aluminium", 384),
    STAINLESS_STEEL("StainlessSteel", 512),
    TITANIUM("Titanium", 768),
    TUNGSTEN_STEEL("TungstenSteel", 1024);

    /** GT material name ({@code Materials.get}). */
    public final String material;
    /** Ores one head lasts for on average. */
    public final int ores;

    DrillHead(String material, int ores) {
        this.material = material;
        this.ores = ores;
    }

    /** Fewest and most ores a head of a material not listed here lasts. */
    public static final int MIN_USES = 32, MAX_USES = 16384;

    /** Ores a drill head of a material not listed here lasts on average: half its GT tool durability, clamped. */
    public static int derivedUses(long durability) {
        return (int) Math.max(MIN_USES, Math.min(MAX_USES, durability / 2));
    }

    /** Whether one use wears out a head that lasts {@code uses} uses on average; {@code roll} is uniform in [0, 1). */
    public static boolean wears(int uses, double roll) {
        return uses <= 1 || roll * uses < 1;
    }

    /** The chance of {@link #wears} in percent, for tooltips: "0.39". */
    public static String percent(int uses) {
        return String.format(Locale.ROOT, "%.2g", 100.0 / Math.max(1, uses));
    }
}
