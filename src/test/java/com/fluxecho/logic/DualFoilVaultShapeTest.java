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
 * The Dual-foil Vault's shape against its generator ({@code city/gen/b_hall.py}, {@code vault()}): the same cells (a
 * checksum of every layer), the same counts, mirror-symmetric (its two corner stairs too), the controller over its
 * stand in the kit portal, the door air, the facade canvas and the sawtooth roof, the catwalks, the cross walk and the
 * 32-step spirals, the 43 units in slot order (42 canvases, then the foil), the gate niche in the foil (soft canvas,
 * built with its wall, in no unit), and a stage for every cell as CITY.md §4.2 lists them.
 */
class DualFoilVaultShapeTest {

    private static final ShapeKit.Shape S = DualFoilVaultShape.SHAPE;

    @Test
    void theCellsAreTheGenerators() {
        CRC32 crc = new CRC32();
        crc.update(
            String.join("\n", ShapeKit.layers(S))
                .getBytes(StandardCharsets.UTF_8));
        // zlib.crc32 of the generator's 27 layers, y0 first, rows from the front, each row x 0..26 (the niche's $ in)
        assertEquals(4211763960L, crc.getValue());
    }

    @Test
    void theGeneratorsCounts() {
        Map<Character, Integer> want = new TreeMap<>();
        Object[] pairs = { 'W', 1717, 'Q', 839, '.', 825, 'L', 417, 'd', 330, 'q', 322, 'e', 159, 'r', 153, 'n', 129,
            'p', 126, 'c', 126, 'F', 120, 'G', 114, 'w', 51, '-', 48, 't', 32, '$', 12, 'A', 4, 'K', 2, 'h', 2, '~',
            1 };
        for (int i = 0; i < pairs.length; i += 2) want.put((Character) pairs[i], (Integer) pairs[i + 1]);
        assertEquals(want, S.counts());
        assertEquals(4644, S.checkedCount());
        assertEquals(837, S.softCount());
        assertEquals(48, S.airCount());
        assertEquals(14, S.decor.size(), "the canopy");
        assertEquals(5543, S.steps());
        ShapeKit.Summary sum = ShapeKit.summary(S, null);
        assertEquals(5543, sum.steps);
        assertEquals(43, sum.units);
        assertEquals(43, sum.slots);
        assertTrue(sum.symmetric);
    }

