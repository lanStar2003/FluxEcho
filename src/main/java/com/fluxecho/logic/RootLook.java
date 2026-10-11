package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where the Tidal Well's drawing puts its light (0.12.0, CITY.md §4.9 Visuals), worked out from its shape
 * ({@link RootShape}) in the well's own frame (x across, y up from the floor slab at Y0 - 26, z from the front):
 * <ul>
 * <li>The three tidal rings: once it forms, the ring segments give way to drawn rings that rise and fall with the tide
 * ({@link Tide}, the well eating it twice over) and turn, the upper ones a little behind the lower ({@link #ringRise},
 * {@link #ringTurn}); never low enough to touch a head on the island or the bridges, never into the ramp.</li>
 * <li>The ribs: a line of light up the inner face of each of the eight lit ribs ({@link #ribs}), broken where the
 * flux-layer bands cross them ({@link #ribRuns}), a pulse climbing them.</li>
 * <li>The bands: the three flux-layer bands (表层, 记忆层, 深层) as lines along the inner faces of their dark courses
 * ({@link #bandFaces}).</li>
 * <li>The pool: its water cells ({@link ModuleLook#water}), for the caustics on its surface.</li>
 * <li>From the surface: the forum's grate band round the dais over the well ({@link #grate}), glowing with the tide,
 * and
 * at the full tide the rings' crests showing through it ({@link #crest}).</li>
 * </ul>
 * Pure, so the tests hold the rings clear of the people and the lines on the walls.
 */
public final class RootLook {

    /** How far the rings rise and fall at the top of a spring tide (blocks). */
    public static final double RISE = 1.25;
    /** How far behind the ring under it each ring follows the tide (ticks of day time). */
    public static final int LAG = 400;
    /** How fast the rings turn (radians a tick), and how far the tide sways them on top of that (radians). */
    public static final double SPIN = 0.004, SWAY = 0.8;
    /** A standing player's head over the floor they stand on, and the room kept over it. */
    public static final double HEAD = 1.8, CLEAR = 0.5;
    /** From how high a tide the rings' crests show through the grate band, and how high they rise at most. */
    public static final double CREST_FROM = 0.75, CREST_HIGH = 0.9;
    /** The forum layer over the lid (Y0) in the well's frame. */
    public static final int SURFACE_Y = RootShape.HEIGHT;

    private static final int[][] SIDES = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
    /** The eight lines the lit ribs stand on, from the front clockwise seen from above. */
    private static final int[][] LINES = { { 0, -1 }, { 1, -1 }, { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 }, { -1, 0 },
        { -1, -1 } };

    private RootLook() {}

    /** The tide the well sees at a day time: {@link Tide#norm}, -1 to 1. */
    public static double tide(double dayTime) {
        return Tide.norm(dayTime);
    }

    /**
     * How far ring {@code ring} (0 the lowest) stands over or under its place at a day time: {@link #RISE} each way at
     * a
     * spring tide (the well swings twice as far as the tide, so a neap tide still moves it a quarter as much), a ring
     * {@link #LAG} ticks behind the one under it.
     */
    public static double ringRise(int ring, double dayTime) {
        return RISE * Tide.norm(dayTime - LAG * ring);
    }

    /**
     * How far ring {@code ring} has turned (radians) at the world's total time {@code t} and day time
     * {@code dayTime}: a slow spin, the middle ring the other way, swaying with the tide.
     */
    public static double ringTurn(int ring, double t, double dayTime) {
        double dir = ring % 2 == 0 ? 1 : -1;
        return dir * (SPIN * t + SWAY * Tide.norm(dayTime - LAG * ring));
    }

    /** The lowest a ring's underside comes (its layer less the fall), in the well's frame. */
    public static double ringLowest(int ring) {
        return RootShape.RING_Y[ring] - RISE;
    }

    /** The highest a ring's top comes, in the well's frame. */
    public static double ringHighest(int ring) {
        return RootShape.RING_Y[ring] + 1 + RISE;
    }

    /**
     * How high the rings' crests rise through the grate band at a tide ({@link #tide}): nothing below
     * {@link #CREST_FROM}, then up to {@link #CREST_HIGH} blocks over the forum at the top of a spring tide.
     */
    public static double crest(double tide) {
        if (tide <= CREST_FROM) return 0;
        return CREST_HIGH * Math.min(1, (tide - CREST_FROM) / (1 - CREST_FROM));
    }

    /**
     * The eight ribs' inner faces: for each line from the front clockwise, the point {x, z} where a ray from the
     * well's axis along it first enters the wall, and the line's direction {dx, dz} (unit length).
     */
    public static double[][] ribs() {
        double[][] out = new double[LINES.length][];
        double c = RootShape.MID + 0.5;
        for (int i = 0; i < LINES.length; i++) {
            double l = Math.hypot(LINES[i][0], LINES[i][1]), dx = LINES[i][0] / l, dz = LINES[i][1] / l;
            double tau = 0;
            while (tau < RootShape.SIZE) {
                int x = (int) Math.floor(c + dx * tau), z = (int) Math.floor(c + dz * tau);
                if (RootShape.wall(x, z)) break;
                tau += 0.01;
            }
            out[i] = new double[] { c + dx * tau, c + dz * tau, dx, dz };
        }
        return out;
    }

    /**
     * The stretches of rib {@code rib} that are lit, {from, to} layers (to exclusive), bottom up: the rib cells
     * ({@code L}) of the wall where its ray enters it, broken by the flux-layer bands.
     */
    public static List<int[]> ribRuns(int rib) {
        double[] r = ribs()[rib];
        int x = (int) Math.floor(r[0] + r[2] * 0.02), z = (int) Math.floor(r[1] + r[3] * 0.02);
        List<int[]> out = new ArrayList<>();
        int from = -1;
        for (int y = RootShape.FLOOR_Y; y <= RootShape.LID_Y; y++) {
            boolean lit = y < RootShape.LID_Y && RootShape.cell(x, y, z) == 'L';
            if (lit && from < 0) from = y;
            if (!lit && from >= 0) {
                out.add(new int[] { from, y });
                from = -1;
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * The inner faces of a flux-layer band's dark course at layer {@code y} ({@link RootShape#BANDS}): {x, z, inX, inZ}
     * for every course cell of the wall and every side of it that looks into the well.
     */
    public static List<int[]> bandFaces(int y) {
        List<int[]> out = new ArrayList<>();
        for (int x = 0; x < RootShape.SIZE; x++) for (int z = 0; z < RootShape.SIZE; z++) {
            if (!RootShape.wall(x, z) || RootShape.cell(x, y, z) != ModuleLook.COURSE) continue;
            for (int[] o : SIDES) {
                int nx = x + o[0], nz = z + o[1];
                if (RootShape.inside(nx, y, nz)) out.add(new int[] { x, z, o[0], o[1] });
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * The forum's grate band over the well, {x, z} in the well's frame (the cells of the forum layer, Y0, at
     * {@link #SURFACE_Y}): what the establish job paved as grate round the dais ({@link CampusPlan#grateBand}).
     */
    public static List<int[]> grate() {
        List<int[]> out = new ArrayList<>();
        int h = RootShape.MID;
        for (int a = -h; a <= h; a++) for (int r = -h; r <= h; r++) {
            if (!CampusPlan.grateBand(a, r)) continue;
            int[] p = RootShape.fromCampus(a, r, 0);
            out.add(new int[] { p[0], p[2] });
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * The highest a standing player's head comes under a ring: over every floor cell (a top people stand on, with
     * room above it) within the ring's outer radius, its stand height plus {@link #HEAD}; the floor's level when there
     * is none.
     */
    public static double headUnder(int ring) {
        double outer = RootShape.RING_R[ring] + 1, c = RootShape.MID;
        double top = RootShape.FLOOR_Y + 1;
        for (int x = 0; x < RootShape.SIZE; x++) for (int z = 0; z < RootShape.SIZE; z++) {
            if (Math.hypot(x - c, z - c) > outer + 0.75) continue;
            for (int y = 0; y < RootShape.RING_Y[ring]; y++) {
                char ch = RootShape.cell(x, y, z);
                // nobody stands in water, on a ring or on the controller
                if (ch == ShapeKit.NONE || ch == ShapeKit.AIR
                    || ch == '='
                    || ch == RootShape.RING
                    || ch == ShapeKit.CONTROLLER) continue;
                char up = RootShape.cell(x, y + 1, z);
                if (up == ShapeKit.NONE || up == ShapeKit.AIR) top = Math.max(top, y + 1 + HEAD);
            }
        }
        return top;
    }
}
