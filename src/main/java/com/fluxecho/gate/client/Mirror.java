package com.fluxecho.gate.client;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.fluxecho.FluxEcho;
import com.fluxecho.logic.GateGeometry;
import com.fluxecho.logic.MirrorSection;

/**
 * What lies behind one gate, as the server sent it: the sections around its partner, in the partner's own coordinates.
 * Minecraft's block renderer reads it like a world, so each section is built into display lists the same way a chunk
 * is; drawing them moved through the gate is {@link GateClient}'s job. Blocks that need their tile entity to be drawn
 * come out plain (the prototype sends no tile entities).
 */
final class Mirror implements IBlockAccess {

    final int viewerDim, sourceDim;
    final GateGeometry.Gate viewer, source;
    final GateGeometry.Box box;
    private final Map<Long, Part> parts = new HashMap<>();

    private static final class Part {

        final int sx, sy, sz;
        MirrorSection data;
        int lists = -1;
        boolean dirty = true;
        final boolean[] has = new boolean[2];

        Part(int sx, int sy, int sz) {
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
        }
    }

    Mirror(int viewerDim, GateGeometry.Gate viewer, int sourceDim, GateGeometry.Gate source, GateGeometry.Box box) {
        this.viewerDim = viewerDim;
        this.viewer = viewer;
        this.sourceDim = sourceDim;
        this.source = source;
        this.box = box;
    }

    boolean sameAs(int sourceDim, GateGeometry.Gate source) {
        return this.sourceDim == sourceDim && this.source.x == source.x
            && this.source.y == source.y
            && this.source.z == source.z
            && this.source.facing == source.facing;
    }

    boolean hasAny() {
        for (Part p : parts.values()) if (p.data != null) return true;
        return false;
    }

    void put(long key, MirrorSection s) {
        if (s == null) return;
        int sx = MirrorSection.keyX(key), sy = MirrorSection.keyY(key), sz = MirrorSection.keyZ(key);
        parts.computeIfAbsent(key, k -> new Part(sx, sy, sz)).data = s;
        // faces and shading at the edges depend on the neighbours
        for (int[] d : new int[][] { { 0, 0, 0 }, { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 },
            { 0, 0, -1 } }) {
            Part n = parts.get(MirrorSection.key(sx + d[0], sy + d[1], sz + d[2]));
            if (n != null) n.dirty = true;
        }
    }

    // ---- as a world, for the block renderer

    private MirrorSection at(int x, int y, int z) {
        if (y < 0 || y > 255) return null;
        Part p = parts.get(MirrorSection.key(x >> 4, y >> 4, z >> 4));
        return p == null ? null : p.data;
    }

    @Override
    public Block getBlock(int x, int y, int z) {
        MirrorSection s = at(x, y, z);
        if (s == null) return Blocks.air;
        Block b = Block.getBlockById(s.ids[MirrorSection.index(x, y, z)]);
        return b == null ? Blocks.air : b;
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        MirrorSection s = at(x, y, z);
        return s == null ? 0 : s.meta[MirrorSection.index(x, y, z)];
    }

    @Override
    public TileEntity getTileEntity(int x, int y, int z) {
        return null;
    }

    private int light(int x, int y, int z) {
        MirrorSection s = at(x, y, z);
        return s == null ? 0xF0 : s.light[MirrorSection.index(x, y, z)] & 255;
    }

    @Override
    public int getLightBrightnessForSkyBlocks(int x, int y, int z, int minBlockLight) {
        int l = light(x, y, z);
        if (getBlock(x, y, z).getUseNeighborBrightness()) {
            // slabs, stairs, farmland: as bright as their brightest open neighbour, like a chunk cache does
            for (int[] d : new int[][] { { 0, 1, 0 }, { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 } }) {
                int n = light(x + d[0], y + d[1], z + d[2]);
                l = Math.max(l & 0xF0, n & 0xF0) | Math.max(l & 15, n & 15);
            }
        }
        int sky = l >> 4, block = Math.max(l & 15, minBlockLight);
        return sky << 20 | block << 4;
    }

    @Override
    public int isBlockProvidingPowerTo(int x, int y, int z, int side) {
        return 0;
    }

    @Override
    public boolean isAirBlock(int x, int y, int z) {
        return getBlock(x, y, z).isAir(this, x, y, z);
    }

    @Override
    public BiomeGenBase getBiomeGenForCoords(int x, int z) {
        for (int sy = box.minY >> 4; sy <= box.maxY >> 4; sy++) {
            Part p = parts.get(MirrorSection.key(x >> 4, sy, z >> 4));
            if (p != null && p.data != null) {
                BiomeGenBase b = BiomeGenBase.getBiome(p.data.biomes[(z & 15) << 4 | (x & 15)] & 255);
                return b == null ? BiomeGenBase.plains : b;
            }
        }
        return BiomeGenBase.plains;
    }

    @Override
    public int getHeight() {
        return 256;
    }

    @Override
    public boolean extendedLevelsInChunkCache() {
        return false;
    }

