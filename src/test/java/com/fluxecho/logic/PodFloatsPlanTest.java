package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.BuildPlan.Plan;
import com.fluxecho.logic.BuildPlan.Step;

/**
 * The pod floats (0.16.0, CITY.md §3, M13 §4): the release job's plan takes down exactly the moored shape's legs at
 * the pod's datum, from the top, as AIR steps, and lays nothing else; the floating plan is the moored plan without its
 * legs, step for step, its HARD steps the cells the floating structure check looks at, its clearing the pod's box
 * alone; it may leave the doors of given sky slots unglazed; and the pod's doors belong to the eight sky slots as the
 * sky family's catwalks meet them.
 */
class PodFloatsPlanTest {

    private static final int[][] FACINGS = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
    private static final int CX = 100, Y0 = 64, CZ = -200, ZENITH = SiteFamily.ZENITH_SITE;

    private static long pos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /**
     * The release at every datum and front: one AIR step per leg cell of the moored shape there (234, 210, 186, 162),
     * exactly the cells the moored plan lays its LEGS stage in, the highest first and never a lower before a higher, in
     * its first stage; the second has none; no clear column, so the forum, the supply port and the bin stay.
     */
    @Test
    void theReleaseTakesTheLegsDownFromTheTop() {
        int[][] cases = { { 64, 40 }, { 205, 36 }, { 210, 32 }, { 214, 28 }, { 205, 40 } };
        for (int[] k : cases) {
            int y0 = k[0], f = k[1];
            DomeGeneratorShape.Moor moor = DomeGeneratorShape.moor(f);
            for (int[] fr : FACINGS) {
                Plan p = BuildPlan.release(CX, y0, CZ, fr[0], fr[1], f);
                assertEquals(BuildPlan.RELEASE, p.key);
                assertEquals(BuildPlan.L_STAGES, p.stages);
                assertTrue(p.clear.isEmpty(), "nothing is cleared");
                assertEquals(6 * (f - 1), p.steps.size());
                assertEquals(6 * (f - 1), p.count(BuildPlan.L_LEGS));
                assertEquals(0, p.count(BuildPlan.L_FLOAT), "the pod floats: no step");
                // the moored plan's legs at that datum, built where the moored blueprint has them
                Set<Long> want = new HashSet<>();
                int[] c = moor.controller(ZENITH, CX, y0, CZ, fr[0], fr[1]);
                int[] front = BuildPlan.front(c[3]);
                Blueprint b = moor.blueprint;
                for (Blueprint.Cell cell : b.cells()) {
                    if (cell.ch != DomeGeneratorShape.LEG) continue;
                    int[] w = b.world(cell.a, cell.b, cell.c, c[0], c[1], c[2], front[0], front[1]);
                    want.add(pos(w[0], w[1], w[2]));
                }
                assertEquals(6 * (f - 1), want.size());
                Set<Long> got = new HashSet<>();
                int lastY = Integer.MAX_VALUE;
                for (Step s : p.steps) {
                    assertEquals(BuildPlan.AIR, s.kind);
                    assertEquals(Parts.AIR, s.part);
                    assertEquals(BuildPlan.L_LEGS, s.stage);
                    assertEquals(BuildPlan.NO_GROUP, s.group);
                    assertTrue(s.y <= lastY, "top-down");
                    assertTrue(s.y >= y0 + 1 && s.y <= y0 + f - 1, "a leg's height");
                    lastY = s.y;
                    assertTrue(got.add(pos(s.x, s.y, s.z)), "a cell twice");
                }
                assertEquals(want, got, "Y0 " + y0 + " F " + f);
                assertEquals(y0 + f - 1, p.steps.get(0).y, "the legs' heads first");
                assertEquals(y0 + 1, p.steps.get(p.steps.size() - 1).y, "their feet last");
            }
        }
        // at the full datum the moored plan's own LEGS steps are its cells too, as the builder laid them
        Plan moored = BuildPlan.zenith(ZENITH, CX, Y0, CZ, 0, -1);
        Set<Long> legs = new HashSet<>();
        for (int i = moored.start(DomeGeneratorShape.Z_LEGS); i < moored.end(DomeGeneratorShape.Z_LEGS); i++) {
            Step s = moored.steps.get(i);
            legs.add(pos(s.x, s.y, s.z));
        }
        Set<Long> released = new HashSet<>();
        for (Step s : BuildPlan.release(CX, Y0, CZ, 0, -1, 40).steps) released.add(pos(s.x, s.y, s.z));
        assertEquals(legs, released);
        assertThrows(IllegalArgumentException.class, () -> BuildPlan.release(CX, Y0, CZ, 0, -1, 38));
        assertThrows(IllegalArgumentException.class, () -> BuildPlan.release(CX, Y0, CZ, 1, 1, 40));
    }

