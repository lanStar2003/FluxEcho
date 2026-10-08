package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ItemFilterTest {

    @Test
    void aDamageValueOnlyMatchesThatDamage() {
        ItemFilter f = new ItemFilter(new String[] { "minecraft:skull:1" });
        assertTrue(f.matches("minecraft:skull", 1));
        assertFalse(f.matches("minecraft:skull", 0));
    }

    @Test
    void noDamageMatchesEveryDamageAndCaseIsIgnored() {
        ItemFilter f = new ItemFilter(new String[] { " Minecraft:Nether_Star " });
        assertTrue(f.matches("minecraft:nether_star", 0));
        assertTrue(f.matches("minecraft:NETHER_STAR", 7));
    }

    @Test
    void brokenEntriesAreIgnored() {
        ItemFilter f = new ItemFilter(new String[] { "", "skull", "minecraft:skull:x", ":skull", null });
        assertTrue(f.isEmpty());
        assertFalse(f.matches("minecraft:skull", 1));
    }
}
