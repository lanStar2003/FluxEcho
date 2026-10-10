package com.fluxecho.campus.client;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.campus.Campus;
import com.fluxecho.campus.PartBlocks;
import com.fluxecho.campus.Terrain;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.BuildState;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.GhostCells;
import com.fluxecho.nexus.ClientTiles;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.nexus.TileNexus;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Works out in the client tick what the projection of each nexus's build job shows, so that {@link BuildRender} only
 * streams ready-made arrays (it runs several times a frame and must not read the world or sort anything).
 * <p>
 * Per nexus with a job it keeps the job's plan ({@link BuildClient#plan}), and while the viewer is within
 * {@code buildGhostRange} + {@value #SCAN_MARGIN} of it in {@code full} mode and the job shows a projection, a
 * done-scan
 * of the client's own world: every drawable cell is checked against the rule the server's builder uses to count a
 * cell as placed ({@link #placed}), nearest the viewer first, {@value #FIRST_PASS_PER_TICK} cells a tick until each
 * has been checked once and then 1,024 cells per 10 ticks. Cells not checked yet are not shown (so a player arriving at
 * a half-built campus does not see ghosts over what stands). Cells in chunks the client has not loaded count as not
 * built while the chunk is missing, and are checked again, up to {@value #PENDING_PER_TICK} a tick, until it arrives,
 * so the built ones vanish within a tick of their chunk. The cells of launches that just landed are checked every
 * tick, so their ghosts vanish within a tick or two. From the result it selects the ghost ({@link GhostCells}: the
 * nearest unbuilt cells up to {@code buildGhostCells}, their exterior faces against the client world and the creases
 * between them, the lowest layer of the current stage) and precomputes every face's corners and texture coordinates
 * and every crease's ends, at most once every {@value #REBUILD_TICKS} ticks; cells landing in between are only marked
 * {@link Ghost#gone}. Further out only the plan's bounds are kept, for the outlines.
 * <p>
 * The result is published per nexus as an immutable {@link Ghost}, read with {@link #ghost}. Everything is forgotten
 * when the client leaves its world, and a nexus is forgotten when it unloads or its job ends.
 */
@SideOnly(Side.CLIENT)
public final class BuildScan {

    /** Cells checked per tick per nexus once the first pass is through: 1,024 per 10 ticks. */
    public static final int SCAN_PER_TICK = 103;
    /** Cells checked per tick in the first pass after a plan appears or the viewer comes near. */
    public static final int FIRST_PASS_PER_TICK = 1024;
    /** Cells found in a chunk the client did not have, checked again per tick until their chunk arrives. */
    public static final int PENDING_PER_TICK = 1024;
    /** The most creases kept per ghost (nearest cells first). */
    public static final int MAX_CREASES = 16384;
    /** The fewest ticks between two selections of a nexus's ghost. */
    public static final int REBUILD_TICKS = 10;
    /** Ticks after which the selection is made again anyway (the world round the ghost may have changed). */
    public static final int REFRESH_TICKS = 100;
    /** How far (blocks) the viewer moves before the ghost is selected again round where it stands now. */
    public static final double MOVE = 8;
    /** How much further than {@code buildGhostRange} the done-scan runs, so the ghost is ready when it is drawn. */
    public static final int SCAN_MARGIN = 64;
    /** How far from its plan's box a job's outlines are drawn (blocks). */
    public static final int OUTLINE_RANGE = 512;
    /** The most cells of the lowest layer kept. */
    public static final int LAYER_CELLS = 512;
    /** How far a ghost's box grows past its cell, so its faces lie just in front of a block in the same cell. */
    public static final float GROW = 0.002f;
    /** How far from a nexus's centre its plan is tracked at all: the outline range and the widest plan. */
    private static final int TRACK_RANGE = OUTLINE_RANGE + 128;
    /** Ticks after its landing a launch's cell is checked each tick (its block arrives a little after). */
    private static final int LANDED_CHECK = BuildClient.LAND_LINGER;
    /** Part codes have six bits; an icon is kept for each part and side. */
    private static final int PARTS = 64;

    private static final int[] NO_INTS = new int[0];
    private static final float[] NO_FLOATS = new float[0];
    private static final BitSet NO_BITS = new BitSet(0);

    /**
     * What the projection of one nexus's job shows, as of one selection: immutable once published (do not change the
     * arrays or bit sets). Positions of faces are relative to the nexus centre {@link #ox}, {@link #oy}, {@link #oz}
     * (the camera-relative origin is {@code ox - RenderManager.renderPosX} and so on); bounds, edges and the plan's
     * steps are in world coordinates.
     */
    public static final class Ghost {

        /** The plan the ghost was made from and its plan key ({@link Campus.View#planKey}). */
        public final BuildPlan.Plan plan;
        public final String planKey;
        /** Grows with every snapshot published for the nexus. */
        public final int rev;
        /** The nexus centre {x, Y0, z} the face positions are relative to. */
        public final int ox, oy, oz;
        /**
         * Whether the done-scan runs and the arrays below hold a ghost; false when only the bounds and the edge are
         * kept (out of range, {@code outline} mode, or a state without a projection).
         */
        public final boolean live;
        /** Whether every drawable cell has been checked at least once since the scan began. */
        public final boolean scanned;
        /** Where the viewer stood when the cells were selected. */
        public final double vx, vy, vz;
        /** The selected cells, step indices into {@code plan.steps}, nearest the viewer first. */
        public final int[] cells;
        /**
         * The exterior faces, each packed as position in {@link #cells} {@code << 3 | side} (unpack with
         * {@link GhostCells#cell} and {@link GhostCells#side}).
         */
        public final int[] faces;
        /**
         * Twelve floats per face: four corners x, y, z relative to the origin, grown by {@link #GROW}. A side face
         * (sides 2 to 5) goes bottom-left, bottom-right, top-right, top-left as seen from outside, so corners 2 and 3
         * are its top; a top or bottom face goes round the face.
         */
        public final float[] xyz;
        /** Eight floats per face: u, v of each corner on the block atlas (the part's own icon for that side). */
        public final float[] uv;
        /**
         * Six floats per crease ({@link GhostCells#creases}: a rim, edge or corner of the ghost's surface): its two
         * ends x, y, z relative to the origin, the lower first.
         */
        public final float[] creases;
        /** The position in {@link #cells} of each crease's cell. */
        public final int[] creaseCells;
        /** Positions in {@link #cells} on the lowest layer. */
        public final BitSet inLayer;
        /** Step indices found built since the selection was made: their faces are skipped until the next one. */
        public final BitSet gone;
        /** The stage of the lowest layer and its y ({@link Integer#MIN_VALUE} when there is none). */
        public final int layerStage, layerY;
        /** The lowest layer's cells, step indices nearest the viewer first, at most {@link #LAYER_CELLS}. */
        public final int[] layer;
        /** The box {minX, minY, minZ, maxX, maxY, maxZ} of the plan's drawable cells, or null when it has none. */
        public final int[] bounds;
        /** The same box for each stage, null for a stage without drawable cells. */
        public final int[][] stageBounds;
        /**
         * The outline of the ground the plan clears (its clear columns' footprint): segments {x0, z0, x1, z1} between
         * block corners; empty when the plan clears nothing.
         */
        public final int[] edge;
        /**
         * Where the completion ring spreads from (world x, z) and how far: the nexus centre and the promenade's outer
         * edge for the establish and forum plans (their box reaches out along the gate), the middle of the box and
         * half its longer side for a module's.
         */
        public final double ringX, ringZ, ringR;

        Ghost(Track t, int rev, boolean live, boolean scanned, double vx, double vy, double vz, int[] cells,
            int[] faces, float[] xyz, float[] uv, float[] creases, int[] creaseCells, BitSet inLayer, BitSet gone,
            int layerStage, int layerY, int[] layer) {
            this.plan = t.plan;
            this.planKey = t.planKey;
            this.rev = rev;
            this.ox = t.ox;
            this.oy = t.oy;
            this.oz = t.oz;
            this.live = live;
            this.scanned = scanned;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.cells = cells;
            this.faces = faces;
            this.xyz = xyz;
            this.uv = uv;
            this.creases = creases;
            this.creaseCells = creaseCells;
            this.inLayer = inLayer;
            this.gone = gone;
            this.layerStage = layerStage;
            this.layerY = layerY;
            this.layer = layer;
            this.bounds = t.bounds.all;
            this.stageBounds = t.bounds.stages;
            this.edge = t.edge;
            this.ringX = t.ringX;
            this.ringZ = t.ringZ;
            this.ringR = t.ringR;
        }

        /** The same ghost with more cells gone. */
        Ghost withGone(Track t, int rev, BitSet gone) {
            return new Ghost(
                t,
                rev,
                live,
                scanned,
                vx,
                vy,
                vz,
                cells,
                faces,
                xyz,
                uv,
                creases,
                creaseCells,
                inLayer,
                gone,
                layerStage,
                layerY,
                layer);
        }

        /** Whether the lowest layer exists. */
        public boolean hasLayer() {
            return layerY != Integer.MIN_VALUE && layer.length > 0;
        }
    }

    /** What the tick keeps for one nexus (client thread only, but {@link #ghost} is read from the renderer). */
    private static final class Track {

        final BuildClient.Key key;
        final BuildPlan.Plan plan;
        final String planKey;
        final GhostCells.Cells cells;
        final GhostCells.Index index;
        final GhostCells.Bounds bounds;
        final int[] edge;
        final int ox, oy, oz;
        final double ringX, ringZ, ringR;

        // the done-scan; order is null while it does not run
        int[] order;
        int cursor;
        boolean firstPass;
        /** Cells not shown: built, or not checked yet. */
        BitSet hidden;
        /** Cells last checked while their chunk was missing (so shown); checked again until it arrives. */
        BitSet pending = new BitSet();
        int pendingAt;
        /** Shown cells found built since the last selection. */
        BitSet gone = new BitSet();
        boolean dirty, goneChanged, selected;
        long lastRebuild;
        double svx, svy, svz;
        int lastStage = Integer.MIN_VALUE;
        int rev;
        volatile Ghost ghost;

        Track(BuildClient.Key key, BuildPlan.Plan plan, String planKey, int[] centre) {
            this.key = key;
            this.plan = plan;
            this.planKey = planKey;
            this.cells = GhostCells.Cells.of(plan.steps);
            this.index = new GhostCells.Index(cells);
            this.bounds = GhostCells.bounds(cells, plan.stages);
            List<BuildPlan.Column> clear = plan.clear;
            int[] xs = new int[clear.size()], zs = new int[clear.size()];
            for (int i = 0; i < xs.length; i++) {
                xs[i] = clear.get(i).x;
                zs[i] = clear.get(i).z;
            }
            this.edge = GhostCells.edge(xs, zs);
            this.ox = centre[0];
            this.oy = centre[1];
            this.oz = centre[2];
            int[] b = bounds.all;
            if (BuildPlan.ESTABLISH.equals(plan.key) || BuildPlan.FORUM.equals(plan.key) || b == null) {
                // these pave the forum and the promenade round the nexus; the gate strip pulls their box forward
                ringX = ox + 0.5;
                ringZ = oz + 0.5;
                ringR = CampusPlan.RING_R + 0.5;
            } else {
                ringX = (b[0] + b[3] + 1) / 2.0;
                ringZ = (b[2] + b[5] + 1) / 2.0;
                ringR = Math.max(b[3] + 1 - b[0], b[5] + 1 - b[2]) / 2.0;
            }
        }
    }

    private static final Map<BuildClient.Key, Track> TRACKS = new ConcurrentHashMap<>();
    private static boolean failed;

    private BuildScan() {}

    /** Hooks the client tick and the world unload; called once by {@code CampusClient.register()}. */
    public static void register() {
        BuildScan h = new BuildScan();
        FMLCommonHandler.instance()
            .bus()
            .register(h);
        MinecraftForge.EVENT_BUS.register(h);
    }

    // ---- reading

    /** The projection of the nexus's job as last worked out, or null when there is none (yet). */
    public static Ghost ghost(TileNexus n) {
        World w = n == null ? null : n.getWorldObj();
        if (w == null) return null;
        Track t = TRACKS.get(new BuildClient.Key(w.provider.dimensionId, n.xCoord, n.yCoord, n.zCoord));
        return t == null ? null : t.ghost;
    }

    /**
     * The progress numbers of the nexus's job as {@link BuildClient} last had them, when they are of the job the
     * nexus's description names; null otherwise (then the description's own numbers count).
     */
    private static BuildClient.State numbers(TileNexus n) {
        World w = n.getWorldObj();
        Campus.View v = n.clientCampus;
        if (w == null || v == null) return null;
        BuildClient.State s = BuildClient.state(w.provider.dimensionId, n.xCoord, n.yCoord, n.zCoord);
        return s != null && s.job.equals(v.job) ? s : null;
    }

    /** The state of the nexus's job: the latest progress numbers', else its description's. */
    public static BuildState.State state(TileNexus n) {
        BuildClient.State s = numbers(n);
        if (s != null) return s.stateEnum();
        Campus.View v = n.clientCampus;
        return v == null ? BuildState.State.SURVEY : v.stateEnum();
    }

    /** Why the nexus's job is paused ({@link BuildState.Pause#NONE} when it is not). */
    public static BuildState.Pause pause(TileNexus n) {
        BuildClient.State s = numbers(n);
        if (s != null) return s.pauseEnum();
        Campus.View v = n.clientCampus;
        return v == null ? BuildState.Pause.NONE : v.pauseEnum();
    }

    /** The stage the nexus's job is at. */
    public static int stage(TileNexus n) {
        BuildClient.State s = numbers(n);
        if (s != null) return s.stage;
        Campus.View v = n.clientCampus;
        return v == null ? 0 : v.stage;
    }

    /** Whether a job in this state shows its projection: waiting for 开始, building, or paused. */
    public static boolean showsGhost(BuildState.State s) {
        return s == BuildState.State.PROJECTING || s == BuildState.State.WAITING
            || s == BuildState.State.BUILDING
            || s == BuildState.State.PAUSED;
    }

    /** The distance from a point to a box of cells {minX, minY, minZ, maxX, maxY, maxZ} (0 inside it). */
    public static double boxDistance(int[] box, double x, double y, double z) {
        double dx = Math.max(Math.max(box[0] - x, 0), x - (box[3] + 1)),
            dy = Math.max(Math.max(box[1] - y, 0), y - (box[4] + 1)),
            dz = Math.max(Math.max(box[2] - z, 0), z - (box[5] + 1));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Whether the client world holds what the server's builder counts as placed for a step: for a cell that must
     * become air, air; otherwise the part's own block and meta ({@code Terrain.isTarget}, the rule of the builder's
     * {@code ALREADY} verdict: grass and the library core in any meta, dirt and our parts in theirs). A cell outside
     * the height limits or in a chunk the client has not loaded is not.
     */
    public static boolean placed(World w, int x, int y, int z, int part, byte kind) {
        return loaded(w, x, z) && placedLoaded(w, x, y, z, part, kind);
    }

    /** {@link #placed} for a cell whose chunk the client has. */
    private static boolean placedLoaded(World w, int x, int y, int z, int part, byte kind) {
        if (y < 0 || y > 255) return false;
        Block b = w.getBlock(x, y, z);
        boolean air = b.getMaterial() == Material.air || b.isAir(w, x, y, z);
        if (kind == BuildPlan.AIR) return air;
        return Terrain.isTarget(b, w.getBlockMetadata(x, y, z), part, air);
    }

    /**
     * Whether the client has the chunk of the cell. The client's chunk provider says every chunk exists and hands
     * out an empty one for those it does not have, so the empty one is what tells.
     */
    static boolean loaded(World w, int x, int z) {
        IChunkProvider p = w.getChunkProvider();
        return p.chunkExists(x >> 4, z >> 4) && !p.provideChunk(x >> 4, z >> 4)
            .isEmpty();
    }

    /** Whether the client world's block at a cell is opaque; a chunk it does not have reads as air. */
    private static boolean opaque(World w, int x, int y, int z) {
        if (y < 0 || y > 255) return false;
        return w.getBlock(x, y, z)
            .isOpaqueCube();
    }

    // ---- the tick

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        World w = mc.theWorld;
        if (w == null) {
            if (!TRACKS.isEmpty()) clear();
            return;
        }
        if (failed) return;
        try {
            tick(mc, w);
        } catch (RuntimeException ex) {
            failed = true;
            clear();
            FluxEcho.LOG.warn("The build projection's scan failed; projections are off until the game restarts", ex);
        }
    }

    @SubscribeEvent
    public void onUnload(WorldEvent.Unload e) {
        if (e.world.isRemote) clear();
    }

    /** Forgets every nexus. */
    public static void clear() {
        TRACKS.clear();
    }

    private static void tick(Minecraft mc, World w) {
        Entity viewer = mc.renderViewEntity != null ? mc.renderViewEntity : mc.thePlayer;
        if (viewer == null || "off".equals(Config.buildProjection)) {
            if (!TRACKS.isEmpty()) clear();
            return;
        }
        long now = w.getTotalWorldTime();
        double vx = viewer.posX, vy = viewer.posY, vz = viewer.posZ;
        boolean full = "full".equals(Config.buildProjection);
        int dim = w.provider.dimensionId;
        Set<BuildClient.Key> seen = new HashSet<>();
        for (TileMultiblock m : ClientTiles.all()) {
            if (!(m instanceof TileNexus n) || m.getWorldObj() != w || m.isInvalid()) continue;
            Campus.View v = n.clientCampus;
            if (v == null || !v.active || !v.hasJob()) continue;
            int[] c = n.centre();
            double hx = c[0] + 0.5 - vx, hz = c[2] + 0.5 - vz;
            if (hx * hx + hz * hz > (double) TRACK_RANGE * TRACK_RANGE) continue;
            BuildPlan.Plan plan = BuildClient.plan(n);
            if (plan == null) continue;
            BuildClient.Key key = new BuildClient.Key(dim, n.xCoord, n.yCoord, n.zCoord);
            Track t = TRACKS.get(key);
            if (t == null || t.plan != plan) {
                t = new Track(key, plan, v.planKey, c);
                TRACKS.put(key, t);
            }
            seen.add(key);
            boolean live = full && showsGhost(state(n))
                && t.bounds.all != null
                && boxDistance(t.bounds.all, vx, vy, vz) <= Config.buildGhostRange + SCAN_MARGIN;
            if (live) scan(t, w, now, vx, vy, vz, stage(n));
            else rest(t);
        }
        TRACKS.keySet()
            .retainAll(seen);
    }

    /** Stops the done-scan of a nexus (it starts over when it runs again) and keeps only its bounds. */
    private static void rest(Track t) {
        Ghost g = t.ghost;
        if (g != null && !g.live) return;
        t.order = null;
        t.hidden = null;
        t.pending = new BitSet();
        t.pendingAt = 0;
        t.gone = new BitSet();
        t.goneChanged = false;
        t.selected = false;
        t.ghost = new Ghost(
            t,
            ++t.rev,
            false,
            false,
            0,
            0,
            0,
            NO_INTS,
            NO_INTS,
            NO_FLOATS,
            NO_FLOATS,
            NO_FLOATS,
            NO_INTS,
            NO_BITS,
            NO_BITS,
            0,
            Integer.MIN_VALUE,
            NO_INTS);
    }

    private static void scan(Track t, World w, long now, double vx, double vy, double vz, int stage) {
        int n = t.cells.size();
        if (t.order == null) {
            // a new scan: nearest the viewer first, nothing shown before it is checked
            t.order = GhostCells.order(t.cells, vx, vy, vz);
            t.cursor = 0;
            t.firstPass = true;
            t.hidden = new BitSet(n);
            t.hidden.set(0, n);
            t.pending = new BitSet();
            t.pendingAt = 0;
            t.gone = new BitSet();
            t.goneChanged = false;
            t.selected = false;
            t.dirty = true;
            t.lastRebuild = Long.MIN_VALUE / 2;
        }
        if (now < t.lastRebuild) t.lastRebuild = Long.MIN_VALUE / 2;
        // cells whose chunk was missing when they were checked: it may have arrived since
        boolean changed = recheck(t, w);
        // the cells of launches that landed lately: their blocks arrive a tick or two after the landing
        for (BuildClient.Launch l : BuildClient.launches(t.key.dim, t.key.x, t.key.y, t.key.z)) {
            if (l.land > now || l.land < now - LANDED_CHECK) continue;
            int i = t.index.find(l.x, l.y, l.z);
            if (i >= 0 && t.cells.drawable(i)) changed |= check(t, w, i);
        }
        int budget = Math.min(t.firstPass ? FIRST_PASS_PER_TICK : SCAN_PER_TICK, t.order.length);
        for (int k = 0; k < budget; k++) {
            int i = t.order[t.cursor];
            if (++t.cursor >= t.order.length) {
                t.cursor = 0;
                t.firstPass = false;
            }
            changed |= check(t, w, i);
        }
        if (changed) t.dirty = true;
        if (!t.selected || sq(vx - t.svx) + sq(vy - t.svy) + sq(vz - t.svz) >= MOVE * MOVE) t.dirty = true;
        if (stage != t.lastStage) t.dirty = true;
        // the builder clearing terrain uncovers faces that were hidden against it
        if (!BuildClient.clears(t.key.dim, t.key.x, t.key.y, t.key.z)
            .isEmpty()) t.dirty = true;
        if (now - t.lastRebuild >= REFRESH_TICKS) t.dirty = true;
        if (t.dirty && now - t.lastRebuild >= REBUILD_TICKS) rebuild(t, w, now, vx, vy, vz, stage);
        else if (t.goneChanged) {
            t.goneChanged = false;
            Ghost g = t.ghost;
            if (g != null && g.live) t.ghost = g.withGone(t, ++t.rev, (BitSet) t.gone.clone());
        }
    }

    /**
     * Checks again, up to {@value #PENDING_PER_TICK} of them, the cells last checked while their chunk was missing,
     * those whose chunk has arrived; true when any of them changed whether it shows.
     */
    private static boolean recheck(Track t, World w) {
        BitSet p = t.pending;
        if (p.isEmpty()) return false;
        GhostCells.Cells c = t.cells;
        boolean changed = false;
        int left = Math.min(PENDING_PER_TICK, p.cardinality());
        long lastChunk = 0;
        boolean known = false, have = false;
        int i = p.nextSetBit(t.pendingAt);
        for (int k = 0; k < left; k++) {
            if (i < 0) i = p.nextSetBit(0);
            if (i < 0) break;
            int x = c.x[i], z = c.z[i];
            long chunk = (long) (x >> 4) << 32 | (z >> 4) & 0xFFFFFFFFL;
            if (!known || chunk != lastChunk) {
                // a chunk's cells mostly come together: one look per chunk
                have = loaded(w, x, z);
                lastChunk = chunk;
                known = true;
            }
            if (have) changed |= check(t, w, i);
            i = p.nextSetBit(i + 1);
        }
        t.pendingAt = Math.max(0, i);
        return changed;
    }

    /**
     * Checks one cell against the world; true when whether it shows changed. A cell whose chunk the client does not
     * have shows (it is not known to be built) and is remembered, to be checked again when the chunk comes.
     */
    private static boolean check(Track t, World w, int i) {
        GhostCells.Cells c = t.cells;
        boolean have = loaded(w, c.x[i], c.z[i]);
        t.pending.set(i, !have);
        boolean done = have && placedLoaded(w, c.x[i], c.y[i], c.z[i], c.part[i], c.kind[i]);
        if (t.hidden.get(i) == done) return false;
        t.hidden.set(i, done);
        if (done) {
            t.gone.set(i);
            t.goneChanged = true;
        }
        return true;
    }

    private static double sq(double d) {
        return d * d;
    }

    /** Selects the ghost round the viewer and precomputes its faces, then publishes it. */
    private static void rebuild(Track t, World w, long now, double vx, double vy, double vz, int stage) {
        GhostCells.Cells c = t.cells;
        int[] sel = GhostCells
            .select(c, t.hidden, vx, vy, vz, Config.buildGhostRange, Math.max(0, Config.buildGhostCells));
        int[] faces = GhostCells.faces(c, sel, (x, y, z) -> opaque(w, x, y, z));
        GhostCells.Layer layer = GhostCells.lowestLayer(c, t.hidden, stage, vx, vy, vz, LAYER_CELLS);
        IIcon[] icons = new IIcon[PARTS * 6];
        boolean[] tried = new boolean[PARTS * 6];
        float[] xyz = new float[faces.length * 12], uv = new float[faces.length * 8];
        int[] kept = new int[faces.length];
        int n = 0;
        for (int f : faces) {
            int i = sel[GhostCells.cell(f)], side = GhostCells.side(f);
            IIcon ic = icon(c.part[i], side, icons, tried);
            if (ic == null) continue;
            corners(xyz, uv, n, c.x[i] - t.ox, c.y[i] - t.oy, c.z[i] - t.oz, side, ic);
            kept[n++] = f;
        }
        // the creases, the lines a drawing of the building would have: from every face, with an icon or not
        int[] creases = GhostCells.creases(c, sel, faces);
        int ne = Math.min(creases.length, MAX_CREASES);
        float[] ends = new float[ne * 6];
        int[] endCells = new int[ne];
        for (int k = 0; k < ne; k++) {
            int f = faces[GhostCells.creaseFace(creases[k])], s = GhostCells.cell(f), i = sel[s];
            GhostCells.creaseEnds(
                c.x[i] - t.ox,
                c.y[i] - t.oy,
                c.z[i] - t.oz,
                GhostCells.side(f),
                GhostCells.creaseDir(creases[k]),
                ends,
                k * 6);
            endCells[k] = s;
        }
        BitSet inLayer = new BitSet(sel.length);
        if (layer.exists()) for (int s = 0; s < sel.length; s++) {
            int i = sel[s];
            if (c.stage[i] == stage && c.y[i] == layer.y) inLayer.set(s);
        }
        t.gone = new BitSet();
        t.goneChanged = false;
        t.dirty = false;
        t.selected = true;
        t.lastRebuild = now;
        t.lastStage = stage;
        t.svx = vx;
        t.svy = vy;
        t.svz = vz;
        t.ghost = new Ghost(
            t,
            ++t.rev,
            true,
            !t.firstPass,
            vx,
            vy,
            vz,
            sel,
            n == faces.length ? kept : Arrays.copyOf(kept, n),
            n == faces.length ? xyz : Arrays.copyOf(xyz, n * 12),
            n == faces.length ? uv : Arrays.copyOf(uv, n * 8),
            ends,
            endCells,
            inLayer,
            NO_BITS,
            stage,
            layer.y,
            layer.cells);
    }

    /**
     * The icon of a part's side: the block's own ({@code getIcon(side, meta)}), its side 0 icon if that fails, or null
     * (the face is left out) for a part without a block or whose icons cannot be had. Kept per selection, as a texture
     * reload makes new icons.
     */
    private static IIcon icon(int part, int side, IIcon[] cache, boolean[] tried) {
        if (part < 0 || part >= PARTS) return null;
        int k = part * 6 + side;
        if (tried[k]) return cache[k];
        tried[k] = true;
        IIcon ic = null;
        Block b = PartBlocks.block(part);
        if (b != null && b != Blocks.air) {
            int meta = PartBlocks.meta(part);
            try {
                ic = b.getIcon(side, meta);
            } catch (RuntimeException e) {
                ic = null;
            }
            if (ic == null) try {
                ic = b.getIcon(0, meta);
            } catch (RuntimeException e) {
                ic = null;
            }
        }
        cache[k] = ic;
        return ic;
    }

    /**
     * Writes face {@code f}'s corners (cell at {@code x, y, z} relative to the origin, grown by {@link #GROW}) and
     * their texture coordinates, mapped the way vanilla maps a block's faces: on the sides u runs left to right as
     * seen from outside and v top to bottom, on top and bottom u runs along x and v along z.
     */
    private static void corners(float[] xyz, float[] uv, int f, int x, int y, int z, int side, IIcon ic) {
        float x0 = x - GROW, x1 = x + 1 + GROW, y0 = y - GROW, y1 = y + 1 + GROW, z0 = z - GROW, z1 = z + 1 + GROW;
        float u0 = ic.getMinU(), u1 = ic.getMaxU(), v0 = ic.getMinV(), v1 = ic.getMaxV();
        int p = f * 12, q = f * 8;
        switch (side) {
            case 0:
                corner(xyz, p, x0, y0, z1);
                corner(xyz, p + 3, x0, y0, z0);
                corner(xyz, p + 6, x1, y0, z0);
                corner(xyz, p + 9, x1, y0, z1);
                uv4(uv, q, u0, v1, u0, v0, u1, v0, u1, v1);
                return;
            case 1:
                corner(xyz, p, x0, y1, z0);
                corner(xyz, p + 3, x0, y1, z1);
                corner(xyz, p + 6, x1, y1, z1);
                corner(xyz, p + 9, x1, y1, z0);
                uv4(uv, q, u0, v0, u0, v1, u1, v1, u1, v0);
                return;
            case 2:
                // north, seen from the north: its left is the east
                upright(xyz, p, x1, z0, x0, z0, y0, y1);
                break;
            case 3:
                // south: its left is the west
                upright(xyz, p, x0, z1, x1, z1, y0, y1);
                break;
            case 4:
                // west: its left is the north
                upright(xyz, p, x0, z0, x0, z1, y0, y1);
                break;
            default:
                // east: its left is the south
                upright(xyz, p, x1, z1, x1, z0, y0, y1);
        }
        uv4(uv, q, u0, v1, u1, v1, u1, v0, u0, v0);
    }

    /** An upright face from its left edge {@code (xl, zl)} to its right edge {@code (xr, zr)}, bottom first. */
    private static void upright(float[] xyz, int p, float xl, float zl, float xr, float zr, float y0, float y1) {
        corner(xyz, p, xl, y0, zl);
        corner(xyz, p + 3, xr, y0, zr);
        corner(xyz, p + 6, xr, y1, zr);
        corner(xyz, p + 9, xl, y1, zl);
    }

    private static void corner(float[] xyz, int p, float x, float y, float z) {
        xyz[p] = x;
        xyz[p + 1] = y;
        xyz[p + 2] = z;
    }

    private static void uv4(float[] uv, int q, float a0, float b0, float a1, float b1, float a2, float b2, float a3,
        float b3) {
        uv[q] = a0;
        uv[q + 1] = b0;
        uv[q + 2] = a1;
        uv[q + 3] = b1;
        uv[q + 4] = a2;
        uv[q + 5] = b2;
        uv[q + 6] = a3;
        uv[q + 7] = b3;
    }
}
