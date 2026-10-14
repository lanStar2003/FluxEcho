package com.fluxecho.logic;

/**
 * Where the Sky Sail Array's drawing puts its light (0.16.0, CITY.md §4.12 Visuals: "three huge rendered light sails
 * spread between the pairs, fluttering, turned toward the sun, with light streaming in from the sky (gold)"), worked
 * out in its frame ({@link SkySailsShape}: x across, y up from the deck, z back from the front):
 * <ul>
 * <li>Three sails of gold light, one between each pair of masts ({@link #SAILS}: the pairs at {@code (+-6, 0)},
 * {@code (+-6, 5)}, {@code (+-3, 8)}), each hanging from a yard between its two masts' tops as they stand in the world
 * ({@link #yard}: over high ground the masts clip at the world's top and the yard comes down with them; when too
 * little of them is left the sail keeps its least height over the sockets and is drawn past the top), down towards
 * the sockets, never lower than {@link #FOOT_MIN}.</li>
 * <li>Each turns towards the sun about its yard (by day; towards the moon by night, fainter): the sail's face looks as
 * nearly at the light as a turn about the yard allows ({@link #tilt}), at most {@link #TILT_MAX} from hanging straight
 * down, easing back to hanging when the light comes along the yard, and never further than the room it has before its
 * cloth would meet a neighbouring pair's masts or the hut ({@link #room}: the front sail and the middle one turn only a
 * little, the back one all the way). It bellies away from the light, the light pressing on it, and flutters
 * ({@link #point}).</li>
 * <li>Gold light streams in from the sky along the light's way onto each sail ({@link #RAYS} rays), and the sails'
 * edges burn brighter.</li>
 * </ul>
 * Every motion is a function of the world time and the sky's angle alone, so a frame drawn again behind a light gate is
 * the same frame. The counts are what the renderer draws ({@link #quads}, {@link #landmarkQuads}).
 */
public final class SailLook {

    /** The sails: each between masts {@code 2s} and {@code 2s + 1} of {@link SkySailsShape#MASTS}. */
    public static final int SAILS = SkySailsShape.MASTS.length / 2;
    /** How far in from each mast's axis a sail starts, along its yard. */
    public static final double INSET = 0.3;
    /** How far a sail hangs at most, the lowest its foot comes, and the least height it keeps. */
    public static final double DROP = 10.2, FOOT_MIN = 3.4, MIN_H = 2.4;
    /** The most a sail turns from hanging straight down (radians), and below what share of light along it it eases. */
    public static final double TILT_MAX = 0.96, EASE = 0.35;
    /** How far a sail bellies and flutters (blocks), and the ticks of its flutter. */
    public static final double BELLY = 0.8, FLUTTER = 0.22;
    public static final int FLUTTER_T = 52;
    /** The grid a sail is drawn in near, mid and far: {across, down}. */
    public static final int[][] GRID = { { 8, 6 }, { 4, 3 }, { 1, 1 } };
    /** The rays of light streaming onto a sail near, and their length. */
    public static final int RAYS = 3;
    public static final double RAY_LEN = 14;
    /** How bright the moon makes the sails by night, as a share of the sun by day. */
    public static final double MOONLIGHT = 0.35;
    /** The highest y a block can stand at. */
    private static final int WORLD_TOP = 255;

    /** The quads of a glow. */
    public static final int GLOW_QUADS = 1;

    /**
     * The step the room each sail has to turn is looked for in (radians), the samples across and down a sail it is
     * checked at, and how far the cloth may stand off the sail's plane (its belly and flutter at their most).
     */
    private static final double ROOM_STEP = 0.01, CLOTH = BELLY + 1.5 * FLUTTER;
    private static final int ROOM_SAMPLES = 24;
    /**
     * How far each sail may turn either way before its cloth would meet a block of the array (a neighbouring pair's
     * masts, the hut): {least, most} (radians), worked out once from the array's shape.
     */
    private static final double[][] ROOM = new double[SAILS][];

    static {
        for (int s = 0; s < SAILS; s++) ROOM[s] = new double[] { -room(s, -1), room(s, 1) };
    }

    private SailLook() {}

    /** The x of the two ends of sail {@code s}'s yard and its z (the pair's masts stand at the same z). */
    public static double[] span(int s) {
        int[] a = SkySailsShape.MASTS[2 * s], b = SkySailsShape.MASTS[2 * s + 1];
        double xa = SkyRing.MID + Math.min(a[0], b[0]) + 0.5 + INSET,
            xb = SkyRing.MID + Math.max(a[0], b[0]) + 0.5 - INSET;
        return new double[] { xa, xb, SkyRing.MID + a[1] + 0.5 };
    }

