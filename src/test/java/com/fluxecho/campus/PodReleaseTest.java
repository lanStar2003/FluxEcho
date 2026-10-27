package com.fluxecho.campus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fluxecho.city.CityModule;
import com.fluxecho.city.TileCityModule;
import com.fluxecho.city.TileDomeGenerator;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.frame.FrameModule;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.BuildState;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.DomeGeneratorShape;
import com.fluxecho.logic.Mix;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.ResearchTree;
import com.fluxecho.logic.SiteFamily;
import com.fluxecho.logic.TerrainRule;
import com.fluxecho.nexus.NexusGui;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.nexus.TileNexus;

import cpw.mods.fml.common.Loader;

/**
 * The pod floats (0.16.0, CITY.md §3, M13 §4), on the {@code NexusGrowsTest} harness: a real Echo Dome Generator tile
 * checks itself with its own structure definition in a world of its own, its blocks set where the plans put them. A
 * moored pod whose legs the release job's real plan takes down forms as floating after the first cell goes and stays
 * so, its units and samples kept; a repair afterwards is the floating plan and lays no leg; a fresh dome job offered
 * once the pod may float from the start builds it floating directly. And the job round it: the release job's key,
 * form, save and sync, the controller it waits on, the zenith's protected boxes it alone is let into, the shape it
 * waits for, when it is wanted, the plans the form picks, and the cooldown after a member cancels it.
 */
@Tag("slow")
class PodReleaseTest {

    private static final int X = 40, Y = 72, Z = -30, NORTH = 2, ZENITH = SiteFamily.ZENITH_SITE;
    private static BlockFrame oldFrame;
    private static BlockDeck oldDeck;
    private static BlockFitting oldFitting;

    /** A formed nexus of the phase the test gives, whose teams have the research it names. */
    private static final class Nexus extends TileNexus {

        final Set<String> done = new HashSet<>();

        @Override
        public boolean formed() {
            return true;
        }

        @Override
        public boolean has(String id) {
            return done.contains(id);
        }
    }

    /** A pod that finds the test's nexus under it, as one docked over a loaded nexus does. */
    private static final class Pod extends TileDomeGenerator {

        TileNexus over;

        @Override
        public TileNexus under() {
            return over;
        }
    }

    @BeforeAll
    static void setUp() throws ReflectiveOperationException {
        structureLib();
        oldFrame = FrameModule.frame;
        oldDeck = CampusModule.deck;
        oldFitting = CampusModule.fitting;
        if (FrameModule.frame == null) FrameModule.frame = new BlockFrame();
        if (CampusModule.deck == null) CampusModule.deck = new BlockDeck();
        if (CampusModule.fitting == null) CampusModule.fitting = new BlockFitting();
        forgetDefinitions();
        if (ModuleSpecs.get(DomeGeneratorShape.KEY) == null) ModuleSpecs.register(CityModule.domeGenerator());
    }

    @AfterAll
    static void tearDown() throws ReflectiveOperationException {
        FrameModule.frame = oldFrame;
        CampusModule.deck = oldDeck;
        CampusModule.fitting = oldFitting;
        forgetDefinitions();
    }

    /** The city modules' structure definitions are made once from the blocks there are: made again with the test's. */
    private static void forgetDefinitions() throws ReflectiveOperationException {
        for (String name : new String[] { "DEFINITIONS", "BUILD_DEFINITIONS" }) {
            Field f = TileCityModule.class.getDeclaredField(name);
            f.setAccessible(true);
            ((Map<?, ?>) f.get(null)).clear();
        }
    }

    /** Loads StructureLib's mod class once, with a bare FML loader standing in while it does. */
    private static void structureLib() throws ReflectiveOperationException {
        Field instance = Loader.class.getDeclaredField("instance");
        instance.setAccessible(true);
        boolean none = instance.get(null) == null;
        if (none) {
            Class<?> u = Class.forName("sun.misc.Unsafe");
            Field f = u.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            Method alloc = u.getMethod("allocateInstance", Class.class);
            instance.set(null, alloc.invoke(f.get(null), Loader.class));
        }
        try {
            Class.forName("com.gtnewhorizon.structurelib.StructureLib", true, PodReleaseTest.class.getClassLoader());
        } finally {
            if (none) instance.set(null, null);
        }
    }

    /** Runs the server's structure check once, as its timer or a frame change does. */
    private static void recheck(TileMultiblock t) throws ReflectiveOperationException {
        Method m = TileMultiblock.class.getDeclaredMethod("recheckNow");
        m.setAccessible(true);
        m.invoke(t);
    }

    /** Takes the formed cells the check set out of the server's table again. */
    private static void clearCells(TileMultiblock t) throws ReflectiveOperationException {
        Method m = TileMultiblock.class.getDeclaredMethod("clearCells");
        m.setAccessible(true);
        m.invoke(t);
    }

    /** A world of its own with a provider (the formed cells are kept per dimension). */
    private static FakeWorld world() throws ReflectiveOperationException {
        FakeWorld w = FakeWorld.create();
        Field provider = World.class.getDeclaredField("provider");
        provider.setAccessible(true);
        provider.set(w, new WorldProviderSurface());
        return w;
    }

    /** A nexus of phase IV facing north in the world, its teams with the sky ring. */
    private static Nexus nexus(FakeWorld w) {
        Nexus n = w.put(new Nexus(), X, Y, Z);
        n.place((UUID) null, null, NORTH);
        n.regrow(4);
        n.done.add(ResearchTree.SKY_RING);
        return n;
    }

    /** Sets every block a plan places (an AIR step's cell emptied); the controller is the test's tile. */
    private static int lay(FakeWorld w, BuildPlan.Plan p) {
        int set = 0;
        for (BuildPlan.Step s : p.steps) {
            if (s.kind == BuildPlan.AIR) {
                w.set(s.x, s.y, s.z, null, 0);
                continue;
            }
            Block b = PartBlocks.block(s.part);
            if (b == null) continue;
            w.set(s.x, s.y, s.z, b, PartBlocks.meta(s.part));
            set++;
        }
        return set;
    }

