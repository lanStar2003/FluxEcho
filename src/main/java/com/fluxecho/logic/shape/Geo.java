package com.fluxecho.logic.shape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One shape in one neighbourhood: what it shows on each of the cell's six faces ({@link #section}), the faces it has
 * inside the cell ({@link #interior}), its collision and sight-blocking boxes, and the overlays of its faces. Each
 * kind of shape is a subclass; {@link ShapeGeo} decides from two neighbours' sections what each draws on the face
 * they share.
 * <p>
 * Sections are lists of convex {@link Piece}s. The pieces of a face are built per quadrant in the frame of a ring
 * corner or side, so a turned neighbourhood gives the same pieces turned; a quadrant filled whole is marked so the
 * drawing can merge it with its neighbours into one rectangle. Flags a shape works out from its neighbours are
 * computed when first needed, and a section only reads the cells it needs, so a neighbour can work out the section
 * this cell shows on their shared face from what it knows ({@link Nbhd#neighbour}).
 */
abstract class Geo {

    /** No cell to the side: an empty section. */
    static final List<Piece> NONE = Collections.emptyList();

    final Form form;
    final Nbhd n;

    Geo(Form form, Nbhd n) {
        this.form = form;
        this.n = n;
    }

    /** The geometry of a shape in a neighbourhood. */
    static Geo of(Form form, Nbhd n) {
        switch (form) {
            case ROUND:
            case COPING:
                return new RoundGeo(form, n);
            case SLOPE:
                return new SlopeGeo(form, n);
            case SOFFIT:
                return new SoffitGeo(form, n);
            case HALF:
            case STEP:
                return new HalfGeo(form, n);
            case RIB:
                return new RibGeo(form, n);
            case COLUMN:
                return new ColumnGeo(form, n);
            case LOUVRE:
                return new LouvreGeo(form, n);
            default:
                return new CubeGeo(form, n);
        }
    }

    /** What the shape's solid shows on cell face {@code face}: convex pieces, empty when it does not reach it. */
    abstract List<Piece> section(int face);

    /** The faces inside the cell: curves, slopes, chamfers, and the faces of thin pieces off the cell's faces. */
    abstract void interior(Mesh m);

    /** The overlay of the shape's faces on cell face {@code face}. */
    Overlay overlay(int face) {
        return Overlay.NONE;
    }

    /** The continuation bits of the shape's faces on cell face {@code face} ({@link Quad#conn}). */
    int conn(int face) {
        return 0;
    }

    /** The collision boxes {minX, minY, minZ, maxX, maxY, maxZ} inside the cell. */
    List<double[]> boxes() {
        return Collections.singletonList(box(0, 0, 0, 1, 1, 1));
    }

    /** The boxes that block a line of sight: the whole cell for a body. */
    List<double[]> occluders() {
        return boxes();
    }

    // ---- reading the neighbours

    /** Whether the cell across face {@code face} is open (空). */
    final boolean open(int face) {
        return !n.closed(Dir.FX[face], Dir.FY[face], Dir.FZ[face]);
    }

    /** Whether the cell across ring side {@code k} is open. */
    final boolean openSide(int k) {
        return open(Dir.ring(k));
    }

    /** Whether the diagonal cell at ring corner {@code k} is open. */
    final boolean openDiag(int k) {
        return !n.closed(Dir.rx(k) + Dir.rx(k + 1), 0, Dir.rz(k) + Dir.rz(k + 1));
    }

    /** Whether the cell over ring side {@code k} is open. */
    final boolean openUpper(int k) {
        return !n.closed(Dir.rx(k), 1, Dir.rz(k));
    }

    /** Our shape across face {@code face}, or null. */
    final Form formAt(int face) {
        return n.form(Dir.FX[face], Dir.FY[face], Dir.FZ[face]);
    }

    /** Whether the cell across face {@code face} is this shape of this family. */
    final boolean sameAt(int face) {
        return n.same(Dir.FX[face], Dir.FY[face], Dir.FZ[face], form);
    }

    /** Whether the cell across face {@code face} is neither ours nor an opaque cube: air, plants, water, glass. */
    final boolean airAt(int face) {
        int dx = Dir.FX[face], dy = Dir.FY[face], dz = Dir.FZ[face];
        return n.form(dx, dy, dz) == null && !n.solid(dx, dy, dz);
    }

    // ---- pieces

    /** A convex piece of a section; {@code quad} is its quadrant when it fills that quadrant whole, else -1. */
    static final class Piece {

        final double[][] p;
        final int quad;

        Piece(double[][] p, int quad) {
            this.p = p;
            this.quad = quad;
        }

        /** A piece that is not a whole quadrant. */
        Piece(double[][] p) {
            this(p, -1);
        }
    }

    /**
     * Quadrant {@code q} of the side face on ring side {@code j}, whole: 0 and 1 lie toward ring side {@code j + 1}
     * (t > 0), 2 and 3 toward {@code j - 1}; 0 and 2 are the lower halves.
     */
    static Piece sideQuad(int j, int q) {
        double t0 = q < 2 ? 0 : -0.5, h0 = (q & 1) == 0 ? -0.5 : 0;
        return new Piece(sideRect(j, t0, t0 + 0.5, h0, h0 + 0.5), q);
    }

    /** A rectangle on the side face of ring side {@code j}: t along ring side {@code j + 1}, h up, both centred. */
    static double[][] sideRect(int j, double t0, double t1, double h0, double h1) {
        return new double[][] { Dir.pt(j, 0.5, j + 1, t0, h0), Dir.pt(j, 0.5, j + 1, t1, h0),
            Dir.pt(j, 0.5, j + 1, t1, h1), Dir.pt(j, 0.5, j + 1, t0, h1) };
    }

    /** The quarter of the top ({@code h = 0.5}) or bottom ({@code h = -0.5}) face at ring corner {@code k}, whole. */
    static Piece capQuad(int k, double h) {
        return new Piece(
            new double[][] { Dir.pt(k, 0, k + 1, 0, h), Dir.pt(k, 0.5, k + 1, 0, h), Dir.pt(k, 0.5, k + 1, 0.5, h),
                Dir.pt(k, 0, k + 1, 0.5, h) },
            k & 3);
    }

    /** A whole face as four whole quadrants. */
    static List<Piece> wholeFace(int face) {
        List<Piece> out = new ArrayList<>(4);
        int j = Dir.ringOf(face);
        for (int q = 0; q < 4; q++) out.add(j >= 0 ? sideQuad(j, q) : capQuad(q, face == Dir.UP ? 0.5 : -0.5));
        return out;
    }

    /**
     * A quarter disc of radius 0.5 centred on a face's centre, as the four-segment arc the curved faces end in:
     * {@code centre} first, then the arc's five points.
     */
    static Piece disc(double[] centre, double[][] arc) {
        double[][] p = new double[6][];
        p[0] = centre;
        System.arraycopy(arc, 0, p, 1, 5);
        return new Piece(p);
    }

    /**
     * The polygons to draw for a section nothing covers: whole quadrants merged into as few rectangles as the rule
     * below gives (whole rows first on a side face, then whole columns; on the top or bottom a pair of quadrants
     * beside the missing one when three are whole), every other piece as it is. The rule turns with the cell.
     */
    List<double[][]> merged(int face, List<Piece> pieces) {
        boolean[] full = new boolean[4];
        List<double[][]> out = new ArrayList<>(pieces.size()), rest = new ArrayList<>(pieces.size());
        for (Piece p : pieces) if (p.quad >= 0) full[p.quad] = true;
        else rest.add(p.p);
        int j = Dir.ringOf(face);
        if (j >= 0) mergeSide(j, full, out);
        else mergeCap(face == Dir.UP ? 0.5 : -0.5, full, out);
        out.addAll(rest);
        return out;
    }

    private static void mergeSide(int j, boolean[] full, List<double[][]> out) {
        if (full[0] && full[1] && full[2] && full[3]) {
            out.add(sideRect(j, -0.5, 0.5, -0.5, 0.5));
            return;
        }
        for (int row = 0; row < 2; row++) if (full[row] && full[row + 2]) {
            out.add(sideRect(j, -0.5, 0.5, row == 0 ? -0.5 : 0, row == 0 ? 0 : 0.5));
            full[row] = full[row + 2] = false;
        }
        for (int col = 0; col < 4; col += 2) if (full[col] && full[col + 1]) {
            out.add(sideRect(j, col == 0 ? 0 : -0.5, col == 0 ? 0.5 : 0, -0.5, 0.5));
            full[col] = full[col + 1] = false;
        }
        for (int q = 0; q < 4; q++) if (full[q]) out.add(sideQuad(j, q).p);
    }

    private static void mergeCap(double h, boolean[] full, List<double[][]> out) {
        int count = 0, missing = -1;
        for (int k = 0; k < 4; k++) if (full[k]) count++;
        else missing = k;
        if (count == 4) {
            out.add(
                new double[][] { Dir.pt(0, 0.5, 1, 0.5, h), Dir.pt(1, 0.5, 2, 0.5, h), Dir.pt(2, 0.5, 3, 0.5, h),
                    Dir.pt(3, 0.5, 0, 0.5, h) });
            return;
        }
        int pair = -1;
        if (count == 3) pair = missing + 1;
        else if (count == 2) for (int k = 0; k < 4; k++) if (full[k] && full[(k + 1) & 3]) pair = k;
        if (pair >= 0) {
            int k = pair;
            out.add(
                new double[][] { Dir.pt(k, 0.5, k + 1, 0, h), Dir.pt(k, 0.5, k + 1, 0.5, h),
                    Dir.pt(k + 1, 0.5, k + 2, 0.5, h), Dir.pt(k + 1, 0, k + 2, 0.5, h) });
            full[k & 3] = full[(k + 1) & 3] = false;
        }
        for (int k = 0; k < 4; k++) if (full[k]) out.add(capQuad(k, h).p);
    }

    /** A box. */
    static double[] box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return new double[] { x0, y0, z0, x1, y1, z1 };
    }
}
