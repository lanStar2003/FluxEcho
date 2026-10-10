package com.fluxecho.campus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.core.Owners;
import com.fluxecho.logic.BuildLedger;
import com.fluxecho.logic.BuildPace;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.BuildState;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.FxCodec;
import com.fluxecho.logic.Mix;
import com.fluxecho.logic.PartRecipes;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.ResearchTree;
import com.fluxecho.logic.RingSlots;
import com.fluxecho.nexus.Costs;
import com.fluxecho.nexus.NexusRegistry;
import com.fluxecho.nexus.TileModule;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.nexus.TileNexus;
import com.fluxecho.research.Research;
import com.gtnewhorizons.modularui.api.forge.ItemStackHandler;

/**
 * The campus of one nexus (0.10.0 「营造」): whether the nexus builds at all, its construction jobs (the current one and
 * the queue), the material ledger and the spoils, the hall sites its modules stand on, and the entry points the GUI,
 * the core item and the supply port call. {@link TileNexus} owns one and ticks it first thing every server tick,
 * formed or not, so the establish job can raise the nexus round its own core.
 * <p>
 * A nexus placed by hand the 0.9.2 way, or saved by 0.9.2, is <i>legacy</i>: nothing is built and nothing in the world
 * is touched until a member lays the forum. An <i>active</i> campus runs one job at a time ({@link Builder}); each job
 * is surveyed, projected, and waits for a member's 开始 before it touches anything. Module jobs appear by themselves
 * once the nexus's teams have a module's research and the ring has room for it.
 * <p>
 * Materials come from members (补给, the intake slot) and, for a started job, from automation (the supply port,
 * {@link #offer}); the ledger only takes what the job still needs. Every entry point returns a lang key under
 * {@code fluxecho.build.} saying what happened.
 */
public final class Campus {

    /** Spoils slots. */
    public static final int SPOILS = 27;
    /** Ticks a module whose job was cancelled is not offered again. */
    static final long DECLINE_TICKS = 24000;
    /** Ticks the nexus or a sited module must stand unformed before a repair is offered. */
    static final long REPAIR_AFTER = 200;
    /**
     * Ticks a finished job stays the current one before the next (or none) takes its place, so its DONE state and
     * finish time reach the clients (the completion effect) even when another job waits.
     */
    static final long DONE_LINGER = 100;
    /** Ticks between two sends of the progress numbers when nothing changed (for players who came near since). */
    static final long STATE_HEARTBEAT = 100;
    /**
     * Ticks after a core carrying its campus went back where it stood before the campus looks whether the nexus
     * stands ({@link #resume}): by then the structure has been checked.
     */
    static final long RESUME_CHECK = 40;

    private static final String P = "fluxecho.build.";
    private static final String NO_JOB = P + "no_job", NOT_MEMBER = P + "not_member";

    private final TileNexus nexus;
    private final Builder builder = new Builder(this);
    private final FxBatch fx = new FxBatch();
    private final BuildLedger ledger = new BuildLedger();
    private final ItemStackHandler intake = new ItemStackHandler(1) {

        @Override
        protected void onContentsChanged(int slot) {
            dirty();
        }
    };
    private final ItemStackHandler spoils = new ItemStackHandler(SPOILS) {

        @Override
        protected void onContentsChanged(int slot) {
            dirty();
        }
    };

    private boolean active, hinted, skipBlocked;
    private BuildJob job;
    private final List<BuildJob> queue = new ArrayList<>();
    /** Recorded module controllers by hall site, and the module key standing there ("" when not known). */
    private final Map<Integer, int[]> sites = new TreeMap<>();
    private final Map<Integer, String> siteKinds = new HashMap<>();
    /** Module keys whose job a member cancelled, until when they are not offered again. */
    private final Map<String, Long> declined = new HashMap<>();
    private CampusPlan plan;
    /**
     * A campus resumed by a core put back where it stood ({@link #resume}): the world tick it looks whether the nexus
     * stands (-1: nothing to look at), and the plan key of the campus job that was not finished ("" for none).
     */
    private long resumeCheckAt = -1;
    private String resumeJob = "";

    // ---- live, server
    private boolean loaded, changed, saveDirty, needDirty = true, failed;
    private BuildJob needJob;
    private Map<String, Long> need = Collections.emptyMap();
    private long nexusUnformedSince = -1;
    private final Map<Integer, Long> siteUnformedSince = new HashMap<>();
    private List<int[]> liveKeepOut = new ArrayList<>(), protectedBoxes = new ArrayList<>();
    private long keepOutAt = Long.MIN_VALUE, protectedAt = Long.MIN_VALUE, etaAt = Long.MIN_VALUE;
    private long eta;
    private long lastStateKey = Long.MIN_VALUE, lastStateSent = Long.MIN_VALUE, lastBlockedHash, hintAt;
    /** Whether the members were told that the spoils are full (until they fit again). */
    private boolean spoilsTold;

    public Campus(TileNexus nexus) {
        this.nexus = nexus;
    }

    // ---- state

    /** False for a legacy nexus (0.9.2, or a core placed sneaking): nothing is built or touched. */
    public boolean active() {
        return active;
    }

    /** The current job, or null. */
    public BuildJob job() {
        return job;
    }

    /** The keys of the queued jobs, in order (a copy). */
    public List<String> queue() {
        List<String> out = new ArrayList<>();
        for (BuildJob j : queue) out.add(j.key);
        return out;
    }

    public BuildLedger ledger() {
        return ledger;
    }

    /** The GUI's intake slot (not open to automation); credited every tick. */
    public ItemStackHandler intake() {
        return intake;
    }

    /** What the builder kept of the ores and logs it cleared. */
    public ItemStackHandler spoils() {
        return spoils;
    }

    /** The campus layout, seeded with the nexus controller's position. */
    public CampusPlan plan() {
        if (plan == null) plan = new CampusPlan(Mix.seed(nexus.xCoord, nexus.yCoord, nexus.zCoord));
        return plan;
    }

    /** The recorded controller {x, y, z} of the module on a hall site, or null. */
    public int[] siteController(int site) {
        int[] c = sites.get(site);
        return c == null ? null : c.clone();
    }

    /** Records the controller of the module standing on a hall site (the builder commissioned it, or it docked). */
    public void recordSite(int site, int x, int y, int z) {
        sites.put(site, new int[] { x, y, z });
        String kind = "";
        World w = world();
        if (w != null && w.blockExists(x, y, z) && w.getTileEntity(x, y, z) instanceof TileModule m) {
            kind = m.moduleKey();
        } else if (siteKinds.containsKey(site)) kind = siteKinds.get(site);
        siteKinds.put(site, kind);
        keepOutAt = Long.MIN_VALUE;
        dirty();
    }

    /**
     * The world boxes {x0, z0, x1, z1} of the modules the campus must not touch: those standing near the nexus that
     * are not on a recorded hall site (0.9.2 halls) and the footprints of the modules on recorded sites. The current
     * job's plan was made with its own boxes ({@link BuildJob#keepOut()}).
     */
    public List<int[]> keepOut() {
        refreshKeepOut();
        List<int[]> out = new ArrayList<>();
        for (int[] b : liveKeepOut) out.add(b.clone());
        return out;
    }

    /** Whether blocked cells of the structure are skipped instead of pausing the job (跳过受阻). */
    public boolean skipBlocked() {
        return skipBlocked;
    }

    public void setSkipBlocked(boolean on) {
        if (skipBlocked == on) return;
        skipBlocked = on;
        stateChanged();
    }

    // ---- the server tick

