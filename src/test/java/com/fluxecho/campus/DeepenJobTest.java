package com.fluxecho.campus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fluxecho.city.CityModule;
import com.fluxecho.city.TileRoot;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.BuildState;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.Mix;
import com.fluxecho.logic.RootShape;
import com.fluxecho.logic.SiteFamily;
import com.fluxecho.nexus.NexusGui;
import com.fluxecho.nexus.TileNexus;

/**
 * The deepen job (0.16.0, CITY.md §4.9, M13 §7): the root's module job's keys ({@code module:root@9}, so it works the
 * root's own site and controller) with the deep root's form as a saved and synced field ({@code Rs}); that field picks
 * its plan (the new part over the formed well), a repair's (the whole form) or none (the well, as 0.15.0); the shape
 * the builder names and waits for; the controller of the well the form grows from, a hand-dug full well's over low
 * ground too; its name and stages, in both lang files; and the day's cooldown after a member cancels it.
 */
class DeepenJobTest {

    private static final int X = 60, Z = 90, EAST = 5;

    @BeforeAll
    static void setUp() {
        if (ModuleSpecs.get(RootShape.KEY) == null) ModuleSpecs.register(CityModule.root());
    }

    private static ModuleSpec spec() {
        return ModuleSpecs.get(RootShape.KEY);
    }

    /** A formed nexus over the base layer {@code y0}, facing east, in a world of its own with a provider. */
    private static TileNexus nexus(int y0) {
        FakeWorld w = FakeWorld.create();
        try {
            Field provider = World.class.getDeclaredField("provider");
            provider.setAccessible(true);
            provider.set(w, new WorldProviderSurface());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        TileNexus n = w.put(new TileNexus() {

            @Override
            public boolean formed() {
                return true;
            }
        }, X, y0 + 2, Z);
        n.place((UUID) null, null, EAST);
        assertEquals(y0, n.centre()[1]);
        return n;
    }

    /** The nexus's campus, active, with the job and the root recorded on its site at the controller cell given. */
    private static Campus campus(TileNexus n, BuildJob job, int[] root) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("St", "active");
        t.setInteger("Pv", BuildJob.GRADE_V);
        if (job != null) t.setTag("Job", job.write());
        t.setTag("Queue", new NBTTagList());
        NBTTagCompound s = new NBTTagCompound();
        s.setInteger("S", SiteFamily.ROOT_SITE);
        s.setIntArray("P", new int[] { root[0], root[1], root[2] });
        s.setString("K", RootShape.KEY);
        NBTTagList sites = new NBTTagList();
        sites.appendTag(s);
        t.setTag("Sites", sites);
        NBTTagCompound tile = new NBTTagCompound();
        tile.setTag("Campus", t);
        Campus campus = n.campus();
        campus.readNBT(tile);
        return campus;
    }

    private static BuildPlan.Plan plan(BuildJob j, TileNexus n) {
        int[] c = n.centre();
        return BuildJob.planFor(
            j.planKey(),
            new CampusPlan(Mix.seed(n.xCoord, n.yCoord, n.zCoord)),
            c[0],
            c[1],
            c[2],
            n.front().offsetX,
            n.front().offsetZ,
            n.xCoord,
            n.yCoord,
            n.zCoord,
            j.keepOut(),
            j.neighbours(),
            j.regainFrom(),
            j.retiresOuter(),
            j.patch(),
            j.form());
    }

