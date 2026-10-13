package com.fluxecho.campus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntPredicate;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.BuildState;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.NexusShape;
import com.fluxecho.logic.SiteFamily;

/**
 * One construction job of a nexus campus and how far it has got: which plan it builds (the establish job, the forum of
 * a 0.9.2 nexus, a module on a site, a repair of one of those, the 0.10.1 refresh of a campus laid out by an older
 * plan, since 0.12.0 the growth of the nexus to its next phase, since 0.14.1 the retirement of a docked 0.9.2 hall, or
 * since 0.15.0 the outer establish that lays the outer ring's ground), where it is in its life ({@link BuildState}),
 * which stage it is in, which steps are done or skipped, how far the clearing has dug, which cells are in flight, and
 * the counts the GUI shows.
 * <p>
 * The plan itself is never saved: {@link #planFor} recomputes it from the job key, the nexus's position and front, the
 * {@link CampusPlan} seeded with the nexus controller's position, the keep-out boxes saved with the job and (0.15.0,
 * the outer establish) the neighbouring campuses' centres saved with it, so the client can compute the same plan from
 * the synced key ({@link Campus.View}). The done and skipped sets are indexes into that plan and are only valid for
 * the plan version {@link #V} they were saved with.
 */
public final class BuildJob {

    /**
     * The version of the plans: raise it whenever {@link BuildPlan} or the module shapes change what a job builds, so a
     * job saved with older plans starts its bookkeeping over (its built cells come free) instead of misreading it.
     * 2 (0.10.1): the light wells lie flush in the floor layer, the recycle bin stands beside the supply port, and a
     * module's clear box grades its floor. 3 (0.14.1): the Eco Dome's plan lost the soft grass step over its crown
     * stair, which moves every later step of it.
     */
    public static final int V = 3;
    /**
     * The version of the campus grading the 整修园区 offer (0.10.1) holds a campus to, saved as its {@code Pv}: 2, the
     * floor, wells and fixtures of 0.10.1's forum plan. It moves only when those change, not with every {@link #V}
     * (0.14.1): before, raising {@code V} for a module's plan would have offered every campus a renovation again.
     */
    public static final int GRADE_V = 2;
    /** The most cells in flight at once, and the most blocked cells reported (the GUI, the red boxes, the save). */
    public static final int MAX_FLIGHTS = 256, MAX_BLOCKED = 64;
    /** The most blocked cells kept by position; beyond that they are only counted. */
    static final int MAX_TRACKED = 8192;
    /** The prefix of a repair job's key: {@code repair:establish} or {@code repair:<site>}. */
    public static final String REPAIR = "repair:";
    /**
     * The key of the refresh job (0.10.1, 整修园区): the forum plan once more over a campus whose establish or forum job
     * finished under an older plan version, so its floor, wells and fixtures are brought up to the current plan.
     */
    public static final String REFRESH = "refresh";
    /**
     * The prefix of a retire job's key (0.14.1, {@link #retire}): {@code retire:<slot>}, the job and its plan key
     * alike.
     */
    public static final String RETIRE = BuildPlan.RETIRE + ":";

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
    /**
     * The centres {x, z} of the other campuses near enough to share the outer ring's ground (0.15.0, the outer
     * establish, {@link com.fluxecho.logic.CampusSplit}): its plan grades and paves only its own side of the split.
     * Each is {x, z, claims} with the outer diagonals that campus had claimed (saved apart, {@code Nc}, only when
     * one has). Saved ({@code Nb}) and synced, as the keep-out boxes are, so both sides make the same plan; empty for
     * every other job but a retire job on a campus whose outer ring is laid ({@link #retireOuter}).
     */
    final List<int[]> neighbours = new ArrayList<>();
    /**
     * The neighbours the outer ring was laid with, for an outer establish that only regains the ground they had
     * ({@link BuildPlan#outer} with {@code regainFrom}, 0.15.0), {x, z} or {x, z, claims}: saved ({@code Nr}, their
     * claims {@code Nq}) and synced; empty for every other job, the first outer establish too.
     */
    final List<int[]> regainFrom = new ArrayList<>();
    /**
     * Whether a retire job lays the outer ring's floor inside its box as well (0.15.0, {@link BuildPlan#retire} with
     * {@code outerLaid}): offered once the outer ring was laid. Saved ({@code Ro}) and synced only when set, so a
     * retire job saved by 0.14.2 makes the same plan as it did.
     */
    boolean retireOuter;
    /**
     * The boxes {x0, z0, x1, z1} an outer establish that only patches the ring lays inside (0.15.0,
     * {@link BuildPlan#outer} with {@code only}, {@link #outerPatch}): the ground a module that stood on no site of
     * the campus kept the ring out of and has left since. Saved ({@code Op}) and synced; empty for every other job.
     */
    final List<int[]> patch = new ArrayList<>();

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
    /**
     * Whether it is free (0.10.1): started by a player in creative mode, or made free by an operator. A free job takes
     * no materials and no EU, and a module job needs no core in the ledger before 开始.
     */
    boolean free;

