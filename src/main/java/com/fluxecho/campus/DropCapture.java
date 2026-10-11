package com.fluxecho.campus;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Catches the items a block spills while the campus builder takes it down (0.10.1): the contents of a chest, a GT
 * machine's inventory, an AE drive's cells, the other half of a door. The builder opens a capture round the cell it is
 * removing ({@link #begin}), removes the block and closes it ({@link #end}); in between, every item entity that joins
 * the same world within {@link #REACH} blocks of the cell is cancelled ({@link Handler}) and its stack kept, so it can
 * go into the campus's recycle store instead of lying about. Experience orbs and every other entity are left alone,
 * and outside a capture nothing is touched. Only items that are being spawned count: the join event also fires for the
 * saved item entities of a chunk that loads during the removal (a neighbour's harvest may load one), and those are in
 * their chunk already, so cancelling them would not take them out of it ({@link #caught}).
 * <p>
 * The state is static and belongs to the server thread, which is the only one that removes blocks and spawns items in
 * a server world; a client world never matches the captured world. The decision itself ({@link #take}) needs no
 * world object of a particular type, so it can be tested.
 */
public final class DropCapture {

    /** Blocks round the removed cell, on every axis, an item may appear in and still be caught. */
    public static final int REACH = 2;

    private static Object world;
    private static int cx, cy, cz;
    private static List<ItemStack> caught = new ArrayList<>();

    private DropCapture() {}

    /**
     * Opens a capture round the cell in the world. A capture still open (which the builder never leaves) moves to
     * the new cell and keeps what it caught.
     */
    static void begin(Object w, int x, int y, int z) {
        world = w;
        cx = x;
        cy = y;
        cz = z;
    }

    /** Whether a capture is open. */
    static boolean active() {
        return world != null;
    }

    /**
     * Offers an item that is about to appear at {@code (px, py, pz)} in the world: kept and true (the entity must not
     * spawn) when a capture is open in that world and the point lies within {@link #REACH} of the cell; false
     * otherwise. A null or empty stack is never taken.
     */
    static boolean take(Object w, double px, double py, double pz, ItemStack s) {
        if (world == null || w != world || s == null || s.stackSize <= 0) return false;
        if (px < cx - REACH || px >= cx + 1 + REACH || py < cy - REACH || py >= cy + 1 + REACH) return false;
        if (pz < cz - REACH || pz >= cz + 1 + REACH) return false;
        caught.add(s.copy());
        return true;
    }

    /**
     * The handler's decision for an item entity joining the world: {@link #take} for an item being spawned; never for
     * one already in its chunk ({@code inChunk}, {@code Entity.addedToChunk}: a chunk that loads hands its saved
     * entities to the world with the same event, and a cancelled one stays in the chunk, to be saved again: taking it
     * would make a copy).
     */
    static boolean caught(Object w, boolean inChunk, double px, double py, double pz, ItemStack s) {
        return !inChunk && take(w, px, py, pz, s);
    }

    /** Closes the capture and returns what it caught (an empty list when nothing, or when none was open). */
    static List<ItemStack> end() {
        List<ItemStack> out = caught;
        caught = new ArrayList<>();
        world = null;
        return out;
    }

    /** Registers the handler (the campus module's init). */
    static void register() {
        MinecraftForge.EVENT_BUS.register(new Handler());
    }

    /** Cancels the item entities a capture takes (server worlds only; the client never opens one). */
    public static final class Handler {

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onJoin(EntityJoinWorldEvent e) {
            if (world == null || e.world == null || e.world.isRemote || !(e.entity instanceof EntityItem item)) return;
            if (caught(e.world, item.addedToChunk, item.posX, item.posY, item.posZ, item.getEntityItem()))
                e.setCanceled(true);
        }
    }
}
