package com.fluxecho.logic;

/**
 * Stable hashing for seeded, deterministic layouts (the campus paving, its wells and lobes). The same inputs give the
 * same numbers on the client and the server and across restarts, so both sides compute the same plan from the
 * controller's position alone.
 */
public final class Mix {

    private Mix() {}

    /** A splitmix-style chain over the values, starting from {@code seed}. */
    public static long hash(long seed, long... v) {
        long x = seed;
        for (long a : v) {
            x = (x ^ (a & 0xFFFFFFFFL)) * 0x9E3779B97F4A7C15L;
            x ^= x >>> 31;
        }
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 29;
        return x;
    }

    /** A number in [0, 1) from the values. */
    public static double unit(long seed, long... v) {
        return (hash(seed, v) >>> 11) / (double) (1L << 53);
    }

    /** The seed of a campus: its controller's position. */
    public static long seed(int x, int y, int z) {
        return hash(0x5EEDF1E5L, x, y, z);
    }
}