    // ---- progress
    final BitSet done = new BitSet(), skipped = new BitSet(), flying = new BitSet();
    final List<Flight> flights = new ArrayList<>();
    /** The clear column being dug and the next cell in it (Integer.MIN_VALUE: the column has not started). */
    int clearColumn, clearY = Integer.MIN_VALUE;
    /** The first step of the current stage that is not done or skipped (a hint; never past an unfinished step). */
    int cursor;
    int cleared, natural, unloaded;
    /** Cells the last survey found to fill under the floor level, and cells filled so far. */
    int toFill, filled;
    /**
     * Tile entities that are not ores (machines, chests) the last survey found the job will take down, and up to
     * {@link #MAX_BLOCKED} of their cells (the projection's yellow boxes); a cell is forgotten once taken down.
     */
    int machines;
    final Set<Long> machineCells = new LinkedHashSet<>();
    /**
     * Blocked cells by position (at most {@link #MAX_TRACKED}), how many more were only counted, and their number when
     * not all are known (after a load).
     */
    final Set<Long> blocked = new LinkedHashSet<>();
    int blockedOverflow, blockedSaved;
    /** Waiting for the structure to form: since when (-1: not waiting), and how often it failed. */
    long formSince = -1;
    int formTries;
    /**
     * Grading cells (packed, {@link #pos}) left open because an entity stood in them, and the floor tiles and fixtures
     * (SOFT steps) that gave way to one and were skipped (0.10.1). The builder gives both another try in a last
     * touch-up pass before the job ends, so no floor cell stays open because a player or an animal stood there; it
     * waits for them since {@link #touchUpSince} (-1: not waiting) and gives up after a while. Saved ({@code Tu},
     * {@code Gw}, {@code Ts}).
     */
    final Set<Long> touchUp = new LinkedHashSet<>();
    final BitSet gaveWay = new BitSet();
    long touchUpSince = -1;

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

    /** A repair of a phase-I nexus ({@code repair:establish}), the same plan as the establish job. */
    public static BuildJob repairNexus(List<int[]> keepOut) {
        return repairNexus(keepOut, 1);
    }

    /**
     * A repair of the nexus ({@code repair:establish}) as it has grown (0.12.0): the establish plan of its phase
     * ({@link BuildPlan#establishKey}), so a grown nexus is put back with its stages and its lift pad, never as the
     * phase-I nexus.
     */
    public static BuildJob repairNexus(List<int[]> keepOut, int phase) {
        BuildJob j = new BuildJob(REPAIR + BuildPlan.ESTABLISH, BuildPlan.establishKey(phase), -1);
        j.setKeepOut(keepOut);
        return j;
    }

    /**
     * The growth of a formed nexus to {@code phase} (0.12.0, {@code grow:<phase>}, {@link BuildPlan#grow}): the stage
     * of that phase, and at phase II the light-lift pad; it ends once the nexus stands as the new phase.
     */
    public static BuildJob grow(int phase) {
        String k = BuildPlan.growKey(phase);
        return new BuildJob(k, k, -1);
    }

    /** A repair of the module on a site ({@code repair:<site>}), the same plan as its module job. */
    public static BuildJob repairModule(ModuleSpec spec, int site) {
        return new BuildJob(REPAIR + site, spec.jobKey(site), site);
    }

    /**
     * The refresh of a campus laid out by an older plan ({@link #REFRESH}, 0.10.1): the forum plan (grading, floor,
     * pylons, the supply port and the recycle bin, no nexus), keeping out the boxes of the modules standing round it.
     * It clears and grades like a first build; what already stands right comes free.
     */
    public static BuildJob refresh(List<int[]> keepOut) {
        BuildJob j = new BuildJob(REFRESH, BuildPlan.FORUM, -1);
        j.setKeepOut(keepOut);
        return j;
    }

    /**
     * The retire job of the docked 0.9.2 library hall on an inner ring slot (0.14.1, {@code retire:<slot>},
     * {@link BuildPlan#retire}): it takes the hall down into the recycle store and gives its ground back to the forum,
     * so the city module whose site it stood on can be built. It waits for 开始 like any job. Its one box
     * {x0, z0, x1, z1, yFrom, yTo} is the ground it works, not a box it keeps out of: the hall's keep-out columns over
     * the hall's heights. It travels in the job's keep-out list, so it is saved and reaches the client as every job's
     * boxes do and both sides make the same plan. Its site is the slot, which the 建造 page names.
     */
    public static BuildJob retire(int slot, int[] box) {
        return retire(slot, box, false, null);
    }

