package com.fluxecho.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.LibraryUnits;

/**
 * The Echo Library's books on the wire ({@link BookPacket}): a description of a full Archive and a delta of the most
 * slots one carries must fit a vanilla packet (its NBT is written compressed behind a short length) whatever NBT the
 * books carry, every slot must come back once, and books with ordinary NBT should travel whole.
 */
class LibraryPacketTest {

    /** Room left in a packet's 32767 bytes for the rest of a description. */
    private static final int PACKET = 32767;

    /** An item stack as {@code ItemStack.writeToNBT} writes it. */
    private static NBTTagCompound stack(int id, int meta, NBTTagCompound tag) {
        NBTTagCompound s = new NBTTagCompound();
        s.setShort("id", (short) id);
        s.setByte("Count", (byte) 1);
        s.setShort("Damage", (short) meta);
        if (tag != null) s.setTag("tag", tag);
        return s;
    }

    private static String noise(Random r, int n) {
        StringBuilder b = new StringBuilder(n);
        for (int i = 0; i < n; i++) b.append((char) (33 + r.nextInt(94)));
        return b.toString();
    }

    /** NBT that does not compress: long random text, random bytes and ints. */
    private static NBTTagCompound noisy(Random r) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("Data", noise(r, 4000));
        byte[] bytes = new byte[3000];
        r.nextBytes(bytes);
        t.setByteArray("Bytes", bytes);
        int[] ints = new int[400];
        for (int i = 0; i < ints.length; i++) ints[i] = r.nextInt();
        t.setIntArray("Ints", ints);
        return t;
    }

    /** NBT shaped like a Forestry bee's: a genome of named alleles, repetitive and large. */
    private static NBTTagCompound bee(int k) {
        String[] species = { "Forest", "Meadows", "Common", "Cultivated", "Noble", "Majestic", "Imperial", "Diligent",
            "Unweary", "Industrious", "Steadfast", "Valiant", "Heroic", "Sinister", "Fiendish", "Demonic" };
        NBTTagCompound t = new NBTTagCompound();
        NBTTagList chromosomes = new NBTTagList();
        for (int c = 0; c < 15; c++) {
            NBTTagCompound ch = new NBTTagCompound();
            ch.setByte("Slot", (byte) c);
            ch.setString(
                "UID0",
                "forestry.allele" + (c == 0 ? "species" + species[k % species.length] : "gene" + c + "x" + k % 5));
            ch.setString(
                "UID1",
                "forestry.allele"
                    + (c == 0 ? "species" + species[(k / 3) % species.length] : "gene" + c + "y" + k % 7));
            chromosomes.appendTag(ch);
        }
        NBTTagCompound genome = new NBTTagCompound();
        genome.setTag("Chromosomes", chromosomes);
        t.setTag("Genome", genome);
        t.setInteger("Health", 40 + k % 30);
        t.setInteger("MaxH", 70);
        t.setBoolean("IsAnalyzed", true);
        t.setTag("Name", new NBTTagString("Bee " + k));
        return t;
    }

    private static int compressed(NBTTagCompound t) throws IOException {
        return CompressedStreamTools.compress(t).length;
    }

    /** What a packet says about each slot: a whole stack, an id and meta only, or empty. */
    private static final class Seen {

        final NBTTagCompound[] whole;
        final int[] id, meta;
        final boolean[] empty;
        int named;

        Seen(int slots) {
            whole = new NBTTagCompound[slots];
            id = new int[slots];
            meta = new int[slots];
            empty = new boolean[slots];
            java.util.Arrays.fill(id, Integer.MIN_VALUE);
        }
    }

    private static Seen read(NBTTagCompound t, int slots) {
        Seen s = new Seen(slots);
        BookPacket.read(t, (slot, stack, id, meta) -> {
            assertTrue(slot >= 0 && slot < slots, "slot " + slot);
            assertEquals(Integer.MIN_VALUE, s.id[slot], "slot " + slot + " named twice");
            s.named++;
            s.whole[slot] = stack;
            s.id[slot] = id;
            s.meta[slot] = meta;
            s.empty[slot] = id < 0;
        });
        return s;
    }

    @Test
    void aFullArchiveOfNoisyBooksFits() throws IOException {
        Random r = new Random(11);
        List<BookPacket.Book> books = new ArrayList<>();
        for (int i = 0; i < LibraryUnits.SLOTS; i++)
            books.add(new BookPacket.Book(i, stack(1 + r.nextInt(31999), r.nextInt(32768), noisy(r))));
        NBTTagCompound t = new NBTTagCompound();
        BookPacket.write(t, books);
        int size = compressed(t);
        assertTrue(size < BookPacket.LIMIT, "702 noisy books take " + size + " bytes compressed");
        Seen s = read(t, LibraryUnits.SLOTS);
        assertEquals(LibraryUnits.SLOTS, s.named, "every book once");
        for (int i = 0; i < LibraryUnits.SLOTS; i++) {
            NBTTagCompound b = books.get(i).stack;
            assertEquals(b.getShort("id") & 0xFFFF, s.id[i], "id of " + i);
            assertEquals(b.getShort("Damage") & 0xFFFF, s.meta[i], "meta of " + i);
        }
    }

    @Test
    void aFullArchiveOfBeesTravelsLargelyWhole() throws IOException {
        List<BookPacket.Book> books = new ArrayList<>();
        for (int i = 0; i < LibraryUnits.SLOTS; i++) books.add(new BookPacket.Book(i, stack(4000, 0, bee(i))));
        NBTTagCompound t = new NBTTagCompound();
        BookPacket.write(t, books);
        int size = compressed(t);
        assertTrue(size < BookPacket.LIMIT, "702 bees take " + size + " bytes compressed");
        Seen s = read(t, LibraryUnits.SLOTS);
        assertEquals(LibraryUnits.SLOTS, s.named, "every bee once");
        int whole = 0;
        for (NBTTagCompound w : s.whole) if (w != null) whole++;
        assertTrue(whole >= 100, "only " + whole + " bees keep their genome");
        // the whole ones carry their NBT unchanged, the others their item
        for (int i = 0; i < LibraryUnits.SLOTS; i++) {
            if (s.whole[i] != null) assertEquals(bee(i), s.whole[i].getCompoundTag("tag"), "bee " + i + "'s genome");
            else assertEquals(4000, s.id[i], "bee " + i + "'s item");
        }
    }

    @Test
    void plainBooksAllTravelWhole() throws IOException {
        List<BookPacket.Book> books = new ArrayList<>();
        Random r = new Random(12);
        for (int i = 0; i < LibraryUnits.SLOTS; i++)
            books.add(new BookPacket.Book(i, stack(1 + r.nextInt(31999), r.nextInt(32768), null)));
        NBTTagCompound t = new NBTTagCompound();
        BookPacket.write(t, books);
        assertTrue(compressed(t) < BookPacket.LIMIT);
        Seen s = read(t, LibraryUnits.SLOTS);
        for (int i = 0; i < LibraryUnits.SLOTS; i++) assertNotNull(s.whole[i], "book " + i + " whole");
        assertEquals(0, t.getIntArray("Bi").length, "nothing sent plain");
    }

    @Test
    void aHugeBookTravelsPlainAndTheRestWhole() throws IOException {
        Random r = new Random(13);
        List<BookPacket.Book> books = new ArrayList<>();
        NBTTagCompound huge = new NBTTagCompound();
        byte[] bytes = new byte[400_000];
        r.nextBytes(bytes);
        huge.setByteArray("Blob", bytes);
        books.add(new BookPacket.Book(5, stack(300, 7, huge)));
        for (int i = 6; i < 200; i++) books.add(new BookPacket.Book(i, stack(301, i, bee(i))));
        NBTTagCompound t = new NBTTagCompound();
        BookPacket.write(t, books);
        assertTrue(compressed(t) < BookPacket.LIMIT);
        Seen s = read(t, LibraryUnits.SLOTS);
        assertNull(s.whole[5], "the huge book travels without its NBT");
        assertEquals(300, s.id[5]);
        assertEquals(7, s.meta[5]);
        for (int i = 6; i < 200; i++) assertNotNull(s.whole[i], "bee " + i + " whole");
    }

    @Test
    void aDeltaOfTheMostSlotsFits() throws IOException {
        Random r = new Random(14);
        List<BookPacket.Book> books = new ArrayList<>();
        for (int i = 0; i < BookPacket.DELTA_SLOTS; i++) books.add(
            new BookPacket.Book(i * 11, i % 4 == 0 ? null : stack(1 + r.nextInt(31999), r.nextInt(32768), noisy(r))));
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("Dim", -1);
        t.setInteger("X", -30_000_000);
        t.setInteger("Y", 255);
        t.setInteger("Z", 30_000_000);
        t.setInteger("Rv", Integer.MAX_VALUE);
        t.setInteger("Ep", Integer.MIN_VALUE);
        BookPacket.write(t, books);
        int size = compressed(t);
        assertTrue(size < BookPacket.LIMIT, "a delta of " + BookPacket.DELTA_SLOTS + " noisy slots takes " + size);
        Seen s = read(t, LibraryUnits.SLOTS);
        assertEquals(BookPacket.DELTA_SLOTS, s.named, "every slot of the delta once");
        for (int i = 0; i < BookPacket.DELTA_SLOTS; i++)
            assertEquals(i % 4 == 0, s.empty[i * 11], "slot " + i * 11 + " emptied or filled");
    }

    @Test
    void theLimitLeavesRoomInAPacket() {
        // the rest of a description (state, dock, owner, shape, nexus position) is a few hundred bytes at most
        assertTrue(BookPacket.LIMIT + 2_000 < PACKET);
    }
}
