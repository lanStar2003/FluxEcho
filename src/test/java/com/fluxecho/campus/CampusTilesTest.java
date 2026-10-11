package com.fluxecho.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.function.Function;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import com.fluxecho.nexus.TileNexus;

/**
 * The supply port and the recycle bin in a world ({@link FakeWorld}): they only ever use the nexus tile the world holds
 * now, so a nexus whose chunk unloaded (and came back as a new tile) is never fed or drained through a stale copy; and
 * a hopper that takes the last item of a kind from the bin and puts it back loses nothing.
 */
class CampusTilesTest {

    private static final int NX = 100, NY = 70, NZ = 100;
    /** Far enough from the nexus to lie in another chunk. */
    private static final int BX = 140, BY = 70, BZ = 100;

    /** An item with no registry entry, made from a kind by the store's loader while a test runs. */
    private static final Item ITEM = new Item();
    private static final short ID = 4000;

    private static NBTTagCompound kind(int meta) {
        NBTTagCompound k = new NBTTagCompound();
        k.setShort("id", ID);
        k.setShort("Damage", (short) meta);
        return k;
    }

    /** Runs the body with kinds of {@link #ID} made into stacks of {@link #ITEM} (the tests have no item registry). */
    private static void withItems(Runnable body) {
        Function<NBTTagCompound, ItemStack> was = RecycleStore.loader;
        RecycleStore.loader = t -> t.getShort("id") == ID
            ? new ItemStack(ITEM, t.getByte("Count"), t.getShort("Damage"))
            : null;
        try {
            body.run();
        } finally {
            RecycleStore.loader = was;
        }
    }

    @Test
    void aNexusWhoseChunkUnloadedIsNotUsedAnyMore() {
        FakeWorld w = FakeWorld.create();
        TileNexus first = w.put(new TileNexus(), NX, NY, NZ);
        TileRecycleBin bin = w.put(new TileRecycleBin(), BX, BY, BZ);
        TileSupplyPort port = w.put(new TileSupplyPort(), BX, BY, BZ + 1);
        bin.link(first);
        port.link(first);
        assertSame(first, bin.nexus());
        assertSame(first, port.nexus());
        assertSame(
            first.campus()
                .recycle(),
            bin.store());

        // the nexus's chunk unloads: its tile is told so, never invalidated, and must not be drained or fed any more
        w.unload(NX, NZ);
        assertNull(bin.nexus(), "an unloaded nexus is gone for the bin");
        assertNull(bin.store());
        assertNull(port.nexus(), "and for the port");

        // the chunk loads again with a new tile (its saved store): that one is used, never the old copy
        w.load(NX, NZ);
        TileNexus again = w.put(new TileNexus(), NX, NY, NZ);
        assertSame(again, bin.nexus());
        assertSame(again, port.nexus());
        assertSame(
            again.campus()
                .recycle(),
            bin.store());
        assertNull(TileSupplyPort.loadedNexus(w, null));
        assertNull(TileSupplyPort.loadedNexus(w, new int[] { NX, NY + 1, NZ }), "no nexus there");
    }

    @Test
    void aHopperThatTakesTheLastItemAndPutsItBackLosesNothing() {
        withItems(() -> {
            FakeWorld w = FakeWorld.create();
            TileNexus n = w.put(new TileNexus(), NX, NY, NZ);
            TileRecycleBin bin = w.put(new TileRecycleBin(), BX, BY, BZ);
            bin.link(n);
            RecycleStore store = n.campus()
                .recycle();
            store.add(kind(0), 1);

            // the vanilla hopper: read the slot, copy it, take one, find no room, set the copy back
            ItemStack read = bin.getStackInSlot(0);
            assertNotNull(read);
            assertEquals(1, read.stackSize);
            ItemStack copy = read.copy();
            ItemStack took = bin.decrStackSize(0, 1);
            assertEquals(1, took.stackSize);
            assertEquals(0, store.count(kind(0)), "the last item left the store");
            assertNull(bin.getStackInSlot(0), "the slot is empty now");
            bin.setInventorySlotContents(0, copy);
            assertEquals(1, store.count(kind(0)), "the item it could not keep went back");
            assertEquals(1, bin.getStackInSlot(0).stackSize, "and the slot shows it again");

            // nothing more than was taken goes in, and nothing of another kind
            ItemStack more = copy.copy();
            more.stackSize = 5;
            bin.setInventorySlotContents(0, more);
            assertEquals(1, store.count(kind(0)));
            bin.setInventorySlotContents(1, new ItemStack(ITEM, 3, 0));
            assertEquals(1, store.total());

            // a new tick: what was taken the tick before cannot come back
            assertEquals(1, bin.decrStackSize(0, 1).stackSize);
            w.time++;
            bin.setInventorySlotContents(0, copy);
            assertEquals(0, store.total());
        });
    }
}
