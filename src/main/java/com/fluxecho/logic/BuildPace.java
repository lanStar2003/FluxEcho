package com.fluxecho.logic;

/**
 * The pace of the campus builder: a budget that fills every tick and is spent on launches, so the build rate is set by
 * config ({@code buildBlocksPerTick}) times the pace of the current stage, whatever the server's tick load. A single
 * step costs 1; a group (a whole shelf unit of 15 cells) costs its size and launches only when the budget covers all of
 * it, so a unit always appears at once. The budget is capped at {@link #CAP}, so a long stall never turns into a burst.
 * Clearing has its own budget ({@code buildClearPerTick}), so digging never starves placing or the other way round.
 * <p>
 * One instance belongs to a running job; the static helpers give the stage paces, the flight time of a launch and an
 * estimate of the time left. Budgets are doubles: rates such as 0.25 per tick add up exactly.
 */
public final class BuildPace {

    /** The most budget either accumulator holds (more than the largest group, 15). */
    public static final double CAP = 24;
    /** The cost of one step. */
    public static final int STEP = 1;
    /** The shortest and longest flight of a launched cell, in ticks. */
    public static final int MIN_FLIGHT = 4, MAX_FLIGHT = 16;
    /** Slack for the comparisons, so rates such as 0.1 per tick never fall short by a rounding error. */
    private static final double EPS = 1e-9;

    private double budget, clearBudget;

    /** An empty accumulator. */
    public BuildPace() {}

    /** The placement budget now. Can be below zero after a group larger than the cap (see {@link #tryGroup}). */
    public double budget() {
        return budget;
    }

    /** The clearing budget now. */
    public double clearBudget() {
        return clearBudget;
    }

    /** Restores both budgets (from NBT). */
    public void load(double budget, double clearBudget) {
        this.budget = Math.min(CAP, budget);
        this.clearBudget = clearBudget;
    }

    /** Empties both budgets (a pause, a stage change the caller wants to start cleanly, a new job). */
    public void reset() {
        budget = 0;
        clearBudget = 0;
    }

    /**
     * One tick's refill of the placement budget: {@code perTick * stagePace}, capped at {@link #CAP}. Negative or
     * non-finite rates add nothing.
     */
    public void refill(double perTick, double stagePace) {
        double add = perTick * stagePace;
        if (!(add > 0) || Double.isInfinite(add)) return;
        budget = Math.min(CAP, budget + add);
    }

    /**
     * One tick's refill of the clearing budget: {@code clearPerTick}, capped at {@link #CAP} or at one tick's rate when
     * that is larger (so a configured rate above the cap still clears that many per tick).
     */
    public void refillClear(double clearPerTick) {
        if (!(clearPerTick > 0) || Double.isInfinite(clearPerTick)) return;
        clearBudget = Math.min(Math.max(CAP, clearPerTick), clearBudget + clearPerTick);
    }

    /** Whether one step can launch now. */
    public boolean canStep() {
        return budget + EPS >= STEP;
    }

    /** Spends {@link #STEP} for one launch and returns true, or returns false and spends nothing. */
    public boolean tryStep() {
        if (!canStep()) return false;
        budget -= STEP;
        return true;
    }

    /**
     * Whether a group of {@code size} cells can launch now: the budget covers the whole group. A group larger than the
     * cap could never be covered, so it waits for a full budget instead.
     */
    public boolean canGroup(int size) {
        if (size <= 0) return true;
        return budget + EPS >= Math.min(size, CAP);
    }

    /**
     * Spends {@code size} for a whole group and returns true, or returns false and spends nothing. Only a group larger
     * than the cap leaves the budget below zero; the debt is paid back by later refills, so the average rate holds.
     */
    public boolean tryGroup(int size) {
        if (!canGroup(size)) return false;
        if (size > 0) budget -= size;
        return true;
    }

    /** Whether one cell can be cleared now. */
    public boolean canClear() {
        return clearBudget + EPS >= STEP;
    }

    /** Spends one clear and returns true, or returns false and spends nothing. */
    public boolean tryClear() {
        if (!canClear()) return false;
        clearBudget -= STEP;
        return true;
    }

    /**
     * The pace of an establish stage, by the stage numbers of {@link BuildPlan}: the nexus core
     * ({@link BuildPlan#E_CORE}) is laid ceremonially at a quarter speed, the floor ({@link BuildPlan#E_FLOOR}) ripples
     * out at one and a half, everything else runs at one. Clearing uses its own budget and is not paced here.
     */
    public static double establishPace(int stage) {
        switch (stage) {
            case BuildPlan.E_CORE:
                return 0.25;
            case BuildPlan.E_FLOOR:
                return 1.5;
            default:
                return 1.0;
        }
    }

    /**
     * The pace of a module stage, by the stage numbers of {@link BuildPlan}: the floor ({@link BuildPlan#M_FLOOR}) at
     * one and a half, everything else at one (the shelf units of {@link BuildPlan#M_FIT} are gated by their group size
     * instead).
     */
    public static double modulePace(int stage) {
        return stage == BuildPlan.M_FLOOR ? 1.5 : 1.0;
    }

    /**
     * The flight of a launched cell in ticks: {@code clamp(4 + distance / 6, 4, 16)}, rounded down, where the distance
     * is in blocks from the nexus core to the cell. A negative or NaN distance flies the shortest time.
     */
    public static int flightTicks(double distance) {
        if (!(distance > 0)) return MIN_FLIGHT;
        double t = 4 + distance / 6;
        if (t >= MAX_FLIGHT) return MAX_FLIGHT;
        return Math.max(MIN_FLIGHT, (int) t);
    }

    /** {@link #flightTicks(double)} for a cell at {@code (dx, dy, dz)} from the launch point. */
    public static int flightTicks(double dx, double dy, double dz) {
        return flightTicks(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    /**
     * An estimate of the ticks left for {@code remaining} cells at {@code perTick * pace} launches per tick (rounded
     * up), without the last flight; 0 when nothing remains and -1 when the rate is not positive (the job cannot
     * progress).
     */
    public static long etaTicks(long remaining, double perTick, double pace) {
        if (remaining <= 0) return 0;
        double rate = perTick * pace;
        if (!(rate > 0) || Double.isInfinite(rate)) return -1;
        return (long) Math.ceil(remaining / rate - EPS);
    }

    /**
     * An estimate of the ticks left for a whole job: the sum over its stages of the remaining placements at
     * {@code perTick} times each stage's pace, plus the remaining clears at {@code clearPerTick}. Returns -1 when
     * something remains that its rate cannot progress.
     *
     * @param remainingByStage cells left to place in each stage, indexed by stage number
     * @param establish        whether the stages are establish stages ({@link #establishPace}) or module stages
     *                         ({@link #modulePace})
     */
    public static long etaTicks(int[] remainingByStage, boolean establish, double perTick, long clears,
        double clearPerTick) {
        long total = 0;
        if (remainingByStage != null) for (int stage = 0; stage < remainingByStage.length; stage++) {
            double pace = establish ? establishPace(stage) : modulePace(stage);
            long t = etaTicks(remainingByStage[stage], perTick, pace);
            if (t < 0) return -1;
            total += t;
        }
        long c = etaTicks(clears, clearPerTick, 1.0);
        if (c < 0) return -1;
        return total + c;
    }
}
