package com.fluxecho.logic;

/**
 * Where an echo machine's item slots go, in its GUI and on its NEI page: inputs from the left, outputs against the
 * right, one column for up to two slots, two for up to four, three beyond, centred in the height of the work area.
 */
public final class SlotLayout {

    public static final int SLOT = 18;

    private SlotLayout() {}

    /**
     * Top left corners of {@code n} slots.
     *
     * @param x            the left edge of the first column, or with {@code rightAligned} the right edge of the last
     * @param areaTop      top of the area they are centred in
     * @param areaHeight   its height
     * @param rightAligned whether {@code x} is the right edge
     */
    public static int[][] grid(int n, int x, int areaTop, int areaHeight, boolean rightAligned) {
        int cols = columns(n), rows = rows(n);
        int[][] at = new int[n][];
        int top = areaTop + (areaHeight - rows * SLOT) / 2;
        for (int i = 0; i < n; i++) {
            int col = i % cols, row = i / cols;
            at[i] = new int[] { rightAligned ? x - (cols - col) * SLOT : x + col * SLOT, top + row * SLOT };
        }
        return at;
    }

    /** How many columns {@code n} slots take. */
    public static int columns(int n) {
        return n <= 2 ? 1 : n <= 4 ? 2 : 3;
    }

    /** How many rows {@code n} slots take. */
    public static int rows(int n) {
        int cols = columns(n);
        return (n + cols - 1) / cols;
    }

    /** The right edge of the rightmost slot. */
    public static int right(int[][] slots) {
        int r = Integer.MIN_VALUE;
        for (int[] s : slots) r = Math.max(r, s[0] + SLOT);
        return r;
    }

    /** The left edge of the leftmost slot. */
    public static int left(int[][] slots) {
        int l = Integer.MAX_VALUE;
        for (int[] s : slots) l = Math.min(l, s[0]);
        return l;
    }
}
