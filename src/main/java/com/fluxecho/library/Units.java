package com.fluxecho.library;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxecho.campus.CampusModule;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.frame.FrameModule;
import com.fluxecho.logic.Parts;
import com.fluxecho.nexus.BlockNexus;
import com.fluxecho.nexus.NexusRegistry;
import com.fluxecho.nexus.TileMultiblock;

/**
 * The Echo Archive's bookcases, used by hand: a bookcase is used as a whole, so clicking any part of it (a shelf body,
 * its plinth or crown, or a post beside it) means that bookcase. An empty hand opens the library's GUI at it; a sample
 * in hand is filed into its first free place; a blank index card is written for the book clicked (or the bookcase's
 * first book); and sneaking with an empty hand takes the clicked book down. Registered for the frame's shelves and the
 * campus fittings; the 0.9.2 hall's shelves keep their own use ({@link Shelves}).
 * <p>
 * Both sides answer alike, so the client swings the arm and does not place the held block: the client finds the same
 * formed Archive among its loaded multiblocks. Only the server acts.
 */
public final class Units {

    /** Half the width of a post (2 to 14 sixteenths), where a click on its face lands. */
    private static final double POST_HALF = 6 / 16.0;

    private Units() {}

    /** {@link BlockFrame.Use} and {@code BlockFitting.Use}: true for a part of a formed Archive's bookcase. */
    public static boolean use(World w, int x, int y, int z, EntityPlayer p, int side, int meta) {
        if (!part(w.getBlock(x, y, z), meta)) return false;
        double[] hit = hit(p, x, y, z, side);
        for (TileMultiblock m : NexusRegistry.loaded(w)) {
            if (!(m instanceof TileLibrary l) || !l.formed() || !l.isArchive() || !l.covers(w, x, y, z)) continue;
            int unit = l.unitAt(x, y, z, side, hit[0], hit[1]);
            if (unit < 0) continue;
            if (!w.isRemote) use(l, unit, w, x, y, z, p);
            return true;
        }
        return false;
    }

    /** Whether the block is one a bookcase is made of: a shelf body, a post, a plinth or a crown. */
    private static boolean part(Block b, int meta) {
        if (b == FrameModule.frame) return meta == BlockFrame.SHELF;
        return b == CampusModule.fitting && (meta == Parts.F_POST || meta == Parts.F_PLINTH || meta == Parts.F_CROWN);
    }

    /**
     * Where the player's look meets the clicked face of a post, {x, z} in the world: which half of a post's face was
     * hit picks the bookcase on that side. Clicked from above or below, the player's own position stands in for it.
     */
    private static double[] hit(EntityPlayer p, int x, int y, int z, int side) {
        ForgeDirection d = ForgeDirection.getOrientation(side);
        Vec3 look = p.getLook(1f);
        if (d.offsetX != 0 && Math.abs(look.xCoord) > 1e-4) {
            double plane = x + 0.5 + d.offsetX * POST_HALF, t = (plane - p.posX) / look.xCoord;
            return new double[] { plane, p.posZ + t * look.zCoord };
        }
        if (d.offsetZ != 0 && Math.abs(look.zCoord) > 1e-4) {
            double plane = z + 0.5 + d.offsetZ * POST_HALF, t = (plane - p.posZ) / look.zCoord;
            return new double[] { p.posX + t * look.xCoord, plane };
        }
        return new double[] { p.posX, p.posZ };
    }

    private static void use(TileLibrary l, int unit, World w, int x, int y, int z, EntityPlayer p) {
        if (!BlockNexus.mayUse(l, p)) {
            p.addChatMessage(new ChatComponentTranslation("fluxecho.library.unit.not_yours", l.ownerName()));
            return;
        }
        ItemStack held = p.getHeldItem();
        int slot = l.slotAt(x, y, z);
        String call = l.callNumber(unit);
        if (held == null) {
            ItemStack book = slot >= 0 ? l.samples.getStackInSlot(slot) : null;
            if (p.isSneaking() && book != null) {
                l.samples.setStackInSlot(slot, null);
                p.inventory.setInventorySlotContents(p.inventory.currentItem, book);
                tell(p, "took", book.getDisplayName(), call);
                Shelves.sound(w, x, y, z, 0.8f);
                p.inventoryContainer.detectAndSendChanges();
                return;
            }
            l.selectUnit(unit);
            BlockNexus.open(l, p);
            return;
        }
        if (held.getItem() instanceof ItemLibraryCard) {
            if (ItemLibraryCard.written(held)) {
                p.addChatMessage(new ChatComponentTranslation("fluxecho.library.shelf.card_written"));
                return;
            }
            ItemStack book = slot >= 0 ? l.samples.getStackInSlot(slot) : null;
            if (book == null) book = firstBook(l, unit);
            if (book == null) {
                tell(p, "no_book", call);
                return;
            }
            ItemStack card = ItemLibraryCard.write(book, l.team());
            Shelves.useUp(p, held);
            if (!p.inventory.addItemStackToInventory(card)) p.dropPlayerItemWithRandomChoice(card, false);
            tell(p, "card", book.getDisplayName(), call);
            Shelves.sound(w, x, y, z, 1.4f);
            p.inventoryContainer.detectAndSendChanges();
            return;
        }
        int free = l.firstFree(unit);
        if (free < 0) {
            tell(p, "full", call);
            return;
        }
        ItemStack one = held.copy();
        one.stackSize = 1;
        if (!l.samples.isItemValid(free, one)) {
            p.addChatMessage(new ChatComponentTranslation("fluxecho.library.shelf.duplicate", held.getDisplayName()));
            return;
        }
        l.samples.setStackInSlot(free, one);
        Shelves.useUp(p, held);
        tell(p, "put", one.getDisplayName(), call, l.unitFill(unit));
        Shelves.sound(w, x, y, z, 1.1f);
        p.inventoryContainer.detectAndSendChanges();
    }

    /** The bookcase's first book (row by row from the top left), or null. */
    private static ItemStack firstBook(TileLibrary l, int unit) {
        for (int k = 0; k < 9; k++) {
            ItemStack s = l.samples.getStackInSlot(unit * 9 + k);
            if (s != null) return s;
        }
        return null;
    }

    private static void tell(EntityPlayer p, String key, Object... args) {
        p.addChatMessage(new ChatComponentTranslation("fluxecho.library.unit." + key, args));
    }
}
