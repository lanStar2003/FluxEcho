package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * A pavilion's site on the campus (0.13.0, CITY.md §2.2): its floor (the apron with its trim inner row, the diagonal
 * allee with its lit dashed centre, its chevrons and its lit threshold) grows the campus without touching the
 * establish floor, the halls' floors or the other pavilions'; its front, the dock's test and the site as row boxes.
 */
class PavilionFloorTest {

    private static final int LIT = Parts.deck(Parts.D_LIT), TRIM = Parts.deck(Parts.D_TRIM),
        CHEVRON = Parts.deck(Parts.D_CHEVRON), DECK = Parts.deck(Parts.D_DECK), DARK = Parts.deck(Parts.D_DARK);

    @Test
    void theFloorIsTheApronAndTheAllee() {
        for (long seed : new long[] { 7L, -91L, 123_456_789L }) {
            CampusPlan cp = new CampusPlan(seed);
            for (int site : CampusPlan.PAVILION_SITES) {
                Map<Long, Integer> floor = cp.pavilionFloor(site);
                Set<Long> want = new HashSet<>(CampusPlan.pavilionApron(site));
                want.addAll(CampusPlan.pavilionAllee(site));
                assertEquals(
                    140,
                    CampusPlan.pavilionApron(site)
                        .size());
                assertEquals(
                    44,
                    CampusPlan.pavilionAllee(site)
                        .size());
                assertEquals(181, want.size(), "three allee cells lie on the apron, before the portal");
                assertEquals(want, floor.keySet());
                for (Map.Entry<Long, Integer> e : floor.entrySet()) {
                    long k = e.getKey();
                    int a = CampusPlan.keyA(k), r = CampusPlan.keyR(k);
                    assertFalse(
                        cp.establishFloor()
                            .containsKey(k),
                        "never an establish tile");
                    assertFalse(CampusPlan.oct(a, r, CampusPlan.RING_R, CampusPlan.RING_K));
                    assertFalse(CampusPlan.inPavilion(site, a, r), "never the building's own cell");
                    int code = e.getValue();
                    assertTrue(code == LIT || code == TRIM || code == CHEVRON || code == DECK || code == DARK);
                }
                assertEquals(floor, new CampusPlan(seed).pavilionFloor(site), "the same seed, the same floor");
            }
        }
    }

    @Test
    void theAlleesLightsChevronsAndThreshold() {
        CampusPlan cp = new CampusPlan(42L);
        for (int site : CampusPlan.PAVILION_SITES) {
            Map<Long, Integer> floor = cp.pavilionFloor(site);
            int lit = 0, chevrons = 0, threshold = 0;
            for (long k : CampusPlan.pavilionAllee(site)) {
                int a = Math.abs(CampusPlan.keyA(k)), r = Math.abs(CampusPlan.keyR(k));
                int t = a + r, d = a - r;
                int code = floor.get(k);
                if (t >= 54) {
                    assertEquals(LIT, code, "the threshold at " + t);
                    threshold++;
                } else if (t >= 50) {
                    assertEquals(CHEVRON, code, "a chevron at " + t);
                    chevrons++;
                } else if (d == 0) {
                    assertEquals(0, t % 2, "the centre runs on even steps");
                    assertEquals(LIT, code, "the dashed centre at " + t);
                    lit++;
                } else assertEquals(TRIM, code, "the allee's curb at " + t);
            }
            assertEquals(3, threshold);
            assertEquals(6, chevrons);
            assertEquals(11, lit, "28, 30 .. 48");
            // the allee leaves the promenade ring's diagonal face: its first cells touch the ring
            int[] ax = CampusPlan.siteAxis(site);
            assertTrue(CampusPlan.oct(ax[0] * 13, ax[1] * 13, CampusPlan.RING_R, CampusPlan.RING_K));
            assertTrue(floor.containsKey(CampusPlan.key(ax[0] * 14, ax[1] * 13)));
            // the apron's inner row round the building is trim, but where the threshold crosses it
            for (long k : CampusPlan.pavilionApron(site)) {
                int a = CampusPlan.keyA(k), r = CampusPlan.keyR(k);
                boolean inner = false;
                for (int da = -1; da <= 1; da++)
                    for (int dr = -1; dr <= 1; dr++) inner |= CampusPlan.inPavilion(site, a + da, r + dr);
                boolean sill = Math.abs(a) + Math.abs(r) >= 54 && Math.abs(Math.abs(a) - Math.abs(r)) <= 1;
                if (inner && !sill) assertEquals(TRIM, (int) floor.get(k), a + "," + r);
            }
        }
    }

