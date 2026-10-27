package com.fluxecho.logic.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Number, unit and time formats (ui/FINAL.md section 1.9) and the FluxMono rules (section 1.4.2, 2.2, 2.4): SI with
 * three significant digits, whole percentages below 10% get one decimal, countdowns as m:ss or h:mm:ss, game time with
 * midnight at tick 18000, slot counts that fit an 18-pixel slot, glyph advance = height x 0.6 rounded.
 */
class FmtTest {

    // ------------------------------------------------------------------ SI, three significant digits

    @Test
    void siUsesThreeSignificantDigits() {
        // the examples of 1.9
        assertEquals("512", Fmt.si(512));
        assertEquals("2.05k", Fmt.si(2048));
        assertEquals("30.7k", Fmt.si(30720));
        assertEquals("1.97M", Fmt.si(1966080));
        assertEquals("2.10G", Fmt.si(2_100_000_000L));
        // the edges
        assertEquals("0", Fmt.si(0));
        assertEquals("999", Fmt.si(999));
        assertEquals("1.00k", Fmt.si(1000));
        assertEquals("1.00k", Fmt.si(1004));
        assertEquals("1.01k", Fmt.si(1005));
        assertEquals("999k", Fmt.si(999_499));
        assertEquals("1.00M", Fmt.si(999_500), "rounding up carries into the next prefix");
        assertEquals("100k", Fmt.si(99_950));
        assertEquals("99.9k", Fmt.si(99_949));
        assertEquals("1.00T", Fmt.si(1_000_000_000_000L));
        assertEquals("999T", Fmt.si(999_000_000_000_000L));
        assertEquals("1500T", Fmt.si(1_500_000_000_000_000L), "past T the number just grows");
        assertEquals("9223372T", Fmt.si(Long.MAX_VALUE));
        assertEquals("-30.7k", Fmt.si(-30720));
        assertEquals("-512", Fmt.si(-512));
        assertEquals("-9223372T", Fmt.si(Long.MIN_VALUE));
    }

    @Test
    void siForFractions() {
        assertEquals("12.5", Fmt.si(12.5));
        assertEquals("1.50", Fmt.si(1.5));
        assertEquals("0.125", Fmt.si(0.125));
        assertEquals("128", Fmt.si(128.0));
        assertEquals("2.05k", Fmt.si(2048.0));
        assertEquals("1.00k", Fmt.si(999.6));
        assertEquals("0", Fmt.si(0.0));
        assertEquals("-1.25", Fmt.si(-1.25));
        assertEquals("0", Fmt.si(Double.NaN));
    }

    @Test
    void valuesKeepTheUnitApart() {
        Fmt.Value v = Fmt.value(384, Fmt.EU_T);
        assertEquals("384", v.number);
        assertEquals("EU/t", v.unit);
        assertEquals("FC/s", Fmt.FC_S);
        assertEquals("FC", Fmt.FC);
        assertEquals("EU", Fmt.EU);
        assertEquals("2.05k", Fmt.value(2048, Fmt.EU).number);
        assertEquals(2, Fmt.UNIT_GAP, "number and unit sit 2 px apart (1.4.3 rule 2)");
    }

    // ------------------------------------------------------------------ percentages

    @Test
    void percentagesAreWholeAboveTen() {
        assertEquals("0%", Fmt.percent(0));
        assertEquals("7.3%", Fmt.percent(0.073));
        assertEquals("9.9%", Fmt.percent(0.0999));
        assertEquals("10%", Fmt.percent(0.1));
        assertEquals("50%", Fmt.percent(0.5));
        assertEquals("99%", Fmt.percent(0.9999), "a bar only says 100% when it is done");
        assertEquals("100%", Fmt.percent(1));
        assertEquals("100%", Fmt.percent(1.7), "clamped");
        assertEquals("0%", Fmt.percent(-0.2), "clamped");
        assertEquals("0.5%", Fmt.percent(0.005));
        assertEquals("0%", Fmt.percent(Double.NaN));
    }

    // ------------------------------------------------------------------ time

    @Test
    void countdownsAreClockFaces() {
        assertEquals("14:20", Fmt.clock(14 * 60 + 20));
        assertEquals("1:57:03", Fmt.clock(3600 + 57 * 60 + 3));
        assertEquals("0:05", Fmt.clock(5));
        assertEquals("0:00", Fmt.clock(0));
        assertEquals("0:00", Fmt.clock(-3));
        assertEquals("59:59", Fmt.clock(3599));
        assertEquals("1:00:00", Fmt.clock(3600));
        assertEquals("100:00:00", Fmt.clock(360000));
        assertEquals("1:00", Fmt.clockTicks(1200));
        assertEquals("0:00", Fmt.clockTicks(19), "ticks round down to the second");
    }

    /** "D3 · 23:40", midnight = tick 18000 (1.9): the day number turns over at midnight, not at tick 0. */
    @Test
    void gameTimeCountsFromMidnight() {
        assertEquals("D1 · 06:00", Fmt.gameTime(0));
        assertEquals("D1 · 12:00", Fmt.gameTime(6000));
        assertEquals("D1 · 18:00", Fmt.gameTime(12000));
        assertEquals("D1 · 23:59", Fmt.gameTime(17999));
        assertEquals("D2 · 00:00", Fmt.gameTime(18000));
        assertEquals("D3 · 23:40", Fmt.gameTime(2 * 24000 + 17667));
        assertEquals("D1 · 06:00", Fmt.gameTime(-5), "no negative days");
    }

