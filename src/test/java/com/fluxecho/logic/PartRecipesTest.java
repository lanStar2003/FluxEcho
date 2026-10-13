package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

class PartRecipesTest {

    private static final long U = PartRecipes.UNIT;
    private static final int DECK = Parts.deck(Parts.D_DECK), TRIM = Parts.deck(Parts.D_TRIM),
        SEAT = Parts.frame(Parts.FR_SEAT);

    private static Map<String, Long> map(Object... kv) {
        Map<String, Long> m = new TreeMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], ((Number) kv[i + 1]).longValue());
        return m;
    }

    @Test
    void everyKeyParses() {
        for (PartRecipes.Recipe r : PartRecipes.all()) for (String spec : r.keys.values()) {
            assertFalse(spec.contains("*"), r.name + ": a key has no count");
            int code = PartRecipes.partCode(spec);
            if (code >= 0) {
                assertNotNull(PartRecipes.get(code), r.name + ": " + spec + " has a recipe");
                assertFalse(PartRecipes.partOnly(code), r.name + ": " + spec + " can be expanded");
            } else {
                assertDoesNotThrow(() -> { ResearchTree.Cost.parse(spec); }, r.name + ": " + spec);
                assertFalse(spec.contains("@*"), "no wildcard meta: " + spec);
            }
        }
    }

    @Test
    void patternsAreShaped() {
        for (PartRecipes.Recipe r : PartRecipes.all()) {
            assertTrue(r.pattern.length >= 1 && r.pattern.length <= 3, r.name);
            Set<Character> used = new HashSet<>();
            for (String row : r.pattern) {
                assertEquals(r.pattern[0].length(), row.length(), r.name + ": rows of one width");
                for (char ch : row.toCharArray()) if (ch != ' ') {
                    assertTrue(r.keys.containsKey(ch), r.name + ": key for " + ch);
                    used.add(ch);
                }
            }
            assertEquals(r.keys.keySet(), used, r.name + ": every key is used");
            assertTrue(r.yield >= 1 && r.yield <= 64, r.name);
        }
    }

    @Test
    void tableHoldsEveryPart() {
        for (int m = 0; m < Parts.DECK_TYPES; m++) assertNotNull(PartRecipes.get(Parts.deck(m)), Parts.DECK_NAMES[m]);
        for (int m = 0; m < Parts.FITTING_TYPES; m++)
            assertNotNull(PartRecipes.get(Parts.fitting(m)), Parts.FITTING_NAMES[m]);
        int[] frames = { Parts.FR_BASE, Parts.FR_BASE_LIT, Parts.FR_PILLAR, Parts.FR_CONDUIT, Parts.FR_RING,
            Parts.FR_FOUNDATION, Parts.FR_SHELF, Parts.FR_CONSOLE, Parts.FR_ANCHOR };
        int[] yields = { 8, 4, 4, 1, 4, 8, 32, 2, 4 };
        for (int i = 0; i < frames.length; i++)
            assertEquals(yields[i], PartRecipes.get(Parts.frame(frames[i])).yield, "frame " + frames[i]);
        assertNotNull(PartRecipes.get(Parts.SUPPLY_PORT));
        assertEquals(1, PartRecipes.get(Parts.SUPPLY_PORT).yield);
        assertNotNull(PartRecipes.get(Parts.RECYCLE_BIN), "the recycle bin (0.10.1)");
        assertEquals(1, PartRecipes.get(Parts.RECYCLE_BIN).yield);
        assertEquals(
            "item:minecraft:chest",
            PartRecipes.get(Parts.RECYCLE_BIN).keys.get('B'),
            "steel plates round a chest under a flux crystal");
        assertNull(PartRecipes.get(SEAT), "the HV hull has no spec");
        assertNull(PartRecipes.get(Parts.LIBRARY_CORE));
        assertNull(PartRecipes.get(Parts.GRASS));
        assertNull(PartRecipes.get(Parts.DIRT));
        assertNull(PartRecipes.get(Parts.AIR));
        assertNull(PartRecipes.get(Parts.WATER), "water is paid in EU alone");
        for (int m = 0; m < Parts.MODULE_CORES; m++) assertNull(PartRecipes.get(Parts.moduleCore(m)));
        assertEquals(
            9 + Parts.DECK_TYPES + Parts.FITTING_TYPES + 2,
            PartRecipes.all()
                .size());
        int last = -1;
        for (PartRecipes.Recipe r : PartRecipes.all()) {
            assertTrue(r.part > last, "ordered by code");
            last = r.part;
            assertEquals(r, PartRecipes.get(r.part));
        }
    }

    @Test
    void framePatternsAsInTheFrameModule() {
        PartRecipes.Recipe console = PartRecipes.get(Parts.frame(Parts.FR_CONSOLE));
        assertArrayEquals(new String[] { "SCS", " P ", " L " }, console.pattern);
        assertEquals(PartRecipes.part(Parts.frame(Parts.FR_PILLAR)), console.keys.get('P'));
        assertEquals(PartRecipes.part(Parts.frame(Parts.FR_BASE_LIT)), console.keys.get('L'));
        PartRecipes.Recipe base = PartRecipes.get(Parts.frame(Parts.FR_BASE));
        assertArrayEquals(new String[] { "BSB", "SGS", "BSB" }, base.pattern);
        assertEquals("item:minecraft:stonebrick@32767", base.keys.get('B'));
        assertEquals("item:minecraft:glowstone_dust", PartRecipes.get(Parts.frame(Parts.FR_BASE_LIT)).keys.get('G'));
        assertArrayEquals(new String[] { "SCS", "SSS" }, PartRecipes.get(Parts.frame(Parts.FR_RING)).pattern);
    }

    @Test
    void deckAndTrimCosts() {
        Map<String, Long> deck = map("ore:stone", 32768, "ore:plateSteel", 4096);
        assertEquals(deck, PartRecipes.cost(DECK));
        Map<String, Long> trim = new TreeMap<>(deck);
        trim.put("ore:dustCoal", 8192L);
        assertEquals(trim, PartRecipes.cost(TRIM));
    }

    @Test
    void deeperCosts() {
        // 7/8 of a deck and a quarter of glowstone dust
        assertEquals(
            map("ore:stone", 28672, "ore:plateSteel", 3584, "ore:dustGlowstone", 16384),
            PartRecipes.cost(Parts.deck(Parts.D_LIT)));
        assertEquals(
            map("ore:plateSteel", 8192, "item:minecraft:bookshelf", 8192, "ore:gemEchoCrystal", 2048),
            PartRecipes.cost(Parts.frame(Parts.FR_SHELF)),
            "a shelf yields 32");
        // a lit panel and a quarter of a lit frame base, three levels deep
        assertEquals(
            map(
                "item:minecraft:stonebrick@32767",
                36864,
                "ore:plateSteel",
                3584,
                "ore:dustGlowstone",
                16384,
                "item:minecraft:glowstone_dust",
                16384,
                "ore:plateStainlessSteel",
                8192,
                "ore:dustFluxGrit",
                2048,
                "ore:gemFluxCrystal",
                4096),
            PartRecipes.cost(Parts.fitting(Parts.F_PEDESTAL)));
    }

    /** The parts of 0.11.0: the socket, the basin rim and the cladding anchor, expanded exactly. */
    @Test
    void theCitysPartsCost() {
        // half a panel, half a lit panel and an eighth of a flux crystal
        assertEquals(
            map(
                "item:minecraft:stonebrick@32767",
                16384 + 14336,
                "ore:plateSteel",
                2048 + 1792,
                "ore:dustGlowstone",
                8192,
                "ore:gemFluxCrystal",
                8192),
            PartRecipes.cost(Parts.fitting(Parts.F_SOCKET)));
        // half a trim deck and an eighth of a steel plate
        assertEquals(
            map("ore:stone", 16384, "ore:plateSteel", 2048 + 8192, "ore:dustCoal", 4096),
            PartRecipes.cost(Parts.fitting(Parts.F_BASIN)));
        // a stainless plate, a ring segment and a quarter of a flux crystal
        assertEquals(
            map("ore:plateStainlessSteel", 65536 + 81920, "ore:gemFluxCrystal", 16384 + 16384),
            PartRecipes.cost(Parts.frame(Parts.FR_ANCHOR)));
        assertArrayEquals(new String[] { "PLP", "LCL", "PLP" }, PartRecipes.get(Parts.fitting(Parts.F_SOCKET)).pattern);
        assertArrayEquals(new String[] { "T T", "TST" }, PartRecipes.get(Parts.fitting(Parts.F_BASIN)).pattern);
        assertEquals(8, PartRecipes.get(Parts.fitting(Parts.F_SOCKET)).yield);
        assertEquals(8, PartRecipes.get(Parts.fitting(Parts.F_BASIN)).yield);
    }

    /**
     * The canvas of 0.14.0 (CITY.md §1.4): eight wall panels round a sheet of paper make eight, so a canvas costs a
     * panel and an eighth of a sheet of paper.
     */
    @Test
    void theCanvasCosts() {
        PartRecipes.Recipe r = PartRecipes.get(Parts.deck(Parts.D_CANVAS));
        assertNotNull(r);
        assertArrayEquals(new String[] { "PPP", "PaP", "PPP" }, r.pattern);
        assertEquals(8, r.yield);
        assertEquals(PartRecipes.part(Parts.deck(Parts.D_PANEL)), r.keys.get('P'));
        assertEquals("item:minecraft:paper", r.keys.get('a'));
        Map<String, Long> want = new TreeMap<>(PartRecipes.cost(Parts.deck(Parts.D_PANEL)));
        want.merge("item:minecraft:paper", U / 8, Long::sum);
        assertEquals(want, PartRecipes.cost(Parts.deck(Parts.D_CANVAS)));
        assertEquals(
            map("item:minecraft:stonebrick@32767", 32768, "ore:plateSteel", 4096, "item:minecraft:paper", 8192),
            PartRecipes.cost(Parts.deck(Parts.D_CANVAS)));
    }

    /** The light-lift pad of 0.12.0: four lit panels, four flux crystals and an ender eye make one. */
    @Test
    void theLiftPadCosts() {
        PartRecipes.Recipe r = PartRecipes.get(Parts.fitting(Parts.F_LIFT));
        assertArrayEquals(new String[] { "LCL", "CEC", "LCL" }, r.pattern);
        assertEquals(1, r.yield);
        Map<String, Long> want = new TreeMap<>();
        for (Map.Entry<String, Long> e : PartRecipes.cost(Parts.deck(Parts.D_PANEL_LIT))
            .entrySet()) want.merge(e.getKey(), 4 * e.getValue(), Long::sum);
        want.merge("ore:gemFluxCrystal", 4 * U, Long::sum);
        want.merge("item:minecraft:ender_eye", U, Long::sum);
        assertEquals(want, PartRecipes.cost(Parts.fitting(Parts.F_LIFT)));
    }

    @Test
    void costsAreExactExpansions() {
        for (PartRecipes.Recipe r : PartRecipes.all()) {
            if (PartRecipes.partOnly(r.part)) continue;
            Map<String, Long> cost = PartRecipes.cost(r.part);
            assertFalse(cost.isEmpty(), r.name);
            Map<String, Long> made = new TreeMap<>();
            for (Map.Entry<String, Long> e : cost.entrySet()) {
                assertFalse(PartRecipes.isPart(e.getKey()), r.name + ": raw keys only");
                assertTrue(e.getValue() > 0, r.name);
                made.put(e.getKey(), e.getValue() * r.yield);
            }
            Map<String, Long> spent = new TreeMap<>();
            for (Map.Entry<String, Integer> in : r.ingredients()
                .entrySet()) {
                int sub = PartRecipes.partCode(in.getKey());
                if (sub < 0) spent.merge(in.getKey(), in.getValue() * U, Long::sum);
                else for (Map.Entry<String, Long> s : PartRecipes.cost(sub)
                    .entrySet()) spent.merge(s.getKey(), in.getValue() * s.getValue(), Long::sum);
            }
            assertEquals(spent, made, r.name + ": yield x cost = the crafting grid");
        }
    }

    @Test
    void partOnlyParts() {
        assertTrue(PartRecipes.partOnly(SEAT));
        assertTrue(PartRecipes.partOnly(Parts.LIBRARY_CORE));
        assertTrue(PartRecipes.partOnly(Parts.SUPPLY_PORT));
        assertTrue(PartRecipes.partOnly(Parts.RECYCLE_BIN), "the bin is taken finished, like the port");
        for (int m = 0; m < Parts.MODULE_CORES; m++)
            assertTrue(PartRecipes.partOnly(Parts.moduleCore(m)), "a module core is manifested");
        for (PartRecipes.Recipe r : PartRecipes.all()) if (r.part != Parts.SUPPLY_PORT && r.part != Parts.RECYCLE_BIN)
            assertFalse(PartRecipes.partOnly(r.part), r.name);
        for (int code : new int[] { SEAT, Parts.LIBRARY_CORE, Parts.SUPPLY_PORT, Parts.RECYCLE_BIN, Parts.GRASS,
            Parts.DIRT, Parts.WATER, Parts.AIR, Parts.moduleCore(ModuleCores.ECO_DOME) })
            assertTrue(
                PartRecipes.cost(code)
                    .isEmpty(),
                "no raw cost for " + code);
    }

    @Test
    void partSpecs() {
        assertEquals("part:16", PartRecipes.part(DECK));
        assertEquals(16, PartRecipes.partCode("part:16"));
        assertEquals(-1, PartRecipes.partCode("ore:stone"));
        assertEquals(-1, PartRecipes.partCode("part:x"));
        assertEquals(-1, PartRecipes.partCode("part:"));
        assertEquals(-1, PartRecipes.partCode(null));
    }

    @Test
    void inexactOrBrokenTablesThrow() {
        Map<Integer, PartRecipes.Recipe> t = new TreeMap<>();
        t.put(1, PartRecipes.recipe(1, 3, "thirds", new String[] { "a" }, 'a', "ore:stone"));
        assertThrows(IllegalStateException.class, () -> PartRecipes.computeCosts(t, Collections.<Integer>emptySet()));

        Map<Integer, PartRecipes.Recipe> deep = new TreeMap<>();
        deep.put(1, PartRecipes.recipe(1, 2, "halves", new String[] { "a" }, 'a', "ore:stone"));
        deep.put(2, PartRecipes.recipe(2, 3, "a third of a half", new String[] { "p" }, 'p', "part:1"));
        assertThrows(
            IllegalStateException.class,
            () -> PartRecipes.computeCosts(deep, Collections.<Integer>emptySet()));
        deep.put(2, PartRecipes.recipe(2, 4, "a quarter of a half", new String[] { "p" }, 'p', "part:1"));
        assertEquals(
            map("ore:stone", U / 8),
            PartRecipes.computeCosts(deep, Collections.<Integer>emptySet())
                .get(2));

        Map<Integer, PartRecipes.Recipe> loop = new TreeMap<>();
        loop.put(1, PartRecipes.recipe(1, 1, "one", new String[] { "p" }, 'p', "part:2"));
        loop.put(2, PartRecipes.recipe(2, 1, "two", new String[] { "p" }, 'p', "part:1"));
        assertThrows(
            IllegalStateException.class,
            () -> PartRecipes.computeCosts(loop, Collections.<Integer>emptySet()));

        Map<Integer, PartRecipes.Recipe> only = new TreeMap<>();
        only.put(1, PartRecipes.recipe(1, 1, "port", new String[] { "a" }, 'a', "ore:stone"));
        only.put(2, PartRecipes.recipe(2, 1, "uses the port", new String[] { "p" }, 'p', "part:1"));
        assertThrows(IllegalStateException.class, () -> PartRecipes.computeCosts(only, Collections.singleton(1)));

        assertThrows(
            IllegalStateException.class,
            () -> PartRecipes.recipe(1, 1, "wildcard", new String[] { "a" }, 'a', "item:minecraft:stonebrick@*"));
        assertThrows(
            IllegalStateException.class,
            () -> PartRecipes.recipe(1, 1, "unused key", new String[] { "a" }, 'a', "ore:stone", 'b', "ore:dirt"));
        assertThrows(
            IllegalStateException.class,
            () -> PartRecipes.recipe(1, 1, "ragged", new String[] { "aa", "a" }, 'a', "ore:stone"));
    }
}
