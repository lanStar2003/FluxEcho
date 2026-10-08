package com.fluxecho.logic;

/**
 * The Blood Echo's numbers: how fast an orb lets it make LP, how much meat that eats, and how much LP an altar can
 * take without spilling (the altar multiplies what it is given by its self-sacrifice runes).
 */
public final class BloodRates {

    private BloodRates() {}

    /** LP per tick for an orb level, from the table (level 1 is the first entry); levels past the end use the last. */
    public static int lpPerTick(int[] table, int orbLevel) {
        if (table.length == 0) return 0;
        int i = Math.max(1, Math.min(table.length, orbLevel)) - 1;
        return Math.max(0, table[i]);
    }

    /** Meat to take so the credit covers {@code amount} LP. */
    public static int meatNeeded(int credit, int amount, int lpPerMeat) {
        if (amount <= credit) return 0;
        return (amount - credit + lpPerMeat - 1) / Math.max(1, lpPerMeat);
    }

    /**
     * LP to hand an altar so that, multiplied by {@code 1 + selfSacrificeMultiplier}, it still fits into its free room.
     */
    public static int altarShare(int buffer, int room, float selfSacrificeMultiplier) {
        if (buffer <= 0 || room <= 0) return 0;
        int fits = (int) Math.floor(room / (1.0 + Math.max(0f, selfSacrificeMultiplier)));
        return Math.max(0, Math.min(buffer, Math.max(1, fits)));
    }

    /** Parses the config list ("4, 8, 16"); bad entries count as 0. */
    public static int[] parse(String[] entries) {
        int[] out = new int[entries.length];
        for (int i = 0; i < entries.length; i++) {
            try {
                out[i] = Math.max(0, Integer.parseInt(entries[i].trim()));
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }
}
