package com.fluxecho.logic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * How many primal units an aspect is worth: a primal is 1, a compound the sum of its two components, all the way
 * down. This is what synthesizing one essentia of it costs. Aspects are named by their tags; an unknown component
 * counts as 1. A cycle or a chain deeper than {@link #MAX_DEPTH} (broken addon data) costs {@link #CAP}.
 */
public final class AspectUnits {

    public static final int MAX_DEPTH = 16;
    public static final int CAP = 1 << 16;

    private final Map<String, String[]> components;
    private final Map<String, Integer> memo = new HashMap<>();

    /** @param components tag to its components' tags; a primal maps to an empty array or is absent */
    public AspectUnits(Map<String, String[]> components) {
        this.components = components;
    }

    public int of(String tag) {
        return of(tag, new HashSet<>(), 0);
    }

    private int of(String tag, Set<String> path, int depth) {
        Integer known = memo.get(tag);
        if (known != null) return known;
        String[] parts = components.get(tag);
        if (parts == null || parts.length == 0) return 1;
        if (depth >= MAX_DEPTH || !path.add(tag)) return CAP;
        long sum = 0;
        for (String p : parts) sum += of(p, path, depth + 1);
        path.remove(tag);
        int units = (int) Math.min(CAP, sum);
        memo.put(tag, units);
        return units;
    }
}
