package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The release job's projection (0.16.0, CITY.md §3: the legs outlined in yellow 将拆除): the takedown outline is the
 * fold edges of the AIR steps' cells where one of our blocks still stands, so the six legs show as six outlined
 * pillars until the builder takes them down, and nothing once they are gone.
 */
class TakedownTest {

    @Test
    void theLegsAreOutlinedUntilTheyAreGone() {
        int cx = 100, y0 = 70, cz = -200;
        for (int datum : new int[] { 40, 36, 32, 28 }) {
            BuildPlan.Plan plan = BuildPlan.release(cx, y0, cz, 1, 0, datum);
            GhostCells.Cells cells = GhostCells.Cells.of(plan.steps);
            int[] air = XRay.airSteps(cells);
            assertEquals(plan.steps.size(), air.length, "every step takes a leg cell down");
            Set<Long> legs = new HashSet<>();
            for (int i : air) legs.add(XRay.pos(cells.x[i], cells.y[i], cells.z[i]));
            // all standing: outlined, every segment's ends on a leg cell's corners
            int[] all = XRay.takedown(cells, air, (x, y, z) -> legs.contains(XRay.pos(x, y, z)));
            assertTrue(all.length > 0 && all.length % 6 == 0);
            for (int k = 0; k < all.length; k += 6)
                assertTrue(touches(legs, all[k], all[k + 1], all[k + 2]), "datum " + datum);
            // nothing standing: nothing outlined
            assertEquals(0, XRay.takedown(cells, air, (x, y, z) -> false).length);
            // one cell left: its cube's twelve edges
            int i = air[air.length - 1];
            long one = XRay.pos(cells.x[i], cells.y[i], cells.z[i]);
            assertEquals(12 * 6, XRay.takedown(cells, air, (x, y, z) -> XRay.pos(x, y, z) == one).length);
        }
    }

    @Test
    void onlyTheAirStepsAreLookedAt() {
        // a plan's HARD and SOFT cells are never taken down, its first TAKEDOWN_STEPS AIR steps are
        java.util.List<BuildPlan.Step> steps = new java.util.ArrayList<>();
        steps.add(new BuildPlan.Step(0, 0, 0, 1, 0, BuildPlan.NO_GROUP, BuildPlan.HARD));
        steps.add(new BuildPlan.Step(1, 0, 0, Parts.AIR, 0, BuildPlan.NO_GROUP, BuildPlan.AIR));
        steps.add(new BuildPlan.Step(2, 0, 0, 1, 0, BuildPlan.NO_GROUP, BuildPlan.SOFT));
        steps.add(new BuildPlan.Step(3, 0, 0, Parts.AIR, 0, BuildPlan.NO_GROUP, BuildPlan.DIG));
        GhostCells.Cells cells = GhostCells.Cells.of(steps);
        int[] air = XRay.airSteps(cells);
        assertEquals(1, air.length);
        assertEquals(1, air[0]);
        assertEquals(12 * 6, XRay.takedown(cells, air, (x, y, z) -> true).length);
    }

    /** Whether the corner (x, y, z) is a corner of one of the cells. */
    private static boolean touches(Set<Long> cells, int x, int y, int z) {
        for (int dx = -1; dx <= 0; dx++) for (int dy = -1; dy <= 0; dy++)
            for (int dz = -1; dz <= 0; dz++) if (cells.contains(XRay.pos(x + dx, y + dy, z + dz))) return true;
        return false;
    }
}
