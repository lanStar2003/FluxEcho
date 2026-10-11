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
import gregtech.api.util.GTRecipeConstants;

/**
 * The flux materials (blueprint 3.6): flux grit, which only the Flux Shard Collector gives off while it condenses ores;
 * flux crystal, grown from it in GT's autoclave; echo crystal, which only the Flux Nexus makes; and with phase II
 * (0.12.0, CITY.md §5.1, in their minimal form: one recipe each, used by the phase's research and module cores) phase
 * alloy, smelted in GT's electric blast furnace, and resonant glass, which the nexus manifests from it
 * ({@code nexus.Manifests}). Each is in the ore dictionary, so recipes and other machines can take them by name.
 */
public final class MatterModule {

    public static final String GRIT = "dustFluxGrit", CRYSTAL = "gemFluxCrystal", ECHO_CRYSTAL = "gemEchoCrystal";
    /** The phase-II materials' ore names (0.12.0): BLUEPRINT 3.6's {@code ingotPhaseAlloy}, and resonant glass. */
    public static final String PHASE_ALLOY = "ingotPhaseAlloy", RESONANT_GLASS = "glassResonant";
    /** Phase alloy in the blast furnace: EV power, the coil heat it needs (titanium's and a little more), its time. */
    public static final int PHASE_ALLOY_EUT = 1920, PHASE_ALLOY_HEAT = 2700, PHASE_ALLOY_TICKS = 600;

    public static Item grit, crystal, echoCrystal, phaseAlloy, resonantGlass;

    private MatterModule() {}

    public static void preInit() {
        grit = new ItemMatter("flux_grit");
        GameRegistry.registerItem(grit, "flux_grit");
        crystal = new ItemMatter("flux_crystal");
        GameRegistry.registerItem(crystal, "flux_crystal");
        echoCrystal = new ItemEchoCrystal();
        GameRegistry.registerItem(echoCrystal, "echo_crystal");
        phaseAlloy = new ItemMatter("phase_alloy");
        GameRegistry.registerItem(phaseAlloy, "phase_alloy");
        resonantGlass = new ItemMatter("resonant_glass");
        GameRegistry.registerItem(resonantGlass, "resonant_glass");
        OreDictionary.registerOre(GRIT, grit);
        OreDictionary.registerOre(CRYSTAL, crystal);
        // any entry: the ore dictionary does not look at NBT
        OreDictionary.registerOre(ECHO_CRYSTAL, new ItemStack(echoCrystal, 1, 0));
        OreDictionary.registerOre(PHASE_ALLOY, phaseAlloy);
        OreDictionary.registerOre(RESONANT_GLASS, resonantGlass);
    }

    /**
     * Flux crystal: grit, ender pearl dust and distilled water in GT's autoclave (MV). Phase alloy (0.12.0): two flux
     * crystals, titanium dust and an echo crystal (any, whatever it holds) in GT's electric blast furnace at EV.
     */
    public static void postInit() {
        if (!Config.defaultRecipes) return;
        try {
            GTValues.RA.stdBuilder()
                .itemInputs(
                    new ItemStack(crystal, 2),
                    GTOreDictUnificator.get(OrePrefixes.dust, Materials.Titanium, 1),
                    new ItemStack(echoCrystal, 1, 0))
                .itemOutputs(new ItemStack(phaseAlloy))
                .duration(PHASE_ALLOY_TICKS)
                .eut(PHASE_ALLOY_EUT)
                .metadata(GTRecipeConstants.COIL_HEAT, PHASE_ALLOY_HEAT)
                .addTo(RecipeMaps.blastFurnaceRecipes);
        } catch (Throwable t) {
            FluxEcho.LOG.error("Failed to register the phase alloy recipe", t);
        }
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

    public static ItemStack phaseAlloy(int n) {
        return new ItemStack(phaseAlloy, n);
    }

    public static ItemStack resonantGlass(int n) {
        return new ItemStack(resonantGlass, n);
    }
}