    /**
     * The job: the root's module job's keys and site, queued as a module job is, named 潮汐井生长为深根, the form it
     * grows the well into carried, the builder naming that form to the core and waiting for it; a form that is none
     * refused.
     */
    @Test
    void theDeepenJobAndItsForm() {
        BuildJob d = BuildJob.deepen(spec(), RootShape.DEEP);
        String key = BuildPlan.moduleKey(RootShape.KEY, SiteFamily.ROOT_SITE);
        assertEquals(key, d.key());
        assertEquals(key, d.planKey());
        assertEquals(SiteFamily.ROOT_SITE, d.site());
        assertEquals(RootShape.KEY, d.module());
        assertTrue(d.deepens());
        assertFalse(d.repair());
        assertFalse(d.form().whole, "its new part only");
        assertEquals(RootShape.DEEP, d.form().root);
        assertEquals(0, d.rank(), "queued as a module job");
        assertEquals("fluxecho.build.job.deepen", ((ChatComponentTranslation) d.name()).getKey());
        assertEquals(RootShape.DEEP, d.shapeFor(spec(), 70), "the form, named to the core");
        assertEquals(RootShape.DEEP, Builder.formShape(d));
        assertFalse(Builder.standsAs(d, RootShape.WELL), "not done while it is the well");
        assertTrue(Builder.standsAs(d, RootShape.DEEP));
        assertTrue(Campus.worksSite(d, SiteFamily.ROOT_SITE), "the root's own boxes do not hold it");
        assertThrows(IllegalArgumentException.class, () -> BuildJob.deepen(spec(), RootShape.WELL));
        // the root's own module job and its repair stay as 0.15.0 made them
        BuildJob well = BuildJob.module(spec(), SiteFamily.ROOT_SITE);
        assertFalse(well.deepens());
        assertNull(well.form().root);
        assertEquals(RootShape.WELL, well.shapeFor(spec(), 70));
        assertNull(Builder.formShape(well));
        assertTrue(Builder.standsAs(well, RootShape.WELL_20));
        BuildJob fix = BuildJob.repairModule(spec(), SiteFamily.ROOT_SITE);
        assertTrue(fix.form().whole, "a repair's key says it is one");
        assertFalse(fix.deepens());
        fix.rootAs(RootShape.ROOT_7);
        assertEquals(RootShape.ROOT_7, fix.form().root);
        assertFalse(fix.deepens(), "a repair puts the form back whole");
        assertEquals(RootShape.ROOT_7, fix.shapeFor(spec(), 41));
        assertTrue(Builder.standsAs(fix, RootShape.ROOT_7));
        fix.rootAs("nothing");
        assertEquals(RootShape.ROOT_7, fix.form().root, "a name of no form is ignored");
        assertEquals(
            "fluxecho.build.job.module",
            ((ChatComponentTranslation) BuildJob.name(key, key)).getKey(),
            "without the form the module job's name");
    }

    /**
     * The form picks the plan: the deepen job the new part over the formed well ({@link BuildPlan#deepen}), a repair
     * that carries it the whole form ({@link BuildPlan#deep}), a root job without it the well, exactly as 0.15.0.
     */
    @Test
    void theFormPicksThePlan() {
        TileNexus n = nexus(70);
        int[] c = n.centre();
        int fx = n.front().offsetX, fz = n.front().offsetZ;
        RootShape.Deep root = RootShape.deep(RootShape.DEEP);
        BuildPlan.Plan grow = plan(BuildJob.deepen(spec(), RootShape.DEEP), n);
        assertEquals(11027, grow.steps.size());
        assertEquals(BuildPlan.deepen(SiteFamily.ROOT_SITE, c[0], c[1], c[2], fx, fz, root).steps, grow.steps);
        BuildJob fix = BuildJob.repairModule(spec(), SiteFamily.ROOT_SITE);
        fix.rootAs(RootShape.DEEP);
        BuildPlan.Plan whole = plan(fix, n);
        assertEquals(30300, whole.steps.size());
        assertEquals(BuildPlan.deep(SiteFamily.ROOT_SITE, c[0], c[1], c[2], fx, fz, root).steps, whole.steps);
        BuildPlan.Plan well = plan(BuildJob.module(spec(), SiteFamily.ROOT_SITE), n);
        assertEquals(BuildPlan.root(SiteFamily.ROOT_SITE, c[0], c[1], c[2], fx, fz).steps, well.steps);
        assertEquals(well.steps, plan(BuildJob.repairModule(spec(), SiteFamily.ROOT_SITE), n).steps);
        // in-well over a hand-dug full well at Y0 28: the form's own well, not the one the base layer gives
        TileNexus low = nexus(28);
        BuildPlan.Plan in = plan(BuildJob.deepen(spec(), RootShape.ROOT_IN), low);
        assertEquals(113, in.steps.size());
        int[] lc = low.centre();
        BuildPlan.Step core = in.steps.get(in.steps.size() - 1);
        assertEquals(lc[1] - 23, core.y, "the full well's controller");
    }

