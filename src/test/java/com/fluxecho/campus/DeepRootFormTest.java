package com.fluxecho.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSnow;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fluxecho.city.TileCityModule;
import com.fluxecho.city.TileRoot;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.frame.FrameModule;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.ResearchTree;
import com.fluxecho.logic.RootShape;
import com.fluxecho.logic.SiteFamily;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.nexus.TileNexus;
import com.gtnewhorizons.modularui.api.forge.ItemStackHandler;

import cpw.mods.fml.common.Loader;

/**
 * The deep root forms where its real plans build it (0.16.0, CITY.md §4.9, the design's §7.10), on the
 * {@code PodReleaseTest} harness: a real root tile checks itself with its own structure definition in a world of its
 * own, every block set where the plans put them, under a nexus of phase IV. A well the real well plan sank grows by
 * the real deepen plan into the form the rule gives its base layer, staying formed as the well all through until its
 * floor slab's last cells change (a chamber), or until its last fitting lands (in-well), in any weather; a chamber
 * root with a broken bay keeps its form for its repair; an in-well root with a broken bay stands as its well again,
 * its bays' samples kept; and every root tile keeps 16 samples, a well's 8-slot save loading into them.
 */
@Tag("slow")
class DeepRootFormTest {

    private static final int X = 40, Z = -30, EAST = 5;
    private static BlockFrame oldFrame;
    private static BlockDeck oldDeck;
    private static BlockFitting oldFitting;
    private static Block snow;

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

    /** A root that finds the test's nexus over it, as one under a loaded nexus does. */
    private static final class Root extends TileRoot {

        TileNexus over;

