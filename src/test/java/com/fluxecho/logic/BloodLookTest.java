package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The Blood Covenant Altar's drawing (0.13.0, CITY.md §4.4 Visuals): the orb floats over the altar between y12 and
 * y16 in the funnel's air, its strings hang from the oculus's lit rim, the droplets fall clear onto the altar's top;
 * the cornice lines lie on the cornices' outer faces and their octagons round them; the circle's glow lies on its lit
 * tiles and the pulse never shows inside a block; the cradles' orbs float over their sockets; the heart beats twice a
 * beat and every motion repeats; the altar keeps CITY.md §1.3's budgets.
 */
class BloodLookTest {

    private static final CityBuild B = BloodCovenantShape.BUILD;

    @Test
    void theOrbFloatsOverTheAltarUnderTheOculus() {
        double most = BloodLook.ORB_R * (1 + BloodLook.ORB_SWELL);
        for (boolean on : new boolean[] { false, true }) for (int j = 0; j < 200; j++) {
            double r = BloodLook.orbRadius(j * 0.7, on);
            assertTrue(r >= BloodLook.ORB_R - 1e-9 && r <= most + 1e-9);
        }
        assertTrue(BloodLook.ORB_Y - most >= BloodCovenantShape.SLAB2_Y, "from y12");
        assertTrue(BloodLook.ORB_Y + most <= BloodCovenantShape.ROOF_Y, "to y16, under the oculus");
        for (int i = 0; i <= 12; i++) for (int j = 0; j < 24; j++) {
            double[] p = BloodLook.orbPoint(i, 12, j, 24, most);
            assertTrue(LookCells.clearance(B, p[0], p[1], p[2], LookCells.OPEN, 1.5) >= 0.5, "in the funnel's air");
        }
        assertEquals(BloodCovenantShape.MID + 0.5, BloodLook.AXIS, 0, "over the altar");
    }

    @Test
    void theStringsHangFromTheRimToTheOrb() {
        for (boolean on : new boolean[] { false, true }) for (int j = 0; j < 30; j++) {
            double r = BloodLook.orbRadius(j * 1.9, on);
            for (int k = 0; k < BloodLook.STRINGS; k++) {
                double[] top = BloodLook.stringPoint(k, 0, r);
                assertEquals('L', LookCells.at(B, top[0], top[1] + 0.05, top[2]), "under the oculus's lit rim");
                for (int i = 1; i <= 20; i++) {
                    double[] p = BloodLook.stringPoint(k, i / 20.0, r);
                    assertTrue(LookCells.clearance(B, p[0], p[1], p[2], LookCells.OPEN, 1) >= 0.02, "in the air");
                }
                double[] end = BloodLook.stringPoint(k, 1, r);
                double d = Math
                    .sqrt(sq(end[0] - BloodLook.AXIS) + sq(end[1] - BloodLook.ORB_Y) + sq(end[2] - BloodLook.AXIS));
                assertEquals(r, d, 1e-9, "on the orb");
                double u = BloodLook.drop(k, j * 3.3);
                assertTrue(u >= 0 && u < 1);
            }
            for (int k = 0; k < BloodLook.DRIPS; k++) {
                double[] top = BloodLook.dripTop(k, r);
                double y = top[1] - BloodLook.dripLength(k, j * 2.1);
                assertTrue(LookCells.clearance(B, top[0], y, top[2], LookCells.OPEN, 1) >= 0.5, "a drip in the air");
                assertTrue(top[1] < BloodLook.ORB_Y, "under the orb");
            }
        }
    }

