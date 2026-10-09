package com.fluxdepths.fluid;

import com.fluxdepths.shard.DrillHead;

/**
 * The three fluid pumps. Each second one gives a share of the imprinted chunk's pristine amount (what GT's drilling
 * rigs get from it per operation before it runs dry), never drains the chunk itself, and wears a drill head. GT's own
 * drilling rigs, which cover many chunks at once, take over from EV.
 */
public enum PumpTier {

    // GT tier, EU/t, first drill head, default share of the pristine amount per second
    LV(1, 24, DrillHead.STEEL, 0.1),
    MV(2, 96, DrillHead.ALUMINIUM, 0.25),
    HV(3, 384, DrillHead.STAINLESS_STEEL, 0.5);

    /** Ticks per cycle: one second. */
    public static final int CYCLE = 20;
    /** Seconds of pumping per ore a drill head lasts in a shard collector: a steel head runs an hour on average. */
    public static final int SECONDS_PER_ORE = 15;

    public final int gtTier;
    public final int energy;
    public final DrillHead minHead;
    public final double defaultShare;

    PumpTier(int gtTier, int energy, DrillHead minHead, double defaultShare) {
        this.gtTier = gtTier;
        this.energy = energy;
        this.minHead = minHead;
        this.defaultShare = defaultShare;
    }

    public String key() {
        return name().toLowerCase();
    }

    /** Seconds one drill head keeps a pump running, on average. */
    public static int seconds(DrillHead head) {
        return head.ores * SECONDS_PER_ORE;
    }

    public boolean takes(DrillHead head) {
        return head != null && head.ordinal() >= minHead.ordinal();
    }
}