    /** The floors grow apart: no pavilion floor touches a hall's (27 x 35 on 2, 4, 6) or another pavilion's. */
    @Test
    void theFloorsNeverMeet() {
        CampusPlan cp = new CampusPlan(-5L);
        Set<Long> halls = new HashSet<>();
        for (int site : CampusPlan.HALL_SITES) halls.addAll(
            cp.siteFloor(site, 27, 35)
                .keySet());
        for (int site : CampusPlan.HALL_SITES) halls.addAll(CampusPlan.hallCells(site, 27, 35));
        Set<Long> seen = new HashSet<>();
        for (int site : CampusPlan.PAVILION_SITES) {
            for (long k : cp.pavilionFloor(site)
                .keySet()) {
                assertFalse(halls.contains(k), "a hall's cell under pavilion " + site);
                assertTrue(seen.add(k), "two pavilions' floors meet");
            }
            for (long k : CampusPlan.pavilionCells(site)) assertFalse(halls.contains(k));
        }
        assertThrows(IllegalArgumentException.class, () -> cp.pavilionFloor(2));
    }

    @Test
    void theFrontAndTheDocksTest() {
        assertArrayEquals(new int[] { -1, 0 }, CampusPlan.pavilionFront(1));
        assertArrayEquals(new int[] { -1, 0 }, CampusPlan.pavilionFront(7));
        assertArrayEquals(new int[] { 1, 0 }, CampusPlan.pavilionFront(3));
        assertArrayEquals(new int[] { 1, 0 }, CampusPlan.pavilionFront(5));
        assertThrows(IllegalArgumentException.class, () -> CampusPlan.pavilionFront(4));
        assertEquals(1, CampusPlan.pavilionSiteAt(36, 36));
        assertEquals(3, CampusPlan.pavilionSiteAt(-33, 39));
        assertEquals(5, CampusPlan.pavilionSiteAt(-36, -36));
        assertEquals(7, CampusPlan.pavilionSiteAt(36, -36));
        assertEquals(-1, CampusPlan.pavilionSiteAt(32, 36));
        assertEquals(-1, CampusPlan.pavilionSiteAt(0, 40));
        for (int site : CampusPlan.PAVILION_SITES)
            assertArrayEquals(CampusPlan.diagonalCentre(site), CampusPlan.moduleCentre(site, 25));
    }

    /** The site as boxes a row: they hold its cells and nothing else, and every one of them. */
    @Test
    void theSiteAsRowBoxes() {
        for (int site : CampusPlan.PAVILION_SITES) {
            Set<Long> cells = CampusPlan.pavilionSite(site);
            assertTrue(cells.containsAll(CampusPlan.pavilionCells(site)));
            assertTrue(cells.containsAll(CampusPlan.pavilionApron(site)));
            assertTrue(cells.containsAll(CampusPlan.pavilionAllee(site)));
            List<int[]> boxes = CampusPlan.rowBoxes(cells);
            Set<Long> covered = new HashSet<>();
            for (int[] b : boxes) {
                assertEquals(b[0], b[2], "a row");
                for (int r = b[1]; r <= b[3]; r++) {
                    long k = CampusPlan.key(b[0], r);
                    assertTrue(cells.contains(k), "a box holds a cell not of the site");
                    assertTrue(covered.add(k), "boxes overlap");
                }
            }
            assertEquals(cells, covered);
            assertTrue(boxes.size() < 50, "a row of the octagon is one box, an allee row too: " + boxes.size());
        }
        // runs split where a row has a gap
        Set<Long> gap = new HashSet<>();
        gap.add(CampusPlan.key(0, 1));
        gap.add(CampusPlan.key(0, 2));
        gap.add(CampusPlan.key(0, 4));
        List<int[]> b = CampusPlan.rowBoxes(gap);
        assertEquals(2, b.size());
        assertArrayEquals(new int[] { 0, 1, 0, 2 }, b.get(0));
        assertArrayEquals(new int[] { 0, 4, 0, 4 }, b.get(1));
    }
}
