package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;

import org.junit.jupiter.api.Test;

/**
 * The root's shapes against their generator ({@code city/gen/b_root.py}): the Tidal Well cell for cell (a checksum of
 * every layer), its counts, mirror-symmetric but for the soft helix ramp, the lid open over the lift, the walls and
 * their bands, the island with its pad and controller, the eight tide taps, the walk from the island's pad, the rings
 * out of reach; the deep root and the ley core as data; the stages; and what the well takes of a campus; and since
 * 0.14.1 the shallower wells over low ground ({@code tidal_well(D)}), their taps, walks, rings and stages.
 */
class RootShapeTest {

    private static final ShapeKit.Shape S = RootShape.SHAPE;

    private static long crc(List<String> rows) {
        CRC32 c = new CRC32();
        c.update(
            String.join("\n", rows)
                .getBytes(StandardCharsets.UTF_8));
        return c.getValue();
    }

    private static Map<Character, Integer> counts(Object... pairs) {
        Map<Character, Integer> want = new TreeMap<>();
        for (int i = 0; i < pairs.length; i += 2) want.put((Character) pairs[i], (Integer) pairs[i + 1]);
        return want;
    }

    @Test
    void theWellIsTheGenerators() {
        // zlib.crc32 of tidal_well()'s layers, rows from the front, y0 first, each row x 0..24
        assertEquals(996641253L, crc(RootShape.rows(S)));
        assertEquals(
            counts(
                '-',
                1,
                '=',
                84,
                'B',
                677,
                'F',
                176,
                'K',
                1,
                'L',
                144,
                'R',
                76,
                'W',
                1440,
                '^',
                1,
                'd',
                196,
                'n',
                264,
                'o',
                8,
                'u',
                20,
                'w',
                264,
                '{',
                64,
                '|',
                36,
                '}',
                54,
                '~',
                1),
            S.counts());
        assertEquals(3268, S.checkedCount());
        assertEquals(238, S.softCount());
        assertEquals(1, S.airCount());
        assertEquals(3507, S.steps());
        assertTrue(
            S.symmetric()
                .isEmpty(),
            "mirror-symmetric: the helix ramp is soft");
        assertEquals(25, S.W);
        assertEquals(26, S.H);
        assertEquals(25, S.D);
    }

    /** The two shapes the root grows into later, as data now: the generator's cells exactly. */
    @Test
    void theDeepRootAndTheLeyCoreAreTheGenerators() {
        ShapeKit.Shape deep = RootShape.deepRoot(), core = RootShape.leyCore();
        assertEquals(2817673059L, crc(RootShape.rows(deep)));
        assertEquals(21006675L, crc(RootShape.rows(core)));
        assertEquals(39, deep.H);
        assertEquals(57, core.H);
        assertEquals(5028, deep.checkedCount());
        assertEquals(5826, core.checkedCount());
        assertEquals(238, deep.softCount());
        assertEquals(238, core.softCount());
        // the well's floor (y13 of the deep root) is the well's slab with a grate ring and the lift's way through
        for (int z = 0; z < 25; z++) for (int x = 0; x < 25; x++) for (int y = 0; y < 26; y++) {
            char w = S.get(x, y, z), d = deep.get(x, y + 13, z);
            // the slab's grate ring, its open centre, and the deep root's ring under it outside the well
            if (y == 0 && (d == ':' || x == 12 && z == 12 || !RootShape.inWell(x, z))) continue;
            assertEquals(w, d, "the well over the deep root at " + x + "," + y + "," + z);
        }
    }

