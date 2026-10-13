package com.fluxecho.logic;

/**
 * The families of sites a campus builds on (0.11.0, CITY.md §2.1): every module stands on the sites of one family,
 * and the family decides its envelope, where its controller stands and which way it faces, its site floor, its clear
 * box and its keep-out. The inner city has three: the halls on the cardinal axes (2, 4, 6, front row at radial 24 —
 * the Echo Archive's and the Eco Dome's), the pavilions on the diagonals (1, 3, 5, 7, the octagon {@code oct(12, 16)}
 * centred at {@code (+-36, +-36)}), and the gate straddling the road (site 0). The others (up: the zenith, the sky
 * ring, the orbit; down: the root; out: the outer diagonals and flankers and the third ring) are named now so that
 * later versions add buildings to a plan that already holds them; only the hall family is built in 0.11.0, and the
 * masterplan draws the pavilions' envelopes.
 * <p>
 * 0.12.0 gives the two axis families a site number each, past the inner ring's eight, so the campus records and docks
 * them like any other site: {@link #ZENITH_SITE} for the pod over the nexus, {@link #ROOT_SITE} for the well under it.
 * An axis site takes no room on the inner ring ({@link #axis}).
 * <p>
 * 0.14.1 numbers the families later versions build, in ranges of their own that never meet 0 to 9 and leave room
 * between them: the outer diagonals O1, O3, O5, O7 are 11, 13, 15, 17 (ten more than the inner diagonal they stand
 * beyond); the outer flankers O0+, O0-, O2+, O2-, O4+, O4-, O6+, O6- are 20 to 27; the sky platforms S0 to S7 are 30 to
 * 37; the orbital deck is 40; the third ring's T00 to T15 are 50 to 65. So {@link #of} never takes an outer site for
 * an axis site or folds it onto the inner ring. A site may open later than its family's first sites
 * ({@link #opensAt}: half the flankers and half the sky platforms at phase V), and the families out on the campus's
 * second and third rings stand on ground a campus job of its own lays first ({@link #ground}).
 * <p>
 * The ordinals are not saved anywhere; the names may be (a module spec's family), so they never change. The site
 * numbers are saved (recorded sites, job keys) and never change either.
 */
public enum SiteFamily {

    /** Sites 2, 4, 6: 27 across, at most 35 deep from radial 24, the kit portal on the axis facing the nexus. */
    HALL(1, null, 4, 2, 6),
    /** Sites 1, 3, 5, 7: the octagon {@code oct(12, 16)} at {@code (+-36, +-36)}, a centre console (phase II). */
    PAVILION(2, null, 1, 3, 5, 7),
    /** Site 0: A 23..39, R -15..15 over the gate road, the passage R -6..6 left open (phase II). */
    GATE(2, null, 0),
    /** The pod over the nexus at Y0 + 40 on six legs, later floating (phase II): site {@link #ZENITH_SITE}. */
    ZENITH(2, null, 8),
    /** The well under the nexus, then the deep root and the ley core (phase II): site {@link #ROOT_SITE}. */
    ROOT(2, null, 9),
    /**
     * The four outer diagonals O1, O3, O5, O7, sites 11, 13, 15, 17: {@code oct(15, 20)} at {@code (+-62, +-62)}
     * (phase III, on the outer establish's ground).
     */
    OUTER_DIAG(3, SiteFamily.OUTER_GROUND, 11, 13, 15, 17),
    /**
     * The eight outer flankers O0+, O0-, O2+, O2-, O4+, O4-, O6+, O6-, sites 20 to 27: 27 x 31 at radial 64, cross
     * +-19 (reserved; the four + sides at phase IV, the - sides at V, on the outer establish's ground).
     */
    OUTER_FLANK(4, SiteFamily.OUTER_GROUND, 20, 21, 22, 23, 24, 25, 26, 27),
    /** The sky ring S0..S7 at Y0 + 40 round the pod, sites 30 to 37 (S0, S2, S4, S6 at phase IV, the rest at V). */
    SKY(4, null, 30, 31, 32, 33, 34, 35, 36, 37),
    /** The orbital anchor's deck over the pod, site 40 (phase V). */
    ORBIT(5, null, 40),
    /** The third ring's sixteen 41 x 41 sites, T00..T15, sites 50 to 65 (phase V, on the third ring's ground). */
    THIRD(5, SiteFamily.THIRD_GROUND, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65);

    /** The site of the zenith (the Echo Dome Generator's pod) and of the root (the Tidal Well), on the nexus's axis. */
    public static final int ZENITH_SITE = 8, ROOT_SITE = 9;
    /** The first site past the inner ring's eight (0 to 7): the zenith's. */
    public static final int AXIS_FROM = 8;
    /**
     * The plan keys of the campus jobs that lay the ground of the families further out (CITY.md §2.3): the outer
     * establish (0.15.0) for the outer diagonals and flankers, the third ring's for the third ring.
     */
    public static final String OUTER_GROUND = "establish:outer", THIRD_GROUND = "establish:third";

    /** The nexus phase the family's first sites open at. */
    public final int opens;
    /** The plan key of the campus job that lays the family's ground, or null for the forum's (always laid). */
    private final String ground;
    private final int[] sites;

