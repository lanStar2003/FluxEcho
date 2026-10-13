package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fluxecho.campus.BlockFitting;
import com.fluxecho.frame.BlockFrame;

/**
 * Every move the city kit's walk ({@link ShapeKit.Shape#walk}) takes, in every city shape, checked against the
 * collision boxes the blocks really have ({@link BlockFrame#box}, {@link BlockFitting#box} and the rail's
 * {@link BlockFitting#RAIL_HEIGHT}; every deck part, grass, dirt, the lift pad and the module core are whole cubes,
 * water and air have none), so a stair the kit passes can never again cost the head in game. 0.11.0 to 0.14.0's walk
 * passed the Eco Dome's crown stair, whose fourth step put the head into the crown island's grass (0.14.1).
 * <p>
 * The player is 1.8 high and steps up half a block. A move from a stand {@code s} to a neighbouring column's stand
 * {@code s2} fits when the body crossing at {@code max(s, s2)} meets no box in either column between that column's own
 * stand and its head: a step up is taken while the body is still over the lower column, a drop falls through the
 * lower column. Every box counts as filling its column across (a narrow post or a rail's centre post as much as a
 * wall), which errs on the strict side. Each shape is checked as built and as formed (the cells its module dissolves
 * and lets players through have no box then), from the generator's own starts.
 */
class KitClimbTest {

    /** The player's height and step, the walk's longest drop, and the slack of the box comparisons. */
    private static final double PLAYER_H = 1.8, STEP = 0.5, DROP = 3, EPS = 1e-6;
    /** The columns round the box the walk takes in, as every walk test does. */
    private static final int MARGIN = 2;

    /** One city shape to walk: its shape, the generator's starts, the characters that pass once it formed. */
    private static final class Case {

        final String name;
        final ShapeKit.Shape shape;
        final List<int[]> starts;
        final String passes;

        Case(String name, ShapeKit.Shape shape, List<int[]> starts, String passes) {
            this.name = name;
            this.shape = shape;
            this.starts = starts;
            this.passes = passes;
        }
    }

    /**
     * Every shape of the city with a walk: the eleven modules (the Dome Generator's pod as its generator draws it and
     * as it is built; 0.15.0 the Sophon Array and the Holo Sand Table), the shallower Tidal Wells over low ground
     * (0.14.1), and the Tidal Well's later shapes, the deep root and the ley core, from their top pads.
     */
    private static List<Case> cases() {
        List<Case> out = new ArrayList<>();
        out.add(new Case("eco_dome", EcoDomeShape.SHAPE, EcoDomeShape.walkStarts(), EcoDomeShape.PASSES));
        out.add(
            new Case(
                "dual_foil_vault",
                DualFoilVaultShape.SHAPE,
                DualFoilVaultShape.walkStarts(),
                DualFoilVaultShape.PASSES));
        out.add(
            new Case("arcane_court", ArcaneCourtShape.SHAPE, ArcaneCourtShape.walkStarts(), ArcaneCourtShape.PASSES));
        out.add(
            new Case(
                "blood_covenant",
                BloodCovenantShape.SHAPE,
                BloodCovenantShape.walkStarts(),
                BloodCovenantShape.PASSES));
        out.add(
            new Case(
                "hunting_ground",
                HuntingGroundShape.SHAPE,
                HuntingGroundShape.walkStarts(),
                HuntingGroundShape.PASSES));
        out.add(
            new Case(
                "mana_fountain",
                ManaFountainShape.SHAPE,
                ManaFountainShape.walkStarts(),
                ManaFountainShape.PASSES));
        out.add(
            new Case(
                "resonance_gate",
                ResonanceGateShape.SHAPE,
                ResonanceGateShape.walkStarts(),
                ResonanceGateShape.PASSES));
        // the outer diagonals' buildings (0.15.0): the array's hall, the table's double ramp up to its gallery
        out.add(
            new Case("sophon_array", SophonArrayShape.SHAPE, SophonArrayShape.walkStarts(), SophonArrayShape.PASSES));
        out.add(new Case("holo_table", HoloTableShape.SHAPE, HoloTableShape.walkStarts(), HoloTableShape.PASSES));
        out.add(
            new Case("dome_pod", DomeGeneratorShape.RAW, DomeGeneratorShape.walkStarts(), DomeGeneratorShape.PASSES));
        out.add(
            new Case(
                "dome_pod_built",
                DomeGeneratorShape.SHAPE,
                DomeGeneratorShape.walkStarts(),
                DomeGeneratorShape.PASSES));
        out.add(new Case("tidal_well", RootShape.SHAPE, RootShape.walkStarts(), RootShape.PASSES));
        // the shallower wells over low ground (0.14.1); the zenith's pod is the same at every sky datum, only its
        // legs (which dissolve, and nobody walks) are shorter
        for (RootShape.Well w : RootShape.wells()) if (w.depth != RootShape.DEPTH)
            out.add(new Case("tidal_well_" + w.depth, w.shape, RootShape.walkStarts(), RootShape.PASSES));
        ShapeKit.Shape deep = RootShape.deepRoot(), ley = RootShape.leyCore();
        out.add(new Case("deep_root", deep, topPad(deep), RootShape.PASSES));
        out.add(new Case("ley_core", ley, topPad(ley), RootShape.PASSES));
        return out;
    }

