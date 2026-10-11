package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The part codes are saved and sent, so they are only ever appended: the codes of 0.10 stay where they were and
 * 0.11.0's new parts take the next free ones (the cladding anchor 9, the socket 39, the basin rim 40, water 53, the
 * module cores 64 to 79), all within the one byte a {@code BUILD_FX} packet gives a code.
 */
class PartsTest {

    @Test
    void theCodesOf010Stay() {
        assertEquals(0, Parts.frame(Parts.FR_BASE));
        assertEquals(8, Parts.frame(Parts.FR_CONSOLE));
        assertEquals(16, Parts.deck(Parts.D_DECK));
        assertEquals(32, Parts.fitting(Parts.F_POST));
        assertEquals(38, Parts.fitting(Parts.F_PEDESTAL));
        assertEquals(48, Parts.LIBRARY_CORE);
        assertEquals(49, Parts.SUPPLY_PORT);
        assertEquals(50, Parts.GRASS);
        assertEquals(51, Parts.DIRT);
        assertEquals(52, Parts.RECYCLE_BIN);
        assertEquals(63, Parts.AIR);
    }

    @Test
    void theCodesOf011() {
        assertEquals(9, Parts.frame(Parts.FR_ANCHOR));
        assertEquals(39, Parts.fitting(Parts.F_SOCKET));
        assertEquals(40, Parts.fitting(Parts.F_BASIN));
        assertEquals(53, Parts.WATER);
        assertEquals(64, Parts.moduleCore(ModuleCores.ECO_DOME));
        assertEquals(79, Parts.moduleCore(Parts.MODULE_CORES - 1));
        assertTrue(Parts.moduleCore(Parts.MODULE_CORES - 1) <= Byte.MAX_VALUE, "one byte a code");
        assertThrows(IllegalArgumentException.class, () -> Parts.moduleCore(Parts.MODULE_CORES));
        assertThrows(IllegalArgumentException.class, () -> Parts.moduleCore(-1));
        assertEquals(Parts.FITTING_TYPES, Parts.FITTING_NAMES.length);
        assertEquals(Parts.FITTING_TYPES, Parts.FITTING_LIGHT.length);
        assertEquals("socket", Parts.FITTING_NAMES[Parts.F_SOCKET]);
        assertEquals("basin", Parts.FITTING_NAMES[Parts.F_BASIN]);
    }

    @Test
    void familiesAndMetas() {
        Set<Integer> seen = new HashSet<>();
        for (int code = 0; code < 128; code++) {
            int families = (Parts.isFrame(code) ? 1 : 0) + (Parts.isDeck(code) ? 1 : 0)
                + (Parts.isFitting(code) ? 1 : 0)
                + (Parts.isModuleCore(code) ? 1 : 0);
            assertTrue(families <= 1, "code " + code + " in one family at most");
            if (Parts.isModuleCore(code)) {
                assertTrue(seen.add(Parts.meta(code)));
                assertEquals(code, Parts.moduleCore(Parts.meta(code)));
            }
        }
        assertEquals(Parts.MODULE_CORES, seen.size());
        assertEquals(Parts.F_BASIN, Parts.meta(Parts.fitting(Parts.F_BASIN)));
        assertEquals(Parts.FR_ANCHOR, Parts.meta(Parts.frame(Parts.FR_ANCHOR)));
        assertFalse(Parts.isModuleCore(Parts.LIBRARY_CORE), "the library core keeps its own block");
        assertFalse(Parts.isModuleCore(Parts.WATER));
    }

    @Test
    void waterGrassAndDirtArePaidInEu() {
        assertTrue(Parts.euOnly(Parts.GRASS));
        assertTrue(Parts.euOnly(Parts.DIRT));
        assertTrue(Parts.euOnly(Parts.WATER));
        assertFalse(Parts.euOnly(Parts.AIR));
        assertFalse(Parts.euOnly(Parts.fitting(Parts.F_BASIN)));
        assertFalse(Parts.euOnly(Parts.moduleCore(0)));
    }

    @Test
    void moduleCoreKeys() {
        assertEquals("eco_dome", ModuleCores.key(ModuleCores.ECO_DOME));
        assertEquals(ModuleCores.ECO_DOME, ModuleCores.meta(EcoDomeShape.KEY));
        assertEquals(Parts.MODULE_CORES, ModuleCores.KEYS.length);
        Set<String> keys = new HashSet<>();
        for (String k : ModuleCores.KEYS) assertTrue(keys.add(k), k);
        assertEquals(-1, ModuleCores.meta("archive"), "the Archive keeps the library core");
        assertTrue(ModuleCores.BUILT >= 1);
    }
}
