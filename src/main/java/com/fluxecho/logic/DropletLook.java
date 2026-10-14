package com.fluxecho.logic;

/**
 * Where the Droplet Hangar's drawing puts its light (0.16.0, CITY.md §4.12 Visuals: "mirror droplets (teardrops drawn
 * with a chrome gradient) hover and bob over their cradles"), worked out in its frame ({@link DropletHangarShape}: x
 * across, y up from the deck, z back from the front):
 * <ul>
 * <li>Over each of the eight cradles a droplet hovers ({@link #centre}): a teardrop lying level, its round nose
 * towards the front and its point trailing back ({@link #profile}: {@link #LENGTH} long, at most {@link #RADIUS}
 * thick), a mirror whose every face shows the sky or the ground it would reflect towards the camera ({@link #chrome}:
 * the chrome gradient, a bright horizon line between a pale sky and a dark ground). Docked it hovers at
 * {@link #HOVER} and bobs ({@link #BOB}), each in its own rhythm, swaying a little about the vertical; undocked it
 * rests
 * on its socket's top ({@link #REST}), still. A soft glow lies on its cradle under it.</li>
 * <li>The canopy ring over the kiosk (its ring segments dissolve) is a ring of light ({@link #CANOPY_Y}),
 * breathing.</li>
 * </ul>
 * Every motion is a function of the world time alone. The counts are what the renderer draws ({@link #quads},
 * {@link #landmarkQuads}).
 */
public final class DropletLook {

    /** A droplet's length nose to point, its greatest radius, and the power of its taper towards the point. */
    public static final double LENGTH = 1.75, RADIUS = 0.44, TAPER = 0.8;
    /** Its rings along its length and the sides round it, near and mid ({@code 0} far: a glint alone). */
    public static final int[] RINGS = { 8, 3, 0 }, SIDES = { 10, 5, 0 };
    /** Its axis's height docked and resting, its bob (blocks) and the ticks of one bob, and its sway (radians). */
    public static final double HOVER = 2.75, REST = 2.5, BOB = 0.12, SWAY = 0.22;
    public static final int BOB_T = 96, SWAY_T = 900;
    /** The canopy ring: its middle (over the kiosk's controller), height and radius, and its pieces near and mid. */
    public static final double CANOPY_X = DropletHangarShape.CTRL_X + 0.5, CANOPY_Z = DropletHangarShape.CTRL_Z + 0.5,
        CANOPY_Y = DropletHangarShape.RING_Y + 0.5, CANOPY_R = 2.2;
    public static final int[] CANOPY_PIECES = { 24, 8, 8 };

    /** The chrome gradient: the sky's zenith and horizon, the horizon line, the ground near the horizon and below. */
    public static final int SKY_HIGH = 0x8FB4DC, SKY_LOW = 0xEEF5FF, HORIZON = 0xFFFFFF, GROUND_NEAR = 0x2A3440,
        GROUND_LOW = 0x4C5866;

    /** The quads of a glow. */
    public static final int GLOW_QUADS = 1;

    /** The profile's scale, so its widest ring is {@link #RADIUS}. */
    private static final double PROFILE_SCALE;

    static {
        double most = 0;
        for (int i = 0; i <= 1000; i++) most = Math.max(most, rawRadius(Math.PI * i / 1000));
        PROFILE_SCALE = RADIUS / most;
    }

    private DropletLook() {}

    /** The cradles' middles {x, z} in the frame, in unit order. */
    public static double[] cradle(int i) {
        int[] c = DropletHangarShape.CRADLES[i];
        return new double[] { SkyRing.MID + c[0] + 0.5, SkyRing.MID + c[1] + 0.5 };
    }

    /**
     * Where droplet {@code i}'s middle hovers at the world time {@code t}: {x, y, z, sway}, docked bobbing at
     * {@link #HOVER} each in its own rhythm and swaying about the vertical, undocked resting at {@link #REST}.
     */
    public static double[] centre(int i, double t, boolean docked) {
        double[] c = cradle(i);
        if (!docked) return new double[] { c[0], REST, c[1], 0 };
        double phase = i * 0.61803398875 * 2 * Math.PI;
        double y = HOVER + BOB * StrictMath.sin(2 * Math.PI * t / BOB_T + phase);
        double sway = SWAY * StrictMath.sin(2 * Math.PI * t / SWAY_T + phase * 1.7);
        return new double[] { c[0], y, c[1], sway };
    }

