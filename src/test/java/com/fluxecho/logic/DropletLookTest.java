package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The Droplet Hangar's drawing (0.16.0, CITY.md §4.12 Visuals): eight mirror teardrops, a round nose and a sharp
 * point, hover and bob over their cradles while docked and rest on them undocked, never in a block (the socket under
 * each included); their chrome shows the sky over a bright horizon over the dark ground, as a mirror does; and the
 * counts are the drawing's.
 */
class DropletLookTest {

    private static final CityBuild B = DropletHangarShape.BUILD;

    @Test
    void aDropletIsATeardrop() {
        assertArrayEquals(new double[] { 0, 0 }, DropletLook.profile(0), 1e-12);
        assertArrayEquals(new double[] { DropletLook.LENGTH, 0 }, DropletLook.profile(Math.PI), 1e-12);
        double most = 0, at = 0;
        for (int i = 0; i <= 1000; i++) {
            double[] p = DropletLook.profile(Math.PI * i / 1000);
            if (p[1] > most) {
                most = p[1];
                at = p[0];
            }
            assertTrue(p[0] >= 0 && p[0] <= DropletLook.LENGTH + 1e-12);
        }
        assertEquals(DropletLook.RADIUS, most, 1e-9);
        // the widest ahead of the middle: a round nose, a long taper to the point
        assertTrue(at < DropletLook.LENGTH / 2, "widest at " + at);
    }

    /** Its skin's normals are of unit length and look outward, the nose to the front (-z) as it hangs unturned. */
    @Test
    void itsSkinLooksOutward() {
        double[] c = { 10.5, 3, 10.5, 0 };
        for (int k = 1; k < 8; k++) for (int j = 0; j < 10; j++) {
            double[] s = DropletLook.skin(c, k, 8, j, 10);
            assertEquals(1, Math.sqrt(s[3] * s[3] + s[4] * s[4] + s[5] * s[5]), 1e-6);
            // outward from the axis (x and y round it)
            assertTrue((s[0] - c[0]) * s[3] + (s[1] - c[1]) * s[4] >= -1e-9, "ring " + k + " side " + j);
        }
        assertEquals(c[2] - DropletLook.LENGTH / 2, DropletLook.skin(c, 0, 8, 0, 10)[2], 1e-12, "the nose ahead");
        assertEquals(c[2] + DropletLook.LENGTH / 2, DropletLook.skin(c, 8, 8, 0, 10)[2], 1e-12, "the point behind");
    }

    /**
     * The droplets hover over their cradles, bobbing each in its own rhythm, and rest on them undocked: every point of
     * every skin, at any time, clear of the hangar's blocks (the cradle's basin and its socket under it, the kiosk,
     * the deck); the canopy ring gives way and is not counted.
     */
    @Test
    void theDropletsHoverOverTheirCradles() {
        String open = LookCells.OPEN + DropletHangarShape.DISSOLVES;
        for (int i = 0; i < DropletHangarShape.CRADLES.length; i++) {
            double[] cr = DropletLook.cradle(i);
            int[] u = DropletHangarShape.UNITS.units()
                .get(i).sockets.get(0);
            assertEquals(u[0] + 0.5, cr[0], 0, "over its unit's socket");
            assertEquals(u[2] + 0.5, cr[1], 0);
            for (boolean docked : new boolean[] { true, false }) for (double t = 0; t < 1200; t += 13) {
                double[] c = DropletLook.centre(i, t, docked);
                assertEquals(cr[0], c[0], 0);
                assertEquals(cr[1], c[2], 0);
                for (int k = 0; k <= 8; k++) for (int j = 0; j < 10; j++) {
                    double[] p = DropletLook.skin(c, k, 8, j, 10);
                    assertTrue(
                        LookCells.clearance(B, p[0], p[1], p[2], open, 0.05) >= 0.05,
                        "droplet " + i + (docked ? " docked" : " resting") + " at " + t);
                }
            }
        }
        // docked they bob, each in its own rhythm; resting they lie still
        double[] a = DropletLook.centre(0, 10, true), b = DropletLook.centre(1, 10, true);
        assertTrue(a[1] != b[1]);
        assertArrayEquals(DropletLook.centre(2, 10, false), DropletLook.centre(2, 500, false), 0);
        assertArrayEquals(DropletLook.centre(3, 7, true), DropletLook.centre(3, 7 + 96 * 900, true), 1e-9);
    }

