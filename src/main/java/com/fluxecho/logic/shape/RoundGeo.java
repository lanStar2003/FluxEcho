package com.fluxecho.logic.shape;

import static com.fluxecho.logic.shape.Dir.C;
import static com.fluxecho.logic.shape.Dir.R;
import static com.fluxecho.logic.shape.Dir.S;

import java.util.ArrayList;
import java.util.List;

/**
 * Round (形号 2, style FINAL §3.3.2) and the footing's coping: a cube whose open edges are rounded with radius 0.5 in
 * four segments.
 * <ul>
 * <li>R1: a vertical edge rounds when both its sides and their diagonal are open; R1': with the diagonal closed it
 * stays square, so cells joined only along that edge meet without a seam.</li>
 * <li>R2: a top edge rounds when up, its side and the cell over its side are open. R4: bottom edges never round.</li>
 * <li>R3: where a rounded vertical edge meets two rounded top edges the corner is an eighth of a sphere, two by two
 * segments (four quads, the two at the pole triangles).</li>
 * <li>R5: each face is drawn as what is left of it; R6: concave corners need nothing.</li>
 * </ul>
 * The radius is half the cell, so every rounding lives inside one quadrant of the cell; each of the eight quadrant
 * boxes takes one of eight states from the three edges that meet at its outer corner ({@link #upper}): whole, a
 * vertical or a top cylinder, two top cylinders mitred (square vertical edge), a vertical and a top cylinder meeting in
 * a groin, or the sphere. Cylinders that end on a cell face are cut into four segments there, so they meet the next
 * cell's; the sphere's edges have two, so the three cylinders that end on a sphere narrow from four segments to two in
 * their own length (two of their four pieces are triangles). That keeps the face counts of §3.3.4 and still leaves no
 * gap. The coping is the same with no vertical edge rounded.
 */
final class RoundGeo extends Geo {

    /** A quadrant box's states. */
    static final int FULL = 0, VCYL = 1, TA = 2, TB = 3, MITRE = 4, GROIN_A = 5, GROIN_B = 6, SPHERE = 7;

    private final byte[] vf = new byte[4], tf = new byte[4];

    RoundGeo(Form form, Nbhd n) {
        super(form, n);
    }

    /** Whether the vertical edge at ring corner {@code k} rounds (R1). */
    boolean vertical(int k) {
        k &= 3;
        if (vf[k] == 0) vf[k] = (byte) (form == Form.ROUND && openSide(k) && openSide(k + 1) && openDiag(k) ? 2 : 1);
        return vf[k] == 2;
    }

    /** Whether the top edge on ring side {@code k} rounds (R2). */
    boolean top(int k) {
        k &= 3;
        if (tf[k] == 0) tf[k] = (byte) (openSide(k) && open(Dir.UP) && openUpper(k) ? 2 : 1);
        return tf[k] == 2;
    }

    /**
     * The upper quadrant box at ring corner {@code k}, from its vertical edge and its two top edges: "a" is the edge
     * on ring side {@code k}, "b" the one on {@code k + 1}.
     */
    int upper(int k) {
        boolean v = vertical(k), a = top(k), b = top(k + 1);
        if (v) return a ? (b ? SPHERE : GROIN_A) : (b ? GROIN_B : VCYL);
        return a ? (b ? MITRE : TA) : (b ? TB : FULL);
    }

    // ---- the curved faces

    @Override
    void interior(Mesh m) {
        for (int k = 0; k < 4; k++) if (vertical(k)) verticalEdge(m, k);
        for (int k = 0; k < 4; k++) if (top(k)) topEdge(m, k);
        for (int k = 0; k < 4; k++) if (upper(k) == SPHERE) sphere(m, k);
    }

    /** The rounded vertical edge at corner {@code k}: from the bottom up to its quadrant box's top. */
    private void verticalEdge(Mesh m, int k) {
        int st = upper(k);
        double[][] bottom = new double[5][], top;
        for (int i = 0; i < 5; i++) bottom[i] = vv(k, i, -0.5);
        if (st == SPHERE) top = new double[][] { vv(k, 0, 0), vv(k, 2, 0), vv(k, 4, 0) };
        else {
            top = new double[5][];
            for (int i = 0; i < 5; i++) top[i] = vv(k, i, st == GROIN_A ? R * S[i] : st == GROIN_B ? R * C[i] : 0.5);
        }
        strip(m, bottom, top, Quad.Surface.CYLINDER, 0, -2);
    }

    /** A vertex of the vertical cylinder at corner {@code k}, angle {@code i}, height {@code h}. */
    private static double[] vv(int k, int i, double h) {
        double[] p = Dir.pt(k, R * C[i], k + 1, R * S[i], h);
        double[] nn = Dir.vec(k, C[i], k + 1, S[i], 0);
        return new double[] { p[0], p[1], p[2], nn[0], nn[1], nn[2], R * i * Dir.STEP, 1 - p[1] };
    }

    /** The rounded top edge on ring side {@code k}: between the ends its two corners' boxes give it. */
    private void topEdge(Mesh m, int k) {
        int hi = upper(k), lo = upper(k - 1);
        boolean coarseHi = hi == SPHERE, coarseLo = lo == SPHERE;
        if (coarseHi && coarseLo) return; // both corners are spheres: no length left
        double[][] a = new double[coarseLo ? 3 : 5][], b = new double[coarseHi ? 3 : 5][];
        for (int i = 0, ia = 0, ib = 0; i < 5; i++) {
            if (!coarseLo || i % 2 == 0) a[ia++] = tv(k, i, end(lo, i, -1));
            if (!coarseHi || i % 2 == 0) b[ib++] = tv(k, i, end(hi, i, 1));
        }
        strip(m, a, b, Quad.Surface.CYLINDER, 0, Dir.rx(k + 1) != 0 ? Dir.rx(k + 1) : 3 * Dir.rz(k + 1));
    }

