package com.fluxecho.logic.ui;

import java.util.Locale;

/**
 * The rules of FluxMono, the UI's own monospaced display face (ui/FINAL.md section 1.4.1, 2.2, 2.4). It is drawn by
 * {@code tools/UiAtlas.java} from stroke tables, not from any font file, and sampled from the atlas with linear
 * filtering, so it never passes through Minecraft's font renderer (or Angelica's batching of it) and can be rotated.
 * <p>
 * Five sizes: 6 and 8 take the small glyphs (ASCII and a few symbols, cells of 20 x 32 texels at 4x); 12, 16 and 24
 * take the large glyphs (digits, {@code . , : % + − k M G T} and the roman {@code I V X}, cells of 48 x 96). Every
 * glyph advances {@code round(size x 0.6)} (6 -> 4, 8 -> 5, 12 -> 7, 16 -> 10, 24 -> 14), so numbers do not shake when
 * they change. A character the large set lacks is drawn from the small set, scaled up. Lowercase letters are small
 * capitals. Eyebrows (the English line under a header) are size 6, upper case, tracked +1.
 */
public final class FluxMono {

    private FluxMono() {}

    /** The five sizes, in GUI pixels of glyph height. */
    public static final int[] SIZES = { 6, 8, 12, 16, 24 };
    /** Size of eyebrows, captions and units (C), body numbers (N2), value tiles (N1), and the display (D). */
    public static final int CAPTION = 6, BODY = 8, VALUE = 12, VALUE_LARGE = 16, DISPLAY = 24;
    /** Eyebrow tracking, px between glyphs. */
    public static final int EYEBROW_TRACKING = 1;

    /** The small glyphs in atlas order: ASCII 0x20-0x7E, then the symbols. */
    public static final String SMALL = AtlasMap.SMALL;
    /** The large glyphs in atlas order. */
    public static final String LARGE = AtlasMap.LARGE;

    /** The advance of every glyph at a size: height x 0.6, rounded. */
    public static int advance(int size) {
        return Math.round(size * 0.6f);
    }

    /** Whether a size uses the large glyphs. */
    public static boolean large(int size) {
        return size >= 12;
    }

    /** The drawn width of a glyph cell at a size (the rest of the advance is the gap to the next glyph). */
    public static float glyphWidth(int size) {
        return large(size) ? size * (float) AtlasMap.LARGE_W / AtlasMap.LARGE_H
            : size * (float) AtlasMap.SMALL_W / AtlasMap.SMALL_H;
    }

    /**
     * The baseline of glyphs drawn at {@code size} with their top at 0: where a number and its unit line up. Capitals
     * and digits sit on it; a comma's tail goes below.
     */
    public static float baseline(int size) {
        return large(size) ? size * 87f / AtlasMap.LARGE_H : size * 28f / AtlasMap.SMALL_H;
    }

    /** The width of a string: every character advances the same, plus {@code tracking} between characters. */
    public static int width(String s, int size, int tracking) {
        if (s == null || s.isEmpty()) return 0;
        return s.length() * advance(size) + (s.length() - 1) * tracking;
    }

    /** Index of a character among the small glyphs, or -1. */
    public static int smallIndex(char c) {
        if (c >= 0x20 && c < 0x7F) return c - 0x20;
        return c < 0x80 ? -1 : SMALL.indexOf(c, 0x7F - 0x20);
    }

    /** Index of a character among the large glyphs, or -1; the hyphen draws as the minus sign. */
    public static int largeIndex(char c) {
        return LARGE.indexOf(c == '-' ? '−' : c);
    }

    public static boolean inSmall(char c) {
        return smallIndex(c) >= 0;
    }

    public static boolean inLarge(char c) {
        return largeIndex(c) >= 0;
    }

    /** An eyebrow's text: upper case (the English line under a header, "NEXUS · PHASE III"). */
    public static String eyebrow(String s) {
        return s.toUpperCase(Locale.ROOT);
    }
}
