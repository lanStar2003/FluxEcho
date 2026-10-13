package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The inner city's site plan (0.11.0): the assertions of the scratch generator {@code city/gen/siteplan.py} (its
 * {@code check()}, {@code problems: 0}) for what the campus plans today, the halls, the pavilions' envelopes and the
 * gatehouse round the forum, ring and gate, with the counts the generator reports.
 */
class SitePlanTest {

    private static final int WIDTH = 27, DEPTH = 35;
    private static final CampusPlan PLAN = new CampusPlan(Mix.seed(120, 70, -340));

    /** The forum, the promenade ring and the gate road (siteplan's "core"). */
    private static Set<Long> core() {
        Set<Long> out = new HashSet<>();
        for (int a = -19; a <= 36; a++) for (int r = -19; r <= 19; r++) {
            if (CampusPlan.oct(a, r, CampusPlan.RING_R, CampusPlan.RING_K) || CampusPlan.inGate(a, r))
                out.add(CampusPlan.key(a, r));
        }
        return out;
    }

    private static Set<Long> hallAprons(int site) {
        return CampusPlan.hallApron(site, WIDTH, DEPTH);
    }

    private static void disjoint(String n1, Set<Long> s1, String n2, Set<Long> s2) {
        Set<Long> x = new HashSet<>(s1);
        x.retainAll(s2);
        assertTrue(x.isEmpty(), n1 + " overlaps " + n2 + " at " + x.size() + " cells");
    }

    /** The gap in whole cells between two sets (the Chebyshev distance of their nearest cells, less one). */
    private static int gap(Set<Long> a, Set<Long> b) {
        int best = Integer.MAX_VALUE;
        for (long p : a) for (long q : b) {
            int d = Math.max(
                Math.abs(CampusPlan.keyA(p) - CampusPlan.keyA(q)),
                Math.abs(CampusPlan.keyR(p) - CampusPlan.keyR(q))) - 1;
            best = Math.min(best, d);
        }
        return best;
    }

    @Test
    void theFamiliesHoldTheInnerSites() {
        assertEquals(SiteFamily.GATE, SiteFamily.of(0));
        for (int s : CampusPlan.HALL_SITES) assertEquals(SiteFamily.HALL, SiteFamily.of(s));
        for (int s : CampusPlan.PAVILION_SITES) assertEquals(SiteFamily.PAVILION, SiteFamily.of(s));
        assertArrayEquals(CampusPlan.HALL_SITES, SiteFamily.HALL.sites());
        assertArrayEquals(CampusPlan.PAVILION_SITES, SiteFamily.PAVILION.sites());
        assertArrayEquals(new int[] { 0 }, SiteFamily.GATE.sites());
        // the families further out have numbers of their own (0.14.1), none on the inner ring or the axis's 8 and 9
        for (SiteFamily f : new SiteFamily[] { SiteFamily.OUTER_DIAG, SiteFamily.OUTER_FLANK, SiteFamily.SKY,
            SiteFamily.ORBIT, SiteFamily.THIRD }) for (int s : f.sites()) {
                assertTrue(s > SiteFamily.ROOT_SITE, f + " stands off the inner ring: " + s);
                assertEquals(f, SiteFamily.of(s));
            }
        // the axis (0.12.0): the zenith over the nexus and the root under it, one site each past the ring's eight
        assertArrayEquals(new int[] { SiteFamily.ZENITH_SITE }, SiteFamily.ZENITH.sites());
        assertArrayEquals(new int[] { SiteFamily.ROOT_SITE }, SiteFamily.ROOT.sites());
        assertEquals(SiteFamily.ZENITH, SiteFamily.of(SiteFamily.ZENITH_SITE));
        assertEquals(SiteFamily.ROOT, SiteFamily.of(SiteFamily.ROOT_SITE));
        assertTrue(SiteFamily.ZENITH.axis() && SiteFamily.ROOT.axis() && SiteFamily.ORBIT.axis());
        assertFalse(SiteFamily.HALL.axis() || SiteFamily.PAVILION.axis() || SiteFamily.GATE.axis());
        assertTrue(SiteFamily.axisSite(SiteFamily.ZENITH_SITE) && SiteFamily.axisSite(SiteFamily.ROOT_SITE));
        for (int s = 0; s < 8; s++) assertFalse(SiteFamily.axisSite(s));
        assertEquals(2, SiteFamily.ZENITH.opens);
        assertEquals(2, SiteFamily.ROOT.opens);
        assertEquals(1, SiteFamily.HALL.opens);
        assertEquals(2, SiteFamily.PAVILION.opens);
        assertEquals(10, SiteFamily.values().length);
    }

