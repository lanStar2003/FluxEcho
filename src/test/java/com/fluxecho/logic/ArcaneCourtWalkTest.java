package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The Arcane Court walked as its generator's self-check walks it ({@link ShapeKit.Shape#walk}): from outside the four
 * chamfer portals, in half blocks, half a block up at most, the head clear. It reaches the controller on the altar at
 * eye height and all 21 units, in exactly the generator's number of standing states, and never comes near the finial
 * players pass through once the court forms.
 */
class ArcaneCourtWalkTest {

    private static final Set<Long> REACHED = ArcaneCourtShape.SHAPE.walk(ArcaneCourtShape.walkStarts(), 2);

    @Test
    void theControllerIsAtEyeHeight() {
        assertTrue(ArcaneCourtShape.SHAPE.controllerReach(REACHED));
    }

    @Test
    void everyUnitCanBeReached() {
        List<String> missing = ArcaneCourtShape.SHAPE.unitReach(REACHED);
        assertTrue(missing.isEmpty(), String.valueOf(missing));
    }

    @Test
    void asManyStatesAsTheGeneratorReaches() {
        assertEquals(712, REACHED.size());
        ShapeKit.Summary s = ShapeKit.summary(ArcaneCourtShape.SHAPE, ArcaneCourtShape.walkStarts());
        assertTrue(s.controller);
        assertTrue(s.symmetric);
        assertTrue(s.unreachable.isEmpty());
        assertEquals(712, s.reached);
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
     * The cells players pass through once the court forms ({@link ArcaneCourtShape#PASSES}: the finial's conduits and
     * seat on the spire's cap) lie out of everyone's reach: no standing state the walk reaches has one beside it or
     * under it at the height of its floor, its feet or its head.
     */
    @Test
    void theCellsPlayersPassThroughAreOutOfReach() {
        ShapeKit.Shape s = ArcaneCourtShape.SHAPE;
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
                if (ArcaneCourtShape.PASSES.indexOf(ch) >= 0)
                    near.add(ch + " at " + (x + d[0]) + "," + y + "," + (z + d[1]));
            }
        }
        assertTrue(near.isEmpty(), "cells players pass through, within reach: " + near);
        assertEquals(2, highest, "nobody stands above the floor: the court is one hall under its spire");
        assertEquals(ArcaneCourtShape.DISSOLVES, ArcaneCourtShape.PASSES);
    }
}
