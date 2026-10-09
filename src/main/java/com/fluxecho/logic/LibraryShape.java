package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Echo Library (blueprint 3.7): a hall to walk into, standing on a 13 x 13 module foundation. Its four walls are
 * echo shelves five high, each shelf holding one sample as a book; two doors in the front flank the controller, which
 * sits in the wall between them at eye height. A reading desk (a console stand) stands in the middle. Above the hall a
 * ring of ceiling leaves a 5 x 5 opening into the attic, where the library's depths open upwards; a roof closes it.
 * <p>
 * Coordinates here: {@code x} across (0..12), {@code z} from the front (0..12), {@code y} up from the foundation
 * (0..8).
 * The building takes x and z 1..11; the hall inside, 2..10.
 */
public final class LibraryShape {

    /** Module foundation, frame pillar, echo shelf, frame base, console stand (the reading desk). */
    public static final char FOUNDATION = 'F', PILLAR = 'P', SHELF = 'H', BASE = 'B', CONSOLE = 'K';

    public static final int SIZE = 13, HEIGHT = 9;
    /** The hall's floor level (on the foundation), its top layer, the ceiling, the attic, the roof. */
    public static final int HALL_LOW = 1, HALL_HIGH = 5, CEILING = 6, ATTIC = 7, ROOF = 8;
    /** The controller: across, up, from the front. */
    public static final int CTRL_X = 6, CTRL_Y = 2, CTRL_Z = 1;
    /** The opening into the attic: x and z from {@code OPEN_LOW} to {@code OPEN_HIGH}. */
    public static final int OPEN_LOW = 4, OPEN_HIGH = 8;

    public static final Blueprint BLUEPRINT = new Blueprint(build());

    /** The shelves that hold books, in the order of their sample slots. */
    public static final List<Shelf> SHELVES = Collections.unmodifiableList(shelves());

    private LibraryShape() {}

    /** A shelf of the hall: where it is, and which way its face into the hall looks (one of x or z is 0). */
    public static final class Shelf {

        public final int x, y, z, inX, inZ;

        Shelf(int x, int y, int z, int inX, int inZ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.inX = inX;
            this.inZ = inZ;
        }
    }

    private static String[][] build() {
        String[][] layers = new String[HEIGHT][SIZE];
        for (int y = 0; y < HEIGHT; y++) {
            int layer = HEIGHT - 1 - y;
            for (int z = 0; z < SIZE; z++) {
                StringBuilder row = new StringBuilder();
                for (int x = 0; x < SIZE; x++) row.append(cell(x, y, z));
                layers[layer][z] = row.toString();
            }
        }
        return layers;
    }

    /** Whether {@code (x, z)} is in a door of the front wall (doors are three high). */
    static boolean door(int x, int y, int z) {
        return z == 1 && y >= HALL_LOW && y <= HALL_LOW + 2 && (x == 3 || x == 4 || x == 8 || x == 9);
    }

    static boolean opening(int x, int z) {
        return x >= OPEN_LOW && x <= OPEN_HIGH && z >= OPEN_LOW && z <= OPEN_HIGH;
    }

    public static char cell(int x, int y, int z) {
        if (y == 0) return FOUNDATION;
        if (x < 1 || x > 11 || z < 1 || z > 11) return ' ';
        boolean edgeX = x == 1 || x == 11, edgeZ = z == 1 || z == 11, wall = edgeX || edgeZ;
        if (edgeX && edgeZ) return PILLAR;
        if (y <= HALL_HIGH) {
            if (wall) {
                if (x == CTRL_X && y == CTRL_Y && z == CTRL_Z) return '~';
                if (door(x, y, z)) return ' ';
                return SHELF;
            }
            return x == 6 && z == 6 && y == HALL_LOW ? CONSOLE : ' ';
        }
        if (y == CEILING) return !wall && opening(x, z) ? '-' : BASE;
        if (y == ATTIC) return wall ? BASE : opening(x, z) ? '-' : ' ';
        return wall ? PILLAR : BASE;
    }

    private static List<Shelf> shelves() {
        List<Shelf> out = new ArrayList<>();
        for (int y = HALL_LOW; y <= HALL_HIGH; y++) for (int z = 0; z < SIZE; z++) for (int x = 0; x < SIZE; x++) {
            if (cell(x, y, z) != SHELF) continue;
            int inX = x == 1 ? 1 : x == 11 ? -1 : 0, inZ = z == 1 ? 1 : z == 11 ? -1 : 0;
            out.add(new Shelf(x, y, z, inX, inZ));
        }
        return out;
    }

    /** The blueprint cell of a point of the shape: {across, layer from the top, row from the front}. */
    public static int[] cellOf(int x, int y, int z) {
        return new int[] { x, HEIGHT - 1 - y, z };
    }
}
