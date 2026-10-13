package com.fluxecho.campus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.material.Material;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;

import org.junit.jupiter.api.Test;

import com.fluxecho.city.CityModule;
import com.fluxecho.city.TileCityModule;
import com.fluxecho.city.TileDomeGenerator;
import com.fluxecho.city.TileDualFoilVault;
import com.fluxecho.city.TileResonanceGate;
import com.fluxecho.city.TileRoot;
import com.fluxecho.logic.ArchiveShape;
import com.fluxecho.logic.BuildLedger;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.BuildState;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.DomeGeneratorShape;
import com.fluxecho.logic.FxCodec;
import com.fluxecho.logic.Mix;
import com.fluxecho.logic.NexusShape;
import com.fluxecho.logic.PartRecipes;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.RootShape;
import com.fluxecho.logic.SiteFamily;
import com.fluxecho.logic.TerrainRule;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.nexus.TileNexus;

/**
 * The parts of the campus engine that need no world: job keys and positions, the job's saved form, the plan a job
 * recomputes (the same on the client and the server), module footprints and the effect batches.
 */
class CampusEngineTest {

    private static final int[][] FACINGS = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
    private static final int CX = 100, Y0 = 64, CZ = -200;
    private static final String TEST_MODULE = "enginetest";

    private static ModuleSpec spec() {
        ModuleSpec s = ModuleSpecs.get(TEST_MODULE);
        if (s != null) return s;
        s = new ModuleSpec(
            TEST_MODULE,
            "library",
            ArchiveShape.WIDTH,
            ArchiveShape.DEPTH,
            ArchiveShape.HEIGHT,
            CampusPlan.HALL_SITES,
            Parts.LIBRARY_CORE,
            0x8A5CFF,
            BuildPlan::archive,
            BuildPlan::archiveController);
        ModuleSpecs.register(s);
        return s;
    }

    @Test
    void positionsPackBothWays() {
        int[][] cells = { { 0, 0, 0 }, { -1, 255, -1 }, { 29_999_999, 64, -29_999_999 }, { -30_000_000, -1, 12 },
            { 100, 2047, -200 }, { -5, -2048, 7 } };
        List<Long> seen = new ArrayList<>();
        for (int[] c : cells) {
            long p = BuildJob.pos(c[0], c[1], c[2]);
            assertArrayEquals(c, BuildJob.unpack(p));
            assertFalse(seen.contains(p), "distinct cells pack apart");
            seen.add(p);
        }
    }

    @Test
    void moduleKeysParse() {
        assertEquals("library", ModuleSpecs.moduleOf("module:library@4"));
        assertEquals(4, ModuleSpecs.siteOf("module:library@4"));
        assertNull(ModuleSpecs.moduleOf("establish"));
        assertEquals(-1, ModuleSpecs.siteOf("repair:4"));
        assertEquals(-1, ModuleSpecs.siteOf("module:library@x"));
    }

    @Test
    void aJobSurvivesSaving() {
        ModuleSpec spec = spec();
        BuildJob j = BuildJob.module(spec, 2);
        j.state = BuildState.State.PAUSED;
        j.pause = BuildState.Pause.MATERIALS;
        j.stage = BuildPlan.M_SHELL;
        j.started = true;
        j.touched = true;
        j.projectedAt = 1234;
        j.done.set(0, 700);
        j.done.set(5000);
        j.skipped.set(701);
        j.clearColumn = 17;
        j.clearY = 70;
        j.cleared = 400;
        j.natural = 900;
        j.toFill = 31;
        j.filled = 12;
        j.keepClear = true;
        j.formTries = 2;
        j.free = true;
        j.machines = 5;
        j.addMachine(CX - 7, Y0 + 2, CZ + 9);
        j.addBlocked(CX + 3, Y0 + 1, CZ - 4);
        BuildJob.Flight f = new BuildJob.Flight(702, 99_999L, true, true, Parts.deck(Parts.D_LIT), 2.5);
        f.defers = 2;
        j.flights.add(f);
        j.flying.set(702);

        NBTTagCompound t = j.write();
        BuildJob r = BuildJob.read(t);
        assertEquals("module:" + TEST_MODULE + "@2", r.key());
        assertEquals(r.key(), r.planKey());
        assertEquals(2, r.site());
        assertEquals(BuildJob.V, r.version());
        assertEquals(BuildState.State.PAUSED, r.state());
        assertEquals(BuildState.Pause.MATERIALS, r.pause());
        assertEquals(BuildPlan.M_SHELL, r.stage());
        assertTrue(r.started());
        assertTrue(r.touched());
        assertEquals(1234, r.projectedAt());
        assertEquals(701, r.placed());
        assertTrue(r.done(5000));
        assertTrue(r.skipped(701));
        assertEquals(17, r.clearColumn);
        assertEquals(70, r.clearY);
        assertEquals(400, r.cleared());
        assertEquals(31, r.toFill());
        assertEquals(12, r.filled());
        assertTrue(r.keepClear);
        assertEquals(2, r.formTries, "the form tries keep their key");
        assertTrue(r.free(), "a free job stays free");
        assertEquals(5, r.machines());
        assertArrayEquals(
            new int[] { CX - 7, Y0 + 2, CZ + 9 },
            r.machineCells()
                .get(0));
        assertEquals(1, r.blockedCount());
        assertArrayEquals(
            new int[] { CX + 3, Y0 + 1, CZ - 4 },
            r.blockedCells()
                .get(0));
        assertEquals(
            1,
            r.flights()
                .size());
        BuildJob.Flight g = r.flights()
            .get(0);
        assertEquals(702, g.step);
        assertEquals(99_999L, g.land);
        assertTrue(g.usedPart);
        assertTrue(g.charged);
        assertEquals(2, g.defers);
        assertEquals(Parts.deck(Parts.D_LIT), g.part);
        assertEquals(2.5, g.scale);
        assertTrue(r.flying(702));
        assertNull(BuildJob.read(new NBTTagCompound()), "no job in an empty tag");
    }

    @Test
    void campusJobsKeepTheirBoxes() {
        List<int[]> boxes = new ArrayList<>();
        boxes.add(new int[] { 1, 2, 3, 4 });
        boxes.add(new int[] { -10, -20, -5, -8 });
        BuildJob r = BuildJob.read(
            BuildJob.forum(boxes)
                .write());
        assertEquals(BuildPlan.FORUM, r.key());
        assertTrue(r.campusJob());
        assertFalse(r.raisesNexus());
        assertEquals(
            2,
            r.keepOut()
                .size());
        assertArrayEquals(
            new int[] { -10, -20, -5, -8 },
            r.keepOut()
                .get(1));
        BuildJob repair = BuildJob.read(
            BuildJob.repairNexus(Collections.emptyList())
                .write());
        assertEquals("repair:establish", repair.key());
        assertEquals(BuildPlan.ESTABLISH, repair.planKey());
        assertTrue(repair.repair() && repair.raisesNexus() && repair.campusJob());
        BuildJob mod = BuildJob.repairModule(spec(), 6);
        assertEquals("repair:6", mod.key());
        assertEquals(TEST_MODULE, mod.module());
        assertEquals(6, mod.site());
    }

    @Test
    void plansAreRecomputedTheSameWay() {
        ModuleSpec spec = spec();
        for (int[] f : FACINGS) {
            int[] ctrl = { CX + 5 * f[0], Y0 + 2, CZ + 5 * f[1] };
            CampusPlan cp = new CampusPlan(Mix.seed(ctrl[0], ctrl[1], ctrl[2]));
            List<int[]> none = Collections.emptyList();
            BuildPlan.Plan a = BuildJob
                .planFor(BuildPlan.ESTABLISH, cp, CX, Y0, CZ, f[0], f[1], ctrl[0], ctrl[1], ctrl[2], none);
            BuildPlan.Plan b = BuildPlan
                .establish(cp, CX, Y0, CZ, f[0], f[1], NexusShape.PHASE_1, ctrl[0], ctrl[1], ctrl[2]);
            assertEquals(b.steps, a.steps);
            assertEquals(b.clear, a.clear);
            for (int site : CampusPlan.HALL_SITES) {
                BuildPlan.Plan m = BuildJob
                    .planFor(spec.jobKey(site), cp, CX, Y0, CZ, f[0], f[1], ctrl[0], ctrl[1], ctrl[2], none);
                BuildPlan.Plan n = BuildPlan.archive(cp, site, CX, Y0, CZ, f[0], f[1]);
                assertEquals(n.steps, m.steps);
                assertArrayEquals(
                    BuildPlan.archiveController(site, CX, Y0, CZ, f[0], f[1]),
                    spec.controller(site, CX, Y0, CZ, f[0], f[1]));
            }
        }
        assertNull(BuildJob.planFor("module:nothing@4", new CampusPlan(1), CX, Y0, CZ, 0, -1, 0, 0, 0, null));
        assertNull(BuildJob.planFor(spec.jobKey(1), new CampusPlan(1), CX, Y0, CZ, 0, -1, 0, 0, 0, null));
    }

    @Test
    void footprintsHoldTheHall() {
        ModuleSpec spec = spec();
        for (int[] f : FACINGS) for (int site : CampusPlan.HALL_SITES) {
            int[] box = spec.footprint(site, CX, CZ, f[0], f[1], 0);
            assertEquals(spec.width * spec.depth, (box[2] - box[0] + 1) * (box[3] - box[1] + 1), "the box is tight");
            for (int x = box[0] - 2; x <= box[2] + 2; x++) for (int z = box[1] - 2; z <= box[3] + 2; z++) {
                int[] l = CampusPlan.toLocal(x - CX, z - CZ, f[0], f[1]);
                boolean in = x >= box[0] && x <= box[2] && z >= box[1] && z <= box[3];
                assertEquals(CampusPlan.inHall(site, spec.width, spec.depth, l[0], l[1]), in, x + "," + z);
            }
            int[] wide = spec.footprint(site, CX, CZ, f[0], f[1], 1);
            assertArrayEquals(new int[] { box[0] - 1, box[1] - 1, box[2] + 1, box[3] + 1 }, wide);
        }
    }

    @Test
    void flightsSavedWithoutTheirPartStayUnknown() {
        BuildJob j = BuildJob.forum(Collections.emptyList());
        j.flights.add(new BuildJob.Flight(3, 10L, false, true));
        NBTTagCompound t = j.write();
        // a flight saved before flights remembered their part
        t.getTagList("Fl", 10)
            .getCompoundTagAt(0)
            .removeTag("P");
        t.getTagList("Fl", 10)
            .getCompoundTagAt(0)
            .removeTag("Sc");
        BuildJob.Flight g = BuildJob.read(t)
            .flights()
            .get(0);
        assertEquals(-1, g.part);
        assertTrue(g.scale < 0);
    }

    @Test
    void forgottenFlightsGoBackToTheLedger() {
        int deck = Parts.deck(Parts.D_DECK), lit = Parts.deck(Parts.D_LIT);
        BuildLedger ledger = new BuildLedger();
        ledger.addPart(lit, 1);
        for (java.util.Map.Entry<String, Long> e : PartRecipes.cost(deck)
            .entrySet()) ledger.addRaw(e.getKey(), e.getValue());
        assertTrue(ledger.charge(lit, 1.0));
        boolean litUsedPart = ledger.usedPart();
        assertTrue(ledger.charge(deck, 1.0));
        boolean deckUsedPart = ledger.usedPart();
        assertEquals(0, ledger.part(lit));
        assertEquals(0, ledger.raw("ore:stone"));

        BuildJob j = BuildJob.forum(Collections.emptyList());
        // step indexes of some other plan version: only the remembered parts can refund them
        j.flights.add(new BuildJob.Flight(9000, 1L, litUsedPart, true, lit, 1.0));
        j.flights.add(new BuildJob.Flight(9001, 1L, deckUsedPart, true, deck, 1.0));
        j.flights.add(new BuildJob.Flight(9002, 1L, false, false, Parts.SUPPLY_PORT, 1.0));
        j.flying.set(9000, 9003);
        Builder.forget(j, ledger, null);
        assertTrue(j.flights.isEmpty());
        assertTrue(j.flying.isEmpty());
        assertEquals(1, ledger.part(lit));
        assertEquals(
            PartRecipes.cost(deck)
                .get("ore:stone"),
            ledger.raw("ore:stone"));
        assertEquals(0, ledger.part(Parts.SUPPLY_PORT), "the free port was not charged, so nothing comes back");
    }

    @Test
    void movingASiteTakesBackTheStart() {
        ModuleSpec spec = spec();
        BuildJob j = BuildJob.module(spec, 4);
        j.state = BuildState.State.PAUSED;
        j.pause = BuildState.Pause.MATERIALS;
        j.started = true;
        j.startedBy = java.util.UUID.randomUUID();
        j.moveTo(spec, 2);
        assertEquals(spec.jobKey(2), j.key());
        assertFalse(j.started());
        assertNull(j.startedBy());
        assertEquals(BuildState.State.SURVEY, j.state());
        assertEquals(BuildState.Pause.NONE, j.pause());
        assertEquals(
            BuildState.State.PROJECTING,
            BuildState.surveyed(j.state, j.started, j.pause),
            "the new site waits for 开始");
    }

