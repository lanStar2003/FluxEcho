package com.fluxecho.logic.shape;

import java.util.ArrayList;
import java.util.List;

/**
 * Rib (肋, 形号 6, hull only): a plate 4/16 thick, full height, joining the ribs beside it (style FINAL §3.2).
 * <ul>
 * <li>K1: a core post 4/16 square, and toward every side where a rib stands an arm out to the cell's face, so a row of
 * ribs is one plate and a cross of them a cross.</li>
 * <li>K2: where the next rib is one cell up and over (or down and over) and neither the cell over nor the cell up (or
 * down) is a rib, a 45-degree brace of the same section runs from the core toward it. The two ribs' braces meet on
 * the plane through their shared corner edge square to the brace, so the bar runs on unbroken; near that edge the
 * brace reaches up to 0.09 past its own cell into the two corner cells beside the joint.</li>
 * </ul>
 * Ribs join ribs of any family (only the hull has them). Collision stays the whole cell (§3.2: 防穿); the sight boxes
 * are the core and the arms.
 */
final class RibGeo extends Geo {

    /** Half the plate's thickness. */
    static final double HALF = 2 / 16.0;
    /** The brace's corners lie this far off its centre line, along each axis of its plane. */
    private static final double Q = HALF * Math.sqrt(0.5);

    RibGeo(Form form, Nbhd n) {
        super(form, n);
    }

    /** Whether ring side {@code k} has an arm (a rib stands there). */
    boolean arm(int k) {
        return formAt(Dir.ring(k)) == Form.RIB;
    }

    /** Whether a brace runs from the core toward ring side {@code k}, up ({@code dy} 1) or down (-1). */
    boolean brace(int k, int dy) {
        return n.form(Dir.rx(k), dy, Dir.rz(k)) == Form.RIB && !arm(k)
            && formAt(dy > 0 ? Dir.UP : Dir.DOWN) != Form.RIB;
    }

    @Override
    void interior(Mesh m) {
        // the plate's long faces: on the plane HALF out toward ring side k, the core's face unless an arm leaves
        // there, and the sides of the arms toward k - 1 and k + 1
        for (int k = 0; k < 4; k++) {
            double[] nrm = Dir.vec(k, 1, k + 1, 0, 0);
            boolean lo = arm(k - 1), mid = !arm(k), hi = arm(k + 1);
            double[][] spans = { { -0.5, -HALF }, { -HALF, HALF }, { HALF, 0.5 } };
            boolean[] on = { lo, mid, hi };
            for (int a = 0; a < 3; a++) {
                if (!on[a]) continue;
                int b = a;
                while (b + 1 < 3 && on[b + 1]) b++;
                m.flat(
                    new double[][] { Dir.pt(k, HALF, k + 1, spans[a][0], -0.5),
                        Dir.pt(k, HALF, k + 1, spans[b][1], -0.5), Dir.pt(k, HALF, k + 1, spans[b][1], 0.5),
                        Dir.pt(k, HALF, k + 1, spans[a][0], 0.5) },
                    nrm,
                    -1,
                    Overlay.NONE,
                    0);
                a = b;
            }
        }
        for (int k = 0; k < 4; k++) for (int dy = -1; dy <= 1; dy += 2) if (brace(k, dy)) brace(m, k, dy);
    }

    /**
     * The brace toward ring side {@code k} and {@code dy}: from the core's middle to the plane through the shared
     * corner edge square to the bar; its long faces and its two sides (the ends are inside the core and on the joint).
     */
    private static void brace(Mesh m, int k, int dy) {
        // corners in (u along ring side k, h): two at the start (u + h' = 0), two at the joint (u + h' = 1)
        double[][] uh = { { -Q, Q }, { Q, -Q }, { 0.5 + Q, 0.5 - Q }, { 0.5 - Q, 0.5 + Q } };
        double[][] lo = new double[4][], hi = new double[4][];
        for (int i = 0; i < 4; i++) {
            lo[i] = Dir.pt(k, uh[i][0], k + 1, -HALF, dy * uh[i][1]);
            hi[i] = Dir.pt(k, uh[i][0], k + 1, HALF, dy * uh[i][1]);
        }
        double s = Math.sqrt(0.5);
        // the face on the up side of the bar (corners 0, 3) and the face on its down side (1, 2)
        m.flat(new double[][] { lo[0], lo[3], hi[3], hi[0] }, Dir.vec(k, -s, k + 1, 0, dy * s), -1, Overlay.NONE, 0);
        m.flat(new double[][] { lo[1], lo[2], hi[2], hi[1] }, Dir.vec(k, s, k + 1, 0, -dy * s), -1, Overlay.NONE, 0);
        m.flat(lo, Dir.vec(k, 0, k + 1, -1, 0), -1, Overlay.NONE, 0);
        m.flat(hi, Dir.vec(k, 0, k + 1, 1, 0), -1, Overlay.NONE, 0);
    }

