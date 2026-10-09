package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LibraryShapeTest {

    private final Blueprint b = LibraryShape.BLUEPRINT;

    @Test
    void sizeAndController() {
        assertEquals(9, b.width());
        assertEquals(9, b.depth());
        assertEquals(8, b.height());
        assertEquals(4, b.ctrlA);
        assertEquals(1, b.ctrlC, "the cube's front face, one row behind the foundation's edge");
        assertEquals(3, b.ctrlB, "half way up the cube");
        assertTrue(b.symmetric());
        assertTrue(b.width() <= 21 && b.depth() <= 21, "fits an inner ring slot (21 x 21)");
    }

    @Test
    void parts() {
        assertEquals(68, b.count(LibraryShape.PILLAR), "the cube's twelve edges");
        assertEquals(124, b.count(LibraryShape.SHELF), "five faces of 25, the controller left out");
        assertEquals(81 + 25, b.count(LibraryShape.FOUNDATION), "the foundation and the cube's floor");
    }

    @Test
    void theInsideIsLeftOpen() {
        for (int layer = 1; layer <= 5; layer++) assertEquals(' ', b.at(4, layer, 4));
    }

    @Test
    void centreOfTheFoundation() {
        // the foundation's centre is 3 blocks behind the controller and 4 down
        assertArrayEquals(new int[] { 0, 66, 3 }, b.world(4, 7, 4, 0, 70, 0, 0, -1));
    }
}
