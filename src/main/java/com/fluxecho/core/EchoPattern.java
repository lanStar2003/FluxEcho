package com.fluxecho.core;

import java.util.Arrays;

import net.minecraft.item.ItemStack;

/**
 * Something an echo machine can make right now with a known result: what goes into its inputs and what comes out.
 * The Echo ME Provider offers these to AE2's autocrafting. Stacks are copies; counts may exceed 64.
 */
public final class EchoPattern {

    public final ItemStack[] inputs;
    public final ItemStack[] outputs;

    public EchoPattern(ItemStack[] inputs, ItemStack[] outputs) {
        this.inputs = copy(inputs);
        this.outputs = copy(outputs);
    }

    private static ItemStack[] copy(ItemStack[] in) {
        ItemStack[] out = new ItemStack[in.length];
        for (int i = 0; i < in.length; i++) out[i] = in[i] == null ? null : in[i].copy();
        return out;
    }

    private static int hash(ItemStack s) {
        if (s == null) return 0;
        int h = System.identityHashCode(s.getItem());
        h = 31 * h + s.getItemDamage();
        h = 31 * h + s.stackSize;
        return 31 * h + (s.getTagCompound() == null ? 0
            : s.getTagCompound()
                .hashCode());
    }

    private static boolean same(ItemStack a, ItemStack b) {
        if (a == null || b == null) return a == b;
        return a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage()
            && a.stackSize == b.stackSize
            && ItemStack.areItemStackTagsEqual(a, b);
    }

    private static boolean same(ItemStack[] a, ItemStack[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (!same(a[i], b[i])) return false;
        return true;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EchoPattern p && same(inputs, p.inputs) && same(outputs, p.outputs);
    }

    @Override
    public int hashCode() {
        int h = 1;
        for (ItemStack s : inputs) h = 31 * h + hash(s);
        for (ItemStack s : outputs) h = 31 * h + hash(s);
        return h;
    }

    @Override
    public String toString() {
        return Arrays.toString(inputs) + " -> " + Arrays.toString(outputs);
    }
}
