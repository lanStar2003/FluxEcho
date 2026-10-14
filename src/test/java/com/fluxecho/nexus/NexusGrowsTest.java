package com.fluxecho.nexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fluxecho.campus.BlockFitting;
import com.fluxecho.campus.CampusModule;
import com.fluxecho.campus.FakeWorld;
import com.fluxecho.campus.PartBlocks;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.frame.FrameModule;
import com.fluxecho.logic.Blueprint;
import com.fluxecho.logic.NexusShape;
import com.fluxecho.logic.Parts;

import cpw.mods.fml.common.Loader;

/**
 * A nexus grown to phase II finds itself grown again once stage 3 stands on it (0.15.0): each phase holds the one
 * below it whole, so with stage 3 raised the phase-II blueprint still checks out, and a check that tried the phase it
 * stood as first never looked at phase III; the 0.15.0 batch (the outer ring, both new buildings) waits on a nexus of
 * phase III. Here a real nexus tile checks itself with its own structure definition in a world of its own, its frame
 * blocks and lift pad set where the blueprints put them, its teams' research given by the test: with
 * {@code phase_3} it stands as phase III, without it as phase II.
 * <p>
 * StructureLib's check needs its mod class loaded, which outside the game needs a bare FML loader standing in while
 * it loads (as {@code DoorwayRoomTest} does it); the frame block and the fittings block are made here when the game has
 * not made them, and taken away again after.
 */
class NexusGrowsTest {

    private static final int X = 40, Y = 70, Z = -30, NORTH = 2;
    private static BlockFrame oldFrame;
    private static BlockFitting oldFitting;

    /** A nexus whose teams have the research the test names, and that tells no team it formed. */
    private static final class Grower extends TileNexus {

        final Set<String> done = new HashSet<>();

        @Override
        public boolean has(String id) {
            return done.contains(id);
        }

        @Override
        protected void formedChanged(boolean now) {
            // the anchor's grant needs the server's saved data; the phase follows shapeChanged
        }
    }

    @BeforeAll
    static void setUp() throws ReflectiveOperationException {
        structureLib();
        oldFrame = FrameModule.frame;
        oldFitting = CampusModule.fitting;
        if (FrameModule.frame == null) FrameModule.frame = new BlockFrame();
        if (CampusModule.fitting == null) CampusModule.fitting = new BlockFitting();
        forgetDefinitions();
    }

    @AfterAll
    static void tearDown() throws ReflectiveOperationException {
        FrameModule.frame = oldFrame;
        CampusModule.fitting = oldFitting;
        forgetDefinitions();
    }

    /** The nexus's structure definitions are made once from the blocks there are: made again with the test's. */
    private static void forgetDefinitions() throws ReflectiveOperationException {
        for (String name : new String[] { "definition", "buildDefinition" }) {
            Field f = TileNexus.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(null, null);
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
            Class.forName("com.gtnewhorizon.structurelib.StructureLib", true, NexusGrowsTest.class.getClassLoader());
        } finally {
            if (none) instance.set(null, null);
        }
    }

    /** Sets every block of a phase's blueprint round a controller at (X, Y, Z) facing north. */
    private static void raise(FakeWorld w, int phase) {
        Blueprint bp = NexusShape.phase(phase);
        for (Blueprint.Cell c : bp.cells()) {
            if (c.ch == '~') continue;
            int[] p = bp.world(c.a, c.b, c.c, X, Y, Z, 0, -1);
            if (c.ch == NexusShape.LIFT) {
                int lift = Parts.fitting(Parts.F_LIFT);
                w.set(p[0], p[1], p[2], PartBlocks.block(lift), PartBlocks.meta(lift));
            } else w.set(p[0], p[1], p[2], FrameModule.frame, frameMeta(c.ch));
        }
    }

