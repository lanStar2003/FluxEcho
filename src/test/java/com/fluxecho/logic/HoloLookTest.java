package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The Holo Sand Table's drawing (0.15.0, CITY.md §4.11 Visuals): the ring light hangs where its segments were; the name
 * plate floats over the middle above it, clear of the dome; the campus's miniature, its ground lifted for the deepest
 * root and its edge scaled to the glass, stands over the table and under the ring light whatever stands on the campus
 * and however it turns; it keeps the nearest boxes; the table keeps CITY.md §1.3's budgets, counted as drawn.
 */
class HoloLookTest {

    private static final CityBuild B = HoloTableShape.BUILD;
    private static final String OPEN = LookCells.OPEN + HoloTableShape.DISSOLVES;

    @Test
    void theRingLightHangsWhereItsSegmentsWere() {
        assertEquals(9.5, HoloLook.RING_Y, 0);
        assertEquals(6, HoloLook.RING_RADIUS, 0);
        int segments = 0;
        for (int x = 0; x < B.width(); x++) for (int z = 0; z < B.depth(); z++) {
            if (B.cell(x, HoloTableShape.RING_Y, z) != 'R') continue;
            segments++;
            double d = Math.hypot(x + 0.5 - HoloLook.CX, z + 0.5 - HoloLook.CZ);
            assertTrue(Math.abs(d - HoloLook.RING_RADIUS) <= 0.5 + 1e-9, "a segment on the ring: " + d);
        }
        assertTrue(segments > 0);
        // the ring of light runs through the segments' layer, clear of everything that stays
        for (int i = 0; i < 96; i++) {
            double[] p = HoloLook.onRing(2 * Math.PI * i / 96);
            assertTrue(LookCells.clearance(B, p[0], HoloLook.RING_Y, p[1], OPEN, 2) >= 2, "ring at " + i);
        }
        // an even glow idle, a highlight running round powered, once every RING_RUN_T ticks
        assertEquals(1, HoloLook.ringLight(1.3, 55, false), 0);
        float low = Float.MAX_VALUE, high = 0;
        for (int i = 0; i < 64; i++) {
            float k = HoloLook.ringLight(2 * Math.PI * i / 64, 40, true);
            low = Math.min(low, k);
            high = Math.max(high, k);
            assertEquals(k, HoloLook.ringLight(2 * Math.PI * i / 64, 40 + HoloLook.RING_RUN_T, true), 1e-5);
        }
        assertTrue(low >= 1 && high > 1.7, low + " .. " + high);
    }

    @Test
    void thePlateFloatsOverTheMiddleAboveTheRingLight() {
        List<double[]> plates = ModuleLook.plates(B);
        assertEquals(1, plates.size());
        double[] p = plates.get(0);
        assertArrayEquals(HoloLook.plate(), p, 0);
        assertEquals(HoloLook.CX, p[0], 0);
        assertEquals(HoloLook.CZ, p[2], 0);
        assertTrue(
            p[1] > HoloLook.RING_Y + 2 && p[1] < HoloTableShape.LANTERN_Y,
            "over the ring light, under the lantern");
        assertTrue(LookCells.clearance(B, p[0], p[1], p[2], OPEN, 2) >= 2, "in the dome's open middle");
        // the table's console is off the middle, so the centrally planned rule does not hold for it
        assertTrue(!ModuleLook.central(B));
        // the Sophon Array keeps the pavilions' rule: over its central console, under the oculus
        assertTrue(ModuleLook.central(SophonArrayShape.BUILD));
        double[] s = ModuleLook.plates(SophonArrayShape.BUILD)
            .get(0);
        assertArrayEquals(new double[] { 15.5, SophonArrayShape.CTRL_Y + 3.6, 15.5 }, s, 1e-9);
        assertTrue(s[1] < SophonArrayShape.ROOF_Y, "under the roof");
        assertTrue(LookCells.clearance(SophonArrayShape.BUILD, s[0], s[1], s[2], LookCells.OPEN, 0.3) >= 0.3);
    }

