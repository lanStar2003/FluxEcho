package com.fluxecho.logic.shape;

import java.util.ArrayList;
import java.util.List;

/**
 * Louvre (格, 形号 9): open horizontal slats (style FINAL §3.2: 横向百叶，镂空). Two slats 3/16 thick span the cell
 * at heights 4–7/16 and 12–15/16, half a block apart, so a stack of louvres has the same spacing throughout; slats
 * run on into the next louvre without a face between them. The eye passes between the slats: the sight boxes are the
 * slats alone (OcclusionGate, style §8.4.1), while collision is the whole cell.
 */
final class LouvreGeo extends Geo {

    /** The slats' bottoms and tops, centred (-0.5 is the cell's floor). */
    static final double[][] SLATS = { { -4 / 16.0, -1 / 16.0 }, { 4 / 16.0, 7 / 16.0 } };

    LouvreGeo(Form form, Nbhd n) {
        super(form, n);
    }

    @Override
    void interior(Mesh m) {
        for (double[] s : SLATS) for (int t = 0; t < 2; t++) {
            double h = s[t];
            m.flat(
                new double[][] { Dir.pt(0, 0.5, 1, 0.5, h), Dir.pt(1, 0.5, 2, 0.5, h), Dir.pt(2, 0.5, 3, 0.5, h),
                    Dir.pt(3, 0.5, 0, 0.5, h) },
                new double[] { 0, t == 0 ? -1 : 1, 0 },
                -1,
                Overlay.NONE,
                0);
        }
    }

    @Override
    List<Piece> section(int face) {
        int j = Dir.ringOf(face);
        if (j < 0) return NONE;
        List<Piece> out = new ArrayList<>(2);
        for (double[] s : SLATS) out.add(new Piece(sideRect(j, -0.5, 0.5, s[0], s[1])));
        return out;
    }

    @Override
    List<double[]> occluders() {
        List<double[]> out = new ArrayList<>(2);
        for (double[] s : SLATS) out.add(box(0, 0.5 + s[0], 0, 1, 0.5 + s[1], 1));
        return out;
    }
}