    @Test
    void theDropletsFallOntoTheAltar() {
        int x = BloodCovenantShape.MID, z = BloodCovenantShape.MID;
        assertEquals(ShapeKit.CONTROLLER, B.cell(x, (int) BloodLook.ALTAR_TOP - 1, z), "the altar's top");
        for (int y = (int) BloodLook.ALTAR_TOP; y < BloodLook.ORB_Y; y++)
            assertEquals(ShapeKit.NONE, B.cell(x, y, z), "the air they fall through at y" + y);
        for (int k = 0; k < BloodLook.DROPLETS; k++) {
            for (int j = 0; j < BloodLook.DROP_T * 4; j++) {
                double t = j * 0.25 + k * 0.01;
                double[] d = BloodLook.droplet(k, t);
                assertTrue(d[0] >= BloodLook.ALTAR_TOP - 1e-9, "never into the altar");
                assertTrue(d[0] <= BloodLook.ORB_Y - BloodLook.ORB_R + 1e-9, "from under the orb");
                assertTrue(d[0] + d[2] <= BloodLook.ORB_Y, "its streak under the orb's middle");
                assertTrue(d[2] > 0 && d[1] >= 0 && d[1] <= 1);
                double[] same = BloodLook.droplet(k, t + BloodLook.DROP_T);
                assertEquals(d[0], same[0], 1e-9, "the same again a fall later");
            }
            // each leaves the orb with a beat's strong stroke and falls faster and faster
            double start = 1e-6 + BloodLook.LUB * BloodLook.BEAT_T - k * BloodLook.DROP_T / (double) BloodLook.DROPLETS;
            double u0 = BloodLook.droplet(k, start)[0], u1 = BloodLook.droplet(k, start + BloodLook.DROP_T * 0.25)[0],
                u2 = BloodLook.droplet(k, start + BloodLook.DROP_T * 0.5)[0];
            assertEquals(BloodLook.ORB_Y - BloodLook.ORB_R, u0, 1e-9, "leaving the orb");
            assertTrue(u0 - u1 < u1 - u2, "speeding up");
            assertTrue(BloodLook.heartbeat(start, true) > 0.95, "with the strong stroke");
        }
    }

    /**
     * The name plate floats on the droplets' axis over the central console, so the droplets pass behind it: no streak
     * or glow is ever drawn in the plate's band (its pane, a fifth of a block or so each way, with room round it), and
     * a droplet still shows over the plate as it leaves the orb and under it as it lands on the altar.
     */
    @Test
    void theDropletsPassBehindTheNamePlate() {
        double plate = ModuleLook.plates(B)
            .get(0)[1];
        assertEquals(plate, BloodLook.PLATE_Y, 1e-12);
        assertEquals(BloodCovenantShape.CTRL_Y + 3.6, plate, 1e-9, "over the central console");
        // the plate's pane: a 1.5-scale title and a caption, 29 pixels at 1/64 block
        double pane = (Math.ceil(9 * 1.5) + 15) / 64 / 2;
        assertTrue(BloodLook.PLATE_CLEAR >= pane + BloodLook.DROP_GLOW, "the band holds the pane");
        assertTrue(
            plate - BloodLook.PLATE_CLEAR > BloodLook.ALTAR_TOP
                && plate + BloodLook.PLATE_CLEAR < BloodLook.ORB_Y - BloodLook.ORB_R,
            "the plate lies on the fall line");
        boolean over = false, under = false;
        for (int k = 0; k < BloodLook.DROPLETS; k++) for (int j = 0; j < BloodLook.DROP_T * 8; j++) {
            double[] d = BloodLook.droplet(k, j * 0.125 + k * 0.01);
            if (d[1] <= 0) continue;
            double lo = d[0] - BloodLook.DROP_GLOW, hi = d[0] + d[2];
            assertTrue(lo > plate + 0.35 || hi < plate - 0.35, "a droplet across the plate at y" + d[0]);
            if (d[1] > 0.5 && lo > plate) over = true;
            if (d[1] > 0.3 && hi < plate) under = true;
        }
        assertTrue(over, "seen falling over the plate");
        assertTrue(under, "seen landing under it");
    }

    @Test
    void theCorniceLinesLieOnTheCornicesOutsides() {
        int[] want = { 100, 76, 52 };
        for (int c = 0; c < BloodLook.CORNICES.length; c++) {
            int y = BloodLook.CORNICES[c];
            List<int[]> faces = BloodLook.cornice(y);
            assertEquals(want[c], faces.size(), "the cornice at y" + y);
            boolean[] seen = new boolean[faces.size()];
            double[][] hull = BloodLook.hull(y);
            for (int[] f : faces) {
                assertEquals('n', B.cell(f[0], y, f[1]), "a cornice cell");
                assertEquals(ShapeKit.NONE, B.cell(f[0] + f[2], y, f[1] + f[3]), "facing the open air");
                seen[f[4]] = true;
                // the face's outer edge lies inside its octagon, at most a chamfer step's corner in from it
                double ex = f[0] + 0.5 + f[2] * 0.5, ez = f[1] + 0.5 + f[3] * 0.5;
                double gap = inside(hull, ex, ez);
                assertTrue(gap >= 0 && gap <= 0.75, "within the octagon at y" + y + ": " + gap);
            }
            for (boolean s : seen) assertTrue(s, "every place round it once");
            // the octagon's flat sides lie just off the flat faces
            assertEquals(
                BloodLook.HULL_OUT,
                inside(hull, BloodLook.AXIS + (y == 7 ? 12.5 : y == 12 ? 9.5 : 6.5), BloodLook.AXIS),
                1e-9);
            for (int[] f : faces) for (double s : new double[] { 0, 0.5, 1 }) {
                double v = BloodLook.seep(f[4], faces.size(), s * BloodLook.SEEP_T, c);
                assertTrue(v >= 0 && v <= 1);
            }
        }
    }

