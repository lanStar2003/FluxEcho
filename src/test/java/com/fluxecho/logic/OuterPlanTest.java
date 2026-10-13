package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import com.fluxecho.logic.BuildPlan.Column;
import com.fluxecho.logic.BuildPlan.Plan;
import com.fluxecho.logic.BuildPlan.Step;

/**
 * The outer establish (0.15.0, {@code establish:outer}, M12 §3, {@link BuildPlan#outer}): it grades the annulus and
 * the inner disc's new paving, lays the outer floor cell for cell and stands the outer gate's pylons, for every front;
 * it never takes a cell of the establish floor, leaves every keep-out box alone, and shares the ground with a
 * neighbouring campus without either of them ever planning the other's columns.
 */
class OuterPlanTest {

    private static final int[][] FACINGS = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
    private static final int CX = 1000, Y0 = 70, CZ = -300;

    private static Plan outer(CampusPlan cp, int cx, int cz, int[] f, List<int[]> keepOut, List<int[]> near) {
        return BuildPlan.outer(cp, cx, Y0, cz, f[0], f[1], keepOut, near);
    }

    private static long col(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static List<Step> stage(Plan p, int s) {
        List<Step> out = new ArrayList<>();
        for (Step st : p.steps) if (st.stage == s) out.add(st);
        return out;
    }

    @Test
    void itGradesTheRingAndPavesItCellForCell() {
        for (int[] f : FACINGS) {
            CampusPlan cp = new CampusPlan(Mix.seed(CX, Y0 + 2, CZ));
            Plan p = outer(cp, CX, CZ, f, Collections.emptyList(), null);
            assertEquals(BuildPlan.OUTER, p.key);
            assertEquals(SiteFamily.OUTER_GROUND, p.key);
            assertEquals(BuildPlan.O_STAGES, p.stages);
            assertEquals(0, BuildPlan.establishPhase(p.key), "no establish of a phase");

            Set<Long> columns = new HashSet<>();
            for (Column c : p.clear) {
                assertTrue(columns.add(col(c.x, c.z)), "a column twice");
                assertTrue(c.grade);
                assertEquals(Y0 + 1, c.yFrom);
                assertEquals(Y0 + BuildPlan.CLEAR_UP, c.yTo);
            }
            assertEquals(21935, columns.size(), "the annulus (19,836) and the paving in the inner disc (2,099)");

            Map<Long, Integer> floor = cp.outerFloor();
            assertEquals(3579, floor.size());
            assertEquals(OuterRing.paved(), floor.keySet());
            List<Step> tiles = stage(p, BuildPlan.O_FLOOR);
            assertEquals(3579, tiles.size());
            Set<Long> establish = cp.establishFloor()
                .keySet();
            for (Step s : tiles) {
                assertEquals(Y0, s.y);
                assertEquals(BuildPlan.SOFT, s.kind);
                int[] l = CampusPlan.toLocal(s.x - CX, s.z - CZ, f[0], f[1]);
                long k = CampusPlan.key(l[0], l[1]);
                assertEquals(floor.get(k), Integer.valueOf(s.part));
                assertFalse(establish.contains(k), "never an establish tile");
                assertTrue(columns.contains(col(s.x, s.z)), "every tile on a graded column");
            }

            // four pylons of two pillars and a conduit, each on a gate tile, bottom-up
            List<Step> pylons = stage(p, BuildPlan.O_FIXTURE);
            assertEquals(12, pylons.size());
            for (Step s : pylons) {
                int[] l = CampusPlan.toLocal(s.x - CX, s.z - CZ, f[0], f[1]);
                assertTrue(OuterRing.inOuterGate(l[0], l[1]));
                assertTrue(s.y > Y0 && s.y <= Y0 + BuildPlan.PYLON_PILLARS + 1);
            }
            assertTrue(
                stage(p, BuildPlan.O_CLEAR).isEmpty(),
                "clearing is columns only, the builder's bands keep to their per-tick limits");
        }
    }

    /** A module standing in the way keeps its box: no column, tile nor pylon of the plan lies in it. */
    @Test
    void itLeavesTheKeepOutBoxesAlone() {
        int[] f = FACINGS[1];
        CampusPlan cp = new CampusPlan(Mix.seed(CX, Y0 + 2, CZ));
        // a box over the outer gate (A 88 to 97, R -6 to 6) and one over a street in the inner disc
        List<int[]> boxes = new ArrayList<>();
        for (int[] b : new int[][] { { 88, -6, 97, 6 }, { 30, 17, 40, 21 } }) {
            int[] p = CampusPlan.toWorld(b[0], b[1], f[0], f[1]), q = CampusPlan.toWorld(b[2], b[3], f[0], f[1]);
            boxes.add(
                new int[] { CX + Math.min(p[0], q[0]), CZ + Math.min(p[1], q[1]), CX + Math.max(p[0], q[0]),
                    CZ + Math.max(p[1], q[1]) });
        }
        Plan p = outer(cp, CX, CZ, f, boxes, null);
        Plan all = outer(cp, CX, CZ, f, Collections.emptyList(), null);
        int inBoxes = 0;
        for (Column c : all.clear) if (in(boxes, c.x, c.z)) inBoxes++;
        assertTrue(inBoxes > 100);
        assertEquals(all.clear.size() - inBoxes, p.clear.size());
        for (Column c : p.clear) assertFalse(in(boxes, c.x, c.z));
        for (Step s : p.steps) assertFalse(in(boxes, s.x, s.z), "a step in a keep-out box: " + s);
        assertTrue(stage(p, BuildPlan.O_FIXTURE).isEmpty(), "the pylons stand on the kept gate");
    }

    private static boolean in(List<int[]> boxes, int x, int z) {
        for (int[] b : boxes) if (x >= b[0] && x <= b[2] && z >= b[1] && z <= b[3]) return true;
        return false;
    }

    /**
     * Two campuses placed at the old spacing (144) share the ground between them: neither plans a column the other
     * does, each keeps its whole inner square, and {@link BuildPlan#outerTrimmed} says how much each gave up.
     */
    @Test
    void twoCampusesShareTheGround() {
        int[][] others = { { CX + 144, CZ }, { CX - 100, CZ + 144 }, { CX + 150, CZ + 90 } };
        for (int[] o : others) for (int i = 0; i < FACINGS.length; i++) {
            int[] fa = FACINGS[i], fb = FACINGS[(i + 1) % FACINGS.length];
            CampusPlan ca = new CampusPlan(Mix.seed(CX, Y0 + 2, CZ)), cb = new CampusPlan(Mix.seed(o[0], Y0, o[1]));
            List<int[]> nearA = Collections.singletonList(o), nearB = Collections.singletonList(new int[] { CX, CZ });
            Plan a = outer(ca, CX, CZ, fa, Collections.emptyList(), nearA);
            Plan b = outer(cb, o[0], o[1], fb, Collections.emptyList(), nearB);
            Set<Long> ofA = new HashSet<>();
            for (Column c : a.clear) {
                ofA.add(col(c.x, c.z));
                assertFalse(CampusSplit.inInner(c.x, c.z, o[0], o[1]), "never the other's square");
            }
            for (Column c : b.clear) assertFalse(ofA.contains(col(c.x, c.z)), "both plan " + c.x + "," + c.z);
            Set<Long> tilesA = new HashSet<>();
            for (Step s : a.steps) if (s.y == Y0) tilesA.add(col(s.x, s.z));
            for (Step s : b.steps) if (s.y == Y0) assertFalse(tilesA.contains(col(s.x, s.z)));

            Plan lone = outer(ca, CX, CZ, fa, Collections.emptyList(), null);
            int[] trim = BuildPlan.outerTrimmed(ca, CX, CZ, fa[0], fa[1], nearA);
            assertEquals(lone.clear.size(), trim[1]);
            assertEquals(lone.clear.size() - a.clear.size(), trim[0]);
            assertTrue(trim[0] > 0, "the ring reaches the neighbour");
            assertEquals(0, BuildPlan.outerTrimmed(ca, CX, CZ, fa[0], fa[1], null)[0]);
        }
        // a campus far away takes nothing
        CampusPlan cp = new CampusPlan(3L);
        Plan far = outer(
            cp,
            CX,
            CZ,
            FACINGS[0],
            Collections.emptyList(),
            Collections.singletonList(new int[] { CX + 400, CZ }));
        assertEquals(21935, far.clear.size());
    }

    /** The plan is the same on both sides: the same seed, boxes and neighbours give the same steps. */
    @Test
    void thePlanIsDeterministic() {
        List<int[]> near = Collections.singletonList(new int[] { CX + 160, CZ - 40 });
        Plan a = outer(new CampusPlan(9L), CX, CZ, FACINGS[2], Collections.emptyList(), near);
        Plan b = outer(new CampusPlan(9L), CX, CZ, FACINGS[2], Collections.emptyList(), near);
        assertEquals(a.steps.size(), b.steps.size());
        Map<String, Integer> pa = new HashMap<>();
        for (Step s : a.steps) pa.put(s.x + "," + s.y + "," + s.z, s.part);
        for (Step s : b.steps) assertEquals(pa.get(s.x + "," + s.y + "," + s.z), Integer.valueOf(s.part));
        assertEquals(a.clear, b.clear);
    }

    /**
     * The 建造 page's trim line counts the campuses that take ground (0.15.0, {@link BuildPlan#outerTrimmed}'s third
     * number), not every campus near enough to share it: a campus beyond one that takes ground takes none itself, and
     * the columns given up stay the same; of eight neighbours round a campus at the old spacing, those that own a
     * column of its ring by their own reckoning (a diagonal one whose corner of sites goes to another takes none).
     */
    @Test
    void theTrimLineCountsTheNeighboursThatTakeGround() {
        CampusPlan cp = new CampusPlan(Mix.seed(CX, Y0 + 2, CZ));
        int[] f = FACINGS[0];
        int[] one = BuildPlan
            .outerTrimmed(cp, CX, CZ, f[0], f[1], Collections.singletonList(new int[] { CX + 144, CZ }));
        assertEquals(1, one[2]);
        assertTrue(one[0] > 0);
        for (int[] far : new int[][] { { CX + 200, CZ }, { CX + 219, CZ + 219 } }) {
            List<int[]> two = new ArrayList<>();
            two.add(new int[] { CX + 144, CZ });
            two.add(far);
            int[] t = BuildPlan.outerTrimmed(cp, CX, CZ, f[0], f[1], two);
            assertEquals(1, t[2], "the far one takes nothing");
            assertEquals(one[0], t[0]);
            assertEquals(one[1], t[1]);
        }
        List<int[]> grid = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if (dx != 0 || dz != 0) grid.add(new int[] { CX + 144 * dx, CZ + 144 * dz });
        // each neighbour that owns a column of the ring by its own reckoning ({@link CampusSplit#ours}) counts once
        Set<Long> ring = new HashSet<>(OuterRing.annulus());
        ring.addAll(
            cp.outerFloor()
                .keySet());
        int takers = 0;
        for (int[] n : grid) {
            List<int[]> others = new ArrayList<>(grid);
            others.remove(n);
            others.add(new int[] { CX, CZ });
            boolean takes = false;
            for (long k : ring) {
                int[] d = CampusPlan.toWorld(CampusPlan.keyA(k), CampusPlan.keyR(k), f[0], f[1]);
                takes |= CampusSplit.ours(CX + d[0], CZ + d[1], n[0], n[1], others);
                if (takes) break;
            }
            if (takes) takers++;
        }
        assertTrue(takers >= 7, "the axis neighbours and most diagonal ones: " + takers);
        assertEquals(takers, BuildPlan.outerTrimmed(cp, CX, CZ, f[0], f[1], grid)[2]);
        assertEquals(0, BuildPlan.outerTrimmed(cp, CX, CZ, f[0], f[1], null)[2]);
    }

    /**
     * A building on an outer diagonal keeps its whole site against a neighbour's outer establish (0.15.0, M12 §3: the
     * outer establish never touches another campus's cells or claims): with A at the centre and B 150 away on either
     * side, B's plan holds no column of A's site once A claims it (the split gives a claimed site wholly to its
     * campus); before, B's establish graded 56 columns of it. Where the split alone gives part of A's site away (A's
     * claim not in the registry, B first by its centre, the overlap of A's -x +z site and B's +x +z one), the keep-out
     * boxes the standing building gives B's establish ({@link CampusSplit#strayDiagBoxes}: its whole site) keep every
     * column and tile of B's plan off it.
     */
    @Test
    void aNeighboursOuterEstablishLeavesABuildingsWholeSite() {
        int q = CampusSplit.quadrant(1, 1);
        Set<Long> cells = new HashSet<>();
        for (int[] c : CampusSplit.siteCells(CX, CZ, q)) cells.add(col(c[0], c[1]));
        for (int side : new int[] { 1, -1 }) for (int[] fb : FACINGS) {
            int bx = CX + 150 * side;
            CampusPlan cb = new CampusPlan(Mix.seed(bx, Y0, CZ));
            List<int[]> claimed = Collections.singletonList(new int[] { CX, CZ, 1 << q });
            Plan b = outer(cb, bx, CZ, fb, Collections.emptyList(), claimed);
            for (Column c : b.clear) assertFalse(cells.contains(col(c.x, c.z)), "B grades A's claimed site");
            for (Step s : b.steps) assertFalse(cells.contains(col(s.x, s.z)), "B paves A's claimed site");
        }
        // without the claim and with B first by its centre, B's site wins the overlap: that is what the claim stops
        int west = CampusSplit.quadrant(-1, 1);
        Set<Long> site = new HashSet<>();
        for (int[] c : CampusSplit.siteCells(CX, CZ, west)) site.add(col(c[0], c[1]));
        CampusPlan cb = new CampusPlan(Mix.seed(CX - 150, Y0, CZ));
        List<int[]> unclaimed = Collections.singletonList(new int[] { CX, CZ });
        List<int[]> boxes = CampusSplit.strayDiagBoxes(
            CX - OuterRing.DIAG_CENTRE,
            CZ + OuterRing.DIAG_CENTRE,
            Arrays.asList(new int[] { CX - 150, CZ }, new int[] { CX, CZ }));
        for (int[] fb : FACINGS) {
            Plan open = outer(cb, CX - 150, CZ, fb, Collections.emptyList(), unclaimed);
            int taken = 0;
            for (Column c : open.clear) if (site.contains(col(c.x, c.z))) taken++;
            assertTrue(taken > 0, "an unclaimed site overlapping the first one's goes to the first one");
            // the building standing there, seen as a stray module: its whole site kept out
            Plan kept = outer(cb, CX - 150, CZ, fb, boxes, unclaimed);
            for (Column c : kept.clear) assertFalse(site.contains(col(c.x, c.z)), "B grades A's building's site");
            for (Step s : kept.steps) assertFalse(site.contains(col(s.x, s.z)), "B paves A's building's site");
        }
    }

    /**
     * A neighbour whose core was broken after the ring was laid gives its side back (0.15.0): the regaining job plans
     * exactly the columns that went to it and are the campus's own now, none the ring laid already; with the
     * neighbour still there it has nothing to do.
     */
    @Test
    void aRegainLaysOnlyWhatTheGoneNeighbourHad() {
        int[] f = FACINGS[2];
        CampusPlan cp = new CampusPlan(Mix.seed(CX, Y0 + 2, CZ));
        List<int[]> was = Collections.singletonList(new int[] { CX + 144, CZ - 30 });
        Plan laid = outer(cp, CX, CZ, f, Collections.emptyList(), was);
        Plan lone = outer(cp, CX, CZ, f, Collections.emptyList(), null);
        Plan regain = BuildPlan.outer(cp, CX, Y0, CZ, f[0], f[1], Collections.emptyList(), null, was);
        Set<Long> first = new HashSet<>(), again = new HashSet<>(), all = new HashSet<>();
        for (Column c : laid.clear) first.add(col(c.x, c.z));
        for (Column c : regain.clear) assertTrue(again.add(col(c.x, c.z)));
        for (Column c : lone.clear) all.add(col(c.x, c.z));
        for (long c : again) assertFalse(first.contains(c), "the ring laid it already");
        Set<Long> both = new HashSet<>(first);
        both.addAll(again);
        assertEquals(all, both, "together the whole ring");
        assertEquals(regain.clear.size(), BuildPlan.outerRegain(cp, CX, CZ, f[0], f[1], was, null));
        assertEquals(BuildPlan.outerTrimmed(cp, CX, CZ, f[0], f[1], was)[0], regain.clear.size());
        for (Step s : regain.steps) assertTrue(again.contains(col(s.x, s.z)), "a tile on a regained column");
        assertEquals(0, BuildPlan.outerRegain(cp, CX, CZ, f[0], f[1], was, was), "the neighbour is still there");
        assertEquals(0, BuildPlan.outerRegain(cp, CX, CZ, f[0], f[1], null, null), "laid with no neighbour");
    }

    /**
     * A 0.9.2 hall retired after the outer ring is laid (0.15.0): the outer establish kept out of the hall's box, which
     * on a diagonal slot covers a stretch of the 井 streets, so the retire job lays the outer floor there, and together
     * the two lay the outer floor cell for cell, for every front; without the outer ring laid the retire plan is the
     * 0.14.1 one, step for step; a neighbour's side of the split is left to it.
     */
    @Test
    void aHallRetiredAfterTheOuterRingLaysTheStreetsItStoodOn() {
        for (int[] f : FACINGS) {
            CampusPlan cp = new CampusPlan(Mix.seed(CX, Y0 + 2, CZ));
            int[] off = RingSlots.offset(1, 32, f[0], f[1]);
            int[] k = BuildPlan.moduleKeepOut(new int[] { CX + off[0], Y0 + 1, CZ + off[1] });
            int[] box = { k[0], k[1], k[2], k[3], Y0, Y0 + 12 };
            List<int[]> hall = Collections.singletonList(new int[] { k[0], k[1], k[2], k[3] });
            Plan ring = outer(cp, CX, CZ, f, hall, null), whole = outer(cp, CX, CZ, f, Collections.emptyList(), null);
            String key = BuildPlan.retireKey(1);
            Plan before = BuildPlan.retire(cp, key, CX, Y0, CZ, f[0], f[1], box),
                old = BuildPlan.retire(cp, key, CX, Y0, CZ, f[0], f[1], box, false, null),
                now = BuildPlan.retire(cp, key, CX, Y0, CZ, f[0], f[1], box, true, null);
            assertEquals(before.steps.size(), old.steps.size());
            for (int i = 0; i < old.steps.size(); i++) assertEquals(text(before.steps.get(i)), text(old.steps.get(i)));
            assertEquals(before.clear, old.clear);
            Map<Long, Integer> tiles = new HashMap<>(), want = new HashMap<>();
            for (Step s : stage(ring, BuildPlan.O_FLOOR)) tiles.put(col(s.x, s.z), s.part);
            for (Step s : stage(whole, BuildPlan.O_FLOOR)) want.put(col(s.x, s.z), s.part);
            int added = 0;
            Set<String> hallOwn = new HashSet<>();
            for (Step s : before.steps) hallOwn.add(text(s));
            for (Step s : now.steps) {
                if (hallOwn.contains(text(s)) || s.y != Y0) continue;
                assertEquals(BuildPlan.D_FLOOR, s.stage);
                assertTrue(tiles.put(col(s.x, s.z), s.part) == null, "a cell the ring laid");
                added++;
            }
            assertTrue(added > 0, "the streets in the hall's box");
            assertEquals(want, tiles, "the outer floor cell for cell");
            // a neighbour's side of the split stays its own
            List<int[]> near = Collections.singletonList(new int[] { CX + off[0] * 4, CZ + off[1] * 4 });
            Plan split = BuildPlan.retire(cp, key, CX, Y0, CZ, f[0], f[1], box, true, near);
            for (Step s : split.steps) if (!hallOwn.contains(text(s)))
                assertTrue(CampusSplit.ours(s.x, s.z, CX, CZ, near), "a neighbour's cell");
        }
    }

    private static String text(Step s) {
        return s.x + "," + s.y + "," + s.z + ":" + s.part + "@" + s.stage + "/" + s.kind;
    }

    /**
     * The outer establish paves the 井 streets right up to a standing hall's own ground (0.15.0): it keeps out of the
     * cells the hall's plan clears or lays ({@code ModuleSpec#outerKeepOut}), not the hall's whole site box, whose
     * rectangle took in seven garden cells of the street's edge row on either side of the forecourt and left them
     * grass. For the Echo Archive on sites 2, 4 and 6 and every front: every street cell off the hall's ground gets its
     * tile, and nothing of the ring lies on the hall's ground.
     */
    @Test
    void theStreetsArePavedUpToAStandingHall() {
        com.fluxecho.campus.ModuleSpec archive = com.fluxecho.library.LibraryModule.spec();
        for (int site : new int[] { 2, 4, 6 }) for (int[] f : FACINGS) {
            CampusPlan cp = new CampusPlan(Mix.seed(CX, Y0 + 2, CZ));
            Plan hall = archive.plan(cp, site, CX, Y0, CZ, f[0], f[1]);
            Set<Long> own = new HashSet<>();
            for (Column c : hall.clear) own.add(col(c.x, c.z));
            for (Step s : hall.steps) own.add(col(s.x, s.z));
            List<int[]> exact = archive.outerKeepOut(cp, site, CX, Y0, CZ, f[0], f[1]);
            Set<Long> boxed = new HashSet<>();
            for (int[] b : exact) for (int x = Math.min(b[0], b[2]); x <= Math.max(b[0], b[2]); x++)
                for (int z = Math.min(b[1], b[3]); z <= Math.max(b[1], b[3]); z++) boxed.add(col(x, z));
            assertEquals(own, boxed, "the boxes hold the hall's own ground exactly");
            Plan ring = outer(cp, CX, CZ, f, exact, null);
            Set<Long> tiles = new HashSet<>();
            for (Step s : stage(ring, BuildPlan.O_FLOOR)) tiles.add(col(s.x, s.z));
            for (Column c : ring.clear) assertFalse(own.contains(col(c.x, c.z)), "the ring grades the hall's ground");
            for (Step s : ring.steps) assertFalse(own.contains(col(s.x, s.z)), "the ring lays on the hall's ground");
            int notch = 0;
            for (long k : CampusPlan.streetCells()) {
                int[] d = CampusPlan.toWorld(CampusPlan.keyA(k), CampusPlan.keyR(k), f[0], f[1]);
                long w = col(CX + d[0], CZ + d[1]);
                if (own.contains(w)) continue;
                assertTrue(tiles.contains(w), "a street cell left grass beside the hall on site " + site);
            }
            // the site box's rectangle (what the jobs of 0.10 to 0.14 keep out of) would have left some
            Plan old = outer(cp, CX, CZ, f, archive.keepOut(cp, site, CX, Y0, CZ, f[0], f[1]), null);
            Set<Long> oldTiles = new HashSet<>();
            for (Step s : stage(old, BuildPlan.O_FLOOR)) oldTiles.add(col(s.x, s.z));
            for (long k : CampusPlan.streetCells()) {
                int[] d = CampusPlan.toWorld(CampusPlan.keyA(k), CampusPlan.keyR(k), f[0], f[1]);
                long w = col(CX + d[0], CZ + d[1]);
                if (!own.contains(w) && !oldTiles.contains(w)) notch++;
            }
            if (site == 2) assertTrue(notch > 0, "the rectangle's notch beside the forecourt");
            // and the protection the outer establish gets is the same ground from bedrock to the sky
            for (int[] p : archive.outerProtect(cp, site, CX, Y0, CZ, f[0], f[1])) {
                assertEquals(0, p[1]);
                assertEquals(255, p[4]);
                assertTrue(own.contains(col(p[0], p[2])) && own.contains(col(p[3], p[5])));
            }
        }
    }

    /**
     * A 0.9.2 hall broken by hand after the outer ring was laid round it (0.15.0): its retire job is withdrawn and the
     * module on its site lays only its own ground, so the campus offers the outer establish again limited to the
     * hall's box ({@link BuildPlan#outer} with {@code only}). For the hall on slot 1 and every front the patch lays
     * exactly the outer floor the ring left out in the box, the 井 streets among it, and grades only the box's columns
     * of the ring; together the ring and the patch lay the outer floor cell for cell; the ground a module job keeps is
     * left alone; a neighbour's side of the split stays its own.
     */
    @Test
    void aPatchLaysTheStreetsAVanishedHallKeptTheRingOff() {
        for (int[] f : FACINGS) {
            CampusPlan cp = new CampusPlan(Mix.seed(CX, Y0 + 2, CZ));
            int[] off = RingSlots.offset(1, 32, f[0], f[1]);
            int[] k = BuildPlan.moduleKeepOut(new int[] { CX + off[0], Y0 + 1, CZ + off[1] });
            List<int[]> hall = Collections.singletonList(k);
            Plan ring = outer(cp, CX, CZ, f, hall, null), whole = outer(cp, CX, CZ, f, Collections.emptyList(), null);
            Plan patch = BuildPlan.outer(cp, CX, Y0, CZ, f[0], f[1], Collections.emptyList(), null, null, hall);
            assertEquals(BuildPlan.OUTER, patch.key);
            Map<Long, Integer> laid = new HashMap<>(), want = new HashMap<>(), patched = new HashMap<>();
            for (Step s : stage(ring, BuildPlan.O_FLOOR)) laid.put(col(s.x, s.z), s.part);
            for (Step s : stage(whole, BuildPlan.O_FLOOR)) want.put(col(s.x, s.z), s.part);
            for (Step s : stage(patch, BuildPlan.O_FLOOR)) {
                assertTrue(inBox(k, s.x, s.z), "a tile outside the hall's box");
                assertTrue(patched.put(col(s.x, s.z), s.part) == null);
                assertTrue(laid.put(col(s.x, s.z), s.part) == null, "a tile the ring laid");
            }
            assertEquals(want, laid, "the ring and the patch lay the outer floor cell for cell");
            int streets = 0;
            for (long c : CampusPlan.streetCells()) {
                int[] d = CampusPlan.toWorld(CampusPlan.keyA(c), CampusPlan.keyR(c), f[0], f[1]);
                if (!inBox(k, CX + d[0], CZ + d[1])) continue;
                assertTrue(patched.containsKey(col(CX + d[0], CZ + d[1])), "a street cell in the box left out");
                streets++;
            }
            assertTrue(streets > 0, "the hall's box covers a stretch of the streets");
            Set<Long> graded = new HashSet<>();
            for (Column c : ring.clear) graded.add(col(c.x, c.z));
            int boxColumns = 0;
            for (Column c : whole.clear) if (inBox(k, c.x, c.z)) boxColumns++;
            assertEquals(boxColumns, patch.clear.size(), "the ring's columns in the box");
            for (Column c : patch.clear) {
                assertTrue(inBox(k, c.x, c.z));
                assertFalse(graded.contains(col(c.x, c.z)), "a column the ring graded");
            }
            // the ground of a module job still to run is kept out
            int[] busy = { k[0], k[1], (k[0] + k[2]) / 2, k[3] };
            Plan kept = BuildPlan.outer(cp, CX, Y0, CZ, f[0], f[1], Collections.singletonList(busy), null, null, hall);
            for (Step s : kept.steps) assertFalse(inBox(busy, s.x, s.z), "a tile on a module job's ground");
            for (Column c : kept.clear) assertFalse(inBox(busy, c.x, c.z), "a column of a module job's ground");
            // a neighbour's side of the split stays its own
            List<int[]> near = Collections.singletonList(new int[] { CX + off[0] * 4, CZ + off[1] * 4 });
            Plan split = BuildPlan.outer(cp, CX, Y0, CZ, f[0], f[1], Collections.emptyList(), near, null, hall);
            for (Step s : split.steps) assertTrue(CampusSplit.ours(s.x, s.z, CX, CZ, near), "a neighbour's cell");
        }
        // with no boxes given, the plan is the outer establish's, step for step
        int[] f = FACINGS[1];
        CampusPlan cp = new CampusPlan(Mix.seed(CX, Y0 + 2, CZ));
        Plan a = outer(cp, CX, CZ, f, Collections.emptyList(), null),
            b = BuildPlan.outer(cp, CX, Y0, CZ, f[0], f[1], Collections.emptyList(), null, null, null);
        assertEquals(a.steps.size(), b.steps.size());
        for (int i = 0; i < a.steps.size(); i++) assertEquals(text(a.steps.get(i)), text(b.steps.get(i)));
        assertEquals(a.clear.size(), b.clear.size());
    }

    private static boolean inBox(int[] b, int x, int z) {
        return x >= Math.min(b[0], b[2]) && x <= Math.max(b[0], b[2])
            && z >= Math.min(b[1], b[3])
            && z <= Math.max(b[1], b[3]);
    }
}