    /** The pod's tile on the controller cell {x, y, z, facing}, over the nexus. */
    private static Pod pod(FakeWorld w, TileNexus n, int[] at) {
        Pod d = w.put(new Pod(), at[0], at[1], at[2]);
        d.over = n;
        d.place((UUID) null, null, at[3]);
        return d;
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

    private static long pos(int x, int y, int z) {
        return BuildJob.pos(x, y, z);
    }

    /**
     * A moored pod raised by the real zenith plan over a phase-IV nexus; the real release plan's steps taken one by
     * one as the builder lands them (each leg cell emptied). After the first one the pod stands floating, with its
     * units and the sample in a ward; once the last leg cell is gone it still does, every leg cell empty. A repair of
     * it then is the floating plan (the moored plan would lay the legs again): it lays no leg and the pod forms again
     * as floating.
     */
    @Test
    void aMooredPodReleasedByTheRealPlanFloats() throws ReflectiveOperationException {
        FakeWorld w = world();
        Nexus n = nexus(w);
        int[] c = n.centre();
        int fx = n.front().offsetX, fz = n.front().offsetZ;
        ModuleSpec spec = ModuleSpecs.get(DomeGeneratorShape.KEY);
        BuildJob build = BuildJob.module(spec, ZENITH);
        assertFalse(build.floats(), "a dome job is moored unless it is made to float");
        lay(w, plan(build, n));
        Pod d = pod(w, n, DomeGeneratorShape.controller(ZENITH, c[0], c[1], c[2], fx, fz));
        try {
            recheck(d);
            assertTrue(d.formed(), "it stands on its legs");
            assertEquals(DomeGeneratorShape.MOORED, d.shapeName());
            ItemStack sample = new ItemStack(new Item().setUnlocalizedName("sample"));
            d.samples()
                .setStackInSlot(3, sample);

            BuildJob release = BuildJob.release(DomeGeneratorShape.podDatum(d.yCoord, c[1]));
            assertEquals(40, release.form().pod);
            BuildPlan.Plan legs = plan(release, n);
            assertEquals(DomeGeneratorShape.LEG_CELLS, legs.steps.size());
            int pillar = Parts.frame(Parts.FR_PILLAR);
            for (BuildPlan.Step s : legs.steps) assertEquals(
                pillar,
                PartBlocks.code(w.getBlock(s.x, s.y, s.z), w.getBlockMetadata(s.x, s.y, s.z)),
                "a leg cell of the standing pod");
            assertTrue(d.covers(w, legs.steps.get(0).x, legs.steps.get(0).y, legs.steps.get(0).z));

            // the first cell goes: the pod floats at its next check
            BuildPlan.Step first = legs.steps.get(0);
            w.set(first.x, first.y, first.z, null, 0);
            recheck(d);
            assertTrue(d.formed(), "it floats on");
            assertEquals(DomeGeneratorShape.FLOATING, d.shapeName());
            assertSame(DomeGeneratorShape.FLOATING_BUILD, d.build());
            assertEquals(40, d.datum());
            BuildPlan.Step low = legs.steps.get(legs.steps.size() - 1);
            assertFalse(d.covers(w, low.x, low.y, low.z), "floating, it covers its own box only");
            assertTrue(Builder.standsAs(release, d.shapeName()), "what the release waits for");
            assertFalse(Builder.standsAs(release, DomeGeneratorShape.MOORED));

            // the rest of the legs come down
            for (BuildPlan.Step s : legs.steps) w.set(s.x, s.y, s.z, null, 0);
            recheck(d);
            assertTrue(d.formed());
            assertEquals(DomeGeneratorShape.FLOATING, d.shapeName());
            for (BuildPlan.Step s : legs.steps) assertTrue(w.isAirBlock(s.x, s.y, s.z));
            assertEquals(7, d.unitCount());
            assertEquals(
                7,
                d.samples()
                    .getSlots());
            assertSame(
                sample,
                d.samples()
                    .getStackInSlot(3),
                "the ward keeps its sample");
            int[] ward = d.slotCell(3);
            assertEquals(3, d.slotAt(ward[0], ward[1], ward[2]));

            // a wall cell breaks: the repair is the floating plan, which lays no leg
            BuildPlan.Plan floating = BuildPlan.floating(ZENITH, c[0], c[1], c[2], fx, fz, 40, 0);
            BuildPlan.Step wall = null;
            for (BuildPlan.Step s : floating.steps)
                if (s.stage == DomeGeneratorShape.Z_SHELL && s.kind == BuildPlan.HARD) {
                    wall = s;
                    break;
                }
            w.set(wall.x, wall.y, wall.z, null, 0);
            recheck(d);
            assertFalse(d.formed());
            assertEquals(DomeGeneratorShape.FLOATING, d.shapeName(), "it remembers it floated");
            BuildJob repair = Campus.repairOf(spec, ZENITH, d, c[1]);
            assertTrue(repair.repair());
            assertTrue(repair.floats());
            assertEquals(40, repair.form().pod);
            assertEquals(DomeGeneratorShape.FLOATING, Builder.formShape(repair));
            BuildPlan.Plan fix = plan(repair, n);
            assertEquals(floating.steps, fix.steps);
            Set<Long> legCells = new HashSet<>();
            for (BuildPlan.Step s : legs.steps) legCells.add(pos(s.x, s.y, s.z));
            for (BuildPlan.Step s : fix.steps) assertFalse(legCells.contains(pos(s.x, s.y, s.z)), "a leg: " + s);
            for (BuildPlan.Column col : fix.clear) assertTrue(col.yFrom >= c[1] + 40, "no leg column is cleared");
            // the moored plan would have put the legs back
            assertEquals(
                DomeGeneratorShape.LEG_CELLS,
                plan(BuildJob.repairModule(spec, ZENITH), n).count(DomeGeneratorShape.Z_LEGS));
            lay(w, fix);
            recheck(d);
            assertTrue(d.formed());
            assertEquals(DomeGeneratorShape.FLOATING, d.shapeName());
            for (BuildPlan.Step s : legs.steps) assertTrue(w.isAirBlock(s.x, s.y, s.z), "no leg came back");
            assertSame(
                sample,
                d.samples()
                    .getStackInSlot(3));
        } finally {
            clearCells(d);
        }
    }

    /**
     * A fresh dome job offered once the pod may float from the start (the sky ring researched, the nexus of phase
     * IV) is made floating, as the campus makes it; the builder names the shape {@code floating} to the core it
     * commissions; the campus waits on the controller where the plan puts it; the pod it lays forms floating at once,
     * with no leg anywhere.
     */
    @Test
    void aFreshPhaseFourDomeJobBuildsTheFloatingPodDirectly() throws ReflectiveOperationException {
        FakeWorld w = world();
        Nexus n = nexus(w);
        int[] c = n.centre();
        ModuleSpec spec = ModuleSpecs.get(DomeGeneratorShape.KEY);
        assertTrue(Campus.floatsFresh(DomeGeneratorShape.KEY, n.phase(), n.has(ResearchTree.SKY_RING)));
        BuildJob j = BuildJob.module(spec, ZENITH);
        j.floatAt(DomeGeneratorShape.datum(c[1]), 0);
        assertTrue(j.floats());
        assertEquals(DomeGeneratorShape.FLOATING, j.shapeFor(spec, c[1]));
        BuildPlan.Plan p = plan(j, n);
        assertEquals(0, p.count(DomeGeneratorShape.Z_LEGS));
        int set = lay(w, p);
        assertTrue(set > 1000);
        BuildPlan.Step last = p.steps.get(p.steps.size() - 1);
        int[] at = n.campus()
            .moduleController(j);
        assertTrue(last.at(at[0], at[1], at[2]), "the controller where the campus waits for it");
        Pod d = pod(w, n, at);
        d.expectShape(j.shapeFor(spec, c[1]));
        assertEquals(DomeGeneratorShape.FLOATING, d.shapeName());
        try {
            recheck(d);
            assertTrue(d.formed(), "it floats at once");
            assertEquals(DomeGeneratorShape.FLOATING, d.shapeName());
            assertNull(d.firstWrong());
            int pillars = 0;
            for (BuildPlan.Step s : BuildPlan.release(c[0], c[1], c[2], 0, -1, 40).steps)
                if (!w.isAirBlock(s.x, s.y, s.z)) pillars++;
            assertEquals(0, pillars, "no leg");
            // a moored pod's job would not form there: it has no legs
            assertFalse(Campus.floatsFresh(DomeGeneratorShape.KEY, 3, true), "not before phase IV");
            assertFalse(Campus.floatsFresh(DomeGeneratorShape.KEY, 4, false), "not without the sky ring");
            assertFalse(Campus.floatsFresh("eco_dome", 4, true));
        } finally {
            clearCells(d);
        }
    }

    // ---- the job round it

    /** An active campus of the nexus with the job and the zenith recorded as the dome's (its controller at 40). */
    private static Campus campus(Nexus n, BuildJob job) {
        int[] c = n.centre();
        int[] at = DomeGeneratorShape.controller(ZENITH, c[0], c[1], c[2], n.front().offsetX, n.front().offsetZ);
        NBTTagCompound t = new NBTTagCompound();
        t.setString("St", "active");
        t.setInteger("Pv", BuildJob.GRADE_V);
        if (job != null) t.setTag("Job", job.write());
        t.setTag("Queue", new NBTTagList());
        NBTTagCompound s = new NBTTagCompound();
        s.setInteger("S", ZENITH);
        s.setIntArray("P", new int[] { at[0], at[1], at[2] });
        s.setString("K", DomeGeneratorShape.KEY);
        NBTTagList sites = new NBTTagList();
        sites.appendTag(s);
        t.setTag("Sites", sites);
        NBTTagCompound tile = new NBTTagCompound();
        tile.setTag("Campus", t);
        Campus campus = n.campus();
        campus.readNBT(tile);
        return campus;
    }

    /**
     * The release job: key and plan key {@code release}, the zenith its site, queued as a module job is, named
     * 释放吊舱, its datum saved ({@code Pf}) and read back, a datum that is none refused; its plan the release at that
     * datum; a job without a form saves as 0.15.0 saved it, and a form read from a bad tag is none.
     */
    @Test
    void theReleaseJobAndItsForm() {
        BuildJob r = BuildJob.release(36);
        assertEquals(BuildPlan.RELEASE, r.key());
        assertEquals(BuildPlan.RELEASE, r.planKey());
        assertEquals(ZENITH, r.site());
        assertTrue(r.releases());
        assertFalse(r.floats(), "it builds no pod");
        assertNull(r.module(), "no module job: it needs no core");
        assertEquals(0, r.rank());
        assertFalse(r.repair() || r.grows() || r.retires() || r.campusJob());
        assertEquals(
            "fluxecho.build.job.release",
            ((ChatComponentTranslation) BuildJob.name(r.key(), r.planKey())).getKey());
        NBTTagCompound t = r.write();
        assertEquals(36, t.getInteger("Pf"));
        assertFalse(t.hasKey("Pd"));
        BuildJob back = BuildJob.read(t);
        assertTrue(back.releases());
        assertEquals(36, back.form().pod);
        assertEquals(36, back.podDatum(64), "its own datum, not the base layer's");
        assertThrows(IllegalArgumentException.class, () -> BuildJob.release(38));
        assertEquals(
            BuildPlan.release(100, 205, -200, 0, -1, 36).steps,
            BuildJob.planFor(
                r.planKey(),
                new CampusPlan(1),
                100,
                205,
                -200,
                0,
                -1,
                0,
                0,
                0,
                r.keepOut(),
                r.neighbours(),
                r.regainFrom(),
                false,
                r.patch(),
                r.form()).steps);

        ModuleSpec spec = ModuleSpecs.get(DomeGeneratorShape.KEY);
        NBTTagCompound plain = BuildJob.module(spec, ZENITH)
            .write();
        assertFalse(plain.hasKey("Pf"), "saved as 0.15.0 saved it");
        assertFalse(plain.hasKey("Pd"));
        BuildJob floating = BuildJob.module(spec, ZENITH);
        floating.floatAt(40, 1 | 1 << 6);
        BuildJob fb = BuildJob.read(floating.write());
        assertTrue(fb.floats());
        assertEquals(40, fb.form().pod);
        assertEquals(1 | 1 << 6, fb.form().doors);
        assertThrows(IllegalArgumentException.class, () -> floating.floatAt(30, 0), "no sky datum");
        // a job of another module is no pod's: it floats nothing
        BuildJob root = BuildJob.module(CityModule.root(), SiteFamily.ROOT_SITE);
        root.floatAt(40, 0);
        assertFalse(root.floats());
        assertFalse(
            root.form()
                .floats());
        assertFalse(
            root.write()
                .hasKey("Pf"));

        NBTTagCompound bad = new NBTTagCompound();
        bad.setInteger("Pf", 39);
        bad.setInteger("Pd", 0x1FF);
        BuildJob.Form f = BuildJob.Form.read(bad);
        assertEquals(0, f.pod, "no sky datum");
        assertEquals(0xFF, f.doors, "eight sky slots");
        assertEquals("", new BuildJob.Form().signature());
        assertFalse(new BuildJob.Form().floats());
    }

    /**
     * The form picks the dome's plan: none, the moored zenith plan of 0.15.0, step for step; a floating pod's datum,
     * the floating plan at it, its doors as given; a datum on another module's job picks nothing.
     */
    @Test
    void theFormPicksThePlan() {
        ModuleSpec spec = ModuleSpecs.get(DomeGeneratorShape.KEY);
        String key = spec.jobKey(ZENITH);
        CampusPlan cp = new CampusPlan(Mix.seed(X, Y, Z));
        BuildPlan.Plan moored = BuildJob.planFor(key, cp, 100, 205, -200, 0, -1, 0, 0, 0, null);
        assertEquals(BuildPlan.zenith(ZENITH, 100, 205, -200, 0, -1).steps, moored.steps);
        BuildJob.Form f = new BuildJob.Form();
        assertEquals(
            moored.steps,
            BuildJob.planFor(key, cp, 100, 205, -200, 0, -1, 0, 0, 0, null, null, null, false, null, f).steps);
        f.pod = 40;
        f.doors = 1 << 2;
        assertEquals(
            BuildPlan.floating(ZENITH, 100, 205, -200, 0, -1, 40, 1 << 2).steps,
            BuildJob.planFor(key, cp, 100, 205, -200, 0, -1, 0, 0, 0, null, null, null, false, null, f).steps);
        // with no datum given, the release takes the legs the base layer gives
        assertEquals(
            BuildPlan.release(100, 205, -200, 0, -1, 36).steps,
            BuildJob.planFor(
                BuildPlan.RELEASE,
                cp,
                100,
                205,
                -200,
                0,
                -1,
                0,
                0,
                0,
                null,
                null,
                null,
                false,
                null,
                null).steps);
    }

    /**
     * The campus waits on the pod's own controller: the release job's and a floating job's at the datum they carry
     * (40 over high ground for a pod built by hand on the full legs), a moored one's at the datum the base layer
     * gives; the release job's site is its own for the zenith's protected boxes, and every other job's is not.
     */
    @Test
    void theControllerAndTheBoxesOfTheRelease() throws ReflectiveOperationException {
        FakeWorld w = world();
        Nexus n = nexus(w);
        int[] c = n.centre();
        int fx = n.front().offsetX, fz = n.front().offsetZ;
        Campus campus = campus(n, null);
        int[] at40 = DomeGeneratorShape.moor(40)
            .controller(ZENITH, c[0], c[1], c[2], fx, fz);
        assertArrayEquals(at40, campus.moduleController(BuildJob.release(40)));
        ModuleSpec spec = ModuleSpecs.get(DomeGeneratorShape.KEY);
        assertArrayEquals(
            DomeGeneratorShape.controller(ZENITH, c[0], c[1], c[2], fx, fz),
            campus.moduleController(BuildJob.module(spec, ZENITH)));
        // over high ground (Y0 205: F 36) a pod built by hand on the full legs is released, and repaired, at 40
        TileNexus high = new TileNexus();
        high.xCoord = X;
        high.yCoord = 207;
        high.zCoord = Z;
        high.place((UUID) null, null, NORTH);
        int[] h = high.centre();
        assertEquals(205, h[1]);
        int[] hand = DomeGeneratorShape.moor(40)
            .controller(ZENITH, h[0], h[1], h[2], fx, fz);
        assertArrayEquals(
            hand,
            high.campus()
                .moduleController(BuildJob.release(40)));
        BuildJob fix = BuildJob.repairModule(spec, ZENITH);
        fix.floatAt(40, 0);
        assertArrayEquals(
            hand,
            high.campus()
                .moduleController(fix));
        assertArrayEquals(
            DomeGeneratorShape.moor(36)
                .controller(ZENITH, h[0], h[1], h[2], fx, fz),
            high.campus()
                .moduleController(BuildJob.repairModule(spec, ZENITH)),
            "a moored one at the datum the base layer gives");

        assertTrue(Campus.worksSite(BuildJob.release(40), ZENITH));
        assertFalse(Campus.worksSite(BuildJob.release(40), SiteFamily.ROOT_SITE));
        assertTrue(Campus.worksSite(BuildJob.module(spec, ZENITH), ZENITH));
        assertFalse(Campus.worksSite(BuildJob.grow(4), ZENITH));
        assertFalse(Campus.worksSite(BuildJob.module(CityModule.root(), SiteFamily.ROOT_SITE), ZENITH));

        // a leg cell: guarded from every job but the release, so its AIR step takes our pillar away, credited
        BuildPlan.Step leg = BuildPlan.release(c[0], c[1], c[2], fx, fz, 40).steps.get(5);
        assertTrue(campus.protectedCell(leg.x, leg.y, leg.z), "the zenith's own box, with no job");
        Campus releasing = campus(n, BuildJob.release(40));
        assertFalse(releasing.protectedCell(leg.x, leg.y, leg.z), "not for the release job");
        int[] pod = DomeGeneratorShape.protect(c[0], c[1], c[2], fx, fz)
            .get(40);
        assertFalse(releasing.protectedCell(pod[0], pod[1], pod[2]), "nor the pod's box");
        Campus growing = campus(n, BuildJob.grow(5));
        assertTrue(growing.protectedCell(leg.x, leg.y, leg.z), "a growth keeps out of the legs");

        TerrainRule.Probe ours = new TerrainRule.Probe();
        ours.ours = true;
        for (boolean all : new boolean[] { true, false })
            assertEquals(TerrainRule.Verdict.REPLACE, Builder.verdict(leg, ours, false, all), "taken, credited");
        TerrainRule.Probe guarded = new TerrainRule.Probe();
        guarded.ours = guarded.protectedArea = guarded.oursInOtherStructure = true;
        for (boolean all : new boolean[] { true, false })
            assertEquals(TerrainRule.Verdict.BLOCKED, Builder.verdict(leg, guarded, false, all), "a guarded leg");
        TerrainRule.Probe air = new TerrainRule.Probe();
        air.air = true;
        assertEquals(TerrainRule.Verdict.ALREADY, Builder.verdict(leg, air, false, true), "a leg already gone");
    }

    /**
     * When the release is wanted: the sky ring, a nexus of phase IV, the pod docked on the zenith, and it stands moored
     * or blocks of its legs are left (a leg broken at phase IV floats the pod and leaves the others standing). It waits
     * for the floating pod; it names the floating shape when it does not form.
     */
    @Test
    void whenTheReleaseIsWanted() {
        assertTrue(Campus.releaseWanted(true, 4, true, true, 0));
        assertTrue(Campus.releaseWanted(true, 5, true, false, 12), "legs left under a floating pod");
        assertFalse(Campus.releaseWanted(true, 4, true, false, 0), "nothing left to release");
        assertFalse(Campus.releaseWanted(false, 4, true, true, 0), "no sky ring");
        assertFalse(Campus.releaseWanted(true, 3, true, true, 0), "phase III");
        assertFalse(Campus.releaseWanted(true, 4, false, true, 0), "no pod docked");
        BuildJob r = BuildJob.release(40);
        assertEquals(DomeGeneratorShape.FLOATING, Builder.formShape(r));
        assertNull(Builder.formShape(BuildJob.module(ModuleSpecs.get(DomeGeneratorShape.KEY), ZENITH)));
        assertTrue(Builder.standsAs(BuildJob.grow(4), "phase4"), "every other job any shape it forms as");
    }

    /**
     * The client hears the release job's datum and a floating pod's doors with the job ({@code Pf}, {@code Pd}), and
     * plans the same; a member who cancels the release is not offered it again for a day.
     */
    @Test
    void theReleaseTravelsAndIsDeclined() throws ReflectiveOperationException {
        FakeWorld w = world();
        Nexus n = nexus(w);
        Campus c = campus(n, BuildJob.release(40));
        NBTTagCompound t = new NBTTagCompound();
        c.writeSync(t);
        Campus.View v = new Campus.View();
        v.read(t);
        assertEquals(BuildPlan.RELEASE, v.planKey);
        assertEquals(40, v.form.pod);
        assertEquals(ZENITH, v.site);
        assertEquals("|f40,0", v.form.signature());
        Campus.View none = new Campus.View();
        none.read(new NBTTagCompound());
        assertFalse(none.form.floats());

        assertTrue(
            c.job()
                .releases());
        w.time = 1000;
        c.cancelJob();
        assertEquals(
            BuildState.State.CANCELLED,
            c.job()
                .state());
        Campus.Carried carried = Campus.Carried.read(c.carried());
        assertEquals(1000 + Campus.DECLINE_TICKS, (long) carried.declined.get(BuildPlan.RELEASE));
    }

    /**
     * The keys the code builds at run time are in both lang files: the offer's chat line and the release's two
     * stages, the second shown while the builder waits for the pod to float.
     */
    @Test
    void theReleaseIsNamedInBothLanguages() throws IOException {
        for (String lang : new String[] { "en_US", "zh_CN" }) {
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(
                Paths.get("src", "main", "resources", "assets", "fluxecho", "lang", lang + ".lang"),
                StandardCharsets.UTF_8)) {
                p.load(r);
            }
            for (String k : new String[] { "build.release_offered", "build.job.release", "build.stage.l0",
                "build.stage.l1" }) assertTrue(p.containsKey("fluxecho." + k), lang + ": " + k);
        }
        assertEquals("build.stage.l0", NexusGui.stageKey(BuildPlan.RELEASE, BuildPlan.L_LEGS));
        assertEquals("build.stage.l1", NexusGui.stageKey(BuildPlan.RELEASE, BuildPlan.L_FLOAT));
        assertEquals("build.stage.l1", NexusGui.stageKey(BuildPlan.RELEASE, BuildPlan.L_STAGES), "waiting to float");
    }

