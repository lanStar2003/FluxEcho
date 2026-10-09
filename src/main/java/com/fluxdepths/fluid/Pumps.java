package com.fluxdepths.fluid;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.fluxdepths.Config;
import com.fluxdepths.FluxDepths;
import com.fluxdepths.RecipeGuard;
import com.fluxdepths.item.ItemImprint;
import com.fluxdepths.shard.Collectors;

import gregtech.GTMod;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.objects.GTUODimension;
import gregtech.api.objects.GTUOFluid;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBuilder;

/**
 * The three fluid pumps as GT machines on consecutive ids from {@link Config#pumpsFirstId}, their recipe map (GUI and
 * NEI only) and crafting recipes.
 */
public final class Pumps {

    private static final Map<PumpTier, ItemStack> STACKS = new EnumMap<>(PumpTier.class);
    private static RecipeMap<?> map;
    private static boolean tried;

    private Pumps() {}

    public static void register() {
        for (PumpTier tier : PumpTier.values()) {
            int id = Config.pumpsFirstId + tier.ordinal();
            IMetaTileEntity taken = GregTechAPI.METATILEENTITIES[id];
            if (taken != null) throw new IllegalStateException(
                "FluxDepths: GT machine id " + id
                    + " is already used by "
                    + taken.getClass()
                        .getName()
                    + ". Set fluid_pumps.firstMachineId in config/fluxdepths.cfg to the start of 3 free ids.");
            STACKS.put(tier, new MTEFluidPump(id, tier).getStackForm(1));
        }
        map();
    }

    public static ItemStack get(PumpTier tier) {
        ItemStack s = STACKS.get(tier);
        return s == null ? null : s.copy();
    }

    static synchronized RecipeMap<?> map() {
        if (!tried) {
            tried = true;
            try {
                map = RecipeMapBuilder.of("fluxdepths.recipe.fluid_pump")
                    .maxIO(1, 1, 0, 1)
                    .minInputs(0, 0)
                    .useSpecialSlot()
                    .slotOverlays(
                        (index, isFluid, isOutput, isSpecial) -> isSpecial ? GTUITextures.OVERLAY_SLOT_DATA_STICK
                            : null)
                    .progressBar(GTUITextures.PROGRESSBAR_ARROW)
                    .neiHandlerInfo(b -> {
                        ItemStack icon = get(PumpTier.LV);
                        return icon == null ? b : b.setDisplayStack(icon);
                    })
                    .build();
            } catch (Throwable t) {
                FluxDepths.LOG.error("GT refused the fluid pump recipe map; pumps work without NEI pages", t);
            }
        }
        return map;
    }

    /** One NEI page per underground fluid, with the average pristine amount, at the LV pump's rate. */
    public static void addNeiPages() {
        RecipeMap<?> m = map();
        if (m == null) return;
        Map<String, int[]> fluids = new LinkedHashMap<>();
        for (GTUODimension d : GTMod.proxy.mUndergroundOil.getDimensionList()
            .values()) {
            for (GTUOFluid f : d.getFluids()
                .values()) {
                Fluid fluid = f.getFluid();
                if (fluid == null || f.MaxAmount <= 0) continue;
                int avg = (f.MinAmount + f.MaxAmount) / 2;
                fluids.merge(fluid.getName(), new int[] { avg }, (a, b) -> a[0] >= b[0] ? a : b);
            }
        }
        double share = MTEFluidPump.share(PumpTier.LV);
        for (Map.Entry<String, int[]> e : fluids.entrySet()) {
            int litres = PumpRates.perCycle(e.getValue()[0], share);
            if (litres <= 0) continue;
            GTValues.RA.stdBuilder()
                .special(ItemImprint.fluidForNei(e.getKey(), e.getValue()[0]))
                .fluidOutputs(new FluidStack(FluidRegistry.getFluid(e.getKey()), litres))
                .duration(PumpTier.CYCLE)
                .eut(PumpTier.LV.energy)
                .fake()
                .addTo(m);
        }
    }

    /**
     * Each pump is built from the one below it (the LV one from a Flux Shard Collector), a hull, two circuits, two
     * electric pumps, two fluid pipes and a plate.
     */
    public static void registerCrafting() {
        try {
            recipe(
                PumpTier.LV,
                Collectors.main(),
                ItemList.Hull_LV,
                Materials.LV,
                ItemList.Electric_Pump_LV,
                Materials.Bronze);
            recipe(
                PumpTier.MV,
                get(PumpTier.LV),
                ItemList.Hull_MV,
                Materials.MV,
                ItemList.Electric_Pump_MV,
                Materials.Steel);
            recipe(
                PumpTier.HV,
                get(PumpTier.MV),
                ItemList.Hull_HV,
                Materials.HV,
                ItemList.Electric_Pump_HV,
                Materials.StainlessSteel);
        } catch (Throwable t) {
            FluxDepths.LOG.error("Failed to register the fluid pump recipes", t);
        }
    }

    private static void recipe(PumpTier tier, ItemStack below, ItemList hull, Materials circuit, ItemList pump,
        Materials pipe) {
        RecipeGuard.shaped(
            tier.name() + " Fluid Pump",
            get(tier),
            new Object[] { "CPC", "UHU", "TXT", 'C', OrePrefixes.circuit.get(circuit), 'P', OrePrefixes.plate.get(pipe),
                'U', pump.get(1), 'H', hull.get(1), 'T', OrePrefixes.pipeMedium.get(pipe), 'X', below });
    }
}