    /**
     * The teardrop's profile at {@code phi} from its nose (0) to its point (pi): {along, radius}, {@code along} from
     * the
     * nose 0 to {@link #LENGTH}; a round nose, the widest about a third along, a sharp point.
     */
    public static double[] profile(double phi) {
        return new double[] { LENGTH * (1 - StrictMath.cos(phi)) / 2, PROFILE_SCALE * rawRadius(phi) };
    }

    private static double rawRadius(double phi) {
        return StrictMath.sin(phi) * StrictMath.pow(Math.max(0, (1 + StrictMath.cos(phi)) / 2), TAPER);
    }

    /**
     * A point of droplet {@code i}'s skin: ring {@code k} of {@code rings} (0 the nose, {@code rings} the point), side
     * {@code j} of {@code sides}, the droplet centred at {@code c} ({@link #centre}): {x, y, z, nx, ny, nz} in the
     * frame,
     * its outward normal. The nose looks to the front (-z), turned by its sway.
     */
    public static double[] skin(double[] c, int k, int rings, int j, int sides) {
        double[] out = new double[6];
        place(c, StrictMath.cos(c[3]), StrictMath.sin(c[3]), local(k, rings, j, sides), out);
        return out;
    }

    /**
     * {@link #skin} of a drawn tier's skin into {@code out} without working anything out again (0.16.0): the point
     * {@code (k, j)} of the tier's rings and sides ({@link #RINGS}, {@link #SIDES}) is read from a table made once, and
     * turned by the droplet's sway, whose cosine {@code cs} and sine {@code sn} the caller works out once a droplet.
     * The
     * renderer fills a grid of its own with it, so drawing the hangar makes no arrays a frame.
     *
     * @throws IllegalArgumentException for a tier that draws no skin (far)
     */
    public static void skinInto(double[] c, double cs, double sn, int tier, int k, int j, double[] out) {
        place(c, cs, sn, table(tier)[k][j], out);
    }

    /** The unturned skins of the drawn tiers, made once each: {lx, ly, lz, nx, ny, nz} round the droplet's middle. */
    private static final double[][][][] TABLES = new double[RINGS.length][][][];

    private static double[][][] table(int tier) {
        double[][][] t = TABLES[tier];
        if (t != null) return t;
        int rings = RINGS[tier], sides = SIDES[tier];
        if (rings <= 0) throw new IllegalArgumentException("no skin at tier " + tier);
        t = new double[rings + 1][sides + 1][];
        for (int k = 0; k <= rings; k++) for (int j = 0; j <= sides; j++) t[k][j] = local(k, rings, j, sides);
        // made the same by any thread that gets here first: it is only ever read
        TABLES[tier] = t;
        return t;
    }

    /** A point of the unturned skin round the droplet's middle and its outward normal: {lx, ly, lz, nx, ny, nz}. */
    private static double[] local(int k, int rings, int j, int sides) {
        double phi = Math.PI * k / rings, a = 2 * Math.PI * j / sides;
        double[] p = profile(phi);
        // along the axis from its middle, nose ahead (-z); round it in the across-up plane
        double along = p[0] - LENGTH / 2, ca = StrictMath.cos(a), sa = StrictMath.sin(a);
        double lx = p[1] * ca, ly = p[1] * sa, lz = along;
        // the normal of a surface of revolution: out across the ring, and along the axis by the radius's slope
        double dp = 1e-4, phi2 = Math.min(Math.PI, phi + dp), phi1 = Math.max(0, phi - dp);
        double[] p2 = profile(phi2), p1 = profile(phi1);
        double dAlong = p2[0] - p1[0], dRad = p2[1] - p1[1];
        double nr = dAlong, nz = -dRad, nl = Math.hypot(nr, nz);
        if (nl < 1e-12) {
            nr = 0;
            nz = k == 0 ? -1 : 1;
            nl = 1;
        }
        nr /= nl;
        nz /= nl;
        return new double[] { lx, ly, lz, nr * ca, nr * sa, nz };
    }

