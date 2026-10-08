package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class GeneSpliceTest {

    private static Map<String, String> bee(String prefix) {
        Map<String, String> g = new LinkedHashMap<>();
        g.put(Chromosomes.SPECIES, prefix + ".species");
        for (String t : Chromosomes.TRAITS) g.put(t, prefix + "." + t);
        return g;
    }

    private static Map<String, String> genes(String... kv) {
        Map<String, String> g = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) g.put(kv[i], kv[i + 1]);
        return g;
    }

    @Test
    void extractTakesTheCircuitsGenesNeverTheSpecies() {
        Map<String, String> forest = bee("forest");
        assertEquals(genes("SPEED", "forest.SPEED"), GeneSplice.extract(forest, Karyotype.BEE.forCircuit(1)));
        Map<String, String> env = GeneSplice.extract(forest, Karyotype.BEE.forCircuit(Karyotype.CIRCUIT_ENVIRONMENT));
        assertEquals(
            Chromosomes.ENVIRONMENT,
            Arrays.asList(
                env.keySet()
                    .toArray()));
        Map<String, String> all = GeneSplice.extract(forest, Karyotype.BEE.forCircuit(Karyotype.CIRCUIT_ALL));
        assertEquals(Chromosomes.TRAITS.size(), all.size());
        assertTrue(!all.containsKey(Chromosomes.SPECIES));
        assertTrue(
            GeneSplice.extract(forest, Collections.singletonList(Chromosomes.SPECIES))
                .isEmpty());
    }

    @Test
    void extractSkipsGenesTheDonorLacks() {
        Map<String, String> partial = genes("SPEED", "fast");
        assertTrue(
            GeneSplice.extract(partial, Karyotype.BEE.forCircuit(2))
                .isEmpty());
    }

    @Test
    void mergeJoinsSamples() {
        Map<String, String> m = GeneSplice
            .merge(Arrays.asList(genes("SPEED", "fast"), genes("LIFESPAN", "short", "FERTILITY", "four")));
        assertEquals(genes("SPEED", "fast", "LIFESPAN", "short", "FERTILITY", "four"), m);
    }

    @Test
    void mergeAcceptsAgreeingSamplesAndRefusesDisagreeingOnes() {
        assertEquals(
            genes("SPEED", "fast"),
            GeneSplice.merge(Arrays.asList(genes("SPEED", "fast"), genes("SPEED", "fast"))));
        assertNull(GeneSplice.merge(Arrays.asList(genes("SPEED", "fast"), genes("SPEED", "slow"))));
    }

    @Test
    void mergeDropsTheSpecies() {
        Map<String, String> m = GeneSplice.merge(Collections.singletonList(genes("SPECIES", "x", "SPEED", "fast")));
        assertEquals(genes("SPEED", "fast"), m);
    }

    @Test
    void applyWritesSampleGenesOverTheImprintAndKeepsTheSpecies() {
        Map<String, String> meadows = bee("meadows");
        Map<String, String> out = GeneSplice
            .apply(meadows, genes("SPEED", "fast", "SPECIES", "forest.species", "EFFECT", "none"));
        assertEquals("meadows.species", out.get(Chromosomes.SPECIES));
        assertEquals("fast", out.get("SPEED"));
        assertEquals("none", out.get("EFFECT"));
        assertEquals("meadows.LIFESPAN", out.get("LIFESPAN"));
        assertEquals("meadows.SPEED", meadows.get("SPEED"));
    }

    @Test
    void orderedFollowsTheKaryotype() {
        Map<String, String> g = genes("EFFECT", "e", "SPEED", "s", "NOT_A_GENE", "x");
        assertEquals(
            Arrays.asList("SPEED", "EFFECT"),
            Arrays.asList(
                GeneSplice.ordered(Karyotype.BEE, g)
                    .keySet()
                    .toArray()));
    }

    @Test
    void shapes() {
        assertEquals(GeneSplice.Shape.ONE, GeneSplice.shape(Karyotype.BEE, Collections.singletonList("SPEED")));
        List<String> env = Chromosomes.ENVIRONMENT;
        assertEquals(GeneSplice.Shape.ENVIRONMENT, GeneSplice.shape(Karyotype.BEE, env));
        assertEquals(GeneSplice.Shape.TRAITS, GeneSplice.shape(Karyotype.BEE, Chromosomes.TRAITS));
        assertEquals(GeneSplice.Shape.SET, GeneSplice.shape(Karyotype.BEE, Arrays.asList("SPEED", "EFFECT")));
        assertEquals(GeneSplice.Shape.TRAITS, GeneSplice.shape(Karyotype.TREE, Karyotype.TREE.traits));
        assertEquals(GeneSplice.Shape.SET, GeneSplice.shape(Karyotype.TREE, Arrays.asList("GROWTH", "HEIGHT")));
    }
}
