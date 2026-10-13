package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The Sophon Array's drawing (0.15.0, CITY.md §4.10 Visuals): the geodesic frame lies on the lattice sphere it
 * replaces, clear of every block that stays; the struts rise from the podium's roof to the sphere; the light point
 * unfolds into a turning icosahedron inside the sphere and the circuit canopy spreads over the sky above the building,
 * its traces running out from the die without crossing to vias short of its rim, a pulse flowing along each; every
 * motion repeats; the array keeps CITY.md §1.3's budgets and its landmark its 400 quads at any distance, counted as
 * drawn.
 */
class SophonLookTest {

    private static final CityBuild B = SophonArrayShape.BUILD;
    private static final String OPEN = LookCells.OPEN + SophonArrayShape.DISSOLVES;

    @Test
    void theFrameLiesOnTheLatticeItReplaces() {
        // the lattice's middle: the centre of its centre cell, radius 12 (CITY.md §4.10 "radius 12, centre y20")
        assertEquals(15.5, SophonLook.CX, 0);
        assertEquals(20.5, SophonLook.CY, 0);
        assertEquals(15.5, SophonLook.CZ, 0);
        assertEquals(12, SophonLook.RADIUS, 0);
        for (int freq : new int[] { 1, 2, 3 }) {
            List<double[]> f = SophonLook.frame(freq);
            assertEquals(30 * freq * freq, f.size(), "a subdivided icosahedron's edges, no edge twice");
            Set<String> points = new HashSet<>();
            for (double[] e : f) {
                for (int k = 0; k < 2; k++) {
                    double x = e[3 * k], y = e[3 * k + 1], z = e[3 * k + 2];
                    assertEquals(12, dist(x, y, z), 1e-9, "on the sphere");
                    points.add(Math.round(x * 1e6) + "," + Math.round(y * 1e6) + "," + Math.round(z * 1e6));
                }
                // the edge's middle stays in the lattice's air: nothing but dissolving pillars near it
                double mx = (e[0] + e[3]) / 2, my = (e[1] + e[4]) / 2, mz = (e[2] + e[5]) / 2;
                assertTrue(LookCells.clearance(B, mx, my, mz, OPEN, 1.2) >= 1.2, "frame at " + my);
            }
            assertEquals(10 * freq * freq + 2, points.size(), "the geodesic sphere's vertices");
        }
        // its top and bottom: the lattice's own (y8 to y32, the box 33 high)
        double low = Double.MAX_VALUE, high = -1;
        for (double[] e : SophonLook.frame(SophonLook.FREQ)) {
            low = Math.min(low, Math.min(e[1], e[4]));
            high = Math.max(high, Math.max(e[1], e[4]));
        }
        assertEquals(8.5, low, 1e-9);
        assertEquals(32.5, high, 1e-9);
        assertTrue(high < SophonArrayShape.HEIGHT);
        // the far frame from past FRAME_FAR
        assertEquals(SophonLook.FREQ, SophonLook.frameFreq(0));
        assertEquals(SophonLook.FREQ, SophonLook.frameFreq(SophonLook.FRAME_FAR));
        assertEquals(SophonLook.FAR_FREQ, SophonLook.frameFreq(SophonLook.FRAME_FAR + 1));
    }

    @Test
    void theStrutsRiseFromTheRoofToTheSphere() {
        List<double[]> s = SophonLook.struts();
        assertEquals(4, s.size());
        for (double[] e : s) {
            int x = (int) Math.floor(e[0]), z = (int) Math.floor(e[2]);
            assertEquals(5, Math.abs(x - SophonArrayShape.MID), "on the struts' columns");
            assertEquals(5, Math.abs(z - SophonArrayShape.MID));
            assertEquals(SophonArrayShape.ROOF_Y + 1, e[1], 0, "standing on the roof");
            assertTrue(B.cell(x, SophonArrayShape.ROOF_Y, z) != ShapeKit.NONE, "the roof under the strut");
            for (int y = SophonArrayShape.STRUT_FROM; y <= SophonArrayShape.STRUT_TO; y++)
                assertTrue(SophonArrayShape.strut(x, y, z), "the dissolved strut at y" + y);
            assertEquals(12, dist(e[3], e[4], e[5]), 1e-9, "its top on the sphere");
            assertTrue(e[4] > e[1] && e[4] < SophonArrayShape.STRUT_TO + 1.5, "up to where the lattice starts");
        }
    }

