package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.BuildPlan.Plan;
import com.fluxecho.logic.BuildPlan.Step;

/**
 * The Tidal Well's job ({@link BuildPlan#root}, CITY.md §4.9), sunk as a caisson: its HARD steps are exactly the cells
 * the structure check looks at for every front; SINK goes layer by layer from the lid down, each layer's shell a band
 * before the band that digs its inside, so nothing is dug before the ring over it stands; DRAIN goes over the inside
 * again; FLOOR, FIT and COMMISSION fill it in; nothing reaches the forum; and the bands, the repeated cells and what a
 * dug cell keeps are what the builder reads.
 */
class RootPlanTest {

    private static final int[][] FACINGS = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
    private static final int CX = 100, Y0 = 64, CZ = -200;
    /** Cells inside the shell: 24 layers of the octagon's 329 inner columns. */
    private static final int INSIDE = 24 * 329;

    private static Plan plan(int[] f) {
        return RootShape.plan(new CampusPlan(Mix.seed(CX, Y0 + 2, CZ)), SiteFamily.ROOT_SITE, CX, Y0, CZ, f[0], f[1]);
    }

    private static long pos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    @Test
    void theHardStepsAreTheWellsCells() {
        Blueprint b = RootShape.BLUEPRINT;
        for (int[] f : FACINGS) {
            Plan p = plan(f);
            int[] c = RootShape.controller(SiteFamily.ROOT_SITE, CX, Y0, CZ, f[0], f[1]);
            int[] front = BuildPlan.front(c[3]);
            Set<Long> wantHard = new HashSet<>(), wantAir = new HashSet<>();
            for (Blueprint.Cell cell : b.cells()) {
                int[] w = b.world(cell.a, cell.b, cell.c, c[0], c[1], c[2], front[0], front[1]);
                (cell.ch == '-' ? wantAir : wantHard).add(pos(w[0], w[1], w[2]));
            }
            Set<Long> hard = new HashSet<>(), air = new HashSet<>(), dig = new HashSet<>(), drain = new HashSet<>();
            int soft = 0;
            for (Step s : p.steps) {
                assertTrue(s.y < Y0, "nothing reaches the forum: " + s);
                assertTrue(s.y >= Y0 - 26);
                long k = pos(s.x, s.y, s.z);
                switch (s.kind) {
                    case BuildPlan.HARD:
                        assertTrue(hard.add(k), "a cell placed twice");
                        break;
                    case BuildPlan.AIR:
                        assertTrue(air.add(k));
                        break;
                    case BuildPlan.DIG:
                        assertTrue(dig.add(k));
                        assertEquals(Parts.AIR, s.part);
                        break;
                    case BuildPlan.DRAIN:
                        assertTrue(drain.add(k));
                        assertEquals(Parts.AIR, s.part);
                        break;
                    default:
                        assertEquals(BuildPlan.SOFT, s.kind);
                        soft++;
                }
            }
            assertEquals(wantHard, hard, "front " + f[0] + "," + f[1]);
            assertEquals(wantAir, air, "the lid's open centre");
            assertEquals(3268, hard.size());
            assertEquals(1, air.size());
            assertEquals(238, soft);
            assertEquals(INSIDE, dig.size());
            assertEquals(dig, drain, "it drains what it dug");
            assertEquals(3268 + 238 + 1 + 2 * INSIDE, p.steps.size());
            assertTrue(p.clear.isEmpty(), "the caisson digs its inside, no clear columns");
            assertEquals(RootShape.R_STAGES, p.stages);
            assertEquals(BuildPlan.moduleKey(RootShape.KEY, SiteFamily.ROOT_SITE), p.key);
            // the lid's open centre is right under the dais centre, over the island's pad
            assertTrue(air.contains(pos(CX, Y0 - 1, CZ)));
            // the controller last, as its module core, where the spec puts it
            Step last = p.steps.get(p.steps.size() - 1);
            assertEquals(Parts.moduleCore(ModuleCores.ROOT), last.part);
            assertEquals(RootShape.R_COMMISSION, last.stage);
            assertTrue(last.at(c[0], c[1], c[2]));
            assertEquals(1, p.count(RootShape.R_COMMISSION));
        }
    }

