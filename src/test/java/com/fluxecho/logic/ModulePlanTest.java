package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.BuildPlan.Plan;
import com.fluxecho.logic.BuildPlan.Step;

/**
 * The generic module plan (0.11.0): the Echo Archive's plan through it is cell for cell the plan of 0.10 (checksums
 * taken before the refactor), and the Eco Dome's plan holds exactly its checked cells as HARD steps (four fronts,
 * three sites), its door air, its soft cells, its planters as groups, its canopy and seeded ornaments, in CITY.md
 * §4.1's stages.
 */
class ModulePlanTest {

    private static final int[][] FACINGS = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
    private static final int CX = 100, Y0 = 64, CZ = -200;

    private static CampusPlan campus(int[] f) {
        return new CampusPlan(Mix.seed(CX + 5 * f[0], Y0 + 2, CZ + 5 * f[1]));
    }

    private static Plan dome(int site, int[] f) {
        return EcoDomeShape.plan(campus(f), site, CX, Y0, CZ, f[0], f[1]);
    }

    private static long pos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** The plan's checksum as 0.10's {@code BuildPlan.archive} gave it (every step and column, in order). */
    private static String digest(Plan p) {
        StringBuilder s = new StringBuilder(p.key).append('|')
            .append(p.stages)
            .append('|');
        for (int st = 0; st <= p.stages; st++) s.append(p.start(st))
            .append(',');
        for (Step x : p.steps) s.append(x.x)
            .append(',')
            .append(x.y)
            .append(',')
            .append(x.z)
            .append(',')
            .append(x.part)
            .append(',')
            .append(x.stage)
            .append(',')
            .append(x.group)
            .append(',')
            .append(x.kind)
            .append(';');
        for (BuildPlan.Column x : p.clear) s.append(x.x)
            .append(',')
            .append(x.z)
            .append(',')
            .append(x.yFrom)
            .append(',')
            .append(x.yTo)
            .append(',')
            .append(x.grade)
            .append(';');
        CRC32 crc = new CRC32();
        crc.update(
            s.toString()
                .getBytes(StandardCharsets.UTF_8));
        return p.steps.size() + " " + p.clear.size() + " " + Long.toHexString(crc.getValue());
    }

    /** The Archive's plans, cell for cell, for every front and hall site, as 0.10.1 laid them. */
    @Test
    void theArchivesPlanIsCellForCellTheSame() {
        String[][] want = { { "6237 1073 d066959e", "6237 1073 616b3395", "6237 1073 157da197" },
            { "6237 1073 7d05ec8a", "6237 1073 804d9e0a", "6237 1073 146d0313" },
            { "6237 1073 91b986dd", "6237 1073 d4e2eb2", "6237 1073 9530c922" },
            { "6237 1073 98673035", "6237 1073 93afa224", "6237 1073 bac37f2b" } };
        for (int i = 0; i < FACINGS.length; i++) for (int j = 0; j < CampusPlan.HALL_SITES.length; j++) {
            int[] f = FACINGS[i];
            int site = CampusPlan.HALL_SITES[j];
            assertEquals(
                want[i][j],
                digest(BuildPlan.archive(campus(f), site, CX, Y0, CZ, f[0], f[1])),
                "front " + f[0] + "," + f[1] + " site " + site);
        }
    }

    @Test
    void theHardStepsAreTheCheckedCells() {
        Blueprint b = EcoDomeShape.BLUEPRINT;
        for (int site : EcoDomeShape.SITES) for (int[] f : FACINGS) {
            Plan p = dome(site, f);
            int[] c = EcoDomeShape.controller(site, CX, Y0, CZ, f[0], f[1]);
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
            assertEquals(2777, hard.size());
            assertEquals(air, airSteps);
            assertEquals(48, airSteps.size());
            assertTrue(soft >= 774 + 14, "the soft cells and the canopy at least");
            assertEquals(BuildPlan.moduleKey(EcoDomeShape.KEY, site), p.key);
            // the controller faces the nexus, its front row at radial 24 on the site's axis
            int[] ax = CampusPlan.siteAxis(site);
            int[] l = CampusPlan.toLocal(c[0] - CX, c[2] - CZ, f[0], f[1]);
            assertArrayEquals(new int[] { CampusPlan.HALL_FRONT * ax[0], CampusPlan.HALL_FRONT * ax[1] }, l);
            assertEquals(Y0 + 2, c[1]);
            // the foundation's centre is the module centre the dock looks for
            int[] mc = b.world(13, EcoDomeShape.HEIGHT - 1, 17, c[0], c[1], c[2], front[0], front[1]);
            assertArrayEquals(
                CampusPlan.moduleCentre(site, EcoDomeShape.DEPTH),
                CampusPlan.toLocal(mc[0] - CX, mc[2] - CZ, f[0], f[1]));
            assertEquals(Y0, mc[1]);
        }
    }

