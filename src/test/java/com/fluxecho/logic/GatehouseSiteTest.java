package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The gate site in the campus plan (0.14.0, CITY.md §2.1-2.2): the gatehouse's envelope over the gate road, its open
 * passage, the passage cells the road leaves bare, its apron's floor (only ever growing: never a cell of the
 * establish floor, the dais, the ring or the gate road), the ground its job takes, and the door and sign the nexus's
 * bridge and the masterplan use.
 */
class GatehouseSiteTest {

    @Test
    void theEnvelopeAndThePassage() {
        assertEquals(
            527,
            CampusPlan.gatehouseCells()
                .size());
        int passage = 0;
        for (long k : CampusPlan.gatehouseCells()) {
            int a = CampusPlan.keyA(k), r = CampusPlan.keyR(k);
            assertTrue(CampusPlan.inGatehouse(a, r));
            if (CampusPlan.inGatePassage(a, r)) passage++;
        }
        assertEquals(13 * 17, passage);
        assertFalse(CampusPlan.inGatehouse(22, 0));
        assertFalse(CampusPlan.inGatehouse(40, 0));
        assertFalse(CampusPlan.inGatehouse(30, 16));
        assertFalse(CampusPlan.inGatePassage(30, 7), "a wing");
        assertTrue(CampusPlan.inGatePassage(23, -6));
        // the gate road runs through the passage and its pylons stand in it
        for (int a = CampusPlan.GATEHOUSE_FROM; a <= 36; a++) assertTrue(CampusPlan.inGate(a, 0));
        for (int[] q : new CampusPlan(5L).pylons()) assertTrue(CampusPlan.inGatePassage(q[0], q[1]), "a pylon");
    }

    /**
     * The passage cells the road leaves bare: |R| 4..6 off the node's (A 25..29) and the threshold's (A 34 on)
     * widenings, R 6 everywhere; never a cell of the gate road.
     */
    @Test
    void thePassageFloor() {
        int n = 0;
        for (int a = 20; a <= 42; a++) for (int r = -8; r <= 8; r++) {
            boolean floor = CampusPlan.gatePassageFloor(a, r);
            if (!floor) continue;
            n++;
            assertTrue(CampusPlan.inGatePassage(a, r));
            assertFalse(CampusPlan.inGate(a, r), "on the road at " + a + "," + r);
            assertTrue(Math.abs(r) >= 4);
        }
        assertEquals(58, n);
        assertTrue(CampusPlan.gatePassageFloor(24, 4));
        assertFalse(CampusPlan.gatePassageFloor(27, 5), "the node's widening");
        assertTrue(CampusPlan.gatePassageFloor(27, 6));
        assertFalse(CampusPlan.gatePassageFloor(38, 4), "counted from the threshold on, as the generator does");
    }

    /**
     * The apron's floor for several seeds: within the apron two cells round the envelope, never a cell of the
     * establish floor or of the gate road, the ring or the dais; trim along the wings; the same for the same seed.
     */
    @Test
    void theApronsFloorOnlyGrows() {
        Set<Long> apron = CampusPlan.gatehouseApron();
        assertEquals(35 * 21 - 527, apron.size());
        for (long seed = 1; seed <= 8; seed++) {
            CampusPlan campus = new CampusPlan(seed * 104729);
            Map<Long, Integer> floor = campus.gatehouseFloor();
            assertEquals(floor, new CampusPlan(seed * 104729).gatehouseFloor(), "the same plan for the same seed");
            Set<Long> establish = campus.establishFloor()
                .keySet();
            for (Map.Entry<Long, Integer> e : floor.entrySet()) {
                long k = e.getKey();
                int a = CampusPlan.keyA(k), r = CampusPlan.keyR(k);
                assertTrue(apron.contains(k));
                assertFalse(establish.contains(k), "an establish tile at " + a + "," + r);
                assertFalse(CampusPlan.inGate(a, r));
                assertFalse(CampusPlan.oct(a, r, CampusPlan.RING_R, CampusPlan.RING_K));
                assertTrue(Parts.isDeck(e.getValue()));
                boolean byWing = false;
                for (int da = -1; da <= 1; da++) for (int dr = -1; dr <= 1; dr++)
                    byWing |= CampusPlan.inGatehouse(a + da, r + dr) && !CampusPlan.inGatePassage(a + da, r + dr);
                if (byWing) assertEquals(Parts.deck(Parts.D_TRIM), (int) e.getValue(), "trim by a wing");
            }
            // the apron's cells on the road (both mouths, |R| <= 3) are the establish floor's, the rest the gate's
            for (long k : apron) {
                int a = CampusPlan.keyA(k), r = CampusPlan.keyR(k);
                if (!establish.contains(k) && !CampusPlan.inGate(a, r))
                    assertTrue(floor.containsKey(k), "a bare apron cell at " + a + "," + r);
            }
            // the apron less the road's 14 cells at the passage's front mouth (A 21..22, |R| <= 3)
            assertEquals(apron.size() - 14, floor.size(), "the apron round the wings");
        }
    }

    /** The ground the job takes: the wings, the bare passage cells and the apron less the establish floor's. */
    @Test
    void theSiteIsTheGroundTheJobTakes() {
        Set<Long> site = CampusPlan.gatehouseSite();
        for (long k : site) {
            int a = CampusPlan.keyA(k), r = CampusPlan.keyR(k);
            assertFalse(CampusPlan.inGate(a, r), "the gate road at " + a + "," + r);
            if (CampusPlan.inGatePassage(a, r)) assertTrue(CampusPlan.gatePassageFloor(a, r));
        }
        assertEquals(
            2 * 9 * 17,
            site.stream()
                .filter(k -> CampusPlan.inGatehouse(CampusPlan.keyA(k), CampusPlan.keyR(k)))
                .filter(k -> !CampusPlan.inGatePassage(CampusPlan.keyA(k), CampusPlan.keyR(k)))
                .count());
    }

    /** The bridge from the nexus runs to the passage's mouth; the sign stands on the road two cells before it. */
    @Test
    void theDoorAndTheSign() {
        assertEquals(CampusPlan.GATEHOUSE_FROM - 0.5, CampusPlan.GATE_DOOR, 1e-12);
        assertEquals(CampusPlan.GATE_DOOR - 2, CampusPlan.GATE_SIGN, 1e-12);
        assertEquals(CampusPlan.GATE_DOOR, CampusPlan.siteDoor(0, true), 1e-12);
        assertEquals(-1, CampusPlan.siteDoor(0, false), 1e-12);
        assertEquals(7, CampusPlan.GATE_HEADROOM);
        assertEquals(SiteFamily.GATE, CampusPlan.family(0));
    }
}