    @Test
    void theStages() {
        Plan p = plan(FACINGS[0]);
        assertEquals(417 * 2 + 88 * 24 + INSIDE, p.count(RootShape.R_SINK), "the shell and the inside dug out");
        assertEquals(INSIDE, p.count(RootShape.R_DRAIN));
        assertEquals(237, p.count(RootShape.R_FLOOR), "island, pad, bridges, rim, walkway");
        assertEquals(1 + 76 + 154 + 8 + 84, p.count(RootShape.R_FIT), "console, rings, ramp, taps, water");
        for (int i = p.start(RootShape.R_FLOOR); i < p.end(RootShape.R_FLOOR); i++) {
            Step s = p.steps.get(i);
            assertEquals(Y0 - 25, s.y, "the floor level");
            assertEquals(BuildPlan.HARD, s.kind);
        }
        assertEquals(Parts.fitting(Parts.F_LIFT), p.steps.get(p.start(RootShape.R_FLOOR)).part, "the pad first");
        // FIT: the console, the rings, the ramp, then the taps (a group each, in unit order), then the water
        int i = p.start(RootShape.R_FIT);
        assertEquals(Parts.frame(Parts.FR_CONSOLE), p.steps.get(i++).part);
        for (int k = 0; k < 76; k++) assertEquals(Parts.frame(Parts.FR_RING), p.steps.get(i++).part);
        for (int k = 0; k < 154; k++) {
            Step s = p.steps.get(i++);
            assertEquals(BuildPlan.SOFT, s.kind, "the helix ramp is soft");
            assertEquals(BuildPlan.NO_GROUP, s.group);
        }
        for (int g = 0; g < 8; g++) {
            Step s = p.steps.get(i++);
            assertEquals(g, s.group);
            assertEquals(Parts.fitting(Parts.F_SOCKET), s.part);
            assertEquals(Y0 - 25, s.y);
        }
        for (int k = 0; k < 84; k++) {
            Step s = p.steps.get(i++);
            assertEquals(Parts.WATER, s.part, "the pool's water after its rim and taps");
            assertEquals(BuildPlan.SOFT, s.kind);
        }
        assertEquals(p.end(RootShape.R_FIT), i);
        // the stage keys as the build page reads them
        assertEquals(RootShape.R_STAGES - 1, RootShape.R_COMMISSION);
    }

    /**
     * The caisson: SINK is the lid, then per layer from the top its wall ring and then its inside dug out, then the
     * slab; a band each, so the builder digs no layer before the ring over it stands.
     */
    @Test
    void theCaissonSinksLayerByLayerFromTheLid() {
        for (int[] f : FACINGS) {
            Plan p = plan(f);
            int start = p.start(RootShape.R_SINK), end = p.end(RootShape.R_SINK);
            assertEquals(49, p.bandCount(), "the lid, two bands for each of 24 layers, the slab");
            List<int[]> bands = new ArrayList<>();
            for (int i = start; i < end; i = p.bandEnd(i)) bands.add(new int[] { i, Math.min(end, p.bandEnd(i)) });
            assertEquals(50, bands.size());
            // the lid first: the forum and the nexus never stand over an open hole
            int[] lid = bands.get(0);
            assertEquals(417, lid[1] - lid[0]);
            for (int i = lid[0]; i < lid[1]; i++) assertEquals(Y0 - 1, p.steps.get(i).y);
            for (int layer = 0; layer < 24; layer++) {
                int y = Y0 - 2 - layer;
                int[] ring = bands.get(1 + 2 * layer), dig = bands.get(2 + 2 * layer);
                assertEquals(88, ring[1] - ring[0], "the wall ring of y" + y);
                assertEquals(329, dig[1] - dig[0], "the inside of y" + y);
                for (int i = ring[0]; i < ring[1]; i++) {
                    Step s = p.steps.get(i);
                    assertEquals(y, s.y);
                    assertEquals(BuildPlan.HARD, s.kind);
                }
                for (int i = dig[0]; i < dig[1]; i++) {
                    Step s = p.steps.get(i);
                    assertEquals(y, s.y);
                    assertEquals(BuildPlan.DIG, s.kind);
                }
            }
            int[] slab = bands.get(49);
            assertEquals(417, slab[1] - slab[0]);
            for (int i = slab[0]; i < slab[1]; i++) assertEquals(Y0 - 26, p.steps.get(i).y);
            // DRAIN: the inside again, from the top down
            int last = Integer.MAX_VALUE;
            for (int i = p.start(RootShape.R_DRAIN); i < p.end(RootShape.R_DRAIN); i++) {
                Step s = p.steps.get(i);
                assertEquals(BuildPlan.DRAIN, s.kind);
                assertTrue(s.y <= last);
                last = s.y;
            }
            // past SINK no band begins
            assertEquals(p.steps.size(), p.bandEnd(p.start(RootShape.R_DRAIN)));
        }
    }

