package com.fluxecho.client.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fluxecho.client.ui.FluxCanvas.Align;
import com.fluxecho.logic.ui.Anim;
import com.fluxecho.logic.ui.AtlasMap;
import com.fluxecho.logic.ui.ColorMath;
import com.fluxecho.logic.ui.FluxMono;
import com.fluxecho.logic.ui.Fmt;
import com.fluxecho.logic.ui.Lay;
import com.fluxecho.logic.ui.Lay.Frame;
import com.fluxecho.logic.ui.TextModel.Style;
import com.fluxecho.logic.ui.TextModel.Surface;
import com.fluxecho.logic.ui.Tok;
import com.fluxecho.logic.ui.Tok.Building;
import com.fluxecho.logic.ui.Tok.Constellation;
import com.fluxecho.logic.ui.Tok.Hue;

/**
 * The token sample sheet of U0 (ui/FINAL.md section 6.1: "colour card PNG, with the section remap before and after"):
 * every colour token with its value, the semantic hues on their tints, the nine constellations, the 23 building rows
 * with their sigils, section codes on paper before and after the remap, the type scale and FluxMono, the shape
 * vocabulary, every icon, the easings and the three breakpoints, drawn with {@link J2dFluxCanvas} to
 * {@code build/previews/0.17.0/ui-tokens.png}; and the atlas on a dark ground to {@code ui-atlas.png}. Text uses
 * Minecraft's font when the vanilla jar is in the Gradle cache, grey bars otherwise.
 */
@Tag("slow")
class UiTokensPreviewTest {

    static final File JAR = new File(
        System.getProperty("user.home"),
        ".gradle/caches/retro_futura_gradle/mc-vanilla/1.7.10/client.jar");
    static final File OUT = new File("build/previews/0.17.0");
    static final int W = 720, H = 1380, SCALE = 2, M = 16;