    @Test
    void thePointUnfoldsIntoATurningIcosahedron() {
        assertEquals(20, SophonLook.ICO_FACES.length);
        assertEquals(30, SophonLook.ICO_EDGES.length);
        // every face's sides are edges
        Set<String> edges = new HashSet<>();
        for (int[] e : SophonLook.ICO_EDGES) edges.add(Math.min(e[0], e[1]) + "-" + Math.max(e[0], e[1]));
        assertEquals(30, edges.size());
        for (int[] f : SophonLook.ICO_FACES) for (int i = 0; i < 3; i++) {
            int a = f[i], b = f[(i + 1) % 3];
            assertTrue(edges.contains(Math.min(a, b) + "-" + Math.max(a, b)), "face side " + a + "-" + b);
        }
        for (double t : new double[] { 0, 77.5, 12345.25 }) for (double scale : new double[] { 0.3, 1 }) {
            double[][] v = SophonLook.ico(t, scale);
            assertEquals(12, v.length);
            for (double[] p : v) assertEquals(SophonLook.ICO_R * scale, dist(p[0], p[1], p[2]), 1e-9);
            // every edge as long as every other: still a regular icosahedron, turned
            double first = d(v[SophonLook.ICO_EDGES[0][0]], v[SophonLook.ICO_EDGES[0][1]]);
            for (int[] e : SophonLook.ICO_EDGES) assertEquals(first, d(v[e[0]], v[e[1]]), 1e-9);
            // it turns: the same again a turn later, not half a turn later
            double[][] w = SophonLook.ico(t + SophonLook.ICO_TURN_T, scale), h = SophonLook.ico(t + 47, scale);
            for (int i = 0; i < 12; i++) assertEquals(0, d(v[i], w[i]), 1e-6);
            assertTrue(d(v[1], h[1]) > 0.05, "turning");
        }
        assertTrue(SophonLook.ICO_R < SophonLook.RADIUS / 2, "well inside the frame");
        // unfolding by the time since the power changed; folding back without it
        assertEquals(0, SophonLook.unfold(true, 0), 0);
        assertEquals(0.5, SophonLook.unfold(true, SophonLook.UNFOLD_MS / 2), 1e-9);
        assertEquals(1, SophonLook.unfold(true, SophonLook.UNFOLD_MS), 0);
        assertEquals(1, SophonLook.unfold(true, Long.MAX_VALUE), 0, "a client arriving late sees it unfolded");
        assertEquals(1, SophonLook.unfold(false, 0), 0);
        assertEquals(0, SophonLook.unfold(false, SophonLook.FOLD_MS), 0);
        assertEquals(0, SophonLook.unfold(false, Long.MAX_VALUE), 0, "and folded");
        // first the icosahedron grows out of the point, then the canopy spreads
        assertEquals(0, SophonLook.icoScale(0), 0);
        assertEquals(1, SophonLook.icoScale(SophonLook.ICO_SHARE), 0);
        assertEquals(0, SophonLook.spread(SophonLook.ICO_SHARE), 0);
        assertEquals(1, SophonLook.spread(1), 0);
        assertTrue(SophonLook.spread(0.7) > 0 && SophonLook.spread(0.7) < 1);
    }