    @Test
    void aPauseDuringTheSurveyHolds() {
        BuildJob j = BuildJob.establish(Collections.emptyList());
        assertFalse(j.playerPause(), "nobody started it: nothing to pause");
        assertEquals(BuildState.Pause.NONE, j.pause());
        // 开始 during the survey, then 暂停 before it ends
        j.started = true;
        assertTrue(j.playerPause());
        assertEquals(BuildState.State.SURVEY, j.state());
        assertEquals(BuildState.Pause.PLAYER, j.pause());
        assertEquals(
            BuildState.State.PAUSED,
            BuildState.surveyed(j.state, j.started, j.pause),
            "the survey ends paused, not building");
        // a running job pauses at once; a job that waits for 开始 cannot be paused
        j.state = BuildState.State.BUILDING;
        j.pause = BuildState.Pause.NONE;
        assertTrue(j.playerPause());
        assertEquals(BuildState.State.PAUSED, j.state());
        assertEquals(BuildState.Pause.PLAYER, j.pause());
        assertTrue(j.playerPause(), "pausing twice is fine");
        BuildJob p = BuildJob.establish(Collections.emptyList());
        p.state = BuildState.State.PROJECTING;
        assertFalse(p.playerPause());
        assertEquals(BuildState.State.PROJECTING, p.state());
    }

    @Test
    void repairsLeaveTheGradingAlone() {
        BuildPlan.Column grade = new BuildPlan.Column(0, 0, Y0 + 1, Y0 + 24, true);
        BuildPlan.Column dais = new BuildPlan.Column(1, 1, Y0 + 1, Y0 + 24, false);
        List<int[]> none = Collections.emptyList();
        BuildJob est = BuildJob.establish(none);
        assertTrue(est.clears(grade) && est.clears(dais));
        BuildJob forum = BuildJob.forum(none);
        assertTrue(forum.clears(grade));
        BuildJob rep = BuildJob.repairNexus(none);
        assertFalse(rep.clears(grade), "a repair does not grade the disc again");
        assertTrue(rep.clears(dais), "nothing may stand inside the nexus");
        BuildJob mod = BuildJob.repairModule(spec(), 4);
        assertFalse(mod.clears(dais));
        // started over after it stopped as incomplete: its clearing was done
        BuildJob again = BuildJob.module(spec(), 4);
        again.restart(true);
        assertFalse(again.clears(dais));
        est.restart(true);
        assertFalse(est.clears(grade));
        assertTrue(est.clears(dais));
        // a new site is new ground
        again.moveTo(spec(), 2);
        assertTrue(again.clears(dais));
    }

    @Test
    void aRepairGoesFirst() {
        ModuleSpec spec = spec();
        List<BuildJob> queue = new ArrayList<>();
        BuildJob lib = BuildJob.module(spec, 4);
        lib.state = BuildState.State.PROJECTING;
        BuildJob other = BuildJob.module(spec, 6);
        assertSame(lib, Campus.admit(null, queue, lib));
        assertSame(lib, Campus.admit(lib, queue, other));
        assertEquals(1, queue.size());
        // nobody started the module (it waits for its core): the repair takes its place
        BuildJob rep = BuildJob.repairNexus(Collections.emptyList());
        assertSame(rep, Campus.admit(lib, queue, rep));
        assertSame(lib, queue.get(0));
        assertSame(other, queue.get(1));
        // a started job keeps its place; repairs queue ahead of the other jobs, in their order
        List<BuildJob> q2 = new ArrayList<>();
        BuildJob running = BuildJob.module(spec, 2);
        running.state = BuildState.State.BUILDING;
        running.started = true;
        q2.add(other);
        BuildJob r1 = BuildJob.repairModule(spec, 4), r2 = BuildJob.repairNexus(Collections.emptyList());
        assertSame(running, Campus.admit(running, q2, r1));
        assertSame(running, Campus.admit(running, q2, r2));
        assertSame(r1, q2.get(0));
        assertSame(r2, q2.get(1));
        assertSame(other, q2.get(2));
        // a job that touched the world does not give way either
        BuildJob touched = BuildJob.module(spec, 4);
        touched.state = BuildState.State.PROJECTING;
        touched.touched = true;
        assertFalse(Campus.yields(touched));
    }

    /**
     * A recorded site whose module is not known yet (its chunk not loaded since) takes its own site only (0.14.1):
     * 0.14.0 counted it as a module of every kind of the site's family, so the other halls were neither offered nor
     * listed anywhere until the chunk loaded.
     */
    @Test
    void anUnknownModuleOnASiteIsNoModuleOfEveryKind() {
        Map<Integer, String> kinds = new java.util.TreeMap<>();
        kinds.put(4, "");
        kinds.put(2, "eco_dome");
        assertEquals(-1, Campus.builtSite(kinds, "library"));
        assertEquals(-1, Campus.builtSite(kinds, "dual_foil_vault"));
        assertEquals(2, Campus.builtSite(kinds, "eco_dome"));
        kinds.put(4, "library");
        assertEquals(4, Campus.builtSite(kinds, "library"));
    }

    /**
     * 换一个 (0.14.1): a job nobody started goes to the end of the queue without being declined and the next one comes
     * up; a started job, one that touched anything, or a job with nothing behind it stays where it is.
     */
    @Test
    void laterPutsAJobNobodyStartedAtTheEndOfTheQueue() {
        ModuleSpec spec = spec();
        BuildJob a = BuildJob.module(spec, 4), b = BuildJob.module(spec, 2), c = BuildJob.module(spec, 6);
        a.state = BuildState.State.PROJECTING;
        List<BuildJob> queue = new ArrayList<>(Arrays.asList(b, c));
        assertSame(b, Campus.defer(a, queue));
        assertEquals(Arrays.asList(c, a), queue);
        assertSame(c, Campus.defer(b, queue), "a job still surveyed gives way too");
        assertEquals(Arrays.asList(a, b), queue);
        c.started = true;
        assertSame(c, Campus.defer(c, queue), "a started job keeps its place");
        c.started = false;
        c.touched = true;
        assertSame(c, Campus.defer(c, queue), "so does one that touched the world");
        assertEquals(Arrays.asList(a, b), queue);
        List<BuildJob> none = new ArrayList<>();
        assertSame(a, Campus.defer(a, none));
        assertTrue(none.isEmpty());
        assertNull(Campus.defer(null, queue));
    }

    /**
     * The retire job of a docked 0.9.2 hall (0.14.1): {@code retire:<slot>}, named by its slot, waiting at the end of
     * the queue like a module job; its one box is the hall's ground, saved and read back with the job, from which both
     * sides make the same plan: every column of the box cleared from its top down to Y0 + 1 and graded, then the
     * forum's floor and fixtures inside the box. No module, no campus job: it raises nothing.
     */
    @Test
    void aRetireJobTakesTheHallDownAndGivesItsGroundBack() {
        int[] f = FACINGS[1];
        int[] ctrl = { CX + 5 * f[0], Y0 + 2, CZ + 5 * f[1] };
        CampusPlan cp = new CampusPlan(Mix.seed(ctrl[0], ctrl[1], ctrl[2]));
        // a hall on the gate road (slot 0, 32 ahead), standing two cells low, 11 high
        int[] o = com.fluxecho.logic.RingSlots.offset(0, 32, f[0], f[1]);
        int[] keep = BuildPlan.moduleKeepOut(new int[] { CX + o[0], Y0 - 2, CZ + o[1] });
        int[] box = { keep[0], keep[1], keep[2], keep[3], Y0 - 2, Y0 + 9 };
        BuildJob j = BuildJob.read(
            BuildJob.retire(0, box)
                .write());
        assertEquals("retire:0", j.key());
        assertEquals("retire:0", j.planKey());
        assertTrue(j.retires());
        assertEquals(0, j.site());
        assertArrayEquals(box, j.retireBox());
        assertNull(j.module());
        assertFalse(j.campusJob() || j.repair() || j.grows() || j.raisesNexus());
        assertEquals(0, j.rank(), "it waits behind the queued jobs");
        assertEquals("fluxecho.build.job.retire", ((ChatComponentTranslation) j.name()).getKey());
        assertEquals("build.stage.d0", com.fluxecho.nexus.NexusGui.stageKey(j.planKey(), 0));
        assertEquals("build.stage.d2", com.fluxecho.nexus.NexusGui.stageKey(j.planKey(), 9));
        assertNull(
            BuildJob.forum(Collections.emptyList())
                .retireBox());
        assertEquals(-1, BuildPlan.retireSlot("retire:8"), "a ring slot only");
        assertEquals(-1, BuildPlan.retireSlot("retire:x"));

        BuildPlan.Plan p = BuildJob
            .planFor(j.planKey(), cp, CX, Y0, CZ, f[0], f[1], ctrl[0], ctrl[1], ctrl[2], j.keepOut());
        assertEquals(BuildPlan.D_STAGES, p.stages);
        int cols = (keep[2] - keep[0] + 1) * (keep[3] - keep[1] + 1);
        assertEquals(cols, p.clear.size());
        for (BuildPlan.Column c : p.clear) {
            assertTrue(c.grade, "graded: no pit where the hall stood");
            assertEquals(Y0 + 1, c.yFrom);
            assertEquals(Y0 + 9, c.yTo);
            assertTrue(c.x >= keep[0] && c.x <= keep[2] && c.z >= keep[1] && c.z <= keep[3]);
        }
        // the forum's tiles there, and on the gate road its pylons, which the establish job left out round the hall
        BuildPlan.Plan forum = BuildPlan.forum(cp, CX, Y0, CZ, f[0], f[1]);
        int inside = 0, pylons = 0;
        for (BuildPlan.Step s : forum.steps) if (s.x >= keep[0] && s.x <= keep[2] && s.z >= keep[1] && s.z <= keep[3]) {
            inside++;
            if (s.stage == BuildPlan.E_FIXTURE) pylons++;
        }
        assertEquals(inside, p.steps.size());
        assertTrue(pylons > 0, "the gate road's pylons stand in the hall's box");
        assertEquals(pylons, p.count(BuildPlan.D_FIXTURE));
        assertEquals(0, p.count(BuildPlan.D_CLEAR));
        for (BuildPlan.Step s : p.steps) assertTrue(s.y >= Y0 && s.kind == BuildPlan.SOFT, s.toString());
        // the same plan from the same inputs, and none without its box
        assertEquals(
            p.steps,
            BuildJob.planFor("retire:0", cp, CX, Y0, CZ, f[0], f[1], ctrl[0], ctrl[1], ctrl[2], j.keepOut()).steps);
        assertNull(BuildJob.planFor("retire:0", cp, CX, Y0, CZ, f[0], f[1], 0, 0, 0, Collections.emptyList()));
    }

    /**
     * A retire job whose hall is gone before 开始 (0.14.1: a member broke it by hand, and the module was offered on its
     * site at once) is withdrawn, not declined, as long as it never began: it must not clear the old box, whatever the
     * clear-all rule says, nor lay forum tiles the module's clearing takes away again. It is kept while a docked hall
     * still blocks its slot, while a hall (even unformed) stands in its box, and once it began.
     */
    @Test
    void aRetireJobWhoseHallIsGoneIsWithdrawn() {
        int[] box = { 10, 20, 24, 34, Y0 - 1, Y0 + 11 };
        BuildJob mod = BuildJob.forum(Collections.emptyList());
        BuildJob gone = BuildJob.retire(4, box), kept = BuildJob.retire(6, box.clone());
        List<BuildJob> jobs = Arrays.asList(mod, gone, kept);
        Set<Integer> none = Collections.emptySet();
        assertEquals(Arrays.asList(gone, kept), Campus.staleRetires(jobs, none, b -> false));
        assertEquals(
            Collections.singletonList(gone),
            Campus.staleRetires(jobs, Collections.singleton(6), b -> false),
            "a docked hall still blocks slot 6");
        assertTrue(
            Campus.staleRetires(jobs, none, b -> true)
                .isEmpty(),
            "a hall still stands in the box");
        gone.started = true;
        kept.touched = true;
        assertTrue(
            Campus.staleRetires(jobs, none, b -> false)
                .isEmpty(),
            "begun: started, or something touched");
        gone.started = false;
        kept.touched = false;
        gone.state = BuildState.State.DONE;
        assertEquals(Collections.singletonList(kept), Campus.staleRetires(jobs, none, b -> false), "an ended job");
    }

