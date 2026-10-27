package com.fluxecho.campus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fluxecho.city.CityModule;
import com.fluxecho.city.TileCityModule;
import com.fluxecho.city.TileDomeGenerator;
import com.fluxecho.city.TileWeatherStation;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.frame.FrameModule;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.DomeGeneratorShape;
import com.fluxecho.logic.Mix;
import com.fluxecho.logic.SiteFamily;
import com.fluxecho.logic.SkyRing;
import com.fluxecho.logic.WeatherStationShape;
import com.fluxecho.nexus.NexusGui;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.nexus.TileNexus;

import cpw.mods.fml.common.Loader;

/**
 * The sky ring's jobs on a campus (0.16.0, M13 §5): a sky module's job carries the floor of the pod that stood when it
 * was offered ({@code Sd}), saved and synced and read back, and plans and waits at that deck; while it is the current
 * job the pod's protected box lets it into exactly its own door and its catwalk's first row; a repair of the floating
 * pod leaves open every door that leads to a sky module standing or to a job for one, and its plan clears over those
 * doors' catwalks; a sky module's repair is laid at the deck it stands at. On the {@code PodReleaseTest} harness, the
 * pod a floating plan with an open door lays and the module its job lays both form, and the module can be walked to.
 */
@Tag("slow")
class SkyJobsTest {

    private static final int X = 40, Y = 72, Z = -30, NORTH = 2, ZENITH = SiteFamily.ZENITH_SITE;
    private static BlockFrame oldFrame;
    private static BlockDeck oldDeck;
    private static BlockFitting oldFitting;

    /** A formed nexus of phase IV whose teams have every research. */
    private static final class Nexus extends TileNexus {

        @Override
        public boolean formed() {
            return true;
        }

        @Override
        public boolean has(String id) {
            return true;
        }
    }

