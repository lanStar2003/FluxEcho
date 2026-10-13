package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The Dual-foil Vault walked as its generator's self-check walks it ({@link ShapeKit.Shape#walk}): from outside the
 * doors, in half blocks, half a block up at most, the head clear. It crosses the nave, climbs both corner tower stairs
 * all the way, walks both catwalks, the landings and the cross walk over the foil, and reaches the controller at eye
 * height, every canvas from the floor its tier stands on and the foil from the nave's floor, in exactly the generator's
 * number of standing states.
 */
class DualFoilVaultWalkTest {

    private static final Set<Long> REACHED = DualFoilVaultShape.SHAPE.walk(DualFoilVaultShape.walkStarts(), 2);

    @Test
    void theControllerIsAtEyeHeight() {
        assertTrue(DualFoilVaultShape.SHAPE.controllerReach(REACHED));
    }

    @Test
    void everyCanvasAndTheFoilCanBeReached() {
        List<String> missing = DualFoilVaultShape.SHAPE.unitReach(REACHED);
        assertTrue(missing.isEmpty(), String.valueOf(missing));
    }

    @Test
    void asManyStatesAsTheGeneratorReaches() {
        assertEquals(1484, REACHED.size());
        ShapeKit.Summary s = ShapeKit.summary(DualFoilVaultShape.SHAPE, DualFoilVaultShape.walkStarts());
        assertTrue(s.controller);
        assertTrue(s.symmetric);
        assertTrue(s.unreachable.isEmpty());
        assertEquals(1484, s.reached);
    }

    /**
     * Each canvas is used from the floor its tier stands on, right in front of it: the ground tier from the nave's
     * floor (stand 1), the middle tier from the first catwalk (stand 9), the upper tier from the second (stand 17).
     */
    @Test
    void eachTierIsUsedFromItsOwnFloor() {
        for (UnitLayout.Unit u : DualFoilVaultShape.UNITS.units()) {
            if (!"canvas".equals(u.kind)) continue;
            boolean stood = false;
            for (int[] c : u.cells) {
                if (c[1] != u.floor) continue;
                stood |= REACHED.contains(ShapeKit.Shape.state(c[0] + u.face[0], c[2], 2 * u.floor));
            }
            assertTrue(stood, u.name + " from stand " + u.floor);
        }
        // the foil from the nave's floor before it, the desk between
        int before = 0;
        for (int x = 7; x <= 19; x++) if (REACHED.contains(ShapeKit.Shape.state(x, 33, 2))) before++;
        assertEquals(13, before, "the whole foil's width");
    }

    /**
     * Both stairs are climbed all the way (every half block from the floor to the second catwalk), both catwalks are
     * walked from the landings to the back wall, the cross walk joins them over the foil, and nobody stands higher.
     */
    @Test
    void theStairsTheCatwalksAndTheCrossWalk() {
        Set<Integer> heights = new HashSet<>();
        int cross = 0;
        boolean[] left = new boolean[2], right = new boolean[2], back = new boolean[2];
        for (long st : REACHED) {
            int x = ShapeKit.Shape.stateX(st), z = ShapeKit.Shape.stateZ(st), h = ShapeKit.Shape.stateH(st);
            heights.add(h);
            for (int i = 0; i < 2; i++) {
                int stand = 2 * (DualFoilVaultShape.DECKS[i] + 1);
                if (h != stand) continue;
                if (x <= 3 && z >= 29) left[i] = true;
                if (x >= 23 && z >= 29) right[i] = true;
                if (x >= 4 && x <= 6 && z <= 5) back[i] = true;
            }
            if (h == 34 && x >= 4 && x <= 22 && z >= 31) cross++;
        }
        for (int i = 0; i < 2; i++) {
            assertTrue(left[i] && right[i], "both catwalks at deck " + DualFoilVaultShape.DECKS[i]);
            assertTrue(back[i], "the landing at deck " + DualFoilVaultShape.DECKS[i]);
        }
        assertEquals(19 * 2, cross, "the cross walk's two rows behind its rail");
        for (int h = 2; h <= 34; h++) assertTrue(heights.contains(h), "a step at " + h + " half blocks");
        assertEquals(34, java.util.Collections.max(heights), "nobody stands on the roof");
    }
}
