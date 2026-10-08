package com.fluxecho.thaumcraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.fluxecho.Config;
import com.fluxecho.core.EchoPattern;
import com.fluxecho.core.MachineId;
import com.fluxecho.logic.Choice;

import gregtech.api.enums.GTValues;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.crafting.CrucibleRecipe;

/**
 * Crucible Echo (MV). Runs a crucible recipe without throwing anything into a crucible: put the catalyst in, and the
 * essentia the recipe needs is synthesized from EU and shard credit, as in the Essentia Echo. Only recipes whose
 * research the owner has finished and whose aspects the team has learned. When one catalyst fits several recipes, a
 * programmed circuit picks one (they are listed in a fixed order).
 */
public class MTECrucibleEcho extends MTEThaumMachine {

    private static List<CrucibleRecipe> recipes;

    public MTECrucibleEcho(int id) {
        super(MachineId.CRUCIBLE_ECHO, id, 2, 1);
    }

    private MTECrucibleEcho(String name, String[] description, ITexture[][][] textures) {
        super(MachineId.CRUCIBLE_ECHO, name, description, textures, 2, 1);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity te) {
        return new MTECrucibleEcho(mName, mDescriptionArray, mTextures);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return TCRecipeMaps.crucibleEcho();
    }

    @Override
    public boolean allowSelectCircuit() {
        return true;
    }

    @Override
    protected int unitsPerShard() {
        return Config.essentiaPerShard;
    }

    @Override
    protected Object[] tooltipArgs() {
        return new Object[] { Config.essentiaEuPerUnit, Config.essentiaPerShard };
    }

    /** Every crucible recipe, in a fixed order; read once all mods have added theirs. */
    private static synchronized List<CrucibleRecipe> recipes() {
        if (recipes == null) {
            List<CrucibleRecipe> all = new ArrayList<>();
            for (Object o : ThaumcraftApi.getCraftingRecipes()) if (o instanceof CrucibleRecipe r) all.add(r);
            all.sort(
                Comparator.comparing((CrucibleRecipe r) -> r.key == null ? "" : r.key)
                    .thenComparing(
                        r -> r.getRecipeOutput() == null ? ""
                            : r.getRecipeOutput()
                                .getUnlocalizedName())
                    .thenComparingInt(
                        r -> r.getRecipeOutput() == null ? 0
                            : r.getRecipeOutput()
                                .getItemDamage()));
            recipes = all;
        }
        return recipes;
    }

    @Override
    protected int work() {
        UUID owner = getBaseMetaTileEntity().getOwnerUuid();
        UUID team = team();
        if (owner == null || team == null) return idle("no_owner");
        learnFromInputs(team);

        int slot = -1;
        for (int i = 0; i < mInputSlotCount && slot < 0; i++) {
            ItemStack s = input(i);
            if (s != null && !AspectSamples.isShard(s) && !AspectSamples.isEssentia(s)) slot = i;
        }
        if (slot < 0) return idle("no_catalyst");
        ItemStack catalyst = input(slot);

        List<CrucibleRecipe> fits = new ArrayList<>();
        boolean anyCatalyst = false;
        for (CrucibleRecipe r : recipes()) {
            if (r.aspects == null || r.getRecipeOutput() == null || !r.catalystMatches(catalyst)) continue;
            anyCatalyst = true;
            if (!researched(owner, r.key)) continue;
            if (knowsAll(team, r.aspects)) fits.add(r);
        }
        if (fits.isEmpty()) return idle(anyCatalyst ? "crucible_locked" : "no_crucible_recipe");
        int pick = Choice.pick(fits.size(), circuit());
        if (pick < 0) return idle("need_circuit_recipe");
        CrucibleRecipe r = fits.get(pick);

        long units = units(r.aspects);
        if (!credit(units, false)) return idle("no_shard");
        ItemStack out = r.getRecipeOutput()
            .copy();
        if (!canOutput(out)) return blocked();
        credit(units, true);
        catalyst.stackSize--;
        mOutputItems[0] = out;
        // the essentia's EU is drawn over the cycle, at most the machine's voltage per tick
        long eu = units * Config.essentiaEuPerUnit;
        int maxEut = (int) GTValues.V[mTier];
        int ticks = (int) Math.max(20, (eu + maxEut - 1) / maxEut);
        return start((int) Math.max(1, (eu + ticks - 1) / ticks), ticks);
    }

    /** A catalyst's stand-in for an AE pattern: the stack, or the first of its ore dictionary entries. */
    private static ItemStack example(Object catalyst) {
        ItemStack e = null;
        if (catalyst instanceof ItemStack s) e = s.copy();
        else if (catalyst instanceof List<?>l && !l.isEmpty() && l.get(0) instanceof ItemStack s) e = s.copy();
        if (e == null) return null;
        e.stackSize = 1;
        if (e.getItemDamage() == OreDictionary.WILDCARD_VALUE) e.setItemDamage(0);
        return e;
    }

    /**
     * Every crucible recipe the owner and team have unlocked, catalyst in, result out; for a catalyst with several,
     * the one the circuit picks.
     */
    @Override
    public List<EchoPattern> echoPatterns() {
        UUID owner = getBaseMetaTileEntity().getOwnerUuid();
        UUID team = team();
        if (!Config.thaumEnabled || owner == null || team == null) return Collections.emptyList();
        Map<String, List<CrucibleRecipe>> byCatalyst = new LinkedHashMap<>();
        Map<String, ItemStack> examples = new HashMap<>();
        for (CrucibleRecipe r : recipes()) {
            if (r.aspects == null || r.getRecipeOutput() == null) continue;
            ItemStack e = example(r.catalyst);
            if (e == null || !researched(owner, r.key) || !knowsAll(team, r.aspects)) continue;
            String k = e.getItem()
                .getUnlocalizedName() + "@"
                + e.getItemDamage();
            examples.putIfAbsent(k, e);
            byCatalyst.computeIfAbsent(k, x -> new ArrayList<>())
                .add(r);
        }
        List<EchoPattern> out = new ArrayList<>();
        for (Map.Entry<String, List<CrucibleRecipe>> en : byCatalyst.entrySet()) {
            int pick = Choice.pick(
                en.getValue()
                    .size(),
                circuit());
            if (pick < 0) continue;
            out.add(
                new EchoPattern(
                    new ItemStack[] { examples.get(en.getKey()) },
                    new ItemStack[] { en.getValue()
                        .get(pick)
                        .getRecipeOutput()
                        .copy() }));
        }
        return out;
    }
}
