package com.fluxecho.logic;

import java.util.regex.Pattern;

/** Small repairs to texts taken from other mods' language files. */
public final class LangText {

    private static final Pattern BLANK = Pattern.compile("%(\\d+\\$)?[sd]");

    private LangText() {}

    /** Whether a text has blanks its mod fills in itself: "%s", "%1$s", "%d" (not "50%"). */
    public static boolean hasBlanks(String text) {
        return text != null && BLANK.matcher(text)
            .find();
    }

    /** The text with those blanks left out: "%s Crimson Praetor%s" is "Crimson Praetor". */
    public static String withoutBlanks(String text) {
        return BLANK.matcher(text)
            .replaceAll("")
            .trim();
    }
}
