package com.fluxecho;

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
}
