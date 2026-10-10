package com.fluxecho.campus;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.BuildState;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.NexusShape;

/**
 * One construction job of a nexus campus and how far it has got: which plan it builds (the establish job, the forum of
 * a 0.9.2 nexus, a module on a hall site, or a repair of one of those), where it is in its life
 * ({@link BuildState}), which stage it is in, which steps are done or skipped, how far the clearing has dug, which
 * cells are in flight, and the counts the GUI shows.
 * <p>
 * The plan itself is never saved: {@link #planFor} recomputes it from the job key, the nexus's position and front, the
 * {@link CampusPlan} seeded with the nexus controller's position, and the keep-out boxes saved with the job, so the
 * client can compute the same plan from the synced key ({@link Campus.View}). The done and skipped sets are indexes
 * into
 * that plan and are only valid for the plan version {@link #V} they were saved with.
 */
public final class BuildJob {

    /**
     * The version of the plans: raise it whenever {@link BuildPlan} or the module shapes change what a job builds, so a
     * job saved with older plans starts its bookkeeping over (its built cells come free) instead of misreading it.
     */
    public static final int V = 1;
    /** The most cells in flight at once, and the most blocked cells reported (the GUI, the red boxes, the save). */
    public static final int MAX_FLIGHTS = 256, MAX_BLOCKED = 64;
    /** The most blocked cells kept by position; beyond that they are only counted. */
    static final int MAX_TRACKED = 8192;
    /** The prefix of a repair job's key: {@code repair:establish} or {@code repair:<site>}. */
    public static final String REPAIR = "repair:";

    /**
     * A launched cell on its way: it was paid for and lands at {@link #land}. It remembers what it paid for (the part
     * and the cost scale), so its credit can go back to the ledger even when the plan it was launched from is gone (a
     * new plan version, a plan that shrank).
     */
    public static final class Flight {

        /** The step index in the plan. */
        public final int step;
        /** The world tick it lands (later when it had to wait for an entity or a chunk). */
        public long land;
        /** Whether its charge used a finished part (passed back to the ledger's refund). */
        public final boolean usedPart;
        /** Whether the ledger was charged at all (false for the free supply port). */
        public final boolean charged;
        /** The part it paid for ({@link com.fluxecho.logic.Parts} code), -1 when not known (saved before 0.10.0). */
        public final int part;
        /** The cost scale it was charged with, negative when not known (then the configured one is used). */
        public final double scale;
        /** How often it waited for an entity standing in its cell. */
        public int defers;

        public Flight(int step, long land, boolean usedPart, boolean charged) {
            this(step, land, usedPart, charged, -1, -1);
        }

        public Flight(int step, long land, boolean usedPart, boolean charged, int part, double scale) {
            this.step = step;
            this.land = land;
            this.usedPart = usedPart;
            this.charged = charged;
            this.part = part;
            this.scale = scale;
        }
    }

    // ---- what it builds
    String key, planKey;
    int site;
    int version = V;
    final List<int[]> keepOut = new ArrayList<>();

    // ---- where it is
    BuildState.State state = BuildState.initial();
    BuildState.Pause pause = BuildState.Pause.NONE;
    int stage;
    /** Whether a member pressed 开始 on it (or it was started before a re-survey). */
    boolean started;
    UUID startedBy;
    /** Whether it cleared or placed anything yet (the site can be moved until then). */
    boolean touched;
    /**
     * Whether its clearing was already done once and must not run again (a job started over by 修复 after it stopped
     * as incomplete); see {@link #clears}.
     */
    boolean keepClear;
    long projectedAt = -1, finishedAt = -1;

