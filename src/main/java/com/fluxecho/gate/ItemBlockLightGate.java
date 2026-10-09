package com.fluxecho.gate;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.fluxecho.Config;
import com.fluxecho.core.EchoText;
import com.fluxecho.logic.FoldedZone;
import com.fluxecho.logic.RoomPlan;

/**
 * The light gate as an item: remembers its room once it has one; before that, sneak-use in the air picks the shape of
 * the room it will open (the prototype lets you try every module's room). Not put down inside the folded zone.
 */
public class ItemBlockLightGate extends ItemBlock {

    public ItemBlockLightGate(Block block) {
        super(block);
    }

    /** The room this gate opens onto, -1 for a new one. */
    public static int plot(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("plot") ? stack.getTagCompound()
                    .getInteger("plot") : -1;
    }

    /** The shape of the room a new gate opens. */
    public static String room(ItemStack stack) {
        String id = stack != null && stack.hasTagCompound() ? stack.getTagCompound()
            .getString("room") : "";
        return RoomPlan.template(id).id;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World w, EntityPlayer player) {
        if (!player.isSneaking() || plot(stack) >= 0) return stack;
        String next = RoomPlan.next(room(stack)).id;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setString("room", next);
        if (!w.isRemote) player.addChatMessage(
            new ChatComponentTranslation(
                "fluxecho.gate.picked",
                new ChatComponentTranslation("fluxecho.gate.room." + next)));
        return stack;
    }

    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World w, int x, int y, int z, int side, float hx,
        float hy, float hz, int meta) {
        if (FoldedZone.contains(x, z) || w.provider.dimensionId == Config.gateDimension) {
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
        RoomPlan p = new RoomPlan(room(stack));
        String name = EchoText.t("gate.room." + p.t.id);
        if (plot >= 0) lines.addAll(EchoText.lines("gate.item.plot", plot + 1, name));
        else lines.addAll(
            EchoText.lines("gate.item.new", name, p.maxX - p.minX + 1, p.maxZ - p.minZ + 1, p.maxY - p.minY + 1));
    }
}
