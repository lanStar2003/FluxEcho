package com.fluxecho.logic;

import java.util.List;
import java.util.function.BiPredicate;

/**
 * Which slots to take items from to pay a list of costs ({@link ResearchTree.Cost}): each cost from the slots that
 * match it, in slot order, no slot counted twice. Used for manifestation inputs and research items.
 */
public final class CostPlan {

    private CostPlan() {}

    /**
     * @param costs   what to pay
     * @param counts  items in each slot (0 for empty)
     * @param matches whether the slot's item pays the cost
     * @return how many to take from each slot, or null when the slots do not hold enough
     */
    public static int[] plan(List<ResearchTree.Cost> costs, int[] counts,
        BiPredicate<ResearchTree.Cost, Integer> matches) {
        int[] left = counts.clone();
        int[] take = new int[counts.length];
        for (ResearchTree.Cost c : costs) {
            int need = c.count;
            for (int i = 0; i < left.length && need > 0; i++) {
                if (left[i] <= 0 || !matches.test(c, i)) continue;
                int n = Math.min(need, left[i]);
                left[i] -= n;
                take[i] += n;
                need -= n;
            }
            if (need > 0) return null;
        }
        return take;
    }
}
