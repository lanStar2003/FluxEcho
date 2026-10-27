package com.fluxecho.logic.shape;

import static com.fluxecho.logic.shape.Dir.C;
import static com.fluxecho.logic.shape.Dir.S;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Column (柱, 形号 7; the footing's 7): an octagonal prism 10/16 across the flats, full height, its faces square to
 * the sides and the diagonals. A column on a column draws no end between them (the two octagons cover each other);
 * an end is four quads fanned from its centre, one per quadrant, so the fan turns with the cell. The fixtures' column
 * heads (§3.8.4) share this section.
 */
final class ColumnGeo extends Geo {

    /** Half the width across the flats, and the radius to a corner. */
    static final double FLAT = 5 / 16.0, CORNER = FLAT / C[1];

    ColumnGeo(Form form, Nbhd n) {
        super(form, n);
    }

    /** The octagon's corner at 22.5 ({@code i = 1}) or 67.5 ({@code i = 3}) degrees from ring side k, at height h. */
    static double[] corner(int k, int i, double h) {
        return Dir.pt(k, CORNER * C[i], k + 1, CORNER * S[i], h);
    }

    @Override
    void interior(Mesh m) {
        for (int k = 0; k < 4; k++) {
            m.flat(
                new double[][] { corner(k, 1, -0.5), corner(k, 3, -0.5), corner(k, 3, 0.5), corner(k, 1, 0.5) },
                Dir.unit(Dir.vec(k, 1, k + 1, 1, 0)),
                -1,
                Overlay.NONE,
                0);
            m.flat(
                new double[][] { corner(k, 3, -0.5), corner(k + 1, 1, -0.5), corner(k + 1, 1, 0.5), corner(k, 3, 0.5) },
                Dir.vec(k, 0, k + 1, 1, 0),
                -1,
                Overlay.NONE,
                0);
        }
    }

    @Override
    List<Piece> section(int face) {
        if (face != Dir.UP && face != Dir.DOWN) return NONE;
        double h = face == Dir.UP ? 0.5 : -0.5;
        List<Piece> out = new ArrayList<>(4);
        for (int k = 0; k < 4; k++) out.add(
            new Piece(
                new double[][] { Dir.pt(k, 0, k + 1, 0, h), corner(k, 1, h), corner(k, 3, h), corner(k + 1, 1, h) }));
        return out;
    }

    @Override
    List<double[]> occluders() {
        double a = 0.5 - FLAT, b = 0.5 + FLAT;
        return Collections.singletonList(box(a, 0, a, b, 1, b));
    }
}
