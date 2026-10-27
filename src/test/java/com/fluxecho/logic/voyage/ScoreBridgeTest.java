package com.fluxecho.logic.voyage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.ResearchTree;
import com.fluxecho.plan.Csv;

/**
 * The scoreboard bridge's pure part (narrative §7.4, ids.md §11): the 31 narrative objectives registered at once in
 * 0.17 (L18), the ones v1 gives a value, their ranges and ladders, the route's trunk and the building rows. The
 * expected values are copied from the narrative's tables; the names and rows are also read back from the ledger's
 * export {@code docs/dev/ids.csv}, so the bridge cannot drift from the only ID book.
 */
class ScoreBridgeTest {

    private static List<List<String>> ledger() throws IOException {
        return Csv.parse(new String(Files.readAllBytes(Paths.get("docs", "dev", "ids.csv")), StandardCharsets.UTF_8));
    }

    /** The ledger's scoreboard rows as names: the grouped row ("feB02、feB03、feB05–feB09、feB11–feB17") spelt out. */
    private static Set<String> ledgerObjectives() throws IOException {
        Set<String> out = new LinkedHashSet<>();
        Pattern range = Pattern.compile("feB(\\d\\d)[–-]feB(\\d\\d)");
        for (List<String> r : ledger()) {
            if (!"scoreboard".equals(r.get(0))) continue;
            for (String part : r.get(2)
                .split("、")) {
                Matcher m = range.matcher(part.trim());
                if (m.matches()) {
                    for (int i = Integer.parseInt(m.group(1)); i <= Integer.parseInt(m.group(2)); i++)
                        out.add(String.format("feB%02d", i));
                } else out.add(part.trim());
            }
        }
        return out;
    }

    @Test
    void theThirtyOneNarrativeObjectivesAreTheLedgers() throws IOException {
        assertEquals(31, ScoreBridge.OBJECTIVES.size(), "narrative §7.4: 31 objectives in 0.17 (L18)");
        assertEquals(31, new HashSet<>(ScoreBridge.OBJECTIVES).size(), "each once");
        Set<String> ledger = ledgerObjectives();
        // the 32nd, the hunting ground's own, comes with the building in 0.24 (L10)
        assertTrue(ledger.remove("feHunt"), "ids.md §11 lists feHunt");
        assertEquals(ledger, new LinkedHashSet<>(ScoreBridge.OBJECTIVES), "the objectives of ids.md §11");
        assertEquals(
            Arrays.asList(
                "feRec",
                "feCat",
                "feShore",
                "feXiang",
                "feCoord",
                "fePhase",
                "feRes",
                "feRoute",
                "feLeap",
                "feList"),
            ScoreBridge.OBJECTIVES.subList(0, 10),
            "in the narrative's order");
        for (int i = 0; i <= 20; i++) assertEquals(String.format("feB%02d", i), ScoreBridge.OBJECTIVES.get(10 + i));
        for (String o : ScoreBridge.OBJECTIVES) {
            assertTrue(o.length() <= 16, o + ": vanilla objective names are at most 16 characters");
            assertTrue(o.startsWith("fe"), o);
        }
    }

    @Test
    void versionOneWritesTheCardsObjectives() {
        // refactor §3.2 T01 and narrative §9.2 N1: feRec, feCat, fePhase, feRes, feRoute and level 1 of feB01–17
        List<String> want = new ArrayList<>(Arrays.asList("feRec", "feCat", "fePhase", "feRes", "feRoute"));
        for (int i = 1; i <= 17; i++) want.add(String.format("feB%02d", i));
        assertEquals(want, ScoreBridge.V1);
        assertTrue(ScoreBridge.OBJECTIVES.containsAll(ScoreBridge.V1));
    }

    @Test
    void theBuildingRowsAreTheLedgers() throws IOException {
        Map<String, Integer> rows = new LinkedHashMap<>();
        for (List<String> r : ledger()) {
            if (!"buildings".equals(r.get(0))) continue;
            Matcher m = Pattern.compile("row: (\\d\\d) .*score: (\\S+)")
                .matcher(r.get(3));
            assertTrue(m.find(), r.toString());
            int row = Integer.parseInt(m.group(1));
            if (row > 20) continue; // 21 is the ascension, scored by feLeap
            assertEquals(String.format("feB%02d", row), m.group(2), r.get(2));
            rows.put(r.get(2), row);
        }
        assertEquals(21, rows.size(), "rows 00–20");
        for (Map.Entry<String, Integer> e : rows.entrySet()) {
            assertEquals((int) e.getValue(), ScoreBridge.row(e.getKey()), e.getKey());
            assertEquals(e.getKey(), ScoreBridge.buildingKey(e.getValue()));
            assertEquals(String.format("feB%02d", e.getValue()), ScoreBridge.building(e.getValue()));
        }
        assertEquals(-1, ScoreBridge.row("ascension"));
        assertEquals(-1, ScoreBridge.row("no_such_building"));
        assertEquals(-1, ScoreBridge.row(null));
    }

