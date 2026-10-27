package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.shape.Family;
import com.fluxecho.logic.shape.FormCodes;

/**
 * Every building's own legend (0.17.0, style FINAL §3.8.5): the base legend is frozen (every character keeps the part
 * it had in 0.16.0, and no character joins it), a building declares its new characters in its own legend, never one
 * of the base's, and its checksum takes the legend in, the same as the generator's ({@code city/gen/kit.py}'s
 * {@code crc}). A shape without a legend reads, checks and sums exactly as before.
 */
class LegendTest {

    /**
     * The base legend as 0.16.0 has it, character and part code ({@code kit.py}'s {@code LEGEND_BASE}): the frame
     * 0 to 10, the deck 16 to 28, the fittings 32 to 41, grass 50, dirt 51, water 53, must-be-air 63; the soft twins
     * share their solid twins' parts.
     */
    private static final Object[][] BASE = { { 'F', 6 }, { 'B', 0 }, { 'g', 1 }, { 'P', 2 }, { 'C', 3 }, { 'R', 4 },
        { 'Z', 5 }, { 'K', 8 }, { 'A', 9 }, { 'f', 10 }, { 'd', 16 }, { '.', 16 }, { '#', 17 }, { '_', 17 },
        { 'e', 18 }, { '+', 18 }, { ':', 19 }, { '%', 20 }, { 'k', 21 }, { '*', 23 }, { 'W', 24 }, { '{', 24 },
        { 'L', 25 }, { '!', 25 }, { 'w', 26 }, { '[', 26 }, { 'n', 27 }, { 'Q', 28 }, { '$', 28 }, { 'q', 32 },
        { 'p', 33 }, { 'c', 34 }, { 'r', 35 }, { '|', 35 }, { 't', 36 }, { '}', 36 }, { 'G', 37 }, { '/', 37 },
        { 'h', 38 }, { 'o', 39 }, { 'u', 40 }, { '^', 41 }, { ',', 50 }, { ';', 51 }, { '=', 53 }, { '-', 63 } };

    /** The sample's layers, y0 first, each row from the front, as the generator side built it. */
    private static final String[][] LAYERS = { { "xxxxy", "xxxxx", "xxxxx", "yxxxx" },
        { "babab", "aU.va", "a-~-a", "bsssb" }, { "mmimm", "iNlNi", "WeWe ", "  -  " } };

    /**
     * {@code kit.crc} of the sample: {@code Shape('legend_sample', 5, 3, 4, legend=...)} with the legend below and the
     * layers above, as {@code city/gen/kit.py} sums it (0xc95d43cb).
     */
    private static final long SAMPLE_CRC = 3378332619L;

    private static ShapeKit.Shape sample() {
        ShapeKit.Shape s = new ShapeKit.Shape("legend_sample", 5, 3, 4);
        s.legend('a', FormCodes.code(Family.HULL, 0))
            .legend('b', FormCodes.code(Family.HULL, 2))
            .legend('s', FormCodes.code(Family.HULL, 5))
            .legend('x', FormCodes.code(Family.FOOTING, 0))
            .legend('y', FormCodes.code(Family.FOOTING, 9))
            .legend('m', FormCodes.code(Family.SKIN_GARDEN, 7))
            .legend('i', FormCodes.code(Family.SKIN_SCRIPT, 13), true)
            .legend('v', Parts.VINE)
            .legend('l', Parts.BIRCH)
            .legend('N', Parts.deck(Parts.D_HULL_NUMBER))
            .legend('U', Parts.fitting(Parts.F_BED));
        for (int y = 0; y < LAYERS.length; y++) for (int z = 0; z < LAYERS[y].length; z++) {
            String row = LAYERS[y][z];
            for (int x = 0; x < row.length(); x++) if (row.charAt(x) != ' ') s.set(x, y, z, row.charAt(x));
        }
        s.controller = new int[] { 2, 1, 2 };
        return s;
    }

    @Test
    void theBaseLegendIsFrozen() {
        StringBuilder chars = new StringBuilder("~ ");
        for (Object[] e : BASE) {
            char ch = (Character) e[0];
            chars.append(ch);
            assertEquals(e[1], ShapeKit.partOf(ch), "'" + ch + "' keeps its part");
            assertTrue(ShapeKit.inBase(ch));
        }
        assertEquals(-1, ShapeKit.partOf(ShapeKit.CONTROLLER));
        assertEquals(-1, ShapeKit.partOf(ShapeKit.NONE));
        // no printable character joined the base legend since 0.16.0
        List<Character> added = new ArrayList<>();
        for (char ch = 32; ch < 127; ch++)
            if (ShapeKit.inBase(ch) && chars.indexOf(String.valueOf(ch)) < 0) added.add(ch);
        assertEquals(new ArrayList<Character>(), added, "characters added to the base legend");
        assertEquals(".,=;+_{}|[!/$", ShapeKit.SOFT, "the base legend's soft characters");
    }

