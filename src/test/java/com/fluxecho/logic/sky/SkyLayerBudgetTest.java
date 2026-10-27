package com.fluxecho.logic.sky;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The sky layer's budget and time axis (0.17.0 prototype N0, refactor FINAL F06 and §7.1, narrative FINAL §9.2 N0):
 * the first aurora is at most 96 quads at every moment, in both placements; it is a pure function of the time (the
 * same moment draws the same quads, however often a frame draws it); only quads, every vertex with a unit normal
 * towards the camera and an alpha in range; the sky layer stays inside the far plane at every render distance and
 * keeps out of the player's face, and stands where it is aimed; the world curtain stays where it was hung. Also the
 * sun's path under the shader pack's tilt (refactor §7.1 "日轨"), which {@code probe sun} draws.
 */
class SkyLayerBudgetTest {

    private static final double[] ORIGIN = { 0, 0, 0 };

    @Test
    void theAuroraIsAtMostNinetySixQuadsAtEveryMoment() {
        assertEquals(96, Aurora.QUADS, "N0: ≤ 96 quads");
        Aurora.Placement sky = SkySheet.sky(0, -1, 10), world = SkySheet.world(100, 64, 100, 0, -1);
        for (int t = 0; t < 24000; t += 37) {
            assertTrue(
                Aurora.quads(t + 0.5, sky, ORIGIN)
                    .size() <= 96);
            assertTrue(
                Aurora.quads(t + 0.25, world, new double[] { 100, 66, 100 })
                    .size() <= 96);
        }
    }

    @Test
    void theSameMomentDrawsTheSameQuads() {
        Aurora.Placement sky = SkySheet.sky(0, -1, 12);
        for (double t : new double[] { 0, 18000.5, 123456.75 }) {
            List<double[][]> a = Aurora.quads(t, sky, ORIGIN), b = Aurora.quads(t, sky, ORIGIN);
            assertEquals(a.size(), b.size());
            for (int i = 0; i < a.size(); i++) for (int k = 0; k < 4; k++) assertArrayEquals(a.get(i)[k], b.get(i)[k]);
        }
        // and the time moves it
        double[][] q0 = Aurora.quads(1000, sky, ORIGIN)
            .get(10),
            q1 = Aurora.quads(1100, sky, ORIGIN)
                .get(10);
        assertTrue(q0[0][0] != q1[0][0] || q0[0][7] != q1[0][7], "the curtain waves and ripples with the time");
    }

    @Test
    void onlyQuadsWithUnitNormalsTowardsTheCamera() {
        // the sky layer is drawn at the camera; the world curtain is seen from a camera somewhere in the world
        Aurora.Placement[] places = { SkySheet.sky(1, 0, 8), SkySheet.world(100, 64, 100, 0, -1) };
        double[][] cameras = { ORIGIN, { 120, 70, 90 } };
        for (int i = 0; i < places.length; i++) {
            Aurora.Placement p = places[i];
            double[] cam = cameras[i];
            for (double[][] q : Aurora.quads(4321, p, cam)) {
                assertEquals(4, q.length, "quads only (Angelica takes quads)");
                for (double[] v : q) {
                    double n = Math.sqrt(v[3] * v[3] + v[4] * v[4] + v[5] * v[5]);
                    assertEquals(1, n, 1e-9, "every vertex has a unit normal");
                    double tx = cam[0] - v[0], ty = cam[1] - v[1], tz = cam[2] - v[2];
                    assertTrue(tx * v[3] + ty * v[4] + tz * v[5] > 0, "the normal faces the camera");
                    assertTrue(v[7] >= 0 && v[7] <= 1, "alpha in range");
                }
            }
        }
    }

    @Test
    void theSkyLayerStaysInsideTheFarPlaneAndOutOfTheFace() {
        for (int rd = 2; rd <= 32; rd++) {
            double far = SkySheet.farClip(rd);
            for (double t : new double[] { 0, 777, 18000 })
                for (double[][] q : Aurora.quads(t, SkySheet.sky(0, -1, rd), ORIGIN)) for (double[] v : q) {
                    double d = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
                    assertTrue(d < 0.5 * far, "render distance " + rd + ": " + d + " of " + far);
                    assertTrue(d > 0.4 * far, "not in the player's face");
                    assertTrue(v[1] > 0, "above the horizon");
                }
        }
        assertEquals(320, SkySheet.farClip(10), 1e-9, "the user's render distance 10: the far plane at 320");
    }

