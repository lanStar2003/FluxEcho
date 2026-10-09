package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

class MirrorSectionTest {

    @Test
    void comesBackAsItWent() {
        MirrorSection s = new MirrorSection();
        Random r = new Random(7);
        for (int i = 0; i < MirrorSection.BLOCKS; i++) {
            s.ids[i] = (char) r.nextInt(40000); // NotEnoughIDs: past 4095
            s.meta[i] = (char) r.nextInt(40000);
            s.light[i] = (byte) r.nextInt(256);
        }
        for (int i = 0; i < MirrorSection.COLUMNS; i++) s.biomes[i] = (byte) r.nextInt(256);
        MirrorSection back = MirrorSection.decode(s.encode());
        assertArrayEquals(s.ids, back.ids);
        assertArrayEquals(s.meta, back.meta);
        assertArrayEquals(s.light, back.light);
        assertArrayEquals(s.biomes, back.biomes);
    }

    @Test
    void airIsSmallAndFitsAPacket() {
        MirrorSection air = new MirrorSection();
        Arrays.fill(air.light, (byte) 0xF0);
        assertTrue(air.encode().length < 200, "an empty section is a few bytes");
        MirrorSection noise = new MirrorSection();
        Random r = new Random(1);
        for (int i = 0; i < MirrorSection.BLOCKS; i++) {
            noise.ids[i] = (char) r.nextInt(65536);
            noise.meta[i] = (char) r.nextInt(65536);
            noise.light[i] = (byte) r.nextInt(256);
        }
        assertTrue(noise.encode().length < 30000, "even the worst section fits one packet");
    }

    @Test
    void badBytesAreNoSection() {
        assertNull(MirrorSection.decode(new byte[] { 1, 2, 3 }));
        byte[] good = new MirrorSection().encode();
        assertNull(MirrorSection.decode(Arrays.copyOf(good, good.length / 2)));
    }

    @Test
    void keysKeepNegativeCoordinates() {
        int[][] cases = { { 0, 0, 0 }, { -1, 15, -1 }, { 64, 4, -2 }, { -1875000, 255, 1875000 }, { 2047, 3, -2048 } };
        for (int[] c : cases) {
            long k = MirrorSection.key(c[0], c[1], c[2]);
            assertEquals(c[0], MirrorSection.keyX(k));
            assertEquals(c[1], MirrorSection.keyY(k));
            assertEquals(c[2], MirrorSection.keyZ(k));
        }
        assertEquals(MirrorSection.index(15, 15, 15), MirrorSection.BLOCKS - 1);
    }
}
