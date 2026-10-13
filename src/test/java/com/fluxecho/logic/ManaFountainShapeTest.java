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
 * The Mana Fountain Array's shape against its generator ({@code city/gen/b_pav.py}, {@code mana_fountain()}): the same
 * cells (a checksum of every layer of the generator's nine), the same counts, mirror-symmetric, the controller on the
 * obelisk's console with the conduits and the seat over it, the four portals' air, the cloister (the glass roof, the
 * colonnade, the open court), the eight fountains with their water and the eight urns in slot order, the gate niche
 * soft but built, and a stage for every cell as CITY.md §4.6 lists them, the water last.
 */
class ManaFountainShapeTest {

    private static final ShapeKit.Shape S = ManaFountainShape.SHAPE;

    @Test
    void theCellsAreTheGenerators() {
        CRC32 crc = new CRC32();
        crc.update(
            String.join("\n", ShapeKit.layers(S))
                .getBytes(StandardCharsets.UTF_8));
        // zlib.crc32 of the generator's nine layers, y0 first, rows from the front, each row x 0..24
        assertEquals(4155736375L, crc.getValue());
    }

    @Test
    void theGeneratorsCounts() {
        Map<Character, Integer> want = new TreeMap<>();
        Object[] pairs = { '.', 232, 'W', 198, 'L', 173, '-', 160, 'q', 160, 'G', 156, 'n', 152, 'F', 96, ',', 80, '+',
            73, 'w', 73, 'u', 64, 'o', 16, '=', 8, 'p', 8, 'A', 4, '/', 4, 'C', 4, '[', 3, '!', 3, '{', 2, 'K', 1, '~',
            1, 'Z', 1 };
        for (int i = 0; i < pairs.length; i += 2) want.put((Character) pairs[i], (Integer) pairs[i + 1]);
        assertEquals(want, S.counts());
        assertEquals(1107, S.checkedCount());
        assertEquals(405, S.softCount());
        assertEquals(160, S.airCount());
        assertEquals(0, S.decor.size(), "no canopy: its doors are its chamfer portals");
        assertEquals(1672, S.steps());
        ShapeKit.Summary sum = ShapeKit.summary(S, null);
        assertEquals(1672, sum.steps);
        assertEquals(16, sum.units);
        assertEquals(16, sum.slots);
        assertTrue(sum.symmetric);
    }

    /** The generator's box is nine high but its top layer is empty, so the blueprint is eight high (y0..y7). */
    @Test
    void theBoxIsTwentyFiveSquareAndEightHigh() {
        assertEquals(25, S.W);
        assertEquals(25, S.D);
        assertEquals(ManaFountainShape.GEN_HEIGHT, S.H);
        assertEquals(8, ManaFountainShape.HEIGHT);
        for (int x = 0; x < 25; x++) for (int z = 0; z < 25; z++) assertEquals(' ', S.get(x, 8, z));
        Blueprint b = ManaFountainShape.BLUEPRINT;
        assertEquals(8, b.height());
        assertEquals(25, b.width());
        assertEquals(25, b.depth());
        for (int y = 0; y < ManaFountainShape.HEIGHT; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            int[] c = ManaFountainShape.cellOf(x, y, z);
            assertEquals(ShapeKit.soft(ch) ? ' ' : ch, b.at(c[0], c[1], c[2]), x + "," + y + "," + z);
            assertEquals(ch, ManaFountainShape.cell(x, y, z));
            assertEquals(ch, ManaFountainShape.BUILD.cell(x, y, z));
        }
        assertEquals(' ', ManaFountainShape.BUILD.cell(12, 8, 12));
    }

    @Test
    void symmetricAcrossX() {
        assertTrue(
            S.symmetric()
                .isEmpty());
        assertTrue(S.asym.isEmpty());
    }

    /** The obelisk: the console, the controller at eye height, four conduits and the seat that dissolve. */
    @Test
    void theControllerOnTheObelisk() {
        assertArrayEquals(new int[] { 12, 2, 12 }, S.controller);
        assertEquals('K', S.get(12, 1, 12));
        assertEquals('~', S.get(12, 2, 12));
        for (int y = ManaFountainShape.OBELISK_FROM; y < ManaFountainShape.OBELISK_TO; y++)
            assertEquals('C', S.get(12, y, 12), "a conduit at y" + y);
        assertEquals('Z', S.get(12, ManaFountainShape.OBELISK_TO, 12));
        Blueprint b = ManaFountainShape.BLUEPRINT;
        assertArrayEquals(ManaFountainShape.cellOf(12, 2, 12), new int[] { b.ctrlA, b.ctrlB, b.ctrlC });
        assertEquals(-1, ManaFountainShape.UNITS.unitOfCell(12, 1, 12));
    }

    @Test
    void theFourPortals() {
        int air = 0;
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            if (S.get(x, y, z) != '-') continue;
            assertTrue(y >= 1 && y <= 4, "door air at y" + y);
            assertTrue(ManaFountainShape.inPortal(x, z));
            assertEquals('n', S.get(x, 5, z), "the lintel over " + x + "," + z);
            air++;
        }
        assertEquals(160, air, "four portals of 40 cells, two deep");
        for (int[] c : new int[][] { { 4, 4 }, { 20, 4 }, { 4, 20 }, { 20, 20 } }) {
            for (int y = 1; y <= 4; y++) assertEquals('-', S.get(c[0], y, c[1]));
        }
    }

    /**
     * The cloister: the glass roof at y7 over the arcade ring (oct(12, 16) less oct(9, 12)), its outer ring a cornice
     * and its ribs on the eight lines; the colonnade on the oct(9, 12) line, an architrave at y6 over a post every
     * other cell, open by the diagonals; the open court oct(8, 11) with nothing over it but the obelisk; grass in the
     * arcade ring off the axes and the diagonals; the magic circle in the court.
     */
    @Test
    void theCloister() {
        Map<Character, Integer> roof = new TreeMap<>();
        int colonnade = 0, posts = 0, grass = 0;
        for (int x = 0; x < 25; x++) for (int z = 0; z < 25; z++) {
            int du = x - 12, dv = z - 12;
            boolean arcade = ShapeKit.octo(du, dv, 12, 16) && !ShapeKit.octo(du, dv, 9, 12);
            boolean col = ManaFountainShape.onColonnade(x, z);
            assertEquals(ShapeKit.octo(du, dv, 9, 12) && !ShapeKit.octo(du, dv, 8, 11), col);
            char top = S.get(x, 7, z);
            if (arcade) {
                roof.merge(top, 1, Integer::sum);
                boolean rib = du == 0 || dv == 0 || Math.abs(du) == Math.abs(dv);
                assertEquals(!ShapeKit.octo(du, dv, 11, 15) ? 'n' : rib ? 'W' : 'G', top, x + "," + z);
            } else if (x != 12 || z != 12) assertEquals(' ', top, "open over the court at " + x + "," + z);
            if (col) {
                assertEquals('n', S.get(x, 6, z), "the architrave");
                colonnade++;
                if (S.get(x, 1, z) == 'q') {
                    posts++;
                    for (int y = 1; y <= 5; y++) assertEquals('q', S.get(x, y, z));
                    assertTrue((du + dv) % 2 == 0 && Math.abs(Math.abs(du) - Math.abs(dv)) > 1);
                }
            } else if (ShapeKit.octo(du, dv, 8, 11) && (x != 12 || z != 12))
                assertEquals(' ', S.get(x, 6, z), "the court is open");
            if (S.get(x, 0, z) == ',') {
                grass++;
                assertTrue(ShapeKit.onorm(du, dv) >= 10 && du != 0 && dv != 0);
            }
        }
        Map<Character, Integer> want = new TreeMap<>();
        want.put('G', 128);
        want.put('n', 64);
        want.put('W', 12);
        assertEquals(want, roof);
        assertEquals(48, colonnade);
        assertEquals(32, posts);
        assertEquals(80, grass);
        assertEquals('+', S.get(12, 0, 4), "the circle's axis");
        assertEquals('+', S.get(12, 0, 10), "the inner circle");
        assertEquals(ManaFountainShape.ROOF_Y, ManaFountainShape.HEIGHT - 1);
    }

    /**
     * The gate niche (内境门位, CITY.md §4.15) in the back flat face's middle, x 11..13, y1..4, z 24: its twelve cells
     * are soft (left out of the blueprint the structure check uses) yet hold the same blocks as the wall round them
     * (the dark course at y1, the lit pilaster at x 12, a panel at y2 and glass above beside it), built with the wall.
     */
    @Test
    void theGateNicheIsSoftAndStillBuilt() {
        Set<Long> want = new HashSet<>();
        for (int x = 11; x <= 13; x++) for (int y = 1; y <= 4; y++) want.add(ShapeKit.Shape.pos(x, y, 24));
        assertEquals(want, S.niche);
        Blueprint b = ManaFountainShape.BLUEPRINT;
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            boolean niche = want.contains(ShapeKit.Shape.pos(x, y, z));
            assertEquals(niche, ManaFountainShape.inNiche(x, y, z), x + "," + y + "," + z);
            if (!niche) continue;
            assertTrue(ShapeKit.soft(ch), "soft at " + x + "," + y + "," + z);
            int[] c = ManaFountainShape.cellOf(x, y, z);
            assertEquals(' ', b.at(c[0], c[1], c[2]), "not checked at " + x + "," + y + "," + z);
            char wall = y == 1 ? 'w' : x == 12 ? 'L' : y == 2 ? 'W' : 'G';
            assertEquals(wall, ShapeKit.checkedTwin(ch));
            assertEquals(ShapeKit.partOf(wall), ShapeKit.partOf(ch), "the same block at " + x + "," + y + "," + z);
            assertEquals(BuildPlan.M_SHELL, ManaFountainShape.stage(x, y, z, ch), "built with the wall");
        }
        for (int y = 1; y <= 4; y++) for (int x : new int[] { 10, 14 }) {
            char ch = S.get(x, y, 24);
            assertTrue(ShapeKit.checked(ch), x + "," + y);
            assertEquals(ch, b.at(x, ManaFountainShape.HEIGHT - 1 - y, 24));
        }
        for (UnitLayout.Unit u : ManaFountainShape.UNITS.units())
            for (int[] c : u.cells) assertFalse(ManaFountainShape.inNiche(c[0], c[1], c[2]), u.name);
    }

    @Test
    void everyCharacterIsALegendPart() {
        for (Map.Entry<Character, Integer> e : S.counts()
            .entrySet()) {
            char ch = e.getKey();
            if (ch == '~') continue;
            assertTrue(ShapeKit.partOf(ch) >= 0, "'" + ch + "'");
        }
        assertEquals(Parts.moduleCore(ModuleCores.MANA_FOUNTAIN), ManaFountainShape.BUILD.part(12, 2, 12, '~'));
        assertEquals(Parts.WATER, ShapeKit.partOf('='));
        assertEquals(Parts.fitting(Parts.F_POST), ShapeKit.partOf('q'));
        assertEquals(Parts.fitting(Parts.F_BASIN), ShapeKit.partOf('u'));
    }

    /**
     * The 16 units in the generator's order, which is the slot order for good: eight fountains (a basin rim of eight
     * round the water, the socket over the water last), then eight flower urns (a plinth and a socket).
     */
    @Test
    void theUnits() {
        UnitLayout u = ManaFountainShape.UNITS;
        assertEquals(16, u.size());
        assertEquals(16, u.slots());
        int[][] fountains = { { 18, 12 }, { 6, 12 }, { 12, 18 }, { 12, 6 }, { 16, 16 }, { 8, 16 }, { 16, 8 },
            { 8, 8 } };
        int[][] urns = { { 20, 14 }, { 20, 10 }, { 4, 14 }, { 4, 10 }, { 14, 20 }, { 10, 20 }, { 14, 4 }, { 10, 4 } };
        for (int i = 0; i < 16; i++) {
            UnitLayout.Unit p = u.unit(i);
            assertEquals(1, p.slots);
            assertEquals(i, p.firstSlot);
            assertEquals(1, p.sockets.size());
            assertEquals(1, p.floor);
            if (i < 8) {
                int[] f = fountains[i];
                assertEquals("fountain-" + (i + 1), p.name);
                assertEquals("fountain", p.kind);
                assertEquals(9, p.cells.size());
                assertArrayEquals(new int[] { f[0] - 1, 1, f[1] - 1 }, p.cells.get(0));
                assertArrayEquals(new int[] { f[0], 2, f[1] }, p.cells.get(8));
                assertArrayEquals(new int[] { f[0], 2, f[1] }, p.sockets.get(0));
                assertEquals('=', S.get(f[0], 1, f[1]), "the water under the socket");
                assertEquals(-1, u.unitOfCell(f[0], 1, f[1]), "the water is no unit's");
                for (int k = 0; k < 8; k++) assertEquals('u', S.get(p.cells.get(k)[0], 1, p.cells.get(k)[2]));
            } else {
                int[] at = urns[i - 8];
                assertEquals("urn-" + (i - 7), p.name);
                assertEquals("urn", p.kind);
                assertEquals(2, p.cells.size());
                assertArrayEquals(new int[] { at[0], 1, at[1] }, p.cells.get(0));
                assertEquals('p', S.get(at[0], 1, at[1]));
                assertArrayEquals(new int[] { at[0], 2, at[1] }, p.sockets.get(0));
                assertTrue(ShapeKit.octo(at[0] - 12, at[1] - 12, 8, 11), "inside the colonnade");
            }
            for (int[] c : p.cells) assertTrue(ShapeKit.checked(S.get(c[0], c[1], c[2])), p.name + " checked");
        }
        assertArrayEquals(new int[] { 1 }, u.storeys(), "everything stands in the court");
    }

    /** Every cell gets a stage, and the stages hold what CITY.md §4.6 lists: the water last in FIT. */
    @Test
    void everyCellHasItsStage() {
        Map<Integer, Integer> perStage = new HashMap<>();
        Set<Long> unitCells = new HashSet<>();
        for (UnitLayout.Unit u : ManaFountainShape.UNITS.units())
            for (int[] c : u.cells) unitCells.add(ShapeKit.Shape.pos(c[0], c[1], c[2]));
        for (int y = 0; y < ManaFountainShape.HEIGHT; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            if (ch == ' ' || unitCells.contains(ShapeKit.Shape.pos(x, y, z))) continue;
            int st = ManaFountainShape.stage(x, y, z, ch);
            perStage.merge(st, 1, Integer::sum);
            if (ch == '~') assertEquals(BuildPlan.M_COMMISSION, st);
            else if (ch == 'K' || ch == 'C' || ch == 'Z' || ch == '=')
                assertEquals(BuildPlan.M_FIT, st, "the obelisk and the water");
            else if (y == 0) assertEquals(ShapeKit.soft(ch) ? BuildPlan.M_FLOOR : BuildPlan.M_SHELL, st);
            else if (y == 7) assertEquals(BuildPlan.M_ROOF, st, "the glass roof at " + x + "," + z);
            else if (ManaFountainShape.onColonnade(x, z)) assertEquals(BuildPlan.M_DECK, st, "the colonnade");
            else assertEquals(BuildPlan.M_SHELL, st, "the outer wall at " + x + "," + y + "," + z);
            if (st == BuildPlan.M_FIT) {
                int rank = ManaFountainShape.fitRank(x, y, z, ch);
                assertTrue(ch == '=' ? rank > 0 : rank < 0, "the obelisk before the units, the water after them");
            }
        }
        for (int s : new int[] { BuildPlan.M_FLOOR, BuildPlan.M_SHELL, BuildPlan.M_DECK, BuildPlan.M_ROOF,
            BuildPlan.M_FIT, BuildPlan.M_COMMISSION }) assertNotNull(perStage.get(s), "stage " + s + " has cells");
        assertFalse(perStage.containsKey(BuildPlan.M_UPPER), "no upper walls");
        assertFalse(perStage.containsKey(BuildPlan.M_CLEAR));
        assertEquals(1, (int) perStage.get(BuildPlan.M_COMMISSION));
        assertEquals(1 + 4 + 1 + 8, (int) perStage.get(BuildPlan.M_FIT), "the obelisk and the eight waters");
        assertEquals(160 + 48, (int) perStage.get(BuildPlan.M_DECK), "the posts and the architrave");
        assertEquals(128 + 64 + 12, (int) perStage.get(BuildPlan.M_ROOF), "the glass roof");
    }
}
