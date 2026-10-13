package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * The generic module plan on an outer diagonal (0.15.0, M12 §5 and §6) with its two buildings, the Sophon Array and
 * the Holo Sand Table: the HARD steps are exactly each one's checked cells for four nexus fronts on every outer
 * diagonal, the controller stands where the building puts it (the array's over the octagon's centre, the table's six
 * toward its front) with the blueprint's front along A toward the nexus, the clear box is the octagon and one round it
 * up to two over the top, the site floor is the apron and the allée, the units launch as groups in slot order with the
 * fittings CITY.md §4.10 and §4.11 put after them (the array's struts and lattice last, bottom-up; the table's ring
 * light), the array's gate niche is built soft with its wall, the stages hold what CITY lists, and everything the job
 * touches lies on the outer diagonal's site.
 */
class OuterModulesPlanTest {

    private static final int[][] FACINGS = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
    private static final int CX = 700, Y0 = 66, CZ = -340;

    interface Plans {

        Plan plan(CampusPlan plan, int site, int cx, int y0, int cz, int fx, int fz);
    }

    interface Controllers {

        int[] at(int site, int cx, int y0, int cz, int fx, int fz);
    }

    /** One building as the tests read it. */
    static final class M {

        final String key;
        final CityBuild build;
        final Blueprint bp;
        final ShapeKit.Shape shape;
        final int[] sites;
        final int meta, checked, air, clearTo, toFront;
        final Plans plans;
        final Controllers controllers;

        M(String key, CityBuild build, ShapeKit.Shape shape, int[] sites, int meta, int checked, int air, int clearTo,
            int toFront, Plans plans, Controllers controllers) {
            this.key = key;
            this.build = build;
            this.bp = build.blueprint();
            this.shape = shape;
            this.sites = sites;
            this.meta = meta;
            this.checked = checked;
            this.air = air;
            this.clearTo = clearTo;
            this.toFront = toFront;
            this.plans = plans;
            this.controllers = controllers;
        }

        Plan plan(int site, int[] f) {
            return plans.plan(campus(f), site, CX, Y0, CZ, f[0], f[1]);
        }

        int[] controller(int site, int[] f) {
            return controllers.at(site, CX, Y0, CZ, f[0], f[1]);
        }

        /** The world cell of a point of the shape for the controller of a site and front. */
        int[] world(int site, int[] f, int x, int y, int z) {
            int[] c = controller(site, f);
            int[] front = BuildPlan.front(c[3]);
            int[] cell = build.cellOf(x, y, z);
            return bp.world(cell[0], cell[1], cell[2], c[0], c[1], c[2], front[0], front[1]);
        }
    }

    static final M SOPHON = new M(
        SophonArrayShape.KEY,
        SophonArrayShape.BUILD,
        SophonArrayShape.SHAPE,
        SophonArrayShape.SITES,
        ModuleCores.SOPHON_ARRAY,
        2397,
        128,
        35,
        0,
        SophonArrayShape::plan,
        SophonArrayShape::controller);
    static final M HOLO = new M(
        HoloTableShape.KEY,
        HoloTableShape.BUILD,
        HoloTableShape.SHAPE,
        HoloTableShape.SITES,
        ModuleCores.HOLO_TABLE,
        2148,
        150,
        18,
        6,
        HoloTableShape::plan,
        HoloTableShape::controller);
    static final M[] ALL = { SOPHON, HOLO };

    private static CampusPlan campus(int[] f) {
        return new CampusPlan(Mix.seed(CX + 11 * f[0], Y0 + 2, CZ + 11 * f[1]));
    }

    private static long pos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** The campus cell (a, r) of a world column. */
    private static int[] local(int x, int z, int[] f) {
        return CampusPlan.toLocal(x - CX, z - CZ, f[0], f[1]);
    }

    @Test
    void theirSitesAreTheOuterDiagonals() {
        assertArrayEquals(new int[] { 11, 17, 13, 15 }, SophonArrayShape.SITES, "the array prefers O1");
        assertArrayEquals(new int[] { 17, 11, 15, 13 }, HoloTableShape.SITES, "the table prefers O7");
        for (M m : ALL) for (int site : m.sites) assertEquals(SiteFamily.OUTER_DIAG, SiteFamily.of(site));
    }

