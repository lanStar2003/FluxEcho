package com.fluxecho.config;

import net.minecraftforge.common.config.Configuration;

/**
 * The mobs section of config/fluxecho.cfg: the Prey Echo (MobsInfo). Since 0.17.0 each section of the config file is
 * its own class, listed in Config.SECTIONS, so that work on different subsystems (and later on each building) never
 * edits the same file; the keys, defaults, bounds and comments are the ones 0.16.0 shipped, word for word.
 */
public final class MobsConfig {

    public static final String CATEGORY = "mobs";

    public static boolean mobsEnabled = true;
    public static int mobEuPerHealth = 128, mobMinTicks = 200;
    public static boolean mobBosses = true;
    public static int mobBossMultiplier = 50;
    public static String[] mobDropBlacklist = { "minecraft:skull:1" };

    private MobsConfig() {}

    public static void load(Configuration c) {
        c.setCategoryComment(
            CATEGORY,
            "Prey (needs MobsInfo): take an imprint of a mob you killed yourself, then the Prey Echo drops its loot from EU.");
        mobsEnabled = c.getBoolean(
            "enabled",
            CATEGORY,
            mobsEnabled,
            "Switch the module off: no recipes, and placed machines stop working (they stay in the world).");
        mobEuPerHealth = c.getInt(
            "euPerHealth",
            CATEGORY,
            mobEuPerHealth,
            1,
            1_000_000,
            "EU per point of the mob's max health, per kill. The Prey Echo is an LV machine; a kill never takes less than minTicks.");
        mobMinTicks = c.getInt("minTicks", CATEGORY, mobMinTicks, 1, 72000, "Shortest time of one kill, in ticks.");
        mobBosses = c.getBoolean(
            "allowBosses",
            CATEGORY,
            mobBosses,
            "Let the Prey Echo repeat bosses (the Wither, Twilight Forest bosses, ...) you killed yourself.");
        mobBossMultiplier = c.getInt(
            "bossMultiplier",
            CATEGORY,
            mobBossMultiplier,
            1,
            100_000,
            "A boss costs this many times the EU (and time) of a mob with its health.");
        mobDropBlacklist = c.getStringList(
            "dropBlacklist",
            CATEGORY,
            mobDropBlacklist,
            "Drops the Prey Echo never makes, as modid:name or modid:name:damage.");
    }
}
