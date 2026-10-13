package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The root under the nexus (0.12.0, CITY.md §4.9): one module, {@code root}, whose shapes grow downward. This version
 * builds its first shape, 潮汐井 the Tidal Well ({@link #WELL}); the deep root and the ley core are ported as data only
 * ({@link #deepRoot()}, {@link #leyCore()}) for the batches that build them.
 * <p>
 * The well adapts to low ground (0.14.1, CITY.md §1.3 Vertical): its depth D is the deepest of 26, 20, 14 and 8 whose
 * floor slab, Y0 - D, stays at y {@link #LOWEST_SLAB} or over, clear of the bedrock ({@link #depth}). Each depth is a
 * shape of its own ({@link Well}: {@link #WELL} 26 deep as before, {@link #WELL_20}, {@link #WELL_14},
 * {@link #WELL_8}), the generator's {@code tidal_well(D)}: the same floor level, island, pool and eight taps on the
 * same cells (so the same slots), the lid at y D - 1, and only the bands, rings and ramp that fit under it. A campus
 * under Y0 {@link #MIN_Y0} has room for none, and a core is not placed there ({@link CampusBand}).
 * <p>
 * The well ({@link #SHAPE}, a port of {@code tidal_well()} in {@code city/gen/b_root.py}) is a shaft
 * {@code oct(11, 15)} from its lid at Y0 - 1 (frame base under the forum's tiles and the dais, its centre left open for
 * the light lift) down to its floor slab at Y0 - 26. Its walls are 24 high with lit ribs on the eight lines and three
 * flux-layer bands (a dark course under a cornice at y6/7, y12/13, y18/19). At the floor level (the walkway at y1,
 * people stand at Y0 - 24) the island {@code oct(2, 3)} holds the lift pad and the controller over its console, facing
 * +A; round it the tidal pool (water), its basin rim with eight tide taps (sockets, one slot each, {@link #UNITS}),
 * four bridges on the axes and the walkway along the wall. Three tidal rings of frame ring segments hang over the pool,
 * and a helix ramp (soft: a helix cannot mirror) climbs one turn clockwise along the wall from the walkway to just
 * under the lid.
 * <p>
 * Frame: the shape's own (x across, R = 12 - x; y up from the floor slab, Y0 - 26 + y; z from the front, A = 12 - z),
 * which is also the module's blueprint. Its controller {@code (12, 3, 10)} is campus (A 2, R 0, Y0 - 23).
 * <p>
 * Built as a caisson ({@link BuildPlan#root}): the stage SINK goes layer by layer from the lid down, placing each
 * layer's wall ring before it digs that layer's inside, so the hole is never open and water from outside meets a wall;
 * DRAIN takes out what flowed in; FLOOR, FIT and COMMISSION fill it in. A campus too close to bedrock has no room for
 * it ({@link #noRoom}). The rings ({@code R}) give way to the rendered tide rings once it forms, and players pass
 * through them ({@link #DISSOLVES}, {@link #PASSES}); nobody can stand where they hang.
 * <p>
 * Counts (as the generator's): checked 3,268, soft 238, air 1, 3,507 steps; with the caisson's digging and draining
 * the plan has 15,792 more.
 */
public final class RootShape {

    public static final String KEY = "root";
    /** The root's shapes: the Tidal Well (this version), the deep root and the ley core (later batches). */
    public static final String WELL = "well", DEEP = "root", CORE = "core";
    /** The shallower Tidal Wells over low ground (0.14.1), 20, 14 and 8 deep: names only ever appended. */
    public static final String WELL_20 = "well_20", WELL_14 = "well_14", WELL_8 = "well_8";
    /** The Tidal Well's depths, deepest first: the full well, then the shallower ones (CITY.md §1.3 Vertical). */
    private static final int[] DEPTH_LIST = { 26, 20, 14, 8 };
    /** The root's site under the nexus (CITY.md §2.6): one per campus. */
    public static final int[] SITES = { SiteFamily.ROOT_SITE };
    /** The well's box: 25 across and deep round the axis at {@link #MID}, 26 high. */
    public static final int SIZE = 25, MID = 12, HEIGHT = 26;
    /** The well's octagon {@code oct(11, 15)} round the axis. */
    public static final int WELL_R = CampusPlan.ROOT_R, WELL_K = CampusPlan.ROOT_K;
    /** How far under the base layer its floor slab lies: Y0 - 26. */
    public static final int DEPTH = HEIGHT;
    /** The lid's layer (Y0 - 1), the floor level (the walkway, Y0 - 25; people stand at Y0 - 24) and the slab's. */
    public static final int LID_Y = HEIGHT - 1, FLOOR_Y = 1, SLAB_Y = 0;
    /** The controller over its console on the island, two in front of the pad: campus (A 2, R 0, Y0 - 23). */
    public static final int CTRL_X = MID, CTRL_Y = 3, CTRL_Z = MID - 2;
    /** The light-lift pad in the middle of the island. */
    public static final int PAD_X = MID, PAD_Y = FLOOR_Y, PAD_Z = MID;
    /**
     * The lowest floor slab a world allows (bedrock reaches y 4), and so the lowest base layer of the full well, Y0 >=
     * 32, and of the shallowest, Y0 >= 14 (0.14.1: under it no well fits).
     */
    public static final int LOWEST_SLAB = 6, LOWEST_Y0 = LOWEST_SLAB + DEPTH,
        MIN_Y0 = LOWEST_SLAB + DEPTH_LIST[DEPTH_LIST.length - 1];
    /** The flux-layer bands on the wall: a dark course at these layers, a cornice over each (表层, 记忆层, 深层). */
    public static final int[] BANDS = { 6, 12, 18 };
    /** The three tidal rings over the pool: their layers and their inner radii (each a cell wide). */
    public static final int[] RING_Y = { 6, 11, 16 };
    public static final double[] RING_R = { 2.5, 3.5, 4.5 };
    /**
     * The helix ramp: where its turn starts (degrees clockwise from the front) and the share of a turn it climbs (the
     * rest is the gap over its start).
     */
    public static final double RAMP_START = 200, RAMP_TURN = 0.97;
    /** The rings give way to the rendered tide rings; players pass through them (nobody stands where they hang). */
    public static final String DISSOLVES = "R", PASSES = "R";
    /** The ring segment's character. */
    public static final char RING = 'R';

    /** The stages of its job (CITY.md §4.9): the caisson, the draining, the floor, the fittings, commissioning. */
    public static final int R_SINK = 0, R_DRAIN = 1, R_FLOOR = 2, R_FIT = 3, R_COMMISSION = 4, R_STAGES = 5;

    /** The columns of the octagon, and those of its wall (the watertight ring). */
    private static final Set<Long> PLAN = plan(SIZE, WELL_R, WELL_K), WALL = ShapeKit.thickBoundary(PLAN);
    /** The Tidal Well at each of its depths, deepest first (0.14.1). */
    private static final List<Well> WELLS = wells(DEPTH_LIST);

    /** The full well, the generator's cells exactly. */
    public static final ShapeKit.Shape SHAPE = WELLS.get(0).shape;
    /** The eight tide taps, in slot order: the same cells at every depth. */
    public static final UnitLayout UNITS = WELLS.get(0).units;
    /** Its checked blueprint: the shape's box, soft cells left out. */
    public static final Blueprint BLUEPRINT = WELLS.get(0).blueprint;
    /** The full Tidal Well as its module plan reads it ({@link BuildPlan#root}). */
    public static final CityBuild BUILD = WELLS.get(0).build;

    static {
        for (Well w : WELLS) {
            if (w.units.size() != 8 || w.units.slots() != 8) throw new IllegalStateException("the tide taps changed");
            for (int i = 0; i < 8; i++) {
                if (!Arrays.equals(w.units.unit(i).cells.get(0), UNITS.unit(i).cells.get(0)))
                    throw new IllegalStateException("a tap moved " + w.depth + " deep");
            }
        }
    }

    /**
     * The Tidal Well at one of its depths (0.14.1): its shape (the generator's {@code tidal_well(D)}), its units, its
     * checked blueprint and its build, and where its lid, bands and rings lie. The frame is the shape's own at every
     * depth (x across, R = 12 - x; y up from the floor slab, Y0 - D + y; z from the front, A = 12 - z), so the floor
     * level, the island, the controller {@code (12, 3, 10)} and the taps keep their cells; only the lid comes down.
     */
    public static final class Well {

        /** Its depth D: the floor slab at Y0 - D, the lid at Y0 - 1. */
        public final int depth;
        /** Its shape's name in the structure definitions ({@link #WELL} for the full well). */
        public final String name;
        /** The lid's layer, D - 1. */
        public final int lidY;
        /** The flux-layer bands that fit under the lid (a dark course at each, a cornice over it). */
        private final int[] bands;
        /** The tidal rings that hang under the lid's layer below: their layers and inner radii. */
        private final int[] ringY;
        private final double[] ringR;
        /** Its cells, units, checked blueprint and build. */
        public final ShapeKit.Shape shape;
        public final UnitLayout units;
        public final Blueprint blueprint;
        public final CityBuild build;

        private Well(int depth) {
            this.depth = depth;
            name = depth == DEPTH ? WELL : WELL + "_" + depth;
            lidY = depth - 1;
            bands = bandsUnder(lidY);
            int n = 0;
            while (n < RING_Y.length && RING_Y[n] < lidY - 1) n++;
            ringY = Arrays.copyOf(RING_Y, n);
            ringR = Arrays.copyOf(RING_R, n);
            shape = new ShapeKit.Shape(KEY, SIZE, depth, SIZE);
            shape.groundOutside = false;
            for (Object[] u : wellInto(shape, 0, depth)) addUnit(shape, u);
            units = new UnitLayout(shape);
            blueprint = RootShape.blueprint(shape);
            final Well self = this;
            build = new CityBuild(
                KEY,
                shape,
                depth,
                blueprint,
                units,
                Parts.moduleCore(ModuleCores.ROOT),
                new CityBuild.Stages() {

                    @Override
                    public int stage(int x, int y, int z, char ch) {
                        return self.stage(x, y, z, ch);
                    }

                    @Override
                    public int fitRank(int x, int y, int z, char ch) {
                        return RootShape.fitRank(x, y, z, ch);
                    }
                });
        }

        /** The flux-layer bands under its lid, bottom up ({@link #BANDS} in the full well). */
        public int[] bands() {
            return bands.clone();
        }

        /** The layers of the tidal rings under its lid, bottom up ({@link #RING_Y} in the full well). */
        public int[] ringY() {
            return ringY.clone();
        }

        /** The inner radii of those rings ({@link #RING_R} in the full well). */
        public double[] ringR() {
            return ringR.clone();
        }

        /** A point of the well as a campus cell {a, r, dy} (dy from the base layer, negative under it). */
        public int[] toCampus(int x, int y, int z) {
            return new int[] { MID - z, MID - x, y - depth };
        }

        /** A campus cell {a, r, dy} as a point of the well {x, y, z} (it may lie outside the well's box). */
        public int[] fromCampus(int a, int r, int dy) {
            return new int[] { MID - r, dy + depth, MID - a };
        }

        /** The character of a cell: the checked legend, a soft character, {@code -} or a space. */
        public char cell(int x, int y, int z) {
            return shape.get(x, y, z);
        }

        /** Whether a cell is its shell: the lid, the floor slab and the wall. */
        public boolean shell(int x, int y, int z) {
            return inWell(x, z) && (y == lidY || y == SLAB_Y || wall(x, z));
        }

        /** Whether a cell lies inside the shell: what the caisson digs out and drains. */
        public boolean inside(int x, int y, int z) {
            return inWell(x, z) && !wall(x, z) && y > SLAB_Y && y < lidY;
        }

        /** The stage of a cell that is not part of a tide tap, as {@link RootShape#stage} for the full well. */
        public int stage(int x, int y, int z, char ch) {
            if (ch == ShapeKit.CONTROLLER) return R_COMMISSION;
            if (shell(x, y, z)) return R_SINK;
            if (y == FLOOR_Y && ch != '=') return R_FLOOR;
            if (ch == 'K' || ch == RING || ch == '=' || ShapeKit.soft(ch)) return R_FIT;
            throw new IllegalStateException("no stage for '" + ch + "' at " + x + "," + y + "," + z);
        }

        /**
         * Where its controller stands under the nexus: {x, y, z, facing ordinal}, campus (A 2, R 0, Y0 - D + 3)
         * facing the nexus's front.
         *
         * @throws IllegalArgumentException if the site is not the root's or the front not a horizontal unit vector
         */
        public int[] controller(int site, int cx, int y0, int cz, int fx, int fz) {
            int facing = BuildPlan.facingOrdinal(fx, fz);
            if (site != SiteFamily.ROOT_SITE) throw new IllegalArgumentException("not the root: " + site);
            int[] a = toCampus(CTRL_X, CTRL_Y, CTRL_Z);
            int[] w = CampusPlan.toWorld(a[0], a[1], fx, fz);
            return new int[] { cx + w[0], y0 + a[2], cz + w[1], facing };
        }

        /** The box {x0, y0, z0, x1, y1, z1} nothing but its own jobs takes a block down in: Y0 - D to the lid. */
        public List<int[]> protect(int cx, int y0, int cz, int fx, int fz) {
            int[] b = footprint(cx, cz, fx, fz, 0);
            return Collections.singletonList(new int[] { b[0], y0 - depth, b[1], b[2], y0 - 1, b[3] });
        }
    }

    private static List<Well> wells(int[] depths) {
        List<Well> out = new ArrayList<>();
        for (int d : depths) out.add(new Well(d));
        return Collections.unmodifiableList(out);
    }

    /** The flux-layer bands that fit under a lid at layer {@code lid}: those whose cornice stays under it. */
    private static int[] bandsUnder(int lid) {
        int n = 0;
        while (n < BANDS.length && BANDS[n] + 1 < lid) n++;
        return Arrays.copyOf(BANDS, n);
    }

    /** The Tidal Well's depths, deepest first: 26, 20, 14, 8. */
    public static int[] depths() {
        return DEPTH_LIST.clone();
    }

    /** The Tidal Well at every depth, deepest (the full well) first. */
    public static List<Well> wells() {
        return WELLS;
    }

    /**
     * The well's depth over a campus whose base layer is {@code y0} (0.14.1): the deepest of 26, 20, 14 and 8 whose
     * floor slab stays at y {@link #LOWEST_SLAB} or over (26 for Y0 >= 32, 20 for 26..31, 14 for 20..25, 8 for
     * 14..19); the shallowest under Y0 14, where it has no room ({@link #noRoom}).
     */
    public static int depth(int y0) {
        for (int d : DEPTH_LIST) if (y0 - d >= LOWEST_SLAB) return d;
        return DEPTH_LIST[DEPTH_LIST.length - 1];
    }

    /** The well a campus whose base layer is {@code y0} sinks ({@link #depth}). */
    public static Well forBase(int y0) {
        return well(depth(y0));
    }

    /**
     * The well of a depth.
     *
     * @throws IllegalArgumentException for a depth that is not one of {@link #depths}
     */
    public static Well well(int depth) {
        for (Well w : WELLS) if (w.depth == depth) return w;
        throw new IllegalArgumentException("no Tidal Well " + depth + " deep");
    }

    /** The well whose shape has the name, or null for any other name. */
    public static Well named(String name) {
        for (Well w : WELLS) if (w.name.equals(name)) return w;
        return null;
    }

    /** The name of the shape the well over a campus whose base layer is {@code y0} stands as. */
    public static String shapeFor(int y0) {
        return forBase(y0).name;
    }

    /**
     * The well a controller at (x, y, z) stands for under a nexus whose base layer is {@code y0} and whose front is
     * (fx, fz), or null when it stands at neither of its cells (0.14.1): the one at the depth the base layer gives
     * ({@link #forBase}), where the builder sinks it, else the full well, where 0.14.0 put every well, so one dug there
     * by hand over low ground before the shallower wells came still forms and docks.
     */
    public static Well wellAt(int x, int y, int z, int cx, int y0, int cz, int fx, int fz) {
        for (Well w : new Well[] { forBase(y0), well(DEPTH) }) {
            int[] at = w.controller(SiteFamily.ROOT_SITE, cx, y0, cz, fx, fz);
            if (at[0] == x && at[1] == y && at[2] == z) return w;
        }
        return null;
    }

    private RootShape() {}

    /** The module job on the root site ({@link BuildPlan#root}). */
    public static BuildPlan.Plan plan(CampusPlan plan, int site, int cx, int y0, int cz, int fx, int fz) {
        return BuildPlan.root(site, cx, y0, cz, fx, fz);
    }

    /**
     * Where its controller stands under the nexus: {x, y, z, facing ordinal}, campus (A 2, R 0, Y0 - D + 3) for the
     * depth D the base layer gives the well ({@link #depth}; Y0 - 23 under the full well), facing the nexus's front.
     *
     * @throws IllegalArgumentException if the site is not the root's or the front not a horizontal unit vector
     */
    public static int[] controller(int site, int cx, int y0, int cz, int fx, int fz) {
        return forBase(y0).controller(site, cx, y0, cz, fx, fz);
    }

    /** A point of the full well as a campus cell {a, r, dy} (dy from the base layer, negative under it). */
    public static int[] toCampus(int x, int y, int z) {
        return new int[] { MID - z, MID - x, y - DEPTH };
    }

    /** A campus cell {a, r, dy} as a point of the full well {x, y, z} (it may lie outside the well's box). */
    public static int[] fromCampus(int a, int r, int dy) {
        return new int[] { MID - r, dy + DEPTH, MID - a };
    }

    /** The character of a cell of the full well: the checked legend, a soft character, {@code -} or a space. */
    public static char cell(int x, int y, int z) {
        return SHAPE.get(x, y, z);
    }

    /** Whether the column (x, z) lies in the well's octagon. */
    public static boolean inWell(int x, int z) {
        return PLAN.contains(ShapeKit.key(x, z));
    }

    /** Whether the column (x, z) is the well's wall: the octagon's watertight edge. */
    public static boolean wall(int x, int z) {
        return WALL.contains(ShapeKit.key(x, z));
    }

    /**
     * Whether a cell of the full well is its shell: the lid, the floor slab and the wall. The caisson lays it layer by
     * layer as it sinks; everything inside is dug out first and filled in afterwards ({@link Well#shell} at a depth).
     */
    public static boolean shell(int x, int y, int z) {
        return inWell(x, z) && (y == LID_Y || y == SLAB_Y || wall(x, z));
    }

    /**
     * Whether a cell lies inside the full well's shell (between the slab and the lid, inside the wall): what the
     * caisson digs out and drains ({@link Well#inside} at a depth).
     */
    public static boolean inside(int x, int y, int z) {
        return inWell(x, z) && !wall(x, z) && y > SLAB_Y && y < LID_Y;
    }

    /**
     * The stage of a cell of the full well that is not part of a tide tap: SINK the shell (the lid with its open
     * centre, the walls, the floor slab); FLOOR the floor level's island, lift pad, bridges, pool rim and walkway; FIT
     * the controller's console, the tidal rings, the helix ramp and the pool's water; COMMISSION the controller.
     */
    public static int stage(int x, int y, int z, char ch) {
        return WELLS.get(0)
            .stage(x, y, z, ch);
    }

    /**
     * The order within FIT of a cell that is no tap's: the controller's console, the rings, the ramp (all before the
     * taps), then the pool's water once its rim is whole (after them).
     */
    public static int fitRank(int x, int y, int z, char ch) {
        if (ch == 'K') return -3;
        if (ch == RING) return -2;
        if (ch == '=') return 1;
        return -1;
    }

    /** Every layer of a shape as rows, bottom up, rows from the front: the checksum test's input. */
    static List<String> rows(ShapeKit.Shape s) {
        List<String> out = new ArrayList<>();
        for (int y = 0; y < s.H; y++) for (int z = 0; z < s.D; z++) {
            char[] r = new char[s.W];
            for (int x = 0; x < s.W; x++) r[x] = s.get(x, y, z);
            out.add(new String(r));
        }
        return out;
    }

    /** The checked layers of the well for {@link Blueprint}, top down: soft cells become spaces. */
    private static Blueprint blueprint(ShapeKit.Shape s) {
        String[][] layers = new String[s.H][s.D];
        for (int y = 0; y < s.H; y++) for (int z = 0; z < s.D; z++) {
            StringBuilder row = new StringBuilder(s.W);
            for (int x = 0; x < s.W; x++) {
                char ch = s.get(x, y, z);
                row.append(ShapeKit.soft(ch) ? ShapeKit.NONE : ch);
            }
            layers[s.H - 1 - y][z] = row.toString();
        }
        return new Blueprint(layers);
    }

    /** The standing states {x, z, h} the generator's self-check walks from: on the island's pad. */
    public static List<int[]> walkStarts() {
        return Collections.singletonList(new int[] { PAD_X, PAD_Z, 2 * (PAD_Y + 1) });
    }

    // ---------------------------------------------------------------------------------------------------------------
    // the envelope: what it takes of the campus

    /** Its octagon's box seen from above, {x0, z0, x1, z1} (inclusive), with {@code margin} round it. */
    public static int[] footprint(int cx, int cz, int fx, int fz, int margin) {
        int h = WELL_R + margin;
        int[] p = CampusPlan.toWorld(-h, -h, fx, fz), q = CampusPlan.toWorld(h, h, fx, fz);
        return new int[] { cx + Math.min(p[0], q[0]), cz + Math.min(p[1], q[1]), cx + Math.max(p[0], q[0]),
            cz + Math.max(p[1], q[1]) };
    }

    /** The columns the campus jobs leave alone once it stands: none, it lies wholly under the forum. */
    public static List<int[]> keepOut() {
        return Collections.emptyList();
    }

    /**
     * The box {x0, y0, z0, x1, y1, z1} nothing but its own jobs takes a block down in: its octagon's box from the floor
     * slab (Y0 - D; Y0 - 26 under the full well) to the lid (Y0 - 1). The forum over it stays the nexus's own; a
     * grading or a repair of the campus stops at the lid.
     */
    public static List<int[]> protect(int cx, int y0, int cz, int fx, int fz) {
        return forBase(y0).protect(cx, y0, cz, fx, fz);
    }

    /**
     * Why a campus whose base layer is {@code y0} has no room for it ({@code build.module.why.depth}: even the
     * shallowest well's floor slab would lie under y {@link #LOWEST_SLAB}, among the bedrock, under Y0
     * {@link #MIN_Y0}), or null. Over it the well takes the depth the ground has room for (0.14.1, {@link #depth}).
     */
    public static String noRoom(int y0) {
        return y0 < MIN_Y0 ? "build.module.why.depth" : null;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // the generator (a port of b_root.py)

    private static boolean contains(int[] a, int v) {
        for (int x : a) if (x == v) return true;
        return false;
    }

    /** The cells of {@code oct(half, k)} round the middle of an {@code n x n} box. */
    private static Set<Long> plan(int n, int half, int k) {
        int c = (n - 1) / 2;
        Set<Long> out = new LinkedHashSet<>();
        for (int x = 0; x < n; x++) for (int z = 0; z < n; z++) if (ShapeKit.octo(x - c, z - c, half, k)) {
            out.add(ShapeKit.key(x, z));
        }
        return out;
    }

    /** The generator's octagon norm: {@code max(|du|, |dv|, ceil((|du| + |dv|) * 0.75))}. */
    static int onorm(int du, int dv) {
        int a = Math.abs(du), b = Math.abs(dv);
        return Math.max(Math.max(a, b), (int) Math.ceil((a + b) * 0.75));
    }

    /** {@code math.atan2(x - C, -(z - C))}: 0 at the front, clockwise seen from above. */
    private static double bearing(int x, int z) {
        return StrictMath.atan2(x - MID, -(z - MID));
    }

    /** Python's float modulo: the result has the sign of the divisor. */
    private static double mod(double a, double m) {
        double r = a % m;
        return r < 0 ? r + m : r;
    }

    private static double radians(double deg) {
        return deg * (Math.PI / 180);
    }

    @SuppressWarnings("unchecked")
    private static void addUnit(ShapeKit.Shape s, Object[] u) {
        s.unit((String) u[0], (String) u[1], (List<int[]>) u[2], (Integer) u[3], null);
    }

    /**
     * {@code well_into(s, base, D)}: the Tidal Well {@code depth} deep, its floor slab at {@code base} and its lid at
     * base + D - 1 (base + 25 in the full well). Returns its units {name, kind, cells, slots} in the generator's order,
     * for the caller to declare.
     */
    private static List<Object[]> wellInto(ShapeKit.Shape s, int base, int depth) {
        final int C = MID, lid = depth - 1;
        Set<Long> plan = plan(SIZE, WELL_R, WELL_K), edge = ShapeKit.thickBoundary(plan);
        List<Object[]> units = new ArrayList<>();
        // the flux-layer bands whose cornice fits under the lid (all three in the full well)
        int[] bands = bandsUnder(lid);
        // floor slab, lid, walls with flux-layer bands
        for (long k : plan) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k);
            char ch = edge.contains(k) ? 'F' : 'B';
            s.set(x, base, z, ch);
            s.set(x, base + lid, z, ch);
        }
        // the axis lift passes the lid under the dais centre
        s.set(C, base + lid, C, ShapeKit.AIR);
        for (long k : edge) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k), du = x - C, dv = z - C;
            boolean rib = du == 0 || dv == 0 || Math.abs(du) == Math.abs(dv);
            for (int y = 1; y < lid; y++) {
                char ch;
                if (contains(bands, y)) ch = 'w';
                else if (contains(bands, y - 1)) ch = 'n';
                else ch = rib ? 'L' : 'W';
                s.set(x, base + y, z, ch);
            }
        }
        // floor level (stand base + 2): the island oct(2, 3), four bridges, the pool, its rim, the walkway
        for (long k : plan) {
            if (edge.contains(k)) continue;
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k), du = x - C, dv = z - C, n = onorm(du, dv);
            char ch;
            if (ShapeKit.octo(du, dv, 2, 3)) ch = 'B';
            else if (Math.abs(du) <= 1 || Math.abs(dv) <= 1) ch = 'd';
            else if (n <= 7) ch = '=';
            else if (n == 8) ch = 'u';
            else ch = 'd';
            s.set(x, base + 1, z, ch);
        }
        s.lift(C, base + 1, C);
        s.set(C, base + 2, C - 2, 'K');
        s.set(C, base + 3, C - 2, ShapeKit.CONTROLLER);
        s.controller = new int[] { C, base + 3, C - 2 };
        // the helix ramp (soft: a helix cannot mirror) on the band of octagon radius 9..10, one turn clockwise from the
        // front-left, stand base + 2 to base + D - 4 (base + 22 in the full well), a gap where the turn ends over its
        // start; it climbs 2 (D - 6) half-blocks, 40 in the full well
        double start = radians(RAMP_START);
        int climb = 2 * (depth - 6);
        Map<Long, Double> rampTop = new HashMap<>();
        for (long k : plan) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k), n = onorm(x - C, z - C);
            if (edge.contains(k) || n != 9 && n != 10) continue;
            double f = mod(bearing(x, z) - start, 2 * Math.PI) / (2 * Math.PI);
            if (f > RAMP_TURN) continue;
            // half-block steps: Python's round() is half to even, as rint
            double h = 2 + Math.rint(climb * f) / 2.0;
            rampTop.put(k, h);
            if (h <= 2) continue;
            if (h == Math.floor(h)) s.set(x, base + (int) h - 1, z, '{');
            else s.set(x, base + (int) h, z, '}');
        }
        // the ramp's rail on radius 8, beside every ramp cell from stand 4 up
        int[][] sides = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (long k : plan) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k);
            if (onorm(x - C, z - C) != 8 || edge.contains(k)) continue;
            double max = Double.NEGATIVE_INFINITY;
            for (int[] d : sides) {
                Double h = rampTop.get(ShapeKit.key(x + d[0], z + d[1]));
                if (h != null) max = Math.max(max, h);
            }
            if (max >= 4) s.set(x, base + (int) Math.ceil(max), z, '|');
        }
        // eight tide taps: sockets set into the pool rim between the bridges (the walkway right beside them)
        for (int t = 0; t < 8; t++) {
            double a = radians(22.5 + 45 * t), best = Double.POSITIVE_INFINITY;
            int bx = -1, bz = -1;
            for (long k : plan) {
                int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k);
                if (s.get(x, base + 1, z) != 'u') continue;
                double d = Math.abs(mod(bearing(x, z) - a + Math.PI, 2 * Math.PI) - Math.PI);
                if (bx < 0 || d < best - 1e-9) {
                    best = d;
                    bx = x;
                    bz = z;
                }
            }
            s.set(bx, base + 1, bz, 'o');
            List<int[]> cells = new ArrayList<>();
            cells.add(new int[] { bx, base + 1, bz });
            units.add(new Object[] { "tap-" + (t + 1), "tap", cells, 1 });
        }
        // the three tidal rings over the pool: frame ring segments at three heights and radii, those under the lid's
        // layer below (all three in the full well, two 14 deep, none 8 deep)
        for (int i = 0; i < RING_Y.length && RING_Y[i] < lid - 1; i++) for (long k : plan) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k);
            double d = ShapeKit.hypot(x - C, z - C);
            if (RING_R[i] <= d && d < RING_R[i] + 1) s.set(x, base + RING_Y[i], z, RING);
        }
        return units;
    }

    /**
     * {@code root_into(s, base)}: the deep-root chamber {@code oct(12, 16)} with its floor slab at {@code base} and its
     * ceiling the well's floor slab at base + 13: walls, the ring under the well floor, the pad, eight hanging root
     * conduits and eight root bays with lit veins to the middle.
     */
    private static void rootInto(ShapeKit.Shape s, int base) {
        final int C = MID;
        Set<Long> plan = plan(SIZE, 12, 16), edge = ShapeKit.thickBoundary(plan);
        for (long k : plan) s.set(ShapeKit.keyX(k), base, ShapeKit.keyZ(k), edge.contains(k) ? 'F' : 'B');
        for (long k : edge) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k), du = x - C, dv = z - C;
            boolean rib = du == 0 || dv == 0 || Math.abs(du) == Math.abs(dv);
            for (int y = 1; y < 13; y++) s.set(x, base + y, z, y == 12 ? 'n' : rib ? 'L' : y == 1 ? 'w' : 'W');
        }
        // the ring under the well floor between the two walls, at the ceiling's level
        for (long k : plan) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k);
            if (!ShapeKit.octo(x - C, z - C, WELL_R, WELL_K)) s.set(x, base + 13, z, 'B');
        }
        s.lift(C, base, C);
        // hanging root conduits from the ceiling at radius 5, six cells down
        for (int t = 0; t < 8; t++) {
            double a = radians(45 * t);
            int x = C + (int) Math.rint(5 * StrictMath.sin(a)), z = C - (int) Math.rint(5 * StrictMath.cos(a));
            for (int y = 7; y < 13; y++) s.set(x, base + y, z, 'C');
        }
        // eight root bays: a plinth and a socket against the wall, a lit vein to the middle
        for (int t = 0; t < 8; t++) {
            double a = radians(45 * t), sin = StrictMath.sin(a), cos = StrictMath.cos(a);
            int x = C + (int) Math.rint(10 * sin), z = C - (int) Math.rint(10 * cos);
            if (Math.abs(x - C) == Math.abs(z - C)) {
                x = C + (int) Math.rint(7.5 * sin + 0.01);
                z = C - (int) Math.rint(7.5 * cos - 0.01);
            }
            s.set(x, base + 1, z, 'p');
            s.set(x, base + 2, z, 'o');
            for (int r = 2; r < 10; r++) {
                int px = C + (int) Math.rint(r * sin), pz = C - (int) Math.rint(r * cos);
                if (s.get(px, base, pz) == 'B' && (px != x || pz != z)) s.set(px, base, pz, 'g');
            }
        }
    }

    /**
     * {@code deep_root()}: the Tidal Well over the deep-root chamber, its floor opened round the island by a grate ring
     * and over the lower pad, 25 x 39 x 25 (the chamber's floor slab Y0 - 39 at y0). Data for a later batch (CITY.md
     * §4.9, phase IV): its cells only. Its units, when they are built, must keep the well's eight taps first (their
     * slots are the well's), though the generator lists the bays before them.
     */
    public static ShapeKit.Shape deepRoot() {
        ShapeKit.Shape s = new ShapeKit.Shape("deep_root", SIZE, 39, SIZE);
        s.groundOutside = false;
        rootInto(s, 0);
        wellInto(s, 13, DEPTH);
        grate(s, 13);
        return s;
    }

    /**
     * {@code ley_core()}: the deep root over the sphere chamber (radius 8.5, centre Y0 - 48) with its crown platform
     * and eight source sockets, 25 x 57 x 25. Data for a later batch (CITY.md §4.9, UV): its cells only.
     */
    public static ShapeKit.Shape leyCore() {
        final int C = MID, baseRoot = 18, cy = 9;
        final double radius = 8.5;
        ShapeKit.Shape s = new ShapeKit.Shape("ley_core", SIZE, 57, SIZE);
        s.groundOutside = false;
        rootInto(s, baseRoot);
        wellInto(s, baseRoot + 13, DEPTH);
        grate(s, baseRoot + 13);
        // the sphere's shell: cells of the ball with a side neighbour outside it, lit on the eight lines and the
        // equator
        for (int y = 0; y < 18; y++) for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) {
            if (!ShapeKit.ballShell(x, y, z, C, cy, C, radius)) continue;
            int du = x - C, dv = z - C;
            boolean rib = du == 0 || dv == 0 || Math.abs(du) == Math.abs(dv);
            s.set(x, y, z, rib || y == cy ? 'L' : 'W');
        }
        // the sphere's top opens into the deep root's floor: the lift goes through
        s.set(C, baseRoot, C, '^');
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) {
            if (ShapeKit.hypot(x - C, z - C) < 1.5) for (int y = 14; y < 18; y++) s.clear(x, y, z);
        }
        // the crown platform (stand y15) inside the sphere's top: a deck oct(5, 7), a rail round it, the pad
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) {
            if (ShapeKit.octo(x - C, z - C, 5, 7) && ShapeKit.inBall(x, 14, z, C, cy, C, radius)) s.set(x, 14, z, 'd');
        }
        s.lift(C, 14, C);
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) {
            if (ShapeKit.octo(x - C, z - C, 5, 7) && !ShapeKit.octo(x - C, z - C, 4, 5) && s.get(x, 14, z) == 'd')
                s.set(x, 15, z, 'r');
        }
        for (int t = 0; t < 8; t++) {
            double a = radians(22.5 + 45 * t);
            int x = C + (int) Math.rint(3.6 * StrictMath.sin(a)), z = C - (int) Math.rint(3.6 * StrictMath.cos(a));
            s.set(x, 15, z, 'o');
        }
        return s;
    }

    /** The deep root's grate ring round the island in the well's floor at {@code y}, and the lift's way through. */
    private static void grate(ShapeKit.Shape s, int y) {
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) {
            if (s.get(x, y, z) == 'B' && onorm(x - MID, z - MID) == 3) s.set(x, y, z, ':');
        }
        s.set(MID, y, MID, ShapeKit.AIR);
    }
}
