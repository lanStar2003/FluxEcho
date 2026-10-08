package com.fluxecho.codex;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.fluxecho.FluxEcho;
import com.fluxecho.core.EchoText;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** The Echo Codex: right-click to see everything your team has done once and the flux layer now echoes. */
public class ItemCodex extends Item {

    public ItemCodex() {
        setUnlocalizedName("fluxecho.codex");
        setTextureName(FluxEcho.MODID + ":codex");
        setMaxStackSize(1);
        setCreativeTab(FluxEcho.TAB);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) FluxEcho.proxy.openCodex(player);
        return stack;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List tip, boolean advanced) {
        tip.addAll(EchoText.lines("codex.tip"));
    }
}