    /**
     * The floating plan is the moored plan at the same datum without its LEGS stage and its leg columns: every other
     * step the same, in the same order, in the same stage; its clear columns the pod's box alone; nothing placed
     * under the pod's floor.
     */
    @Test
    void theFloatingPlanIsTheMooredPlanWithoutItsLegs() {
        for (int[] fr : FACINGS) {
            for (int y0 : new int[] { Y0, 205, 209, 213, 220 }) {
                int f = DomeGeneratorShape.datum(y0);
                Plan moored = BuildPlan.zenith(ZENITH, CX, y0, CZ, fr[0], fr[1]);
                Plan floating = BuildPlan.floating(ZENITH, CX, y0, CZ, fr[0], fr[1], f, 0);
                assertEquals(moored.key, floating.key, "the dome's job key: the job field picks the plan");
                assertEquals(DomeGeneratorShape.Z_STAGES, floating.stages);
                assertEquals(0, floating.count(DomeGeneratorShape.Z_LEGS), "never a leg");
                List<Step> want = new ArrayList<>();
                for (Step s : moored.steps) if (s.stage != DomeGeneratorShape.Z_LEGS) want.add(s);
                assertEquals(want, floating.steps, "Y0 " + y0);
                for (int s = 0; s < DomeGeneratorShape.Z_STAGES; s++)
                    if (s != DomeGeneratorShape.Z_LEGS) assertEquals(moored.count(s), floating.count(s), "stage " + s);
                for (Step s : floating.steps) assertTrue(s.y >= y0 + f, "nothing under the pod's floor");
                assertEquals(23 * 23, floating.clear.size(), "the pod's box alone");
                for (BuildPlan.Column c : floating.clear) {
                    assertEquals(y0 + f, c.yFrom);
                    assertEquals(y0 + f + 12, c.yTo);
                    assertFalse(c.grade);
                    assertTrue(moored.clear.contains(c), "a column the moored plan clears too");
                }
            }
        }
    }

    /**
     * Its HARD steps are exactly the cells of the floating blueprint round the controller at the plan's datum (those
     * over y 255 left out), for every front: at the datum the base layer gives, and at 40 for a pod built by hand on
     * the full legs over high ground before 0.14.1.
     */
    @Test
    void theFloatingPlansHardStepsAreTheFloatingShapesCells() {
        Blueprint b = DomeGeneratorShape.FLOATING_BP;
        int[][] cases = { { Y0, 40 }, { 205, 36 }, { 205, 40 }, { 213, 40 }, { 214, 28 }, { 222, 28 } };
        for (int[] k : cases) for (int[] fr : FACINGS) {
            int y0 = k[0], f = k[1];
            Plan p = BuildPlan.floating(ZENITH, CX, y0, CZ, fr[0], fr[1], f, 0);
            int[] c = DomeGeneratorShape.moor(f)
                .controller(ZENITH, CX, y0, CZ, fr[0], fr[1]);
            assertEquals(y0 + f + 2, c[1]);
            int[] front = BuildPlan.front(c[3]);
            Set<Long> want = new HashSet<>();
            for (Blueprint.Cell cell : b.cells()) {
                int[] w = b.world(cell.a, cell.b, cell.c, c[0], c[1], c[2], front[0], front[1]);
                if (w[1] <= 255) want.add(pos(w[0], w[1], w[2]));
            }
            Set<Long> hard = new HashSet<>();
            int soft = 0;
            for (Step s : p.steps) {
                if (s.kind == BuildPlan.HARD) hard.add(pos(s.x, s.y, s.z));
                else soft++;
            }
            assertEquals(want, hard, "Y0 " + y0 + " F " + f);
            if (y0 + f + 9 <= 255) {
                assertEquals(1027, hard.size());
                assertEquals(276 + 96 + 4, soft);
            }
            Step last = p.steps.get(p.steps.size() - 1);
            assertTrue(last.at(c[0], c[1], c[2]), "the controller last");
            assertEquals(Parts.moduleCore(ModuleCores.DOME_GENERATOR), last.part);
        }
        assertThrows(
            IllegalArgumentException.class,
            () -> BuildPlan.floating(ZENITH, CX, Y0, CZ, 0, -1, 39, 0),
            "no sky datum");
        assertThrows(IllegalArgumentException.class, () -> BuildPlan.floating(2, CX, Y0, CZ, 0, -1, 40, 0));
    }

