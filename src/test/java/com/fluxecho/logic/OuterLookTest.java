package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * What the client draws of the outer ring (0.15.0, CITY.md §2.3, M12 §7): the masterplan's streets, avenues, gate and
 * promenade outline exactly the cells the outer establish paves; the outer sites' outlines and signs keep off every
 * building that can stand there; the split line is the same for both campuses and runs where the split does; the
 * projection's outline of the ground left to a neighbour bounds exactly what the job does not grade, which the 建造
 * page counts; an outer diagonal over the split is the one the campus does not offer.
 */
class OuterLookTest {

    @Test
    void theStreetsOutlineTheirCells() {
        List<OuterLook.Path> streets = OuterLook.streets();
        assertEquals(8, streets.size(), "four streets, each in two halves either side of the promenade ring");
        Set<Long> union = new HashSet<>();
        int i = 0;
        for (int sign = 1; sign >= -1; sign -= 2) for (int along = 1; along >= -1; along -= 2) {
            for (boolean alongA : new boolean[] { true, false }) {
                // the half street's own cells: cross 18..20 out from radial 8 to 60, outside the promenade ring
                Set<Long> cells = new HashSet<>();
                for (int c = CampusPlan.STREET_FROM; c <= CampusPlan.STREET_TO; c++)
                    for (int t = 8; t <= CampusPlan.STREET_REACH; t++) {
                        int a = alongA ? along * t : sign * c, r = alongA ? sign * c : along * t;
                        if (!CampusPlan.oct(a, r, CampusPlan.RING_R, CampusPlan.RING_K))
                            cells.add(CampusPlan.key(a, r));
                    }
                union.addAll(cells);
                Set<String> want = outline(cells);
                // open where it meets the outer promenade
                String open = (alongA ? "A:" : "R:") + 2 * along * 60.5 + ":";
                want.removeIf(e -> e.startsWith(open));
                assertEquals(want, edges(streets.get(i++)), "street " + i);
            }
        }
        assertEquals(CampusPlan.streetCells(), union, "every street cell, no other");
    }

    @Test
    void theAvenuesAndTheOuterGateOutlineTheirCells() {
        List<OuterLook.Path> av = OuterLook.avenues();
        assertEquals(7, av.size(), "the front avenue with the gate, and the other three's two edges each");
        Set<Long> front = new HashSet<>(OuterRing.outerGate());
        Set<Long> all = OuterRing.avenues();
        for (long k : all) if (CampusPlan.keyA(k) >= OuterRing.AVENUE_FRONT_FROM && Math.abs(CampusPlan.keyR(k)) <= 3
            && CampusPlan.keyA(k) > Math.abs(CampusPlan.keyR(k))) front.add(k);
        Set<String> want = outline(front);
        // open toward the gatehouse
        want.removeIf(e -> e.startsWith("A:" + 2 * 39.5 + ":"));
        assertEquals(want, edges(av.get(0)), "the front avenue and the outer gate");
        // the others: their sides, from the outer promenade's inner edge to the disc's
        int[][] axes = { { 0, 1 }, { -1, 0 }, { 0, -1 } };
        for (int k = 0; k < 3; k++) {
            Set<Long> cells = new HashSet<>();
            for (int t = OuterRing.AVENUE_FROM; t <= OuterRing.AVENUE_TO; t++) for (int s = -3; s <= 3; s++)
                cells.add(CampusPlan.key(t * axes[k][0] - s * axes[k][1], t * axes[k][1] + s * axes[k][0]));
            assertTrue(all.containsAll(cells));
            Set<String> side = outline(cells), got = new HashSet<>(edges(av.get(1 + 2 * k)));
            got.addAll(edges(av.get(2 + 2 * k)));
            // their ends lie across the axis (on the promenade's inner edge and the disc's)
            String across = axes[k][0] == 0 ? "R:" : "A:";
            side.removeIf(e -> e.startsWith(across));
            assertEquals(side, got, "avenue " + k);
        }
        // the pylons' squares on their cells, on the gate's tiles
        List<OuterLook.Path> py = OuterLook.pylons();
        assertEquals(4, py.size());
        for (int k = 0; k < 4; k++) {
            int[] c = OuterRing.pylons()
                .get(k);
            assertEquals(outline(Collections.singleton(CampusPlan.key(c[0], c[1]))), edges(py.get(k)));
            assertTrue(OuterRing.inOuterGate(c[0], c[1]));
        }
    }

