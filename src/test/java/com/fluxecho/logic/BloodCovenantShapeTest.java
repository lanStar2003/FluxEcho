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
 * The Blood Covenant Altar's shape against its generator ({@code city/gen/b_pav.py}, {@code blood_covenant()}): the
 * same cells (a checksum of every layer), the same counts, mirror-symmetric, the controller over the altar's console on
 * the dais at the middle, the four portals' air, the three stepped tiers round the oculus, the 12 units in slot order,
 * the gate niche soft but built, and a stage for every cell as CITY.md §4.4 lists them.
 */
class BloodCovenantShapeTest {

    private static final ShapeKit.Shape S = BloodCovenantShape.SHAPE;

    @Test
    void theCellsAreTheGenerators() {
        CRC32 crc = new CRC32();
        crc.update(
            String.join("\n", ShapeKit.layers(S))
                .getBytes(StandardCharsets.UTF_8));
        // zlib.crc32 of the generator's layers, y0 first, rows from the front, each row x 0..24
        assertEquals(1871731853L, crc.getValue());
    }

    @Test
    void theGeneratorsCounts() {
        Map<Character, Integer> want = new TreeMap<>();
        Object[] pairs = { 'W', 906, '.', 281, 'L', 205, 'n', 184, 'w', 177, '-', 160, 'F', 96, '_', 64, 'r', 64, '+',
            40, 't', 24, 'B', 12, 'o', 12, 'p', 8, '{', 6, 'A', 4, '[', 3, '!', 3, 'K', 1, '~', 1 };
        for (int i = 0; i < pairs.length; i += 2) want.put((Character) pairs[i], (Integer) pairs[i + 1]);
        assertEquals(want, S.counts());
        assertEquals(1694, S.checkedCount());
        assertEquals(397, S.softCount());
        assertEquals(160, S.airCount());
        assertEquals(0, S.decor.size(), "no canopy: its doors are its chamfer portals");
        assertEquals(2251, S.steps());
        ShapeKit.Summary sum = ShapeKit.summary(S, null);
        assertEquals(2251, sum.steps);
        assertEquals(12, sum.units);
        assertEquals(12, sum.slots);
        assertTrue(sum.symmetric);
    }

