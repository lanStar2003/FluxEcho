package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * The way back from the world into a blueprint ({@link Blueprint#cell}, {@link Blueprint#point}): what the library
 * uses to tell which shelf unit a clicked block or the camera is in, for every facing.
 */
class BlueprintTest {

    private static final int[][] FACINGS = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
    private static final int X = 120, Y = 66, Z = -340;

    @Test
    void cellUndoesWorld() {
        for (Blueprint bp : new Blueprint[] { ArchiveShape.BLUEPRINT, LibraryShape.BLUEPRINT, NexusShape.PHASE_1 }) {
            for (int[] f : FACINGS) for (Blueprint.Cell c : bp.cells()) {
                int[] w = bp.world(c.a, c.b, c.c, X, Y, Z, f[0], f[1]);
                assertArrayEquals(
                    new int[] { c.a, c.b, c.c },
                    bp.cell(w[0], w[1], w[2], X, Y, Z, f[0], f[1]),
                    "cell " + c.a + "," + c.b + "," + c.c + " facing " + f[0] + "," + f[1]);
            }
        }
    }

    @Test
    void pointsFallInTheirCell() {
        Random r = new Random(5);
        Blueprint bp = ArchiveShape.BLUEPRINT;
        for (int[] f : FACINGS) for (int k = 0; k < 4000; k++) {
            int a = r.nextInt(ArchiveShape.WIDTH), b = r.nextInt(ArchiveShape.HEIGHT),
                c = r.nextInt(ArchiveShape.DEPTH);
            int[] w = bp.world(a, b, c, X, Y, Z, f[0], f[1]);
            double px = w[0] + r.nextDouble(), py = w[1] + r.nextDouble(), pz = w[2] + r.nextDouble();
            double[] p = bp.point(px, py, pz, X, Y, Z, f[0], f[1]);
            String at = a + "," + b + "," + c + " facing " + f[0] + "," + f[1];
            assertEquals(a, (int) Math.floor(p[0]), "across of " + at);
            assertEquals(ArchiveShape.HEIGHT - 1 - b, (int) Math.floor(p[1]), "height of " + at);
            assertEquals(c, (int) Math.floor(p[2]), "row of " + at);
        }
    }

    @Test
    void theArchiveInsideMatchesItsCells() {
        // a player standing on the nave floor in front of the desk is inside; one standing before the doors is not
        Blueprint bp = ArchiveShape.BLUEPRINT;
        for (int[] f : FACINGS) {
            int[] desk = ArchiveShape.cellOf(ArchiveShape.DESK[0], ArchiveShape.DESK[1], ArchiveShape.DESK[2] - 2);
            int[] w = bp.world(desk[0], desk[1], desk[2], X, Y, Z, f[0], f[1]);
            double[] in = bp.point(w[0] + 0.5, w[1] + 0.1, w[2] + 0.5, X, Y, Z, f[0], f[1]);
            assertEquals(true, ArchiveShape.inside(in[0], in[1], in[2]), "on the nave floor");
            int[] door = ArchiveShape.cellOf(ArchiveShape.MID - 2, 1, 0);
            int[] o = bp.world(door[0], door[1], door[2], X, Y, Z, f[0], f[1]);
            // one block further out than the door cell, toward the nexus
            double[] out = bp.point(o[0] + 0.5 + f[0], o[1] + 0.1, o[2] + 0.5 + f[1], X, Y, Z, f[0], f[1]);
            assertEquals(false, ArchiveShape.inside(out[0], out[1], out[2]), "in front of the doors");
        }
    }
}
