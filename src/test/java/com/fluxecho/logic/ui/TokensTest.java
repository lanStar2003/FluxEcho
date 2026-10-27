package com.fluxecho.logic.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fluxecho.client.FluxDraw;
import com.fluxecho.logic.ui.Tok.Building;
import com.fluxecho.logic.ui.Tok.Constellation;
import com.fluxecho.logic.ui.Tok.Hue;

/**
 * The design tokens of the "clear-sky astrolabe" UI (ui/FINAL.md section 1.3, 1.4.4, 1.5.1) against the rules the
 * design states for them: every text colour reads at 4.5:1 on every paper it can land on, graphics at 3:1, the light
 * colours on the ink card, and the building light colour is the building's own colour in the world (style section
 * 3.6). The contrast figures the design printed are checked to two decimals, so a changed token shows up here.
 */
class TokensTest {

    /** The papers any text may land on: paper-0, 1, 2, the console floor and paper-3 (ui 1.4.4). */
    static final int[] PAPERS = { Tok.PAPER_0, Tok.PAPER_1, Tok.PAPER_2, Tok.PAPER_FLOOR, Tok.PAPER_3 };

    static double cr(int a, int b) {
        return ColorMath.contrast(a, b);
    }

    static void near(double expected, double actual, String what) {
        assertEquals(expected, actual, 0.0101, what);
    }

    // ------------------------------------------------------------------ 1.3.1 neutrals

    @Test
    void neutralsAreTheDesignedValues() {
        assertEquals(0xFFFFFFFF, Tok.PAPER_0);
        assertEquals(0xFFF7FAFC, Tok.PAPER_1);
        assertEquals(0xFFEEF3F7, Tok.PAPER_2);
        assertEquals(0xFFE4ECF3, Tok.PAPER_FLOOR);
        assertEquals(0xFFE2E9F0, Tok.PAPER_3);
        assertEquals(0xFFCBD6E1, Tok.LINE_1);
        assertEquals(0xFFA9B8C8, Tok.LINE_2);
        assertEquals(0xFF58697D, Tok.INK_3);
        assertEquals(0xFF4A596B, Tok.INK_2);
        assertEquals(0xFF1C2836, Tok.INK_1);
        assertEquals(0xFF0D1520, Tok.INK_0);
        assertEquals(0xFFEAF6FF, Tok.GLASS);
        assertEquals(0xFFF4FBFF, Tok.LUM_TEXT);
        assertEquals(0xFFBFE3F2, Tok.LUM_TEXT_2);
        assertEquals(0xFF86A6B8, Tok.LUM_IDLE);
        assertEquals(0xFF0E1726, Tok.NIGHT_0);
        assertEquals(0xFF16243A, Tok.NIGHT_1);
        assertEquals(0xFF0D1821, Tok.HOLO_BACKING);
    }

    /** The table of 1.3.1: contrast against paper-1 and against the console floor. */
    @Test
    void neutralContrastsAreThePrintedOnes() {
        near(5.37, cr(Tok.INK_3, Tok.PAPER_1), "ink-3 on paper-1");
        near(4.71, cr(Tok.INK_3, Tok.PAPER_FLOOR), "ink-3 on floor");
        near(6.83, cr(Tok.INK_2, Tok.PAPER_1), "ink-2 on paper-1");
        near(6.00, cr(Tok.INK_2, Tok.PAPER_FLOOR), "ink-2 on floor");
        near(14.25, cr(Tok.INK_1, Tok.PAPER_1), "ink-1 on paper-1");
        near(12.51, cr(Tok.INK_1, Tok.PAPER_FLOOR), "ink-1 on floor");
        near(17.49, cr(Tok.INK_0, Tok.PAPER_1), "ink-0 on paper-1");
        near(1.41, cr(Tok.LINE_1, Tok.PAPER_1), "line-1");
        near(1.93, cr(Tok.LINE_2, Tok.PAPER_1), "line-2");
        near(1.07, cr(Tok.PAPER_2, Tok.PAPER_1), "paper-2");
        near(1.17, cr(Tok.PAPER_3, Tok.PAPER_1), "paper-3");
        // The design prints 1.17 for paper-floor against paper-1 as well; the formula gives 1.14 (erratum in the F08
        // report). Nothing depends on it: text contrast is checked against the floor itself.
        near(1.14, cr(Tok.PAPER_FLOOR, Tok.PAPER_1), "paper-floor");
    }

