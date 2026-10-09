package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class LibraryShapeTest {

    private final Blueprint b = LibraryShape.BLUEPRINT;

    private static char at(int x, int y, int z) {
        int[] c = LibraryShape.cellOf(x, y, z);
        return LibraryShape.BLUEPRINT.at(c[0], c[1], c[2]);
    }

    @Test
    void sizeAndController() {
        assertEquals(13, b.width());
        assertEquals(13, b.depth());
        assertEquals(9, b.height());
        assertEquals(6, b.ctrlA);
        assertEquals(1, b.ctrlC, "in the front wall, one row behind the foundation's edge");
        assertEquals(6, b.ctrlB, "two above the foundation: eye height");
        assertTrue(b.symmetric());
        assertTrue(b.width() <= 21 && b.depth() <= 21, "fits an inner ring slot (21 x 21)");
    }

    @Test
    void parts() {
        assertEquals(169, b.count(LibraryShape.FOUNDATION));
        assertEquals(167, b.count(LibraryShape.SHELF), "four walls five high, less the doors and the controller");
        assertEquals(68, b.count(LibraryShape.PILLAR), "the corners and the roof's edge");
        assertEquals(209, b.count(LibraryShape.BASE), "ceiling, attic walls and roof");
        assertEquals(1, b.count(LibraryShape.CONSOLE));
        assertEquals(50, b.count('-'), "the opening and the attic over it stay empty");
    }

    @Test
    void doorsFlankTheController() {
        for (int y = 1; y <= 3; y++) for (int x : new int[] { 3, 4, 8, 9 }) assertEquals(' ', at(x, y, 1));
        assertEquals('~', at(6, 2, 1));
        assertEquals(LibraryShape.SHELF, at(6, 1, 1), "a shelf under the controller");
        assertEquals(LibraryShape.SHELF, at(3, 4, 1), "shelves over the doors");
    }

    @Test
    void theHallIsOpenAndTheDeskInTheMiddle() {
        for (int y = 1; y <= 5; y++) for (int x = 2; x <= 10; x++) for (int z = 2; z <= 10; z++) {
            char ch = at(x, y, z);
            if (x == 6 && z == 6 && y == 1) assertEquals(LibraryShape.CONSOLE, ch);
            else assertEquals(' ', ch, "the hall at " + x + "," + y + "," + z);
        }
    }

    @Test
    void theShelvesFaceIntoTheHall() {
        assertEquals(167, LibraryShape.SHELVES.size());
        Set<String> seen = new HashSet<>();
        for (LibraryShape.Shelf s : LibraryShape.SHELVES) {
            assertEquals(LibraryShape.SHELF, at(s.x, s.y, s.z));
            assertEquals(1, Math.abs(s.inX) + Math.abs(s.inZ), "one face into the hall");
            int ix = s.x + s.inX, iz = s.z + s.inZ;
            assertTrue(ix >= 2 && ix <= 10 && iz >= 2 && iz <= 10, "the face looks into the hall");
            assertTrue(s.y >= 1 && s.y <= 5);
            assertTrue(seen.add(s.x + "," + s.y + "," + s.z));
        }
    }

    @Test
    void theOpeningIsOverTheMiddle() {
        for (int x = 4; x <= 8; x++) for (int z = 4; z <= 8; z++) {
            assertEquals('-', at(x, 6, z));
            assertEquals('-', at(x, 7, z));
            assertEquals(LibraryShape.BASE, at(x, 8, z), "the roof closes it");
        }
        assertEquals(LibraryShape.BASE, at(3, 6, 6), "ceiling round the opening");
    }

    @Test
    void centreOfTheFoundation() {
        // controller at (0, 70, 0) facing north: the foundation's centre is 5 blocks behind it and 2 down
        assertArrayEquals(new int[] { 0, 68, 5 }, b.world(6, 8, 6, 0, 70, 0, 0, -1));
    }
}
