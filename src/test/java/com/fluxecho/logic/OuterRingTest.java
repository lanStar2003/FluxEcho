package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;

import org.junit.jupiter.api.Test;

/**
 * The outer ring's ground (0.15.0, M12 §3 and §4, CITY.md §2.3), cell for cell as {@code city/gen/siteplan.py} has
 * it: every set's size and a CRC32 of its cells sorted by (A, R) ("a,r;" each), worked out by running the generator
 * read-only (its {@code check()} reports no problem). The outer gate, the pavilions' outer allées, the door marks and
 * the outer diagonals' allées are CITY.md's words the generator does not list; they are checked against the rules.
 */
class OuterRingTest {

    /** The size and CRC32 of a set of cells sorted by (a, r), as the generator's fingerprint prints them. */
    static String fp(Set<Long> cells) {
        List<int[]> l = new ArrayList<>();
        for (long k : cells) l.add(new int[] { CampusPlan.keyA(k), CampusPlan.keyR(k) });
        l.sort((x, y) -> x[0] != y[0] ? Integer.compare(x[0], y[0]) : Integer.compare(x[1], y[1]));
        StringBuilder s = new StringBuilder();
        for (int[] c : l) s.append(c[0])
            .append(',')
            .append(c[1])
            .append(';');
        CRC32 crc = new CRC32();
        crc.update(
            s.toString()
                .getBytes(StandardCharsets.US_ASCII));
        return cells.size() + " " + crc.getValue();
    }

    @Test
    void theGroundIsTheGeneratorsCellForCell() {
        assertEquals("1228 3802742406", fp(CampusPlan.streetCells()), "the 井 streets");
        assertEquals("1176 281216225", fp(OuterRing.promenade()), "the outer promenade");
        assertEquals("1183 838333664", fp(OuterRing.avenues()), "the avenues");
        assertEquals("19836 2634850803", fp(OuterRing.annulus()), "the annulus to grade");
        String[] diag = { "741 3510289739", "741 907415488", "741 1559165842", "741 3593375393" };
        String[] apron = { "172 1561752737", "172 2333702466", "172 3125950856", "172 244008192" };
        for (int i = 0; i < 4; i++) {
            int site = OuterRing.DIAG_SITES[i];
            assertEquals(diag[i], fp(OuterRing.diagCells(site)), "O" + (site - 10));
            assertEquals(apron[i], fp(OuterRing.diagApron(site)), "O" + (site - 10) + " apron");
        }
        // O0+, O0-, O2+ ... in the generator's (sorted) order; the sites are 20 + axis site + (side > 0 ? 0 : 1)
        String[] flank = { "837 1167597693", "837 449051239", "837 273345978", "837 3338521288", "837 1259647298",
            "837 1027776698", "837 626400241", "837 3910683236" };
        String[] flankApron = { "246 3774937067", "246 3996570515", "246 3674121949", "246 183705031", "246 2260735007",
            "246 360140202", "246 2884050129", "246 4217505640" };
        for (int site = OuterRing.FLANK_FIRST; site <= OuterRing.FLANK_LAST; site++) {
            assertEquals(flank[site - 20], fp(OuterRing.flankCells(site)), "flanker " + site);
            assertEquals(flankApron[site - 20], fp(OuterRing.flankApron(site)), "flanker apron " + site);
        }
    }