    @Test
    void theLidTheWallsAndTheSlab() {
        for (int z = 0; z < 25; z++) for (int x = 0; x < 25; x++) {
            boolean in = RootShape.inWell(x, z), wall = RootShape.wall(x, z);
            assertEquals(in, ShapeKit.octo(x - 12, z - 12, 11, 15));
            if (!in) {
                for (int y = 0; y < 26; y++) assertEquals(' ', S.get(x, y, z), "nothing outside the octagon");
                continue;
            }
            char lid = S.get(x, RootShape.LID_Y, z), slab = S.get(x, RootShape.SLAB_Y, z);
            if (x == 12 && z == 12) assertEquals('-', lid, "the lid's centre is open for the lift");
            else assertEquals(wall ? 'F' : 'B', lid);
            assertEquals(wall ? 'F' : 'B', slab);
            for (int y = 1; y < 25; y++) {
                assertEquals(RootShape.shell(x, y, z), wall);
                assertEquals(RootShape.inside(x, y, z), !wall);
                if (!wall) continue;
                char ch = S.get(x, y, z);
                boolean rib = x == 12 || z == 12 || Math.abs(x - 12) == Math.abs(z - 12);
                if (y == 6 || y == 12 || y == 18) assertEquals('w', ch, "a flux-layer band's dark course");
                else if (y == 7 || y == 13 || y == 19) assertEquals('n', ch, "its cornice");
                else assertEquals(rib ? 'L' : 'W', ch);
            }
        }
        assertArrayEquals(new int[] { 6, 12, 18 }, RootShape.BANDS);
        // the lift's way: the island's pad, then open up to the lid's open centre under the dais
        assertEquals('^', S.get(12, 1, 12));
        for (int y = 2; y < 25; y++) assertEquals(' ', S.get(12, y, 12), "the shaft over the pad is open at y" + y);
        assertEquals(
            88,
            ShapeKit.thickBoundary(columns())
                .size(),
            "the wall ring");
        assertEquals(417, columns().size(), "the octagon");
    }

    private static Set<Long> columns() {
        Set<Long> out = new java.util.LinkedHashSet<>();
        for (int z = 0; z < 25; z++)
            for (int x = 0; x < 25; x++) if (RootShape.inWell(x, z)) out.add(ShapeKit.key(x, z));
        return out;
    }

    @Test
    void theIslandThePadTheControllerAndTheTaps() {
        assertArrayEquals(new int[] { 12, 3, 10 }, S.controller);
        assertEquals('~', S.get(12, 3, 10));
        assertEquals('K', S.get(12, 2, 10), "over its console, one higher than a hall's (the island is a dais)");
        assertEquals('B', S.get(12, 1, 10), "on the island");
        assertEquals(1, S.lifts.size());
        assertArrayEquals(new int[] { 12, 1, 12 }, S.lifts.get(0));
        // campus (A 2, R 0, Y0 - 23), facing +A; the pad (A 0, R 0, Y0 - 25)
        assertArrayEquals(new int[] { 2, 0, -23 }, RootShape.toCampus(12, 3, 10));
        assertArrayEquals(new int[] { 0, 0, -25 }, RootShape.toCampus(12, 1, 12));
        assertArrayEquals(new int[] { 12, 3, 10 }, RootShape.fromCampus(2, 0, -23));
        assertArrayEquals(new int[] { 0, 0, -1 }, RootShape.toCampus(12, RootShape.LID_Y, 12), "the lid's centre");
        UnitLayout u = RootShape.UNITS;
        assertEquals(8, u.size());
        assertEquals(8, u.slots());
        // the generator's taps, in its order: clockwise from the front right
        int[][] at = { { 15, 5 }, { 19, 9 }, { 19, 15 }, { 15, 19 }, { 9, 19 }, { 5, 15 }, { 5, 9 }, { 9, 5 } };
        for (int i = 0; i < 8; i++) {
            UnitLayout.Unit t = u.unit(i);
            assertEquals("tap-" + (i + 1), t.name);
            assertEquals("tap", t.kind);
            assertEquals(1, t.slots);
            assertEquals(1, t.cells.size());
            assertArrayEquals(new int[] { at[i][0], 1, at[i][1] }, t.cells.get(0));
            assertEquals('o', S.get(at[i][0], 1, at[i][1]));
            assertEquals(8, RootShape.onorm(at[i][0] - 12, at[i][1] - 12), "set into the pool's rim");
            assertEquals(i, u.slotOfSocket(at[i][0], 1, at[i][1]));
        }
        assertEquals(-1, u.unitOfCell(12, 2, 10), "the controller's console is no tap's");
    }

