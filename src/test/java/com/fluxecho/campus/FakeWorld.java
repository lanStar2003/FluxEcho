package com.fluxecho.campus;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

/**
 * A server world for tile tests: no constructor of {@link World} runs (it would build a chunk provider, biomes and a
 * spawn point), the object is allocated bare. It answers what the campus tiles ask of a world: which chunks are loaded,
 * the tile entity at a cell, the time; marking a tile dirty does nothing. A chunk can be unloaded and loaded again with
 * a new tile in it, as the game does it: the old tile is not invalidated, only no longer in the world.
 * <p>
 * A test may set blocks ({@link #set}): a cell given none holds no block ({@code getBlock} is null) and reads as air.
 * With {@link #onlySetCellsExist} every other cell reads as not loaded, so a test of a few cells of a large plan sees
 * only those.
 */
public class FakeWorld extends World {

    private Map<Long, TileEntity> tiles;
    private Set<Long> unloaded;
    private Map<Long, Block> blocks;
    private Map<Long, Integer> metas;
    private boolean onlySet;
    long time;

    /** Never called: {@link #create} allocates the world without running a constructor. */
    private FakeWorld() {
        super(null, null, (net.minecraft.world.WorldSettings) null, null, null);
    }

    /** A new empty world, every chunk loaded, at tick 0. */
    public static FakeWorld create() {
        try {
            Class<?> u = Class.forName("sun.misc.Unsafe");
            Field f = u.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            Method alloc = u.getMethod("allocateInstance", Class.class);
            FakeWorld w = (FakeWorld) alloc.invoke(f.get(null), FakeWorld.class);
            w.tiles = new HashMap<>();
            w.unloaded = new HashSet<>();
            w.blocks = new HashMap<>();
            w.metas = new HashMap<>();
            w.isRemote = false;
            return w;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot make a bare world", e);
        }
    }

    private static long cell(int x, int y, int z) {
        return BuildJob.pos(x, y, z);
    }

    private static long chunk(int x, int z) {
        return ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL);
    }

    /** Puts a tile at the cell (replacing what stood there) and gives it this world. */
    public <T extends TileEntity> T put(T te, int x, int y, int z) {
        te.xCoord = x;
        te.yCoord = y;
        te.zCoord = z;
        te.setWorldObj(this);
        tiles.put(cell(x, y, z), te);
        return te;
    }

    /** Unloads the chunk of the column: its tiles leave the world, uninvalidated. */
    public void unload(int x, int z) {
        unloaded.add(chunk(x, z));
        tiles.keySet()
            .removeIf(p -> {
                int[] c = BuildJob.unpack(p);
                return chunk(c[0], c[2]) == chunk(x, z);
            });
    }

    /** Loads the chunk of the column again (empty: put its new tiles in). */
    public void load(int x, int z) {
        unloaded.remove(chunk(x, z));
    }

    /** Sets the block (and its meta) at the cell; null takes it away. */
    public FakeWorld set(int x, int y, int z, Block b, int meta) {
        if (b == null) {
            blocks.remove(cell(x, y, z));
            metas.remove(cell(x, y, z));
        } else {
            blocks.put(cell(x, y, z), b);
            metas.put(cell(x, y, z), meta);
        }
        return this;
    }

    /** From now on only the cells given a block exist: every other reads as an unloaded one. */
    public FakeWorld onlySetCellsExist() {
        onlySet = true;
        return this;
    }

    @Override
    public boolean blockExists(int x, int y, int z) {
        return y >= 0 && y < 256 && !unloaded.contains(chunk(x, z)) && (!onlySet || blocks.containsKey(cell(x, y, z)));
    }

    @Override
    public TileEntity getTileEntity(int x, int y, int z) {
        return blockExists(x, y, z) ? tiles.get(cell(x, y, z)) : null;
    }

    @Override
    public long getTotalWorldTime() {
        return time;
    }

    @Override
    public Block getBlock(int x, int y, int z) {
        return blocks.get(cell(x, y, z));
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        Integer m = metas.get(cell(x, y, z));
        return m == null ? 0 : m;
    }

    @Override
    public boolean isAirBlock(int x, int y, int z) {
        Block b = getBlock(x, y, z);
        return b == null || b.isAir(this, x, y, z);
    }

    @Override
    public void markTileEntityChunkModified(int x, int y, int z, TileEntity te) {}

    @Override
    public void func_147453_f(int x, int y, int z, Block b) {}

    @Override
    public void markBlockForUpdate(int x, int y, int z) {}

    @Override
    protected IChunkProvider createChunkProvider() {
        return null;
    }

    @Override
    protected int func_152379_p() {
        return 0;
    }

    @Override
    public Entity getEntityByID(int id) {
        return null;
    }
}
