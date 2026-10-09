package com.fluxecho;

import java.io.File;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;

import com.fluxecho.logic.BloodRates;

/**
 * {@code config/fluxecho.cfg}. Every module can be switched off; its machines stay registered so placed ones do not
 * vanish from a world, only the recipes and the work stop.
 */
public final class Config {

    private static final String GENERAL = "general", QUESTS = "quests", BEES = "bees", THAUM = "thaumcraft",
        BLOOD = "bloodmagic", MOBS = "mobs", CROPS = "crops", MANA = "botania", CODEX = "codex", AE = "ae2",
        EFFECTS = "effects", GATES = "gates", NEXUS = "nexus";

    public static int firstMachineId = 24530;
    public static boolean defaultRecipes = true;

    public static boolean installQuests = true;

    public static boolean beesEnabled = true;
    public static int imprintTicks = 100, imprintEut = 16;
    public static int incubateTicks = 200, incubateEut = 24;
    public static int assembleTicks = 100, assembleEut = 16;
    public static int honeyPerPrincess = 12, dronesWithPrincess = 2;
    public static int honeyPerDrones = 16, dronesPerBatch = 16;
    public static int fertilizerPerSaplings = 4, saplingsPerBatch = 4;
    public static int honeyPerButterfly = 8, butterfliesPerBatch = 1;

    public static boolean thaumEnabled = true;
    public static int essentiaEuPerUnit = 128, essentiaPerShard = 64;
    public static int visEuPerCentiVis = 10;
    public static int visPedestalRate = 25, visModuleRate = 50, visWirelessRange = 32, visLinkEut = 32768,
        visHologramRange = 12;
    public static long visPedestalBuffer = 4_000_000;
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
    public static int manaPerTick = 16, euPerMana = 2, manaPerPetal = 1250, manaRange = 4, manaHeight = 2,
        manaHologramRange = 16;

    public static boolean codexHints = true;

    public static boolean nexusEnabled = true, nexusEffects = true;
    public static double gritChance = 0.15, researchScale = 1.0;
    public static int crystalGrit = 3, nexusUpkeep = 512, nexusCompute = 20, innerRadius = 32, recordCooldown = 24000,
        manifestEut = 480, libraryUpkeep = 128, nexusEffectRange = 128;

    public static boolean machineEffects = true, flowTrails = true;
    public static int effectRange = 32, machineHologramRange = 16;

    public static boolean aeEnabled = true;

    public static boolean gateRecipe = true, gateLiveView = true, gateRealView = true, gateHoldBase = true;
    public static int gateDimension = 7270, gateProvider = 7270, gateViewRange = 32;

    /** Written into the file; an older one is brought up to date by {@link #upgrade}. */
    private static final String VERSION = "0.9.2";

    private Config() {}

