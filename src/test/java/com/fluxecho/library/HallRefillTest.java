package com.fluxecho.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import org.junit.jupiter.api.Test;

/**
 * A library takes the team's book vault in once, the first time it forms, whichever shape it stands as: a 0.9.2 hall
 * whose core was broken (its books went to the vault) and placed again gets its books back, into its 167 places, as
 * the Echo Archive does into its 702.
 */
class HallRefillTest {

    /** A library that only notes what it asks of the vault (no world needed). */
    private static final class Counting extends TileLibrary {

        int asks, capacityAsked = -1;

        @Override
        int takeFromVault(int capacity) {
            asks++;
            capacityAsked = capacity;
            return 0;
        }
    }

    /** A library read from a save without a shape: a 0.9.2 hall. */
    private static Counting hall(NBTTagCompound saved) {
        Counting l = new Counting();
        l.readFromNBT(saved);
        return l;
    }

    private static NBTTagCompound oldSave() {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("id", "fluxecho:library");
        t.setByte("Facing", (byte) 3);
        NBTTagCompound samples = new NBTTagCompound();
        samples.setInteger("Size", TileLibrary.HALL_BOOKS);
        t.setTag("Samples", samples);
        return t;
    }

    @Test
    void aHallTakesTheVaultInWhenItFirstForms() {
        Counting l = hall(oldSave());
        assertFalse(l.isArchive());
        l.formedChanged(true);
        assertEquals(1, l.asks, "a hall formed for the first time takes its books back");
        assertEquals(TileLibrary.HALL_BOOKS, l.capacityAsked, "into the hall's own places");
        l.formedChanged(false);
        l.formedChanged(true);
        assertEquals(1, l.asks, "once: later books come back with 取回");

        // saved and loaded, it remembers that it did (saving needs the tile class's id, as in the game)
        try {
            TileEntity.addMapping(Counting.class, "fluxecho:library_refill_test");
        } catch (IllegalArgumentException alreadyThere) {
            // registered before
        }
        NBTTagCompound again = new NBTTagCompound();
        l.writeToNBT(again);
        assertTrue(again.getBoolean("Refilled"));
        Counting back = hall(again);
        back.formedChanged(true);
        assertEquals(0, back.asks);
    }

    @Test
    void anArchiveTakesItInToo() {
        Counting a = new Counting();
        assertTrue(a.isArchive());
        a.formedChanged(true);
        assertEquals(1, a.asks);
        assertEquals(TileLibrary.ARCHIVE_BOOKS, a.capacityAsked);
    }
}
