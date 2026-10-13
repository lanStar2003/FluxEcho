package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where the Mana Fountain Array's drawing puts its light (0.13.0, CITY.md §4.6 Visuals), worked out in the array's
 * own frame ({@link ManaFountainShape}: x across, y up from the floor's base layer, z from the blueprint's front) round
 * its axis, the middle of the obelisk's column ({@link #AXIS}):
 * <ul>
 * <li>The mana spout: once the array forms, the obelisk's conduits and seat give way to a spiral column of motes rising
 * from the controller's top, two strands winding round each other ({@link #spoutMote}); undocked it burns low.</li>
 * <li>Docked: from each fountain's socket a jet of pale-blue light arcs to the spout, drops of light running along it
 * ({@link #jetPoint}); the magic circle in the court's floor turns: its outer circle's light runs round it with its
 * runes ({@link #outerPieces}, {@link #rune}), its inner circle's ticks the other way ({@link #tick}). Where a
 * fountain's
 * basin or an urn stands on the floor the circle passes under it and is not drawn.</li>
 * <li>Powered (near): mana sparkles drift from the spout out through the colonnade's openings into the garden soil of
 * the arcade ring ({@link #sparkle}).</li>
 * </ul>
 * Every motion is a function of the world time alone and repeats with its own period, so a frame drawn again behind a
 * light gate is the same frame. Pure arithmetic over the shape, so the tests hold the jets and the sparkles in the
 * court's air, the spout in the obelisk's dissolved column, the circle on the open floor and the whole array within its
 * quad budgets ({@link #quads}).
 */
public final class ManaLook {

    /** The array's axis across and deep in its frame: the middle of the obelisk's column. */
    public static final double AXIS = ManaFountainShape.MID + 0.5;

    // ---- the spout

    /** Where the spout rises from (over the controller's top) and to (over the seat, under the name plate). */
    public static final double SPOUT_FROM = ManaFountainShape.CTRL_Y + 1.15, SPOUT_TO = 8.6;
    /** How far from the axis its strands wind at its foot (they widen a little as they rise), and their turns. */
    public static final double SPOUT_R = 0.32, SPOUT_TURNS = 2.5;
    /** The ticks a mote takes up the spout, and the motes near, mid and far (far only its glow). */
    public static final int SPOUT_T = 100;
    public static final int[] SPOUT_MOTES = { 32, 12, 0 };

    // ---- the jets

    /** A jet leaves its socket's top here, and meets the spout this far from the axis at this height. */
    public static final double JET_FROM_Y = 3.05, JET_TO_R = 0.3, JET_TO_Y = 5.4;
    /** How high a jet arcs over the straight line from its socket to the spout, and its half-widths at either end. */
    public static final double JET_ARC = 1.6, JET_HALF0 = 0.09, JET_HALF1 = 0.04;
    /** The pieces a jet is drawn in near, mid and far. */
    public static final int[] JET_PIECES = { 20, 6, 2 };
    /** The drops of light running along each jet (near), and the ticks one takes from the socket to the spout. */
    public static final int JET_DROPS = 3, JET_T = 50;

    // ---- the magic circle

    /** The height the circle's light lies at (a little over the floor's top) and its two circles' radii. */
    public static final double CIRCLE_Y = 1.02, OUTER_R = 7.7, INNER_R = 1.9;
    /**
     * The pieces a whole outer and inner circle is cut into near and mid (far neither is drawn): no piece is longer
     * than a whole circle's piece, and a circle is cut where it passes under a block.
     */
    public static final int[] OUTER_PIECES = { 64, 32, 0 }, INNER_PIECES = { 24, 12, 0 };
    /** How finely a circle is looked over for the blocks standing on it (samples once round). */
    private static final int CIRCLE_SAMPLES = 1440;
    /** The ticks of one turn of the outer circle and of the inner one (the other way). */
    public static final int OUTER_TURN_T = 1800, INNER_TURN_T = 1200;
    /** How many bright dashes run round the outer circle as it turns. */
    public static final int DASHES = 8;
    /** The runes riding the outer circle, their strokes, a rune's half-size along the circle and across it. */
    public static final int RUNES = 12, STROKES = 3;
    public static final double RUNE_ALONG = 0.22, RUNE_ACROSS = 0.18;
    /** The ticks on the inner circle: how many, and from and to how far out each reaches. */
    public static final int TICKS = 8;
    public static final double TICK_IN = 1.55, TICK_OUT = 2.25;
    /**
     * The runes' strokes, three a rune, {along0, across0, along1, across1} in a rune's own square (-1..1 each way): an
     * alphabet of six, each rune of the circle one of them.
     */
    private static final double[][][] ALPHABET = { { { -1, -1, 0, 1 }, { 0, 1, 1, -1 }, { -0.5, 0, 0.5, 0 } },
        { { 0, -1, 0, 1 }, { -1, 0.5, 0, 1 }, { 1, 0.5, 0, 1 } },
        { { -1, 1, 1, 1 }, { 1, 1, -1, -1 }, { -1, -1, 1, -1 } },
        { { -1, -1, -1, 1 }, { -1, 0, 1, 1 }, { -1, 0, 1, -1 } },
        { { 0, -1, 0, 1 }, { -1, -0.3, 1, -0.3 }, { -0.6, 0.5, 0.6, 0.5 } },
        { { -1, 1, 0, -1 }, { 0, -1, 1, 1 }, { -1, 0.2, 1, 0.2 } } };

    // ---- the sparkles

    /** The sparkles drifting at once (near, powered), and the ticks one takes from the spout to the garden. */
    public static final int SPARKLES = 16, SPARKLE_T = 220;
    /** Where a sparkle leaves the spout (how far from the axis, how high), and where it settles over the soil. */
    public static final double SPARKLE_FROM_R = 0.45, SPARKLE_FROM_Y = 6.0, SPARKLE_TO_Y = 1.35;
    /** The share of its way at which a sparkle passes the colonnade's opening. */
    public static final double SPARKLE_GAP_AT = 0.75;

    /** The quads of a glow. */
    public static final int GLOW_QUADS = 1;

    /** The fountains' and the urns' sockets {x, y, z}, in unit order. */
    private static final List<int[]> FOUNTAINS, URNS;
    /** The outer and inner circles' pieces that lie on the open floor near and mid: {from, to} angles each. */
    private static final List<double[]>[] OUTER_OPEN, INNER_OPEN;
    /** The sparkles' ways out: {gapX, gapZ, gardenX, gardenZ} (cells) each. */
    private static final List<int[]> WAYS;

    static {
        List<int[]> f = new ArrayList<>(), u = new ArrayList<>();
        for (UnitLayout.Unit unit : ManaFountainShape.UNITS.units()) {
            if (unit.kind.equals("fountain")) f.add(unit.sockets.get(0));
            else if (unit.kind.equals("urn")) u.add(unit.sockets.get(0));
        }
        FOUNTAINS = Collections.unmodifiableList(f);
        URNS = Collections.unmodifiableList(u);
        if (FOUNTAINS.size() != 8 || URNS.size() != 8) throw new IllegalStateException("the mana fountain's units");
        @SuppressWarnings("unchecked")
        List<double[]>[] outer = new List[2], inner = new List[2];
        for (int tier = 0; tier < 2; tier++) {
            outer[tier] = openPieces(OUTER_R, OUTER_PIECES[tier]);
            inner[tier] = openPieces(INNER_R, INNER_PIECES[tier]);
        }
        OUTER_OPEN = outer;
        INNER_OPEN = inner;
        WAYS = Collections.unmodifiableList(ways());
    }

    private ManaLook() {}

    /** The fountains' sockets {x, y, z}, in unit order. */
    public static List<int[]> fountains() {
        return FOUNTAINS;
    }

    /** The urns' sockets {x, y, z}, in unit order. */
    public static List<int[]> urns() {
        return URNS;
    }

    // ---- the spout

    /**
     * Mote {@code k} of {@code n} of the spout at the world time {@code t}: {x, y, z, alpha} in the array's frame. The
     * motes lie on two strands winding round the axis, each mote rising along its strand from {@link #SPOUT_FROM} to
     * {@link #SPOUT_TO} every {@link #SPOUT_T} ticks, fading in at the foot and out at the top.
     */
    public static double[] spoutMote(int k, int n, double t) {
        int strands = 2, per = Math.max(1, n / strands), strand = k % strands, i = k / strands;
        double u = ArcaneLook.frac(t / SPOUT_T + i / (double) per + strand * 0.5 / per);
        double y = SPOUT_FROM + (SPOUT_TO - SPOUT_FROM) * u;
        double a = 2 * Math.PI * (SPOUT_TURNS * u + strand * 0.5);
        double r = SPOUT_R * (0.8 + 0.5 * u);
        return new double[] { AXIS + r * Math.cos(a), y, AXIS + r * Math.sin(a), Math.pow(Math.sin(Math.PI * u), 0.7) };
    }

    // ---- the jets

    /**
     * The point {@code u} of the way along fountain {@code i}'s jet (0 over its socket, 1 at the spout): {x, y, z} in
     * the array's frame, on an arc {@link #JET_ARC} over the straight line between.
     */
    public static double[] jetPoint(int i, double u) {
        int[] s = FOUNTAINS.get(i);
        double sx = s[0] + 0.5, sz = s[2] + 0.5;
        double dx = sx - AXIS, dz = sz - AXIS, l = Math.sqrt(dx * dx + dz * dz);
        double ex = AXIS + dx / l * JET_TO_R, ez = AXIS + dz / l * JET_TO_R;
        double y = JET_FROM_Y + (JET_TO_Y - JET_FROM_Y) * u + JET_ARC * 4 * u * (1 - u);
        return new double[] { sx + (ex - sx) * u, y, sz + (ez - sz) * u };
    }

    /** A jet's half-width at the share {@code u} of its way: thinning from its socket to the spout. */
    public static double jetHalf(double u) {
        return JET_HALF0 + (JET_HALF1 - JET_HALF0) * u;
    }

    /** How far along fountain {@code i}'s jet its drop of light {@code k} is at the world time {@code t} (0 to 1). */
    public static double jetDrop(int i, int k, double t) {
        return ArcaneLook.frac(t / JET_T + k / (double) JET_DROPS + i * 0.11);
    }

    // ---- the magic circle

    /** How far the outer circle has turned at {@code t} (radians); the inner one turns the other way. */
    public static double outerTurn(double t) {
        return 2 * Math.PI * ArcaneLook.frac(t / OUTER_TURN_T);
    }

    /** How far the inner circle has turned at {@code t} (radians, against the outer one's way). */
    public static double innerTurn(double t) {
        return -2 * Math.PI * ArcaneLook.frac(t / INNER_TURN_T);
    }

    /** A point of a circle round the axis: {x, z} at the angle {@code a} and radius {@code r}. */
    public static double[] onCircle(double r, double a) {
        return new double[] { AXIS + r * Math.cos(a), AXIS + r * Math.sin(a) };
    }

    /**
     * The pieces of the outer circle drawn at a tier ({@link ModuleLook#NEAR} or {@code MID}): {from, to} angles
     * (radians) each, together the stretches of the circle that lie on the open floor, each stretch cut into pieces no
     * longer than a whole circle's {@link #OUTER_PIECES} would be. The pieces stay put; the light runs round them.
     */
    public static List<double[]> outerPieces(int tier) {
        return OUTER_OPEN[tier];
    }

    /** The pieces of the inner circle drawn at a tier, as {@link #outerPieces}. */
    public static List<double[]> innerPieces(int tier) {
        return INNER_OPEN[tier];
    }

    /**
     * The open stretches of the circle of radius {@code r} cut into pieces of at most a {@code n}th of the way round:
     * looked over at {@link #CIRCLE_SAMPLES} angles, each stretch running from its first open angle to its last, so
     * its ends and every angle between lie over the open floor (a block on the floor is wider than a sample's step).
     */
    private static List<double[]> openPieces(double r, int n) {
        int m = CIRCLE_SAMPLES;
        boolean[] open = new boolean[m];
        int shut = -1;
        for (int i = 0; i < m; i++) {
            double[] p = onCircle(r, 2 * Math.PI * i / m);
            open[i] = openFloor(p[0], p[1]);
            if (!open[i] && shut < 0) shut = i;
        }
        List<double[]> stretches = new ArrayList<>();
        if (shut < 0) stretches.add(new double[] { 0, 2 * Math.PI });
        else {
            // walk once round from a shut angle, so no stretch is split where the angles wrap
            int from = -1;
            for (int k = 1; k <= m; k++) {
                int i = shut + k;
                boolean o = open[i % m];
                if (o && from < 0) from = i;
                if (!o && from >= 0) {
                    stretches.add(new double[] { 2 * Math.PI * from / m, 2 * Math.PI * (i - 1) / m });
                    from = -1;
                }
            }
        }
        List<double[]> out = new ArrayList<>();
        double most = 2 * Math.PI / n;
        for (double[] st : stretches) {
            int pieces = Math.max(1, (int) Math.ceil((st[1] - st[0]) / most - 1e-9));
            for (int j = 0; j < pieces; j++) out
                .add(new double[] { st[0] + (st[1] - st[0]) * j / pieces, st[0] + (st[1] - st[0]) * (j + 1) / pieces });
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * How bright the outer circle is at the angle {@code a} at the world time {@code t}, 0 to 1: {@link #DASHES} bright
     * dashes running round it as it turns.
     */
    public static double dash(double a, double t) {
        return 0.5 + 0.5 * Math.cos(DASHES * (a - outerTurn(t)));
    }

    /** Which letter of the alphabet rune {@code i} of the circle is (the same for every array). */
    public static int runeLetter(int i) {
        return (int) (Mix.unit(0x3A7AL, i) * ALPHABET.length);
    }

    /**
     * Rune {@code i}'s strokes at the world time {@code t}: {x0, z0, x1, z1} each in the frame, riding the outer
     * circle as it turns; null when any end of them lies over a block standing on the floor (a basin rim, an urn),
     * which the rune passes under.
     */
    public static double[][] rune(int i, double t) {
        double mid = 2 * Math.PI * i / RUNES + outerTurn(t);
        double[][] out = new double[STROKES][];
        for (int k = 0; k < STROKES; k++) {
            double[] st = ALPHABET[runeLetter(i)][k];
            double[] a = runePoint(mid, st[0], st[1]), b = runePoint(mid, st[2], st[3]);
            if (!openFloor(a[0], a[1]) || !openFloor(b[0], b[1])) return null;
            out[k] = new double[] { a[0], a[1], b[0], b[1] };
        }
        return out;
    }

    /** A point of a rune's square ({@code along}, {@code across}, -1..1) round the angle {@code mid}: {x, z}. */
    private static double[] runePoint(double mid, double along, double across) {
        double r = OUTER_R + across * RUNE_ACROSS, a = mid + along * RUNE_ALONG / OUTER_R;
        return onCircle(r, a);
    }

    /** Tick {@code j} of the inner circle at the world time {@code t}: {x0, z0, x1, z1}, a short spoke across it. */
    public static double[] tick(int j, double t) {
        double a = 2 * Math.PI * j / TICKS + innerTurn(t);
        double[] p = onCircle(TICK_IN, a), q = onCircle(TICK_OUT, a);
        return new double[] { p[0], p[1], q[0], q[1] };
    }

    /**
     * Whether the court's floor at the point {@code (x, z)} lies open: a soft floor tile under it and nothing standing
     * on it (a fountain's basin and its water, an urn, a post or the obelisk's console stand on theirs).
     */
    public static boolean openFloor(double x, double z) {
        int cx = (int) Math.floor(x), cz = (int) Math.floor(z);
        char floor = ManaFountainShape.cell(cx, 0, cz);
        return ShapeKit.soft(floor) && floor != ShapeKit.NONE && ManaFountainShape.cell(cx, 1, cz) == ShapeKit.NONE;
    }

    // ---- the sparkles

    /**
     * The sparkles' ways out of the court: through every opening between the colonnade's posts on its four flat sides
     * (the cells of the colonnade's ring with nothing on them under the architrave) into the garden soil beyond, one
     * cell straight out or two out and one aside: {gapX, gapZ, gardenX, gardenZ} each.
     */
    public static List<int[]> sparkleWays() {
        return WAYS;
    }

    private static List<int[]> ways() {
        List<int[]> out = new ArrayList<>();
        int c = ManaFountainShape.MID;
        for (int x = 0; x < ManaFountainShape.WIDTH; x++) for (int z = 0; z < ManaFountainShape.DEPTH; z++) {
            int du = x - c, dv = z - c;
            if (!ManaFountainShape.onColonnade(x, z) || Math.abs(du) != 9 && Math.abs(dv) != 9) continue;
            boolean open = true;
            for (int y = 1; y < ManaFountainShape.ARCHITRAVE_Y; y++)
                open &= ManaFountainShape.cell(x, y, z) == ShapeKit.NONE;
            if (!open) continue;
            int nx = Math.abs(du) == 9 ? Integer.signum(du) : 0, nz = Math.abs(dv) == 9 ? Integer.signum(dv) : 0;
            for (int out1 = 1; out1 <= 2; out1++) for (int side = -(out1 - 1); side <= out1 - 1; side++) {
                int gx = x + nx * out1 + nz * side, gz = z + nz * out1 + nx * side;
                if (ManaFountainShape.cell(gx, 0, gz) != ',' || ManaFountainShape.cell(gx, 1, gz) != ShapeKit.NONE)
                    continue;
                out.add(new int[] { x, z, gx, gz });
            }
        }
        return out;
    }

    /**
     * Sparkle {@code k} at the world time {@code t}: {x, y, z, alpha} in the array's frame. It leaves the spout high,
     * sinks as it drifts out to one of the colonnade's openings and on into the garden, where it settles over the soil
     * and fades; every {@link #SPARKLE_T} ticks it starts again on another way.
     */
    public static double[] sparkle(int k, double t) {
        double clock = t / SPARKLE_T + k / (double) SPARKLES;
        long cycle = (long) Math.floor(clock);
        double u = clock - cycle;
        int[] w = WAYS.get((int) (Mix.unit(0x5A9CL, k, cycle) * WAYS.size()));
        double[] p = sparklePath(w, u);
        double alpha = Math.min(1, u / 0.1) * (u > 0.85 ? (1 - u) / 0.15 : 1);
        return new double[] { p[0], p[1], p[2], alpha };
    }

    /**
     * The point {@code u} of the way out along a sparkle's way {@code w} ({@link #sparkleWays}): {x, y, z}. Across, a
     * straight line from the spout to the opening's middle (reached at {@link #SPARKLE_GAP_AT}) and on to the garden
     * cell's middle; up, sinking from {@link #SPARKLE_FROM_Y} to {@link #SPARKLE_TO_Y}, slowly at first.
     */
    public static double[] sparklePath(int[] w, double u) {
        double gx = w[0] + 0.5, gz = w[1] + 0.5, tx = w[2] + 0.5, tz = w[3] + 0.5;
        double dx = gx - AXIS, dz = gz - AXIS, l = Math.sqrt(dx * dx + dz * dz);
        double sx = AXIS + dx / l * SPARKLE_FROM_R, sz = AXIS + dz / l * SPARKLE_FROM_R;
        double x, z;
        if (u <= SPARKLE_GAP_AT) {
            double s = u / SPARKLE_GAP_AT;
            x = sx + (gx - sx) * s;
            z = sz + (gz - sz) * s;
        } else {
            double s = (u - SPARKLE_GAP_AT) / (1 - SPARKLE_GAP_AT);
            x = gx + (tx - gx) * s;
            z = gz + (tz - gz) * s;
        }
        double y = SPARKLE_FROM_Y - (SPARKLE_FROM_Y - SPARKLE_TO_Y) * Math.pow(u, 1.6);
        return new double[] { x, y, z };
    }

    // ---- the budget

    /**
     * The most quads the array's own visuals take a frame at a tier ({@link ModuleLook#tier}), formed, docked or not,
     * powered or not, counted as {@code ManaFountainRender} draws them: the spout (its motes, near and mid, and its
     * glow); docked, the jets (near each piece a band and a halo and the drops; further off one band a piece) and, near
     * and mid, the magic circle (the outer circle's open pieces, near its runes, the inner circle's open pieces, near
     * its ticks); near and powered, the sparkles.
     */
    public static int quads(int tier, boolean docked, boolean on) {
        boolean nearby = tier == ModuleLook.NEAR;
        int n = SPOUT_MOTES[tier] + GLOW_QUADS;
        if (docked) {
            n += FOUNTAINS.size() * JET_PIECES[tier] * (nearby ? 2 : 1);
            if (nearby) n += FOUNTAINS.size() * JET_DROPS;
            if (tier != ModuleLook.FAR) n += OUTER_OPEN[tier].size() + INNER_OPEN[tier].size();
            if (nearby) n += RUNES * STROKES + TICKS;
        }
        if (nearby && on) n += SPARKLES;
        return n;
    }
}
