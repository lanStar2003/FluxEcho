package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

/**
 * The Echo Hunting Ground walked as its generator's self-check walks it ({@link ShapeKit.Shape#walk}): from outside the
 * four chamfer portals, in half blocks, half a block up at most, the head clear. It reaches the controller on the
 * emitter at eye height and all twelve consoles, climbs every terrace from 1.5 to 4 without a jump, in exactly the
 * generator's number of standing states, and never comes near the velarium players pass through once the ground forms.
 */
class HuntingGroundWalkTest {

    private static final Set<Long> REACHED = HuntingGroundShape.SHAPE.walk(HuntingGroundShape.walkStarts(), 2);

    @Test
    void theControllerIsAtEyeHeight() {
        assertTrue(HuntingGroundShape.SHAPE.controllerReach(REACHED));
    }

    @Test
    void everyUnitCanBeReached() {
        List<String> missing = HuntingGroundShape.SHAPE.unitReach(REACHED);
        assertTrue(missing.isEmpty(), String.valueOf(missing));
    }

    @Test
    void asManyStatesAsTheGeneratorReaches() {
        assertEquals(740, REACHED.size());
        ShapeKit.Summary s = ShapeKit.summary(HuntingGroundShape.SHAPE, HuntingGroundShape.walkStarts());
        assertTrue(s.controller);
        assertTrue(s.symmetric);
        assertTrue(s.unreachable.isEmpty());
        assertEquals(740, s.reached);
    }

    /**
     * The seating is walked everywhere without a jump: someone stands on every terrace, half a block apart from the
     * floor (h 2, in half blocks) up to the top terrace and the bridges (h 8, one stands at 4), in the generator's
     * numbers.
     */
    @Test
    void everyTerraceIsWalked() {
        Map<Integer, Integer> byHeight = new TreeMap<>();
        for (long st : REACHED) byHeight.merge(ShapeKit.Shape.stateH(st), 1, Integer::sum);
        Map<Integer, Integer> want = new TreeMap<>();
        int[][] pairs = { { 2, 504 }, { 3, 24 }, { 4, 28 }, { 5, 36 }, { 6, 60 }, { 7, 44 }, { 8, 44 } };
        for (int[] p : pairs) want.put(p[0], p[1]);
        assertEquals(want, byHeight);
    }

    /** Every portal is walked through: someone stands in each one's opening at ground level. */
    @Test
    void everyPortalIsWalkedThrough() {
        int[][] portals = { { 4, 4 }, { 20, 4 }, { 4, 20 }, { 20, 20 } };
        for (int[] p : portals) {
            boolean in = false;
            for (long st : REACHED) {
                if (ShapeKit.Shape.stateX(st) == p[0] && ShapeKit.Shape.stateZ(st) == p[1]
                    && ShapeKit.Shape.stateH(st) == 2) in = true;
            }
            assertTrue(in, "portal " + p[0] + "," + p[1]);
        }
    }

    /**
     * The cells players pass through once the ground forms ({@link HuntingGroundShape#PASSES}: the velarium's ring at
     * y9) lie out of everyone's reach: no standing state the walk reaches has one beside it or under it at the height
     * of its floor, its feet or its head; the highest anyone stands is the top terrace at 4, the ring five over it.
     */
    @Test
    void theCellsPlayersPassThroughAreOutOfReach() {
        ShapeKit.Shape s = HuntingGroundShape.SHAPE;
        List<String> near = new ArrayList<>();
        int[][] round = { { 0, 0 }, { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        int highest = 0;
        for (long st : REACHED) {
            int x = ShapeKit.Shape.stateX(st), z = ShapeKit.Shape.stateZ(st), h = ShapeKit.Shape.stateH(st);
            int feet = h / 2;
            highest = Math.max(highest, h);
            for (int[] d : round) for (int y = feet - 1; y <= feet + 1; y++) {
                if (!s.inb(x + d[0], y, z + d[1])) continue;
                char ch = s.get(x + d[0], y, z + d[1]);
                if (HuntingGroundShape.PASSES.indexOf(ch) >= 0)
                    near.add(ch + " at " + (x + d[0]) + "," + y + "," + (z + d[1]));
            }
        }
        assertTrue(near.isEmpty(), "cells players pass through, within reach: " + near);
        assertEquals(8, highest, "the top terrace and the bridges, one stands at 4");
        assertTrue(HuntingGroundShape.VELARIUM_Y - highest / 2 >= 5, "the ring hangs five over the highest feet");
        assertEquals(HuntingGroundShape.DISSOLVES, HuntingGroundShape.PASSES);
    }
}