    /**
     * The form travels: saved as {@code Rs} and read back, sent to the client in the campus's sync and read there, its
     * repair flag from the job's key on both sides; a job without it saves and syncs as 0.15.0, a form of no deep root
     * reads as none. The campus's controller of the job is the form's well's, and a member's 取消 declines the root
     * for a day, as every module job.
     */
    @Test
    void theFormTravelsAndTheJobIsDeclined() {
        BuildJob d = BuildJob.deepen(spec(), RootShape.ROOT_IN);
        NBTTagCompound t = d.write();
        assertEquals(RootShape.ROOT_IN, t.getString("Rs"));
        BuildJob back = BuildJob.read(t);
        assertTrue(back.deepens());
        assertEquals(RootShape.ROOT_IN, back.form().root);
        assertFalse(back.form().whole);
        assertFalse(
            BuildJob.module(spec(), SiteFamily.ROOT_SITE)
                .write()
                .hasKey("Rs"),
            "a root job without a form saves as before");
        NBTTagCompound bad = new NBTTagCompound();
        bad.setString("Rs", "deep_root");
        assertNull(BuildJob.Form.read(bad).root);
        BuildJob fix = BuildJob.repairModule(spec(), SiteFamily.ROOT_SITE);
        fix.rootAs(RootShape.ROOT_IN);
        BuildJob fixBack = BuildJob.read(fix.write());
        assertTrue(fixBack.form().whole, "a repair read back is one");
        assertEquals(RootShape.ROOT_IN, fixBack.form().root);

        // the campus: a hand-dug full well over low ground (Y0 28), its controller at the full well's cell
        TileNexus n = nexus(28);
        int[] c = n.centre();
        int fx = n.front().offsetX, fz = n.front().offsetZ;
        int[] full = RootShape.well(RootShape.DEPTH)
            .controller(SiteFamily.ROOT_SITE, c[0], c[1], c[2], fx, fz);
        Campus campus = campus(n, d, full);
        assertArrayEquals(full, campus.moduleController(d), "the form's well's controller");
        assertArrayEquals(
            RootShape.controller(SiteFamily.ROOT_SITE, c[0], c[1], c[2], fx, fz),
            campus.moduleController(BuildJob.module(spec(), SiteFamily.ROOT_SITE)),
            "a well job's where the base layer sinks one");
        NBTTagCompound sync = new NBTTagCompound();
        campus.writeSync(sync);
        Campus.View v = new Campus.View();
        v.read(sync);
        assertEquals(RootShape.ROOT_IN, v.form.root);
        assertFalse(v.form.whole);
        assertTrue(BuildJob.deepens(v.job, v.planKey, v.form));
        assertEquals("|r" + RootShape.ROOT_IN, v.form.signature());
        Campus.View repairView = new Campus.View();
        NBTTagCompound rt = new NBTTagCompound();
        rt.setString("K", BuildJob.REPAIR + SiteFamily.ROOT_SITE);
        rt.setString("Pk", BuildPlan.moduleKey(RootShape.KEY, SiteFamily.ROOT_SITE));
        rt.setString("Rs", RootShape.ROOT_IN);
        repairView.read(rt);
        assertTrue(repairView.form.whole, "the client plans a repair whole too");
        assertEquals("|r" + RootShape.ROOT_IN + "w", repairView.form.signature());
        assertFalse(BuildJob.deepens(repairView.job, repairView.planKey, repairView.form));

        ((FakeWorld) n.getWorldObj()).time = 500;
        campus.cancelJob();
        assertEquals(
            BuildState.State.CANCELLED,
            campus.job()
                .state());
        Campus.Carried carried = Campus.Carried.read(campus.carried());
        assertEquals(500 + Campus.DECLINE_TICKS, (long) carried.declined.get(RootShape.KEY));
    }