    /**
     * The height of the sails' yard in the frame over a deck at world height {@code deck}: the top of the highest mast
     * cell in the world (the crown's top, y14, or lower where the world's top clips the masts), but never so low the
     * sail would have less than {@link #MIN_H} over {@link #FOOT_MIN}: there it is drawn past the world's top.
     */
    public static double yard(int deck) {
        double top = Math.min(SkySailsShape.CROWN_Y + 1, WORLD_TOP + 1 - deck);
        return Math.max(top, FOOT_MIN + MIN_H);
    }

    /** How far a sail hangs from a yard at height {@code yard}: {@link #DROP}, no lower than {@link #FOOT_MIN}. */
    public static double height(double yard) {
        return Math.max(MIN_H, Math.min(DROP, yard - FOOT_MIN));
    }

    /**
     * The sky's light at a celestial angle (the world's, 0 at noon): {x, y, z, strength} with (x, y, z) the way
     * towards the sun along the world's axes while it is up (it rises in the east, +x), else towards the moon,
     * strength 1 by day, {@link #MOONLIGHT} by night, easing over the horizon.
     */
    public static double[] light(double celestial) {
        double a = 2 * Math.PI * celestial;
        double x = -StrictMath.sin(a), y = StrictMath.cos(a);
        boolean day = y >= 0;
        double up = Math.abs(y), strength = (day ? 1 : MOONLIGHT) * Math.min(1, 0.25 + up * 3);
        return day ? new double[] { x, y, 0, strength } : new double[] { -x, -y, 0, strength };
    }

    /**
     * How far a sail turns about its yard (radians, 0 hanging straight down, positive swinging its foot towards +z) to
     * face the light, whose way in the frame is {@code (ly, lz)} up and back: the face's normal along the light's
     * share square to the yard, within {@link #TILT_MAX}, easing back to hanging as that share fades (the light coming
     * along the yard).
     */
    public static double tilt(double ly, double lz) {
        double p = Math.hypot(ly, lz);
        if (p < 1e-9) return 0;
        double raw = StrictMath.atan2(lz >= 0 ? ly : -ly, Math.abs(lz));
        double ease = Math.min(1, p / EASE);
        return Math.max(-TILT_MAX, Math.min(TILT_MAX, raw * ease * ease * (3 - 2 * ease)));
    }

    /**
     * How far sail {@code s} turns to face the light ({@link #tilt(double, double)}) within the room it has to turn
     * ({@link #room}): the sails hang close together (their pairs 5 and 3 blocks apart, the hut in front), so each
     * turns only as far as its cloth stays clear of its neighbours' masts and the hut.
     */
    public static double tilt(int s, double ly, double lz) {
        double t = tilt(ly, lz);
        return Math.max(ROOM[s][0], Math.min(ROOM[s][1], t));
    }

    /** The room sail {@code s} has to turn: {least, most} tilt (radians, the least at most 0, the most at least 0). */
    public static double[] room(int s) {
        return ROOM[s].clone();
    }

    /**
     * How far (radians, from 0) sail {@code s} may turn towards {@code dir} (+1 or -1) before its cloth meets a block.
     */
    private static double room(int s, int dir) {
        double best = 0;
        for (int k = 1; k * ROOM_STEP <= TILT_MAX + 1e-9; k++) {
            if (!clear(s, dir * k * ROOM_STEP)) break;
            best = k * ROOM_STEP;
        }
        return best;
    }

    /**
     * Whether sail {@code s} turned {@code tilt}, at its full drop from the crown's top, lies clear of every block of
     * the array but its own masts: sampled across and down it and on both sides of its plane as far as the cloth may
     * stand off it (none at its two sides, which stay on the masts).
     */
    private static boolean clear(int s, double tilt) {
        double yard = SkySailsShape.CROWN_Y + 1, drop = height(yard);
        double[] sp = span(s);
        int[] a = SkySailsShape.MASTS[2 * s], b = SkySailsShape.MASTS[2 * s + 1];
        double c = StrictMath.cos(tilt), sn = StrictMath.sin(tilt);
        for (int i = 0; i <= ROOM_SAMPLES; i++) for (int j = 0; j <= ROOM_SAMPLES; j++) for (int k = -2; k <= 2; k++) {
            double u = i / (double) ROOM_SAMPLES, v = j / (double) ROOM_SAMPLES;
            double off = k / 2.0 * CLOTH * StrictMath.sin(Math.PI * u), down = v * drop;
            int x = (int) Math.floor(sp[0] + (sp[1] - sp[0]) * u), y = (int) Math.floor(yard - down * c + sn * off),
                z = (int) Math.floor(sp[2] + down * sn + c * off);
            boolean own = z == SkyRing.MID + a[1] && (x == SkyRing.MID + a[0] || x == SkyRing.MID + b[0]);
            if (own) continue;
            char ch = SkySailsShape.BUILD.cell(x, y, z);
            if (ch != ShapeKit.NONE && ch != ShapeKit.AIR) return false;
        }
        return true;
    }

