package com.fluxecho.logic;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The genes on a bee imprint, by Forestry's chromosome names ({@code EnumBeeChromosome.name()}), and which of them a
 * programmed circuit copies from the sample onto a base imprint. The species is never copied, and the unused HUMIDITY
 * chromosome is not kept at all. Trees and butterflies have their own lists in {@link Karyotype}.
 */
public final class Chromosomes {

    public static final String SPECIES = "SPECIES";

    /** Every gene but the species, in circuit order: circuit 1 is SPEED ... circuit 12 is EFFECT. */
    public static final List<String> TRAITS = Collections.unmodifiableList(
        Arrays.asList(
            "SPEED",
            "LIFESPAN",
            "FERTILITY",
            "TEMPERATURE_TOLERANCE",
            "NOCTURNAL",
            "HUMIDITY_TOLERANCE",
            "TOLERANT_FLYER",
            "CAVE_DWELLING",
            "FLOWER_PROVIDER",
            "FLOWERING",
            "TERRITORY",
            "EFFECT"));

    /** Circuit 13: what decides where a bee works (climate, day and night, rain, sky). */
    public static final List<String> ENVIRONMENT = Collections.unmodifiableList(
        Arrays.asList("TEMPERATURE_TOLERANCE", "NOCTURNAL", "HUMIDITY_TOLERANCE", "TOLERANT_FLYER", "CAVE_DWELLING"));

    public static final int CIRCUIT_ENVIRONMENT = 13, CIRCUIT_ALL = 14;

    private Chromosomes() {}

    /** Every gene a bee imprint holds: the species, then the traits. */
    public static List<String> all() {
        return Karyotype.BEE.all();
    }

    /** The genes a circuit copies on a bee imprint; empty for no circuit or an unknown one. */
    public static List<String> forCircuit(int circuit) {
        return Karyotype.BEE.forCircuit(circuit);
    }

    /**
     * The base imprint with the named genes taken from the donor. A gene the donor lacks stays as it was; the species
     * always stays.
     */
    public static Map<String, String> transfer(Map<String, String> base, Map<String, String> donor,
        List<String> genes) {
        Map<String, String> out = new LinkedHashMap<>(base);
        for (String g : genes) {
            if (SPECIES.equals(g)) continue;
            String uid = donor.get(g);
            if (uid != null) out.put(g, uid);
        }
        return out;
    }
}
