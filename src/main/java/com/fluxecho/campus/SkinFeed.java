package com.fluxecho.campus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import com.fluxecho.FluxEcho;
import com.fluxecho.city.TileCityModule;
import com.fluxecho.library.TileLibrary;
import com.fluxecho.logic.ArchiveShape;
import com.fluxecho.logic.Blueprint;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.BuildState;
import com.fluxecho.logic.LibraryShape;
import com.fluxecho.logic.NexusShape;
import com.fluxecho.logic.skin.LampRule;
import com.fluxecho.logic.skin.SkinCodec;
import com.fluxecho.logic.skin.SkinEntry;
import com.fluxecho.logic.skin.SkinKeys;
import com.fluxecho.logic.skin.SkinPalette;
import com.fluxecho.logic.skin.SkinProfiles;
import com.fluxecho.logic.skin.SkinTable;
import com.fluxecho.logic.skin.SkinTrack;
import com.fluxecho.logic.skin.SkinViewers;
import com.fluxecho.nexus.NexusNet;
import com.fluxecho.nexus.TileModule;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.nexus.TileNexus;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * The server side of the box table (style §3.9.3): a campus's feed keeps one record a building ({@link SkinTrack}: the
 * nexus, every module on a site, a module being built for the first time) from what the campus knows and what the
 * buildings' tiles say, saves the records with the campus ({@code Skn}, so a building whose chunk is not loaded still
 * has its entry), and tells the players: the whole table of the campuses within 512 blocks when they log in, change
 * dimension, or come within 512 blocks of a campus ({@code SKIN_TABLE}, S→C 7), and every changed entry to the players
 * that have its campus ({@code SKIN_DELTA}, S→C 8). The client's own structure check decides only what dissolves, never
 * how a building is drawn.
 * <p>
 * 0.17.0: every building is an old one ({@link SkinProfiles#LEGACY}); the table drives their lamp windows. The feed
 * never changes a block or the campus's behaviour: a failure is logged and stops the feed, never the campus.
 */
public final class SkinFeed {

    /** Ticks between two looks at the buildings. */
    static final int EVERY = 20;
    /** Ticks between two checks of who is near which campus. */
    static final int CHECK = 100;
    /** The NBT key of the records in the campus's tag (not {@code Sk}: that is 0.16's skip-blocked flag). */
    static final String KEY = "Skn";
    private static final int SKN_VERSION = 1;

    private final Campus campus;
    private final Map<Long, SkinTrack> tracks = new LinkedHashMap<>();
    /** The entries as the players last heard them, by building. */
    private Map<Long, SkinEntry> told = new HashMap<>();
    private long id = Long.MIN_VALUE;
    private int dim;
    private boolean failed;

    SkinFeed(Campus campus) {
        this.campus = campus;
    }

    /** The campus's id: its nexus's centre, packed. */
    public long id() {
        return id;
    }

    /** The entries now, nexus first. */
    public List<SkinEntry> entries() {
        List<SkinEntry> out = new ArrayList<>(tracks.size());
        for (SkinTrack t : tracks.values()) out.add(t.entry());
        return out;
    }

    // ---- the campus's buildings

    /** Called every tick by the campus; looks at the buildings once a second. */
    void tick(long now) {
        if (failed || now % EVERY != 0) return;
        try {
            evaluate(now);
        } catch (RuntimeException e) {
            failed = true;
            FluxEcho.LOG.error("The box table of the campus at {} failed and stops until it is loaded again", id, e);
        }
    }

    /** Looks at every building and tells the players what changed. */
    void evaluate(long now) {
        World w = campus.world();
        TileNexus n = campus.nexus();
        if (w == null || w.isRemote || w.provider == null) return;
        int[] c = n.centre();
        long was = id;
        int wasDim = dim;
        id = SkinEntry.pack(c[0], c[1], c[2]);
        dim = w.provider.dimensionId;
        if (was != Long.MIN_VALUE && (was != id || wasDim != dim)) unlist(wasDim, was);
        // records made for another centre (the nexus moved whole, here or while it was saved) follow it
        for (Map.Entry<Long, SkinTrack> e : tracks.entrySet()) {
            SkinTrack t = e.getValue();
            if (t.entry().campus == id) continue;
            e.setValue(
                SkinTrack.saved(
                    t.entry()
                        .toBuilder()
                        .campus(id)
                        .build(),
                    t.apartSince()));
            told.remove(e.getKey());
        }
        Set<Long> seen = new HashSet<>();
        observe(n, "nexus", now, repairQueued(BuildJob.REPAIR + BuildPlan.ESTABLISH), seen);
        for (int site : campus.recordedSites()) {
            int[] p = campus.siteController(site);
            long bid = SkinEntry.pack(p[0], p[1], p[2]);
            if (!w.blockExists(p[0], p[1], p[2])) {
                // not loaded: the record stays as it was last seen (or as Skn had it)
                if (tracks.containsKey(bid)) seen.add(bid);
                continue;
            }
            TileEntity te = w.getTileEntity(p[0], p[1], p[2]);
            if (te instanceof TileModule m && SkinKeys.row(m.moduleKey()) >= 0)
                observe(m, m.moduleKey(), now, repairQueued(BuildJob.REPAIR + site), seen);
        }
        building(now, seen);
        tracks.keySet()
            .retainAll(seen);
        publish();
        list(dim, id, this);
    }

    /** One loaded structure tile: its look as it stands, then its state, power and repair. */
    private void observe(TileMultiblock t, String key, long now, boolean repair, Set<Long> seen) {
        long bid = SkinEntry.pack(t.xCoord, t.yCoord, t.zCoord);
        boolean formed = t.formed();
        SkinTrack track = tracks.get(bid);
        if (track != null && !key.equals(
            track.entry()
                .key()))
            track = null;
        if (track == null && !formed && t instanceof TileNexus && !establishing()) return;
        Look l = look(t, key);
        boolean awake = LampRule.isAwake(formed, docked(t), powered(t));
        if (track == null) {
            track = !formed && t instanceof TileNexus ? SkinTrack.building(id, key, l.shape, l.facing, l.ctrl, l.box)
                : SkinTrack.seen(id, key, l.shape, l.facing, l.ctrl, l.box, formed, awake, now);
            tracks.put(bid, track);
        } else track.observe(now, formed, awake, repair);
        track.look(l.shape, l.facing, l.box, l.lamp, l.accent);
        seen.add(bid);
    }

    /** A module job building a site for the first time: its record says so, its box is its plan's. */
    private void building(long now, Set<Long> seen) {
        BuildJob j = campus.job();
        if (j == null || !j.started() || BuildState.ended(j.state())) return;
        ModuleSpec spec = ModuleSpecs.ofJob(j.key());
        int site = j.site();
        if (spec == null || site < 0 || SkinKeys.row(spec.key) < 0) return;
        int[] c = campus.centre(), f = campus.front();
        int[] ctrl = spec.controller(site, c[0], c[1], c[2], f[0], f[1]);
        if (ctrl == null) return;
        long bid = SkinEntry.pack(ctrl[0], ctrl[1], ctrl[2]);
        if (seen.contains(bid)) return;
        SkinTrack track = tracks.get(bid);
        if (track == null) {
            BuildPlan.Plan plan = j.plan(campus);
            if (plan == null || plan.steps.isEmpty()) return;
            int[] box = { ctrl[0], ctrl[1], ctrl[2], ctrl[0], ctrl[1], ctrl[2] };
            for (BuildPlan.Step s : plan.steps) {
                box[0] = Math.min(box[0], s.x);
                box[1] = Math.min(box[1], s.y);
                box[2] = Math.min(box[2], s.z);
                box[3] = Math.max(box[3], s.x);
                box[4] = Math.max(box[4], s.y);
                box[5] = Math.max(box[5], s.z);
            }
            String shape = spec.shapeFor(c[1]);
            track = SkinTrack.building(
                id,
                spec.key,
                shape == null ? TileMultiblock.MAIN : shape,
                ctrl.length > 3 ? ctrl[3] : 2,
                new int[] { ctrl[0], ctrl[1], ctrl[2] },
                box);
            tracks.put(bid, track);
        } else track.observe(now, false, false, false);
        seen.add(bid);
    }

    /** Whether the nexus's establish job runs (its record is {@code BUILDING} until the nexus first forms). */
    private boolean establishing() {
        BuildJob j = campus.job();
        return j != null && j.started()
            && !BuildState.ended(j.state())
            && j.key()
                .startsWith(BuildPlan.ESTABLISH);
    }

    /** Whether a job of the key is queued or runs. */
    private boolean repairQueued(String key) {
        BuildJob j = campus.job();
        if (j != null && j.key()
            .equals(key) && !BuildState.ended(j.state())) return true;
        return campus.queue()
            .contains(key);
    }

    /** What the players are told of a change: a delta each, to the players that have the campus. */
    private void publish() {
        Map<Long, SkinEntry> now = new HashMap<>();
        List<SkinCodec.Delta> deltas = new ArrayList<>();
        for (Map.Entry<Long, SkinTrack> e : tracks.entrySet()) {
            SkinEntry x = e.getValue()
                .entry();
            now.put(e.getKey(), x);
            if (!x.equals(told.get(e.getKey()))) deltas.add(SkinCodec.Delta.put(dim, x));
        }
        for (Long gone : told.keySet()) if (!now.containsKey(gone)) deltas.add(SkinCodec.Delta.remove(dim, id, gone));
        told = now;
        if (deltas.isEmpty()) return;
        campus.nexus()
            .markDirty();
        List<Object> to;
        synchronized (SkinFeed.class) {
            to = VIEWERS.watchers(dim, id);
        }
        if (to.isEmpty()) return;
        for (SkinCodec.Delta d : deltas) {
            NBTTagCompound t = wrap(SkinCodec.write(d));
            for (Object p : to) sender.send(p, NexusNet.SKIN_DELTA, t);
        }
    }

    // ---- a building's look

    /** A building as its tile gives it: shape, facing, controller, box, lamp box, accent. */
    private static final class Look {

        String shape;
        int facing;
        int[] ctrl, box, lamp;
        int accent;
    }

    private static Look look(TileMultiblock t, String key) {
        Look l = new Look();
        l.shape = t.shapeName();
        l.facing = t.front()
            .ordinal();
        l.ctrl = new int[] { t.xCoord, t.yCoord, t.zCoord };
        Blueprint bp = blueprint(t, l.shape);
        int fx = t.front().offsetX, fz = t.front().offsetZ;
        l.box = box(bp, t, fx, fz, 0, 0, 0, bp.width() - 1, bp.height() - 1, bp.depth() - 1);
        int[] m = SkinProfiles.legacyMask(key, l.shape, bp)
            .bounds();
        l.lamp = m == null ? null : box(bp, t, fx, fz, m[0], m[1], m[2], m[3], m[4], m[5]);
        int fallback = t instanceof TileModule mod ? mod.moduleColor() : SkinPalette.NEXUS_CYAN;
        l.accent = SkinPalette.accentOr(key, l.shape, fallback);
        return l;
    }

    /** The blueprint of the shape a structure tile stands (or last stood) as. */
    static Blueprint blueprint(TileMultiblock t, String shape) {
        if (t instanceof TileCityModule cm) return cm.build()
            .blueprint();
        if (t instanceof TileNexus) {
            int p = NexusShape.phaseOf(shape);
            return p > 0 ? NexusShape.phase(p) : NexusShape.PHASE_1;
        }
        if (t instanceof TileLibrary)
            return TileLibrary.HALL.equals(shape) ? LibraryShape.BLUEPRINT : ArchiveShape.BLUEPRINT;
        throw new IllegalArgumentException("no blueprint for " + t);
    }

    /** The world box of a box of blueprint cells {a0..a1, b0..b1, c0..c1} round the tile's controller. */
    private static int[] box(Blueprint bp, TileMultiblock t, int fx, int fz, int a0, int b0, int c0, int a1, int b1,
        int c1) {
        int[] out = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
            Integer.MIN_VALUE };
        for (int k = 0; k < 8; k++) {
            int[] p = bp.world(
                (k & 1) == 0 ? a0 : a1,
                (k & 2) == 0 ? b0 : b1,
                (k & 4) == 0 ? c0 : c1,
                t.xCoord,
                t.yCoord,
                t.zCoord,
                fx,
                fz);
            for (int i = 0; i < 3; i++) {
                out[i] = Math.min(out[i], p[i]);
                out[i + 3] = Math.max(out[i + 3], p[i]);
            }
        }
        return out;
    }

    /** Whether the tile is docked (the nexus always is). */
    private static boolean docked(TileMultiblock t) {
        return !(t instanceof TileModule m) || "docked".equals(m.dockStatus());
    }

    /** Whether the tile paid for its power this tick. */
    private static boolean powered(TileMultiblock t) {
        if (t instanceof TileCityModule cm) return cm.powered();
        if (t instanceof TileLibrary l) return l.powered();
        if (t instanceof TileNexus n) return n.powered();
        return false;
    }

    // ---- saving (Skn)

    /** Writes the records into the campus's tag ({@code Skn}), when there are any. */
    void write(NBTTagCompound campusTag) {
        if (tracks.isEmpty()) return;
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(b)) {
            out.writeByte(SKN_VERSION);
            out.writeShort(tracks.size());
            for (SkinTrack t : tracks.values()) {
                SkinCodec.writeEntry(out, t.entry());
                out.writeLong(t.apartSince());
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        campusTag.setByteArray(KEY, b.toByteArray());
    }

    /** Reads the records back from the campus's tag; a tag without them (a campus of 0.16) starts with none. */
    void read(NBTTagCompound campusTag) {
        tracks.clear();
        told = new HashMap<>();
        failed = false;
        if (campusTag == null || !campusTag.hasKey(KEY)) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(campusTag.getByteArray(KEY)))) {
            int v = in.readUnsignedByte();
            if (v != SKN_VERSION) throw new IOException("Skn version " + v);
            int n = in.readUnsignedShort();
            for (int i = 0; i < n; i++) {
                SkinEntry e = SkinCodec.readEntry(in);
                long apart = in.readLong();
                tracks.put(e.id(), SkinTrack.saved(e, apart));
                told.put(e.id(), e);
            }
        } catch (IOException | RuntimeException e) {
            FluxEcho.LOG.warn("The saved box table of a campus could not be read; it is made anew", e);
            tracks.clear();
            told.clear();
        }
    }

    // ---- the players: every campus's feed, who has which

    /** Where the feed sends: a viewer (a player's UUID in the game, {@link #viewer}), a kind, the payload. */
    public interface Sender {

        void send(Object player, int kind, NBTTagCompound data);
    }

    /** Sends on the nexus channel, to the player online now under the viewer's key ({@link #viewer}). */
    public static final Sender NETWORK = (player, kind, data) -> {
        EntityPlayerMP p = online(player);
        if (p != null) NexusNet.tell(p, kind, data);
    };

    /**
     * The key a player is a viewer under: an {@link EntityPlayer} by their UUID, so the new player object a respawn
     * makes (a death, the way back from the End) is the same viewer as the old one, and the old object is neither kept
     * nor sent to once the player logs out; anything else (the tests' players) as it is.
     */
    static Object viewer(Object player) {
        return player instanceof EntityPlayer p ? p.getUniqueID() : player;
    }

    /** The online player a viewer's key stands for, or null (logged out, or no server). */
    private static EntityPlayerMP online(Object viewer) {
        if (viewer instanceof EntityPlayerMP p) return p;
        if (!(viewer instanceof UUID u)) return null;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return null;
        for (Object o : server.getConfigurationManager().playerEntityList)
            if (o instanceof EntityPlayerMP p && u.equals(p.getUniqueID())) return p;
        return null;
    }

    /** Where the feed sends; tests put their own. */
    static Sender sender = NETWORK;

    /** The campuses' feeds by dimension and campus id. */
    private static final Map<Integer, Map<Long, SkinFeed>> FEEDS = new HashMap<>();
    private static final SkinViewers VIEWERS = new SkinViewers();

    private static synchronized void list(int dim, long campus, SkinFeed f) {
        FEEDS.computeIfAbsent(dim, k -> new LinkedHashMap<>())
            .put(campus, f);
    }

    /** A campus is gone from where it was: the players that have it are told its table is empty now. */
    private static void unlist(int dim, long campus) {
        List<Object> had;
        synchronized (SkinFeed.class) {
            Map<Long, SkinFeed> m = FEEDS.get(dim);
            if (m != null) m.remove(campus);
            had = VIEWERS.watchers(dim, campus);
            VIEWERS.forget(dim, campus);
        }
        NBTTagCompound none = wrap(
            SkinCodec.write(new SkinCodec.Table(dim, false, new long[] { campus }, new ArrayList<SkinEntry>())));
        for (Object p : had) sender.send(p, NexusNet.SKIN_TABLE, none);
    }

    private static synchronized List<SkinViewers.Site> sites() {
        List<SkinViewers.Site> out = new ArrayList<>();
        for (Map.Entry<Integer, Map<Long, SkinFeed>> d : FEEDS.entrySet()) for (Long c : d.getValue()
            .keySet()) out.add(new SkinViewers.Site(c, d.getKey()));
        return out;
    }

    private static synchronized List<SkinEntry> entries(int dim, Collection<Long> campuses) {
        List<SkinEntry> out = new ArrayList<>();
        Map<Long, SkinFeed> m = FEEDS.get(dim);
        if (m != null) for (Long c : campuses) {
            SkinFeed f = m.get(c);
            if (f != null) out.addAll(f.entries());
        }
        return out;
    }

    /** A player logged in: the whole table of their dimension's campuses within 512 blocks. */
    public static void login(Object player, int dim, double x, double z) {
        enter(viewer(player), dim, x, z);
    }

    /** A player changed dimension: the old table goes, the new dimension's comes whole. */
    public static void changedDimension(Object player, int dim, double x, double z) {
        enter(viewer(player), dim, x, z);
    }

    /**
     * A player respawned: a change of dimension when it put them in another one. In the same dimension the client kept
     * its world, and its table with it.
     */
    public static void respawned(Object player, int dim, double x, double z) {
        Object v = viewer(player);
        Integer had;
        synchronized (SkinFeed.class) {
            had = VIEWERS.dim(v);
        }
        if (had == null || had != dim) enter(v, dim, x, z);
    }

    /** The check every 100 ticks: the campuses a player came within 512 blocks of since, each whole. */
    public static void check(Object player, int dim, double x, double z) {
        Object v = viewer(player);
        List<Long> came;
        synchronized (SkinFeed.class) {
            came = VIEWERS.check(v, dim, x, z, sites());
        }
        if (came == null) enter(v, dim, x, z);
        else if (!came.isEmpty()) table(v, dim, false, came);
    }

    public static synchronized void logout(Object player) {
        VIEWERS.leave(viewer(player));
    }

    /** Forgets every feed and player (the server stops; the tests start over). */
    public static synchronized void clear() {
        FEEDS.clear();
        VIEWERS.clear();
    }

    private static void enter(Object player, int dim, double x, double z) {
        List<Long> near;
        synchronized (SkinFeed.class) {
            near = VIEWERS.enter(player, dim, x, z, sites());
        }
        table(player, dim, true, near);
    }

    private static void table(Object player, int dim, boolean reset, List<Long> campuses) {
        long[] ids = new long[campuses.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = campuses.get(i);
        SkinCodec.Table t = new SkinCodec.Table(dim, reset, ids, entries(dim, campuses));
        sender.send(player, NexusNet.SKIN_TABLE, wrap(SkinCodec.write(t)));
    }

    /** The payload of a box table message: its bytes ({@link SkinCodec}) under {@code B}. */
    static NBTTagCompound wrap(byte[] b) {
        NBTTagCompound t = new NBTTagCompound();
        t.setByteArray("B", b);
        return t;
    }

    /** The bytes of a box table message. */
    public static byte[] bytes(NBTTagCompound t) {
        return t == null ? new byte[0] : t.getByteArray("B");
    }

    // ---- the game's events

    private static boolean registered;

    /** Hooks the players' logins, changes of dimension, respawns and logouts, the range check and world unloads. */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        Events e = new Events();
        FMLCommonHandler.instance()
            .bus()
            .register(e);
        MinecraftForge.EVENT_BUS.register(e);
    }

    /** The handlers; each catches its own failure, so the box table never stops a login. */
    public static final class Events {

        @SubscribeEvent
        public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
            run(e.player, p -> login(p, p.dimension, p.posX, p.posZ));
        }

        @SubscribeEvent
        public void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
            run(e.player, p -> changedDimension(p, p.dimension, p.posX, p.posZ));
        }

        @SubscribeEvent
        public void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
            run(e.player, p -> respawned(p, p.dimension, p.posX, p.posZ));
        }

        @SubscribeEvent
        public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
            logout(e.player);
        }

        @SubscribeEvent
        public void onTick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null || server.getTickCounter() % CHECK != 0) return;
            prune();
            for (Object o : server.getConfigurationManager().playerEntityList)
                if (o instanceof EntityPlayerMP p) run(p, q -> check(q, q.dimension, q.posX, q.posZ));
        }

        @SubscribeEvent
        public void onWorldUnload(WorldEvent.Unload e) {
            if (e.world == null || e.world.isRemote || e.world.provider == null) return;
            int d = e.world.provider.dimensionId;
            synchronized (SkinFeed.class) {
                // the overworld unloads when the server stops: everything goes
                if (d == 0) clear();
                Map<Long, SkinFeed> m = FEEDS.remove(d);
                if (m != null) for (Long c : m.keySet()) VIEWERS.forget(d, c);
            }
        }

        private interface Body {

            void run(EntityPlayer p);
        }

        private static void run(EntityPlayer p, Body body) {
            if (!(p instanceof EntityPlayerMP)) return;
            try {
                body.run(p);
            } catch (RuntimeException ex) {
                FluxEcho.LOG.warn("The box table could not be sent to {}", p.getCommandSenderName(), ex);
            }
        }
    }

    /**
     * Drops the feeds of nexuses that are gone: their chunk is loaded and no nexus stands there any more (a nexus in an
     * unloaded chunk keeps its feed, which still answers for its buildings). The players that had such a campus are
     * told its table is empty, so its lamp windows go back to how 0.16 drew them.
     */
    private static void prune() {
        List<long[]> gone = new ArrayList<>();
        synchronized (SkinFeed.class) {
            for (Map.Entry<Integer, Map<Long, SkinFeed>> d : FEEDS.entrySet()) {
                // a dimension not loaded is not loaded for it
                World w = DimensionManager.getWorld(d.getKey());
                if (w == null) continue;
                for (Map.Entry<Long, SkinFeed> e : d.getValue()
                    .entrySet()) {
                    TileNexus n = e.getValue().campus.nexus();
                    if (!w.blockExists(n.xCoord, n.yCoord, n.zCoord)) continue;
                    if (w.getTileEntity(n.xCoord, n.yCoord, n.zCoord) instanceof TileNexus) continue;
                    gone.add(new long[] { d.getKey(), e.getKey() });
                }
            }
        }
        for (long[] g : gone) unlist((int) g[0], g[1]);
    }

    // ---- the NEI preview (§3.9.7)

    /**
     * A structure tile built in a preview world (our info container calls this when the tile's world is no real one):
     * the preview gets one entry for it, formed, powered, by day, over its shape's box ({@link SkinTable#putPreview}).
     */
    public static void preview(TileMultiblock t) {
        try {
            if (t == null || t.getWorldObj() == null) return;
            String key = t instanceof TileModule m ? m.moduleKey() : "nexus";
            if (SkinKeys.row(key) < 0) return;
            Look l = look(t, key);
            SkinTable.putPreview(t.getWorldObj(), l.box, key, l.shape, l.facing, l.ctrl);
        } catch (RuntimeException e) {
            FluxEcho.LOG.debug("No box table entry for a preview of {}", t, e);
        }
    }
}