    // ---- progress
    final BitSet done = new BitSet(), skipped = new BitSet(), flying = new BitSet();
    final List<Flight> flights = new ArrayList<>();
    /** The clear column being dug and the next cell in it (Integer.MIN_VALUE: the column has not started). */
    int clearColumn, clearY = Integer.MIN_VALUE;
    /**
     * The lowest cell the current grading column fills (Integer.MIN_VALUE: not worked out yet; Integer.MAX_VALUE: the
     * column has no hole to fill).
     */
    int fillTo = Integer.MIN_VALUE;
    /** The first step of the current stage that is not done or skipped (a hint; never past an unfinished step). */
    int cursor;
    int cleared, natural, unloaded;
    /** Cells the last survey found to fill under the floor level, and cells filled so far. */
    int toFill, filled;
    /**
     * Blocked cells by position (at most {@link #MAX_TRACKED}), how many more were only counted, and their number when
     * not all are known (after a load).
     */
    final Set<Long> blocked = new LinkedHashSet<>();
    int blockedOverflow, blockedSaved;
    /** Waiting for the structure to form: since when (-1: not waiting), and how often it failed. */
    long formSince = -1;
    int formTries;

    // ---- transient
    private BuildPlan.Plan plan;
    private Map<Long, Integer> index;
    /** The survey's cursor: steps first, then the clear columns (cell by cell). */
    int surveyStep, surveyColumn, surveyY = Integer.MIN_VALUE;
    boolean surveyInit;
    /** {@link #cleared} and {@link #filled} when the last survey began (the estimate counts the work since). */
    int clearedAtSurvey, filledAtSurvey;

    private BuildJob(String key, String planKey, int site) {
        this.key = key;
        this.planKey = planKey;
        this.site = site;
    }

    /** The establish job of a freshly placed core, keeping out the boxes of the modules standing near it. */
    public static BuildJob establish(List<int[]> keepOut) {
        BuildJob j = new BuildJob(BuildPlan.ESTABLISH, BuildPlan.ESTABLISH, -1);
        j.setKeepOut(keepOut);
        return j;
    }

    /** The forum job of a 0.9.2 nexus, keeping out the boxes of the modules standing round it. */
    public static BuildJob forum(List<int[]> keepOut) {
        BuildJob j = new BuildJob(BuildPlan.FORUM, BuildPlan.FORUM, -1);
        j.setKeepOut(keepOut);
        return j;
    }

    /** A module job on a hall site. */
    public static BuildJob module(ModuleSpec spec, int site) {
        String k = spec.jobKey(site);
        return new BuildJob(k, k, site);
    }

    /** A repair of the nexus ({@code repair:establish}), the same plan as the establish job. */
    public static BuildJob repairNexus(List<int[]> keepOut) {
        BuildJob j = new BuildJob(REPAIR + BuildPlan.ESTABLISH, BuildPlan.ESTABLISH, -1);
        j.setKeepOut(keepOut);
        return j;
    }

    /** A repair of the module on a site ({@code repair:<site>}), the same plan as its module job. */
    public static BuildJob repairModule(ModuleSpec spec, int site) {
        return new BuildJob(REPAIR + site, spec.jobKey(site), site);
    }

    private void setKeepOut(List<int[]> boxes) {
        keepOut.clear();
        if (boxes != null) for (int[] b : boxes) if (b != null && b.length == 4) keepOut.add(b.clone());
    }

    // ---- identity

    /** The job key: {@code establish}, {@code forum}, {@code module:<module>@<site>}, {@code repair:...}. */
    public String key() {
        return key;
    }

    /** The key of the plan it builds: its own key, or for a repair the key of the job it repairs. */
    public String planKey() {
        return planKey;
    }

    /** The hall site of a module job or a module repair; -1 for the others. */
    public int site() {
        return site;
    }

    public int version() {
        return version;
    }

    public BuildState.State state() {
        return state;
    }

    public BuildState.Pause pause() {
        return pause;
    }

    public int stage() {
        return stage;
    }

    /** Whether a member has pressed 开始 on it. */
    public boolean started() {
        return started;
    }

    /** The member who pressed 开始, or null. */
    public UUID startedBy() {
        return startedBy;
    }

    /** Whether it has cleared or placed anything (the site is fixed from then on). */
    public boolean touched() {
        return touched;
    }

    /** World tick its survey ended and its projection appeared; -1 before. */
    public long projectedAt() {
        return projectedAt;
    }

