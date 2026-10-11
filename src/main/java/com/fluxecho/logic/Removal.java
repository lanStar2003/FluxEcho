package com.fluxecho.logic;

/**
 * How the campus builder takes the block in a cell down (0.10.1), apart from the world: which way it goes, whether the
 * recycle store must have room first, and what is left to do once a player's break has run.
 * <p>
 * A block that is recycled is broken the way a player's break does it ({@code ItemInWorldManager.tryHarvestBlock}),
 * for the owner's fake player: {@code onBlockHarvested}, {@code removedByPlayer} (which may harvest later), then
 * {@code onBlockDestroyedByPlayer} and {@code harvestBlock}, whose drops (and whatever the block spills) are caught.
 * Its drops are never read beforehand, so nothing is counted twice; an ore that keeps its drops in its tile gives them
 * from the tile it cached when it was broken, as it does for a player.
 */
public final class Removal {

    /** The ways a cell is taken down. */
    public enum Way {
        /** It simply becomes air and gives nothing: a fluid, or any block when there is no player to break it. */
        PLAIN,
        /** One of our own loose blocks: it becomes air and is credited to the ledger as its part. */
        CREDIT,
        /** Anything else: broken as a player's break does, its drops and spills going to the recycle store. */
        HARVEST
    }

    /** What is left to do after a player's break has run on the cell. */
    public enum After {
        /** The cell is empty: done. */
        DONE,
        /**
         * Something is left that must simply become air: the block itself, which waited for a harvest that did not
         * remove it, or the fluid it left behind (ice leaves water). Setting it to air gives no drops.
         */
        CLEAR,
        /** The block would not go, or something else now stands there: the cell is refused and looked at later. */
        REFUSED
    }

    private Removal() {}

    /**
     * The way the cell is taken down: one of our own loose blocks ({@code ours}, known as a part) is credited, a fluid
     * (or any block when there is no player to break it) becomes air, everything else is harvested.
     */
    public static Way way(boolean fluid, boolean ours, boolean player) {
        if (ours) return Way.CREDIT;
        if (fluid || !player) return Way.PLAIN;
        return Way.HARVEST;
    }

    /**
     * Whether taking the cell down can put items in the recycle store, so the builder waits while the store is full:
     * only a harvest does. A fluid becomes air and one of our loose blocks is credited as a part; either goes ahead.
     */
    public static boolean needsRoom(boolean fluid, boolean ours) {
        return way(fluid, ours, true) == Way.HARVEST;
    }

    /**
     * What is left to do after the break, from what stands in the cell now: {@code removed} is what
     * {@code removedByPlayer} answered, {@code air} whether the cell is empty now, {@code same} whether the block that
     * was broken is still there and {@code fluid} whether what is there now is a fluid.
     */
    public static After after(boolean removed, boolean air, boolean same, boolean fluid) {
        if (air) return After.DONE;
        if (fluid && !same) return After.CLEAR;
        if (removed && same) return After.CLEAR;
        return After.REFUSED;
    }
}
