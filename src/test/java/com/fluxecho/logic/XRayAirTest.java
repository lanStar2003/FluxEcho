package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The projection of a plan that builds high in the air (0.12.0, the zenith's pod at Y0 + 40): what hangs is found (the
 * pod's floor, not the legs that hold themselves up, nor a hall's floating island), its shadow is the pod's octagon
 * on the forum, and plumb lines drop from its outermost corners.
 */
class XRayAirTest {

    private static final int CX = 100, Y0 = 64, CZ = -200;

    private static GhostCells.Cells zenith(int fx, int fz) {
        BuildPlan.Plan p = DomeGeneratorShape
            .plan(new CampusPlan(Mix.seed(CX, Y0 + 2, CZ)), SiteFamily.ZENITH_SITE, CX, Y0, CZ, fx, fz);
        return GhostCells.Cells.of(p.steps);
    }

    @Test
    void thePodHangsAndItsLegsDoNot() {
        GhostCells.Cells c = zenith(0, -1);
        assertEquals(Y0 + DomeGeneratorShape.FLOOR_UP, XRay.hangs(c, Y0), "the pod's floor");
        BuildPlan.Plan hall = EcoDomeShape.plan(new CampusPlan(7), 2, CX, Y0, CZ, 0, -1);
        assertEquals(Integer.MIN_VALUE, XRay.hangs(GhostCells.Cells.of(hall.steps), Y0), "a hall stands on the ground");
        BuildPlan.Plan well = RootShape.plan(new CampusPlan(7), SiteFamily.ROOT_SITE, CX, Y0, CZ, 0, -1);
        assertEquals(Integer.MIN_VALUE, XRay.hangs(GhostCells.Cells.of(well.steps), Y0));
        assertEquals(0, XRay.shadow(GhostCells.Cells.of(hall.steps), Integer.MIN_VALUE).length);
    }

    @Test
    void itsShadowIsThePodsOctagon() {
        for (int[] f : new int[][] { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } }) {
            GhostCells.Cells c = zenith(f[0], f[1]);
            int[] s = XRay.shadow(c, XRay.hangs(c, Y0));
            assertTrue(s.length >= 8 * 4, "an octagon's sides at least");
            int r = DomeGeneratorShape.POD_R;
            for (int k = 0; k < s.length; k += 2) {
                assertTrue(Math.abs(s[k] - CX - 0.5) <= r + 1, "within the pod's box across");
                assertTrue(Math.abs(s[k + 1] - CZ - 0.5) <= r + 1, "within the pod's box along");
            }
            int[] p = XRay.plumbs(s);
            assertTrue(p.length >= 8 && p.length <= 16, "up to eight plumb lines, one a direction: " + p.length);
            for (int k = 0; k < p.length; k += 2) for (int j = k + 2; j < p.length; j += 2)
                assertTrue(p[k] != p[j] || p[k + 1] != p[j + 1], "each corner once");
            int max = 0;
            for (int k = 0; k < p.length; k += 2) max = Math.max(max, Math.abs(p[k] - CX) + Math.abs(p[k + 1] - CZ));
            assertTrue(max >= DomeGeneratorShape.POD_K, "out at the chamfers");
        }
        assertEquals(0, XRay.plumbs(new int[0]).length);
    }
}
