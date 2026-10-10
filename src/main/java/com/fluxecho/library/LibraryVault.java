package com.fluxecho.library;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;

import com.gtnewhorizons.modularui.api.forge.IItemHandlerModifiable;

/**
 * Each team's book vault: where an Echo Library's samples go when its core is taken down, so that rebuilding the
 * library (as the campus does when it raises the Echo Archive in place of a 0.9.2 hall) never costs a book. A library
 * that forms as the Archive for the first time, and the library GUI's 取回 button, fill the library's free places from
 * the vault in unit order; a hall that has no shelf for a book puts it here too.
 * <p>
 * Saved with the world ({@code data/fluxecho_vault.dat}, in the shared map storage, so every dimension sees the same
 * vault): {@code Teams[{Team, Items[stack NBT]}]}. The books are kept as the NBT their stacks were written as, so a
 * book whose mod went missing stays in the vault instead of vanishing. Server side only.
 */
public class LibraryVault extends WorldSavedData {

    static final String NAME = "fluxecho_vault";

    /** Per team, the books in the order they came in. */
    private final Map<UUID, List<NBTTagCompound>> books = new LinkedHashMap<>();

    public LibraryVault(String name) {
        super(name);
    }

    /** The vault of the world's save, or null where there is none (a client world, a world without storage). */
    static LibraryVault of(World w) {
        if (w == null || w.isRemote) return null;
        MapStorage m = w.mapStorage;
        if (m == null) return null;
        LibraryVault v = (LibraryVault) m.loadData(LibraryVault.class, NAME);
        if (v == null) {
            v = new LibraryVault(NAME);
            m.setData(NAME, v);
        }
        return v;
    }

    /**
     * Puts the books into the team's vault, each stack as it is given. A library keeps one item a place, so its
     * callers hand in one item a book ({@link TileLibrary#oneEach}) and drop the rest of a stack that a place should
     * never have held. Returns false when there is no vault to put them in (no team, no storage); the caller then
     * keeps or drops them itself.
     */
    public static boolean put(World w, UUID team, List<ItemStack> stacks) {
        LibraryVault v = team == null ? null : of(w);
        if (v == null) return false;
        for (ItemStack s : stacks) if (s != null) v.add(team, s.writeToNBT(new NBTTagCompound()));
        v.markDirty();
        return true;
    }

    /** How many books the team's vault holds. */
    public static int count(World w, UUID team) {
        LibraryVault v = team == null ? null : of(w);
        return v == null ? 0
            : v.raw(team)
                .size();
    }

    /**
     * Moves books from the team's vault into the handler's free places below {@code capacity}, in slot order (for the
     * Archive that is unit order), each place taking only a book the handler accepts there ({@code isItemValid}: a
     * library refuses a second copy of a book it keeps). Books it cannot take stay in the vault. A saved stack of more
     * than one gives one item and keeps the rest ({@link #useOne}), so nothing is ever cut away. Returns how many
     * moved.
     */
    public static int takeInto(World w, UUID team, IItemHandlerModifiable into, int capacity) {
        LibraryVault v = team == null ? null : of(w);
        if (v == null) return 0;
        List<NBTTagCompound> list = v.books.get(team);
        if (list == null || list.isEmpty()) return 0;
        int cap = Math.min(capacity, into.getSlots()), slot = 0, moved = 0;
        for (Iterator<NBTTagCompound> it = list.iterator(); it.hasNext();) {
            while (slot < cap && into.getStackInSlot(slot) != null) slot++;
            if (slot >= cap) break;
            NBTTagCompound e = it.next();
            ItemStack s = ItemStack.loadItemStackFromNBT(e);
            // an item of a mod that is gone stays as it is, to come back with the mod
            if (s == null) continue;
            s.stackSize = 1;
            if (!into.isItemValid(slot, s)) continue;
            into.setStackInSlot(slot, s);
            if (useOne(e)) it.remove();
            moved++;
        }
        if (list.isEmpty()) v.books.remove(team);
        if (moved > 0) v.markDirty();
        return moved;
    }

    /**
     * One item of a saved stack went into a library: a stack of more than one is saved one smaller and kept, and the
     * method answers false; a stack of one (or of a count that makes no sense) is used up, and it answers true.
     */
    static boolean useOne(NBTTagCompound stack) {
        int n = stack.getByte("Count");
        if (n <= 1) return true;
        stack.setByte("Count", (byte) (n - 1));
        return false;
    }

    // ---- the saved form

    void add(UUID team, NBTTagCompound stack) {
        books.computeIfAbsent(team, k -> new ArrayList<>())
            .add(stack);
    }

    /** The team's books as saved (a view). */
    List<NBTTagCompound> raw(UUID team) {
        List<NBTTagCompound> l = books.get(team);
        return l == null ? Collections.emptyList() : Collections.unmodifiableList(l);
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        books.clear();
        NBTTagList teams = t.getTagList("Teams", 10);
        for (int i = 0; i < teams.tagCount(); i++) {
            NBTTagCompound e = teams.getCompoundTagAt(i);
            UUID team;
            try {
                team = UUID.fromString(e.getString("Team"));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            NBTTagList items = e.getTagList("Items", 10);
            for (int k = 0; k < items.tagCount(); k++) add(team, items.getCompoundTagAt(k));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound t) {
        NBTTagList teams = new NBTTagList();
        for (Map.Entry<UUID, List<NBTTagCompound>> e : books.entrySet()) {
            if (e.getValue()
                .isEmpty()) continue;
            NBTTagCompound team = new NBTTagCompound();
            team.setString(
                "Team",
                e.getKey()
                    .toString());
            NBTTagList items = new NBTTagList();
            for (NBTTagCompound s : e.getValue()) items.appendTag(s.copy());
            team.setTag("Items", items);
            teams.appendTag(team);
        }
        t.setTag("Teams", teams);
    }
}
