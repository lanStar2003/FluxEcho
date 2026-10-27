package com.fluxecho.config;

import net.minecraftforge.common.config.Configuration;

/**
 * The thaumcraft section of config/fluxecho.cfg: essentia from EU, the Flux Vis Pedestal and its modules, the Insight
 * Echo. Since 0.17.0 each section of the config file is its own class, listed in Config.SECTIONS, so that work on
 * different subsystems (and later on each building) never edits the same file; the keys, defaults, bounds and comments
 * are the ones 0.16.0 shipped, word for word.
 */
public final class ThaumConfig {

    public static final String CATEGORY = "thaumcraft";

    public static boolean thaumEnabled = true;
    public static int essentiaEuPerUnit = 128, essentiaPerShard = 64;
    public static int visEuPerCentiVis = 10;
    public static int visPedestalRate = 25, visModuleRate = 50, visWirelessRange = 32, visLinkEut = 32768,
        visHologramRange = 12;
    public static long visPedestalBuffer = 4_000_000;
    public static int insightTicks = 200, insightEut = 16;

    private ThaumConfig() {}

    public static void load(Configuration c) {
        c.setCategoryComment(
            CATEGORY,
            "Thaumcraft: aspects your team has held once (a phial, a jar, a crystal, a shard) are synthesized from EU; wands are charged from EU with the primals you know.");
        thaumEnabled = c.getBoolean(
            "enabled",
            CATEGORY,
            thaumEnabled,
            "Switch the module off: no recipes, and placed machines and outlets stop working (they stay in the world).");
        essentiaEuPerUnit = c.getInt(
            "essentiaEuPerUnit",
            CATEGORY,
            essentiaEuPerUnit,
            1,
            1_000_000,
            "EU per primal unit of synthesized essentia. A primal is 1 unit, a compound the sum of its components (Lux = 2, Permutatio = 3, ...).");
        essentiaPerShard = c.getInt(
            "essentiaUnitsPerShard",
            CATEGORY,
            essentiaPerShard,
            1,
            100_000,
            "Primal units of essentia one vis crystal shard pays for (any shard, the balanced one too).");
        visEuPerCentiVis = c.getInt(
            "pedestalEuPerCentivis",
            CATEGORY,
            visEuPerCentiVis,
            1,
            1_000_000,
            "EU per centivis the Flux Vis Pedestal puts into a wand (100 centivis = 1 vis; 10 = 1000 EU per vis, about 1500 EU/t with all six primals filling and no module).");
        visPedestalRate = c.getInt(
            "pedestalCentivisPerTick",
            CATEGORY,
            visPedestalRate,
            1,
            100_000,
            "Centivis per primal per tick the Flux Vis Pedestal draws with no extraction module (25 = 5 vis a second).");
        visModuleRate = c.getInt(
            "extractionModuleCentivisPerTick",
            CATEGORY,
            visModuleRate,
            0,
            100_000,
            "Centivis per primal per tick each extraction module adds (they add up, up to 4).");
        visPedestalBuffer = parseLong(
            c.getString(
                "pedestalBufferEU",
                CATEGORY,
                String.valueOf(visPedestalBuffer),
                "EU the Flux Vis Pedestal stores."),
            visPedestalBuffer);
        visWirelessRange = c.getInt(
            "wirelessModuleRange",
            CATEGORY,
            visWirelessRange,
            1,
            512,
            "Blocks within which the wireless module fills the wands, sceptres, staves and vis amulets the team carries.");
        visLinkEut = c.getInt(
            "linkModuleEuPerTick",
            CATEGORY,
            visLinkEut,
            1,
            Integer.MAX_VALUE,
            "Most EU/t the link module draws from the team's GT wireless network (FluxLite's network) into the pedestal.");
        visHologramRange = c.getInt(
            "hologramRange",
            CATEGORY,
            visHologramRange,
            0,
            64,
            "Blocks within which the Flux Vis Pedestal shows its status hologram (power, modules, the wand's vis). Client side; 0 turns it off.");
        insightTicks = c.getInt(
            "insightTicks",
            CATEGORY,
            insightTicks,
            1,
            72000,
            "Ticks the Insight Echo takes to give the owner a scanned item's research points again (one paper each).");
        insightEut = c.getInt("insightEuPerTick", CATEGORY, insightEut, 1, 32, "EU/t of the Insight Echo (LV).");
    }

    private static long parseLong(String s, long fallback) {
        try {
            return Math.max(1, Long.parseLong(s.trim()));
        } catch (NumberFormatException | NullPointerException e) {
            return fallback;
        }
    }
}
