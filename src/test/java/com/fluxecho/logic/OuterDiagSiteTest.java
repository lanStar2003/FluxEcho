package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.BuildPlan.Column;
import com.fluxecho.logic.BuildPlan.Plan;
import com.fluxecho.logic.BuildPlan.Step;

/**
 * The outer ring's floors (0.15.0): the outer establish's floor (its light only on lines, the outer gate's trim, the
 * door marks) and an outer diagonal's site floor (its apron and allée, 175 cells), and the generic module plan on an
 * outer diagonal (M12 §4): a 31 x 31 building whose base fills {@code oct(15, 20)} stands with its controller on the
 * octagon's centre facing along A toward the nexus, and everything its job clears or places lies on its site, for
 * every site and front; the Arcane Court's smaller octagon fits there too.
 */
class OuterDiagSiteTest {

    private static final int LIT = Parts.deck(Parts.D_LIT), TRIM = Parts.deck(Parts.D_TRIM),
        CHEVRON = Parts.deck(Parts.D_CHEVRON), DECK = Parts.deck(Parts.D_DECK), DARK = Parts.deck(Parts.D_DARK);
    private static final int[][] FACINGS = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
    private static final int CX = -400, Y0 = 64, CZ = 250;

    /** A stand-in for an outer diagonal's building: a 31 x 31 octagon floor, a ring of pillars, the controller. */
    static final CityBuild OCTAGON = octagon();

    private static CityBuild octagon() {
        ShapeKit.Shape s = new ShapeKit.Shape("outer_test", 31, 4, 31);
        for (int x = 0; x < 31; x++) for (int z = 0; z < 31; z++) {
            int du = Math.abs(x - 15), dv = Math.abs(z - 15);
            if (du + dv > 20) continue;
            s.set(x, 0, z, 'F');
            if (du + dv >= 19 || du == 15 || dv == 15) for (int y = 1; y < 4; y++) s.set(x, y, z, 'P');
        }
        s.set(15, 2, 15, ShapeKit.CONTROLLER);
        s.controller = new int[] { 15, 2, 15 };
        return new CityBuild(
            "outer_test",
            s,
            4,
            ShapeKit.blueprint(s, 4),
            new UnitLayout(s),
            Parts.moduleCore(0),
            new CityBuild.Stages() {

                @Override
                public int stage(int x, int y, int z, char ch) {
                    return ch == ShapeKit.CONTROLLER ? BuildPlan.M_COMMISSION : BuildPlan.M_SHELL;
                }

                @Override
                public int fitRank(int x, int y, int z, char ch) {
                    return 1;
                }
            });
    }

    @Test
    void theOuterFloorLightsOnlyItsLines() {
        for (long seed : new long[] { 5L, -77L, 31_337L }) {
            CampusPlan cp = new CampusPlan(seed);
            Map<Long, Integer> floor = cp.outerFloor();
            assertEquals(OuterRing.paved(), floor.keySet());
            int lit = 0;
            for (Map.Entry<Long, Integer> e : floor.entrySet()) {
                int a = CampusPlan.keyA(e.getKey()), r = CampusPlan.keyR(e.getKey()), code = e.getValue();
                assertTrue(
                    code == LIT || code == TRIM || code == DECK || code == DARK,
                    "a deck tile at " + a + "," + r);
                assertFalse(CampusPlan.oct(a, r, CampusPlan.RING_R, CampusPlan.RING_K), "never the inner ring");
                if (code == LIT) lit++;
                if (OuterRing.doorMark(a, r)) assertEquals(LIT, code, "a door mark");
                if (OuterRing.inOuterGate(a, r) && r == 0 && a % 2 == 0) assertEquals(LIT, code, "the gate's seam");
                if (OuterRing.inOuterGate(a, r) && Math.abs(r) == OuterRing.GATE_WIDE)
                    assertEquals(TRIM, code, "the gate's sides");
            }
            assertTrue(lit * 100 < floor.size() * 10, "light on lines only: " + lit + " of " + floor.size());
            assertTrue(lit > 200);
            assertEquals(floor, new CampusPlan(seed).outerFloor(), "the same seed, the same floor");
            // the pylons stand on the gate's trim
            for (int[] p : OuterRing.pylons()) assertEquals(TRIM, floor.get(CampusPlan.key(p[0], p[1])));
        }
    }

    @Test
    void anOuterDiagonalsFloorIsItsApronAndAllee() {
        CampusPlan cp = new CampusPlan(8L);
        Set<Long> seen = new HashSet<>(
            cp.outerFloor()
                .keySet());
        seen.addAll(
            cp.establishFloor()
                .keySet());
        for (int site : OuterRing.DIAG_SITES) {
            Map<Long, Integer> floor = cp.outerDiagFloor(site);
            assertEquals(175, floor.size());
            Set<Long> want = new HashSet<>(OuterRing.diagApron(site));
            want.addAll(OuterRing.diagAllee(site));
            assertEquals(want, floor.keySet());
            for (Map.Entry<Long, Integer> e : floor.entrySet()) {
                long k = e.getKey();
                int a = CampusPlan.keyA(k), r = CampusPlan.keyR(k), code = e.getValue();
                assertTrue(seen.add(k), "a cell of another floor at " + a + "," + r);
                assertFalse(OuterRing.inDiag(site, a, r));
                int t = Math.abs(a) + Math.abs(r);
                if (OuterRing.diagAllee(site)
                    .contains(k)) assertEquals(t >= 102 ? LIT : CHEVRON, code, "the allée at " + t);
                else assertTrue(code == TRIM || code == DECK || code == DARK);
                // the shadow gap round the building
                boolean beside = false;
                for (int da = -1; da <= 1; da++)
                    for (int dr = -1; dr <= 1; dr++) beside |= OuterRing.inDiag(site, a + da, r + dr);
                if (beside && !OuterRing.diagAllee(site)
                    .contains(k)) assertEquals(TRIM, code);
            }
            assertEquals(floor, new CampusPlan(8L).outerDiagFloor(site), "the same seed, the same floor");
        }
        assertThrows(IllegalArgumentException.class, () -> cp.outerDiagFloor(1));
    }

