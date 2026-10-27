package com.fluxecho.config;

import net.minecraftforge.common.config.Configuration;

/**
 * The botania section of config/fluxecho.cfg: the Mana Echo Spring. Since 0.17.0 each section of the config file is its
 * own class, listed in Config.SECTIONS, so that work on different subsystems (and later on each building) never edits
 * the same file; the keys, defaults, bounds and comments are the ones 0.16.0 shipped, word for word.
 */
public final class ManaConfig {

    public static final String CATEGORY = "botania";

    public static boolean manaEnabled = true;
    public static int manaPerTick = 16, euPerMana = 2, manaPerPetal = 1250, manaRange = 4, manaHeight = 2,
        manaHologramRange = 16;

    private ManaConfig() {}

    public static void load(Configuration c) {
        c.setCategoryComment(
            CATEGORY,
            "Botania: the Mana Echo Spring draws the echo of mana up from the flux layer with EU and a few petals, into the mana pools around it. The circuit in its core (LV to LuV) sets its tier: each tier draws 4x the mana of the one below, for 4x the EU, and a petal is worth 4x as much.");
        manaEnabled = c.getBoolean(
            "enabled",
            CATEGORY,
            manaEnabled,
            "Switch the module off: no recipes, and placed machines stop working (they stay in the world).");
        manaPerTick = c.getInt(
            "lvManaPerTick",
            CATEGORY,
            manaPerTick,
            1,
            10_000,
            "Mana per tick with an LV core (x4 per tier above). With euPerMana 2 the default uses each tier's full voltage at 1 A.");
        euPerMana = c.getInt("euPerMana", CATEGORY, euPerMana, 1, 10_000, "EU per mana.");
        manaPerPetal = c.getInt(
            "lvManaPerPetal",
            CATEGORY,
            manaPerPetal,
            1,
            10_000_000,
            "Mana one mystical petal is worth with an LV core (x4 per tier above, so petals go at the same pace at every tier).");
        manaRange = c.getInt(
            "poolRange",
            CATEGORY,
            manaRange,
            1,
            16,
            "How far sideways the spring reaches pools with an LV core (one more per tier above).");
        manaHeight = c.getInt(
            "poolHeight",
            CATEGORY,
            manaHeight,
            0,
            16,
            "How far up and down it reaches with an LV core (one more every second tier above).");
        manaHologramRange = c.getInt(
            "hologramRange",
            CATEGORY,
            manaHologramRange,
            0,
            64,
            "Blocks within which a spring's hologram shows (each spring's is off until switched on in its GUI or with a screwdriver). Client side; 0 turns them all off.");
    }
}
