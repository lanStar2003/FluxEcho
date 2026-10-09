package com.fluxdepths.item;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;

import com.fluxdepths.FluxDepths;
import com.fluxdepths.shard.Veins;
import com.sinthoras.visualprospecting.database.OreVeinPosition;
import com.sinthoras.visualprospecting.database.ServerCache;
import com.sinthoras.visualprospecting.database.veintypes.VeinType;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.objects.GTUOFluid;
import gregtech.common.UndergroundOil;

/**
 * Takes an imprint of the vein under the player: the 3x3 chunk area a GT vein is generated in, as VisualProspecting
 * recorded it when the world was made. Sneaking, it takes the chunk's underground fluid instead. Costs a sheet of
 * paper either way.
 */
public class ItemImprinter extends Item {

    public ItemImprinter() {
        setUnlocalizedName("fluxdepths.imprinter");
        setTextureName(FluxDepths.MODID + ":imprinter");
        setMaxStackSize(1);
        setFull3D();
        setCreativeTab(FluxDepths.TAB);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack held, World world, EntityPlayer player) {
        if (world.isRemote) return held;
        int x = MathHelper.floor_double(player.posX), z = MathHelper.floor_double(player.posZ);
        int dim = world.provider.dimensionId;
        if (player.isSneaking()) return fluid(held, world, player, x >> 4, z >> 4);
        List<OreVeinPosition> found = ServerCache.instance.prospectOreBlockRadius(dim, x, z, 0);
        OreVeinPosition at = found.isEmpty() ? null : found.get(0);
        Veins.Vein vein = at == null || at.veinType == VeinType.NO_VEIN ? null : Veins.get(at.veinType.name);
        if (vein == null) {
            player.addChatMessage(new ChatComponentTranslation("fluxdepths.imprinter.none"));
            return held;
        }
        if (!player.capabilities.isCreativeMode && !player.inventory.consumeInventoryItem(Items.paper)) {
            player.addChatMessage(new ChatComponentTranslation("fluxdepths.imprinter.paper"));
            return held;
        }
        ItemStack imprint = ItemImprint
            .of(vein.name, dim, world.provider.getDimensionName(), at.getBlockX(), at.getBlockZ());
        if (!player.inventory.addItemStackToInventory(imprint)) player.dropPlayerItemWithRandomChoice(imprint, false);
        player.inventoryContainer.detectAndSendChanges();
        player.addChatMessage(
            new ChatComponentTranslation(
                "fluxdepths.imprinter.ok",
                vein.displayName(),
                world.provider.getDimensionName()));
        world.playSoundAtEntity(player, "random.orb", 0.4f, 0.6f);
        return held;
    }

    /** Sneaking: the underground fluid of the chunk underfoot, as it was before anyone pumped it. */
    private static ItemStack fluid(ItemStack held, World world, EntityPlayer player, int cx, int cz) {
        Pair<GTUOFluid, Integer> p = UndergroundOil.getPristineAmount(world, cx, cz);
        Fluid fluid = p == null || p.getLeft() == null ? null
            : p.getLeft()
                .getFluid();
        int amount = p == null || p.getRight() == null ? 0 : p.getRight();
        if (fluid == null || amount <= 0) {
            player.addChatMessage(new ChatComponentTranslation("fluxdepths.imprinter.no_fluid"));
            return held;
        }
        if (!player.capabilities.isCreativeMode && !player.inventory.consumeInventoryItem(Items.paper)) {
            player.addChatMessage(new ChatComponentTranslation("fluxdepths.imprinter.paper"));
            return held;
        }
        ItemStack imprint = ItemImprint
            .ofFluid(fluid.getName(), amount, world.provider.dimensionId, world.provider.getDimensionName(), cx, cz);
        if (!player.inventory.addItemStackToInventory(imprint)) player.dropPlayerItemWithRandomChoice(imprint, false);
        player.inventoryContainer.detectAndSendChanges();
        player.addChatMessage(
            new ChatComponentTranslation(
                "fluxdepths.imprinter.fluid_ok",
                fluid.getLocalizedName(new FluidStack(fluid, 1)),
                amount));
        world.playSoundAtEntity(player, "random.orb", 0.4f, 0.8f);
        return held;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack s, EntityPlayer player, List lines, boolean advanced) {
        for (String line : StatCollector.translateToLocal("fluxdepths.imprinter.tip")
            .split("\\\\n")) lines.add(EnumChatFormatting.GRAY + line);
    }
}
