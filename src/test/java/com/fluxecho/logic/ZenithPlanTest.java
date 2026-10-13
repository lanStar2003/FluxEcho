package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.BuildPlan.Plan;
import com.fluxecho.logic.BuildPlan.Step;

/**
 * The Echo Dome Generator's job ({@link BuildPlan#zenith}, CITY.md §4.8): its HARD steps are exactly the cells the
 * {@code moored} structure check looks at, for every front; the stages come as CLEAR · LEGS · FLOOR · SHELL · ROOF ·
 * FIT · COMMISSION; the legs rise bottom-up all six together; the wards are groups; the controller is the last step;
 * the clearing takes only the legs' own heights over the forum and the pod's box in the air.
 */
class ZenithPlanTest {

    private static final int[][] FACINGS = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
    private static final int CX = 100, Y0 = 64, CZ = -200;

    private static Plan plan(int[] f) {
        return DomeGeneratorShape
            .plan(new CampusPlan(Mix.seed(CX, Y0 + 2, CZ)), SiteFamily.ZENITH_SITE, CX, Y0, CZ, f[0], f[1]);
    }

    private static long pos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    @Test
    void theHardStepsAreTheMooredShapesCells() {
        Blueprint b = DomeGeneratorShape.MOORED_BP;
        for (int[] f : FACINGS) {
            Plan p = plan(f);
            int[] c = DomeGeneratorShape.controller(SiteFamily.ZENITH_SITE, CX, Y0, CZ, f[0], f[1]);
            int[] front = BuildPlan.front(c[3]);
            Set<Long> want = new HashSet<>();
            for (Blueprint.Cell cell : b.cells()) {
                int[] w = b.world(cell.a, cell.b, cell.c, c[0], c[1], c[2], front[0], front[1]);
                want.add(pos(w[0], w[1], w[2]));
            }
            Set<Long> hard = new HashSet<>(), all = new HashSet<>();
            int soft = 0;
            for (Step s : p.steps) {
                assertTrue(all.add(pos(s.x, s.y, s.z)), "a cell twice");
                if (s.kind == BuildPlan.HARD) hard.add(pos(s.x, s.y, s.z));
                else {
                    assertEquals(BuildPlan.SOFT, s.kind, "no air steps");
                    soft++;
                }
            }
            assertEquals(want, hard, "front " + f[0] + "," + f[1]);
            assertEquals(1261, hard.size());
            assertEquals(276 + 96 + 4, soft);
            assertEquals(1637, p.steps.size());
            assertEquals(DomeGeneratorShape.Z_STAGES, p.stages);
            assertEquals(BuildPlan.moduleKey(DomeGeneratorShape.KEY, SiteFamily.ZENITH_SITE), p.key);
            // the controller last, as its module core, where the spec puts it
            Step last = p.steps.get(p.steps.size() - 1);
            assertEquals(Parts.moduleCore(ModuleCores.DOME_GENERATOR), last.part);
            assertEquals(DomeGeneratorShape.Z_COMMISSION, last.stage);
            assertTrue(last.at(c[0], c[1], c[2]));
            assertEquals(1, p.count(DomeGeneratorShape.Z_COMMISSION));
        }
    }

    @Test
    void theStages() {
        Plan p = plan(FACINGS[0]);
        assertEquals(0, p.count(DomeGeneratorShape.Z_CLEAR), "clearing has columns, no steps");
        assertEquals(234, p.count(DomeGeneratorShape.Z_LEGS));
        assertEquals(80 + 276 + 1, p.count(DomeGeneratorShape.Z_FLOOR), "foundation, deck, the lift pad");
        assertEquals(1 + 7 * 2, p.count(DomeGeneratorShape.Z_FIT), "the console, then seven wards of two");
        int legPart = Parts.frame(Parts.FR_PILLAR);
        int lastY = Integer.MIN_VALUE;
        for (int i = p.start(DomeGeneratorShape.Z_LEGS); i < p.end(DomeGeneratorShape.Z_LEGS); i++) {
            Step s = p.steps.get(i);
            assertEquals(legPart, s.part);
            assertEquals(BuildPlan.HARD, s.kind);
            assertTrue(s.y >= lastY, "the legs rise bottom-up, all six together");
            assertTrue(s.y >= Y0 + 1 && s.y <= Y0 + 39);
            lastY = s.y;
        }
        // the floor: the pod's floor layer at Y0 + 40
        for (int i = p.start(DomeGeneratorShape.Z_FLOOR); i < p.end(DomeGeneratorShape.Z_FLOOR); i++)
            assertEquals(Y0 + 40, p.steps.get(i).y);
        for (int i = p.start(DomeGeneratorShape.Z_SHELL); i < p.end(DomeGeneratorShape.Z_SHELL); i++) {
            int y = p.steps.get(i).y;
            assertTrue(y >= Y0 + 41 && y <= Y0 + 45);
        }
        for (int i = p.start(DomeGeneratorShape.Z_ROOF); i < p.end(DomeGeneratorShape.Z_ROOF); i++) {
            int y = p.steps.get(i).y;
            assertTrue(y >= Y0 + 46 && y <= Y0 + 49);
        }
        // the wards: one group each, contiguous, in unit order, after the console
        int fit = p.start(DomeGeneratorShape.Z_FIT);
        assertEquals(BuildPlan.NO_GROUP, p.steps.get(fit).group);
        for (int g = 0; g < 7; g++) for (int k = 0; k < 2; k++) assertEquals(g, p.steps.get(fit + 1 + 2 * g + k).group);
        // the stage keys as the build page reads them
        assertEquals(DomeGeneratorShape.Z_STAGES - 1, DomeGeneratorShape.Z_COMMISSION);
    }

