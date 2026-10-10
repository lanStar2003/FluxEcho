package com.fluxecho.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.ArchiveShape;
import com.fluxecho.logic.LibraryUnits;

/**
 * The Echo Archive GUI's floor plans: every bookcase's tile lies inside its storey's plan and no two tiles of a storey
 * overlap, a click on a tile picks that bookcase (what is drawn and what is clicked agree), and the plan is the right
 * way round for someone walking in (the doors at the bottom, the left-hand ranges on the left).
 */
class ArchiveMapTest {

    @Test
    void everyTileLiesInsideItsPlan() {
        for (LibraryUnits.Unit u : LibraryUnits.UNITS) {
            int[] t = ArchiveMap.tile(u.index);
            assertTrue(t[0] < t[2] && t[1] < t[3], "tile of " + u.call + " has no area");
            assertTrue(t[0] >= 1 && t[1] >= 1, "tile of " + u.call + " crosses the plan's top or left wall");
            assertTrue(
                t[2] <= ArchiveMap.W - 1 && t[3] <= ArchiveMap.H - 1,
                "tile of " + u.call + " crosses the plan's bottom or right wall");
            int along = u.alongX ? t[2] - t[0] : t[3] - t[1], across = u.alongX ? t[3] - t[1] : t[2] - t[0];
            assertEquals(3 * ArchiveMap.CELL, along, "a bookcase is three blocks long on the plan: " + u.call);
            assertEquals(ArchiveMap.CELL, across, "a bookcase is one block deep on the plan: " + u.call);
        }
    }

    @Test
    void tilesOfAStoreyDoNotOverlap() {
        for (LibraryUnits.Unit a : LibraryUnits.UNITS) for (LibraryUnits.Unit b : LibraryUnits.UNITS) {
            if (a.index >= b.index || a.storey != b.storey) continue;
            int[] p = ArchiveMap.tile(a.index), q = ArchiveMap.tile(b.index);
            boolean overlap = p[0] < q[2] && q[0] < p[2] && p[1] < q[3] && q[1] < p[3];
            assertFalse(overlap, a.call + " and " + b.call + " overlap on the plan");
        }
    }

    @Test
    void aClickOnATilePicksItsBookcase() {
        for (LibraryUnits.Unit u : LibraryUnits.UNITS) {
            int[] t = ArchiveMap.tile(u.index);
            double cx = (t[0] + t[2]) / 2.0, cy = (t[1] + t[3]) / 2.0;
            assertEquals(u.index, ArchiveMap.nearest(u.storey, cx, cy), "the middle of " + u.call);
            assertEquals(u.storey, ArchiveMap.storey(u.index));
        }
        // far outside every plan nothing is picked
        assertEquals(-1, ArchiveMap.nearest(0, -50, -50));
        assertEquals(-1, ArchiveMap.nearest(1, ArchiveMap.W + 50, ArchiveMap.H + 50));
    }

    @Test
    void thePlanIsTheRightWayRoundForSomeoneWalkingIn() {
        // the doors (the front, z 0) at the bottom, the back wall at the top
        assertTrue(ArchiveMap.py(1) > ArchiveMap.py(ArchiveShape.DEPTH - 2));
        // the Archive's across axis runs to the right of a player walking in, so low x is on the left
        assertTrue(ArchiveMap.px(1) < ArchiveMap.px(ArchiveShape.WIDTH - 2));
        for (LibraryUnits.Unit u : LibraryUnits.UNITS) {
            int[] t = ArchiveMap.tile(u.index);
            double mid = (t[0] + t[2]) / 2.0;
            if (u.call.contains("-L")) assertTrue(mid < ArchiveMap.W / 2.0, u.call + " is a left-hand range");
            if (u.call.contains("-R")) assertTrue(mid > ArchiveMap.W / 2.0, u.call + " is a right-hand range");
        }
    }
}
