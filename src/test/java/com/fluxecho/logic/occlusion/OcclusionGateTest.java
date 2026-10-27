package com.fluxecho.logic.occlusion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.shape.Form;
import com.fluxecho.logic.shape.Nbhd;
import com.fluxecho.logic.shape.ShapeGeo;

/**
 * The occlusion gate in a fake world (style FINAL §8.4.1, refactor FINAL F06): a piece behind a wall hides, behind a
 * window it shows, behind a louvre it shows where the eye passes between the slats, it never flickers (two blocked
 * rounds to hide, one seen to show), every query between two rounds gives the same answer, a round casts at most 400
 * lines near to far, far pieces are judged every second round, indoor pieces follow the camera, and the depth mode
 * leaves the near pieces to the depth buffer.
 */
class OcclusionGateTest {

    /** A world of cells written by hand. */
    static final class Cells implements SightCells {

        final Map<Long, Integer> kinds = new HashMap<>();
        final Map<Long, List<double[]>> boxes = new HashMap<>();
        final Map<Long, Integer> heights = new HashMap<>();

        static long key(int x, int y, int z) {
            return ((long) x * 73856093L) ^ ((long) y * 19349663L) ^ ((long) z * 83492791L);
        }

        Cells solid(int x, int y, int z) {
            kinds.put(key(x, y, z), SOLID);
            return this;
        }

        Cells clear(int x, int y, int z) {
            kinds.remove(key(x, y, z));
            return this;
        }

        Cells shape(int x, int y, int z, Form form) {
            List<double[]> b = ShapeGeo.occluders(form, Nbhd.ISOLATED);
            if (b.isEmpty()) kinds.remove(key(x, y, z));
            else {
                kinds.put(key(x, y, z), BOXES);
                boxes.put(key(x, y, z), b);
            }
            return this;
        }

        /** A wall of solid cells across x from {@code x0} to {@code x1}, y 0 to {@code top}, at {@code z}. */
        Cells wall(int x0, int x1, int top, int z) {
            for (int x = x0; x <= x1; x++) for (int y = 0; y <= top; y++) solid(x, y, z);
            return this;
        }

        @Override
        public int kind(int x, int y, int z) {
            Integer k = kinds.get(key(x, y, z));
            return k == null ? CLEAR : k;
        }

        @Override
        public List<double[]> boxes(int x, int y, int z) {
            List<double[]> b = boxes.get(key(x, y, z));
            return b == null ? Collections.emptyList() : b;
        }

        @Override
        public int height(int x, int z) {
            Integer h = heights.get(key(x, 0, z));
            return h == null ? Integer.MIN_VALUE : h;
        }
    }

    private static final double[] EYE = { 0.5, 1.62, 0.5 };

    /** A square piece facing the eye along z, half size 0.75, at (x, y, z). */
    private static GatePiece panel(long id, double x, double y, double z) {
        return GatePiece.panel(id, new double[] { x, y, z }, new double[] { 0.75, 0, 0 }, new double[] { 0, 0.75, 0 });
    }

    private static GateCore.Report round(GateCore g, Cells w, double[] eye, GatePiece... pieces) {
        List<GatePiece> l = new ArrayList<>();
        Collections.addAll(l, pieces);
        return g.round(l, eye, w, GateCore.Mode.GATE, null);
    }

    @Test
    void aPieceBehindAWallHidesAfterTwoBlockedRounds() {
        Cells w = new Cells().wall(-4, 4, 4, 8);
        GateCore g = new GateCore();
        GatePiece behind = panel(1, 0.5, 3, 10.5), before = panel(2, 0.5, 1, 5.5);
        assertTrue(g.visible(1), "a piece the gate has not judged is shown");
        round(g, w, EYE, behind, before);
        assertTrue(g.visible(1), "one blocked round does not hide a piece yet");
        round(g, w, EYE, behind, before);
        assertFalse(g.visible(1), "two blocked rounds in a row hide it");
        assertTrue(g.visible(2), "the piece before the wall stays shown");
    }

    @Test
    void aPieceBehindAWindowShows() {
        Cells w = new Cells().wall(-4, 4, 4, 8);
        // a window two wide and two high in the wall: glass and a window pane do not block the eye
        for (int x = 2; x <= 3; x++) for (int y = 1; y <= 2; y++) w.clear(x, y, 8);
        w.shape(2, 1, 8, Form.WINDOW);
        GateCore g = new GateCore();
        GatePiece window = panel(3, 4.0, 2, 10.5);
        for (int i = 0; i < 5; i++) round(g, w, EYE, window);
        assertTrue(g.visible(3), "the eye sees it through the window");
    }

