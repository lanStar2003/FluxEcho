package com.fluxecho.campus;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.world.BlockEvent;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.frame.FrameEvents;
import com.fluxecho.logic.BuildLedger;
import com.fluxecho.logic.BuildPace;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.BuildState;
import com.fluxecho.logic.FxCodec;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.Removal;
import com.fluxecho.logic.TerrainRule;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.nexus.TileNexus;
import com.fluxlite.backend.GTWirelessBackend;
import com.mojang.authlib.GameProfile;

/**
 * The construction engine of one nexus campus: it runs the campus's current {@link BuildJob} one server tick at a time.
 * <ul>
 * <li>SURVEY classifies the plan's cells against the world a few hundred at a time (only in loaded chunks), marks the
 * cells already right as done, counts what it would clear (and the machines and chests among it) and what blocks it,
 * then shows the projection.</li>
 * <li>BUILDING works stage by stage: the clear columns (stage 0) top-down, then the steps of the stage within a window
 * ahead of the first unfinished one. A step launches when the ledger pays for it and the team's wireless network pays
 * its EU; a shelf unit launches whole. It lands after a short flight: the cell is looked at again, emptied (the break
 * event posted for the owner's fake player first) and placed (the place event too, undone when cancelled).</li>
 * <li>What the rules let it clear ({@link TerrainRule}; since 0.10.1 by default everything but protected cells) is
 * broken as a player's break does it, for the owner's fake player ({@link Removal}): its drops and every item it spills
 * while it goes ({@link DropCapture}) go to the campus's recycle store, our own loose blocks to the ledger as parts,
 * fluids simply become air. When the store is full the builder waits for a member to take some out before it breaks
 * anything more rather than lose anything.</li>
 * <li>Grading (0.10.1): after a graded column is cleared, its floor cell becomes grass wherever the plan does not pave
 * it, whatever stood there, and the ground under it is filled with dirt down to {@code buildFillDepth}, as far as it
 * is open (air, water, plants, logs, leaves) and no further. An entity standing in a cell is waited for a little; the
 * cell, and a floor tile that gave way to an entity, are done in a last touch-up before the job ends.</li>
 * <li>Pauses lift by themselves when their cause clears (materials, power, unloaded chunks, a blocked cell, a
 * protection mod); the player's pause only by 开始. Running short of power or materials pauses only after a while
 * without progress, and such a pause lifts no sooner than {@link #RESUME_AFTER} ticks later, so a trickle of EU or
 * items does not flip the job (and resend the nexus) every few ticks.</li>
 * <li>The caisson (0.12.0, the root under the nexus): a {@link BuildPlan#DIG} step is dug out at once, as a clear
 * column's cell is (the clearing's pace and EU, its block to the recycle store), and a {@link BuildPlan#DRAIN} step
 * takes away only a fluid that flowed in; neither flies. The builder works no step of a stage's band before the band
 * before it is done ({@link BuildPlan.Plan#bandEnd}), so the root lays each layer's wall ring before it digs the
 * layer's inside, from the lid down.</li>
 * <li>A free job ({@link Campus#free}: started in creative mode, or made free by an operator) takes no materials and
 * no EU. An operator's instant build ({@link Campus#instant}) runs the same code paths at {@link #INSTANT_CELLS} cells
 * a tick with flights of one tick and only a few effects.</li>
 * </ul>
 * While it edits the world {@link #QUIET} is set (and put back in a {@code finally}), so our blocks do not make every
 * multiblock nearby re-check its structure for each block; the builder tells them once at the end of each stage.
 */
public final class Builder {

    /** Set while the builder edits the world (server thread): our blocks then skip {@code FrameEvents.changed}. */
    public static boolean QUIET;

    /** Steps looked at ahead of the first unfinished one. */
    static final int WINDOW = 256;
    /** Steps found already right that are marked done for free per tick (the rest wait a tick). */
    static final int FREE_PER_TICK = 64;
    /** Survey work per tick: a cell that needs a full look costs 8, an empty or skipped one 1. */
    static final int SURVEY_UNITS = 512 * 8, LOOK = 8, GLANCE = 1;
    /** Clearing work per tick, counted like the survey's: an empty cell costs 1, a cell that is looked at 8. */
    static final int CLEAR_UNITS = 4096;
    /**
     * An operator's instant build ({@link Campus#instant}): the budget of cells per tick (launches, clears, the window
     * looked at, flights in the air, cells found right), how much more survey and clearing work a tick does, and how
     * many launch and clearing effects a tick still shows.
     */
    static final int INSTANT_CELLS = 2048, INSTANT_WORK = 8, INSTANT_FX = 32;
    /** Ticks to wait for a structure to form, and how often to try before pausing as incomplete. */
    static final int FORM_WAIT = 200, FORM_TRIES = 3;
    /** Ticks with nothing affordable, or no power, before the job pauses for it. */
    static final int MATERIAL_WAIT = 40, POWER_WAIT = 40;
    /** Ticks a pause for materials or power lasts at least before it lifts by itself. */
    static final int RESUME_AFTER = 100;
    /** Ticks between attempts to resume a pause (a protection mod's refusal is tried less often). */
    static final int RETRY = 10, RETRY_PROTECTED = 100;
    /**
     * Ticks a launch waits for an entity in its cell, and how often before a floor tile gives way; a cell of the
     * structure keeps waiting, every {@link #DEFER_LONG} ticks, up to {@link #LONG_DEFERS} more times (a minute), and
     * is then skipped for now: the structure check looks at it again ({@link #recheck}).
     */
    static final int DEFER = 10, DEFERS = 3, DEFER_LONG = 40, LONG_DEFERS = 30;
    /**
     * Ticks the last touch-up ({@link #touchUp}) may take for the cells left because an entity stood there: a minute,
     * as long as a cell of the structure waits.
     */
    static final int TOUCH_UP_WAIT = DEFER_LONG * LONG_DEFERS;
    /** Ticks a landing waits for room in the full recycle store before it looks again. */
    static final int RETRY_SPOILS = 20;
    /** {@link #gradeCell}: the run under the floor ends at this cell (the crust, or a cell that must stay). */
    private static final int STOP = 0;
    /** Where the launches leave from: the core crystal above the nexus centre. */
    static final double CORE_Y = 4.5;

    private static final UUID NOBODY = UUID.nameUUIDFromBytes("[FluxEcho]".getBytes());

    private final Campus campus;
    private final BuildPace pace = new BuildPace();
    private long euThisTick, stuckSince = -1, pausedAt = -1;
    private boolean landingProtected, loggedFailure;
    /** The grading cell an entity stands in, and since when (it is filled once the entity moves away). */
    private long fillWaitPos = Long.MIN_VALUE, fillWaitSince;
    /** Whether this tick runs the instant mode, and the effects it showed so far. */
    private boolean fast;
    private int fxThisTick;

    /** What one pass over the job found. */
    private static final class Outcome {

        int progress;
        boolean budget, waiting, blocked, unloaded, power, materials;
    }

    Builder(Campus campus) {
        this.campus = campus;
    }

    /** EU drawn in the last tick. */
    long euLastTick() {
        return euThisTick;
    }

    /** A new job: the pace starts empty, nothing is stuck. */
    void reset() {
        pace.reset();
        stuckSince = -1;
        pausedAt = -1;
        fillWaitPos = Long.MIN_VALUE;
        loggedFailure = false;
    }

    /** Runs the job for one tick. */
    void tick(BuildJob job, long now) {
        euThisTick = 0;
        fxThisTick = 0;
        landingProtected = false;
        fast = campus.instant(job);
        BuildPlan.Plan plan = job.plan(campus);
        if (plan == null) {
            // a module this game no longer knows: the job cannot go on
            if (!BuildState.ended(job.state)) {
                job.state = BuildState.cancel(job.state);
                campus.stateChanged();
            }
            return;
        }
        land(job, plan, now);
        switch (job.state) {
            case SURVEY:
                survey(job, plan, now);
                break;
            case WAITING:
                job.state = BuildState.start(job.state, BuildState.Pause.NONE);
                job.pause = BuildState.Pause.NONE;
                campus.stateChanged();
                break;
            case BUILDING: {
                refill(job);
                Outcome o = build(job, plan, now);
                if (landingProtected) pauseJob(job, BuildState.Pause.PROTECTED);
                else stuck(job, o, now);
                break;
            }
            case PAUSED:
                refill(job);
                tryResume(job, plan, now);
                break;
            default:
                break;
        }
        if (euThisTick > 0) com.fluxlite.core.ServerEvents.addTeamTick(campus.team(), 0, euThisTick, euThisTick, true);
    }

