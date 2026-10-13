package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;

import org.junit.jupiter.api.Test;

/**
 * The Echo Hunting Ground's shape against its generator ({@code city/gen/b_pav.py}, {@code hunting_ground()}): the same
 * cells (a checksum of every layer), the same counts, mirror-symmetric, the controller on the holo emitter at the
 * arena's centre, the portals' air, the arena's grid, the terraces, the vomitoria and their bridges, the velarium, the
 * twelve imprint consoles at 30 degree steps in slot order, the gate niche soft but built, and a stage for every cell
 * as CITY.md §4.5 lists them.
 */
class HuntingGroundShapeTest {

    private static final ShapeKit.Shape S = HuntingGroundShape.SHAPE;

    @Test
    void theCellsAreTheGenerators() {
        CRC32 crc = new CRC32();
        crc.update(
            String.join("\n", ShapeKit.layers(S))
                .getBytes(StandardCharsets.UTF_8));
        // zlib.crc32 of the generator's layers, y0 first, rows from the front, each row x 0..24
        assertEquals(4232271297L, crc.getValue());
    }

    @Test
    void theGeneratorsCounts() {
        Map<Character, Integer> want = new TreeMap<>();
        Object[] pairs = { 'W', 482, '.', 304, 'd', 244, 'L', 161, 'n', 136, 'R', 108, 't', 104, 'F', 96, '-', 88, 'w',
            73, 'G', 48, ':', 45, '+', 36, 'p', 12, 'o', 12, '{', 6, 'A', 4, 'h', 4, '[', 3, '!', 3, 'K', 1, '~', 1 };
        for (int i = 0; i < pairs.length; i += 2) want.put((Character) pairs[i], (Integer) pairs[i + 1]);
        assertEquals(want, S.counts());
        assertEquals(1531, S.checkedCount());
        assertEquals(352, S.softCount());
        assertEquals(88, S.airCount());
        assertEquals(0, S.decor.size(), "no canopy: its doors are its chamfer portals");
        assertEquals(1971, S.steps());
        ShapeKit.Summary sum = ShapeKit.summary(S, null);
        assertEquals(1971, sum.steps);
        assertEquals(12, sum.units);
        assertEquals(12, sum.slots);
        assertTrue(sum.symmetric);
    }