    /**
     * {@link #retire(int, int[])} on a campus whose outer ring is laid ({@code outerLaid}, 0.15.0): the job also lays
     * the outer ring's floor inside its box, which the outer establish kept out of while the hall stood, leaving the
     * ground of the {@code neighbours} (their centres {x, z}, or {x, z, claims}) to them.
     */
    public static BuildJob retire(int slot, int[] box, boolean outerLaid, List<int[]> neighbours) {
        String k = BuildPlan.retireKey(slot);
        BuildJob j = new BuildJob(k, k, slot);
        j.setKeepOut(Collections.singletonList(box));
        j.retireOuter = outerLaid;
        if (outerLaid) j.setNeighbours(neighbours);
        return j;
    }

    /**
     * Makes a retire job's plan again with the hall's box, the outer ring's state and the neighbours of now (0.15.0):
     * the outer ring may have been laid since the job was offered. Only for a job that has touched nothing and has
     * nothing in flight; returns whether anything changed (the job then starts its survey over).
     */
    boolean retargetRetire(int[] box, boolean outerLaid, List<int[]> centres) {
        if (touched || !flights.isEmpty() || BuildState.ended(state)) return false;
        List<int[]> near = new ArrayList<>();
        if (outerLaid && centres != null) for (int[] c : centres) if (c != null && c.length >= 2) near.add(centre(c));
        List<int[]> boxes = Collections.singletonList(box);
        if (outerLaid == retireOuter && sameBoxes(boxes, keepOut) && sameBoxes(near, neighbours)) return false;
        setKeepOut(boxes);
        setNeighbours(near);
        retireOuter = outerLaid;
        restart(keepClear);
        return true;
    }

    /**
     * The outer establish of a campus whose ring is laid, run again only to regain ground (0.15.0,
     * {@link BuildPlan#outer} with {@code regainFrom}): a neighbour whose core was broken after the ring was laid
     * leaves its side of the split, which this job grades and paves; it touches nothing the first one laid.
     *
     * @param laidWith the neighbours the outer ring was laid with
     */
    public static BuildJob outerRegain(List<int[]> keepOut, List<int[]> neighbours, List<int[]> laidWith) {
        BuildJob j = outer(keepOut, neighbours);
        if (laidWith != null) for (int[] c : laidWith) if (c != null && c.length >= 2) j.regainFrom.add(centre(c));
        return j;
    }

    /**
     * The outer establish of a campus whose ring is laid, run again only inside the boxes a module that stood on no
     * site of the campus kept the ring out of and has left since (0.15.0, {@link BuildPlan#outer} with {@code only}):
     * a 0.9.2 hall broken by hand, a stray core taken away, another campus's building gone. It grades and paves the
     * ring's columns in those boxes that the split leaves to the campus and nothing standing keeps out now.
     *
     * @param boxes the vacated boxes {x0, z0, x1, z1}
     */
    public static BuildJob outerPatch(List<int[]> keepOut, List<int[]> neighbours, List<int[]> boxes) {
        BuildJob j = outer(keepOut, neighbours);
        if (boxes != null)
            for (int[] b : boxes) if (b != null && b.length >= 4) j.patch.add(new int[] { b[0], b[1], b[2], b[3] });
        return j;
    }

    /** Whether it is an outer establish that only lays the ring inside vacated boxes again (0.15.0). */
    public boolean patches() {
        return laysOuter() && !patch.isEmpty();
    }

    /** The boxes an outer establish that patches the ring lays inside (read-only; empty for the others). */
    public List<int[]> patch() {
        return Collections.unmodifiableList(patch);
    }

    /** A neighbour's centre with its claims, as the plans take it: {x, z} or {x, z, claims}. */
    private static int[] centre(int[] c) {
        return c.length >= 3 && c[2] != 0 ? new int[] { c[0], c[1], c[2] } : new int[] { c[0], c[1] };
    }

    /** Whether it is an outer establish that only regains the ground of neighbours that went (0.15.0). */
    public boolean regains() {
        return laysOuter() && !regainFrom.isEmpty();
    }

    /** Whether it is a retire job that lays the outer ring's floor in its box too (0.15.0). */
    public boolean retiresOuter() {
        return retires() && retireOuter;
    }

    /** The neighbours an outer establish that regains ground regains it from (read-only; empty for the others). */
    public List<int[]> regainFrom() {
        return Collections.unmodifiableList(regainFrom);
    }