    /**
     * The dug cells a later step fills: the survey looks at a cell's first step only, and a dig keeps what a later step
     * places there when it stands already (a well raised again, a repair).
     */
    @Test
    void aDugCellKnowsWhatFillsItLater() {
        Plan p = plan(FACINGS[0]);
        Map<Long, Integer> first = new HashMap<>();
        int repeats = 0;
        for (int i = 0; i < p.steps.size(); i++) {
            Step s = p.steps.get(i);
            long k = pos(s.x, s.y, s.z);
            boolean seen = first.containsKey(k);
            assertEquals(seen, p.repeat(i), "step " + i);
            if (!seen) first.put(k, i);
            else repeats++;
        }
        // every inside cell is dug, then drained; 561 of them are filled too (floor 237, fittings 323, the core)
        assertEquals(INSIDE + 237 + 323 + 1, repeats);
        int filled = 0;
        for (int i = p.start(RootShape.R_SINK); i < p.end(RootShape.R_SINK); i++) {
            Step s = p.steps.get(i);
            if (s.kind != BuildPlan.DIG) {
                assertEquals(-1, p.placedAfter(i), "the shell is placed once");
                continue;
            }
            int after = p.placedAfter(i);
            if (after >= 0) filled++;
        }
        assertEquals(237 + 323 + 1, filled);
        // the island's pad: dug, drained, then the pad; the controller's cell: dug, drained, then the core
        int pad = first.get(pos(CX, Y0 - 25, CZ));
        assertEquals(BuildPlan.DIG, p.steps.get(pad).kind);
        assertEquals(Parts.fitting(Parts.F_LIFT), p.placedAfter(pad));
        int[] c = RootShape.controller(SiteFamily.ROOT_SITE, CX, Y0, CZ, 0, -1);
        int core = first.get(pos(c[0], c[1], c[2]));
        assertEquals(Parts.moduleCore(ModuleCores.ROOT), p.placedAfter(core));
        // a cell of the shaft's air: dug, drained, nothing more
        int shaft = first.get(pos(CX, Y0 - 10, CZ));
        assertEquals(-1, p.placedAfter(shaft));
        assertFalse(p.repeat(shaft));
    }

    @Test
    void bandsBeginInsideTheirStage() {
        List<List<Step>> two = new ArrayList<>();
        for (int s = 0; s < 2; s++) {
            List<Step> l = new ArrayList<>();
            for (int k = 0; k < 5; k++) l.add(new Step(k, s, 0, 1, s, BuildPlan.NO_GROUP, BuildPlan.HARD));
            two.add(l);
        }
        Plan p = new Plan(
            "t",
            2,
            two,
            Collections.<BuildPlan.Column>emptyList(),
            Arrays.asList(Arrays.asList(2, 4), null));
        assertEquals(2, p.bandEnd(0));
        assertEquals(2, p.bandEnd(1));
        assertEquals(4, p.bandEnd(2));
        assertEquals(10, p.bandEnd(4), "the last band runs to the end of the steps");
        assertEquals(10, p.bandEnd(7));
        Plan plain = new Plan("t", 2, two, Collections.<BuildPlan.Column>emptyList());
        assertEquals(0, plain.bandCount());
        assertEquals(10, plain.bandEnd(0), "a plan without bands is one band");
        assertThrows(
            IllegalArgumentException.class,
            () -> new Plan(
                "t",
                2,
                two,
                Collections.<BuildPlan.Column>emptyList(),
                Arrays.asList(Arrays.asList(0), null)));
        assertThrows(
            IllegalArgumentException.class,
            () -> new Plan(
                "t",
                2,
                two,
                Collections.<BuildPlan.Column>emptyList(),
                Arrays.asList(Arrays.asList(5), null)));
    }

