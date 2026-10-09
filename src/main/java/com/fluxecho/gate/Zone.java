package com.fluxecho.gate;

import java.util.Arrays;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.biome.WorldChunkManagerHell;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.WorldEvent;

import com.fluxecho.logic.FoldedZone;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** The folded zone in a running world: its chunks come empty, and nothing spawns in it. */
public final class Zone {

    Zone() {}

    /** A new chunk of the zone: air, lit by the sky, populated already. */
    public static Chunk emptyChunk(World w, int x, int z) {
        Chunk c = new Chunk(w, x, z);
        Arrays.fill(c.getBiomeArray(), (byte) biome(w).biomeID);
        c.generateSkylightMap();
        c.isTerrainPopulated = true;
        c.isLightPopulated = true;
        return c;
    }

    /** The dimension's own biome when it has only one (the Nether, most planets), plains otherwise. */
    static BiomeGenBase biome(World w) {
        WorldChunkManager m = w.getWorldChunkManager();
        BiomeGenBase b = m instanceof WorldChunkManagerHell ? m.getBiomeGenAt(0, 0) : null;
        return b == null ? BiomeGenBase.plains : b;
    }

    @SubscribeEvent
    public void onPotentialSpawns(WorldEvent.PotentialSpawns e) {
        if (FoldedZone.contains(e.x, e.z)) e.setCanceled(true);
    }
}
