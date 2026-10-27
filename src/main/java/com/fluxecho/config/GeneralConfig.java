package com.fluxecho.config;

import net.minecraftforge.common.config.Configuration;

/**
 * The general section of config/fluxecho.cfg: what every module shares (the GT machine ids, the default recipes). Since
 * 0.17.0 each section of the config file is its own class, listed in Config.SECTIONS, so that work on different
 * subsystems (and later on each building) never edits the same file; the keys, defaults, bounds and comments are the
 * ones 0.16.0 shipped, word for word.
 */
public final class GeneralConfig {

    public static final String CATEGORY = "general";

    public static int firstMachineId = 24530;
    public static boolean defaultRecipes = true;

    private GeneralConfig() {}

    public static void load(Configuration c) {
        c.setCategoryComment(CATEGORY, "Settings shared by every module.");
        firstMachineId = c.getInt(
            "firstMachineId",
            CATEGORY,
            firstMachineId,
            1,
            32000,
            "First of the 40 GT machine ids FluxEcho keeps (24530-24569 by default). Change it only for a new world or when another mod takes these ids.");
        defaultRecipes = c.getBoolean(
            "enableDefaultRecipes",
            CATEGORY,
            defaultRecipes,
            "Register the crafting recipes of the machines; switch off to write your own with CraftTweaker.");
    }
}