    /** The generator's start for the root's shapes: on the highest lift pad. */
    private static List<int[]> topPad(ShapeKit.Shape s) {
        int[] top = null;
        for (int[] p : s.lifts) if (top == null || p[1] > top[1]) top = p;
        return Collections.singletonList(new int[] { top[0], top[2], 2 * (top[1] + 1) });
    }

    // ---- the real boxes

    /**
     * The vertical extent {lo, hi} of a legend character's collision inside its cell, or null for none: read from the
     * blocks' own boxes.
     */
    static double[] extent(char ch) {
        if (ch == ShapeKit.NONE || ch == ShapeKit.AIR || ch == '=') return null;
        if (ch == ShapeKit.CONTROLLER) return new double[] { 0, 1 };
        int code = ShapeKit.partOf(ch);
        if (code == Parts.GRASS || code == Parts.DIRT) return new double[] { 0, 1 };
        if (Parts.isFrame(code)) {
            float[] b = BlockFrame.box(Parts.meta(code));
            return new double[] { b[1], b[4] };
        }
        if (Parts.isFitting(code)) {
            int meta = Parts.meta(code);
            if (meta == Parts.F_RAIL) return new double[] { 0, BlockFitting.RAIL_HEIGHT };
            float[] b = BlockFitting.box(meta);
            return new double[] { b[1], b[4] };
        }
        // the deck parts are whole cubes: BlockDeck keeps the block's own box
        if (Parts.isDeck(code)) return new double[] { 0, 1 };
        throw new IllegalArgumentException("no box for '" + ch + "'");
    }

    /** A shape's columns as the boxes the player meets, built or formed. */
    private static final class Boxes {

        final ShapeKit.Shape s;
        final String passes;
        final Map<Long, List<double[]>> cols = new HashMap<>();

        Boxes(ShapeKit.Shape s, String passes) {
            this.s = s;
            this.passes = passes;
        }

        /** The boxes of a column, {lo, hi, x, y, z, character}. */
        List<double[]> column(int x, int z) {
            return cols.computeIfAbsent(ShapeKit.key(x, z), k -> make(x, z));
        }

        private List<double[]> make(int x, int z) {
            List<double[]> out = new ArrayList<>();
            boolean in = x >= 0 && x < s.W && z >= 0 && z < s.D;
            // the graded ground round the box, and the paved apron at y0 inside it, where the shape leaves y0 empty
            if (!in && s.groundOutside) out.add(new double[] { -50, 1, x, 0, z, 'g' });
            for (int y = 0; y < s.H && in; y++) {
                char ch = s.get(x, y, z);
                if (y == 0 && ch == ShapeKit.NONE && s.groundOutside) {
                    out.add(new double[] { 0, 1, x, 0, z, '_' });
                    continue;
                }
                if (passes.indexOf(ch) >= 0) continue;
                double[] e = extent(ch);
                if (e != null) out.add(new double[] { y + e[0], y + e[1], x, y, z, ch });
            }
            for (int[] d : s.decor) {
                if (d[0] != x || d[2] != z) continue;
                double[] e = extent((char) d[3]);
                if (e != null) out.add(new double[] { d[1] + e[0], d[1] + e[1], x, d[1], z, d[3] });
            }
            return out;
        }

        /** The boxes of the column inside the open interval (lo, hi). */
        List<double[]> blockers(int x, int z, double lo, double hi) {
            List<double[]> out = new ArrayList<>();
            for (double[] b : column(x, z)) if (b[0] < hi - EPS && b[1] > lo + EPS) out.add(b);
            return out;
        }

        /** Where a player can stand in the column: on the top of a box with the body's height clear over it. */
        List<Double> stands(int x, int z) {
            List<Double> out = new ArrayList<>();
            for (double[] b : column(x, z)) if (blockers(x, z, b[1], b[1] + PLAYER_H).isEmpty()) out.add(b[1]);
            return out;
        }