    private void refill(BuildJob job) {
        if (fast) {
            pace.flood(INSTANT_CELLS);
            return;
        }
        // a growth (0.12.0) runs in the establish job's stage numbers
        boolean nexusStages = job.campusJob() || job.grows();
        double stagePace = nexusStages ? BuildPace.establishPace(job.stage) : BuildPace.modulePace(job.stage);
        pace.refill(Config.buildBlocksPerTick, stagePace);
        pace.refillClear(Config.buildClearPerTick);
    }

    /** The steps looked at ahead, the cells in the air and the right cells marked done per tick. */
    private int window() {
        return fast ? INSTANT_CELLS : WINDOW;
    }

    private int maxFlights() {
        return fast ? INSTANT_CELLS : BuildJob.MAX_FLIGHTS;
    }

    private int freePerTick() {
        return fast ? INSTANT_CELLS : FREE_PER_TICK;
    }

    /** Whether one more effect may go out this tick (always outside the instant mode). */
    private boolean showFx() {
        return !fast || fxThisTick++ < INSTANT_FX;
    }

    // ---- pauses

    private void stuck(BuildJob job, Outcome o, long now) {
        if (job.state != BuildState.State.BUILDING) return;
        if (o.progress > 0 || o.budget || o.waiting) {
            stuckSince = -1;
            return;
        }
        BuildState.Pause why = o.blocked ? BuildState.Pause.BLOCKED
            : o.unloaded ? BuildState.Pause.UNLOADED
                : o.power ? BuildState.Pause.POWER : o.materials ? BuildState.Pause.MATERIALS : null;
        if (why == null) return;
        if (why == BuildState.Pause.MATERIALS || why == BuildState.Pause.POWER) {
            // short of materials or power only when nothing moved for a while: an income that pays a block every
            // few ticks keeps the job building instead of flipping it between paused and building
            if (stuckSince < 0) stuckSince = now;
            if (now - stuckSince < (why == BuildState.Pause.POWER ? POWER_WAIT : MATERIAL_WAIT)) return;
        }
        stuckSince = -1;
        pauseJob(job, why);
    }

    void pauseJob(BuildJob job, BuildState.Pause why) {
        if (!BuildState.canPause(job.state, job.pause, why)) return;
        if (job.state == BuildState.State.PAUSED && job.pause == why) return;
        job.state = BuildState.pause(job.state, job.pause, why);
        job.pause = why;
        World w = campus.world();
        pausedAt = w == null ? -1 : w.getTotalWorldTime();
        campus.stateChanged();
        campus.paused(job, why);
    }

    private void tryResume(BuildJob job, BuildPlan.Plan plan, long now) {
        if (!BuildState.autoResumes(job.pause)) return;
        if (job.pause == BuildState.Pause.INCOMPLETE) {
            // the structure was put right by hand: the next pass sees it formed and moves on
            TileMultiblock t = target(job);
            if (t != null && t.formed() && grownTo(job)) resume(job);
            return;
        }
        boolean shortage = job.pause == BuildState.Pause.MATERIALS || job.pause == BuildState.Pause.POWER;
        if (shortage && !fast && pausedAt >= 0 && now - pausedAt < RESUME_AFTER) return;
        // the instant mode keeps this cadence too: a pass over its window every tick would cost a lot for nothing
        int every = job.pause == BuildState.Pause.PROTECTED ? RETRY_PROTECTED : RETRY;
        if (now % every != 0) return;
        Outcome o = build(job, plan, now);
        if (o.progress > 0 && !landingProtected) resume(job);
    }

    /** Lifts a pause whose cause cleared (nothing happens when the job is no longer paused that way). */
    private void resume(BuildJob job) {
        if (job.state != BuildState.State.PAUSED || !BuildState.autoResumes(job.pause)) return;
        job.state = BuildState.resume(job.state, job.pause);
        job.pause = BuildState.Pause.NONE;
        stuckSince = -1;
        campus.stateChanged();
    }

    // ---- the survey

    private void survey(BuildJob job, BuildPlan.Plan plan, long now) {
        World w = campus.world();
        if (!job.surveyInit) {
            job.surveyInit = true;
            job.surveyStep = 0;
            job.surveyColumn = job.clearColumn;
            job.surveyY = Integer.MIN_VALUE;
            job.natural = 0;
            job.toFill = 0;
            job.machines = 0;
            job.machineCells.clear();
            job.clearedAtSurvey = job.cleared;
            job.filledAtSurvey = job.filled;
            job.unloaded = 0;
            job.clearBlocked();
        }
        int units = SURVEY_UNITS * (fast ? INSTANT_WORK : 1);
        while (job.surveyStep < plan.steps.size() && units > 0) {
            int i = job.surveyStep++;
            if (job.done.get(i) || job.skipped.get(i) || job.flying.get(i)) {
                units -= GLANCE;
                continue;
            }
            BuildPlan.Step s = plan.steps.get(i);
            if (plan.repeat(i)) {
                // a cell the caisson dug and fills again (0.12.0): its later steps are judged in their own stage
                units -= GLANCE;
                continue;
            }
            units -= LOOK;
            if (!w.blockExists(s.x, s.y, s.z)) {
                job.unloaded++;
                continue;
            }
            TerrainRule.Probe p = probe(w, plan, i, s);
            TerrainRule.Verdict v = verdict(job, s, p);
            if (v == TerrainRule.Verdict.ALREADY) {
                // a caisson's cell found empty is looked at again when its band is sunk: it may not stay so
                if (BuildPlan.surveySettles(s.kind)) markDone(job, i);
            } else if (v == TerrainRule.Verdict.BLOCKED) job.addBlocked(s.x, s.y, s.z);
            else if (taken(v)) counted(job, s.x, s.y, s.z, p);
        }
        if (units > 0) units = surveyColumns(job, plan, w, units);
        if (units <= 0) return;
        // done: show the projection, or carry on with a job that was started before
        BuildState.State next = BuildState.surveyed(job.state, job.started, job.pause);
        job.state = next;
        // a job surveyed again after a load keeps its projection's time (the scan plane does not play again)
        if (next == BuildState.State.PROJECTING && job.projectedAt < 0) job.projectedAt = now;
        if (next != BuildState.State.PAUSED) job.pause = BuildState.Pause.NONE;
        campus.stateChanged();
    }

    /** A cell the survey found the job will take down: counted, and marked yellow when it is a machine or a chest. */
    private static void counted(BuildJob job, int x, int y, int z, TerrainRule.Probe p) {
        job.natural++;
        if (p.te) {
            job.machines++;
            job.addMachine(x, y, z);
        }
    }

    /**
     * Surveys the clear columns the job works ({@link BuildJob#clears}): what they would clear or find blocked, and
     * for a grading column its floor cell and the ground the grading would fill ({@link #surveyGrade}). Returns the
     * units left (0 or less when it has to go on next tick).
     */
    private int surveyColumns(BuildJob job, BuildPlan.Plan plan, World w, int units) {
        int y0 = campus.centre()[1];
        while (job.surveyColumn < plan.clear.size()) {
            BuildPlan.Column c = plan.clear.get(job.surveyColumn);
            if (!job.clears(c)) {
                job.surveyColumn++;
                continue;
            }
            int top = top(job, c, y0);
            if (job.surveyY == Integer.MIN_VALUE) {
                if (!w.blockExists(c.x, 64, c.z)) {
                    job.unloaded += Math.max(0, top - c.yFrom + 1);
                    job.surveyColumn++;
                    units -= GLANCE;
                    if (units <= 0) return units;
                    continue;
                }
                job.surveyY = top;
            }
            while (job.surveyY >= c.yFrom) {
                if (units <= 0) return units;
                int y = job.surveyY--;
                if (job.stepAt(c.x, y, c.z) >= 0 || w.isAirBlock(c.x, y, c.z)) {
                    units -= GLANCE;
                    continue;
                }
                units -= LOOK;
                TerrainRule.Probe p = Terrain.probe(w, c.x, y, c.z, -1, campus);
                TerrainRule.Verdict v = clearVerdict(job, p);
                if (taken(v)) counted(job, c.x, y, c.z, p);
                else if (v == TerrainRule.Verdict.BLOCKED) job.addBlocked(c.x, y, c.z);
            }
            if (c.grade) {
                if (units <= 0) return units;
                units -= surveyGrade(job, c, w, y0);
            }
            job.surveyColumn++;
            job.surveyY = Integer.MIN_VALUE;
        }
        return units;
    }

