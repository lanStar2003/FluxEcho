package com.fluxecho.client.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import net.minecraft.item.ItemStack;

import com.fluxecho.logic.ui.AtlasMap;
import com.fluxecho.logic.ui.ColorMath;
import com.fluxecho.logic.ui.FluxMono;
import com.fluxecho.logic.ui.TextModel;
import com.fluxecho.logic.ui.Tok;
import com.fluxecho.logic.ui.UiGeo;
import com.fluxecho.logic.ui.Xform;
import com.fluxlite.gui.preview.Java2DCanvas;

/**
 * {@link FluxCanvas} in Java2D, for tests and PNG previews (ui/FINAL.md section 2.10): the same geometry as the game
 * canvas ({@link UiGeo}), the same transform and clip arithmetic ({@link Xform}, so a clip cuts where the game's
 * scissor cuts), sprites and FluxMono glyphs taken from the committed atlas picture. Text uses Minecraft's own unicode
 * font through FluxLite's {@link Java2DCanvas} when the vanilla jar is given; without it, text is drawn as grey bars of
 * the model's width, enough for layout.
 */
public final class J2dFluxCanvas implements FluxCanvas {

    public final BufferedImage image;
    private final Graphics2D g;
    private final int scale;
    private final Java2DCanvas font;
    private final Xform xf = new Xform();
    private final Deque<AffineTransform> saved = new ArrayDeque<>();
    private final Map<Long, BufferedImage> tinted = new HashMap<>();
    private float opacity = 1;
    private Blend blend = Blend.NORMAL;
    private static BufferedImage atlas;

