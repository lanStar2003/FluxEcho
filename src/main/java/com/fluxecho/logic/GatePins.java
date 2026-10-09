package com.fluxecho.logic;

import java.util.HashSet;
import java.util.Set;

/**
 * Which chunks a player keeps watching besides the square around them, so that both sides of a light gate are
 * loaded for them and walking through moves nothing in or out:
 * <ul>
 * <li>near a gate: what its partner's side shows through it (all of a room, or the outside in front of a room's
 * gate);</li>
 * <li>inside a room: the whole room, and the square around the room's outside gate, as wide as the server's view,
 * so going back out finds everything where it was.</li>
 * </ul>
 * Chunks are keyed as Minecraft's {@code ChunkCoordIntPair.chunkXZ2Int} keys them.
 */
public final class GatePins {

    /** What a room's gate shows of the outside: this deep in front of the outside gate, this wide, below and above. */
    public static final int VIEW_DEPTH = 64, VIEW_HALF = 40, VIEW_BELOW = 16, VIEW_ABOVE = 40;

    private GatePins() {}

    public static long key(int cx, int cz) {
        return (long) cx & 0xFFFFFFFFL | ((long) cz & 0xFFFFFFFFL) << 32;
    }

    public static int keyX(long k) {
        return (int) k;
    }

    public static int keyZ(long k) {
        return (int) (k >>> 32);
    }

    /** What a room's gate shows: the outside in front of its partner. */
    public static GateGeometry.Box outsideView(GateGeometry.Gate outside) {
        return GateGeometry.view(outside, VIEW_DEPTH, VIEW_HALF, VIEW_BELOW, VIEW_ABOVE);
    }

    public static void addBox(Set<Long> out, GateGeometry.Box b) {
        for (int cx = b.minX >> 4; cx <= b.maxX >> 4; cx++)
            for (int cz = b.minZ >> 4; cz <= b.maxZ >> 4; cz++) out.add(key(cx, cz));
    }

    public static void addSquare(Set<Long> out, int cx, int cz, int radius) {
        for (int x = cx - radius; x <= cx + radius; x++)
            for (int z = cz - radius; z <= cz + radius; z++) out.add(key(x, z));
    }

    /** Whether a chunk is in the square of {@code radius} chunks round {@code (cx, cz)}, as the server sends them. */
    public static boolean inSquare(long k, int cx, int cz, int radius) {
        return Math.abs(keyX(k) - cx) <= radius && Math.abs(keyZ(k) - cz) <= radius;
    }

    /** A linked gate: where it stands and the blocks its partner's side shows through it. */
    public static final class Linked {

        public final GateGeometry.Gate gate;
        public final GateGeometry.Box far;

        public Linked(GateGeometry.Gate gate, GateGeometry.Box far) {
            this.gate = gate;
            this.far = far;
        }

        boolean near(double x, double y, double z, double range) {
            double dx = gate.cx() - x, dy = gate.y + 1.5 - y, dz = gate.cz() - z;
            return dx * dx + dy * dy + dz * dz <= range * range;
        }
    }

    /**
     * The chunks a player at {@code (x, y, z)} keeps. {@code gates} are the linked gates of their dimension;
     * {@code room} is the room they are in and {@code outside} its outside gate (both null when in no room);
     * {@code hold} is how many chunks round the outside gate stay theirs while in the room, 0 for none.
     */
    public static Set<Long> wanted(double x, double y, double z, Iterable<Linked> gates, double range,
        GateGeometry.Box room, GateGeometry.Gate outside, int hold) {
        Set<Long> out = new HashSet<>();
        for (Linked g : gates) if (g.near(x, y, z, range)) addBox(out, g.far);
        if (room != null) {
            addBox(out, room);
            if (outside != null) {
                addBox(out, outsideView(outside));
                if (hold > 0) addSquare(out, outside.x >> 4, outside.z >> 4, hold);
            }
        }
        return out;
    }
}
