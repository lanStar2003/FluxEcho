package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * What a must-be-air cell of a checked structure lets stand in it (0.14.2), on plain facts: air, or a replaceable
 * block that is no fluid. The blocks themselves (snow layers by height, tall grass, water, a wall) are in
 * {@code nexus.DoorwayRoomTest}.
 */
class MustBeAirTest {

    @Test
    void airOrReplaceableAndNoFluid() {
        assertTrue(MustBeAir.open(true, false, false), "air");
        assertTrue(MustBeAir.open(true, true, true), "air is air whatever else is said");
        assertTrue(MustBeAir.open(false, true, false), "a snow layer, tall grass, a vine, fire");
        assertFalse(MustBeAir.open(false, true, true), "a fluid is replaceable, yet no doorway");
        assertFalse(MustBeAir.open(false, false, false), "anything solid");
        assertFalse(MustBeAir.open(false, false, true));
    }
}