    /** A pod that finds the test's nexus under it. */
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
        if (ModuleSpecs.get(WeatherStationShape.KEY) == null) ModuleSpecs.register(CityModule.weatherStation());
        if (ModuleSpecs.get("observatory") == null) ModuleSpecs.register(CityModule.observatory());
    }

    @AfterAll
    static void tearDown() throws ReflectiveOperationException {
        FrameModule.frame = oldFrame;
        CampusModule.deck = oldDeck;
        CampusModule.fitting = oldFitting;
        forgetDefinitions();
    }

    private static void forgetDefinitions() throws ReflectiveOperationException {
        for (String name : new String[] { "DEFINITIONS", "BUILD_DEFINITIONS" }) {
            Field f = TileCityModule.class.getDeclaredField(name);
            f.setAccessible(true);
            ((Map<?, ?>) f.get(null)).clear();
        }
    }

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
            Class.forName("com.gtnewhorizon.structurelib.StructureLib", true, SkyJobsTest.class.getClassLoader());
        } finally {
            if (none) instance.set(null, null);
        }
    }

    private static void recheck(TileMultiblock t) throws ReflectiveOperationException {
        Method m = TileMultiblock.class.getDeclaredMethod("recheckNow");
        m.setAccessible(true);
        m.invoke(t);
    }

    private static void clearCells(TileMultiblock t) throws ReflectiveOperationException {
        Method m = TileMultiblock.class.getDeclaredMethod("clearCells");
        m.setAccessible(true);
        m.invoke(t);
    }

    private static FakeWorld world() throws ReflectiveOperationException {
        FakeWorld w = FakeWorld.create();
        Field provider = World.class.getDeclaredField("provider");
        provider.setAccessible(true);
        provider.set(w, new WorldProviderSurface());
        return w;
    }

    private static Nexus nexus(FakeWorld w) {
        Nexus n = w.put(new Nexus(), X, Y, Z);
        n.place((UUID) null, null, NORTH);
        n.regrow(4);
        return n;
    }

    private static void lay(FakeWorld w, BuildPlan.Plan p) {
        for (BuildPlan.Step s : p.steps) {
            if (s.kind == BuildPlan.AIR) {
                w.set(s.x, s.y, s.z, null, 0);
                continue;
            }
            Block b = PartBlocks.block(s.part);
            if (b != null) w.set(s.x, s.y, s.z, b, PartBlocks.meta(s.part));
        }
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
     * An active campus of the nexus: the zenith recorded as the dome's at the datum the base layer gives, the sites
     * {@code sky} as the Weather Station's (site 30) or the Observatory's (34), {@code job} the current one and
     * {@code queued} after it.
     */
    private static Campus campus(Nexus n, BuildJob job, int[] sky, BuildJob... queued) {
        int[] c = n.centre();
        int fx = n.front().offsetX, fz = n.front().offsetZ;
        NBTTagCompound t = new NBTTagCompound();
        t.setString("St", "active");
        t.setInteger("Pv", BuildJob.GRADE_V);
        if (job != null) t.setTag("Job", job.write());
        NBTTagList queue = new NBTTagList();
        for (BuildJob q : queued) queue.appendTag(q.write());
        t.setTag("Queue", queue);
        NBTTagList sites = new NBTTagList();
        int[] pod = DomeGeneratorShape.controller(ZENITH, c[0], c[1], c[2], fx, fz);
        sites.appendTag(site(ZENITH, pod, DomeGeneratorShape.KEY));
        for (int s : sky) {
            ModuleSpec spec = ModuleSpecs.get(s == 30 ? WeatherStationShape.KEY : "observatory");
            sites.appendTag(site(s, spec.controller(s, c[0], c[1], c[2], fx, fz), spec.key));
        }
        t.setTag("Sites", sites);
        NBTTagCompound tile = new NBTTagCompound();
        tile.setTag("Campus", t);
        Campus campus = n.campus();
        campus.readNBT(tile);
        return campus;
    }

    private static NBTTagCompound site(int site, int[] at, String key) {
        NBTTagCompound s = new NBTTagCompound();
        s.setInteger("S", site);
        s.setIntArray("P", new int[] { at[0], at[1], at[2] });
        s.setString("K", key);
        return s;
    }

    @Test
    void aSkyJobCarriesItsDeck() {
        ModuleSpec spec = ModuleSpecs.get(WeatherStationShape.KEY);
        BuildJob j = BuildJob.module(spec, 30);
        assertEquals(0, j.form().deck, "a sky job made by itself plans at the base layer's datum");
        j.deckAt(40);
        assertEquals(40, j.form().deck);
        NBTTagCompound t = j.write();
        assertEquals(40, t.getInteger("Sd"));
        assertFalse(t.hasKey("Pf"));
        assertEquals(
            40,
            BuildJob.read(t)
                .form().deck);
        assertEquals(
            "|s40",
            j.form()
                .signature());
        // a bad datum reads as none; a job of any other kind takes no deck
        NBTTagCompound bad = new NBTTagCompound();
        bad.setInteger("Sd", 39);
        assertEquals(0, BuildJob.Form.read(bad).deck);
        BuildJob dome = BuildJob.module(ModuleSpecs.get(DomeGeneratorShape.KEY), ZENITH);
        dome.deckAt(40);
        assertEquals(0, dome.form().deck);
        assertFalse(
            dome.write()
                .hasKey("Sd"),
            "a dome job saves as 0.15.0 saved it");
        // its plan and the controller the campus waits on are at its deck, over high ground too (Y0 205: F 36)
        int y0 = 205;
        BuildPlan.Plan at40 = BuildJob.planFor(
            j.planKey(),
            new CampusPlan(1),
            0,
            y0,
            0,
            0,
            -1,
            0,
            0,
            0,
            j.keepOut(),
            j.neighbours(),
            j.regainFrom(),
            false,
            j.patch(),
            j.form());
        assertEquals(BuildPlan.sky(30, 0, y0, 0, 0, -1, 40, WeatherStationShape.BUILD).steps, at40.steps);
        BuildPlan.Plan base = BuildJob.planFor(
            j.planKey(),
            new CampusPlan(1),
            0,
            y0,
            0,
            0,
            -1,
            0,
            0,
            0,
            j.keepOut(),
            j.neighbours(),
            j.regainFrom(),
            false,
            j.patch(),
            null);
        assertEquals(BuildPlan.sky(30, 0, y0, 0, 0, -1, 36, WeatherStationShape.BUILD).steps, base.steps);
        assertEquals("build.stage.k5", NexusGui.stageKey(j.planKey(), SkyRing.K_DECOR));
    }

    @Test
    void theJobsControllerAndThePodsBoxesItIsLetInto() throws ReflectiveOperationException {
        FakeWorld w = world();
        Nexus n = nexus(w);
        int[] c = n.centre();
        int fx = n.front().offsetX, fz = n.front().offsetZ;
        ModuleSpec spec = ModuleSpecs.get(WeatherStationShape.KEY);
        BuildJob j = BuildJob.module(spec, 30);
        j.deckAt(40);
        Campus idle = campus(n, null, new int[0]);
        assertArrayEquals(
            SkyRing.controller(WeatherStationShape.BUILD, 30, c[0], c[1], c[2], fx, fz, 40),
            idle.moduleController(j));
        List<int[]> exempt = SkyRing.exempt(30, c[0], c[1], c[2], fx, fz, 40);
        for (int[] e : exempt) assertTrue(idle.protectedCell(e[0], e[1], e[2]), "the pod's own, with no job");
        Campus building = campus(n, j, new int[0]);
        for (int[] e : exempt) assertFalse(building.protectedCell(e[0], e[1], e[2]), "its door and catwalk's mouth");
        // the rest of the pod's box, and the next sky site's door, still hold it
        int[] pad = DomeGeneratorShape.moor(40)
            .podToCampus(10, 1, 10);
        int[] pw = CampusPlan.toWorld(pad[0], pad[1], fx, fz);
        assertTrue(building.protectedCell(c[0] + pw[0], c[1] + pad[2], c[2] + pw[1]), "over the pod's pad");
        for (int[] e : SkyRing.exempt(32, c[0], c[1], c[2], fx, fz, 40))
            assertTrue(building.protectedCell(e[0], e[1], e[2]), "another site's door");
        // over the catwalk's mouth the pod's box holds the job too
        int[] over = exempt.get(exempt.size() - 1);
        assertTrue(building.protectedCell(over[0], over[1] + 1, over[2]));
    }

    @Test
    void theDoorsAPodRepairLeavesOpen() throws ReflectiveOperationException {
        FakeWorld w = world();
        Nexus n = nexus(w);
        int[] c = n.centre();
        int fx = n.front().offsetX, fz = n.front().offsetZ;
        ModuleSpec dome = ModuleSpecs.get(DomeGeneratorShape.KEY), weather = ModuleSpecs.get(WeatherStationShape.KEY);
        // the weather station stands on S0, the observatory's job is queued for S4
        BuildJob observatory = BuildJob.module(ModuleSpecs.get("observatory"), 34);
        Campus campus = campus(n, null, new int[] { 30 }, observatory);
        assertEquals(SkyRing.bit(30) | SkyRing.bit(34), campus.openDoors());

        // the pod floats, built by the floating plan with those doors open
        BuildJob fresh = BuildJob.module(dome, ZENITH);
        fresh.floatAt(40, campus.openDoors());
        BuildPlan.Plan pod = plan(fresh, n);
        Set<Long> doors = new HashSet<>();
        for (int site : new int[] { 30, 34 }) for (int[] d : SkyRing.doorCells(site, 40)) {
            int[] dw = CampusPlan.toWorld(d[0], d[1], fx, fz);
            doors.add(BuildJob.pos(c[0] + dw[0], c[1] + d[2], c[2] + dw[1]));
        }
        for (BuildPlan.Step s : pod.steps) assertFalse(doors.contains(BuildJob.pos(s.x, s.y, s.z)), "glazed: " + s);
        lay(w, pod);
        int[] at = campus.moduleController(fresh);
        Pod d = w.put(new Pod(), at[0], at[1], at[2]);
        d.over = n;
        d.place((UUID) null, null, at[3]);
        d.expectShape(DomeGeneratorShape.FLOATING);
        try {
            recheck(d);
            assertTrue(d.formed(), "it floats with its doors open: " + d.firstWrong());
            // a repair of it leaves the same doors open
            BuildJob repair = Campus.repairOf(dome, ZENITH, d, c[1], campus.openDoors());
            assertTrue(repair.floats());
            assertEquals(SkyRing.bit(30) | SkyRing.bit(34), repair.form().doors);
            assertEquals(
                repair.form().doors,
                BuildJob.read(repair.write())
                    .form().doors);
            for (BuildPlan.Step s : plan(repair, n).steps)
                assertFalse(doors.contains(BuildJob.pos(s.x, s.y, s.z)), "a repair glazes a door: " + s);

            // the weather station's job laid next to it: the module forms, and the walk reaches it through the door
            BuildJob job = BuildJob.module(weather, 30);
            job.deckAt(DomeGeneratorShape.podDatum(d.yCoord, c[1]));
            assertEquals(40, job.form().deck);
            lay(w, plan(job, n));
            int[] m = campus.moduleController(job);
            TileWeatherStation t = w.put(new TileWeatherStation(), m[0], m[1], m[2]);
            t.place((UUID) null, null, m[3]);
            recheck(t);
            assertTrue(t.formed(), "the weather station: " + t.firstWrong());
            recheck(d);
            assertTrue(d.formed(), "the pod stays formed with the catwalk at its door");
            // its repair is laid at the deck it stands at
            BuildJob fix = Campus.repairOf(weather, 30, t, c[1], 0);
            assertEquals(40, fix.form().deck);
            assertEquals(0, Campus.skyDatum(d, c[1]), "the pod is no sky module");
            assertEquals(
                0,
                Campus.repairOf(weather, 30, null, c[1], 0)
                    .form().deck,
                "no deck from nothing");
        } finally {
            clearCells(d);
        }
    }

    /**
     * A fresh pod meets the decks already standing (0.16.0): over high ground (Y0 205, where the base layer gives F 36)
     * a weather station laid level with a pod built by hand on the full legs keeps its deck at 40 after that pod is
     * gone; the dome job offered then floats at 40, its controller at Y0 + 42, its door to S0 open (the floor, the door
     * and the catwalk at one level, the composite walk {@code KitClimbTest} climbs), never at 36 under the catwalk.
     * With no sky module it floats where the base layer says.
     */
    @Test
    void aFreshPodMeetsTheDecksAlreadyStanding() throws ReflectiveOperationException {
        assertEquals(40, SkyRing.podDatumFor(205, new int[] { 40 }));
        assertEquals(36, SkyRing.podDatumFor(205, new int[0]));
        assertEquals(36, SkyRing.podDatumFor(205, null));
        assertEquals(40, SkyRing.podDatumFor(205, new int[] { 0, 39, 40, 36 }), "the first that is a sky datum");
        assertEquals(40, SkyRing.podDatumFor(70, new int[0]));

        FakeWorld w = world();
        Nexus n = w.put(new Nexus(), X, 207, Z);
        n.place((UUID) null, null, NORTH);
        n.regrow(4);
        int[] c = n.centre();
        assertEquals(205, c[1]);
        int fx = n.front().offsetX, fz = n.front().offsetZ;
        ModuleSpec dome = ModuleSpecs.get(DomeGeneratorShape.KEY);
        assertEquals(36, DomeGeneratorShape.datum(c[1]), "the base layer's datum");

        Campus none = campusAt(n, -1, null);
        assertEquals(36, none.freshPodDatum(), "no sky module: the base layer's datum");

        int[] station = SkyRing.controller(WeatherStationShape.BUILD, 30, c[0], c[1], c[2], fx, fz, 40);
        Campus campus = campusAt(n, 30, station);
        assertEquals(40, campus.freshPodDatum(), "the standing deck's");
        BuildJob fresh = BuildJob.module(dome, ZENITH);
        fresh.floatAt(campus.freshPodDatum(), campus.openDoors());
        assertTrue(fresh.floats());
        assertEquals(40, fresh.form().pod);
        assertEquals(SkyRing.bit(30), fresh.form().doors);
        int[] ctrl = campus.moduleController(fresh);
        assertEquals(c[1] + 42, ctrl[1], "the pod's controller at Y0 + 42");
        BuildPlan.Plan pod = plan(fresh, n);
        assertTrue(
            pod.steps.get(pod.steps.size() - 1)
                .at(ctrl[0], ctrl[1], ctrl[2]));
        // the pod's floor, its open door and the station's catwalk at one level
        assertEquals(station[1] - DomeGeneratorShape.CTRL_Y, c[1] + 40, "the station's deck");
        Set<Long> door = new HashSet<>();
        for (int[] d : SkyRing.doorCells(30, 40)) {
            int[] dw = CampusPlan.toWorld(d[0], d[1], fx, fz);
            assertTrue(d[2] >= 41 && d[2] <= 44, "the door over the floor at 40");
            door.add(BuildJob.pos(c[0] + dw[0], c[1] + d[2], c[2] + dw[1]));
        }
        for (BuildPlan.Step st : pod.steps) assertFalse(door.contains(BuildJob.pos(st.x, st.y, st.z)), "glazed: " + st);
        for (BuildPlan.Column col : pod.clear) assertTrue(col.yFrom >= c[1] + 40, "nothing cleared under the floor");
        // a job for the station's platform not ended gives its deck as well, the recorded site gone
        BuildJob job = BuildJob.module(ModuleSpecs.get(WeatherStationShape.KEY), 30);
        job.deckAt(40);
        Campus building = campusAt(n, -1, null, job);
        assertEquals(40, building.freshPodDatum(), "the deck of a sky job not ended");
    }

    /**
     * An active campus of the nexus with the zenith's site recorded, a sky site {@code sky} at {@code at}, the jobs.
     */
    private static Campus campusAt(Nexus n, int sky, int[] at, BuildJob... jobs) {
        int[] c = n.centre();
        NBTTagCompound t = new NBTTagCompound();
        t.setString("St", "active");
        t.setInteger("Pv", BuildJob.GRADE_V);
        if (jobs.length > 0) t.setTag("Job", jobs[0].write());
        NBTTagList queue = new NBTTagList();
        for (int i = 1; i < jobs.length; i++) queue.appendTag(jobs[i].write());
        t.setTag("Queue", queue);
        NBTTagList sites = new NBTTagList();
        sites.appendTag(
            site(
                ZENITH,
                DomeGeneratorShape.controller(ZENITH, c[0], c[1], c[2], n.front().offsetX, n.front().offsetZ),
                DomeGeneratorShape.KEY));
        if (sky >= 0) sites.appendTag(site(sky, at, WeatherStationShape.KEY));
        t.setTag("Sites", sites);
        NBTTagCompound tile = new NBTTagCompound();
        tile.setTag("Campus", t);
        Campus campus = n.campus();
        campus.readNBT(tile);
        return campus;
    }
}
