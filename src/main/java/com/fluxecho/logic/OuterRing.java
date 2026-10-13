package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The outer ring of a campus (0.15.0, CITY.md §2.3), ported from the site plan's generator {@code siteplan.py}
 * ({@code outer_prom}, {@code avenues}, {@code flankers}, {@code flank_aprons}, {@code outer_diagonals},
 * {@code outer_diag_aprons}; the four 井 streets are {@link CampusPlan#streetCells}): the ground the outer establish
 * job ({@code establish:outer}, {@link SiteFamily#OUTER_GROUND}) grades and paves once the nexus has grown to phase III
 * and its teams researched the outer ring, and the sites the families further out stand on.
 * <p>
 * The job grades the annulus {@code oct(97, 147)} less the inner disc {@code oct(64, 90)} (19,836 columns) and paves
 * the four 井 streets between the inner buildings, the outer promenade {@code oct(63, 99)} less {@code oct(60, 96)}
 * right behind the halls' backs, the four avenues on the axes (the front one continuing the gate road from the
 * gatehouse to the outer gate at A 91 to 97, the other three from the outer promenade to the disc's edge), the outer
 * gate with its four pylons, each pavilion's outer allée from its outer chamfer portal to the promenade, and a lit mark
 * on the promenade in front of every outer flanker's doors. The cells come from the generator exactly; the outer gate
 * is the front avenue widened to {@code |R| <= 5} over its seven rows, as the inner gate is widened where its pylons
 * stand, so the four pylons stand on its tiles.
 * <p>
 * Beyond the promenade stand the outer sites: the four outer diagonals (sites 11, 13, 15, 17 = O1, O3, O5, O7: the
 * octagon {@code oct(15, 20)} centred {@code (+-62, +-62)}, centrally planned like the pavilions, an apron of two and a
 * short allée from the promenade to the inner chamfer portal) and the eight outer flankers (sites 20 to 27: 27 across,
 * 31 deep from radial 64, either side of an avenue at cross {@code +-19}, closing the 井 streets with their doors;
 * reserved, only their envelopes are drawn).
 * <p>
 * Everything is in the campus frame (A ahead, R to the right) and the same for every seed; the paving's tiles are the
 * seeded {@link CampusPlan#outerFloor}.
 */
public final class OuterRing {

    /** The outer disc {@code oct(OUTER_R, OUTER_K)}; the outer establish grades it less the inner disc. */
    public static final int OUTER_R = 97, OUTER_K = 147;
    /** The outer promenade: {@code oct(PROM_R, PROM_K)} less {@code oct(PROM_IN_R, PROM_IN_K)}. */
    public static final int PROM_R = 63, PROM_K = 99, PROM_IN_R = 60, PROM_IN_K = 96;
    /**
     * The four avenues, {@code |cross| <= AVENUE_HALF} on the axes: the front one from the gatehouse
     * ({@code AVENUE_FRONT_FROM}), the other three from the outer promenade ({@code AVENUE_FROM}), all out to the outer
     * disc's edge ({@code AVENUE_TO}).
     */
    public static final int AVENUE_HALF = 3, AVENUE_FRONT_FROM = 40, AVENUE_FROM = 61, AVENUE_TO = 97;
    /** The outer gate on the front avenue: A {@code GATE_FROM..GATE_TO}, {@code |R| <= GATE_WIDE}. */
    public static final int GATE_FROM = 91, GATE_TO = 97, GATE_WIDE = 5;
    /**
     * A pavilion's outer allée: three cells wide on its diagonal from its outer chamfer portal ({@code |a| + |r|} =
     * {@code 2 * 36 + 16 + 1}) to the outer promenade's diagonal face ({@code PROM_IN_K}), as its inner allée runs from
     * the promenade ring to its inner portal.
     */
    public static final int ALLEE_FROM = 2 * CampusPlan.DIAGONAL_CENTRE + CampusPlan.DIAGONAL_K + 1,
        ALLEE_TO = PROM_IN_K;
    /** The outer flankers: front row at radial {@code FLANK_FRONT}, 27 across, 31 deep, centred {@code +-19} across. */
    public static final int FLANK_FRONT = 64, FLANK_W = 27, FLANK_D = 31, FLANK_CROSS = 19;
    /** The flankers' sites, 20 to 27: O0+, O0-, O2+, O2-, O4+, O4-, O6+, O6- (an axis site and a side each). */
    public static final int FLANK_FIRST = 20, FLANK_LAST = 27;
    /**
     * The lit door marks: the promenade's row in front of a flanker's front ({@code DOOR_MARK_ROW}), across its two
     * doors and the trumeau between them ({@code |cross - 19| <= DOOR_MARK_HALF}).
     */
    public static final int DOOR_MARK_ROW = FLANK_FRONT - 1, DOOR_MARK_HALF = 3;
    /**
     * The outer diagonals (sites 11, 13, 15, 17): the octagon {@code oct(DIAG_SIZE / 2, DIAG_K)} centred
     * {@code DIAG_CENTRE} out on both axes, an apron of {@code DIAG_APRON} round it.
     */
    public static final int DIAG_CENTRE = 62, DIAG_SIZE = 31, DIAG_K = 20, DIAG_APRON = 2;
    /** The outer diagonal sites. */
    public static final int[] DIAG_SITES = { 11, 13, 15, 17 };
    /**
     * An outer diagonal's allée: three cells wide on its diagonal from the outer promenade's diagonal face
     * ({@code |a| + |r|} = {@code PROM_K + 1}) to its inner chamfer portal ({@code 2 * 62 - 20 - 1}), the last two rows
     * on its apron.
     */
    public static final int DIAG_ALLEE_FROM = PROM_K + 1, DIAG_ALLEE_TO = 2 * DIAG_CENTRE - DIAG_K - 1;
    /**
     * Where on its diagonal ({@code |a| + |r|}) an outer diagonal's masterplan sign stands: on its allée's middle line
     * just past the outer promenade, outside its apron, in front of its inner portal.
     */
    public static final int DIAG_SIGN = DIAG_ALLEE_FROM;
    /**
     * How far along a diagonal an outer diagonal's inner portal lies from the nexus centre: the face of its octagon's
     * chamfer, {@code |a| + |r|} = 2 * 62 - 20, half a cell out, along the unit diagonal (about 73.2 blocks).
     */
    public static final double DIAG_DOOR = (2.0 * DIAG_CENTRE - DIAG_K - 0.5) / Math.sqrt(2);

    /** The unit axis of every site in (A, R), as {@link CampusPlan#siteAxis} has them. */
    private static final int[][] AXES = { { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 }, { -1, 0 }, { -1, -1 }, { 0, -1 },
        { 1, -1 } };
    private static final int[][] PYLONS = { { 91, 5 }, { 91, -5 }, { 96, 5 }, { 96, -5 } };

    private OuterRing() {}

    // ---- the ground the outer establish lays

    /** Whether (a, r) lies in the outer disc {@code oct(97, 147)}. */
    public static boolean inOuterDisc(int a, int r) {
        return CampusPlan.oct(a, r, OUTER_R, OUTER_K);
    }

    /** Whether (a, r) lies in the annulus the outer establish grades: the outer disc less {@code oct(64, 90)}. */
    public static boolean inAnnulus(int a, int r) {
        return inOuterDisc(a, r) && !CampusPlan.oct(a, r, CampusPlan.GRADE_R, CampusPlan.GRADE_K);
    }

    /** The annulus the outer establish grades (19,836 cells), the same for every campus. */
    public static Set<Long> annulus() {
        return Cells.ANNULUS;
    }

    /** Whether (a, r) lies on the outer promenade, {@code oct(63, 99)} less {@code oct(60, 96)}. */
    public static boolean inPromenade(int a, int r) {
        return CampusPlan.oct(a, r, PROM_R, PROM_K) && !CampusPlan.oct(a, r, PROM_IN_R, PROM_IN_K);
    }

    /** The outer promenade's cells (1,176). */
    public static Set<Long> promenade() {
        Set<Long> out = new LinkedHashSet<>();
        for (int a = -PROM_R; a <= PROM_R; a++)
            for (int r = -PROM_R; r <= PROM_R; r++) if (inPromenade(a, r)) out.add(CampusPlan.key(a, r));
        return out;
    }

    /**
     * The four avenues (1,183 cells): {@code |cross| <= 3} on the front axis from A 40 (the gatehouse's far side) to
     * A 97, and on the other three axes from radial 61 (the outer promenade) to radial 97, as {@code avenues()} lists
     * them.
     */
    public static Set<Long> avenues() {
        Set<Long> out = new LinkedHashSet<>();
        for (int a = AVENUE_FRONT_FROM; a <= AVENUE_TO; a++)
            for (int r = -AVENUE_HALF; r <= AVENUE_HALF; r++) out.add(CampusPlan.key(a, r));
        for (int site : new int[] { 2, 4, 6 }) {
            int[] ax = AXES[site];
            for (int t = AVENUE_FROM; t <= AVENUE_TO; t++)
                for (int s = -AVENUE_HALF; s <= AVENUE_HALF; s++) out.add(siteKey(ax, t, s));
        }
        return out;
    }

    /**
     * Where (a, r) lies on an avenue, {t, s} along and across its axis, or null: the front one from A 40, the others
     * from radial 61, all to 97, {@code |s| <= 3}.
     */
    public static int[] onAvenue(int a, int r) {
        for (int site = 0; site < AXES.length; site += 2) {
            int[] ax = AXES[site];
            int t = a * ax[0] + r * ax[1], s = -a * ax[1] + r * ax[0];
            int from = site == 0 ? AVENUE_FRONT_FROM : AVENUE_FROM;
            if (t >= from && t <= AVENUE_TO && Math.abs(s) <= AVENUE_HALF) return new int[] { t, s };
        }
        return null;
    }

    /** Whether (a, r) lies in the outer gate: A 91 to 97, {@code |R| <= 5} (the front avenue widened there). */
    public static boolean inOuterGate(int a, int r) {
        return a >= GATE_FROM && a <= GATE_TO && Math.abs(r) <= GATE_WIDE;
    }

    /** The outer gate's cells (77: the front avenue's 49 and the widening's 28). */
    public static Set<Long> outerGate() {
        Set<Long> out = new LinkedHashSet<>();
        for (int a = GATE_FROM; a <= GATE_TO; a++)
            for (int r = -GATE_WIDE; r <= GATE_WIDE; r++) out.add(CampusPlan.key(a, r));
        return out;
    }

    /** The cells {a, r} of the outer gate's four pylons: (91, +-5) and (96, +-5). Each stands on a gate tile. */
    public static List<int[]> pylons() {
        List<int[]> out = new ArrayList<>();
        for (int[] p : PYLONS) out.add(p.clone());
        return out;
    }

    /**
     * A pavilion's outer allée (12 cells): three cells wide ({@code |a| - |r|} in -1..1) on its diagonal, from its
     * outer chamfer portal ({@code |a| + |r|} 89) to the outer promenade (96); its first three cells lie on the
     * pavilion's apron, which the pavilion's own floor takes once it stands.
     *
     * @throws IllegalArgumentException for a site that is not an inner diagonal (1, 3, 5, 7)
     */
    public static Set<Long> pavilionAllee(int site) {
        return diagonalBand(innerDiagonal(site), ALLEE_FROM, ALLEE_TO);
    }

    /** The four pavilions' outer allées (48 cells). */
    public static Set<Long> pavilionAllees() {
        Set<Long> out = new LinkedHashSet<>();
        for (int site : CampusPlan.PAVILION_SITES) out.addAll(pavilionAllee(site));
        return out;
    }

    /**
     * Whether (a, r) is a lit door mark: the outer promenade's row in front of an outer flanker (radial 63), across
     * its doors and trumeau ({@code |cross - 19| <= 3}).
     */
    public static boolean doorMark(int a, int r) {
        for (int site = 0; site < AXES.length; site += 2) {
            int[] ax = AXES[site];
            int t = a * ax[0] + r * ax[1], s = -a * ax[1] + r * ax[0];
            if (t == DOOR_MARK_ROW && Math.abs(Math.abs(s) - FLANK_CROSS) <= DOOR_MARK_HALF) return true;
        }
        return false;
    }

    /** The door marks' cells (56: seven in front of each of the eight flankers), all on the outer promenade. */
    public static Set<Long> doorMarks() {
        Set<Long> out = new LinkedHashSet<>();
        for (long k : promenade()) if (doorMark(CampusPlan.keyA(k), CampusPlan.keyR(k))) out.add(k);
        return out;
    }

    /**
     * Every cell the outer establish paves (3,579): the 井 streets, the outer promenade, the avenues, the outer gate and
     * the pavilions' outer allées, before anything is left out for what stands on them (the plan leaves out the cells
     * of standing modules and of neighbouring campuses).
     */
    public static Set<Long> paved() {
        return Cells.PAVED;
    }

    // ---- the outer diagonals (sites 11, 13, 15, 17)

    /** Whether the site is an outer diagonal (O1, O3, O5, O7). */
    public static boolean diagSite(int site) {
        return SiteFamily.of(site) == SiteFamily.OUTER_DIAG;
    }

    /**
     * The unit axis {da, dr} of an outer site: an outer diagonal's is the inner diagonal's ten below it (11 beyond 1),
     * an outer flanker's its avenue's.
     *
     * @throws IllegalArgumentException for a site of neither family
     */
    public static int[] axis(int site) {
        if (diagSite(site)) return AXES[site - 10].clone();
        if (SiteFamily.of(site) == SiteFamily.OUTER_FLANK) return AXES[flankAxisSite(site)].clone();
        throw new IllegalArgumentException("not an outer site: " + site);
    }

    /**
     * The centre {a, r} of an outer diagonal's octagon, (+-62, +-62).
     *
     * @throws IllegalArgumentException for a site that is not an outer diagonal
     */
    public static int[] diagCentre(int site) {
        if (!diagSite(site)) throw new IllegalArgumentException("not an outer diagonal: " + site);
        int[] ax = AXES[site - 10];
        return new int[] { ax[0] * DIAG_CENTRE, ax[1] * DIAG_CENTRE };
    }

    /** Whether (a, r) lies in an outer diagonal's envelope, {@code oct(15, 20)} round its centre. */
    public static boolean inDiag(int site, int a, int r) {
        if (!diagSite(site)) return false;
        int[] c = diagCentre(site);
        return CampusPlan.oct(a - c[0], r - c[1], DIAG_SIZE / 2, DIAG_K);
    }

    /** An outer diagonal's envelope (741 cells). */
    public static Set<Long> diagCells(int site) {
        return octCells(diagCentre(site), DIAG_SIZE / 2, DIAG_K);
    }

    /** An outer diagonal's apron: {@code oct(17, 22)} round its centre less the envelope (172 cells). */
    public static Set<Long> diagApron(int site) {
        Set<Long> out = octCells(diagCentre(site), DIAG_SIZE / 2 + DIAG_APRON, DIAG_K + DIAG_APRON);
        out.removeAll(diagCells(site));
        return out;
    }

    /**
     * An outer diagonal's allée (6 cells): three cells wide on its diagonal from the outer promenade's diagonal face
     * ({@code |a| + |r|} 100) to its inner chamfer portal (103); the last three lie on its apron.
     */
    public static Set<Long> diagAllee(int site) {
        if (!diagSite(site)) throw new IllegalArgumentException("not an outer diagonal: " + site);
        return diagonalBand(AXES[site - 10], DIAG_ALLEE_FROM, DIAG_ALLEE_TO);
    }

    /**
     * Every cell an outer diagonal's job takes on its site (916 cells): its apron {@code oct(17, 22)} with the
     * envelope, its clear margin and its seeded annex inside, and its allée. What the campus jobs keep out of and what
     * only the module's own jobs take down once it stands ({@link CampusPlan#rowBoxes} makes boxes of it).
     */
    public static Set<Long> diagSiteCells(int site) {
        Set<Long> out = octCells(diagCentre(site), DIAG_SIZE / 2 + DIAG_APRON, DIAG_K + DIAG_APRON);
        out.addAll(diagAllee(site));
        return out;
    }

    /**
     * The way an outer diagonal's blueprint front faces, {da, dr}: along A toward the nexus, as a pavilion's does
     * (-A on the sites ahead of it, 11 and 17; +A on those behind, 13 and 15).
     */
    public static int[] diagFront(int site) {
        int[] c = diagCentre(site);
        return new int[] { c[0] > 0 ? -1 : 1, 0 };
    }

    /** The outer diagonal whose centre lies within 3 of (a, r) on both axes, or -1. */
    public static int diagSiteAt(int a, int r) {
        for (int site : DIAG_SITES) {
            int[] c = diagCentre(site);
            if (Math.abs(a - c[0]) <= 3 && Math.abs(r - c[1]) <= 3) return site;
        }
        return -1;
    }

    /**
     * Where the masterplan's sign of an outer diagonal stands, {a, r}: on its allée's middle line at
     * {@link #DIAG_SIGN} (+-50, +-50), just past the outer promenade and outside its apron, in front of its inner
     * portal, so the building raised there never shuts it inside its walls.
     */
    public static int[] diagSign(int site) {
        if (!diagSite(site)) throw new IllegalArgumentException("not an outer diagonal: " + site);
        int[] ax = axis(site);
        int d = DIAG_SIGN / 2;
        return new int[] { ax[0] * d, ax[1] * d };
    }

    // ---- the outer flankers (sites 20 to 27, reserved)

    /** The cardinal site (0, 2, 4, 6) whose avenue an outer flanker stands beside. */
    public static int flankAxisSite(int site) {
        if (SiteFamily.of(site) != SiteFamily.OUTER_FLANK)
            throw new IllegalArgumentException("not an outer flanker: " + site);
        return (site - FLANK_FIRST) / 2 * 2;
    }

    /** The side of the avenue an outer flanker stands on: +1 for the + sites (20, 22, 24, 26), -1 for the others. */
    public static int flankSide(int site) {
        flankAxisSite(site);
        return (site - FLANK_FIRST) % 2 == 0 ? 1 : -1;
    }

    /** An outer flanker's envelope (837 cells): 27 across centred 19 off its avenue, 31 deep from radial 64. */
    public static Set<Long> flankCells(int site) {
        int[] ax = AXES[flankAxisSite(site)];
        int cross = flankSide(site) * FLANK_CROSS;
        Set<Long> out = new LinkedHashSet<>();
        for (int t = FLANK_FRONT; t < FLANK_FRONT + FLANK_D; t++)
            for (int s = cross - FLANK_W / 2; s <= cross + (FLANK_W - 1) / 2; s++) out.add(siteKey(ax, t, s));
        return out;
    }

    /** Whether (a, r) lies in an outer flanker's envelope. */
    public static boolean inFlank(int site, int a, int r) {
        int[] ax = AXES[flankAxisSite(site)];
        int cross = flankSide(site) * FLANK_CROSS;
        int t = a * ax[0] + r * ax[1], s = -a * ax[1] + r * ax[0];
        return t >= FLANK_FRONT && t < FLANK_FRONT + FLANK_D
            && s >= cross - FLANK_W / 2
            && s <= cross + (FLANK_W - 1) / 2;
    }

    /**
     * An outer flanker's apron as the generator reserves it ({@code flank_aprons}): two cells round the envelope, the
     * back corners cut where the overshoot past the back row and past the side together exceeds 3.
     */
    public static Set<Long> flankApron(int site) {
        int[] ax = AXES[flankAxisSite(site)];
        int cross = flankSide(site) * FLANK_CROSS;
        int back = FLANK_FRONT + FLANK_D - 1, lo = cross - FLANK_W / 2, hi = cross + (FLANK_W - 1) / 2;
        Set<Long> foot = flankCells(site), out = new LinkedHashSet<>();
        for (int t = FLANK_FRONT - 2; t <= back + 2; t++) for (int s = lo - 2; s <= hi + 2; s++) {
            long k = siteKey(ax, t, s);
            if (foot.contains(k)) continue;
            if ((t - back) + (s < lo ? lo - s : s - hi) > 3) continue;
            out.add(k);
        }
        return out;
    }

    // ---- helpers

    /** The inner diagonal's axis of a pavilion site. */
    private static int[] innerDiagonal(int site) {
        if (SiteFamily.of(site) != SiteFamily.PAVILION) throw new IllegalArgumentException("not a pavilion: " + site);
        return AXES[site];
    }

    /** The three-cell band on a diagonal from {@code |a| + |r|} = {@code from} to {@code to}, as the allées are. */
    private static Set<Long> diagonalBand(int[] ax, int from, int to) {
        Set<Long> out = new LinkedHashSet<>();
        for (int t = from; t <= to; t++) for (int d = -1; d <= 1; d++) {
            if ((t + d) % 2 != 0) continue;
            int a = (t + d) / 2, r = (t - d) / 2;
            out.add(CampusPlan.key(ax[0] * a, ax[1] * r));
        }
        return out;
    }

    /** The key of the cell {@code t} out along a cardinal axis and {@code s} across it (clockwise). */
    private static long siteKey(int[] ax, int t, int s) {
        return CampusPlan.key(t * ax[0] - s * ax[1], t * ax[1] + s * ax[0]);
    }

    private static Set<Long> octCells(int[] c, int half, int k) {
        Set<Long> out = new LinkedHashSet<>();
        for (int du = -half; du <= half; du++) for (int dv = -half; dv <= half; dv++) {
            if (Math.abs(du) + Math.abs(dv) <= k) out.add(CampusPlan.key(c[0] + du, c[1] + dv));
        }
        return out;
    }

    /** The cell sets every campus shares (they do not depend on the seed). */
    private static final class Cells {

        static final Set<Long> ANNULUS, PAVED;

        static {
            Set<Long> s = new LinkedHashSet<>();
            for (int a = -OUTER_R; a <= OUTER_R; a++)
                for (int r = -OUTER_R; r <= OUTER_R; r++) if (inAnnulus(a, r)) s.add(CampusPlan.key(a, r));
            ANNULUS = Collections.unmodifiableSet(s);
            Set<Long> p = new LinkedHashSet<>(CampusPlan.streetCells());
            p.addAll(promenade());
            p.addAll(avenues());
            p.addAll(outerGate());
            p.addAll(pavilionAllees());
            PAVED = Collections.unmodifiableSet(p);
        }
    }
}