    /**
     * Surveys what grading does under a cleared column, as {@link #gradeCell} will ({@link TerrainRule#gradeColumn}):
     * the floor cell (counted to clear and to fill, or blocked and shown red) and the run of cells under it to fill.
     * Returns the units it took.
     */
    private int surveyGrade(BuildJob job, BuildPlan.Column c, World w, int y0) {
        if (campus.otherCampusAt(c.x, c.z)) return GLANCE;
        int bottom = Math.max(0, y0 - Math.max(0, Config.buildFillDepth));
        int n = Math.max(0, y0 - bottom + 1);
        TerrainRule.Probe[] seen = new TerrainRule.Probe[n];
        TerrainRule.Verdict[] v = TerrainRule.gradeColumn(i -> {
            int y = y0 - i;
            if (y > 255 || job.stepAt(c.x, y, c.z) >= 0) return null;
            return seen[i] = gradeProbe(w, c.x, y, c.z, y0);
        }, n, job.module() != null, Config.buildClearAll);
        int used = GLANCE;
        for (int i = 0; i < n; i++) {
            if (seen[i] != null) used += LOOK;
            if (v[i] == null || v[i] == TerrainRule.Verdict.ALREADY) continue;
            int y = y0 - i;
            if (v[i] == TerrainRule.Verdict.BLOCKED) {
                if (i == 0) job.addBlocked(c.x, y, c.z);
                break;
            }
            if (taken(v[i])) counted(job, c.x, y, c.z, seen[i]);
            job.toFill++;
        }
        return used;
    }

    /** The top of a clear column: grading and dais columns are clipped to the configured height. */
    private static int top(BuildJob job, BuildPlan.Column c, int y0) {
        if (!job.campusJob()) return c.yTo;
        return Math.min(c.yTo, y0 + Math.max(0, Config.buildClearHeight));
    }

    // ---- building

    /** One pass: the clearing or the steps of the current stage, moving on to the next stage when one is finished. */
    private Outcome build(BuildJob job, BuildPlan.Plan plan, long now) {
        Outcome o = new Outcome();
        // cells in flight land in a few ticks: the job is not stuck while there are any
        if (!job.flights.isEmpty()) o.waiting = true;
        for (int guard = 0; guard <= plan.stages + 1; guard++) {
            int s = job.stage;
            if (s >= plan.stages) {
                // past the last stage: the cells left for an entity are tried again, then a module waits for its
                // structure and the campus jobs are done
                if (!touchUp(job, plan, now, o)) return o;
                if (job.campusJob() || job.grows()) finish(job, now);
                else waitForm(job, plan, now, o);
                return o;
            }
            if (s == 0 && job.clearColumn < plan.clear.size()) {
                clear(job, plan, now, o);
                if (job.clearColumn < plan.clear.size()) return o;
                campus.graded(job);
            }
            if (s == BuildPlan.E_FORM && (job.campusJob() || job.grows())) {
                if (!job.raisesNexus() && !job.grows()) {
                    nextStage(job, plan);
                    continue;
                }
                if (!waitForm(job, plan, now, o)) return o;
                continue;
            }
            steps(job, plan, s, now, o);
            if (!stageFinished(job, plan, s)) return o;
            nextStage(job, plan);
        }
        return o;
    }

    private void nextStage(BuildJob job, BuildPlan.Plan plan) {
        int[] a = anchor(job);
        World w = campus.world();
        if (a != null) FrameEvents.changed(w, a[0], a[1], a[2]);
        campus.nexus()
            .frameChanged();
        job.stage++;
        job.cursor = plan.start(job.stage);
        job.formSince = -1;
        campus.stateChanged();
    }

    private boolean stageFinished(BuildJob job, BuildPlan.Plan plan, int s) {
        int end = plan.end(s);
        advanceCursor(job, plan, s);
        if (job.cursor < end) return false;
        for (BuildJob.Flight f : job.flights) if (f.step >= plan.start(s) && f.step < end) return false;
        return true;
    }

    private void advanceCursor(BuildJob job, BuildPlan.Plan plan, int s) {
        int end = plan.end(s);
        int c = Math.max(job.cursor, plan.start(s));
        while (c < end && (job.done.get(c) || job.skipped.get(c))) c++;
        job.cursor = c;
    }

    /**
     * Waits for the structure the job raised to form (the nexus after its core, a module after its controller); on
     * success moves on (or finishes a module job). After {@link #FORM_WAIT} ticks it looks at the cells again and
     * builds the wrong ones anew, and after {@link #FORM_TRIES} such rounds it pauses as incomplete. Returns whether
     * the structure stands.
     */
    private boolean waitForm(BuildJob job, BuildPlan.Plan plan, long now, Outcome o) {
        TileMultiblock t = target(job);
        if (t != null && t.formed() && grownTo(job)) {
            job.formSince = -1;
            job.formTries = 0;
            if (job.stage >= plan.stages) finish(job, now);
            else nextStage(job, plan);
            o.progress++;
            return true;
        }
        o.waiting = true;
        if (job.formSince < 0) {
            job.formSince = now;
            if (t != null) {
                recommission(job, t);
                t.frameChanged();
            }
            return false;
        }
        if (now - job.formSince < FORM_WAIT) return false;
        job.formSince = -1;
        job.formTries++;
        int redo = job.formTries >= FORM_TRIES ? -1 : recheck(job, plan);
        if (redo < 0) {
            job.formTries = 0;
            pauseJob(job, BuildState.Pause.INCOMPLETE);
            return false;
        }
        job.stage = redo;
        job.cursor = plan.start(redo);
        campus.stateChanged();
        return false;
    }

    /**
     * Looks at every HARD step again: a done one whose cell is wrong now is marked unfinished, and a skipped one (it
     * was blocked, or an entity would not move) whose cell can take its block now is tried again. Returns the first
     * stage that has such a step, or -1 when there is nothing to build anew (the structure fails for another reason).
     */
    private int recheck(BuildJob job, BuildPlan.Plan plan) {
        World w = campus.world();
        int first = -1;
        boolean changed = false;
        for (int i = 0; i < plan.steps.size(); i++) {
            BuildPlan.Step s = plan.steps.get(i);
            if (s.kind != BuildPlan.HARD || !w.blockExists(s.x, s.y, s.z)) continue;
            if (job.done.get(i)) {
                Block b = w.getBlock(s.x, s.y, s.z);
                if (Terrain.isTarget(b, w.getBlockMetadata(s.x, s.y, s.z), s.part, b.isAir(w, s.x, s.y, s.z))) continue;
                job.done.clear(i);
            } else if (job.skipped.get(i) && !job.flying.get(i)) {
                if (PartBlocks.block(s.part) == null) continue;
                TerrainRule.Verdict v = verdict(job, s, Terrain.probe(w, s.x, s.y, s.z, s.part, campus));
                if (v == TerrainRule.Verdict.BLOCKED) continue;
                job.skipped.clear(i);
                changed = true;
                if (v == TerrainRule.Verdict.ALREADY) {
                    job.done.set(i);
                    continue;
                }
            } else continue;
            if (first < 0 || s.stage < first) first = s.stage;
        }
        if (first >= 0 || changed) campus.dirty();
        return first;
    }

    /**
     * The last pass before a job ends (0.10.1): what was left because a player or an animal stood there is done now
     * that they moved. Floor tiles and fixtures that gave way go through their stage again once their cell is free
     * ({@link BuildJob#retryGaveWay}); grading cells left open are graded, the lowest first. While something still
     * stands in such a cell the job waits; the touch-up lasts at most {@link #TOUCH_UP_WAIT} ticks, and what is left
     * then stays as it is. Returns whether nothing is left to touch up (the job may end).
     */
    private boolean touchUp(BuildJob job, BuildPlan.Plan plan, long now, Outcome o) {
        if (!job.needsTouchUp()) {
            job.touchUpSince = -1;
            return true;
        }
        if (job.touchUpSince < 0) {
            job.touchUpSince = now;
            campus.dirty();
        } else if (now - job.touchUpSince >= TOUCH_UP_WAIT) {
            job.endTouchUp();
            campus.dirty();
            return true;
        }
        World w = campus.world();
        int redo = job.retryGaveWay(plan, i -> {
            BuildPlan.Step st = plan.steps.get(i);
            return w.checkChunksExist(st.x - 1, st.y - 1, st.z - 1, st.x + 1, st.y + 1, st.z + 1)
                && !entityIn(w, st.x, st.y, st.z);
        });
        if (redo >= 0) {
            // back to the first stage that has one: the stages in between pass at once, all their steps are done
            job.stage = redo;
            job.cursor = plan.start(redo);
            campus.stateChanged();
            o.progress++;
            return false;
        }
        int y0 = campus.centre()[1];
        for (int[] c : job.openCells()) {
            if (!w.checkChunksExist(c[0] - 1, 0, c[2] - 1, c[0] + 1, 255, c[2] + 1) || entityIn(w, c[0], c[1], c[2]))
                continue;
            long at = BuildJob.pos(c[0], c[1], c[2]);
            job.touchUp.remove(at);
            int r = gradeCell(job, c[0], c[1], c[2], y0, now, o);
            if (r < 0) {
                // no budget, power or room this tick: the cell waits
                job.touchUp.add(at);
                return false;
            }
            campus.dirty();
        }
        if (!job.needsTouchUp()) {
            job.touchUpSince = -1;
            return true;
        }
        // something still stands in such a cell: wait for it to move on
        o.waiting = true;
        return false;
    }

