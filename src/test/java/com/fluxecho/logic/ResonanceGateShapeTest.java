package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;

import org.junit.jupiter.api.Test;

/**
 * The Resonance Gate's shape against its generator ({@code city/gen/b_gate.py}, {@code resonance_gate()}): the same
 * cells (a checksum of every layer), the same counts, mirror-symmetric but for its two tower stairs, the controller on
 * the kiosk in the road under the bridge, the open passage (nothing built in it under the bridge but the kiosk and the
 * floor the road leaves unpaved, exactly the cells {@link CampusPlan#gatePassageFloor} names), the bridge with the
 * spire core and its glass, the door air, the ten units in slot order, no gate niche, and a stage for every cell as
 * CITY.md §4.7 lists them.
 */
class ResonanceGateShapeTest {

    private static final ShapeKit.Shape S = ResonanceGateShape.SHAPE;

    @Test
    void theCellsAreTheGenerators() {
        CRC32 crc = new CRC32();
        crc.update(
            String.join("\n", ShapeKit.layers(S))
                .getBytes(StandardCharsets.UTF_8));
        // zlib.crc32 of the generator's eighteen layers, y0 first, rows from the front, each row x 0..30
        assertEquals(1079565838L, crc.getValue());
    }

    @Test
    void theGeneratorsCounts() {
        Map<Character, Integer> want = new TreeMap<>();
        Object[] pairs = { 'W', 1222, 'L', 328, 'd', 283, '.', 268, 'n', 160, '-', 136, 'G', 109, 'F', 96, 'w', 84, 'o',
            24, 'q', 16, 't', 16, 'c', 10, 'p', 10, 'A', 4, 'C', 3, 'h', 2, 'K', 1, 'Z', 1, '~', 1 };
        for (int i = 0; i < pairs.length; i += 2) want.put((Character) pairs[i], (Integer) pairs[i + 1]);
        assertEquals(want, S.counts());
        assertEquals(2370, S.checkedCount());
        assertEquals(268, S.softCount());
        assertEquals(136, S.airCount());
        assertEquals(0, S.decor.size(), "no canopy");
        assertEquals(2774, S.steps());
        ShapeKit.Summary sum = ShapeKit.summary(S, null);
        assertEquals(2774, sum.steps);
        assertEquals(10, sum.units);
        assertEquals(24, sum.slots);
        assertTrue(sum.symmetric);
    }