    /**
     * How far inside the convex octagon {@code hull} the point {@code (x, z)} lies: the least distance to its sides,
     * negative outside.
     */
    private static double inside(double[][] hull, double x, double z) {
        double best = Double.MAX_VALUE;
        int n = hull.length;
        // the corners run round one way: the side's inward normal is the same side of every edge
        double cx = 0, cz = 0;
        for (double[] p : hull) {
            cx += p[0] / n;
            cz += p[1] / n;
        }
        for (int i = 0; i < n; i++) {
            double[] a = hull[i], b = hull[(i + 1) % n];
            double ex = b[0] - a[0], ez = b[1] - a[1], l = Math.hypot(ex, ez);
            double nx = -ez / l, nz = ex / l;
            if ((cx - a[0]) * nx + (cz - a[1]) * nz < 0) {
                nx = -nx;
                nz = -nz;
            }
            best = Math.min(best, (x - a[0]) * nx + (z - a[1]) * nz);
        }
        return best;
    }

    @Test
    void theGlowLiesOnTheRitualCirclesTiles() {
        List<int[]> tiles = BloodLook.ritual();
        int lit = 0;
        for (int x = 0; x < BloodCovenantShape.WIDTH; x++)
            for (int z = 0; z < BloodCovenantShape.DEPTH; z++) if (B.cell(x, 0, z) == '+') lit++;
        assertTrue(tiles.size() > lit / 2 && tiles.size() <= lit, tiles.size() + " of " + lit + " lit tiles");
        boolean spoke = false, ring = false;
        for (int[] c : tiles) {
            assertEquals('+', B.cell(c[0], 0, c[1]), "a lit tile");
            assertEquals(ShapeKit.NONE, B.cell(c[0], 1, c[1]), "open over it");
            int du = c[0] - BloodCovenantShape.MID, dv = c[1] - BloodCovenantShape.MID;
            if (Math.abs(du) == Math.abs(dv) && ShapeKit.onorm(du, dv) < 8) spoke = true;
            if (ShapeKit.onorm(du, dv) == 8) ring = true;
        }
        assertTrue(spoke && ring, "the spokes and the circle");
        assertTrue(BloodLook.RITUAL_Y > 1 && BloodLook.RITUAL_Y < 1.05, "just over the floor's top");
    }

    @Test
    void thePulseRunsOutWithEachBeatAndNeverShowsInABlock() {
        double prev = -1;
        for (int j = 0; j < BloodLook.BEAT_T; j++) {
            double t = j + BloodLook.LUB * BloodLook.BEAT_T + 0.001;
            double r = BloodLook.pulseRadius(t);
            assertTrue(r >= BloodLook.PULSE_FROM && r <= BloodLook.PULSE_TO);
            assertTrue(r > prev, "outward through the beat");
            prev = r;
            assertEquals(r, BloodLook.pulseRadius(t + BloodLook.BEAT_T), 1e-9);
            for (int k = 0; k < BloodLook.SPOKES; k++) {
                double[] m = BloodLook.pulseMote(k, t);
                assertEquals(Math.abs(m[0] - BloodLook.AXIS), Math.abs(m[2] - BloodLook.AXIS), 1e-9, "on a diagonal");
                if (m[3] > 0) assertEquals(ShapeKit.NONE, LookCells.at(B, m[0], m[1], m[2]), "in the air");
            }
        }
        // under a cradle's plinth the glow is hidden
        boolean hidden = false;
        for (int j = 0; j < 200; j++) {
            double[] m = BloodLook.pulseMote(0, j * BloodLook.BEAT_T / 200.0);
            if (LookCells.at(B, m[0], 1, m[2]) != ShapeKit.NONE) {
                assertEquals(0, m[3], 0);
                hidden = true;
            }
        }
        assertTrue(hidden, "the spoke runs under a cradle");
    }

