package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.GateGeometry.Gate;

class PortalMathTest {

    private static final double E = 1e-6;
    private static final double[] PROJ = PortalMath.perspective(70, 16 / 9.0, 0.05, 512);

    private static double ndcZ(double[] proj, double x, double y, double z) {
        double[] c = PortalMath.transform(proj, x, y, z, 1);
        return c[2] / c[3];
    }

    private static double[] ndcXY(double[] proj, double x, double y, double z) {
        double[] c = PortalMath.transform(proj, x, y, z, 1);
        return new double[] { c[0] / c[3], c[1] / c[3] };
    }

    @Test
    void inverseUndoes() {
        double[] m = PortalMath.multiply(PROJ, PortalMath.view(37f, -21f));
        double[] i = PortalMath.multiply(m, PortalMath.invert(m));
        assertArrayEquals(PortalMath.identity(), i, E);
    }

    @Test
    void theViewLooksWhereMinecraftLooks() {
        for (float yaw : new float[] { 0, 90, 180, 270, 33, -140 }) for (float pitch : new float[] { 0, 45, -60 }) {
            double[] l = PortalMath.look(yaw, pitch);
            double[] eye = PortalMath.transform(PortalMath.view(yaw, pitch), l[0], l[1], l[2], 1);
            assertArrayEquals(new double[] { 0, 0, -1, 1 }, eye, E, yaw + " " + pitch);
        }
        // yaw 0 looks south (+z)
        assertArrayEquals(new double[] { 0, 0, 1 }, PortalMath.look(0, 0), E);
    }

    @Test
    void theObliqueNearPlaneCutsAtThePlaneAndKeepsTheScreen() {
        // everything nearer than 5 blocks straight ahead goes
        double[] clip = { 0, 0, -1, -5 };
        double[] o = PortalMath.oblique(PROJ, clip);
        assertEquals(-1, ndcZ(o, 0, 0, -5), E);
        assertEquals(-1, ndcZ(o, 2, -1, -5), E);
        double far = ndcZ(o, 0.4, 0.3, -40);
        assertTrue(far > -1 && far < 1, "kept: " + far);
        assertTrue(ndcZ(o, 0, 0, -3) < -1, "cut");
        // the picture stays where it was
        assertArrayEquals(ndcXY(PROJ, 1.3, -0.7, -9), ndcXY(o, 1.3, -0.7, -9), E);
        // a slanted plane works the same
        double n = Math.sqrt(0.3 * 0.3 + 1);
        double[] slant = { 0.3 / n, 0, -1 / n, -4 / n };
        double[] s = PortalMath.oblique(PROJ, slant);
        // 0.3 x - z - 4 = 0 at x = 2: z = -3.4
        assertEquals(-1, ndcZ(s, 2, 0.5, -3.4), E);
        assertTrue(ndcZ(s, 2, 0.5, -2.5) < -1);
        double kept = ndcZ(s, 2, 0.5, -30);
        assertTrue(kept > -1 && kept < 1);
    }

    @Test
    void planesCarryWithTheirPoints() {
        double[] mv = PortalMath.multiply(PortalMath.view(123f, 17f), translation(0.1, -0.05, -0.1));
        double[] plane = { 0.6, 0, -0.8, 3.5 };
        double[] eyePlane = PortalMath.carryPlane(mv, plane);
        for (double[] p : new double[][] { { 1, 2, 3 }, { -4, 0.5, 9 }, { 7, -3, -2 } }) {
            double before = plane[0] * p[0] + plane[1] * p[1] + plane[2] * p[2] + plane[3];
            double[] e = PortalMath.transform(mv, p[0], p[1], p[2], 1);
            double after = eyePlane[0] * e[0] + eyePlane[1] * e[1] + eyePlane[2] * e[2] + eyePlane[3] * e[3];
            assertEquals(before, after, E);
        }
    }

    private static double[] translation(double x, double y, double z) {
        double[] m = PortalMath.identity();
        m[12] = x;
        m[13] = y;
        m[14] = z;
        return m;
    }

