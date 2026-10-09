package com.fluxecho.gate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.fluxecho.logic.GateGeometry;
import com.fluxecho.logic.GatePins;
import com.fluxecho.logic.RoomPlan;
import com.fluxecho.logic.RoomPlan.Cell;

/**
 * Builds a room into the world. The blocks go straight into the chunks (a big room is tens of thousands of them,
 * which one by one would relight the world tens of thousands of times), then the room is lit once: its lights shine,
 * and sky comes in through glass.
 */
final class Rooms {

    private Rooms() {}

    /**
     * Builds {@code plan} with its floor's middle at {@code (cx, fy, cz)}, in {@code biome}. Air is left as it is,
     * so building a room again over an old one keeps what is in it; only the shell is put back.
     */
    static void build(WorldServer w, RoomPlan plan, int cx, int fy, int cz, BiomeGenBase biome) {
        GateGeometry.Box b = plan.box(cx, fy, cz);
        Set<Long> room = new HashSet<>();
        GatePins.addBox(room, b);
        // the chunks round the room too, so the light reaches the walls
        Set<Long> loadedHere = new HashSet<>();
        for (int x = (b.minX - 17) >> 4; x <= (b.maxX + 17) >> 4; x++)
            for (int z = (b.minZ - 17) >> 4; z <= (b.maxZ + 17) >> 4; z++) {
                if (!w.getChunkProvider()
                    .chunkExists(x, z)) loadedHere.add(GatePins.key(x, z));
                w.getChunkFromChunkCoords(x, z);
            }
        boolean watched = false;
        for (long k : room) watched |= w.getPlayerManager()
            .func_152621_a(GatePins.keyX(k), GatePins.keyZ(k));

        List<int[]> lights = new ArrayList<>();
        for (int x = b.minX; x <= b.maxX; x++) for (int z = b.minZ; z <= b.maxZ; z++) {
            Chunk c = w.getChunkFromChunkCoords(x >> 4, z >> 4);
            ExtendedBlockStorage[] st = c.getBlockStorageArray();
            for (int y = b.minY; y <= b.maxY; y++) {
                Cell cell = plan.cell(x - cx, y - fy, z - cz);
                if (!cell.solid()) continue;
                Block block = cell == Cell.GLASS ? GateModule.glass : GateModule.shell;
                int meta = cell == Cell.GLASS ? 0 : BlockInteriorShell.meta(cell);
                if (c.getBlock(x & 15, y, z & 15) != Blocks.air) {
                    // something was there (an old room's shell, or what someone left): the long way round
                    w.setBlock(x, y, z, block, meta, 2);
                    continue;
                }
                ExtendedBlockStorage s = st[y >> 4];
                if (s == null) s = st[y >> 4] = new ExtendedBlockStorage(y >> 4 << 4, !w.provider.hasNoSky);
                s.func_150818_a(x & 15, y & 15, z & 15, block);
                s.setExtBlockMetadata(x & 15, y & 15, z & 15, meta);
                if (cell.light() > 0) lights.add(new int[] { x, y, z });
                if (watched) w.markBlockForUpdate(x, y, z);
            }
        }
        for (long k : room) {
            Chunk c = w.getChunkFromChunkCoords(GatePins.keyX(k), GatePins.keyZ(k));
            Arrays.fill(c.getBiomeArray(), (byte) biome.biomeID);
            c.generateSkylightMap();
            c.isTerrainPopulated = true;
            c.isLightPopulated = true;
            c.setChunkModified();
        }
        for (int[] l : lights) w.updateLightByType(EnumSkyBlock.Block, l[0], l[1], l[2]);
        if (plan.t.glass && !w.provider.hasNoSky) skyFromGlass(w, plan, cx, fy, cz);
        for (long k : loadedHere) {
            int x = GatePins.keyX(k), z = GatePins.keyZ(k);
            if (!room.contains(k) && !w.getPlayerManager()
                .func_152621_a(x, z)) w.theChunkProviderServer.unloadChunksIfNotNearSpawn(x, z);
        }
    }

    /**
     * The sky shines straight down through the glass already; this spreads it sideways under the ribs and the edge of
     * the roof, from every column of air beside a column under glass.
     */
    private static void skyFromGlass(WorldServer w, RoomPlan plan, int cx, int fy, int cz) {
        int nx = plan.maxX - plan.minX + 1, nz = plan.maxZ - plan.minZ + 1;
        boolean[] sky = new boolean[nx * nz];
        for (int x = plan.minX; x <= plan.maxX; x++) for (int z = plan.minZ; z <= plan.maxZ; z++) {
            int y = plan.maxY;
            while (y > 0 && !plan.inside(x, y, z)) y--;
            sky[(x - plan.minX) * nz + z - plan.minZ] = y > 0 && plan.cell(x, y + 1, z) == Cell.GLASS;
        }
        for (int x = plan.minX + 1; x < plan.maxX; x++) for (int z = plan.minZ + 1; z < plan.maxZ; z++) {
            int i = (x - plan.minX) * nz + z - plan.minZ;
            if (sky[i] || !(sky[i + nz] || sky[i - nz] || sky[i + 1] || sky[i - 1])) continue;
            for (int y = 1; y <= plan.maxY; y++)
                if (plan.inside(x, y, z)) w.updateLightByType(EnumSkyBlock.Sky, cx + x, fy + y, cz + z);
        }
    }
}
