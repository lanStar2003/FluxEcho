package com.fluxecho.campus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.ArchiveShape;
import com.fluxecho.logic.BuildLedger;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.BuildState;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.FxCodec;
import com.fluxecho.logic.Mix;
import com.fluxecho.logic.NexusShape;
import com.fluxecho.logic.PartRecipes;
import com.fluxecho.logic.Parts;

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
        j.fillTo = 61;
        j.keepClear = true;
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
        assertEquals(61, r.fillTo);
        assertTrue(r.keepClear);
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
}
