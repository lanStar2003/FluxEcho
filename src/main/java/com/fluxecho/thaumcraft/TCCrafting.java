package com.fluxecho.thaumcraft;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.fluxecho.FluxEcho;
import com.fluxecho.core.EchoRecipes;
import com.fluxecho.core.MachineId;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTModHandler;
import thaumcraft.common.config.ConfigBlocks;
import thaumcraft.common.config.ConfigItems;

/**
 * Crafting table recipes, each with the Thaumcraft device whose job the machine repeats, as proof the job was done
 * once by hand: the Essentia Echo an alchemical furnace and a warded jar, the Flux Vis Pedestal a wand recharge
 * pedestal,
 * the Insight Echo a table, scribing tools and a thaumometer (the research table is made from them in the world, it
 * has no item), the Crucible Echo a crucible, the Infusion Echo a runic matrix and arcane pedestals.
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

        EchoRecipes.machine(
            MachineId.ESSENTIA_ECHO,
            new Object[] { "JEJ", "CHC", "PFP", 'J', jar, 'E', pearl, 'C', OrePrefixes.circuit.get(Materials.MV), 'H',
                ItemList.Hull_MV.get(1), 'P', ItemList.Electric_Pump_MV.get(1), 'F', furnace });
        EchoRecipes.shaped(
            "Essentia Outlet",
            new ItemStack(TCModule.outlet),
            new Object[] { "PTP", "TJT", "PTP", 'P', thaumium, 'T', tube, 'J', jar });
        Object mv = OrePrefixes.circuit.get(Materials.MV);
        EchoRecipes.shaped(
            "Flux Vis Pedestal",
            new ItemStack(TCModule.visPedestal),
            new Object[] { "TET", "CWC", "AHA", 'T', thaumium, 'E', pearl, 'C', mv, 'W', pedestal, 'A',
                OrePrefixes.plate.get(Materials.Aluminium), 'H', ItemList.Hull_MV.get(1) });
        EchoRecipes.shaped(
            "Extraction Module",
            ItemVisModule.stack(ItemVisModule.Kind.EXTRACTION, 1),
            new Object[] { "TST", "SCS", "TST", 'T', thaumium, 'S', balanced, 'C', mv });
        EchoRecipes.shaped(
            "Wireless Charging Module",
            ItemVisModule.stack(ItemVisModule.Kind.WIRELESS, 1),
            new Object[] { "TRT", "CEC", "TRT", 'T', thaumium, 'R', ItemList.Emitter_MV.get(1), 'E', pearl, 'C', mv });
        EchoRecipes.shaped(
            "Flux Link Module",
            ItemVisModule.stack(ItemVisModule.Kind.LINK, 1),
            new Object[] { "TCT", "RES", "TCT", 'T', thaumium, 'C', mv, 'R', ItemList.Emitter_MV.get(1), 'E',
                new ItemStack(Items.ender_eye), 'S', ItemList.Sensor_MV.get(1) });
        EchoRecipes.machine(
            MachineId.INSIGHT_ECHO,
            new Object[] { "PEP", "CHC", "IMT", 'P', thaumium, 'E', pearl, 'C', OrePrefixes.circuit.get(Materials.LV),
                'H', ItemList.Hull_LV.get(1), 'I', new ItemStack(ConfigItems.itemInkwell, 1, 0), 'M',
                new ItemStack(ConfigItems.itemThaumometer), 'T', new ItemStack(ConfigBlocks.blockTable, 1, 0) });
        EchoRecipes.machine(
            MachineId.CRUCIBLE_ECHO,
            new Object[] { "PEP", "CHC", "UXU", 'P', thaumium, 'E', pearl, 'C', OrePrefixes.circuit.get(Materials.MV),
                'H', ItemList.Hull_MV.get(1), 'U', ItemList.Electric_Pump_MV.get(1), 'X',
                new ItemStack(ConfigBlocks.blockMetalDevice, 1, 0) });
        EchoRecipes.machine(
            MachineId.INFUSION_ECHO,
            new Object[] { "PEP", "CHC", "AMA", 'P', thaumium, 'E', pearl, 'C', OrePrefixes.circuit.get(Materials.MV),
                'H', ItemList.Hull_MV.get(1), 'A', new ItemStack(ConfigBlocks.blockStoneDevice, 1, 1), 'M',
                new ItemStack(ConfigBlocks.blockStoneDevice, 1, 2) });
        FluxEcho.LOG.info("Thaumcraft machine recipes registered");
    }
}
