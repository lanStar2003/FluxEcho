package com.fluxecho.matter;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;

/**
 * The flux materials of the anchoring era (blueprint 3.6): flux grit, which only the Flux Shard Collector gives off
 * while it condenses ores; flux crystal, grown from it in GT's autoclave; echo crystal, which only the Flux Nexus
 * makes. Each is in the ore dictionary, so recipes and other machines can take them by name.
 */
public final class MatterModule {

    public static final String GRIT = "dustFluxGrit", CRYSTAL = "gemFluxCrystal", ECHO_CRYSTAL = "gemEchoCrystal";

    public static Item grit, crystal, echoCrystal;

    private MatterModule() {}

    public static void preInit() {
        grit = new ItemMatter("flux_grit");
        GameRegistry.registerItem(grit, "flux_grit");
        crystal = new ItemMatter("flux_crystal");
        GameRegistry.registerItem(crystal, "flux_crystal");
        echoCrystal = new ItemEchoCrystal();
        GameRegistry.registerItem(echoCrystal, "echo_crystal");
        OreDictionary.registerOre(GRIT, grit);
        OreDictionary.registerOre(CRYSTAL, crystal);
        // any entry: the ore dictionary does not look at NBT
        OreDictionary.registerOre(ECHO_CRYSTAL, new ItemStack(echoCrystal, 1, 0));
    }

    /** Flux crystal: grit, ender pearl dust and distilled water in GT's autoclave (MV). */
    public static void postInit() {
        if (!Config.defaultRecipes) return;
        try {
            GTValues.RA.stdBuilder()
                .itemInputs(
                    new ItemStack(grit, Config.crystalGrit),
                    GTOreDictUnificator.get(OrePrefixes.dust, Materials.EnderPearl, 1))
                .fluidInputs(GTModHandler.getDistilledWater(250))
                .itemOutputs(new ItemStack(crystal))
                .duration(600)
                .eut(120)
                .addTo(RecipeMaps.autoclaveRecipes);
        } catch (Throwable t) {
            FluxEcho.LOG.error("Failed to register the flux crystal recipe", t);
        }
    }

    public static ItemStack grit(int n) {
        return new ItemStack(grit, n);
    }

    public static ItemStack crystal(int n) {
        return new ItemStack(crystal, n);
    }
}
