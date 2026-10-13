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
 * The Arcane Court's shape against its generator ({@code city/gen/b_pav.py}, {@code arcane_court()}): the same cells (a
 * checksum of every layer), the same counts, mirror-symmetric, the controller on the altar's console at the middle,
 * the four portals' air, the spire, the 21 units in slot order, the gate niche soft but built, and a stage for every
 * cell as CITY.md §4.3 lists them.
 */
class ArcaneCourtShapeTest {

    private static final ShapeKit.Shape S = ArcaneCourtShape.SHAPE;

    @Test
    void theCellsAreTheGenerators() {
        CRC32 crc = new CRC32();
        crc.update(
            String.join("\n", ShapeKit.layers(S))
                .getBytes(StandardCharsets.UTF_8));
        // zlib.crc32 of the generator's layers, y0 first, rows from the front, each row x 0..24
        assertEquals(1506882503L, crc.getValue());
    }

    @Test
    void theGeneratorsCounts() {
        Map<Character, Integer> want = new TreeMap<>();
        Object[] pairs = { 'W', 931, 'L', 389, 'G', 328, '.', 304, '-', 160, 'n', 136, 'F', 96, '+', 81, 'w', 65, 'o',
            44, 'u', 32, 'K', 9, 'p', 8, 'c', 8, 'A', 8, '/', 4, '[', 3, '!', 3, '{', 2, 'C', 2, '~', 1, 'Z', 1 };
        for (int i = 0; i < pairs.length; i += 2) want.put((Character) pairs[i], (Integer) pairs[i + 1]);
        assertEquals(want, S.counts());
        assertEquals(2058, S.checkedCount());
        assertEquals(397, S.softCount());
        assertEquals(160, S.airCount());
        assertEquals(0, S.decor.size(), "no canopy: a centrally planned building's doors are its chamfer portals");
        assertEquals(2615, S.steps());
        ShapeKit.Summary sum = ShapeKit.summary(S, null);
        assertEquals(2615, sum.steps);
        assertEquals(21, sum.units);
        assertEquals(44, sum.slots);
        assertTrue(sum.symmetric);
    }

    @Test
    void theBoxIsTwentyFiveSquareAndThirtyThreeHigh() {
        assertEquals(25, S.W);
        assertEquals(25, S.D);
        assertEquals(33, S.H);
        Blueprint b = ArcaneCourtShape.BLUEPRINT;
        assertEquals(ArcaneCourtShape.HEIGHT, b.height());
        assertEquals(25, b.width());
        assertEquals(25, b.depth());
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            int[] c = ArcaneCourtShape.cellOf(x, y, z);
            assertEquals(ShapeKit.soft(ch) ? ' ' : ch, b.at(c[0], c[1], c[2]), x + "," + y + "," + z);
        }
        // the foundation: the plan's thick edge, soft deck and the rune ring inside
        for (int x = 0; x < 25; x++) for (int z = 0; z < 25; z++) {
            boolean in = ShapeKit.octo(x - 12, z - 12, 12, 16);
            char ch = S.get(x, 0, z);
            if (!in) assertEquals(' ', ch);
            else if (ArcaneCourtShape.onEdge(x, z)) assertEquals('F', ch);
            else assertTrue(ch == '.' || ch == '+', x + "," + z);
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
    void theControllerOnTheAltar() {
        assertArrayEquals(new int[] { 12, 2, 12 }, S.controller);
        assertEquals('~', S.get(12, 2, 12));
        assertEquals('K', S.get(12, 1, 12), "over the altar's console stand");
        Blueprint b = ArcaneCourtShape.BLUEPRINT;
        assertArrayEquals(ArcaneCourtShape.cellOf(12, 2, 12), new int[] { b.ctrlA, b.ctrlB, b.ctrlC });
        // the altar is in no unit; the infusion circle round it is
        assertEquals(-1, ArcaneCourtShape.UNITS.unitOfCell(12, 1, 12));
        // the spire is hollow over the altar: thirty blocks of air up to the lantern's cap
        for (int y = 3; y < ArcaneCourtShape.CAP_Y; y++) assertEquals(' ', S.get(12, y, 12), "y" + y);
        assertEquals('W', S.get(12, ArcaneCourtShape.CAP_Y, 12));
    }

    @Test
    void theFourPortals() {
        int air = 0;
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            if (S.get(x, y, z) != '-') continue;
            assertTrue(y >= 1 && y <= 4, "door air at y" + y);
            assertTrue(ArcaneCourtShape.inPortal(x, z));
            assertEquals('n', S.get(x, 5, z), "the lintel over " + x + "," + z);
            air++;
        }
        assertEquals(160, air, "four portals of 40 cells, two deep");
        // a portal on each chamfer, the jambs either side lit
        for (int[] c : new int[][] { { 4, 4 }, { 20, 4 }, { 4, 20 }, { 20, 20 } }) {
            for (int y = 1; y <= 4; y++) assertEquals('-', S.get(c[0], y, c[1]));
        }
        assertEquals('L', S.get(6, 3, 3), "a jamb of the front-left portal");
        assertEquals('-', S.get(5, 3, 3));
    }

