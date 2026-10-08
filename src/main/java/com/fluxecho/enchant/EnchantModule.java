package com.fluxecho.enchant;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.codex.Categories;
import com.fluxecho.core.EchoRecipeMaps;
import com.fluxecho.core.MachineId;
import com.fluxecho.core.Machines;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTModHandler;

/** Enchanting (vanilla, always loaded). The machine stays registered when the module is switched off. */
public final class EnchantModule {

    private EnchantModule() {}

    static RecipeMap<?> map() {
        return EchoRecipeMaps.map(
            MachineId.ENCHANT_ECHO,
            b -> b.maxIO(2, 1, 0, 0)
                .minInputs(0, 0)
                .useSpecialSlot()
                .slotOverlays(
                    (index, fluid, output, special) -> special ? GTUITextures.OVERLAY_SLOT_PAGE_PRINTED
                        : !output && !fluid && index == 0 ? GTUITextures.OVERLAY_SLOT_PAGE_BLANK : null));
    }

    public static void machines() {
        Machines.put(MachineId.ENCHANT_ECHO, new MTEEnchantEcho(Machines.claim(MachineId.ENCHANT_ECHO)));
        map();
    }

    /** The recipe takes an enchanting table and bookshelves: the machine repeats what you did at the table. */
    public static void postInit() {
        Categories.register(
            new Categories.Category(Categories.ENCHANT, EnchantBooks::icon, EnchantBooks::name, EnchantBooks::keyOf));
        if (!Config.enchantEnabled || !Config.defaultRecipes) return;
        try {
            GTModHandler.addCraftingRecipe(
                Machines.get(MachineId.ENCHANT_ECHO),
                GTModHandler.RecipeBits.NOT_REMOVABLE,
                new Object[] { "PEP", "CHC", "BTB", 'P', OrePrefixes.plate.get(Materials.Steel), 'E',
                    new ItemStack(Items.ender_pearl), 'C', OrePrefixes.circuit.get(Materials.MV), 'H',
                    ItemList.Hull_MV.get(1), 'B', new ItemStack(Blocks.bookshelf), 'T',
                    new ItemStack(Blocks.enchanting_table) });
        } catch (Throwable t) {
            FluxEcho.LOG.error("Failed to register the Enchant Echo recipe", t);
        }
    }
}
