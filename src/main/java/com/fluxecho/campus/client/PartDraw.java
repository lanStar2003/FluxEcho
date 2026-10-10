package com.fluxecho.campus.client;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Drawing helpers shared by the chunk-mesh renderers of the campus parts and of the frame's shelf units. They keep the
 * one approach that works under shader packs: a part is drawn with {@link RenderBlocks#renderStandardBlock} (vanilla
 * lighting and smooth lighting), then its bright lines are drawn again on the faces that show, at full brightness with
 * ambient occlusion off. Nothing here keeps state between calls, so it is safe wherever the chunk is built.
 */
@SideOnly(Side.CLIENT)
public final class PartDraw {

    /** Full block and sky light: the glowing lines ignore the light around them. */
    public static final int BRIGHT = 0xF000F0;

    /** The neighbour offset of each side, in vanilla's side order (down, up, north, south, west, east). */
    public static final int[][] OFFSETS = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 },
        { 1, 0, 0 } };

    /** Vanilla's flat shading of each side: the underside darkest, east and west darker than north and south. */
    private static final float[] SHADE = { 0.5f, 1f, 0.8f, 0.8f, 0.6f, 0.6f };

    private PartDraw() {}

    /** The flat shade vanilla gives a face looking towards side s. */
    public static float shade(int s) {
        return SHADE[s];
    }

    /** Draws one face of the current render bounds with an icon, lit as the tessellator is set. */
    public static void face(RenderBlocks r, Block block, double x, double y, double z, int s, IIcon icon) {
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

    /** Whether the block's face towards side s is drawn, by the block's own rule (as vanilla asks it). */
    public static boolean shows(IBlockAccess w, int x, int y, int z, Block block, int s) {
        int[] o = OFFSETS[s];
        return block.shouldSideBeRendered(w, x + o[0], y + o[1], z + o[2], s);
    }

    /**
     * A box drawn the way vanilla draws a block: the block's own icons, vanilla lighting, the faces the block's
     * {@code shouldSideBeRendered} hides left out.
     */
    public static boolean box(RenderBlocks r, Block block, int x, int y, int z, double x0, double y0, double z0,
        double x1, double y1, double z1) {
        r.setRenderBounds(x0, y0, z0, x1, y1, z1);
        return r.renderStandardBlock(block, x, y, z);
    }

    /**
     * A box with every face drawn, for pieces whose faces lie inside the block (a neighbour must not hide them), with
     * vanilla lighting. A non-null icon is used on all six faces unless something (the block breaking animation)
     * already overrides the texture.
     */
    public static void inner(RenderBlocks r, Block block, int x, int y, int z, IIcon icon, double x0, double y0,
        double z0, double x1, double y1, double z1) {
        boolean all = r.renderAllFaces;
        boolean override = icon != null && !r.hasOverrideBlockTexture();
        r.renderAllFaces = true;
        if (override) r.setOverrideBlockTexture(icon);
        r.setRenderBounds(x0, y0, z0, x1, y1, z1);
        r.renderStandardBlock(block, x, y, z);
        if (override) r.clearOverrideBlockTexture();
        r.renderAllFaces = all;
    }

    /**
     * Sets the tessellator up for glowing overlays: full brightness, white, smooth lighting off. Returns the renderer's
     * smooth lighting flag for {@link #glowOff}.
     */
    public static boolean glowOn(RenderBlocks r) {
        boolean ao = r.enableAO;
        r.enableAO = false;
        Tessellator t = Tessellator.instance;
        t.setBrightness(BRIGHT);
        t.setColorOpaque_F(1f, 1f, 1f);
        return ao;
    }

    public static void glowOff(RenderBlocks r, boolean ao) {
        r.enableAO = ao;
    }

    /**
     * One face of a box (world coordinates) with explicit texture coordinates in pixels: {@code u0..u1} runs from the
     * face's left to its right and {@code v0..v1} from its top to its bottom as seen from outside (for the top face,
     * "up" is north; for the underside, south). The vertices go round counter-clockwise seen from outside, and the
     * tessellator's current colour and brightness light them.
     */
    public static void quad(Tessellator t, int s, double x0, double y0, double z0, double x1, double y1, double z1,
        IIcon icon, double u0, double v0, double u1, double v1) {
        double a = icon.getInterpolatedU(u0), b = icon.getInterpolatedU(u1);
        double c = icon.getInterpolatedV(v0), d = icon.getInterpolatedV(v1);
        switch (s) {
            case 0:
                t.addVertexWithUV(x0, y0, z1, a, c);
                t.addVertexWithUV(x0, y0, z0, a, d);
                t.addVertexWithUV(x1, y0, z0, b, d);
                t.addVertexWithUV(x1, y0, z1, b, c);
                break;
            case 1:
                t.addVertexWithUV(x0, y1, z0, a, c);
                t.addVertexWithUV(x0, y1, z1, a, d);
                t.addVertexWithUV(x1, y1, z1, b, d);
                t.addVertexWithUV(x1, y1, z0, b, c);
                break;
            case 2:
                t.addVertexWithUV(x1, y1, z0, a, c);
                t.addVertexWithUV(x1, y0, z0, a, d);
                t.addVertexWithUV(x0, y0, z0, b, d);
                t.addVertexWithUV(x0, y1, z0, b, c);
                break;
            case 3:
                t.addVertexWithUV(x0, y1, z1, a, c);
                t.addVertexWithUV(x0, y0, z1, a, d);
                t.addVertexWithUV(x1, y0, z1, b, d);
                t.addVertexWithUV(x1, y1, z1, b, c);
                break;
            case 4:
                t.addVertexWithUV(x0, y1, z0, a, c);
                t.addVertexWithUV(x0, y0, z0, a, d);
                t.addVertexWithUV(x0, y0, z1, b, d);
                t.addVertexWithUV(x0, y1, z1, b, c);
                break;
            default:
                t.addVertexWithUV(x1, y1, z1, a, c);
                t.addVertexWithUV(x1, y0, z1, a, d);
                t.addVertexWithUV(x1, y0, z0, b, d);
                t.addVertexWithUV(x1, y1, z0, b, c);
        }
    }

    // ---- inventory

    /** What texture a box shows on a side in the inventory; null leaves the side out. */
    public interface Icons {

        IIcon at(int side);
    }

    /** Sets the normal of side s; the inventory is lit by OpenGL, which needs one per face. */
    public static void normal(Tessellator t, int s) {
        int[] o = OFFSETS[s];
        t.setNormal(o[0], o[1], o[2]);
    }

    /** A box in the inventory, each face with its normal; call between {@code startDrawingQuads} and {@code draw}. */
    public static void inventoryBox(RenderBlocks r, Block block, Icons icons, double x0, double y0, double z0,
        double x1, double y1, double z1) {
        r.setRenderBounds(x0, y0, z0, x1, y1, z1);
        Tessellator t = Tessellator.instance;
        for (int s = 0; s < 6; s++) {
            IIcon icon = icons.at(s);
            if (icon == null) continue;
            normal(t, s);
            face(r, block, 0, 0, 0, s, icon);
        }
    }
}
