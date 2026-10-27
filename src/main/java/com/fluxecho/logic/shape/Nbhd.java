package com.fluxecho.logic.shape;

import java.util.Arrays;

/**
 * A cell's neighbourhood: what stands in each of the 26 cells around it (the 3×3×3 block minus the centre), as pure
 * data a renderer reads from {@code IBlockAccess} and a test writes by hand.
 * <p>
 * Each cell has a code: one of ours with its {@link Form} and whether it is of the centre's family ({@link #ours}),
 * or not ours and then {@link #OPEN} (air, plants, glass, other mods' partial blocks), {@link #SOLID} (an opaque cube:
 * {@code isOpaqueCube}) or {@link #WATER} (open, but a pool lip and a spout look for it). The style's neighbour model
 * (§3.3.1) reads fourteen of the cells (the six faces, the four horizontal diagonals, the four cells over the sides);
 * a cell is closed (实) when it is an opaque cube or one of our {@link Form#closed} shapes, open (空) otherwise. The
 * other twelve cells only serve to work out what a neighbour of ours shows on the face it shares with the centre, so
 * faces between two of our shapes are culled exactly where they meet (and the lower diagonals tell a rib where its
 * brace goes).
 * <p>
 * A neighbour whose block {@code Formed} hides counts as {@link #OPEN}. A cell a computation may not know (in the
 * neighbourhood of a neighbour, beyond the centre's reach) reads as {@link Unknown}, which callers inside this package
 * catch to fall back to the safe choice.
 */
public final class Nbhd {

    /** Not one of ours: air or any block that is not an opaque cube. */
    public static final int OPEN = 0;
    /** Not one of ours: an opaque cube ({@code isOpaqueCube}). */
    public static final int SOLID = 1;
    /** Not one of ours: water (open). */
    public static final int WATER = 2;

    private static final int KIND = 3, FORM_SHIFT = 2, FORM_MASK = 0x3F << FORM_SHIFT, SAME_FAMILY = 1 << 8,
        FAMILY_UNKNOWN = 1 << 9, UNKNOWN = 1 << 10;

    /** The cells in the order of {@link #index}; the centre's slot is unused. */
    private final int[] cells;

    /** Every neighbour open: a shape standing alone, as an item or a lone block in the air. */
    public static final Nbhd ISOLATED = new Nbhd(new int[27]);

    private Nbhd(int[] cells) {
        this.cells = cells;
    }

    /** The code of one of our shapes, {@code sameFamily} when it is of the centre's material family. */
    public static int ours(Form form, boolean sameFamily) {
        return ((form.ordinal() + 1) << FORM_SHIFT) | (sameFamily ? SAME_FAMILY : 0);
    }

    /** The slot of offset {@code (dx, dy, dz)}, each in -1..1. */
    public static int index(int dx, int dy, int dz) {
        return (dx + 1) * 9 + (dy + 1) * 3 + dz + 1;
    }

    /** A neighbourhood from 27 codes in {@link #index} order (the centre's slot is ignored). */
    public static Nbhd of(int[] codes) {
        if (codes.length != 27) throw new IllegalArgumentException("27 codes, not " + codes.length);
        int[] c = codes.clone();
        c[13] = 0;
        return new Nbhd(c);
    }

    /** Writes a neighbourhood cell by cell; unset cells are {@link #OPEN}. */
    public static final class Builder {

        private final int[] c = new int[27];

        /** Sets the cell at an offset. */
        public Builder set(int dx, int dy, int dz, int code) {
            if (dx == 0 && dy == 0 && dz == 0) throw new IllegalArgumentException("the centre is not a neighbour");
            c[index(dx, dy, dz)] = code;
            return this;
        }

        /** The neighbourhood. */
        public Nbhd build() {
            return new Nbhd(c.clone());
        }
    }

    // ---- the style's fourteen-neighbour model as a bit mask (the tests' spelling of §3.3.4)

    /** Mask bits 0–5: the faces in {@code ForgeDirection} order. */
    public static final int DOWN = 1, UP = 2, NORTH = 4, SOUTH = 8, WEST = 16, EAST = 32;
    /** Mask bits 6–9: the horizontal diagonals east+south, south+west, west+north, north+east. */
    public static final int ES = 64, SW = 128, WN = 256, NE = 512;
    /** Mask bits 10–13: the cells over the east, south, west and north sides. */
    public static final int EU = 1024, SU = 2048, WU = 4096, NU = 8192;

