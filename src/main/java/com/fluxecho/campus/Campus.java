package com.fluxecho.campus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;

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

import com.fluxecho.FluxEcho;
import com.fluxecho.config.CampusConfig;
import com.fluxecho.core.Owners;
import com.fluxecho.logic.BuildLedger;
import com.fluxecho.logic.BuildPace;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.BuildState;
import com.fluxecho.logic.CampusBand;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.CampusSplit;
import com.fluxecho.logic.CityOffer;
import com.fluxecho.logic.DomeGeneratorShape;
import com.fluxecho.logic.FxCodec;
import com.fluxecho.logic.Mix;
import com.fluxecho.logic.OuterRing;
import com.fluxecho.logic.PartRecipes;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.ResearchTree;
import com.fluxecho.logic.RootShape;
import com.fluxecho.logic.SiteFamily;
import com.fluxecho.logic.SkyRing;
import com.fluxecho.logic.WrongCell;
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
 * once the nexus's teams have a module's research and a site of its is free (0.14.1: no ring room to wait for any
 * more, {@link CityOffer}); a docked 0.9.2 hall that alone stands in a module's way is offered a retire job.
 * <p>
 * Materials come from members (补给, the intake slot) and, for a started job, from automation (the supply port,
 * {@link #offer}); the ledger only takes what the job still needs. Every entry point returns a lang key under
 * {@code fluxecho.build.} saying what happened.
 * <p>
 * 0.10.1: what the builder takes down goes to the campus's {@link RecycleStore} ({@link #recycle()}, taken out at the
 * recycle bin or with 取出), which replaced the 27 slots of spoils. A job started by a player in creative mode is free
 * (no materials, no EU), and an operator can make every job of the nexus free ({@link #setFreeBuild}) or finish the
 * current one at once ({@link #finishNow}). A campus whose floor was laid by an older plan is offered one 整修园区 job
 * ({@link BuildJob#refresh}). The 建造 page and the masterplan show every module the nexus can build and where it
 * stands ({@link #modules()}).
 * <p>
 * 0.15.0: a nexus of phase III whose teams researched the outer ring is offered the outer establish
 * ({@link BuildJob#outer}, {@code establish:outer}), which grades and paves the outer ring; once it has run the outer
 * families' sites stand on laid ground ({@link #groundLaid}) and their modules are offered there. A campus whose outer
 * disc reaches a neighbouring campus's shares the ground with it instead of being refused ({@link CampusSplit}).
 * <p>
 * 0.16.0: a nexus of phase IV whose teams researched the sky ring is offered the release of the Echo Dome Generator's
 * pod ({@link BuildJob#release}): its legs come down from the top, credited to the ledger, and the pod stands as
 * {@code floating}. From then on the pod is never built on legs again: a repair of a floating pod, and a dome job
 * offered once the pod may float from the start, build the floating plan ({@link BuildJob.Form#pod}).
 */
public final class Campus {

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
    /** What the builder took down (0.10.1); every change marks the campus to be saved. */
    private final RecycleStore recycle = new RecycleStore(this::dirty);
    /** The box table of the campus's buildings (0.17.0, style §3.9): saved as {@code Skn}, sent to the players near. */
    private final SkinFeed skin = new SkinFeed(this);

    private boolean active, hinted, skipBlocked;
    /** Every job of this nexus is free (an operator's switch, NBT {@code Fb}). */
    private boolean freeBuild;
    /**
     * The grading version ({@link BuildJob#GRADE_V}; up to 0.14.0 it was {@link BuildJob#V}, then also 2) under which
     * the campus's grading last ran in full, or a campus job was declined (NBT {@code Pv}; 0 for a campus saved before
     * 0.10.1). Below the current one the campus is offered a refresh ({@link BuildJob#refresh}) once.
     */
    private int gradedVersion;
    private BuildJob job;
    private final List<BuildJob> queue = new ArrayList<>();
    /** Recorded module controllers by hall site, and the module key standing there ("" when not known). */
    private final Map<Integer, int[]> sites = new TreeMap<>();
    private final Map<Integer, String> siteKinds = new HashMap<>();
    /** Module keys whose job a member cancelled, until when they are not offered again. */
    private final Map<String, Long> declined = new HashMap<>();
    /**
     * The plan keys of the campus jobs that laid ground beyond the forum (0.15.0, {@link SiteFamily#ground}: the outer
     * establish once it ran to its end), saved as {@code Gl} and carried by a broken core.
     */
    private final Set<String> laid = new LinkedHashSet<>();
    /**
     * The neighbours {x, z} (or {x, z, claims}) the outer ring was laid with (0.15.0), saved as {@code Gn} (their
     * claims {@code Gq}) and carried by a broken core: the outer sites are judged against them as well as against the
     * campuses registered now ({@link #splitNeighbours}), so a site the ring left to a neighbour stays trimmed, and
     * once one of them is gone the campus offers to regain its side ({@link #offerOuter}).
     */
    private final List<int[]> laidWith = new ArrayList<>();
    /**
     * The keep-out boxes {x0, z0, x1, z1} (by their columns) of the modules on no site of the campus that the outer
     * ring was laid round (0.15.0: a 0.9.2 hall, a stray core, another campus's building), saved as {@code Gk} and
     * carried by a broken core: once one of them is gone and nothing holds its box, the campus offers to lay the ring
     * there too ({@link #offerPatch}), and the box is forgotten.
     */
    private final List<int[]> laidBoxes = new ArrayList<>();
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
    private List<int[]> liveKeepOut = new ArrayList<>(), liveOuterKeepOut = new ArrayList<>(),
        protectedBoxes = new ArrayList<>();
    /**
     * The boxes of {@link #liveOuterKeepOut} that the modules on no recorded site give (0.15.0, {@link #offerPatch}).
     */
    private List<int[]> liveOuterStrays = new ArrayList<>();
    /** The radius the campus registry holds for this campus (0: not written since the load). */
    private int registeredRadius;
    /** The outer diagonals the campus registry holds as claimed by this campus ({@link #claims}, 0.15.0). */
    private int registeredClaims = -1;
    /** The neighbours the client was last told of ({@code Ns}, 0.15.0), as a signature. */
    private String syncedNeighbours = "";
    /** The outer establish's give-up for the 建造 page ({@link #outerTrim}), and the job and neighbours it was for. */
    private int[] outerTrim;
    private String outerTrimFor = "";
    private long keepOutAt = Long.MIN_VALUE, protectedAt = Long.MIN_VALUE, etaAt = Long.MIN_VALUE;
    private long eta;
    private long lastStateKey = Long.MIN_VALUE, lastStateSent = Long.MIN_VALUE, lastBlockedHash, hintAt;
    /** Whether the members were told that the recycle store is full (until it has room again). */
    private boolean recycleTold;
    /** An operator's instant build ({@link #finishNow}): the job, who asked (null: nobody to tell), whether told. */
    private BuildJob instantJob;
    private UUID instantBy;
    private boolean instantPauseTold;
    /** A hash of the module list last sent ({@link #modules()}), to send the description again when it changes. */
    private long modulesHash;

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

    /** For each queued job, in the order of {@link #queue()}, whether it is the deepen job (0.16.0). */
    public List<Boolean> queueDeepens() {
        List<Boolean> out = new ArrayList<>();
        for (BuildJob j : queue) out.add(j.deepens());
        return out;
    }

    public BuildLedger ledger() {
        return ledger;
    }

    /** The GUI's intake slot (not open to automation); credited every tick. */
    public ItemStackHandler intake() {
        return intake;
    }

    /**
     * What the builder took down (0.10.1): the drops of what it cleared and the contents of what it dismantled. Read it
     * freely; take from it through {@link #takeRecycled} (any change marks the campus to be saved either way).
     */
    public RecycleStore recycle() {
        return recycle;
    }

    /** Whether every job of this nexus is free (an operator's switch, {@code /fluxecho build free}). */
    public boolean freeBuild() {
        return freeBuild;
    }

    /**
     * Turns free building for every job of this nexus on or off ({@code /fluxecho build free}); returns the lang key
     * {@code fluxecho.build.free_on} or {@code free_off} for the reply.
     */
    public String setFreeBuild(boolean on) {
        if (freeBuild != on) {
            freeBuild = on;
            needDirty = true;
            stateChanged();
        }
        return P + (on ? "free_on" : "free_off");
    }

    /**
     * Whether a job is free: it was started by a player in creative mode ({@link BuildJob#free()}), or the nexus builds
     * everything free ({@link #freeBuild}). A free job takes no materials and no EU, and needs no core before 开始.
     */
    public boolean free(BuildJob j) {
        return j != null && (j.free || freeBuild);
    }

    /** Whether the job runs as an operator's instant build ({@link #finishNow}). */
    public boolean instant(BuildJob j) {
        return j != null && j == instantJob;
    }

    /** The campus layout, seeded with the nexus controller's position. */
    public CampusPlan plan() {
        if (plan == null) plan = new CampusPlan(Mix.seed(nexus.xCoord, nexus.yCoord, nexus.zCoord));
        return plan;
    }

    /** The sites with a recorded module, in order (the hall sites, and since 0.12.0 the axis sites). */
    public List<Integer> recordedSites() {
        return new ArrayList<>(sites.keySet());
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
     * are not on a recorded hall site (0.9.2 halls) and the footprints of the modules on recorded sites; the zenith's
     * legs as height-ranged boxes {x0, z0, x1, z1, yFrom, yTo} (0.12.0). The current job's plan was made with its own
     * boxes ({@link BuildJob#keepOut()}).
     */
    public List<int[]> keepOut() {
        refreshKeepOut();
        List<int[]> out = new ArrayList<>();
        for (int[] b : liveKeepOut) out.add(b.clone());
        return out;
    }

    /**
     * The keep-out boxes of the outer establish (0.15.0): {@link #keepOut()}, but the loaded modules on no recorded
     * site are looked for out to the outer disc ({@link #OUTER_REACH}), not only round the inner one.
     */
    public List<int[]> outerKeepOut() {
        refreshKeepOut();
        List<int[]> out = new ArrayList<>();
        for (int[] b : liveOuterKeepOut) out.add(b.clone());
        return out;
    }

    /**
     * How far from the nexus centre (Chebyshev) a module standing on no recorded site is kept out of the outer
     * establish and keeps its keep-out box round it in the campus's look at the outer sites: the outer disc and the
     * margin the inner disc has ({@link CampusPlan#GRADE_R} + 24).
     */
    static final int OUTER_REACH = OuterRing.OUTER_R + 24;

    /**
     * The centres {x, z} of the other campuses near enough to share the outer ring's ground (0.15.0,
     * {@link CampusSplit#REACH}), from the campus registry; with {@code drop} the entries whose nexus is gone are
     * dropped first (the outer establish would else leave them ground for ever).
     */
    List<int[]> neighbours(boolean drop) {
        World w = world();
        List<int[]> out = new ArrayList<>();
        // only a server world has a registry (a bare test world has no provider either)
        if (w == null || w.isRemote || w.provider == null) return out;
        int[] c = centre();
        if (drop) CampusRegistry.dropStale(w, c[0], c[2], CampusSplit.REACH);
        for (int[] o : CampusRegistry.centres(w)) {
            if (o[0] == c[0] && o[1] == c[1] && o[2] == c[2]) continue;
            if (Math.max(Math.abs(o[0] - c[0]), Math.abs(o[2] - c[2])) > CampusSplit.REACH) continue;
            // with the outer diagonals it claimed (0.15.0): the split gives a claimed site wholly to its campus
            out.add(o[3] != 0 ? new int[] { o[0], o[2], o[3] } : new int[] { o[0], o[2] });
        }
        out.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        return out;
    }

    /**
     * The neighbours the campus's outer sites are judged against (0.15.0): those registered now
     * ({@link #neighbours}, with their claims), and those the outer ring was laid with that are gone from the
     * registry ({@link #laidWith}, with the claims they had when it was laid), so a site the ring left to a neighbour
     * is not offered as whole while its ground is still ungraded: a site that shares a cell with an outer diagonal a
     * gone neighbour had claimed stays held for good ({@link CityOffer#TRIMMED}), as the ring left it, until the ground
     * is regained ({@link #offerOuter}, offered as soon as the neighbour is gone); then they are dropped.
     */
    List<int[]> splitNeighbours() {
        List<int[]> out = neighbours(false);
        return withLaid(out, laidWith);
    }

    /** The registered neighbours and the ones the ring was laid with that are not among them, sorted, each once. */
    static List<int[]> withLaid(List<int[]> live, List<int[]> laidWith) {
        List<int[]> out = new ArrayList<>(live);
        for (int[] o : laidWith) {
            boolean known = false;
            for (int[] n : live) known |= n[0] == o[0] && n[1] == o[1];
            if (!known) out.add(o.clone());
        }
        out.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        return out;
    }

    /**
     * The outer diagonals the campus has claimed (0.15.0, {@link CampusSplit}): a bit per world quadrant
     * ({@link CampusSplit#quadrant}) for each outer diagonal a module stands on (a recorded site) or a module job not
     * ended is for, queued or not. The registry keeps them ({@link #syncRegistry}), so the split of every campus gives
     * those sites wholly to this one.
     */
    int claims() {
        int[] f = front();
        int out = 0;
        for (int site : sites.keySet())
            if (OuterRing.diagSite(site)) out |= 1 << CampusSplit.siteQuadrant(site, f[0], f[1]);
        for (BuildJob j : jobs()) if (!BuildState.ended(j.state) && j.module() != null && OuterRing.diagSite(j.site))
            out |= 1 << CampusSplit.siteQuadrant(j.site, f[0], f[1]);
        return out;
    }

    /**
     * Whether the campus job that lays a family's ground has run here ({@link SiteFamily#ground}): since 0.15.0 the
     * outer establish once it ran to its end; the third ring's later. The outer families' sites are offered only then.
     * Since 0.16.0 the sky ring's ground ({@link SiteFamily#POD_FLOATS}) is the pod standing as it floats now
     * ({@link #floatingDatum}).
     */
    public boolean groundLaid(String job) {
        if (SiteFamily.POD_FLOATS.equals(job)) return floatingDatum() > 0;
        return job != null && laid.contains(job);
    }

    /**
     * The sky datum of the Echo Dome Generator's pod docked on the zenith while it stands formed as it floats
     * (0.16.0): the floor its sky modules' decks are level with ({@link DomeGeneratorShape#podDatum}); 0 while it is
     * moored, unformed or gone.
     */
    int floatingDatum() {
        for (TileModule m : nexus.docked()) {
            if (m.slot() != SiteFamily.ZENITH_SITE || !m.formed()) continue;
            int datum = floatingPod(m, centre()[1]);
            if (datum > 0) return datum;
        }
        return 0;
    }

    /**
     * The doors of the pod a floating plan leaves open (0.16.0, {@link BuildJob.Form#doors}): a bit for each sky site
     * a module stands on (a recorded site) or a module job not ended is for, so a repair of the pod never glazes a door
     * that leads to one.
     */
    int openDoors() {
        int doors = 0;
        for (int site : sites.keySet()) if (SkyRing.platformSite(site)) doors |= SkyRing.bit(site);
        for (BuildJob j : jobs()) if (!BuildState.ended(j.state) && j.module() != null && SkyRing.platformSite(j.site))
            doors |= SkyRing.bit(j.site);
        return doors;
    }

    /**
     * The radius the campus registry keeps for this campus (0.15.0): the outer disc's once the outer establish has
     * begun (it touched the ground) or ran, else the inner disc's. Another campus's jobs keep out of the outer part
     * only where the split gives it to this one ({@link CampusSplit#owns}).
     */
    int registryRadius() {
        if (groundLaid(BuildPlan.OUTER)) return OuterRing.OUTER_R;
        for (BuildJob j : jobs())
            if (j.laysOuter() && j.touched && !BuildState.ended(j.state)) return OuterRing.OUTER_R;
        return CampusPlan.GRADE_R;
    }

    /**
     * Writes the campus into the registry again when the radius it keeps or the outer diagonals it claims changed (or
     * were never written).
     */
    private void syncRegistry() {
        World w = world();
        // only a server world has a registry (a bare test world has no provider either)
        if (w == null || w.isRemote || w.provider == null || !active) return;
        int want = registryRadius(), claims = claims();
        if (want == registeredRadius && claims == registeredClaims) return;
        int[] c = centre();
        if (want != registeredRadius) CampusRegistry.add(world(), c[0], c[1], c[2], want);
        CampusRegistry.setClaims(world(), c[0], c[1], c[2], claims);
        registeredRadius = want;
        registeredClaims = claims;
    }

    /**
     * How much of the outer ring the neighbouring campuses take from the current outer establish, {lost, all, takers}
     * in columns and campuses ({@link BuildPlan#outerTrimmed}); null when the job is not the outer establish. Worked
     * out once per job and neighbours (the 建造 page asks every half second).
     */
    public int[] outerTrim() {
        // a patch (0.15.0) lays a few boxes again: what the ring left to the neighbours is the first job's to say
        if (job == null || !job.laysOuter() || job.patches()) return null;
        StringBuilder sig = new StringBuilder().append(System.identityHashCode(job));
        for (int[] n : job.neighbours) sig.append('|')
            .append(Arrays.toString(n));
        if (outerTrim == null || !sig.toString()
            .equals(outerTrimFor)) {
            int[] c = centre(), f = front();
            outerTrim = BuildPlan.outerTrimmed(plan(), c[0], c[2], f[0], f[1], job.neighbours);
            outerTrimFor = sig.toString();
        }
        return outerTrim.clone();
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
        skin.tick(now);
        if (now % 100 == 50) takeOverflow();
        if (!active) {
            legacyHint(now);
            flush(now);
            return;
        }
        creditIntake();
        if (resumeCheckAt >= 0 && now >= resumeCheckAt) afterResume();
        if (now % 20 == 0) watchStructures(now);
        if (now % 100 == 0) {
            syncRegistry();
            autoJobs(now);
            watchModules();
            watchNeighbours();
        }
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
            // the radius it keeps (0.15.0): the outer disc's once the outer ring was begun or laid
            int radius = registryRadius();
            if (CampusRegistry.radiusOf(w, c[0], c[1], c[2]) != radius) CampusRegistry.add(w, c[0], c[1], c[2], radius);
            registeredRadius = radius;
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

    /**
     * Takes back into the recycle store what a broken core of the team could not carry ({@link RecycleOverflow}), as
     * far as the store has room, and tells the members.
     */
    private void takeOverflow() {
        if (recycle.full()) return;
        int n = RecycleOverflow.drainInto(team(), recycle);
        if (n > 0) tell(
            new ChatComponentTranslation(
                P + "overflow_back",
                String.valueOf(n),
                nexus.xCoord + ", " + nexus.yCoord + ", " + nexus.zCoord));
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
     * Offers a module job for every module kind the campus's look offers ({@link #look}, {@link CityOffer}): its
     * research done, its phase reached, room for its height, turned on, not cancelled lately and a site of its free; on
     * the first free site of its candidates, in PROJECTING once surveyed, waiting for a member's 开始. There is no ring
     * room to wait for since 0.14.1: every building the nexus has researched is offered as soon as a site of its is
     * free. A kind kept off its last free site by nothing but a docked 0.9.2 hall gets the hall a retire job
     * ({@link #offerRetire}).
     */
    private void autoJobs(long now) {
        // the docked modules are known only once the nexus has looked for them since it loaded or formed
        if (!nexus.formed() || !nexus.dockKnown()) return;
        offerRefresh();
        offerGrowth(now);
        offerDeepen(now);
        offerRelease(now);
        floatPendingDome();
        offerOuter(now);
        withdraw(staleOuterOffers());
        Set<Integer> halls = new LinkedHashSet<>();
        for (CityOffer.Offer o : look(now)) {
            if (o.hall >= 0 && CityOffer.LEGACY_HALL.equals(o.reason)) halls.add(o.hall);
            ModuleSpec spec = o.offered() ? ModuleSpecs.get(o.kind.key) : null;
            if (spec == null) continue;
            BuildJob j = BuildJob.module(spec, o.site);
            // a pod offered once it may float from the start is built floating, never on legs to release (0.16.0), and
            // leaves the doors to the sky modules open
            if (floatsFresh(spec.key, nexus.phase(), nexus.has(Research.SKY_RING)))
                j.floatAt(freshPodDatum(), openDoors());
            // a sky module (0.16.0) is laid level with the floor of the pod that floats
            if (spec.family == SiteFamily.SKY) {
                int deck = floatingDatum();
                if (deck > 0) j.deckAt(deck);
            }
            enqueue(j);
            // the site as every window names it (0.14.0): "4 号位" by number, but 大门, 天顶, 根位 by name
            tell(
                new ChatComponentTranslation(
                    P + "unlocked",
                    new ChatComponentTranslation("fluxecho.module." + spec.key),
                    siteLabel(o.site)));
        }
        withdraw(staleRetires(jobs(), halls, this::hallStandsIn));
        for (int slot : halls) offerRetire(slot, now);
    }

    /**
     * The retire jobs that outlived their hall (0.14.1): not begun ({@link #yields}: never started, nothing touched,
     * nothing in flight), their slot no longer among those a docked 0.9.2 hall blocks a building's last site on
     * ({@code halls}), and no library hall left, formed or not, with its centre in their box ({@code hallInBox}). A
     * member who broke the hall by hand gets the module offered on its site at once, and the job must not then clear
     * the old box (it takes everything unguarded there, whatever {@code buildClearAll} says) and lay forum tiles the
     * module's clearing takes away again. A job whose hall still stands, even unformed or undocked, is kept: it is then
     * the one thing that can clear the site.
     */
    static List<BuildJob> staleRetires(List<BuildJob> jobs, Set<Integer> halls, Predicate<int[]> hallInBox) {
        List<BuildJob> out = new ArrayList<>();
        for (BuildJob j : jobs) {
            if (!j.retires() || !yields(j) || halls.contains(BuildPlan.retireSlot(j.planKey()))) continue;
            int[] box = j.retireBox();
            if (box == null || !hallInBox.test(box)) out.add(j);
        }
        return out;
    }

    /**
     * Whether a library hall, formed or not, has the centre of its foundation in the box's columns, or part of the box
     * lies in a chunk that is not loaded (a hall may stand there unseen).
     */
    private boolean hallStandsIn(int[] box) {
        World w = world();
        if (w == null) return true;
        int y = Math.max(0, Math.min(255, box[4]));
        for (int x : new int[] { box[0], box[2] })
            for (int z : new int[] { box[1], box[3] }) if (!w.blockExists(x, y, z)) return true;
        for (TileModule m : NexusRegistry.modules(w.provider.dimensionId)) {
            if (m.isInvalid() || m.getWorldObj() != w || !(m instanceof com.fluxecho.library.TileLibrary)) continue;
            if (inColumns(box, m.centre())) return true;
        }
        return false;
    }

    /** Takes jobs that never began out of the queue, the current one too, without declining them. */
    private void withdraw(List<BuildJob> gone) {
        if (gone.isEmpty()) return;
        queue.removeAll(gone);
        if (gone.contains(job)) nextJob();
        needDirty = true;
        stateChanged();
        syncRegistry();
    }

    /**
     * The buildings offered on outer diagonals, not begun, whose site a neighbour's claim now holds a cell of (0.15.0):
     * two campuses that offered buildings on overlapping sites in the same look both claimed them, and the split
     * gives the pair to the first by its centre; the other's job is taken back, and its kind is offered on another
     * site at the next look.
     */
    private List<BuildJob> staleOuterOffers() {
        List<BuildJob> out = new ArrayList<>();
        List<int[]> near = null;
        int own = 0;
        int[] c = centre(), f = front();
        for (BuildJob j : jobs()) {
            if (j.module() == null || j.repair() || !OuterRing.diagSite(j.site) || !yields(j)) continue;
            if (near == null) {
                near = splitNeighbours();
                own = claims();
            }
            if (near.isEmpty()) return out;
            int q = CampusSplit.siteQuadrant(j.site, f[0], f[1]);
            for (int[] cell : CampusSplit.siteCells(c[0], c[2], q)) {
                if (CampusSplit.ours(cell[0], cell[1], c[0], c[2], own, near)) continue;
                out.add(j);
                break;
            }
        }
        return out;
    }

    /** A site as every window names it: "4 号位" by number, 大门, 天顶 and 根位 by name. */
    private static IChatComponent siteLabel(int site) {
        return new ChatComponentTranslation("fluxecho." + SiteFamily.labelKey(site), String.valueOf(site));
    }

    /**
     * The campus's look at every module kind (0.14.1, {@link CityOffer#look}), in registration order: the kinds that
     * stand or have a job left out, the others with where they would go and why they are not offered when they are
     * not. Both the offers ({@link #autoJobs}) and the module list ({@link #modules}) come from it.
     */
    private List<CityOffer.Offer> look(long now) {
        int y0 = centre()[1];
        List<CityOffer.Kind> kinds = new ArrayList<>();
        Set<String> standing = new HashSet<>();
        for (ModuleSpec spec : ModuleSpecs.all()) {
            kinds.add(spec.kind(y0));
            if (exists(spec)) standing.add(spec.key);
        }
        List<BuildJob> all = jobs();
        return CityOffer.look(
            kinds,
            nexus.phase(),
            nexus::has,
            standing,
            new SiteView(),
            declined,
            now,
            this::groundLaid,
            g -> groundWaits(g, all));
    }

    /**
     * Whether the job that lays a family's ground waits for the jobs before it (0.15.0,
     * {@link CityOffer#GROUND_WAITS}): the outer establish is not queued, and a campus job or a retire job of a 0.9.2
     * hall still to run holds its offer back ({@link #outerWaits}), so the module list names those rather than a job
     * that is not there.
     */
    static boolean groundWaits(String ground, List<BuildJob> jobs) {
        if (!BuildPlan.OUTER.equals(ground)) return false;
        for (BuildJob j : jobs) if (j.laysOuter() && !BuildState.ended(j.state)) return false;
        return outerWaits(jobs);
    }

    /**
     * Offers the outer establish (0.15.0, CITY.md §2.3, {@link BuildJob#outer}) once the nexus has grown to phase III
     * and its teams have the outer ring's research, while its ground is not laid: a job that waits for 开始 like any
     * other, made with the keep-out boxes of the modules standing round the nexus out to the outer disc and the
     * centres of the neighbouring campuses it shares the ground with. Not while it or another campus job (the
     * establish, the forum, a refresh, a repair of the nexus) is still to run, so the forum's grading is never offered
     * again over the outer ring's streets; not while a retire job of a 0.9.2 hall is still to run, so the streets the
     * hall stands on are laid by the ring rather than left round it; nor for a day after a member cancelled it.
     * <p>
     * Once the ring is laid, a neighbour it was laid with that is gone from the registry (its core broken) left its
     * side of the split: the same job is offered again to regain that ground only ({@link BuildJob#outerRegain}),
     * and when nothing came back the campus just forgets the neighbour. Failing that, a module on no site of the
     * campus that the ring was laid round and that has gone since (a 0.9.2 hall broken by hand) left a gap in the
     * streets: the same job is offered again to lay the ring in its box only ({@link #offerPatch}).
     */
    private void offerOuter(long now) {
        if (nexus.phase() < SiteFamily.OUTER_DIAG.opens || !nexus.has(Research.OUTER_RING)) return;
        if (outerWaits(jobs())) return;
        Long until = declined.get(BuildPlan.OUTER);
        if (until != null && now < until) return;
        if (groundLaid(BuildPlan.OUTER)) {
            if (!offerRegain()) offerPatch();
            return;
        }
        keepOutAt = Long.MIN_VALUE;
        enqueue(BuildJob.outer(outerKeepOut(), neighbours(true)));
        tell(new ChatComponentTranslation(P + "outer_offered"));
    }

    /**
     * Whether the outer establish waits for the jobs queued now (0.15.0): one of its own or another campus job (the
     * establish, the forum, a refresh, a repair of the nexus) still to run, or a retire job of a 0.9.2 hall still to
     * run (the ring then lays the streets the hall stood on rather than keeping out of its box for good).
     */
    static boolean outerWaits(List<BuildJob> jobs) {
        for (BuildJob j : jobs)
            if (!BuildState.ended(j.state) && (j.laysOuter() || j.campusJob() || j.retires())) return true;
        return false;
    }

    /**
     * The outer ring is laid and a neighbour it was laid with is gone (0.15.0): offers the job that regains the ground
     * that neighbour had ({@link BuildPlan#outer} with {@code regainFrom}), or, when it gives nothing back, forgets
     * it. Returns whether it offered the job.
     */
    private boolean offerRegain() {
        if (laidWith.isEmpty()) return false;
        List<int[]> now = neighbours(true);
        boolean gone = false;
        for (int[] o : laidWith) {
            boolean still = false;
            for (int[] n : now) still |= n[0] == o[0] && n[1] == o[1];
            gone |= !still;
        }
        if (!gone) return false;
        int[] c = centre(), f = front();
        if (BuildPlan.outerRegain(plan(), c[0], c[2], f[0], f[1], laidWith, now) == 0) {
            laidWith.clear();
            for (int[] n : now) laidWith.add(n.clone());
            dirty();
            return false;
        }
        keepOutAt = Long.MIN_VALUE;
        enqueue(BuildJob.outerRegain(outerKeepOut(), now, laidWith));
        tell(new ChatComponentTranslation(P + "outer_regain_offered"));
        return true;
    }

    /**
     * The outer ring is laid and a module on no site of the campus that it was laid round is gone (0.15.0,
     * {@link #laidBoxes}: a 0.9.2 hall broken by hand, whose retire job was then withdrawn, a stray core taken away,
     * another campus's building gone): offers the job that lays the ring in the boxes nothing on no site holds now
     * ({@link BuildJob#outerPatch}), keeping out of every module standing and the ground of every module job, so the
     * streets the module stood on are laid rather than left a gap for good; when the split and the keep-out leave
     * nothing there to lay, it forgets the boxes. Only boxes wholly in loaded chunks count (a module there may stand
     * unseen). Returns whether it offered the job.
     */
    private boolean offerPatch() {
        if (laidBoxes.isEmpty()) return false;
        World w = world();
        if (w == null) return false;
        refreshKeepOut();
        int y = centre()[1];
        List<int[]> gone = vacated(laidBoxes, liveOuterStrays, b -> boxLoaded(w, b, y));
        if (gone.isEmpty()) return false;
        List<int[]> keep = patchKeepOut(), near = neighbours(true);
        int[] c = centre(), f = front();
        BuildPlan.Plan p = BuildPlan.outer(plan(), c[0], c[1], c[2], f[0], f[1], keep, near, null, gone);
        if (p.clear.isEmpty() && p.steps.isEmpty()) {
            forgetBoxes(laidBoxes, gone);
            dirty();
            return false;
        }
        enqueue(BuildJob.outerPatch(keep, near, gone));
        tell(new ChatComponentTranslation(P + "outer_patch_offered"));
        return true;
    }

    /**
     * The boxes of {@code laid} that no module on no site holds now (0.15.0, {@link #offerPatch}): none of
     * {@code strays} (the boxes the modules on no recorded site give now) overlaps it, and {@code loaded} says it lies
     * wholly in loaded chunks.
     */
    static List<int[]> vacated(List<int[]> laid, List<int[]> strays, Predicate<int[]> loaded) {
        List<int[]> out = new ArrayList<>();
        for (int[] b : laid) {
            boolean held = false;
            for (int[] k : strays) held |= CityOffer.overlap(b, k);
            if (!held && loaded.test(b)) out.add(b.clone());
        }
        return out;
    }

    /**
     * The boxes of an outer establish's keep-out that the modules on no recorded site gave (0.15.0,
     * {@link #laidBoxes}): the whole-column ones that are none of the recorded sites' {@code recorded}.
     */
    static List<int[]> laidRound(List<int[]> keepOut, List<int[]> recorded) {
        List<int[]> out = new ArrayList<>();
        for (int[] b : keepOut) {
            if (b.length != 4) continue;
            boolean site = false;
            for (int[] r : recorded) site |= Arrays.equals(b, r);
            if (!site) out.add(b.clone());
        }
        return out;
    }

    /** Takes the boxes equal to one of {@code gone} out of {@code boxes}. */
    static void forgetBoxes(List<int[]> boxes, List<int[]> gone) {
        boxes.removeIf(b -> {
            for (int[] g : gone) if (Arrays.equals(b, g)) return true;
            return false;
        });
    }

    /** Whether every chunk the box's columns {x0, z0, x1, z1} lie in is loaded. */
    private static boolean boxLoaded(World w, int[] b, int y) {
        int x0 = Math.min(b[0], b[2]), x1 = Math.max(b[0], b[2]), z0 = Math.min(b[1], b[3]), z1 = Math.max(b[1], b[3]);
        int yy = Math.max(0, Math.min(255, y));
        for (int x = x0;; x = Math.min(x1, x + 16)) {
            for (int z = z0;; z = Math.min(z1, z + 16)) {
                if (!w.blockExists(x, yy, z)) return false;
                if (z == z1) break;
            }
            if (x == x1) break;
        }
        return true;
    }

    /**
     * The keep-out of an outer establish that patches the ring (0.15.0, {@link #offerPatch}): the outer establish's
     * ({@link #outerKeepOut}), and the ground of every module job still to run, whose module stands on no recorded
     * site yet, as the outer establish keeps out of it once the module stands ({@link ModuleSpec#outerKeepOut} and
     * the columns of {@link ModuleSpec#outerProtect}: a hall's exact ground, so the streets beside it are laid).
     */
    private List<int[]> patchKeepOut() {
        List<int[]> keep = outerKeepOut();
        int[] c = centre(), f = front();
        for (BuildJob j : jobs()) {
            ModuleSpec spec = BuildState.ended(j.state) || j.retires() ? null : ModuleSpecs.get(j.module());
            if (spec == null || !spec.hasSite(j.site)) continue;
            keep.addAll(spec.outerKeepOut(plan(), j.site, c[0], c[1], c[2], f[0], f[1]));
            for (int[] p : spec.outerProtect(plan(), j.site, c[0], c[1], c[2], f[0], f[1]))
                keep.add(new int[] { p[0], p[2], p[3], p[5] });
        }
        return keep;
    }

    /**
     * What stands on the campus's sites now ({@link CityOffer.Sites}), seen once for one look: the recorded sites and
     * the jobs (each takes its site), the keep-out boxes of the modules on the recorded sites, and every loaded module
     * of the dimension (a stray one near the nexus keeps its keep-out box clear; the centre of any lies on no free
     * site), each docked 0.9.2 library hall marked with its ring slot, so that a site it alone blocks can be told
     * apart.
     */
    private final class SiteView implements CityOffer.Sites {

        final int[] c = centre(), f = front();
        final List<int[]> recorded = new ArrayList<>();
        final List<CityOffer.Stray> strays = new ArrayList<>();
        final List<TileModule> modules = new ArrayList<>();
        /**
         * The neighbouring campuses' centres with their claims, and the campus's own claims (0.15.0), looked up the
         * first time an outer site is asked about.
         */
        private List<int[]> near;
        private int claimed;
        /** What {@link #lost} and {@link #contested} worked out for an outer site in this look, by site. */
        private final Map<Integer, Integer> lostAt = new HashMap<>(), contestedAt = new HashMap<>();

        SiteView() {
            for (int site : sites.keySet()) recorded.addAll(siteKeepOut(site));
            World w = world();
            // out to the outer disc (0.15.0): a module standing there keeps its box clear of the outer sites too
            int reach = OUTER_REACH;
            if (w != null) for (TileModule m : NexusRegistry.modules(w.provider.dimensionId)) {
                if (m.isInvalid() || m.getWorldObj() != w) continue;
                modules.add(m);
                // a sky module (0.16.0) floats over the halls and the gate: its centre is on no ground site
                if (floatsOverGround(m)) continue;
                int[] mc = m.centre();
                boolean near = Math.max(Math.abs(mc[0] - c[0]), Math.abs(mc[2] - c[2])) <= reach;
                if (!near || onRecordedSite(m)) {
                    strays.add(new CityOffer.Stray(null, mc[0], mc[2], legacyHallSlot(m)));
                    continue;
                }
                // another campus's outer diagonal building keeps its whole site clear of ours (0.15.0)
                for (int[] keep : strayBoxes(m)) strays.add(new CityOffer.Stray(keep, mc[0], mc[2], legacyHallSlot(m)));
            }
        }

        @Override
        public int blocker(CityOffer.Kind kind, int site) {
            if (sites.containsKey(site) || jobOnSite(site) != null) return CityOffer.OTHER;
            ModuleSpec spec = ModuleSpecs.get(kind.key);
            if (spec == null) return CityOffer.OTHER;
            if (spec.axis() || spec.family == SiteFamily.SKY) {
                // an axis site (0.12.0), or a sky site (0.16.0), is free when no loaded module stands on its
                // controller cell (a sky module's at either deck it may stand at): the keep-out boxes and the module
                // centres are about the ground round the nexus, which the zenith only touches with its legs and the
                // sky ring floats over
                int[] at = spec.controller(site, c[0], c[1], c[2], f[0], f[1]);
                int[] former = spec.family == SiteFamily.SKY ? spec.formerController(site, c[0], c[1], c[2], f[0], f[1])
                    : null;
                for (TileModule m : modules) {
                    if (m.xCoord == at[0] && m.yCoord == at[1] && m.zCoord == at[2]) return CityOffer.OTHER;
                    if (former != null && m.xCoord == former[0] && m.yCoord == former[1] && m.zCoord == former[2])
                        return CityOffer.OTHER;
                }
                return CityOffer.FREE;
            }
            // an outer diagonal (0.15.0) cell for cell: the corners of its square reach past its octagon to where a
            // neighbour's outer diagonal building may stand, its whole site kept clear, without the two sharing a cell
            if (spec.family == SiteFamily.OUTER_DIAG) return CityOffer.siteBlocker(
                CampusSplit.siteCells(c[0], c[2], CampusSplit.siteQuadrant(site, f[0], f[1])),
                recorded,
                strays);
            return CityOffer.blocker(spec.footprint(site, c[0], c[2], f[0], f[1], 1), recorded, strays);
        }

        /**
         * Whether the campus owns the whole of an outer site's ground (0.15.0): every cell of an outer diagonal's
         * apron and allée lies on this campus's side of the split with its neighbours ({@link #lost} is 0).
         */
        @Override
        public boolean whole(CityOffer.Kind kind, int site) {
            return lost(kind, site) == 0;
        }

        /**
         * How much of an outer diagonal's apron and allée the split gives to the neighbours (0.15.0,
         * {@link CampusSplit#siteLoss} against {@link #splitNeighbours} with the campus's own claims): 0 for a whole
         * site, the cells it would take from them for one the campus may still claim, -1 for one a neighbour holds
         * part of for good (its inner square, an outer diagonal it claimed). Every site of the inner city and the
         * axis is whole.
         */
        @Override
        public int lost(CityOffer.Kind kind, int site) {
            if (SiteFamily.of(site) != SiteFamily.OUTER_DIAG || near().isEmpty()) return 0;
            Integer known = lostAt.get(site);
            if (known != null) return known;
            int n = CampusSplit.siteLoss(CampusSplit.siteQuadrant(site, f[0], f[1]), c[0], c[2], claimed, near);
            lostAt.put(site, n);
            return n;
        }

        /**
         * How many of the neighbours' unclaimed outer diagonals an outer diagonal of this campus shares ground with
         * (0.15.0, {@link CampusSplit#contested}): the look takes a site that shares none first. 0 for every other
         * site.
         */
        @Override
        public int contested(CityOffer.Kind kind, int site) {
            if (SiteFamily.of(site) != SiteFamily.OUTER_DIAG || near().isEmpty()) return 0;
            Integer known = contestedAt.get(site);
            if (known != null) return known;
            int n = CampusSplit.contested(CampusSplit.siteQuadrant(site, f[0], f[1]), c[0], c[2], near);
            contestedAt.put(site, n);
            return n;
        }

        /** The neighbours and the campus's own claims, looked up the first time an outer site is asked about. */
        private List<int[]> near() {
            if (near == null) {
                near = splitNeighbours();
                claimed = claims();
            }
            return near;
        }
    }

    /**
     * The inner ring slot of a module that is a docked 0.9.2 library hall of this nexus (0.14.1): a formed Echo Library
     * standing as the 0.9.2 hall and docked here, which the campus may retire. -1 for any other module.
     */
    private int legacyHallSlot(TileModule m) {
        if (!(m instanceof com.fluxecho.library.TileLibrary l)
            || !com.fluxecho.library.TileLibrary.HALL.equals(l.shapeName())
            || !l.formed()
            || l.nexus() != nexus) return -1;
        return SiteFamily.ringSite(l.slot()) ? l.slot() : -1;
    }

    /**
     * Offers the retire job of the docked 0.9.2 hall on a ring slot (0.14.1, {@link BuildJob#retire}): the hall is all
     * that keeps a researched module off its last free site. Not while one is still to run for it, nor for a day after
     * a member cancelled it; the job waits for 开始 like any other.
     */
    private void offerRetire(int slot, long now) {
        String key = BuildPlan.retireKey(slot);
        for (BuildJob j : jobs()) if (key.equals(j.key) && !BuildState.ended(j.state)) return;
        Long until = declined.get(key);
        if (until != null && now < until) return;
        TileModule hall = null;
        for (TileModule m : nexus.docked()) if (legacyHallSlot(m) == slot) hall = m;
        if (hall == null) return;
        // once the outer ring is laid (0.15.0) the job also lays its floor in the box, which the ring kept out of
        boolean outer = groundLaid(BuildPlan.OUTER);
        enqueue(BuildJob.retire(slot, retireBox(hall), outer, outer ? neighbours(false) : null));
        tell(new ChatComponentTranslation(P + "retire_offered", siteLabel(slot)));
    }

    /**
     * The ground a retire job works for a 0.9.2 hall, {x0, z0, x1, z1, yFrom, yTo} ({@link BuildPlan#retireBox}): the
     * keep-out box the campus jobs left round the centre of its foundation and its bounds, from its lowest to its
     * highest cell, each side kept off the ground of the modules on the recorded sites and of the module jobs (their
     * keep-out and protected columns), so a neighbour built next to a hall that stands off its slot point keeps its
     * apron.
     */
    int[] retireBox(TileModule hall) {
        List<int[]> keep = new ArrayList<>();
        for (int site : sites.keySet()) {
            keep.addAll(siteKeepOut(site));
            // a sky module's boxes (0.16.0) are in the air over the halls: no ground of a hall's
            if (SiteFamily.of(site) == SiteFamily.SKY) continue;
            for (int[] p : siteProtect(site)) keep.add(new int[] { p[0], p[2], p[3], p[5] });
        }
        int[] c = centre(), f = front();
        for (BuildJob j : jobs()) {
            ModuleSpec spec = BuildState.ended(j.state) || j.retires() ? null : ModuleSpecs.get(j.module());
            if (spec == null || !spec.hasSite(j.site) || spec.family == SiteFamily.SKY) continue;
            keep.addAll(spec.keepOut(plan(), j.site, c[0], c[1], c[2], f[0], f[1]));
            for (int[] p : spec.protect(plan(), j.site, c[0], c[1], c[2], f[0], f[1]))
                keep.add(new int[] { p[0], p[2], p[3], p[5] });
        }
        return BuildPlan.retireBox(hall.centre(), hall.bounds(), keep);
    }

    /**
     * Before a retire job that has touched nothing starts (0.14.1): its box is taken again round its hall, if the hall
     * still docks here, as a campus job's keep-out is ({@link #freshKeepOut}); a neighbour may have been built next to
     * the hall since the job was offered.
     */
    private void freshRetireBox() {
        TileModule hall = null;
        for (TileModule m : nexus.docked()) if (legacyHallSlot(m) == job.site) hall = m;
        // the outer ring may have been laid since the job was offered (0.15.0): its floor in the box comes with it
        boolean outer = groundLaid(BuildPlan.OUTER);
        if (hall == null || !job.retargetRetire(retireBox(hall), outer, outer ? neighbours(false) : null)) return;
        builder.reset();
        needDirty = true;
        etaAt = Long.MIN_VALUE;
        stateChanged();
    }

    /**
     * Offers the growth of the nexus to its next phase (0.12.0, CITY.md §3) once its teams have that phase's research
     * and this version reaches it ({@link ResearchTree#nextGrowth}: {@code grow:2} on a nexus of phase I, and since
     * 0.15.0 {@code grow:3} on one of phase II): a {@code grow:<phase>} job that waits for 开始 like any other, queued
     * after the repairs. Not while one is still to run, nor for a day after a member cancelled it.
     */
    private void offerGrowth(long now) {
        int next = ResearchTree.nextGrowth(nexus.phase(), nexus::has);
        if (next == 0) return;
        for (BuildJob j : jobs()) if (j.grows() && !BuildState.ended(j.state)) return;
        Long until = declined.get(BuildPlan.growKey(next));
        if (until != null && now < until) return;
        enqueue(BuildJob.grow(next));
        tell(
            new ChatComponentTranslation(
                P + "grow_offered",
                new ChatComponentTranslation("fluxecho.nexus.phase." + next)));
    }

    // ---- the deep root (0.16.0)

    /**
     * Offers the deepen job (0.16.0, CITY.md §4.9, {@link BuildJob#deepen}) once the nexus has grown to phase IV, its
     * teams have the deep root's research and the root docked under it stands formed as a well
     * ({@link RootShape#deepenTo}: the form the room under that well leaves, a chamber or the roots in the well): a job
     * on the root's own site that waits for 开始 like any other. Not while a job is on the root's site (the deepen job
     * itself, a repair), nor for a day after a member cancelled it (the root's module key is declined, as for every
     * module job). One that never began is taken back once there is nothing to deepen (the well gone or come apart, or
     * the root a deep root already).
     */
    private void offerDeepen(long now) {
        List<BuildJob> pending = new ArrayList<>();
        for (BuildJob j : jobs()) if (j.deepens() && !BuildState.ended(j.state)) pending.add(j);
        String form = deepenForm();
        if (form == null) {
            List<BuildJob> gone = new ArrayList<>();
            for (BuildJob j : pending) if (yields(j)) gone.add(j);
            withdraw(gone);
            return;
        }
        if (!pending.isEmpty() || jobOnSite(SiteFamily.ROOT_SITE) != null) return;
        Long until = declined.get(RootShape.KEY);
        if (until != null && now < until) return;
        ModuleSpec spec = ModuleSpecs.get(RootShape.KEY);
        if (spec == null || !spec.enabled()) return;
        enqueue(BuildJob.deepen(spec, form));
        tell(new ChatComponentTranslation(P + "deepen_offered"));
    }

    /**
     * The deep root's form the root docked on its site would grow into now ({@link RootShape#deepenTo}), or null when
     * there is none to offer.
     */
    private String deepenForm() {
        TileModule root = null;
        for (TileModule m : nexus.docked())
            if (RootShape.KEY.equals(m.moduleKey()) && m.slot() == SiteFamily.ROOT_SITE) root = m;
        if (root == null) return null;
        return RootShape.deepenTo(
            nexus.phase(),
            nexus.has(Research.DEEP_ROOT),
            root.formed(),
            root.shapeName(),
            centre()[1],
            root.yCoord);
    }

    /**
     * The deep root's form a root's controller tile stands, or last stood, as (0.16.0), so its repair puts that form
     * back whole; null for any other tile, a root that is a well, and none at all.
     */
    static String deepRootOf(TileEntity te) {
        if (!(te instanceof TileModule m) || !RootShape.KEY.equals(m.moduleKey())) return null;
        return RootShape.deep(m.shapeName()) == null ? null : m.shapeName();
    }

    // ---- the pod floats (0.16.0)

    /**
     * Offers the release job of the Echo Dome Generator (0.16.0, CITY.md §3, {@link BuildJob#release}) once the
     * nexus's teams have the sky ring and the nexus has grown to phase IV, while the pod docked on the zenith stands
     * moored or any block of its legs is left ({@link #releaseWanted}: a leg broken at phase IV floats the pod at once
     * and leaves the other legs standing as plain pillars): a job at the pod's own datum that waits for 开始 like any
     * other. Not while one is still to run, nor for a day after a member cancelled it. One that never began is taken
     * back once there is nothing to release (the pod gone, or floating with no leg left).
     */
    private void offerRelease(long now) {
        List<BuildJob> pending = new ArrayList<>();
        for (BuildJob j : jobs()) if (j.releases() && !BuildState.ended(j.state)) pending.add(j);
        int datum = releaseDatum();
        if (datum == 0) {
            List<BuildJob> gone = new ArrayList<>();
            for (BuildJob j : pending) if (yields(j)) gone.add(j);
            withdraw(gone);
            return;
        }
        if (!pending.isEmpty()) return;
        Long until = declined.get(BuildPlan.RELEASE);
        if (until != null && now < until) return;
        enqueue(BuildJob.release(datum));
        tell(new ChatComponentTranslation(P + "release_offered"));
    }

    /**
     * The sky datum of the pod the release job would take the legs of now, or 0 when there is none to release
     * ({@link #releaseWanted}): the Echo Dome Generator docked on the zenith, its datum read from its controller
     * ({@link DomeGeneratorShape#podDatum}: a pod built by hand on the full legs over high ground has its legs at 40).
     */
    private int releaseDatum() {
        boolean skyRing = nexus.has(Research.SKY_RING);
        if (!skyRing || nexus.phase() < DomeGeneratorShape.FLOAT_PHASE) return 0;
        TileModule pod = null;
        for (TileModule m : nexus.docked())
            if (DomeGeneratorShape.KEY.equals(m.moduleKey()) && m.slot() == SiteFamily.ZENITH_SITE) pod = m;
        if (pod == null) return 0;
        int datum = DomeGeneratorShape.podDatum(pod.yCoord, centre()[1]);
        if (!DomeGeneratorShape.isDatum(datum)) return 0;
        boolean moored = DomeGeneratorShape.moored(pod.shapeName()) != null;
        int left = moored ? 0 : legsLeft(datum);
        return releaseWanted(skyRing, nexus.phase(), true, moored, left) ? datum : 0;
    }

    /**
     * Whether the release job is wanted (0.16.0): the teams have the sky ring, the nexus stands at phase IV or more,
     * the pod is docked on the zenith, and it stands moored or {@code legsLeft} blocks of its legs still stand.
     */
    static boolean releaseWanted(boolean skyRing, int phase, boolean docked, boolean moored, int legsLeft) {
        return skyRing && phase >= DomeGeneratorShape.FLOAT_PHASE && docked && (moored || legsLeft > 0);
    }

    /**
     * How many cells the release job at the sky datum would take down ({@link BuildPlan#release}) still hold a frame
     * pillar of ours, in loaded chunks: the legs a floating pod left standing.
     */
    private int legsLeft(int datum) {
        World w = world();
        if (w == null) return 0;
        int[] c = centre(), f = front();
        int pillar = Parts.frame(Parts.FR_PILLAR), n = 0;
        for (BuildPlan.Step s : BuildPlan.release(c[0], c[1], c[2], f[0], f[1], datum).steps) {
            if (!w.blockExists(s.x, s.y, s.z)) continue;
            if (PartBlocks.code(w.getBlock(s.x, s.y, s.z), w.getBlockMetadata(s.x, s.y, s.z)) == pillar) n++;
        }
        return n;
    }

    /**
     * Whether a new job of the module kind builds the pod floating (0.16.0, CITY.md §3): the Echo Dome Generator, once
     * the teams have the sky ring and the nexus has grown to phase IV, stands as it will stay, never on legs a release
     * job would take down again.
     */
    static boolean floatsFresh(String key, int phase, boolean skyRing) {
        return DomeGeneratorShape.KEY.equals(key) && skyRing && phase >= DomeGeneratorShape.FLOAT_PHASE;
    }

    /**
     * A dome job offered on legs before the pod could float, and never begun ({@link #yields}), is made a floating
     * one once it can ({@link #floatsFresh}, 0.16.0): it is surveyed again with the pod alone in its projection.
     */
    private void floatPendingDome() {
        if (!floatsFresh(DomeGeneratorShape.KEY, nexus.phase(), nexus.has(Research.SKY_RING))) return;
        for (BuildJob j : jobs()) {
            if (j.repair() || j.floats() || !DomeGeneratorShape.KEY.equals(j.module()) || !yields(j)) continue;
            j.floatAt(freshPodDatum(), openDoors());
            j.restart(j.keepClear);
            if (j == job) {
                builder.reset();
                etaAt = Long.MIN_VALUE;
            }
            needDirty = true;
            stateChanged();
        }
    }

    /**
     * The sky datum a fresh floating pod is laid at (0.16.0, {@link SkyRing#podDatumFor}): the deck of a sky module
     * already recorded on its platform site (from its recorded controller, whether or not its chunk is loaded), else
     * of a sky job not over, else the datum the base layer gives. A pod built by hand on the full legs over high ground
     * floated at 40 and its sky modules were laid there; a pod raised in its place meets their catwalks at 40, never
     * at the lower datum the base layer would give (where its doors and its clearing would miss them).
     */
    int freshPodDatum() {
        int y0 = centre()[1];
        List<Integer> decks = new ArrayList<>();
        for (Map.Entry<Integer, int[]> e : new TreeMap<>(sites).entrySet())
            if (SkyRing.platformSite(e.getKey())) decks.add(DomeGeneratorShape.podDatum(e.getValue()[1], y0));
        for (BuildJob j : jobs()) if (!BuildState.ended(j.state) && j.module() != null && SkyRing.platformSite(j.site))
            decks.add(j.form().deck);
        int[] d = new int[decks.size()];
        for (int i = 0; i < d.length; i++) d[i] = decks.get(i);
        return SkyRing.podDatumFor(y0, d);
    }

    /**
     * The sky datum of an Echo Dome Generator that stands, or last stood, as floating (0.16.0), read from its
     * controller's height over the base layer {@code y0}; 0 for any other tile or module, a pod that stands moored, and
     * none at all.
     */
    static int floatingPod(TileEntity te, int y0) {
        if (!(te instanceof TileModule m) || !DomeGeneratorShape.KEY.equals(m.moduleKey())
            || !DomeGeneratorShape.FLOATING.equals(m.shapeName())) return 0;
        int datum = DomeGeneratorShape.podDatum(m.yCoord, y0);
        return DomeGeneratorShape.isDatum(datum) ? datum : 0;
    }

    /**
     * Whether a module of the kind already stands or is being built: one on a recorded hall site, one docked in the
     * shape the campus builds ({@link #campusShape}), or a job for it. Since 0.10.1 a docked 0.9.2 library hall does
     * not count, so the Echo Archive is offered next to it once the library is researched and a hall site is free.
     */
    private boolean exists(ModuleSpec spec) {
        for (TileModule m : nexus.docked()) if (spec.key.equals(m.moduleKey()) && campusShape(m)) return true;
        return builtSite(spec) >= 0 || jobFor(spec) != null;
    }

    /**
     * Whether a module stands in a shape the campus builds: any but the 0.9.2 library hall ({@code hall}); the city's
     * modules stand as {@code main}, the zenith as {@code moored} or {@code floating} (0.12.0).
     */
    private static boolean campusShape(TileModule m) {
        return !com.fluxecho.library.TileLibrary.HALL.equals(m.shapeName());
    }

    /**
     * The recorded site a module of the kind stands on, or -1. A site whose module is not known yet ("": its chunk was
     * not loaded since the site was recorded) only takes its own site (0.14.1): before, it counted as a module of every
     * kind of the site's family, so none of them was offered or listed anywhere else until the chunk loaded.
     */
    private int builtSite(ModuleSpec spec) {
        return builtSite(siteKinds, spec.key);
    }

    /** The recorded site whose module is of the kind ({@code kinds}: site → module key, "" when not known), or -1. */
    static int builtSite(Map<Integer, String> kinds, String key) {
        for (Map.Entry<Integer, String> e : new TreeMap<>(kinds).entrySet())
            if (key.equals(e.getValue())) return e.getKey();
        return -1;
    }

    /** The job (current or queued, not a repair, not over) that builds a module of the kind, or null. */
    private BuildJob jobFor(ModuleSpec spec) {
        for (BuildJob j : jobs()) {
            if (spec.key.equals(j.module()) && !j.repair() && !BuildState.ended(j.state)) return j;
        }
        return null;
    }

    // ---- the modules the nexus can build (the 建造 page's list, the masterplan's signs)

    /** Where a module the nexus can build stands. The ordinal is sent ({@code Md}): values are only ever appended. */
    public enum ModuleStatus {
        /** Its research is not done yet. */
        NEEDS_RESEARCH,
        /** Its job waits (projected, not started yet), or it will be offered at the next look (within 5 s). */
        QUEUED,
        /** Its job was started. */
        BUILDING,
        /** It stands on a hall site (or docked in its campus shape). */
        BUILT,
        /**
         * Researched but not offered now, and why ({@link ModuleState#reason}, 0.12.0 and 0.14.1): the nexus's phase,
         * the campus's height or depth, turned off in the config, cancelled lately, a docked 0.9.2 hall in the way, or
         * something else on every site it may take ({@link CityOffer}); no reason while the nexus does not stand.
         */
        NO_ROOM,
        /**
         * Researched, a site free, but the inner ring full (0.11.0 to 0.14.0). Since 0.14.1 there is no ring room to
         * wait for and nothing is given this status; it stays for its ordinal, and a client still names it.
         */
        RING_FULL
    }

    /**
     * One module kind and where it stands: its module key ({@code fluxecho.module.<key>}), its research id
     * ({@code fluxecho.research.node.<research>}), its status, and the hall site it stands on, is built on, or would
     * be built on next (-1 when none is free). A NO_ROOM status may say why (0.12.0, {@link #reason}: a lang key under
     * {@code fluxecho.}, such as {@code build.module.why.phase} or {@code build.module.why.height}; empty otherwise).
     */
    public static final class ModuleState {

        public final String key, research;
        public final ModuleStatus status;
        public final int site;
        /** Why it has no room (a lang key under {@code fluxecho.}), or "" (0.12.0). */
        public final String reason;

        public ModuleState(String key, String research, ModuleStatus status, int site) {
            this(key, research, status, site, "");
        }

        public ModuleState(String key, String research, ModuleStatus status, int site, String reason) {
            this.key = key;
            this.research = research;
            this.status = status;
            this.site = site;
            this.reason = reason == null ? "" : reason;
        }

        /**
         * The list as the description packet and the build page carry it: {@code [{K, R, S, P, W}]} ({@code W} the
         * reason, only when there is one).
         */
        public static NBTTagList write(List<ModuleState> l) {
            NBTTagList out = new NBTTagList();
            for (ModuleState m : l) {
                NBTTagCompound c = new NBTTagCompound();
                c.setString("K", m.key);
                c.setString("R", m.research == null ? "" : m.research);
                c.setByte("S", (byte) m.status.ordinal());
                c.setByte("P", (byte) m.site);
                if (!m.reason.isEmpty()) c.setString("W", m.reason);
                out.appendTag(c);
            }
            return out;
        }

        /** The list from {@link #write}; an unknown status reads as NO_ROOM. */
        public static List<ModuleState> read(NBTTagList l) {
            List<ModuleState> out = new ArrayList<>();
            if (l == null) return out;
            ModuleStatus[] all = ModuleStatus.values();
            for (int i = 0; i < l.tagCount(); i++) {
                NBTTagCompound c = l.getCompoundTagAt(i);
                int s = c.getByte("S");
                out.add(
                    new ModuleState(
                        c.getString("K"),
                        c.getString("R"),
                        s >= 0 && s < all.length ? all[s] : ModuleStatus.NO_ROOM,
                        c.getByte("P"),
                        c.getString("W")));
            }
            return out;
        }

        /**
         * Whether the list records a pavilion built on the site (0.13.0): a {@link ModuleStatus#BUILT} state there
         * whose module is a pavilion's ({@link SiteFamily#PAVILION}). A module docked on a diagonal that the client
         * has not loaded is then the pavilion, not a 0.9.2 module on the old inner ring.
         */
        public static boolean pavilionBuilt(List<ModuleState> modules, int site) {
            return builtOf(modules, site, SiteFamily.PAVILION);
        }

        /**
         * Whether the list records the gatehouse built on the gate (0.14.0): a {@link ModuleStatus#BUILT} state on
         * site 0 whose module is of the gate family ({@link SiteFamily#GATE}). A module docked on site 0 that the
         * client has not loaded is then the gatehouse, not a 0.9.2 module on the old inner ring's slot 0.
         */
        public static boolean gateBuilt(List<ModuleState> modules, int site) {
            return builtOf(modules, site, SiteFamily.GATE);
        }

        private static boolean builtOf(List<ModuleState> modules, int site, SiteFamily family) {
            if (modules == null || SiteFamily.of(site) != family) return false;
            for (ModuleState m : modules) {
                if (m.status != ModuleStatus.BUILT || m.site != site) continue;
                ModuleSpec spec = ModuleSpecs.get(m.key);
                if (spec != null && spec.family == family) return true;
            }
            return false;
        }
    }

    /**
     * Every module kind the nexus can build ({@link ModuleSpecs}) and where it stands: built (on its site), being
     * built, waiting, needing its research, or researched and not offered now with the reason; with the site it is on
     * or would go to. Server side (it asks the research of the nexus's teams). The kinds with no module and no job are
     * the campus's look ({@link #look}), the same that offers them, so the list and the offers never disagree.
     */
    public List<ModuleState> modules() {
        List<ModuleState> out = new ArrayList<>();
        World w = world();
        if (w == null || w.isRemote) return out;
        Map<String, CityOffer.Offer> looked = new HashMap<>();
        for (CityOffer.Offer o : look(w.getTotalWorldTime())) looked.put(o.kind.key, o);
        for (ModuleSpec spec : ModuleSpecs.all()) out.add(moduleState(spec, looked.get(spec.key)));
        return out;
    }

    private ModuleState moduleState(ModuleSpec spec, CityOffer.Offer o) {
        int site = builtSite(spec);
        if (site >= 0) return new ModuleState(spec.key, spec.research, ModuleStatus.BUILT, site);
        for (TileModule m : nexus.docked()) {
            if (spec.key.equals(m.moduleKey()) && campusShape(m))
                return new ModuleState(spec.key, spec.research, ModuleStatus.BUILT, m.slot());
        }
        BuildJob j = jobFor(spec);
        if (j != null) {
            boolean started = j == job && (j.started || BuildState.consented(j.state));
            return new ModuleState(
                spec.key,
                spec.research,
                started ? ModuleStatus.BUILDING : ModuleStatus.QUEUED,
                j.site);
        }
        if (o == null) return new ModuleState(spec.key, spec.research, ModuleStatus.NO_ROOM, -1);
        if (!o.researched) return new ModuleState(spec.key, spec.research, ModuleStatus.NEEDS_RESEARCH, o.site);
        if (o.reason != null) return new ModuleState(spec.key, spec.research, ModuleStatus.NO_ROOM, o.site, o.reason);
        return new ModuleState(spec.key, spec.research, offerStatus(active && nexus.formed()), o.site);
    }

    /**
     * Where a researched module the campus's look offers stands while it has no job: offered at the next look
     * (QUEUED), or NO_ROOM while the campus does not offer at all (a nexus that does not stand). Never
     * {@link ModuleStatus#RING_FULL} since 0.14.1: there is no ring room to wait for.
     */
    static ModuleStatus offerStatus(boolean offering) {
        return offering ? ModuleStatus.QUEUED : ModuleStatus.NO_ROOM;
    }

    /** Sends the description again when the module list changed (a research done, a site taken). */
    private void watchModules() {
        long h = 17;
        for (ModuleState m : modules()) {
            h = h * 31 + m.key.hashCode();
            h = h * 31 + m.status.ordinal();
            h = h * 31 + m.site;
        }
        if (h != modulesHash) {
            modulesHash = h;
            stateChanged();
        }
    }

    // ---- the refresh of an older campus

    /**
     * Offers the 整修园区 job (0.10.1) to a campus whose grading last ran under an older grading version
     * ({@link BuildJob#GRADE_V}; a 0.10.0 campus: its floor kept leaves and logs, its wells were sunk): the forum plan
     * again, with the boxes of the modules round it kept out. Not while a campus job is still to run (so never twice);
     * the job waits for 开始 like any other. The version is noted only once the grading ran ({@link #graded}) or a
     * member cancelled the job ({@link #cancel}): a core broken while the refresh waits or runs carries the old
     * version, and the campus put back is offered it again.
     */
    void offerRefresh() {
        if (gradedVersion >= BuildJob.GRADE_V) return;
        for (BuildJob j : jobs()) if (j.campusJob() && !BuildState.ended(j.state)) return;
        keepOutAt = Long.MIN_VALUE;
        enqueue(BuildJob.refresh(keepOut()));
        tell(new ChatComponentTranslation(P + "refresh_offered"));
        dirty();
    }

    /**
     * The builder finished the clearing of a job: the grading of a campus job that clears in full (not a repair, not
     * started over without clearing) ran under the current plan version.
     */
    void graded(BuildJob j) {
        if (!j.campusJob() || j.repair() || j.keepClear || j.version != BuildJob.V) return;
        if (gradedVersion >= BuildJob.GRADE_V) return;
        gradedVersion = BuildJob.GRADE_V;
        dirty();
    }

    /**
     * The job (current or queued, not over) on a site, or null. A retire job (0.14.1) names its hall's ring slot as its
     * site but takes none: the module waiting for that site still says the hall is in its way. Nor does the release
     * job (0.16.0) take the zenith: the pod it works on stands there, and is repaired when it comes apart.
     */
    private BuildJob jobOnSite(int site) {
        for (BuildJob j : jobs())
            if (j.site == site && !j.retires() && !j.releases() && !BuildState.ended(j.state)) return j;
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
        // a building offered on an outer diagonal claims it at once (0.15.0), before the next campus looks
        if (j.module() != null && OuterRing.diagSite(j.site)) syncRegistry();
    }

    /**
     * Where a new job goes; returns the current job afterwards. Without a current job it becomes the current one. A
     * repair or a refresh ({@link BuildJob#rank} 2) goes ahead of every queued job (none of those was started: 开始
     * acts on the current job only), and takes the current job's place when that one ranks lower and no member has
     * started it and it has touched nothing (a module job waiting for its core must not hold a repair up for good);
     * the job that gives way goes back to the head of the queue and is surveyed again when its turn comes. A growth of
     * the nexus (rank 1, 0.12.0) does the same after the repairs and refreshes. Any other job waits at the end of the
     * queue.
     */
    static BuildJob admit(BuildJob current, List<BuildJob> queue, BuildJob j) {
        if (current == null) return j;
        int rank = j.rank();
        if (rank == 0) {
            queue.add(j);
            return current;
        }
        if (current.rank() < rank && yields(current)) {
            queue.add(0, current);
            return j;
        }
        int at = 0;
        while (at < queue.size() && queue.get(at)
            .rank() >= rank) at++;
        queue.add(at, j);
        return current;
    }

    /**
     * 换一个 (0.14.1): the current job, not started yet, goes to the end of the queue and the next one comes up, so a
     * member can reach any queued job without cancelling the ones before it (a cancelled module is not offered again
     * for a day). Only while the job could give way to a repair ({@link #yields}: never started, nothing touched,
     * nothing in flight); it is not declined, and it is surveyed again when its turn comes back.
     */
    public String defer(EntityPlayer p) {
        if (job == null || BuildState.ended(job.state)) return NO_JOB;
        if (!nexus.member(p)) return NOT_MEMBER;
        if (queue.isEmpty()) return P + "defer_alone";
        BuildJob next = defer(job, queue);
        if (next == job) return P + "defer_locked";
        job = next;
        if (!job.started && !BuildState.ended(job.state)) job.resurvey();
        builder.reset();
        needDirty = true;
        etaAt = Long.MIN_VALUE;
        stateChanged();
        return P + "deferred";
    }

    /** Whether 换一个 would bring up another job now ({@link #defer}; for the GUI's button). */
    public boolean canDefer() {
        return job != null && !queue.isEmpty() && yields(job);
    }

    /**
     * Where 换一个 leaves the jobs; returns the current job afterwards. A current job that may give way
     * ({@link #yields}) goes to the end of the queue and the queue's first job takes its place; otherwise (or with
     * nothing queued) nothing moves.
     */
    static BuildJob defer(BuildJob current, List<BuildJob> queue) {
        if (current == null || queue.isEmpty() || !yields(current)) return current;
        BuildJob next = queue.remove(0);
        queue.add(current);
        return next;
    }

    /** Whether a job may give way to a repair: not over, never started, nothing touched, nothing in flight. */
    static boolean yields(BuildJob j) {
        return !BuildState.ended(j.state) && !j.started
            && !j.touched
            && j.flights.isEmpty()
            && (j.state == BuildState.State.SURVEY || j.state == BuildState.State.PROJECTING);
    }

    private void nextJob() {
        if (instantJob != null && instantJob == job) instantJob = null;
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
        long now = w == null ? 0 : w.getTotalWorldTime();
        if (now - keepOutAt < 100 && keepOutAt != Long.MIN_VALUE) return;
        keepOutAt = now;
        List<int[]> out = new ArrayList<>(), outer = new ArrayList<>(), strays = new ArrayList<>();
        int[] c = centre();
        int reach = CampusPlan.GRADE_R + 24;
        // the loaded modules that are not on a recorded site (0.9.2 halls), then the recorded sites, loaded or not;
        // the outer establish's (0.15.0) looks for them out to the outer disc
        if (w != null) for (TileModule m : NexusRegistry.modules(w.provider.dimensionId)) {
            if (m.isInvalid() || m.getWorldObj() != w || onRecordedSite(m)) continue;
            int[] mc = m.centre();
            int d = Math.max(Math.abs(mc[0] - c[0]), Math.abs(mc[2] - c[2]));
            if (d > OUTER_REACH) continue;
            // another campus's outer diagonal building keeps its whole site (0.15.0), any other the box round it
            for (int[] box : strayBoxes(m)) {
                if (d <= reach) out.add(box);
                outer.add(box.clone());
                strays.add(box.clone());
            }
        }
        for (Map.Entry<Integer, int[]> e : sites.entrySet()) {
            out.addAll(siteKeepOut(e.getKey()));
            // the outer establish keeps out of a hall's exact ground (0.15.0), so the streets beside it are paved
            outer.addAll(siteOuterKeepOut(e.getKey()));
        }
        liveKeepOut = out;
        liveOuterKeepOut = outer;
        liveOuterStrays = strays;
    }

    /**
     * The keep-out boxes {x0, z0, x1, z1} the campus jobs leave round a module standing on no site this campus
     * recorded: the box round the centre of its foundation ({@link BuildPlan#moduleKeepOut}), and for an outer diagonal
     * building (0.15.0, another campus's) its whole site as its own campus keeps it
     * ({@link CampusSplit#strayDiagBoxes}: its apron, annex and allée), so an outer establish here never grades round
     * it even where the split moved after it was built.
     */
    private List<int[]> strayBoxes(TileModule m) {
        // a sky module (0.16.0) takes nothing of the ground under it
        if (floatsOverGround(m)) return Collections.emptyList();
        int[] mc = m.centre();
        ModuleSpec spec = ModuleSpecs.get(m.moduleKey());
        if (spec == null || spec.family != SiteFamily.OUTER_DIAG)
            return Collections.singletonList(BuildPlan.moduleKeepOut(mc));
        List<int[]> centres = new ArrayList<>();
        World w = world();
        if (w != null && !w.isRemote && w.provider != null)
            for (int[] o : CampusRegistry.centres(w)) centres.add(new int[] { o[0], o[2] });
        return CampusSplit.strayDiagBoxes(mc[0], mc[2], centres);
    }

    /**
     * Whether a module is one of the sky ring's (0.16.0): it floats on its platform over the halls and the gate, so its
     * centre and its box say nothing of the ground the campus jobs work, nor of a ground site's being free.
     */
    static boolean floatsOverGround(TileModule m) {
        ModuleSpec spec = ModuleSpecs.get(m.moduleKey());
        return spec != null && spec.family == SiteFamily.SKY;
    }

    /**
     * The keep-out boxes and the protected boxes of the recorded sites so far, by module key, site, centre, front; and
     * the outer establish's (0.15.0).
     */
    private final Map<String, List<int[]>> siteKeepOuts = new HashMap<>(), siteProtects = new HashMap<>(),
        siteOuterKeepOuts = new HashMap<>(), siteOuterProtects = new HashMap<>();

    /**
     * The keep-out boxes the campus jobs leave alone for the module recorded on a site ({@link ModuleSpec#keepOut}: a
     * hall's whole {@link ModuleSpec#siteBox} as columns {x0, z0, x1, z1}, the zenith's legs over their heights
     * {x0, z0, x1, z1, yFrom, yTo}, 0.12.0); empty when the site's module is not known.
     */
    List<int[]> siteKeepOut(int site) {
        return siteBoxes(site, siteKeepOuts, 0);
    }

    /**
     * The keep-out boxes the outer establish leaves alone for the module recorded on a site (0.15.0,
     * {@link ModuleSpec#outerKeepOut}): a hall's exact ground row by row rather than its site box, so the 井 streets
     * beside it are paved to its forecourt.
     */
    List<int[]> siteOuterKeepOut(int site) {
        return siteBoxes(site, siteOuterKeepOuts, 2);
    }

    /** The protected boxes of a recorded site for the outer establish (0.15.0, {@link ModuleSpec#outerProtect}). */
    List<int[]> siteOuterProtect(int site) {
        return siteBoxes(site, siteOuterProtects, 3);
    }

    /**
     * The boxes {x0, y0, z0, x1, y1, z1} nothing of the nexus touches but the jobs of the module recorded on a site
     * ({@link ModuleSpec#protect}: a hall's site box from bedrock to the sky, an axis module's own space, 0.12.0);
     * empty when the site's module is not known.
     */
    List<int[]> siteProtect(int site) {
        return siteBoxes(site, siteProtects, 1);
    }

    /**
     * A recorded site's boxes, cached by module, site, centre and front: {@code which} 0 its keep-out, 1 its
     * protection, 2 and 3 the outer establish's (0.15.0).
     */
    private List<int[]> siteBoxes(int site, Map<String, List<int[]>> cache, int which) {
        ModuleSpec spec = ModuleSpecs.get(siteKinds.get(site));
        if (spec == null || !spec.hasSite(site)) return Collections.emptyList();
        int[] c = centre();
        int[] f = front();
        // a sky module (0.16.0) stands at the deck of the pod it was built by: its controller says which
        int[] at = sites.get(site);
        int datum = spec.family == SiteFamily.SKY && at != null ? DomeGeneratorShape.podDatum(at[1], c[1]) : 0;
        String key = spec.key + "@"
            + site
            + ":"
            + c[0]
            + ","
            + c[1]
            + ","
            + c[2]
            + ":"
            + f[0]
            + ","
            + f[1]
            + ":"
            + datum;
        List<int[]> boxes = cache.get(key);
        if (boxes == null) {
            switch (which) {
                case 1:
                    boxes = spec.protect(plan(), site, c[0], c[1], c[2], f[0], f[1], datum);
                    break;
                case 2:
                    boxes = spec.outerKeepOut(plan(), site, c[0], c[1], c[2], f[0], f[1]);
                    break;
                case 3:
                    boxes = spec.family == SiteFamily.SKY
                        ? spec.protect(plan(), site, c[0], c[1], c[2], f[0], f[1], datum)
                        : spec.outerProtect(plan(), site, c[0], c[1], c[2], f[0], f[1]);
                    break;
                default:
                    boxes = spec.keepOut(plan(), site, c[0], c[1], c[2], f[0], f[1]);
            }
            if (cache.size() > 16) cache.clear();
            cache.put(key, boxes);
        }
        List<int[]> out = new ArrayList<>();
        for (int[] b : boxes) out.add(b.clone());
        return out;
    }

    /**
     * Whether the multiblock is the module recorded on an axis site (0.12.0) or on the gate (0.14.0): a module whose
     * box takes in what stays the nexus's (the zenith's the nexus's whole column, the gatehouse's the gate road and the
     * establish pylons in its open passage), so its site's own boxes say what it takes instead.
     */
    private boolean onBoxedSite(TileMultiblock m) {
        for (Map.Entry<Integer, int[]> e : sites.entrySet()) {
            int[] s = e.getValue();
            if (boxedSite(e.getKey()) && s[0] == m.xCoord && s[1] == m.yCoord && s[2] == m.zCoord) return true;
        }
        return false;
    }

    /**
     * Whether the module on a site is kept off by its site's own boxes alone, not by its multiblock's whole bounds: the
     * axis sites (0.12.0) and the gate (0.14.0), whose bounds take in what stays the nexus's.
     */
    static boolean boxedSite(int site) {
        SiteFamily f = SiteFamily.of(site);
        return f != null && (f.axis() || f == SiteFamily.GATE);
    }

    /** Whether the cell lies in one of the boxes {x0, y0, z0, x1, y1, z1}. */
    static boolean inBoxes(List<int[]> boxes, int x, int y, int z) {
        for (int[] b : boxes) {
            if (x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] && z >= b[2] && z <= b[5]) return true;
        }
        return false;
    }

    private boolean onRecordedSite(TileMultiblock m) {
        for (int[] s : sites.values()) if (s[0] == m.xCoord && s[1] == m.yCoord && s[2] == m.zCoord) return true;
        return false;
    }

    // ---- what the builder asks

    TileNexus nexus() {
        return nexus;
    }

    /** The campus's box table feed (0.17.0). */
    SkinFeed skin() {
        return skin;
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

    /**
     * Where the module controller of a module job (or its repair) stands: {x, y, z, facing}; null for the others. Since
     * 0.16.0 the release job's is the controller of the pod it releases, and a job that builds the pod floating has it
     * at the datum the job carries ({@link BuildJob#podDatum}): a pod built by hand on the full legs over high ground
     * stands at 40, not at the datum the base layer gives one built now.
     */
    int[] moduleController(BuildJob j) {
        int[] c = centre();
        int[] f = front();
        if (j.releases() || j.floats()) return DomeGeneratorShape.moor(j.podDatum(c[1]))
            .controller(SiteFamily.ZENITH_SITE, c[0], c[1], c[2], f[0], f[1]);
        ModuleSpec spec = ModuleSpecs.get(j.module());
        if (spec == null || !spec.hasSite(j.site)) return null;
        // a root job that builds the deep root (0.16.0): the controller of the well that form grows from, which a well
        // dug by hand over low ground has at the full well's cell, not where the base layer would sink one now
        RootShape.Deep deep = j.form()
            .deep();
        if (deep != null && RootShape.KEY.equals(spec.key))
            return deep.controller(j.site, c[0], c[1], c[2], f[0], f[1]);
        // a sky module's (0.16.0) at the deck its job carries
        return spec.controller(j.site, c[0], c[1], c[2], f[0], f[1], j.form().deck);
    }

    /** The core part of a module job's module; -1 for the others. */
    int corePart(BuildJob j) {
        ModuleSpec spec = ModuleSpecs.get(j.module());
        return spec == null ? -1 : spec.corePart;
    }

    /**
     * Keeps what the builder took down in the recycle store (nothing when {@code buildKeepSpoils} is off: then it is
     * voided). The builder asks {@link #recycleFull} before it harvests anything; the store takes whatever a removal
     * gives even past its soft limit, so nothing is lost.
     */
    void recycle(List<ItemStack> drops) {
        if (drops == null || !CampusConfig.buildKeepSpoils) return;
        for (ItemStack d : drops) recycle.add(d);
    }

    /**
     * Whether the builder must wait before it takes anything more down: the recycle store is full (and kept). Tells
     * the online members once, until it has room again.
     */
    boolean recycleFull() {
        if (!CampusConfig.buildKeepSpoils || !recycle.full()) {
            recycleTold = false;
            return false;
        }
        if (!recycleTold) {
            recycleTold = true;
            tell(
                new ChatComponentTranslation(
                    P + "spoils_full",
                    nexus.xCoord + ", " + nexus.yCoord + ", " + nexus.zCoord));
        }
        return true;
    }

    /**
     * Takes up to {@code max} of a kind out of the recycle store as one stack (at most the item's stack size); null
     * when the store holds none, or the item is no longer in the game (it stays stored). For the recycle bin's slots
     * and screen.
     */
    public ItemStack takeRecycled(NBTTagCompound kind, int max) {
        if (kind == null || max <= 0) return null;
        ItemStack probe = RecycleStore.stackOf(kind, 1);
        if (probe == null) return null;
        int n = (int) recycle.extract(kind, Math.min(max, Math.max(1, probe.getMaxStackSize())));
        return n <= 0 ? null : RecycleStore.stackOf(kind, n);
    }

    /**
     * Fills the player's main inventory from the recycle store, the largest kinds first, as far as it has room (nothing
     * is dropped at their feet, nothing is voided, in creative mode either); returns how many items moved. 取出 and
     * the recycle bin's 全部取出.
     */
    public int takeRecycled(EntityPlayer p) {
        if (p == null || recycle.isEmpty()) return 0;
        int moved = 0;
        for (RecycleStore.Entry e : new ArrayList<>(recycle.list())) {
            ItemStack one = RecycleStore.stackOf(e.kind, 1);
            if (one == null) continue;
            int limit = Math.max(1, Math.min(one.getMaxStackSize(), p.inventory.getInventoryStackLimit()));
            long most = (long) limit * p.inventory.mainInventory.length;
            ItemStack s = RecycleStore.stackOf(e.kind, (int) Math.min(e.count, most));
            int took = insert(p, s);
            if (took <= 0) continue;
            recycle.extract(e.kind, took);
            moved += took;
        }
        if (moved > 0) {
            recycleTold = false;
            p.inventory.markDirty();
        }
        return moved;
    }

    /**
     * Puts as much of the stack into the player's main inventory as fits, onto stacks of the same item first, then
     * into empty slots; returns how many went in. The stack itself is not changed.
     */
    static int insert(EntityPlayer p, ItemStack s) {
        ItemStack[] inv = p.inventory.mainInventory;
        int cap = p.inventory.getInventoryStackLimit();
        int limit = Math.max(1, Math.min(s.getMaxStackSize(), cap));
        int left = s.stackSize;
        for (int i = 0; i < inv.length && left > 0; i++) {
            ItemStack t = inv[i];
            if (t == null || !t.isStackable() || !t.isItemEqual(s) || !ItemStack.areItemStackTagsEqual(t, s)) continue;
            int n = Math.min(Math.min(t.getMaxStackSize(), cap) - t.stackSize, left);
            if (n <= 0) continue;
            t.stackSize += n;
            left -= n;
        }
        for (int i = 0; i < inv.length && left > 0; i++) {
            if (inv[i] != null) continue;
            ItemStack put = s.copy();
            put.stackSize = Math.min(limit, left);
            inv[i] = put;
            left -= put.stackSize;
        }
        return s.stackSize - left;
    }

    /**
     * Whether a cell of ours belongs to another structure: another multiblock's box, or a keep-out box of the job. The
     * boxes are worked out at most once a second, and again as soon as another job is the current one (0.14.1): they
     * depend on the job, and a retire job's hall is guarded from every job but itself.
     */
    boolean protectedCell(int x, int y, int z) {
        World w = world();
        long now = w.getTotalWorldTime();
        if (now - protectedAt >= 20 || protectedAt == Long.MIN_VALUE || protectedFor != job) {
            protectedAt = now;
            protectedFor = job;
            protectedBoxes = protectedBoxes();
        }
        if (inBoxes(protectedBoxes, x, y, z)) return true;
        return inBoxes(podBoxes, x, y, z) && !podExempt.contains(cellKey(x, y, z));
    }

    /** The job the protected boxes were worked out for. */
    private BuildJob protectedFor;
    /**
     * The zenith's protected boxes while a sky module's job is the current one (0.16.0), and the cells of them its job
     * works ({@link SkyRing#exempt}: its door and its catwalk's first row), as {@link #cellKey}: they hold every other
     * cell for it.
     */
    private List<int[]> podBoxes = new ArrayList<>();
    private Set<Long> podExempt = new HashSet<>();

    /** One long for a world cell. */
    private static long cellKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private List<int[]> protectedBoxes() {
        List<int[]> out = new ArrayList<>();
        podBoxes = new ArrayList<>();
        podExempt = new HashSet<>();
        // a sky module's job (0.16.0) opens its door in the pod and lays its catwalk's first row inside the pod's box
        boolean skyJob = job != null && job.module() != null && SkyRing.platformSite(job.site);
        if (skyJob) {
            int[] c = centre(), f = front();
            int datum = SkyRing.datum(c[1], job.form().deck);
            for (int[] e : SkyRing.exempt(job.site, c[0], c[1], c[2], f[0], f[1], datum))
                podExempt.add(cellKey(e[0], e[1], e[2]));
        }
        World w = world();
        int[] own = job == null ? null : moduleController(job);
        // a retire job (0.14.1) works the ground of the hall it takes down: the hall, and its one box, are its own
        int[] retire = job == null ? null : job.retireBox();
        // the modules on the campus's recorded hall sites, loaded or not, but the one the job builds or repairs (and
        // the pod the release job takes the legs of, 0.16.0)
        for (Map.Entry<Integer, int[]> e : sites.entrySet()) {
            if (job != null && worksSite(job, e.getKey())) continue;
            // the whole site: the building, its forecourt, apron and canopy (0.10.1; the footprint alone before); an
            // axis module only the space it takes (0.12.0); for the outer establish a hall's exact ground (0.15.0)
            List<int[]> boxes = job != null && job.laysOuter() ? siteOuterProtect(e.getKey()) : siteProtect(e.getKey());
            if (skyJob && e.getKey() == SiteFamily.ZENITH_SITE) podBoxes.addAll(boxes);
            else out.addAll(boxes);
        }
        for (TileMultiblock m : NexusRegistry.loaded(w)) {
            if (m == nexus) continue;
            if (own != null && m.xCoord == own[0] && m.yCoord == own[1] && m.zCoord == own[2]) continue;
            if (retire != null && m instanceof TileModule tm && inColumns(retire, tm.centre())) continue;
            // the zenith's bounds take in the nexus's whole column from its legs to its pod: its own boxes above say
            // what it takes, so the nexus can still grow and be repaired under it (0.12.0); the gatehouse's take in
            // the gate road and the pylons under its bridge, which its boxes leave to the nexus's jobs (0.14.0)
            if (onBoxedSite(m)) continue;
            int[] b = m.bounds();
            if (b != null) out.add(b.clone());
            else if (m instanceof TileModule tm) {
                int[] mc = tm.centre();
                int[] k = BuildPlan.moduleKeepOut(mc);
                out.add(new int[] { k[0], mc[1] - 1, k[1], k[2], mc[1] + 16, k[3] });
            }
            // another campus's outer diagonal building (0.15.0): its whole site from bedrock to the sky, so no job
            // here digs under or fills over its apron, annex or allée
            if (m instanceof TileModule tm && !onRecordedSite(tm)) {
                ModuleSpec spec = ModuleSpecs.get(tm.moduleKey());
                if (spec != null && spec.family == SiteFamily.OUTER_DIAG)
                    for (int[] k : strayBoxes(tm)) out.add(new int[] { k[0], 0, k[1], k[2], 255, k[3] });
            }
        }
        // the job's keep-out boxes: whole columns, or a height-ranged box over its heights (0.12.0)
        if (job != null && retire == null) for (int[] k : job.keepOut) out.add(
            new int[] { Math.min(k[0], k[2]), k.length == 6 ? Math.min(k[4], k[5]) : 0, Math.min(k[1], k[3]),
                Math.max(k[0], k[2]), k.length == 6 ? Math.max(k[4], k[5]) : 255, Math.max(k[1], k[3]) });
        return out;
    }

    /**
     * Whether a job works the module recorded on a site, so that site's protected boxes do not hold it: the module job
     * or the repair on the site, and (0.16.0) the release job on the zenith, which takes down the legs of the pod
     * standing there. The zenith's boxes hold the leg columns and the pod's box; every other job keeps out of both.
     */
    static boolean worksSite(BuildJob j, int site) {
        if (j.module() != null && j.site == site) return true;
        return j.releases() && site == SiteFamily.ZENITH_SITE;
    }

    /** Whether the cell's column lies in the columns of a box {x0, z0, x1, z1, ...}. */
    static boolean inColumns(int[] box, int[] cell) {
        return cell[0] >= Math.min(box[0], box[2]) && cell[0] <= Math.max(box[0], box[2])
            && cell[2] >= Math.min(box[1], box[3])
            && cell[2] <= Math.max(box[1], box[3]);
    }

    /** Whether the column lies in another nexus's campus. */
    boolean otherCampusAt(int x, int z) {
        return CampusRegistry.ownerAt(world(), x, z, centre()) != null;
    }

    /** A job came to its end: tell the members (and an operator who finished it at once, how it went). */
    void finished(BuildJob j) {
        tell(new ChatComponentTranslation(P + "done", j.name()));
        // the outer establish ran to its end (0.15.0): the outer families' sites stand on laid ground now, judged
        // against the neighbours it was laid with (a job that regained ground: those it was made with now)
        if (j.laysOuter()) {
            if (j.patches()) {
                // a patch laid the boxes it was made for: they are forgotten, the neighbours the ring was laid with
                // kept (a regain may be still to come)
                forgetBoxes(laidBoxes, j.patch);
            } else {
                laidWith.clear();
                for (int[] n : j.neighbours) laidWith.add(n.clone());
                // the boxes of the modules on no site it was laid round, to lay once they are gone
                List<int[]> recorded = new ArrayList<>();
                for (int site : sites.keySet()) recorded.addAll(siteOuterKeepOut(site));
                if (!j.regains()) laidBoxes.clear();
                for (int[] b : laidRound(j.keepOut, recorded)) {
                    boolean known = false;
                    for (int[] k : laidBoxes) known |= Arrays.equals(k, b);
                    if (!known) laidBoxes.add(b);
                }
            }
            laid.add(BuildPlan.OUTER);
            syncRegistry();
            dirty();
            stateChanged();
        }
        // a retire job that laid the outer floor in its box (0.15.0) laid the ring the hall kept out of there
        int[] retired = j.retiresOuter() ? j.retireBox() : null;
        if (retired != null && laidBoxes.removeIf(b -> CityOffer.overlap(b, retired))) dirty();
        if (j != instantJob) return;
        instantJob = null;
        report(
            new ChatComponentTranslation(
                P + "instant_done",
                j.name(),
                String.valueOf(j.placed()),
                String.valueOf(j.cleared),
                String.valueOf(j.filled)));
    }

    /**
     * The builder paused a job: an operator's instant build tells who asked, once, why it stopped, and ends there when
     * the cause needs someone to act ({@link #endsInstant}); the job goes on at the normal pace once it is put right.
     */
    void paused(BuildJob j, BuildState.Pause why) {
        if (j != instantJob || why == null) return;
        if (endsInstant(why)) instantJob = null;
        if (instantPauseTold) return;
        instantPauseTold = true;
        report(
            new ChatComponentTranslation(
                P + "instant_paused",
                j.name(),
                new ChatComponentTranslation(
                    P + "pause."
                        + why.name()
                            .toLowerCase(Locale.ROOT))));
    }

    /**
     * The builder paused a job as incomplete (0.14.2): tells the members which cell keeps the structure from forming,
     * where it is, what is there and what should be (the first wrong cell, {@link TileMultiblock#firstWrong}), or, when
     * none can be named (it lies in an unloaded chunk, the controller is gone), only that it did not form; either way
     * how to go on (修复, or putting it right by hand).
     */
    void incomplete(BuildJob j, WrongCell cell) {
        if (cell == null) {
            tell(new ChatComponentTranslation(P + "incomplete", j.name()));
            return;
        }
        tell(
            new ChatComponentTranslation(
                P + "incomplete_at",
                j.name(),
                cell.pos(),
                new ChatComponentTranslation(cell.found),
                new ChatComponentTranslation(cell.wanted)));
    }

    /**
     * The cell that keeps the structure of a job paused as incomplete from forming (0.14.2, the build page's line), or
     * null: no such job, or no cell can be named. Looked for with the structure's own check at most every few seconds.
     */
    public WrongCell incompleteCell() {
        if (job == null || job.state != BuildState.State.PAUSED || job.pause != BuildState.Pause.INCOMPLETE)
            return null;
        TileMultiblock t = builder.target(job);
        return t == null ? null : t.firstWrong(Builder.formShape(job));
    }

    /**
     * Whether a pause ends an operator's instant build: a blocked cell, a protection mod's refusal, an unloaded chunk,
     * a structure that will not form. None of those lifts by itself soon, and the instant mode would otherwise stay on
     * for good. (A free job never waits for materials or power.)
     */
    static boolean endsInstant(BuildState.Pause why) {
        return why == BuildState.Pause.BLOCKED || why == BuildState.Pause.PROTECTED
            || why == BuildState.Pause.UNLOADED
            || why == BuildState.Pause.INCOMPLETE;
    }

    /** Tells the operator who started the instant build, when online; the members otherwise. */
    private void report(IChatComponent msg) {
        MinecraftServer server = MinecraftServer.getServer();
        if (instantBy != null && server != null && server.getConfigurationManager() != null) {
            for (Object o : server.getConfigurationManager().playerEntityList) {
                if (o instanceof EntityPlayerMP p && instantBy.equals(p.getUniqueID())) {
                    p.addChatMessage(msg);
                    return;
                }
            }
        }
        tell(msg);
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
     * the Echo Archive comes after it (since 0.10.1 a docked 0.9.2 hall no longer stands in for it, {@link #exists}):
     * on the first tick after a load, then every ten seconds until someone was online to hear it.
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
        if (!CampusConfig.campusEnabled) return P + "legacy_only";
        if (active && job != null && job.raisesNexus()) return P + "already_running";
        active = true;
        hinted = true;
        int[] c = centre();
        registeredRadius = registryRadius();
        CampusRegistry.add(world(), c[0], c[1], c[2], registeredRadius);
        keepOutAt = Long.MIN_VALUE;
        enqueue(BuildJob.establish(keepOut()));
        return P + "placed_lifted";
    }

    /**
     * 铺设广场 on a legacy nexus: the campus becomes active and the forum job is made (grading, floor, fixtures).
     * Refused when another campus lies so near that their inner discs would overlap ({@link #innerSpacing}; up to
     * 0.14.2 the whole {@code CampusConfig.buildMinSpacing}, whose default 0.15.0 raised for new campuses: the nexus
     * stands
     * here already, and the outer rings share their ground, {@link CampusSplit}); and since 0.14.1 when its base lies
     * outside the campus band ({@link CampusBand}): a nexus built by hand may stand anywhere, but a campus over Y0 225
     * could not grow, one under Y0 14 has no room for its well.
     */
    public String layForum(EntityPlayer p) {
        if (!nexus.member(p)) return NOT_MEMBER;
        if (active || !CampusConfig.campusEnabled) return P + "legacy_only";
        int[] c = centre();
        String band = CampusBand.refusal(c[1], false);
        if (band != null) return "fluxecho." + band;
        if (CampusRegistry.tooClose(world(), c[0], c[2], innerSpacing()) != null) return P + "forum_too_close";
        active = true;
        hinted = true;
        registeredRadius = registryRadius();
        CampusRegistry.add(world(), c[0], c[1], c[2], registeredRadius);
        keepOutAt = Long.MIN_VALUE;
        enqueue(BuildJob.forum(keepOut()));
        return P + "forum_laid";
    }

    /**
     * The spacing a campus that already stands keeps from the others (0.15.0): a core put back where it stood
     * ({@link #resume}) and a 0.9.2 nexus laying its forum ({@link #layForum}) only need room for their inner disc,
     * {@link CampusSplit#INNER_SPACING} (144, the spacing every campus was placed at before 0.15.0), or less when the
     * config asks for less ({@code buildMinSpacing}, 0 turning the check off). The default 312 is for new cores
     * ({@code ItemBlockNexusCore}); refusing a campus that stands would only take its campus away.
     */
    public static int innerSpacing() {
        return CampusConfig.buildMinSpacing <= 0 ? 0
            : Math.min(CampusConfig.buildMinSpacing, CampusSplit.INNER_SPACING);
    }

    /**
     * 开始: a member's consent (or lifting the player's pause). A module job first needs its core in the ledger, unless
     * it is free: a player in creative mode starts the job free (0.10.1; no materials, no EU, no core), and it stays
     * so.
     */
    public String start(EntityPlayer p) {
        if (job == null || BuildState.ended(job.state)) return NO_JOB;
        if (!nexus.member(p)) return NOT_MEMBER;
        if (growsUnformed(job, nexus.formed())) return P + "grow_needs_nexus";
        boolean creative = p.capabilities != null && p.capabilities.isCreativeMode;
        freshKeepOut();
        ModuleSpec spec = ModuleSpecs.get(job.module());
        if (spec != null && !job.repair()
            && !creative
            && !free(job)
            && BuildLedger.clamp(CampusConfig.buildCostScale) > 0
            && ledger.part(spec.corePart) < 1
            && !coreBuilt(job)) return P + "need_core";
        if (job.state == BuildState.State.SURVEY) {
            // consent given while the survey runs: it goes on by itself when the survey ends (and a pause asked
            // for during the survey is taken back)
            consent(p.getUniqueID(), creative);
            if (job.pause == BuildState.Pause.PLAYER) job.pause = BuildState.Pause.NONE;
            stateChanged();
            return P + "started";
        }
        if (!BuildState.canStart(job.state, job.pause)) return P + "already_running";
        job.state = BuildState.start(job.state, job.pause);
        job.pause = BuildState.Pause.NONE;
        consent(p.getUniqueID(), creative);
        stateChanged();
        return P + "started";
    }

    /**
     * Whether 开始 is refused on the job because it is a growth that has not begun (never started, nothing touched,
     * nothing in flight) while the nexus stands unformed (0.12.0): its plan holds only the new stage and the lift pad
     * and cannot put a broken base right, so the nexus is repaired first (修复, which takes its place while it waits).
     * A growth that has begun goes on, a player's pause lifted: its pad landing takes the nexus apart for a moment, and
     * if the nexus does not form it stops as incomplete, where 修复 repairs the nexus instead ({@link #repairGrowth}).
     * This is exactly the growth {@link #holdsNexusRepair} lets through, so an unformed nexus always leaves 开始 or 修复
     * open.
     */
    static boolean growsUnformed(BuildJob j, boolean formed) {
        return j.grows() && !j.started && !j.touched && j.flights.isEmpty() && !formed;
    }

    /**
     * Before a campus job that has touched nothing starts: its plan is made again with the keep-out boxes of now
     * ({@link BuildJob#retarget}). It was made with the modules loaded when it was offered; by 开始 more of them may be
     * loaded round the player, or a module may have been built meanwhile. The outer establish (0.15.0) also takes the
     * neighbouring campuses of now: one may have been placed or have gone since.
     */
    private void freshKeepOut() {
        if (job != null && job.retires()) freshRetireBox();
        if (job == null || !job.campusJob() && !job.laysOuter()) return;
        keepOutAt = Long.MIN_VALUE;
        // the outer establish (0.15.0) takes its boxes out to the outer disc, and its neighbours of now
        boolean changed = job.laysOuter()
            ? job.retarget(job.patches() ? patchKeepOut() : outerKeepOut(), neighbours(true))
            : job.retarget(keepOut());
        if (changed) {
            builder.reset();
            needDirty = true;
            etaAt = Long.MIN_VALUE;
            stateChanged();
        }
    }

    /** The current job was started by the player; a start in creative mode makes it free. */
    private void consent(UUID by, boolean free) {
        job.started = true;
        job.startedBy = by;
        if (free && !job.free) {
            job.free = true;
            needDirty = true;
        }
    }

    /**
     * An operator's {@code /fluxecho build finish}: the current job (the next queued one when there is none) is
     * started if it was not, made free, and run as an instant build until it is done: {@link Builder#INSTANT_CELLS}
     * cells a tick, flights of one tick, few effects, through the same code paths as ever (protection, the capture,
     * recycling, forming and commissioning). The player ({@code null} for the console) is told when it is done, or why
     * it stopped; the members when the player is gone. No member check: the command checks its permission. Returns a
     * lang key: {@code build.instant_started}, or {@code build.no_job}.
     */
    public String finishNow(EntityPlayer p) {
        if (!active) return NO_JOB;
        if (job != null && BuildState.ended(job.state) && !queue.isEmpty()) {
            // a finished job lingering (for its completion effect) or a cancelled one landing its last cells: the
            // next one is meant; what is still in flight goes back to the ledger
            Builder.forget(job, ledger, job.plan(this));
            nextJob();
        }
        if (job == null && !queue.isEmpty()) nextJob();
        if (job == null || BuildState.ended(job.state)) return NO_JOB;
        freshKeepOut();
        UUID by = p == null ? null : p.getUniqueID();
        if (job.state == BuildState.State.SURVEY) {
            consent(by, true);
            if (job.pause == BuildState.Pause.PLAYER) job.pause = BuildState.Pause.NONE;
        } else if (BuildState.canStart(job.state, job.pause)) {
            job.state = BuildState.start(job.state, job.pause);
            job.pause = BuildState.Pause.NONE;
            consent(by, true);
        } else consent(job.startedBy != null ? job.startedBy : by, true);
        instantJob = job;
        instantBy = by;
        instantPauseTold = false;
        stateChanged();
        return P + "instant_started";
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
        cancelJob();
        return P + "cancelled";
    }

    /** Ends the current job as a member's 取消 does ({@link #cancel}, after its checks). */
    void cancelJob() {
        job.state = BuildState.cancel(job.state);
        if (job.module() != null && !job.repair())
            declined.put(job.module(), world().getTotalWorldTime() + DECLINE_TICKS);
        // a retire job cancelled is offered again a day later too (0.14.1)
        if (job.retires()) declined.put(job.planKey(), world().getTotalWorldTime() + DECLINE_TICKS);
        // a growth cancelled is offered again a day later, as a module is (0.12.0); so is the outer establish (0.15.0)
        // and the release of the pod (0.16.0)
        if (job.grows() || job.laysOuter() || job.releases())
            declined.put(job.planKey(), world().getTotalWorldTime() + DECLINE_TICKS);
        if (declinesRefresh(job)) gradedVersion = Math.max(gradedVersion, BuildJob.GRADE_V);
        if (job == instantJob) instantJob = null;
        stateChanged();
        // a building cancelled on an outer diagonal drops its claim at once (0.15.0), as an offer makes it
        if (job.module() != null && OuterRing.diagSite(job.site)) syncRegistry();
    }

    /**
     * Whether cancelling the job declines the refresh for good: a campus job that grades (establish, forum, the
     * refresh itself). A repair of the nexus is a campus job too, but it never grades: cancelling it says nothing
     * about the floor.
     */
    static boolean declinesRefresh(BuildJob j) {
        return j.campusJob() && !j.repair();
    }

    /**
     * ◀ ▶: moves a module job's projection to the next free site of its module, until it touched anything: through its
     * candidates (0.14.1, {@link ModuleSpec#candidates}: its preference list, then its family's other sites open at the
     * nexus's phase).
     */
    public String moveSite(EntityPlayer p, int dir) {
        if (job == null || BuildState.ended(job.state)) return NO_JOB;
        if (!nexus.member(p)) return NOT_MEMBER;
        ModuleSpec spec = ModuleSpecs.get(job.module());
        if (spec == null || job.repair() || job.touched || !job.flights.isEmpty()) return P + "site_locked";
        int site = CityOffer
            .move(spec.kind(centre()[1]), job.site, dir, nexus.phase(), this::groundLaid, new SiteView());
        if (site < 0) return P + "site_locked";
        int from = job.site;
        job.moveTo(spec, site);
        builder.reset();
        stateChanged();
        // a building moved onto or off an outer diagonal moves its claim at once (0.15.0), as an offer makes it: the
        // registry would else hold the old site until the next look, and a neighbour looking first could offer its
        // own building on ground overlapping the new one
        if (OuterRing.diagSite(from) || OuterRing.diagSite(site)) syncRegistry();
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

    /**
     * 取出: whole items of the raw credit (not echo crystals) to the member (overflow at their feet), then from the
     * recycle store as much as their inventory takes ({@link #takeRecycled(EntityPlayer)}; the rest stays stored).
     */
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
        if (takeRecycled(p) > 0) gave = true;
        if (!gave) return P + "nothing_to_withdraw";
        dirty();
        p.inventory.markDirty();
        return P + "withdrawn";
    }

    private static void give(EntityPlayer p, ItemStack s) {
        if (!p.inventory.addItemStackToInventory(s) && s.stackSize > 0) p.dropPlayerItemWithRandomChoice(s, false);
    }

    /**
     * 修复: a job that stopped as incomplete starts over on the same plan (cells already right come free), but a growth
     * that did gives way to a repair of the nexus ({@link #repairGrowth}), and since 0.16.0 a release to a repair of
     * the pod ({@link #repairRelease}) and the deepen job to a repair of the whole deep root ({@link #repairDeepen}):
     * their plans hold only their new part, so starting them over cannot put a broken cell of the rest right;
     * otherwise a repair of the nexus or of a sited module that stood unformed for a while is queued.
     */
    public String repair(EntityPlayer p) {
        if (!nexus.member(p)) return NOT_MEMBER;
        if (!active) return P + "nothing_to_repair";
        return repairNow();
    }

    /** {@link #repair} after its checks. */
    String repairNow() {
        if (job != null && job.grows()
            && job.state == BuildState.State.PAUSED
            && job.pause == BuildState.Pause.INCOMPLETE) return repairGrowth();
        if (job != null && job.releases()
            && job.state == BuildState.State.PAUSED
            && job.pause == BuildState.Pause.INCOMPLETE) return repairRelease();
        if (job != null && job.deepens()
            && job.state == BuildState.State.PAUSED
            && job.pause == BuildState.Pause.INCOMPLETE) return repairDeepen();
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

    /**
     * 修复 on a growth that stopped as incomplete (0.12.0): its plan holds only the new stage and the lift pad, so
     * starting it over cannot put back a broken base. The growth ends (not declined: no member said no to it) and a
     * repair of the nexus is queued in its place, at the phase {@link #repairPhase} gives: once the growth's stage and
     * pad stand, the establish plan of the new phase puts the base, the stages and the pad right and the nexus forms as
     * that phase; a repair at the old phase would clear the new stage off the dais.
     */
    String repairGrowth() {
        BuildJob g = job;
        int phase = repairPhase(g, nexus.grown());
        g.state = BuildState.cancel(g.state);
        if (g == instantJob) instantJob = null;
        enqueue(BuildJob.repairNexus(keepOut(), phase));
        return P + "repair_queued";
    }

    /**
     * 修复 on a release job that stopped as incomplete (0.16.0): its plan holds only the legs it takes down, so starting
     * it over cannot put a broken pod right. The release ends (not declined: no member said no to it) and the pod on
     * the zenith is repaired in its place as the shape it last stood as ({@link #repairOf}: floating once it floated,
     * at its own datum), and floating at the release's datum all the same once the release began taking the legs down
     * ({@link #floatPodRepair}: a pod that came apart before it floated still carries the moored name), so the repair
     * never lays the legs the members agreed to release. A repair of the pod already queued (by an earlier 修复 while
     * the release ran) is the one; with no pod left nothing is queued and the members hear the release ended
     * ({@code release_ended}); a new pod is offered, floating from the start.
     */
    String repairRelease() {
        BuildJob r = job;
        r.state = BuildState.cancel(r.state);
        if (r == instantJob) instantJob = null;
        stateChanged();
        if (jobOnSite(SiteFamily.ZENITH_SITE) != null) return P + "repair_queued";
        ModuleSpec spec = ModuleSpecs.get(DomeGeneratorShape.KEY);
        int[] at = sites.get(SiteFamily.ZENITH_SITE);
        World w = world();
        TileEntity te = at != null && w != null && w.blockExists(at[0], at[1], at[2])
            ? w.getTileEntity(at[0], at[1], at[2])
            : null;
        if (spec == null || !(te instanceof TileModule m) || !DomeGeneratorShape.KEY.equals(m.moduleKey()))
            return P + "release_ended";
        BuildJob fix = repairOf(spec, SiteFamily.ZENITH_SITE, te, centre()[1], openDoors());
        floatPodRepair(fix, te, r);
        enqueue(fix);
        return P + "repair_queued";
    }

    /**
     * Makes a repair of the pod on the zenith float when the pod can only float (0.16.0), though its tile does not say
     * it floated ({@link #repairOf} reads only the shape it last stood as): the release {@code release} began taking
     * its legs down (the pod came apart before it ever floated, so it is still named moored), or the nexus may float a
     * pod from the start ({@link #floatsFresh}) and a door to a sky module is open or no leg of the pod is left (a core
     * put back by hand over a released pod names itself moored). It floats at the release's datum, else at the one its
     * controller stands at. So a repair never lays again the legs of a pod meant to float, nor glazes a door that leads
     * to a sky module. Any other repair is left as it is.
     */
    private void floatPodRepair(BuildJob r, TileEntity te, BuildJob release) {
        if (r.floats() || !DomeGeneratorShape.KEY.equals(r.module())) return;
        int doors = openDoors();
        if (release != null && (release.started || release.touched)
            && release.form()
                .floats()) {
            r.floatAt(release.form().pod, doors);
            return;
        }
        if (!floatsFresh(DomeGeneratorShape.KEY, nexus.phase(), nexus.has(Research.SKY_RING))) return;
        int y0 = centre()[1];
        int datum = te == null ? 0 : DomeGeneratorShape.podDatum(te.yCoord, y0);
        if (!DomeGeneratorShape.isDatum(datum)) datum = DomeGeneratorShape.datum(y0);
        if (doors != 0 || legsLeft(datum) == 0) r.floatAt(datum, doors);
    }

    /** The release job that is not over, or null (there is only ever one). */
    private BuildJob pendingRelease() {
        for (BuildJob j : jobs()) if (j.releases() && !BuildState.ended(j.state)) return j;
        return null;
    }

    /**
     * 修复 on the deepen job stopped as incomplete (0.16.0): its plan holds only the deep root's new part
     * ({@link BuildPlan#deepen}), so starting it over cannot put a broken cell of the well right (a tap, a wall block,
     * the ramp broken while it ran), and the root's own repair is held off while the job stands on its site. The
     * deepen job ends (not declined: no member said no to it) and a repair of the root is queued in its place as the
     * form the job was growing it into ({@link BuildJob#rootAs}: the whole deep root's plan, {@link BuildPlan#deep}),
     * which puts the well's cells and the new part right together and waits for that form. Never by the tile's name,
     * which still says the well: a repair as the well would close the grate again.
     */
    String repairDeepen() {
        BuildJob d = job;
        String form = d.form().root;
        d.state = BuildState.cancel(d.state);
        if (d == instantJob) instantJob = null;
        stateChanged();
        ModuleSpec spec = ModuleSpecs.get(RootShape.KEY);
        if (spec == null || !spec.hasSite(SiteFamily.ROOT_SITE)) return P + "nothing_to_repair";
        BuildJob r = BuildJob.repairModule(spec, SiteFamily.ROOT_SITE);
        r.rootAs(form);
        enqueue(r);
        return P + "repair_queued";
    }

    /**
     * The phase a repair of the nexus builds when it takes the place of a growth: the growth's phase once its stage and
     * lift pad were placed (it reached its FORM stage), else the phase the nexus grew to.
     */
    static int repairPhase(BuildJob growth, int grown) {
        if (growth == null || !growth.grows() || growth.stage < BuildPlan.E_FORM) return grown;
        return Math.max(grown, growth.growPhase());
    }

    /**
     * Whether a job keeps 修复 from repairing the unformed nexus: one that raises the nexus itself (the establish job, a
     * repair of it), or a growth that has begun (started, touched something or with cells in flight): its lift pad
     * landing takes the nexus apart for a moment, and it ends with the nexus formed as the new phase or stops as
     * incomplete, where 修复 repairs the nexus instead ({@link #repairGrowth}). A growth still waiting for 开始 holds
     * nothing up: the repair takes its place ({@link #admit}) and the growth is surveyed again after it.
     */
    static boolean holdsNexusRepair(BuildJob j) {
        if (BuildState.ended(j.state)) return false;
        if (j.raisesNexus()) return true;
        return j.grows() && (j.started || j.touched || !j.flights.isEmpty());
    }

    private BuildJob repairJob() {
        World w = world();
        if (w == null) return null;
        long now = w.getTotalWorldTime();
        if (!nexus.formed() && nexusUnformedSince >= 0 && now - nexusUnformedSince > REPAIR_AFTER) {
            boolean busy = false;
            for (BuildJob j : jobs()) if (holdsNexusRepair(j)) busy = true;
            if (!busy) return BuildJob.repairNexus(keepOut(), nexus.grown());
        }
        for (Map.Entry<Integer, Long> e : siteUnformedSince.entrySet()) {
            if (now - e.getValue() <= REPAIR_AFTER || jobOnSite(e.getKey()) != null) continue;
            ModuleSpec spec = ModuleSpecs.get(siteKinds.get(e.getKey()));
            if (spec == null || !spec.hasSite(e.getKey())) continue;
            int[] at = sites.get(e.getKey());
            TileEntity te = at != null && w.blockExists(at[0], at[1], at[2]) ? w.getTileEntity(at[0], at[1], at[2])
                : null;
            // the pod is repaired as the shape it stands as (0.16.0), and so is the root (a deep root whole, never as
            // the bare well): not while its tile cannot be asked
            if (te == null && (DomeGeneratorShape.KEY.equals(spec.key) || RootShape.KEY.equals(spec.key))) continue;
            BuildJob r = repairOf(spec, e.getKey(), te, centre()[1], openDoors());
            // the pod is repaired floating whenever it can only float, its tile's name aside (0.16.0)
            if (e.getKey() == SiteFamily.ZENITH_SITE) floatPodRepair(r, te, pendingRelease());
            // a sky module whose tile cannot be asked (0.16.0) is repaired at the deck its recorded controller gives
            int deck = at == null || spec.family != SiteFamily.SKY ? 0
                : DomeGeneratorShape.podDatum(at[1], centre()[1]);
            if (r.form().deck == 0 && DomeGeneratorShape.isDatum(deck)) r.deckAt(deck);
            return r;
        }
        return null;
    }

    /**
     * The repair of the module recorded on a site, whose controller's tile is {@code at} (null when not known), over a
     * nexus whose base layer is {@code y0}: its module job's plan once more, and since 0.16.0 for an Echo Dome
     * Generator that stands, or last stood, as floating the floating plan at its own datum ({@link #floatingPod}), so
     * a repair never raises its legs again (the floating shape matches first: they would stand as plain pillars).
     */
    static BuildJob repairOf(ModuleSpec spec, int site, TileEntity at, int y0) {
        return repairOf(spec, site, at, y0, 0);
    }

    /**
     * {@link #repairOf(ModuleSpec, int, TileEntity, int)} leaving the doors of the sky slots in {@code doors} open on a
     * floating pod (0.16.0, {@link #openDoors}): a repair never glazes a door that leads to a sky module or to a job
     * for one. A sky module's repair is laid at the deck its controller stands at ({@link #skyDatum}).
     */
    static BuildJob repairOf(ModuleSpec spec, int site, TileEntity at, int y0, int doors) {
        BuildJob r = BuildJob.repairModule(spec, site);
        int pod = floatingPod(at, y0);
        if (pod > 0) r.floatAt(pod, doors);
        // a deep root (0.16.0) is put back whole as the form it stood as, never as the bare well
        String deep = deepRootOf(at);
        if (deep != null) r.rootAs(deep);
        int deck = spec.family == SiteFamily.SKY ? skyDatum(at, y0) : 0;
        if (deck > 0) r.deckAt(deck);
        return r;
    }

    /**
     * The sky datum of the deck a sky module stands at (0.16.0), read from its controller's height over the base layer
     * {@code y0} (every sky module's controller stands {@link DomeGeneratorShape#CTRL_Y} over its deck, as the pod's
     * over its floor); 0 for any other tile, or one at no sky datum.
     */
    static int skyDatum(TileEntity te, int y0) {
        if (!(te instanceof TileModule m)) return 0;
        ModuleSpec spec = ModuleSpecs.get(m.moduleKey());
        if (spec == null || spec.family != SiteFamily.SKY) return 0;
        int datum = DomeGeneratorShape.podDatum(m.yCoord, y0);
        return DomeGeneratorShape.isDatum(datum) ? datum : 0;
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
            // a refresh is offered again by the version below, not carried
            if (!j.campusJob() || j.refresh() || BuildState.ended(j.state)) continue;
            if (j.raisesNexus()) c.unfinished = BuildPlan.ESTABLISH;
            else if (c.unfinished.isEmpty()) c.unfinished = BuildPlan.FORUM;
        }
        c.graded = gradedVersion;
        c.free = freeBuild;
        c.phase = nexus.grown();
        c.laid.addAll(laid);
        for (int[] n : laidWith) c.laidWith.add(n.clone());
        for (int[] b : laidBoxes) c.laidBoxes.add(b.clone());
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
        if (c == null || w == null || w.isRemote || active || !CampusConfig.campusEnabled) return null;
        if (!c.at(w.provider.dimensionId, nexus.xCoord, nexus.yCoord, nexus.zCoord)) return null;
        // back on its own dais: it faces the way it did, whichever way the player stood
        if (nexus.front()
            .ordinal() != c.front) nexus.place(nexus.owner(), nexus.ownerName(), c.front);
        int[] ce = centre();
        if (CampusRegistry.tooClose(w, ce[0], ce[2], innerSpacing()) != null) {
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
        gradedVersion = c.graded;
        freeBuild = c.free;
        laid.clear();
        laid.addAll(c.laid);
        laidWith.clear();
        for (int[] n : c.laidWith) laidWith.add(n.clone());
        laidBoxes.clear();
        for (int[] b : c.laidBoxes) laidBoxes.add(b.clone());
        // the nexus put back is the phase it had grown to (0.12.0): it forms as that phase, a repair builds that phase
        nexus.regrow(c.phase);
        resumeJob = c.unfinished;
        resumeCheckAt = w.getTotalWorldTime() + RESUME_CHECK;
        registeredRadius = registryRadius();
        CampusRegistry.add(w, ce[0], ce[1], ce[2], registeredRadius);
        registeredClaims = claims();
        CampusRegistry.setClaims(w, ce[0], ce[1], ce[2], registeredClaims);
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
            // the same plan as the establish job, without its grading, at the phase the nexus had grown to: what
            // stands comes free
            enqueue(BuildJob.repairNexus(keepOut(), nexus.grown()));
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
     * "" for none), {@code Pv} (the grading version its grading last ran under, 0.10.1), {@code Fb} (free building) and
     * {@code Ph} (the phase the nexus had grown to, 0.12.0; 1 when missing).
     */
    static final class Carried {

        int dim, front = 2, phase = 1;
        int[] core = new int[3];
        final Map<Integer, int[]> sites = new TreeMap<>();
        final Map<Integer, String> kinds = new HashMap<>();
        boolean skip, free;
        final Map<String, Long> declined = new HashMap<>();
        String unfinished = "";
        int graded;
        /** The grounds laid beyond the forum (0.15.0, {@code Gl}): the outer establish's plan key once it ran. */
        final Set<String> laid = new LinkedHashSet<>();
        /** The neighbours {x, z} or {x, z, claims} the outer ring was laid with (0.15.0, {@code Gn} and {@code Gq}). */
        final List<int[]> laidWith = new ArrayList<>();
        /** The boxes of the modules on no site the outer ring was laid round (0.15.0, {@code Gk}). */
        final List<int[]> laidBoxes = new ArrayList<>();

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
            t.setInteger("Pv", graded);
            t.setBoolean("Fb", free);
            t.setInteger("Ph", phase);
            if (!laid.isEmpty()) t.setTag("Gl", grounds(laid));
            if (!laidWith.isEmpty()) t.setIntArray("Gn", BuildJob.centres(laidWith));
            int[] laidClaims = BuildJob.claims(laidWith);
            if (laidClaims.length > 0) t.setIntArray("Gq", laidClaims);
            if (!laidBoxes.isEmpty()) t.setIntArray("Gk", BuildJob.boxes(laidBoxes, 4));
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
            c.graded = t.getInteger("Pv");
            c.free = t.getBoolean("Fb");
            c.phase = Math.max(1, t.getInteger("Ph"));
            readGrounds(t.getTagList("Gl", 8), c.laid);
            BuildJob.readCentres(t.getIntArray("Gn"), c.laidWith);
            BuildJob.readClaims(t.getIntArray("Gq"), c.laidWith);
            BuildJob.readFlat(t.getIntArray("Gk"), c.laidBoxes);
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

    /**
     * What the current job still needs, in units (empty when none, or when the job is free); see
     * {@link BuildLedger#need}.
     */
    private Map<String, Long> need() {
        if (job == null || free(job)) return Collections.emptyMap();
        if (needDirty || needJob != job) {
            needJob = job;
            needDirty = false;
            need = ledger.need(unbuilt(job), CampusConfig.buildCostScale);
        }
        return need;
    }

    /**
     * Part counts of the job's steps that are not done, skipped or in flight; see
     * {@link #unbuilt(BuildPlan.Plan, BuildJob)}.
     */
    private Map<Integer, Integer> unbuilt(BuildJob j) {
        return unbuilt(j.plan(this), j);
    }

    /**
     * Part counts of the plan's steps the job has not done, skipped or launched: what it still has to be paid for. The
     * campus jobs' supply port and recycle bin are left out (given free once, {@link Builder#gift}), and so is any
     * step this game cannot place ({@link Builder#placeable}: the bin before its block is registered).
     */
    static Map<Integer, Integer> unbuilt(BuildPlan.Plan pl, BuildJob j) {
        Map<Integer, Integer> out = new TreeMap<>();
        if (pl == null) return out;
        for (int i = 0; i < pl.steps.size(); i++) {
            if (j.done(i) || j.skipped(i) || j.flying(i)) continue;
            BuildPlan.Step s = pl.steps.get(i);
            if (BuildPlan.empties(s.kind) || Builder.gift(s.part) && j.campusJob() || !Builder.placeable(s)) continue;
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
        // the caisson's digging and draining (0.12.0) go at the clearing's pace: the survey counted them as clears
        for (int i = 0; i < pl.steps.size(); i++)
            if (!job.done(i) && !job.skipped(i) && !BuildPlan.digs(pl.steps.get(i).kind)) rem[pl.steps.get(i).stage]++;
        long clears = Math.max(0, job.natural - (job.cleared - job.clearedAtSurvey))
            + Math.max(0, job.toFill - (job.filled - job.filledAtSurvey));
        eta = BuildPace.etaTicks(
            rem,
            job.campusJob() || job.grows(),
            CampusConfig.buildBlocksPerTick,
            clears,
            CampusConfig.buildClearPerTick);
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
        // 0.10.1: the recycle store replaced the spoils ("Spoils" is still read, once, and moved in)
        t.setTag("Recycle", recycle.write());
        t.setBoolean("Fb", freeBuild);
        t.setInteger("Pv", gradedVersion);
        // the grounds laid beyond the forum (0.15.0), only once there is one, and the neighbours the ring was laid with
        if (!laid.isEmpty()) t.setTag("Gl", grounds(laid));
        if (!laidWith.isEmpty()) t.setIntArray("Gn", BuildJob.centres(laidWith));
        int[] laidClaims = BuildJob.claims(laidWith);
        if (laidClaims.length > 0) t.setIntArray("Gq", laidClaims);
        // the boxes of the modules on no site the outer ring was laid round (0.15.0), only once there are any
        if (!laidBoxes.isEmpty()) t.setIntArray("Gk", BuildJob.boxes(laidBoxes, 4));
        NBTTagCompound d = new NBTTagCompound();
        for (Map.Entry<String, Long> e : declined.entrySet()) d.setLong(e.getKey(), e.getValue());
        t.setTag("Declined", d);
        if (resumeCheckAt >= 0) {
            t.setLong("Rc", resumeCheckAt);
            t.setString("Rj", resumeJob);
        }
        skin.write(t);
        tile.setTag("Campus", t);
    }

    /** Reads the campus from the nexus's tag; a tag without {@code Campus} is a legacy nexus. */
    public void readNBT(NBTTagCompound tile) {
        sites.clear();
        siteKinds.clear();
        queue.clear();
        declined.clear();
        laid.clear();
        laidWith.clear();
        laidBoxes.clear();
        registeredRadius = 0;
        registeredClaims = -1;
        job = null;
        loaded = false;
        needDirty = true;
        hintAt = 0;
        resumeCheckAt = -1;
        resumeJob = "";
        intake.setStackInSlot(0, null);
        instantJob = null;
        recycle.read(null);
        skin.read(null);
        if (tile == null || !tile.hasKey("Campus")) {
            active = false;
            hinted = false;
            skipBlocked = false;
            freeBuild = false;
            gradedVersion = 0;
            ledger.load(null, null);
            return;
        }
        NBTTagCompound t = tile.getCompoundTag("Campus");
        active = "active".equals(t.getString("St"));
        hinted = t.getBoolean("Hint");
        skipBlocked = t.getBoolean("Skip");
        freeBuild = t.getBoolean("Fb");
        gradedVersion = t.getInteger("Pv");
        readGrounds(t.getTagList("Gl", 8), laid);
        BuildJob.readCentres(t.getIntArray("Gn"), laidWith);
        BuildJob.readClaims(t.getIntArray("Gq"), laidWith);
        BuildJob.readFlat(t.getIntArray("Gk"), laidBoxes);
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
        recycle.read(t.getTagList("Recycle", 10));
        if (t.hasKey("Spoils")) {
            // the 27 slots of 0.10.0's spoils move into the recycle store (and are not written again)
            ItemStackHandler saved = new ItemStackHandler(0);
            saved.deserializeNBT(t.getCompoundTag("Spoils"));
            for (int i = 0; i < saved.getSlots(); i++) recycle.add(saved.getStackInSlot(i));
        }
        NBTTagCompound d = t.getCompoundTag("Declined");
        for (Object k : d.func_150296_c()) declined.put((String) k, d.getLong((String) k));
        if (t.hasKey("Rc")) {
            resumeCheckAt = t.getLong("Rc");
            resumeJob = t.getString("Rj");
        }
        skin.read(t);
    }

    // ---- the client copy

    /**
     * The small client copy for the nexus's description packet: the mode, 跳过受阻 and free building, the module list
     * of an active campus ({@code Md}, {@link ModuleState#write}), then with a job its key, plan key, site, plan
     * version, state, pause, stage and progress, when it was projected and finished, whether it is free, its top three
     * missing items, its blocked cells and the machines it will take down ({@code Bl}, {@code Te}: {@link FxCodec},
     * relative to the centre, at most 64 each) and the keep-out boxes its plan was made with. Sent on state changes
     * only; the progress numbers between them go over the network ({@link CampusNet#sendState}).
     */
    public void writeSync(NBTTagCompound t) {
        t.setBoolean("Ac", active);
        // the switches are the campus's, not the job's: they reach the client with or without a job
        t.setBoolean("Sk", skipBlocked);
        t.setBoolean("Fb", freeBuild);
        if (active) t.setTag("Md", ModuleState.write(modules()));
        // the outer ring's ground is laid (0.15.0): the masterplan and the projection show it as the campus's
        if (groundLaid(BuildPlan.OUTER)) t.setBoolean("Ol", true);
        // the neighbours the outer sites are judged against, with their claims, and the campus's own claims
        // (0.15.0): with the campus, not the job, so the masterplan draws the same split before, during and after the
        // outer establish whether or not the client has those nexuses loaded
        if (active && showsSplit()) {
            List<int[]> near = splitNeighbours();
            syncedNeighbours = signature(near);
            if (!near.isEmpty()) t.setIntArray("Ns", triples(near));
            int own = claims();
            if (own != 0) t.setByte("Oc", (byte) own);
        }
        // the sky datum of a dome job not over that builds the pod floating, current or queued (0.16.0): the
        // masterplan draws no feet under a pod queued to float behind another job
        int floatingJob = floatingDomeJob();
        if (floatingJob > 0) t.setInteger("Pq", floatingJob);
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
        t.setBoolean("Fe", free(job));
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
        t.setIntArray("Te", packed(job.machineCells(), c));
        t.setIntArray("Ko", BuildJob.boxes(job.keepOut));
        int[] ranged = BuildJob.boxes(job.keepOut, 6);
        if (ranged.length > 0) t.setIntArray("Kr", ranged);
        // the outer establish's neighbours (0.15.0): the client plans the same split, with their claims; an outer
        // establish that regains ground, and a retire job that lays the outer floor, plan as theirs do
        if (!job.neighbours.isEmpty()) t.setIntArray("Nb", BuildJob.centres(job.neighbours));
        int[] claims = BuildJob.claims(job.neighbours);
        if (claims.length > 0) t.setIntArray("Nc", claims);
        if (!job.regainFrom.isEmpty()) t.setIntArray("Nr", BuildJob.centres(job.regainFrom));
        int[] regainClaims = BuildJob.claims(job.regainFrom);
        if (regainClaims.length > 0) t.setIntArray("Nq", regainClaims);
        if (job.retireOuter) t.setBoolean("Ro", true);
        if (!job.patch.isEmpty()) t.setIntArray("Op", BuildJob.boxes(job.patch, 4));
        // the floating pod's datum and its open doors (0.16.0): the client plans the release and the floating pod
        job.form.write(t);
    }

    /**
     * The sky datum of the first job of the Echo Dome Generator not over (the current one, then the queue's) that
     * builds the pod floating ({@code Pq}, 0.16.0), or 0.
     */
    int floatingDomeJob() {
        for (BuildJob j : jobs()) if (!BuildState.ended(j.state) && j.floats()) return j.form().pod;
        return 0;
    }

    /**
     * Whether the client is told the neighbours the outer sites are judged against ({@code Ns}, 0.15.0): once the
     * nexus has grown to the phase the outer ring opens at, the ring is laid, or an outer establish is queued; older
     * campuses sync as before.
     */
    private boolean showsSplit() {
        if (groundLaid(BuildPlan.OUTER) || nexus.phase() >= SiteFamily.OUTER_DIAG.opens) return true;
        for (BuildJob j : jobs()) if (j.laysOuter()) return true;
        return false;
    }

    /** The neighbours {x, z} or {x, z, claims} flat as triples {x, z, claims} ({@code Ns}). */
    static int[] triples(List<int[]> near) {
        int[] out = new int[3 * near.size()];
        for (int i = 0; i < near.size(); i++) {
            int[] n = near.get(i);
            out[3 * i] = n[0];
            out[3 * i + 1] = n[1];
            out[3 * i + 2] = CampusSplit.claims(n);
        }
        return out;
    }

    /** Reads the triples of {@link #triples} into {x, z} or {x, z, claims} each. */
    static List<int[]> readTriples(int[] flat) {
        List<int[]> out = new ArrayList<>();
        if (flat == null) return out;
        for (int i = 0; i + 2 < flat.length; i += 3) out.add(
            flat[i + 2] != 0 ? new int[] { flat[i], flat[i + 1], flat[i + 2] } : new int[] { flat[i], flat[i + 1] });
        return out;
    }

    /** A list of neighbours as a string, to tell when it changed. */
    private static String signature(List<int[]> near) {
        StringBuilder s = new StringBuilder();
        for (int[] n : near) s.append(Arrays.toString(n));
        return s.toString();
    }

    /**
     * Tells the clients again when the neighbours the outer sites are judged against changed (0.15.0): a campus
     * placed or gone, a claim made or dropped, which change no state of this campus. Looked at with the registry,
     * every 100 ticks.
     */
    private void watchNeighbours() {
        if (!showsSplit()) return;
        if (!signature(splitNeighbours()).equals(syncedNeighbours)) stateChanged();
    }

    /** The plan keys of laid grounds as a list of strings ({@code Gl}, 0.15.0). */
    static NBTTagList grounds(Set<String> keys) {
        NBTTagList l = new NBTTagList();
        for (String k : keys) l.appendTag(new net.minecraft.nbt.NBTTagString(k));
        return l;
    }

    /** Reads the grounds a list from {@link #grounds} holds into {@code into}: only the known ground job keys. */
    static void readGrounds(NBTTagList l, Set<String> into) {
        if (l == null) return;
        for (int i = 0; i < l.tagCount(); i++) {
            String k = l.getStringTagAt(i);
            if (SiteFamily.OUTER_GROUND.equals(k) || SiteFamily.THIRD_GROUND.equals(k)) into.add(k);
        }
    }

    /** World cells {x, y, z} packed relative to the centre ({@link FxCodec}); cells too far out are left out. */
    private static int[] packed(List<int[]> cells, int[] c) {
        List<Integer> l = new ArrayList<>();
        for (int[] b : cells) {
            int dx = b[0] - c[0], dy = b[1] - c[1], dz = b[2] - c[2];
            if (FxCodec.fits(dx, dy, dz)) l.add(FxCodec.pack(dx, dy, dz));
        }
        int[] out = new int[l.size()];
        for (int i = 0; i < out.length; i++) out[i] = l.get(i);
        return out;
    }

    /** The client's copy of {@link #writeSync}. */
    public static final class View {

        public boolean active, skipBlocked;
        /** Free building for every job of the nexus ({@code Fb}), and whether the current job is free ({@code Fe}). */
        public boolean freeBuild, free;
        /** The modules the nexus can build and where they stand ({@code Md}); empty for a legacy campus. */
        public List<ModuleState> modules = new ArrayList<>();
        /**
         * The machines and chests the job will take down, packed like {@link #blocked} ({@code Te}, the yellow boxes).
         */
        public int[] machines = new int[0];
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
        /**
         * The neighbouring campuses' centres {x, z} (or {x, z, claims}) the job's plan was made with ({@code Nb} and
         * {@code Nc}, 0.15.0): the outer establish's, and a retire job's that lays the outer floor.
         */
        public List<int[]> neighbours = new ArrayList<>();
        /** The neighbours an outer establish that regains ground regains it from ({@code Nr}, 0.15.0). */
        public List<int[]> regainFrom = new ArrayList<>();
        /** Whether the retire job lays the outer ring's floor in its box too ({@code Ro}, 0.15.0). */
        public boolean retireOuter;
        /** The boxes an outer establish that patches the ring lays inside ({@code Op}, 0.15.0). */
        public List<int[]> patch = new ArrayList<>();
        /** Whether the outer establish has laid the outer ring's ground ({@code Ol}, 0.15.0, {@link #groundLaid}). */
        public boolean outerLaid;
        /**
         * What the job knows of the form its building takes ({@code Pf}, {@code Pd}, 0.16.0, {@link BuildJob.Form}):
         * the datum the release job releases, a floating pod's plan and the doors it leaves open.
         */
        public BuildJob.Form form = new BuildJob.Form();
        /**
         * The neighbours the campus's outer sites are judged against, {x, z} or {x, z, claims} ({@code Ns}, 0.15.0,
         * {@link Campus#splitNeighbours}): sent with the campus, job or not, so the masterplan draws the split and
         * signs the sites as the server offers them.
         */
        public List<int[]> split = new ArrayList<>();
        /** The outer diagonals the campus has claimed ({@code Oc}, 0.15.0, a bit per world quadrant). */
        public int claims;
        /**
         * The sky datum of a job of the Echo Dome Generator not over, current or queued, that builds the pod floating
         * ({@code Pq}, 0.16.0, {@link Campus#floatingDomeJob}), or 0: the masterplan draws no feet under it.
         */
        public int floatingJob;

        public void read(NBTTagCompound t) {
            active = t.getBoolean("Ac");
            skipBlocked = t.getBoolean("Sk");
            freeBuild = t.getBoolean("Fb");
            free = t.getBoolean("Fe");
            outerLaid = t.getBoolean("Ol");
            modules = ModuleState.read(t.getTagList("Md", 10));
            machines = t.getIntArray("Te");
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
            BuildJob.readBoxes(t, keepOut);
            neighbours = new ArrayList<>();
            BuildJob.readCentres(t.getIntArray("Nb"), neighbours);
            BuildJob.readClaims(t.getIntArray("Nc"), neighbours);
            regainFrom = new ArrayList<>();
            BuildJob.readCentres(t.getIntArray("Nr"), regainFrom);
            BuildJob.readClaims(t.getIntArray("Nq"), regainFrom);
            retireOuter = t.getBoolean("Ro");
            patch = new ArrayList<>();
            BuildJob.readFlat(t.getIntArray("Op"), patch);
            form = BuildJob.Form.read(t);
            // a repair picks the whole plan of a deep root's form (0.16.0): its key says so, as on the server
            form.whole = job.startsWith(BuildJob.REPAIR);
            split = readTriples(t.getIntArray("Ns"));
            claims = t.getByte("Oc") & 15;
            int pq = t.getInteger("Pq");
            floatingJob = DomeGeneratorShape.isDatum(pq) ? pq : 0;
        }

        /** Whether there is a job. */
        public boolean hasJob() {
            return job != null && !job.isEmpty();
        }

        /** Whether the campus records a pavilion built on the site (0.13.0, {@link ModuleState#pavilionBuilt}). */
        public boolean pavilionBuilt(int site) {
            return ModuleState.pavilionBuilt(modules, site);
        }

        /** Whether the campus records the gatehouse built on the gate (0.14.0, {@link ModuleState#gateBuilt}). */
        public boolean gateBuilt(int site) {
            return ModuleState.gateBuilt(modules, site);
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
