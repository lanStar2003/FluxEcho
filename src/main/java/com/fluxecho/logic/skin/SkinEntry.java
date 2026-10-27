package com.fluxecho.logic.skin;

import java.util.Arrays;
import java.util.Objects;

/**
 * One building of the box table (style §3.9.2): which campus it belongs to, its box in the world, the box its lamp
 * windows lie in, its {@code Buildings} row, the shape it stands as, its facing and controller, its state, the
 * debounced power bit, when it first formed, its accent, its variant and its few bytes of extra data. Immutable: the
 * server makes a new entry whenever something changes ({@link SkinTrack}), the client swaps whole tables, so a chunk
 * built on another thread never sees one half changed. The arrays are never written to after the entry is made.
 */
public final class SkinEntry {

    /** The most bytes of {@link #extra} (refactor §4.2, {@code TileCityModule.writeSkinExtra}). */
    public static final int EXTRA_MAX = 64;

    /** The campus: its nexus's centre, packed ({@link #pack}). */
    public final long campus;
    /** The building's box {x0, y0, z0, x1, y1, z1} (inclusive): its shape's box round its controller. */
    public final int[] box;
    /**
     * The box its lamp windows lie in {x0, y0, z0, x1, y1, z1}, the part of the building a lamp flip rebuilds
     * (refactor §5.6: the bounds of its {@code LEGACY_MASKS}); null when it has none, or none is known yet.
     */
    public final int[] lamp;
    /** Its {@code Buildings} row ({@link SkinKeys}). */
    public final int row;
    /** The name of the shape it stands as: an old shape ({@code main}, {@code moored}, {@code well_14}...) or a new. */
    public final String shape;
    /** Its controller's facing, a {@code ForgeDirection} ordinal (2 to 5). */
    public final int facing;
    /** Its controller {x, y, z}. */
    public final int[] ctrl;
    public final SkinState state;
    /** Whether it is awake and powered, debounced (§3.9.4). */
    public final boolean powered;
    /**
     * The world tick it first formed, from which the developing sweep runs once; 0 for a building that stood before
     * the table knew it (no sweep).
     */
    public final long formedAt;
    /** Its accent, 0xRRGGBB ({@link SkinPalette}). */
    public final int accent;
    /** Its variant (the pattern its materials carry in this building, style §3.5); 0 for the old buildings. */
    public final int variant;
    /** Its extra bytes: the ring's segments, the nexus's hull-number bricks and its {@code VOYAGE} part (0.19 on). */
    public final byte[] extra;

    private SkinEntry(Builder b) {
        campus = b.campus;
        box = b.box.clone();
        lamp = b.lamp == null ? null : b.lamp.clone();
        row = b.row;
        shape = b.shape;
        facing = b.facing;
        ctrl = b.ctrl.clone();
        state = b.state;
        powered = b.powered;
        formedAt = b.formedAt;
        accent = b.accent;
        variant = b.variant;
        extra = b.extra.clone();
    }

    /** The building's module key ({@code eco_dome}, {@code nexus}...). */
    public String key() {
        return SkinKeys.key(row);
    }

    /** Which building of its campus it is: its controller, packed. */
    public long id() {
        return pack(ctrl[0], ctrl[1], ctrl[2]);
    }

    /** How far it stands from its nexus across the ground, in blocks: the lamp wave's {@code d} (§3.9.5). */
    public double distance() {
        int[] n = unpack(campus);
        double dx = ctrl[0] - n[0], dz = ctrl[2] - n[2];
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Whether its box holds the block. */
    public boolean contains(int x, int y, int z) {
        return in(box, x, y, z);
    }

    /** Whether its lamp box holds the block. */
    public boolean lampContains(int x, int y, int z) {
        return lamp != null && in(lamp, x, y, z);
    }

    /** The box a lamp flip rebuilds: the lamp box, or the whole box when it has none. */
    public int[] rebuildBox() {
        return lamp != null ? lamp : box;
    }

    static boolean in(int[] b, int x, int y, int z) {
        return x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] && z >= b[2] && z <= b[5];
    }

    static long volume(int[] b) {
        return (long) (b[3] - b[0] + 1) * (b[4] - b[1] + 1) * (b[5] - b[2] + 1);
    }

    /**
     * A block position in one long: x and z in 26 bits each (±33 million), y in 12. The campus and building ids are
     * such longs.
     */
    public static long pack(int x, int y, int z) {
        return (long) (x & 0x3FFFFFF) << 38 | (long) (z & 0x3FFFFFF) << 12 | y & 0xFFF;
    }

