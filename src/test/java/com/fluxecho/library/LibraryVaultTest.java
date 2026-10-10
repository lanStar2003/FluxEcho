package com.fluxecho.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

/**
 * The team book vault keeps every book of every team, in order, through a save and a load, and never cuts a stack
 * down: a place that somehow held a stack goes in as one book with the rest given back, and a saved stack of more than
 * one gives one item at a time.
 */
class LibraryVaultTest {

    private static NBTTagCompound stack(int id, int meta, String name) {
        NBTTagCompound s = new NBTTagCompound();
        s.setShort("id", (short) id);
        s.setByte("Count", (byte) 1);
        s.setShort("Damage", (short) meta);
        if (name != null) {
            NBTTagCompound tag = new NBTTagCompound(), display = new NBTTagCompound();
            display.setString("Name", name);
            tag.setTag("display", display);
            s.setTag("tag", tag);
        }
        return s;
    }

    @Test
    void booksSurviveASaveAndALoad() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-00000000000a"), b = UUID.randomUUID();
        LibraryVault v = new LibraryVault(LibraryVault.NAME);
        for (int i = 0; i < 702; i++) v.add(a, stack(4000 + i % 50, i, i % 3 == 0 ? "book " + i : null));
        v.add(b, stack(1, 0, null));
        // a book of a mod that went missing is kept as it was saved
        NBTTagCompound unknown = stack(31_999, 5, "lost mod's book");
        v.add(b, unknown);

        NBTTagCompound saved = new NBTTagCompound();
        v.writeToNBT(saved);
        LibraryVault back = new LibraryVault(LibraryVault.NAME);
        back.readFromNBT(saved);

        assertEquals(
            702,
            back.raw(a)
                .size());
        for (int i = 0; i < 702; i++) assertEquals(
            v.raw(a)
                .get(i),
            back.raw(a)
                .get(i),
            "book " + i + " of team a");
        assertEquals(
            2,
            back.raw(b)
                .size());
        assertEquals(
            unknown,
            back.raw(b)
                .get(1));
        assertTrue(
            back.raw(UUID.randomUUID())
                .isEmpty(),
            "a team without books has an empty vault");
    }

    @Test
    void aBrokenEntryIsSkippedNotFatal() {
        NBTTagCompound saved = new NBTTagCompound();
        net.minecraft.nbt.NBTTagList teams = new net.minecraft.nbt.NBTTagList();
        NBTTagCompound bad = new NBTTagCompound();
        bad.setString("Team", "not a uuid");
        teams.appendTag(bad);
        saved.setTag("Teams", teams);
        LibraryVault v = new LibraryVault(LibraryVault.NAME);
        v.readFromNBT(saved);
        NBTTagCompound out = new NBTTagCompound();
        v.writeToNBT(out);
        assertEquals(
            0,
            out.getTagList("Teams", 10)
                .tagCount());
    }

    @Test
    void aSavedStackOfManyGivesOneItemAtATime() {
        NBTTagCompound s = stack(4000, 2, "a stack");
        s.setByte("Count", (byte) 3);
        assertFalse(LibraryVault.useOne(s), "two are left");
        assertEquals(2, s.getByte("Count"));
        assertFalse(LibraryVault.useOne(s), "one is left");
        assertEquals(1, s.getByte("Count"));
        assertTrue(LibraryVault.useOne(s), "the last one used it up");

        NBTTagCompound broken = stack(4001, 0, null);
        broken.setByte("Count", (byte) 0);
        assertTrue(LibraryVault.useOne(broken), "a count that makes no sense is used up, not kept forever");
    }

    /** A stack of no item (enough here: its count and tag are what is checked) marked with a tag. */
    private static ItemStack marked(int count, String mark) {
        ItemStack s = new ItemStack((Item) null, count, 0);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Mark", mark);
        s.setTagCompound(tag);
        return s;
    }

    private static String mark(ItemStack s) {
        return s.getTagCompound()
            .getString("Mark");
    }

    @Test
    void aPlaceHoldingAStackGoesInAsOneBookAndTheRestIsGivenBack() {
        ItemStack drones = marked(16, "drones"), one = marked(1, "one");
        List<ItemStack> more = new ArrayList<>();
        List<ItemStack> books = TileLibrary.oneEach(Arrays.asList(drones, null, one), more);

        assertEquals(2, books.size());
        assertEquals(1, books.get(0).stackSize);
        assertEquals("drones", mark(books.get(0)));
        assertEquals(1, books.get(1).stackSize);
        assertEquals("one", mark(books.get(1)));
        assertEquals(1, more.size(), "only the stack has a rest");
        assertEquals(15, more.get(0).stackSize, "the fifteen others are given back, not cut away");
        assertEquals("drones", mark(more.get(0)));
        assertEquals(16, drones.stackSize, "the stacks handed in are left as they were");
    }
}