    /**
     * Whether a growth's nexus stands as the phase it grows to (0.12.0); true for every other job. Until then the
     * nexus still stands as the phase below: its new stage stands on top of a shape that holds without it.
     */
    private boolean grownTo(BuildJob job) {
        return !job.grows() || campus.nexus()
            .phase() >= job.growPhase();
    }

    /** The structure the job raises: the nexus (its establish job and repair, its growth), or the module. */
    TileMultiblock target(BuildJob job) {
        if (job.raisesNexus() || job.grows()) return campus.nexus();
        int[] c = campus.moduleController(job);
        if (c == null) return null;
        World w = campus.world();
        if (!w.blockExists(c[0], c[1], c[2])) return null;
        TileEntity te = w.getTileEntity(c[0], c[1], c[2]);
        return te instanceof TileMultiblock m ? m : null;
    }

    /** Where the multiblocks are told about a finished stage: the nexus controller, or the module's controller. */
    private int[] anchor(BuildJob job) {
        if (job.campusJob() || job.grows()) return campus.controller();
        return campus.moduleController(job);
    }

    private void finish(BuildJob job, long now) {
        resume(job);
        if (job.state != BuildState.State.BUILDING) return;
        job.state = BuildState.finish(job.state);
        job.finishedAt = now;
        campus.finished(job);
        campus.stateChanged();
    }

    // ---- clearing and grading

    /**
     * Works the clear columns of stage 0 in order: each one top-down to just over the floor level; a grading column
     * then grades its floor cell and fills the ground under it ({@link #gradeCell}), from the floor level down. Columns
     * the job does not work ({@link BuildJob#clears}: a repair's) are passed over. The work per tick is limited like
     * the survey's ({@link #CLEAR_UNITS}).
     */
    private void clear(BuildJob job, BuildPlan.Plan plan, long now, Outcome o) {
        World w = campus.world();
        int y0 = campus.centre()[1];
        int units = CLEAR_UNITS * (fast ? INSTANT_WORK : 1);
        while (job.clearColumn < plan.clear.size()) {
            BuildPlan.Column c = plan.clear.get(job.clearColumn);
            if (!job.clears(c)) {
                nextColumn(job);
                continue;
            }
            if (!w.checkChunksExist(c.x - 1, 0, c.z - 1, c.x + 1, 255, c.z + 1)) {
                o.unloaded = true;
                return;
            }
            int top = top(job, c, y0);
            if (job.clearY == Integer.MIN_VALUE) job.clearY = top;
            while (job.clearY >= c.yFrom) {
                if (units <= 0) {
                    o.budget = true;
                    return;
                }
                int y = job.clearY;
                if (y < 0 || y > 255 || job.stepAt(c.x, y, c.z) >= 0 || w.isAirBlock(c.x, y, c.z)) {
                    units -= GLANCE;
                    job.clearY--;
                    continue;
                }
                units -= LOOK;
                if (clearCell(job, c.x, y, c.z, now, o) < 0) return;
                job.clearY--;
            }
            if (c.grade && !campus.otherCampusAt(c.x, c.z)) {
                int bottom = Math.max(0, y0 - Math.max(0, Config.buildFillDepth));
                // the clearing stops just over the floor level, so the run starts at the floor cell
                if (job.clearY > y0) job.clearY = y0;
                while (job.clearY >= bottom) {
                    if (units <= 0) {
                        o.budget = true;
                        return;
                    }
                    int y = job.clearY;
                    units -= LOOK;
                    if (y <= 255 && job.stepAt(c.x, y, c.z) < 0) {
                        int r = gradeCell(job, c.x, y, c.z, y0, now, o);
                        if (r < 0) return;
                        if (r == STOP) break;
                    }
                    job.clearY--;
                }
            }
            nextColumn(job);
            o.progress++;
        }
    }

    private static void nextColumn(BuildJob job) {
        job.clearColumn++;
        job.clearY = Integer.MIN_VALUE;
    }

    /**
     * The facts about a grading cell: the floor cell is probed for grass, or for dirt when a block that stays stands
     * on it (grass would die under it), and holds what it should when it is either; a cell under it for dirt.
     */
    private TerrainRule.Probe gradeProbe(World w, int x, int y, int z, int y0) {
        if (y != y0) return Terrain.probe(w, x, y, z, Parts.DIRT, campus);
        TerrainRule.Probe p = Terrain.probe(w, x, y, z, Parts.GRASS, campus);
        if (!p.target && floorPart(w, x, y, z) == Parts.DIRT) {
            p.target = Terrain.isTarget(w.getBlock(x, y, z), w.getBlockMetadata(x, y, z), Parts.DIRT, false);
        }
        return p;
    }

    /** What the floor cell becomes: grass, or dirt under a solid block that stays over it. */
    private static int floorPart(World w, int x, int y, int z) {
        if (y >= 255) return Parts.GRASS;
        return w.getBlock(x, y + 1, z)
            .getMaterial()
            .isSolid() ? Parts.DIRT : Parts.GRASS;
    }

    /** {@link TerrainRule#forFloor} for the floor cell, {@link TerrainRule#forFill} under it. */
    private static TerrainRule.Verdict gradeVerdict(BuildJob job, TerrainRule.Probe p, boolean floor) {
        return floor ? TerrainRule.forFloor(p, job.module() != null, Config.buildClearAll)
            : TerrainRule.forFill(p, Config.buildClearAll);
    }

    /**
     * Clears one cell of a column: 1 done (or nothing to do), -1 stop for this tick. A loose block of ours
     * ({@link TerrainRule.Verdict#REPLACE}) is credited to the ledger as its part; everything else that is taken down
     * goes to the recycle store ({@link #removeCell}).
     */
    private int clearCell(BuildJob job, int x, int y, int z, long now, Outcome o) {
        World w = campus.world();
        TerrainRule.Probe p = Terrain.probe(w, x, y, z, -1, campus);
        TerrainRule.Verdict v = clearVerdict(job, p);
        if (v == TerrainRule.Verdict.ALREADY) return 1;
        if (v == TerrainRule.Verdict.BLOCKED) {
            job.addBlocked(x, y, z);
            return 1;
        }
        if (waitsForRoom(p.fluid, v == TerrainRule.Verdict.REPLACE)) {
            // the recycle store is full: wait here until a member takes something out instead of losing it
            o.waiting = true;
            return -1;
        }
        if (!pace.canClear()) {
            o.budget = true;
            return -1;
        }
        if (!payEu(job, Config.buildEuPerClear)) {
            o.power = true;
            return -1;
        }
        pace.tryClear();
        if (removeCell(x, y, z, v == TerrainRule.Verdict.REPLACE, now)) {
            job.cleared++;
            job.touched = true;
            job.unblock(x, y, z);
            job.machineGone(x, y, z);
            o.progress++;
        } else job.addBlocked(x, y, z);
        return 1;
    }