    /** {x, y, z} of a packed position. */
    public static int[] unpack(long k) {
        return new int[] { (int) (k >> 38), (int) (k & 0xFFF), (int) (k << 26 >> 38) };
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A builder holding this entry's values, to make a changed copy. */
    public Builder toBuilder() {
        Builder b = new Builder();
        b.campus = campus;
        b.box = box.clone();
        b.lamp = lamp == null ? null : lamp.clone();
        b.row = row;
        b.shape = shape;
        b.facing = facing;
        b.ctrl = ctrl.clone();
        b.state = state;
        b.powered = powered;
        b.formedAt = formedAt;
        b.accent = accent;
        b.variant = variant;
        b.extra = extra.clone();
        return b;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SkinEntry e)) return false;
        return campus == e.campus && row == e.row
            && facing == e.facing
            && powered == e.powered
            && formedAt == e.formedAt
            && accent == e.accent
            && variant == e.variant
            && state == e.state
            && shape.equals(e.shape)
            && Arrays.equals(box, e.box)
            && Arrays.equals(lamp, e.lamp)
            && Arrays.equals(ctrl, e.ctrl)
            && Arrays.equals(extra, e.extra);
    }

    @Override
    public int hashCode() {
        return Objects.hash(campus, row, shape, state, powered, formedAt, accent, Arrays.hashCode(ctrl));
    }

    @Override
    public String toString() {
        return key() + ":" + shape + " " + state + (powered ? " powered" : "") + " at " + Arrays.toString(ctrl);
    }

    /** Makes entries; every value but the key has a default. */
    public static final class Builder {

        private long campus;
        private int[] box = new int[6], lamp, ctrl = new int[3];
        private int row = -1, facing = 2, accent = 0xFFFFFF, variant;
        private String shape = "main";
        private SkinState state = SkinState.FORMED;
        private boolean powered;
        private long formedAt;
        private byte[] extra = new byte[0];

        private Builder() {}

        public Builder campus(long campus) {
            this.campus = campus;
            return this;
        }

        /**
         * The building by its module key.
         *
         * @throws IllegalArgumentException for a key that is no {@code Buildings} row
         */
        public Builder key(String key) {
            int r = SkinKeys.row(key);
            if (r < 0) throw new IllegalArgumentException("no building row for " + key);
            row = r;
            return this;
        }

        public Builder row(int row) {
            if (SkinKeys.key(row) == null) throw new IllegalArgumentException("no building row " + row);
            this.row = row;
            return this;
        }

        public Builder shape(String shape) {
            this.shape = Objects.requireNonNull(shape);
            return this;
        }

        public Builder facing(int facing) {
            this.facing = facing;
            return this;
        }

        public Builder ctrl(int x, int y, int z) {
            ctrl = new int[] { x, y, z };
            return this;
        }

        public Builder box(int x0, int y0, int z0, int x1, int y1, int z1) {
            return box(new int[] { x0, y0, z0, x1, y1, z1 });
        }

        public Builder box(int[] b) {
            box = sorted(b);
            return this;
        }

        public Builder lamp(int x0, int y0, int z0, int x1, int y1, int z1) {
            return lamp(new int[] { x0, y0, z0, x1, y1, z1 });
        }

        /** The lamp box, or null for none. */
        public Builder lamp(int[] b) {
            lamp = b == null ? null : sorted(b);
            return this;
        }

        public Builder state(SkinState state) {
            this.state = Objects.requireNonNull(state);
            return this;
        }

        public Builder powered(boolean powered) {
            this.powered = powered;
            return this;
        }

        public Builder formedAt(long formedAt) {
            this.formedAt = formedAt;
            return this;
        }

        public Builder accent(int accent) {
            this.accent = accent & 0xFFFFFF;
            return this;
        }

        public Builder variant(int variant) {
            this.variant = variant & 0xFF;
            return this;
        }

        /**
         * @throws IllegalArgumentException for more than {@link #EXTRA_MAX} bytes
         */
        public Builder extra(byte[] extra) {
            if (extra.length > EXTRA_MAX)
                throw new IllegalArgumentException("extra is at most " + EXTRA_MAX + " bytes, not " + extra.length);
            this.extra = extra.clone();
            return this;
        }

        /**
         * @throws IllegalArgumentException without a key
         */
        public SkinEntry build() {
            if (row < 0) throw new IllegalArgumentException("an entry needs its building");
            return new SkinEntry(this);
        }

        /** A box as given or with its corners swapped round, so min comes first on every axis. */
        private static int[] sorted(int[] b) {
            if (b.length != 6) throw new IllegalArgumentException("a box is six numbers");
            return new int[] { Math.min(b[0], b[3]), Math.min(b[1], b[4]), Math.min(b[2], b[5]), Math.max(b[0], b[3]),
                Math.max(b[1], b[4]), Math.max(b[2], b[5]) };
        }
    }
}
