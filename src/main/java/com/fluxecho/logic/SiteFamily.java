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
 * The ordinals are not saved anywhere; the names may be (a module spec's family), so they never change. The site
 * numbers are saved (recorded sites, job keys) and never change either.
 */
public enum SiteFamily {

    /** Sites 2, 4, 6: 27 across, at most 35 deep from radial 24, the kit portal on the axis facing the nexus. */
    HALL(1, 4, 2, 6),
    /** Sites 1, 3, 5, 7: the octagon {@code oct(12, 16)} at {@code (+-36, +-36)}, a centre console (phase II). */
    PAVILION(2, 1, 3, 5, 7),
    /** Site 0: A 23..39, R -15..15 over the gate road, the passage R -6..6 left open (phase II). */
    GATE(2, 0),
    /** The pod over the nexus at Y0 + 40 on six legs, later floating (phase II): site {@link #ZENITH_SITE}. */
    ZENITH(2, 8),
    /** The well under the nexus, then the deep root and the ley core (phase II): site {@link #ROOT_SITE}. */
    ROOT(2, 9),
    /** The four outer diagonals O1, O3, O5, O7: {@code oct(15, 20)} at {@code (+-62, +-62)} (phase III). */
    OUTER_DIAG(3),
    /** The eight outer flankers O0+ … O6-: 27 x 31 at radial 64, cross +-19 (reserved, phase IV). */
    OUTER_FLANK(4),
    /** The sky ring S0..S7 at Y0 + 40 round the pod (phase IV). */
    SKY(4),
    /** The orbital anchor's deck over the pod (phase V). */
    ORBIT(5),
    /** The third ring's sixteen 41 x 41 sites, T00..T15 (phase V). */
    THIRD(5);

    /** The site of the zenith (the Echo Dome Generator's pod) and of the root (the Tidal Well), on the nexus's axis. */
    public static final int ZENITH_SITE = 8, ROOT_SITE = 9;
    /** The inner ring's sites are 0 to 7; the sites from here on stand on the nexus's axis. */
    public static final int AXIS_FROM = 8;

    /** The nexus phase the family's first sites open at. */
    public final int opens;
    private final int[] sites;

    SiteFamily(int opens, int... sites) {
        this.opens = opens;
        this.sites = sites;
    }

    /**
     * The sites of the family: the inner ring's (0..7) for the gate, the pavilions and the halls, the axis site for the
     * zenith and the root (0.12.0); empty for the families later versions place.
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

    /** Whether a site is on the nexus's axis rather than on the inner ring ({@link #AXIS_FROM} and up). */
    public static boolean axisSite(int site) {
        return site >= AXIS_FROM;
    }

    /** Whether a site is one of the inner ring's (0 to 7): a module docked there takes room on the ring. */
    public static boolean ringSite(int site) {
        return site >= 0 && site < AXIS_FROM;
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
     * stands, and the 0.9.2 ring's slot 0 on the gate road), the axis sites by name: 天顶 the zenith, 根位 the root.
     */
    public static String labelKey(int site) {
        if (site == ZENITH_SITE) return "build.gui.site_zenith";
        if (site == ROOT_SITE) return "build.gui.site_root";
        if (site == 0) return "build.gui.site_gate";
        return "build.gui.site";
    }

    /**
     * The family of a site: on the inner ring (0..7) the gate, a pavilion or a hall; {@link #ZENITH_SITE} the zenith
     * and {@link #ROOT_SITE} the root.
     */
    public static SiteFamily of(int site) {
        if (site == ZENITH_SITE) return ZENITH;
        if (site == ROOT_SITE) return ROOT;
        int k = Math.floorMod(site, 8);
        if (k == 0) return GATE;
        return k % 2 == 1 ? PAVILION : HALL;
    }
}
