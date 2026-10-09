package com.fluxecho.gate.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraftforge.client.ForgeHooksClient;

import org.lwjgl.opengl.GL11;

import com.fluxecho.logic.GateGeometry;

/**
 * What one gate shows: the partner's side ({@code box}, in the partner's own coordinates), straight from the
 * client's world, which has it loaded (the server keeps it so). Each 16-block section is built into display lists the
 * way a chunk is, with the real tile entities, so machines are drawn as they are; blocks drawn by a special renderer
 * are drawn by it every frame. A section is built again when a block in it changes; drawing it moved through the gate
 * is {@link GateClient}'s job.
 */
final class FarView {

    /** Tile entities with a special renderer drawn per view, at most. */
    private static final int MAX_TILES = 256;

    final GateGeometry.Gate viewer, source;
    final GateGeometry.Box box;
    private final Map<Long, Part> parts = new HashMap<>();

    private static final class Part {

        final int sx, sy, sz;
        final double dist;
        int lists = -1;
        boolean dirty = true;
        final boolean[] has = new boolean[2];
        final List<TileEntity> tiles = new ArrayList<>();

        Part(int sx, int sy, int sz, GateGeometry.Gate source) {
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            double dx = sx * 16 + 8 - source.cx(), dy = sy * 16 + 8 - source.y, dz = sz * 16 + 8 - source.cz();
            dist = dx * dx + dy * dy + dz * dz;
        }
    }

    FarView(GateGeometry.Gate viewer, GateGeometry.Gate source, GateGeometry.Box box) {
        this.viewer = viewer;
        this.source = source;
        this.box = box;
        for (int sx = box.minX >> 4; sx <= box.maxX >> 4; sx++)
            for (int sy = Math.max(0, box.minY >> 4); sy <= Math.min(15, box.maxY >> 4); sy++)
                for (int sz = box.minZ >> 4; sz <= box.maxZ >> 4; sz++)
                    parts.put(key(sx, sy, sz), new Part(sx, sy, sz, source));
    }

    private static long key(int sx, int sy, int sz) {
        return ((long) (sx & 0x3FFFFFF) << 38) | ((long) (sz & 0x3FFFFFF) << 12) | (sy & 0xFFF);
    }

    boolean sameAs(GateGeometry.Gate viewer, GateGeometry.Gate source, GateGeometry.Box box) {
        return same(this.viewer, viewer) && same(this.source, source)
            && this.box.minX == box.minX
            && this.box.minY == box.minY
            && this.box.minZ == box.minZ
            && this.box.maxX == box.maxX
            && this.box.maxY == box.maxY
            && this.box.maxZ == box.maxZ;
    }

    private static boolean same(GateGeometry.Gate a, GateGeometry.Gate b) {
        return a.x == b.x && a.y == b.y && a.z == b.z && a.facing == b.facing;
    }

    boolean hasAny() {
        for (Part p : parts.values()) if (p.lists >= 0) return true;
        return false;
    }

    /** A block changed: its section is built again, and the neighbour it touches (faces and shading cross over). */
    void changed(int x, int y, int z) {
        if (x < box.minX - 1 || x > box.maxX + 1 || z < box.minZ - 1 || z > box.maxZ + 1) return;
        int sx = x >> 4, sy = y >> 4, sz = z >> 4;
        dirty(sx, sy, sz);
        if ((x & 15) == 0) dirty(sx - 1, sy, sz);
        if ((x & 15) == 15) dirty(sx + 1, sy, sz);
        if ((y & 15) == 0) dirty(sx, sy - 1, sz);
        if ((y & 15) == 15) dirty(sx, sy + 1, sz);
        if ((z & 15) == 0) dirty(sx, sy, sz - 1);
        if ((z & 15) == 15) dirty(sx, sy, sz + 1);
    }