    /**
     * Runs the campus for one tick (server side; {@link TileNexus} calls it first, formed or not). An exception stops
     * the campus until the nexus is loaded again (logged once) instead of taking the server's tick down with it.
     */
    public void tick() {
        if (failed) return;
        try {
            run();
        } catch (RuntimeException e) {
            failed = true;
            Builder.QUIET = false;
            FluxEcho.LOG.error(
                "The campus of the Flux Nexus at {},{},{} failed and stops until the nexus is loaded again",
                nexus.xCoord,
                nexus.yCoord,
                nexus.zCoord,
                e);
        }
    }

    private void run() {
        World w = world();
        if (w == null || w.isRemote) return;
        long now = w.getTotalWorldTime();
        if (!loaded) {
            loaded = true;
            afterLoad();
        }
        if (!active) {
            legacyHint(now);
            flush(now);
            return;
        }
        creditIntake();
        if (resumeCheckAt >= 0 && now >= resumeCheckAt) afterResume();
        if (now % 20 == 0) watchStructures(now);
        if (now % 100 == 0) autoJobs(now);
        if (job == null && !queue.isEmpty()) nextJob();
        if (job != null) {
            builder.tick(job, now);
            if (BuildState.ended(job.state) && job.flights.isEmpty() && !lingers(now)) nextJob();
        }
        flush(now);
    }

    /**
     * Whether a finished job stays the current one a little longer, so the GUI shows it done and the clients see its
     * {@code finishedAt} (the completion effect): for {@link #DONE_LINGER} ticks, also when another job is waiting
     * (the establish job usually has the library's waiting behind it).
     */
    private boolean lingers(long now) {
        if (job.state != BuildState.State.DONE || job.finishedAt < 0) return false;
        return now - job.finishedAt < DONE_LINGER;
    }

    private void flush(long now) {
        if (!fx.isEmpty() && (now % 4 == 0 || fx.full())) {
            CampusNet.sendFx(nexus, fx);
            fx.reset();
        }
        if (now % 10 == 0) {
            long key = stateKey();
            // the numbers go out when they change, and now and then while there is a job, so a player who comes
            // near (or logs in) later gets them too
            if (key != lastStateKey || job != null && now - lastStateSent >= STATE_HEARTBEAT) {
                lastStateKey = key;
                lastStateSent = now;
                CampusNet.sendState(nexus);
            }
        }
        if (job != null && now % 20 == 0 && job.blockedShownHash() != lastBlockedHash) {
            // the red boxes follow the blocked cells (a cell put right, a new one found), at most once a second
            changed = true;
        }
        if (changed) {
            changed = false;
            saveDirty = false;
            nexus.syncCampus();
        } else if (saveDirty) {
            saveDirty = false;
            nexus.markDirty();
        }
    }

    /** A hash of the progress numbers, to send them only when they change. */
    private long stateKey() {
        if (job == null) return 0;
        long k = job.placed();
        k = k * 31 + job.cleared;
        k = k * 31 + job.filled;
        k = k * 31 + job.skippedCount();
        k = k * 31 + job.blockedCount();
        k = k * 31 + job.unloaded;
        k = k * 31 + job.state.ordinal();
        k = k * 31 + job.pause.ordinal();
        k = k * 31 + job.stage;
        k = k * 31 + builder.euLastTick();
        return k * 31 + job.flights.size();
    }

    /**
     * After a load: the campus is entered in the {@link CampusRegistry} where the nexus stands now (a nexus moved
     * whole, by a teleposer or a world editor, is found there, and the entries that moves and vanished nexuses left
     * nearby are dropped); jobs saved mid-way are surveyed again; jobs of an older plan version start their books over,
     * their launches in flight refunded by the part each remembers, and a job that had finished its clearing does
     * not clear again.
     */
    private void afterLoad() {
        World w = world();
        if (active) {
            int[] c = centre();
            CampusRegistry.dropStale(w, c[0], c[2], 2 * CampusPlan.GRADE_R);
            if (!CampusRegistry.has(w, c[0], c[1], c[2])) CampusRegistry.add(w, c[0], c[1], c[2], CampusPlan.GRADE_R);
        }
        List<BuildJob> all = new ArrayList<>(queue);
        if (job != null) all.add(job);
        for (BuildJob j : all) {
            if (j.version != BuildJob.V) {
                Builder.forget(j, ledger, null);
                long projected = j.projectedAt;
                j.restart(j.stage > 0 || j.keepClear);
                j.projectedAt = projected;
                dirty();
            } else if (!BuildState.ended(j.state) && j.state != BuildState.State.SURVEY) j.resurvey();
        }
    }

    private void creditIntake() {
        ItemStack s = intake.getStackInSlot(0);
        if (s == null) return;
        ItemStack left = credit(s, false, false);
        if (left != s) intake.setStackInSlot(0, left);
    }

    /** Notes when the nexus and the sited modules fell apart (for 修复), and forgets sites whose module is gone. */
    private void watchStructures(long now) {
        if (nexus.formed()) nexusUnformedSince = -1;
        else if (nexusUnformedSince < 0) nexusUnformedSince = now;
        World w = world();
        for (Iterator<Map.Entry<Integer, int[]>> it = sites.entrySet()
            .iterator(); it.hasNext();) {
            Map.Entry<Integer, int[]> e = it.next();
            int[] c = e.getValue();
            if (!w.blockExists(c[0], c[1], c[2])) continue;
            TileEntity te = w.getTileEntity(c[0], c[1], c[2]);
            if (!(te instanceof TileModule m)) {
                if (jobOnSite(e.getKey()) != null) continue;
                it.remove();
                siteKinds.remove(e.getKey());
                siteUnformedSince.remove(e.getKey());
                keepOutAt = Long.MIN_VALUE;
                dirty();
                continue;
            }
            if (m.formed()) siteUnformedSince.remove(e.getKey());
            else if (!siteUnformedSince.containsKey(e.getKey())) siteUnformedSince.put(e.getKey(), now);
            if ("".equals(siteKinds.get(e.getKey()))) siteKinds.put(e.getKey(), m.moduleKey());
        }
    }

    // ---- module jobs

    /**
     * Offers a module job for every module kind whose research the nexus's teams have, that has no module yet
     * (docked, sited or being built), while the ring has room: on the first free hall site of its preference, in
     * PROJECTING once surveyed, waiting for a member's 开始.
     */
    private void autoJobs(long now) {
        // the docked modules are known only once the nexus has looked for them since it loaded or formed
        if (!nexus.formed() || !nexus.dockKnown()) return;
        // the ring's room: the slots the phase opens, at most seven modules on a campus
        int cap = Math.min(RingSlots.open(TileNexus.PHASE), 7);
        int busy = nexus.docked()
            .size();
        for (BuildJob j : jobs()) if (j.module() != null && !j.repair()) busy++;
        for (ModuleSpec spec : ModuleSpecs.all()) {
            if (busy >= cap) return;
            if (!spec.enabled() || !nexus.has(spec.research) || exists(spec)) continue;
            Long until = declined.get(spec.key);
            if (until != null && now < until) continue;
            int site = freeSite(spec, -1, 1);
            if (site < 0) continue;
            enqueue(BuildJob.module(spec, site));
            busy++;
            tell(
                new ChatComponentTranslation(
                    P + "unlocked",
                    new ChatComponentTranslation("fluxecho.module." + spec.key),
                    String.valueOf(site)));
        }
    }

    /** Whether a module of the kind already stands (docked, on a recorded site) or is being built. */
    private boolean exists(ModuleSpec spec) {
        for (TileModule m : nexus.docked()) if (spec.key.equals(m.moduleKey())) return true;
        for (Map.Entry<Integer, int[]> e : sites.entrySet()) {
            String kind = siteKinds.get(e.getKey());
            if (spec.key.equals(kind) || ("".equals(kind) || kind == null) && spec.hasSite(e.getKey())) return true;
        }
        for (BuildJob j : jobs()) if (spec.key.equals(j.module())) return true;
        return false;
    }

