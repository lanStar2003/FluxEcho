package com.fluxecho.client.ui;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.fluxecho.logic.ui.AtlasMap;
import com.fluxecho.logic.ui.ColorMath;
import com.fluxecho.logic.ui.FluxMono;
import com.fluxecho.logic.ui.Tok;
import com.fluxecho.logic.ui.UiGeo;
import com.fluxecho.logic.ui.Xform;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * {@link FluxCanvas} in the game: the Tessellator, quads only (a triangle is a quad that repeats a corner; Angelica's
 * tessellator works in quads everywhere), and Minecraft's font for text. One geometry for the GUI and, through
 * {@link #world}, for cards drawn in the world, where every vertex gets a normal (shader packs light by it) and nothing
 * is clipped.
 * <p>
 * Shapes go into one open batch until something needs a different GL state (the atlas texture, text, an item, a clip,
 * a transform, a blend mode), which draws the batch first; so a page costs a handful of draw calls, not one per shape.
 * The 2D transform and the clip are tracked in an {@link Xform} next to the GL matrix, so clips follow the transform
 * and nest (ui/FINAL.md section 2.3.2). {@link #begin} and {@link #end} wrap a frame and restore every state they
 * touch,
 * ending with texturing switched on by hand (Angelica only hears about glEnable / glDisable, not glPopAttrib).
 */
@SideOnly(Side.CLIENT)
public final class McFluxCanvas implements FluxCanvas {

    public static final ResourceLocation ATLAS_2X = new ResourceLocation("fluxecho", "textures/gui/ui/atlas@2x.png");
    public static final ResourceLocation ATLAS_4X = new ResourceLocation("fluxecho", "textures/gui/ui/atlas@4x.png");

    private static RenderItem items;

    private static final int NONE = 0, SHAPES = 1, SPRITES = 2;

    private final FontRenderer font;
    private final TextureManager textures;
    private final int factor, displayHeight;
    private final boolean world;
    private final Xform xf = new Xform();
    private float opacity = 1;
    private Blend blend = Blend.NORMAL;
    private int batch = NONE;
    private boolean depthKept;

    public McFluxCanvas(FontRenderer font, TextureManager textures, int factor, int displayHeight) {
        this(font, textures, factor, displayHeight, false);
    }

    private McFluxCanvas(FontRenderer font, TextureManager textures, int factor, int displayHeight, boolean world) {
        this.font = font;
        this.textures = textures;
        this.factor = Math.max(1, factor);
        this.displayHeight = displayHeight;
        this.world = world;
    }

    /** A canvas for the current screen, at the game's GUI scale. */
    public static McFluxCanvas gui() {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution r = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        return new McFluxCanvas(mc.fontRenderer, mc.getTextureManager(), r.getScaleFactor(), mc.displayHeight);
    }

    /** A canvas for cards in the world; the caller sets up the matrix and the world GL state. */
    public static McFluxCanvas world(FontRenderer font, TextureManager textures) {
        return new McFluxCanvas(font, textures, 4, 0, true);
    }

    @Override
    public int factor() {
        return factor;
    }

    // ------------------------------------------------------------------ frame

    /** Starts a frame: saves the GL state this canvas changes and sets the flat 2D state. */
    public void begin() {
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LIGHTING_BIT
                | GL11.GL_SCISSOR_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        xf.reset();
        blend = Blend.NORMAL;
        depthKept = false;
        opacity = 1;
    }

    /** Ends a frame: draws what is pending and restores the state {@link #begin} saved. */
    public void end() {
        flush();
        while (xf.depth() > 0) {
            xf.pop();
            GL11.glPopMatrix();
        }
        xf.reset();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glPopAttrib();
        restoreTexture();
        GL11.glColor4f(1, 1, 1, 1);
    }

    /**
     * Switches texturing back on after a {@code glPopAttrib}: the pop restores OpenGL, but Angelica only tells the
     * shader pipeline when texturing is switched through glEnable / glDisable.
     */
    public static void restoreTexture() {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    // ------------------------------------------------------------------ batching

    private Tessellator shapes() {
        Tessellator t = Tessellator.instance;
        if (batch != SHAPES) {
            flush();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            t.startDrawingQuads();
            if (world) t.setNormal(0, -1, 0);
            batch = SHAPES;
        }
        return t;
    }

    private Tessellator sprites() {
        Tessellator t = Tessellator.instance;
        if (batch != SPRITES) {
            flush();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            textures.bindTexture(world || factor > 2 ? ATLAS_4X : ATLAS_2X);
            t.startDrawingQuads();
            if (world) t.setNormal(0, -1, 0);
            batch = SPRITES;
        }
        return t;
    }

    /** Draws the open batch, if any. */
    public void flush() {
        if (batch == NONE) return;
        batch = NONE;
        Tessellator.instance.draw();
    }

    private void color(Tessellator t, int argb) {
        int a = (int) ((argb >>> 24) * opacity), r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        if (blend == Blend.MULTIPLY) {
            // multiply has no alpha: fade the colour towards white instead
            float f = a / 255f;
            r = 255 - (int) ((255 - r) * f);
            g = 255 - (int) ((255 - g) * f);
            b = 255 - (int) ((255 - b) * f);
            a = 255;
        }
        t.setColorRGBA(r, g, b, a);
    }

    private void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int c0, int c1,
        int c2, int c3) {
        Tessellator t = shapes();
        color(t, c0);
        t.addVertex(x0, y0, 0);
        color(t, c1);
        t.addVertex(x1, y1, 0);
        color(t, c2);
        t.addVertex(x2, y2, 0);
        color(t, c3);
        t.addVertex(x3, y3, 0);
    }

    private void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int c) {
        quad(x0, y0, x1, y1, x2, y2, x3, y3, c, c, c, c);
    }

    /** A convex outline as quads fanned from its first point, coloured by a vertical gradient. */
    private void fan(float[] p, float y, float h, int top, int bottom) {
        int n = p.length / 2;
        for (int i = 1; i + 1 < n; i += 2) {
            int j = Math.min(i + 2, n - 1);
            quad(
                p[0],
                p[1],
                p[2 * i],
                p[2 * i + 1],
                p[2 * i + 2],
                p[2 * i + 3],
                p[2 * j],
                p[2 * j + 1],
                shade(top, bottom, p[1], y, h),
                shade(top, bottom, p[2 * i + 1], y, h),
                shade(top, bottom, p[2 * i + 3], y, h),
                shade(top, bottom, p[2 * j + 1], y, h));
        }
    }

    private static int shade(int top, int bottom, float py, float y, float h) {
        return top == bottom || h <= 0 ? top : ColorMath.mix(top, bottom, (py - y) / h);
    }

    /** The band between two outlines with the same number of points, as quads. */
    private void band(float[] outer, float[] inner, int argb, boolean closed) {
        int n = outer.length / 2;
        for (int i = 0; i < (closed ? n : n - 1); i++) {
            int k = (i + 1) % n;
            quad(
                outer[2 * i],
                outer[2 * i + 1],
                outer[2 * k],
                outer[2 * k + 1],
                inner[2 * k],
                inner[2 * k + 1],
                inner[2 * i],
                inner[2 * i + 1],
                argb);
        }
    }

    // ------------------------------------------------------------------ Canvas

    @Override
    public void setOpacity(float opacity) {
        this.opacity = Math.max(0, Math.min(1, opacity));
    }

    @Override
    public void surface(boolean solid) {
        if (!world) return;
        flush();
        GL11.glDepthMask(solid);
        if (solid) GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        else {
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(-1, -4);
        }
    }

    @Override
    public void fill(float x, float y, float w, float h, int argb) {
        gradient(x, y, w, h, argb, argb);
    }

    @Override
    public void gradient(float x, float y, float w, float h, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        quad(x, y + h, x + w, y + h, x + w, y, x, y, bottom, bottom, top, top);
    }

    @Override
    public void round(float x, float y, float w, float h, float r, int argb) {
        roundGradient(x, y, w, h, r, argb, argb);
    }

    @Override
    public void roundGradient(float x, float y, float w, float h, float r, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        fan(UiGeo.rounded(x, y, w, h, r, UiGeo.segments(r, factor)), y, h, top, bottom);
    }

    @Override
    public void roundStroke(float x, float y, float w, float h, float r, float th, int argb) {
        if (w <= 0 || h <= 0) return;
        int seg = UiGeo.segments(r, factor);
        band(
            UiGeo.rounded(x, y, w, h, r, seg),
            UiGeo.rounded(x + th, y + th, w - 2 * th, h - 2 * th, Math.max(0, r - th), seg),
            argb,
            true);
    }

    @Override
    public void circle(float cx, float cy, float r, int argb) {
        radial(cx, cy, r, argb, argb);
    }

    @Override
    public void line(float[] xs, float[] ys, float width, int argb) {
        int n = Math.min(xs.length, ys.length);
        if (n < 2) return;
        float hw = width / 2;
        for (int i = 0; i < n - 1; i++) segment(xs[i], ys[i], xs[i + 1], ys[i + 1], hw, argb);
        if (n <= 64) for (int i = 1; i < n - 1; i++) circle(xs[i], ys[i], hw, argb);
    }

    private void segment(float x0, float y0, float x1, float y1, float hw, int argb) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) return;
        float nx = -dy / len * hw, ny = dx / len * hw;
        quad(x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny, argb);
    }

    @Override
    public void area(float[] xs, float[] ys, float baseY, int top, int bottom) {
        int n = Math.min(xs.length, ys.length);
        for (int i = 0; i + 1 < n; i++)
            quad(xs[i], ys[i], xs[i + 1], ys[i + 1], xs[i + 1], baseY, xs[i], baseY, top, top, bottom, bottom);
    }

    @Override
    public float text(String s, float x, float y, int argb, float scale) {
        if (s == null || s.isEmpty()) return 0;
        flush();
        int c = ColorMath.alpha(argb, opacity);
        // the font renderer treats an alpha below 4 as fully opaque
        if ((c >>> 24) < 5) return width(s, scale);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        if (world) GL11.glNormal3f(0, -1, 0);
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        if (scale != 1) GL11.glScalef(scale, scale, 1);
        font.drawString(s, 0, 0, c, false);
        GL11.glPopMatrix();
        return font.getStringWidth(s) * scale;
    }

    @Override
    public float width(String s, float scale) {
        return s == null ? 0 : font.getStringWidth(s) * scale;
    }

    @Override
    public void clip(float x, float y, float w, float h) {
        if (world) return;
        flush();
        xf.clip(x, y, w, h);
        scissor();
    }

    @Override
    public void unclip() {
        if (world) return;
        flush();
        xf.unclip();
        scissor();
    }

    private void scissor() {
        float[] box = xf.clipBox();
        if (box == null) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            return;
        }
        int[] s = Xform.scissor(box, factor, displayHeight);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(s[0], s[1], s[2], s[3]);
    }

    // ------------------------------------------------------------------ FluxCanvas

    @Override
    public void chamfer(float x, float y, float w, float h, float cut, int argb) {
        chamferGradient(x, y, w, h, cut, argb, argb);
    }

    @Override
    public void chamferGradient(float x, float y, float w, float h, float cut, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        fan(UiGeo.chamfer(x, y, w, h, cut, Tok.BEVEL), y, h, top, bottom);
    }

    @Override
    public void chamferStroke(float x, float y, float w, float h, float cut, float t, int argb) {
        if (w <= 0 || h <= 0) return;
        band(UiGeo.chamfer(x, y, w, h, cut, Tok.BEVEL), UiGeo.chamferInner(x, y, w, h, cut, Tok.BEVEL, t), argb, true);
    }

    @Override
    public void arc(float cx, float cy, float r0, float r1, float a0, float a1, int c0, int c1) {
        int n = UiGeo.arcSteps(a0, a1);
        for (int i = 0; i < n; i++) {
            float b0 = a0 + (a1 - a0) * i / n, b1 = a0 + (a1 - a0) * (i + 1) / n;
            int k0 = ColorMath.mix(c0, c1, i / (float) n), k1 = ColorMath.mix(c0, c1, (i + 1) / (float) n);
            float[] o0 = UiGeo.polar(cx, cy, r1, b0), o1 = UiGeo.polar(cx, cy, r1, b1);
            float[] i0 = UiGeo.polar(cx, cy, r0, b0), i1 = UiGeo.polar(cx, cy, r0, b1);
            quad(o0[0], o0[1], o1[0], o1[1], i1[0], i1[1], i0[0], i0[1], k0, k1, k1, k0);
        }
    }

    @Override
    public void radial(float cx, float cy, float r, int center, int edge) {
        for (int i = 0; i < 24; i++) {
            float[] p = UiGeo.polar(cx, cy, r, i * 15f), q = UiGeo.polar(cx, cy, r, (i + 1) * 15f);
            quad(cx, cy, p[0], p[1], q[0], q[1], q[0], q[1], center, edge, edge, edge);
        }
    }

    @Override
    public void dash(float[] xs, float[] ys, float width, float on, float off, float phase, int argb) {
        List<float[]> d = UiGeo.dashes(xs, ys, on, off, phase);
        for (float[] s : d) segment(s[0], s[1], s[2], s[3], width / 2, argb);
    }

    private void uvQuad(float x, float y, float w, float h, int u, int v, int uw, int vh, int argb) {
        if (w <= 0 || h <= 0) return;
        Tessellator t = sprites();
        float s = 1f / AtlasMap.SIZE;
        float u0 = u * s, v0 = v * s, u1 = (u + uw) * s, v1 = (v + vh) * s;
        color(t, argb);
        t.addVertexWithUV(x, y + h, 0, u0, v1);
        t.addVertexWithUV(x + w, y + h, 0, u1, v1);
        t.addVertexWithUV(x + w, y, 0, u1, v0);
        t.addVertexWithUV(x, y, 0, u0, v0);
    }

    @Override
    public void sprite(int id, float x, float y, float w, float h, int argb) {
        uvQuad(x, y, w, h, AtlasMap.x(id), AtlasMap.y(id), AtlasMap.w(id), AtlasMap.h(id), argb);
    }

    @Override
    public void nine(int id, float x, float y, float w, float h, int argb) {
        int b = AtlasMap.border(id);
        if (b <= 0) {
            sprite(id, x, y, w, h, argb);
            return;
        }
        int u = AtlasMap.x(id), v = AtlasMap.y(id), uw = AtlasMap.w(id), vh = AtlasMap.h(id);
        float g = Math.min(b / 4f, Math.min(w, h) / 2);
        float[] xs = { x, x + g, x + w - g, x + w }, ys = { y, y + g, y + h - g, y + h };
        int[] us = { u, u + b, u + uw - b, u + uw }, vs = { v, v + b, v + vh - b, v + vh };
        for (int j = 0; j < 3; j++) for (int i = 0; i < 3; i++) uvQuad(
            xs[i],
            ys[j],
            xs[i + 1] - xs[i],
            ys[j + 1] - ys[j],
            us[i],
            vs[j],
            us[i + 1] - us[i],
            vs[j + 1] - vs[j],
            argb);
    }

    @Override
    public void blend(Blend mode) {
        if (mode == blend) return;
        flush();
        blend = mode;
        applyBlend();
    }

    private void applyBlend() {
        switch (blend) {
            case ADD -> GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            case MULTIPLY -> GL11.glBlendFunc(GL11.GL_DST_COLOR, GL11.GL_ZERO);
            default -> GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    @Override
    public float mono(String s, float x, float y, int size, int argb, Align align, int tracking) {
        if (s == null || s.isEmpty()) return 0;
        int width = FluxMono.width(s, size, tracking), adv = FluxMono.advance(size) + tracking;
        float x0 = align == Align.CENTER ? x - width / 2f : align == Align.RIGHT ? x - width : x;
        boolean large = FluxMono.large(size);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            float gx = x0 + i * adv;
            int li = large ? FluxMono.largeIndex(c) : -1;
            if (li >= 0) {
                uvQuad(
                    gx,
                    y,
                    FluxMono.glyphWidth(size),
                    size,
                    AtlasMap.largeX(li),
                    AtlasMap.largeY(li),
                    AtlasMap.LARGE_W,
                    AtlasMap.LARGE_H,
                    argb);
                continue;
            }
            int si = FluxMono.smallIndex(c);
            if (si <= 0) continue; // a space, or no glyph
            uvQuad(
                gx,
                y,
                size * (float) AtlasMap.SMALL_W / AtlasMap.SMALL_H,
                size,
                AtlasMap.smallX(si),
                AtlasMap.smallY(si),
                AtlasMap.SMALL_W,
                AtlasMap.SMALL_H,
                argb);
        }
        return width;
    }

    @Override
    public void item(ItemStack stack, float x, float y, float size, boolean overlay) {
        if (stack == null || stack.getItem() == null) return;
        flush();
        if (items == null) items = new RenderItem();
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        GL11.glScalef(size / 16f, size / 16f, 1);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.enableGUIStandardItemLighting();
        float z = items.zLevel;
        try {
            items.zLevel = Tok.Z_ITEM;
            items.renderItemAndEffectIntoGUI(font, textures, stack, 0, 0);
            if (overlay) {
                items.zLevel = Tok.Z_COUNT;
                items.renderItemOverlayIntoGUI(font, textures, stack, 0, 0);
            }
        } finally {
            items.zLevel = z;
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            if (!depthKept) GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            applyBlend();
            GL11.glPopMatrix();
        }
    }

    @Override
    public void push(float dx, float dy, float s) {
        flush();
        xf.push(dx, dy, s);
        GL11.glPushMatrix();
        GL11.glTranslatef(dx, dy, 0);
        if (s != 1) GL11.glScalef(s, s, 1);
    }

    @Override
    public void pop() {
        flush();
        xf.pop();
        GL11.glPopMatrix();
    }

    @Override
    public void overlay(Runnable layer) {
        flush();
        GL11.glPushMatrix();
        GL11.glTranslatef(0, 0, Tok.Z_OVERLAY);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        depthKept = true;
        try {
            layer.run();
            flush();
        } finally {
            GL11.glPopMatrix();
        }
    }
}