    /** A mirror shows the sky over a bright horizon over the dark ground, as the view bounces off it. */
    @Test
    void theChromeIsTheSkyAndTheGround() {
        // looking down on its top: the sky overhead
        near(DropletLook.SKY_HIGH, DropletLook.chrome(0, 1, 0, 0, -1, 0));
        // looking up at its underside: the ground below
        near(DropletLook.GROUND_LOW, DropletLook.chrome(0, -1, 0, 0, 1, 0));
        // looking at its side level: the horizon
        near(DropletLook.HORIZON, DropletLook.chrome(0, 0, -1, 0, 0, 1));
        // the sky paler towards the horizon; the ground dark just under it
        int sky = lum(DropletLook.chrome(0, 1, 0, 0, -1, 0)), low = lum(DropletLook.chrome(0, 0.2, -1, 0, 0, 1));
        assertTrue(low > sky, low + " over " + sky);
        assertTrue(lum(DropletLook.chrome(0, -0.06, -1, 0, 0, 1)) < lum(DropletLook.HORIZON));
        assertTrue(lum(DropletLook.chrome(0, -1, 0, 0, 1, 0)) < sky, "the ground darker than the sky");
    }

    private static int lum(int rgb) {
        return (rgb >> 16 & 0xFF) + (rgb >> 8 & 0xFF) + (rgb & 0xFF);
    }

    /** Two colours alike to within a step in each channel (the gradient rounds down). */
    private static void near(int want, int got) {
        for (int shift = 0; shift <= 16; shift += 8)
            assertTrue(Math.abs((want >> shift & 0xFF) - (got >> shift & 0xFF)) <= 1, Integer.toHexString(got));
    }

    @Test
    void theCountsAreTheDrawing() {
        assertEquals(8 * 10 + 2, DropletLook.dropletQuads(ModuleLook.NEAR));
        assertEquals(3 * 5 + 2, DropletLook.dropletQuads(ModuleLook.MID));
        assertEquals(1, DropletLook.dropletQuads(ModuleLook.FAR));
        assertEquals(48, DropletLook.canopyQuads(ModuleLook.NEAR));
        assertEquals(8, DropletLook.canopyQuads(ModuleLook.MID));
        assertEquals(8 * 82 + 48, DropletLook.quads(ModuleLook.NEAR));
        assertEquals(0, DropletLook.landmarkQuads());
    }

    /**
     * The renderer reads the drawn tiers' skins from a table made once (0.16.0) and turns them by the sway itself: at
     * every point of the near and mid skins and at any sway, the same point and normal {@link DropletLook#skin} works
     * out afresh.
     */
    @Test
    void theSkinTableIsTheSkin() {
        double[] out = new double[6];
        for (int tier : new int[] { ModuleLook.NEAR, ModuleLook.MID }) {
            int rings = DropletLook.RINGS[tier], sides = DropletLook.SIDES[tier];
            for (double sway : new double[] { 0, DropletLook.SWAY, -DropletLook.SWAY, 0.1 }) {
                double[] c = { 7.5, DropletLook.HOVER + 0.05, 12.5, sway };
                double cs = StrictMath.cos(sway), sn = StrictMath.sin(sway);
                for (int k = 0; k <= rings; k++) for (int j = 0; j <= sides; j++) {
                    DropletLook.skinInto(c, cs, sn, tier, k, j, out);
                    assertArrayEquals(
                        DropletLook.skin(c, k, rings, j, sides),
                        out,
                        1e-12,
                        "tier " + tier + ", sway " + sway + ", ring " + k + ", side " + j);
                }
            }
        }
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> DropletLook.skinInto(new double[4], 1, 0, ModuleLook.FAR, 0, 0, new double[6]),
            "far draws no skin");
    }
}
