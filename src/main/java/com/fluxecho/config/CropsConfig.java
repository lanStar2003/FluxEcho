package com.fluxecho.config;

import net.minecraftforge.common.config.Configuration;

/**
 * The crops section of config/fluxecho.cfg: the seed machines (IC2 and Crops++). Since 0.17.0 each section of the
 * config file is its own class, listed in Config.SECTIONS, so that work on different subsystems (and later on each
 * building) never edits the same file; the keys, defaults, bounds and comments are the ones 0.16.0 shipped, word for
 * word.
 */
public final class CropsConfig {

    public static final String CATEGORY = "crops";

    public static boolean cropsEnabled = true;
    public static int cropImprintTicks = 100, cropImprintEut = 16;
    public static int seedTicks = 200, seedEut = 24, sticksPerSeed = 1;

    private CropsConfig() {}

    public static void load(Configuration c) {
        c.setCategoryComment(
            CATEGORY,
            "Crops (IC2 and Crops++): imprint a fully scanned seed bag once, then get seed bags of it with its stats.");
        cropsEnabled = c.getBoolean(
            "enabled",
            CATEGORY,
            cropsEnabled,
            "Switch the module off: no recipes, and placed machines stop working (they stay in the world).");
        cropImprintTicks = c.getInt(
            "imprintTicks",
            CATEGORY,
            cropImprintTicks,
            1,
            72000,
            "Ticks the Seed Imprinter takes per imprint.");
        cropImprintEut = c
            .getInt("imprintEuPerTick", CATEGORY, cropImprintEut, 1, 32, "EU/t of the Seed Imprinter (LV).");
        seedTicks = c.getInt("seedTicks", CATEGORY, seedTicks, 1, 72000, "Ticks the Seed Echo takes per seed bag.");
        seedEut = c.getInt("seedEuPerTick", CATEGORY, seedEut, 1, 32, "EU/t of the Seed Echo (LV).");
        sticksPerSeed = c.getInt("sticksPerSeed", CATEGORY, sticksPerSeed, 0, 64, "Crop sticks per seed bag.");
    }
}
