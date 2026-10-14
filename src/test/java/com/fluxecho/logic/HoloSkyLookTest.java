package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The Holo Sand Table's miniature of the sky ring and the deep root (0.16.0): a sky module is a box up at the pod's
 * deck over its platform, for every front; its catwalk two lines from the pod's rim to the platform's front; the deep
 * root's chamber a wider section under the well's; the miniature still stands under the ring light and over the glass
 * with the sky ring on it, and the table keeps its budgets with every line counted.
 */
class HoloSkyLookTest {

    private static final int[][] FRONTS = { { 1, 0 }, { 0, 1 }, { -1, 0 }, { 0, -1 } };
    private static final int[] SKY = { 30, 32, 34, 36 };

    @Test
    void aSkyModuleIsABoxUpAtThePodsDeck() {
        int datum = DomeGeneratorShape.FLOOR_UP;
        for (int[] f : FRONTS) for (int site : SKY) {
            HoloLook.Box b = HoloLook.skyBox(site, f[0], f[1], datum, 15, 7);
            int[] c = SkyRing.centre(site), w = CampusPlan.toWorld(c[0], c[1], f[0], f[1]);
            assertEquals(w[0], (b.x0 + b.x1) / 2, 1e-12);
            assertEquals(w[1], (b.z0 + b.z1) / 2, 1e-12);
            assertEquals(SkyRing.SIZE, b.x1 - b.x0, 1e-12);
            assertEquals(SkyRing.SIZE, b.z1 - b.z0, 1e-12);
            // its deck's block at Y0 + F, the forum's floor at Y0 + 1
            assertEquals(datum - 1, b.y0, 0);
            assertEquals(datum - 1 + 15, b.y1, 0);
            assertEquals(7, b.rgb);
            // radial 21.5 to 42.5 out on its axis
            int[] ax = SkyRing.axis(site), a = CampusPlan.toWorld(ax[0], ax[1], f[0], f[1]);
            double near = Math.min(a[0] * b.x0 + a[1] * b.z0, a[0] * b.x1 + a[1] * b.z1);
            assertEquals(SkyRing.FRONT - 0.5, near, 1e-9);
        }
        assertThrows(IllegalArgumentException.class, () -> HoloLook.skyBox(31, 1, 0, 40, 10, 0), "a berth");
        assertThrows(IllegalArgumentException.class, () -> HoloLook.skyBox(2, 1, 0, 40, 10, 0), "a hall site");
    }

    @Test
    void theCatwalkRunsFromThePodToThePlatform() {
        for (int[] f : FRONTS) for (int site : SKY) {
            List<double[]> walks = HoloLook.walks(site, f[0], f[1], 36);
            assertEquals(2, walks.size());
            int[] ax = SkyRing.axis(site), a = CampusPlan.toWorld(ax[0], ax[1], f[0], f[1]);
            double[] across = { -a[1], a[0] };
            double side = 0;
            for (double[] l : walks) {
                assertEquals(36, l[1], 0);
                assertEquals(36, l[4], 0);
                // from the pod's rim out to the platform's front, along the axis
                assertEquals(SkyRing.CATWALK_FROM - 0.5, a[0] * l[0] + a[1] * l[2], 1e-9);
                assertEquals(SkyRing.CATWALK_TO + 0.5, a[0] * l[3] + a[1] * l[5], 1e-9);
                double s0 = across[0] * l[0] + across[1] * l[2], s1 = across[0] * l[3] + across[1] * l[5];
                assertEquals(s0, s1, 1e-9, "parallel to the axis");
                assertEquals(SkyRing.CATWALK_HALF + 0.5, Math.abs(s0), 1e-9, "the deck's outer edge");
                side += s0;
            }
            assertEquals(0, side, 1e-9, "one each side");
        }
        assertThrows(IllegalArgumentException.class, () -> HoloLook.walks(33, 1, 0, 40));
    }

    @Test
    void theDeepRootsChamberIsAWiderSectionUnderTheWells() {
        HoloLook.Box[] s = HoloLook.rootSections(-40, -1, 13, 5);
        assertEquals(RootShape.WELL_R + 0.5, s[0].x1, 0);
        assertEquals(-27, s[0].y0, 0);
        assertEquals(-1, s[0].y1, 0);
        assertEquals(RootShape.CHAMBER_R + 0.5, s[1].x1, 0);
        assertEquals(-40, s[1].y0, 0);
        assertEquals(-27, s[1].y1, 0);
        HoloLook.Box[] in = HoloLook.rootSections(-27, -1, 0, 5);
        assertEquals(-27, in[0].y0, 0);
        assertNull(in[1], "an in-well form has no chamber");
        // the model shows the chamber under the well, and only with a root
        HoloLook.Model m = HoloLook.model(4, true, 0, null, null, null, 0, s[0], s[1], null);
        assertEquals(2, m.sections.size());
        assertTrue(HoloLook.model(4, true, 0, null, null, null, 0, null, s[1], null).sections.isEmpty());
    }

