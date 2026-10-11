package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Browsing a module's units in its window ({@link UnitBrowse}): the arrows go round, whatever a client asks for is
 * checked or brought into range, and the list's pages hold every unit once (the Eco Dome's twelve planters at eight a
 * page: two pages).
 */
class UnitBrowseTest {

    @Test
    void theArrowsGoRound() {
        assertEquals(1, UnitBrowse.step(0, 1, 12));
        assertEquals(11, UnitBrowse.step(0, -1, 12));
        assertEquals(0, UnitBrowse.step(11, 1, 12));
        assertEquals(4, UnitBrowse.step(11, 5, 12));
        // a selection out of range is brought in first
        assertEquals(0, UnitBrowse.step(40, 1, 12));
        assertEquals(0, UnitBrowse.step(3, 1, 0), "a module without units");
    }

    @Test
    void aSelectionIsCheckedOrClamped() {
        assertTrue(UnitBrowse.valid(0, 12));
        assertTrue(UnitBrowse.valid(11, 12));
        assertFalse(UnitBrowse.valid(12, 12));
        assertFalse(UnitBrowse.valid(-1, 12));
        assertFalse(UnitBrowse.valid(0, 0));
        assertEquals(0, UnitBrowse.clamp(-5, 12));
        assertEquals(11, UnitBrowse.clamp(99, 12));
        assertEquals(0, UnitBrowse.clamp(3, 0));
    }

    @Test
    void thePagesHoldEveryUnitOnce() {
        int count = EcoDomeShape.UNITS.size(), per = 8;
        assertEquals(2, UnitBrowse.pages(count, per));
        assertEquals(1, UnitBrowse.pages(0, per));
        int seen = 0;
        for (int page = 0; page < UnitBrowse.pages(count, per); page++) for (int line = 0; line < per; line++) {
            int u = UnitBrowse.unitAt(page, line, count, per);
            if (u < 0) continue;
            assertEquals(seen, u, "in order");
            assertEquals(page, UnitBrowse.pageOf(u, per));
            seen++;
        }
        assertEquals(count, seen);
        assertEquals(-1, UnitBrowse.unitAt(1, 7, count, per));
        assertEquals(-1, UnitBrowse.unitAt(0, 8, count, per));
        assertEquals(1, UnitBrowse.page(-1, count, per), "the pages go round");
        assertEquals(0, UnitBrowse.page(2, count, per));
    }
}
