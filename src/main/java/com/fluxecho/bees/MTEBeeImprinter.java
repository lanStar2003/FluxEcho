package com.fluxecho.bees;

import java.util.List;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.fluxecho.Config;
import com.fluxecho.codex.Categories;
import com.fluxecho.core.EchoText;
import com.fluxecho.core.MTEEchoMachine;
import com.fluxecho.core.MachineId;
import com.fluxecho.logic.Chromosomes;
import com.fluxecho.logic.Karyotype;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;

/**
 * Bee Imprinter (LV). A real bee, sapling (or pollen) or butterfly, or an imprint, sits in the special slot and is
 * never harmed.
 * <ul>
 * <li>Paper in the input: a full imprint of the sample.</li>
 * <li>An imprint of the same kind in the input and a programmed circuit: that imprint with the sample's genes for the
 * circuit (see {@link Karyotype#forCircuit}); on bees circuit 13 carries over everything that decides where a bee
 * works.</li>
 * </ul>
 */
public class MTEBeeImprinter extends MTEEchoMachine {

    public MTEBeeImprinter(int id) {
        super(MachineId.BEE_IMPRINTER, id, 1, 1);
    }

    private MTEBeeImprinter(String name, String[] description, ITexture[][][] textures) {
        super(MachineId.BEE_IMPRINTER, name, description, textures, 1, 1);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity te) {
        return new MTEBeeImprinter(mName, mDescriptionArray, mTextures);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return BeeNei.imprinterMap();
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
        return new Object[] { EchoText.seconds(Config.imprintTicks), Config.imprintEut };
    }

    /** The ledger category of a root. */
    static String category(String root) {
        if (Karyotype.TREES.equals(root)) return Categories.TREE;
        if (Karyotype.BUTTERFLIES.equals(root)) return Categories.BUTTERFLY;
        return Categories.BEE;
    }

    @Override
    protected int work() {
        ItemStack sampleStack = sample();
        Map<String, String> sample = BeeImprints.genes(sampleStack);
        String root = BeeImprints.root(sampleStack);
        Karyotype k = Karyotype.of(root);
        if (sample == null || k == null) return idle("no_bee_sample");
        ItemStack in = input(0);
        if (in == null) return idle("no_paper");

        ItemStack out;
        if (in.getItem() == Items.paper) {
            out = BeeImprints.imprint(root, sample);
        } else if (BeeImprints.isImprint(in)) {
            Map<String, String> base = BeeImprints.read(in);
            if (base == null) return idle("no_paper");
            if (!root.equals(BeeImprints.root(in))) return idle("imprint_kind_mismatch");
            List<String> genes = k.forCircuit(circuit());
            if (genes.isEmpty()) return idle("need_circuit");
            out = BeeImprints.imprint(root, Chromosomes.transfer(base, sample, genes));
        } else {
            return idle("no_paper");
        }
        if (!canOutput(out)) return blocked();

        in.stackSize--;
        mOutputItems[0] = out;
        if (!BeeImprints.isImprint(sampleStack)) remember(category(root), sample.get(Chromosomes.SPECIES));
        return start(Config.imprintEut, Config.imprintTicks);
    }
}