    /**
     * A floating plan given sky slots leaves their doors unglazed (the sky family's rule picks which): the twelve
     * glazing steps of each slot go, nothing else; with every slot all 96 door cells, and the plan still ends with the
     * controller.
     */
    @Test
    void aFloatingPlanLeavesTheGivenDoorsOpen() {
        Plan all = BuildPlan.floating(ZENITH, CX, Y0, CZ, 0, -1, 40, 0);
        int[] c = DomeGeneratorShape.moor(40)
            .controller(ZENITH, CX, Y0, CZ, 0, -1);
        int[] front = BuildPlan.front(c[3]);
        CityBuild fb = DomeGeneratorShape.FLOATING_BUILD;
        for (int slot = 0; slot < DomeGeneratorShape.SKY_SLOTS; slot++) {
            Plan open = BuildPlan.floating(ZENITH, CX, Y0, CZ, 0, -1, 40, 1 << slot);
            assertEquals(all.steps.size() - 12, open.steps.size(), "slot " + slot);
            Set<Long> door = new HashSet<>();
            for (int[] d : DomeGeneratorShape.doorCells(slot)) {
                int[] cell = fb.cellOf(d[0], d[1], d[2]);
                int[] w = fb.blueprint()
                    .world(cell[0], cell[1], cell[2], c[0], c[1], c[2], front[0], front[1]);
                door.add(pos(w[0], w[1], w[2]));
            }
            assertEquals(12, door.size());
            List<Step> want = new ArrayList<>();
            for (Step s : all.steps) {
                if (!door.contains(pos(s.x, s.y, s.z))) want.add(s);
                else {
                    assertEquals(BuildPlan.SOFT, s.kind, "a door is soft glazing");
                    assertEquals(Parts.fitting(Parts.F_GLAZE), s.part);
                }
            }
            assertEquals(want, open.steps);
        }
        Plan none = BuildPlan.floating(ZENITH, CX, Y0, CZ, 0, -1, 40, 0xFF);
        assertEquals(all.steps.size() - 96, none.steps.size());
        assertTrue(
            none.steps.get(none.steps.size() - 1)
                .at(c[0], c[1], c[2]));
    }

    /**
     * The pod's 24 door columns belong to the eight sky slots, three each, twelve cells each: S0 on the gate's axis
     * (+A: z 0, x 9..11), S2 on +R (x 0), S4 on -A (z 20), S6 on -R (x 20), the odd ones on the chamfers between
     * (S1 (3, 3), (4, 3), (3, 4), between +A and +R); mirrored across the gate's axis the slots go k to 8 - k.
     */
    @Test
    void theDoorsBelongToTheSkySlots() {
        Set<Long> seen = new HashSet<>();
        for (int slot = 0; slot < DomeGeneratorShape.SKY_SLOTS; slot++) {
            List<int[]> cells = DomeGeneratorShape.doorCells(slot);
            assertEquals(12, cells.size(), "slot " + slot);
            Set<Long> cols = new HashSet<>();
            for (int[] d : cells) {
                assertTrue(DomeGeneratorShape.door(d[0], d[1], d[2]));
                assertTrue(seen.add(pos(d[0], d[1], d[2])), "a door cell of two slots");
                cols.add(pos(d[0], 0, d[2]));
                assertEquals(slot, DomeGeneratorShape.doorSlot(d[0], d[2]));
                assertEquals((8 - slot) % 8, DomeGeneratorShape.doorSlot(DomeGeneratorShape.SIZE - 1 - d[0], d[2]));
            }
            assertEquals(3, cols.size());
        }
        assertEquals(96, seen.size(), "every door cell");
        assertTrue(
            DomeGeneratorShape.doorCells(8)
                .isEmpty());
        assertEquals(-1, DomeGeneratorShape.doorSlot(10, 10), "the pad is no door");
        assertEquals(-1, DomeGeneratorShape.doorSlot(1, 4), "a wall column");
        for (int x = 9; x <= 11; x++) {
            assertEquals(0, DomeGeneratorShape.doorSlot(x, 0));
            assertEquals(2, DomeGeneratorShape.doorSlot(0, x));
            assertEquals(4, DomeGeneratorShape.doorSlot(x, 20));
            assertEquals(6, DomeGeneratorShape.doorSlot(20, x));
        }
        int[][] diag = { { 3, 3, 1 }, { 4, 3, 1 }, { 3, 4, 1 }, { 3, 16, 3 }, { 3, 17, 3 }, { 4, 17, 3 }, { 16, 17, 5 },
            { 17, 16, 5 }, { 17, 17, 5 }, { 16, 3, 7 }, { 17, 3, 7 }, { 17, 4, 7 } };
        for (int[] d : diag) assertEquals(d[2], DomeGeneratorShape.doorSlot(d[0], d[1]), d[0] + "," + d[1]);
    }

    /**
     * A pod's datum is where its controller stands over the base layer, the full legs' 40 over high ground too; only
     * the four sky datums are datums.
     */
    @Test
    void aPodsDatumIsWhereItsControllerStands() {
        for (int y0 : new int[] { 14, 64, 203, 205, 210, 214, 225 }) {
            int f = DomeGeneratorShape.datum(y0);
            int[] c = DomeGeneratorShape.controller(ZENITH, CX, y0, CZ, 0, -1);
            assertEquals(f, DomeGeneratorShape.podDatum(c[1], y0));
            int[] full = DomeGeneratorShape.moor(40)
                .controller(ZENITH, CX, y0, CZ, 0, -1);
            assertEquals(40, DomeGeneratorShape.podDatum(full[1], y0));
        }
        for (int f : DomeGeneratorShape.datums()) assertTrue(DomeGeneratorShape.isDatum(f));
        for (int f : new int[] { 0, 27, 29, 39, 41, -40 }) assertFalse(DomeGeneratorShape.isDatum(f));
    }
}
