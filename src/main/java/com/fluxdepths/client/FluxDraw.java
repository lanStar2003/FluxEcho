package com.fluxdepths.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

/**
 * The flux world's look, shared by the collector's GUI, its hologram and its NEI page: deep navy panes, cyan seams,
 * a violet depth glow, and the tier colours. Plain GL quads, no textures. Client side only.
 */
public final class FluxDraw {

    public static final int DEEP = 0x0A1622, PANE = 0x0F2231, SEAM = 0x1F3C4E, CYAN = 0x4FE3FF, VIOLET = 0x8A5CFF,
        WHITE = 0xE8F8FF, DIM = 0x86A6B8, RED = 0xFF6050, GREEN = 0x6CFF8A, AMBER = 0xFFB040;

    private FluxDraw() {}

    public static FontRenderer font() {
        return Minecraft.getMinecraft().fontRenderer;
    }

    /** Starts flat, translucent drawing: no texture, blending on. */
    public static void begin() {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glShadeModel(GL11.GL_SMOOTH);
    }

    /** Back to textured drawing (text, items). */
    public static void end() {
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    public static void rect(double x0, double y0, double x1, double y1, int rgb, float a) {
        gradient(x0, y0, x1, y1, rgb, a, rgb, a);
    }

    /** A rectangle shading from {@code top} to {@code bottom}. */
    public static void gradient(double x0, double y0, double x1, double y1, int top, float aTop, int bottom,
        float aBottom) {
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_I(top, alpha(aTop));
        t.addVertex(x1, y0, 0);
        t.addVertex(x0, y0, 0);
        t.setColorRGBA_I(bottom, alpha(aBottom));
        t.addVertex(x0, y1, 0);
        t.addVertex(x1, y1, 0);
        t.draw();
    }

    /** A rectangle shading from {@code left} to {@code right}. */
    public static void hgradient(double x0, double y0, double x1, double y1, int left, float aLeft, int right,
        float aRight) {
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_I(right, alpha(aRight));
        t.addVertex(x1, y0, 0);
        t.setColorRGBA_I(left, alpha(aLeft));
        t.addVertex(x0, y0, 0);
        t.addVertex(x0, y1, 0);
        t.setColorRGBA_I(right, alpha(aRight));
        t.addVertex(x1, y1, 0);
        t.draw();
    }

    /** A one-pixel outline. */
    public static void frame(double x0, double y0, double x1, double y1, int rgb, float a) {
        rect(x0, y0, x1, y0 + 1, rgb, a);
        rect(x0, y1 - 1, x1, y1, rgb, a);
        rect(x0, y0 + 1, x0 + 1, y1 - 1, rgb, a);
        rect(x1 - 1, y0 + 1, x1, y1 - 1, rgb, a);
    }

    /** The flux pane: deep fill, seam outline, bright corner brackets. */
    public static void pane(double x0, double y0, double x1, double y1, float a) {
        gradient(x0, y0, x1, y1, PANE, a * 0.92f, DEEP, a * 0.92f);
        frame(x0, y0, x1, y1, SEAM, a);
        corners(x0, y0, x1, y1, 5, CYAN, a);
    }

    public static void corners(double x0, double y0, double x1, double y1, double len, int rgb, float a) {
        rect(x0, y0, x0 + len, y0 + 1, rgb, a);
        rect(x0, y0, x0 + 1, y0 + len, rgb, a);
        rect(x1 - len, y0, x1, y0 + 1, rgb, a);
        rect(x1 - 1, y0, x1, y0 + len, rgb, a);
        rect(x0, y1 - 1, x0 + len, y1, rgb, a);
        rect(x0, y1 - len, x0 + 1, y1, rgb, a);
        rect(x1 - len, y1 - 1, x1, y1, rgb, a);
        rect(x1 - 1, y1 - len, x1, y1, rgb, a);
    }

    /** A horizontal bar: track, fill up to {@code fill} (0..1) with a bright leading edge. */
    public static void bar(double x0, double y0, double x1, double y1, float fill, int rgb, float a) {
        rect(x0, y0, x1, y1, DEEP, a);
        frame(x0, y0, x1, y1, SEAM, a);
        if (fill <= 0) return;
        double end = x0 + 1 + (x1 - x0 - 2) * Math.min(1, fill);
        hgradient(x0 + 1, y0 + 1, end, y1 - 1, darker(rgb), a, rgb, a);
        rect(Math.max(x0 + 1, end - 1), y0 + 1, end, y1 - 1, WHITE, a * 0.8f);
    }

    /** A diagonal sheen sweeping across a box; {@code t} in ticks. */
    public static void scan(double x0, double y0, double x1, double y1, float t, int rgb, float a) {
        double h = y1 - y0, at = (t * 1.5) % (h + 16) - 8;
        if (at + 2 < 0 || at > h) return;
        rect(x0, y0 + Math.max(0, at), x1, y0 + Math.min(h, at + 2), rgb, a);
    }

    /** Text with an alpha the font renderer keeps (it treats almost none as full). */
    public static void text(String s, int x, int y, int rgb, float a) {
        font().drawString(s, x, y, argb(rgb, a));
    }

    public static void centered(String s, int cx, int y, int rgb, float a) {
        FontRenderer f = font();
        f.drawString(s, cx - f.getStringWidth(s) / 2, y, argb(rgb, a));
    }

    public static void right(String s, int rx, int y, int rgb, float a) {
        FontRenderer f = font();
        f.drawString(s, rx - f.getStringWidth(s), y, argb(rgb, a));
    }

    /** Text at {@code scale} (0.5 = half size), left-aligned at x, y. */
    public static void small(String s, double x, double y, float scale, int rgb, float a) {
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, 0);
        GL11.glScalef(scale, scale, 1f);
        font().drawString(s, 0, 0, argb(rgb, a));
        GL11.glPopMatrix();
    }

    public static void smallCentered(String s, double cx, double y, float scale, int rgb, float a) {
        small(s, cx - font().getStringWidth(s) * scale / 2, y, scale, rgb, a);
    }

    public static int argb(int rgb, float a) {
        int alpha = Math.max(5, Math.min(255, (int) (a * 255)));
        return alpha << 24 | rgb & 0xFFFFFF;
    }

    public static int darker(int rgb) {
        return (rgb >> 17 & 0x7F) << 16 | (rgb >> 9 & 0x7F) << 8 | rgb >> 1 & 0x7F;
    }

    private static int alpha(float a) {
        return (int) (Math.max(0, Math.min(1, a)) * 255);
    }
}