    /**
     * The families later versions build on have site numbers of their own (0.14.1), so no outer site is ever taken for
     * an axis site (0.14.0 called every site from 8 up the axis's) or folded onto the inner ring by its number modulo 8
     * (11 was a pavilion's, 20 a hall's): the outer diagonals 11-17 odd, the flankers 20-27, the sky 30-37, the orbit
     * 40, the third ring 50-65. Each opens at its phase, half the flankers and the sky's berths a phase later, and the
     * outer families stand on ground their own campus job lays.
     */
    @Test
    void theFamiliesFurtherOutHaveNumbersOfTheirOwn() {
        java.util.Set<Integer> all = new java.util.HashSet<>();
        for (SiteFamily f : SiteFamily.values()) for (int s : f.sites()) {
            assertTrue(all.add(s), "site " + s + " belongs to one family");
            assertEquals(f, SiteFamily.of(s), "site " + s);
        }
        assertArrayEquals(new int[] { 11, 13, 15, 17 }, SiteFamily.OUTER_DIAG.sites());
        assertArrayEquals(new int[] { 20, 21, 22, 23, 24, 25, 26, 27 }, SiteFamily.OUTER_FLANK.sites());
        assertArrayEquals(new int[] { 30, 31, 32, 33, 34, 35, 36, 37 }, SiteFamily.SKY.sites());
        assertArrayEquals(new int[] { 40 }, SiteFamily.ORBIT.sites());
        assertEquals(16, SiteFamily.THIRD.sites().length);
        assertEquals(50, SiteFamily.THIRD.sites()[0]);
        assertEquals(65, SiteFamily.THIRD.sites()[15]);
        for (int s : new int[] { -1, 10, 12, 18, 19, 28, 38, 41, 49, 66, 255 }) assertNull(SiteFamily.of(s), "" + s);
        for (int s : new int[] { 11, 13, 20, 30, 50 }) assertFalse(SiteFamily.axisSite(s), "site " + s);
        assertTrue(SiteFamily.axisSite(40), "the orbit's deck stands on the axis");
        assertEquals(3, SiteFamily.opensAt(11));
        assertEquals(4, SiteFamily.opensAt(20));
        assertEquals(5, SiteFamily.opensAt(21));
        assertEquals(4, SiteFamily.opensAt(30));
        assertEquals(5, SiteFamily.opensAt(31));
        assertEquals(5, SiteFamily.opensAt(40));
        assertEquals(5, SiteFamily.opensAt(57));
        assertEquals(1, SiteFamily.opensAt(4));
        assertEquals(2, SiteFamily.opensAt(0));
        assertEquals(Integer.MAX_VALUE, SiteFamily.opensAt(10));
        assertEquals(SiteFamily.OUTER_GROUND, SiteFamily.OUTER_DIAG.ground());
        assertEquals(SiteFamily.OUTER_GROUND, SiteFamily.OUTER_FLANK.ground());
        assertEquals(SiteFamily.THIRD_GROUND, SiteFamily.THIRD.ground());
        for (SiteFamily f : new SiteFamily[] { SiteFamily.HALL, SiteFamily.PAVILION, SiteFamily.GATE, SiteFamily.ZENITH,
            SiteFamily.ROOT, SiteFamily.SKY, SiteFamily.ORBIT }) assertNull(f.ground(), f.name());
        // no establish key is mistaken for a ground job, nor the other way round
        assertEquals(0, BuildPlan.establishPhase(SiteFamily.OUTER_GROUND));
        assertEquals(0, BuildPlan.establishPhase(SiteFamily.THIRD_GROUND));
    }

