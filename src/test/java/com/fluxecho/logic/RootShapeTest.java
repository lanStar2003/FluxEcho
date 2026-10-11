package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * out of reach; the deep root and the ley core as data; the stages; and what the well takes of a campus.
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
        assertEquals("build.module.why.depth", RootShape.noRoom(31));
        assertEquals("build.module.why.depth", RootShape.noRoom(10));
        assertEquals(32, RootShape.LOWEST_Y0);
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
        assertArrayEquals(new int[] { SiteFamily.ROOT_SITE }, RootShape.SITES);
        assertEquals(ModuleCores.ROOT, RootShape.BUILD.corePart() - Parts.MODULE_CORE);
        assertEquals(RootShape.KEY, ModuleCores.KEYS[ModuleCores.ROOT]);
        assertFalse(SiteFamily.ROOT.hasSite(SiteFamily.ZENITH_SITE));
    }
}
