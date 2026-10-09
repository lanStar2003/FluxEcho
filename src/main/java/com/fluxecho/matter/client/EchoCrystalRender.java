package com.fluxecho.matter.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.fluxecho.codex.Categories;
import com.fluxecho.matter.ItemEchoCrystal;

/**
 * An echo crystal in a slot shows the entry it holds floating on its face (blueprint 3.6): the crystal, then the
 * entry's own icon (a bee, an aspect, an orb...) small in the middle.
 */
public final class EchoCrystalRender implements IItemRenderer {

    private static final RenderItem ITEMS = new RenderItem();
    private static boolean inside;

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return type == ItemRenderType.INVENTORY && !inside;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return false;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack stack, Object... data) {
        Minecraft mc = Minecraft.getMinecraft();
        IIcon icon = stack.getIconIndex();
        mc.getTextureManager()
            .bindTexture(TextureMap.locationItemsTexture);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        if (icon != null) ITEMS.renderIcon(0, 0, icon, 16, 16);
        ItemStack shown = shown(stack);
        if (shown == null) return;
        inside = true;
        try {
            GL11.glPushMatrix();
            GL11.glTranslatef(4f, 4f, 10f);
            GL11.glScalef(0.5f, 0.5f, 1f);
            RenderHelper.enableGUIStandardItemLighting();
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            ITEMS.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), shown, 0, 0);
            RenderHelper.disableStandardItemLighting();
            GL11.glPopMatrix();
        } catch (RuntimeException e) {
            GL11.glPopMatrix();
        } finally {
            inside = false;
            GL11.glDisable(GL11.GL_LIGHTING);
        }
    }

    /** The icon of the entry the crystal holds; null for none or an echo crystal (no crystal in a crystal). */
    private static ItemStack shown(ItemStack stack) {
        Categories.Category c = ItemEchoCrystal.categoryOf(stack);
        String key = ItemEchoCrystal.key(stack);
        if (c == null || key == null) return null;
        try {
            ItemStack s = c.icon.apply(key);
            return s == null || s.getItem() instanceof ItemEchoCrystal ? null : s;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
