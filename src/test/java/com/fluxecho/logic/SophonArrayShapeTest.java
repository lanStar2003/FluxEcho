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
 * The Sophon Array's shape against its generator ({@code city/gen/b_outer.py}, {@code sophon_array()}, 0.15.0): the
 * same cells (a checksum of every layer), the same counts, mirror-symmetric, the controller on its console at the
 * centre under the glazed oculus, the four portals' air, the podium's roof, the four struts and the lattice sphere of
 * 774 frame pillars, the 48 racks in slot order, the gate niche soft but built, and a stage for every cell as CITY.md
 * §4.10 lists them (the struts and the lattice after the racks).
 */
class SophonArrayShapeTest {

    private static final ShapeKit.Shape S = SophonArrayShape.SHAPE;

    @Test
    void theCellsAreTheGenerators() {
        CRC32 crc = new CRC32();
        crc.update(
            String.join("\n", ShapeKit.layers(S))
                .getBytes(StandardCharsets.UTF_8));
        // zlib.crc32 of the generator's layers, y0 first, rows from the front, each row x 0..30
        assertEquals(2217670034L, crc.getValue());
    }

    @Test
    void theGeneratorsCounts() {
        Map<Character, Integer> want = new TreeMap<>();
        Object[] pairs = { 'P', 774, 'W', 738, '.', 428, 'G', 209, 'n', 152, 'o', 144, '_', 136, '-', 128, 'F', 120,
            'w', 97, 'L', 61, '+', 57, 'p', 48, 'c', 48, 'A', 4, '/', 4, '[', 3, '!', 3, '{', 2, 'K', 1, '~', 1 };
        for (int i = 0; i < pairs.length; i += 2) want.put((Character) pairs[i], (Integer) pairs[i + 1]);
        assertEquals(want, S.counts());
        assertEquals(2397, S.checkedCount());
        assertEquals(633, S.softCount());
        assertEquals(128, S.airCount());
        assertEquals(0, S.decor.size(), "no canopy: a centrally planned building's doors are its chamfer portals");
        assertEquals(3158, S.steps());
        ShapeKit.Summary sum = ShapeKit.summary(S, null);
        assertEquals(3158, sum.steps);
        assertEquals(48, sum.units);
        assertEquals(144, sum.slots);
        assertTrue(sum.symmetric);
    }

