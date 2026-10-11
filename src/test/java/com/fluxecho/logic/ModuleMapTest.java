package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The module window's floor plans ({@link ModuleMap}), held to the Eco Dome: three storeys (the ground, the deck of
 * the islands and the gallery, the crown island), every planter's cells inside its storey's plan and apart from the
 * others', a click on any of a planter's cells picks that planter (what is drawn and what is clicked agree), the doors
 * at the bottom, and each block showing what stands there: the trunk, the pond, the doors, the console, the island's
 * grass over the open garden.
 */
class ModuleMapTest {

    private static final ModuleMap MAP = new ModuleMap(EcoDomeShape.BUILD, EcoDomeShape.UNITS, "CZ", 124, 112);

    @Test
    void theEcoDomeFitsAtThreePixelsABlock() {
        assertEquals(3, MAP.cell);
        assertEquals(27 * 3 + 2, MAP.w);
        assertEquals(35 * 3 + 2, MAP.h);
        assertTrue(MAP.w <= 124 && MAP.h <= 112);
        // a module too large for three pixels a block takes fewer, never none
        ModuleMap small = new ModuleMap(EcoDomeShape.BUILD, EcoDomeShape.UNITS, "CZ", 60, 60);
        assertEquals(1, small.cell);
        assertTrue(small.w <= 60 && small.h <= 60 || small.cell == 1);
    }

    @Test
    void threeStoreysAndTheirPlanters() {
        assertEquals(3, MAP.storeys());
        assertEquals(1, MAP.level(0));
        assertEquals(7, MAP.level(1));
        assertEquals(12, MAP.level(2));
        assertEquals(
            7,
            MAP.units(0)
                .size(),
            "seven planters on the ground");
        assertEquals(
            4,
            MAP.units(1)
                .size(),
            "two on the island, two on the gallery");
        assertEquals(
            1,
            MAP.units(2)
                .size(),
            "one on the crown island");
        for (UnitLayout.Unit u : EcoDomeShape.UNITS.units()) {
            assertEquals(
                u.name.startsWith("U-") ? 13 : 15,
                MAP.tiles(u.index)
                    .size(),
                "a planter is fifteen cells, three sockets in a rim of twelve (the gallery's leave the cornice)");
            assertNotNull(MAP.bounds(u.index));
        }
        assertEquals(-1, MAP.storeyOf(-1));
        assertEquals(-1, MAP.storeyOf(12));
        assertTrue(
            MAP.tiles(99)
                .isEmpty());
    }

    @Test
    void everyCellLiesInsideThePlanAndNoTwoUnitsOverlap() {
        for (UnitLayout.Unit u : EcoDomeShape.UNITS.units()) for (int[] t : MAP.tiles(u.index)) {
            assertTrue(t[0] < t[2] && t[1] < t[3], u.name + " has a tile without area");
            assertTrue(t[0] >= 1 && t[1] >= 1 && t[2] <= MAP.w - 1 && t[3] <= MAP.h - 1, u.name + " leaves the plan");
            assertEquals(MAP.cell, t[2] - t[0]);
            assertEquals(MAP.cell, t[3] - t[1]);
        }
        for (UnitLayout.Unit a : EcoDomeShape.UNITS.units()) for (UnitLayout.Unit b : EcoDomeShape.UNITS.units()) {
            if (a.index >= b.index || MAP.storeyOf(a.index) != MAP.storeyOf(b.index)) continue;
            for (int[] p : MAP.tiles(a.index)) for (int[] q : MAP.tiles(b.index)) {
                boolean overlap = p[0] < q[2] && q[0] < p[2] && p[1] < q[3] && q[1] < p[3];
                assertFalse(overlap, a.name + " and " + b.name + " overlap on the plan");
            }
        }
    }

    @Test
    void aClickOnAnyCellPicksItsUnit() {
        for (UnitLayout.Unit u : EcoDomeShape.UNITS.units()) {
            int storey = MAP.storeyOf(u.index);
            for (int[] t : MAP.tiles(u.index))
                assertEquals(u.index, MAP.nearest(storey, (t[0] + t[2]) / 2.0, (t[1] + t[3]) / 2.0), u.name);
        }
        assertEquals(-1, MAP.nearest(0, -50, -50));
        assertEquals(-1, MAP.nearest(2, MAP.w / 2.0, MAP.h - 2), "nothing near the doors on the crown storey");
        // the trunk's middle on the ground is no planter's
        int[] trunk = MAP.tile(EcoDomeShape.TRUNK_X, EcoDomeShape.TRUNK_Z);
        assertEquals(-1, MAP.nearest(0, (trunk[0] + trunk[2]) / 2.0, (trunk[1] + trunk[3]) / 2.0));
    }

