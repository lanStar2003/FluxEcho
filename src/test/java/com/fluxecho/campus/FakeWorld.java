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
 */
public class FakeWorld extends World {

    private Map<Long, TileEntity> tiles;
    private Set<Long> unloaded;
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

    @Override
    public boolean blockExists(int x, int y, int z) {
        return y >= 0 && y < 256 && !unloaded.contains(chunk(x, z));
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
        return null;
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        return 0;
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