    @Test
    void theAxisModulesTakeNoRingRoom() {
        // the counter the nexus window, Waila and the flux terminal show against the ring's open slots (0.12.0)
        assertEquals(2, SiteFamily.ringCount(2, 4, 8, 9), "the Archive and the Eco Dome; the zenith and the root not");
        assertEquals(4, SiteFamily.ringCount(1, 2, 4, 6, 8, 9));
        assertEquals(0, SiteFamily.ringCount(SiteFamily.ZENITH_SITE, SiteFamily.ROOT_SITE));
        assertEquals(0, SiteFamily.ringCount());
        for (int s = 0; s < RingSlots.SLOTS; s++) assertTrue(SiteFamily.ringSite(s), "site " + s);
        assertFalse(SiteFamily.ringSite(-1));
        assertFalse(SiteFamily.ringSite(SiteFamily.ZENITH_SITE));
        assertFalse(SiteFamily.ringSite(SiteFamily.ROOT_SITE));
        // the terminal names the axis sites instead of numbering them
        assertEquals("build.gui.site_zenith", SiteFamily.labelKey(SiteFamily.ZENITH_SITE));
        assertEquals("build.gui.site_root", SiteFamily.labelKey(SiteFamily.ROOT_SITE));
    }

    @Test
    void theGeneratorsCounts() {
        int[][] centres = { { 36, 36 }, { -36, 36 }, { -36, -36 }, { 36, -36 } };
        for (int i = 0; i < 4; i++) {
            int site = CampusPlan.PAVILION_SITES[i];
            assertArrayEquals(centres[i], CampusPlan.diagonalCentre(site));
            assertEquals(
                481,
                CampusPlan.pavilionCells(site)
                    .size(),
                "pavilion " + site);
            assertEquals(
                140,
                CampusPlan.pavilionApron(site)
                    .size(),
                "apron " + site);
            assertEquals(
                44,
                CampusPlan.pavilionAllee(site)
                    .size(),
                "allee " + site);
            for (long k : CampusPlan.pavilionCells(site))
                assertTrue(CampusPlan.inPavilion(site, CampusPlan.keyA(k), CampusPlan.keyR(k)));
            // the envelope spans A, R 24..48 from the nexus
            for (long k : CampusPlan.pavilionCells(site)) {
                assertTrue(Math.abs(CampusPlan.keyA(k)) >= 24 && Math.abs(CampusPlan.keyA(k)) <= 48);
                assertTrue(Math.abs(CampusPlan.keyR(k)) >= 24 && Math.abs(CampusPlan.keyR(k)) <= 48);
            }
        }
        assertEquals(
            527,
            CampusPlan.gatehouseCells()
                .size());
        assertEquals(
            1228,
            CampusPlan.streetCells()
                .size());
        assertThrows(IllegalArgumentException.class, () -> CampusPlan.pavilionAllee(2));
        assertThrows(IllegalArgumentException.class, () -> CampusPlan.hallCells(1, WIDTH, DEPTH));
    }

