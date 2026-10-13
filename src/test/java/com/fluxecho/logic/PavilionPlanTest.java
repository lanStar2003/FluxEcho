package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.BuildPlan.Plan;
import com.fluxecho.logic.BuildPlan.Step;

/**
 * The generic module plan on a pavilion site (0.13.0, the PAVILION family, CITY.md §2.1-2.2), with the Arcane Court:
 * the HARD steps are exactly its checked cells for four nexus fronts on every diagonal, the controller stands on the
 * octagon's centre facing along A toward the nexus, the clear box is its octagon and one round it up to Y0 + 35, the
 * site floor is its apron and its allee, the units launch as groups in slot order, the gate niche is built soft with
 * its wall, the seeded annex stands on the apron, and everything the job touches lies in the pavilion's site.
 */
class PavilionPlanTest {

    private static final int[][] FACINGS = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
    private static final int CX = 100, Y0 = 64, CZ = -200;

    private static CampusPlan campus(int[] f) {
        return new CampusPlan(Mix.seed(CX + 5 * f[0], Y0 + 2, CZ + 5 * f[1]));
    }

    private static Plan court(int site, int[] f) {
        return ArcaneCourtShape.plan(campus(f), site, CX, Y0, CZ, f[0], f[1]);
    }

    private static long pos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** The campus cell (a, r) of a world column. */
    private static int[] local(int x, int z, int[] f) {
        return CampusPlan.toLocal(x - CX, z - CZ, f[0], f[1]);
    }

    @Test
    void theHardStepsAreTheCheckedCells() {
        Blueprint b = ArcaneCourtShape.BLUEPRINT;
        for (int site : ArcaneCourtShape.SITES) for (int[] f : FACINGS) {
            Plan p = court(site, f);
            int[] c = ArcaneCourtShape.controller(site, CX, Y0, CZ, f[0], f[1]);
            int[] front = BuildPlan.front(c[3]);
            Set<Long> want = new HashSet<>(), air = new HashSet<>();
            for (Blueprint.Cell cell : b.cells()) {
                int[] w = b.world(cell.a, cell.b, cell.c, c[0], c[1], c[2], front[0], front[1]);
                (cell.ch == '-' ? air : want).add(pos(w[0], w[1], w[2]));
            }
            Set<Long> hard = new HashSet<>(), airSteps = new HashSet<>();
            for (Step s : p.steps) {
                if (s.kind == BuildPlan.HARD) assertTrue(hard.add(pos(s.x, s.y, s.z)), "a HARD cell twice");
                else if (s.kind == BuildPlan.AIR) airSteps.add(pos(s.x, s.y, s.z));
            }
            assertEquals(want, hard, "site " + site);
            assertEquals(2058, hard.size());
            assertEquals(air, airSteps);
            assertEquals(160, airSteps.size());
            assertEquals(BuildPlan.moduleKey(ArcaneCourtShape.KEY, site), p.key);
        }
    }