    /**
     * The outer establish (0.15.0, {@code establish:outer}, {@link BuildPlan#outer}): it grades the outer ring's
     * annulus and paves its streets, promenade, avenues, outer gate and allées, keeping out the boxes of the modules
     * standing round the nexus and leaving the ground nearer to a neighbouring campus ({@code neighbours}, their
     * centres {x, z}) to that one. It waits for 开始 like any job and raises nothing: it is done once its last stage is.
     */
    public static BuildJob outer(List<int[]> keepOut, List<int[]> neighbours) {
        BuildJob j = new BuildJob(BuildPlan.OUTER, BuildPlan.OUTER, -1);
        j.setKeepOut(keepOut);
        j.setNeighbours(neighbours);
        return j;
    }

    private void setNeighbours(List<int[]> centres) {
        neighbours.clear();
        if (centres != null) for (int[] c : centres) if (c != null && c.length >= 2) neighbours.add(centre(c));
    }

    private void setKeepOut(List<int[]> boxes) {
        keepOut.clear();
        if (boxes != null) for (int[] b : boxes) if (keepOutBox(b)) keepOut.add(b.clone());
    }

    /**
     * Whether a keep-out box has a form a plan takes: {x0, z0, x1, z1} (whole columns) or, since 0.12.0,
     * {x0, z0, x1, z1, yFrom, yTo} (the columns over those heights only).
     */
    static boolean keepOutBox(int[] b) {
        return b != null && (b.length == 4 || b.length == 6);
    }

    /**
     * Makes the plan again with the keep-out boxes of now (0.10.1): a campus job is made with the boxes of the modules
     * loaded when it was offered, and 开始 may come much later, when more of them are loaded (or a module was built
     * meanwhile). Only for a job that has touched nothing and has nothing in flight; returns whether the boxes changed,
     * and then the job starts its survey over (keeping whether it was started).
     */
    boolean retarget(List<int[]> boxes) {
        return retarget(boxes, neighbours);
    }

    /**
     * {@link #retarget(List)} with the neighbouring campuses of now as well (0.15.0, the outer establish): a campus
     * may have been placed or have gone since the job was offered. Returns whether either changed (the job then starts
     * its survey over).
     */
    boolean retarget(List<int[]> boxes, List<int[]> centres) {
        if (touched || !flights.isEmpty() || BuildState.ended(state)) return false;
        List<int[]> next = new ArrayList<>();
        if (boxes != null) for (int[] b : boxes) if (keepOutBox(b)) next.add(b);
        List<int[]> near = new ArrayList<>();
        if (centres != null) for (int[] c : centres) if (c != null && c.length >= 2) near.add(centre(c));
        if (sameBoxes(next, keepOut) && sameBoxes(near, neighbours)) return false;
        setKeepOut(next);
        setNeighbours(near);
        restart(keepClear);
        return true;
    }

    /** Whether two lists hold the same boxes, in any order. */
    static boolean sameBoxes(List<int[]> a, List<int[]> b) {
        if (a.size() != b.size()) return false;
        List<String> x = new ArrayList<>(), y = new ArrayList<>();
        for (int[] k : a) x.add(Arrays.toString(k));
        for (int[] k : b) y.add(Arrays.toString(k));
        Collections.sort(x);
        Collections.sort(y);
        return x.equals(y);
    }

    // ---- identity

    /**
     * The job key: {@code establish}, {@code forum}, {@code module:<module>@<site>}, {@code repair:...}, and since
     * 0.14.1 {@code retire:<slot>}.
     */
    public String key() {
        return key;
    }

    /** The key of the plan it builds: its own key, or for a repair the key of the job it repairs. */
    public String planKey() {
        return planKey;
    }

    /**
     * The site of a module job or a module repair, the inner ring slot of a retire job (0.14.1); -1 for the others.
     */
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

    /**
     * The keep-out boxes its plan was made with (read-only): {x0, z0, x1, z1}, or {x0, z0, x1, z1, yFrom, yTo} for a
     * height-ranged one (0.12.0).
     */
    public List<int[]> keepOut() {
        return Collections.unmodifiableList(keepOut);
    }

    /**
     * The centres {x, z} of the neighbouring campuses its plan leaves their side of the split to (0.15.0, the outer
     * establish; read-only, empty for every other job).
     */
    public List<int[]> neighbours() {
        return Collections.unmodifiableList(neighbours);
    }

    /** Whether it is the outer establish (0.15.0, {@code establish:outer}): it lays the outer ring's ground. */
    public boolean laysOuter() {
        return BuildPlan.OUTER.equals(planKey);
    }

