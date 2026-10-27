package com.fluxecho.logic.shape;

import java.util.ArrayList;
import java.util.List;

/**
 * Convex polygons as arrays of 3D points: the pieces faces are made of, their difference on a cell face (what one
 * cell must draw where its neighbour covers part of the shared face) and the merging of coplanar neighbours into one
 * polygon. Every result keeps the order its inputs came in, so pieces built in a turned frame come out turned.
 */
final class Poly {

    /** Two points closer than this are one point; a polygon smaller than {@link #AREA} is no polygon. */
    static final double EPS = 1e-9, AREA = 1e-9;

    private Poly() {}

    /** The polygon without repeated consecutive points (the last against the first too). */
    static double[][] dedup(double[][] p) {
        List<double[]> out = new ArrayList<>(p.length);
        for (double[] q : p) if (out.isEmpty() || !same(out.get(out.size() - 1), q)) out.add(q);
        while (out.size() > 1 && same(out.get(0), out.get(out.size() - 1))) out.remove(out.size() - 1);
        return out.toArray(new double[0][]);
    }

    /** Whether two points coincide. */
    static boolean same(double[] a, double[] b) {
        return Math.abs(a[0] - b[0]) < EPS && Math.abs(a[1] - b[1]) < EPS && Math.abs(a[2] - b[2]) < EPS;
    }

    /** Newell's normal of a polygon: its direction is the winding's, its length twice the area. */
    static double[] newell(double[][] p) {
        double x = 0, y = 0, z = 0;
        for (int i = 0; i < p.length; i++) {
            double[] a = p[i], b = p[(i + 1) % p.length];
            x += (a[1] - b[1]) * (a[2] + b[2]);
            y += (a[2] - b[2]) * (a[0] + b[0]);
            z += (a[0] - b[0]) * (a[1] + b[1]);
        }
        return new double[] { x, y, z };
    }

    /** The polygon wound the other way, still starting at its first point. */
    static double[][] reverse(double[][] p) {
        double[][] r = new double[p.length][];
        r[0] = p[0];
        for (int i = 1; i < p.length; i++) r[i] = p[p.length - i];
        return r;
    }

    /** The polygon wound counter-clockwise about {@code n}. */
    static double[][] facing(double[][] p, double[] n) {
        double[] w = newell(p);
        return w[0] * n[0] + w[1] * n[1] + w[2] * n[2] < 0 ? reverse(p) : p;
    }

    // ---- on a cell face: the two coordinates in the face's plane

    /** The in-plane coordinates of a face: x and z on up and down, x and y on north and south, z and y otherwise. */
    private static int[] axes(int face) {
        switch (face) {
            case Dir.DOWN:
            case Dir.UP:
                return new int[] { 0, 2 };
            case Dir.NORTH:
            case Dir.SOUTH:
                return new int[] { 0, 1 };
            default:
                return new int[] { 2, 1 };
        }
    }

    /** The signed area of a polygon in a face's plane. */
    private static double area(double[][] p, int i, int j) {
        double s = 0;
        for (int k = 0; k < p.length; k++) {
            double[] a = p[k], b = p[(k + 1) % p.length];
            s += a[i] * b[j] - b[i] * a[j];
        }
        return s / 2;
    }

    /** The area of a polygon lying on a cell face. */
    static double area(double[][] p, int face) {
        int[] ax = axes(face);
        return Math.abs(area(p, ax[0], ax[1]));
    }

    /**
     * What of the polygons {@code a} the polygons {@code b} leave uncovered, on cell face {@code face}: convex pieces,
     * none smaller than {@link #AREA}.
     */
    static List<double[][]> subtract(List<double[][]> a, List<double[][]> b, int face) {
        int[] ax = axes(face);
        int i = ax[0], j = ax[1];
        List<double[][]> out = new ArrayList<>();
        for (double[][] p : a) {
            // only the cuts that overlap the piece, so far-off edges do not split it, in an order of the piece's own
            List<double[][]> cuts = new ArrayList<>();
            for (double[][] c : b) if (Math.abs(area(c, i, j)) >= AREA && overlaps(p, c, i, j)) cuts.add(c);
            if (cuts.size() > 1) sortAround(p, cuts, i, j);
            List<double[][]> cur = new ArrayList<>();
            cur.add(p);
            for (double[][] c : cuts) {
                List<double[][]> next = new ArrayList<>();
                for (double[][] q : cur) diff(q, c, area(c, i, j) > 0, i, j, next);
                cur = next;
            }
            out.addAll(cur);
        }
        return out;
    }

    /** Whether two convex polygons on a face share some area. */
    private static boolean overlaps(double[][] p, double[][] c, int i, int j) {
        boolean ccw = area(c, i, j) > 0;
        double[][] rest = p;
        for (int e = 0; e < c.length && rest != null; e++) rest = clip(rest, c[e], c[(e + 1) % c.length], ccw, i, j);
        return rest != null && Math.abs(area(rest, i, j)) > AREA;
    }

