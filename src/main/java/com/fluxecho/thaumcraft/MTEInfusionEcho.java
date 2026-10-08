package com.fluxecho.thaumcraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.ItemStack;

import com.fluxecho.Config;
import com.fluxecho.codex.Categories;
import com.fluxecho.core.EchoPattern;
import com.fluxecho.core.MachineId;

import gregtech.api.enums.GTValues;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import thaumcraft.api.aspects.Aspect;

/**
 * Infusion Echo (MV). The special slot holds a finished item that was infused (or made at the arcane workbench) by
 * hand once, and is kept; the machine makes more of it from the same ingredients in its nine inputs, with no
 * pedestals, no instability and no warp. The essentia of an infusion is synthesized as in the Essentia Echo; the vis
 * of an arcane recipe costs one primal unit per vis. Only recipes the owner has researched and whose aspects the team
 * has learned.
 */
public class MTEInfusionEcho extends MTEThaumMachine {

    public MTEInfusionEcho(int id) {
        super(MachineId.INFUSION_ECHO, id, 9, 1);
    }

    private MTEInfusionEcho(String name, String[] description, ITexture[][][] textures) {
        super(MachineId.INFUSION_ECHO, name, description, textures, 9, 1);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity te) {
        return new MTEInfusionEcho(mName, mDescriptionArray, mTextures);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return TCRecipeMaps.infusionEcho();
    }

    @Override
    protected int unitsPerShard() {
        return Config.essentiaPerShard;
    }

    @Override
    protected Object[] tooltipArgs() {
        return new Object[] { Config.essentiaEuPerUnit, Config.essentiaPerShard };
    }

    /** Primal units an option costs: the essentia's units, or one per vis. */
    private static long cost(ThaumRecipes.Option o) {
        if (!o.arcane) return units(o.aspects);
        long u = 0;
        if (o.aspects != null) for (Aspect a : o.aspects.getAspects()) if (a != null) u += o.aspects.getAmount(a);
        return u;
    }

    /** The options for the sample that the owner and team may use, in order. */
    private List<ThaumRecipes.Option> unlocked(UUID owner, UUID team) {
        List<ThaumRecipes.Option> out = new ArrayList<>();
        for (ThaumRecipes.Option o : ThaumRecipes.forSample(sample()))
            if (researched(owner, o.research) && knowsAll(team, o.aspects)) out.add(o);
        return out;
    }

    private ItemStack[] inputs() {
        ItemStack[] in = new ItemStack[mInputSlotCount];
        for (int i = 0; i < in.length; i++) in[i] = input(i);
        return in;
    }

    @Override
    protected int work() {
        UUID owner = getBaseMetaTileEntity().getOwnerUuid();
        UUID team = team();
        if (owner == null || team == null) return idle("no_owner");
        learnFromInputs(team);
        if (sample() == null) return idle("no_infusion_sample");
        if (ThaumRecipes.forSample(sample())
            .isEmpty()) return idle("no_thaum_recipe");
        List<ThaumRecipes.Option> options = unlocked(owner, team);
        if (options.isEmpty()) return idle("thaum_locked");

        ItemStack[] in = inputs();
        for (ThaumRecipes.Option o : options) {
            int[] take = ThaumRecipes.plan(o.ingredients, in);
            if (take == null) continue;
            ItemStack out = o.output.copy();
            if (!canOutput(out)) return blocked();
            // take the ingredients first, so shards that are ingredients are not counted as credit
            ItemStack[] before = new ItemStack[in.length];
            for (int i = 0; i < in.length; i++) {
                before[i] = in[i] == null ? null : in[i].copy();
                if (take[i] > 0) in[i].stackSize -= take[i];
            }
            long units = cost(o);
            if (!credit(units, true)) {
                for (int i = 0; i < in.length; i++) mInventory[getInputSlot() + i] = before[i];
                return idle("no_shard");
            }
            for (int i = 0; i < in.length; i++)
                if (in[i] != null && in[i].stackSize <= 0) mInventory[getInputSlot() + i] = null;
            mOutputItems[0] = out;
            remember(Categories.INFUSION, ThaumRecipes.key(sample()));
            long eu = units * Config.essentiaEuPerUnit;
            int maxEut = (int) GTValues.V[mTier];
            int ticks = (int) Math.max(20, (eu + maxEut - 1) / maxEut);
            return start((int) Math.max(1, (eu + ticks - 1) / ticks), ticks);
        }
        return idle("no_ingredients");
    }

    /** The first unlocked recipe for the sample, with each ingredient as one concrete item. */
    @Override
    public List<EchoPattern> echoPatterns() {
        UUID owner = getBaseMetaTileEntity().getOwnerUuid();
        UUID team = team();
        if (!Config.thaumEnabled || owner == null || team == null || sample() == null) return Collections.emptyList();
        List<ThaumRecipes.Option> options = unlocked(owner, team);
        if (options.isEmpty()) return Collections.emptyList();
        ThaumRecipes.Option o = options.get(0);
        List<ItemStack> ins = new ArrayList<>();
        for (ThaumRecipes.Ingredient i : o.ingredients) {
            boolean merged = false;
            for (ItemStack s : ins) if (s.isItemEqual(i.example) && ItemStack.areItemStackTagsEqual(s, i.example)) {
                s.stackSize++;
                merged = true;
                break;
            }
            if (!merged) ins.add(i.example.copy());
        }
        return Collections
            .singletonList(new EchoPattern(ins.toArray(new ItemStack[0]), new ItemStack[] { o.output.copy() }));
    }
}
