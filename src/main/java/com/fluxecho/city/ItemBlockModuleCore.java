package com.fluxecho.city;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import com.fluxecho.client.Keys;
import com.fluxecho.core.EchoText;
import com.fluxecho.logic.ModuleCores;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The module cores as items, one a meta ({@link ModuleCores}): {@code tile.fluxecho.module_core.<module>.name}, what it
 * is ({@code fluxecho.<module>.tip}) and with Shift how it is built ({@code fluxecho.<module>.structure}). The block is
 * placed with the item's meta, which names the module.
 */
public class ItemBlockModuleCore extends ItemBlock {

    public ItemBlockModuleCore(Block block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    @Override
    public int getMetadata(int meta) {
        return meta;
    }

    /** The module key of a stack's meta (the Eco Dome's for a meta without a module). */
    static String key(ItemStack stack) {
        return ModuleCores.KEYS[BlockModuleCore.built(stack.getItemDamage())];
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return "tile.fluxecho.module_core." + key(stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List tip, boolean advanced) {
        String key = key(stack);
        tip.addAll(EchoText.lines(key + ".tip"));
        if (Keys.shift()) tip.addAll(EchoText.lines(key + ".structure"));
        else tip.add(EchoText.t("gui.shift_build"));
    }
}
