package com.fluxecho.logic.probe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.DomeGeneratorShape;
import com.fluxecho.logic.probe.CamPoints.Place;
import com.fluxecho.logic.probe.CamPoints.Point;

/**
 * The camera points against masterplan FINAL §7.3 (0.17.0, refactor FINAL F06 and §2.6): A the gatehouse bridge (31, 0)
 * at Y0 + 15 looking west; B the pod's bridge (0, 0) at Y0 + F + 2; C the sunward pier's crown walk (-79, 28) at Y0 + 8
 * looking east; D the tether deck (0, 0) at Y0 + 52 looking down; E the look-back hill (-110, 0) at Y0 + 9 looking
 * east;
 * eyes 1.6 higher; the campus frame turned with the nexus's front (§2.1 {@code toWorld}); every point within the
 * campus's reach.
 */
class CamPointsTest {

    private static final int CX = 1000, Y0 = 70, CZ = -500, F = 40;

    @Test
    void thePointsAreTheMasterplans() {
        Object[][] table = { { Point.A, 31, 0, 15, 16.6 }, { Point.B, 0, 0, 42, 43.6 }, { Point.C, -79, 28, 8, 9.6 },
            { Point.D, 0, 0, 52, 53.6 }, { Point.E, -110, 0, 9, 10.6 } };
        for (Object[] row : table) {
            Point p = (Point) row[0];
            assertEquals(row[1], p.a, p + " A");
            assertEquals(row[2], p.r, p + " R");
            int up = p.up + (p.overDatum ? F : 0);
            assertEquals(row[3], up, p + " stands at Y0 + " + up);
            assertEquals((Double) row[4], up + CamPoints.EYE, 1e-9, p + " eye height");
        }
        assertEquals(5, Point.values().length);
    }

    @Test
    void onANewCampusFacingEast() {
        // a new campus faces east: x = A, z = R, +R is south
        Place a = CamPoints.place(Point.A, CX, Y0, CZ, 1, 0, F);
        assertEquals(CX + 31.5, a.x, 1e-9);
        assertEquals(Y0 + 15, a.y, 1e-9);
        assertEquals(CZ + 0.5, a.z, 1e-9);
        assertEquals(90, a.yaw, 1e-4, "A looks west, down the axis to the mast and the ring");
        assertEquals(0, a.pitch, 1e-9);
        Place b = CamPoints.place(Point.B, CX, Y0, CZ, 1, 0, F);
        assertEquals(Y0 + 42, b.y, 1e-9, "B: two layers over the pod's floor at Y0 + 40");
        assertEquals(90, b.yaw, 1e-4);
        assertTrue(b.pitch > 0, "B looks a little down at the city");
        Place c = CamPoints.place(Point.C, CX, Y0, CZ, 1, 0, F);
        assertEquals(CX - 79 + 0.5, c.x, 1e-9);
        assertEquals(CZ + 28 + 0.5, c.z, 1e-9, "the sunward pier: south of the axis");
        assertEquals(Y0 + 8, c.y, 1e-9);
        assertEquals(270, c.yaw, 1e-4, "C looks east at the city");
        Place d = CamPoints.place(Point.D, CX, Y0, CZ, 1, 0, F);
        assertEquals(Y0 + 52, d.y, 1e-9);
        assertTrue(d.pitch >= 45, "D looks down");
        Place e = CamPoints.place(Point.E, CX, Y0, CZ, 1, 0, F);
        assertEquals(CX - 110 + 0.5, e.x, 1e-9);
        assertEquals(Y0 + 9, e.y, 1e-9);
        assertEquals(270, e.yaw, 1e-4, "E looks east through the ring");
    }

    @Test
    void anOldCampusKeepsItsOwnFront() {
        // a nexus facing north: the bow is -z, starboard (+R) is east
        assertArrayEquals(new int[] { 0, -31 }, CamPoints.toWorld(31, 0, 0, -1));
        assertArrayEquals(new int[] { 28, 79 }, CamPoints.toWorld(-79, 28, 0, -1));
        Place a = CamPoints.place(Point.A, 0, 64, 0, 0, -1, F);
        assertEquals(0.5, a.x, 1e-9);
        assertEquals(-30.5, a.z, 1e-9);
        assertEquals(0, a.yaw, 1e-4, "A looks to the stern: south for a nexus facing north");
        // facing west and south as well: the four fronts are four turns of one picture
        for (int[] f : new int[][] { { 1, 0 }, { 0, 1 }, { -1, 0 }, { 0, -1 } }) {
            int[] w = CamPoints.toWorld(-79, 28, f[0], f[1]);
            assertEquals(-79 * -79 + 28 * 28, w[0] * w[0] + w[1] * w[1], "a turn keeps the distance");
            int[] right = CamPoints.toWorld(0, 1, f[0], f[1]);
            assertEquals(-f[1], right[0], "+R is the front turned right");
            assertEquals(f[0], right[1]);
        }
    }

    @Test
    void thePodsDatumFollowsTheBaseLayer() {
        int high = DomeGeneratorShape.datum(200);
        Place b = CamPoints.place(Point.B, 0, 200, 0, 1, 0, high);
        assertEquals(200 + high + 2, b.y, 1e-9);
        assertEquals(40, DomeGeneratorShape.datum(64), "F is 40 on ordinary ground");
    }

    @Test
    void theDistancesTheMasterplanChecks() {
        // A: 31 to the nexus, 111 to the ring at A -80; E: 30 to the ring; every point within the campus's reach
        assertEquals(31, Point.A.a);
        assertEquals(111, Point.A.a - (-80));
        assertEquals(30, -80 - Point.E.a);
        for (Point p : Point.values())
            assertTrue(Math.max(Math.abs(p.a), Math.abs(p.r)) <= 127, p + " lies within Chebyshev 127 (FxCodec)");
    }

    @Test
    void lettersNameThePoints() {
        assertSame(Point.A, Point.of("a"));
        assertSame(Point.E, Point.of(" E "));
        assertNull(Point.of("F"));
        assertNull(Point.of("AB"));
        assertNull(Point.of(null));
        assertEquals(90, CamPoints.yaw(-1, 0), 1e-4);
        assertEquals(0, CamPoints.yaw(0, 1), 1e-4);
        assertEquals(180, CamPoints.yaw(0, -1), 1e-4);
        assertEquals(270, CamPoints.yaw(1, 0), 1e-4);
    }
}
