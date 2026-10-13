package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.BuildPlan.Column;
import com.fluxecho.logic.BuildPlan.Plan;
import com.fluxecho.logic.BuildPlan.Step;

/**
 * The generic module plan on the gate (0.14.0, the GATE family, CITY.md §2.1-2.2), with the Resonance Gate: the
 * gatehouse's frame ({@code x = 15 + R}, {@code z = A - 23}, its front facing the nexus), the HARD steps exactly its
 * checked cells for four nexus fronts, the controller on the kiosk at (A 30, R 0, Y0 + 2) facing the nexus; the open
 * passage never cleared below Y0 + 7 and never graded, so the gate road, its widenings and the establish pylons stay
 * the establish plan's; a floor that only grows (its apron and the passage cells the road leaves bare); keep-out boxes
 * under which a later forum or refresh of the nexus still lays the road's tiles and raises the pylons; the units as
 * groups in slot order; the seeded annex on the apron; and the masterplan sign's place before the gatehouse left free.
 */
class GatePlanTest {

    private static final int[][] FACINGS = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
    private static final int CX = -300, Y0 = 70, CZ = 410;

    private static CampusPlan campus(int[] f) {
        return new CampusPlan(Mix.seed(CX + 7 * f[0], Y0, CZ + 7 * f[1]));
    }

    private static Plan gate(CampusPlan campus, int[] f) {
        return ResonanceGateShape.plan(campus, 0, CX, Y0, CZ, f[0], f[1]);
    }

    private static long pos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** The campus cell (a, r) of a world column. */
    private static int[] local(int x, int z, int[] f) {
        return CampusPlan.toLocal(x - CX, z - CZ, f[0], f[1]);
    }

    /** The campus cell of a world column as one key. */
    private static long cell(int x, int z, int[] f) {
        int[] l = local(x, z, f);
        return CampusPlan.key(l[0], l[1]);
    }

    /** Every shape cell lies where the gate's frame puts it: {@code x = 15 + R}, {@code z = A - 23}, y up from Y0. */
    @Test
    void theShapeLiesInTheGatesFrame() {
        Blueprint b = ResonanceGateShape.BLUEPRINT;
        for (int[] f : FACINGS) {
            int[] c = ResonanceGateShape.controller(0, CX, Y0, CZ, f[0], f[1]);
            int[] front = BuildPlan.front(c[3]);
            for (Blueprint.Cell cell : b.cells()) {
                int x = cell.a, y = ResonanceGateShape.HEIGHT - 1 - cell.b, z = cell.c;
                int[] w = b.world(cell.a, cell.b, cell.c, c[0], c[1], c[2], front[0], front[1]);
                int[] d = CampusPlan.toWorld(z + CampusPlan.GATEHOUSE_FROM, x - ResonanceGateShape.MID, f[0], f[1]);
                assertArrayEquals(new int[] { CX + d[0], Y0 + y, CZ + d[1] }, w, x + "," + y + "," + z);
            }
        }
    }

    @Test
    void theHardStepsAreTheCheckedCells() {
        Blueprint b = ResonanceGateShape.BLUEPRINT;
        for (int[] f : FACINGS) {
            Plan p = gate(campus(f), f);
            int[] c = ResonanceGateShape.controller(0, CX, Y0, CZ, f[0], f[1]);
            int[] front = BuildPlan.front(c[3]);
            Set<Long> want = new HashSet<>(), air = new HashSet<>();
            for (Blueprint.Cell cell : b.cells()) {
                int[] w = b.world(cell.a, cell.b, cell.c, c[0], c[1], c[2], front[0], front[1]);
                (cell.ch == '-' ? air : want).add(pos(w[0], w[1], w[2]));
            }
            Set<Long> hard = new HashSet<>(), airSteps = new HashSet<>();
            for (Step s : p.steps) {
                if (s.kind == BuildPlan.HARD) assertTrue(hard.add(pos(s.x, s.y, s.z)), "a HARD cell twice");
                else if (s.kind == BuildPlan.AIR) airSteps.add(pos(s.x, s.y, s.z));
            }
            assertEquals(want, hard);
            assertEquals(2370, hard.size());
            assertEquals(air, airSteps);
            assertEquals(136, airSteps.size());
            assertEquals(BuildPlan.moduleKey(ResonanceGateShape.KEY, 0), p.key);
            assertEquals(BuildPlan.M_STAGES, p.stages);
        }
    }

