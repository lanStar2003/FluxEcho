package com.fluxecho.ae;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.fluxecho.core.EchoPattern;

import appeng.api.AEApi;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

/**
 * An echo machine's pattern as AE2 sees it: a processing pattern (inputs pushed into the machine, outputs come back
 * into the network) that needs no pattern item. Equal patterns of several machines are one pattern to AE, which then
 * spreads its jobs over all of them.
 */
final class EchoPatternDetails implements ICraftingPatternDetails {

    final EchoPattern pattern;
    private final IAEItemStack[] inputs, outputs;
    private int priority;

    EchoPatternDetails(EchoPattern pattern) {
        this.pattern = pattern;
        this.inputs = convert(pattern.inputs);
        this.outputs = convert(pattern.outputs);
    }

    private static IAEItemStack[] convert(ItemStack[] stacks) {
        int n = 0;
        for (ItemStack s : stacks) if (s != null) n++;
        IAEItemStack[] out = new IAEItemStack[n];
        int i = 0;
        for (ItemStack s : stacks) if (s != null) {
            IAEItemStack a = AEApi.instance()
                .storage()
                .createItemStack(s);
            a.setStackSize(s.stackSize);
            out[i++] = a;
        }
        return out;
    }

    /** No pattern item: the first output stands for the pattern in AE's crafting status. */
    @Override
    public ItemStack getPattern() {
        return pattern.outputs.length == 0 || pattern.outputs[0] == null ? null : pattern.outputs[0].copy();
    }

    @Override
    public boolean isValidItemForSlot(int slot, ItemStack stack, World world) {
        return true;
    }

    @Override
    public boolean isCraftable() {
        return false;
    }

    @Override
    public IAEItemStack[] getInputs() {
        return inputs;
    }

    @Override
    public IAEItemStack[] getCondensedInputs() {
        return inputs;
    }

    @Override
    public IAEItemStack[] getCondensedOutputs() {
        return outputs;
    }

    @Override
    public IAEItemStack[] getOutputs() {
        return outputs;
    }

    @Override
    public boolean canSubstitute() {
        return false;
    }

    @Override
    public ItemStack getOutput(InventoryCrafting craftingInv, World world) {
        return null;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EchoPatternDetails d && d.pattern.equals(pattern);
    }

    @Override
    public int hashCode() {
        return pattern.hashCode();
    }
}