        /**
         * The real stand a kit state stands at: its own height when a box tops there, else the highest stand up to
         * half a block lower (a seat or a ring the kit counts as a whole block); null when there is none.
         */
        Double realOf(int x, int z, int h) {
            double want = h / 2.0;
            Double best = null;
            for (double v : stands(x, z)) {
                if (Math.abs(v - want) < EPS) return v;
                if (v >= want - STEP - EPS && v <= want + EPS && (best == null || v > best)) best = v;
            }
            return best;
        }

        /**
         * Why the body standing at {@code s} over {@code (x, z)} cannot walk to {@code s2} over {@code (x2, z2)}, or
         * null when it can: each column clear from its own stand to the head of the body at the higher one.
         */
        String move(int x, int z, double s, int x2, int z2, double s2) {
            if (s2 - s > STEP + EPS) return "a step of " + (s2 - s);
            if (s - s2 > DROP + EPS) return "a drop of " + (s - s2);
            double head = Math.max(s, s2) + PLAYER_H;
            List<double[]> hit = new ArrayList<>(blockers(x, z, s, head));
            hit.addAll(blockers(x2, z2, s2, head));
            if (hit.isEmpty()) return null;
            StringBuilder b = new StringBuilder("blocked by");
            for (double[] h : hit) b.append(
                String.format(
                    " '%c' at %d,%d,%d [%.3f..%.3f]",
                    (char) h[5],
                    (int) h[2],
                    (int) h[3],
                    (int) h[4],
                    h[0],
                    h[1]));
            return b.toString();
        }
    }

    /**
     * What the kit's walk takes from every state it reaches: {x, z, h, x2, z2, h2} for every move to a neighbouring
     * column it accepts (the light lifts' rides between pads of one column are not walking).
     */
    private static List<int[]> kitMoves(ShapeKit.Shape s, Set<Long> reached) {
        List<int[]> out = new ArrayList<>();
        Map<Long, List<Integer>> fl = new HashMap<>();
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (long st : reached) {
            int x = ShapeKit.Shape.stateX(st), z = ShapeKit.Shape.stateZ(st), h = ShapeKit.Shape.stateH(st);
            for (int[] d : dirs) {
                int nx = x + d[0], nz = z + d[1];
                if (nx < -MARGIN || nx >= s.W + MARGIN || nz < -MARGIN || nz >= s.D + MARGIN) continue;
                for (int h2 : fl.computeIfAbsent(ShapeKit.key(nx, nz), k -> s.floors(nx, nz))) {
                    if (h2 - h > 1 || h - h2 > 6) continue;
                    if (s.crosses(x, z, h, nx, nz, h2)) out.add(new int[] { x, z, h, nx, nz, h2 });
                }
            }
        }
        return out;
    }

    /** The moves of the kit's walk that the real boxes refuse, as text; checks each move is in the reached set too. */
    private static List<String> refused(ShapeKit.Shape s, List<int[]> starts, String passes, int[] count) {
        Set<Long> reached = s.walk(starts, MARGIN);
        Boxes boxes = new Boxes(s, passes);
        List<String> bad = new ArrayList<>();
        for (int[] m : kitMoves(s, reached)) {
            assertTrue(reached.contains(ShapeKit.Shape.state(m[3], m[4], m[5])), "the walk took every move it accepts");
            count[0]++;
            if (m[2] != m[5]) count[1]++;
            Double a = boxes.realOf(m[0], m[1], m[2]), b = boxes.realOf(m[3], m[4], m[5]);
            String why;
            if (a == null || b == null) why = "no real stand (" + a + ", " + b + ")";
            else why = boxes.move(m[0], m[1], a, m[3], m[4], b);
            if (why != null) bad.add(
                String
                    .format("(%d,%d @%.1f) -> (%d,%d @%.1f): %s", m[0], m[1], m[2] / 2.0, m[3], m[4], m[5] / 2.0, why));
        }
        return bad;
    }

    @Test
    void everyMoveTheKitTakesFitsTheRealBoxes() {
        int climbs = 0;
        for (Case c : cases()) for (boolean formed : new boolean[] { false, true }) {
            int[] count = new int[2];
            List<String> bad = refused(c.shape, c.starts, formed ? c.passes : "", count);
            String what = c.name + (formed ? " formed" : " built");
            assertTrue(bad.isEmpty(), what + ": " + bad.size() + " moves the kit takes cost the head: " + bad);
            assertTrue(count[0] > 100, what + " walks somewhere");
            climbs += count[1];
        }
        // the moves that change height (the Arcane Court has none: its floors are level)
        assertTrue(climbs > 1000, "the city's stairs, ramps and terraces: " + climbs + " moves up or down");
    }

