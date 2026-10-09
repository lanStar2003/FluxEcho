package com.fluxdepths.shard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * The ores of one vein and how often each comes out. Same weights as GTNH's void miner gives a vein: primary and
 * secondary ore 1 each, in-between and sporadic ore 1/8 each; an ore that fills two roles counts twice.
 */
public final class Mix<T> {

    public static final double MAIN = 1, MINOR = 1.0 / 8;

    private final List<T> ores = new ArrayList<>();
    private final List<Double> weights = new ArrayList<>();
    private double total;

    /**
     * {@code same} decides whether two ores are the same (item stacks have no useful equals). Null ores (a vein layer
     * without one) are skipped.
     */
    public static <T> Mix<T> ofVein(T primary, T secondary, T between, T sporadic, BiPredicate<T, T> same) {
        Mix<T> m = new Mix<>();
        m.add(primary, MAIN, same);
        m.add(secondary, MAIN, same);
        m.add(between, MINOR, same);
        m.add(sporadic, MINOR, same);
        return m;
    }

    private void add(T ore, double weight, BiPredicate<T, T> same) {
        if (ore == null) return;
        for (int i = 0; i < ores.size(); i++) {
            if (same.test(ores.get(i), ore)) {
                weights.set(i, weights.get(i) + weight);
                total += weight;
                return;
            }
        }
        ores.add(ore);
        weights.add(weight);
        total += weight;
    }

    public boolean isEmpty() {
        return ores.isEmpty();
    }

    public int size() {
        return ores.size();
    }

    public List<T> ores() {
        return Collections.unmodifiableList(ores);
    }

    /** Share of the ore at {@code i}, 0..1. */
    public double share(int i) {
        return total <= 0 ? 0 : weights.get(i) / total;
    }

    /** The ore for a uniform random number {@code r} in [0, 1). */
    public T pick(double r) {
        if (ores.isEmpty()) return null;
        double at = r * total;
        for (int i = 0; i < ores.size(); i++) {
            at -= weights.get(i);
            if (at < 0) return ores.get(i);
        }
        return ores.get(ores.size() - 1);
    }
}
