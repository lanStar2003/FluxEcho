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
 * The ordinals are not saved anywhere; the names may be (a module spec's family), so they never change.
 */
public enum SiteFamily {

    /** Sites 2, 4, 6: 27 across, at most 35 deep from radial 24, the kit portal on the axis facing the nexus. */
    HALL(1, 4, 2, 6),
    /** Sites 1, 3, 5, 7: the octagon {@code oct(12, 16)} at {@code (+-36, +-36)}, a centre console (phase II). */
    PAVILION(2, 1, 3, 5, 7),
    /** Site 0: A 23..39, R -15..15 over the gate road, the passage R -6..6 left open (phase II). */
    GATE(2, 0),
    /** The pod over the nexus at Y0 + 40 on six legs, later floating (phase II). */
    ZENITH(2),
    /** The well under the nexus, then the deep root and the ley core (phase II). */
    ROOT(2),
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

    /** The nexus phase the family's first sites open at. */
    public final int opens;
    private final int[] sites;

    SiteFamily(int opens, int... sites) {
        this.opens = opens;
        this.sites = sites;
    }

    /** The inner-ring sites (0..7) of the family; empty for the families that stand elsewhere. */
    public int[] sites() {
        return sites.clone();
    }

    /** Whether the inner-ring site belongs to the family. */
    public boolean hasSite(int site) {
        for (int s : sites) if (s == site) return true;
        return false;
    }

    /** The family of an inner-ring site (0..7): the gate, a pavilion or a hall. */
    public static SiteFamily of(int site) {
        int k = Math.floorMod(site, 8);
        if (k == 0) return GATE;
        return k % 2 == 1 ? PAVILION : HALL;
    }
}