    /**
     * Whether it grades the ground the way the campus jobs do, its clearing clipped to {@code buildClearHeight}: the
     * establish and forum plans (and their repairs and the refresh), and since 0.15.0 the outer establish.
     */
    public boolean grades() {
        return campusJob() || laysOuter();
    }

    /** Whether it builds the establish or forum plan (the campus round the nexus) rather than a module. */
    public boolean campusJob() {
        return raisesNexus() || BuildPlan.FORUM.equals(planKey);
    }

    /** Whether its plan raises the nexus itself (the establish job and its repair, at any phase). */
    public boolean raisesNexus() {
        return BuildPlan.establishPhase(planKey) > 0;
    }

    /** Whether it grows the nexus to its next phase (0.12.0, {@code grow:<phase>}). */
    public boolean grows() {
        return BuildPlan.growPhase(planKey) > 0;
    }

    /** The phase a growth job grows the nexus to, or 0 for any other job. */
    public int growPhase() {
        return BuildPlan.growPhase(planKey);
    }

    /** Whether it is a repair. */
    public boolean repair() {
        return key.startsWith(REPAIR);
    }

    /** Whether it is the refresh of a campus laid out by an older plan ({@link #REFRESH}). */
    public boolean refresh() {
        return REFRESH.equals(key);
    }

    /** Whether it retires a docked 0.9.2 hall (0.14.1, {@link #retire}). */
    public boolean retires() {
        return BuildPlan.retireSlot(planKey) >= 0;
    }

    /**
     * The ground a retire job works, {x0, z0, x1, z1, yFrom, yTo} ({@link #retire}), or null for any other job (or a
     * retire job saved without it).
     */
    public int[] retireBox() {
        if (!retires()) return null;
        for (int[] b : keepOut) if (b.length == 6) return b.clone();
        return null;
    }

    /** Whether it goes ahead of the queued jobs ({@link Campus#admit}): a repair, the refresh, or a growth. */
    public boolean priority() {
        return rank() > 0;
    }

    /**
     * Where it queues ({@link Campus#admit}): 2 a repair or the refresh of the campus, 1 the growth of the nexus
     * (0.12.0: after the repairs, before the modules), 0 every other job.
     */
    public int rank() {
        if (repair() || refresh()) return 2;
        return grows() ? 1 : 0;
    }

    /** Whether the job itself is free ({@link #free}); the campus may make every job free ({@link Campus#free}). */
    public boolean free() {
        return free;
    }

    /** Machines and chests (tile entities that are not ores) the last survey found the job will take down. */
    public int machines() {
        return machines;
    }

    /** Up to {@link #MAX_BLOCKED} cells {x, y, z} of the machines it will take down (the yellow boxes). */
    public List<int[]> machineCells() {
        List<int[]> out = new ArrayList<>();
        for (long p : machineCells) {
            if (out.size() >= MAX_BLOCKED) break;
            out.add(unpack(p));
        }
        return out;
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
        if (REFRESH.equals(key)) return new ChatComponentTranslation("fluxecho.build.job.refresh");
        int slot = BuildPlan.retireSlot(planKey);
        if (slot >= 0) return new ChatComponentTranslation(
            "fluxecho.build.job.retire",
            new ChatComponentTranslation("fluxecho." + SiteFamily.labelKey(slot), String.valueOf(slot)));
        int grow = BuildPlan.growPhase(planKey);
        if (grow > 0) return new ChatComponentTranslation(
            "fluxecho.build.job.grow",
            new ChatComponentTranslation("fluxecho.nexus.phase." + grow));
        if (BuildPlan.OUTER.equals(planKey)) return new ChatComponentTranslation("fluxecho.build.job.outer");
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
     * The plan of a job, the same on the server and the client: {@code establish} (or {@code establish:<phase>} for
     * the repair of a grown nexus), {@code forum}, {@code grow:<phase>} or {@code module:<module>@<site>} for a nexus
     * whose base centre is {@code (cx, y0, cz)}, whose front is {@code (fx, fz)} and whose controller is at
     * {@code ctrl}, with the campus plan seeded from that controller. Null when the plan key names a module this game
     * does not know. The outer establish is planned with no neighbour here ({@link #planFor(String, CampusPlan, int,
     * int, int, int, int, int, int, int, List, List)} gives them).
     */
    public static BuildPlan.Plan planFor(String planKey, CampusPlan cp, int cx, int y0, int cz, int fx, int fz,
        int ctrlX, int ctrlY, int ctrlZ, List<int[]> keepOut) {
        return planFor(planKey, cp, cx, y0, cz, fx, fz, ctrlX, ctrlY, ctrlZ, keepOut, null);
    }

