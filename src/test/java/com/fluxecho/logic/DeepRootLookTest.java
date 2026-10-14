package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The deep root's drawing (0.16.0, CITY.md §4.9 Visuals; d016_final §5): in a chamber the light flows down each
 * dissolved conduit and out along the floor's vein to the bay on its line, the well's floor grate glowing from below;
 * in-well the strands hang at radius 6.0 from the axis, at most 0.3 thick, clear of the outer tide ring, and a root
 * collar closes the gap under each bay plinth over the basin rim; a chamber keeps its buried plinth line dark; the pool
 * glows a strip of cells at a time; and every form, with the well's drawing under it, keeps CITY.md §1.3's budgets.
 */
class DeepRootLookTest {

    private static final int[] BUDGET = { 2000, 300, 64 };

    @Test
    void theInWellStrandsHangAtRadiusSixClearOfTheRings() {
        double c = RootShape.MID + 0.5;
        assertTrue(RootLook.STRAND_HALO <= 0.3 && RootLook.STRAND_CORE < RootLook.STRAND_HALO);
        double outerRing = RootShape.RING_R[RootShape.RING_R.length - 1] + 1;
        for (RootShape.Deep d : RootShape.deeps()) {
            List<double[]> strands = RootLook.strands(d);
            assertEquals(8, strands.size(), d.name);
            for (double[] s : strands) {
                assertTrue(s[2] > s[3], d.name + ": from its top down");
                if (d.h > 0) {
                    // in a chamber: from its ceiling (the well's floor slab) down the conduit's column
                    assertEquals(d.h, s[2], 0, d.name);
                    assertEquals(RootShape.CONDUIT, d.cell((int) Math.floor(s[0]), (int) s[3], (int) Math.floor(s[1])));
                    continue;
                }
                double r = Math.hypot(s[0] - c, s[1] - c);
                assertEquals(RootShape.INWELL_CONDUIT_R, r, 1e-9, d.name);
                assertTrue(r - RootLook.STRAND_HALO / 2 - outerRing >= 0.35 - 1e-9, d.name + ": clear of the rings");
                assertEquals(d.well.lidY, s[2], 0, d.name + ": from the lid");
            }
        }
    }

    /** In a chamber each way runs down its conduit, to the floor and out along the vein to the bay on its line. */
    @Test
    void theFlowsEndAtTheBays() {
        for (RootShape.Deep d : RootShape.deeps()) {
            List<double[][]> flows = RootLook.flows(d);
            assertEquals(8, flows.size());
            Set<String> bays = new HashSet<>();
            for (UnitLayout.Unit u : d.units.units()) if ("bay".equals(u.kind)) {
                int[] p = u.cells.get(0);
                bays.add((p[0] + 0.5) + "," + (p[2] + 0.5));
            }
            assertEquals(8, bays.size(), d.name);
            Set<String> reached = new HashSet<>();
            for (double[][] way : flows) {
                assertTrue(RootLook.length(way) > 0);
                for (int i = 1; i < way.length; i++) assertTrue(way[i][1] <= way[i - 1][1] + 0.05, "down, then level");
                if (d.h == 0) {
                    assertEquals(2, way.length, d.name);
                    continue;
                }
                double[] end = way[way.length - 1];
                assertEquals(RootLook.VEIN_Y, end[1], 0);
                reached.add(end[0] + "," + end[2]);
            }
            if (d.h > 0) assertEquals(bays, reached, d.name + ": every bay, once");
            // the heads run along their ways, one after another, by the world time alone
            double len = RootLook.length(flows.get(0));
            for (double t = 0; t < 500; t += 17) {
                double h = RootLook.flowHead(0, 8, t, len);
                assertTrue(h >= -2 - 1e-9 && h <= len + 2 + 1e-9);
                assertEquals(h, RootLook.flowHead(0, 8, t + RootLook.FLOW_T, len), 1e-6);
            }
        }
    }

    @Test
    void theGrateGlowsFromBelowAndTheCollarsCloseTheGaps() {
        for (RootShape.Deep d : RootShape.deeps()) {
            List<int[]> grate = RootLook.grateFrom(d), collars = RootLook.collars(d);
            if (d.h > 0) {
                assertEquals(24, grate.size(), d.name);
                for (int[] g : grate) assertEquals(':', d.cell(g[0], d.h, g[1]));
                assertTrue(collars.isEmpty());
                assertTrue(RootLook.courseBuried(d), d.name + ": its plinth line runs deep in the ground");
            } else {
                assertTrue(grate.isEmpty());
                assertEquals(4, collars.size(), d.name);
                for (int[] k : collars) {
                    assertEquals('u', d.cell(k[0], k[2] - 1, k[1]), "a basin rim under the plinth");
                    double from = RootLook.collarFrom(k[2]);
                    assertTrue(from > k[2] - 1 && from < k[2], "the gap over the rim's top");
                }
                assertFalse(RootLook.courseBuried(d));
                assertEquals(
                    0,
                    ModuleLook.course(d.build)
                        .size(),
                    d.name + ": no plinth line in-well");
            }
        }
        assertFalse(RootLook.courseBuried(null));
    }