    @Test
    void theSetsAreWhatTheirNamesSay() {
        for (long k : OuterRing.annulus()) {
            int a = CampusPlan.keyA(k), r = CampusPlan.keyR(k);
            assertTrue(OuterRing.inOuterDisc(a, r));
            assertFalse(CampusPlan.oct(a, r, CampusPlan.GRADE_R, CampusPlan.GRADE_K));
        }
        for (long k : OuterRing.promenade()) assertTrue(OuterRing.inPromenade(CampusPlan.keyA(k), CampusPlan.keyR(k)));
        for (long k : OuterRing.avenues())
            assertTrue(OuterRing.onAvenue(CampusPlan.keyA(k), CampusPlan.keyR(k)) != null);
        int onAvenue = 0;
        for (int a = -OuterRing.OUTER_R; a <= OuterRing.OUTER_R; a++)
            for (int r = -OuterRing.OUTER_R; r <= OuterRing.OUTER_R; r++)
                if (OuterRing.onAvenue(a, r) != null) onAvenue++;
        assertEquals(1183, onAvenue, "onAvenue holds the avenues and nothing else");

        // the outer gate: the front avenue widened to |R| 5 from A 91 to 97, its four pylons on it
        assertEquals(
            77,
            OuterRing.outerGate()
                .size());
        Set<Long> avenues = OuterRing.avenues();
        int widening = 0;
        for (long k : OuterRing.outerGate()) if (!avenues.contains(k)) widening++;
        assertEquals(28, widening);
        for (int[] p : OuterRing.pylons()) {
            assertTrue(OuterRing.inOuterGate(p[0], p[1]), "a pylon on the gate");
            assertEquals(OuterRing.GATE_WIDE, Math.abs(p[1]));
        }
        assertEquals(
            4,
            OuterRing.pylons()
                .size());

        // the pavilions' outer allées: 12 cells each, from the outer chamfer portal (89) to the promenade (96)
        assertEquals(
            48,
            OuterRing.pavilionAllees()
                .size());
        for (int site : CampusPlan.PAVILION_SITES) {
            Set<Long> allee = OuterRing.pavilionAllee(site);
            assertEquals(12, allee.size());
            int onApron = 0;
            for (long k : allee) {
                int a = CampusPlan.keyA(k), r = CampusPlan.keyR(k);
                int t = Math.abs(a) + Math.abs(r);
                assertTrue(t >= 89 && t <= 96);
                assertTrue(Math.abs(Math.abs(a) - Math.abs(r)) <= 1);
                assertFalse(CampusPlan.inPavilion(site, a, r), "never the pavilion's own cell");
                if (CampusPlan.pavilionApron(site)
                    .contains(k)) onApron++;
            }
            assertEquals(3, onApron, "its first three cells lie on the pavilion's apron");
            assertTrue(
                allee.contains(
                    CampusPlan.key(
                        48 * Integer.signum(CampusPlan.siteAxis(site)[0]),
                        48 * Integer.signum(CampusPlan.siteAxis(site)[1]))),
                "the last cell meets the promenade");
        }
        assertThrows(IllegalArgumentException.class, () -> OuterRing.pavilionAllee(2));

        // the door marks: seven lit cells on the promenade's row 63 before each flanker's doors
        Set<Long> marks = OuterRing.doorMarks();
        assertEquals(56, marks.size());
        Set<Long> prom = OuterRing.promenade();
        assertTrue(prom.containsAll(marks));
        for (int site = OuterRing.FLANK_FIRST; site <= OuterRing.FLANK_LAST; site++) {
            int n = 0;
            for (long k : marks) {
                int[] ax = OuterRing.axis(site);
                int a = CampusPlan.keyA(k), r = CampusPlan.keyR(k);
                int t = a * ax[0] + r * ax[1], s = -a * ax[1] + r * ax[0];
                // the cell in front of it, one step towards the nexus from its front row
                if (t == OuterRing.FLANK_FRONT - 1 && OuterRing.inFlank(site, a + ax[0], r + ax[1])) n++;
                if (t == OuterRing.FLANK_FRONT - 1
                    && Math.abs(s - OuterRing.flankSide(site) * OuterRing.FLANK_CROSS) <= 3)
                    assertTrue(OuterRing.inFlank(site, a + ax[0], r + ax[1]));
            }
            assertEquals(7, n, "seven marks before flanker " + site);
        }

        // everything paved: the union, and none of it on a building's envelope
        Set<Long> paved = OuterRing.paved();
        assertEquals(3579, paved.size());
        for (int site : OuterRing.DIAG_SITES) for (long k : OuterRing.diagSiteCells(site))
            assertFalse(paved.contains(k), "the outer establish never paves an outer diagonal's site");
        for (int site = OuterRing.FLANK_FIRST; site <= OuterRing.FLANK_LAST; site++)
            for (long k : OuterRing.flankCells(site)) assertFalse(paved.contains(k), "nor a flanker's envelope");
    }

