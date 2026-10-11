package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The Echo Dome Generator's drawing (0.12.0, CITY.md §4.8 Visuals): the ribs run through the leg cells they replace,
 * foot on the forum and head under the pod; the arcs reach the dome; its apex cells lie on it; and the landmark (the
 * dome, the moored legs' far lines and the crystal's glow) never takes more than its 400 quads at any distance and any
 * radius the dome grows to, counted piece by piece as the renderer cuts its lines.
 */
class DomeLookTest {

    @Test
    void theRibsRunThroughTheLegCells() {
        List<int[]> cells = DomeGeneratorShape.legs();
        assertEquals(DomeGeneratorShape.LEG_CELLS, cells.size());
        int per = DomeGeneratorShape.LEG_TOP;
        for (int i = 0; i < cells.size(); i++) {
            int leg = i / per;
            int[] c = cells.get(i);
            int[] p = DomeGeneratorShape.campusToPod(c[0], c[1], c[2]);
            // the curve at the cell's height: the cell's middle is dy + 0.5 over the base layer
            double s = (c[2] - 0.5) / per;
            double[] q = DomeLook.legPoint(leg, s);
            assertEquals(p[1] + 0.5, q[1], 1e-9, "the same height");
            double off = Math.hypot(p[0] + 0.5 - q[0], p[2] + 0.5 - q[2]);
            assertTrue(off <= Math.sqrt(0.5) + 1e-9, "leg " + leg + " cell " + i + " is " + off + " off its rib");
        }
        for (int leg = 0; leg < DomeGeneratorShape.LEGS; leg++) {
            double[] foot = DomeLook.legPoint(leg, 0), head = DomeLook.legPoint(leg, 1);
            assertEquals(DomeLook.CY, foot[1], 1e-9, "the foot stands on the forum");
            assertEquals(0, head[1], 1e-9, "the head meets the pod's floor");
            assertEquals(
                DomeGeneratorShape.LEG_FOOT,
                Math.hypot(foot[0] - DomeLook.CX, foot[2] - DomeLook.CZ),
                0.1,
                "the foot at radius 14");
            assertEquals(DomeGeneratorShape.LEG_HEAD, Math.hypot(head[0] - DomeLook.CX, head[2] - DomeLook.CZ), 1e-9);
        }
        assertEquals(DomeLook.FOOT_HALF, DomeLook.legHalf(0), 1e-12);
        assertEquals(DomeLook.HEAD_HALF, DomeLook.legHalf(1), 1e-12);
        assertTrue(DomeLook.legHalf(0.5) < DomeLook.legHalf(0.2), "tapering up");
    }

    @Test
    void theArcsReachTheDome() {
        for (int phase = 2; phase <= 5; phase++) {
            double r = DomeLook.shellRadius(phase);
            for (int[] n : DomeGeneratorShape.nodes()) {
                double[] a = { n[0] + 0.5, n[1] + 0.5, n[2] + 0.5 };
                double[] b = DomeLook.arcEnd(a[0], a[1], a[2], r);
                double d = Math.sqrt(sq(b[0] - DomeLook.CX) + sq(b[1] - DomeLook.CY) + sq(b[2] - DomeLook.CZ));
                assertEquals(r, d, 1e-9, "on the sphere");
                assertTrue(Math.hypot(b[0] - DomeLook.CX, b[2] - DomeLook.CZ) > 20, "out from the pod");
                double[] p0 = DomeLook.arcPoint(a, b, 0), p1 = DomeLook.arcPoint(a, b, 1),
                    m = DomeLook.arcPoint(a, b, 0.5);
                assertEquals(a[1], p0[1], 1e-9);
                assertEquals(b[0], p1[0], 1e-9);
                assertTrue(m[1] > a[1], "bowing up");
            }
        }
        assertEquals(64, DomeLook.shellRadius(2), 0);
        assertEquals(104, DomeLook.shellRadius(3), 0);
        assertEquals(128, DomeLook.shellRadius(4), 0);
        assertEquals(160, DomeLook.shellRadius(5), 0);
    }

    @Test
    void theApexCellsLieOnTheDome() {
        List<double[][]> cells = DomeLook.apexCells(64, DomeLook.HEX_RINGS);
        assertEquals(19, cells.size());
        assertEquals(
            7,
            DomeLook.apexCells(64, 1)
                .size());
        for (double[][] cell : cells) {
            assertEquals(7, cell.length);
            for (double[] p : cell) {
                assertEquals(64, Math.sqrt(sq(p[0]) + sq(p[1]) + sq(p[2])), 1e-9);
                assertTrue(p[1] > 50, "near the apex");
            }
        }
        boolean lit = false, dark = false;
        for (double t = 0; t < 2000; t += 7) {
            double s = DomeLook.shimmer(3, t);
            assertTrue(s >= 0 && s <= 1);
            lit |= s > 0.9;
            dark |= s == 0;
        }
        assertTrue(lit && dark, "a cell shimmers now and then");
    }

