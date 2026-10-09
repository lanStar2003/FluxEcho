package com.fluxecho.matter;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.fluxecho.FluxEcho;
import com.fluxecho.core.EchoText;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** A flux material with nothing of its own but a name, an icon and a line of lore: flux grit, flux crystal. */
public class ItemMatter extends Item {

    private final String key;

    public ItemMatter(String key) {
        this.key = key;
        setUnlocalizedName("fluxecho." + key);
        setTextureName(FluxEcho.MODID + ":" + key);
        setCreativeTab(FluxEcho.TAB);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List tip, boolean advanced) {
        tip.addAll(EchoText.lines(key + ".tip"));
    }
}
