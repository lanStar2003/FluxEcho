package com.fluxecho.campus;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import com.fluxecho.core.EchoText;
import com.fluxecho.logic.Parts;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** The campus deck's looks as items, each with its own name and what it is for. */
public class ItemBlockDeck extends ItemBlock {

    public ItemBlockDeck(Block block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    /** The part name of a stack's damage; unknown damage values read as the first part. */
    private static String name(ItemStack stack) {
        int m = stack.getItemDamage();
        return Parts.DECK_NAMES[m >= 0 && m < Parts.DECK_TYPES ? m : 0];
    }

    @Override
    public int getMetadata(int meta) {
        return meta;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return "tile.fluxecho.deck." + name(stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List tip, boolean advanced) {
        tip.addAll(EchoText.lines("deck." + name(stack) + ".tip"));
    }
}
