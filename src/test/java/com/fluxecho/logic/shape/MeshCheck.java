package com.fluxecho.logic.shape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Checks on meshes made of polygons (lists of 3D points) for ShapeGeoTest: whether they close up into the surface of
 * a solid, every edge walked once each way (the manifold test of style §3.3.7 #2), and the volume they enclose.
 * <p>
 * An edge that one face has whole and the next face has in pieces (a T-junction, where a merged rectangle meets two
 * quadrant pieces) is cut at the vertices lying on it before the count, so it passes: it leaves no gap. A real gap, a
 * chord of one arc against two chords of another, leaves a vertex off the edge and fails.
 */
final class MeshCheck {

    private static final double EPS = 1e-7, CELL = 0.25;

    private MeshCheck() {}

    /** The quads as polygons, a triangle's repeated vertex dropped. */
    static List<double[][]> polys(List<Quad> quads) {
        List<double[][]> out = new ArrayList<>(quads.size());
        for (Quad q : quads) {
            double[][] p = new double[4][];
            for (int i = 0; i < 4; i++) p[i] = new double[] { q.x(i), q.y(i), q.z(i) };
            out.add(Poly.dedup(p));
        }
        return out;
    }

    /** The polygons moved by {@code (dx, dy, dz)}. */
    static List<double[][]> moved(List<double[][]> polys, double dx, double dy, double dz) {
        List<double[][]> out = new ArrayList<>(polys.size());
        for (double[][] p : polys) {
            double[][] m = new double[p.length][];
            for (int i = 0; i < p.length; i++) m[i] = new double[] { p[i][0] + dx, p[i][1] + dy, p[i][2] + dz };
            out.add(m);
        }
        return out;
    }

    /** The volume the polygons enclose, positive when they face outward (fanned from each first point). */
    static double volume(List<double[][]> polys) {
        double v = 0;
        for (double[][] p : polys) for (int i = 1; i + 1 < p.length; i++) {
            double[] a = p[0], b = p[i], c = p[i + 1];
            v += a[0] * (b[1] * c[2] - b[2] * c[1]) - a[1] * (b[0] * c[2] - b[2] * c[0])
                + a[2] * (b[0] * c[1] - b[1] * c[0]);
        }
        return v / 6;
    }

    /**
     * Null when every edge of the polygons is walked as often one way as the other; else the first edge that is not.
     */
    static String closure(List<double[][]> polys) {
        List<double[]> verts = new ArrayList<>();
        Map<Key, Integer> ids = new HashMap<>();
        Map<Key, List<Integer>> grid = new HashMap<>();
        List<int[]> faces = new ArrayList<>(polys.size());
        for (double[][] p : polys) {
            int[] f = new int[p.length];
            for (int i = 0; i < p.length; i++) {
                Key k = new Key(p[i], 1 / EPS);
                Integer id = ids.get(k);
                if (id == null) {
                    id = verts.size();
                    verts.add(p[i]);
                    ids.put(k, id);
                    grid.computeIfAbsent(Key.cellOf(p[i]), x -> new ArrayList<>())
                        .add(id);
                }
                f[i] = id;
            }
            faces.add(f);
        }
        Map<Long, int[]> count = new HashMap<>();
        for (int[] f : faces) for (int i = 0; i < f.length; i++) {
            int a = f[i], b = f[(i + 1) % f.length];
            if (a == b) continue;
            List<Integer> chain = split(verts, grid, a, b);
            for (int c = 0; c + 1 < chain.size(); c++) {
                int u = chain.get(c), w = chain.get(c + 1);
                long key = (long) Math.min(u, w) << 32 | Math.max(u, w);
                count.computeIfAbsent(key, x -> new int[2])[u < w ? 0 : 1]++;
            }
        }
        for (Map.Entry<Long, int[]> e : count.entrySet()) if (e.getValue()[0] != e.getValue()[1]) {
            int u = (int) (e.getKey() >> 32), w = (int) (long) e.getKey();
            return String.format(
                java.util.Locale.ROOT,
                "edge (%.4f %.4f %.4f)-(%.4f %.4f %.4f) walked %d and %d times",
                verts.get(u)[0],
                verts.get(u)[1],
                verts.get(u)[2],
                verts.get(w)[0],
                verts.get(w)[1],
                verts.get(w)[2],
                e.getValue()[0],
                e.getValue()[1]);
        }
        return null;
    }

    /** The edge from vertex a to b as the chain of vertices lying on it, in order. */
    private static List<Integer> split(List<double[]> verts, Map<Key, List<Integer>> grid, int a, int b) {
        double[] p = verts.get(a), q = verts.get(b);
        double dx = q[0] - p[0], dy = q[1] - p[1], dz = q[2] - p[2], len2 = dx * dx + dy * dy + dz * dz;
        List<double[]> on = new ArrayList<>();
        int x0 = (int) Math.floor(Math.min(p[0], q[0]) / CELL) - 1,
            x1 = (int) Math.floor(Math.max(p[0], q[0]) / CELL) + 1;
        int y0 = (int) Math.floor(Math.min(p[1], q[1]) / CELL) - 1,
            y1 = (int) Math.floor(Math.max(p[1], q[1]) / CELL) + 1;
        int z0 = (int) Math.floor(Math.min(p[2], q[2]) / CELL) - 1,
            z1 = (int) Math.floor(Math.max(p[2], q[2]) / CELL) + 1;
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
            List<Integer> cell = grid.get(new Key(x, y, z));
            if (cell == null) continue;
            for (int id : cell) {
                if (id == a || id == b) continue;
                double[] v = verts.get(id);
                double t = ((v[0] - p[0]) * dx + (v[1] - p[1]) * dy + (v[2] - p[2]) * dz) / len2;
                if (t <= 1e-9 || t >= 1 - 1e-9) continue;
                double ex = p[0] + t * dx - v[0], ey = p[1] + t * dy - v[1], ez = p[2] + t * dz - v[2];
                if (ex * ex + ey * ey + ez * ez < EPS * EPS) on.add(new double[] { t, id });
            }
        }
        on.sort((m, n) -> Double.compare(m[0], n[0]));
        List<Integer> chain = new ArrayList<>(on.size() + 2);
        chain.add(a);
        for (double[] o : on) chain.add((int) o[1]);
        chain.add(b);
        return chain;
    }

    /** A point rounded to a grid. */
    private static final class Key {

        final long x, y, z;

        Key(double[] p, double scale) {
            this(Math.round(p[0] * scale), Math.round(p[1] * scale), Math.round(p[2] * scale));
        }

        Key(long x, long y, long z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        /** The grid cell of a point (floor, not round). */
        static Key cellOf(double[] p) {
            return new Key(
                (long) Math.floor(p[0] / CELL),
                (long) Math.floor(p[1] / CELL),
                (long) Math.floor(p[2] / CELL));
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && k.x == x && k.y == y && k.z == z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }
    }
}
