package com.fluxecho.logic;

/**
 * Where the Observatory's drawing puts its light (0.16.0, CITY.md §4.12: "the telescope pier ... dissolving into the
 * rendered telescope, while a big ring lens floats over the dome"), worked out in its frame
 * ({@link ObservatoryShape}: x across, y up from the deck, z back from the front):
 * <ul>
 * <li>Formed, the whole pier (its console, pillars and seat) gives way to the telescope: a steel column from the deck
 * up to a fork ({@link #PIVOT_Y}), and on the fork a tube ({@link #TUBE_R}, {@link #TUBE_FRONT} ahead of the pivot
 * and {@link #TUBE_BACK} behind it) that looks out through the dome's meridian slit (the slit runs front to back over
 * the room's middle, so the tube turns in that plane, always under the dome): it nods slowly either side of the zenith
 * along the slit, as far as its front stays inside the dome's shell ({@link #elevation}, {@link #ELEVATION_SWING}),
 * lit rings at both its ends, its objective a glassy disc.</li>
 * <li>Over the dome a big ring lens floats ({@link #LENS_Y}, radius {@link #LENS_R}), turning slowly, rocking a little
 * and bobbing ({@link #lensTilt}, {@link #lensBob}); a faint glassy fill inside it. Powered, a thin beam of light runs
 * from the lens's middle down to the objective, the two focused on one another.</li>
 * </ul>
 * Every motion is a function of the world time alone. The counts are what the renderer draws ({@link #quads},
 * {@link #landmarkQuads}).
 */
public final class ObservatoryLook {

    /** The pier's axis (the room's middle). */
    public static final double PIER_X = ObservatoryShape.ROOM_X + 0.5, PIER_Z = ObservatoryShape.ROOM_Z + 0.5;
    /** The column's foot (the deck's top, the console dissolved too) and the pivot the tube turns on, over the seat. */
    public static final double PIER_FOOT = 1, PIVOT_Y = ObservatoryShape.SEAT_Y + 0.6, PIER_W = 0.28;
    /** The fork's half-width across, and how far it rises under the pivot. */
    public static final double FORK = 0.75, FORK_DROP = 0.9;
    /** The tube: its radius, its length ahead of the pivot and behind it, its sides. */
    public static final double TUBE_R = 0.4, TUBE_FRONT = 2.8, TUBE_BACK = 1.0;
    public static final int TUBE_SIDES = 8;
    /**
     * The tube's sweep: its elevation's middle and swing (radians from level towards the front, -z), and its ticks:
     * a slow nod either side of the zenith along the slit, as far as the tube's front stays inside the dome's shell.
     */
    public static final double ELEVATION_MID = Math.PI / 2, ELEVATION_SWING = 0.3;
    public static final int SWEEP_T = 12000;

    /** The ring lens: its middle's height (over the dome's top layer y11), its radius, width and thickness. */
    public static final double LENS_Y = ObservatoryShape.HEIGHT + 2.6, LENS_R = 5.6, LENS_W = 0.7, LENS_H = 0.35;
    /** Its segments near, mid and far, the ticks of one turn, its rock (radians) and its bob (blocks). */
    public static final int[] LENS_SEGMENTS = { 32, 16, 16 };
    public static final int LENS_TURN_T = 2400, ROCK_T = 700, BOB_T = 160;
    public static final double ROCK = 0.12, BOB = 0.2;

    /** The quads of a glow, and of the column (a strut's four sides). */
    public static final int GLOW_QUADS = 1, STRUT_QUADS = 4;

    private ObservatoryLook() {}

    /**
     * The tube's elevation at the world time {@code t} (radians from level towards the front, -z, through the zenith,
     * pi/2, to the back): sweeping slowly over the sky along the slit.
     */
    public static double elevation(double t) {
        return ELEVATION_MID + ELEVATION_SWING * StrictMath.sin(2 * Math.PI * t / SWEEP_T);
    }