    /** World tick it was done; -1 before. */
    public long finishedAt() {
        return finishedAt;
    }

    /** The keep-out boxes {x0, z0, x1, z1} its plan was made with (read-only). */
    public List<int[]> keepOut() {
        return Collections.unmodifiableList(keepOut);
    }

    /** Whether it builds the establish or forum plan (the campus round the nexus) rather than a module. */
    public boolean campusJob() {
        return BuildPlan.ESTABLISH.equals(planKey) || BuildPlan.FORUM.equals(planKey);
    }

    /** Whether its plan raises the nexus itself (the establish job and its repair). */
    public boolean raisesNexus() {
        return BuildPlan.ESTABLISH.equals(planKey);
    }

    /** Whether it is a repair. */
    public boolean repair() {
        return key.startsWith(REPAIR);
    }

    /**
     * Whether it works a clear column of its plan. A first build clears and grades them all. A repair (and a job
     * started over by 修复) puts the structure's cells right and nothing else: what grew on the campus or what the
     * players built there since the first clearing stays. Only the columns over the nexus's dais (not grading) are
     * cleared again by a repair of the nexus, as nothing may stand inside it.
     */
    public boolean clears(BuildPlan.Column c) {
        if (!repair() && !keepClear) return true;
        return raisesNexus() && !c.grade;
    }

    /** The module key of a module job or a module repair; null for the others. */
    public String module() {
        return ModuleSpecs.moduleOf(planKey);
    }

    /** Its name for chat and the GUI, such as 建造回响书库 or 修复通量中枢. */
    public IChatComponent name() {
        return name(key, planKey);
    }

    /** The name of a job from its key and plan key (the client has both in {@link Campus.View}). */
    public static IChatComponent name(String key, String planKey) {
        if (key == null || key.isEmpty()) return new ChatComponentTranslation("fluxecho.build.gui.none");
        String module = ModuleSpecs.moduleOf(planKey);
        if (key.startsWith(REPAIR)) {
            IChatComponent what = module != null ? new ChatComponentTranslation("fluxecho.module." + module)
                : new ChatComponentTranslation("tile.fluxecho.nexus.name");
            return new ChatComponentTranslation("fluxecho.build.job.repair", what);
        }
        if (module != null) return new ChatComponentTranslation(
            "fluxecho.build.job.module",
            new ChatComponentTranslation("fluxecho.module." + module));
        return new ChatComponentTranslation(
            BuildPlan.FORUM.equals(key) ? "fluxecho.build.job.forum" : "fluxecho.build.job.establish");
    }

    // ---- the plan

    /**
     * The plan of a job, the same on the server and the client: {@code establish}, {@code forum} or
     * {@code module:<module>@<site>} for a nexus whose base centre is {@code (cx, y0, cz)}, whose front is
     * {@code (fx, fz)} and whose controller is at {@code ctrl}, with the campus plan seeded from that controller. Null
     * when the plan key names a module this game does not know.
     */
    public static BuildPlan.Plan planFor(String planKey, CampusPlan cp, int cx, int y0, int cz, int fx, int fz,
        int ctrlX, int ctrlY, int ctrlZ, List<int[]> keepOut) {
        if (BuildPlan.ESTABLISH.equals(planKey))
            return BuildPlan.establish(cp, cx, y0, cz, fx, fz, NexusShape.PHASE_1, ctrlX, ctrlY, ctrlZ, keepOut);
        if (BuildPlan.FORUM.equals(planKey)) return BuildPlan.forum(cp, cx, y0, cz, fx, fz, keepOut);
        ModuleSpec spec = ModuleSpecs.ofJob(planKey);
        int site = ModuleSpecs.siteOf(planKey);
        if (spec == null || !spec.hasSite(site)) return null;
        return spec.plan(cp, site, cx, y0, cz, fx, fz);
    }