    @Test
    void everyInkReadsOnEveryPaper() {
        for (int ink : new int[] { Tok.INK_0, Tok.INK_1, Tok.INK_2, Tok.INK_3 })
            for (int p : new int[] { Tok.PAPER_0, Tok.PAPER_1, Tok.PAPER_2, Tok.PAPER_FLOOR })
                assertTrue(cr(ink, p) >= 4.5, Integer.toHexString(ink) + " on " + Integer.toHexString(p));
    }

    // ------------------------------------------------------------------ 1.3.2 semantic hues

    /** {ink, base, tint, lum, ink on paper-1, ink on floor, ink on tint} per hue, from the table of 1.3.2. */
    static final Object[][] HUES = { { Hue.FLUX, 0x0A6F8F, 0x14A6CE, 0xD9F4FB, 0x4FE3FF, 5.44, 4.78, 4.96 },
        { Hue.ECHO, 0x5A3EC8, 0x7C5CFF, 0xEAE4FF, 0x8A5CFF, 6.77, 5.95, 5.76 },
        { Hue.GOLD, 0x925D0C, 0xE4951B, 0xFFECCF, 0xFFC56B, 5.28, 4.64, 4.79 },
        { Hue.OK, 0x1B7442, 0x2DB56A, 0xDDF5E6, 0x6CFF8A, 5.53, 4.85, 5.04 },
        { Hue.WARN, 0x965800, 0xF09A1E, 0xFFEDD4, 0xFFB040, 5.43, 4.76, 4.96 },
        { Hue.ERR, 0xB0261F, 0xE5484D, 0xFDE3E2, 0xFF6050, 6.37, 5.60, 5.49 } };

    @Test
    void semanticHuesAreTheTable() {
        assertEquals(7, Hue.values().length, "six hues and idle");
        for (Object[] r : HUES) {
            Hue h = (Hue) r[0];
            assertEquals(0xFF000000 | (int) r[1], h.ink, h + " ink");
            assertEquals(0xFF000000 | (int) r[2], h.base, h + " base");
            assertEquals(0xFF000000 | (int) r[3], h.tint, h + " tint");
            assertEquals(0xFF000000 | (int) r[4], h.lum, h + " lum");
            near((double) r[5], cr(h.ink, Tok.PAPER_1), h + " ink on paper-1");
            near((double) r[6], cr(h.ink, Tok.PAPER_FLOOR), h + " ink on floor");
            near((double) r[7], cr(h.ink, h.tint), h + " ink on its tint");
            for (int p : PAPERS)
                if (p != Tok.PAPER_3) assertTrue(cr(h.ink, p) >= 4.5, h + " ink on " + Integer.toHexString(p));
            assertTrue(cr(h.ink, h.tint) >= 4.5, h + " ink on its tint");
        }
        assertEquals(Tok.INK_3, Hue.IDLE.ink);
        assertEquals(Tok.LINE_2, Hue.IDLE.base);
        assertEquals(Tok.PAPER_3, Hue.IDLE.tint);
        assertEquals(Tok.LUM_IDLE, Hue.IDLE.lum);
    }

    /** "The lum column is FluxDraw's colours, not one changed" (1.3.2), except gold, which is now Evening-Star gold. */
    @Test
    void lumIsWhatTheWorldAlreadyDraws() {
        assertEquals(0xFF000000 | FluxDraw.CYAN, Hue.FLUX.lum);
        assertEquals(0xFF000000 | FluxDraw.VIOLET, Hue.ECHO.lum);
        assertEquals(0xFF000000 | FluxDraw.GREEN, Hue.OK.lum);
        assertEquals(0xFF000000 | FluxDraw.AMBER, Hue.WARN.lum);
        assertEquals(0xFF000000 | FluxDraw.RED, Hue.ERR.lum);
        assertEquals(0xFF000000 | FluxDraw.DIM, Hue.IDLE.lum);
        assertEquals(0xFFFFC56B, Hue.GOLD.lum, "gold is Evening-Star gold #FFC56B");
        assertEquals(0xFFFFC56B, Tok.EVENING_GOLD);
    }

