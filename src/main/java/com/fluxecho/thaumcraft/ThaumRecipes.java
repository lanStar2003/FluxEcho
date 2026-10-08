package com.fluxecho.thaumcraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import cpw.mods.fml.common.registry.GameRegistry;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.crafting.ShapedArcaneRecipe;
import thaumcraft.api.crafting.ShapelessArcaneRecipe;

/**
 * The infusion and arcane recipes, by what they make: the Infusion Echo looks up its sample here. Only recipes with
 * a plain item result count (runic augments and the like change the input's NBT instead). Built on first use, after
 * every mod (and the GTNH core mod's changes in postInit) is done.
 */
final class ThaumRecipes {

    /** One thing a recipe needs: any stack the test accepts; {@link #example} is what an AE pattern asks for. */
    static final class Ingredient {

        final Predicate<ItemStack> test;
        final ItemStack example;

        Ingredient(Predicate<ItemStack> test, ItemStack example) {
            this.test = test;
            this.example = example;
        }
    }

    /** One way to make an item. */
    static final class Option {

        final boolean arcane;
        final String research;
        /** Essentia of an infusion; the vis of an arcane recipe (one primal unit per vis). */
        final AspectList aspects;
        /** An infusion's centre first, then its components; an arcane recipe's ingredients. One item each. */
        final List<Ingredient> ingredients;
        final ItemStack output;

        Option(boolean arcane, String research, AspectList aspects, List<Ingredient> ingredients, ItemStack output) {
            this.arcane = arcane;
            this.research = research;
            this.aspects = aspects;
            this.ingredients = ingredients;
            this.output = output;
        }
    }

    private static Map<String, List<Option>> byOutput;

    private ThaumRecipes() {}

    /** {@code modid:name:damage} of an item, the key of the ledger and of this index. */
    static String key(ItemStack s) {
        if (s == null || s.getItem() == null) return null;
        GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(s.getItem());
        return id == null ? null : id.modId + ":" + id.name + ":" + s.getItemDamage();
    }

    /** The item of a key, for the Codex. */
    static ItemStack stack(String key) {
        int last = key.lastIndexOf(':'), first = key.indexOf(':');
        if (first <= 0 || last <= first) return null;
        Item item = GameRegistry.findItem(key.substring(0, first), key.substring(first + 1, last));
        try {
            return item == null ? null : new ItemStack(item, 1, Integer.parseInt(key.substring(last + 1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The ways to make the sample, in a fixed order; empty when none. */
    static List<Option> forSample(ItemStack sample) {
        List<Option> all = index().get(key(sample));
        if (all == null) return Collections.emptyList();
        List<Option> out = new ArrayList<>();
        for (Option o : all) {
            // a recipe whose result carries NBT only counts for a sample with the same NBT
            if (o.output.hasTagCompound() && !ItemStack.areItemStackTagsEqual(o.output, sample)) continue;
            out.add(o);
        }
        return out;
    }

    /** Whether something makes this item, for the Codex's tooltip line. */
    static boolean isProduct(ItemStack s) {
        return index().containsKey(key(s));
    }

    private static synchronized Map<String, List<Option>> index() {
        if (byOutput == null) {
            Map<String, List<Option>> m = new HashMap<>();
            for (Object o : ThaumcraftApi.getCraftingRecipes()) {
                Option opt = null;
                try {
                    if (o instanceof InfusionRecipe r) opt = infusion(r);
                    else if (o instanceof IArcaneRecipe r) opt = arcane(r);
                } catch (RuntimeException ignored) {
                    // a recipe that does not answer is left out
                }
                String k = opt == null ? null : key(opt.output);
                if (k != null) m.computeIfAbsent(k, x -> new ArrayList<>())
                    .add(opt);
            }
            byOutput = m;
        }
        return byOutput;
    }

    private static Option infusion(InfusionRecipe r) {
        if (!(r.getRecipeOutput() instanceof ItemStack out) || out.getItem() == null || r.getRecipeInput() == null)
            return null;
        List<Ingredient> ins = new ArrayList<>();
        ins.add(infusionPart(r.getRecipeInput()));
        if (r.getComponents() != null) for (ItemStack c : r.getComponents()) if (c != null) ins.add(infusionPart(c));
        return new Option(false, r.getResearch(), r.getAspects(), ins, out.copy());
    }

    /** Thaumcraft's own comparison, with ore dictionary equivalents and wildcard damage. */
    private static Ingredient infusionPart(ItemStack want) {
        ItemStack w = want.copy();
        w.stackSize = 1;
        return new Ingredient(
            have -> InfusionRecipe.areItemStacksEqual(have, w, true) || OreDictionary.itemMatches(w, have, false),
            example(w));
    }

    private static Option arcane(IArcaneRecipe r) {
        ItemStack out = r.getRecipeOutput();
        if (out == null || out.getItem() == null) return null;
        List<Object> raw = new ArrayList<>();
        if (r instanceof ShapedArcaneRecipe s) Collections.addAll(raw, s.getInput());
        else if (r instanceof ShapelessArcaneRecipe s) raw.addAll(s.getInput());
        else return null;
        List<Ingredient> ins = new ArrayList<>();
        for (Object o : raw) {
            if (o == null) continue;
            Ingredient i = arcanePart(o);
            if (i == null) return null;
            ins.add(i);
        }
        if (ins.isEmpty()) return null;
        return new Option(true, r.getResearch(), r.getAspects(), ins, out.copy());
    }

    private static Ingredient arcanePart(Object o) {
        if (o instanceof ItemStack want) {
            ItemStack w = want.copy();
            return new Ingredient(have -> OreDictionary.itemMatches(w, have, false), example(w));
        }
        if (o instanceof List<?>alts && !alts.isEmpty()) {
            List<ItemStack> ok = new ArrayList<>();
            for (Object a : alts) if (a instanceof ItemStack s) ok.add(s.copy());
            if (ok.isEmpty()) return null;
            return new Ingredient(have -> {
                for (ItemStack w : ok) if (OreDictionary.itemMatches(w, have, false)) return true;
                return false;
            }, example(ok.get(0)));
        }
        return null;
    }

    /** A concrete stack of one: wildcard damage becomes 0. */
    private static ItemStack example(ItemStack s) {
        ItemStack e = s.copy();
        e.stackSize = 1;
        if (e.getItemDamage() == OreDictionary.WILDCARD_VALUE) e.setItemDamage(0);
        return e;
    }

    /**
     * Which input slots give each ingredient: per slot how many items to take, or null when the inputs do not hold
     * them all. Greedy, in slot order.
     */
    static int[] plan(List<Ingredient> ingredients, ItemStack[] slots) {
        int[] take = new int[slots.length];
        for (Ingredient i : ingredients) {
            boolean found = false;
            for (int s = 0; s < slots.length && !found; s++) {
                ItemStack have = slots[s];
                if (have == null || have.stackSize - take[s] <= 0 || !i.test.test(have)) continue;
                take[s]++;
                found = true;
            }
            if (!found) return null;
        }
        return take;
    }
}
