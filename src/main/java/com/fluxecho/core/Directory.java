package com.fluxecho.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.fluxecho.Config;
import com.fluxecho.campus.BuildJob;
import com.fluxecho.campus.Campus;
import com.fluxecho.logic.BuildState;
import com.fluxecho.nexus.TileNexus;

/**
 * Every flux machine the server has seen running lately, for the flux terminal's machine page: the echo machines, the
 * shard collectors and fluid pumps, the Flux Nexus and its modules. Each loaded one reports every few seconds; one
 * not heard from shows as unloaded, and is forgotten after an hour. In memory only.
 * <p>
 * A Flux Nexus whose campus is building reports that instead of its own state ({@link #nexusStatus}): 建造中 while a
 * started job is under way (working), 建造暂停 while it is paused (a problem, or idle when a member paused it). The
 * nexus's GUI header, its Waila line and the terminal's nexus page use the same rule.
 */
public final class Directory {

    /** How a machine is doing: working, idle (all well), or wanting something. */
    public static final int WORKING = 0, IDLE = 1, PROBLEM = 2;

    /** The nexus's name key, as it reports itself. */
    public static final String NEXUS = "tile.fluxecho.nexus.name";
    /** The prefix of the nexus's status keys. */
    public static final String NEXUS_STATUS = "fluxecho.nexus.status.";
    /** The nexus statuses of its campus (under {@link #NEXUS_STATUS}): a job under way, a job paused. */
    public static final String BUILDING = "building", BUILD_PAUSED = "build_paused";

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
        if (NEXUS.equals(name) && w.blockExists(x, y, z)) {
            // a nexus that builds says so instead of what its own tick saw
            TileEntity te = w.getTileEntity(x, y, z);
            if (te instanceof TileNexus n) {
                String own = status != null && status.startsWith(NEXUS_STATUS) ? status.substring(NEXUS_STATUS.length())
                    : "";
                String now = nexusStatus(own, n.campus());
                if (!now.equals(own)) {
                    status = NEXUS_STATUS + now;
                    level = nexusLevel(now, n.campus());
                }
            }
        }
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

    /**
     * The status (under {@link #NEXUS_STATUS}) a nexus shows with its campus considered: {@link #BUILD_PAUSED} while
     * its job is paused (a member's pause only when the nexus has nothing better to say), {@link #BUILDING} while a
     * started job is under way and the nexus does no other work, else its own status. Server side.
     */
    public static String nexusStatus(TileNexus n) {
        return nexusStatus(n.status(), n.campus());
    }

    /** {@link #nexusStatus(TileNexus)} for the nexus's own status {@code own} and its campus. */
    public static String nexusStatus(String own, Campus campus) {
        // a campus that does not tick builds nothing, whatever its job says
        BuildJob j = campus == null || !campus.active() || !Config.nexusEnabled || !Config.campusEnabled ? null
            : campus.job();
        if (j == null) return own;
        return nexusStatus(own, j.state(), j.pause(), j.started());
    }

    /**
     * The rule of {@link #nexusStatus(TileNexus)} on plain values: the nexus's own status, its job's state and pause,
     * and whether a member started the job.
     */
    public static String nexusStatus(String own, BuildState.State state, BuildState.Pause pause, boolean started) {
        if (state == null || BuildState.ended(state) || "disabled".equals(own)) return own;
        boolean working = "researching".equals(own) || "manifesting".equals(own);
        if (state == BuildState.State.PAUSED) return pause == BuildState.Pause.PLAYER && working ? own : BUILD_PAUSED;
        boolean underWay = state == BuildState.State.BUILDING || state == BuildState.State.WAITING
            || state == BuildState.State.SURVEY && started;
        return underWay && !working ? BUILDING : own;
    }

    /** The level of a construction status: building works, a member's pause is idle, any other pause a problem. */
    static int nexusLevel(String status, Campus campus) {
        if (BUILDING.equals(status)) return WORKING;
        BuildJob j = campus == null ? null : campus.job();
        return j != null && j.pause() == BuildState.Pause.PLAYER ? IDLE : PROBLEM;
    }

    public static void clear() {
        synchronized (ENTRIES) {
            ENTRIES.clear();
        }
    }
}