    @Test
    void buttonsCarryWhiteText() {
        assertEquals(0xFF0C7A9C, Tok.FLUX_600);
        assertEquals(0xFF0A6F8F, Tok.FLUX_600_HOVER);
        assertEquals(0xFF085C77, Tok.FLUX_600_PRESSED);
        near(4.91, cr(Tok.PAPER_0, Tok.FLUX_600), "white on flux-600");
        near(6.68, cr(Tok.PAPER_0, Hue.ERR.ink), "white on err-ink");
        // flux-base under white text is only 2.85:1, which is why it is never a button
        near(2.85, cr(Tok.PAPER_0, Hue.FLUX.base), "white on flux-base");
        assertTrue(cr(Tok.PAPER_0, Tok.FLUX_600_HOVER) >= 4.5);
        assertTrue(cr(Tok.PAPER_0, Tok.FLUX_600_PRESSED) >= 4.5);
    }

    /**
     * The light colours on the ink card (#0D1520 at 0.94). The design printed them for the card over paper-1; gold's
     * 11.75 was taken against the opaque card instead (erratum: 10.15 on the same footing as the rest).
     */
    @Test
    void lightColoursReadOnTheInkCard() {
        assertEquals(0.94f, Tok.INK_CARD_ALPHA);
        assertEquals(0xF00D1520, Tok.INK_CARD, "ink-0 at alpha 0.94");
        assertEquals(240, Math.round(0.94f * 255));
        int card = ColorMath.over(Tok.INK_0, Tok.INK_CARD_ALPHA, Tok.PAPER_1);
        near(15.16, cr(Tok.LUM_TEXT, card), "lum-text");
        near(11.69, cr(Tok.LUM_TEXT_2, card), "lum-text-2");
        near(6.16, cr(Tok.LUM_IDLE, card), "lum-idle");
        near(10.39, cr(Hue.FLUX.lum, card), "flux");
        near(10.15, cr(Hue.GOLD.lum, card), "gold");
        near(11.75, cr(Hue.GOLD.lum, Tok.INK_0), "gold on the opaque card");
        near(12.31, cr(Hue.OK.lum, card), "ok");
        near(8.71, cr(Hue.WARN.lum, card), "warn");
        near(5.31, cr(Hue.ERR.lum, card), "err");
        near(3.82, cr(Hue.ECHO.lum, card), "echo");
        // the rules, over the lightest paper a card can float on (the worst case for a dark card)
        int worst = ColorMath.over(Tok.INK_0, Tok.INK_CARD_ALPHA, Tok.PAPER_0);
        for (Hue h : Hue.values()) assertTrue(cr(h.lum, worst) >= 3, h + " lum on the ink card");
        assertTrue(cr(Tok.LUM_TEXT, worst) >= 7, "lum-text on the ink card");
        assertTrue(cr(Tok.LUM_TEXT_2, worst) >= 7, "lum-text-2 on the ink card");
    }

    // ------------------------------------------------------------------ 1.4.4 section codes on paper

    /** code, colour, contrast on paper-0 / paper-1 / paper-2 / floor / paper-3 (floor of section 0 not printed). */
    static final Object[][] SECTION = { { "0", 0x0D1520, 18.34, 17.49, 16.41, -1.0, 14.97 },
        { "19", 0x1F4FB0, 7.49, 7.15, 6.71, 6.27, 6.12 }, { "2a", 0x1B7442, 5.80, 5.53, 5.19, 4.85, 4.73 },
        { "3b", 0x0A6F8F, 5.70, 5.44, 5.10, 4.78, 4.66 }, { "4c", 0xB0261F, 6.68, 6.37, 5.98, 5.60, 5.46 },
        { "5", 0x5A3EC8, 7.10, 6.77, 6.36, 5.95, 5.80 }, { "d", 0x9030C8, 6.10, 5.82, 5.46, 5.11, 4.98 },
        { "6", 0x925D0C, 5.54, 5.28, 4.96, 4.64, 4.52 }, { "e", 0x965800, 5.69, 5.43, 5.09, 4.76, 4.64 },
        { "78", 0x58697D, 5.63, 5.37, 5.04, 4.71, 4.60 }, { "f", 0x1C2836, 14.93, 14.25, 13.37, 12.51, 12.19 } };