    @Test
    void theMiniatureStandsOverTheGlassUnderTheRingLight() {
        for (boolean outer : new boolean[] { false, true }) {
            HoloLook.Model m = city(outer, 26);
            assertEquals(HoloLook.MINI_R / (outer ? 97.5 : 64.5), m.scale, 1e-12);
            assertEquals(HoloLook.TABLE_TOP + HoloLook.LIFT + 57 * m.scale, m.ground, 1e-12);
            // the ground's edge at MINI_R across its flat sides
            double[][] edge = m.rings.get(m.rings.size() - 1);
            assertEquals(HoloLook.MINI_R, m.scale * edge[0][0], 1e-9);
            for (int i = 0; i < 12; i++) {
                double turn = 2 * Math.PI * i / 12;
                double[] lo = { Double.MAX_VALUE }, hi = { -1 }, far = { 0 };
                for (HoloLook.Box b : all(m)) for (int k = 0; k < 8; k++) check(
                    m,
                    turn,
                    (k & 1) == 0 ? b.x0 : b.x1,
                    (k & 2) == 0 ? b.y0 : b.y1,
                    (k & 4) == 0 ? b.z0 : b.z1,
                    lo,
                    hi,
                    far);
                for (double[] l : m.legs) {
                    check(m, turn, l[0], l[1], l[2], lo, hi, far);
                    check(m, turn, l[3], l[4], l[5], lo, hi, far);
                }
                for (double[][] o : m.rings) for (double[] c : o) check(m, turn, c[0], 0, c[1], lo, hi, far);
                // over the glass, never below it; under the ring light's segments (y9); within the table's rim
                assertTrue(lo[0] >= HoloLook.TABLE_TOP + HoloLook.LIFT - 1e-9, "outer " + outer + ": low " + lo[0]);
                assertTrue(hi[0] < HoloTableShape.RING_Y - 0.5, "outer " + outer + ": high " + hi[0]);
                assertTrue(far[0] <= HoloTableShape.TABLE_R, "outer " + outer + ": out to " + far[0]);
            }
        }
    }

    @Test
    void theMiniatureKeepsTheNearestBoxesAndTurns() {
        List<HoloLook.Box> boxes = new ArrayList<>();
        for (int i = 30; i > 0; i--) boxes.add(new HoloLook.Box(2 * i, 0, 0, 2 * i + 1, 5, 1, i));
        HoloLook.Model m = HoloLook.model(3, false, 0x4FE3FF, boxes, null, null, 0, null);
        assertEquals(HoloLook.MINI_BOXES, m.boxes.size());
        for (int i = 0; i < m.boxes.size(); i++) assertEquals(i + 1, m.boxes.get(i).rgb, "the nearest first");
        assertTrue(m.sections.isEmpty() && m.legs.isEmpty(), "nothing on the axis");
        assertEquals(3, m.rings.size(), "the forum, the promenade ring, the ground's edge");
        // the nexus at its phase, on its base
        assertEquals(NexusShape.height(3) - 1, m.nexus.y1, 0);
        assertEquals(-1, m.nexus.y0, 0);
        // one turn every TURN_T ticks; the miniature keeps its size as it turns
        for (double t : new double[] { 0, 333.3, 98765.5 }) {
            double[] a = HoloLook.at(m, 40, 3, -20, HoloLook.turn(t)),
                b = HoloLook.at(m, 40, 3, -20, HoloLook.turn(t + HoloLook.TURN_T));
            assertArrayEquals(a, b, 1e-6);
            assertEquals(Math.hypot(40, 20) * m.scale, Math.hypot(a[0], a[2]), 1e-9);
            assertEquals(m.ground + 3 * m.scale, a[1], 1e-12);
        }
        assertTrue(HoloLook.turn(10) > HoloLook.turn(5), "turning");
        assertTrue(HoloLook.model(1, false, 0, null, null, null, 0, null).boxes.isEmpty(), "no modules, no boxes");
    }