    @Test
    void theCradlesOrbsFloatOverTheirSockets() {
        assertEquals(
            8,
            BloodLook.cradles()
                .size());
        for (int i = 0; i < 8; i++) {
            int[] s = BloodLook.cradles()
                .get(i);
            assertEquals('o', B.cell(s[0], s[1], s[2]), "a socket");
            assertEquals(
                BloodCovenantShape.UNITS.unitOfSlot(BloodLook.cradleSlot(i)),
                BloodCovenantShape.UNITS.unitOfCell(s[0], s[1], s[2]),
                "its own slot");
            for (int j = 0; j < 50; j++) {
                double[] c = BloodLook.cradleOrb(i, j * 9.1);
                assertEquals(s[0] + 0.5, c[0], 0);
                assertEquals(s[2] + 0.5, c[2], 0);
                // the orb and the ring round it, in the air of the cell over the socket
                double reach = BloodLook.CRADLE_ORB + 0.14;
                assertTrue(c[1] - reach > s[1] + 1, "over the socket's top");
                for (double[] o : new double[][] { { 0, -reach, 0 }, { 0, reach, 0 }, { reach, 0, 0 }, { -reach, 0, 0 },
                    { 0, 0, reach }, { 0, 0, -reach } })
                    assertEquals(ShapeKit.NONE, LookCells.at(B, c[0] + o[0], c[1] + o[1], c[2] + o[2]), "in the air");
            }
        }
    }

    @Test
    void theHeartBeatsTwiceABeat() {
        double t0 = 1000 * BloodLook.BEAT_T;
        double lub = BloodLook.heartbeat(t0 + BloodLook.LUB * BloodLook.BEAT_T, true),
            dub = BloodLook.heartbeat(t0 + BloodLook.DUB * BloodLook.BEAT_T, true),
            between = BloodLook.heartbeat(t0 + 0.18 * BloodLook.BEAT_T, true),
            rest = BloodLook.heartbeat(t0 + 0.7 * BloodLook.BEAT_T, true);
        assertTrue(lub > 0.95, "the strong stroke");
        assertTrue(dub > 0.5 && dub < lub, "the softer one");
        assertTrue(between < 0.2 && rest < 0.05, "and rest between");
        for (int j = 0; j < 100; j++) {
            double t = j * 1.37;
            for (boolean on : new boolean[] { false, true }) {
                double v = BloodLook.heartbeat(t, on);
                assertTrue(v >= 0 && v <= 1);
            }
            assertEquals(BloodLook.heartbeat(t, true), BloodLook.heartbeat(t + BloodLook.BEAT_T, true), 1e-9);
            assertEquals(BloodLook.heartbeat(t, false), BloodLook.heartbeat(t + BloodLook.BREATH_T, false), 1e-9);
            for (int i = 0; i < 8; i++) assertEquals(
                Math.cos(BloodLook.cradleTurn(i, t)),
                Math.cos(BloodLook.cradleTurn(i, t + BloodLook.CRADLE_TURN_T)),
                1e-9);
        }
    }

    @Test
    void theAltarKeepsItsBudget() {
        // the shared drawing as drawn: the forming sweep through the 25 x 25 box is a plane and four rims of four
        // pieces
        assertEquals(17, ModuleLook.sweepQuads(B));
        assertEquals(17, ModuleLook.baseQuads(B, BloodCovenantShape.UNITS, ModuleLook.FAR));
        int[] budget = { 2000, 300, 64 };
        for (int tier = ModuleLook.NEAR; tier <= ModuleLook.FAR; tier++) {
            int base = ModuleLook.baseQuads(B, BloodCovenantShape.UNITS, tier);
            for (boolean docked : new boolean[] { false, true }) for (boolean on : new boolean[] { false, true }) {
                if (on && !docked) continue;
                int own = BloodLook.quads(tier, docked, on);
                assertTrue(
                    base + own <= budget[tier],
                    "tier " + tier + (docked ? " docked" : "") + (on ? " powered" : "") + ": " + base + " + " + own);
            }
        }
        // far: the orb's two glows and the three octagons, each side a line cut into pieces of at most 8 blocks
        int hulls = 0;
        for (int y : BloodLook.CORNICES) hulls += BloodLook.hullQuads(y);
        assertEquals(16 + 12 + 8, hulls);
        assertEquals(2 + hulls, BloodLook.quads(ModuleLook.FAR, true, true));
        assertEquals(2, BloodLook.quads(ModuleLook.FAR, false, false));
    }

    private static double sq(double v) {
        return v * v;
    }
}