    @Test
    void theHardStepsAreTheCheckedCells() {
        for (M m : ALL) for (int site : m.sites) for (int[] f : FACINGS) {
            Plan p = m.plan(site, f);
            int[] c = m.controller(site, f);
            int[] front = BuildPlan.front(c[3]);
            Set<Long> want = new HashSet<>(), air = new HashSet<>();
            for (Blueprint.Cell cell : m.bp.cells()) {
                int[] w = m.bp.world(cell.a, cell.b, cell.c, c[0], c[1], c[2], front[0], front[1]);
                (cell.ch == '-' ? air : want).add(pos(w[0], w[1], w[2]));
            }
            Set<Long> hard = new HashSet<>(), airSteps = new HashSet<>();
            for (Step s : p.steps) {
                if (s.kind == BuildPlan.HARD) assertTrue(hard.add(pos(s.x, s.y, s.z)), m.key + ": a HARD cell twice");
                else if (s.kind == BuildPlan.AIR) airSteps.add(pos(s.x, s.y, s.z));
            }
            assertEquals(want, hard, m.key + " site " + site);
            assertEquals(m.checked, hard.size(), m.key);
            assertEquals(air, airSteps, m.key);
            assertEquals(m.air, airSteps.size(), m.key);
            assertEquals(BuildPlan.moduleKey(m.key, site), p.key);
        }
    }

