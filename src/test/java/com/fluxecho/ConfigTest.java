package com.fluxecho;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConfigTest {

    @Test
    void olderVersions() {
        assertTrue(Config.older(null, "0.5.0"));
        assertTrue(Config.older("", "0.5.0"));
        assertTrue(Config.older("0.4.1", "0.5.0"));
        assertTrue(Config.older("0.5.0", "0.6.0"));
        assertTrue(Config.older("0.9", "0.10.0"));
        assertFalse(Config.older("0.5.0", "0.5.0"));
        assertFalse(Config.older("0.6.0", "0.5.0"));
        assertFalse(Config.older("1.0", "0.6.0"));
    }

    @Test
    void campusDefaultsOf0101() {
        assertTrue(Config.buildClearAll, "the builder empties what it clears (the player asked for it)");
        assertEquals(4, Config.buildFillDepth, "grading fills four deep (0.10.0: two)");
        // a file that kept 0.10.0's default gets the new one; a depth a player chose stays
        assertTrue(Config.bumpsFillDepth("2"));
        assertTrue(Config.bumpsFillDepth(" 2 "));
        assertFalse(Config.bumpsFillDepth("3"));
        assertFalse(Config.bumpsFillDepth("0"));
        assertFalse(Config.bumpsFillDepth(null));
        assertTrue(Config.older("0.10.0", "0.10.1"));
    }
}