    /**
     * The next hall site of the spec from {@code from} in direction {@code dir} (in its order of preference; from -1
     * the first) that is free: no recorded module, no job, no loaded module standing there, and its footprint clear of
     * the keep-out boxes. -1 when none is.
     */
    private int freeSite(ModuleSpec spec, int from, int dir) {
        int n = spec.sites.length;
        int start = -1;
        for (int i = 0; i < n; i++) if (spec.sites[i] == from) start = i;
        for (int k = 1; k <= n; k++) {
            int idx = start < 0 ? k - 1 : Math.floorMod(start + dir * k, n);
            int site = spec.sites[idx];
            if (site == from) continue;
            if (siteFree(spec, site)) return site;
        }
        return -1;
    }

    private boolean siteFree(ModuleSpec spec, int site) {
        if (sites.containsKey(site) || jobOnSite(site) != null) return false;
        int[] c = centre();
        int[] f = front();
        int[] box = spec.footprint(site, c[0], c[2], f[0], f[1], 1);
        refreshKeepOut();
        for (int[] k : liveKeepOut) if (overlap(box, k)) return false;
        for (TileModule m : NexusRegistry.modules(world().provider.dimensionId)) {
            if (m.isInvalid() || m.getWorldObj() != world()) continue;
            int[] mc = m.centre();
            if (mc[0] >= box[0] && mc[0] <= box[2] && mc[2] >= box[1] && mc[2] <= box[3]) return false;
        }
        return true;
    }

    private static boolean overlap(int[] a, int[] b) {
        int ax0 = Math.min(a[0], a[2]), ax1 = Math.max(a[0], a[2]), az0 = Math.min(a[1], a[3]),
            az1 = Math.max(a[1], a[3]);
        int bx0 = Math.min(b[0], b[2]), bx1 = Math.max(b[0], b[2]), bz0 = Math.min(b[1], b[3]),
            bz1 = Math.max(b[1], b[3]);
        return ax0 <= bx1 && bx0 <= ax1 && az0 <= bz1 && bz0 <= az1;
    }

    private BuildJob jobOnSite(int site) {
        for (BuildJob j : jobs()) if (j.site == site && !BuildState.ended(j.state)) return j;
        return null;
    }

    /** The current job and the queue. */
    private List<BuildJob> jobs() {
        List<BuildJob> out = new ArrayList<>();
        if (job != null) out.add(job);
        out.addAll(queue);
        return out;
    }

    private void enqueue(BuildJob j) {
        BuildJob before = job;
        job = admit(job, queue, j);
        if (job != before) {
            builder.reset();
            etaAt = Long.MIN_VALUE;
        }
        needDirty = true;
        stateChanged();
    }

    /**
     * Where a new job goes; returns the current job afterwards. Without a current job it becomes the current one. A
     * repair goes ahead of every queued job (none of those was started: 开始 acts on the current job only), and takes
     * the current job's place when no member has started that one and it has touched nothing (a module job waiting
     * for its core must not hold a repair up for good); the job that gives way goes back to the head of the queue and
     * is surveyed again when its turn comes. Any other job waits at the end of the queue.
     */
    static BuildJob admit(BuildJob current, List<BuildJob> queue, BuildJob j) {
        if (current == null) return j;
        if (!j.repair()) {
            queue.add(j);
            return current;
        }
        if (!current.repair() && yields(current)) {
            queue.add(0, current);
            return j;
        }
        int at = 0;
        while (at < queue.size() && queue.get(at)
            .repair()) at++;
        queue.add(at, j);
        return current;
    }

    /** Whether a job may give way to a repair: not over, never started, nothing touched, nothing in flight. */
    static boolean yields(BuildJob j) {
        return !BuildState.ended(j.state) && !j.started
            && !j.touched
            && j.flights.isEmpty()
            && (j.state == BuildState.State.SURVEY || j.state == BuildState.State.PROJECTING);
    }

    private void nextJob() {
        job = queue.isEmpty() ? null : queue.remove(0);
        // a job that gave way to a repair looks at the world again: the repair changed it
        if (job != null && !job.started && !BuildState.ended(job.state)) job.resurvey();
        builder.reset();
        needDirty = true;
        etaAt = Long.MIN_VALUE;
        stateChanged();
    }

    private void refreshKeepOut() {
        World w = world();
        if (w == null) return;
        long now = w.getTotalWorldTime();
        if (now - keepOutAt < 100 && keepOutAt != Long.MIN_VALUE) return;
        keepOutAt = now;
        List<int[]> out = new ArrayList<>();
        int[] c = centre();
        int[] f = front();
        int reach = CampusPlan.GRADE_R + 24;
        for (TileModule m : NexusRegistry.modules(w.provider.dimensionId)) {
            if (m.isInvalid() || m.getWorldObj() != w || onRecordedSite(m)) continue;
            int[] mc = m.centre();
            if (Math.max(Math.abs(mc[0] - c[0]), Math.abs(mc[2] - c[2])) > reach) continue;
            out.add(BuildPlan.moduleKeepOut(mc));
        }
        for (Map.Entry<Integer, int[]> e : sites.entrySet()) {
            ModuleSpec spec = ModuleSpecs.get(siteKinds.get(e.getKey()));
            if (spec != null && spec.hasSite(e.getKey()))
                out.add(spec.footprint(e.getKey(), c[0], c[2], f[0], f[1], 1));
        }
        liveKeepOut = out;
    }

    private boolean onRecordedSite(TileMultiblock m) {
        for (int[] s : sites.values()) if (s[0] == m.xCoord && s[1] == m.yCoord && s[2] == m.zCoord) return true;
        return false;
    }

    // ---- what the builder asks

    TileNexus nexus() {
        return nexus;
    }

    World world() {
        return nexus.getWorldObj();
    }

    /** The nexus centre {x, y0, z}. */
    int[] centre() {
        return nexus.centre();
    }

    /** The nexus front {fx, fz}. */
    int[] front() {
        return new int[] { nexus.front().offsetX, nexus.front().offsetZ };
    }

    /** The nexus controller {x, y, z}. */
    int[] controller() {
        return new int[] { nexus.xCoord, nexus.yCoord, nexus.zCoord };
    }

    UUID team() {
        return nexus.team();
    }

    FxBatch fx() {
        return fx;
    }

    /** Something saved changed (the ledger, a job's progress): save the tile, recompute the need. */
    void dirty() {
        saveDirty = true;
        needDirty = true;
    }

    /** A state the client shows changed: send the description again (once, at the end of the tick). */
    void stateChanged() {
        changed = true;
        dirty();
    }

    /** Where the module controller of a module job (or its repair) stands: {x, y, z, facing}; null for the others. */
    int[] moduleController(BuildJob j) {
        ModuleSpec spec = ModuleSpecs.get(j.module());
        if (spec == null || !spec.hasSite(j.site)) return null;
        int[] c = centre();
        int[] f = front();
        return spec.controller(j.site, c[0], c[1], c[2], f[0], f[1]);
    }

    /** The core part of a module job's module; -1 for the others. */
    int corePart(BuildJob j) {
        ModuleSpec spec = ModuleSpecs.get(j.module());
        return spec == null ? -1 : spec.corePart;
    }

    /**
     * Keeps drops in the spoils. The builder asks {@link #spoilsFit} first and waits while they would not fit, so
     * nothing it breaks is lost.
     */
    void spoil(List<ItemStack> drops) {
        for (ItemStack d : drops) {
            ItemStack left = d == null ? null : d.copy();
            for (int i = 0; i < spoils.getSlots() && left != null; i++) left = spoils.insertItem(i, left, false);
        }
    }

