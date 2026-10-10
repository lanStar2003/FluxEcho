package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

/** The Archive's book spines: opaque, stable, muted, varied, and leaning toward the library violet. */
class SpineColourTest {

    private static int channel(int argb, int shift) {
        return argb >> shift & 0xFF;
    }

    /** Whether a colour is palette entry {@code i}, shaded within the allowed range and tinted toward the violet. */
    private static boolean from(int argb, int i) {
        for (int shift = 0; shift <= 16; shift += 8) {
            double p = SpineColour.PALETTE[i] >> shift & 0xFF, v = SpineColour.VIOLET >> shift & 0xFF;
            double lo = p * (1 - SpineColour.SHADE), hi = Math.min(255, p * (1 + SpineColour.SHADE));
            lo += (v - lo) * SpineColour.TINT;
            hi += (v - hi) * SpineColour.TINT;
            int c = channel(argb, shift);
            if (c < Math.floor(Math.min(lo, hi)) - 1 || c > Math.ceil(Math.max(lo, hi)) + 1) return false;
        }
        return true;
    }

    private static int pick(int id, int meta, int name) {
        return SpineColour.pick(SpineColour.hash(id, meta, name));
    }

    @Test
    void everySpineIsOpaqueAndStable() {
        Random r = new Random(1);
        for (int k = 0; k < 2000; k++) {
            int id = r.nextInt(32000), meta = r.nextInt(32768), name = r.nextInt();
            int c = SpineColour.of(id, meta, name);
            assertEquals(0xFF, c >>> 24, "alpha of " + id + ":" + meta);
            assertNotEquals(0, c, "0 means no book to the shelf renderer");
            assertEquals(c, SpineColour.of(id, meta, name), "the same book, the same colour");
        }
    }

    @Test
    void spinesComeFromTheTintedPalette() {
        Random r = new Random(2);
        for (int k = 0; k < 2000; k++) {
            int id = r.nextInt(32000), meta = r.nextInt(16), name = r.nextInt();
            int c = SpineColour.of(id, meta, name);
            assertTrue(
                from(c, pick(id, meta, name)),
                String.format("%06X is no shaded, tinted palette colour", c & 0xFFFFFF));
        }
    }

    @Test
    void spinesAreMuted() {
        Random r = new Random(3);
        for (int k = 0; k < 2000; k++) {
            int c = SpineColour.of(r.nextInt(32000), r.nextInt(16), r.nextInt());
            int max = Math.max(channel(c, 16), Math.max(channel(c, 8), channel(c, 0)));
            int min = Math.min(channel(c, 16), Math.min(channel(c, 8), channel(c, 0)));
            assertTrue(max <= 200, String.format("%06X is too bright", c & 0xFFFFFF));
            assertTrue(max - min <= 140, String.format("%06X is too saturated", c & 0xFFFFFF));
        }
    }

    @Test
    void aShelfOfBooksVaries() {
        // a full Archive of different samples: many bindings, none of them taking over the room
        Map<Integer, Integer> uses = new HashMap<>();
        int books = LibraryUnits.SLOTS;
        for (int k = 0; k < books; k++) uses.merge(pick(4000 + k, k % 7, ("sample" + k).hashCode()), 1, Integer::sum);
        assertEquals(SpineColour.PALETTE.length, uses.size(), "only " + uses.size() + " bindings in use");
        for (Map.Entry<Integer, Integer> e : uses.entrySet())
            assertTrue(e.getValue() < books / 8, "binding " + e.getKey() + " on " + e.getValue() + " books");
        // the same item under another name (another bee species) is usually another book
        int same = 0;
        for (int k = 0; k < 200; k++)
            if (SpineColour.of(5000, 0, ("a" + k).hashCode()) == SpineColour.of(5000, 0, ("b" + k).hashCode())) same++;
        assertTrue(same < 20, same + " of 200 renamed books kept their colour");
    }

    @Test
    void theRoomLeansViolet() {
        // the tint pulls every channel toward the violet: blue rises on average, green falls behind it
        double blue = 0, paletteBlue = 0;
        Random r = new Random(4);
        int n = 3000;
        for (int k = 0; k < n; k++) blue += channel(SpineColour.of(r.nextInt(32000), 0, r.nextInt()), 0);
        for (int p : SpineColour.PALETTE) paletteBlue += p & 0xFF;
        assertTrue(blue / n > paletteBlue / SpineColour.PALETTE.length + 20, "the spines do not lean violet");
    }
}
