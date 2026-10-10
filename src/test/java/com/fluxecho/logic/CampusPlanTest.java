package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CampusPlanTest {

    /** The four horizontal fronts a nexus can face. */
    private static final int[][] FRONTS = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
    /** The Echo Archive's footprint. */
    private static final int WIDTH = 27, DEPTH = 35;

    private static final CampusPlan PLAN = new CampusPlan(Mix.seed(120, 70, -340));

    /** Seeds of nexus controllers scattered over a world. */
    static List<Long> seeds(int n) {
        List<Long> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(Mix.seed(i * 997 - 20000, 40 + i % 100, i * 131 - 5000));
        return out;
    }

    /** Every cell of a hall footprint, found by scanning the campus. */
    static Set<Long> footprint(int site, int width, int depth) {
        Set<Long> out = new HashSet<>();
        for (int a = -80; a <= 80; a++) for (int r = -80; r <= 80; r++) {
            if (CampusPlan.inHall(site, width, depth, a, r)) out.add(CampusPlan.key(a, r));
        }
        return out;
    }

    @Test
    void rotationIsABijectionThatMatchesTheRingSlots() {
        for (int[] f : FRONTS) {
            Set<Long> seen = new HashSet<>();
            for (int a = -70; a <= 70; a++) for (int r = -70; r <= 70; r++) {
                int[] w = CampusPlan.toWorld(a, r, f[0], f[1]);
                assertArrayEquals(new int[] { a, r }, CampusPlan.toLocal(w[0], w[1], f[0], f[1]));
                assertTrue(seen.add(CampusPlan.key(w[0], w[1])), "two cells on one world offset");
                int[] l = CampusPlan.toLocal(a, r, f[0], f[1]);
                assertArrayEquals(new int[] { a, r }, CampusPlan.toWorld(l[0], l[1], f[0], f[1]));
            }
            // ahead is the front, right is clockwise seen from above: (-fz, fx)
            assertArrayEquals(new int[] { f[0], f[1] }, CampusPlan.toWorld(1, 0, f[0], f[1]));
            assertArrayEquals(new int[] { -f[1], f[0] }, CampusPlan.toWorld(0, 1, f[0], f[1]));
            for (int k = 0; k < RingSlots.SLOTS; k++) {
                int[] ax = CampusPlan.siteAxis(k);
                int len = k % 2 == 0 ? 32 : 23; // 32 * cos 45 rounds to 23 on the diagonals
                assertArrayEquals(
                    RingSlots.offset(k, 32, f[0], f[1]),
                    CampusPlan.toWorld(ax[0] * len, ax[1] * len, f[0], f[1]),
                    "site " + k + " lies on the ray of legacy slot " + k);
            }
        }
    }

    @Test
    void keysRoundTrip() {
        int[] values = { 0, 1, -1, 23, -41, 64, -64, 1000, -1000, Integer.MAX_VALUE, Integer.MIN_VALUE };
        Set<Long> seen = new HashSet<>();
        for (int a : values) for (int r : values) {
            long k = CampusPlan.key(a, r);
            assertEquals(a, CampusPlan.keyA(k));
            assertEquals(r, CampusPlan.keyR(k));
            assertTrue(seen.add(k));
        }
    }

    @Test
    void sitesAndCentres() {
        int[][] axes = { { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 }, { -1, 0 }, { -1, -1 }, { 0, -1 }, { 1, -1 } };
        for (int k = 0; k < 8; k++) assertArrayEquals(axes[k], CampusPlan.siteAxis(k), "site " + k);
        assertArrayEquals(new int[] { -41, 0 }, CampusPlan.moduleCentre(4, 35));
        assertArrayEquals(new int[] { 0, 41 }, CampusPlan.moduleCentre(2, 35));
        assertArrayEquals(new int[] { 0, -41 }, CampusPlan.moduleCentre(6, 35));
        assertArrayEquals(new int[] { 34, 34 }, CampusPlan.diagonalCentre(1));
        assertArrayEquals(new int[] { -34, 34 }, CampusPlan.diagonalCentre(3));
        assertArrayEquals(new int[] { -34, -34 }, CampusPlan.diagonalCentre(5));
        assertArrayEquals(new int[] { 34, -34 }, CampusPlan.diagonalCentre(7));
        assertArrayEquals(CampusPlan.diagonalCentre(3), CampusPlan.moduleCentre(3, 35));
        assertThrows(IllegalArgumentException.class, () -> CampusPlan.diagonalCentre(2));
        for (int site : CampusPlan.HALL_SITES) {
            int[] c = CampusPlan.moduleCentre(site, DEPTH);
            assertTrue(CampusPlan.inHall(site, WIDTH, DEPTH, c[0], c[1]));
            for (int da = -3; da <= 3; da++) for (int dr = -3; dr <= 3; dr++) {
                assertEquals(site, CampusPlan.hallSiteAt(c[0] + da, c[1] + dr, DEPTH));
            }
            assertEquals(-1, CampusPlan.hallSiteAt(c[0] + 4, c[1], DEPTH));
            assertEquals(-1, CampusPlan.hallSiteAt(c[0], c[1] - 4, DEPTH));
        }
        assertEquals(-1, CampusPlan.hallSiteAt(0, 0, DEPTH), "the nexus itself");
        assertEquals(-1, CampusPlan.hallSiteAt(41, 0, DEPTH), "the gate is never a hall");
        assertFalse(CampusPlan.inHall(0, WIDTH, DEPTH, 30, 0), "the gate is never a hall");
        assertThrows(IllegalArgumentException.class, () -> PLAN.siteFloor(0, WIDTH, DEPTH));
        assertThrows(IllegalArgumentException.class, () -> PLAN.siteFloor(1, WIDTH, DEPTH));

        List<int[]> pylons = PLAN.pylons();
        assertEquals(4, pylons.size());
        Set<Long> at = new HashSet<>();
        for (int[] p : pylons) at.add(CampusPlan.key(p[0], p[1]));
        for (int a : new int[] { 27, 35 }) for (int r : new int[] { -5, 5 }) {
            assertTrue(at.contains(CampusPlan.key(a, r)));
        }
        assertArrayEquals(new int[] { 9, 5 }, PLAN.supplyPort());
    }

    @Test
    void legacySlotsLieInsideTheirHallSites() {
        for (int[] f : FRONTS) for (int site : CampusPlan.HALL_SITES) {
            int[] o = RingSlots.offset(site, 32, f[0], f[1]);
            int[] l = CampusPlan.toLocal(o[0], o[1], f[0], f[1]);
            assertTrue(
                CampusPlan.inHall(site, WIDTH, DEPTH, l[0], l[1]),
                "legacy slot " + site + " in its Archive site");
            for (int other : CampusPlan.HALL_SITES) {
                if (other != site) assertFalse(CampusPlan.inHall(other, WIDTH, DEPTH, l[0], l[1]));
            }
        }
    }

    @Test
    void archivesFitTheHallSitesWithoutOverlap() {
        Map<Long, Integer> establish = PLAN.establishFloor();
        Map<Integer, Set<Long>> feet = new HashMap<>();
        for (int site : CampusPlan.HALL_SITES) {
            Set<Long> foot = footprint(site, WIDTH, DEPTH);
            assertEquals(WIDTH * DEPTH, foot.size());
            for (long k : foot) {
                int a = CampusPlan.keyA(k), r = CampusPlan.keyR(k);
                assertFalse(CampusPlan.inForum(a, r), "Archive " + site + " on the forum at " + a + "," + r);
                assertFalse(CampusPlan.inRing(a, r), "Archive " + site + " on the ring at " + a + "," + r);
                assertFalse(CampusPlan.inGate(a, r), "Archive " + site + " on the gate at " + a + "," + r);
                assertFalse(CampusPlan.inBase(a, r));
                assertFalse(establish.containsKey(k));
                assertTrue(
                    PLAN.gradeArea()
                        .contains(k),
                    "the footprint is graded");
            }
            feet.put(site, foot);
        }
        for (int site : CampusPlan.HALL_SITES) for (int other : CampusPlan.HALL_SITES) {
            Set<Long> floor = PLAN.siteFloor(other, WIDTH, DEPTH)
                .keySet();
            for (long k : feet.get(site)) {
                assertFalse(floor.contains(k), "Archive " + site + " under the floor of site " + other);
                if (other != site) assertFalse(
                    feet.get(other)
                        .contains(k),
                    "Archives " + site + " and " + other);
            }
        }
    }

    @Test
    void everyGradedCellIsInsideTheDisc() {
        Set<Long> grade = PLAN.gradeArea();
        int expected = 0;
        for (int a = -CampusPlan.GRADE_R; a <= CampusPlan.GRADE_R; a++) {
            for (int r = -CampusPlan.GRADE_R; r <= CampusPlan.GRADE_R; r++) {
                boolean graded = CampusPlan.oct(a, r, CampusPlan.GRADE_R, CampusPlan.GRADE_K);
                if (graded && !CampusPlan.inBase(a, r)) expected++;
            }
        }
        assertEquals(expected, grade.size());
        for (long k : grade) {
            int a = CampusPlan.keyA(k), r = CampusPlan.keyR(k);
            assertTrue(CampusPlan.oct(a, r, CampusPlan.GRADE_R, CampusPlan.GRADE_K), a + "," + r);
            assertFalse(CampusPlan.inBase(a, r), "the dais is not graded");
        }
        assertTrue(
            grade.containsAll(
                PLAN.establishFloor()
                    .keySet()));
        for (int site : CampusPlan.HALL_SITES) {
            assertTrue(
                grade.containsAll(
                    PLAN.siteFloor(site, WIDTH, DEPTH)
                        .keySet()));
        }
        assertEquals(grade, new CampusPlan(5).gradeArea(), "the disc does not depend on the seed");
    }

    @Test
    void establishPavesTheForumTheGateAndTheWholeRing() {
        Map<Long, Integer> establish = PLAN.establishFloor();
        for (int a = -30; a <= 40; a++) for (int r = -30; r <= 30; r++) {
            boolean want = CampusPlan.inForum(a, r) || CampusPlan.inRing(a, r) || CampusPlan.inGate(a, r);
            assertEquals(want, establish.containsKey(CampusPlan.key(a, r)), a + "," + r);
        }
        // forum 772, promenade ring 340 (all eight sectors), gate 172 of which 21 lie on the ring
        assertEquals(772 + 340 + 172 - 21, establish.size());
        for (int[] p : PLAN.pylons()) {
            long k = CampusPlan.key(p[0], p[1]);
            assertTrue(establish.containsKey(k), "a pylon stands on a deck tile");
            assertFalse(
                PLAN.wells()
                    .contains(k));
        }
        int[] port = PLAN.supplyPort();
        long pk = CampusPlan.key(port[0], port[1]);
        assertTrue(establish.containsKey(pk), "the supply port stands on a deck tile");
        assertFalse(
            PLAN.wells()
                .contains(pk));
    }

    @Test
    void moduleFloorsOnlyGrow() {
        for (long seed : seeds(16)) {
            CampusPlan p = new CampusPlan(seed);
            Map<Long, Integer> merged = new HashMap<>(p.establishFloor());
            for (int site : CampusPlan.HALL_SITES) {
                Map<Long, Integer> floor = p.siteFloor(site, WIDTH, DEPTH);
                assertFalse(floor.isEmpty());
                for (Map.Entry<Long, Integer> e : floor.entrySet()) {
                    int a = CampusPlan.keyA(e.getKey()), r = CampusPlan.keyR(e.getKey());
                    assertNull(
                        merged.put(e.getKey(), e.getValue()),
                        "site " + site + " changes a laid tile at " + a + "," + r);
                    assertFalse(CampusPlan.inHall(site, WIDTH, DEPTH, a, r), "a site floor under its own module");
                }
            }
            for (Map.Entry<Long, Integer> e : p.establishFloor()
                .entrySet()) {
                assertEquals(e.getValue(), merged.get(e.getKey()));
            }
            // other module sizes never reach into the establish floor either
            int[][] sizes = { { 13, 13 }, { 21, 25 }, { 31, 41 } };
            for (int site : CampusPlan.HALL_SITES) for (int[] size : sizes) {
                for (long k : p.siteFloor(site, size[0], size[1])
                    .keySet()) {
                    assertFalse(
                        p.establishFloor()
                            .containsKey(k));
                    assertFalse(CampusPlan.inHall(site, size[0], size[1], CampusPlan.keyA(k), CampusPlan.keyR(k)));
                }
            }
        }
    }

    @Test
    void plansAreDeterministic() {
        long seed = Mix.seed(-812, 64, 3301);
        CampusPlan a = new CampusPlan(seed), b = new CampusPlan(seed);
        assertEquals(a.establishFloor(), b.establishFloor());
        assertEquals(
            new ArrayList<>(
                a.establishFloor()
                    .keySet()),
            new ArrayList<>(
                b.establishFloor()
                    .keySet()));
        assertEquals(a.wells(), b.wells());
        assertArrayEquals(a.lobeSites(), b.lobeSites());
        for (int site : CampusPlan.HALL_SITES) {
            assertEquals(a.siteFloor(site, WIDTH, DEPTH), b.siteFloor(site, WIDTH, DEPTH));
        }
        assertEquals(a.siteFloor(4, WIDTH, DEPTH), a.siteFloor(4, WIDTH, DEPTH), "a cached floor is the same floor");
    }

    @Test
    void seedsDifferByPosition() {
        long here = Mix.seed(100, 64, 100);
        long[] near = { Mix.seed(101, 64, 100), Mix.seed(100, 65, 100), Mix.seed(100, 64, 101), Mix.seed(-100, 64, 100),
            Mix.seed(100, 64, -100), Mix.seed(64, 100, 100) };
        CampusPlan p = new CampusPlan(here);
        Set<Long> distinct = new HashSet<>();
        distinct.add(here);
        for (long s : near) {
            assertTrue(distinct.add(s), "two positions share a seed");
            CampusPlan q = new CampusPlan(s);
            assertNotEquals(p.establishFloor(), q.establishFloor(), "the paving follows the position");
            assertEquals(
                p.establishFloor()
                    .keySet(),
                q.establishFloor()
                    .keySet(),
                "the geometry does not");
        }
    }
}
