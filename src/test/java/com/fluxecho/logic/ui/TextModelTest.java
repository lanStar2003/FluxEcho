package com.fluxecho.logic.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.ui.TextModel.Run;
import com.fluxecho.logic.ui.TextModel.Style;
import com.fluxecho.logic.ui.TextModel.Surface;

/**
 * The text width model shared by layout and the layout tests (ui/FINAL.md section 1.4.3, 1.4.4, 2.4): ascii.png
 * advances, 9 px for CJK, section codes are 0 wide and bold adds 1 per glyph; one fit / wrap with an ellipsis; section
 * colour codes are remapped on paper and left alone on the ink card.
 */
class TextModelTest {

    /** The widths ResearchTreeTest pinned to Minecraft's font. */
    @Test
    void widthsAreMinecrafts() {
        assertEquals(68, TextModel.width("Arcane Court"));
        assertEquals(104, TextModel.width("Echo Hunting Ground"));
        assertEquals(106, TextModel.width("Blood Covenant Altar"));
        assertEquals(27, TextModel.width("秘法庭"));
        assertEquals(4, TextModel.advance(' '));
        assertEquals(2, TextModel.advance('i'));
        assertEquals(9, TextModel.advance('中'));
        assertEquals(0, TextModel.width(""));
        assertEquals(0, TextModel.width(null));
    }

    @Test
    void sectionCodesHaveNoWidth() {
        assertEquals(27, TextModel.width("§e秘法庭"));
        assertEquals(27, TextModel.width("秘§b法§r庭"));
        assertEquals(14, TextModel.width("§lAB"), "bold adds 1 per glyph");
        assertEquals(13, TextModel.width("§lA§rB"));
        assertEquals(6, TextModel.width("§l§eA"), "a colour code ends bold, as in FontRenderer");
        assertEquals(7, TextModel.width("§e§lA"));
        assertEquals(6, TextModel.width("A§"), "a dangling section sign is dropped");
        assertEquals("秘法庭", TextModel.strip("§e秘§l法§r庭"));
    }

    @Test
    void stylesAreOneTwoAndBold() {
        assertEquals(1f, Style.B.scale);
        assertEquals(1f, Style.H2.scale);
        assertEquals(2f, Style.H1.scale);
        assertEquals(8, Style.B.height);
        assertEquals(11, Style.B.lineHeight);
        assertEquals(11, Style.H2.lineHeight);
        assertEquals(16, Style.H1.height);
        assertEquals(20, Style.H1.lineHeight);
        assertTrue(Style.H2.bold);
        assertFalse(Style.B.bold);
        assertEquals(68, TextModel.width("Arcane Court", Style.B));
        assertEquals(69, TextModel.width("Arcane Court", Style.H2), "the second pass sits half a pixel right");
        assertEquals(136, TextModel.width("Arcane Court", Style.H1));
    }

    @Test
    void fitCutsWithAnEllipsis() {
        assertEquals("秘法庭", TextModel.fit("秘法庭", 27));
        assertEquals("秘法…", TextModel.fit("秘法庭秘法庭", 30));
        assertEquals("§e秘法…", TextModel.fit("§e秘法庭秘法庭", 30), "codes before the cut stay");
        assertEquals("…", TextModel.fit("秘法庭", 10));
        assertEquals("", TextModel.fit("秘法庭", 5), "not even the ellipsis fits");
        for (int w = 0; w < 120; w++) assertTrue(TextModel.width(TextModel.fit("Echo Hunting Ground", w)) <= w);
    }

    @Test
    void wrapBreaksAtSpacesAndBetweenHanzi() {
        assertEquals(Arrays.asList("Echo", "Hunting", "Ground"), TextModel.wrap("Echo Hunting Ground", 60, 9));
        assertEquals(Arrays.asList("Echo Hunting", "Ground"), TextModel.wrap("Echo Hunting Ground", 64, 9));
        assertEquals(Arrays.asList("一二三", "四五六"), TextModel.wrap("一二三四五六", 30, 9));
        assertEquals(Arrays.asList("a", "b"), TextModel.wrap("a\nb", 100, 9), "newlines break");
        assertEquals(
            Arrays.asList("Supercalifragilis", "tic"),
            TextModel.wrap("Supercalifragilistic", 84, 9),
            "a word longer than the line is cut");
        // the last line gets the ellipsis when there is more
        List<String> two = TextModel.wrap("Echo Hunting Ground", 60, 2);
        assertEquals(2, two.size());
        assertEquals("Echo", two.get(0));
        assertTrue(
            two.get(1)
                .endsWith("…"),
            two.get(1));
        assertTrue(TextModel.width(two.get(1)) <= 60);
        // colours carry over a break, as vanilla's wrapping does
        assertEquals(Arrays.asList("§e一二三", "§e四五六"), TextModel.wrap("§e一二三四五六", 30, 9));
        assertEquals(Arrays.asList(), TextModel.wrap("", 30, 9));
    }

    /** A line is at most 34 hanzi (306 px) (1.4.3 rule 7). */
    @Test
    void lineMeasure() {
        assertEquals(306, TextModel.MAX_LINE);
        assertEquals(34 * 9, TextModel.MAX_LINE);
    }

    /** Paper remaps colour codes to inks, keeps bold as a second pass, hands the other formats to the font. */
    @Test
    void runsOnPaper() {
        List<Run> r = TextModel.runs("a§eb§lc§rd§oe", Surface.PAPER, Tok.INK_1);
        assertEquals(5, r.size());
        run(r.get(0), "a", Tok.INK_1, false, "");
        run(r.get(1), "b", Tok.Hue.WARN.ink, false, "");
        run(r.get(2), "c", Tok.Hue.WARN.ink, true, "");
        run(r.get(3), "d", Tok.INK_1, false, "");
        run(r.get(4), "e", Tok.INK_1, false, "§o");
        List<Run> s = TextModel.runs("§o§ex§k§ny", Surface.PAPER, Tok.INK_2);
        assertEquals(2, s.size());
        run(s.get(0), "x", Tok.Hue.WARN.ink, false, "", "a colour code clears the formats");
        run(s.get(1), "y", Tok.Hue.WARN.ink, false, "§k§n");
        assertEquals(
            1,
            TextModel.runs("plain", Surface.PAPER, Tok.INK_1)
                .size());
        assertEquals(
            0,
            TextModel.runs("", Surface.PAPER, Tok.INK_1)
                .size());
    }

    @Test
    void runsOnInkAreUntouched() {
        for (Surface dark : new Surface[] { Surface.INK, Surface.LUM }) {
            List<Run> r = TextModel.runs("a§eb§lc", dark, Tok.LUM_TEXT);
            assertEquals(1, r.size());
            run(r.get(0), "a§eb§lc", Tok.LUM_TEXT, false, "");
        }
    }

    static void run(Run r, String text, int color, boolean bold, String formats) {
        run(r, text, color, bold, formats, text);
    }

    static void run(Run r, String text, int color, boolean bold, String formats, String what) {
        assertEquals(text, r.text, what);
        assertEquals(Integer.toHexString(color), Integer.toHexString(r.color), what);
        assertEquals(bold, r.bold, what);
        assertEquals(formats, r.formats, what);
    }
}