    /** The tube's way from the pivot at an elevation: {dx, dy, dz} in the frame (across is always 0). */
    public static double[] tubeAxis(double elevation) {
        return new double[] { 0, StrictMath.sin(elevation), -StrictMath.cos(elevation) };
    }

    /** The middle of the tube's objective (its far end ahead of the pivot) at an elevation. */
    public static double[] objective(double elevation) {
        double[] a = tubeAxis(elevation);
        return new double[] { PIER_X, PIVOT_Y + a[1] * TUBE_FRONT, PIER_Z + a[2] * TUBE_FRONT };
    }

    /** How far the ring lens has turned at the world time (radians). */
    public static double lensTurn(double t) {
        return 2 * Math.PI * ArcaneLook.frac(t / LENS_TURN_T);
    }

    /** How far the ring lens rocks about its across axis at the world time (radians). */
    public static double lensTilt(double t) {
        return ROCK * StrictMath.sin(2 * Math.PI * t / ROCK_T);
    }

    /** How far the ring lens bobs over its height at the world time (blocks). */
    public static double lensBob(double t) {
        return BOB * StrictMath.sin(2 * Math.PI * t / BOB_T);
    }

    /**
     * A point of the ring lens at angle {@code a} round it and radius {@code r}, {@code dy} over its middle's plane:
     * {x, y, z} in the frame, rocked {@code tilt} about the across axis and bobbed {@code bob}.
     */
    public static double[] lensPoint(double a, double r, double dy, double tilt, double bob) {
        double x = r * StrictMath.cos(a), z = r * StrictMath.sin(a);
        double c = StrictMath.cos(tilt), s = StrictMath.sin(tilt);
        return new double[] { PIER_X + x, LENS_Y + bob + dy * c + z * s, PIER_Z + z * c - dy * s };
    }

    // ---- the budget

    /**
     * The quads of the telescope at a tier: near the column, the fork's two arms, the tube's sides (those turned to
     * the camera, never more than all of them) and its two ends' rings of light, its objective's disc and glow; mid the
     * column, the tube as a band and the objective's glow; none far.
     */
    public static int telescopeQuads(int tier) {
        if (tier == ModuleLook.FAR) return 0;
        if (tier == ModuleLook.MID) return STRUT_QUADS + 1 + GLOW_QUADS;
        return STRUT_QUADS + 2 + TUBE_SIDES + 2 * TUBE_SIDES + TUBE_SIDES + GLOW_QUADS;
    }

    /**
     * The quads of the ring lens at a tier: near each segment's faces turned to the camera (of its top or bottom one,
     * its outer and its inner band) and a seam of light, the glassy fill inside it in half as many pieces, and two
     * glows (its middle and a glint running round it); mid each segment's top or bottom and its seam; far a line a
     * segment.
     */
    public static int lensQuads(int tier) {
        int seg = LENS_SEGMENTS[tier];
        if (tier == ModuleLook.FAR) return seg;
        if (tier == ModuleLook.MID) return seg * 2;
        return seg * 3 + seg + seg / 2 + 2 * GLOW_QUADS;
    }

    /** The beam from the lens down to the objective while powered: a line and its halo near, a line mid. */
    public static int beamQuads(int tier) {
        return tier == ModuleLook.FAR ? 0 : tier == ModuleLook.NEAR ? 2 : 1;
    }

    /**
     * The most quads the observatory's own drawing takes a frame at a tier ({@link ModuleLook#tier}), powered: the
     * telescope, the ring lens and the beam. The shared sky drawing ({@link SkyLook#quads}) and the drawing of every
     * city module ({@link ModuleLook#baseQuads}) come on top.
     */
    public static int quads(int tier) {
        return telescopeQuads(tier) + lensQuads(tier) + beamQuads(tier);
    }

    /** The most quads its landmark takes past the effects' range: the ring lens as far, and a glow in it. */
    public static int landmarkQuads() {
        return lensQuads(ModuleLook.FAR) + GLOW_QUADS;
    }
}