    /**
     * The unturned point {@code l} turned by the sway ({@code cs}, {@code sn}) round the droplet's middle {@code c}.
     */
    private static void place(double[] c, double cs, double sn, double[] l, double[] out) {
        double x = l[0] * cs - l[2] * sn, z = l[0] * sn + l[2] * cs, mx = l[3] * cs - l[5] * sn,
            mz = l[3] * sn + l[5] * cs;
        out[0] = c[0] + x;
        out[1] = c[1] + l[1];
        out[2] = c[2] + z;
        out[3] = mx;
        out[4] = l[4];
        out[5] = mz;
    }

    /**
     * The colour a mirror shows where its outward normal is {@code (nx, ny, nz)} and the camera looks along
     * {@code (vx, vy, vz)} at it (both in the world, any length): the sky or the ground the view reflects off it
     * towards, as a chrome gradient: the reflected way's height picks a pale sky darkening to its zenith, a bright line
     * at the horizon, a dark ground under it.
     */
    public static int chrome(double nx, double ny, double nz, double vx, double vy, double vz) {
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz), vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (nl < 1e-12 || vl < 1e-12) return SKY_LOW;
        nx /= nl;
        ny /= nl;
        nz /= nl;
        vx /= vl;
        vy /= vl;
        vz /= vl;
        double d = vx * nx + vy * ny + vz * nz, ry = vy - 2 * d * ny;
        if (ry >= 0.08) return mix(SKY_LOW, SKY_HIGH, Math.min(1, (ry - 0.08) / 0.9));
        if (ry >= 0) return mix(HORIZON, SKY_LOW, ry / 0.08);
        if (ry >= -0.12) return mix(HORIZON, GROUND_NEAR, -ry / 0.12);
        return mix(GROUND_NEAR, GROUND_LOW, Math.min(1, (-ry - 0.12) / 0.88));
    }

    /** The canopy ring's point at angle {@code a}: {x, y, z} in the frame. */
    public static double[] canopy(double a) {
        return new double[] { CANOPY_X + CANOPY_R * StrictMath.cos(a), CANOPY_Y,
            CANOPY_Z + CANOPY_R * StrictMath.sin(a) };
    }

    // ---- the budget

    /**
     * The quads of one droplet at a tier: near and mid every face of its skin (rings by sides; the renderer draws only
     * those turned to the camera, never more), a glint on it and a glow on its cradle; far its glint alone.
     */
    public static int dropletQuads(int tier) {
        return RINGS[tier] * SIDES[tier] + (tier == ModuleLook.FAR ? GLOW_QUADS : 2 * GLOW_QUADS);
    }

    /** The quads of the canopy ring at a tier: its pieces, near each with a halo. */
    public static int canopyQuads(int tier) {
        return CANOPY_PIECES[tier] * (tier == ModuleLook.NEAR ? 2 : 1);
    }

    /**
     * The most quads the hangar's own drawing takes a frame at a tier ({@link ModuleLook#tier}): its eight droplets and
     * the canopy ring. The shared sky drawing ({@link SkyLook#quads}) and the drawing of every city module
     * ({@link ModuleLook#baseQuads}) come on top.
     */
    public static int quads(int tier) {
        return DropletHangarShape.CRADLES.length * dropletQuads(tier) + canopyQuads(tier);
    }

    /** Its landmark past the effects' range draws nothing of its own: the droplets are too small to see so far. */
    public static int landmarkQuads() {
        return 0;
    }

    private static int mix(int a, int b, double f) {
        f = Math.max(0, Math.min(1, f));
        int r = (int) ((a >> 16 & 0xFF) * (1 - f) + (b >> 16 & 0xFF) * f);
        int g = (int) ((a >> 8 & 0xFF) * (1 - f) + (b >> 8 & 0xFF) * f);
        int bl = (int) ((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
        return r << 16 | g << 8 | bl;
    }
}
