package com.fluxecho.matter;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.fluxecho.FluxEcho;
import com.fluxecho.codex.Categories;
import com.fluxecho.core.EchoText;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Echo crystal: a flux crystal the Flux Nexus has pressed one entry of the team's Echo Codex into (a bee, an aspect, an
 * orb...). The entry is kept in its NBT and floats on its face ({@code client.EchoCrystalRender}); recipes take any
 * echo crystal whatever it holds.
 */
public class ItemEchoCrystal extends Item {

    private static final String CAT = "Cat", KEY = "Key";

    public ItemEchoCrystal() {
        setUnlocalizedName("fluxecho.echo_crystal");
        setTextureName(FluxEcho.MODID + ":echo_crystal");
        setCreativeTab(FluxEcho.TAB);
    }

    /** An echo crystal holding the ledger entry {@code key} of {@code category}. */
    public static ItemStack of(String category, String key, int count) {
        ItemStack s = new ItemStack(MatterModule.echoCrystal, count);
        NBTTagCompound t = new NBTTagCompound();
        t.setString(CAT, category);
        t.setString(KEY, key);
        s.setTagCompound(t);
        return s;
    }

    /** The category of the entry it holds; null for a blank one (creative, NEI). */
    public static String category(ItemStack s) {
        NBTTagCompound t = s == null ? null : s.getTagCompound();
        return t == null || !t.hasKey(CAT) ? null : t.getString(CAT);
    }

    public static String key(ItemStack s) {
        NBTTagCompound t = s == null ? null : s.getTagCompound();
        return t == null || !t.hasKey(KEY) ? null : t.getString(KEY);
    }

    /** The codex category of the entry, when its module is loaded. */
    public static Categories.Category categoryOf(ItemStack s) {
        String id = category(s);
        if (id == null) return null;
        for (Categories.Category c : Categories.all()) if (c.id.equals(id)) return c;
        return null;
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.uncommon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List tip, boolean advanced) {
        String key = key(stack);
        Categories.Category c = categoryOf(stack);
        if (key != null) {
            String name;
            try {
                name = c == null ? key : c.name.apply(key);
            } catch (RuntimeException e) {
                name = key;
            }
            String cat = EchoText.t("codex.cat." + category(stack));
            tip.add(EchoText.t("echo_crystal.holds", cat, name));
        }
        tip.addAll(EchoText.lines("echo_crystal.tip"));
    }
}
