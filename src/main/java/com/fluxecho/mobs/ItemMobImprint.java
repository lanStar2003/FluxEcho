package com.fluxecho.mobs;

import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.fluxecho.FluxEcho;
import com.fluxecho.core.EchoText;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A prey imprint. A blank one gets written when its holder kills a mob, or when they right-click a living mob of a
 * kind their statistics say they have killed before. The Prey Echo drops the loot of the mob on it.
 */
public class ItemMobImprint extends Item {

    public ItemMobImprint() {
        setUnlocalizedName("fluxecho.mob_imprint");
        setTextureName(FluxEcho.MODID + ":mob_imprint");
        setCreativeTab(FluxEcho.TAB);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        String mob = MobImprints.mob(stack);
        return mob == null ? super.getItemStackDisplayName(stack)
            : EchoText.t("mob_imprint.of", MobImprints.displayName(mob));
    }

    @Override
    public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer player, EntityLivingBase target) {
        if (!MobImprints.isBlank(stack)) return false;
        if (player instanceof EntityPlayerMP p) MobEvents.writeFromStats(p, stack, target);
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List tip, boolean advanced) {
        String mob = MobImprints.mob(stack);
        if (mob == null) tip.addAll(EchoText.lines("mob_imprint.blank"));
        else tip.add(EnumChatFormatting.DARK_GRAY + mob);
    }
}
