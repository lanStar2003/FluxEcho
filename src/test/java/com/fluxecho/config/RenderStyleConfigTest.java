package com.fluxecho.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** A hand-edited file cannot put the 0.17.0 client settings outside what the renderers understand. */
class RenderStyleConfigTest {

    @Test
    void anUnknownChoiceFallsBackToTheSafeDefault() {
        assertEquals("depth", RenderConfig.pick(" depth ", "gate", "depth"));
        assertEquals("gate", RenderConfig.pick("Depth", "gate", "depth"));
        assertEquals("gate", RenderConfig.pick("", "gate", "depth"));
        assertEquals("gate", RenderConfig.pick(null, "gate", "depth"));
        assertEquals("sky", RenderConfig.pick("sky", "world", "sky"));
        assertEquals("world", RenderConfig.pick("aurora", "world", "sky"));
    }

    @Test
    void theHullBrightnessStaysWithinItsBounds() {
        assertEquals(1.0, StyleConfig.clamp(1.2));
        assertEquals(0.85, StyleConfig.clamp(0.5));
        assertEquals(0.9, StyleConfig.clamp(0.9));
        assertEquals(1.0, StyleConfig.clamp(Double.NaN));
    }
}
