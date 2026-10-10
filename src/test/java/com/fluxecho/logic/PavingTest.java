package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class PavingTest {

    private static final int DECK = Parts.deck(Parts.D_DECK), TRIM = Parts.deck(Parts.D_TRIM),
        LIT = Parts.deck(Parts.D_LIT), GRATE = Parts.deck(Parts.D_GRATE), DARK = Parts.deck(Parts.D_DARK),
        CHEVRON = Parts.deck(Parts.D_CHEVRON), WELL = Parts.deck(Parts.D_WELL);
    /** The Echo Archive's footprint. */
    private static final int WIDTH = 27, DEPTH = 35;

    /** Plans of nexus controllers scattered over a world. */
    private static List<CampusPlan> plans(int n) {
        List<CampusPlan> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new CampusPlan(Mix.seed(i * 997 - 20000, 40 + i % 100, i * 131 - 5000)));
        return out;
    }

    /** {t, s}: out along a hall site's axis and across it. */
    private static int[] siteLocal(int site, long k) {
        int[] ax = CampusPlan.siteAxis(site);
        int a = CampusPlan.keyA(k), r = CampusPlan.keyR(k);
        return new int[] { a * ax[0] + r * ax[1], -a * ax[1] + r * ax[0] };
    }

    private static long siteKey(int site, int t, int s) {
        int[] ax = CampusPlan.siteAxis(site);
        return CampusPlan.key(t * ax[0] - s * ax[1], t * ax[1] + s * ax[0]);
    }

    private static boolean rib(int a, int r) {
        return a == 0 || r == 0 || Math.abs(a) == Math.abs(r);
    }

    /** The share of each code in a floor. */
    private static Map<Integer, Double> shares(Map<Long, Integer> floor) {
        Map<Integer, Double> out = new HashMap<>();
        for (int code : floor.values()) out.merge(code, 1.0 / floor.size(), Double::sum);
        return out;
    }

    /**
     * The spec's share bounds (DECK at least 45%, TRIM 12 to 35%, LIT at most 9%) are those of the establish floor,
     * as in the design's establish table they came from; the hall floors are measured below.
     */
    @Test
    void sharesStayInBounds() {
        for (CampusPlan p : plans(64)) {
            Map<Integer, Double> share = shares(p.establishFloor());
            double deck = share.getOrDefault(DECK, 0.0), trim = share.getOrDefault(TRIM, 0.0),
                lit = share.getOrDefault(LIT, 0.0);
            assertTrue(deck >= 0.45, "DECK " + deck);
            assertTrue(trim >= 0.12 && trim <= 0.35, "TRIM " + trim);
            assertTrue(lit <= 0.09, "LIT " + lit);
        }
    }

    /**
     * A hall floor is about half TRIM: the apron's inner ring (the shadow gap round the building, 115 of its 296
     * cells for the Archive) is TRIM by rule, and the seams and curbs add to it, as in the generator (whose library
     * floor is 47% TRIM). So a finished campus runs above the establish floor's 35% TRIM, but its deck and light
     * shares stay in bounds, and its TRIM stays under 45%.
     */
    @Test
    void sharesOfAFinishedCampus() {
        for (CampusPlan p : plans(64)) {
            Map<Long, Integer> campus = new HashMap<>(p.establishFloor());
            for (int site : CampusPlan.HALL_SITES) {
                Map<Long, Integer> hall = p.siteFloor(site, WIDTH, DEPTH);
                int ring = 0;
                for (Map.Entry<Long, Integer> e : hall.entrySet()) {
                    int[] ts = siteLocal(site, e.getKey());
                    boolean inner = ts[0] >= 23 && ts[0] <= 24 + DEPTH
                        && Math.abs(ts[1]) <= WIDTH / 2 + 1
                        && !(ts[0] == 23 && Math.abs(ts[1]) <= 6);
                    if (inner) {
                        ring++;
                        assertEquals(TRIM, (int) e.getValue(), "the shadow gap at t " + ts[0] + ", s " + ts[1]);
                    }
                }
                assertEquals(115, ring, "the apron's inner ring");
                double trim = shares(hall).getOrDefault(TRIM, 0.0);
                assertTrue(trim >= 115.0 / 296 && trim <= 0.62, "hall TRIM " + trim);
                campus.putAll(hall);
            }
            Map<Integer, Double> share = shares(campus);
            double deck = share.getOrDefault(DECK, 0.0), trim = share.getOrDefault(TRIM, 0.0),
                lit = share.getOrDefault(LIT, 0.0);
            assertTrue(deck >= 0.45, "DECK " + deck);
            assertTrue(trim >= 0.12 && trim <= 0.45, "TRIM " + trim);
            assertTrue(lit <= 0.09, "LIT " + lit);
        }
    }

    @Test
    void lightOnlyOnTheAllowedLines() {
        Set<Integer> establishCodes = new HashSet<>(), siteCodes = new HashSet<>();
        for (int code : new int[] { DECK, TRIM, LIT, GRATE, DARK, WELL }) establishCodes.add(code);
        for (int code : new int[] { DECK, TRIM, LIT, DARK, CHEVRON }) siteCodes.add(code);
        for (CampusPlan p : plans(32)) {
            for (Map.Entry<Long, Integer> e : p.establishFloor()
                .entrySet()) {
                int a = CampusPlan.keyA(e.getKey()), r = CampusPlan.keyR(e.getKey());
                assertTrue(establishCodes.contains(e.getValue()), "establish code " + e.getValue());
                if (e.getValue() != LIT) continue;
                boolean ribLine = CampusPlan.inForum(a, r) && rib(a, r);
                boolean gateSeam = CampusPlan.inGate(a, r) && r == 0;
                assertTrue(ribLine || gateSeam, "light off the lines at " + a + "," + r);
            }
            for (int site : CampusPlan.HALL_SITES) for (Map.Entry<Long, Integer> e : p.siteFloor(site, WIDTH, DEPTH)
                .entrySet()) {
                    int[] ts = siteLocal(site, e.getKey());
                    int t = ts[0], s = ts[1];
                    assertTrue(siteCodes.contains(e.getValue()), "site code " + e.getValue());
                    if (e.getValue() == LIT) {
                        boolean threshold = t == CampusPlan.HALL_FRONT - 1 && Math.abs(s) <= 3;
                        boolean spine = s == 0 && t >= 20 && t <= CampusPlan.HALL_FRONT - 1;
                        assertTrue(threshold || spine, "light off the lines at t " + t + ", s " + s);
                    }
                    if (e.getValue() == CHEVRON) {
                        assertEquals(22 - t, Math.abs(s), "a chevron off the V at t " + t + ", s " + s);
                    }
                }
        }
    }

    @Test
    void wellsSitOnTheForumClearOfTheDais() {
        for (CampusPlan p : plans(64)) {
            Set<Long> wells = p.wells();
            Map<Long, Integer> establish = p.establishFloor();
            for (Map.Entry<Long, Integer> e : establish.entrySet()) {
                assertEquals(wells.contains(e.getKey()), e.getValue() == WELL, "a WELL tile is exactly a well cell");
            }
            assertTrue(
                establish.keySet()
                    .containsAll(wells));
            // the wells are whole 3x3 recesses, apart from each other
            List<int[]> centres = new ArrayList<>();
            for (long k : wells) {
                int a = CampusPlan.keyA(k), r = CampusPlan.keyR(k);
                boolean whole = true;
                for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) {
                    whole &= wells.contains(CampusPlan.key(a + i, r + j));
                }
                if (whole) centres.add(new int[] { a, r });
            }
            assertEquals(wells.size(), 9 * centres.size(), "wells are separate 3x3 squares");
            assertTrue(centres.size() >= 2 && centres.size() <= 6, centres.size() + " wells");
            Set<Integer> lobed = new HashSet<>();
            for (int site : p.lobeSites()) lobed.add(site);
            int lobeWells = 0;
            for (int[] c : centres) {
                boolean forumWell = CampusPlan.inForum(c[0], c[1]);
                int site = c[0] > 0 ? c[1] > 0 ? 1 : 7 : c[1] > 0 ? 3 : 5;
                if (!forumWell) {
                    lobeWells++;
                    assertTrue(lobed.contains(site), "a well beyond the forum rim outside a lobe");
                }
                for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) {
                    int a = c[0] + i, r = c[1] + j;
                    if (forumWell) assertTrue(CampusPlan.inForum(a, r), "a forum well reaches off the forum");
                    else {
                        boolean lobe = CampusPlan.inRing(a, r) && Math.abs(a) <= 16 && Math.abs(r) <= 16;
                        assertTrue(CampusPlan.inForum(a, r) || lobe, "a lobe well reaches off the forum and its lobe");
                    }
                    assertFalse(CampusPlan.inGate(a, r), "a well on the gate");
                    for (int da = -1; da <= 1; da++) for (int dr = -1; dr <= 1; dr++) {
                        assertFalse(CampusPlan.inBase(a + da, r + dr), "a well within 1 of the dais");
                    }
                }
            }
            assertEquals(2, lobeWells, "one well in each lobe");
        }
    }

    @Test
    void lobesDarkenTwoSeededDiagonals() {
        Set<String> pairs = new HashSet<>();
        int darkInLobes = 0, darkElsewhere = 0;
        for (CampusPlan p : plans(64)) {
            int[] lobes = p.lobeSites();
            assertEquals(2, lobes.length);
            assertNotEquals(lobes[0], lobes[1]);
            for (int site : lobes) assertTrue(site % 2 == 1, "lobes lie on diagonals");
            pairs.add(lobes[0] + "-" + lobes[1]);
            Set<Integer> lobed = new HashSet<>();
            for (int site : lobes) lobed.add(site);
            for (Map.Entry<Long, Integer> e : p.establishFloor()
                .entrySet()) {
                int a = CampusPlan.keyA(e.getKey()), r = CampusPlan.keyR(e.getKey());
                if (!CampusPlan.inRing(a, r) || Math.abs(a) > 16 || Math.abs(r) > 16 || e.getValue() != DARK) continue;
                int site = a > 0 ? r > 0 ? 1 : 7 : r > 0 ? 3 : 5;
                if (lobed.contains(site)) darkInLobes++;
                else darkElsewhere++;
            }
        }
        assertTrue(pairs.size() >= 4, "the seed picks different diagonals: " + pairs);
        assertTrue(darkInLobes > 3 * darkElsewhere, darkInLobes + " dark lobe cells, " + darkElsewhere + " elsewhere");
    }

    @Test
    void hallFloorsLeadToTheDoor() {
        for (CampusPlan p : plans(8)) for (int site : CampusPlan.HALL_SITES) {
            Map<Long, Integer> floor = p.siteFloor(site, WIDTH, DEPTH);
            assertEquals(62 + 234, floor.size(), "forecourt and apron");
            int[] half = { 8, 8, 7, 6 };
            for (int i = 0; i < half.length; i++) for (int s = -half[i]; s <= half[i]; s++) {
                assertTrue(floor.containsKey(siteKey(site, 20 + i, s)), "forecourt at t " + (20 + i) + ", s " + s);
            }
            for (int s = -3; s <= 3; s++) assertEquals(LIT, floor.get(siteKey(site, 23, s)), "the door threshold");
            assertEquals(LIT, floor.get(siteKey(site, 20, 0)), "the spine");
            assertEquals(LIT, floor.get(siteKey(site, 22, 0)), "the spine");
            for (int s : new int[] { -2, 2 }) assertEquals(CHEVRON, floor.get(siteKey(site, 20, s)));
            for (int s : new int[] { -1, 1 }) assertEquals(CHEVRON, floor.get(siteKey(site, 21, s)));
            // the apron's inner ring is the shadow gap
            for (Map.Entry<Long, Integer> e : floor.entrySet()) {
                int[] ts = siteLocal(site, e.getKey());
                if (ts[0] <= 23 && Math.abs(ts[1]) <= (ts[0] >= 22 ? 7 - (ts[0] - 22) : 8)) continue; // forecourt
                int a = CampusPlan.keyA(e.getKey()), r = CampusPlan.keyR(e.getKey());
                boolean touches = false;
                for (int da = -1; da <= 1; da++) for (int dr = -1; dr <= 1; dr++) {
                    touches |= CampusPlan.inHall(site, WIDTH, DEPTH, a + da, r + dr);
                }
                if (touches) assertEquals(TRIM, e.getValue(), "the apron's inner ring at t " + ts[0] + ", s " + ts[1]);
            }
            // the back corners are chamfered
            assertFalse(floor.containsKey(siteKey(site, 24 + DEPTH + 1, 15)));
            assertFalse(floor.containsKey(siteKey(site, 24 + DEPTH + 1, -15)));
            assertTrue(floor.containsKey(siteKey(site, 24 + DEPTH + 1, 14)));
        }
    }

    @Test
    void matchesTheGenerator() {
        // The sample seed of spec/gen/synth_campus.py, cross-checked tile by tile against a Python port of these rules.
        CampusPlan p = new CampusPlan(0x5EEDF1E5L);
        Map<Integer, Integer> establish = new HashMap<>(), back = new HashMap<>();
        for (int code : p.establishFloor()
            .values()) establish.merge(code, 1, Integer::sum);
        for (int code : p.siteFloor(4, WIDTH, DEPTH)
            .values()) back.merge(code, 1, Integer::sum);
        Map<Integer, Integer> want = new HashMap<>();
        want.put(DECK, 779);
        want.put(TRIM, 296);
        want.put(LIT, 65);
        want.put(GRATE, 24);
        want.put(DARK, 54);
        want.put(WELL, 45);
        assertEquals(want, establish);
        want.clear();
        want.put(DECK, 109);
        want.put(TRIM, 150);
        want.put(LIT, 9);
        want.put(DARK, 24);
        want.put(CHEVRON, 4);
        assertEquals(want, back);
        assertEquals(5, p.lobeSites()[0]);
        assertEquals(7, p.lobeSites()[1]);
    }
}