    SiteFamily(int opens, String ground, int... sites) {
        this.opens = opens;
        this.ground = ground;
        this.sites = sites;
    }

    /**
     * The sites of the family: the inner ring's (0..7) for the gate, the pavilions and the halls, the axis site for the
     * zenith and the root (0.12.0), and since 0.14.1 the numbers of the families later versions build on.
     */
    public int[] sites() {
        return sites.clone();
    }

    /** Whether the site belongs to the family. */
    public boolean hasSite(int site) {
        for (int s : sites) if (s == site) return true;
        return false;
    }

    /**
     * Whether the family stands on the nexus's axis (the zenith over it, the root under it): its modules take no room
     * on the inner ring, are entered by the light lift and are built in the air or underground.
     */
    public boolean axis() {
        return this == ZENITH || this == ROOT || this == ORBIT;
    }

    /**
     * The plan key of the campus job that lays the ground the family's sites stand on ({@link #OUTER_GROUND},
     * {@link #THIRD_GROUND}), or null when the forum is their ground (the inner city, the axis, the sky over the
     * forum): a site waits for that job to have run before a module is offered on it (0.14.1).
     */
    public String ground() {
        return ground;
    }

    /**
     * The nexus phase a site opens at (0.14.1): its family's {@link #opens}, but the flankers' - sides (21, 23, 25, 27)
     * and the sky's berths S1, S3, S5, S7 (31, 33, 35, 37) a phase later (CITY.md §2.1: phase IV opens four of each,
     * V all eight); {@link Integer#MAX_VALUE} for a number no family holds.
     */
    public static int opensAt(int site) {
        SiteFamily f = of(site);
        if (f == null) return Integer.MAX_VALUE;
        if ((f == OUTER_FLANK || f == SKY) && site % 2 == 1) return f.opens + 1;
        return f.opens;
    }

    /**
     * How many sites of the inner ring (0 to 7) a nexus of the phase has opened (0.14.1): the ring room the nexus
     * window, Waila and the flux terminal show and the NEI city preview stands modules on, 3 at phase I (the halls) and
     * 8 from phase II (the halls, the pavilions and the gate). A campus offers and docks every module of an open site,
     * so the room never turns one away; a 0.9.2 nexus without a campus keeps {@link RingSlots#open}.
     */
    public static int openRingSites(int phase) {
        int n = 0;
        for (int s = 0; s < RingSlots.SLOTS; s++) if (opensAt(s) <= phase) n++;
        return n;
    }

    /**
     * Whether a site stands on the nexus's axis (the zenith 8, the root 9, the orbit 40): by its family since 0.14.1,
     * so no site of the families further out is ever taken for one.
     */
    public static boolean axisSite(int site) {
        SiteFamily f = of(site);
        return f != null && f.axis();
    }

    /** Whether a site is one of the inner ring's (0 to 7): a module docked there takes room on the ring. */
    public static boolean ringSite(int site) {
        return site >= 0 && site < RingSlots.SLOTS;
    }

    /**
     * How many of the docked modules' sites take room on the inner ring: the counter every window shows against the
     * ring's open slots (the nexus window, Waila, the flux terminal). The axis modules (0.12.0) take none.
     */
    public static int ringCount(int... sites) {
        int n = 0;
        for (int s : sites) if (ringSite(s)) n++;
        return n;
    }

    /**
     * The lang key (under {@code fluxecho.}) a window or a sign names a site with: the inner ring's sites by number
     * ({@code build.gui.site}, "%s 号位", given the site) but the gate by name (0.14.0: 大门, where the gatehouse
     * stands, and the 0.9.2 ring's slot 0 on the gate road), the axis sites by name: 天顶 the zenith, 根位 the root;
     * since 0.15.0 the outer ring's sites by their CITY.md names, the outer diagonals O1, O3, O5, O7
     * ({@code build.gui.site_o1}…) and the reserved flankers O0+, O0−, O2+ … O6− ({@code build.gui.site_o0p}, with
     * {@code p} for the + side and {@code m} for the − side). The site's number is given to every key; only the inner
     * ring's uses it.
     */
    public static String labelKey(int site) {
        if (site == ZENITH_SITE) return "build.gui.site_zenith";
        if (site == ROOT_SITE) return "build.gui.site_root";
        if (site == 0) return "build.gui.site_gate";
        SiteFamily f = of(site);
        if (f == OUTER_DIAG) return "build.gui.site_o" + (site - 10);
        if (f == OUTER_FLANK) return "build.gui.site_o" + (site - 20) / 2 * 2 + ((site - 20) % 2 == 0 ? "p" : "m");
        return "build.gui.site";
    }

    /**
     * The family of a site: on the inner ring (0..7) the gate, a pavilion or a hall; {@link #ZENITH_SITE} the zenith
     * and {@link #ROOT_SITE} the root; since 0.14.1 the families further out by their own numbers (see the class
     * comment). Null for a number no family holds (10, 12, 18, a negative one...).
     */
    public static SiteFamily of(int site) {
        if (site >= 0 && site < RingSlots.SLOTS) {
            if (site == 0) return GATE;
            return site % 2 == 1 ? PAVILION : HALL;
        }
        for (SiteFamily f : values()) if (f != HALL && f != PAVILION && f != GATE && f.hasSite(site)) return f;
        return null;
    }
}