    @Test
    void onlyTheRootSite() {
        assertThrows(IllegalArgumentException.class, () -> BuildPlan.root(SiteFamily.ZENITH_SITE, CX, Y0, CZ, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> BuildPlan.root(SiteFamily.ROOT_SITE, CX, Y0, CZ, 1, 1));
        assertTrue(BuildPlan.digs(BuildPlan.DIG) && BuildPlan.digs(BuildPlan.DRAIN));
        assertFalse(BuildPlan.digs(BuildPlan.AIR));
        assertTrue(BuildPlan.empties(BuildPlan.AIR) && BuildPlan.empties(BuildPlan.DIG));
        assertFalse(BuildPlan.empties(BuildPlan.SOFT) || BuildPlan.empties(BuildPlan.HARD));
        // the other plans keep one step a cell
        Set<Long> cells = new HashSet<>();
        Plan zenith = DomeGeneratorShape
            .plan(new CampusPlan(Mix.seed(CX, Y0 + 2, CZ)), SiteFamily.ZENITH_SITE, CX, Y0, CZ, 0, -1);
        for (int i = 0; i < zenith.steps.size(); i++) {
            Step s = zenith.steps.get(i);
            assertTrue(cells.add(pos(s.x, s.y, s.z)));
            assertFalse(zenith.repeat(i));
        }
        assertEquals(0, zenith.bandCount());
    }

    /**
     * Over low ground (0.14.1) the job sinks the well of the campus's depth: its HARD steps are that well's cells, its
     * slab at Y0 - D over the bedrock, its lid at Y0 - 1, layer by layer in two bands each as the full well, digging
     * and draining exactly its inside.
     */
    @Test
    void overLowGroundTheHardStepsAreTheDepthsWell() {
        for (int y0 : new int[] { 14, 19, 20, 25, 26, 31 }) {
            RootShape.Well w = RootShape.forBase(y0);
            int d = w.depth;
            Plan p = RootShape.plan(new CampusPlan(Mix.seed(CX, y0 + 2, CZ)), SiteFamily.ROOT_SITE, CX, y0, CZ, 0, -1);
            int[] c = RootShape.controller(SiteFamily.ROOT_SITE, CX, y0, CZ, 0, -1);
            assertEquals(y0 - d + 3, c[1]);
            int[] front = BuildPlan.front(c[3]);
            Set<Long> wantHard = new HashSet<>();
            for (Blueprint.Cell cell : w.blueprint.cells()) {
                if (cell.ch == '-') continue;
                int[] at = w.blueprint.world(cell.a, cell.b, cell.c, c[0], c[1], c[2], front[0], front[1]);
                wantHard.add(pos(at[0], at[1], at[2]));
            }
            Set<Long> hard = new HashSet<>();
            int dig = 0, drain = 0;
            for (Step s : p.steps) {
                assertTrue(s.y >= y0 - d && s.y < y0, "between the slab and the lid at " + y0);
                assertTrue(s.y >= RootShape.LOWEST_SLAB, "over the bedrock at " + y0);
                if (s.kind == BuildPlan.HARD) hard.add(pos(s.x, s.y, s.z));
                if (s.kind == BuildPlan.DIG) dig++;
                if (s.kind == BuildPlan.DRAIN) drain++;
            }
            assertEquals(wantHard, hard, "Y0 " + y0);
            assertEquals((d - 2) * 329, dig, "the inside dug");
            assertEquals((d - 2) * 329, drain, "and drained");
            assertEquals(2 * d - 3, p.bandCount(), "the lid, two bands for each of D - 2 layers, the slab");
            assertTrue(
                p.steps.get(p.steps.size() - 1)
                    .at(c[0], c[1], c[2]),
                "the controller last");
            assertTrue(p.clear.isEmpty());
        }
    }
}
