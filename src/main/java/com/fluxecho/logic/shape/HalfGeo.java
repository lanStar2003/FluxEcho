package com.fluxecho.logic.shape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Half (形号 4) and step (5): the lower half of the cell, a slab one stands on at half height. The step carries a warm
 * nose line on each side it shows toward the open (style FINAL §3.2: 鼻线朝空气那边).
 */
final class HalfGeo extends Geo {

    HalfGeo(Form form, Nbhd n) {
        super(form, n);
    }

    @Override
    List<Piece> section(int face) {
        if (face == Dir.UP) return NONE;
        if (face == Dir.DOWN) return wholeFace(face);
        int j = Dir.ringOf(face);
        List<Piece> out = new ArrayList<>(2);
        out.add(sideQuad(j, 0));
        out.add(sideQuad(j, 2));
        return out;
    }

    @Override
    void interior(Mesh m) {
        m.flat(
            new double[][] { Dir.pt(0, 0.5, 1, 0.5, 0), Dir.pt(1, 0.5, 2, 0.5, 0), Dir.pt(2, 0.5, 3, 0.5, 0),
                Dir.pt(3, 0.5, 0, 0.5, 0) },
            new double[] { 0, 1, 0 },
            -1,
            Overlay.NONE,
            0);
    }

    @Override
    Overlay overlay(int face) {
        return form == Form.STEP && Dir.ringOf(face) >= 0 && airAt(face) ? Overlay.NOSE : Overlay.NONE;
    }

    @Override
    List<double[]> boxes() {
        return Collections.singletonList(box(0, 0, 0, 1, 0.5, 1));
    }
}