    /**
     * 修复 on a release job stopped as incomplete (the pod came apart while its legs came down) ends the release, not
     * declined, and queues the pod's repair in its place as it last stood: floating, at its own datum. The release
     * takes no site from the pod's repair meanwhile.
     */
    @Test
    void aStuckReleaseGivesWayToThePodsRepair() throws ReflectiveOperationException {
        FakeWorld w = world();
        Nexus n = nexus(w);
        int[] c = n.centre();
        BuildJob stuck = BuildJob.release(40);
        stuck.state = BuildState.State.PAUSED;
        stuck.pause = BuildState.Pause.INCOMPLETE;
        stuck.started = true;
        Campus campus = campus(n, stuck);
        Pod d = pod(w, n, DomeGeneratorShape.controller(ZENITH, c[0], c[1], c[2], 0, -1));
        d.expectShape(DomeGeneratorShape.FLOATING);
        assertTrue(campus.canRepair());
        assertEquals("fluxecho.build.repair_queued", campus.repairNow());
        assertEquals(
            BuildState.State.CANCELLED,
            campus.job()
                .state());
        NBTTagCompound tile = new NBTTagCompound();
        campus.writeNBT(tile);
        NBTTagList q = tile.getCompoundTag("Campus")
            .getTagList("Queue", 10);
        assertEquals(1, q.tagCount());
        BuildJob repair = BuildJob.read(q.getCompoundTagAt(0));
        assertTrue(repair.repair());
        assertTrue(repair.floats(), "the pod as it last stood");
        assertEquals(40, repair.form().pod);
        assertFalse(
            Campus.Carried.read(campus.carried()).declined.containsKey(BuildPlan.RELEASE),
            "nobody said no to it");
    }

