package com.fluxecho.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import org.junit.jupiter.api.Test;

import com.fluxecho.library.BookPacket;

/**
 * The recycle bin screen's pages (0.10.1): a page holds only the rows asked for with the store's totals, stays small
 * enough to sync however heavy the items' NBT (the heaviest kinds go without their tag, still named by the hash of the
 * whole kind), and a click finds its kind again even after the store changed order.
 */
class RecyclePageTest {

    private static NBTTagCompound kind(int id, int meta, String mark, int noise) {
        NBTTagCompound s = new NBTTagCompound();
        s.setShort("id", (short) id);
        s.setShort("Damage", (short) meta);
        if (mark != null || noise > 0) {
            NBTTagCompound tag = new NBTTagCompound();
            if (mark != null) tag.setString("Mark", mark);
            if (noise > 0) {
                // a backpack's worth of NBT that does not compress away
                Random r = new Random(id * 31L + meta);
                NBTTagList inv = new NBTTagList();
                for (int i = 0; i < noise; i++) inv.appendTag(new NBTTagString(Long.toHexString(r.nextLong())));
                tag.setTag("Items", inv);
            }
            s.setTag("tag", tag);
        }
        return s;
    }

    @Test
    void aPageHoldsTheRowsAskedForAndTheTotals() {
        RecycleStore store = new RecycleStore();
        for (int i = 0; i < 100; i++) store.add(kind(1 + i, 0, null, 0), 1000 - i);
        NBTTagCompound t = RecyclePage.write(store.list(), 18, 54, store.total());
        RecyclePage.Page p = RecyclePage.read(t);
        assertEquals(100, p.kinds);
        assertEquals(store.total(), p.items);
        assertEquals(18, p.first);
        assertEquals(54, p.entries.size());
        assertNull(p.at(17));
        assertEquals(1000 - 18, p.at(18).count, "entries in the store's order, largest first");
        assertEquals(1000 - 71, p.at(71).count);
        assertNull(p.at(72));
        assertFalse(p.at(18).cut);
        assertEquals(19, p.at(18).kind.getShort("id"));

        RecyclePage.Page end = RecyclePage.read(RecyclePage.write(store.list(), 90, 54, store.total()));
        assertEquals(10, end.entries.size(), "the last page is short");
        RecyclePage.Page past = RecyclePage.read(RecyclePage.write(store.list(), 500, 54, store.total()));
        assertEquals(100, past.first, "a first index past the end is brought into range");
        assertTrue(past.entries.isEmpty());
        assertTrue(RecyclePage.read(null).entries.isEmpty());
    }

    @Test
    void heavyNbtStaysUnderTheLimit() {
        RecycleStore store = new RecycleStore();
        for (int i = 0; i < 54; i++) store.add(kind(300 + i, 0, "pack " + i, 400), 54 - i);
        store.add(kind(5, 0, "light", 0), 1);
        NBTTagCompound t = RecyclePage.write(store.list(), 0, 60, store.total());
        assertTrue(BookPacket.compressedSize(t) < RecyclePage.LIMIT, "the page fits a sync packet");
        RecyclePage.Page p = RecyclePage.read(t);
        assertEquals(55, p.entries.size(), "every kind is on the page, cut or not");
        int cut = 0;
        for (RecyclePage.Entry e : p.entries) if (e.cut) {
            cut++;
            assertFalse(e.kind.hasKey("tag"), "a cut kind comes without its tag");
        }
        assertTrue(cut > 0, "the heaviest went without their tag");
        RecyclePage.Entry light = p.entries.get(54);
        assertFalse(light.cut, "a small tag still travels");
        assertTrue(light.kind.hasKey("tag"));
        // the hash names the whole kind, cut or not
        List<RecycleStore.Entry> list = store.list();
        for (int i = 0; i < list.size(); i++) assertEquals(RecyclePage.hash(list.get(i).kind), p.entries.get(i).hash);
    }

    @Test
    void aClickFindsItsKindAgain() {
        RecycleStore store = new RecycleStore();
        NBTTagCompound a = kind(1, 0, null, 0), b = kind(2, 0, null, 0), c = kind(2, 0, "named", 0);
        store.add(a, 30);
        store.add(b, 20);
        store.add(c, 10);
        List<RecycleStore.Entry> before = store.list();
        int hb = RecyclePage.hash(before.get(1).kind);
        assertEquals(1, RecyclePage.find(before, 1, hb));
        // the store changed order since the screen drew it
        store.add(b, 100);
        List<RecycleStore.Entry> after = store.list();
        int at = RecyclePage.find(after, 1, hb);
        assertEquals(0, at, "found where it went");
        assertEquals(b, after.get(at).kind);
        assertNotNull(after.get(RecyclePage.find(after, 2, RecyclePage.hash(c))));
        store.extract(c, 10);
        assertEquals(-1, RecyclePage.find(store.list(), 2, RecyclePage.hash(c)), "gone from the store");
        assertEquals(-1, RecyclePage.find(store.list(), -5, 12345));
    }
}
