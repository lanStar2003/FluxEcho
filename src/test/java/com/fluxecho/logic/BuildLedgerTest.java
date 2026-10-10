package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

class BuildLedgerTest {

    private static final long U = PartRecipes.UNIT;
    private static final int DECK = Parts.deck(Parts.D_DECK), TRIM = Parts.deck(Parts.D_TRIM),
        SHELF = Parts.frame(Parts.FR_SHELF);

    /** Credits the raw cost of n placements of the part at scale 1. */
    private static void fund(BuildLedger l, int part, int n) {
        for (Map.Entry<String, Long> e : PartRecipes.cost(part)
            .entrySet()) l.addRaw(e.getKey(), e.getValue() * n);
    }

    private static Map<Integer, Integer> unbuilt(int... codeCount) {
        Map<Integer, Integer> m = new HashMap<>();
        for (int i = 0; i < codeCount.length; i += 2) m.put(codeCount[i], codeCount[i + 1]);
        return m;
    }

    @Test
    void chargeAndRefundRoundTrip() {
        BuildLedger l = new BuildLedger();
        fund(l, DECK, 1);
        l.addRaw("ore:stone", 5);
        Map<String, Long> before = new TreeMap<>(l.rawView());
        assertTrue(l.charge(DECK, 1));
        assertFalse(l.usedPart());
        assertEquals(5, l.raw("ore:stone"));
        assertEquals(0, l.raw("ore:plateSteel"));
        assertFalse(
            l.rawView()
                .containsKey("ore:plateSteel"),
            "empty lines are dropped");
        l.refund(DECK, 1, l.usedPart());
        assertEquals(before, l.rawView());
    }

    @Test
    void unaffordableChangesNothing() {
        BuildLedger l = new BuildLedger();
        l.addRaw("ore:stone", 10 * U);
        assertFalse(l.charge(DECK, 1), "no steel plate");
        assertEquals(10 * U, l.raw("ore:stone"));
        assertEquals(
            1,
            l.rawView()
                .size());
    }

    @Test
    void partsBeforeRaw() {
        BuildLedger l = new BuildLedger();
        l.addPart(DECK, 1);
        fund(l, DECK, 1);
        assertTrue(l.charge(DECK, 1));
        assertTrue(l.usedPart());
        assertEquals(0, l.part(DECK));
        assertEquals(
            PartRecipes.cost(DECK)
                .get("ore:stone")
                .longValue(),
            l.raw("ore:stone"),
            "raw untouched");
        assertTrue(l.charge(DECK, 1));
        assertFalse(l.usedPart());
        assertTrue(
            l.rawView()
                .isEmpty());
        assertFalse(l.charge(DECK, 1));
        l.refund(DECK, 1, true);
        assertEquals(1, l.part(DECK), "a part comes back as a part");
        assertTrue(
            l.rawView()
                .isEmpty());
    }

    @Test
    void partOnlyNeedsAFinishedPart() {
        BuildLedger l = new BuildLedger();
        l.addRaw("ore:plateSteel", 64 * U);
        l.addRaw("item:minecraft:hopper", 64 * U);
        l.addRaw("ore:gemFluxCrystal", 64 * U);
        assertFalse(l.charge(Parts.SUPPLY_PORT, 1));
        assertFalse(l.charge(Parts.LIBRARY_CORE, 1));
        l.addPart(Parts.LIBRARY_CORE, 1);
        assertTrue(l.charge(Parts.LIBRARY_CORE, 1));
        assertTrue(l.usedPart());
        assertEquals(0, l.part(Parts.LIBRARY_CORE));
        l.refund(Parts.LIBRARY_CORE, 1, true);
        assertEquals(1, l.part(Parts.LIBRARY_CORE));
    }

    @Test
    void gradingIsFree() {
        BuildLedger l = new BuildLedger();
        assertTrue(l.charge(Parts.DIRT, 1));
        assertTrue(l.charge(Parts.GRASS, 10));
        assertTrue(
            l.need(unbuilt(Parts.DIRT, 100, Parts.AIR, 5), 1)
                .isEmpty());
    }

    @Test
    void scaleZeroIsFree() {
        BuildLedger l = new BuildLedger();
        assertTrue(l.charge(DECK, 0));
        assertFalse(l.usedPart());
        assertTrue(l.charge(Parts.SUPPLY_PORT, 0));
        assertTrue(
            l.rawView()
                .isEmpty());
        assertTrue(
            l.need(unbuilt(DECK, 50, Parts.LIBRARY_CORE, 1), 0)
                .isEmpty());
        l.refund(DECK, 0, false);
        assertTrue(
            l.rawView()
                .isEmpty(),
            "a free charge refunds nothing");
        assertEquals(0, BuildLedger.scaled(1234, -3), "negative scales clamp to 0");
    }

