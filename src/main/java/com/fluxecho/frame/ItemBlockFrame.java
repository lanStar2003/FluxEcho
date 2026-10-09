package com.fluxecho.frame;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import com.fluxecho.core.EchoText;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** The flux frame's parts as items, each with its own name and what it is for. */
public class ItemBlockFrame extends ItemBlock {

    public ItemBlockFrame(Block block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    @Override
    public int getMetadata(int meta) {
        return meta;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        int m = stack.getItemDamage();
        return "tile.fluxecho.frame." + BlockFrame.NAMES[m >= 0 && m < BlockFrame.TYPES ? m : 0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List tip, boolean advanced) {
        int m = stack.getItemDamage();
        tip.addAll(EchoText.lines("frame." + BlockFrame.NAMES[m >= 0 && m < BlockFrame.TYPES ? m : 0] + ".tip"));
    }
}
