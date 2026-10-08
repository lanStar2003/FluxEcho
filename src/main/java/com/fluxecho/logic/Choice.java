package com.fluxecho.logic;

/**
 * Picking one of several candidates with a programmed circuit: a single candidate needs no circuit; with more,
 * circuit N picks the N-th (candidates come in a fixed order, so the same circuit keeps picking the same one).
 */
public final class Choice {

    private Choice() {}

    /**
     * Index of the chosen candidate, or -1 when none (no candidates, or a circuit is needed and missing or too big).
     */
    public static int pick(int candidates, int circuit) {
        if (candidates <= 0) return -1;
        if (candidates == 1) return 0;
        return circuit >= 1 && circuit <= candidates ? circuit - 1 : -1;
    }
}
