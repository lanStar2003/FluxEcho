package com.fluxdepths.item;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.fluxdepths.FluxDepths;
import com.fluxdepths.shard.Veins;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * An imprint of the depths, taken in one world. A vein imprint names which shard a vein was projected from (the vein
 * name VisualProspecting records); a fluid imprint names a chunk's underground fluid and its pristine amount. Shard
 * collectors take vein imprints, fluid pumps fluid imprints; both only resonate with imprints of their own world.
 */
public class ItemImprint extends Item {

    private static final String VEIN = "vein", FLUID = "fluid", AMOUNT = "amount", DIM = "dim", DIM_NAME = "dimName",
        X = "x", Z = "z";
    /** NEI's imprints belong to no particular world. */
    public static final int ANY_DIM = Integer.MIN_VALUE;

    public ItemImprint() {
        setUnlocalizedName("fluxdepths.imprint");
        setTextureName(FluxDepths.MODID + ":imprint");
        setMaxStackSize(1);
        setCreativeTab(FluxDepths.TAB);
    }

    private static ItemStack of(NBTTagCompound t, int dim, String dimName, int x, int z) {
        ItemStack s = new ItemStack(FluxDepths.imprint);
        t.setInteger(DIM, dim);
        if (dimName != null) t.setString(DIM_NAME, dimName);
        t.setInteger(X, x);
        t.setInteger(Z, z);
        s.setTagCompound(t);
        return s;
    }

    public static ItemStack of(String vein, int dim, String dimName, int x, int z) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString(VEIN, vein);
        return of(t, dim, dimName, x, z);
    }

    /** A fluid imprint of the chunk at chunk coordinates {@code cx, cz}. */
    public static ItemStack ofFluid(String fluid, int amount, int dim, String dimName, int cx, int cz) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString(FLUID, fluid);
        t.setInteger(AMOUNT, amount);
        return of(t, dim, dimName, cx, cz);
    }

    /** An imprint for NEI's recipe pages. */
    public static ItemStack forNei(String vein) {
        return of(vein, ANY_DIM, null, 0, 0);
    }

    public static ItemStack fluidForNei(String fluid, int amount) {
        return ofFluid(fluid, amount, ANY_DIM, null, 0, 0);
    }

    public static boolean is(ItemStack s) {
        return s != null && s.getItem() instanceof ItemImprint;
    }

    /** The vein name, or null for a blank or fluid imprint. */
    public static String vein(ItemStack s) {
        NBTTagCompound t = is(s) ? s.getTagCompound() : null;
        return t != null && t.hasKey(VEIN) ? t.getString(VEIN) : null;
    }

    /** The fluid of a fluid imprint, or null (another imprint, or a fluid this pack no longer has). */
    public static Fluid fluid(ItemStack s) {
        NBTTagCompound t = is(s) ? s.getTagCompound() : null;
        return t != null && t.hasKey(FLUID) ? FluidRegistry.getFluid(t.getString(FLUID)) : null;
    }

    /** The pristine amount on a fluid imprint: litres per operation of GT's drilling rigs. */
    public static int amount(ItemStack s) {
        NBTTagCompound t = is(s) ? s.getTagCompound() : null;
        return t == null ? 0 : Math.max(0, t.getInteger(AMOUNT));
    }

    public static int dim(ItemStack s) {
        NBTTagCompound t = s.getTagCompound();
        return t != null && t.hasKey(DIM) ? t.getInteger(DIM) : ANY_DIM;
    }

    @Override
    public String getItemStackDisplayName(ItemStack s) {
        Fluid f = fluid(s);
        if (f != null) return StatCollector
            .translateToLocalFormatted("item.fluxdepths.imprint.fluid", f.getLocalizedName(new FluidStack(f, 1)));
        Veins.Vein v = Veins.get(vein(s));
        String base = super.getItemStackDisplayName(s);
        return v == null ? base
            : StatCollector.translateToLocalFormatted("item.fluxdepths.imprint.of", v.displayName());
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack s, EntityPlayer player, List lines, boolean advanced) {
        NBTTagCompound t = s.getTagCompound();
        int dim = dim(s);
        String world = t == null ? "" : t.hasKey(DIM_NAME) ? t.getString(DIM_NAME) : String.valueOf(dim);
        if (fluid(s) != null) {
            lines.add(
                EnumChatFormatting.GRAY
                    + StatCollector.translateToLocalFormatted("fluxdepths.imprint.amount", amount(s)));
            if (dim != ANY_DIM) lines.add(
                EnumChatFormatting.DARK_AQUA + StatCollector
                    .translateToLocalFormatted("fluxdepths.imprint.chunk", world, t.getInteger(X), t.getInteger(Z)));
            lines.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("fluxdepths.imprint.fluid_lore"));
            return;
        }
        Veins.Vein v = Veins.get(vein(s));
        if (v == null) {
            lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("fluxdepths.imprint.blank"));
            return;
        }
        for (int i = 0; i < v.mix.size(); i++) {
            ItemStack ore = v.mix.ores()
                .get(i);
            lines.add(
                EnumChatFormatting.GRAY + String.format(" %s  %.1f%%", ore.getDisplayName(), v.mix.share(i) * 100));
        }
        if (dim == ANY_DIM) {
            lines.add(
                EnumChatFormatting.DARK_AQUA
                    + StatCollector.translateToLocalFormatted("fluxdepths.imprint.dims", String.join(", ", v.dims)));
        } else {
            lines.add(
                EnumChatFormatting.DARK_AQUA + StatCollector
                    .translateToLocalFormatted("fluxdepths.imprint.world", world, t.getInteger(X), t.getInteger(Z)));
        }
        lines.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("fluxdepths.imprint.lore"));
    }
}