    /** 31 across, 17 deep and 18 high, the crown posts in its top layer; the blueprint is the box itself. */
    @Test
    void theBoxIsThirtyOneBySeventeenAndEighteenHigh() {
        assertEquals(31, S.W);
        assertEquals(17, S.D);
        assertEquals(18, S.H);
        assertEquals(ResonanceGateShape.HEIGHT, S.H);
        assertEquals(15, S.MID);
        int crown = 0;
        for (int x = 0; x < S.W; x++) for (int z = 0; z < S.D; z++) if (S.get(x, 17, z) != ' ') {
            assertEquals('L', S.get(x, 17, z));
            crown++;
        }
        assertEquals(12, crown, "six lit crown posts a wing");
        Blueprint b = ResonanceGateShape.BLUEPRINT;
        assertEquals(18, b.height());
        assertEquals(31, b.width());
        assertEquals(17, b.depth());
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            int[] c = ResonanceGateShape.cellOf(x, y, z);
            assertEquals(ShapeKit.soft(ch) ? ' ' : ch, b.at(c[0], c[1], c[2]), x + "," + y + "," + z);
            assertEquals(ch, ResonanceGateShape.cell(x, y, z));
            assertEquals(ch, ResonanceGateShape.BUILD.cell(x, y, z));
        }
        assertEquals(' ', ResonanceGateShape.BUILD.cell(15, 18, 8));
        assertEquals(
            2370 + 136,
            b.cells()
                .size(),
            "the checked cells and the door air");
    }

    /** Mirror-symmetric across x but for the two tower stairs, which turn opposite ways: 16 steps each. */
    @Test
    void symmetricButForTheStairs() {
        assertTrue(
            S.symmetric()
                .isEmpty());
        assertEquals(32, S.asym.size());
        Set<Long> steps = new HashSet<>();
        for (int i = 0; i < 2; i++) {
            int[] w = ResonanceGateShape.WELLS[i];
            List<int[]> spiral = ShapeKit.spiral(w[0], w[1], 1, 16, 6, i == 0 ? 1 : -1);
            assertEquals(16, spiral.size());
            for (int[] st : spiral) {
                steps.add(ShapeKit.Shape.pos(st[0], st[1], st[2]));
                assertEquals((char) st[3], S.get(st[0], st[1], st[2]));
                assertTrue(ResonanceGateShape.onStair(st[0], st[1], st[2]));
            }
            assertEquals('W', S.get(spiral.get(15)[0], 8, spiral.get(15)[2]), "the last step lands on the deck");
            for (int y = 1; y <= 8; y++) assertEquals('q', S.get(w[0], y, w[1]), "the stair's post");
        }
        assertEquals(steps, S.asym);
        assertArrayEquals(new int[] { 4, 13 }, ResonanceGateShape.WELLS[0]);
        assertArrayEquals(new int[] { 26, 13 }, ResonanceGateShape.WELLS[1]);
    }

    /** The kiosk in the road under the bridge: its console y1, the controller y2, facing the nexus (z 0). */
    @Test
    void theControllerOnTheKiosk() {
        assertArrayEquals(new int[] { 15, 2, 7 }, S.controller);
        assertEquals('K', S.get(15, 1, 7));
        assertEquals('~', S.get(15, 2, 7));
        Blueprint b = ResonanceGateShape.BLUEPRINT;
        assertArrayEquals(ResonanceGateShape.cellOf(15, 2, 7), new int[] { b.ctrlA, b.ctrlB, b.ctrlC });
        assertEquals(-1, ResonanceGateShape.UNITS.unitOfCell(15, 1, 7));
        // A 30, R 0 in the campus frame: x = 15 + R, z = A - 23
        assertEquals(30, CampusPlan.GATEHOUSE_FROM + 7);
        for (int y = 3; y < CampusPlan.GATE_HEADROOM; y++) assertEquals(' ', S.get(15, y, 7), "open over the kiosk");
    }

    /**
     * The passage x 9..21 (R -6..6) is open seven high: under the bridge nothing but the kiosk, and on its floor the
     * soft deck exactly where the gate road leaves the ground bare ({@link CampusPlan#gatePassageFloor}).
     */
    @Test
    void thePassageIsOpen() {
        for (int x = 9; x <= 21; x++) for (int z = 0; z < S.D; z++) {
            int a = z + CampusPlan.GATEHOUSE_FROM, r = x - 15;
            assertTrue(CampusPlan.inGatePassage(a, r));
            assertFalse(ResonanceGateShape.inWing(x));
            char floor = S.get(x, 0, z);
            assertEquals(CampusPlan.gatePassageFloor(a, r) ? '.' : ' ', floor, "the floor at A " + a + " R " + r);
            for (int y = 1; y < CampusPlan.GATE_HEADROOM; y++) {
                boolean kiosk = x == 15 && z == 7 && y <= 2;
                assertEquals(kiosk, S.get(x, y, z) != ' ', x + "," + y + "," + z);
            }
        }
        for (int x : new int[] { 0, 8, 22, 30 }) assertTrue(ResonanceGateShape.inWing(x));
        // the road's own cells: its strip, the node and the threshold widenings
        assertEquals(' ', S.get(15, 0, 0));
        assertEquals(' ', S.get(20, 0, 4), "the node's widening at A 27");
        assertEquals('.', S.get(20, 0, 8), "R 5 at A 31, between the node and the threshold");
        assertEquals(' ', S.get(20, 0, 12), "the threshold's widening at A 35");
        assertEquals('.', S.get(21, 0, 12), "R 6");
    }

    /**
     * The bridge (x 9..21, z 5..11): its deck y8 with a cornice under its edges, glazed side walls y9..12, its roof
     * y13; the spire core in its middle (conduits y9..11, the seat y12, a glass cell over it); openings y9..12 into
     * both wings' upper halls.
     */
    @Test
    void theBridgeAndTheSpireCore() {
        for (int x = 9; x <= 21; x++) for (int z = 5; z <= 11; z++) {
            assertEquals('d', S.get(x, 8, z));
            char roof = S.get(x, 13, z);
            if (z == 5 || z == 11) {
                assertEquals('n', S.get(x, 7, z));
                assertEquals('n', roof);
                for (int y = 9; y <= 12; y++) {
                    char want = (y == 10 || y == 11) && x % 2 == 1 ? 'G' : x == 9 || x == 21 ? 'L' : 'W';
                    if (S.get(x, y, z) == 'p' || S.get(x, y, z) == 'o' || S.get(x, y, z) == 'c') continue;
                    assertEquals(want, S.get(x, y, z), x + "," + y + "," + z);
                }
            } else assertEquals(x == 15 && z == 8 ? 'G' : 'W', roof);
        }
        for (int y = 9; y <= 11; y++) assertEquals('C', S.get(15, y, 8));
        assertEquals('Z', S.get(15, 12, 8));
        assertEquals('G', S.get(15, 13, 8));
        for (int z = 6; z <= 10; z++) for (int y = 9; y <= 12; y++) {
            assertEquals('-', S.get(8, y, z));
            assertEquals('-', S.get(22, y, z));
        }
        assertEquals(ResonanceGateShape.DISSOLVES, ResonanceGateShape.PASSES);
        assertEquals("CZ", ResonanceGateShape.DISSOLVES);
    }

    /** The door air: the wings' front doors and passage doors y1..4, two deep, and the bridge openings y9..12. */
    @Test
    void theDoorAir() {
        int low = 0, high = 0;
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            if (S.get(x, y, z) != '-') continue;
            assertTrue(ResonanceGateShape.inWing(x), "door air in a wing: " + x);
            if (y >= 1 && y <= 4) low++;
            else if (y >= 9 && y <= 12) high++;
            else throw new AssertionError("door air at y" + y);
        }
        assertEquals(6 * 4 * 2 + 3 * 4 * 4, low, "two front doors three wide, two passage doors two deep");
        assertEquals(2 * 5 * 4, high, "the bridge openings");
        assertEquals(136, low + high);
        for (int x : new int[] { 3, 4, 5, 25, 26, 27 }) assertEquals('n', S.get(x, 5, 0), "the front door's lintel");
    }

    /** Six resonators (2 slots each, facing into the chamber), then four racks (3 slots, facing into the halls). */
    @Test
    void theUnitsInSlotOrder() {
        UnitLayout u = ResonanceGateShape.UNITS;
        assertEquals(10, u.size());
        assertEquals(24, u.slots());
        List<ShapeKit.Unit> units = S.units();
        int[][] resonators = { { 11, 6 }, { 15, 6 }, { 19, 6 }, { 11, 10 }, { 15, 10 }, { 19, 10 } };
        for (int i = 0; i < 6; i++) {
            ShapeKit.Unit r = units.get(i);
            assertEquals("resonator-" + (i + 1), r.name);
            assertEquals("resonator", r.kind);
            assertEquals(2, r.slots);
            assertArrayEquals(new int[] { 0, i < 3 ? 1 : -1 }, r.face);
            assertEquals(4, r.cells.size());
            String chars = "";
            for (int k = 0; k < 4; k++) {
                int[] c = r.cells.get(k);
                assertArrayEquals(new int[] { resonators[i][0], 9 + k, resonators[i][1] }, c);
                chars += S.get(c[0], c[1], c[2]);
            }
            assertEquals("pooc", chars);
            assertEquals("resonator", u.unit(i).kind);
            assertEquals(2 * i, u.unit(i).firstSlot);
        }
        int[][] racks = { { 1, 4 }, { 1, 8 }, { 29, 4 }, { 29, 8 } };
        for (int i = 0; i < 4; i++) {
            ShapeKit.Unit r = units.get(6 + i);
            assertEquals("rack-" + (7 + i), r.name);
            assertEquals("rack", r.kind);
            assertEquals(3, r.slots);
            assertArrayEquals(new int[] { i < 2 ? 1 : -1, 0 }, r.face);
            String chars = "";
            for (int k = 0; k < 5; k++) {
                int[] c = r.cells.get(k);
                assertArrayEquals(new int[] { racks[i][0], 1 + k, racks[i][1] }, c);
                chars += S.get(c[0], c[1], c[2]);
            }
            assertEquals("poooc", chars);
            assertEquals(12 + 3 * i, u.unit(6 + i).firstSlot);
        }
    }

    /** The gate is no building a light gate's niche goes in (CITY.md §4.15): it has none. */
    @Test
    void noGateNiche() {
        assertTrue(S.niche.isEmpty());
        for (char ch : "[!/{$".toCharArray()) assertFalse(
            S.counts()
                .containsKey(ch),
            "'" + ch + "'");
    }

    /**
     * Every cell that belongs to no unit has its stage (CITY.md §4.7): FLOOR the soft floor, SHELL the foundations, the
     * wings' walls y1..7 and the door air, DECK the y8 decks, the stairs and the bridge's cornice, UPPER the wings
     * y9..17 (walls, cornices, roofs and crown posts) and the bridge's side walls, ROOF the bridge's roof alone (its 91
     * cells at y13), FIT the kiosk's console, the pedestals and the spire core, COMMISSION the controller; within FIT
     * the console comes first and the core last.
     */
    @Test
    void everyCellHasItsStage() {
        Map<Integer, Integer> perStage = new TreeMap<>();
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            if (ch == ' ' || ResonanceGateShape.UNITS.unitOfCell(x, y, z) >= 0) continue;
            int stage = ResonanceGateShape.stage(x, y, z, ch);
            assertTrue(stage > BuildPlan.M_CLEAR && stage < BuildPlan.M_STAGES, x + "," + y + "," + z);
            perStage.merge(stage, 1, Integer::sum);
            if (y == 0) assertEquals(ch == '.' ? BuildPlan.M_FLOOR : BuildPlan.M_SHELL, stage);
            if (ch == '-') assertEquals(BuildPlan.M_SHELL, stage);
            if (ch == 'K' || ch == 'C' || ch == 'Z' || ch == 'h') assertEquals(BuildPlan.M_FIT, stage);
            if (ch == '~') assertEquals(BuildPlan.M_COMMISSION, stage);
            if (ResonanceGateShape.onStair(x, y, z)) assertEquals(BuildPlan.M_DECK, stage, "the stairs");
            if (ch == 'd') assertEquals(BuildPlan.M_DECK, stage);
            if (ResonanceGateShape.inWing(x) && y >= 9 && ch != '-')
                assertEquals(BuildPlan.M_UPPER, stage, x + "," + y + "," + z);
            if (y >= ResonanceGateShape.WING_ROOF)
                assertEquals(BuildPlan.M_UPPER, stage, "the wings' roofs and crowns");
            if (stage == BuildPlan.M_ROOF) {
                assertEquals(ResonanceGateShape.BRIDGE_ROOF, y, "only the bridge's roof is ROOF");
                assertFalse(ResonanceGateShape.inWing(x), x + "," + y + "," + z);
            }
        }
        assertEquals(13 * 7, (int) perStage.get(BuildPlan.M_ROOF), "the bridge's roof, x 9..21 by z 5..11");
        assertEquals(268, (int) perStage.get(BuildPlan.M_FLOOR));
        assertEquals(1, (int) perStage.get(BuildPlan.M_COMMISSION));
        assertEquals(1 + 3 + 1 + 2, (int) perStage.get(BuildPlan.M_FIT), "the console, the core and two pedestals");
        assertEquals(-1, ResonanceGateShape.fitRank(15, 1, 7, 'K'));
        assertEquals(1, ResonanceGateShape.fitRank(4, 1, 5, 'h'));
        assertEquals(2, ResonanceGateShape.fitRank(15, 9, 8, 'C'));
        assertEquals(2, ResonanceGateShape.fitRank(15, 12, 8, 'Z'));
        assertEquals(BuildPlan.M_ROOF, ResonanceGateShape.stage(15, 13, 8, 'G'), "the glass over the core");
        assertEquals(BuildPlan.M_DECK, ResonanceGateShape.stage(9, 7, 5, 'n'), "the bridge's cornice");
        assertEquals(BuildPlan.M_UPPER, ResonanceGateShape.stage(10, 9, 5, 'W'), "the bridge's wall");
        assertThrows(IllegalStateException.class, () -> ResonanceGateShape.stage(15, 4, 2, 'W'), "nothing there");
    }

    @Test
    void itsSitesAndBuild() {
        assertArrayEquals(new int[] { 0 }, ResonanceGateShape.SITES);
        assertEquals(SiteFamily.GATE, SiteFamily.of(0));
        assertEquals(Parts.moduleCore(ModuleCores.RESONANCE_GATE), ResonanceGateShape.BUILD.corePart());
        assertEquals(ResonanceGateShape.KEY, ModuleCores.key(ModuleCores.RESONANCE_GATE));
        assertTrue(ResonanceGateShape.BUILD.ornaments());
        assertEquals(31, CampusPlan.GATEHOUSE_HALF * 2 + 1);
        assertEquals(17, CampusPlan.GATEHOUSE_TO - CampusPlan.GATEHOUSE_FROM + 1);
        assertThrows(
            IllegalArgumentException.class,
            () -> ResonanceGateShape.plan(new CampusPlan(1L), 4, 0, 64, 0, 0, -1),
            "a hall site");
        assertThrows(
            IllegalArgumentException.class,
            () -> ResonanceGateShape.plan(new CampusPlan(1L), 8, 0, 64, 0, 0, -1),
            "the zenith");
    }
}
