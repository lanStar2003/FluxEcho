package com.fluxecho.logic;

/**
 * The Flux Nexus, phase I (blueprint 3.3): an 11 x 11 octagonal base whose eight spokes carry the flux to the middle,
 * four pillars holding a ring of sixteen segments above, and a column of conduits under the crystal seat in the
 * middle. The controller sits in the rim at the front.
 * <p>
 * Once formed, the pillars, conduits, seat and ring give way to what they stood for: four floating struts, a column of
 * light, a floating crystal core and a turning ring ({@link #dissolves}); the base stays.
 */
public final class NexusShape {

    /** Base, lit base (the spokes), pillar, conduit, ring segment, crystal seat. */
    public static final char BASE = 'B', LIT = 'G', PILLAR = 'P', CONDUIT = 'C', RING = 'R', SEAT = 'S';

    public static final int RADIUS = 5, HEIGHT = 6;

    public static final Blueprint PHASE_1 = new Blueprint(build());

    private NexusShape() {}

    /** Whether the cell is in the octagonal base. */
    static boolean inBase(int dx, int dz) {
        return Math.abs(dx) <= RADIUS && Math.abs(dz) <= RADIUS && Math.abs(dx) + Math.abs(dz) <= 7;
    }

    /** Whether the cell is in the ring above (centred 2.5 to 3.5 blocks out). */
    static boolean inRing(int dx, int dz) {
        double d = Math.sqrt(dx * dx + dz * dz);
        return d >= 2.5 && d < 3.5;
    }

    private static String[][] build() {
        int size = 2 * RADIUS + 1;
        String[][] layers = new String[HEIGHT][size];
        for (int y = 0; y < HEIGHT; y++) {
            int layer = HEIGHT - 1 - y; // from the top
            for (int c = 0; c < size; c++) {
                StringBuilder row = new StringBuilder();
                for (int a = 0; a < size; a++) {
                    int dx = a - RADIUS, dz = c - RADIUS;
                    row.append(cell(y, dx, dz));
                }
                layers[layer][c] = row.toString();
            }
        }
        return layers;
    }

    private static char cell(int y, int dx, int dz) {
        boolean pillar = Math.abs(dx) == 2 && Math.abs(dz) == 2;
        if (y == 0) {
            if (!inBase(dx, dz)) return ' ';
            if (dx == 0 && dz == -RADIUS) return '~';
            return dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz) ? LIT : BASE;
        }
        if (y < HEIGHT - 1) {
            if (pillar) return PILLAR;
            if (dx == 0 && dz == 0) return y == HEIGHT - 2 ? SEAT : CONDUIT;
            return ' ';
        }
        return inRing(dx, dz) ? RING : ' ';
    }

    /** Whether a cell's block gives way to its rendered form when the nexus forms (and lets players through). */
    public static boolean dissolves(char ch) {
        return ch == PILLAR || ch == CONDUIT || ch == RING || ch == SEAT;
    }
}
