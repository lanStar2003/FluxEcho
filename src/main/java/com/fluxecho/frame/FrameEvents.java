package com.fluxecho.frame;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.World;

/**
 * Tells the multiblocks around a flux frame block that it was placed or broken, so they look at their structure at
 * once instead of at their next regular check. Server side; the controllers sign themselves up while loaded.
 */
public final class FrameEvents {

    /** A loaded multiblock controller. */
    public interface Watcher {

        /** Whether the block could belong to its structure. */
        boolean covers(World w, int x, int y, int z);

        /** Look at the structure again soon. */
        void frameChanged();
    }

    private static final List<Watcher> WATCHERS = new ArrayList<>();

    private FrameEvents() {}

    public static synchronized void watch(Watcher w) {
        if (!WATCHERS.contains(w)) WATCHERS.add(w);
    }

    public static synchronized void unwatch(Watcher w) {
        WATCHERS.remove(w);
    }

    public static void changed(World w, int x, int y, int z) {
        if (w == null || w.isRemote) return;
        List<Watcher> all;
        synchronized (FrameEvents.class) {
            all = new ArrayList<>(WATCHERS);
        }
        for (Watcher watcher : all) if (watcher.covers(w, x, y, z)) watcher.frameChanged();
    }

    public static synchronized void clear() {
        WATCHERS.clear();
    }
}
