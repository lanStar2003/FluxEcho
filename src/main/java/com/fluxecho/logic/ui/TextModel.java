package com.fluxecho.logic.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * The text model of the new UI (ui/FINAL.md section 1.4, 2.4): how wide Minecraft's font draws a string, how to cut a
 * string to a width with an ellipsis or wrap it into lines, and how a string's section codes become coloured runs.
 * Layout and the layout tests use this one model, so a page that fits in a test fits in the game (it replaces the three
 * copies in the old nexus, city and archive screens).
 * <p>
 * Widths are Minecraft 1.7.10's: ascii.png's advances for ASCII (most letters 6, a space 4), 9 for anything else (a
 * CJK glyph in the unicode font; other symbols are narrower, so the model errs wide). Section codes are 0 wide; bold
 * adds 1 per glyph, and a colour code ends bold, as in {@code FontRenderer}. Chinese is only ever drawn at 1x (8 px) or
 * 2x (16 px), the three styles here; smaller text is FluxMono.
 * <p>
 * On paper the colour codes are replaced by the inks of {@link Tok#sectionOnPaper} (vanilla's colours are made for a
 * dark ground); bold becomes a second pass half a pixel to the right; the other formats go to the font as they are. On
 * the ink card, the HUD and in the world the string goes to the font untouched.
 */
public final class TextModel {

    private TextModel() {}

    /** The longest line, 34 hanzi; longer text goes into columns (1.4.3 rule 7). */
    public static final int MAX_LINE = 306;
    /** The ellipsis that ends a cut string. */
    public static final String ELLIPSIS = "…";

    /** What a string is drawn on: paper remaps colour codes, the dark surfaces keep them. */
    public enum Surface {
        PAPER,
        INK,
        LUM
    }

    /** The three text styles: body (1x), bold card titles and buttons (1x, two passes), page titles (2x). */
    public enum Style {

        B(1, 8, 11, false),
        H2(1, 8, 11, true),
        H1(2, 16, 20, false);

        public final float scale;
        public final int height, lineHeight;
        public final boolean bold;

        Style(float scale, int height, int lineHeight, boolean bold) {
            this.scale = scale;
            this.height = height;
            this.lineHeight = lineHeight;
            this.bold = bold;
        }
    }

    // ------------------------------------------------------------------ widths

    /** The advance of one glyph at 1x. */
    public static int advance(char c) {
        if (c >= 128) return 9;
        if ("!,.:;i|".indexOf(c) >= 0) return 2;
        if ("'`l".indexOf(c) >= 0) return 3;
        if (" I[]t".indexOf(c) >= 0) return 4;
        if ("\"()*<>fk{}".indexOf(c) >= 0) return 5;
        if ("@~".indexOf(c) >= 0) return 7;
        return 6;
    }

    /** Whether {@code c} after a section sign is a colour code (it also ends bold and the other formats). */
    static boolean colourCode(char c) {
        return Tok.SECTION_CODES.indexOf(Character.toLowerCase(c)) >= 0;
    }

