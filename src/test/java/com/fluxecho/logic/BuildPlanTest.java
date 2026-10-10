package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.BuildPlan.Column;
import com.fluxecho.logic.BuildPlan.Plan;
import com.fluxecho.logic.BuildPlan.Step;

class BuildPlanTest {

    /** The four nexus fronts {fx, fz}: north, east, south, west. */
    private static final int[][] FACINGS = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
    private static final int CX = 100, Y0 = 64, CZ = -200;
    private static final int W = ArchiveShape.WIDTH, D = ArchiveShape.DEPTH, H = ArchiveShape.HEIGHT;

    // ---- fixtures ----

    /** The phase-I controller of a nexus centred on (CX, Y0, CZ): five ahead of the centre, two up. */
    private static int[] nexusController(int[] f) {
        return new int[] { CX + 5 * f[0], Y0 + 2, CZ + 5 * f[1] };
    }

    private static CampusPlan campus(int[] f) {
        int[] c = nexusController(f);
        return new CampusPlan(Mix.seed(c[0], c[1], c[2]));
    }

    private static Plan establish(int[] f) {
        int[] c = nexusController(f);
        return BuildPlan.establish(campus(f), CX, Y0, CZ, f[0], f[1], NexusShape.PHASE_1, c[0], c[1], c[2]);
    }

    private static Plan archive(int site, int[] f) {
        return BuildPlan.archive(campus(f), site, CX, Y0, CZ, f[0], f[1]);
    }

    private static long pos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static long pos(Step s) {
        return pos(s.x, s.y, s.z);
    }

    /** The campus cell {a, r} of a world cell. */
    private static int[] local(int x, int z, int[] f) {
        return CampusPlan.toLocal(x - CX, z - CZ, f[0], f[1]);
    }

    private static long dist2(int x, int z, int[] f) {
        int[] l = local(x, z, f);
        return (long) l[0] * l[0] + (long) l[1] * l[1];
    }

    private static List<Step> stage(Plan p, int stage) {
        return p.steps.subList(p.start(stage), p.end(stage));
    }

    /** The world cell of an Archive local cell, the way the structure check maps it from the controller. */
    private static int[] archiveCell(int site, int[] f, int x, int y, int z) {
        int[] c = BuildPlan.archiveController(site, CX, Y0, CZ, f[0], f[1]);
        int[] front = BuildPlan.front(c[3]);
        return ArchiveShape.BLUEPRINT.world(x, H - 1 - y, z, c[0], c[1], c[2], front[0], front[1]);
    }

    // ---- facings ----

    @Test
    void facingOrdinalsAreForgeDirections() {
        assertEquals(2, BuildPlan.facingOrdinal(0, -1), "north");
        assertEquals(3, BuildPlan.facingOrdinal(0, 1), "south");
        assertEquals(4, BuildPlan.facingOrdinal(-1, 0), "west");
        assertEquals(5, BuildPlan.facingOrdinal(1, 0), "east");
        for (int o = 2; o <= 5; o++) {
            int[] f = BuildPlan.front(o);
            assertEquals(o, BuildPlan.facingOrdinal(f[0], f[1]));
        }
        assertThrows(IllegalArgumentException.class, () -> BuildPlan.facingOrdinal(1, 1));
        assertThrows(IllegalArgumentException.class, () -> BuildPlan.front(1));
        assertThrows(IllegalArgumentException.class, () -> BuildPlan.front(6));
    }

    @Test
    void theFixtureIsANexusCentredOnItsBase() {
        Blueprint b = NexusShape.PHASE_1;
        for (int[] f : FACINGS) {
            int[] c = nexusController(f);
            assertArrayEquals(
                new int[] { CX, Y0, CZ },
                b.world(b.ctrlA, b.height() - 1, b.ctrlC + b.centreBack(), c[0], c[1], c[2], f[0], f[1]));
        }
    }

    // ---- establish ----

