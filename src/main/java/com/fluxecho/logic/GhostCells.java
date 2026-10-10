package com.fluxecho.logic;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * What the client draws of a build job's projection (0.10.0), worked out from the plan alone: which unbuilt cells are
 * shown as ghosts (the nearest to the viewer, up to a cap), which of their faces show (the exterior ones), the lowest
 * layer still to build in a stage, the bounding boxes of the stages, and the outline of the ground a plan clears.
 * <p>
 * It is pure so it can be tested and timed without Minecraft: the plan's cells come in as parallel arrays
 * ({@link Cells}), the cells already built (or not to be shown yet) as a {@link BitSet} of step indices, and the world
 * only through an {@link Opaque} predicate. A plan has up to some 30,000 cells and the client reselects every few
 * ticks, so the nearest cells are found by a partial selection rather than a full sort, and neighbour lookups use a
 * primitive hash of packed positions. Everything is deterministic: distances are compared in steps of
 * 1/{@value #DIST_SCALE} of a squared block, and equal distances go by step index.
 */
public final class GhostCells {

    /** The neighbour offset of each face, in Minecraft's side order: down, up, north, south, west, east. */
    public static final int[][] FACES = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 },
        { 1, 0, 0 } };
    /** Squared distances are compared in steps of 1/64 of a squared block. */
    public static final int DIST_SCALE = 64;
    /** The most cells a plan may have: the step index shares a long with the distance when sorting. */
    public static final int MAX_CELLS = 1 << 24;

    private static final int INDEX_BITS = 24;
    private static final long INDEX_MASK = (1L << INDEX_BITS) - 1;
    private static final long MAX_DIST = (1L << (63 - INDEX_BITS)) - 1;
    /** Rounds of partitioning before the selection gives up and sorts what is left (it never should). */
    private static final int MAX_ROUNDS = 64;

    private GhostCells() {}

    /** Whether the world block at a cell is opaque (a ghost face against it cannot be seen). */
    @FunctionalInterface
    public interface Opaque {

        boolean at(int x, int y, int z);
    }

    /** A plan's cells as parallel arrays, indexed like the plan's steps. */
    public static final class Cells {

        public final int[] x, y, z, part, stage;
        /** {@link BuildPlan#HARD}, {@link BuildPlan#SOFT} or {@link BuildPlan#AIR}. */
        public final byte[] kind;

        /**
         * Wraps the arrays (not copied); they must all have the same length.
         *
         * @throws IllegalArgumentException when the lengths differ or there are more than {@link #MAX_CELLS} cells
         */
        public Cells(int[] x, int[] y, int[] z, int[] part, int[] stage, byte[] kind) {
            int n = x.length;
            if (y.length != n || z.length != n || part.length != n || stage.length != n || kind.length != n)
                throw new IllegalArgumentException("The cell arrays differ in length.");
            if (n > MAX_CELLS) throw new IllegalArgumentException("Too many cells: " + n);
            this.x = x;
            this.y = y;
            this.z = z;
            this.part = part;
            this.stage = stage;
            this.kind = kind;
        }

        /** The cells of a plan's steps, in step order. */
        public static Cells of(List<BuildPlan.Step> steps) {
            int n = steps.size();
            int[] x = new int[n], y = new int[n], z = new int[n], part = new int[n], stage = new int[n];
            byte[] kind = new byte[n];
            for (int i = 0; i < n; i++) {
                BuildPlan.Step s = steps.get(i);
                x[i] = s.x;
                y[i] = s.y;
                z[i] = s.z;
                part[i] = s.part;
                stage[i] = s.stage;
                kind[i] = s.kind;
            }
            return new Cells(x, y, z, part, stage, kind);
        }

        public int size() {
            return x.length;
        }

        /** Whether the cell is drawn as a ghost at all: it places a block (not an {@link BuildPlan#AIR} step). */
        public boolean drawable(int i) {
            return kind[i] != BuildPlan.AIR && part[i] != Parts.AIR;
        }
    }

    // ---- the nearest cells

    /**
     * The step indices of the drawable cells not set in {@code done} whose centre lies within {@code range} of the
     * viewer, nearest first (squared distance to the cell's centre, ties by index), at most {@code cap} of them.
     * {@code done} may be null (nothing built).
     */
    public static int[] select(Cells c, BitSet done, double vx, double vy, double vz, double range, int cap) {
        if (cap <= 0 || !(range >= 0)) return new int[0];
        double r2 = range * range;
        int n = c.size();
        long[] keys = new long[n];
        int m = 0;
        for (int i = 0; i < n; i++) {
            if (!c.drawable(i) || done != null && done.get(i)) continue;
            double d2 = dist2(c, i, vx, vy, vz);
            if (d2 > r2) continue;
            keys[m++] = key(d2, i);
        }
        return nearest(keys, m, cap);
    }

    /** Every drawable cell, nearest the viewer first (the order of a first scan). */
    public static int[] order(Cells c, double vx, double vy, double vz) {
        return select(c, null, vx, vy, vz, Double.POSITIVE_INFINITY, Integer.MAX_VALUE);
    }

    private static double dist2(Cells c, int i, double vx, double vy, double vz) {
        double dx = c.x[i] + 0.5 - vx, dy = c.y[i] + 0.5 - vy, dz = c.z[i] + 0.5 - vz;
        return dx * dx + dy * dy + dz * dz;
    }

    /** A sort key: the squared distance in steps of 1/{@link #DIST_SCALE} above, the index below. */
    static long key(double d2, int index) {
        long q = d2 >= MAX_DIST / (double) DIST_SCALE ? MAX_DIST : d2 > 0 ? (long) (d2 * DIST_SCALE + 0.5) : 0;
        return q << INDEX_BITS | index;
    }

    /** The indices of the {@code cap} smallest of the first {@code m} keys, in order. */
    private static int[] nearest(long[] keys, int m, int cap) {
        int k = Math.min(cap, m);
        if (k < m) selectSmallest(keys, m, k);
        Arrays.sort(keys, 0, k);
        int[] out = new int[k];
        for (int j = 0; j < k; j++) out[j] = (int) (keys[j] & INDEX_MASK);
        return out;
    }

    /**
     * Moves the {@code k} smallest of the first {@code n} keys (all distinct) to the front, in no particular order:
     * a quickselect with a median-of-three pivot, linear on average.
     */
    static void selectSmallest(long[] a, int n, int k) {
        if (k <= 0 || k >= n) return;
        int lo = 0, hi = n - 1, target = k - 1;
        for (int round = 0; hi > lo; round++) {
            if (round >= MAX_ROUNDS) {
                Arrays.sort(a, lo, hi + 1);
                return;
            }
            long p = median(a[lo], a[(lo + hi) >>> 1], a[hi]);
            int i = lo, j = hi;
            while (i <= j) {
                while (a[i] < p) i++;
                while (a[j] > p) j--;
                if (i <= j) {
                    long t = a[i];
                    a[i] = a[j];
                    a[j] = t;
                    i++;
                    j--;
                }
            }
            // now a[lo..i-1] <= p and a[j+1..hi] >= p; anything between equals p
            if (target <= j) hi = j;
            else if (target >= i) lo = i;
            else return;
        }
    }

    private static long median(long a, long b, long c) {
        if (a < b) return b < c ? b : a < c ? c : a;
        return a < c ? a : b < c ? c : b;
    }

    // ---- faces

    /** The selected cell (its position in the selection) of a packed face. */
    public static int cell(int packedFace) {
        return packedFace >>> 3;
    }

    /** The side (0 to 5, {@link #FACES} order) of a packed face. */
    public static int side(int packedFace) {
        return packedFace & 7;
    }

    /**
     * The exterior faces of the selected cells, each packed as {@code position in selected << 3 | side}, cell by cell
     * in selection order and side by side within a cell. A face is left out when the cell next to it is another
     * selected cell (the two ghosts share it) or when {@code opaque} says the world block there is opaque (it hides the
     * face); {@code opaque} may be null.
     */
    public static int[] faces(Cells c, int[] selected, Opaque opaque) {
        PosMap set = new PosMap(selected.length);
        for (int i : selected) set.put(pos(c.x[i], c.y[i], c.z[i]), i);
        int[] out = new int[Math.max(16, selected.length * 2)];
        int n = 0;
        for (int s = 0; s < selected.length; s++) {
            int i = selected[s];
            for (int f = 0; f < FACES.length; f++) {
                int nx = c.x[i] + FACES[f][0], ny = c.y[i] + FACES[f][1], nz = c.z[i] + FACES[f][2];
                if (set.get(pos(nx, ny, nz)) >= 0) continue;
                if (opaque != null && opaque.at(nx, ny, nz)) continue;
                if (n == out.length) out = Arrays.copyOf(out, n * 2);
                out[n++] = s << 3 | f;
            }
        }
        return Arrays.copyOf(out, n);
    }

    // ---- creases

    /** The direction (a {@link #FACES} index) of a packed crease. */
    public static int creaseDir(int packedCrease) {
        return packedCrease & 7;
    }

    /** The face (its index in the faces array) of a packed crease. */
    public static int creaseFace(int packedCrease) {
        return packedCrease >>> 3;
    }

    /**
     * The creases of the ghost's surface: the edges of its exterior faces where a face does not run on flat into the
     * next cell's (the rim of a wall, the edge of a floor, a corner, the sides of an opening), each packed as
     * {@code index in faces << 3 | direction}, the direction (a {@link #FACES} index across the face's axis) the edge
     * lies towards from the middle of its face. An edge shared by two faces of one cell (a convex corner) is given
     * once, by the face with the lower side; one shared with a face of the cell diagonally across (a concave corner)
     * once, by the cell with the lower step index. They are the lines a drawing of the building would have: a grid of
     * every cell's edges would turn into a solid sheet a little way off, these stay few and read from afar.
     *
     * @param faces the result of {@link #faces} for the same selection
     */
    public static int[] creases(Cells c, int[] selected, int[] faces) {
        PosMap at = new PosMap(selected.length);
        for (int s = 0; s < selected.length; s++) {
            int i = selected[s];
            at.put(pos(c.x[i], c.y[i], c.z[i]), s);
        }
        byte[] mask = new byte[selected.length];
        for (int f : faces) mask[cell(f)] |= (byte) (1 << side(f));
        int[] out = new int[Math.max(16, faces.length * 2)];
        int n = 0;
        for (int k = 0; k < faces.length; k++) {
            int s = cell(faces[k]), side = side(faces[k]), i = selected[s];
            for (int d = 0; d < FACES.length; d++) {
                if (d >> 1 == side >> 1) continue;
                int nx = c.x[i] + FACES[d][0], ny = c.y[i] + FACES[d][1], nz = c.z[i] + FACES[d][2];
                int next = at.get(pos(nx, ny, nz));
                // the face runs on flat into the next cell's
                if (next >= 0 && (mask[next] & 1 << side) != 0) continue;
                if ((mask[s] & 1 << d) != 0) {
                    // a convex corner of the cell: its two faces share the edge
                    if (d < side) continue;
                } else {
                    int across = at.get(pos(nx + FACES[side][0], ny + FACES[side][1], nz + FACES[side][2]));
                    // a concave corner: the cell diagonally across has the face on the other side of the edge
                    if (across >= 0 && (mask[across] & 1 << (d ^ 1)) != 0 && selected[across] < i) continue;
                }
                if (n == out.length) out = Arrays.copyOf(out, n * 2);
                out[n++] = k << 3 | d;
            }
        }
        return Arrays.copyOf(out, n);
    }

    /**
     * The two ends of a crease of the cell at {@code (x, y, z)}: the edge where its face {@code side} meets the plane
     * of its side {@code dir}, written as {x0, y0, z0, x1, y1, z1} (the ends one block apart along the third axis,
     * the lower first) into {@code out} at {@code at}.
     */
    public static void creaseEnds(int x, int y, int z, int side, int dir, float[] out, int at) {
        float[] p = { x, y, z };
        int as = AXIS[side >> 1], ad = AXIS[dir >> 1], free = 3 - as - ad;
        p[as] += side & 1;
        p[ad] += dir & 1;
        out[at] = p[0];
        out[at + 1] = p[1];
        out[at + 2] = p[2];
        p[free] += 1;
        out[at + 3] = p[0];
        out[at + 4] = p[1];
        out[at + 5] = p[2];
    }

    /** The coordinate (0 x, 1 y, 2 z) of each pair of {@link #FACES}: down/up, north/south, west/east. */
    private static final int[] AXIS = { 1, 2, 0 };

    // ---- the lowest layer

    /** The lowest layer still to build in a stage: its height and its cells (step indices, nearest first). */
    public static final class Layer {

        /** No cell of the stage is left. */
        public static final Layer NONE = new Layer(Integer.MIN_VALUE, new int[0]);

        /** The layer's y, or {@link Integer#MIN_VALUE} when there is none. */
        public final int y;
        /** Step indices, nearest the viewer first. */
        public final int[] cells;

        Layer(int y, int[] cells) {
            this.y = y;
            this.cells = cells;
        }

        public boolean exists() {
            return y != Integer.MIN_VALUE;
        }
    }

    /**
     * The lowest y among the drawable cells of {@code stage} not set in {@code done}, and the cells of that stage at
     * that y, nearest the viewer first, at most {@code cap}. {@link Layer#NONE} when the stage has no such cell.
     */
    public static Layer lowestLayer(Cells c, BitSet done, int stage, double vx, double vy, double vz, int cap) {
        int n = c.size(), low = Integer.MAX_VALUE, count = 0;
        for (int i = 0; i < n; i++) {
            if (c.stage[i] != stage || !c.drawable(i) || done != null && done.get(i)) continue;
            if (c.y[i] < low) {
                low = c.y[i];
                count = 1;
            } else if (c.y[i] == low) count++;
        }
        if (count == 0) return Layer.NONE;
        long[] keys = new long[count];
        int m = 0;
        for (int i = 0; i < n; i++) {
            if (c.stage[i] != stage || c.y[i] != low || !c.drawable(i) || done != null && done.get(i)) continue;
            keys[m++] = key(dist2(c, i, vx, vy, vz), i);
        }
        return new Layer(low, nearest(keys, m, Math.max(0, cap)));
    }

    // ---- bounds

    /**
     * The bounding boxes of the drawable cells, each {minX, minY, minZ, maxX, maxY, maxZ} (inclusive cell
     * coordinates), or null where there is no drawable cell.
     */
    public static final class Bounds {

        /** The whole plan's box. */
        public final int[] all;
        /** Each stage's box, by stage. */
        public final int[][] stages;

        Bounds(int[] all, int[][] stages) {
            this.all = all;
            this.stages = stages;
        }
    }

    /** The boxes of the plan's drawable cells, whole and for each of its {@code stages} stages. */
    public static Bounds bounds(Cells c, int stages) {
        int[][] per = new int[Math.max(0, stages)][];
        int[] all = null;
        for (int i = 0; i < c.size(); i++) {
            if (!c.drawable(i)) continue;
            all = grow(all, c.x[i], c.y[i], c.z[i]);
            int s = c.stage[i];
            if (s >= 0 && s < per.length) per[s] = grow(per[s], c.x[i], c.y[i], c.z[i]);
        }
        return new Bounds(all, per);
    }

    private static int[] grow(int[] b, int x, int y, int z) {
        if (b == null) return new int[] { x, y, z, x, y, z };
        b[0] = Math.min(b[0], x);
        b[1] = Math.min(b[1], y);
        b[2] = Math.min(b[2], z);
        b[3] = Math.max(b[3], x);
        b[4] = Math.max(b[4], y);
        b[5] = Math.max(b[5], z);
        return b;
    }

    // ---- the outline of a footprint

    /**
     * The outline of a footprint of columns {x, z} (parallel arrays; repeats allowed) as straight segments between
     * block corners, {x0, z0, x1, z1} each, with x0 <= x1 and z0 <= z1: every side of a column with no column next to
     * it, runs along one line joined into one segment. Lines along x come first (by z, then x), then lines along z (by
     * x, then z). Holes in the footprint get their own outline.
     */
    public static int[] edge(int[] xs, int[] zs) {
        if (xs.length != zs.length) throw new IllegalArgumentException("The column arrays differ in length.");
        PosMap set = new PosMap(xs.length);
        for (int i = 0; i < xs.length; i++) set.put(pos(xs[i], 0, zs[i]), i);
        long[] alongX = new long[xs.length * 2], alongZ = new long[xs.length * 2];
        int nx = 0, nz = 0;
        for (int i = 0; i < xs.length; i++) {
            int x = xs[i], z = zs[i];
            // a repeated column is outlined once
            if (set.get(pos(x, 0, z)) != i) continue;
            if (set.get(pos(x, 0, z - 1)) < 0) alongX[nx++] = line(z, x);
            if (set.get(pos(x, 0, z + 1)) < 0) alongX[nx++] = line(z + 1, x);
            if (set.get(pos(x - 1, 0, z)) < 0) alongZ[nz++] = line(x, z);
            if (set.get(pos(x + 1, 0, z)) < 0) alongZ[nz++] = line(x + 1, z);
        }
        int[] out = new int[(nx + nz) * 4];
        int n = merge(alongX, nx, out, 0, true);
        n = merge(alongZ, nz, out, n, false);
        return Arrays.copyOf(out, n);
    }

    /** A unit edge on a line: the line's coordinate above, where it starts along the line below (both in order). */
    private static long line(int at, int from) {
        return (long) at << 32 | ((from ^ 0x80000000) & 0xFFFFFFFFL);
    }

    private static int merge(long[] edges, int count, int[] out, int n, boolean alongX) {
        Arrays.sort(edges, 0, count);
        int i = 0;
        while (i < count) {
            int at = (int) (edges[i] >> 32), from = (int) edges[i] ^ 0x80000000, to = from + 1;
            int j = i + 1;
            while (j < count && (int) (edges[j] >> 32) == at && ((int) edges[j] ^ 0x80000000) == to) {
                to++;
                j++;
            }
            if (alongX) {
                out[n++] = from;
                out[n++] = at;
                out[n++] = to;
                out[n++] = at;
            } else {
                out[n++] = at;
                out[n++] = from;
                out[n++] = at;
                out[n++] = to;
            }
            i = j;
        }
        return n;
    }

    // ---- looking cells up by position

    /** Finds a plan's step by its cell (a plan has at most one step per cell). */
    public static final class Index {

        private final PosMap map;

        /** Indexes every cell, drawable or not; of two steps at one cell (plans have none) the first is kept. */
        public Index(Cells c) {
            map = new PosMap(c.size());
            for (int i = 0; i < c.size(); i++) map.put(pos(c.x[i], c.y[i], c.z[i]), i);
        }

        /** The step index at the cell, or -1. */
        public int find(int x, int y, int z) {
            return map.get(pos(x, y, z));
        }
    }

    /**
     * A cell packed into a long: 26 bits each across and along (any Minecraft position), 12 bits up. Distinct for
     * {@code -2^25 <= x, z < 2^25} and {@code -2048 <= y < 2048}.
     */
    static long pos(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
    }

    /** An open-addressing map from packed cells to non-negative ints; the first value put for a key is kept. */
    private static final class PosMap {

        private final long[] keys;
        private final int[] values;
        private final boolean[] used;
        private final int mask;

        PosMap(int expected) {
            int cap = 4;
            while (cap < expected * 2 && cap < 1 << 30) cap <<= 1;
            keys = new long[cap];
            values = new int[cap];
            used = new boolean[cap];
            mask = cap - 1;
        }

        private int slot(long k) {
            long h = k * 0x9E3779B97F4A7C15L;
            return (int) (h ^ h >>> 32) & mask;
        }

        void put(long k, int v) {
            int s = slot(k);
            while (used[s]) {
                if (keys[s] == k) return;
                s = s + 1 & mask;
            }
            used[s] = true;
            keys[s] = k;
            values[s] = v;
        }

        int get(long k) {
            int s = slot(k);
            while (used[s]) {
                if (keys[s] == k) return values[s];
                s = s + 1 & mask;
            }
            return -1;
        }
    }
}
