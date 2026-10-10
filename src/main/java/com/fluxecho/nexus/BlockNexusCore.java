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
 */
public class BlockNexusCore extends BlockNexus {

    /** {dim, x, y, z, world tick} of a core whose credit {@link #breakBlock} just dropped (server thread), or null. */
    private static long[] droppedCredit;

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
        if (w != null && !w.isRemote) {
            if (takeDropped(w, x, y, z)) return new ArrayList<>();
            if (w.getTileEntity(x, y, z) instanceof TileNexus n && ItemBlockNexusCore.carries(n))
                return new ArrayList<>();
        }
        return super.getDrops(w, x, y, z, meta, fortune);
    }

    @Override
    public void breakBlock(World w, int x, int y, int z, Block block, int meta) {
        TileEntity te = w.getTileEntity(x, y, z);
        if (!w.isRemote && te instanceof TileNexus n && placed(n)) {
            int[] c = n.centre();
            CampusRegistry.remove(w, c[0], c[1], c[2]);
            if (!w.restoringBlockSnapshots) {
                dropIntake(n, w, x, y, z);
                dropCredit(n, w, x, y, z);
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
     * Drops the core item with what the nexus hands it, when it hands anything: always (like a chest's contents,
     * whatever {@code doTileDrops} says, and for a creative-mode breaker too). A core that carries nothing is left to
     * {@link #getDrops}.
     */
    private void dropCredit(TileNexus n, World w, int x, int y, int z) {
        Item item = Item.getItemFromBlock(this);
        if (item == null) return;
        NBTTagCompound carried = ItemBlockNexusCore.carry(n);
        if (carried.hasNoTags()) return;
        ItemStack core = new ItemStack(item, 1, damageDropped(0));
        core.setTagCompound(carried);
        EntityItem e = new EntityItem(w, x + 0.5, y + 0.5, z + 0.5, core);
        e.delayBeforeCanPickup = 10;
        w.spawnEntityInWorld(e);
        droppedCredit = new long[] { w.provider.dimensionId, x, y, z, w.getTotalWorldTime() };
    }
}