    @Test
    void establishBuildsPhaseOneWithoutTheControllerConsoleFirst() {
        Blueprint b = NexusShape.PHASE_1;
        Map<Character, Integer> parts = new HashMap<>();
        parts.put(NexusShape.BASE, Parts.frame(Parts.FR_BASE));
        parts.put(NexusShape.LIT, Parts.frame(Parts.FR_BASE_LIT));
        parts.put(NexusShape.PILLAR, Parts.frame(Parts.FR_PILLAR));
        parts.put(NexusShape.CONDUIT, Parts.frame(Parts.FR_CONDUIT));
        parts.put(NexusShape.RING, Parts.frame(Parts.FR_RING));
        parts.put(NexusShape.SEAT, Parts.frame(Parts.FR_SEAT));
        parts.put(NexusShape.CONSOLE, Parts.frame(Parts.FR_CONSOLE));
        for (int[] f : FACINGS) {
            int[] c = nexusController(f);
            Map<Long, Integer> want = new HashMap<>();
            for (Blueprint.Cell cell : b.cells()) {
                if (cell.ch == '~') continue;
                int[] w = b.world(cell.a, cell.b, cell.c, c[0], c[1], c[2], f[0], f[1]);
                want.put(pos(w[0], w[1], w[2]), parts.get(cell.ch));
            }
            Plan p = establish(f);
            List<Step> core = stage(p, BuildPlan.E_CORE);
            Map<Long, Integer> got = new HashMap<>();
            for (Step s : core) {
                assertEquals(BuildPlan.HARD, s.kind);
                assertEquals(BuildPlan.NO_GROUP, s.group);
                got.put(pos(s), s.part);
            }
            assertEquals(core.size(), got.size(), "no cell twice");
            assertEquals(want, got, "exactly the phase-I cells but the controller");
            assertFalse(got.containsKey(pos(c[0], c[1], c[2])), "the controller is the player's core");

            Step first = core.get(0);
            assertEquals(Parts.frame(Parts.FR_CONSOLE), first.part, "the console stand first");
            assertTrue(first.at(c[0], c[1] - 1, c[2]), "right under the floating core");

            // then the lit spokes outward, the base by octagon distance, then the rest bottom-up
            int phase = 0, last = -1;
            for (Step s : core.subList(1, core.size())) {
                int[] l = local(s.x, s.z, f);
                int now, key;
                if (s.part == Parts.frame(Parts.FR_BASE_LIT)) {
                    now = 1;
                    key = Math.max(Math.abs(l[0]), Math.abs(l[1]));
                } else if (s.part == Parts.frame(Parts.FR_BASE)) {
                    now = 2;
                    int aa = Math.abs(l[0]), ar = Math.abs(l[1]);
                    key = Math.max(7 * Math.max(aa, ar), 5 * (aa + ar));
                } else {
                    now = 3;
                    key = s.y;
                }
                assertTrue(now >= phase, "spokes, then base, then the rest: " + s);
                if (now > phase) last = -1;
                assertTrue(key >= last, "outward (or upward) within each part: " + s);
                phase = now;
                last = key;
                if (now < 3) assertEquals(Y0, s.y, "the dais is the base layer");
            }
        }
    }

    @Test
    void establishPavesOutwardFromTheCentre() {
        for (int[] f : FACINGS) {
            CampusPlan cp = campus(f);
            Plan p = establish(f);
            List<Step> floor = stage(p, BuildPlan.E_FLOOR);
            Map<Long, Step> byPos = new HashMap<>();
            for (Step s : floor) byPos.put(pos(s), s);
            assertEquals(floor.size(), byPos.size());
            assertEquals(
                cp.establishFloor()
                    .size()
                    + cp.wells()
                        .size(),
                floor.size(),
                "one step per paved cell, two per light well");
            for (Map.Entry<Long, Integer> e : cp.establishFloor()
                .entrySet()) {
                int a = CampusPlan.keyA(e.getKey()), r = CampusPlan.keyR(e.getKey());
                int[] d = CampusPlan.toWorld(a, r, f[0], f[1]);
                int x = CX + d[0], z = CZ + d[1];
                if (cp.wells()
                    .contains(e.getKey())) {
                    Step top = byPos.get(pos(x, Y0, z)), bottom = byPos.get(pos(x, Y0 - 1, z));
                    assertEquals(Parts.AIR, top.part);
                    assertEquals(BuildPlan.AIR, top.kind);
                    assertEquals(Parts.deck(Parts.D_WELL), bottom.part);
                    assertEquals(BuildPlan.SOFT, bottom.kind);
                } else {
                    Step s = byPos.get(pos(x, Y0, z));
                    assertEquals((int) e.getValue(), s.part);
                    assertEquals(BuildPlan.SOFT, s.kind);
                }
            }
            long last = -1;
            for (Step s : floor) {
                long d = dist2(s.x, s.z, f);
                assertTrue(d >= last, "a ripple outward: " + s);
                last = d;
            }
        }
    }

