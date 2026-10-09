package com.fluxecho.library;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.fluxecho.nexus.NexusRegistry;
import com.fluxecho.nexus.TileModule;

/**
 * An echo machine with a written index card in its sample slot works with the card's sample while an Echo Library of
 * the machine's team keeps it, is docked and powered (and in the same dimension, unless {@code library_reach}).
 * Server side; the client sees only the card.
 */
public final class Lending {

    private static final Map<NBTTagCompound, ItemStack> CACHE = new WeakHashMap<>();

    private Lending() {}

    /** Whether the stack is a written index card. */
    public static boolean isCard(ItemStack s) {
        return ItemLibraryCard.written(s);
    }

    /** The sample a library lends for the card, a fresh copy; null when none lends it. */
    public static ItemStack resolve(ItemStack card, World w, UUID machineTeam) {
        if (w == null || w.isRemote || machineTeam == null || !ItemLibraryCard.written(card)) return null;
        UUID cardTeam = ItemLibraryCard.team(card);
        if (cardTeam != null && !cardTeam.equals(machineTeam)) return null;
        ItemStack sample = cached(card);
        if (sample == null) return null;
        int dim = w.provider.dimensionId;
        for (TileModule m : NexusRegistry.allModules()) {
            if (m instanceof TileLibrary lib && lib.lends(machineTeam, dim) && lib.holds(sample)) {
                lib.lent();
                return sample.copy();
            }
        }
        return null;
    }

    private static synchronized ItemStack cached(ItemStack card) {
        NBTTagCompound tag = ItemLibraryCard.sampleTag(card);
        if (tag == null) return null;
        ItemStack s = CACHE.get(tag);
        if (s == null) {
            s = ItemStack.loadItemStackFromNBT(tag);
            if (s != null) CACHE.put(tag, s);
        }
        return s;
    }
}
