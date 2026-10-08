package com.fluxecho.thaumcraft;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.fluxecho.FluxEcho;
import com.fluxecho.core.MachineId;
import com.fluxecho.core.Machines;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTModHandler;
import thaumcraft.common.config.ConfigBlocks;
import thaumcraft.common.config.ConfigItems;

/**
 * Crafting table recipes, each with the Thaumcraft device whose job the machine repeats, as proof the job was done
 * once by hand: the Essentia Echo an alchemical furnace and a warded jar, the Vis Charger a wand recharge pedestal,
 * the Insight Echo a table, scribing tools and a thaumometer (the research table is made from them in the world, it
 * has no item), the Crucible Echo a crucible.
 */
final class TCCrafting {

    private static final long BITS = GTModHandler.RecipeBits.NOT_REMOVABLE;

    private TCCrafting() {}

    static void register() {
        ItemStack pearl = new ItemStack(Items.ender_pearl);
        ItemStack furnace = new ItemStack(ConfigBlocks.blockStoneDevice, 1, 0);
        ItemStack pedestal = new ItemStack(ConfigBlocks.blockStoneDevice, 1, 5);
        ItemStack jar = new ItemStack(ConfigBlocks.blockJar, 1, 0);
        ItemStack tube = new ItemStack(ConfigBlocks.blockTube, 1, 0);
        ItemStack balanced = new ItemStack(ConfigItems.itemShard, 1, 6);
        Object thaumium = OrePrefixes.plate.get(Materials.Thaumium);

        GTModHandler.addCraftingRecipe(
            Machines.get(MachineId.ESSENTIA_ECHO),
            BITS,
            new Object[] { "JEJ", "CHC", "PFP", 'J', jar, 'E', pearl, 'C', OrePrefixes.circuit.get(Materials.MV), 'H',
                ItemList.Hull_MV.get(1), 'P', ItemList.Electric_Pump_MV.get(1), 'F', furnace });
        GTModHandler.addCraftingRecipe(
            new ItemStack(TCModule.outlet),
            BITS,
            new Object[] { "PTP", "TJT", "PTP", 'P', thaumium, 'T', tube, 'J', jar });
        GTModHandler.addCraftingRecipe(
            Machines.get(MachineId.VIS_CHARGER),
            BITS,
            new Object[] { "PEP", "CHC", "SWS", 'P', thaumium, 'E', pearl, 'C', OrePrefixes.circuit.get(Materials.MV),
                'H', ItemList.Hull_MV.get(1), 'S', balanced, 'W', pedestal });
        GTModHandler.addCraftingRecipe(
            Machines.get(MachineId.INSIGHT_ECHO),
            BITS,
            new Object[] { "PEP", "CHC", "IMT", 'P', thaumium, 'E', pearl, 'C', OrePrefixes.circuit.get(Materials.LV),
                'H', ItemList.Hull_LV.get(1), 'I', new ItemStack(ConfigItems.itemInkwell, 1, 0), 'M',
                new ItemStack(ConfigItems.itemThaumometer), 'T', new ItemStack(ConfigBlocks.blockTable, 1, 0) });
        GTModHandler.addCraftingRecipe(
            Machines.get(MachineId.CRUCIBLE_ECHO),
            BITS,
            new Object[] { "PEP", "CHC", "UXU", 'P', thaumium, 'E', pearl, 'C', OrePrefixes.circuit.get(Materials.MV),
                'H', ItemList.Hull_MV.get(1), 'U', ItemList.Electric_Pump_MV.get(1), 'X',
                new ItemStack(ConfigBlocks.blockMetalDevice, 1, 0) });
        FluxEcho.LOG.info("Thaumcraft machine recipes registered");
    }
}
