package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LiftRuleTest {

    @Test
    void clickingTheGroundLiftsTheCoreToEyeHeight() {
        assertEquals(2, LiftRule.lift(LiftRule.TOP, false, false, true, true));
        assertEquals(LiftRule.LIFT, LiftRule.lift(1, false, false, true, true));
    }

    @Test
    void sideAndBottomClicksPlaceNormally() {
        for (int side : new int[] { 0, 2, 3, 4, 5 })
            assertEquals(0, LiftRule.lift(side, false, false, true, true), "side " + side);
    }

    @Test
    void sneakingIsTheManualPath() {
        assertEquals(0, LiftRule.lift(LiftRule.TOP, true, false, true, true));
    }

    @Test
    void ourOwnBlocksAreBuiltOnNormally() {
        assertEquals(0, LiftRule.lift(LiftRule.TOP, false, true, true, true), "a frame or deck block");
    }

    @Test
    void bothCellsAboveMustBeFree() {
        assertEquals(0, LiftRule.lift(LiftRule.TOP, false, false, false, true), "a block or an entity right above");
        assertEquals(0, LiftRule.lift(LiftRule.TOP, false, false, true, false), "a low ceiling or a player's head");
        assertEquals(0, LiftRule.lift(LiftRule.TOP, false, false, false, false));
    }

    @Test
    void truthTable() {
        int lifts = 0;
        for (int side = 0; side < 6; side++) for (int bits = 0; bits < 16; bits++) {
            boolean sneaking = (bits & 1) != 0, ours = (bits & 2) != 0, free1 = (bits & 4) != 0,
                free2 = (bits & 8) != 0;
            int expected = side == 1 && !sneaking && !ours && free1 && free2 ? 2 : 0;
            assertEquals(expected, LiftRule.lift(side, sneaking, ours, free1, free2), "side " + side + " bits " + bits);
            if (expected == 2) lifts++;
        }
        assertEquals(1, lifts, "exactly one combination lifts");
    }
}
