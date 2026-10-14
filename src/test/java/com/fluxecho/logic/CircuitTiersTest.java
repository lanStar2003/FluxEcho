package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The tier names costs give circuits (0.16.0): GT 5.09.51 registers no {@code circuitLuV} (its LuV circuits are
 * {@code circuitMaster}), so every circuit a research asks for is read as a GT voltage tier and paid with GT's own ore
 * name for it. A cost naming circuits the table does not know could never be paid in survival.
 */
class CircuitTiersTest {

    @Test
    void theTiersAreGtsVoltages() {
        assertEquals(0, CircuitTiers.tierOf("circuitULV"));
        assertEquals(1, CircuitTiers.tierOf("circuitLV"));
        assertEquals(2, CircuitTiers.tierOf("circuitMV"));
        assertEquals(3, CircuitTiers.tierOf("circuitHV"));
        assertEquals(4, CircuitTiers.tierOf("circuitEV"));
        assertEquals(5, CircuitTiers.tierOf("circuitIV"));
        assertEquals(6, CircuitTiers.tierOf("circuitLuV"));
        assertEquals(7, CircuitTiers.tierOf("circuitZPM"));
        assertEquals(8, CircuitTiers.tierOf("circuitUV"));
        assertEquals(9, CircuitTiers.count());
        for (int t = 0; t < CircuitTiers.count(); t++) assertEquals(t, CircuitTiers.tierOf(CircuitTiers.name(t)));
        // GT's own names and everything else are not tier names: they are looked up as they are
        assertEquals(-1, CircuitTiers.tierOf("circuitMaster"));
        assertEquals(-1, CircuitTiers.tierOf("circuitData"));
        assertEquals(-1, CircuitTiers.tierOf("circuitluv"));
        assertEquals(-1, CircuitTiers.tierOf("plateChrome"));
        assertEquals(-1, CircuitTiers.tierOf(""));
        assertEquals(-1, CircuitTiers.tierOf(null));
    }

    /** Every circuit a research node asks for is a tier the table translates. */
    @Test
    void everyResearchCircuitIsATier() {
        int asked = 0;
        for (ResearchTree.Node n : ResearchTree.all()) for (ResearchTree.Cost c : n.costs) {
            if (!c.ore || !c.name.startsWith("circuit")) continue;
            asked++;
            assertTrue(CircuitTiers.tierOf(c.name) >= 0, n.id + " asks for " + c.name + ", which GT never registers");
        }
        assertTrue(asked >= 20, "every phase's nodes take circuits");
        boolean luv = false;
        for (ResearchTree.Cost c : ResearchTree.get(ResearchTree.PHASE_4).costs) luv |= "circuitLuV".equals(c.name);
        assertTrue(luv, "phase IV takes LuV circuits");
    }
}
