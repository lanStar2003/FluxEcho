package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The x-ray projection of an underground plan (0.12.0): the fold edges of a set of cells, joined along their lines;
 * a cube is its twelve edges, flat faces have none inside them, a concave corner has its own; the Tidal Well's plan is
 * underground and outlined in a few hundred segments, its shaft's corners running its whole depth.
 */
class XRayTest {

    private static Set<String> segments(int[] e) {
        Set<String> out = new HashSet<>();
        for (int i = 0; i < e.length; i += 6) {
            int axes = 0;
            for (int k = 0; k < 3; k++) if (e[i + k] != e[i + 3 + k]) {
                axes++;
                assertTrue(e[i + k] < e[i + 3 + k], "a segment runs forwards");
            }
            assertEquals(1, axes, "a segment runs along one axis");
            assertTrue(
                out.add(e[i] + "," + e[i + 1] + "," + e[i + 2] + ";" + e[i + 3] + "," + e[i + 4] + "," + e[i + 5]));
        }
        return out;
    }

    private static int[] edges(int[]... cells) {
        int[] xs = new int[cells.length], ys = new int[cells.length], zs = new int[cells.length];
        for (int i = 0; i < cells.length; i++) {
            xs[i] = cells[i][0];
            ys[i] = cells[i][1];
            zs[i] = cells[i][2];
        }
        return XRay.edges(xs, ys, zs);
    }

    @Test
    void aCubeIsItsTwelveEdges() {
        Set<String> s = segments(edges(new int[] { 5, -20, 7 }));
        assertEquals(12, s.size());
        assertTrue(s.contains("5,-20,7;6,-20,7"));
        assertTrue(s.contains("6,-20,8;6,-19,8"));
        assertTrue(s.contains("5,-19,7;5,-19,8"));
    }

    @Test
    void aRowIsOneBoxAndRepeatsCountOnce() {
        Set<String> s = segments(
            edges(new int[] { 0, 0, 0 }, new int[] { 1, 0, 0 }, new int[] { 2, 0, 0 }, new int[] { 1, 0, 0 }));
        assertEquals(12, s.size(), "the seams between the cells lie flat");
        assertTrue(s.contains("0,0,0;3,0,0"));
        assertTrue(s.contains("0,1,1;3,1,1"));
        // a 2 x 2 x 2 block: still a box
        assertEquals(
            12,
            segments(
                edges(
                    new int[] { 0, 0, 0 },
                    new int[] { 1, 0, 0 },
                    new int[] { 0, 1, 0 },
                    new int[] { 1, 1, 0 },
                    new int[] { 0, 0, 1 },
                    new int[] { 1, 0, 1 },
                    new int[] { 0, 1, 1 },
                    new int[] { 1, 1, 1 })).size());
    }

    @Test
    void anLHasItsConcaveCorner() {
        Set<String> s = segments(edges(new int[] { 0, 0, 0 }, new int[] { 1, 0, 0 }, new int[] { 0, 0, 1 }));
        assertEquals(18, s.size(), "six sides on top, six below, six corners up");
        assertTrue(s.contains("1,0,1;1,1,1"), "the concave corner");
        assertTrue(s.contains("0,1,0;2,1,0"));
        assertTrue(s.contains("1,1,1;2,1,1"));
    }

    @Test
    void cellsTouchingAlongAnEdgeFoldThere() {
        Set<String> s = segments(edges(new int[] { 0, 0, 0 }, new int[] { 1, 1, 0 }));
        assertTrue(s.contains("1,1,0;1,1,1"), "the shared edge");
        assertThrows(IllegalArgumentException.class, () -> XRay.edges(new int[1], new int[2], new int[1]));
        assertEquals(0, XRay.edges(new int[0], new int[0], new int[0]).length);
    }

    /** The Tidal Well's plan is underground; its outline is a few hundred segments, the shaft's corners full depth. */
    @Test
    void theWellsOutline() {
        int cx = 100, y0 = 64, cz = -200;
        BuildPlan.Plan p = RootShape
            .plan(new CampusPlan(Mix.seed(cx, y0 + 2, cz)), SiteFamily.ROOT_SITE, cx, y0, cz, 0, -1);
        GhostCells.Cells c = GhostCells.Cells.of(p.steps);
        assertTrue(XRay.underground(c, y0));
        assertFalse(XRay.underground(c, y0 - 26), "nothing under the slab");
        int[] cells = XRay.below(c, null, y0, -1);
        assertEquals(3268 + 238, cells.length, "every cell it places, none it only digs");
        int[] shell = XRay.below(c, null, y0, RootShape.R_SINK);
        assertEquals(417 * 2 + 88 * 24 - 1, shell.length, "the shell but the lid's open centre");
        BitSet done = new BitSet();
        for (int i : shell) done.set(i);
        assertEquals(cells.length - shell.length, XRay.below(c, done, y0, -1).length);
        int[] e = XRay.edges(c, cells);
        Set<String> s = segments(e);
        int n = s.size();
        assertTrue(n > 100 && n < 3000, "a few hundred segments: " + n);
        int fullDepth = 0;
        for (int i = 0; i < e.length; i += 6) {
            for (int k = 0; k < 6; k++) {
                int v = e[i + k], lo = k % 3 == 1 ? y0 - 26 : (k % 3 == 0 ? cx : cz) - 12,
                    hi = k % 3 == 1 ? y0 : (k % 3 == 0 ? cx : cz) + 13;
                assertTrue(v >= lo && v <= hi, "inside the well's box");
            }
            if (e[i + 1] == y0 - 26 && e[i + 4] == y0) fullDepth++;
        }
        assertTrue(fullDepth >= 8, "the shaft's outer corners run its whole depth: " + fullDepth);
        // the flat faces' top edges along the lid, nine cells long, on the four sides
        int flatTops = 0;
        for (String seg : s) {
            String[] ab = seg.split(";");
            String[] a = ab[0].split(","), b = ab[1].split(",");
            int len = Math.abs(Integer.parseInt(b[0]) - Integer.parseInt(a[0]))
                + Math.abs(Integer.parseInt(b[2]) - Integer.parseInt(a[2]));
            if (Integer.parseInt(a[1]) == y0 && Integer.parseInt(b[1]) == y0 && len == 9) flatTops++;
        }
        assertTrue(flatTops >= 4, "the lid's four flat sides: " + flatTops);
    }
}