    /** The pool's strips cover its water cells exactly, each once, in far fewer quads. */
    @Test
    void thePoolGlowsAStripOfCellsAtATime() {
        for (int depth : new int[] { 26, 20, 14, 8 }) {
            List<double[]> water = ModuleLook.water(RootShape.well(depth).build, RootShape.FLOOR_Y, 0.5);
            List<double[]> strips = RootLook.poolStrips(water);
            Set<String> cells = new HashSet<>(), covered = new HashSet<>();
            for (double[] w : water) cells.add((int) Math.floor(w[0]) + "," + (int) Math.floor(w[1]));
            for (double[] s : strips) {
                assertEquals(1, s[3] - s[1], 0, "a strip a row");
                for (double x = s[0]; x < s[2]; x++)
                    assertTrue(covered.add((int) x + "," + (int) s[1]), "each cell once");
            }
            assertEquals(cells, covered);
            assertTrue(strips.size() * 3 < water.size(), strips.size() + " strips for " + water.size() + " cells");
        }
    }

    /**
     * Every deep root form, with the drawing every city module shares (the chamber forms' plinth line dark), the
     * well's drawing under it and its own, stays within 2,000 near, 300 mid and 64 far; so does every plain well.
     */
    @Test
    void everyFormKeepsItsBudget() {
        for (RootShape.Deep d : RootShape.deeps()) for (int tier = ModuleLook.NEAR; tier <= ModuleLook.FAR; tier++) {
            int base = ModuleLook.baseQuads(d.build, d.units, tier, !RootLook.courseBuried(d)),
                well = RootLook.wellQuads(d.well, tier), deep = RootLook.deepQuads(d, tier);
            assertTrue(
                base + well + deep <= BUDGET[tier],
                d.name + " tier " + tier + ": " + base + " + " + well + " + " + deep);
        }
        for (int depth : new int[] { 26, 20, 14, 8 }) {
            RootShape.Well w = RootShape.well(depth);
            for (int tier = ModuleLook.NEAR; tier <= ModuleLook.FAR; tier++) {
                int base = ModuleLook.baseQuads(w.build, RootShape.UNITS, tier), well = RootLook.wellQuads(w, tier);
                assertTrue(base + well <= BUDGET[tier], "well " + depth + " tier " + tier + ": " + base + " + " + well);
            }
        }
        // the course flag only drops the plinth line
        RootShape.Deep full = RootShape.deeps()
            .get(0);
        int faces = ModuleLook.course(full.build)
            .size();
        assertTrue(faces > 0);
        assertEquals(
            ModuleLook.baseQuads(full.build, full.units, ModuleLook.MID) - faces,
            ModuleLook.baseQuads(full.build, full.units, ModuleLook.MID, false));
        assertEquals(
            ModuleLook.baseQuads(full.build, full.units, ModuleLook.NEAR) - 3 * faces,
            ModuleLook.baseQuads(full.build, full.units, ModuleLook.NEAR, false));
        assertEquals(0, RootLook.deepQuads(full, ModuleLook.FAR));
        assertEquals(RootLook.FAR_RING_PIECES * 3, RootLook.wellQuads(full.well, ModuleLook.FAR));
    }

    /**
     * The forum's grate the well's look reads from the surface (0.16.0) is found in the frame of the shape the root
     * stands as: the well's layer lifted by the form's base ({@code TileRoot.wellWorldOf}), the same world block the
     * well's own frame gives, for every form and every grate cell. Without the lift a chamber root looked 13 or 7
     * layers too low and never found its grate (no glow, no tide-crest blades).
     */
    @Test
    void theGrateIsFoundInTheFormsFrame() {
        for (RootShape.Deep d : RootShape.deeps()) {
            int surface = RootLook.surfaceY(d.well), layer = surface + RootShape.base(d.name);
            for (int[] g : RootLook.grate()) {
                int[] deep = d.build.cellOf(g[0], layer, g[1]), well = d.well.build.cellOf(g[0], surface, g[1]);
                assertArrayEquals(
                    d.well.build.blueprint()
                        .world(well[0], well[1], well[2], 0, 0, 0, 0, -1),
                    d.build.blueprint()
                        .world(deep[0], deep[1], deep[2], 0, 0, 0, 0, -1),
                    d.name + " at " + g[0] + "," + g[1]);
                if (d.h > 0) {
                    int[] low = d.build.cellOf(g[0], surface, g[1]);
                    assertFalse(
                        java.util.Arrays.equals(
                            d.well.build.blueprint()
                                .world(well[0], well[1], well[2], 0, 0, 0, 0, -1),
                            d.build.blueprint()
                                .world(low[0], low[1], low[2], 0, 0, 0, 0, -1)),
                        d.name + ": the well's layer alone is the chamber's height too low");
                }
            }
        }
        assertEquals(39, RootLook.surfaceY(RootShape.deep(RootShape.DEEP).well) + RootShape.base(RootShape.DEEP));
        assertEquals(33, RootLook.surfaceY(RootShape.deep(RootShape.ROOT_7).well) + RootShape.base(RootShape.ROOT_7));
        assertEquals(26, RootLook.surfaceY(RootShape.deep(RootShape.ROOT_IN).well) + RootShape.base(RootShape.ROOT_IN));
    }
}