    @Test
    void thePromenadeAndTheDiscAreTheirOctagons() {
        double[][] out = OuterLook.promenade()
            .get(0).pts,
            in = OuterLook.promenade()
                .get(1).pts,
            disc = OuterLook.disc()
                .get(0).pts;
        for (int a = -100; a <= 100; a++) for (int r = -100; r <= 100; r++) {
            assertEquals(
                OuterRing.inPromenade(a, r),
                inside(out, a, r) && !inside(in, a, r),
                "promenade at " + a + "," + r);
            assertEquals(OuterRing.inOuterDisc(a, r), inside(disc, a, r), "disc at " + a + "," + r);
        }
    }

    @Test
    void theOuterSitesAreOutlinedAndSigned() {
        for (int site : OuterRing.DIAG_SITES) {
            double[][] o = OuterLook.diagOutline(site);
            int[] c = OuterRing.diagCentre(site);
            for (int a = c[0] - 20; a <= c[0] + 20; a++) for (int r = c[1] - 20; r <= c[1] + 20; r++)
                assertEquals(OuterRing.inDiag(site, a, r), inside(o, a, r), "O" + (site - 10) + " at " + a + "," + r);
            double[] s = OuterLook.signAt(site);
            int sa = (int) Math.round(s[0]), sr = (int) Math.round(s[1]);
            long k = CampusPlan.key(sa, sr);
            assertTrue(
                OuterRing.diagAllee(site)
                    .contains(k),
                "on its allée");
            assertFalse(
                OuterRing.diagCells(site)
                    .contains(k)
                    || OuterRing.diagApron(site)
                        .contains(k),
                "outside its building and apron");
            assertEquals(OuterLook.SIGN_UP, s[2], 0);
        }
        for (int site = OuterRing.FLANK_FIRST; site <= OuterRing.FLANK_LAST; site++) {
            Set<Long> cells = new HashSet<>();
            for (double[] p : OuterLook.flankOutline(site)) assertNotNull(p);
            double[][] o = OuterLook.flankOutline(site);
            for (int a = -100; a <= 100; a++)
                for (int r = -100; r <= 100; r++) if (inside(o, a, r)) cells.add(CampusPlan.key(a, r));
            assertEquals(OuterRing.flankCells(site), cells, "flanker " + site);
            double[] s = OuterLook.signAt(site);
            int sa = (int) Math.round(s[0]), sr = (int) Math.round(s[1]);
            assertTrue(OuterRing.inPromenade(sa, sr), "on the outer promenade before its doors");
            for (int other = OuterRing.FLANK_FIRST; other <= OuterRing.FLANK_LAST; other++)
                assertFalse(OuterRing.inFlank(other, sa, sr), "outside every flanker");
            for (int d : OuterRing.DIAG_SITES) assertFalse(
                OuterRing.diagSiteCells(d)
                    .contains(CampusPlan.key(sa, sr)));
        }
    }