    /**
     * Grades one cell of a graded column ({@link #clear}): the floor cell becomes grass (dirt under a solid block that
     * stays), the cells under it dirt, each taken down first when something stands there ({@link TerrainRule#forFloor},
     * {@link TerrainRule#forFill}); EU only, one clearing's worth per cell. An entity standing in the cell is waited
     * for a little, then the cell is left for the touch-up ({@link #touchUp}). Returns 1 when done with the cell,
     * {@link #STOP} when the run under the floor ends here (a blocked floor cell is shown red), -1 to stop for this
     * tick.
     */
    private int gradeCell(BuildJob job, int x, int y, int z, int y0, long now, Outcome o) {
        World w = campus.world();
        boolean floor = y == y0;
        TerrainRule.Probe p = gradeProbe(w, x, y, z, y0);
        TerrainRule.Verdict v = gradeVerdict(job, p, floor);
        if (v == TerrainRule.Verdict.ALREADY) return 1;
        if (v == TerrainRule.Verdict.BLOCKED) {
            if (floor) job.addBlocked(x, y, z);
            return STOP;
        }
        if (entityIn(w, x, y, z)) {
            long at = BuildJob.pos(x, y, z);
            if (fillWaitPos != at) {
                fillWaitPos = at;
                fillWaitSince = now;
            }
            if (now - fillWaitSince < (long) DEFER * DEFERS) {
                o.waiting = true;
                return -1;
            }
            // the clearing goes on; the cell is graded again in the touch-up before the job ends
            job.leftOpen(x, y, z);
            campus.dirty();
            return 1;
        }
        boolean breaks = taken(v);
        if (breaks && waitsForRoom(p.fluid, v == TerrainRule.Verdict.REPLACE)) {
            o.waiting = true;
            return -1;
        }
        if (!pace.canClear()) {
            o.budget = true;
            return -1;
        }
        if (!payEu(job, Config.buildEuPerClear)) {
            o.power = true;
            return -1;
        }
        pace.tryClear();
        if (breaks) {
            if (!removeCell(x, y, z, v == TerrainRule.Verdict.REPLACE, now)) {
                job.addBlocked(x, y, z);
                return STOP;
            }
            job.cleared++;
            job.machineGone(x, y, z);
        }
        int part = floor ? floorPart(w, x, y, z) : Parts.DIRT;
        if (!placeCell(x, y, z, part)) {
            if (floor) job.addBlocked(x, y, z);
            return STOP;
        }
        job.unblock(x, y, z);
        job.touched = true;
        job.filled++;
        o.progress++;
        return 1;
    }

    // ---- steps

    private void steps(BuildJob job, BuildPlan.Plan plan, int s, long now, Outcome o) {
        World w = campus.world();
        advanceCursor(job, plan, s);
        // a banded stage (the caisson) works only the band of its first unfinished step
        int end = Math.min(plan.end(s), plan.bandEnd(job.cursor)), windowEnd = Math.min(end, job.cursor + window());
        int free = 0;
        boolean pending = false;
        for (int i = job.cursor; i < windowEnd;) {
            BuildPlan.Step st = plan.steps.get(i);
            if (st.group != BuildPlan.NO_GROUP) {
                int gEnd = i;
                while (gEnd < plan.steps.size() && plan.steps.get(gEnd).group == st.group
                    && plan.steps.get(gEnd).stage == st.stage) gEnd++;
                int r = group(job, plan, i, gEnd, now, o);
                if (r < 0) return;
                if (r > 0) pending = true;
                i = gEnd;
                continue;
            }
            if (job.done.get(i) || job.skipped.get(i)) {
                i++;
                continue;
            }
            if (job.flying.get(i)) {
                pending = true;
                i++;
                continue;
            }
            if (!w.checkChunksExist(st.x - 1, st.y - 1, st.z - 1, st.x + 1, st.y + 1, st.z + 1)) {
                o.unloaded = true;
                i++;
                continue;
            }
            if (BuildPlan.digs(st.kind)) {
                int r = dig(job, plan, i, st, now, o, free < freePerTick());
                if (r == DIG_STOP) return;
                if (r == DIG_FREE) free++;
                i++;
                continue;
            }
            TerrainRule.Verdict v = verdict(job, st, Terrain.probe(w, st.x, st.y, st.z, st.part, campus));
            switch (v) {
                case ALREADY:
                    if (free < freePerTick()) {
                        free++;
                        markDone(job, i);
                        job.unblock(st.x, st.y, st.z);
                        o.progress++;
                    } else o.budget = true;
                    break;
                case BLOCKED:
                    job.addBlocked(st.x, st.y, st.z);
                    if (st.kind == BuildPlan.SOFT || campus.skipBlocked()) {
                        markSkipped(job, i);
                        o.progress++;
                    } else o.blocked = true;
                    break;
                default:
                    if (job.flights.size() >= maxFlights() || !pace.canStep()) {
                        o.budget = true;
                        return;
                    }
                    int r = launch(job, plan, new int[] { i }, new boolean[] { v != TerrainRule.Verdict.PLACE }, now);
                    if (r == LAUNCHED) {
                        pace.tryStep();
                        o.progress++;
                        pending = true;
                    } else if (r == NO_POWER) {
                        o.power = true;
                        return;
                    } else if (r == NO_MATERIALS) o.materials = true;
                    else o.progress++; // skipped: nothing can place it
                    break;
            }
            i++;
        }
        // cells still in flight: the job is not stuck until they landed
        if (pending) o.waiting = true;
    }

    /** What {@link #dig} did: stop the pass, marked done for free, left for a later pass, done. */
    private static final int DIG_STOP = -1, DIG_FREE = 0, DIG_LATER = 1, DIG_DONE = 2;

    /**
     * A {@link BuildPlan#DIG} or {@link BuildPlan#DRAIN} step of the caisson (0.12.0), done at once as a clear column's
     * cell is: found empty (or already holding what a later step of the plan places there) it is marked done for free
     * while the tick's free marks last; a cell the rules forbid is shown red and skipped, as a clear column passes it;
     * anything else is taken down at the clearing's pace for its EU, what it held going to the recycle store. Returns
     * {@link #DIG_STOP} to end the pass (no clearing budget, no power, a full recycle store), {@link #DIG_FREE} when it
     * was marked done for free, {@link #DIG_LATER} when no free mark was left this tick, else {@link #DIG_DONE}.
     */
    private int dig(BuildJob job, BuildPlan.Plan plan, int i, BuildPlan.Step st, long now, Outcome o,
        boolean freeLeft) {
        TerrainRule.Probe p = probe(campus.world(), plan, i, st);
        TerrainRule.Verdict v = verdict(job, st, p);
        if (v == TerrainRule.Verdict.ALREADY) {
            if (!freeLeft) {
                o.budget = true;
                return DIG_LATER;
            }
            markDone(job, i);
            job.unblock(st.x, st.y, st.z);
            o.progress++;
            return DIG_FREE;
        }
        if (v != TerrainRule.Verdict.BLOCKED) {
            if (waitsForRoom(p.fluid, v == TerrainRule.Verdict.REPLACE)) {
                // the recycle store is full: wait here until a member takes something out instead of losing it
                o.waiting = true;
                return DIG_STOP;
            }
            if (!pace.canClear()) {
                o.budget = true;
                return DIG_STOP;
            }
            if (!payEu(job, Config.buildEuPerClear)) {
                o.power = true;
                return DIG_STOP;
            }
            pace.tryClear();
            if (removeCell(st.x, st.y, st.z, v == TerrainRule.Verdict.REPLACE, now)) {
                job.cleared++;
                job.touched = true;
                job.unblock(st.x, st.y, st.z);
                job.machineGone(st.x, st.y, st.z);
                markDone(job, i);
                o.progress++;
                return DIG_DONE;
            }
        }
        // the rules or a protection mod keep the cell: shown red and passed, as a clear column passes it
        job.addBlocked(st.x, st.y, st.z);
        markSkipped(job, i);
        o.progress++;
        return DIG_DONE;
    }

    /**
     * The facts about a step's cell: against its own part, or for a {@link BuildPlan#DIG} or {@link BuildPlan#DRAIN}
     * step against the part a later step of the plan places there ({@link BuildPlan.Plan#placedAfter}), which it leaves
     * standing.
     */
    private TerrainRule.Probe probe(World w, BuildPlan.Plan plan, int i, BuildPlan.Step st) {
        int target = BuildPlan.digs(st.kind) ? plan.placedAfter(i) : st.part;
        return Terrain.probe(w, st.x, st.y, st.z, target, campus);
    }