    @Test
    void fixturesArePylonsThenThePort() {
        for (int[] f : FACINGS) {
            CampusPlan cp = campus(f);
            List<Step> fix = stage(establish(f), BuildPlan.E_FIXTURE);
            assertEquals(13, fix.size(), "four pylons of three, and the port");
            Step port = fix.get(12);
            int[] pl = cp.supplyPort();
            int[] pd = CampusPlan.toWorld(pl[0], pl[1], f[0], f[1]);
            assertTrue(port.at(CX + pd[0], Y0 + 1, CZ + pd[1]));
            assertEquals(Parts.SUPPLY_PORT, port.part);
            Set<Long> want = new HashSet<>();
            for (int[] py : cp.pylons()) {
                int[] d = CampusPlan.toWorld(py[0], py[1], f[0], f[1]);
                for (int y = 1; y <= 3; y++) want.add(pos(CX + d[0], Y0 + y, CZ + d[1]));
            }
            int lastY = Integer.MIN_VALUE;
            for (Step s : fix.subList(0, 12)) {
                assertTrue(want.remove(pos(s)), "a pylon cell: " + s);
                assertEquals(Parts.frame(s.y == Y0 + 3 ? Parts.FR_CONDUIT : Parts.FR_PILLAR), s.part);
                assertTrue(s.y >= lastY, "the pylons rise together");
                lastY = s.y;
            }
            assertTrue(want.isEmpty());
        }
    }

    @Test
    void establishGradesTheDiscAndClearsTheDais() {
        for (int[] f : FACINGS) {
            CampusPlan cp = campus(f);
            int[] c = nexusController(f);
            Plan p = establish(f);
            assertEquals("establish", p.key);
            assertEquals(BuildPlan.E_STAGES, p.stages);
            assertEquals(0, p.count(BuildPlan.E_CLEAR), "clearing is columns, not steps");
            assertEquals(0, p.count(BuildPlan.E_FORM), "forming places nothing");
            Set<Long> graded = new HashSet<>(), dais = new HashSet<>();
            Set<Long> columns = new HashSet<>();
            long last = -1;
            for (Column col : p.clear) {
                assertTrue(columns.add(pos(col.x, 0, col.z)), "one column per cell: " + col);
                assertTrue(col.yTo >= col.yFrom, "cleared from the top down to yFrom");
                assertEquals(Y0 + BuildPlan.CLEAR_UP, col.yTo);
                int[] l = local(col.x, col.z, f);
                (col.grade ? graded : dais).add(CampusPlan.key(l[0], l[1]));
                if (col.x == c[0] && col.z == c[2]) assertEquals(c[1] + 1, col.yFrom, "never the core itself");
                else assertEquals(Y0 + 1, col.yFrom);
                long d = dist2(col.x, col.z, f);
                assertTrue(d >= last, "outward from the centre");
                last = d;
            }
            assertEquals(cp.gradeArea(), graded, "the whole graded disc");
            for (long k : dais) assertTrue(CampusPlan.inBase(CampusPlan.keyA(k), CampusPlan.keyR(k)));
            assertEquals(97, dais.size(), "every dais column");
            for (Column col : p.clear) {
                int[] l = local(col.x, col.z, f);
                assertTrue(CampusPlan.oct(l[0], l[1], CampusPlan.GRADE_R, CampusPlan.GRADE_K));
            }
        }
    }

    @Test
    void theForumIsTheEstablishWithoutTheNexus() {
        for (int[] f : FACINGS) {
            Plan e = establish(f);
            Plan fo = BuildPlan.forum(campus(f), CX, Y0, CZ, f[0], f[1]);
            assertEquals("forum", fo.key);
            assertEquals(BuildPlan.E_STAGES, fo.stages);
            assertEquals(0, fo.count(BuildPlan.E_CORE));
            List<Step> want = new ArrayList<>(e.steps);
            want.removeAll(stage(e, BuildPlan.E_CORE));
            assertEquals(want, fo.steps);
            List<Column> grade = new ArrayList<>();
            for (Column c : e.clear) if (c.grade) grade.add(c);
            assertEquals(grade, fo.clear, "grading only: the 0.9.2 nexus stands on the dais");
        }
    }

    /**
     * A 0.9.2 library hall docked on legacy ring slot {@code slot} at the default radius 32, placed the way
     * {@code TileNexus.previewModules} places it: its foundation centre on the slot at Y0, its front towards the
     * nexus. Returns the hall's foundation centre {x, y, z} followed by every blueprint cell {x, y, z}.
     */
    private static List<int[]> legacyHall(int slot, int[] f) {
        Blueprint b = LibraryShape.BLUEPRINT;
        int[] o = RingSlots.offset(slot, 32, f[0], f[1]);
        int[] face = RingSlots.facing(-o[0], -o[1]);
        int[] cc = LibraryShape.cellOf(LibraryShape.SIZE / 2, 0, LibraryShape.SIZE / 2);
        int[] rel = b.world(cc[0], cc[1], cc[2], 0, 0, 0, face[0], face[1]);
        int x = CX + o[0] - rel[0], y = Y0 - rel[1], z = CZ + o[1] - rel[2];
        List<int[]> out = new ArrayList<>();
        out.add(b.world(cc[0], cc[1], cc[2], x, y, z, face[0], face[1]));
        for (Blueprint.Cell c : b.cells()) out.add(b.world(c.a, c.b, c.c, x, y, z, face[0], face[1]));
        return out;
    }