    @Test
    void sectionCodesAreRemappedOnPaper() {
        Set<Character> seen = new HashSet<>();
        for (Object[] r : SECTION) {
            for (char code : ((String) r[0]).toCharArray()) {
                int c = Tok.sectionOnPaper(code);
                assertEquals(0xFF000000 | (int) r[1], c, "§" + code);
                assertEquals(c, Tok.sectionOnPaper(Character.toUpperCase(code)), "§" + code + " upper case");
                for (int i = 0; i < PAPERS.length; i++) {
                    double printed = (double) r[2 + i];
                    if (printed > 0) near(printed, cr(c, PAPERS[i]), "§" + code + " on paper " + i);
                    assertTrue(cr(c, PAPERS[i]) >= 4.5, "§" + code + " on paper " + i);
                }
                seen.add(code);
            }
        }
        assertEquals(16, seen.size(), "all sixteen colour codes");
        for (char f : "klmnor".toCharArray()) assertEquals(0, Tok.sectionOnPaper(f), "§" + f + " is not a colour");
        assertEquals(0, Tok.sectionOnPaper('x'));
        // the named tokens behind the table
        assertEquals(Tok.INK_0, Tok.sectionOnPaper('0'));
        assertEquals(Tok.BLUE_INK, Tok.sectionOnPaper('9'));
        assertEquals(Hue.OK.ink, Tok.sectionOnPaper('a'));
        assertEquals(Hue.FLUX.ink, Tok.sectionOnPaper('b'));
        assertEquals(Hue.ERR.ink, Tok.sectionOnPaper('c'));
        assertEquals(Hue.ECHO.ink, Tok.sectionOnPaper('5'));
        assertEquals(Tok.MAGENTA_INK, Tok.sectionOnPaper('d'));
        assertEquals(Hue.GOLD.ink, Tok.sectionOnPaper('6'));
        assertEquals(Hue.WARN.ink, Tok.sectionOnPaper('e'));
        assertEquals(Tok.INK_3, Tok.sectionOnPaper('7'));
        assertEquals(Tok.INK_1, Tok.sectionOnPaper('f'));
    }

    /** The vanilla colours the remap replaces are unreadable on paper, which is the reason for it (1.4.4). */
    @Test
    void vanillaColoursFailOnPaper() {
        near(1.02, cr(0xFFFFFF55, Tok.PAPER_1), "§e");
        near(1.17, cr(0xFF55FFFF, Tok.PAPER_1), "§b");
        near(1.27, cr(0xFF55FF55, Tok.PAPER_1), "§a");
        near(1.82, cr(0xFFFFAA00, Tok.PAPER_1), "§6");
        near(2.22, cr(0xFFAAAAAA, Tok.PAPER_1), "§7");
        near(1.05, cr(0xFFFFFFFF, Tok.PAPER_1), "§f");
        for (char code : "0123456789abcdef".toCharArray()) {
            int v = Tok.vanillaSection(code);
            assertEquals(0xFF, v >>> 24);
        }
        assertEquals(0xFFFFFF55, Tok.vanillaSection('e'));
        assertEquals(0xFFFFAA00, Tok.vanillaSection('6'));
    }

    // ------------------------------------------------------------------ 1.3.3 constellations

