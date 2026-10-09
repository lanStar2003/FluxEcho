package com.fluxecho.logic;

/**
 * The folded zone: the far corner of every dimension, past {@link #START} on both x and z, where the light gates'
 * rooms are. Nothing generates there (each new chunk is empty and counts as populated), nothing spawns, and a room
 * sits every {@link #SPACING} blocks on a grid {@link #COLUMNS} wide, so no room ever sees another.
 * <p>
 * A gate's room is in the gate's own dimension, so walking through only moves the player within the world they are
 * in: both sides stay loaded and nothing is reloaded.
 */
public final class FoldedZone {

    /** Where the zone begins, on both x and z. */
    public static final int START = 1 << 20;
    public static final int SPACING = 1024, COLUMNS = 64;
    /** The rooms' floor. */
    public static final int FLOOR_Y = 64;

    private FoldedZone() {}

    public static boolean contains(double x, double z) {
        return x >= START && z >= START;
    }

    public static boolean containsChunk(int cx, int cz) {
        return cx >= START >> 4 && cz >= START >> 4;
    }

    /** The middle of a room's floor. */
    public static int centerX(int plot) {
        return START + Math.floorMod(plot, COLUMNS) * SPACING + SPACING / 2;
    }

    public static int centerZ(int plot) {
        return START + Math.floorDiv(plot, COLUMNS) * SPACING + SPACING / 2;
    }

    /** The room a point is in the cell of, -1 outside the zone. */
    public static int plotAt(double x, double z) {
        if (!contains(x, z)) return -1;
        long gx = (long) Math.floor((x - START) / SPACING), gz = (long) Math.floor((z - START) / SPACING);
        if (gx >= COLUMNS || gz > Integer.MAX_VALUE / COLUMNS) return -1;
        return (int) (gz * COLUMNS + gx);
    }
}
