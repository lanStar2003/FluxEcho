package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ResearchTreeTest {

    @Test
    void oneRootAndEveryRequirementComesEarlier() {
        int roots = 0;
        Set<String> seen = new HashSet<>();
        for (ResearchTree.Node n : ResearchTree.all()) {
            if (n.root()) roots++;
            for (String r : n.requires) {
                assertNotNull(ResearchTree.get(r), n.id + " needs " + r);
                assertTrue(seen.contains(r), n.id + " needs " + r + ", listed later: no cycles");
            }
            seen.add(n.id);
        }
        assertEquals(1, roots);
        assertTrue(
            ResearchTree.get(ResearchTree.ANCHOR)
                .root());
    }

    @Test
    void positionsAreOnTheMapAndApart() {
        for (ResearchTree.Node a : ResearchTree.all()) {
            assertTrue(
                a.x >= 16 && a.x <= ResearchTree.WIDTH - 16 && a.y >= 16 && a.y <= ResearchTree.HEIGHT - 16,
                a.id);
            for (ResearchTree.Node b : ResearchTree.all())
                if (a != b) assertTrue(Math.hypot(a.x - b.x, a.y - b.y) >= 24, a.id + " too close to " + b.id);
        }
    }

    @Test
    void everyNodeOfThisPhaseCanBeReachedAndCosts() {
        Set<String> done = new HashSet<>();
        done.add(ResearchTree.ANCHOR);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (ResearchTree.Node n : ResearchTree.all())
                if (ResearchTree.check(n, done, ResearchTree.PHASE_NOW, 1000) == ResearchTree.Block.NONE) {
                    assertTrue(n.compute > 0, n.id + " takes compute");
                    assertFalse(n.costs.isEmpty(), n.id + " takes items");
                    grew |= done.add(n.id);
                }
        }
        for (ResearchTree.Node n : ResearchTree.all()) {
            if (n.phase <= ResearchTree.PHASE_NOW) assertTrue(done.contains(n.id), n.id + " reachable");
            else assertFalse(done.contains(n.id), n.id + " waits for a later phase");
        }
    }

    @Test
    void blocks() {
        ResearchTree.Node echo = ResearchTree.get(ResearchTree.ECHO_CRYSTAL);
        Set<String> anchor = Collections.singleton(ResearchTree.ANCHOR);
        assertEquals(ResearchTree.Block.REQUIRES, ResearchTree.check(echo, Collections.emptySet(), 1, 10));
        assertEquals(ResearchTree.Block.RECORDS, ResearchTree.check(echo, anchor, 1, 4));
        assertEquals(ResearchTree.Block.NONE, ResearchTree.check(echo, anchor, 1, 5));
        assertEquals(ResearchTree.Block.DONE, ResearchTree.check(ResearchTree.get(ResearchTree.ANCHOR), anchor, 1, 0));
        assertEquals(
            ResearchTree.Block.LATER_PHASE,
            ResearchTree.check(ResearchTree.get(ResearchTree.DOME), anchor, 1, 0));
    }

    @Test
    void costs() {
        ResearchTree.Cost c = ResearchTree.Cost.parse("ore:gemFluxCrystal*16");
        assertTrue(c.ore);
        assertEquals("gemFluxCrystal", c.name);
        assertEquals(16, c.count);
        c = ResearchTree.Cost.parse("item:minecraft:dye@4*3");
        assertFalse(c.ore);
        assertEquals("minecraft:dye", c.name);
        assertEquals(4, c.meta);
        assertEquals(3, c.count);
        assertEquals(1, ResearchTree.Cost.parse("item:minecraft:book").count);
        assertThrows(IllegalArgumentException.class, () -> ResearchTree.Cost.parse("book*2"));
        assertThrows(IllegalArgumentException.class, () -> ResearchTree.Cost.parse("item:book*2"));
        assertThrows(IllegalArgumentException.class, () -> ResearchTree.Cost.parse("ore:x*0"));
    }

    @Test
    void scaledCompute() {
        ResearchTree.Node n = ResearchTree.get(ResearchTree.LIBRARY);
        assertEquals(n.compute / 2, ResearchTree.computeFor(n, 0.5));
        assertEquals(1, ResearchTree.computeFor(n, 1e-9));
        assertEquals(0, ResearchTree.computeFor(ResearchTree.get(ResearchTree.ANCHOR), 1));
    }
}