    /**
     * A whole city at phase IV with the sky ring (its four modules up at the deck, their catwalks), the pod floating
     * and the deep root's chamber: the miniature stands over the glass and under the ring light, inner or outer, and
     * the table keeps its budgets, every line counted.
     */
    @Test
    void theMiniatureWithTheSkyRingStaysUnderTheRingLight() {
        for (boolean outer : new boolean[] { false, true }) {
            HoloLook.Model m = sky(outer);
            assertEquals(8, m.walks.size());
            assertEquals(3, m.sections.size());
            for (int i = 0; i < 12; i++) {
                double turn = 2 * Math.PI * i / 12, lo = Double.MAX_VALUE, hi = -1, far = 0;
                List<HoloLook.Box> all = new ArrayList<>(m.boxes);
                all.add(m.nexus);
                all.addAll(m.sections);
                for (HoloLook.Box b : all) for (int k = 0; k < 8; k++) {
                    double[] p = HoloLook.at(
                        m,
                        (k & 1) == 0 ? b.x0 : b.x1,
                        (k & 2) == 0 ? b.y0 : b.y1,
                        (k & 4) == 0 ? b.z0 : b.z1,
                        turn);
                    lo = Math.min(lo, p[1]);
                    hi = Math.max(hi, p[1]);
                    far = Math.max(far, Math.hypot(p[0], p[2]));
                }
                assertTrue(lo >= HoloLook.TABLE_TOP + HoloLook.LIFT - 1e-9, "over the glass: " + lo);
                assertTrue(hi < HoloTableShape.RING_Y - 0.5, "under the ring light: " + hi);
                assertTrue(far <= HoloTableShape.TABLE_R, "within the rim: " + far);
            }
            int[] budget = { 2000, 300, 64 };
            for (int tier = ModuleLook.NEAR; tier <= ModuleLook.FAR; tier++) {
                int base = ModuleLook.baseQuads(HoloTableShape.BUILD, HoloTableShape.UNITS, tier),
                    own = HoloLook.quads(tier, m);
                assertTrue(base + own <= budget[tier], "tier " + tier + ": " + base + " + " + own);
            }
            // every line is shorter than 8 blocks at the miniature's scale: a quad a catwalk's edge; the pod's section
            // two planes with their four edges across and four up, a quad each
            HoloLook.Model bare = HoloLook
                .model(5, outer, 0x4FE3FF, m.boxes, null, null, 1, m.sections.get(1), m.sections.get(2), null);
            assertEquals(m.scale, bare.scale, 0);
            assertEquals(
                HoloLook.miniQuads(bare, ModuleLook.NEAR) + m.walks.size() + 2 + 8,
                HoloLook.miniQuads(m, ModuleLook.NEAR));
            assertEquals(HoloLook.miniQuads(bare, ModuleLook.MID) + 2, HoloLook.miniQuads(m, ModuleLook.MID));
        }
    }

    /** The city of HoloLookTest at phase IV, the sky ring's four modules on it, the pod floating, the deep root. */
    private static HoloLook.Model sky(boolean outer) {
        int datum = DomeGeneratorShape.FLOOR_UP;
        List<HoloLook.Box> boxes = new ArrayList<>();
        List<double[]> walks = new ArrayList<>();
        int[] heights = { WeatherStationShape.HEIGHT, SkySailsShape.HEIGHT, ObservatoryShape.HEIGHT,
            DropletHangarShape.HEIGHT };
        for (int i = 0; i < SKY.length; i++) {
            boxes.add(HoloLook.skyBox(SKY[i], 1, 0, datum, heights[i], 0xFFFFFF));
            walks.addAll(HoloLook.walks(SKY[i], 1, 0, datum));
        }
        for (int[] c : new int[][] { { 36, 36 }, { 36, -36 }, { -36, 36 }, { -36, -36 } })
            boxes.add(new HoloLook.Box(c[0] - 12.5, 0, c[1] - 12.5, c[0] + 12.5, 33, c[1] + 12.5, 0xFFFFFF));
        HoloLook.Box pod = new HoloLook.Box(
            -10.5,
            datum - 1,
            -10.5,
            10.5,
            datum - 1 + DomeGeneratorShape.HEIGHT,
            10.5,
            1);
        HoloLook.Box[] root = HoloLook.rootSections(-41, -1, 13, 2);
        return HoloLook.model(5, outer, 0x4FE3FF, boxes, pod, null, 1, root[0], root[1], walks);
    }
}
