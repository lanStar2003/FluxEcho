package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NexusShapeTest {

    private final Blueprint b = NexusShape.PHASE_1;

    @Test
    void sizeAndController() {
        assertEquals(11, b.width());
        assertEquals(11, b.depth());
        assertEquals(6, b.height());
        assertEquals(5, b.ctrlA, "in the middle across");
        assertEquals(5, b.ctrlB, "on the base, the lowest layer");
        assertEquals(0, b.ctrlC, "in the front row");
        assertEquals(5, b.centreBack(), "the centre is five blocks behind the controller");
        assertTrue(b.symmetric(), "mirror-symmetric, so StructureLib's across axis does not matter");
    }

    @Test
    void parts() {
        assertEquals(96, b.count(NexusShape.BASE) + b.count(NexusShape.LIT), "base without the controller");
        assertEquals(16, b.count(NexusShape.PILLAR));
        assertEquals(3, b.count(NexusShape.CONDUIT));
        assertEquals(1, b.count(NexusShape.SEAT));
        assertEquals(16, b.count(NexusShape.RING));
    }

    @Test
    void pillarsHoldTheRing() {
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
            int a = 5 + 2 * sx, c = 5 + 2 * sz;
            for (int layer = 1; layer <= 4; layer++) assertEquals(NexusShape.PILLAR, b.at(a, layer, c));
            assertEquals(NexusShape.RING, b.at(a, 0, c), "a ring segment on top of each pillar");
            assertEquals(NexusShape.LIT, b.at(a, 5, c), "on a lit spoke");
        }
    }

    @Test
    void theCoreColumn() {
        assertEquals(NexusShape.SEAT, b.at(5, 1, 5));
        for (int layer = 2; layer <= 4; layer++) assertEquals(NexusShape.CONDUIT, b.at(5, layer, 5));
        assertEquals(NexusShape.LIT, b.at(5, 5, 5));
        assertEquals(' ', b.at(5, 0, 5), "open above the seat: the core floats there");
    }

    @Test
    void whatDissolves() {
        assertTrue(NexusShape.dissolves(NexusShape.PILLAR));
        assertTrue(NexusShape.dissolves(NexusShape.RING));
        assertTrue(NexusShape.dissolves(NexusShape.CONDUIT));
        assertTrue(NexusShape.dissolves(NexusShape.SEAT));
        assertFalse(NexusShape.dissolves(NexusShape.BASE));
        assertFalse(NexusShape.dissolves(NexusShape.LIT));
        assertFalse(NexusShape.dissolves('~'));
    }

    @Test
    void worldPositions() {
        // controller at the origin facing north (0, -1): the centre is 5 blocks south
        assertArrayEquals(new int[] { 0, 64, 5 }, b.world(5, 5, 5, 0, 64, 0, 0, -1));
        // facing east: the centre is 5 blocks west
        assertArrayEquals(new int[] { -5, 64, 0 }, b.world(5, 5, 5, 0, 64, 0, 1, 0));
        // the seat is four up from the centre
        assertArrayEquals(new int[] { 0, 68, 5 }, b.world(5, 1, 5, 0, 64, 0, 0, -1));
    }
}