    /**
     * The gate niche (CITY.md §4.15) is built, as SOFT steps of the drum's own blocks in the SHELL stage, on every site
     * and front: the dome looks the same, but the niche is not part of the checked shape.
     */
    @Test
    void theGateNicheIsBuiltSoft() {
        Blueprint b = EcoDomeShape.BLUEPRINT;
        for (int site : EcoDomeShape.SITES) for (int[] f : FACINGS) {
            Plan p = dome(site, f);
            int[] c = EcoDomeShape.controller(site, CX, Y0, CZ, f[0], f[1]);
            int[] front = BuildPlan.front(c[3]);
            Map<Long, Step> at = new HashMap<>();
            for (Step s : p.steps) at.put(pos(s.x, s.y, s.z), s);
            int n = 0;
            for (int x = EcoDomeShape.NICHE_X0; x <= EcoDomeShape.NICHE_X1; x++)
                for (int y = EcoDomeShape.NICHE_Y0; y <= EcoDomeShape.NICHE_Y1; y++) {
                    int[] cell = EcoDomeShape.cellOf(x, y, EcoDomeShape.NICHE_Z);
                    int[] w = b.world(cell[0], cell[1], cell[2], c[0], c[1], c[2], front[0], front[1]);
                    Step s = at.get(pos(w[0], w[1], w[2]));
                    assertNotNull(s, "the niche is built at " + x + "," + y + " site " + site);
                    assertEquals(BuildPlan.SOFT, s.kind);
                    assertEquals(BuildPlan.M_SHELL, s.stage);
                    char wall = y == 1 ? 'w' : x == EcoDomeShape.MID ? 'L' : 'G';
                    assertEquals(ShapeKit.partOf(wall), s.part, "the drum's own block at " + x + "," + y);
                    n++;
                }
            assertEquals(12, n);
        }
    }

    @Test
    void stepsAreUniqueAndStagesRunForward() {
        for (int site : EcoDomeShape.SITES) for (int[] f : FACINGS) {
            Plan p = dome(site, f);
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
            assertEquals(Parts.moduleCore(ModuleCores.ECO_DOME), core.part);
            assertEquals(BuildPlan.HARD, core.kind);
            int[] c = EcoDomeShape.controller(site, CX, Y0, CZ, f[0], f[1]);
            assertTrue(core.at(c[0], c[1], c[2]));
        }
    }

    @Test
    void thePlantersAreGroupsOfFifteenInUnitOrder() {
        Plan p = dome(2, FACINGS[0]);
        List<Step> fit = p.steps.subList(p.start(BuildPlan.M_FIT), p.end(BuildPlan.M_FIT));
        Map<Integer, Integer> sizes = new HashMap<>();
        int lastGroup = BuildPlan.NO_GROUP, groups = 0;
        boolean inGroups = false, afterGroups = false;
        for (Step s : fit) {
            if (s.group != BuildPlan.NO_GROUP) {
                assertFalse(afterGroups, "the planters are contiguous");
                inGroups = true;
                if (s.group != lastGroup) {
                    assertEquals(lastGroup + 1, s.group, "in unit order");
                    groups++;
                    lastGroup = s.group;
                }
                sizes.merge(s.group, 1, Integer::sum);
            } else if (inGroups) afterGroups = true;
        }
        assertEquals(12, groups);
        // fifteen cells a planter; the two on the gallery (U-R, U-L) leave the drum's cornice
        for (int g = 0; g < 12; g++) assertEquals(g == 9 || g == 10 ? 13 : 15, (int) sizes.get(g));
        // the tree comes first in FIT, the water after its rim
        assertEquals(Parts.frame(Parts.FR_CONDUIT), fit.get(0).part);
        int rim = -1, water = -1;
        for (int i = 0; i < fit.size(); i++) {
            Step s = fit.get(i);
            if (s.group != BuildPlan.NO_GROUP) continue;
            if (s.part == Parts.fitting(Parts.F_BASIN) && rim < 0) rim = i;
            if (s.part == Parts.WATER && water < 0) water = i;
        }
        assertTrue(rim >= 0 && water > rim, "rim " + rim + " water " + water);
    }

    @Test
    void theClearBoxReachesY0Plus23() {
        Plan p = dome(2, FACINGS[1]);
        assertEquals((27 + 2) * (35 + 2), p.clear.size());
        for (BuildPlan.Column c : p.clear) {
            assertEquals(Y0 + 1, c.yFrom);
            assertEquals(Y0 + 23, c.yTo);
            assertTrue(c.grade);
        }
    }

