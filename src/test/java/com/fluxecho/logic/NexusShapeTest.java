package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NexusShapeTest {

    private final Blueprint b = NexusShape.PHASE_1;

    /** The character at a point of a phase's shape: {@code y} up from the base, {@code (dx, dz)} from its middle. */
    private static char at(Blueprint s, int dx, int y, int dz) {
        return s.at(dx + 5, s.height() - 1 - y, dz + 5);
    }

    @Test
    void sizeAndController() {
        assertEquals(11, b.width());
        assertEquals(11, b.depth());
        assertEquals(6, b.height());
        assertEquals(5, b.ctrlA, "in the middle across");
        assertEquals(3, b.ctrlB, "two above the base, the lowest layer");
        assertEquals(0, b.ctrlC, "in the front row");
        assertEquals(5, b.centreBack(), "the centre is five blocks behind the controller");
        assertTrue(b.symmetric(), "mirror-symmetric, so StructureLib's across axis does not matter");
    }

    @Test
    void parts() {
        assertEquals(97, b.count(NexusShape.BASE) + b.count(NexusShape.LIT), "the whole octagon");
        assertEquals(16, b.count(NexusShape.PILLAR));
        assertEquals(3, b.count(NexusShape.CONDUIT));
        assertEquals(1, b.count(NexusShape.SEAT));
        assertEquals(16, b.count(NexusShape.RING));
        assertEquals(1, b.count(NexusShape.CONSOLE));
    }

    @Test
    void theControllerSitsOnAConsoleAtEyeHeight() {
        assertEquals(NexusShape.LIT, at(b, 0, 0, -5), "the front spoke under it");
        assertEquals(NexusShape.CONSOLE, at(b, 0, 1, -5));
        assertEquals('~', at(b, 0, 2, -5));
    }

    @Test
    void ribsLeanInToTheRing() {
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
            assertEquals(NexusShape.LIT, at(b, 3 * sx, 0, 3 * sz), "standing on a lit spoke");
            for (int y = 1; y <= 2; y++) assertEquals(NexusShape.PILLAR, at(b, 3 * sx, y, 3 * sz));
            for (int y = 3; y <= 4; y++) assertEquals(NexusShape.PILLAR, at(b, 2 * sx, y, 2 * sz));
            assertEquals(NexusShape.RING, at(b, 2 * sx, 5, 2 * sz), "the ring rests on the rib's top");
        }
    }

    @Test
    void theCoreColumn() {
        for (int y = 1; y <= 3; y++) assertEquals(NexusShape.CONDUIT, at(b, 0, y, 0));
        assertEquals(NexusShape.SEAT, at(b, 0, 4, 0));
        assertEquals(NexusShape.LIT, at(b, 0, 0, 0));
        assertEquals(' ', at(b, 0, 5, 0), "open above the seat: the core floats there");
    }

    @Test
    void everyPhaseAddsAStage() {
        for (int p = 1; p <= NexusShape.PHASES; p++) {
            Blueprint s = NexusShape.phase(p);
            assertEquals(NexusShape.height(p), s.height());
            assertEquals(5, s.ctrlA);
            assertEquals(0, s.ctrlC);
            assertEquals(s.height() - 1 - NexusShape.CONTROLLER_UP, s.ctrlB, "the controller stays on its console");
            assertTrue(s.symmetric());
            assertEquals(16 * p, s.count(NexusShape.PILLAR));
            assertEquals(16 * p, s.count(NexusShape.RING));
            assertEquals(3 * p, s.count(NexusShape.CONDUIT));
            assertEquals(p, s.count(NexusShape.SEAT));
            assertEquals(97, s.count(NexusShape.BASE) + s.count(NexusShape.LIT));
            if (p == 1) continue;
            // the phase below stands unchanged inside it
            Blueprint below = NexusShape.phase(p - 1);
            for (int y = 0; y < below.height(); y++) for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++)
                assertEquals(at(below, dx, y, dz), at(s, dx, y, dz), "phase " + p + " at " + dx + "," + y + "," + dz);
        }
        assertEquals(
            26,
            NexusShape.phase(5)
                .height(),
            "five rings: the finished nexus");
    }

    @Test
    void whatDissolves() {
        assertTrue(NexusShape.dissolves(NexusShape.PILLAR));
        assertTrue(NexusShape.dissolves(NexusShape.RING));
        assertTrue(NexusShape.dissolves(NexusShape.CONDUIT));
        assertTrue(NexusShape.dissolves(NexusShape.SEAT));
        assertFalse(NexusShape.dissolves(NexusShape.BASE));
        assertFalse(NexusShape.dissolves(NexusShape.LIT));
        assertFalse(NexusShape.dissolves(NexusShape.CONSOLE));
        assertFalse(NexusShape.dissolves('~'));
    }

    @Test
    void worldPositions() {
        int bottom = b.height() - 1;
        // controller at the origin facing north (0, -1): the base's centre is 5 blocks south and 2 down
        assertArrayEquals(new int[] { 0, 62, 5 }, b.world(5, bottom, 5, 0, 64, 0, 0, -1));
        // facing east: 5 blocks west
        assertArrayEquals(new int[] { -5, 62, 0 }, b.world(5, bottom, 5, 0, 64, 0, 1, 0));
        // the seat is four up from the base's centre
        assertArrayEquals(new int[] { 0, 66, 5 }, b.world(5, bottom - 4, 5, 0, 64, 0, 0, -1));
        // every phase puts the base in the same place
        Blueprint top = NexusShape.phase(5);
        assertArrayEquals(
            new int[] { 0, 62, 5 },
            top.world(5, top.height() - 1, 5, 0, 64, 0, 0, -1),
            "the base does not move with the phase");
    }
}
