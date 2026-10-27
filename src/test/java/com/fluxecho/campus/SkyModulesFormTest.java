package com.fluxecho.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSnow;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fluxecho.city.TileCityModule;
import com.fluxecho.city.TileDropletHangar;
import com.fluxecho.city.TileObservatory;
import com.fluxecho.city.TileSkySails;
import com.fluxecho.city.TileWeatherStation;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.frame.FrameModule;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.CityBuild;
import com.fluxecho.logic.DomeGeneratorShape;
import com.fluxecho.logic.DropletHangarShape;
import com.fluxecho.logic.ObservatoryShape;
import com.fluxecho.logic.SkyRing;
import com.fluxecho.logic.SkySailsShape;
import com.fluxecho.logic.WeatherStationShape;
import com.fluxecho.nexus.TileMultiblock;

import cpw.mods.fml.common.Loader;

/**
 * Every sky module forms where its real plan builds it (0.16.0, M13 §5, §6), on the {@code PodReleaseTest} harness: a
 * real tile of each of the four checks itself with its own structure definition in a world of its own, every block set
 * where {@link BuildPlan#sky} puts it, on every module site of the sky ring (S0, S2, S4, S6) and for every front of
 * the nexus, over a low base and over the highest base a core may be placed at (Y0 225, its deck at 253, the masts and
 * the roofs clipped at the world's top); and it forms the same with a snow layer settled in every cell its plan
 * empties (its doorways and the pod's door), as 0.14.2's doorway lets it. A block taken out of it unforms it, and the
 * first wrong cell is named.
 */
@Tag("slow")
class SkyModulesFormTest {

    private static BlockFrame oldFrame;
    private static BlockDeck oldDeck;
    private static BlockFitting oldFitting;
    private static Block snow;

    /** A module kind: its tile and its build. */
    private static final class Kind {

        final Supplier<TileCityModule> make;
        final CityBuild build;

        Kind(Supplier<TileCityModule> make, CityBuild build) {
            this.make = make;
            this.build = build;
        }
    }

    private static final Kind[] KINDS = { new Kind(TileWeatherStation::new, WeatherStationShape.BUILD),
        new Kind(TileSkySails::new, SkySailsShape.BUILD), new Kind(TileObservatory::new, ObservatoryShape.BUILD),
        new Kind(TileDropletHangar::new, DropletHangarShape.BUILD) };
    private static final int[][] FRONTS = { { 0, -1 }, { 0, 1 }, { -1, 0 }, { 1, 0 } };

    @BeforeAll
    static void setUp() throws ReflectiveOperationException {
        structureLib();
        oldFrame = FrameModule.frame;
        oldDeck = CampusModule.deck;
        oldFitting = CampusModule.fitting;
        if (FrameModule.frame == null) FrameModule.frame = new BlockFrame();
        if (CampusModule.deck == null) CampusModule.deck = new BlockDeck();
        if (CampusModule.fitting == null) CampusModule.fitting = new BlockFitting();
        snow = new BlockSnow() {}.setBlockName("snow");
        forgetDefinitions();
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
            Class
                .forName("com.gtnewhorizon.structurelib.StructureLib", true, SkyModulesFormTest.class.getClassLoader());
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

    /** A world of its own with a provider (the formed cells are kept per dimension). */
    private static FakeWorld world() throws ReflectiveOperationException {
        FakeWorld w = FakeWorld.create();
        Field provider = World.class.getDeclaredField("provider");
        provider.setAccessible(true);
        provider.set(w, new WorldProviderSurface());
        return w;
    }

    /**
     * Sets every block a plan places (an AIR step's cell emptied, or with {@code weather} a snow layer of meta 0 left
     * in it); the controller is the test's tile.
     */
    private static void lay(FakeWorld w, BuildPlan.Plan p, boolean weather) {
        for (BuildPlan.Step s : p.steps) {
            if (s.kind == BuildPlan.AIR) {
                w.set(s.x, s.y, s.z, weather ? snow : null, 0);
                continue;
            }
            Block b = PartBlocks.block(s.part);
            if (b != null) w.set(s.x, s.y, s.z, b, PartBlocks.meta(s.part));
        }
    }

    @Test
    void everySkyModuleFormsOnEverySiteAndFrontInAnyWeather() throws ReflectiveOperationException {
        int formed = 0;
        for (Kind k : KINDS)
            for (int site = SkyRing.SITE0; site < SkyRing.SITE0 + SkyRing.SLOTS; site += 2) for (int[] f : FRONTS)
                for (int y0 : new int[] { 64, 225 }) for (boolean weather : new boolean[] { false, true }) {
                    int cx = 120, cz = -340, datum = DomeGeneratorShape.datum(y0);
                    String what = k.build
                        .key() + "@" + site + " front " + f[0] + "," + f[1] + " Y0 " + y0 + (weather ? " in snow" : "");
                    FakeWorld w = world();
                    BuildPlan.Plan plan = BuildPlan.sky(site, cx, y0, cz, f[0], f[1], datum, k.build);
                    lay(w, plan, weather);
                    int[] at = SkyRing.controller(k.build, site, cx, y0, cz, f[0], f[1], datum);
                    TileCityModule t = w.put(k.make.get(), at[0], at[1], at[2]);
                    t.place((UUID) null, null, at[3]);
                    recheck(t);
                    assertTrue(t.formed(), what + ": " + t.firstWrong());
                    formed++;
                    if (weather || y0 != 64) continue;
                    // a block of its frame taken out unforms it, and is the first wrong cell named
                    BuildPlan.Step hard = null;
                    for (BuildPlan.Step s : plan.steps)
                        if (s.kind == BuildPlan.HARD && s.stage == SkyRing.K_FIT) hard = s;
                    w.set(hard.x, hard.y, hard.z, null, 0);
                    recheck(t);
                    assertFalse(t.formed(), what);
                    assertEquals(hard.x, t.firstWrong().x, what);
                    assertEquals(hard.y, t.firstWrong().y, what);
                    assertEquals(hard.z, t.firstWrong().z, what);
                }
        assertEquals(4 * 4 * 4 * 2 * 2, formed);
    }

    /**
     * Beside a pod built by hand on the full legs over high ground (at 40, Y0 204..213), its deck is laid level with
     * that pod's floor at the datum its job carries, and it forms there.
     */
    @Test
    void aModuleBesideAPodOnTheFullLegsFormsAtItsDeck() throws ReflectiveOperationException {
        for (Kind k : KINDS) {
            int y0 = 210, cx = 0, cz = 0;
            FakeWorld w = world();
            BuildPlan.Plan plan = BuildPlan.sky(32, cx, y0, cz, 1, 0, DomeGeneratorShape.FLOOR_UP, k.build);
            lay(w, plan, true);
            int[] at = SkyRing.controller(k.build, 32, cx, y0, cz, 1, 0, DomeGeneratorShape.FLOOR_UP);
            assertEquals(y0 + 42, at[1]);
            TileCityModule t = w.put(k.make.get(), at[0], at[1], at[2]);
            t.place((UUID) null, null, at[3]);
            recheck(t);
            assertTrue(t.formed(), k.build.key() + ": " + t.firstWrong());
        }
    }
}
