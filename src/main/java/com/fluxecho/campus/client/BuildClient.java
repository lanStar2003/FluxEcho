package com.fluxecho.campus.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.WorldEvent;

import com.fluxecho.campus.BuildJob;
import com.fluxecho.campus.Campus;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.BuildState;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.FxCodec;
import com.fluxecho.logic.Mix;
import com.fluxecho.nexus.TileNexus;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * What the client knows about the construction going on round each nexus, for the renderers to draw from (they only
 * read): per nexus (dimension and controller position) the latest progress numbers ({@link State}, from
 * {@code BUILD_STATE}), the cells in flight ({@link Launch}) and the cells just cleared ({@link Clear}, from
 * {@code BUILD_FX}), all with absolute world ticks; and a cache of the job's plan, computed from the nexus's synced
 * {@link Campus.View} exactly as the server computes it.
 * <p>
 * Entries are pruned in the client tick once they are over ({@link #prune}); progress numbers not refreshed for
 * {@link #STATE_TTL} ticks (the server repeats them every few seconds while a job runs) are dropped, as are numbers
 * the nexus's description contradicts ({@link #viewChanged}), so a player who walked away and back is not shown an old
 * state. Everything is forgotten when the client leaves its world ({@link #clear}); the hooks register themselves the
 * first time a packet arrives. Packets are
 * stored on the client thread; each nexus's lists are immutable snapshots replaced wholesale, so a reader on any
 * thread sees a consistent list without locking.
 * <p>
 * The class itself touches no client-only class outside {@link Hooks}, so common code may refer to it.
 */
public final class BuildClient {

    /** Ticks a landed launch stays listed (the landing flash). */
    public static final int LAND_LINGER = 10;
    /** Ticks a clear stays listed after it happened. */
    public static final int CLEAR_LINGER = 20;
    /** The most entries kept per nexus and kind, and the most clears waiting for their particles. */
    public static final int MAX_PER_NEXUS = 512, MAX_PENDING = 512;
    /** Entries further in the future than this are taken as a clock mix-up and dropped. */
    static final int MAX_AHEAD = 400;
    /** Ticks progress numbers stay without being sent again (three of the server's heartbeats). */
    public static final int STATE_TTL = 300;

    /** A nexus on the client: its dimension and controller. */
    public static final class Key {

        public final int dim, x, y, z;

        public Key(int dim, int x, int y, int z) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key k)) return false;
            return k.dim == dim && k.x == x && k.y == y && k.z == z;
        }

        @Override
        public int hashCode() {
            return ((dim * 31 + x) * 31 + y) * 31 + z;
        }

        @Override
        public String toString() {
            return dim + ":" + x + "," + y + "," + z;
        }
    }

    /** A cell launched from the core: it flies from {@link #start} and lands (prints in) at {@link #land}. */
    public static final class Launch {

        public final int dim, x, y, z;
        /** The part code ({@link com.fluxecho.logic.Parts}). */
        public final int part;
        /** World ticks it left the core and lands. */
        public final long start, land;

        public Launch(int dim, int x, int y, int z, int part, long start, long land) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.part = part;
            this.start = start;
            this.land = land;
        }
    }

    /** A cell the builder cleared: the block that was there and when. */
    public static final class Clear {

        public final int dim, x, y, z;
        /** {@code Block.getIdFromBlock} of the block that was there, and its meta. */
        public final int block, meta;
        /** The world tick of its batch (the clear happened within the four ticks after it). */
        public final long at;

        public Clear(int dim, int x, int y, int z, int block, int meta, long at) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block;
            this.meta = meta;
            this.at = at;
        }
    }

    /** The progress numbers of a nexus's current job, as the server last sent them. */
    public static final class State {

        /** The job key and its plan key; both empty when the nexus has no job. */
        public final String job, planKey;
        public final int state, pause, stage, placed, total, cleared, blocked, skipped, unloaded, flights;
        /** Cells the grading fills under the floor level (the survey's count), and cells filled so far. */
        public final int toFill, filled;
        /** EU the builder drew in its last tick, and the estimated ticks left (-1: cannot progress). */
        public final long eu, eta;
        /** The server's world tick when it was sent. */
        public final long at;

        State(NBTTagCompound t) {
            job = t.getString("K");
            planKey = t.hasKey("Pk") ? t.getString("Pk") : job;
            state = t.getByte("St");
            pause = t.getByte("Ps");
            stage = t.getByte("Sg");
            placed = t.getInteger("Pl");
            total = t.getInteger("To");
            cleared = t.getInteger("Cl");
            blocked = t.getInteger("Bk");
            skipped = t.getInteger("Sk");
            unloaded = t.getInteger("Un");
            toFill = t.getInteger("Tf");
            filled = t.getInteger("Fd");
            flights = t.getShort("Fl");
            eu = t.getLong("Eu");
            eta = t.getLong("Et");
            at = t.getLong("W");
        }

        public boolean hasJob() {
            return !job.isEmpty();
        }

        public BuildState.State stateEnum() {
            BuildState.State[] v = BuildState.State.values();
            return state >= 0 && state < v.length ? v[state] : BuildState.State.SURVEY;
        }

        public BuildState.Pause pauseEnum() {
            BuildState.Pause[] v = BuildState.Pause.values();
            return pause >= 0 && pause < v.length ? v[pause] : BuildState.Pause.NONE;
        }

        /** Placed steps as a share of the plan, 0..1. */
        public float fraction() {
            return total <= 0 ? 0 : Math.min(1f, placed / (float) total);
        }
    }

    /** Everything known about one nexus; replaced wholesale on every change. */
    public static final class Site {

        public final Key key;
        /** The nexus's centre {x, y0, z}, or null before the first effect packet. */
        public final int[] centre;
        /** The latest progress numbers, or null before the first state packet. */
        public final State state;
        /** Cells in flight or just landed, and cells just cleared (unmodifiable). */
        public final List<Launch> launches;
        public final List<Clear> clears;

        Site(Key key, int[] centre, State state, List<Launch> launches, List<Clear> clears) {
            this.key = key;
            this.centre = centre;
            this.state = state;
            this.launches = launches;
            this.clears = clears;
        }

        Site with(int[] c, State s, List<Launch> l, List<Clear> cl) {
            return new Site(key, c, s, l, cl);
        }

        boolean empty() {
            return state == null && launches.isEmpty() && clears.isEmpty();
        }
    }

    private static final Map<Key, Site> SITES = new ConcurrentHashMap<>();
    private static final Queue<Clear> PENDING = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger PENDING_SIZE = new AtomicInteger();
    private static final Map<Key, CachedPlan> PLANS = new ConcurrentHashMap<>();
    private static final AtomicBoolean HOOKED = new AtomicBoolean();

    private BuildClient() {}

    // ---- receiving (client thread)

    /** A {@code BUILD_FX} packet arrived (see {@link com.fluxecho.campus.CampusNet} for its fields). */
    public static void onFx(NBTTagCompound t) {
        hook();
        Key k = key(t);
        int[] c = t.getIntArray("Ce");
        if (c.length != 3) return;
        long base = t.getLong("T");
        List<Launch> launches = new ArrayList<>();
        int[] cells = t.getIntArray("P");
        byte[] parts = t.getByteArray("M"), flights = t.getByteArray("L"), offsets = t.getByteArray("O");
        for (int i = 0; i < cells.length && i < parts.length && i < flights.length; i++) {
            long start = base + (i < offsets.length ? offsets[i] : 0);
            launches.add(
                new Launch(
                    k.dim,
                    c[0] + FxCodec.dx(cells[i]),
                    c[1] + FxCodec.dy(cells[i]),
                    c[2] + FxCodec.dz(cells[i]),
                    parts[i] & 0xFF,
                    start,
                    start + Math.max(0, flights[i])));
        }
        List<Clear> clears = new ArrayList<>();
        int[] cc = t.getIntArray("C"), blocks = t.getIntArray("Cb");
        for (int i = 0; i < cc.length && i < blocks.length; i++) {
            Clear cl = new Clear(
                k.dim,
                c[0] + FxCodec.dx(cc[i]),
                c[1] + FxCodec.dy(cc[i]),
                c[2] + FxCodec.dz(cc[i]),
                blocks[i] >>> 4,
                blocks[i] & 15,
                base);
            clears.add(cl);
            if (PENDING_SIZE.incrementAndGet() > MAX_PENDING) {
                // the oldest clears lose their particles first
                if (PENDING.poll() != null) PENDING_SIZE.decrementAndGet();
            }
            PENDING.add(cl);
        }
        Site s = SITES.get(k);
        if (s == null) s = new Site(k, c, null, Collections.emptyList(), Collections.emptyList());
        SITES.put(k, s.with(c, s.state, append(s.launches, launches), append(s.clears, clears)));
    }

    /** A {@code BUILD_STATE} packet arrived. */
    public static void onState(NBTTagCompound t) {
        hook();
        Key k = key(t);
        State st = new State(t);
        int[] c = t.getIntArray("Ce");
        Site s = SITES.get(k);
        if (s == null) s = new Site(k, null, null, Collections.emptyList(), Collections.emptyList());
        SITES.put(k, s.with(c.length == 3 ? c : s.centre, st, s.launches, s.clears));
    }

    /**
     * The nexus's description arrived ({@link Campus.View}): progress numbers of another job or state than it names
     * are out of date (they were sent before the transition, or before the player left) and are dropped until the
     * next {@code BUILD_STATE}.
     */
    public static void viewChanged(int dim, int x, int y, int z, Campus.View v) {
        if (v == null) return;
        Key k = new Key(dim, x, y, z);
        Site s = SITES.get(k);
        if (s == null || s.state == null) return;
        if (s.state.job.equals(v.job) && s.state.state == v.state) return;
        Site next = s.with(s.centre, null, s.launches, s.clears);
        if (next.empty()) SITES.remove(k, s);
        else SITES.replace(k, s, next);
    }

    private static Key key(NBTTagCompound t) {
        return new Key(t.getInteger("Dim"), t.getInteger("X"), t.getInteger("Y"), t.getInteger("Z"));
    }

    private static <E> List<E> append(List<E> old, List<E> more) {
        if (more.isEmpty()) return old;
        List<E> out = new ArrayList<>(old.size() + more.size());
        out.addAll(old);
        out.addAll(more);
        // keep the newest when a nexus floods the store
        if (out.size() > MAX_PER_NEXUS) out = new ArrayList<>(out.subList(out.size() - MAX_PER_NEXUS, out.size()));
        return Collections.unmodifiableList(out);
    }

    // ---- reading (any thread)

    /** What is known about the nexus whose controller is at the position, or null. */
    public static Site site(int dim, int x, int y, int z) {
        return SITES.get(new Key(dim, x, y, z));
    }

    /** Every nexus with something known in the dimension. */
    public static List<Site> sites(int dim) {
        List<Site> out = new ArrayList<>();
        for (Site s : SITES.values()) if (s.key.dim == dim) out.add(s);
        return out;
    }

    /** Every nexus with something known. */
    public static Collection<Site> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(SITES.values()));
    }

    /** The latest progress numbers of the nexus, or null. */
    public static State state(int dim, int x, int y, int z) {
        Site s = site(dim, x, y, z);
        return s == null ? null : s.state;
    }

    /** The nexus's cells in flight or just landed (empty when none). */
    public static List<Launch> launches(int dim, int x, int y, int z) {
        Site s = site(dim, x, y, z);
        return s == null ? Collections.emptyList() : s.launches;
    }

    /** The nexus's cells cleared lately (empty when none). */
    public static List<Clear> clears(int dim, int x, int y, int z) {
        Site s = site(dim, x, y, z);
        return s == null ? Collections.emptyList() : s.clears;
    }

    /**
     * Takes up to {@code max} clears that have not had their break particles yet, oldest first; each clear is handed
     * out once (the client tick spawns the particles, not the renderer).
     */
    public static List<Clear> drainClears(int max) {
        List<Clear> out = new ArrayList<>();
        Clear c;
        while (out.size() < max && (c = PENDING.poll()) != null) {
            PENDING_SIZE.decrementAndGet();
            out.add(c);
        }
        return out;
    }

    // ---- the plan

    private static final class CachedPlan {

        final String sig;
        final BuildPlan.Plan plan;

        CachedPlan(String sig, BuildPlan.Plan plan) {
            this.sig = sig;
            this.plan = plan;
        }
    }

    /**
     * The plan of the nexus's current job as the server builds it: computed from the synced job ({@link Campus.View}:
     * plan key, version, keep-out boxes), the nexus's centre and front and the campus seed of its controller, and
     * cached until one of those changes. Null when the nexus has no job or the plan key names a module this game does
     * not know. Computing a module's plan takes a moment, so call it from the client thread, not while meshing.
     */
    public static BuildPlan.Plan plan(TileNexus n) {
        if (n == null || n.getWorldObj() == null) return null;
        Campus.View v = n.clientCampus;
        if (v == null || !v.active || !v.hasJob()) return null;
        int[] c = n.centre();
        ForgeDirection f = n.front();
        StringBuilder sig = new StringBuilder(v.planKey).append('|')
            .append(v.version)
            .append('|')
            .append(c[0])
            .append(',')
            .append(c[1])
            .append(',')
            .append(c[2])
            .append('|')
            .append(f.ordinal());
        for (int[] b : v.keepOut) sig.append('|')
            .append(b[0])
            .append(',')
            .append(b[1])
            .append(',')
            .append(b[2])
            .append(',')
            .append(b[3]);
        Key k = new Key(n.getWorldObj().provider.dimensionId, n.xCoord, n.yCoord, n.zCoord);
        String s = sig.toString();
        CachedPlan cached = PLANS.get(k);
        if (cached != null && cached.sig.equals(s)) return cached.plan;
        hook();
        BuildPlan.Plan p;
        try {
            p = BuildJob.planFor(
                v.planKey,
                new CampusPlan(Mix.seed(n.xCoord, n.yCoord, n.zCoord)),
                c[0],
                c[1],
                c[2],
                f.offsetX,
                f.offsetZ,
                n.xCoord,
                n.yCoord,
                n.zCoord,
                v.keepOut);
        } catch (RuntimeException e) {
            com.fluxecho.FluxEcho.LOG.warn("Could not compute the build plan {} of the nexus at {}", v.planKey, k, e);
            p = null;
        }
        PLANS.put(k, new CachedPlan(s, p));
        return p;
    }

    // ---- housekeeping

    /**
     * Drops the launches and clears that are over at the world tick, and progress numbers older than
     * {@link #STATE_TTL}; nexuses with nothing left are forgotten.
     */
    public static void prune(long worldTime) {
        for (Map.Entry<Key, Site> e : SITES.entrySet()) {
            Site s = e.getValue();
            List<Launch> l = s.launches;
            List<Clear> c = s.clears;
            List<Launch> keepL = l;
            List<Clear> keepC = c;
            State st = s.state;
            if (st != null && (worldTime - st.at > STATE_TTL || st.at > worldTime + MAX_AHEAD)) st = null;
            if (!l.isEmpty()) {
                List<Launch> k = new ArrayList<>(l.size());
                for (Launch x : l) if (x.land + LAND_LINGER >= worldTime && x.start <= worldTime + MAX_AHEAD) k.add(x);
                if (k.size() != l.size()) keepL = Collections.unmodifiableList(k);
            }
            if (!c.isEmpty()) {
                List<Clear> k = new ArrayList<>(c.size());
                for (Clear x : c) if (x.at + CLEAR_LINGER >= worldTime && x.at <= worldTime + MAX_AHEAD) k.add(x);
                if (k.size() != c.size()) keepC = Collections.unmodifiableList(k);
            }
            if (keepL == l && keepC == c && st == s.state) continue;
            Site next = s.with(s.centre, st, keepL, keepC);
            if (next.empty()) SITES.remove(e.getKey(), s);
            else SITES.replace(e.getKey(), s, next);
        }
        if (PENDING_SIZE.get() > 0) {
            // particles of clears long past are not worth spawning any more
            Clear c;
            while ((c = PENDING.peek()) != null && c.at + CLEAR_LINGER < worldTime) {
                if (PENDING.remove(c)) PENDING_SIZE.decrementAndGet();
            }
        }
    }

    /** Forgets everything (the client left its world). */
    public static void clear() {
        SITES.clear();
        PLANS.clear();
        PENDING.clear();
        PENDING_SIZE.set(0);
    }

    /** Whether nothing is stored. */
    public static boolean isEmpty() {
        return SITES.isEmpty() && PLANS.isEmpty() && PENDING_SIZE.get() == 0;
    }

    private static void hook() {
        if (HOOKED.compareAndSet(false, true)) Hooks.register();
    }

    /** The client tick that prunes the store and the world unload that empties it. */
    @SideOnly(Side.CLIENT)
    public static final class Hooks {

        static void register() {
            Hooks h = new Hooks();
            FMLCommonHandler.instance()
                .bus()
                .register(h);
            MinecraftForge.EVENT_BUS.register(h);
        }

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            World w = Minecraft.getMinecraft().theWorld;
            if (w == null) {
                if (!isEmpty()) clear();
                return;
            }
            prune(w.getTotalWorldTime());
        }

        @SubscribeEvent
        public void onUnload(WorldEvent.Unload e) {
            if (e.world.isRemote) clear();
        }
    }
}