    /** The width at 1x: section codes are 0 wide, bold adds 1 per glyph. */
    public static int width(String s) {
        if (s == null) return 0;
        int w = 0;
        boolean bold = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§') {
                if (i + 1 < s.length()) {
                    char code = Character.toLowerCase(s.charAt(++i));
                    if (code == 'l') bold = true;
                    else if (code == 'r' || colourCode(code)) bold = false;
                }
                continue;
            }
            w += advance(c);
            if (bold) w++;
        }
        return w;
    }

    /** The width in a style: 2x doubles it, the bold style's second pass adds a pixel. */
    public static int width(String s, Style style) {
        int w = Math.round(width(s) * style.scale);
        return style.bold && w > 0 ? w + 1 : w;
    }

    /** The string without its section codes. */
    public static String strip(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§') i++;
            else b.append(c);
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ fit and wrap

    /**
     * The string cut to {@code max} pixels at 1x: whole if it fits, otherwise as many glyphs as leave room for the
     * ellipsis, then the ellipsis; the section codes before the cut stay. Empty when not even the ellipsis fits.
     */
    public static String fit(String s, int max) {
        if (s == null) return "";
        if (width(s) <= max) return s;
        int room = max - width(ELLIPSIS);
        if (room < 0) return "";
        int end = cut(s, 0, room);
        return s.substring(0, end) + ELLIPSIS;
    }

    /** The end index of the longest prefix of {@code s} from {@code start} no wider than {@code room} at 1x. */
    private static int cut(String s, int start, int room) {
        int w = 0, end = start;
        boolean bold = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§') {
                if (i + 1 < s.length()) {
                    char code = Character.toLowerCase(s.charAt(i + 1));
                    if (code == 'l') bold = true;
                    else if (code == 'r' || colourCode(code)) bold = false;
                    i++;
                }
                end = Math.min(s.length(), i + 1);
                continue;
            }
            int a = advance(c) + (bold ? 1 : 0);
            if (w + a > room) return end;
            w += a;
            end = i + 1;
        }
        return end;
    }

    /**
     * The string wrapped into lines of at most {@code max} pixels at 1x and at most {@code maxLines} lines: breaks at
     * spaces between words, anywhere between hanzi, at a newline, and inside a word longer than the line. Each line
     * starts with the section codes in force where it starts, as vanilla's wrapping does. When text is left over, the
     * last line is the rest cut with an ellipsis.
     */
    public static List<String> wrap(String s, int max, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (s == null || s.isEmpty() || maxLines <= 0) return lines;
        String rest = s;
        while (true) {
            int nl = rest.indexOf('\n');
            String para = nl >= 0 ? rest.substring(0, nl) : rest;
            int end = lineEnd(para, max);
            String line = trimEnd(para.substring(0, end));
            String after;
            if (end < para.length()) after = rest.substring(para.charAt(end) == ' ' ? end + 1 : end);
            else after = nl >= 0 ? rest.substring(nl + 1) : "";
            if (after.isEmpty()) {
                lines.add(line);
                return lines;
            }
            if (lines.size() == maxLines - 1) {
                lines.add(fit(rest.replace('\n', ' '), max));
                return lines;
            }
            lines.add(line);
            rest = carry(line) + after;
        }
    }

    /** Where a line of one paragraph ends: the last space that fits, else the last glyph that fits, at least one. */
    private static int lineEnd(String para, int max) {
        int end = cut(para, 0, max);
        if (end >= para.length()) return para.length();
        int first = 0;
        while (first + 1 < para.length() && para.charAt(first) == '§') first += 2;
        if (end <= first) return Math.min(para.length(), first + 1); // not one glyph fits: take one anyway
        char next = para.charAt(end), prev = para.charAt(end - 1);
        if (next == ' ' || prev >= 128 || next >= 128) return end; // a space, or between hanzi
        int space = para.lastIndexOf(' ', end - 1);
        return space > first ? space : end;
    }

    private static String trimEnd(String line) {
        int e = line.length();
        while (e > 0 && line.charAt(e - 1) == ' ') e--;
        return line.substring(0, e);
    }

    /** The section codes in force at the end of a line: the last colour and the formats after it. */
    private static String carry(String line) {
        String colour = "", formats = "";
        for (int i = 0; i + 1 < line.length(); i++) {
            if (line.charAt(i) != '§') continue;
            char code = Character.toLowerCase(line.charAt(i + 1));
            if (colourCode(code)) {
                colour = "§" + code;
                formats = "";
            } else if (code == 'r') {
                colour = "";
                formats = "";
            } else if ("klmno".indexOf(code) >= 0) formats += "§" + code;
            i++;
        }
        return colour + formats;
    }

    // ------------------------------------------------------------------ runs

    /** A piece of text in one colour: on paper, the ink, the bold pass, and the other formats for the font. */
    public static final class Run {

        public final String text;
        public final int color;
        public final boolean bold;
        /** Format codes other than bold ({@code §o §n §m §k}) to put in front of the text for the font. */
        public final String formats;

        Run(String text, int color, boolean bold, String formats) {
            this.text = text;
            this.color = color;
            this.bold = bold;
            this.formats = formats;
        }
    }

    /**
     * The coloured runs of a string. On paper: colour codes become their inks (and end bold and the formats, as in the
     * font), {@code §r} returns to {@code defaultColor}, {@code §l} turns bold on, the other formats are collected.
     * On the ink card and in the world: one run, the string as it is.
     */
    public static List<Run> runs(String s, Surface surface, int defaultColor) {
        List<Run> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        if (surface != Surface.PAPER) {
            out.add(new Run(s, defaultColor, false, ""));
            return out;
        }
        int color = defaultColor;
        boolean bold = false;
        String formats = "";
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '§') {
                text.append(c);
                continue;
            }
            if (i + 1 >= s.length()) break;
            char code = Character.toLowerCase(s.charAt(++i));
            int ink = Tok.sectionOnPaper(code);
            boolean change = ink != 0 || code == 'r' || "klmno".indexOf(code) >= 0;
            if (!change) continue;
            if (text.length() > 0) {
                out.add(new Run(text.toString(), color, bold, formats));
                text.setLength(0);
            }
            if (ink != 0) {
                color = ink;
                bold = false;
                formats = "";
            } else if (code == 'r') {
                color = defaultColor;
                bold = false;
                formats = "";
            } else if (code == 'l') bold = true;
            else if (formats.indexOf(code) < 0) formats += "§" + code;
        }
        if (text.length() > 0) out.add(new Run(text.toString(), color, bold, formats));
        return out;
    }
}
