package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 智子阵列 The Sophon Array (0.15.0, CITY.md §4.10): the first building of the OUTER_DIAG family, a sphere on a plinth
 * after Boullée's Cenotaph for Newton holding the light point of the sophon. The podium is the octagon
 * {@code oct(15, 20)} (walls y1..5 with lit pilasters every 3 and on the 0/45 degree lines, glazing y3..4, four chamfer
 * portals), its cornice and roof at y6 with a glazed oculus {@code oct(4, 5)} in a lit rim; four struts (frame pillars
 * y7..10 at {@code (+-5, +-5)}) carry the lattice sphere (radius 12 round y20, y8..32: meridians every 30 degrees and
 * parallels every four layers of frame pillars). Struts and lattice give way to the rendered geodesic frame round the
 * light point once the array forms ({@link #DISSOLVES}).
 * <p>
 * Inside, the data hall: 48 memory racks (plinth y1, three core sockets y2..4, crown y5, floor to ceiling) in two rows
 * on each of the four sides (4 racks at radius 7, 8 at radius 10), facing the centre, the four diagonal portal paths
 * left clear (the outermost racks of the radius 10 rows stand in the inner cells of the portals, which the generator
 * draws over their air, so each portal is eight columns, not the pavilions' ten, and still walked through); the cold
 * aisles between the rows marked in soft trim tiles, lit lines on the two axes; the controller on
 * its console at the centre under the oculus, where one looks up through the glass at the light point. 48 units, 144
 * slots; their order is the slot order and never changes ({@link #UNITS}).
 * <p>
 * The geometry is an exact port of the generator ({@code city/gen/b_outer.py}, {@code sophon_array()}) on
 * {@link ShapeKit}'s outer helpers ({@link ShapeKit#octoShell}); it uses the city legend unchanged and mirrors across x
 * but for its gate niche (内境门位, CITY.md §4.15: the back flat face's middle, {@code x 14..16, y 1..4, z 30}), which is
 * soft wall. The generator calls its racks' kind {@code rack}; the Resonance Gate's tuning racks (0.14.0) took that
 * kind's name, so the array's are {@code memory_rack} (M12 §5): a different thing, its own name in the window. On an
 * outer diagonal its blueprint's front faces along A toward the nexus and its controller stands over the octagon's
 * centre ({@link BuildPlan#outerDiagController}).
 * <p>
 * Counts: checked 2,397 (774 of them the struts' 16 and the lattice's 758 frame pillars), soft 633 (12 of them the
 * niche), must-be-air 128 (four portals) = 3,158 steps.
 */
public final class SophonArrayShape {

    public static final String KEY = "sophon_array";
    /**
     * The outer diagonals it may stand on, preferred first: O1 in front on the nexus's right (CITY.md §4.10), then the
     * other front one, O7, then those behind, O3 and O5 (the Mana Fountain Array's order on the diagonals inside them).
     */
    public static final int[] SITES = { 11, 17, 13, 15 };
    /** The box: 31 across and deep, 33 high (y0..y32); the octagon's middle at {@link #MID}. */
    public static final int WIDTH = ShapeKit.OUT_SIZE, DEPTH = ShapeKit.OUT_SIZE, HEIGHT = 33, MID = ShapeKit.OUT_C;
    /** The controller on the central console under the oculus, at eye height. */
    public static final int CTRL_X = MID, CTRL_Y = 2, CTRL_Z = MID;
    /** The podium's walls rise to {@code WALL_TOP}; its cornice, roof and oculus are the layer {@code ROOF_Y}. */
    public static final int WALL_TOP = 5, ROOF_Y = 6;
    /** The struts: frame pillars from {@code STRUT_FROM} to {@code STRUT_TO}, {@code STRUT_OFF} out on both axes. */
    public static final int STRUT_FROM = 7, STRUT_TO = 10, STRUT_OFF = 5;
    /** The lattice sphere: radius {@code SPHERE_R} round layer {@code SPHERE_Y}, drawn from layer 8 to 32. */
    public static final int SPHERE_Y = 20, SPHERE_R = 12, LATTICE_FROM = 8, LATTICE_TO = 32;
    /** The racks: 48 units of three slots each. */
    public static final int RACKS = 48, RACK_SLOTS = 3;
    /** The gate niche (内境门位, CITY.md §4.15): the back flat face's middle. */
    public static final int NICHE_X0 = MID - 1, NICHE_X1 = MID + 1, NICHE_Y0 = 1, NICHE_Y1 = 4, NICHE_Z = DEPTH - 1;
    /**
     * The characters that give way to the rendered geodesic frame once the array forms: the struts and the lattice
     * sphere's frame pillars. Players pass through them as well ({@link #PASSES}): they stand on the podium's roof and
     * above it, where no one walks (the podium is one storey without a stair to its roof; the walk test holds it).
     */
    public static final String DISSOLVES = "P", PASSES = "P";
    /** The unit kind of the racks (lang {@code unit.memory_rack}). */
    public static final String RACK_KIND = "memory_rack";

    /** The shape as the generator draws it (do not change it: every array is the shape itself). */
    public static final ShapeKit.Shape SHAPE = build();
    /** The units in slot order: the 48 racks. */
    public static final UnitLayout UNITS = new UnitLayout(SHAPE);
    /** The checked shape for StructureLib: the soft cells left out (anything goes there). */
    public static final Blueprint BLUEPRINT = ShapeKit.blueprint(SHAPE, HEIGHT);

    /** The plan's walls (its thick edge) and the portals' columns, for the stage rule. */
    private static final Set<Long> EDGE, DOORS;

    static {
        Set<Long> plan = new java.util.LinkedHashSet<>();
        for (int x = 0; x < WIDTH; x++) for (int z = 0; z < DEPTH; z++) {
            if (ShapeKit.octo(x - MID, z - MID, ShapeKit.OUT_HALF, ShapeKit.OUT_K)) plan.add(ShapeKit.key(x, z));
        }
        EDGE = Collections.unmodifiableSet(ShapeKit.thickBoundary(plan));
        Set<Long> doors = new java.util.LinkedHashSet<>();
        for (int x = 0; x < WIDTH; x++) for (int z = 0; z < DEPTH; z++) {
            for (int y = 1; y <= 4; y++) if (SHAPE.get(x, y, z) == ShapeKit.AIR) doors.add(ShapeKit.key(x, z));
        }
        DOORS = Collections.unmodifiableSet(doors);
        if (UNITS.size() != RACKS || UNITS.slots() != RACKS * RACK_SLOTS)
            throw new IllegalStateException("the sophon array's units");
        if (SHAPE.H != HEIGHT) throw new IllegalStateException("the sophon array is no longer 33 high");
    }

    /** The Sophon Array as the generic module plan builds it ({@link BuildPlan#module} on an outer diagonal). */
    public static final CityBuild BUILD = new CityBuild(
        KEY,
        SHAPE,
        HEIGHT,
        BLUEPRINT,
        UNITS,
        Parts.moduleCore(ModuleCores.SOPHON_ARRAY),
        new CityBuild.Stages() {

            @Override
            public int stage(int x, int y, int z, char ch) {
                return SophonArrayShape.stage(x, y, z, ch);
            }

            @Override
            public int fitRank(int x, int y, int z, char ch) {
                return SophonArrayShape.fitRank(x, y, z, ch);
            }

            @Override
            public int[] stageOrder() {
                return STAGE_ORDER.clone();
            }
        });

    /**
     * The order its stages are built in (CITY.md §4.10, M12 §5): CLEAR, FLOOR, SHELL, DECK (empty), ROOF, FIT (the
     * racks and the console), UPPER (the struts and the lattice sphere, bottom-up), DECOR, COMMISSION; a FIT before
     * UPPER, so the racks go into the hall before the sphere rises over its roof.
     */
    public static final int[] STAGE_ORDER = { BuildPlan.M_CLEAR, BuildPlan.M_FLOOR, BuildPlan.M_SHELL, BuildPlan.M_DECK,
        BuildPlan.M_ROOF, BuildPlan.M_FIT, BuildPlan.M_UPPER, BuildPlan.M_DECOR, BuildPlan.M_COMMISSION };

    private SophonArrayShape() {}

    /**
     * The Sophon Array's job on an outer diagonal ({@link BuildPlan#module} of {@link #BUILD}).
     *
     * @throws IllegalArgumentException if the site is not an outer diagonal or the front not a horizontal unit vector
     */
    public static BuildPlan.Plan plan(CampusPlan plan, int site, int cx, int y0, int cz, int fx, int fz) {
        if (SiteFamily.of(site) != SiteFamily.OUTER_DIAG)
            throw new IllegalArgumentException("not an outer diagonal: " + site);
        return BuildPlan.module(plan, site, cx, y0, cz, fx, fz, BUILD);
    }

    /** Where the Sophon Array's controller stands on an outer diagonal: {x, y, z, facing ordinal}. */
    public static int[] controller(int site, int cx, int y0, int cz, int fx, int fz) {
        return BuildPlan.outerDiagController(BUILD, site, cx, y0, cz, fx, fz);
    }

    /** The character of a cell: the checked legend, a soft character, {@code -} or a space (anything). */
    public static char cell(int x, int y, int z) {
        return SHAPE.get(x, y, z);
    }

    /** The blueprint cell of a point of the shape: {across, layer from the top, row from the front}. */
    public static int[] cellOf(int x, int y, int z) {
        return new int[] { x, HEIGHT - 1 - y, z };
    }

    /** Whether the column (x, z) carries the podium's walls (the plan's thick edge). */
    public static boolean onEdge(int x, int z) {
        return EDGE.contains(ShapeKit.key(x, z));
    }

    /** Whether the column (x, z) is one of the four chamfer portals' (their opening and its two-deep air). */
    public static boolean inPortal(int x, int z) {
        return DOORS.contains(ShapeKit.key(x, z));
    }

    /** Whether a cell is part of the gate niche (soft wall, CITY.md §4.15). */
    public static boolean inNiche(int x, int y, int z) {
        return z == NICHE_Z && x >= NICHE_X0 && x <= NICHE_X1 && y >= NICHE_Y0 && y <= NICHE_Y1;
    }

    /** Whether a cell is one of the four struts' (the lattice's frame pillars are the other {@code P} cells). */
    public static boolean strut(int x, int y, int z) {
        return y >= STRUT_FROM && y <= STRUT_TO
            && Math.abs(x - MID) == STRUT_OFF
            && Math.abs(z - MID) == STRUT_OFF
            && SHAPE.get(x, y, z) == 'P';
    }

    /**
     * The build stage of a cell that is not part of a unit (CITY.md §4.10): FLOOR the soft floor of y0 with its trim
     * aisles and lit axes; SHELL the foundations, the podium's walls y1..5 with their anchors, the portals' air and
     * lintels (the gate niche with its wall); ROOF the podium's cornice, roof and oculus at y6; FIT the central console
     * after the racks; UPPER the struts and the lattice sphere, bottom-up; COMMISSION the controller.
     * <p>
     * CITY.md lists UPPER after FIT ("a FIT before UPPER is intended", M12 §5): the racks go into the hall before the
     * sphere rises over its roof, so the array builds its stages in its own order ({@link #STAGE_ORDER}); DECK stays
     * empty. (Before the fixes of 0.15.0 the struts and the lattice were the last of FIT, the same cells in the same
     * order under FIT's name.)
     */
    public static int stage(int x, int y, int z, char ch) {
        if (ch == ShapeKit.CONTROLLER) return BuildPlan.M_COMMISSION;
        if (ch == ShapeKit.AIR) return BuildPlan.M_SHELL;
        if (y == 0) return ShapeKit.soft(ch) ? BuildPlan.M_FLOOR : BuildPlan.M_SHELL;
        if (ch == 'K') return BuildPlan.M_FIT;
        if (ch == 'P') return BuildPlan.M_UPPER;
        if (y <= WALL_TOP && (onEdge(x, z) || inPortal(x, z))) return BuildPlan.M_SHELL;
        if (y == ROOF_Y) return BuildPlan.M_ROOF;
        throw new IllegalStateException("no stage for '" + ch + "' at " + x + "," + y + "," + z);
    }

    /** The order within FIT of a cell that is not part of a unit: the central console, after the racks. */
    public static int fitRank(int x, int y, int z, char ch) {
        return 1;
    }

    /** The standing states {x, z, h} the generator's self-check walks from: outside the four portals. */
    public static List<int[]> walkStarts() {
        return ShapeKit.outerStarts();
    }

    /** The counts of the shape by character, for the tests. */
    static Map<Character, Integer> counts() {
        return SHAPE.counts();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // the generator (a port of b_outer.py's sophon_array)

    private static ShapeKit.Shape build() {
        final int N = ShapeKit.OUT_SIZE, C = ShapeKit.OUT_C;
        ShapeKit.Shape s = new ShapeKit.Shape(KEY, N, HEIGHT, N);
        ShapeKit.Shell sh = ShapeKit
            .octoShell(s, N, C, ShapeKit.OUT_HALF, ShapeKit.OUT_K, WALL_TOP, 3, new int[] { 3, 4 }, true, 'w');
        // the cornice on the walls, the roof, and the glazed oculus oct(4, 5) in its lit rim
        for (long k : sh.plan) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k), du = x - C, dv = z - C;
            if (sh.edge.contains(k)) s.set(x, ROOF_Y, z, 'n');
            else if (ShapeKit.octo(du, dv, 4, 5)) s.set(x, ROOF_Y, z, ShapeKit.octo(du, dv, 3, 4) ? 'G' : 'L');
            else s.set(x, ROOF_Y, z, 'W');
        }
        s.set(C, 1, C, 'K');
        s.set(C, CTRL_Y, C, ShapeKit.CONTROLLER);
        s.controller = new int[] { C, CTRL_Y, C };
        List<Object[]> units = new ArrayList<>();
        // the racks: two rows on each side, the front and back rows facing the centre across z, the side rows across x
        for (int sgn : new int[] { -1, 1 }) {
            int[][] rows = { { 7, 2, 4 }, { 10, 2, 4, 6, 8 } };
            for (int[] row : rows) {
                int dv = row[0];
                for (int i = 1; i < row.length; i++) {
                    int du = row[i];
                    for (int sx : new int[] { -1, 1 }) {
                        rack(s, units, C + sx * du, C + sgn * dv, new int[] { 0, -sgn });
                        rack(s, units, C + sgn * dv, C + sx * du, new int[] { -sgn, 0 });
                    }
                }
            }
        }
        // the cold-aisle floor: soft trim lines between the rows, lit lines on the main axes
        for (long k : sh.plan) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k), du = x - C, dv = z - C;
            if (s.get(x, 0, z) != '.') continue;
            int adu = Math.abs(du), adv = Math.abs(dv);
            if (du == 0 || dv == 0) s.set(x, 0, z, '+');
            else if (adv == 8 || adv == 9 || adu == 8 || adu == 9) s.set(x, 0, z, '_');
        }
        // four struts from the roof to the sphere
        int[][] struts = { { STRUT_OFF, STRUT_OFF }, { -STRUT_OFF, STRUT_OFF }, { STRUT_OFF, -STRUT_OFF },
            { -STRUT_OFF, -STRUT_OFF } };
        for (int[] st : struts) for (int y = STRUT_FROM; y <= STRUT_TO; y++) s.set(C + st[0], y, C + st[1], 'P');
        // the lattice sphere: the shell of radius 12 round (C, 20, C), where a meridian (every 30 degrees, within 0.6
        // of it along the parallel's circle) or a parallel (every fourth layer from the centre) runs
        for (int y = LATTICE_FROM; y <= LATTICE_TO; y++) for (int x = 0; x < N; x++) for (int z = 0; z < N; z++) {
            int dx = x - C, dy = y - SPHERE_Y, dz = z - C;
            double d = Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
            if (!(SPHERE_R - 0.5 <= d && d < SPHERE_R + 0.5)) continue;
            // the generator's latitude (asin) is never used: only the longitude and the layer decide
            double lon = ShapeKit.pyMod(ShapeKit.degrees(StrictMath.atan2(dx, dz)), 30);
            double rr = ShapeKit.hypot(dx, dz);
            boolean meridian = Math.min(lon, 30 - lon) * rr * Math.PI / 180 < 0.6;
            boolean parallel = dy % 4 == 0;
            if (meridian || parallel) s.set(x, y, z, 'P');
        }
        // cladding anchors: the four flat faces' middles at y5
        for (long k : sh.edge) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k), du = x - C, dv = z - C;
            char ch = s.get(x, WALL_TOP, z);
            if ((du == 0 || dv == 0) && (ch == 'W' || ch == 'L')) s.set(x, WALL_TOP, z, 'A');
        }
        // the gate niche (内境门位, CITY.md §4.15) in the back flat face's middle, x 14..16, y1..4, z 30: the same
        // blocks (the dark course, the panels, the glass and the lit pilaster), soft, so the 内境 gate can open it later
        s.gateNiche(NICHE_X0, NICHE_X1, NICHE_Y0, NICHE_Y1, NICHE_Z);
        for (Object[] u : units) {
            @SuppressWarnings("unchecked")
            List<int[]> cells = (List<int[]>) u[1];
            s.unit((String) u[0], RACK_KIND, cells, RACK_SLOTS, (int[]) u[2]);
        }
        return s;
    }

    /** A rack at (x, z): plinth y1, three core sockets y2..4, crown y5, its face toward the centre. */
    private static void rack(ShapeKit.Shape s, List<Object[]> units, int x, int z, int[] face) {
        char[] parts = { 'p', 'o', 'o', 'o', 'c' };
        List<int[]> cells = new ArrayList<>();
        for (int y = 1; y <= 5; y++) {
            s.set(x, y, z, parts[y - 1]);
            cells.add(new int[] { x, y, z });
        }
        units.add(new Object[] { "rack-" + (units.size() + 1), cells, face });
    }
}
