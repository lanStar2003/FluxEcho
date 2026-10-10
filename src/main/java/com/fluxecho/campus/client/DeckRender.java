package com.fluxecho.campus.client;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import org.lwjgl.opengl.GL11;

import com.fluxecho.campus.BlockDeck;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws the campus deck into the chunk mesh: the cube as vanilla draws a stone block, then, for the metas that give
 * light, the tile's bright lines again on every face that shows and has an overlay ({@link BlockDeck#glow}: the lit
 * strip's floor cross only on top, a level line on its upright faces), at full brightness with smooth lighting off.
 * That is how the frame's lit parts are drawn too, and it keeps the lines lit under a shader pack.
 */
@SideOnly(Side.CLIENT)
public final class DeckRender implements ISimpleBlockRenderingHandler {

    private final int id;

    public DeckRender(int id) {
        this.id = id;
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess w, int x, int y, int z, Block block, int modelId, RenderBlocks r) {
        if (!(block instanceof BlockDeck deck)) return false;
        int meta = w.getBlockMetadata(x, y, z);
        boolean drawn = PartDraw.box(r, block, x, y, z, 0, 0, 0, 1, 1, 1);
        // the breaking animation draws the block again with its own texture: no overlays then
        if (r.hasOverrideBlockTexture()) return drawn;
        boolean ao = r.enableAO, lit = false;
        for (int s = 0; s < 6; s++) {
            IIcon g = deck.glow(meta, s);
            if (g == null || !(r.renderAllFaces || PartDraw.shows(w, x, y, z, block, s))) continue;
            if (!lit) PartDraw.glowOn(r);
            lit = true;
            PartDraw.face(r, block, x, y, z, s, g);
        }
        if (lit) PartDraw.glowOff(r, ao);
        return drawn || lit;
    }

    @Override
    public void renderInventoryBlock(Block block, int meta, int modelId, RenderBlocks r) {
        if (!(block instanceof BlockDeck deck)) return;
        GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        PartDraw.inventoryBox(r, block, s -> deck.icon(meta, s), 0, 0, 0, 1, 1, 1);
        PartDraw.inventoryBox(r, block, s -> deck.glow(meta, s), 0, 0, 0, 1, 1, 1);
        t.draw();
        r.setRenderBounds(0, 0, 0, 1, 1, 1);
        GL11.glTranslatef(0.5f, 0.5f, 0.5f);
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
