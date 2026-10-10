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
 * multiblock's drawing ({@link Formed#HIDE}) draws nothing. A shelf that is a body of a shelf unit is drawn by
 * {@link ShelfUnits}.
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
        if (meta == BlockFrame.CONSOLE) return console(w, x, y, z, frame, r);
        // a shelf between a plinth and a crown is a body of a shelf unit, with a compartment and books
        if (meta == BlockFrame.SHELF && ShelfUnits.draw(w, x, y, z, frame, r)) return true;
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

    /** The console's three parts: foot, column, top plate. */
    private static final float[][] CONSOLE = { { 0, 0, 0, 1, 2 / 16f, 1 },
        { 4 / 16f, 2 / 16f, 4 / 16f, 12 / 16f, 13 / 16f, 12 / 16f },
        { 1 / 16f, 13 / 16f, 1 / 16f, 15 / 16f, 1, 15 / 16f } };

    /** A console stand: a foot, a column with a lit channel up each side, a plate with a glowing ring on top. */
    private static boolean console(IBlockAccess w, int x, int y, int z, BlockFrame frame, RenderBlocks r) {
        for (float[] b : CONSOLE) {
            r.setRenderBounds(b[0], b[1], b[2], b[3], b[4], b[5]);
            r.renderStandardBlock(frame, x, y, z);
        }
        boolean ao = r.enableAO;
        r.enableAO = false;
        Tessellator t = Tessellator.instance;
        t.setBrightness(BRIGHT);
        t.setColorOpaque_F(1f, 1f, 1f);
        float[] col = CONSOLE[1], top = CONSOLE[2];
        r.setRenderBounds(col[0] - 0.002, col[1], col[2] - 0.002, col[3] + 0.002, col[4], col[5] + 0.002);
        for (int s = 2; s < 6; s++) face(r, frame, x, y, z, s, frame.glow(s, BlockFrame.CONSOLE));
        r.setRenderBounds(top[0], top[1], top[2], top[3], top[4] + 0.002, top[5]);
        if (r.renderAllFaces || faceShows(w, x, y, z, frame, 1))
            face(r, frame, x, y, z, 1, frame.glow(1, BlockFrame.CONSOLE));
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
        Tessellator t = Tessellator.instance;
        if (meta == BlockFrame.CONSOLE) {
            t.startDrawingQuads();
            for (float[] p : CONSOLE) inventoryBox(r, block, null, p[0], p[1], p[2], p[3], p[4], p[5], meta);
            float[] top = CONSOLE[2];
            inventoryBox(r, block, frame.glow(1, meta), top[0], top[1], top[2], top[3], top[4] + 0.002f, top[5], meta);
            t.draw();
            r.setRenderBounds(0, 0, 0, 1, 1, 1);
            GL11.glTranslatef(0.5f, 0.5f, 0.5f);
            return;
        }
        float[] b = BlockFrame.box(meta);
        r.setRenderBounds(b[0], b[1], b[2], b[3], b[4], b[5]);
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

    /** A box of the console in the inventory, with the part's own icons ({@code icon} null) or one icon on top. */
    private static void inventoryBox(RenderBlocks r, Block block, IIcon icon, float x0, float y0, float z0, float x1,
        float y1, float z1, int meta) {
        r.setRenderBounds(x0, y0, z0, x1, y1, z1);
        Tessellator t = Tessellator.instance;
        for (int s = 0; s < 6; s++) {
            if (icon != null && s != 1) continue;
            normal(t, s);
            face(r, block, 0, 0, 0, s, icon != null ? icon : block.getIcon(s, meta));
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
