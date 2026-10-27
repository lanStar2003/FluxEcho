package com.fluxecho.config;

import net.minecraftforge.common.config.Configuration;

/**
 * The nexus section of config/fluxecho.cfg, nexus part: the flux materials, the nexus itself, its research, its effects
 * and the upkeep of what docks on it. Since 0.17.0 each section of the config file is its own class, listed in
 * Config.SECTIONS, so that work on different subsystems (and later on each building) never edits the same file; the
 * keys, defaults, bounds and comments are the ones 0.16.0 shipped, word for word.
 */
public final class NexusConfig {

    public static final String CATEGORY = "nexus";

    public static boolean nexusEnabled = true, nexusEffects = true;
    public static double gritChance = 0.15, researchScale = 1.0;
    public static int crystalGrit = 3, nexusUpkeep = 512, nexusCompute = 20, innerRadius = 32, recordCooldown = 24000,
        manifestEut = 480, libraryUpkeep = 128, nexusEffectRange = 128;
    /** EU/t a city module (0.11.0: the Eco Dome) draws while it is docked on a nexus. */
    public static int moduleUpkeep = 128;

    private NexusConfig() {}

    public static void load(Configuration c) {
        c.setCategoryComment(
            CATEGORY,
            "The Flux Nexus (from HV): the flux materials, the nexus itself, its research and the modules docked on its rings. Energy comes from the owner's team wireless network.");
        nexusEnabled = c.getBoolean(
            "enabled",
            CATEGORY,
            nexusEnabled,
            "Switch the nexus and its modules off: no recipes, and placed ones stop working (they stay in the world). The flux materials stay.");
        gritChance = c.get(
            CATEGORY,
            "gritChance",
            gritChance,
            "Chance that a Flux Shard Collector gives off one flux grit with each ore it condenses (0-1). The only source of flux grit.",
            0,
            1)
            .getDouble();
        crystalGrit = c
            .getInt("crystalGrit", CATEGORY, crystalGrit, 1, 64, "Flux grit per flux crystal in GT's autoclave.");
        nexusUpkeep = c.getInt(
            "upkeepEuPerTick",
            CATEGORY,
            nexusUpkeep,
            0,
            Integer.MAX_VALUE,
            "EU/t a formed nexus draws from its owner's team wireless network to stay anchored (phase I).");
        nexusCompute = c.getInt(
            "computePerSecond",
            CATEGORY,
            nexusCompute,
            0,
            1_000_000,
            "Flux compute a formed, powered nexus produces each second (phase I); research runs on it.");
        researchScale = c
            .get(
                CATEGORY,
                "researchCostScale",
                researchScale,
                "Multiplies the compute every research takes (0.1 = ten times faster).",
                0.01,
                100)
            .getDouble();
        manifestEut = c.getInt(
            "manifestEuPerTick",
            CATEGORY,
            manifestEut,
            1,
            Integer.MAX_VALUE,
            "EU/t the nexus's manifestation table draws on top of the upkeep while it makes something.");
        recordCooldown = c.getInt(
            "recordCooldownTicks",
            CATEGORY,
            recordCooldown,
            0,
            Integer.MAX_VALUE,
            "Ticks before the same Echo Codex entry can be pressed into an echo crystal again (24000 = one day).");
        innerRadius = c.getInt(
            "innerRingRadius",
            CATEGORY,
            innerRadius,
            12,
            128,
            "Blocks from the nexus's centre to the centre of each inner ring slot. Changing it moves the slots of nexuses already built.");
        libraryUpkeep = c.getInt(
            "libraryUpkeepEuPerTick",
            CATEGORY,
            libraryUpkeep,
            0,
            Integer.MAX_VALUE,
            "EU/t an Echo Library draws while it is docked on a nexus.");
        moduleUpkeep = c.getInt(
            "moduleUpkeepEuPerTick",
            CATEGORY,
            moduleUpkeep,
            0,
            Integer.MAX_VALUE,
            "EU/t a city module the nexus built (the Eco Dome and the modules after it) draws while it is docked on a nexus. Without it the module stands unpowered; what its units hold stays.");
        nexusEffects = c.getBoolean(
            "effects",
            CATEGORY,
            nexusEffects,
            "Client: draw the formed nexus and its modules (floating rings, light column, bridges, the library's depths). Off: the bare blocks.");
        nexusEffectRange = c.getInt(
            "effectRange",
            CATEGORY,
            nexusEffectRange,
            16,
            512,
            "Client: blocks within which a formed nexus and its modules are drawn.");
    }
}