    @Test
    void theFloorStageHoldsTheSiteFloorAndTheGarden() {
        int[] f = FACINGS[2];
        Plan p = dome(6, f);
        List<Step> floor = p.steps.subList(p.start(BuildPlan.M_FLOOR), p.end(BuildPlan.M_FLOOR));
        int site = campus(f).siteFloor(6, EcoDomeShape.WIDTH, EcoDomeShape.DEPTH)
            .size();
        // the y0 soft cells (deck and the garden's grass); the islands' grass is in DECK and FIT
        Map<Integer, Integer> want = new HashMap<>();
        int ground = 0;
        for (int x = 0; x < 27; x++) for (int z = 0; z < 35; z++) {
            char ch = EcoDomeShape.cell(x, 0, z);
            if (!ShapeKit.soft(ch)) continue;
            ground++;
            if (ch == ',') want.merge(Parts.GRASS, 1, Integer::sum);
        }
        int grass = 0;
        for (Step s : floor) {
            assertEquals(BuildPlan.SOFT, s.kind);
            assertEquals(Y0, s.y);
            if (s.part == Parts.GRASS) grass++;
        }
        assertTrue(ground > 500, "the floor is mostly soft");
        assertEquals(site + ground, floor.size());
        assertTrue(grass >= want.get(Parts.GRASS), "the garden's grass at least");
        int islandGrass = 0;
        for (int y = 1; y < EcoDomeShape.HEIGHT; y++) for (int x = 0; x < 27; x++)
            for (int z = 0; z < 35; z++) if (EcoDomeShape.cell(x, y, z) == ',') islandGrass++;
        assertTrue(islandGrass > 0, "grass on the islands");
    }

    @Test
    void theOrnamentsComeFromTheSeed() {
        Set<Integer> depths = new HashSet<>(), kinds = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            long seed = Mix.seed(i * 37 - 900, 64, i * 11);
            int d = BuildPlan.canopyDepth(seed);
            assertTrue(d >= 2 && d <= 4);
            depths.add(d);
            kinds.add(BuildPlan.annexKind(seed));
            for (int[] c : BuildPlan.annex(27, 35, seed)) {
                assertTrue(c[0] == -2 || c[0] == 28, "on the apron's outer row");
                assertTrue(c[1] >= 1 && c[1] <= 3);
                assertTrue(c[2] >= 6 && c[2] <= 28);
            }
        }
        assertEquals(3, depths.size(), "every canopy depth");
        assertEquals(4, kinds.size(), "every annex");
        // the DECOR stage: the canopy (two to four rows of seven) and the annex, all SOFT and outside the blueprint
        for (int site : EcoDomeShape.SITES) for (int[] f : FACINGS) {
            Plan p = dome(site, f);
            int[] c = EcoDomeShape.controller(site, CX, Y0, CZ, f[0], f[1]);
            long seed = Mix.seed(c[0], c[1], c[2]);
            List<Step> decor = p.steps.subList(p.start(BuildPlan.M_DECOR), p.end(BuildPlan.M_DECOR));
            assertEquals(
                7 * BuildPlan.canopyDepth(seed) + BuildPlan.annex(27, 35, seed)
                    .size(),
                decor.size());
            for (Step s : decor) assertEquals(BuildPlan.SOFT, s.kind);
            // the same inputs give the same plan
            assertEquals(digest(p), digest(dome(site, f)));
        }
        // two sites of one nexus differ in their ornaments' seeds
        int[] a = EcoDomeShape.controller(2, CX, Y0, CZ, 0, -1), b = EcoDomeShape.controller(6, CX, Y0, CZ, 0, -1);
        assertNotEquals(Mix.seed(a[0], a[1], a[2]), Mix.seed(b[0], b[1], b[2]));
    }

    @Test
    void theStagesHoldWhatCityLists() {
        Plan p = dome(2, FACINGS[3]);
        int[] c = EcoDomeShape.controller(2, CX, Y0, CZ, -1, 0);
        int[] front = BuildPlan.front(c[3]);
        Map<Long, Integer> stageOf = new HashMap<>();
        for (Step s : p.steps) stageOf.put(pos(s.x, s.y, s.z), s.stage);
        Blueprint b = EcoDomeShape.BLUEPRINT;
        // the drum's glazing is SHELL, the dome's ROOF, the island's rim DECK, the trunk FIT
        int[][] probes = { { 13, 3, 34 }, { 13, 14, 32 }, { 9, 6, 21 }, { 13, 9, 21 }, { 13, 2, 0 } };
        int[] stages = { BuildPlan.M_SHELL, BuildPlan.M_ROOF, BuildPlan.M_DECK, BuildPlan.M_FIT,
            BuildPlan.M_COMMISSION };
        for (int i = 0; i < probes.length; i++) {
            int[] l = probes[i];
            assertTrue(EcoDomeShape.cell(l[0], l[1], l[2]) != ' ', "probe " + i);
            int[] cell = EcoDomeShape.cellOf(l[0], l[1], l[2]);
            int[] w = b.world(cell[0], cell[1], cell[2], c[0], c[1], c[2], front[0], front[1]);
            assertEquals(stages[i], (int) stageOf.get(pos(w[0], w[1], w[2])), "probe " + i);
        }
        List<Integer> counts = new ArrayList<>();
        for (int s = 0; s < BuildPlan.M_STAGES; s++) counts.add(p.count(s));
        for (int s = BuildPlan.M_FLOOR; s < BuildPlan.M_STAGES; s++) assertTrue(counts.get(s) > 0, "stage " + s);
    }
}
