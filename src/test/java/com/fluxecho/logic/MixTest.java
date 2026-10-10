package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class MixTest {

    @Test
    void deterministic() {
        assertEquals(Mix.seed(100, 64, -200), Mix.seed(100, 64, -200));
        assertEquals(Mix.hash(7, 1, 2, 3), Mix.hash(7, 1, 2, 3));
        assertEquals(Mix.unit(7, 21, 3), Mix.unit(7, 21, 3));
        assertEquals(Mix.hash(Mix.seed(0, 0, 0)), Mix.hash(Mix.seed(0, 0, 0)), "no values at all");
        // pinned, so a change of the hash (which would move every built campus's paving) shows up here
        assertEquals(-9200842011715848914L, Mix.seed(0, 64, 0));
        assertEquals(8050258516870413121L, Mix.seed(100, 64, -200));
        assertEquals(Mix.seed(0, 64, 0), Mix.hash(0x5EEDF1E5L, 0, 64, 0), "the seed is the hash of the position");
    }

    @Test
    void unitIsInTheHalfOpenInterval() {
        double sum = 0;
        int n = 0;
        long[] seeds = { 0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE, Mix.seed(12, 70, -9) };
        for (long seed : seeds) for (int a = -40; a <= 40; a++) for (int b = -40; b <= 40; b++) {
            double u = Mix.unit(seed, a, b);
            assertTrue(u >= 0 && u < 1, "unit " + u + " for " + seed + "," + a + "," + b);
            sum += u;
            n++;
        }
        double mean = sum / n;
        assertTrue(mean > 0.48 && mean < 0.52, "roughly uniform, mean " + mean);
        for (long v : new long[] { Long.MIN_VALUE, Long.MAX_VALUE, -1, 0, 0xFFFFFFFFL }) {
            double u = Mix.unit(v, v, v);
            assertTrue(u >= 0 && u < 1, "extreme input " + v);
        }
    }

    @Test
    void differentPositionsGiveDifferentSeeds() {
        Set<Long> seeds = new HashSet<>();
        int n = 0;
        for (int x = -60; x <= 60; x++) for (int z = -60; z <= 60; z++) for (int y = 0; y < 256; y += 16) {
            seeds.add(Mix.seed(x, y, z));
            n++;
        }
        assertEquals(n, seeds.size(), "every position its own seed");
        // far apart and with swapped coordinates
        assertNotEquals(Mix.seed(1, 2, 3), Mix.seed(3, 2, 1));
        assertNotEquals(Mix.seed(1, 2, 3), Mix.seed(2, 1, 3));
        assertNotEquals(Mix.seed(30_000_000, 64, 0), Mix.seed(-30_000_000, 64, 0));
        assertNotEquals(Mix.seed(0, 64, 0), Mix.seed(0, 65, 0));
    }

    @Test
    void theSeedAndTheValuesBothMatter() {
        assertNotEquals(Mix.hash(1, 5), Mix.hash(2, 5));
        assertNotEquals(Mix.hash(1, 5), Mix.hash(1, 6));
        assertNotEquals(Mix.hash(1, 5, 6), Mix.hash(1, 6, 5), "order matters");
        assertNotEquals(Mix.hash(1, 5), Mix.hash(1, 5, 0), "length matters");
        assertNotEquals(Mix.unit(9, 21, 0), Mix.unit(9, 21, 1));
    }

    @Test
    void bitsAreSpread() {
        // neighbouring cells should not give neighbouring numbers: count how often a unit lands in each tenth
        int[] bins = new int[10];
        long seed = Mix.seed(5, 64, 5);
        for (int a = 0; a < 100; a++) for (int r = 0; r < 100; r++) bins[(int) (Mix.unit(seed, a, r) * 10)]++;
        for (int i = 0; i < 10; i++) assertTrue(bins[i] > 850 && bins[i] < 1150, "tenth " + i + ": " + bins[i]);
    }
}
