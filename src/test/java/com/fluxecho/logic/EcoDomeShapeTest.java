package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;

import org.junit.jupiter.api.Test;

/**
 * The Eco Dome's shape against its generator ({@code city/gen/b_hall.py}, {@code eco_dome()}): the same cells (a
 * checksum of every layer), the same counts, mirror-symmetric but for the trunk's spiral, the controller over its
 * stand, the door air, the planters, and a stage for every cell as CITY.md §4.1 lists them.
 */
class EcoDomeShapeTest {

    private static final ShapeKit.Shape S = EcoDomeShape.SHAPE;

    @Test
    void theCellsAreTheGenerators() {
        CRC32 crc = new CRC32();
        crc.update(
            String.join("\n", EcoDomeShape.rows())
                .getBytes(StandardCharsets.UTF_8));
        // zlib.crc32 of the generator's layers, rows from the front, y0 first, each row x 0..26
        // (2037949574 before 0.14.1 opened the crown deck over the spiral's fourth step; 798826595 before the gate
        // niche turned soft: the same blocks, other characters)
        assertEquals(913267591L, crc.getValue());
    }

    @Test
    void theGeneratorsCounts() {
        Map<Character, Integer> want = new TreeMap<>();
        Object[] pairs = { 'G', 720, '.', 573, 'W', 550, 'L', 443, 'd', 282, 'u', 172, ',', 161, 'n', 111, 'r', 104,
            'F', 102, 'w', 92, 'e', 70, '-', 48, 'B', 36, 'o', 36, '=', 28, 't', 17, 'C', 16, 'q', 14, 'A', 7, '/', 6,
            '[', 3, '!', 3, 'h', 2, 'K', 1, '~', 1, 'Z', 1 };
        for (int i = 0; i < pairs.length; i += 2) want.put((Character) pairs[i], (Integer) pairs[i + 1]);
        assertEquals(want, S.counts());
        assertEquals(2777, S.checkedCount());
        assertEquals(774, S.softCount());
        assertEquals(48, S.airCount());
        assertEquals(14, S.decor.size());
        assertEquals(3613, S.steps());
        ShapeKit.Summary sum = ShapeKit.summary(S, null);
        assertEquals(3613, sum.steps);
        assertEquals(12, sum.units);
        assertEquals(36, sum.slots);
    }

    @Test
    void theBoxIsTwentySevenByThirtyFiveAndTwentyOneHigh() {
        assertEquals(27, S.W);
        assertEquals(35, S.D);
        assertEquals(22, S.H, "the generator allots 22 layers");
        for (int x = 0; x < S.W; x++) for (int z = 0; z < S.D; z++) assertEquals(' ', S.get(x, 21, z));
        boolean top = false;
        for (int x = 0; x < S.W; x++) for (int z = 0; z < S.D; z++) top |= S.get(x, 20, z) != ' ';
        assertTrue(top, "the lantern reaches y20");
        Blueprint b = EcoDomeShape.BLUEPRINT;
        assertEquals(EcoDomeShape.HEIGHT, b.height());
        assertEquals(27, b.width());
        assertEquals(35, b.depth());
        for (int y = 0; y < EcoDomeShape.HEIGHT; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            int[] c = EcoDomeShape.cellOf(x, y, z);
            assertEquals(ShapeKit.soft(ch) ? ' ' : ch, b.at(c[0], c[1], c[2]), x + "," + y + "," + z);
        }
    }

