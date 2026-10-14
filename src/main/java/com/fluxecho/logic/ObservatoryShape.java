package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 观星台 The Observatory (0.16.0, CITY.md §4.12): the sky ring's module over the Archive (S4, site 34), an observatory
 * dome with its slit and a ring telescope. On its floating platform ({@link SkyRing#platform}): a vestibule (x 6..14,
 * z 1..5) with the kit portal and the controller {@code (10, 2, 1)} opens into an octagonal observing room
 * {@code oct(7, 9)} round {@code (10, 12)} (walls y1..5, the cornice at y6) under a hollow stepped dome (y7..11) with a
 * glazed meridian slit; the telescope pier in the middle (a console y1, pillars y2..6, a seat y7) and seven lens cases
 * (plinth and socket) round the wall.
 * <p>
 * Once it forms, the whole pier, its console, pillars and seat ({@link #DISSOLVES}, only in the pier's column:
 * {@link #pier}), gives way to the rendered telescope, while a big ring lens floats over the dome (CITY.md §4.12 and
 * the generator); the kit portal's console under the controller stays. Units: lens x7, one slot each, in the
 * generator's order ({@link #UNITS}, never to change).
 * <p>
 * The geometry is an exact port of the generator ({@code city/gen/b_sky.py}, {@code observatory()}). Counts: checked
 * 984, must-be-air 48 = 1,032 steps; with its catwalk and the pod's door its job has 1,099 steps.
 */
public final class ObservatoryShape {

    public static final String KEY = "observatory";
    /** The sky sites it may stand on, preferred first: S4 over the Archive (CITY.md §4.12). */
    public static final int[] SITES = { 34 };
    /** The box: the platform's 21 x 21, 13 high (y0..y12, the dome's top at y11). */
    public static final int WIDTH = SkyRing.SIZE, DEPTH = SkyRing.SIZE, HEIGHT = 13;
    /** The observing room's middle and its octagon {@code oct(7, 9)}. */
    public static final int ROOM_X = SkyRing.MID, ROOM_Z = 12, ROOM_HALF = 7, ROOM_K = 9;
    /** The vestibule: x 6..14, z 1..5. */
    public static final int VEST_X0 = 6, VEST_X1 = 14, VEST_Z0 = 1, VEST_Z1 = 5;
    /** The walls rise to {@code WALL_TOP} under the cornice at {@code CORNICE_Y}; the dome from {@code DOME_Y} up. */
    public static final int WALL_TOP = 5, CORNICE_Y = 6, DOME_Y = 7;
    /** The pier: its console at y1, its pillars from y2 to {@code PIER_TOP}, its seat at {@code SEAT_Y}. */
    public static final int PIER_TOP = 6, SEAT_Y = 7;
    /** The lens cases' places from the room's middle, in unit order. */
    public static final int[][] LENSES = { { 6, 0 }, { -6, 0 }, { 4, 4 }, { -4, 4 }, { 0, 6 }, { 4, -4 }, { -4, -4 } };
    /** The controller in the kit portal's trumeau. */
    public static final int CTRL_X = SkyRing.MID, CTRL_Y = 2, CTRL_Z = VEST_Z0;
    /**
     * The pier's console, pillars and seat: they give way to the rendered telescope, and players pass through them; the
     * console only in the pier's column ({@link #pier}, the tile's {@code dissolvesAt}), never the portal's under the
     * controller.
     */
    public static final String DISSOLVES = "KPZ", PASSES = "KPZ";
    /** The unit kind (lang {@code unit.lens}), the generator's. */
    public static final String KIND = "lens";

    /** The dome's steps {half, k, y}: each is only the ring it adds over the next one. */
    private static final int[][] STEPS = { { 6, 8, 7 }, { 5, 7, 8 }, { 4, 5, 9 }, { 3, 4, 10 }, { 1, 2, 11 } };

    /** The shape as the generator draws it (do not change it: every array is the shape itself). */
    public static final ShapeKit.Shape SHAPE = build();
    /** The units in slot order: lens-1..7. */
    public static final UnitLayout UNITS = new UnitLayout(SHAPE);
    /** The checked shape for StructureLib. */
    public static final Blueprint BLUEPRINT = ShapeKit.blueprint(SHAPE, HEIGHT);

    /** The walls' columns: the thick edge of the room and the vestibule together. */
    private static final Set<Long> EDGE;

    static {
        EDGE = Collections.unmodifiableSet(ShapeKit.thickBoundary(plan()));
        if (UNITS.size() != LENSES.length || UNITS.slots() != LENSES.length)
            throw new IllegalStateException("the observatory's units");
        if (SHAPE.H != HEIGHT) throw new IllegalStateException("the observatory is no longer 13 high");
    }

    /** The Observatory as its sky job builds it ({@link BuildPlan#sky}). */
    public static final CityBuild BUILD = new CityBuild(
        KEY,
        SHAPE,
        HEIGHT,
        BLUEPRINT,
        UNITS,
        Parts.moduleCore(ModuleCores.OBSERVATORY),
        new CityBuild.Stages() {

            @Override
            public int stage(int x, int y, int z, char ch) {
                return ObservatoryShape.stage(x, y, z, ch);
            }

            @Override
            public int fitRank(int x, int y, int z, char ch) {
                return ObservatoryShape.fitRank(x, y, z, ch);
            }
        });

    private ObservatoryShape() {}

    /** Whether the column (x, z) carries the walls (the thick edge of the room and the vestibule). */
    public static boolean onEdge(int x, int z) {
        return EDGE.contains(ShapeKit.key(x, z));
    }

    /** Whether the cell is the telescope pier's (its column, y1..y7). */
    public static boolean pier(int x, int y, int z) {
        return x == ROOM_X && z == ROOM_Z && y >= 1 && y <= SEAT_Y;
    }

    /**
     * The build stage of a cell that is not part of a unit ({@link SkyRing#K_CLEAR} ..): FLOAT the platform and its
     * rail; SHELL the walls y1..5 with the portal and its doors' air, and the cornice at y6; ROOF the vestibule's roof
     * and the room's ceiling ring at y6 and the stepped dome over them; FIT the controller's console before the lens
     * cases, then the pier; COMMISSION the controller.
     */
    public static int stage(int x, int y, int z, char ch) {
        int common = SkyRing.commonStage(y, ch);
        if (common >= 0) return common;
        if (pier(x, y, z)) return SkyRing.K_FIT;
        if (ch == 'K') return SkyRing.K_FIT;
        if (onEdge(x, z) && y <= CORNICE_Y) return SkyRing.K_SHELL;
        if (y >= CORNICE_Y) return SkyRing.K_ROOF;
        // the portal's trumeau posts stand in the vestibule's front wall, its edge
        throw new IllegalStateException("no stage for '" + ch + "' at " + x + "," + y + "," + z);
    }

    /** The order within FIT of a cell that is not part of a unit: the portal's console first, the pier after. */
    public static int fitRank(int x, int y, int z, char ch) {
        return pier(x, y, z) ? 1 : -1;
    }

    /** The standing states {x, z, h} the generator's walk starts from: the catwalk's mouth. */
    public static List<int[]> walkStarts() {
        return SkyRing.walkStarts();
    }

    /** The counts of the shape by character, for the tests. */
    static Map<Character, Integer> counts() {
        return SHAPE.counts();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // the generator (a port of b_sky.py's observatory)

    /** The room's octagon and the vestibule's box together. */
    private static Set<Long> plan() {
        Set<Long> all = new LinkedHashSet<>(room());
        for (int x = VEST_X0; x <= VEST_X1; x++) for (int z = VEST_Z0; z <= VEST_Z1; z++) all.add(ShapeKit.key(x, z));
        return all;
    }

    /** The observing room's octagon {@code oct(7, 9)} round its middle. */
    private static Set<Long> room() {
        return octagon(ROOM_HALF, ROOM_K);
    }

    /** The columns of the octagon {@code oct(half, k)} round the room's middle. */
    private static Set<Long> octagon(int half, int k) {
        Set<Long> out = new LinkedHashSet<>();
        for (int x = 0; x < WIDTH; x++) for (int z = 0; z < DEPTH; z++) {
            if (ShapeKit.octo(x - ROOM_X, z - ROOM_Z, half, k)) out.add(ShapeKit.key(x, z));
        }
        return out;
    }

    private static ShapeKit.Shape build() {
        final int cx = ROOM_X, cz = ROOM_Z;
        ShapeKit.Shape s = SkyRing.platform(KEY, HEIGHT);
        Set<Long> room = room(), all = plan();
        Set<Long> edge = ShapeKit.thickBoundary(all);
        for (long k : edge) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k);
            for (int y = 1; y <= WALL_TOP; y++) {
                boolean rib = (x - cx == 0 || Math.abs(x - cx) == Math.abs(z - cz)) && room.contains(k);
                s.set(x, y, z, y == 1 ? 'w' : rib ? 'L' : 'W');
            }
            s.set(x, CORNICE_Y, z, 'n');
        }
        // the vestibule's roof, where it is not the room
        for (int x = VEST_X0; x <= VEST_X1; x++) for (int z = VEST_Z0; z <= VEST_Z1; z++) {
            long k = ShapeKit.key(x, z);
            if (!edge.contains(k) && !room.contains(k)) s.set(x, CORNICE_Y, z, 'W');
        }
        ShapeKit.portal(s, VEST_Z0, WALL_TOP, false, SkyRing.MID);
        // the hollow stepped dome: each step only the ring it adds over the next one
        for (int i = 0; i < STEPS.length; i++) {
            Set<Long> ring = octagon(STEPS[i][0], STEPS[i][1]);
            if (i + 1 < STEPS.length) ring.removeAll(octagon(STEPS[i + 1][0], STEPS[i + 1][1]));
            int y = STEPS[i][2];
            for (long k : ring) {
                int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k);
                s.set(x, y, z, x == cx ? 'G' : Math.abs(x - cx) == Math.abs(z - cz) || z == cz ? 'L' : 'W');
            }
        }
        // the room's ceiling ring at the cornice, round the dome's first step
        for (long k : room) {
            if (edge.contains(k)) continue;
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k);
            if (s.get(x, CORNICE_Y, z) == ShapeKit.NONE && !ShapeKit.octo(x - cx, z - cz, 6, 8))
                s.set(x, CORNICE_Y, z, 'W');
        }
        // the telescope pier
        s.set(cx, 1, cz, 'K');
        for (int y = 2; y <= PIER_TOP; y++) s.set(cx, y, cz, 'P');
        s.set(cx, SEAT_Y, cz, 'Z');
        List<Object[]> units = new ArrayList<>();
        for (int i = 0; i < LENSES.length; i++) {
            int x = cx + LENSES[i][0], z = cz + LENSES[i][1];
            s.set(x, 1, z, 'p');
            s.set(x, 2, z, 'o');
            List<int[]> cells = new ArrayList<>();
            cells.add(new int[] { x, 1, z });
            cells.add(new int[] { x, 2, z });
            units.add(new Object[] { "lens-" + (i + 1), cells });
        }
        for (Object[] u : units) {
            @SuppressWarnings("unchecked")
            List<int[]> cells = (List<int[]>) u[1];
            s.unit((String) u[0], KIND, cells, 1, null);
        }
        return s;
    }
}
