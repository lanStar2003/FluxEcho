package com.fluxecho.logic;

import java.util.Arrays;

/**
 * One step of the Flux Vis Pedestal's live charging: how much vis (in centivis) flows into each primal of a wand, and
 * the EU it takes. Each primal takes at most {@code rate}; when the stored EU does not pay for all of it, what it does
 * pay for is shared out evenly, so the emptiest primals are not starved by the fullest.
 */
public final class VisFlow {

    public final int[] add;
    public final long eu;

    private VisFlow(int[] add, long eu) {
        this.add = add;
        this.eu = eu;
    }

    public long total() {
        long t = 0;
        for (int a : add) t += a;
        return t;
    }

    /**
     * @param room    centivis each primal still takes (0 for a full one)
     * @param rate    most centivis one primal takes in this step
     * @param euPerCv EU per centivis
     * @param euHave  EU there is to spend
     */
    public static VisFlow step(int[] room, int rate, int euPerCv, long euHave) {
        int cost = Math.max(1, euPerCv);
        int[] want = new int[room.length];
        long total = 0;
        for (int i = 0; i < room.length; i++) {
            want[i] = Math.max(0, Math.min(room[i], rate));
            total += want[i];
        }
        long budget = Math.max(0, euHave) / cost;
        int[] add = new int[room.length];
        if (total <= budget) {
            add = want;
        } else {
            Integer[] order = new Integer[room.length];
            for (int i = 0; i < order.length; i++) order[i] = i;
            Arrays.sort(order, (a, b) -> Integer.compare(want[a], want[b]));
            long left = budget;
            for (int k = 0; k < order.length; k++) {
                int i = order[k];
                long share = left / (order.length - k);
                add[i] = (int) Math.min(want[i], share);
                left -= add[i];
            }
        }
        long sum = 0;
        for (int a : add) sum += a;
        return new VisFlow(add, sum * cost);
    }

    /** Centivis per primal per tick: the base flow and what each extraction module adds. */
    public static int rate(int base, int perModule, int modules) {
        long r = (long) Math.max(0, base) + (long) Math.max(0, perModule) * Math.max(0, modules);
        return (int) Math.min(Integer.MAX_VALUE, r);
    }
}
