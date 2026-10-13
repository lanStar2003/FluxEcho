package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 全息沙盘 The Holo Sand Table (0.15.0, CITY.md §4.11): the second building of the OUTER_DIAG family, a rotunda round a
 * round table: *Foundation*'s Prime Radiant and the war room's table under its ring light, with the Reichstag dome's
 * double ramp. The drum is the octagon {@code oct(15, 20)} (walls y1..9 with a glazed band y3..7 between lit pilasters
 * every 3 and on the 0/45 degree lines, four chamfer portals, the cornice at y10) under a hollow stepped shallow dome
 * (rings at y10..13, each layer only the ring it adds) and a glazed lantern (y14..15).
 * <p>
 * Inside, the round table (radius 4.5 at y1: a plinth rim round a glass top, one unit of nine slots), the ring light
 * hanging over it (frame ring segments at radius 5.5..6.5, y9, which give way to the rendered light ring once the
 * table forms, {@link #DISSOLVES}), the controller on its console at the table's front (6 toward the blueprint's
 * front), nine layer consoles round the table (units without slots: each opens its layer), and the double ramp: two
 * mirrored ramps up the drum's sides (octagon radius 13..14, from 30 degrees off the front to 155) to a back gallery
 * (deck y6, railed) looking down on the hologram. 10 units, 9 slots; their order is the slot order and never changes
 * ({@link #UNITS}).
 * <p>
 * The geometry is an exact port of the generator ({@code city/gen/b_outer.py}, {@code holo_table()}) on
 * {@link ShapeKit}'s outer helpers ({@link ShapeKit#octoShell}); it uses the city legend unchanged, mirrors across x
 * and has no gate niche (CITY.md §4.15 lists none for it). Python's {@code round} rounds a half to even, as
 * {@link Math#rint} does. The generator lists the table's cells in its set's order; nothing depends on that order (the
 * table has no sockets, so no cell shows a slot), and the port lists them in its plan's order, x by x.
 * <p>
 * Counts: checked 2,148, soft 621 (the floor), must-be-air 150 (four portals) = 2,919 steps.
 */
public final class HoloTableShape {

    public static final String KEY = "holo_table";
    /**
     * The outer diagonals it may stand on, preferred first: O7 in front on the nexus's left (CITY.md §4.11), then the
     * other front one, O1, then those behind, O5 and O3 (the Arcane Court's order on the diagonals inside them).
     */
    public static final int[] SITES = { 17, 11, 15, 13 };
    /** The box: 31 across and deep, 16 high (y0..y15); the octagon's middle at {@link #MID}. */
    public static final int WIDTH = ShapeKit.OUT_SIZE, DEPTH = ShapeKit.OUT_SIZE, HEIGHT = 16, MID = ShapeKit.OUT_C;
    /** The controller on its console at the table's front, at eye height. */
    public static final int CTRL_X = MID, CTRL_Y = 2, CTRL_Z = MID - 6;
    /** The drum's walls rise to {@code WALL_TOP}, its cornice at {@code CORNICE_Y}; the dome from there up. */
    public static final int WALL_TOP = 9, CORNICE_Y = 10, LANTERN_Y = 14;
    /** The round table at y1: radius {@code TABLE_R}, its plinth rim beyond {@code TABLE_GLASS}. */
    public static final double TABLE_R = 4.5, TABLE_GLASS = 3.5;
    /** The ring light: frame rings at {@code RING_IN <= d < RING_OUT} from the middle, hanging at {@code RING_Y}. */
    public static final double RING_IN = 5.5, RING_OUT = 6.5;
    public static final int RING_Y = 9;
    /** The ramps' band (octagon radius) and the rails' ring just inside it, the gallery's deck and stand. */
    public static final int RAMP_FROM = 13, RAMP_TO = 14, RAIL_RING = 12, GALLERY_Y = 6, GALLERY_STAND = 7;
    /** The ramps climb from {@code RAMP_START} degrees off the front; the gallery is the back from {@code GALLERY}. */
    public static final double RAMP_START = 30, GALLERY = 155;
    /** The table's slots, and the layer consoles (units without slots). */
    public static final int TABLE_SLOTS = 9, CONSOLES = 9;
    /**
     * The characters that give way to the rendered light ring once the table forms: the ring light's segments.
     * Players pass through them as well ({@link #PASSES}): they hang at y9 over the table, six blocks in from the
     * ramps and the gallery, out of everyone's reach (the walk test holds it).
     */
    public static final String DISSOLVES = "R", PASSES = "R";
    /** The unit kinds (lang {@code unit.table}, {@code unit.console}), the generator's. */
    public static final String TABLE_KIND = "table", CONSOLE_KIND = "console";

    /** The shape as the generator draws it (do not change it: every array is the shape itself). */
    public static final ShapeKit.Shape SHAPE = build();
    /** The units in slot order: the table, then the nine layer consoles. */
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
        if (UNITS.size() != 1 + CONSOLES || UNITS.slots() != TABLE_SLOTS)
            throw new IllegalStateException("the holo sand table's units");
        if (SHAPE.H != HEIGHT) throw new IllegalStateException("the holo sand table is no longer 16 high");
    }

    /** The Holo Sand Table as the generic module plan builds it ({@link BuildPlan#module} on an outer diagonal). */
    public static final CityBuild BUILD = new CityBuild(
        KEY,
        SHAPE,
        HEIGHT,
        BLUEPRINT,
        UNITS,
        Parts.moduleCore(ModuleCores.HOLO_TABLE),
        new CityBuild.Stages() {

            @Override
            public int stage(int x, int y, int z, char ch) {
                return HoloTableShape.stage(x, y, z, ch);
            }

            @Override
            public int fitRank(int x, int y, int z, char ch) {
                return HoloTableShape.fitRank(x, y, z, ch);
            }
        });

    private HoloTableShape() {}

    /**
     * The Holo Sand Table's job on an outer diagonal ({@link BuildPlan#module} of {@link #BUILD}).
     *
     * @throws IllegalArgumentException if the site is not an outer diagonal or the front not a horizontal unit vector
     */
    public static BuildPlan.Plan plan(CampusPlan plan, int site, int cx, int y0, int cz, int fx, int fz) {
        if (SiteFamily.of(site) != SiteFamily.OUTER_DIAG)
            throw new IllegalArgumentException("not an outer diagonal: " + site);
        return BuildPlan.module(plan, site, cx, y0, cz, fx, fz, BUILD);
    }

    /** Where the Holo Sand Table's controller stands on an outer diagonal: {x, y, z, facing ordinal}. */
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

    /** Whether the column (x, z) carries the drum's walls (the plan's thick edge). */
    public static boolean onEdge(int x, int z) {
        return EDGE.contains(ShapeKit.key(x, z));
    }

    /** Whether the column (x, z) is one of the four chamfer portals' (their opening and its two-deep air). */
    public static boolean inPortal(int x, int z) {
        return DOORS.contains(ShapeKit.key(x, z));
    }

    /**
     * The angle of a column off the blueprint's front seen from the middle, in degrees: 0 straight to the front, 180
     * straight to the back, the same on both sides ({@code math.degrees(math.atan2(abs(du), -dv))}).
     */
    public static double offFront(int x, int z) {
        return ShapeKit.degrees(StrictMath.atan2(Math.abs(x - MID), -(z - MID)));
    }

    /**
     * The window's plan levels for the building alone (0.13.0's {@code ModuleMap} overview): the back gallery, where
     * one stands on its deck at y6 looking down on the table.
     */
    public static int[] planLevels() {
        return new int[] { GALLERY_STAND };
    }

    /**
     * The build stage of a cell that is not part of a unit (CITY.md §4.11): FLOOR the soft floor of y0; SHELL the
     * foundations, the drum's walls y1..9 with their anchors and its cornice at y10, the portals' air and lintels; DECK
     * the two ramps (their panels and treads), the back gallery's deck and the rails; ROOF the dome's stepped rings
     * from y10 and the lantern; FIT after the table and the layer consoles the controller's console, then the ring
     * light; COMMISSION the controller. The ramps start in the front portals' inner columns (the generator lays their
     * first panels and treads over those columns' air at y1), and those ramp cells are DECK as the rest of the ramps.
     */
    public static int stage(int x, int y, int z, char ch) {
        if (ch == ShapeKit.CONTROLLER) return BuildPlan.M_COMMISSION;
        if (ch == ShapeKit.AIR) return BuildPlan.M_SHELL;
        if (y == 0) return ShapeKit.soft(ch) ? BuildPlan.M_FLOOR : BuildPlan.M_SHELL;
        if (ch == 'K' || ch == 'R') return BuildPlan.M_FIT;
        if (onEdge(x, z) && y <= CORNICE_Y) return BuildPlan.M_SHELL;
        if (onEdge(x, z)) throw new IllegalStateException("a wall over the cornice at " + x + "," + y + "," + z);
        if (y >= CORNICE_Y) return BuildPlan.M_ROOF;
        if (ch == 'W' || ch == 't' || ch == 'd' || ch == 'r') return BuildPlan.M_DECK;
        // the portals' lintels
        if (inPortal(x, z)) return BuildPlan.M_SHELL;
        throw new IllegalStateException("no stage for '" + ch + "' at " + x + "," + y + "," + z);
    }

    /** The order within FIT of a cell that is not part of a unit: the controller's console, then the ring light. */
    public static int fitRank(int x, int y, int z, char ch) {
        return ch == 'K' ? 1 : 2;
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
    // the generator (a port of b_outer.py's holo_table)

    private static ShapeKit.Shape build() {
        final int N = ShapeKit.OUT_SIZE, C = ShapeKit.OUT_C;
        ShapeKit.Shape s = new ShapeKit.Shape(KEY, N, HEIGHT, N);
        ShapeKit.Shell sh = ShapeKit
            .octoShell(s, N, C, ShapeKit.OUT_HALF, ShapeKit.OUT_K, WALL_TOP, 3, new int[] { 3, 4, 5, 6, 7 }, true, 'w');
        for (long k : sh.edge) s.set(ShapeKit.keyX(k), CORNICE_Y, ShapeKit.keyZ(k), 'n');
        // the hollow stepped dome: each step only the ring it adds over the next one, glazed from the lantern up
        int[][] steps = { { 14, 18, 10 }, { 12, 16, 11 }, { 10, 13, 12 }, { 7, 9, 13 }, { 4, 5, 14 }, { 2, 3, 15 } };
        for (int i = 0; i < steps.length; i++) {
            int h = steps[i][0], k = steps[i][1], y = steps[i][2];
            int[] next = i + 1 < steps.length ? steps[i + 1] : null;
            for (long p : sh.plan) {
                int x = ShapeKit.keyX(p), z = ShapeKit.keyZ(p), du = x - C, dv = z - C;
                if (!ShapeKit.octo(du, dv, h, k) || sh.edge.contains(p)) continue;
                // hollow: only the ring this step adds
                if (next != null && ShapeKit.octo(du, dv, next[0], next[1])) continue;
                boolean rib = du == 0 || dv == 0 || Math.abs(du) == Math.abs(dv);
                s.set(x, y, z, y >= LANTERN_Y && !rib ? 'G' : rib ? 'L' : 'W');
            }
        }
        List<Object[]> units = new ArrayList<>();
        // the round table: a plinth rim round a glass top, one unit
        List<int[]> table = new ArrayList<>();
        for (long p : sh.plan) {
            int x = ShapeKit.keyX(p), z = ShapeKit.keyZ(p);
            double d = ShapeKit.hypot(x - C, z - C);
            if (d <= TABLE_R) {
                s.set(x, 1, z, d > TABLE_GLASS ? 'p' : 'G');
                table.add(new int[] { x, 1, z });
            }
        }
        units.add(new Object[] { "table", TABLE_KIND, table, TABLE_SLOTS });
        s.set(CTRL_X, 1, CTRL_Z, 'K');
        s.set(CTRL_X, CTRL_Y, CTRL_Z, ShapeKit.CONTROLLER);
        s.controller = new int[] { CTRL_X, CTRL_Y, CTRL_Z };
        // the nine layer consoles round the table (the last pair either side of the controller)
        int[][] round = { { 7, 0 }, { -7, 0 }, { 0, 7 }, { 5, 5 }, { -5, 5 }, { 5, -5 }, { -5, -5 }, { 3, -7 } };
        for (int[] at : round) {
            int[][] pts = at[0] == 3 && at[1] == -7 ? new int[][] { { 3, -7 }, { -3, -7 } } : new int[][] { at };
            for (int[] p : pts) {
                int x = C + p[0], z = C + p[1];
                s.set(x, 1, z, 'K');
                List<int[]> cells = new ArrayList<>();
                cells.add(new int[] { x, 1, z });
                units.add(new Object[] { "layer-" + units.size(), CONSOLE_KIND, cells, 0 });
            }
        }
        // the ring light hanging over the table
        for (long p : sh.plan) {
            int x = ShapeKit.keyX(p), z = ShapeKit.keyZ(p);
            double d = ShapeKit.hypot(x - C, z - C);
            if (RING_IN <= d && d < RING_OUT) s.set(x, RING_Y, z, 'R');
        }
        // the double ramp: the band of octagon radius 13..14 on each side, from the front (stand 1) to the back
        // gallery (stand 7); the gallery is the back band from 155 degrees at deck y6
        Map<Long, Double> ramp = new HashMap<>();
        for (long p : sh.plan) {
            if (sh.edge.contains(p)) continue;
            int x = ShapeKit.keyX(p), z = ShapeKit.keyZ(p);
            int n = ShapeKit.onorm(x - C, z - C);
            if (n != RAMP_FROM && n != RAMP_TO) continue;
            double ang = offFront(x, z);
            if (ang >= GALLERY) {
                s.set(x, GALLERY_Y, z, 'd');
                continue;
            }
            if (ang < RAMP_START) continue;
            double f = (ang - RAMP_START) / 125.0;
            // stand 1 .. 7 in half blocks; Python's round takes a half to the even side
            double h = 1 + Math.rint(12 * f) / 2.0;
            ramp.put(p, h);
            if (h <= 1) continue;
            if (h == Math.floor(h)) s.set(x, (int) h - 1, z, 'W');
            else s.set(x, (int) h, z, 't');
        }
        // the rails along the ramps' inner side once they are three up, and round the gallery
        int[][] sides = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (long p : sh.plan) {
            int x = ShapeKit.keyX(p), z = ShapeKit.keyZ(p);
            if (sh.edge.contains(p) || ShapeKit.onorm(x - C, z - C) != RAIL_RING) continue;
            double top = Double.NaN;
            for (int[] d : sides) {
                Double h = ramp.get(ShapeKit.key(x + d[0], z + d[1]));
                if (h != null && (Double.isNaN(top) || h > top)) top = h;
            }
            double ang = offFront(x, z);
            if (ang >= GALLERY) s.set(x, GALLERY_STAND, z, 'r');
            else if (!Double.isNaN(top) && top >= 3) s.set(x, (int) Math.ceil(top), z, 'r');
        }
        // cladding anchors: the four flat faces' middles at y8
        for (long k : sh.edge) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k), du = x - C, dv = z - C;
            char ch = s.get(x, 8, z);
            if ((du == 0 || dv == 0) && (ch == 'W' || ch == 'L' || ch == 'G')) s.set(x, 8, z, 'A');
        }
        for (Object[] u : units) {
            @SuppressWarnings("unchecked")
            List<int[]> cells = (List<int[]>) u[2];
            s.unit((String) u[0], (String) u[1], cells, (Integer) u[3], null);
        }
        return s;
    }
}
