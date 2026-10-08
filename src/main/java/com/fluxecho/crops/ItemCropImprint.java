package com.fluxecho.crops;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.fluxecho.FluxEcho;
import com.fluxecho.core.EchoText;
import com.fluxecho.logic.CropStats;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ic2.api.crops.CropCard;

/**
 * A crop imprint: the crop and the stats of a fully scanned seed bag, taken in the Seed Imprinter without using the
 * seeds up. The Seed Echo makes seed bags of it.
 */
public class ItemCropImprint extends Item {

    public ItemCropImprint() {
        setUnlocalizedName("fluxecho.crop_imprint");
        setTextureName(FluxEcho.MODID + ":crop_imprint");
        setCreativeTab(FluxEcho.TAB);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        CropCard crop = CropImprints.crop(stack);
        return crop == null ? super.getItemStackDisplayName(stack)
            : EchoText.t("crop_imprint.of", CropImprints.name(crop));
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List tip, boolean advanced) {
        if (CropImprints.crop(stack) == null) {
            tip.addAll(EchoText.lines("crop_imprint.blank"));
            return;
        }
        CropStats s = CropImprints.stats(stack);
        tip.add(EnumChatFormatting.GRAY + EchoText.t("crop_imprint.stats", s.growth, s.gain, s.resistance));
    }
}