    @Test
    void theBoxIsThirtyOneSquareAndThirtyThreeHigh() {
        assertEquals(31, S.W);
        assertEquals(31, S.D);
        assertEquals(33, S.H);
        Blueprint b = SophonArrayShape.BLUEPRINT;
        assertEquals(SophonArrayShape.HEIGHT, b.height());
        assertEquals(31, b.width());
        assertEquals(31, b.depth());
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            int[] c = SophonArrayShape.cellOf(x, y, z);
            assertEquals(ShapeKit.soft(ch) ? ' ' : ch, b.at(c[0], c[1], c[2]), x + "," + y + "," + z);
        }
        // the foundation: the plan's thick edge, soft deck with its trim aisles and lit axes inside
        for (int x = 0; x < 31; x++) for (int z = 0; z < 31; z++) {
            boolean in = ShapeKit.octo(x - 15, z - 15, 15, 20);
            char ch = S.get(x, 0, z);
            if (!in) assertEquals(' ', ch);
            else if (SophonArrayShape.onEdge(x, z)) assertEquals('F', ch);
            else if (x == 15 || z == 15) assertEquals('+', ch, "the lit axes");
            else assertTrue(ch == '.' || ch == '_', x + "," + z);
        }
        assertEquals('_', S.get(7, 0, 9), "a cold aisle between the rows");
    }

    @Test
    void symmetricAcrossX() {
        assertTrue(
            S.symmetric()
                .isEmpty());
        assertTrue(S.asym.isEmpty());
    }

    /** The controller on its console at the hall's centre, under the oculus: nothing between it and the sphere. */
    @Test
    void theControllerUnderTheOculus() {
        assertArrayEquals(new int[] { 15, 2, 15 }, S.controller);
        assertEquals('~', S.get(15, 2, 15));
        assertEquals('K', S.get(15, 1, 15), "over its console stand");
        Blueprint b = SophonArrayShape.BLUEPRINT;
        assertArrayEquals(SophonArrayShape.cellOf(15, 2, 15), new int[] { b.ctrlA, b.ctrlB, b.ctrlC });
        assertEquals(-1, SophonArrayShape.UNITS.unitOfCell(15, 1, 15), "the console is no unit");
        for (int y = 3; y < SophonArrayShape.ROOF_Y; y++) assertEquals(' ', S.get(15, y, 15), "y" + y);
        assertEquals('G', S.get(15, SophonArrayShape.ROOF_Y, 15), "the oculus's glass");
        assertEquals(' ', S.get(15, 7, 15));
        assertEquals('P', S.get(15, SophonArrayShape.LATTICE_FROM, 15), "the sphere's lowest point over the oculus");
    }

    @Test
    void theFourPortals() {
        int air = 0;
        Set<Long> columns = new HashSet<>();
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            if (S.get(x, y, z) != '-') continue;
            assertTrue(y >= 1 && y <= 4, "door air at y" + y);
            assertTrue(SophonArrayShape.inPortal(x, z));
            assertEquals('n', S.get(x, 5, z), "the lintel over " + x + "," + z);
            columns.add(ShapeKit.key(x, z));
            air++;
        }
        assertEquals(128, air, "four portals of 32 cells, two deep");
        assertEquals(32, columns.size());
        for (int[] c : new int[][] { { 5, 5 }, { 25, 5 }, { 5, 25 }, { 25, 25 } }) {
            for (int y = 1; y <= 4; y++) assertEquals('-', S.get(c[0], y, c[1]), c[0] + "," + y + "," + c[1]);
        }
        // the outermost racks of the radius-10 rows stand in the portals' inner cells, as the generator draws them
        assertEquals('p', S.get(7, 1, 5));
        assertEquals('c', S.get(5, 5, 7));
    }

    /** The podium's cornice on its walls, its roof, and the glazed oculus oct(4, 5) in a lit rim, all at y6. */
    @Test
    void thePodiumsRoofAndOculus() {
        int glass = 0, rim = 0;
        for (int x = 0; x < 31; x++) for (int z = 0; z < 31; z++) {
            int du = x - 15, dv = z - 15;
            char ch = S.get(x, SophonArrayShape.ROOF_Y, z);
            if (!ShapeKit.octo(du, dv, 15, 20)) assertEquals(' ', ch);
            else if (SophonArrayShape.onEdge(x, z)) assertEquals('n', ch);
            else if (ShapeKit.octo(du, dv, 3, 4)) {
                assertEquals('G', ch);
                glass++;
            } else if (ShapeKit.octo(du, dv, 4, 5)) {
                assertEquals('L', ch);
                rim++;
            } else assertEquals('W', ch);
        }
        assertEquals(37, glass);
        assertEquals(20, rim);
        // the walls: plinth y1, pilasters, glazing y3..4, the cladding anchors in the flat faces' middles at y5
        assertEquals('w', S.get(0, 1, 12));
        assertEquals('L', S.get(0, 3, 12), "a pilaster every 3");
        assertEquals('G', S.get(0, 3, 13));
        assertEquals('W', S.get(0, 5, 13));
        for (int[] a : new int[][] { { 15, 0 }, { 0, 15 }, { 30, 15 }, { 15, 30 } })
            assertEquals('A', S.get(a[0], 5, a[1]));
    }

    /**
     * The four struts (y7..10 at (+-5, +-5)) and the lattice sphere (radius 12 round y20, y8..32): every other frame
     * pillar lies on the sphere's shell, on a meridian or a parallel; 16 strut cells and 758 lattice cells, 774 in
     * all, every one of them dissolving.
     */
    @Test
    void theStrutsAndTheLatticeSphere() {
        int struts = 0, lattice = 0;
        Set<Integer> layers = new HashSet<>();
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            if (S.get(x, y, z) != 'P') continue;
            if (SophonArrayShape.strut(x, y, z)) {
                struts++;
                continue;
            }
            lattice++;
            layers.add(y);
            double d = Math.sqrt((x - 15) * (x - 15) + (y - 20) * (y - 20) + (z - 15) * (z - 15));
            assertTrue(d >= 11.5 && d < 12.5, "on the shell at " + x + "," + y + "," + z);
            assertEquals('P', S.get(x, y, 30 - z), "a sphere mirrors across z too");
        }
        assertEquals(16, struts);
        assertEquals(758, lattice);
        assertEquals(25, layers.size(), "y8..32");
        for (int[] st : new int[][] { { 10, 10 }, { 20, 10 }, { 10, 20 }, { 20, 20 } })
            for (int y = 7; y <= 10; y++) assertEquals('P', S.get(st[0], y, st[1]));
        assertEquals('P', S.get(15, 32, 15), "the top pole");
        assertEquals('P', S.get(15, 20, 3), "the equator");
        assertEquals("P", SophonArrayShape.DISSOLVES);
        assertEquals(SophonArrayShape.DISSOLVES, SophonArrayShape.PASSES);
        for (int y = 7; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++)
            assertTrue(" P".indexOf(S.get(x, y, z)) >= 0, "only frame pillars over the roof");
    }

    /**
     * The gate niche (内境门位, CITY.md §4.15) in the back flat face's middle, x 14..16, y1..4, z 30: its twelve cells
     * are soft (left out of the blueprint the structure check uses) yet hold the same blocks as the wall round them
     * (the dark course at y1, the lit pilaster at x 15, a panel at y2 and glass above beside it), built with the wall.
     */
    @Test
    void theGateNicheIsSoftAndStillBuilt() {
        Set<Long> want = new HashSet<>();
        for (int x = 14; x <= 16; x++) for (int y = 1; y <= 4; y++) want.add(ShapeKit.Shape.pos(x, y, 30));
        assertEquals(want, S.niche);
        Blueprint b = SophonArrayShape.BLUEPRINT;
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            boolean niche = want.contains(ShapeKit.Shape.pos(x, y, z));
            assertEquals(niche, SophonArrayShape.inNiche(x, y, z), x + "," + y + "," + z);
            if (!niche) continue;
            assertTrue(ShapeKit.soft(ch), "soft at " + x + "," + y + "," + z);
            int[] c = SophonArrayShape.cellOf(x, y, z);
            assertEquals(' ', b.at(c[0], c[1], c[2]), "not checked at " + x + "," + y + "," + z);
            char wall = y == 1 ? 'w' : x == 15 ? 'L' : y == 2 ? 'W' : 'G';
            assertEquals(wall, ShapeKit.checkedTwin(ch));
            assertEquals(ShapeKit.partOf(wall), ShapeKit.partOf(ch), "the same block at " + x + "," + y + "," + z);
            assertEquals(BuildPlan.M_SHELL, SophonArrayShape.stage(x, y, z, ch), "built with the wall");
        }
        for (int y = 1; y <= 4; y++) for (int x : new int[] { 13, 17 }) {
            char ch = S.get(x, y, 30);
            assertTrue(ShapeKit.checked(ch), x + "," + y);
            assertEquals(ch, b.at(x, SophonArrayShape.HEIGHT - 1 - y, 30));
        }
        for (UnitLayout.Unit u : SophonArrayShape.UNITS.units())
            for (int[] c : u.cells) assertFalse(SophonArrayShape.inNiche(c[0], c[1], c[2]), u.name);
    }

    @Test
    void everyCharacterIsALegendPart() {
        for (Map.Entry<Character, Integer> e : S.counts()
            .entrySet()) {
            char ch = e.getKey();
            if (ch == '~') continue;
            assertTrue(ShapeKit.partOf(ch) >= 0, "'" + ch + "'");
        }
        assertEquals(Parts.moduleCore(ModuleCores.SOPHON_ARRAY), SophonArrayShape.BUILD.part(15, 2, 15, '~'));
        assertEquals(Parts.frame(Parts.FR_PILLAR), ShapeKit.partOf('P'));
        assertEquals(73, Parts.moduleCore(ModuleCores.SOPHON_ARRAY), "fluxecho:module_core meta 9");
    }

    /**
     * The 48 racks in the generator's order, which is the slot order for good: each a plinth, three core sockets and a
     * crown from the floor to the ceiling, facing the centre, in two rows (radius 7 and 10) on every side.
     */
    @Test
    void theRacks() {
        UnitLayout u = SophonArrayShape.UNITS;
        assertEquals(48, u.size());
        assertEquals(144, u.slots());
        int slot = 0;
        Map<Integer, Integer> rows = new HashMap<>();
        for (int i = 0; i < 48; i++) {
            UnitLayout.Unit r = u.unit(i);
            assertEquals("rack-" + (i + 1), r.name);
            assertEquals("memory_rack", r.kind, "the array's own kind, not the gate's tuning rack");
            assertEquals(3, r.slots);
            assertEquals(3, r.sockets.size(), r.name + ": a socket a slot");
            assertEquals(slot, r.firstSlot);
            slot += 3;
            assertEquals(5, r.cells.size());
            int x = r.cells.get(0)[0], z = r.cells.get(0)[2];
            char[] parts = { 'p', 'o', 'o', 'o', 'c' };
            for (int y = 1; y <= 5; y++) {
                assertArrayEquals(new int[] { x, y, z }, r.cells.get(y - 1));
                assertEquals(parts[y - 1], S.get(x, y, z));
            }
            // it faces the centre across the row it stands in
            int du = x - 15, dv = z - 15;
            int radius = Math.abs(r.face[0] == 0 ? dv : du);
            assertTrue(radius == 7 || radius == 10, r.name + " at " + radius);
            rows.merge(radius, 1, Integer::sum);
            assertEquals(-Integer.signum(r.face[0] == 0 ? dv : du), r.face[0] == 0 ? r.face[1] : r.face[0]);
        }
        assertEquals(16, (int) rows.get(7));
        assertEquals(32, (int) rows.get(10));
        assertArrayEquals(new int[] { 13, 1, 8 }, u.unit(0).cells.get(0));
        assertArrayEquals(new int[] { 0, 1 }, u.unit(0).face);
        assertArrayEquals(new int[] { 25, 1, 23 }, u.unit(47).cells.get(0));
        assertArrayEquals(new int[] { -1, 0 }, u.unit(47).face);
        assertArrayEquals(new int[] { 1 }, u.storeys(), "everything stands on the hall's floor");
    }

    /** Every cell gets a stage, and the stages hold what CITY.md §4.10 lists. */
    @Test
    void everyCellHasItsStage() {
        Map<Integer, Integer> perStage = new HashMap<>();
        Set<Long> unitCells = new HashSet<>();
        for (UnitLayout.Unit u : SophonArrayShape.UNITS.units())
            for (int[] c : u.cells) unitCells.add(ShapeKit.Shape.pos(c[0], c[1], c[2]));
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            if (ch == ' ' || unitCells.contains(ShapeKit.Shape.pos(x, y, z))) continue;
            int st = SophonArrayShape.stage(x, y, z, ch);
            perStage.merge(st, 1, Integer::sum);
            if (ch == '~') assertEquals(BuildPlan.M_COMMISSION, st);
            if (ch == '-' || ch == 'F') assertEquals(BuildPlan.M_SHELL, st);
            if (y == 0 && ShapeKit.soft(ch)) assertEquals(BuildPlan.M_FLOOR, st);
            if (y >= 1 && y <= 5 && ch != 'K' && ch != '~') assertEquals(BuildPlan.M_SHELL, st, "the podium's walls");
            if (y == 6) assertEquals(BuildPlan.M_ROOF, st, "the podium's roof and oculus");
            if (ch == 'K') assertEquals(BuildPlan.M_FIT, st);
            if (ch == 'P') assertEquals(BuildPlan.M_UPPER, st, "the struts and the lattice");
            if (st == BuildPlan.M_FIT) assertTrue(SophonArrayShape.fitRank(x, y, z, ch) > 0, "after the racks");
        }
        for (int s : new int[] { BuildPlan.M_FLOOR, BuildPlan.M_SHELL, BuildPlan.M_ROOF, BuildPlan.M_FIT,
            BuildPlan.M_UPPER, BuildPlan.M_COMMISSION }) assertNotNull(perStage.get(s), "stage " + s + " has cells");
        assertFalse(perStage.containsKey(BuildPlan.M_DECK), "no deck");
        assertFalse(perStage.containsKey(BuildPlan.M_CLEAR));
        assertEquals(1, (int) perStage.get(BuildPlan.M_COMMISSION));
        assertEquals(1, (int) perStage.get(BuildPlan.M_FIT), "the console after the racks");
        assertEquals(774, (int) perStage.get(BuildPlan.M_UPPER), "the 774 frame pillars");
        assertEquals(621, (int) perStage.get(BuildPlan.M_FLOOR));
        assertEquals(741, (int) perStage.get(BuildPlan.M_ROOF), "the roof over the whole octagon");
        // its own order (M12 §5): ROOF, then FIT (the racks), then UPPER (the struts and the lattice), a FIT before
        // UPPER as CITY.md lists them
        int[] order = SophonArrayShape.STAGE_ORDER;
        assertEquals(BuildPlan.M_STAGES, order.length);
        int roof = -1, fit = -1, upper = -1;
        for (int p = 0; p < order.length; p++) {
            if (order[p] == BuildPlan.M_ROOF) roof = p;
            if (order[p] == BuildPlan.M_FIT) fit = p;
            if (order[p] == BuildPlan.M_UPPER) upper = p;
        }
        assertTrue(roof < fit && fit < upper, "ROOF, FIT, UPPER");
        assertEquals(BuildPlan.M_COMMISSION, order[BuildPlan.M_STAGES - 1]);
    }
}