    @Test
    void theCanopySpreadsOverTheSkyAboveTheBuilding() {
        // Y0 + 40, seven over the lattice's top, radius 30 round the sphere's axis
        assertEquals(40, SophonLook.CANOPY_Y, 0);
        assertTrue(SophonLook.CANOPY_Y > SophonArrayShape.HEIGHT + 5);
        assertEquals(30, SophonLook.CANOPY_R, 0);
        // clear of the sky ring's berths over the inner diagonals: (+-23, +-23) radius 14 at the same height, the
        // canopy's middle at (+-62, +-62)
        assertTrue(Math.hypot(62 - 23, 62 - 23) > 14 + SophonLook.CANOPY_R + 5);
        List<double[]> tiles = SophonLook.tiles();
        assertEquals(60, tiles.size());
        for (double[] q : tiles) {
            for (double[] c : new double[][] { { q[0], q[1] }, { q[2], q[1] }, { q[2], q[3] }, { q[0], q[3] } })
                assertTrue(r(c) <= SophonLook.CANOPY_R, "a tile inside the canopy");
            assertEquals(SophonLook.TILE - SophonLook.TILE_GAP, q[2] - q[0], 1e-9);
        }
        List<double[][]> traces = SophonLook.traces();
        assertEquals(SophonLook.TRACES, traces.size());
        for (int k = 0; k < traces.size(); k++) {
            double[][] t = traces.get(k);
            // from the die's edge out to a via short of the rim, every step straight or at 45 degrees
            assertEquals(SophonLook.DIE, Math.max(Math.abs(t[0][0] - 15.5), Math.abs(t[0][1] - 15.5)), 1e-9);
            double end = r(t[t.length - 1]);
            assertTrue(end >= 24 && end <= 27 + 1e-9, "trace " + k + " ends at " + end);
            for (int i = 1; i < t.length; i++) {
                double dx = Math.abs(t[i][0] - t[i - 1][0]), dz = Math.abs(t[i][1] - t[i - 1][1]);
                assertTrue(dx == 0 || dz == 0 || Math.abs(dx - dz) < 1e-9, "trace " + k + " step " + i);
                assertTrue(r(t[i]) > r(t[i - 1]), "running out");
            }
        }
        // no two traces cross or touch
        for (int a = 0; a < traces.size(); a++) for (int b = a + 1; b < traces.size(); b++)
            assertTrue(gap(traces.get(a), traces.get(b)) >= 1.5, "traces " + a + " and " + b);
        // the ring bus inside, the fade from the die to a quarter at the rim
        for (double[] p : SophonLook.bus()) assertEquals(SophonLook.BUS_R, r(p), 1e-9);
        assertEquals(1, SophonLook.radialFade(0), 0);
        assertEquals(0.25f, SophonLook.radialFade(SophonLook.CANOPY_R), 1e-6);
        assertTrue(SophonLook.radialFade(10) > SophonLook.radialFade(20));
    }

    @Test
    void aPulseFlowsOutAlongEveryTrace() {
        List<double[][]> traces = SophonLook.traces();
        for (int k = 0; k < traces.size(); k++) {
            double[][] tr = traces.get(k);
            boolean seen = false, gap = false;
            double last = -1;
            for (int j = 0; j < SophonLook.PULSE_T * 4; j++) {
                double t = j * 0.25;
                double[] q = SophonLook.pulse(k, t, 1);
                if (q == null) {
                    gap = true;
                    continue;
                }
                seen = true;
                assertTrue(onTrace(tr, q[0], q[1]) && onTrace(tr, q[2], q[3]), "pulse " + k + " on its trace");
                assertTrue(Math.hypot(q[2] - q[0], q[3] - q[1]) <= SophonLook.PULSE_LEN + 1e-9);
                // repeating with its period
                double[] w = SophonLook.pulse(k, t + SophonLook.PULSE_T, 1);
                assertNotNull(w);
                assertEquals(q[2], w[2], 1e-6);
                last = Math.max(last, r(new double[] { q[2], q[3] }));
            }
            assertTrue(seen && gap, "trace " + k + " pulses and rests");
            assertEquals(r(tr[tr.length - 1]), last, 0.5, "out to the via");
            // not past what the spreading canopy has drawn
            for (int j = 0; j < SophonLook.PULSE_T; j++) {
                double[] q = SophonLook.pulse(k, j, 0.3);
                if (q != null) assertTrue(along(tr, q[2], q[3]) <= SophonLook.length(tr) * 0.3 + 1e-6);
            }
            assertNull(SophonLook.pulse(k, 5, 0));
        }
    }

