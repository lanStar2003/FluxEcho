package com.fluxecho.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.nbt.NBTTagCompound;

/**
 * What the server last sent for each machine hologram, keyed by place; filled from the network thread, read by the
 * renderers. An entry not refreshed for a few seconds is dropped (the hologram was switched off, or the player left).
 */
public final class HoloStore {

    private static final long STALE_MS = 2500;

    public static final class Shown {

        public final byte kind;
        public final int dim, x, y, z;
        public volatile NBTTagCompound data;
        volatile long at;

        Shown(byte kind, int dim, int x, int y, int z) {
            this.kind = kind;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final Map<String, Shown> SHOWN = new ConcurrentHashMap<>();

    private HoloStore() {}

    public static void receive(byte kind, int dim, int x, int y, int z, NBTTagCompound data) {
        Shown s = SHOWN
            .computeIfAbsent(kind + ":" + dim + ":" + x + ":" + y + ":" + z, k -> new Shown(kind, dim, x, y, z));
        s.data = data;
        s.at = System.currentTimeMillis();
    }

    /** The fresh entries of a kind in a world; stale ones are dropped on the way. */
    public static List<Shown> current(byte kind, int dim) {
        List<Shown> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Iterator<Shown> it = SHOWN.values()
            .iterator(); it.hasNext();) {
            Shown s = it.next();
            if (now - s.at > STALE_MS) it.remove();
            else if (s.kind == kind && s.dim == dim) out.add(s);
        }
        return out;
    }

    public static void forget(Shown s) {
        SHOWN.values()
            .remove(s);
    }
}