    // ------------------------------------------------------------------ slot counts, ordinals, roman

    /** A count in an 18-pixel slot (C27, C31): at most four FluxMono-6 glyphs, so 4 x 4 = 16 px. */
    @Test
    void countsFitASlot() {
        assertEquals("7", Fmt.count(7));
        assertEquals("999", Fmt.count(999));
        assertEquals("1.2k", Fmt.count(1234));
        assertEquals("9.9k", Fmt.count(9999));
        assertEquals("10k", Fmt.count(10_000));
        assertEquals("999k", Fmt.count(999_999));
        assertEquals("1.0M", Fmt.count(1_000_000));
        assertEquals("1.2M", Fmt.count(1_250_000));
        assertEquals("999M", Fmt.count(999_999_999));
        assertEquals("1.0G", Fmt.count(1_000_000_000));
        assertEquals("0", Fmt.count(-4), "no negative stacks");
        long[] probes = { 0, 9, 10, 99, 100, 1000, 9999, 10000, 99999, 100000, 999999, 1000000, 12345678, 999999999,
            1000000000L, 123456789012L, 999999999999999L, Long.MAX_VALUE };
        for (long n : probes) {
            String s = Fmt.count(n);
            assertTrue(s.length() <= 4, n + " -> " + s);
            assertTrue(FluxMono.width(s, 6, 0) <= Tok.SLOT - 2, n + " fits the slot");
        }
    }

    @Test
    void ordinalsAndRomanNumerals() {
        assertEquals("07", Fmt.order(7));
        assertEquals("24", Fmt.order(24));
        assertEquals("I", Fmt.roman(1));
        assertEquals("III", Fmt.roman(3));
        assertEquals("IV", Fmt.roman(4));
        assertEquals("V", Fmt.roman(5));
        assertEquals("IX", Fmt.roman(9));
        assertEquals("X", Fmt.roman(10));
        assertEquals("XXXIX", Fmt.roman(39));
        assertEquals("0", Fmt.roman(0), "no roman zero");
    }

    // ------------------------------------------------------------------ FluxMono

    /** "Advance = height x 0.6 rounded (6 -> 4, 8 -> 5, 12 -> 7, 16 -> 10, 24 -> 14), monospaced" (2.4). */
    @Test
    void monoAdvances() {
        int[][] table = { { 6, 4 }, { 8, 5 }, { 12, 7 }, { 16, 10 }, { 24, 14 } };
        assertEquals(5, FluxMono.SIZES.length);
        for (int i = 0; i < table.length; i++) {
            assertEquals(table[i][0], FluxMono.SIZES[i]);
            assertEquals(table[i][1], FluxMono.advance(table[i][0]), "size " + table[i][0]);
        }
        assertFalse(FluxMono.large(6));
        assertFalse(FluxMono.large(8));
        assertTrue(FluxMono.large(12));
        assertTrue(FluxMono.large(16));
        assertTrue(FluxMono.large(24));
        assertEquals(5 * 4, FluxMono.width("384k", 8, 0));
        assertEquals(4 * 4 + 3, FluxMono.width("EU/T", 6, 1), "eyebrow tracking +1 between glyphs");
        assertEquals(0, FluxMono.width("", 8, 1));
        // the drawn glyph sits inside its advance
        for (int s : FluxMono.SIZES) assertTrue(FluxMono.glyphWidth(s) <= FluxMono.advance(s), "size " + s);
    }

    @Test
    void monoGlyphSets() {
        assertEquals(95 + 13, FluxMono.SMALL.length());
        for (char c = 0x20; c < 0x7F; c++) assertTrue(FluxMono.inSmall(c), "ascii " + c);
        for (char c : "·×±°←→↑↓◆▶✦▤⚠".toCharArray()) assertTrue(FluxMono.inSmall(c), "symbol " + c);
        assertEquals(23, FluxMono.LARGE.length());
        for (char c : "0123456789.,:%+−kMGTIVX".toCharArray()) assertTrue(FluxMono.inLarge(c), "large " + c);
        assertTrue(FluxMono.inLarge('-'), "the hyphen draws as the minus");
        assertFalse(FluxMono.inLarge('E'));
        assertFalse(FluxMono.inSmall('中'));
        assertEquals("NEXUS · PHASE III", FluxMono.eyebrow("Nexus · Phase iii"));
    }

    /** Every number the formats make for the big sizes (N1, D) is drawn from the large glyphs, so it stays crisp. */
    @Test
    void numbersUseOnlyLargeGlyphs() {
        long[] probes = { 0, 7, 512, 2048, 30720, 1966080, 2_100_000_000L, -30720, 999_500, 1_500_000_000_000_000L };
        for (long n : probes) large(Fmt.si(n));
        for (double f : new double[] { 0, 0.073, 0.5, 1 }) large(Fmt.percent(f));
        for (long s : new long[] { 0, 59, 860, 7023 }) large(Fmt.clock(s));
        for (int r = 1; r <= 10; r++) large(Fmt.roman(r));
        for (int o = 1; o <= 24; o++) large(Fmt.order(o));
        large(Fmt.count(1234));
    }

    static void large(String s) {
        for (char c : s.toCharArray()) assertTrue(FluxMono.inLarge(c), "'" + c + "' of " + s);
    }
}
