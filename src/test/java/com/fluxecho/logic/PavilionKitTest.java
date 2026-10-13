package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.CRC32;

import org.junit.jupiter.api.Test;

/**
 * The pavilion helpers of {@link ShapeKit} (0.13.0) against the generator they port ({@code city/gen/b_pav.py}): the
 * octagon norm, the plan and its thick edge, the four chamfer portals, the shell with each pavilion's wall height,
 * pilaster rhythm and glazed rows (checksums of the generator's layers), the centre controller, the back gate niche,
 * and the spiral (the generator's own is the kit's).
 */
class PavilionKitTest {

    private static long crc(ShapeKit.Shape s) {
        CRC32 c = new CRC32();
        c.update(
            String.join("\n", ShapeKit.layers(s))
                .getBytes(StandardCharsets.UTF_8));
        return c.getValue();
    }

    @Test
    void theOctagonNormIsTheGenerators() {
        StringBuilder b = new StringBuilder();
        int sum = 0;
        for (int a = -15; a <= 15; a++) for (int r = -15; r <= 15; r++) {
            int n = ShapeKit.onorm(a, r);
            if (b.length() > 0) b.append(',');
            b.append(n);
            sum += n;
        }
        CRC32 c = new CRC32();
        c.update(
            b.toString()
                .getBytes(StandardCharsets.UTF_8));
        // zlib.crc32 of ','.join(onorm(a, b) for a in -15..15 for b in -15..15)
        assertEquals(33328030L, c.getValue());
        assertEquals(11824, sum);
        // a cell's norm is at most h exactly when it lies in oct(h, 4h / 3) (the chamfer not rounded)
        for (int h = 1; h <= 14; h++) for (int a = -16; a <= 16; a++) for (int r = -16; r <= 16; r++) {
            assertEquals(ShapeKit.octo(a, r, h, 4 * h / 3), ShapeKit.onorm(a, r) <= h, h + ": " + a + "," + r);
        }
        int[] k = { 0, 1, 3, 4, 5, 7, 8, 9, 11, 12, 13, 15, 16 };
        for (int h = 0; h < k.length; h++) assertEquals(k[h], ShapeKit.octK(h), "round(4 * " + h + " / 3)");
    }

    @Test
    void thePlanItsEdgeAndItsPortals() {
        Set<Long> plan = ShapeKit.planCells();
        assertEquals(481, plan.size(), "oct(12, 16), as the diagonal sites' envelope");
        assertEquals(
            481,
            CampusPlan.pavilionCells(1)
                .size());
        Set<Long> edge = ShapeKit.thickEdge(plan);
        assertEquals(96, edge.size());
        assertEquals(ShapeKit.thickBoundary(plan), edge);
        Set<Long> doors = ShapeKit.portalCells(edge);
        int[][] want = { { 3, 5 }, { 3, 19 }, { 4, 4 }, { 4, 5 }, { 4, 6 }, { 4, 18 }, { 4, 19 }, { 4, 20 }, { 5, 3 },
            { 5, 4 }, { 5, 5 }, { 5, 6 }, { 5, 18 }, { 5, 19 }, { 5, 20 }, { 5, 21 }, { 6, 4 }, { 6, 5 }, { 6, 19 },
            { 6, 20 }, { 18, 4 }, { 18, 5 }, { 18, 19 }, { 18, 20 }, { 19, 3 }, { 19, 4 }, { 19, 5 }, { 19, 6 },
            { 19, 18 }, { 19, 19 }, { 19, 20 }, { 19, 21 }, { 20, 4 }, { 20, 5 }, { 20, 6 }, { 20, 18 }, { 20, 19 },
            { 20, 20 }, { 21, 5 }, { 21, 19 } };
        Set<Long> w = new TreeSet<>();
        for (int[] p : want) w.add(ShapeKit.key(p[0], p[1]));
        assertEquals(w, new TreeSet<>(doors), "the generator's portal cells");
        // ten a portal, on the four chamfers, symmetric both ways
        for (long d : doors) {
            int x = ShapeKit.keyX(d), z = ShapeKit.keyZ(d);
            assertTrue(doors.contains(ShapeKit.key(24 - x, z)) && doors.contains(ShapeKit.key(x, 24 - z)));
            assertTrue(plan.contains(d));
        }
    }

