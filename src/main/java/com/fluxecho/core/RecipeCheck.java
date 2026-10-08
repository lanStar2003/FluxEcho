package com.fluxecho.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import com.fluxecho.FluxEcho;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Once everything has loaded, checks that every machine and item FluxEcho gives a recipe to still has one in the
 * crafting manager; one that lost it (GT or another mod took it out) gets its plain ore recipe back. Also reports an
 * empty ore name among the ingredients: NEI hides such a recipe and nobody can craft it.
 */
public final class RecipeCheck {

    private static final class Expected {

        final ItemStack output;
        final IRecipe plain;

        Expected(ItemStack output, IRecipe plain) {
            this.output = output;
            this.plain = plain;
        }
    }

    private static final Map<String, Expected> EXPECTED = new LinkedHashMap<>();

    private RecipeCheck() {}

    /** Notes that the stack should have a crafting recipe by the end of loading, and the recipe to put back if not. */
    static void expect(String what, ItemStack output, IRecipe plain) {
        EXPECTED.put(what, new Expected(output.copy(), plain));
    }

    private static boolean makes(IRecipe r, ItemStack out) {
        ItemStack o = r.getRecipeOutput();
        return o != null && o.getItem() == out.getItem() && o.getItemDamage() == out.getItemDamage();
    }

    private static Object[] inputs(IRecipe r) {
        if (r instanceof ShapedOreRecipe s) return s.getInput();
        if (r instanceof ShapelessOreRecipe s) return s.getInput()
            .toArray();
        return new Object[0];
    }

    /** Whether one of the recipe's ingredients is an ore name nothing is registered under. */
    private static boolean emptyOre(IRecipe r) {
        for (Object in : inputs(r)) if (in instanceof Collection<?>c && c.isEmpty()) return true;
        return false;
    }

    public static void run() {
        if (EXPECTED.isEmpty()) return;
        List<?> all = CraftingManager.getInstance()
            .getRecipeList();
        int ok = 0;
        List<String> restored = new ArrayList<>(), hidden = new ArrayList<>();
        for (Map.Entry<String, Expected> e : EXPECTED.entrySet()) {
            boolean found = false, good = false;
            for (Object o : all) {
                if (!(o instanceof IRecipe r) || !makes(r, e.getValue().output)) continue;
                found = true;
                if (!emptyOre(r)) good = true;
            }
            if (!found) {
                GameRegistry.addRecipe(e.getValue().plain);
                restored.add(e.getKey());
                good = !emptyOre(e.getValue().plain);
            }
            if (good) ok++;
            else hidden.add(e.getKey());
        }
        if (!restored.isEmpty()) FluxEcho.LOG.warn("Recipe check: put back the lost recipes of {}", restored);
        if (!hidden.isEmpty())
            FluxEcho.LOG.warn("Recipe check: the recipes of {} use an ore name nothing is registered under", hidden);
        FluxEcho.LOG.info("Recipe check: {} of {} machines and items have a working recipe", ok, EXPECTED.size());
    }
}