    @Test
    void theArrayKeepsItsBudget() {
        int[] budget = { 2000, 300, 64 };
        for (int tier = ModuleLook.NEAR; tier <= ModuleLook.FAR; tier++) {
            int base = ModuleLook.baseQuads(B, SophonArrayShape.UNITS, tier);
            for (boolean docked : new boolean[] { false, true }) for (boolean on : new boolean[] { false, true }) {
                if (on && !docked) continue;
                int own = SophonLook.quads(tier, docked, on);
                assertTrue(
                    base + own <= budget[tier],
                    "tier " + tier + (docked ? " docked" : "") + (on ? " powered" : "") + ": " + base + " + " + own);
            }
        }
        // 144 sockets, each seen from two sides at most
        int sockets = 0;
        for (UnitLayout.Unit u : SophonArrayShape.UNITS.units()) sockets += u.sockets.size();
        assertEquals(144, sockets);
        // the landmark at every whole distance it is drawn at, powered or not, and as it unfolds and folds back
        for (int d = 0; d <= (int) SophonLook.LANDMARK; d++) {
            for (boolean on : new boolean[] { false, true }) {
                int q = SophonLook.landmarkQuads(d, on);
                assertTrue(q <= SophonLook.BUDGET, d + " blocks: " + q);
            }
            for (double u : new double[] { 0, 0.2, 0.4, 0.5, 1 }) {
                int q = SophonLook.landmarkQuads(d, u);
                assertTrue(q <= SophonLook.BUDGET, d + " blocks unfolded " + u + ": " + q);
                assertTrue(q <= SophonLook.landmarkQuads(d, true), "the powered count bounds every state");
            }
        }
        assertEquals(1, SophonLook.landmarkFade(SophonLook.LANDMARK - SophonLook.LANDMARK_FADE), 1e-6);
        assertEquals(0.5, SophonLook.landmarkFade(SophonLook.LANDMARK - SophonLook.LANDMARK_FADE / 2), 1e-6);
        assertEquals(0, SophonLook.landmarkFade(SophonLook.LANDMARK), 0);
        // the column, near with its motes and glow, mid alone, never far or undocked
        assertEquals(4 + 6 + 1, SophonLook.quads(ModuleLook.NEAR, true, false));
        assertEquals(4, SophonLook.quads(ModuleLook.MID, true, true));
        assertEquals(0, SophonLook.quads(ModuleLook.FAR, true, true));
        assertEquals(0, SophonLook.quads(ModuleLook.NEAR, false, false));
    }