    @Test
    void thePlanIsTheRightWayRoundForSomeoneWalkingIn() {
        // the front (z 0, the doors) at the bottom, the dome's back at the top; across to the right
        assertTrue(MAP.py(0) > MAP.py(EcoDomeShape.DEPTH - 1));
        assertTrue(MAP.px(EcoDomeShape.WIDTH - 1) > MAP.px(0));
        assertArrayEquals(new int[] { 1, MAP.h - 1 - 3, 4, MAP.h - 1 }, MAP.tile(0, 0));
        // the doors of the portal (x 10..12 and 14..16 at z 0) are on the bottom row
        for (int x : new int[] { 10, 11, 12, 14, 15, 16 })
            assertEquals(ModuleMap.Mark.DOOR, MAP.mark(0, x, 0), "door at " + x);
    }

    @Test
    void eachBlockShowsWhatStandsThere() {
        int tx = EcoDomeShape.TRUNK_X, tz = EcoDomeShape.TRUNK_Z;
        for (int s = 0; s < 3; s++) assertEquals(ModuleMap.Mark.TRUNK, MAP.mark(s, tx, tz), "the trunk on " + s);
        assertEquals(ModuleMap.Mark.CONSOLE, MAP.mark(0, 13, 0), "the console under the controller");
        assertEquals(ModuleMap.Mark.WATER, MAP.mark(0, tx + 2, tz), "the pond");
        assertEquals(ModuleMap.Mark.FIXTURE, MAP.mark(0, tx + 4, tz), "the pond's rim");
        assertEquals(ModuleMap.Mark.FIXTURE, MAP.mark(0, 9, 6), "a holo pedestal");
        // the deck: the island's grass, the open garden below the deck elsewhere, the gallery's lit edge
        assertEquals(ModuleMap.Mark.GRASS, MAP.mark(1, tx + 2, tz + 1));
        assertEquals(ModuleMap.Mark.NONE, MAP.mark(1, tx + 7, tz + 5), "open to the garden");
        assertEquals(ModuleMap.Mark.FLOOR, MAP.mark(1, tx + 7, tz + 1), "a bridge to the island");
        assertEquals(ModuleMap.Mark.NONE, MAP.mark(0, 0, 0), "outside the building");
        boolean rail = false, wall = false;
        for (int x = 0; x < EcoDomeShape.WIDTH; x++) for (int z = 0; z < EcoDomeShape.DEPTH; z++) {
            rail |= MAP.mark(1, x, z) == ModuleMap.Mark.RAIL;
            wall |= MAP.mark(0, x, z) == ModuleMap.Mark.WALL;
        }
        assertTrue(rail, "the gallery's and the bridges' rails");
        assertTrue(wall, "the walls round the ground floor");
        assertEquals(ModuleMap.Mark.NONE, MAP.mark(5, 1, 1));
        assertEquals(ModuleMap.Mark.NONE, MAP.mark(0, -1, 1));
    }

    @Test
    void someoneStandingIsOnTheStoreyUnderThem() {
        assertEquals(-1, MAP.storeyAt(-1));
        assertEquals(0, MAP.storeyAt(1));
        assertEquals(0, MAP.storeyAt(6.4));
        assertEquals(1, MAP.storeyAt(7));
        assertEquals(1, MAP.storeyAt(11));
        assertEquals(2, MAP.storeyAt(12));
        assertEquals(2, MAP.storeyAt(19));
    }

    @Test
    void classifyReadsTheFloorUnderAnEmptyBlock() {
        CityBuild b = EcoDomeShape.BUILD;
        assertEquals(ModuleMap.Mark.NONE, ModuleMap.classify(b, 0, 0, 0, null));
        assertEquals(ModuleMap.Mark.NONE, ModuleMap.classify(b, 0, 1, 0, null));
        assertEquals(ModuleMap.Mark.WALL, ModuleMap.classify(b, 2, 1, 0, null), "the plinth course");
        assertEquals(ModuleMap.Mark.TRUNK, ModuleMap.classify(b, 13, 5, 21, "CZ"));
        assertEquals(ModuleMap.Mark.WALL, ModuleMap.classify(b, 13, 5, 21, ""), "a trunk that does not dissolve");
    }
}
