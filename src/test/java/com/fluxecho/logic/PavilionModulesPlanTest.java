package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * The generic module plan on a pavilion site (0.13.0) with the other three pavilions, the Blood Covenant Altar, the
 * Echo Hunting Ground and the Mana Fountain Array, as {@link PavilionPlanTest} holds it with the Arcane Court: the HARD
 * steps are exactly each one's checked cells for four nexus fronts on every diagonal, the controller stands over the
 * octagon's centre (the altar's at Y0 + 3) facing along A toward the nexus, the clear box is the octagon and one round
 * it up to two over the top, the site floor is the apron and the allee, the units launch as groups in slot order with
 * the fittings CITY.md §4.4-4.6 puts before and after them (the fountains' water last), the gate niche is built soft
 * with its wall, the stages hold what CITY lists, and everything the job touches lies in the pavilion's site.
 */
class PavilionModulesPlanTest {

    private static final int[][] FACINGS = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
    private static final int CX = -300, Y0 = 70, CZ = 120;

    interface Plans {

        Plan plan(CampusPlan plan, int site, int cx, int y0, int cz, int fx, int fz);
    }

    interface Controllers {

        int[] at(int site, int cx, int y0, int cz, int fx, int fz);
    }

    /** One pavilion as the tests read it. */
    static final class P {

        final String key;
        final CityBuild build;
        final Blueprint bp;
        final ShapeKit.Shape shape;
        final int[] sites;
        final int meta, checked, air, ctrlY, clearTo;
        final Plans plans;
        final Controllers controllers;

        P(String key, CityBuild build, ShapeKit.Shape shape, int[] sites, int meta, int checked, int air, int ctrlY,
            int clearTo, Plans plans, Controllers controllers) {
            this.key = key;
            this.build = build;
            this.bp = build.blueprint();
            this.shape = shape;
            this.sites = sites;
            this.meta = meta;
            this.checked = checked;
            this.air = air;
            this.ctrlY = ctrlY;
            this.clearTo = clearTo;
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

    static final P BLOOD = new P(
        BloodCovenantShape.KEY,
        BloodCovenantShape.BUILD,
        BloodCovenantShape.SHAPE,
        BloodCovenantShape.SITES,
        ModuleCores.BLOOD_COVENANT,
        1694,
        160,
        3,
        19,
        BloodCovenantShape::plan,
        BloodCovenantShape::controller);
    static final P HUNTING = new P(
        HuntingGroundShape.KEY,
        HuntingGroundShape.BUILD,
        HuntingGroundShape.SHAPE,
        HuntingGroundShape.SITES,
        ModuleCores.HUNTING_GROUND,
        1531,
        88,
        2,
        12,
        HuntingGroundShape::plan,
        HuntingGroundShape::controller);
    static final P MANA = new P(
        ManaFountainShape.KEY,
        ManaFountainShape.BUILD,
        ManaFountainShape.SHAPE,
        ManaFountainShape.SITES,
        ModuleCores.MANA_FOUNTAIN,
        1107,
        160,
        2,
        10,
        ManaFountainShape::plan,
        ManaFountainShape::controller);
    static final P[] ALL = { BLOOD, HUNTING, MANA };

    private static CampusPlan campus(int[] f) {
        return new CampusPlan(Mix.seed(CX + 7 * f[0], Y0 + 3, CZ + 7 * f[1]));
    }

    private static long pos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** The campus cell (a, r) of a world column. */
    private static int[] local(int x, int z, int[] f) {
        return CampusPlan.toLocal(x - CX, z - CZ, f[0], f[1]);
    }

    @Test
    void theHardStepsAreTheCheckedCells() {
        for (P m : ALL) for (int site : m.sites) for (int[] f : FACINGS) {
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
     * The controller stands over the octagon's centre (+-36, +-36), at Y0 + 2 (the Blood Covenant Altar's over its
     * dais at Y0 + 3), and the blueprint's front faces along A toward the nexus; the foundation's centre is the module
     * centre the dock looks for, at Y0.
     */
    @Test
    void theControllerStandsOverTheCentreFacingAlongA() {
        for (P m : ALL) for (int site : CampusPlan.PAVILION_SITES) for (int[] f : FACINGS) {
            int[] c = m.controller(site, f);
            int[] centre = CampusPlan.diagonalCentre(site);
            assertArrayEquals(centre, local(c[0], c[2], f), m.key + " site " + site);
            assertEquals(Y0 + m.ctrlY, c[1], m.key);
            int[] front = BuildPlan.front(c[3]);
            assertArrayEquals(CampusPlan.pavilionFront(site), CampusPlan.toLocal(front[0], front[1], f[0], f[1]));
            int[] mc = m.world(site, f, 12, 0, 12);
            assertArrayEquals(CampusPlan.moduleCentre(site, 25), local(mc[0], mc[2], f));
            assertEquals(Y0, mc[1]);
            assertArrayEquals(c, BuildPlan.moduleController(m.build, site, CX, Y0, CZ, f[0], f[1]));
        }
        assertThrows(IllegalArgumentException.class, () -> BloodCovenantShape.controller(2, CX, Y0, CZ, 0, -1));
        assertThrows(
            IllegalArgumentException.class,
            () -> HuntingGroundShape.plan(campus(FACINGS[0]), 4, CX, Y0, CZ, 0, -1));
        assertThrows(
            IllegalArgumentException.class,
            () -> ManaFountainShape.plan(campus(FACINGS[0]), 9, CX, Y0, CZ, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> ManaFountainShape.controller(1, CX, Y0, CZ, 1, 1));
    }

    @Test
    void stepsAreUniqueAndStagesRunForward() {
        for (P m : ALL) for (int site : m.sites) for (int[] f : FACINGS) {
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
            assertEquals(0, p.count(BuildPlan.M_UPPER), m.key + ": no upper walls");
            assertTrue(p.count(BuildPlan.M_DECK) > 0, m.key + ": its decks");
            assertTrue(p.count(BuildPlan.M_ROOF) > 0, m.key + ": its roof");
            assertEquals(1, p.count(BuildPlan.M_COMMISSION));
            Step core = p.steps.get(p.steps.size() - 1);
            assertEquals(Parts.moduleCore(m.meta), core.part);
            assertEquals(BuildPlan.HARD, core.kind);
            int[] c = m.controller(site, f);
            assertTrue(core.at(c[0], c[1], c[2]));
        }
    }

    /**
     * The octagon and one round it, from Y0 + 1 to two over the shape's top + 1 (CITY.md §4.4: the altar to Y0 + 19;
     * the hunting ground to Y0 + 12; the fountains to Y0 + 10), graded.
     */
    @Test
    void theClearBoxIsTheOctagonAndOneRoundIt() {
        for (P m : ALL) for (int site : CampusPlan.PAVILION_SITES) {
            int[] f = FACINGS[(site + 2) % 4];
            Plan p = m.plan(site, f);
            int[] centre = CampusPlan.diagonalCentre(site);
            Set<Long> cols = new HashSet<>();
            for (BuildPlan.Column c : p.clear) {
                assertEquals(Y0 + 1, c.yFrom);
                assertEquals(Y0 + m.clearTo, c.yTo, m.key);
                assertTrue(c.grade);
                int[] l = local(c.x, c.z, f);
                assertTrue(cols.add(CampusPlan.key(l[0], l[1])));
                assertTrue(CampusPlan.oct(l[0] - centre[0], l[1] - centre[1], 13, 18), "inside oct(13, 18)");
            }
            assertEquals(585, p.clear.size(), m.key);
        }
    }

    /**
     * The FLOOR stage: the site's floor (apron and allee) and the pavilion's own floor of y0 inside its walls (385
     * cells each: the octagon's 481 less its 96 foundations), all at Y0; soft but for the hunting ground's grate tiles.
     */
    @Test
    void theFloorStageHoldsTheApronTheAlleeAndTheInnerFloor() {
        for (P m : ALL) for (int site : CampusPlan.PAVILION_SITES) {
            int[] f = FACINGS[site % 4];
            CampusPlan cp = campus(f);
            Plan p = m.plans.plan(cp, site, CX, Y0, CZ, f[0], f[1]);
            List<Step> floor = p.steps.subList(p.start(BuildPlan.M_FLOOR), p.end(BuildPlan.M_FLOOR));
            Map<Long, Integer> site0 = cp.pavilionFloor(site);
            int inner = 0, hard = 0;
            Map<Long, Integer> laid = new HashMap<>();
            for (Step s : floor) {
                assertEquals(Y0, s.y);
                int[] l = local(s.x, s.z, f);
                if (CampusPlan.inPavilion(site, l[0], l[1])) {
                    inner++;
                    if (s.kind == BuildPlan.HARD) {
                        hard++;
                        assertEquals(Parts.deck(Parts.D_GRATE), s.part, "the arena's grate");
                    } else assertEquals(BuildPlan.SOFT, s.kind);
                } else {
                    assertEquals(BuildPlan.SOFT, s.kind);
                    laid.put(CampusPlan.key(l[0], l[1]), s.part);
                }
            }
            assertEquals(385, inner, m.key);
            assertEquals(m == HUNTING ? 45 : 0, hard, m.key);
            assertEquals(site0, laid, m.key + ": the site floor, every cell with its tile");
        }
    }

    /**
     * FIT: the units as contiguous groups in slot order, with the fittings each pavilion puts before them (the dais and
     * the altar's console; the emitter's console and its pedestals; the obelisk bottom-up) and after them (the
     * fountains' water, last).
     */
    @Test
    void theUnitsAreGroupsInSlotOrderAmongTheirFittings() {
        Map<P, int[]> sizes = new HashMap<>();
        sizes.put(BLOOD, new int[] { 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1 });
        sizes.put(HUNTING, new int[] { 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2 });
        sizes.put(MANA, new int[] { 9, 9, 9, 9, 9, 9, 9, 9, 2, 2, 2, 2, 2, 2, 2, 2 });
        for (P m : ALL) {
            Plan p = m.plan(m.sites[0], FACINGS[1]);
            List<Step> fit = p.steps.subList(p.start(BuildPlan.M_FIT), p.end(BuildPlan.M_FIT));
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
            int[] want = sizes.get(m);
            assertEquals(want.length, groups.size(), m.key);
            for (int g = 0; g < want.length; g++) assertEquals(want[g], (int) groups.get(g), m.key + " unit " + g);
            if (m == BLOOD) {
                assertEquals(37, before.size());
                for (int i = 0; i < 36; i++) {
                    int part = before.get(i).part;
                    assertTrue(part == Parts.frame(Parts.FR_BASE) || part == Parts.fitting(Parts.F_TREAD), "the dais");
                }
                assertEquals(Parts.frame(Parts.FR_CONSOLE), before.get(36).part, "the altar's console on it");
                assertTrue(after.isEmpty());
            } else if (m == HUNTING) {
                assertEquals(5, before.size());
                assertEquals(Parts.frame(Parts.FR_CONSOLE), before.get(0).part, "the emitter first");
                for (int i = 1; i < 5; i++) assertEquals(Parts.fitting(Parts.F_PEDESTAL), before.get(i).part);
                assertTrue(after.isEmpty());
            } else {
                assertEquals(6, before.size());
                assertEquals(Parts.frame(Parts.FR_CONSOLE), before.get(0).part, "the obelisk's console");
                for (int i = 1; i < 5; i++) assertEquals(Parts.frame(Parts.FR_CONDUIT), before.get(i).part);
                assertEquals(Parts.frame(Parts.FR_SEAT), before.get(5).part);
                for (int i = 1; i < 6; i++) assertTrue(before.get(i).y > before.get(i - 1).y, "bottom-up");
                assertEquals(8, after.size(), "the eight fountains' water, after every unit");
                for (Step s : after) {
                    assertEquals(Parts.WATER, s.part);
                    assertEquals(BuildPlan.SOFT, s.kind);
                    assertEquals(Y0 + 1, s.y);
                }
                assertEquals(after.get(after.size() - 1), fit.get(fit.size() - 1), "the water is FIT's last");
            }
        }
    }

    /** The gate niche is built, as SOFT steps of the wall's own blocks in the SHELL stage, on every site and front. */
    @Test
    void theGateNicheIsBuiltSoft() {
        for (P m : ALL) for (int site : m.sites) for (int[] f : FACINGS) {
            Plan p = m.plan(site, f);
            Map<Long, Step> at = new HashMap<>();
            for (Step s : p.steps) at.put(pos(s.x, s.y, s.z), s);
            int n = 0;
            for (int x = 11; x <= 13; x++) for (int y = 1; y <= 4; y++) {
                int[] w = m.world(site, f, x, y, 24);
                Step s = at.get(pos(w[0], w[1], w[2]));
                assertNotNull(s, m.key + ": the niche is built at " + x + "," + y + " site " + site);
                assertEquals(BuildPlan.SOFT, s.kind);
                assertEquals(BuildPlan.M_SHELL, s.stage);
                assertEquals(ShapeKit.partOf(ShapeKit.checkedTwin(m.shape.get(x, y, 24))), s.part);
                n++;
            }
            assertEquals(12, n);
        }
    }

    /** CITY.md §4.4-4.6's stages, probed in the world. */
    @Test
    void theStagesHoldWhatCityLists() {
        int f = BuildPlan.M_FLOOR, sh = BuildPlan.M_SHELL, dk = BuildPlan.M_DECK, rf = BuildPlan.M_ROOF,
            ft = BuildPlan.M_FIT, cm = BuildPlan.M_COMMISSION;
        Map<P, int[][]> probes = new HashMap<>();
        // {x, y, z, stage}: the blood altar's floor, tier 1, the slab at y7, tier 2, the slab at y12, the rail, tier 3,
        // the oculus's rim, the dais, the altar's console, the controller
        probes.put(
            BLOOD,
            new int[][] { { 12, 0, 6, f }, { 0, 3, 12, sh }, { 12, 7, 2, dk }, { 12, 9, 3, dk }, { 12, 12, 3, dk },
                { 12, 8, 0, rf }, { 12, 14, 6, rf }, { 12, 16, 9, rf }, { 12, 1, 10, ft }, { 12, 2, 12, ft },
                { 12, 3, 12, cm } });
        // the hunting ground's grid, its wall, a terrace, the top terrace, a bridge, the velarium, a pedestal, the core
        probes.put(
            HUNTING,
            new int[][] { { 12, 0, 8, f }, { 0, 4, 12, sh }, { 12, 1, 5, dk }, { 12, 3, 1, dk }, { 5, 3, 5, dk },
                { 12, 9, 1, rf }, { 14, 1, 12, ft }, { 12, 2, 12, cm } });
        // the fountains' circle, the outer wall, the architrave, a post, the glass roof, a conduit, the water, the core
        probes.put(
            MANA,
            new int[][] { { 12, 0, 4, f }, { 0, 3, 12, sh }, { 12, 6, 3, dk }, { 11, 1, 3, dk }, { 12, 7, 1, rf },
                { 12, 5, 12, ft }, { 18, 1, 12, ft }, { 12, 2, 12, cm } });
        for (P m : ALL) {
            int site = m.sites[1];
            int[] fr = FACINGS[3];
            Plan p = m.plan(site, fr);
            Map<Long, Integer> stageOf = new HashMap<>();
            for (Step s : p.steps) stageOf.put(pos(s.x, s.y, s.z), s.stage);
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

    /** Everything the job lays or clears lies in the pavilion's site: its apron with the building, and its allee. */
    @Test
    void theJobStaysInItsSite() {
        for (P m : ALL) for (int site : CampusPlan.PAVILION_SITES) for (int[] f : FACINGS) {
            Plan p = m.plan(site, f);
            Set<Long> own = CampusPlan.pavilionSite(site);
            for (Step s : p.steps) {
                int[] l = local(s.x, s.z, f);
                assertTrue(own.contains(CampusPlan.key(l[0], l[1])), m.key + ": step outside the site: " + s);
            }
            for (BuildPlan.Column c : p.clear) {
                int[] l = local(c.x, c.z, f);
                assertTrue(own.contains(CampusPlan.key(l[0], l[1])), m.key + ": clear column outside the site");
            }
            assertEquals(p.steps, m.plan(site, f).steps, m.key + ": the same inputs give the same plan");
        }
        assertFalse(BLOOD.plan(5, FACINGS[0]).steps.isEmpty());
    }

    /**
     * CITY.md §4.5's DECK, "terraces in rings from the arena out, vomitorium bridges": on every diagonal and nexus
     * front the hunting ground lays its seating a whole ring at a time from the arena's rim (octagon radius 6) out to
     * the wall's (11), each ring bottom-up, and the vomitoria's bridges after every terrace.
     */
    @Test
    void theHuntingDeckRisesRingByRingFromTheArenaOut() {
        P m = HUNTING;
        int mid = HuntingGroundShape.MID;
        for (int site : CampusPlan.PAVILION_SITES) for (int[] f : FACINGS) {
            Map<Long, int[]> localOf = new HashMap<>();
            for (int y = 0; y < m.build.height(); y++)
                for (int z = 0; z < m.build.depth(); z++) for (int x = 0; x < m.build.width(); x++) {
                    if (m.build.cell(x, y, z) == ShapeKit.NONE) continue;
                    int[] w = m.world(site, f, x, y, z);
                    localOf.put(pos(w[0], w[1], w[2]), new int[] { x, y, z });
                }
            Plan p = m.plan(site, f);
            List<Step> deck = p.steps.subList(p.start(BuildPlan.M_DECK), p.end(BuildPlan.M_DECK));
            int ring = HuntingGroundShape.TERRACE_FROM, lastY = -1, bridges = 0;
            boolean bridging = false;
            Set<Integer> rings = new HashSet<>();
            for (Step s : deck) {
                int[] l = localOf.get(pos(s.x, s.y, s.z));
                assertNotNull(l, "a DECK step off the shape: " + s);
                if (HuntingGroundShape.bridge(l[0], l[1], l[2])) {
                    bridging = true;
                    bridges++;
                    continue;
                }
                assertFalse(bridging, "a terrace after the bridges at " + l[0] + "," + l[1] + "," + l[2]);
                int r = ShapeKit.onorm(l[0] - mid, l[2] - mid);
                assertTrue(r >= ring, "ring " + r + " after ring " + ring);
                if (r > ring) lastY = -1;
                assertTrue(l[1] >= lastY, "ring " + r + " bottom-up");
                ring = r;
                lastY = l[1];
                rings.add(r);
            }
            assertEquals(6, rings.size(), "six rings of terraces");
            assertEquals(HuntingGroundShape.TERRACE_TO, ring, "the wall's ring last");
            assertEquals(24, bridges, "four vomitoria's bridges");
            assertTrue(bridging, "the bridges last");
        }
        // the stages of every other pavilion keep their plain bottom-up order
        for (P other : new P[] { BLOOD, MANA }) {
            Plan p = other.plan(other.sites[0], FACINGS[0]);
            for (int st : new int[] { BuildPlan.M_SHELL, BuildPlan.M_DECK, BuildPlan.M_ROOF }) {
                int lastY = Integer.MIN_VALUE;
                for (Step s : p.steps.subList(p.start(st), p.end(st))) {
                    assertTrue(s.y >= lastY, other.key + " stage " + st + " bottom-up");
                    lastY = s.y;
                }
            }
        }
    }
}
