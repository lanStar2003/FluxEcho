package com.fluxdepths.shard;

import net.minecraft.nbt.NBTTagCompound;

/**
 * What a collector keeps between cycles: whose turn it is and how many ores it made; and for Waila, the GUI and the
 * hologram, what it is doing.
 */
public final class ShardState {

    public enum Status {
        IDLE,
        WORKING,
        NO_IMPRINT,
        WRONG_WORLD,
        NO_HEAD,
        NO_FLUID,
        OUTPUT_FULL,
        DISABLED,
        NO_POWER
    }

    /** Average ores the drill head in use lasts for (0: none), not saved. */
    public int headUses;
    /** Imprints of this world in use at the last cycle, not saved. */
    public int imprints;
    /** Index of the imprint whose turn is next. */
    public int next;
    /** Ores condensed since the collector was placed. */
    public long produced;
    public Status status = Status.IDLE;
    /** Vein of the last ore. */
    public String lastVein = "";

    public void save(NBTTagCompound t) {
        t.setInteger("fdNext", next);
        t.setLong("fdProduced", produced);
        t.setString("fdVein", lastVein);
    }

    public void load(NBTTagCompound t) {
        next = Math.max(0, t.getInteger("fdNext"));
        produced = Math.max(0, t.getLong("fdProduced"));
        lastVein = t.getString("fdVein");
    }

    public static Status status(int ordinal) {
        Status[] all = Status.values();
        return all[Math.max(0, Math.min(all.length - 1, ordinal))];
    }
}
