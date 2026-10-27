package com.fluxecho.save;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;

import com.fluxecho.campus.BlockDeck;
import com.fluxecho.campus.BlockFitting;
import com.fluxecho.campus.BuildJob;
import com.fluxecho.campus.CampusModule;
import com.fluxecho.campus.FakeWorld;
import com.fluxecho.campus.PartBlocks;
import com.fluxecho.city.TileCityModule;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.frame.FrameModule;
import com.fluxecho.library.TileLibrary;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.nexus.TileNexus;

import cpw.mods.fml.common.Loader;

/**
 * A world for the golden saves: the test {@link FakeWorld} with a provider, the cells the campus builder's real plans
 * put down, each remembered by its part code ({@code logic.Parts}), so the world can be written as the part of every
 * cell and read back, on another day and by another version of the code, into a world the buildings form in again.
 * It also holds what every test that checks a structure outside the game needs (as {@code PodReleaseTest} and the
 * other form tests do it): StructureLib loaded with a bare FML loader standing in, the frame, deck and fitting blocks
 * made when the game has not made them, the structure definitions made again from them, the server's check run once,
 * and the item the golden samples are made of.
 * <p>
 * A cell is written by its part, not by its block: the part codes are frozen IDs (only appended to), and a world read
 * back sets whatever block the code is now. Cells of vanilla parts (grass, dirt, water) are kept by their code too,
 * though the test has no block for them.
 */
public final class GoldenWorld {

    /** The free item id the golden samples are made of, and its name (the same as the deep-root tests use). */
    public static final int SAMPLE_ID = 4000;

    public final FakeWorld world;
    /** The part code of every cell the plans laid, by {@link BuildJob#pos}. */
    public final Map<Long, Integer> parts = new TreeMap<>();

    private static BlockFrame oldFrame;
    private static BlockDeck oldDeck;
    private static BlockFitting oldFitting;
    private static int depth;