    @Test
    void theTableKeepsItsBudget() {
        int[] budget = { 2000, 300, 64 };
        HoloLook.Model m = city(true, HoloLook.MINI_BOXES + 6);
        assertEquals(HoloLook.MINI_BOXES, m.boxes.size());
        assertEquals(2, m.sections.size());
        assertEquals(6, m.legs.size());
        for (int tier = ModuleLook.NEAR; tier <= ModuleLook.FAR; tier++) {
            int base = ModuleLook.baseQuads(B, HoloTableShape.UNITS, tier);
            for (HoloLook.Model mm : new HoloLook.Model[] { null, m, city(false, 30) }) {
                int own = HoloLook.quads(tier, mm);
                assertTrue(base + own <= budget[tier], "tier " + tier + ": " + base + " + " + own);
            }
        }
        // no sockets on the table: the shared drawing is the sweep, the plate and the plinth line
        int faces = ModuleLook.course(B)
            .size();
        assertEquals(17 + ModuleLook.PLATE_QUADS + 3 * faces, ModuleLook.baseQuads(B, HoloTableShape.UNITS, 0));
        assertEquals(96, HoloLook.ringQuads(ModuleLook.NEAR));
        assertEquals(24, HoloLook.ringQuads(ModuleLook.MID));
        assertEquals(16, HoloLook.ringQuads(ModuleLook.FAR));
        assertEquals(0, HoloLook.miniQuads(m, ModuleLook.FAR));
        assertEquals(16, HoloLook.quads(ModuleLook.FAR, m));
    }

    @Test
    void theCountIsTheDrawing() {
        HoloLook.Model m = city(true, HoloLook.MINI_BOXES);
        // every line of the miniature is shorter than 8 blocks: one quad each
        int near = HoloLook.GROUND_FILL + m.rings.size() * 8
            + (m.boxes.size() + 1) * (5 + 4)
            + m.sections.size() * (2 + 8)
            + m.legs.size()
            + HoloLook.BEAM_QUADS;
        assertEquals(near, HoloLook.miniQuads(m, ModuleLook.NEAR));
        int mid = HoloLook.GROUND_FILL + 8 + m.boxes.size() + 1 + 2 * m.sections.size();
        assertEquals(mid, HoloLook.miniQuads(m, ModuleLook.MID));
        assertEquals(97.5, m.rings.get(m.rings.size() - 1)[0][0], 0, "mid, the outer disc's edge alone");
    }

    /** A point of the miniature: the lowest and highest it reaches and how far out from the table's middle. */
    private static void check(HoloLook.Model m, double turn, double x, double y, double z, double[] lo, double[] hi,
        double[] far) {
        double[] p = HoloLook.at(m, x, y, z, turn);
        lo[0] = Math.min(lo[0], p[1]);
        hi[0] = Math.max(hi[0], p[1]);
        far[0] = Math.max(far[0], Math.hypot(p[0], p[2]));
    }

    private static List<HoloLook.Box> all(HoloLook.Model m) {
        List<HoloLook.Box> out = new ArrayList<>(m.boxes);
        out.add(m.nexus);
        out.addAll(m.sections);
        return out;
    }

    /**
     * A whole city as its miniature, facing +x (a ahead is +x, r is +z): the gatehouse, the three halls, the four
     * pavilions, with the outer ring the four outer diagonals and boxes on the flankers, the zenith's pod on its legs
     * at the full datum and the deepest root; the boxes as tall as the tallest building (33), up to {@code most} of
     * them.
     */
    private static HoloLook.Model city(boolean outer, int most) {
        List<HoloLook.Box> boxes = new ArrayList<>();
        boxes.add(box(23, 39, -15, 15, 31));
        for (int[] ax : new int[][] { { 0, 1 }, { -1, 0 }, { 0, -1 } }) boxes.add(axisBox(ax, 24, 58, -13, 13, 33));
        for (int[] c : new int[][] { { 36, 36 }, { 36, -36 }, { -36, 36 }, { -36, -36 } })
            boxes.add(box(c[0] - 12, c[0] + 12, c[1] - 12, c[1] + 12, 33));
        if (outer) {
            for (int[] c : new int[][] { { 62, 62 }, { 62, -62 }, { -62, 62 }, { -62, -62 } })
                boxes.add(box(c[0] - 15, c[0] + 15, c[1] - 15, c[1] + 15, 33));
            for (int[] ax : new int[][] { { 1, 0 }, { 0, 1 }, { -1, 0 }, { 0, -1 } }) {
                boxes.add(axisBox(ax, 64, 94, 6, 32, 33));
                boxes.add(axisBox(ax, 64, 94, -32, -6, 33));
            }
        }
        while (boxes.size() < most) boxes.add(box(-3, 3, 30, 36, 20));
        int datum = DomeGeneratorShape.FLOOR_UP;
        HoloLook.Box pod = new HoloLook.Box(
            -10.5,
            datum - 1,
            -10.5,
            10.5,
            datum - 1 + DomeGeneratorShape.HEIGHT,
            10.5,
            1);
        List<double[]> legs = new ArrayList<>();
        for (int[] f : DomeGeneratorShape.feet()) {
            double len = Math.hypot(f[0], f[1]);
            legs.add(
                new double[] { f[0], 0, f[1], f[0] / len * DomeGeneratorShape.LEG_HEAD, datum - 1,
                    f[1] / len * DomeGeneratorShape.LEG_HEAD });
        }
        HoloLook.Box root = new HoloLook.Box(-11.5, -57, -11.5, 11.5, -1, 11.5, 2);
        return HoloLook.model(5, outer, 0x4FE3FF, boxes, pod, legs, 1, root);
    }

