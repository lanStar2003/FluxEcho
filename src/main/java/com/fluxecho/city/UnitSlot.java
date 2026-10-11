package com.fluxecho.city;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.modularui.api.forge.IItemHandlerModifiable;
import com.gtnewhorizons.modularui.common.internal.wrapper.BaseSlot;

/**
 * A slot of a city module's unit in its window ({@link CityGui}'s grid). A unit's slot keeps one item, and every way
 * into it honours that (the slot limit is one) but one: vanilla's hotbar key over an empty slot
 * ({@code Container.slotClick} mode 2) puts the whole hotbar stack in, whatever the limit. So this slot keeps one item
 * of whatever is put in and gives the rest back to the player (into the inventory, or at their feet when it is full);
 * then it sends the whole window again, since the client foresaw the whole stack going in and its hotbar emptied. The
 * Echo Archive's book places do the same.
 */
final class UnitSlot extends BaseSlot {

    private final EntityPlayer player;

    UnitSlot(IItemHandlerModifiable handler, int index, EntityPlayer player) {
        super(handler, index);
        this.player = player;
    }

    @Override
    public void putStack(ItemStack s) {
        if (s == null || s.stackSize <= 1) {
            super.putStack(s);
            return;
        }
        ItemStack one = s.copy();
        one.stackSize = 1;
        super.putStack(one);
        // the client only foresees; the server gives the rest back and tells it what happened
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return;
        ItemStack rest = s.copy();
        rest.stackSize = s.stackSize - 1;
        player.inventory.addItemStackToInventory(rest);
        if (rest.stackSize > 0) player.dropPlayerItemWithRandomChoice(rest, false);
        if (player instanceof EntityPlayerMP mp) {
            Container c = mp.openContainer;
            if (c != null) mp.sendContainerAndContentsToPlayer(c, c.getInventory());
        }
    }
}