    @Test
    void tokenSheet() throws Exception {
        try (ZipFile jar = JAR.isFile() ? new ZipFile(JAR) : null) {
            J2dFluxCanvas c = new J2dFluxCanvas(W, H, SCALE, jar);
            draw(c);
            OUT.mkdirs();
            File f = new File(OUT, "ui-tokens.png");
            ImageIO.write(c.image, "png", f);
            assertTrue(f.length() > 10_000);
        }
        BufferedImage atlas = J2dFluxCanvas.atlas();
        BufferedImage dark = new BufferedImage(atlas.getWidth(), atlas.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dark.createGraphics();
        g.setColor(new Color(Tok.INK_1, true));
        g.fillRect(0, 0, dark.getWidth(), dark.getHeight());
        g.drawImage(atlas, 0, 0, null);
        g.dispose();
        ImageIO.write(dark, "png", new File(OUT, "ui-atlas.png"));
    }

    static String hex(int argb) {
        return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
    }

    static String ratio(double r) {
        return String.format(Locale.ROOT, "%.2f", r);
    }

    static void heading(J2dFluxCanvas c, String zh, String en, float y) {
        Ui.text(c, zh, M, y, Style.H2, Surface.PAPER, Tok.INK_1);
        Ui.eyebrow(c, en, M + 4 + c.width(zh, 1) + 6, y + 1.5f, Tok.INK_3);
        // the tick rule under a header: hairline, a 2 px tick every 8 px
        c.fill(M, y + 11, W - 2 * M, Tok.hairline(SCALE), Tok.LINE_1);
        for (float x = M; x <= W - M; x += Tok.TICK_EVERY)
            c.fill(x, y + 11, Tok.hairline(SCALE), Tok.TICK_HEIGHT, Tok.LINE_1);
    }

    static void swatch(J2dFluxCanvas c, float x, float y, float w, float h, int argb) {
        c.chamfer(x, y, w, h, Tok.CUT_CHIP, argb);
        c.chamferStroke(x, y, w, h, Tok.CUT_CHIP, Tok.hairline(SCALE), ColorMath.alpha(Tok.INK_0, 0.18f));
    }

    static void draw(J2dFluxCanvas c) {
        c.gradient(0, 0, W, H, Tok.PAPER_2, Tok.PAPER_FLOOR);
        // a panel the sheet sits on
        c.nine(AtlasMap.S_SHADOW_PANEL, 4, 4 + Tok.SHADOW_DY, W - 8, H - 8, Tok.SHADOW);
        c.chamferGradient(8, 8, W - 16, H - 16, Tok.CUT_WINDOW, Tok.PANEL_TOP, Tok.PANEL_BOTTOM);
        c.chamferStroke(8, 8, W - 16, H - 16, Tok.CUT_WINDOW, Tok.hairline(SCALE), Tok.LINE_1);
        c.fill(10, 12, Tok.PLATE_BAR_W, Tok.PLATE_BAR_H * 2, Hue.FLUX.base);
        Ui.text(c, "晴空星盘 · 令牌样张", M, 14, Style.H1, Surface.PAPER, Tok.INK_0);
        Ui.eyebrow(c, "UI tokens · 0.17.0 · U0 · M = 427x240", M, 34, Tok.INK_3);
        neutrals(c, 48);
        hues(c, 150);
        constellations(c, 298);
        buildings(c, 382);
        sections(c, 676);
        type(c, 822);
        shapes(c, 940);
        icons(c, 1060);
        motion(c, 1240);
    }

    static void neutrals(J2dFluxCanvas c, float y) {
        heading(c, "中性色", "Neutrals · porcelain and ink", y);
        List<Object[]> named = Tok.named();
        float x = M;
        for (int i = 0; i < 11; i++) {
            int col = (int) named.get(i)[1];
            swatch(c, x, y + 18, 56, 22, col);
            c.mono((String) named.get(i)[0], x, y + 43, 6, Tok.INK_2, Align.LEFT);
            c.mono(hex(col), x, y + 51, 6, Tok.INK_3, Align.LEFT);
            if (i >= 7) c.mono(
                ratio(ColorMath.contrast(col, Tok.PAPER_FLOOR)) + " on floor",
                x,
                y + 58,
                6,
                Tok.INK_3,
                Align.LEFT);
            x += 60;
        }
        // the light set on the ink card
        float y2 = y + 70;
        c.chamfer(M - 2, y2 - 3, 7 * 64 + 4, 22, Tok.CUT_CARD, Tok.INK_CARD);
        x = M;
        for (int i = 11; i < named.size(); i++) {
            int col = (int) named.get(i)[1];
            c.chamfer(x, y2, 12, 12, Tok.CUT_CHIP, col);
            c.mono((String) named.get(i)[0], x + 15, y2 + 1, 6, Tok.LUM_TEXT, Align.LEFT);
            c.mono(hex(col), x + 15, y2 + 8, 6, Tok.LUM_TEXT_2, Align.LEFT);
            x += 64;
        }
        swatch(c, 482, y2 - 2, 56, 18, Tok.FLUX_600);
        Ui.text(c, "主按钮", 488, y2 + 3, Style.H2, Surface.INK, Tok.PAPER_0);
        c.mono(
            "flux-600 " + ratio(ColorMath.contrast(Tok.PAPER_0, Tok.FLUX_600)),
            542,
            y2 + 4,
            6,
            Tok.INK_2,
            Align.LEFT);
    }

    static void hues(J2dFluxCanvas c, float y) {
        heading(c, "语义色", "Semantic hues · ink / base / tint / lum", y);
        String[] zh = { "通量", "回响", "长庚金", "成", "待", "停", "闲" };
        float ry = y + 18;
        for (Hue h : Hue.values()) {
            float x = M;
            Ui.text(c, zh[h.ordinal()], x, ry + 3, Style.B, Surface.PAPER, Tok.INK_1);
            c.mono(
                h.name()
                    .toLowerCase(Locale.ROOT),
                x + 36,
                ry + 4,
                6,
                Tok.INK_3,
                Align.LEFT);
            x = 84;
            int[] grades = { h.ink, h.base, h.tint };
            for (int g : grades) {
                swatch(c, x, ry, 44, 14, g);
                c.mono(hex(g), x + 47, ry + 4, 6, Tok.INK_3, Align.LEFT);
                x += 82;
            }
            c.chamfer(x, ry, 44, 14, Tok.CUT_CHIP, Tok.INK_CARD);
            c.fill(x + 4, ry + 4, 36, 6, h.lum);
            c.mono(hex(h.lum), x + 47, ry + 4, 6, Tok.INK_3, Align.LEFT);
            x += 82;
            // ink on its tint, the way a chip or a banner uses it
            c.chamfer(x, ry, 108, 14, Tok.CUT_CHIP, h.tint);
            c.fill(x, ry, 2, 14, h.base);
            Ui.text(c, "运转 ·", x + 5, ry + 3, Style.B, Surface.PAPER, h.ink);
            Ui.value(c, "384", Fmt.EU_T, x + 38, ry + 3, 8, h.ink, h.ink);
            c.mono(
                ratio(ColorMath.contrast(h.ink, Tok.PAPER_FLOOR)) + " / " + ratio(ColorMath.contrast(h.ink, h.tint)),
                x + 112,
                ry + 4,
                6,
                Tok.INK_3,
                Align.LEFT);
            ry += 17;
        }
    }

    static void constellations(J2dFluxCanvas c, float y) {
        heading(c, "星座", "Constellations · on the chart paper", y);
        c.chamfer(M, y + 16, W - 2 * M, 56, Tok.CUT_CARD, Tok.STAR_PAPER);
        c.fill(M, y + 58, W - 2 * M, 14, Tok.PAPER_3);
        float x = M + 8;
        for (Constellation s : Constellation.values()) {
            float cx = x + 30, cy = s == Constellation.RECALL ? y + 64 : y + 34;
            c.radial(cx, cy, 16, ColorMath.alpha(s.wash, 0.9f), ColorMath.alpha(s.wash, 0));
            c.sprite(AtlasMap.S_STAR_4, cx - 8, cy - 8, 16, 16, s.ink);
            c.mono(s.name(), cx, y + 46, 6, s.ink, Align.CENTER);
            c.mono(hex(s.ink), cx, y + 53, 6, Tok.INK_3, Align.CENTER);
            c.chamfer(x + 48, y + 20, 10, 10, Tok.CUT_CHIP, Tok.NIGHT_0);
            c.circle(x + 53, y + 25, 3, s.lum);
            x += 76;
        }
    }

    static void buildings(J2dFluxCanvas c, float y) {
        heading(c, "建筑铭色", "Buildings · lum = style 3.6 / line / ink / tint", y);
        List<Building> all = Tok.buildings();
        for (int i = 0; i < all.size(); i++) {
            Building b = all.get(i);
            float x = i < 12 ? M : M + 348, ry = y + 18 + (i % 12) * 22.5f;
            c.chamfer(x, ry, 20, 20, Tok.CUT_CHIP, b.tint);
            c.sprite(b.sigil, x + 2, ry + 2, 16, 16, b.line);
            c.chamfer(x + 24, ry + 2, 16, 16, Tok.CUT_CHIP, Tok.INK_CARD);
            c.sprite(b.sigil, x + 26, ry + 4, 12, 12, b.lum);
            int[] grades = { b.line, b.ink, b.tint };
            float sx = x + 44;
            for (int g : grades) {
                swatch(c, sx, ry + 4, 12, 12, g);
                sx += 15;
            }
            c.fill(x + 90, ry + 3, Tok.PLATE_BAR_W, Tok.PLATE_BAR_H, b.line);
            Ui.text(c, b.toString(), x + 96, ry + 2, Style.H2, Surface.PAPER, b.ink);
            c.mono(
                hex(b.lum) + "  "
                    + ratio(ColorMath.contrast(b.ink, Tok.PAPER_FLOOR))
                    + " / "
                    + ratio(ColorMath.contrast(b.line, Tok.PAPER_FLOOR)),
                x + 96,
                ry + 12,
                6,
                Tok.INK_3,
                Align.LEFT);
        }
    }

    static void sections(J2dFluxCanvas c, float y) {
        heading(c, "§ 颜色码 · 纸面重映射", "Section codes · vanilla / on paper / on the ink card", y);
        String codes = Tok.SECTION_CODES;
        for (int i = 0; i < 16; i++) {
            char code = codes.charAt(i);
            float x = i < 8 ? M : M + 348, ry = y + 18 + (i % 8) * 15;
            c.mono("§" + code, x, ry + 4, 8, Tok.INK_3, Align.LEFT);
            c.text("回响 Echo", x + 16, ry + 2, Tok.vanillaSection(code), 1);
            c.mono(
                ratio(ColorMath.contrast(Tok.vanillaSection(code), Tok.PAPER_1)),
                x + 72,
                ry + 4,
                6,
                Hue.ERR.ink,
                Align.LEFT);
            Ui.text(c, "§" + code + "回响 Echo", x + 100, ry + 2, Style.B, Surface.PAPER, Tok.INK_1);
            c.mono(
                ratio(ColorMath.contrast(Tok.sectionOnPaper(code), Tok.PAPER_FLOOR)),
                x + 172,
                ry + 4,
                6,
                Tok.INK_3,
                Align.LEFT);
            c.chamfer(x + 200, ry, 110, 13, Tok.CUT_CHIP, Tok.INK_CARD);
            Ui.text(c, "§" + code + "回响 Echo", x + 205, ry + 2, Style.B, Surface.INK, Tok.LUM_TEXT);
        }
    }

    static void type(J2dFluxCanvas c, float y) {
        heading(c, "字", "Type · hanzi 1x / 2x only, FluxMono 6 8 12 16 24", y);
        Ui.text(c, "魔力泉阵", M, y + 18, Style.H1, Surface.PAPER, Tok.INK_0);
        Ui.eyebrow(c, "Mana fountain array · EV · 09", M, y + 38, Tok.INK_3);
        Ui.text(c, "营造 · 从背包补给", M, y + 48, Style.H2, Surface.PAPER, Tok.INK_1);
        Ui.text(c, "补 12 块钛板就能开始。§e缺料§r 的行排在最前。", M, y + 62, Style.B, Surface.PAPER, Tok.INK_1);
        Ui.text(c, "说明、单位与坐标轴用 ink-3。", M, y + 75, Style.B, Surface.PAPER, Tok.INK_3);
        String fit = com.fluxecho.logic.ui.TextModel.fit("这一行太长了，放不下的部分用省略号截断，悬停看全文", 150);
        Ui.text(c, fit, M, y + 88, Style.B, Surface.PAPER, Tok.INK_2);
        float x = 260;
        int[] sizes = FluxMono.SIZES;
        String[] samples = { "0123456789 EU/t FC/s D3 · 23:40 ±×°", "512 2.05k 30.7k 1.97M 2.10G 7.3% 14:20",
            "384 1:57:03", "99% IV", "2.05k" };
        float ry = y + 18;
        for (int i = 0; i < sizes.length; i++) {
            c.mono(Integer.toString(sizes[i]), x, ry + sizes[i] - 6, 6, Tok.INK_3, Align.LEFT);
            c.mono(samples[i], x + 14, ry, sizes[i], Tok.INK_1, Align.LEFT);
            ry += sizes[i] + 5;
        }
        Ui.value(c, Fmt.si(384), Fmt.EU_T, 560, y + 18, 24, Tok.INK_1, Tok.INK_3);
        Ui.value(c, Fmt.si(1966080), Fmt.FC_S, 560, y + 48, 16, Hue.ECHO.ink, Tok.INK_3);
        Ui.value(c, Fmt.percent(0.073), null, 560, y + 70, 12, Hue.OK.ink, Tok.INK_3);
        c.mono(Fmt.gameTime(65667), 560, y + 88, 8, Tok.INK_2, Align.LEFT);
    }

    static void shapes(J2dFluxCanvas c, float y) {
        heading(c, "形", "Shapes · cut 6 4 3 2, hairline, shadow, ink card, arcs", y);
        float x = M;
        int[] cuts = { Tok.CUT_WINDOW, Tok.CUT_CARD, Tok.CUT_BUTTON, Tok.CUT_CHIP };
        float[] widths = { 96, 80, 60, 40 }, heights = { 60, 44, 16, 11 };
        for (int i = 0; i < 4; i++) {
            c.nine(
                AtlasMap.S_SHADOW_PANEL,
                x - 8,
                y + 18 - 8 + Tok.SHADOW_DY,
                widths[i] + 16,
                heights[i] + 16,
                Tok.SHADOW);
            c.chamferGradient(x, y + 18, widths[i], heights[i], cuts[i], Tok.PANEL_TOP, Tok.PANEL_BOTTOM);
            c.chamferStroke(x, y + 18, widths[i], heights[i], cuts[i], Tok.hairline(SCALE), Tok.LINE_1);
            c.fill(x + widths[i] - cuts[i] / 2f - 1.5f, y + 18 + 1, 2, 2, Hue.OK.base);
            c.mono("cut " + cuts[i], x, y + 18 + heights[i] + 4, 6, Tok.INK_3, Align.LEFT);
            x += widths[i] + 16;
        }
        // buttons: primary, secondary, danger
        float by = y + 92;
        c.chamfer(M, by, 64, 16, Tok.CUT_BUTTON, Tok.FLUX_600);
        Ui.text(c, "开始研究", M + 8, by + 4, Style.H2, Surface.INK, Tok.PAPER_0);
        c.chamfer(M + 72, by, 64, 16, Tok.CUT_BUTTON, Tok.PAPER_0);
        c.chamferStroke(M + 72, by, 64, 16, Tok.CUT_BUTTON, Tok.STROKE, Tok.LINE_2);
        Ui.text(c, "从背包补给", M + 80, by + 4, Style.H2, Surface.PAPER, Tok.INK_1);
        c.chamfer(M + 144, by, 64, 16, Tok.CUT_BUTTON, Hue.ERR.ink);
        Ui.text(c, "全部取出", M + 152, by + 4, Style.H2, Surface.INK, Tok.PAPER_0);
        // ink card
        float ix = 330;
        c.nine(AtlasMap.S_SHADOW_CARD, ix - 8, y + 18 - 8 + Tok.SHADOW_DY, 156, 66, Tok.SHADOW);
        c.chamfer(ix, y + 18, 140, 50, Tok.CUT_CARD, Tok.INK_CARD);
        c.fill(ix, y + 18, 2, 50, Hue.FLUX.lum);
        Ui.text(c, "§b天璇§r · 已点亮", ix + 8, y + 24, Style.H2, Surface.INK, Tok.LUM_TEXT);
        c.fill(ix + 8, y + 37, 124, Tok.hairline(SCALE), Tok.INK_CARD_RULE);
        Ui.text(c, "研究花费 2.05k EU", ix + 8, y + 41, Style.B, Surface.INK, Tok.LUM_TEXT_2);
        Ui.value(c, "92", "%", ix + 8, y + 53, 8, Tok.LUM_TEXT, Tok.LUM_IDLE);
        // a progress ring, a gauge, a glow, a flowing dash
        float gx = 500, gy = y + 48;
        c.arc(gx, gy, 14, 16, 0, 360, Tok.PAPER_3, Tok.PAPER_3);
        c.arc(gx, gy, 14, 16, 0, 245, Hue.FLUX.base, Hue.FLUX.ink);
        c.mono(Fmt.percent(245 / 360.0), gx, gy - 3, 6, Tok.INK_1, Align.CENTER);
        c.arc(gx + 52, gy + 6, 16, 20, -120, 120, Tok.PAPER_3, Tok.PAPER_3);
        c.arc(gx + 52, gy + 6, 16, 20, -120, 40, Hue.WARN.base, Hue.WARN.base);
        c.blend(FluxCanvas.Blend.MULTIPLY);
        c.sprite(AtlasMap.S_NEBULA_0, gx + 80, gy - 30, 60, 60, Constellation.HULL.wash);
        c.blend(FluxCanvas.Blend.NORMAL);
        c.sprite(AtlasMap.S_DOT_1, gx + 96, gy - 14, 28, 28, Hue.GOLD.base);
        c.dash(
            new float[] { gx + 150, gx + 186 },
            new float[] { gy - 20, gy + 16 },
            1,
            4,
            3,
            Anim.flow(800, Anim.FLOW, 7),
            Hue.ECHO.base);
    }

    static void icons(J2dFluxCanvas c, float y) {
        heading(c, "图标", "Icons · system, domain, building sigils (16 px), micro (8 px)", y);
        for (int i = 0; i < AtlasMap.ICON_COUNT; i++) {
            float x = M + (i % 26) * 26.5f, ry = y + 18 + (i / 26) * 30;
            boolean sigil = AtlasMap.name(i)
                .startsWith("sigil.");
            c.sprite(i, x + 4, ry, 16, 16, sigil ? Hue.FLUX.ink : Tok.INK_2);
            c.mono(Integer.toString(i), x + 12, ry + 18, 6, Tok.INK_3, Align.CENTER);
        }
        for (int i = 0; i < AtlasMap.MICRO_COUNT; i++) {
            float x = M + i * 26.5f, ry = y + 18 + 4 * 30 + 4;
            c.sprite(AtlasMap.ICON_COUNT + i, x + 8, ry, 8, 8, Tok.INK_1);
        }
        float ry = y + 18 + 4 * 30 + 18;
        c.mono("fluxmono " + FluxMono.SMALL.substring(1), M, ry, 6, Tok.INK_2, Align.LEFT);
    }

    static void motion(J2dFluxCanvas c, float y) {
        heading(c, "动效与断点", "Motion · out / inOut / back · breakpoints S M L", y);
        String[] names = { "out", "inOut", "back" };
        for (int k = 0; k < 3; k++) {
            float x = M + k * 100, bx = x, by = y + 22, bw = 80, bh = 44;
            c.chamfer(bx, by, bw, bh, Tok.CUT_CHIP, Tok.PAPER_0);
            float[] xs = new float[41], ys = new float[41];
            for (int i = 0; i <= 40; i++) {
                float t = i / 40f, v = k == 0 ? Anim.out(t) : k == 1 ? Anim.inOut(t) : Anim.back(t);
                xs[i] = bx + 4 + t * (bw - 8);
                ys[i] = by + bh - 6 - v * (bh - 16);
            }
            c.line(xs, ys, 1, Hue.FLUX.ink);
            c.mono(names[k], bx + 4, by + 3, 6, Tok.INK_3, Align.LEFT);
        }
        c.mono(
            "micro " + Anim.MICRO
                + "  short "
                + Anim.SHORT
                + "  medium "
                + Anim.MEDIUM
                + "  long "
                + Anim.LONG
                + "  epic "
                + Anim.EPIC
                + " ms",
            M,
            y + 72,
            6,
            Tok.INK_3,
            Align.LEFT);
        // the console frame at three sizes, a quarter scale
        int[][] screens = { { 320, 240 }, { 427, 240 }, { 640, 360 } };
        float x = 330;
        for (int[] s : screens) {
            Frame f = Lay.console(s[0], s[1]);
            c.push(x, y + 22, 0.25f);
            c.fill(0, 0, s[0], s[1], Tok.PAPER_FLOOR);
            c.fill(f.top.x, f.top.y, f.top.w, f.top.h, Tok.LINE_1);
            c.fill(f.bottom.x, f.bottom.y, f.bottom.w, f.bottom.h, Tok.LINE_1);
            c.fill(f.rail.x, f.rail.y, f.rail.w, f.rail.h, Tok.PAPER_3);
            c.fill(f.content.x, f.content.y, f.content.w, f.content.h, Tok.PAPER_0);
            c.fill(f.drawer.x, f.drawer.y, f.drawer.w, f.drawer.h, ColorMath.alpha(Hue.FLUX.tint, 0.8f));
            c.pop();
            c.mono(f.tier + " " + s[0] + "x" + s[1], x, y + 22 + s[1] / 4f + 3, 6, Tok.INK_2, Align.LEFT);
            x += s[0] / 4f + 12;
        }
    }
}
