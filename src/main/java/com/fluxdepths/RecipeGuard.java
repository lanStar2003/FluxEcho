package com.fluxdepths;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import com.fluxecho.core.EchoRecipes;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.ToolDictNames;
import gregtech.api.util.GTModHandler;

/**
 * FluxDepths' crafting recipes, through GT so that its tools (lowercase letters) and ore unification work. GT drops a
 * recipe without a word when it does not like it; then the same recipe goes in as a plain Forge ore recipe, and
 * {@link #check} puts it back at the end of loading if anything took it out again. Every step that goes wrong says so
 * in the log.
 */
public final class RecipeGuard {

    private static final long BITS = GTModHandler.RecipeBits.NOT_REMOVABLE;
    /** GT's tool letters in recipe shapes, as ore names for a plain recipe. */
    private static final Map<Character, String> TOOLS = new LinkedHashMap<>();
    private static final Map<String, Object[]> EXPECTED = new LinkedHashMap<>();

    static {
        TOOLS.put('d', ToolDictNames.craftingToolScrewdriver.name());
        TOOLS.put('f', ToolDictNames.craftingToolFile.name());
        TOOLS.put('h', ToolDictNames.craftingToolHardHammer.name());
        TOOLS.put('k', ToolDictNames.craftingToolKnife.name());
        TOOLS.put('r', ToolDictNames.craftingToolSoftMallet.name());
        TOOLS.put('s', ToolDictNames.craftingToolSaw.name());
        TOOLS.put('w', ToolDictNames.craftingToolWrench.name());
        TOOLS.put('x', ToolDictNames.craftingToolWireCutter.name());
    }

    private RecipeGuard() {}

    /**
     * The recipe with GT's tool letters and ore names spelled out, for Forge; null when Forge cannot read it.
     */
    private static IRecipe plain(String what, ItemStack output, Object[] recipe) {
        List<Object> r = new ArrayList<>(Arrays.asList(EchoRecipes.forForge(recipe)));
        for (Object o : recipe) {
            if (!(o instanceof String row) || row.length() > 3) continue;
            for (char c : row.toCharArray()) if (TOOLS.containsKey(c) && !r.contains(c)) {
                r.add(c);
                r.add(TOOLS.get(c));
            }
        }
        try {
            return new ShapedOreRecipe(output.copy(), r.toArray());
        } catch (RuntimeException e) {
            FluxDepths.LOG.warn("Forge cannot read the recipe of {}: {}", what, e.getMessage());
            return null;
        }
    }

    /** A shaped recipe for {@code what} (a name for the log). */
    public static void shaped(String what, ItemStack output, Object... recipe) {
        if (output == null) {
            FluxDepths.LOG.warn("No recipe for {}: the item is not registered", what);
            return;
        }
        for (int i = 0; i < recipe.length; i++)
            if (recipe[i] instanceof Character c && (i + 1 >= recipe.length || recipe[i + 1] == null)) {
                FluxDepths.LOG.warn("No recipe for {}: ingredient '{}' not found in this pack", what, c);
                return;
            }
        IRecipe plain = plain(what, output, recipe);
        boolean viaGT = GTModHandler.addCraftingRecipe(output.copy(), BITS, recipe.clone());
        if (plain != null) EXPECTED.put(what, new Object[] { output.copy(), plain });
        if (viaGT) return;
        if (plain == null) {
            FluxDepths.LOG.warn("No recipe for {}: GregTech did not take it, and Forge cannot read it either", what);
            return;
        }
        FluxDepths.LOG.warn("GregTech did not take the recipe of {}; adding it as a plain ore recipe", what);
        GameRegistry.addRecipe(plain);
    }

    /** A shapeless recipe without tools, straight into Forge (GT adds nothing to one). */
    public static void shapeless(String what, ItemStack output, Object... inputs) {
        if (output == null) {
            FluxDepths.LOG.warn("No recipe for {}: the item is not registered", what);
            return;
        }
        for (Object o : inputs) if (o == null) {
            FluxDepths.LOG.warn("No recipe for {}: an ingredient is not in this pack", what);
            return;
        }
        GameRegistry.addRecipe(new ShapelessOreRecipe(output.copy(), inputs));
    }

    private static boolean emptyOre(IRecipe r) {
        if (r instanceof ShapedOreRecipe s)
            for (Object in : s.getInput()) if (in instanceof Collection<?>c && c.isEmpty()) return true;
        return false;
    }

    /** At the end of loading: puts back lost recipes and reports ones nobody can craft. */
    public static void check() {
        if (EXPECTED.isEmpty()) return;
        List<?> all = CraftingManager.getInstance()
            .getRecipeList();
        int ok = 0;
        List<String> restored = new ArrayList<>(), hidden = new ArrayList<>();
        for (Map.Entry<String, Object[]> e : EXPECTED.entrySet()) {
            ItemStack out = (ItemStack) e.getValue()[0];
            IRecipe plain = (IRecipe) e.getValue()[1];
            boolean found = false, good = false;
            for (Object o : all) {
                if (!(o instanceof IRecipe r)) continue;
                ItemStack ro = r.getRecipeOutput();
                if (ro == null || ro.getItem() != out.getItem() || ro.getItemDamage() != out.getItemDamage()) continue;
                found = true;
                if (!emptyOre(r)) good = true;
            }
            if (!found) {
                GameRegistry.addRecipe(plain);
                restored.add(e.getKey());
                good = !emptyOre(plain);
            }
            if (good) ok++;
            else hidden.add(e.getKey());
        }
        if (!restored.isEmpty()) FluxDepths.LOG.warn("Recipe check: put back the lost recipes of {}", restored);
        if (!hidden.isEmpty())
            FluxDepths.LOG.warn("Recipe check: the recipes of {} use an ore name nothing is registered under", hidden);
        FluxDepths.LOG.info("Recipe check: {} of {} machines and items have a working recipe", ok, EXPECTED.size());
    }
}
