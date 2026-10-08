package com.fluxecho.bees;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.fluxecho.logic.GeneSplice;
import com.fluxecho.logic.Karyotype;

/**
 * Gene samples: some genes of a bee, tree or butterfly (never the species), written like an imprint's genes (see
 * {@link BeeImprints}). Taken in the Bee Imprinter with a programmed circuit; put together with an imprint, or with
 * other samples, in a crafting grid or the Gene Assembler ({@link #splice}).
 */
public final class GeneSamples {

    private static final String GENES = "Genes", ROOT = "Root";

    private GeneSamples() {}

    public static boolean is(ItemStack s) {
        return s != null && BeeModule.geneSample != null && s.getItem() == BeeModule.geneSample;
    }

    /** rootBees (also when the tag says nothing), rootTrees or rootButterflies. */
    public static String root(ItemStack s) {
        NBTTagCompound t = s.getTagCompound();
        return t != null && t.hasKey(ROOT) ? t.getString(ROOT) : Karyotype.BEES;
    }

    /** The genes on a sample, in the karyotype's order; null for a blank or unreadable one. */
    public static Map<String, String> genes(ItemStack s) {
        NBTTagCompound tag = is(s) ? s.getTagCompound() : null;
        Karyotype k = tag == null ? null : Karyotype.of(root(s));
        if (k == null || !tag.hasKey(GENES)) return null;
        NBTTagCompound g = tag.getCompoundTag(GENES);
        Map<String, String> out = new LinkedHashMap<>();
        for (String name : k.traits) if (g.hasKey(name)) out.put(name, g.getString(name));
        return out.isEmpty() ? null : out;
    }

    public static ItemStack of(String root, Map<String, String> genes) {
        Karyotype k = Karyotype.of(root);
        NBTTagCompound g = new NBTTagCompound();
        for (Map.Entry<String, String> e : GeneSplice.ordered(k, genes)
            .entrySet()) g.setString(e.getKey(), e.getValue());
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag(GENES, g);
        if (!Karyotype.BEES.equals(root)) tag.setString(ROOT, root);
        ItemStack s = new ItemStack(BeeModule.geneSample);
        s.setTagCompound(tag);
        return s;
    }

    /** What putting stacks together gives: a result, or the lang key (under fluxecho.status.) of why not. */
    public static final class Splice {

        public final ItemStack result;
        public final String problem;

        private Splice(ItemStack result, String problem) {
            this.result = result;
            this.problem = problem;
        }

        static Splice no(String problem) {
            return new Splice(null, problem);
        }
    }

    /**
     * One imprint and samples: the imprint with the samples' genes written over it (the species stays). Samples only
     * (two or more): one sample with all their genes. Everything must be of one kind (bee, tree, butterfly), and no
     * two samples may disagree on a gene.
     */
    public static Splice splice(List<ItemStack> stacks) {
        ItemStack imprint = null;
        String root = null;
        List<Map<String, String>> samples = new ArrayList<>();
        for (ItemStack s : stacks) {
            String r;
            if (BeeImprints.isImprint(s)) {
                if (imprint != null) return Splice.no("splice_two_imprints");
                if (BeeImprints.read(s) == null) return Splice.no("splice_blank");
                imprint = s;
                r = BeeImprints.root(s);
            } else if (is(s)) {
                Map<String, String> g = genes(s);
                if (g == null) return Splice.no("splice_blank");
                samples.add(g);
                r = root(s);
            } else {
                return Splice.no("splice_foreign");
            }
            if (root == null) root = r;
            else if (!root.equals(r)) return Splice.no("splice_kinds");
        }
        if (samples.isEmpty() || imprint == null && samples.size() < 2) return Splice.no("splice_need_samples");
        Map<String, String> merged = GeneSplice.merge(samples);
        if (merged == null) return Splice.no("splice_conflict");
        ItemStack out = imprint == null ? of(root, merged)
            : BeeImprints.imprint(root, GeneSplice.apply(BeeImprints.read(imprint), merged));
        return new Splice(out, null);
    }
}
