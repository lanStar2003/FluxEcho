package com.fluxecho.thaumcraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.core.EchoRecipeMaps;
import com.fluxecho.core.MachineId;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.GTValues;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.common.config.ConfigItems;

/**
 * Examples for the Thaumcraft machines' NEI pages: what the Essentia Echo learns from and where its essentia comes
 * out, what the Insight Echo turns paper into, every crucible recipe the Crucible Echo can run, and every infusion
 * and arcane recipe the Infusion Echo can repeat (with the finished item as its sample). The essentia's EU is drawn
 * over the cycle at the machine's voltage, as the machines do.
 */
final class TCNei {

    /** Vis crystal shards: aer, and the balanced one that pays for any essentia. */
    private static final int AER_SHARD = 0, BALANCED_SHARD = 6;

    private TCNei() {}

    static void pages() {
        essentia();
        insight();
        int crucible = crucible(), infusion = infusion();
        FluxEcho.LOG
            .info("Thaumcraft echoes: {} crucible and {} infusion or arcane recipes in NEI", crucible, infusion);
    }

    private static void essentia() {
        ItemStack phial = AspectSamples.phial("aer");
        ItemStack shard = new ItemStack(ConfigItems.itemShard, 1, AER_SHARD);
        List<ItemStack> out = new ArrayList<>();
        if (phial != null) out.add(phial.copy());
        if (TCModule.outlet != null) out.add(new ItemStack(TCModule.outlet));
        List<ItemStack> in = new ArrayList<>();
        if (phial != null) in.add(phial);
        in.add(shard);
        EchoRecipeMaps
            .page(MachineId.ESSENTIA_ECHO, null, in.toArray(new ItemStack[0]), out.toArray(new ItemStack[0]), 0, 20);
    }

    private static void insight() {
        Item book = GameRegistry.findItem("Thaumcraft", "ItemThaumonomicon");
        EchoRecipeMaps.page(
            MachineId.INSIGHT_ECHO,
            new ItemStack(Items.diamond),
            new ItemStack[] { new ItemStack(Items.paper) },
            book == null ? new ItemStack[0] : new ItemStack[] { new ItemStack(book) },
            Config.insightEut,
            Config.insightTicks);
    }

    /** The EU a tick and ticks for {@code units} of essentia, as the machines draw it. */
    private static int[] power(long units, int tier) {
        long eu = units * Config.essentiaEuPerUnit;
        int maxEut = (int) GTValues.V[tier];
        int ticks = (int) Math.max(20, (eu + maxEut - 1) / maxEut);
        return new int[] { (int) Math.max(1, (eu + ticks - 1) / ticks), ticks };
    }

    /** Balanced shards for {@code units}, as one stack. */
    private static ItemStack shards(long units) {
        long n = (units + Config.essentiaPerShard - 1) / Math.max(1, Config.essentiaPerShard);
        return new ItemStack(ConfigItems.itemShard, (int) Math.max(1, Math.min(64, n)), BALANCED_SHARD);
    }

    private static int crucible() {
        int pages = 0;
        for (CrucibleRecipe r : MTECrucibleEcho.recipes()) {
            ItemStack catalyst = MTECrucibleEcho.example(r.catalyst);
            if (catalyst == null || r.getRecipeOutput() == null || r.aspects == null) continue;
            long units = MTEThaumMachine.units(r.aspects);
            int[] p = power(units, MachineId.CRUCIBLE_ECHO.tier);
            EchoRecipeMaps.page(
                MachineId.CRUCIBLE_ECHO,
                null,
                new ItemStack[] { catalyst, shards(units) },
                new ItemStack[] { r.getRecipeOutput()
                    .copy() },
                p[0],
                p[1]);
            pages++;
        }
        return pages;
    }

    private static int infusion() {
        int pages = 0;
        for (Map.Entry<String, List<ThaumRecipes.Option>> e : new TreeMap<>(ThaumRecipes.all()).entrySet()) {
            for (ThaumRecipes.Option o : e.getValue()) {
                List<ItemStack> in = new ArrayList<>();
                for (ThaumRecipes.Ingredient i : o.ingredients) {
                    if (i.example == null) continue;
                    boolean merged = false;
                    for (ItemStack s : in)
                        if (s.isItemEqual(i.example) && ItemStack.areItemStackTagsEqual(s, i.example)) {
                            s.stackSize++;
                            merged = true;
                            break;
                        }
                    if (!merged) {
                        ItemStack s = i.example.copy();
                        s.stackSize = 1;
                        in.add(s);
                    }
                }
                if (in.isEmpty() || in.size() > 9) continue;
                long units = MTEInfusionEcho.cost(o);
                in.add(shards(units));
                if (in.size() > 9) in.remove(in.size() - 1);
                int[] p = power(units, MachineId.INFUSION_ECHO.tier);
                EchoRecipeMaps.page(
                    MachineId.INFUSION_ECHO,
                    o.output.copy(),
                    in.toArray(new ItemStack[0]),
                    new ItemStack[] { o.output.copy() },
                    p[0],
                    p[1]);
                pages++;
            }
        }
        return pages;
    }
}