    /** The controller on the kiosk in the road under the bridge: (A 30, R 0, Y0 + 2), its front toward the nexus. */
    @Test
    void theControllerStandsOnTheKioskFacingTheNexus() {
        for (int[] f : FACINGS) {
            int[] c = ResonanceGateShape.controller(0, CX, Y0, CZ, f[0], f[1]);
            assertArrayEquals(new int[] { 30, 0 }, local(c[0], c[2], f));
            assertEquals(Y0 + 2, c[1]);
            int[] front = BuildPlan.front(c[3]);
            assertArrayEquals(new int[] { -1, 0 }, CampusPlan.toLocal(front[0], front[1], f[0], f[1]), "-A");
            assertArrayEquals(c, BuildPlan.moduleController(ResonanceGateShape.BUILD, 0, CX, Y0, CZ, f[0], f[1]));
            // the module centre the window and the 0.9.2 ring look at: the foundation's middle, (A 31, R 0)
            Blueprint b = ResonanceGateShape.BLUEPRINT;
            int[] mc = b.world(15, ResonanceGateShape.HEIGHT - 1, 8, c[0], c[1], c[2], front[0], front[1]);
            assertArrayEquals(new int[] { 31, 0 }, local(mc[0], mc[2], f));
            assertEquals(Y0, mc[1]);
        }
        assertThrows(
            IllegalArgumentException.class,
            () -> BuildPlan.gateController(ResonanceGateShape.BUILD, 2, CX, Y0, CZ, 0, -1),
            "a hall site");
        assertThrows(
            IllegalArgumentException.class,
            () -> BuildPlan.gateController(ResonanceGateShape.BUILD, 0, CX, Y0, CZ, 1, 1));
    }

    @Test
    void stepsAreUniqueAndStagesRunForward() {
        for (int[] f : FACINGS) {
            Plan p = gate(campus(f), f);
            Set<Long> seen = new HashSet<>();
            int last = -1;
            for (Step s : p.steps) {
                assertTrue(seen.add(pos(s.x, s.y, s.z)), "two steps on " + s);
                assertTrue(s.stage >= last);
                last = s.stage;
            }
            Step ctrl = p.steps.get(p.steps.size() - 1);
            assertEquals(BuildPlan.M_COMMISSION, ctrl.stage);
            assertEquals(Parts.moduleCore(ModuleCores.RESONANCE_GATE), ctrl.part);
            assertEquals(1, p.count(BuildPlan.M_COMMISSION));
        }
    }

    /**
     * The clear box: the wings and one cell round them, graded, from Y0 + 1 to two over the crown posts (Y0 + 20); over
     * the passage only the bridge's columns (13 x 7), from Y0 + {@link CampusPlan#GATE_HEADROOM} and never graded.
     */
    @Test
    void thePassageIsNeverClearedBelowTheBridge() {
        for (int[] f : FACINGS) {
            Plan p = gate(campus(f), f);
            int graded = 0, bridge = 0;
            Set<Long> cols = new HashSet<>();
            for (Column c : p.clear) {
                int[] l = local(c.x, c.z, f);
                int a = l[0], r = l[1];
                assertTrue(cols.add(CampusPlan.key(a, r)), "a column twice");
                assertTrue(a >= CampusPlan.GATEHOUSE_FROM - 1 && a <= CampusPlan.GATEHOUSE_TO + 1, "A " + a);
                assertTrue(Math.abs(r) <= CampusPlan.GATEHOUSE_HALF + 1, "R " + r);
                assertEquals(Y0 + 17 + 1 + BuildPlan.MODULE_HEADROOM, c.yTo);
                if (Math.abs(r) <= CampusPlan.GATE_PASSAGE) {
                    assertFalse(c.grade, "the passage is never graded at " + a + "," + r);
                    assertEquals(Y0 + CampusPlan.GATE_HEADROOM, c.yFrom, "the passage at " + a + "," + r);
                    assertTrue(a >= 28 && a <= 34, "only under the bridge: A " + a);
                    bridge++;
                } else {
                    assertTrue(c.grade);
                    assertEquals(Y0 + 1, c.yFrom);
                    graded++;
                }
            }
            assertEquals(13 * 7, bridge);
            assertEquals((33 - 13) * 19, graded);
        }
    }

