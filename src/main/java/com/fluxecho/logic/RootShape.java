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
 * The root under the nexus (0.12.0, CITY.md §4.9): one module, {@code root}, whose shapes grow downward. Its first
 * shape is 潮汐井 the Tidal Well ({@link #WELL}); since 0.16.0 a formed well grows into 深根 the deep root
 * ({@link Deep}); the ley core is ported as data only ({@link #leyCore()}) for the batch that builds it.
 * <p>
 * The deep root takes the room the ground leaves (0.16.0, "root by room", CITY.md §1.3 Vertical): with D the depth of
 * the well it grows from, a chamber of 13 layers under the well's floor slab where its own slab stays at y
 * {@link #LOWEST_SLAB} or over ({@link #DEEP}), else one of 7 ({@link #ROOT_7}), else the roots come down into the
 * well itself ({@link #ROOT_IN}, {@link #ROOT_IN_20}, {@link #ROOT_IN_14}, {@link #ROOT_IN_8}): every campus that has a
 * well has a deep root ({@link #deepFor}). Each keeps the well's cells, its controller and its taps (slots 0 to 7) and
 * adds eight bays (slots 8 to 15).
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
    /**
     * The rings give way to the rendered tide rings, and (0.16.0) the deep root's hanging conduits to its flowing light
     * veins; players pass through both (nobody stands where the rings hang, and a conduit hangs over the chamber's
     * floor
     * or the pool). The wells have no conduit, so nothing changes for them.
     */
    public static final String DISSOLVES = "RC", PASSES = "RC";
    /** The ring segment's character, and (0.16.0) the deep root's conduit's. */
    public static final char RING = 'R', CONDUIT = 'C';

    /**
     * The deep root's other shapes over lower ground (0.16.0, "root by room"): the chamber one 6-layer band lower, and
     * the in-well forms over a well 26, 20, 14 and 8 deep; names only ever appended, after {@link #WELL_8}. The full
     * deep root is {@link #DEEP}.
     */
    public static final String ROOT_7 = "root_7", ROOT_IN = "root_in", ROOT_IN_20 = "root_in_20",
        ROOT_IN_14 = "root_in_14", ROOT_IN_8 = "root_in_8";
    /**
     * The deep root's chamber heights in layers (its floor slab and its walls), fullest first: 13, or one 6-layer band
     * less over lower ground (the same step the well's depths take).
     */
    private static final int[] CHAMBER_LIST = { 13, 7 };
    /**
     * The in-well form's bays stand where the eight lines cross the basin rim (octagon radius 8), its conduits hang at
     * radius 6, just outside the tide rings (at most 5.5).
     */
    public static final int INWELL_BAY_RING = 8, INWELL_CONDUIT_R = 6;
    /** The chamber's octagon {@code oct(12, 16)}, one wider than the well's. */
    public static final int CHAMBER_R = 12, CHAMBER_K = 16;
    /** The phase the deep root stands from (CITY.md §3: phase IV). */
    public static final int DEEP_PHASE = 4;
    /**
     * The samples every root tile keeps (0.16.0): the well's eight taps and the deep root's eight bays. A well shows
     * the taps' only; its 8-slot saves load into the first eight.
     */
    public static final int SLOTS = 16;

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

    /** The columns of the deep root's chamber octagon, and those of its wall. */
    private static final Set<Long> CHAMBER_PLAN = plan(SIZE, CHAMBER_R, CHAMBER_K),
        CHAMBER_WALL = ShapeKit.thickBoundary(CHAMBER_PLAN);
    /**
     * The deep root's shapes (0.16.0), in the order their names were appended: the full chamber ({@link #DEEP}), the
     * 7-layer chamber, then the in-well forms over the wells 26, 20, 14 and 8 deep.
     */
    private static final List<Deep> DEEPS = makeDeeps();

    static {
        for (Deep d : DEEPS) {
            if (d.units.size() != 16 || d.units.slots() != SLOTS)
                throw new IllegalStateException("the deep root's units changed: " + d.name);
            // its taps keep the well's cells (moved up by the chamber) and slots 0..7; its bays follow
            for (int i = 0; i < 8; i++) {
                int[] t = d.units.unit(i).cells.get(0), w = UNITS.unit(i).cells.get(0);
                if (t[0] != w[0] || t[1] != w[1] + d.h || t[2] != w[2] || !"tap".equals(d.units.unit(i).kind))
                    throw new IllegalStateException("a tap moved in " + d.name);
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

    /**
     * 深根 The deep root in one of its forms (0.16.0, CITY.md §4.9, "root by room"): the Tidal Well it grows from, and
     * either a chamber of {@link #h} layers under that well's floor slab (the generator's {@code deep_root(h)}: 13, or
     * 7
     * over lower ground) or, where no chamber fits, the roots come down into the well itself ({@code deep_root_in(D)},
     * {@code h} 0): eight bays on the basin rim and eight conduits from the lid, the well's own box, nothing dug.
     * <p>
     * Its frame is its shape's own: x across, y up from its lowest layer (the chamber's floor slab at Y0 - D - h, or
     * the
     * well's at Y0 - D), z from the front. The well lies at y {@code h} and up, its cells as the well's but for the
     * 24-cell grate in its floor slab and the slab's open centre over a chamber; the controller is the well's, at
     * {@code (12, h + 3, 10)}, the same block in the world. Its units are the well's eight taps first (slots 0 to 7,
     * the well's), then the eight bays (slots 8 to 15), though the chamber's generator lists the bays first.
     */
    public static final class Deep {

        /** The well it grows from: the full well under a chamber, the well of its own depth in-well. */
        public final Well well;
        /** Its chamber's layers under the well's floor slab (13 or 7), or 0 for an in-well form. */
        public final int h;
        /** Its shape's name in the structure definitions ({@link #DEEP} for the full chamber). */
        public final String name;
        /** Its cells, units, checked blueprint and build. */
        public final ShapeKit.Shape shape;
        public final UnitLayout units;
        public final Blueprint blueprint;
        public final CityBuild build;
        /** The bays' cells, as {@link ShapeKit.Shape#pos} keys. */
        private final Set<Long> bays = new java.util.HashSet<>();

        private Deep(int h, Well well) {
            if (h > 0 && well.depth != DEPTH) throw new IllegalArgumentException("a chamber under a shallower well");
            this.h = h;
            this.well = well;
            name = deepName(h, well.depth);
            String gen = h == 0 ? (well.depth == DEPTH ? "deep_root_in" : "deep_root_in_" + well.depth)
                : h == CHAMBER_LIST[0] ? "deep_root" : "deep_root_" + h;
            shape = new ShapeKit.Shape(gen, SIZE, h + well.depth, SIZE);
            shape.groundOutside = false;
            List<Object[]> taps, bayUnits;
            if (h > 0) {
                // the generator's deep_root(h): the chamber, the well over it, the grate in the well's floor
                bayUnits = rootInto(shape, 0, h);
                taps = wellInto(shape, h, well.depth);
                grate(shape, h);
            } else {
                // its deep_root_in(D): the well, and the roots come down into it
                taps = wellInto(shape, 0, well.depth);
                bayUnits = inWell(shape, well.depth);
            }
            Map<String, Integer> usedFrom = new HashMap<>();
            for (Object[] u : taps) addUnit(shape, u);
            for (Object[] u : bayUnits) {
                addUnit(shape, u);
                @SuppressWarnings("unchecked")
                List<int[]> cells = (List<int[]>) u[2];
                for (int[] c : cells) bays.add(ShapeKit.Shape.pos(c[0], c[1], c[2]));
                // an in-well bay stands on the floor level, among the taps, so the plan shows them on one storey
                if (h == 0) usedFrom.put((String) u[0], FLOOR_Y);
            }
            units = new UnitLayout(shape, usedFrom);
            blueprint = RootShape.blueprint(shape);
            final Deep self = this;
            build = new CityBuild(
                KEY,
                shape,
                shape.H,
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

        /** The character of a cell: the checked legend, a soft character, {@code -} or a space. */
        public char cell(int x, int y, int z) {
            return shape.get(x, y, z);
        }

        /** The character the well it grows from has at a cell of this frame (a space under or over the well). */
        public char wellCell(int x, int y, int z) {
            return well.cell(x, y - h, z);
        }

        /**
         * Whether a cell belongs to the chamber: under the well's floor slab, or the ring round that slab between the
         * chamber's wall and the well's ({@code oct(12, 16)} less {@code oct(11, 15)}). None in-well.
         */
        public boolean inChamber(int x, int y, int z) {
            if (h == 0 || y < 0 || !CHAMBER_PLAN.contains(ShapeKit.key(x, z))) return false;
            return y < h || y == h && !inWell(x, z);
        }

        /** Whether a cell is the chamber's shell: its floor slab, its walls, the ring under the well's floor. */
        public boolean chamberShell(int x, int y, int z) {
            return inChamber(x, y, z) && (y == 0 || y == h || CHAMBER_WALL.contains(ShapeKit.key(x, z)));
        }

        /** Whether a cell lies inside the chamber's shell: what its caisson digs out and drains. */
        public boolean chamberInside(int x, int y, int z) {
            return inChamber(x, y, z) && !chamberShell(x, y, z);
        }

        /** Whether a cell is the shell of the whole form: the chamber's, or the well's over it. */
        public boolean shell(int x, int y, int z) {
            return chamberShell(x, y, z) || y >= h && well.shell(x, y - h, z);
        }

        /** Whether a cell lies inside the shell of the whole form: the chamber's inside, or the well's. */
        public boolean inside(int x, int y, int z) {
            return chamberInside(x, y, z) || y > h && well.inside(x, y - h, z);
        }

        /** Whether a cell is one of a bay's (its plinth or its socket). */
        public boolean bay(int x, int y, int z) {
            return bays.contains(ShapeKit.Shape.pos(x, y, z));
        }

        /**
         * The stage of a cell that is not part of a tide tap: the chamber's shell SINK; its conduits (and the in-well
         * conduits) and the bays FIT; every cell of the well as the well's ({@link Well#stage}), the grate and the open
         * centre in its floor slab with the slab in SINK.
         */
        public int stage(int x, int y, int z, char ch) {
            if (ch == ShapeKit.CONTROLLER) return R_COMMISSION;
            if (ch == CONDUIT || bay(x, y, z)) return R_FIT;
            if (inChamber(x, y, z)) {
                if (chamberShell(x, y, z)) return R_SINK;
                throw new IllegalStateException("no stage for '" + ch + "' at " + x + "," + y + "," + z);
            }
            return well.stage(x, y - h, z, ch);
        }

        /**
         * Where its controller stands under the nexus: the well's ({@link Well#controller}), campus (A 2, R 0, Y0 - D +
         * 3), the same block for every form.
         *
         * @throws IllegalArgumentException if the site is not the root's or the front not a horizontal unit vector
         */
        public int[] controller(int site, int cx, int y0, int cz, int fx, int fz) {
            return well.controller(site, cx, y0, cz, fx, fz);
        }

        /**
         * The box {x0, y0, z0, x1, y1, z1} nothing but its own jobs takes a block down in: with a chamber its
         * octagon's box and one more round it (the chamber's wider wall) from the chamber's floor slab (Y0 - D - h) to
         * the lid; in-well the well's own.
         */
        public List<int[]> protect(int cx, int y0, int cz, int fx, int fz) {
            if (h == 0) return well.protect(cx, y0, cz, fx, fz);
            int[] b = footprint(cx, cz, fx, fz, 1);
            return Collections.singletonList(new int[] { b[0], y0 - well.depth - h, b[1], b[2], y0 - 1, b[3] });
        }

        /** A point of the form as a campus cell {a, r, dy} (dy from the base layer, negative under it). */
        public int[] toCampus(int x, int y, int z) {
            return new int[] { MID - z, MID - x, y - h - well.depth };
        }

        /** A campus cell {a, r, dy} as a point of the form {x, y, z} (it may lie outside its box). */
        public int[] fromCampus(int a, int r, int dy) {
            return new int[] { MID - r, dy + h + well.depth, MID - a };
        }
    }

    private static List<Deep> makeDeeps() {
        List<Deep> out = new ArrayList<>();
        for (int h : CHAMBER_LIST) out.add(new Deep(h, well(DEPTH)));
        for (int d : DEPTH_LIST) out.add(new Deep(0, well(d)));
        return Collections.unmodifiableList(out);
    }

    /** The deep root's chamber heights, fullest first: 13, 7. */
    public static int[] chambers() {
        return CHAMBER_LIST.clone();
    }

    /**
     * The deep root's chamber over a campus whose base layer is {@code y0} and whose formed well is {@code depth} deep
     * (0.16.0, the generator's {@code chamber(y0, D)}): the first of 13 and 7 whose floor slab, Y0 - D - h, stays at y
     * {@link #LOWEST_SLAB} or over, or 0 when none fits (the in-well form). The depth is the well the root actually
     * stands as (its controller's height), never {@link #depth}: a 0.14.0 well dug by hand over low ground is 26 deep.
     * Under the full well 13 for Y0 >= 45, 7 for 39..44, 0 under that.
     */
    public static int chamber(int y0, int depth) {
        for (int h : CHAMBER_LIST) if (y0 - depth - h >= LOWEST_SLAB) return h;
        return 0;
    }

    /**
     * How long a root conduit hanging from a ceiling slab at layer {@code ceil} (the floor slab at 0) is (0.16.0, the
     * generator's {@code conduit_len}): through the upper half of the room, never lower than 3 over the layer people
     * stand on ({@code stand}), so 1.2 over a head: 6 in the 13-chamber, 3 in the 7-chamber, 12, 9, 6 and 2 in the
     * wells 26, 20, 14 and 8 deep.
     */
    public static int conduitLen(int ceil, int stand) {
        return Math.min(Math.floorDiv(ceil - 1, 2), ceil - stand - 3);
    }

    /**
     * The shape name of the deep root with a chamber of {@code h} layers (13 or 7) under the full well, or with
     * {@code h} 0 in the well {@code depth} deep.
     *
     * @throws IllegalArgumentException for a chamber height or a depth it has no shape for
     */
    public static String deepName(int h, int depth) {
        if (h == CHAMBER_LIST[0]) return DEEP;
        if (h == CHAMBER_LIST[1]) return ROOT_7;
        if (h != 0) throw new IllegalArgumentException("no deep root chamber " + h + " high");
        well(depth);
        return depth == DEPTH ? ROOT_IN : ROOT_IN + "_" + depth;
    }

    /** The deep root's forms (0.16.0): the two chambers, then the in-well forms, as their names were appended. */
    public static List<Deep> deeps() {
        return DEEPS;
    }

    /** The deep root's form whose shape has the name, or null for any other name (a well's too). */
    public static Deep deep(String name) {
        for (Deep d : DEEPS) if (d.name.equals(name)) return d;
        return null;
    }

    /**
     * The deep root a formed well {@code w} grows into over a campus whose base layer is {@code y0} (0.16.0, "root by
     * room", the generator's {@code deep_root_for}): the chamber when one fits under it ({@link #chamber}), else the
     * in-well form of its depth. A chamber only ever lies under the full well; a shallower well (under Y0 32) never
     * has room for one.
     */
    public static Deep deepFor(int y0, Well w) {
        int h = w.depth == DEPTH ? chamber(y0, w.depth) : 0;
        return deep(deepName(h, w.depth));
    }

    /**
     * The deep root's form the deepen job would grow the root into now (0.16.0, CITY.md §4.9), or null when there is
     * none to offer: the nexus has grown to phase IV ({@code phase}), its teams have the deep root's research
     * ({@code researched}), and the root docked under it stands formed as a well ({@code formed}, {@code shape}). The
     * well's depth is read from its controller's height ({@code ctrlY}) over the base layer {@code y0}, the depth it
     * really stands at (a 0.14.0 well dug by hand over low ground is 26 deep), and must be the well it formed as; its
     * form is then {@link #deepFor}. A root that stands as a deep root already has nothing to grow.
     */
    public static String deepenTo(int phase, boolean researched, boolean formed, String shape, int y0, int ctrlY) {
        if (phase < DEEP_PHASE || !researched || !formed) return null;
        Well w = named(shape);
        if (w == null || y0 + CTRL_Y - ctrlY != w.depth) return null;
        return deepFor(y0, w).name;
    }

    /**
     * Where the well's frame lies in the frame of the shape named (0.16.0): the layer of the well's floor slab, the
     * chamber's height under a chamber, 0 for a well and an in-well form. The well's drawing ({@link RootLook}) adds
     * it.
     */
    public static int base(String shapeName) {
        Deep d = deep(shapeName);
        return d == null ? 0 : d.h;
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
     * grading or a repair of the campus stops at the lid. Where the deep root has a chamber under the well (0.16.0,
     * Y0 39 and up, {@link #chamber}) the box takes in the ground it grows into: one wider (the chamber's wall) and
     * down
     * to the chamber's floor slab, so no other job digs there before or after; nothing else ever digs that ground.
     */
    public static List<int[]> protect(int cx, int y0, int cz, int fx, int fz) {
        Well w = forBase(y0);
        int h = w.depth == DEPTH ? chamber(y0, w.depth) : 0;
        return h > 0 ? deep(deepName(h, w.depth)).protect(cx, y0, cz, fx, fz) : w.protect(cx, y0, cz, fx, fz);
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
     * {@code root_into(s, base, h)}: the deep-root chamber {@code oct(12, 16)} of {@code h} layers (13, or 7 over lower
     * ground, 0.16.0) with its floor slab at {@code base} and its ceiling the well's floor slab at base + h: walls, the
     * ring under the well floor, the pad, eight hanging root conduits ({@link #conduitLen} long) and eight root bays
     * with lit veins to the middle. Returns the bays {name, kind, cells, slots} in the generator's order.
     */
    private static List<Object[]> rootInto(ShapeKit.Shape s, int base, int h) {
        final int C = MID;
        Set<Long> plan = plan(SIZE, CHAMBER_R, CHAMBER_K), edge = ShapeKit.thickBoundary(plan);
        List<Object[]> units = new ArrayList<>();
        for (long k : plan) s.set(ShapeKit.keyX(k), base, ShapeKit.keyZ(k), edge.contains(k) ? 'F' : 'B');
        for (long k : edge) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k), du = x - C, dv = z - C;
            boolean rib = du == 0 || dv == 0 || Math.abs(du) == Math.abs(dv);
            for (int y = 1; y < h; y++) s.set(x, base + y, z, y == h - 1 ? 'n' : rib ? 'L' : y == 1 ? 'w' : 'W');
        }
        // the ring under the well floor between the two walls, at the ceiling's level
        for (long k : plan) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k);
            if (!ShapeKit.octo(x - C, z - C, WELL_R, WELL_K)) s.set(x, base + h, z, 'B');
        }
        s.lift(C, base, C);
        // hanging root conduits from the ceiling at radius 5: six cells down in the full chamber, three in the
        // 7-chamber
        int len = conduitLen(h, 1);
        for (int t = 0; t < 8; t++) {
            double a = radians(45 * t);
            int x = C + (int) Math.rint(5 * StrictMath.sin(a)), z = C - (int) Math.rint(5 * StrictMath.cos(a));
            for (int y = h - len; y < h; y++) s.set(x, base + y, z, CONDUIT);
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
            List<int[]> cells = new ArrayList<>();
            cells.add(new int[] { x, base + 1, z });
            cells.add(new int[] { x, base + 2, z });
            units.add(new Object[] { "bay-" + (t + 1), "bay", cells, 1 });
            for (int r = 2; r < 10; r++) {
                int px = C + (int) Math.rint(r * sin), pz = C - (int) Math.rint(r * cos);
                if (s.get(px, base, pz) == 'B' && (px != x || pz != z)) s.set(px, base, pz, 'g');
            }
        }
        return units;
    }

    /**
     * {@code in_well(s, D)} (0.16.0): the deep root where no chamber fits under the well, the roots come down into the
     * well {@code depth} deep already in {@code s} (its slab at y0). Eight root bays where the eight lines cross the
     * basin rim ({@link #INWELL_BAY_RING}: plinth y2, socket y3, on the axes at the bridges' outer ends (0, 8), on the
     * diagonals on the rim (5, 5)); eight root conduits from the lid at radius {@link #INWELL_CONDUIT_R}, outside the
     * tide rings, {@code conduitLen(lid, 2)} long. Only cells the well leaves empty are set. Returns the bays.
     *
     * @throws IllegalStateException when a cell it sets is not empty
     */
    private static List<Object[]> inWell(ShapeKit.Shape s, int depth) {
        final int C = MID, lid = depth - 1, len = conduitLen(lid, 2);
        List<Object[]> units = new ArrayList<>();
        for (int k = 0; k < 8; k++) {
            double a = radians(45 * k), sin = StrictMath.sin(a), cos = StrictMath.cos(a);
            // the line's unit step as the generator rounds it (0, 1 or 0.707: no halves, so rint is Python's round)
            int sx = (int) Math.rint(sin), sz = -(int) Math.rint(cos);
            int t = 1;
            while (t < 12 && onorm(t * sx, t * sz) != INWELL_BAY_RING) t++;
            if (t == 12) throw new IllegalStateException("no bay on line " + k);
            int x = C + t * sx, z = C + t * sz;
            List<int[]> cells = new ArrayList<>();
            int[][] parts = { { 2, 'p' }, { 3, 'o' } };
            for (int[] yc : parts) {
                if (s.get(x, yc[0], z) != ShapeKit.NONE)
                    throw new IllegalStateException("a bay on a cell of the well at " + x + "," + yc[0] + "," + z);
                s.set(x, yc[0], z, (char) yc[1]);
                cells.add(new int[] { x, yc[0], z });
            }
            units.add(new Object[] { "bay-" + (k + 1), "bay", cells, 1 });
            int cx = C + (int) Math.rint(INWELL_CONDUIT_R * sin), cz = C - (int) Math.rint(INWELL_CONDUIT_R * cos);
            for (int y = lid - len; y < lid; y++) {
                if (s.get(cx, y, cz) != ShapeKit.NONE)
                    throw new IllegalStateException("a conduit on a cell of the well at " + cx + "," + y + "," + cz);
                s.set(cx, y, cz, CONDUIT);
            }
        }
        return units;
    }

    /**
     * {@code deep_root()}: the Tidal Well over the deep-root chamber, its floor opened round the island by a grate ring
     * and over the lower pad, 25 x 39 x 25 (the chamber's floor slab Y0 - 39 at y0): the shape of {@link #DEEP}
     * (0.16.0, {@link Deep}), its units the well's eight taps first (their slots are the well's), then the bays, though
     * the generator lists the bays before them.
     */
    public static ShapeKit.Shape deepRoot() {
        return deep(DEEP).shape;
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
        rootInto(s, baseRoot, CHAMBER_LIST[0]);
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
