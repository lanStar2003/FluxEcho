package com.fluxecho.thaumcraft;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.core.EchoText;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** The Flux Vis Pedestal's modules, one item with a damage value per kind. */
public class ItemVisModule extends Item {

    /** The kinds, by damage value. */
    public enum Kind {

        /** More vis per tick; they add up. */
        EXTRACTION,
        /** Also charges what the team carries nearby. */
        WIRELESS,
        /** Draws EU from the team's GT wireless network (FluxLite's network) when the pedestal runs low. */
        LINK;

        public String key() {
            return name().toLowerCase();
        }

        public static Kind of(ItemStack s) {
            if (s == null || !(s.getItem() instanceof ItemVisModule)) return null;
            int d = s.getItemDamage();
            return d >= 0 && d < values().length ? values()[d] : null;
        }
    }

    private IIcon[] icons;

    public ItemVisModule() {
        setUnlocalizedName("fluxecho.vis_module");
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(FluxEcho.TAB);
    }

    public static ItemStack stack(Kind k, int count) {
        return new ItemStack(TCModule.visModule, count, k.ordinal());
    }

    @Override
    public String getUnlocalizedName(ItemStack s) {
        Kind k = Kind.of(s);
        return k == null ? super.getUnlocalizedName(s) : "item.fluxecho.vis_module." + k.key();
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (Kind k : Kind.values()) list.add(new ItemStack(item, 1, k.ordinal()));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister r) {
        icons = new IIcon[Kind.values().length];
        for (Kind k : Kind.values()) icons[k.ordinal()] = r.registerIcon(FluxEcho.MODID + ":vis_module_" + k.key());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        return icons[Math.max(0, Math.min(icons.length - 1, damage))];
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack s, EntityPlayer player, List tip, boolean advanced) {
        Kind k = Kind.of(s);
        if (k == null) return;
        Object[] args = switch (k) {
            case EXTRACTION -> new Object[] { Config.visModuleRate * 20 / 100.0 };
            case WIRELESS -> new Object[] { Config.visWirelessRange };
            case LINK -> new Object[] { Config.visLinkEut };
        };
        tip.addAll(EchoText.lines("vis_module." + k.key() + ".tip", args));
        tip.addAll(EchoText.lines("vis_module.install"));
    }
}
