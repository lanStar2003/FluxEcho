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
            // from phase II the lift pad takes the place of the lit hub in the middle of the base (0.12.0)
            int pads = p >= NexusShape.LIFT_FROM ? 1 : 0;
            assertEquals(pads, s.count(NexusShape.LIFT));
            assertEquals(97 - pads, s.count(NexusShape.BASE) + s.count(NexusShape.LIT));
            assertEquals(pads == 1 ? NexusShape.LIFT : NexusShape.LIT, at(s, 0, 0, 0));
            if (p == 1) continue;
            // the phase below stands unchanged inside it, but for the pad that replaced the hub at phase II
            Blueprint below = NexusShape.phase(p - 1);
            for (int y = 0; y < below.height(); y++) for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
                if (p == NexusShape.LIFT_FROM && y == 0 && dx == 0 && dz == 0) continue;
                assertEquals(at(below, dx, y, dz), at(s, dx, y, dz), "phase " + p + " at " + dx + "," + y + "," + dz);
            }
        }
        assertEquals(
            26,
            NexusShape.phase(5)
                .height(),
            "five rings: the finished nexus");
    }

    /**
     * The growth job's cells (0.12.0): the stage a phase adds to the phase below, bottom-up, and at phase II the lift
     * pad first; together with the phase below they are exactly the phase's blueprint.
     */
    @Test
    void theStageCellsAreWhatAPhaseAdds() {
        assertTrue(
            NexusShape.stageCells(1)
                .isEmpty(),
            "phase I is the establish job's");
        assertTrue(
            NexusShape.stageCells(NexusShape.PHASES + 1)
                .isEmpty());
        for (int p = 2; p <= NexusShape.PHASES; p++) {
            Blueprint s = NexusShape.phase(p), below = NexusShape.phase(p - 1);
            java.util.List<int[]> cells = NexusShape.stageCells(p);
            int added = 0;
            for (int y = 0; y < s.height(); y++) for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
                char now = at(s, dx, y, dz), was = y < below.height() ? at(below, dx, y, dz) : ' ';
                if (now != was) added++;
            }
            assertEquals(added, cells.size(), "phase " + p);
            int lastY = -1;
            for (int[] c : cells) {
                assertEquals((char) c[3], at(s, c[1], c[0], c[2]), "phase " + p);
                assertTrue(c[0] >= lastY, "bottom-up");
                lastY = c[0];
                if (c[3] == NexusShape.LIFT) assertEquals(NexusShape.LIFT_FROM, p);
            }
            if (p == NexusShape.LIFT_FROM) assertEquals(NexusShape.LIFT, cells.get(0)[3], "the pad first");
            assertEquals(p, NexusShape.phaseOf(NexusShape.shapeName(p)));
        }
        assertEquals(0, NexusShape.phaseOf("main"));
        assertEquals(0, NexusShape.phaseOf("phase9"));
        assertEquals(0, NexusShape.phaseOf(null));
        assertEquals("phase1", NexusShape.shapeName(0));
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

    @Test
    void theReachOfEachPhase() {
        // the cube a nexus covers round its controller: 12 for phase I (as in 0.11.0) and for phase II, which a phase-I
        // nexus may form as next
        assertEquals(12, NexusShape.reach(1));
        assertEquals(12, NexusShape.reach(2));
        assertEquals(17, NexusShape.reach(3));
        assertEquals(22, NexusShape.reach(4));
        assertEquals(27, NexusShape.reach(5));
        assertEquals(12, NexusShape.reach(0), "clamped");
        assertEquals(27, NexusShape.reach(9), "clamped");
        for (int p = 1; p <= NexusShape.PHASES; p++) {
            Blueprint b = NexusShape.phase(p);
            assertEquals(Math.max(b.width(), Math.max(b.depth(), b.height())) + 1, NexusShape.reach(p));
        }
    }

    /**
     * A phase holds the one below it whole (0.15.0): every cell of phase II's blueprint is the same in phase III, and
     * so on up, and phase I differs from phase II only in the lift pad. So a phase-II nexus with stage 3 standing on it
     * still checks out as phase II (stage 3 lies over its blueprint), which is why the nexus tries its highest
     * researched phase first ({@link NexusShape#tryOrder}).
     */
    @Test
    void aPhaseHoldsTheOneBelowWhole() {
        for (int p = 1; p < NexusShape.PHASES; p++) {
            Blueprint low = NexusShape.phase(p), high = NexusShape.phase(p + 1);
            int differ = 0;
            for (int y = 0; y < low.height(); y++) for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
                char was = at(low, dx, y, dz);
                if (was == ' ') continue;
                if (at(high, dx, y, dz) != was) {
                    differ++;
                    assertEquals(1, p, "only phase I's lit middle gives way, to the lift pad");
                    assertEquals(NexusShape.LIFT, at(high, dx, y, dz));
                    assertEquals(0, y + dx * dx + dz * dz);
                }
            }
            assertEquals(p == 1 ? 1 : 0, differ, "phase " + p + " in phase " + (p + 1));
        }
    }

    /**
     * The phases a nexus tries to stand as, highest first (0.15.0): from the highest researched down to the one it grew
     * to, never under it, never past the last phase; with nothing higher researched only the one it grew to.
     */
    @Test
    void theNexusTriesItsHighestResearchedPhaseFirst() {
        assertArrayEquals(new int[] { 3, 2 }, NexusShape.tryOrder(2, 3));
        assertArrayEquals(new int[] { 2 }, NexusShape.tryOrder(2, 2));
        assertArrayEquals(new int[] { 2 }, NexusShape.tryOrder(2, 1), "never under the phase it grew to");
        assertArrayEquals(new int[] { 1 }, NexusShape.tryOrder(1, 1));
        assertArrayEquals(new int[] { 3, 2, 1 }, NexusShape.tryOrder(1, 3));
        assertArrayEquals(new int[] { 5, 4 }, NexusShape.tryOrder(4, 9), "never past the last phase");
        assertArrayEquals(new int[] { 1 }, NexusShape.tryOrder(0, 0), "clamped");
        // what a nexus may stand as with every phase's research is held to what this version reaches
        int all = ResearchTree.researchedPhase(id -> true);
        assertEquals(ResearchTree.PHASE_NOW, all);
        assertArrayEquals(new int[] { 3, 2 }, NexusShape.tryOrder(2, all));
        assertArrayEquals(new int[] { 3 }, NexusShape.tryOrder(3, all));
        // with only phase II's research a phase-II nexus tries phase II alone
        assertArrayEquals(new int[] { 2 }, NexusShape.tryOrder(2, ResearchTree.researchedPhase("phase_2"::equals)));
    }
}
