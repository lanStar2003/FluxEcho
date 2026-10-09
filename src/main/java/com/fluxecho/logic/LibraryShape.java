package com.fluxecho.logic;

/**
 * The Echo Library (blueprint 3.7): a 7 x 7 x 7 cube of glowing echo shelves framed by pillars, standing on a 9 x 9
 * module foundation; its controller is the middle of the front face. Once formed the shelves of its faces give way
 * to the view into the library's depths ({@link #opens}); they stay solid.
 */
public final class LibraryShape {

    /** Module foundation, frame pillar, echo shelf. */
    public static final char FOUNDATION = 'F', PILLAR = 'P', SHELF = 'H';

    public static final int CUBE = 7, FLOOR = 9;

    public static final Blueprint BLUEPRINT = new Blueprint(build());

    private LibraryShape() {}

    private static String[][] build() {
        int height = CUBE + 1;
        String[][] layers = new String[height][FLOOR];
        for (int y = 0; y < height; y++) {
            int layer = height - 1 - y;
            for (int c = 0; c < FLOOR; c++) {
                StringBuilder row = new StringBuilder();
                for (int a = 0; a < FLOOR; a++) row.append(cell(y, a, c));
                layers[layer][c] = row.toString();
            }
        }
        return layers;
    }

    private static char cell(int y, int a, int c) {
        if (y == 0) return FOUNDATION;
        int x = a - 1, z = c - 1, h = y - 1; // in the cube, 0..6
        if (x < 0 || x >= CUBE || z < 0 || z >= CUBE) return ' ';
        int outer = (x == 0 || x == CUBE - 1 ? 1 : 0) + (h == 0 || h == CUBE - 1 ? 1 : 0)
            + (z == 0 || z == CUBE - 1 ? 1 : 0);
        if (outer >= 2) return PILLAR;
        if (outer == 0) return ' ';
        if (h == 0) return FOUNDATION; // the cube's own floor
        if (z == 0 && x == CUBE / 2 && h == CUBE / 2) return '~';
        return SHELF;
    }

    /** Whether a cell's block opens onto the library's depths once formed (it stays solid). */
    public static boolean opens(char ch) {
        return ch == SHELF;
    }
}
