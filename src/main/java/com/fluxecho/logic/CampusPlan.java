package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The masterplan and the paving of a nexus campus (0.10.0): which cells the establish job grades and paves, where the
 * halls, the gate, its pylons and the supply port stand, and which deck tile goes on every paved cell.
 * <p>
 * Everything is laid out in the campus frame round the nexus centre: {@code A} runs ahead along the nexus front and
 * {@code R} to its right (clockwise seen from above); {@link #toWorld} and {@link #toLocal} turn that frame into world
 * offsets the same way {@link RingSlots#offset} does. Site {@code k} sits {@code 45 * k} degrees clockwise from the
 * front: site 0 is the gate, sites 4, 2 and 6 take halls (the Echo Archive), the diagonals are reserved.
 * <p>
 * The establish job paves the forum ({@code oct(16, 22)} round the dais), its grate band, the gate and the whole
 * promenade ring ({@code oct(19, 26)}); a hall job later adds its forecourt and apron with {@link #siteFloor}. Floors
 * only ever grow: a site floor never touches a cell of the establish floor, so a finished tile is never re-evaluated.
 * <p>
 * The tile rules are a port of the scratch generator {@code spec/gen/synth_campus.py}: light only on lines (forum ribs,
 * the gate seam, hall spines and thresholds), the grate band, chevrons before each door, 3x3 light wells, trim on gate
 * edges, curbs, the apron's shadow gap and Voronoi seams, then dark clusters on a radial tone gradient, and plain deck
 * everywhere else. Two seeded lobes on the promenade's diagonals break the symmetry with extra dark clusters and a
 * light well each. Every choice comes from the seed (see {@link Mix}) and {@link StrictMath}, never from the config,
 * so the client and the server compute the same plan from the controller's position alone.
 * <p>
 * The tile shares the paving is held to (deck at least 45%, trim 12 to 35%, light at most 9%) are those of the
 * establish floor. A hall floor is about half trim, because the apron's inner ring (the shadow gap round the building)
 * is trim by rule, as in the generator; a campus with three finished halls therefore runs to about 40% trim, with its
 * deck and light shares still in bounds.
 */
public final class CampusPlan {

    /** The radial distance of a hall's front row from the nexus centre. */
    public static final int HALL_FRONT = 24;
    /** The graded disc, {@code oct(64, 90)}. */
    public static final int GRADE_R = 64, GRADE_K = 90;
    /** The forum, {@code oct(16, 22)} less the dais. */
    public static final int FORUM_R = 16, FORUM_K = 22;
    /** The outer edge of the promenade ring, {@code oct(19, 26)}. */
    public static final int RING_R = 19, RING_K = 26;
    /** The hall sites in the order a module prefers them: the back first, then the right and the left. */
    public static final int[] HALL_SITES = { 4, 2, 6 };
    /** The reserved envelope of a diagonal site: centred this far out on both axes, this many cells square. */
    public static final int DIAGONAL_CENTRE = 34, DIAGONAL_SIZE = 23;

    /** The dais, the nexus base {@code oct(5, 7)}; paving never touches it. */
    private static final int BASE_R = 5, BASE_K = 7;
    /** The grate band, {@code oct(6, 8)} less the dais. */
    private static final int GRATE_R = 6, GRATE_K = 8;
    /** Forum ribs are solid out to this Chebyshev radius and dashed beyond it. */
    private static final int RIB_SOLID = 10;
    /** The gate strip ({@code |R| <= 3}), widened to {@code |R| <= 5} at the node and at the far threshold. */
    private static final int GATE_FROM = 17, GATE_TO = 36, GATE_HALF = 3, GATE_WIDE = 5, NODE_FROM = 25, NODE_TO = 29,
        GATE_END = 34;
    private static final int[][] PYLONS = { { 27, 5 }, { 27, -5 }, { 35, 5 }, { 35, -5 } };
    private static final int[] SUPPLY_PORT = { 9, 5 };
    /** The forecourt runs from this radial distance to the threshold with these half-widths. */
    private static final int FORE_FROM = 20;
    private static final int[] FORE_HALF = { 8, 8, 7, 6 };
    /** The lit door threshold: the row just in front of a hall, this many cells either side of its axis. */
    private static final int THRESHOLD = HALL_FRONT - 1, THRESHOLD_HALF = 3;
    /** The chevron V points at the door: its apex on the axis at this radial distance, its arms this far across. */
    private static final int CHEVRON_APEX = 22, CHEVRON_ARM = 4;
    /**
     * The apron is a ring this wide round a hall's footprint; its back corners are cut where the overshoot past the
     * back row and the side together exceeds the chamfer.
     */
    private static final int APRON = 2, APRON_CHAMFER = 3;
    /** Forum light-well candidates sit at (+-9, +-9), each kept with this chance. */
    private static final int WELL_AT = 9;
    private static final double WELL_KEEP = 0.75;
    /** Ragged edge: forum cells on an open edge drop in 2x2 clusters with this chance, never this close to a path. */
    private static final double DROP = 0.45;
    private static final int DROP_CLEAR = 3;
    /** Dark clusters (3x3) are chosen with the chance {@code clamp((r - 12) / 140, 0, 0.35)}. */
    private static final double DARK_FROM = 12, DARK_SPAN = 140, DARK_MAX = 0.35;
    /** A cell is a Voronoi seam when its two nearest seed points are closer to each other than this. */
    private static final double SEAM = 0.5;
    /** Two of the four diagonals get a lobe reaching this far past the forum rim, dark clusters with this chance. */
    private static final int LOBES = 2, LOBE_REACH = 4;
    private static final double LOBE_DARK = 0.55;

    /** Hash salts: the generator's, then the lobes'. */
    private static final int S_SEED_ANGLE = 3, S_SEED_A = 5, S_SEED_R = 6, S_DROP = 7, S_WELL_KEEP = 11,
        S_WELL_SHIFT = 13, S_DARK = 17, S_LOBE = 21, S_LOBE_DARK = 23, S_LOBE_SIDE = 25, S_LOBE_SHIFT = 27;

    private static final int DECK = Parts.deck(Parts.D_DECK), TRIM = Parts.deck(Parts.D_TRIM),
        LIT = Parts.deck(Parts.D_LIT), GRATE = Parts.deck(Parts.D_GRATE), DARK = Parts.deck(Parts.D_DARK),
        CHEVRON = Parts.deck(Parts.D_CHEVRON), WELL = Parts.deck(Parts.D_WELL);

    /** The unit axis of every site in (A, R): the cardinals, then the diagonals between them. */
    private static final int[][] AXES = { { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 }, { -1, 0 }, { -1, -1 }, { 0, -1 },
        { 1, -1 } };
    private static final int[][] SIDES = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    private final long seed;
    /** The Voronoi seed points of the paving seams, in (A, R). */
    private final double[] seedA, seedR;
    /** The diagonal sites that carry a lobe. */
    private final int[] lobeSites;
    /** The promenade cells the lobes darken. */
    private final Set<Long> lobe;
    private final Set<Long> wells;
    /** The establish set after the ragged edge: forum, gate and promenade ring. */
    private final Set<Long> paved;
    private final Map<Long, Integer> establish;
    /** Site floors already computed, keyed by site, width and depth. */
    private final Map<Long, Map<Long, Integer>> sites = new HashMap<>();

    /**
     * Computes the establish floor, the wells and the lobes for a campus seed, normally {@link Mix#seed} of the nexus
     * controller's position. Hall floors are computed on first request and kept.
     */
    public CampusPlan(long seed) {
        this.seed = seed;

        // Voronoi seed points on polar rings every 6 blocks from r 8, about 7 apart along each ring, jittered by 2.
        List<double[]> points = new ArrayList<>();
        for (int ring = 1; ring < 12; ring++) {
            int rr = 2 + 6 * ring;
            int n = Math.max(6, (int) (2 * Math.PI * rr / 7));
            for (int k = 0; k < n; k++) {
                double angle = 2 * Math.PI * (k + Mix.unit(seed, ring, k, S_SEED_ANGLE)) / n;
                points.add(
                    new double[] { rr * StrictMath.cos(angle) + (Mix.unit(seed, ring, k, S_SEED_A) * 4 - 2),
                        rr * StrictMath.sin(angle) + (Mix.unit(seed, ring, k, S_SEED_R) * 4 - 2) });
            }
        }
        seedA = new double[points.size()];
        seedR = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            seedA[i] = points.get(i)[0];
            seedR[i] = points.get(i)[1];
        }

        // The forum light wells: a 3x3 recess at each kept candidate, shifted -1, 0 or +1 along its diagonal.
        Set<Long> w = new LinkedHashSet<>();
        for (int sa = 1; sa >= -1; sa -= 2) for (int sr = 1; sr >= -1; sr -= 2) {
            if (Mix.unit(seed, sa, sr, S_WELL_KEEP) >= WELL_KEEP) continue;
            int d = (int) (Mix.unit(seed, sa, sr, S_WELL_SHIFT) * 3) - 1;
            square(w, sa * (WELL_AT + d), sr * (WELL_AT + d));
        }

        // The lobes: the promenade band outside the forum's diagonal face, darkened, with one more light well.
        lobeSites = pickLobes(seed);
        Set<Long> lobeCells = new LinkedHashSet<>();
        for (int site : lobeSites) {
            int[] ax = AXES[site];
            for (int a = -RING_R; a <= RING_R; a++) for (int r = -RING_R; r <= RING_R; r++) {
                if (inLobe(ax, a, r)) lobeCells.add(key(a, r));
            }
            // The well straddles the forum rim (its centre one past it) beside the diagonal rib, on a seeded side, so
            // it never touches the forum well of the same diagonal and leaves the ring's outer curb whole.
            int side = Mix.unit(seed, S_LOBE_SIDE, site) < 0.5 ? 1 : -1;
            int shift = 2 + (int) (Mix.unit(seed, S_LOBE_SHIFT, site) * 2);
            int along = FORUM_K + 1, across = side * (2 * shift + 1);
            int p = (along + across) / 2, q = along - p;
            square(w, ax[0] * p, ax[1] * q);
        }
        lobe = Collections.unmodifiableSet(lobeCells);

        // The establish set: forum and promenade ring (oct(19, 26) less the dais) and the gate.
        Set<Long> e = new LinkedHashSet<>();
        for (int a = -RING_R; a <= GATE_TO; a++) for (int r = -RING_R; r <= RING_R; r++) {
            if (inBase(a, r)) continue;
            if (oct(a, r, RING_R, RING_K) || inGate(a, r)) e.add(key(a, r));
        }
        // The ragged edge, computed against the establish set alone so that no later module changes it. With the
        // full promenade ring round the forum no forum cell has an open edge, so this drops nothing for the current
        // geometry; the lobes carry the irregularity instead.
        List<Long> drop = new ArrayList<>();
        for (long k : e) {
            int a = keyA(k), r = keyR(k);
            if (!inForum(a, r) || inGate(a, r) || rib(a, r) || !outsideTouch(a, r, e, -1, 0, 0)) continue;
            if (nearPath(a, r)) continue;
            if (Mix.unit(seed, Math.floorDiv(a, 2), Math.floorDiv(r, 2), S_DROP) < DROP) drop.add(k);
        }
        e.removeAll(drop);
        w.retainAll(e);
        paved = Collections.unmodifiableSet(e);
        wells = Collections.unmodifiableSet(w);

        Map<Long, Integer> floor = new LinkedHashMap<>();
        for (long k : e) floor.put(k, tile(keyA(k), keyR(k), e, -1, 0, 0, false));
        establish = Collections.unmodifiableMap(floor);
    }

    // ---- frame ----

    /** The world offset {dx, dz} of the campus cell (a, r) for a nexus whose front is {@code (fx, fz)}. */
    public static int[] toWorld(int a, int r, int fx, int fz) {
        return new int[] { a * fx - r * fz, a * fz + r * fx };
    }

    /** The campus cell {a, r} of the world offset {@code (dx, dz)} for a nexus whose front is {@code (fx, fz)}. */
    public static int[] toLocal(int dx, int dz, int fx, int fz) {
        return new int[] { dx * fx + dz * fz, -dx * fz + dz * fx };
    }

    /** One long for the cell (a, r), used as the key of every cell set and floor map. */
    public static long key(int a, int r) {
        return ((long) a << 32) | (r & 0xFFFFFFFFL);
    }

    public static int keyA(long k) {
        return (int) (k >> 32);
    }

    public static int keyR(long k) {
        return (int) k;
    }

    /** The octagon test {@code oct(rmax, k)}: {@code |a| <= rmax}, {@code |r| <= rmax} and {@code |a| + |r| <= k}. */
    public static boolean oct(int a, int r, int rmax, int k) {
        int aa = Math.abs(a), ar = Math.abs(r);
        return aa <= rmax && ar <= rmax && aa + ar <= k;
    }

    /** Whether the cell belongs to the dais, the nexus base {@code oct(5, 7)}. */
    public static boolean inBase(int a, int r) {
        return oct(a, r, BASE_R, BASE_K);
    }

    /** Whether the cell belongs to the forum, {@code oct(16, 22)} less the dais. */
    public static boolean inForum(int a, int r) {
        return oct(a, r, FORUM_R, FORUM_K) && !inBase(a, r);
    }

    /** Whether the cell belongs to the promenade ring, {@code oct(19, 26)} less the forum and the dais. */
    public static boolean inRing(int a, int r) {
        return oct(a, r, RING_R, RING_K) && !oct(a, r, FORUM_R, FORUM_K);
    }

    /**
     * Whether the cell belongs to the gate: A 17 to 36 with {@code |R| <= 3}, widened to {@code |R| <= 5} at the node
     * (A 25 to 29) and the far threshold (A 34 to 36).
     */
    public static boolean inGate(int a, int r) {
        if (a < GATE_FROM || a > GATE_TO) return false;
        int ar = Math.abs(r);
        return ar <= GATE_HALF || ar <= GATE_WIDE && (a >= NODE_FROM && a <= NODE_TO || a >= GATE_END);
    }

    // ---- sites ----

    /** The unit axis {da, dr} of a site: (1, 0) for the front, then clockwise; the diagonals are (+-1, +-1). */
    public static int[] siteAxis(int site) {
        int[] ax = AXES[Math.floorMod(site, AXES.length)];
        return new int[] { ax[0], ax[1] };
    }

    /**
     * The centre {a, r} of a module at the site: on a cardinal site the middle of a footprint {@code depth} deep whose
     * front row is at {@link #HALL_FRONT}; on a diagonal site the reserved envelope's centre.
     */
    public static int[] moduleCentre(int site, int depth) {
        int k = Math.floorMod(site, AXES.length);
        if (k % 2 == 1) return diagonalCentre(k);
        int d = HALL_FRONT + (depth - 1) / 2;
        return new int[] { AXES[k][0] * d, AXES[k][1] * d };
    }

    /** The hall site whose module centre for the depth lies within 3 of (a, r) on both axes, or -1. */
    public static int hallSiteAt(int a, int r, int depth) {
        for (int site : HALL_SITES) {
            int[] c = moduleCentre(site, depth);
            if (Math.abs(a - c[0]) <= 3 && Math.abs(r - c[1]) <= 3) return site;
        }
        return -1;
    }

    /**
     * Whether (a, r) lies in the footprint of a {@code width} x {@code depth} hall module at the site: its front row at
     * radial distance {@link #HALL_FRONT}, centred on the site axis (an even width puts its extra column on the left
     * of the axis seen from the nexus). Always false for sites that are not hall sites.
     */
    public static boolean inHall(int site, int width, int depth, int a, int r) {
        if (!isHallSite(site)) return false;
        int[] ax = AXES[site];
        int t = a * ax[0] + r * ax[1], s = -a * ax[1] + r * ax[0];
        return t >= HALL_FRONT && t < HALL_FRONT + depth && s >= -(width / 2) && s <= (width - 1) / 2;
    }

    /** The reserved masterplan envelope centre {a, r} of a diagonal site, (+-34, +-34); the envelope is 23 x 23. */
    public static int[] diagonalCentre(int site) {
        int k = Math.floorMod(site, AXES.length);
        if (k % 2 == 0) throw new IllegalArgumentException("not a diagonal site: " + site);
        return new int[] { AXES[k][0] * DIAGONAL_CENTRE, AXES[k][1] * DIAGONAL_CENTRE };
    }

    private static boolean isHallSite(int site) {
        for (int k : HALL_SITES) if (k == site) return true;
        return false;
    }

    // ---- the plan ----

    /** The seed the plan was made from. */
    public long seed() {
        return seed;
    }

    /**
     * The floor laid by the establish job: forum, grate band, gate and the full promenade ring, as cell key to deck
     * part code for the Y0 cell. Light wells map to the WELL code; see {@link #wells()} for how they are built.
     */
    public Map<Long, Integer> establishFloor() {
        return establish;
    }

    /**
     * The light wells: 3x3 recesses, cell keys, a subset of {@link #establishFloor()}'s keys. The builder puts air at
     * Y0 and a deck WELL at Y0 - 1 on these cells.
     */
    public Set<Long> wells() {
        return wells;
    }

    /** The two diagonal sites (from 1, 3, 5, 7) whose promenade band carries a lobe, in increasing order. */
    public int[] lobeSites() {
        return lobeSites.clone();
    }

    /**
     * The floor laid by a hall module job at the site for a {@code width} x {@code depth} module: forecourt, apron,
     * spine dashes, door threshold and chevrons, as cell key to deck part code. It never contains a cell of
     * {@link #establishFloor()} nor of the module's own footprint.
     *
     * @throws IllegalArgumentException if the site is not a hall site or the size is not positive
     */
    public Map<Long, Integer> siteFloor(int site, int width, int depth) {
        if (!isHallSite(site)) throw new IllegalArgumentException("not a hall site: " + site);
        if (width < 1 || depth < 1) throw new IllegalArgumentException("bad module size " + width + "x" + depth);
        long ck = ((long) site << 42) | ((long) width << 21) | depth;
        synchronized (sites) {
            Map<Long, Integer> floor = sites.get(ck);
            if (floor == null) {
                floor = buildSiteFloor(site, width, depth);
                sites.put(ck, floor);
            }
            return floor;
        }
    }

    /** Every graded cell: {@code oct(GRADE_R, GRADE_K)} less the dais. The same for every seed. */
    public Set<Long> gradeArea() {
        return Grade.AREA;
    }

    /** The cells {a, r} of the four gate pylons: (27, +-5) and (35, +-5). Each stands on a deck tile. */
    public List<int[]> pylons() {
        List<int[]> out = new ArrayList<>();
        for (int[] p : PYLONS) out.add(new int[] { p[0], p[1] });
        return out;
    }

    /** The cell {a, r} of the supply port, (9, 5); it stands at Y0 + 1 on a deck tile. */
    public int[] supplyPort() {
        return new int[] { SUPPLY_PORT[0], SUPPLY_PORT[1] };
    }

    // ---- paving ----

    private Map<Long, Integer> buildSiteFloor(int site, int width, int depth) {
        int[] ax = AXES[site];
        int back = HALL_FRONT + depth - 1, lo = -(width / 2), hi = (width - 1) / 2;
        Set<Long> fore = new LinkedHashSet<>(), apron = new LinkedHashSet<>();
        for (int i = 0; i < FORE_HALF.length; i++) for (int s = -FORE_HALF[i]; s <= FORE_HALF[i]; s++) {
            fore.add(siteKey(ax, FORE_FROM + i, s));
        }
        for (int t = HALL_FRONT - APRON; t <= back + APRON; t++) for (int s = lo - APRON; s <= hi + APRON; s++) {
            long k = siteKey(ax, t, s);
            if (fore.contains(k) || inHall(site, width, depth, keyA(k), keyR(k))) continue;
            // the overshoot past the back row plus the overshoot past the side: the corner cells beyond the chamfer go
            int out = (t - back) + (s < 0 ? lo - s : s - hi);
            if (out > APRON_CHAMFER) continue;
            apron.add(k);
        }
        Set<Long> cells = new LinkedHashSet<>(fore);
        cells.addAll(apron);
        // monotone growth: the establish floor (and anything in its disc) is never touched again
        List<Long> skip = new ArrayList<>();
        for (long k : cells) {
            int a = keyA(k), r = keyR(k);
            if (paved.contains(k) || inBase(a, r) || oct(a, r, RING_R, RING_K) || inGate(a, r)) skip.add(k);
        }
        cells.removeAll(skip);
        Set<Long> all = new HashSet<>(paved);
        all.addAll(cells);
        Map<Long, Integer> floor = new LinkedHashMap<>();
        for (long k : cells) floor.put(k, tile(keyA(k), keyR(k), all, site, width, depth, apron.contains(k)));
        return Collections.unmodifiableMap(floor);
    }

    /**
     * The deck code of a paved cell, by the generator's rules in priority order. {@code site} is -1 for the establish
     * floor, else the hall site whose floor the cell belongs to; {@code apron} marks the hall's apron cells.
     */
    private int tile(int a, int r, Set<Long> all, int site, int width, int depth, boolean apron) {
        long k = key(a, r);
        if (wells.contains(k)) return WELL;
        // light only on lines; the ribs cross the grate band so the dais spokes run on unbroken
        if (inForum(a, r) && rib(a, r)) {
            if (Math.max(Math.abs(a), Math.abs(r)) <= RIB_SOLID || (Math.abs(a) + Math.abs(r)) % 2 == 0) return LIT;
        }
        if (oct(a, r, GRATE_R, GRATE_K) && !inBase(a, r)) return GRATE;
        if (inGate(a, r) && r == 0 && a % 2 == 0) return LIT;
        if (site >= 0) {
            int[] ax = AXES[site];
            int t = a * ax[0] + r * ax[1], s = -a * ax[1] + r * ax[0], as = Math.abs(s);
            if (t == THRESHOLD && as <= THRESHOLD_HALF) return LIT;
            if (s == 0 && t >= FORE_FROM && t <= THRESHOLD && t % 2 == 0) return LIT;
            // chevrons pointing at the door; the apex itself is on the lit spine
            if (t >= CHEVRON_APEX - CHEVRON_ARM && t <= CHEVRON_APEX && as == CHEVRON_APEX - t && s != 0) {
                return CHEVRON;
            }
        }
        if (inGate(a, r)) {
            int ar = Math.abs(r);
            if (ar == GATE_HALF && !(a >= NODE_FROM && a <= NODE_TO || a >= GATE_END) || ar == GATE_WIDE) return TRIM;
        }
        if (apron) {
            for (int da = -1; da <= 1; da++) for (int dr = -1; dr <= 1; dr++) {
                if (inHall(site, width, depth, a + da, r + dr)) return TRIM;
            }
        } else if (outsideTouch(a, r, all, site, width, depth)) {
            return TRIM;
        }
        if (seam(a, r)) return TRIM;
        int ca = Math.floorDiv(a, 3), cr = Math.floorDiv(r, 3);
        if (lobe.contains(k) && Mix.unit(seed, ca, cr, S_LOBE_DARK) < LOBE_DARK) return DARK;
        double rad = StrictMath.hypot(a, r);
        double chance = Math.max(0, Math.min(DARK_MAX, (rad - DARK_FROM) / DARK_SPAN));
        if (Mix.unit(seed, ca, cr, S_DARK) < chance) return DARK;
        return DECK;
    }

    /** Whether a side neighbour of the cell is unpaved ground (not paved, not the dais, not the module footprint). */
    private static boolean outsideTouch(int a, int r, Set<Long> all, int site, int width, int depth) {
        for (int[] d : SIDES) {
            int na = a + d[0], nr = r + d[1];
            if (!all.contains(key(na, nr)) && !inBase(na, nr) && !inHall(site, width, depth, na, nr)) return true;
        }
        return false;
    }

    /** Whether a path of the establish set (the gate or the promenade ring) lies within the ragged edge's clearance. */
    private static boolean nearPath(int a, int r) {
        for (int i = -DROP_CLEAR; i <= DROP_CLEAR; i++) for (int j = -DROP_CLEAR; j <= DROP_CLEAR; j++) {
            if (inGate(a + i, r + j) || inRing(a + i, r + j)) return true;
        }
        return false;
    }

    /** Whether the cell's two nearest Voronoi seed points are about equally far: a seam between two paving fields. */
    private boolean seam(int a, int r) {
        double d0 = Double.MAX_VALUE, d1 = Double.MAX_VALUE;
        for (int i = 0; i < seedA.length; i++) {
            double d = StrictMath.hypot(a - seedA[i], r - seedR[i]);
            if (d < d0) {
                d1 = d0;
                d0 = d;
            } else if (d < d1) {
                d1 = d;
            }
        }
        return d1 - d0 < SEAM;
    }

    /** Forum ribs: the axes and the diagonals. */
    private static boolean rib(int a, int r) {
        return a == 0 || r == 0 || Math.abs(a) == Math.abs(r);
    }

    /**
     * Whether a promenade cell lies in the lobe of the diagonal {@code ax}: in that quadrant, outside the forum's
     * diagonal face and at most 4 past its rim.
     */
    private static boolean inLobe(int[] ax, int a, int r) {
        return inRing(a, r) && ax[0] * a > 0
            && ax[1] * r > 0
            && Math.abs(a) <= FORUM_R
            && Math.abs(r) <= FORUM_R
            && Math.abs(a) + Math.abs(r) - FORUM_K <= LOBE_REACH;
    }

    /** The two diagonal sites with the lowest seeded draws, in increasing order. */
    private static int[] pickLobes(long seed) {
        List<Integer> diagonals = new ArrayList<>();
        for (int site = 1; site < AXES.length; site += 2) diagonals.add(site);
        diagonals.sort((x, y) -> {
            int c = Double.compare(Mix.unit(seed, S_LOBE, x), Mix.unit(seed, S_LOBE, y));
            return c != 0 ? c : Integer.compare(x, y);
        });
        int[] out = new int[LOBES];
        for (int i = 0; i < LOBES; i++) out[i] = diagonals.get(i);
        Arrays.sort(out);
        return out;
    }

    /** The key of the cell {@code t} out along a cardinal site axis and {@code s} across it. */
    private static long siteKey(int[] ax, int t, int s) {
        return key(t * ax[0] - s * ax[1], t * ax[1] + s * ax[0]);
    }

    private static void square(Set<Long> into, int ca, int cr) {
        for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) into.add(key(ca + i, cr + j));
    }

    /** The graded disc, shared by every plan (it does not depend on the seed). */
    private static final class Grade {

        static final Set<Long> AREA;

        static {
            Set<Long> s = new LinkedHashSet<>();
            for (int a = -GRADE_R; a <= GRADE_R; a++) for (int r = -GRADE_R; r <= GRADE_R; r++) {
                if (oct(a, r, GRADE_R, GRADE_K) && !inBase(a, r)) s.add(key(a, r));
            }
            AREA = Collections.unmodifiableSet(s);
        }
    }
}
