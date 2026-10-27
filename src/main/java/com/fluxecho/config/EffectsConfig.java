package com.fluxecho.config;

import net.minecraftforge.common.config.Configuration;

/**
 * The effects section of config/fluxecho.cfg: how the echo machines show their work in the world. Since 0.17.0 each
 * section of the config file is its own class, listed in Config.SECTIONS, so that work on different subsystems (and
 * later on each building) never edits the same file; the keys, defaults, bounds and comments are the ones 0.16.0
 * shipped, word for word.
 */
public final class EffectsConfig {

    public static final String CATEGORY = "effects";

    public static boolean machineEffects = true, flowTrails = true;
    public static int effectRange = 32, machineHologramRange = 16;

    private EffectsConfig() {}

    public static void load(Configuration c) {
        c.setCategoryComment(
            CATEGORY,
            "How the echo machines show their work in the world: an effect above each while it works, trails to what the Mana Echo Spring, the Blood Echo and a pedestal's wireless module fill, and their holograms.");
        machineEffects = c.getBoolean(
            "machineEffects",
            CATEGORY,
            machineEffects,
            "Client: an effect above an echo machine while it works (rings, motes, sparks in its colour).");
        flowTrails = c.getBoolean(
            "flowTrails",
            CATEGORY,
            flowTrails,
            "Client: a trail from the Mana Echo Spring to each mana pool it fills, from the Blood Echo to its altar, and from a Flux Vis Pedestal to each player its wireless module charges.");
        effectRange = c.getInt("effectRange", CATEGORY, effectRange, 0, 128, "Client: blocks within which they show.");
        machineHologramRange = c.getInt(
            "hologramRange",
            CATEGORY,
            machineHologramRange,
            0,
            64,
            "Blocks within which an echo machine's hologram shows (each one's is off until switched on in its GUI or with a screwdriver on its side). 0 turns them all off.");
    }
}
