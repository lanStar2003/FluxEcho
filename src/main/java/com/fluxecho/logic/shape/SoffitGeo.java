package com.fluxecho.logic.shape;

import static com.fluxecho.logic.shape.Dir.C;
import static com.fluxecho.logic.shape.Dir.S;

import java.util.ArrayList;
import java.util.List;

/**
 * Soffit (拱腹, 形号 13; the footing's 8, a skin's 6; style FINAL §3.2): the upper corner of a door or window opening
 * ground into a round shoulder, a concave quarter cylinder of radius 1 in four segments.
 * <p>
 * It takes its direction from its neighbours: below it and on one side ("a") is air (the opening), on the opposite
 * side and above is solid (the jamb and the lintel). The quarter cylinder is centred on the cell's bottom edge on side
 * a and runs along the wall, from the jamb side's bottom edge to side a's top edge: the cell keeps the jamb and the
 * lintel and loses side a and the bottom. Where two adjacent sides both qualify the first in turning order whose
 * successor also does wins; with no side qualifying the soffit is a plain cube. Its ends, where the wall's faces are,
 * are fans of four triangles from the jamb's top corner.
 */
final class SoffitGeo extends Geo {

    private int dir = -2;

    SoffitGeo(Form form, Nbhd n) {
        super(form, n);
    }

    /** The open side "a" (a ring index), or -1 when the neighbours make no soffit. */
    int dir() {
        if (dir == -2) {
            dir = -1;
            if (open(Dir.DOWN) && !open(Dir.UP)) {
                boolean[] q = new boolean[4];
                for (int k = 0; k < 4; k++) q[k] = openSide(k) && !openSide(k + 2);
                for (int k = 0; k < 4 && dir < 0; k++) if (q[k] && (q[(k + 1) & 3] || !q[(k + 3) & 3])) dir = k;
            }
        }
        return dir;
    }

    @Override
    void interior(Mesh m) {
        int k = dir();
        if (k < 0) return;
        int axis = Dir.rx(k + 1) != 0 ? Dir.rx(k + 1) : 3 * Dir.rz(k + 1);
        for (int i = 0; i < 4; i++) m.curved(
            new double[][] { cv(k, i, -0.5, axis), cv(k, i + 1, -0.5, axis), cv(k, i + 1, 0.5, axis),
                cv(k, i, 0.5, axis) },
            Quad.Surface.CONCAVE,
            0,
            axis);
    }

    /** A vertex of the concave face: arc angle {@code i} from the jamb's bottom edge, at {@code e} along the wall. */
    private static double[] cv(int k, int i, double e, int axis) {
        double[] p = arc(k, i, e);
        double[] nn = Dir.vec(k, C[i], k + 1, 0, -S[i]);
        return new double[] { p[0], p[1], p[2], nn[0], nn[1], nn[2], i * Dir.STEP, Mesh.tex(p, axis) };
    }

    /** The arc's point {@code i}: centre on side a's bottom edge, radius 1, from the jamb side's bottom edge up. */
    private static double[] arc(int k, int i, double e) {
        return Dir.pt(k, 0.5 - C[i], k + 1, e, -0.5 + S[i]);
    }

    @Override
    List<Piece> section(int face) {
        int k = dir();
        if (k < 0) return wholeFace(face);
        if (face == Dir.UP) return wholeFace(face);
        if (face == Dir.DOWN) return NONE;
        int j = Dir.ringOf(face), d = (j - k) & 3;
        if (d == 0) return NONE;
        if (d == 2) return wholeFace(face);
        // an end: four triangles fanned from the jamb's top corner
        double e = d == 1 ? 0.5 : -0.5;
        double[] corner = Dir.pt(k, -0.5, k + 1, e, 0.5);
        List<Piece> out = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) out.add(new Piece(new double[][] { corner, arc(k, i, e), arc(k, i + 1, e) }));
        return out;
    }
}