    /** lum (narrative 3.2), ink, wash, ink on the star-chart paper (-1: not printed), ink on horizon or ground. */
    static final Object[][] STARS = { { Constellation.ROUTE, 0xFFC56B, 0x925D0C, 0xFAE2BD, 4.82, 5.01 },
        { Constellation.RECALL, 0xC9D6E2, 0x4A6C8B, 0xD1DCE6, -1.0, 4.50 },
        { Constellation.KEEL, 0x4FE3FF, 0x0B778C, 0xBDF0FA, 4.53, 4.71 },
        { Constellation.COMPASS, 0xFF8CE6, 0xC810A0, 0xFABDED, 4.53, 4.70 },
        { Constellation.HULL, 0x7DB8FF, 0x116AD6, 0xBDD8FA, 4.50, 4.68 },
        { Constellation.FURNACE, 0xFF9F5A, 0xB0520E, 0xFAD6BD, 4.50, 4.68 },
        { Constellation.MEMORY, 0x8A5CFF, 0x794AF0, 0xCEBDFA, 4.51, 4.69 },
        { Constellation.OUTLAND, 0x5FD3A8, 0x227B5A, 0xC7F0E1, 4.51, 4.69 },
        { Constellation.HOLD, 0xD8F4F0, 0x40766F, 0xC7EFEA, 4.52, 4.70 } };

    @Test
    void constellationsAreTheTable() {
        assertEquals(9, Constellation.values().length);
        assertEquals(0xFFE9F0F7, Tok.STAR_PAPER);
        assertEquals(0xFFF7F3EC, Tok.HORIZON);
        for (Object[] r : STARS) {
            Constellation s = (Constellation) r[0];
            assertEquals(0xFF000000 | (int) r[1], s.lum, s + " lum");
            assertEquals(0xFF000000 | (int) r[2], s.ink, s + " ink");
            assertEquals(0xFF000000 | (int) r[3], s.wash, s + " wash");
            double onChart = (double) r[4];
            if (s == Constellation.RECALL) {
                // the recall stars live below the horizon, on the ground band
                near((double) r[5], cr(s.ink, Tok.PAPER_3), s + " ink on the ground");
                assertTrue(cr(s.ink, Tok.PAPER_3) >= 4.5, s + " on the ground");
            } else {
                near(onChart, cr(s.ink, Tok.STAR_PAPER), s + " ink on the chart");
                near((double) r[5], cr(s.ink, Tok.HORIZON), s + " ink on the horizon");
                assertTrue(cr(s.ink, Tok.STAR_PAPER) >= 4.5, s + " on the chart");
            }
        }
        assertEquals(Hue.GOLD.lum, Constellation.ROUTE.lum, "the route is Evening-Star gold");
        assertEquals(Hue.GOLD.ink, Constellation.ROUTE.ink);
    }

    /** "Nine inks pairwise ΔE00 ≥ 8"; the closest pairs as printed in 1.3.3. */
    @Test
    void constellationInksStayApart() {
        Constellation[] all = Constellation.values();
        for (int i = 0; i < all.length; i++) for (int j = i + 1; j < all.length; j++) {
            double d = ColorMath.deltaE00(all[i].ink, all[j].ink);
            assertTrue(d >= 8, all[i] + " and " + all[j] + " are " + d + " apart");
        }
        assertEquals(8.0, ColorMath.deltaE00(Constellation.RECALL.ink, Constellation.HULL.ink), 0.05);
        assertEquals(10.1, ColorMath.deltaE00(Constellation.OUTLAND.ink, Constellation.HOLD.ink), 0.05);
        assertEquals(11.1, ColorMath.deltaE00(Constellation.ROUTE.ink, Constellation.FURNACE.ink), 0.05);
    }

    // ------------------------------------------------------------------ 1.3.4 buildings

    /** Style section 3.6, the building's colour in the world (2026-10-15 values): what lum must equal. */
    static final Map<String, Integer> STYLE_3_6 = new LinkedHashMap<>();

