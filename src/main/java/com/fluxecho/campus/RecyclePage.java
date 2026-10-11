package com.fluxecho.campus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.fluxecho.library.BookPacket;

/**
 * One page of the recycle store on its way to the recycle bin's screen (0.10.1). The store may hold thousands of kinds
 * and an item's NBT can be large (a dismantled backpack, a written book), while a GUI sync packet holds at most 32767
 * bytes, so the screen only ever gets the rows it shows: from a first index, at most a page of kinds, each with its
 * count and an identity hash, plus the store's totals. A kind travels whole while the NBT tags of the page's kinds stay
 * within a budget, and otherwise without its tag (the icon and the name are those of the plain item); the budget
 * starts generous and is halved until the page compresses under {@link #LIMIT}, as {@link BookPacket} does for books.
 * <p>
 * The hash ({@link #hash}, of the whole kind, worked out on the server) is how a click names a kind: the screen sends
 * the index it drew and the hash, and the server takes the kind at that index if the hash matches, or else looks the
 * hash up, so a store that changed order in between never hands out the wrong item ({@link #find}).
 * <p>
 * Keys: {@code Kd} kinds in the store, {@code Ti} items in the store, {@code Of} the index of the first entry,
 * {@code E} the entries {@code {K: kind, N: count, H: hash}} ({@code C} set on one whose kind was sent without its
 * tag).
 */
public final class RecyclePage {

    /** The most a page may take compressed, leaving room for the rest of the sync packet. */
    public static final int LIMIT = 24_000;
    /** Item NBT (bytes of its binary form) the whole kinds of a page may carry at first. */
    static final int TAG_BUDGET = 128 * 1024;
    /** Below this the next try sends no item NBT at all. */
    private static final int MIN_BUDGET = 1024;

    /** One kind on the page. */
    public static final class Entry {

        /** The kind, without its tag when {@link #cut}. */
        public final NBTTagCompound kind;
        public final long count;
        /** The hash of the whole kind, as the server has it. */
        public final int hash;
        /** Whether its tag was left out to keep the page small. */
        public final boolean cut;

        Entry(NBTTagCompound kind, long count, int hash, boolean cut) {
            this.kind = kind;
            this.count = count;
            this.hash = hash;
            this.cut = cut;
        }
    }

    /** A page as the screen reads it. */
    public static final class Page {

        public static final Page EMPTY = new Page(0, 0, 0, Collections.emptyList());

        /** Kinds and items the whole store holds. */
        public final int kinds;
        public final long items;
        /** The store index of the first entry. */
        public final int first;
        public final List<Entry> entries;

        Page(int kinds, long items, int first, List<Entry> entries) {
            this.kinds = kinds;
            this.items = items;
            this.first = first;
            this.entries = entries;
        }

        /** The entry at a store index, or null when the page does not hold it. */
        public Entry at(int index) {
            int i = index - first;
            return i < 0 || i >= entries.size() ? null : entries.get(i);
        }
    }

    private RecyclePage() {}

    /** The identity of a whole kind (stable between server and client, as it only depends on the NBT's content). */
    public static int hash(NBTTagCompound kind) {
        return kind == null ? 0 : kind.hashCode();
    }

    /**
     * The page of {@code list} (the store's {@link RecycleStore#list}) from index {@code first} (brought into range)
     * with at most {@code size} kinds, small enough to sync.
     */
    public static NBTTagCompound write(List<RecycleStore.Entry> list, int first, int size, long items) {
        int from = Math.max(0, Math.min(first, list.size())), to = Math.min(list.size(), from + Math.max(0, size));
        List<RecycleStore.Entry> part = list.subList(from, to);
        int[] tags = new int[part.size()];
        for (int i = 0; i < tags.length; i++) {
            NBTTagCompound k = part.get(i).kind;
            tags[i] = k.hasKey("tag", 10) ? BookPacket.binarySize(k.getCompoundTag("tag")) : 0;
        }
        int budget = TAG_BUDGET;
        while (true) {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("Kd", list.size());
            t.setLong("Ti", items);
            t.setInteger("Of", from);
            t.setTag("E", entries(part, tags, budget));
            if (budget == 0 || BookPacket.compressedSize(t) < LIMIT) return t;
            budget = budget / 2 < MIN_BUDGET ? 0 : budget / 2;
        }
    }

    /** The entries with at most {@code budget} bytes of item tags among the whole kinds. */
    private static NBTTagList entries(List<RecycleStore.Entry> part, int[] tags, int budget) {
        NBTTagList l = new NBTTagList();
        long used = 0;
        for (int i = 0; i < part.size(); i++) {
            RecycleStore.Entry e = part.get(i);
            NBTTagCompound k = (NBTTagCompound) e.kind.copy();
            boolean cut = false;
            if (tags[i] > 0) {
                if (used + tags[i] <= budget) used += tags[i];
                else {
                    k.removeTag("tag");
                    cut = true;
                }
            }
            NBTTagCompound c = new NBTTagCompound();
            c.setTag("K", k);
            c.setLong("N", e.count);
            c.setInteger("H", hash(e.kind));
            if (cut) c.setBoolean("C", true);
            l.appendTag(c);
        }
        return l;
    }

    /** The page from {@link #write}; an empty page for null. */
    public static Page read(NBTTagCompound t) {
        if (t == null) return Page.EMPTY;
        NBTTagList l = t.getTagList("E", 10);
        List<Entry> out = new ArrayList<>(l.tagCount());
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            out.add(new Entry(c.getCompoundTag("K"), c.getLong("N"), c.getInteger("H"), c.getBoolean("C")));
        }
        return new Page(t.getInteger("Kd"), t.getLong("Ti"), t.getInteger("Of"), Collections.unmodifiableList(out));
    }

    /**
     * The index in {@code list} of the kind a click named: {@code index} when the kind there has the hash, else the
     * first kind with it; -1 when the store holds no such kind any more.
     */
    public static int find(List<RecycleStore.Entry> list, int index, int hash) {
        if (index >= 0 && index < list.size() && hash(list.get(index).kind) == hash) return index;
        for (int i = 0; i < list.size(); i++) if (hash(list.get(i).kind) == hash) return i;
        return -1;
    }
}