    /**
     * {@link #planFor(String, CampusPlan, int, int, int, int, int, int, int, int, List)} with the centres {x, z} of
     * the neighbouring campuses the outer establish ({@code establish:outer}, 0.15.0) leaves their side of the split
     * to; every other plan ignores them.
     */
    public static BuildPlan.Plan planFor(String planKey, CampusPlan cp, int cx, int y0, int cz, int fx, int fz,
        int ctrlX, int ctrlY, int ctrlZ, List<int[]> keepOut, List<int[]> neighbours) {
        return planFor(planKey, cp, cx, y0, cz, fx, fz, ctrlX, ctrlY, ctrlZ, keepOut, neighbours, null, false);
    }

    /**
     * {@link #planFor(String, CampusPlan, int, int, int, int, int, int, int, int, List, List)} with what 0.15.0's
     * fixes add to a job: the neighbours an outer establish that regains ground regains it from ({@code regainFrom},
     * {@link #regains}), and whether a retire job lays the outer ring's floor in its box ({@code retireOuter}, with the
     * neighbours as the split's).
     */
    public static BuildPlan.Plan planFor(String planKey, CampusPlan cp, int cx, int y0, int cz, int fx, int fz,
        int ctrlX, int ctrlY, int ctrlZ, List<int[]> keepOut, List<int[]> neighbours, List<int[]> regainFrom,
        boolean retireOuter) {
        return planFor(
            planKey,
            cp,
            cx,
            y0,
            cz,
            fx,
            fz,
            ctrlX,
            ctrlY,
            ctrlZ,
            keepOut,
            neighbours,
            regainFrom,
            retireOuter,
            null);
    }