    @Test
    void theLaddersHaveTheNarrativesLevels() {
        // narrative §7.4, the ladder table
        assertEquals(3, ScoreBridge.top("feB00"), "capsule: placed, sonar used, rekindled");
        assertEquals(3, ScoreBridge.top("feB01"), "nexus: formed, first manifestation, share ready");
        for (int i : new int[] { 2, 3, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15, 16, 17 })
            assertEquals(3, ScoreBridge.top(String.format("feB%02d", i)), "feB" + i);
        assertEquals(7, ScoreBridge.top("feB04"), "root: well, bell, deep root, net, ley core, matter, share");
        assertEquals(4, ScoreBridge.top("feB10"), "sophon: formed, a rack full, unfold 2, share");
        assertEquals(5, ScoreBridge.top("feB18"), "mast top: anchor, satellite, crown, whisper, share");
        assertEquals(5, ScoreBridge.top("feB19"), "heart: formed, running, share, heart_2, heart_3");
        assertEquals(6, ScoreBridge.top("feB20"), "ring: broken, a segment, 12 segments, keystone, stand, share");
        assertEquals(7, ScoreBridge.top("feXiang"));
        assertEquals(7, ScoreBridge.top("feCoord"));
        assertEquals(5, ScoreBridge.top("fePhase"));
        assertEquals(13, ScoreBridge.top("feRoute"));
        assertEquals(6, ScoreBridge.top("feLeap"));
        assertEquals(20, ScoreBridge.top("feList"));
        for (String counted : new String[] { "feRec", "feCat", "feShore", "feRes" })
            assertEquals(Integer.MAX_VALUE, ScoreBridge.top(counted), counted);
    }

    @Test
    void laddersOnlyRiseAndCountsFollowTheState() {
        for (int i = 0; i <= 20; i++) assertTrue(ScoreBridge.rises(String.format("feB%02d", i)), "feB" + i);
        assertTrue(ScoreBridge.rises("fePhase"), "the highest phase reached");
        assertFalse(ScoreBridge.rises("feList"), "narrative §7.4: the checklist rows are live, they may drop");
        for (String counted : new String[] { "feRec", "feCat", "feShore", "feRes", "feRoute" })
            assertFalse(ScoreBridge.rises(counted), counted + " is counted again from the saved state");

        // a building's ladder keeps the highest level it reached: a level 1 seen again does not pull a 3 down
        assertEquals(3, ScoreBridge.next("feB04", 3, 1));
        assertEquals(1, ScoreBridge.next("feB04", null, 1));
        assertEquals(1, ScoreBridge.next("feB04", 0, 1));
        assertEquals(2, ScoreBridge.next("fePhase", 2, 0), "an unloaded nexus does not lower the phase");
        assertEquals(4, ScoreBridge.next("fePhase", 2, 4));
        assertEquals(5, ScoreBridge.next("fePhase", null, 9), "clamped to the ladder's top");
        assertEquals(3, ScoreBridge.next("feB13", null, 4), "three levels, no more (L10)");
        // counts are written as they are: a research reset lowers feRes
        assertEquals(40, ScoreBridge.next("feRes", 50, 40));
        assertEquals(0, ScoreBridge.next("feRec", 7, -3), "never below zero");
    }

    @Test
    void theRouteIsTheDonePartOfTheTrunk() {
        // narrative §7.4 feRoute: anchor 1 … crossing 13; the trunk is one chain, so ≥ n means the n-th is done
        assertEquals(
            Arrays.asList(
                "anchor",
                "inner_ring",
                "phase_2",
                "phase_3",
                "outer_ring",
                "phase_4",
                "sky_ring",
                "phase_5",
                "source_reach",
                "third_ring",
                "home_bearing",
                "leap",
                "crossing"),
            ScoreBridge.TRUNK);
        assertEquals(0, ScoreBridge.route(Collections.emptySet()));
        assertEquals(1, ScoreBridge.route(set("anchor", "library", "echo_crystal")));
        assertEquals(3, ScoreBridge.route(set("anchor", "inner_ring", "phase_2", "dome")));
        assertEquals(0, ScoreBridge.route(set("inner_ring", "phase_2")), "nothing counts past a gap");
        assertEquals(2, ScoreBridge.route(set("anchor", "inner_ring", "phase_3")));
        assertEquals(13, ScoreBridge.route(new HashSet<>(ScoreBridge.TRUNK)));
        // 0.16.0's spine is the trunk's first seven stars
        Set<String> spine = set(
            ResearchTree.ANCHOR,
            ResearchTree.INNER_RING,
            ResearchTree.PHASE_2,
            ResearchTree.PHASE_3,
            ResearchTree.OUTER_RING,
            ResearchTree.PHASE_4,
            ResearchTree.SKY_RING);
        assertEquals(7, ScoreBridge.route(spine));
        for (String id : spine) assertTrue(ResearchTree.get(id) != null, id + " is a shipped node");
    }

    @Test
    void aTeamsStateGivesTheVersionOneValues() {
        ScoreBridge.State s = new ScoreBridge.State();
        s.records.put("bee", 3);
        s.records.put("aspect", 5);
        s.records.put("mob", 0);
        s.research.addAll(set("anchor", "inner_ring", "library", "phase_2"));
        s.phase = 2;
        s.formed.addAll(set("nexus", "library", "root", "not_a_building"));
        Map<String, Integer> v = ScoreBridge.values(s);
        assertEquals(new LinkedHashSet<>(ScoreBridge.V1), v.keySet(), "every v1 objective, in order");
        assertEquals(8, (int) v.get("feRec"));
        assertEquals(2, (int) v.get("feCat"), "a category with no entry does not count");
        assertEquals(4, (int) v.get("feRes"));
        assertEquals(3, (int) v.get("feRoute"));
        assertEquals(2, (int) v.get("fePhase"));
        assertEquals(1, (int) v.get("feB01"));
        assertEquals(1, (int) v.get("feB02"));
        assertEquals(1, (int) v.get("feB04"), "root formed in any shape: level 1 (the well)");
        assertEquals(0, (int) v.get("feB03"));
        assertEquals(0, (int) v.get("feB17"));

        ScoreBridge.State empty = new ScoreBridge.State();
        for (int x : ScoreBridge.values(empty)
            .values()) assertEquals(0, x);
    }

    private static Set<String> set(String... s) {
        return new LinkedHashSet<>(Arrays.asList(s));
    }
}
