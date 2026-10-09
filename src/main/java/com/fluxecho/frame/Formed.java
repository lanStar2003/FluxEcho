package com.fluxecho.frame;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.world.World;

/**
 * Which blocks of formed multiblocks have given way to what the multiblock draws in their place
 * (blueprint principle 5): hidden ones render nothing ({@link #HIDE}), some also let players through ({@link #PASS}).
 * Each formed controller sets its cells here on both sides; the frame blocks ask.
 * <p>
 * The client has one world at a time and its chunk builder may ask from another thread, so its table is an immutable
 * map swapped whole. The server keeps one per dimension and is only asked on the server thread.
 */
public final class Formed {

    public static final int HIDE = 1, PASS = 2;

    private static final Map<Long, Map<Long, Integer>> CLIENT_OWNERS = new LinkedHashMap<>();
    private static volatile Map<Long, Integer> client = Collections.emptyMap();

    private static final Map<Integer, Map<Long, Map<Long, Integer>>> SERVER_OWNERS = new HashMap<>();
    private static final Map<Integer, Map<Long, Integer>> SERVER = new HashMap<>();

    private Formed() {}

    /** A block position in one long (x and z 26 bits each, y 12 bits). */
    public static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (long) y & 0xFFFL;
    }

    /**
     * The cells of the multiblock whose controller is at {@code owner} ({@link #key}); empty or null when it is not
     * formed.
     */
    public static synchronized void set(World w, long owner, Map<Long, Integer> cells) {
        if (w == null) return;
        boolean empty = cells == null || cells.isEmpty();
        if (w.isRemote) {
            if (empty ? CLIENT_OWNERS.remove(owner) == null : cells.equals(CLIENT_OWNERS.put(owner, cells))) return;
            client = Collections.unmodifiableMap(union(CLIENT_OWNERS));
        } else {
            int dim = w.provider.dimensionId;
            Map<Long, Map<Long, Integer>> owners = SERVER_OWNERS.computeIfAbsent(dim, d -> new LinkedHashMap<>());
            if (empty ? owners.remove(owner) == null : cells.equals(owners.put(owner, cells))) return;
            SERVER.put(dim, union(owners));
        }
    }

    private static Map<Long, Integer> union(Map<Long, Map<Long, Integer>> owners) {
        Map<Long, Integer> out = new HashMap<>();
        for (Map<Long, Integer> cells : owners.values())
            for (Map.Entry<Long, Integer> e : cells.entrySet()) out.merge(e.getKey(), e.getValue(), (a, b) -> a | b);
        return out;
    }

    /** The flags of a block in this world. */
    public static int flags(World w, int x, int y, int z) {
        if (w == null) return 0;
        if (w.isRemote) return clientFlags(x, y, z);
        Map<Long, Integer> m;
        synchronized (Formed.class) {
            m = SERVER.get(w.provider.dimensionId);
        }
        Integer f = m == null ? null : m.get(key(x, y, z));
        return f == null ? 0 : f;
    }

    /** The flags of a block in the client's world; safe from any thread. */
    public static int clientFlags(int x, int y, int z) {
        Integer f = client.get(key(x, y, z));
        return f == null ? 0 : f;
    }

    /** Leaving a world: its multiblocks set themselves again when the next one loads. */
    public static synchronized void clearClient() {
        CLIENT_OWNERS.clear();
        client = Collections.emptyMap();
    }

    public static synchronized void clearServer() {
        SERVER_OWNERS.clear();
        SERVER.clear();
    }
}