    /** 27 across, 35 deep and 27 high, the skylights' ridges in its top layer; the blueprint is the box itself. */
    @Test
    void theBoxIsTwentySevenByThirtyFiveAndTwentySevenHigh() {
        assertEquals(27, S.W);
        assertEquals(35, S.D);
        assertEquals(27, S.H);
        assertEquals(DualFoilVaultShape.HEIGHT, S.H);
        assertEquals(13, S.MID);
        Blueprint b = DualFoilVaultShape.BLUEPRINT;
        assertEquals(27, b.height());
        assertEquals(27, b.width());
        assertEquals(35, b.depth());
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            int[] c = DualFoilVaultShape.cellOf(x, y, z);
            assertEquals(ShapeKit.soft(ch) ? ' ' : ch, b.at(c[0], c[1], c[2]), x + "," + y + "," + z);
        }
        assertArrayEquals(DualFoilVaultShape.cellOf(13, 2, 0), new int[] { b.ctrlA, b.ctrlB, b.ctrlC });
    }

    @Test
    void symmetricAcrossTheNaveTheStairsToo() {
        assertTrue(
            S.symmetric()
                .isEmpty());
        assertEquals(64, S.asym.size(), "two spirals of 32 steps");
        for (long k : S.asym) {
            int x = (int) (k >>> 40), z = (int) (k & 0xFFFFF);
            boolean left = Math.abs(x - 2) <= 1, right = Math.abs(x - 24) <= 1;
            assertTrue((left || right) && Math.abs(z - 2) <= 1, "a step outside the wells at " + x + "," + z);
        }
        // the two stairs turn opposite ways, so each is the other's mirror image: nothing breaks the mirror, though the
        // generator lists their steps as asymmetric as it does every spiral's
        java.util.List<String> broken = new java.util.ArrayList<>();
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z), m = S.get(S.W - 1 - x, y, z);
            if (ch != ' ' && !ShapeKit.soft(ch) && !ShapeKit.soft(m) && ch != m) broken.add(x + "," + y + "," + z);
        }
        assertTrue(broken.isEmpty(), "a broken mirror at " + broken);
        for (int[] st : ShapeKit.spiral(2, 2, 1, 32, 0, 1))
            assertTrue(DualFoilVaultShape.onStair(26 - st[0], st[1], st[2]), "the right stair mirrors the left");
    }

    @Test
    void theControllerAndThePortal() {
        assertArrayEquals(new int[] { 13, 2, 0 }, S.controller);
        assertEquals('~', S.get(13, 2, 0));
        assertEquals('K', S.get(13, 1, 0), "over its console stand");
        assertEquals('q', S.get(13, 3, 0));
        assertEquals('q', S.get(13, 4, 0));
        int air = 0;
        for (int x = 10; x <= 16; x++) for (int y = 1; y <= 4; y++) for (int z = 0; z <= 1; z++) {
            if (x == 13) continue;
            assertEquals('-', S.get(x, y, z), "door " + x + "," + y + "," + z);
            air++;
        }
        assertEquals(48, air, "two doors 3 x 4, two deep");
        for (int x = 9; x <= 17; x++) assertEquals('n', S.get(x, 5, 0), "the lintel");
        assertEquals(14, S.decor.size());
        for (int[] d : S.decor) {
            assertEquals(5, d[1]);
            assertTrue(d[2] == -1 || d[2] == -2);
            assertEquals('R', (char) d[3], "the canopy's ring segments");
        }
        // the desk under the foil: a second console stand between two holo pedestals
        assertEquals('K', S.get(13, 1, 30));
        assertEquals('h', S.get(10, 1, 30));
        assertEquals('h', S.get(16, 1, 30));
    }

    /**
     * The monolith: walls y1..23 round the box with a dark course at y1, lit corners and lit pilasters every four along
     * the long walls and every five across; the cornice band at y24; the roof at y25 with its six skylight strips under
     * the ridges at y26; the facade canvas over the doors in its lit frame, an anchor at each corner.
     */
    @Test
    void theMonolithItsFacadeAndItsRoof() {
        for (int x = 0; x < S.W; x++) for (int z = 0; z < S.D; z++) {
            if (!DualFoilVaultShape.onEdge(x, z)) continue;
            assertEquals('F', S.get(x, 0, z));
            assertEquals('n', S.get(x, 24, z), "the cornice band");
            boolean corner = (x == 0 || x == 26) && (z == 0 || z == 34);
            if (corner) for (int y = 1; y <= 23; y++) assertEquals('L', S.get(x, y, z), "a lit corner");
            for (int y = 25; y < 27; y++) assertEquals(' ', S.get(x, y, z), "nothing over the cornice");
        }
        for (int z : new int[] { 7, 15, 23, 31 }) assertEquals('W', S.get(0, 7, z));
        for (int z : new int[] { 4, 8, 12, 16 }) assertEquals('L', S.get(0, 7, z), "a pilaster every four");
        for (int x : new int[] { 3, 8, 13, 18, 23 }) assertEquals('L', S.get(x, 22, 34), "every five across");
        int sky = 0, ridge = 0;
        for (int x = 1; x < 26; x++) for (int z = 1; z < 34; z++) {
            boolean strip = z % 6 == 3 && x >= 4 && x <= 22;
            assertEquals(strip, DualFoilVaultShape.skylight(x, z));
            assertEquals(strip ? 'G' : 'W', S.get(x, 25, z));
            if (strip) sky++;
            if (S.get(x, 26, z) != ' ') {
                assertTrue(strip, "a ridge over a skylight");
                assertEquals('W', S.get(x, 26, z));
                ridge++;
            }
        }
        assertEquals(6 * 19, sky, "six strips");
        assertEquals(sky, ridge);
        int canvas = 0;
        for (int x = 4; x <= 22; x++) for (int y = 7; y <= 20; y++) {
            boolean frame = x == 4 || x == 22 || y == 7 || y == 20;
            boolean corner = (x == 4 || x == 22) && (y == 7 || y == 20);
            assertEquals(corner ? 'A' : frame ? 'L' : 'Q', S.get(x, y, 0), x + "," + y);
            assertEquals(!frame, DualFoilVaultShape.inFacade(x, y));
            if (!frame) canvas++;
        }
        assertEquals(17 * 12, canvas, "the facade canvas, 17 x 12");
    }

    /**
     * The catwalks along both long walls at deck y8 and y16 (three wide, round the stair wells), their lit edges and
     * rails from z7, the front landings with their lit edges and rails, the cross walk over the foil (deck y16, z
     * 31..33, a rail on its nave edge, open to the catwalks), and the two spirals of 32 steps from stand 1 to stand 17
     * round their posts, leaving at steps 16 and 32 beside the decks.
     */
    @Test
    void theCatwalksTheCrossWalkAndTheStairs() {
        for (int yd : DualFoilVaultShape.DECKS) {
            for (int z = 4; z < 34; z++) for (int x : new int[] { 1, 2, 24, 25 })
                assertEquals('d', S.get(x, yd, z), "the catwalk at " + x + "," + yd + "," + z);
            for (int z = 7; z < 34; z++) for (int x : new int[] { 3, 23 }) {
                assertEquals('e', S.get(x, yd, z), "the lit edge at " + x + "," + yd + "," + z);
                // the cross walk meets the upper catwalks: no rail between them
                boolean open = yd == 16 && z >= 31;
                assertEquals(open ? ' ' : 'r', S.get(x, yd + 1, z), "the rail at " + x + "," + (yd + 1) + "," + z);
            }
            for (int x = 4; x <= 6; x++) for (int z = 1; z <= 6; z++) {
                assertEquals(x == 6 || z == 6 ? 'e' : 'd', S.get(x, yd, z), "the landing at " + x + "," + z);
                assertEquals(x == 6 || z == 6 ? 'r' : ' ', S.get(x, yd + 1, z));
            }
        }
        for (int x = 4; x <= 22; x++) {
            assertEquals('e', S.get(x, 16, 31));
            assertEquals('d', S.get(x, 16, 32));
            assertEquals('d', S.get(x, 16, 33));
            assertEquals('r', S.get(x, 17, 31), "the cross walk's rail");
            assertEquals(' ', S.get(x, 17, 32));
        }
        for (int[] p : DualFoilVaultShape.STAIRS) for (int y = 1; y <= 16; y++) assertEquals('q', S.get(p[0], y, p[1]));
        java.util.List<int[]> steps = ShapeKit.spiral(2, 2, 1, 32, 0, 1);
        assertEquals(32, steps.size());
        int[] last = steps.get(31), mid = steps.get(15);
        assertEquals('W', (char) last[3]);
        assertEquals(16, last[1], "the last step tops at 17");
        assertEquals(8, mid[1], "the sixteenth tops at 9, the first catwalk's stand");
        for (int[] st : steps) assertEquals((char) st[3], S.get(st[0], st[1], st[2]));
        for (int[] st : ShapeKit.spiral(24, 2, 1, 32, 0, -1)) assertEquals((char) st[3], S.get(st[0], st[1], st[2]));
    }

    /**
     * The gate niche (内境门位, CITY.md §4.15) is the foil's lower middle, x 12..14, y 3..6, z 34: its twelve cells are
     * soft canvas ({@code $}, left out of the blueprint the structure check uses) yet hold the canvas block like the
     * foil round them and are built with the back wall (SHELL), and no unit has them: the foil keeps its other 131
     * cells, all checked.
     */
    @Test
    void theGateNicheIsSoftCanvasInNoUnit() {
        Set<Long> want = new HashSet<>();
        for (int x = 12; x <= 14; x++) for (int y = 3; y <= 6; y++) want.add(ShapeKit.Shape.pos(x, y, 34));
        assertEquals(want, S.niche);
        Blueprint b = DualFoilVaultShape.BLUEPRINT;
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            boolean niche = want.contains(ShapeKit.Shape.pos(x, y, z));
            assertEquals(niche, DualFoilVaultShape.inNiche(x, y, z), x + "," + y + "," + z);
            assertEquals(niche, ch == '$', x + "," + y + "," + z);
            if (!niche) continue;
            assertTrue(ShapeKit.soft(ch));
            assertFalse(ShapeKit.checked(ch));
            int[] c = DualFoilVaultShape.cellOf(x, y, z);
            assertEquals(' ', b.at(c[0], c[1], c[2]), "not checked at " + x + "," + y);
            assertEquals('Q', ShapeKit.checkedTwin(ch));
            assertEquals(Parts.deck(Parts.D_CANVAS), ShapeKit.partOf(ch), "the canvas block at " + x + "," + y);
            assertEquals(BuildPlan.M_SHELL, DualFoilVaultShape.stage(x, y, z, ch), "built with the back wall");
            assertEquals(-1, DualFoilVaultShape.UNITS.unitOfCell(x, y, z), "in no unit");
        }
        // the foil round it stays checked canvas, its lit frame under it
        for (int y = 3; y <= 6; y++) for (int x : new int[] { 11, 15 }) assertEquals('Q', S.get(x, y, 34));
        for (int x = 12; x <= 14; x++) {
            assertEquals('L', S.get(x, 2, 34), "the foil's frame under the niche");
            assertEquals('Q', S.get(x, 7, 34));
        }
    }

    /**
     * The 43 units in slot order: the canvases tier by tier (ground, catwalk 1, catwalk 2), the left wall before the
     * right, front to back, each a column of 3 plinths, 12 canvas cells and 3 crowns on its wall facing into the nave,
     * one slot; then the foil, 131 canvas cells on the back wall facing the desk, one slot.
     */
    @Test
    void theCanvasesAndTheFoil() {
        UnitLayout u = DualFoilVaultShape.UNITS;
        assertEquals(43, u.size());
        assertEquals(43, u.slots());
        String[] tiers = { "G", "M", "U" };
        int i = 0;
        for (int t = 0; t < 3; t++) for (String side : new String[] { "L", "R" }) for (int k = 1; k <= 7; k++) {
            UnitLayout.Unit c = u.unit(i);
            assertEquals(tiers[t] + side + "-" + k, c.name);
            assertEquals("canvas", c.kind);
            assertEquals(1, c.slots);
            assertEquals(i, c.firstSlot);
            assertTrue(c.sockets.isEmpty(), "a canvas shows its slot over its whole field");
            assertEquals(18, c.cells.size());
            int xw = side.equals("L") ? 0 : 26, yp = DualFoilVaultShape.TIERS[t], z0 = 5 + 4 * (k - 1);
            assertArrayEquals(new int[] { side.equals("L") ? 1 : -1, 0 }, c.face);
            assertEquals(yp, c.floor);
            Map<Character, Integer> parts = new HashMap<>();
            for (int[] p : c.cells) {
                assertEquals(xw, p[0]);
                assertTrue(p[2] >= z0 && p[2] <= z0 + 2 && p[1] >= yp && p[1] <= yp + 5);
                char ch = S.get(p[0], p[1], p[2]);
                assertEquals(p[1] == yp ? 'p' : p[1] == yp + 5 ? 'c' : 'Q', ch);
                parts.merge(ch, 1, Integer::sum);
            }
            assertEquals(3, (int) parts.get('p'));
            assertEquals(12, (int) parts.get('Q'));
            assertEquals(3, (int) parts.get('c'));
            // posts either side of it
            for (int y = yp; y <= yp + 5; y++) {
                assertEquals('q', S.get(xw, y, z0 - 1));
                assertEquals('q', S.get(xw, y, z0 + 3));
            }
            i++;
        }
        UnitLayout.Unit foil = u.unit(42);
        assertEquals("foil", foil.name);
        assertEquals("foil", foil.kind);
        assertEquals(1, foil.slots);
        assertEquals(42, foil.firstSlot);
        assertEquals(131, foil.cells.size());
        assertArrayEquals(new int[] { 0, -1 }, foil.face);
        for (int[] p : foil.cells) {
            assertEquals(34, p[2]);
            assertTrue(DualFoilVaultShape.inFoil(p[0], p[1]));
            assertFalse(DualFoilVaultShape.inNiche(p[0], p[1], p[2]));
            assertEquals('Q', S.get(p[0], p[1], p[2]));
        }
        assertArrayEquals(new int[] { 7, 3, 34 }, foil.cells.get(0));
        // the window's plans: the three tiers' floors; the foil hangs over its frame (its lowest cells at y3) and is
        // used from the nave's floor, so it shows on the ground floor's plan
        assertEquals(3, foil.base);
        assertEquals(DualFoilVaultShape.FOIL_FLOOR, foil.floor);
        assertArrayEquals(new int[] { 1, 9, 17 }, u.storeys());
        assertEquals(0, u.storey(42));
        for (int k = 0; k < 42; k++) assertEquals(u.unit(k).base, u.unit(k).floor, "a canvas stands on its plinths");
    }

    @Test
    void everyCharacterIsALegendPart() {
        for (Map.Entry<Character, Integer> e : S.counts()
            .entrySet()) {
            char ch = e.getKey();
            if (ch == '~') continue;
            assertTrue(ShapeKit.partOf(ch) >= 0, "'" + ch + "'");
        }
        assertEquals(Parts.deck(Parts.D_CANVAS), ShapeKit.partOf('Q'));
        assertEquals(Parts.frame(Parts.FR_ANCHOR), ShapeKit.partOf('A'));
        assertEquals(Parts.moduleCore(ModuleCores.DUAL_FOIL_VAULT), DualFoilVaultShape.BUILD.part(13, 2, 0, '~'));
        assertEquals(65, Parts.moduleCore(ModuleCores.DUAL_FOIL_VAULT), "module core meta 1");
        assertEquals("", DualFoilVaultShape.DISSOLVES, "nothing dissolves");
    }

    /** Every cell gets a stage, and the stages hold what CITY.md §4.2 lists. */
    @Test
    void everyCellHasItsStage() {
        Map<Integer, Integer> perStage = new HashMap<>();
        Set<Long> unitCells = new HashSet<>();
        for (UnitLayout.Unit u : DualFoilVaultShape.UNITS.units())
            for (int[] c : u.cells) unitCells.add(ShapeKit.Shape.pos(c[0], c[1], c[2]));
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            if (ch == ' ' || unitCells.contains(ShapeKit.Shape.pos(x, y, z))) continue;
            int st = DualFoilVaultShape.stage(x, y, z, ch);
            perStage.merge(st, 1, Integer::sum);
            boolean edge = DualFoilVaultShape.onEdge(x, z);
            if (ch == '~') assertEquals(BuildPlan.M_COMMISSION, st);
            else if (ch == '-') assertEquals(BuildPlan.M_SHELL, st);
            else if (y == 0) assertEquals(ch == '.' ? BuildPlan.M_FLOOR : BuildPlan.M_SHELL, st);
            else if (ch == 'h' || ch == 'A' || ch == 'K' && !edge) assertEquals(BuildPlan.M_FIT, st, "the desk");
            else if (DualFoilVaultShape.onStair(x, y, z)) assertEquals(BuildPlan.M_DECK, st, "the stairs");
            else if (edge && y <= 7) assertEquals(BuildPlan.M_SHELL, st, "the walls y1..7 at " + x + "," + y + "," + z);
            else if (edge && y <= 23) assertEquals(BuildPlan.M_UPPER, st, "the walls y8..23");
            else if (y >= 24) assertEquals(BuildPlan.M_ROOF, st, "the cornice, roof and ridges");
            else {
                assertTrue(y == 8 || y == 9 || y == 16 || y == 17, "a deck cell at " + x + "," + y + "," + z);
                assertEquals(BuildPlan.M_DECK, st);
            }
            if (st == BuildPlan.M_FIT) assertTrue(DualFoilVaultShape.fitRank(x, y, z, ch) > 0, "after the units");
        }
        for (int s = BuildPlan.M_FLOOR; s < BuildPlan.M_STAGES; s++) {
            if (s == BuildPlan.M_DECOR) continue;
            assertNotNull(perStage.get(s), "stage " + s + " has cells");
        }
        assertFalse(perStage.containsKey(BuildPlan.M_CLEAR));
        assertEquals(1, (int) perStage.get(BuildPlan.M_COMMISSION));
        assertEquals(7, (int) perStage.get(BuildPlan.M_FIT), "the desk, two pedestals and four anchors");
        // the facade canvas is wall (UPPER), the portal's console stand the trumeau (SHELL)
        assertEquals(BuildPlan.M_UPPER, DualFoilVaultShape.stage(13, 15, 0, 'Q'));
        assertEquals(BuildPlan.M_SHELL, DualFoilVaultShape.stage(13, 1, 0, 'K'));
    }

    @Test
    void theFitStageRanksTheDeskThePedestalsThenTheAnchors() {
        int desk = DualFoilVaultShape.fitRank(13, 1, 30, 'K'), pedestal = DualFoilVaultShape.fitRank(10, 1, 30, 'h'),
            anchor = DualFoilVaultShape.fitRank(4, 7, 0, 'A');
        assertTrue(desk > 0 && pedestal > desk && anchor > pedestal);
        assertEquals(
            2,
            DualFoilVaultShape.walkStarts()
                .size());
    }
}
