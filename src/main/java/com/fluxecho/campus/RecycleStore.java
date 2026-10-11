package com.fluxecho.campus;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * The recycle store of a nexus campus (0.10.1): everything the builder takes down that is not one of our own parts
 * (the drops of what it clears, the contents of the chests and machines it dismantles) is kept here, to be taken out
 * at the recycle bin, by automation through the bin, or with 取出 in the nexus GUI. It replaced the 27 slots of
 * 0.10.0's spoils, which ran full after a few trees.
 * <p>
 * A kind is an item prototype: the NBT {@code ItemStack.writeToNBT} writes for one item, without its count (item id,
 * damage and tag). Two stacks are the same kind when that NBT is equal, so items merge by item, meta and NBT, and an
 * item whose mod is gone keeps its NBT untouched. Counts are longs (a graded disc yields millions of cobblestone). The
 * store reports {@link #full()} at {@link #MAX_KINDS} kinds, or once its saved form would take {@link #MAX_BYTES}
 * (it keeps a running estimate, {@link #bytes()}): it is saved inside the nexus tile, and a chunk whose NBT passes the
 * region file's limit (about 1 MB) is silently not saved at all, so a store of machines and AE cells with heavy NBT
 * must stop well short of it. The builder then waits instead of clearing (it never loses what it takes down, so one
 * removal may carry the store a little past either mark).
 * <p>
 * Pure data, server side, no world: the stacks are turned into kinds and back by {@link #kindOf} and {@link #stackOf}.
 * Saved as a list of {@code {K: kind, N: count}} ({@link #write}, {@link #read}). Every change runs the listener given
 * to the constructor (the campus marks itself to be saved) and bumps {@link #version()}.
 */
public final class RecycleStore {

    /** The most kinds the store takes before it is {@link #full()}. */
    public static final int MAX_KINDS = 4096;
    /**
     * The most bytes its saved form ({@link #write}, uncompressed) may take before it is {@link #full()}: a quarter of
     * the region file's limit for a whole chunk, which the nexus tile shares with everything else in its chunk.
     */
    public static final int MAX_BYTES = 256 * 1024;
    /** What one entry {@code {K: kind, N: count}} of {@link #write} takes beyond its kind's own bytes (rounded up). */
    static final int ENTRY_BYTES = 16;

    /**
     * How a kind's NBT (with a count) is made into a stack ({@link #stackOf}): {@code ItemStack.loadItemStackFromNBT}.
     * Only the unit tests replace it, as they have no item registry.
     */
    static Function<NBTTagCompound, ItemStack> loader = ItemStack::loadItemStackFromNBT;

    /** One kind and how many of it are stored (a snapshot; the kind must not be modified). */
    public static final class Entry {

        /** The item prototype (see the class comment). Shared with the store: never modify it. */
        public final NBTTagCompound kind;
        public final long count;

        Entry(NBTTagCompound kind, long count) {
            this.kind = kind;
            this.count = count;
        }
    }

    /** Count per kind, in the order the kinds first came in (the tie-break of {@link #list()}). */
    private final Map<NBTTagCompound, Long> counts = new LinkedHashMap<>();
    /** The saved size of each kind's entry ({@link #entryBytes}), worked out once when the kind comes in. */
    private final Map<NBTTagCompound, Integer> sizes = new HashMap<>();
    private final Runnable onChange;
    private long total, bytes;
    private int version;
    private List<Entry> sorted;

    /** An empty store whose changes nobody hears of. */
    public RecycleStore() {
        this(null);
    }

    /** An empty store that runs {@code onChange} after every change (null for none). */
    public RecycleStore(Runnable onChange) {
        this.onChange = onChange;
    }

    // ---- what it holds

    /** How many kinds it holds. */
    public int kinds() {
        return counts.size();
    }

    /** How many items it holds in all (saturating at {@code Long.MAX_VALUE}). */
    public long total() {
        return total;
    }

    public boolean isEmpty() {
        return counts.isEmpty();
    }

    /**
     * Whether it holds {@link #MAX_KINDS} kinds or more, or its saved form takes {@link #MAX_BYTES} or more: the
     * builder waits until some are taken out.
     */
    public boolean full() {
        return counts.size() >= MAX_KINDS || bytes >= MAX_BYTES;
    }

    /** About how many bytes its saved form ({@link #write}) takes, uncompressed: the sum of {@link #entryBytes}. */
    public long bytes() {
        return bytes;
    }

    /** How many of the kind it holds (0 for an unknown or null kind). */
    public long count(NBTTagCompound kind) {
        if (kind == null) return 0;
        Long n = counts.get(kind);
        return n == null ? 0 : n;
    }

    /**
     * Every kind with its count, the largest count first (kinds of equal count in the order they first came in). The
     * list is cached until the next change and must not be modified, nor its kinds.
     */
    public List<Entry> list() {
        if (sorted == null) {
            List<Entry> out = new ArrayList<>(counts.size());
            for (Map.Entry<NBTTagCompound, Long> e : counts.entrySet()) out.add(new Entry(e.getKey(), e.getValue()));
            // a stable sort: equal counts keep the order the kinds came in
            out.sort((a, b) -> Long.compare(b.count, a.count));
            sorted = Collections.unmodifiableList(out);
        }
        return sorted;
    }

    /** A number that changes whenever the contents change (for screens and automation that cache a view of it). */
    public int version() {
        return version;
    }

    // ---- changing it

    /** Adds {@code n} of the kind (nothing for a null kind or {@code n <= 0}); the kind is copied. */
    public void add(NBTTagCompound kind, long n) {
        if (kind == null || n <= 0) return;
        Long have = counts.get(kind);
        if (have == null) put((NBTTagCompound) kind.copy(), n);
        else counts.put(kind, sum(have, n));
        total = sum(total, n);
        changed();
    }

    /** A kind not held yet comes in, and its entry's size with it. */
    private void put(NBTTagCompound kind, long n) {
        int size = entryBytes(kind);
        counts.put(kind, n);
        sizes.put(kind, size);
        bytes += size;
    }

    /** A kind taken out whole goes, and its entry's size with it. */
    private void drop(NBTTagCompound kind) {
        counts.remove(kind);
        Integer size = sizes.remove(kind);
        if (size != null) bytes = Math.max(0, bytes - size);
    }

    /**
     * Takes up to {@code max} of the kind out and returns how many were taken (0 when it holds none, or {@code max}
     * is not positive). A kind taken out completely is forgotten.
     */
    public long extract(NBTTagCompound kind, long max) {
        if (kind == null || max <= 0) return 0;
        Long have = counts.get(kind);
        if (have == null) return 0;
        long take = Math.min(have, max);
        if (take >= have) drop(kind);
        else counts.put(kind, have - take);
        total = Math.max(0, total - take);
        changed();
        return take;
    }

    /** Empties the store. */
    public void clear() {
        if (counts.isEmpty()) return;
        counts.clear();
        sizes.clear();
        total = 0;
        bytes = 0;
        changed();
    }

    /**
     * Moves kinds from one store into another, the largest counts first, while the other is not {@link #full()}; a
     * kind moves whole, never split. Returns how many kinds moved. A team's put-aside store drains into a nexus this
     * way ({@link RecycleOverflow}).
     */
    public static int moveInto(RecycleStore from, RecycleStore to) {
        int moved = 0;
        for (Entry e : new ArrayList<>(from.list())) {
            if (to.full()) break;
            to.add(e.kind, e.count);
            from.extract(e.kind, e.count);
            moved++;
        }
        return moved;
    }

    private void changed() {
        sorted = null;
        version++;
        if (onChange != null) onChange.run();
    }

    private static long sum(long a, long b) {
        long s = a + b;
        return s < 0 ? Long.MAX_VALUE : s;
    }

    // ---- saving

    /** The store as a list of {@code {K: kind, N: count}}, the largest first. */
    public NBTTagList write() {
        NBTTagList l = new NBTTagList();
        for (Entry e : list()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setTag("K", e.kind.copy());
            c.setLong("N", e.count);
            l.appendTag(c);
        }
        return l;
    }

    /**
     * Replaces the contents with a saved list ({@link #write}); entries without a kind or with a count below one are
     * skipped, and the same kind twice is merged. A null list empties the store.
     */
    public void read(NBTTagList l) {
        counts.clear();
        sizes.clear();
        total = 0;
        bytes = 0;
        if (l != null) for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            if (!c.hasKey("K", 10)) continue;
            long n = c.getLong("N");
            NBTTagCompound kind = c.getCompoundTag("K");
            if (n <= 0 || kind.hasNoTags()) continue;
            Long have = counts.get(kind);
            if (have == null) put((NBTTagCompound) kind.copy(), n);
            else counts.put(kind, sum(have, n));
            total = sum(total, n);
        }
        sorted = null;
        version++;
    }

    /**
     * The bytes one entry of the kind takes in the saved list ({@link #write}), uncompressed: the kind as NBT writes
     * it, and the entry round it ({@link #ENTRY_BYTES}).
     */
    public static int entryBytes(NBTTagCompound kind) {
        DataOutputStream out = new DataOutputStream(new OutputStream() {

            @Override
            public void write(int b) {}

            @Override
            public void write(byte[] b, int off, int len) {}
        });
        try {
            CompressedStreamTools.write(kind, out);
        } catch (IOException | RuntimeException e) {
            // a kind that cannot be written is counted as large, never as nothing
            return MAX_BYTES / 16;
        }
        return out.size() + ENTRY_BYTES;
    }

    // ---- stacks

    /**
     * The kind of a stack: what {@code writeToNBT} writes for it, without the count. Null for a null stack or one
     * without an item.
     */
    public static NBTTagCompound kindOf(ItemStack s) {
        if (s == null || s.getItem() == null) return null;
        NBTTagCompound t = s.writeToNBT(new NBTTagCompound());
        t.removeTag("Count");
        return t;
    }

    /**
     * A stack of {@code n} of the kind, or null when the item is not in the game any more (its mod was removed) or
     * {@code n} is not positive. The kind is not modified.
     */
    public static ItemStack stackOf(NBTTagCompound kind, int n) {
        if (kind == null || n <= 0) return null;
        NBTTagCompound t = (NBTTagCompound) kind.copy();
        t.setByte("Count", (byte) 1);
        ItemStack s = loader.apply(t);
        if (s == null || s.getItem() == null) return null;
        s.stackSize = n;
        return s;
    }

    /** Adds a stack (nothing for null, empty or itemless stacks). */
    public void add(ItemStack s) {
        if (s == null || s.stackSize <= 0) return;
        add(kindOf(s), s.stackSize);
    }
}