    /** The jobs queued after the current one, as the campus saves them. */
    private static NBTTagList queue(Campus campus) {
        NBTTagCompound tile = new NBTTagCompound();
        campus.writeNBT(tile);
        return tile.getCompoundTag("Campus")
            .getTagList("Queue", 10);
    }

    /** Marks the site as come apart at the tick {@code since}, as the campus's watch does. */
    @SuppressWarnings("unchecked")
    private static void unformedSince(Campus campus, int site, long since) throws ReflectiveOperationException {
        Field f = Campus.class.getDeclaredField("siteUnformedSince");
        f.setAccessible(true);
        ((Map<Integer, Long>) f.get(campus)).put(site, since);
    }

    /** The first HARD step of the floating plan's shell (a wall block of the pod) at the datum. */
    private static BuildPlan.Step wall(int[] c, int fx, int fz, int datum) {
        for (BuildPlan.Step s : BuildPlan.floating(ZENITH, c[0], c[1], c[2], fx, fz, datum, 0).steps)
            if (s.stage == DomeGeneratorShape.Z_SHELL && s.kind == BuildPlan.HARD) return s;
        throw new AssertionError("no shell");
    }

    /**
     * A release that stalls before the pod ever floated (0.16.0): the moored pod came apart (a wall block broken) and
     * the release took all its legs down under it, so its tile is still named moored. 修复 ends the release and queues
     * the pod's repair floating at the release's datum, its plan with no leg and no step on a leg's cell; laid, the pod
     * forms floating. The legs the members agreed to release never come back.
     */
    @Test
    void aReleaseStuckBeforeThePodFloatedIsRepairedFloating() throws ReflectiveOperationException {
        FakeWorld w = world();
        Nexus n = nexus(w);
        int[] c = n.centre();
        int fx = n.front().offsetX, fz = n.front().offsetZ;
        ModuleSpec spec = ModuleSpecs.get(DomeGeneratorShape.KEY);
        lay(w, plan(BuildJob.module(spec, ZENITH), n));
        Pod d = pod(w, n, DomeGeneratorShape.controller(ZENITH, c[0], c[1], c[2], fx, fz));
        try {
            recheck(d);
            assertTrue(d.formed());
            assertEquals(DomeGeneratorShape.MOORED, d.shapeName());
            BuildPlan.Step broken = wall(c, fx, fz, 40);
            w.set(broken.x, broken.y, broken.z, null, 0);
            recheck(d);
            assertFalse(d.formed());
            assertEquals(DomeGeneratorShape.MOORED, d.shapeName(), "never floated: still named moored");
            BuildPlan.Plan legs = BuildPlan.release(c[0], c[1], c[2], fx, fz, 40);
            Set<Long> legCells = new HashSet<>();
            for (BuildPlan.Step s : legs.steps) {
                w.set(s.x, s.y, s.z, null, 0);
                legCells.add(pos(s.x, s.y, s.z));
            }
            recheck(d);
            assertFalse(d.formed());
            assertEquals(DomeGeneratorShape.MOORED, d.shapeName());

            BuildJob stuck = BuildJob.release(40);
            stuck.state = BuildState.State.PAUSED;
            stuck.pause = BuildState.Pause.INCOMPLETE;
            stuck.started = true;
            stuck.touched = true;
            Campus campus = campus(n, stuck);
            assertEquals("fluxecho.build.repair_queued", campus.repairNow());
            NBTTagList q = queue(campus);
            assertEquals(1, q.tagCount());
            BuildJob repair = BuildJob.read(q.getCompoundTagAt(0));
            assertTrue(repair.repair());
            assertTrue(repair.floats(), "floating, though the tile says moored");
            assertEquals(40, repair.form().pod, "at the release's datum");
            BuildPlan.Plan fix = plan(repair, n);
            assertEquals(0, fix.count(DomeGeneratorShape.Z_LEGS));
            for (BuildPlan.Step s : fix.steps) assertFalse(legCells.contains(pos(s.x, s.y, s.z)), "a leg: " + s);
            lay(w, fix);
            recheck(d);
            assertTrue(d.formed(), "" + d.firstWrong());
            assertEquals(DomeGeneratorShape.FLOATING, d.shapeName());
            for (BuildPlan.Step s : legs.steps) assertTrue(w.isAirBlock(s.x, s.y, s.z), "no leg came back");
        } finally {
            clearCells(d);
        }
    }

