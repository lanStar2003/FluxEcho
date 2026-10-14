package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 水滴机库 The Droplet Hangar (0.16.0, CITY.md §4.12): the sky ring's module on S6 (site 36), or S2 when the sails took
 * it, *Three-Body*'s droplets on a carrier deck. On its floating platform ({@link SkyRing#platform}): eight droplet
 * cradles (a 3 x 3 basin rim round a socket) in four mirrored pairs at {@code (+-6, -2)}, {@code (+-6, 3)},
 * {@code (+-3, 7)}, {@code (+-3, -6)} from the middle, and the control kiosk at the front (a console y1 and the
 * controller y2 at {@code (10, 2, 2)}) under a canopy ring of frame ring segments (y5).
 * <p>
 * Once it forms, the canopy ring ({@link #DISSOLVES}) gives way to its rendered light ring, and players pass through it
 * (it hangs at y5 over the kiosk, out of reach). Units: cradle x8, one slot each (a droplet), in the generator's order
 * ({@link #UNITS}, never to change), of the new kind {@link #KIND} {@code droplet_cradle}: the generator calls them
 * {@code cradle}, which is already the Blood Covenant Altar's orb cradle's kind.
 * <p>
 * The geometry is an exact port of the generator ({@code city/gen/b_sky.py}, {@code droplet_hangar()}). Counts:
 * checked 520 = 520 steps; with its catwalk and the pod's door its job has 587 steps.
 */
public final class DropletHangarShape {

    public static final String KEY = "droplet_hangar";
    /** The sky sites it may stand on, preferred first: S6, then S2 (CITY.md §4.12). */
    public static final int[] SITES = { 36, 32 };
    /** The box: the platform's 21 x 21, 7 high (y0..y6, the canopy ring at y5). */
    public static final int WIDTH = SkyRing.SIZE, DEPTH = SkyRing.SIZE, HEIGHT = 7;
    /** The cradles' middles from the platform's middle, in unit order. */
    public static final int[][] CRADLES = { { -6, -2 }, { 6, -2 }, { -6, 3 }, { 6, 3 }, { -3, 7 }, { 3, 7 }, { -3, -6 },
        { 3, -6 } };
    /** The controller on the kiosk's console, two rows in from the front. */
    public static final int CTRL_X = SkyRing.MID, CTRL_Y = 2, CTRL_Z = 2;
    /** The canopy ring's height. */
    public static final int RING_Y = 5;
    /** The canopy ring: it gives way to the rendered light ring, and players pass through it. */
    public static final String DISSOLVES = "R", PASSES = "R";
    /**
     * The unit kind (lang {@code unit.droplet_cradle}): new in 0.16.0, since {@code cradle} is the Blood Covenant
     * Altar's.
     */
    public static final String KIND = "droplet_cradle";

    /** The shape as the generator draws it (do not change it: every array is the shape itself). */
    public static final ShapeKit.Shape SHAPE = build();
    /** The units in slot order: cradle-1..8. */
    public static final UnitLayout UNITS = new UnitLayout(SHAPE);
    /** The checked shape for StructureLib. */
    public static final Blueprint BLUEPRINT = ShapeKit.blueprint(SHAPE, HEIGHT);

    static {
        if (UNITS.size() != CRADLES.length || UNITS.slots() != CRADLES.length)
            throw new IllegalStateException("the droplet hangar's units");
        if (SHAPE.H != HEIGHT) throw new IllegalStateException("the droplet hangar is no longer 7 high");
    }

    /** The Droplet Hangar as its sky job builds it ({@link BuildPlan#sky}). */
    public static final CityBuild BUILD = new CityBuild(
        KEY,
        SHAPE,
        HEIGHT,
        BLUEPRINT,
        UNITS,
        Parts.moduleCore(ModuleCores.DROPLET_HANGAR),
        new CityBuild.Stages() {

            @Override
            public int stage(int x, int y, int z, char ch) {
                return DropletHangarShape.stage(x, y, z, ch);
            }

            @Override
            public int fitRank(int x, int y, int z, char ch) {
                return DropletHangarShape.fitRank(x, y, z, ch);
            }
        });

    private DropletHangarShape() {}

    /**
     * The build stage of a cell that is not part of a unit ({@link SkyRing#K_CLEAR} ..): FLOAT the platform and its
     * rail; ROOF the canopy ring; FIT the kiosk's console before the cradles; COMMISSION the controller. It has no
     * walls:
     * its SHELL is empty.
     */
    public static int stage(int x, int y, int z, char ch) {
        int common = SkyRing.commonStage(y, ch);
        if (common >= 0) return common;
        if (ch == 'K') return SkyRing.K_FIT;
        if (ch == 'R' && y == RING_Y) return SkyRing.K_ROOF;
        throw new IllegalStateException("no stage for '" + ch + "' at " + x + "," + y + "," + z);
    }

    /** The order within FIT of a cell that is not part of a unit: the kiosk's console, before the cradles. */
    public static int fitRank(int x, int y, int z, char ch) {
        return -1;
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
    // the generator (a port of b_sky.py's droplet_hangar)

    private static ShapeKit.Shape build() {
        ShapeKit.Shape s = SkyRing.platform(KEY, HEIGHT);
        List<Object[]> units = new ArrayList<>();
        for (int i = 0; i < CRADLES.length; i++) {
            int x = SkyRing.MID + CRADLES[i][0], z = SkyRing.MID + CRADLES[i][1];
            List<int[]> cells = new ArrayList<>();
            for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
                s.set(x + a, 1, z + b, a == 0 && b == 0 ? 'o' : 'u');
                cells.add(new int[] { x + a, 1, z + b });
            }
            units.add(new Object[] { "cradle-" + (i + 1), cells });
        }
        s.set(CTRL_X, 1, CTRL_Z, 'K');
        s.set(CTRL_X, CTRL_Y, CTRL_Z, ShapeKit.CONTROLLER);
        s.controller = new int[] { CTRL_X, CTRL_Y, CTRL_Z };
        for (int x = 0; x < WIDTH; x++) for (int z = 0; z < DEPTH; z++) {
            if (!SkyRing.onPlatform(x, z)) continue;
            int du = x - SkyRing.MID, dv = z - CTRL_Z;
            if (ShapeKit.octo(du, dv, 2, 3) && !ShapeKit.octo(du, dv, 1, 2) && z >= 0) s.set(x, RING_Y, z, 'R');
        }
        for (Object[] u : units) {
            @SuppressWarnings("unchecked")
            List<int[]> cells = (List<int[]>) u[1];
            s.unit((String) u[0], KIND, cells, 1, null);
        }
        return s;
    }
}