    /**
     * A shelf unit: its unfinished cells launch together or not at all. Returns -1 to stop the pass (no budget or
     * power), 1 when cells of it are in flight or launched, 0 otherwise.
     */
    private int group(BuildJob job, BuildPlan.Plan plan, int from, int to, long now, Outcome o) {
        World w = campus.world();
        List<Integer> todo = new ArrayList<>();
        List<Boolean> breaks = new ArrayList<>();
        boolean flying = false, blocked = false;
        for (int i = from; i < to; i++) {
            if (job.done.get(i) || job.skipped.get(i)) continue;
            if (job.flying.get(i)) {
                flying = true;
                continue;
            }
            BuildPlan.Step st = plan.steps.get(i);
            if (!w.checkChunksExist(st.x - 1, st.y - 1, st.z - 1, st.x + 1, st.y + 1, st.z + 1)) {
                o.unloaded = true;
                return 0;
            }
            TerrainRule.Verdict v = verdict(job, st, Terrain.probe(w, st.x, st.y, st.z, st.part, campus));
            if (v == TerrainRule.Verdict.ALREADY) {
                markDone(job, i);
                job.unblock(st.x, st.y, st.z);
                o.progress++;
            } else if (v == TerrainRule.Verdict.BLOCKED) {
                job.addBlocked(st.x, st.y, st.z);
                if (campus.skipBlocked()) {
                    markSkipped(job, i);
                    o.progress++;
                } else blocked = true;
            } else {
                todo.add(i);
                // only the cells that must break something first pay for the break
                breaks.add(v != TerrainRule.Verdict.PLACE);
            }
        }
        if (blocked) {
            o.blocked = true;
            return 0;
        }
        if (flying) return 1;
        if (todo.isEmpty()) return 0;
        if (job.flights.size() + todo.size() > maxFlights() || !pace.canGroup(todo.size())) {
            o.budget = true;
            return -1;
        }
        int[] steps = new int[todo.size()];
        boolean[] needsBreak = new boolean[steps.length];
        for (int k = 0; k < steps.length; k++) {
            steps[k] = todo.get(k);
            needsBreak[k] = breaks.get(k);
        }
        int r = launch(job, plan, steps, needsBreak, now);
        if (r == LAUNCHED) {
            pace.tryGroup(steps.length);
            o.progress++;
            return 1;
        }
        if (r == NO_POWER) {
            o.power = true;
            return -1;
        }
        if (r == NO_MATERIALS) o.materials = true;
        return 0;
    }

    private static final int LAUNCHED = 0, NO_MATERIALS = 1, NO_POWER = 2, SKIPPED = 3;

    /**
     * Whether a step can be placed at all: an AIR step always, any other only when its part has a block in this game
     * (the recycle bin's step waits for its block to be registered, {@link CampusModule#recycleBin}). A step that
     * cannot be placed is skipped, never paid for.
     */
    static boolean placeable(BuildPlan.Step st) {
        return st.kind == BuildPlan.AIR || PartBlocks.block(st.part) != null;
    }

    /**
     * Whether a part is a campus fixture the establish and forum jobs place for free once per spot when the ledger has
     * none: the supply port, and the recycle bin (0.10.1).
     */
    static boolean gift(int part) {
        return part == Parts.SUPPLY_PORT || part == Parts.RECYCLE_BIN;
    }

    /**
     * Pays for the steps (all or nothing) and sends them on their way; {@code needsBreak[k]} says whether step
     * {@code k} must break a block first (it pays the clearing EU on top, {@link #stepEu}). A free job
     * ({@link Campus#free}) pays nothing.
     */
    private int launch(BuildJob job, BuildPlan.Plan plan, int[] steps, boolean[] needsBreak, long now) {
        double scale = Config.buildCostScale;
        boolean free = campus.free(job);
        boolean[] used = new boolean[steps.length], charged = new boolean[steps.length];
        long eu = 0;
        for (int k = 0; k < steps.length; k++) {
            BuildPlan.Step st = plan.steps.get(steps[k]);
            if (!placeable(st)) {
                // nothing to place it with (a part this game has no block for): skip it
                markSkipped(job, steps[k]);
                refundAll(plan, steps, used, charged, k);
                return SKIPPED;
            }
            eu += stepEu(st.kind, st.part, needsBreak[k], Config.buildEuPerBlock, Config.buildEuPerClear);
            if (free || st.kind == BuildPlan.AIR || Parts.euOnly(st.part)) continue;
            if (campus.ledger()
                .charge(st.part, scale)) {
                charged[k] = true;
                used[k] = campus.ledger()
                    .usedPart();
            } else if (gift(st.part) && job.campusJob()) {
                if (job.repair() || CampusRegistry.portGifted(campus.world(), st.x, st.y, st.z)) {
                    // the gift is given once: a repair, or a nexus placed again on the same ground, leaves the
                    // fixture's cell to the players (a crafted one can always be placed by hand)
                    markSkipped(job, steps[k]);
                    refundAll(plan, steps, used, charged, k);
                    return SKIPPED;
                }
                // the campus's first gift: the establish (and forum) job places its supply port and bin for free
                charged[k] = false;
            } else {
                refundAll(plan, steps, used, charged, k);
                return NO_MATERIALS;
            }
        }
        if (!free && !drawEu(eu)) {
            refundAll(plan, steps, used, charged, steps.length);
            return NO_POWER;
        }
        int[] c = campus.centre();
        for (int k = 0; k < steps.length; k++) {
            BuildPlan.Step st = plan.steps.get(steps[k]);
            int flight = fast ? 1 : BuildPace.flightTicks(st.x - c[0], st.y - (c[1] + CORE_Y), st.z - c[2]);
            BuildJob.Flight f = new BuildJob.Flight(steps[k], now + flight, used[k], charged[k], st.part, scale);
            job.flights.add(f);
            job.flying.set(steps[k]);
            int dx = st.x - c[0], dy = st.y - c[1], dz = st.z - c[2];
            if (FxCodec.fits(dx, dy, dz) && showFx()) campus.fx()
                .launch(FxCodec.pack(dx, dy, dz), st.part, flight, now);
        }
        job.touched = true;
        campus.dirty();
        return LAUNCHED;
    }

    private void refundAll(BuildPlan.Plan plan, int[] steps, boolean[] used, boolean[] charged, int upTo) {
        for (int k = 0; k < upTo; k++) {
            if (charged[k]) campus.ledger()
                .refund(plan.steps.get(steps[k]).part, Config.buildCostScale, used[k]);
        }
        campus.dirty();
    }

    private void refund(BuildPlan.Plan plan, BuildJob.Flight f) {
        refund(campus.ledger(), f, plan);
        campus.dirty();
    }

    /**
     * Puts what a launch was charged back into the ledger: by the part and the scale it remembers, or (saved before
     * flights kept them) by its step of the plan, when a plan of the same version is given. Nothing for a launch that
     * was not charged or whose part cannot be told.
     */
    static void refund(BuildLedger ledger, BuildJob.Flight f, BuildPlan.Plan plan) {
        if (!f.charged) return;
        int part = f.part;
        if (part < 0 && plan != null && f.step >= 0 && f.step < plan.steps.size()) part = plan.steps.get(f.step).part;
        if (part < 0) return;
        ledger.refund(part, f.scale >= 0 ? f.scale : Config.buildCostScale, f.usedPart);
    }

    /** Refunds every launch still in flight into the ledger and forgets them (the core is being broken). */
    void refundFlights(BuildJob job) {
        forget(job, campus.ledger(), job.plan(campus));
        campus.dirty();
    }

    // ---- landing

    private void land(BuildJob job, BuildPlan.Plan plan, long now) {
        if (job.flights.isEmpty()) return;
        // a plan that shrank has no step for these: they go back to the ledger by the part they remember
        for (BuildJob.Flight f : job.dropStrayFlights(plan.steps.size())) refund(plan, f);
        World w = campus.world();
        List<BuildJob.Flight> due = new ArrayList<>();
        for (BuildJob.Flight f : job.flights) if (f.land <= now) due.add(f);
        for (BuildJob.Flight f : due) {
            BuildPlan.Step st = plan.steps.get(f.step);
            if (!w.checkChunksExist(st.x - 1, st.y - 1, st.z - 1, st.x + 1, st.y + 1, st.z + 1)) {
                f.land = now + RETRY;
                continue;
            }
            int r = landOne(job, st, f, now);
            if (r == KEEP) continue;
            job.flights.remove(f);
            job.flying.clear(f.step);
            if (r != DONE) refund(plan, f);
            // a free fixture is given once per spot: a nexus placed again there does not get another
            if (r == DONE && !f.charged && gift(st.part)) CampusRegistry.markPortGifted(w, st.x, st.y, st.z);
            if (r == DONE || r == DONE_REFUNDED) markDone(job, f.step);
            else if (r == REFUND_SKIP) markSkipped(job, f.step);
            campus.dirty();
        }
    }

    /** What became of a landing launch. */
    private static final int DONE = 0, KEEP = 1, REFUND = 2, REFUND_SKIP = 3, DONE_REFUNDED = 4;

