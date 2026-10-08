package com.fluxecho.mobs;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.Mods;
import com.fluxecho.codex.Categories;
import com.fluxecho.core.EchoRecipeMaps;
import com.fluxecho.core.MachineId;
import com.fluxecho.core.Machines;

import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTModHandler;

/**
 * Prey (needs MobsInfo for the drop tables). Only called when MobsInfo is loaded. The imprint and the machine are
 * registered even when the module is switched off; recipes are not, and the machine stops.
 */
public final class MobModule {

    public static Item imprint;

    private MobModule() {}

    static RecipeMap<?> map() {
        return EchoRecipeMaps.map(
            MachineId.MOB_ECHO,
            b -> b.maxIO(1, 4, 0, 0)
                .minInputs(0, 0)
                .useSpecialSlot()
                .slotOverlays(
                    (index, fluid, output, special) -> special ? GTUITextures.OVERLAY_SLOT_PAGE_PRINTED
                        : !output && !fluid ? GTUITextures.OVERLAY_SLOT_HAMMER : null));
    }

    public static void preInit() {
        imprint = new ItemMobImprint();
        GameRegistry.registerItem(imprint, "mob_imprint");
        MinecraftForge.EVENT_BUS.register(new MobEvents());
        if (Mods.waila) FMLInterModComms.sendMessage("Waila", "register", "com.fluxecho.mobs.MobWaila.register");
    }

    public static void machines() {
        Machines.put(MachineId.MOB_ECHO, new MTEMobEcho(Machines.claim(MachineId.MOB_ECHO)));
        map();
    }

    public static void postInit() {
        Categories.register(
            new Categories.Category(Categories.MOB, MobImprints::egg, MobImprints::displayName, MobImprints::keyOf));
        if (!Config.mobsEnabled || !Config.defaultRecipes) return;
        try {
            GTModHandler.addShapelessCraftingRecipe(
                new ItemStack(imprint, 4),
                GTModHandler.RecipeBits.NOT_REMOVABLE,
                new Object[] { new ItemStack(Items.paper), new ItemStack(Items.paper), new ItemStack(Items.paper),
                    new ItemStack(Items.paper), new ItemStack(Items.rotten_flesh) });
            GTModHandler.addCraftingRecipe(
                Machines.get(MachineId.MOB_ECHO),
                GTModHandler.RecipeBits.NOT_REMOVABLE,
                new Object[] { "PEP", "CHC", "ASA", 'P', OrePrefixes.plate.get(Materials.Steel), 'E',
                    new ItemStack(Items.ender_pearl), 'C', OrePrefixes.circuit.get(Materials.LV), 'H',
                    ItemList.Hull_LV.get(1), 'A', ItemList.Robot_Arm_LV.get(1), 'S', new ItemStack(Items.iron_sword) });
        } catch (Throwable t) {
            FluxEcho.LOG.error("Failed to register the prey recipes", t);
        }
    }
}