    @Test
    void theCountIsTheDrawing() {
        // recounted here the way CityFrame.lineRel cuts a line: one quad for every 8 blocks or part of them
        assertEquals(8, DomeLook.PIECE, 0);
        int frame = 0;
        for (double[] e : SophonLook.frame(2)) frame += cut(dist3(e));
        assertEquals(120, frame, "every edge of the twice-cut icosahedron shorter than 8");
        assertEquals(frame, SophonLook.frameQuads(2));
        assertEquals(60, SophonLook.frameQuads(1), "the icosahedron's 30 edges of 12.6 blocks, two pieces each");
        assertEquals(4, SophonLook.strutQuads());
        assertEquals(2, SophonLook.coreQuads(false), "the point: a glow and its core");
        assertEquals(2 + 20 + 30, SophonLook.coreQuads(true), "and the icosahedron's faces and edges");
        int canopy = SophonLook.tiles()
            .size() + 1
            + 4 * cut(2 * SophonLook.DIE);
        for (double[][] tr : SophonLook.traces()) {
            for (int i = 1; i < tr.length; i++) {
                double len = Math.hypot(tr[i][0] - tr[i - 1][0], tr[i][1] - tr[i - 1][1]);
                // no straight piece a whole multiple of 8 long, so the renderer's cut cannot slip by one
                assertTrue(Math.abs(len / 8 - Math.rint(len / 8)) > 1e-3, "a piece " + len + " long");
                canopy += cut(len);
            }
            canopy += 2;
        }
        double[][] bus = SophonLook.bus();
        for (int i = 0; i < bus.length; i++) canopy += cut(
            Math.hypot(bus[(i + 1) % bus.length][0] - bus[i][0], bus[(i + 1) % bus.length][1] - bus[i][1]));
        assertEquals(canopy, SophonLook.canopyQuads());
        assertEquals(
            191,
            canopy,
            "60 tiles, the die 9, 16 traces in 66 pieces with a via and a pulse each, the bus 24");
        assertEquals(120 + 4 + 52 + 191, SophonLook.landmarkQuads(100, true));
        assertEquals(60 + 4 + 2, SophonLook.landmarkQuads(400, false));
        // half way through folding back after the power dropped the icosahedron is whole and the canopy still spread
        // in part: both are drawn, so both are counted
        assertEquals(0.5, SophonLook.unfold(false, 750), 1e-9);
        assertEquals(60 + 4 + 52 + 191, SophonLook.landmarkQuads(400, SophonLook.unfold(false, 750)));
        assertEquals(60 + 4 + 2, SophonLook.landmarkQuads(400, SophonLook.unfold(false, SophonLook.FOLD_MS)));
        assertEquals(SophonLook.coreQuads(true), SophonLook.coreQuads(1.0));
        assertEquals(SophonLook.coreQuads(false), SophonLook.coreQuads(0.0));
    }

    private static boolean onTrace(double[][] t, double x, double z) {
        for (int i = 1; i < t.length; i++) if (segDist(t[i - 1], t[i], x, z) < 1e-6) return true;
        return false;
    }

    /** How far along a trace the point lies. */
    private static double along(double[][] t, double x, double z) {
        double at = 0;
        for (int i = 1; i < t.length; i++) {
            double seg = Math.hypot(t[i][0] - t[i - 1][0], t[i][1] - t[i - 1][1]);
            if (segDist(t[i - 1], t[i], x, z) < 1e-6) return at + Math.hypot(x - t[i - 1][0], z - t[i - 1][1]);
            at += seg;
        }
        return Double.MAX_VALUE;
    }

    /** The least distance between two traces, sampled every tenth of a block along the first. */
    private static double gap(double[][] a, double[][] b) {
        double best = Double.MAX_VALUE;
        for (int i = 1; i < a.length; i++) {
            double len = Math.hypot(a[i][0] - a[i - 1][0], a[i][1] - a[i - 1][1]);
            for (double s = 0; s <= len; s += 0.1) {
                double x = a[i - 1][0] + (a[i][0] - a[i - 1][0]) * s / len,
                    z = a[i - 1][1] + (a[i][1] - a[i - 1][1]) * s / len;
                for (int j = 1; j < b.length; j++) best = Math.min(best, segDist(b[j - 1], b[j], x, z));
            }
        }
        return best;
    }

    private static double segDist(double[] p, double[] q, double x, double z) {
        double dx = q[0] - p[0], dz = q[1] - p[1], l2 = dx * dx + dz * dz;
        double u = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - p[0]) * dx + (z - p[1]) * dz) / l2));
        return Math.hypot(x - p[0] - u * dx, z - p[1] - u * dz);
    }

    private static int cut(double length) {
        return Math.max(1, (int) Math.ceil(length / 8));
    }

    private static double r(double[] p) {
        return Math.hypot(p[0] - 15.5, p[1] - 15.5);
    }

    private static double dist(double x, double y, double z) {
        return Math.sqrt(sq(x - 15.5) + sq(y - 20.5) + sq(z - 15.5));
    }

    private static double d(double[] a, double[] b) {
        return Math.sqrt(sq(a[0] - b[0]) + sq(a[1] - b[1]) + sq(a[2] - b[2]));
    }

    private static double dist3(double[] e) {
        return Math.sqrt(sq(e[3] - e[0]) + sq(e[4] - e[1]) + sq(e[5] - e[2]));
    }

    private static double sq(double v) {
        return v * v;
    }
}
