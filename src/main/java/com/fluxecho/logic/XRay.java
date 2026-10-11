package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The x-ray projection of a plan that builds under the ground (0.12.0, the root's caisson, CITY.md §4.9): the ghost
 * faces of a projection are hidden by the terrain round them, so an underground plan is shown by its edges alone,
 * drawn through the ground. Pure data, the same for every frame: the client works it out once per plan and draws the
 * segments.
 * <p>
 * The edges are the folds of the surface of a set of cells: an edge of the block grid is one where the four cells
 * round it are filled in a pattern that is not flat (one or three of them, or two across a diagonal), the lines a
 * wireframe of the solid shows. Runs of such edges along one line are joined into one segment, so the Tidal Well's
 * shaft is a few hundred segments, not its tens of thousands of block edges.
 * <p>
 * Its counterpart for a plan that builds high in the air (the zenith's pod at Y0 + 40): what {@link #hangs} there gets
 * its {@link #shadow} drawn on the ground and {@link #plumbs} dropped to it, so a player on the forum sees where the
 * ghost overhead stands.
 */
public final class XRay {

    /** The axes of a segment: along x, y or z. */
    public static final int X = 0, Y = 1, Z = 2;

    private XRay() {}

    /**
     * Whether a plan builds anything under its base layer {@code y0}: some cell it places lies below it (the root), so
     * its projection is drawn by its x-ray edges.
     */
    public static boolean underground(GhostCells.Cells c, int y0) {
        for (int i = 0; i < c.size(); i++) if (c.drawable(i) && c.y[i] < y0) return true;
        return false;
    }

    /**
     * The drawable cells of a plan below {@code belowY} (exclusive) that are not set in {@code done} (null: none is),
     * of one stage or of all ({@code stage} -1), as step indices: what {@link #edges(GhostCells.Cells, int[])}
     * outlines.
     */
    public static int[] below(GhostCells.Cells c, BitSet done, int belowY, int stage) {
        int[] out = new int[c.size()];
        int n = 0;
        for (int i = 0; i < c.size(); i++) {
            if (!c.drawable(i) || c.y[i] >= belowY || done != null && done.get(i)) continue;
            if (stage >= 0 && c.stage[i] != stage) continue;
            out[n++] = i;
        }
        return Arrays.copyOf(out, n);
    }

    /** {@link #edges(int[], int[], int[])} of the cells of a plan with the given step indices. */
    public static int[] edges(GhostCells.Cells c, int[] steps) {
        int[] xs = new int[steps.length], ys = new int[steps.length], zs = new int[steps.length];
        for (int k = 0; k < steps.length; k++) {
            xs[k] = c.x[steps[k]];
            ys[k] = c.y[steps[k]];
            zs[k] = c.z[steps[k]];
        }
        return edges(xs, ys, zs);
    }

    /**
     * The fold edges of a set of cells {x, y, z} (parallel arrays; repeats allowed), as segments between block
     * corners, six ints each: {x0, y0, z0, x1, y1, z1} with the start before the end along the segment's one axis.
     * Segments come by axis (x, then y, then z), then by the line they lie on, then along it.
     *
     * @throws IllegalArgumentException if the arrays differ in length
     */
    public static int[] edges(int[] xs, int[] ys, int[] zs) {
        if (xs.length != ys.length || xs.length != zs.length)
            throw new IllegalArgumentException("The cell arrays differ in length.");
        Set<Long> cells = new HashSet<>();
        for (int i = 0; i < xs.length; i++) cells.add(pos(xs[i], ys[i], zs[i]));
        List<int[]> out = new ArrayList<>();
        for (int axis = X; axis <= Z; axis++) {
            // every unit edge along the axis that a cell touches, keyed by its line and its place along it
            Set<Long> seen = new HashSet<>();
            List<long[]> folds = new ArrayList<>();
            for (long p : cells) {
                int x = posX(p), y = posY(p), z = posZ(p);
                // the four edges of the cell along the axis, at the corners (u, v) of its cross-section
                for (int du = 0; du <= 1; du++) for (int dv = 0; dv <= 1; dv++) {
                    int[] e = edgeOf(axis, x, y, z, du, dv);
                    long key = pos(e[0], e[1], e[2]);
                    if (!seen.add(key)) continue;
                    if (fold(cells, axis, e[0], e[1], e[2])) folds.add(new long[] { line(axis, e), along(axis, e) });
                }
            }
            folds.sort((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
            for (int i = 0; i < folds.size();) {
                int j = i;
                while (j + 1 < folds.size() && folds.get(j + 1)[0] == folds.get(i)[0]
                    && folds.get(j + 1)[1] == folds.get(j)[1] + 1) j++;
                out.add(segment(axis, folds.get(i)[0], (int) folds.get(i)[1], (int) folds.get(j)[1] + 1));
                i = j + 1;
            }
        }
        int[] flat = new int[out.size() * 6];
        for (int i = 0; i < out.size(); i++) System.arraycopy(out.get(i), 0, flat, i * 6, 6);
        return flat;
    }

    /**
     * The unit edge along {@code axis} at the corner (du, dv) of the cell's cross-section: the block corner it starts
     * from, {x, y, z}.
     */
    private static int[] edgeOf(int axis, int x, int y, int z, int du, int dv) {
        switch (axis) {
            case X:
                return new int[] { x, y + du, z + dv };
            case Y:
                return new int[] { x + du, y, z + dv };
            default:
                return new int[] { x + du, y + dv, z };
        }
    }

    /**
     * Whether the unit edge along {@code axis} from the corner (x, y, z) is a fold: the four cells round it are filled
     * in one or three places, or in two across a diagonal.
     */
    private static boolean fold(Set<Long> cells, int axis, int x, int y, int z) {
        boolean[] in = new boolean[4];
        int n = 0;
        for (int k = 0; k < 4; k++) {
            int a = k & 1, b = k >> 1;
            long p;
            switch (axis) {
                case X:
                    p = pos(x, y - 1 + a, z - 1 + b);
                    break;
                case Y:
                    p = pos(x - 1 + a, y, z - 1 + b);
                    break;
                default:
                    p = pos(x - 1 + a, y - 1 + b, z);
            }
            in[k] = cells.contains(p);
            if (in[k]) n++;
        }
        if (n == 1 || n == 3) return true;
        // two filled across a diagonal (0 and 3, or 1 and 2) fold; two side by side lie flat
        return n == 2 && in[0] == in[3];
    }

    /** The line an edge lies on (its two coordinates across the axis, packed), for sorting and joining. */
    private static long line(int axis, int[] e) {
        int u, v;
        switch (axis) {
            case X:
                u = e[1];
                v = e[2];
                break;
            case Y:
                u = e[0];
                v = e[2];
                break;
            default:
                u = e[0];
                v = e[1];
        }
        return ((long) u << 32) | (v & 0xFFFFFFFFL);
    }

    /** An edge's place along its axis. */
    private static long along(int axis, int[] e) {
        return e[axis];
    }

    /** The segment {x0, y0, z0, x1, y1, z1} along {@code axis} on {@code line} from {@code from} to {@code to}. */
    private static int[] segment(int axis, long line, int from, int to) {
        int u = (int) (line >> 32), v = (int) line;
        switch (axis) {
            case X:
                return new int[] { from, u, v, to, u, v };
            case Y:
                return new int[] { u, from, v, u, to, v };
            default:
                return new int[] { u, v, from, u, v, to };
        }
    }

    // ---- what hangs in the air

    /** How high over the base layer a cell must be to count as hanging in the air (higher than any hall's deck). */
    public static final int AIR = 16;
    /** The extreme points of a shadow that get a plumb line: the eight compass directions. */
    private static final int[][] COMPASS = { { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 }, { -1, 0 }, { -1, -1 }, { 0, -1 },
        { 1, -1 } };

    /**
     * The lowest layer of a plan that hangs in the air (0.12.0: the zenith's pod at Y0 + 40), or
     * {@link Integer#MIN_VALUE} when nothing does. A column of the plan hangs when the lowest cell it places in it
     * (from
     * the base layer {@code y0} up) is at least {@link #AIR} over {@code y0} and no column next to it places anything
     * lower: a hall lays its floor on the base layer under everything it builds (its floating island and its dome's cap
     * too); a leg leaning across the columns is held by the column it came from; the pod's floor has nothing under it
     * but the forum, which is not its plan's. The answer is the lowest such cell.
     */
    public static int hangs(GhostCells.Cells c, int y0) {
        java.util.Map<Long, Integer> lowest = new java.util.HashMap<>();
        for (int i = 0; i < c.size(); i++) {
            if (!c.drawable(i) || c.y[i] < y0) continue;
            lowest.merge(pos(c.x[i], 0, c.z[i]), c.y[i], Math::min);
        }
        int low = Integer.MAX_VALUE;
        for (java.util.Map.Entry<Long, Integer> e : lowest.entrySet()) {
            int y = e.getValue();
            if (y < y0 + AIR || y >= low) continue;
            int x = posX(e.getKey()), z = posZ(e.getKey());
            boolean held = false;
            for (int dx = -1; dx <= 1 && !held; dx++) for (int dz = -1; dz <= 1 && !held; dz++) {
                Integer n = lowest.get(pos(x + dx, 0, z + dz));
                held = n != null && n < y;
            }
            if (!held) low = y;
        }
        return low == Integer.MAX_VALUE ? Integer.MIN_VALUE : low;
    }

    /**
     * The shadow on the ground of what hangs: the outline ({@link GhostCells#edge}, segments {x0, z0, x1, z1} between
     * block corners) of the columns of every drawable cell at or above {@code fromY} ({@link #hangs}); empty when
     * {@code fromY} is {@link Integer#MIN_VALUE}.
     */
    public static int[] shadow(GhostCells.Cells c, int fromY) {
        if (fromY == Integer.MIN_VALUE) return new int[0];
        int[] xs = new int[c.size()], zs = new int[c.size()];
        int n = 0;
        for (int i = 0; i < c.size(); i++) {
            if (!c.drawable(i) || c.y[i] < fromY) continue;
            xs[n] = c.x[i];
            zs[n++] = c.z[i];
        }
        return GhostCells.edge(Arrays.copyOf(xs, n), Arrays.copyOf(zs, n));
    }

    /**
     * Where plumb lines drop from what hangs to the ground: the corners of a {@link #shadow} furthest out in each of
     * the eight compass directions, {x, z} pairs (block corners), each once.
     */
    public static int[] plumbs(int[] shadow) {
        if (shadow.length < 4) return new int[0];
        List<Long> picked = new ArrayList<>();
        for (int[] d : COMPASS) {
            long best = 0;
            double bestV = Double.NEGATIVE_INFINITY;
            for (int k = 0; k + 1 < shadow.length; k += 2) {
                // every segment's two ends, as x, z pairs (k steps over x0 z0, then x1 z1)
                int x = shadow[k], z = shadow[k + 1];
                double v = x * d[0] + z * d[1];
                if (v > bestV) {
                    bestV = v;
                    best = ((long) x << 32) | (z & 0xFFFFFFFFL);
                }
            }
            if (!picked.contains(best)) picked.add(best);
        }
        int[] out = new int[picked.size() * 2];
        for (int i = 0; i < picked.size(); i++) {
            out[2 * i] = (int) (picked.get(i) >> 32);
            out[2 * i + 1] = (int) (long) picked.get(i);
        }
        return out;
    }

    /** A block corner or cell as one long: 26 bits across and along, 12 up (as {@link GhostCells}). */
    static long pos(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
    }

    static int posX(long p) {
        return (int) (p >> 38);
    }

    static int posZ(long p) {
        return (int) (p << 26 >> 38);
    }

    static int posY(long p) {
        return (int) (p << 52 >> 52);
    }
}