    /**
     * The establish plan's gate stays its own: no step of the gatehouse lies on a cell of the establish floor below
     * the bridge's headroom but the kiosk (two cells over the road's middle at A 30), none on a pylon's column, no
     * grading column on any of them, and its floor is never laid on a cell the establish floor holds, for any seed.
     */
    @Test
    void theRoadAndThePylonsStayTheEstablishPlans() {
        for (long seed = 1; seed <= 6; seed++) for (int[] f : FACINGS) {
            CampusPlan campus = new CampusPlan(seed * 7919);
            Plan p = gate(campus, f);
            Set<Long> establish = campus.establishFloor()
                .keySet();
            Set<Long> pylons = new HashSet<>();
            for (int[] q : campus.pylons()) pylons.add(CampusPlan.key(q[0], q[1]));
            assertEquals(4, pylons.size());
            int kiosk = 0;
            for (Step s : p.steps) {
                long k = cell(s.x, s.z, f);
                assertFalse(pylons.contains(k), "on a pylon's column: " + s);
                if (!establish.contains(k)) continue;
                assertTrue(s.y > Y0, "on the road's tile: " + s);
                if (s.y < Y0 + CampusPlan.GATE_HEADROOM) {
                    assertArrayEquals(new int[] { 30, 0 }, local(s.x, s.z, f), "only the kiosk under the bridge");
                    kiosk++;
                }
            }
            assertEquals(2, kiosk, "the kiosk's console and controller");
            for (Column c : p.clear) {
                long k = cell(c.x, c.z, f);
                assertFalse(pylons.contains(k), "a pylon's column cleared");
                if (establish.contains(k)) {
                    assertFalse(c.grade, "an establish tile graded");
                    assertTrue(c.yFrom >= Y0 + CampusPlan.GATE_HEADROOM);
                }
            }
            for (long k : campus.gatehouseFloor()
                .keySet()) assertFalse(establish.contains(k), "the floor grows: " + k);
        }
    }

    /**
     * The floor: the soft FLOOR steps at Y0 are the gatehouse's apron ({@link CampusPlan#gatehouseFloor}), the wings'
     * hall floors and the passage cells the road leaves bare ({@link CampusPlan#gatePassageFloor}), deck parts.
     */
    @Test
    void theFloorIsTheApronTheHallsAndThePassagesBareCells() {
        for (int[] f : FACINGS) {
            CampusPlan campus = campus(f);
            Plan p = gate(campus, f);
            Map<Long, Integer> floor = new HashMap<>();
            for (int i = p.start(BuildPlan.M_FLOOR); i < p.end(BuildPlan.M_FLOOR); i++) {
                Step s = p.steps.get(i);
                assertEquals(Y0, s.y);
                assertEquals(BuildPlan.SOFT, s.kind);
                assertTrue(Parts.isDeck(s.part), "a deck tile: " + s);
                assertEquals(null, floor.put(cell(s.x, s.z, f), s.part));
            }
            Map<Long, Integer> apron = campus.gatehouseFloor();
            int halls = 0, passage = 0;
            for (Map.Entry<Long, Integer> e : floor.entrySet()) {
                int a = CampusPlan.keyA(e.getKey()), r = CampusPlan.keyR(e.getKey());
                if (apron.containsKey(e.getKey())) {
                    assertEquals(apron.get(e.getKey()), e.getValue());
                    assertFalse(CampusPlan.inGatehouse(a, r));
                } else if (CampusPlan.inGatePassage(a, r)) {
                    assertTrue(CampusPlan.gatePassageFloor(a, r), a + "," + r);
                    passage++;
                } else {
                    assertTrue(CampusPlan.inGatehouse(a, r));
                    assertEquals(Parts.deck(Parts.D_DECK), (int) e.getValue());
                    halls++;
                }
            }
            assertEquals(apron.size() + 268, floor.size());
            assertEquals(2 * 7 * 15, halls, "the wings' hall floors");
            assertEquals(268 - 2 * 7 * 15, passage, "the passage cells the road leaves bare");
            for (int a = CampusPlan.GATEHOUSE_FROM; a <= CampusPlan.GATEHOUSE_TO; a++)
                for (int r = -CampusPlan.GATE_PASSAGE; r <= CampusPlan.GATE_PASSAGE; r++) assertEquals(
                    CampusPlan.gatePassageFloor(a, r),
                    floor.containsKey(CampusPlan.key(a, r)),
                    a + "," + r);
        }
    }