    @Test
    void scaleTwoDoubles() {
        BuildLedger l = new BuildLedger();
        fund(l, DECK, 1);
        assertFalse(l.charge(DECK, 2));
        assertEquals(
            PartRecipes.cost(DECK)
                .get("ore:stone")
                .longValue(),
            l.raw("ore:stone"));
        fund(l, DECK, 1);
        assertTrue(l.charge(DECK, 2));
        assertTrue(
            l.rawView()
                .isEmpty());
        l.refund(DECK, 2, false);
        assertEquals(65536, l.raw("ore:stone"));
        assertEquals(8192, l.raw("ore:plateSteel"));
    }

    @Test
    void scaledLinesRoundUp() {
        assertEquals(4506, BuildLedger.scaled(4096, 1.1), "4505.6 rounds up");
        assertEquals(11, BuildLedger.scaled(10, 1.1), "an exact result is not pushed up by binary noise");
        assertEquals(1, BuildLedger.scaled(1, 0.01));
        assertEquals(4096 * 10, BuildLedger.scaled(4096, 99), "the scale caps at 10");
        assertEquals(4096, BuildLedger.scaled(4096, Double.NaN));
        BuildLedger l = new BuildLedger();
        Map<String, Long> need = l.need(unbuilt(DECK, 3), 1.1);
        assertEquals(
            3 * 4506L,
            need.get("ore:plateSteel")
                .longValue(),
            "rounded per placement, as charged");
    }

    @Test
    void withdrawLeavesEchoCrystals() {
        BuildLedger l = new BuildLedger();
        l.addRaw("ore:gemEchoCrystal", 3 * U);
        l.addRaw("ore:stone", 5 * U / 2);
        l.addRaw("ore:dustCoal", U / 2);
        Map<String, Integer> w = l.withdrawable();
        assertEquals(1, w.size());
        assertEquals(
            2,
            w.get("ore:stone")
                .intValue());
        assertFalse(BuildLedger.canWithdraw("ore:gemEchoCrystal"));
        assertEquals(0, l.take("ore:gemEchoCrystal", 1));
        assertEquals(3 * U, l.raw("ore:gemEchoCrystal"));
        assertEquals(2, l.take("ore:stone", 5), "whole items only");
        assertEquals(U / 2, l.raw("ore:stone"));
        assertTrue(
            l.withdrawable()
                .isEmpty());
    }

    @Test
    void needCountsPartsFirstThenBalances() {
        BuildLedger l = new BuildLedger();
        l.addPart(DECK, 4);
        l.addRaw("ore:stone", 32768L * 3);
        Map<String, Long> need = l.need(unbuilt(DECK, 10, TRIM, 2), 1);
        // 6 decks and 2 trims left: 8 decks of stone and plate, 2 eighths of coal
        assertEquals(
            32768L * 8 - 32768L * 3,
            need.get("ore:stone")
                .longValue());
        assertEquals(
            4096L * 8,
            need.get("ore:plateSteel")
                .longValue());
        assertEquals(
            8192L * 2,
            need.get("ore:dustCoal")
                .longValue());
        assertEquals(
            6 * U,
            need.get("part:16")
                .longValue());
        assertEquals(
            2 * U,
            need.get("part:17")
                .longValue());
        assertEquals(5, need.size());

        Map<String, Long> bill = BuildLedger.bill(need);
        assertEquals(3, bill.size(), "craftable part lines are alternatives, not bill lines");
        assertEquals("ore:stone", new ArrayList<>(bill.keySet()).get(0), "largest first");
    }

