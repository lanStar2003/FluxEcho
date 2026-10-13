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
 * The Holo Sand Table's shape against its generator ({@code city/gen/b_outer.py}, {@code holo_table()}, 0.15.0): the
 * same cells (a checksum of every layer), the same counts, mirror-symmetric, the controller on its console at the
 * table's front, the four portals' air, the drum, the hollow stepped dome and the lantern, the round table, the ring
 * light, the nine layer consoles, the double ramp and the back gallery, the units in slot order, and a stage for
 * every cell as CITY.md §4.11 lists them.
 */
class HoloTableShapeTest {

    private static final ShapeKit.Shape S = HoloTableShape.SHAPE;

    @Test
    void theCellsAreTheGenerators() {
        CRC32 crc = new CRC32();
        crc.update(
            String.join("\n", ShapeKit.layers(S))
                .getBytes(StandardCharsets.UTF_8));
        // zlib.crc32 of the generator's layers, y0 first, rows from the front, each row x 0..30
        assertEquals(1420218536L, crc.getValue());
    }

    @Test
    void theGeneratorsCounts() {
        Map<Character, Integer> want = new TreeMap<>();
        Object[] pairs = { 'W', 830, '.', 621, 'G', 541, 'L', 201, 'n', 152, '-', 150, 'F', 120, 'w', 100, 't', 50, 'r',
            45, 'R', 40, 'p', 32, 'd', 22, 'K', 10, 'A', 4, '~', 1 };
        for (int i = 0; i < pairs.length; i += 2) want.put((Character) pairs[i], (Integer) pairs[i + 1]);
        assertEquals(want, S.counts());
        assertEquals(2148, S.checkedCount());
        assertEquals(621, S.softCount());
        assertEquals(150, S.airCount());
        assertEquals(0, S.decor.size(), "no canopy: a centrally planned building's doors are its chamfer portals");
        assertEquals(2919, S.steps());
        ShapeKit.Summary sum = ShapeKit.summary(S, null);
        assertEquals(10, sum.units);
        assertEquals(9, sum.slots);
        assertTrue(sum.symmetric);
        assertTrue(S.asym.isEmpty());
        assertTrue(S.niche.isEmpty(), "CITY.md §4.15 gives the table no gate niche");
    }

