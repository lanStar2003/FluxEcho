package com.fluxecho;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/**
 * {@code config/fluxecho.cfg}. Every module can be switched off; its machines stay registered so placed ones do not
 * vanish from a world, only the recipes and the work stop.
 */
public final class Config {

    private static final String GENERAL = "general", QUESTS = "quests", BEES = "bees";

    public static int firstMachineId = 24530;
    public static boolean defaultRecipes = true;

    public static boolean installQuests = true;

    public static boolean beesEnabled = true;
    public static int imprintTicks = 100, imprintEut = 16;
    public static int incubateTicks = 200, incubateEut = 24;
    public static int honeyPerPrincess = 12, dronesWithPrincess = 2;
    public static int honeyPerDrones = 16, dronesPerBatch = 16;

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
            "Copy FluxEcho's quest lines into config/betterquesting/DefaultQuests at startup and add them to QuestLinesOrder.txt. Other quest lines are never touched.");

        c.setCategoryComment(
            BEES,
            "Bees (Forestry): imprint a real bee once, then incubate pristine princesses and drones of it.");
        beesEnabled = c.getBoolean(
            "enabled",
            BEES,
            beesEnabled,
            "Switch the module off: no recipes, no NEI pages, and placed machines stop working (they stay in the world).");
        imprintTicks = c.getInt("imprintTicks", BEES, imprintTicks, 1, 72000, "Ticks the Bee Imprinter takes per imprint.");
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
        dronesPerBatch = c.getInt("dronesPerBatch", BEES, dronesPerBatch, 1, 64, "Drones in a drone batch (circuit 2).");

        if (c.hasChanged()) c.save();
    }
}
