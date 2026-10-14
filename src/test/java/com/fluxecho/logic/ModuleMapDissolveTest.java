package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A module kind whose dissolving characters give way only in some cells (0.16.0, the Weather Station: its mast's
 * pillars dissolve, its vane pylons' of the same character stay): its plan pages draw the mast as a trunk and the
 * pylons as what they are; every other kind's plans are as before.
 */
class ModuleMapDissolveTest {

    private static final CityBuild B = WeatherStationShape.BUILD;

    @Test
    void onlyTheMastIsATrunk() {
        String dissolves = WeatherStationShape.DISSOLVES;
        ModuleMap.DissolvesAt mast = WeatherStationShape::mast;
        int masts = 0, pylons = 0;
        for (int x = 0; x < B.width(); x++) for (int y = 0; y < B.height(); y++) for (int z = 0; z < B.depth(); z++) {
            char ch = B.cell(x, y, z);
            if (ch == ShapeKit.NONE || dissolves.indexOf(ch) < 0) continue;
            ModuleMap.Mark all = ModuleMap.classify(B, x, y, z, dissolves),
                some = ModuleMap.classify(B, x, y, z, dissolves, mast);
            assertEquals(ModuleMap.Mark.TRUNK, all, "every such cell, by the old rule");
            if (WeatherStationShape.mast(x, y, z)) {
                masts++;
                assertEquals(ModuleMap.Mark.TRUNK, some);
            } else {
                pylons++;
                assertNotEquals(ModuleMap.Mark.TRUNK, some, "a pylon at " + x + ", " + y + ", " + z);
            }
        }
        assertTrue(masts > 0 && pylons > 0, masts + " mast cells, " + pylons + " pylon cells");
        // a cell that does not dissolve is what it is under either rule
        assertEquals(
            ModuleMap.classify(B, 10, 0, 10, dissolves),
            ModuleMap.classify(B, 10, 0, 10, dissolves, ModuleMap.EVERYWHERE));
    }

    @Test
    void thePlansFollowTheRule() {
        ModuleMap old = new ModuleMap(B, WeatherStationShape.UNITS, WeatherStationShape.DISSOLVES, 200, 200);
        ModuleMap kept = new ModuleMap(
            B,
            WeatherStationShape.UNITS,
            WeatherStationShape.DISSOLVES,
            WeatherStationShape::mast,
            200,
            200,
            new int[0]);
        ModuleMap every = new ModuleMap(
            B,
            WeatherStationShape.UNITS,
            WeatherStationShape.DISSOLVES,
            ModuleMap.EVERYWHERE,
            200,
            200,
            new int[0]);
        assertEquals(old.storeys(), every.storeys());
        assertEquals(old.storeys(), kept.storeys());
        int differ = 0;
        for (int s = 0; s < old.storeys(); s++)
            for (int x = 0; x < B.width(); x++) for (int z = 0; z < B.depth(); z++) {
                assertEquals(old.mark(s, x, z), every.mark(s, x, z), "the old rule is every cell");
                if (old.mark(s, x, z) == kept.mark(s, x, z)) continue;
                // only a pylon's trunk turns back into what it is
                differ++;
                assertEquals(ModuleMap.Mark.TRUNK, old.mark(s, x, z));
                assertTrue(
                    Math.abs(x - SkyRing.MID) == WeatherStationShape.PYLON_DX,
                    "a pylon's column: " + x + ", " + z);
            }
        assertTrue(differ > 0, "the pylons show on the plan");
    }
}
