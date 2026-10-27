package com.fluxecho.crops;

import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.fluxecho.config.CropsConfig;
import com.fluxecho.core.EchoPattern;
import com.fluxecho.core.EchoText;
import com.fluxecho.core.MTEEchoMachine;
import com.fluxecho.core.MachineId;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import ic2.api.crops.CropCard;
import ic2.api.item.IC2Items;

/**
 * Seed Echo (LV). A crop imprint sits in the special slot and is kept; crop sticks in the input become fully scanned
 * seed bags of its crop with its stats, no cross-breeding needed.
 */
public class MTESeedEcho extends MTEEchoMachine {

    public MTESeedEcho(int id) {
        super(MachineId.SEED_ECHO, id, 1, 1);
    }

    private MTESeedEcho(String name, String[] description, ITexture[][][] textures) {
        super(MachineId.SEED_ECHO, name, description, textures, 1, 1);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity te) {
        return new MTESeedEcho(mName, mDescriptionArray, mTextures);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return CropModule.seedMap();
    }

    @Override
    protected boolean moduleEnabled() {
        return CropsConfig.cropsEnabled;
    }

    @Override
    protected Object[] tooltipArgs() {
        return new Object[] { CropsConfig.sticksPerSeed, EchoText.seconds(CropsConfig.seedTicks), CropsConfig.seedEut };
    }

    private ItemStack seeds() {
        ItemStack s = sample();
        CropCard crop = CropImprints.isImprint(s) ? CropImprints.crop(s) : null;
        return crop == null ? null : CropImprints.seeds(crop, CropImprints.stats(s));
    }

    @Override
    protected int work() {
        if (!CropImprints.isImprint(sample())) return idle("no_crop_imprint");
        ItemStack out = seeds();
        if (out == null) return idle("unknown_crop");
        ItemStack in = input(0);
        int sticks = CropsConfig.sticksPerSeed;
        if (sticks > 0 && (!CropImprints.isSticks(in) || in.stackSize < sticks)) return idle("no_sticks");
        if (!canOutput(out)) return blocked();

        if (sticks > 0) in.stackSize -= sticks;
        mOutputItems[0] = out;
        return start(CropsConfig.seedEut, CropsConfig.seedTicks);
    }

    @Override
    public List<EchoPattern> echoPatterns() {
        ItemStack out = CropsConfig.cropsEnabled ? seeds() : null;
        if (out == null) return Collections.emptyList();
        ItemStack sticks = IC2Items.getItem("crop");
        ItemStack[] in = CropsConfig.sticksPerSeed > 0 && sticks != null
            ? new ItemStack[] { withSize(sticks, CropsConfig.sticksPerSeed) }
            : new ItemStack[0];
        return Collections.singletonList(new EchoPattern(in, new ItemStack[] { out }));
    }

    private static ItemStack withSize(ItemStack s, int n) {
        ItemStack c = s.copy();
        c.stackSize = n;
        return c;
    }
}
