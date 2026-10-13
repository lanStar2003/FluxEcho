package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the Sophon Array's drawing puts its light (0.15.0, CITY.md §4.10 Visuals), worked out in the array's frame
 * ({@link SophonArrayShape}: x across, y up from the base layer, z back from the blueprint front) round the lattice
 * sphere's middle ({@link #CX}, {@link #CY}, {@link #CZ}):
 * <ul>
 * <li>Formed (its struts and lattice dissolve): the lattice becomes a rendered geodesic frame of thin bright struts, a
 * subdivided icosahedron on the sphere of radius 12 ({@link #frame}), and four bright struts carry it from the
 * podium's roof ({@link #struts}); in its middle a light point burns, low while undocked.</li>
 * <li>Powered (active): the point unfolds into a turning icosahedron ({@link #ico}, {@link #unfold}), and then a flat
 * circuit plane spreads over the sky above the building ({@link #CANOPY_Y} over the base, radius
 * {@link #CANOPY_R}): a grid of faint tiles ({@link #tiles}), a die in the middle, sixteen traces running out from it
 * with a 45 degree jog each to a via ({@link #traces}), a ring bus crossing them ({@link #bus}), and a pulse of light
 * flowing out along every trace ({@link #pulse}); it fades towards its rim ({@link #radialFade}) and with distance
 * ({@link #landmarkFade}). "A 2D circuit covering the sky".</li>
 * <li>Docked, a soft column of light rises from the central console up through the oculus to the point
 * ({@link #BEAM_FROM}), so one under the glass looks up along it.</li>
 * <li>A landmark (CITY.md §1.3 "sophon canopy"): the frame, its struts, the point or the icosahedron and the canopy
 * are drawn to {@link #LANDMARK} blocks, fading out over the last {@link #LANDMARK_FADE}, never more than
 * {@link #BUDGET} quads at any distance ({@link #landmarkQuads}); within the effects' range they are the same drawing,
 * so the two meet without a seam. The frame drops to the plain icosahedron's edges past {@link #FRAME_FAR}.</li>
 * </ul>
 * Every motion is a function of the world time alone, and the unfolding of the time since the array was last heard to
 * turn on or off, so a frame drawn again behind a light gate is the same frame. The geometry is built once from integer
 * steps and {@link StrictMath}, so the tests hold the canopy, the frame and the struts where they belong and every
 * count to what the renderer draws ({@link #quads}, {@link #landmarkQuads}).
 */
public final class SophonLook {

    /**
     * The lattice sphere's middle (the middle of its centre cell, CITY.md §4.10 "radius 12, centre y20") and radius.
     */
    public static final double CX = SophonArrayShape.MID + 0.5, CY = SophonArrayShape.SPHERE_Y + 0.5,
        CZ = SophonArrayShape.MID + 0.5, RADIUS = SophonArrayShape.SPHERE_R;
    /** Where the struts stand: on the podium's roof (the top of its layer y6). */
    public static final double STRUT_FOOT = SophonArrayShape.ROOF_Y + 1;
    /**
     * The geodesic frame's subdivision near and far, and from how far (blocks from the middle) the far one is drawn.
     */
    public static final int FREQ = 2, FAR_FREQ = 1;
    public static final double FRAME_FAR = 192;

    /** The icosahedron the point unfolds into: its radius, the ticks of one turn, its tilt (radians). */
    public static final double ICO_R = 3.2, ICO_TILT = 0.4;
    public static final int ICO_TURN_T = 360;
    /** How long (ms) the point takes to unfold into the icosahedron and the canopy to spread, and to fold back. */
    public static final long UNFOLD_MS = 4000, FOLD_MS = 1500;
    /** The share of the unfolding the icosahedron takes; the canopy spreads over the rest. */
    public static final double ICO_SHARE = 0.4;

    /** The circuit canopy: its height over the base (Y0 + 40) and its radius. */
    public static final double CANOPY_Y = 40, CANOPY_R = 30;
    /**
     * Its tiles' size and the gap between them, the die's half-size, the ring bus's radius and sides. The die is wide
     * enough that the lanes leaving two sides at a corner keep apart, and its half a block keeps every straight piece
     * of a trace past the die a whole number and a half long, never a multiple of the 8 blocks a line is cut at, so
     * its count cannot slip by one.
     */
    public static final double TILE = 6, TILE_GAP = 0.4, DIE = 5.5, BUS_R = 18;
    public static final int BUS_SIDES = 24;
    /** The traces: four lanes out of each of the die's four sides, their lanes across a side. */
    public static final int SIDES = 4, LANES = 4, TRACES = SIDES * LANES;
    private static final int[] LANE = { -3, -1, 1, 3 };
    /**
     * A via's half-size, a flowing pulse's length, how much longer than its run a pulse's cycle is (the rest
     * between two pulses), and the ticks of one cycle.
     */
    public static final double VIA = 0.55, PULSE_LEN = 2.5, PULSE_REST = 1.4;
    public static final int PULSE_T = 70;

    /** The column of light from the central console up through the oculus to the point, and its rising motes. */
    public static final double BEAM_FROM = SophonArrayShape.CTRL_Y + 1, BEAM_R = 0.35;
    public static final int BEAM_MOTES = 6, BEAM_MOTE_T = 60;

    /** How far the landmark is drawn (blocks from the sphere's middle), how long it fades out, and its budget. */
    public static final double LANDMARK = 512, LANDMARK_FADE = 64;
    public static final int BUDGET = 400;
    /** The quads of a glow (a mote) and of a column of light ({@code Shapes.beam}: two planes in two halves). */
    public static final int GLOW_QUADS = 1, BEAM_QUADS = 4;

    /** The unit icosahedron with a vertex straight up: the top, the upper ring of five, the lower ring, the bottom. */
    private static final double[][] ICO = icosahedron();
    /** The icosahedron's faces (corners into {@link #ico}'s points) and edges. */
    public static final int[][] ICO_FACES = icoFaces(), ICO_EDGES = icoEdges();

    private static final Map<Integer, List<double[]>> FRAMES = new LinkedHashMap<>();
    private static final List<double[]> STRUTS, TILES;
    private static final List<double[][]> TRACE_LIST;
    private static final double[][] BUS;

    static {
        synchronized (FRAMES) {
            FRAMES.put(FAR_FREQ, Collections.unmodifiableList(geodesic(FAR_FREQ)));
            FRAMES.put(FREQ, Collections.unmodifiableList(geodesic(FREQ)));
        }
        STRUTS = Collections.unmodifiableList(strutLines());
        TILES = Collections.unmodifiableList(tileList());
        TRACE_LIST = Collections.unmodifiableList(traceList());
        BUS = busRing();
    }

    private SophonLook() {}

    // ---- the frame

    /**
     * The geodesic frame's edges at a subdivision, {x0, y0, z0, x1, y1, z1} each in the array's frame: the
     * icosahedron's faces cut into {@code freq * freq} triangles each and pushed out onto the sphere of radius
     * {@link #RADIUS} round its middle ({@code 30 * freq * freq} edges, no edge twice).
     */
    public static List<double[]> frame(int freq) {
        synchronized (FRAMES) {
            List<double[]> f = FRAMES.get(freq);
            if (f == null) {
                f = Collections.unmodifiableList(geodesic(freq));
                FRAMES.put(freq, f);
            }
            return f;
        }
    }

    /** The frame's subdivision drawn at a distance from the sphere's middle. */
    public static int frameFreq(double distance) {
        return distance <= FRAME_FAR ? FREQ : FAR_FREQ;
    }

    /**
     * The four struts that carried the lattice, {x0, y0, z0, x1, y1, z1} each: from the podium's roof at the struts'
     * columns ({@code (+-5, +-5)} round the middle) up to the sphere where their line meets it.
     */
    public static List<double[]> struts() {
        return STRUTS;
    }

    private static List<double[]> strutLines() {
        List<double[]> out = new ArrayList<>();
        int o = SophonArrayShape.STRUT_OFF;
        double top = CY - Math.sqrt(RADIUS * RADIUS - 2.0 * o * o);
        for (int[] s : new int[][] { { o, o }, { -o, o }, { o, -o }, { -o, -o } }) {
            double x = CX + s[0], z = CZ + s[1];
            out.add(new double[] { x, STRUT_FOOT, z, x, top, z });
        }
        return out;
    }

    private static List<double[]> geodesic(int freq) {
        List<double[]> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int[] f : ICO_FACES) {
            double[] a = ICO[f[0]], b = ICO[f[1]], c = ICO[f[2]];
            for (int i = 0; i < freq; i++) for (int j = 0; i + j < freq; j++) {
                double[] p = onSphere(a, b, c, i, j, freq), q = onSphere(a, b, c, i + 1, j, freq),
                    r = onSphere(a, b, c, i, j + 1, freq);
                edge(out, seen, p, q);
                edge(out, seen, p, r);
                edge(out, seen, q, r);
            }
        }
        return out;
    }

    /** The point {@code i / n} along a to b and {@code j / n} along a to c of a face, pushed out onto the sphere. */
    private static double[] onSphere(double[] a, double[] b, double[] c, int i, int j, int n) {
        double x = a[0] + (b[0] - a[0]) * i / n + (c[0] - a[0]) * j / n,
            y = a[1] + (b[1] - a[1]) * i / n + (c[1] - a[1]) * j / n,
            z = a[2] + (b[2] - a[2]) * i / n + (c[2] - a[2]) * j / n;
        double len = Math.sqrt(x * x + y * y + z * z);
        return new double[] { CX + RADIUS * x / len, CY + RADIUS * y / len, CZ + RADIUS * z / len };
    }

    private static void edge(List<double[]> out, java.util.Set<String> seen, double[] p, double[] q) {
        String kp = pointKey(p), kq = pointKey(q);
        if (!seen.add(kp.compareTo(kq) < 0 ? kp + "|" + kq : kq + "|" + kp)) return;
        out.add(new double[] { p[0], p[1], p[2], q[0], q[1], q[2] });
    }

    private static String pointKey(double[] p) {
        return Math.round(p[0] * 1e6) + "," + Math.round(p[1] * 1e6) + "," + Math.round(p[2] * 1e6);
    }

    // ---- the icosahedron

    private static double[][] icosahedron() {
        double[][] v = new double[12][];
        double y = 1 / Math.sqrt(5), r = 2 / Math.sqrt(5);
        v[0] = new double[] { 0, 1, 0 };
        for (int k = 0; k < 5; k++) {
            double up = 2 * StrictMath.PI * k / 5, down = up + StrictMath.PI / 5;
            v[1 + k] = new double[] { r * StrictMath.cos(up), y, r * StrictMath.sin(up) };
            v[6 + k] = new double[] { r * StrictMath.cos(down), -y, r * StrictMath.sin(down) };
        }
        v[11] = new double[] { 0, -1, 0 };
        return v;
    }

    private static int[][] icoFaces() {
        int[][] f = new int[20][];
        for (int k = 0; k < 5; k++) {
            int u0 = 1 + k, u1 = 1 + (k + 1) % 5, l0 = 6 + k, l1 = 6 + (k + 1) % 5;
            f[k] = new int[] { 0, u0, u1 };
            f[5 + k] = new int[] { u0, l0, u1 };
            f[10 + k] = new int[] { u1, l0, l1 };
            f[15 + k] = new int[] { 11, l1, l0 };
        }
        return f;
    }

    private static int[][] icoEdges() {
        int[][] e = new int[30][];
        for (int k = 0; k < 5; k++) {
            int u0 = 1 + k, u1 = 1 + (k + 1) % 5, l0 = 6 + k, l1 = 6 + (k + 1) % 5;
            e[k] = new int[] { 0, u0 };
            e[5 + k] = new int[] { u0, u1 };
            e[10 + k] = new int[] { u0, l0 };
            e[15 + k] = new int[] { u1, l0 };
            e[20 + k] = new int[] { l0, l1 };
            e[25 + k] = new int[] { 11, l0 };
        }
        return e;
    }

    /**
     * The icosahedron's twelve corners at the world time {@code t}, {x, y, z} each in the array's frame round the
     * sphere's middle: {@code scale} of its full radius {@link #ICO_R} (0 is the light point, 1 unfolded), tilted
     * {@link #ICO_TILT} and turning once every {@link #ICO_TURN_T} ticks.
     */
    public static double[][] ico(double t, double scale) {
        double turn = 2 * Math.PI * ArcaneLook.frac(t / ICO_TURN_T), ct = Math.cos(ICO_TILT), st = Math.sin(ICO_TILT),
            cy = Math.cos(turn), sy = Math.sin(turn), r = ICO_R * scale;
        double[][] out = new double[ICO.length][];
        for (int i = 0; i < ICO.length; i++) {
            double[] v = ICO[i];
            // tilt about x, then turn about the vertical
            double y = v[1] * ct - v[2] * st, z = v[1] * st + v[2] * ct, x = v[0];
            out[i] = new double[] { CX + r * (x * cy - z * sy), CY + r * y, CZ + r * (x * sy + z * cy) };
        }
        return out;
    }

    /**
     * How far the array has unfolded, 0 (the light point) to 1 (the icosahedron turning and the canopy spread):
     * rising over {@link #UNFOLD_MS} once it is powered, falling back over {@link #FOLD_MS} once it is not, by the
     * milliseconds since that last changed ({@code Long.MAX_VALUE} for long ago, so a client that arrives late sees it
     * as it is and nothing replays).
     */
    public static double unfold(boolean on, long since) {
        double s = Math.max(0, since);
        return on ? Math.min(1, s / UNFOLD_MS) : Math.max(0, 1 - s / FOLD_MS);
    }

    /** The icosahedron's size at an unfolding: it grows out of the point over the first {@link #ICO_SHARE}. */
    public static double icoScale(double unfold) {
        return Math.max(0, Math.min(1, unfold / ICO_SHARE));
    }

    /** How far the canopy has spread at an unfolding: over the rest, once the icosahedron stands. */
    public static double spread(double unfold) {
        return Math.max(0, Math.min(1, (unfold - ICO_SHARE) / (1 - ICO_SHARE)));
    }

    // ---- the canopy

    /**
     * The canopy's tiles, {x0, z0, x1, z1, r} each in the array's frame (the plane at {@link #CANOPY_Y}), {@code r}
     * how far its middle lies from the canopy's: the {@link #TILE} grid over the canopy, the tiles whose middle lies
     * within half a tile of its rim, each drawn {@link #TILE_GAP} short of its neighbours.
     */
    public static List<double[]> tiles() {
        return TILES;
    }

    private static List<double[]> tileList() {
        List<double[]> out = new ArrayList<>();
        int n = (int) Math.round(CANOPY_R / TILE);
        double half = TILE / 2 - TILE_GAP / 2;
        for (int i = -n; i < n; i++) for (int j = -n; j < n; j++) {
            double u = (i + 0.5) * TILE, v = (j + 0.5) * TILE, r = Math.sqrt(u * u + v * v);
            if (r > CANOPY_R - TILE / 2) continue;
            out.add(new double[] { CX + u - half, CZ + v - half, CX + u + half, CZ + v + half, r });
        }
        return out;
    }

    /**
     * The canopy's traces, each a line of points {x, z} in the array's frame from the die's edge outward: four lanes
     * out of each side of the die, straight out a little, a 45 degree jog that fans the lanes apart (the outer lanes
     * further, and first, so no two lanes ever cross), then straight out to a via short of the rim. Lengths and ends
     * vary by the side and the lane (integer steps, the same everywhere).
     */
    public static List<double[][]> traces() {
        return TRACE_LIST;
    }

    private static List<double[][]> traceList() {
        List<double[][]> out = new ArrayList<>();
        int[][] out4 = { { 1, 0 }, { 0, 1 }, { -1, 0 }, { 0, -1 } };
        for (int s = 0; s < SIDES; s++) {
            int ux = out4[s][0], uz = out4[s][1], vx = -uz, vz = ux;
            for (int l = 0; l < LANES; l++) {
                int v0 = LANE[l], sign = v0 < 0 ? -1 : 1;
                // the outer lanes jog first: an inner lane runs on at least a block further before its own jog
                int base = 2 + Math.floorMod(s * 2 + 1, 3), outer = Math.abs(v0) == 3 ? 1 : 0;
                int run = outer == 1 ? base : base + 1 + Math.floorMod(s + l, 2), jog = outer == 1 ? 7 : 3,
                    end = 25 + Math.floorMod(s * 5 + l * 7, 3);
                int v2 = v0 + sign * jog;
                double u1 = DIE + run, u2 = u1 + jog, u3 = Math.floor(Math.sqrt((double) end * end - v2 * v2));
                double[][] uv = { { DIE, v0 }, { u1, v0 }, { u2, v2 }, { u3, v2 } };
                double[][] pts = new double[uv.length][];
                for (int k = 0; k < uv.length; k++)
                    pts[k] = new double[] { CX + uv[k][0] * ux + uv[k][1] * vx, CZ + uv[k][0] * uz + uv[k][1] * vz };
                out.add(pts);
            }
        }
        return out;
    }

    /** A trace's length along its points. */
    public static double length(double[][] trace) {
        double l = 0;
        for (int i = 1; i < trace.length; i++) l += dist(trace[i - 1], trace[i]);
        return l;
    }

    /** The ring bus's corners {x, z} in the array's frame: {@link #BUS_SIDES} round the radius {@link #BUS_R}. */
    public static double[][] bus() {
        return BUS;
    }

    private static double[][] busRing() {
        double[][] out = new double[BUS_SIDES][];
        for (int i = 0; i < BUS_SIDES; i++) {
            double a = 2 * StrictMath.PI * i / BUS_SIDES;
            out[i] = new double[] { CX + BUS_R * StrictMath.cos(a), CZ + BUS_R * StrictMath.sin(a) };
        }
        return out;
    }

    /**
     * The pulse on trace {@code k} at the world time {@code t}, drawn out to {@code reach} of the trace's length (the
     * canopy spreading): {x0, z0, x1, z1} on the one segment its head is on, or null while it is between runs or past
     * what is drawn. A pulse runs out from the die to the via every {@link #PULSE_T} ticks, each trace at its own
     * phase, and rests a while at the via before the next ({@link #PULSE_REST}).
     */
    public static double[] pulse(int k, double t, double reach) {
        double[][] tr = TRACE_LIST.get(k);
        double len = length(tr), drawn = len * Math.max(0, Math.min(1, reach));
        double head = ArcaneLook.frac(t / PULSE_T + k * 0.618) * (len + PULSE_LEN) * PULSE_REST,
            tail = head - PULSE_LEN;
        head = Math.min(head, drawn);
        if (head <= Math.max(0, tail)) return null;
        double at = 0;
        for (int i = 1; i < tr.length; i++) {
            double seg = dist(tr[i - 1], tr[i]);
            if (head <= at + seg + 1e-9) {
                double a = Math.max(tail, at) - at, b = head - at;
                if (b <= a) return null;
                double[] p = tr[i - 1], q = tr[i];
                return new double[] { p[0] + (q[0] - p[0]) * a / seg, p[1] + (q[1] - p[1]) * a / seg,
                    p[0] + (q[0] - p[0]) * b / seg, p[1] + (q[1] - p[1]) * b / seg };
            }
            at += seg;
        }
        return null;
    }

    /**
     * How bright the canopy is at a radius from its middle: full over the die, falling to a quarter at its rim (it
     * fades out into the sky rather than ending at an edge).
     */
    public static float radialFade(double r) {
        double u = Math.max(0, Math.min(1, r / CANOPY_R));
        return (float) (1 - 0.75 * u * u);
    }

    // ---- the landmark

    /** How much of the landmark shows at a distance from the sphere's middle: 1, fading to 0 over its last blocks. */
    public static float landmarkFade(double distance) {
        return (float) Math.max(0, Math.min(1, (LANDMARK - distance) / LANDMARK_FADE));
    }

    // ---- the budget

    /** The quads of a line {@code length} long ({@link DomeLook#pieces}: the renderer cuts it every 8 blocks). */
    private static int line(double length) {
        return DomeLook.pieces(length);
    }

    /** The quads of the frame at a subdivision: each edge in its pieces. */
    public static int frameQuads(int freq) {
        int n = 0;
        for (double[] e : frame(freq)) n += line(len(e));
        return n;
    }

    /** The quads of the four struts. */
    public static int strutQuads() {
        int n = 0;
        for (double[] e : STRUTS) n += line(len(e));
        return n;
    }

    /**
     * The quads of the middle, powered or not long since: {@link #coreQuads(double)} fully unfolded or folded.
     */
    public static int coreQuads(boolean on) {
        return coreQuads(unfold(on, Long.MAX_VALUE));
    }

    /**
     * The quads of the middle at an unfolding, as {@code SophonRender} draws it: the light point (a glow and its white
     * core) and, while the icosahedron shows ({@link #icoScale} over 0.001, also while it folds back for
     * {@link #FOLD_MS} after the power drops), its twenty faces and its thirty edges in their pieces at its full size
     * (a smaller one never takes more).
     */
    public static int coreQuads(double unfold) {
        int n = 2 * GLOW_QUADS;
        if (icoScale(unfold) <= 0.001) return n;
        double[][] v = ico(0, 1);
        n += ICO_FACES.length;
        for (int[] e : ICO_EDGES) n += line(dist3(v[e[0]], v[e[1]]));
        return n;
    }

    /**
     * The quads of the spread canopy: a quad a tile, the die and its four edges, each trace's segments in their
     * pieces with a via and a pulse, and the ring bus's sides in their pieces.
     */
    public static int canopyQuads() {
        int n = TILES.size() + 1 + 4 * line(2 * DIE);
        for (double[][] tr : TRACE_LIST) {
            for (int i = 1; i < tr.length; i++) n += line(dist(tr[i - 1], tr[i]));
            n += 2;
        }
        for (int i = 0; i < BUS_SIDES; i++) n += line(dist(BUS[i], BUS[(i + 1) % BUS_SIDES]));
        return n;
    }

    /**
     * The most quads the landmark takes a frame at a distance, powered or not long since:
     * {@link #landmarkQuads(double, double)} fully unfolded or folded. The powered count bounds every state.
     */
    public static int landmarkQuads(double distance, boolean on) {
        return landmarkQuads(distance, unfold(on, Long.MAX_VALUE));
    }

    /**
     * The most quads the landmark takes a frame at a distance from the sphere's middle and an unfolding
     * ({@link #unfold}), counted as {@code SophonRender} draws it: the frame at that distance's subdivision, the
     * struts, the middle ({@link #coreQuads(double)}) and, once it has spread at all, the canopy (its whole count, an
     * upper bound while it spreads or folds back). So the 1.5 s fold-back after the power drops, when the
     * icosahedron and the shrinking canopy are still drawn, is counted too. The same drawing within the effects'
     * range, so this holds there as well.
     */
    public static int landmarkQuads(double distance, double unfold) {
        return frameQuads(frameFreq(distance)) + strutQuads()
            + coreQuads(unfold)
            + (spread(unfold) > 0 ? canopyQuads() : 0);
    }

    /**
     * The most quads the array's own drawing that is no landmark takes a frame at a tier ({@link ModuleLook#tier}):
     * docked, near and mid, the column of light up through the oculus, near with its motes and a glow where it
     * leaves the console. The shared drawing ({@link ModuleLook#baseQuads}) comes on top; the landmark has its own
     * budget.
     */
    public static int quads(int tier, boolean docked, boolean on) {
        if (!docked || tier == ModuleLook.FAR) return 0;
        return BEAM_QUADS + (tier == ModuleLook.NEAR ? BEAM_MOTES + GLOW_QUADS : 0);
    }

    /** The height a rising mote {@code k} of the column has reached at the world time {@code t}, and its alpha. */
    public static double[] beamMote(int k, double t, double top) {
        double u = ArcaneLook.frac(t / BEAM_MOTE_T + k / (double) BEAM_MOTES);
        return new double[] { BEAM_FROM + (top - BEAM_FROM) * u, Math.sin(Math.PI * u) };
    }

    private static double len(double[] e) {
        return Math.sqrt(sq(e[3] - e[0]) + sq(e[4] - e[1]) + sq(e[5] - e[2]));
    }

    private static double dist(double[] a, double[] b) {
        return Math.sqrt(sq(b[0] - a[0]) + sq(b[1] - a[1]));
    }

    private static double dist3(double[] a, double[] b) {
        return Math.sqrt(sq(b[0] - a[0]) + sq(b[1] - a[1]) + sq(b[2] - a[2]));
    }

    private static double sq(double d) {
        return d * d;
    }
}
