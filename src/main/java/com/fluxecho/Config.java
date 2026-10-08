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
        BLOOD = "bloodmagic", MOBS = "mobs", CROPS = "crops", MANA = "botania", ENCHANT = "enchanting", CODEX = "codex",
        AE = "ae2";

    public static int firstMachineId = 24530;
    public static boolean defaultRecipes = true;

    public static boolean installQuests = true;

    public static boolean beesEnabled = true;
    public static int imprintTicks = 100, imprintEut = 16;
    public static int incubateTicks = 200, incubateEut = 24;
    public static int honeyPerPrincess = 12, dronesWithPrincess = 2;
    public static int honeyPerDrones = 16, dronesPerBatch = 16;
    public static int fertilizerPerSaplings = 4, saplingsPerBatch = 4;
    public static int honeyPerButterfly = 8, butterfliesPerBatch = 1;

    public static boolean thaumEnabled = true;
    public static int essentiaEuPerUnit = 128, essentiaPerShard = 64;
    public static int visEuPerCentiVis = 1, visPerShard = 200, visMaxTicks = 2400;
    public static int insightTicks = 200, insightEut = 16;

    public static boolean bloodEnabled = true;
    public static int[] lpPerTick = { 4, 8, 16, 32, 48, 64 };
    public static int euPerLp = 2, lpPerMeat = 2000, altarRange = 5, altarHeight = 10;

    public static boolean mobsEnabled = true;
    public static int mobEuPerHealth = 128, mobMinTicks = 200;
    public static boolean mobBosses = true;
    public static int mobBossMultiplier = 50;
    public static String[] mobDropBlacklist = { "minecraft:skull:1" };

    public static boolean cropsEnabled = true;
    public static int cropImprintTicks = 100, cropImprintEut = 16;
    public static int seedTicks = 200, seedEut = 24, sticksPerSeed = 1;

    public static boolean manaEnabled = true;
    public static int manaPerTick = 64, euPerMana = 2, manaPerPetal = 5000, manaRange = 4, manaHeight = 2;

    public static boolean enchantEnabled = true;
    public static int enchantEuPerLevel = 16384, lapisPerLevel = 1;
    public static String[] enchantBlacklist = {};

    public static boolean codexHints = true;

    public static boolean aeEnabled = true;

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
        fertilizerPerSaplings = c.getInt(
            "fertilizerPerSaplings",
            BEES,
            fertilizerPerSaplings,
            0,
            64,
            "Forestry fertilizer per batch of saplings from a tree imprint.");
        saplingsPerBatch = c
            .getInt("saplingsPerBatch", BEES, saplingsPerBatch, 1, 64, "Saplings in a batch from a tree imprint.");
        honeyPerButterfly = c.getInt(
            "honeyPerButterfly",
            BEES,
            honeyPerButterfly,
            0,
            64,
            "Honey drops per batch of butterflies from a butterfly imprint.");
        butterfliesPerBatch = c.getInt(
            "butterfliesPerBatch",
            BEES,
            butterfliesPerBatch,
            1,
            64,
            "Butterflies in a batch from a butterfly imprint.");

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

        c.setCategoryComment(
            MOBS,
            "Prey (needs MobsInfo): take an imprint of a mob you killed yourself, then the Prey Echo drops its loot from EU.");
        mobsEnabled = c.getBoolean(
            "enabled",
            MOBS,
            mobsEnabled,
            "Switch the module off: no recipes, and placed machines stop working (they stay in the world).");
        mobEuPerHealth = c.getInt(
            "euPerHealth",
            MOBS,
            mobEuPerHealth,
            1,
            1_000_000,
            "EU per point of the mob's max health, per kill. The Prey Echo is an LV machine; a kill never takes less than minTicks.");
        mobMinTicks = c.getInt("minTicks", MOBS, mobMinTicks, 1, 72000, "Shortest time of one kill, in ticks.");
        mobBosses = c.getBoolean(
            "allowBosses",
            MOBS,
            mobBosses,
            "Let the Prey Echo repeat bosses (the Wither, Twilight Forest bosses, ...) you killed yourself.");
        mobBossMultiplier = c.getInt(
            "bossMultiplier",
            MOBS,
            mobBossMultiplier,
            1,
            100_000,
            "A boss costs this many times the EU (and time) of a mob with its health.");
        mobDropBlacklist = c.getStringList(
            "dropBlacklist",
            MOBS,
            mobDropBlacklist,
            "Drops the Prey Echo never makes, as modid:name or modid:name:damage.");

        c.setCategoryComment(
            CROPS,
            "Crops (IC2 and Crops++): imprint a fully scanned seed bag once, then get seed bags of it with its stats.");
        cropsEnabled = c.getBoolean(
            "enabled",
            CROPS,
            cropsEnabled,
            "Switch the module off: no recipes, and placed machines stop working (they stay in the world).");
        cropImprintTicks = c
            .getInt("imprintTicks", CROPS, cropImprintTicks, 1, 72000, "Ticks the Seed Imprinter takes per imprint.");
        cropImprintEut = c.getInt("imprintEuPerTick", CROPS, cropImprintEut, 1, 32, "EU/t of the Seed Imprinter (LV).");
        seedTicks = c.getInt("seedTicks", CROPS, seedTicks, 1, 72000, "Ticks the Seed Echo takes per seed bag.");
        seedEut = c.getInt("seedEuPerTick", CROPS, seedEut, 1, 32, "EU/t of the Seed Echo (LV).");
        sticksPerSeed = c.getInt("sticksPerSeed", CROPS, sticksPerSeed, 0, 64, "Crop sticks per seed bag.");

        c.setCategoryComment(
            MANA,
            "Botania: mana from EU into the mana pools near the Mana Echo, a few petals along the way.");
        manaEnabled = c.getBoolean(
            "enabled",
            MANA,
            manaEnabled,
            "Switch the module off: no recipes, and placed machines stop working (they stay in the world).");
        manaPerTick = c.getInt(
            "manaPerTick",
            MANA,
            manaPerTick,
            1,
            100_000,
            "Mana per tick. The Mana Echo is an MV machine: keep manaPerTick x euPerMana at 128 or below.");
        euPerMana = c.getInt("euPerMana", MANA, euPerMana, 1, 10_000, "EU per mana.");
        manaPerPetal = c.getInt("manaPerPetal", MANA, manaPerPetal, 1, 10_000_000, "Mana one mystical petal is worth.");
        manaRange = c.getInt("poolRange", MANA, manaRange, 1, 16, "How far sideways the Mana Echo looks for pools.");
        manaHeight = c.getInt("poolHeight", MANA, manaHeight, 0, 16, "How far up and down it looks for pools.");

        c.setCategoryComment(ENCHANT, "Enchanting: copy an enchanted book you hold from EU, books and lapis.");
        enchantEnabled = c.getBoolean(
            "enabled",
            ENCHANT,
            enchantEnabled,
            "Switch the module off: no recipe, and placed machines stop working (they stay in the world).");
        enchantEuPerLevel = c.getInt(
            "euPerLevel",
            ENCHANT,
            enchantEuPerLevel,
            1,
            100_000_000,
            "EU per enchantment level on the copied book (Fortune III is 3 levels).");
        lapisPerLevel = c.getInt("lapisPerLevel", ENCHANT, lapisPerLevel, 0, 64, "Lapis lazuli per enchantment level.");
        enchantBlacklist = c
            .getStringList("blacklist", ENCHANT, enchantBlacklist, "Enchantment ids (numbers) that are never copied.");

        c.setCategoryComment(CODEX, "The Echo Codex and the tooltip lines.");
        codexHints = c.getBoolean(
            "tooltipHints",
            CODEX,
            codexHints,
            "Client: a line under bees, seed bags, phials, ... saying whether your team has done it once (NEI shows it too).");

        c.setCategoryComment(AE, "Applied Energistics 2: the Echo ME Provider.");
        aeEnabled = c.getBoolean(
            "enabled",
            AE,
            aeEnabled,
            "Switch the Echo ME Provider off: no recipe, and placed ones offer no patterns.");

        if (c.hasChanged()) c.save();
    }
}
