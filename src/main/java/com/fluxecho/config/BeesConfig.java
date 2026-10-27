package com.fluxecho.config;

import net.minecraftforge.common.config.Configuration;

/**
 * The bees section of config/fluxecho.cfg: the bee machines (Forestry), their times, EU and batch sizes. Since 0.17.0
 * each section of the config file is its own class, listed in Config.SECTIONS, so that work on different subsystems
 * (and later on each building) never edits the same file; the keys, defaults, bounds and comments are the ones 0.16.0
 * shipped, word for word.
 */
public final class BeesConfig {

    public static final String CATEGORY = "bees";

    public static boolean beesEnabled = true;
    public static int imprintTicks = 100, imprintEut = 16;
    public static int incubateTicks = 200, incubateEut = 24;
    public static int assembleTicks = 100, assembleEut = 16;
    public static int honeyPerPrincess = 12, dronesWithPrincess = 2;
    public static int honeyPerDrones = 16, dronesPerBatch = 16;
    public static int fertilizerPerSaplings = 4, saplingsPerBatch = 4;
    public static int honeyPerButterfly = 8, butterfliesPerBatch = 1;

    private BeesConfig() {}

    public static void load(Configuration c) {
        c.setCategoryComment(
            CATEGORY,
            "Bees (Forestry): imprint a real bee once, then incubate pristine princesses and drones of it; take gene samples of single traits and put them together into the bee you want.");
        beesEnabled = c.getBoolean(
            "enabled",
            CATEGORY,
            beesEnabled,
            "Switch the module off: no recipes, no NEI pages, and placed machines stop working (they stay in the world).");
        imprintTicks = c
            .getInt("imprintTicks", CATEGORY, imprintTicks, 1, 72000, "Ticks the Bee Imprinter takes per imprint.");
        imprintEut = c.getInt("imprintEuPerTick", CATEGORY, imprintEut, 1, 32, "EU/t of the Bee Imprinter (LV).");
        incubateTicks = c
            .getInt("incubateTicks", CATEGORY, incubateTicks, 1, 72000, "Ticks the Larva Incubator takes per batch.");
        incubateEut = c.getInt("incubateEuPerTick", CATEGORY, incubateEut, 1, 32, "EU/t of the Larva Incubator (LV).");
        assembleTicks = c
            .getInt("assembleTicks", CATEGORY, assembleTicks, 1, 72000, "Ticks the Gene Assembler takes per job.");
        assembleEut = c.getInt("assembleEuPerTick", CATEGORY, assembleEut, 1, 32, "EU/t of the Gene Assembler (LV).");
        honeyPerPrincess = c.getInt(
            "honeyPerPrincess",
            CATEGORY,
            honeyPerPrincess,
            0,
            64,
            "Honey drops per princess batch (circuit 1: a princess and her drones).");
        dronesWithPrincess = c
            .getInt("dronesWithPrincess", CATEGORY, dronesWithPrincess, 0, 64, "Drones that come with a princess.");
        honeyPerDrones = c
            .getInt("honeyPerDrones", CATEGORY, honeyPerDrones, 0, 64, "Honey drops per drone batch (circuit 2).");
        dronesPerBatch = c
            .getInt("dronesPerBatch", CATEGORY, dronesPerBatch, 1, 64, "Drones in a drone batch (circuit 2).");
        fertilizerPerSaplings = c.getInt(
            "fertilizerPerSaplings",
            CATEGORY,
            fertilizerPerSaplings,
            0,
            64,
            "Forestry fertilizer per batch of saplings from a tree imprint.");
        saplingsPerBatch = c
            .getInt("saplingsPerBatch", CATEGORY, saplingsPerBatch, 1, 64, "Saplings in a batch from a tree imprint.");
        honeyPerButterfly = c.getInt(
            "honeyPerButterfly",
            CATEGORY,
            honeyPerButterfly,
            0,
            64,
            "Honey drops per batch of butterflies from a butterfly imprint.");
        butterfliesPerBatch = c.getInt(
            "butterfliesPerBatch",
            CATEGORY,
            butterfliesPerBatch,
            1,
            64,
            "Butterflies in a batch from a butterfly imprint.");
    }
}
