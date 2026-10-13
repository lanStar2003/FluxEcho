package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The Mana Fountain Array walked as its generator's self-check walks it ({@link ShapeKit.Shape#walk}): from outside the
 * four chamfer portals, in half blocks, half a block up at most, the head clear. Through the arcade and the colonnade
 * it reaches the controller on the obelisk at eye height and all 16 units, in exactly the generator's number of
 * standing states, all on the floor, and never comes near the obelisk's conduits and seat players pass through once the
 * array forms: they stand on the controller, over every head.
 */
class ManaFountainWalkTest {

    private static final Set<Long> REACHED = ManaFountainShape.SHAPE.walk(ManaFountainShape.walkStarts(), 2);

    @Test
    void theControllerIsAtEyeHeight() {
        assertTrue(ManaFountainShape.SHAPE.controllerReach(REACHED));
    }

    @Test
    void everyUnitCanBeReached() {
        List<String> missing = ManaFountainShape.SHAPE.unitReach(REACHED);
        assertTrue(missing.isEmpty(), String.valueOf(missing));
    }

    @Test
    void asManyStatesAsTheGeneratorReaches() {
        assertEquals(652, REACHED.size());
        ShapeKit.Summary s = ShapeKit.summary(ManaFountainShape.SHAPE, ManaFountainShape.walkStarts());
        assertTrue(s.controller);
        assertTrue(s.symmetric);
        assertTrue(s.unreachable.isEmpty());
        assertEquals(652, s.reached);
    }

    /** Every portal is walked through, and someone stands in the garden of the arcade ring and in the court. */
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
        boolean garden = false, court = false;
        for (long st : REACHED) {
            int x = ShapeKit.Shape.stateX(st), z = ShapeKit.Shape.stateZ(st);
            if (ManaFountainShape.SHAPE.get(x, 0, z) == ',') garden = true;
            if (Math.abs(x - 12) + Math.abs(z - 12) == 1) court = true;
        }
        assertTrue(garden, "the arcade's garden");
        assertTrue(court, "beside the obelisk");
    }

    /**
     * The cells players pass through once the array forms ({@link ManaFountainShape#PASSES}: the obelisk's conduits
     * and seat over the controller) lie out of everyone's reach: no standing state the walk reaches has one beside it
     * or under it at the height of its floor, its feet or its head; everyone stands on the floor, the lowest conduit
     * at y3 over their heads, and the controller and its console below the conduits close the column in.
     */
    @Test
    void theCellsPlayersPassThroughAreOutOfReach() {
        ShapeKit.Shape s = ManaFountainShape.SHAPE;
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
                if (ManaFountainShape.PASSES.indexOf(ch) >= 0)
                    near.add(ch + " at " + (x + d[0]) + "," + y + "," + (z + d[1]));
            }
        }
        assertTrue(near.isEmpty(), "cells players pass through, within reach: " + near);
        assertEquals(2, highest, "nobody stands above the floor: the court is open and the roof out of reach");
        assertEquals('~', s.get(12, 2, 12), "the controller under the lowest conduit");
        assertEquals(ManaFountainShape.DISSOLVES, ManaFountainShape.PASSES);
    }
}
