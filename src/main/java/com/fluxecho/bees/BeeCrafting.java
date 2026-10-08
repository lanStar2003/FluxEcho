package com.fluxecho.bees;

import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.fluxecho.FluxEcho;
import com.fluxecho.core.MachineId;
import com.fluxecho.core.Machines;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTModHandler;

/**
 * Crafting table recipes. Each machine takes the vanilla tool of the job it repeats, as proof the job was done once
 * by hand: the imprinter a portable Beealyzer, the incubator an Apiary. The ender pearl is the link to the flux layer,
 * as in FluxLite and FluxDepths.
 */
final class BeeCrafting {

    private static final long BITS = GTModHandler.RecipeBits.NOT_REMOVABLE;

    private BeeCrafting() {}

    static void register() {
        ItemStack beealyzer = GameRegistry.findItemStack("Forestry", "beealyzer", 1);
        Block apiculture = GameRegistry.findBlock("Forestry", "apiculture");
        ItemStack apiary = apiculture == null ? null : new ItemStack(apiculture, 1, 0);
        ItemStack pearl = new ItemStack(Items.ender_pearl);

        if (beealyzer == null) FluxEcho.LOG.warn("No Forestry beealyzer: the Bee Imprinter has no recipe");
        else GTModHandler.addCraftingRecipe(
            Machines.get(MachineId.BEE_IMPRINTER),
            BITS,
            new Object[] { "PEP", "CHC", "RBR", 'P', OrePrefixes.plate.get(Materials.Steel), 'E', pearl, 'C',
                OrePrefixes.circuit.get(Materials.LV), 'H', ItemList.Hull_LV.get(1), 'R', ItemList.Robot_Arm_LV.get(1),
                'B', beealyzer });

        if (apiary == null) FluxEcho.LOG.warn("No Forestry apiary: the Larva Incubator has no recipe");
        else GTModHandler.addCraftingRecipe(
            Machines.get(MachineId.BEE_INCUBATOR),
            BITS,
            new Object[] { "PEP", "CHC", "UAU", 'P', OrePrefixes.plate.get(Materials.Steel), 'E', pearl, 'C',
                OrePrefixes.circuit.get(Materials.LV), 'H', ItemList.Hull_LV.get(1), 'U',
                ItemList.Electric_Pump_LV.get(1), 'A', apiary });
    }
}