    /** Whether all of the drops would fit into the spoils as they are now. */
    boolean spoilsFit(List<ItemStack> drops) {
        ItemStackHandler room = new ItemStackHandler(SPOILS);
        for (int i = 0; i < SPOILS; i++) {
            ItemStack s = spoils.getStackInSlot(i);
            room.setStackInSlot(i, s == null ? null : s.copy());
        }
        for (ItemStack d : drops) {
            ItemStack left = d == null ? null : d.copy();
            for (int i = 0; i < SPOILS && left != null; i++) left = room.insertItem(i, left, false);
            if (left != null && left.stackSize > 0) return false;
        }
        spoilsTold = false;
        return true;
    }

    /** The builder waits for room in the spoils: tells the online members once (until the drops fit again). */
    void spoilsFull() {
        if (spoilsTold) return;
        spoilsTold = true;
        tell(new ChatComponentTranslation(P + "spoils_full", nexus.xCoord + ", " + nexus.yCoord + ", " + nexus.zCoord));
    }

    /** Whether a cell of ours belongs to another structure: another multiblock's box, or a keep-out box of the job. */
    boolean protectedCell(int x, int y, int z) {
        World w = world();
        long now = w.getTotalWorldTime();
        if (now - protectedAt >= 20 || protectedAt == Long.MIN_VALUE) {
            protectedAt = now;
            protectedBoxes = protectedBoxes();
        }
        for (int[] b : protectedBoxes) {
            if (x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] && z >= b[2] && z <= b[5]) return true;
        }
        return false;
    }

    private List<int[]> protectedBoxes() {
        List<int[]> out = new ArrayList<>();
        World w = world();
        int[] own = job == null ? null : moduleController(job);
        for (TileMultiblock m : NexusRegistry.loaded(w)) {
            if (m == nexus) continue;
            if (own != null && m.xCoord == own[0] && m.yCoord == own[1] && m.zCoord == own[2]) continue;
            int[] b = m.bounds();
            if (b != null) out.add(b.clone());
            else if (m instanceof TileModule tm) {
                int[] mc = tm.centre();
                int[] k = BuildPlan.moduleKeepOut(mc);
                out.add(new int[] { k[0], mc[1] - 1, k[1], k[2], mc[1] + 16, k[3] });
            }
        }
        if (job != null) for (int[] k : job.keepOut) out.add(
            new int[] { Math.min(k[0], k[2]), 0, Math.min(k[1], k[3]), Math.max(k[0], k[2]), 255,
                Math.max(k[1], k[3]) });
        return out;
    }

    /** Whether the column lies in another nexus's campus. */
    boolean otherCampusAt(int x, int z) {
        return CampusRegistry.ownerAt(world(), x, z, centre()) != null;
    }

    /** A job came to its end: tell the members. */
    void finished(BuildJob j) {
        tell(new ChatComponentTranslation(P + "done", j.name()));
    }

    /** Tells the online members of the nexus's teams; returns how many heard it. */
    private int tell(IChatComponent msg) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return 0;
        int n = 0;
        java.util.Set<UUID> teams = nexus.teams();
        for (Object o : server.getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP p && teams.contains(Owners.team(p.getUniqueID()))) {
                p.addChatMessage(msg);
                n++;
            }
        }
        return n;
    }

    /**
     * Tells the members of a legacy nexus whose teams have the library, once, that the forum can be laid now and how
     * the Echo Archive comes after it (only once no library is docked: a 0.9.2 hall counts as the library,
     * {@link #exists}): on the first tick after a load, then every ten seconds until someone was online to hear it.
     */
    private void legacyHint(long now) {
        if (hinted || now < hintAt) return;
        hintAt = now + 200;
        if (!nexus.has(Research.LIBRARY)) return;
        if (tell(new ChatComponentTranslation(P + "legacy_hint")) > 0) {
            hinted = true;
            dirty();
        }
    }

    // ---- entry points

    /**
     * A core was placed on the ground (lifted): the campus becomes active and the establish job is made, in SURVEY
     * and then PROJECTING; nothing is touched before a member presses 开始.
     */
    public String establish(EntityPlayer placer) {
        if (!Config.campusEnabled) return P + "legacy_only";
        if (active && job != null && job.raisesNexus()) return P + "already_running";
        active = true;
        hinted = true;
        int[] c = centre();
        CampusRegistry.add(world(), c[0], c[1], c[2], CampusPlan.GRADE_R);
        keepOutAt = Long.MIN_VALUE;
        enqueue(BuildJob.establish(keepOut()));
        return P + "placed_lifted";
    }

    /**
     * 铺设广场 on a legacy nexus: the campus becomes active and the forum job is made (grading, floor, fixtures).
     * Refused, like a lifted core, when another campus lies within {@code Config.buildMinSpacing}: the campuses would
     * overlap.
     */
    public String layForum(EntityPlayer p) {
        if (!nexus.member(p)) return NOT_MEMBER;
        if (active || !Config.campusEnabled) return P + "legacy_only";
        int[] c = centre();
        if (CampusRegistry.tooClose(world(), c[0], c[2], Config.buildMinSpacing) != null) return P + "forum_too_close";
        active = true;
        hinted = true;
        CampusRegistry.add(world(), c[0], c[1], c[2], CampusPlan.GRADE_R);
        keepOutAt = Long.MIN_VALUE;
        enqueue(BuildJob.forum(keepOut()));
        return P + "forum_laid";
    }

    /** 开始: a member's consent (or lifting the player's pause). A module job first needs its core in the ledger. */
    public String start(EntityPlayer p) {
        if (job == null || BuildState.ended(job.state)) return NO_JOB;
        if (!nexus.member(p)) return NOT_MEMBER;
        ModuleSpec spec = ModuleSpecs.get(job.module());
        if (spec != null && !job.repair()
            && BuildLedger.clamp(Config.buildCostScale) > 0
            && ledger.part(spec.corePart) < 1
            && !coreBuilt(job)) return P + "need_core";
        if (job.state == BuildState.State.SURVEY) {
            // consent given while the survey runs: it goes on by itself when the survey ends (and a pause asked
            // for during the survey is taken back)
            job.started = true;
            job.startedBy = p.getUniqueID();
            if (job.pause == BuildState.Pause.PLAYER) job.pause = BuildState.Pause.NONE;
            stateChanged();
            return P + "started";
        }
        if (!BuildState.canStart(job.state, job.pause)) return P + "already_running";
        job.state = BuildState.start(job.state, job.pause);
        job.pause = BuildState.Pause.NONE;
        job.started = true;
        job.startedBy = p.getUniqueID();
        stateChanged();
        return P + "started";
    }

    /** Whether the job's module controller already stands (its last step is done). */
    private boolean coreBuilt(BuildJob j) {
        BuildPlan.Plan pl = j.plan(this);
        return pl != null && !pl.steps.isEmpty() && j.done(pl.steps.size() - 1);
    }

    /**
     * 暂停: the player's pause (only 开始 lifts it). A started job that is being surveyed keeps the pause for the end
     * of its survey ({@link BuildJob#playerPause}); a job nobody started yet cannot be paused ({@code not_now}).
     */
    public String pause(EntityPlayer p) {
        if (job == null || BuildState.ended(job.state)) return NO_JOB;
        if (!nexus.member(p)) return NOT_MEMBER;
        if (!job.playerPause()) return P + "not_now";
        stateChanged();
        return P + "paused";
    }

    /**
     * 取消 (the GUI asks twice): the job ends, what was built stays, the cells in flight still land, the credit stays
     * in the ledger. A cancelled module is not offered again for a day.
     */
    public String cancel(EntityPlayer p) {
        if (job == null || BuildState.ended(job.state)) return NO_JOB;
        if (!nexus.member(p)) return NOT_MEMBER;
        job.state = BuildState.cancel(job.state);
        if (job.module() != null && !job.repair())
            declined.put(job.module(), world().getTotalWorldTime() + DECLINE_TICKS);
        stateChanged();
        return P + "cancelled";
    }

    /** ◀ ▶: moves a module job's projection to the next free site of its module, until it touched anything. */
    public String moveSite(EntityPlayer p, int dir) {
        if (job == null || BuildState.ended(job.state)) return NO_JOB;
        if (!nexus.member(p)) return NOT_MEMBER;
        ModuleSpec spec = ModuleSpecs.get(job.module());
        if (spec == null || job.repair() || job.touched || !job.flights.isEmpty()) return P + "site_locked";
        int site = freeSite(spec, job.site, dir < 0 ? -1 : 1);
        if (site < 0) return P + "site_locked";
        job.moveTo(spec, site);
        builder.reset();
        stateChanged();
        return P + "site_moved";
    }

    /** 补给: takes from the member's main inventory what the job still needs. */
    public String supply(EntityPlayer p) {
        if (job == null || BuildState.ended(job.state)) return NO_JOB;
        if (!nexus.member(p)) return NOT_MEMBER;
        ItemStack[] inv = p.inventory.mainInventory;
        boolean took = false;
        for (int i = 0; i < inv.length; i++) {
            ItemStack s = inv[i];
            if (s == null) continue;
            int before = s.stackSize;
            ItemStack left = credit(s, false, false);
            int after = left == null ? 0 : left.stackSize;
            if (after == before) continue;
            took = true;
            inv[i] = left;
        }
        if (!took) return P + "nothing_needed";
        p.inventory.markDirty();
        return P + "supplied";
    }

    /** 取出: whole items of the raw credit (not echo crystals) and the spoils, to the member (overflow at their feet). */
    public String withdraw(EntityPlayer p) {
        if (!nexus.member(p)) return NOT_MEMBER;
        boolean gave = false;
        for (Map.Entry<String, Integer> e : ledger.withdrawable()
            .entrySet()) {
            ItemStack kind = stackOf(e.getKey(), 1);
            if (kind == null) continue;
            int n = ledger.take(e.getKey(), e.getValue());
            int max = Math.max(1, kind.getMaxStackSize());
            while (n > 0) {
                ItemStack s = kind.copy();
                s.stackSize = Math.min(n, max);
                n -= s.stackSize;
                give(p, s);
                gave = true;
            }
        }
        for (int i = 0; i < spoils.getSlots(); i++) {
            ItemStack s = spoils.getStackInSlot(i);
            if (s == null) continue;
            spoils.setStackInSlot(i, null);
            give(p, s);
            gave = true;
        }
        if (!gave) return P + "nothing_to_withdraw";
        spoilsTold = false;
        dirty();
        p.inventory.markDirty();
        return P + "withdrawn";
    }

    private static void give(EntityPlayer p, ItemStack s) {
        if (!p.inventory.addItemStackToInventory(s) && s.stackSize > 0) p.dropPlayerItemWithRandomChoice(s, false);
    }

    /**
     * 修复: a job that stopped as incomplete starts over on the same plan (cells already right come free); otherwise a
     * repair of the nexus or of a sited module that stood unformed for a while is queued.
     */
    public String repair(EntityPlayer p) {
        if (!nexus.member(p)) return NOT_MEMBER;
        if (!active) return P + "nothing_to_repair";
        if (job != null && job.state == BuildState.State.PAUSED && job.pause == BuildState.Pause.INCOMPLETE) {
            // the same plan again, its clearing already done: only the structure's cells are put right
            Builder.forget(job, ledger, job.plan(this));
            job.restart(true);
            builder.reset();
            stateChanged();
            return P + "repair_queued";
        }
        BuildJob r = repairJob();
        if (r == null) return P + "nothing_to_repair";
        enqueue(r);
        return P + "repair_queued";
    }

    /** Whether 修复 would do something now (for the GUI's button). */
    public boolean canRepair() {
        if (!active) return false;
        if (job != null && job.state == BuildState.State.PAUSED && job.pause == BuildState.Pause.INCOMPLETE)
            return true;
        return repairJob() != null;
    }

    private BuildJob repairJob() {
        World w = world();
        if (w == null) return null;
        long now = w.getTotalWorldTime();
        if (!nexus.formed() && nexusUnformedSince >= 0 && now - nexusUnformedSince > REPAIR_AFTER) {
            boolean busy = false;
            for (BuildJob j : jobs()) if (j.raisesNexus() && !BuildState.ended(j.state)) busy = true;
            if (!busy) return BuildJob.repairNexus(keepOut());
        }
        for (Map.Entry<Integer, Long> e : siteUnformedSince.entrySet()) {
            if (now - e.getValue() <= REPAIR_AFTER || jobOnSite(e.getKey()) != null) continue;
            ModuleSpec spec = ModuleSpecs.get(siteKinds.get(e.getKey()));
            if (spec != null && spec.hasSite(e.getKey())) return BuildJob.repairModule(spec, e.getKey());
        }
        return null;
    }

    /** Refunds every cell in flight into the ledger (the core is being broken and carries the credit away). */
    public void refundFlights() {
        if (job != null) builder.refundFlights(job);
    }

    // ---- a core carried away and put back

    /**
     * What the core of an active campus takes along when it is broken, so the campus carries on when the core goes
     * back where it stood ({@link #resume}): the dimension, the core's position and front, the recorded hall sites
     * with their module kinds, 跳过受阻, the modules declined for a while, and the campus job (establish or forum)
     * that was not finished. No job, no ledger: the credit travels separately. Null for a legacy campus.
     */
    public NBTTagCompound carried() {
        if (!active) return null;
        World w = world();
        Carried c = new Carried();
        c.dim = w == null ? 0 : w.provider.dimensionId;
        c.core = controller();
        c.front = nexus.front()
            .ordinal();
        for (Map.Entry<Integer, int[]> e : sites.entrySet()) {
            c.sites.put(e.getKey(), e.getValue());
            String kind = siteKinds.get(e.getKey());
            c.kinds.put(e.getKey(), kind == null ? "" : kind);
        }
        c.skip = skipBlocked;
        c.declined.putAll(declined);
        for (BuildJob j : jobs()) {
            if (!j.campusJob() || BuildState.ended(j.state)) continue;
            if (j.raisesNexus()) c.unfinished = BuildPlan.ESTABLISH;
            else if (c.unfinished.isEmpty()) c.unfinished = BuildPlan.FORUM;
        }
        return c.write();
    }

    /** Whether the last {@link #resume} found another campus too close: the core then grows no campus at all. */
    private boolean resumeRefused;

    /**
     * Whether putting the core back where it stood was refused because another campus is now too close; a lifted
     * placement then must not lay out a new campus there either (it would ignore the spacing). Not saved.
     */
    public boolean resumeRefused() {
        return resumeRefused;
    }

    /**
     * A core carrying its campus ({@link #carried}) was placed: this tile was just made for it (server side). Where the
     * core stood before (the same dimension and block) the campus carries on, whichever way the core was placed: the
     * core takes its old front, so the dais fits it again; the campus is active again with its hall sites, so its
     * modules dock as before and 修复 works; and {@link #RESUME_CHECK} ticks later, once the structure was checked, a
     * repair of the nexus is queued when the nexus does not stand (or its establish job was not finished), or a forum
     * job that was not finished is queued again; neither grades the ground again, and both wait for 开始 like any
     * job. Placed anywhere else the core is a new nexus and nothing is resumed. Returns a lang key for the placer, or
     * null when there was nothing to resume here.
     */
    public String resume(NBTTagCompound tag) {
        resumeRefused = false;
        Carried c = Carried.read(tag);
        World w = world();
        if (c == null || w == null || w.isRemote || active || !Config.campusEnabled) return null;
        if (!c.at(w.provider.dimensionId, nexus.xCoord, nexus.yCoord, nexus.zCoord)) return null;
        // back on its own dais: it faces the way it did, whichever way the player stood
        if (nexus.front()
            .ordinal() != c.front) nexus.place(nexus.owner(), nexus.ownerName(), c.front);
        int[] ce = centre();
        if (CampusRegistry.tooClose(w, ce[0], ce[2], Config.buildMinSpacing) != null) {
            resumeRefused = true;
            return P + "resume_too_close";
        }
        active = true;
        hinted = true;
        skipBlocked = c.skip;
        sites.clear();
        siteKinds.clear();
        for (Map.Entry<Integer, int[]> e : c.sites.entrySet()) {
            sites.put(e.getKey(), e.getValue());
            siteKinds.put(e.getKey(), c.kinds.containsKey(e.getKey()) ? c.kinds.get(e.getKey()) : "");
        }
        declined.putAll(c.declined);
        resumeJob = c.unfinished;
        resumeCheckAt = w.getTotalWorldTime() + RESUME_CHECK;
        CampusRegistry.add(w, ce[0], ce[1], ce[2], CampusPlan.GRADE_R);
        keepOutAt = Long.MIN_VALUE;
        stateChanged();
        return P + "resumed";
    }

    /** The resumed campus looks at its nexus: a repair when it does not stand, the unfinished campus job again. */
    private void afterResume() {
        resumeCheckAt = -1;
        String unfinished = resumeJob;
        resumeJob = "";
        dirty();
        boolean nexusJob = false, forumJob = false;
        for (BuildJob j : jobs()) {
            if (BuildState.ended(j.state)) continue;
            if (j.raisesNexus()) nexusJob = true;
            else if (j.campusJob()) forumJob = true;
        }
        if (!nexusJob && (!nexus.formed() || BuildPlan.ESTABLISH.equals(unfinished))) {
            // the same plan as the establish job, without its grading: what stands comes free
            enqueue(BuildJob.repairNexus(keepOut()));
        } else if (!forumJob && BuildPlan.FORUM.equals(unfinished)) {
            BuildJob f = BuildJob.forum(keepOut());
            // its clearing was done before the core was broken
            f.restart(true);
            enqueue(f);
        }
    }

    /**
     * A campus as a broken core carries it ({@link #carried}), read and written without a world. Item NBT:
     * {@code Dim}, {@code Pos} (the core, int[3]), {@code F} (its front), {@code Sites} [{S, P, K}] as the campus saves
     * them, {@code Skip}, {@code Declined} {module: until}, {@code Job} (the plan key of the campus job not finished,
     * "" for none).
     */
    static final class Carried {

        int dim, front = 2;
        int[] core = new int[3];
        final Map<Integer, int[]> sites = new TreeMap<>();
        final Map<Integer, String> kinds = new HashMap<>();
        boolean skip;
        final Map<String, Long> declined = new HashMap<>();
        String unfinished = "";

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("Dim", dim);
            t.setIntArray("Pos", core.clone());
            t.setByte("F", (byte) front);
            NBTTagList l = new NBTTagList();
            for (Map.Entry<Integer, int[]> e : sites.entrySet()) {
                NBTTagCompound c = new NBTTagCompound();
                c.setInteger("S", e.getKey());
                c.setIntArray("P", e.getValue());
                String kind = kinds.get(e.getKey());
                c.setString("K", kind == null ? "" : kind);
                l.appendTag(c);
            }
            t.setTag("Sites", l);
            t.setBoolean("Skip", skip);
            NBTTagCompound d = new NBTTagCompound();
            for (Map.Entry<String, Long> e : declined.entrySet()) d.setLong(e.getKey(), e.getValue());
            t.setTag("Declined", d);
            t.setString("Job", unfinished);
            return t;
        }

        /** The carried campus, or null when the tag holds none (no core position). */
        static Carried read(NBTTagCompound t) {
            if (t == null || !t.hasKey("Pos")) return null;
            int[] pos = t.getIntArray("Pos");
            if (pos.length != 3) return null;
            Carried c = new Carried();
            c.dim = t.getInteger("Dim");
            c.core = pos;
            int f = t.getByte("F");
            c.front = f >= 2 && f <= 5 ? f : 2;
            NBTTagList l = t.getTagList("Sites", 10);
            for (int i = 0; i < l.tagCount(); i++) {
                NBTTagCompound e = l.getCompoundTagAt(i);
                int[] p = e.getIntArray("P");
                if (p.length != 3) continue;
                c.sites.put(e.getInteger("S"), p);
                c.kinds.put(e.getInteger("S"), e.getString("K"));
            }
            c.skip = t.getBoolean("Skip");
            NBTTagCompound d = t.getCompoundTag("Declined");
            for (Object k : d.func_150296_c()) c.declined.put((String) k, d.getLong((String) k));
            String job = t.getString("Job");
            c.unfinished = BuildPlan.ESTABLISH.equals(job) || BuildPlan.FORUM.equals(job) ? job : "";
            return c;
        }

        /** Whether the core stood at the block in the dimension. */
        boolean at(int dimension, int x, int y, int z) {
            return dim == dimension && core[0] == x && core[1] == y && core[2] == z;
        }
    }

    // ---- materials

    /**
     * Credits a stack offered by automation (the supply port): only to a job a member started, and only what it still
     * needs. Returns what is left (null when all was taken).
     */
    public ItemStack offer(ItemStack s, boolean simulate) {
        return credit(s, simulate, true);
    }

    /** Credits what the job needs of a stack; {@code automation} only feeds a started job. */
    private ItemStack credit(ItemStack s, boolean simulate, boolean automation) {
        if (s == null || s.stackSize <= 0 || s.getItem() == null) return s;
        if (!active || job == null || BuildState.ended(job.state)) return s;
        int code = partCode(s);
        if (automation && !job.started && !BuildState.consented(job.state)) {
            // before 开始 automation hands in only the module's core, which 开始 asks for
            int core = corePart(job);
            if (core < 0 || code != core) return s;
        }
        Map<String, Long> want = need();
        if (code >= 0) {
            Long units = want.get(PartRecipes.part(code));
            if (units == null || units <= 0) return s;
            int n = (int) Math.min(s.stackSize, (units + PartRecipes.UNIT - 1) / PartRecipes.UNIT);
            if (!simulate) {
                ledger.addPart(code, n);
                dirty();
            }
            return rest(s, n);
        }
        // every raw line the item matches takes what it lacks, the next one what is left (glowstone dust feeds both
        // an item line and an ore line)
        Map<String, Integer> shares = BuildLedger.share(s.stackSize, want, k -> {
            ResearchTree.Cost c = cost(k);
            return c != null && Costs.matches(c, s);
        });
        int taken = 0;
        for (Map.Entry<String, Integer> e : shares.entrySet()) {
            taken += e.getValue();
            if (!simulate) ledger.addRaw(e.getKey(), e.getValue() * PartRecipes.UNIT);
        }
        if (taken <= 0) return s;
        if (!simulate) dirty();
        return rest(s, taken);
    }

    private static ItemStack rest(ItemStack s, int taken) {
        if (taken >= s.stackSize) return null;
        ItemStack left = s.copy();
        left.stackSize -= taken;
        return left;
    }

    /** The part code of one of our block items, or -1. */
    private static int partCode(ItemStack s) {
        net.minecraft.block.Block b = net.minecraft.block.Block.getBlockFromItem(s.getItem());
        if (b == null || b == net.minecraft.init.Blocks.air) return -1;
        int code = PartBlocks.code(b, s.getItemDamage());
        if (code >= 0) return code;
        return b == PartBlocks.block(Parts.SUPPLY_PORT) ? Parts.SUPPLY_PORT : -1;
    }

    private static final Map<String, ResearchTree.Cost> COSTS = new HashMap<>();

    private static synchronized ResearchTree.Cost cost(String key) {
        if (COSTS.containsKey(key)) return COSTS.get(key);
        ResearchTree.Cost c;
        try {
            c = ResearchTree.Cost.parse(key);
        } catch (RuntimeException e) {
            c = null;
        }
        COSTS.put(key, c);
        return c;
    }

    /**
     * An item stack for a ledger key: a part's block, an item key's item, or the preferred ore-dictionary entry of an
     * ore key (GregTech's first); null when nothing in the pack stands for it.
     */
    public static ItemStack stackOf(String key, int count) {
        int code = PartRecipes.partCode(key);
        if (code >= 0) return PartBlocks.stack(code, count);
        ResearchTree.Cost c = cost(key);
        if (c == null) return null;
        ItemStack best = null;
        for (ItemStack o : Costs.options(c)) {
            if (o == null || o.getItem() == null) continue;
            Object name = Item.itemRegistry.getNameForObject(o.getItem());
            if (name != null && name.toString()
                .startsWith("gregtech:")) {
                best = o;
                break;
            }
            if (best == null) best = o;
        }
        if (best == null) return null;
        ItemStack out = best.copy();
        out.stackSize = count;
        if (out.getItemDamage() == OreDictionary.WILDCARD_VALUE) out.setItemDamage(0);
        return out;
    }

    /** What the current job still needs, in units (empty when none); see {@link BuildLedger#need}. */
    private Map<String, Long> need() {
        if (job == null) return Collections.emptyMap();
        if (needDirty || needJob != job) {
            needJob = job;
            needDirty = false;
            need = ledger.need(unbuilt(job), Config.buildCostScale);
        }
        return need;
    }

    /** Part counts of the job's steps that are not done, skipped or in flight (the free supply port left out). */
    private Map<Integer, Integer> unbuilt(BuildJob j) {
        Map<Integer, Integer> out = new TreeMap<>();
        BuildPlan.Plan pl = j.plan(this);
        if (pl == null) return out;
        for (int i = 0; i < pl.steps.size(); i++) {
            if (j.done(i) || j.skipped(i) || j.flying(i)) continue;
            BuildPlan.Step s = pl.steps.get(i);
            if (s.kind == BuildPlan.AIR || s.part == Parts.SUPPLY_PORT && j.campusJob()) continue;
            out.merge(s.part, 1, Integer::sum);
        }
        return out;
    }

    // ---- numbers

    /**
     * The bill of the current job: what a player must still supply, in units (1/65536 item), largest first; a module
     * job's core first. Empty without a job.
     */
    public Map<String, Long> bill() {
        if (job == null) return Collections.emptyMap();
        Map<String, Long> b = BuildLedger.bill(need());
        int core = corePart(job);
        String coreKey = core < 0 ? null : PartRecipes.part(core);
        if (coreKey == null || !b.containsKey(coreKey)) return b;
        Map<String, Long> out = new LinkedHashMap<>();
        out.put(coreKey, b.get(coreKey));
        for (Map.Entry<String, Long> e : b.entrySet()) if (!e.getKey()
            .equals(coreKey)) out.put(e.getKey(), e.getValue());
        return out;
    }

    /** Steps done (placed, or found already right). */
    public int placed() {
        return job == null ? 0 : job.placed();
    }

    /** Steps in the job's plan. */
    public int total() {
        if (job == null) return 0;
        BuildPlan.Plan pl = job.plan(this);
        return pl == null ? 0 : pl.steps.size();
    }

    public int cleared() {
        return job == null ? 0 : job.cleared;
    }

    public int blocked() {
        return job == null ? 0 : job.blockedCount();
    }

    public int skipped() {
        return job == null ? 0 : job.skippedCount();
    }

    public int unloaded() {
        return job == null ? 0 : job.unloaded;
    }

    /** EU the builder drew last tick. */
    public long euPerTick() {
        return builder.euLastTick();
    }

    /** An estimate of the ticks the job still takes at the configured pace; -1 when it cannot progress. */
    public long etaTicks() {
        if (job == null) return 0;
        World w = world();
        long now = w == null ? 0 : w.getTotalWorldTime();
        if (now - etaAt < 20 && etaAt != Long.MIN_VALUE) return eta;
        etaAt = now;
        BuildPlan.Plan pl = job.plan(this);
        if (pl == null) return eta = -1;
        int[] rem = new int[pl.stages];
        for (int i = 0; i < pl.steps.size(); i++) if (!job.done(i) && !job.skipped(i)) rem[pl.steps.get(i).stage]++;
        long clears = Math.max(0, job.natural - (job.cleared - job.clearedAtSurvey))
            + Math.max(0, job.toFill - (job.filled - job.filledAtSurvey));
        eta = BuildPace.etaTicks(rem, job.campusJob(), Config.buildBlocksPerTick, clears, Config.buildClearPerTick);
        return eta;
    }

    /** Up to 64 blocked world cells {x, y, z}. */
    public List<int[]> blockedCells() {
        return job == null ? new ArrayList<>() : job.blockedCells();
    }

    // ---- saving

    /** Writes the campus under the tag {@code Campus} of the nexus's tag. */
    public void writeNBT(NBTTagCompound tile) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("St", active ? "active" : "legacy");
        t.setBoolean("Hint", hinted);
        t.setBoolean("Skip", skipBlocked);
        NBTTagList s = new NBTTagList();
        for (Map.Entry<Integer, int[]> e : sites.entrySet()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("S", e.getKey());
            c.setIntArray("P", e.getValue());
            String kind = siteKinds.get(e.getKey());
            c.setString("K", kind == null ? "" : kind);
            s.appendTag(c);
        }
        t.setTag("Sites", s);
        if (job != null) t.setTag("Job", job.write());
        NBTTagList q = new NBTTagList();
        for (BuildJob j : queue) q.appendTag(j.write());
        t.setTag("Queue", q);
        NBTTagCompound l = new NBTTagCompound(), raw = new NBTTagCompound(), parts = new NBTTagCompound();
        for (Map.Entry<String, Long> e : ledger.rawView()
            .entrySet()) raw.setLong(e.getKey(), e.getValue());
        for (Map.Entry<Integer, Integer> e : ledger.partView()
            .entrySet()) parts.setInteger(String.valueOf(e.getKey()), e.getValue());
        l.setTag("Raw", raw);
        l.setTag("Parts", parts);
        t.setTag("Ledger", l);
        t.setTag("BuildIn", intake.serializeNBT());
        t.setTag("Spoils", spoils.serializeNBT());
        NBTTagCompound d = new NBTTagCompound();
        for (Map.Entry<String, Long> e : declined.entrySet()) d.setLong(e.getKey(), e.getValue());
        t.setTag("Declined", d);
        if (resumeCheckAt >= 0) {
            t.setLong("Rc", resumeCheckAt);
            t.setString("Rj", resumeJob);
        }
        tile.setTag("Campus", t);
    }

    /** Reads the campus from the nexus's tag; a tag without {@code Campus} is a legacy nexus. */
    public void readNBT(NBTTagCompound tile) {
        sites.clear();
        siteKinds.clear();
        queue.clear();
        declined.clear();
        job = null;
        loaded = false;
        needDirty = true;
        hintAt = 0;
        resumeCheckAt = -1;
        resumeJob = "";
        intake.setStackInSlot(0, null);
        for (int i = 0; i < SPOILS; i++) spoils.setStackInSlot(i, null);
        if (tile == null || !tile.hasKey("Campus")) {
            active = false;
            hinted = false;
            skipBlocked = false;
            ledger.load(null, null);
            return;
        }
        NBTTagCompound t = tile.getCompoundTag("Campus");
        active = "active".equals(t.getString("St"));
        hinted = t.getBoolean("Hint");
        skipBlocked = t.getBoolean("Skip");
        NBTTagList s = t.getTagList("Sites", 10);
        for (int i = 0; i < s.tagCount(); i++) {
            NBTTagCompound c = s.getCompoundTagAt(i);
            int[] pos = c.getIntArray("P");
            if (pos.length != 3) continue;
            sites.put(c.getInteger("S"), pos);
            siteKinds.put(c.getInteger("S"), c.getString("K"));
        }
        job = BuildJob.read(t.getCompoundTag("Job"));
        NBTTagList q = t.getTagList("Queue", 10);
        for (int i = 0; i < q.tagCount(); i++) {
            BuildJob j = BuildJob.read(q.getCompoundTagAt(i));
            if (j != null) queue.add(j);
        }
        NBTTagCompound l = t.getCompoundTag("Ledger"), raw = l.getCompoundTag("Raw"), parts = l.getCompoundTag("Parts");
        Map<String, Long> r = new TreeMap<>();
        for (Object k : raw.func_150296_c()) r.put((String) k, raw.getLong((String) k));
        Map<Integer, Integer> pm = new TreeMap<>();
        for (Object k : parts.func_150296_c()) {
            try {
                pm.put(Integer.parseInt((String) k), parts.getInteger((String) k));
            } catch (NumberFormatException ignored) {}
        }
        ledger.load(r, pm);
        if (t.hasKey("BuildIn")) {
            ItemStackHandler saved = new ItemStackHandler(0);
            saved.deserializeNBT(t.getCompoundTag("BuildIn"));
            intake.setStackInSlot(0, saved.getSlots() > 0 ? saved.getStackInSlot(0) : null);
        }
        if (t.hasKey("Spoils")) {
            // through a handler of the saved size, so a changed size keeps what fits
            ItemStackHandler saved = new ItemStackHandler(0);
            saved.deserializeNBT(t.getCompoundTag("Spoils"));
            for (int i = 0; i < SPOILS; i++)
                spoils.setStackInSlot(i, i < saved.getSlots() ? saved.getStackInSlot(i) : null);
        }
        NBTTagCompound d = t.getCompoundTag("Declined");
        for (Object k : d.func_150296_c()) declined.put((String) k, d.getLong((String) k));
        if (t.hasKey("Rc")) {
            resumeCheckAt = t.getLong("Rc");
            resumeJob = t.getString("Rj");
        }
    }

    // ---- the client copy

    /**
     * The small client copy for the nexus's description packet: the mode and 跳过受阻, then with a job its key, plan
     * key, site, plan version, state, pause, stage and progress, when it was projected and finished, its top three
     * missing items, its blocked cells ({@link FxCodec}, relative to the centre) and the keep-out boxes its plan was
     * made with. Sent on state changes only; the progress numbers between them go over the network
     * ({@link CampusNet#sendState}).
     */
    public void writeSync(NBTTagCompound t) {
        t.setBoolean("Ac", active);
        // the switch is the campus's, not the job's: it reaches the client with or without a job
        t.setBoolean("Sk", skipBlocked);
        if (job == null) return;
        lastBlockedHash = job.blockedShownHash();
        t.setString("K", job.key);
        t.setString("Pk", job.planKey);
        t.setByte("S", (byte) job.site);
        t.setInteger("V", job.version);
        t.setByte("St", (byte) job.state.ordinal());
        t.setByte("Ps", (byte) job.pause.ordinal());
        t.setByte("Sg", (byte) job.stage);
        BuildPlan.Plan pl = job.plan(this);
        t.setByte("Sn", (byte) (pl == null ? 0 : pl.stages));
        t.setInteger("Pl", job.placed());
        t.setInteger("To", pl == null ? 0 : pl.steps.size());
        t.setLong("Pa", job.projectedAt);
        t.setLong("Ft", job.finishedAt);
        NBTTagList mi = new NBTTagList();
        for (Map.Entry<String, Long> e : bill().entrySet()) {
            if (mi.tagCount() >= 3) break;
            long items = (e.getValue() + PartRecipes.UNIT - 1) / PartRecipes.UNIT;
            ItemStack s = stackOf(e.getKey(), 1);
            if (s == null) continue;
            NBTTagCompound c = s.writeToNBT(new NBTTagCompound());
            c.setLong("N", items);
            mi.appendTag(c);
        }
        t.setTag("Mi", mi);
        int[] c = centre();
        List<Integer> bl = new ArrayList<>();
        for (int[] b : job.blockedCells()) {
            int dx = b[0] - c[0], dy = b[1] - c[1], dz = b[2] - c[2];
            if (FxCodec.fits(dx, dy, dz)) bl.add(FxCodec.pack(dx, dy, dz));
        }
        int[] blocked = new int[bl.size()];
        for (int i = 0; i < blocked.length; i++) blocked[i] = bl.get(i);
        t.setIntArray("Bl", blocked);
        t.setIntArray("Ko", BuildJob.boxes(job.keepOut));
    }

    /** The client's copy of {@link #writeSync}. */
    public static final class View {

        public boolean active, skipBlocked;
        /** The job key ("" for none) and the key of the plan it builds. */
        public String job = "", planKey = "";
        public int site = -1, version, state, pause, stage, stages, placed, total;
        public long projectedAt = -1, finishedAt = -1;
        /** The top three missing items (one each) and how many of each. */
        public ItemStack[] missing = new ItemStack[0];
        public long[] missingCount = new long[0];
        /** Blocked cells, packed relative to the nexus centre ({@link FxCodec}). */
        public int[] blocked = new int[0];
        /** The keep-out boxes {x0, z0, x1, z1} the job's plan was made with. */
        public List<int[]> keepOut = new ArrayList<>();

        public void read(NBTTagCompound t) {
            active = t.getBoolean("Ac");
            skipBlocked = t.getBoolean("Sk");
            job = t.getString("K");
            planKey = t.hasKey("Pk") ? t.getString("Pk") : job;
            site = t.hasKey("S") ? t.getByte("S") : -1;
            version = t.getInteger("V");
            state = t.getByte("St");
            pause = t.getByte("Ps");
            stage = t.getByte("Sg");
            stages = t.getByte("Sn");
            placed = t.getInteger("Pl");
            total = t.getInteger("To");
            projectedAt = t.hasKey("Pa") ? t.getLong("Pa") : -1;
            finishedAt = t.hasKey("Ft") ? t.getLong("Ft") : -1;
            NBTTagList mi = t.getTagList("Mi", 10);
            List<ItemStack> ms = new ArrayList<>();
            List<Long> mc = new ArrayList<>();
            for (int i = 0; i < mi.tagCount(); i++) {
                NBTTagCompound c = mi.getCompoundTagAt(i);
                ItemStack s = ItemStack.loadItemStackFromNBT(c);
                if (s == null) continue;
                ms.add(s);
                mc.add(c.getLong("N"));
            }
            missing = ms.toArray(new ItemStack[0]);
            missingCount = new long[mc.size()];
            for (int i = 0; i < missingCount.length; i++) missingCount[i] = mc.get(i);
            blocked = t.getIntArray("Bl");
            keepOut = new ArrayList<>();
            int[] ko = t.getIntArray("Ko");
            for (int i = 0; i + 3 < ko.length; i += 4)
                keepOut.add(new int[] { ko[i], ko[i + 1], ko[i + 2], ko[i + 3] });
        }

        /** Whether there is a job. */
        public boolean hasJob() {
            return job != null && !job.isEmpty();
        }

        public BuildState.State stateEnum() {
            BuildState.State[] v = BuildState.State.values();
            return state >= 0 && state < v.length ? v[state] : BuildState.State.SURVEY;
        }

        public BuildState.Pause pauseEnum() {
            BuildState.Pause[] v = BuildState.Pause.values();
            return pause >= 0 && pause < v.length ? v[pause] : BuildState.Pause.NONE;
        }
    }
}
