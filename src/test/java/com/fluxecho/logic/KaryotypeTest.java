package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.jupiter.api.Test;

class KaryotypeTest {

    @Test
    void beesKeepTheirOldCircuits() {
        for (int c = 0; c <= 16; c++) assertEquals(Chromosomes.forCircuit(c), Karyotype.BEE.forCircuit(c));
        assertEquals(Chromosomes.all(), Karyotype.BEE.all());
    }

    @Test
    void anImprintWithoutARootIsABee() {
        assertSame(Karyotype.BEE, Karyotype.of(null));
        assertSame(Karyotype.BEE, Karyotype.of(""));
        assertSame(Karyotype.TREE, Karyotype.of("rootTrees"));
        assertSame(Karyotype.BUTTERFLY, Karyotype.of("rootButterflies"));
        assertNull(Karyotype.of("rootFlowers"));
    }

    @Test
    void treesHaveNoEnvironmentCircuit() {
        assertEquals(12, Karyotype.TREE.traits.size());
        assertEquals(Collections.singletonList("GROWTH"), Karyotype.TREE.forCircuit(1));
        assertEquals(Collections.singletonList("FIREPROOF"), Karyotype.TREE.forCircuit(12));
        assertTrue(
            Karyotype.TREE.forCircuit(13)
                .isEmpty());
        assertEquals(Karyotype.TREE.traits, Karyotype.TREE.forCircuit(14));
    }

    @Test
    void aButterflysThirteenthTraitIsItsTerritory() {
        assertEquals(13, Karyotype.BUTTERFLY.traits.size());
        assertEquals(Collections.singletonList("TERRITORY"), Karyotype.BUTTERFLY.forCircuit(13));
        assertEquals(Karyotype.BUTTERFLY.traits, Karyotype.BUTTERFLY.forCircuit(14));
        assertEquals(
            "SPECIES",
            Karyotype.BUTTERFLY.all()
                .get(0));
    }
}