    /**
     * Orders the cuts by the direction of their middles from the piece's middle, measured from the piece's first edge
     * toward its inside: a measure of the piece itself, so a turned or mirrored face orders its cuts the same way.
     */
    private static void sortAround(double[][] p, List<double[][]> cuts, int i, int j) {
        double[] m = mid(p, i, j);
        double ex = p[1][i] - p[0][i], ey = p[1][j] - p[0][j], side = area(p, i, j) > 0 ? 1 : -1;
        cuts.sort((x, y) -> {
            double[] a = mid(x, i, j), b = mid(y, i, j);
            int c = Double
                .compare(angle(a[0] - m[0], a[1] - m[1], ex, ey, side), angle(b[0] - m[0], b[1] - m[1], ex, ey, side));
            return c != 0 ? c
                : Double.compare(Math.hypot(a[0] - m[0], a[1] - m[1]), Math.hypot(b[0] - m[0], b[1] - m[1]));
        });
    }

    private static double angle(double dx, double dy, double ex, double ey, double side) {
        double a = Math.atan2(side * (ex * dy - ey * dx), ex * dx + ey * dy);
        return a < -1e-12 ? a + 2 * Math.PI : Math.max(a, 0);
    }

    private static double[] mid(double[][] p, int i, int j) {
        double x = 0, y = 0;
        for (double[] q : p) {
            x += q[i];
            y += q[j];
        }
        return new double[] { x / p.length, y / p.length };
    }

    /** {@code p} minus the convex {@code cut}: peel off what lies outside each of the cut's edges in turn. */
    private static void diff(double[][] p, double[][] cut, boolean ccw, int i, int j, List<double[][]> out) {
        double[][] rest = p;
        for (int e = 0; e < cut.length && rest != null; e++) {
            double[] a = cut[e], b = cut[(e + 1) % cut.length];
            double[][] outside = clip(rest, a, b, !ccw, i, j);
            if (outside != null && Math.abs(area(outside, i, j)) > AREA) out.add(outside);
            rest = clip(rest, a, b, ccw, i, j);
            if (rest != null && Math.abs(area(rest, i, j)) <= AREA) rest = null;
        }
    }

    /** The part of a convex polygon on the left (or right) of the line through {@code a} and {@code b}, or null. */
    private static double[][] clip(double[][] p, double[] a, double[] b, boolean left, int i, int j) {
        List<double[]> out = new ArrayList<>(p.length + 2);
        double ex = b[i] - a[i], ey = b[j] - a[j], sign = left ? 1 : -1;
        for (int k = 0; k < p.length; k++) {
            double[] s = p[k], t = p[(k + 1) % p.length];
            double ds = sign * (ex * (s[j] - a[j]) - ey * (s[i] - a[i]));
            double dt = sign * (ex * (t[j] - a[j]) - ey * (t[i] - a[i]));
            if (ds >= -EPS) out.add(s);
            if (ds > EPS && dt < -EPS || ds < -EPS && dt > EPS) {
                double f = ds / (ds - dt);
                out.add(new double[] { s[0] + (t[0] - s[0]) * f, s[1] + (t[1] - s[1]) * f, s[2] + (t[2] - s[2]) * f });
            }
        }
        double[][] r = dedup(out.toArray(new double[0][]));
        return r.length >= 3 ? r : null;
    }

    // ---- merging coplanar pieces

    /**
     * The union of two coplanar convex polygons sharing a whole edge, when it is convex itself; null otherwise. Both
     * are taken wound about {@code n}; the result starts where the shared edge ends in {@code p}.
     */
    static double[][] merge(double[][] p, double[][] q, double[] n) {
        p = facing(p, n);
        q = facing(q, n);
        for (int a = 0; a < p.length; a++) for (int b = 0; b < q.length; b++) {
            if (!same(p[a], q[(b + 1) % q.length]) || !same(p[(a + 1) % p.length], q[b])) continue;
            List<double[]> m = new ArrayList<>();
            for (int k = 1; k <= p.length; k++) m.add(p[(a + k) % p.length]);
            for (int k = 2; k < q.length; k++) m.add(q[(b + k) % q.length]);
            double[][] r = dropCollinear(m.toArray(new double[0][]), n);
            return convex(r, n) ? r : null;
        }
        return null;
    }

    /** The polygon without points lying on the line through their neighbours. */
    private static double[][] dropCollinear(double[][] p, double[] n) {
        List<double[]> out = new ArrayList<>();
        for (int k = 0; k < p.length; k++) {
            double[] a = p[(k + p.length - 1) % p.length], b = p[k], c = p[(k + 1) % p.length];
            if (Math.abs(turn(a, b, c, n)) > EPS) out.add(b);
        }
        return out.toArray(new double[0][]);
    }

    /** Whether a polygon wound about {@code n} turns left at every corner. */
    static boolean convex(double[][] p, double[] n) {
        if (p.length < 3) return false;
        for (int k = 0; k < p.length; k++)
            if (turn(p[k], p[(k + 1) % p.length], p[(k + 2) % p.length], n) <= EPS) return false;
        return true;
    }

    /** How far the path a, b, c turns left about {@code n}. */
    private static double turn(double[] a, double[] b, double[] c, double[] n) {
        double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
        double vx = c[0] - b[0], vy = c[1] - b[1], vz = c[2] - b[2];
        return (uy * vz - uz * vy) * n[0] + (uz * vx - ux * vz) * n[1] + (ux * vy - uy * vx) * n[2];
    }
}
