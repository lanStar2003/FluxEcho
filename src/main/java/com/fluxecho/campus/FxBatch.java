package com.fluxecho.campus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.fluxecho.logic.FxCodec;

/**
 * What the campus builder did over the last few ticks that players nearby should see: the cells it launched (each
 * flies from the core to its cell and prints in when it lands) and the cells it cleared (break particles). The builder
 * fills it as it works and hands it to {@link CampusNet#sendFx} every four ticks when it is not empty (at once when a
 * page filled up), then {@link #reset()}s it. Cells are packed with {@link FxCodec} relative to the nexus centre.
 * <p>
 * The entries are kept in {@link Page pages} of at most {@link #MAX} launches and {@link #MAX} clears, one packet
 * each, so a packet stays small however busy the builder is: a page that is full starts the next one rather than
 * dropping the entry. A batch holds at most {@link #MAX_PAGES} pages, far more than the builder fills before it is
 * sent ({@link BuildJob#MAX_FLIGHTS} launches in flight, the clearing pace), so only a runaway batch would lose
 * effects (these are effects only).
 * <p>
 * Times: {@link Page#base()} is the world tick of the page's first entry. A launch left the core at
 * {@code base + launchOffsets()[i]} and lands at {@code base + launchOffsets()[i] + launchFlights()[i]}.
 */
public final class FxBatch {

    /** The most launches, and the most clears, in one page (one packet). */
    public static final int MAX = 64;
    /** The most pages one batch holds. */
    public static final int MAX_PAGES = 32;

    /** One packet's worth of launches and clears. */
    public static final class Page {

        private final int[] launchCells = new int[MAX], clearCells = new int[MAX], clearBlocks = new int[MAX];
        private final byte[] launchParts = new byte[MAX], launchFlights = new byte[MAX], launchOffsets = new byte[MAX];
        private int launches, clears;
        private long base = -1;

        private void stamp(long worldTick) {
            if (base < 0) base = worldTick;
        }

        /** The world tick of its first entry. */
        public long base() {
            return base;
        }

        public int launches() {
            return launches;
        }

        public int clears() {
            return clears;
        }

        /** The launched cells, packed relative to the nexus centre. */
        public int[] launchCells() {
            return Arrays.copyOf(launchCells, launches);
        }

        /** The part code of each launch. */
        public byte[] launchParts() {
            return Arrays.copyOf(launchParts, launches);
        }

        /** The flight of each launch in ticks. */
        public byte[] launchFlights() {
            return Arrays.copyOf(launchFlights, launches);
        }

        /** The tick each launch left the core, relative to {@link #base()}. */
        public byte[] launchOffsets() {
            return Arrays.copyOf(launchOffsets, launches);
        }

        /** The cleared cells, packed relative to the nexus centre. */
        public int[] clearCells() {
            return Arrays.copyOf(clearCells, clears);
        }

        /** The block of each cleared cell as {@code blockId << 4 | meta}. */
        public int[] clearBlocks() {
            return Arrays.copyOf(clearBlocks, clears);
        }
    }

    private final List<Page> pages = new ArrayList<>();

    /** Adds a launch: the packed cell, its part code, its flight in ticks, the world tick it left the core. */
    public void launch(int packedCell, int part, int flightTicks, long worldTick) {
        Page p = page(true, worldTick);
        if (p == null) return;
        p.stamp(worldTick);
        p.launchCells[p.launches] = packedCell;
        p.launchParts[p.launches] = (byte) part;
        p.launchFlights[p.launches] = (byte) Math.max(0, Math.min(127, flightTicks));
        p.launchOffsets[p.launches] = (byte) Math.max(0, Math.min(127, worldTick - p.base));
        p.launches++;
    }

    /** Adds a cleared cell: the packed cell and the block that was there as {@code blockId << 4 | meta}. */
    public void clear(int packedCell, int blockIdMeta, long worldTick) {
        Page p = page(false, worldTick);
        if (p == null) return;
        p.stamp(worldTick);
        p.clearCells[p.clears] = packedCell;
        p.clearBlocks[p.clears] = blockIdMeta;
        p.clears++;
    }

    /**
     * The page the next launch (or clear) goes on: the last one while it has room for it and its launch offsets can
     * still be told apart (within 127 ticks of its first entry), else a new one; null when the batch is at
     * {@link #MAX_PAGES}.
     */
    private Page page(boolean launch, long worldTick) {
        Page last = pages.isEmpty() ? null : pages.get(pages.size() - 1);
        if (last != null && (launch ? last.launches : last.clears) < MAX
            && (last.base < 0 || worldTick - last.base <= 127)) return last;
        if (pages.size() >= MAX_PAGES) return null;
        Page p = new Page();
        pages.add(p);
        return p;
    }

    public boolean isEmpty() {
        for (Page p : pages) if (p.launches > 0 || p.clears > 0) return false;
        return true;
    }

    /** Whether a page filled up (the builder sends the batch early then, so its pages stay few). */
    public boolean full() {
        if (pages.size() > 1) return true;
        return !pages.isEmpty() && (pages.get(0).launches >= MAX || pages.get(0).clears >= MAX);
    }

    /** Empties it for the next ticks. */
    public void reset() {
        pages.clear();
    }

    /** Its pages in order, one packet each (read-only). */
    public List<Page> pages() {
        return Collections.unmodifiableList(pages);
    }

    /** The world tick of its first entry; -1 when empty. */
    public long base() {
        return pages.isEmpty() ? -1 : pages.get(0).base;
    }

    /** Launches on all its pages. */
    public int launches() {
        int n = 0;
        for (Page p : pages) n += p.launches;
        return n;
    }

    /** Clears on all its pages. */
    public int clears() {
        int n = 0;
        for (Page p : pages) n += p.clears;
        return n;
    }
}
