package com.fluxecho.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.World;

/**
 * Every flux machine the server has seen running lately, for the flux terminal's machine page: the echo machines, the
 * shard collectors and fluid pumps, the Flux Nexus and its modules. Each loaded one reports every few seconds; one
 * not heard from shows as unloaded, and is forgotten after an hour. In memory only.
 */
public final class Directory {

    /** How a machine is doing: working, idle (all well), or wanting something. */
    public static final int WORKING = 0, IDLE = 1, PROBLEM = 2;

    public static final class Entry {

        public final UUID team;
        public final int dim, x, y, z;
        public final String name, status;
        public final int level;
        public final long seen;

        Entry(UUID team, int dim, int x, int y, int z, String name, String status, int level, long seen) {
            this.team = team;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.name = name;
            this.status = status;
            this.level = level;
            this.seen = seen;
        }
    }

    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static final long FORGET_MS = 3_600_000;

    private Directory() {}

    /**
     * A machine reports itself.
     *
     * @param name   lang key of its name
     * @param status lang key of what it does
     */
    public static void report(World w, int x, int y, int z, UUID team, String name, String status, int level) {
        if (w == null || w.isRemote || team == null) return;
        int dim = w.provider.dimensionId;
        Entry e = new Entry(team, dim, x, y, z, name, status, level, System.currentTimeMillis());
        synchronized (ENTRIES) {
            ENTRIES.put(dim + ":" + x + ":" + y + ":" + z, e);
        }
    }

    /** The team's machines, those wanting something first. */
    public static List<Entry> of(UUID team) {
        List<Entry> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        synchronized (ENTRIES) {
            for (Iterator<Entry> it = ENTRIES.values()
                .iterator(); it.hasNext();) {
                Entry e = it.next();
                if (now - e.seen > FORGET_MS) it.remove();
                else if (e.team.equals(team)) out.add(e);
            }
        }
        out.sort((a, b) -> a.level != b.level ? b.level - a.level : a.name.compareTo(b.name));
        return out;
    }

    /** Level of an echo machine's status key ({@code fluxecho.status.}). */
    public static int level(String status) {
        if ("working".equals(status)) return WORKING;
        if (status == null || "idle".equals(status) || "ready".equals(status)) return IDLE;
        return PROBLEM;
    }

    public static void clear() {
        synchronized (ENTRIES) {
            ENTRIES.clear();
        }
    }
}
