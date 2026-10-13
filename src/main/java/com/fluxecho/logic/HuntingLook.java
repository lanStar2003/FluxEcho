package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Where the Echo Hunting Ground's drawing puts its light (0.13.0, CITY.md §4.5 Visuals), worked out in the ground's own
 * frame ({@link HuntingGroundShape}: x across, y up from the floor's base layer, z from the blueprint's front) round
 * its axis, the middle of the emitter's column ({@link #AXIS}):
 * <ul>
 * <li>The velarium: once the ground forms, the ring of frame ring segments at y9 gives way to a translucent canopy of
 * hexagons, amber at its rim and cyan over the arena, closing over the whole bowl in a shallow dome
 * ({@link #canopyY}, {@link #hexes}, {@link #hexEdges}); the ring itself stays as drawn lines ({@link #ring}).</li>
 * <li>Docked: a line of light sweeps across the arena's holo grid every {@link #SCAN_T} ticks, each sweep a quarter
 * turn on from the last ({@link #scanPieces}), lighting the grid's lit tiles behind it ({@link #trail}); and every
 * imprint console with a sample shows a small hologram over its socket: a flat silhouette turned to the camera
 * ({@link #silhouette}), in the colours of the mob's spawn egg when the sample names a mob that has one, else in
 * neutral colours. The silhouettes are drawn shapes, never entity models: drawing entity models after the world under
 * a shader pack needs its own prototype (CITY.md §4.5).</li>
 * <li>Powered: nothing more until the imprinted mobs perform with the function (0.19.0 on); the light is brighter.</li>
 * </ul>
 * Every motion is a function of the world time alone and repeats with its own period, so a frame drawn again behind a
 * light gate is the same frame. Pure arithmetic over the shape, so the tests hold the canopy over the bowl and clear of
 * the wall, the scan line on the arena's open tiles, the silhouettes in the air over their consoles and the whole
 * ground within its quad budgets ({@link #quads}).
 */
public final class HuntingLook {

    /** The ground's axis across and deep in its frame: the middle of the emitter's column. */
    public static final double AXIS = HuntingGroundShape.MID + 0.5;

    // ---- the velarium's canopy

    /**
     * The canopy's rim: the octagon through the middle of the velarium's ring (its flat sides {@code EDGE_FLAT} out,
     * its chamfers where {@code |x| + |z| = EDGE_CHAMFER}), at the ring's middle height {@code EDGE_Y}; it rises
     * {@code RISE} to its middle over the emitter.
     */
    public static final double EDGE_FLAT = 10.5, EDGE_CHAMFER = 14.0, EDGE_Y = HuntingGroundShape.VELARIUM_Y + 0.5,
        RISE = 1.5;
    /** The ring's inner and outer octagons (the velarium's cells at octagon radius 10 and 11): flat side, chamfer. */
    public static final double RING_IN_FLAT = 9.5, RING_IN_CHAMFER = 13.0, RING_OUT_FLAT = 11.5,
        RING_OUT_CHAMFER = 15.0;
    /** A hexagon's size (its corners' distance from its middle) near and mid; far the canopy is one octagon. */
    public static final double[] HEX = { 1.25, 2.0 };
    /** The ticks of one swell of the canopy's light rolling in from its rim. */
    public static final int CANOPY_T = 240;
    /** The quads of the far canopy (an octagon of eight quads round its middle). */
    public static final int FAN_QUADS = 8;
    /** The ties drawn across the ring between its two octagons, near: one at every corner. */
    public static final int TIES = 8;

    // ---- the arena's scan

    /** The ticks from one sweep of the scan line to the next (six seconds), and the ticks one sweep takes. */
    public static final int SCAN_T = 120, SWEEP_T = 36;
    /** How far out from the axis the arena's grid reaches each way (its cells at octagon radius 5 and in). */
    public static final double ARENA_EDGE = HuntingGroundShape.ARENA_R + 0.5;
    /** How far behind the line the grid's lit tiles still glow, and the height the scan's light lies at. */
    public static final double TRAIL = 2.5, SCAN_Y = 1.02;
    /** The ways the line sweeps, one a sweep in turn: front to back, right to left, back to front, left to right. */
    private static final int[][] DIRS = { { 0, 1 }, { -1, 0 }, { 0, -1 }, { 1, 0 } };

    // ---- the imprint consoles' silhouettes

    /** How high over its socket's top a silhouette's foot floats, how far it bobs, and the ticks of one bob. */
    public static final double SIL_LIFT = 0.1, SIL_BOB = 0.03;
    public static final int SIL_BOB_T = 90;
    /** A silhouette's size: its shape's unit (its outline lies within half a unit each way across). */
    public static final double SIL_SCALE = 0.85;
    /** The ticks a hologram's scan stripe takes up its silhouette. */
    public static final int SIL_SCAN_T = 50;
    /** The most rectangles a silhouette is drawn in. */
    public static final int SIL_RECTS = 8;
    /** The kinds of silhouette: what a mob looks like from a little way off. */
    public static final int BIPED = 0, BEAST = 1, CRAWLER = 2, TALL = 3, BLOB = 4, SMALL = 5, CREEPER = 6;
    /**
     * Each kind's rectangles {u0, v0, u1, v1, colour} in its own unit (u across, -0.5..0.5; v up from its foot),
     * colour 0 the egg's main colour and 1 its spots: a zombie's front, a cow's side, a spider's front, an enderman, a
     * slime, a bat, a creeper.
     */
    private static final double[][][] SILHOUETTES = {
        { { -0.12, 0.78, 0.12, 1.0, 0 }, { -0.16, 0.42, 0.16, 0.78, 0 }, { -0.25, 0.45, -0.17, 0.76, 0 },
            { 0.17, 0.45, 0.25, 0.76, 0 }, { -0.15, 0, -0.02, 0.42, 1 }, { 0.02, 0, 0.15, 0.42, 1 },
            { -0.08, 0.86, -0.03, 0.9, 1 }, { 0.03, 0.86, 0.08, 0.9, 1 } },
        { { -0.35, 0.3, 0.25, 0.6, 0 }, { 0.22, 0.45, 0.45, 0.72, 0 }, { -0.32, 0, -0.24, 0.3, 1 },
            { -0.18, 0, -0.1, 0.3, 1 }, { 0.06, 0, 0.14, 0.3, 1 }, { 0.16, 0, 0.24, 0.3, 1 },
            { -0.15, 0.42, 0.0, 0.55, 1 }, { 0.36, 0.6, 0.4, 0.64, 1 } },
        { { -0.18, 0.1, 0.18, 0.38, 0 }, { -0.5, 0.26, -0.18, 0.3, 0 }, { 0.18, 0.26, 0.5, 0.3, 0 },
            { -0.5, 0, -0.46, 0.26, 0 }, { 0.46, 0, 0.5, 0.26, 0 }, { -0.4, 0.12, -0.18, 0.16, 0 },
            { 0.18, 0.12, 0.4, 0.16, 0 }, { -0.1, 0.24, 0.1, 0.3, 1 } },
        { { -0.12, 1.06, 0.12, 1.3, 0 }, { -0.1, 0.62, 0.1, 1.06, 0 }, { -0.17, 0.3, -0.12, 1.02, 0 },
            { 0.12, 0.3, 0.17, 1.02, 0 }, { -0.09, 0, -0.03, 0.62, 0 }, { 0.03, 0, 0.09, 0.62, 0 },
            { -0.09, 1.14, -0.02, 1.17, 1 }, { 0.02, 1.14, 0.09, 1.17, 1 } },
        { { -0.3, 0.05, 0.3, 0.65, 0 }, { -0.18, 0.15, 0.18, 0.5, 1 }, { -0.15, 0.42, -0.07, 0.5, 1 },
            { 0.07, 0.42, 0.15, 0.5, 1 }, { -0.05, 0.22, 0.05, 0.27, 1 } },
        { { -0.12, 0.15, 0.12, 0.4, 0 }, { -0.08, 0.38, 0.08, 0.52, 0 }, { -0.32, 0.25, -0.12, 0.35, 1 },
            { 0.12, 0.25, 0.32, 0.35, 1 }, { -0.06, 0, -0.03, 0.15, 1 }, { 0.03, 0, 0.06, 0.15, 1 } },
        { { -0.13, 0.7, 0.13, 0.96, 0 }, { -0.1, 0.25, 0.1, 0.7, 0 }, { -0.14, 0, -0.02, 0.25, 0 },
            { 0.02, 0, 0.14, 0.25, 0 }, { -0.09, 0.84, -0.03, 0.9, 1 }, { 0.03, 0.84, 0.09, 0.9, 1 },
            { -0.04, 0.72, 0.04, 0.82, 1 } } };
    /**
     * The words in a mob's registered name that tell its kind, tried in this order (a zombie pigman is a biped before
     * it is a pig, an endermite a crawler before it is an enderman).
     */
    private static final String[][] KIND_WORDS = { { "zombie", "skeleton", "villager", "witch", "golem", "pigman" },
        { "endermite", "silverfish", "spider" }, { "enderman" }, { "creeper" }, { "slime", "ghast", "cube", "squid" },
        { "bat", "chicken", "bird", "wisp" }, { "cow", "pig", "sheep", "wolf", "ozelot", "ocelot", "horse", "cat",
            "fox", "boar", "deer", "bear", "goat", "rabbit" } };
    private static final int[] KIND_OF_WORDS = { BIPED, CRAWLER, TALL, CREEPER, BLOB, SMALL, BEAST };
    /** A silhouette's neutral colours, for a sample that names no mob with a spawn egg: steel grey and cyan. */
    public static final int NEUTRAL = 0x86A6B8, NEUTRAL_SPOTS = 0x4FE3FF;
    /** The prey imprint's registry name and the tag it writes its mob under ({@code com.fluxecho.mobs}). */
    public static final String IMPRINT_ITEM = "fluxecho:mob_imprint", IMPRINT_TAG = "Mob";

    /** The canopy's hexagons near and mid: each {cx, cz, q, seed} and its six corners {x, y, z}. */
    private static final List<double[][]> HEXES_NEAR, HEXES_MID;
    /** The hexagons' edges near, each drawn once: {x0, y0, z0, x1, y1, z1}. */
    private static final List<double[]> EDGES_NEAR;
    /** The arena's lit tiles open to the air {x, z}, and for each way across the runs of open grid in every row. */
    private static final List<int[]> LIT;
    private static final List<int[]>[][] RUNS;
    /** The imprint consoles' sockets {x, y, z} and their slots, in unit order. */
    private static final List<int[]> CONSOLES;
    private static final int[] CONSOLE_SLOTS;

    static {
        HEXES_NEAR = hexGrid(HEX[0]);
        HEXES_MID = hexGrid(HEX[1]);
        EDGES_NEAR = edgesOf(HEXES_NEAR);
        List<int[]> lit = new ArrayList<>();
        for (int x = 0; x < HuntingGroundShape.WIDTH; x++) for (int z = 0; z < HuntingGroundShape.DEPTH; z++) {
            if (HuntingGroundShape.cell(x, 0, z) == '+' && HuntingGroundShape.cell(x, 1, z) == ShapeKit.NONE)
                lit.add(new int[] { x, z });
        }
        LIT = Collections.unmodifiableList(lit);
        @SuppressWarnings("unchecked")
        List<int[]>[][] runs = new List[2][HuntingGroundShape.WIDTH];
        for (int across = 0; across < 2; across++) for (int row = 0; row < HuntingGroundShape.WIDTH; row++) {
            List<int[]> l = new ArrayList<>();
            int from = -1;
            for (int i = 0; i <= HuntingGroundShape.WIDTH; i++) {
                boolean open = i < HuntingGroundShape.WIDTH && (across == 0 ? openGrid(i, row) : openGrid(row, i));
                if (open && from < 0) from = i;
                if (!open && from >= 0) {
                    l.add(new int[] { from, i });
                    from = -1;
                }
            }
            runs[across][row] = Collections.unmodifiableList(l);
        }
        RUNS = runs;
        List<int[]> c = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        for (UnitLayout.Unit u : HuntingGroundShape.UNITS.units()) {
            if (!u.kind.equals("imprint")) continue;
            c.add(u.sockets.get(0));
            slots.add(u.firstSlot);
        }
        CONSOLES = Collections.unmodifiableList(c);
        CONSOLE_SLOTS = new int[slots.size()];
        for (int i = 0; i < CONSOLE_SLOTS.length; i++) CONSOLE_SLOTS[i] = slots.get(i);
        if (CONSOLES.size() != HuntingGroundShape.CONSOLES)
            throw new IllegalStateException("the hunting ground's consoles");
    }

    private HuntingLook() {}

    // ---- the canopy

    /**
     * How far out a point {@code (dx, dz)} off the axis lies in the canopy's octagon: 0 at the axis, 1 on its rim
     * (the octagon's norm: the larger of the flat sides' and the chamfers' shares).
     */
    public static double outline(double dx, double dz) {
        double a = Math.abs(dx), b = Math.abs(dz);
        return Math.max(Math.max(a, b) / EDGE_FLAT, (a + b) / EDGE_CHAMFER);
    }

    /** The canopy's height over the point {@code (x, z)} of the frame (a point past its rim is taken on the rim). */
    public static double canopyY(double x, double z) {
        double q = Math.min(1, outline(x - AXIS, z - AXIS));
        return EDGE_Y + RISE * (1 - q * q);
    }

    /**
     * The canopy's hexagons at a tier ({@link ModuleLook#NEAR} or {@code MID}): each {@code {cx, cz, q, seed}} (its
     * middle, how far out it lies, 0 to 1, and a share for its shimmer) followed by its six corners {x, y, z} on the
     * dome, the corners of a hexagon at the rim pulled in onto the rim. Far the canopy is {@link #fan}.
     */
    public static List<double[][]> hexes(int tier) {
        return tier == ModuleLook.NEAR ? HEXES_NEAR : HEXES_MID;
    }

    /** The near hexagons' edges, each drawn once: {x0, y0, z0, x1, y1, z1}. */
    public static List<double[]> hexEdges() {
        return EDGES_NEAR;
    }

    /**
     * A hexagonal grid of size {@code s} over the canopy: the hexagons whose middles lie inside its rim, flat side to
     * the front, one over the axis.
     */
    private static List<double[][]> hexGrid(double s) {
        List<double[][]> out = new ArrayList<>();
        int reach = (int) Math.ceil(EDGE_FLAT / s) + 2;
        for (int i = -reach; i <= reach; i++) for (int j = -reach; j <= reach; j++) {
            double cx = 1.5 * s * i, cz = Math.sqrt(3) * s * (j + i / 2.0);
            double q = outline(cx, cz);
            if (q >= 1) continue;
            double[][] h = new double[7][];
            h[0] = new double[] { AXIS + cx, AXIS + cz, q, Mix.unit(0x4E7CL, i, j) };
            for (int k = 0; k < 6; k++) {
                double a = Math.PI / 3 * k;
                double px = cx + s * Math.cos(a), pz = cz + s * Math.sin(a);
                double pq = outline(px, pz);
                if (pq > 1) {
                    px /= pq;
                    pz /= pq;
                }
                h[k + 1] = new double[] { AXIS + px, canopyY(AXIS + px, AXIS + pz), AXIS + pz };
            }
            out.add(h);
        }
        return Collections.unmodifiableList(out);
    }

    /** Every edge of the hexagons once (an edge two hexagons share is drawn once). */
    private static List<double[]> edgesOf(List<double[][]> hexes) {
        Map<String, double[]> seen = new HashMap<>();
        List<double[]> out = new ArrayList<>();
        for (double[][] h : hexes) for (int k = 0; k < 6; k++) {
            double[] a = h[k + 1], b = h[(k + 1) % 6 + 1];
            String ka = key(a), kb = key(b);
            if (ka.equals(kb)) continue;
            String edge = ka.compareTo(kb) < 0 ? ka + "|" + kb : kb + "|" + ka;
            if (seen.containsKey(edge)) continue;
            double[] e = { a[0], a[1], a[2], b[0], b[1], b[2] };
            seen.put(edge, e);
            out.add(e);
        }
        return Collections.unmodifiableList(out);
    }

    private static String key(double[] p) {
        return Math.round(p[0] * 1000) + "," + Math.round(p[2] * 1000);
    }

    /**
     * How bright the canopy's hexagon is at the world time {@code t}, 0.35 to 1: a swell rolling in from the rim
     * every {@link #CANOPY_T} ticks, each hexagon a little out of step ({@code hex[0]} of {@link #hexes}).
     */
    public static double hexLight(double[] middle, double t) {
        double ph = 2 * Math.PI * (t / CANOPY_T + middle[2] * 0.9) + middle[3] * 1.2;
        return 0.35 + 0.65 * (0.5 + 0.5 * Math.sin(ph));
    }

    /**
     * An octagon at the canopy's rim height: its eight corners {x, z} in the frame, for the flat sides {@code flat}
     * out and the chamfers where {@code |x| + |z| = chamfer}, in order round the axis.
     */
    public static double[][] octagon(double flat, double chamfer) {
        double other = chamfer - flat;
        double[][] corner = { { flat, other }, { other, flat }, { -other, flat }, { -flat, other }, { -flat, -other },
            { -other, -flat }, { other, -flat }, { flat, -other } };
        double[][] out = new double[8][];
        for (int i = 0; i < 8; i++) out[i] = new double[] { AXIS + corner[i][0], AXIS + corner[i][1] };
        return out;
    }

    /** The velarium's ring as drawn: its inner octagon ({@code outer} false) or its outer one. */
    public static double[][] ring(boolean outer) {
        return outer ? octagon(RING_OUT_FLAT, RING_OUT_CHAMFER) : octagon(RING_IN_FLAT, RING_IN_CHAMFER);
    }

    /** The canopy's rim: the octagon through the ring's middle. */
    public static double[][] fan() {
        return octagon(EDGE_FLAT, EDGE_CHAMFER);
    }

    /** The quads of an octagon drawn as lines, each side cut into pieces as every line is. */
    public static int octagonQuads(double[][] o) {
        int n = 0;
        for (int i = 0; i < o.length; i++) {
            double[] a = o[i], b = o[(i + 1) % o.length];
            n += DomeLook.pieces(Math.hypot(b[0] - a[0], b[1] - a[1]));
        }
        return n;
    }

    // ---- the scan

    /** The way sweep {@code cycle} runs ({x, z} of the frame, a unit along an axis): a quarter turn each sweep. */
    public static int[] scanWay(long cycle) {
        return DIRS[(int) Math.floorMod(cycle, (long) DIRS.length)];
    }

    /**
     * How far along its way the scan line has run at the world time {@code t} (blocks from the axis): from
     * {@code -ARENA_EDGE} at the start of each sweep, on past {@code ARENA_EDGE} (the line is drawn while it lies on
     * the grid, {@link #sweeping}; the trail behind it fades on).
     */
    public static double scanAt(double t) {
        double tau = t - Math.floor(t / SCAN_T) * SCAN_T;
        return -ARENA_EDGE + 2 * ARENA_EDGE * tau / SWEEP_T;
    }

    /** Whether the scan line lies on the grid at {@code t}: the first {@link #SWEEP_T} ticks of every sweep. */
    public static boolean sweeping(double t) {
        return t - Math.floor(t / SCAN_T) * SCAN_T < SWEEP_T;
    }

    /**
     * The scan line at the world time {@code t}, as the pieces that lie on the arena's grid with nothing standing on
     * them (the emitter and the holo pedestals cut it): {x0, z0, x1, z1} each in the frame, none between sweeps.
     */
    public static List<double[]> scanPieces(double t) {
        List<double[]> out = new ArrayList<>();
        if (!sweeping(t)) return out;
        int[] way = scanWay((long) Math.floor(t / SCAN_T));
        double p = scanAt(t);
        // the line runs across the way: along x when the way is along z, and along z when it is along x
        boolean alongX = way[0] == 0;
        int sign = alongX ? way[1] : way[0];
        double at = AXIS + p * sign;
        // a line on a cell's edge belongs to the cell it has come through
        int row = sign > 0 ? (int) Math.floor(at) : (int) Math.ceil(at) - 1;
        if (row < 0 || row >= HuntingGroundShape.WIDTH) return out;
        for (int[] r : RUNS[alongX ? 0 : 1][row]) {
            if (alongX) out.add(new double[] { r[0], at, r[1], at });
            else out.add(new double[] { at, r[0], at, r[1] });
        }
        return out;
    }

    /** Whether the arena's grid cell {@code (x, z)} lies open to the air (a tile of the grid, nothing on it). */
    private static boolean openGrid(int x, int z) {
        char floor = HuntingGroundShape.cell(x, 0, z);
        return (floor == ':' || floor == '+') && HuntingGroundShape.cell(x, 1, z) == ShapeKit.NONE;
    }

    /** The most pieces the scan line is ever drawn in: the most runs of open grid in any row either way. */
    public static int scanPiecesMost() {
        int most = 0;
        for (List<int[]>[] way : RUNS) for (List<int[]> row : way) most = Math.max(most, row.size());
        return most;
    }

    /** The arena grid's lit tiles that lie open to the air, {x, z}. */
    public static List<int[]> lit() {
        return LIT;
    }

    /**
     * How brightly the scan's trail lights the grid's lit tile {@code (x, z)} at the world time {@code t}, 0 to 1:
     * brightest just behind the line, fading out {@link #TRAIL} behind it; nothing ahead of it.
     */
    public static double trail(int x, int z, double t) {
        int[] way = scanWay((long) Math.floor(t / SCAN_T));
        double along = (x + 0.5 - AXIS) * way[0] + (z + 0.5 - AXIS) * way[1];
        double behind = scanAt(t) - along;
        if (behind < 0 || behind > TRAIL) return 0;
        return 1 - behind / TRAIL;
    }

    // ---- the silhouettes

    /** The imprint consoles' sockets {x, y, z}, in unit order. */
    public static List<int[]> consoles() {
        return CONSOLES;
    }

    /** The slot imprint console {@code i} keeps its sample in. */
    public static int consoleSlot(int i) {
        return CONSOLE_SLOTS[i];
    }

    /**
     * Where console {@code i}'s silhouette stands at the world time {@code t}: {x, y, z} of its foot, over the middle
     * of its socket's top, bobbing a little.
     */
    public static double[] silhouetteFoot(int i, double t) {
        int[] s = CONSOLES.get(i);
        double bob = SIL_BOB * Math.sin(2 * Math.PI * t / SIL_BOB_T + i * 0.7);
        return new double[] { s[0] + 0.5, s[1] + 1 + SIL_LIFT + bob, s[2] + 0.5 };
    }

    /** How high up its silhouette console {@code i}'s hologram stripe is at {@code t} (0 to 1 of its height). */
    public static double stripe(int i, double t) {
        return ArcaneLook.frac(t / SIL_SCAN_T + i * 0.17);
    }

    /**
     * The kind of silhouette for a mob by the name it is registered under ({@code Zombie}, {@code CaveSpider},
     * {@code Thaumcraft.Wisp}, ...): the first of the kinds a word of it tells, a biped when none does or there is no
     * mob.
     */
    public static int kindOf(String mob) {
        if (mob == null) return BIPED;
        String name = mob.toLowerCase(Locale.ROOT);
        for (int k = 0; k < KIND_WORDS.length; k++) for (String w : KIND_WORDS[k]) {
            if (name.contains(w)) return KIND_OF_WORDS[k];
        }
        return BIPED;
    }

    /** A kind's rectangles {u0, v0, u1, v1, colour} (do not change the arrays). */
    public static double[][] silhouette(int kind) {
        return SILHOUETTES[kind];
    }

    /** How many kinds of silhouette there are. */
    public static int kinds() {
        return SILHOUETTES.length;
    }

    /** The height of a kind's silhouette in its own unit (its highest rectangle's top). */
    public static double height(int kind) {
        double top = 0;
        for (double[] r : SILHOUETTES[kind]) top = Math.max(top, r[3]);
        return top;
    }

    /**
     * The mob a sample names when it is a prey imprint: its registry name and its {@link #IMPRINT_TAG} tag (null when
     * it has none); null for anything else, or a blank imprint. A spawn egg names its mob by its damage, which the
     * renderer reads itself.
     */
    public static String imprintMob(String registryName, String tag) {
        if (!IMPRINT_ITEM.equals(registryName) || tag == null || tag.isEmpty()) return null;
        return tag;
    }

    // ---- the budget

    /**
     * The most quads the ground's own visuals take a frame at a tier ({@link ModuleLook#tier}), formed, docked or not,
     * powered or not, counted as {@code HuntingGroundRender} draws them: the canopy (near its hexagons, two quads each,
     * their edges and the ring's two octagons with their ties; mid its hexagons and its rim; far an octagon of quads
     * and its rim); docked, the scan line (near each piece a core and a glow, and the trail's lit tiles; mid the core);
     * near and docked, the consoles' holograms (a silhouette, its stripe and its projector's glow each).
     */
    public static int quads(int tier, boolean docked, boolean on) {
        int n;
        if (tier == ModuleLook.NEAR)
            n = HEXES_NEAR.size() * 2 + EDGES_NEAR.size() + octagonQuads(ring(false)) + octagonQuads(ring(true)) + TIES;
        else if (tier == ModuleLook.MID) n = HEXES_MID.size() * 2 + octagonQuads(fan());
        else n = FAN_QUADS + octagonQuads(fan());
        if (docked && tier == ModuleLook.NEAR) n += scanPiecesMost() * 2 + LIT.size();
        if (docked && tier == ModuleLook.MID) n += scanPiecesMost();
        if (docked && tier == ModuleLook.NEAR) n += CONSOLES.size() * (SIL_RECTS + 2);
        return n;
    }
}
