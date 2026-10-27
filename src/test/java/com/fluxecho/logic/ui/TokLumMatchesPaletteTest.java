package com.fluxecho.logic.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.RootShape;
import com.fluxecho.logic.skin.SkinPalette;
import com.fluxecho.logic.ui.Tok.Building;

/**
 * The interface's building light colour is the building's own colour in the world (ui 1.3.4: "{@code lum} = style 3.6
 * accent, the one source {@code SkinPalette.accent(id)}"). The two tables were written by two cards of 0.17.0 (F07 the
 * palette, F08 the tokens), each against the design's numbers; this test, added when both were merged, ties them to
 * each other, so a colour the swatch wall changes in one place and not the other is a red build.
 * <p>
 * It checks the rows both ways (the same 23 rows, the root in its three forms), every row's value, and the two
 * lookups by key and by shape name, which must pick the same row for every shape the root has had.
 */
class TokLumMatchesPaletteTest {

    /** A row's name in both tables: the key, and the form after a slash where the building has forms. */
    private static String name(String key, String form) {
        return form == null ? key : key + "/" + form;
    }

    private static Map<String, Integer> palette() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (SkinPalette.Row r : SkinPalette.rows()) m.put(name(r.key, r.form), r.rgb);
        return m;
    }

    private static Map<String, Integer> tokens() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Building b : Tok.buildings()) m.put(name(b.key, b.form), b.lum & 0xFFFFFF);
        return m;
    }

    @Test
    void bothTablesHoldTheSameRows() {
        Map<String, Integer> p = palette(), t = tokens();
        assertEquals(23, p.size(), "the palette's rows (a row each, the root in three forms)");
        assertEquals(23, t.size(), "the tokens' building rows");
        assertEquals(p.keySet(), t.keySet(), "the rows of SkinPalette and Tok.buildings()");
    }

    @Test
    void everyBuildingsLumIsItsAccent() {
        Map<String, Integer> p = palette(), t = tokens();
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, Integer> e : t.entrySet()) {
            Integer accent = p.get(e.getKey());
            if (accent == null || accent.intValue() != e.getValue()) wrong.add(
                String.format(
                    "%s: Tok lum #%06X, SkinPalette %s",
                    e.getKey(),
                    e.getValue(),
                    accent == null ? "none" : String.format("#%06X", accent)));
        }
        assertTrue(wrong.isEmpty(), "lum must equal the accent:\n  " + String.join("\n  ", wrong));
        for (Building b : Tok.buildings())
            assertEquals(0xFF, b.lum >>> 24, b + ": lum is an opaque colour, the accent with full alpha");
    }

    @Test
    void theNexusAndTheCapsuleAreNexusCyan() {
        assertEquals(SkinPalette.NEXUS_CYAN, Tok.building("nexus").lum & 0xFFFFFF);
        assertEquals(SkinPalette.NEXUS_CYAN, Tok.building("drift_capsule").lum & 0xFFFFFF);
    }

    @Test
    void bothLookUpTheSameRowByKeyAndShape() {
        // every shape name the root has had (0.16.0's wells and deep roots), the ley core's, and none at all
        List<String> shapes = Arrays.asList(
            null,
            RootShape.WELL,
            RootShape.WELL_20,
            RootShape.WELL_14,
            RootShape.WELL_8,
            RootShape.DEEP,
            RootShape.ROOT_7,
            RootShape.ROOT_IN,
            RootShape.ROOT_IN_20,
            RootShape.ROOT_IN_14,
            RootShape.ROOT_IN_8,
            RootShape.CORE,
            "phase4",
            "");
        for (SkinPalette.Row r : SkinPalette.rows()) {
            Building byKey = Tok.building(r.key);
            assertNotNull(byKey, r.key + " has a Tok row");
            assertEquals(SkinPalette.accent(r.key), byKey.lum & 0xFFFFFF, r.key + " by key");
            for (String s : shapes) {
                Building b = Tok.building(r.key, s);
                assertNotNull(b, r.key + " in shape " + s);
                assertEquals(SkinPalette.accent(r.key, s), b.lum & 0xFFFFFF, r.key + " in shape " + s);
                if (r.form != null) assertEquals(SkinPalette.rootForm(s), b.form, "the root's form for shape " + s);
            }
        }
        // a key that names no building has no row in either (the ascension's placeholder row has no colour)
        assertNull(Tok.building("ascension"));
        assertEquals(SkinPalette.NONE, SkinPalette.accent("ascension"));
    }
}
