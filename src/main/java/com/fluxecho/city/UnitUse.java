package com.fluxecho.city;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.fluxecho.nexus.BlockNexus;
import com.fluxecho.nexus.NexusRegistry;
import com.fluxecho.nexus.TileMultiblock;

/**
 * The units of a city module, used by hand (0.11.0; the Echo Archive's {@code Units.use} made general): a unit is used
 * as a whole, so clicking any of its cells means that unit. An empty hand opens the module's window at the unit (on
 * its Units page, {@code CityGui}); an item in hand goes into the unit's first empty slot (one item); sneaking with an
 * empty hand on a socket takes the item that socket shows. Registered for the frame blocks and the campus fittings (the
 * parts units are made of); the Archive's bookcases keep their own use.
 * <p>
 * Both sides answer alike, so the client swings the arm and does not place the held block: the client finds the same
 * formed module among its loaded multiblocks. Only the server acts. Messages: {@code fluxecho.city.unit.<key>}.
 */
public final class UnitUse {

    private UnitUse() {}

    /** {@code BlockFrame.Use} and {@code BlockFitting.Use}: true for a cell of a unit of a formed city module. */
    public static boolean use(World w, int x, int y, int z, EntityPlayer p, int side, int meta) {
        for (TileMultiblock m : NexusRegistry.loaded(w)) {
            if (!(m instanceof TileCityModule c) || !c.formed() || !c.covers(w, x, y, z)) continue;
            int unit = c.unitAt(x, y, z);
            if (unit < 0) continue;
            if (!w.isRemote) use(c, unit, x, y, z, p);
            return true;
        }
        return false;
    }

    /** What a click on a cell of the unit does (server side). */
    static void use(TileCityModule c, int unit, int x, int y, int z, EntityPlayer p) {
        if (!BlockNexus.mayUse(c, p)) {
            p.addChatMessage(new ChatComponentTranslation("fluxecho.city.unit.not_yours", c.ownerName()));
            return;
        }
        ItemStack held = p.getHeldItem();
        String name = c.unitName(unit);
        int[] slots = c.unitSlots(unit);
        if (held == null) {
            int slot = c.slotAt(x, y, z);
            ItemStack s = slot >= 0 ? c.samples.getStackInSlot(slot) : null;
            if (p.isSneaking() && s != null) {
                c.samples.setStackInSlot(slot, null);
                p.inventory.setInventorySlotContents(p.inventory.currentItem, s);
                tell(p, "took", s.getDisplayName(), name);
                p.inventoryContainer.detectAndSendChanges();
                return;
            }
            c.selectUnit(unit);
            c.openOnUnit = true;
            BlockNexus.open(c, p);
            return;
        }
        int free = c.firstFree(unit);
        if (free < 0) {
            // the text names the fill and the size (a placeholder with no argument throws on the client)
            tell(p, "full", name, c.unitFill(unit), slots[1]);
            return;
        }
        ItemStack one = held.copy();
        one.stackSize = 1;
        if (!c.samples.isItemValid(free, one)) return;
        c.samples.setStackInSlot(free, one);
        if (!p.capabilities.isCreativeMode) {
            held.stackSize--;
            if (held.stackSize <= 0) p.inventory.setInventorySlotContents(p.inventory.currentItem, null);
        }
        tell(p, "put", one.getDisplayName(), name, c.unitFill(unit), slots[1]);
        p.inventoryContainer.detectAndSendChanges();
    }

    private static void tell(EntityPlayer p, String key, Object... args) {
        p.addChatMessage(new ChatComponentTranslation("fluxecho.city.unit." + key, args));
    }
}
