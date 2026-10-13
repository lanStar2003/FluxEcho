package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The Resonance Gate walked as its generator's self-check walks it ({@link ShapeKit.Shape#walk}): from the road before
 * the passage and before the left wing's front door, in half blocks, half a block up at most, the head clear. It
 * reaches both wings' ground halls, climbs both tower stairs to the upper halls, crosses into the resonance chamber on
 * the bridge, reaches all ten units and the controller on the kiosk at eye height from the road, in exactly the
 * generator's number of standing states.
 */
class ResonanceGateWalkTest {

    private static final Set<Long> REACHED = ResonanceGateShape.SHAPE.walk(ResonanceGateShape.walkStarts(), 2);

    @Test
    void theControllerIsAtEyeHeightFromTheRoad() {
        assertTrue(ResonanceGateShape.SHAPE.controllerReach(REACHED));
        // someone standing on the road beside the kiosk sees it
        boolean road = false;
        for (int[] d : new int[][] { { 14, 7 }, { 16, 7 }, { 15, 6 }, { 15, 8 } })
            road |= REACHED.contains(ShapeKit.Shape.state(d[0], d[1], 2));
        assertTrue(road);
    }

    @Test
    void everyUnitCanBeReached() {
        List<String> missing = ResonanceGateShape.SHAPE.unitReach(REACHED);
        assertTrue(missing.isEmpty(), String.valueOf(missing));
    }

    @Test
    void asManyStatesAsTheGeneratorReaches() {
        assertEquals(932, REACHED.size());
        ShapeKit.Summary s = ShapeKit.summary(ResonanceGateShape.SHAPE, ResonanceGateShape.walkStarts());
        assertTrue(s.controller);
        assertTrue(s.symmetric);
        assertTrue(s.unreachable.isEmpty());
        assertEquals(932, s.reached);
    }

    /**
     * Both ground halls, both tower stairs all the way (every half block from the floor to the upper deck), both
     * upper halls and the resonance chamber on the bridge (58 standing cells) are walked.
     */
    @Test
    void bothWingsTheStairsAndTheChamber() {
        int chamber = 0;
        Set<Integer> heights = new HashSet<>();
        boolean leftHall = false, rightHall = false, leftUpper = false, rightUpper = false;
        for (long st : REACHED) {
            int x = ShapeKit.Shape.stateX(st), z = ShapeKit.Shape.stateZ(st), h = ShapeKit.Shape.stateH(st);
            heights.add(h);
            if (x >= 9 && x <= 21 && z >= 5 && z <= 11 && h == 18) chamber++;
            if (x >= 1 && x <= 7 && z >= 1 && z <= 15 && h == 2) leftHall = true;
            if (x >= 23 && x <= 29 && z >= 1 && z <= 15 && h == 2) rightHall = true;
            if (x >= 1 && x <= 7 && z >= 1 && z <= 15 && h == 18) leftUpper = true;
            if (x >= 23 && x <= 29 && z >= 1 && z <= 15 && h == 18) rightUpper = true;
        }
        assertTrue(leftHall && rightHall, "the ground halls");
        assertTrue(leftUpper && rightUpper, "the upper halls");
        assertEquals(58, chamber, "the resonance chamber");
        for (int h = 2; h <= 18; h++) assertTrue(heights.contains(h), "a step at " + h + " half blocks");
        assertEquals(18, java.util.Collections.max(heights), "nobody stands on a roof");
    }

    /**
     * Formed, the spire core's conduits and seat ({@link ResonanceGateShape#PASSES}) let players through, and the
     * chamber's deck runs under them, so with them gone one more standing cell opens: the core's own column on the
     * deck (15, 8), its head under the seat's place and the glass over it. Nothing else changes: the gate's
     * documented exception to the rule that cells players pass through lie out of reach (CITY.md §4.7 "Dissolving",
     * as the nexus's own conduits and seat).
     */
    @Test
    void theDissolvedCoreOpensItsColumnAlone() {
        ShapeKit.Shape s = ResonanceGateShape.SHAPE;
        boolean beside = false;
        for (long st : REACHED) {
            int x = ShapeKit.Shape.stateX(st), z = ShapeKit.Shape.stateZ(st), h = ShapeKit.Shape.stateH(st);
            if (h == 18 && Math.abs(x - 15) + Math.abs(z - 8) == 1) beside = true;
        }
        assertTrue(beside, "the core is within reach in the chamber");
        ShapeKit.Shape open = new ShapeKit.Shape(s.name, s.W, s.H, s.D);
        for (int y = 0; y < s.H; y++) for (int z = 0; z < s.D; z++) for (int x = 0; x < s.W; x++) {
            char ch = s.get(x, y, z);
            if (ch != ' ' && ResonanceGateShape.PASSES.indexOf(ch) < 0) open.set(x, y, z, ch);
        }
        open.controller = s.controller.clone();
        Set<Long> walked = open.walk(ResonanceGateShape.walkStarts(), 2);
        Set<Long> more = new HashSet<>(walked);
        more.removeAll(REACHED);
        assertEquals(java.util.Collections.singleton(ShapeKit.Shape.state(15, 8, 18)), more);
        assertTrue(walked.containsAll(REACHED));
        assertEquals('G', s.get(15, 13, 8), "glass closes the column over the seat's place");
        assertEquals('d', s.get(15, 8, 8), "the deck under it");
    }
}