    @Test
    void theBoxIsThirtyOneSquareAndSixteenHigh() {
        assertEquals(31, S.W);
        assertEquals(31, S.D);
        assertEquals(16, S.H);
        Blueprint b = HoloTableShape.BLUEPRINT;
        assertEquals(HoloTableShape.HEIGHT, b.height());
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            int[] c = HoloTableShape.cellOf(x, y, z);
            assertEquals(ShapeKit.soft(ch) ? ' ' : ch, b.at(c[0], c[1], c[2]), x + "," + y + "," + z);
        }
        for (int x = 0; x < 31; x++) for (int z = 0; z < 31; z++) {
            boolean in = ShapeKit.octo(x - 15, z - 15, 15, 20);
            char ch = S.get(x, 0, z);
            if (!in) assertEquals(' ', ch);
            else assertEquals(HoloTableShape.onEdge(x, z) ? 'F' : '.', ch, x + "," + z);
        }
    }

    /** The controller on its console at the table's front, six toward the blueprint's front from the middle. */
    @Test
    void theControllerAtTheTablesFront() {
        assertArrayEquals(new int[] { 15, 2, 9 }, S.controller);
        assertEquals('~', S.get(15, 2, 9));
        assertEquals('K', S.get(15, 1, 9));
        Blueprint b = HoloTableShape.BLUEPRINT;
        assertArrayEquals(HoloTableShape.cellOf(15, 2, 9), new int[] { b.ctrlA, b.ctrlB, b.ctrlC });
        assertEquals(-1, HoloTableShape.UNITS.unitOfCell(15, 1, 9), "the controller's console is no unit");
        // every other console stand is a layer console
        int stands = 0;
        for (int z = 0; z < 31; z++) for (int x = 0; x < 31; x++) if (S.get(x, 1, z) == 'K') {
            stands++;
            if (x != 15 || z != 9) assertTrue(HoloTableShape.UNITS.unitOfCell(x, 1, z) > 0, x + "," + z);
        }
        assertEquals(10, stands);
    }

    @Test
    void theFourPortals() {
        int air = 0;
        Set<Long> columns = new HashSet<>();
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            if (S.get(x, y, z) != '-') continue;
            assertTrue(y >= 1 && y <= 4, "door air at y" + y);
            assertTrue(HoloTableShape.inPortal(x, z));
            char lintel = S.get(x, 5, z);
            assertTrue(
                lintel == 'n' || lintel == 't' || lintel == 'W',
                "the lintel over " + x + "," + z + ": " + lintel);
            columns.add(ShapeKit.key(x, z));
            air++;
        }
        assertEquals(150, air);
        assertEquals(40, columns.size(), "four portals of ten columns");
        // the ramps rise through the back portals' inner columns: their panels and treads at y5 take four lintels in
        // each
        int lintels = 0, overhead = 0;
        for (long k : columns) {
            char ch = S.get(ShapeKit.keyX(k), 5, ShapeKit.keyZ(k));
            if (ch == 'n') lintels++;
            else {
                assertTrue(ShapeKit.keyZ(k) > 15, "in a back portal");
                overhead++;
            }
        }
        assertEquals(32, lintels);
        assertEquals(8, overhead);
        // the ramps start in the front portals' inner columns: their first panels and treads take the air at y1
        int ramp = 0;
        for (long k : columns) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k);
            char ch = S.get(x, 1, z);
            if (ch == '-') continue;
            assertTrue(ch == 't' || ch == 'W', "a ramp's start at " + x + "," + z);
            assertTrue(z < 15, "in a front portal");
            ramp++;
        }
        assertEquals(10, ramp);
    }

    /** The drum's walls and cornice, the dome's rings each only the ring it adds, the lantern and its cap. */
    @Test
    void theDrumTheDomeAndTheLantern() {
        int cornice = 0;
        for (int x = 0; x < 31; x++) for (int z = 0; z < 31; z++) if (HoloTableShape.onEdge(x, z)) {
            assertEquals('n', S.get(x, 10, z));
            cornice++;
        }
        assertEquals(120, cornice);
        assertEquals('G', S.get(0, 3, 13), "the glazed band");
        assertEquals('W', S.get(0, 8, 13));
        assertEquals('w', S.get(0, 1, 13));
        for (int[] a : new int[][] { { 15, 0 }, { 0, 15 }, { 30, 15 }, { 15, 30 } })
            assertEquals('A', S.get(a[0], 8, a[1]));
        // hollow: the dome's rings leave the middle open up to the lantern's cap at y15
        for (int y = 10; y <= 14; y++) assertEquals(' ', S.get(15, y, 15), "y" + y);
        assertEquals('L', S.get(15, 15, 15), "the cap's rib cross");
        assertEquals('G', S.get(16, 15, 13), "the cap's glass");
        assertEquals('W', S.get(14, 10, 2), "the first ring at y10");
        assertEquals('L', S.get(15, 10, 2), "a rib of it");
        assertEquals(' ', S.get(15, 10, 4), "inside the first ring");
        assertEquals('G', S.get(14, 14, 11), "the lantern's glass");
        assertEquals('L', S.get(15, 14, 11), "a lantern rib");
    }

    /** The round table at y1: plinths round a glass top, radius 4.5, one unit of nine slots without sockets. */
    @Test
    void theTableAndTheRingLight() {
        int plinths = 0, glass = 0, ring = 0;
        for (int x = 0; x < 31; x++) for (int z = 0; z < 31; z++) {
            double d = ShapeKit.hypot(x - 15, z - 15);
            char ch = S.get(x, 1, z);
            if (d <= 3.5) {
                assertEquals('G', ch);
                glass++;
            } else if (d <= 4.5) {
                assertEquals('p', ch);
                plinths++;
            }
            if (d >= 5.5 && d < 6.5) {
                assertEquals('R', S.get(x, HoloTableShape.RING_Y, z));
                ring++;
            } else assertTrue(S.get(x, HoloTableShape.RING_Y, z) != 'R');
        }
        assertEquals(37, glass);
        assertEquals(32, plinths);
        assertEquals(40, ring);
        UnitLayout.Unit t = HoloTableShape.UNITS.unit(0);
        assertEquals("table", t.name);
        assertEquals("table", t.kind);
        assertEquals(9, t.slots);
        assertEquals(69, t.cells.size());
        assertTrue(t.sockets.isEmpty(), "its slots show over the whole table");
        assertEquals(null, t.face);
        assertEquals("R", HoloTableShape.DISSOLVES);
        assertEquals(HoloTableShape.DISSOLVES, HoloTableShape.PASSES);
    }

    /** The nine layer consoles in the generator's order: units without slots, each a single console stand at y1. */
    @Test
    void theLayerConsoles() {
        UnitLayout u = HoloTableShape.UNITS;
        assertEquals(10, u.size());
        assertEquals(9, u.slots());
        int[][] at = { { 7, 0 }, { -7, 0 }, { 0, 7 }, { 5, 5 }, { -5, 5 }, { 5, -5 }, { -5, -5 }, { 3, -7 },
            { -3, -7 } };
        for (int i = 1; i <= 9; i++) {
            UnitLayout.Unit c = u.unit(i);
            assertEquals("layer-" + i, c.name);
            assertEquals("console", c.kind);
            assertEquals(0, c.slots, "a console keeps nothing");
            assertEquals(9, c.firstSlot, "after the table's nine");
            assertEquals(1, c.cells.size());
            assertArrayEquals(new int[] { 15 + at[i - 1][0], 1, 15 + at[i - 1][1] }, c.cells.get(0));
            assertEquals('K', S.get(15 + at[i - 1][0], 1, 15 + at[i - 1][1]));
            assertEquals(i, u.unitOfCell(15 + at[i - 1][0], 1, 15 + at[i - 1][1]));
        }
        for (int s = 0; s < 9; s++) assertEquals(0, u.unitOfSlot(s), "every slot is the table's");
        assertEquals(-1, u.unitOfSlot(9));
        assertArrayEquals(new int[] { 1 }, u.storeys(), "every unit stands on the floor");
        assertArrayEquals(new int[] { 7 }, HoloTableShape.planLevels(), "and the gallery is shown for the building");
    }

    /**
     * The double ramp: on each side a band of octagon radius 13..14 climbs from 30 degrees off the front to 155 in
     * half steps (panels at whole stands, treads at half ones) to the gallery's deck at y6 round the back; the rails
     * on radius 12 once the ramp is three up, and round the gallery at y7. Mirrored like everything checked.
     */
    @Test
    void theDoubleRampAndTheGallery() {
        int deck = 0, treads = 0, rails = 0;
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            int n = ShapeKit.onorm(x - 15, z - 15);
            if (ch == 'd') {
                deck++;
                assertEquals(HoloTableShape.GALLERY_Y, y);
                assertTrue(HoloTableShape.offFront(x, z) >= 155);
                assertTrue(n == 13 || n == 14);
            }
            if (ch == 't') {
                treads++;
                assertTrue(n == 13 || n == 14, "a tread on the ramp's band");
                assertTrue(y >= 1 && y <= 6);
            }
            if (ch == 'r') {
                rails++;
                assertEquals(12, n);
                assertTrue(y >= 3 && y <= 7);
            }
        }
        assertEquals(22, deck);
        assertEquals(50, treads);
        assertEquals(45, rails);
        assertEquals('d', S.get(15, 6, 28));
        assertEquals('r', S.get(15, 7, 27), "the gallery's rail");
        assertEquals(' ', S.get(15, 7, 28), "over the gallery's deck");
        assertEquals(0.0, HoloTableShape.offFront(15, 0), 1e-9);
        assertEquals(180.0, HoloTableShape.offFront(15, 30), 1e-9);
        assertEquals(90.0, HoloTableShape.offFront(0, 15), 1e-9);
    }

    @Test
    void everyCharacterIsALegendPart() {
        for (Map.Entry<Character, Integer> e : S.counts()
            .entrySet()) {
            char ch = e.getKey();
            if (ch == '~') continue;
            assertTrue(ShapeKit.partOf(ch) >= 0, "'" + ch + "'");
        }
        assertEquals(Parts.moduleCore(ModuleCores.HOLO_TABLE), HoloTableShape.BUILD.part(15, 2, 9, '~'));
        assertEquals(74, Parts.moduleCore(ModuleCores.HOLO_TABLE), "fluxecho:module_core meta 10");
        assertEquals(Parts.frame(Parts.FR_RING), ShapeKit.partOf('R'));
    }

    /** Every cell gets a stage, and the stages hold what CITY.md §4.11 lists. */
    @Test
    void everyCellHasItsStage() {
        Map<Integer, Integer> perStage = new HashMap<>();
        Set<Long> unitCells = new HashSet<>();
        for (UnitLayout.Unit u : HoloTableShape.UNITS.units())
            for (int[] c : u.cells) unitCells.add(ShapeKit.Shape.pos(c[0], c[1], c[2]));
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            if (ch == ' ' || unitCells.contains(ShapeKit.Shape.pos(x, y, z))) continue;
            int st = HoloTableShape.stage(x, y, z, ch);
            perStage.merge(st, 1, Integer::sum);
            if (ch == '~') assertEquals(BuildPlan.M_COMMISSION, st);
            if (ch == '-' || ch == 'F') assertEquals(BuildPlan.M_SHELL, st);
            if (y == 0 && ShapeKit.soft(ch)) assertEquals(BuildPlan.M_FLOOR, st);
            if (HoloTableShape.onEdge(x, z) && y >= 1) assertEquals(BuildPlan.M_SHELL, st, "the drum and cornice");
            if (!HoloTableShape.onEdge(x, z) && y >= 10) assertEquals(BuildPlan.M_ROOF, st, "the dome and lantern");
            if (ch == 'd' || ch == 'r' || ch == 't') assertEquals(BuildPlan.M_DECK, st);
            if (ch == 'K' || ch == 'R') assertEquals(BuildPlan.M_FIT, st);
            if (st == BuildPlan.M_FIT) assertTrue(HoloTableShape.fitRank(x, y, z, ch) > 0, "after the units");
        }
        for (int s : new int[] { BuildPlan.M_FLOOR, BuildPlan.M_SHELL, BuildPlan.M_DECK, BuildPlan.M_ROOF,
            BuildPlan.M_FIT, BuildPlan.M_COMMISSION }) assertNotNull(perStage.get(s), "stage " + s + " has cells");
        assertFalse(perStage.containsKey(BuildPlan.M_UPPER), "no upper walls");
        assertFalse(perStage.containsKey(BuildPlan.M_CLEAR));
        assertEquals(1, (int) perStage.get(BuildPlan.M_COMMISSION));
        assertEquals(41, (int) perStage.get(BuildPlan.M_FIT), "the controller's console and the ring light");
        assertEquals(621, (int) perStage.get(BuildPlan.M_FLOOR));
        assertTrue(HoloTableShape.fitRank(15, 1, 9, 'K') < HoloTableShape.fitRank(15, 9, 9, 'R'));
    }
}
