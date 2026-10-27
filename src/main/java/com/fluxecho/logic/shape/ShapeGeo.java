package com.fluxecho.logic.shape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The geometry of the new material families' shapes (style FINAL §3.2–§3.3): pure functions from a shape and its
 * neighbourhood to quads with per-vertex normals and texture coordinates, to collision boxes, to sight-blocking boxes
 * and to the shape's section on each cell face. {@code ShapeRender} hands the quads to the Tessellator and lays the
 * glow and variant layers over them; the blocks take their collision from {@link #boxes}, and so do the walk tests,
 * so what a player bumps into and what the tests walk are one list of boxes.
 * <p>
 * Everything is in local cell coordinates, 0..1 on each axis, x east, y up, z south (see {@link Quad} for the vertex,
 * normal and texture conventions). Nothing here reads global state: the same shape in the same neighbourhood gives
 * the same quads, so a renderer may cache them by ({@link Form}, {@link Nbhd}) and may call this from Angelica's
 * mesh threads.
 * <p>
 * <b>Which faces are drawn.</b> A face on the cell boundary is drawn as what the shape shows there minus what the
 * neighbour across it covers: nothing against an opaque cube (it draws its own face toward our non-opaque blocks);
 * nothing between two whole-block shapes; between two of our solid shapes, exactly the part of each section the other
 * does not cover, when both can work out the other's section from what they see (else both draw their whole section,
 * which only costs hidden faces); between a see-through shape and anything, everything, except between two of the
 * same see-through shape and family, where the glass between them goes. So a seam opens nowhere and two equal faces
 * never both draw.
 * <p>
 * <b>Budget.</b> Among opaque cubes and air a whole-block shape gives at most 6 quads, a round 42 (40 standing alone:
 * four vertical edges and four sphere corners of 4, its foot in four quarter discs of 2), a coping 21, a slope 16, a
 * column 16, a soffit or a louvre 12, every other shape 6. Against our own shapes a face shows only what the
 * neighbour leaves open, which can be cut in pieces: the corner a rounded edge leaves beside it is four triangles, the
 * gaps between louvre slats two strips per quadrant. No cell gives more than {@link #MAX_QUADS}; the most ShapeGeoTest
 * finds among neighbourhoods crowded with rounds, slopes, columns, ribs and louvres is in the seventies.
 */
public final class ShapeGeo {

    /** The most quads one cell gives, over every shape and neighbourhood (ShapeGeoTest measures it). */
    public static final int MAX_QUADS = 128;

    private ShapeGeo() {}

    /** The quads of a shape in a neighbourhood, interior faces first, then the cell faces in face order. */
    public static List<Quad> quads(Form form, Nbhd n) {
        Geo g = Geo.of(form, n);
        Mesh m = new Mesh();
        g.interior(m);
        for (int f = 0; f < 6; f++) face(g, m, f);
        return Collections.unmodifiableList(m.out);
    }

    /**
     * The quads of a shape standing alone, closed on every side, for the item renderer: alone in the air, except a
     * soffit, shown in its opening (air below and on its east side, solid west and above).
     */
    public static List<Quad> item(Form form) {
        Nbhd n = form == Form.SOFFIT ? new Nbhd.Builder().set(0, 1, 0, Nbhd.SOLID)
            .set(-1, 0, 0, Nbhd.SOLID)
            .build() : Nbhd.ISOLATED;
        Geo g = Geo.of(form, n);
        Mesh m = new Mesh();
        g.interior(m);
        for (int f = 0; f < 6; f++)
            for (double[][] p : g.merged(f, g.section(f))) m.flat(p, normal(f), f, Overlay.NONE, 0);
        return Collections.unmodifiableList(m.out);
    }

    /**
     * The collision boxes of a shape, {minX, minY, minZ, maxX, maxY, maxZ} in the cell: the whole cell, but the
     * lower half for half and step, and for a slope cut along its diagonal (S2) the lower half plus the upper half
     * on its closed side (§3.3.5). Every box lies inside the cell, so the walk model's whole and half blocks are never
     * less than the real boxes.
     */
    public static List<double[]> boxes(Form form, Nbhd n) {
        return copy(
            Geo.of(form, n)
                .boxes());
    }

    /**
     * The boxes that block a line of sight through the cell ({@code OcclusionGate}, style §8.4.1): the whole cell for
     * a body; the slab of a half or step, the core and arms of a rib, the column's square, the louvre's slats; none
     * for a window or grate.
     */
    public static List<double[]> occluders(Form form, Nbhd n) {
        if (form.seeThrough()) return Collections.emptyList();
        return copy(
            Geo.of(form, n)
                .occluders());
    }

    /**
     * What a shape's solid shows on cell face {@code face} ({@code ForgeDirection} order) in a neighbourhood: convex
     * polygons of 3D points on that face, empty when the shape does not reach it. This is the face before culling.
     */
    public static List<double[][]> section(Form form, Nbhd n, int face) {
        List<double[][]> out = new ArrayList<>();
        for (Geo.Piece p : Geo.of(form, n)
            .section(face)) out.add(copy(p.p));
        return out;
    }

    // ---- drawing a cell face

    /** The outward normal of a cell face. */
    static double[] normal(int face) {
        return new double[] { Dir.FX[face], Dir.FY[face], Dir.FZ[face] };
    }

    /** Draws what shape {@code g} shows on cell face {@code f} and its neighbour does not cover. */
    private static void face(Geo g, Mesh m, int f) {
        List<Geo.Piece> own = g.section(f);
        if (own.isEmpty()) return;
        List<Geo.Piece> cover = cover(g, f);
        if (cover == FULL) return;
        Overlay ov = g.overlay(f);
        int conn = g.conn(f);
        double[] nrm = normal(f);
        if (cover == null) {
            for (double[][] p : g.merged(f, own)) m.flat(p, nrm, f, ov, conn);
            return;
        }
        List<double[][]> a = new ArrayList<>(own.size()), b = new ArrayList<>(cover.size());
        for (Geo.Piece p : own) a.add(p.p);
        for (Geo.Piece p : cover) b.add(p.p);
        for (double[][] p : Poly.subtract(a, b, f)) m.flat(p, nrm, f, ov, conn);
    }

    /** The whole face: the neighbour covers everything. */
    private static final List<Geo.Piece> FULL = new ArrayList<>(0);

    /**
     * What the neighbour across face {@code f} covers of it: {@link #FULL}, null for nothing, or the neighbour's
     * section on the shared face. Both cells decide the same way: a cell subtracts its neighbour's section only when
     * the neighbour can work out its own the other way round.
     */
    private static List<Geo.Piece> cover(Geo g, int f) {
        Nbhd n = g.n;
        int dx = Dir.FX[f], dy = Dir.FY[f], dz = Dir.FZ[f];
        Form b = n.form(dx, dy, dz);
        if (b == null) return n.solid(dx, dy, dz) ? FULL : null;
        Form a = g.form;
        if (a.seeThrough() || b.seeThrough()) return a == b && n.sameFamily(dx, dy, dz) ? FULL : null;
        if (a.wholeBlock() && b.wholeBlock()) return FULL;
        List<Geo.Piece> theirs = trySection(b, n.neighbour(f, a), Dir.OPP[f]);
        if (theirs == null || trySection(a, n.seenFrom(f), f) == null) return null;
        int whole = 0;
        for (Geo.Piece p : theirs) if (p.quad >= 0) whole++;
        return whole == 4 && theirs.size() == 4 ? FULL : theirs;
    }

    /** A shape's section, or null when the neighbourhood does not tell it. */
    private static List<Geo.Piece> trySection(Form form, Nbhd n, int face) {
        try {
            return Geo.of(form, n)
                .section(face);
        } catch (Nbhd.Unknown e) {
            return null;
        }
    }

    private static List<double[]> copy(List<double[]> boxes) {
        List<double[]> out = new ArrayList<>(boxes.size());
        for (double[] b : boxes) out.add(b.clone());
        return out;
    }

    private static double[][] copy(double[][] p) {
        double[][] c = new double[p.length][];
        for (int i = 0; i < p.length; i++) c[i] = p[i].clone();
        return c;
    }
}
