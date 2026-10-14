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
        assertEquals("RC", RootShape.DISSOLVES, "the rings, and (0.16.0) the deep root's conduits");
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
            // 0.16.0: over Y0 45 the box takes in the ground the deep root's chamber grows into
            assertArrayEquals(new int[] { cx - 12, y0 - 39, cz - 12, cx + 12, y0 - 1, cz + 12 }, p.get(0));
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

    // ---- the deep root (0.16.0, "root by room")

    /** The highest lift pad of a shape as the generator's walk start. */
    private static List<int[]> topPad(ShapeKit.Shape s) {
        int[] top = null;
        for (int[] p : s.lifts) if (top == null || p[1] > top[1]) top = p;
        return java.util.Collections.singletonList(new int[] { top[0], top[2], 2 * (top[1] + 1) });
    }

    /**
     * Every form of the deep root is its generator's ({@code deep_root(h)}, {@code deep_root_in(D)}, b_root.py 0.16.0)
     * cell for cell: the checksum of its layers, its height, its counts, mirror-symmetric; its units the well's taps on
     * the well's cells as slots 0 to 7, then the eight bays as slots 8 to 15; the well's controller; and the
     * generator's walk from its top pad reaching the controller and every unit, every tread stood on.
     */
    @Test
    void theDeepFormsAreTheGenerators() {
        // {chamber, well depth, zlib.crc32 of its layers, height, checked, soft, air, conduits, walk}
        long[][] want = { { 13, 26, 2817673059L, 39, 5028, 238, 2, 48, 791 },
            { 7, 26, 2020465489L, 33, 4428, 238, 2, 24, 791 }, { 0, 26, 3203471782L, 26, 3380, 238, 1, 96, 411 },
            { 0, 20, 568924105L, 20, 2828, 237, 1, 72, 403 }, { 0, 14, 963591689L, 14, 2248, 230, 1, 48, 389 },
            { 0, 8, 2007386574L, 8, 1640, 194, 1, 16, 305 } };
        String[] names = { RootShape.DEEP, RootShape.ROOT_7, RootShape.ROOT_IN, RootShape.ROOT_IN_20,
            RootShape.ROOT_IN_14, RootShape.ROOT_IN_8 };
        assertEquals(
            6,
            RootShape.deeps()
                .size());
        for (int i = 0; i < want.length; i++) {
            long[] w = want[i];
            RootShape.Deep d = RootShape.deeps()
                .get(i);
            ShapeKit.Shape s = d.shape;
            String at = " of " + names[i];
            assertEquals(names[i], d.name);
            assertSame(d, RootShape.deep(names[i]));
            assertEquals(w[0], d.h, "chamber" + at);
            assertEquals(w[1], d.well.depth, "well" + at);
            assertEquals(w[2], crc(RootShape.rows(s)), "the generator's cells" + at);
            assertEquals(w[3], s.H, "height" + at);
            assertEquals(w[4], s.checkedCount(), "checked" + at);
            assertEquals(w[5], s.softCount(), "soft" + at);
            assertEquals(w[6], s.airCount(), "air" + at);
            assertEquals(
                w[7],
                (long) s.counts()
                    .get(RootShape.CONDUIT),
                "conduits" + at);
            assertTrue(
                s.symmetric()
                    .isEmpty(),
                "mirror-symmetric" + at);
            assertArrayEquals(new int[] { 12, d.h + 3, 10 }, s.controller, "the well's controller" + at);
            assertArrayEquals(new int[] { 2, 0, 3 - d.well.depth }, d.toCampus(12, d.h + 3, 10));
            assertArrayEquals(new int[] { 12, d.h + 3, 10 }, d.fromCampus(2, 0, 3 - d.well.depth));
            UnitLayout u = d.units;
            assertEquals(16, u.size());
            assertEquals(RootShape.SLOTS, u.slots());
            for (int k = 0; k < 8; k++) {
                UnitLayout.Unit t = u.unit(k), wt = RootShape.UNITS.unit(k);
                assertEquals(wt.name, t.name);
                assertEquals("tap", t.kind);
                int[] c = wt.cells.get(0);
                assertArrayEquals(new int[] { c[0], c[1] + d.h, c[2] }, t.cells.get(0), "tap " + k + at);
                assertEquals(k, u.slotOfSocket(c[0], c[1] + d.h, c[2]));
                UnitLayout.Unit b = u.unit(8 + k);
                assertEquals("bay-" + (k + 1), b.name);
                assertEquals("bay", b.kind);
                assertEquals(1, b.slots);
                assertEquals(8 + k, b.firstSlot);
                assertEquals(2, b.cells.size());
                int[] plinth = b.cells.get(0), socket = b.cells.get(1);
                assertEquals('p', s.get(plinth[0], plinth[1], plinth[2]));
                assertEquals('o', s.get(socket[0], socket[1], socket[2]));
                assertEquals(plinth[1] + 1, socket[1]);
                assertEquals(d.h > 0 ? 1 : 2, plinth[1], "the bay's plinth" + at);
                assertTrue(d.bay(plinth[0], plinth[1], plinth[2]));
            }
            // the walk from the top pad: the island's (and the chamber's floor below it by the lift)
            Set<Long> reached = s.walk(topPad(s), 2);
            assertEquals(w[8], reached.size(), "the generator's walk" + at);
            assertTrue(s.controllerReach(reached), "the controller" + at);
            assertTrue(
                s.unitReach(reached)
                    .isEmpty(),
                "every unit" + at);
            assertTrue(
                s.idleTreads(reached)
                    .isEmpty(),
                "every tread stood on" + at);
            // its blueprint is its box with the soft cells left out, its build's controller the shape's
            assertEquals(s.H, d.blueprint.height());
            assertEquals(
                s.checkedCount() + s.airCount(),
                d.blueprint.cells()
                    .size());
            assertArrayEquals(s.controller, d.build.controller());
            assertEquals(RootShape.KEY, d.build.key());
            assertEquals(d.h, RootShape.base(d.name));
        }
        assertSame(RootShape.deep(RootShape.DEEP).shape, RootShape.deepRoot(), "the deep root's data is its shape");
        assertNull(RootShape.deep(RootShape.WELL), "a well is no deep root");
        assertNull(RootShape.deep("root_in_9"));
        assertEquals(0, RootShape.base(RootShape.WELL_20));
        // the lift in the chamber forms: the chamber's pad under the island's, the axis open between them
        for (RootShape.Deep d : RootShape.deeps()) {
            List<Integer> pads = new ArrayList<>();
            for (int[] p : d.shape.lifts) {
                assertEquals(12, p[0]);
                assertEquals(12, p[2]);
                pads.add(p[1]);
            }
            java.util.Collections.sort(pads);
            if (d.h == 0) assertEquals(java.util.Collections.singletonList(1), pads, "in-well: the island's pad alone");
            else {
                assertEquals(java.util.Arrays.asList(0, d.h + 1), pads, d.name);
                for (int y = 1; y <= d.h; y++)
                    assertTrue(" -".indexOf(d.cell(12, y, 12)) >= 0, d.name + ": the shaft open at y" + y);
                assertEquals('-', d.cell(12, d.h, 12), "the well's floor slab open over the chamber's pad");
            }
        }
    }

    /**
     * Every form holds its well whole (the RootShapeTest 0.12 check over every form): a chamber changes exactly the 24
     * grate cells and the open centre of the well's floor slab, adds the 64-cell ring round that slab in cells the well
     * leaves empty and its chamber under it; an in-well form changes no cell of the well and adds its bays and
     * conduits in the well's empty cells only.
     */
    @Test
    void everyFormHoldsItsWellWhole() {
        // {changed B to ':', changed B to '-', new cells in blank well cells, cells under the well's slab}
        int[][] want = { { 24, 1, 64, 1697 }, { 24, 1, 64, 1097 }, { 0, 0, 112, 0 }, { 0, 0, 88, 0 }, { 0, 0, 64, 0 },
            { 0, 0, 32, 0 } };
        for (int i = 0; i < want.length; i++) {
            RootShape.Deep d = RootShape.deeps()
                .get(i);
            int grate = 0, centre = 0, blank = 0, under = 0;
            for (int z = 0; z < 25; z++) for (int x = 0; x < 25; x++) {
                for (int y = 0; y < d.h; y++) if (d.cell(x, y, z) != ' ') under++;
                for (int y = 0; y < d.well.depth; y++) {
                    char w = d.well.cell(x, y, z), c = d.cell(x, y + d.h, z);
                    assertEquals(w, d.wellCell(x, y + d.h, z));
                    if (w == c) continue;
                    if (w == ' ') {
                        blank++;
                        assertTrue(c == 'p' || c == 'o' || c == 'C' || c == 'B', d.name + ": what it adds " + c);
                    } else if (w == 'B' && c == ':') {
                        grate++;
                        assertEquals(0, y, "the grate in the well's floor slab");
                        assertEquals(3, RootShape.onorm(x - 12, z - 12));
                    } else if (w == 'B' && c == '-') {
                        centre++;
                        assertArrayEquals(new int[] { 12, 0, 12 }, new int[] { x, y, z });
                    } else throw new AssertionError(d.name + " changes the well at " + x + "," + y + "," + z);
                }
            }
            assertArrayEquals(want[i], new int[] { grate, centre, blank, under }, d.name);
        }
    }

    /**
     * The rule over every base layer of the band (CITY.md §1.3 Vertical, 0.16.0): with the well the base layer gives,
     * root_in_8 over Y0 14..19, root_in_14 20..25, root_in_20 26..31, root_in 32..38, root_7 39..44 and root 45..225,
     * no cell under y 6, the top at Y0 - 1, the controller where the well's stands; and over the full wells 0.14.0 let
     * players dig by hand over low ground (Y0 26..31) the in-well form, in the well's own air.
     */
    @Test
    void everyBaseLayerOfTheBandHasADeepRoot() {
        assertArrayEquals(new int[] { 13, 7 }, RootShape.chambers());
        String[][] runs = { { "14", "19", RootShape.ROOT_IN_8 }, { "20", "25", RootShape.ROOT_IN_14 },
            { "26", "31", RootShape.ROOT_IN_20 }, { "32", "38", RootShape.ROOT_IN }, { "39", "44", RootShape.ROOT_7 },
            { "45", "225", RootShape.DEEP } };
        int covered = 0;
        for (String[] r : runs) for (int y0 = Integer.parseInt(r[0]); y0 <= Integer.parseInt(r[1]); y0++) {
            RootShape.Well w = RootShape.forBase(y0);
            RootShape.Deep d = RootShape.deepFor(y0, w);
            assertEquals(r[2], d.name, "Y0 " + y0);
            assertSame(w, d.well);
            int base = y0 - w.depth - d.h, lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int y = 0; y < d.shape.H; y++) for (int z = 0; z < 25; z++) for (int x = 0; x < 25; x++) {
                if (d.cell(x, y, z) == ' ') continue;
                lo = Math.min(lo, base + y);
                hi = Math.max(hi, base + y);
            }
            assertTrue(lo >= RootShape.LOWEST_SLAB, "nothing in the bedrock at Y0 " + y0 + ": " + lo);
            assertEquals(y0 - 1, hi, "the lid at Y0 - 1 at " + y0);
            assertEquals(y0 - w.depth + 3, base + d.shape.controller[1], "the well's controller at " + y0);
            assertArrayEquals(
                w.controller(SiteFamily.ROOT_SITE, 0, y0, 0, 0, -1),
                d.controller(SiteFamily.ROOT_SITE, 0, y0, 0, 0, -1));
            covered++;
        }
        assertEquals(212, covered, "every base layer of the band, 14 to 225");
        for (int y0 = RootShape.MIN_Y0; y0 <= 38; y0++) assertEquals(0, RootShape.chamber(y0, 26), "Y0 " + y0);
        assertEquals(7, RootShape.chamber(39, 26));
        assertEquals(13, RootShape.chamber(45, 26));
        // the 0.14.0 full wells dug by hand over low ground: the in-well form, nothing dug
        for (int y0 = 26; y0 <= 31; y0++) {
            RootShape.Deep d = RootShape.deepFor(y0, RootShape.well(RootShape.DEPTH));
            assertEquals(RootShape.ROOT_IN, d.name, "a hand-dug full well at Y0 " + y0);
            assertEquals(0, d.h);
        }
        // the conduits: the upper half of the room, never lower than 3 over the layer people stand on
        assertEquals(6, RootShape.conduitLen(13, 1));
        assertEquals(3, RootShape.conduitLen(7, 1));
        int[] inWell = { 12, 9, 6, 2 };
        for (int i = 0; i < 4; i++) assertEquals(inWell[i], RootShape.conduitLen(RootShape.depths()[i] - 1, 2));
        assertEquals(RootShape.ROOT_IN_20, RootShape.deepName(0, 20));
        assertThrows(IllegalArgumentException.class, () -> RootShape.deepName(9, 26));
        assertThrows(IllegalArgumentException.class, () -> RootShape.deepName(0, 25));
    }

    /**
     * Every cell of every form has its stage: the chamber's shell SINK (the ring round the well's floor slab, the
     * walls, the floor slab with its pad and veins), its conduits and bays FIT, the well's cells as the well's.
     */
    @Test
    void everyCellOfEveryFormHasItsStage() {
        for (RootShape.Deep d : RootShape.deeps()) {
            int sink = 0, fit = 0;
            for (int y = 0; y < d.shape.H; y++) for (int z = 0; z < 25; z++) for (int x = 0; x < 25; x++) {
                char ch = d.cell(x, y, z);
                if (ch == ' ') continue;
                int st = d.stage(x, y, z, ch);
                assertTrue(st >= RootShape.R_SINK && st <= RootShape.R_COMMISSION);
                if (st == RootShape.R_SINK) {
                    assertTrue(d.shell(x, y, z), d.name + " " + x + "," + y + "," + z);
                    sink++;
                }
                if (ch == RootShape.CONDUIT || d.bay(x, y, z)) {
                    assertEquals(RootShape.R_FIT, st);
                    fit++;
                }
                if (d.inChamber(x, y, z) && !d.bay(x, y, z) && ch != RootShape.CONDUIT)
                    assertEquals(RootShape.R_SINK, st);
            }
            int chamberShell = d.h == 0 ? 0 : 64 + 96 * (d.h - 1) + 481;
            assertEquals(417 * 2 + 88 * (d.well.depth - 2) + chamberShell, sink, d.name);
            assertEquals(
                16 + d.shape.counts()
                    .get(RootShape.CONDUIT),
                fit,
                d.name);
            // the inside a caisson digs: the chamber's 385 columns over its walls' layers, and the well's
            for (int z = 0; z < 25; z++) for (int x = 0; x < 25; x++) for (int y = 0; y < d.shape.H; y++) {
                assertFalse(d.inside(x, y, z) && d.shell(x, y, z));
                if (d.chamberInside(x, y, z)) assertTrue(y > 0 && y < d.h);
            }
        }
    }

    /**
     * What the deep root takes of a campus (0.16.0): its controller is the well's, and with a chamber its box is one
     * wider and reaches down to the chamber's floor slab; in-well it is the well's.
     */
    @Test
    void theDeepRootsEnvelope() {
        int cx = 100, cz = -40;
        for (int[] f : new int[][] { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } }) {
            for (int[] k : new int[][] { { 45, 13 }, { 70, 13 }, { 39, 7 }, { 44, 7 } }) {
                int y0 = k[0];
                assertArrayEquals(
                    new int[] { cx - 12, y0 - 26 - k[1], cz - 12, cx + 12, y0 - 1, cz + 12 },
                    RootShape.protect(cx, y0, cz, f[0], f[1])
                        .get(0),
                    "Y0 " + y0);
            }
            for (int y0 : new int[] { 14, 20, 31, 32, 38 }) {
                int d = RootShape.depth(y0);
                assertArrayEquals(
                    new int[] { cx - 11, y0 - d, cz - 11, cx + 11, y0 - 1, cz + 11 },
                    RootShape.protect(cx, y0, cz, f[0], f[1])
                        .get(0),
                    "Y0 " + y0 + ": the in-well form's box is the well's");
                assertEquals(
                    RootShape.deepFor(y0, RootShape.forBase(y0))
                        .protect(cx, y0, cz, f[0], f[1])
                        .get(0)[1],
                    y0 - d);
            }
        }
    }

    /**
     * When the deepen job is offered (0.16.0, M13 §7.9, {@link RootShape#deepenTo}): a nexus of phase IV whose teams
     * have the deep root's research over a root formed as a well, whose controller stands at the depth of the well it
     * formed as; then the form the rule gives that well. Phase III, no research, a root not formed, one that is the
     * deep root already and a controller at another depth offer nothing. A full well dug by hand over low ground
     * (Y0 28, its controller at Y0 - 23) grows into the roots in the well, not the chamber.
     */
    @Test
    void theDeepenJobIsOfferedOverAFormedWellOfPhaseFour() {
        int full = RootShape.CTRL_Y - RootShape.DEPTH;
        assertEquals(RootShape.DEEP, RootShape.deepenTo(4, true, true, RootShape.WELL, 70, 70 + full));
        assertEquals(RootShape.DEEP, RootShape.deepenTo(5, true, true, RootShape.WELL, 70, 70 + full), "phase V too");
        assertEquals(RootShape.ROOT_7, RootShape.deepenTo(4, true, true, RootShape.WELL, 41, 41 + full));
        assertEquals(RootShape.ROOT_7, RootShape.deepenTo(4, true, true, RootShape.WELL, 39, 39 + full));
        assertEquals(RootShape.ROOT_IN, RootShape.deepenTo(4, true, true, RootShape.WELL, 38, 38 + full));
        assertEquals(RootShape.ROOT_IN, RootShape.deepenTo(4, true, true, RootShape.WELL, 28, 28 - 23), "dug by hand");
        assertEquals(
            RootShape.ROOT_IN_20,
            RootShape.deepenTo(4, true, true, RootShape.WELL_20, 28, 28 + RootShape.CTRL_Y - 20));
        assertEquals(
            RootShape.ROOT_IN_14,
            RootShape.deepenTo(4, true, true, RootShape.WELL_14, 22, 22 + RootShape.CTRL_Y - 14));
        assertEquals(
            RootShape.ROOT_IN_8,
            RootShape.deepenTo(4, true, true, RootShape.WELL_8, 16, 16 + RootShape.CTRL_Y - 8));
        assertNull(RootShape.deepenTo(3, true, true, RootShape.WELL, 70, 70 + full), "phase III");
        assertNull(RootShape.deepenTo(4, false, true, RootShape.WELL, 70, 70 + full), "not researched");
        assertNull(RootShape.deepenTo(4, true, false, RootShape.WELL, 70, 70 + full), "not formed");
        for (RootShape.Deep d : RootShape.deeps())
            assertNull(RootShape.deepenTo(4, true, true, d.name, 70, 70 + full), "already " + d.name);
        assertNull(RootShape.deepenTo(4, true, true, RootShape.WELL, 70, 70 + full + 6), "a controller at 20 deep");
        assertNull(RootShape.deepenTo(4, true, true, RootShape.WELL_20, 70, 70 + full), "a well_20 26 deep");
        assertNull(RootShape.deepenTo(4, true, true, "nothing", 70, 70 + full));
    }
}