    static {
        STYLE_3_6.put("drift_capsule", 0x4FE3FF); // the capsule is the phase-0 nexus (ui 1.3.4)
        STYLE_3_6.put("nexus", 0x4FE3FF);
        STYLE_3_6.put("library", 0xFFD9A0);
        STYLE_3_6.put("eco_dome", 0x7BE08A);
        STYLE_3_6.put("mana_fountain", 0x6AA8FF);
        STYLE_3_6.put("arcane_court", 0xB07CFF);
        STYLE_3_6.put("blood_covenant", 0xE0344A);
        STYLE_3_6.put("hunting_ground", 0xFF9A3C);
        STYLE_3_6.put("resonance_gate", 0x5CF2C2);
        STYLE_3_6.put("dual_foil_vault", 0xF2EBDD);
        STYLE_3_6.put("dome_generator", 0x7DB8FF);
        STYLE_3_6.put("root/well", 0x2E9FB8);
        STYLE_3_6.put("root/root", 0x5FD3A8);
        STYLE_3_6.put("root/core", 0xFF7A3C);
        STYLE_3_6.put("sophon_array", 0xFF8CE6);
        STYLE_3_6.put("holo_table", 0xA8E0FF);
        STYLE_3_6.put("sky_sails", 0xFFD27A);
        STYLE_3_6.put("observatory", 0xB9C8FF);
        STYLE_3_6.put("droplet_hangar", 0xD8E4F0);
        STYLE_3_6.put("weather_station", 0xFF9E8A);
        STYLE_3_6.put("orbital_anchor", 0xFFFFFF);
        STYLE_3_6.put("source_heart", 0xF6E7FF);
        STYLE_3_6.put("leap_ring", 0xC9B8FF);
    }

    /** key/form, line, ink, tint, line on paper-1 / floor, ink on paper-1 / floor, ink on tint (ui 1.3.4). */
    static final Object[][] BUILDINGS = {
        { "drift_capsule", 0x0E94AD, 0x0B7489, 0xE5F7FA, 3.42, 3.00, 5.17, 4.54, 4.91 },
        { "nexus", 0x0E94AD, 0x0B7489, 0xE5F7FA, 3.42, 3.00, 5.17, 4.54, 4.91 },
        { "library", 0xBA783E, 0x925E31, 0xF7EFE8, 3.43, 3.01, 5.18, 4.55, 4.77 },
        { "eco_dome", 0x249B36, 0x1D7A2B, 0xE6F9E9, 3.44, 3.02, 5.18, 4.54, 4.93 },
        { "mana_fountain", 0x3C86EF, 0x1265DA, 0xE5EEFA, 3.43, 3.01, 5.14, 4.51, 4.60 },
        { "arcane_court", 0xA06AF3, 0x843DEF, 0xEDE5FA, 3.42, 3.01, 5.14, 4.52, 4.41 },
        { "blood_covenant", 0xE0344A, 0xCF1F36, 0xFAE5E8, 4.21, 3.70, 5.13, 4.51, 4.47 },
        { "hunting_ground", 0xCF6D11, 0xA4550D, 0xFAEFE5, 3.43, 3.01, 5.16, 4.53, 4.77 },
        { "resonance_gate", 0x0D9A6D, 0x0A7956, 0xE5FAF4, 3.42, 3.00, 5.16, 4.53, 4.97 },
        { "dual_foil_vault", 0xA4823F, 0x826631, 0xF7F2E9, 3.43, 3.01, 5.14, 4.52, 4.84 },
        { "dome_generator", 0x3287EE, 0x1167D1, 0xE5EFFA, 3.43, 3.01, 5.16, 4.53, 4.65 },
        { "root/well", 0x2B93AA, 0x217486, 0xE7F6F9, 3.42, 3.00, 5.13, 4.50, 4.85 },
        { "root/root", 0x2A986F, 0x217858, 0xE7F8F2, 3.44, 3.02, 5.15, 4.52, 4.91 },
        { "root/core", 0xE95713, 0xB8450F, 0xFAECE5, 3.43, 3.01, 5.14, 4.51, 4.67 },
        { "sophon_array", 0xEE2FC5, 0xC4109D, 0xFAE5F6, 3.43, 3.01, 5.13, 4.50, 4.50 },
        { "holo_table", 0x118ED4, 0x0E70A7, 0xE5F3FA, 3.43, 3.01, 5.14, 4.52, 4.76 },
        { "sky_sails", 0xB57C0F, 0x8E620C, 0xFAF3E5, 3.42, 3.00, 5.13, 4.51, 4.87 },
        { "observatory", 0x607FF2, 0x335CEE, 0xE5EAFA, 3.45, 3.03, 5.13, 4.50, 4.48 },
        { "droplet_hangar", 0x588BBF, 0x3D6D9E, 0xE9F0F6, 3.42, 3.00, 5.16, 4.53, 4.71 },
        { "weather_station", 0xEE5131, 0xC83010, 0xFAE9E5, 3.42, 3.00, 5.16, 4.53, 4.59 },
        { "orbital_anchor", 0x878787, 0x5A6B80, 0xF0F3F7, 3.43, 3.01, 5.20, 4.57, 4.90 },
        { "source_heart", 0xB95BF2, 0xA01EED, 0xF2E5FA, 3.43, 3.01, 5.13, 4.51, 4.45 },
        { "leap_ring", 0x9071F3, 0x7148F0, 0xE9E3FD, 3.43, 3.01, 5.15, 4.53, 4.34 } };