    private int landOne(BuildJob job, BuildPlan.Step st, BuildJob.Flight f, long now) {
        World w = campus.world();
        TerrainRule.Probe p = Terrain.probe(w, st.x, st.y, st.z, st.part, campus);
        TerrainRule.Verdict v = verdict(job, st, p);
        if (v == TerrainRule.Verdict.ALREADY) {
            // someone put the right block there meanwhile: the launch's credit goes back
            job.unblock(st.x, st.y, st.z);
            return DONE_REFUNDED;
        }
        if (v == TerrainRule.Verdict.BLOCKED) {
            job.addBlocked(st.x, st.y, st.z);
            return st.kind == BuildPlan.SOFT || campus.skipBlocked() ? REFUND_SKIP : REFUND;
        }
        if (st.kind != BuildPlan.AIR && Terrain.solid(st.part) && entityIn(w, st.x, st.y, st.z)) {
            if (f.defers < DEFERS) {
                f.defers++;
                f.land = now + DEFER;
                return KEEP;
            }
            // a floor tile or a light gives way to whoever stands there (and is tried again in the touch-up); a cell
            // of the structure waits for them to move on, as the structure cannot form without it
            if (st.kind == BuildPlan.SOFT) {
                job.gaveWay(f.step);
                return REFUND_SKIP;
            }
            if (f.defers >= DEFERS + LONG_DEFERS) return REFUND_SKIP;
            f.defers++;
            f.land = now + DEFER_LONG;
            return KEEP;
        }
        boolean breaks = (v != TerrainRule.Verdict.PLACE || st.kind == BuildPlan.AIR)
            && !w.isAirBlock(st.x, st.y, st.z);
        if (breaks && waitsForRoom(p.fluid, v == TerrainRule.Verdict.REPLACE)) {
            // the recycle store is full: the launch hovers until a member takes something out (取出)
            f.land = now + RETRY_SPOILS;
            return KEEP;
        }
        if (breaks) {
            if (!removeCell(st.x, st.y, st.z, v == TerrainRule.Verdict.REPLACE, now)) return protectedCell(job, st);
            job.machineGone(st.x, st.y, st.z);
        }
        if (st.kind != BuildPlan.AIR && !placeCell(st.x, st.y, st.z, st.part)) return protectedCell(job, st);
        job.unblock(st.x, st.y, st.z);
        if (st.kind != BuildPlan.AIR) placed(job, st);
        return DONE;
    }

    private int protectedCell(BuildJob job, BuildPlan.Step st) {
        job.addBlocked(st.x, st.y, st.z);
        if (st.kind == BuildPlan.SOFT) return REFUND_SKIP;
        landingProtected = true;
        return REFUND;
    }

    /** After a step placed its block: commission a module controller, link a supply port or a recycle bin. */
    private void placed(BuildJob job, BuildPlan.Step st) {
        World w = campus.world();
        if (st.part == Parts.LIBRARY_CORE || st.part == campus.corePart(job)) {
            int[] c = campus.moduleController(job);
            TileEntity te = w.getTileEntity(st.x, st.y, st.z);
            if (te instanceof TileMultiblock m && c != null && c.length > 3) {
                TileNexus n = campus.nexus();
                commission(job, m, n.owner(), n.ownerName(), c[3]);
            }
        } else if (st.part == Parts.SUPPLY_PORT) {
            TileSupplyPort.linkAt(w, st.x, st.y, st.z, campus.nexus());
        } else if (st.part == Parts.RECYCLE_BIN) {
            if (w.getTileEntity(st.x, st.y, st.z) instanceof NexusLink l) l.linkTo(campus.nexus());
        }
    }

    /**
     * Gives a module controller standing on the job's controller cell its owner and its front towards the nexus, makes
     * it look at its structure again and records it on the job's site.
     */
    private void commission(BuildJob job, TileMultiblock m, UUID owner, String ownerName, int front) {
        World w = campus.world();
        m.place(owner, ownerName, front);
        m.markDirty();
        w.markBlockForUpdate(m.xCoord, m.yCoord, m.zCoord);
        FrameEvents.changed(w, m.xCoord, m.yCoord, m.zCoord);
        m.frameChanged();
        campus.recordSite(job.site, m.xCoord, m.yCoord, m.zCoord);
    }

    /**
     * A module controller that was already standing on its cell when the job reached it (put there by hand, or while
     * the nexus was unloaded) was never commissioned: it may face the wrong way, so its structure never forms. Turns
     * it to face the nexus, keeping a player owner it has (the nexus's owner when it has none).
     */
    private void recommission(BuildJob job, TileMultiblock t) {
        if (job.raisesNexus() || job.grows()) return;
        int[] c = campus.moduleController(job);
        if (c == null || c.length < 4 || t.xCoord != c[0] || t.yCoord != c[1] || t.zCoord != c[2]) return;
        if (t.owner() != null && t.front()
            .ordinal() == c[3]) return;
        TileNexus n = campus.nexus();
        if (t.owner() != null) commission(job, t, t.owner(), t.ownerName(), c[3]);
        else commission(job, t, n.owner(), n.ownerName(), c[3]);
    }

    // ---- world edits

    /**
     * Whether the builder must wait before it takes the cell down: the removal would harvest the block
     * ({@link Removal#needsRoom}) and the recycle store is full. A fluid and one of our own loose blocks ({@code ours})
     * put nothing in the store, so they go ahead.
     */
    private boolean waitsForRoom(boolean fluid, boolean ours) {
        return Removal.needsRoom(fluid, ours) && campus.recycleFull();
    }

    /**
     * Takes the block in a cell down for the owner's fake player. The break event is posted first (a protection mod
     * may refuse). Then, while a {@link DropCapture} catches every item that appears round the cell, the block goes the
     * way {@link Removal#way} picks:
     * <ul>
     * <li>everything that is recycled is broken the way a player's break does it ({@code tryHarvestBlock}):
     * {@code onBlockHarvested}, {@code removedByPlayer} (asked to harvest), then {@code onBlockDestroyedByPlayer} and
     * {@code harvestBlock} whether or not the hand could harvest it. Its drops are never read beforehand, so nothing
     * is counted twice; a GT or Bartworks ore drops from the tile it cached when it broke, a GT machine drops itself
     * and spills its inventory, a chest spills its contents. What is left afterwards is cleared or the cell refused
     * ({@link Removal#after}): a block that waited for a harvest that did not remove it, or the water ice leaves,
     * simply becomes air;</li>
     * <li>one of our own loose blocks ({@code ours}) becomes air and is credited to the ledger as its part;</li>
     * <li>a fluid becomes air and gives nothing.</li>
     * </ul>
     * Everything caught, and anything a mod hands straight to the harvesting player, goes to the recycle store
     * ({@link Campus#recycle(List)}, which voids it when {@code buildKeepSpoils} is off), even when the removal failed
     * half-way. An exception is logged once and the cell treated as refused. Returns false when refused.
     */
    private boolean removeCell(int x, int y, int z, boolean ours, long now) {
        World w = campus.world();
        Block b = w.getBlock(x, y, z);
        int meta = w.getBlockMetadata(x, y, z);
        boolean was = QUIET;
        QUIET = true;
        FakePlayer fp = null;
        DropCapture.begin(w, x, y, z);
        try {
            fp = fake(x, y, z);
            if (fp != null) {
                BlockEvent.BreakEvent e = new BlockEvent.BreakEvent(x, y, z, w, b, meta, fp);
                MinecraftForge.EVENT_BUS.post(e);
                if (e.isCanceled()) return false;
            }
            int code = ours ? PartBlocks.code(b, meta) : -1;
            boolean gone = switch (Removal.way(Terrain.isFluid(b), code >= 0, fp != null)) {
                case CREDIT -> {
                    w.setBlock(x, y, z, Blocks.air, 0, 3);
                    campus.ledger()
                        .addPart(code, 1);
                    campus.dirty();
                    yield true;
                }
                case PLAIN -> {
                    w.setBlock(x, y, z, Blocks.air, 0, 3);
                    yield true;
                }
                case HARVEST -> harvest(w, x, y, z, b, meta, fp);
            };
            if (!gone) return false;
            int[] c = campus.centre();
            int dx = x - c[0], dy = y - c[1], dz = z - c[2];
            if (FxCodec.fits(dx, dy, dz) && showFx()) campus.fx()
                .clear(FxCodec.pack(dx, dy, dz), Block.getIdFromBlock(b) << 4 | (meta & 15), now);
            return true;
        } catch (Throwable t) {
            failed("remove", x, y, z, t);
            return false;
        } finally {
            // whatever the block gave or spilled is kept, even when its removal failed half-way
            List<ItemStack> got = DropCapture.end();
            if (fp != null) handedOver(fp, got);
            campus.recycle(got);
            QUIET = was;
        }
    }