    @Test
    void aLouvreHidesOnlyWhereItsSlatsAre() {
        Cells w = new Cells();
        for (int x = -3; x <= 3; x++) for (int y = 0; y <= 5; y++) w.shape(x, y, 8, Form.LOUVRE);
        // a horizontal line at 0.6 of a cell passes between the slats (0.25-0.44 and 0.75-0.94)
        double[] gapEye = { 0.5, 2.6, 0.5 }, slatEye = { 0.5, 2.3, 0.5 };
        GateCore g = new GateCore();
        GatePiece between = GatePiece.of(4, new double[] { 0.5, 2.6, 12.5 }),
            onSlat = GatePiece.of(5, new double[] { 0.5, 2.3, 12.5 });
        for (int i = 0; i < 3; i++) {
            g.round(Collections.singletonList(between), gapEye, w, GateCore.Mode.GATE, null);
        }
        assertTrue(g.visible(4), "between the slats the piece shows");
        GateCore h = new GateCore();
        for (int i = 0; i < 3; i++) h.round(Collections.singletonList(onSlat), slatEye, w, GateCore.Mode.GATE, null);
        assertFalse(h.visible(5), "behind a slat it hides");
        // a piece partly behind a slat: one of its points between them is enough
        GateCore k = new GateCore();
        GatePiece partly = GatePiece.of(6, new double[] { 0.5, 2.3, 12.5 }, new double[] { 0.5, 2.6, 12.5 });
        for (int i = 0; i < 3; i++) k.round(Collections.singletonList(partly), slatEye, w, GateCore.Mode.GATE, null);
        assertTrue(k.visible(6), "partly behind the louvre, the piece shows");
    }

    @Test
    void theEyeSeesBetweenRibsAndPastAColumnButNotThroughThem() {
        Cells w = new Cells();
        for (int y = 0; y <= 5; y++) {
            w.shape(0, y, 8, Form.RIB);
            w.shape(2, y, 8, Form.RIB);
        }
        // straight down the rib's core: blocked; through the gap beside its core: seen
        assertTrue(SightRay.blocked(w, new double[] { 0.5, 2, 0.5 }, new double[] { 0.5, 2, 12.5 }));
        assertFalse(SightRay.blocked(w, new double[] { 1.5, 2, 0.5 }, new double[] { 1.5, 2, 12.5 }));
        assertFalse(SightRay.blocked(w, new double[] { 0.1, 2, 0.5 }, new double[] { 0.1, 2, 12.5 }));
        w.shape(5, 1, 8, Form.COLUMN);
        assertTrue(SightRay.blocked(w, new double[] { 5.5, 1.5, 0.5 }, new double[] { 5.5, 1.5, 12.5 }));
        assertFalse(SightRay.blocked(w, new double[] { 5.05, 1.5, 0.5 }, new double[] { 5.05, 1.5, 12.5 }));
    }

    @Test
    void itDoesNotFlicker() {
        Cells w = new Cells().wall(-4, 4, 4, 8);
        GatePiece p = panel(7, 0.5, 2, 10.5);
        // from the far side of the wall the piece is in plain sight
        double[] open = { 0.5, 1.62, 15.5 };
        GateCore g = new GateCore();
        // blocked, seen, blocked, seen ...: never two blocked rounds in a row, never hidden
        for (int i = 0; i < 6; i++) {
            round(g, w, i % 2 == 0 ? EYE : open, p);
            assertTrue(g.visible(7), "round " + i);
        }
        round(g, w, EYE, p);
        round(g, w, EYE, p);
        assertFalse(g.visible(7));
        round(g, w, open, p);
        assertTrue(g.visible(7), "one round seen shows it again");
    }

    @Test
    void everyQueryBetweenTwoRoundsGivesTheSameAnswer() {
        Cells w = new Cells().wall(-4, 4, 4, 8);
        GateCore g = new GateCore();
        GatePiece p = panel(8, 0.5, 2, 10.5);
        round(g, w, EYE, p);
        round(g, w, EYE, p);
        boolean first = g.visible(8);
        for (int i = 0; i < 100; i++) assertEquals(first, g.visible(8), "a frame drawing it many times asks the same");
    }

    @Test
    void aRoundCastsAtMostFourHundredLinesNearToFar() {
        Cells w = new Cells().wall(-60, 60, 30, 8);
        List<GatePiece> pieces = new ArrayList<>();
        for (int i = 0; i < 300; i++) pieces.add(panel(100 + i, (i % 50) - 25, 1 + (i / 50), 10.5 + (i % 7)));
        GateCore g = new GateCore();
        GateCore.Report r = g.round(pieces, EYE, w, GateCore.Mode.GATE, null);
        assertTrue(r.rays <= GateCore.MAX_RAYS, r.rays + " lines");
        assertEquals(GateCore.MAX_RAYS, r.rays, "every blocked piece costs its five points until the budget ends");
        assertEquals(GateCore.MAX_RAYS / 5, r.judged);
        assertEquals(300 - GateCore.MAX_RAYS / 5, r.deferred);
        // the nearest are judged first: after two rounds the nearest eighty are hidden
        g.round(pieces, EYE, w, GateCore.Mode.GATE, null);
        List<GatePiece> byDistance = new ArrayList<>(pieces);
        byDistance.sort((a, b) -> Double.compare(a.distance(EYE), b.distance(EYE)));
        for (int i = 0; i < GateCore.MAX_RAYS / 5; i++) assertFalse(g.visible(byDistance.get(i).id));
        assertTrue(g.visible(byDistance.get(byDistance.size() - 1).id), "the farthest waits its turn, shown");
        assertTrue(r.cells / (double) Math.max(1, r.rays) < 40, "the lines walk fewer than 40 cells on average");
    }

