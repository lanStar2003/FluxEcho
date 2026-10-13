package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The Blood Covenant Altar walked as its generator's self-check walks it ({@link ShapeKit.Shape#walk}): from outside
 * the four chamfer portals, in half blocks, half a block up at most, the head clear. It climbs the dais's treads and
 * blocks, reaches the controller at eye height from there and all 12 units, in exactly the generator's number of
 * standing states. Nothing of it dissolves, so there is nothing players pass through.
 */
class BloodCovenantWalkTest {

    private static final Set<Long> REACHED = BloodCovenantShape.SHAPE.walk(BloodCovenantShape.walkStarts(), 2);

    @Test
    void theControllerIsAtEyeHeightFromTheDais() {
        assertTrue(BloodCovenantShape.SHAPE.controllerReach(REACHED));
        // the dais's block ring next to the altar: one stands on it at y2, the eye at 3.62, the controller's middle 3.5
        boolean onDais = false;
        for (long st : REACHED) {
            int x = ShapeKit.Shape.stateX(st), z = ShapeKit.Shape.stateZ(st), h = ShapeKit.Shape.stateH(st);
            if (Math.abs(x - 12) + Math.abs(z - 12) == 1) {
                assertEquals(4, h, "beside the altar one stands on the dais");
                onDais = true;
            }
        }
        assertTrue(onDais);
    }

    @Test
    void everyUnitCanBeReached() {
        List<String> missing = BloodCovenantShape.SHAPE.unitReach(REACHED);
        assertTrue(missing.isEmpty(), String.valueOf(missing));
    }

    @Test
    void asManyStatesAsTheGeneratorReaches() {
        assertEquals(752, REACHED.size());
        ShapeKit.Summary s = ShapeKit.summary(BloodCovenantShape.SHAPE, BloodCovenantShape.walkStarts());
        assertTrue(s.controller);
        assertTrue(s.symmetric);
        assertTrue(s.unreachable.isEmpty());
        assertEquals(752, s.reached);
        // 716 on the floor, 24 on the dais's treads, 12 on its blocks: nobody gets onto the tiers
        int floor = 0, treads = 0, blocks = 0;
        for (long st : REACHED) {
            int h = ShapeKit.Shape.stateH(st);
            if (h == 2) floor++;
            else if (h == 3) treads++;
            else if (h == 4) blocks++;
            else throw new AssertionError("someone stands at " + h / 2.0);
        }
        assertEquals(716, floor);
        assertEquals(24, treads);
        assertEquals(12, blocks);
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

    /** Nothing dissolves and nothing lets players through: the blood orb over the oculus is drawn, not built. */
    @Test
    void nothingDissolves() {
        assertEquals("", BloodCovenantShape.DISSOLVES);
        assertEquals("", BloodCovenantShape.PASSES);
    }
}
