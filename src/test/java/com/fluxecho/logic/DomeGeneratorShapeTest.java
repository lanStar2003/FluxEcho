package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * The Echo Dome Generator's shape against its generator ({@code city/gen/b_sky.py}, {@code dome_pod()} and
 * {@code dome_legs()}): the same pod cells (a checksum of every layer), the same counts, mirror-symmetric, the
 * controller, the pad, the seven wards, the soft door glazing and shaft cap added to it, the legs (the generator's on
 * the side R >= 0, mirrored), the two blueprints, the walk, and the envelope it takes of a campus.
 */
class DomeGeneratorShapeTest {

    private static final ShapeKit.Shape RAW = DomeGeneratorShape.RAW, S = DomeGeneratorShape.SHAPE;

    private static long crc(String s) {
        CRC32 c = new CRC32();
        c.update(s.getBytes(StandardCharsets.UTF_8));
        return c.getValue();
    }

    @Test
    void thePodIsTheGenerators() {
        // zlib.crc32 of dome_pod()'s layers, rows from the front, y0 first, each row x 0..20
        assertEquals(4099585315L, crc(String.join("\n", DomeGeneratorShape.rows(RAW))));
        Map<Character, Integer> want = new TreeMap<>();
        Object[] pairs = { 'W', 540, '.', 276, 'G', 172, 'F', 80, 'n', 80, 'L', 76, 'w', 56, 'K', 8, 'o', 7, 'C', 6,
            '^', 1, '~', 1 };
        for (int i = 0; i < pairs.length; i += 2) want.put((Character) pairs[i], (Integer) pairs[i + 1]);
        assertEquals(want, RAW.counts());
        assertEquals(1027, RAW.checkedCount());
        assertEquals(276, RAW.softCount());
        assertEquals(0, RAW.airCount());
        assertTrue(
            RAW.symmetric()
                .isEmpty());
        assertEquals(21, RAW.W);
        assertEquals(21, RAW.D);
        assertEquals(10, RAW.H);
    }

