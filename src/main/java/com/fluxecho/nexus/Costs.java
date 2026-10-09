package com.fluxecho.nexus;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.fluxecho.logic.CostPlan;
import com.fluxecho.logic.ResearchTree;

import cpw.mods.fml.common.registry.GameRegistry;

/** What research and manifestation costs mean in items: matching a stack, showing a cost, taking it. */
public final class Costs {

    private Costs() {}

    public static boolean matches(ResearchTree.Cost c, ItemStack s) {
        if (s == null || s.getItem() == null) return false;
        if (c.ore) {
            for (ItemStack o : OreDictionary.getOres(c.name)) if (OreDictionary.itemMatches(o, s, false)) return true;
            return false;
        }
        Item item = item(c);
        return item != null && s.getItem() == item
            && (c.meta == OreDictionary.WILDCARD_VALUE || s.getItemDamage() == c.meta);
    }

    private static Item item(ResearchTree.Cost c) {
        int colon = c.name.indexOf(':');
        return GameRegistry.findItem(c.name.substring(0, colon), c.name.substring(colon + 1));
    }

    /** The stacks a cost can be paid with, each of the cost's size; empty when nothing in the pack pays it. */
    public static List<ItemStack> options(ResearchTree.Cost c) {
        List<ItemStack> out = new ArrayList<>();
        if (c.ore) {
            for (ItemStack o : OreDictionary.getOres(c.name)) {
                ItemStack s = o.copy();
                if (s.getItemDamage() == OreDictionary.WILDCARD_VALUE) s.setItemDamage(0);
                s.stackSize = c.count;
                out.add(s);
            }
        } else {
            Item item = item(c);
            if (item != null) out.add(new ItemStack(item, c.count, c.meta));
        }
        return out;
    }

    /** How many to take from each slot of the stacks, or null when they cannot pay. */
    public static int[] plan(List<ResearchTree.Cost> costs, ItemStack[] slots) {
        int[] counts = new int[slots.length];
        for (int i = 0; i < slots.length; i++) counts[i] = slots[i] == null ? 0 : slots[i].stackSize;
        return CostPlan.plan(costs, counts, (c, i) -> matches(c, slots[i]));
    }

    /** Takes the costs out of the player's inventory, all or nothing. Creative players pay nothing. */
    public static boolean takeFrom(EntityPlayer p, List<ResearchTree.Cost> costs) {
        if (p.capabilities.isCreativeMode) return true;
        ItemStack[] inv = p.inventory.mainInventory;
        int[] take = plan(costs, inv);
        if (take == null) return false;
        for (int i = 0; i < inv.length; i++) {
            if (take[i] <= 0) continue;
            inv[i].stackSize -= take[i];
            if (inv[i].stackSize <= 0) inv[i] = null;
        }
        p.inventory.markDirty();
        return true;
    }
}
