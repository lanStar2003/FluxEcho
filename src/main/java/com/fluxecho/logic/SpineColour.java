package com.fluxecho.logic;

/**
 * The colour of a book's spine in the Echo Archive's shelf units. Every sample on a shelf is drawn as a book, and a
 * room of 702 books reads as a library only if the spines vary the way old bindings do: muted leather browns, deep
 * reds, bottle greens, navy and ochre, never the bright colours of the items themselves. The colour follows from the
 * book alone (its item id, its metadata and the hash of its display name), so it is the same for every player and
 * stays put when the book is moved to another shelf; every colour is pulled a fifth of the way toward the library's
 * violet so the whole room shares one cast.
 */
public final class SpineColour {

    /** The library's own colour, which every spine leans toward. */
    public static final int VIOLET = 0x8A5CFF;
    /** How far each spine leans toward {@link #VIOLET}. */
    public static final double TINT = 0.2;
    /** How much lighter or darker one book may be than its palette colour (a fraction of each channel). */
    public static final double SHADE = 0.12;

    /** The bindings: leathers, reds, greens, blues, ochres and a few odd ones, all muted. */
    static final int[] PALETTE = {
        // leather browns
        0x6B4226, 0x8B5A2B, 0x5C3A21, 0x7A4E2D, 0x4E3220,
        // deep reds and burgundy
        0x7A1F1F, 0x8E2A2A, 0x5E1A1A, 0x5A1E2E,
        // bottle and moss greens
        0x2F4F2F, 0x3B5E3B, 0x24452E, 0x4A5A2A,
        // navy and slate blues
        0x1F2F4F, 0x23395B, 0x1B2A44, 0x34465A,
        // ochres and tan
        0xA67C2E, 0x8C6A1F, 0x9A7B4F,
        // teal and plum
        0x1F4A4A, 0x4A2B4A };

    private SpineColour() {}

    /**
     * The spine colour of a book: an opaque ARGB colour (alpha 0xFF, so never 0) picked by the item id, the metadata
     * and the display name's hash, lightened or darkened a little per book, then tinted toward {@link #VIOLET}.
     */
    public static int of(int itemId, int meta, int nameHash) {
        long h = hash(itemId, meta, nameHash);
        int base = PALETTE[pick(h)];
        // a second, independent draw for the shade, so books of one colour are not all alike
        double shade = 1 + SHADE * (2 * Mix.unit(h, 7) - 1);
        int r = channel(base >> 16, shade, VIOLET >> 16);
        int g = channel(base >> 8, shade, VIOLET >> 8);
        int b = channel(base, shade, VIOLET);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    static long hash(int itemId, int meta, int nameHash) {
        return Mix.hash(0x5B1E5B1EL, itemId, meta, nameHash);
    }

    /** The {@link #PALETTE} entry a book's hash picks. */
    static int pick(long hash) {
        return (int) Math.floorMod(hash, (long) PALETTE.length);
    }

    /** One channel: shaded, then mixed {@link #TINT} toward the violet's channel, kept in 0..255. */
    private static int channel(int base, double shade, int violet) {
        double c = Math.min(255, (base & 0xFF) * shade);
        double v = violet & 0xFF;
        return (int) Math.round(Math.max(0, Math.min(255, c + (v - c) * TINT)));
    }
}
