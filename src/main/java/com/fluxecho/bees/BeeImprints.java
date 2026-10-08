package com.fluxecho.bees;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.fluxecho.logic.Chromosomes;
import com.fluxecho.logic.Karyotype;

import forestry.api.apiculture.BeeManager;
import forestry.api.apiculture.EnumBeeType;
import forestry.api.apiculture.IBee;
import forestry.api.apiculture.IBeeGenome;
import forestry.api.arboriculture.EnumGermlingType;
import forestry.api.arboriculture.ITree;
import forestry.api.arboriculture.ITreeRoot;
import forestry.api.genetics.AlleleManager;
import forestry.api.genetics.IAllele;
import forestry.api.genetics.IAlleleSpecies;
import forestry.api.genetics.IChromosomeType;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IIndividual;
import forestry.api.genetics.ISpeciesRoot;
import forestry.api.lepidopterology.EnumFlutterType;
import forestry.api.lepidopterology.IButterfly;
import forestry.api.lepidopterology.IButterflyRoot;

/**
 * Between Forestry and FluxEcho's gene maps (chromosome name to allele uid, see {@link Karyotype}): reading the genes
 * of a bee, sapling or butterfly or of an imprint, writing imprints, and growing new ones from genes. An imprint
 * names its species root unless it is a bee's, so bee imprints stay exactly as they were before trees came along.
 */
public final class BeeImprints {

    private static final String GENES = "Genes", ROOT = "Root";

    private BeeImprints() {}

    public static boolean isImprint(ItemStack s) {
        return s != null && BeeModule.imprint != null && s.getItem() == BeeModule.imprint;
    }

    /** The species root of a bee, sapling, pollen or butterfly that FluxEcho handles; null for anything else. */
    public static ISpeciesRoot rootOf(ItemStack s) {
        if (s == null || isImprint(s)) return null;
        ISpeciesRoot r = AlleleManager.alleleRegistry.getSpeciesRoot(s);
        return r != null && Karyotype.of(r.getUID()) != null && r.isMember(s) ? r : null;
    }

    public static boolean isBee(ItemStack s) {
        ISpeciesRoot r = rootOf(s);
        return r != null && Karyotype.BEES.equals(r.getUID());
    }

    /** The root an imprint or a member belongs to: rootBees, rootTrees, rootButterflies; null for anything else. */
    public static String root(ItemStack s) {
        if (isImprint(s)) {
            NBTTagCompound t = s.getTagCompound();
            return t != null && t.hasKey(ROOT) ? t.getString(ROOT) : Karyotype.BEES;
        }
        ISpeciesRoot r = rootOf(s);
        return r == null ? null : r.getUID();
    }

    /** The genes of a member (its active alleles) or of an imprint; null for anything else. */
    public static Map<String, String> genes(ItemStack s) {
        if (isImprint(s)) return read(s);
        ISpeciesRoot r = rootOf(s);
        IIndividual i = r == null ? null : r.getMember(s);
        return i == null ? null : genes(r, i.getGenome());
    }

    private static IChromosomeType chromosome(ISpeciesRoot root, String name) {
        for (IChromosomeType t : root.getKaryotype()) if (t instanceof Enum<?>e && e.name()
            .equals(name)) return t;
        return null;
    }

    private static Map<String, String> genes(ISpeciesRoot root, IGenome g) {
        Karyotype k = Karyotype.of(root.getUID());
        Map<String, String> out = new LinkedHashMap<>();
        for (String name : k.all()) {
            IChromosomeType t = chromosome(root, name);
            IAllele a = t == null ? null : g.getActiveAllele(t);
            if (a != null) out.put(name, a.getUID());
        }
        return out.containsKey(Chromosomes.SPECIES) ? out : null;
    }

    /** The genes of a species template (as registered with Forestry). */
    public static Map<String, String> genes(ISpeciesRoot root, IAllele[] template) {
        Karyotype k = Karyotype.of(root.getUID());
        Map<String, String> out = new LinkedHashMap<>();
        for (String name : k.all()) {
            IChromosomeType t = chromosome(root, name);
            int i = t == null ? -1 : t.ordinal();
            if (i >= 0 && i < template.length && template[i] != null) out.put(name, template[i].getUID());
        }
        return out;
    }

    /** The genes written on an imprint; null for a blank one. */
    public static Map<String, String> read(ItemStack s) {
        NBTTagCompound tag = s.getTagCompound();
        Karyotype k = Karyotype.of(root(s));
        if (tag == null || !tag.hasKey(GENES) || k == null) return null;
        NBTTagCompound g = tag.getCompoundTag(GENES);
        Map<String, String> out = new LinkedHashMap<>();
        for (String name : k.all()) if (g.hasKey(name)) out.put(name, g.getString(name));
        return out.containsKey(Chromosomes.SPECIES) ? out : null;
    }

    public static ItemStack imprint(String root, Map<String, String> genes) {
        NBTTagCompound g = new NBTTagCompound();
        for (Map.Entry<String, String> e : genes.entrySet()) g.setString(e.getKey(), e.getValue());
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag(GENES, g);
        if (!Karyotype.BEES.equals(root)) tag.setString(ROOT, root);
        ItemStack s = new ItemStack(BeeModule.imprint);
        s.setTagCompound(tag);
        return s;
    }