    /** A canvas of {@code w x h} GUI pixels at {@code scale} image pixels each; {@code jar} may be null. */
    public J2dFluxCanvas(int w, int h, int scale, ZipFile jar) {
        this.scale = scale;
        if (jar != null) {
            try {
                font = new Java2DCanvas(w, h, scale, jar);
            } catch (Exception e) {
                throw new IllegalStateException("cannot read the vanilla font", e);
            }
            image = font.image;
            g = font.graphics();
        } else {
            font = null;
            image = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_ARGB);
            g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.scale(scale, scale);
        }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    /** The committed 4x atlas, read once. */
    static synchronized BufferedImage atlas() {
        if (atlas == null) {
            try (InputStream in = J2dFluxCanvas.class
                .getResourceAsStream("/assets/fluxecho/textures/gui/ui/atlas@4x.png")) {
                if (in == null) throw new IllegalStateException("the UI atlas is missing; run java tools/UiAtlas.java");
                atlas = ImageIO.read(in);
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return atlas;
    }

    public Graphics2D graphics() {
        return g;
    }

    @Override
    public int factor() {
        return scale;
    }

    private Color c(int argb) {
        return new Color(ColorMath.alpha(argb, opacity), true);
    }

    @Override
    public void setOpacity(float opacity) {
        this.opacity = Math.max(0, Math.min(1, opacity));
        if (font != null) font.setOpacity(this.opacity);
    }

    // ------------------------------------------------------------------ Canvas

    @Override
    public void fill(float x, float y, float w, float h, int argb) {
        if (w <= 0 || h <= 0) return;
        g.setColor(c(argb));
        g.fill(new Rectangle2D.Float(x, y, w, h));
    }

    @Override
    public void gradient(float x, float y, float w, float h, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        g.setPaint(new GradientPaint(0, y, c(top), 0, y + h, c(bottom)));
        g.fill(new Rectangle2D.Float(x, y, w, h));
    }

    private static Path2D.Float outline(float[] p) {
        Path2D.Float path = new Path2D.Float();
        for (int i = 0; i < p.length; i += 2) {
            if (i == 0) path.moveTo(p[0], p[1]);
            else path.lineTo(p[i], p[i + 1]);
        }
        path.closePath();
        return path;
    }

    @Override
    public void round(float x, float y, float w, float h, float r, int argb) {
        roundGradient(x, y, w, h, r, argb, argb);
    }

    @Override
    public void roundGradient(float x, float y, float w, float h, float r, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        g.setPaint(new GradientPaint(0, y, c(top), 0, y + h, c(bottom)));
        g.fill(new RoundRectangle2D.Float(x, y, w, h, 2 * r, 2 * r));
    }

    @Override
    public void roundStroke(float x, float y, float w, float h, float r, float t, int argb) {
        if (w <= 0 || h <= 0) return;
        Area a = new Area(new RoundRectangle2D.Float(x, y, w, h, 2 * r, 2 * r));
        float ri = Math.max(0, r - t);
        a.subtract(new Area(new RoundRectangle2D.Float(x + t, y + t, w - 2 * t, h - 2 * t, 2 * ri, 2 * ri)));
        g.setColor(c(argb));
        g.fill(a);
    }

    @Override
    public void circle(float cx, float cy, float r, int argb) {
        g.setColor(c(argb));
        g.fill(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
    }

    @Override
    public void line(float[] xs, float[] ys, float width, int argb) {
        if (xs.length < 2) return;
        Path2D.Float p = new Path2D.Float();
        p.moveTo(xs[0], ys[0]);
        for (int i = 1; i < Math.min(xs.length, ys.length); i++) p.lineTo(xs[i], ys[i]);
        g.setColor(c(argb));
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.draw(p);
    }

    @Override
    public void area(float[] xs, float[] ys, float baseY, int top, int bottom) {
        int n = Math.min(xs.length, ys.length);
        if (n < 2) return;
        Path2D.Float p = new Path2D.Float();
        p.moveTo(xs[0], baseY);
        float minY = baseY;
        for (int i = 0; i < n; i++) {
            p.lineTo(xs[i], ys[i]);
            minY = Math.min(minY, ys[i]);
        }
        p.lineTo(xs[n - 1], baseY);
        p.closePath();
        g.setPaint(new GradientPaint(0, minY, c(top), 0, baseY, c(bottom)));
        g.fill(p);
    }

    @Override
    public float text(String s, float x, float y, int argb, float textScale) {
        if (s == null || s.isEmpty()) return 0;
        if (font != null) return font.text(s, x, y, argb, textScale);
        // no font: a bar the width the model gives, where the glyphs would be
        float w = TextModel.width(s) * textScale;
        g.setColor(c(ColorMath.alpha(argb, 0.45f)));
        g.fill(new Rectangle2D.Float(x, y + 1 * textScale, w, 6 * textScale));
        return w;
    }

    @Override
    public float width(String s, float textScale) {
        if (font != null) return font.width(s, textScale);
        return TextModel.width(s) * textScale;
    }

    @Override
    public void clip(float x, float y, float w, float h) {
        xf.clip(x, y, w, h);
        applyClip();
    }

    @Override
    public void unclip() {
        xf.unclip();
        applyClip();
    }

    private void applyClip() {
        float[] box = xf.clipBox();
        AffineTransform t = g.getTransform();
        g.setTransform(new AffineTransform());
        if (box == null) g.setClip(null);
        else {
            int[] d = Xform.device(box, scale);
            g.setClip(new Rectangle(d[0], d[1], d[2], d[3]));
        }
        g.setTransform(t);
    }

    // ------------------------------------------------------------------ FluxCanvas

    @Override
    public void chamfer(float x, float y, float w, float h, float cut, int argb) {
        chamferGradient(x, y, w, h, cut, argb, argb);
    }

    @Override
    public void chamferGradient(float x, float y, float w, float h, float cut, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        g.setPaint(new GradientPaint(0, y, c(top), 0, y + h, c(bottom)));
        g.fill(outline(UiGeo.chamfer(x, y, w, h, cut, Tok.BEVEL)));
    }

    @Override
    public void chamferStroke(float x, float y, float w, float h, float cut, float t, int argb) {
        if (w <= 0 || h <= 0) return;
        Area a = new Area(outline(UiGeo.chamfer(x, y, w, h, cut, Tok.BEVEL)));
        a.subtract(new Area(outline(UiGeo.chamferInner(x, y, w, h, cut, Tok.BEVEL, t))));
        g.setColor(c(argb));
        g.fill(a);
    }

    @Override
    public void arc(float cx, float cy, float r0, float r1, float a0, float a1, int c0, int c1) {
        int n = UiGeo.arcSteps(a0, a1);
        for (int i = 0; i < n; i++) {
            float b0 = a0 + (a1 - a0) * i / n, b1 = a0 + (a1 - a0) * (i + 1) / n;
            float[] o0 = UiGeo.polar(cx, cy, r1, b0), o1 = UiGeo.polar(cx, cy, r1, b1);
            float[] i0 = UiGeo.polar(cx, cy, r0, b0), i1 = UiGeo.polar(cx, cy, r0, b1);
            g.setColor(c(ColorMath.mix(c0, c1, (i + 0.5f) / n)));
            // a hair of overlap so the antialiased seams between pieces do not show
            g.fill(outline(new float[] { o0[0], o0[1], o1[0], o1[1], i1[0], i1[1], i0[0], i0[1] }));
            g.setStroke(new BasicStroke(0.25f));
            g.draw(outline(new float[] { o1[0], o1[1], i1[0], i1[1] }));
        }
    }

    @Override
    public void radial(float cx, float cy, float r, int center, int edge) {
        if (r <= 0) return;
        g.setPaint(
            new RadialGradientPaint(
                new Point2D.Float(cx, cy),
                r,
                new float[] { 0, 1 },
                new Color[] { c(center), c(edge) },
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
    }

    @Override
    public void dash(float[] xs, float[] ys, float width, float on, float off, float phase, int argb) {
        g.setColor(c(argb));
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        for (float[] s : UiGeo.dashes(xs, ys, on, off, phase)) {
            Path2D.Float p = new Path2D.Float();
            p.moveTo(s[0], s[1]);
            p.lineTo(s[2], s[3]);
            g.draw(p);
        }
    }

    /** A rectangle of the atlas in one colour (the atlas is white; its alpha is the shape). */
    private BufferedImage tint(int u, int v, int w, int h, int argb) {
        long key = ((long) u << 48) ^ ((long) v << 36) ^ ((long) w << 24) ^ ((long) h << 12) ^ (argb * 0x9E3779B1L);
        return tinted.computeIfAbsent(key, k -> {
            BufferedImage src = atlas();
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            int a0 = argb >>> 24, rgb = argb & 0xFFFFFF;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int a = (src.getRGB(u + x, v + y) >>> 24) * a0 / 255;
                if (a > 0) out.setRGB(x, y, a << 24 | rgb);
            }
            return out;
        });
    }

    private void blit(int u, int v, int uw, int vh, float x, float y, float w, float h, int argb) {
        if (w <= 0 || h <= 0 || uw <= 0 || vh <= 0) return;
        BufferedImage img = tint(u, v, uw, vh, ColorMath.alpha(argb, opacity));
        AffineTransform t = new AffineTransform(w / uw, 0, 0, h / vh, x, y);
        g.drawImage(img, t, null);
    }

    @Override
    public void sprite(int id, float x, float y, float w, float h, int argb) {
        blit(AtlasMap.x(id), AtlasMap.y(id), AtlasMap.w(id), AtlasMap.h(id), x, y, w, h, argb);
    }

    @Override
    public void nine(int id, float x, float y, float w, float h, int argb) {
        int b = AtlasMap.border(id);
        if (b <= 0) {
            sprite(id, x, y, w, h, argb);
            return;
        }
        int u = AtlasMap.x(id), v = AtlasMap.y(id), uw = AtlasMap.w(id), vh = AtlasMap.h(id);
        float gb = Math.min(b / 4f, Math.min(w, h) / 2);
        float[] xs = { x, x + gb, x + w - gb, x + w }, ys = { y, y + gb, y + h - gb, y + h };
        int[] us = { u, u + b, u + uw - b, u + uw }, vs = { v, v + b, v + vh - b, v + vh };
        for (int j = 0; j < 3; j++) for (int i = 0; i < 3; i++) blit(
            us[i],
            vs[j],
            us[i + 1] - us[i],
            vs[j + 1] - vs[j],
            xs[i],
            ys[j],
            xs[i + 1] - xs[i],
            ys[j + 1] - ys[j],
            argb);
    }

    @Override
    public void blend(Blend mode) {
        blend = mode;
        g.setComposite(mode == Blend.NORMAL ? AlphaComposite.SrcOver : new Mix(mode == Blend.ADD));
    }

    @Override
    public float mono(String s, float x, float y, int size, int argb, Align align, int tracking) {
        if (s == null || s.isEmpty()) return 0;
        int width = FluxMono.width(s, size, tracking), adv = FluxMono.advance(size) + tracking;
        float x0 = align == Align.CENTER ? x - width / 2f : align == Align.RIGHT ? x - width : x;
        boolean large = FluxMono.large(size);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            float gx = x0 + i * adv;
            int li = large ? FluxMono.largeIndex(ch) : -1;
            if (li >= 0) {
                blit(
                    AtlasMap.largeX(li),
                    AtlasMap.largeY(li),
                    AtlasMap.LARGE_W,
                    AtlasMap.LARGE_H,
                    gx,
                    y,
                    FluxMono.glyphWidth(size),
                    size,
                    argb);
                continue;
            }
            int si = FluxMono.smallIndex(ch);
            if (si <= 0) continue;
            blit(
                AtlasMap.smallX(si),
                AtlasMap.smallY(si),
                AtlasMap.SMALL_W,
                AtlasMap.SMALL_H,
                gx,
                y,
                size * (float) AtlasMap.SMALL_W / AtlasMap.SMALL_H,
                size,
                argb);
        }
        return width;
    }

    /** No item renderer here: a slot-coloured square with a question mark. */
    @Override
    public void item(ItemStack stack, float x, float y, float size, boolean overlay) {
        if (stack == null) return;
        round(x + 1, y + 1, size - 2, size - 2, 2, 0xFFB8C6D4);
        mono("?", x + size / 2, y + size / 2 - 4, 8, Tok.INK_1, Align.CENTER);
        if (overlay && stack.stackSize > 1)
            mono(Integer.toString(stack.stackSize), x + size, y + size - 6, FluxMono.CAPTION, Tok.INK_1, Align.RIGHT);
    }

    @Override
    public void push(float dx, float dy, float s) {
        saved.push(g.getTransform());
        g.translate(dx, dy);
        g.scale(s, s);
        xf.push(dx, dy, s);
    }

    @Override
    public void pop() {
        xf.pop();
        g.setTransform(saved.pop());
    }

    @Override
    public void overlay(Runnable layer) {
        layer.run();
    }

    /** Additive and multiply blending for the preview (Java2D has only Porter-Duff). */
    static final class Mix implements Composite {

        final boolean add;

        Mix(boolean add) {
            this.add = add;
        }

        @Override
        public CompositeContext createContext(ColorModel src, ColorModel dst, RenderingHints hints) {
            return new CompositeContext() {

                @Override
                public void dispose() {}

                @Override
                public void compose(Raster s, Raster d, WritableRaster out) {
                    int w = Math.min(s.getWidth(), d.getWidth()), h = Math.min(s.getHeight(), d.getHeight());
                    Object sp = null, dp = null;
                    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                        sp = s.getDataElements(x, y, sp);
                        dp = d.getDataElements(x, y, dp);
                        int a = src.getRGB(sp), b = dst.getRGB(dp);
                        float f = (a >>> 24) / 255f;
                        int r = 0;
                        for (int sh = 0; sh < 24; sh += 8) {
                            int ca = a >> sh & 255, cb = b >> sh & 255, v;
                            if (add) v = Math.min(255, cb + Math.round(ca * f));
                            else v = Math.round(cb * (255 - (255 - ca) * f) / 255f);
                            r |= v << sh;
                        }
                        r |= (b >>> 24) << 24;
                        out.setDataElements(x, y, dst.getDataElements(r, null));
                    }
                }
            };
        }
    }
}