    @Test
    void wantsIsBackPressure() {
        BuildLedger l = new BuildLedger();
        Map<Integer, Integer> plan = unbuilt(DECK, 2, Parts.SUPPLY_PORT, 1);
        Map<String, Long> need = l.need(plan, 1);
        assertTrue(l.wants("ore:stone", need));
        assertTrue(l.wants("part:16", need));
        assertTrue(l.wants("part:49", need));
        assertFalse(l.wants("ore:dustCoal", need), "nothing in the plan uses coal");
        assertFalse(l.wants("part:17", need));
        assertEquals(
            PartRecipes.UNIT,
            BuildLedger.bill(need)
                .get("part:49")
                .longValue(),
            "part-only parts are billed");

        fund(l, DECK, 2);
        need = l.need(plan, 1);
        assertFalse(l.wants("ore:stone", need), "the balance already covers it");
        assertFalse(l.wants("part:16", need), "raw credit pays for the decks: a finished one would only pile up");
        assertTrue(l.wants("part:49", need), "the port can only be paid with a finished one");
        assertEquals(1, need.size());
        assertFalse(l.funded(plan, 1));
        l.addPart(Parts.SUPPLY_PORT, 1);
        need = l.need(plan, 1);
        assertTrue(need.isEmpty(), "fully funded by raw credit and the port");
        assertTrue(l.funded(plan, 1));
        assertTrue(
            BuildLedger.bill(need)
                .isEmpty());
        l.addPart(DECK, 2);
        need = l.need(plan, 1);
        assertFalse(l.wants("part:16", need));
        assertFalse(l.wants("part:49", need));
        assertTrue(need.isEmpty());
    }

    @Test
    void aFinishedPartIsWantedOnlyWhileItsRawIsShort() {
        BuildLedger l = new BuildLedger();
        Map<Integer, Integer> plan = unbuilt(DECK, 10);
        // plates for all ten decks, stone for seven and a half
        l.addRaw("ore:plateSteel", 4096L * 10);
        l.addRaw("ore:stone", 32768L * 15 / 2);
        Map<String, Long> need = l.need(plan, 1);
        assertFalse(l.wants("ore:plateSteel", need));
        assertTrue(l.wants("ore:stone", need));
        assertEquals(
            3 * U,
            need.get("part:16")
                .longValue(),
            "three finished decks close the stone shortfall of two and a half");
        assertFalse(l.funded(plan, 1));
        l.addPart(DECK, 3);
        need = l.need(plan, 1);
        assertTrue(need.isEmpty(), "seven decks from raw credit, three finished: " + need);
        assertTrue(l.funded(plan, 1));
        for (int i = 0; i < 10; i++) assertTrue(l.charge(DECK, 1), "placement " + i);
        assertFalse(l.charge(DECK, 1));
    }

    @Test
    void anEmptyNeedIsExactlyAFundedJob() {
        // random ledgers against random plans: the need is empty exactly when every unbuilt cell can be charged, and
        // agrees with the bill; a part line never asks for more parts than are missing
        int[] codes = { DECK, TRIM, Parts.deck(Parts.D_LIT), Parts.deck(Parts.D_DARK), SHELF, Parts.SUPPLY_PORT,
            Parts.LIBRARY_CORE, Parts.DIRT };
        Random rnd = new Random(0x10AD);
        int funded = 0, unfunded = 0;
        for (int round = 0; round < 400; round++) {
            double scale = new double[] { 1, 1, 1.1, 2, 0.5 }[rnd.nextInt(5)];
            Map<Integer, Integer> plan = new HashMap<>();
            for (int code : codes) if (rnd.nextInt(3) > 0) plan.put(code, rnd.nextInt(6));
            // mode 0: anything; 1: exactly funded; 2: exactly funded but one unit or one part-only part short
            int mode = rnd.nextInt(3);
            Integer starved = mode == 2 ? codes[rnd.nextInt(codes.length)] : null;
            BuildLedger l = new BuildLedger();
            for (Map.Entry<Integer, Integer> e : plan.entrySet()) {
                int code = e.getKey(), n = e.getValue();
                boolean starve = e.getKey()
                    .equals(starved);
                int have = mode == 0 ? rnd.nextInt(n + 2) / 2 : rnd.nextInt(n + 1);
                if (PartRecipes.partOnly(code) && mode > 0) have = starve ? Math.max(0, n - 1) : n;
                l.addPart(code, have);
                int rawFor = mode == 0 ? rnd.nextInt(n + 2) : n - have;
                boolean first = true;
                for (Map.Entry<String, Long> c : PartRecipes.cost(code)
                    .entrySet()) {
                    long units = BuildLedger.scaled(c.getValue(), scale) * rawFor;
                    long less = mode == 0 ? (rnd.nextInt(4) == 0 ? 1 : 0) : starve && first ? 1 : 0;
                    if (units > 0) l.addRaw(c.getKey(), units - less);
                    first = false;
                }
            }
            Map<String, Long> need = l.need(plan, scale);
            assertEquals(
                need.isEmpty(),
                BuildLedger.bill(need)
                    .isEmpty(),
                "need " + need);
            assertEquals(need.isEmpty(), l.funded(plan, scale));
            for (Map.Entry<String, Long> e : need.entrySet()) {
                int code = PartRecipes.partCode(e.getKey());
                if (code < 0) continue;
                long missing = plan.get(code) - Math.min(plan.get(code), l.part(code));
                assertTrue(e.getValue() <= missing * U, "never more finished parts than are missing: " + e);
            }
            boolean all = true;
            for (Map.Entry<Integer, Integer> e : plan.entrySet()) {
                for (int i = 0; i < e.getValue(); i++) all &= l.charge(e.getKey(), scale);
            }
            assertEquals(need.isEmpty(), all, "need " + need + " at scale " + scale);
            if (all) funded++;
            else unfunded++;
        }
        assertTrue(funded > 40 && unfunded > 40, funded + " funded, " + unfunded + " short");
    }