    /**
     * 修复 on the deepen job stopped as incomplete (a cell of the well broke while it ran, so the deep root's form never
     * stood) ends the job, not declined, and queues the root's repair in its place as that form, whole: its plan puts
     * the well's own cells back too (the deepen job's holds none of them), and it waits for the form.
     */
    @Test
    void aStuckDeepenJobGivesWayToTheWholeRootsRepair() {
        for (String form : new String[] { RootShape.DEEP, RootShape.ROOT_IN_20 }) {
            int y0 = RootShape.DEEP.equals(form) ? 64 : 28;
            TileNexus n = nexus(y0);
            int[] c = n.centre();
            int fx = n.front().offsetX, fz = n.front().offsetZ;
            BuildJob stuck = BuildJob.deepen(spec(), form);
            stuck.state = BuildState.State.PAUSED;
            stuck.pause = BuildState.Pause.INCOMPLETE;
            stuck.started = true;
            stuck.touched = true;
            RootShape.Deep d = RootShape.deep(form);
            Campus campus = campus(n, stuck, d.controller(SiteFamily.ROOT_SITE, c[0], c[1], c[2], fx, fz));
            assertTrue(campus.canRepair());
            assertEquals("fluxecho.build.repair_queued", campus.repairNow(), form);
            assertEquals(
                BuildState.State.CANCELLED,
                campus.job()
                    .state(),
                "the deepen job ended");
            assertFalse(
                Campus.Carried.read(campus.carried()).declined.containsKey(RootShape.KEY),
                "nobody said no to it");
            NBTTagCompound tile = new NBTTagCompound();
            campus.writeNBT(tile);
            NBTTagList q = tile.getCompoundTag("Campus")
                .getTagList("Queue", 10);
            assertEquals(1, q.tagCount());
            BuildJob repair = BuildJob.read(q.getCompoundTagAt(0));
            assertTrue(repair.repair());
            assertEquals(BuildJob.REPAIR + SiteFamily.ROOT_SITE, repair.key());
            assertEquals(form, repair.form().root, "as the form it was growing into");
            assertTrue(repair.form().whole);
            assertFalse(repair.deepens());
            assertEquals(form, Builder.formShape(repair));
            // its plan holds every cell of the well it grows from, which the deepen job's holds next to none of
            BuildPlan.Plan whole = plan(repair, n), part = plan(stuck, n);
            assertEquals(BuildPlan.deep(SiteFamily.ROOT_SITE, c[0], c[1], c[2], fx, fz, d).steps, whole.steps);
            java.util.Set<Long> inWhole = new java.util.HashSet<>(), inPart = new java.util.HashSet<>();
            for (BuildPlan.Step st : whole.steps) inWhole.add(BuildJob.pos(st.x, st.y, st.z));
            for (BuildPlan.Step st : part.steps) inPart.add(BuildJob.pos(st.x, st.y, st.z));
            int wellCells = 0, partHolds = 0;
            for (BuildPlan.Step st : BuildPlan.root(SiteFamily.ROOT_SITE, c[0], c[1], c[2], fx, fz).steps) {
                if (BuildPlan.digs(st.kind)) continue;
                wellCells++;
                long at = BuildJob.pos(st.x, st.y, st.z);
                assertTrue(inWhole.contains(at), form + ": the repair puts the well's cell back at " + st);
                if (inPart.contains(at)) partHolds++;
            }
            assertEquals(d.well.depth, RootShape.forBase(y0).depth, "the well the base layer sank");
            assertTrue(wellCells > 2000, form + ": " + wellCells);
            assertTrue(partHolds <= 26, form + ": the deepen job holds " + partHolds + " of the well's cells");
        }
    }

    /** The build page's copy names the deepen job and its stages as itself, and so does a queued one. */
    @Test
    void theBuildPageNamesItAsItself() {
        NBTTagCompound t = new NBTTagCompound();
        String key = BuildPlan.moduleKey(RootShape.KEY, SiteFamily.ROOT_SITE);
        t.setString("K", key);
        t.setString("Pk", key);
        t.setBoolean("Dr", true);
        NBTTagList q = new NBTTagList();
        NBTTagCompound e = new NBTTagCompound();
        e.setString("K", key);
        e.setString("Pk", key);
        e.setBoolean("Dr", true);
        q.appendTag(e);
        t.setTag("Q", q);
        NexusGui.Build b = new NexusGui.Build();
        b.read(t);
        assertTrue(b.deepens);
        assertEquals(3, b.queue.get(0).length, "a queued deepen job is named as itself");
        assertEquals("build.stage.dr0", NexusGui.stageKey(key, RootShape.R_SINK, true));
        assertEquals("build.stage.dr3", NexusGui.stageKey(key, RootShape.R_FIT, true));
        assertEquals("build.stage.dr4", NexusGui.stageKey(key, RootShape.R_STAGES, true), "waiting for the form");
        assertEquals("build.stage.r3", NexusGui.stageKey(key, RootShape.R_FIT), "the well's job as before");
    }

    /** The keys the code builds at run time for the deep root are in both lang files, matching. */
    @Test
    void theDeepRootIsNamedInBothLanguages() throws IOException {
        String[] keys = { "build.deepen_offered", "build.job.deepen", "build.stage.dr0", "build.stage.dr1",
            "build.stage.dr2", "build.stage.dr3", "build.stage.dr4", "module.root.deep", "unit.bay" };
        Properties zh = null;
        for (String lang : new String[] { "en_US", "zh_CN" }) {
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(
                Paths.get("src", "main", "resources", "assets", "fluxecho", "lang", lang + ".lang"),
                StandardCharsets.UTF_8)) {
                p.load(r);
            }
            for (String k : keys) {
                assertTrue(p.containsKey("fluxecho." + k), lang + ": " + k);
                assertFalse(
                    p.getProperty("fluxecho." + k)
                        .contains("%"),
                    "no placeholders: " + k);
            }
            if (lang.equals("zh_CN")) zh = p;
        }
        assertEquals("深根", zh.getProperty("fluxecho.module.root.deep"));
        assertEquals("根龛", zh.getProperty("fluxecho.unit.bay"));
        assertEquals("module.root.deep", new TileRoot() {

            {
                expectShape(RootShape.DEEP);
            }
        }.nameKey());
    }
}
