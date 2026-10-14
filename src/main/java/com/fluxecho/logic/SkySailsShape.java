package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 天光帆阵 The Sky Sail Array (0.16.0, CITY.md §4.12): the sky ring's module on S2 (site 32), or S6 when the hangar took
 * it, solar sails (IKAROS) rigged like a square-rigged ship. On its floating platform ({@link SkyRing#platform}): the
 * bridge cabin at the front (x 6..14, z 1..6: walls y1..4, roof y5, the kit portal with the controller
 * {@code (10, 2, 1)}, a back door, two holo pedestals inside), and six masts behind it in three pairs at
 * {@code (+-6, 0)}, {@code (+-6, 5)}, {@code (+-3, 8)} from the middle: a plinth, the sail socket, ten posts and a
 * crown
 * at y13. Three huge rendered light sails spread between the pairs.
 * <p>
 * A mast's unit is its plinth and socket only ({@link #UNITS}, 0.16.0's generator): the posts and the crown are plain
 * checked cells, so over high ground, where the masts clip at the world's top, no unit cell ever leaves the world.
 * Units: mast x6, one slot each (a resonant-glass sail), in the generator's order (never to change). Nothing dissolves.
 * <p>
 * The geometry is an exact port of the generator ({@code city/gen/b_sky.py}, {@code sail_array()}). Counts: checked
 * 637, must-be-air 57 = 694 steps; with its catwalk and the pod's door its job has 761 steps.
 */
public final class SkySailsShape {

    public static final String KEY = "sky_sails";
    /** The sky sites it may stand on, preferred first: S2, then S6 (CITY.md §4.12). */
    public static final int[] SITES = { 32, 36 };
    /** The box: the platform's 21 x 21, 15 high (y0..y14, the crowns at y13). */
    public static final int WIDTH = SkyRing.SIZE, DEPTH = SkyRing.SIZE, HEIGHT = 15;
    /** The bridge cabin: x 6..14, z 1..6, walls to y4 under its roof at y5. */
    public static final int HUT_X0 = 6, HUT_X1 = 14, HUT_Z0 = 1, HUT_Z1 = 6, HUT_TOP = 4;
    /** A mast: its plinth at y1, its socket at y2, its posts y3..y12, its crown at y13. */
    public static final int POST_FROM = 3, POST_TO = 12, CROWN_Y = 13;
    /** The masts' places from the middle, in unit order. */
    public static final int[][] MASTS = { { -6, 0 }, { 6, 0 }, { -6, 5 }, { 6, 5 }, { -3, 8 }, { 3, 8 } };
    /** The controller in the kit portal's trumeau. */
    public static final int CTRL_X = SkyRing.MID, CTRL_Y = 2, CTRL_Z = HUT_Z0;
    /** Nothing gives way once it forms: the sails are drawn between the masts. */
    public static final String DISSOLVES = "", PASSES = "";
    /** The unit kind (lang {@code unit.mast}), the generator's. */
    public static final String KIND = "mast";

    /** The shape as the generator draws it (do not change it: every array is the shape itself). */
    public static final ShapeKit.Shape SHAPE = build();
    /** The units in slot order: mast-1..6. */
    public static final UnitLayout UNITS = new UnitLayout(SHAPE);
    /** The checked shape for StructureLib. */
    public static final Blueprint BLUEPRINT = ShapeKit.blueprint(SHAPE, HEIGHT);

    static {
        if (UNITS.size() != MASTS.length || UNITS.slots() != MASTS.length)
            throw new IllegalStateException("the sky sail array's units");
        if (SHAPE.H != HEIGHT) throw new IllegalStateException("the sky sail array is no longer 15 high");
    }

    /** The Sky Sail Array as its sky job builds it ({@link BuildPlan#sky}). */
    public static final CityBuild BUILD = new CityBuild(
        KEY,
        SHAPE,
        HEIGHT,
        BLUEPRINT,
        UNITS,
        Parts.moduleCore(ModuleCores.SKY_SAILS),
        new CityBuild.Stages() {

            @Override
            public int stage(int x, int y, int z, char ch) {
                return SkySailsShape.stage(x, y, z, ch);
            }

            @Override
            public int fitRank(int x, int y, int z, char ch) {
                return SkySailsShape.fitRank(x, y, z, ch);
            }
        });

    private SkySailsShape() {}

    /** Whether the cell lies in the bridge cabin's box (its walls, the portal, its roof). */
    public static boolean inHut(int x, int z) {
        return x >= HUT_X0 && x <= HUT_X1 && z >= HUT_Z0 && z <= HUT_Z1;
    }

    /** The mast (0..5, unit order) whose column (x, z) is, or -1. */
    public static int mastAt(int x, int z) {
        for (int i = 0; i < MASTS.length; i++)
            if (x == SkyRing.MID + MASTS[i][0] && z == SkyRing.MID + MASTS[i][1]) return i;
        return -1;
    }

    /**
     * The build stage of a cell that is not part of a unit ({@link SkyRing#K_CLEAR} ..): FLOAT the platform and its
     * rail; SHELL the cabin's walls, the portal and its doors' air; ROOF the cabin's roof; FIT the controller's console
     * before the units, then the holo pedestals and the masts' posts and crowns; COMMISSION the controller.
     */
    public static int stage(int x, int y, int z, char ch) {
        int common = SkyRing.commonStage(y, ch);
        if (common >= 0) return common;
        if (ch == 'K' || ch == 'h') return SkyRing.K_FIT;
        if (mastAt(x, z) >= 0) return SkyRing.K_FIT;
        if (inHut(x, z)) return y <= HUT_TOP ? SkyRing.K_SHELL : SkyRing.K_ROOF;
        throw new IllegalStateException("no stage for '" + ch + "' at " + x + "," + y + "," + z);
    }

    /**
     * The order within FIT of a cell that is not part of a unit: the console first; after the masts' units the holo
     * pedestals, then the masts' posts and crowns, bottom-up.
     */
    public static int fitRank(int x, int y, int z, char ch) {
        if (ch == 'K') return -1;
        return ch == 'h' ? 1 : 2;
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
    // the generator (a port of b_sky.py's sail_array)

    private static ShapeKit.Shape build() {
        ShapeKit.Shape s = SkyRing.platform(KEY, HEIGHT);
        SkyRing.hut(s, HUT_X0, HUT_X1, HUT_Z0, HUT_Z1, HUT_TOP);
        s.set(8, 1, 4, 'h');
        s.set(12, 1, 4, 'h');
        List<Object[]> units = new ArrayList<>();
        for (int i = 0; i < MASTS.length; i++) {
            int x = SkyRing.MID + MASTS[i][0], z = SkyRing.MID + MASTS[i][1];
            s.set(x, 1, z, 'p');
            s.set(x, 2, z, 'o');
            for (int y = POST_FROM; y <= POST_TO; y++) s.set(x, y, z, 'q');
            s.set(x, CROWN_Y, z, 'c');
            List<int[]> cells = new ArrayList<>();
            cells.add(new int[] { x, 1, z });
            cells.add(new int[] { x, 2, z });
            units.add(new Object[] { "mast-" + (i + 1), cells });
        }
        for (Object[] u : units) {
            @SuppressWarnings("unchecked")
            List<int[]> cells = (List<int[]>) u[1];
            s.unit((String) u[0], KIND, cells, 1, null);
        }
        return s;
    }
}