    /** Its plan for the campus's nexus (computed once, then kept). */
    public BuildPlan.Plan plan(Campus c) {
        if (plan == null) {
            int[] ce = c.centre();
            int[] f = c.front();
            int[] ctrl = c.controller();
            plan = planFor(planKey, c.plan(), ce[0], ce[1], ce[2], f[0], f[1], ctrl[0], ctrl[1], ctrl[2], keepOut);
            index = null;
        }
        return plan;
    }

    /** The step index at a world cell, or -1. */
    int stepAt(int x, int y, int z) {
        if (plan == null) return -1;
        if (index == null) {
            index = new HashMap<>();
            for (int i = 0; i < plan.steps.size(); i++) {
                BuildPlan.Step s = plan.steps.get(i);
                index.put(pos(s.x, s.y, s.z), i);
            }
        }
        Integer i = index.get(pos(x, y, z));
        return i == null ? -1 : i;
    }

    /**
     * Moves a module job that has not touched anything to another site: a new key and plan, a new survey. The move
     * takes back a member's 开始 (and a pause): the new site's projection must be seen and agreed to before anything
     * there is cleared.
     */
    void moveTo(ModuleSpec spec, int newSite) {
        site = newSite;
        key = spec.jobKey(newSite);
        planKey = key;
        started = false;
        startedBy = null;
        pause = BuildState.Pause.NONE;
        restart(false);
    }

    /**
     * Forgets the bookkeeping (done, skipped, clearing, counts) and starts the survey again, for a new plan or a
     * repair of the same plan; cells already right come free again. Flights are left to the caller.
     *
     * @param keepClear whether the clearing was already done and must not run again ({@link #clears})
     */
    void restart(boolean keepClear) {
        plan = null;
        index = null;
        done.clear();
        skipped.clear();
        stage = 0;
        cursor = 0;
        clearColumn = 0;
        clearY = Integer.MIN_VALUE;
        fillTo = Integer.MIN_VALUE;
        this.keepClear = keepClear;
        cleared = natural = unloaded = toFill = filled = 0;
        clearBlocked();
        formSince = -1;
        formTries = 0;
        projectedAt = -1;
        version = V;
        resurvey();
    }

    /** Starts a new survey (keeping the started flag and the pause reason for its end). */
    void resurvey() {
        if (!BuildState.ended(state)) state = BuildState.resurvey(state);
        surveyInit = false;
    }

    /**
     * 暂停 by a member: a started job is paused for the player (only 开始 lifts it); one that is being surveyed keeps
     * the player's pause for the end of its survey ({@link BuildState#surveyed} then gives PAUSED). Returns whether the
     * job is now paused by the player; false when it cannot be paused now (not started yet, or over).
     */
    boolean playerPause() {
        BuildState.Pause me = BuildState.Pause.PLAYER;
        if (BuildState.ended(state)) return false;
        if (state == BuildState.State.SURVEY) {
            if (!started) return false;
            pause = me;
            return true;
        }
        if (state == BuildState.State.PAUSED && pause == me) return true;
        if (!BuildState.canPause(state, pause, me)) return false;
        state = BuildState.pause(state, pause, me);
        pause = me;
        return true;
    }

    // ---- progress

    public boolean done(int step) {
        return done.get(step);
    }

    public boolean skipped(int step) {
        return skipped.get(step);
    }

    public boolean flying(int step) {
        return flying.get(step);
    }

    /** Steps done (placed by the builder or found already right). */
    public int placed() {
        return done.cardinality();
    }

    public int skippedCount() {
        return skipped.cardinality();
    }

    public int cleared() {
        return cleared;
    }

    /** Natural cells the last survey found to clear. */
    public int natural() {
        return natural;
    }

    public int unloaded() {
        return unloaded;
    }

    /** Cells the last survey found to fill under the floor level (grading). */
    public int toFill() {
        return toFill;
    }

    /** Cells filled under the floor level so far. */
    public int filled() {
        return filled;
    }

    public int blockedCount() {
        return Math.max(blocked.size() + blockedOverflow, blockedSaved);
    }

    /** Up to {@link #MAX_BLOCKED} blocked world cells {x, y, z}. */
    public List<int[]> blockedCells() {
        List<int[]> out = new ArrayList<>();
        for (long p : blocked) {
            if (out.size() >= MAX_BLOCKED) break;
            out.add(unpack(p));
        }
        return out;
    }