    /** Every tread of every shape is stood on: a stair the walk cannot climb is a stair nobody can (0.14.1). */
    @Test
    void everyTreadIsStoodOn() {
        int treads = 0;
        for (Case c : cases()) {
            ShapeKit.Shape s = c.shape;
            List<int[]> idle = s.idleTreads(s.walk(c.starts, MARGIN));
            List<String> where = new ArrayList<>();
            for (int[] t : idle) where.add(Arrays.toString(t));
            assertTrue(idle.isEmpty(), c.name + ": treads nobody stands on " + where);
            for (int y = 0; y < s.H; y++) for (int z = 0; z < s.D; z++) for (int x = 0; x < s.W; x++) {
                char ch = s.get(x, y, z);
                if (ch == 't' || ch == '}') treads++;
            }
        }
        assertTrue(treads > 200, "the city's stairs: " + treads + " treads");
    }

    /** The boxes the check reads are the blocks' own (BlockFitting, BlockFrame, BlockDeck), part by part. */
    @Test
    void theBoxesAreTheBlocks() {
        assertArrayEquals(new double[] { 0, 0.5 }, extent('t'), 1e-9);
        assertArrayEquals(new double[] { 0, 0.5 }, extent('}'), 1e-9);
        assertArrayEquals(new double[] { 0, 1.5 }, extent('r'), 1e-9);
        assertArrayEquals(new double[] { 0, 1.5 }, extent('|'), 1e-9);
        assertArrayEquals(new double[] { 0, 10 / 16.0 }, extent('u'), 1e-9);
        assertArrayEquals(new double[] { 0, 12 / 16.0 }, extent('h'), 1e-9);
        assertArrayEquals(new double[] { 0, 0.5 }, extent('Z'), 1e-9);
        assertArrayEquals(new double[] { 5 / 16.0, 11 / 16.0 }, extent('R'), 1e-9);
        for (char ch : "FBgPCKAdWLwnQGqopc^~,;.{[!/$#e:%k*".toCharArray())
            assertArrayEquals(new double[] { 0, 1 }, extent(ch), 1e-9, String.valueOf(ch));
        assertNull(extent(' '));
        assertNull(extent('-'));
        assertNull(extent('='));
    }

    /**
     * The Eco Dome as 0.11.0 to 0.14.0 built it, with the crown deck's grass at (12, 11, 22): the real boxes refuse the
     * crown stair's fourth step (stand 9 over (12, 22) to stand 9.5 over (12, 21)), the head meeting the grass at 11
     * on its way to 11.3, and the walk refuses it too. Walked as it is now, that dome reaches 1,172 states and leaves
     * the stair's three top treads idle; the dome as it is built now reaches 1,179.
     */
    @Test
    void theOldCrownStepIsRefused() {
        ShapeKit.Shape now = EcoDomeShape.SHAPE;
        ShapeKit.Shape old = new ShapeKit.Shape("eco_dome_0140", now.W, now.H, now.D);
        for (int y = 0; y < now.H; y++)
            for (int z = 0; z < now.D; z++) for (int x = 0; x < now.W; x++) old.set(x, y, z, now.get(x, y, z));
        old.decor.addAll(now.decor);
        old.lifts.addAll(now.lifts);
        old.set(12, EcoDomeShape.CROWN_Y, 22, ',');

        Boxes boxes = new Boxes(old, "");
        String why = boxes.move(12, 22, 9.0, 12, 21, 9.5);
        assertTrue(why != null && why.contains("',' at 12,11,22"), String.valueOf(why));
        assertNull(new Boxes(now, "").move(12, 22, 9.0, 12, 21, 9.5), "the opened deck lets the head through");
        assertFalse(old.crosses(12, 22, 18, 12, 21, 19));
        assertTrue(now.crosses(12, 22, 18, 12, 21, 19));

        Set<Long> reached = old.walk(EcoDomeShape.walkStarts(), MARGIN);
        assertEquals(1172, reached.size());
        List<int[]> idle = old.idleTreads(reached);
        assertEquals(3, idle.size());
        assertArrayEquals(new int[] { 12, 9, 21 }, idle.get(0));
        assertArrayEquals(new int[] { 13, 10, 20 }, idle.get(1));
        assertArrayEquals(new int[] { 14, 11, 21 }, idle.get(2));
        assertEquals(
            1179,
            now.walk(EcoDomeShape.walkStarts(), MARGIN)
                .size());
        // and the real boxes find no other move of today's walk to refuse in it
        assertEquals(0, refused(old, EcoDomeShape.walkStarts(), "", new int[2]).size());
    }
}