    /**
     * The keep-out the campus jobs leave alone once the gatehouse stands ({@link BuildPlan#gateKeepOut}): every column
     * its plan works, from the lowest height it works there; height-ranged only over the passage (the bridge from Y0 +
     * 7, the kiosk's column from Y0 + 1), whole columns elsewhere; no two boxes share a column; the protected boxes
     * are the same boxes over the same heights.
     */
    @Test
    void theKeepOutIsWhatTheJobWorks() {
        for (int[] f : FACINGS) {
            Plan p = gate(campus(f), f);
            List<int[]> keep = BuildPlan.gateKeepOut(p, CX, Y0, CZ, f[0], f[1]);
            Map<Long, Integer> from = new HashMap<>();
            for (int[] b : keep) {
                assertTrue(b.length == 4 || b.length == 6);
                assertTrue(b[0] <= b[2] && b[1] <= b[3]);
                for (int x = b[0]; x <= b[2]; x++) for (int z = b[1]; z <= b[3]; z++) {
                    int[] l = local(x, z, f);
                    int y = b.length == 6 ? b[4] : Integer.MIN_VALUE;
                    assertEquals(null, from.put(CampusPlan.key(l[0], l[1]), y), "two boxes on " + l[0] + "," + l[1]);
                    if (b.length == 6) {
                        assertTrue(CampusPlan.inGatePassage(l[0], l[1]), "ranged in the passage only");
                        assertEquals(255, b[5]);
                        boolean kiosk = l[0] == 30 && l[1] == 0;
                        assertEquals(kiosk ? Y0 + 1 : Y0 + CampusPlan.GATE_HEADROOM, b[4], l[0] + "," + l[1]);
                    }
                }
            }
            Set<Long> worked = new HashSet<>();
            for (Step s : p.steps) {
                long k = cell(s.x, s.z, f);
                worked.add(k);
                Integer y = from.get(k);
                assertTrue(y != null && s.y >= y, "a step outside the keep-out: " + s);
            }
            for (Column c : p.clear) {
                long k = cell(c.x, c.z, f);
                worked.add(k);
                Integer y = from.get(k);
                assertTrue(y != null && (c.grade ? y == Integer.MIN_VALUE : c.yFrom >= y), "cleared outside");
            }
            assertEquals(worked, from.keySet(), "the boxes hold the worked columns alone");
            List<int[]> protect = BuildPlan.gateProtect(keep);
            assertEquals(keep.size(), protect.size());
            for (int i = 0; i < keep.size(); i++) {
                int[] k = keep.get(i), q = protect.get(i);
                int lo = k.length == 6 ? k[4] : 0;
                assertArrayEquals(new int[] { k[0], lo, k[1], k[2], 255, k[3] }, q);
            }
            assertArrayEquals(
                keep.toArray(new int[0][]),
                BuildPlan.gateKeepOut(p, CX, Y0, CZ, f[0], f[1])
                    .toArray(new int[0][]),
                "the same boxes every time");
        }
    }

    /**
     * Under a standing gatehouse the forum (the refresh, a repair of the campus) with its keep-out still lays every
     * tile of the gate road and its widenings under the bridge and around the kiosk, and raises the four pylons; it
     * never places, grades or paves where the gatehouse works.
     */
    @Test
    void theForumStillRestoresTheRoadAndThePylons() {
        for (int[] f : FACINGS) {
            CampusPlan campus = campus(f);
            Plan p = gate(campus, f);
            List<int[]> keep = BuildPlan.gateKeepOut(p, CX, Y0, CZ, f[0], f[1]);
            Plan forum = BuildPlan.forum(campus, CX, Y0, CZ, f[0], f[1], keep);
            Set<Long> gateCells = new HashSet<>();
            for (Step s : p.steps) gateCells.add(pos(s.x, s.y, s.z));
            Set<Long> floorTiles = new HashSet<>();
            List<int[]> pylonSteps = new ArrayList<>();
            for (Step s : forum.steps) {
                assertFalse(gateCells.contains(pos(s.x, s.y, s.z)), "the forum on the gatehouse: " + s);
                if (s.stage == BuildPlan.E_FLOOR) floorTiles.add(cell(s.x, s.z, f));
                if (s.stage == BuildPlan.E_FIXTURE) pylonSteps.add(local(s.x, s.z, f));
            }
            int under = 0;
            for (long k : campus.establishFloor()
                .keySet()) {
                int a = CampusPlan.keyA(k), r = CampusPlan.keyR(k);
                if (!CampusPlan.inGatePassage(a, r)) continue;
                assertTrue(floorTiles.contains(k), "the road's tile at " + a + "," + r);
                under++;
            }
            assertTrue(under > 100, "the road through the passage: " + under);
            assertTrue(floorTiles.contains(CampusPlan.key(30, 0)), "the tile under the kiosk");
            for (int[] q : campus.pylons()) {
                int n = 0;
                for (int[] s : pylonSteps) if (s[0] == q[0] && s[1] == q[1]) n++;
                assertEquals(BuildPlan.PYLON_PILLARS + 1, n, "the pylon at " + q[0] + "," + q[1]);
            }
            Set<Long> whole = new HashSet<>();
            for (int[] b : keep) if (b.length == 4)
                for (int x = b[0]; x <= b[2]; x++) for (int z = b[1]; z <= b[3]; z++) whole.add(cell(x, z, f));
            for (Column c : forum.clear) {
                long k = cell(c.x, c.z, f);
                assertFalse(whole.contains(k), "the forum grades the gatehouse's ground");
                if (c.grade) assertFalse(
                    CampusPlan.inGatePassage(CampusPlan.keyA(k), CampusPlan.keyR(k)) && keepsAbove(keep, c, f),
                    "graded under the bridge");
            }
            for (Step s : forum.steps) assertFalse(whole.contains(cell(s.x, s.z, f)) && s.y >= Y0, "paved: " + s);
        }
    }

