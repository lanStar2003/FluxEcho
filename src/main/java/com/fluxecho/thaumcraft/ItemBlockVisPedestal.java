package com.fluxecho.thaumcraft;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import com.fluxecho.Config;
import com.fluxecho.core.EchoText;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** The Flux Vis Pedestal as an item: GT's "Machine Type" line and how to use it. */
public class ItemBlockVisPedestal extends ItemBlock {

    public ItemBlockVisPedestal(Block block) {
        super(block);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack s, EntityPlayer player, List tip, boolean advanced) {
        tip.add(EchoText.machineType("vis_pedestal"));
        tip.addAll(
            EchoText.lines(
                "vis_pedestal.tip",
                Config.visPedestalRate * 20 / 100.0,
                Config.visEuPerCentiVis * 100,
                Config.visPedestalBuffer,
                TileVisPedestal.MODULE_SLOTS));
    }
}
