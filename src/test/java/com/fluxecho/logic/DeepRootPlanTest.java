package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
 * The deep root's jobs (0.16.0, CITY.md §4.9, the design's "Root-by-room" §6): the deepen job over a formed well
 * ({@link BuildPlan#deepen}) builds only the new part, a chamber sunk as a caisson under the intact well in bands from
 * the top down (the ring round the well's floor slab first), drained, its conduits and bays fitted, and last the cells
 * it changes in the well's floor slab; an in-well form only fits; no step but those last ones is at a cell the well's
 * structure checks, and its HARD and AIR steps are exactly the cells the deep root adds or changes. The whole plan of
 * a form ({@link BuildPlan#deep}), a repair's, is its caisson from the lid down, its HARD steps exactly the form's
 * checked cells.
 */
class DeepRootPlanTest {

    private static final int[][] FACINGS = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
    private static final int CX = 300, CZ = 140;

    /** A base layer each form is the rule's at: {Y0, form index in {@link RootShape#deeps}}. */
    private static final int[][] AT = { { 70, 0 }, { 41, 1 }, { 35, 2 }, { 28, 3 }, { 22, 4 }, { 16, 5 } };

    private static long pos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static RootShape.Deep form(int i) {
        return RootShape.deeps()
            .get(i);
    }

    /** The world cell of a point of the form's frame, its controller placed for the nexus's front. */
    private static int[] world(RootShape.Deep d, int[] ctrl, int x, int y, int z) {
        int[] c = d.build.cellOf(x, y, z), f = BuildPlan.front(ctrl[3]);
        return d.blueprint.world(c[0], c[1], c[2], ctrl[0], ctrl[1], ctrl[2], f[0], f[1]);
    }

    @Test
    void theRuleGivesEachBaseItsForm() {
        for (int[] a : AT)
            assertEquals(form(a[1]).name, RootShape.deepFor(a[0], RootShape.forBase(a[0])).name, "Y0 " + a[0]);
    }

    /** The deepen job's counts (the design's §6 table) and its bands, for every front. */
    @Test
    void theDeepenJobsStagesAndBands() {
        // {SINK, its bands, DRAIN, FIT, conduits, total}
        int[][] want = { { 6317, 26, 4620, 89, 48, 11027 }, { 3431, 14, 2310, 65, 24, 5807 }, { 0, 0, 0, 112, 96, 113 },
            { 0, 0, 0, 88, 72, 89 }, { 0, 0, 0, 64, 48, 65 }, { 0, 0, 0, 32, 16, 33 } };
        for (int[] a : AT) for (int[] f : FACINGS) {
            int y0 = a[0];
            RootShape.Deep d = form(a[1]);
            int[] w = want[a[1]];
            String what = d.name + " at Y0 " + y0 + " front " + f[0] + "," + f[1];
            Plan p = BuildPlan.deepen(SiteFamily.ROOT_SITE, CX, y0, CZ, f[0], f[1], d);
            assertEquals(BuildPlan.moduleKey(RootShape.KEY, SiteFamily.ROOT_SITE), p.key);
            assertEquals(RootShape.R_STAGES, p.stages);
            assertTrue(p.clear.isEmpty(), "no clear columns");
            assertEquals(w[0], p.count(RootShape.R_SINK), "SINK " + what);
            assertEquals(w[2], p.count(RootShape.R_DRAIN), "DRAIN " + what);
            assertEquals(0, p.count(RootShape.R_FLOOR), "the well's floor stands " + what);
            assertEquals(w[3], p.count(RootShape.R_FIT), "FIT " + what);
            assertEquals(1, p.count(RootShape.R_COMMISSION), "the controller, already there");
            assertEquals(w[5], p.steps.size(), what);
            assertEquals(w[1] == 0 ? 0 : w[1] - 1, p.bandCount(), "bands " + what);
            int base = y0 - d.well.depth - d.h;
            if (d.h > 0) {
                // the ring round the well's floor slab, then each layer's wall ring and its inside, then the slab
                List<int[]> bands = new ArrayList<>();
                int start = p.start(RootShape.R_SINK), end = p.end(RootShape.R_SINK);
                for (int i = start; i < end; i = p.bandEnd(i)) bands.add(new int[] { i, Math.min(end, p.bandEnd(i)) });
                assertEquals(w[1], bands.size());
                int[] ring = bands.get(0);
                assertEquals(64, ring[1] - ring[0], "the ring " + what);
                for (int i = ring[0]; i < ring[1]; i++) {
                    Step s = p.steps.get(i);
                    assertEquals(y0 - 26, s.y, "the ring at the well's floor slab");
                    assertEquals(Parts.frame(Parts.FR_BASE), s.part);
                }
                for (int layer = 0; layer < d.h - 1; layer++) {
                    int y = y0 - 27 - layer;
                    int[] wall = bands.get(1 + 2 * layer), dig = bands.get(2 + 2 * layer);
                    assertEquals(96, wall[1] - wall[0], "the wall of y" + y);
                    assertEquals(385, dig[1] - dig[0], "the inside of y" + y);
                    for (int i = wall[0]; i < wall[1]; i++) {
                        assertEquals(y, p.steps.get(i).y);
                        assertEquals(BuildPlan.HARD, p.steps.get(i).kind);
                    }
                    for (int i = dig[0]; i < dig[1]; i++) {
                        assertEquals(y, p.steps.get(i).y);
                        assertEquals(BuildPlan.DIG, p.steps.get(i).kind);
                    }
                }
                int[] slab = bands.get(w[1] - 1);
                assertEquals(481, slab[1] - slab[0], "the chamber's floor slab " + what);
                for (int i = slab[0]; i < slab[1]; i++) assertEquals(base, p.steps.get(i).y);
                int last = Integer.MAX_VALUE;
                for (int i = p.start(RootShape.R_DRAIN); i < p.end(RootShape.R_DRAIN); i++) {
                    Step s = p.steps.get(i);
                    assertEquals(BuildPlan.DRAIN, s.kind);
                    assertTrue(s.y <= last, "drained from the top down");
                    last = s.y;
                }
            }
            // FIT: the conduits from the bottom up, the bays a group each (units 8..15), the floor slab's cells last
            int i = p.start(RootShape.R_FIT), lastY = Integer.MIN_VALUE;
            for (int k = 0; k < w[4]; k++, i++) {
                Step s = p.steps.get(i);
                assertEquals(Parts.frame(Parts.FR_CONDUIT), s.part, what);
                assertEquals(BuildPlan.NO_GROUP, s.group);
                assertTrue(s.y >= lastY);
                lastY = s.y;
            }
            for (int g = 8; g < 16; g++) for (int k = 0; k < 2; k++, i++) {
                Step s = p.steps.get(i);
                assertEquals(g, s.group, what);
                assertEquals(BuildPlan.HARD, s.kind);
                assertEquals(
                    k == 0 ? ShapeKit.partOf('p') : ShapeKit.partOf('o'),
                    s.part,
                    "the plinth, then its socket");
            }
            int grate = 0, air = 0;
            for (; i < p.end(RootShape.R_FIT); i++) {
                Step s = p.steps.get(i);
                assertEquals(y0 - 26, s.y, "in the well's floor slab");
                if (s.kind == BuildPlan.AIR) {
                    air++;
                    assertArrayEquals(new int[] { CX, y0 - 26, CZ }, new int[] { s.x, s.y, s.z }, "its open centre");
                } else {
                    grate++;
                    assertEquals(ShapeKit.partOf(':'), s.part);
                }
            }
            assertEquals(d.h > 0 ? 24 : 0, grate, what);
            assertEquals(d.h > 0 ? 1 : 0, air, what);
            int[] c = d.controller(SiteFamily.ROOT_SITE, CX, y0, CZ, f[0], f[1]);
            Step core = p.steps.get(p.start(RootShape.R_COMMISSION));
            assertTrue(core.at(c[0], c[1], c[2]));
            assertEquals(Parts.moduleCore(ModuleCores.ROOT), core.part);
            assertEquals(y0 - d.well.depth + 3, c[1], "the well's controller");
        }
    }

    /**
     * No step of the deepen job is at a cell the well's structure checks but the last ones, which change its floor
     * slab (and the controller, already there): the well stays formed and docked while its root grows, and the chamber
     * forms right after. Its HARD and AIR steps are exactly the cells the form adds and changes; its digs exactly the
     * chamber's inside.
     */
    @Test
    void theDeepenJobBuildsOnlyItsNewPart() {
        for (int[] a : AT) for (int[] f : FACINGS) {
            int y0 = a[0];
            RootShape.Deep d = form(a[1]);
            String what = d.name + " at Y0 " + y0 + " front " + f[0] + "," + f[1];
            Plan p = BuildPlan.deepen(SiteFamily.ROOT_SITE, CX, y0, CZ, f[0], f[1], d);
            int[] ctrl = d.controller(SiteFamily.ROOT_SITE, CX, y0, CZ, f[0], f[1]);
            Set<Long> wellChecked = new HashSet<>(), added = new HashSet<>(), changed = new HashSet<>(),
                inside = new HashSet<>();
            for (int y = 0; y < d.shape.H; y++) for (int z = 0; z < 25; z++) for (int x = 0; x < 25; x++) {
                int[] at = world(d, ctrl, x, y, z);
                long k = pos(at[0], at[1], at[2]);
                char ch = d.cell(x, y, z), w = d.wellCell(x, y, z);
                if (w != ShapeKit.NONE && !ShapeKit.soft(w)) wellChecked.add(k);
                if (d.chamberInside(x, y, z)) inside.add(k);
                if (ch == w || ch == ShapeKit.NONE) continue;
                (w == ShapeKit.NONE ? added : changed).add(k);
            }
            assertEquals(d.h > 0 ? 25 : 0, changed.size(), what);
            int firstOpen = p.end(RootShape.R_FIT) - changed.size();
            Set<Long> hardAir = new HashSet<>(), dug = new HashSet<>();
            for (int i = 0; i < p.steps.size(); i++) {
                Step s = p.steps.get(i);
                long k = pos(s.x, s.y, s.z);
                boolean core = s.stage == RootShape.R_COMMISSION;
                if (wellChecked.contains(k) && !core)
                    assertTrue(i >= firstOpen && i < p.end(RootShape.R_FIT), what + ": a well cell at step " + i);
                if (i >= firstOpen && i < p.end(RootShape.R_FIT)) assertTrue(changed.contains(k), what);
                if (s.kind == BuildPlan.DIG) assertTrue(dug.add(k));
                if (!core && (s.kind == BuildPlan.HARD || s.kind == BuildPlan.AIR)) assertTrue(hardAir.add(k), what);
                if (core) assertTrue(wellChecked.contains(k), "the well's controller");
            }
            Set<Long> want = new HashSet<>(added);
            want.addAll(changed);
            assertEquals(want, hardAir, what + ": its HARD and AIR steps are the cells it adds and changes");
            assertEquals(inside, dug, what + ": it digs the chamber's inside");
            assertEquals(new int[] { 1761, 1161, 112, 88, 64, 32 }[a[1]], added.size(), what);
            for (Step s : p.steps) assertTrue(s.y >= RootShape.LOWEST_SLAB && s.y < y0, what + ": in the band");
        }
    }

    /**
     * The whole plan of each form (a repair's): its caisson from the lid down to its lowest floor slab, every checked
     * cell of the form a HARD step where its blueprint puts it, its inside dug and drained (the design's §6: the
     * shape's
     * steps and twice the inside).
     */
    @Test
    void theWholePlanOfEachForm() {
        int[] total = { 30300, 25080, 19411, 14910, 10375, 5783 };
        for (int[] a : AT) for (int[] f : FACINGS) {
            int y0 = a[0];
            RootShape.Deep d = form(a[1]);
            String what = d.name + " at Y0 " + y0 + " front " + f[0] + "," + f[1];
            Plan p = BuildPlan.deep(SiteFamily.ROOT_SITE, CX, y0, CZ, f[0], f[1], d);
            assertEquals(total[a[1]], p.steps.size(), what);
            int[] ctrl = d.controller(SiteFamily.ROOT_SITE, CX, y0, CZ, f[0], f[1]);
            int[] front = BuildPlan.front(ctrl[3]);
            Set<Long> wantHard = new HashSet<>(), hard = new HashSet<>();
            for (Blueprint.Cell cell : d.blueprint.cells()) {
                if (cell.ch == '-') continue;
                int[] at = d.blueprint.world(cell.a, cell.b, cell.c, ctrl[0], ctrl[1], ctrl[2], front[0], front[1]);
                wantHard.add(pos(at[0], at[1], at[2]));
            }
            int dig = 0, drain = 0, air = 0, soft = 0;
            for (Step s : p.steps) {
                if (s.kind == BuildPlan.HARD) assertTrue(hard.add(pos(s.x, s.y, s.z)), what);
                else if (s.kind == BuildPlan.DIG) dig++;
                else if (s.kind == BuildPlan.DRAIN) drain++;
                else if (s.kind == BuildPlan.AIR) air++;
                else soft++;
            }
            assertEquals(wantHard, hard, what);
            assertEquals(d.shape.softCount(), soft);
            assertEquals(d.shape.airCount(), air);
            int inside = 329 * (d.well.depth - 2) + 385 * Math.max(0, d.h - 1);
            assertEquals(inside, dig, what);
            assertEquals(inside, drain, what);
            assertEquals(d.h > 0 ? 2 * d.well.depth - 3 + 1 + 2 * (d.h - 1) + 0 : 2 * d.well.depth - 3, p.bandCount());
            Step last = p.steps.get(p.steps.size() - 1);
            assertTrue(last.at(ctrl[0], ctrl[1], ctrl[2]), "the controller last");
            // every unit a group in unit order: the taps 0..7, the bays 8..15
            int groups = 0, prev = -1;
            for (Step s : p.steps) if (s.group != BuildPlan.NO_GROUP) {
                assertTrue(s.group >= prev);
                prev = s.group;
                groups++;
            }
            assertEquals(8 + 16, groups);
            assertEquals(15, prev);
        }
        assertThrows(
            IllegalArgumentException.class,
            () -> BuildPlan.deepen(SiteFamily.ZENITH_SITE, CX, 70, CZ, 0, -1, form(0)));
        assertThrows(
            IllegalArgumentException.class,
            () -> BuildPlan.deep(SiteFamily.ROOT_SITE, CX, 70, CZ, 1, 1, form(0)));
    }

    /**
     * The deepen job's budgets (CITY.md §1.3): the most checked cells a form has, the most cells it places, the volume
     * a chamber's caisson takes and the EU, within the caps.
     */
    @Test
    void theDeepRootStaysWithinItsBudgets() {
        int checked = 0, placed = 0, volume = 0;
        for (int[] a : AT) {
            RootShape.Deep d = form(a[1]);
            checked = Math.max(checked, d.shape.checkedCount());
            Plan p = BuildPlan.deepen(SiteFamily.ROOT_SITE, CX, a[0], CZ, 0, -1, d);
            int n = 0, v = 0;
            for (Step s : p.steps) {
                if (s.kind == BuildPlan.HARD || s.kind == BuildPlan.AIR || s.kind == BuildPlan.SOFT) n++;
                if (s.stage == RootShape.R_SINK) v++;
            }
            placed = Math.max(placed, n);
            volume = Math.max(volume, v);
        }
        assertEquals(5028, checked);
        assertTrue(checked <= 7000);
        assertEquals(1787, placed, "1,785 blocks, the open centre and the controller's step");
        assertTrue(placed <= 8000);
        assertEquals(6317, volume);
        assertTrue(volume <= 40000);
        assertFalse(placed * 256L + 4620 * 64L > 2_000_000L, "within 2 M EU");
    }
}
