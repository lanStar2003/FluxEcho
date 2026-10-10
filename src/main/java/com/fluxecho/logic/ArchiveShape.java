package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * The Echo Archive (0.10.0): the "Long Room" library the nexus builds on a hall site. It is 27 across, 35 deep and 16
 * high: a two-storey hall with five double-faced shelf ranges per side, a double-height void over the nave, a gallery
 * deck at y6 reached by two mirrored spiral stairs in the front corners, a reading area with the desk at the back,
 * and a raised clerestory roof with a skylight slit along the ridge. The controller sits in the trumeau between the
 * two front doors, at eye height above a console stand.
 * <p>
 * The geometry is a faithful port of the design generator ({@code spec/gen/archive.py}) with its characters renamed so
 * they are unique across every library shape (the 0.9.2 hall uses {@code F P H B K ~ -}; the shared ones mean the
 * same here). The generator used {@code C} for both unit crowns and the cornice band; here crowns are {@code c} and
 * the cornice (the y12 perimeter and the lintel over the doors) is {@code n}. Its stair blocks and roof panels became
 * plain panels {@code W}. One deliberate change: the generator put a gallery slab over the sixth step of each stair
 * (x1 / x25, z1), which leaves a player stepping off that step to the seventh only 2 blocks under the slab, too low
 * for the 1.8-high player box to rise half a block; the slab is left out, so each stair has two slab cells fewer.
 * <p>
 * Coordinates: {@code x} across 0..26 (the axis at {@link #MID}), {@code y} up 0..15 (y0 the foundation layer, the
 * floor players stand on), {@code z} from the front 0..34 (the front faces the nexus). This is the frame of
 * {@link Blueprint}: {@code x} is the blueprint's across axis, which runs to the right of someone standing outside
 * looking at the front, so the frame is mirrored relative to the world's axes; rules about "left" and "right" must
 * account for that.
 */
public final class ArchiveShape {

    public static final int WIDTH = 27, DEPTH = 35, HEIGHT = 16, MID = 13, CTRL_X = 13, CTRL_Y = 2, CTRL_Z = 0;

    /** Blueprint characters (see the class comment for the table of parts). */
    public static final char FOUNDATION = 'F', AIR = '-', CONTROLLER = '~', CONSOLE = 'K', BODY = 'S', POST = 'q',
        PLINTH = 'p', CROWN = 'c', CORNICE = 'n', PANEL = 'W', PILASTER = 'L', COURSE = 'w', GLAZE = 'G', SLAB = 'd',
        EDGE = 'e', RAIL = 'r', PEDESTAL = 'h', TREAD = 't';
    /** Soft interior floor: paved as a plain deck, never checked by the structure. Only {@link #cell} returns it. */
    public static final char SOFT = '.';
    /** Anything (not checked). */
    public static final char ANY = ' ';

    /** The gallery deck layer, the flat roof and cornice layer, the clerestory, the step-back and the ridge. */
    public static final int DECK_Y = 6, ROOF_Y = 12, CLERESTORY_Y = 13, STEP_BACK_Y = 14, RIDGE_Y = 15;

    /** The reading desk: a console stand on the axis, under the skylight slit. */
    public static final int[] DESK = { 13, 1, 29 };

    /** The five double-faced ranges: the z of their door-side run and of their back-side run. */
    static final int[][] RANGES = { { 5, 6 }, { 10, 11 }, { 15, 16 }, { 20, 21 }, { 25, 26 } };
    /** The double-height void over the nave, inclusive bounds. */
    static final int VOID_X0 = 9, VOID_X1 = 17, VOID_Z0 = 5, VOID_Z1 = 29;
    /** The centre posts of the two spiral stairs (left, right). */
    static final int[][] STAIRS = { { 2, 2 }, { 24, 2 } };
    /** The eight cells around a stair post in walking order for the left stair; the right stair mirrors x. */
    private static final int[][] RING = { { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 }, { -1, 0 }, { -1, -1 }, { 0, -1 },
        { 1, -1 } };
    /** Steps per stair: one and a half turns, rising half a block each, from the floor to the gallery deck. */
    public static final int STEPS = 12;

    /** Every step of both stairs, {x, y, z}: the left stair's twelve in walking order, then the right stair's. */
    private static final int[][] STEP_CELLS = steps();
    /** The character of every cell, indexed by {@link #index}. */
    private static final char[] GRID = grid();

    public static final Blueprint BLUEPRINT = new Blueprint(layers());

    private ArchiveShape() {}

    private static int index(int x, int y, int z) {
        return (y * DEPTH + z) * WIDTH + x;
    }

    /**
     * The blueprint character at a point of the shape, or {@link #SOFT} for the paved interior floor; a space outside
     * the box.
     */
    public static char cell(int x, int y, int z) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) return ANY;
        return GRID[index(x, y, z)];
    }

    /** The blueprint cell of a point of the shape: {across, layer from the top, row from the front}. */
    public static int[] cellOf(int x, int y, int z) {
        return new int[] { x, HEIGHT - 1 - y, z };
    }

    /** Whether the step with the given number within its stair (0 = lowest) is a half-slab tread. */
    public static boolean tread(int step) {
        return step % 2 == 0;
    }

    /**
     * The steps of both spiral stairs, {x, y, z}: the left stair's twelve in walking order (lowest first), then the
     * right stair's. Even steps are treads (fitting TREAD, a lower half slab), odd steps full panels; a player
     * standing on step {@code i} stands at {@code 1.5 + 0.5 * i}, so the last one is level with the gallery deck.
     */
    public static List<int[]> stairSteps() {
        List<int[]> out = new ArrayList<>();
        for (int[] s : STEP_CELLS) out.add(s.clone());
        return out;
    }

    /** Whether a cell is a step of a spiral stair (a tread or a full stair block). */
    public static boolean isStair(int x, int y, int z) {
        return stepAt(x, y, z) >= 0;
    }

    /** Every cell of the paved interior floor at y0, {x, z} (824 of them). */
    public static List<int[]> softFloor() {
        List<int[]> out = new ArrayList<>();
        for (int z = 0; z < DEPTH; z++) for (int x = 0; x < WIDTH; x++) {
            if (cell(x, 0, z) == SOFT) out.add(new int[] { x, z });
        }
        return out;
    }

    /**
     * Blocks outside the checked box, {x, y, z, part code}: the entrance canopy, a 7 x 2 slab of frame RING at y5 in
     * front of the doors (x 10..16, z -1 and -2).
     */
    public static List<int[]> decor() {
        List<int[]> out = new ArrayList<>();
        for (int z = -1; z >= -2; z--) for (int x = 10; x <= 16; x++) {
            out.add(new int[] { x, 5, z, Parts.frame(Parts.FR_RING) });
        }
        return out;
    }

    /** The lecterns, {x, y, z}: every console stand other than the desk and the stand under the controller. */
    public static List<int[]> lecterns() {
        List<int[]> out = new ArrayList<>();
        for (int y = 0; y < HEIGHT; y++) for (int z = 0; z < DEPTH; z++) for (int x = 0; x < WIDTH; x++) {
            if (cell(x, y, z) != CONSOLE) continue;
            if (x == DESK[0] && y == DESK[1] && z == DESK[2]) continue;
            if (x == CTRL_X && y == CTRL_Y - 1 && z == CTRL_Z) continue;
            out.add(new int[] { x, y, z });
        }
        return out;
    }

    /** The pedestals at the range ends and beside the desk, {x, y, z}. */
    public static List<int[]> pedestals() {
        List<int[]> out = new ArrayList<>();
        for (int y = 0; y < HEIGHT; y++) for (int z = 0; z < DEPTH; z++) for (int x = 0; x < WIDTH; x++) {
            if (cell(x, y, z) == PEDESTAL) out.add(new int[] { x, y, z });
        }
        return out;
    }

    /**
     * Whether a point in local coordinates (blocks, fractional) is inside the enclosed interior: the hall over the
     * floor and under the flat roof (x 1..25, z 1..33, y 1..11), the clerestory over the void (x 10..16, y 12..13)
     * and the space under the ridge (x 12..14, y 14). Standing on the flat roof or in a door opening is outside.
     */
    public static boolean inside(double x, double y, double z) {
        if (z < 1 || z >= DEPTH - 1 || y < 1) return false;
        if (y < ROOF_Y) return x >= 1 && x < WIDTH - 1;
        if (y < STEP_BACK_Y) return x >= VOID_X0 + 1 && x < VOID_X1;
        if (y < RIDGE_Y) return x >= MID - 1 && x < MID + 2;
        return false;
    }

    /**
     * The part a blueprint character stands for: a {@link Parts} code, {@link Parts#AIR} for a door cell that must be
     * air, {@link Parts#LIBRARY_CORE} for the controller, a plain deck for the soft floor, and -1 for "anything".
     *
     * @throws IllegalArgumentException for a character the Archive does not use
     */
    public static int partOf(char ch) {
        switch (ch) {
            case FOUNDATION:
                return Parts.frame(Parts.FR_FOUNDATION);
            case AIR:
                return Parts.AIR;
            case CONTROLLER:
                return Parts.LIBRARY_CORE;
            case CONSOLE:
                return Parts.frame(Parts.FR_CONSOLE);
            case BODY:
                return Parts.frame(Parts.FR_SHELF);
            case POST:
                return Parts.fitting(Parts.F_POST);
            case PLINTH:
                return Parts.fitting(Parts.F_PLINTH);
            case CROWN:
                return Parts.fitting(Parts.F_CROWN);
            case CORNICE:
                return Parts.deck(Parts.D_CORNICE);
            case PANEL:
                return Parts.deck(Parts.D_PANEL);
            case PILASTER:
                return Parts.deck(Parts.D_PANEL_LIT);
            case COURSE:
                return Parts.deck(Parts.D_PANEL_DARK);
            case GLAZE:
                return Parts.fitting(Parts.F_GLAZE);
            case SLAB:
            case SOFT:
                return Parts.deck(Parts.D_DECK);
            case EDGE:
                return Parts.deck(Parts.D_LIT);
            case RAIL:
                return Parts.fitting(Parts.F_RAIL);
            case PEDESTAL:
                return Parts.fitting(Parts.F_PEDESTAL);
            case TREAD:
                return Parts.fitting(Parts.F_TREAD);
            case ANY:
                return -1;
            default:
                throw new IllegalArgumentException("not an Archive character: '" + ch + "'");
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // generation (a port of spec/gen/archive.py)

    private static int[][] steps() {
        int[][] out = new int[STAIRS.length * STEPS][];
        for (int s = 0; s < STAIRS.length; s++) {
            int cx = STAIRS[s][0], cz = STAIRS[s][1], mirror = cx > MID ? -1 : 1;
            for (int i = 0; i < STEPS; i++) {
                int[] d = RING[i % RING.length];
                // the walking surface in half blocks: 3 (1.5) for the first step, rising by one half each step
                int halves = i + 3;
                int y = halves % 2 == 0 ? halves / 2 - 1 : halves / 2;
                out[s * STEPS + i] = new int[] { cx + d[0] * mirror, y, cz + d[1] };
            }
        }
        return out;
    }

    /** The step number within its stair (0..11) of a stair cell, or -1. */
    private static int stepAt(int x, int y, int z) {
        for (int k = 0; k < STEP_CELLS.length; k++) {
            int[] s = STEP_CELLS[k];
            if (s[0] == x && s[1] == y && s[2] == z) return k % STEPS;
        }
        return -1;
    }

    private static char[] grid() {
        char[] out = new char[WIDTH * HEIGHT * DEPTH];
        for (int y = 0; y < HEIGHT; y++) for (int z = 0; z < DEPTH; z++) for (int x = 0; x < WIDTH; x++) {
            out[index(x, y, z)] = generate(x, y, z);
        }
        return out;
    }

    private static String[][] layers() {
        String[][] out = new String[HEIGHT][DEPTH];
        for (int y = 0; y < HEIGHT; y++) for (int z = 0; z < DEPTH; z++) {
            StringBuilder row = new StringBuilder(WIDTH);
            for (int x = 0; x < WIDTH; x++) {
                char ch = cell(x, y, z);
                row.append(ch == SOFT ? ANY : ch);
            }
            out[HEIGHT - 1 - y][z] = row.toString();
        }
        return out;
    }

    static boolean inWell(int x, int z) {
        for (int[] s : STAIRS) if (Math.abs(x - s[0]) <= 1 && Math.abs(z - s[1]) <= 1) return true;
        return false;
    }

    private static boolean inRange(int z) {
        for (int[] r : RANGES) if (z == r[0] || z == r[1]) return true;
        return false;
    }

    /** A cell of a shelf run whose posts stand every fourth cell from {@code a0}, its plinth at {@code lo}. */
    private static char unitPart(int y, int lo, int v, int a0) {
        if (Math.floorMod(v - a0, 4) == 0) return POST;
        if (y == lo) return PLINTH;
        if (y == lo + 4) return CROWN;
        return BODY;
    }

    private static char generate(int x, int y, int z) {
        boolean ex = x == 0 || x == WIDTH - 1, ez = z == 0 || z == DEPTH - 1;
        if (y == 0) return ex || ez || (x == MID && z == 1) ? FOUNDATION : SOFT;
        int step = stepAt(x, y, z);
        if (step >= 0) return tread(step) ? TREAD : PANEL;
        for (int[] s : STAIRS) if (x == s[0] && z == s[1] && y <= DECK_Y) return POST;
        if (ex || ez) return shell(x, y, z, ex, ez);
        if (y == DECK_Y) return gallery(x, z);
        if (y == ROOF_Y) {
            if (x == VOID_X0 || x == VOID_X1) return PANEL;
            return x > VOID_X0 && x < VOID_X1 ? ANY : PANEL;
        }
        if (y == CLERESTORY_Y) {
            if (x == VOID_X0 || x == VOID_X1) return z % 5 == 0 ? PILASTER : GLAZE;
            return ANY;
        }
        if (y == STEP_BACK_Y) {
            if (x >= VOID_X0 && x <= VOID_X0 + 2 || x >= VOID_X1 - 2 && x <= VOID_X1) {
                return z % 5 == 0 ? PILASTER : PANEL;
            }
            return ANY;
        }
        if (y == RIDGE_Y) {
            if (x >= MID - 1 && x <= MID + 1) {
                if (x == MID) return z % 5 == 0 ? PILASTER : GLAZE;
                return PANEL;
            }
            return ANY;
        }
        if (y <= 5) return ground(x, y, z);
        return upper(x, y, z);
    }

    /** The outer walls, the cornice band, the front doors with the trumeau, and the gables of the clerestory roof. */
    private static char shell(int x, int y, int z, boolean ex, boolean ez) {
        if (y >= CLERESTORY_Y) {
            // the front and back gables close the raised roof over the void
            if (ez && x >= VOID_X0 && x <= VOID_X1 && (y <= STEP_BACK_Y || x >= MID - 1 && x <= MID + 1)) return PANEL;
            return ANY;
        }
        if (y == ROOF_Y) return CORNICE;
        if (z == 0 && x >= 10 && x <= 16 && y <= 4) {
            // the trumeau between the two doors: console stand, controller, two posts
            if (x == MID) return y == 1 ? CONSOLE : y == 2 ? CONTROLLER : POST;
            return AIR;
        }
        if (z == 0 && x >= VOID_X0 && x <= VOID_X1 && y == 5) return CORNICE;
        if (ex && ez) return PILASTER;
        if (ex && z % 5 == 0) return PILASTER;
        if (ez && x % 4 == 1 && !(z == 0 && x >= VOID_X0 && x <= VOID_X1)) return PILASTER;
        if (ex && z >= 7 && z <= 24 && z % 5 == 3 && (y >= 2 && y <= 4 || y >= 8 && y <= 10)) return GLAZE;
        if (y == 1) return COURSE;
        return PANEL;
    }

    /** The gallery deck at y6: open over the void and the stair wells, a lit edge round the void. */
    private static char gallery(int x, int z) {
        if (x >= VOID_X0 && x <= VOID_X1 && z >= VOID_Z0 && z <= VOID_Z1) return ANY;
        if (inWell(x, z)) {
            // a landing beside the last step; the generator also decked z1, which blocked the sixth step's rise
            return (x == 1 || x == WIDTH - 2) && z == 2 ? SLAB : ANY;
        }
        boolean edgeX = (x == VOID_X0 - 1 || x == VOID_X1 + 1) && z >= VOID_Z0 - 1 && z <= VOID_Z1 + 1;
        boolean edgeZ = (z == VOID_Z0 - 1 || z == VOID_Z1 + 1) && x >= VOID_X0 - 1 && x <= VOID_X1 + 1;
        return edgeX || edgeZ ? EDGE : SLAB;
    }

    /** The ground storey, y1..y5: ranges, front, side and back wall units, lecterns, pedestals and the vestibule. */
    private static char ground(int x, int y, int z) {
        if (inRange(z)) {
            if (x >= 1 && x <= 9) return unitPart(y, 1, x, 1);
            if (x >= 17 && x <= 25) return unitPart(y, 1, x, 17);
        }
        if (z == 1 && (x >= 4 && x <= 8 || x >= 18 && x <= 22)) return unitPart(y, 1, x, x <= 8 ? 4 : 18);
        if (z == DEPTH - 2 && (x >= 1 && x <= 9 || x >= 17 && x <= 25)) return unitPart(y, 1, x, x <= 9 ? 1 : 17);
        if ((x == 1 || x == WIDTH - 2) && z >= 27 && z <= 31) return unitPart(y, 1, z, 27);
        if (y == 1 && x == DESK[0] && z == DESK[2]) return CONSOLE;
        if (y == 1 && (x == 11 || x == 15) && z == DESK[2]) return PEDESTAL;
        if (y == 1 && (x == 1 || x == WIDTH - 2) && (z == 8 || z == 13 || z == 18 || z == 23)) return CONSOLE;
        if (y == 1 && (x == 10 || x == 16) && inRange(z)) return PEDESTAL;
        if (y <= 4 && x >= 10 && x <= 16 && z == 1 && x != MID) return AIR;
        return ANY;
    }

    /** The upper storey, y7..y11: wall-side ranges, the front and back wall runs and the rails round the void. */
    private static char upper(int x, int y, int z) {
        if (inWell(x, z)) return ANY;
        if (inRange(z) && (x >= 1 && x <= 5 || x >= 21 && x <= 25)) return unitPart(y, 7, x, x <= 5 ? 1 : 21);
        if (z == 1 && x >= 5 && x <= 21) return unitPart(y, 7, x, 5);
        if (z == DEPTH - 2 && x >= 1 && x <= 25) return unitPart(y, 7, x, 1);
        if (y == 7) {
            boolean alongZ = (x == VOID_X0 - 1 || x == VOID_X1 + 1) && z >= VOID_Z0 && z <= VOID_Z1;
            boolean alongX = (z == VOID_Z0 - 1 || z == VOID_Z1 + 1) && x >= VOID_X0 && x <= VOID_X1;
            if (alongZ || alongX) return RAIL;
        }
        return ANY;
    }
}