    /**
     * Where a top edge ends at a corner, along ring side {@code k + 1} ({@code side} 1 at corner {@code k}, -1 at
     * corner {@code k - 1}), at arc angle {@code i}: the cell face, the mitre, the groin's seam, or the sphere.
     */
    private static double end(int state, int i, int side) {
        switch (state) {
            case MITRE:
                return side * R * C[i];
            case GROIN_A:
            case GROIN_B:
                return side * R * S[i];
            case SPHERE:
                return 0;
            default:
                return side * 0.5;
        }
    }

    /** A vertex of the top cylinder on ring side {@code k}, angle {@code i}, at {@code s} along ring side k + 1. */
    private static double[] tv(int k, int i, double s) {
        double[] p = Dir.pt(k, R * C[i], k + 1, s, R * S[i]);
        double[] nn = Dir.vec(k, C[i], k + 1, 0, S[i]);
        int axis = Dir.rx(k + 1) != 0 ? Dir.rx(k + 1) : 3 * Dir.rz(k + 1);
        return new double[] { p[0], p[1], p[2], nn[0], nn[1], nn[2], R * i * Dir.STEP, Mesh.tex(p, axis) };
    }

    /** The eighth of a sphere at corner {@code k}, two by two segments. */
    private void sphere(Mesh m, int k) {
        for (int j = 0; j < 4; j += 2) for (int i = 0; i < 4; i += 2) m.curved(
            new double[][] { sv(k, i, j), sv(k, i + 2, j), sv(k, i + 2, j + 2), sv(k, i, j + 2) },
            Quad.Surface.SPHERE,
            0,
            0);
    }

    /** A vertex of the sphere at corner {@code k}: longitude {@code i} from ring side k, latitude {@code j}. */
    private static double[] sv(int k, int i, int j) {
        double[] p = Dir.pt(k, R * C[j] * C[i], k + 1, R * C[j] * S[i], R * S[j]);
        double[] nn = Dir.vec(k, C[j] * C[i], k + 1, C[j] * S[i], S[j]);
        return new double[] { p[0], p[1], p[2], nn[0], nn[1], nn[2], R * i * Dir.STEP, R * (4 - j) * Dir.STEP };
    }

    /**
     * A cylinder between two ends of five vertices each (four segments) or three (two segments, at a sphere). Four
     * against four are four quads; four against two are two triangles and two quads, so the count stays four.
     */
    static void strip(Mesh m, double[][] a, double[][] b, Quad.Surface s, int uAxis, int vAxis) {
        if (a.length == 5 && b.length == 5) {
            for (int i = 0; i < 4; i++) m.curved(new double[][] { a[i], a[i + 1], b[i + 1], b[i] }, s, uAxis, vAxis);
            return;
        }
        double[][] f = a.length == 5 ? a : b, c = a.length == 5 ? b : a;
        m.curved(new double[][] { f[0], f[1], c[0] }, s, uAxis, vAxis);
        m.curved(new double[][] { f[1], f[2], c[1], c[0] }, s, uAxis, vAxis);
        m.curved(new double[][] { f[2], f[3], c[1] }, s, uAxis, vAxis);
        m.curved(new double[][] { f[3], f[4], c[2], c[1] }, s, uAxis, vAxis);
    }

    // ---- the faces on the cell's faces

    @Override
    List<Piece> section(int face) {
        List<Piece> out = new ArrayList<>(4);
        if (face == Dir.UP || face == Dir.DOWN) {
            double h = face == Dir.UP ? 0.5 : -0.5;
            for (int k = 0; k < 4; k++) {
                int st = face == Dir.UP ? upper(k) : vertical(k) ? VCYL : FULL;
                if (st == FULL) out.add(capQuad(k, h));
                else if (st == VCYL) out.add(capDisc(k, h));
            }
            return out;
        }
        int j = Dir.ringOf(face);
        // toward ring side j + 1: corner j, whose side "a" this face is
        if (!vertical(j)) out.add(sideQuad(j, 0));
        int up = upper(j);
        if (up == FULL) out.add(sideQuad(j, 1));
        else if (up == TB) out.add(sideDisc(j, 1));
        // toward ring side j - 1: corner j - 1, whose side "b" this face is
        if (!vertical(j - 1)) out.add(sideQuad(j, 2));
        up = upper(j - 1);
        if (up == FULL) out.add(sideQuad(j, 3));
        else if (up == TA) out.add(sideDisc(j, -1));
        return out;
    }

    /** The quarter disc a vertical cylinder leaves on the top or bottom face at corner {@code k}. */
    private static Piece capDisc(int k, double h) {
        double[][] arc = new double[5][];
        for (int i = 0; i < 5; i++) arc[i] = Dir.pt(k, R * C[i], k + 1, R * S[i], h);
        return disc(Dir.pt(k, 0, k + 1, 0, h), arc);
    }

    /** The quarter disc a top cylinder ending on the side face of ring side {@code j} leaves there (t > 0 or < 0). */
    private static Piece sideDisc(int j, int sign) {
        double[][] arc = new double[5][];
        for (int i = 0; i < 5; i++) arc[i] = Dir.pt(j, 0.5, j + 1, sign * R * C[i], R * S[i]);
        return disc(Dir.pt(j, 0.5, j + 1, 0, 0), arc);
    }
}
