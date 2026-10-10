package com.fluxecho.campus;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
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
import com.fluxecho.logic.TerrainRule;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.nexus.TileNexus;
import com.fluxlite.backend.GTWirelessBackend;
import com.mojang.authlib.GameProfile;

/**
 * The construction engine of one nexus campus: it runs the campus's current {@link BuildJob} one server tick at a time.
 * <ul>
 * <li>SURVEY classifies the plan's cells against the world a few hundred at a time (only in loaded chunks), marks the
 * cells already right as done, counts what it would clear and what blocks it, then shows the projection.</li>
 * <li>BUILDING works stage by stage: the clear columns (stage 0) top-down, then the steps of the stage within a window
 * ahead of the first unfinished one. A step launches when the ledger pays for it and the team's wireless network pays
 * its EU; a shelf unit launches whole. It lands after a short flight: the cell is looked at again, broken (the break
 * event posted for the owner's fake player first) and placed (the place event too, undone when cancelled).</li>
 * <li>Grading fills only holes open to the sky under the floor level (a dip, a pond), never a cellar or a room under
 * a roof, and waits for an animal or a player standing in the hole.</li>
 * <li>Pauses lift by themselves when their cause clears (materials, power, unloaded chunks, a blocked cell, a
 * protection mod); the player's pause only by 开始. Running short of power or materials pauses only after a while
 * without progress, and such a pause lifts no sooner than {@link #RESUME_AFTER} ticks later, so a trickle of EU or
 * items does not flip the job (and resend the nexus) every few ticks.</li>
 * <li>Logs and ores go to the spoils; when those are full the builder waits for a member to take them out rather
 * than lose what it breaks.</li>
 * </ul>
 * While it edits the world {@link #QUIET} is set, so our blocks do not make every multiblock nearby re-check its
 * structure for each block; the builder tells them once at the end of each stage instead.
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
    /** Ticks a landing waits for room in the full spoils before it looks again. */
    static final int RETRY_SPOILS = 20;
    /** {@link BuildJob#fillTo} of a grading column with no hole to fill. */
    static final int NO_FILL = Integer.MAX_VALUE;
    /** Where the launches leave from: the core crystal above the nexus centre. */
    static final double CORE_Y = 4.5;

    private static final UUID NOBODY = UUID.nameUUIDFromBytes("[FluxEcho]".getBytes());

    private final Campus campus;
    private final BuildPace pace = new BuildPace();
    private long euThisTick, stuckSince = -1, pausedAt = -1;
    private boolean landingProtected, loggedFailure;
    /** The grading cell an entity stands in, and since when (it is filled once the entity moves away). */
    private long fillWaitPos = Long.MIN_VALUE, fillWaitSince;

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
        landingProtected = false;
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
        double stagePace = job.campusJob() ? BuildPace.establishPace(job.stage) : BuildPace.modulePace(job.stage);
        pace.refill(Config.buildBlocksPerTick, stagePace);
        pace.refillClear(Config.buildClearPerTick);
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
    }

    private void tryResume(BuildJob job, BuildPlan.Plan plan, long now) {
        if (!BuildState.autoResumes(job.pause)) return;
        if (job.pause == BuildState.Pause.INCOMPLETE) {
            // the structure was put right by hand: the next pass sees it formed and moves on
            TileMultiblock t = target(job);
            if (t != null && t.formed()) resume(job);
            return;
        }
        boolean shortage = job.pause == BuildState.Pause.MATERIALS || job.pause == BuildState.Pause.POWER;
        if (shortage && pausedAt >= 0 && now - pausedAt < RESUME_AFTER) return;
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
            job.clearedAtSurvey = job.cleared;
            job.filledAtSurvey = job.filled;
            job.unloaded = 0;
            job.clearBlocked();
        }
        int units = SURVEY_UNITS;
        while (job.surveyStep < plan.steps.size() && units > 0) {
            int i = job.surveyStep++;
            if (job.done.get(i) || job.skipped.get(i) || job.flying.get(i)) {
                units -= GLANCE;
                continue;
            }
            BuildPlan.Step s = plan.steps.get(i);
            units -= LOOK;
            if (!w.blockExists(s.x, s.y, s.z)) {
                job.unloaded++;
                continue;
            }
            TerrainRule.Verdict v = verdict(job, s, Terrain.probe(w, s.x, s.y, s.z, s.part, campus));
            if (v == TerrainRule.Verdict.ALREADY) markDone(job, i);
            else if (v == TerrainRule.Verdict.BLOCKED) job.addBlocked(s.x, s.y, s.z);
            else if (v == TerrainRule.Verdict.CLEAR) job.natural++;
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

    /**
     * Surveys the clear columns the job works ({@link BuildJob#clears}): what they would clear or find blocked, and
     * for a grading column the cells its hole would take to fill. Returns the units left (0 or less when it has to go
     * on next tick).
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
                TerrainRule.Verdict v = clearVerdict(job, Terrain.probe(w, c.x, y, c.z, -1, campus));
                if (v == TerrainRule.Verdict.CLEAR || v == TerrainRule.Verdict.REPLACE) job.natural++;
                else if (v == TerrainRule.Verdict.BLOCKED) job.addBlocked(c.x, y, c.z);
            }
            if (c.grade) {
                if (units <= 0) return units;
                units -= 2 * LOOK;
                job.toFill += fillCount(job, c, w, top, y0);
            }
            job.surveyColumn++;
            job.surveyY = Integer.MIN_VALUE;
        }
        return units;
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
                // past the last stage: a module waits for its structure, the campus jobs are done
                if (job.campusJob()) finish(job, now);
                else waitForm(job, plan, now, o);
                return o;
            }
            if (s == 0 && job.clearColumn < plan.clear.size()) {
                clear(job, plan, now, o);
                if (job.clearColumn < plan.clear.size()) return o;
            }
            if (s == BuildPlan.E_FORM && job.campusJob()) {
                if (!job.raisesNexus()) {
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
        if (t != null && t.formed()) {
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

    /** The structure the job raises: the nexus, or the module whose controller its plan places. */
    TileMultiblock target(BuildJob job) {
        if (job.raisesNexus()) return campus.nexus();
        int[] c = campus.moduleController(job);
        if (c == null) return null;
        World w = campus.world();
        if (!w.blockExists(c[0], c[1], c[2])) return null;
        TileEntity te = w.getTileEntity(c[0], c[1], c[2]);
        return te instanceof TileMultiblock m ? m : null;
    }

    /** Where the multiblocks are told about a finished stage: the nexus controller, or the module's controller. */
    private int[] anchor(BuildJob job) {
        if (job.campusJob()) return campus.controller();
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

    // ---- clearing

    /**
     * Works the clear columns of stage 0 in order: each one top-down to the floor level, and a grading column then
     * fills its hole ({@link #fillBottom}). Columns the job does not work ({@link BuildJob#clears}: a repair's) are
     * passed over. The work per tick is limited like the survey's ({@link #CLEAR_UNITS}).
     */
    private void clear(BuildJob job, BuildPlan.Plan plan, long now, Outcome o) {
        World w = campus.world();
        int y0 = campus.centre()[1];
        int units = CLEAR_UNITS;
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
            if (c.grade) {
                if (job.fillTo == Integer.MIN_VALUE) {
                    if (units <= 0) {
                        o.budget = true;
                        return;
                    }
                    units -= 2 * LOOK;
                    job.fillTo = campus.otherCampusAt(c.x, c.z) ? NO_FILL : fillBottom(job, c, w, top, y0, false);
                }
                while (job.clearY >= job.fillTo) {
                    if (units <= 0) {
                        o.budget = true;
                        return;
                    }
                    int y = job.clearY;
                    units -= LOOK;
                    if (y >= 0 && y <= 255 && job.stepAt(c.x, y, c.z) < 0 && fillCell(job, c.x, y, c.z, y0, now, o) < 0)
                        return;
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
        job.fillTo = Integer.MIN_VALUE;
    }

    /**
     * The lowest cell a grading column fills under the floor level, or {@link #NO_FILL}. Only a hole open to the sky is
     * filled: nothing that blocks movement stands over the floor level up to the cleared height (our own steps aside;
     * in the survey, cells the clearing will take away aside), and nothing blocks the light above that. The hole is
     * the run of empty cells (air, or replaceable like water and tall grass; not lava) from the floor level down, at
     * most {@code buildFillDepth} below it. A column whose floor-level cell is ground fills nothing, so a cave or a
     * cellar under the ground is left alone, as is a room under a roof.
     */
    private int fillBottom(BuildJob job, BuildPlan.Column c, World w, int top, int y0, boolean survey) {
        if (y0 < 0 || y0 > 255) return NO_FILL;
        if (w.getHeightValue(c.x, c.z) > top + 1) return NO_FILL;
        for (int y = y0 + 1; y <= Math.min(255, top); y++) {
            if (job.stepAt(c.x, y, c.z) >= 0) continue;
            Block b = w.getBlock(c.x, y, c.z);
            if (b.isAir(w, c.x, y, c.z) || !b.getMaterial()
                .blocksMovement()) continue;
            if (survey && taken(clearVerdict(job, Terrain.probe(w, c.x, y, c.z, -1, campus)))) continue;
            return NO_FILL;
        }
        int bottom = NO_FILL;
        for (int y = y0; y >= Math.max(0, y0 - Math.max(0, Config.buildFillDepth)); y--) {
            if (!fillable(w, c.x, y, c.z)) break;
            bottom = y;
        }
        return bottom;
    }

    /** How many cells the grading column would fill (the survey's count). */
    private int fillCount(BuildJob job, BuildPlan.Column c, World w, int top, int y0) {
        if (campus.otherCampusAt(c.x, c.z)) return 0;
        int bottom = fillBottom(job, c, w, top, y0, true);
        int n = 0;
        for (int y = y0; y >= bottom; y--) if (job.stepAt(c.x, y, c.z) < 0) n++;
        return n;
    }

    /** Whether grading may fill the cell: air, or a replaceable block such as water or tall grass, but not lava. */
    private static boolean fillable(World w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        if (b.isAir(w, x, y, z)) return true;
        return b.isReplaceable(w, x, y, z) && b.getMaterial() != Material.lava;
    }

    /**
     * Clears one cell of a column: 1 done (or nothing to do), -1 stop for this tick. A loose block of ours that a
     * module job takes away ({@link TerrainRule.Verdict#REPLACE}) is credited to the ledger as its part.
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
        boolean ours = v == TerrainRule.Verdict.REPLACE;
        List<ItemStack> keep = ours ? null : kept(w, x, y, z);
        if (keep != null && !campus.spoilsFit(keep)) {
            // the spoils are full: wait here until a member takes them out (取出) instead of losing the drops
            campus.spoilsFull();
            o.waiting = true;
            return -1;
        }
        if (!pace.canClear()) {
            o.budget = true;
            return -1;
        }
        if (!drawEu(Config.buildEuPerClear)) {
            o.power = true;
            return -1;
        }
        pace.tryClear();
        if (breakCell(job, x, y, z, ours, keep, now)) {
            job.cleared++;
            job.touched = true;
            job.unblock(x, y, z);
            o.progress++;
        } else job.addBlocked(x, y, z);
        return 1;
    }

    /**
     * Grades one cell of a hole under the floor level ({@link #fillBottom}): the floor level itself gets grass (dirt
     * under a solid block) where it is empty, the cells below dirt where they are empty or water. Never inside another
     * structure's box; an entity standing in the cell is waited for a little, then the cell is left open. EU only.
     * Returns 1 when done with the cell, -1 to stop for this tick.
     */
    private int fillCell(BuildJob job, int x, int y, int z, int y0, long now, Outcome o) {
        World w = campus.world();
        if (!fillable(w, x, y, z) || campus.protectedCell(x, y, z)) return 1;
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
            return 1;
        }
        int part;
        if (y == y0) {
            Block above = w.getBlock(x, y + 1, z);
            part = above.getMaterial()
                .isSolid() ? Parts.DIRT : Parts.GRASS;
        } else part = Parts.DIRT;
        if (!pace.canClear()) {
            o.budget = true;
            return -1;
        }
        if (!drawEu(Config.buildEuPerClear)) {
            o.power = true;
            return -1;
        }
        pace.tryClear();
        boolean placed;
        QUIET = true;
        try {
            placed = placeCell(x, y, z, part);
        } finally {
            QUIET = false;
        }
        if (!placed) return 1;
        job.touched = true;
        job.filled++;
        o.progress++;
        return 1;
    }

    // ---- steps

    private void steps(BuildJob job, BuildPlan.Plan plan, int s, long now, Outcome o) {
        World w = campus.world();
        advanceCursor(job, plan, s);
        int end = plan.end(s), windowEnd = Math.min(end, job.cursor + WINDOW);
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
            TerrainRule.Verdict v = verdict(job, st, Terrain.probe(w, st.x, st.y, st.z, st.part, campus));
            switch (v) {
                case ALREADY:
                    if (free < FREE_PER_TICK) {
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
                    if (job.flights.size() >= BuildJob.MAX_FLIGHTS || !pace.canStep()) {
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
        if (job.flights.size() + todo.size() > BuildJob.MAX_FLIGHTS || !pace.canGroup(todo.size())) {
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
     * Pays for the steps (all or nothing) and sends them on their way; {@code needsBreak[k]} says whether step
     * {@code k} must break a block first (it pays the clearing EU on top, {@link #stepEu}).
     */
    private int launch(BuildJob job, BuildPlan.Plan plan, int[] steps, boolean[] needsBreak, long now) {
        double scale = Config.buildCostScale;
        boolean[] used = new boolean[steps.length], charged = new boolean[steps.length];
        long eu = 0;
        for (int k = 0; k < steps.length; k++) {
            BuildPlan.Step st = plan.steps.get(steps[k]);
            if (st.kind != BuildPlan.AIR && PartBlocks.block(st.part) == null) {
                // nothing to place it with (a part this game has no block for): skip it
                markSkipped(job, steps[k]);
                refundAll(plan, steps, used, charged, k);
                return SKIPPED;
            }
            eu += stepEu(st.kind, st.part, needsBreak[k], Config.buildEuPerBlock, Config.buildEuPerClear);
            if (st.kind == BuildPlan.AIR || st.part == Parts.GRASS || st.part == Parts.DIRT) continue;
            if (campus.ledger()
                .charge(st.part, scale)) {
                charged[k] = true;
                used[k] = campus.ledger()
                    .usedPart();
            } else if (st.part == Parts.SUPPLY_PORT && job.campusJob()) {
                if (job.repair() || CampusRegistry.portGifted(campus.world(), st.x, st.y, st.z)) {
                    // the gift is given once: a repair, or a nexus placed again on the same ground, leaves the
                    // port's cell to the players (a crafted port can always be placed by hand)
                    markSkipped(job, steps[k]);
                    refundAll(plan, steps, used, charged, k);
                    return SKIPPED;
                }
                // the campus's first gift: the establish (and forum) job places its supply port for free
                charged[k] = false;
            } else {
                refundAll(plan, steps, used, charged, k);
                return NO_MATERIALS;
            }
        }
        if (!drawEu(eu)) {
            refundAll(plan, steps, used, charged, steps.length);
            return NO_POWER;
        }
        int[] c = campus.centre();
        for (int k = 0; k < steps.length; k++) {
            BuildPlan.Step st = plan.steps.get(steps[k]);
            int flight = BuildPace.flightTicks(st.x - c[0], st.y - (c[1] + CORE_Y), st.z - c[2]);
            BuildJob.Flight f = new BuildJob.Flight(steps[k], now + flight, used[k], charged[k], st.part, scale);
            job.flights.add(f);
            job.flying.set(steps[k]);
            int dx = st.x - c[0], dy = st.y - c[1], dz = st.z - c[2];
            if (FxCodec.fits(dx, dy, dz)) campus.fx()
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
            // the free supply port is given once per spot: a nexus placed again there does not get another
            if (r == DONE && !f.charged && st.part == Parts.SUPPLY_PORT)
                CampusRegistry.markPortGifted(w, st.x, st.y, st.z);
            if (r == DONE || r == DONE_REFUNDED) markDone(job, f.step);
            else if (r == REFUND_SKIP) markSkipped(job, f.step);
            campus.dirty();
        }
    }

    /** What became of a landing launch. */
    private static final int DONE = 0, KEEP = 1, REFUND = 2, REFUND_SKIP = 3, DONE_REFUNDED = 4;

    private int landOne(BuildJob job, BuildPlan.Step st, BuildJob.Flight f, long now) {
        World w = campus.world();
        TerrainRule.Verdict v = verdict(job, st, Terrain.probe(w, st.x, st.y, st.z, st.part, campus));
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
            // a floor tile or a light gives way to whoever stands there; a cell of the structure waits for them to
            // move on, as the structure cannot form without it
            if (st.kind == BuildPlan.SOFT || f.defers >= DEFERS + LONG_DEFERS) return REFUND_SKIP;
            f.defers++;
            f.land = now + DEFER_LONG;
            return KEEP;
        }
        boolean breaks = (v != TerrainRule.Verdict.PLACE || st.kind == BuildPlan.AIR)
            && !w.isAirBlock(st.x, st.y, st.z);
        List<ItemStack> keep = null;
        if (breaks && v != TerrainRule.Verdict.REPLACE) {
            keep = kept(w, st.x, st.y, st.z);
            if (keep != null && !campus.spoilsFit(keep)) {
                // the spoils are full: the launch hovers until a member takes them out (取出)
                campus.spoilsFull();
                f.land = now + RETRY_SPOILS;
                return KEEP;
            }
        }
        QUIET = true;
        try {
            if (breaks && !breakCell(job, st.x, st.y, st.z, v == TerrainRule.Verdict.REPLACE, keep, now)) {
                return protectedCell(job, st);
            }
            if (st.kind != BuildPlan.AIR && !placeCell(st.x, st.y, st.z, st.part)) return protectedCell(job, st);
        } finally {
            QUIET = false;
        }
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

    /** After a step placed its block: commission a module controller, link a supply port. */
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
        if (job.raisesNexus()) return;
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
     * The drops a cell keeps in the spoils when it is broken ({@code buildKeepSpoils}: ores and logs), worked out
     * before it is broken (an ore's drops may need its tile, and are rolled once); null when it keeps nothing.
     */
    private static List<ItemStack> kept(World w, int x, int y, int z) {
        if (!Config.buildKeepSpoils) return null;
        Block b = w.getBlock(x, y, z);
        if (!Terrain.spoils(w, x, y, z, b)) return null;
        List<ItemStack> d = b.getDrops(w, x, y, z, w.getBlockMetadata(x, y, z), 0);
        return d == null ? new ArrayList<>() : d;
    }

    /**
     * Breaks a cell for the owner's fake player: posts the break event first (a protection mod may refuse), puts the
     * drops worked out by {@link #kept} into the spoils, credits one of our own parts, then removes the block. Returns
     * false when refused.
     */
    private boolean breakCell(BuildJob job, int x, int y, int z, boolean ours, List<ItemStack> keep, long now) {
        World w = campus.world();
        Block b = w.getBlock(x, y, z);
        int meta = w.getBlockMetadata(x, y, z);
        try {
            FakePlayer fp = fake(x, y, z);
            if (fp != null) {
                BlockEvent.BreakEvent e = new BlockEvent.BreakEvent(x, y, z, w, b, meta, fp);
                MinecraftForge.EVENT_BUS.post(e);
                if (e.isCanceled()) return false;
            }
            if (ours) {
                int code = PartBlocks.code(b, meta);
                if (code >= 0) campus.ledger()
                    .addPart(code, 1);
                campus.dirty();
            } else if (keep != null) campus.spoil(keep);
            int[] c = campus.centre();
            int dx = x - c[0], dy = y - c[1], dz = z - c[2];
            if (FxCodec.fits(dx, dy, dz)) campus.fx()
                .clear(FxCodec.pack(dx, dy, dz), Block.getIdFromBlock(b) << 4 | (meta & 15), now);
            w.setBlock(x, y, z, Blocks.air, 0, 3);
            return true;
        } catch (Throwable t) {
            failed("break", x, y, z, t);
            return false;
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
    }

    private void failed(String what, int x, int y, int z, Throwable t) {
        if (loggedFailure) return;
        loggedFailure = true;
        FluxEcho.LOG
            .warn("The campus builder could not {} the block at {},{},{}; treating it as protected", what, x, y, z, t);
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
     * The verdict for a step's cell: {@link TerrainRule#forBuild}, or for a cell that must become air the clear rules.
     * A module job's ({@link BuildJob#module}) also takes away a loose supply port of ours.
     */
    static TerrainRule.Verdict verdict(BuildJob job, BuildPlan.Step s, TerrainRule.Probe p) {
        return verdict(s, p, job.module() != null);
    }

    /** {@link #verdict(BuildJob, BuildPlan.Step, TerrainRule.Probe)} with the job's kind given. */
    static TerrainRule.Verdict verdict(BuildPlan.Step s, TerrainRule.Probe p, boolean module) {
        if (s.kind != BuildPlan.AIR) return TerrainRule.forBuild(p, module);
        if (p.air) return TerrainRule.Verdict.ALREADY;
        if (p.replaceable && !p.lava) return TerrainRule.Verdict.CLEAR;
        TerrainRule.Verdict v = TerrainRule.forBuild(p, module);
        return v == TerrainRule.Verdict.PLACE ? TerrainRule.Verdict.CLEAR : v;
    }

    /**
     * The verdict for a cell of a clear column ({@link TerrainRule#forClear}): a module job ({@link BuildJob#module};
     * never one of the nexus's own jobs) also takes its loose blocks of ours away, credited as their parts.
     */
    static TerrainRule.Verdict clearVerdict(BuildJob job, TerrainRule.Probe p) {
        return TerrainRule.forClear(p, job.module() != null);
    }

    /** Whether a clear verdict takes the cell's block away (natural terrain, an ore, or a loose block of ours). */
    private static boolean taken(TerrainRule.Verdict v) {
        return v == TerrainRule.Verdict.CLEAR || v == TerrainRule.Verdict.REPLACE;
    }

    /**
     * The EU one launched step costs: a cell that is only cleared, or filled with grass or dirt, the clearing EU; any
     * other block the placing EU, and the clearing EU on top only when that cell has something to break first (a
     * bookcase launched whole pays the clearing EU for the cells that need it, not for all fifteen).
     */
    static long stepEu(int kind, int part, boolean breaks, long euPerBlock, long euPerClear) {
        if (kind == BuildPlan.AIR || part == Parts.GRASS || part == Parts.DIRT) return euPerClear;
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