    /**
     * Which side of a sail turned {@code tilt} faces the light: 1 when its normal {@code (sin tilt, cos tilt)} (up,
     * back) looks towards {@code (ly, lz)}, else -1.
     */
    public static int lit(double tilt, double ly, double lz) {
        return StrictMath.sin(tilt) * ly + StrictMath.cos(tilt) * lz >= 0 ? 1 : -1;
    }

    /**
     * A point of sail {@code s} at {@code (u, v)} (across its yard 0..1, down it 0..1) at the world time {@code t}:
     * {x, y, z}, the sail hanging {@code drop} from its yard at {@code yard}, turned {@code tilt}, bellied away from
     * the
     * light ({@code side}, {@link #lit}) and fluttering. The yard's ends and the sail's two sides stay on their
     * lines; the belly and the flutter grow from the edges.
     */
    public static double[] point(int s, double u, double v, double yard, double drop, double tilt, int side, double t) {
        double[] sp = span(s);
        double c = StrictMath.cos(tilt), sn = StrictMath.sin(tilt);
        double across = StrictMath.sin(Math.PI * u);
        double belly = -side * BELLY * across * StrictMath.sin(Math.PI * Math.min(1, v * 1.1));
        double flutter = FLUTTER * across
            * (v * StrictMath.sin(2 * Math.PI * (u * 1.5 - t / FLUTTER_T) + s * 1.9)
                + 0.5 * v * v * StrictMath.sin(2 * Math.PI * (v * 1.2 - t / (FLUTTER_T * 0.7)) + u * 3));
        double off = belly + flutter, down = v * drop;
        return new double[] { sp[0] + (sp[1] - sp[0]) * u, yard - down * c + sn * off, sp[2] + down * sn + c * off };
    }

    /**
     * How bright a sail is (0 to 1) when its face turned {@code tilt} meets light of {@code strength} coming
     * {@code (lx, ly, lz)} in the frame: dim when the light glances off it, full face-on.
     */
    public static double glow(double tilt, double lx, double ly, double lz, double strength) {
        double face = Math.abs(StrictMath.sin(tilt) * ly + StrictMath.cos(tilt) * lz)
            / Math.max(1e-9, Math.sqrt(lx * lx + ly * ly + lz * lz));
        return strength * (0.35 + 0.65 * face);
    }

    // ---- the budget

    /** The quads of a line {@code length} long, in the 8-block pieces the renderer cuts it into. */
    private static int line(double length) {
        return DomeLook.pieces(length);
    }

    /**
     * The most quads one sail takes at a tier: its grid's cells, its edges (the yard, the foot and its two sides, each
     * cut where the grid is, near with a halo), near its rays of light in two pieces each. A far sail's edges are whole
     * lines, each in its pieces: the yard as long as the span, the sides at most {@link #DROP}, the foot the span and
     * the most the belly and flutter can bend it.
     */
    public static int sailQuads(int s, int tier) {
        int nx = GRID[tier][0], ny = GRID[tier][1];
        int n = nx * ny;
        if (tier == ModuleLook.FAR) {
            double[] sp = span(s);
            double w = sp[1] - sp[0];
            return n + line(w) + line(w + 2 * (BELLY + FLUTTER * 1.5)) + 2 * line(DROP);
        }
        int edges = 2 * nx + 2 * ny;
        return n + (tier == ModuleLook.NEAR ? 2 * edges + 2 * RAYS : edges);
    }

    /**
     * The most quads the array's own drawing takes a frame at a tier ({@link ModuleLook#tier}): its three sails and,
     * near, a glow on each mast's top. The shared sky drawing ({@link SkyLook#quads}) and the drawing of every city
     * module ({@link ModuleLook#baseQuads}) come on top.
     */
    public static int quads(int tier) {
        int n = 0;
        for (int s = 0; s < SAILS; s++) n += sailQuads(s, tier);
        if (tier == ModuleLook.NEAR) n += SkySailsShape.MASTS.length * GLOW_QUADS;
        return n;
    }

    /** The most quads its landmark takes past the effects' range: the sails as far. */
    public static int landmarkQuads() {
        return quads(ModuleLook.FAR);
    }
}
