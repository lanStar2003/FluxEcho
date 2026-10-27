package com.fluxecho.logic.shape;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The shapes' geometry against style FINAL §3.3.7, item by item, with the face counts of §3.3.4 copied from its
 * table: 1 face counts, 2 watertight, 3 no broken faces, 4 normals, 5 a quarter turn of the neighbourhood turns the
 * output, 6 collision boxes, 7 a pure function. Around them: the faces between two neighbours of ours, the overlays,
 * the budget, the items, the families and the 0.17.0 part codes against the ID ledger.
 * <p>
 * Correction (勘误) of the table: case C's slope is 7 quads, not 5. The table lists "切角 1 + 四坡角 4" and leaves out
 * the two lower halves of the open side faces that it counts in cases A and B ("±z 下半 2", "±z 面 2") and in C's
 * round ("两面各 1"); with them it is 1 + 4 + 2. Every other number is the table's.
 */
class ShapeGeoTest {

    // ---- §3.3.4: the six neighbour cases (closed cells as opaque cubes, everything else open)

    /** A: a one-wide wall's middle. ±z open, ±x closed, up open, down closed. */
    static final int A = Nbhd.WEST | Nbhd.EAST | Nbhd.DOWN;
    /** B: the wall's end: +x open too. */
    static final int B = Nbhd.WEST | Nbhd.DOWN;
    /** C: an L's outer corner: -x, -z open; +x, +z closed; up open. */
    static final int C = Nbhd.EAST | Nbhd.SOUTH | Nbhd.DOWN;
    /** D: a cell of a thick wall's stepped 45-degree edge: -x, +z open; +x, -z and the diagonals +x+z, -x-z closed. */
    static final int D = Nbhd.EAST | Nbhd.NORTH | Nbhd.ES | Nbhd.WN | Nbhd.UP | Nbhd.DOWN;
    /** E: beside a concave corner: ±x closed, ±z open, up closed (the corner's cell at +x+z closed). */
    static final int E = Nbhd.WEST | Nbhd.EAST | Nbhd.ES | Nbhd.UP | Nbhd.DOWN;
    /** F: a one-thick diagonal line: four sides open, the diagonals +x+z and -x-z closed, up closed. */
    static final int F = Nbhd.ES | Nbhd.WN | Nbhd.UP | Nbhd.DOWN;

    /** Case, mask, round's quads, slope's quads (§3.3.4; C's slope corrected, see the class comment). */
    static final Object[][] TABLE = { { "A", A, 10, 4 }, { "B", B, 26, 7 }, { "C", C, 19, 7 }, { "D", D, 6, 1 },
        { "E", E, 2, 2 }, { "F", F, 12, 6 } };

    /** 1. The face counts of §3.3.4 in all four facings, round and slope. */
    @Test
    void faceCountsMatchTheTable() {
        for (Object[] row : TABLE) {
            Nbhd n = Nbhd.ofMask((Integer) row[1]);
            for (int turn = 0; turn < 4; turn++) {
                assertEquals(
                    row[2],
                    ShapeGeo.quads(Form.ROUND, n)
                        .size(),
                    "round, case " + row[0] + ", turn " + turn);
                assertEquals(
                    row[3],
                    ShapeGeo.quads(Form.SLOPE, n)
                        .size(),
                    "slope, case " + row[0] + ", turn " + turn);
                n = n.rotated();
            }
        }
    }

    /**
     * What the round's quads are, by the table's words: A two top edges of 4 and the lower halves; B two half
     * vertical edges, two corners, two half top edges of 4 each and two faces; C one of each and two faces and the top
     * quarter; D a full vertical edge and two faces; E two faces; F two vertical edges and four faces.
     */
    @Test
    void theRoundsQuadsAreTheTablesParts() {
        assertEquals("PLANE 2, CYLINDER 8", kinds(Form.ROUND, A));
        assertEquals("PLANE 2, CYLINDER 16, SPHERE 8", kinds(Form.ROUND, B));
        assertEquals("PLANE 3, CYLINDER 12, SPHERE 4", kinds(Form.ROUND, C));
        assertEquals("PLANE 2, CYLINDER 4", kinds(Form.ROUND, D));
        assertEquals("PLANE 2", kinds(Form.ROUND, E));
        assertEquals("PLANE 4, CYLINDER 8", kinds(Form.ROUND, F));
        // a sphere corner: four quads, the two at the pole triangles
        int tris = 0;
        for (Quad q : ShapeGeo.quads(Form.ROUND, Nbhd.ofMask(C)))
            if (q.surface == Quad.Surface.SPHERE && q.triangle()) tris++;
        assertEquals(2, tris);
        // the slope's: D is one 45-degree face through the cell's diagonal, full height
        List<Quad> d = ShapeGeo.quads(Form.SLOPE, Nbhd.ofMask(D));
        assertEquals(1, d.size());
        assertEquals(Math.sqrt(2), area(d.get(0)), 1e-9);
    }

    private static String kinds(Form f, int mask) {
        Map<Quad.Surface, Integer> m = new EnumMap<>(Quad.Surface.class);
        for (Quad q : ShapeGeo.quads(f, Nbhd.ofMask(mask))) m.merge(q.surface, 1, Integer::sum);
        StringBuilder b = new StringBuilder();
        for (Map.Entry<Quad.Surface, Integer> e : m.entrySet()) b.append(b.length() > 0 ? ", " : "")
            .append(e.getKey())
            .append(' ')
            .append(e.getValue());
        return b.toString();
    }

