package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.GateGeometry.Box;
import com.fluxecho.logic.GateGeometry.Gate;

class GateGeometryTest {

    private static final double E = 1e-9;

    @Test
    void quarterTurnsFollowMinecraftsYaw() {
        // yaw 0 looks south (+z), 90 west (-x), 180 north, 270 east
        for (int side = 0; side < 4; side++) {
            double[] r = GateGeometry.rotate(1, GateGeometry.dx(side), GateGeometry.dz(side));
            assertEquals(GateGeometry.dx(side + 1 & 3), r[0], E);
            assertEquals(GateGeometry.dz(side + 1 & 3), r[1], E);
            assertEquals(side, GateGeometry.sideOfYaw(side * 90f));
            assertEquals(side, GateGeometry.sideOfYaw(side * 90f + 360f));
        }
        assertEquals(2, GateGeometry.sideOfYaw(-180f));
    }

    @Test
    void glRotationTurnsTheSameWay() {
        for (int q = 0; q < 4; q++) {
            double a = Math.toRadians(GateGeometry.glDegrees(q));
            // glRotatef about +y: x' = x cos + z sin, z' = -x sin + z cos
            double x = 0.3, z = -1.7;
            double[] r = GateGeometry.rotate(q, x, z);
            assertEquals(r[0], x * Math.cos(a) + z * Math.sin(a), 1e-6);
            assertEquals(r[1], -x * Math.sin(a) + z * Math.cos(a), 1e-6);
        }
    }

    @Test
    void stepsIntoTheFrontOnly() {
        Gate g = new Gate(10, 64, 20, 0); // faces south: you come from +z
        assertTrue(g.entered(10.5, 64, 21.0, 10.5, 64, 20.4));
        assertFalse(g.entered(10.5, 64, 20.4, 10.5, 64, 21.0), "walking out the front is not going in");
        assertFalse(g.entered(13.0, 64, 21.0, 13.0, 64, 20.4), "beside the pane");
        assertFalse(g.entered(10.5, 68, 21.0, 10.5, 68, 20.4), "over the pane");
        assertFalse(g.entered(10.5, 64, 22.0, 10.5, 64, 21.0), "still in front");
    }

    @Test
    void walkingInComesOutOfThePartnerGoingAway() {
        Gate a = new Gate(0, 70, 0, 3); // faces east: walk in going west
        Gate b = new Gate(1024, 64, 10, 2); // faces north: come out going north
        double[] p = GateGeometry.exit(a, b, 0.4, 70, 0.5, 0.3);
        assertTrue(b.front(p[0], p[2]) >= 0.3 - E, "in front of the partner");
        assertEquals(64, p[1], E);
        assertEquals(0, b.across(p[0], p[2]), E);
        // walking west (yaw 90) comes out walking north (yaw 180)
        assertEquals(2, GateGeometry.sideOfYaw(GateGeometry.exitYaw(a, b, 90f)));
    }

    @Test
    void eachWayUndoesTheOther() {
        Gate a = new Gate(-37, 12, 99, 1), b = new Gate(2048, 64, 10, 2);
        double[] p = GateGeometry.carry(a, b, -40.2, 15.5, 97.1);
        double[] back = GateGeometry.carry(b, a, p[0], p[1], p[2]);
        assertArrayEquals(new double[] { -40.2, 15.5, 97.1 }, back, 1e-9);
        // what lies in front of b shows behind a
        double[] seen = GateGeometry.carry(b, a, b.cx(), 64, b.cz() - 5);
        assertTrue(a.front(seen[0], seen[2]) < 0);
    }

    @Test
    void theViewIsInFrontOfTheGate() {
        Gate g = new Gate(100, 64, 10, 2);
        Box v = GateGeometry.view(g, 40, 20, 8, 24);
        assertTrue(v.contains(100, 64, -20));
        assertTrue(v.contains(80, 56, 10));
        assertFalse(v.contains(100, 64, 11), "behind it");
        assertFalse(v.contains(121, 64, 0), "too far to the side");
        assertTrue(v.touchesSection(6, 4, -1));
        assertFalse(v.touchesSection(6, 4, 1));
        Box top = GateGeometry.view(new Gate(0, 250, 0, 0), 4, 2, 8, 24);
        assertEquals(255, top.maxY);
    }

    @Test
    void plotsAreFarApartAndFindable() {
        for (int plot = 0; plot < 5; plot++) {
            Gate g = GateGeometry.plotGate(plot);
            assertEquals(plot, GateGeometry.plotOf(g.cx()));
            assertEquals(plot, GateGeometry.plotOf(GateGeometry.plotX(plot) - 500));
            assertEquals(2, g.facing);
        }
    }

    @Test
    void projectsThroughOpenGlMatrices() {
        float[] identity = { 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1 };
        // a perspective with 90 degrees of view, near 0.05, far 100, square screen
        float n = 0.05f, f = 100f;
        float[] persp = { 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, -(f + n) / (f - n), -1, 0, 0, -2 * f * n / (f - n), 0 };
        double[] centre = GateGeometry.project(persp, identity, 0, 0, -5);
        assertEquals(0, centre[0], 1e-6);
        assertEquals(0, centre[1], 1e-6);
        assertEquals(5, centre[2], 1e-6);
        double[] edge = GateGeometry.project(persp, identity, 5, -5, -5);
        assertEquals(1, edge[0], 1e-6);
        assertEquals(-1, edge[1], 1e-6);
        assertTrue(GateGeometry.project(persp, identity, 0, 0, 3)[2] < 0, "behind the camera");
    }
}
