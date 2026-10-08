package com.fluxecho.bees;

import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;

import com.fluxecho.FluxEcho;
import com.fluxecho.core.EchoText;
import com.fluxecho.logic.Chromosomes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import forestry.api.apiculture.IAlleleBeeSpecies;
import forestry.api.genetics.AlleleManager;
import forestry.api.genetics.IAllele;

/**
 * A bee imprint: every gene of a real bee, taken in the Bee Imprinter without harming it. The Larva Incubator grows
 * pristine princesses and drones from it; the imprinter can also copy single genes from another bee onto it.
 */
public class ItemBeeImprint extends Item {

    public ItemBeeImprint() {
        setUnlocalizedName("fluxecho.bee_imprint");
        setTextureName(FluxEcho.MODID + ":bee_imprint");
        setCreativeTab(FluxEcho.TAB);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        Map<String, String> genes = BeeImprints.read(stack);
        IAlleleBeeSpecies species = genes == null ? null : BeeImprints.species(genes);
        if (species == null) return super.getItemStackDisplayName(stack);
        return EchoText.t("bee_imprint.of", species.getName());
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List tip, boolean advanced) {
        Map<String, String> genes = BeeImprints.read(stack);
        if (genes == null) {
            tip.addAll(EchoText.lines("bee_imprint.blank"));
            return;
        }
        if (BeeImprints.species(genes) == null) {
            tip.add(EnumChatFormatting.RED + EchoText.t("bee_imprint.unknown", genes.get(Chromosomes.SPECIES)));
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            for (String name : Chromosomes.TRAITS) {
                String uid = genes.get(name);
                IAllele a = uid == null ? null : AlleleManager.alleleRegistry.getAllele(uid);
                String value = a != null ? a.getName() : EnumChatFormatting.RED + String.valueOf(uid);
                tip.add(EchoText.t("gene." + name) + ": " + EnumChatFormatting.WHITE + value);
            }
        } else {
            tip.add(EnumChatFormatting.DARK_GRAY + EchoText.t("bee_imprint.shift"));
        }
        if (!BeeImprints.missing(genes)
            .isEmpty()) tip.add(EnumChatFormatting.GOLD + EchoText.t("bee_imprint.missing"));
    }
}
