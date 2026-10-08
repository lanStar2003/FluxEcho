package com.fluxecho.ae;

import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTModHandler;

/**
 * Applied Energistics 2: the Echo ME Provider. Only called when AE2 is loaded. The block is registered even when it
 * is switched off; then it offers nothing.
 */
public final class AEModule {

    public static Block provider;

    private AEModule() {}

    public static void preInit() {
        provider = new BlockEchoProvider();
        GameRegistry.registerBlock(provider, "echo_provider");
        GameRegistry.registerTileEntity(TileEchoProvider.class, "fluxecho:echo_provider");
    }

    /** An ME interface and a molecular assembler: what you would otherwise wire up for every echo machine. */
    public static void postInit() {
        if (!Config.aeEnabled || !Config.defaultRecipes) return;
        try {
            ItemStack iface = GameRegistry.findItemStack("appliedenergistics2", "tile.BlockInterface", 1);
            ItemStack assembler = GameRegistry.findItemStack("appliedenergistics2", "tile.BlockMolecularAssembler", 1);
            if (iface == null || assembler == null) {
                FluxEcho.LOG.warn("No ME interface or molecular assembler found: the Echo ME Provider has no recipe");
                return;
            }
            GTModHandler.addCraftingRecipe(
                new ItemStack(provider),
                GTModHandler.RecipeBits.NOT_REMOVABLE,
                new Object[] { "PEP", "IHA", "PCP", 'P', OrePrefixes.plate.get(Materials.Aluminium), 'E',
                    new ItemStack(Items.ender_pearl), 'I', iface, 'H', ItemList.Hull_MV.get(1), 'A', assembler, 'C',
                    OrePrefixes.circuit.get(Materials.MV) });
        } catch (Throwable t) {
            FluxEcho.LOG.error("Failed to register the Echo ME Provider recipe", t);
        }
    }
}
