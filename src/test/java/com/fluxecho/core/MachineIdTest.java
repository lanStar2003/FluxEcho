package com.fluxecho.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class MachineIdTest {

    @Test
    void offsetsAreUniqueAndInsideTheReservedRange() {
        Set<Integer> seen = new HashSet<>();
        for (MachineId m : MachineId.values()) {
            assertTrue(seen.add(m.offset), "duplicate offset " + m.offset);
            assertTrue(m.offset >= 0 && m.offset < MachineId.RESERVED, m + " outside the reserved ids");
        }
    }

    @Test
    void defaultIdsStayClearOfFluxDepths() {
        // FluxDepths uses 24520-24526; nothing in GTNH 2.8.4 uses 24521-24575 otherwise
        assertEquals(24530, MachineId.BEE_IMPRINTER.id(24530));
        assertTrue(MachineId.values()[MachineId.values().length - 1].id(24530) < 24530 + MachineId.RESERVED);
    }
}
