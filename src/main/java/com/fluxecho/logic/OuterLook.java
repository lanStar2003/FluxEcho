package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the client draws of a campus's outer ring (0.15.0, CITY.md §2.3, M12 §7), worked out from {@link OuterRing}
 * and {@link CampusSplit} so the drawing and the ground the outer establish lays always agree:
 * <ul>
 * <li>The masterplan's outer ring, as lines in the campus frame (A ahead, R to the right, cell edges at half blocks):
 * the outer disc's edge ({@link #disc}), the outer promenade's two edges ({@link #promenade}), the eight halves of the
 * four 井 streets from the promenade ring out to the outer promenade, each with its stepped inner end
 * ({@link #streets}), the four avenues with the outer gate's widening on the front one ({@link #avenues}), the outer
 * gate's pylons ({@link #pylons}), the outer diagonals' octagons ({@link #diagOutline}) and the reserved outer
 * flankers' envelopes ({@link #flankOutline}), each outer site's sign ({@link #signAt}).</li>
 * <li>The split with a neighbouring campus: on the masterplan the straight bisector between the two nexus centres
 * where it crosses both campuses' outer discs ({@link #splitLine}, the line the squared-Euclidean split runs along; the
 * cells right at an inner disc's square keep to the square, which the projection's outline shows exactly); in the
 * outer establish's projection the outline of every column of its ground the neighbours keep ({@link #givenUp},
 * {@link #trimOutline}); and which outer diagonals reach over the split and are not offered here
 * ({@link #siteTrimmed}, the engine's rule).</li>
 * </ul>
 * Pure arithmetic in the campus frame and in world columns, the same on every client.
 */
public final class OuterLook {

    /** A line of the masterplan: its points {a, r} in the campus frame, and whether it closes back to its first. */
    public static final class Path {

        public final double[][] pts;
        public final boolean closed;

        Path(double[][] pts, boolean closed) {
            this.pts = pts;
            this.closed = closed;
        }
    }

    /** How high over the floor an outer site's sign stands (as a hall's before its door). */
    public static final double SIGN_UP = 3.2;
    /** How far along its avenue a reserved flanker's sign stands: on the outer promenade before its doors. */
    public static final double FLANK_SIGN = OuterRing.FLANK_FRONT - 2;

    private static final List<Path> DISC, PROMENADE, STREETS, AVENUES, PYLONS;

    static {
        DISC = Collections.singletonList(new Path(octagon(OuterRing.OUTER_R, OuterRing.OUTER_K), true));
        List<Path> p = new ArrayList<>();
        p.add(new Path(octagon(OuterRing.PROM_R, OuterRing.PROM_K), true));
        p.add(new Path(octagon(OuterRing.PROM_IN_R, OuterRing.PROM_IN_K), true));
        PROMENADE = Collections.unmodifiableList(p);
        STREETS = Collections.unmodifiableList(streetPaths());
        AVENUES = Collections.unmodifiableList(avenuePaths());
        List<Path> py = new ArrayList<>();
        for (int[] c : OuterRing.pylons()) py.add(new Path(square(c[0], c[1]), true));
        PYLONS = Collections.unmodifiableList(py);
    }

    private OuterLook() {}

    /**
     * The outline of the cells of {@code oct(rmax, k)} in the campus frame: {@code |A|, |R| <= rmax + 1/2} and
     * {@code |A| + |R| <= k + 1}, the line through the outer corners of the octagon's stepped edge (the masterplan's
     * way of drawing an octagon since 0.10.0). The same in the world's axes, whichever way the nexus faces.
     */
    public static double[][] octagon(int rmax, int k) {
        double h = rmax + 0.5, s = k + 1, c = Math.min(h, s - h);
        return new double[][] { { h, -c }, { h, c }, { c, h }, { -c, h }, { -h, c }, { -h, -c }, { -c, -h },
            { c, -h } };
    }

    /** The outer disc's edge, {@code oct(97, 147)}. */
    public static List<Path> disc() {
        return DISC;
    }

    /** The outer promenade's outer and inner edges, {@code oct(63, 99)} and {@code oct(60, 96)}. */
    public static List<Path> promenade() {
        return PROMENADE;
    }

    /**
     * The four 井 streets ({@link CampusPlan#streetCells}): each street's two halves, one either side of the promenade
     * ring, as one line from the outer promenade down one edge (cross 17.5), round the stepped inner end where the
     * ring cuts it (its cross 18 cells start a row further out than its 19 and 20) and back up the other edge (cross
     * 20.5), open where it meets the outer promenade.
     */
    public static List<Path> streets() {
        return STREETS;
    }

    private static List<Path> streetPaths() {
        int from = CampusPlan.STREET_FROM, to = CampusPlan.STREET_TO, reach = CampusPlan.STREET_REACH;
        // the first row of each cross line outside the promenade ring oct(19, 26)
        int first18 = firstRow(from), first19 = firstRow(from + 1);
        if (firstRow(to) != first19) throw new IllegalStateException("the streets' inner ends changed");
        // (t along the street, c across it)
        double[][] tc = { { reach + 0.5, from - 0.5 }, { first18 - 0.5, from - 0.5 }, { first18 - 0.5, from + 0.5 },
            { first19 - 0.5, from + 0.5 }, { first19 - 0.5, to + 0.5 }, { reach + 0.5, to + 0.5 } };
        List<Path> out = new ArrayList<>();
        for (int sign = 1; sign >= -1; sign -= 2) for (int along = 1; along >= -1; along -= 2) {
            double[][] alongA = new double[tc.length][], alongR = new double[tc.length][];
            for (int i = 0; i < tc.length; i++) {
                alongA[i] = new double[] { along * tc[i][0], sign * tc[i][1] };
                alongR[i] = new double[] { sign * tc[i][1], along * tc[i][0] };
            }
            out.add(new Path(alongA, false));
            out.add(new Path(alongR, false));
        }
        return out;
    }

    /** The first row (radial, from 8) of a street's cross line {@code c} that lies outside the promenade ring. */
    private static int firstRow(int c) {
        for (int t = 8; t <= CampusPlan.STREET_REACH; t++)
            if (!CampusPlan.oct(t, c, CampusPlan.RING_R, CampusPlan.RING_K)) return t;
        throw new IllegalStateException("a street inside the promenade ring");
    }

    /**
     * The four avenues ({@link OuterRing#avenues}): the front one from the gatehouse's far side (A 39.5) out to the
     * outer disc's edge, widened to the outer gate ({@link OuterRing#inOuterGate}) over its last rows and closed across
     * the gate's outer edge; the other three as their two edges from the outer promenade's inner edge to the disc's.
     */
    public static List<Path> avenues() {
        return AVENUES;
    }

    private static List<Path> avenuePaths() {
        double w = OuterRing.AVENUE_HALF + 0.5, g = OuterRing.GATE_WIDE + 0.5, a0 = OuterRing.AVENUE_FRONT_FROM - 0.5,
            ga = OuterRing.GATE_FROM - 0.5, end = OuterRing.AVENUE_TO + 0.5;
        List<Path> out = new ArrayList<>();
        out.add(
            new Path(
                new double[][] { { a0, -w }, { ga, -w }, { ga, -g }, { end, -g }, { end, g }, { ga, g }, { ga, w },
                    { a0, w } },
                false));
        int[][] axes = { { 0, 1 }, { -1, 0 }, { 0, -1 } };
        double t0 = OuterRing.AVENUE_FROM - 0.5;
        for (int[] ax : axes) for (double s : new double[] { -w, w }) out.add(
            new Path(
                new double[][] { { t0 * ax[0] - s * ax[1], t0 * ax[1] + s * ax[0] },
                    { end * ax[0] - s * ax[1], end * ax[1] + s * ax[0] } },
                false));
        return out;
    }

    /** The outer gate's four pylons ({@link OuterRing#pylons}), each its cell's square. */
    public static List<Path> pylons() {
        return PYLONS;
    }

    private static double[][] square(double a, double r) {
        return new double[][] { { a - 0.5, r - 0.5 }, { a + 0.5, r - 0.5 }, { a + 0.5, r + 0.5 },
            { a - 0.5, r + 0.5 } };
    }

    /** An outer diagonal's envelope ({@link OuterRing#diagCells}), its octagon {@code oct(15, 20)} round its centre. */
    public static double[][] diagOutline(int site) {
        int[] c = OuterRing.diagCentre(site);
        double[][] o = octagon(OuterRing.DIAG_SIZE / 2, OuterRing.DIAG_K);
        for (double[] p : o) {
            p[0] += c[0];
            p[1] += c[1];
        }
        return o;
    }

    /** A reserved outer flanker's envelope ({@link OuterRing#flankCells}): its four corners' outline. */
    public static double[][] flankOutline(int site) {
        int[] ax = OuterRing.axis(site);
        int cross = OuterRing.flankSide(site) * OuterRing.FLANK_CROSS;
        double t0 = OuterRing.FLANK_FRONT - 0.5, t1 = OuterRing.FLANK_FRONT + OuterRing.FLANK_D - 0.5,
            s0 = cross - OuterRing.FLANK_W / 2 - 0.5, s1 = cross + (OuterRing.FLANK_W - 1) / 2 + 0.5;
        return new double[][] { local(ax, t0, s0), local(ax, t1, s0), local(ax, t1, s1), local(ax, t0, s1) };
    }

    /** The campus point {A, R} {@code t} along an axis and {@code s} across it (clockwise, as the sites count). */
    private static double[] local(int[] ax, double t, double s) {
        return new double[] { t * ax[0] - s * ax[1], t * ax[1] + s * ax[0] };
    }

    /**
     * Where an outer site's masterplan sign stands, {A, R, height over the floor}: an outer diagonal's on its allée
     * before its inner portal ({@link OuterRing#diagSign}), a flanker's on the outer promenade before its doors (over
     * its door marks' row, on the 井 street's axis); both outside every building that can rise there.
     *
     * @throws IllegalArgumentException for a site that is no outer site
     */
    public static double[] signAt(int site) {
        if (OuterRing.diagSite(site)) {
            int[] s = OuterRing.diagSign(site);
            return new double[] { s[0], s[1], SIGN_UP };
        }
        int[] ax = OuterRing.axis(site);
        double[] p = local(ax, FLANK_SIGN, OuterRing.flankSide(site) * OuterRing.FLANK_CROSS);
        return new double[] { p[0], p[1], SIGN_UP };
    }

    // ---- the split

    /**
     * The split with a neighbouring campus as the masterplan draws it, {x0, z0, x1, z1} in world block coordinates
     * (a column's middle at {@code x + 0.5}): the perpendicular bisector of the two nexus centres (cx, cz) and
     * (ox, oz), where it crosses both campuses' outer discs ({@link #octagon} of {@code oct(97, 147)} round each
     * centre's middle): only there do the two rings meet, as a column of one ring outside the other's stays with the
     * first ({@link CampusSplit}); null when it does not (the neighbour is too far to share any of it) or the centres
     * are the same. Both campuses draw the same line.
     */
    public static double[] splitLine(int cx, int cz, int ox, int oz) {
        if (cx == ox && cz == oz) return null;
        double px = cx + 0.5, pz = cz + 0.5, qx = ox + 0.5, qz = oz + 0.5;
        // the bisector, relative to this centre: m + s * d
        double mx = (qx - px) / 2, mz = (qz - pz) / 2, dx = -(qz - pz), dz = qx - px;
        double h = OuterRing.OUTER_R + 0.5, k = OuterRing.OUTER_K + 1;
        // the disc's eight sides as half-planes nx * x + nz * z <= c
        double[][] planes = { { 1, 0, h }, { -1, 0, h }, { 0, 1, h }, { 0, -1, h }, { 1, 1, k }, { 1, -1, k },
            { -1, 1, k }, { -1, -1, k } };
        double lo = Double.NEGATIVE_INFINITY, hi = Double.POSITIVE_INFINITY;
        // this disc's sides round 0, then the neighbour's round 2 * m
        for (int side = 0; side < 2; side++) for (double[] p : planes) {
            double dn = p[0] * dx + p[1] * dz, mn = p[0] * mx + p[1] * mz;
            double c = side == 0 ? p[2] : p[2] + 2 * mn;
            if (Math.abs(dn) < 1e-12) {
                if (mn > c) return null;
                continue;
            }
            double s = (c - mn) / dn;
            if (dn > 0) hi = Math.min(hi, s);
            else lo = Math.max(lo, s);
        }
        if (!(lo < hi)) return null;
        return new double[] { px + mx + lo * dx, pz + mz + lo * dz, px + mx + hi * dx, pz + mz + hi * dz };
    }

    /**
     * Every column of the outer establish's ground (the annulus and the outer floor, {@link BuildPlan#outer}'s
     * columns) of the campus centred at (cx, cz) facing (fx, fz) that its neighbours keep ({@link CampusSplit#ours}),
     * {x, z} each in the world: the part of the ring the job leaves to them ({@link BuildPlan#outerTrimmed} counts
     * them). Empty without neighbours.
     */
    public static List<int[]> givenUp(CampusPlan plan, int cx, int cz, int fx, int fz, List<int[]> neighbours) {
        List<int[]> out = new ArrayList<>();
        if (neighbours == null || neighbours.isEmpty()) return out;
        java.util.Set<Long> columns = new java.util.LinkedHashSet<>(OuterRing.annulus());
        columns.addAll(
            plan.outerFloor()
                .keySet());
        for (long k : columns) {
            int[] d = CampusPlan.toWorld(CampusPlan.keyA(k), CampusPlan.keyR(k), fx, fz);
            if (!CampusSplit.ours(cx + d[0], cz + d[1], cx, cz, neighbours))
                out.add(new int[] { cx + d[0], cz + d[1] });
        }
        return out;
    }

    /**
     * The outline of the ground the neighbours keep ({@link #givenUp}) as straight segments between block corners,
     * {x0, z0, x1, z1} each ({@link GhostCells#edge}): the outer establish's projection draws it, so a player sees
     * where the job stops and why. Empty without neighbours or when they keep nothing.
     */
    public static int[] trimOutline(CampusPlan plan, int cx, int cz, int fx, int fz, List<int[]> neighbours) {
        List<int[]> cols = givenUp(plan, cx, cz, fx, fz, neighbours);
        int[] xs = new int[cols.size()], zs = new int[cols.size()];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = cols.get(i)[0];
            zs[i] = cols.get(i)[1];
        }
        return GhostCells.edge(xs, zs);
    }

    /**
     * Whether an outer diagonal is left to the neighbours, the campus claiming none of its own:
     * {@link #siteTrimmed(int, int, int, int, int, int, List)}.
     */
    public static boolean siteTrimmed(int site, int cx, int cz, int fx, int fz, List<int[]> neighbours) {
        return siteTrimmed(site, cx, cz, fx, fz, 0, neighbours);
    }

    /**
     * Whether an outer diagonal is left to the neighbours (0.15.0): a neighbour holds part of its apron and allée for
     * good ({@link CampusSplit#siteLoss} is -1: its inner square, or an outer diagonal it claimed), so the campus never
     * offers a building there and the masterplan says it is left to them. A site the split only takes part of while
     * nobody claims it is not: the campus may still take it, after the free sites that share less ground with the
     * neighbours' outer diagonals ({@code CityOffer}). False for every other site and without neighbours;
     * {@code claims} are the campus's own (a bit per world quadrant), {@code neighbours} {x, z} or {x, z, claims}.
     */
    public static boolean siteTrimmed(int site, int cx, int cz, int fx, int fz, int claims, List<int[]> neighbours) {
        if (!OuterRing.diagSite(site) || neighbours == null || neighbours.isEmpty()) return false;
        return CampusSplit.siteLoss(CampusSplit.siteQuadrant(site, fx, fz), cx, cz, claims, neighbours) < 0;
    }

    /**
     * The neighbours a client's masterplan judges a campus's outer ring against (0.15.0), {x, z} or {x, z, claims}
     * each, sorted, each once: those the server judges its outer sites against ({@code split}, sent with the campus
     * whether or not the client has those nexuses loaded), then those its current job was planned with
     * ({@code jobList}), then the active campuses the client has loaded ({@code loaded}, centres {x, z}) within
     * {@link CampusSplit#REACH} of the centre (cx, cz), the campus itself left out. The first entry for a centre
     * wins, so a neighbour keeps the claims the server sent.
     */
    public static List<int[]> neighbours(List<int[]> split, List<int[]> jobList, List<int[]> loaded, int cx, int cz) {
        List<int[]> out = new ArrayList<>();
        for (List<int[]> l : java.util.Arrays.asList(split, jobList, loaded)) {
            if (l == null) continue;
            for (int[] o : l) {
                if (o == null || o.length < 2 || o[0] == cx && o[1] == cz) continue;
                if (Math.max(Math.abs(o[0] - cx), Math.abs(o[1] - cz)) > CampusSplit.REACH) continue;
                boolean known = false;
                for (int[] n : out) known |= n[0] == o[0] && n[1] == o[1];
                if (!known)
                    out.add(o.length >= 3 && o[2] != 0 ? new int[] { o[0], o[1], o[2] } : new int[] { o[0], o[1] });
            }
        }
        out.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        return out;
    }
}