    private static boolean inAny(List<int[]> boxes, int x, int z) {
        for (int[] b : boxes) if (x >= b[0] && x <= b[2] && z >= b[1] && z <= b[3]) return true;
        return false;
    }

    @Test
    void theForumAndEstablishLeaveTheDockedHallsAlone() {
        for (int[] f : FACINGS) {
            List<int[]> boxes = new ArrayList<>();
            Set<Long> hallColumns = new HashSet<>(), slot0 = new HashSet<>();
            for (int k = 0; k < RingSlots.SLOTS; k++) {
                List<int[]> hall = legacyHall(k, f);
                int[] centre = hall.get(0);
                assertArrayEquals(
                    RingSlots.offset(k, 32, f[0], f[1]),
                    new int[] { centre[0] - CX, centre[2] - CZ },
                    "the hall sits on its slot");
                assertEquals(Y0, centre[1]);
                boxes.add(BuildPlan.moduleKeepOut(centre));
                for (int[] c : hall.subList(1, hall.size())) {
                    // the hall and the ring of cells round it
                    for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) {
                        hallColumns.add(pos(c[0] + i, 0, c[2] + j));
                        if (k == 0) slot0.add(pos(c[0] + i, 0, c[2] + j));
                    }
                }
            }

            // without the keep-out the gate, its pylons and the grading run through the hall on slot 0
            Plan plain = BuildPlan.forum(campus(f), CX, Y0, CZ, f[0], f[1]);
            int floorHits = 0, pylonHits = 0;
            for (Step s : plain.steps) {
                if (!slot0.contains(pos(s.x, 0, s.z))) continue;
                if (s.stage == BuildPlan.E_FLOOR) floorHits++;
                if (s.stage == BuildPlan.E_FIXTURE) pylonHits++;
            }
            assertTrue(floorHits > 50, floorHits + " gate tiles on the slot-0 hall");
            assertEquals(12, pylonHits, "every pylon cell stands in the slot-0 hall");

            int[] c = nexusController(f);
            Plan forum = BuildPlan.forum(campus(f), CX, Y0, CZ, f[0], f[1], boxes);
            Plan est = BuildPlan
                .establish(campus(f), CX, Y0, CZ, f[0], f[1], NexusShape.PHASE_1, c[0], c[1], c[2], boxes);
            for (Plan p : new Plan[] { forum, est }) {
                for (Step s : p.steps) {
                    if (s.stage == BuildPlan.E_CORE) continue;
                    assertFalse(hallColumns.contains(pos(s.x, 0, s.z)), p.key + " builds in a docked hall: " + s);
                }
                for (Column col : p.clear) {
                    assertFalse(hallColumns.contains(pos(col.x, 0, col.z)), p.key + " clears a docked hall: " + col);
                }
            }

            // nothing else changes: the kept plans are the plain ones less the columns in a box
            Plan plainEst = establish(f);
            for (Plan[] pair : new Plan[][] { { plain, forum }, { plainEst, est } }) {
                List<Step> want = new ArrayList<>();
                for (Step s : pair[0].steps) {
                    if (s.stage == BuildPlan.E_CORE || !inAny(boxes, s.x, s.z)) want.add(s);
                }
                assertEquals(want, pair[1].steps);
                List<Column> cols = new ArrayList<>();
                for (Column col : pair[0].clear) if (!col.grade || !inAny(boxes, col.x, col.z)) cols.add(col);
                assertEquals(cols, pair[1].clear);
            }
            assertEquals(
                stage(plainEst, BuildPlan.E_CORE),
                stage(est, BuildPlan.E_CORE),
                "the nexus itself is never kept out");

            // the gate still runs from the forum up to the hall
            Set<Long> paved = new HashSet<>();
            for (Step s : stage(forum, BuildPlan.E_FLOOR)) paved.add(pos(s.x, 0, s.z));
            for (int a = 17; a <= 24; a++) {
                int[] d = CampusPlan.toWorld(a, 0, f[0], f[1]);
                assertTrue(paved.contains(pos(CX + d[0], 0, CZ + d[1])), "the gate at A " + a);
            }
            assertEquals(1, stage(forum, BuildPlan.E_FIXTURE).size(), "the pylons stand in the hall; the port stays");
        }
        assertThrows(IllegalArgumentException.class, () -> BuildPlan.moduleKeepOut(new int[] { 1, 2 }));
        assertThrows(
            IllegalArgumentException.class,
            () -> BuildPlan.forum(campus(FACINGS[0]), CX, Y0, CZ, 0, -1, Arrays.asList(new int[] { 1, 2, 3 })));
        List<int[]> flipped = new ArrayList<>();
        flipped.add(new int[] { CX + 40, CZ + 7, CX + 20, CZ - 7 });
        List<int[]> ordered = new ArrayList<>();
        ordered.add(new int[] { CX + 20, CZ - 7, CX + 40, CZ + 7 });
        assertEquals(
            BuildPlan.forum(campus(FACINGS[1]), CX, Y0, CZ, 1, 0, ordered).steps,
            BuildPlan.forum(campus(FACINGS[1]), CX, Y0, CZ, 1, 0, flipped).steps,
            "any corner order");
    }

    // ---- the Archive ----

    @Test
    void theArchiveFacesTheNexusFromItsSite() {
        for (int site : CampusPlan.HALL_SITES) for (int[] f : FACINGS) {
            int[] c = BuildPlan.archiveController(site, CX, Y0, CZ, f[0], f[1]);
            int[] ax = CampusPlan.siteAxis(site);
            assertEquals(Y0 + ArchiveShape.CTRL_Y, c[1]);
            assertArrayEquals(
                new int[] { CampusPlan.HALL_FRONT * ax[0], CampusPlan.HALL_FRONT * ax[1] },
                local(c[0], c[2], f),
                "the front row at radial 24 on the site axis");
            int[] out = CampusPlan.toWorld(ax[0], ax[1], f[0], f[1]);
            assertArrayEquals(new int[] { -out[0], -out[1] }, BuildPlan.front(c[3]), "the front faces the nexus");

            int[] centre = archiveCell(site, f, ArchiveShape.MID, 0, (D - 1) / 2);
            assertEquals(Y0, centre[1]);
            assertArrayEquals(
                CampusPlan.moduleCentre(site, D),
                local(centre[0], centre[2], f),
                "the Archive's centre is the site's module centre");
            int[] back = archiveCell(site, f, ArchiveShape.MID, 0, D - 1);
            int far = CampusPlan.HALL_FRONT + D - 1;
            assertArrayEquals(
                new int[] { far * ax[0], far * ax[1] },
                local(back[0], back[2], f),
                "the back row runs away from the nexus");
            for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) {
                int[] w = archiveCell(site, f, x, 0, z);
                int[] l = local(w[0], w[2], f);
                assertTrue(CampusPlan.inHall(site, W, D, l[0], l[1]), "the footprint is the hall's");
            }
        }
        assertThrows(IllegalArgumentException.class, () -> BuildPlan.archiveController(0, CX, Y0, CZ, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> BuildPlan.archiveController(3, CX, Y0, CZ, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> BuildPlan.archiveController(4, CX, Y0, CZ, 1, 1));
    }

    @Test
    void archiveHardCellsAreTheBlueprintsCheckCells() {
        Blueprint b = ArchiveShape.BLUEPRINT;
        for (int site : CampusPlan.HALL_SITES) for (int[] f : FACINGS) {
            int[] c = BuildPlan.archiveController(site, CX, Y0, CZ, f[0], f[1]);
            int[] front = BuildPlan.front(c[3]);
            Map<Long, Integer> hard = new HashMap<>();
            Set<Long> air = new HashSet<>();
            for (Blueprint.Cell cell : b.cells()) {
                int[] w = b.world(cell.a, cell.b, cell.c, c[0], c[1], c[2], front[0], front[1]);
                if (cell.ch == ArchiveShape.AIR) air.add(pos(w[0], w[1], w[2]));
                else hard.put(pos(w[0], w[1], w[2]), ArchiveShape.partOf(cell.ch));
            }
            Plan p = archive(site, f);
            Map<Long, Integer> gotHard = new HashMap<>();
            Set<Long> gotAir = new HashSet<>();
            for (Step s : p.steps) {
                if (s.kind == BuildPlan.HARD) gotHard.put(pos(s), s.part);
                else if (s.kind == BuildPlan.AIR) {
                    gotAir.add(pos(s));
                    assertEquals(Parts.AIR, s.part);
                    assertEquals(BuildPlan.M_SHELL, s.stage, "door cells belong to the shell");
                }
            }
            assertEquals(hard, gotHard, "site " + site + " front " + f[0] + "," + f[1]);
            assertEquals(air, gotAir);
            assertEquals(5055, gotHard.size(), "every checked cell but the door air");
            assertEquals(48, gotAir.size());
        }
    }

    @Test
    void noCellIsPlacedTwice() {
        for (int[] f : FACINGS) {
            assertUnique(establish(f));
            assertUnique(BuildPlan.forum(campus(f), CX, Y0, CZ, f[0], f[1]));
            for (int site : CampusPlan.HALL_SITES) assertUnique(archive(site, f));
        }
    }

    private static void assertUnique(Plan p) {
        Set<Long> seen = new HashSet<>();
        for (Step s : p.steps) assertTrue(seen.add(pos(s)), p.key + " places " + s + " twice");
        Set<Long> cols = new HashSet<>();
        for (Column c : p.clear) assertTrue(cols.add(pos(c.x, 0, c.z)), p.key + " clears " + c + " twice");
        int stage = 0;
        for (Step s : p.steps) {
            assertTrue(s.stage >= stage, "stages never go back");
            stage = s.stage;
        }
        for (int st = 0; st < p.stages; st++) {
            for (Step s : stage(p, st)) assertEquals(st, s.stage);
        }
        assertEquals(p.steps.size(), p.end(p.stages - 1));
    }

    @Test
    void archiveStages() {
        for (int site : CampusPlan.HALL_SITES) for (int[] f : FACINGS) {
            CampusPlan cp = campus(f);
            Plan p = archive(site, f);
            assertEquals("module:library@" + site, p.key);
            assertEquals(BuildPlan.M_STAGES, p.stages);
            assertEquals(0, p.count(BuildPlan.M_CLEAR));

            // FLOOR: the site floor and the soft interior floor, outward from the nexus
            List<Step> floor = stage(p, BuildPlan.M_FLOOR);
            assertEquals(
                cp.siteFloor(site, W, D)
                    .size()
                    + ArchiveShape.softFloor()
                        .size(),
                floor.size());
            long last = -1;
            for (Step s : floor) {
                assertEquals(Y0, s.y);
                assertEquals(BuildPlan.SOFT, s.kind);
                long d = dist2(s.x, s.z, f);
                assertTrue(d >= last, "outward from the nexus: " + s);
                last = d;
            }
            Map<Long, Integer> parts = new HashMap<>();
            for (Step s : floor) parts.put(pos(s), s.part);
            for (int[] xz : ArchiveShape.softFloor()) {
                int[] w = archiveCell(site, f, xz[0], 0, xz[1]);
                boolean spine = xz[0] == ArchiveShape.MID && xz[1] <= 28 && xz[1] % 2 == 0;
                assertEquals(Parts.deck(spine ? Parts.D_LIT : Parts.D_DECK), (int) parts.get(pos(w[0], w[1], w[2])));
            }
            for (int z = 2; z <= 28; z += 2) {
                int[] w = archiveCell(site, f, ArchiveShape.MID, 0, z);
                assertEquals(Parts.deck(Parts.D_LIT), (int) parts.get(pos(w[0], w[1], w[2])), "the lit spine");
            }

            // the shell, deck, upper walls and roof rise layer by layer within their bands
            assertBand(p, BuildPlan.M_SHELL, 1, 5);
            assertBand(p, BuildPlan.M_DECK, 1, 7);
            assertBand(p, BuildPlan.M_UPPER, 7, 11);
            assertBand(p, BuildPlan.M_ROOF, 12, 15);
            Map<Long, Step> at = index(p);
            for (int[] st : ArchiveShape.stairSteps()) {
                int[] w = archiveCell(site, f, st[0], st[1], st[2]);
                assertEquals(BuildPlan.M_DECK, at.get(pos(w[0], w[1], w[2])).stage, "the stairs come with the deck");
            }
            for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) {
                char ch = ArchiveShape.cell(x, ArchiveShape.DECK_Y, z);
                if (ch == ArchiveShape.ANY) continue;
                int[] w = archiveCell(site, f, x, ArchiveShape.DECK_Y, z);
                assertEquals(BuildPlan.M_DECK, at.get(pos(w[0], w[1], w[2])).stage, "the whole y6 layer");
            }
            for (int x = 0; x < W; x++) for (int y = 1; y < ArchiveShape.DECK_Y; y++) {
                for (int z : new int[] { 0, D - 1 }) {
                    if (ArchiveShape.cell(x, y, z) == ArchiveShape.CONTROLLER) continue;
                    int[] w = archiveCell(site, f, x, y, z);
                    assertEquals(BuildPlan.M_SHELL, at.get(pos(w[0], w[1], w[2])).stage, "the front and back walls");
                }
            }

            // DECOR is the canopy, COMMISSION the controller alone, last
            List<Step> decor = stage(p, BuildPlan.M_DECOR);
            assertEquals(
                ArchiveShape.decor()
                    .size(),
                decor.size());
            for (Step s : decor) {
                assertEquals(Parts.frame(Parts.FR_RING), s.part);
                assertEquals(Y0 + 5, s.y);
            }
            List<Step> commission = stage(p, BuildPlan.M_COMMISSION);
            assertEquals(1, commission.size());
            Step last1 = p.steps.get(p.steps.size() - 1);
            assertEquals(commission.get(0), last1, "the controller is the last step");
            int[] c = BuildPlan.archiveController(site, CX, Y0, CZ, f[0], f[1]);
            assertTrue(last1.at(c[0], c[1], c[2]));
            assertEquals(Parts.LIBRARY_CORE, last1.part);
            assertEquals(BuildPlan.HARD, last1.kind);
            for (Step s : p.steps) {
                if (s != last1) assertNotEquals(Parts.LIBRARY_CORE, s.part, "one controller");
            }
        }
    }

    private static void assertBand(Plan p, int stage, int yLo, int yHi) {
        List<Step> l = stage(p, stage);
        assertFalse(l.isEmpty());
        int last = Integer.MIN_VALUE;
        for (Step s : l) {
            assertTrue(s.y >= Y0 + yLo && s.y <= Y0 + yHi, "stage " + stage + " out of its band: " + s);
            assertTrue(s.y >= last, "stage " + stage + " bottom-up: " + s);
            last = s.y;
        }
    }

    private static Map<Long, Step> index(Plan p) {
        Map<Long, Step> out = new HashMap<>();
        for (Step s : p.steps) out.put(pos(s), s);
        return out;
    }

    @Test
    void unitsAreContiguousGroupsOfFifteen() {
        for (int site : CampusPlan.HALL_SITES) for (int[] f : FACINGS) {
            Plan p = archive(site, f);
            for (Step s : p.steps) {
                if (s.stage != BuildPlan.M_FIT) assertEquals(BuildPlan.NO_GROUP, s.group, "only units are groups");
            }
            List<Step> fit = stage(p, BuildPlan.M_FIT);
            int i = 0;
            while (fit.get(i).group == BuildPlan.NO_GROUP) {
                assertEquals(Parts.fitting(Parts.F_POST), fit.get(i).part, "posts before the units");
                i++;
            }
            for (LibraryUnits.Unit u : LibraryUnits.UNITS) {
                Set<Long> want = new HashSet<>();
                int plinths = 0, bodies = 0, crowns = 0;
                for (int y = u.yBody - 1; y <= u.yBody + 3; y++) for (int k = 0; k < 3; k++) {
                    int[] w = archiveCell(site, f, u.x(k), y, u.z(k));
                    want.add(pos(w[0], w[1], w[2]));
                }
                int lastY = Integer.MIN_VALUE;
                for (int k = 0; k < 15; k++, i++) {
                    Step s = fit.get(i);
                    assertEquals(u.index, s.group, "unit " + u.call + " is one contiguous group");
                    assertTrue(want.remove(pos(s)), "a cell of " + u.call);
                    assertTrue(s.y >= lastY, "bottom-up within the unit");
                    lastY = s.y;
                    if (s.part == Parts.fitting(Parts.F_PLINTH)) plinths++;
                    else if (s.part == Parts.frame(Parts.FR_SHELF)) bodies++;
                    else if (s.part == Parts.fitting(Parts.F_CROWN)) crowns++;
                }
                assertTrue(want.isEmpty());
                assertEquals(3, plinths);
                assertEquals(9, bodies);
                assertEquals(3, crowns);
            }
            // then pedestals, lecterns, the desk and the foundations
            List<Step> tail = fit.subList(i, fit.size());
            int[] counts = new int[4];
            int phase = 0;
            int[] desk = archiveCell(site, f, ArchiveShape.DESK[0], ArchiveShape.DESK[1], ArchiveShape.DESK[2]);
            for (Step s : tail) {
                assertEquals(BuildPlan.NO_GROUP, s.group);
                int now;
                if (s.part == Parts.fitting(Parts.F_PEDESTAL)) now = 0;
                else if (s.part == Parts.frame(Parts.FR_CONSOLE) && !s.at(desk[0], desk[1], desk[2])) now = 1;
                else if (s.part == Parts.frame(Parts.FR_CONSOLE)) now = 2;
                else if (s.part == Parts.frame(Parts.FR_FOUNDATION)) now = 3;
                else throw new AssertionError("unexpected fitting " + s);
                assertTrue(now >= phase, "pedestals, lecterns, desk, foundations: " + s);
                phase = now;
                counts[now]++;
            }
            assertArrayEquals(
                new int[] { ArchiveShape.pedestals()
                    .size(),
                    ArchiveShape.lecterns()
                        .size(),
                    1, ArchiveShape.BLUEPRINT.count(ArchiveShape.FOUNDATION) },
                counts);
        }
    }

    @Test
    void mirroredPairsAreAdjacent() {
        for (int site : CampusPlan.HALL_SITES) for (int[] f : FACINGS) {
            Plan p = archive(site, f);
            int[] ax = CampusPlan.siteAxis(site);
            for (int stage : new int[] { BuildPlan.M_SHELL, BuildPlan.M_DECK, BuildPlan.M_UPPER, BuildPlan.M_ROOF }) {
                List<Step> l = stage(p, stage);
                for (int i = 0; i < l.size(); i++) {
                    Step s = l.get(i);
                    int[] a = local(s.x, s.z, f);
                    int t = a[0] * ax[0] + a[1] * ax[1], across = -a[0] * ax[1] + a[1] * ax[0];
                    if (across == 0) continue;
                    // the mirror cell across the site axis
                    int ma = t * ax[0] + across * ax[1], mr = t * ax[1] - across * ax[0];
                    int[] mw = CampusPlan.toWorld(ma, mr, f[0], f[1]);
                    boolean adjacent = i > 0 && l.get(i - 1)
                        .at(CX + mw[0], s.y, CZ + mw[1]) || i + 1 < l.size()
                            && l.get(i + 1)
                                .at(CX + mw[0], s.y, CZ + mw[1]);
                    assertTrue(adjacent, "the mirror of " + s + " is next to it");
                }
            }
        }
    }

    @Test
    void archiveClearsItsBoxAndAMargin() {
        for (int site : CampusPlan.HALL_SITES) for (int[] f : FACINGS) {
            Plan p = archive(site, f);
            int[] ax = CampusPlan.siteAxis(site);
            int top = Integer.MIN_VALUE;
            for (Step s : p.steps) top = Math.max(top, s.y);
            assertEquals(Y0 + H - 1, top, "the ridge is the Archive's top layer");
            assertEquals((W + 2) * (D + 2), p.clear.size());
            Set<Long> cols = new HashSet<>();
            long last = -1;
            for (Column c : p.clear) {
                assertFalse(c.grade);
                assertEquals(Y0 + 1, c.yFrom);
                assertEquals(Y0 + H + BuildPlan.ARCHIVE_HEADROOM, c.yTo, "Y0 + HEIGHT + 2, cleared from the top down");
                assertEquals(top + 3, c.yTo, "three above the ridge");
                int[] l = local(c.x, c.z, f);
                int t = l[0] * ax[0] + l[1] * ax[1], s = -l[0] * ax[1] + l[1] * ax[0];
                assertTrue(t >= CampusPlan.HALL_FRONT - 1 && t <= CampusPlan.HALL_FRONT + D, "t " + t);
                assertTrue(Math.abs(s) <= W / 2 + 1, "s " + s);
                cols.add(pos(c.x, 0, c.z));
                long d = dist2(c.x, c.z, f);
                assertTrue(d >= last);
                last = d;
            }
            for (int x = -1; x <= W; x++) for (int z = -1; z <= D; z++) {
                int[] w = archiveCell(site, f, x, 0, z);
                assertTrue(cols.contains(pos(w[0], 0, w[2])), "the footprint and its margin");
            }
        }
    }

    @Test
    void plansAreDeterministic() {
        for (int[] f : FACINGS) {
            Plan a = establish(f), b = establish(f);
            assertEquals(a.steps, b.steps);
            assertEquals(a.clear, b.clear);
            for (int site : CampusPlan.HALL_SITES) {
                Plan x = archive(site, f), y = archive(site, f);
                assertEquals(x.steps, y.steps);
                assertEquals(x.clear, y.clear);
            }
        }
        // the same build in every direction: the parts come in the same order whichever way the nexus faces
        List<Integer> north = new ArrayList<>(), east = new ArrayList<>();
        for (Step s : archive(4, FACINGS[0]).steps) north.add(s.part);
        for (Step s : archive(4, FACINGS[1]).steps) east.add(s.part);
        assertEquals(north.size(), east.size());
        List<Integer> northFit = north.subList(archive(4, FACINGS[0]).start(BuildPlan.M_SHELL), north.size()),
            eastFit = east.subList(archive(4, FACINGS[1]).start(BuildPlan.M_SHELL), east.size());
        assertEquals(northFit, eastFit, "the building itself does not depend on the facing");
    }

    @Test
    void stageIndexes() {
        Plan p = archive(4, FACINGS[0]);
        int total = 0;
        for (int s = 0; s < p.stages; s++) {
            assertEquals(total, p.start(s));
            total += p.count(s);
            assertEquals(total, p.end(s));
        }
        assertEquals(p.steps.size(), total);
        assertEquals(p.steps.size(), p.start(p.stages), "past the last stage");
        assertThrows(UnsupportedOperationException.class, () -> p.steps.clear());
        assertThrows(UnsupportedOperationException.class, () -> p.clear.clear());
        assertEquals("module:library@2", BuildPlan.moduleKey(BuildPlan.LIBRARY, 2));
    }
}
