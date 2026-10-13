package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.BuildPlan.Plan;
import com.fluxecho.logic.BuildPlan.Step;

/**
 * The Dual-foil Vault's plan through the generic module plan (0.14.0): exactly its checked cells as HARD steps (four
 * fronts, its three hall sites), its door air, its soft cells and the gate niche built soft in the back wall's stage,
 * its 43 units as groups in slot order (42 canvases of 18 cells, the foil of 131) before the desk, the pedestals and
 * the anchors, its clear box, its floor and canopy and seeded ornaments, in CITY.md §4.2's stages and within the
 * budgets of CITY.md §1.3.
 */
class VaultPlanTest {

    private static final int[][] FACINGS = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
    private static final int CX = -300, Y0 = 70, CZ = 140;

    private static CampusPlan campus(int[] f) {
        return new CampusPlan(Mix.seed(CX + 7 * f[0], Y0 + 2, CZ - 3 * f[1]));
    }

    private static Plan vault(int site, int[] f) {
        return DualFoilVaultShape.plan(campus(f), site, CX, Y0, CZ, f[0], f[1]);
    }

    private static long pos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** The world block of a cell of the shape on a site and front. */
    private static int[] world(int site, int[] f, int x, int y, int z) {
        int[] c = DualFoilVaultShape.controller(site, CX, Y0, CZ, f[0], f[1]);
        int[] front = BuildPlan.front(c[3]);
        int[] cell = DualFoilVaultShape.cellOf(x, y, z);
        return DualFoilVaultShape.BLUEPRINT.world(cell[0], cell[1], cell[2], c[0], c[1], c[2], front[0], front[1]);
    }

    @Test
    void theHardStepsAreTheCheckedCells() {
        Blueprint b = DualFoilVaultShape.BLUEPRINT;
        for (int site : DualFoilVaultShape.SITES) for (int[] f : FACINGS) {
            Plan p = vault(site, f);
            int[] c = DualFoilVaultShape.controller(site, CX, Y0, CZ, f[0], f[1]);
            int[] front = BuildPlan.front(c[3]);
            Set<Long> want = new HashSet<>(), air = new HashSet<>();
            for (Blueprint.Cell cell : b.cells()) {
                int[] w = b.world(cell.a, cell.b, cell.c, c[0], c[1], c[2], front[0], front[1]);
                (cell.ch == '-' ? air : want).add(pos(w[0], w[1], w[2]));
            }
            Set<Long> hard = new HashSet<>(), airSteps = new HashSet<>();
            int soft = 0;
            for (Step s : p.steps) {
                if (s.kind == BuildPlan.HARD) assertTrue(hard.add(pos(s.x, s.y, s.z)), "a HARD cell twice");
                else if (s.kind == BuildPlan.AIR) airSteps.add(pos(s.x, s.y, s.z));
                else soft++;
            }
            assertEquals(want, hard, "site " + site);
            assertEquals(4644, hard.size());
            assertEquals(air, airSteps);
            assertEquals(48, airSteps.size());
            assertTrue(soft >= 837 + 14, "the soft cells and the canopy at least");
            assertEquals(BuildPlan.moduleKey(DualFoilVaultShape.KEY, site), p.key);
            // the controller faces the nexus, its front row at radial 24 on the site's axis
            int[] ax = CampusPlan.siteAxis(site);
            int[] l = CampusPlan.toLocal(c[0] - CX, c[2] - CZ, f[0], f[1]);
            assertArrayEquals(new int[] { CampusPlan.HALL_FRONT * ax[0], CampusPlan.HALL_FRONT * ax[1] }, l);
            assertEquals(Y0 + 2, c[1]);
            // the foundation's centre is the module centre the dock looks for
            int[] mc = b.world(13, DualFoilVaultShape.HEIGHT - 1, 17, c[0], c[1], c[2], front[0], front[1]);
            assertArrayEquals(
                CampusPlan.moduleCentre(site, DualFoilVaultShape.DEPTH),
                CampusPlan.toLocal(mc[0] - CX, mc[2] - CZ, f[0], f[1]));
            assertEquals(Y0, mc[1]);
        }
    }

