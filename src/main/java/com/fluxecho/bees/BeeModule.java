package com.fluxecho.bees;

import net.minecraft.item.Item;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.core.MachineId;
import com.fluxecho.core.Machines;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Bees (Forestry). Only called when Forestry is loaded. The item and machines are registered even when the module
 * is switched off, so imprints and placed machines survive; recipes and NEI pages are not.
 */
public final class BeeModule {

    public static Item imprint;

    private BeeModule() {}

    public static void preInit() {
        imprint = new ItemBeeImprint();
        GameRegistry.registerItem(imprint, "bee_imprint");
    }

    public static void machines() {
        Machines.put(MachineId.BEE_IMPRINTER, new MTEBeeImprinter(Machines.claim(MachineId.BEE_IMPRINTER)));
        Machines.put(MachineId.BEE_INCUBATOR, new MTEBeeIncubator(Machines.claim(MachineId.BEE_INCUBATOR)));
        BeeNei.imprinterMap();
        BeeNei.incubatorMap();
    }

    public static void postInit() {
        if (!Config.beesEnabled || !Config.defaultRecipes) return;
        try {
            BeeCrafting.register();
        } catch (Throwable t) {
            FluxEcho.LOG.error("Failed to register the bee machine recipes", t);
        }
    }

    public static void loadComplete() {
        if (Config.beesEnabled) BeeNei.addPages();
    }
}
