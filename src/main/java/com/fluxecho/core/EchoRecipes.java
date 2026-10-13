package com.fluxecho.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import com.fluxecho.FluxEcho;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.GTValues;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTModHandler;

/**
 * FluxEcho's crafting recipes, through GT so that GT's ore unification works in them. GT drops a recipe without a
 * word when it does not like it; then the same recipe goes in as a plain Forge ore recipe, and {@link RecipeCheck}
 * puts it back at the end of loading if anything took it out again. Every step that goes wrong says so in the log. A
 * recipe in one of GT's machines that a material needs ({@link #wiremill}, 0.15.0) has a crafting recipe to stand in
 * when GT cannot take it.
 */
public final class EchoRecipes {

    private static final long BITS = GTModHandler.RecipeBits.NOT_REMOVABLE;

    private EchoRecipes() {}

    /** The keys of a shaped recipe whose ingredient is null. */
    private static List<String> missing(Object[] recipe) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < recipe.length; i++) {
            if (recipe[i] instanceof Character c && (i + 1 >= recipe.length || recipe[i + 1] == null))
                out.add(String.valueOf(c));
        }
        return out;
    }

    private static void add(String what, ItemStack output, boolean viaGT, IRecipe plain) {
        if (plain != null) RecipeCheck.expect(what, output, plain);
        if (viaGT) return;
        if (plain == null) {
            FluxEcho.LOG.warn("No recipe for {}: GregTech did not take it, and Forge cannot read it either", what);
            return;
        }
        FluxEcho.LOG.warn("GregTech did not take the recipe of {}; adding it as a plain ore recipe", what);
        GameRegistry.addRecipe(plain);
    }

    /**
     * The recipe in words Forge reads: GT's ore names ({@code OrePrefixes.plate.get(...)}) are GT objects that only
     * spell the name, which Forge turns down; it gets the name itself.
     */
    public static Object[] forForge(Object[] recipe) {
        Object[] out = recipe.clone();
        for (int i = 0; i < out.length; i++) {
            Object o = out[i];
            if (o != null && !(o instanceof String || o instanceof Character
                || o instanceof ItemStack
                || o instanceof Item
                || o instanceof Block)) out[i] = String.valueOf(o);
        }
        return out;
    }

    /** The plain Forge recipe, or null when Forge cannot read the ingredients. */
    private static IRecipe plain(String what, boolean shaped, ItemStack output, Object[] recipe) {
        try {
            return shaped ? new ShapedOreRecipe(output.copy(), forForge(recipe))
                : new ShapelessOreRecipe(output.copy(), forForge(recipe));
        } catch (RuntimeException e) {
            FluxEcho.LOG.warn("Forge cannot read the recipe of {}: {}", what, e.getMessage());
            return null;
        }
    }

    /** A shaped recipe for {@code what} (a name for the log). */
    public static void shaped(String what, ItemStack output, Object... recipe) {
        if (output == null) {
            FluxEcho.LOG.warn("No recipe for {}: the item is not registered", what);
            return;
        }
        List<String> missing = missing(recipe);
        if (!missing.isEmpty()) {
            FluxEcho.LOG.warn("No recipe for {}: ingredient {} not found in this pack", what, missing);
            return;
        }
        IRecipe plain = plain(what, true, output, recipe);
        add(what, output, GTModHandler.addCraftingRecipe(output.copy(), BITS, recipe.clone()), plain);
    }

    /** A shapeless recipe for {@code what}. */
    public static void shapeless(String what, ItemStack output, Object... ingredients) {
        if (output == null) {
            FluxEcho.LOG.warn("No recipe for {}: the item is not registered", what);
            return;
        }
        for (Object o : ingredients) if (o == null) {
            FluxEcho.LOG.warn("No recipe for {}: an ingredient is not found in this pack", what);
            return;
        }
        IRecipe plain = plain(what, false, output, ingredients);
        add(what, output, GTModHandler.addShapelessCraftingRecipe(output.copy(), BITS, ingredients.clone()), plain);
    }

    /** A shaped recipe for a machine. */
    public static void machine(MachineId m, Object... recipe) {
        shaped(m.english, Machines.get(m), recipe);
    }

    /**
     * A recipe in GT's wiremill (0.15.0: phase alloy drawn into the wire memory filament is coated from). When GT's
     * recipe API is not the one this build was made against, or the wiremill takes nothing, the shaped crafting recipe
     * {@code byHand} for {@code handOutput} stands in for it (its GT tool letters, such as {@code x} the wire cutter,
     * work as in any GT crafting recipe), so the material can still be made; either way the log says what happened.
     */
    public static void wiremill(String what, ItemStack input, ItemStack output, int ticks, int eut,
        ItemStack handOutput, Object... byHand) {
        if (input == null || input.getItem() == null || output == null || output.getItem() == null) {
            FluxEcho.LOG.warn("No wiremill recipe for {}: an item is not registered", what);
            return;
        }
        try {
            Collection<?> added = GTValues.RA.stdBuilder()
                .itemInputs(input.copy())
                .itemOutputs(output.copy())
                .duration(ticks)
                .eut(eut)
                .addTo(RecipeMaps.wiremillRecipes);
            if (added != null && !added.isEmpty()) return;
            FluxEcho.LOG.warn("GregTech's wiremill did not take the recipe of {}; adding it by hand instead", what);
        } catch (Throwable t) {
            FluxEcho.LOG.warn("GregTech's wiremill is not available for {}; adding it by hand instead", what, t);
        }
        if (byHand.length > 0) shaped(what, handOutput, byHand);
    }
}