    /**
     * 修复 while the release runs, on a pod that came apart, queues the pod's repair (floating: the release has begun);
     * when the release then stops as incomplete, 修复 ends it and queues no second repair of the zenith.
     */
    @Test
    void aStuckReleaseQueuesNoSecondRepair() throws ReflectiveOperationException {
        FakeWorld w = world();
        Nexus n = nexus(w);
        int[] c = n.centre();
        BuildJob release = BuildJob.release(40);
        release.state = BuildState.State.BUILDING;
        release.started = true;
        release.touched = true;
        Campus campus = campus(n, release);
        pod(w, n, DomeGeneratorShape.controller(ZENITH, c[0], c[1], c[2], 0, -1));
        unformedSince(campus, ZENITH, 0);
        w.time = 1000;
        assertEquals("fluxecho.build.repair_queued", campus.repairNow());
        NBTTagList q = queue(campus);
        assertEquals(1, q.tagCount());
        BuildJob first = BuildJob.read(q.getCompoundTagAt(0));
        assertEquals(BuildJob.REPAIR + ZENITH, first.key());
        assertTrue(first.floats(), "the release has begun: the pod floats");
        assertTrue(
            campus.job()
                .releases(),
            "the release runs on");

        BuildJob running = campus.job();
        running.state = BuildState.State.PAUSED;
        running.pause = BuildState.Pause.INCOMPLETE;
        assertEquals("fluxecho.build.repair_queued", campus.repairNow());
        assertEquals(
            BuildState.State.CANCELLED,
            campus.job()
                .state());
        q = queue(campus);
        int repairs = 0;
        for (int i = 0; i < q.tagCount(); i++) if ((BuildJob.REPAIR + ZENITH).equals(
            BuildJob.read(q.getCompoundTagAt(i))
                .key()))
            repairs++;
        assertEquals(1, repairs, "one repair of the zenith");
        assertEquals(1, q.tagCount());
    }

