package com.fluxecho.logic.occlusion;

import java.util.Collections;
import java.util.List;

/**
 * What a line of sight meets in the world, cell by cell (style FINAL §8.4.1): the pure view {@link SightRay} walks, so
 * the client reads its world through an adapter and the tests write theirs by hand.
 * <ul>
 * <li>{@link #SOLID}: the whole cell blocks the eye (an opaque vanilla cube, one of our whole-block shapes, a round,
 * slope or soffit).</li>
 * <li>{@link #BOXES}: only the boxes {@link #boxes} names block it (a rib, a column, a half or step, a louvre's slats:
 * {@code ShapeGeo.occluders}); the eye passes between them.</li>
 * <li>{@link #CLEAR}: nothing blocks it (air, glass, a window's pane, water, the translucent resonant crystal).</li>
 * </ul>
 */
public interface SightCells {

    int CLEAR = 0, SOLID = 1, BOXES = 2;

    /** What the cell at {@code (x, y, z)} is to the eye. */
    int kind(int x, int y, int z);

    /** The boxes of a {@link #BOXES} cell, {minX, minY, minZ, maxX, maxY, maxZ} in the cell (0 to 1). */
    default List<double[]> boxes(int x, int y, int z) {
        return Collections.emptyList();
    }

    /**
     * The height of the ground at a column: the lowest y above which only sky is seen, for the coarse test ("is the
     * piece under the terrain"); {@link Integer#MIN_VALUE} when the view does not know it.
     */
    default int height(int x, int z) {
        return Integer.MIN_VALUE;
    }
}