    static String id(Building b) {
        return b.form == null ? b.key : b.key + "/" + b.form;
    }

    @Test
    void twentyThreeBuildingRowsInTheTablesOrder() {
        List<Building> all = Tok.buildings();
        assertEquals(23, all.size());
        for (int i = 0; i < BUILDINGS.length; i++) assertEquals(BUILDINGS[i][0], id(all.get(i)), "row " + i);
        Set<String> ids = new HashSet<>();
        for (Building b : all) assertTrue(ids.add(id(b)), "two rows for " + id(b));
    }

    /** ui 1.3.4: "lum = the building's own colour in style 3.6", one source of truth for the world and the UI. */
    @Test
    void buildingLumIsItsColourInTheWorld() {
        for (Building b : Tok.buildings()) {
            Integer world = STYLE_3_6.get(id(b));
            assertNotNull(world, id(b));
            assertEquals(0xFF000000 | world, b.lum, id(b) + " lum");
        }
    }

    @Test
    void buildingInksFollowTheirRules() {
        List<Building> all = Tok.buildings();
        for (int i = 0; i < BUILDINGS.length; i++) {
            Object[] r = BUILDINGS[i];
            Building b = all.get(i);
            String w = id(b);
            assertEquals(0xFF000000 | (int) r[1], b.line, w + " line");
            assertEquals(0xFF000000 | (int) r[2], b.ink, w + " ink");
            assertEquals(0xFF000000 | (int) r[3], b.tint, w + " tint");
            near((double) r[4], cr(b.line, Tok.PAPER_1), w + " line on paper-1");
            near((double) r[5], cr(b.line, Tok.PAPER_FLOOR), w + " line on floor");
            near((double) r[6], cr(b.ink, Tok.PAPER_1), w + " ink on paper-1");
            near((double) r[7], cr(b.ink, Tok.PAPER_FLOOR), w + " ink on floor");
            near((double) r[8], cr(b.ink, b.tint), w + " ink on its tint");
            assertTrue(cr(b.line, Tok.PAPER_FLOOR) >= 3.0, w + " line ≥ 3:1");
            assertTrue(cr(b.ink, Tok.PAPER_FLOOR) >= 4.5, w + " ink ≥ 4.5:1");
            assertTrue(cr(b.ink, b.tint) >= 4.3, w + " ink on its tint ≥ 4.3:1");
        }
    }