    @Override
    List<Piece> section(int face) {
        if (face == Dir.UP || face == Dir.DOWN) {
            double h = face == Dir.UP ? 0.5 : -0.5;
            List<Piece> out = new ArrayList<>(8);
            // the core in quarters, each in its corner's frame, so the pieces turn with the cell
            for (int k = 0; k < 4; k++) out.add(
                new Piece(
                    new double[][] { Dir.pt(k, 0, k + 1, 0, h), Dir.pt(k, HALF, k + 1, 0, h),
                        Dir.pt(k, HALF, k + 1, HALF, h), Dir.pt(k, 0, k + 1, HALF, h) }));
            for (int k = 0; k < 4; k++) if (arm(k)) out.add(
                new Piece(
                    new double[][] { Dir.pt(k, HALF, k + 1, -HALF, h), Dir.pt(k, 0.5, k + 1, -HALF, h),
                        Dir.pt(k, 0.5, k + 1, HALF, h), Dir.pt(k, HALF, k + 1, HALF, h) }));
            return out;
        }
        int j = Dir.ringOf(face);
        if (!arm(j)) return NONE;
        List<Piece> out = new ArrayList<>(1);
        out.add(new Piece(sideRect(j, -HALF, HALF, -0.5, 0.5)));
        return out;
    }

    /**
     * An end drawn whole: the core joined with a lone arm, or with the one pair of opposite arms (the plate), so a
     * straight run of ribs ends in one rectangle; an L, a cross or a bare post stays in pieces (no join of those is
     * the same turned).
     */
    @Override
    List<double[][]> merged(int face, List<Piece> pieces) {
        if (face != Dir.UP && face != Dir.DOWN) return super.merged(face, pieces);
        double[] nrm = { 0, face == Dir.UP ? 1 : -1, 0 };
        int count = 0, pair = -1;
        for (int k = 0; k < 4; k++) if (arm(k)) count++;
        for (int k = 0; k < 2; k++) if (arm(k) && arm(k + 2)) pair = pair < 0 ? k : -2;
        List<double[][]> out = new ArrayList<>(pieces.size());
        double h = face == Dir.UP ? 0.5 : -0.5;
        double[][] core = { Dir.pt(0, HALF, 1, HALF, h), Dir.pt(1, HALF, 2, HALF, h), Dir.pt(2, HALF, 3, HALF, h),
            Dir.pt(3, HALF, 0, HALF, h) };
        int p = 4;
        double[][][] arms = new double[4][][];
        for (int k = 0; k < 4; k++) if (arm(k)) arms[k] = pieces.get(p++).p;
        if (count == 1 || pair >= 0) {
            for (int k = 0; k < 4; k++) {
                if (arms[k] == null || count != 1 && (k & 1) != pair) continue;
                double[][] m = Poly.merge(core, arms[k], nrm);
                if (m != null) {
                    core = m;
                    arms[k] = null;
                }
            }
        }
        out.add(core);
        for (double[][] a : arms) if (a != null) out.add(a);
        return out;
    }

    @Override
    List<double[]> occluders() {
        List<double[]> out = new ArrayList<>(5);
        double a = 0.5 - HALF, b = 0.5 + HALF;
        out.add(box(a, 0, a, b, 1, b));
        if (arm(0)) out.add(box(b, 0, a, 1, 1, b));
        if (arm(2)) out.add(box(0, 0, a, a, 1, b));
        if (arm(1)) out.add(box(a, 0, b, b, 1, 1));
        if (arm(3)) out.add(box(a, 0, 0, b, 1, a));
        return out;
    }
}