    @Test
    void theBoxIsTwentyFiveSquareAndTenHigh() {
        assertEquals(25, S.W);
        assertEquals(25, S.D);
        assertEquals(10, S.H);
        Blueprint b = HuntingGroundShape.BLUEPRINT;
        assertEquals(HuntingGroundShape.HEIGHT, b.height());
        assertEquals(25, b.width());
        assertEquals(25, b.depth());
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            int[] c = HuntingGroundShape.cellOf(x, y, z);
            assertEquals(ShapeKit.soft(ch) ? ' ' : ch, b.at(c[0], c[1], c[2]), x + "," + y + "," + z);
        }
    }

    @Test
    void symmetricAcrossX() {
        assertTrue(
            S.symmetric()
                .isEmpty());
        assertTrue(S.asym.isEmpty());
    }

    @Test
    void theControllerOnTheHoloEmitter() {
        assertArrayEquals(new int[] { 12, 2, 12 }, S.controller);
        assertEquals('~', S.get(12, 2, 12));
        assertEquals('K', S.get(12, 1, 12), "over the emitter's console");
        for (int[] d : new int[][] { { 2, 0 }, { -2, 0 }, { 0, 2 }, { 0, -2 } })
            assertEquals('h', S.get(12 + d[0], 1, 12 + d[1]), "a holo pedestal");
        Blueprint b = HuntingGroundShape.BLUEPRINT;
        assertArrayEquals(HuntingGroundShape.cellOf(12, 2, 12), new int[] { b.ctrlA, b.ctrlB, b.ctrlC });
        assertEquals(-1, HuntingGroundShape.UNITS.unitOfCell(12, 1, 12));
        for (int y = 3; y < S.H; y++) assertEquals(' ', S.get(12, y, 12), "open to the sky at y" + y);
    }

    /**
     * The portals' must-be-air: the openings in the wall and, where no terrace or vomitorium took them, the cells one
     * further in; every one under its lintel.
     */
    @Test
    void thePortals() {
        int air = 0;
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            if (S.get(x, y, z) != '-') continue;
            assertTrue(y >= 1 && y <= 4, "door air at y" + y);
            assertTrue(HuntingGroundShape.inPortal(x, z));
            assertEquals('n', S.get(x, 5, z), "the lintel over " + x + "," + z);
            air++;
        }
        assertEquals(88, air);
        for (int[] c : new int[][] { { 4, 4 }, { 20, 4 }, { 4, 20 }, { 20, 20 } }) {
            for (int y = 1; y <= 4; y++) assertEquals('-', S.get(c[0], y, c[1]));
        }
    }

    /**
     * The arena (octagon radius 5 and in): grate and soft lit tiles alternating in the floor; the terraces from radius
     * 6 (a tread at y1, one stands at 1.5) to 11 (deck at y3, one stands at 4), half a block a ring; the vomitoria cut
     * through them at ground level, bridged by deck at y3 from radius 9 out; the outer wall's cornice at y8 and the
     * velarium's ring at y9 over the top terraces.
     */
    @Test
    void theArenaTheTerracesAndTheVelarium() {
        int grid = 0;
        for (int x = 0; x < 25; x++) for (int z = 0; z < 25; z++) {
            int du = x - 12, dv = z - 12, r = ShapeKit.onorm(du, dv);
            if (!ShapeKit.octo(du, dv, 12, 16) || HuntingGroundShape.onEdge(x, z)) continue;
            if (r <= 5) {
                assertEquals((du + dv) % 2 == 0 ? ':' : '+', S.get(x, 0, z), "the grid at " + x + "," + z);
                grid++;
            }
            if (r == 10 || r == 11) assertEquals('R', S.get(x, 9, z), "the velarium at " + x + "," + z);
            else assertEquals(' ', S.get(x, 9, z));
        }
        assertEquals(81, grid);
        // a terrace ring a step: radius 7 a deck block at y1, radius 8 a tread on it at y2, radius 11 deck at y3
        assertEquals('d', S.get(12, 1, 5));
        assertEquals('t', S.get(12, 2, 4));
        assertEquals('d', S.get(12, 1, 4));
        assertEquals('W', S.get(12, 1, 1));
        assertEquals('W', S.get(12, 2, 1));
        assertEquals('d', S.get(12, 3, 1));
        assertEquals('t', S.get(11, 1, 6), "the lowest terrace beside a console");
        // a vomitorium: open at ground level from the portal in, its bridge over it from radius 9 out
        assertTrue(HuntingGroundShape.inVomitorium(6, 6));
        assertEquals(' ', S.get(6, 1, 6));
        assertEquals(' ', S.get(6, 2, 6));
        assertTrue(HuntingGroundShape.inVomitorium(5, 5));
        assertEquals('d', S.get(5, 3, 5), "the bridge");
        assertEquals(' ', S.get(5, 4, 5));
        assertFalse(HuntingGroundShape.inVomitorium(12, 6));
        for (int x = 0; x < 25; x++) for (int z = 0; z < 25; z++) {
            if (!HuntingGroundShape.onEdge(x, z)) continue;
            assertEquals('n', S.get(x, 8, z), "the cornice at " + x + "," + z);
        }
        assertEquals(HuntingGroundShape.VELARIUM_Y, S.H - 1);
    }

    /**
     * The gate niche (内境门位, CITY.md §4.15) in the back flat face's middle, x 11..13, y1..4, z 24: its twelve cells
     * are soft (left out of the blueprint the structure check uses) yet hold the same blocks as the wall round them
     * (the dark course at y1, the lit pilaster at x 12, panels beside it), built with the wall.
     */
    @Test
    void theGateNicheIsSoftAndStillBuilt() {
        Set<Long> want = new HashSet<>();
        for (int x = 11; x <= 13; x++) for (int y = 1; y <= 4; y++) want.add(ShapeKit.Shape.pos(x, y, 24));
        assertEquals(want, S.niche);
        Blueprint b = HuntingGroundShape.BLUEPRINT;
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            boolean niche = want.contains(ShapeKit.Shape.pos(x, y, z));
            assertEquals(niche, HuntingGroundShape.inNiche(x, y, z), x + "," + y + "," + z);
            if (!niche) continue;
            assertTrue(ShapeKit.soft(ch), "soft at " + x + "," + y + "," + z);
            int[] c = HuntingGroundShape.cellOf(x, y, z);
            assertEquals(' ', b.at(c[0], c[1], c[2]), "not checked at " + x + "," + y + "," + z);
            char wall = y == 1 ? 'w' : x == 12 ? 'L' : 'W';
            assertEquals(wall, ShapeKit.checkedTwin(ch));
            assertEquals(ShapeKit.partOf(wall), ShapeKit.partOf(ch), "the same block at " + x + "," + y + "," + z);
            assertEquals(BuildPlan.M_SHELL, HuntingGroundShape.stage(x, y, z, ch), "built with the wall");
        }
        for (int y = 1; y <= 4; y++) for (int x : new int[] { 10, 14 }) {
            char ch = S.get(x, y, 24);
            assertTrue(ShapeKit.checked(ch), x + "," + y);
            assertEquals(ch, b.at(x, HuntingGroundShape.HEIGHT - 1 - y, 24));
        }
        for (UnitLayout.Unit u : HuntingGroundShape.UNITS.units())
            for (int[] c : u.cells) assertFalse(HuntingGroundShape.inNiche(c[0], c[1], c[2]), u.name);
    }

    @Test
    void everyCharacterIsALegendPart() {
        for (Map.Entry<Character, Integer> e : S.counts()
            .entrySet()) {
            char ch = e.getKey();
            if (ch == '~') continue;
            assertTrue(ShapeKit.partOf(ch) >= 0, "'" + ch + "'");
        }
        assertEquals(Parts.moduleCore(ModuleCores.HUNTING_GROUND), HuntingGroundShape.BUILD.part(12, 2, 12, '~'));
        assertEquals(Parts.frame(Parts.FR_RING), ShapeKit.partOf('R'));
        assertEquals(Parts.deck(Parts.D_GRATE), ShapeKit.partOf(':'));
    }

    /**
     * The twelve imprint consoles in the generator's order, which is the slot order for good: round the arena's rim
     * from the front (toward the nexus) to the right at 30 degree steps, each the rim cell nearest its angle.
     */
    @Test
    void theUnits() {
        UnitLayout u = HuntingGroundShape.UNITS;
        assertEquals(12, u.size());
        assertEquals(12, u.slots());
        int[][] at = { { 12, 6 }, { 15, 7 }, { 17, 9 }, { 18, 12 }, { 17, 15 }, { 15, 17 }, { 12, 18 }, { 9, 17 },
            { 7, 15 }, { 6, 12 }, { 7, 9 }, { 9, 7 } };
        Set<Long> plan = ShapeKit.planCells();
        for (int i = 0; i < 12; i++) {
            UnitLayout.Unit p = u.unit(i);
            assertEquals("imprint-" + (i + 1), p.name);
            assertEquals("imprint", p.kind);
            assertEquals(1, p.slots);
            assertEquals(i, p.firstSlot);
            assertEquals(2, p.cells.size());
            assertArrayEquals(new int[] { at[i][0], 1, at[i][1] }, p.cells.get(0));
            assertArrayEquals(new int[] { at[i][0], 2, at[i][1] }, p.sockets.get(0));
            assertArrayEquals(at[i], HuntingGroundShape.consoleCell(plan, i));
            assertEquals('p', S.get(at[i][0], 1, at[i][1]));
            assertEquals(' ', S.get(at[i][0], 3, at[i][1]), "head room over the socket");
            assertEquals(6, ShapeKit.onorm(at[i][0] - 12, at[i][1] - 12), "on the arena's rim");
            assertFalse(HuntingGroundShape.inVomitorium(at[i][0], at[i][1]));
            assertEquals(1, p.floor);
        }
        assertArrayEquals(new int[] { 1 }, u.storeys(), "the consoles stand on the arena's floor");
    }

    /**
     * The window's plan stays at the arena's floor (0.13.0): a console's plinth has the seating's deck block behind it
     * at its own level, but people stand beside it in the arena, so its storey is not flush like the Tidal Well's taps
     * (whose plan stays one level up over the walkway) and the plan shows the arena's grid, not a hole.
     */
    @Test
    void theWindowsPlanStaysOnTheArenasFloor() {
        assertFalse(ModuleMap.flush(HuntingGroundShape.BUILD, HuntingGroundShape.UNITS, 1));
        assertTrue(ModuleMap.flush(RootShape.BUILD, RootShape.UNITS, RootShape.FLOOR_Y), "the taps stay flush");
        assertEquals('d', S.get(12, 1, 5), "the deck block behind the first console");
        assertEquals(' ', S.get(12, 1, 7), "the arena in front of it");
        ModuleMap map = new ModuleMap(HuntingGroundShape.BUILD, HuntingGroundShape.UNITS, "R", 128, 128);
        assertEquals(ModuleMap.Mark.LIT, map.mark(0, 12, 11), "a lit tile of the arena's grid");
        assertEquals(ModuleMap.Mark.FLOOR, map.mark(0, 13, 9), "a grate tile");
        assertEquals(ModuleMap.Mark.FIXTURE, map.mark(0, 12, 10), "a holo pedestal");
    }

    /** Every cell gets a stage, and the stages hold what CITY.md §4.5 lists. */
    @Test
    void everyCellHasItsStage() {
        Map<Integer, Integer> perStage = new HashMap<>();
        Set<Long> unitCells = new HashSet<>();
        for (UnitLayout.Unit u : HuntingGroundShape.UNITS.units())
            for (int[] c : u.cells) unitCells.add(ShapeKit.Shape.pos(c[0], c[1], c[2]));
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            if (ch == ' ' || unitCells.contains(ShapeKit.Shape.pos(x, y, z))) continue;
            int st = HuntingGroundShape.stage(x, y, z, ch);
            perStage.merge(st, 1, Integer::sum);
            boolean edge = HuntingGroundShape.onEdge(x, z);
            if (ch == '~') assertEquals(BuildPlan.M_COMMISSION, st);
            else if (ch == '-' || ch == 'F' || edge) assertEquals(BuildPlan.M_SHELL, st, "the wall at " + x + "," + y);
            else if (y == 0) assertEquals(BuildPlan.M_FLOOR, st, "the floor and the arena's grid");
            else if (ch == 'R') assertEquals(BuildPlan.M_ROOF, st, "the velarium");
            else if (ch == 'K' || ch == 'h') assertEquals(BuildPlan.M_FIT, st, "the emitter and its pedestals");
            else if (ch == 'n') assertEquals(BuildPlan.M_SHELL, st, "a portal's lintel");
            else assertEquals(BuildPlan.M_DECK, st, "the terraces at " + x + "," + y + "," + z);
            if (st == BuildPlan.M_FIT) assertTrue(HuntingGroundShape.fitRank(x, y, z, ch) < 0, "before the units");
        }
        for (int s : new int[] { BuildPlan.M_FLOOR, BuildPlan.M_SHELL, BuildPlan.M_DECK, BuildPlan.M_ROOF,
            BuildPlan.M_FIT, BuildPlan.M_COMMISSION }) assertNotNull(perStage.get(s), "stage " + s + " has cells");
        assertFalse(perStage.containsKey(BuildPlan.M_UPPER), "no upper walls");
        assertFalse(perStage.containsKey(BuildPlan.M_CLEAR));
        assertEquals(1, (int) perStage.get(BuildPlan.M_COMMISSION));
        assertEquals(5, (int) perStage.get(BuildPlan.M_FIT), "the emitter's console and its four pedestals");
        assertEquals(108, (int) perStage.get(BuildPlan.M_ROOF), "the velarium's ring");
        assertTrue(HuntingGroundShape.fitRank(12, 1, 12, 'K') < HuntingGroundShape.fitRank(14, 1, 12, 'h'));
    }
}