    @Test
    void theBoxIsTwentyFiveSquareAndSeventeenHigh() {
        assertEquals(25, S.W);
        assertEquals(25, S.D);
        assertEquals(17, S.H);
        Blueprint b = BloodCovenantShape.BLUEPRINT;
        assertEquals(BloodCovenantShape.HEIGHT, b.height());
        assertEquals(25, b.width());
        assertEquals(25, b.depth());
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            int[] c = BloodCovenantShape.cellOf(x, y, z);
            assertEquals(ShapeKit.soft(ch) ? ' ' : ch, b.at(c[0], c[1], c[2]), x + "," + y + "," + z);
        }
        // the foundation: the plan's thick edge, soft deck inside with the bays' trim and the ritual circle
        for (int x = 0; x < 25; x++) for (int z = 0; z < 25; z++) {
            boolean in = ShapeKit.octo(x - 12, z - 12, 12, 16);
            char ch = S.get(x, 0, z);
            if (!in) assertEquals(' ', ch);
            else if (BloodCovenantShape.onEdge(x, z)) assertEquals('F', ch);
            else assertTrue(ch == '.' || ch == '+' || ch == '_', x + "," + z);
        }
    }

    @Test
    void symmetricAcrossX() {
        assertTrue(
            S.symmetric()
                .isEmpty());
        assertTrue(S.asym.isEmpty());
    }

    /**
     * The controller stands at y3 over the altar's console on the dais: one stands on the dais's block ring at y2, so
     * it is at eye height there. The dais's middle under the console is left empty, as the generator draws it.
     */
    @Test
    void theControllerOverTheAltarOnTheDais() {
        assertArrayEquals(new int[] { 12, 3, 12 }, S.controller);
        assertEquals('~', S.get(12, 3, 12));
        assertEquals('K', S.get(12, 2, 12), "over the altar's console");
        assertEquals(' ', S.get(12, 1, 12), "the dais's middle, closed in by its blocks");
        for (int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } })
            assertEquals('B', S.get(12 + d[0], 1, 12 + d[1]));
        assertEquals('t', S.get(12, 1, 9), "the dais's treads");
        assertEquals('B', S.get(12, 1, 10));
        Blueprint b = BloodCovenantShape.BLUEPRINT;
        assertArrayEquals(BloodCovenantShape.cellOf(12, 3, 12), new int[] { b.ctrlA, b.ctrlB, b.ctrlC });
        assertEquals(-1, BloodCovenantShape.UNITS.unitOfCell(12, 2, 12));
        // the sky over the altar: open from the controller up through the oculus
        for (int y = 4; y < S.H; y++) assertEquals(' ', S.get(12, y, 12), "y" + y);
    }

    @Test
    void theFourPortals() {
        int air = 0;
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            if (S.get(x, y, z) != '-') continue;
            assertTrue(y >= 1 && y <= 4, "door air at y" + y);
            assertTrue(BloodCovenantShape.inPortal(x, z));
            assertEquals('n', S.get(x, 5, z), "the lintel over " + x + "," + z);
            air++;
        }
        assertEquals(160, air, "four portals of 40 cells, two deep");
        for (int[] c : new int[][] { { 4, 4 }, { 20, 4 }, { 4, 20 }, { 20, 20 } }) {
            for (int y = 1; y <= 4; y++) assertEquals('-', S.get(c[0], y, c[1]));
        }
        assertEquals('L', S.get(6, 3, 3), "a jamb of the front-left portal");
    }

    /**
     * The three tiers: tier 1's walls y1..6 on the plan's edge under the slab at y7 (its outer ring a cornice, a rail
     * round its terrace at y8), tier 2's walls y8..11 on oct(9, 12) (a dark course, lit ribs) under the slab at y12,
     * tier 3's walls y13..15 on oct(6, 8) under the roof at y16, open in the oculus oct(2, 3) with its lit rim.
     */
    @Test
    void theThreeTiersRoundTheOculus() {
        for (int y = 1; y <= 6; y++) assertTrue(ShapeKit.checked(S.get(12, y, 0)) || S.get(12, y, 0) == '!');
        assertEquals('A', S.get(12, 5, 0), "an anchor in the front face's middle");
        assertEquals('n', S.get(12, 7, 0), "the first slab's cornice");
        assertEquals('W', S.get(12, 7, 2));
        assertEquals('r', S.get(12, 8, 0), "the terrace's rail");
        assertEquals(' ', S.get(12, 7, 4), "the funnel opens inside oct(8, 11)");
        assertEquals('L', S.get(12, 8, 3), "tier 2's rib");
        assertEquals('w', S.get(10, 8, 3), "tier 2's dark course");
        assertEquals('W', S.get(10, 9, 3));
        assertEquals('n', S.get(12, 12, 3), "the second slab's cornice");
        assertEquals(' ', S.get(12, 12, 7), "the funnel opens inside oct(5, 7)");
        assertEquals('L', S.get(12, 13, 6), "tier 3's rib");
        assertEquals('n', S.get(12, 16, 6), "the roof's cornice");
        assertEquals('L', S.get(12, 16, 9), "the oculus's rim");
        assertEquals(' ', S.get(12, 16, 10), "the oculus");
        int rim = 0, rails = 0;
        for (int x = 0; x < 25; x++) for (int z = 0; z < 25; z++) {
            int du = x - 12, dv = z - 12;
            if (ShapeKit.octo(du, dv, 3, 4) && !ShapeKit.octo(du, dv, 2, 3)) {
                assertEquals('L', S.get(x, 16, z));
                rim++;
            }
            if (ShapeKit.octo(du, dv, 2, 3)) assertEquals(' ', S.get(x, 16, z), "the oculus at " + x + "," + z);
            if (S.get(x, 8, z) == 'r') {
                assertTrue(ShapeKit.octo(du, dv, 12, 16) && !ShapeKit.octo(du, dv, 11, 15));
                assertEquals('n', S.get(x, 7, z), "the rail stands on the cornice");
                rails++;
            }
        }
        assertEquals(16, rim);
        assertEquals(64, rails);
        assertEquals(BloodCovenantShape.ROOF_Y, S.H - 1);
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
        Blueprint b = BloodCovenantShape.BLUEPRINT;
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            boolean niche = want.contains(ShapeKit.Shape.pos(x, y, z));
            assertEquals(niche, BloodCovenantShape.inNiche(x, y, z), x + "," + y + "," + z);
            if (!niche) continue;
            assertTrue(ShapeKit.soft(ch), "soft at " + x + "," + y + "," + z);
            int[] c = BloodCovenantShape.cellOf(x, y, z);
            assertEquals(' ', b.at(c[0], c[1], c[2]), "not checked at " + x + "," + y + "," + z);
            char wall = y == 1 ? 'w' : x == 12 ? 'L' : 'W';
            assertEquals(wall, ShapeKit.checkedTwin(ch));
            assertEquals(ShapeKit.partOf(wall), ShapeKit.partOf(ch), "the same block at " + x + "," + y + "," + z);
            assertEquals(BuildPlan.M_SHELL, BloodCovenantShape.stage(x, y, z, ch), "built with the wall");
        }
        for (int y = 1; y <= 4; y++) for (int x : new int[] { 10, 14 }) {
            char ch = S.get(x, y, 24);
            assertTrue(ShapeKit.checked(ch), x + "," + y);
            assertEquals(ch, b.at(x, BloodCovenantShape.HEIGHT - 1 - y, 24));
        }
        for (UnitLayout.Unit u : BloodCovenantShape.UNITS.units())
            for (int[] c : u.cells) assertFalse(BloodCovenantShape.inNiche(c[0], c[1], c[2]), u.name);
    }

    @Test
    void everyCharacterIsALegendPart() {
        for (Map.Entry<Character, Integer> e : S.counts()
            .entrySet()) {
            char ch = e.getKey();
            if (ch == '~') continue;
            assertTrue(ShapeKit.partOf(ch) >= 0, "'" + ch + "'");
        }
        assertEquals(Parts.moduleCore(ModuleCores.BLOOD_COVENANT), BloodCovenantShape.BUILD.part(12, 3, 12, '~'));
        assertEquals(Parts.frame(Parts.FR_BASE), ShapeKit.partOf('B'));
        assertEquals(Parts.fitting(Parts.F_RAIL), ShapeKit.partOf('r'));
    }

    /** The 12 units in the generator's order, which is the slot order for good. */
    @Test
    void theUnits() {
        UnitLayout u = BloodCovenantShape.UNITS;
        assertEquals(12, u.size());
        assertEquals(12, u.slots());
        int[][] cradles = { { 18, 12 }, { 6, 12 }, { 12, 18 }, { 12, 6 }, { 16, 16 }, { 8, 16 }, { 16, 8 }, { 8, 8 } };
        int[][] bays = { { 12, 3 }, { 12, 21 }, { 3, 12 }, { 21, 12 } };
        for (int i = 0; i < 12; i++) {
            UnitLayout.Unit p = u.unit(i);
            boolean cradle = i < 8;
            assertEquals(cradle ? "cradle-" + (i + 1) : "bay-" + (i - 7), p.name);
            assertEquals(cradle ? "cradle" : "ritual", p.kind);
            assertEquals(1, p.slots);
            assertEquals(1, p.sockets.size());
            assertEquals(i, p.firstSlot);
            assertEquals(1, p.floor);
            int[] at = cradle ? cradles[i] : bays[i - 8];
            if (cradle) {
                assertEquals(2, p.cells.size());
                assertArrayEquals(new int[] { at[0], 1, at[1] }, p.cells.get(0));
                assertEquals('p', S.get(at[0], 1, at[1]));
                assertArrayEquals(new int[] { at[0], 2, at[1] }, p.sockets.get(0));
                assertEquals(6, ShapeKit.onorm(at[0] - 12, at[1] - 12), "at octagon radius 6");
            } else {
                assertEquals(1, p.cells.size());
                assertArrayEquals(new int[] { at[0], 1, at[1] }, p.sockets.get(0));
                // the bay round it: a 5 x 5 square of soft trim in the floor
                for (int a = -2; a <= 2; a++) for (int b = -2; b <= 2; b++) {
                    if (Math.abs(a) < 2 && Math.abs(b) < 2) continue;
                    char f = S.get(at[0] + a, 0, at[1] + b);
                    assertTrue(f == '_' || f == 'F', "the bay's trim at " + (at[0] + a) + "," + (at[1] + b));
                }
            }
            for (int[] c : p.cells) assertTrue(ShapeKit.checked(S.get(c[0], c[1], c[2])), p.name + " checked");
            assertEquals(null, p.face);
        }
        assertArrayEquals(new int[] { 1 }, u.storeys(), "everything stands on the floor");
    }

    /** Every cell gets a stage, and the stages hold what CITY.md §4.4 lists. */
    @Test
    void everyCellHasItsStage() {
        Map<Integer, Integer> perStage = new HashMap<>();
        Set<Long> unitCells = new HashSet<>();
        for (UnitLayout.Unit u : BloodCovenantShape.UNITS.units())
            for (int[] c : u.cells) unitCells.add(ShapeKit.Shape.pos(c[0], c[1], c[2]));
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            if (ch == ' ' || unitCells.contains(ShapeKit.Shape.pos(x, y, z))) continue;
            int st = BloodCovenantShape.stage(x, y, z, ch);
            perStage.merge(st, 1, Integer::sum);
            if (ch == '-' || ch == 'F') assertEquals(BuildPlan.M_SHELL, st);
            if (y == 0 && ShapeKit.soft(ch)) assertEquals(BuildPlan.M_FLOOR, st, "the floor, its bays and circle");
            if (ch == '~') assertEquals(BuildPlan.M_COMMISSION, st);
            else if (ch == 'B' || ch == 't' || ch == 'K') assertEquals(BuildPlan.M_FIT, st, "the dais and the altar");
            else if (y >= 1 && y <= 6) assertEquals(BuildPlan.M_SHELL, st, "tier 1 at " + x + "," + y + "," + z);
            else if (y == 7 || y == 12 || y >= 8 && y <= 11 && ch != 'r')
                assertEquals(BuildPlan.M_DECK, st, "the slabs and tier 2 at " + x + "," + y + "," + z);
            else if (y > 0) assertEquals(BuildPlan.M_ROOF, st, "tier 3, the roof and the rail at " + x + "," + y);
            if (st == BuildPlan.M_FIT) assertTrue(BloodCovenantShape.fitRank(x, y, z, ch) < 0, "before the units");
        }
        for (int s : new int[] { BuildPlan.M_FLOOR, BuildPlan.M_SHELL, BuildPlan.M_DECK, BuildPlan.M_ROOF,
            BuildPlan.M_FIT, BuildPlan.M_COMMISSION }) assertNotNull(perStage.get(s), "stage " + s + " has cells");
        assertFalse(perStage.containsKey(BuildPlan.M_UPPER), "no upper walls");
        assertFalse(perStage.containsKey(BuildPlan.M_CLEAR));
        assertEquals(1, (int) perStage.get(BuildPlan.M_COMMISSION));
        assertEquals(12 + 24 + 1, (int) perStage.get(BuildPlan.M_FIT), "the dais's blocks and treads, the console");
        // the dais before the altar's console on it
        assertTrue(BloodCovenantShape.fitRank(12, 1, 11, 'B') < BloodCovenantShape.fitRank(12, 2, 12, 'K'));
    }
}