    /**
     * The style's model: the cells whose bits are set in {@code mask} are closed (opaque cubes), every other cell is
     * open.
     */
    public static Nbhd ofMask(int mask) {
        int[] c = new int[27];
        for (int f = 0; f < 6; f++) if ((mask & (1 << f)) != 0) c[index(Dir.FX[f], Dir.FY[f], Dir.FZ[f])] = SOLID;
        for (int k = 0; k < 4; k++) {
            if ((mask & (64 << k)) != 0) c[index(Dir.rx(k) + Dir.rx(k + 1), 0, Dir.rz(k) + Dir.rz(k + 1))] = SOLID;
            if ((mask & (1024 << k)) != 0) c[index(Dir.rx(k), 1, Dir.rz(k))] = SOLID;
        }
        return new Nbhd(c);
    }

    // ---- reading

    /** The code at an offset; {@link Unknown} when this neighbourhood does not know the cell. */
    public int code(int dx, int dy, int dz) {
        int c = cells[index(dx, dy, dz)];
        if ((c & UNKNOWN) != 0) throw Unknown.INSTANCE;
        return c;
    }

    /** Our shape at an offset, or null when the cell is not one of ours. */
    public Form form(int dx, int dy, int dz) {
        return formOf(code(dx, dy, dz));
    }

    /** Whether the cell at an offset is closed (实): an opaque cube or one of our closed shapes. */
    public boolean closed(int dx, int dy, int dz) {
        int c = code(dx, dy, dz);
        Form f = formOf(c);
        return f != null ? f.closed() : (c & KIND) == SOLID;
    }

    /** Whether the cell at an offset is not ours and opaque. */
    public boolean solid(int dx, int dy, int dz) {
        int c = code(dx, dy, dz);
        return formOf(c) == null && (c & KIND) == SOLID;
    }

    /** Whether the cell at an offset is water. */
    public boolean water(int dx, int dy, int dz) {
        int c = code(dx, dy, dz);
        return formOf(c) == null && (c & KIND) == WATER;
    }

    /** Whether the cell at an offset is ours and of the centre's family. */
    public boolean sameFamily(int dx, int dy, int dz) {
        int c = code(dx, dy, dz);
        if ((c & FAMILY_UNKNOWN) != 0) throw Unknown.INSTANCE;
        return formOf(c) != null && (c & SAME_FAMILY) != 0;
    }

    /** Whether the cell at an offset is the shape {@code form} of the centre's family. */
    public boolean same(int dx, int dy, int dz, Form form) {
        return form(dx, dy, dz) == form && sameFamily(dx, dy, dz);
    }

    private static Form formOf(int code) {
        int o = (code & FORM_MASK) >> FORM_SHIFT;
        return o == 0 ? null : Form.of(o - 1);
    }

    // ---- other views

    /** The neighbourhood turned a quarter about +y (east to south, south to west, ...), as the world turns. */
    public Nbhd rotated() {
        int[] c = new int[27];
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++)
            for (int dz = -1; dz <= 1; dz++) c[index(-dz, dy, dx)] = cells[index(dx, dy, dz)];
        return new Nbhd(c);
    }

    /**
     * The neighbourhood of the neighbour across face {@code face}, as far as this one knows it: the cells within
     * reach of both, the centre as {@code self}, everything farther {@link Unknown}. Families are only known relative
     * to the centre, so they read as unknown (geometry never depends on them).
     */
    Nbhd neighbour(int face, Form self) {
        int ox = Dir.FX[face], oy = Dir.FY[face], oz = Dir.FZ[face];
        int[] c = new int[27];
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dy == 0 && dz == 0) continue;
            int x = ox + dx, y = oy + dy, z = oz + dz;
            int i = index(dx, dy, dz);
            if (x == 0 && y == 0 && z == 0) c[i] = ours(self, false) | FAMILY_UNKNOWN;
            else if (Math.abs(x) <= 1 && Math.abs(y) <= 1 && Math.abs(z) <= 1)
                c[i] = cells[index(x, y, z)] | ((cells[index(x, y, z)] & UNKNOWN) != 0 ? 0 : FAMILY_UNKNOWN);
            else c[i] = UNKNOWN;
        }
        return new Nbhd(c);
    }

    /**
     * This neighbourhood as the neighbour across face {@code face} can know it: the cells within its reach, the rest
     * {@link Unknown}. A cell decides to cull against a neighbour only when the neighbour can work out the same.
     */
    Nbhd seenFrom(int face) {
        int ox = Dir.FX[face], oy = Dir.FY[face], oz = Dir.FZ[face];
        int[] c = cells.clone();
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++)
            if (Math.abs(dx - ox) > 1 || Math.abs(dy - oy) > 1 || Math.abs(dz - oz) > 1) c[index(dx, dy, dz)] = UNKNOWN;
        return new Nbhd(c);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Nbhd n && Arrays.equals(cells, n.cells);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(cells);
    }

    /** A cell this neighbourhood does not know; thrown without a stack, caught inside the package. */
    static final class Unknown extends RuntimeException {

        private static final long serialVersionUID = 1L;
        static final Unknown INSTANCE = new Unknown();

        private Unknown() {
            super("unknown cell", null, false, false);
        }
    }
}