    public GoldenWorld() {
        world = FakeWorld.create();
        try {
            Field provider = World.class.getDeclaredField("provider");
            provider.setAccessible(true);
            provider.set(world, new WorldProviderSurface());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- the test's blocks and StructureLib

    /** Loads StructureLib and makes the blocks and the structure definitions (once; pair with {@link #tearDown}). */
    public static synchronized void setUp() {
        if (depth++ > 0) return;
        try {
            structureLib();
            oldFrame = FrameModule.frame;
            oldDeck = CampusModule.deck;
            oldFitting = CampusModule.fitting;
            if (FrameModule.frame == null) FrameModule.frame = new BlockFrame();
            if (CampusModule.deck == null) CampusModule.deck = new BlockDeck();
            if (CampusModule.fitting == null) CampusModule.fitting = new BlockFitting();
            forgetDefinitions();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot set the structure check up", e);
        }
    }

    /** Puts the blocks back as they were and forgets the definitions made of the test's blocks. */
    public static synchronized void tearDown() {
        if (--depth > 0) return;
        FrameModule.frame = oldFrame;
        CampusModule.deck = oldDeck;
        CampusModule.fitting = oldFitting;
        try {
            forgetDefinitions();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The structure definitions are made once from the blocks there are: made again with the test's. */
    private static void forgetDefinitions() throws ReflectiveOperationException {
        for (String name : new String[] { "DEFINITIONS", "BUILD_DEFINITIONS" }) {
            Field f = TileCityModule.class.getDeclaredField(name);
            f.setAccessible(true);
            ((Map<?, ?>) f.get(null)).clear();
        }
        for (Class<?> k : new Class<?>[] { TileNexus.class, TileLibrary.class })
            for (String name : new String[] { "definition", "buildDefinition" }) {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                f.set(null, null);
            }
    }

    /** Loads StructureLib's mod class once, with a bare FML loader standing in while it does. */
    private static void structureLib() throws ReflectiveOperationException {
        Field instance = Loader.class.getDeclaredField("instance");
        instance.setAccessible(true);
        boolean none = instance.get(null) == null;
        if (none) instance.set(null, bare(Loader.class));
        try {
            Class.forName("com.gtnewhorizon.structurelib.StructureLib", true, GoldenWorld.class.getClassLoader());
        } finally {
            if (none) instance.set(null, null);
        }
    }

    private static Object bare(Class<?> k) throws ReflectiveOperationException {
        Class<?> u = Class.forName("sun.misc.Unsafe");
        Field f = u.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Method alloc = u.getMethod("allocateInstance", Class.class);
        return alloc.invoke(f.get(null), k);
    }

    // ---- laying plans and reading the world back

    /**
     * Sets every cell of a plan as the builder lands it: a dig or a drain leaves the cell as it is (the world is
     * empty there), an AIR step empties it, every other step puts its part.
     */
    public void lay(BuildPlan.Plan p) {
        for (BuildPlan.Step s : p.steps) {
            if (BuildPlan.digs(s.kind)) continue;
            if (s.kind == BuildPlan.AIR) set(s.x, s.y, s.z, -1);
            else set(s.x, s.y, s.z, s.part);
        }
    }

    /** Puts a part in a cell (-1 empties it): its block, when the test has one, and its code. */
    public void set(int x, int y, int z, int part) {
        long at = BuildJob.pos(x, y, z);
        if (part < 0) {
            parts.remove(at);
            world.set(x, y, z, null, 0);
            return;
        }
        parts.put(at, part);
        Block b = PartBlocks.block(part);
        world.set(x, y, z, b, b == null ? 0 : PartBlocks.meta(part));
    }

    /** The part code laid in a cell, or -1. */
    public int part(int x, int y, int z) {
        Integer p = parts.get(BuildJob.pos(x, y, z));
        return p == null ? -1 : p;
    }

    /** The world as NBT: {@code X}, {@code Y}, {@code Z} and {@code P} (the part), one entry a cell, in cell order. */
    public NBTTagCompound write() {
        int n = parts.size(), i = 0;
        int[] xs = new int[n], ys = new int[n], zs = new int[n], ps = new int[n];
        for (Map.Entry<Long, Integer> e : parts.entrySet()) {
            int[] c = BuildJob.unpack(e.getKey());
            xs[i] = c[0];
            ys[i] = c[1];
            zs[i] = c[2];
            ps[i++] = e.getValue();
        }
        NBTTagCompound t = new NBTTagCompound();
        t.setIntArray("X", xs);
        t.setIntArray("Y", ys);
        t.setIntArray("Z", zs);
        t.setIntArray("P", ps);
        return t;
    }

    /** A world of the cells written by {@link #write}, each set to the block its part is now. */
    public static GoldenWorld read(NBTTagCompound t) {
        GoldenWorld g = new GoldenWorld();
        int[] xs = t.getIntArray("X"), ys = t.getIntArray("Y"), zs = t.getIntArray("Z"), ps = t.getIntArray("P");
        for (int i = 0; i < ps.length; i++) g.set(xs[i], ys[i], zs[i], ps[i]);
        return g;
    }

    // ---- tiles

    /** Puts a structure tile at its controller cell {x, y, z, facing}, owned by the owner, in this world. */
    public <T extends TileMultiblock> T place(T t, int[] at, java.util.UUID owner) {
        world.put(t, at[0], at[1], at[2]);
        t.place(owner, owner == null ? null : "golden", at[3]);
        return t;
    }

    /** Runs the server's structure check once, as its timer or a frame change does. */
    public static void recheck(TileMultiblock t) {
        call(t, "recheckNow");
    }

    /** Takes the formed cells the check set out of the server's table again. */
    public static void clearCells(TileMultiblock t) {
        call(t, "clearCells");
    }

    private static void call(TileMultiblock t, String name) {
        try {
            Method m = TileMultiblock.class.getDeclaredMethod(name);
            m.setAccessible(true);
            m.invoke(t);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // what the check itself threw, as it threw it
            if (e.getCause() instanceof RuntimeException r) throw r;
            if (e.getCause() instanceof Error r) throw r;
            throw new IllegalStateException(name + " failed", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot call " + name, e);
        }
    }

    /**
     * Makes a tile class save under a tile entity id: the real class by Forge's own mapping (when nothing has mapped
     * it yet), and a test's subclass of it (a nexus that knows its teams' research, a pod that knows its nexus) under
     * the same id, so its tag reads back as the real tile's. For a subclass only the class-to-name half of Forge's
     * table is touched; the name still names the real class.
     */
    @SuppressWarnings("unchecked")
    public static void map(Class<? extends TileEntity> real, Class<? extends TileEntity> sub, String id) {
        try {
            TileEntity.addMapping(real, id);
        } catch (IllegalArgumentException alreadyThere) {
            // another test mapped it
        }
        if (sub == null || sub == real) return;
        try {
            for (Field f : TileEntity.class.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) || !Map.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Map<Object, Object> m = (Map<Object, Object>) f.get(null);
                if (m.containsKey(real)) m.put(sub, m.get(real));
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot map " + sub + " to " + id, e);
        }
    }

    /**
     * The item the golden samples are made of, registered once under {@link #SAMPLE_ID} so a stack of it saves and
     * loads by its id (the registry's own adder refuses outside a mod's loading, so its raw one is called).
     */
    public static synchronized Item sampleItem() {
        Item there = Item.getItemById(SAMPLE_ID);
        if (there != null) return there;
        Item item = new Item().setUnlocalizedName("fluxecho_test_sample");
        try {
            Method raw = Item.itemRegistry.getClass()
                .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
            raw.setAccessible(true);
            raw.invoke(Item.itemRegistry, SAMPLE_ID, "fluxecho_test:sample", item);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return item;
    }
}