    private static int frameMeta(char ch) {
        switch (ch) {
            case NexusShape.BASE:
                return BlockFrame.BASE;
            case NexusShape.LIT:
                return BlockFrame.BASE_LIT;
            case NexusShape.PILLAR:
                return BlockFrame.PILLAR;
            case NexusShape.CONDUIT:
                return BlockFrame.CONDUIT;
            case NexusShape.RING:
                return BlockFrame.RING;
            case NexusShape.SEAT:
                return BlockFrame.SEAT;
            case NexusShape.CONSOLE:
                return BlockFrame.CONSOLE;
            default:
                throw new IllegalArgumentException("no frame part " + ch);
        }
    }

    /** Runs the server's structure check once, as its 100-tick recheck does. */
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

    /** A world of its own with a provider (the formed cells are kept per dimension) and a nexus grown to phase II. */
    private static Grower phaseTwo(FakeWorld w, String... research) throws ReflectiveOperationException {
        Field provider = World.class.getDeclaredField("provider");
        provider.setAccessible(true);
        provider.set(w, new WorldProviderSurface());
        raise(w, 2);
        Grower n = w.put(new Grower(), X, Y, Z);
        n.done.addAll(Arrays.asList(research));
        n.place((UUID) null, null, NORTH);
        n.regrow(2);
        n.expectShape(NexusShape.shapeName(2));
        return n;
    }

    @Test
    void aPhaseTwoNexusWithStageThreeStandsAsPhaseThree() throws ReflectiveOperationException {
        FakeWorld w = FakeWorld.create();
        Grower n = phaseTwo(w, "phase_2", "phase_3");
        try {
            recheck(n);
            assertTrue(n.formed(), "phase II stands");
            assertEquals("phase2", n.shapeName());
            assertEquals(2, n.phase());
            // the growth job raises stage 3 on it: the next check finds it grown
            raise(w, 3);
            recheck(n);
            assertTrue(n.formed());
            assertEquals("phase3", n.shapeName(), "the fullest phase that stands");
            assertEquals(3, n.phase(), "grown");
            assertEquals(3, n.grown());
            // and it stays so on the checks after
            recheck(n);
            assertEquals("phase3", n.shapeName());
        } finally {
            clearCells(n);
        }
    }

    /**
     * The same a phase further (0.16.0): a nexus grown to phase III with stage 4 raised on it stands as phase IV once
     * its teams have {@code phase_4}, and as phase III until then.
     */
    @Test
    void aPhaseThreeNexusWithStageFourStandsAsPhaseFour() throws ReflectiveOperationException {
        FakeWorld w = FakeWorld.create();
        Grower n = phaseTwo(w, "phase_2", "phase_3");
        try {
            raise(w, 3);
            recheck(n);
            assertEquals("phase3", n.shapeName());
            assertEquals(3, n.phase());
            raise(w, 4);
            recheck(n);
            assertTrue(n.formed());
            assertEquals("phase3", n.shapeName(), "phase IV is not researched");
            assertEquals(3, n.phase());
            // the research comes in: the next check finds it grown
            n.done.add("phase_4");
            recheck(n);
            assertTrue(n.formed());
            assertEquals("phase4", n.shapeName(), "the fullest phase that stands");
            assertEquals(4, n.phase(), "grown");
            assertEquals(4, n.grown());
            recheck(n);
            assertEquals("phase4", n.shapeName());
        } finally {
            clearCells(n);
        }
    }

    @Test
    void withoutPhaseThreesResearchItStaysPhaseTwo() throws ReflectiveOperationException {
        FakeWorld w = FakeWorld.create();
        Grower n = phaseTwo(w, "phase_2");
        try {
            raise(w, 3);
            recheck(n);
            assertTrue(n.formed());
            assertEquals("phase2", n.shapeName(), "phase III is not researched");
            assertEquals(2, n.phase());
            // the research comes in: the next check finds it grown
            n.done.add("phase_3");
            recheck(n);
            assertEquals("phase3", n.shapeName());
            assertEquals(3, n.phase());
        } finally {
            clearCells(n);
        }
    }
}
