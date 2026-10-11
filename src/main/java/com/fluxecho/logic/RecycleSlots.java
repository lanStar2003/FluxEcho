package com.fluxecho.logic;

import java.util.Arrays;
import java.util.List;

/**
 * The recycle bin's face to automation (0.10.1): the campus's recycle store shown to hoppers, pipes and AE buses as
 * {@link #SLOTS} virtual slots, read and take only. Slot {@code i} holds the {@code i}-th largest kind of the store, as
 * one stack of {@code min(count, stack size)}; kinds whose item cannot be made into a stack any more (its mod is gone)
 * are left out. Generic over the kind, so it runs without items (the bin's tile gives it the store).
 * <p>
 * The view is made again at the first access of every tick, and whenever someone else (the bin's screen, another bin,
 * the nexus GUI's 取出, the builder filling the store) changed the store since the view last looked: a slot never
 * shows more than the store holds at that moment, so two extractors (or an extractor and a player) acting in the same
 * tick cannot take the same items twice. Within a tick the view's own extractions only lower its slots (they are not
 * filled up again before the next tick), so a caller that read a slot and then acts on it sees what it read.
 * <p>
 * Taking out comes two ways, as inventories are emptied in 1.7.10: {@link #take} ({@code decrStackSize}) and
 * {@link #set} with a smaller size ({@code setInventorySlotContents} after the caller shrank the stack it read). A
 * {@link #set} with a larger size only puts back what was taken through this view in the same tick (a hopper that
 * took an item it then had no room for puts it back this way); nothing else ever goes into the store.
 */
public final class RecycleSlots<K> {

    /** How many virtual slots the bin shows. */
    public static final int SLOTS = 27;

    /** What the view reads and changes. */
    public interface Store<K> {

        /** The kinds held, the largest count first (the store's own order, which the view keeps). */
        List<K> largest();

        /** How many of the kind the store holds (0 for none). */
        long count(K kind);

        /** How many of the kind make one stack; 0 when it cannot be made into a stack at all. */
        int stackSize(K kind);

        /** Takes up to {@code max} of the kind out; returns how many were taken. */
        long extract(K kind, long max);

        /** Puts {@code n} of the kind back. */
        void add(K kind, long n);

        /** A number that changes whenever the contents change. */
        int version();
    }

    private final Object[] kinds = new Object[SLOTS];
    private final int[] size = new int[SLOTS];
    /** Per slot: how many items this view took out this tick and may still put back. */
    private final int[] owed = new int[SLOTS];
    private long tick = Long.MIN_VALUE;
    private int seen;
    private int rev;

    /**
     * Makes the view again when the tick changed or someone else changed the store since the view last looked; returns
     * whether it did. Call it before reading or changing a slot.
     */
    public boolean refresh(Store<K> s, long now) {
        if (now == tick && s.version() == seen) return false;
        tick = now;
        seen = s.version();
        rev++;
        Arrays.fill(kinds, null);
        Arrays.fill(size, 0);
        Arrays.fill(owed, 0);
        int slot = 0;
        for (K k : s.largest()) {
            if (slot >= SLOTS) break;
            int stack = s.stackSize(k);
            long n = s.count(k);
            if (stack <= 0 || n <= 0) continue;
            kinds[slot] = k;
            size[slot] = (int) Math.min(n, stack);
            slot++;
        }
        return true;
    }

    /** Grows every time the view is made again (the tile builds its stacks anew then). */
    public int rev() {
        return rev;
    }

    /** The kind shown in the slot, or null for an empty slot (or one out of range). */
    @SuppressWarnings("unchecked")
    public K kind(int slot) {
        return slot < 0 || slot >= SLOTS ? null : (K) kinds[slot];
    }

    /** How many the slot shows (0 when empty). */
    public int size(int slot) {
        return slot < 0 || slot >= SLOTS || kinds[slot] == null ? 0 : size[slot];
    }

    /**
     * Takes up to {@code n} from the slot out of the store ({@code decrStackSize}); returns how many came out (never
     * more than the slot shows, nor than the store holds).
     */
    public int take(Store<K> s, int slot, int n) {
        K k = kind(slot);
        if (k == null || n <= 0) return 0;
        int took = (int) s.extract(k, Math.min(n, size[slot]));
        size[slot] -= took;
        owed[slot] += took;
        seen = s.version();
        return took;
    }

    /**
     * Sets the slot to show {@code n} of its kind ({@code setInventorySlotContents} with a stack of its kind, 0 for
     * none): less than it shows takes the difference out of the store; more puts back at most what this view took from
     * the slot this tick. Returns the items moved: negative when taken out, positive when put back.
     */
    public int set(Store<K> s, int slot, int n) {
        K k = kind(slot);
        if (k == null) return 0;
        n = Math.max(0, n);
        int moved = 0;
        if (n < size[slot]) {
            int took = (int) s.extract(k, size[slot] - n);
            size[slot] -= took;
            owed[slot] += took;
            moved = -took;
        } else if (n > size[slot]) {
            int back = Math.min(n - size[slot], owed[slot]);
            if (back > 0) {
                s.add(k, back);
                size[slot] += back;
                owed[slot] -= back;
                moved = back;
            }
        }
        seen = s.version();
        return moved;
    }
}
