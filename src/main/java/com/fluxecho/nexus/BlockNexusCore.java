package com.fluxecho.nexus;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.fluxecho.campus.CampusRegistry;
import com.gtnewhorizons.modularui.api.forge.ItemStackHandler;

/**
 * The Flux Nexus's core: the controller in the front rim of its base.
 * <p>
 * A core that carries something (the campus's credit, the manifestation still running:
 * {@link ItemBlockNexusCore#carry})
 * drops from {@link #breakBlock}, the one place that still sees the tile, whatever breaks it, so neither is lost when a
 * nexus is moved; {@link #getDrops} then gives nothing, so it never drops twice. A plain core drops the ordinary way
 * ({@link #getDrops}), only when it is harvested or blown up, never when the block is merely replaced: a teleposer or
 * another block mover swapping the core away (it breaks a fresh, empty tile in its place) gets nothing out of it.
 * Nothing drops while Forge undoes a refused placement ({@code restoringBlockSnapshots}), as the item was never used up
 * then.
 * <p>
 * Breaking a placed core also forgets its campus in the {@link CampusRegistry}; an empty tile (never placed by anyone)
 * leaves the registry alone, and a nexus moved whole enters itself again where it lands.
 * <p>
 * The core of an active campus also carries the campus ({@link ItemBlockNexusCore#CAMPUS}). With credit it rides on
 * the credit core {@link #breakBlock} drops. Without, it rides on the ordinary drop: {@link #getDrops} takes it from
 * the tile when it is asked first (an explosion, a block breaker), or from what {@link #breakBlock} noted for it the
 * same tick when it is asked after (a harvest). Nothing more drops than before, so the campus is no dupe path: one
 * core, carrying it, whenever a core drops at all.
 */
public class BlockNexusCore extends BlockNexus {

    /** {dim, x, y, z, world tick} of a core whose credit {@link #breakBlock} just dropped (server thread), or null. */
    private static long[] droppedCredit;
    /**
     * {dim, x, y, z, world tick} of a core of an active campus {@link #breakBlock} just broke without credit, and the
     * item NBT its ordinary drop takes along ({@link ItemBlockNexusCore#campusTag}); null when there is none.
     */
    private static long[] campusAt;
    private static NBTTagCompound campusTag;

    public BlockNexusCore() {
        super("nexus", "nexus");
    }

    @Override
    public TileEntity createNewTileEntity(World w, int meta) {
        return new TileNexus();
    }

    /**
     * The plain core, unless the core carries something: then {@link #breakBlock} drops it with what it carries (the
     * tile is still there when a block breaker or an explosion asks first, and the credit core was just dropped when a
     * harvest asks after).
     */
    @Override
    public ArrayList<ItemStack> getDrops(World w, int x, int y, int z, int meta, int fortune) {
        NBTTagCompound campus = null;
        if (w != null && !w.isRemote) {
            if (takeDropped(w, x, y, z)) return new ArrayList<>();
            TileEntity te = w.getTileEntity(x, y, z);
            if (te instanceof TileNexus n && ItemBlockNexusCore.carries(n)) return new ArrayList<>();
            // asked before the block went (the tile is there) or right after breakBlock noted the campus
            campus = te instanceof TileNexus here ? ItemBlockNexusCore.campusTag(here) : takeCampus(w, x, y, z);
        }
        ArrayList<ItemStack> out = super.getDrops(w, x, y, z, meta, fortune);
        if (campus != null) {
            Item item = Item.getItemFromBlock(this);
            for (ItemStack s : out) {
                if (s == null || s.getItem() != item || s.stackSize != 1) continue;
                s.setTagCompound(campus);
                break;
            }
        }
        return out;
    }

    @Override
    public void breakBlock(World w, int x, int y, int z, Block block, int meta) {
        TileEntity te = w.getTileEntity(x, y, z);
        if (!w.isRemote && te instanceof TileNexus n && placed(n)) {
            int[] c = n.centre();
            CampusRegistry.remove(w, c[0], c[1], c[2]);
            if (!w.restoringBlockSnapshots) {
                dropIntake(n, w, x, y, z);
                if (!dropCredit(n, w, x, y, z)) noteCampus(n, w, x, y, z);
            }
        }
        super.breakBlock(w, x, y, z, block, meta);
    }

    /** Whether the tile is a placed nexus (an owner, or a campus), not a fresh one a block mover put in its place. */
    private static boolean placed(TileNexus n) {
        return n.owner() != null || n.campus()
            .active()
            || n.campus()
                .job() != null;
    }

    /**
     * Notes the campus of a core broken without credit, for the ordinary drop that may follow this tick
     * ({@link #getDrops} after a harvest). A block mover that only removes the core never asks, and the note is
     * forgotten.
     */
    private static void noteCampus(TileNexus n, World w, int x, int y, int z) {
        NBTTagCompound tag = ItemBlockNexusCore.campusTag(n);
        if (tag == null) return;
        campusAt = new long[] { w.provider.dimensionId, x, y, z, w.getTotalWorldTime() };
        campusTag = tag;
    }

    /** The campus noted for the core at the position this tick (and forgets it), or null. */
    private static NBTTagCompound takeCampus(World w, int x, int y, int z) {
        long[] d = campusAt;
        NBTTagCompound tag = campusTag;
        if (d == null) return null;
        campusAt = null;
        campusTag = null;
        boolean here = d[0] == w.provider.dimensionId && d[1] == x
            && d[2] == y
            && d[3] == z
            && d[4] == w.getTotalWorldTime();
        return here ? tag : null;
    }

    /** Whether the core at the position dropped its credit this tick (and forgets it). */
    private static boolean takeDropped(World w, int x, int y, int z) {
        long[] d = droppedCredit;
        if (d == null) return false;
        droppedCredit = null;
        return d[0] == w.provider.dimensionId && d[1] == x && d[2] == y && d[3] == z && d[4] == w.getTotalWorldTime();
    }

    /** The build page's intake slot holds what the job did not take yet: it falls out like the inventory. */
    private static void dropIntake(TileNexus n, World w, int x, int y, int z) {
        ItemStackHandler intake = n.campus()
            .intake();
        for (int i = 0; i < intake.getSlots(); i++) {
            ItemStack s = intake.getStackInSlot(i);
            if (s == null) continue;
            w.spawnEntityInWorld(new EntityItem(w, x + 0.5, y + 0.5, z + 0.5, s.copy()));
            intake.setStackInSlot(i, null);
        }
    }

    /**
     * Drops the core item with what the nexus hands it, when it hands any credit or manifestation (and then its campus
     * with them): always (like a chest's contents, whatever {@code doTileDrops} says, and for a creative-mode breaker
     * too). A core that carries nothing of the kind is left to {@link #getDrops}. Returns whether it dropped.
     */
    private boolean dropCredit(TileNexus n, World w, int x, int y, int z) {
        Item item = Item.getItemFromBlock(this);
        if (item == null) return false;
        NBTTagCompound carried = ItemBlockNexusCore.carry(n);
        if (carried.hasNoTags()) return false;
        ItemStack core = new ItemStack(item, 1, damageDropped(0));
        core.setTagCompound(carried);
        EntityItem e = new EntityItem(w, x + 0.5, y + 0.5, z + 0.5, core);
        e.delayBeforeCanPickup = 10;
        w.spawnEntityInWorld(e);
        droppedCredit = new long[] { w.provider.dimensionId, x, y, z, w.getTotalWorldTime() };
        return true;
    }
}
