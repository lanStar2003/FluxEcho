package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class GtPatternTest {

    /** The spec of every key with its count in the rows, as {@link PartRecipes.Recipe#ingredients} counts them. */
    private static Map<String, Integer> ingredients(GtPattern.Safe s) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<Character, String> e : s.keys.entrySet()) {
            int n = 0;
            for (String row : s.pattern) for (int i = 0; i < row.length(); i++) if (row.charAt(i) == e.getKey()) n++;
            out.merge(e.getValue(), n, Integer::sum);
        }
        return out;
    }

    @Test
    void noPartRecipeHandsGtAToolLetter() {
        for (PartRecipes.Recipe r : PartRecipes.all()) {
            GtPattern.Safe s = GtPattern.safe(r.pattern, r.keys);
            for (String row : s.pattern) for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                assertFalse(GtPattern.tool(c), r.name + ": '" + c + "' is a GT tool letter in " + row);
                assertFalse(Character.isLowerCase(c), r.name + ": lowercase '" + c + "' left in " + row);
            }
            for (char k : s.keys.keySet()) assertFalse(Character.isLowerCase(k), r.name + ": lowercase key " + k);
            assertEquals(r.keys.size(), s.keys.size(), r.name + ": no two keys merged");
            assertEquals(r.ingredients(), ingredients(s), r.name + ": same ingredients in the same numbers");
            assertEquals(r.pattern.length, s.pattern.length, r.name);
            for (int i = 0; i < r.pattern.length; i++)
                assertEquals(r.pattern[i].length(), s.pattern[i].length(), r.name + ": row " + i);
        }
    }

    @Test
    void theRecipesGtUsedToBreakKeepTheirIngredients() {
        // TRIM 'DcD': the coal dust used to become a crowbar
        PartRecipes.Recipe trim = PartRecipes.get(Parts.deck(Parts.D_TRIM));
        GtPattern.Safe s = GtPattern.safe(trim.pattern, trim.keys);
        assertArrayEquals(new String[] { "DDD", "DCD", "DDD" }, s.pattern);
        assertEquals("ore:dustCoal", s.keys.get('C'));
        // RAIL 'r r': the steel rods used to become soft mallets
        PartRecipes.Recipe rail = PartRecipes.get(Parts.fitting(Parts.F_RAIL));
        s = GtPattern.safe(rail.pattern, rail.keys);
        assertArrayEquals(new String[] { "R R", "RGR", "R R" }, s.pattern);
        assertEquals("ore:stickSteel", s.keys.get('R'));
        assertEquals("ore:paneGlass", s.keys.get('G'));
    }

    @Test
    void aTakenUppercaseLetterIsNotReused() {
        Map<Character, String> keys = new LinkedHashMap<>();
        keys.put('c', "ore:a");
        keys.put('C', "ore:b");
        keys.put('A', "ore:c");
        GtPattern.Safe s = GtPattern.safe(new String[] { "cCA" }, keys);
        assertArrayEquals(new String[] { "BCA" }, s.pattern);
        assertEquals("ore:a", s.keys.get('B'));
        assertEquals("ore:b", s.keys.get('C'));
        assertEquals("ore:c", s.keys.get('A'));
    }

    @Test
    void uppercaseOnlyRecipesAreUnchanged() {
        PartRecipes.Recipe base = PartRecipes.get(Parts.frame(Parts.FR_BASE));
        GtPattern.Safe s = GtPattern.safe(base.pattern, base.keys);
        assertArrayEquals(base.pattern, s.pattern);
        assertEquals(base.keys, s.keys);
        assertTrue(GtPattern.tool('r') && GtPattern.tool('c') && !GtPattern.tool('g'));
    }
}
