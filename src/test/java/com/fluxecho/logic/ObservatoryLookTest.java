package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The Observatory's drawing (0.16.0, CITY.md §4.12 Visuals): the rendered telescope stands on the dissolved pier and
 * nods along the glazed slit, its whole tube inside the dome's shell at every elevation of its sweep; the ring lens
 * floats over the dome however it turns, rocks and bobs, over the platform; and the counts are the drawing's.
 */
class ObservatoryLookTest {

    private static final CityBuild B = ObservatoryShape.BUILD;
    private static final String OPEN = LookCells.OPEN + ObservatoryShape.DISSOLVES;

    @Test
    void theTelescopeStandsOnTheDissolvedPier() {
        // the pier's column is where the pillars and the seat dissolve
        int x = (int) Math.floor(ObservatoryLook.PIER_X), z = (int) Math.floor(ObservatoryLook.PIER_Z);
        for (int y = 2; y <= ObservatoryShape.SEAT_Y; y++)
            assertTrue(ObservatoryShape.DISSOLVES.indexOf(B.cell(x, y, z)) >= 0, "the pier at y" + y);
        assertTrue(
            ObservatoryLook.PIVOT_Y > ObservatoryShape.SEAT_Y && ObservatoryLook.PIVOT_Y < ObservatoryShape.SEAT_Y + 1);
        // the sweep by the world time alone, round the zenith
        for (double t = 0; t < ObservatoryLook.SWEEP_T; t += 97) {
            double e = ObservatoryLook.elevation(t);
            assertEquals(e, ObservatoryLook.elevation(t + ObservatoryLook.SWEEP_T), 1e-9);
            assertTrue(Math.abs(e - Math.PI / 2) <= ObservatoryLook.ELEVATION_SWING + 1e-12);
            double[] a = ObservatoryLook.tubeAxis(e);
            assertEquals(1, Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]), 1e-12);
            assertTrue(a[1] > 0.9, "looking up");
        }
    }

    /**
     * The tube stays inside the dome at every elevation of its sweep: its surface (eight sides round its axis, from
     * behind the pivot to its objective) clear of the dome's shell, its glazed slit and its walls.
     */
    @Test
    void theTubeStaysInsideTheDome() {
        double lo = ObservatoryLook.ELEVATION_MID - ObservatoryLook.ELEVATION_SWING,
            hi = ObservatoryLook.ELEVATION_MID + ObservatoryLook.ELEVATION_SWING;
        for (double e = lo; e <= hi + 1e-9; e += (hi - lo) / 24) {
            double[] ax = ObservatoryLook.tubeAxis(e);
            for (double s = -ObservatoryLook.TUBE_BACK; s <= ObservatoryLook.TUBE_FRONT + 1e-9; s += 0.1)
                for (int k = 0; k < ObservatoryLook.TUBE_SIDES; k++) {
                    double a = 2 * Math.PI * k / ObservatoryLook.TUBE_SIDES;
                    double across = Math.cos(a) * ObservatoryLook.TUBE_R, up = Math.sin(a) * ObservatoryLook.TUBE_R;
                    // square to the axis: across x, and (0, cos e, sin e) in the axis's plane
                    double x = ObservatoryLook.PIER_X + across,
                        y = ObservatoryLook.PIVOT_Y + ax[1] * s + up * Math.cos(e),
                        z = ObservatoryLook.PIER_Z + ax[2] * s + up * Math.sin(e);
                    assertTrue(
                        LookCells.clearance(B, x, y, z, OPEN, 0.1) >= 0.1,
                        "elevation " + e + ", " + s + " along, side " + k);
                }
            // the objective is the tube's front end
            double[] o = ObservatoryLook.objective(e);
            assertEquals(ObservatoryLook.PIVOT_Y + ax[1] * ObservatoryLook.TUBE_FRONT, o[1], 1e-12);
        }
    }

    /** The ring lens floats over the dome's top and over the platform, however it turns, rocks and bobs. */
    @Test
    void theRingLensFloatsOverTheDome() {
        for (double t = 0; t < 3000; t += 23.5) {
            double tilt = ObservatoryLook.lensTilt(t), bob = ObservatoryLook.lensBob(t);
            assertTrue(Math.abs(tilt) <= ObservatoryLook.ROCK + 1e-12 && Math.abs(bob) <= ObservatoryLook.BOB + 1e-12);
            for (double a = 0; a < 2 * Math.PI; a += 0.2)
                for (int side : new int[] { -1, 1 }) for (int face : new int[] { -1, 1 }) {
                    double r = ObservatoryLook.LENS_R + side * ObservatoryLook.LENS_W / 2;
                    double[] p = ObservatoryLook
                        .lensPoint(a + ObservatoryLook.lensTurn(t), r, face * ObservatoryLook.LENS_H / 2, tilt, bob);
                    assertTrue(p[1] > ObservatoryShape.HEIGHT + 0.5, "over the dome: " + p[1]);
                    assertTrue(p[0] > 0 && p[0] < SkyRing.SIZE && p[2] > 0 && p[2] < SkyRing.SIZE, "over the platform");
                }
        }
        // its middle over the pier
        double[] m = ObservatoryLook.lensPoint(0, 0, 0, 0, 0);
        assertEquals(ObservatoryLook.PIER_X, m[0], 1e-12);
        assertEquals(ObservatoryLook.PIER_Z, m[2], 1e-12);
        assertEquals(ObservatoryLook.LENS_Y, m[1], 1e-12);
    }

    @Test
    void theCountsAreTheDrawing() {
        assertEquals(4 + 2 + 8 + 16 + 8 + 1, ObservatoryLook.telescopeQuads(ModuleLook.NEAR));
        assertEquals(4 + 1 + 1, ObservatoryLook.telescopeQuads(ModuleLook.MID));
        assertEquals(0, ObservatoryLook.telescopeQuads(ModuleLook.FAR));
        assertEquals(32 * 3 + 32 + 16 + 2, ObservatoryLook.lensQuads(ModuleLook.NEAR));
        assertEquals(32, ObservatoryLook.lensQuads(ModuleLook.MID));
        assertEquals(16, ObservatoryLook.lensQuads(ModuleLook.FAR));
        assertEquals(17, ObservatoryLook.landmarkQuads());
        assertEquals(
            ObservatoryLook.telescopeQuads(0) + ObservatoryLook.lensQuads(0) + ObservatoryLook.beamQuads(0),
            ObservatoryLook.quads(0));
    }
}