    @Test
    void theSplitLineIsTheSameForBothCampuses() {
        int[][] pairs = { { 0, 0, 150, 0 }, { 0, 0, 144, 100 }, { 1000, -40, 1160, 60 }, { 5, 5, -140, -140 } };
        for (int[] p : pairs) {
            double[] a = OuterLook.splitLine(p[0], p[1], p[2], p[3]), b = OuterLook.splitLine(p[2], p[3], p[0], p[1]);
            assertNotNull(a);
            assertNotNull(b);
            for (double[] l : new double[][] { a, b }) for (int k = 0; k < 2; k++) {
                double x = l[2 * k], z = l[2 * k + 1];
                // equally far from both centres' middles
                assertEquals(
                    Math.hypot(x - p[0] - 0.5, z - p[1] - 0.5),
                    Math.hypot(x - p[2] - 0.5, z - p[3] - 0.5),
                    1e-6,
                    Arrays.toString(p));
            }
            // the same segment from both sides, its ends inside both discs and on the edge of one (where the rings
            // meet)
            boolean same = Math.abs(a[0] - b[0]) < 1e-6 && Math.abs(a[1] - b[1]) < 1e-6
                && Math.abs(a[2] - b[2]) < 1e-6
                && Math.abs(a[3] - b[3]) < 1e-6
                || Math.abs(a[0] - b[2]) < 1e-6 && Math.abs(a[1] - b[3]) < 1e-6
                    && Math.abs(a[2] - b[0]) < 1e-6
                    && Math.abs(a[3] - b[1]) < 1e-6;
            assertTrue(same, "both draw the same segment " + Arrays.toString(p));
            for (int k = 0; k < 2; k++) {
                double over = Math
                    .max(over(a[2 * k], a[2 * k + 1], p[0], p[1]), over(a[2 * k], a[2 * k + 1], p[2], p[3]));
                assertEquals(0, over, 1e-6, "on the edge of where both discs are");
            }
            // columns either side of it go to the nearer campus, away from the outer diagonals (the split bends round
            // those, CampusSplit; their octagons are drawn)
            double nx = p[2] - p[0], nz = p[3] - p[1], len = Math.hypot(nx, nz);
            List<int[]> other = Collections.singletonList(new int[] { p[2], p[3] });
            int tried = 0;
            for (int step = 1; step < 20; step++) {
                double t = step / 20.0;
                double mx = a[0] + (a[2] - a[0]) * t, mz = a[1] + (a[3] - a[1]) * t;
                int ox = (int) Math.floor(mx - 3 * nx / len), oz = (int) Math.floor(mz - 3 * nz / len),
                    tx = (int) Math.floor(mx + 3 * nx / len), tz = (int) Math.floor(mz + 3 * nz / len);
                boolean onSite = false;
                for (int[] c : new int[][] { { ox, oz }, { tx, tz } })
                    onSite |= CampusSplit.siteAt(c[0], c[1], p[0], p[1]) >= 0
                        || CampusSplit.siteAt(c[0], c[1], p[2], p[3]) >= 0;
                // near an end a column may lie outside the other ring, which leaves it to this one (CampusSplit)
                onSite |= !OuterRing.inOuterDisc(ox - p[0], oz - p[1]) || !OuterRing.inOuterDisc(tx - p[2], tz - p[3]);
                if (onSite) continue;
                tried++;
                assertTrue(CampusSplit.ours(ox, oz, p[0], p[1], other), "our side");
                assertFalse(CampusSplit.ours(tx, tz, p[0], p[1], other), "their side");
            }
            assertTrue(tried >= 2, "the line runs off the sites somewhere: " + Arrays.toString(p));
        }
        // too far to share anything, or the campus itself
        assertNull(OuterLook.splitLine(0, 0, 400, 0));
        assertNull(OuterLook.splitLine(7, 7, 7, 7));
    }

    @Test
    void theTrimOutlinesTheGroundLeftToTheNeighbours() {
        CampusPlan plan = new CampusPlan(12345L);
        int cx = 0, cz = 0;
        for (int[] f : new int[][] { { 1, 0 }, { 0, -1 } }) {
            assertTrue(
                OuterLook.givenUp(plan, cx, cz, f[0], f[1], Collections.<int[]>emptyList())
                    .isEmpty());
            assertEquals(0, OuterLook.trimOutline(plan, cx, cz, f[0], f[1], null).length);
            List<int[]> near = Arrays.asList(new int[] { 150, 0 }, new int[] { -60, 160 });
            List<int[]> given = OuterLook.givenUp(plan, cx, cz, f[0], f[1], near);
            assertEquals(
                BuildPlan.outerTrimmed(plan, cx, cz, f[0], f[1], near)[0],
                given.size(),
                "the 建造 page's count");
            assertTrue(given.size() > 1000);
            Set<Long> set = new HashSet<>();
            for (int[] g : given) {
                assertFalse(CampusSplit.ours(g[0], g[1], cx, cz, near));
                assertTrue(set.add(CampusPlan.key(g[0], g[1])), "each column once");
            }
            // the outline bounds exactly that ground: one side of every unit edge in it, the other out
            int[] e = OuterLook.trimOutline(plan, cx, cz, f[0], f[1], near);
            assertTrue(e.length > 0 && e.length % 4 == 0);
            int units = 0;
            for (int k = 0; k < e.length; k += 4) {
                boolean alongX = e[k + 1] == e[k + 3];
                for (int u = alongX ? e[k] : e[k + 1]; u < (alongX ? e[k + 2] : e[k + 3]); u++) {
                    units++;
                    long in = alongX ? CampusPlan.key(u, e[k + 1]) : CampusPlan.key(e[k], u),
                        out = alongX ? CampusPlan.key(u, e[k + 1] - 1) : CampusPlan.key(e[k] - 1, u);
                    assertTrue(set.contains(in) != set.contains(out), "an edge of the left ground");
                }
            }
            assertEquals(boundary(set), units);
        }
    }

