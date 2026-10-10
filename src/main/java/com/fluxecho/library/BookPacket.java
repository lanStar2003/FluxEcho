package com.fluxecho.library;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * The Echo Library's books on their way to the client: all of them in the tile's description, the ones that changed
 * in a {@link com.fluxecho.nexus.NexusNet#LIB_DELTA} packet. Vanilla writes a packet's NBT compressed behind a short
 * length, so it may not take more than 32767 bytes; an Archive holds 702 books, and a shelf of bees carries a lot of
 * NBT. So a book travels whole (its stack with its NBT) while the NBT of the packet's whole books stays within a
 * budget, and otherwise as its item id and metadata only, which is all the shelves and most of the GUI need. The
 * budget starts generous (item NBT compresses well) and is halved until the books compress under {@link #LIMIT}.
 * <p>
 * Keys: {@code B} a list of whole stacks as {@code ItemStack.writeToNBT} writes them, each with its slot as
 * {@code At} (short); {@code Bi} int pairs {@code slot, id << 16 | meta} for the books sent without their NBT;
 * {@code E} the slots that are empty now (deltas only: a description lists every book, so a slot it leaves out is
 * empty). Only NBT classes are used, so the packet's size can be tested without a game.
 */
public final class BookPacket {

    /** The most the books of one packet may take compressed, leaving room for the rest of a description. */
    public static final int LIMIT = 28_000;
    /** The most slots one delta packet carries; the rest wait for the next tick. */
    public static final int DELTA_SLOTS = 64;
    /** Item NBT (bytes of its binary form) the whole books of one packet may carry at first. */
    static final int TAG_BUDGET = 256 * 1024;
    /** Below this the next try sends no item NBT at all. */
    private static final int MIN_BUDGET = 1024;

    private BookPacket() {}

    /** One slot's book: its stack as {@code ItemStack.writeToNBT} wrote it, or null for an empty slot. */
    public static final class Book {

        public final int slot;
        public final NBTTagCompound stack;

        public Book(int slot, NBTTagCompound stack) {
            this.slot = slot;
            this.stack = stack;
        }
    }

    /** What a reader does with each slot a packet names. */
    public interface Sink {

        /**
         * A slot's book: its whole stack ({@code stack} set, its slot in {@code At}), or only its item id and metadata
         * ({@code stack} null, {@code id >= 0}), or none ({@code stack} null, {@code id} -1).
         */
        void book(int slot, NBTTagCompound stack, int id, int meta);
    }

    /**
     * Writes the books into {@code t} ({@code B}, {@code Bi}, {@code E}) so that they compress under {@link #LIMIT}.
     * The stacks of the books sent whole get their slot as {@code At} and become part of the packet.
     */
    public static void write(NBTTagCompound t, List<Book> books) {
        int[] sizes = new int[books.size()];
        for (int i = 0; i < sizes.length; i++) {
            NBTTagCompound s = books.get(i).stack;
            sizes[i] = s != null && s.hasKey("tag", 10) ? binarySize(s.getCompoundTag("tag")) : 0;
        }
        int budget = TAG_BUDGET;
        while (true) {
            NBTTagCompound out = encode(books, sizes, budget);
            if (budget == 0 || compressedSize(out) < LIMIT) {
                for (String k : new String[] { "B", "Bi", "E" }) if (out.hasKey(k)) t.setTag(k, out.getTag(k));
                return;
            }
            budget = budget / 2 < MIN_BUDGET ? 0 : budget / 2;
        }
    }

    /** Hands every slot the packet names to the sink: whole books first, then the plain ones, then the empty slots. */
    public static void read(NBTTagCompound t, Sink sink) {
        NBTTagList whole = t.getTagList("B", 10);
        for (int k = 0; k < whole.tagCount(); k++) {
            NBTTagCompound s = whole.getCompoundTagAt(k);
            sink.book(s.getShort("At"), s, s.getShort("id") & 0xFFFF, s.getShort("Damage") & 0xFFFF);
        }
        int[] plain = t.getIntArray("Bi");
        for (int k = 0; k + 1 < plain.length; k += 2)
            sink.book(plain[k], null, plain[k + 1] >>> 16, plain[k + 1] & 0xFFFF);
        for (int slot : t.getIntArray("E")) sink.book(slot, null, -1, 0);
    }

    /**
     * The books with at most {@code budget} bytes of item NBT among the whole ones, {@code sizes} being each book's
     * item NBT in bytes.
     */
    static NBTTagCompound encode(List<Book> books, int[] sizes, int budget) {
        NBTTagList whole = new NBTTagList();
        int[] plain = new int[2 * books.size()], empty = new int[books.size()];
        int np = 0, ne = 0;
        long used = 0;
        for (int i = 0; i < books.size(); i++) {
            Book b = books.get(i);
            if (b.stack == null) {
                empty[ne++] = b.slot;
                continue;
            }
            int size = sizes[i];
            if (size == 0 || used + size <= budget) {
                used += size;
                b.stack.setShort("At", (short) b.slot);
                whole.appendTag(b.stack);
            } else {
                plain[np++] = b.slot;
                plain[np++] = (b.stack.getShort("id") & 0xFFFF) << 16 | b.stack.getShort("Damage") & 0xFFFF;
            }
        }
        NBTTagCompound out = new NBTTagCompound();
        out.setTag("B", whole);
        if (np > 0) out.setIntArray("Bi", Arrays.copyOf(plain, np));
        if (ne > 0) out.setIntArray("E", Arrays.copyOf(empty, ne));
        return out;
    }

    /**
     * How many bytes the compound takes compressed, as a packet writes it; past every limit if it cannot be written.
     */
    static int compressedSize(NBTTagCompound t) {
        try {
            return CompressedStreamTools.compress(t).length;
        } catch (IOException | RuntimeException e) {
            return Integer.MAX_VALUE;
        }
    }

    /** How many bytes the compound takes uncompressed (its binary NBT form). */
    static int binarySize(NBTTagCompound t) {
        Counter c = new Counter();
        try {
            CompressedStreamTools.write(t, new DataOutputStream(c));
        } catch (IOException | RuntimeException e) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.min(Integer.MAX_VALUE, c.n);
    }

    /** An output that only counts. */
    private static final class Counter extends OutputStream {

        long n;

        @Override
        public void write(int b) {
            n++;
        }

        @Override
        public void write(byte[] b, int off, int len) {
            n += len;
        }
    }
}
