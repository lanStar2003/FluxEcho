package com.fluxecho.logic;

/**
 * Packs a cell position relative to an origin (the nexus centre) into one int, for the build packets and the synced
 * list of blocked cells. Minecraft 1.7.10 NBT has only byte and int arrays, no long or short arrays, so every position
 * travels as one int in an {@code int[]}. The layout is {@code (dx+128) | (dz+128) << 8 | (dy+512) << 16}: eight bits
 * each across and along, ten bits up, which covers a whole campus (it spans at most 64 blocks from the centre) and
 * any height from the origin.
 */
public final class FxCodec {

    /** The largest offset across or along in either direction. */
    public static final int MAX_XZ = 127;
    /** The lowest and highest offset up. */
    public static final int MIN_Y = -512, MAX_Y = 511;

    private FxCodec() {}

    /** Whether an offset can be packed: {@code |dx|, |dz| <= 127} and {@code -512 <= dy <= 511}. */
    public static boolean fits(int dx, int dy, int dz) {
        return dx >= -MAX_XZ && dx <= MAX_XZ && dz >= -MAX_XZ && dz <= MAX_XZ && dy >= MIN_Y && dy <= MAX_Y;
    }

    /**
     * The packed offset.
     *
     * @throws IllegalArgumentException when the offset does not {@link #fits fit}
     */
    public static int pack(int dx, int dy, int dz) {
        if (!fits(dx, dy, dz))
            throw new IllegalArgumentException("Offset " + dx + "," + dy + "," + dz + " does not fit a packed cell.");
        return (dx + 128) & 0xFF | ((dz + 128) & 0xFF) << 8 | ((dy + 512) & 0x3FF) << 16;
    }

    /** The offset across (x) of a packed cell. */
    public static int dx(int packed) {
        return (packed & 0xFF) - 128;
    }

    /** The offset up (y) of a packed cell. */
    public static int dy(int packed) {
        return ((packed >>> 16) & 0x3FF) - 512;
    }

    /** The offset along (z) of a packed cell. */
    public static int dz(int packed) {
        return ((packed >>> 8) & 0xFF) - 128;
    }
}