    /**
     * An outer diagonal is left to a neighbour when the neighbour holds part of it for good (0.15.0): its inner square,
     * or an outer diagonal it claimed; a site the split only takes part of while nobody claims it is not (the campus
     * may still take it). The engine's rule ({@link CampusSplit#siteLoss} below 0), for every site.
     */
    @Test
    void anOuterDiagonalANeighbourHoldsIsLeftToIt() {
        int cx = 300, cz = -200;
        // a neighbour on the front at the old spacing (facing +x, O1 is +A +R = +x +z), claiming nothing: no site is
        // left to it, the split gives the overlapping pair of sites to the first by its centre
        List<int[]> near = Collections.singletonList(new int[] { cx + 144, cz });
        for (int site : OuterRing.DIAG_SITES) assertFalse(OuterLook.siteTrimmed(site, cx, cz, 1, 0, near));
        // it claims its outer diagonal over O1's ground (world -x +z of it): O1 is left to it, O7 beside it is not
        int theirs = 1 << CampusSplit.quadrant(-1, 1);
        List<int[]> claimed = Collections.singletonList(new int[] { cx + 144, cz, theirs });
        assertTrue(OuterLook.siteTrimmed(11, cx, cz, 1, 0, claimed));
        assertFalse(OuterLook.siteTrimmed(17, cx, cz, 1, 0, claimed));
        assertFalse(OuterLook.siteTrimmed(15, cx, cz, 1, 0, claimed), "the far one stays");
        // a neighbour nearer than the old spacing holds O1's ground in its inner square
        List<int[]> close = Collections.singletonList(new int[] { cx + 130, cz });
        assertTrue(OuterLook.siteTrimmed(11, cx, cz, 1, 0, close));
        assertTrue(OuterLook.siteTrimmed(17, cx, cz, 1, 0, close));
        assertFalse(OuterLook.siteTrimmed(13, cx, cz, 1, 0, close));
        for (int site : OuterRing.DIAG_SITES) assertFalse(OuterLook.siteTrimmed(site, cx, cz, 1, 0, null));
        assertFalse(OuterLook.siteTrimmed(3, cx, cz, 1, 0, claimed), "an inner site is whole");
        // the engine's rule
        for (List<int[]> l : java.util.Arrays.asList(near, claimed, close))
            for (int[] f : new int[][] { { 1, 0 }, { 0, -1 } }) for (int site : OuterRing.DIAG_SITES) assertEquals(
                CampusSplit.siteLoss(CampusSplit.siteQuadrant(site, f[0], f[1]), cx, cz, 0, l) < 0,
                OuterLook.siteTrimmed(site, cx, cz, f[0], f[1], l),
                "O" + (site - 10));
    }