    /**
     * CLEAR: each leg column over its own heights only (the forum's tiles and fixtures stay), the pod's box in the air.
     */
    @Test
    void theClearing() {
        for (int[] f : FACINGS) {
            Plan p = plan(f);
            assertEquals(40 + 23 * 23, p.clear.size());
            Map<Long, int[]> legs = new HashMap<>();
            for (int[] col : DomeGeneratorShape.legColumns()) {
                int[] w = CampusPlan.toWorld(col[0], col[1], f[0], f[1]);
                legs.put(CampusPlan.key(CX + w[0], CZ + w[1]), col);
            }
            int legCols = 0;
            for (BuildPlan.Column c : p.clear) {
                assertTrue(!c.grade, "nothing is graded");
                if (c.yFrom == Y0 + 40) {
                    assertEquals(Y0 + 52, c.yTo);
                    continue;
                }
                int[] col = legs.get(CampusPlan.key(c.x, c.z));
                assertTrue(col != null, "a column under the pod is a leg's");
                assertEquals(Y0 + col[2], c.yFrom);
                assertEquals(Y0 + col[3], c.yTo);
                assertTrue(c.yFrom >= Y0 + 1, "the forum's tiles stay");
                legCols++;
            }
            assertEquals(40, legCols);
            int[] e = BuildPlan.extent(p);
            int[] fp = DomeGeneratorShape.footprint(CX, CZ, f[0], f[1], 0);
            assertTrue(e[0] >= fp[0] && e[1] >= fp[1] && e[2] <= fp[2] && e[3] <= fp[3], "inside its footprint");
        }
    }

    @Test
    void onlyTheZenith() {
        assertThrows(IllegalArgumentException.class, () -> DomeGeneratorShape.controller(2, CX, Y0, CZ, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> BuildPlan.zenith(SiteFamily.ZENITH_SITE, CX, Y0, CZ, 1, 1));
    }

    /**
     * Over high ground (0.14.1) the job builds the moored shape of the campus's sky datum: its HARD steps are that
     * shape's cells in the world (those over y 255 left out, from Y0 219), the legs F - 1 high, the pod's floor at
     * Y0 + F, the clearing up to Y0 + F + 12, and the wards and the controller always in.
     */
    @Test
    void overHighGroundTheHardStepsAreTheDatumsShape() {
        for (int y0 : new int[] { 204, 209, 213, 218, 219, 222, 225 }) {
            DomeGeneratorShape.Moor m = DomeGeneratorShape.forBase(y0);
            int f = m.datum;
            Blueprint b = m.blueprint;
            Plan p = DomeGeneratorShape
                .plan(new CampusPlan(Mix.seed(CX, y0 + 2, CZ)), SiteFamily.ZENITH_SITE, CX, y0, CZ, 0, -1);
            int[] c = DomeGeneratorShape.controller(SiteFamily.ZENITH_SITE, CX, y0, CZ, 0, -1);
            assertEquals(y0 + f + 2, c[1]);
            int[] front = BuildPlan.front(c[3]);
            Set<Long> want = new HashSet<>();
            int over = 0;
            for (Blueprint.Cell cell : b.cells()) {
                int[] w = b.world(cell.a, cell.b, cell.c, c[0], c[1], c[2], front[0], front[1]);
                if (w[1] > 255) over++;
                else want.add(pos(w[0], w[1], w[2]));
            }
            if (y0 <= 218) assertEquals(0, over, "the whole pod in the world at " + y0);
            else assertTrue(over > 0, "the pod's top clips at " + y0);
            Set<Long> hard = new HashSet<>();
            for (Step s : p.steps) {
                assertTrue(s.y <= 255, "a step over the world at " + y0);
                if (s.kind == BuildPlan.HARD) hard.add(pos(s.x, s.y, s.z));
            }
            assertEquals(want, hard, "Y0 " + y0);
            assertEquals(6 * (f - 1), p.count(DomeGeneratorShape.Z_LEGS));
            for (int i = p.start(DomeGeneratorShape.Z_LEGS); i < p.end(DomeGeneratorShape.Z_LEGS); i++) {
                int y = p.steps.get(i).y;
                assertTrue(y >= y0 + 1 && y <= y0 + f - 1);
            }
            for (int i = p.start(DomeGeneratorShape.Z_FLOOR); i < p.end(DomeGeneratorShape.Z_FLOOR); i++)
                assertEquals(y0 + f, p.steps.get(i).y);
            assertEquals(1 + 7 * 2, p.count(DomeGeneratorShape.Z_FIT), "the console and every ward");
            assertTrue(
                p.steps.get(p.steps.size() - 1)
                    .at(c[0], c[1], c[2]),
                "the controller last");
            int pod = 0;
            for (BuildPlan.Column col : p.clear) if (col.yFrom == y0 + f) {
                assertEquals(y0 + f + 12, col.yTo);
                pod++;
            }
            assertEquals(23 * 23, pod);
            assertEquals(
                23 * 23 + m.legColumns()
                    .size(),
                p.clear.size());
        }
    }
}