    @Test
    void replacedOwnPartsCreditOneToOne() {
        BuildLedger l = new BuildLedger();
        l.addPart(SHELF, 9);
        Map<String, Long> need = l.need(unbuilt(SHELF, 9), 1);
        assertTrue(need.isEmpty(), "old shelves pay for unit bodies");
        for (int i = 0; i < 9; i++) assertTrue(l.charge(SHELF, 1));
        assertEquals(0, l.part(SHELF));
    }

    @Test
    void viewsAndLoadRoundTrip() {
        BuildLedger l = new BuildLedger();
        l.addRaw("ore:stone", 12345);
        l.addRaw("ore:gemEchoCrystal", U);
        l.addPart(DECK, 3);
        l.addPart(Parts.LIBRARY_CORE, 1);
        BuildLedger copy = new BuildLedger();
        copy.load(new HashMap<>(l.rawView()), new HashMap<>(l.partView()));
        assertEquals(l.rawView(), copy.rawView());
        assertEquals(l.partView(), copy.partView());

        Map<String, Long> bad = new HashMap<>();
        bad.put("ore:stone", 7L);
        bad.put("ore:dustCoal", 0L);
        bad.put("ore:plateSteel", -5L);
        bad.put("part:16", 9L);
        Map<Integer, Integer> badParts = new HashMap<>();
        badParts.put(DECK, 0);
        badParts.put(TRIM, 2);
        copy.load(bad, badParts);
        assertEquals(
            1,
            copy.rawView()
                .size());
        assertEquals(7, copy.raw("ore:stone"));
        assertEquals(
            1,
            copy.partView()
                .size());
        assertEquals(2, copy.part(TRIM));
        assertThrows(
            UnsupportedOperationException.class,
            () -> copy.rawView()
                .put("ore:stone", 1L));
    }

    @Test
    void badCreditIsRefused() {
        BuildLedger l = new BuildLedger();
        assertThrows(IllegalArgumentException.class, () -> l.addRaw("ore:stone", -1));
        assertThrows(IllegalArgumentException.class, () -> l.addRaw("part:16", U));
        assertThrows(IllegalArgumentException.class, () -> l.addPart(DECK, -1));
        assertTrue(
            l.rawView()
                .isEmpty());
        assertTrue(
            l.partView()
                .isEmpty());
    }

    @Test
    void aStackFeedsEveryLineItMatches() {
        Map<String, Long> need = new java.util.LinkedHashMap<>();
        need.put("item:minecraft:glowstone_dust", 3 * U);
        need.put("part:" + DECK, 5 * U);
        need.put("ore:ingotSteel", 7 * U);
        need.put("ore:dustGlowstone", 2 * U + 1);
        need.put("ore:dustRedstone", 0L);
        // glowstone dust is both an item line and an ore line
        java.util.function.Predicate<String> glowstone = k -> k.contains("lowstone") || k.startsWith("part:");
        Map<String, Integer> got = BuildLedger.share(10, need, glowstone);
        assertEquals(2, got.size(), "part lines are never fed raw items");
        assertEquals(3, (int) got.get("item:minecraft:glowstone_dust"));
        assertEquals(3, (int) got.get("ore:dustGlowstone"), "a part of an item rounds up to a whole one");
        assertEquals(
            new ArrayList<>(got.keySet()),
            java.util.Arrays.asList("item:minecraft:glowstone_dust", "ore:dustGlowstone"),
            "in the need's order");

        // a stack smaller than the first line goes to it alone
        got = BuildLedger.share(2, need, glowstone);
        assertEquals(1, got.size());
        assertEquals(2, (int) got.get("item:minecraft:glowstone_dust"));

        // nothing it matches, or a line already met, takes nothing
        assertTrue(
            BuildLedger.share(10, need, k -> k.equals("ore:dustRedstone"))
                .isEmpty());
        assertTrue(
            BuildLedger.share(0, need, glowstone)
                .isEmpty());
    }
}
