package com.fluxecho.logic;

/**
 * What the pavilions' look tests ask of a shape (0.13.0): how much room a drawn point has round it, so a test can hold
 * a drawing in a building's air and out of its blocks.
 */
final class LookCells {

    private LookCells() {}

    /**
     * How far the point {@code (x, y, z)} of a module's frame is from the nearest cell of its shape whose character is
     * not one of {@code open} (blocks; outside the shape's box counts as open), looking at most {@code reach} blocks
     * round it ({@code reach} when nothing is that near).
     */
    static double clearance(CityBuild b, double x, double y, double z, String open, double reach) {
        double best = reach;
        int r = (int) Math.ceil(reach) + 1;
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y), cz = (int) Math.floor(z);
        for (int i = cx - r; i <= cx + r; i++)
            for (int j = cy - r; j <= cy + r; j++) for (int k = cz - r; k <= cz + r; k++) {
                if (open.indexOf(b.cell(i, j, k)) >= 0) continue;
                double dx = Math.max(0, Math.max(i - x, x - i - 1)), dy = Math.max(0, Math.max(j - y, y - j - 1)),
                    dz = Math.max(0, Math.max(k - z, z - k - 1));
                best = Math.min(best, Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
        return best;
    }

    /** The character of the cell a point of a module's frame lies in. */
    static char at(CityBuild b, double x, double y, double z) {
        return b.cell((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    /** The open characters: nothing, and must-be-air. */
    static final String OPEN = " -";
}
