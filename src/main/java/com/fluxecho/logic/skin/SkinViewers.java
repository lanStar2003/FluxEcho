package com.fluxecho.logic.skin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Who has which campus's box table (style §3.9.3): a player has, in the dimension they are in, the campuses that came
 * within 512 blocks of them, until they are more than 640 away (so a player on the edge does not make the table come
 * and go). The server's feed asks this whom to send what: the whole table on a login and a change of dimension, a
 * campus's table when one comes within range, a changed entry to the players that have its campus. Pure: players are
 * keys, campuses are their packed centres.
 */
public final class SkinViewers {

    /** A campus comes within reach at 512 blocks across the ground, and is dropped past 640. */
    public static final int RANGE = 512, DROP = 640;

    /** A campus: its packed centre and its dimension. */
    public static final class Site {

        public final long id;
        public final int dim;

        public Site(long id, int dim) {
            this.id = id;
            this.dim = dim;
        }
    }

    /** One player's state: their dimension and the campuses they have. */
    private static final class Viewer {

        int dim;
        final Set<Long> has = new LinkedHashSet<>();
    }

    private final Map<Object, Viewer> viewers = new HashMap<>();

    /**
     * A player logs in, changes dimension or respawns in another: they get the whole table of the campuses within
     * reach in their dimension (possibly none), and have those from now on. Returns the campuses to send.
     */
    public List<Long> enter(Object player, int dim, double x, double z, Collection<Site> sites) {
        Viewer v = new Viewer();
        v.dim = dim;
        viewers.put(player, v);
        List<Long> out = new ArrayList<>();
        for (Site s : sites) if (s.dim == dim && near(s.id, x, z, RANGE)) {
            v.has.add(s.id);
            out.add(s.id);
        }
        return out;
    }

    /** The dimension the player was last given a table of, or null for a player not known. */
    public Integer dim(Object player) {
        Viewer v = viewers.get(player);
        return v == null ? null : v.dim;
    }

    /**
     * The check every 100 ticks: the campuses that came within 512 blocks of the player since (to send), the ones past
     * 640 dropped. A player not known yet, or found in another dimension, needs {@link #enter} instead: null.
     */
    public List<Long> check(Object player, int dim, double x, double z, Collection<Site> sites) {
        Viewer v = viewers.get(player);
        if (v == null || v.dim != dim) return null;
        List<Long> out = new ArrayList<>();
        Set<Long> live = new LinkedHashSet<>();
        for (Site s : sites) {
            if (s.dim != dim) continue;
            live.add(s.id);
            if (!v.has.contains(s.id) && near(s.id, x, z, RANGE)) {
                v.has.add(s.id);
                out.add(s.id);
            }
        }
        // past 640, or gone, the campus is dropped; it is sent again when it comes back within 512
        v.has.removeIf(id -> !live.contains(id) || !near(id, x, z, DROP));
        return out;
    }

    /** The players in the dimension that have the campus. */
    public List<Object> watchers(int dim, long campus) {
        List<Object> out = new ArrayList<>();
        for (Map.Entry<Object, Viewer> e : viewers.entrySet())
            if (e.getValue().dim == dim && e.getValue().has.contains(campus)) out.add(e.getKey());
        return out;
    }

    /** The player left. */
    public void leave(Object player) {
        viewers.remove(player);
    }

    /** A campus is gone: nobody has it any more. */
    public void forget(int dim, long campus) {
        for (Viewer v : viewers.values()) if (v.dim == dim) v.has.remove(campus);
    }

    public void clear() {
        viewers.clear();
    }

    /** Whether a campus centre lies within {@code r} blocks of the point across the ground. */
    static boolean near(long campus, double x, double z, int r) {
        int[] c = SkinEntry.unpack(campus);
        double dx = c[0] - x, dz = c[2] - z;
        return dx * dx + dz * dz <= (double) r * r;
    }
}
