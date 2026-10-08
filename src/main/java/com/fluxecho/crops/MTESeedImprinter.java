package com.fluxecho.crops;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.fluxecho.Config;
import com.fluxecho.codex.Categories;
import com.fluxecho.core.EchoText;
import com.fluxecho.core.MTEEchoMachine;
import com.fluxecho.core.MachineId;
import com.fluxecho.logic.CropStats;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import ic2.api.crops.CropCard;

/**
 * Seed Imprinter (LV). A fully scanned seed bag (or a crop imprint) sits in the special slot and is kept.
 * <ul>
 * <li>Paper in the input: a crop imprint of the sample, crop and stats.</li>
 * <li>A crop imprint in the input and a programmed circuit: that imprint with the sample's growth (1), gain (2),
 * resistance (3) or all three (4); its crop stays.</li>
 * </ul>
 */
public class MTESeedImprinter extends MTEEchoMachine {

    public MTESeedImprinter(int id) {
        super(MachineId.SEED_IMPRINTER, id, 1, 1);
    }

    private MTESeedImprinter(String name, String[] description, ITexture[][][] textures) {
        super(MachineId.SEED_IMPRINTER, name, description, textures, 1, 1);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity te) {
        return new MTESeedImprinter(mName, mDescriptionArray, mTextures);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return CropModule.imprinterMap();
    }

    @Override
    public boolean allowSelectCircuit() {
        return true;
    }

    @Override
    protected boolean moduleEnabled() {
        return Config.cropsEnabled;
    }

    @Override
    protected Object[] tooltipArgs() {
        return new Object[] { EchoText.seconds(Config.cropImprintTicks), Config.cropImprintEut };
    }

    @Override
    protected int work() {
        ItemStack sample = sample();
        CropCard crop = CropImprints.crop(sample);
        if (crop == null) return idle("no_seed_sample");
        if (!CropImprints.known(sample)) return idle("seed_not_scanned");
        CropStats stats = CropImprints.stats(sample);
        ItemStack in = input(0);
        if (in == null) return idle("no_paper_crop");

        ItemStack out;
        if (in.getItem() == Items.paper) {
            out = CropImprints.imprint(crop, stats);
        } else if (CropImprints.isImprint(in) && CropImprints.crop(in) != null) {
            if (!CropStats.copies(circuit())) return idle("need_circuit_crop");
            out = CropImprints.imprint(
                CropImprints.crop(in),
                CropImprints.stats(in)
                    .with(stats, circuit()));
        } else {
            return idle("no_paper_crop");
        }
        if (!canOutput(out)) return blocked();

        in.stackSize--;
        mOutputItems[0] = out;
        if (CropImprints.isSeedBag(sample)) remember(Categories.CROP, CropImprints.key(crop));
        return start(Config.cropImprintEut, Config.cropImprintTicks);
    }
}