    @Test
    void theMembraneIsANearPlaneForACameraBehindIt() {
        // a gate facing south at the origin; a camera 6 blocks behind it (north), looking south through it
        Gate g = new Gate(0, 64, 0, 0);
        double cx = 0.5, cy = 65.6, cz = -5.5;
        double[] plane = PortalMath.membranePlane(g, cx, cz);
        assertTrue(plane[3] < 0, "the camera is on the side that is cut");
        double[] mv = PortalMath.view(0f, 0f);
        double[] o = PortalMath.oblique(PROJ, PortalMath.carryPlane(mv, plane));
        // a block 3 blocks past the gate is kept, one between the camera and the gate is cut
        double[] past = PortalMath.transform(mv, 0.5 - cx, 65 - cy, 3.5 - cz, 1);
        double[] between = PortalMath.transform(mv, 0.5 - cx, 65 - cy, -2.5 - cz, 1);
        double kept = ndcZ(o, past[0], past[1], past[2]);
        assertTrue(kept > -1 && kept < 1);
        assertTrue(ndcZ(o, between[0], between[1], between[2]) < -1);
    }

    @Test
    void thePyramidHoldsWhatTheMembraneShows() {
        Gate g = new Gate(10, 64, 20, 0);
        // the camera a few blocks behind (north of) the gate, a little to the side
        double cx = 11.2, cy = 65.4, cz = 15.0;
        double[][] planes = PortalMath.pyramid(PortalMath.corners(g, cx, cy, cz, 0), 0);
        assertNotNull(planes);
        // straight through the middle, far past the gate
        double mx = g.cx() - cx, my = g.y + 1.5 - cy, mz = g.cz() - cz;
        assertTrue(PortalMath.inside(planes, mx * 4, my * 4, mz * 4));
        // and on the way there, so the renderer can walk from the camera to the gate
        assertTrue(PortalMath.inside(planes, mx * 0.3, my * 0.3, mz * 0.3));
        // beside the gate, and behind the camera
        assertFalse(PortalMath.inside(planes, mx + 6, my, mz * 4));
        assertFalse(PortalMath.inside(planes, -mx, -my, -mz));
        // padding takes in a little more
        double[][] wide = PortalMath.pyramid(PortalMath.corners(g, cx, cy, cz, 0), 1);
        assertTrue(PortalMath.inside(wide, mx * 2 + 1.8 * 2, my * 2, mz * 2));
    }

    @Test
    void theScreenRectangleFollowsTheMembrane() {
        Gate g = new Gate(0, 64, 0, 2);
        double cx = 0.5, cy = 65.6, cz = 8.5;
        // looking north at it, 8 blocks away: in the middle of the screen
        double[] view = PortalMath.view(180f, 0f);
        int[] r = PortalMath.screenRect(PROJ, view, PortalMath.corners(g, cx, cy, cz, 0), 1600, 900, 0);
        assertNotNull(r);
        assertTrue(r[0] > 0 && r[0] + r[2] < 1600 && r[1] > 0 && r[1] + r[3] < 900, java.util.Arrays.toString(r));
        assertTrue(r[0] < 800 && r[0] + r[2] > 800);
        // looking away: nothing to draw
        assertNull(
            PortalMath.screenRect(PROJ, PortalMath.view(0f, 0f), PortalMath.corners(g, cx, cy, cz, 0), 1600, 900, 0));
        // looking east, the gate far off to the left: not on the screen
        assertNull(
            PortalMath
                .screenRect(PROJ, PortalMath.view(270f, 0f), PortalMath.corners(g, -10, cy, cz + 30, 0), 1600, 900, 0));
        // standing in it: all of the screen
        assertArrayEquals(
            new int[] { 0, 0, 1600, 900 },
            PortalMath.screenRect(PROJ, view, PortalMath.corners(g, 0.5, 65.6, 0.6, 0), 1600, 900, 0));
    }
}