    /** The repair of the zenith the campus holds, as its current job or queued after it, or null. */
    private static BuildJob zenithRepair(Campus campus) {
        BuildJob now = campus.job();
        if (now != null && (BuildJob.REPAIR + ZENITH).equals(now.key())) return now;
        NBTTagList q = queue(campus);
        for (int i = 0; i < q.tagCount(); i++) {
            BuildJob j = BuildJob.read(q.getCompoundTagAt(i));
            if (j != null && (BuildJob.REPAIR + ZENITH).equals(j.key())) return j;
        }
        return null;
    }

    /**
     * The repair a release that began queues for the pod: floating at the release's datum with every door glazed (no
     * sky module stands), no leg in its plan, no step on a leg's cell (the legs still standing among them, which only
     * the release takes down) and no leg's column cleared.
     */
    private static void assertFloatsOverTheLegs(BuildJob repair, Nexus n, Set<Long> legCells, String why) {
        assertTrue(repair != null && repair.repair(), why);
        assertTrue(repair.floats(), why + ": floating, though the tile says moored");
        assertEquals(40, repair.form().pod, why + ": at the release's datum");
        assertEquals(0, repair.form().doors, why + ": no door open");
        BuildPlan.Plan fix = plan(repair, n);
        assertEquals(0, fix.count(DomeGeneratorShape.Z_LEGS), why);
        for (BuildPlan.Step s : fix.steps) assertFalse(legCells.contains(pos(s.x, s.y, s.z)), why + ", a leg: " + s);
        for (BuildPlan.Column col : fix.clear) assertTrue(col.yFrom >= n.centre()[1] + 40, why + ": a leg's column");
    }