    @Test
    void anOuterDiagonalsSite() {
        Set<Long> seen = new HashSet<>();
        for (int site : OuterRing.DIAG_SITES) {
            int[] c = OuterRing.diagCentre(site);
            assertEquals(62, Math.abs(c[0]));
            assertEquals(62, Math.abs(c[1]));
            assertArrayEquals(CampusPlan.siteAxis(site - 10), OuterRing.axis(site), "the inner diagonal's axis");
            assertArrayEquals(OuterRing.axis(site), CampusPlan.siteAxis(site), "the campus plan agrees");
            assertArrayEquals(CampusPlan.pavilionFront(site - 10), OuterRing.diagFront(site), "faces as a pavilion");
            assertEquals(site, OuterRing.diagSiteAt(c[0] + 3, c[1] - 3));
            assertEquals(-1, OuterRing.diagSiteAt(c[0] + 4, c[1]));

            // the allée: 6 cells from the promenade's diagonal face to the portal, the last three on the apron
            Set<Long> allee = OuterRing.diagAllee(site), apron = OuterRing.diagApron(site);
            assertEquals(6, allee.size());
            int onApron = 0;
            for (long k : allee) {
                int a = CampusPlan.keyA(k), r = CampusPlan.keyR(k);
                int t = Math.abs(a) + Math.abs(r);
                assertTrue(t >= 100 && t <= 103);
                assertFalse(OuterRing.inPromenade(a, r));
                assertFalse(OuterRing.inDiag(site, a, r));
                if (apron.contains(k)) onApron++;
            }
            assertEquals(3, onApron);
            Set<Long> cells = OuterRing.diagSiteCells(site);
            assertEquals(916, cells.size());
            for (long k : cells) {
                assertTrue(seen.add(k), "the outer diagonals' sites never meet");
                assertTrue(OuterRing.inAnnulus(CampusPlan.keyA(k), CampusPlan.keyR(k)), "all of it in the annulus");
            }
            // the inner chamfer portal: the first envelope cell past the allée's end
            int[] ax = OuterRing.axis(site);
            assertTrue(OuterRing.inDiag(site, 52 * ax[0], 52 * ax[1]));
            assertFalse(OuterRing.inDiag(site, 51 * ax[0], 51 * ax[1]));
            assertEquals((103.5) / Math.sqrt(2), OuterRing.DIAG_DOOR, 1e-9);
            assertEquals(OuterRing.DIAG_DOOR, CampusPlan.siteDoor(site, false), 1e-9);
            assertEquals(OuterRing.DIAG_DOOR, CampusPlan.siteDoor(site, true), 1e-9);
            // the masterplan's sign: on the allée, outside the apron, in front of the portal
            int[] sign = OuterRing.diagSign(site);
            assertTrue(allee.contains(CampusPlan.key(sign[0], sign[1])));
            assertFalse(apron.contains(CampusPlan.key(sign[0], sign[1])));
            assertEquals(50, Math.abs(sign[0]));
        }
        // the flankers' envelopes and aprons keep off the outer diagonals' sites
        for (int site = OuterRing.FLANK_FIRST; site <= OuterRing.FLANK_LAST; site++) {
            for (long k : OuterRing.flankCells(site)) assertFalse(seen.contains(k), "flanker " + site);
            assertEquals(OuterRing.axis(site)[0], CampusPlan.siteAxis(OuterRing.flankAxisSite(site))[0]);
        }
        assertThrows(IllegalArgumentException.class, () -> OuterRing.diagCentre(1));
        assertThrows(IllegalArgumentException.class, () -> OuterRing.axis(9));
        assertThrows(IllegalArgumentException.class, () -> OuterRing.flankAxisSite(11));
        assertEquals(-1, OuterRing.flankSide(21));
        assertEquals(1, OuterRing.flankSide(26));
        assertEquals(6, OuterRing.flankAxisSite(27));
    }

    @Test
    void theOuterSitesHaveTheirNames() {
        assertEquals("build.gui.site_o1", SiteFamily.labelKey(11));
        assertEquals("build.gui.site_o7", SiteFamily.labelKey(17));
        assertEquals("build.gui.site_o0p", SiteFamily.labelKey(20));
        assertEquals("build.gui.site_o0m", SiteFamily.labelKey(21));
        assertEquals("build.gui.site_o6m", SiteFamily.labelKey(27));
        assertEquals("build.gui.site", SiteFamily.labelKey(3));
        assertEquals("build.gui.site_gate", SiteFamily.labelKey(0));
        assertEquals("build.gui.site_zenith", SiteFamily.labelKey(8));
    }
}