    /**
     * {@link #planFor(String, CampusPlan, int, int, int, int, int, int, int, int, List, List, List, boolean)} with the
     * boxes an outer establish that patches the ring lays inside ({@code patch}, {@link #patches}, 0.15.0).
     */
    public static BuildPlan.Plan planFor(String planKey, CampusPlan cp, int cx, int y0, int cz, int fx, int fz,
        int ctrlX, int ctrlY, int ctrlZ, List<int[]> keepOut, List<int[]> neighbours, List<int[]> regainFrom,
        boolean retireOuter, List<int[]> patch) {
        if (BuildPlan.OUTER.equals(planKey)) return BuildPlan.outer(
            cp,
            cx,
            y0,
            cz,
            fx,
            fz,
            keepOut,
            neighbours,
            regainFrom == null || regainFrom.isEmpty() ? null : regainFrom,
            patch == null || patch.isEmpty() ? null : patch);
        int raise = BuildPlan.establishPhase(planKey);
        if (raise > 0)
            return BuildPlan.establish(cp, cx, y0, cz, fx, fz, NexusShape.phase(raise), ctrlX, ctrlY, ctrlZ, keepOut);
        int grow = BuildPlan.growPhase(planKey);
        if (grow > 0) return BuildPlan.grow(grow, cx, y0, cz, fx, fz, ctrlX, ctrlY, ctrlZ);
        if (BuildPlan.FORUM.equals(planKey)) return BuildPlan.forum(cp, cx, y0, cz, fx, fz, keepOut);
        if (BuildPlan.retireSlot(planKey) >= 0) {
            // a retire job's one box is the hall's ground it works (0.14.1), and since 0.15.0 the outer ring's floor
            // there once that is laid
            for (int[] b : keepOut)
                if (b.length == 6) return BuildPlan.retire(cp, planKey, cx, y0, cz, fx, fz, b, retireOuter, neighbours);
            return null;
        }
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
            plan = planFor(
                planKey,
                c.plan(),
                ce[0],
                ce[1],
                ce[2],
                f[0],
                f[1],
                ctrl[0],
                ctrl[1],
                ctrl[2],
                keepOut,
                neighbours,
                regainFrom,
                retireOuter,
                patch);
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
        this.keepClear = keepClear;
        cleared = natural = unloaded = toFill = filled = machines = 0;
        machineCells.clear();
        clearBlocked();
        touchUp.clear();
        gaveWay.clear();
        touchUpSince = -1;
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

    /** Notes a machine the job will take down (the first {@link #MAX_BLOCKED} cells are kept for the yellow boxes). */
    void addMachine(int x, int y, int z) {
        if (machineCells.size() < MAX_BLOCKED) machineCells.add(pos(x, y, z));
    }

    /** A machine's cell was taken down (or found otherwise): its yellow box goes. */
    void machineGone(int x, int y, int z) {
        machineCells.remove(pos(x, y, z));
    }

    // ---- the touch-up

    /** A grading cell was left open because an entity stood in it: it is graded again in the touch-up. */
    void leftOpen(int x, int y, int z) {
        if (touchUp.size() < MAX_TRACKED) touchUp.add(pos(x, y, z));
    }

    /**
     * A floor tile or fixture (a SOFT step) gave way to an entity and was skipped: it is tried again in the touch-up.
     */
    void gaveWay(int step) {
        if (step >= 0) gaveWay.set(step);
    }

    /** Whether the touch-up has anything left to do. */
    boolean needsTouchUp() {
        return !touchUp.isEmpty() || !gaveWay.isEmpty();
    }

    /**
     * The grading cells left open {x, y, z}, the lowest first: a hole is filled from the bottom up, so the floor cell
     * over it is not closed over an empty pocket.
     */
    List<int[]> openCells() {
        List<int[]> out = new ArrayList<>();
        for (long p : touchUp) out.add(unpack(p));
        out.sort((a, b) -> Integer.compare(a[1], b[1]));
        return out;
    }

    /**
     * Gives the steps that gave way to an entity another try where {@code free} says their cell is free now: they are
     * neither skipped nor marked any more. Returns the first stage that holds such a step, or -1 when none could go
     * yet. Steps the plan does not have (it shrank) are forgotten.
     */
    int retryGaveWay(BuildPlan.Plan plan, IntPredicate free) {
        int first = -1;
        for (int i = gaveWay.nextSetBit(0); i >= 0; i = gaveWay.nextSetBit(i + 1)) {
            if (i >= plan.steps.size()) {
                gaveWay.clear(i);
                continue;
            }
            if (!free.test(i)) continue;
            gaveWay.clear(i);
            if (!skipped.get(i)) continue;
            skipped.clear(i);
            int stage = plan.steps.get(i).stage;
            if (first < 0 || stage < first) first = stage;
        }
        return first;
    }

    /** The touch-up gave up waiting: what is left of it is left as it is. */
    void endTouchUp() {
        touchUp.clear();
        gaveWay.clear();
        touchUpSince = -1;
    }

    /**
     * A hash of the blocked cells and the machine cells the client is shown ({@link #blockedCells},
     * {@link #machineCells}), to send them again when they change.
     */
    long blockedShownHash() {
        long h = 17;
        int n = 0;
        for (long p : blocked) {
            if (n++ >= MAX_BLOCKED) break;
            h = h * 31 + p;
        }
        h = h * 31 + n;
        for (long p : machineCells) h = h * 37 + p;
        return h * 31 + machineCells.size();
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
        // the height-ranged boxes (0.12.0) apart, so the whole-column ones are saved exactly as before
        int[] ranged = boxes(keepOut, 6);
        if (ranged.length > 0) t.setIntArray("Kr", ranged);
        // the outer establish's neighbours (0.15.0), only when it has any, so every other job saves as before; their
        // claims apart, only when one claims an outer diagonal
        if (!neighbours.isEmpty()) t.setIntArray("Nb", centres(neighbours));
        int[] claims = claims(neighbours);
        if (claims.length > 0) t.setIntArray("Nc", claims);
        // the neighbours an outer establish regains ground from, and a retire job that lays the outer floor (0.15.0)
        if (!regainFrom.isEmpty()) t.setIntArray("Nr", centres(regainFrom));
        int[] regainClaims = claims(regainFrom);
        if (regainClaims.length > 0) t.setIntArray("Nq", regainClaims);
        if (retireOuter) t.setBoolean("Ro", true);
        // the boxes an outer establish that patches the ring lays inside, only when it has any (0.15.0)
        if (!patch.isEmpty()) t.setIntArray("Op", boxes(patch, 4));
        t.setIntArray("D", bits(done));
        t.setIntArray("Sk", bits(skipped));
        t.setInteger("Cc", clearColumn);
        t.setInteger("Cy", clearY);
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
        // 0.10.1; "Fr" holds the form tries since 0.10.0, so the free flag is "Fe"
        t.setBoolean("Fe", free);
        t.setInteger("Mc", machines);
        int[] te = new int[3 * machineCells.size()];
        int k = 0;
        for (long p : machineCells) {
            int[] c = unpack(p);
            te[k++] = c[0];
            te[k++] = c[1];
            te[k++] = c[2];
        }
        t.setIntArray("Te", te);
        int[] tu = new int[3 * touchUp.size()];
        k = 0;
        for (long p : touchUp) {
            int[] c = unpack(p);
            tu[k++] = c[0];
            tu[k++] = c[1];
            tu[k++] = c[2];
        }
        t.setIntArray("Tu", tu);
        t.setIntArray("Gw", bits(gaveWay));
        t.setLong("Ts", touchUpSince);
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
        readBoxes(t, j.keepOut);
        readCentres(t.getIntArray("Nb"), j.neighbours);
        readClaims(t.getIntArray("Nc"), j.neighbours);
        readCentres(t.getIntArray("Nr"), j.regainFrom);
        readClaims(t.getIntArray("Nq"), j.regainFrom);
        j.retireOuter = t.getBoolean("Ro");
        readFlat(t.getIntArray("Op"), j.patch);
        unbits(t.getIntArray("D"), j.done);
        unbits(t.getIntArray("Sk"), j.skipped);
        j.clearColumn = t.getInteger("Cc");
        j.clearY = t.hasKey("Cy") ? t.getInteger("Cy") : Integer.MIN_VALUE;
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
        j.free = t.getBoolean("Fe");
        j.machines = t.getInteger("Mc");
        int[] te = t.getIntArray("Te");
        for (int i = 0; i + 2 < te.length; i += 3) j.addMachine(te[i], te[i + 1], te[i + 2]);
        int[] tu = t.getIntArray("Tu");
        for (int i = 0; i + 2 < tu.length; i += 3) j.leftOpen(tu[i], tu[i + 1], tu[i + 2]);
        unbits(t.getIntArray("Gw"), j.gaveWay);
        j.touchUpSince = t.hasKey("Ts") ? t.getLong("Ts") : -1;
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

    /** The centres {x, z} of the list, flat ({@code Nb}, 0.15.0). */
    static int[] centres(List<int[]> l) {
        int[] out = new int[2 * l.size()];
        for (int i = 0; i < l.size(); i++) {
            out[2 * i] = l.get(i)[0];
            out[2 * i + 1] = l.get(i)[1];
        }
        return out;
    }

    /** Reads the centres saved flat by {@link #centres} into {@code into}. */
    static void readCentres(int[] flat, List<int[]> into) {
        if (flat == null) return;
        for (int i = 0; i + 1 < flat.length; i += 2) into.add(new int[] { flat[i], flat[i + 1] });
    }

    /**
     * The outer diagonals each neighbour {x, z, claims} claims, one number each in the list's order ({@code Nc},
     * 0.15.0); empty when none claims any, so a list without claims saves as before.
     */
    static int[] claims(List<int[]> l) {
        boolean any = false;
        int[] out = new int[l.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = com.fluxecho.logic.CampusSplit.claims(l.get(i));
            any |= out[i] != 0;
        }
        return any ? out : new int[0];
    }

    /** Gives the centres read by {@link #readCentres} the claims {@link #claims} saved, in order. */
    static void readClaims(int[] flat, List<int[]> centres) {
        if (flat == null) return;
        for (int i = 0; i < flat.length && i < centres.size(); i++) {
            int[] c = centres.get(i);
            if (flat[i] != 0 && c.length >= 2) centres.set(i, new int[] { c[0], c[1], flat[i] });
        }
    }

    /** The whole-column keep-out boxes {x0, z0, x1, z1} of the list, flat ({@code Ko}). */
    static int[] boxes(List<int[]> l) {
        return boxes(l, 4);
    }

    /** The keep-out boxes of the list that have {@code size} numbers, flat. */
    static int[] boxes(List<int[]> l, int size) {
        int n = 0;
        for (int[] b : l) if (b.length == size) n++;
        int[] out = new int[size * n];
        int at = 0;
        for (int[] b : l) if (b.length == size) {
            System.arraycopy(b, 0, out, at, size);
            at += size;
        }
        return out;
    }

    /** Reads whole-column boxes saved flat, four numbers each ({@code Op}, 0.15.0), into {@code into}. */
    static void readFlat(int[] flat, List<int[]> into) {
        if (flat == null) return;
        for (int i = 0; i + 3 < flat.length; i += 4)
            into.add(new int[] { flat[i], flat[i + 1], flat[i + 2], flat[i + 3] });
    }

    /**
     * Reads the keep-out boxes saved as {@code Ko} (whole columns, four numbers each) and {@code Kr} (height-ranged,
     * six numbers each, 0.12.0) into {@code into}.
     */
    static void readBoxes(NBTTagCompound t, List<int[]> into) {
        int[] ko = t.getIntArray("Ko");
        for (int i = 0; i + 3 < ko.length; i += 4) into.add(new int[] { ko[i], ko[i + 1], ko[i + 2], ko[i + 3] });
        int[] kr = t.getIntArray("Kr");
        for (int i = 0; i + 5 < kr.length; i += 6)
            into.add(new int[] { kr[i], kr[i + 1], kr[i + 2], kr[i + 3], kr[i + 4], kr[i + 5] });
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