    /** Blocks changed all over a range (a chunk arrived, light spread): every section touching it, and one more. */
    void changed(int x0, int y0, int z0, int x1, int y1, int z1) {
        if (x1 < box.minX - 1 || x0 > box.maxX + 1 || z1 < box.minZ - 1 || z0 > box.maxZ + 1) return;
        for (Part p : parts.values()) if (p.sx * 16 <= x1 + 1 && p.sx * 16 + 15 >= x0 - 1
            && p.sy * 16 <= y1 + 1
            && p.sy * 16 + 15 >= y0 - 1
            && p.sz * 16 <= z1 + 1
            && p.sz * 16 + 15 >= z0 - 1) p.dirty = true;
    }

    private void dirty(int sx, int sy, int sz) {
        Part p = parts.get(key(sx, sy, sz));
        if (p != null) p.dirty = true;
    }

    // ---- building

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

    private static boolean loaded(World w, int cx, int cz) {
        return !(w.getChunkFromChunkCoords(cx, cz) instanceof EmptyChunk);
    }

    /** Builds up to {@code budget} changed sections whose chunk is loaded, nearest the gate first; returns how many. */
    int build(World w, int budget) {
        int built = 0;
        while (built < budget) {
            Part next = null;
            for (Part p : parts.values())
                if (p.dirty && (next == null || p.dist < next.dist) && loaded(w, p.sx, p.sz)) next = p;
            if (next == null) break;
            build(w, next);
            built++;
        }
        return built;
    }

    private void build(World w, Part p) {
        p.dirty = false;
        if (p.lists < 0) p.lists = GLAllocation.generateDisplayLists(2);
        int ox = p.sx * 16, oy = p.sy * 16, oz = p.sz * 16;
        ChunkCache cache = new ChunkCache(w, ox - 1, oy - 1, oz - 1, ox + 16, oy + 16, oz + 16, 1);
        RenderBlocks rb = new RenderBlocks(cache);
        Tessellator t = Tessellator.instance;
        for (int pass = 0; pass < 2; pass++) {
            boolean any = false;
            GL11.glNewList(p.lists + pass, GL11.GL_COMPILE);
            setRenderPass(pass);
            t.startDrawingQuads();
            t.setTranslation(-ox, -oy, -oz);
            for (int y = oy; y < oy + 16; y++) for (int z = oz; z < oz + 16; z++) for (int x = ox; x < ox + 16; x++) {
                Block b = cache.getBlock(x, y, z);
                if (b.getMaterial() == Material.air || !b.canRenderInPass(pass)) continue;
                try {
                    any |= rb.renderBlockByRenderType(b, x, y, z);
                } catch (Throwable e) {
                    // a block that cannot be drawn here is left out
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
        p.tiles.clear();
        Chunk c = w.getChunkFromChunkCoords(p.sx, p.sz);
        for (Object o : c.chunkTileEntityMap.values()) {
            TileEntity te = (TileEntity) o;
            if (te.isInvalid() || te.yCoord < oy || te.yCoord >= oy + 16) continue;
            if (TileEntityRendererDispatcher.instance.hasSpecialRenderer(te)) p.tiles.add(te);
        }
    }

    // ---- drawing

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

    /** The special renderers of the tile entities in view, each as lit as where it stands. */
    void drawTiles(World w, float partialTicks) {
        int n = 0;
        for (Part p : parts.values()) {
            for (TileEntity te : p.tiles) {
                if (te.isInvalid() || n++ >= MAX_TILES) continue;
                int light = w.getLightBrightnessForSkyBlocks(te.xCoord, te.yCoord, te.zCoord, 0);
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light % 65536, light / 65536);
                GL11.glColor4f(1f, 1f, 1f, 1f);
                try {
                    TileEntityRendererDispatcher.instance.renderTileEntityAt(
                        te,
                        te.xCoord - source.cx(),
                        te.yCoord - source.y,
                        te.zCoord - source.cz(),
                        partialTicks);
                } catch (Throwable ignored) {
                    // a renderer that cannot draw here is skipped this frame
                }
            }
        }
    }

    void free() {
        for (Part p : parts.values()) {
            if (p.lists >= 0) GLAllocation.deleteDisplayLists(p.lists);
            p.lists = -1;
            p.dirty = true;
            p.tiles.clear();
        }
    }
}
