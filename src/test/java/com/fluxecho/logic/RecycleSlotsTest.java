package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The recycle bin's virtual slots (0.10.1): the 27 largest kinds, each at most a stack; taking out through either way
 * an inventory is emptied decrements the store; a hopper that had no room puts its item back, but nothing more than was
 * taken; and two extractors, or an extractor and a player, acting in the same tick never take the same items twice.
 */
class RecycleSlotsTest {

    /** A store of named kinds, largest first like the campus's recycle store, with a stack size per kind. */
    static final class Fake implements RecycleSlots.Store<String> {

        final Map<String, Long> counts = new LinkedHashMap<>();
        final Map<String, Integer> stack = new HashMap<>();
        int version;

        Fake put(String k, long n, int stackSize) {
            counts.put(k, n);
            stack.put(k, stackSize);
            version++;
            return this;
        }

        long total() {
            long t = 0;
            for (long n : counts.values()) t += n;
            return t;
        }

        @Override
        public List<String> largest() {
            List<String> l = new ArrayList<>(counts.keySet());
            l.sort((a, b) -> Long.compare(counts.get(b), counts.get(a)));
            return l;
        }

        @Override
        public long count(String k) {
            Long n = counts.get(k);
            return n == null ? 0 : n;
        }

        @Override
        public int stackSize(String k) {
            Integer s = stack.get(k);
            return s == null ? 64 : s;
        }

        @Override
        public long extract(String k, long max) {
            long have = count(k), take = Math.min(have, Math.max(0, max));
            if (take <= 0) return 0;
            if (take == have) counts.remove(k);
            else counts.put(k, have - take);
            version++;
            return take;
        }

        @Override
        public void add(String k, long n) {
            counts.merge(k, n, Long::sum);
            version++;
        }

        @Override
        public int version() {
            return version;
        }
    }

    @Test
    void theLargestKindsAsStacks() {
        Fake s = new Fake();
        for (int i = 0; i < 30; i++) s.put("k" + i, 10 + i, 64);
        s.put("pearl", 1000, 16)
            .put("sword", 3, 1)
            .put("gone", 500, 0);
        RecycleSlots<String> v = new RecycleSlots<>();
        assertTrue(v.refresh(s, 1));
        assertEquals("pearl", v.kind(0), "the largest first");
        assertEquals(16, v.size(0), "at most one stack of it");
        for (int i = 0; i < RecycleSlots.SLOTS; i++) assertTrue(v.kind(i) != null, "every slot used");
        for (int i = 0; i < RecycleSlots.SLOTS; i++)
            assertFalse("gone".equals(v.kind(i)), "a kind that cannot be a stack is left out");
        for (int i = 1; i < RecycleSlots.SLOTS; i++) assertEquals(39 - (i - 1), v.size(i), "then by count");
        for (int i = 0; i < RecycleSlots.SLOTS; i++) assertFalse("sword".equals(v.kind(i)), "not among the 27 largest");
        assertNull(v.kind(RecycleSlots.SLOTS));
        assertEquals(0, v.size(-1));
        assertFalse(v.refresh(s, 1), "nothing changed within the tick");

        Fake few = new Fake().put("a", 5, 64);
        RecycleSlots<String> w = new RecycleSlots<>();
        w.refresh(few, 1);
        assertEquals(5, w.size(0));
        assertNull(w.kind(1), "the rest are empty");
        assertEquals(0, w.take(few, 1, 10));
    }

    @Test
    void takingDecrementsTheStore() {
        Fake s = new Fake().put("cobble", 1000, 64);
        RecycleSlots<String> v = new RecycleSlots<>();
        v.refresh(s, 1);
        assertEquals(10, v.take(s, 0, 10));
        assertEquals(990, s.count("cobble"));
        assertEquals(54, v.size(0), "the slot shows what is left of its stack this tick");
        assertEquals(54, v.take(s, 0, 100), "never more than the slot shows");
        assertEquals(936, s.count("cobble"));
        assertEquals(0, v.size(0));
        v.refresh(s, 2);
        assertEquals(64, v.size(0), "a full stack again the next tick");
    }

    @Test
    void settingASmallerStackTakesTheDifference() {
        Fake s = new Fake().put("dirt", 40, 64);
        RecycleSlots<String> v = new RecycleSlots<>();
        v.refresh(s, 1);
        assertEquals(-15, v.set(s, 0, 25), "an AE bus shrank the stack it read and set it back");
        assertEquals(25, s.count("dirt"));
        assertEquals(0, v.set(s, 0, 25), "the same size again moves nothing");
        assertEquals(-25, v.set(s, 0, 0), "set to nothing: all it showed is taken");
        assertEquals(0, s.count("dirt"));
        assertEquals(0, v.set(s, 5, 3), "an empty slot takes nothing in");
        assertEquals(0, s.total());
    }

    @Test
    void aHopperPutsBackWhatItCouldNotTake() {
        Fake s = new Fake().put("log", 1000, 64);
        RecycleSlots<String> v = new RecycleSlots<>();
        v.refresh(s, 1);
        // the vanilla hopper: read, copy, take one, find no room, set the copy back
        int read = v.size(0);
        assertEquals(1, v.take(s, 0, 1));
        assertEquals(999, s.count("log"));
        assertEquals(1, v.set(s, 0, read));
        assertEquals(1000, s.count("log"), "the item it took went back");
        assertEquals(64, v.size(0));
        assertEquals(0, v.set(s, 0, 64 + 30), "nothing more than was taken goes in");
        assertEquals(1000, s.count("log"));

        // put back only within the tick it was taken
        assertEquals(1, v.take(s, 0, 1));
        v.refresh(s, 2);
        assertEquals(0, v.set(s, 0, 65));
        assertEquals(999, s.count("log"));
    }

    @Test
    void twoExtractorsInOneTickNeverTakeTheSameItems() {
        Fake s = new Fake().put("ore", 50, 64);
        RecycleSlots<String> a = new RecycleSlots<>(), b = new RecycleSlots<>();
        a.refresh(s, 7);
        b.refresh(s, 7);
        assertEquals(50, a.size(0));
        assertEquals(50, b.size(0));
        // a takes everything by shrinking the stack it read
        assertEquals(-50, a.set(s, 0, 0));
        // b looks again before acting (the store changed under it) and sees nothing left
        assertTrue(b.refresh(s, 7));
        assertNull(b.kind(0));
        assertEquals(0, b.take(s, 0, 64));
        assertEquals(0, b.set(s, 0, 0));
        assertEquals(0, s.total());

        // a player takes some through the screen in the same tick: the view follows
        Fake t = new Fake().put("sand", 64, 64);
        RecycleSlots<String> c = new RecycleSlots<>();
        c.refresh(t, 9);
        assertEquals(40, t.extract("sand", 40));
        assertTrue(c.refresh(t, 9));
        assertEquals(24, c.size(0), "never more than the store holds now");
        assertEquals(24, c.take(t, 0, 64));
        assertEquals(0, t.total());
    }

    @Test
    void puttingBackIsBoundToTheItemsTakenFromThatSlot() {
        Fake s = new Fake().put("a", 100, 64)
            .put("b", 90, 64);
        RecycleSlots<String> v = new RecycleSlots<>();
        v.refresh(s, 3);
        assertEquals(64, v.take(s, 0, 64));
        long before = s.total();
        assertEquals(0, v.set(s, 1, 100), "what was taken from slot 0 does not let slot 1 grow");
        assertEquals(before, s.total());
    }
}