    @Test
    void theSkyLayerStandsWhereItIsAimed() {
        // aimed north: the middle of the curtain lies north of the camera, at the foot and top elevations
        Aurora.Placement north = SkySheet.sky(0, -1, 10);
        double[] foot = north.point(0, 0.5, 0, 0), top = north.point(0, 0.5, 1, 0);
        assertTrue(foot[2] < 0 && Math.abs(foot[0]) < 1e-6, "north is -z");
        assertEquals(SkySheet.FOOT_ELEVATION, Math.toDegrees(Math.atan2(foot[1], Math.hypot(foot[0], foot[2]))), 1e-6);
        assertEquals(SkySheet.TOP_ELEVATION, Math.toDegrees(Math.atan2(top[1], Math.hypot(top[0], top[2]))), 1e-6);
        // its ends span the horizon by ±50 degrees
        double[] end = north.point(0, 0, 0, 0);
        assertEquals(
            50,
            Math.toDegrees(
                Math.acos(
                    (end[0] * foot[0] + end[2] * foot[2]) / Math.hypot(end[0], end[2]) / Math.hypot(foot[0], foot[2]))),
            1e-6);
    }

    @Test
    void theWorldCurtainStaysWhereItWasHung() {
        Aurora.Placement south = SkySheet.world(100, 64, 100, 0, 1);
        List<double[][]> a = Aurora.quads(555, south, new double[] { 100, 66, 100 }),
            b = Aurora.quads(555, south, new double[] { -400, 90, 700 });
        for (int i = 0; i < a.size(); i++) for (int k = 0; k < 4; k++)
            for (int c = 0; c < 3; c++) assertEquals(a.get(i)[k][c], b.get(i)[k][c], 0, "the camera does not move it");
        double[] mid = south.point(0, 0.5, 0, 0);
        assertEquals(100, mid[0], 1e-9);
        assertEquals(100 + SkySheet.WORLD_DISTANCE, mid[2], 1e-9, "128 to the south of its anchor");
        assertEquals(64 + SkySheet.WORLD_FOOT, mid[1], 1e-9);
    }

    @Test
    void theSunPathUnderComplementary() {
        assertEquals(0, SunPath.celestialAngle(6000, 0), 1e-9, "noon");
        assertEquals(0.5, SunPath.celestialAngle(18000, 0), 1e-9, "midnight");
        // vanilla: the sun at the zenith at noon, due west at sunset
        double[] vanillaNoon = SunPath.sun(0, SunPath.VANILLA);
        assertEquals(90, SunPath.elevation(vanillaNoon), 1e-9);
        double[] set = SunPath.sun(0.25, SunPath.COMPLEMENTARY);
        assertArrayEquals(new double[] { -1, 0, 0 }, set, 1e-9, "it still sets due west with the tilt");
        assertArrayEquals(new double[] { 1, 0, 0 }, SunPath.sun(0.75, SunPath.COMPLEMENTARY), 1e-9, "and rises east");
        // -40: at noon 50 degrees high in the south (critique feasibility #2)
        double[] noon = SunPath.sun(0, SunPath.COMPLEMENTARY);
        assertEquals(50, SunPath.elevation(noon), 1e-9);
        assertTrue(noon[2] > 0 && Math.abs(noon[0]) < 1e-12, "south is +z");
        // a sail turning only about the north-south axis misses the tilted noon sun by 40 degrees
        double[] n = SunPath.sailNormal(noon);
        assertNotNull(n);
        assertEquals(40, SunPath.angle(n, noon), 1e-9);
        assertEquals(0, SunPath.angle(SunPath.sailNormal(vanillaNoon), vanillaNoon), 1e-9);
    }
}
