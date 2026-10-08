package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The genes an imprint keeps for one Forestry species root (bees, trees, butterflies), by the chromosome names of
 * that root's enum, and which of them a programmed circuit copies: circuit N is the N-th trait, 13 the environment
 * (bees only: a butterfly's 13th trait is its territory) and 14 every trait. The species is never copied.
 */
public final class Karyotype {

    public static final String BEES = "rootBees", TREES = "rootTrees", BUTTERFLIES = "rootButterflies";

    public static final int CIRCUIT_ENVIRONMENT = 13, CIRCUIT_ALL = 14;

    public static final Karyotype BEE = new Karyotype(BEES, Chromosomes.TRAITS, Chromosomes.ENVIRONMENT);
    public static final Karyotype TREE = new Karyotype(
        TREES,
        Arrays.asList(
            "GROWTH",
            "HEIGHT",
            "FERTILITY",
            "FRUITS",
            "YIELD",
            "PLANT",
            "SAPPINESS",
            "TERRITORY",
            "EFFECT",
            "MATURATION",
            "GIRTH",
            "FIREPROOF"),
        Collections.emptyList());
    public static final Karyotype BUTTERFLY = new Karyotype(
        BUTTERFLIES,
        Arrays.asList(
            "SIZE",
            "SPEED",
            "LIFESPAN",
            "METABOLISM",
            "FERTILITY",
            "TEMPERATURE_TOLERANCE",
            "HUMIDITY_TOLERANCE",
            "NOCTURNAL",
            "TOLERANT_FLYER",
            "FIRE_RESIST",
            "FLOWER_PROVIDER",
            "EFFECT",
            "TERRITORY"),
        Collections.emptyList());

    /** Forestry's uid of the species root. */
    public final String root;
    /** Every gene but the species, in circuit order. */
    public final List<String> traits;
    /** What circuit 13 copies; empty when the root has no such set. */
    public final List<String> environment;

    private Karyotype(String root, List<String> traits, List<String> environment) {
        this.root = root;
        this.traits = Collections.unmodifiableList(new ArrayList<>(traits));
        this.environment = Collections.unmodifiableList(new ArrayList<>(environment));
    }

    /** The karyotype of a root uid; null (an imprint from before trees) means bees, an unknown uid null. */
    public static Karyotype of(String root) {
        if (root == null || root.isEmpty() || BEES.equals(root)) return BEE;
        if (TREES.equals(root)) return TREE;
        if (BUTTERFLIES.equals(root)) return BUTTERFLY;
        return null;
    }

    /** Every gene an imprint holds: the species, then the traits. */
    public List<String> all() {
        List<String> a = new ArrayList<>(traits.size() + 1);
        a.add(Chromosomes.SPECIES);
        a.addAll(traits);
        return a;
    }

    /** The genes a circuit copies; empty for no circuit or one this root does not use. */
    public List<String> forCircuit(int circuit) {
        if (circuit == CIRCUIT_ALL) return traits;
        if (circuit == CIRCUIT_ENVIRONMENT && !environment.isEmpty()) return environment;
        if (circuit >= 1 && circuit <= traits.size()) return Collections.singletonList(traits.get(circuit - 1));
        return Collections.emptyList();
    }
}