    /** A bee imprint. */
    public static ItemStack imprint(Map<String, String> genes) {
        return imprint(Karyotype.BEES, genes);
    }

    public static ISpeciesRoot speciesRoot(String root) {
        return root == null ? null : AlleleManager.alleleRegistry.getSpeciesRoot(root);
    }

    /** The species, or null when this pack does not have it (any more). */
    public static IAlleleSpecies species(Map<String, String> genes) {
        IAllele a = AlleleManager.alleleRegistry.getAllele(genes.get(Chromosomes.SPECIES));
        return a instanceof IAlleleSpecies s ? s : null;
    }

    /** Traits this pack does not have (renamed or removed); one grown from the genes gets the species' default. */
    public static List<String> missing(String root, Map<String, String> genes) {
        List<String> out = new ArrayList<>();
        ISpeciesRoot r = speciesRoot(root);
        Karyotype k = Karyotype.of(root);
        if (r == null || k == null) return out;
        for (String name : k.traits) {
            String uid = genes.get(name);
            if (uid != null && allele(r, name, uid) == null) out.add(name);
        }
        return out;
    }

    private static IAllele allele(ISpeciesRoot root, String chromosome, String uid) {
        IChromosomeType t = chromosome(root, chromosome);
        IAllele a = AlleleManager.alleleRegistry.getAllele(uid);
        return a != null && t != null
            && t.getAlleleClass()
                .isInstance(a) ? a : null;
    }

    /** The species' template, overwritten gene by gene; null when the species is unknown here. */
    private static IAllele[] template(ISpeciesRoot root, Map<String, String> genes) {
        IAlleleSpecies species = species(genes);
        Karyotype k = Karyotype.of(root.getUID());
        if (species == null || k == null || species.getRoot() != root) return null;
        IAllele[] t = root.getTemplate(species.getUID());
        t = (t != null ? t : root.getDefaultTemplate()).clone();
        t[0] = species;
        for (String name : k.traits) {
            String uid = genes.get(name);
            IAllele a = uid == null ? null : allele(root, name, uid);
            IChromosomeType c = chromosome(root, name);
            if (a != null && c != null && c.ordinal() < t.length) t[c.ordinal()] = a;
        }
        return t;
    }

    /**
     * A pristine, analysed, pure-bred bee with the genes: the species' template, overwritten gene by gene. Null when
     * the species is unknown here.
     */
    public static IBee bee(Map<String, String> genes, World world) {
        IAllele[] t = template(BeeManager.beeRoot, genes);
        if (t == null) return null;
        IBee bee = BeeManager.beeRoot.getBee(world, (IBeeGenome) BeeManager.beeRoot.templateAsGenome(t));
        bee.setIsNatural(true);
        bee.analyze();
        return bee;
    }

    public static ItemStack stack(IBee bee, EnumBeeType type, int count) {
        ItemStack s = BeeManager.beeRoot.getMemberStack(bee, type.ordinal());
        s.stackSize = count;
        return s;
    }

    /** Analysed saplings of the genes; null when the species is unknown here. */
    public static ItemStack saplings(Map<String, String> genes, int count) {
        ISpeciesRoot r = speciesRoot(Karyotype.TREES);
        IAllele[] t = r instanceof ITreeRoot tr ? template(tr, genes) : null;
        if (t == null) return null;
        ITree tree = ((ITreeRoot) r).templateAsIndividual(t);
        tree.analyze();
        ItemStack s = r.getMemberStack(tree, EnumGermlingType.SAPLING.ordinal());
        s.stackSize = count;
        return s;
    }

    /** Analysed butterflies of the genes; null when the species is unknown here. */
    public static ItemStack butterflies(Map<String, String> genes, int count) {
        ISpeciesRoot r = speciesRoot(Karyotype.BUTTERFLIES);
        IAllele[] t = r instanceof IButterflyRoot br ? template(br, genes) : null;
        if (t == null) return null;
        IButterfly b = ((IButterflyRoot) r).templateAsIndividual(t);
        b.analyze();
        ItemStack s = r.getMemberStack(b, EnumFlutterType.BUTTERFLY.ordinal());
        s.stackSize = count;
        return s;
    }

    /** One member of the species for an icon: a princess, a sapling, a butterfly. */
    public static ItemStack icon(String root, String species) {
        ISpeciesRoot r = speciesRoot(root);
        IAllele[] t = r == null ? null : r.getTemplate(species);
        if (t == null) return null;
        Map<String, String> genes = genes(r, t);
        if (Karyotype.BEES.equals(root)) {
            IBee bee = bee(genes, null);
            return bee == null ? null : stack(bee, EnumBeeType.PRINCESS, 1);
        }
        if (Karyotype.TREES.equals(root)) return saplings(genes, 1);
        return butterflies(genes, 1);
    }

    /** The species of a member or an imprint of the root, for the Codex. */
    public static String speciesKey(ItemStack s, String root) {
        if (!root.equals(root(s))) return null;
        Map<String, String> g = genes(s);
        return g == null ? null : g.get(Chromosomes.SPECIES);
    }

    public static String speciesName(String uid) {
        IAllele a = AlleleManager.alleleRegistry.getAllele(uid);
        return a == null ? uid : a.getName();
    }
}