    @Test
    void symmetricButForTheTrunksSpiral() {
        assertTrue(
            S.symmetric()
                .isEmpty());
        int[][] asym = { { 4, 2, 4 }, { 4, 3, 2 }, { 4, 3, 3 }, { 4, 6, 4 }, { 5, 2, 4 }, { 5, 4, 2 }, { 5, 6, 4 },
            { 6, 1, 3 }, { 6, 1, 4 }, { 6, 4, 2 }, { 6, 5, 3 }, { 6, 5, 4 }, { 12, 8, 22 }, { 12, 9, 20 },
            { 12, 9, 21 }, { 13, 8, 22 }, { 13, 10, 20 }, { 14, 7, 21 }, { 14, 7, 22 }, { 14, 10, 20 }, { 14, 11, 21 },
            { 14, 11, 22 }, { 20, 1, 3 }, { 20, 1, 4 }, { 20, 4, 2 }, { 20, 5, 3 }, { 20, 5, 4 }, { 21, 2, 4 },
            { 21, 4, 2 }, { 21, 6, 4 }, { 22, 2, 4 }, { 22, 3, 2 }, { 22, 3, 3 }, { 22, 6, 4 } };
        Set<Long> want = new HashSet<>();
        for (int[] c : asym) want.add(ShapeKit.Shape.pos(c[0], c[1], c[2]));
        assertEquals(want, S.asym);
        // the vestibule stairs mirror each other: only the ten steps round the trunk break the mirror
        int broken = 0;
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z), m = S.get(S.W - 1 - x, y, z);
            if (ch == ' ' || ShapeKit.soft(ch) || ShapeKit.soft(m)) continue;
            if (ch != m) {
                broken++;
                assertTrue(
                    EcoDomeShape.trunkSpiral(x, y, z) || EcoDomeShape.trunkSpiral(S.W - 1 - x, y, z),
                    "a broken mirror off the trunk at " + x + "," + y + "," + z);
            }
        }
        assertTrue(broken > 0 && broken <= 20, "broken " + broken);
    }

    @Test
    void theControllerAndThePortal() {
        assertArrayEquals(new int[] { 13, 2, 0 }, S.controller);
        assertEquals('~', S.get(13, 2, 0));
        assertEquals('K', S.get(13, 1, 0), "over its console stand");
        assertEquals('q', S.get(13, 3, 0));
        assertEquals('q', S.get(13, 4, 0));
        Blueprint b = EcoDomeShape.BLUEPRINT;
        assertArrayEquals(EcoDomeShape.cellOf(13, 2, 0), new int[] { b.ctrlA, b.ctrlB, b.ctrlC });
        int air = 0;
        for (int x = 10; x <= 16; x++) for (int y = 1; y <= 4; y++) for (int z = 0; z <= 1; z++) {
            if (x == 13) continue;
            assertEquals('-', S.get(x, y, z), "door " + x + "," + y + "," + z);
            air++;
        }
        assertEquals(48, air, "two doors 3 x 4, two deep");
        for (int x = 9; x <= 17; x++) assertEquals('n', S.get(x, 5, 0), "the lintel");
        // the canopy: ring segments two rows out at y5
        for (int[] d : EcoDomeShape.decor()) {
            assertEquals(5, d[1]);
            assertTrue(d[2] == -1 || d[2] == -2);
            assertEquals(Parts.frame(Parts.FR_RING), d[3]);
        }
    }

    /**
     * The gate niche (内境门位, CITY.md §4.15) in the back drum wall's middle, x 12..14, y1..4, z 34: its twelve cells
     * are soft (left out of the blueprint the structure check uses) yet hold the same blocks as the wall round them
     * (the dark course at y1, the glass either side of the back rib above), so a later release opens it for the 内境
     * gate without touching the checked shape.
     */
    @Test
    void theGateNicheIsSoftAndStillBuilt() {
        Set<Long> want = new HashSet<>();
        for (int x = 12; x <= 14; x++) for (int y = 1; y <= 4; y++) want.add(ShapeKit.Shape.pos(x, y, 34));
        assertEquals(want, S.niche);
        Blueprint b = EcoDomeShape.BLUEPRINT;
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            boolean niche = want.contains(ShapeKit.Shape.pos(x, y, z));
            assertEquals(niche, EcoDomeShape.inNiche(x, y, z), x + "," + y + "," + z);
            if (!niche) continue;
            assertTrue(ShapeKit.soft(ch), "soft at " + x + "," + y + "," + z);
            assertFalse(ShapeKit.checked(ch));
            int[] c = EcoDomeShape.cellOf(x, y, z);
            assertEquals(' ', b.at(c[0], c[1], c[2]), "not checked at " + x + "," + y + "," + z);
            // the wall it stands in: the dark course, then the back rib between two panes
            char wall = y == 1 ? 'w' : x == 13 ? 'L' : 'G';
            assertEquals(wall, ShapeKit.checkedTwin(ch));
            assertEquals(ShapeKit.partOf(wall), ShapeKit.partOf(ch), "the same block at " + x + "," + y + "," + z);
            assertEquals(BuildPlan.M_SHELL, EcoDomeShape.stage(x, y, z, ch), "built with the drum's wall");
        }
        // the wall round it stays checked: the course and the glass beside it, the rib's anchor and the glass over it
        for (int y = 1; y <= 4; y++) for (int x : new int[] { 11, 15 }) {
            assertEquals(y == 1 ? 'w' : 'G', S.get(x, y, 34), x + "," + y);
            assertEquals(S.get(x, y, 34), b.at(x, EcoDomeShape.HEIGHT - 1 - y, 34));
        }
        assertEquals('A', S.get(13, 5, 34));
        assertEquals('G', S.get(12, 5, 34));
        assertEquals('G', S.get(14, 5, 34));
        // none of it is a planter's
        for (UnitLayout.Unit u : EcoDomeShape.UNITS.units())
            for (int[] c : u.cells) assertFalse(EcoDomeShape.inNiche(c[0], c[1], c[2]), u.name);
    }

    @Test
    void theTreeIsATrunkAndASeat() {
        for (int y = 1; y <= 16; y++) assertEquals('C', S.get(13, y, 21));
        assertEquals('Z', S.get(13, 17, 21));
        assertEquals(
            16,
            S.counts()
                .get('C'));
    }

    @Test
    void everyCharacterIsALegendPart() {
        for (Map.Entry<Character, Integer> e : S.counts()
            .entrySet()) {
            char ch = e.getKey();
            if (ch == '~') continue;
            assertTrue(ShapeKit.partOf(ch) >= 0, "'" + ch + "'");
        }
        assertEquals(Parts.WATER, ShapeKit.partOf('='));
        assertEquals(Parts.GRASS, ShapeKit.partOf(','));
        assertEquals(Parts.fitting(Parts.F_SOCKET), ShapeKit.partOf('o'));
        assertEquals(Parts.fitting(Parts.F_BASIN), ShapeKit.partOf('u'));
        assertEquals(Parts.frame(Parts.FR_ANCHOR), ShapeKit.partOf('A'));
        assertEquals(Parts.moduleCore(ModuleCores.ECO_DOME), EcoDomeShape.BUILD.part(13, 2, 0, '~'));
    }

    @Test
    void thePlanters() {
        UnitLayout u = EcoDomeShape.UNITS;
        String[] names = { "G-RB", "G-RF", "G-R", "G-LB", "G-LF", "G-L", "G-B", "I-F", "I-B", "U-R", "U-L", "C" };
        int[][] first = { { 19, 1, 26 }, { 19, 1, 14 }, { 22, 1, 20 }, { 7, 1, 26 }, { 7, 1, 14 }, { 4, 1, 20 },
            { 12, 1, 30 }, { 12, 7, 18 }, { 12, 7, 24 }, { 23, 7, 25 }, { 3, 7, 25 }, { 12, 12, 23 } };
        assertEquals(12, u.size());
        assertEquals(36, u.slots());
        for (int i = 0; i < 12; i++) {
            UnitLayout.Unit p = u.unit(i);
            assertEquals(names[i], p.name);
            assertEquals("planter", p.kind);
            // the gallery planters leave the drum's cornice where their rim would reach the wall
            int bed = p.name.startsWith("U-") ? 13 : 15;
            assertEquals(bed, p.cells.size(), p.name);
            assertEquals(3, p.slots);
            assertEquals(3, p.sockets.size());
            assertEquals(3 * i, p.firstSlot);
            assertArrayEquals(first[i], p.sockets.get(0));
            int sockets = 0, rims = 0;
            for (int[] c : p.cells) {
                char ch = S.get(c[0], c[1], c[2]);
                if (ch == 'o') sockets++;
                if (ch == 'u') rims++;
            }
            assertEquals(3, sockets);
            assertEquals(bed - 3, rims);
        }
        assertArrayEquals(new int[] { 1, 7, 12 }, u.storeys(), "the ground, the deck and the crown island");
    }

    /**
     * The drum's cornice band (y7) runs whole round the dome: no planter's basin rim takes a cell of it, which would
     * leave a slit between the rim's slab and the glass over it.
     */
    @Test
    void theDrumsCorniceIsWhole() {
        int band = 0;
        for (int x = 0; x < S.W; x++) for (int z = 0; z < S.D; z++) {
            if (!EcoDomeShape.onEdge(x, z) || EcoDomeShape.blockWall(x, z)) continue;
            assertEquals('n', S.get(x, 7, z), "the cornice at " + x + "," + z);
            band++;
        }
        assertEquals(55, band, "the band runs round the drum");
        for (UnitLayout.Unit u : EcoDomeShape.UNITS.units())
            for (int[] c : u.cells) assertFalse(EcoDomeShape.onEdge(c[0], c[2]), u.name + " on the wall");
    }

    /** Every cell gets a stage, and the stages hold what CITY.md §4.1 lists. */
    @Test
    void everyCellHasItsStage() {
        Map<Integer, Integer> perStage = new HashMap<>();
        Set<Long> unitCells = new HashSet<>();
        for (UnitLayout.Unit u : EcoDomeShape.UNITS.units())
            for (int[] c : u.cells) unitCells.add(ShapeKit.Shape.pos(c[0], c[1], c[2]));
        for (int y = 0; y < EcoDomeShape.HEIGHT; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            if (ch == ' ' || unitCells.contains(ShapeKit.Shape.pos(x, y, z))) continue;
            int st = EcoDomeShape.stage(x, y, z, ch);
            perStage.merge(st, 1, Integer::sum);
            if (ch == '~') assertEquals(BuildPlan.M_COMMISSION, st);
            if (ch == '-') assertEquals(BuildPlan.M_SHELL, st);
            if (ch == 'F') assertEquals(BuildPlan.M_SHELL, st, "the foundations open the shell");
            if (ch == '.') assertEquals(BuildPlan.M_FLOOR, st);
            if (ch == ',' && y == 0) assertEquals(BuildPlan.M_FLOOR, st, "the garden's grass");
            if (ch == ',' && y == EcoDomeShape.DECK_Y) assertEquals(BuildPlan.M_DECK, st, "the island's grass");
            if (ch == ',' && y == EcoDomeShape.CROWN_Y) assertEquals(BuildPlan.M_FIT, st, "the crown island");
            if (ch == 'C' || ch == 'Z' || ch == 'A' || ch == 'h' || ch == '=') assertEquals(BuildPlan.M_FIT, st);
            if (ch == 'u') assertEquals(BuildPlan.M_FIT, st, "the pond's rim");
            if (EcoDomeShape.trunkSpiral(x, y, z)) assertEquals(BuildPlan.M_FIT, st);
            if (EcoDomeShape.vestibuleStair(x, y, z)) assertEquals(BuildPlan.M_DECK, st);
            if (ch == 'G' && y >= 8 && !EcoDomeShape.blockWall(x, z)) assertEquals(BuildPlan.M_ROOF, st, "the dome");
            if (EcoDomeShape.onEdge(x, z) && y >= 1 && y <= 7 && "~ACZhu=".indexOf(ch) < 0)
                assertEquals(BuildPlan.M_SHELL, st, "the drum's wall at " + x + "," + y + "," + z);
            if (EcoDomeShape.blockWall(x, z) && y >= 8 && y <= 12) assertEquals(BuildPlan.M_UPPER, st);
            if (st == BuildPlan.M_FIT) assertTrue(EcoDomeShape.fitRank(x, y, z, ch) != 0);
        }
        for (int s = BuildPlan.M_FLOOR; s < BuildPlan.M_STAGES; s++) {
            if (s == BuildPlan.M_DECOR) continue;
            assertNotNull(perStage.get(s), "stage " + s + " has cells");
        }
        assertFalse(perStage.containsKey(BuildPlan.M_CLEAR));
        assertEquals(1, (int) perStage.get(BuildPlan.M_COMMISSION));
    }

    @Test
    void theFitStageRanksTheTreeFirstAndTheWaterAfterItsRim() {
        assertTrue(EcoDomeShape.fitRank(13, 5, 21, 'C') < 0);
        assertTrue(EcoDomeShape.fitRank(13, 5, 21, 'C') < EcoDomeShape.fitRank(14, 7, 21, 't'));
        int rim = EcoDomeShape.fitRank(0, 1, 0, 'u'), water = EcoDomeShape.fitRank(0, 1, 0, '=');
        assertTrue(rim > 0 && water > rim, "water only once its rim stands");
        List<int[]> starts = EcoDomeShape.walkStarts();
        assertEquals(2, starts.size());
    }
}
