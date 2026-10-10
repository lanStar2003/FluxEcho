package com.fluxecho.library;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.modularui.api.forge.IItemHandlerModifiable;
import com.gtnewhorizons.modularui.common.internal.wrapper.BaseSlot;

/**
 * A slot of a library's book places in a GUI: the 0.9.2 hall's shelves and the Echo Archive's bookcase grid. A place
 * keeps one book, and every way into it honours that (the slot limit is one) but one: vanilla's hotbar key over an
 * empty slot ({@code Container.slotClick} mode 2) puts the whole hotbar stack in, whatever the limit. Such a stack
 * would sit in the place as one book and lose the rest of its count later, in the vault. So this slot keeps one item
 * of whatever is put in and gives the rest back to the player (into the inventory, or at their feet when it is full);
 * then it sends the whole window again, since the client foresaw the whole stack going in and its hotbar emptied.
 * <p>
 * Only putting a stack in goes through here. The check of whether a book may go in ({@code isItemValid}) takes the
 * place's stack out and puts it back through the handler directly, and must not split it.
 */
final class BookSlot extends BaseSlot {

    private final EntityPlayer player;

    BookSlot(IItemHandlerModifiable handler, int index, EntityPlayer player) {
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
