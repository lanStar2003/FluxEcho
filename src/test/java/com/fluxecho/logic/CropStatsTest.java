package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CropStatsTest {

    private static final CropStats BASE = new CropStats(1, 2, 3), DONOR = new CropStats(21, 31, 0);

    @Test
    void eachCircuitCopiesItsStat() {
        assertEquals(new CropStats(21, 2, 3), BASE.with(DONOR, 1));
        assertEquals(new CropStats(1, 31, 3), BASE.with(DONOR, 2));
        assertEquals(new CropStats(1, 2, 0), BASE.with(DONOR, 3));
        assertEquals(DONOR, BASE.with(DONOR, 4));
    }

    @Test
    void otherCircuitsCopyNothing() {
        assertFalse(CropStats.copies(0));
        assertFalse(CropStats.copies(5));
        assertTrue(CropStats.copies(4));
        assertEquals(BASE, BASE.with(DONOR, 0));
    }

    @Test
    void statsStayInIc2sRange() {
        CropStats s = new CropStats(-4, 40, 31);
        assertEquals(0, s.growth);
        assertEquals(31, s.gain);
        assertEquals(31, s.resistance);
    }
}