    @Test
    void blockedCellsAreCappedButCounted() {
        BuildJob j = BuildJob.forum(Collections.emptyList());
        int n = BuildJob.MAX_TRACKED + 10;
        for (int i = 0; i < n; i++) j.addBlocked(i, 70, 0);
        j.addBlocked(0, 70, 0);
        assertEquals(n, j.blockedCount());
        assertEquals(BuildJob.MAX_TRACKED, j.blocked.size());
        assertEquals(
            BuildJob.MAX_BLOCKED,
            j.blockedCells()
                .size());
        long shown = j.blockedShownHash();
        j.unblock(BuildJob.MAX_TRACKED + 5, 70, 0);
        assertEquals(shown, j.blockedShownHash(), "a cell beyond the shown ones changes nothing shown");
        j.unblock(3, 70, 0);
        assertNotEquals(shown, j.blockedShownHash(), "a shown cell put right is sent again");
        j.clearBlocked();
        assertEquals(0, j.blockedCount());
    }

    @Test
    void effectBatchesKeepTheirTimes() {
        FxBatch b = new FxBatch();
        assertTrue(b.isEmpty());
        b.launch(FxCodec.pack(3, 4, -5), Parts.deck(Parts.D_LIT), 9, 1000);
        b.launch(FxCodec.pack(-3, 0, 5), Parts.frame(Parts.FR_BASE), 4, 1002);
        b.clear(FxCodec.pack(1, 1, 1), 1 << 4 | 2, 1003);
        assertEquals(1000, b.base());
        assertEquals(
            1,
            b.pages()
                .size());
        FxBatch.Page p = b.pages()
            .get(0);
        assertArrayEquals(new byte[] { 0, 2 }, p.launchOffsets());
        assertArrayEquals(new byte[] { 9, 4 }, p.launchFlights());
        assertArrayEquals(
            new byte[] { (byte) Parts.deck(Parts.D_LIT), (byte) Parts.frame(Parts.FR_BASE) },
            p.launchParts());
        assertEquals(-5, FxCodec.dz(p.launchCells()[0]));
        assertArrayEquals(new int[] { 1 << 4 | 2 }, p.clearBlocks());
        assertFalse(b.full());
        b.reset();
        assertTrue(b.isEmpty());
        assertEquals(-1, b.base());
    }

    @Test
    void aBusyTickLosesNoEffect() {
        // more launches and clears in one tick than one packet holds: they go on further pages, none is dropped
        FxBatch b = new FxBatch();
        int n = BuildJob.MAX_FLIGHTS + 44;
        for (int i = 0; i < n; i++) b.launch(FxCodec.pack(i % 30, 1, i / 30), Parts.deck(Parts.D_DECK), 6, 2000);
        for (int i = 0; i < n; i++) b.clear(FxCodec.pack(i % 30, 2, i / 30), 1 << 4, 2000);
        assertEquals(n, b.launches());
        assertEquals(n, b.clears());
        assertTrue(b.full(), "a full page sends the batch at once");
        int launches = 0, clears = 0;
        for (FxBatch.Page p : b.pages()) {
            assertTrue(p.launches() <= FxBatch.MAX && p.clears() <= FxBatch.MAX, "each packet stays small");
            assertEquals(2000, p.base());
            launches += p.launchCells().length;
            clears += p.clearCells().length;
        }
        assertEquals(n, launches);
        assertEquals(n, clears);
        // every launch keeps its own cell, in order
        int k = 0;
        for (FxBatch.Page p : b.pages()) for (int cell : p.launchCells()) {
            assertEquals(k % 30, FxCodec.dx(cell));
            assertEquals(k / 30, FxCodec.dz(cell));
            k++;
        }
        // an entry too late for the page's offsets starts a page of its own
        FxBatch late = new FxBatch();
        late.launch(0, 0, 4, 100);
        late.launch(0, 0, 4, 100 + 200);
        assertEquals(
            2,
            late.pages()
                .size());
        assertEquals(
            300,
            late.pages()
                .get(1)
                .base());
        // a runaway batch is bounded
        FxBatch huge = new FxBatch();
        for (int i = 0; i < FxBatch.MAX * (FxBatch.MAX_PAGES + 3); i++) huge.launch(0, 0, 4, 5);
        assertEquals(FxBatch.MAX * FxBatch.MAX_PAGES, huge.launches());
    }

    @Test
    void aCarriedCampusSurvivesTheItem() {
        Campus.Carried c = new Campus.Carried();
        c.dim = -1;
        c.core = new int[] { CX, Y0 + 2, CZ };
        c.front = 5;
        c.sites.put(4, new int[] { CX + 41, Y0 + 2, CZ });
        c.kinds.put(4, "library");
        c.sites.put(2, new int[] { CX, Y0 + 2, CZ - 41 });
        c.kinds.put(2, "");
        c.skip = true;
        c.declined.put("library", 123_456L);
        c.unfinished = BuildPlan.ESTABLISH;
        c.graded = BuildJob.GRADE_V;
        c.free = true;
        // the item's tag is copied whenever the stack is
        NBTTagCompound t = (NBTTagCompound) c.write()
            .copy();
        Campus.Carried back = Campus.Carried.read(t);
        assertEquals(-1, back.dim);
        assertArrayEquals(new int[] { CX, Y0 + 2, CZ }, back.core);
        assertEquals(5, back.front);
        assertEquals(2, back.sites.size());
        assertArrayEquals(new int[] { CX + 41, Y0 + 2, CZ }, back.sites.get(4));
        assertArrayEquals(new int[] { CX, Y0 + 2, CZ - 41 }, back.sites.get(2));
        assertEquals("library", back.kinds.get(4));
        assertEquals("", back.kinds.get(2));
        assertTrue(back.skip);
        assertEquals(123_456L, (long) back.declined.get("library"));
        assertEquals(BuildPlan.ESTABLISH, back.unfinished);
        assertEquals(BuildJob.GRADE_V, back.graded, "a campus graded by this plan is not offered a refresh again");
        assertTrue(back.free, "free building goes with the core");
        assertTrue(back.at(-1, CX, Y0 + 2, CZ), "put back where it stood");
        assertFalse(back.at(0, CX, Y0 + 2, CZ), "another dimension");
        assertFalse(back.at(-1, CX, Y0 + 1, CZ), "a block lower, on the console stand's cell");
        assertFalse(back.at(-1, CX + 1, Y0 + 2, CZ));

        // the forum, nothing unfinished, and what a tag that makes no sense gives
        c.unfinished = BuildPlan.FORUM;
        assertEquals(BuildPlan.FORUM, Campus.Carried.read(c.write()).unfinished);
        NBTTagCompound odd = c.write();
        odd.setString("Job", "module:library@4");
        odd.setByte("F", (byte) 9);
        Campus.Carried o = Campus.Carried.read(odd);
        assertEquals("", o.unfinished, "only the campus jobs are taken up again");
        assertEquals(2, o.front);
        assertNull(Campus.Carried.read(new NBTTagCompound()), "no campus without the core's position");
        assertNull(Campus.Carried.read(null));
        NBTTagCompound shortPos = c.write();
        shortPos.setIntArray("Pos", new int[] { 1, 2 });
        assertNull(Campus.Carried.read(shortPos));
    }

    /**
     * A repair of a module keeps the players' garden: its soft grass, dirt and water go back only where the cell is
     * empty, while its structure's cells and a first build's ground are judged as always.
     */
    @Test
    void aRepairKeepsTheGarden() {
        TerrainRule.Probe farmland = new TerrainRule.Probe();
        farmland.natural = true;
        BuildPlan.Step grass = new BuildPlan.Step(CX, Y0, CZ, Parts.GRASS, 0, BuildPlan.NO_GROUP, BuildPlan.SOFT),
            water = new BuildPlan.Step(CX, Y0 + 1, CZ, Parts.WATER, 0, BuildPlan.NO_GROUP, BuildPlan.SOFT),
            deck = new BuildPlan.Step(CX, Y0, CZ, Parts.deck(Parts.D_DECK), 0, BuildPlan.NO_GROUP, BuildPlan.SOFT),
            wall = new BuildPlan.Step(CX, Y0 + 1, CZ, Parts.deck(Parts.D_PANEL), 0, BuildPlan.NO_GROUP, BuildPlan.HARD);
        for (boolean all : new boolean[] { true, false }) {
            assertEquals(TerrainRule.Verdict.ALREADY, Builder.verdict(grass, farmland, true, all, true));
            assertEquals(TerrainRule.Verdict.ALREADY, Builder.verdict(water, farmland, true, all, true));
            assertEquals(
                TerrainRule.Verdict.CLEAR,
                Builder.verdict(grass, farmland, true, all, false),
                "a first build");
            assertEquals(TerrainRule.Verdict.CLEAR, Builder.verdict(deck, farmland, true, all, true), "a floor tile");
            assertEquals(TerrainRule.Verdict.CLEAR, Builder.verdict(wall, farmland, true, all, true), "the structure");
        }
        TerrainRule.Probe air = new TerrainRule.Probe();
        air.air = true;
        assertEquals(TerrainRule.Verdict.PLACE, Builder.verdict(grass, air, true, true, true), "a hole is filled");
    }

    /**
     * The full ring (0.11.0 to 0.14.0) keeps its status for its ordinal, and a list holding it still travels; since
     * 0.14.1 no module is ever given it: a researched module the campus offers is QUEUED, one it does not offer is
     * NO_ROOM with its reason ({@link com.fluxecho.logic.CityOffer}).
     */
    @Test
    void aFullRingIsNamed() {
        assertEquals(Campus.ModuleStatus.QUEUED, Campus.offerStatus(true));
        assertEquals(Campus.ModuleStatus.NO_ROOM, Campus.offerStatus(false), "the nexus does not stand");
        for (boolean offering : new boolean[] { true, false })
            assertNotEquals(Campus.ModuleStatus.RING_FULL, Campus.offerStatus(offering));
        assertEquals(4, Campus.ModuleStatus.NO_ROOM.ordinal());
        assertEquals(5, Campus.ModuleStatus.RING_FULL.ordinal());
        List<Campus.ModuleState> back = Campus.ModuleState.read(
            Campus.ModuleState.write(
                Collections
                    .singletonList(new Campus.ModuleState("eco_dome", "eco_dome", Campus.ModuleStatus.RING_FULL, 2))));
        assertEquals(Campus.ModuleStatus.RING_FULL, back.get(0).status);
    }

    @Test
    void onlyTheCellsThatBreakPayForIt() {
        // a bookcase of fifteen cells launched whole, one of which has a block to break first
        long eu = 0;
        int shelf = Parts.frame(Parts.FR_SHELF);
        for (int k = 0; k < 15; k++) eu += Builder.stepEu(BuildPlan.HARD, shelf, k == 7, 256, 16);
        assertEquals(15 * 256 + 16, eu);
        assertEquals(256, Builder.stepEu(BuildPlan.SOFT, Parts.deck(Parts.D_LIT), false, 256, 16));
        assertEquals(16, Builder.stepEu(BuildPlan.AIR, Parts.AIR, true, 256, 16), "a cell only cleared");
        assertEquals(16, Builder.stepEu(BuildPlan.HARD, Parts.GRASS, true, 256, 16), "grass costs the clearing EU");
        assertEquals(16, Builder.stepEu(BuildPlan.HARD, Parts.DIRT, false, 256, 16));
    }

    @Test
    void theRefreshIsTheForumOnceMoreAndGoesFirst() {
        List<int[]> boxes = new ArrayList<>();
        boxes.add(new int[] { 1, 2, 3, 4 });
        BuildJob r = BuildJob.read(
            BuildJob.refresh(boxes)
                .write());
        assertEquals(BuildJob.REFRESH, r.key());
        assertEquals(BuildPlan.FORUM, r.planKey(), "grading, floor, pylons, port and bin; no nexus");
        assertTrue(r.refresh());
        assertTrue(r.campusJob());
        assertTrue(r.priority());
        assertFalse(r.repair(), "a repair never grades; the refresh must");
        assertFalse(r.raisesNexus());
        assertNull(r.module());
        assertEquals(1, r.keepOut.size());
        assertTrue(r.clears(new BuildPlan.Column(0, 0, Y0 + 1, Y0 + 24, true)), "it grades like a first build");
        assertEquals("fluxecho.build.job.refresh", ((ChatComponentTranslation) r.name()).getKey());
        assertEquals(
            "fluxecho.build.job.refresh",
            ((ChatComponentTranslation) BuildJob.name(BuildJob.REFRESH, BuildPlan.FORUM)).getKey());
        int[] f = FACINGS[1];
        int[] ctrl = { CX + 5 * f[0], Y0 + 2, CZ + 5 * f[1] };
        CampusPlan cp = new CampusPlan(Mix.seed(ctrl[0], ctrl[1], ctrl[2]));
        BuildPlan.Plan a = BuildJob.planFor(r.planKey(), cp, CX, Y0, CZ, f[0], f[1], ctrl[0], ctrl[1], ctrl[2], boxes);
        BuildPlan.Plan b = BuildPlan.forum(cp, CX, Y0, CZ, f[0], f[1], boxes);
        assertEquals(b.steps, a.steps);

        // it goes ahead of a module job nobody started, behind a repair, and a started job keeps its place
        List<BuildJob> queue = new ArrayList<>();
        BuildJob lib = BuildJob.module(spec(), 4);
        lib.state = BuildState.State.PROJECTING;
        BuildJob refresh = BuildJob.refresh(Collections.emptyList());
        assertSame(refresh, Campus.admit(lib, queue, refresh));
        assertSame(lib, queue.get(0));
        BuildJob rep = BuildJob.repairNexus(Collections.emptyList());
        assertSame(refresh, Campus.admit(refresh, queue, rep), "neither gives way to the other");
        assertSame(rep, queue.get(0));
        assertSame(lib, queue.get(1));
        List<BuildJob> q2 = new ArrayList<>();
        BuildJob running = BuildJob.module(spec(), 2);
        running.state = BuildState.State.BUILDING;
        running.started = true;
        assertSame(running, Campus.admit(running, q2, BuildJob.refresh(Collections.emptyList())));
        assertEquals(1, q2.size());
    }