    /**
     * A release that began and stopped with legs still standing under a pod that came apart (0.16.0): the moored pod
     * lost a wall block and the release took only the top of its legs down, so its tile is still named moored, no sky
     * module stands (no door is open) and legs are left, so nothing but the release says the pod must float. 修复 while
     * the release still runs (the repair of a site that stood unformed for a while) and 修复 on the release stopped as
     * incomplete both queue the pod's repair floating at the release's datum, which leaves the standing legs to the
     * release and lays no leg again; laid, the pod forms floating over them. The same world with no release, or with
     * one offered and never begun, gets the moored repair, legs and all: only the release that began makes it float.
     */
    @Test
    void aReleaseThatBeganIsRepairedFloatingOverTheLegsLeft() throws ReflectiveOperationException {
        FakeWorld w = world();
        Nexus n = nexus(w);
        int[] c = n.centre();
        int fx = n.front().offsetX, fz = n.front().offsetZ;
        ModuleSpec spec = ModuleSpecs.get(DomeGeneratorShape.KEY);
        lay(w, plan(BuildJob.module(spec, ZENITH), n));
        Pod d = pod(w, n, DomeGeneratorShape.controller(ZENITH, c[0], c[1], c[2], fx, fz));
        try {
            recheck(d);
            assertTrue(d.formed());
            assertEquals(DomeGeneratorShape.MOORED, d.shapeName());
            BuildPlan.Step broken = wall(c, fx, fz, 40);
            w.set(broken.x, broken.y, broken.z, null, 0);
            // the release took the upper half of the legs (its plan runs top-down) before it stopped
            BuildPlan.Plan legs = BuildPlan.release(c[0], c[1], c[2], fx, fz, 40);
            Set<Long> legCells = new HashSet<>();
            int pillar = Parts.frame(Parts.FR_PILLAR), half = legs.steps.size() / 2, standing = 0;
            for (int i = 0; i < legs.steps.size(); i++) {
                BuildPlan.Step s = legs.steps.get(i);
                legCells.add(pos(s.x, s.y, s.z));
                if (i < half) w.set(s.x, s.y, s.z, null, 0);
                else if (PartBlocks.code(w.getBlock(s.x, s.y, s.z), w.getBlockMetadata(s.x, s.y, s.z)) == pillar)
                    standing++;
            }
            assertEquals(legs.steps.size() - half, standing, "the lower legs still stand");
            recheck(d);
            assertFalse(d.formed());
            assertEquals(DomeGeneratorShape.MOORED, d.shapeName(), "never floated: still named moored");
            w.time = 1000;

            // no release: the pod is repaired as it is named, its legs with it
            Campus campus = campus(n, null);
            unformedSince(campus, ZENITH, 0);
            assertEquals("fluxecho.build.repair_queued", campus.repairNow());
            BuildJob moored = zenithRepair(campus);
            assertTrue(moored != null && !moored.floats(), "no release, legs standing, no door open: moored");
            assertEquals(DomeGeneratorShape.LEG_CELLS, plan(moored, n).count(DomeGeneratorShape.Z_LEGS));

            // a release offered and never begun makes no difference
            campus = campus(n, BuildJob.release(40));
            unformedSince(campus, ZENITH, 0);
            assertEquals("fluxecho.build.repair_queued", campus.repairNow());
            moored = zenithRepair(campus);
            assertTrue(moored != null && !moored.floats(), "the release never began: moored");

            // 修复 while the release runs on, having begun
            BuildJob running = BuildJob.release(40);
            running.state = BuildState.State.BUILDING;
            running.started = true;
            campus = campus(n, running);
            unformedSince(campus, ZENITH, 0);
            assertEquals("fluxecho.build.repair_queued", campus.repairNow());
            assertTrue(
                campus.job()
                    .releases(),
                "the release runs on");
            assertFloatsOverTheLegs(zenithRepair(campus), n, legCells, "while the release runs");

            // 修复 on the release stopped as incomplete: it ends and the pod is repaired in its place
            BuildJob stuck = BuildJob.release(40);
            stuck.state = BuildState.State.PAUSED;
            stuck.pause = BuildState.Pause.INCOMPLETE;
            stuck.started = true;
            campus = campus(n, stuck);
            assertEquals("fluxecho.build.repair_queued", campus.repairNow());
            assertEquals(
                BuildState.State.CANCELLED,
                campus.job()
                    .state());
            BuildJob fix = zenithRepair(campus);
            assertFloatsOverTheLegs(fix, n, legCells, "the release stopped");

            lay(w, plan(fix, n));
            recheck(d);
            assertTrue(d.formed(), "" + d.firstWrong());
            assertEquals(DomeGeneratorShape.FLOATING, d.shapeName());
            for (int i = half; i < legs.steps.size(); i++) {
                BuildPlan.Step s = legs.steps.get(i);
                assertEquals(
                    pillar,
                    PartBlocks.code(w.getBlock(s.x, s.y, s.z), w.getBlockMetadata(s.x, s.y, s.z)),
                    "a leg left for the release");
            }
        } finally {
            clearCells(d);
        }
    }

    /**
     * A stuck release with no pod left on the zenith ends (not declined), queues nothing, and says so: the release
     * ended with nothing to repair, a key of its own in both lang files (never "no structure needs repairing", which
     * would hide that the release was stopped).
     */
    @Test
    void aStuckReleaseWithNoPodEndsAndSaysSo() throws ReflectiveOperationException, IOException {
        FakeWorld w = world();
        Nexus n = nexus(w);
        BuildJob stuck = BuildJob.release(40);
        stuck.state = BuildState.State.PAUSED;
        stuck.pause = BuildState.Pause.INCOMPLETE;
        stuck.started = true;
        Campus campus = campus(n, stuck);
        assertEquals("fluxecho.build.release_ended", campus.repairNow());
        assertEquals(
            BuildState.State.CANCELLED,
            campus.job()
                .state());
        assertEquals(0, queue(campus).tagCount());
        assertFalse(Campus.Carried.read(campus.carried()).declined.containsKey(BuildPlan.RELEASE));
        for (String lang : new String[] { "en_US", "zh_CN" }) {
            Properties p = langFile(lang);
            assertTrue(p.containsKey("fluxecho.build.release_ended"), lang);
            assertFalse(
                p.getProperty("fluxecho.build.release_ended")
                    .contains("%"),
                "no placeholders");
        }
    }

