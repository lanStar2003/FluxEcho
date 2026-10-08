package com.fluxecho.logic;

import java.util.Locale;

/** Short numbers for the Flux Vis Pedestal's hologram, where there is little room. */
public final class Compact {

    private static final String UNITS = "KMGTPE";

    private Compact() {}

    /** 950, 1.20K, 45.0M, 400K: three significant digits from a thousand on. */
    public static String si(long v) {
        if (v < 0) return "-" + si(v == Long.MIN_VALUE ? Long.MAX_VALUE : -v);
        if (v < 1000) return Long.toString(v);
        double d = v;
        int unit = -1;
        while (d >= 999.5 && unit < UNITS.length() - 1) {
            d /= 1000;
            unit++;
        }
        String f = d >= 99.95 ? "%.0f%c" : d >= 9.995 ? "%.1f%c" : "%.2f%c";
        return String.format(Locale.ROOT, f, d, UNITS.charAt(unit));
    }

    /** Centivis per tick as vis per second: 25 is "5", 30 is "6", 33 is "6.6". */
    public static String visPerSecond(long centivisPerTick) {
        long tenths = centivisPerTick * 20 / 10;
        return tenths % 10 == 0 ? si(tenths / 10) : String.format(Locale.ROOT, "%.1f", tenths / 10.0);
    }

    /** Centivis as whole vis, rounded down. */
    public static String vis(long centivis) {
        return si(Math.max(0, centivis) / 100);
    }

    /** {@code part / whole} clamped to 0..1; 0 when there is no whole. */
    public static float fraction(long part, long whole) {
        if (whole <= 0 || part <= 0) return 0f;
        return part >= whole ? 1f : (float) ((double) part / whole);
    }
}
