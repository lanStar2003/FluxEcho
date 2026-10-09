package com.fluxecho.logic;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * One 16 x 16 x 16 section of what a light gate shows, as the server sends it: block ids and metadata (16 bits each,
 * as NotEnoughIDs allows), sky and block light, and the biomes of its 16 x 16 columns. Mostly air, so it is deflated.
 * Block index: {@code y << 8 | z << 4 | x}, inside the section.
 */
public final class MirrorSection {

    public static final int BLOCKS = 4096, COLUMNS = 256;
    private static final int SIZE = BLOCKS * 5 + COLUMNS;

    public final char[] ids = new char[BLOCKS], meta = new char[BLOCKS];
    /** Sky light in the high four bits, block light in the low four. */
    public final byte[] light = new byte[BLOCKS];
    public final byte[] biomes = new byte[COLUMNS];

    public static int index(int x, int y, int z) {
        return (y & 15) << 8 | (z & 15) << 4 | (x & 15);
    }

    public byte[] encode() {
        byte[] raw = new byte[SIZE];
        int o = 0;
        for (int i = 0; i < BLOCKS; i++) {
            raw[o++] = (byte) (ids[i] >> 8);
            raw[o++] = (byte) ids[i];
            raw[o++] = (byte) (meta[i] >> 8);
            raw[o++] = (byte) meta[i];
            raw[o++] = light[i];
        }
        System.arraycopy(biomes, 0, raw, o, COLUMNS);
        Deflater d = new Deflater(Deflater.BEST_SPEED);
        try {
            d.setInput(raw);
            d.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(512);
            byte[] buf = new byte[4096];
            while (!d.finished()) out.write(buf, 0, d.deflate(buf));
            return out.toByteArray();
        } finally {
            d.end();
        }
    }

    /** The section a server sent; null when the bytes are not one. */
    public static MirrorSection decode(byte[] data) {
        byte[] raw = new byte[SIZE];
        Inflater inf = new Inflater();
        try {
            inf.setInput(data);
            int n = 0;
            while (n < SIZE && !inf.finished()) {
                int got = inf.inflate(raw, n, SIZE - n);
                if (got == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                n += got;
            }
            if (n != SIZE) return null;
        } catch (DataFormatException e) {
            return null;
        } finally {
            inf.end();
        }
        MirrorSection s = new MirrorSection();
        int o = 0;
        for (int i = 0; i < BLOCKS; i++) {
            s.ids[i] = (char) ((raw[o] & 255) << 8 | raw[o + 1] & 255);
            s.meta[i] = (char) ((raw[o + 2] & 255) << 8 | raw[o + 3] & 255);
            s.light[i] = raw[o + 4];
            o += 5;
        }
        System.arraycopy(raw, o, s.biomes, 0, COLUMNS);
        return s;
    }

    /** One long for a section's coordinates: x and z within +-2^21 sections, y within 0..255. */
    public static long key(int sx, int sy, int sz) {
        return ((long) (sx & 0x3FFFFF) << 30) | ((long) (sz & 0x3FFFFF) << 8) | (sy & 0xFF);
    }

    public static int keyX(long key) {
        return (int) (key << 12 >> 42);
    }

    public static int keyZ(long key) {
        return (int) (key << 34 >> 42);
    }

    public static int keyY(long key) {
        return (int) (key & 0xFF);
    }
}