    /**
     * A core put back by hand over a released pod (0.16.0) names itself moored; its repair floats all the same once
     * the pod may float, at its controller's datum, leaving the door to the weather station standing on S0 open: no
     * leg, no glazing in that door.
     */
    @Test
    void aCorePutBackOverAReleasedPodIsRepairedFloatingWithItsDoorsOpen() throws ReflectiveOperationException {
        FakeWorld w = world();
        Nexus n = nexus(w);
        int[] c = n.centre();
        int fx = n.front().offsetX, fz = n.front().offsetZ;
        ModuleSpec spec = ModuleSpecs.get(DomeGeneratorShape.KEY);
        lay(w, plan(BuildJob.module(spec, ZENITH), n));
        int[] at = DomeGeneratorShape.controller(ZENITH, c[0], c[1], c[2], fx, fz);
        Pod d = pod(w, n, at);
        Pod again = null;
        try {
            recheck(d);
            assertEquals(DomeGeneratorShape.MOORED, d.shapeName());
            BuildPlan.Plan legs = BuildPlan.release(c[0], c[1], c[2], fx, fz, 40);
            for (BuildPlan.Step s : legs.steps) w.set(s.x, s.y, s.z, null, 0);
            recheck(d);
            assertTrue(d.formed());
            assertEquals(DomeGeneratorShape.FLOATING, d.shapeName(), "released");
            clearCells(d);
            BuildPlan.Step broken = wall(c, fx, fz, 40);
            w.set(broken.x, broken.y, broken.z, null, 0);
            // the core put back by hand: a fresh tile, named moored
            again = pod(w, n, at);
            assertEquals(DomeGeneratorShape.MOORED, again.shapeName());

            NBTTagCompound t = new NBTTagCompound();
            t.setString("St", "active");
            t.setInteger("Pv", BuildJob.GRADE_V);
            t.setTag("Queue", new NBTTagList());
            NBTTagList sites = new NBTTagList();
            for (int[] e : new int[][] { { ZENITH, at[0], at[1], at[2] }, { 30, at[0], at[1], at[2] - 24 } }) {
                NBTTagCompound site = new NBTTagCompound();
                site.setInteger("S", e[0]);
                site.setIntArray("P", new int[] { e[1], e[2], e[3] });
                site.setString("K", e[0] == ZENITH ? DomeGeneratorShape.KEY : "weather_station");
                sites.appendTag(site);
            }
            t.setTag("Sites", sites);
            NBTTagCompound tile = new NBTTagCompound();
            tile.setTag("Campus", t);
            Campus campus = n.campus();
            campus.readNBT(tile);
            unformedSince(campus, ZENITH, 0);
            w.time = 1000;
            assertEquals("fluxecho.build.repair_queued", campus.repairNow());
            BuildJob repair = campus.job();
            assertTrue(repair.repair());
            assertTrue(repair.floats(), "it can only float");
            assertEquals(40, repair.form().pod);
            assertTrue((repair.form().doors & com.fluxecho.logic.SkyRing.bit(30)) != 0, "the door to S0 left open");
            BuildPlan.Plan fix = plan(repair, n);
            assertEquals(0, fix.count(DomeGeneratorShape.Z_LEGS));
            Set<Long> legCells = new HashSet<>(), door = new HashSet<>();
            for (BuildPlan.Step s : legs.steps) legCells.add(pos(s.x, s.y, s.z));
            for (int[] e : com.fluxecho.logic.SkyRing.doorCells(30, 40)) {
                int[] dw = CampusPlan.toWorld(e[0], e[1], fx, fz);
                door.add(pos(c[0] + dw[0], c[1] + e[2], c[2] + dw[1]));
            }
            for (BuildPlan.Step s : fix.steps) {
                assertFalse(legCells.contains(pos(s.x, s.y, s.z)), "a leg: " + s);
                assertFalse(door.contains(pos(s.x, s.y, s.z)), "glazing the door: " + s);
            }
        } finally {
            clearCells(d);
            if (again != null) clearCells(again);
        }
    }

    /**
     * A released pod stays up while its nexus is apart (0.16.0): with no formed nexus over it (a broken block of the
     * nexus, its own repair) it is still tried floating, so it stays formed and floating, where before it came apart.
     */
    @Test
    void aReleasedPodStaysUpWhileItsNexusIsApart() throws ReflectiveOperationException {
        FakeWorld w = world();
        Nexus n = nexus(w);
        int[] c = n.centre();
        int fx = n.front().offsetX, fz = n.front().offsetZ;
        ModuleSpec spec = ModuleSpecs.get(DomeGeneratorShape.KEY);
        lay(w, plan(BuildJob.module(spec, ZENITH), n));
        Pod d = pod(w, n, DomeGeneratorShape.controller(ZENITH, c[0], c[1], c[2], fx, fz));
        try {
            recheck(d);
            for (BuildPlan.Step s : BuildPlan.release(c[0], c[1], c[2], fx, fz, 40).steps)
                w.set(s.x, s.y, s.z, null, 0);
            recheck(d);
            assertEquals(DomeGeneratorShape.FLOATING, d.shapeName());
            // the nexus comes apart: the pod finds no formed nexus under it
            d.over = null;
            recheck(d);
            assertTrue(d.formed(), "it stays up");
            assertEquals(DomeGeneratorShape.FLOATING, d.shapeName());
        } finally {
            clearCells(d);
        }
    }

    /**
     * A dome job that builds the pod floating names its clearing stage for the pod's place in the air (its plan has no
     * legs and clears no leg's column); a moored one's stays the legs' and the pod's; the key is in both lang files.
     */
    @Test
    void theFloatingPodsClearingIsNamedForThePod() throws IOException {
        ModuleSpec spec = ModuleSpecs.get(DomeGeneratorShape.KEY);
        String key = spec.jobKey(ZENITH);
        assertEquals("build.stage.zf0", NexusGui.stageKey(key, DomeGeneratorShape.Z_CLEAR, false, true));
        assertEquals("build.stage.z0", NexusGui.stageKey(key, DomeGeneratorShape.Z_CLEAR, false, false));
        assertEquals("build.stage.z0", NexusGui.stageKey(key, DomeGeneratorShape.Z_CLEAR), "a moored job as before");
        assertEquals("build.stage.z3", NexusGui.stageKey(key, DomeGeneratorShape.Z_SHELL, false, true));
        assertEquals("build.stage.l0", NexusGui.stageKey(BuildPlan.RELEASE, BuildPlan.L_LEGS, false, true));
        BuildJob floating = BuildJob.module(spec, ZENITH);
        floating.floatAt(40, 0);
        assertTrue(floating.floats());
        assertFalse(
            BuildJob.module(spec, ZENITH)
                .floats());
        NexusGui.Build b = new NexusGui.Build();
        NBTTagCompound t = new NBTTagCompound();
        t.setString("K", key);
        t.setBoolean("Fl", true);
        b.read(t);
        assertTrue(b.floats);
        for (String lang : new String[] { "en_US", "zh_CN" }) {
            Properties p = langFile(lang);
            assertTrue(p.containsKey("fluxecho.build.stage.zf0"), lang);
            assertFalse(
                p.getProperty("fluxecho.build.stage.zf0")
                    .contains("%"));
        }
    }

    private static Properties langFile(String lang) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(
            Paths.get("src", "main", "resources", "assets", "fluxecho", "lang", lang + ".lang"),
            StandardCharsets.UTF_8)) {
            p.load(r);
        }
        return p;
    }
}
