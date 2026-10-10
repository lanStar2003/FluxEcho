package com.fluxecho.logic;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A shaped recipe's pattern and keys made safe for GregTech's {@code GTModHandler.addCraftingRecipe}. GT reads some
 * lowercase pattern letters as crafting tools ({@link #TOOLS}: {@code c} a crowbar, {@code r} a soft mallet,
 * {@code b} a blade and so on): for every such letter in a row it appends the letter and the tool's ore name after the
 * caller's keys, and the shaped ore recipe it then builds keeps the last value given for a key, so the tool silently
 * replaces the caller's ingredient. GT still reports success, so no fallback recipe is added either.
 *
 * <p>
 * {@link #safe} therefore gives every lowercase key (not just today's tool letters, so a letter GT starts to read
 * later cannot break a recipe) an uppercase letter the recipe does not use yet, and rewrites the rows and the keys with
 * it. The ingredients and their places stay exactly as they were, so the crafted recipe still matches
 * {@link PartRecipes#cost}.
 */
public final class GtPattern {

    /** The pattern letters GT 5.09.51 turns into crafting tools when they appear in a shaped recipe's rows. */
    public static final String TOOLS = "bcdfhijkmprswx";

    /** A pattern with its keys, ready to hand to GT. */
    public static final class Safe {

        /** The rows, rewritten; never modify. */
        public final String[] pattern;
        /** Every key with its ingredient spec, in the original key order. */
        public final Map<Character, String> keys;

        Safe(String[] pattern, Map<Character, String> keys) {
            this.pattern = pattern;
            this.keys = Collections.unmodifiableMap(keys);
        }
    }

    private GtPattern() {}

    /** Whether GT reads the letter as a crafting tool. */
    public static boolean tool(char c) {
        return TOOLS.indexOf(c) >= 0;
    }

    /**
     * The pattern and keys with every lowercase key renamed to a free uppercase letter: its own uppercase form when the
     * recipe does not use that already, else the first free one from {@code A}. Other keys keep their letter.
     *
     * @throws IllegalArgumentException when there are not enough free uppercase letters (a recipe has at most nine)
     */
    public static Safe safe(String[] pattern, Map<Character, String> keys) {
        Set<Character> used = new HashSet<>(keys.keySet());
        for (String row : pattern) for (int i = 0; i < row.length(); i++) used.add(row.charAt(i));
        Map<Character, Character> rename = new HashMap<>();
        for (char c : keys.keySet()) {
            if (!Character.isLowerCase(c)) continue;
            char to = Character.toUpperCase(c);
            if (to < 'A' || to > 'Z' || used.contains(to)) to = free(used);
            used.add(to);
            rename.put(c, to);
        }

        String[] rows = new String[pattern.length];
        for (int r = 0; r < pattern.length; r++) {
            StringBuilder sb = new StringBuilder(pattern[r]);
            for (int i = 0; i < sb.length(); i++) {
                Character to = rename.get(sb.charAt(i));
                if (to != null) sb.setCharAt(i, to);
            }
            rows[r] = sb.toString();
        }
        Map<Character, String> out = new LinkedHashMap<>();
        for (Map.Entry<Character, String> e : keys.entrySet()) {
            Character to = rename.get(e.getKey());
            out.put(to != null ? to : e.getKey(), e.getValue());
        }
        return new Safe(rows, out);
    }

    private static char free(Set<Character> used) {
        for (char c = 'A'; c <= 'Z'; c++) if (!used.contains(c)) return c;
        throw new IllegalArgumentException("no free uppercase letter for a recipe key");
    }
}
