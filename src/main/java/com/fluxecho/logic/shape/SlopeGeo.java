package com.fluxecho.logic.shape;

import java.util.ArrayList;
import java.util.List;

/**
 * Slope (形号 3, style FINAL §3.3.3): a cube cut by 45-degree planes where it meets the open.
 * <ul>
 * <li>S1: a vertical edge whose sides and diagonal are open is chamfered by the vertical plane through the two face
 * midpoints (an eighth of the cell goes); with the diagonal closed it stays square.</li>
 * <li>S2: a cell with two adjacent sides open, the two others closed and both cells along the 45-degree line closed
 * (a thick wall's stepped 45-degree edge) is cut along its diagonal: the cuts of the cells along the line make one flat
 * 45-degree wall.</li>
 * <li>S3: with up open, every open side is an eave at half height and the roof rises at 45 degrees from it to the full
 * height at the cell's middle: one open side gives a wedge, two opposite ones a gable ridge, two adjacent ones a hip
 * corner; over a chamfered corner a triangle runs from the chamfer's eave up to the middle.</li>
 * <li>S4: a one-cell wall's end (three sides open) is two chamfers meeting in a bow; the two roof triangles over them
 * are one (folded) quad.</li>
 * </ul>
 * Each quadrant column of the cell is whole, chamfered, cut away by S2 or halved by it ({@link #column}); each upper
 * quadrant box takes its roof from whether its two sides are open ({@link #roof}). On a side face toward a closed
 * cell the section is always the lower half whole and, above it, a whole quadrant or the triangle a perpendicular
 * roof leaves ({@link #section}), so a neighbour can work it out without knowing whether S2 applies.
 */
final class SlopeGeo extends Geo {

    /** A quadrant column's states. */
    static final int FULL = 0, CHAMFER = 1, REMOVED = 2, KEEP_A = 3, KEEP_B = 4;
    /** An upper quadrant box's roofs. */
    static final int NONE = 0, FLAT = 1, FACET_A = 2, FACET_B = 3, HIP = 4, CHAMFER_ROOF = 5;

    private static final double SQ2 = Math.sqrt(0.5), SQ3 = Math.sqrt(1.0 / 3);
    private int s2 = -2;

    SlopeGeo(Form form, Nbhd n) {
        super(form, n);
    }

    /** The ring corner S2 cuts away (its two sides are the open ones), or -1. */
    int s2() {
        if (s2 == -2) {
            s2 = -1;
            for (int k = 0; k < 4; k++) if (openSide(k) && openSide(
                k + 1) && !openSide(k + 2) && !openSide(k + 3) && !openDiag(k + 1) && !openDiag(k + 3)) s2 = k;
        }
        return s2;
    }

    /** Whether corner {@code k} is chamfered (S1). */
    boolean chamfer(int k) {
        return openSide(k) && openSide(k + 1) && openDiag(k);
    }

    /** The quadrant column at ring corner {@code k}. */
    int column(int k) {
        int s = s2();
        if (s >= 0) {
            int d = (k - s) & 3;
            // the cut corner goes; its neighbours keep the half toward their closed side
            return d == 0 ? REMOVED : d == 1 ? KEEP_B : d == 3 ? KEEP_A : FULL;
        }
        return chamfer(k) ? CHAMFER : FULL;
    }

    /** The roof over the quadrant at ring corner {@code k} ("a" is ring side k, "b" ring side k + 1). */
    int roof(int k) {
        if (!open(Dir.UP)) return NONE;
        int c = column(k);
        if (c == REMOVED) return NONE;
        boolean a = openSide(k), b = openSide(k + 1);
        if (!a && !b) return FLAT;
        if (a && !b) return FACET_A;
        if (!a) return FACET_B;
        return c == CHAMFER ? CHAMFER_ROOF : HIP;
    }

    // ---- inside the cell