    /** The generator's walk from the island's pad: the walkway, the bridges, the ramp up to y21. */
    @Test
    void theControllerAndEveryTapAreReachedFromThePad() {
        Set<Long> reached = S.walk(RootShape.walkStarts(), 2);
        assertEquals(414, reached.size(), "the generator's walk");
        assertTrue(S.controllerReach(reached));
        assertTrue(
            S.unitReach(reached)
                .isEmpty());
        int top = 0;
        for (long st : reached) top = Math.max(top, ShapeKit.Shape.stateH(st));
        assertEquals(42, top, "the helix ramp climbs to y21, four under the lid");
    }

    /** The tidal rings give way to the rendered rings and let players through: nobody stands near enough to tell. */
    @Test
    void theRingsAreOutOfReach() {
        Set<Long> reached = S.walk(RootShape.walkStarts(), 2);
        List<String> near = new ArrayList<>();
        int[][] round = { { 0, 0 }, { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (long st : reached) {
            int x = ShapeKit.Shape.stateX(st), z = ShapeKit.Shape.stateZ(st), feet = ShapeKit.Shape.stateH(st) / 2;
            for (int[] d : round) for (int y = feet - 1; y <= feet + 2; y++) {
                if (RootShape.PASSES.indexOf(S.get(x + d[0], y, z + d[1])) >= 0)
                    near.add((x + d[0]) + "," + y + "," + (z + d[1]));
            }
        }
        assertTrue(near.isEmpty(), "rings within reach: " + near);
        assertEquals("R", RootShape.DISSOLVES);
        // three rings of a cell's width over the pool at y6, y11 and y16
        int[] perRing = new int[3];
        for (int y = 0; y < 26; y++) for (int z = 0; z < 25; z++) for (int x = 0; x < 25; x++) {
            if (S.get(x, y, z) != 'R') continue;
            int k = java.util.Arrays.binarySearch(RootShape.RING_Y, y);
            assertTrue(k >= 0, "a ring segment off its layers at y" + y);
            double d = ShapeKit.hypot(x - 12, z - 12);
            assertTrue(d >= RootShape.RING_R[k] && d < RootShape.RING_R[k] + 1);
            perRing[k]++;
        }
        assertEquals(76, perRing[0] + perRing[1] + perRing[2]);
        for (int n : perRing) assertTrue(n > 0);
    }

    @Test
    void everyCellHasItsStage() {
        int sink = 0, floor = 0, fit = 0, commission = 0;
        for (int y = 0; y < 26; y++) for (int z = 0; z < 25; z++) for (int x = 0; x < 25; x++) {
            char ch = S.get(x, y, z);
            if (ch == ' ') continue;
            if (ch != '-' && ch != '~') assertTrue(ShapeKit.partOf(ch) >= 0, "a legend part: " + ch);
            int st = RootShape.stage(x, y, z, ch);
            if (st == RootShape.R_SINK) {
                assertTrue(RootShape.shell(x, y, z));
                sink++;
            } else if (st == RootShape.R_FLOOR) {
                assertEquals(RootShape.FLOOR_Y, y);
                floor++;
            } else if (st == RootShape.R_FIT) {
                assertNotEquals(0, RootShape.fitRank(x, y, z, ch));
                fit++;
            } else {
                assertEquals(RootShape.R_COMMISSION, st);
                commission++;
            }
        }
        assertEquals(417 * 2 + 88 * 24, sink, "lid, slab and walls");
        assertEquals(237 + 8, floor, "island, pad, bridges, rim, walkway, and the taps (built as units in FIT)");
        assertEquals(1 + 76 + 154 + 84, fit, "console, rings, ramp and rails, water");
        assertEquals(1, commission);
        assertTrue(RootShape.fitRank(12, 2, 10, 'K') < RootShape.fitRank(0, 6, 0, 'R'));
        assertTrue(RootShape.fitRank(0, 6, 0, 'R') < RootShape.fitRank(0, 3, 0, '{'));
        assertTrue(RootShape.fitRank(0, 1, 0, '=') > 0, "the water after the taps");
    }

    /** What the well takes of a campus: nothing on the ground, its own box under it, and enough depth. */
    @Test
    void theEnvelope() {
        assertNull(RootShape.noRoom(32), "the slab at y6, over the bedrock");
        // 0.14.1: over lower ground the well is shallower instead of being refused; under Y0 14 none fits
        assertNull(RootShape.noRoom(31));
        assertNull(RootShape.noRoom(14), "the shallowest well's slab at y6");
        assertEquals("build.module.why.depth", RootShape.noRoom(13));
        assertEquals("build.module.why.depth", RootShape.noRoom(10));
        assertEquals(32, RootShape.LOWEST_Y0);
        assertEquals(14, RootShape.MIN_Y0);
        assertTrue(
            RootShape.keepOut()
                .isEmpty(),
            "the forum over it stays the campus's");
        int cx = 100, y0 = 70, cz = -40;
        for (int[] f : new int[][] { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } }) {
            List<int[]> p = RootShape.protect(cx, y0, cz, f[0], f[1]);
            assertEquals(1, p.size());
            assertArrayEquals(new int[] { cx - 11, y0 - 26, cz - 11, cx + 11, y0 - 1, cz + 11 }, p.get(0));
            assertArrayEquals(
                new int[] { cx - 12, cz - 12, cx + 12, cz + 12 },
                RootShape.footprint(cx, cz, f[0], f[1], 1));
            // the controller (A 2, R 0, Y0 - 23) facing the nexus's front
            int[] c = RootShape.controller(SiteFamily.ROOT_SITE, cx, y0, cz, f[0], f[1]);
            assertArrayEquals(
                new int[] { cx + 2 * f[0], y0 - 23, cz + 2 * f[1], BuildPlan.facingOrdinal(f[0], f[1]) },
                c);
        }
        assertThrows(IllegalArgumentException.class, () -> RootShape.controller(4, cx, y0, cz, 0, -1));
        // over low ground: the controller and the protected box follow the well's depth
        for (int low : new int[] { 14, 19, 20, 25, 26, 31 }) {
            int d = RootShape.depth(low);
            assertArrayEquals(
                new int[] { cx, low - d + 3, cz - 2, BuildPlan.facingOrdinal(0, -1) },
                RootShape.controller(SiteFamily.ROOT_SITE, cx, low, cz, 0, -1));
            assertArrayEquals(
                new int[] { cx - 11, low - d, cz - 11, cx + 11, low - 1, cz + 11 },
                RootShape.protect(cx, low, cz, 0, -1)
                    .get(0));
        }
        assertArrayEquals(new int[] { SiteFamily.ROOT_SITE }, RootShape.SITES);
        assertEquals(ModuleCores.ROOT, RootShape.BUILD.corePart() - Parts.MODULE_CORE);
        assertEquals(RootShape.KEY, ModuleCores.KEYS[ModuleCores.ROOT]);
        assertFalse(SiteFamily.ROOT.hasSite(SiteFamily.ZENITH_SITE));
    }

    /**
     * The well's depth over low ground (0.14.1, CITY.md §1.3 Vertical): the deepest of 26, 20, 14 and 8 whose floor
     * slab stays at y 6 or over, so the slab never reaches the bedrock for a base a core may be placed at (Y0 14 up).
     */
    @Test
    void theWellsDepthStepsUpOverLowGround() {
        assertArrayEquals(new int[] { 26, 20, 14, 8 }, RootShape.depths());
        int[][] table = { { 255, 26 }, { 79, 26 }, { 32, 26 }, { 31, 20 }, { 26, 20 }, { 25, 14 }, { 20, 14 },
            { 19, 8 }, { 14, 8 } };
        for (int[] t : table) assertEquals(t[1], RootShape.depth(t[0]), "Y0 " + t[0]);
        for (int y0 = RootShape.MIN_Y0; y0 <= 255; y0++) {
            int d = RootShape.depth(y0);
            assertTrue(y0 - d >= RootShape.LOWEST_SLAB, "the slab over the bedrock at " + y0);
            for (int deeper : RootShape.depths()) if (deeper > d) assertTrue(y0 - deeper < RootShape.LOWEST_SLAB);
            assertEquals(RootShape.well(d).name, RootShape.shapeFor(y0));
        }
        assertEquals(RootShape.WELL, RootShape.well(26).name);
        assertEquals(RootShape.WELL_20, RootShape.well(20).name);
        assertEquals(RootShape.WELL_14, RootShape.well(14).name);
        assertEquals(RootShape.WELL_8, RootShape.well(8).name);
        assertEquals(20, RootShape.named("well_20").depth);
        assertNull(RootShape.named(RootShape.DEEP), "the deep root is no well");
        assertThrows(IllegalArgumentException.class, () -> RootShape.well(25));
        RootShape.Well full = RootShape.well(26);
        assertSame(RootShape.SHAPE, full.shape);
        assertSame(RootShape.BUILD, full.build);
        assertSame(RootShape.BLUEPRINT, full.blueprint);
    }

    /** The shallower wells are the generator's {@code tidal_well(D)} cell for cell (b_root.py, 0.14.1). */
    @Test
    void theShallowerWellsAreTheGenerators() {
        // {depth, zlib.crc32 of its layers, checked, soft, steps, the generator's walk}
        long[][] want = { { 26, 996641253L, 3268, 238, 3507, 414 }, { 20, 1024968167L, 2740, 237, 2978, 407 },
            { 14, 607618077L, 2184, 230, 2415, 392 }, { 8, 1009653990L, 1608, 194, 1803, 307 } };
        for (long[] w : want) {
            RootShape.Well well = RootShape.well((int) w[0]);
            ShapeKit.Shape s = well.shape;
            String at = " at depth " + w[0];
            assertEquals(w[1], crc(RootShape.rows(s)), "tidal_well(" + w[0] + ")");
            assertEquals(w[2], s.checkedCount(), "checked" + at);
            assertEquals(w[3], s.softCount(), "soft" + at);
            assertEquals(1, s.airCount(), "the lid's open centre" + at);
            assertEquals(w[4], s.steps(), "steps" + at);
            assertEquals(w[0], s.H);
            assertTrue(
                s.symmetric()
                    .isEmpty(),
                "mirror-symmetric" + at);
            // the walk from the island's pad reaches the controller and every tap
            Set<Long> reached = s.walk(RootShape.walkStarts(), 2);
            assertEquals(w[5], reached.size(), "the generator's walk" + at);
            assertTrue(s.controllerReach(reached));
            assertTrue(
                s.unitReach(reached)
                    .isEmpty());
            // the blueprint is the shape's box with its soft cells left out, its controller on the island
            assertEquals(well.depth, well.blueprint.height());
            assertEquals(
                s.checkedCount() + 1,
                well.blueprint.cells()
                    .size(),
                "the checked cells and the lid's open centre" + at);
            assertArrayEquals(new int[] { 12, 3, 10 }, s.controller);
            assertArrayEquals(new int[] { 12, 3, 10 }, well.build.controller());
        }
    }

    /**
     * Every depth keeps the full well's floor level, island, controller and its eight taps on the same cells in the
     * same order (so a tap's slot is the same), and the lid with its open centre at y D - 1.
     */
    @Test
    void theTapsKeepTheirCellsAndOrderAtEveryDepth() {
        for (RootShape.Well w : RootShape.wells()) {
            UnitLayout u = w.units;
            assertEquals(8, u.size());
            assertEquals(8, u.slots());
            for (int i = 0; i < 8; i++) {
                UnitLayout.Unit t = u.unit(i), full = RootShape.UNITS.unit(i);
                assertEquals(full.name, t.name);
                assertEquals(full.kind, t.kind);
                assertEquals(full.slots, t.slots);
                assertArrayEquals(full.cells.get(0), t.cells.get(0), "tap " + i + " at depth " + w.depth);
                assertEquals(i, u.slotOfSocket(t.cells.get(0)[0], 1, t.cells.get(0)[2]));
            }
            for (int z = 0; z < 25; z++) for (int x = 0; x < 25; x++) {
                assertEquals(RootShape.SHAPE.get(x, RootShape.FLOOR_Y, z), w.cell(x, RootShape.FLOOR_Y, z), "floor");
                assertEquals(RootShape.SHAPE.get(x, RootShape.SLAB_Y, z), w.cell(x, RootShape.SLAB_Y, z), "slab");
                if (!RootShape.inWell(x, z)) continue;
                char lid = w.cell(x, w.lidY, z);
                if (x == 12 && z == 12) assertEquals('-', lid, "the lid's centre is open for the lift");
                else assertEquals(RootShape.wall(x, z) ? 'F' : 'B', lid);
                for (int y = 1; y < w.lidY; y++) {
                    assertEquals(RootShape.wall(x, z), w.shell(x, y, z));
                    assertEquals(!RootShape.wall(x, z), w.inside(x, y, z));
                }
            }
            assertEquals('K', w.cell(12, 2, 10));
            assertEquals('~', w.cell(12, 3, 10));
            assertEquals(w.depth - 1, w.lidY);
            assertArrayEquals(new int[] { 2, 0, 3 - w.depth }, w.toCampus(12, 3, 10));
            assertArrayEquals(new int[] { 12, 3, 10 }, w.fromCampus(2, 0, 3 - w.depth));
        }
    }

    /**
     * A shallower well keeps the bands and rings that fit under its lid (20 deep: two bands, three rings; 14 deep: one
     * band, two rings; 8 deep: none), its rings out of reach, its ramp up to four under the lid, every cell staged.
     */
    @Test
    void theBandsRingsAndRampFitUnderTheLid() {
        int[][] bands = { { 6, 12, 18 }, { 6, 12 }, { 6 }, {} };
        int[][] rings = { { 6, 11, 16 }, { 6, 11, 16 }, { 6, 11 }, {} };
        int[] perRing = { 76, 76, 48, 0 };
        for (int i = 0; i < 4; i++) {
            RootShape.Well w = RootShape.wells()
                .get(i);
            assertArrayEquals(bands[i], w.bands(), "bands at depth " + w.depth);
            assertArrayEquals(rings[i], w.ringY(), "rings at depth " + w.depth);
            assertEquals(rings[i].length, w.ringR().length);
            int ringCells = 0;
            for (int y = 0; y < w.depth; y++) for (int z = 0; z < 25; z++) for (int x = 0; x < 25; x++) {
                char ch = w.cell(x, y, z);
                if (ch == RootShape.RING) {
                    ringCells++;
                    int k = java.util.Arrays.binarySearch(w.ringY(), y);
                    assertTrue(k >= 0, "a ring off its layers at y" + y);
                }
                if (ch == ' ') continue;
                int st = w.stage(x, y, z, ch);
                assertTrue(st >= RootShape.R_SINK && st <= RootShape.R_COMMISSION);
                if (st == RootShape.R_SINK) assertTrue(w.shell(x, y, z));
            }
            assertEquals(perRing[i], ringCells);
            Set<Long> reached = w.shape.walk(RootShape.walkStarts(), 2);
            int top = 0;
            for (long st : reached) {
                top = Math.max(top, ShapeKit.Shape.stateH(st));
                int x = ShapeKit.Shape.stateX(st), z = ShapeKit.Shape.stateZ(st), feet = ShapeKit.Shape.stateH(st) / 2;
                for (int[] d : new int[][] { { 0, 0 }, { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } })
                    for (int y = feet - 1; y <= feet + 2; y++)
                        assertFalse(w.cell(x + d[0], y, z + d[1]) == RootShape.RING, "a ring within reach");
            }
            assertTrue(top / 2 + 2 <= w.lidY, "a head clear of the lid at depth " + w.depth);
        }
    }
}
