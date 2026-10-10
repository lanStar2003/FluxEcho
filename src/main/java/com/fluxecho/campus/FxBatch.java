package com.fluxecho.campus;

import java.util.Arrays;

import com.fluxecho.logic.FxCodec;

/**
 * What the campus builder did over the last few ticks that players nearby should see: the cells it launched (each
 * flies from the core to its cell and prints in when it lands) and the cells it cleared (break particles). The builder
 * fills it as it works and hands it to {@link CampusNet#sendFx} every four ticks when it is not empty, then
 * {@link #reset()}s it. Cells are packed with {@link FxCodec} relative to the nexus centre; at most {@link #MAX} of
 * each kind are kept per batch (the rest is dropped: these are effects only).
 * <p>
 * Times: {@link #base()} is the world tick of the batch's first entry. A launch left the core at
 * {@code base + launchOffsets()[i]} and lands at {@code base + launchOffsets()[i] + launchFlights()[i]}.
 */
public final class FxBatch {

    /** The most launches, and the most clears, in one batch. */
    public static final int MAX = 64;

    private final int[] launchCells = new int[MAX], clearCells = new int[MAX], clearBlocks = new int[MAX];
    private final byte[] launchParts = new byte[MAX], launchFlights = new byte[MAX], launchOffsets = new byte[MAX];
    private int launches, clears;
    private long base = -1;

    /** Adds a launch: the packed cell, its part code, its flight in ticks, the world tick it left the core. */
    public void launch(int packedCell, int part, int flightTicks, long worldTick) {
        if (launches >= MAX) return;
        stamp(worldTick);
        launchCells[launches] = packedCell;
        launchParts[launches] = (byte) part;
        launchFlights[launches] = (byte) Math.max(0, Math.min(127, flightTicks));
        launchOffsets[launches] = (byte) Math.max(0, Math.min(127, worldTick - base));
        launches++;
    }

    /** Adds a cleared cell: the packed cell and the block that was there as {@code blockId << 4 | meta}. */
    public void clear(int packedCell, int blockIdMeta, long worldTick) {
        if (clears >= MAX) return;
        stamp(worldTick);
        clearCells[clears] = packedCell;
        clearBlocks[clears] = blockIdMeta;
        clears++;
    }

    private void stamp(long worldTick) {
        if (base < 0) base = worldTick;
    }

    public boolean isEmpty() {
        return launches == 0 && clears == 0;
    }

    /** Whether one of its lists is full (the builder sends it early then). */
    public boolean full() {
        return launches >= MAX || clears >= MAX;
    }

    /** Empties it for the next ticks. */
    public void reset() {
        launches = clears = 0;
        base = -1;
    }

    /** The world tick of its first entry; -1 when empty. */
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
