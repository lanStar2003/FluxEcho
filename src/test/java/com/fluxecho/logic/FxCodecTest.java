package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.BitSet;

import org.junit.jupiter.api.Test;

class FxCodecTest {

    @Test
    void theLayout() {
        assertEquals(128 | 128 << 8 | 512 << 16, FxCodec.pack(0, 0, 0));
        assertEquals(255 | 1 << 8 | 1023 << 16, FxCodec.pack(127, 511, -127));
        assertEquals(1 | 255 << 8, FxCodec.pack(-127, -512, 127));
    }

    @Test
    void everyValidOffsetRoundTripsToItsOwnInt() {
        BitSet seen = new BitSet(1 << 26);
        long count = 0;
        for (int dy = FxCodec.MIN_Y; dy <= FxCodec.MAX_Y; dy++)
            for (int dz = -FxCodec.MAX_XZ; dz <= FxCodec.MAX_XZ; dz++)
                for (int dx = -FxCodec.MAX_XZ; dx <= FxCodec.MAX_XZ; dx++) {
                    int p = FxCodec.pack(dx, dy, dz);
                    if (FxCodec.dx(p) != dx || FxCodec.dy(p) != dy || FxCodec.dz(p) != dz) fail(
                        "round trip of " + dx
                            + ","
                            + dy
                            + ","
                            + dz
                            + " gave "
                            + FxCodec.dx(p)
                            + ","
                            + FxCodec.dy(p)
                            + ","
                            + FxCodec.dz(p));
                    if (p < 0 || p >= 1 << 26) fail("packed " + dx + "," + dy + "," + dz + " out of 26 bits: " + p);
                    if (seen.get(p)) fail("two offsets share the packed value " + p);
                    seen.set(p);
                    count++;
                }
        assertEquals(255L * 255L * 1024L, count);
        assertEquals(count, seen.cardinality(), "no two offsets pack alike");
    }

    @Test
    void outOfRangeIsRejected() {
        int[][] bad = { { 128, 0, 0 }, { -128, 0, 0 }, { 0, 0, 128 }, { 0, 0, -128 }, { 0, 512, 0 }, { 0, -513, 0 },
            { 1000, 0, 0 }, { 0, 0, -1000 }, { 0, 4096, 0 }, { Integer.MIN_VALUE, 0, 0 }, { 0, Integer.MAX_VALUE, 0 },
            { 0, 0, Integer.MAX_VALUE }, { 128, 512, 128 } };
        for (int[] b : bad) {
            assertFalse(FxCodec.fits(b[0], b[1], b[2]), b[0] + "," + b[1] + "," + b[2]);
            assertThrows(
                IllegalArgumentException.class,
                () -> FxCodec.pack(b[0], b[1], b[2]),
                b[0] + "," + b[1] + "," + b[2]);
        }
    }

    @Test
    void theEdgesFit() {
        int[] xz = { -127, 0, 127 };
        int[] ys = { -512, 0, 511 };
        for (int dx : xz) for (int dz : xz) for (int dy : ys) {
            assertTrue(FxCodec.fits(dx, dy, dz));
            int p = FxCodec.pack(dx, dy, dz);
            assertEquals(dx, FxCodec.dx(p));
            assertEquals(dy, FxCodec.dy(p));
            assertEquals(dz, FxCodec.dz(p));
        }
    }

    @Test
    void aWholeCampusFits() {
        // the graded disc reaches 64 across and along, the clearance 24 up and the supports 24 down
        for (int a = -64; a <= 64; a++) for (int r = -64; r <= 64; r++) {
            assertTrue(FxCodec.fits(a, 24, r));
            assertTrue(FxCodec.fits(a, -24, r));
        }
    }
}
