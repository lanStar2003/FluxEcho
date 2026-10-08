package com.fluxecho.bees;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.fluxecho.logic.Chromosomes;

import forestry.api.apiculture.BeeManager;
import forestry.api.apiculture.EnumBeeChromosome;
import forestry.api.apiculture.EnumBeeType;
import forestry.api.apiculture.IAlleleBeeSpecies;
import forestry.api.apiculture.IBee;
import forestry.api.apiculture.IBeeGenome;
import forestry.api.genetics.AlleleManager;
import forestry.api.genetics.IAllele;

/**
 * Between Forestry and FluxEcho's gene maps (chromosome name to allele uid, see {@link Chromosomes}): reading the
 * genes of a bee or an imprint, writing imprints, and growing bees from genes.
 */
public final class BeeImprints {

    private static final String GENES = "Genes";

    private BeeImprints() {}

    public static boolean isImprint(ItemStack s) {
        return s != null && BeeModule.imprint != null && s.getItem() == BeeModule.imprint;
    }

    public static boolean isBee(ItemStack s) {
        return s != null && BeeManager.beeRoot != null && BeeManager.beeRoot.isMember(s);
    }

    /** The genes of a bee (its active alleles) or of an imprint; null for anything else. */
    public static Map<String, String> genes(ItemStack s) {
        if (isImprint(s)) return read(s);
        if (!isBee(s)) return null;
        IBee bee = BeeManager.beeRoot.getMember(s);
        return bee == null ? null : genes(bee.getGenome());
    }

    private static Map<String, String> genes(IBeeGenome g) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String name : Chromosomes.all()) {
            IAllele a = g.getActiveAllele(EnumBeeChromosome.valueOf(name));
            if (a != null) out.put(name, a.getUID());
        }
        return out.containsKey(Chromosomes.SPECIES) ? out : null;
    }

    /** The genes of a species template (as registered with Forestry). */
    public static Map<String, String> genes(IAllele[] template) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String name : Chromosomes.all()) {
            int i = EnumBeeChromosome.valueOf(name)
                .ordinal();
            if (i < template.length && template[i] != null) out.put(name, template[i].getUID());
        }
        return out;
    }

    /** The genes written on an imprint; null for a blank one. */
    public static Map<String, String> read(ItemStack s) {
        NBTTagCompound tag = s.getTagCompound();
        if (tag == null || !tag.hasKey(GENES)) return null;
        NBTTagCompound g = tag.getCompoundTag(GENES);
        Map<String, String> out = new LinkedHashMap<>();
        for (String name : Chromosomes.all()) if (g.hasKey(name)) out.put(name, g.getString(name));
        return out.containsKey(Chromosomes.SPECIES) ? out : null;
    }

    public static ItemStack imprint(Map<String, String> genes) {
        NBTTagCompound g = new NBTTagCompound();
        for (Map.Entry<String, String> e : genes.entrySet()) g.setString(e.getKey(), e.getValue());
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag(GENES, g);
        ItemStack s = new ItemStack(BeeModule.imprint);
        s.setTagCompound(tag);
        return s;
    }

    /** The species, or null when this pack does not have it (any more). */
    public static IAlleleBeeSpecies species(Map<String, String> genes) {
        IAllele a = AlleleManager.alleleRegistry.getAllele(genes.get(Chromosomes.SPECIES));
        return a instanceof IAlleleBeeSpecies s ? s : null;
    }

    /** Traits this pack does not have (renamed or removed); a bee grown from the genes gets the species' default. */
    public static List<String> missing(Map<String, String> genes) {
        List<String> out = new ArrayList<>();
        for (String name : Chromosomes.TRAITS) {
            String uid = genes.get(name);
            if (uid != null && allele(name, uid) == null) out.add(name);
        }
        return out;
    }

    private static IAllele allele(String chromosome, String uid) {
        IAllele a = AlleleManager.alleleRegistry.getAllele(uid);
        return a != null && EnumBeeChromosome.valueOf(chromosome)
            .getAlleleClass()
            .isInstance(a) ? a : null;
    }

    /**
     * A pristine, analysed, pure-bred bee with the genes: the species' template, overwritten gene by gene. Null when
     * the species is unknown here.
     */
    public static IBee bee(Map<String, String> genes, World world) {
        IAlleleBeeSpecies species = species(genes);
        if (species == null) return null;
        IAllele[] t = BeeManager.beeRoot.getTemplate(species.getUID());
        t = (t != null ? t : BeeManager.beeRoot.getDefaultTemplate()).clone();
        t[EnumBeeChromosome.SPECIES.ordinal()] = species;
        for (String name : Chromosomes.TRAITS) {
            String uid = genes.get(name);
            IAllele a = uid == null ? null : allele(name, uid);
            if (a != null) t[EnumBeeChromosome.valueOf(name)
                .ordinal()] = a;
        }
        IBee bee = BeeManager.beeRoot.getBee(world, BeeManager.beeRoot.templateAsGenome(t));
        bee.setIsNatural(true);
        bee.analyze();
        return bee;
    }

    public static ItemStack stack(IBee bee, EnumBeeType type, int count) {
        ItemStack s = BeeManager.beeRoot.getMemberStack(bee, type.ordinal());
        s.stackSize = count;
        return s;
    }
}