    @Test
    void theRecycleBinStepWaitsForItsBlock() {
        assertNull(PartBlocks.block(Parts.RECYCLE_BIN), "its block is registered with the bin, not by the engine");
        BuildPlan.Step bin = new BuildPlan.Step(
            CX,
            Y0 + 1,
            CZ,
            Parts.RECYCLE_BIN,
            BuildPlan.E_FIXTURE,
            BuildPlan.NO_GROUP,
            BuildPlan.SOFT);
        assertFalse(Builder.placeable(bin), "skipped, never paid for, until the block exists");
        assertTrue(Builder.placeable(new BuildPlan.Step(CX, Y0, CZ, Parts.AIR, 0, BuildPlan.NO_GROUP, BuildPlan.AIR)));
        assertTrue(Builder.gift(Parts.RECYCLE_BIN), "given once per spot, like the supply port");
        assertTrue(Builder.gift(Parts.SUPPLY_PORT));
        assertFalse(Builder.gift(Parts.deck(Parts.D_DECK)));
        assertFalse(Builder.gift(Parts.LIBRARY_CORE));

        // nobody is billed for the bin or the port of a campus job
        int[] f = FACINGS[0];
        int[] ctrl = { CX + 5 * f[0], Y0 + 2, CZ + 5 * f[1] };
        CampusPlan cp = new CampusPlan(Mix.seed(ctrl[0], ctrl[1], ctrl[2]));
        BuildPlan.Plan est = BuildPlan
            .establish(cp, CX, Y0, CZ, f[0], f[1], NexusShape.PHASE_1, ctrl[0], ctrl[1], ctrl[2]);
        int binSteps = 0;
        for (BuildPlan.Step s : est.steps) if (s.part == Parts.RECYCLE_BIN) binSteps++;
        assertEquals(1, binSteps, "the establish plan stands one bin");
        BuildJob j = BuildJob.establish(Collections.emptyList());
        Map<Integer, Integer> due = Campus.unbuilt(est, j);
        assertFalse(due.containsKey(Parts.RECYCLE_BIN));
        assertFalse(due.containsKey(Parts.SUPPLY_PORT));

        // once the bin's block is registered, the step is placed and the block read back as the part (any meta)
        Block block = new Block(Material.iron) {};
        CampusModule.recycleBin = block;
        try {
            assertTrue(Builder.placeable(bin));
            assertSame(block, PartBlocks.block(Parts.RECYCLE_BIN));
            assertEquals(Parts.RECYCLE_BIN, PartBlocks.code(block, 3));
            assertFalse(
                Campus.unbuilt(est, j)
                    .containsKey(Parts.RECYCLE_BIN),
                "still a gift to the campus job");
        } finally {
            CampusModule.recycleBin = null;
        }
    }

    @Test
    void freeBuildingIsTheCampusesSwitchAndTheJobs() {
        Campus c = new Campus(null);
        BuildJob j = BuildJob.forum(Collections.emptyList());
        assertFalse(c.free(j));
        assertFalse(c.free(null));
        j.free = true;
        assertTrue(c.free(j), "started in creative mode");
        j.free = false;
        assertEquals("fluxecho.build.free_on", c.setFreeBuild(true));
        assertTrue(c.freeBuild());
        assertTrue(c.free(j), "every job of the nexus");
        NBTTagCompound t = new NBTTagCompound();
        c.writeSync(t);
        Campus.View v = new Campus.View();
        v.read(t);
        assertTrue(v.freeBuild);
        assertEquals("fluxecho.build.free_off", c.setFreeBuild(false));
        assertFalse(c.free(j));
        assertFalse(c.instant(j), "an instant build is only what an operator asked for");
    }

    @Test
    void theModuleListTravels() {
        // the ordinals are sent: they never change
        assertEquals(0, Campus.ModuleStatus.NEEDS_RESEARCH.ordinal());
        assertEquals(1, Campus.ModuleStatus.QUEUED.ordinal());
        assertEquals(2, Campus.ModuleStatus.BUILDING.ordinal());
        assertEquals(3, Campus.ModuleStatus.BUILT.ordinal());
        assertEquals(4, Campus.ModuleStatus.NO_ROOM.ordinal());
        List<Campus.ModuleState> l = Arrays.asList(
            new Campus.ModuleState("library", "library", Campus.ModuleStatus.NEEDS_RESEARCH, 4),
            new Campus.ModuleState("dome", "dome", Campus.ModuleStatus.BUILT, 2),
            new Campus.ModuleState("tide", null, Campus.ModuleStatus.NO_ROOM, -1));
        net.minecraft.nbt.NBTTagList t = Campus.ModuleState.write(l);
        t.getCompoundTagAt(2)
            .setByte("S", (byte) 99);
        List<Campus.ModuleState> back = Campus.ModuleState.read(t);
        assertEquals(3, back.size());
        assertEquals("library", back.get(0).key);
        assertEquals("library", back.get(0).research);
        assertEquals(Campus.ModuleStatus.NEEDS_RESEARCH, back.get(0).status);
        assertEquals(4, back.get(0).site);
        assertEquals(Campus.ModuleStatus.BUILT, back.get(1).status);
        assertEquals(2, back.get(1).site);
        assertEquals("", back.get(2).research);
        assertEquals(-1, back.get(2).site);
        assertEquals(Campus.ModuleStatus.NO_ROOM, back.get(2).status, "an unknown status reads as no room");
        assertTrue(
            Campus.ModuleState.read(null)
                .isEmpty());
    }

    // ---- campuses with a nexus tile (no world: the tile answers its position and front, the server is not running)

    /** A nexus controller at (CX, Y0 + 2, CZ) facing the given way, in no world. */
    private static TileNexus nexus(int facing) {
        TileNexus n = new TileNexus();
        n.xCoord = CX;
        n.yCoord = Y0 + 2;
        n.zCoord = CZ;
        n.place((UUID) null, null, facing);
        return n;
    }

