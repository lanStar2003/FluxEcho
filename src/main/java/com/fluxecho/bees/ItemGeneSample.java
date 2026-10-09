package com.fluxecho.bees;

import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.fluxecho.FluxEcho;
import com.fluxecho.core.EchoText;
import com.fluxecho.logic.GeneSplice;
import com.fluxecho.logic.Karyotype;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import forestry.api.genetics.AlleleManager;
import forestry.api.genetics.IAllele;
import forestry.api.genetics.IAlleleBoolean;

/**
 * A gene sample: the genes a programmed circuit picked out of a real bee, sapling or butterfly in the Bee Imprinter.
 * Put it in a crafting grid (or the Gene Assembler) with an imprint of the same kind to write its genes over the
 * imprint's, or with other samples to make one sample of them all.
 */
public class ItemGeneSample extends Item {

    public ItemGeneSample() {
        setUnlocalizedName("fluxecho.gene_sample");
        setTextureName(FluxEcho.MODID + ":gene_sample");
        setCreativeTab(FluxEcho.TAB);
    }

    /** {@code bee}, {@code tree} or {@code butterfly}. */
    private static String kind(String root) {
        if (Karyotype.TREES.equals(root)) return "tree";
        if (Karyotype.BUTTERFLIES.equals(root)) return "butterfly";
        return "bee";
    }

    /**
     * A trait's value as the player reads it. Forestry names most alleles in the player's language already; its
     * yes/no alleles only carry a lang key ("forestry.allele.false") that Forestry never shows, writing yes or no
     * itself, and so does this.
     */
    static String allele(String uid) {
        IAllele a = uid == null ? null : AlleleManager.alleleRegistry.getAllele(uid);
        if (a == null) return EnumChatFormatting.RED + String.valueOf(uid);
        if (a instanceof IAlleleBoolean b) return EchoText.t(b.getValue() ? "gene.yes" : "gene.no");
        String name = a.getName();
        return name != null && StatCollector.canTranslate(name) ? StatCollector.translateToLocal(name) : name;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        Map<String, String> genes = GeneSamples.genes(stack);
        if (genes == null) return super.getItemStackDisplayName(stack);
        String root = GeneSamples.root(stack);
        String kind = EchoText.t("gene_sample." + kind(root));
        GeneSplice.Shape shape = GeneSplice.shape(Karyotype.of(root), genes.keySet());
        if (shape == GeneSplice.Shape.ONE) {
            Map.Entry<String, String> g = genes.entrySet()
                .iterator()
                .next();
            return EchoText.t("gene_sample.one", kind, EchoText.t("gene." + g.getKey()), allele(g.getValue()));
        }
        String what = EchoText.t(
            "gene_sample.shape." + shape.name()
                .toLowerCase());
        return EchoText.t("gene_sample.many", kind, what, genes.size());
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List tip, boolean advanced) {
        Map<String, String> genes = GeneSamples.genes(stack);
        if (genes == null) {
            tip.addAll(EchoText.lines("gene_sample.blank"));
            return;
        }
        for (Map.Entry<String, String> g : genes.entrySet())
            tip.add(EchoText.t("gene." + g.getKey()) + ": " + EnumChatFormatting.WHITE + allele(g.getValue()));
        tip.addAll(EchoText.lines("gene_sample.use"));
    }
}