    // ---- 2. watertight

    /** The shapes the neighbour model's rules shape (§3.3.2–§3.3.3) and the soffit, whose direction it takes. */
    static final Form[] RULED = { Form.ROUND, Form.SLOPE, Form.COPING, Form.SOFFIT };

    /**
     * 2. Watertight, one cell: the faces inside the cell and the sections on its six faces close up into a solid,
     * every edge walked once each way, with a positive volume: the six cases, and 400 neighbourhoods drawn at random,
     * for the ruled shapes; for every shape, a dozen.
     */
    @Test
    void everyCellIsClosed() {
        Random r = new Random(17);
        for (Form f : RULED) {
            for (Object[] row : TABLE) assertClosed(f, Nbhd.ofMask((Integer) row[1]));
            for (int i = 0; i < 400; i++) assertClosed(f, Nbhd.ofMask(r.nextInt(1 << 14)));
        }
        for (Form f : Form.values()) {
            assertClosed(f, Nbhd.ISOLATED);
            for (int i = 0; i < 12; i++) assertClosed(f, Nbhd.ofMask(r.nextInt(1 << 14)));
        }
    }

    /** 2. Watertight, one cell, every one of the 16,384 neighbourhoods of the fourteen-cell model. */
    @Test
    @Tag("slow")
    void everyCellIsClosedInEveryNeighbourhood() {
        for (Form f : RULED) for (int mask = 0; mask < 1 << 14; mask++) assertClosed(f, Nbhd.ofMask(mask));
    }

    static void assertClosed(Form f, Nbhd n) {
        List<double[][]> polys = new ArrayList<>();
        for (Quad q : ShapeGeo.quads(f, n)) if (q.face < 0) polys.add(
            MeshCheck.polys(Collections.singletonList(q))
                .get(0));
        for (int face = 0; face < 6; face++) for (double[][] p : ShapeGeo.section(f, n, face))
            polys.add(Poly.facing(Poly.dedup(p), ShapeGeo.normal(face)));
        String err = MeshCheck.closure(polys);
        assertNull(err, f + " " + n.hashCode() + ": " + err);
        double v = MeshCheck.volume(polys);
        assertTrue(v > 0.05 && v <= 1 + 1e-9, f + ": volume " + v);
    }

