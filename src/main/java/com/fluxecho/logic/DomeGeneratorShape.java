package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 回响穹顶发生器 The Echo Dome Generator (0.12.0, CITY.md §4.8): the zenith's module, a saucer pod {@code oct(10, 14)}
 * whose floor is Y0 + 40 over the nexus, standing on six curved legs that rise from the forum (the {@link #MOORED}
 * shape, phases II and III) until it is released to float (the {@link #FLOATING} shape, the pod alone, from phase IV).
 * <p>
 * The pod ({@link #SHAPE}, a port of the generator's {@code dome_pod()} in {@code city/gen/b_sky.py}): the floor
 * (foundation on the thick edge, soft deck inside, the light-lift pad in the middle), panoramic walls y1..y5 (a dark
 * course, glazing y2..y4 with lit mullions on the eight lines, panel y5), the cornice and the ceiling slab y6, a
 * stepped saucer roof (the rings {@code oct(7, 9)} and {@code oct(5, 6)} and a glazed {@code oct(2, 3)} cap, y7..y9)
 * round the axis shaft over the pad, six emitter nodes (frame conduit) on the roof, and inside the controller on its
 * console four in front of the pad with seven ward consoles round it (console and socket; one slot each, one per dome
 * function, {@link #UNITS}).
 * <p>
 * Two kinds of cell the generator leaves empty are built here as soft cells, never checked: the eight door openings
 * (three wide on the four flat faces and the four chamfers, y1..y4) are glazed ({@code /}) until a catwalk docks, and
 * the axis shaft over the pad (y6..y9) is capped with the blocks the roof has round it ({@code { { { /}) until the
 * orbital anchor's job opens it. The generator's own cells are {@link #RAW}.
 * <p>
 * The legs ({@link #legs()}) are frame pillars from the forum (Y0 + 1) to just under the pod's rim (Y0 + 39), their
 * radius {@code 9 + 5 (1 - h / 38)^1.6} at the angles 30 + 60k degrees: 234 cells, computed on the side R >= 0 with
 * {@link StrictMath} and mirrored across the gate axis (rounded the generator's way, half to even; the generator
 * rounds the two sides of the top cell apart). They miss the forum's light wells, the supply port and the recycle bin.
 * The legs ({@code P}) and the emitter nodes ({@code C}) give way to the rendered ribs and arcs once it forms, and
 * players pass through both ({@link #DISSOLVES}, {@link #PASSES}).
 * <p>
 * Frames: the pod's own frame is the module's frame (x across, 10 - R; y up from the pod floor; z from the pod's front,
 * 10 - A), as every renderer and window reads a module; the blueprint is the box the legs and the pod take together,
 * 29 across (R 14..-14), 49 high (Y0 + 1..Y0 + 49) and 25 deep (A 12..-12), with the pod's cell (0, 0, 0) at
 * {@link #OFFSET}. The controller {@code (10, 2, 6)} is campus {@code (A 4, R 0, Y0 + 42)}, facing +A (the gate).
 * <p>
 * Counts: the pod's checked 1,027 (moored 1,261), soft 276 + 96 door glazing + 4 shaft cap = 376, 1,637 steps.
 * <p>
 * The zenith adapts to high ground (0.14.1, CITY.md §1.3 Vertical): its sky datum F, the pod floor's height over the
 * base layer, is 40 wherever the pod's clear box (Y0 + F + 12) fits under the world's top, and steps down to 36, 32
 * and 28 over Y0 203, 207 and 211 ({@link #datum}); 28 is the least, the pod floor just over the phase V nexus and its
 * growth's clear box (Y0 + 27). The pod's cells are the same at every datum; only the legs are shorter (the
 * generator's {@code dome_legs(F)}, the same feet), so each datum is a moored shape of its own ({@link Moor}:
 * {@link #MOORED} 40 as before, {@link #MOORED_36}, {@link #MOORED_32}, {@link #MOORED_28}), and the floating shape
 * is one for every datum. Over Y0 218 the pod's top layers lie over the world's top: the plan leaves them out and the
 * structure check skips them, and the wards and the controller stay in the world up to Y0 225 ({@link #noRoom}).
 */
public final class DomeGeneratorShape {

    public static final String KEY = "dome_generator";
    /** The zenith (CITY.md §2.5): one per campus. */
    public static final int[] SITES = { SiteFamily.ZENITH_SITE };
    /** The two shapes: on its legs (phases II, III), floating (from phase IV, CITY.md §3). */
    public static final String MOORED = "moored", FLOATING = "floating";
    /** The moored shapes on shorter legs over high ground (0.14.1): names only ever appended. */
    public static final String MOORED_36 = "moored_36", MOORED_32 = "moored_32", MOORED_28 = "moored_28";
    /** The sky datums, highest first: the pod floor's height over the base layer, Y0 + F (CITY.md §1.3 Vertical). */
    private static final int[] DATUM_LIST = { 40, 36, 32, 28 };
    /** The nexus phase from which it may stand as {@link #FLOATING}. */
    public static final int FLOAT_PHASE = 4;
    /** The pod's box: 21 across and deep, the axis at {@link #MID}; the generator allots 10 layers (y0..y9). */
    public static final int SIZE = 21, MID = 10, HEIGHT = 10;
    /** The pod's octagon {@code oct(10, 14)} round the axis. */
    public static final int POD_R = CampusPlan.ZENITH_R, POD_K = CampusPlan.ZENITH_K;
    /** The pod floor's height over the base layer: Y0 + 40. */
    public static final int FLOOR_UP = 40;
    /** The controller over its console, four in front of the pad: campus (A 4, R 0, Y0 + 42). */
    public static final int CTRL_X = 10, CTRL_Y = 2, CTRL_Z = 6;
    /** The light-lift pad in the middle of the floor. */
    public static final int PAD_X = MID, PAD_Y = 0, PAD_Z = MID;
    /** The legs: how many, the height of their top cell over the base layer, their cells all together. */
    public static final int LEGS = 6, LEG_TOP = 39, LEG_CELLS = 234;
    /** A leg's radius at its foot and at its head, and the exponent of its curve. */
    public static final double LEG_FOOT = 14, LEG_HEAD = 9, LEG_CURVE = 1.6;
    /** The blueprint's box (the legs and the pod together) and where the pod's cell (0, 0, 0) lies in it. */
    public static final int BP_WIDTH = 29, BP_HEIGHT = 49, BP_DEPTH = 25;
    /** {across, up from the blueprint's lowest layer (Y0 + 1), rows from its front (A 12)}. */
    public static final int[] OFFSET = { 4, FLOOR_UP - 1, 2 };
    /** The legs and the emitter nodes give way to the rendered ribs and arcs; players pass through both. */
    public static final String DISSOLVES = "PC", PASSES = "PC";
    /** The leg's character (frame pillar). */
    public static final char LEG = 'P';
    /** The six emitter nodes on the roof (frame conduits at y7), {du, dv} from the axis; the arcs leave from them. */
    private static final int[][] NODE_OFFSETS = { { 7, 4 }, { -7, 4 }, { 7, -4 }, { -7, -4 }, { 0, 8 }, { 0, -8 } };
    /** The layer of the emitter nodes. */
    public static final int NODE_Y = 7;
    /** The seven ward consoles round the pad, {du, dv} from the axis, in unit order. */
    private static final int[][] WARD_OFFSETS = { { 6, 0 }, { -6, 0 }, { 0, 6 }, { 4, 4 }, { -4, 4 }, { 4, -4 },
        { -4, -4 } };

    /** The stages of its job (CITY.md §4.8). */
    public static final int Z_CLEAR = 0, Z_LEGS = 1, Z_FLOOR = 2, Z_SHELL = 3, Z_ROOF = 4, Z_FIT = 5, Z_COMMISSION = 6,
        Z_STAGES = 7;
    /** The clear box round the pod: this many cells round its box, up to this many over its roof (Y0 + 52). */
    public static final int POD_MARGIN = BuildPlan.MODULE_MARGIN, POD_HEADROOM = BuildPlan.MODULE_HEADROOM;
    /** The pod clear box's top over the base layer: Y0 + 52; a campus must have that much room under the sky. */
    public static final int CLEAR_TOP = FLOOR_UP + HEIGHT + POD_HEADROOM;

    /** The generator's cells (do not change it: the checksum test holds it to {@code dome_pod()}). */
    public static final ShapeKit.Shape RAW = build(false);
    /** The pod as it is built: the generator's cells, the door glazing and the shaft cap. */
    public static final ShapeKit.Shape SHAPE = build(true);
    /** The seven ward units, in slot order. */
    public static final UnitLayout UNITS = new UnitLayout(SHAPE);
    /** Its stage rule, the same at every datum. */
    private static final CityBuild.Stages STAGES = new CityBuild.Stages() {

        @Override
        public int stage(int x, int y, int z, char ch) {
            return DomeGeneratorShape.stage(x, y, z, ch);
        }

        @Override
        public int fitRank(int x, int y, int z, char ch) {
            return DomeGeneratorShape.fitRank(x, y, z, ch);
        }
    };
    /** The moored generator at each sky datum, highest first (0.14.1). */
    private static final List<Moor> MOORS = moors(DATUM_LIST);
    /** The legs in the campus frame, {a, r, dy} (dy 1..39 over the base layer), leg by leg, each from its foot. */
    private static final List<int[]> LEG_LIST = MOORS.get(0).legs;
    /** Every leg column {a, r} with the lowest and the highest dy a leg takes in it: {a, r, dyFrom, dyTo}. */
    private static final List<int[]> LEG_COLUMNS = MOORS.get(0).columns;
    /** The checked shapes: the pod and its legs, the pod alone (the same box, so the controller's cell is shared). */
    public static final Blueprint MOORED_BP = MOORS.get(0).blueprint, FLOATING_BP = blueprint(false, FLOOR_UP);

    /** The Echo Dome Generator on its full legs as its module plan reads it ({@link BuildPlan#zenith}). */
    public static final CityBuild BUILD = MOORS.get(0).build;

    static {
        if (UNITS.size() != 7 || UNITS.slots() != 7) throw new IllegalStateException("the ward units changed");
        if (LEG_LIST.size() != LEG_CELLS) throw new IllegalStateException("the legs changed");
    }

    /**
     * The generator moored at one sky datum F (0.14.1): its legs (the generator's {@code dome_legs(F)}: dy 1..F - 1,
     * radius {@code 9 + 5 (1 - h / (F - 2))^1.6}, the same feet at every datum), its blueprint (the legs and the pod
     * together, F + 9 high, the pod's cell (0, 0, 0) at {@link #offset}) and its build. The pod's frame is the module's
     * own at every datum (y up from the pod floor, Y0 + F); the controller is campus (A 4, R 0, Y0 + F + 2).
     */
    public static final class Moor {

        /** Its sky datum F: the pod floor at Y0 + F. */
        public final int datum;
        /** Its shape's name in the structure definitions ({@link #MOORED} at 40). */
        public final String name;
        /** Its checked blueprint and its build. */
        public final Blueprint blueprint;
        public final CityBuild build;
        final List<int[]> legs, columns;

        private Moor(int datum) {
            this.datum = datum;
            name = datum == FLOOR_UP ? MOORED : MOORED + "_" + datum;
            legs = legCells(datum);
            columns = DomeGeneratorShape.legColumns(legs);
            blueprint = DomeGeneratorShape.blueprint(true, datum);
            build = new CityBuild(
                KEY,
                SHAPE,
                HEIGHT,
                blueprint,
                UNITS,
                Parts.moduleCore(ModuleCores.DOME_GENERATOR),
                STAGES,
                offset());
        }

        /** The height of the legs' top cell over the base layer: Y0 + F - 1, just under the pod's rim. */
        public int legTop() {
            return datum - 1;
        }

        /** The pod clear box's top over the base layer: Y0 + F + 12. */
        public int clearTop() {
            return datum + HEIGHT + POD_HEADROOM;
        }

        /** {across, up from the blueprint's lowest layer (Y0 + 1), rows from its front (A 12)}. */
        public int[] offset() {
            return new int[] { OFFSET[0], datum - 1, OFFSET[2] };
        }

        /** A point of the pod as a campus cell {a, r, dy} (dy over the base layer). */
        public int[] podToCampus(int x, int y, int z) {
            return new int[] { MID - z, MID - x, datum + y };
        }

        /** A campus cell {a, r, dy} as a point of the pod {x, y, z} (it may lie outside the pod's box). */
        public int[] campusToPod(int a, int r, int dy) {
            return new int[] { MID - r, dy - datum, MID - a };
        }

        /** The cells of its legs, {a, r, dy} in the campus frame, leg by leg from the foot. */
        public List<int[]> legs() {
            List<int[]> out = new ArrayList<>(legs.size());
            for (int[] c : legs) out.add(c.clone());
            return out;
        }

        /** Its leg cells as points of the pod {x, y, z} (y from -(F - 1) to -1). */
        public List<int[]> legPoints() {
            List<int[]> out = new ArrayList<>(legs.size());
            for (int[] c : legs) out.add(campusToPod(c[0], c[1], c[2]));
            return out;
        }

        /** The columns its legs take, {a, r, dyFrom, dyTo}: the lowest and the highest leg cell in each. */
        public List<int[]> legColumns() {
            List<int[]> out = new ArrayList<>(columns.size());
            for (int[] c : columns) out.add(c.clone());
            return out;
        }

        /**
         * Where its controller stands on the zenith: {x, y, z, facing ordinal}, campus (A 4, R 0, Y0 + F + 2) facing
         * the nexus's front.
         *
         * @throws IllegalArgumentException if the site is not the zenith or the front not a horizontal unit vector
         */
        public int[] controller(int site, int cx, int y0, int cz, int fx, int fz) {
            int facing = BuildPlan.facingOrdinal(fx, fz);
            if (site != SiteFamily.ZENITH_SITE) throw new IllegalArgumentException("not the zenith: " + site);
            int[] a = podToCampus(CTRL_X, CTRL_Y, CTRL_Z);
            int[] w = CampusPlan.toWorld(a[0], a[1], fx, fz);
            return new int[] { cx + w[0], y0 + a[2], cz + w[1], facing };
        }

        /** Every leg column over the heights its leg takes there: {x0, z0, x1, z1, yFrom, yTo}. */
        public List<int[]> keepOut(int cx, int y0, int cz, int fx, int fz) {
            List<int[]> out = new ArrayList<>();
            for (int[] c : columns) {
                int[] w = CampusPlan.toWorld(c[0], c[1], fx, fz);
                out.add(new int[] { cx + w[0], cz + w[1], cx + w[0], cz + w[1], y0 + c[2], y0 + c[3] });
            }
            return out;
        }

        /** Every leg column over its leg's heights, and the pod's clear box from Y0 + F to Y0 + F + 12. */
        public List<int[]> protect(int cx, int y0, int cz, int fx, int fz) {
            List<int[]> out = new ArrayList<>();
            for (int[] c : columns) {
                int[] w = CampusPlan.toWorld(c[0], c[1], fx, fz);
                out.add(new int[] { cx + w[0], y0 + c[2], cz + w[1], cx + w[0], y0 + c[3], cz + w[1] });
            }
            int h = MID + POD_MARGIN;
            int[] b = box(cx, cz, fx, fz, -h, -h, h, h);
            out.add(new int[] { b[0], y0 + datum, b[1], b[2], y0 + clearTop(), b[3] });
            return out;
        }
    }

    private static List<Moor> moors(int[] datums) {
        List<Moor> out = new ArrayList<>();
        for (int f : datums) out.add(new Moor(f));
        return Collections.unmodifiableList(out);
    }

    /** The sky datums, highest first: 40, 36, 32, 28. */
    public static int[] datums() {
        return DATUM_LIST.clone();
    }

    /** The moored generator at every sky datum, the full legs (40) first. */
    public static List<Moor> moors() {
        return MOORS;
    }

    /**
     * The sky datum F over a campus whose base layer is {@code y0} (0.14.1): the highest of 40, 36, 32 and 28 whose
     * pod clear box, Y0 + F + 12, stays under the world's top (40 for Y0 <= 203, 36 for 204..207, 32 for 208..211);
     * 28 over that, where the pod's top layers clip.
     */
    public static int datum(int y0) {
        for (int f : DATUM_LIST) if (y0 + f + HEIGHT + POD_HEADROOM <= WORLD_TOP) return f;
        return DATUM_LIST[DATUM_LIST.length - 1];
    }

    /** The moored generator over a campus whose base layer is {@code y0} ({@link #datum}). */
    public static Moor forBase(int y0) {
        return moor(datum(y0));
    }

    /**
     * The moored generator at a sky datum.
     *
     * @throws IllegalArgumentException for a datum that is not one of {@link #datums}
     */
    public static Moor moor(int datum) {
        for (Moor m : MOORS) if (m.datum == datum) return m;
        throw new IllegalArgumentException("no sky datum " + datum);
    }

    /** The moored generator whose shape has the name, or null for the floating shape and any other name. */
    public static Moor moored(String name) {
        for (Moor m : MOORS) if (m.name.equals(name)) return m;
        return null;
    }

    /** The name of the moored shape the generator over a campus whose base layer is {@code y0} stands as. */
    public static String shapeFor(int y0) {
        return forBase(y0).name;
    }

    /**
     * The moored generator a controller at (x, y, z) stands for over a nexus whose base layer is {@code y0} and whose
     * front is (fx, fz), or null when it stands at neither of its cells (0.14.1): the one at the sky datum the base
     * layer gives ({@link #forBase}), where the builder raises it, else the one on the full legs, where 0.14.0 put
     * every generator, so one built there by hand over high ground before the lower datums came still forms and docks.
     */
    public static Moor mooredAt(int x, int y, int z, int cx, int y0, int cz, int fx, int fz) {
        for (Moor m : new Moor[] { forBase(y0), moor(FLOOR_UP) }) {
            int[] at = m.controller(SiteFamily.ZENITH_SITE, cx, y0, cz, fx, fz);
            if (at[0] == x && at[1] == y && at[2] == z) return m;
        }
        return null;
    }

    /** The highest y a block can stand at. */
    private static final int WORLD_TOP = 255;
    /** The highest layer of the pod a ward's socket or the controller takes: y2 over the pod floor. */
    private static final int STAND_TOP = 2;

    private DomeGeneratorShape() {}

    /** The module job on the zenith ({@link BuildPlan#zenith}). */
    public static BuildPlan.Plan plan(CampusPlan plan, int site, int cx, int y0, int cz, int fx, int fz) {
        return BuildPlan.zenith(site, cx, y0, cz, fx, fz);
    }

    /**
     * Where its controller stands on the zenith: {x, y, z, facing ordinal}, campus (A 4, R 0, Y0 + F + 2) for the sky
     * datum the base layer gives it ({@link #datum}; Y0 + 42 on its full legs) facing the nexus's front.
     *
     * @throws IllegalArgumentException if the site is not the zenith or the front not a horizontal unit vector
     */
    public static int[] controller(int site, int cx, int y0, int cz, int fx, int fz) {
        return forBase(y0).controller(site, cx, y0, cz, fx, fz);
    }

    /** A point of the pod on its full legs as a campus cell {a, r, dy} (dy over the base layer). */
    public static int[] podToCampus(int x, int y, int z) {
        return new int[] { MID - z, MID - x, FLOOR_UP + y };
    }

    /** A campus cell {a, r, dy} as a point of the pod on its full legs {x, y, z} (it may lie outside the pod's box). */
    public static int[] campusToPod(int a, int r, int dy) {
        return new int[] { MID - r, dy - FLOOR_UP, MID - a };
    }

    /** The character of a pod cell: the checked legend, a soft character or a space. */
    public static char cell(int x, int y, int z) {
        return SHAPE.get(x, y, z);
    }

    /** Whether the column (x, z) of the pod lies in its octagon. */
    public static boolean inPod(int x, int z) {
        return ShapeKit.octo(x - MID, z - MID, POD_R, POD_K);
    }

    /** The cells of the full legs, {a, r, dy} in the campus frame, leg by leg from the foot: 234 of them. */
    public static List<int[]> legs() {
        List<int[]> out = new ArrayList<>(LEG_LIST.size());
        for (int[] c : LEG_LIST) out.add(c.clone());
        return out;
    }

    /** The full legs' cells as points of the pod {x, y, z} (y from -39 to -1). */
    public static List<int[]> legPoints() {
        List<int[]> out = new ArrayList<>(LEG_LIST.size());
        for (int[] c : LEG_LIST) out.add(campusToPod(c[0], c[1], c[2]));
        return out;
    }

    /** The columns the full legs take, {a, r, dyFrom, dyTo}: the lowest and the highest leg cell in each. */
    public static List<int[]> legColumns() {
        List<int[]> out = new ArrayList<>(LEG_COLUMNS.size());
        for (int[] c : LEG_COLUMNS) out.add(c.clone());
        return out;
    }

    /** The leg's feet {a, r} (their dy-1 cells): (12, +-7), (0, +-14), (-12, +-7), the same at every datum. */
    public static List<int[]> feet() {
        List<int[]> out = new ArrayList<>();
        for (int[] c : LEG_LIST) if (c[2] == 1) out.add(new int[] { c[0], c[1] });
        return out;
    }

    /** The emitter nodes as points of the pod {x, y, z}: where the rendered arcs leave the roof. */
    public static List<int[]> nodes() {
        List<int[]> out = new ArrayList<>();
        for (int[] n : NODE_OFFSETS) out.add(new int[] { MID + n[0], NODE_Y, MID + n[1] });
        return out;
    }

    /** Whether a cell of the pod is one of the eight door openings (glazed soft until a catwalk docks). */
    public static boolean door(int x, int y, int z) {
        return y >= 1 && y <= 4 && cell(x, y, z) == '/' && RAW.get(x, y, z) == ShapeKit.NONE;
    }

    /** Whether a cell of the pod is the axis shaft's cap (soft until the orbital anchor's job opens it). */
    public static boolean shaftCap(int x, int y, int z) {
        return x == MID && z == MID && y >= 6 && y <= 9;
    }

    /**
     * The stage of a pod cell that is not part of a ward unit: FLOOR the floor (foundation, deck, the lift pad); SHELL
     * the walls y1..y5 and the door glazing; ROOF the cornice, the ceiling, the saucer, the shaft's cap and the
     * emitter nodes (y6..y9); FIT the controller's console; COMMISSION the controller. The legs are LEGS
     * ({@link BuildPlan#zenith} lays them; they are outside the pod's box).
     */
    public static int stage(int x, int y, int z, char ch) {
        if (ch == ShapeKit.CONTROLLER) return Z_COMMISSION;
        if (ch == LEG && y < 0) return Z_LEGS;
        if (y == 0) return Z_FLOOR;
        if (ch == 'K') return Z_FIT;
        if (y >= 1 && y <= 5) return Z_SHELL;
        if (y >= 6) return Z_ROOF;
        throw new IllegalStateException("no stage for '" + ch + "' at " + x + "," + y + "," + z);
    }

    /** The order within FIT of a cell that is no ward's: the controller's console, before the wards. */
    public static int fitRank(int x, int y, int z, char ch) {
        return -1;
    }

    /** Every layer of the generator's pod as rows, bottom up, rows from the front: the checksum test's input. */
    static List<String> rows(ShapeKit.Shape s) {
        List<String> out = new ArrayList<>();
        for (int y = 0; y < HEIGHT; y++) for (int z = 0; z < SIZE; z++) {
            char[] r = new char[SIZE];
            for (int x = 0; x < SIZE; x++) r[x] = s.get(x, y, z);
            out.add(new String(r));
        }
        return out;
    }

    /** The standing states {x, z, h} the generator's self-check walks from: on the lift pad. */
    public static List<int[]> walkStarts() {
        return Collections.singletonList(new int[] { PAD_X, PAD_Z, 2 * (PAD_Y + 1) });
    }

    // ---------------------------------------------------------------------------------------------------------------
    // the envelope: what it takes of the campus

    /**
     * Its box seen from above, {x0, z0, x1, z1} (inclusive): A -12..12 and R -14..14 (the legs' feet and the pod),
     * with {@code margin} round it, for the nexus centre {@code (cx, cz)} facing {@code (fx, fz)}.
     */
    public static int[] footprint(int cx, int cz, int fx, int fz, int margin) {
        int ha = MID + OFFSET[2] + margin, hr = MID + OFFSET[0] + margin;
        return box(cx, cz, fx, fz, -ha, -hr, ha, hr);
    }

    /**
     * What the campus jobs leave alone once it stands: every leg column over the heights its leg takes there (at the
     * datum the base layer gives it), as height-ranged keep-out boxes {x0, z0, x1, z1, yFrom, yTo}
     * ({@link BuildPlan#establish}). A grading column that reaches a leg is left out, so the forum and refresh jobs
     * never run into the legs; the forum's tile at Y0 and the pylons, the supply port and the recycle bin at Y0 + 1
     * under a leg that starts higher stay in the campus's plans, so a repair still puts them back.
     */
    public static List<int[]> keepOut(int cx, int y0, int cz, int fx, int fz) {
        return forBase(y0).keepOut(cx, y0, cz, fx, fz);
    }

    /**
     * The boxes {x0, y0, z0, x1, y1, z1} nothing but its own jobs takes a block down in: every leg column over the
     * heights its leg takes there, and the pod's clear box (its box with {@link #POD_MARGIN} round it, from Y0 + F to
     * Y0 + F + 12; Y0 + 40 to Y0 + {@link #CLEAR_TOP} on its full legs). The forum's tiles and fixtures under the legs,
     * and the nexus under the pod, stay the nexus's own.
     */
    public static List<int[]> protect(int cx, int y0, int cz, int fx, int fz) {
        return forBase(y0).protect(cx, y0, cz, fx, fz);
    }

    /**
     * Why a campus whose base layer is {@code y0} has no room for it ({@code build.module.why.height}: even at the
     * lowest datum its controller or a ward would lie over the world's top, over Y0 225), or null. The pod's higher
     * layers may clip at the world's top (0.14.1): the plan leaves those cells out and the structure check skips them.
     */
    public static String noRoom(int y0) {
        return y0 + datum(y0) + STAND_TOP > WORLD_TOP ? "build.module.why.height" : null;
    }

    /** The world box {x0, z0, x1, z1} of the campus rectangle a0..a1 by r0..r1. */
    private static int[] box(int cx, int cz, int fx, int fz, int a0, int r0, int a1, int r1) {
        int[] p = CampusPlan.toWorld(a0, r0, fx, fz), q = CampusPlan.toWorld(a1, r1, fx, fz);
        return new int[] { cx + Math.min(p[0], q[0]), cz + Math.min(p[1], q[1]), cx + Math.max(p[0], q[0]),
            cz + Math.max(p[1], q[1]) };
    }

    // ---------------------------------------------------------------------------------------------------------------
    // the generator (a port of b_sky.py's dome_pod and dome_legs)

    /**
     * The legs at the sky datum {@code datum} (F): for each of the six angles 30 + 60k degrees, the cells of radius
     * {@code 9 + 5 (1 - h / (F - 2))^1.6} at the heights h = 0..F - 2 (dy = 1 + h), rounded half to even as the
     * generator's {@code round} (F 40: {@code 9 + 5 (1 - h / 38)^1.6}, h = 0..38). The legs on the side R < 0 (210, 270
     * and 330 degrees) are the mirror images of those at 150, 90 and 30 degrees.
     */
    private static List<int[]> legCells(int datum) {
        List<int[]> out = new ArrayList<>();
        for (int k = 0; k < LEGS; k++) {
            int src = k < LEGS / 2 ? k : LEGS - 1 - k, sign = k < LEGS / 2 ? 1 : -1;
            double th = Math.toRadians(30 + 60 * src);
            double cos = StrictMath.cos(th), sin = StrictMath.sin(th);
            for (int h = 0; h < datum - 1; h++) {
                double r = LEG_HEAD + (LEG_FOOT - LEG_HEAD) * StrictMath.pow(1 - h / (double) (datum - 2), LEG_CURVE);
                out.add(new int[] { (int) Math.rint(r * cos), sign * (int) Math.rint(r * sin), 1 + h });
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static List<int[]> legColumns(List<int[]> legs) {
        Map<Long, int[]> cols = new LinkedHashMap<>();
        for (int[] c : legs) {
            int[] col = cols.computeIfAbsent(ShapeKit.key(c[0], c[1]), k -> new int[] { c[0], c[1], c[2], c[2] });
            col[2] = Math.min(col[2], c[2]);
            col[3] = Math.max(col[3], c[2]);
        }
        return Collections.unmodifiableList(new ArrayList<>(cols.values()));
    }

    /**
     * The checked blueprint at the sky datum {@code datum} (F): the pod's checked cells (soft ones left out) with its
     * cell (0, 0, 0) F - 1 over the blueprint's lowest layer, and with {@code legs} every leg cell; F + 9 high
     * ({@link #BP_HEIGHT} at 40). The floating shape's is the one at 40 without legs, for every datum: the pod's cells
     * round the controller are the same.
     */
    private static Blueprint blueprint(boolean legs, int datum) {
        int height = datum + HEIGHT - 1, up = datum - 1;
        char[][][] g = new char[height][BP_DEPTH][BP_WIDTH];
        for (char[][] layer : g) for (char[] row : layer) java.util.Arrays.fill(row, ' ');
        for (int y = 0; y < HEIGHT; y++) for (int z = 0; z < SIZE; z++) for (int x = 0; x < SIZE; x++) {
            char ch = SHAPE.get(x, y, z);
            if (ch == ShapeKit.NONE || ShapeKit.soft(ch)) continue;
            g[height - 1 - (y + up)][z + OFFSET[2]][x + OFFSET[0]] = ch;
        }
        if (legs) for (int[] c : legCells(datum)) {
            // a campus cell {a, r, dy} as a point of the pod at this datum
            int px = MID - c[1], py = c[2] - datum, pz = MID - c[0];
            g[height - 1 - (py + up)][pz + OFFSET[2]][px + OFFSET[0]] = LEG;
        }
        String[][] layers = new String[height][BP_DEPTH];
        for (int b = 0; b < height; b++) for (int c = 0; c < BP_DEPTH; c++) layers[b][c] = new String(g[b][c]);
        return new Blueprint(layers);
    }

    /**
     * The pod: {@code dome_pod()} cell for cell; with {@code built} the door openings glazed and the shaft capped
     * (soft).
     */
    private static ShapeKit.Shape build(boolean built) {
        final int N = SIZE, C = MID;
        ShapeKit.Shape s = new ShapeKit.Shape("dome_pod", N, HEIGHT, N);
        s.groundOutside = false;
        Set<Long> plan = new LinkedHashSet<>();
        for (int x = 0; x < N; x++)
            for (int z = 0; z < N; z++) if (ShapeKit.octo(x - C, z - C, POD_R, POD_K)) plan.add(ShapeKit.key(x, z));
        Set<Long> edge = ShapeKit.thickBoundary(plan);
        Set<Long> doors = new LinkedHashSet<>();
        for (long k : edge) {
            int du = ShapeKit.keyX(k) - C, dv = ShapeKit.keyZ(k) - C;
            // the four flat faces, and the four chamfers
            if (Math.abs(du) <= 1 && Math.abs(dv) >= 9 || Math.abs(dv) <= 1 && Math.abs(du) >= 9) doors.add(k);
            if (Math.abs(Math.abs(du) - Math.abs(dv)) <= 1 && Math.min(Math.abs(du), Math.abs(dv)) >= 5) doors.add(k);
        }
        for (long k : plan) s.set(ShapeKit.keyX(k), 0, ShapeKit.keyZ(k), edge.contains(k) ? 'F' : '.');
        s.lift(C, 0, C);
        for (long k : edge) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k), du = x - C, dv = z - C;
            for (int y = 1; y < 6; y++) {
                if (doors.contains(k) && y <= 4) {
                    // unchecked: glazed now, air once a catwalk docks
                    if (built) s.set(x, y, z, '/');
                    continue;
                }
                char ch = 'W';
                if (y == 1) ch = 'w';
                else if (y <= 4) ch = du == 0 || dv == 0 || Math.abs(du) == Math.abs(dv) ? 'L' : 'G';
                s.set(x, y, z, ch);
            }
        }
        for (long k : plan) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k), du = x - C, dv = z - C;
            s.set(x, 6, z, edge.contains(k) ? 'n' : 'W');
            if (ShapeKit.octo(du, dv, 7, 9)) s.set(x, 7, z, !ShapeKit.octo(du, dv, 6, 8) ? 'L' : 'W');
            if (ShapeKit.octo(du, dv, 5, 6)) s.set(x, 8, z, !ShapeKit.octo(du, dv, 4, 5) ? 'L' : 'W');
            if (ShapeKit.octo(du, dv, 2, 3)) s.set(x, 9, z, !ShapeKit.octo(du, dv, 1, 1) ? 'L' : 'G');
        }
        // the shaft over the pad: the generator leaves it open; built, it is capped (soft) until phase V
        for (int y = 6; y < 10; y++) {
            char cap = ShapeKit.checkedTwin(s.get(C, y, C)) == 'G' ? '/' : '{';
            if (built) s.set(C, y, C, cap);
            else s.clear(C, y, C);
        }
        for (int[] n : NODE_OFFSETS) s.set(C + n[0], NODE_Y, C + n[1], 'C');
        // the controller in front of the pad (toward the gate), seven ward consoles round the pad
        s.set(C, 1, C - 4, 'K');
        s.set(C, 2, C - 4, ShapeKit.CONTROLLER);
        s.controller = new int[] { C, 2, C - 4 };
        for (int i = 0; i < WARD_OFFSETS.length; i++) {
            int x = C + WARD_OFFSETS[i][0], z = C + WARD_OFFSETS[i][1];
            s.set(x, 1, z, 'K');
            s.set(x, 2, z, 'o');
            List<int[]> cells = new ArrayList<>();
            cells.add(new int[] { x, 1, z });
            cells.add(new int[] { x, 2, z });
            s.unit("ward-" + (i + 1), "ward", cells, 1, null);
        }
        return s;
    }
}
