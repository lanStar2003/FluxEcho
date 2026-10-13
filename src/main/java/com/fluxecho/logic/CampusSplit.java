package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * How two campuses share the ground between them (0.15.0, M12 §3, replacing CITY.md §2.9's refusal
 * {@code build.outer_too_close}): a campus placed at the old spacing (144) has its inner disc to itself, but its outer
 * disc ({@code oct(97, 147)}) may reach a neighbour's. Instead of refusing the outer establish, each campus grades and
 * paves only the ground that is its own:
 * <ul>
 * <li>a column within {@link CampusPlan#GRADE_R} of a campus centre (Chebyshev, the square every campus guarded for
 * its inner disc since 0.10.0) belongs to that campus, and to none at all where two such squares meet;</li>
 * <li>any other column goes to the campus that ranks first for it: a campus whose outer diagonal it has taken (a
 * building stands there or a job is queued for one: its <em>claim</em>) ranks before one whose outer diagonal holds
 * the column unclaimed, which ranks before every campus whose outer diagonal does not hold it, and a campus whose
 * outer disc ({@link OuterRing#inOuterDisc}) holds the column ranks before one whose disc does not; two campuses
 * whose outer diagonals both hold it rank by their centres (first by x, then by z), and among the rest of the same
 * rank the centre nearest wins (squared Euclidean distance, a Voronoi split), a tie on the bisector going to the
 * campus first by x, then by z.</li>
 * </ul>
 * So the split bends round the outer diagonals ({@link #siteAt}): a site's apron and allée (out to 79 from its centre)
 * never reach a neighbour's inner square at the old spacing of 144 (it starts at 80), and the split gives a whole site
 * to one side or the other instead of cutting every site that crosses the bisector, which left a campus between two
 * neighbours at the old spacing no outer diagonal at all. Where two campuses' sites overlap (centres 144 to 158
 * apart) the first by its centre keeps its site whole until the other claims its own: first come, first served.
 * <p>
 * Every campus answers the question the same way from the same centres and claims, so two campuses never both claim a
 * column and no column outside the inner squares is left to neither: the ranking is one strict order of the campuses
 * for every column, so the split is symmetric. Pure arithmetic over world columns, the same on the server and the
 * client (the outer establish's projection, the masterplan). A neighbour is given as {x, z} or, with its claims,
 * {x, z, claims} (a bit per world quadrant, {@link #quadrant}).
 */
public final class CampusSplit {

    /** How far apart (Chebyshev) two campus centres must be for their inner discs never to meet: 2 * 64 + 16. */
    public static final int INNER_SPACING = 2 * CampusPlan.GRADE_R + 16;
    /** How far apart two campus centres must be for their outer discs never to meet: 2 * 97 + 16 (CITY.md §2.9). */
    public static final int OUTER_SPACING = 2 * OuterRing.OUTER_R + 16;
    /**
     * The default spacing of new campuses since 0.15.0 ({@code buildMinSpacing}): 2 * 148 + 16, room for both third
     * rings (CITY.md §2.9). A core placed nearer is refused: that is placement, not a building.
     */
    public static final int DEFAULT_SPACING = 2 * 148 + 16;
    /** The spacing every campus was placed at before 0.15.0, the old default of {@code buildMinSpacing}. */
    public static final int OLD_SPACING = INNER_SPACING;
    /**
     * How far (Chebyshev, between centres) another campus may take part of the outer ring's ground: twice the farthest
     * a column of the outer disc lies from its centre (about 109.1, at {@code (97, 50)}), rounded up.
     */
    public static final int REACH = 220;

    private CampusSplit() {}

    /**
     * Whether the column (x, z) lies in the square a campus centred at (cx, cz) guards for its inner disc: Chebyshev
     * within {@link CampusPlan#GRADE_R}.
     */
    public static boolean inInner(int x, int z, int cx, int cz) {
        return Math.max(Math.abs(x - cx), Math.abs(z - cz)) <= CampusPlan.GRADE_R;
    }

    /**
     * Whether a campus centred at (cx, cz) wins the column (x, z) from one centred at (ox, oz) by distance alone: it is
     * nearer (squared Euclidean), or as near and its centre comes first by x, then by z. Exactly one of two different
     * centres wins every column.
     */
    public static boolean nearer(int x, int z, int cx, int cz, int ox, int oz) {
        long dc = sq(x - cx) + sq(z - cz), dn = sq(x - ox) + sq(z - oz);
        if (dc != dn) return dc < dn;
        return first(cx, cz, ox, oz);
    }

    /** Whether the centre (cx, cz) comes before (ox, oz): first by x, then by z. */
    public static boolean first(int cx, int cz, int ox, int oz) {
        return cx != ox ? cx < ox : cz < oz;
    }

    // ---- the outer diagonals in the world

    /**
     * The world quadrant of an offset from a campus centre: bit 0 set west of it ({@code dx < 0}), bit 1 north of it
     * ({@code dz < 0}). The outer diagonals stand one in each whichever way the campus faces, so the outer diagonals
     * a campus claims are a mask of four bits.
     */
    public static int quadrant(int dx, int dz) {
        return (dx < 0 ? 1 : 0) | (dz < 0 ? 2 : 0);
    }

    /**
     * The world quadrant ({@link #quadrant}) of an outer diagonal (11, 13, 15, 17) of a campus facing (fx, fz).
     *
     * @throws IllegalArgumentException for a site that is not an outer diagonal
     */
    public static int siteQuadrant(int site, int fx, int fz) {
        int[] c = OuterRing.diagCentre(site);
        int[] d = CampusPlan.toWorld(c[0], c[1], fx, fz);
        return quadrant(d[0], d[1]);
    }

    /**
     * The outer diagonal of a campus centred at (cx, cz) that holds the column (x, z), as its world quadrant, or -1:
     * the site's apron {@code oct(17, 22)} round (+-62, +-62) with the building's envelope inside, and its allée
     * ({@link OuterRing#diagSiteCells}, which lies the same in the world whichever way the campus faces).
     */
    public static int siteAt(int x, int z, int cx, int cz) {
        int dx = x - cx, dz = z - cz;
        if (dx == 0 || dz == 0) return -1;
        return onSite(Math.abs(dx), Math.abs(dz)) ? quadrant(dx, dz) : -1;
    }

    /** Whether the offset (u, v) out from a campus centre (both positive) lies on its outer diagonal there. */
    private static boolean onSite(int u, int v) {
        int du = Math.abs(u - OuterRing.DIAG_CENTRE), dv = Math.abs(v - OuterRing.DIAG_CENTRE);
        int half = OuterRing.DIAG_SIZE / 2 + OuterRing.DIAG_APRON, k = OuterRing.DIAG_K + OuterRing.DIAG_APRON;
        return du <= half && dv <= half && du + dv <= k
            || u + v >= OuterRing.DIAG_ALLEE_FROM && u + v <= OuterRing.DIAG_ALLEE_TO && Math.abs(u - v) <= 1;
    }

    /**
     * The world columns {x, z} of the outer diagonal in quadrant {@code q} of a campus centred at (cx, cz) (916, the
     * cells {@link #siteAt} finds there).
     */
    public static List<int[]> siteCells(int cx, int cz, int q) {
        int sx = (q & 1) != 0 ? -1 : 1, sz = (q & 2) != 0 ? -1 : 1;
        int lo = OuterRing.DIAG_CENTRE - OuterRing.DIAG_SIZE / 2 - OuterRing.DIAG_APRON;
        List<int[]> out = new ArrayList<>();
        for (int u = 1; u <= OuterRing.DIAG_ALLEE_TO; u++) for (int v = 1; v <= OuterRing.DIAG_ALLEE_TO; v++) {
            if (u + v < Math.min(2 * lo, OuterRing.DIAG_ALLEE_FROM) || !onSite(u, v)) continue;
            out.add(new int[] { cx + sx * u, cz + sz * v });
        }
        return out;
    }

    /**
     * The world boxes {x0, z0, x1, z1} of the outer diagonal in quadrant {@code q} of a campus centred at (cx, cz),
     * one per run of a row ({@link CampusPlan#rowBoxes}): they hold its {@link #siteCells} exactly.
     */
    public static List<int[]> siteBoxes(int cx, int cz, int q) {
        java.util.Set<Long> cells = new java.util.LinkedHashSet<>();
        for (int[] c : siteCells(cx, cz, q)) cells.add(CampusPlan.key(c[0], c[1]));
        return CampusPlan.rowBoxes(cells);
    }

    /**
     * The keep-out boxes {x0, z0, x1, z1} of a module whose foundation is centred at (x, z) and that stands on an outer
     * diagonal of no site the asking campus recorded (0.15.0, another campus's building): the whole site of the
     * campus among {@code centres} (each {x, z, ...}) whose outer diagonal is centred within 3 of it
     * ({@link #siteBoxes}: its apron, annex and allée, as its own campus keeps it clear); failing that, the apron
     * {@code oct(17, 22)} round the module's centre, row by row.
     */
    public static List<int[]> strayDiagBoxes(int x, int z, List<int[]> centres) {
        if (centres != null) for (int[] o : centres) {
            if (o == null || o.length < 2) continue;
            int dx = x - o[0], dz = z - o[1];
            if (Math.abs(Math.abs(dx) - OuterRing.DIAG_CENTRE) > 3
                || Math.abs(Math.abs(dz) - OuterRing.DIAG_CENTRE) > 3) continue;
            return siteBoxes(o[0], o[1], quadrant(dx, dz));
        }
        int half = OuterRing.DIAG_SIZE / 2 + OuterRing.DIAG_APRON, k = OuterRing.DIAG_K + OuterRing.DIAG_APRON;
        java.util.Set<Long> cells = new java.util.LinkedHashSet<>();
        for (int du = -half; du <= half; du++) for (int dv = -half; dv <= half; dv++)
            if (Math.abs(du) + Math.abs(dv) <= k) cells.add(CampusPlan.key(x + du, z + dv));
        return CampusPlan.rowBoxes(cells);
    }

    /** The claims of a neighbour given as {x, z} (none) or {x, z, claims}. */
    public static int claims(int[] o) {
        return o != null && o.length >= 3 ? o[2] & 15 : 0;
    }

    /**
     * How a campus ranks for a column outside the inner squares: 0 when the column lies on an outer diagonal of its
     * that it claims, 1 on one it does not claim, 2 elsewhere in its outer disc, 3 outside it. The lower rank wins, so
     * a column of one campus's ring that is nearer to a neighbour but lies outside the neighbour's ring stays with the
     * campus that can grade it: two rings offset at an angle leave no strip between them that neither lays.
     */
    static int rank(int x, int z, int cx, int cz, int claims) {
        int q = siteAt(x, z, cx, cz);
        if (q < 0) return OuterRing.inOuterDisc(x - cx, z - cz) ? 2 : 3;
        return (claims >> q & 1) != 0 ? 0 : 1;
    }

    /**
     * Whether a campus centred at (cx, cz) with its claims wins the column (x, z) from one centred at (ox, oz) with
     * its own, outside both inner squares: the lower {@link #rank}; two outer diagonals by their centres
     * ({@link #first}); else by distance ({@link #nearer}). Exactly one of two different centres wins every column,
     * and the order is the same whichever pair is asked, so among many campuses exactly one wins.
     */
    public static boolean beats(int x, int z, int cx, int cz, int cClaims, int ox, int oz, int oClaims) {
        int rc = rank(x, z, cx, cz, cClaims), ro = rank(x, z, ox, oz, oClaims);
        if (rc != ro) return rc < ro;
        return rc >= 2 ? nearer(x, z, cx, cz, ox, oz) : first(cx, cz, ox, oz);
    }

    /**
     * Whether the column (x, z) belongs to the campus centred at (cx, cz) among {@code others} (their centres {x, z},
     * or {x, z, claims}; this campus's own centre among them is ignored), the campus claiming none of its outer
     * diagonals: {@link #ours(int, int, int, int, int, List)}.
     */
    public static boolean ours(int x, int z, int cx, int cz, List<int[]> others) {
        return ours(x, z, cx, cz, 0, others);
    }

    /**
     * Whether the column (x, z) belongs to the campus centred at (cx, cz), which claims the outer diagonals of
     * {@code claims}, among {@code others}: it lies in no other campus's inner square, and either in its own or the
     * campus wins it from every other ({@link #beats}). With no others every column is its own.
     */
    public static boolean ours(int x, int z, int cx, int cz, int claims, List<int[]> others) {
        if (others == null || others.isEmpty()) return true;
        for (int[] o : others) if (!self(o, cx, cz) && inInner(x, z, o[0], o[1])) return false;
        if (inInner(x, z, cx, cz)) return true;
        for (int[] o : others)
            if (!self(o, cx, cz) && !beats(x, z, cx, cz, claims, o[0], o[1], claims(o))) return false;
        return true;
    }

    /**
     * Which campus the column (x, z) belongs to as {@link #ours} sees it from the campus at (cx, cz): -1 for this
     * one, the index in {@code others} of the one it belongs to, or -2 for none (inner squares meet there).
     */
    public static int owner(int x, int z, int cx, int cz, int claims, List<int[]> others) {
        if (others == null || others.isEmpty()) return -1;
        int square = -1;
        for (int i = 0; i < others.size(); i++) {
            int[] o = others.get(i);
            if (self(o, cx, cz) || !inInner(x, z, o[0], o[1])) continue;
            if (square >= 0 || inInner(x, z, cx, cz)) return -2;
            square = i;
        }
        if (square >= 0) return square;
        if (inInner(x, z, cx, cz)) return -1;
        int best = -1, bx = cx, bz = cz, bc = claims;
        for (int i = 0; i < others.size(); i++) {
            int[] o = others.get(i);
            if (self(o, cx, cz) || !beats(x, z, o[0], o[1], claims(o), bx, bz, bc)) continue;
            best = i;
            bx = o[0];
            bz = o[1];
            bc = claims(o);
        }
        return best;
    }

    /**
     * Whether the campus centred at (ox, oz), whose ground reaches {@code radius} (Chebyshev) round it, owns the column
     * (x, z) against the asking campus centred at (cx, cz), neither claiming an outer diagonal:
     * {@link #owns(int, int, int, int, int, int, int, int, int)}.
     */
    public static boolean owns(int x, int z, int ox, int oz, int radius, int cx, int cz) {
        return owns(x, z, ox, oz, radius, 0, cx, cz, 0);
    }

    /**
     * Whether the campus centred at (ox, oz), whose ground reaches {@code radius} (Chebyshev) round it and which claims
     * the outer diagonals of {@code oClaims}, owns the column (x, z) against the asking campus centred at (cx, cz),
     * which claims those of {@code cClaims}: its inner square always (as before 0.15.0, whatever asks), and the rest of
     * its reach only where the split gives the column to it ({@link #beats} between the two). A campus that has not
     * laid an outer ring ({@code radius} at most {@link CampusPlan#GRADE_R}) owns only its square.
     */
    public static boolean owns(int x, int z, int ox, int oz, int radius, int oClaims, int cx, int cz, int cClaims) {
        if (inInner(x, z, ox, oz)) return true;
        if (Math.max(Math.abs(x - ox), Math.abs(z - oz)) > radius) return false;
        if (inInner(x, z, cx, cz)) return false;
        return beats(x, z, ox, oz, oClaims, cx, cz, cClaims);
    }

    /**
     * How the outer diagonal in quadrant {@code q} of the campus centred at (cx, cz), which claims {@code claims},
     * stands against {@code others} (0.15.0): 0 when every cell of it is the campus's own (the site is whole); else
     * the number of its cells the split gives to the others while the campus may still take it (no cell of it lies in
     * another campus's inner square or on an outer diagonal another campus claims, so its own claim would win them:
     * first come, first served); -1 when it may not.
     */
    public static int siteLoss(int q, int cx, int cz, int claims, List<int[]> others) {
        if (others == null || others.isEmpty()) return 0;
        int lost = 0;
        for (int[] c : siteCells(cx, cz, q)) {
            for (int[] o : others) {
                if (self(o, cx, cz)) continue;
                if (inInner(c[0], c[1], o[0], o[1])) return -1;
                int oq = siteAt(c[0], c[1], o[0], o[1]);
                if (oq >= 0 && (claims(o) >> oq & 1) != 0) return -1;
            }
            if (!ours(c[0], c[1], cx, cz, claims, others)) lost++;
        }
        return lost;
    }

    /**
     * How many outer diagonals of {@code others} that their campuses have not claimed share ground with the outer
     * diagonal in quadrant {@code q} of the campus centred at (cx, cz) (0.15.0): each counted once, however many of
     * its cells the two share. A site that shares none is one no neighbour may want, so the campus takes it before one
     * that does ({@link CityOffer.Sites#contested}); a site the split takes cells of always shares one, as a cell is
     * lost only where it lies on a neighbour's outer diagonal.
     */
    public static int contested(int q, int cx, int cz, List<int[]> others) {
        if (others == null || others.isEmpty()) return 0;
        int[] hit = new int[others.size()];
        for (int[] c : siteCells(cx, cz, q)) for (int i = 0; i < hit.length; i++) {
            int[] o = others.get(i);
            if (self(o, cx, cz)) continue;
            int oq = siteAt(c[0], c[1], o[0], o[1]);
            if (oq >= 0 && (claims(o) >> oq & 1) == 0) hit[i] |= 1 << oq;
        }
        int n = 0;
        for (int h : hit) n += Integer.bitCount(h);
        return n;
    }

    /** Whether a neighbour {x, z} is the campus itself. */
    private static boolean self(int[] o, int cx, int cz) {
        return o == null || o.length < 2 || o[0] == cx && o[1] == cz;
    }

    private static long sq(long v) {
        return v * v;
    }
}