        @Override
        public TileNexus over() {
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
            Class.forName("com.gtnewhorizon.structurelib.StructureLib", true, DeepRootFormTest.class.getClassLoader());
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

    /** A nexus over the base layer {@code y0}, facing east, of the phase given, its teams with the deep root. */
    private static Nexus nexus(FakeWorld w, int y0, int phase) {
        Nexus n = w.put(new Nexus(), X, y0 + 2, Z);
        n.place((UUID) null, null, EAST);
        n.regrow(phase);
        n.done.add(ResearchTree.DEEP_ROOT);
        assertEquals(y0, n.centre()[1]);
        return n;
    }

    /**
     * Sets the blocks of the plan's steps {@code from} (inclusive) to {@code to} (exclusive) as the builder lands them:
     * a dig or a drain leaves this world's empty cell as it is, an AIR step's cell is emptied (or with {@code weather}
     * holds a snow layer of meta 0); the controller is the test's tile.
     */
    private static void lay(FakeWorld w, BuildPlan.Plan p, int from, int to, boolean weather) {
        for (int i = from; i < to; i++) {
            BuildPlan.Step s = p.steps.get(i);
            if (BuildPlan.digs(s.kind)) continue;
            if (s.kind == BuildPlan.AIR) {
                w.set(s.x, s.y, s.z, weather ? snow : null, 0);
                continue;
            }
            Block b = PartBlocks.block(s.part);
            if (b != null) w.set(s.x, s.y, s.z, b, PartBlocks.meta(s.part));
        }
    }

    private static void lay(FakeWorld w, BuildPlan.Plan p, boolean weather) {
        lay(w, p, 0, p.steps.size(), weather);
    }

    /** The root's tile on the controller cell {x, y, z, facing}, under the nexus. */
    private static Root root(FakeWorld w, TileNexus n, int[] at) {
        Root r = w.put(new Root(), at[0], at[1], at[2]);
        r.over = n;
        r.place((UUID) null, null, at[3]);
        return r;
    }

    /**
     * A well the real well plan sank under a nexus of phase IV grows by the real deepen plan into the form its base
     * layer's rule gives, at every form, in clear weather and with snow in every cell a plan empties: the well stands
     * formed all through the chamber's sinking and its fittings, comes apart at the first cell its floor slab changes
     * and forms as the deep root once the last lands (an in-well form, holding its well whole, forms at its last
     * fitting); the tile then keeps the form's build, units, sixteen samples, name and colour.
     */
    @Test
    void aWellGrowsIntoTheRulesFormByTheDeepenPlan() throws ReflectiveOperationException {
        int[] bases = { 70, 41, 35, 28, 22, 16 };
        for (int y0 : bases) for (boolean weather : new boolean[] { false, true }) {
            FakeWorld w = world();
            Nexus n = nexus(w, y0, 4);
            int[] c = n.centre();
            int fx = n.front().offsetX, fz = n.front().offsetZ;
            RootShape.Well well = RootShape.forBase(y0);
            RootShape.Deep d = RootShape.deepFor(y0, well);
            String what = d.name + " at Y0 " + y0 + (weather ? " in snow" : "");
            lay(w, BuildPlan.root(SiteFamily.ROOT_SITE, c[0], y0, c[2], fx, fz), weather);
            Root r = root(w, n, well.controller(SiteFamily.ROOT_SITE, c[0], y0, c[2], fx, fz));
            try {
                recheck(r);
                assertTrue(r.formed(), what + ": the well stands " + r.firstWrong());
                assertEquals(well.name, r.shapeName());
                assertEquals(
                    RootShape.SLOTS,
                    r.samples()
                        .getSlots());
                assertEquals(8, r.shownSlots(), "a well shows its taps");

                BuildPlan.Plan p = BuildPlan.deepen(SiteFamily.ROOT_SITE, c[0], y0, c[2], fx, fz, d);
                int opens = d.h > 0 ? 25 : 0, firstOpen = p.end(RootShape.R_FIT) - opens;
                if (d.h > 0) {
                    lay(w, p, 0, firstOpen, weather);
                    recheck(r);
                    assertTrue(r.formed(), what + ": the well stands while its chamber is sunk and fitted");
                    assertEquals(well.name, r.shapeName());
                    lay(w, p, firstOpen, firstOpen + 1, weather);
                    recheck(r);
                    assertFalse(r.formed(), what + ": the first grate cell takes the well apart");
                    lay(w, p, firstOpen + 1, p.steps.size(), weather);
                } else lay(w, p, weather);
                recheck(r);
                assertTrue(r.formed(), what + ": " + r.firstWrong());
                assertEquals(d.name, r.shapeName(), what);
                assertSame(d.build, r.build());
                assertSame(d.units, r.layout());
                assertSame(well, r.well());
                assertEquals(d.h, r.base());
                assertEquals(16, r.unitCount());
                assertEquals(RootShape.SLOTS, r.shownSlots());
                assertEquals("module.root.deep", r.nameKey());
                assertEquals(TileRoot.DEEP_COLOR, r.moduleColor());
                // its own box once formed: down to the chamber's floor slab
                int[] floor = r.worldOf(12, 0, 12);
                assertEquals(y0 - well.depth - d.h, floor[1]);
                assertTrue(r.covers(w, floor[0], floor[1], floor[2]));
                recheck(r);
                assertEquals(d.name, r.shapeName(), what + ": and stays so");
            } finally {
                clearCells(r);
            }
        }
    }

    /**
     * Without phase IV the well its cell stands for is all its check tries: a well with the deep root's every block
     * still stands as the well (in-well) or not at all (a chamber, its floor slab changed); at phase IV it is the deep
     * root.
     */
    @Test
    void theDeepRootStandsOnlyUnderANexusOfPhaseFour() throws ReflectiveOperationException {
        for (int y0 : new int[] { 70, 35 }) {
            FakeWorld w = world();
            Nexus n = nexus(w, y0, 3);
            int[] c = n.centre();
            int fx = n.front().offsetX, fz = n.front().offsetZ;
            RootShape.Deep d = RootShape.deepFor(y0, RootShape.forBase(y0));
            lay(w, BuildPlan.deep(SiteFamily.ROOT_SITE, c[0], y0, c[2], fx, fz, d), false);
            Root r = root(w, n, d.controller(SiteFamily.ROOT_SITE, c[0], y0, c[2], fx, fz));
            try {
                recheck(r);
                assertEquals(d.h == 0, r.formed(), d.name + " at phase III");
                assertEquals(RootShape.WELL, r.shapeName());
                n.regrow(4);
                recheck(r);
                assertTrue(r.formed());
                assertEquals(d.name, r.shapeName(), "at phase IV");
            } finally {
                clearCells(r);
            }
        }
    }

    /**
     * A chamber root with a broken bay (0.16.0) comes apart, but keeps its form under a nexus of phase IV, so its
     * repair puts it back whole ({@code Campus.repairOf} reads the form from its name); the broken cell is the first
     * wrong one named. The repair's whole plan laid again, it forms.
     */
    @Test
    void aChamberRootWithABrokenBayKeepsItsForm() throws ReflectiveOperationException {
        FakeWorld w = world();
        int y0 = 70;
        Nexus n = nexus(w, y0, 4);
        int[] c = n.centre();
        int fx = n.front().offsetX, fz = n.front().offsetZ;
        RootShape.Deep d = RootShape.deep(RootShape.DEEP);
        BuildPlan.Plan whole = BuildPlan.deep(SiteFamily.ROOT_SITE, c[0], y0, c[2], fx, fz, d);
        lay(w, whole, false);
        Root r = root(w, n, d.controller(SiteFamily.ROOT_SITE, c[0], y0, c[2], fx, fz));
        try {
            recheck(r);
            assertTrue(r.formed(), "laid whole by its repair's plan: " + r.firstWrong());
            assertEquals(RootShape.DEEP, r.shapeName());
            int[] socket = r.unitCells(12)
                .get(1);
            w.set(socket[0], socket[1], socket[2], null, 0);
            recheck(r);
            assertFalse(r.formed());
            assertEquals(RootShape.DEEP, r.shapeName(), "its form kept for its repair");
            assertEquals(RootShape.DEEP, Campus.deepRootOf(r));
            BuildJob repair = Campus.repairOf(com.fluxecho.city.CityModule.root(), SiteFamily.ROOT_SITE, r, y0);
            assertEquals(RootShape.DEEP, repair.form().root);
            assertTrue(repair.form().whole);
            assertFalse(repair.deepens());
            assertEquals(socket[0], r.firstWrong().x);
            assertEquals(socket[1], r.firstWrong().y);
            assertEquals(socket[2], r.firstWrong().z);
            lay(w, whole, false);
            recheck(r);
            assertTrue(r.formed(), "repaired");
            assertEquals(RootShape.DEEP, r.shapeName());
        } finally {
            clearCells(r);
        }
    }

    /**
     * An in-well root with a broken bay holds its well whole, so it stands as its well again (no repair: the deepen
     * job is offered again); the samples of its bays stay in their slots, which the well does not show.
     */
    @Test
    void anInWellRootWithABrokenBayStandsAsItsWell() throws ReflectiveOperationException {
        FakeWorld w = world();
        int y0 = 22;
        Nexus n = nexus(w, y0, 4);
        int[] c = n.centre();
        int fx = n.front().offsetX, fz = n.front().offsetZ;
        RootShape.Deep d = RootShape.deep(RootShape.ROOT_IN_14);
        lay(w, BuildPlan.deep(SiteFamily.ROOT_SITE, c[0], y0, c[2], fx, fz, d), true);
        Root r = root(w, n, d.controller(SiteFamily.ROOT_SITE, c[0], y0, c[2], fx, fz));
        try {
            recheck(r);
            assertTrue(r.formed(), r.firstWrong() + "");
            assertEquals(RootShape.ROOT_IN_14, r.shapeName());
            ItemStack sample = new ItemStack(new Item().setUnlocalizedName("sample"));
            r.samples()
                .setStackInSlot(9, sample);
            assertEquals(1, r.sampleCount());
            int[] plinth = r.unitCells(9)
                .get(0);
            w.set(plinth[0], plinth[1], plinth[2], null, 0);
            recheck(r);
            assertTrue(r.formed(), "it stands as its well");
            assertEquals(RootShape.WELL_14, r.shapeName());
            assertNull(Campus.deepRootOf(r), "nothing for a repair to put back");
            assertSame(
                sample,
                r.samples()
                    .getStackInSlot(9),
                "the bay's sample stays where it was");
            assertEquals(8, r.shownSlots());
            assertEquals(0, r.sampleCount(), "a well shows its taps only");
            assertTrue(
                r.contents()
                    .contains(sample),
                "and gives it back when its core is broken");
            assertEquals(
                RootShape.ROOT_IN_14,
                RootShape.deepenTo(4, true, r.formed(), r.shapeName(), y0, r.yCoord),
                "the deepen job is offered again");
        } finally {
            clearCells(r);
        }
    }

    /**
     * A tap of the well breaks while the deepen job runs (a chamber at Y0 64, an in-well form at Y0 28): the job lays
     * its whole new part, but the form never stands, so it stops as incomplete. 修复 then queues the root's repair as
     * the form, whose whole plan puts the tap back with everything else; laid, the root forms as that form. Starting
     * the deepen job over could never have: its plan holds no cell of the well.
     */
    @Test
    void aStuckDeepenJobsRepairPutsTheWellRightAndTheRootForms() throws ReflectiveOperationException {
        if (ModuleSpecs.get(RootShape.KEY) == null) ModuleSpecs.register(com.fluxecho.city.CityModule.root());
        for (int y0 : new int[] { 64, 28 }) {
            FakeWorld w = world();
            Nexus n = nexus(w, y0, 4);
            int[] c = n.centre();
            int fx = n.front().offsetX, fz = n.front().offsetZ;
            RootShape.Well well = RootShape.forBase(y0);
            RootShape.Deep d = RootShape.deepFor(y0, well);
            lay(w, BuildPlan.root(SiteFamily.ROOT_SITE, c[0], y0, c[2], fx, fz), false);
            int[] ctrl = well.controller(SiteFamily.ROOT_SITE, c[0], y0, c[2], fx, fz);
            Root r = root(w, n, ctrl);
            try {
                recheck(r);
                assertTrue(r.formed(), d.name + ": the well stands");
                BuildPlan.Plan grow = BuildPlan.deepen(SiteFamily.ROOT_SITE, c[0], y0, c[2], fx, fz, d);
                int[] tap = r.unitCells(0)
                    .get(0);
                lay(w, grow, false);
                w.set(tap[0], tap[1], tap[2], null, 0);
                for (BuildPlan.Step st : grow.steps)
                    assertFalse(st.at(tap[0], tap[1], tap[2]), "no step of the well's");
                recheck(r);
                assertFalse(r.formed(), d.name + ": the form never stands");

                BuildJob stuck = BuildJob.deepen(com.fluxecho.city.CityModule.root(), d.name);
                stuck.state = com.fluxecho.logic.BuildState.State.PAUSED;
                stuck.pause = com.fluxecho.logic.BuildState.Pause.INCOMPLETE;
                stuck.started = true;
                stuck.touched = true;
                Campus campus = campus(n, stuck, ctrl);
                assertEquals("fluxecho.build.repair_queued", campus.repairNow());
                NBTTagCompound tile = new NBTTagCompound();
                campus.writeNBT(tile);
                net.minecraft.nbt.NBTTagList q = tile.getCompoundTag("Campus")
                    .getTagList("Queue", 10);
                assertEquals(1, q.tagCount());
                BuildJob repair = BuildJob.read(q.getCompoundTagAt(0));
                assertEquals(d.name, repair.form().root);
                assertTrue(repair.form().whole);
                lay(w, plan(repair, n), false);
                assertFalse(w.isAirBlock(tap[0], tap[1], tap[2]), d.name + ": the tap is back");
                recheck(r);
                assertTrue(r.formed(), d.name + ": " + r.firstWrong());
                assertEquals(d.name, r.shapeName());
            } finally {
                clearCells(r);
            }
        }
    }

    /** The nexus's campus, active, with the job and the root recorded on its site at the controller cell given. */
    private static Campus campus(TileNexus n, BuildJob job, int[] root) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("St", "active");
        t.setInteger("Pv", BuildJob.GRADE_V);
        if (job != null) t.setTag("Job", job.write());
        t.setTag("Queue", new net.minecraft.nbt.NBTTagList());
        NBTTagCompound s = new NBTTagCompound();
        s.setInteger("S", SiteFamily.ROOT_SITE);
        s.setIntArray("P", new int[] { root[0], root[1], root[2] });
        s.setString("K", RootShape.KEY);
        net.minecraft.nbt.NBTTagList sites = new net.minecraft.nbt.NBTTagList();
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
            new com.fluxecho.logic.CampusPlan(com.fluxecho.logic.Mix.seed(n.xCoord, n.yCoord, n.zCoord)),
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

    /** The free item id the test's sample item is registered under. */
    private static final int SAMPLE_ID = 4000;

    /**
     * An item of the test's own, registered once under {@link #SAMPLE_ID} so a stack of it saves and loads by its id
     * (the registry's own {@code addObject} refuses outside a mod's loading, so its raw adder is called); shared with
     * any test of this package that saves samples.
     */
    static synchronized Item sampleItem() {
        Item there = Item.getItemById(SAMPLE_ID);
        if (there != null) return there;
        Item item = new Item().setUnlocalizedName("fluxecho_test_sample");
        try {
            Method raw = Item.itemRegistry.getClass()
                .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
            raw.setAccessible(true);
            raw.invoke(Item.itemRegistry, SAMPLE_ID, "fluxecho_test:sample", item);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        assertSame(item, Item.getItemById(SAMPLE_ID));
        return item;
    }

    /**
     * Every root tile keeps 16 samples (0.16.0), whatever it stands as, and a well saved by 0.15.0 with its eight
     * loads into the first eight, each sample in the slot it was saved in (its tap's), the rest empty; its save keeps
     * sixteen from then on, with those samples.
     */
    @Test
    void everyRootKeepsSixteenSamplesAndAnEightSlotSaveLoads() {
        try {
            TileEntity.addMapping(TileRoot.class, "fluxecho:root");
        } catch (IllegalArgumentException alreadyThere) {
            // another test registered it
        }
        TileRoot fresh = new TileRoot();
        assertEquals(
            RootShape.SLOTS,
            fresh.samples()
                .getSlots());
        assertEquals(RootShape.WELL, fresh.shapeName());
        assertEquals(8, fresh.shownSlots());
        assertEquals(8, fresh.unitCount());
        assertEquals("module.root", fresh.nameKey());
        assertEquals(TileRoot.COLOR, fresh.moduleColor());
        NBTTagCompound saved = new NBTTagCompound();
        fresh.writeToNBT(saved);
        // a 0.15.0 well: eight slots in its save, samples in the first tap's slot and the last one's
        Item item = sampleItem();
        ItemStackHandler old = new ItemStackHandler(8);
        old.setStackInSlot(0, new ItemStack(item, 1, 3));
        old.setStackInSlot(7, new ItemStack(item, 1, 11));
        saved.setTag("Samples", old.serializeNBT());
        assertEquals(
            8,
            saved.getCompoundTag("Samples")
                .getInteger("Size"));
        TileRoot back = new TileRoot();
        back.readFromNBT(saved);
        assertEquals(
            RootShape.SLOTS,
            back.samples()
                .getSlots(),
            "the handler keeps its sixteen");
        for (int i = 0; i < RootShape.SLOTS; i++) {
            ItemStack at = back.sample(i);
            if (i == 0 || i == 7) {
                assertNotNull(at, "the tap's sample in its slot " + i);
                assertSame(item, at.getItem());
                assertEquals(i == 0 ? 3 : 11, at.getItemDamage(), "slot " + i);
            } else assertNull(at, "slot " + i);
        }
        NBTTagCompound again = new NBTTagCompound();
        back.writeToNBT(again);
        assertEquals(
            RootShape.SLOTS,
            again.getCompoundTag("Samples")
                .getInteger("Size"));
        TileRoot twice = new TileRoot();
        twice.readFromNBT(again);
        assertEquals(
            3,
            twice.sample(0)
                .getItemDamage(),
            "kept in a sixteen-slot save");
        assertEquals(
            11,
            twice.sample(7)
                .getItemDamage());
        for (int i = 8; i < RootShape.SLOTS; i++) assertNull(twice.sample(i));
        TileRoot deep = new TileRoot();
        deep.expectShape(RootShape.ROOT_7);
        assertEquals(RootShape.ROOT_7, deep.shapeName());
        assertEquals(RootShape.SLOTS, deep.shownSlots());
        assertNotNull(deep.deep());
        assertEquals(7, deep.base());
    }
}
