package com.fluxdepths.shard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import org.junit.jupiter.api.Test;

class MixTest {

    private static Mix<String> vein(String p, String s, String b, String sp) {
        return Mix.ofVein(p, s, b, sp, Objects::equals);
    }

    @Test
    void primaryAndSecondaryOutweighTheMinorOres() {
        Mix<String> m = vein("chalcopyrite", "iron", "pyrite", "copper");
        assertEquals(4, m.size());
        // 1 : 1 : 1/8 : 1/8 out of 2.25
        assertEquals(1 / 2.25, m.share(0), 1e-9);
        assertEquals(1 / 2.25, m.share(1), 1e-9);
        assertEquals(0.125 / 2.25, m.share(2), 1e-9);
        assertEquals(0.125 / 2.25, m.share(3), 1e-9);
    }

    @Test
    void anOreInTwoLayersCountsTwice() {
        Mix<String> m = vein("tin", "tin", "cassiterite", "cassiterite");
        assertEquals(2, m.size());
        assertEquals(2 / 2.25, m.share(0), 1e-9);
        assertEquals(0.25 / 2.25, m.share(1), 1e-9);
    }

    @Test
    void missingLayersAreSkipped() {
        Mix<String> m = vein("a", null, null, "b");
        assertEquals(2, m.size());
        assertNull(vein(null, null, null, null).pick(0.5));
    }

    @Test
    void picksFollowTheShares() {
        Mix<String> m = vein("a", "b", "c", "d");
        Random r = new Random(7);
        Map<String, Integer> n = new HashMap<>();
        int total = 200_000;
        for (int i = 0; i < total; i++) n.merge(m.pick(r.nextDouble()), 1, Integer::sum);
        for (int i = 0; i < m.size(); i++) {
            double seen = n.get(
                m.ores()
                    .get(i))
                / (double) total;
            assertEquals(
                m.share(i),
                seen,
                0.005,
                m.ores()
                    .get(i));
        }
        assertEquals("d", m.pick(0.9999999));
        assertEquals("a", m.pick(0));
        assertTrue(m.share(0) > m.share(2));
    }
}