    @Test
    void theSpireAndTheFinial() {
        // the cornice at y12 all round the walls, the first spire ring (radius 12) at y13 with its anchors
        int cornice = 0;
        for (int x = 0; x < 25; x++) for (int z = 0; z < 25; z++) if (ArcaneCourtShape.onEdge(x, z)) {
            assertEquals('n', S.get(x, 12, z));
            cornice++;
        }
        assertEquals(96, cornice);
        assertEquals(
            8,
            (int) S.counts()
                .get('A'),
            "the eight ribs at y13");
        for (int[] a : new int[][] { { 12, 0 }, { 0, 12 }, { 24, 12 }, { 12, 24 } })
            assertEquals('A', S.get(a[0], 13, a[1]));
        // radius 8 at y21..22, radius 3 at y27
        assertEquals('L', S.get(12, 21, 4));
        assertEquals('L', S.get(12, 27, 9));
        assertEquals(' ', S.get(12, 27, 8));
        // the glazed bands at y16 and y20, the lantern at y28
        assertEquals('G', S.get(9, 16, 1));
        assertEquals('G', S.get(10, 28, 12));
        for (int y = ArcaneCourtShape.FINIAL_FROM; y < ArcaneCourtShape.FINIAL_TO; y++)
            assertEquals('C', S.get(12, y, 12));
        assertEquals('Z', S.get(12, ArcaneCourtShape.FINIAL_TO, 12));
    }

