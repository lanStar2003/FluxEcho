package com.fluxecho.config;

import net.minecraftforge.common.config.Configuration;

import com.fluxecho.logic.BloodRates;

/**
 * The bloodmagic section of config/fluxecho.cfg: the Blood Echo, LP from EU and meat. Since 0.17.0 each section of the
 * config file is its own class, listed in Config.SECTIONS, so that work on different subsystems (and later on each
 * building) never edits the same file; the keys, defaults, bounds and comments are the ones 0.16.0 shipped, word for
 * word.
 */
public final class BloodConfig {

    public static final String CATEGORY = "bloodmagic";

    public static boolean bloodEnabled = true;
    public static int[] lpPerTick = { 4, 8, 16, 32, 48, 64 };
    public static int euPerLp = 2, lpPerMeat = 2000, altarRange = 5, altarHeight = 10;

    private BloodConfig() {}

    public static void load(Configuration c) {
        c.setCategoryComment(
            CATEGORY,
            "Blood Magic: LP from EU and a little meat, as fast as the bound blood orb you made in the altar allows.");
        bloodEnabled = c.getBoolean(
            "enabled",
            CATEGORY,
            bloodEnabled,
            "Switch the module off: no recipes, and placed machines stop working (they stay in the world).");
        String[] rates = c.getStringList(
            "lpPerTick",
            CATEGORY,
            new String[] { "4", "8", "16", "32", "48", "64" },
            "LP per tick for blood orbs of level 1 (weak) to 6 (transcendent); higher levels use the last entry. The Blood Echo is an MV machine: keep rate x euPerLp at 128 or below.");
        lpPerTick = BloodRates.parse(rates);
        euPerLp = c.getInt("euPerLp", CATEGORY, euPerLp, 1, 1000, "EU per LP.");
        lpPerMeat = c.getInt(
            "lpPerMeat",
            CATEGORY,
            lpPerMeat,
            1,
            1_000_000,
            "LP one piece of raw meat or rotten flesh is worth.");
        altarRange = c.getInt(
            "altarRange",
            CATEGORY,
            altarRange,
            1,
            16,
            "How far sideways the Blood Echo looks for an altar (like the Well of Suffering).");
        altarHeight = c.getInt(
            "altarHeight",
            CATEGORY,
            altarHeight,
            1,
            32,
            "How far up and down the Blood Echo looks for an altar.");
    }
}
