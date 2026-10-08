package com.fluxecho.bees;

import java.util.List;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.fluxecho.Config;
import com.fluxecho.core.EchoText;
import com.fluxecho.core.MTEEchoMachine;
import com.fluxecho.core.MachineId;
import com.fluxecho.logic.Chromosomes;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;

/**
 * Bee Imprinter (LV). A real bee (or an imprint) sits in the special slot and is never harmed.
 * <ul>
 * <li>Paper in the input: a full imprint of the sample.</li>
 * <li>An imprint in the input and a programmed circuit: that imprint with the sample's genes for the circuit (see
 * {@link Chromosomes#forCircuit}); circuit 13 carries over everything that decides where a bee works.</li>
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

    @Override
    protected int work() {
        Map<String, String> sample = BeeImprints.genes(sample());
        if (sample == null) return idle("no_bee_sample");
        ItemStack in = input(0);
        if (in == null) return idle("no_paper");

        ItemStack out;
        if (in.getItem() == Items.paper) {
            out = BeeImprints.imprint(sample);
        } else if (BeeImprints.isImprint(in)) {
            Map<String, String> base = BeeImprints.read(in);
            List<String> genes = Chromosomes.forCircuit(circuit());
            if (base == null) return idle("no_paper");
            if (genes.isEmpty()) return idle("need_circuit");
            out = BeeImprints.imprint(Chromosomes.transfer(base, sample, genes));
        } else {
            return idle("no_paper");
        }
        if (!canOutput(out)) return blocked();

        in.stackSize--;
        mOutputItems[0] = out;
        return start(Config.imprintEut, Config.imprintTicks);
    }
}
