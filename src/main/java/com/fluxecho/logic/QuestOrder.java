package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Merges quest lines into BetterQuesting's {@code QuestLinesOrder.txt}, one {@code "<id>: <name>"} per line. BQ only
 * loads the lines listed there, and throws on a byte order mark or a blank line.
 * <p>
 * Each of our lines replaces the line with the same id in place, or goes at the end. Every other line stays as it
 * is and where it is; blank lines and a byte order mark are dropped, and the file's line ending is kept.
 */
public final class QuestOrder {

    private QuestOrder() {}

    /** The merged file; equal to {@code text} when nothing needed to change. */
    public static String merge(String text, List<String> ours) {
        String body = text.startsWith("﻿") ? text.substring(1) : text;
        String eol = body.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>();
        for (String l : body.split("\r?\n", -1)) if (!l.trim()
            .isEmpty()) lines.add(l);
        for (String entry : ours) {
            int at = indexOf(lines, id(entry));
            if (at < 0) lines.add(entry);
            else lines.set(at, entry);
        }
        String merged = String.join(eol, lines) + eol;
        return merged.equals(text) ? text : merged;
    }

    /** The quest line id of an order line: everything before the first colon. */
    public static String id(String line) {
        int colon = line.indexOf(':');
        return (colon < 0 ? line : line.substring(0, colon)).trim();
    }

    private static int indexOf(List<String> lines, String id) {
        for (int i = 0; i < lines.size(); i++) if (id(lines.get(i)).equals(id)) return i;
        return -1;
    }
}
