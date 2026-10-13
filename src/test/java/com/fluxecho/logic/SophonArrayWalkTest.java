package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The Sophon Array walked as its generator's self-check walks it ({@link ShapeKit.Shape#walk}): from outside the four
 * chamfer portals, in half blocks, half a block up at most, the head clear. It reaches the controller under the oculus
 * at eye height and all 48 racks, in exactly the generator's number of standing states, every portal is walked
 * through, and nobody ever comes near the struts and the lattice players pass through once the array forms.
 */
class SophonArrayWalkTest {

    private static final Set<Long> REACHED = SophonArrayShape.SHAPE.walk(SophonArrayShape.walkStarts(), 2);

    @Test
    void theControllerIsAtEyeHeight() {
        assertTrue(SophonArrayShape.SHAPE.controllerReach(REACHED));
    }

    @Test
    void everyRackCanBeReached() {
        List<String> missing = SophonArrayShape.SHAPE.unitReach(REACHED);
        assertTrue(missing.isEmpty(), String.valueOf(missing));
    }

    @Test
    void asManyStatesAsTheGeneratorReaches() {
        assertEquals(1076, REACHED.size());
        ShapeKit.Summary s = ShapeKit.summary(SophonArrayShape.SHAPE, SophonArrayShape.walkStarts());
        assertTrue(s.controller);
        assertTrue(s.symmetric);
        assertTrue(s.unreachable.isEmpty());
        assertTrue(s.idleTreads.isEmpty());
        assertEquals(1076, s.reached);
    }

    /** Every portal is walked through: someone stands in each one's outer opening at ground level. */
    @Test
    void everyPortalIsWalkedThrough() {
        int[][] portals = { { 5, 5 }, { 25, 5 }, { 5, 25 }, { 25, 25 } };
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
     * The cells players pass through once the array forms ({@link SophonArrayShape#PASSES}: the struts and the lattice
     * sphere's frame pillars, over the podium's roof) lie out of everyone's reach: nobody stands above the hall's
     * floor (the podium has no stair to its roof), and no reached standing state has one beside it or under it at the
     * height of its floor, its feet or its head. So, unlike the dome generator's legs, they follow the rule.
     */
    @Test
    void theStrutsAndTheLatticeAreOutOfReach() {
        ShapeKit.Shape s = SophonArrayShape.SHAPE;
        List<String> near = new ArrayList<>();
        int[][] round = { { 0, 0 }, { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        int highest = 0;
        for (long st : REACHED) {
            int x = ShapeKit.Shape.stateX(st), z = ShapeKit.Shape.stateZ(st), h = ShapeKit.Shape.stateH(st);
            int feet = h / 2;
            highest = Math.max(highest, h);
            for (int[] d : round) for (int y = feet - 1; y <= feet + 2; y++) {
                if (!s.inb(x + d[0], y, z + d[1])) continue;
                char ch = s.get(x + d[0], y, z + d[1]);
                if (SophonArrayShape.PASSES.indexOf(ch) >= 0)
                    near.add(ch + " at " + (x + d[0]) + "," + y + "," + (z + d[1]));
            }
        }
        assertTrue(near.isEmpty(), "cells players pass through, within reach: " + near);
        assertEquals(2, highest, "nobody stands above the hall's floor");
        assertEquals(SophonArrayShape.DISSOLVES, SophonArrayShape.PASSES);
    }
}