    @Override
    void interior(Mesh m) {
        boolean up = open(Dir.UP);
        for (int k = 0; k < 4; k++) if (column(k) == CHAMFER) {
            double top = up ? 0 : 0.5;
            m.flat(
                new double[][] { Dir.pt(k, 0.5, k + 1, 0, -0.5), Dir.pt(k, 0, k + 1, 0.5, -0.5),
                    Dir.pt(k, 0, k + 1, 0.5, top), Dir.pt(k, 0.5, k + 1, 0, top) },
                Dir.vec(k, SQ2, k + 1, SQ2, 0),
                -1,
                Overlay.NONE,
                0);
        }
        int s = s2();
        if (s >= 0) {
            double[][] face = up
                ? new double[][] { Dir.pt(s, 0.5, s + 1, -0.5, -0.5), Dir.pt(s, -0.5, s + 1, 0.5, -0.5),
                    Dir.pt(s, -0.5, s + 1, 0.5, 0), Dir.pt(s, 0, s + 1, 0, 0.5), Dir.pt(s, 0.5, s + 1, -0.5, 0) }
                : new double[][] { Dir.pt(s, 0.5, s + 1, -0.5, -0.5), Dir.pt(s, -0.5, s + 1, 0.5, -0.5),
                    Dir.pt(s, -0.5, s + 1, 0.5, 0.5), Dir.pt(s, 0.5, s + 1, -0.5, 0.5) };
            m.flat(face, Dir.vec(s, SQ2, s + 1, SQ2, 0), -1, Overlay.NONE, 0);
        }
        if (up) roofs(m);
    }

    /** The roof facets: per side the pieces of its quadrants, merged when one convex polygon; the chamfer roofs. */
    private void roofs(Mesh m) {
        boolean[] cham = new boolean[4];
        for (int k = 0; k < 4; k++) cham[k] = roof(k) == CHAMFER_ROOF;
        for (int j = 0; j < 4; j++) {
            double[] nrm = Dir.vec(j, SQ2, j + 1, 0, SQ2);
            // ring side j's facet: its piece over corner j - 1 (that corner's side b), then over corner j (side a)
            List<double[][]> ps = new ArrayList<>(2);
            int r = roof(j - 1);
            if (r == FACET_B) ps.add(lift(j - 1, columnPlan(j - 1), false));
            else if (r == HIP) ps.add(lift(j - 1, new double[][] { { 0, 0 }, { 0.5, 0.5 }, { 0, 0.5 } }, false));
            r = roof(j);
            if (r == FACET_A) ps.add(lift(j, columnPlan(j), true));
            else if (r == HIP) ps.add(lift(j, new double[][] { { 0, 0 }, { 0.5, 0 }, { 0.5, 0.5 } }, true));
            double[][] merged = ps.size() == 2 ? Poly.merge(ps.get(0), ps.get(1), nrm) : null;
            if (merged != null) m.flat(merged, nrm, -1, Overlay.NONE, 0);
            else for (double[][] p : ps) m.flat(p, nrm, -1, Overlay.NONE, 0);
        }
        int count = 0;
        for (boolean c : cham) if (c) count++;
        for (int k = 0; k < 4; k++) {
            if (!cham[k]) continue;
            if (count == 2 && cham[(k + 3) & 3]) continue; // drawn with corner k - 1 as the bow below
            if (count == 2 && cham[(k + 1) & 3]) bow(m, k + 1);
            else m.flat(
                new double[][] { Dir.pt(k, 0, k + 1, 0, 0.5), Dir.pt(k, 0.5, k + 1, 0, 0),
                    Dir.pt(k, 0, k + 1, 0.5, 0) },
                Dir.vec(k, SQ3, k + 1, SQ3, SQ3),
                -1,
                Overlay.NONE,
                0);
        }
    }

    /**
     * The bow (S4): the two chamfer roofs at corners {@code k - 1} and {@code k}, which share ring side {@code k}'s
     * midpoint, as one quad folded along the line from the middle of the top to that midpoint.
     */
    private static void bow(Mesh m, int k) {
        double[] apex = Dir.pt(k, 0, k + 1, 0, 0.5), tip = Dir.pt(k, 0.5, k + 1, 0, 0);
        double[] next = Dir.pt(k, 0, k + 1, 0.5, 0), prev = Dir.pt(k - 1, 0.5, k, 0, 0);
        double[] nk = Dir.vec(k, SQ3, k + 1, SQ3, SQ3), np = Dir.vec(k - 1, SQ3, k, SQ3, SQ3);
        double[] mid = Dir.unit(new double[] { nk[0] + np[0], nk[1] + np[1], nk[2] + np[2] });
        m.curved(new double[][] { bv(apex, mid), bv(next, nk), bv(tip, mid), bv(prev, np) }, Quad.Surface.PLANE, 1, 3);
    }

    /** A bow vertex, textured like a top face. */
    private static double[] bv(double[] p, double[] nrm) {
        return new double[] { p[0], p[1], p[2], nrm[0], nrm[1], nrm[2], p[0], p[2] };
    }

    /** A quadrant column's plan in its corner's (u, w), for the facets over it. */
    private double[][] columnPlan(int k) {
        switch (column(k)) {
            case KEEP_A:
                return new double[][] { { 0, 0 }, { 0.5, 0 }, { 0.5, 0.5 } };
            case KEEP_B:
                return new double[][] { { 0, 0 }, { 0.5, 0.5 }, { 0, 0.5 } };
            default:
                return new double[][] { { 0, 0 }, { 0.5, 0 }, { 0.5, 0.5 }, { 0, 0.5 } };
        }
    }

