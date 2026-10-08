package com.fluxecho.bees;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.fluxecho.Config;
import com.fluxecho.core.EchoPattern;
import com.fluxecho.core.EchoText;
import com.fluxecho.core.MTEEchoMachine;
import com.fluxecho.core.MachineId;
import com.fluxecho.logic.Karyotype;

import cpw.mods.fml.common.registry.GameRegistry;
import forestry.api.apiculture.EnumBeeType;
import forestry.api.apiculture.IBee;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;

/**
 * Larva Incubator (LV). An imprint (or a real bee, sapling or butterfly) sits in the special slot and is kept; it
 * grows more of its genes, whatever the climate.
 * <ul>
 * <li>Bees, fed honey drops: with no circuit or circuit 1 a princess (output 1) and her drones (output 2), with
 * circuit 2 a batch of drones. Pristine, analysed, pure-bred.</li>
 * <li>Trees, fed Forestry fertilizer: a batch of analysed saplings.</li>
 * <li>Butterflies, fed honey drops: analysed butterflies.</li>
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
            Config.dronesWithPrincess, Config.honeyPerDrones, Config.dronesPerBatch, Config.fertilizerPerSaplings,
            Config.saplingsPerBatch, Config.honeyPerButterfly, Config.butterfliesPerBatch };
    }

    static boolean isHoney(ItemStack s) {
        if (s == null) return false;
        int honey = OreDictionary.getOreID("dropHoney");
        for (int id : OreDictionary.getOreIDs(s)) if (id == honey) return true;
        return false;
    }

    static ItemStack fertilizer(int count) {
        return GameRegistry.findItemStack("Forestry", "fertilizerCompound", Math.max(1, count));
    }

    static ItemStack honey(int count) {
        return GameRegistry.findItemStack("Forestry", "honeyDrop", Math.max(1, count));
    }

    /** One batch: what it eats, how much, and what grows. */
    private static final class Batch {

        final boolean honey;
        final int food;
        final ItemStack[] outputs;

        Batch(boolean honey, int food, ItemStack... outputs) {
            this.honey = honey;
            this.food = food;
            this.outputs = outputs;
        }

        boolean eats(ItemStack s) {
            if (honey) return isHoney(s);
            ItemStack f = fertilizer(1);
            return s != null && f != null && s.isItemEqual(f);
        }

        ItemStack foodStack() {
            return honey ? honey(food) : fertilizer(food);
        }
    }

    /** The batch for the sample and circuit; null when its species is unknown here. */
    private Batch batch(String root, Map<String, String> genes) {
        if (Karyotype.TREES.equals(root)) {
            ItemStack saplings = BeeImprints.saplings(genes, Config.saplingsPerBatch);
            return saplings == null ? null : new Batch(false, Config.fertilizerPerSaplings, saplings, null);
        }
        if (Karyotype.BUTTERFLIES.equals(root)) {
            ItemStack b = BeeImprints.butterflies(genes, Config.butterfliesPerBatch);
            return b == null ? null : new Batch(true, Config.honeyPerButterfly, b, null);
        }
        IBee bee = BeeImprints.bee(genes, world());
        if (bee == null) return null;
        boolean drones = circuit() == CIRCUIT_DRONES;
        int droneCount = drones ? Config.dronesPerBatch : Config.dronesWithPrincess;
        ItemStack princess = drones ? null : BeeImprints.stack(bee, EnumBeeType.PRINCESS, 1);
        ItemStack droneStack = droneCount > 0 ? BeeImprints.stack(bee, EnumBeeType.DRONE, droneCount) : null;
        return new Batch(true, drones ? Config.honeyPerDrones : Config.honeyPerPrincess, princess, droneStack);
    }

    @Override
    protected int work() {
        ItemStack s = sample();
        Map<String, String> genes = BeeImprints.genes(s);
        if (genes == null) return idle("no_imprint");
        Batch b = batch(BeeImprints.root(s), genes);
        if (b == null) return idle("unknown_species");

        ItemStack in = input(0);
        if (b.food > 0 && (!b.eats(in) || in.stackSize < b.food)) return idle(b.honey ? "no_honey" : "no_fertilizer");
        if (!canOutput(b.outputs)) return blocked();

        if (b.food > 0) in.stackSize -= b.food;
        mOutputItems[0] = b.outputs[0];
        mOutputItems[1] = b.outputs[1];
        return start(Config.incubateEut, Config.incubateTicks);
    }

    /** One batch of what the sample grows, for the Echo ME Provider. */
    @Override
    public List<EchoPattern> echoPatterns() {
        ItemStack s = sample();
        Map<String, String> genes = Config.beesEnabled ? BeeImprints.genes(s) : null;
        Batch b = genes == null ? null : batch(BeeImprints.root(s), genes);
        if (b == null) return Collections.emptyList();
        ItemStack food = b.food > 0 ? b.foodStack() : null;
        ItemStack[] in = food == null ? new ItemStack[0] : new ItemStack[] { food };
        ItemStack[] out = b.outputs[1] == null ? new ItemStack[] { b.outputs[0] }
            : b.outputs[0] == null ? new ItemStack[] { b.outputs[1] } : b.outputs;
        return Collections.singletonList(new EchoPattern(in, out));
    }
}
