package com.fluxdepths;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.fluxdepths.fluid.PumpRates;
import com.fluxdepths.fluid.PumpTier;

/**
 * {@code config/fluxdepths.cfg}. Every module can be switched off; its blocks stay registered so placed machines do
 * not vanish from a world, only the recipes and the work stop.
 */
public final class Config {

    private static final String SHARDS = "shard_collectors", PUMPS = "fluid_pumps";

    public static boolean shardsEnabled = true;
    public static int shardsFirstId = 24520;
    public static boolean crossDimension = false;
    public static boolean shardRecipes = true;
    public static int hologramRange = 16;

    public static boolean pumpsEnabled = true;
    public static int pumpsFirstId = 24527;
    public static double[] pumpShares = defaultShares();
    public static boolean pumpRecipes = true;

    private Config() {}

    private static double[] defaultShares() {
        PumpTier[] t = PumpTier.values();
        double[] d = new double[t.length];
        for (int i = 0; i < t.length; i++) d[i] = t[i].defaultShare;
        return d;
    }

    public static void load(File file) {
        Configuration c = new Configuration(file);
        c.setCategoryComment(
            SHARDS,
            "The Flux Shard Collector: one machine that echoes a sampled ore vein, on steam or, with an LV to LuV circuit in its core, at that voltage.");
        shardsEnabled = c.getBoolean(
            "enabled",
            SHARDS,
            shardsEnabled,
            "Switch the module off: no recipes, no NEI pages, and placed collectors stop working (they stay in the world).");
        shardsFirstId = c.getInt(
            "firstMachineId",
            SHARDS,
            shardsFirstId,
            1,
            32000,
            "First of the 7 GT machine ids the collector uses (the collector, then the six ids the old tiered collectors had). Change it only for a new world or when another mod takes these ids.");
        crossDimension = c.getBoolean(
            "crossDimension",
            SHARDS,
            crossDimension,
            "Let imprints (vein and fluid) work in any world, not only the one they were taken in.");
        hologramRange = c.getInt(
            "hologramRange",
            SHARDS,
            hologramRange,
            0,
            64,
            "Blocks within which a collector's hologram shows (each collector's hologram is off until switched on in its GUI or with a screwdriver). 0 turns all holograms off.");
        shardRecipes = c.getBoolean(
            "enableDefaultRecipes",
            SHARDS,
            shardRecipes,
            "Register the crafting recipes; switch off to write your own with CraftTweaker.");

        c.setCategoryComment(
            PUMPS,
            "Fluid pumps: echo the underground fluid (oil, gas, ...) of a chunk you took a fluid imprint of, LV to HV.");
        pumpsEnabled = c.getBoolean(
            "enabled",
            PUMPS,
            pumpsEnabled,
            "Switch the module off: no recipes, no NEI pages, and placed pumps stop working (they stay in the world).");
        pumpsFirstId = c.getInt(
            "firstMachineId",
            PUMPS,
            pumpsFirstId,
            1,
            32000,
            "First of the 3 GT machine ids the pumps use (24527-24529 by default, right after the collectors). Change it only for a new world or when another mod takes these ids.");
        String[] defaults = new String[pumpShares.length];
        for (int i = 0; i < defaults.length; i++) defaults[i] = String.valueOf(defaultShares()[i]);
        pumpShares = PumpRates.parse(
            c.getStringList(
                "sharePerSecond",
                PUMPS,
                defaults,
                "Litres per second for the LV, MV and HV pump, as a share of the imprinted chunk's pristine amount (what GT's drilling rigs get per operation)."),
            defaultShares());
        pumpRecipes = c.getBoolean(
            "enableDefaultRecipes",
            PUMPS,
            pumpRecipes,
            "Register the crafting recipes; switch off to write your own with CraftTweaker.");
        if (c.hasChanged()) c.save();
    }
}