    /** The near colours 1.3.4 lists (ΔE00 < 5 between inks); the UI tells these apart by sigil and number. */
    @Test
    void nearInksAreTheKnownOnes() {
        assertEquals(1.2, ColorMath.deltaE00(ink("root/well"), ink("nexus")), 0.05);
        assertEquals(1.6, ColorMath.deltaE00(ink("mana_fountain"), ink("dome_generator")), 0.05);
        assertEquals(1.4, ColorMath.deltaE00(ink("resonance_gate"), ink("root/root")), 0.05);
        assertEquals(3.3, ColorMath.deltaE00(ink("arcane_court"), ink("leap_ring")), 0.05);
        assertEquals(4.7, ColorMath.deltaE00(ink("holo_table"), ink("droplet_hangar")), 0.05);
        assertEquals(4.9, ColorMath.deltaE00(ink("arcane_court"), ink("source_heart")), 0.05);
        Set<String> known = new HashSet<>();
        for (String p : new String[] { "root/well nexus", "root/well drift_capsule", "drift_capsule nexus",
            "mana_fountain dome_generator", "resonance_gate root/root", "arcane_court leap_ring",
            "holo_table droplet_hangar", "arcane_court source_heart" }) known.add(p);
        List<Building> all = Tok.buildings();
        for (int i = 0; i < all.size(); i++) for (int j = i + 1; j < all.size(); j++) {
            double d = ColorMath.deltaE00(all.get(i).ink, all.get(j).ink);
            if (d < 5) assertTrue(
                known.contains(id(all.get(i)) + " " + id(all.get(j)))
                    || known.contains(id(all.get(j)) + " " + id(all.get(i))),
                "new near pair " + id(all.get(i)) + " / " + id(all.get(j)) + " at " + d);
        }
    }

    static int ink(String id) {
        for (Building b : Tok.buildings()) if (id(b).equals(id)) return b.ink;
        throw new AssertionError(id);
    }

    /** Lookup by key, by key and form, and by key and shape name (the root's three shapes are three rows). */
    @Test
    void buildingsAreFoundByKeyAndShape() {
        assertSame(
            Tok.buildings()
                .get(1),
            Tok.building("nexus"));
        assertEquals(0xFF2E9FB8, Tok.building("root").lum, "the root's first form is the tidal well");
        assertEquals(0xFF2E9FB8, Tok.building("root", "well").lum);
        assertEquals(0xFF2E9FB8, Tok.building("root", "well_v2_14").lum);
        assertEquals(0xFF5FD3A8, Tok.building("root", "root_in_20").lum);
        assertEquals(0xFF5FD3A8, Tok.building("root", "root_v2_7").lum);
        assertEquals(0xFFFF7A3C, Tok.building("root", "core").lum);
        assertEquals(0xFFFF7A3C, Tok.building("root", "core_in_w8").lum);
        assertEquals(0xFFFFFFFF, Tok.building("orbital_anchor", "source_crown").lum, "the crown shares the anchor");
        assertEquals(Tok.building("library").ink, Tok.building("library", "lantern").ink, "shape ignored elsewhere");
        assertEquals(null, Tok.building("no_such_building"));
        for (Building b : Tok.buildings()) assertTrue(b.sigil >= 0 && b.sigil < AtlasMap.COUNT, id(b) + " sigil");
    }

    // ------------------------------------------------------------------ 1.5.1 sizes

    @Test
    void sizesAreOnTheFourPixelGrid() {
        assertEquals(4, Tok.U);
        for (int s : Tok.SPACING) assertTrue(s == 2 || s % 4 == 0 || s == 6, "spacing " + s);
        assertEquals(Arrays.asList(2, 4, 6, 8, 12, 16, 24), Tok.spacing());
        assertEquals(6, Tok.CUT_WINDOW);
        assertEquals(4, Tok.CUT_CARD);
        assertEquals(3, Tok.CUT_BUTTON);
        assertEquals(2, Tok.CUT_CHIP);
        assertEquals(1, Tok.BEVEL);
        assertEquals(1f, Tok.hairline(1));
        assertEquals(0.5f, Tok.hairline(2));
        assertEquals(0.5f, Tok.hairline(4));
        assertEquals(18, Tok.SLOT);
        assertEquals(16, Tok.ITEM);
        assertEquals(12, Tok.HIT_MIN);
        assertEquals(8, Tok.TICK_EVERY);
        assertEquals(2, Tok.TICK_HEIGHT);
        assertEquals(8, Tok.SHADOW_BLUR);
        assertEquals(2, Tok.SHADOW_DY);
        assertEquals(0x290D1520, Tok.SHADOW, "ink-0 at alpha 0.16");
        assertEquals(41, Math.round(0.16f * 255));
        assertEquals(300, Tok.Z_OVERLAY);
        assertEquals(100, Tok.Z_ITEM);
        assertEquals(200, Tok.Z_COUNT);
    }
}
