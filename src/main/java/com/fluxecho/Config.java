package com.fluxecho;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.fluxecho.logic.BloodRates;

/**
 * {@code config/fluxecho.cfg}. Every module can be switched off; its machines stay registered so placed ones do not
 * vanish from a world, only the recipes and the work stop.
 */
public final class Config {

    private static final String GENERAL = "general", QUESTS = "quests", BEES = "bees", THAUM = "thaumcraft",
        BLOOD = "bloodmagic";

    public static int firstMachineId = 24530;
    public static boolean defaultRecipes = true;

    public static boolean installQuests = true;

    public static boolean beesEnabled = true;
    public static int imprintTicks = 100, imprintEut = 16;
    public static int incubateTicks = 200, incubateEut = 24;
    public static int honeyPerPrincess = 12, dronesWithPrincess = 2;
    public static int honeyPerDrones = 16, dronesPerBatch = 16;

    public static boolean thaumEnabled = true;
    public static int essentiaEuPerUnit = 128, essentiaPerShard = 64;
    public static int visEuPerCentiVis = 1, visPerShard = 200, visMaxTicks = 2400;
    public static int insightTicks = 200, insightEut = 16;

    public static boolean bloodEnabled = true;
    public static int[] lpPerTick = { 4, 8, 16, 32, 48, 64 };
    public static int euPerLp = 2, lpPerMeat = 2000, altarRange = 5, altarHeight = 10;

    private Config() {}

    public static void load(File file) {
        Configuration c = new Configuration(file);

        c.setCategoryComment(GENERAL, "Settings shared by every module.");
        firstMachineId = c.getInt(
            "firstMachineId",
            GENERAL,
            firstMachineId,
            1,
            32000,
            "First of the 40 GT machine ids FluxEcho keeps (24530-24569 by default). Change it only for a new world or when another mod takes these ids.");
        defaultRecipes = c.getBoolean(
            "enableDefaultRecipes",
            GENERAL,
            defaultRecipes,
            "Register the crafting recipes of the machines; switch off to write your own with CraftTweaker.");

        c.setCategoryComment(QUESTS, "FluxEcho's quest lines for BetterQuesting.");
        installQuests = c.getBoolean(
            "install",
            QUESTS,
            installQuests,
            "Put FluxEcho's quest lines into every world's quest database when it loads (missing quests added, changed ones updated, progress kept), and copy them into config/betterquesting/DefaultQuests and QuestLinesOrder.txt at startup so /bq_admin default load keeps them. Other quest lines are never touched.");

        c.setCategoryComment(
            BEES,
            "Bees (Forestry): imprint a real bee once, then incubate pristine princesses and drones of it.");
        beesEnabled = c.getBoolean(
            "enabled",
            BEES,
            beesEnabled,
            "Switch the module off: no recipes, no NEI pages, and placed machines stop working (they stay in the world).");
        imprintTicks = c
            .getInt("imprintTicks", BEES, imprintTicks, 1, 72000, "Ticks the Bee Imprinter takes per imprint.");
        imprintEut = c.getInt("imprintEuPerTick", BEES, imprintEut, 1, 32, "EU/t of the Bee Imprinter (LV).");
        incubateTicks = c
            .getInt("incubateTicks", BEES, incubateTicks, 1, 72000, "Ticks the Larva Incubator takes per batch.");
        incubateEut = c.getInt("incubateEuPerTick", BEES, incubateEut, 1, 32, "EU/t of the Larva Incubator (LV).");
        honeyPerPrincess = c.getInt(
            "honeyPerPrincess",
            BEES,
            honeyPerPrincess,
            0,
            64,
            "Honey drops per princess batch (circuit 1: a princess and her drones).");
        dronesWithPrincess = c
            .getInt("dronesWithPrincess", BEES, dronesWithPrincess, 0, 64, "Drones that come with a princess.");
        honeyPerDrones = c
            .getInt("honeyPerDrones", BEES, honeyPerDrones, 0, 64, "Honey drops per drone batch (circuit 2).");
        dronesPerBatch = c
            .getInt("dronesPerBatch", BEES, dronesPerBatch, 1, 64, "Drones in a drone batch (circuit 2).");

        c.setCategoryComment(
            THAUM,
            "Thaumcraft: aspects your team has held once (a phial, a jar, a crystal, a shard) are synthesized from EU; wands are charged from EU with the primals you know.");
        thaumEnabled = c.getBoolean(
            "enabled",
            THAUM,
            thaumEnabled,
            "Switch the module off: no recipes, and placed machines and outlets stop working (they stay in the world).");
        essentiaEuPerUnit = c.getInt(
            "essentiaEuPerUnit",
            THAUM,
            essentiaEuPerUnit,
            1,
            1_000_000,
            "EU per primal unit of synthesized essentia. A primal is 1 unit, a compound the sum of its components (Lux = 2, Permutatio = 3, ...).");
        essentiaPerShard = c.getInt(
            "essentiaUnitsPerShard",
            THAUM,
            essentiaPerShard,
            1,
            100_000,
            "Primal units of essentia one vis crystal shard pays for (any shard, the balanced one too).");
        visEuPerCentiVis = c.getInt(
            "visEuPerCentiVis",
            THAUM,
            visEuPerCentiVis,
            1,
            10_000,
            "EU per centivis the Vis Charger puts into a wand (100 centivis = 1 vis).");
        visPerShard = c.getInt(
            "visPerShard",
            THAUM,
            visPerShard,
            1,
            100_000,
            "Vis (summed over the primals) one vis crystal shard pays for in the Vis Charger.");
        visMaxTicks = c.getInt(
            "visMaxTicks",
            THAUM,
            visMaxTicks,
            20,
            72000,
            "Longest Vis Charger cycle; a wand that holds more is topped up over several cycles.");
        insightTicks = c.getInt(
            "insightTicks",
            THAUM,
            insightTicks,
            1,
            72000,
            "Ticks the Insight Echo takes to give the owner a scanned item's research points again (one paper each).");
        insightEut = c.getInt("insightEuPerTick", THAUM, insightEut, 1, 32, "EU/t of the Insight Echo (LV).");

        c.setCategoryComment(
            BLOOD,
            "Blood Magic: LP from EU and a little meat, as fast as the bound blood orb you made in the altar allows.");
        bloodEnabled = c.getBoolean(
            "enabled",
            BLOOD,
            bloodEnabled,
            "Switch the module off: no recipes, and placed machines stop working (they stay in the world).");
        String[] rates = c.getStringList(
            "lpPerTick",
            BLOOD,
            new String[] { "4", "8", "16", "32", "48", "64" },
            "LP per tick for blood orbs of level 1 (weak) to 6 (transcendent); higher levels use the last entry. The Blood Echo is an MV machine: keep rate x euPerLp at 128 or below.");
        lpPerTick = BloodRates.parse(rates);
        euPerLp = c.getInt("euPerLp", BLOOD, euPerLp, 1, 1000, "EU per LP.");
        lpPerMeat = c
            .getInt("lpPerMeat", BLOOD, lpPerMeat, 1, 1_000_000, "LP one piece of raw meat or rotten flesh is worth.");
        altarRange = c.getInt(
            "altarRange",
            BLOOD,
            altarRange,
            1,
            16,
            "How far sideways the Blood Echo looks for an altar (like the Well of Suffering).");
        altarHeight = c
            .getInt("altarHeight", BLOOD, altarHeight, 1, 32, "How far up and down the Blood Echo looks for an altar.");

        if (c.hasChanged()) c.save();
    }
}