    /**
     * Breaks the block as a player's break does ({@code ItemInWorldManager.tryHarvestBlock}), for the fake player, and
     * clears or refuses what is left ({@link Removal#after}). Returns whether the cell is empty now.
     */
    private static boolean harvest(World w, int x, int y, int z, Block b, int meta, FakePlayer fp) {
        b.onBlockHarvested(w, x, y, z, meta, fp);
        boolean removed = b.removedByPlayer(w, fp, x, y, z, true);
        if (removed) {
            b.onBlockDestroyedByPlayer(w, x, y, z, meta);
            // a player's break harvests only what the hand can; the builder takes everything it was let break
            b.harvestBlock(w, fp, x, y, z, meta);
        }
        Block left = w.getBlock(x, y, z);
        return switch (Removal.after(removed, left.isAir(w, x, y, z), left == b, Terrain.isFluid(left))) {
            case DONE -> true;
            case CLEAR -> {
                w.setBlock(x, y, z, Blocks.air, 0, 3);
                yield true;
            }
            case REFUSED -> false;
        };
    }

    /**
     * Moves into the list whatever is in the fake player's inventory: a mod may hand a harvest straight to the player
     * that broke the block instead of dropping it. The fake player is the builder's own, so anything there came from
     * its breaks.
     */
    private static void handedOver(FakePlayer fp, List<ItemStack> into) {
        ItemStack[] inv = fp.inventory.mainInventory;
        for (int i = 0; i < inv.length; i++) {
            if (inv[i] != null && inv[i].stackSize > 0) into.add(inv[i]);
            inv[i] = null;
        }
    }

    /**
     * Places a part for the owner's fake player: the block goes in, then the place event is posted, and a refusal puts
     * back what was there. Returns false when refused.
     */
    private boolean placeCell(int x, int y, int z, int part) {
        World w = campus.world();
        Block block = PartBlocks.block(part);
        if (block == null) return false;
        int meta = PartBlocks.meta(part);
        boolean was = QUIET;
        QUIET = true;
        try {
            BlockSnapshot snap = BlockSnapshot.getBlockSnapshot(w, x, y, z);
            try {
                w.setBlock(x, y, z, block, meta, 3);
                FakePlayer fp = fake(x, y, z);
                if (fp == null) return true;
                BlockEvent.PlaceEvent e = ForgeEventFactory.onPlayerBlockPlace(fp, snap, ForgeDirection.UP);
                if (!e.isCanceled()) return true;
            } catch (Throwable t) {
                failed("place", x, y, z, t);
            }
            w.restoringBlockSnapshots = true;
            try {
                snap.restore(true, false);
            } catch (Throwable ignored) {} finally {
                w.restoringBlockSnapshots = false;
            }
            return false;
        } finally {
            QUIET = was;
        }
    }

    private void failed(String what, int x, int y, int z, Throwable t) {
        if (loggedFailure) return;
        loggedFailure = true;
        FluxEcho.LOG.warn(
            "The campus builder could not {} the block at {},{},{}; the cell is left and shown blocked",
            what,
            x,
            y,
            z,
            t);
    }

    /**
     * The owner's fake player, moved to the cell with nothing in its hand; null outside a server world. Forge keeps one
     * fake player per profile for every world, so its world and dimension are set for this campus each time (claim mods
     * look the claim up by the player's dimension).
     */
    private FakePlayer fake(int x, int y, int z) {
        World w = campus.world();
        if (!(w instanceof WorldServer ws)) return null;
        UUID owner = campus.nexus()
            .owner();
        FakePlayer fp = FakePlayerFactory.get(ws, new GameProfile(owner != null ? owner : NOBODY, "[FluxEcho]"));
        fp.worldObj = ws;
        fp.dimension = ws.provider.dimensionId;
        fp.setPosition(x + 0.5, y + 0.5, z + 0.5);
        fp.inventory.mainInventory[fp.inventory.currentItem] = null;
        return fp;
    }

    private static boolean entityIn(World w, int x, int y, int z) {
        List<?> l = w
            .getEntitiesWithinAABB(EntityLivingBase.class, AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1));
        return !l.isEmpty();
    }

    // ---- shared

    /**
     * The verdict for a step's cell under the configured rule ({@code buildClearAll}): {@link TerrainRule#forBuild},
     * or for a cell that must become air the clear rules. A module job's ({@link BuildJob#module}) also takes away a
     * loose supply port of ours under the conservative rule.
     */
    static TerrainRule.Verdict verdict(BuildJob job, BuildPlan.Step s, TerrainRule.Probe p) {
        return verdict(s, p, job.module() != null, Config.buildClearAll, job.repair());
    }

    /** {@link #verdict(BuildJob, BuildPlan.Step, TerrainRule.Probe)} for a first build, the rule given. */
    static TerrainRule.Verdict verdict(BuildPlan.Step s, TerrainRule.Probe p, boolean module, boolean all) {
        return verdict(s, p, module, all, false);
    }

    /**
     * {@link #verdict(BuildJob, BuildPlan.Step, TerrainRule.Probe)} with the job's kind and the rule given. A repair
     * lays its soft ground (grass, dirt, water) only where the cell is empty ({@link TerrainRule#forRepairGround}): the
     * players' garden stays. The caisson's steps (0.12.0) dig ({@link TerrainRule#forDig}: a repair only drains) and
     * drain ({@link TerrainRule#forDrain}); their probe looks for what a later step places in the cell.
     */
    static TerrainRule.Verdict verdict(BuildPlan.Step s, TerrainRule.Probe p, boolean module, boolean all,
        boolean repair) {
        if (s.kind == BuildPlan.DIG) return TerrainRule.forDig(p, module, all, repair);
        if (s.kind == BuildPlan.DRAIN) return TerrainRule.forDrain(p, module, all);
        if (repair && s.kind == BuildPlan.SOFT && Parts.euOnly(s.part))
            return TerrainRule.forRepairGround(p, module, all);
        if (s.kind != BuildPlan.AIR) return TerrainRule.forBuild(p, module, all);
        if (p.air) return TerrainRule.Verdict.ALREADY;
        if (p.replaceable && !p.lava) return TerrainRule.Verdict.CLEAR;
        TerrainRule.Verdict v = TerrainRule.forBuild(p, module, all);
        return v == TerrainRule.Verdict.PLACE ? TerrainRule.Verdict.CLEAR : v;
    }

    /**
     * The verdict for a cell of a clear column under the configured rule ({@link TerrainRule#forClear}): under the
     * conservative rule a module job ({@link BuildJob#module}; never one of the nexus's own jobs) also takes its loose
     * blocks of ours away, credited as their parts; under the clear-all rule every job does.
     */
    static TerrainRule.Verdict clearVerdict(BuildJob job, TerrainRule.Probe p) {
        return TerrainRule.forClear(p, job.module() != null, Config.buildClearAll);
    }

    /** Whether a verdict takes the cell's block away (CLEAR, or REPLACE for a loose block of ours). */
    private static boolean taken(TerrainRule.Verdict v) {
        return v == TerrainRule.Verdict.CLEAR || v == TerrainRule.Verdict.REPLACE;
    }

    /**
     * The EU one launched step costs: a cell that is only cleared, or filled with grass, dirt or water
     * ({@link Parts#euOnly}), the clearing EU; any other block the placing EU, and the clearing EU on top only when
     * that cell has something to break first (a bookcase launched whole pays the clearing EU for the cells that need
     * it, not for all fifteen).
     */
    static long stepEu(int kind, int part, boolean breaks, long euPerBlock, long euPerClear) {
        if (kind == BuildPlan.AIR || Parts.euOnly(part)) return euPerClear;
        return euPerBlock + (breaks ? euPerClear : 0);
    }

    private void markDone(BuildJob job, int i) {
        job.done.set(i);
        campus.dirty();
    }

    private void markSkipped(BuildJob job, int i) {
        job.skipped.set(i);
        campus.dirty();
    }

    /** Pays the EU of a clearing or grading cell, nothing for a free job; true when it could. */
    private boolean payEu(BuildJob job, long eu) {
        return campus.free(job) || drawEu(eu);
    }

    /** Draws EU from the owner team's wireless network; true when it could (or nothing was needed). */
    private boolean drawEu(long eu) {
        if (eu <= 0) return true;
        UUID team = campus.team();
        if (team == null || !GTWirelessBackend.INSTANCE.add(team, BigInteger.valueOf(-eu))) return false;
        euThisTick += eu;
        return true;
    }

    /**
     * Drops every flight of the job, its charge back into the ledger ({@link #refund}); {@code plan} is the plan the
     * flights were launched from, or null when it is not known any more (a new plan version).
     */
    static void forget(BuildJob job, BuildLedger ledger, BuildPlan.Plan plan) {
        for (Iterator<BuildJob.Flight> it = job.flights.iterator(); it.hasNext();) {
            BuildJob.Flight f = it.next();
            if (ledger != null) refund(ledger, f, plan);
            job.flying.clear(f.step);
            it.remove();
        }
    }
}