    @Test
    void aBuildingsLegendIsItsOwn() {
        ShapeKit.Shape s = sample();
        assertEquals(FormCodes.code(Family.HULL, 2), ShapeKit.partOf(s, 'b'));
        assertEquals(46, ShapeKit.partOf(s, 'U'), "the planting bed");
        assertEquals(29, ShapeKit.partOf(s, 'N'), "the hull-number tile");
        assertEquals(Parts.VINE, ShapeKit.partOf(s, 'v'));
        assertEquals(ShapeKit.partOf('W'), ShapeKit.partOf(s, 'W'), "a base character reads as in the base");
        assertThrows(IllegalArgumentException.class, () -> ShapeKit.partOf('b'), "not the base legend's");
        assertThrows(IllegalArgumentException.class, () -> ShapeKit.partOf(s, 'j'), "in neither legend");
        // the soft ones: declared soft, and every live material whatever is declared
        assertTrue(ShapeKit.soft(s, 'i'));
        assertTrue(ShapeKit.soft(s, 'v'));
        assertTrue(ShapeKit.soft(s, 'l'));
        assertTrue(ShapeKit.soft(s, '.'));
        assertFalse(ShapeKit.soft(s, 'a'));
        assertTrue(ShapeKit.checked(s, 'm'));
        assertFalse(ShapeKit.checked(s, 'i'));
        // another shape does not see this one's legend
        assertFalse(ShapeKit.soft(new ShapeKit.Shape("other", 1, 1, 1), 'i'));
        // the base legend is frozen: a legend may not take one of its characters, nor the reserved three, nor twice
        for (char ch : new char[] { 'W', '.', '~', '-', ' ' }) assertThrows(
            IllegalArgumentException.class,
            () -> new ShapeKit.Shape("bad", 1, 1, 1).legend(ch, FormCodes.code(Family.HULL, 0)),
            "'" + ch + "'");
        assertThrows(IllegalArgumentException.class, () -> sample().legend('a', 96), "declared twice");
        assertThrows(IllegalArgumentException.class, () -> new ShapeKit.Shape("bad", 1, 1, 1).legend('j', -1));
    }

    /** The sample's checksum, legend text and summary, as {@code kit.py} has them. */
    @Test
    void theChecksumIsTheGenerators() {
        ShapeKit.Shape s = sample();
        assertEquals(
            String.join(
                "\n",
                "N=deck:13",
                "U=fitting:14",
                "a=hull:0",
                "b=hull:2",
                "i=skin_script:13 soft",
                "l=live:0 soft",
                "m=skin_garden:7",
                "s=hull:5",
                "v=live:2 soft",
                "x=footing:0",
                "y=footing:9"),
            ShapeKit.legendText(s));
        assertEquals(SAMPLE_CRC, ShapeKit.crc(s));
        ShapeKit.Summary sum = ShapeKit.summary(s, null);
        assertEquals(46, sum.checked);
        assertEquals(6, sum.soft);
        assertEquals(3, sum.air);
        assertTrue(sum.unknown.isEmpty(), "every character is in a legend: " + sum.unknown);
        // a legend that changes changes the checksum, though no cell does
        ShapeKit.Shape t = new ShapeKit.Shape("legend_sample", 5, 3, 4);
        t.legend('a', FormCodes.code(Family.HULL, 1));
        t.set(0, 0, 0, 'a');
        ShapeKit.Shape u = new ShapeKit.Shape("legend_sample", 5, 3, 4);
        u.legend('a', FormCodes.code(Family.HULL, 0));
        u.set(0, 0, 0, 'a');
        assertFalse(ShapeKit.crc(t) == ShapeKit.crc(u));
        // a character no legend has is named by the self-check
        ShapeKit.Shape odd = new ShapeKit.Shape("odd", 2, 1, 1);
        odd.set(0, 0, 0, 'j');
        odd.set(1, 0, 0, 'W');
        assertEquals(Arrays.asList('j'), ShapeKit.summary(odd, null).unknown);
    }

    /**
     * Every shape before 0.17.0 has no legend of its own, so it reads and sums as its generator always gave it: the
     * Eco Dome's checksum is still the one {@code EcoDomeShapeTest} holds (913267591), and every character of every
     * hall and pavilion shape reads the base legend's part.
     */
    @Test
    void theOldShapesStayAsTheyWere() {
        assertEquals(913267591L, ShapeKit.crc(EcoDomeShape.SHAPE));
        assertEquals("", ShapeKit.legendText(EcoDomeShape.SHAPE));
        for (ShapeKit.Shape s : new ShapeKit.Shape[] { EcoDomeShape.SHAPE, DualFoilVaultShape.SHAPE,
            ArcaneCourtShape.SHAPE, ManaFountainShape.SHAPE, ResonanceGateShape.SHAPE, SophonArrayShape.SHAPE }) {
            assertTrue(
                s.legend()
                    .isEmpty(),
                s.name);
            for (int y = 0; y < s.H; y++) for (int z = 0; z < s.D; z++) for (int x = 0; x < s.W; x++) {
                char ch = s.get(x, y, z);
                assertEquals(ShapeKit.partOf(ch), ShapeKit.partOf(s, ch), s.name + " '" + ch + "'");
                assertEquals(ShapeKit.soft(ch), ShapeKit.soft(s, ch), s.name + " '" + ch + "'");
            }
            assertTrue(ShapeKit.summary(s, null).unknown.isEmpty(), s.name);
        }
    }
}