    /** The cells in flight (read-only). */
    public List<Flight> flights() {
        return Collections.unmodifiableList(flights);
    }

    /** Notes a blocked cell; beyond {@link #MAX_TRACKED} known cells only the count grows. */
    void addBlocked(int x, int y, int z) {
        long p = pos(x, y, z);
        if (blocked.size() < MAX_TRACKED) blocked.add(p);
        else if (!blocked.contains(p)) blockedOverflow++;
    }

    void unblock(int x, int y, int z) {
        blocked.remove(pos(x, y, z));
    }

    /** Forgets the blocked cells (a new survey counts them again). */
    void clearBlocked() {
        blocked.clear();
        blockedOverflow = 0;
        blockedSaved = 0;
    }

    /** A hash of the blocked cells the client is shown ({@link #blockedCells}), to send them again when they change. */
    long blockedShownHash() {
        long h = 17;
        int n = 0;
        for (long p : blocked) {
            if (n++ >= MAX_BLOCKED) break;
            h = h * 31 + p;
        }
        return h * 31 + n;
    }

    // ---- positions

    /** A world cell packed into a long (x and z 26 bits, y 12 bits). */
    public static long pos(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (y & 0xFFFL);
    }

    public static int[] unpack(long p) {
        int x = (int) (p >> 38), z = (int) (p << 26 >> 38), y = (int) (p & 0xFFF);
        if (y >= 2048) y -= 4096;
        return new int[] { x, y, z };
    }

