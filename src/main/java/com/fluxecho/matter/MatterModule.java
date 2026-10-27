package com.fluxecho.matter;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.fluxecho.FluxEcho;
import com.fluxecho.config.GeneralConfig;
import com.fluxecho.config.NexusConfig;
import com.fluxecho.core.EchoRecipes;

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
 * ({@code nexus.Manifests}); with phase III (0.15.0, minimal as well) memory filament, phase alloy drawn into a fine
 * wire in GT's wiremill and coated with an echo crystal at the manifestation table; with phase IV (0.16.0, minimal as
 * well) manifest matter, a little of the flux layer the nexus makes into matter at the table, and the anti-gravity
 * lattice it makes of manifest matter and resonant glass, the floating foundations' material. Each is in the ore
 * dictionary, so recipes and other machines can take them by name.
 */
public final class MatterModule {

    public static final String GRIT = "dustFluxGrit", CRYSTAL = "gemFluxCrystal", ECHO_CRYSTAL = "gemEchoCrystal";
    /** The phase-II materials' ore names (0.12.0): BLUEPRINT 3.6's {@code ingotPhaseAlloy}, and resonant glass. */
    public static final String PHASE_ALLOY = "ingotPhaseAlloy", RESONANT_GLASS = "glassResonant";
    /** Phase alloy in the blast furnace: EV power, the coil heat it needs (titanium's and a little more), its time. */
    public static final int PHASE_ALLOY_EUT = 1920, PHASE_ALLOY_HEAT = 2700, PHASE_ALLOY_TICKS = 600;

    /**
     * The phase-III material's ore names (0.15.0, BLUEPRINT 3.6): memory filament, and the bare phase alloy wire the
     * wiremill draws for it (GT's own name for a fine wire of a material).
     */
    public static final String MEMORY_FILAMENT = "wireMemoryFilament", PHASE_ALLOY_WIRE = "wireFinePhaseAlloy";
    /**
     * Phase alloy in the wiremill (0.15.0): IV power, its time, and the wires an ingot gives (GT's wiremill draws eight
     * fine wires from an ingot); by hand, if GT's wiremill cannot take the recipe, a wire cutter gets two.
     */
    public static final int WIRE_EUT = 7680, WIRE_TICKS = 200, WIRES_PER_INGOT = 8, WIRES_BY_HAND = 2;

    /**
     * The phase-IV materials' ore names (0.16.0, BLUEPRINT 3.6): manifest matter, as an ingot (black-gold liquid metal
     * the nexus holds in shape), and the anti-gravity lattice, for which GT has no prefix that fits (a lattice is
     * neither a plate nor a gem, nor GT's frame box), so it takes one of its own the way resonant glass took
     * {@code glass}.
     */
    public static final String MANIFEST_MATTER = "ingotManifestMatter", ANTI_GRAVITY_LATTICE = "latticeAntiGravity";
    /**
     * Manifest matter at the table (0.16.0): the extra power it draws on top of the table's share while it runs (LuV,
     * by the tier's recipe power as EV's 1920 and IV's 7680 above), its time, how much one run makes, and the lowest
     * nexus phase that may make it (BLUEPRINT 3.6: "from phase III of the nexus on").
     */
    public static final int MANIFEST_MATTER_EUT = 30720, MANIFEST_MATTER_TICKS = 1200, MANIFEST_MATTER_YIELD = 4,
        MANIFEST_MATTER_PHASE = 3;
    /** The anti-gravity lattice at the table (0.16.0): its time and how many one run makes. */
    public static final int LATTICE_TICKS = 400, LATTICE_YIELD = 4;

    public static Item grit, crystal, echoCrystal, phaseAlloy, resonantGlass, phaseAlloyWire, memoryFilament,
        manifestMatter, antiGravityLattice;

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
        // memory filament (0.15.0) and the bare wire it is coated from
        phaseAlloyWire = new ItemMatter("phase_alloy_wire");
        GameRegistry.registerItem(phaseAlloyWire, "phase_alloy_wire");
        memoryFilament = new ItemMatter("memory_filament");
        GameRegistry.registerItem(memoryFilament, "memory_filament");
        OreDictionary.registerOre(PHASE_ALLOY_WIRE, phaseAlloyWire);
        OreDictionary.registerOre(MEMORY_FILAMENT, memoryFilament);
        // manifest matter and the anti-gravity lattice (0.16.0), both made at the manifestation table only
        manifestMatter = new ItemMatter("manifest_matter");
        GameRegistry.registerItem(manifestMatter, "manifest_matter");
        antiGravityLattice = new ItemMatter("anti_gravity_lattice");
        GameRegistry.registerItem(antiGravityLattice, "anti_gravity_lattice");
        OreDictionary.registerOre(MANIFEST_MATTER, manifestMatter);
        OreDictionary.registerOre(ANTI_GRAVITY_LATTICE, antiGravityLattice);
    }

    /**
     * Flux crystal: grit, ender pearl dust and distilled water in GT's autoclave (MV). Phase alloy (0.12.0): two flux
     * crystals, titanium dust and an echo crystal (any, whatever it holds) in GT's electric blast furnace at EV. Phase
     * alloy wire (0.15.0): an ingot drawn into eight in GT's wiremill at IV, the first half of memory filament's chain;
     * the nexus coats it with an echo crystal ({@code nexus.Manifests}).
     */
    public static void postInit() {
        if (!GeneralConfig.defaultRecipes) return;
        try {
            EchoRecipes.wiremill(
                "phase alloy wire",
                phaseAlloy(1),
                phaseAlloyWire(WIRES_PER_INGOT),
                WIRE_TICKS,
                WIRE_EUT,
                phaseAlloyWire(WIRES_BY_HAND),
                "Ix",
                'I',
                PHASE_ALLOY);
        } catch (Throwable t) {
            FluxEcho.LOG.error("Failed to register the phase alloy wire recipe", t);
        }
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
                    new ItemStack(grit, NexusConfig.crystalGrit),
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

    public static ItemStack phaseAlloyWire(int n) {
        return new ItemStack(phaseAlloyWire, n);
    }

    public static ItemStack memoryFilament(int n) {
        return new ItemStack(memoryFilament, n);
    }

    public static ItemStack manifestMatter(int n) {
        return new ItemStack(manifestMatter, n);
    }

    public static ItemStack antiGravityLattice(int n) {
        return new ItemStack(antiGravityLattice, n);
    }
}
