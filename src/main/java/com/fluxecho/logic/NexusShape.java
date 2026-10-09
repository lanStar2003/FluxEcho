package com.fluxecho.logic;

/**
 * The Flux Nexus (blueprint 3.3): an 11 x 11 octagonal base whose eight spokes carry the flux to the middle, and on it
 * one stage per phase, five blocks tall each: four ribs leaning in from the base's diagonals to hold a ring of sixteen
 * segments, and a column of conduits under a crystal seat in the middle. Phase I is the base and one stage, phase V
 * five stages: the column of five floating rings of the finished nexus.
 * <p>
 * The controller sits on a console stand at the front of the base, at eye height for someone standing there. Once
 * formed, the ribs, conduits, seats and rings give way to what they stood for: curved ribs, a column of light, floating
 * crystal cores and turning rings ({@link #dissolves}); the base and the console stay.
 */
public final class NexusShape {

    /** Base, lit base (the spokes), rib, conduit, ring segment, crystal seat, console stand. */
    public static final char BASE = 'B', LIT = 'G', PILLAR = 'P', CONDUIT = 'C', RING = 'R', SEAT = 'S', CONSOLE = 'K';

    public static final int RADIUS = 5, STAGE = 5, PHASES = 5;
    /** The controller's height above the base. */
    public static final int CONTROLLER_UP = 2;

    private static final Blueprint[] SHAPES = new Blueprint[PHASES];

    static {
        for (int p = 1; p <= PHASES; p++) SHAPES[p - 1] = new Blueprint(build(p));
    }

    /** What the nexus checks for today. */
    public static final Blueprint PHASE_1 = phase(1);

    private NexusShape() {}

    /** The nexus of a phase, 1 to {@link #PHASES}. */
    public static Blueprint phase(int p) {
        return SHAPES[Math.max(1, Math.min(PHASES, p)) - 1];
    }

    /** Blocks from the bottom of the base to the top of the highest ring. */
    public static int height(int phase) {
        return 1 + STAGE * phase;
    }

    /** Whether the cell is in the octagonal base. */
    static boolean inBase(int dx, int dz) {
        return Math.abs(dx) <= RADIUS && Math.abs(dz) <= RADIUS && Math.abs(dx) + Math.abs(dz) <= 7;
    }

    /** Whether the cell is in a ring (centred 2.5 to 3.5 blocks out). */
    static boolean inRing(int dx, int dz) {
        double d = Math.sqrt(dx * dx + dz * dz);
        return d >= 2.5 && d < 3.5;
    }

    private static String[][] build(int phase) {
        int size = 2 * RADIUS + 1, height = height(phase);
        String[][] layers = new String[height][size];
        for (int y = 0; y < height; y++) {
            int layer = height - 1 - y; // from the top
            for (int c = 0; c < size; c++) {
                StringBuilder row = new StringBuilder();
                for (int a = 0; a < size; a++) row.append(cell(y, a - RADIUS, c - RADIUS));
                layers[layer][c] = row.toString();
            }
        }
        return layers;
    }

    /**
     * The part at height {@code y} above the base's bottom, {@code (dx, dz)} from its middle ({@code dz < 0} front).
     */
    static char cell(int y, int dx, int dz) {
        if (y == 0) {
            if (!inBase(dx, dz)) return ' ';
            return dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz) ? LIT : BASE;
        }
        if (dx == 0 && dz == -RADIUS) {
            if (y == CONTROLLER_UP - 1) return CONSOLE;
            if (y == CONTROLLER_UP) return '~';
        }
        int s = (y - 1) % STAGE; // the height within its stage
        if (s <= 1 && Math.abs(dx) == 3 && Math.abs(dz) == 3) return PILLAR;
        if ((s == 2 || s == 3) && Math.abs(dx) == 2 && Math.abs(dz) == 2) return PILLAR;
        if (dx == 0 && dz == 0) {
            if (s <= 2) return CONDUIT;
            if (s == 3) return SEAT;
        }
        return s == 4 && inRing(dx, dz) ? RING : ' ';
    }

    /** Whether a cell's block gives way to its rendered form when the nexus forms (and lets players through). */
    public static boolean dissolves(char ch) {
        return ch == PILLAR || ch == CONDUIT || ch == RING || ch == SEAT;
    }
}
