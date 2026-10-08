package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gene samples, as in a gene template: some of a root's genes (never the species), taken from a bee, sapling or
 * butterfly by a programmed circuit, merged with other samples, and written over an imprint. Two samples that hold
 * different alleles of the same gene do not go together.
 */
public final class GeneSplice {

    /** What a sample holds, for its name. */
    public enum Shape {
        /** A single gene. */
        ONE,
        /** Exactly the root's environment genes (bees: circuit 13). */
        ENVIRONMENT,
        /** Every gene but the species (circuit 14). */
        TRAITS,
        /** Any other set. */
        SET
    }

    private GeneSplice() {}

    /** The donor's genes in the list (in the karyotype's order), those it has; never the species. */
    public static Map<String, String> extract(Map<String, String> donor, List<String> genes) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String g : genes) {
            if (Chromosomes.SPECIES.equals(g)) continue;
            String uid = donor.get(g);
            if (uid != null) out.put(g, uid);
        }
        return out;
    }

    /** The samples' genes together; null when two of them disagree on a gene. The species is dropped. */
    public static Map<String, String> merge(Collection<Map<String, String>> samples) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map<String, String> s : samples) for (Map.Entry<String, String> e : s.entrySet()) {
            if (Chromosomes.SPECIES.equals(e.getKey())) continue;
            String had = out.putIfAbsent(e.getKey(), e.getValue());
            if (had != null && !had.equals(e.getValue())) return null;
        }
        return out;
    }

    /** The imprint's genes with the sample's written over them; the species stays. */
    public static Map<String, String> apply(Map<String, String> imprint, Map<String, String> sample) {
        return Chromosomes.transfer(imprint, sample, new ArrayList<>(sample.keySet()));
    }

    /** The genes in the karyotype's order, unknown chromosome names dropped. */
    public static Map<String, String> ordered(Karyotype k, Map<String, String> genes) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String g : k.traits) if (genes.containsKey(g)) out.put(g, genes.get(g));
        return out;
    }

    public static Shape shape(Karyotype k, Collection<String> genes) {
        if (genes.size() == 1) return Shape.ONE;
        HashSet<String> set = new HashSet<>(genes);
        if (!k.environment.isEmpty() && set.equals(new HashSet<>(k.environment))) return Shape.ENVIRONMENT;
        if (set.equals(new HashSet<>(k.traits))) return Shape.TRAITS;
        return Shape.SET;
    }
}