    private static HoloLook.Box box(int a0, int a1, int r0, int r1, int h) {
        return new HoloLook.Box(a0 - 0.5, 0, r0 - 0.5, a1 + 0.5, h, r1 + 0.5, 0xFFFFFF);
    }

    /** A box {@code t0..t1} along a site's axis and {@code s0..s1} across it (clockwise, as the sites count). */
    private static HoloLook.Box axisBox(int[] ax, int t0, int t1, int s0, int s1, int h) {
        double[][] c = { { t0, s0 }, { t1, s1 } };
        double a0 = c[0][0] * ax[0] - c[0][1] * ax[1], r0 = c[0][0] * ax[1] + c[0][1] * ax[0],
            a1 = c[1][0] * ax[0] - c[1][1] * ax[1], r1 = c[1][0] * ax[1] + c[1][1] * ax[0];
        return new HoloLook.Box(
            Math.min(a0, a1) - 0.5,
            0,
            Math.min(r0, r1) - 0.5,
            Math.max(a0, a1) + 0.5,
            h,
            Math.max(r0, r1) + 0.5,
            0xFFFFFF);
    }

    /**
     * The miniature's module boxes come from the campus view (0.15.0): a Sophon Array the view lists as built on O1,
     * with no tile loaded, is a box of its 31 x 31 octagon's footprint round the outer diagonal's centre, as high as
     * the building, in its colour, for every front; a loaded tile's bounds take its place when the client has it.
     */
    @Test
    void aBuiltModuleIsABoxFromTheCampusView() {
        com.fluxecho.campus.ModuleSpec spec = com.fluxecho.city.CityModule.sophonArray();
        int cx = 300, y0 = 70, cz = -90;
        int[][] fronts = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
        for (int[] f : fronts) {
            int[] fp = spec.footprint(11, cx, cz, f[0], f[1], 0);
            HoloLook.Box b = HoloLook.siteBox(fp, null, cx, y0, cz, spec.height, spec.colour);
            int[] d = CampusPlan.toWorld(OuterRing.diagCentre(11)[0], OuterRing.diagCentre(11)[1], f[0], f[1]);
            assertEquals(d[0], (b.x0 + b.x1) / 2, 1e-9, "on O1's centre");
            assertEquals(d[1], (b.z0 + b.z1) / 2, 1e-9);
            assertEquals(OuterRing.DIAG_SIZE, b.x1 - b.x0, 1e-9);
            assertEquals(OuterRing.DIAG_SIZE, b.z1 - b.z0, 1e-9);
            assertEquals(-1, b.y0, 1e-9, "from the forum's floor");
            assertEquals(SophonArrayShape.HEIGHT - 1, b.y1, 1e-9);
            assertEquals(0xFF8CE6, b.rgb);
            // the loaded structure's own bounds, a block off the footprint, take its place
            int[] bounds = { fp[0] + 1, y0, fp[1] + 1, fp[2] + 1, y0 + 20, fp[3] + 1 };
            HoloLook.Box t = HoloLook.siteBox(fp, bounds, cx, y0, cz, spec.height, 0x123456);
            assertEquals(b.x0 + 1, t.x0, 1e-9);
            assertEquals(b.x1 + 1, t.x1, 1e-9);
            assertEquals(20, t.y1, 1e-9);
            assertEquals(0x123456, t.rgb);
            // the model keeps it
            List<HoloLook.Box> boxes = new ArrayList<>();
            boxes.add(b);
            HoloLook.Model m = HoloLook.model(3, true, 0, boxes, null, null, 0, null);
            assertEquals(1, m.boxes.size());
        }
    }
}