    @Test
    void theDomeKeepsItsBudget() {
        // every radius shellRadius gives, at every whole distance the landmark is drawn at (CITY.md §1.3)
        for (int phase = 0; phase <= 6; phase++) {
            double r = DomeLook.shellRadius(phase);
            for (int d = 0; d <= (int) DomeLook.LANDMARK; d++) {
                int quads = DomeLook.landmarkQuads(d, r, true);
                assertTrue(quads <= DomeLook.BUDGET, "radius " + r + " at " + d + ": " + quads + " quads");
                assertTrue(DomeLook.landmarkQuads(d, r, false) < quads, "the legs are counted");
            }
        }
        assertTrue(DomeLook.groundSegments(500) < DomeLook.groundSegments(50), "fewer sides far away");
        assertTrue(DomeLook.farLegQuads() <= 64, "the far legs within a building's far budget");
    }

    @Test
    void theCountIsTheDrawing() {
        // recounted here the way CityFrame.lineRel cuts a line: one quad for every 8 blocks or part of them
        assertEquals(8, DomeLook.PIECE, 0);
        double[][] cases = { { 0, 64 }, { 150, 64 }, { 250, 64 }, { 400, 64 }, { 100, 104 }, { 250, 128 }, { 250, 160 },
            { 500, 160 } };
        for (double[] c : cases) {
            double d = c[0], r = c[1];
            int n = DomeLook.groundSegments(d), expected = 0;
            for (int i = 0; i < n; i++) {
                double p0 = i * 2 * Math.PI / n, p1 = (i + 1) * 2 * Math.PI / n;
                // the soft band, then the line along it
                expected += 1 + cut(2 * r * Math.sin((p1 - p0) / 2));
            }
            for (double[][] cell : DomeLook.apexCells(r, DomeLook.hexRings(d, r))) {
                expected += 2;
                for (int k = 1; k <= 6; k++) expected += cut(dist(cell[k], cell[k % 6 + 1]));
            }
            assertEquals(expected, DomeLook.shellQuads(d, r), "radius " + r + " at " + d);
        }
        // the phase II ring between 192 and 320 (where 48 sides of 8.4 blocks once cost two pieces each)
        assertEquals(56 * 2 + 19 * 8, DomeLook.shellQuads(250, 64));
        int legs = 0;
        for (int leg = 0; leg < DomeGeneratorShape.LEGS; leg++)
            for (int k = 0; k < DomeLook.FAR_LEG_LINES; k++) legs += cut(
                dist(
                    DomeLook.legPoint(leg, k / (double) DomeLook.FAR_LEG_LINES),
                    DomeLook.legPoint(leg, (k + 1) / (double) DomeLook.FAR_LEG_LINES)));
        assertEquals(legs, DomeLook.farLegQuads());
        assertEquals(36, legs, "two lines of about 20 blocks a leg, three pieces each");
        assertEquals(
            DomeLook.shellQuads(250, 64) + legs + 1,
            DomeLook.landmarkQuads(250, 64, true),
            "the dome, the legs and the crystal's glow");
    }

    @Test
    void theLandmarkFadesOutAtItsRange() {
        assertEquals(1, DomeLook.landmarkFade(0), 0);
        assertEquals(1, DomeLook.landmarkFade(DomeLook.LANDMARK - DomeLook.LANDMARK_FADE), 1e-6);
        assertEquals(0.5, DomeLook.landmarkFade(DomeLook.LANDMARK - DomeLook.LANDMARK_FADE / 2), 1e-6);
        assertEquals(0, DomeLook.landmarkFade(DomeLook.LANDMARK), 0);
        assertEquals(0, DomeLook.landmarkFade(DomeLook.LANDMARK + 100), 0);
    }

    private static int cut(double length) {
        return Math.max(1, (int) Math.ceil(length / 8));
    }

    private static double dist(double[] a, double[] b) {
        return Math.sqrt(sq(a[0] - b[0]) + sq(a[1] - b[1]) + sq(a[2] - b[2]));
    }

    private static double sq(double d) {
        return d * d;
    }
}
