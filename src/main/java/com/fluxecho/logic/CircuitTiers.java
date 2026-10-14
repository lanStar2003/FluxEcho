package com.fluxecho.logic;

/**
 * The circuit names research and manifestation costs are written with, read as GT voltage tiers (0.16.0). A cost
 * names a tier's circuits by the tier ({@code ore:circuitEV}, {@code ore:circuitLuV}), which reads well in the data,
 * but GT 5.09.51 registers its circuits in the ore dictionary under the old circuit materials ({@code circuitData} for
 * EV, {@code circuitElite} for IV, {@code circuitMaster} for LuV), and no mod in GTNH 2.8.4 registers
 * {@code circuitLuV}: a cost looked up by its literal name could never be paid. The costs stay as they are (they are
 * data, pinned by the tests); the nexus translates a tier's name to GT's own ore name through this table
 * ({@code Costs.oreName}).
 */
public final class CircuitTiers {

    /** The names a cost may give a tier's circuits, by GT voltage tier: index 0 ULV to 8 UV. */
    private static final String[] NAMES = { "circuitULV", "circuitLV", "circuitMV", "circuitHV", "circuitEV",
        "circuitIV", "circuitLuV", "circuitZPM", "circuitUV" };

    private CircuitTiers() {}

    /**
     * The GT voltage tier a cost's ore name means (3 for {@code circuitHV}, 6 for {@code circuitLuV}), -1 for any other
     * name.
     */
    public static int tierOf(String oreName) {
        for (int t = 0; t < NAMES.length; t++) if (NAMES[t].equals(oreName)) return t;
        return -1;
    }

    /** How many tiers the table knows (ULV to UV). */
    public static int count() {
        return NAMES.length;
    }

    /** The name a cost gives a tier's circuits. */
    public static String name(int tier) {
        return NAMES[tier];
    }
}