    @Override
    public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean otherwise) {
        if (y < 0 || y > 255) return otherwise;
        return getBlock(x, y, z).isSideSolid(this, x, y, z, side);
    }

    // ---- meshes

    private static Field renderPass;

    static {
        try {
            renderPass = ForgeHooksClient.class.getDeclaredField("worldRenderPass");
            renderPass.setAccessible(true);
        } catch (Throwable ignored) {}
    }

    private static void setRenderPass(int pass) {
        try {
            if (renderPass != null) renderPass.setInt(null, pass);
        } catch (Throwable ignored) {}
    }

    /** Builds up to {@code budget} changed sections; returns how many it built. */
    int build(int budget) {
        int built = 0;
        for (Part p : parts.values()) {
            if (built >= budget) break;
            if (!p.dirty || p.data == null) continue;
            build(p);
            built++;
        }
        return built;
    }

    private void build(Part p) {
        p.dirty = false;
        if (p.lists < 0) p.lists = GLAllocation.generateDisplayLists(2);
        RenderBlocks rb = new RenderBlocks(this);
        Tessellator t = Tessellator.instance;
        int ox = p.sx * 16, oy = p.sy * 16, oz = p.sz * 16;
        for (int pass = 0; pass < 2; pass++) {
            boolean any = false;
            GL11.glNewList(p.lists + pass, GL11.GL_COMPILE);
            setRenderPass(pass);
            t.startDrawingQuads();
            t.setTranslation(-ox, -oy, -oz);
            for (int y = oy; y < oy + 16; y++) for (int z = oz; z < oz + 16; z++) for (int x = ox; x < ox + 16; x++) {
                Block b = getBlock(x, y, z);
                if (b.getMaterial() == Material.air || !b.canRenderInPass(pass)) continue;
                try {
                    any |= rb.renderBlockByRenderType(b, x, y, z);
                } catch (Throwable e) {
                    // a block that cannot be drawn outside a real world is left out
                }
            }
            try {
                t.draw();
            } catch (Throwable ignored) {}
            t.setTranslation(0, 0, 0);
            setRenderPass(-1);
            GL11.glEndList();
            p.has[pass] = any;
        }
    }

    /**
     * Calls the sections' lists of one pass, each moved by its offset from the source gate: the caller has turned and
     * moved the view to the viewer gate already.
     */
    void draw(int pass) {
        for (Part p : parts.values()) {
            if (p.lists < 0 || !p.has[pass]) continue;
            GL11.glPushMatrix();
            GL11.glTranslated(p.sx * 16 - source.cx(), p.sy * 16 - source.y, p.sz * 16 - source.cz());
            GL11.glCallList(p.lists + pass);
            GL11.glPopMatrix();
        }
    }

    void free() {
        for (Part p : parts.values()) {
            if (p.lists >= 0) GLAllocation.deleteDisplayLists(p.lists);
            p.lists = -1;
        }
        parts.clear();
    }

    /**
     * Writes the sections into the client's new world where it has no chunk yet, so the far side is there before the
     * server's chunks arrive; the server's chunks replace them when they come. Returns the chunks it made, by
     * {@link GateClient#chunkKey}.
     */
    Map<Long, Chunk> fillInto(WorldClient w) {
        Set<Long> made = new HashSet<>(), skipped = new HashSet<>();
        Map<Long, Chunk> chunks = new HashMap<>();
        for (Part p : parts.values()) {
            if (p.data == null) continue;
            long ck = GateClient.chunkKey(p.sx, p.sz);
            if (skipped.contains(ck)) continue;
            Chunk c = chunks.get(ck);
            if (c == null) {
                if (!(w.getChunkFromChunkCoords(p.sx, p.sz) instanceof EmptyChunk)) {
                    skipped.add(ck);
                    continue;
                }
                w.doPreChunk(p.sx, p.sz, true);
                c = w.getChunkFromChunkCoords(p.sx, p.sz);
                if (c instanceof EmptyChunk) {
                    skipped.add(ck);
                    continue;
                }
                chunks.put(ck, c);
                made.add(ck);
                System.arraycopy(p.data.biomes, 0, c.getBiomeArray(), 0, MirrorSection.COLUMNS);
            }
            ExtendedBlockStorage[] storage = c.getBlockStorageArray();
            if (storage[p.sy] == null) storage[p.sy] = new ExtendedBlockStorage(p.sy << 4, !w.provider.hasNoSky);
            ExtendedBlockStorage s = storage[p.sy];
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                int i = MirrorSection.index(x, y, z);
                Block b = Block.getBlockById(p.data.ids[i]);
                s.func_150818_a(x, y, z, b == null ? Blocks.air : b);
                s.setExtBlockMetadata(x, y, z, p.data.meta[i]);
                s.setExtSkylightValue(x, y, z, (p.data.light[i] >> 4) & 15);
                s.setExtBlocklightValue(x, y, z, p.data.light[i] & 15);
            }
        }
        for (Chunk c : chunks.values()) {
            c.generateHeightMap();
            c.isTerrainPopulated = true;
            c.isLightPopulated = true;
        }
        for (Part p : parts.values()) {
            if (made.contains(GateClient.chunkKey(p.sx, p.sz))) w.markBlockRangeForRenderUpdate(
                p.sx * 16,
                p.sy * 16,
                p.sz * 16,
                p.sx * 16 + 15,
                p.sy * 16 + 15,
                p.sz * 16 + 15);
        }
        if (!made.isEmpty()) FluxEcho.LOG.debug("Light gate filled {} chunks in ahead of the server", made.size());
        return chunks;
    }
}
