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

    /**
     * What an operator's {@code /fluxecho research all} grants: every node the star map lets a team research in this
     * version (0.12.0: phase II, the tidal well and the dome too), never the greyed placeholders of later phases.
     */
    @Test
    void onlyTheNodesOfThisVersionAreAvailable() {
        Set<String> now = new HashSet<>();
        for (ResearchTree.Node n : ResearchTree.availableNow()) now.add(n.id);
        assertTrue(now.contains(ResearchTree.PHASE_2));
        assertTrue(now.contains(ResearchTree.TIDAL_WELL));
        assertTrue(now.contains(ResearchTree.DOME));
        assertTrue(now.contains(ResearchTree.ANCHOR));
        assertTrue(now.contains(ResearchTree.LIBRARY), "the library's research opens the Echo Archive");
        for (ResearchTree.Node n : ResearchTree.all()) {
            // the star map greys a node out exactly when it is not available, however far the nexus has grown
            boolean greyed = ResearchTree.check(n, Collections.emptySet(), 99, 999) == ResearchTree.Block.LATER_PHASE;
            assertEquals(!greyed, ResearchTree.availableNow(n), n.id);
            assertEquals(n.phase <= ResearchTree.PHASE_NOW, now.contains(n.id), n.id);
        }
        // granting them all leaves nothing the star map could still start in this version
        for (ResearchTree.Node n : ResearchTree.all()) {
            ResearchTree.Block b = ResearchTree.check(n, now, ResearchTree.PHASE_NOW, 999);
            assertTrue(b == ResearchTree.Block.DONE || b == ResearchTree.Block.LATER_PHASE, n.id + ": " + b);
        }
        assertFalse(ResearchTree.availableNow((ResearchTree.Node) null));
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

    /** The Eco Dome's research (0.11.0): phase I, after the inner ring and the echo crystal, with real costs. */
    @Test
    void theEcoDome() {
        ResearchTree.Node n = ResearchTree.get(ResearchTree.ECO_DOME);
        assertNotNull(n);
        assertEquals(ResearchTree.Branch.ECHO, n.branch);
        assertEquals(1, n.phase);
        assertEquals(2, ResearchTree.PHASE_NOW, "0.12.0 opens phase II");
        assertTrue(ResearchTree.availableNow(n));
        assertEquals(java.util.Arrays.asList(ResearchTree.INNER_RING, ResearchTree.ECHO_CRYSTAL), n.requires);
        assertEquals(16_000, n.compute);
        assertEquals(3, n.costs.size());
        assertEquals("gemEchoCrystal", n.costs.get(0).name);
        assertEquals(4, n.costs.get(0).count);
        assertEquals("treeSapling", n.costs.get(1).name);
        assertEquals(16, n.costs.get(1).count);
        assertEquals("blockGlass", n.costs.get(2).name);
        assertEquals(32, n.costs.get(2).count);
        Set<String> ring = new HashSet<>();
        ring.add(ResearchTree.ANCHOR);
        ring.add(ResearchTree.INNER_RING);
        assertEquals(ResearchTree.Block.REQUIRES, ResearchTree.check(n, ring, 1, 999));
        ring.add(ResearchTree.ECHO_CRYSTAL);
        assertEquals(ResearchTree.Block.NONE, ResearchTree.check(n, ring, 1, 999));
    }

    /**
     * Phase II made real (0.12.0, CITY.md §5.2 and Appendix B): its node is researched on a nexus of phase I and opens
     * phase II; the tidal well and the dome need it and a nexus of phase II; all three cost titanium plates and EV
     * circuits.
     */
    @Test
    void phaseTwo() {
        ResearchTree.Node p2 = ResearchTree.get(ResearchTree.PHASE_2), dome = ResearchTree.get(ResearchTree.DOME),
            well = ResearchTree.get(ResearchTree.TIDAL_WELL);
        assertEquals(ResearchTree.PHASE_2, ResearchTree.phaseResearch(2));
        assertEquals(2, ResearchTree.phaseOf(ResearchTree.PHASE_2));
        assertEquals(0, ResearchTree.phaseOf(ResearchTree.DOME));
        assertEquals(0, ResearchTree.phaseOf(null));
        assertEquals(0, ResearchTree.phaseOf("phase_x"));
        assertEquals(2, p2.phase);
        assertEquals(1, p2.nexusPhase(), "researched at phase I");
        assertEquals(2, dome.nexusPhase());
        assertEquals(2, well.nexusPhase());
        assertEquals(ResearchTree.Branch.FRAME, p2.branch);
        assertEquals(ResearchTree.Branch.GUARD, dome.branch);
        assertEquals(ResearchTree.Branch.ENERGY, well.branch);
        assertEquals(java.util.Arrays.asList(ResearchTree.INNER_RING), p2.requires);
        assertEquals(java.util.Arrays.asList(ResearchTree.PHASE_2), dome.requires);
        assertEquals(java.util.Arrays.asList(ResearchTree.PHASE_2, ResearchTree.UPKEEP_1), well.requires);
        for (ResearchTree.Node n : new ResearchTree.Node[] { p2, dome, well }) {
            assertTrue(n.compute >= 30_000, n.id);
            Set<String> names = new HashSet<>();
            for (ResearchTree.Cost c : n.costs) names.add(c.name);
            assertTrue(names.contains("plateTitanium") && names.contains("circuitEV"), n.id + ": EV hull and circuits");
            // the phase's own materials (0.12.0, BLUEPRINT 3.6): phase alloy for all three
            assertTrue(names.contains("ingotPhaseAlloy"), n.id + ": phase alloy");
        }
        // resonant glass is manifested once phase II is researched, so only the nodes after it can ask for it
        for (ResearchTree.Node n : new ResearchTree.Node[] { dome, well }) {
            boolean glass = false;
            for (ResearchTree.Cost c : n.costs) glass |= c.ore && "glassResonant".equals(c.name);
            assertTrue(glass, n.id + ": resonant glass");
        }
        for (ResearchTree.Cost c : p2.costs) assertFalse("glassResonant".equals(c.name), "phase II cannot need it");
        Set<String> done = new HashSet<>();
        done.add(ResearchTree.ANCHOR);
        done.add(ResearchTree.INNER_RING);
        assertEquals(ResearchTree.Block.NONE, ResearchTree.check(p2, done, 1, 999), "phase II starts at phase I");
        done.add(ResearchTree.PHASE_2);
        assertEquals(ResearchTree.Block.LATER_PHASE, ResearchTree.check(dome, done, 1, 999), "the dome needs phase II");
        assertEquals(ResearchTree.Block.NONE, ResearchTree.check(dome, done, 2, 999));
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