    /**
     * The gate niche (CITY.md §4.15) in the foil is built, as twelve SOFT steps of the canvas block in the SHELL stage
     * of the back wall, on every site and front; no unit's group holds it.
     */
    @Test
    void theGateNicheIsBuiltSoft() {
        for (int site : DualFoilVaultShape.SITES) for (int[] f : FACINGS) {
            Plan p = vault(site, f);
            Map<Long, Step> at = new HashMap<>();
            for (Step s : p.steps) at.put(pos(s.x, s.y, s.z), s);
            int n = 0;
            for (int x = DualFoilVaultShape.NICHE_X0; x <= DualFoilVaultShape.NICHE_X1; x++)
                for (int y = DualFoilVaultShape.NICHE_Y0; y <= DualFoilVaultShape.NICHE_Y1; y++) {
                    int[] w = world(site, f, x, y, DualFoilVaultShape.NICHE_Z);
                    Step s = at.get(pos(w[0], w[1], w[2]));
                    assertNotNull(s, "the niche is built at " + x + "," + y + " site " + site);
                    assertEquals(BuildPlan.SOFT, s.kind);
                    assertEquals(BuildPlan.M_SHELL, s.stage);
                    assertEquals(BuildPlan.NO_GROUP, s.group, "in no unit");
                    assertEquals(Parts.deck(Parts.D_CANVAS), s.part, "the canvas block at " + x + "," + y);
                    n++;
                }
            assertEquals(12, n);
        }
    }

    @Test
    void stepsAreUniqueAndStagesRunForward() {
        for (int site : DualFoilVaultShape.SITES) for (int[] f : FACINGS) {
            Plan p = vault(site, f);
            Set<Long> seen = new HashSet<>();
            int last = -1;
            for (Step s : p.steps) {
                assertTrue(seen.add(pos(s.x, s.y, s.z)), "two steps on " + s);
                assertTrue(s.stage >= last);
                last = s.stage;
            }
            assertEquals(BuildPlan.M_STAGES, p.stages);
            assertEquals(0, p.count(BuildPlan.M_CLEAR));
            assertEquals(1, p.count(BuildPlan.M_COMMISSION));
            Step core = p.steps.get(p.steps.size() - 1);
            assertEquals(Parts.moduleCore(ModuleCores.DUAL_FOIL_VAULT), core.part);
            assertEquals(BuildPlan.HARD, core.kind);
            int[] c = DualFoilVaultShape.controller(site, CX, Y0, CZ, f[0], f[1]);
            assertTrue(core.at(c[0], c[1], c[2]));
        }
    }

    /**
     * FIT raises the units one group each in slot order, contiguous and bottom-up within a group (the 42 canvases of 18
     * cells, then the foil of 131), then the desk's console stand, the two holo pedestals and the four anchors.
     */
    @Test
    void theUnitsAreGroupsInSlotOrderBeforeTheDesk() {
        Plan p = vault(6, FACINGS[1]);
        List<Step> fit = p.steps.subList(p.start(BuildPlan.M_FIT), p.end(BuildPlan.M_FIT));
        Map<Integer, Integer> sizes = new HashMap<>();
        int lastGroup = BuildPlan.NO_GROUP, groups = 0;
        boolean afterGroups = false;
        List<Integer> rest = new java.util.ArrayList<>();
        int lastY = Integer.MIN_VALUE;
        for (Step s : fit) {
            if (s.group != BuildPlan.NO_GROUP) {
                assertFalse(afterGroups, "the units come first and together");
                if (s.group != lastGroup) {
                    assertEquals(lastGroup + 1, s.group, "in slot order");
                    groups++;
                    lastGroup = s.group;
                    lastY = Integer.MIN_VALUE;
                }
                assertTrue(s.y >= lastY, "bottom-up within a unit");
                lastY = s.y;
                sizes.merge(s.group, 1, Integer::sum);
                assertTrue(
                    s.part == Parts.deck(Parts.D_CANVAS) || s.part == Parts.fitting(Parts.F_PLINTH)
                        || s.part == Parts.fitting(Parts.F_CROWN),
                    "a unit of canvas, plinths and crowns");
            } else {
                afterGroups = true;
                rest.add(s.part);
            }
        }
        assertEquals(43, groups);
        for (int g = 0; g < 42; g++) assertEquals(18, (int) sizes.get(g), "canvas " + g);
        assertEquals(131, (int) sizes.get(42), "the foil");
        int console = Parts.frame(Parts.FR_CONSOLE), pedestal = Parts.fitting(Parts.F_PEDESTAL),
            anchor = Parts.frame(Parts.FR_ANCHOR);
        assertEquals(
            java.util.Arrays.asList(console, pedestal, pedestal, anchor, anchor, anchor, anchor),
            rest,
            "the desk, its pedestals, the facade's anchors");
    }

