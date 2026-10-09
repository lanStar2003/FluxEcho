package com.fluxecho.library;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;

import com.fluxecho.FluxEcho;
import com.fluxecho.core.EchoText;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Library index card: blank, or written by an Echo Library for one of the samples it keeps. A written card in an echo
 * machine's sample slot stands for that sample while a docked library of the machine's team keeps it
 * ({@link Lending}): one real sample, any number of machines.
 */
public class ItemLibraryCard extends Item {

    private static final String SAMPLE = "Sample", TEAM = "Team";

    @SideOnly(Side.CLIENT)
    private IIcon written;

    public ItemLibraryCard() {
        setUnlocalizedName("fluxecho.library_card");
        setTextureName(FluxEcho.MODID + ":library_card");
        setCreativeTab(FluxEcho.TAB);
    }

    /** A card written for the sample, for the team. */
    public static ItemStack write(ItemStack sample, UUID team) {
        ItemStack card = new ItemStack(LibraryModule.card);
        NBTTagCompound t = new NBTTagCompound();
        ItemStack one = sample.copy();
        one.stackSize = 1;
        t.setTag(SAMPLE, one.writeToNBT(new NBTTagCompound()));
        if (team != null) t.setString(TEAM, team.toString());
        card.setTagCompound(t);
        return card;
    }

    public static boolean written(ItemStack card) {
        return card != null && card.getItem() instanceof ItemLibraryCard
            && card.hasTagCompound()
            && card.getTagCompound()
                .hasKey(SAMPLE);
    }

    /** The sample the card stands for, read from it (a fresh copy). */
    public static ItemStack sample(ItemStack card) {
        if (!written(card)) return null;
        return ItemStack.loadItemStackFromNBT(
            card.getTagCompound()
                .getCompoundTag(SAMPLE));
    }

    /** The NBT of the sample as written: the key for the cache in {@link Lending}. */
    static NBTTagCompound sampleTag(ItemStack card) {
        return written(card) ? card.getTagCompound()
            .getCompoundTag(SAMPLE) : null;
    }

    public static UUID team(ItemStack card) {
        if (!written(card)) return null;
        try {
            return UUID.fromString(
                card.getTagCompound()
                    .getString(TEAM));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return written(stack) ? EnumRarity.uncommon : EnumRarity.common;
    }

    @Override
    public int getItemStackLimit(ItemStack stack) {
        return written(stack) ? 1 : 64;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister r) {
        itemIcon = r.registerIcon(FluxEcho.MODID + ":library_card");
        written = r.registerIcon(FluxEcho.MODID + ":library_card_written");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconIndex(ItemStack stack) {
        return written(stack) ? written : itemIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(ItemStack stack, int pass) {
        return getIconIndex(stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        list.add(new ItemStack(item));
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List tip, boolean advanced) {
        ItemStack s = sample(stack);
        if (s != null) {
            tip.add(EchoText.t("library_card.for", s.getDisplayName()));
            tip.addAll(EchoText.lines("library_card.written_tip"));
        } else tip.addAll(EchoText.lines("library_card.tip"));
    }
}
