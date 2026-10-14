package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 天穹气象站 The Weather Station (0.16.0, CITY.md §4.12): the sky ring's module over the gate (S0, site 30), a weather
 * tower and an anemometer mast with Tatlin's tower in miniature behind it. On its floating platform
 * ({@link SkyRing#platform}): an instrument hut at the front (x 6..14, z 1..5: walls y1..4, roof y5, the kit portal
 * with the controller {@code (10, 2, 1)} in its front wall, a back door), the weather mast in the middle of the deck
 * ({@code (10, 12)}: pillars y1..11, a conduit y12, a seat y13), four weather consoles (console and socket) round the
 * mast, and two vane pylons (pillars y1..7 and a ring y8) at the back, {@code (+-6, 7)} from the middle.
 * <p>
 * Once it forms, the mast (its pillars, conduit and seat, {@link #DISSOLVES} in its column {@link #MAST_X},
 * {@link #MAST_Z} only) gives way to the rendered turning vanes under a slowly churning cloud, and players pass through
 * it; the pylons keep their blocks. Units: weather x4, one slot each (weather cores), in the generator's order
 * ({@link #UNITS}, never to change).
 * <p>
 * The geometry is an exact port of the generator ({@code city/gen/b_sky.py}, {@code weather_station()}). Counts:
 * checked 575, must-be-air 57 = 632 steps; with its catwalk (55 soft) and the pod's door (12 must-be-air) its job has
 * 699 steps.
 */
public final class WeatherStationShape {

    public static final String KEY = "weather_station";
    /** The sky sites it may stand on, preferred first: S0 over the gate (CITY.md §4.12). */
    public static final int[] SITES = { 30 };
    /** The box: the platform's 21 x 21, 14 high (y0..y13). */
    public static final int WIDTH = SkyRing.SIZE, DEPTH = SkyRing.SIZE, HEIGHT = 14;
    /** The instrument hut: x 6..14, z 1..5, walls to y4 under its roof at y5. */
    public static final int HUT_X0 = 6, HUT_X1 = 14, HUT_Z0 = 1, HUT_Z1 = 5, HUT_TOP = 4;
    /** The weather mast's column, and its pillars' top, its conduit and its seat. */
    public static final int MAST_X = SkyRing.MID, MAST_Z = 12, MAST_TOP = 11, CONDUIT_Y = 12, SEAT_Y = 13;
    /** The vane pylons: {@code (+-6, 7)} from the middle at z 17, pillars to y7 and a ring at y8. */
    public static final int PYLON_DX = 6, PYLON_Z = 17, PYLON_TOP = 7, PYLON_RING = 8;
    /** The controller in the kit portal's trumeau. */
    public static final int CTRL_X = SkyRing.MID, CTRL_Y = 2, CTRL_Z = HUT_Z0;
    /**
     * The characters that give way to the rendered vanes and cloud once it forms, in the mast's column only (the
     * pylons' pillars and rings stay), and that players pass through.
     */
    public static final String DISSOLVES = "PCZ", PASSES = "PCZ";
    /** The unit kind (lang {@code unit.weather}), the generator's. */
    public static final String KIND = "weather";

    /** The shape as the generator draws it (do not change it: every array is the shape itself). */
    public static final ShapeKit.Shape SHAPE = build();
    /** The units in slot order: weather-1..4. */
    public static final UnitLayout UNITS = new UnitLayout(SHAPE);
    /** The checked shape for StructureLib. */
    public static final Blueprint BLUEPRINT = ShapeKit.blueprint(SHAPE, HEIGHT);

    static {
        if (UNITS.size() != 4 || UNITS.slots() != 4) throw new IllegalStateException("the weather station's units");
        if (SHAPE.H != HEIGHT) throw new IllegalStateException("the weather station is no longer 14 high");
    }

    /** The Weather Station as its sky job builds it ({@link BuildPlan#sky}). */
    public static final CityBuild BUILD = new CityBuild(
        KEY,
        SHAPE,
        HEIGHT,
        BLUEPRINT,
        UNITS,
        Parts.moduleCore(ModuleCores.WEATHER_STATION),
        new CityBuild.Stages() {

            @Override
            public int stage(int x, int y, int z, char ch) {
                return WeatherStationShape.stage(x, y, z, ch);
            }

            @Override
            public int fitRank(int x, int y, int z, char ch) {
                return WeatherStationShape.fitRank(x, y, z, ch);
            }
        });

    private WeatherStationShape() {}

    /** Whether a cell is one of the mast's (its column, y1..y13): the cells that dissolve. */
    public static boolean mast(int x, int y, int z) {
        return x == MAST_X && z == MAST_Z && y >= 1 && y <= SEAT_Y;
    }

    /** Whether the cell lies in the instrument hut's box (its walls, the portal, its roof). */
    public static boolean inHut(int x, int z) {
        return x >= HUT_X0 && x <= HUT_X1 && z >= HUT_Z0 && z <= HUT_Z1;
    }

    /**
     * The build stage of a cell that is not part of a unit ({@link SkyRing#K_CLEAR} ..): FLOAT the platform and its
     * rail; SHELL the hut's walls, the portal and its doors' air; ROOF the hut's roof; FIT the controller's console
     * before the units, then the mast and the pylons; COMMISSION the controller.
     */
    public static int stage(int x, int y, int z, char ch) {
        int common = SkyRing.commonStage(y, ch);
        if (common >= 0) return common;
        if (ch == 'K') return SkyRing.K_FIT;
        if (inHut(x, z)) return y <= HUT_TOP ? SkyRing.K_SHELL : SkyRing.K_ROOF;
        if (ch == 'P' || ch == 'C' || ch == 'Z' || ch == 'R') return SkyRing.K_FIT;
        throw new IllegalStateException("no stage for '" + ch + "' at " + x + "," + y + "," + z);
    }

    /** The order within FIT of a cell that is not part of a unit: the console first, the mast and pylons after. */
    public static int fitRank(int x, int y, int z, char ch) {
        return ch == 'K' ? -1 : 1;
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
    // the generator (a port of b_sky.py's weather_station)

    private static ShapeKit.Shape build() {
        ShapeKit.Shape s = SkyRing.platform(KEY, HEIGHT);
        SkyRing.hut(s, HUT_X0, HUT_X1, HUT_Z0, HUT_Z1, HUT_TOP);
        for (int y = 1; y <= MAST_TOP; y++) s.set(MAST_X, y, MAST_Z, 'P');
        s.set(MAST_X, CONDUIT_Y, MAST_Z, 'C');
        s.set(MAST_X, SEAT_Y, MAST_Z, 'Z');
        List<Object[]> units = new ArrayList<>();
        int[][] round = { { 3, 0 }, { -3, 0 }, { 0, 3 }, { 0, -3 } };
        for (int i = 0; i < round.length; i++) {
            int x = MAST_X + round[i][0], z = MAST_Z + round[i][1];
            s.set(x, 1, z, 'K');
            s.set(x, 2, z, 'o');
            List<int[]> cells = new ArrayList<>();
            cells.add(new int[] { x, 1, z });
            cells.add(new int[] { x, 2, z });
            units.add(new Object[] { "weather-" + (i + 1), cells });
        }
        for (int du : new int[] { -PYLON_DX, PYLON_DX }) {
            for (int y = 1; y <= PYLON_TOP; y++) s.set(SkyRing.MID + du, y, PYLON_Z, 'P');
            s.set(SkyRing.MID + du, PYLON_RING, PYLON_Z, 'R');
        }
        for (Object[] u : units) {
            @SuppressWarnings("unchecked")
            List<int[]> cells = (List<int[]>) u[1];
            s.unit((String) u[0], KIND, cells, 1, null);
        }
        return s;
    }
}