    /**
     * The controller stands on the octagon's centre (+-36, Y0 + 2, +-36), and the blueprint's front faces along A
     * toward the nexus: -A on sites 1 and 7, +A on 3 and 5; the foundation's centre is the module centre the dock
     * looks for.
     */
    @Test
    void theControllerStandsOnTheCentreFacingAlongA() {
        Blueprint b = ArcaneCourtShape.BLUEPRINT;
        for (int site : CampusPlan.PAVILION_SITES) for (int[] f : FACINGS) {
            int[] c = ArcaneCourtShape.controller(site, CX, Y0, CZ, f[0], f[1]);
            int[] centre = CampusPlan.diagonalCentre(site);
            assertArrayEquals(centre, local(c[0], c[2], f), "site " + site);
            assertEquals(Y0 + 2, c[1]);
            int[] front = BuildPlan.front(c[3]);
            int[] along = CampusPlan.toLocal(front[0], front[1], f[0], f[1]);
            assertArrayEquals(new int[] { centre[0] > 0 ? -1 : 1, 0 }, along, "site " + site + " faces along A");
            assertArrayEquals(CampusPlan.pavilionFront(site), along);
            int[] mc = b.world(12, ArcaneCourtShape.HEIGHT - 1, 12, c[0], c[1], c[2], front[0], front[1]);
            assertArrayEquals(CampusPlan.moduleCentre(site, ArcaneCourtShape.DEPTH), local(mc[0], mc[2], f));
            assertEquals(Y0, mc[1]);
            assertEquals(site, CampusPlan.pavilionSiteAt(centre[0] + 2, centre[1] - 3));
            assertArrayEquals(c, BuildPlan.moduleController(ArcaneCourtShape.BUILD, site, CX, Y0, CZ, f[0], f[1]));
        }
        // the back flat face (the gate niche) looks away from the nexus along A
        int[] c = ArcaneCourtShape.controller(1, CX, Y0, CZ, 0, -1);
        int[] front = BuildPlan.front(c[3]);
        int[] niche = b.world(12, ArcaneCourtShape.HEIGHT - 2, 24, c[0], c[1], c[2], front[0], front[1]);
        assertArrayEquals(new int[] { 48, 36 }, local(niche[0], niche[2], new int[] { 0, -1 }));
        assertThrows(IllegalArgumentException.class, () -> ArcaneCourtShape.controller(2, CX, Y0, CZ, 0, -1));
        assertThrows(
            IllegalArgumentException.class,
            () -> ArcaneCourtShape.plan(campus(FACINGS[0]), 4, CX, Y0, CZ, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> ArcaneCourtShape.controller(1, CX, Y0, CZ, 1, 1));
    }

    @Test
    void stepsAreUniqueAndStagesRunForward() {
        for (int site : ArcaneCourtShape.SITES) for (int[] f : FACINGS) {
            Plan p = court(site, f);
            Set<Long> seen = new HashSet<>();
            int last = -1;
            for (Step s : p.steps) {
                assertTrue(seen.add(pos(s.x, s.y, s.z)), "two steps on " + s);
                assertTrue(s.stage >= last);
                last = s.stage;
            }
            assertEquals(BuildPlan.M_STAGES, p.stages);
            assertEquals(0, p.count(BuildPlan.M_CLEAR));
            assertEquals(0, p.count(BuildPlan.M_DECK));
            assertEquals(0, p.count(BuildPlan.M_UPPER));
            assertEquals(1, p.count(BuildPlan.M_COMMISSION));
            Step core = p.steps.get(p.steps.size() - 1);
            assertEquals(Parts.moduleCore(ModuleCores.ARCANE_COURT), core.part);
            assertEquals(BuildPlan.HARD, core.kind);
            int[] c = ArcaneCourtShape.controller(site, CX, Y0, CZ, f[0], f[1]);
            assertTrue(core.at(c[0], c[1], c[2]));
        }
    }

    /** CITY.md §4.3: the hall's octagon and one round it, from Y0 + 1 to Y0 + 35 (the finial's top + 3), graded. */
    @Test
    void theClearBoxIsTheOctagonAndOneRoundItToY0Plus35() {
        for (int site : CampusPlan.PAVILION_SITES) {
            int[] f = FACINGS[site % 4];
            Plan p = court(site, f);
            int[] centre = CampusPlan.diagonalCentre(site);
            Set<Long> cols = new HashSet<>();
            for (BuildPlan.Column c : p.clear) {
                assertEquals(Y0 + 1, c.yFrom);
                assertEquals(Y0 + 35, c.yTo);
                assertTrue(c.grade);
                int[] l = local(c.x, c.z, f);
                assertTrue(cols.add(CampusPlan.key(l[0], l[1])));
                assertTrue(CampusPlan.oct(l[0] - centre[0], l[1] - centre[1], 13, 18), "inside oct(13, 18)");
            }
            int want = 0;
            for (int du = -13; du <= 13; du++) for (int dv = -13; dv <= 13; dv++) {
                if (CampusPlan.oct(du, dv, 13, 18)) {
                    want++;
                    assertTrue(cols.contains(CampusPlan.key(centre[0] + du, centre[1] + dv)));
                }
            }
            assertEquals(585, want);
            assertEquals(585, p.clear.size());
        }
    }

    /** The FLOOR stage: the site's floor (apron and allee) and the court's own soft floor with its rune ring. */
    @Test
    void theFloorStageHoldsTheApronTheAlleeAndTheRuneRing() {
        for (int site : CampusPlan.PAVILION_SITES) {
            int[] f = FACINGS[(site + 1) % 4];
            CampusPlan cp = campus(f);
            Plan p = ArcaneCourtShape.plan(cp, site, CX, Y0, CZ, f[0], f[1]);
            List<Step> floor = p.steps.subList(p.start(BuildPlan.M_FLOOR), p.end(BuildPlan.M_FLOOR));
            Map<Long, Integer> site0 = cp.pavilionFloor(site);
            assertEquals(181, site0.size(), "the apron's 140 and the allee's 44, three of them shared");
            int inner = 0;
            Map<Long, Integer> laid = new HashMap<>();
            for (Step s : floor) {
                assertEquals(BuildPlan.SOFT, s.kind);
                assertEquals(Y0, s.y);
                int[] l = local(s.x, s.z, f);
                long k = CampusPlan.key(l[0], l[1]);
                if (CampusPlan.inPavilion(site, l[0], l[1])) inner++;
                else laid.put(k, s.part);
            }
            assertEquals(304 + 81, inner, "the deck and the rune ring inside");
            assertEquals(site0, laid, "the site floor, every cell with its tile");
            assertEquals(site0.size() + inner, floor.size());
        }
    }

    @Test
    void theUnitsAreGroupsInSlotOrder() {
        Plan p = court(7, FACINGS[1]);
        List<Step> fit = p.steps.subList(p.start(BuildPlan.M_FIT), p.end(BuildPlan.M_FIT));
        Map<Integer, Integer> sizes = new HashMap<>();
        int lastGroup = BuildPlan.NO_GROUP, groups = 0;
        boolean afterGroups = false;
        List<Step> rest = new ArrayList<>();
        for (Step s : fit) {
            if (s.group != BuildPlan.NO_GROUP) {
                assertFalse(afterGroups, "the units are contiguous and first");
                if (s.group != lastGroup) {
                    assertEquals(lastGroup + 1, s.group, "in unit order");
                    groups++;
                    lastGroup = s.group;
                }
                sizes.merge(s.group, 1, Integer::sum);
            } else {
                afterGroups = true;
                rest.add(s);
            }
        }
        assertEquals(21, groups);
        assertEquals(8, (int) sizes.get(0), "the infusion circle");
        for (int g = 1; g <= 4; g++) assertEquals(9, (int) sizes.get(g), "a cauldron");
        for (int g = 5; g <= 12; g++) assertEquals(5, (int) sizes.get(g), "an aspect pillar");
        for (int g = 13; g <= 20; g++) assertEquals(2, (int) sizes.get(g), "a desk");
        // after the units the altar's console, then the finial bottom-up
        assertEquals(4, rest.size());
        assertEquals(Parts.frame(Parts.FR_CONSOLE), rest.get(0).part);
        assertEquals(Parts.frame(Parts.FR_CONDUIT), rest.get(1).part);
        assertEquals(Parts.frame(Parts.FR_CONDUIT), rest.get(2).part);
        assertEquals(Parts.frame(Parts.FR_SEAT), rest.get(3).part);
        assertTrue(rest.get(1).y < rest.get(2).y && rest.get(2).y < rest.get(3).y);
    }

    /** The gate niche is built, as SOFT steps of the wall's own blocks in the SHELL stage, on every site and front. */
    @Test
    void theGateNicheIsBuiltSoft() {
        Blueprint b = ArcaneCourtShape.BLUEPRINT;
        for (int site : ArcaneCourtShape.SITES) for (int[] f : FACINGS) {
            Plan p = court(site, f);
            int[] c = ArcaneCourtShape.controller(site, CX, Y0, CZ, f[0], f[1]);
            int[] front = BuildPlan.front(c[3]);
            Map<Long, Step> at = new HashMap<>();
            for (Step s : p.steps) at.put(pos(s.x, s.y, s.z), s);
            int n = 0;
            for (int x = 11; x <= 13; x++) for (int y = 1; y <= 4; y++) {
                int[] cell = ArcaneCourtShape.cellOf(x, y, 24);
                int[] w = b.world(cell[0], cell[1], cell[2], c[0], c[1], c[2], front[0], front[1]);
                Step s = at.get(pos(w[0], w[1], w[2]));
                assertNotNull(s, "the niche is built at " + x + "," + y + " site " + site);
                assertEquals(BuildPlan.SOFT, s.kind);
                assertEquals(BuildPlan.M_SHELL, s.stage);
                assertEquals(ShapeKit.partOf(ShapeKit.checkedTwin(ArcaneCourtShape.cell(x, y, 24))), s.part);
                n++;
            }
            assertEquals(12, n);
        }
    }

    /**
     * The DECOR stage holds the seeded annex only (no canopy): SOFT, outside the blueprint, on the apron's outer row
     * beside a flat side face, inside the apron {@code oct(14, 18)}; the same inputs give the same plan.
     */
    @Test
    void theAnnexStandsOnTheApron() {
        Set<Integer> kinds = new HashSet<>();
        for (int site : CampusPlan.PAVILION_SITES) for (int[] f : FACINGS) {
            Plan p = court(site, f);
            int[] c = ArcaneCourtShape.controller(site, CX, Y0, CZ, f[0], f[1]);
            long seed = Mix.seed(c[0], c[1], c[2]);
            kinds.add(BuildPlan.annexKind(seed));
            List<Step> decor = p.steps.subList(p.start(BuildPlan.M_DECOR), p.end(BuildPlan.M_DECOR));
            assertEquals(
                BuildPlan.annex(25, 25, BuildPlan.PAVILION_ANNEX_FROM, seed)
                    .size(),
                decor.size());
            int[] centre = CampusPlan.diagonalCentre(site);
            for (Step s : decor) {
                assertEquals(BuildPlan.SOFT, s.kind);
                assertTrue(s.y >= Y0 + 1 && s.y <= Y0 + 3);
                int[] l = local(s.x, s.z, f);
                int du = l[0] - centre[0], dv = l[1] - centre[1];
                assertTrue(CampusPlan.oct(du, dv, 14, 18), "on the apron: " + du + "," + dv);
                assertFalse(CampusPlan.oct(du, dv, 13, 17), "on its outer row");
            }
            assertEquals(p.steps, court(site, f).steps);
        }
        for (int i = 0; i < 100; i++) {
            long seed = Mix.seed(i * 41 - 700, 66, i * 13);
            for (int[] a : BuildPlan.annex(25, 25, BuildPlan.PAVILION_ANNEX_FROM, seed)) {
                assertTrue(a[0] == -2 || a[0] == 26);
                assertTrue(a[2] >= 8 && a[2] <= 16);
                assertTrue(ShapeKit.octo(a[0] - 12, a[2] - 12, 14, 18));
            }
        }
        assertTrue(kinds.size() >= 2, "the seed varies the annex");
    }

    /** Everything the job lays or clears lies in the pavilion's site: its apron with the building, and its allee. */
    @Test
    void theJobStaysInItsSite() {
        for (int site : CampusPlan.PAVILION_SITES) for (int[] f : FACINGS) {
            Plan p = court(site, f);
            Set<Long> own = CampusPlan.pavilionSite(site);
            for (Step s : p.steps) {
                int[] l = local(s.x, s.z, f);
                assertTrue(own.contains(CampusPlan.key(l[0], l[1])), "step outside the site: " + s);
            }
            for (BuildPlan.Column c : p.clear) {
                int[] l = local(c.x, c.z, f);
                assertTrue(own.contains(CampusPlan.key(l[0], l[1])), "clear column outside the site: " + c);
            }
            int[] e = BuildPlan.extent(p);
            assertNotNull(e);
        }
    }

    /** CITY.md §4.3's stages, probed in the world: the walls SHELL, the spire ROOF, the finial FIT, the core last. */
    @Test
    void theStagesHoldWhatCityLists() {
        Plan p = court(5, FACINGS[3]);
        int[] c = ArcaneCourtShape.controller(5, CX, Y0, CZ, -1, 0);
        int[] front = BuildPlan.front(c[3]);
        Map<Long, Integer> stageOf = new HashMap<>();
        for (Step s : p.steps) stageOf.put(pos(s.x, s.y, s.z), s.stage);
        Blueprint b = ArcaneCourtShape.BLUEPRINT;
        int[][] probes = { { 12, 0, 6 }, { 0, 6, 12 }, { 12, 12, 0 }, { 12, 20, 3 }, { 10, 28, 12 }, { 12, 31, 12 },
            { 12, 2, 12 } };
        int[] stages = { BuildPlan.M_FLOOR, BuildPlan.M_SHELL, BuildPlan.M_SHELL, BuildPlan.M_ROOF, BuildPlan.M_ROOF,
            BuildPlan.M_FIT, BuildPlan.M_COMMISSION };
        for (int i = 0; i < probes.length; i++) {
            int[] l = probes[i];
            assertTrue(ArcaneCourtShape.cell(l[0], l[1], l[2]) != ' ', "probe " + i);
            int[] cell = ArcaneCourtShape.cellOf(l[0], l[1], l[2]);
            int[] w = b.world(cell[0], cell[1], cell[2], c[0], c[1], c[2], front[0], front[1]);
            assertEquals(stages[i], (int) stageOf.get(pos(w[0], w[1], w[2])), "probe " + i);
        }
        // the spire's rings bottom-up
        int lastY = -1;
        for (Step s : p.steps.subList(p.start(BuildPlan.M_ROOF), p.end(BuildPlan.M_ROOF))) {
            assertTrue(s.y >= lastY);
            lastY = s.y;
        }
        assertEquals(Y0 + 29, lastY, "the lantern's cap last");
    }

    /**
     * The masterplan's sign of a diagonal (0.13.0) stands on the middle line of its allee, in front of the pavilion's
     * inner portal: outside every pavilion envelope and apron, so none of the four pavilions built there, on any nexus
     * front, puts a block in its column from the floor up past its height, and nearer the nexus than the portal.
     */
    @Test
    void theMasterplanSignStandsOnTheAlleeBeforeThePortal() {
        for (int site : CampusPlan.PAVILION_SITES) {
            int[] at = CampusPlan.pavilionSign(site), ax = CampusPlan.siteAxis(site);
            assertEquals(Math.abs(at[0]), Math.abs(at[1]), "on the allee's middle line");
            assertEquals(ax[0] * Math.abs(at[0]), at[0], "on its own diagonal");
            assertEquals(ax[1] * Math.abs(at[1]), at[1]);
            int t = Math.abs(at[0]) + Math.abs(at[1]);
            assertTrue(
                t >= CampusPlan.ALLEE_FROM && t < 2 * CampusPlan.DIAGONAL_CENTRE - CampusPlan.DIAGONAL_K,
                "between the ring and the portal: " + t);
            long k = CampusPlan.key(at[0], at[1]);
            assertTrue(
                CampusPlan.pavilionAllee(site)
                    .contains(k),
                "on the allee");
            for (int other : CampusPlan.PAVILION_SITES) {
                assertFalse(CampusPlan.inPavilion(other, at[0], at[1]), "in pavilion " + other);
                assertFalse(
                    CampusPlan.pavilionApron(other)
                        .contains(k),
                    "on the apron of " + other);
            }
            for (int[] f : FACINGS) {
                int[] w = CampusPlan.toWorld(at[0], at[1], f[0], f[1]);
                Plan[] plans = { court(site, f), BloodCovenantShape.plan(campus(f), site, CX, Y0, CZ, f[0], f[1]),
                    HuntingGroundShape.plan(campus(f), site, CX, Y0, CZ, f[0], f[1]),
                    ManaFountainShape.plan(campus(f), site, CX, Y0, CZ, f[0], f[1]) };
                for (Plan p : plans) for (Step s : p.steps) {
                    if (s.x == CX + w[0] && s.z == CZ + w[1])
                        assertTrue(s.y <= Y0, p.key + " builds in the sign's column at y" + s.y);
                }
            }
        }
        assertThrows(IllegalArgumentException.class, () -> CampusPlan.pavilionSign(4));
    }

    /**
     * Where the bridge to a module docked on a site runs when the client has not loaded the module (0.13.0): a hall
     * site's to the hall's front row, a diagonal's to a pavilion's inner portal only when the campus records a
     * pavilion built there; a module docked on a diagonal without one stands on the 0.9.2 inner ring and keeps its
     * ring bridge (-1), as the gate's does.
     */
    @Test
    void anUnloadedModulesBridgeRunsToItsDoor() {
        double portal = (2 * CampusPlan.DIAGONAL_CENTRE - CampusPlan.DIAGONAL_K - 0.5) / Math.sqrt(2);
        assertEquals(39.24, CampusPlan.PAVILION_DOOR, 0.01);
        assertEquals(portal, CampusPlan.PAVILION_DOOR, 1e-12);
        for (int site : CampusPlan.PAVILION_SITES) {
            assertEquals(CampusPlan.PAVILION_DOOR, CampusPlan.siteDoor(site, true), 1e-12, "a pavilion on " + site);
            assertEquals(-1, CampusPlan.siteDoor(site, false), 1e-12, "a ring module on " + site);
        }
        for (int site : CampusPlan.HALL_SITES) for (boolean pavilion : new boolean[] { false, true })
            assertEquals(CampusPlan.HALL_FRONT - 0.5, CampusPlan.siteDoor(site, pavilion), 1e-12, "hall " + site);
        for (boolean pavilion : new boolean[] { false, true }) {
            assertEquals(-1, CampusPlan.siteDoor(0, pavilion), 1e-12, "the gate");
            assertEquals(-1, CampusPlan.siteDoor(SiteFamily.ZENITH_SITE, pavilion), 1e-12, "the zenith");
        }
    }
}
