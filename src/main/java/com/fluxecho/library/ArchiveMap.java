package com.fluxecho.library;

import com.fluxecho.logic.ArchiveShape;
import com.fluxecho.logic.LibraryUnits;

/**
 * The floor plans of the Echo Archive as its GUI draws them ({@link ArchiveGui}): one plan a storey, the interior seen
 * from above with the back wall at the top and the doors at the bottom, three pixels a block, the walls a one-pixel
 * line round it. It says where each bookcase's tile lies on its storey's plan and which bookcase a point of a plan is
 * nearest, so that what is drawn and what a click picks always agree. The plan is the right way round for someone
 * walking in: the Archive's across axis runs to the right of a player entering through the doors.
 * <p>
 * Pure arithmetic over {@link ArchiveShape} and {@link LibraryUnits}, used on the client only, but free of client
 * classes so the common GUI code can name it.
 */
public final class ArchiveMap {

    /** Pixels a block. */
    public static final int CELL = 3;
    /** The interior a plan shows: blocks x 1..25 and z 1..33 (the walls are the line round it). */
    public static final int X0 = 1, X1 = ArchiveShape.WIDTH - 2, Z0 = 1, Z1 = ArchiveShape.DEPTH - 2;
    /** A plan's size in pixels, the wall line included: 77 x 101. */
    public static final int W = (X1 - X0 + 1) * CELL + 2, H = (Z1 - Z0 + 1) * CELL + 2;
    /** How far from a tile, in pixels, a click still picks its bookcase. */
    public static final double REACH = 3;

    private ArchiveMap() {}

    /** The plan's x of a point of the Archive (for a whole number, the left edge of that block). */
    public static double px(double x) {
        return 1 + (x - X0) * CELL;
    }

    /** The plan's y of a point of the Archive (for a whole number, the lower edge of that block on the plan). */
    public static double py(double z) {
        return 1 + (Z1 + 1 - z) * CELL;
    }

    /** The storey a bookcase stands on: 0 the ground floor, 1 the gallery. */
    public static int storey(int unit) {
        return LibraryUnits.UNITS.get(unit).storey;
    }

    /** A bookcase's tile on its storey's plan, {x0, y0, x1, y1} in pixels (the far edges exclusive). */
    public static int[] tile(int unit) {
        LibraryUnits.Unit u = LibraryUnits.UNITS.get(unit);
        int x1 = u.alongX ? u.x0 + 3 : u.x0 + 1, z1 = u.alongX ? u.z0 + 1 : u.z0 + 3;
        return new int[] { (int) px(u.x0), (int) py(z1), (int) px(x1), (int) py(u.z0) };
    }

    /**
     * The bookcase of the storey whose tile is nearest to a point of that storey's plan, within {@link #REACH}
     * pixels; -1 when none is that near.
     */
    public static int nearest(int storey, double x, double y) {
        int best = -1;
        double bestD = REACH + 1e-9;
        for (LibraryUnits.Unit u : LibraryUnits.UNITS) {
            if (u.storey != storey) continue;
            int[] t = tile(u.index);
            double dx = Math.max(0, Math.max(t[0] - x, x - t[2])), dy = Math.max(0, Math.max(t[1] - y, y - t[3]));
            double d = Math.sqrt(dx * dx + dy * dy);
            if (d < bestD) {
                bestD = d;
                best = u.index;
            }
        }
        return best;
    }
}
