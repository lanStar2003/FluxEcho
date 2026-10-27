package com.fluxecho.logic.shape;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects a cell's quads: flat convex polygons fanned into quads with the face normal and projected texture
 * coordinates, and curved patches of three or four vertices that carry their own normals and texture coordinates.
 * Every polygon is wound to face its normal before it is cut, so callers never mind the winding.
 */
final class Mesh {

    final List<Quad> out = new ArrayList<>();

    /**
     * A flat convex polygon with outward normal {@code n}: fanned from its first point into quads (a triangle when
     * three points are left), textured by projection along the normal's dominant axis.
     */
    void flat(double[][] poly, double[] n, int face, Overlay overlay, int conn) {
        double[][] p = Poly.dedup(poly);
        if (p.length < 3) return;
        p = Poly.facing(p, n);
        int[] axes = uvAxes(n);
        for (int i = 1; i + 1 < p.length; i += 2) {
            double[] d = i + 2 < p.length ? p[i + 2] : p[i + 1];
            double[][] v = { vertex(p[0], n, axes), vertex(p[i], n, axes), vertex(p[i + 1], n, axes),
                vertex(d, n, axes) };
            out.add(new Quad(v, face, axes[0], axes[1], overlay, conn, Quad.Surface.PLANE));
        }
    }

    /**
     * A curved patch: three or four vertices of eight numbers each (position, normal, u, v), wound to face the sum of
     * its normals; points that repeat are dropped, and a patch of fewer than three points is none.
     */
    void curved(double[][] verts, Quad.Surface surface, int uAxis, int vAxis) {
        List<double[]> vs = new ArrayList<>(4);
        for (double[] v : verts) if (vs.isEmpty() || !Poly.same(vs.get(vs.size() - 1), v)) vs.add(v);
        while (vs.size() > 1 && Poly.same(vs.get(0), vs.get(vs.size() - 1))) vs.remove(vs.size() - 1);
        if (vs.size() < 3) return;
        double[][] p = vs.toArray(new double[0][]);
        double[] sum = new double[3];
        for (double[] v : p) for (int k = 0; k < 3; k++) sum[k] += v[3 + k];
        double[] w = Poly.newell(p);
        if (w[0] * sum[0] + w[1] * sum[1] + w[2] * sum[2] < 0) p = Poly.reverse(p);
        double[][] q = p.length == 4 ? p : new double[][] { p[0], p[1], p[2], p[2] };
        out.add(new Quad(q, -1, uAxis, vAxis, Overlay.NONE, 0, surface));
    }

    /** A vertex of a flat polygon: its point, the face normal and the projected texture coordinates. */
    private static double[] vertex(double[] p, double[] n, int[] axes) {
        return new double[] { p[0], p[1], p[2], n[0], n[1], n[2], tex(p, axes[0]), tex(p, axes[1]) };
    }

    /** The texture coordinate a world axis gives a point: the coordinate, or one minus it along a negative axis. */
    static double tex(double[] p, int axis) {
        double c = p[Math.abs(axis) - 1];
        return axis > 0 ? c : 1 - c;
    }

    /**
     * The texture axes of a flat face with normal {@code n}, projected along its dominant axis (up and down first,
     * then east-west, then north-south): up and down give u = x and v = z; side faces give u to the viewer's right and
     * v = 1 - y.
     */
    static int[] uvAxes(double[] n) {
        double ax = Math.abs(n[0]), ay = Math.abs(n[1]), az = Math.abs(n[2]);
        if (ay >= ax - Poly.EPS && ay >= az - Poly.EPS) return new int[] { 1, 3 };
        if (ax >= az - Poly.EPS) return new int[] { n[0] > 0 ? -3 : 3, -2 };
        return new int[] { n[2] > 0 ? 1 : -1, -2 };
    }
}
