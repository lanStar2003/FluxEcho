package com.fluxecho.frame.client;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import org.lwjgl.opengl.GL11;

import com.fluxecho.frame.BlockFrame;
import com.fluxecho.frame.Formed;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws the flux frame into the chunk mesh: each part's box with the usual lighting, its bright lines again on top at
 * full brightness, the conduit's core and the seat's crystal glowing whole. A part that gave way to a formed
 * multiblock's drawing ({@link Formed#HIDE}) draws nothing.
 */
@SideOnly(Side.CLIENT)
public final class FrameRender implements ISimpleBlockRenderingHandler {

    /** Full block and sky light: the glowing parts ignore the light around them. */
    private static final int BRIGHT = 0xF000F0;

    private final int id;

    public FrameRender(int id) {
        this.id = id;
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess w, int x, int y, int z, Block block, int modelId, RenderBlocks r) {
        if (!(block instanceof BlockFrame frame)) return false;
        if ((Formed.clientFlags(x, y, z) & Formed.HIDE) != 0) return false;
        int meta = w.getBlockMetadata(x, y, z);
        float[] b = BlockFrame.box(meta);
        r.setRenderBounds(b[0], b[1], b[2], b[3], b[4], b[5]);
        r.renderStandardBlock(block, x, y, z);
        boolean ao = r.enableAO;
        r.enableAO = false;
        Tessellator t = Tessellator.instance;
        t.setBrightness(BRIGHT);
        t.setColorOpaque_F(1f, 1f, 1f);
        for (int s = 0; s < 6; s++) {
            IIcon g = frame.glow(s, meta);
            if (g != null && (r.renderAllFaces || faceShows(w, x, y, z, block, s))) face(r, block, x, y, z, s, g);
        }
        if (meta == BlockFrame.CONDUIT)
            glowBox(r, block, x, y, z, frame.core, 6.5f / 16, 0, 6.5f / 16, 9.5f / 16, 1, 9.5f / 16);
        if (meta == BlockFrame.SEAT)
            glowBox(r, block, x, y, z, frame.crystal, 5 / 16f, 8 / 16f, 5 / 16f, 11 / 16f, 15 / 16f, 11 / 16f);
        r.enableAO = ao;
        r.setRenderBounds(0, 0, 0, 1, 1, 1);
        return true;
    }

    private static boolean faceShows(IBlockAccess w, int x, int y, int z, Block block, int s) {
        int[] o = OFFSETS[s];
        return block.shouldSideBeRendered(w, x + o[0], y + o[1], z + o[2], s);
    }

    private static final int[][] OFFSETS = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 },
        { 1, 0, 0 } };

    private static void face(RenderBlocks r, Block block, double x, double y, double z, int s, IIcon icon) {
        switch (s) {
            case 0:
                r.renderFaceYNeg(block, x, y, z, icon);
                break;
            case 1:
                r.renderFaceYPos(block, x, y, z, icon);
                break;
            case 2:
                r.renderFaceZNeg(block, x, y, z, icon);
                break;
            case 3:
                r.renderFaceZPos(block, x, y, z, icon);
                break;
            case 4:
                r.renderFaceXNeg(block, x, y, z, icon);
                break;
            default:
                r.renderFaceXPos(block, x, y, z, icon);
        }
    }

    private static void glowBox(RenderBlocks r, Block block, int x, int y, int z, IIcon icon, float x0, float y0,
        float z0, float x1, float y1, float z1) {
        r.setRenderBounds(x0, y0, z0, x1, y1, z1);
        for (int s = 0; s < 6; s++) face(r, block, x, y, z, s, icon);
    }

    @Override
    public void renderInventoryBlock(Block block, int meta, int modelId, RenderBlocks r) {
        if (!(block instanceof BlockFrame frame)) return;
        GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
        float[] b = BlockFrame.box(meta);
        r.setRenderBounds(b[0], b[1], b[2], b[3], b[4], b[5]);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        for (int s = 0; s < 6; s++) {
            normal(t, s);
            face(r, block, 0, 0, 0, s, block.getIcon(s, meta));
            IIcon g = frame.glow(s, meta);
            if (g != null) face(r, block, 0, 0, 0, s, g);
        }
        if (meta == BlockFrame.CONDUIT)
            inventoryBox(r, block, frame.core, 6.5f / 16, 0, 6.5f / 16, 9.5f / 16, 1, 9.5f / 16);
        if (meta == BlockFrame.SEAT)
            inventoryBox(r, block, frame.crystal, 5 / 16f, 8 / 16f, 5 / 16f, 11 / 16f, 15 / 16f, 11 / 16f);
        t.draw();
        r.setRenderBounds(0, 0, 0, 1, 1, 1);
        GL11.glTranslatef(0.5f, 0.5f, 0.5f);
    }

    private static void inventoryBox(RenderBlocks r, Block block, IIcon icon, float x0, float y0, float z0, float x1,
        float y1, float z1) {
        r.setRenderBounds(x0, y0, z0, x1, y1, z1);
        Tessellator t = Tessellator.instance;
        for (int s = 0; s < 6; s++) {
            normal(t, s);
            face(r, block, 0, 0, 0, s, icon);
        }
    }

    private static void normal(Tessellator t, int s) {
        int[] o = OFFSETS[s];
        t.setNormal(o[0], o[1], o[2]);
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return id;
    }
}
