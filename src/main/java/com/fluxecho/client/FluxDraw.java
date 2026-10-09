package com.fluxecho.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

/**
 * The flux world's look (as in FluxDepths' collector): deep navy panes, cyan seams, the depth's violet glow. Plain GL
 * quads, no textures; for the GUIs and for the holograms in the world. Client side only.
 * <p>
 * In the world ({@link #worldBegin}) every vertex gets a normal pointing at the viewer's up (the panes are drawn upside
 * down, so -y): shader packs light geometry by its normal, and a pane without one comes out washed white.
 */
public final class FluxDraw {

    public static final int DEEP = 0x0A1622, PANE = 0x0F2231, SEAM = 0x1F3C4E, CYAN = 0x4FE3FF, VIOLET = 0x8A5CFF,
        WHITE = 0xE8F8FF, DIM = 0x86A6B8, RED = 0xFF6050, GREEN = 0x6CFF8A, AMBER = 0xFFB040, MANA = 0x46C8FF,
        MANA_PINK = 0xFF8CE6;

    private static boolean world;

    private FluxDraw() {}

    public static FontRenderer font() {
        return Minecraft.getMinecraft().fontRenderer;
    }

    /** The text cut to fit the width (in font pixels), with "…" when anything was cut. */
    public static String fit(String text, int width) {
        FontRenderer f = font();
        if (text == null || f.getStringWidth(text) <= width) return text;
        return f.trimStringToWidth(text, Math.max(0, width - f.getStringWidth("…"))) + "…";
    }

    /**
     * Sets up drawing a hologram in the world: no lighting or fog, full brightness (sky light off under a shader pack,
     * as FluxLite's hologram does), blending, no depth writes. Pair with {@link #worldEnd}.
     */
    public static void worldBegin() {
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LIGHTING_BIT
                | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, ShaderCompat.packInUse() ? 0f : 240f);
        world = true;
    }

    /**
     * Restores what {@link #worldBegin} changed. Texturing is switched on again by hand: the pop restores OpenGL, but
     * Angelica only tells the shader pipeline about glEnable / glDisable, and whatever is drawn next would come out
     * white.
     */
    public static void worldEnd() {
        world = false;
        GL11.glPopAttrib();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 1f, 1f);
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

    public static Tessellator start(int mode) {
        Tessellator t = Tessellator.instance;
        t.startDrawing(mode);
        if (world) t.setNormal(0, -1, 0);
        return t;
    }

    public static void rect(double x0, double y0, double x1, double y1, int rgb, float a) {
        gradient(x0, y0, x1, y1, rgb, a, rgb, a);
    }

    /** A rectangle shading from {@code top} to {@code bottom}. */
    public static void gradient(double x0, double y0, double x1, double y1, int top, float aTop, int bottom,
        float aBottom) {
        Tessellator t = start(GL11.GL_QUADS);
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
        Tessellator t = start(GL11.GL_QUADS);
        t.setColorRGBA_I(right, alpha(aRight));
        t.addVertex(x1, y0, 0);
        t.setColorRGBA_I(left, alpha(aLeft));
        t.addVertex(x0, y0, 0);
        t.addVertex(x0, y1, 0);
        t.setColorRGBA_I(right, alpha(aRight));
        t.addVertex(x1, y1, 0);
        t.draw();
    }

    /**
     * A trapezoid around {@code cx}: {@code halfTop} wide each side at {@code yTop}, {@code halfBottom} at the bottom.
     */
    public static void trapezoid(double cx, double yTop, double halfTop, double yBottom, double halfBottom, int top,
        float aTop, int bottom, float aBottom) {
        Tessellator t = start(GL11.GL_QUADS);
        t.setColorRGBA_I(top, alpha(aTop));
        t.addVertex(cx + halfTop, yTop, 0);
        t.addVertex(cx - halfTop, yTop, 0);
        t.setColorRGBA_I(bottom, alpha(aBottom));
        t.addVertex(cx - halfBottom, yBottom, 0);
        t.addVertex(cx + halfBottom, yBottom, 0);
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

    /** A vertical bar filling from the bottom. */
    public static void column(double x0, double y0, double x1, double y1, float fill, int rgb, float a) {
        rect(x0, y0, x1, y1, DEEP, a);
        frame(x0, y0, x1, y1, SEAM, a);
        if (fill <= 0) return;
        double top = y1 - 1 - (y1 - y0 - 2) * Math.min(1, fill);
        gradient(x0 + 1, top, x1 - 1, y1 - 1, rgb, a, darker(rgb), a);
        rect(x0 + 1, top, x1 - 1, Math.min(y1 - 1, top + 1), WHITE, a * 0.8f);
    }

    /** A band sweeping down a box; {@code t} in ticks. */
    public static void scan(double x0, double y0, double x1, double y1, float t, int rgb, float a) {
        double h = y1 - y0, at = (t * 1.5) % (h + 16) - 8;
        if (at + 2 < 0 || at > h) return;
        rect(x0, y0 + Math.max(0, at), x1, y0 + Math.min(h, at + 2), rgb, a);
    }

    /** Text with an alpha the font renderer keeps (it treats almost none as full). */
    public static void text(String s, double x, double y, int rgb, float a) {
        small(s, x, y, 1f, rgb, a);
    }

    public static void centered(String s, double cx, double y, int rgb, float a) {
        small(s, cx - font().getStringWidth(s) / 2.0, y, 1f, rgb, a);
    }

    public static void right(String s, double rx, double y, int rgb, float a) {
        small(s, rx - font().getStringWidth(s), y, 1f, rgb, a);
    }

    /** Text at {@code scale} (0.5 = half size), left-aligned at x, y. */
    public static void small(String s, double x, double y, float scale, int rgb, float a) {
        if (s == null || s.isEmpty()) return;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        if (world) GL11.glNormal3f(0, -1, 0);
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, 0);
        if (scale != 1f) GL11.glScalef(scale, scale, 1f);
        font().drawString(s, 0, 0, argb(rgb, a));
        GL11.glPopMatrix();
    }

    public static void smallCentered(String s, double cx, double y, float scale, int rgb, float a) {
        small(s, cx - font().getStringWidth(s) * scale / 2, y, scale, rgb, a);
    }

    public static void smallRight(String s, double rx, double y, float scale, int rgb, float a) {
        small(s, rx - font().getStringWidth(s) * scale, y, scale, rgb, a);
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
