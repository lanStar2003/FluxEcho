package com.fluxdepths.fluid;

/** Litres a fluid pump gives per cycle, and the config list of shares. */
public final class PumpRates {

    private PumpRates() {}

    /** The share of the pristine amount, rounded down; at least 1 L while there is any fluid. */
    public static int perCycle(int pristine, double share) {
        if (pristine <= 0 || share <= 0) return 0;
        double l = Math.floor(pristine * share);
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, l));
    }

    /** Parses the config's shares (LV, MV, HV); missing or bad entries fall back to the defaults. */
    public static double[] parse(String[] entries, double[] defaults) {
        double[] out = defaults.clone();
        for (int i = 0; i < out.length && entries != null && i < entries.length; i++) {
            try {
                double v = Double.parseDouble(entries[i].trim());
                if (v >= 0 && !Double.isNaN(v) && !Double.isInfinite(v)) out[i] = v;
            } catch (NumberFormatException | NullPointerException ignored) {
                // keep the default
            }
        }
        return out;
    }
}
