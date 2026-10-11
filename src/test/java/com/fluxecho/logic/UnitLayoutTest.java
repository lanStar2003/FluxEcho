package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

/**
 * The generic units of 0.11.0 ({@link UnitLayout}): slots run unit by unit in the shape's declared order, sockets take
 * their unit's first slots in the order the unit lists them, a slot may have no socket (it is kept in the window
 * only), and no cell belongs to two units.
 */
class UnitLayoutTest {

    private static ShapeKit.Shape shape() {
        ShapeKit.Shape s = new ShapeKit.Shape("t", 8, 6, 4);
        for (int x = 0; x < 8; x++) for (int z = 0; z < 4; z++) s.set(x, 0, z, 'F');
        s.set(1, 1, 1, 'u');
        s.set(2, 1, 1, 'o');
        s.set(3, 1, 1, 'o');
        s.set(5, 3, 2, 'h');
        s.set(5, 4, 2, 'o');
        return s;
    }

    @Test
    void slotsRunUnitByUnit() {
        ShapeKit.Shape s = shape();
        s.unit(
            "bed",
            "planter",
            Arrays.asList(new int[] { 1, 1, 1 }, new int[] { 2, 1, 1 }, new int[] { 3, 1, 1 }),
            3,
            null);
        s.unit("stand", "plinth", Arrays.asList(new int[] { 5, 3, 2 }, new int[] { 5, 4, 2 }), 1, new int[] { 0, -1 });
        UnitLayout l = new UnitLayout(s);
        assertEquals(2, l.size());
        assertEquals(4, l.slots());
        assertEquals(0, l.unit(0).firstSlot);
        assertEquals(3, l.unit(1).firstSlot);
        assertEquals(0, l.slotOfSocket(2, 1, 1));
        assertEquals(1, l.slotOfSocket(3, 1, 1));
        assertEquals(-1, l.slotOfSocket(1, 1, 1), "the rim is no socket");
        assertEquals(3, l.slotOfSocket(5, 4, 2));
        assertArrayEquals(new int[] { 3, 1, 1 }, l.socketOf(1));
        assertNull(l.socketOf(2), "the bed's third slot has no socket");
        assertNull(l.socketOf(4));
        assertNull(l.socketOf(-1));
        assertEquals(0, l.unitOfSlot(2));
        assertEquals(1, l.unitOfSlot(3));
        assertEquals(-1, l.unitOfSlot(4));
        assertEquals(0, l.unitOfCell(1, 1, 1));
        assertEquals(1, l.unitOfCell(5, 3, 2));
        assertEquals(-1, l.unitOfCell(0, 0, 0));
        assertArrayEquals(new int[] { 1, 3 }, l.storeys());
        assertEquals(0, l.storey(0));
        assertEquals(1, l.storey(1));
        assertArrayEquals(
            new int[] { 1, 1, 3, 1 },
            l.unit(0)
                .footprint());
        assertEquals("planter", l.unit(0).kind);
        assertArrayEquals(new int[] { 0, -1 }, l.unit(1).face);
    }

    @Test
    void aCellBelongsToOneUnit() {
        ShapeKit.Shape s = shape();
        s.unit("a", "planter", Collections.singletonList(new int[] { 2, 1, 1 }), 1, null);
        s.unit("b", "planter", Arrays.asList(new int[] { 2, 1, 1 }, new int[] { 3, 1, 1 }), 2, null);
        assertThrows(IllegalStateException.class, () -> new UnitLayout(s));
    }

    @Test
    void aUnitHoldsItsSockets() {
        ShapeKit.Shape s = shape();
        s.unit("a", "planter", Arrays.asList(new int[] { 2, 1, 1 }, new int[] { 3, 1, 1 }), 1, null);
        assertThrows(IllegalStateException.class, () -> new UnitLayout(s));
    }

    /** The Eco Dome's planters: three sockets each, the slots in the order the generator declares the beds. */
    @Test
    void theEcoDomesPlanters() {
        UnitLayout l = EcoDomeShape.UNITS;
        for (int u = 0; u < l.size(); u++) {
            assertEquals(3 * u, l.unit(u).firstSlot);
            assertEquals(3, l.unit(u).sockets.size());
            for (int k = 0; k < 3; k++) assertArrayEquals(l.unit(u).sockets.get(k), l.socketOf(3 * u + k));
        }
    }
}