    /**
     * The neighbours the masterplan judges the outer ring against (0.15.0): the server's list first (its claims kept),
     * then the job's, then the loaded campuses within reach, each once, sorted, the campus itself and the far ones left
     * out; so a neighbour whose nexus the client never loaded still splits the ring, after the job ended too.
     */
    @Test
    void theMasterplansNeighboursComeFromTheServerFirst() {
        List<int[]> split = Arrays.asList(new int[] { 150, 0, 4 }, new int[] { -150, 0 });
        List<int[]> job = Arrays.asList(new int[] { 150, 0 }, new int[] { 0, 160 });
        List<int[]> loaded = Arrays.asList(new int[] { 0, 0 }, new int[] { 0, -170 }, new int[] { 500, 0 });
        List<int[]> got = OuterLook.neighbours(split, job, loaded, 0, 0);
        assertEquals(4, got.size());
        assertArrayEquals(new int[] { -150, 0 }, got.get(0));
        assertArrayEquals(new int[] { 0, -170 }, got.get(1));
        assertArrayEquals(new int[] { 0, 160 }, got.get(2));
        assertArrayEquals(new int[] { 150, 0, 4 }, got.get(3), "the server's claims kept");
        assertEquals(
            2,
            OuterLook.neighbours(split, null, null, 0, 0)
                .size(),
            "with no job and nothing loaded");
        assertTrue(
            OuterLook.neighbours(null, null, null, 0, 0)
                .isEmpty());
    }

    // ---- helpers

    /** The unit edges of a path, as "A:2a:2r0" (a line at A = a from R = r0 up) or "R:2r:2a0". */
    private static Set<String> edges(OuterLook.Path p) {
        Set<String> out = new HashSet<>();
        int n = p.pts.length, segs = p.closed ? n : n - 1;
        for (int i = 0; i < segs; i++) {
            double[] u = p.pts[i], v = p.pts[(i + 1) % n];
            if (u[0] == v[0]) for (double r = Math.min(u[1], v[1]); r < Math.max(u[1], v[1]) - 1e-9; r++)
                out.add("A:" + 2 * u[0] + ":" + 2 * r);
            else {
                assertEquals(u[1], v[1], 0, "an axis-aligned line");
                for (double a = Math.min(u[0], v[0]); a < Math.max(u[0], v[0]) - 1e-9; a++)
                    out.add("R:" + 2 * u[1] + ":" + 2 * a);
            }
        }
        return out;
    }

    /** The unit edges round a set of campus cells, as {@link #edges} names them. */
    private static Set<String> outline(Set<Long> cells) {
        Set<String> out = new HashSet<>();
        for (long k : cells) {
            int a = CampusPlan.keyA(k), r = CampusPlan.keyR(k);
            if (!cells.contains(CampusPlan.key(a + 1, r))) out.add("A:" + 2 * (a + 0.5) + ":" + 2 * (r - 0.5));
            if (!cells.contains(CampusPlan.key(a - 1, r))) out.add("A:" + 2 * (a - 0.5) + ":" + 2 * (r - 0.5));
            if (!cells.contains(CampusPlan.key(a, r + 1))) out.add("R:" + 2 * (r + 0.5) + ":" + 2 * (a - 0.5));
            if (!cells.contains(CampusPlan.key(a, r - 1))) out.add("R:" + 2 * (r - 0.5) + ":" + 2 * (a - 0.5));
        }
        return out;
    }

    /** How many unit edges bound a set of world columns (keyed as campus cells). */
    private static int boundary(Set<Long> cells) {
        int n = 0;
        for (long k : cells) {
            int a = CampusPlan.keyA(k), r = CampusPlan.keyR(k);
            for (int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } })
                if (!cells.contains(CampusPlan.key(a + d[0], r + d[1]))) n++;
        }
        return n;
    }

    /** Whether the cell (a, r)'s middle lies strictly inside a convex polygon (its corners in order). */
    private static boolean inside(double[][] poly, int a, int r) {
        int sign = 0;
        for (int i = 0; i < poly.length; i++) {
            double[] u = poly[i], v = poly[(i + 1) % poly.length];
            double cross = (v[0] - u[0]) * (r - u[1]) - (v[1] - u[1]) * (a - u[0]);
            if (Math.abs(cross) < 1e-9) return false;
            int s = cross > 0 ? 1 : -1;
            if (sign == 0) sign = s;
            else if (s != sign) return false;
        }
        return true;
    }

    /** How far the point (x, z) lies outside the outer disc round the centre (cx, cz)'s middle (0 on its edge). */
    private static double over(double x, double z, int cx, int cz) {
        double u = Math.abs(x - cx - 0.5), v = Math.abs(z - cz - 0.5);
        return Math.max(Math.max(u, v) - (OuterRing.OUTER_R + 0.5), u + v - (OuterRing.OUTER_K + 1));
    }

}
