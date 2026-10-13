package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 回响猎场 The Echo Hunting Ground (0.13.0, CITY.md §4.5): a pavilion that is an open octagonal amphitheatre, the
 * lowest of the four (its bowl reads against the arcane spire across the gate). Its outer wall stands on the plan's
 * octagon {@code oct(12, 16)} (y1..8: arcade glazing y6..7 between lit pilasters every 3 on the flat faces, lit jambs
 * at the four chamfer portals) under a cornice at y8, open to the sky; over the top terraces, octagon radius 10..11,
 * runs the velarium, a ring of frame ring segments at y9 that gives way to a rendered hex canopy once the ground forms.
 * <p>
 * The arena at the middle (octagon radius 5 and in) is a holo grid in the floor, grate and soft lit tiles alternating,
 * round the holo emitter (the controller on its console) and its four holo pedestals at radius 2. Seating rises round
 * it in half-block terraces, one a ring from octagon radius 6 (one stands at 1.5) to 11 (at 4), walkable everywhere
 * without a jump; four vomitoria (the diagonal bands {@code ||du| - |dv|| <= 1} from the portals in to radius 6) cut
 * through the terraces at ground level, bridged by deck at y3 from radius 9 out. Twelve imprint consoles (a plinth and
 * a socket) stand on the arena's rim at 30 degree steps, round from the front: 12 units, 12 slots; their order is the
 * slot order and never changes ({@link #UNITS}).
 * <p>
 * The geometry is an exact port of the generator ({@code city/gen/b_pav.py}, {@code hunting_ground()}) on
 * {@link ShapeKit}'s pavilion helpers; it uses the city legend unchanged and mirrors across x but for its gate niche
 * (内境门位, CITY.md §4.15: the back flat face's middle, {@code x 11..13, y 1..4, z 24}), which is soft wall
 * ({@link ShapeKit#backNiche}). The consoles' angles are the generator's ({@code math.atan2}, taken here with
 * {@link StrictMath}): for every step one rim cell lies clearly nearest, so the pick is the same whatever order the
 * cells are tried in. On a diagonal site its blueprint's front faces along A toward the nexus and its controller stands
 * over the octagon's centre ({@link BuildPlan#pavilionController}).
 * <p>
 * Counts: checked 1,531, soft 352 (12 of them the niche), must-be-air 88 (the portals but where the terraces and the
 * vomitoria take their inner cells) = 1,971 steps.
 */
public final class HuntingGroundShape {

    public static final String KEY = "hunting_ground";
    /** The pavilion sites it may stand on, preferred first: back-right, then 5, 1, 7 (CITY.md §2.2). */
    public static final int[] SITES = { 3, 5, 1, 7 };
    /** The box: 25 across and deep, 10 high (y0..y9); the octagon's middle at {@link #MID}. */
    public static final int WIDTH = ShapeKit.PAV_SIZE, DEPTH = ShapeKit.PAV_SIZE, HEIGHT = 10, MID = ShapeKit.PAV_C;
    /** The controller on the holo emitter's console at the arena's centre, at eye height. */
    public static final int CTRL_X = MID, CTRL_Y = 2, CTRL_Z = MID;
    /** The outer wall rises to its cornice at {@code WALL_TOP}; the velarium's ring hangs over it. */
    public static final int WALL_TOP = 8, VELARIUM_Y = 9;
    /** The arena's octagon radius, and the radii of the lowest and the highest terrace. */
    public static final int ARENA_R = 5, TERRACE_FROM = 6, TERRACE_TO = 11;
    /** The vomitoria's bridges (deck at y3, one stands on them at 4) start this far out. */
    public static final int BRIDGE_FROM = 9, BRIDGE_Y = 3;
    /** The imprint consoles: twelve on the arena's rim, at this octagon radius. */
    public static final int CONSOLES = 12, CONSOLE_R = 6;
    /**
     * The characters that give way to the rendered canopy once the ground forms: the velarium's ring segments. Players
     * pass through them as well ({@link #PASSES}): they hang at y9 over the top terraces, out of everyone's reach (the
     * walk test holds it), and nothing stands on them.
     */
    public static final String DISSOLVES = "R", PASSES = "R";

    /** The shape as the generator draws it (do not change it: every array is the shape itself). */
    public static final ShapeKit.Shape SHAPE = build();
    /** The units in slot order: the twelve imprint consoles, round from the front. */
    public static final UnitLayout UNITS = new UnitLayout(SHAPE);
    /** The checked shape for StructureLib: the soft cells left out (anything goes there). */
    public static final Blueprint BLUEPRINT = ShapeKit.blueprint(SHAPE, HEIGHT);

    /** The plan's walls (its thick edge) and the portals' columns, for the stage rule. */
    private static final Set<Long> EDGE, DOORS;

    static {
        Set<Long> plan = ShapeKit.planCells();
        EDGE = Collections.unmodifiableSet(ShapeKit.thickEdge(plan));
        DOORS = Collections.unmodifiableSet(ShapeKit.portalCells(EDGE));
        if (UNITS.size() != CONSOLES || UNITS.slots() != CONSOLES)
            throw new IllegalStateException("the hunting ground's units");
        if (SHAPE.H != HEIGHT) throw new IllegalStateException("the hunting ground is no longer 10 high");
    }

    /** The Echo Hunting Ground as the generic module plan builds it ({@link BuildPlan#module} on a pavilion site). */
    public static final CityBuild BUILD = new CityBuild(
        KEY,
        SHAPE,
        HEIGHT,
        BLUEPRINT,
        UNITS,
        Parts.moduleCore(ModuleCores.HUNTING_GROUND),
        new CityBuild.Stages() {

            @Override
            public int stage(int x, int y, int z, char ch) {
                return HuntingGroundShape.stage(x, y, z, ch);
            }

            @Override
            public int fitRank(int x, int y, int z, char ch) {
                return HuntingGroundShape.fitRank(x, y, z, ch);
            }

            @Override
            public int order(int x, int y, int z, char ch) {
                return HuntingGroundShape.order(x, y, z, ch);
            }
        });

    private HuntingGroundShape() {}

    /**
     * The Echo Hunting Ground's job on a pavilion site ({@link BuildPlan#module} of {@link #BUILD}).
     *
     * @throws IllegalArgumentException if the site is not a pavilion site or the front not a horizontal unit vector
     */
    public static BuildPlan.Plan plan(CampusPlan plan, int site, int cx, int y0, int cz, int fx, int fz) {
        if (SiteFamily.of(site) != SiteFamily.PAVILION)
            throw new IllegalArgumentException("not a pavilion site: " + site);
        return BuildPlan.module(plan, site, cx, y0, cz, fx, fz, BUILD);
    }

    /** Where the Echo Hunting Ground's controller stands on a pavilion site: {x, y, z, facing ordinal}. */
    public static int[] controller(int site, int cx, int y0, int cz, int fx, int fz) {
        return BuildPlan.pavilionController(BUILD, site, cx, y0, cz, fx, fz);
    }

    /** The character of a cell: the checked legend, a soft character, {@code -} or a space (anything). */
    public static char cell(int x, int y, int z) {
        return SHAPE.get(x, y, z);
    }

    /** The blueprint cell of a point of the shape: {across, layer from the top, row from the front}. */
    public static int[] cellOf(int x, int y, int z) {
        return new int[] { x, HEIGHT - 1 - y, z };
    }

    /** Whether the column (x, z) carries the outer wall (the plan's thick edge). */
    public static boolean onEdge(int x, int z) {
        return EDGE.contains(ShapeKit.key(x, z));
    }

    /** Whether the column (x, z) is one of the four chamfer portals' (their opening and its two-deep air). */
    public static boolean inPortal(int x, int z) {
        return DOORS.contains(ShapeKit.key(x, z));
    }

    /**
     * Whether the column (x, z) lies in a vomitorium ({@code vom}): the diagonal band {@code ||du| - |dv|| <= 1} from a
     * portal in to the arena's rim (octagon radius 6 and out).
     */
    public static boolean inVomitorium(int x, int z) {
        int du = Math.abs(x - MID), dv = Math.abs(z - MID);
        return Math.abs(du - dv) <= 1 && ShapeKit.onorm(x - MID, z - MID) >= TERRACE_FROM;
    }

    /** Whether a cell is part of the gate niche (soft wall, CITY.md §4.15). */
    public static boolean inNiche(int x, int y, int z) {
        return ShapeKit.pavilionNiche(x, y, z);
    }

    /**
     * The build stage of a cell that is not part of a unit (CITY.md §4.5): FLOOR the floor of y0 with the arena's holo
     * grid (its grate tiles checked, its lit tiles soft); SHELL the foundations and the outer wall (y1..8 with its
     * cornice and anchors, the gate niche with its wall), the portals' air and lintels; DECK the terraces and the
     * vomitoria's bridges; ROOF the velarium; FIT the emitter's console and the holo pedestals; COMMISSION the
     * controller. UPPER stays empty. Within DECK the seating goes up ring by ring from the arena out, then the
     * bridges ({@link #order}).
     */
    public static int stage(int x, int y, int z, char ch) {
        if (ch == ShapeKit.CONTROLLER) return BuildPlan.M_COMMISSION;
        if (ch == ShapeKit.AIR) return BuildPlan.M_SHELL;
        if (y == 0) return ch == 'F' ? BuildPlan.M_SHELL : BuildPlan.M_FLOOR;
        if (ch == 'K' || ch == 'h') return BuildPlan.M_FIT;
        if (ch == 'R') return BuildPlan.M_ROOF;
        if (onEdge(x, z) || ch == 'n' && inPortal(x, z)) return BuildPlan.M_SHELL;
        if (y <= BRIDGE_Y && (ch == 'W' || ch == 'd' || ch == 't')) return BuildPlan.M_DECK;
        throw new IllegalStateException("no stage for '" + ch + "' at " + x + "," + y + "," + z);
    }

    /**
     * The order within FIT of a cell that is not part of a unit: before the units the emitter's console, then its holo
     * pedestals (the imprint consoles follow as their units).
     */
    public static int fitRank(int x, int y, int z, char ch) {
        return ch == 'K' ? -2 : -1;
    }

    /**
     * The order within its stage of a cell that is not part of a unit (CITY.md §4.5: "DECK, terraces in rings from the
     * arena out, vomitorium bridges"): a terrace's cells by their ring, its octagon radius from {@link #TERRACE_FROM}
     * at the arena's rim to {@link #TERRACE_TO} by the wall, so the seating goes up a whole ring at a time (each ring
     * bottom-up) from the arena out; the vomitoria's bridges after every ring ({@link #BRIDGE_ORDER}). Every other
     * stage keeps the plain bottom-up order (0).
     */
    public static int order(int x, int y, int z, char ch) {
        if (stage(x, y, z, ch) != BuildPlan.M_DECK) return 0;
        if (bridge(x, y, z)) return BRIDGE_ORDER;
        return ShapeKit.onorm(x - MID, z - MID);
    }

    /** The order of the vomitoria's bridges within DECK: after the outermost terrace. */
    public static final int BRIDGE_ORDER = TERRACE_TO + 1;

    /** Whether a cell is one of the vomitoria's bridges: the deck at {@link #BRIDGE_Y} over a vomitorium. */
    public static boolean bridge(int x, int y, int z) {
        return y == BRIDGE_Y && inVomitorium(x, z) && ShapeKit.onorm(x - MID, z - MID) >= BRIDGE_FROM;
    }

    /** The standing states {x, z, h} the generator's self-check walks from: outside the four portals. */
    public static List<int[]> walkStarts() {
        return ShapeKit.pavilionStarts();
    }

    /** The counts of the shape by character, for the tests. */
    static Map<Character, Integer> counts() {
        return SHAPE.counts();
    }

    /**
     * The rim cell {x, z} nearest the angle {@code 30 * step} degrees round from the front ({@code -z}) toward
     * {@code +x}, among the cells at octagon radius 6 off the vomitoria: where the generator puts its imprint console.
     */
    static int[] consoleCell(Set<Long> plan, int step) {
        double th = StrictMath.toRadians(30 * step);
        double want = StrictMath.atan2(StrictMath.sin(th), StrictMath.cos(th));
        double best = Double.NaN;
        int[] out = null;
        for (long k : plan) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k);
            if (ShapeKit.onorm(x - MID, z - MID) != CONSOLE_R || inVomitorium(x, z)) continue;
            double d = Math.abs(StrictMath.atan2(x - MID, -(z - MID)) - want);
            d = Math.min(d, 2 * Math.PI - d);
            if (out == null || d < best) {
                best = d;
                out = new int[] { x, z };
            }
        }
        return out;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // the generator (a port of b_pav.py's hunting_ground)

    private static ShapeKit.Shape build() {
        final int N = ShapeKit.PAV_SIZE, C = ShapeKit.PAV_C;
        ShapeKit.Shape s = new ShapeKit.Shape(KEY, N, HEIGHT, N);
        ShapeKit.Shell sh = ShapeKit.shell(s, WALL_TOP, 3, new int[] { 6, 7 });
        for (long k : sh.edge) s.set(ShapeKit.keyX(k), WALL_TOP, ShapeKit.keyZ(k), 'n');
        ShapeKit.centreController(s, CTRL_Y);
        // the arena's holo grid, and the terraces: one ring a step, half a block higher each, from radius 6 (one stands
        // at 1.5) to 11 (at 4); the vomitoria cut through them at ground level
        for (long k : sh.plan) {
            if (sh.edge.contains(k)) continue;
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k), du = x - C, dv = z - C;
            int r = ShapeKit.onorm(du, dv);
            if (r <= ARENA_R) {
                s.set(x, 0, z, (du + dv) % 2 == 0 ? ':' : '+');
                continue;
            }
            // the terrace's top in half blocks, 1 + k / 2 for terrace k = r - 5: whole blocks below it, a tread on top
            int top2 = 2 + Math.min(r - ARENA_R, 6), full = top2 / 2;
            for (int y = 1; y < full; y++) s.set(x, y, z, y < full - 1 ? 'W' : 'd');
            if (top2 % 2 != 0) s.set(x, full, z, 't');
            if (inVomitorium(x, z)) for (int y = 1; y < 3; y++) s.clear(x, y, z);
        }
        // the bridges over the vomitoria (one stands on them at 4) from radius 9 out
        for (long k : sh.plan) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k);
            if (sh.edge.contains(k) || !inVomitorium(x, z)) continue;
            if (ShapeKit.onorm(x - C, z - C) >= BRIDGE_FROM) {
                s.set(x, BRIDGE_Y, z, 'd');
                s.clear(x, BRIDGE_Y + 1, z);
            }
        }
        List<Object[]> units = new ArrayList<>();
        // twelve imprint consoles on the arena's rim at 30 degree steps: a plinth at y1, a socket at y2, head room over
        for (int i = 0; i < CONSOLES; i++) {
            int[] p = consoleCell(sh.plan, i);
            int x = p[0], z = p[1];
            s.set(x, 1, z, 'p');
            s.set(x, 2, z, 'o');
            s.clear(x, 3, z);
            List<int[]> cells = new ArrayList<>();
            cells.add(new int[] { x, 1, z });
            cells.add(new int[] { x, 2, z });
            units.add(new Object[] { "imprint-" + (i + 1), "imprint", cells, 1, null });
        }
        // four holo pedestals round the emitter
        int[][] pedestals = { { 2, 0 }, { -2, 0 }, { 0, 2 }, { 0, -2 } };
        for (int[] p : pedestals) s.set(C + p[0], 1, C + p[1], 'h');
        // the velarium: frame ring segments at y9 over the top terraces, two cells in from the wall
        for (long k : sh.plan) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k);
            int r = ShapeKit.onorm(x - C, z - C);
            if ((r == 10 || r == 11) && !sh.edge.contains(k)) s.set(x, VELARIUM_Y, z, 'R');
        }
        // cladding anchors: the four flat faces' middles at y7
        for (long k : sh.edge) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k), du = x - C, dv = z - C;
            char ch = s.get(x, 7, z);
            if ((du == 0 || dv == 0) && (ch == 'W' || ch == 'L' || ch == 'G')) s.set(x, 7, z, 'A');
        }
        for (Object[] u : units) {
            @SuppressWarnings("unchecked")
            List<int[]> cells = (List<int[]>) u[2];
            s.unit((String) u[0], (String) u[1], cells, (Integer) u[3], (int[]) u[4]);
        }
        ShapeKit.backNiche(s);
        return s;
    }
}
