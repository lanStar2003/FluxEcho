package com.fluxecho.bees;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

/**
 * Putting genes together by hand in a crafting grid: an imprint and gene samples give the imprint with the samples'
 * genes; samples alone give one sample of them all (see {@link GeneSamples#splice}). The ingredients are used up, as
 * in the Gene Assembler; more samples come from the Bee Imprinter for a sheet of paper each.
 */
public class GeneSpliceRecipe implements IRecipe {

    private static ItemStack result(InventoryCrafting grid) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < grid.getSizeInventory(); i++) {
            ItemStack s = grid.getStackInSlot(i);
            if (s != null) stacks.add(s);
        }
        if (stacks.isEmpty()) return null;
        return GeneSamples.splice(stacks).result;
    }

    @Override
    public boolean matches(InventoryCrafting grid, World world) {
        return result(grid) != null;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting grid) {
        return result(grid);
    }

    @Override
    public int getRecipeSize() {
        return 9;
    }

    /** Null: the result depends on the ingredients, like vanilla's armour dyeing. */
    @Override
    public ItemStack getRecipeOutput() {
        return null;
    }
}