    /** The pod as it is built: the same checked cells, the door openings glazed and the shaft capped, all soft. */
    @Test
    void theDoorsAreGlazedAndTheShaftCappedSoft() {
        assertEquals(RAW.checkedCount(), S.checkedCount(), "nothing checked added");
        assertEquals(276 + 96 + 4, S.softCount());
        int doors = 0, caps = 0;
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char raw = RAW.get(x, y, z), built = S.get(x, y, z);
            if (raw == built) continue;
            assertEquals(' ', raw, "only empty cells change at " + x + "," + y + "," + z);
            assertTrue(ShapeKit.soft(built));
            if (DomeGeneratorShape.door(x, y, z)) {
                doors++;
                assertEquals('/', built);
                assertTrue(y >= 1 && y <= 4);
                assertTrue(DomeGeneratorShape.inPod(x, z));
            } else {
                assertTrue(DomeGeneratorShape.shaftCap(x, y, z), x + "," + y + "," + z);
                caps++;
            }
        }
        assertEquals(96, doors, "eight doors three wide, y1..y4");
        assertEquals(4, caps);
        assertEquals('{', S.get(10, 6, 10));
        assertEquals('{', S.get(10, 7, 10));
        assertEquals('{', S.get(10, 8, 10));
        assertEquals('/', S.get(10, 9, 10), "the cap's top is glass, as the roof's cap round it");
        // the soft door glazing is the glazing the walls have: a mirror image of itself across the axis
        for (int y = 1; y <= 4; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++)
            assertEquals(DomeGeneratorShape.door(x, y, z), DomeGeneratorShape.door(S.W - 1 - x, y, z));
    }

    @Test
    void theControllerThePadAndTheWards() {
        assertArrayEquals(new int[] { 10, 2, 6 }, S.controller);
        assertEquals('~', S.get(10, 2, 6));
        assertEquals('K', S.get(10, 1, 6));
        assertEquals('^', S.get(10, 0, 10));
        assertEquals(1, S.lifts.size());
        assertArrayEquals(new int[] { 10, 0, 10 }, S.lifts.get(0));
        UnitLayout u = DomeGeneratorShape.UNITS;
        assertEquals(7, u.size());
        assertEquals(7, u.slots());
        int[][] at = { { 16, 10 }, { 4, 10 }, { 10, 16 }, { 14, 14 }, { 6, 14 }, { 14, 6 }, { 6, 6 } };
        for (int i = 0; i < 7; i++) {
            UnitLayout.Unit w = u.unit(i);
            assertEquals("ward-" + (i + 1), w.name);
            assertEquals("ward", w.kind);
            assertEquals(1, w.slots);
            assertEquals(2, w.cells.size());
            assertArrayEquals(new int[] { at[i][0], 1, at[i][1] }, w.cells.get(0));
            assertArrayEquals(new int[] { at[i][0], 2, at[i][1] }, w.cells.get(1));
            assertEquals(i, u.slotOfSocket(at[i][0], 2, at[i][1]));
        }
        assertEquals(-1, u.unitOfCell(10, 1, 6), "the controller's console is no ward's");
        // the controller is campus (A 4, R 0, Y0 + 42); the pad (A 0, R 0, Y0 + 40)
        assertArrayEquals(new int[] { 4, 0, 42 }, DomeGeneratorShape.podToCampus(10, 2, 6));
        assertArrayEquals(new int[] { 0, 0, 40 }, DomeGeneratorShape.podToCampus(10, 0, 10));
        assertArrayEquals(new int[] { 10, 2, 6 }, DomeGeneratorShape.campusToPod(4, 0, 42));
    }

    /** The generator's walk from the pad, with no ground round the pod (it hangs in the air). */
    @Test
    void everyWardAndTheControllerAreReachedFromThePad() {
        Set<Long> raw = RAW.walk(DomeGeneratorShape.walkStarts(), 2);
        assertEquals(293, raw.size(), "the generator's walk: the ring room and the door openings");
        assertTrue(RAW.controllerReach(raw));
        assertTrue(
            RAW.unitReach(raw)
                .isEmpty());
        Set<Long> built = S.walk(DomeGeneratorShape.walkStarts(), 2);
        assertEquals(293 - 24, built.size(), "the glazed doors are wall now");
        assertTrue(S.controllerReach(built));
        assertTrue(
            S.unitReach(built)
                .isEmpty());
    }

    /** The legs: the generator's cells on the side R >= 0, mirrored to the other side (234 cells). */
    @Test
    void theLegsAreTheGeneratorsMirrored() {
        List<int[]> legs = DomeGeneratorShape.legs();
        assertEquals(DomeGeneratorShape.LEG_CELLS, legs.size());
        StringBuilder half = new StringBuilder();
        for (int i = 0; i < 3 * 39; i++) {
            int[] c = legs.get(i);
            half.append(c[0])
                .append(',')
                .append(c[1])
                .append(',')
                .append(c[2])
                .append(';');
            assertTrue(c[1] > 0, "the first three legs stand on the side R > 0");
        }
        // zlib.crc32 of the first three legs of dome_legs() (30, 90 and 150 degrees), "a,r,dy;" each
        assertEquals(2025965872L, crc(half.toString()));
        for (int k = 3; k < 6; k++) for (int h = 0; h < 39; h++) {
            int[] c = legs.get(k * 39 + h), m = legs.get((5 - k) * 39 + h);
            assertArrayEquals(new int[] { m[0], -m[1], m[2] }, c, "leg " + k + " mirrors leg " + (5 - k));
        }
        Set<Long> seen = new HashSet<>();
        for (int[] c : legs) {
            assertTrue(seen.add(ShapeKit.Shape.pos(c[0] + 64, c[2], c[1] + 64)), "a leg cell twice");
            assertTrue(c[2] >= 1 && c[2] <= DomeGeneratorShape.LEG_TOP);
            assertTrue(c[0] != 0 || c[1] != 0, "the axis stays free for the lift");
        }
        Set<String> feet = new HashSet<>();
        for (int[] f : DomeGeneratorShape.feet()) feet.add(f[0] + "," + f[1]);
        assertEquals(new HashSet<>(java.util.Arrays.asList("12,7", "0,14", "-12,7", "-12,-7", "0,-14", "12,-7")), feet);
        // every top cell lies under the pod's floor
        for (int[] c : legs) if (c[2] == DomeGeneratorShape.LEG_TOP) {
            int[] p = DomeGeneratorShape.campusToPod(c[0], c[1], c[2]);
            assertTrue(DomeGeneratorShape.inPod(p[0], p[2]));
            assertTrue(S.get(p[0], 0, p[2]) != ' ', "under the floor");
        }
        int cells = 0;
        for (int[] col : DomeGeneratorShape.legColumns()) cells += col[3] - col[2] + 1;
        assertEquals(DomeGeneratorShape.LEG_CELLS, cells, "the legs' columns are unbroken");
        // 38 in the generator, whose rounding puts the two R < 0 top cells in the columns under them
        assertEquals(
            40,
            DomeGeneratorShape.legColumns()
                .size());
    }

    @Test
    void theTwoBlueprints() {
        Blueprint m = DomeGeneratorShape.MOORED_BP, f = DomeGeneratorShape.FLOATING_BP;
        for (Blueprint b : new Blueprint[] { m, f }) {
            assertEquals(29, b.width());
            assertEquals(49, b.height());
            assertEquals(25, b.depth());
            assertTrue(b.symmetric());
        }
        assertEquals(
            1261,
            m.cells()
                .size());
        assertEquals(
            1027,
            f.cells()
                .size());
        assertEquals(234, m.count('P'));
        assertEquals(0, f.count('P'));
        assertEquals(m.ctrlA, f.ctrlA);
        assertEquals(m.ctrlB, f.ctrlB);
        assertEquals(m.ctrlC, f.ctrlC);
        CityBuild b = DomeGeneratorShape.BUILD;
        assertArrayEquals(new int[] { 14, 7, 8 }, new int[] { m.ctrlA, m.ctrlB, m.ctrlC });
        assertArrayEquals(new int[] { 14, 7, 8 }, b.cellOf(10, 2, 6));
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            int[] c = b.cellOf(x, y, z);
            assertEquals(ShapeKit.soft(ch) ? ' ' : ch, m.at(c[0], c[1], c[2]), x + "," + y + "," + z);
            assertEquals(ShapeKit.soft(ch) ? ' ' : ch, f.at(c[0], c[1], c[2]));
            assertArrayEquals(new int[] { x, y, z }, b.localOf(c[0], c[1], c[2]));
        }
        for (int[] p : DomeGeneratorShape.legPoints()) {
            int[] c = b.cellOf(p[0], p[1], p[2]);
            assertEquals('P', m.at(c[0], c[1], c[2]));
            assertEquals(' ', f.at(c[0], c[1], c[2]));
        }
        assertEquals(ModuleCores.DOME_GENERATOR, b.corePart() - Parts.MODULE_CORE);
        assertEquals(DomeGeneratorShape.KEY, ModuleCores.KEYS[ModuleCores.DOME_GENERATOR]);
    }

    /**
     * The pod's stages: the floor, the shell (walls and door glazing), the roof (from the cornice up), the fittings.
     */
    @Test
    void everyPodCellHasItsStage() {
        for (int y = 0; y < S.H; y++) for (int z = 0; z < S.D; z++) for (int x = 0; x < S.W; x++) {
            char ch = S.get(x, y, z);
            if (ch == ' ') continue;
            int st = DomeGeneratorShape.stage(x, y, z, ch);
            if (ch == '~') assertEquals(DomeGeneratorShape.Z_COMMISSION, st);
            else if (y == 0) assertEquals(DomeGeneratorShape.Z_FLOOR, st);
            else if (ch == 'K' || ch == 'o') {
                if (DomeGeneratorShape.UNITS.unitOfCell(x, y, z) < 0) assertEquals(DomeGeneratorShape.Z_FIT, st);
            } else if (y <= 5) assertEquals(DomeGeneratorShape.Z_SHELL, st, x + "," + y + "," + z);
            else assertEquals(DomeGeneratorShape.Z_ROOF, st);
        }
        assertEquals(DomeGeneratorShape.Z_ROOF, DomeGeneratorShape.stage(17, 7, 14, 'C'), "the emitter nodes");
        assertEquals(
            6,
            DomeGeneratorShape.nodes()
                .size());
        for (int[] n : DomeGeneratorShape.nodes()) assertEquals('C', S.get(n[0], n[1], n[2]));
        assertEquals(DomeGeneratorShape.Z_LEGS, DomeGeneratorShape.stage(18, -20, 10, 'P'));
    }

    /** What it takes of a campus, for every front: the footprint, the leg columns, the protected boxes, the height. */
    @Test
    void theEnvelope() {
        int cx = 100, y0 = 64, cz = -200;
        int[][] fronts = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
        for (int[] f : fronts) {
            int[] fp = DomeGeneratorShape.footprint(cx, cz, f[0], f[1], 0);
            int wx = fp[2] - fp[0] + 1, wz = fp[3] - fp[1] + 1;
            // A -12..12 (25) by R -14..14 (29)
            assertEquals(f[0] == 0 ? 29 : 25, wx);
            assertEquals(f[0] == 0 ? 25 : 29, wz);
            List<int[]> keep = DomeGeneratorShape.keepOut(cx, y0, cz, f[0], f[1]);
            assertEquals(40, keep.size());
            List<int[]> prot = DomeGeneratorShape.protect(cx, y0, cz, f[0], f[1]);
            assertEquals(41, prot.size());
            for (int i = 0; i < 40; i++) {
                int[] k = keep.get(i), p = prot.get(i);
                assertEquals(6, k.length, "a leg column over its leg's heights only");
                assertEquals(k[0], p[0]);
                assertEquals(k[1], p[2]);
                assertEquals(k[4], p[1]);
                assertEquals(k[5], p[4]);
                assertTrue(p[1] >= y0 + 1 && p[4] <= y0 + DomeGeneratorShape.LEG_TOP, "the forum's tiles stay free");
            }
            int[] pod = prot.get(40);
            assertEquals(y0 + 40, pod[1]);
            assertEquals(y0 + 52, pod[4]);
            assertEquals(23, pod[3] - pod[0] + 1);
            assertEquals(23, pod[5] - pod[2] + 1);
            // the nexus's dais stays the nexus's: no protected box takes a cell of its column under the pod
            for (int[] p : prot) assertFalse(
                p[0] <= cx && cx <= p[3] && p[2] <= cz && cz <= p[5] && p[1] <= y0 + 25,
                "a box over the nexus");
            int[] c = DomeGeneratorShape.controller(SiteFamily.ZENITH_SITE, cx, y0, cz, f[0], f[1]);
            assertArrayEquals(
                new int[] { cx + 4 * f[0], y0 + 42, cz + 4 * f[1], BuildPlan.facingOrdinal(f[0], f[1]) },
                c);
        }
        assertNull(DomeGeneratorShape.noRoom(64));
        assertNull(DomeGeneratorShape.noRoom(203));
        assertEquals("build.module.why.height", DomeGeneratorShape.noRoom(204));
    }

    /**
     * The forum's paving stays whole under the legs (CITY.md §4.8): every foot stands on a paved forum cell that is no
     * light well, no leg takes a light well's column, and the supply port, the recycle bin and the pylons keep their
     * Y0 + 1 cells.
     */
    @Test
    void theLegsMissTheForumsWellsAndFixtures() {
        for (long seed = 1; seed <= 40; seed++) {
            CampusPlan plan = new CampusPlan(Mix.seed((int) seed * 31, 70, (int) -seed * 17));
            Map<Long, Integer> floor = plan.establishFloor();
            Set<Long> wells = plan.wells();
            for (int[] foot : DomeGeneratorShape.feet()) {
                long k = CampusPlan.key(foot[0], foot[1]);
                assertTrue(floor.containsKey(k), "a foot on the paving, seed " + seed);
                assertFalse(wells.contains(k), "a foot on a light well, seed " + seed);
            }
            for (int[] col : DomeGeneratorShape.legColumns())
                assertFalse(wells.contains(CampusPlan.key(col[0], col[1])), "a leg over a well, seed " + seed);
            for (int[] col : DomeGeneratorShape.legColumns()) {
                for (int[] p : plan.pylons()) assertFalse(p[0] == col[0] && p[1] == col[1], "a leg on a pylon");
                int[] port = plan.supplyPort(), bin = plan.recycleBin();
                if (col[0] == port[0] && col[1] == port[1] || col[0] == bin[0] && col[1] == bin[1])
                    assertTrue(col[2] > 1, "the port and the bin keep their cells");
            }
        }
    }

    /**
     * With the generator standing (its keep-out in the campus's), the nexus's repair plan (the establish plan of
     * phase II) and the forum and refresh plan still hold every floor tile, pylon, the supply port and the recycle bin
     * under the legs, as without it; only the grading columns that reach a leg are left out, and no step and no
     * grading column ever takes a leg's cell.
     */
    @Test
    void theForumUnderTheLegsStaysInTheCampusPlans() {
        int cx = 100, y0 = 64, cz = -200;
        int[][] fronts = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
        int portSteps = 0, legTiles = 0;
        for (int[] f : fronts) {
            int[] ctrl = { cx + 5 * f[0], y0 + 2, cz + 5 * f[1] };
            CampusPlan cp = new CampusPlan(Mix.seed(ctrl[0], ctrl[1], ctrl[2]));
            List<int[]> keep = DomeGeneratorShape.keepOut(cx, y0, cz, f[0], f[1]);
            Set<Long> legCells = new HashSet<>(), legCols = new HashSet<>();
            for (int[] c : DomeGeneratorShape.legs()) {
                int[] w = CampusPlan.toWorld(c[0], c[1], f[0], f[1]);
                legCells.add(cell(cx + w[0], y0 + c[2], cz + w[1]));
                legCols.add(cell(cx + w[0], 0, cz + w[1]));
            }
            BuildPlan.Plan[] with = {
                BuildPlan.establish(cp, cx, y0, cz, f[0], f[1], NexusShape.phase(2), ctrl[0], ctrl[1], ctrl[2], keep),
                BuildPlan.forum(cp, cx, y0, cz, f[0], f[1], keep) };
            BuildPlan.Plan[] without = {
                BuildPlan.establish(cp, cx, y0, cz, f[0], f[1], NexusShape.phase(2), ctrl[0], ctrl[1], ctrl[2]),
                BuildPlan.forum(cp, cx, y0, cz, f[0], f[1]) };
            for (int k = 0; k < with.length; k++) {
                BuildPlan.Plan p = with[k], q = without[k];
                assertEquals(q.steps.size(), p.steps.size(), "every tile and fixture stays, plan " + k);
                for (int i = 0; i < p.steps.size(); i++) {
                    BuildPlan.Step a = p.steps.get(i), b = q.steps.get(i);
                    assertEquals(cell(b.x, b.y, b.z), cell(a.x, a.y, a.z));
                    assertEquals(b.part, a.part);
                    assertFalse(legCells.contains(cell(a.x, a.y, a.z)), "a step on a leg's cell");
                    if (a.part == Parts.SUPPLY_PORT) {
                        int[] port = CampusPlan.toWorld(cp.supplyPort()[0], cp.supplyPort()[1], f[0], f[1]);
                        assertEquals(cell(cx + port[0], y0 + 1, cz + port[1]), cell(a.x, a.y, a.z));
                        portSteps++;
                    }
                    if (a.y == y0 && legCols.contains(cell(a.x, 0, a.z))) legTiles++;
                }
                int left = 0;
                for (BuildPlan.Column c : p.clear) {
                    for (int y = c.yFrom; y <= c.yTo; y++)
                        assertFalse(legCells.contains(cell(c.x, y, c.z)), "a column through a leg: " + c);
                }
                for (BuildPlan.Column c : q.clear) if (c.grade && legCols.contains(cell(c.x, 0, c.z))) left++;
                int graded = 0;
                for (BuildPlan.Column c : p.clear) if (c.grade) graded++;
                int all = 0;
                for (BuildPlan.Column c : q.clear) if (c.grade) all++;
                assertTrue(left > 0 && graded < all && all - graded <= left, "only leg columns are not graded");
            }
        }
        assertEquals(8, portSteps, "the supply port in both plans, every front");
        assertEquals(8 * 40, legTiles, "the forum's tiles under the legs, every leg column");
    }

    private static long cell(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) y & 0xFFF) << 26 | (long) z & 0x3FFFFFF;
    }
}
