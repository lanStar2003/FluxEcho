package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where the Blood Covenant Altar's drawing puts its light (0.13.0, CITY.md §4.4 Visuals), worked out in the altar's
 * own frame ({@link BloodCovenantShape}: x across, y up from the floor's base layer, z from the blueprint's front)
 * round its axis, the middle of the altar's column ({@link #AXIS}):
 * <ul>
 * <li>The blood orb: a red sphere floating over the altar between y12 and y16, in the stepped funnel under the oculus
 * ({@link #ORB_Y}, {@link #orbRadius}), pulsing: a slow breath, or powered a heartbeat ({@link #heartbeat}). Strings of
 * light hang from the oculus's rim down to it, drops of light running down them into the orb ({@link #stringPoint});
 * short drips hang under it ({@link #dripLength}).</li>
 * <li>Docked: red light seeps along the three tiers' cornices (y7, y12 and the roof's at y16): near, a line on each
 * outer face of a cornice ({@link #cornice}); further off, the cornice's octagon ({@link #hull}). Inside, the ritual
 * circle's lit tiles glow red ({@link #ritual}) and every cradle shows a small orb turning over its socket
 * ({@link #cradleOrb}).</li>
 * <li>Powered: a pulse runs out from the dais along the spokes and over the circle at every beat
 * ({@link #pulseRadius}), and droplets fall from the orb onto the altar ({@link #droplet}).</li>
 * </ul>
 * Every motion is a function of the world time alone and repeats with its own period. Pure arithmetic over the shape,
 * so the tests hold the orb and its strings in the funnel's air, the droplets' path clear down to the altar, the
 * cornice lines on the cornices' outsides, the glow on the circle's tiles and the altar within its quad budgets
 * ({@link #quads}).
 */
public final class BloodLook {

    /** The altar's axis across and deep in its frame: the middle of the altar's column. */
    public static final double AXIS = BloodCovenantShape.MID + 0.5;

    // ---- the orb

    /** The orb's middle height and its radius at rest; how much a heartbeat swells it (a share of the radius). */
    public static final double ORB_Y = 14.0, ORB_R = 1.7, ORB_SWELL = 0.08;
    /** The ticks of one heartbeat (powered) and of one slow breath (not). */
    public static final int BEAT_T = 24, BREATH_T = 80;
    /** Where in a beat its two strokes fall (a share of the beat). */
    public static final double LUB = 0.06, DUB = 0.3;
    /** The orb's rings of latitude and its meridians near, mid and far (none far: a glow only). */
    public static final int[] ORB_LAT = { 8, 5, 0 }, ORB_LON = { 12, 8, 0 };

    // ---- the strings and the drips

    /** The strings of light from the oculus's rim to the orb, and their pieces near, mid and far. */
    public static final int STRINGS = 8;
    public static final int[] STRING_PIECES = { 3, 1, 0 };
    /**
     * How far from the axis a string leaves the rim's underside, how far down from the orb's top it meets the orb
     * (radians), and how much it sags in its middle.
     */
    public static final double STRING_R = 2.75, STRING_POLAR = Math.toRadians(70), STRING_SAG = 0.12;
    /** The ticks a drop of light takes down a string, and how long a drip under the orb grows before it falls. */
    public static final int DRIP_T = 60;
    /** The drips hanging under the orb and their longest. */
    public static final int DRIPS = 4;
    public static final double DRIP_LONG = 1.2, DRIP_POLAR = Math.toRadians(160);

    // ---- the cornices

    /** The cornices the light seeps along: the slab at y7, the slab at y12, the roof at y16. */
    public static final int[] CORNICES = { BloodCovenantShape.SLAB1_Y, BloodCovenantShape.SLAB2_Y,
        BloodCovenantShape.ROOF_Y };
    /** Each cornice's octagon {@code oct(h, k)}: the outer rings of the slabs and the roof. */
    private static final int[][] CORNICE_OCT = { { 12, 16 }, { 9, 12 }, { 6, 8 } };
    /** How high on the cornice its light runs (over its block's foot), and how far out its octagon is drawn. */
    public static final double CORNICE_LIGHT = 0.15, HULL_OUT = 0.05;
    /** The ticks the seeping light takes once round, and how many swells run round at once. */
    public static final int SEEP_T = 240, SEEP_SWELLS = 3;

    // ---- the ritual circle and the pulse

    /** The height the ritual circle's glow lies at: a little over the floor's top. */
    public static final double RITUAL_Y = 1.02;
    /** The pulse's radius as it leaves the dais and as it fades past the circle; its half-width. */
    public static final double PULSE_FROM = 2.5, PULSE_TO = 9.5, PULSE_HALF = 1.2;
    /** The spokes the pulse runs out along (the four diagonals). */
    public static final int SPOKES = 4;

    // ---- the cradles

    /** A cradle's orb: its radius, how high over the socket's top its middle floats, and its turn (ticks). */
    public static final double CRADLE_ORB = 0.22, CRADLE_LIFT = 0.45;
    public static final int CRADLE_TURN_T = 200;
    /** A cradle orb's latitudes and meridians, and the pieces of the ring turning round it. */
    public static final int CRADLE_LAT = 4, CRADLE_LON = 6, CRADLE_RING = 6;

    // ---- the droplets

    /** Droplets falling at once from the orb onto the altar, and the ticks one takes: two beats, one let go a beat. */
    public static final int DROPLETS = 2, DROP_T = 2 * BEAT_T;
    /** Where the droplets land: the controller's top, over the altar's console. */
    public static final double ALTAR_TOP = BloodCovenantShape.CTRL_Y + 1;
    /**
     * The middle height of the altar's name plate, which floats on the droplets' axis over the central console
     * ({@link ModuleLook#plates}), and how far each way from it the droplets give way to it: the plate's pane is about
     * 0.23 each way, and the band has room round it for the glow at a droplet's head ({@link #DROP_GLOW}) and for a
     * camera looking up or down at the plate. A droplet fades out over {@link #PLATE_FADE} as it nears the band, is
     * not drawn while any of it lies in it, and fades back in under it, so no streak ever runs across the name.
     */
    public static final double PLATE_Y = ModuleLook.plates(BloodCovenantShape.BUILD)
        .get(0)[1], PLATE_CLEAR = 0.4, PLATE_FADE = 0.3;
    /** How far the glow at a droplet's head reaches round it. */
    public static final double DROP_GLOW = 0.1;

    /** The quads of a glow. */
    public static final int GLOW_QUADS = 1;

    /** The cradles' sockets {x, y, z}, in unit order, and the slot each keeps its orb in. */
    private static final List<int[]> CRADLES;
    private static final int[] CRADLE_SLOTS;
    /** The ritual circle's lit tiles that lie open to the air, {x, z}. */
    private static final List<int[]> RITUAL;

    static {
        List<int[]> c = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        for (UnitLayout.Unit u : BloodCovenantShape.UNITS.units()) {
            if (!u.kind.equals("cradle")) continue;
            c.add(u.sockets.get(0));
            slots.add(u.firstSlot);
        }
        CRADLES = Collections.unmodifiableList(c);
        CRADLE_SLOTS = new int[slots.size()];
        for (int i = 0; i < CRADLE_SLOTS.length; i++) CRADLE_SLOTS[i] = slots.get(i);
        List<int[]> r = new ArrayList<>();
        for (int x = 0; x < BloodCovenantShape.WIDTH; x++) for (int z = 0; z < BloodCovenantShape.DEPTH; z++) {
            if (BloodCovenantShape.cell(x, 0, z) == '+' && BloodCovenantShape.cell(x, 1, z) == ShapeKit.NONE)
                r.add(new int[] { x, z });
        }
        RITUAL = Collections.unmodifiableList(r);
        if (CRADLES.size() != 8) throw new IllegalStateException("the blood covenant's cradles");
    }

    private BloodLook() {}

    // ---- the orb

    /**
     * How strongly the orb beats at the world time {@code t} (ticks), 0 to 1: powered, a heartbeat of two strokes (a
     * strong one at {@link #LUB}, a softer one at {@link #DUB} of each beat); otherwise a slow breath.
     */
    public static double heartbeat(double t, boolean on) {
        if (!on) return 0.5 + 0.5 * Math.sin(2 * Math.PI * t / BREATH_T);
        double ph = ArcaneLook.frac(t / BEAT_T);
        double v = Math.exp(-sq(ph - LUB) / 0.0015) + 0.6 * Math.exp(-sq(ph - DUB) / 0.002);
        return Math.min(1, v);
    }

    /** The orb's radius at the world time {@code t}: swelling with each beat. */
    public static double orbRadius(double t, boolean on) {
        return ORB_R * (1 + ORB_SWELL * heartbeat(t, on));
    }

    /**
     * A point of the orb's surface: latitude {@code i} of {@code lat} (0 the top) and meridian {@code j} of
     * {@code lon}, for a radius {@code r}. {x, y, z} in the altar's frame.
     */
    public static double[] orbPoint(int i, int lat, int j, int lon, double r) {
        return spherePoint(AXIS, ORB_Y, AXIS, r, i, lat, j, lon);
    }

    /**
     * A point of a sphere of radius {@code r} round {@code (x, y, z)}: latitude {@code i} of {@code lat} (0 the top)
     * and meridian {@code j} of {@code lon} (the orb's, and the cradles' small orbs').
     */
    public static double[] spherePoint(double x, double y, double z, double r, int i, int lat, int j, int lon) {
        double polar = Math.PI * i / lat, az = 2 * Math.PI * j / lon;
        return new double[] { x + r * Math.sin(polar) * Math.cos(az), y + r * Math.cos(polar),
            z + r * Math.sin(polar) * Math.sin(az) };
    }

    // ---- the strings

    /** The angle round the axis of string {@code k} (radians): between the axes and the diagonals. */
    public static double stringAngle(int k) {
        return (k + 0.5) * Math.PI / 4;
    }

    /**
     * The point {@code u} of the way down string {@code k} (0 at the rim, 1 on the orb) for an orb of radius
     * {@code r}: {x, y, z} in the altar's frame. It leaves the underside of the oculus's lit rim and meets the orb
     * {@link #STRING_POLAR} down from its top, sagging a little between.
     */
    public static double[] stringPoint(int k, double u, double r) {
        double a = stringAngle(k), ca = Math.cos(a), sa = Math.sin(a);
        double top = BloodCovenantShape.ROOF_Y - 0.02, rTop = STRING_R;
        double rBottom = r * Math.sin(STRING_POLAR), yBottom = ORB_Y + r * Math.cos(STRING_POLAR);
        double rad = rTop + (rBottom - rTop) * u, y = top + (yBottom - top) * u - STRING_SAG * Math.sin(Math.PI * u);
        return new double[] { AXIS + rad * ca, y, AXIS + rad * sa };
    }

    /** How far down its string the drop of light on string {@code k} is at the world time {@code t} (0 to 1). */
    public static double drop(int k, double t) {
        return ArcaneLook.frac(t / DRIP_T + k * 0.37);
    }

    /** Where drip {@code j} hangs from the orb of radius {@code r}: {x, y, z}, low on the orb's underside. */
    public static double[] dripTop(int j, double r) {
        double a = (j + 0.5) * Math.PI / 2;
        return new double[] { AXIS + r * Math.sin(DRIP_POLAR) * Math.cos(a), ORB_Y + r * Math.cos(DRIP_POLAR),
            AXIS + r * Math.sin(DRIP_POLAR) * Math.sin(a) };
    }

    /** How long drip {@code j} hangs at the world time {@code t}: it grows to {@link #DRIP_LONG} and lets go. */
    public static double dripLength(int j, double t) {
        return DRIP_LONG * (0.25 + 0.75 * ArcaneLook.frac(t / DRIP_T + j * 0.25));
    }

    // ---- the cornices

    /**
     * The outer faces of the cornice at layer {@code y} (one of {@link #CORNICES}): {x, z, outX, outZ, s} for every
     * cornice cell ({@code n}) of the layer and every side of it with open air beside it at that layer, {@code s} its
     * place round the altar (0 to n-1, by the angle round the axis from the front middle to the right of someone
     * walking in, as {@link ModuleLook#course} orders the plinth's).
     */
    public static List<int[]> cornice(int y) {
        List<int[]> out = new ArrayList<>();
        List<Double> angle = new ArrayList<>();
        for (int x = 0; x < BloodCovenantShape.WIDTH; x++) for (int z = 0; z < BloodCovenantShape.DEPTH; z++) {
            if (BloodCovenantShape.cell(x, y, z) != 'n') continue;
            for (int[] o : SIDES) {
                if (BloodCovenantShape.cell(x + o[0], y, z + o[1]) != ShapeKit.NONE) continue;
                out.add(new int[] { x, z, o[0], o[1], 0 });
                double fx = x + 0.5 + o[0] * 0.5 - AXIS, fz = z + 0.5 + o[1] * 0.5 - AXIS;
                double a = Math.atan2(fx, -fz);
                angle.add(a < 0 ? a + 2 * Math.PI : a);
            }
        }
        Integer[] order = new Integer[out.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (p, q) -> Double.compare(angle.get(p), angle.get(q)));
        for (int i = 0; i < order.length; i++) out.get(order[i])[4] = i;
        return Collections.unmodifiableList(out);
    }

    private static final int[][] SIDES = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };

    /**
     * The octagon a cornice's outer faces lie on, pushed {@link #HULL_OUT} out: its eight corners {x, z} in order round
     * the altar, in the altar's frame. Its flat sides lie on the flat faces; its chamfers touch the stepped chamfer
     * faces' outer corners, so a line along them never runs inside the cornice.
     */
    public static double[][] hull(int y) {
        int c = -1;
        for (int i = 0; i < CORNICES.length; i++) if (CORNICES[i] == y) c = i;
        if (c < 0) throw new IllegalArgumentException("no cornice at y" + y);
        int h = CORNICE_OCT[c][0], k = CORNICE_OCT[c][1];
        // the flat side x = h + 1/2 and the chamfer x + z = k + 1, each pushed out by HULL_OUT
        double flat = h + 0.5 + HULL_OUT, cham = k + 1 + HULL_OUT * Math.sqrt(2), other = cham - flat;
        double[][] corner = { { flat, other }, { other, flat }, { -other, flat }, { -flat, other }, { -flat, -other },
            { -other, -flat }, { other, -flat }, { flat, -other } };
        double[][] out = new double[8][];
        for (int i = 0; i < 8; i++) out[i] = new double[] { AXIS + corner[i][0], AXIS + corner[i][1] };
        return out;
    }

    /**
     * How bright the seeping light is at the face {@code s} of {@code n} round a cornice at the world time {@code t},
     * 0 to 1: {@link #SEEP_SWELLS} slow swells running round it, the cornice {@code index} (0 the lowest) out of step
     * with the others.
     */
    public static double seep(int s, int n, double t, int index) {
        double ph = 2 * Math.PI * (SEEP_SWELLS * s / (double) n - t / SEEP_T + index * 0.29);
        return 0.5 + 0.5 * Math.sin(ph);
    }

    // ---- the ritual circle

    /** The ritual circle's lit tiles that lie open to the air (not under the dais or a cradle), {x, z}. */
    public static List<int[]> ritual() {
        return RITUAL;
    }

    /**
     * How far out from the axis the heartbeat's pulse has run at the world time {@code t}: it leaves the dais with the
     * strong stroke of each beat and runs out past the circle.
     */
    public static double pulseRadius(double t) {
        double ph = ArcaneLook.frac(t / BEAT_T - LUB);
        return PULSE_FROM + (PULSE_TO - PULSE_FROM) * ph;
    }

    /** How strongly the pulse lights a tile whose middle is {@code r} from the axis at the world time {@code t}. */
    public static double pulse(double r, double t) {
        double d = r - pulseRadius(t);
        double ph = ArcaneLook.frac(t / BEAT_T - LUB);
        return Math.exp(-d * d / (PULSE_HALF * PULSE_HALF)) * (1 - ph);
    }

    /**
     * The glow at the pulse's front on spoke {@code k} (the four diagonals) at the world time {@code t}: {x, y, z,
     * alpha} a little over the floor, fading as the pulse runs out. Where the spoke passes under a cradle's plinth the
     * glow is hidden (alpha 0), so it never shows inside a block.
     */
    public static double[] pulseMote(int k, double t) {
        double r = pulseRadius(t), d = r / Math.sqrt(2);
        double sx = k % 2 == 0 ? 1 : -1, sz = k < 2 ? 1 : -1;
        double x = AXIS + sx * d, z = AXIS + sz * d, y = RITUAL_Y + 0.08;
        double ph = ArcaneLook.frac(t / BEAT_T - LUB), alpha = 1 - ph;
        if (BloodCovenantShape.cell((int) Math.floor(x), 1, (int) Math.floor(z)) != ShapeKit.NONE
            || BloodCovenantShape.cell((int) Math.floor(x), 0, (int) Math.floor(z)) == ShapeKit.NONE) alpha = 0;
        return new double[] { x, y, z, alpha };
    }

    // ---- the cradles

    /** The cradles' sockets {x, y, z}, in unit order. */
    public static List<int[]> cradles() {
        return CRADLES;
    }

    /** The slot cradle {@code i} keeps its orb in. */
    public static int cradleSlot(int i) {
        return CRADLE_SLOTS[i];
    }

    /** Where cradle {@code i}'s orb floats at the world time {@code t}: {x, y, z}, over its socket's top. */
    public static double[] cradleOrb(int i, double t) {
        int[] s = CRADLES.get(i);
        double bob = 0.04 * Math.sin(2 * Math.PI * t / CRADLE_TURN_T * 2 + i);
        return new double[] { s[0] + 0.5, s[1] + 1 + CRADLE_LIFT + bob, s[2] + 0.5 };
    }

    /** How far cradle {@code i}'s orb has turned at the world time {@code t} (radians). */
    public static double cradleTurn(int i, double t) {
        return 2 * Math.PI * ArcaneLook.frac(t / CRADLE_TURN_T + i / 8.0);
    }

    // ---- the droplets

    /**
     * Droplet {@code k} at the world time {@code t}: {y, alpha, streak} on the axis. It leaves the orb's underside with
     * the strong stroke of a beat, falls faster and faster onto the altar's top, and fades as it lands; its streak
     * grows with its speed. It passes behind the name plate: its alpha is 0 while the streak or the glow at its head
     * lies in the plate's band ({@link #PLATE_Y}), fading over {@link #PLATE_FADE} on either side.
     */
    public static double[] droplet(int k, double t) {
        double u = ArcaneLook.frac((t - LUB * BEAT_T) / DROP_T + k / (double) DROPLETS);
        double top = ORB_Y - ORB_R, y = top - (top - ALTAR_TOP) * u * u;
        double alpha = u < 0.85 ? 1 : (1 - u) / 0.15, streak = Math.min(0.1 + 0.7 * u, y - ALTAR_TOP + 0.1);
        return new double[] { y, alpha * clearOfPlate(y - DROP_GLOW, y + streak), streak };
    }

    /**
     * How much of a droplet reaching from {@code lo} up to {@code hi} shows beside the name plate: 0 while it meets the
     * plate's band, rising to 1 at {@link #PLATE_FADE} from it.
     */
    static double clearOfPlate(double lo, double hi) {
        double gap = Math.max(lo - (PLATE_Y + PLATE_CLEAR), PLATE_Y - PLATE_CLEAR - hi);
        return gap <= 0 ? 0 : Math.min(1, gap / PLATE_FADE);
    }

    // ---- the budget

    /**
     * The most quads the altar's own visuals take a frame at a tier ({@link ModuleLook#tier}), formed, docked or not,
     * powered or not, counted as {@code BloodCovenantRender} draws them: the orb (near and mid its sphere, every face
     * counted though the ones turned away are left out, and two glows), its strings (near with a drop each) and, near,
     * its drips (a line and a drop each); docked, the cornices (near a line and a two-quad halo a face, further off the
     * octagons' sides cut into pieces as every line is, {@link DomeLook#pieces}); near and docked, the ritual circle's
     * tiles and the cradles' orbs (a sphere, a ring and a glow each); near and powered, the pulse's motes on the
     * spokes and the droplets (a streak and a glow each).
     */
    public static int quads(int tier, boolean docked, boolean on) {
        boolean nearby = tier == ModuleLook.NEAR;
        int n = ORB_LAT[tier] * ORB_LON[tier] + 2 * GLOW_QUADS + STRINGS * STRING_PIECES[tier];
        if (nearby) n += STRINGS * GLOW_QUADS + DRIPS * (1 + GLOW_QUADS);
        if (docked) for (int y : CORNICES) n += nearby ? cornice(y).size() * 3 : hullQuads(y);
        if (nearby && docked) {
            n += RITUAL.size();
            n += CRADLES.size() * (CRADLE_LAT * CRADLE_LON + CRADLE_RING + GLOW_QUADS);
        }
        if (nearby && on) n += SPOKES * GLOW_QUADS + DROPLETS * (1 + GLOW_QUADS);
        return n;
    }

    /** The quads of a cornice's octagon drawn as lines: each side cut into pieces as every line is. */
    public static int hullQuads(int y) {
        double[][] h = hull(y);
        int n = 0;
        for (int i = 0; i < h.length; i++) {
            double[] a = h[i], b = h[(i + 1) % h.length];
            n += DomeLook.pieces(Math.hypot(b[0] - a[0], b[1] - a[1]));
        }
        return n;
    }

    private static double sq(double v) {
        return v * v;
    }
}