    /** Whether a ranged keep-out box holds the column of a clearing (the forum leaves such a column alone). */
    private static boolean keepsAbove(List<int[]> keep, Column c, int[] f) {
        for (int[] b : keep) {
            if (b.length != 6) continue;
            if (c.x >= b[0] && c.x <= b[2] && c.z >= b[1] && c.z <= b[3] && c.yTo >= b[4]) return true;
        }
        return false;
    }

    /** The units launch as one group each, in slot order (the six resonators, then the four racks), within FIT. */
    @Test
    void theUnitsLaunchAsGroupsInSlotOrder() {
        Plan p = gate(campus(FACINGS[0]), FACINGS[0]);
        List<Integer> order = new ArrayList<>();
        Map<Integer, Integer> sizes = new HashMap<>();
        char first = 0;
        for (int i = p.start(BuildPlan.M_FIT); i < p.end(BuildPlan.M_FIT); i++) {
            Step s = p.steps.get(i);
            if (i == p.start(BuildPlan.M_FIT)) first = s.part == Parts.frame(Parts.FR_CONSOLE) ? 'K' : '?';
            if (s.group == BuildPlan.NO_GROUP) continue;
            if (order.isEmpty() || order.get(order.size() - 1) != s.group) order.add(s.group);
            sizes.merge(s.group, 1, Integer::sum);
        }
        assertEquals('K', first, "the kiosk's console first");
        List<Integer> want = new ArrayList<>();
        for (int g = 0; g < 10; g++) want.add(g);
        assertEquals(want, order);
        for (int g = 0; g < 10; g++) assertEquals(g < 6 ? 4 : 5, (int) sizes.get(g), "group " + g);
        Step last = p.steps.get(p.end(BuildPlan.M_FIT) - 1);
        assertEquals(Parts.frame(Parts.FR_SEAT), last.part, "the spire core's seat last");
    }

    /** The seeded annex stands on the apron, one cell out from the wings' outer walls (R +-17), on Y0 + 1 and up. */
    @Test
    void theAnnexStandsOnTheApron() {
        for (int[] f : FACINGS) {
            CampusPlan campus = campus(f);
            Plan p = gate(campus, f);
            Set<Long> apron = CampusPlan.gatehouseApron();
            int n = 0;
            for (int i = p.start(BuildPlan.M_DECOR); i < p.end(BuildPlan.M_DECOR); i++) {
                Step s = p.steps.get(i);
                int[] l = local(s.x, s.z, f);
                assertTrue(apron.contains(CampusPlan.key(l[0], l[1])), "off the apron: " + l[0] + "," + l[1]);
                assertEquals(CampusPlan.GATEHOUSE_HALF + 2, Math.abs(l[1]));
                assertTrue(s.y >= Y0 + 1 && s.y <= Y0 + 3);
                assertEquals(BuildPlan.SOFT, s.kind);
                n++;
            }
            assertTrue(n > 0, "an annex");
        }
    }

    /**
     * The masterplan's sign of site 0 stands on the road before the gatehouse (A {@link CampusPlan#GATE_SIGN}, R 0, 3.2
     * over the floor), not inside the bridge; the gatehouse's job works nothing there, and its bridge to the nexus runs
     * to the passage's mouth.
     */
    @Test
    void theSignAndTheBridgeStandBeforeTheGatehouse() {
        assertEquals(20.5, CampusPlan.GATE_SIGN, 1e-12);
        assertEquals(22.5, CampusPlan.GATE_DOOR, 1e-12);
        assertTrue(CampusPlan.inGate(20, 0) && CampusPlan.inGate(21, 0), "on the gate road");
        for (int[] f : FACINGS) {
            Plan p = gate(campus(f), f);
            for (Step s : p.steps) {
                int[] l = local(s.x, s.z, f);
                assertFalse(l[0] <= 21 && Math.abs(l[1]) <= 3, "before the gatehouse on the road: " + s);
            }
            for (Column c : p.clear) assertTrue(local(c.x, c.z, f)[0] >= CampusPlan.GATEHOUSE_FROM - 1);
        }
    }
}
