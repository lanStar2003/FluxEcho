package com.fluxecho.bees;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.fluxecho.Config;
import com.fluxecho.core.EchoText;
import com.fluxecho.core.MTEEchoMachine;
import com.fluxecho.core.MachineId;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;

/**
 * Gene Assembler (LV): the crafting grid's gene splicing ({@link GeneSamples#splice}) as a machine, for automation.
 * Everything in the nine inputs is one job: an imprint and samples, or samples only. One of each stack is used up.
 * With AE, use blocking mode so two jobs never share the inputs.
 */
public class MTEGeneAssembler extends MTEEchoMachine {

    public MTEGeneAssembler(int id) {
        super(MachineId.GENE_ASSEMBLER, id, 9, 1);
    }

    private MTEGeneAssembler(String name, String[] description, ITexture[][][] textures) {
        super(MachineId.GENE_ASSEMBLER, name, description, textures, 9, 1);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity te) {
        return new MTEGeneAssembler(mName, mDescriptionArray, mTextures);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return BeeNei.assemblerMap();
    }

    @Override
    protected boolean moduleEnabled() {
        return Config.beesEnabled;
    }

    @Override
    protected Object[] tooltipArgs() {
        return new Object[] { EchoText.seconds(Config.assembleTicks), Config.assembleEut };
    }

    @Override
    protected int work() {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < mInputSlotCount; i++) if (input(i) != null) stacks.add(input(i));
        if (stacks.isEmpty()) return idle("splice_empty");
        GeneSamples.Splice splice = GeneSamples.splice(stacks);
        if (splice.result == null) return idle(splice.problem);
        if (!canOutput(splice.result)) return blocked();

        int first = getInputSlot();
        for (int i = first; i < first + mInputSlotCount; i++) {
            if (mInventory[i] == null) continue;
            if (--mInventory[i].stackSize <= 0) mInventory[i] = null;
        }
        mOutputItems[0] = splice.result;
        return start(Config.assembleEut, Config.assembleTicks);
    }
}
