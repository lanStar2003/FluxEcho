package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FoldedZoneTest {

    @Test
    void roomsAreInTheZoneAndFindable() {
        for (int plot : new int[] { 0, 1, 63, 64, 65, 1000, 40000 }) {
            int x = FoldedZone.centerX(plot), z = FoldedZone.centerZ(plot);
            assertTrue(FoldedZone.contains(x, z));
            assertTrue(FoldedZone.containsChunk(x >> 4, z >> 4));
            assertEquals(plot, FoldedZone.plotAt(x + 0.5, z + 0.5));
            assertEquals(plot, FoldedZone.plotAt(x - 500, z + 500), "anywhere in its cell");
            assertTrue(x < 30_000_000 && z < 30_000_000, "inside the world");
        }
    }

    @Test
    void theRestOfTheWorldIsNotTheZone() {
        assertFalse(FoldedZone.contains(0, 0));
        assertFalse(FoldedZone.contains(FoldedZone.START + 5000, 100), "both x and z");
        assertFalse(FoldedZone.contains(-FoldedZone.START * 2, FoldedZone.START * 2));
        assertEquals(-1, FoldedZone.plotAt(12, 34));
        assertFalse(FoldedZone.containsChunk((FoldedZone.START >> 4) - 1, FoldedZone.START >> 4));
        assertTrue(FoldedZone.containsChunk(FoldedZone.START >> 4, FoldedZone.START >> 4));
    }

    @Test
    void neighboursAreACellApart() {
        assertEquals(FoldedZone.SPACING, FoldedZone.centerX(1) - FoldedZone.centerX(0));
        assertEquals(FoldedZone.SPACING, FoldedZone.centerZ(FoldedZone.COLUMNS) - FoldedZone.centerZ(0));
        assertEquals(FoldedZone.centerX(0), FoldedZone.centerX(FoldedZone.COLUMNS), "the next row starts over");
        assertEquals(0, FoldedZone.centerX(0) & 15, "rooms sit on chunk corners");
    }
}