    /** The clear box: the whole box and one round it, from Y0 + 1 to two over the ridges (Y0 + 29), graded. */
    @Test
    void theClearBoxReachesY0Plus29() {
        for (int site : DualFoilVaultShape.SITES) {
            Plan p = vault(site, FACINGS[2]);
            assertEquals((27 + 2) * (35 + 2), p.clear.size());
            for (BuildPlan.Column c : p.clear) {
                assertEquals(Y0 + 1, c.yFrom);
                assertEquals(Y0 + 29, c.yTo);
                assertTrue(c.grade);
            }
            // CITY.md §1.3: a job's clear volume within 40,000, its steps within 8,000
            assertTrue(p.clear.size() * 29 <= 40_000);
            assertTrue(p.steps.size() <= 8_000, "steps " + p.steps.size());
        }
    }

    @Test
    void theFloorStageHoldsTheSiteFloorAndTheNave() {
        int[] f = FACINGS[3];
        Plan p = vault(2, f);
        List<Step> floor = p.steps.subList(p.start(BuildPlan.M_FLOOR), p.end(BuildPlan.M_FLOOR));
        int site = campus(f).siteFloor(2, DualFoilVaultShape.WIDTH, DualFoilVaultShape.DEPTH)
            .size();
        for (Step s : floor) {
            assertEquals(BuildPlan.SOFT, s.kind);
            assertEquals(Y0, s.y);
        }
        assertEquals(site + 25 * 33, floor.size(), "the site floor and the nave's 825 soft cells");
    }

    @Test
    void theOrnamentsComeFromTheSeed() {
        for (int site : DualFoilVaultShape.SITES) for (int[] f : FACINGS) {
            Plan p = vault(site, f);
            int[] c = DualFoilVaultShape.controller(site, CX, Y0, CZ, f[0], f[1]);
            long seed = Mix.seed(c[0], c[1], c[2]);
            List<Step> decor = p.steps.subList(p.start(BuildPlan.M_DECOR), p.end(BuildPlan.M_DECOR));
            assertEquals(
                7 * BuildPlan.canopyDepth(seed) + BuildPlan.annex(27, 35, seed)
                    .size(),
                decor.size());
            for (Step s : decor) assertEquals(BuildPlan.SOFT, s.kind);
        }
    }

    /** The stages hold what CITY.md §4.2 lists, probed cell by cell. */
    @Test
    void theStagesHoldWhatCityLists() {
        int[] f = FACINGS[0];
        Plan p = vault(4, f);
        Map<Long, Integer> stageOf = new HashMap<>();
        for (Step s : p.steps) stageOf.put(pos(s.x, s.y, s.z), s.stage);
        // a wall y7, a wall y15, the facade canvas, a catwalk, the cross walk, a stair step, a skylight, a ridge, the
        // cornice, the desk, the controller, a niche cell
        int[][] probes = { { 0, 7, 7 }, { 0, 15, 7 }, { 13, 15, 0 }, { 2, 8, 10 }, { 13, 16, 32 }, { 3, 1, 2 },
            { 13, 25, 3 }, { 13, 26, 3 }, { 13, 24, 0 }, { 13, 1, 30 }, { 13, 2, 0 }, { 13, 4, 34 } };
        int[] stages = { BuildPlan.M_SHELL, BuildPlan.M_UPPER, BuildPlan.M_UPPER, BuildPlan.M_DECK, BuildPlan.M_DECK,
            BuildPlan.M_DECK, BuildPlan.M_ROOF, BuildPlan.M_ROOF, BuildPlan.M_ROOF, BuildPlan.M_FIT,
            BuildPlan.M_COMMISSION, BuildPlan.M_SHELL };
        for (int i = 0; i < probes.length; i++) {
            int[] l = probes[i];
            assertTrue(DualFoilVaultShape.cell(l[0], l[1], l[2]) != ' ', "probe " + i);
            int[] w = world(4, f, l[0], l[1], l[2]);
            assertEquals(stages[i], (int) stageOf.get(pos(w[0], w[1], w[2])), "probe " + i);
        }
        for (int s = BuildPlan.M_FLOOR; s < BuildPlan.M_STAGES; s++) assertTrue(p.count(s) > 0, "stage " + s);
    }
}
