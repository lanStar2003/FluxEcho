package com.fluxecho.crops;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.codex.Categories;
import com.fluxecho.core.EchoRecipeMaps;
import com.fluxecho.core.MachineId;
import com.fluxecho.core.Machines;
import com.fluxecho.logic.CropStats;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;
import ic2.api.crops.CropCard;
import ic2.api.crops.Crops;
import ic2.api.item.IC2Items;

/**
 * Crops (IC2; Crops++ adds its crops through the same API). Only called when IC2 is loaded. The imprint and machines
 * are registered even when the module is switched off; recipes and NEI pages are not, and the machines stop.
 */
public final class CropModule {

    public static Item imprint;

    private CropModule() {}

    static RecipeMap<?> imprinterMap() {
        return EchoRecipeMaps.map(
            MachineId.SEED_IMPRINTER,
            b -> b.maxIO(2, 1, 0, 0)
                .minInputs(0, 0)
                .useSpecialSlot()
                .slotOverlays(
                    (index, fluid, output, special) -> special ? GTUITextures.OVERLAY_SLOT_MICROSCOPE
                        : !output && !fluid && index == 0 ? GTUITextures.OVERLAY_SLOT_PAGE_BLANK : null));
    }

    static RecipeMap<?> seedMap() {
        return EchoRecipeMaps.map(
            MachineId.SEED_ECHO,
            b -> b.maxIO(1, 1, 0, 0)
                .minInputs(0, 0)
                .useSpecialSlot()
                .slotOverlays(
                    (index, fluid, output, special) -> special ? GTUITextures.OVERLAY_SLOT_PAGE_PRINTED : null));
    }

    public static void preInit() {
        imprint = new ItemCropImprint();
        GameRegistry.registerItem(imprint, "crop_imprint");
    }

    public static void machines() {
        Machines.put(MachineId.SEED_IMPRINTER, new MTESeedImprinter(Machines.claim(MachineId.SEED_IMPRINTER)));
        Machines.put(MachineId.SEED_ECHO, new MTESeedEcho(Machines.claim(MachineId.SEED_ECHO)));
        imprinterMap();
        seedMap();
    }

    public static void postInit() {
        Categories.register(new Categories.Category(Categories.CROP, key -> {
            CropCard c = CropImprints.byKey(key);
            return c == null ? null : CropImprints.seeds(c, new CropStats(1, 1, 1));
        }, key -> {
            CropCard c = CropImprints.byKey(key);
            return c == null ? key : CropImprints.name(c);
        }, s -> CropImprints.key(CropImprints.crop(s))));
        if (!Config.cropsEnabled || !Config.defaultRecipes) return;
        try {
            recipes();
        } catch (Throwable t) {
            FluxEcho.LOG.error("Failed to register the crop machine recipes", t);
        }
    }

    /** The imprinter takes a cropnalyzer, the echo crop sticks: what you used to breed crops by hand. */
    private static void recipes() {
        ItemStack cropnalyzer = IC2Items.getItem("cropnalyzer");
        ItemStack sticks = IC2Items.getItem("crop");
        ItemStack pearl = new ItemStack(Items.ender_pearl);
        if (cropnalyzer == null || sticks == null) {
            FluxEcho.LOG.warn("No IC2 cropnalyzer or crop sticks: the crop machines have no recipes");
            return;
        }
        GTModHandler.addCraftingRecipe(
            Machines.get(MachineId.SEED_IMPRINTER),
            GTModHandler.RecipeBits.NOT_REMOVABLE,
            new Object[] { "PEP", "CHC", "RZR", 'P', OrePrefixes.plate.get(Materials.Steel), 'E', pearl, 'C',
                OrePrefixes.circuit.get(Materials.LV), 'H', ItemList.Hull_LV.get(1), 'R', ItemList.Robot_Arm_LV.get(1),
                'Z', GTUtility.copyAmount(1, cropnalyzer) });
        GTModHandler.addCraftingRecipe(
            Machines.get(MachineId.SEED_ECHO),
            GTModHandler.RecipeBits.NOT_REMOVABLE,
            new Object[] { "PEP", "CHC", "SUS", 'P', OrePrefixes.plate.get(Materials.Steel), 'E', pearl, 'C',
                OrePrefixes.circuit.get(Materials.LV), 'H', ItemList.Hull_LV.get(1), 'U',
                ItemList.Electric_Pump_LV.get(1), 'S', GTUtility.copyAmount(1, sticks) });
    }

    /** NEI: one page per crop, crop sticks into seed bags. */
    public static void loadComplete() {
        if (!Config.cropsEnabled) return;
        RecipeMap<?> map = seedMap();
        ItemStack sticks = IC2Items.getItem("crop");
        if (map == null) return;
        ItemStack[] in = Config.sticksPerSeed > 0 && sticks != null
            ? new ItemStack[] { GTUtility.copyAmount(Config.sticksPerSeed, sticks) }
            : new ItemStack[0];
        int pages = 0;
        for (CropCard c : Crops.instance.getCrops()) {
            if (c == null || c == Crops.weed) continue;
            try {
                CropStats stats = new CropStats(1, 1, 1);
                GTValues.RA.stdBuilder()
                    .special(CropImprints.imprint(c, stats))
                    .itemInputs(in)
                    .itemOutputs(CropImprints.seeds(c, stats))
                    .duration(Config.seedTicks)
                    .eut(Config.seedEut)
                    .fake()
                    .addTo(map);
                pages++;
            } catch (RuntimeException e) {
                FluxEcho.LOG.debug("No NEI page for crop {}", c.name(), e);
            }
        }
        FluxEcho.LOG.info("Seed Echo: {} crops in NEI", pages);
    }
}
