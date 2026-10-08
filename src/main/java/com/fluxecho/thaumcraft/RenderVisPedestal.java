package com.fluxecho.thaumcraft;

import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import org.lwjgl.opengl.GL11;

/**
 * The wand on a Flux Vis Pedestal floats above it, turning and bobbing like on Thaumcraft's own recharge pedestal;
 * while vis flows into it, it turns faster and rises a little.
 */
public class RenderVisPedestal extends TileEntitySpecialRenderer {

    private EntityItem shown;

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partial) {
        if (!(te instanceof TileVisPedestal p) || p.wand() == null || te.getWorldObj() == null) return;
        ItemStack wand = p.wand();
        if (shown == null || shown.worldObj != te.getWorldObj()) shown = new EntityItem(te.getWorldObj());
        shown.setEntityItemStack(wand);
        shown.hoverStart = 0f;

        float t = te.getWorldObj()
            .getTotalWorldTime() + partial;
        boolean charging = p.charging();
        float spin = t * (charging ? 6f : 1.5f) % 360f;
        double lift = (charging ? 1.05 : 0.95) + 0.06 * Math.sin(t / 10.0);

        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + lift, z + 0.5);
        GL11.glRotatef(spin, 0f, 1f, 0f);
        GL11.glScalef(1.4f, 1.4f, 1.4f);
        boolean frame = RenderItem.renderInFrame;
        RenderItem.renderInFrame = true;
        RenderManager.instance.renderEntityWithPosYaw(shown, 0, 0, 0, 0f, 0f);
        RenderItem.renderInFrame = frame;
        GL11.glPopMatrix();
    }
}
