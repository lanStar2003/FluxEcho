package com.fluxecho.client.shape;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import com.fluxecho.campus.BlockFitting;
import com.fluxecho.campus.client.PartDraw;
import com.fluxecho.logic.skin.SkinPalette;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws the planting bed (fitting 14, 0.17.0, style FINAL §3.7): a white trough {@link BlockFitting#BED_TOP} high with
 * soil on top, and its warm lip in two layers (style §3.6) at full brightness: a line round the soil just inside the
 * rim, and one under the rim on every side that shows. {@code campus.client.FittingRender} hands the bed's meta here,
 * in the world and as an item.
 */
@SideOnly(Side.CLIENT)
public final class BedDraw {

    private BedDraw() {}

    /** The bed in the chunk mesh: the trough with vanilla's light, then its lip on the faces that show. */
    public static boolean draw(IBlockAccess w, int x, int y, int z, BlockFitting fit, RenderBlocks r) {
        boolean drawn = PartDraw.box(r, fit, x, y, z, 0, 0, 0, 1, BlockFitting.BED_TOP, 1);
        if (r.hasOverrideBlockTexture()) return drawn;
        boolean ao = PartDraw.glowOn(r);
        Tessellator t = Tessellator.instance;
        for (int s = 1; s < 6; s++) {
            if (s > 1 && !(r.renderAllFaces || PartDraw.shows(w, x, y, z, fit, s))) continue;
            r.setRenderBounds(0, 0, 0, 1, BlockFitting.BED_TOP, 1);
            t.setColorOpaque_I(0xFFFFFF);
            PartDraw.face(r, fit, x, y, z, s, SkinIcons.bedGlow(s, false));
            t.setColorOpaque_I(SkinPalette.WARM_WHITE);
            PartDraw.face(r, fit, x, y, z, s, SkinIcons.bedGlow(s, true));
        }
        PartDraw.glowOff(r, ao);
        r.setRenderBounds(0, 0, 0, 1, 1, 1);
        return true;
    }

    /** The bed as an item; call between {@code startDrawingQuads} and {@code draw}. */
    public static void inventory(BlockFitting fit, RenderBlocks r) {
        PartDraw.inventoryBox(r, fit, SkinIcons::bed, 0, 0, 0, 1, BlockFitting.BED_TOP, 1);
        Tessellator t = Tessellator.instance;
        t.setColorOpaque_I(0xFFFFFF);
        PartDraw.inventoryBox(r, fit, s -> s == 0 ? null : glow(s, false), 0, 0, 0, 1, BlockFitting.BED_TOP, 1);
        t.setColorOpaque_I(SkinPalette.WARM_WHITE);
        PartDraw.inventoryBox(r, fit, s -> s == 0 ? null : glow(s, true), 0, 0, 0, 1, BlockFitting.BED_TOP, 1);
        t.setColorOpaque_I(0xFFFFFF);
    }

    private static IIcon glow(int side, boolean edge) {
        return SkinIcons.bedGlow(side, edge);
    }
}