    // ---- saving

    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("K", key);
        t.setString("Pk", planKey);
        t.setInteger("S", site);
        t.setInteger("V", version);
        t.setString("St", state.name());
        t.setString("Ps", pause.name());
        t.setInteger("Sg", stage);
        t.setBoolean("Go", started);
        if (startedBy != null) {
            t.setLong("ByM", startedBy.getMostSignificantBits());
            t.setLong("ByL", startedBy.getLeastSignificantBits());
        }
        t.setBoolean("To", touched);
        t.setBoolean("Kc", keepClear);
        t.setLong("Pa", projectedAt);
        t.setLong("Ft", finishedAt);
        t.setIntArray("Ko", boxes(keepOut));
        t.setIntArray("D", bits(done));
        t.setIntArray("Sk", bits(skipped));
        t.setInteger("Cc", clearColumn);
        t.setInteger("Cy", clearY);
        t.setInteger("Ct", fillTo);
        t.setInteger("Cl", cleared);
        t.setInteger("Na", natural);
        t.setInteger("Un", unloaded);
        t.setInteger("Tf", toFill);
        t.setInteger("Fd", filled);
        t.setInteger("Bc", blockedCount());
        int[] bl = new int[3 * Math.min(MAX_BLOCKED, blocked.size())];
        int i = 0;
        for (long p : blocked) {
            if (i >= bl.length) break;
            int[] c = unpack(p);
            bl[i++] = c[0];
            bl[i++] = c[1];
            bl[i++] = c[2];
        }
        t.setIntArray("Bl", bl);
        NBTTagList fl = new NBTTagList();
        for (Flight f : flights) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("I", f.step);
            c.setLong("L", f.land);
            c.setBoolean("U", f.usedPart);
            c.setBoolean("C", f.charged);
            c.setInteger("P", f.part);
            c.setDouble("Sc", f.scale);
            c.setByte("D", (byte) f.defers);
            fl.appendTag(c);
        }
        t.setTag("Fl", fl);
        t.setLong("Fs", formSince);
        t.setInteger("Fr", formTries);
        return t;
    }

    /** A saved job, or null when the tag holds none. */
    public static BuildJob read(NBTTagCompound t) {
        if (t == null || !t.hasKey("K")) return null;
        BuildJob j = new BuildJob(t.getString("K"), t.getString("Pk"), t.getInteger("S"));
        if (j.planKey.isEmpty()) j.planKey = j.key;
        j.version = t.getInteger("V");
        j.state = parse(BuildState.State.class, t.getString("St"), BuildState.State.SURVEY);
        j.pause = parse(BuildState.Pause.class, t.getString("Ps"), BuildState.Pause.NONE);
        j.stage = t.getInteger("Sg");
        j.started = t.getBoolean("Go");
        if (t.hasKey("ByM")) j.startedBy = new UUID(t.getLong("ByM"), t.getLong("ByL"));
        j.touched = t.getBoolean("To");
        j.keepClear = t.getBoolean("Kc");
        j.projectedAt = t.getLong("Pa");
        j.finishedAt = t.getLong("Ft");
        int[] ko = t.getIntArray("Ko");
        for (int i = 0; i + 3 < ko.length; i += 4) j.keepOut.add(new int[] { ko[i], ko[i + 1], ko[i + 2], ko[i + 3] });
        unbits(t.getIntArray("D"), j.done);
        unbits(t.getIntArray("Sk"), j.skipped);
        j.clearColumn = t.getInteger("Cc");
        j.clearY = t.hasKey("Cy") ? t.getInteger("Cy") : Integer.MIN_VALUE;
        j.fillTo = t.hasKey("Ct") ? t.getInteger("Ct") : Integer.MIN_VALUE;
        j.cleared = t.getInteger("Cl");
        j.natural = t.getInteger("Na");
        j.unloaded = t.getInteger("Un");
        j.toFill = t.getInteger("Tf");
        j.filled = t.getInteger("Fd");
        j.blockedSaved = t.getInteger("Bc");
        int[] bl = t.getIntArray("Bl");
        for (int i = 0; i + 2 < bl.length; i += 3) j.addBlocked(bl[i], bl[i + 1], bl[i + 2]);
        NBTTagList fl = t.getTagList("Fl", 10);
        for (int i = 0; i < fl.tagCount() && j.flights.size() < MAX_FLIGHTS; i++) {
            NBTTagCompound c = fl.getCompoundTagAt(i);
            Flight f = new Flight(
                c.getInteger("I"),
                c.getLong("L"),
                c.getBoolean("U"),
                c.getBoolean("C"),
                c.hasKey("P") ? c.getInteger("P") : -1,
                c.hasKey("Sc") ? c.getDouble("Sc") : -1);
            f.defers = c.getByte("D");
            if (f.step < 0 || j.flying.get(f.step)) continue;
            j.flights.add(f);
            j.flying.set(f.step);
        }
        j.formSince = t.hasKey("Fs") ? t.getLong("Fs") : -1;
        j.formTries = t.getInteger("Fr");
        return j;
    }

    /**
     * Drops the flights whose steps the plan does not have (a plan that shrank), returning them; the caller refunds
     * them by the part each remembers.
     */
    List<Flight> dropStrayFlights(int steps) {
        List<Flight> out = new ArrayList<>();
        for (Iterator<Flight> it = flights.iterator(); it.hasNext();) {
            Flight f = it.next();
            if (f.step < steps) continue;
            out.add(f);
            flying.clear(f.step);
            it.remove();
        }
        return out;
    }

    static int[] boxes(List<int[]> l) {
        int[] out = new int[4 * l.size()];
        for (int i = 0; i < l.size(); i++) System.arraycopy(l.get(i), 0, out, 4 * i, 4);
        return out;
    }

    private static int[] bits(BitSet b) {
        long[] w = b.toLongArray();
        int[] out = new int[2 * w.length];
        for (int i = 0; i < w.length; i++) {
            out[2 * i] = (int) w[i];
            out[2 * i + 1] = (int) (w[i] >>> 32);
        }
        return out;
    }

    private static void unbits(int[] a, BitSet into) {
        long[] w = new long[a.length / 2];
        for (int i = 0; i < w.length; i++) w[i] = (a[2 * i] & 0xFFFFFFFFL) | ((long) a[2 * i + 1] << 32);
        into.clear();
        into.or(BitSet.valueOf(w));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallback;
        }
    }
}
