package com.fluxecho.gate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.util.Vec3;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManagerHell;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

import com.fluxecho.logic.GateGeometry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The flux interior: empty space under a real sky, with the overworld's day and night but never rain, where each
 * light gate's plot floats far from the next. Nothing spawns, and no mod's world generation runs here: a new chunk
 * counts as populated already.
 */
public class InteriorProvider extends WorldProvider {

    @Override
    protected void registerWorldChunkManager() {
        worldChunkMgr = new WorldChunkManagerHell(BiomeGenBase.plains, 0.5f);
    }

    @Override
    public IChunkProvider createChunkGenerator() {
        return new Chunks(worldObj);
    }

    @Override
    public String getDimensionName() {
        return "Flux Interior";
    }

    @Override
    public boolean canRespawnHere() {
        return false;
    }

    @Override
    public boolean isSurfaceWorld() {
        return true;
    }

    @Override
    public boolean canCoordinateBeSpawn(int x, int z) {
        return true;
    }

    @Override
    public ChunkCoordinates getSpawnPoint() {
        GateGeometry.Gate g = GateGeometry.plotGate(0);
        return new ChunkCoordinates(g.x, g.y, 0);
    }

    @Override
    public int getAverageGroundLevel() {
        return GateGeometry.FLOOR_Y + 1;
    }

    @Override
    public boolean canDoRainSnowIce(Chunk chunk) {
        return false;
    }

    @Override
    public boolean canDoLightning(Chunk chunk) {
        return false;
    }

    @Override
    public void updateWeather() {
        worldObj.prevRainingStrength = worldObj.rainingStrength = 0f;
        worldObj.prevThunderingStrength = worldObj.thunderingStrength = 0f;
    }

    @Override
    public String getWelcomeMessage() {
        return null;
    }

    @Override
    public String getDepartMessage() {
        return null;
    }

    /** The overworld's sky, drawn a little towards the flux layer's teal. */
    @Override
    @SideOnly(Side.CLIENT)
    public Vec3 getSkyColor(Entity camera, float partialTicks) {
        return tint(super.getSkyColor(camera, partialTicks), 0.25);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Vec3 getFogColor(float celestialAngle, float partialTicks) {
        return tint(super.getFogColor(celestialAngle, partialTicks), 0.3);
    }

    private static Vec3 tint(Vec3 c, double by) {
        double k = 1 - by;
        return Vec3.createVectorHelper(c.xCoord * k + 0.16 * by, c.yCoord * k + 0.62 * by, c.zCoord * k + 0.78 * by);
    }

    /** Empty chunks of plains, marked populated so no world generation ever runs in them. */
    static final class Chunks implements IChunkProvider {

        private final World world;

        Chunks(World world) {
            this.world = world;
        }

        @Override
        public boolean chunkExists(int x, int z) {
            return true;
        }

        @Override
        public Chunk provideChunk(int x, int z) {
            Chunk c = new Chunk(world, x, z);
            Arrays.fill(c.getBiomeArray(), (byte) BiomeGenBase.plains.biomeID);
            c.generateSkylightMap();
            c.isTerrainPopulated = true;
            return c;
        }

        @Override
        public Chunk loadChunk(int x, int z) {
            return provideChunk(x, z);
        }

        @Override
        public void populate(IChunkProvider provider, int x, int z) {}

        @Override
        public boolean saveChunks(boolean all, IProgressUpdate progress) {
            return true;
        }

        @Override
        public boolean unloadQueuedChunks() {
            return false;
        }

        @Override
        public boolean canSave() {
            return true;
        }

        @Override
        public String makeString() {
            return "FluxInterior";
        }

        @Override
        public List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType type, int x, int y, int z) {
            return Collections.emptyList();
        }

        @Override
        public ChunkPosition func_147416_a(World w, String structure, int x, int y, int z) {
            return null;
        }

        @Override
        public int getLoadedChunkCount() {
            return 0;
        }

        @Override
        public void recreateStructures(int x, int z) {}

        @Override
        public void saveExtraData() {}
    }
}