    /**
     * The controller stands at Y0 + 2 over the octagon's centre (+-62, +-62) (the table's six cells toward its front,
     * which faces along A toward the nexus); the foundation's centre is the module centre the dock looks for, at Y0.
     */
    @Test
    void theControllerStandsWhereTheBuildingPutsIt() {
        for (M m : ALL) for (int site : OuterRing.DIAG_SITES) for (int[] f : FACINGS) {
            int[] c = m.controller(site, f);
            int[] centre = OuterRing.diagCentre(site), fr = OuterRing.diagFront(site);
            assertArrayEquals(
                new int[] { centre[0] + m.toFront * fr[0], centre[1] + m.toFront * fr[1] },
                local(c[0], c[2], f),
                m.key + " site " + site);
            assertEquals(Y0 + 2, c[1], m.key);
            int[] front = BuildPlan.front(c[3]);
            assertArrayEquals(fr, CampusPlan.toLocal(front[0], front[1], f[0], f[1]));
            int[] mc = m.world(site, f, 15, 0, 15);
            assertArrayEquals(centre, local(mc[0], mc[2], f));
            assertArrayEquals(CampusPlan.moduleCentre(site, 31), local(mc[0], mc[2], f));
            assertEquals(Y0, mc[1]);
            assertArrayEquals(c, BuildPlan.moduleController(m.build, site, CX, Y0, CZ, f[0], f[1]));
            assertEquals(site, OuterRing.diagSiteAt(local(mc[0], mc[2], f)[0], local(mc[0], mc[2], f)[1]));
        }
        assertThrows(IllegalArgumentException.class, () -> SophonArrayShape.controller(1, CX, Y0, CZ, 0, -1));
        assertThrows(
            IllegalArgumentException.class,
            () -> SophonArrayShape.plan(campus(FACINGS[0]), 7, CX, Y0, CZ, 0, -1));
        assertThrows(
            IllegalArgumentException.class,
            () -> HoloTableShape.plan(campus(FACINGS[0]), 20, CX, Y0, CZ, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> HoloTableShape.controller(11, CX, Y0, CZ, 1, 1));
    }

    @Test
    void stepsAreUniqueAndStagesRunForward() {
        for (M m : ALL) for (int site : m.sites) for (int[] f : FACINGS) {
            Plan p = m.plan(site, f);
            Set<Long> seen = new HashSet<>();
            int last = -1;
            for (Step s : p.steps) {
                assertTrue(seen.add(pos(s.x, s.y, s.z)), m.key + ": two steps on " + s);
                assertTrue(s.stage >= last);
                last = s.stage;
            }
            assertEquals(BuildPlan.M_STAGES, p.stages);
            assertEquals(0, p.count(BuildPlan.M_CLEAR));
            // the Sophon Array runs its stages in its own order (its struts and lattice after its racks); the plan
            // counts them by position
            int[] order = m == SOPHON ? SophonArrayShape.STAGE_ORDER : null;
            int upper = at(order, BuildPlan.M_UPPER), fit = at(order, BuildPlan.M_FIT);
            assertEquals(m == SOPHON ? 774 : 0, p.count(upper), m.key + ": the struts and lattice, no upper walls");
            assertEquals(
                m == HOLO,
                p.count(at(order, BuildPlan.M_DECK)) > 0,
                m.key + ": the table's ramps and gallery");
            assertTrue(p.count(at(order, BuildPlan.M_ROOF)) > 0, m.key + ": its roof");
            if (m == SOPHON) {
                assertTrue(fit < upper, "the racks before the sphere");
                assertTrue(at(order, BuildPlan.M_ROOF) < fit, "the roof before the racks");
                int from = p.start(upper), lastY = Integer.MIN_VALUE;
                for (int i = from; i < from + p.count(upper); i++) {
                    Step s = p.steps.get(i);
                    assertEquals(Parts.frame(Parts.FR_PILLAR), s.part, "a frame pillar");
                    assertTrue(s.y >= lastY, "bottom-up");
                    lastY = s.y;
                }
                assertEquals(
                    "build.stage.s" + upper,
                    com.fluxecho.nexus.NexusGui.stageKey(BuildPlan.moduleKey(m.key, site), upper));
                assertEquals("build.stage.s6", com.fluxecho.nexus.NexusGui.stageKey(p.key, upper));
                assertEquals("build.stage.s5", com.fluxecho.nexus.NexusGui.stageKey(p.key, fit));
            } else assertEquals(
                "build.stage.c" + BuildPlan.M_FIT,
                com.fluxecho.nexus.NexusGui.stageKey(p.key, BuildPlan.M_FIT));
            assertEquals(1, p.count(BuildPlan.M_COMMISSION));
            Step core = p.steps.get(p.steps.size() - 1);
            assertEquals(Parts.moduleCore(m.meta), core.part);
            assertEquals(BuildPlan.HARD, core.kind);
            int[] c = m.controller(site, f);
            assertTrue(core.at(c[0], c[1], c[2]));
        }
    }

    /**
     * The octagon and one round it (its 741 columns grown to oct(16, 22), 869) and, since the fixes of 0.15.0, the rest
     * of its site (the apron's outer row and the allée: the whole 916 of {@link OuterRing#diagSiteCells}, so a site the
     * outer establish did not grade, one taken by a claim or given back by a neighbour that went, is graded by the
     * building's own job), from Y0 + 1 to two over the shape's top + 1 (CITY.md §4.10: the array to Y0 + 35; the
     * table to Y0 + 18), graded: 32,060 cells for the array, within the 40,000 a job may clear.
     */
    @Test
    void theClearBoxIsTheOctagonAndOneRoundIt() {
        for (M m : ALL) for (int site : OuterRing.DIAG_SITES) {
            int[] f = FACINGS[(site / 2 + 1) % 4];
            Plan p = m.plan(site, f);
            int[] centre = OuterRing.diagCentre(site);
            Set<Long> cols = new HashSet<>();
            long cells = 0;
            for (BuildPlan.Column c : p.clear) {
                assertEquals(Y0 + 1, c.yFrom);
                assertEquals(Y0 + m.clearTo, c.yTo, m.key);
                assertTrue(c.grade);
                int[] l = local(c.x, c.z, f);
                assertTrue(cols.add(CampusPlan.key(l[0], l[1])));
                if (!CampusPlan.oct(l[0] - centre[0], l[1] - centre[1], 16, 22)) assertTrue(
                    OuterRing.diagSiteCells(site)
                        .contains(CampusPlan.key(l[0], l[1])),
                    "on its site");
                cells += c.yTo - c.yFrom + 1;
            }
            assertEquals(OuterRing.diagSiteCells(site), cols, m.key + ": the whole site");
            assertEquals(916, p.clear.size(), m.key);
            assertTrue(cells <= 40_000, m.key + " clears " + cells);
        }
    }

    /**
     * The FLOOR stage: the outer diagonal's site floor (its apron and allée, 175 cells with their tiles) and the
     * building's own soft floor of y0 inside its walls (621 cells: the octagon's 741 less its 120 foundations), all at
     * Y0 and soft.
     */
    @Test
    void theFloorStageHoldsTheApronTheAlleeAndTheInnerFloor() {
        for (M m : ALL) for (int site : OuterRing.DIAG_SITES) {
            int[] f = FACINGS[site % 4];
            CampusPlan cp = campus(f);
            Plan p = m.plans.plan(cp, site, CX, Y0, CZ, f[0], f[1]);
            List<Step> floor = p.steps.subList(p.start(BuildPlan.M_FLOOR), p.end(BuildPlan.M_FLOOR));
            Map<Long, Integer> laid = new HashMap<>();
            int inner = 0;
            for (Step s : floor) {
                assertEquals(Y0, s.y);
                assertEquals(BuildPlan.SOFT, s.kind);
                int[] l = local(s.x, s.z, f);
                if (OuterRing.inDiag(site, l[0], l[1])) inner++;
                else laid.put(CampusPlan.key(l[0], l[1]), s.part);
            }
            assertEquals(621, inner, m.key);
            assertEquals(cp.outerDiagFloor(site), laid, m.key + ": the site floor, every cell with its tile");
        }
    }

    /**
     * FIT: the units as contiguous groups in slot order, then the fittings ranked after them: the array's central
     * console, and then in its own UPPER stage right after FIT its struts and its lattice sphere bottom-up (CITY.md's
     * order, a FIT before UPPER); the table's console under the controller, then the ring light.
     */
    @Test
    void theUnitsAreGroupsInSlotOrderBeforeTheirFittings() {
        for (M m : ALL) {
            Plan p = m.plan(m.sites[0], FACINGS[1]);
            int[] order = m == SOPHON ? SophonArrayShape.STAGE_ORDER : null;
            int fitAt = at(order, BuildPlan.M_FIT);
            List<Step> fit = p.steps.subList(p.start(fitAt), p.end(fitAt));
            List<Step> before = new ArrayList<>(), after = new ArrayList<>();
            Map<Integer, Integer> groups = new HashMap<>();
            int lastGroup = BuildPlan.NO_GROUP;
            for (Step s : fit) {
                if (s.group != BuildPlan.NO_GROUP) {
                    assertTrue(after.isEmpty(), m.key + ": the units are contiguous");
                    if (s.group != lastGroup) {
                        assertEquals(lastGroup + 1, s.group, m.key + ": in unit order");
                        lastGroup = s.group;
                    }
                    groups.merge(s.group, 1, Integer::sum);
                } else if (groups.isEmpty()) before.add(s);
                else after.add(s);
            }
            assertTrue(before.isEmpty(), m.key + ": nothing before the units");
            assertEquals(Parts.frame(Parts.FR_CONSOLE), after.get(0).part, m.key + ": the console first after them");
            if (m == SOPHON) {
                assertEquals(48, groups.size());
                for (int g = 0; g < 48; g++) assertEquals(5, (int) groups.get(g), "rack " + g);
                assertEquals(1, after.size(), "the console");
                // then UPPER, its own stage after FIT: the struts and the lattice, bottom-up
                int upAt = at(order, BuildPlan.M_UPPER);
                assertEquals(fitAt + 1, upAt);
                List<Step> upper = p.steps.subList(p.start(upAt), p.end(upAt));
                assertEquals(774, upper.size());
                int lastY = Integer.MIN_VALUE;
                for (Step s : upper) {
                    assertEquals(Parts.frame(Parts.FR_PILLAR), s.part);
                    assertTrue(s.y >= lastY, "the struts and the lattice bottom-up");
                    lastY = s.y;
                }
                assertEquals(Y0 + SophonArrayShape.STRUT_FROM, upper.get(0).y, "the struts first");
                assertEquals(Y0 + SophonArrayShape.LATTICE_TO, upper.get(upper.size() - 1).y, "the top pole last");
            } else {
                assertEquals(10, groups.size());
                assertEquals(69, (int) groups.get(0), "the table");
                for (int g = 1; g < 10; g++) assertEquals(1, (int) groups.get(g), "console " + g);
                assertEquals(41, after.size());
                for (int i = 1; i < after.size(); i++) {
                    assertEquals(Parts.frame(Parts.FR_RING), after.get(i).part);
                    assertEquals(Y0 + HoloTableShape.RING_Y, after.get(i).y);
                }
            }
        }
    }

    /** The array's gate niche is built, as SOFT steps of the wall's own blocks in SHELL, on every site and front. */
    @Test
    void theGateNicheIsBuiltSoft() {
        M m = SOPHON;
        for (int site : m.sites) for (int[] f : FACINGS) {
            Plan p = m.plan(site, f);
            Map<Long, Step> at = new HashMap<>();
            for (Step s : p.steps) at.put(pos(s.x, s.y, s.z), s);
            int n = 0;
            for (int x = 14; x <= 16; x++) for (int y = 1; y <= 4; y++) {
                int[] w = m.world(site, f, x, y, 30);
                Step s = at.get(pos(w[0], w[1], w[2]));
                assertNotNull(s, "the niche is built at " + x + "," + y + " site " + site);
                assertEquals(BuildPlan.SOFT, s.kind);
                assertEquals(BuildPlan.M_SHELL, s.stage);
                assertEquals(ShapeKit.partOf(ShapeKit.checkedTwin(m.shape.get(x, y, 30))), s.part);
                n++;
            }
            assertEquals(12, n);
        }
    }

    /** CITY.md §4.10 and §4.11's stages, probed in the world. */
    @Test
    void theStagesHoldWhatCityLists() {
        int fl = BuildPlan.M_FLOOR, sh = BuildPlan.M_SHELL, dk = BuildPlan.M_DECK, rf = BuildPlan.M_ROOF,
            ft = BuildPlan.M_FIT, up = BuildPlan.M_UPPER, cm = BuildPlan.M_COMMISSION;
        Map<M, int[][]> probes = new HashMap<>();
        // {x, y, z, stage}: the array's aisle floor, a wall, a lintel, the roof, the oculus, a rack's socket, the
        // console, a strut, the lattice's top pole, the controller
        probes.put(
            SOPHON,
            new int[][] { { 7, 0, 9, fl }, { 0, 3, 12, sh }, { 5, 5, 5, sh }, { 2, 6, 15, rf }, { 15, 6, 15, rf },
                { 13, 2, 8, ft }, { 15, 1, 15, ft }, { 10, 7, 10, up }, { 15, 32, 15, up }, { 15, 2, 15, cm } });
        // the table's floor, the drum, the cornice, a ramp's tread, the gallery, a rail, the dome, the lantern, the
        // table, a console, the ring light, the controller
        probes.put(
            HOLO,
            new int[][] { { 15, 0, 20, fl }, { 0, 4, 15, sh }, { 0, 10, 15, sh }, { 1, 3, 12, dk }, { 15, 6, 28, dk },
                { 15, 7, 27, dk }, { 14, 10, 2, rf }, { 14, 14, 11, rf }, { 15, 1, 15, ft }, { 22, 1, 15, ft },
                { 15, 9, 9, ft }, { 15, 2, 9, cm } });
        for (M m : ALL) {
            int site = m.sites[1];
            int[] fr = FACINGS[3];
            Plan p = m.plan(site, fr);
            // the plan's stages by position, each the stage the building's order names there
            int[] order = m == SOPHON ? SophonArrayShape.STAGE_ORDER : null;
            Map<Long, Integer> stageOf = new HashMap<>();
            for (Step s : p.steps) stageOf.put(pos(s.x, s.y, s.z), order == null ? s.stage : order[s.stage]);
            for (int[] l : probes.get(m)) {
                assertTrue(m.build.cell(l[0], l[1], l[2]) != ' ', m.key + " probe " + l[0] + "," + l[1] + "," + l[2]);
                int[] w = m.world(site, fr, l[0], l[1], l[2]);
                assertEquals(
                    l[3],
                    (int) stageOf.get(pos(w[0], w[1], w[2])),
                    m.key + " " + l[0] + "," + l[1] + "," + l[2]);
            }
        }
    }

    /**
     * Everything the job lays or clears lies on the outer diagonal's site: its apron with the building, and its allée.
     */
    @Test
    void theJobStaysOnItsSite() {
        for (M m : ALL) for (int site : OuterRing.DIAG_SITES) for (int[] f : FACINGS) {
            Plan p = m.plan(site, f);
            Set<Long> own = OuterRing.diagSiteCells(site);
            int decor = 0;
            for (Step s : p.steps) {
                int[] l = local(s.x, s.z, f);
                assertTrue(own.contains(CampusPlan.key(l[0], l[1])), m.key + ": step outside the site: " + s);
                if (s.stage == BuildPlan.M_DECOR) decor++;
            }
            for (BuildPlan.Column c : p.clear) {
                int[] l = local(c.x, c.z, f);
                assertTrue(own.contains(CampusPlan.key(l[0], l[1])), m.key + ": clear column outside the site");
            }
            assertTrue(decor > 0, m.key + ": the annex stands on the apron");
            assertEquals(p.steps, m.plan(site, f).steps, m.key + ": the same inputs give the same plan");
        }
    }

    /**
     * The steps a job takes (CITY.md §1.3: at most 8,000; the array's 3,158 cells and the table's 2,919 with the site
     * floor and the annex), and the controller and every unit cell stand inside the world over any base the band
     * allows: at its top (Y0 225) the array's sphere clips at the world's top, as the Arcane Court's spire does, and
     * the
     * job still raises every rack and ends with the core.
     */
    @Test
    void theJobsStayWithinTheirBudgets() {
        for (M m : ALL) {
            Plan p = m.plan(m.sites[0], FACINGS[0]);
            assertTrue(p.steps.size() <= 8000, m.key + ": " + p.steps.size() + " steps");
            assertTrue(p.steps.size() >= m.shape.steps() + 175, m.key);
            Plan high = m.plans.plan(campus(FACINGS[0]), m.sites[0], CX, CampusBand.HIGHEST, CZ, 0, -1);
            assertTrue(BuildPlan.standTop(high) <= 255, m.key + " at the band's top");
            // over the band's top the array's sphere clips at the world's top (CITY.md §1.3 Vertical); its controller
            // and its racks stay, and the job still ends with the core
            int pillars = 0;
            for (Step st : high.steps) {
                assertTrue(st.y >= 0 && st.y <= 255, m.key + ": a step out of the world");
                if (st.part == Parts.frame(Parts.FR_PILLAR)) pillars++;
            }
            // the lattice's top two layers (40 and 37 cells) lie over y 255 at Y0 225
            if (m == SOPHON) assertEquals(774 - 77, pillars, "the sphere clips at the world's top");
            assertEquals(
                Parts.moduleCore(m.meta),
                high.steps.get(high.steps.size() - 1).part,
                m.key + ": the core last");
            int units = 0;
            for (Step st : high.steps) if (st.group != BuildPlan.NO_GROUP) units++;
            int want = 0;
            for (UnitLayout.Unit u : (m == SOPHON ? SophonArrayShape.UNITS : HoloTableShape.UNITS).units())
                want += u.cells.size();
            assertEquals(want, units, m.key + ": every unit cell in the world");
        }
    }

    /** The position of a stage in a plan built in an order ({@code null}: the generic one, by number). */
    private static int at(int[] order, int stage) {
        if (order == null) return stage;
        for (int p = 0; p < order.length; p++) if (order[p] == stage) return p;
        throw new IllegalArgumentException("no stage " + stage);
    }
}