    /**
     * The gate niche (内境门位, CITY.md §4.15) in the back flat face's middle, x 11..13, y1..4, z 24: its twelve cells
     * are soft (left out of the blueprint the structure check uses) yet hold the same blocks as the wall round them
     * (the dark course at y1, the lit pilaster at x 12, panels at y2 and glass above beside it), built with the wall.
     */
    @Test
    void theGateNicheIsSoftAndStillBuilt() {
        Set<Long> want = new HashSet<>();
        for (int x = 11; x <= 13; x++) for (int y = 1; y <= 4; y++) want.add(ShapeKit.Shape.pos(x, y, 24));
        assertEquals(want, S.niche);
        Blueprint b = ArcaneCourtShape.BLUEPRINT;
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            boolean niche = want.contains(ShapeKit.Shape.pos(x, y, z));
            assertEquals(niche, ArcaneCourtShape.inNiche(x, y, z), x + "," + y + "," + z);
            if (!niche) continue;
            assertTrue(ShapeKit.soft(ch), "soft at " + x + "," + y + "," + z);
            int[] c = ArcaneCourtShape.cellOf(x, y, z);
            assertEquals(' ', b.at(c[0], c[1], c[2]), "not checked at " + x + "," + y + "," + z);
            char wall = y == 1 ? 'w' : x == 12 ? 'L' : y == 2 ? 'W' : 'G';
            assertEquals(wall, ShapeKit.checkedTwin(ch));
            assertEquals(ShapeKit.partOf(wall), ShapeKit.partOf(ch), "the same block at " + x + "," + y + "," + z);
            assertEquals(BuildPlan.M_SHELL, ArcaneCourtShape.stage(x, y, z, ch), "built with the wall");
        }
        // the wall round it stays checked
        for (int y = 1; y <= 4; y++) for (int x : new int[] { 10, 14 }) {
            char ch = S.get(x, y, 24);
            assertTrue(ShapeKit.checked(ch), x + "," + y);
            assertEquals(ch, b.at(x, ArcaneCourtShape.HEIGHT - 1 - y, 24));
        }
        for (UnitLayout.Unit u : ArcaneCourtShape.UNITS.units())
            for (int[] c : u.cells) assertFalse(ArcaneCourtShape.inNiche(c[0], c[1], c[2]), u.name);
    }

    @Test
    void everyCharacterIsALegendPart() {
        for (Map.Entry<Character, Integer> e : S.counts()
            .entrySet()) {
            char ch = e.getKey();
            if (ch == '~') continue;
            assertTrue(ShapeKit.partOf(ch) >= 0, "'" + ch + "'");
        }
        assertEquals(Parts.moduleCore(ModuleCores.ARCANE_COURT), ArcaneCourtShape.BUILD.part(12, 2, 12, '~'));
        assertEquals(Parts.fitting(Parts.F_PLINTH), ShapeKit.partOf('p'));
        assertEquals(Parts.fitting(Parts.F_CROWN), ShapeKit.partOf('c'));
    }

    /** The 21 units in the generator's order, which is the slot order for good. */
    @Test
    void theUnits() {
        UnitLayout u = ArcaneCourtShape.UNITS;
        assertEquals(21, u.size());
        assertEquals(44, u.slots());
        String[] names = { "infusion", "cauldron-1", "cauldron-2", "cauldron-3", "cauldron-4", "aspect-1", "aspect-2",
            "aspect-3", "aspect-4", "aspect-5", "aspect-6", "aspect-7", "aspect-8", "desk-1", "desk-2", "desk-3",
            "desk-4", "desk-5", "desk-6", "desk-7", "desk-8" };
        String[] kinds = { "infusion", "cauldron", "pillar", "desk" };
        int[] first = { 16, 1, 12 };
        int slot = 0;
        for (int i = 0; i < names.length; i++) {
            UnitLayout.Unit p = u.unit(i);
            assertEquals(names[i], p.name);
            String kind = i == 0 ? kinds[0] : i <= 4 ? kinds[1] : i <= 12 ? kinds[2] : kinds[3];
            assertEquals(kind, p.kind);
            int slots = i == 0 ? 8 : kind.equals("pillar") ? 3 : 1;
            assertEquals(slots, p.slots, p.name);
            assertEquals(slots, p.sockets.size(), p.name + ": a socket a slot");
            assertEquals(slot, p.firstSlot, p.name);
            slot += slots;
            int cells = i == 0 ? 8 : kind.equals("cauldron") ? 9 : kind.equals("pillar") ? 5 : 2;
            assertEquals(cells, p.cells.size(), p.name);
            for (int[] c : p.cells) assertTrue(ShapeKit.checked(S.get(c[0], c[1], c[2])), p.name + " checked");
        }
        assertEquals(44, slot);
        assertArrayEquals(first, u.unit(0).sockets.get(0));
        // the pillars face into the hall from the wall they stand against
        assertArrayEquals(new int[] { 0, 1 }, u.unit(5).face);
        assertArrayEquals(new int[] { -1, 0 }, u.unit(12).face);
        assertArrayEquals(new int[] { 8, 1, 1 }, u.unit(5).cells.get(0));
        assertEquals('p', S.get(8, 1, 1));
        assertEquals('c', S.get(8, 5, 1));
        // a desk is a console with a socket on it
        assertArrayEquals(new int[] { 19, 1, 16 }, u.unit(13).cells.get(0));
        assertEquals('K', S.get(19, 1, 16));
        assertEquals('o', S.get(19, 2, 16));
        assertArrayEquals(new int[] { 1 }, u.storeys(), "everything stands on the floor");
    }

    /** Every cell gets a stage, and the stages hold what CITY.md §4.3 lists. */
    @Test
    void everyCellHasItsStage() {
        Map<Integer, Integer> perStage = new HashMap<>();
        Set<Long> unitCells = new HashSet<>();
        for (UnitLayout.Unit u : ArcaneCourtShape.UNITS.units())
            for (int[] c : u.cells) unitCells.add(ShapeKit.Shape.pos(c[0], c[1], c[2]));
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            if (ch == ' ' || unitCells.contains(ShapeKit.Shape.pos(x, y, z))) continue;
            int st = ArcaneCourtShape.stage(x, y, z, ch);
            perStage.merge(st, 1, Integer::sum);
            if (ch == '~') assertEquals(BuildPlan.M_COMMISSION, st);
            if (ch == '-') assertEquals(BuildPlan.M_SHELL, st);
            if (ch == 'F') assertEquals(BuildPlan.M_SHELL, st, "the foundations open the shell");
            if (ch == '.' || ch == '+') assertEquals(BuildPlan.M_FLOOR, st, "the floor and its rune ring");
            if (y >= 1 && y <= 12 && ch != 'K' && ch != '~')
                assertEquals(BuildPlan.M_SHELL, st, "the walls at " + x + "," + y + "," + z);
            if (y >= 13 && ch != 'C' && ch != 'Z') assertEquals(BuildPlan.M_ROOF, st, "the spire at y" + y);
            if (ch == 'C' || ch == 'Z' || ch == 'K') assertEquals(BuildPlan.M_FIT, st, "the altar and the finial");
            if (st == BuildPlan.M_FIT) assertTrue(ArcaneCourtShape.fitRank(x, y, z, ch) > 0, "after the units");
        }
        for (int s : new int[] { BuildPlan.M_FLOOR, BuildPlan.M_SHELL, BuildPlan.M_ROOF, BuildPlan.M_FIT,
            BuildPlan.M_COMMISSION }) assertNotNull(perStage.get(s), "stage " + s + " has cells");
        assertFalse(perStage.containsKey(BuildPlan.M_DECK), "no deck");
        assertFalse(perStage.containsKey(BuildPlan.M_UPPER), "no upper walls");
        assertFalse(perStage.containsKey(BuildPlan.M_CLEAR));
        assertEquals(1, (int) perStage.get(BuildPlan.M_COMMISSION));
        assertEquals(4, (int) perStage.get(BuildPlan.M_FIT), "the altar's console and the finial's three cells");
        // the altar before the finial
        assertTrue(ArcaneCourtShape.fitRank(12, 1, 12, 'K') < ArcaneCourtShape.fitRank(12, 30, 12, 'C'));
    }
}