    /** {@code siteplan.py}'s inner-city checks: buildings, aprons and allees apart, all inside the graded disc. */
    @Test
    void theInnerCityHasNoProblems() {
        Map<String, Set<Long>> named = new LinkedHashMap<>();
        named.put("core", core());
        named.put("gatehouse", CampusPlan.gatehouseCells());
        for (int s : CampusPlan.HALL_SITES) named.put("hall" + s, CampusPlan.hallCells(s, WIDTH, DEPTH));
        for (int s : CampusPlan.PAVILION_SITES) named.put("pav" + s, CampusPlan.pavilionCells(s));
        List<String> names = new ArrayList<>(named.keySet());
        for (int i = 0; i < names.size(); i++) for (int j = i + 1; j < names.size(); j++) {
            // the gatehouse straddles the gate road on purpose (its passage |R| <= 6 is never built)
            if (names.get(i)
                .equals("core")
                && names.get(j)
                    .equals("gatehouse"))
                continue;
            disjoint(names.get(i), named.get(names.get(i)), names.get(j), named.get(names.get(j)));
        }
        Set<Long> halls = new HashSet<>();
        for (int s : CampusPlan.HALL_SITES) halls.addAll(named.get("hall" + s));
        for (int h : CampusPlan.HALL_SITES) {
            for (int p : CampusPlan.PAVILION_SITES) {
                disjoint("hall" + h + " apron", hallAprons(h), "pav" + p, named.get("pav" + p));
                disjoint("hall" + h + " apron", hallAprons(h), "pav" + p + " apron", CampusPlan.pavilionApron(p));
                assertTrue(gap(hallAprons(h), CampusPlan.pavilionApron(p)) >= 6, "hall " + h + " pav " + p);
            }
            disjoint("hall" + h + " apron", hallAprons(h), "gatehouse", CampusPlan.gatehouseCells());
        }
        int closest = Integer.MAX_VALUE;
        for (int h : CampusPlan.HALL_SITES) for (int p : CampusPlan.PAVILION_SITES)
            closest = Math.min(closest, gap(hallAprons(h), CampusPlan.pavilionApron(p)));
        assertEquals(6, closest, "the garden between a hall's apron and a pavilion's");
        Set<Long> core = core();
        for (int p : CampusPlan.PAVILION_SITES) {
            disjoint("pav" + p + " apron", CampusPlan.pavilionApron(p), "gatehouse", CampusPlan.gatehouseCells());
            disjoint("pav" + p + " apron", CampusPlan.pavilionApron(p), "gatehouse apron", CampusPlan.gatehouseApron());
            Set<Long> allee = new HashSet<>(CampusPlan.pavilionAllee(p));
            allee.removeAll(core);
            disjoint("pav" + p + " allee", allee, "halls", halls);
        }
        assertEquals(4, gap(CampusPlan.gatehouseApron(), CampusPlan.pavilionApron(1)), "gatehouse apron to pavilion");
        assertEquals(4, gap(CampusPlan.gatehouseApron(), CampusPlan.pavilionApron(7)), "gatehouse apron to pavilion");
        // the gatehouse leaves the road's pylons in its open passage
        for (int[] p : PLAN.pylons()) {
            assertTrue(CampusPlan.inGatehouse(p[0], p[1]) && Math.abs(p[1]) <= CampusPlan.GATE_PASSAGE);
        }
        // everything inside the phase I disc
        for (Map.Entry<String, Set<Long>> e : named.entrySet()) for (long k : e.getValue()) assertTrue(
            CampusPlan.oct(CampusPlan.keyA(k), CampusPlan.keyR(k), CampusPlan.GRADE_R, CampusPlan.GRADE_K),
            e.getKey() + " leaves the disc");
        // the streets of phase III keep clear of every building and apron
        Set<Long> streets = CampusPlan.streetCells();
        for (Map.Entry<String, Set<Long>> e : named.entrySet()) if (!e.getKey()
            .equals("core")) disjoint("streets", streets, e.getKey(), e.getValue());
        for (int h : CampusPlan.HALL_SITES) {
            Set<Long> apron = new HashSet<>(hallAprons(h));
            // the forecourt is not the street's business: siteplan checks the apron round the footprint only
            Set<Long> fore = new HashSet<>();
            for (long k : apron) {
                int[] ax = CampusPlan.siteAxis(h);
                int t = CampusPlan.keyA(k) * ax[0] + CampusPlan.keyR(k) * ax[1];
                if (t < CampusPlan.HALL_FRONT - 2) fore.add(k);
            }
            apron.removeAll(fore);
            disjoint("streets", streets, "hall" + h + " apron", apron);
        }
        for (int p : CampusPlan.PAVILION_SITES)
            disjoint("streets", streets, "pav" + p + " apron", CampusPlan.pavilionApron(p));
        disjoint("streets", streets, "gatehouse apron", CampusPlan.gatehouseApron());
    }

    @Test
    void anEnvelopeIsItsFamilys() {
        for (int s : CampusPlan.HALL_SITES) {
            int[] c = CampusPlan.moduleCentre(s, DEPTH);
            assertTrue(CampusPlan.inEnvelope(s, WIDTH, DEPTH, c[0], c[1]));
            assertFalse(CampusPlan.inPavilion(s, c[0], c[1]));
        }
        for (int s : CampusPlan.PAVILION_SITES) {
            int[] c = CampusPlan.moduleCentre(s, DEPTH);
            assertArrayEquals(CampusPlan.diagonalCentre(s), c);
            assertTrue(CampusPlan.inEnvelope(s, WIDTH, DEPTH, c[0], c[1]));
            // the chamfer: the corner cell of the 25 x 25 square is outside oct(12, 16)
            assertFalse(CampusPlan.inPavilion(s, c[0] + 12, c[1] + 12));
            assertTrue(CampusPlan.inPavilion(s, c[0] + 12, c[1] + 4));
            assertFalse(CampusPlan.inPavilion(s, c[0] + 12, c[1] + 5));
        }
        assertTrue(CampusPlan.inEnvelope(0, WIDTH, DEPTH, 30, 0));
        assertFalse(CampusPlan.inEnvelope(0, WIDTH, DEPTH, 40, 0));
    }
}
