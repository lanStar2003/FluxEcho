package com.fluxecho.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The release plan of the rebuild, version by version ({@code plan/batches.csv}, refactor/FINAL.md sections 2.1, 2.3,
 * 2.4 and 2.6): which buildings first get their new shape (by their place in the city's one build order, masterplan
 * 6.1), how many ring segments stand, how many research stars are visible, and the estimated new main and test
 * lines. 0.17.0 checks the file's shape, that it only ever grows, and its own row; each later version adds the checks
 * of its own row (refactor 2.5).
 */
class BatchPlanTest {

    static final List<String> HEADER = Arrays
        .asList("version", "name", "buildings", "ring_segments", "stars", "stars_mvp", "main_est", "test_est");
    /** 0.10.0's new main lines, the most one version ever added; the plan stays at or under 20,000. */
    static final int MAIN_CEILING = 21_741, MAIN_PLAN = 20_000;
    static final Pattern BUILDING = Pattern.compile("([1-9][0-9]*):([a-z_]+)");

    /** One row of the plan. */
    static final class Batch {

        final String version, name;
        /** Build order number to building key, for the buildings whose new shape first ships in this version. */
        final TreeMap<Integer, String> buildings = new TreeMap<>();
        final int ring, stars, starsMvp, main, test;

        Batch(List<String> f) {
            assertEquals(HEADER.size(), f.size(), "fields of " + f);
            version = f.get(0);
            name = f.get(1);
            for (String b : f.get(2)
                .split(" ")) {
                if (b.isEmpty()) continue;
                Matcher m = BUILDING.matcher(b);
                assertTrue(m.matches(), "not order:key in " + version + ": " + b);
                assertNull(buildings.put(Integer.parseInt(m.group(1)), m.group(2)), "order twice: " + b);
            }
            ring = Integer.parseInt(f.get(3));
            stars = Integer.parseInt(f.get(4));
            starsMvp = Integer.parseInt(f.get(5));
            main = Integer.parseInt(f.get(6));
            test = Integer.parseInt(f.get(7));
        }
    }

    static List<Batch> plan() throws IOException {
        InputStream in = BatchPlanTest.class.getResourceAsStream("/plan/batches.csv");
        assertNotNull(in, "plan/batches.csv is missing");
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        for (int n; (n = in.read(buf)) > 0;) b.write(buf, 0, n);
        in.close();
        List<List<String>> rows = Csv.parse(new String(b.toByteArray(), StandardCharsets.UTF_8));
        assertEquals(HEADER, rows.get(0), "header of plan/batches.csv");
        List<Batch> out = new ArrayList<>();
        for (List<String> r : rows.subList(1, rows.size())) out.add(new Batch(r));
        return out;
    }

    @Test
    void oneRowPerVersionFrom017To028() throws IOException {
        List<Batch> plan = plan();
        assertEquals(12, plan.size(), "0.17.0 to 0.28.0");
        for (int i = 0; i < plan.size(); i++) assertEquals("0." + (17 + i) + ".0", plan.get(i).version);
    }

    /** Assertion 1: a building earlier in the build order never gets its new shape later than one after it. */
    @Test
    void buildingsShipInTheirBuildOrder() throws IOException {
        int last = 0;
        Set<Integer> orders = new LinkedHashSet<>();
        for (Batch b : plan()) for (int order : b.buildings.keySet()) {
            assertTrue(order > last, b.version + " ships order " + order + " after order " + last);
            last = order;
            orders.add(order);
        }
        // the stranded capsule (1) and the nexus (2) keep their shape; everything from the archive (3) to the
        // keystone (24) gets a new one exactly once
        List<Integer> all = new ArrayList<>();
        for (int i = 3; i <= 24; i++) all.add(i);
        assertEquals(all, new ArrayList<>(orders));
    }

    /** Assertion 2: at most three buildings a version get their new shape (one building's two forms count once). */
    @Test
    void atMostThreeNewBuildingsAVersion() throws IOException {
        for (Batch b : plan()) assertTrue(
            new LinkedHashSet<>(b.buildings.values()).size() <= 3,
            b.version + " ships " + b.buildings.values());
    }

    /** Assertions 4 and 6, in their 0.17.0 form: no version above the line budget; the ring and the stars only grow. */
    @Test
    void theRingAndTheStarsOnlyGrowAndNoVersionIsTooBig() throws IOException {
        Batch prev = null;
        for (Batch b : plan()) {
            assertTrue(b.main <= MAIN_CEILING, b.version + " main " + b.main);
            assertTrue(b.main <= MAIN_PLAN, b.version + " main " + b.main + " is over the plan");
            assertTrue(b.starsMvp <= b.stars, b.version);
            if (prev != null) {
                assertTrue(b.ring >= prev.ring, b.version + " ring");
                assertTrue(b.stars >= prev.stars, b.version + " stars");
                assertTrue(b.starsMvp >= prev.starsMvp, b.version + " stars (MVP)");
            }
            prev = b;
        }
        assertEquals(23, prev.ring, "the ring closes with its 23rd segment");
        assertEquals(83, prev.stars, "all 83 stars in the end");
        assertEquals(56, prev.starsMvp);
    }

    @Test
    void row017() throws IOException {
        Batch b = plan().get(0);
        assertEquals("0.17.0", b.version);
        assertEquals("白舟", b.name);
        assertTrue(b.buildings.isEmpty(), "0.17.0 lays the base; no building has its new shape yet");
        assertEquals(0, b.ring, "the broken ring stands from 0.19.0");
        assertEquals(29, b.stars, "0.16.0's 29 stars, on the old star map");
        assertEquals(27, b.starsMvp);
        assertEquals(16_900, b.main);
        assertEquals(15_300, b.test);
    }
}
