package com.fluxecho.nexus;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import com.fluxecho.client.Keys;
import com.fluxecho.core.EchoText;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** A multiblock core as an item: what it is, and with Shift how to build it. */
public class ItemBlockNexus extends ItemBlock {

    private final String key;

    public ItemBlockNexus(Block block) {
        super(block);
        String name = block.getUnlocalizedName();
        key = name.substring(name.lastIndexOf('.') + 1);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List tip, boolean advanced) {
        tip.addAll(EchoText.lines(key + ".tip"));
        if (Keys.shift()) tip.addAll(EchoText.lines(key + ".structure"));
        else tip.add(EchoText.t("gui.shift_build"));
    }
}