    /**
     * 2. Watertight across cells (D, E, F above all): the cells of a little scene drawn as the renderer draws them,
     * the faces between them culled as they cull them, close up into one surface. Opaque cubes draw their faces toward
     * our shapes as vanilla does, here only the part our shape leaves open.
     */
    @Test
    void scenesAreClosed() {
        for (Form f : new Form[] { Form.ROUND, Form.SLOPE, Form.COPING })
            for (int h = 1; h <= 2; h++) for (boolean floor : new boolean[] { false, true }) {
                // D: a thick wall's stepped 45-degree edge
                assertSceneClosed(scene(5, h, 5, floor, (x, z) -> x >= z, f), "D " + f);
                // E: an L of one-wide walls, its inner corner
                assertSceneClosed(scene(5, h, 5, floor, (x, z) -> x == 1 || z == 1, f), "E " + f);
                // F: a one-thick diagonal line
                assertSceneClosed(scene(5, h, 5, floor, (x, z) -> x == z, f), "F " + f);
                // a one-wide wall with its ends, and a lone post
                assertSceneClosed(scene(5, h, 3, floor, (x, z) -> z == 1 && x > 0 && x < 4, f), "wall " + f);
                assertSceneClosed(scene(3, h, 3, floor, (x, z) -> x == 1 && z == 1, f), "post " + f);
            }
        // round crowns on slope bodies, and the other way round
        Object[][][] g = scene(5, 2, 5, true, (x, z) -> x >= z, Form.SLOPE);
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) if (g[x][2][z] != null) g[x][2][z] = Form.ROUND;
        assertSceneClosed(g, "round over slope");
    }

    /** 2. Watertight across cells: 300 scenes drawn at random from every shape but the rib, opaque cubes and air. */
    @Test
    void randomScenesAreClosed() {
        Random r = new Random(5);
        Object[] pool = { null, null, null, STONE, Form.ROUND, Form.SLOPE, Form.COPING, Form.SOFFIT, Form.WHOLE,
            Form.HALF, Form.STEP, Form.COLUMN, Form.LOUVRE, Form.WINDOW, Form.LAMP, Form.ROUND, Form.SLOPE };
        for (int i = 0; i < 300; i++) {
            Object[][][] g = new Object[4][3][4];
            for (Object[][] a : g)
                for (Object[] b : a) for (int z = 0; z < b.length; z++) b[z] = pool[r.nextInt(pool.length)];
            assertSceneClosed(g, "random scene " + i);
        }
    }

    /** An opaque cube in a scene. */
    static final Object STONE = "stone";

    interface Plan {

        boolean in(int x, int z);
    }

    /** A scene of {@code h} layers of shape {@code f} on the plan, over a floor of opaque cubes or in the air. */
    static Object[][][] scene(int w, int h, int d, boolean floor, Plan plan, Form f) {
        Object[][][] g = new Object[w][h + 1][d];
        for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) {
            if (floor) g[x][0][z] = STONE;
            if (plan.in(x, z)) for (int y = 1; y <= h; y++) g[x][y][z] = f;
        }
        return g;
    }

    static Object at(Object[][][] g, int x, int y, int z) {
        return x < 0 || y < 0 || z < 0 || x >= g.length || y >= g[0].length || z >= g[0][0].length ? null : g[x][y][z];
    }

    /** The neighbourhood of a scene's cell: our shapes all of one family. */
    static Nbhd nbhd(Object[][][] g, int x, int y, int z) {
        Nbhd.Builder b = new Nbhd.Builder();
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dy == 0 && dz == 0) continue;
            Object c = at(g, x + dx, y + dy, z + dz);
            b.set(dx, dy, dz, c == null ? Nbhd.OPEN : c == STONE ? Nbhd.SOLID : Nbhd.ours((Form) c, true));
        }
        return b.build();
    }

    /** The scene's faces as drawn, moved to their cells. */
    static List<double[][]> scenePolys(Object[][][] g) {
        List<double[][]> out = new ArrayList<>();
        for (int x = 0; x < g.length; x++)
            for (int y = 0; y < g[0].length; y++) for (int z = 0; z < g[0][0].length; z++) {
                Object c = g[x][y][z];
                if (c instanceof Form f)
                    out.addAll(MeshCheck.moved(MeshCheck.polys(ShapeGeo.quads(f, nbhd(g, x, y, z))), x, y, z));
                else if (c == STONE) for (int face = 0; face < 6; face++) {
                    int nx = x + Dir.FX[face], ny = y + Dir.FY[face], nz = z + Dir.FZ[face];
                    Object o = at(g, nx, ny, nz);
                    if (o == STONE || o instanceof Form of && of.wholeBlock()) continue;
                    List<double[][]> full = new ArrayList<>();
                    for (Geo.Piece p : Geo.wholeFace(face)) full.add(p.p);
                    List<double[][]> show = o == null ? full
                        : Poly.subtract(full, ShapeGeo.section((Form) o, nbhd(g, nx, ny, nz), Dir.OPP[face]), face);
                    for (double[][] p : show) out.addAll(
                        MeshCheck.moved(Collections.singletonList(Poly.facing(p, ShapeGeo.normal(face))), x, y, z));
                }
            }
        return out;
    }

    static void assertSceneClosed(Object[][][] g, String what) {
        List<double[][]> polys = scenePolys(g);
        String err = MeshCheck.closure(polys);
        assertNull(err, what + ": " + err);
        boolean any = false;
        for (Object[][] a : g) for (Object[] b : a) for (Object c : b) any |= c != null;
        if (any) assertTrue(MeshCheck.volume(polys) > 0, what + ": volume " + MeshCheck.volume(polys));
    }

    // ---- 3. no broken faces

    /**
     * 3. No broken faces: every quad has an area (a triangle's three corners too); a flat quad is convex and wound
     * one way; a folded one (a cylinder narrowing to the sphere's two segments, the slope's bow) folds along its first
     * diagonal without turning over; every vertex is inside the cell (a rib's brace up to 0.09 past it).
     */
    @Test
    void noBrokenFaces() {
        Random r = new Random(3);
        for (Form f : Form.values()) for (int i = 0; i < 300; i++) {
            Nbhd n = i < 6 ? Nbhd.ofMask((Integer) TABLE[i][1]) : i < 150 ? Nbhd.ofMask(r.nextInt(1 << 14)) : random(r);
            for (Quad q : ShapeGeo.quads(f, n)) assertWhole(f, q);
        }
    }

    static void assertWhole(Form f, Quad q) {
        double lo = f == Form.RIB ? -0.1 : -1e-9, hi = f == Form.RIB ? 1.1 : 1 + 1e-9;
        for (double v : q.pos) assertTrue(v >= lo && v <= hi, () -> f + ": vertex outside the cell " + q);
        for (double v : q.normal) assertFalse(Double.isNaN(v), () -> f + ": " + q);
        double[] a = p(q, 0), b = p(q, 1), c = p(q, 2), d = p(q, 3);
        double[] t1 = cross(sub(b, a), sub(c, a)), t2 = cross(sub(c, a), sub(d, a));
        assertTrue(len(t1) > 1e-9, () -> f + ": a quad with no area " + q);
        if (q.triangle()) return;
        assertTrue(len(t2) > 1e-9, () -> f + ": a quad with no area " + q);
        assertTrue(dot(t1, t2) > 0, () -> f + ": a quad turned over " + q);
        if (Math.abs(dot(unit(t1), sub(d, a))) < 1e-9) // flat: every corner turns the same way
            for (int i = 0; i < 4; i++) {
                double[] t = cross(sub(p(q, (i + 1) % 4), p(q, i)), sub(p(q, (i + 2) % 4), p(q, (i + 1) % 4)));
                assertTrue(dot(t, t1) > 1e-12 || len(t) < 1e-9, () -> f + ": a flat quad that is not convex " + q);
            }
    }

    // ---- 4. normals

    /**
     * 4. Normals: unit length; on the side the vertices wind; facing out of the cell's middle (the face's centre minus
     * the cell's centre, or for a slope cut along its diagonal, the middle of what is left; a soffit's middle is in its
     * jamb's top corner); on curves equal to the analytic normal within 1e-6: radial on the cylinders of radius 0.5
     * (vertical edges and top edges) and on the sphere corner, toward the axis on the soffit's concave face.
     */
    @Test
    void normals() {
        Random r = new Random(11);
        for (Form f : Form.values()) for (int i = 0; i < 300; i++) {
            Nbhd n = i < 6 ? Nbhd.ofMask((Integer) TABLE[i][1]) : Nbhd.ofMask(r.nextInt(1 << 14));
            double[] ref = middle(f, n);
            for (Quad q : ShapeGeo.quads(f, n)) {
                double[] w = Poly.newell(new double[][] { p(q, 0), p(q, 1), p(q, 2), p(q, 3) });
                double[] c = centre(q);
                for (int v = 0; v < 4; v++) {
                    double[] nv = nrm(q, v);
                    assertEquals(1, len(nv), 1e-9, () -> f + ": " + q);
                    assertTrue(dot(nv, w) > 0, () -> f + ": normal against the winding " + q);
                    if (ref != null) assertTrue(dot(nv, sub(c, ref)) > 1e-9, () -> f + ": normal into the cell " + q);
                    assertAnalytic(f, q, v);
                }
            }
        }
    }

    /** The point every face of a shape faces away from, or null for the thin shapes made of separate pieces. */
    static double[] middle(Form f, Nbhd n) {
        switch (f) {
            case HALF:
            case STEP:
                return new double[] { 0.5, 0.25, 0.5 };
            case RIB:
            case COLUMN:
                return new double[] { 0.5, 0.5, 0.5 };
            case LOUVRE:
                return null;
            case SLOPE:
                SlopeGeo s = new SlopeGeo(f, n);
                int k = s.s2();
                if (k < 0) return new double[] { 0.5, 0.5, 0.5 };
                // the middle of the triangle the cut leaves
                double[] a = Dir.pt(k - 1, 0.5, k, 0.5, 0), b = Dir.pt(k + 1, 0.5, k + 2, 0.5, 0),
                    c = Dir.pt(k + 2, 0.5, k + 3, 0.5, 0);
                return new double[] { (a[0] + b[0] + c[0]) / 3, 0.4, (a[2] + b[2] + c[2]) / 3 };
            case SOFFIT:
                int d = new SoffitGeo(f, n).dir();
                // just inside the jamb's top corner, which sees every face of the soffit
                return d < 0 ? new double[] { 0.5, 0.5, 0.5 } : Dir.pt(d, -0.48, d + 1, 0, 0.48);
            default:
                return new double[] { 0.5, 0.5, 0.5 };
        }
    }

    static void assertAnalytic(Form f, Quad q, int v) {
        double[] p = p(q, v), nv = nrm(q, v);
        switch (q.surface) {
            case CYLINDER: {
                // radial from one of the three axes through the cell's middle, at radius 0.5
                double[][] radials = { { p[0] - 0.5, 0, p[2] - 0.5 }, { 0, p[1] - 0.5, p[2] - 0.5 },
                    { p[0] - 0.5, p[1] - 0.5, 0 } };
                for (double[] rr : radials) if (Math.abs(len(rr) - 0.5) < 1e-9 && near(nv, scale(rr, 2), 1e-6)) return;
                fail(f + ": a cylinder normal off the radial " + q);
                break;
            }
            case SPHERE:
                double[] rr = sub(p, new double[] { 0.5, 0.5, 0.5 });
                assertEquals(0.5, len(rr), 1e-9, () -> f + ": " + q);
                assertTrue(near(nv, scale(rr, 2), 1e-6), () -> f + ": a sphere normal off the radial " + q);
                break;
            case CONCAVE: {
                // toward an axis on one of the cell's bottom edges, at radius 1
                double[][] axes = { { 0, 0, Double.NaN }, { 1, 0, Double.NaN }, { Double.NaN, 0, 0 },
                    { Double.NaN, 0, 1 } };
                for (double[] ax : axes) {
                    double[] toAxis = { Double.isNaN(ax[0]) ? 0 : ax[0] - p[0], ax[1] - p[1],
                        Double.isNaN(ax[2]) ? 0 : ax[2] - p[2] };
                    if (Math.abs(len(toAxis) - 1) < 1e-9 && near(nv, toAxis, 1e-6)) return;
                }
                fail(f + ": a concave normal off the axis " + q);
                break;
            }
            default:
        }
    }

    // ---- 5. turning

    /**
     * 5. A quarter turn of the neighbourhood turns the output a quarter: the same quads (any starting vertex), turned,
     * with the same normals, faces, overlays and surfaces; for the ruled shapes over all 16,384 neighbourhoods of the
     * model, for every shape over neighbourhoods of our own shapes drawn at random.
     */
    @Test
    void aQuarterTurnTurnsTheShape() {
        for (Form f : RULED) for (int mask = 0; mask < 1 << 14; mask += 3) assertTurns(f, Nbhd.ofMask(mask));
        Random r = new Random(23);
        for (Form f : Form.values()) for (int i = 0; i < 200; i++) assertTurns(f, i % 2 == 0 ? random(r) : crowded(r));
    }

    static void assertTurns(Form f, Nbhd n) {
        List<String> turned = new ArrayList<>(), direct = new ArrayList<>();
        for (Quad q : ShapeGeo.quads(f, n)) turned.add(key(q, true));
        for (Quad q : ShapeGeo.quads(f, n.rotated())) direct.add(key(q, false));
        Collections.sort(turned);
        Collections.sort(direct);
        assertEquals(turned, direct, f + " turned a quarter");
    }

    /** A quad as text, rounded, from its least vertex on: turned a quarter first when {@code turn}. */
    static String key(Quad q, boolean turn) {
        List<double[]> vs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            double[] p = p(q, i), nv = nrm(q, i);
            if (turn) {
                p = new double[] { 1 - p[2], p[1], p[0] };
                nv = new double[] { -nv[2], nv[1], nv[0] };
            }
            double[] v = { p[0], p[1], p[2], nv[0], nv[1], nv[2] };
            if (!vs.isEmpty() && Poly.same(vs.get(vs.size() - 1), v)) continue;
            vs.add(v);
        }
        if (vs.size() > 3 && Poly.same(vs.get(0), vs.get(vs.size() - 1))) vs.remove(vs.size() - 1);
        int start = 0;
        for (int i = 1; i < vs.size(); i++) if (less(vs.get(i), vs.get(start))) start = i;
        StringBuilder b = new StringBuilder();
        int face = q.face;
        if (turn && face >= 2) face = Dir.ring(Dir.ringOf(face) + 1);
        b.append(q.surface)
            .append(' ')
            .append(q.overlay)
            .append(" f")
            .append(face);
        for (int i = 0; i < vs.size(); i++) {
            double[] v = vs.get((start + i) % vs.size());
            for (double c : v) b.append(' ')
                .append(Math.round(c * 1e7));
        }
        return b.toString();
    }

    private static boolean less(double[] a, double[] b) {
        for (int i = 0; i < 3; i++) {
            long x = Math.round(a[i] * 1e7), y = Math.round(b[i] * 1e7);
            if (x != y) return x < y;
        }
        return false;
    }

    // ---- 6. collision

    /**
     * 6. Collision: every box inside the cell; a slope cut along its diagonal (S2) is a stair of two boxes, the lower
     * half whole and the upper half on its closed side, so the gap between what one sees and what one bumps into falls
     * from 0.71 to 0.35 (§3.3.5); half and step are the lower half; everything else is the whole cell.
     */
    @Test
    void collisionBoxes() {
        Random r = new Random(29);
        for (Form f : Form.values()) for (int i = 0; i < 400; i++) {
            Nbhd n = i < 6 ? Nbhd.ofMask((Integer) TABLE[i][1]) : i < 200 ? Nbhd.ofMask(r.nextInt(1 << 14)) : random(r);
            List<double[]> boxes = ShapeGeo.boxes(f, n);
            assertFalse(boxes.isEmpty(), f + " has a box");
            for (double[] b : boxes) {
                for (int k = 0; k < 3; k++) {
                    assertTrue(b[k] >= 0 && b[k + 3] <= 1, f + ": a box outside the cell " + Arrays.toString(b));
                    assertTrue(b[k] < b[k + 3], f + ": an empty box " + Arrays.toString(b));
                }
            }
            if (f == Form.HALF || f == Form.STEP) assertBoxes(boxes, new double[] { 0, 0, 0, 1, 0.5, 1 });
            else if (f != Form.SLOPE || new SlopeGeo(f, n).s2() < 0)
                assertBoxes(boxes, new double[] { 0, 0, 0, 1, 1, 1 });
        }
        // D: open toward +z (south) and -x (west), cut along its diagonal: its upper box is the half away from the
        // south side (the first open side in turning order), on the closed north side
        assertBoxes(
            ShapeGeo.boxes(Form.SLOPE, Nbhd.ofMask(D)),
            new double[] { 0, 0, 0, 1, 0.5, 1 },
            new double[] { 0, 0.5, 0, 1, 1, 0.5 });
        Nbhd d = Nbhd.ofMask(D);
        for (int turn = 0; turn < 4; turn++, d = d.rotated()) {
            List<double[]> boxes = ShapeGeo.boxes(Form.SLOPE, d);
            assertEquals(2, boxes.size());
            // the farthest point of the upper box from the 45-degree face: 0.5 / sqrt 2
            SlopeGeo s = new SlopeGeo(Form.SLOPE, d);
            int k = s.s2();
            double[] b = boxes.get(1), dir = Dir.unit(Dir.vec(k, 1, k + 1, 1, 0));
            double gap = 0;
            for (int c = 0; c < 8; c++) {
                double x = (c & 1) == 0 ? b[0] : b[3], z = (c & 4) == 0 ? b[2] : b[5];
                gap = Math.max(gap, (x - 0.5) * dir[0] + (z - 0.5) * dir[2]);
            }
            assertEquals(Math.sqrt(0.125), gap, 1e-9, "the stair's gap, turn " + turn);
        }
    }

    static void assertBoxes(List<double[]> boxes, double[]... want) {
        assertEquals(want.length, boxes.size());
        for (int i = 0; i < want.length; i++) assertArrayEquals(want[i], boxes.get(i), 1e-12);
    }

    // ---- 7. pure

    /** 7. A pure function: the same input twice gives the same output, bit for bit, whatever ran in between. */
    @Test
    void sameInSameOut() {
        Random r = new Random(31);
        for (Form f : Form.values()) for (int i = 0; i < 50; i++) {
            Nbhd n = random(r);
            String first = bits(ShapeGeo.quads(f, n)) + bits2(ShapeGeo.boxes(f, n));
            ShapeGeo.quads(Form.values()[r.nextInt(Form.values().length)], random(r));
            assertEquals(first, bits(ShapeGeo.quads(f, n)) + bits2(ShapeGeo.boxes(f, n)), f.name());
        }
    }

    static String bits(List<Quad> qs) {
        StringBuilder b = new StringBuilder();
        for (Quad q : qs) {
            for (double v : q.pos) b.append(Long.toHexString(Double.doubleToRawLongBits(v)))
                .append(',');
            for (double v : q.normal) b.append(Long.toHexString(Double.doubleToRawLongBits(v)))
                .append(',');
            for (double v : q.uv) b.append(Long.toHexString(Double.doubleToRawLongBits(v)))
                .append(',');
            b.append(q.face)
                .append(q.uAxis)
                .append(q.vAxis)
                .append(q.overlay)
                .append(q.conn)
                .append(q.surface)
                .append(';');
        }
        return b.toString();
    }

    static String bits2(List<double[]> boxes) {
        StringBuilder b = new StringBuilder();
        for (double[] x : boxes) for (double v : x) b.append(Long.toHexString(Double.doubleToRawLongBits(v)))
            .append(',');
        return b.toString();
    }

    // ---- faces between neighbours

    /** A wall of rounds draws no face where two of them meet, and none against an opaque cube. */
    @Test
    void facesBetweenRoundsAreCulled() {
        int ours = Nbhd.ours(Form.ROUND, true);
        Nbhd n = new Nbhd.Builder().set(1, 0, 0, ours)
            .set(-1, 0, 0, ours)
            .set(0, -1, 0, Nbhd.SOLID)
            .build();
        for (Quad q : ShapeGeo.quads(Form.ROUND, n)) assertTrue(q.face != Dir.EAST && q.face != Dir.WEST, "" + q);
        assertEquals(
            10,
            ShapeGeo.quads(Form.ROUND, n)
                .size(),
            "a wall's middle is case A");
    }

    /**
     * A whole cube against a round's end draws exactly what the round leaves open of their shared face: the two bites
     * its rounded top edges take out of the upper corners, 2 × (1/4 − the four-segment quarter disc).
     */
    @Test
    void aCubeFillsWhatTheRoundLeaves() {
        Nbhd cube = new Nbhd.Builder().set(-1, 0, 0, Nbhd.ours(Form.ROUND, true))
            .set(0, -1, 0, Nbhd.SOLID)
            .set(-1, -1, 0, Nbhd.SOLID)
            .set(1, 0, 0, Nbhd.SOLID)
            .build();
        double west = 0;
        for (Quad q : ShapeGeo.quads(Form.WHOLE, cube)) if (q.face == Dir.WEST) west += area(q);
        double disc = 4 * 0.5 * 0.25 * Math.sin(Math.PI / 8);
        assertEquals(2 * (0.25 - disc), west, 1e-9);
        // and the round draws nothing toward the cube
        Nbhd round = new Nbhd.Builder().set(1, 0, 0, Nbhd.ours(Form.WHOLE, true))
            .set(-1, 0, 0, Nbhd.SOLID)
            .set(0, -1, 0, Nbhd.SOLID)
            .set(1, -1, 0, Nbhd.SOLID)
            .build();
        for (Quad q : ShapeGeo.quads(Form.ROUND, round)) assertTrue(q.face != Dir.EAST, "" + q);
    }

    /** Windows of one family show no glass between them; of two families each shows its own; a cube behind shows. */
    @Test
    void windowsJoin() {
        Nbhd same = new Nbhd.Builder().set(1, 0, 0, Nbhd.ours(Form.WINDOW, true))
            .build();
        Nbhd other = new Nbhd.Builder().set(1, 0, 0, Nbhd.ours(Form.WINDOW, false))
            .build();
        Nbhd cube = new Nbhd.Builder().set(1, 0, 0, Nbhd.ours(Form.WHOLE, true))
            .build();
        assertEquals(
            5,
            ShapeGeo.quads(Form.WINDOW, same)
                .size());
        assertEquals(
            6,
            ShapeGeo.quads(Form.WINDOW, other)
                .size());
        assertEquals(
            6,
            ShapeGeo.quads(
                Form.WHOLE,
                new Nbhd.Builder().set(-1, 0, 0, Nbhd.ours(Form.WINDOW, true))
                    .build())
                .size(),
            "a cube behind a window shows its face");
        assertEquals(
            6,
            ShapeGeo.quads(Form.WINDOW, cube)
                .size());
        // the window's frame runs on into the window beside it: on its south face (u toward +x) the bit for +u
        for (Quad q : ShapeGeo.quads(Form.WINDOW, same)) if (q.face == Dir.SOUTH) assertEquals(2, q.conn);
    }

    // ---- overlays

    /** The keel's strip ends in a point where no keel stands above; the step's nose faces the open; the lamp glows. */
    @Test
    void overlays() {
        for (Quad q : ShapeGeo.quads(Form.KEEL, Nbhd.ISOLATED))
            assertEquals(q.face >= 2 ? Overlay.KEEL_TIP : Overlay.NONE, q.overlay, "" + q);
        Nbhd up = new Nbhd.Builder().set(0, 1, 0, Nbhd.ours(Form.KEEL, true))
            .build();
        for (Quad q : ShapeGeo.quads(Form.KEEL, up)) assertEquals(q.face >= 2 ? Overlay.KEEL : Overlay.NONE, q.overlay);
        // a step with a step east of it, a wall west, air north and south: noses north and south only
        Nbhd step = new Nbhd.Builder().set(1, 0, 0, Nbhd.ours(Form.STEP, true))
            .set(-1, 0, 0, Nbhd.SOLID)
            .set(0, -1, 0, Nbhd.SOLID)
            .build();
        List<Integer> noses = new ArrayList<>();
        for (Quad q : ShapeGeo.quads(Form.STEP, step)) if (q.overlay == Overlay.NOSE) noses.add(q.face);
        Collections.sort(noses);
        assertEquals(Arrays.asList(Dir.NORTH, Dir.SOUTH), noses);
        // a lamp between lamps east and above: its south face continues toward +u (east) and -v (up)
        Nbhd lamp = new Nbhd.Builder().set(1, 0, 0, Nbhd.ours(Form.LAMP, true))
            .set(0, 1, 0, Nbhd.ours(Form.LAMP, true))
            .build();
        for (Quad q : ShapeGeo.quads(Form.LAMP, lamp)) if (q.face == Dir.SOUTH) {
            assertEquals(Overlay.LAMP, q.overlay);
            assertEquals(2 | 4, q.conn);
        }
        // a light seam shows its line on its sides only; a pool lip its lip on top and toward water
        for (Quad q : ShapeGeo.quads(Form.LIGHT_SEAM, Nbhd.ISOLATED))
            assertEquals(q.face >= 2 ? Overlay.LINE : Overlay.NONE, q.overlay);
        Nbhd pool = new Nbhd.Builder().set(0, 0, 1, Nbhd.WATER)
            .build();
        for (Quad q : ShapeGeo.quads(Form.POOL_LIP, pool))
            assertEquals(q.face == Dir.UP || q.face == Dir.SOUTH ? Overlay.LIP : Overlay.NONE, q.overlay, "" + q);
    }

    // ---- budget, items

    /** No cell gives more quads than {@link ShapeGeo#MAX_QUADS}; each shape's most, measured. */
    @Test
    void budget() {
        Random r = new Random(37);
        Map<Form, Integer> most = new EnumMap<>(Form.class), alone = new EnumMap<>(Form.class);
        for (Form f : Form.values()) {
            int m = 0;
            for (int mask = 0; mask < 1 << 14; mask += f == Form.ROUND || f == Form.SLOPE ? 1 : 7) m = Math.max(
                m,
                ShapeGeo.quads(f, Nbhd.ofMask(mask))
                    .size());
            alone.put(f, m);
            for (int i = 0; i < 1500; i++) m = Math.max(
                m,
                ShapeGeo.quads(f, random(r))
                    .size());
            for (int i = 0; i < 1500; i++) m = Math.max(
                m,
                ShapeGeo.quads(f, crowded(r))
                    .size());
            most.put(f, m);
            assertTrue(m <= ShapeGeo.MAX_QUADS, f + " gives " + m);
        }
        // a round standing alone: 4 vertical edges of 4, 4 corners of 4, and its foot in four quarter discs of 2
        assertEquals(
            40,
            ShapeGeo.quads(Form.ROUND, Nbhd.ISOLATED)
                .size());
        // among opaque cubes and air a whole-block shape is the six faces at most
        for (Form f : Form.values()) if (f.wholeBlock()) assertEquals(6, (int) alone.get(f), f.name());
        System.out.println("ShapeGeo most quads per cell, among cubes and air: " + alone);
        System.out.println("ShapeGeo most quads per cell, among our shapes: " + most);
    }

    /** The item of every shape is closed. */
    @Test
    void itemsAreClosed() {
        for (Form f : Form.values()) {
            List<double[][]> polys = MeshCheck.polys(ShapeGeo.item(f));
            assertNull(MeshCheck.closure(polys), f.name());
            assertTrue(MeshCheck.volume(polys) > 0.05, f.name());
        }
        // the soffit's item shows its concave face
        boolean concave = false;
        for (Quad q : ShapeGeo.item(Form.SOFFIT)) concave |= q.surface == Quad.Surface.CONCAVE;
        assertTrue(concave);
    }

    // ---- families and codes

    /** The families' numbers: the hull as it is, the footing's and the skins' replacements (§3.2). */
    @Test
    void familiesNumberTheirShapes() {
        for (int i = 0; i < 16; i++) assertEquals(Form.of(i), Family.HULL.form(i));
        assertEquals(Form.COPING, Family.FOOTING.form(6));
        assertEquals(Form.SOFFIT, Family.FOOTING.form(8));
        assertEquals(Form.POOL_LIP, Family.FOOTING.form(9));
        assertEquals(Form.SPOUT, Family.FOOTING.form(11));
        assertEquals(Form.PLANTER, Family.FOOTING.form(12));
        assertEquals(Form.GRATE, Family.FOOTING.form(13));
        assertEquals(Form.LAMP, Family.SKIN_SCRIPT.form(13)); // paper's lantern panel
        assertEquals(Form.GRATE, Family.SKIN_SCRIPT.form(7)); // ink's lattice
        assertEquals(Form.WINDOW, Family.SKIN_GARDEN.form(7)); // copper-framed window
        assertNull(Family.SKIN_GARDEN.form(15)); // marble's baluster comes with 0.20.0
        for (Family fam : Family.values()) for (int i = 0; i < 16; i++) {
            Form f = fam.form(i);
            if (fam != Family.HULL && f != null) {
                assertTrue(f != Form.RIB && (fam == Family.FOOTING || f != Form.COLUMN), fam + " has no ribs");
            }
        }
        assertTrue(Form.ROUND.closed() && Form.SLOPE.closed() && Form.SOFFIT.closed());
        for (Form f : new Form[] { Form.HALF, Form.STEP, Form.RIB, Form.COLUMN, Form.WINDOW, Form.LOUVRE })
            assertFalse(f.closed(), f + " is thin (§3.3.1)");
        for (Form f : new Form[] { Form.WHOLE, Form.SEAM, Form.LIGHT_SEAM, Form.KEEL, Form.LAMP, Form.FLOOR,
            Form.RELIEF }) assertTrue(f.wholeBlock(), f + " is in the whole-block id (§3.1)");
    }

    private static final Pattern ROW = Pattern.compile("^\\| (\\d+) \\| ([^|]+) \\|");

    /** Every code of the 0.17.0 batch places what its row in the ID ledger (ids.md §1.2, §1.4) says. */
    @Test
    void partCodesMatchTheLedger() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get("docs/dev/ids.md"), StandardCharsets.UTF_8);
        int seen = 0;
        for (String line : lines) {
            Matcher m = ROW.matcher(line);
            if (!m.find()) continue;
            int code = Integer.parseInt(m.group(1));
            String meaning = m.group(2);
            // the part code tables' rows (the ledger's other tables number their rows too)
            if (!(code >= 59 && code <= 62 || code >= 96 && code <= 127) || !meaning.contains(" · 形 ")) continue;
            Family fam = FormCodes.family(code);
            assertNotNull(fam, "code " + code);
            assertTrue(meaning.contains("`" + fam.id + "`"), code + ": " + meaning);
            Matcher shape = Pattern.compile("形 (\\d+) ")
                .matcher(meaning);
            assertTrue(shape.find(), code + ": " + meaning);
            assertEquals(Integer.parseInt(shape.group(1)), FormCodes.number(code), code + ": " + meaning);
            Form f = FormCodes.form(code);
            String word = code == 125 ? "灯笼板" : f.zh;
            assertTrue(meaning.contains(word), code + ": " + meaning + " is not " + f);
            assertEquals(code, FormCodes.code(fam, FormCodes.number(code)));
            seen++;
        }
        assertEquals(36, seen, "the batch's codes: 32 in 96–127 and 4 in 59–62");
        assertNull(FormCodes.form(54));
        assertNull(FormCodes.form(128));
    }

    // ---- helpers

    /** A neighbourhood drawn at random from every kind of cell, our shapes of both families. */
    static Nbhd random(Random r) {
        int[] c = new int[27];
        Form[] forms = Form.values();
        for (int i = 0; i < 27; i++) {
            int k = r.nextInt(8);
            c[i] = k < 2 ? Nbhd.OPEN
                : k == 2 ? Nbhd.SOLID
                    : k == 3 ? Nbhd.WATER : Nbhd.ours(forms[r.nextInt(forms.length)], r.nextInt(4) > 0);
        }
        return Nbhd.of(c);
    }

    /** A neighbourhood crowded with our shapes that cut a face into pieces: rounds, slopes, columns, ribs, louvres. */
    static Nbhd crowded(Random r) {
        Form[] heavy = { Form.ROUND, Form.SLOPE, Form.COPING, Form.SOFFIT, Form.HALF, Form.COLUMN, Form.RIB,
            Form.LOUVRE, Form.STEP };
        int[] c = new int[27];
        for (int i = 0; i < 27; i++) {
            int k = r.nextInt(10);
            c[i] = k == 0 ? Nbhd.OPEN : k == 1 ? Nbhd.SOLID : Nbhd.ours(heavy[r.nextInt(heavy.length)], true);
        }
        return Nbhd.of(c);
    }

    static double[] p(Quad q, int i) {
        return new double[] { q.x(i), q.y(i), q.z(i) };
    }

    static double[] nrm(Quad q, int i) {
        return new double[] { q.normal[3 * i], q.normal[3 * i + 1], q.normal[3 * i + 2] };
    }

    static double[] centre(Quad q) {
        double[][] d = Poly.dedup(new double[][] { p(q, 0), p(q, 1), p(q, 2), p(q, 3) });
        double[] c = new double[3];
        for (double[] v : d) for (int k = 0; k < 3; k++) c[k] += v[k] / d.length;
        return c;
    }

    static double area(Quad q) {
        return len(Poly.newell(new double[][] { p(q, 0), p(q, 1), p(q, 2), p(q, 3) })) / 2;
    }

    static double[] sub(double[] a, double[] b) {
        return new double[] { a[0] - b[0], a[1] - b[1], a[2] - b[2] };
    }

    static double[] scale(double[] a, double s) {
        return new double[] { a[0] * s, a[1] * s, a[2] * s };
    }

    static double[] cross(double[] a, double[] b) {
        return new double[] { a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0] };
    }

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static double len(double[] a) {
        return Math.sqrt(dot(a, a));
    }

    static double[] unit(double[] a) {
        return scale(a, 1 / len(a));
    }

    static boolean near(double[] a, double[] b, double eps) {
        return Math.abs(a[0] - b[0]) < eps && Math.abs(a[1] - b[1]) < eps && Math.abs(a[2] - b[2]) < eps;
    }
}
