package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ArchiveShapeTest {

    private final Blueprint b = ArchiveShape.BLUEPRINT;

    private static char at(int x, int y, int z) {
        int[] c = ArchiveShape.cellOf(x, y, z);
        return ArchiveShape.BLUEPRINT.at(c[0], c[1], c[2]);
    }

    @Test
    void sizeAndController() {
        assertEquals(ArchiveShape.WIDTH, b.width());
        assertEquals(ArchiveShape.DEPTH, b.depth());
        assertEquals(ArchiveShape.HEIGHT, b.height());
        assertEquals(27, b.width());
        assertEquals(35, b.depth());
        assertEquals(16, b.height());
        assertEquals(ArchiveShape.CTRL_X, b.ctrlA);
        assertEquals(ArchiveShape.HEIGHT - 1 - ArchiveShape.CTRL_Y, b.ctrlB);
        assertEquals(ArchiveShape.CTRL_Z, b.ctrlC);
        assertEquals('~', ArchiveShape.cell(13, 2, 0));
        assertEquals('K', ArchiveShape.cell(13, 1, 0), "the controller stands on a console stand");
        assertEquals('q', ArchiveShape.cell(13, 3, 0), "the trumeau above it");
        assertEquals('q', ArchiveShape.cell(13, 4, 0));
        assertEquals('n', ArchiveShape.cell(13, 5, 0), "the lintel over the doors");
    }

    @Test
    void symmetric() {
        assertTrue(b.symmetric(), "the two stairs mirror each other");
        for (int y = 0; y < ArchiveShape.HEIGHT; y++) for (int z = 0; z < ArchiveShape.DEPTH; z++) {
            for (int x = 0; x < ArchiveShape.WIDTH; x++) {
                assertEquals(ArchiveShape.cell(x, y, z), ArchiveShape.cell(ArchiveShape.WIDTH - 1 - x, y, z));
            }
        }
    }

    @Test
    void blueprintMatchesCells() {
        for (int y = 0; y < ArchiveShape.HEIGHT; y++) for (int z = 0; z < ArchiveShape.DEPTH; z++) {
            for (int x = 0; x < ArchiveShape.WIDTH; x++) {
                char ch = ArchiveShape.cell(x, y, z);
                assertEquals(ch == '.' ? ' ' : ch, at(x, y, z), "at " + x + "," + y + "," + z);
            }
        }
        assertEquals(' ', ArchiveShape.cell(-1, 0, 0));
        assertEquals(' ', ArchiveShape.cell(0, 16, 0));
        assertArrayEquals(new int[] { 4, 15, 7 }, ArchiveShape.cellOf(4, 0, 7));
    }

    /**
     * The generator's counts after renaming (C split into crowns c and the cornice n; W, X and stair blocks B merged
     * into W), less the two gallery slabs that were taken off the stairs' sixth steps.
     */
    @Test
    void exactCounts() {
        Map<Character, Integer> want = new HashMap<>();
        want.put('F', 121);
        want.put('G', 129);
        want.put('K', 10);
        want.put('L', 351);
        want.put('S', 702);
        want.put('W', 960 + 756 + 12);
        want.put('c', 234);
        want.put('n', 129);
        want.put('d', 514 - 2);
        want.put('e', 72);
        want.put('h', 22);
        want.put('p', 234);
        want.put('q', 644);
        want.put('r', 68);
        want.put('t', 12);
        want.put('w', 86);
        want.put('~', 1);
        Map<Character, Integer> got = new HashMap<>();
        int soft = 0, air = 0, checked = 0;
        for (int y = 0; y < ArchiveShape.HEIGHT; y++) for (int z = 0; z < ArchiveShape.DEPTH; z++) {
            for (int x = 0; x < ArchiveShape.WIDTH; x++) {
                char ch = ArchiveShape.cell(x, y, z);
                if (ch == '.') soft++;
                else if (ch == '-') air++;
                else if (ch != ' ') {
                    checked++;
                    got.merge(ch, 1, Integer::sum);
                }
            }
        }
        assertEquals(want, got);
        assertEquals(5057 - 2, checked);
        assertEquals(48, air, "the doors and the vestibule");
        assertEquals(824, soft);
        assertEquals(48, b.count('-'));
        assertEquals(0, b.count('.'), "the soft floor is 'anything' in the blueprint");
        assertEquals(3 * 78, got.get('c'), "a row of three crowns per unit");
        assertEquals(3 * 78, got.get('p'), "and of three plinths");
    }

    @Test
    void stairsAreMirroredSpirals() {
        List<int[]> steps = ArchiveShape.stairSteps();
        assertEquals(2 * ArchiveShape.STEPS, steps.size());
        for (int i = 0; i < steps.size(); i++) {
            int[] s = steps.get(i);
            int n = i % ArchiveShape.STEPS;
            assertEquals(ArchiveShape.tread(n) ? 't' : 'W', ArchiveShape.cell(s[0], s[1], s[2]));
            assertTrue(ArchiveShape.isStair(s[0], s[1], s[2]));
            if (i < ArchiveShape.STEPS) assertArrayEquals(
                new int[] { ArchiveShape.WIDTH - 1 - s[0], s[1], s[2] },
                steps.get(i + ArchiveShape.STEPS),
                "step " + i + " mirrors");
        }
        assertFalse(ArchiveShape.isStair(0, 1, 2), "a wall is not a step");
        assertEquals('q', ArchiveShape.cell(2, 6, 2), "the stair post reaches the deck");
        assertEquals(' ', ArchiveShape.cell(2, 7, 2), "and stops there");
    }

    @Test
    void doorsAndVestibule() {
        for (int y = 1; y <= 4; y++) for (int x = 10; x <= 16; x++) {
            if (x == 13) continue;
            assertEquals('-', ArchiveShape.cell(x, y, 0), "door at " + x + "," + y);
            assertEquals('-', ArchiveShape.cell(x, y, 1), "vestibule at " + x + "," + y);
        }
        assertEquals('n', ArchiveShape.cell(10, 5, 0), "the cornice lintel spans the doors");
        assertEquals('F', ArchiveShape.cell(13, 0, 1), "a foundation under the vestibule's axis");
    }

    @Test
    void theInteriorIsEnclosedExceptTheDoors() {
        // through air only, with the door cells closed: nothing escapes and everything reached is inside
        Set<Long> reached = flood(false);
        assertTrue(reached.size() > 1000);
        for (long k : reached) {
            int x = (int) (k >> 32 & 0xFF), y = (int) (k >> 16 & 0xFF), z = (int) (k & 0xFF);
            assertTrue(ArchiveShape.inside(x + 0.5, y + 0.5, z + 0.5), "reached " + x + "," + y + "," + z);
        }
        assertTrue(reached.contains(key(13, 13, 20)), "the clerestory is part of the hall");
        assertTrue(reached.contains(key(13, 14, 20)), "and the space under the ridge");
        assertTrue(reached.contains(key(3, 10, 3)), "and the stair wells");
        // with the doors open it does escape
        assertTrue(flood(true) == null);
    }

    private static long key(int x, int y, int z) {
        return (long) x << 32 | (long) y << 16 | z;
    }

    /** Air reached from the nave; null when it leaves the box. */
    private static Set<Long> flood(boolean doorsOpen) {
        Set<Long> seen = new HashSet<>();
        Deque<int[]> todo = new ArrayDeque<>();
        todo.add(new int[] { 13, 1, 15 });
        seen.add(key(13, 1, 15));
        int[][] dirs = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
        while (!todo.isEmpty()) {
            int[] c = todo.poll();
            for (int[] d : dirs) {
                int x = c[0] + d[0], y = c[1] + d[1], z = c[2] + d[2];
                if (x < 0 || y < 0
                    || z < 0
                    || x >= ArchiveShape.WIDTH
                    || y >= ArchiveShape.HEIGHT
                    || z >= ArchiveShape.DEPTH) return null;
                char ch = ArchiveShape.cell(x, y, z);
                boolean open = ch == ' ' || doorsOpen && ch == '-';
                if (!open) continue;
                if (x == 0 || z == 0
                    || x == ArchiveShape.WIDTH - 1
                    || z == ArchiveShape.DEPTH - 1
                    || y == ArchiveShape.HEIGHT - 1) {
                    if (!doorsOpen) return null;
                }
                if (seen.add(key(x, y, z))) todo.add(new int[] { x, y, z });
            }
        }
        return seen;
    }

    @Test
    void insideVolume() {
        assertTrue(ArchiveShape.inside(13.5, 1.5, 15.5), "the nave");
        assertTrue(ArchiveShape.inside(1.01, 11.9, 33.9), "a back corner of the gallery");
        assertTrue(ArchiveShape.inside(10.5, 13.5, 10.5), "the clerestory");
        assertTrue(ArchiveShape.inside(13.5, 14.5, 10.5), "under the ridge");
        assertFalse(ArchiveShape.inside(3.5, 13.5, 10.5), "on the flat roof");
        assertFalse(ArchiveShape.inside(10.5, 14.5, 10.5), "in the step-back");
        assertFalse(ArchiveShape.inside(13.5, 15.5, 10.5), "in the ridge");
        assertFalse(ArchiveShape.inside(13.5, 1.5, 0.5), "in the door");
        assertFalse(ArchiveShape.inside(13.5, 0.5, 10.5), "in the floor");
        assertFalse(ArchiveShape.inside(0.5, 3.5, 10.5), "in the wall");
        assertFalse(ArchiveShape.inside(13.5, 3.5, 34.5), "in the back wall");
        for (double x = -2; x < 29; x += 0.5) for (double y = -2; y < 18; y += 0.5) {
            for (double z = -2; z < 37; z += 0.5) {
                if (!ArchiveShape.inside(x, y, z)) continue;
                assertTrue(x >= 1 && x < 26 && y >= 1 && y < 16 && z >= 1 && z < 34, "within the hall's box");
            }
        }
    }

    @Test
    void deskLecternsAndPedestals() {
        assertEquals(ArchiveShape.MID, ArchiveShape.DESK[0], "the desk is on the axis");
        assertEquals('K', ArchiveShape.cell(ArchiveShape.DESK[0], ArchiveShape.DESK[1], ArchiveShape.DESK[2]));
        assertEquals('G', ArchiveShape.cell(13, 15, 29), "under the skylight slit");
        assertTrue(ArchiveShape.DESK[2] >= 5 && ArchiveShape.DESK[2] <= 29, "under the void");
        List<int[]> lecterns = ArchiveShape.lecterns();
        assertEquals(8, lecterns.size());
        for (int[] l : lecterns) {
            assertEquals('K', ArchiveShape.cell(l[0], l[1], l[2]));
            assertEquals(1, l[1]);
            assertTrue(l[0] == 1 || l[0] == 25, "in an alcove by the wall");
            assertEquals('G', ArchiveShape.cell(l[0] == 1 ? 0 : 26, 2, l[2]), "under a window slit");
        }
        List<int[]> pedestals = ArchiveShape.pedestals();
        assertEquals(22, pedestals.size());
        for (int[] p : pedestals) assertEquals('h', ArchiveShape.cell(p[0], p[1], p[2]));
    }

    @Test
    void softFloor() {
        List<int[]> soft = ArchiveShape.softFloor();
        assertEquals(824, soft.size());
        Set<Long> seen = new HashSet<>();
        for (int[] c : soft) {
            assertEquals('.', ArchiveShape.cell(c[0], 0, c[1]));
            assertTrue(seen.add(key(c[0], 0, c[1])));
        }
        assertEquals(25 * 33 - 1, soft.size(), "the whole floor inside the foundation ring, less the axis stone");
    }

    @Test
    void canopy() {
        List<int[]> decor = ArchiveShape.decor();
        assertEquals(14, decor.size());
        Set<Long> seen = new HashSet<>();
        for (int[] d : decor) {
            assertEquals(5, d[1]);
            assertTrue(d[2] == -1 || d[2] == -2, "in front of the box");
            assertTrue(d[0] >= 10 && d[0] <= 16);
            assertEquals(Parts.frame(Parts.FR_RING), d[3]);
            assertTrue(seen.add(key(d[0], d[1], d[2] + 8)));
        }
    }

    @Test
    void partCodes() {
        assertEquals(-1, ArchiveShape.partOf(' '));
        assertEquals(Parts.AIR, ArchiveShape.partOf('-'));
        assertEquals(Parts.LIBRARY_CORE, ArchiveShape.partOf('~'));
        assertEquals(Parts.frame(Parts.FR_FOUNDATION), ArchiveShape.partOf('F'));
        assertEquals(Parts.frame(Parts.FR_CONSOLE), ArchiveShape.partOf('K'));
        assertEquals(Parts.frame(Parts.FR_SHELF), ArchiveShape.partOf('S'));
        assertEquals(Parts.fitting(Parts.F_POST), ArchiveShape.partOf('q'));
        assertEquals(Parts.fitting(Parts.F_PLINTH), ArchiveShape.partOf('p'));
        assertEquals(Parts.fitting(Parts.F_CROWN), ArchiveShape.partOf('c'));
        assertEquals(Parts.deck(Parts.D_CORNICE), ArchiveShape.partOf('n'));
        assertEquals(Parts.deck(Parts.D_PANEL), ArchiveShape.partOf('W'));
        assertEquals(Parts.deck(Parts.D_PANEL_LIT), ArchiveShape.partOf('L'));
        assertEquals(Parts.deck(Parts.D_PANEL_DARK), ArchiveShape.partOf('w'));
        assertEquals(Parts.fitting(Parts.F_GLAZE), ArchiveShape.partOf('G'));
        assertEquals(Parts.deck(Parts.D_DECK), ArchiveShape.partOf('d'));
        assertEquals(Parts.deck(Parts.D_DECK), ArchiveShape.partOf('.'));
        assertEquals(Parts.deck(Parts.D_LIT), ArchiveShape.partOf('e'));
        assertEquals(Parts.fitting(Parts.F_RAIL), ArchiveShape.partOf('r'));
        assertEquals(Parts.fitting(Parts.F_PEDESTAL), ArchiveShape.partOf('h'));
        assertEquals(Parts.fitting(Parts.F_TREAD), ArchiveShape.partOf('t'));
        assertThrows(IllegalArgumentException.class, () -> ArchiveShape.partOf('P'));
        for (Blueprint.Cell c : b.cells()) assertTrue(ArchiveShape.partOf(c.ch) >= 0, "a part for " + c.ch);
    }

    @Test
    void charactersDoNotClashWithTheHall() {
        // the 0.9.2 hall uses F P H B K ~ -; the Archive shares only the ones with the same meaning
        Set<Character> hall = new HashSet<>();
        for (Blueprint.Cell c : LibraryShape.BLUEPRINT.cells()) hall.add(c.ch);
        for (Blueprint.Cell c : b.cells()) {
            if (hall.contains(c.ch)) assertTrue("FK~-".indexOf(c.ch) >= 0, "shared character " + c.ch);
        }
    }

    @Test
    void placesInTheWorld() {
        // controller at (0, 70, 0) facing north: the back wall is 34 blocks behind it, the floor 2 below
        int[] back = b.world(13, ArchiveShape.HEIGHT - 1, 34, 0, 70, 0, 0, -1);
        assertArrayEquals(new int[] { 0, 68, 34 }, back);
    }
}
