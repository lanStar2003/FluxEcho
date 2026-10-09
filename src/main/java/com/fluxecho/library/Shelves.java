package com.fluxecho.library;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.fluxecho.frame.BlockFrame;
import com.fluxecho.nexus.BlockNexus;
import com.fluxecho.nexus.NexusRegistry;
import com.fluxecho.nexus.TileMultiblock;

/**
 * The Echo Library's shelves, used by hand from inside the hall: each shelf of a formed library keeps one sample as a
 * book on its face into the hall. A sample in hand goes onto an empty shelf, an empty hand takes the book down, and a
 * blank index card held to a book is written for it. The shelves' outer faces, and the shelves of a library that has
 * not formed, are plain blocks.
 */
public final class Shelves {

    private Shelves() {}

    /** {@link BlockFrame.Use}: true for a hall face of a formed library's shelf (on both sides alike). */
    public static boolean use(World w, int x, int y, int z, EntityPlayer p, int side, int meta) {
        if (meta != BlockFrame.SHELF) return false;
        for (TileMultiblock m : NexusRegistry.loaded(w)) {
            if (!(m instanceof TileLibrary l) || !l.formed()) continue;
            int i = l.shelfAt(x, y, z);
            if (i < 0) continue;
            if (side != l.shelfFace(i)) return false;
            if (!w.isRemote) use(l, i, p, w, x, y, z);
            return true;
        }
        return false;
    }

    private static void use(TileLibrary l, int i, EntityPlayer p, World w, int x, int y, int z) {
        if (!BlockNexus.mayUse(l, p)) {
            p.addChatMessage(new ChatComponentTranslation("fluxecho.nexus.not_yours", l.ownerName()));
            return;
        }
        ItemStack held = p.getHeldItem(), book = l.samples.getStackInSlot(i);
        if (held == null) {
            if (book == null) {
                tell(p, "empty");
                return;
            }
            l.samples.setStackInSlot(i, null);
            p.inventory.setInventorySlotContents(p.inventory.currentItem, book);
            sound(w, x, y, z, 0.8f);
        } else if (held.getItem() instanceof ItemLibraryCard) {
            if (ItemLibraryCard.written(held)) {
                tell(p, "card_written");
                return;
            }
            if (book == null) {
                tell(p, "no_book");
                return;
            }
            ItemStack card = ItemLibraryCard.write(book, l.team());
            useUp(p, held);
            if (!p.inventory.addItemStackToInventory(card)) p.dropPlayerItemWithRandomChoice(card, false);
            tell(p, "written", book.getDisplayName());
            sound(w, x, y, z, 1.4f);
        } else if (book != null) {
            tell(p, "taken", book.getDisplayName());
            return;
        } else if (l.holdsElsewhere(held, i)) {
            tell(p, "duplicate", held.getDisplayName());
            return;
        } else {
            ItemStack one = held.copy();
            one.stackSize = 1;
            l.samples.setStackInSlot(i, one);
            useUp(p, held);
            sound(w, x, y, z, 1.1f);
        }
        p.inventoryContainer.detectAndSendChanges();
    }

    /** Takes one of the held stack, as placing a block would (not in creative). */
    private static void useUp(EntityPlayer p, ItemStack held) {
        if (p.capabilities.isCreativeMode) return;
        if (--held.stackSize <= 0) p.inventory.setInventorySlotContents(p.inventory.currentItem, null);
    }

    private static void sound(World w, int x, int y, int z, float pitch) {
        w.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, "dig.cloth", 0.7f, pitch);
    }

    private static void tell(EntityPlayer p, String key, Object... args) {
        p.addChatMessage(new ChatComponentTranslation("fluxecho.library.shelf." + key, args));
    }
}
