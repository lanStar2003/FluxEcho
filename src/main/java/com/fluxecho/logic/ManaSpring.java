package com.fluxecho.logic;

/**
 * The Mana Echo Spring's numbers for a GT voltage tier (1 LV to 6 LuV), set by the circuit in its core. Each tier
 * draws four times the mana of the one below for four times the EU, and a petal resonates four times as much mana,
 * so petals go at the same pace at every tier. The reach grows too: a block sideways per tier, a block up and down
 * every second tier.
 */
public final class ManaSpring {

    /** GT's voltage of each tier, ULV to LuV. */
    private static final long[] VOLTAGE = { 8, 32, 128, 512, 2048, 8192, 32768 };
    public static final int MIN_TIER = 1, MAX_TIER = 6;

    private ManaSpring() {}

    public static boolean valid(int tier) {
        return tier >= MIN_TIER && tier <= MAX_TIER;
    }

    public static long voltage(int tier) {
        return valid(tier) ? VOLTAGE[tier] : 0;
    }

    private static long scale(long lv, int tier) {
        return valid(tier) ? lv << 2 * (tier - 1) : 0;
    }

    /** Mana a tick; {@code lv} is the LV rate. */
    public static int manaPerTick(int lv, int tier) {
        return (int) Math.min(Integer.MAX_VALUE / 64, scale(lv, tier));
    }

    public static long euPerTick(int lv, int euPerMana, int tier) {
        return (long) manaPerTick(lv, tier) * euPerMana;
    }

    /** Mana one petal is worth; {@code lv} is its LV worth. */
    public static long manaPerPetal(long lv, int tier) {
        return scale(lv, tier);
    }

    public static int range(int base, int tier) {
        return valid(tier) ? base + tier - 1 : base;
    }

    public static int height(int base, int tier) {
        return valid(tier) ? base + (tier - 1) / 2 : base;
    }

    /** Petals a minute while it works; the same at every tier. */
    public static double petalsPerMinute(int lvManaPerTick, long lvPerPetal) {
        return lvPerPetal <= 0 ? 0 : lvManaPerTick * 20.0 * 60 / lvPerPetal;
    }

    /** Petals to eat so that {@code credit} plus them covers {@code amount}. */
    public static int petalsNeeded(long credit, long amount, long perPetal) {
        if (amount <= credit) return 0;
        long per = Math.max(1, perPetal);
        return (int) Math.min(Integer.MAX_VALUE, (amount - credit + per - 1) / per);
    }
}