    /** Loads an active campus into the nexus: graded under {@code pv}, the job and queue given, sites as {S, P, K}. */
    private static Campus active(TileNexus n, int pv, BuildJob job, List<BuildJob> queue, NBTTagList sites) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("St", "active");
        t.setInteger("Pv", pv);
        if (job != null) t.setTag("Job", job.write());
        NBTTagList q = new NBTTagList();
        if (queue != null) for (BuildJob j : queue) q.appendTag(j.write());
        t.setTag("Queue", q);
        if (sites != null) t.setTag("Sites", sites);
        NBTTagCompound tile = new NBTTagCompound();
        tile.setTag("Campus", t);
        Campus c = n.campus();
        c.readNBT(tile);
        return c;
    }

    /** The recorded site of the test module (an Archive) on hall site 4 of the nexus. */
    private static NBTTagList archiveSite(TileNexus n) {
        int[] c = n.centre();
        int[] at = spec().controller(4, c[0], c[1], c[2], n.front().offsetX, n.front().offsetZ);
        NBTTagCompound s = new NBTTagCompound();
        s.setInteger("S", 4);
        s.setIntArray("P", new int[] { at[0], at[1], at[2] });
        s.setString("K", TEST_MODULE);
        NBTTagList l = new NBTTagList();
        l.appendTag(s);
        return l;
    }

    @Test
    void theRefreshKeepsOffAStandingArchivesForecourtApronAndCanopy() {
        for (int facing = 2; facing <= 5; facing++) {
            TileNexus n = nexus(facing);
            Campus c = active(n, 0, null, null, archiveSite(n));
            int[] ce = n.centre();
            int fx = n.front().offsetX, fz = n.front().offsetZ;
            BuildPlan.Plan arch = spec().plan(c.plan(), 4, ce[0], ce[1], ce[2], fx, fz);
            // every column the Archive's job laid something in or cleared: the building, the forecourt, the apron, the
            // canopy over its door
            Set<Long> site = new HashSet<>();
            for (BuildPlan.Step s : arch.steps) site.add(BuildJob.pos(s.x, 0, s.z));
            for (BuildPlan.Column col : arch.clear) site.add(BuildJob.pos(col.x, 0, col.z));
            // the canopy and the forecourt lie outside the footprint and its margin (0.10.0's keep-out box)
            int[] old = spec().footprint(4, ce[0], ce[2], fx, fz, 1);
            int outside = 0;
            for (long p : site) {
                int[] q = BuildJob.unpack(p);
                if (q[0] < Math.min(old[0], old[2]) || q[0] > Math.max(old[0], old[2])
                    || q[2] < Math.min(old[1], old[3])
                    || q[2] > Math.max(old[1], old[3])) outside++;
            }
            assertTrue(outside > 0, "the forecourt and canopy reach past the footprint");

            // the refresh (the forum plan) with the campus's keep-out never grades nor paves any of it
            List<int[]> keepOut = c.keepOut();
            assertEquals(1, keepOut.size());
            BuildPlan.Plan refresh = BuildJob
                .planFor(BuildPlan.FORUM, c.plan(), ce[0], ce[1], ce[2], fx, fz, n.xCoord, n.yCoord, n.zCoord, keepOut);
            for (BuildPlan.Column col : refresh.clear)
                assertFalse(site.contains(BuildJob.pos(col.x, 0, col.z)), "graded inside the Archive's site: " + col);
            for (BuildPlan.Step s : refresh.steps)
                assertFalse(site.contains(BuildJob.pos(s.x, 0, s.z)), "paved inside the Archive's site: " + s);
            assertFalse(refresh.clear.isEmpty(), "the rest of the disc is graded");
        }
    }

    @Test
    void aRefreshIsNotedOnlyOnceItGraded() {
        TileNexus n = nexus(3);
        Campus c = active(n, 0, null, null, null);
        c.offerRefresh();
        assertTrue(
            c.job()
                .refresh());
        assertEquals(
            0,
            c.carried()
                .getInteger("Pv"),
            "a core broken while the refresh waits carries the old version: put back, it is offered again");
        c.offerRefresh();
        assertTrue(
            c.queue()
                .isEmpty(),
            "never offered twice");
        c.graded(c.job());
        assertEquals(
            BuildJob.GRADE_V,
            c.carried()
                .getInteger("Pv"));

        // a cancelled repair of the nexus says nothing about the floor; a cancelled refresh declines it
        assertFalse(Campus.declinesRefresh(BuildJob.repairNexus(Collections.emptyList())));
        assertTrue(Campus.declinesRefresh(BuildJob.refresh(Collections.emptyList())));
        assertTrue(Campus.declinesRefresh(BuildJob.forum(Collections.emptyList())));
        assertFalse(Campus.declinesRefresh(BuildJob.module(spec(), 4)));
        BuildJob repair = BuildJob.repairNexus(Collections.emptyList());
        repair.state = BuildState.State.PROJECTING;
        Campus r = active(nexus(3), 0, repair, null, null);
        r.cancelJob();
        assertEquals(
            0,
            r.carried()
                .getInteger("Pv"),
            "the refresh is still offered");
        BuildJob refresh = BuildJob.refresh(Collections.emptyList());
        refresh.state = BuildState.State.PROJECTING;
        Campus d = active(nexus(3), 0, refresh, null, null);
        d.cancelJob();
        assertEquals(
            BuildJob.GRADE_V,
            d.carried()
                .getInteger("Pv"),
            "declined for good");
    }

    /**
     * 0.14.1 raised the plan version (the Eco Dome's plan lost a step) but not the grading: a campus graded or that
     * declined the refresh under 0.10.1 to 0.14.0 ({@code Pv} 2) is not offered 整修园区 again, while a module job
     * saved under the old plans still starts its books over.
     */
    @Test
    void aNewPlanVersionOffersNoRefreshAgain() {
        assertEquals(3, BuildJob.V);
        assertEquals(2, BuildJob.GRADE_V);
        Campus c = active(nexus(3), 2, null, null, null);
        c.offerRefresh();
        assertNull(c.job(), "graded under 0.14.0: nothing offered");
        assertTrue(
            c.queue()
                .isEmpty());
        Campus old = active(nexus(3), 1, null, null, null);
        old.offerRefresh();
        assertTrue(
            old.job()
                .refresh(),
            "a campus graded before 0.10.1 still is");

        BuildJob saved = BuildJob.module(spec(), 4);
        NBTTagCompound t = saved.write();
        t.setInteger("V", 2);
        assertEquals(
            2,
            BuildJob.read(t)
                .version(),
            "a job saved by 0.14.0 keeps its version until the campus loads it, which starts its books over");
    }

    @Test
    void finishingAtOnceMovesPastAFinishedJob() {
        BuildJob done = BuildJob.establish(Collections.emptyList());
        done.state = BuildState.State.DONE;
        done.finishedAt = 5;
        BuildJob next = BuildJob.module(spec(), 4);
        next.state = BuildState.State.PROJECTING;
        TileNexus n = nexus(2);
        Campus c = active(n, BuildJob.GRADE_V, done, Collections.singletonList(next), null);
        assertEquals("fluxecho.build.instant_started", c.finishNow(null));
        assertEquals(
            next.key(),
            c.job()
                .key(),
            "the queued job, not the finished one that lingers");
        assertTrue(c.instant(c.job()));
        assertTrue(c.free(c.job()));

        // the instant mode ends at a pause someone must put right, and goes on at the normal pace afterwards
        c.paused(c.job(), BuildState.Pause.BLOCKED);
        assertFalse(c.instant(c.job()));
        for (BuildState.Pause p : BuildState.Pause.values()) {
            boolean ends = p == BuildState.Pause.BLOCKED || p == BuildState.Pause.PROTECTED
                || p == BuildState.Pause.UNLOADED
                || p == BuildState.Pause.INCOMPLETE;
            assertEquals(ends, Campus.endsInstant(p), p.name());
        }
        assertEquals("fluxecho.build.no_job", active(nexus(2), 0, done, null, null).finishNow(null));
    }

    @Test
    void aCampusJobTakesTheKeepOutOfNowWhenItStarts() {
        TileNexus n = nexus(4);
        BuildJob forum = BuildJob.forum(Collections.emptyList());
        forum.state = BuildState.State.PROJECTING;
        Campus c = active(n, BuildJob.GRADE_V, forum, null, archiveSite(n));
        // made with nothing kept out (the Archive's chunk was not loaded, say); started: it keeps off the Archive
        c.finishNow(null);
        assertEquals(
            1,
            c.job()
                .keepOut()
                .size());
        assertTrue(
            Arrays.equals(
                c.keepOut()
                    .get(0),
                c.job()
                    .keepOut()
                    .get(0)));
        assertEquals(
            BuildState.State.SURVEY,
            c.job()
                .state(),
            "surveyed again for the new plan");

        BuildJob j = BuildJob.forum(Collections.singletonList(new int[] { 1, 2, 3, 4 }));
        j.state = BuildState.State.PROJECTING;
        j.projectedAt = 7;
        assertFalse(j.retarget(Collections.singletonList(new int[] { 1, 2, 3, 4 })), "the same boxes: nothing to do");
        assertEquals(7, j.projectedAt);
        BuildJob two = BuildJob.forum(Arrays.asList(new int[] { 1, 2, 3, 4 }, new int[] { 5, 6, 7, 8 }));
        two.state = BuildState.State.PROJECTING;
        assertFalse(
            two.retarget(Arrays.asList(new int[] { 5, 6, 7, 8 }, new int[] { 1, 2, 3, 4 })),
            "boxes in another order are the same");
        assertTrue(j.retarget(Arrays.asList(new int[] { 1, 2, 3, 4 }, new int[] { 9, 9, 9, 9 })));
        assertEquals(
            2,
            j.keepOut()
                .size());
        assertEquals(BuildState.State.SURVEY, j.state(), "a new plan: surveyed and projected again");
        assertEquals(-1, j.projectedAt);
        BuildJob touched = BuildJob.forum(Collections.emptyList());
        touched.state = BuildState.State.BUILDING;
        touched.touched = true;
        assertFalse(touched.retarget(Collections.singletonList(new int[] { 1, 2, 3, 4 })), "what it built stays");
        assertTrue(
            touched.keepOut()
                .isEmpty());
    }

    @Test
    void cellsLeftForAnEntityWaitForTheTouchUp() {
        int[] f = FACINGS[2];
        int[] ctrl = { CX + 5 * f[0], Y0 + 2, CZ + 5 * f[1] };
        CampusPlan cp = new CampusPlan(Mix.seed(ctrl[0], ctrl[1], ctrl[2]));
        BuildPlan.Plan plan = BuildPlan.forum(cp, CX, Y0, CZ, f[0], f[1]);
        BuildJob j = BuildJob.forum(Collections.emptyList());
        assertFalse(j.needsTouchUp());

        // grading cells an animal stood in: kept once each, given back lowest first (a hole fills from the bottom)
        j.leftOpen(CX + 30, Y0, CZ);
        j.leftOpen(CX + 30, Y0 - 2, CZ);
        j.leftOpen(CX + 30, Y0 - 1, CZ);
        j.leftOpen(CX + 30, Y0, CZ);
        List<int[]> open = j.openCells();
        assertEquals(3, open.size());
        assertEquals(Y0 - 2, open.get(0)[1]);
        assertEquals(Y0 - 1, open.get(1)[1]);
        assertEquals(Y0, open.get(2)[1]);

        // a floor tile and a pylon that gave way to a player: skipped, and marked for another try
        int tile = plan.start(BuildPlan.E_FLOOR), pylon = plan.start(BuildPlan.E_FIXTURE);
        assertEquals(BuildPlan.SOFT, plan.steps.get(tile).kind);
        for (int i : new int[] { tile, pylon }) {
            j.skipped.set(i);
            j.gaveWay(i);
        }
        assertTrue(j.needsTouchUp());
        assertEquals(-1, j.retryGaveWay(plan, i -> false), "nobody moved yet");
        assertTrue(j.skipped(tile));

        // saved with the job
        j.touchUpSince = 1234;
        BuildJob back = BuildJob.read(j.write());
        assertEquals(
            3,
            back.openCells()
                .size());
        assertEquals(
            Y0 - 2,
            back.openCells()
                .get(0)[1]);
        assertTrue(back.gaveWay.get(tile));
        assertTrue(back.gaveWay.get(pylon));
        assertEquals(1234, back.touchUpSince);

        // the pylon's cell is free: it alone goes back, to its stage; then the tile, to the earlier stage
        assertEquals(BuildPlan.E_FIXTURE, back.retryGaveWay(plan, i -> i == pylon));
        assertFalse(back.skipped(pylon));
        assertTrue(back.skipped(tile));
        assertEquals(BuildPlan.E_FLOOR, back.retryGaveWay(plan, i -> true));
        assertFalse(back.skipped(tile));
        assertFalse(back.gaveWay.get(tile));
        back.touchUp.clear();
        assertFalse(back.needsTouchUp());

        // a step the plan no longer has is forgotten; a new plan forgets everything
        BuildJob stray = BuildJob.forum(Collections.emptyList());
        stray.gaveWay(plan.steps.size() + 5);
        assertEquals(-1, stray.retryGaveWay(plan, i -> true));
        assertFalse(stray.needsTouchUp());
        j.restart(false);
        assertFalse(j.needsTouchUp());
        assertEquals(-1, j.touchUpSince);
        j.leftOpen(1, 2, 3);
        j.endTouchUp();
        assertFalse(j.needsTouchUp(), "given up: left as it is");
    }

    @Test
    void theSkipSwitchReachesTheClientWithoutAJob() {
        Campus c = new Campus(null);
        c.setSkipBlocked(true);
        NBTTagCompound t = new NBTTagCompound();
        c.writeSync(t);
        Campus.View v = new Campus.View();
        v.read(t);
        assertTrue(v.skipBlocked, "跳过受阻 is the campus's, shown with no job too");
        assertFalse(v.hasJob());
    }

    /**
     * The root's caisson (0.12.0): its dig and drain steps take the dig and drain rules, and a repair's dig only
     * drains.
     */
    @Test
    void theCaissonsStepsDigAndDrain() {
        BuildPlan.Step dig = new BuildPlan.Step(0, 40, 0, Parts.AIR, 0, BuildPlan.NO_GROUP, BuildPlan.DIG),
            drain = new BuildPlan.Step(0, 40, 0, Parts.AIR, 1, BuildPlan.NO_GROUP, BuildPlan.DRAIN);
        TerrainRule.Probe stone = new TerrainRule.Probe();
        stone.natural = true;
        TerrainRule.Probe water = new TerrainRule.Probe();
        water.fluid = water.replaceable = water.natural = true;
        for (boolean all : new boolean[] { true, false }) {
            assertEquals(TerrainRule.Verdict.CLEAR, Builder.verdict(dig, stone, true, all, false));
            assertEquals(TerrainRule.Verdict.ALREADY, Builder.verdict(dig, stone, true, all, true), "a repair drains");
            assertEquals(TerrainRule.Verdict.CLEAR, Builder.verdict(dig, water, true, all, true));
            assertEquals(TerrainRule.Verdict.ALREADY, Builder.verdict(drain, stone, true, all, false));
            assertEquals(TerrainRule.Verdict.CLEAR, Builder.verdict(drain, water, true, all, false));
        }
        stone.target = true;
        assertEquals(
            TerrainRule.Verdict.ALREADY,
            Builder.verdict(dig, stone, true, true, false),
            "what fills it stays");
    }

    /**
     * The survey before 开始 marks no caisson cell done (0.12.0): an empty cave pocket inside the shaft is judged done
     * by the dig rule, but something may stand there by the time its band is sunk, so only the band's own pass marks
     * it.
     */
    @Test
    void theSurveyLeavesTheCaissonToItsBand() {
        BuildPlan.Step dig = new BuildPlan.Step(0, 40, 0, Parts.AIR, 0, BuildPlan.NO_GROUP, BuildPlan.DIG);
        TerrainRule.Probe air = new TerrainRule.Probe();
        air.air = true;
        for (boolean all : new boolean[] { true, false })
            assertEquals(TerrainRule.Verdict.ALREADY, Builder.verdict(dig, air, true, all, false), "empty: dug");
        assertFalse(BuildPlan.surveySettles(BuildPlan.DIG));
        assertFalse(BuildPlan.surveySettles(BuildPlan.DRAIN));
        for (byte kind : new byte[] { BuildPlan.HARD, BuildPlan.SOFT, BuildPlan.AIR })
            assertTrue(BuildPlan.surveySettles(kind), "kind " + kind);
    }

    /**
     * A formed nexus covers only as far as the phase it may form as next (0.12.0): a phase-I nexus reaches 12 blocks,
     * as in 0.10.1, so a frame block of ours left 20 blocks in front of it is loose and the clear-all rule takes it
     * down and credits it, while a block inside its base is not; a phase-II nexus reaches as far as phase III.
     */
    @Test
    void ourStrayBlocksInFrontOfAPhaseOneNexusAreLoose() {
        for (int facing = 2; facing <= 5; facing++) {
            TileNexus n = new TileNexus() {

                @Override
                public boolean formed() {
                    return true;
                }
            };
            n.xCoord = CX;
            n.yCoord = Y0 + 2;
            n.zCoord = CZ;
            n.place((UUID) null, null, facing);
            int fx = n.front().offsetX, fz = n.front().offsetZ;
            List<TileNexus> loaded = Collections.singletonList(n);
            int x = CX + 20 * fx, z = CZ + 20 * fz;
            assertFalse(Terrain.inFormedReach(loaded, null, x, Y0 + 1, z), "20 in front: left over");
            TerrainRule.Probe p = new TerrainRule.Probe();
            p.ours = true;
            p.loose = !Terrain.inFormedReach(loaded, null, x, Y0 + 1, z);
            assertEquals(TerrainRule.Verdict.REPLACE, TerrainRule.forClear(p, false, true), "taken down and credited");
            assertTrue(Terrain.inFormedReach(loaded, null, CX + 3 * fx, Y0 + 1, CZ + 3 * fz), "on its base");
            assertTrue(Terrain.inFormedReach(loaded, null, CX, Y0 + 2 + 12, CZ), "its next stage's height");
            assertFalse(Terrain.inFormedReach(loaded, null, CX, Y0 + 2 + 13, CZ));
            // grown to phase II, it covers phase III: 17
            n.regrow(2);
            assertTrue(Terrain.inFormedReach(loaded, null, CX + 17 * fx, Y0 + 1, CZ + 17 * fz));
            assertFalse(Terrain.inFormedReach(loaded, null, x, Y0 + 1, z));
        }
    }

    /**
     * A formed zenith or root covers only its own box (0.12.0), not the cube of its longest side round its controller
     * (50 each way for the zenith, 27 for the root): a frame block of ours left on a 0.9.2 hall site (radius 32, at
     * the front, the side or a diagonal) is loose and the clear-all rule takes it down and credits it, while a cell of
     * a leg, of the pod or of the well is covered.
     */
    @Test
    void ourStrayBlocksOnTheHallSitesAreLooseRoundTheZenithAndTheRoot() {
        int[][] halls = { { 32, 0 }, { 0, 32 }, { 0, -32 }, { -32, 0 }, { 22, 22 }, { 22, -22 }, { -22, 22 } };
        for (int[] f : FACINGS) {
            int[] zc = DomeGeneratorShape.controller(SiteFamily.ZENITH_SITE, CX, Y0, CZ, f[0], f[1]);
            TileDomeGenerator zenith = new TileDomeGenerator() {

                @Override
                public boolean formed() {
                    return true;
                }
            };
            zenith.xCoord = zc[0];
            zenith.yCoord = zc[1];
            zenith.zCoord = zc[2];
            zenith.place((UUID) null, null, zc[3]);
            int[] rc = RootShape.controller(SiteFamily.ROOT_SITE, CX, Y0, CZ, f[0], f[1]);
            TileRoot root = new TileRoot() {

                @Override
                public boolean formed() {
                    return true;
                }
            };
            root.xCoord = rc[0];
            root.yCoord = rc[1];
            root.zCoord = rc[2];
            root.place((UUID) null, null, rc[3]);
            for (List<? extends TileMultiblock> loaded : Arrays.<List<? extends TileMultiblock>>asList(
                Collections.singletonList(zenith),
                Collections.singletonList(root))) {
                for (int[] h : halls) for (int dy : new int[] { 1, 3 }) {
                    int[] w = CampusPlan.toWorld(h[0], h[1], f[0], f[1]);
                    String at = "hall cell " + h[0] + "," + h[1] + " +" + dy + " facing " + f[0] + "," + f[1];
                    assertFalse(Terrain.inFormedReach(loaded, null, CX + w[0], Y0 + dy, CZ + w[1]), at);
                    TerrainRule.Probe p = new TerrainRule.Probe();
                    p.ours = true;
                    p.loose = !Terrain.inFormedReach(loaded, null, CX + w[0], Y0 + dy, CZ + w[1]);
                    assertEquals(TerrainRule.Verdict.REPLACE, TerrainRule.forClear(p, false, true), at);
                }
            }
            List<TileDomeGenerator> zl = Collections.singletonList(zenith);
            for (int[] leg : DomeGeneratorShape.legs()) {
                int[] w = CampusPlan.toWorld(leg[0], leg[1], f[0], f[1]);
                assertTrue(Terrain.inFormedReach(zl, null, CX + w[0], Y0 + leg[2], CZ + w[1]), "a leg cell");
            }
            int[] pod = CampusPlan.toWorld(0, 9, f[0], f[1]);
            assertTrue(Terrain.inFormedReach(zl, null, CX + pod[0], Y0 + 45, CZ + pod[1]), "the pod");
            int[] wall = CampusPlan.toWorld(0, 11, f[0], f[1]);
            assertTrue(
                Terrain.inFormedReach(Collections.singletonList(root), null, CX + wall[0], Y0 - 10, CZ + wall[1]),
                "the well");
        }
    }

    /**
     * The builder's protected boxes round a standing gatehouse (0.14.0, CITY.md §2.2): the gate is a boxed site like
     * the axis sites, so the gatehouse's multiblock bounds (31 x 17 x 18, which take in the gate road and the four
     * establish pylons in its open passage) are left out and its site's own boxes ({@link ModuleSpec#protect}) are
     * what the campus protects. Every road tile and pylon block a forum (the refresh, a repair of the campus) lays
     * there lies inside those bounds yet outside every protected box, so the builder lays them under a standing gate;
     * every block of the gatehouse's own plan (its wings, the bridge from Y0 + 7, the kiosk) stays protected.
     */
    @Test
    void theGatehouseLeavesItsRoadAndPylonsToTheNexussJobs() {
        for (int site = 0; site <= SiteFamily.ROOT_SITE; site++) assertEquals(
            site == 0 || site == SiteFamily.ZENITH_SITE || site == SiteFamily.ROOT_SITE,
            Campus.boxedSite(site),
            "site " + site);
        ModuleSpec spec = CityModule.resonanceGate();
        CampusPlan campus = new CampusPlan(29L);
        for (int[] f : FACINGS) {
            int[] c = spec.controller(0, CX, Y0, CZ, f[0], f[1]);
            TileResonanceGate gate = new TileResonanceGate();
            gate.xCoord = c[0];
            gate.yCoord = c[1];
            gate.zCoord = c[2];
            gate.place((UUID) null, null, c[3]);
            List<int[]> bounds = Collections.singletonList(gate.bounds());
            List<int[]> keep = spec.keepOut(campus, 0, CX, Y0, CZ, f[0], f[1]);
            List<int[]> protect = spec.protect(campus, 0, CX, Y0, CZ, f[0], f[1]);
            String at = " facing " + f[0] + "," + f[1];
            int road = 0, pylons = 0;
            for (BuildPlan.Step s : BuildPlan.forum(campus, CX, Y0, CZ, f[0], f[1], keep).steps) {
                int[] l = CampusPlan.toLocal(s.x - CX, s.z - CZ, f[0], f[1]);
                boolean tile = s.stage == BuildPlan.E_FLOOR && CampusPlan.inGatePassage(l[0], l[1]);
                boolean pylon = s.stage == BuildPlan.E_FIXTURE && Math.abs(l[1]) == 5 && (l[0] == 27 || l[0] == 35);
                if (!tile && !pylon) continue;
                String what = (tile ? "road tile " : "pylon block ") + l[0] + "," + l[1] + " y" + (s.y - Y0) + at;
                assertTrue(Campus.inBoxes(bounds, s.x, s.y, s.z), "inside the gatehouse's bounds: " + what);
                assertFalse(Campus.inBoxes(protect, s.x, s.y, s.z), "protected: " + what);
                if (tile) road++;
                else pylons++;
            }
            assertTrue(road > 100, "the road through the passage: " + road + at);
            assertEquals(4 * (BuildPlan.PYLON_PILLARS + 1), pylons, "the four pylons" + at);
            int held = 0;
            for (BuildPlan.Step s : spec.plan(campus, 0, CX, Y0, CZ, f[0], f[1]).steps) {
                assertTrue(Campus.inBoxes(protect, s.x, s.y, s.z), "the gatehouse's own block " + s + at);
                held++;
            }
            assertTrue(held > 2774, "its shape's steps and the floor round it: " + held);
            // a cell of the left wing's wall, the bridge's cornice at Y0 + 7 and the kiosk's console at Y0 + 1
            for (int[] g : new int[][] { { 0, 3, 0 }, { 9, 7, 5 }, { 15, 1, 7 } }) {
                int[] w = CampusPlan.toWorld(g[2] + 23, g[0] - 15, f[0], f[1]);
                assertTrue(Campus.inBoxes(protect, CX + w[0], Y0 + g[1], CZ + w[1]), g[0] + "," + g[1] + "," + g[2]);
            }
            int[] under = CampusPlan.toWorld(29, 0, f[0], f[1]);
            assertFalse(
                Campus.inBoxes(protect, CX + under[0], Y0 + CampusPlan.GATE_HEADROOM - 1, CZ + under[1]),
                "the passage's headroom under the bridge" + at);
        }
    }

    /**
     * A growth that waits for 开始 holds no repair of a broken nexus up (0.12.0): the repair takes its place and the
     * growth goes back to the head of the queue, and 开始 on it is refused while the nexus is unformed. A growth that
     * has begun (its lift pad landing takes the nexus apart for a moment) holds it up, as the establish job does, and
     * 开始 resumes it: the two rules never both close.
     */
    @Test
    void aGrowthWaitingForStartHoldsNoRepairOfTheNexus() {
        BuildJob grow = BuildJob.grow(2);
        grow.state = BuildState.State.PROJECTING;
        assertFalse(Campus.holdsNexusRepair(grow), "offered, not started");
        assertFalse(Campus.holdsNexusRepair(BuildJob.grow(2)), "queued behind another job");
        List<BuildJob> queue = new ArrayList<>();
        BuildJob rep = BuildJob.repairNexus(Collections.emptyList(), 1);
        assertSame(rep, Campus.admit(grow, queue, rep), "the repair takes its place");
        assertSame(grow, queue.get(0), "and it waits at the head of the queue");
        assertTrue(Campus.growsUnformed(grow, false), "开始 refused while the nexus is unformed");
        assertFalse(Campus.growsUnformed(grow, true));
        assertFalse(Campus.growsUnformed(BuildJob.module(spec(), 4), false), "only a growth");

        BuildJob started = BuildJob.grow(2);
        started.state = BuildState.State.BUILDING;
        started.started = true;
        assertTrue(Campus.holdsNexusRepair(started));
        BuildJob touched = BuildJob.grow(2);
        touched.state = BuildState.State.BUILDING;
        touched.started = true;
        touched.touched = true;
        assertTrue(Campus.holdsNexusRepair(touched));
        assertFalse(Campus.growsUnformed(touched, false), "a growth that has begun goes on");
        BuildJob flying = BuildJob.grow(2);
        flying.state = BuildState.State.PROJECTING;
        flying.flights.add(new BuildJob.Flight(0, 10, false, true));
        assertTrue(Campus.holdsNexusRepair(flying));
        // started but nothing placed yet, then paused by the player: 开始 resumes it (修复 is held by it)
        BuildJob paused = BuildJob.grow(2);
        paused.state = BuildState.State.PAUSED;
        paused.pause = BuildState.Pause.PLAYER;
        paused.started = true;
        assertTrue(Campus.holdsNexusRepair(paused));
        assertFalse(Campus.growsUnformed(paused, false), "a started growth is resumed, not refused");
        // over every begun-or-not combination of a growth that has not ended, an unformed nexus leaves exactly one of
        // 开始 and 修复 open
        for (int bits = 0; bits < 8; bits++) {
            BuildJob j = BuildJob.grow(2);
            j.state = BuildState.State.PROJECTING;
            j.started = (bits & 1) != 0;
            j.touched = (bits & 2) != 0;
            if ((bits & 4) != 0) j.flights.add(new BuildJob.Flight(0, 10, false, true));
            assertTrue(
                Campus.holdsNexusRepair(j) != Campus.growsUnformed(j, false),
                "exactly one of 修复 and 开始 for combination " + bits);
        }
        assertTrue(Campus.holdsNexusRepair(BuildJob.establish(Collections.emptyList())));
        assertTrue(Campus.holdsNexusRepair(BuildJob.repairNexus(Collections.emptyList(), 2)));
        assertFalse(Campus.holdsNexusRepair(BuildJob.module(spec(), 4)));
        started.state = BuildState.State.CANCELLED;
        assertFalse(Campus.holdsNexusRepair(started), "over");
    }

    /**
     * 修复 on a growth that stopped as incomplete (0.12.0) does not start the growth over (its plan cannot put a broken
     * base right): the growth ends, not declined, and the nexus is repaired as the new phase once the growth's stage
     * and pad stand, else as the phase it grew to.
     */
    @Test
    void repairingAnIncompleteGrowthRepairsTheNexusAsItsPhase() {
        BuildJob g = BuildJob.grow(2);
        g.state = BuildState.State.PAUSED;
        g.pause = BuildState.Pause.INCOMPLETE;
        g.started = true;
        g.touched = true;
        g.stage = BuildPlan.E_FORM;
        Campus c = active(nexus(3), BuildJob.GRADE_V, g, null, null);
        assertEquals("fluxecho.build.repair_queued", c.repairNow());
        assertEquals(
            BuildState.State.CANCELLED,
            c.job()
                .state());
        assertEquals(Collections.singletonList("repair:establish"), c.queue());
        NBTTagCompound saved = new NBTTagCompound();
        c.writeNBT(saved);
        BuildJob r = BuildJob.read(
            saved.getCompoundTag("Campus")
                .getTagList("Queue", 10)
                .getCompoundTagAt(0));
        assertEquals(BuildPlan.establishKey(2), r.planKey(), "the new phase: base, stages and pad");
        assertFalse(
            c.carried()
                .getCompoundTag("Declined")
                .hasKey(BuildPlan.growKey(2)),
            "not declined");

        BuildJob early = BuildJob.grow(2);
        early.stage = BuildPlan.E_CORE;
        assertEquals(1, Campus.repairPhase(early, 1), "its stage does not stand yet");
        assertEquals(2, Campus.repairPhase(g, 1));
        assertEquals(3, Campus.repairPhase(g, 3), "never lower than the nexus grew");
        assertEquals(2, Campus.repairPhase(BuildJob.module(spec(), 4), 2));
    }

    /**
     * The zenith's height-ranged keep-out boxes (0.12.0) are saved apart ({@code Kr}), so a job with only whole-column
     * boxes is saved exactly as before (no new key, {@code Ko} as it was), and both kinds come back.
     */
    @Test
    void heightRangedBoxesAreSavedApart() {
        List<int[]> old = Arrays.asList(new int[] { 1, 2, 3, 4 }, new int[] { -10, -20, -5, -8 });
        NBTTagCompound before = BuildJob.forum(old)
            .write();
        assertFalse(before.hasKey("Kr"), "a 0.11.0 job's save is unchanged");
        assertArrayEquals(new int[] { 1, 2, 3, 4, -10, -20, -5, -8 }, before.getIntArray("Ko"));

        List<int[]> mixed = Arrays.asList(new int[] { 1, 2, 3, 4 }, new int[] { 7, 8, 7, 8, Y0 + 18, Y0 + 26 });
        NBTTagCompound t = BuildJob.forum(mixed)
            .write();
        assertArrayEquals(new int[] { 1, 2, 3, 4 }, t.getIntArray("Ko"));
        assertArrayEquals(new int[] { 7, 8, 7, 8, Y0 + 18, Y0 + 26 }, t.getIntArray("Kr"));
        BuildJob r = BuildJob.read(t);
        assertEquals(2, r.keepOut.size());
        assertTrue(BuildJob.sameBoxes(mixed, r.keepOut), "both kinds back");
        assertFalse(r.retarget(mixed), "the same boxes after a load: no new survey");
        // a box of another size is no keep-out box
        assertEquals(
            1,
            BuildJob.forum(Arrays.asList(new int[] { 1, 2, 3 }, new int[] { 1, 2, 3, 4 }))
                .keepOut()
                .size());
    }

    /**
     * A doorway the weather or a player filled after the builder emptied it (0.14.2). The user's Resonance Gate and
     * Dual-foil Vault were built while it snowed: snow layers settled in two doorways of the gate (local 23,1,7 the
     * right wing's door onto the passage, and 8,9,10) and four of the vault's front door (10,1,1 among them) after
     * their
     * AIR steps had run, and the builder's form check looked only at HARD steps again, so both jobs paused as 结构未成型
     * for good. Now a done AIR step is looked at again too: a block the structure refuses there (a player's block, a
     * full snow layer) sends the job back to the step's stage with that step unfinished; a snow layer the structure
     * takes is left as it is, and so is air. A skipped AIR step whose doorway is open now is done as it is. Only the
     * doorway's cell exists in the world, so every other done step is passed over as unloaded.
     */
    @Test
    void aDoorwayFilledAfterItsStepRanIsBuiltAgain() {
        Block snow = new BlockSnow() {}, stone = new Block(Material.rock) {};
        CampusPlan campus = new CampusPlan(29L);
        Campus c = nexus(3).campus();
        Object[][] cases = { { CityModule.resonanceGate(), 0, new TileResonanceGate(), new int[] { 23, 1, 7 } },
            { CityModule.resonanceGate(), 0, new TileResonanceGate(), new int[] { 8, 9, 10 } },
            { CityModule.dualFoilVault(), 6, new TileDualFoilVault(), new int[] { 10, 1, 1 } } };
        for (Object[] k : cases) for (int[] f : FACINGS) {
            ModuleSpec spec = (ModuleSpec) k[0];
            int site = (Integer) k[1];
            TileCityModule tile = (TileCityModule) k[2];
            int[] local = (int[]) k[3];
            String at = spec.key + " local " + Arrays.toString(local) + " facing " + f[0] + "," + f[1];
            BuildPlan.Plan plan = spec.plan(campus, site, CX, Y0, CZ, f[0], f[1]);
            int[] ctrl = spec.controller(site, CX, Y0, CZ, f[0], f[1]);
            tile.xCoord = ctrl[0];
            tile.yCoord = ctrl[1];
            tile.zCoord = ctrl[2];
            tile.place((UUID) null, null, ctrl[3]);
            int[] door = tile.worldOf(local[0], local[1], local[2]);
            int i = -1;
            for (int j = 0; j < plan.steps.size(); j++) if (plan.steps.get(j)
                .at(door[0], door[1], door[2])) i = j;
            assertTrue(i >= 0, "the doorway is a step of the plan: " + at);
            BuildPlan.Step step = plan.steps.get(i);
            assertEquals(BuildPlan.AIR, step.kind, at);
            assertEquals(BuildPlan.M_SHELL, step.stage, at);
            BuildJob job = BuildJob.module(spec, site);
            job.done.set(0, plan.steps.size());
            FakeWorld w = FakeWorld.create()
                .onlySetCellsExist();

            for (int meta = 0; meta <= 6; meta++) {
                w.set(door[0], door[1], door[2], snow, meta);
                assertEquals(-1, Builder.recheck(job, plan, w, c), "a snow layer of meta " + meta + ": " + at);
            }
            assertTrue(job.done.get(i), at);

            w.set(door[0], door[1], door[2], stone, 0);
            assertEquals(step.stage, Builder.recheck(job, plan, w, c), "a block in the doorway: " + at);
            assertFalse(job.done.get(i), "built again: " + at);
            assertEquals(plan.steps.size() - 1, job.done.cardinality(), "only that step: " + at);

            job.done.set(i);
            w.set(door[0], door[1], door[2], snow, 7);
            assertEquals(step.stage, Builder.recheck(job, plan, w, c), "a full snow layer: " + at);
            assertFalse(job.done.get(i), at);

            // skipped once (blocked, or someone stood there) and open now: done as it is
            job.skipped.set(i);
            w.set(door[0], door[1], door[2], snow, 0);
            assertEquals(-1, Builder.recheck(job, plan, w, c), "a skipped doorway with snow: " + at);
            assertTrue(job.done.get(i), at);
            assertFalse(job.skipped.get(i), at);
        }
        // a growth names its first wrong cell in the phase it grows to; a module in the shape it is to stand as
        assertEquals(NexusShape.shapeName(2), Builder.formShape(BuildJob.grow(2)));
        assertNull(Builder.formShape(BuildJob.module(spec(), 4)));
        assertTrue(BuildPlan.formReads(BuildPlan.HARD));
        assertTrue(BuildPlan.formReads(BuildPlan.AIR));
        assertFalse(BuildPlan.formReads(BuildPlan.SOFT), "a soft cell is built, never checked");
        assertFalse(BuildPlan.formReads(BuildPlan.DIG));
        assertFalse(BuildPlan.formReads(BuildPlan.DRAIN));
    }

    /**
     * The outer establish (0.15.0) saves the neighbouring campuses it shares the ground with ({@code Nb}) beside its
     * keep-out boxes, and grades as the campus jobs do; every other job saves no {@code Nb}, as before.
     */
    @Test
    void theOuterEstablishKeepsItsNeighbours() {
        List<int[]> boxes = Collections.singletonList(new int[] { CX + 70, CZ - 5, CX + 80, CZ + 5 });
        List<int[]> near = Arrays.asList(new int[] { CX + 144, CZ }, new int[] { CX - 20, CZ - 150 });
        BuildJob j = BuildJob.outer(boxes, near);
        assertEquals(BuildPlan.OUTER, j.key());
        assertEquals(BuildPlan.OUTER, j.planKey());
        assertEquals(-1, j.site());
        assertTrue(j.laysOuter());
        assertTrue(j.grades());
        assertFalse(j.campusJob(), "not the forum's: a refresh never takes it for its own");
        NBTTagCompound t = j.write();
        assertTrue(t.hasKey("Nb"));
        BuildJob r = BuildJob.read(t);
        assertTrue(r.laysOuter());
        assertEquals(
            2,
            r.neighbours()
                .size());
        assertArrayEquals(
            new int[] { CX - 20, CZ - 150 },
            r.neighbours()
                .get(1));
        assertEquals(
            1,
            r.keepOut()
                .size());
        assertFalse(
            BuildJob.establish(Collections.emptyList())
                .write()
                .hasKey("Nb"));
        assertFalse(
            BuildJob.establish(Collections.emptyList())
                .laysOuter());
        // a neighbour placed or gone before Start makes the plan again; the same ones leave it be
        assertFalse(r.retarget(boxes, near));
        assertTrue(r.retarget(boxes, Collections.singletonList(new int[] { CX + 144, CZ })));
        assertEquals(
            1,
            r.neighbours()
                .size());
    }

    /**
     * The grounds a campus laid beyond the forum ({@code Gl}, 0.15.0) save as a list of plan keys and read back; a key
     * no ground job has is dropped.
     */
    @Test
    void theLaidGroundsSaveAndLoad() {
        Set<String> laid = new java.util.LinkedHashSet<>(Arrays.asList(BuildPlan.OUTER, "establish:nowhere"));
        NBTTagList l = Campus.grounds(laid);
        assertEquals(2, l.tagCount());
        Set<String> back = new java.util.LinkedHashSet<>();
        Campus.readGrounds(l, back);
        assertEquals(Collections.singleton(BuildPlan.OUTER), back);
        Campus.readGrounds(null, back);
        assertEquals(1, back.size());
    }

    /**
     * The outer establish waits for the jobs still to run (0.15.0): its own and every other campus job, and a retire
     * job of a 0.9.2 hall, so the ring lays the streets the hall stood on rather than keeping out of its box for good;
     * a job that ended holds nothing up.
     */
    @Test
    void theOuterEstablishWaitsForARetireJob() {
        assertFalse(Campus.outerWaits(Collections.emptyList()));
        int[] box = { CX + 20, CZ + 20, CX + 30, CZ + 30, Y0, Y0 + 12 };
        BuildJob retire = BuildJob.retire(1, box);
        assertTrue(Campus.outerWaits(Collections.singletonList(retire)));
        retire.state = BuildState.State.DONE;
        assertFalse(Campus.outerWaits(Collections.singletonList(retire)), "ended");
        assertTrue(Campus.outerWaits(Collections.singletonList(BuildJob.forum(Collections.emptyList()))));
        assertTrue(
            Campus.outerWaits(Collections.singletonList(BuildJob.outer(Collections.emptyList(), null))),
            "its own");
        assertFalse(Campus.outerWaits(Collections.singletonList(BuildJob.grow(3))), "a growth is no campus job");
    }

    /**
     * What 0.15.0's fixes add to a job is saved and read back, and the plan made from it is the same: the claims of
     * the outer establish's neighbours ({@code Nc}), the neighbours a regaining job regains from ({@code Nr}), a retire
     * job that lays the outer floor ({@code Ro}, with its neighbours); a job without them saves as before.
     */
    @Test
    void theOuterFixesTravelWithTheJob() {
        List<int[]> near = Arrays.asList(new int[] { CX + 144, CZ, 6 }, new int[] { CX - 150, CZ + 10 });
        BuildJob outer = BuildJob.outer(Collections.emptyList(), near);
        NBTTagCompound t = outer.write();
        assertArrayEquals(new int[] { 6, 0 }, t.getIntArray("Nc"));
        BuildJob back = BuildJob.read(t);
        assertEquals(2, back.neighbours.size());
        assertArrayEquals(new int[] { CX + 144, CZ, 6 }, back.neighbours.get(0));
        assertArrayEquals(new int[] { CX - 150, CZ + 10 }, back.neighbours.get(1));
        assertFalse(back.regains());

        BuildJob regain = BuildJob.outerRegain(Collections.emptyList(), Collections.emptyList(), near);
        BuildJob r2 = BuildJob.read(regain.write());
        assertTrue(r2.regains());
        assertEquals(2, r2.regainFrom.size());
        CampusPlan cp = new CampusPlan(Mix.seed(CX, Y0 + 2, CZ));
        BuildPlan.Plan a = BuildJob.planFor(
            r2.planKey(),
            cp,
            CX,
            Y0,
            CZ,
            0,
            -1,
            CX,
            Y0 + 2,
            CZ - 5,
            r2.keepOut(),
            r2.neighbours(),
            r2.regainFrom(),
            false);
        assertEquals(BuildPlan.outerRegain(cp, CX, CZ, 0, -1, near, Collections.emptyList()), a.clear.size());

        int[] box = { CX + 20, CZ + 20, CX + 30, CZ + 30, Y0, Y0 + 12 };
        BuildJob plain = BuildJob.retire(1, box);
        NBTTagCompound pt = plain.write();
        assertFalse(pt.hasKey("Ro"), "saved as 0.14.2 saved it");
        assertFalse(pt.hasKey("Nb"));
        BuildJob withOuter = BuildJob.read(
            BuildJob.retire(1, box, true, near)
                .write());
        assertTrue(withOuter.retiresOuter());
        assertEquals(2, withOuter.neighbours.size());
        assertFalse(
            BuildJob.read(pt)
                .retiresOuter());
    }

    /**
     * The neighbours the outer ring was laid with (0.15.0, {@code Gn}) go with a broken core, and the outer sites are
     * judged against them as well as against the campuses registered now, so a site the ring left to a neighbour is
     * not taken as whole while its ground lies ungraded; a neighbour in both lists counts once, with the claims the
     * registry has for it, and a gone one keeps the claims it had when the ring was laid.
     */
    @Test
    void theRingsNeighboursAreRemembered() {
        Campus.Carried c = new Campus.Carried();
        c.core = new int[] { CX, Y0 + 2, CZ };
        c.laid.add(BuildPlan.OUTER);
        c.laidWith.add(new int[] { CX + 144, CZ });
        Campus.Carried back = Campus.Carried.read(c.write());
        assertEquals(1, back.laidWith.size());
        assertArrayEquals(new int[] { CX + 144, CZ }, back.laidWith.get(0));
        assertTrue(back.laid.contains(BuildPlan.OUTER));

        List<int[]> live = Collections.singletonList(new int[] { CX + 144, CZ, 3 });
        List<int[]> both = Campus.withLaid(live, Collections.singletonList(new int[] { CX + 144, CZ }));
        assertEquals(1, both.size());
        assertArrayEquals(new int[] { CX + 144, CZ, 3 }, both.get(0), "the registry's claims");
        List<int[]> gone = Campus
            .withLaid(Collections.emptyList(), Collections.singletonList(new int[] { CX - 144, CZ }));
        assertEquals(1, gone.size(), "a neighbour gone from the registry still trims what the ring left it");
        // so the site on its side keeps losing cells until the ground is regained, and is whole without it
        int q = com.fluxecho.logic.CampusSplit.quadrant(-1, 1);
        assertTrue(com.fluxecho.logic.CampusSplit.siteLoss(q, CX, CZ, 0, gone) > 0);
        assertEquals(0, com.fluxecho.logic.CampusSplit.siteLoss(q, CX, CZ, 0, Collections.emptyList()));
        // a gone neighbour keeps the claims the ring was laid with: the site overlapping the outer diagonal it had
        // claimed (150 to the west, its +x +z one) stays held for good until the ground is regained
        int[] claimer = { CX - 150, CZ, 1 << com.fluxecho.logic.CampusSplit.quadrant(1, 1) };
        List<int[]> goneClaimer = Campus.withLaid(Collections.emptyList(), Collections.singletonList(claimer));
        assertArrayEquals(claimer, goneClaimer.get(0), "the claims it was laid with");
        assertEquals(-1, com.fluxecho.logic.CampusSplit.siteLoss(q, CX, CZ, 0, goneClaimer));
        // and a registered one with the same centre but no claim now counts as the registry has it
        List<int[]> live2 = Collections.singletonList(new int[] { CX - 150, CZ });
        assertArrayEquals(
            new int[] { CX - 150, CZ },
            Campus.withLaid(live2, Collections.singletonList(claimer))
                .get(0));
        assertTrue(com.fluxecho.logic.CampusSplit.siteLoss(q, CX, CZ, 0, Campus.withLaid(live2, goneClaimer)) >= 0);
    }

    /**
     * The client hears the neighbours the outer sites are judged against with the campus, not the job (0.15.0,
     * {@code Ns} and the campus's own claims {@code Oc}), and the job's claims, regain list and retire flag: a view
     * read from them has them all, with no job as well, so the masterplan splits the ring after the outer establish
     * ended whether or not the client has the neighbours loaded.
     */
    @Test
    void theSplitReachesTheClientWithTheCampus() {
        List<int[]> near = Arrays.asList(new int[] { CX + 150, CZ, 5 }, new int[] { CX - 144, CZ });
        NBTTagCompound t = new NBTTagCompound();
        t.setBoolean("Ac", true);
        t.setIntArray("Ns", Campus.triples(near));
        t.setByte("Oc", (byte) 9);
        Campus.View v = new Campus.View();
        v.read(t);
        assertFalse(v.hasJob());
        assertEquals(2, v.split.size());
        assertArrayEquals(new int[] { CX + 150, CZ, 5 }, v.split.get(0));
        assertArrayEquals(new int[] { CX - 144, CZ }, v.split.get(1));
        assertEquals(9, v.claims);
        // with a job, its own lists too
        t.setString("K", BuildPlan.OUTER);
        t.setIntArray("Nb", BuildJob.centres(near));
        t.setIntArray("Nc", BuildJob.claims(near));
        t.setIntArray("Nr", BuildJob.centres(Collections.singletonList(new int[] { CX, CZ + 160 })));
        t.setBoolean("Ro", true);
        v.read(t);
        assertArrayEquals(new int[] { CX + 150, CZ, 5 }, v.neighbours.get(0));
        assertEquals(1, v.regainFrom.size());
        assertTrue(v.retireOuter);
        // a campus that never synced them reads none
        Campus.View empty = new Campus.View();
        empty.read(new NBTTagCompound());
        assertTrue(empty.split.isEmpty());
        assertEquals(0, empty.claims);
    }

    /**
     * The campus itself writes the neighbours its outer sites are judged against ({@code Ns}, 0.15.0) with no job as
     * well: a campus whose outer ring is laid syncs the neighbours the ring was laid with, with their claims, after
     * its outer establish ended, so the masterplan splits the ring then too; one whose ground is not laid and whose
     * nexus has not grown to the phase the outer ring opens at writes none until an outer establish is queued.
     */
    @Test
    void aLaidCampusSyncsItsSplitWithoutAJob() {
        List<int[]> with = Arrays.asList(new int[] { CX + 150, CZ, 5 }, new int[] { CX - 144, CZ });
        Campus c = withGround(nexus(0), true, with, null);
        assertNull(c.job());
        NBTTagCompound t = new NBTTagCompound();
        c.writeSync(t);
        Campus.View v = new Campus.View();
        v.read(t);
        assertFalse(v.hasJob());
        assertTrue(v.outerLaid);
        assertEquals(2, v.split.size());
        assertArrayEquals(new int[] { CX - 144, CZ }, v.split.get(0));
        assertArrayEquals(new int[] { CX + 150, CZ, 5 }, v.split.get(1), "with the claims it was laid with");

        TileNexus young = nexus(0);
        assertTrue(young.phase() < SiteFamily.OUTER_DIAG.opens);
        Campus none = withGround(young, false, with, null);
        NBTTagCompound u = new NBTTagCompound();
        none.writeSync(u);
        assertFalse(u.hasKey("Ns"), "not laid, phase below the outer ring's");
        assertFalse(u.hasKey("Oc"));
        Campus queued = withGround(nexus(0), false, with, BuildJob.outer(Collections.emptyList(), with));
        NBTTagCompound q = new NBTTagCompound();
        queued.writeSync(q);
        assertNull(queued.job());
        assertTrue(q.hasKey("Ns"), "an outer establish queued");
    }

    /**
     * An active campus loaded into the nexus with its outer ground laid or not ({@code Gl}), the neighbours the ring
     * was laid with ({@code Gn}, {@code Gq}), no current job, and a queued job or none.
     */
    private static Campus withGround(TileNexus n, boolean laid, List<int[]> laidWith, BuildJob queued) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("St", "active");
        NBTTagList q = new NBTTagList();
        if (queued != null) q.appendTag(queued.write());
        t.setTag("Queue", q);
        if (laid) t.setTag("Gl", Campus.grounds(Collections.singleton(BuildPlan.OUTER)));
        t.setIntArray("Gn", BuildJob.centres(laidWith));
        t.setIntArray("Gq", BuildJob.claims(laidWith));
        NBTTagCompound tile = new NBTTagCompound();
        tile.setTag("Campus", t);
        Campus c = n.campus();
        c.readNBT(tile);
        return c;
    }

    /**
     * The module list says the outer ring's job waits for the jobs before it (0.15.0, {@code why.ground_waits}) only
     * while that job is not queued and a campus job or a retire job holds its offer back.
     */
    @Test
    void theGroundWaitsForTheJobsBeforeIt() {
        assertFalse(Campus.groundWaits(BuildPlan.OUTER, Collections.emptyList()), "nothing holds it back");
        int[] box = { CX + 20, CZ + 20, CX + 30, CZ + 30, Y0, Y0 + 12 };
        BuildJob retire = BuildJob.retire(1, box);
        assertTrue(Campus.groundWaits(BuildPlan.OUTER, Collections.singletonList(retire)));
        assertTrue(Campus.groundWaits(BuildPlan.OUTER, Collections.singletonList(BuildJob.forum(null))));
        assertFalse(
            Campus.groundWaits(BuildPlan.OUTER, Arrays.asList(retire, BuildJob.outer(Collections.emptyList(), null))),
            "queued: there is a job to start");
        assertFalse(Campus.groundWaits("establish:third", Collections.singletonList(retire)), "another ground");
        retire.state = BuildState.State.DONE;
        assertFalse(Campus.groundWaits(BuildPlan.OUTER, Collections.singletonList(retire)), "ended");
    }

    /**
     * The boxes the outer ring was laid round (0.15.0, {@code Gk}): an outer establish remembers the boxes of its
     * keep-out that a module on no recorded site gave, not a recorded site's; once nothing on no site holds one and it
     * lies in loaded chunks it is vacated, and the campus offers the outer establish again limited to it
     * ({@link BuildJob#outerPatch}, {@code Op}), which saves, syncs and plans the same; a patch that ran forgets its
     * boxes; the boxes go with a broken core.
     */
    @Test
    void theBoxesTheRingWasLaidRoundArePatchedOnceVacated() {
        int[] hall = { CX + 20, CZ + 20, CX + 34, CZ + 34 }, stray = { CX - 90, CZ + 5, CX - 76, CZ + 19 },
            site = { CX - 10, CZ - 60, CX + 10, CZ - 40 };
        int[] leg = { CX - 3, CZ - 3, CX + 3, CZ + 3, Y0 + 30, Y0 + 40 };
        List<int[]> keepOut = Arrays.asList(hall, site, stray, leg);
        List<int[]> round = Campus.laidRound(keepOut, Arrays.asList(site.clone(), leg.clone()));
        assertEquals(2, round.size(), "the recorded site's box and the zenith's leg are not remembered");
        assertArrayEquals(hall, round.get(0));
        assertArrayEquals(stray, round.get(1));

        // the hall still stands: held; gone: vacated; in a chunk not loaded: held
        int[] hallNow = hall.clone();
        assertTrue(
            Campus.vacated(round, Arrays.asList(hallNow, stray.clone()), b -> true)
                .isEmpty());
        List<int[]> gone = Campus.vacated(round, Collections.singletonList(stray.clone()), b -> true);
        assertEquals(1, gone.size());
        assertArrayEquals(hall, gone.get(0));
        assertTrue(
            Campus.vacated(round, Collections.singletonList(stray.clone()), b -> false)
                .isEmpty(),
            "unloaded");

        // the patch job saves, reads and plans its boxes
        BuildJob patch = BuildJob.outerPatch(Collections.emptyList(), null, gone);
        assertTrue(patch.patches());
        assertTrue(patch.laysOuter());
        assertFalse(patch.regains());
        NBTTagCompound t = patch.write();
        assertArrayEquals(hall, t.getIntArray("Op"));
        BuildJob back = BuildJob.read(t);
        assertTrue(back.patches());
        assertArrayEquals(
            hall,
            back.patch()
                .get(0));
        assertFalse(
            BuildJob.outer(Collections.emptyList(), null)
                .write()
                .hasKey("Op"),
            "the outer establish saves as before");
        CampusPlan cp = new CampusPlan(Mix.seed(CX, Y0 + 2, CZ));
        BuildPlan.Plan a = BuildJob.planFor(
            back.planKey(),
            cp,
            CX,
            Y0,
            CZ,
            0,
            -1,
            CX,
            Y0 + 2,
            CZ - 5,
            back.keepOut(),
            back.neighbours(),
            back.regainFrom(),
            false,
            back.patch());
        BuildPlan.Plan b = BuildPlan.outer(cp, CX, Y0, CZ, 0, -1, Collections.emptyList(), null, null, gone);
        assertEquals(b.clear.size(), a.clear.size());
        assertEquals(b.steps.size(), a.steps.size());
        // the client reads the boxes with the job
        NBTTagCompound sync = new NBTTagCompound();
        sync.setString("K", BuildPlan.OUTER);
        sync.setIntArray("Op", BuildJob.boxes(gone, 4));
        Campus.View v = new Campus.View();
        v.read(sync);
        assertEquals(1, v.patch.size());
        assertArrayEquals(hall, v.patch.get(0));

        // a patch that ran forgets its boxes, the others stay
        List<int[]> laid = new ArrayList<>(round);
        Campus.forgetBoxes(laid, back.patch());
        assertEquals(1, laid.size());
        assertArrayEquals(stray, laid.get(0));

        // the boxes go with a broken core
        Campus.Carried c = new Campus.Carried();
        c.core = new int[] { CX, Y0 + 2, CZ };
        c.laidBoxes.addAll(round);
        Campus.Carried carried = Campus.Carried.read(c.write());
        assertEquals(2, carried.laidBoxes.size());
        assertArrayEquals(stray, carried.laidBoxes.get(1));
        Campus.Carried none = new Campus.Carried();
        none.core = new int[] { CX, Y0 + 2, CZ };
        assertFalse(
            none.write()
                .hasKey("Gk"),
            "nothing laid round, nothing saved");
    }
}