    /** Each pavilion's shell, drawn alone on an empty box, is the generator's layer for layer. */
    @Test
    void theShellIsTheGenerators() {
        Object[][] cases = { { 12, 3, ShapeKit.range(3, 11), 375758692L }, { 6, 4, new int[0], 4139046325L },
            { 8, 3, new int[] { 6, 7 }, 161516508L }, { 6, 2, new int[] { 3, 4 }, 766981296L } };
        for (Object[] c : cases) {
            int top = (Integer) c[0];
            ShapeKit.Shape s = new ShapeKit.Shape("t", 25, top + 1, 25);
            ShapeKit.Shell sh = ShapeKit.shell(s, top, (Integer) c[1], (int[]) c[2]);
            assertEquals((long) c[3], crc(s), "wall top " + top);
            assertEquals(481, sh.plan.size());
            assertEquals(96, sh.edge.size());
            assertEquals(40, sh.doors.size());
            // the portals: two deep, four high, air, under a cornice lintel at y5
            for (long d : sh.doors) {
                int x = ShapeKit.keyX(d), z = ShapeKit.keyZ(d);
                for (int y = 1; y <= 4; y++) assertEquals('-', s.get(x, y, z));
                assertEquals('n', s.get(x, 5, z));
            }
            assertTrue(
                s.symmetric()
                    .isEmpty());
        }
        assertArrayEquals(new int[] { 3, 4, 5, 6, 7, 8, 9, 10 }, ShapeKit.range(3, 11));
        assertEquals(0, ShapeKit.range(5, 5).length);
    }

    @Test
    void theCentreControllerAndTheBackNiche() {
        ShapeKit.Shape s = new ShapeKit.Shape("t", 25, 13, 25);
        ShapeKit.shell(s, 12, 3, ShapeKit.range(3, 11));
        ShapeKit.centreController(s, 2);
        assertArrayEquals(new int[] { 12, 2, 12 }, s.controller);
        assertEquals('K', s.get(12, 1, 12));
        assertEquals('~', s.get(12, 2, 12));
        ShapeKit.backNiche(s);
        assertEquals(12, s.niche.size());
        for (int x = 11; x <= 13; x++) for (int y = 1; y <= 4; y++) {
            assertTrue(s.niche.contains(ShapeKit.Shape.pos(x, y, 24)));
            assertTrue(ShapeKit.pavilionNiche(x, y, 24));
            assertTrue(ShapeKit.soft(s.get(x, y, 24)));
        }
        assertFalse(ShapeKit.pavilionNiche(10, 1, 24));
        assertFalse(ShapeKit.pavilionNiche(12, 5, 24));
        assertFalse(ShapeKit.pavilionNiche(12, 1, 0), "the back face, not the front");
        // a niche needs a wall
        ShapeKit.Shape empty = new ShapeKit.Shape("e", 25, 6, 25);
        assertThrows(IllegalArgumentException.class, () -> ShapeKit.backNiche(empty));
    }

    /** The generator's {@code spiral} round an octagon's post is the kit's (the Archive's well). */
    @Test
    void theSpiralIsTheKits() {
        // b_pav.spiral(5, 6, 1, 12, 3, -1)
        int[][] want = { { 6, 1, 7, 't' }, { 6, 1, 6, 'W' }, { 6, 2, 5, 't' }, { 5, 2, 5, 'W' }, { 4, 3, 5, 't' },
            { 4, 3, 6, 'W' }, { 4, 4, 7, 't' }, { 5, 4, 7, 'W' }, { 6, 5, 7, 't' }, { 6, 5, 6, 'W' }, { 6, 6, 5, 't' },
            { 5, 6, 5, 'W' } };
        List<int[]> got = new ArrayList<>(ShapeKit.spiral(5, 6, 1, 12, 3, -1));
        assertEquals(want.length, got.size());
        for (int i = 0; i < want.length; i++) assertArrayEquals(want[i], got.get(i), "step " + i);
    }

    @Test
    void theStartsAreOutsideThePortals() {
        List<int[]> s = ShapeKit.pavilionStarts();
        assertEquals(5, s.size());
        Set<Long> plan = ShapeKit.planCells();
        for (int[] p : s) {
            assertEquals(2, p[2], "on the ground");
            assertFalse(plan.contains(ShapeKit.key(p[0], p[1])), "outside the plan");
        }
    }
}