    /** The controller stands on the octagon's centre at Y0 + 2, the blueprint's front along A toward the nexus. */
    @Test
    void theControllerStandsOnTheCentre() {
        for (int site : OuterRing.DIAG_SITES) for (int[] f : FACINGS) {
            int[] c = BuildPlan.moduleController(OCTAGON, site, CX, Y0, CZ, f[0], f[1]);
            assertArrayEquals(c, BuildPlan.outerDiagController(OCTAGON, site, CX, Y0, CZ, f[0], f[1]));
            int[] l = CampusPlan.toLocal(c[0] - CX, c[2] - CZ, f[0], f[1]);
            assertArrayEquals(OuterRing.diagCentre(site), l, "site " + site);
            assertEquals(Y0 + 2, c[1]);
            int[] front = BuildPlan.front(c[3]);
            assertArrayEquals(OuterRing.diagFront(site), CampusPlan.toLocal(front[0], front[1], f[0], f[1]));
            assertArrayEquals(OuterRing.diagCentre(site), CampusPlan.moduleCentre(site, 31));
        }
        assertThrows(
            IllegalArgumentException.class,
            () -> BuildPlan.outerDiagController(OCTAGON, 1, CX, Y0, CZ, 0, -1));
    }

    /**
     * Everything the job of a building on an outer diagonal touches lies on the site ({@link OuterRing#diagSiteCells},
     * the keep-out and the protection the campus gives it): its clear box, its floor, its HARD steps (exactly the
     * octagon), its annex.
     */
    @Test
    void itsJobStaysOnItsSite() {
        for (CityBuild m : Arrays.asList(OCTAGON, ArcaneCourtShape.BUILD))
            for (int site : OuterRing.DIAG_SITES) for (int[] f : FACINGS) {
                CampusPlan cp = new CampusPlan(Mix.seed(CX, Y0 + 2, CZ));
                Plan p = BuildPlan.module(cp, site, CX, Y0, CZ, f[0], f[1], m);
                assertEquals(BuildPlan.moduleKey(m.key(), site), p.key);
                Set<Long> cells = OuterRing.diagSiteCells(site);
                for (Column c : p.clear) {
                    int[] l = CampusPlan.toLocal(c.x - CX, c.z - CZ, f[0], f[1]);
                    assertTrue(cells.contains(CampusPlan.key(l[0], l[1])), m.key() + " clears off its site");
                }
                int hard = 0, floor = 0, decor = 0;
                for (Step s : p.steps) {
                    int[] l = CampusPlan.toLocal(s.x - CX, s.z - CZ, f[0], f[1]);
                    assertTrue(
                        cells.contains(CampusPlan.key(l[0], l[1])),
                        m.key() + "@" + site + " places off its site: " + l[0] + "," + l[1]);
                    if (s.stage == BuildPlan.M_FLOOR) floor++;
                    if (s.stage == BuildPlan.M_DECOR) decor++;
                    if (s.kind == BuildPlan.HARD) hard++;
                    if (m == OCTAGON && s.y == Y0 && s.kind == BuildPlan.HARD)
                        assertTrue(OuterRing.inDiag(site, l[0], l[1]), "its base fills the octagon");
                }
                if (m == OCTAGON) assertEquals(175, floor, "the site floor");
                else assertTrue(floor > 175, "the site floor and the court's own floor cells");
                assertTrue(decor > 0, "the annex stands on the apron");
                if (m == OCTAGON) assertEquals(741 + octagonPillars(), hard);
            }
    }

    private static int octagonPillars() {
        int n = 0;
        for (int x = 0; x < 31; x++) for (int z = 0; z < 31; z++) {
            int du = Math.abs(x - 15), dv = Math.abs(z - 15);
            if (du + dv <= 20 && (du + dv >= 19 || du == 15 || dv == 15)) n += 3;
        }
        return n + 1;
    }

    /** Its keep-out boxes hold its site exactly: every cell of the site, and no cell outside it. */
    @Test
    void theRowBoxesHoldTheSite() {
        for (int site : OuterRing.DIAG_SITES) {
            Set<Long> cells = OuterRing.diagSiteCells(site);
            Set<Long> boxed = new HashSet<>();
            List<int[]> boxes = new ArrayList<>(CampusPlan.rowBoxes(cells));
            for (int[] b : boxes) for (int a = Math.min(b[0], b[2]); a <= Math.max(b[0], b[2]); a++)
                for (int r = Math.min(b[1], b[3]); r <= Math.max(b[1], b[3]); r++) boxed.add(CampusPlan.key(a, r));
            assertEquals(cells, boxed);
        }
    }
}
