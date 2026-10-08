package com.fluxecho.bees;

import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.fluxecho.Config;
import com.fluxecho.core.EchoText;
import com.fluxecho.core.MTEEchoMachine;
import com.fluxecho.core.MachineId;

import forestry.api.apiculture.EnumBeeType;
import forestry.api.apiculture.IBee;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;

/**
 * Larva Incubator (LV). An imprint (or a real bee) sits in the special slot and is kept; honey drops feed the larvae,
 * which grow into pristine, analysed, pure-bred bees of its genes, whatever the climate.
 * <ul>
 * <li>No circuit or circuit 1: a princess (output 1) and her drones (output 2).</li>
 * <li>Circuit 2: a batch of drones.</li>
 * </ul>
 * A princess left in the output blocks the next one, so princesses are never wasted.
 */
public class MTEBeeIncubator extends MTEEchoMachine {

    public static final int CIRCUIT_DRONES = 2;

    public MTEBeeIncubator(int id) {
        super(MachineId.BEE_INCUBATOR, id, 1, 2);
    }

    private MTEBeeIncubator(String name, String[] description, ITexture[][][] textures) {
        super(MachineId.BEE_INCUBATOR, name, description, textures, 1, 2);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity te) {
        return new MTEBeeIncubator(mName, mDescriptionArray, mTextures);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return BeeNei.incubatorMap();
    }

    @Override
    public boolean allowSelectCircuit() {
        return true;
    }

    @Override
    protected boolean moduleEnabled() {
        return Config.beesEnabled;
    }

    @Override
    protected Object[] tooltipArgs() {
        return new Object[] { EchoText.seconds(Config.incubateTicks), Config.incubateEut, Config.honeyPerPrincess,
            Config.dronesWithPrincess, Config.honeyPerDrones, Config.dronesPerBatch };
    }

    static boolean isHoney(ItemStack s) {
        if (s == null) return false;
        int honey = OreDictionary.getOreID("dropHoney");
        for (int id : OreDictionary.getOreIDs(s)) if (id == honey) return true;
        return false;
    }

    @Override
    protected int work() {
        Map<String, String> genes = BeeImprints.genes(sample());
        if (genes == null) return idle("no_imprint");
        IBee bee = BeeImprints.bee(genes, world());
        if (bee == null) return idle("unknown_species");

        boolean drones = circuit() == CIRCUIT_DRONES;
        int honey = drones ? Config.honeyPerDrones : Config.honeyPerPrincess;
        ItemStack in = input(0);
        if (honey > 0 && (!isHoney(in) || in.stackSize < honey)) return idle("no_honey");

        ItemStack princess = drones ? null : BeeImprints.stack(bee, EnumBeeType.PRINCESS, 1);
        int droneCount = drones ? Config.dronesPerBatch : Config.dronesWithPrincess;
        ItemStack droneStack = droneCount > 0 ? BeeImprints.stack(bee, EnumBeeType.DRONE, droneCount) : null;
        if (!canOutput(princess, droneStack)) return blocked();

        if (honey > 0) in.stackSize -= honey;
        mOutputItems[0] = princess;
        mOutputItems[1] = droneStack;
        return start(Config.incubateEut, Config.incubateTicks);
    }
}