    public static void load(File file) {
        Configuration c = new Configuration(file, VERSION);
        upgrade(c);

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
            "Bees (Forestry): imprint a real bee once, then incubate pristine princesses and drones of it; take gene samples of single traits and put them together into the bee you want.");
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
        assembleTicks = c
            .getInt("assembleTicks", BEES, assembleTicks, 1, 72000, "Ticks the Gene Assembler takes per job.");
        assembleEut = c.getInt("assembleEuPerTick", BEES, assembleEut, 1, 32, "EU/t of the Gene Assembler (LV).");
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
            "pedestalEuPerCentivis",
            THAUM,
            visEuPerCentiVis,
            1,
            1_000_000,
            "EU per centivis the Flux Vis Pedestal puts into a wand (100 centivis = 1 vis; 10 = 1000 EU per vis, about 1500 EU/t with all six primals filling and no module).");
        visPedestalRate = c.getInt(
            "pedestalCentivisPerTick",
            THAUM,
            visPedestalRate,
            1,
            100_000,
            "Centivis per primal per tick the Flux Vis Pedestal draws with no extraction module (25 = 5 vis a second).");
        visModuleRate = c.getInt(
            "extractionModuleCentivisPerTick",
            THAUM,
            visModuleRate,
            0,
            100_000,
            "Centivis per primal per tick each extraction module adds (they add up, up to 4).");
        visPedestalBuffer = parseLong(
            c.getString(
                "pedestalBufferEU",
                THAUM,
                String.valueOf(visPedestalBuffer),
                "EU the Flux Vis Pedestal stores."),
            visPedestalBuffer);
        visWirelessRange = c.getInt(
            "wirelessModuleRange",
            THAUM,
            visWirelessRange,
            1,
            512,
            "Blocks within which the wireless module fills the wands, sceptres, staves and vis amulets the team carries.");
        visLinkEut = c.getInt(
            "linkModuleEuPerTick",
            THAUM,
            visLinkEut,
            1,
            Integer.MAX_VALUE,
            "Most EU/t the link module draws from the team's GT wireless network (FluxLite's network) into the pedestal.");
        visHologramRange = c.getInt(
            "hologramRange",
            THAUM,
            visHologramRange,
            0,
            64,
            "Blocks within which the Flux Vis Pedestal shows its status hologram (power, modules, the wand's vis). Client side; 0 turns it off.");
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
            "Botania: the Mana Echo Spring draws the echo of mana up from the flux layer with EU and a few petals, into the mana pools around it. The circuit in its core (LV to LuV) sets its tier: each tier draws 4x the mana of the one below, for 4x the EU, and a petal is worth 4x as much.");
        manaEnabled = c.getBoolean(
            "enabled",
            MANA,
            manaEnabled,
            "Switch the module off: no recipes, and placed machines stop working (they stay in the world).");
        manaPerTick = c.getInt(
            "lvManaPerTick",
            MANA,
            manaPerTick,
            1,
            10_000,
            "Mana per tick with an LV core (x4 per tier above). With euPerMana 2 the default uses each tier's full voltage at 1 A.");
        euPerMana = c.getInt("euPerMana", MANA, euPerMana, 1, 10_000, "EU per mana.");
        manaPerPetal = c.getInt(
            "lvManaPerPetal",
            MANA,
            manaPerPetal,
            1,
            10_000_000,
            "Mana one mystical petal is worth with an LV core (x4 per tier above, so petals go at the same pace at every tier).");
        manaRange = c.getInt(
            "poolRange",
            MANA,
            manaRange,
            1,
            16,
            "How far sideways the spring reaches pools with an LV core (one more per tier above).");
        manaHeight = c.getInt(
            "poolHeight",
            MANA,
            manaHeight,
            0,
            16,
            "How far up and down it reaches with an LV core (one more every second tier above).");
        manaHologramRange = c.getInt(
            "hologramRange",
            MANA,
            manaHologramRange,
            0,
            64,
            "Blocks within which a spring's hologram shows (each spring's is off until switched on in its GUI or with a screwdriver). Client side; 0 turns them all off.");

        c.setCategoryComment(CODEX, "The Echo Codex and the tooltip lines.");
        codexHints = c.getBoolean(
            "tooltipHints",
            CODEX,
            codexHints,
            "Client: a line under bees, seed bags, phials, ... saying whether your team has done it once (NEI shows it too).");

        c.setCategoryComment(
            NEXUS,
            "The Flux Nexus (from HV): the flux materials, the nexus itself, its research and the modules docked on its rings. Energy comes from the owner's team wireless network.");
        nexusEnabled = c.getBoolean(
            "enabled",
            NEXUS,
            nexusEnabled,
            "Switch the nexus and its modules off: no recipes, and placed ones stop working (they stay in the world). The flux materials stay.");
        gritChance = c.get(
            NEXUS,
            "gritChance",
            gritChance,
            "Chance that a Flux Shard Collector gives off one flux grit with each ore it condenses (0-1). The only source of flux grit.",
            0,
            1)
            .getDouble();
        crystalGrit = c
            .getInt("crystalGrit", NEXUS, crystalGrit, 1, 64, "Flux grit per flux crystal in GT's autoclave.");
        nexusUpkeep = c.getInt(
            "upkeepEuPerTick",
            NEXUS,
            nexusUpkeep,
            0,
            Integer.MAX_VALUE,
            "EU/t a formed nexus draws from its owner's team wireless network to stay anchored (phase I).");
        nexusCompute = c.getInt(
            "computePerSecond",
            NEXUS,
            nexusCompute,
            0,
            1_000_000,
            "Flux compute a formed, powered nexus produces each second (phase I); research runs on it.");
        researchScale = c
            .get(
                NEXUS,
                "researchCostScale",
                researchScale,
                "Multiplies the compute every research takes (0.1 = ten times faster).",
                0.01,
                100)
            .getDouble();
        manifestEut = c.getInt(
            "manifestEuPerTick",
            NEXUS,
            manifestEut,
            1,
            Integer.MAX_VALUE,
            "EU/t the nexus's manifestation table draws on top of the upkeep while it makes something.");
        recordCooldown = c.getInt(
            "recordCooldownTicks",
            NEXUS,
            recordCooldown,
            0,
            Integer.MAX_VALUE,
            "Ticks before the same Echo Codex entry can be pressed into an echo crystal again (24000 = one day).");
        innerRadius = c.getInt(
            "innerRingRadius",
            NEXUS,
            innerRadius,
            12,
            128,
            "Blocks from the nexus's centre to the centre of each inner ring slot. Changing it moves the slots of nexuses already built.");
        libraryUpkeep = c.getInt(
            "libraryUpkeepEuPerTick",
            NEXUS,
            libraryUpkeep,
            0,
            Integer.MAX_VALUE,
            "EU/t an Echo Library draws while it is docked on a nexus.");
        nexusEffects = c.getBoolean(
            "effects",
            NEXUS,
            nexusEffects,
            "Client: draw the formed nexus and its modules (floating rings, light column, bridges, the library's depths). Off: the bare blocks.");
        nexusEffectRange = c.getInt(
            "effectRange",
            NEXUS,
            nexusEffectRange,
            16,
            512,
            "Client: blocks within which a formed nexus and its modules are drawn.");

        c.setCategoryComment(
            EFFECTS,
            "How the echo machines show their work in the world: an effect above each while it works, trails to what the Mana Echo Spring, the Blood Echo and a pedestal's wireless module fill, and their holograms.");
        machineEffects = c.getBoolean(
            "machineEffects",
            EFFECTS,
            machineEffects,
            "Client: an effect above an echo machine while it works (rings, motes, sparks in its colour).");
        flowTrails = c.getBoolean(
            "flowTrails",
            EFFECTS,
            flowTrails,
            "Client: a trail from the Mana Echo Spring to each mana pool it fills, from the Blood Echo to its altar, and from a Flux Vis Pedestal to each player its wireless module charges.");
        effectRange = c.getInt("effectRange", EFFECTS, effectRange, 0, 128, "Client: blocks within which they show.");
        machineHologramRange = c.getInt(
            "hologramRange",
            EFFECTS,
            machineHologramRange,
            0,
            64,
            "Blocks within which an echo machine's hologram shows (each one's is off until switched on in its GUI or with a screwdriver on its side). 0 turns them all off.");

        c.setCategoryComment(AE, "Applied Energistics 2: the Echo ME Provider.");
        aeEnabled = c.getBoolean(
            "enabled",
            AE,
            aeEnabled,
            "Switch the Echo ME Provider off: no recipe, and placed ones offer no patterns.");

        c.setCategoryComment(
            GATES,
            "Light gates (prototype): each gate opens onto a room of its own, closed all round, in the folded zone of the gate's own dimension (past x and z 1048576, where nothing generates). Close to a gate you see into it; walking through is a step within the same world, nothing reloads.");
        gateRecipe = c.getBoolean("enableRecipe", GATES, gateRecipe, "Register the crafting recipe of the light gate.");
        gateViewRange = c.getInt(
            "viewRange",
            GATES,
            gateViewRange,
            0,
            64,
            "Blocks within which players see through a gate. The far side is kept loaded for them a little further out, so it is there before they look. 0 turns the view off for everyone.");
        gateLiveView = c.getBoolean(
            "liveView",
            GATES,
            gateLiveView,
            "Client: draw what lies behind a gate on its membrane. Off: the membrane only glows (for weak graphics cards or a shader pack it does not get along with).");
        gateRealView = c.getBoolean(
            "realView",
            GATES,
            gateRealView,
            "Client, with Angelica: walking through a gate is seamless, the far side built before you step in. Without a shader pack the game also draws the world a second time from the far side of each gate in sight, so the membrane shows everything there (machines, mobs, players, holograms, weather); that costs about one more frame's drawing while a gate is in sight. With a shader pack the gate keeps the simpler view (blocks, machines, mobs and FluxEcho's holograms, without the pack's lighting). Off: the 0.8.2 behaviour, with a ripple when walking through.");
        gateHoldBase = c.getBoolean(
            "holdBase",
            GATES,
            gateHoldBase,
            "While a player is inside a room, keep everything they had loaded round the gate outside loaded for them (and running), so walking back out reloads nothing. Off: only what the room's gate shows stays.");
        gateDimension = c.getInt(
            "dimensionId",
            GATES,
            gateDimension,
            -100000,
            100000,
            "Dimension id of the 0.8.1 prototype's flux interior. Rooms are no longer made there; it stays registered so worlds from 0.8.1 load, and its gates are moved into the folded zone.");
        gateProvider = c.getInt(
            "providerId",
            GATES,
            gateProvider,
            -100000,
            100000,
            "World provider id of the 0.8.1 prototype's flux interior; only when another mod takes it.");

        if (c.hasChanged()) c.save();
    }

    /**
     * Brings a config file written by an older FluxEcho up to date: settings that are gone are removed, and settings
     * whose default changed are reset to the new default (0.5.0: the vis pedestal costs ten times the EU, so its
     * buffer and link module grew too).
     */
    private static void upgrade(Configuration c) {
        String was = c.getLoadedConfigVersion();
        if (VERSION.equals(was)) return;
        if (older(was, "0.5.0")) {
            if (c.hasCategory("enchanting")) c.removeCategory(c.getCategory("enchanting"));
            if (c.hasCategory(THAUM)) {
                ConfigCategory t = c.getCategory(THAUM);
                for (String k : new String[] { "visEuPerCentiVis", "visPerShard", "visMaxTicks", "pedestalBufferEU",
                    "linkModuleEuPerTick" }) t.remove(k);
            }
        }
        // 0.6.0: the Mana Echo Spring is tiered by its core; its rates are per LV now
        if (older(was, "0.6.0") && c.hasCategory(MANA)) {
            ConfigCategory m = c.getCategory(MANA);
            for (String k : new String[] { "manaPerTick", "manaPerPetal" }) m.remove(k);
        }
        // 0.9.2: a library keeps one sample on each shelf of its hall
        if (older(was, "0.9.2") && c.hasCategory(NEXUS)) c.getCategory(NEXUS)
            .remove("librarySamples");
    }

    /** Whether a file written by version {@code was} (null: before versions were written) predates {@code v}. */
    static boolean older(String was, String v) {
        if (was == null || was.isEmpty()) return true;
        String[] a = was.split("\\."), b = v.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? parse(a[i]) : 0, y = i < b.length ? parse(b[i]) : 0;
            if (x != y) return x < y;
        }
        return false;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long parseLong(String s, long fallback) {
        try {
            return Math.max(1, Long.parseLong(s.trim()));
        } catch (NumberFormatException | NullPointerException e) {
            return fallback;
        }
    }
}