    @Test
    void farPiecesAreJudgedEverySecondRound() {
        Cells w = new Cells().wall(-60, 60, 30, 80);
        GatePiece far = panel(9, 0.5, 2, 90.5);
        GateCore g = new GateCore();
        GateCore.Report r1 = round(g, w, EYE, far);
        assertEquals(1, r1.skippedFar, "round 1 leaves the far piece");
        GateCore.Report r2 = round(g, w, EYE, far);
        assertEquals(1, r2.judged);
        round(g, w, EYE, far);
        assertTrue(g.visible(9), "one judged round");
        round(g, w, EYE, far);
        assertFalse(g.visible(9), "the second judged round hides it");
    }

    @Test
    void depthModeLeavesTheNearPiecesToTheDepthBuffer() {
        Cells w = new Cells().wall(-60, 60, 30, 8)
            .wall(-60, 60, 30, 80);
        GatePiece near = panel(10, 0.5, 2, 10.5), far = panel(11, 0.5, 2, 90.5);
        GateCore g = new GateCore();
        List<GatePiece> both = new ArrayList<>();
        both.add(near);
        both.add(far);
        for (int i = 0; i < 6; i++) g.round(both, EYE, w, GateCore.Mode.DEPTH, null);
        assertTrue(g.visible(10), "near: the depth buffer hides it, the gate keeps it");
        assertFalse(g.visible(11), "far: the gate still hides it behind the building");
    }

    @Test
    void indoorPiecesFollowTheCamera() {
        Cells w = new Cells();
        int[] room = { 10, 0, 10, 20, 8, 20 };
        GatePiece in = GatePiece.indoor(12, room, new double[] { 15, 3, 15 });
        GateCore g = new GateCore();
        GateCore.Report r = g.round(Collections.singletonList(in), EYE, w, GateCore.Mode.GATE, null);
        assertFalse(g.visible(12), "outside its box the indoor piece is not drawn");
        assertEquals(0, r.rays, "and costs no line");
        g.round(Collections.singletonList(in), new double[] { 12, 2, 12 }, w, GateCore.Mode.GATE, null);
        assertTrue(g.visible(12));
    }

    @Test
    void theCoarseTestSkipsLinesThatCrossNoBuilding() {
        Cells w = new Cells().wall(-4, 4, 4, 8);
        int[] wallBox = { -4, 0, 8, 4, 4, 8 };
        GatePiece open = panel(13, 20.5, 2, 10.5), behind = panel(14, 0.5, 3, 10.5).host(wallBox);
        List<GatePiece> both = new ArrayList<>();
        both.add(open);
        both.add(behind);
        GateCore g = new GateCore();
        GateCore.Report r = g.round(both, EYE, w, GateCore.Mode.GATE, new ArrayList<>());
        assertEquals(1, r.coarse, "the open piece's first line crosses no building: seen without a cast");
        assertEquals(5, r.rays, "the piece behind its building's wall casts all five");
        g.round(both, EYE, w, GateCore.Mode.GATE, new ArrayList<>());
        assertTrue(g.visible(13));
        assertFalse(g.visible(14));
        // a piece under the ground is always cast
        w.heights.put(Cells.key(20, 0, 10), 50);
        GateCore h = new GateCore();
        GateCore.Report under = h.round(Collections.singletonList(open), EYE, w, GateCore.Mode.GATE, new ArrayList<>());
        assertEquals(0, under.coarse);
        assertEquals(1, under.rays);
    }

    @Test
    void theEndsOfALineNeverBlockIt() {
        Cells w = new Cells().solid(0, 1, 0)
            .solid(0, 2, 10);
        // the eye in a solid cell and the target in one: a light on a wall's face is not hidden by the wall it is on
        assertFalse(SightRay.blocked(w, new double[] { 0.5, 1.5, 0.5 }, new double[] { 0.5, 2.5, 10.5 }));
        w.solid(0, 2, 5);
        assertTrue(SightRay.blocked(w, new double[] { 0.5, 1.5, 0.5 }, new double[] { 0.5, 2.5, 10.5 }));
        // past 160 blocks a point counts as seen
        w.wall(-5, 5, 5, 100);
        assertFalse(SightRay.blocked(w, EYE, new double[] { 0.5, 1.62, 170.5 }));
    }

    @Test
    void aStateGoesWhenItsPieceDoes() {
        Cells w = new Cells().wall(-4, 4, 4, 8);
        GateCore g = new GateCore();
        GatePiece p = panel(15, 0.5, 2, 10.5);
        round(g, w, EYE, p);
        round(g, w, EYE, p);
        assertFalse(g.visible(15));
        round(g, w, EYE);
        assertTrue(g.visible(15), "a piece no renderer drew last is forgotten, and shows when it comes back");
    }
}
