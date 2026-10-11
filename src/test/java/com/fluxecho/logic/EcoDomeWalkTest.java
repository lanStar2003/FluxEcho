package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The Eco Dome walked as its generator's self-check walks it ({@link ShapeKit.Shape#walk}): from outside the doors,
 * in half blocks, half a block up at most, the head clear, the vestibule stairs to the deck, the bridges to the
 * floating island and the spiral round the trunk to the crown island. It reaches the controller at eye height and all
 * twelve planters, in exactly the generator's number of standing states.
 */
class EcoDomeWalkTest {

    private static final Set<Long> REACHED = EcoDomeShape.SHAPE.walk(EcoDomeShape.walkStarts(), 2);

    @Test
    void theControllerIsAtEyeHeight() {
        assertTrue(EcoDomeShape.SHAPE.controllerReach(REACHED));
    }

    @Test
    void everyPlanterCanBeReached() {
        assertEquals(
            0,
            EcoDomeShape.SHAPE.unitReach(REACHED)
                .size(),
            String.valueOf(EcoDomeShape.SHAPE.unitReach(REACHED)));
    }

    @Test
    void asManyStatesAsTheGeneratorReaches() {
        assertEquals(1179, REACHED.size());
        ShapeKit.Summary s = ShapeKit.summary(EcoDomeShape.SHAPE, EcoDomeShape.walkStarts());
        assertTrue(s.controller);
        assertTrue(s.symmetric);
        assertTrue(s.unreachable.isEmpty());
        assertEquals(1179, s.reached);
    }

    @Test
    void theCrownIslandAndTheFloatingIslandAreStoodOn() {
        boolean crown = false, island = false, gallery = false;
        for (long st : REACHED) {
            int x = ShapeKit.Shape.stateX(st), z = ShapeKit.Shape.stateZ(st), h = ShapeKit.Shape.stateH(st);
            if (h == 2 * (EcoDomeShape.CROWN_Y + 1) && ShapeKit.octo(x - 13, z - 21, 3, 4)) crown = true;
            if (h == 2 * (EcoDomeShape.DECK_Y + 1) && ShapeKit.octo(x - 13, z - 21, 3, 4)) island = true;
            if (h == 2 * (EcoDomeShape.DECK_Y + 1) && ShapeKit.hypot(x - 13, z - 21) >= 10.5) gallery = true;
        }
        assertTrue(crown, "the crown island");
        assertTrue(island, "the floating island");
        assertTrue(gallery, "the gallery");
    }

    /**
     * The cells players pass through once the dome forms ({@link EcoDomeShape#PASSES}) lie out of everyone's reach: no
     * standing state the walk reaches has one beside it or under it at the height of its floor, its feet or its head.
     * A trunk players passed through would be a shaft beside the floating island's and the crown island's grass and
     * inside the spiral round it, down to the pond (a fall of up to eleven blocks).
     */
    @Test
    void theCellsPlayersPassThroughAreOutOfReach() {
        ShapeKit.Shape s = EcoDomeShape.SHAPE;
        java.util.List<String> near = new java.util.ArrayList<>();
        int[][] round = { { 0, 0 }, { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (long st : REACHED) {
            int x = ShapeKit.Shape.stateX(st), z = ShapeKit.Shape.stateZ(st), h = ShapeKit.Shape.stateH(st);
            int feet = h / 2;
            for (int[] d : round) for (int y = feet - 1; y <= feet + 1; y++) {
                if (!s.inb(x + d[0], y, z + d[1])) continue;
                char ch = s.get(x + d[0], y, z + d[1]);
                if (EcoDomeShape.PASSES.indexOf(ch) >= 0)
                    near.add(ch + " at " + (x + d[0]) + "," + y + "," + (z + d[1]));
            }
        }
        assertTrue(near.isEmpty(), "cells players pass through, within reach: " + near);
        assertTrue(EcoDomeShape.DISSOLVES.contains("C"), "the trunk still gives way to the tree of light");
        assertTrue(EcoDomeShape.PASSES.indexOf('C') < 0, "but keeps its collision");
    }
}