    /** A plan in corner {@code k}'s (u, w) lifted onto the facet of side a ({@code h = 0.5 - u}) or b. */
    private static double[][] lift(int k, double[][] plan, boolean a) {
        double[][] p = new double[plan.length][];
        for (int i = 0; i < plan.length; i++)
            p[i] = Dir.pt(k, plan[i][0], k + 1, plan[i][1], 0.5 - (a ? plan[i][0] : plan[i][1]));
        return p;
    }

    // ---- on the cell's faces

    @Override
    List<Piece> section(int face) {
        List<Piece> out = new ArrayList<>(4);
        if (face == Dir.UP || face == Dir.DOWN) {
            double h = face == Dir.UP ? 0.5 : -0.5;
            for (int k = 0; k < 4; k++) {
                if (face == Dir.UP) {
                    int r = roof(k);
                    if (r != NONE && r != FLAT) continue;
                }
                Piece p = plan(k, h);
                if (p != null) out.add(p);
            }
            return out;
        }
        int j = Dir.ringOf(face);
        boolean up = open(Dir.UP);
        if (!openSide(j)) {
            // toward a closed cell: the lower half, and above it the triangle a perpendicular roof leaves
            out.add(sideQuad(j, 0));
            out.add(up && openSide(j + 1) ? new Piece(sideRect3(j, 1)) : sideQuad(j, 1));
            out.add(sideQuad(j, 2));
            out.add(up && openSide(j - 1) ? new Piece(sideRect3(j, -1)) : sideQuad(j, 3));
            return out;
        }
        int c = column(j);
        if (c == FULL || c == KEEP_A) {
            out.add(sideQuad(j, 0));
            if (roof(j) == NONE) out.add(sideQuad(j, 1));
        }
        c = column(j - 1);
        if (c == FULL || c == KEEP_B) {
            out.add(sideQuad(j, 2));
            if (roof(j - 1) == NONE) out.add(sideQuad(j, 3));
        }
        return out;
    }

    /** The triangle a roof rising from ring side j ± 1 leaves in the upper quadrant of side face j on that side. */
    private static double[][] sideRect3(int j, int sign) {
        return new double[][] { Dir.pt(j, 0.5, j + 1, 0, 0), Dir.pt(j, 0.5, j + 1, sign * 0.5, 0),
            Dir.pt(j, 0.5, j + 1, 0, 0.5) };
    }

    /** A quadrant column's plan on the top or bottom face, or null when it is cut away. */
    private Piece plan(int k, double h) {
        switch (column(k)) {
            case FULL:
                return capQuad(k, h);
            case CHAMFER:
                return new Piece(
                    new double[][] { Dir.pt(k, 0, k + 1, 0, h), Dir.pt(k, 0.5, k + 1, 0, h),
                        Dir.pt(k, 0, k + 1, 0.5, h) });
            case KEEP_A:
                return new Piece(
                    new double[][] { Dir.pt(k, 0, k + 1, 0, h), Dir.pt(k, 0.5, k + 1, 0, h),
                        Dir.pt(k, 0.5, k + 1, 0.5, h) });
            case KEEP_B:
                return new Piece(
                    new double[][] { Dir.pt(k, 0, k + 1, 0, h), Dir.pt(k, 0.5, k + 1, 0.5, h),
                        Dir.pt(k, 0, k + 1, 0.5, h) });
            default:
                return null;
        }
    }

    // ---- boxes

    /** S2's cell is a stair of two boxes: the lower half whole, the upper half on the closed side (§3.3.5). */
    @Override
    List<double[]> boxes() {
        int s = s2();
        if (s < 0) return super.boxes();
        List<double[]> out = new ArrayList<>(2);
        out.add(box(0, 0, 0, 1, 0.5, 1));
        // the upper half away from ring side s
        double x0 = Dir.rx(s) > 0 ? 0 : Dir.rx(s) < 0 ? 0.5 : 0, x1 = Dir.rx(s) > 0 ? 0.5 : 1;
        double z0 = Dir.rz(s) > 0 ? 0 : Dir.rz(s) < 0 ? 0.5 : 0, z1 = Dir.rz(s) > 0 ? 0.5 : 1;
        out.add(box(x0, 0.5, z0, x1, 1, z1));
        return out;
    }

    @Override
    List<double[]> occluders() {
        return super.boxes();
    }
}
