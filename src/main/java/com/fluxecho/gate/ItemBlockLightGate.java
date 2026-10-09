package com.fluxecho.gate;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.fluxecho.Config;
import com.fluxecho.core.EchoText;

/** The light gate as an item: remembers its plot, and is not put down inside the flux interior. */
public class ItemBlockLightGate extends ItemBlock {

    public ItemBlockLightGate(Block block) {
        super(block);
    }

    /** The plot this gate opens onto, -1 for a new one. */
    public static int plot(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("plot") ? stack.getTagCompound()
                    .getInteger("plot") : -1;
    }

    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World w, int x, int y, int z, int side, float hx,
        float hy, float hz, int meta) {
        if (w.provider.dimensionId == Config.gateDimension) {
            if (!w.isRemote && player != null)
                player.addChatMessage(new ChatComponentTranslation("fluxecho.gate.not_inside"));
            return false;
        }
        return super.placeBlockAt(stack, player, w, x, y, z, side, hx, hy, hz, meta);
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        int plot = plot(stack);
        lines.addAll(plot < 0 ? EchoText.lines("gate.item.new") : EchoText.lines("gate.item.plot", plot + 1));
    }
}
