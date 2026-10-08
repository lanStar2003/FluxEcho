package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ChromosomesTest {

    @Test
    void circuitsOneToTwelvePickOneGeneEach() {
        assertEquals(Collections.singletonList("SPEED"), Chromosomes.forCircuit(1));
        assertEquals(Collections.singletonList("TEMPERATURE_TOLERANCE"), Chromosomes.forCircuit(4));
        assertEquals(Collections.singletonList("HUMIDITY_TOLERANCE"), Chromosomes.forCircuit(6));
        assertEquals(Collections.singletonList("EFFECT"), Chromosomes.forCircuit(12));
    }

    @Test
    void circuitThirteenIsTheEnvironmentAndFourteenEveryTrait() {
        assertEquals(
            Arrays
                .asList("TEMPERATURE_TOLERANCE", "NOCTURNAL", "HUMIDITY_TOLERANCE", "TOLERANT_FLYER", "CAVE_DWELLING"),
            Chromosomes.forCircuit(13));
        assertEquals(Chromosomes.TRAITS, Chromosomes.forCircuit(14));
        assertEquals(12, Chromosomes.TRAITS.size());
    }

    @Test
    void noOrUnknownCircuitCopiesNothing() {
        assertTrue(
            Chromosomes.forCircuit(0)
                .isEmpty());
        assertTrue(
            Chromosomes.forCircuit(15)
                .isEmpty());
        assertTrue(
            Chromosomes.forCircuit(-1)
                .isEmpty());
    }

    @Test
    void transferNeverTouchesTheSpecies() {
        Map<String, String> base = new HashMap<>();
        base.put("SPECIES", "forestry.speciesForest");
        base.put("SPEED", "forestry.speedSlowest");
        base.put("NOCTURNAL", "forestry.boolFalse");
        Map<String, String> donor = new HashMap<>();
        donor.put("SPECIES", "gregtech.bee.speciesClay");
        donor.put("SPEED", "forestry.speedFastest");
        donor.put("NOCTURNAL", "forestry.boolTrue");

        Map<String, String> all = Chromosomes.transfer(base, donor, Chromosomes.forCircuit(14));
        assertEquals("forestry.speciesForest", all.get("SPECIES"));
        assertEquals("forestry.speedFastest", all.get("SPEED"));
        assertEquals("forestry.boolTrue", all.get("NOCTURNAL"));

        Map<String, String> one = Chromosomes.transfer(base, donor, Chromosomes.forCircuit(1));
        assertEquals("forestry.speedFastest", one.get("SPEED"));
        assertEquals("forestry.boolFalse", one.get("NOCTURNAL"));
        assertEquals(
            "forestry.speciesForest",
            Chromosomes.transfer(base, donor, Arrays.asList("SPECIES"))
                .get("SPECIES"));
    }

    @Test
    void geneMissingOnTheDonorStays() {
        Map<String, String> base = new HashMap<>();
        base.put("EFFECT", "forestry.effectNone");
        assertEquals(
            "forestry.effectNone",
            Chromosomes.transfer(base, new HashMap<>(), Chromosomes.forCircuit(12))
                .get("EFFECT"));
    }

    @Test
    void allStartsWithTheSpecies() {
        assertEquals(
            "SPECIES",
            Chromosomes.all()
                .get(0));
        assertEquals(
            13,
            Chromosomes.all()
                .size());
    }
}
