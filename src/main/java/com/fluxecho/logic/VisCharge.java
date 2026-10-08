package com.fluxecho.logic;

import java.util.Arrays;

/**
 * One cycle of the Vis Charger: how much vis (in centivis) goes into each primal, and the EU it takes. A cycle is
 * capped at {@code maxTicks} at {@code maxEut}; when the wand holds more than that, the budget is shared out evenly
 * (the emptiest primals are not starved by the fullest), and the next cycle tops it up.
 */
public final class VisCharge {

    public final int[] add;
    public final int ticks, eut;
    public final long eu;

    private VisCharge(int[] add, int ticks, int eut, long eu) {
        this.add = add;
        this.ticks = ticks;
        this.eut = eut;
        this.eu = eu;
    }

    public long total() {
        long t = 0;
        for (int a : add) t += a;
        return t;
    }

    /**
     * @param room     centivis each primal still takes (0 for a full or not yet learned one)
     * @param euPerCv  EU per centivis
     * @param maxEut   EU/t the machine may draw
     * @param maxTicks longest cycle
     */
    public static VisCharge plan(int[] room, int euPerCv, int maxEut, int maxTicks) {
        long budget = (long) maxEut * maxTicks / Math.max(1, euPerCv);
        long total = 0;
        for (int r : room) total += Math.max(0, r);
        int[] add = new int[room.length];
        if (total <= budget) {
            for (int i = 0; i < room.length; i++) add[i] = Math.max(0, room[i]);
        } else {
            Integer[] order = new Integer[room.length];
            for (int i = 0; i < order.length; i++) order[i] = i;
            Arrays.sort(order, (a, b) -> Integer.compare(room[a], room[b]));
            long left = budget;
            for (int k = 0; k < order.length; k++) {
                int i = order[k];
                long share = left / (order.length - k);
                add[i] = (int) Math.min(Math.max(0, room[i]), share);
                left -= add[i];
            }
        }
        long sum = 0;
        for (int a : add) sum += a;
        long eu = sum * Math.max(1, euPerCv);
        int ticks = (int) Math.max(1, (eu + maxEut - 1) / maxEut);
        int eut = (int) Math.max(1, (eu + ticks - 1) / ticks);
        return new VisCharge(add, ticks, eut, eu);
    }
}
