package com.fluxecho.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The trails a machine draws to the blocks it hands something to (see {@code EchoNet.flow}): filled from the network
 * thread, read by {@link FlowFx}. A trail lasts a little longer than the second between two messages, so a steady
 * flow shows as one unbroken trail and fades out soon after it stops.
 */
public final class FlowStore {

    static final long LIFE_MS = 1600;

    public static final class Flow {

        public final int dim, x, y, z, tx, ty, tz;
        public volatile int color;
        /** When it was first and last heard of. */
        public final long since;
        volatile long at;

        Flow(int dim, int x, int y, int z, int tx, int ty, int tz, long now) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.tx = tx;
            this.ty = ty;
            this.tz = tz;
            this.since = now;
        }

        /** 0..1: fades in when it starts and out when it is no longer refreshed. */
        public float strength(long now) {
            float in = Math.min(1f, (now - since) / 300f), out = Math.min(1f, (LIFE_MS - (now - at)) / 400f);
            return Math.max(0f, Math.min(in, out));
        }
    }

    private static final Map<String, Flow> FLOWS = new ConcurrentHashMap<>();

    private FlowStore() {}

    public static void receive(int dim, int x, int y, int z, int tx, int ty, int tz, int color) {
        long now = System.currentTimeMillis();
        Flow f = FLOWS.computeIfAbsent(
            dim + ":" + x + ":" + y + ":" + z + ">" + tx + ":" + ty + ":" + tz,
            k -> new Flow(dim, x, y, z, tx, ty, tz, now));
        f.color = color;
        f.at = now;
    }

    /** The live trails in a world; dead ones are dropped on the way. */
    public static List<Flow> current(int dim) {
        List<Flow> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Iterator<Flow> it = FLOWS.values()
            .iterator(); it.hasNext();) {
            Flow f = it.next();
            if (now - f.at > LIFE_MS) it.remove();
            else if (f.dim == dim) out.add(f);
        }
        return out;
    }
}
