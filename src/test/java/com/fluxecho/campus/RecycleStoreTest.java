package com.fluxecho.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.jupiter.api.Test;

/**
 * The campus's recycle store (0.10.1): kinds merge by item, meta and NBT, counts are longs, extraction never takes
 * more than there is, the list is largest first, the store reports full at its mark without ever losing what comes in,
 * and it survives saving. Kinds are built as the NBT {@code ItemStack.writeToNBT} writes, so no item registry is
 * needed.
 */
class RecycleStoreTest {

    /** A kind as {@link RecycleStore#kindOf} makes it: the item's id and damage, and its tag if any (no count). */
    private static NBTTagCompound kind(int id, int meta, String mark) {
        NBTTagCompound s = new NBTTagCompound();
        s.setShort("id", (short) id);
        s.setShort("Damage", (short) meta);
        if (mark != null) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("Mark", mark);
            s.setTag("tag", tag);
        }
        return s;
    }

    @Test
    void kindsMergeByItemMetaAndNbt() {
        RecycleStore r = new RecycleStore();
        r.add(kind(4, 0, null), 10);
        r.add(kind(4, 0, null), 5);
        r.add(kind(4, 1, null), 3);
        r.add(kind(4, 0, "chest of Ann"), 2);
        r.add(kind(4, 0, "chest of Ann"), 1);
        assertEquals(3, r.kinds(), "cobblestone, its other meta, and the one with a tag");
        assertEquals(21, r.total());
        assertEquals(15, r.count(kind(4, 0, null)));
        assertEquals(3, r.count(kind(4, 1, null)));
        assertEquals(3, r.count(kind(4, 0, "chest of Ann")));
        assertEquals(0, r.count(kind(4, 0, "someone else")));
        r.add(null, 5);
        r.add(kind(5, 0, null), 0);
        r.add(kind(5, 0, null), -3);
        assertEquals(3, r.kinds(), "nothing, or nothing of something, adds no kind");
        assertEquals(0, r.count(null));
    }

    @Test
    void theStoreKeepsItsOwnCopyOfAKind() {
        RecycleStore r = new RecycleStore();
        NBTTagCompound k = kind(17, 0, null);
        r.add(k, 64);
        k.setShort("Damage", (short) 3);
        assertEquals(64, r.count(kind(17, 0, null)), "changing the caller's compound changes nothing stored");
        assertEquals(0, r.count(k));
    }

    @Test
    void extractTakesAtMostWhatIsThere() {
        RecycleStore r = new RecycleStore();
        r.add(kind(3, 0, null), 100);
        assertEquals(64, r.extract(kind(3, 0, null), 64));
        assertEquals(36, r.count(kind(3, 0, null)));
        assertEquals(36, r.extract(kind(3, 0, null), 64), "the rest, no more");
        assertEquals(0, r.kinds(), "a kind taken out whole is forgotten");
        assertEquals(0, r.total());
        assertTrue(r.isEmpty());
        assertEquals(0, r.extract(kind(3, 0, null), 64));
        r.add(kind(3, 0, null), 5);
        assertEquals(0, r.extract(kind(3, 0, null), 0));
        assertEquals(0, r.extract(null, 5));
        assertEquals(5, r.total());
    }

    @Test
    void theListIsLargestFirstThenInTheOrderTheKindsCame() {
        RecycleStore r = new RecycleStore();
        r.add(kind(1, 0, null), 5);
        r.add(kind(2, 0, null), 50);
        r.add(kind(3, 0, null), 5);
        r.add(kind(4, 0, null), 500);
        List<RecycleStore.Entry> l = r.list();
        assertEquals(4, l.size());
        assertEquals(500, l.get(0).count);
        assertEquals(50, l.get(1).count);
        assertEquals(kind(1, 0, null), l.get(2).kind, "a tie keeps the order of arrival");
        assertEquals(kind(3, 0, null), l.get(3).kind);
        assertTrue(l == r.list(), "cached until the next change");
        r.extract(kind(4, 0, null), 499);
        assertEquals(
            kind(2, 0, null),
            r.list()
                .get(0).kind,
            "and sorted again after it");
    }

    @Test
    void fullAtTheMarkButNothingIsLost() {
        RecycleStore r = new RecycleStore();
        for (int i = 0; i < RecycleStore.MAX_KINDS - 1; i++) r.add(kind(i, 0, null), 1);
        assertFalse(r.full());
        r.add(kind(RecycleStore.MAX_KINDS - 1, 0, null), 1);
        assertTrue(r.full(), "the builder waits from here");
        r.add(kind(30000, 0, null), 7);
        assertEquals(RecycleStore.MAX_KINDS + 1, r.kinds(), "what one removal brings in is still kept");
        assertEquals(7, r.count(kind(30000, 0, null)));
        r.extract(kind(30000, 0, null), 7);
        assertTrue(r.full());
        r.extract(kind(0, 0, null), 1);
        assertFalse(r.full(), "room again once a member took something out");
    }

    /** A kind carrying about {@code bytes} of NBT, like an AE cell or a GT machine with its contents. */
    private static NBTTagCompound heavy(int id, int bytes) {
        NBTTagCompound k = kind(id, 0, null);
        NBTTagCompound tag = new NBTTagCompound();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < bytes; i++) b.append((char) ('a' + (i * 7 + id) % 26));
        tag.setString("Data", b.toString());
        k.setTag("tag", tag);
        return k;
    }

    /** The bytes the store's saved list really takes, uncompressed. */
    private static int savedBytes(RecycleStore r) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NBTTagCompound t = new NBTTagCompound();
        t.setTag("L", r.write());
        CompressedStreamTools.write(t, new DataOutputStream(bytes));
        return bytes.size();
    }

    @Test
    void heavyKindsFillItByTheirBytes() throws IOException {
        RecycleStore r = new RecycleStore();
        int n = 0;
        while (!r.full()) r.add(heavy(n++, 10_000), 1);
        assertTrue(n < 40, "a few dozen heavy kinds, far from the kind mark: " + n);
        assertTrue(r.bytes() >= RecycleStore.MAX_BYTES);
        assertTrue(r.bytes() < RecycleStore.MAX_BYTES + 11_000, "one removal past the mark at most");
        int real = savedBytes(r);
        assertTrue(r.bytes() >= real - 64 && r.bytes() <= real * 1.05, "the estimate follows the saved size: " + real);
        assertTrue(real < 1_000_000 / 3, "well short of what a chunk can save");

        // more of a kind it holds adds no bytes; a kind taken out whole gives its bytes back
        long before = r.bytes();
        r.add(heavy(0, 10_000), 5);
        assertEquals(before, r.bytes());
        r.extract(heavy(0, 10_000), 1);
        assertEquals(before, r.bytes(), "part of a kind taken out: it is still saved");
        r.extract(heavy(0, 10_000), 5);
        assertTrue(r.bytes() < before);
        assertFalse(r.full(), "room again for the builder");

        // the estimate is the same after a load, and nothing after a clear
        RecycleStore back = new RecycleStore();
        back.read(r.write());
        assertEquals(r.bytes(), back.bytes());
        back.clear();
        assertEquals(0, back.bytes());
        assertTrue(RecycleStore.entryBytes(kind(4, 0, null)) < 64, "a plain block is a few bytes");
    }

    @Test
    void aPutAsideStoreDrainsAsFarAsThereIsRoom() {
        RecycleStore to = new RecycleStore();
        for (int i = 0; i < RecycleStore.MAX_KINDS - 2; i++) to.add(kind(i, 0, null), 1);
        RecycleStore aside = new RecycleStore();
        aside.add(kind(20000, 0, null), 3_000_000_000L);
        aside.add(kind(20001, 0, null), 7);
        aside.add(kind(20002, 0, null), 5);
        long total = to.total() + aside.total();
        assertEquals(2, RecycleStore.moveInto(aside, to), "the two largest, whole, until it is full");
        assertTrue(to.full());
        assertEquals(3_000_000_000L, to.count(kind(20000, 0, null)), "a kind is never split");
        assertEquals(1, aside.kinds());
        assertEquals(5, aside.count(kind(20002, 0, null)));
        assertEquals(total, to.total() + aside.total(), "nothing lost on the way");
        assertEquals(0, RecycleStore.moveInto(aside, to), "no room, nothing moves");
        to.extract(kind(0, 0, null), 1);
        to.extract(kind(1, 0, null), 1);
        assertEquals(1, RecycleStore.moveInto(aside, to));
        assertTrue(aside.isEmpty());
    }

    @Test
    void theTeamsPutAsideStoresSurviveSavingAndDrain() {
        UUID team = UUID.randomUUID(), other = UUID.randomUUID();
        RecycleOverflow o = new RecycleOverflow(RecycleOverflow.NAME);
        RecycleStore carried = new RecycleStore();
        carried.add(kind(4, 0, null), 9_000_000_000L);
        carried.add(kind(54, 0, "contents"), 2);
        o.add(team, carried.write());
        o.add(team, carried.write());
        o.add(null, carried.write());
        assertEquals(
            18_000_000_000L,
            o.of(team)
                .count(kind(4, 0, null)),
            "put aside twice, merged");
        assertNull(o.of(other));
        assertEquals(
            9_000_000_000L,
            o.of(null)
                .count(kind(4, 0, null)),
            "a nexus with no team has its own");

        NBTTagCompound saved = new NBTTagCompound();
        o.writeToNBT(saved);
        RecycleOverflow back = new RecycleOverflow(RecycleOverflow.NAME);
        back.readFromNBT(saved);
        assertEquals(
            4,
            back.of(team)
                .count(kind(54, 0, "contents")));

        RecycleStore nexus = new RecycleStore();
        assertEquals(0, back.drain(other, nexus), "another team's nexus takes nothing");
        assertEquals(2, back.drain(team, nexus));
        assertEquals(18_000_000_000L, nexus.count(kind(4, 0, null)));
        assertNull(back.of(team), "drained whole: forgotten");
        NBTTagCompound again = new NBTTagCompound();
        back.writeToNBT(again);
        assertEquals(
            1,
            again.getTagList("Teams", 10)
                .tagCount(),
            "only the store of no team is left to save");
    }

    @Test
    void countsAreLongsAndSaturate() {
        RecycleStore r = new RecycleStore();
        r.add(kind(4, 0, null), 3_000_000_000L);
        assertEquals(3_000_000_000L, r.count(kind(4, 0, null)), "a graded disc yields more cobblestone than an int");
        r.add(kind(4, 0, null), Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, r.count(kind(4, 0, null)));
        assertEquals(Long.MAX_VALUE, r.total());
    }

    @Test
    void itSurvivesSaving() {
        RecycleStore r = new RecycleStore();
        r.add(kind(4, 0, null), 3_000_000_000L);
        r.add(kind(54, 0, "contents"), 2);
        r.add(kind(4, 1, null), 9);
        RecycleStore back = new RecycleStore();
        back.read(r.write());
        assertEquals(r.kinds(), back.kinds());
        assertEquals(r.total(), back.total());
        for (int i = 0; i < r.kinds(); i++) {
            assertEquals(
                r.list()
                    .get(i).kind,
                back.list()
                    .get(i).kind);
            assertEquals(
                r.list()
                    .get(i).count,
                back.list()
                    .get(i).count);
        }
        // broken entries are skipped, the same kind twice is merged, nothing at all empties the store
        NBTTagList odd = new NBTTagList();
        NBTTagCompound noKind = new NBTTagCompound();
        noKind.setLong("N", 5);
        odd.appendTag(noKind);
        for (long n : new long[] { 0, -4, 6, 4 }) {
            NBTTagCompound e = new NBTTagCompound();
            e.setTag("K", kind(8, 0, null));
            e.setLong("N", n);
            odd.appendTag(e);
        }
        NBTTagCompound empty = new NBTTagCompound();
        empty.setTag("K", new NBTTagCompound());
        empty.setLong("N", 3);
        odd.appendTag(empty);
        back.read(odd);
        assertEquals(1, back.kinds());
        assertEquals(10, back.count(kind(8, 0, null)));
        back.read(null);
        assertTrue(back.isEmpty());
    }

    @Test
    void everyChangeIsHeard() {
        int[] heard = { 0 };
        RecycleStore r = new RecycleStore(() -> heard[0]++);
        int v = r.version();
        r.add(kind(1, 0, null), 2);
        r.extract(kind(1, 0, null), 1);
        r.extract(kind(9, 0, null), 1);
        r.clear();
        r.clear();
        assertEquals(3, heard[0], "an add, an extract and a clear; nothing taken or nothing to clear is no change");
        assertNotEquals(v, r.version());
        int before = r.version();
        r.read(new NBTTagList());
        assertEquals(3, heard[0], "a load is not a change to save");
        assertNotEquals(before, r.version(), "but views of it are stale");
    }
}
