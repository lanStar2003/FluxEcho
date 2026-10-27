package com.fluxecho.logic.occlusion;

import java.util.List;

/**
 * One line of sight through the block grid (style FINAL §8.4.1): a 3D DDA (Amanatides and Woo) from the eye to a
 * point, cell by cell, at most {@link #MAX_LENGTH} blocks. A {@link SightCells#SOLID} cell blocks it; a
 * {@link SightCells#BOXES} cell blocks it only where the segment crosses one of its boxes, so an eye sees between two
 * ribs, through a louvre's gaps and past a column. The eye's own cell and the target's cell never block: a piece of
 * light lies against the face of what carries it, and an eye in a glass pane or a leaf still sees out.
 * <p>
 * Pure and allocation-light, so the client may cast a few hundred a round.
 */
public final class SightRay {

    /** The longest line walked; a farther point counts as seen (style: "最长 160 格"). */
    public static final double MAX_LENGTH = 160;

    /** What one cast found: whether the line is blocked, and how many cells it walked. */
    public static final class Hit {

        public final boolean blocked;
        public final int cells;

        Hit(boolean blocked, int cells) {
            this.blocked = blocked;
            this.cells = cells;
        }
    }

    private SightRay() {}

    /** Whether the line from {@code eye} to {@code target} (world points {x, y, z}) is blocked in {@code w}. */
    public static boolean blocked(SightCells w, double[] eye, double[] target) {
        return cast(w, eye, target).blocked;
    }

    /** Casts the line from {@code eye} to {@code target} through {@code w}. */
    public static Hit cast(SightCells w, double[] eye, double[] target) {
        double ex = eye[0], ey = eye[1], ez = eye[2];
        double dx = target[0] - ex, dy = target[1] - ey, dz = target[2] - ez;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-9 || len > MAX_LENGTH) return new Hit(false, 0);
        int x = floor(ex), y = floor(ey), z = floor(ez);
        int tx = floor(target[0]), ty = floor(target[1]), tz = floor(target[2]);
        int sx = dx > 0 ? 1 : dx < 0 ? -1 : 0, sy = dy > 0 ? 1 : dy < 0 ? -1 : 0, sz = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        // parameter t along the segment (0 at the eye, 1 at the target) of the next cell boundary on each axis
        double tdx = sx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dx),
            tdy = sy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dy),
            tdz = sz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dz);
        double tmx = sx == 0 ? Double.POSITIVE_INFINITY : ((sx > 0 ? x + 1 : x) - ex) / dx,
            tmy = sy == 0 ? Double.POSITIVE_INFINITY : ((sy > 0 ? y + 1 : y) - ey) / dy,
            tmz = sz == 0 ? Double.POSITIVE_INFINITY : ((sz > 0 ? z + 1 : z) - ez) / dz;
        int cells = 0, limit = 3 * ((int) Math.ceil(len) + 2);
        while (cells < limit) {
            // step to the next cell
            if (tmx <= tmy && tmx <= tmz) {
                if (tmx > 1) break;
                x += sx;
                tmx += tdx;
            } else if (tmy <= tmz) {
                if (tmy > 1) break;
                y += sy;
                tmy += tdy;
            } else {
                if (tmz > 1) break;
                z += sz;
                tmz += tdz;
            }
            if (x == tx && y == ty && z == tz) break;
            cells++;
            int k = w.kind(x, y, z);
            if (k == SightCells.SOLID) return new Hit(true, cells);
            if (k == SightCells.BOXES && crosses(w.boxes(x, y, z), x, y, z, ex, ey, ez, dx, dy, dz))
                return new Hit(true, cells);
        }
        return new Hit(false, cells);
    }

    /** Whether the segment {@code e + t·d}, t in [0, 1], crosses any of a cell's boxes. */
    static boolean crosses(List<double[]> boxes, int cx, int cy, int cz, double ex, double ey, double ez, double dx,
        double dy, double dz) {
        for (double[] b : boxes) {
            double t0 = 0, t1 = 1;
            double[] o = { ex - cx, ey - cy, ez - cz }, d = { dx, dy, dz };
            boolean hit = true;
            for (int a = 0; a < 3 && hit; a++) {
                double lo = b[a], hi = b[a + 3];
                if (Math.abs(d[a]) < 1e-12) {
                    if (o[a] < lo || o[a] > hi) hit = false;
                    continue;
                }
                double ta = (lo - o[a]) / d[a], tb = (hi - o[a]) / d[a];
                if (ta > tb) {
                    double s = ta;
                    ta = tb;
                    tb = s;
                }
                t0 = Math.max(t0, ta);
                t1 = Math.min(t1, tb);
                if (t0 > t1) hit = false;
            }
            if (hit) return true;
        }
        return false;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
