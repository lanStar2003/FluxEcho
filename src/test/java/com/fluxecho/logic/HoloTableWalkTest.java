package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The Holo Sand Table walked as its generator's self-check walks it ({@link ShapeKit.Shape#walk}): from outside the
 * four chamfer portals, in half blocks, half a block up at most, the head clear (0.14.1's rule: each column clear from
 * its own stand). It reaches the controller at the table's front at eye height, the table and all nine consoles, in
 * exactly the generator's number of standing states; both ramps are climbed half a step at a time to the gallery at
 * stand 7 and every tread is stood on; and nobody comes near the ring light players pass through once it forms.
 */
class HoloTableWalkTest {

    private static final Set<Long> REACHED = HoloTableShape.SHAPE.walk(HoloTableShape.walkStarts(), 2);

    @Test
    void theControllerIsAtEyeHeight() {
        assertTrue(HoloTableShape.SHAPE.controllerReach(REACHED));
    }

    @Test
    void everyUnitCanBeReached() {
        List<String> missing = HoloTableShape.SHAPE.unitReach(REACHED);
        assertTrue(missing.isEmpty(), String.valueOf(missing));
    }

    @Test
    void asManyStatesAsTheGeneratorReaches() {
        assertEquals(1130, REACHED.size());
        ShapeKit.Summary s = ShapeKit.summary(HoloTableShape.SHAPE, HoloTableShape.walkStarts());
        assertTrue(s.controller);
        assertTrue(s.symmetric);
        assertTrue(s.unreachable.isEmpty());
        assertEquals(1130, s.reached);
    }

    /**
     * The ramps: every tread is stood on, every half stand from the floor (1) to the gallery (7) is stood on along
     * each side, and the gallery's whole deck is walked at stand 7.
     */
    @Test
    void theRampsAreClimbedToTheGallery() {
        ShapeKit.Shape s = HoloTableShape.SHAPE;
        assertTrue(
            s.idleTreads(REACHED)
                .isEmpty(),
            "every tread is stood on");
        Set<Integer> left = new HashSet<>(), right = new HashSet<>();
        int gallery = 0;
        for (long st : REACHED) {
            int x = ShapeKit.Shape.stateX(st), z = ShapeKit.Shape.stateZ(st), h = ShapeKit.Shape.stateH(st);
            if (!s.inb(x, 0, z)) continue;
            int n = ShapeKit.onorm(x - HoloTableShape.MID, z - HoloTableShape.MID);
            if (n != HoloTableShape.RAMP_FROM && n != HoloTableShape.RAMP_TO) continue;
            if (h == 2 * HoloTableShape.GALLERY_STAND && s.get(x, HoloTableShape.GALLERY_Y, z) == 'd') gallery++;
            if (h >= 3) (x < HoloTableShape.MID ? left : right).add(h);
        }
        for (int h = 3; h <= 2 * HoloTableShape.GALLERY_STAND; h++) {
            assertTrue(left.contains(h), "the left ramp at stand " + h / 2.0);
            assertTrue(right.contains(h), "the right ramp at stand " + h / 2.0);
        }
        assertEquals(22, gallery, "the gallery's deck");
        int highest = 0;
        for (long st : REACHED) highest = Math.max(highest, ShapeKit.Shape.stateH(st));
        assertEquals(2 * HoloTableShape.GALLERY_STAND, highest, "the gallery is the highest stand");
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
     * The ring light's segments ({@link HoloTableShape#PASSES}), hanging at y9 over the table, lie out of everyone's
     * reach: no reached standing state (on the floor, the ramps or the gallery) has one beside it or under it at the
     * height of its floor, its feet or its head.
     */
    @Test
    void theRingLightIsOutOfReach() {
        ShapeKit.Shape s = HoloTableShape.SHAPE;
        List<String> near = new ArrayList<>();
        int[][] round = { { 0, 0 }, { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (long st : REACHED) {
            int x = ShapeKit.Shape.stateX(st), z = ShapeKit.Shape.stateZ(st), h = ShapeKit.Shape.stateH(st);
            int feet = h / 2;
            for (int[] d : round) for (int y = feet - 1; y <= feet + 2; y++) {
                if (!s.inb(x + d[0], y, z + d[1])) continue;
                char ch = s.get(x + d[0], y, z + d[1]);
                if (HoloTableShape.PASSES.indexOf(ch) >= 0)
                    near.add(ch + " at " + (x + d[0]) + "," + y + "," + (z + d[1]));
            }
        }
        assertTrue(near.isEmpty(), "cells players pass through, within reach: " + near);
        assertEquals(HoloTableShape.DISSOLVES, HoloTableShape.PASSES);
    }
}
