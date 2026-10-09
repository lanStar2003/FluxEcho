package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.GateGeometry.Gate;

class GateSightTest {

    private static final double E = 1e-9;

    @Test
    void aPlayerIsAlsoSeenFromBehindTheFarGate() {
        Gate outside = new Gate(100, 70, 200, 0);
        int plot = 3;
        Gate inside = new Gate(FoldedZone.centerX(plot), FoldedZone.FLOOR_Y + 1, FoldedZone.centerZ(plot) + 10, 2);
        // three blocks in front of the outside gate
        double[][] v = GateSight
            .viewpoints(100.5, 70, 203.5, Collections.singletonList(new GateSight.Line(outside, inside)));
        assertEquals(2, v.length);
        assertArrayEquals(new double[] { 100.5, 70, 203.5 }, v[0], E);
        // that is three blocks behind the inside gate, which faces north: south of it
        assertArrayEquals(GateGeometry.carry(outside, inside, 100.5, 70, 203.5), v[1], E);
        assertEquals(-3, inside.front(v[1][0], v[1][2]), E);
    }

    @Test
    void theNearestViewpointCounts() {
        Gate a = new Gate(0, 64, 0, 0), b = new Gate(5000, 64, 5000, 0);
        double[][] v = GateSight.viewpoints(0.5, 64, 4.5, Arrays.asList(new GateSight.Line(a, b)));
        // something by the far gate is near the player's place there, not their own
        assertSame(v[1], GateSight.nearestFlat(v, 5000, 4990));
        assertSame(v[0], GateSight.nearestFlat(v, 3, 3));
        assertEquals(0, GateSight.distanceSq(v, v[1][0], v[1][1], v[1][2]), E);
        assertEquals(1, GateSight.distanceSq(v, 0.5, 65, 4.5), E);
    }
}
