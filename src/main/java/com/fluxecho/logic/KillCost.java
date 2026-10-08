package com.fluxecho.logic;

/**
 * What one echoed kill costs: EU in proportion to the mob's health (a boss many times more), spread over a cycle no
 * shorter than the minimum and never above the machine's voltage per tick.
 */
public final class KillCost {

    public final long eu;
    public final int ticks;
    public final int eut;

    private KillCost(long eu, int ticks, int eut) {
        this.eu = eu;
        this.ticks = ticks;
        this.eut = eut;
    }

    /**
     * @param health      the mob's max health (at least 1 is charged)
     * @param euPerHealth EU per point of health
     * @param multiplier  1, or the boss multiplier
     * @param maxEut      the machine's voltage
     * @param minTicks    the shortest cycle
     */
    public static KillCost of(double health, int euPerHealth, int multiplier, int maxEut, int minTicks) {
        long eu = (long) Math.ceil(Math.max(1, health) * euPerHealth) * Math.max(1, multiplier);
        long ticks = Math.max(Math.max(1, minTicks), (eu + maxEut - 1) / maxEut);
        int t = (int) Math.min(Integer.MAX_VALUE, ticks);
        int eut = (int) Math.max(1, (eu + t - 1) / t);
        return new KillCost(eu, t, eut);
    }
}
