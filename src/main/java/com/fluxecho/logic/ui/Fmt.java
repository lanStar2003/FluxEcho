package com.fluxecho.logic.ui;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * How the UI writes numbers (ui/FINAL.md section 1.9): SI with three significant digits ({@code 512}, {@code 2.05k},
 * {@code 30.7k}, {@code 1.97M}, {@code 2.10G}), whole percentages with one decimal below 10%, countdowns as
 * {@code 14:20} or {@code 1:57:03}, game time as {@code D3 · 23:40} with midnight at tick 18000, slot counts in four
 * glyphs. A number and its unit are separate strings: the number is FluxMono N1 / N2, the unit FluxMono at caption
 * size,
 * 2 px apart on one baseline (1.4.3 rule 2). Everything a number format makes for the large sizes is drawn from the
 * large FluxMono glyphs ({@link FluxMono#inLarge}). Pure functions, no Minecraft classes.
 */
public final class Fmt {

    private Fmt() {}

    /** Units (FluxMono, never translated). */
    public static final String EU = "EU", EU_T = "EU/t", FC = "FC", FC_S = "FC/s";
    /** The gap between a number and its unit, px. */
    public static final int UNIT_GAP = 2;

    private static final String PREFIXES = "kMGT";
    private static final MathContext THREE = new MathContext(3, RoundingMode.HALF_UP);

    /** A number with its unit, kept apart because they are drawn at different sizes. */
    public static final class Value {

        public final String number, unit;

        Value(String number, String unit) {
            this.number = number;
            this.unit = unit;
        }
    }

    public static Value value(double v, String unit) {
        return new Value(si(v), unit);
    }

    /** SI with three significant digits. */
    public static String si(long v) {
        return si(BigDecimal.valueOf(v));
    }

    /** SI with three significant digits; NaN and infinities read 0. */
    public static String si(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "0";
        return si(BigDecimal.valueOf(v));
    }

    private static String si(BigDecimal v) {
        if (v.signum() == 0) return "0";
        String sign = v.signum() < 0 ? "-" : "";
        BigDecimal a = v.abs(), thousand = BigDecimal.valueOf(1000);
        if (a.compareTo(thousand) < 0) {
            // below 1000 an integer is written as it is, a fraction to three significant digits
            if (a.stripTrailingZeros()
                .scale() <= 0) return sign + a.toBigInteger();
            BigDecimal r = a.round(THREE);
            if (r.compareTo(thousand) < 0) return sign + three(r);
        }
        for (int p = 0; p < PREFIXES.length(); p++) {
            BigDecimal r = a.movePointLeft(3 * (p + 1))
                .round(THREE);
            // a value that rounds up to 1000 moves on to the next prefix (999,500 is 1.00M)
            if (r.compareTo(thousand) < 0) return sign + three(r) + PREFIXES.charAt(p);
        }
        // past T the number simply grows
        return sign + a.movePointLeft(12)
            .setScale(0, RoundingMode.HALF_UP)
            .toPlainString() + "T";
    }

    /** A positive number already rounded to three significant digits, written with its trailing zeros. */
    private static String three(BigDecimal r) {
        int magnitude = r.precision() - r.scale() - 1; // floor(log10(r))
        return r.setScale(Math.max(0, 2 - magnitude), RoundingMode.HALF_UP)
            .toPlainString();
    }

    /**
     * A fraction as a percentage: whole numbers, one decimal below 10%, rounded down so that a bar only reads 100%
     * when it is done; clamped to 0-100%.
     */
    public static String percent(double fraction) {
        if (Double.isNaN(fraction) || fraction <= 0) return "0%";
        if (fraction >= 1) return "100%";
        long tenths = (long) Math.floor(fraction * 1000 + 1e-9);
        if (tenths < 100) return tenths / 10 + "." + tenths % 10 + "%";
        return tenths / 10 + "%";
    }

    /** A countdown: {@code m:ss} under an hour, {@code h:mm:ss} from an hour; negative reads 0:00. */
    public static String clock(long seconds) {
        long s = Math.max(0, seconds);
        long h = s / 3600, m = s / 60 % 60, sec = s % 60;
        if (h > 0) return h + ":" + two(m) + ":" + two(sec);
        return s / 60 + ":" + two(sec);
    }

    /** A countdown given in ticks (20 a second, rounded down). */
    public static String clockTicks(long ticks) {
        return clock(ticks / 20);
    }

    /**
     * Game time {@code D3 · 23:40}: tick 0 is 06:00 of day 1 and midnight is tick 18000, where the day number turns
     * over (1.9). Minutes round down.
     */
    public static String gameTime(long worldTime) {
        long t = Math.max(0, worldTime) + 6000; // ticks since the midnight before day 1
        long day = t / 24000 + 1, inDay = t % 24000;
        long hour = inDay / 1000, minute = inDay % 1000 * 60 / 1000;
        return "D" + day + " · " + two(hour) + ":" + two(minute);
    }

    /**
     * A stack count for an 18 px slot: at most four glyphs ({@code 999}, {@code 1.2k}, {@code 999k}, {@code 1.0M},
     * ...), rounded down; negative reads 0.
     */
    public static String count(long n) {
        if (n <= 0) return "0";
        if (n < 1000) return Long.toString(n);
        String units = "kMGTPE";
        long scale = 1000;
        for (int i = 0; i < units.length(); i++) {
            long whole = n / scale;
            if (whole < 10) return whole + "." + n / (scale / 10) % 10 + units.charAt(i);
            if (whole < 1000) return whole + String.valueOf(units.charAt(i));
            if (i + 1 < units.length()) scale *= 1000;
        }
        return n / scale + String.valueOf(units.charAt(units.length() - 1));
    }

    /** A building's fixed order number, two digits ({@code 07}). */
    public static String order(int n) {
        return two(n);
    }

    /** Roman numerals 1-39 (phases, tiers); anything else is written in digits. */
    public static String roman(int n) {
        if (n < 1 || n > 39) return Integer.toString(n);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n / 10; i++) b.append('X');
        String[] ones = { "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX" };
        return b.append(ones[n % 10])
            .toString();
    }

    private static String two(long v) {
        return v < 10 ? "0" + v : Long.toString(v);
    }
}
