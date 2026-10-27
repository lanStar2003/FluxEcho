package com.fluxecho.logic.probe;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fluxecho.logic.probe.SwatchLayout.Cell;
import com.fluxecho.logic.probe.SwatchLayout.Column;
import com.fluxecho.logic.probe.SwatchLayout.Glow;
import com.fluxecho.logic.probe.SwatchLayout.Material;
import com.fluxecho.logic.probe.SwatchLayout.Placement;
import com.fluxecho.logic.shape.Family;
import com.fluxecho.logic.shape.Form;
import com.fluxecho.logic.shape.Nbhd;
import com.fluxecho.logic.shape.Overlay;
import com.fluxecho.logic.shape.Quad;
import com.fluxecho.logic.shape.ShapeGeo;
import com.fluxecho.logic.skin.SkinEntry;
import com.fluxecho.logic.skin.SkinPalette;

/**
 * A preview, not a test (0.17.0, style FINAL §8.6, refactor FINAL F06): the swatch wall of {@link SwatchLayout} seen
 * from the operator's place, a little from the right and above so the front layer's relief reads, drawn from
 * {@code ShapeGeo}'s quads with the committed textures and the light each cell gets (the accent seams, the lit and the
 * dark lamps, the keels, the evening gold), the mirror pool before it, the reserved materials' rows hatched in their
 * style §3.5 colours and the fixtures' rows outlined; the light rows' colour names, the panels' materials and the rows'
 * meanings round it. Into {@code build/previews/0.17.0/swatch.png} for the version's preview sheet; it commits nothing
 * and asserts only that the picture was written. Slow, as it draws.
 */
@Tag("slow")
class SwatchPreview {

    private static final Path TEX = Paths.get("src/main/resources/assets/fluxecho/textures/blocks/skin");
    private static final Path OUT = Paths.get("build/previews/0.17.0/swatch.png");
    /** Pixels a block, sub-pieces a quad side, the margins. */
    private static final int S = 40, SUB = 8, LEFT = 190, TOP = 120, BOTTOM = 330, RIGHT = 60;
    /** The frame: the operator at the origin facing north, so the wall's columns run east (+x), its face north. */
    private static final ProbeFrame FRAME = new ProbeFrame(0, 0, 0, 0, -1);
    /** The view direction: north, a little west and down (from the front, right and above). */
    private static final double[] VIEW = norm(-0.20, -0.26, -1);
    private static final double[] SCREEN_R, SCREEN_U;
    private static final Map<String, BufferedImage> CACHE = new HashMap<>();

    static {
        double[] up = { 0, 1, 0 };
        SCREEN_R = norm(cross(VIEW, up));
        SCREEN_U = cross(SCREEN_R, VIEW);
    }

    /** A polygon to fill: its screen points, its distance along the view, its colour and alpha. */
    private static final class Poly {

        final Polygon p;
        final double depth;
        final int rgb;
        final float alpha;

        Poly(Polygon p, double depth, int rgb, float alpha) {
            this.p = p;
            this.depth = depth;
            this.rgb = rgb;
            this.alpha = alpha;
        }
    }

    @Test
    void drawTheSwatchWall() throws IOException {
        Map<Long, Integer> world = new HashMap<>();
        Map<Long, Placement> what = new HashMap<>();
        for (Placement p : SwatchLayout.placements()) {
            int[] at = FRAME.at(p.along, p.right, p.up);
            long k = SkinEntry.pack(at[0], at[1], at[2]);
            int code;
            switch (p.what) {
                case SKIN -> code = Nbhd.ours(p.family.form(p.number), false) | (p.family.ordinal() << 12);
                case VANILLA -> code = Nbhd.SOLID;
                case WATER -> code = Nbhd.WATER;
                default -> code = Nbhd.OPEN;
            }
            world.put(k, code);
            what.put(k, p);
        }
        // the screen box of the scene, to place it
        double[] lo = { Double.MAX_VALUE, Double.MAX_VALUE }, hi = { -Double.MAX_VALUE, -Double.MAX_VALUE };
        for (Placement p : what.values()) {
            int[] at = FRAME.at(p.along, p.right, p.up);
            for (int corner = 0; corner < 8; corner++) {
                double[] s = screen(at[0] + (corner & 1), at[1] + (corner >> 1 & 1), at[2] + (corner >> 2 & 1));
                lo[0] = Math.min(lo[0], s[0]);
                lo[1] = Math.min(lo[1], s[1]);
                hi[0] = Math.max(hi[0], s[0]);
                hi[1] = Math.max(hi[1], s[1]);
            }
        }
        int width = (int) Math.ceil((hi[0] - lo[0]) * S) + LEFT + RIGHT,
            height = (int) Math.ceil((hi[1] - lo[1]) * S) + TOP + BOTTOM;
        double ox = LEFT - lo[0] * S, oy = TOP - lo[1] * S;

        List<Poly> polys = new ArrayList<>();
        for (Map.Entry<Long, Placement> e : what.entrySet()) {
            Placement p = e.getValue();
            int[] at = FRAME.at(p.along, p.right, p.up);
            switch (p.what) {
                case SKIN -> {
                    Form form = p.family.form(p.number);
                    Nbhd n = nbhd(world, at);
                    Glow glow = glowAt(p);
                    String mat = material(p.family, p.number);
                    for (Quad q : ShapeGeo.quads(form, n))
                        add(polys, q, at, texture(mat, form, q), 0, 1f, glow, mat, ox, oy);
                }
                case VANILLA -> {
                    int rgb = vanilla(p.block);
                    for (Quad q : ShapeGeo.item(Form.WHOLE)) add(polys, q, at, null, rgb, 1f, null, null, ox, oy);
                }
                case WATER -> {
                    // the pool's face: a still, sky-reflecting top
                    double y = at[1] + 0.875;
                    polys.add(
                        poly(
                            new double[][] { { at[0], y, at[2] }, { at[0] + 1, y, at[2] }, { at[0] + 1, y, at[2] + 1 },
                                { at[0], y, at[2] + 1 } },
                            0x4C7FC4,
                            0.92f,
                            ox,
                            oy));
                }
                default -> {}
            }
        }
        polys.sort((a, b) -> Double.compare(b.depth, a.depth));

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // an evening sky behind it
        for (int y = 0; y < height; y++) {
            double t = y / (double) height;
            g.setColor(new Color(mix(0x5D7894, 0xC9B9A6, t)));
            g.drawLine(0, y, width, y);
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        for (Poly p : polys) {
            g.setColor(new Color((p.rgb >> 16) & 255, (p.rgb >> 8) & 255, p.rgb & 255, Math.round(p.alpha * 255)));
            g.fillPolygon(p.p);
            g.drawPolygon(p.p);
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        reserved(g, ox, oy);
        labels(g, ox, oy, width, height);
        g.dispose();
        Files.createDirectories(OUT.getParent());
        ImageIO.write(img, "png", OUT.toFile());
        assertTrue(Files.size(OUT) > 0, "the preview was written");
    }

    // ---- the cells

    /** The light the table gives the block a placement sets, or null. */
    private static Glow glowAt(Placement p) {
        int c = p.right + SwatchLayout.CENTRE;
        if (c < 0 || c >= SwatchLayout.COLS || p.up < 0 || p.up >= SwatchLayout.ROWS) return null;
        if (p.along == SwatchLayout.BACK) return SwatchLayout.back(c, p.up).glow;
        if (p.along == SwatchLayout.FRONT) return SwatchLayout.front(c, p.up).glow;
        return null;
    }

    /** The neighbourhood of a cell in the rig's world, as the test of the layout reads it. */
    private static Nbhd nbhd(Map<Long, Integer> w, int[] at) {
        int[] c = new int[27];
        int self = w.get(SkinEntry.pack(at[0], at[1], at[2])) >> 12;
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dy == 0 && dz == 0) continue;
            Integer v = w.get(SkinEntry.pack(at[0] + dx, at[1] + dy, at[2] + dz));
            int code = v == null ? Nbhd.OPEN : v;
            Form f = code >= 4 && (code & 0xFFF) >= 4 ? Form.of(((code & 0xFF) >> 2) - 1) : null;
            c[Nbhd.index(dx, dy, dz)] = f == null ? code & 3 : Nbhd.ours(f, (code >> 12) == self);
        }
        return Nbhd.of(c);
    }

    /** The reserved materials' rows hatched in their colours on the wall's face, the fixtures' rows outlined. */
    private static void reserved(Graphics2D g, double ox, double oy) {
        for (int r = SwatchLayout.RESERVED_ROW; r < SwatchLayout.ROWS; r++)
            for (int c = 0; c < SwatchLayout.COLS; c++) {
                Cell cell = SwatchLayout.back(c, r);
                Polygon face = face(c, r, ox, oy);
                if (cell.kind == Cell.Kind.RESERVED) {
                    int rgb = cell.material != null ? cell.material.colour : 0xB9C3CA;
                    g.setColor(new Color(rgb));
                    g.fillPolygon(face);
                    Shape clip = g.getClip();
                    g.setClip(face);
                    g.setStroke(new BasicStroke(2f));
                    g.setColor(new Color(lum(rgb) > 0.5 ? 0x2A2A2A : 0xF2F2F2));
                    java.awt.Rectangle b = face.getBounds();
                    for (int k = -b.height; k < b.width; k += 9)
                        g.drawLine(b.x + k, b.y + b.height, b.x + k + b.height, b.y);
                    g.setClip(clip);
                } else if (cell.kind == Cell.Kind.FIXTURE) {
                    g.setColor(new Color(255, 255, 255, 40));
                    g.fillPolygon(face);
                    g.setColor(new Color(255, 255, 255, 170));
                    g.setStroke(
                        new BasicStroke(
                            1.5f,
                            BasicStroke.CAP_BUTT,
                            BasicStroke.JOIN_MITER,
                            1f,
                            new float[] { 4, 4 },
                            0));
                    g.drawPolygon(face);
                }
            }
        g.setStroke(new BasicStroke(1f));
    }

    /** The front face of a wall cell (column, row) on screen. */
    private static Polygon face(int c, int r, double ox, double oy) {
        int[] at = FRAME.at(SwatchLayout.BACK, SwatchLayout.right(c), r);
        double z = at[2] + 1;
        Polygon p = new Polygon();
        double[][] corners = { { at[0], at[1], z }, { at[0] + 1, at[1], z }, { at[0] + 1, at[1] + 1, z },
            { at[0], at[1] + 1, z } };
        for (double[] k : corners) {
            double[] s = screen(k[0], k[1], k[2]);
            p.addPoint((int) Math.round(ox + s[0] * S), (int) Math.round(oy + s[1] * S));
        }
        return p;
    }

    // ---- the words round it

    private static void labels(Graphics2D g, double ox, double oy, int width, int height) {
        Font title = new Font(Font.SANS_SERIF, Font.BOLD, 22), body = new Font(Font.SANS_SERIF, Font.PLAIN, 14),
            small = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        g.setColor(Color.WHITE);
        g.setFont(title);
        g.drawString("0.17.0 样板墙 /fluxecho swatch（style §8.6）· 正视图，略从右上看", 20, 36);
        g.setFont(body);
        g.drawString("33 × 12 格真实方块；墙前一格是浮雕层，再前是三格宽的镜面水池（池边是玄基池唇）；每一格的光按盒子表的强调色、灯亮灯暗画。", 20, 62);
        g.drawString("可见色对 ΔE00：魔力蓝–守护蓝 4.34 · 帆金–长庚金 4.26 · 其余 6.0–11.7（SwatchLayoutTest 核过）", 20, 84);
        // the rows, at the wall's left edge
        String[] rows = { "1 光缝", "2 亮灯", "3 暗灯", "4–6 六种材质 M1–M6", "", "", "7–9 预留 M7–M12（0.20.0）", "", "",
            "10–12 配件（样板墙 2）", "", "" };
        g.setFont(body);
        for (int r = 0; r < SwatchLayout.ROWS; r++) {
            if (rows[r].isEmpty()) continue;
            Polygon f = face(0, r, ox, oy);
            java.awt.Rectangle b = f.getBounds();
            int w = g.getFontMetrics()
                .stringWidth(rows[r]);
            g.setColor(new Color(0, 0, 0, 120));
            g.fillRect(b.x - w - 18, b.y + b.height / 2 - 12, w + 10, 18);
            g.setColor(Color.WHITE);
            g.drawString(rows[r], b.x - w - 13, b.y + b.height / 2 + 2);
        }
        // the light rows' colour names, under the wall, one column each
        int y0 = height - BOTTOM + 30;
        // a dark band under the words, so they read on the light ground
        g.setColor(new Color(0x1E2630));
        g.fillRect(0, y0 - 14, width, height - y0 + 14);
        g.setFont(small);
        for (int c = 0; c < SwatchLayout.COLS; c++) {
            Column col = SwatchLayout.strip()
                .get(c);
            java.awt.Rectangle b = face(c, 0, ox, oy).getBounds();
            int x = b.x + b.width / 2;
            int rgb = col.kind == Column.Kind.GAP ? -1
                : col.kind == Column.Kind.KEEL_MEMORIAL ? SkinPalette.WARM_WHITE : col.rgb();
            if (rgb >= 0) {
                g.setColor(new Color(rgb));
                g.fillRect(x - 8, y0, 16, 16);
                g.setColor(new Color(0x202020));
                g.drawRect(x - 8, y0, 16, 16);
            }
            AffineTransform keep = g.getTransform();
            g.translate(x + 5, y0 + 24);
            g.rotate(Math.PI / 2);
            g.setColor(Color.WHITE);
            g.drawString(col.kind == Column.Kind.GAP ? "·" : col.zh, 0, 0);
            g.setTransform(keep);
        }
        // the material panels
        int y1 = y0 + 150;
        g.setFont(body);
        for (int p = 0; p < 6; p++) {
            Material m = SwatchLayout.panel(p * SwatchLayout.PANEL);
            java.awt.Rectangle a = face(p * SwatchLayout.PANEL, 0, ox, oy).getBounds(),
                b = face(p * SwatchLayout.PANEL + SwatchLayout.PANEL - 1, 0, ox, oy).getBounds();
            g.setColor(new Color(m.colour));
            g.fillRect(a.x + 2, y1, b.x + b.width - a.x - 4, 8);
            g.setColor(Color.WHITE);
            g.drawString(m.id + " " + m.zh + " / " + m.en, a.x + 4, y1 + 26);
        }
        java.awt.Rectangle v = face(SwatchLayout.VANILLA_COL, 0, ox, oy).getBounds();
        g.drawString("原版对照：石头 · 石英 · 白桦", v.x - 40, y1 + 26);
        // the reserved ones
        int y2 = y1 + 58;
        int x = 20;
        g.setFont(small);
        g.drawString("预留材质（第 7–9 行，斜线填色）：", x, y2);
        x += g.getFontMetrics()
            .stringWidth("预留材质（第 7–9 行，斜线填色）：") + 8;
        for (Material m : Material.values()) {
            if (m.placed()) continue;
            g.setColor(new Color(m.colour));
            g.fillRect(x, y2 - 12, 14, 14);
            g.setColor(Color.WHITE);
            g.drawRect(x, y2 - 12, 14, 14);
            String t = m.id + " " + m.zh + String.format(Locale.ROOT, " #%06X", m.colour);
            g.drawString(t, x + 20, y2);
            x += 20 + g.getFontMetrics()
                .stringWidth(t) + 18;
        }
        g.drawString("第 1–3 行：亮灯行在所有时刻都亮（样板墙的灯不随黄昏）；暗灯行是同一颜色的灯熄着。第 10–12 行的配件在 0.20.0 样板墙 2 才摆。", 20, y2 + 24);
    }

    // ---- drawing quads

    /**
     * Adds a quad of the cell at {@code at}, cut into {@link #SUB} × {@link #SUB} pieces each coloured by the texel at
     * its middle (or {@code flat}), shaded by its normal, with its light over it as the box table gives it.
     */
    private static void add(List<Poly> out, Quad q, int[] at, String tex, int flat, float alpha, Glow glow, String mat,
        double ox, double oy) throws IOException {
        double[] mid0 = at(q, 0.5, 0.5);
        if (mid0[3] * -VIEW[0] + mid0[4] * -VIEW[1] + mid0[5] * -VIEW[2] <= 1e-6) return;
        BufferedImage t = tex == null ? null : image(tex);
        BufferedImage[] light = light(q, mat, glow);
        int tint = tint(q.overlay, glow);
        for (int i = 0; i < SUB; i++) for (int j = 0; j < SUB; j++) {
            double u0 = i / (double) SUB, u1 = (i + 1) / (double) SUB, v0 = j / (double) SUB,
                v1 = (j + 1) / (double) SUB;
            double[] mid = at(q, (u0 + u1) / 2, (v0 + v1) / 2);
            int rgb = flat;
            int tx = clamp((int) (mid[6] * 32), 31), ty = clamp((int) (mid[7] * 32), 31);
            if (t != null) {
                int p = t.getRGB(tx, ty);
                if (p >>> 24 == 0) continue; // clear glass
                rgb = p & 0xFFFFFF;
            }
            double shade = 0.62 + 0.38 * Math.max(0, mid[3] * 0.25 + mid[4] * 0.55 + mid[5] * 0.8);
            rgb = scale(rgb, Math.min(1, shade));
            if (light != null) {
                int core = light[0].getRGB(tx, ty), edge = light[1].getRGB(tx, ty);
                if (core >>> 24 != 0) rgb = mix(
                    rgb,
                    tint == SkinPalette.WARM_WHITE ? core & 0xFFFFFF : mix(core & 0xFFFFFF, tint, 0.35),
                    (core >>> 24) / 255.0);
                else if (edge >>> 24 != 0)
                    rgb = mix(rgb, SkinPalette.tint(edge & 0xFFFFFF, tint), (edge >>> 24) / 255.0);
            }
            double[][] corners = { at(q, u0, v0), at(q, u1, v0), at(q, u1, v1), at(q, u0, v1) };
            double[][] world = new double[4][];
            for (int k = 0; k < 4; k++)
                world[k] = new double[] { corners[k][0] + at[0], corners[k][1] + at[1], corners[k][2] + at[2] };
            out.add(poly(world, rgb, alpha, ox, oy));
        }
    }

    private static Poly poly(double[][] world, int rgb, float alpha, double ox, double oy) {
        Polygon p = new Polygon();
        double depth = 0;
        for (double[] w : world) {
            double[] s = screen(w[0], w[1], w[2]);
            p.addPoint((int) Math.round(ox + s[0] * S), (int) Math.round(oy + s[1] * S));
            depth += s[2] / world.length;
        }
        return new Poly(p, depth, rgb, alpha);
    }

    /** A world point on screen (in blocks, y down) and its distance along the view. */
    private static double[] screen(double x, double y, double z) {
        double[] p = { x, y, z };
        return new double[] { dot(p, SCREEN_R), -dot(p, SCREEN_U), dot(p, VIEW) };
    }

    /** The glow textures over a quad, core and edge, or null: a dark lamp has none. */
    private static BufferedImage[] light(Quad q, String mat, Glow glow) throws IOException {
        if (mat == null) return null;
        String name;
        switch (q.overlay) {
            case LINE:
                if (glow != null && !glow.awake) return null;
                name = mat + "/light_seam";
                break;
            case KEEL:
                name = "hull/keel";
                break;
            case KEEL_TIP:
                name = "hull/keel_tip";
                break;
            case LAMP:
                if (glow != null && !glow.lamps) return null;
                name = mat + (mat.equals("paper") ? "/lantern" : "/lamp");
                break;
            case NOSE:
                name = (mat.equals("footing") ? "footing" : "hull") + "/nose";
                break;
            case LIP:
                name = "footing/lip";
                break;
            default:
                return null;
        }
        if (!TEX.resolve(name + "_glow_core.png")
            .toFile()
            .isFile()) return null;
        return new BufferedImage[] { image(name + "_glow_core"), image(name + "_glow_edge") };
    }

    /** The colour a quad's light takes: the accent for seams and lamps, the keel's colour, else warm white. */
    private static int tint(Overlay o, Glow glow) {
        switch (o) {
            case KEEL:
            case KEEL_TIP:
                return glow != null ? glow.keel : SkinPalette.NEXUS_CYAN;
            case LINE:
            case LAMP:
                return glow == null || glow.accent == SkinPalette.NONE ? SkinPalette.WARM_WHITE : glow.accent;
            default:
                return SkinPalette.WARM_WHITE;
        }
    }

    private static String material(Family f, int n) {
        switch (f) {
            case HULL:
                return "hull";
            case FOOTING:
                return "footing";
            case SKIN_GARDEN:
                return n < 8 ? "moss" : "marble";
            default:
                return n < 8 ? "ink" : "paper";
        }
    }

    /** The texture a quad shows (the pattern's first cell), as the form preview picks it. */
    private static String texture(String mat, Form form, Quad q) {
        boolean horizontal = q.uAxis == 1 && q.vAxis == 3, up = q.face == 1;
        String body = mat.equals("footing") ? horizontal ? "footing/top_0_0" : "footing/side_0_0"
            : mat.equals("hull") ? "hull/whole" : mat + "/whole_0_0";
        String p = switch (form) {
            case SEAM -> mat + "/seam_0_0";
            case FLOOR -> mat + "/floor_0_0";
            case RELIEF -> mat + "/relief_0_0";
            case WINDOW -> mat + "/window";
            case GRATE -> mat + "/grate";
            case LAMP -> horizontal ? null : mat + (mat.equals("paper") ? "/lantern" : "/lamp");
            case LIGHT_SEAM -> horizontal ? null : mat + "/light_seam";
            case KEEL -> horizontal ? null : mat + "/keel";
            case SPOUT -> q.overlay == Overlay.SPOUT ? mat + "/spout" : null;
            case PLANTER -> up ? mat + "/planter" : null;
            default -> null;
        };
        if (p != null && !TEX.resolve(p + ".png")
            .toFile()
            .isFile()) p = null;
        return p != null ? p : body;
    }

    private static int vanilla(String block) {
        if (block == null) return 0x7D7D7D;
        if (block.contains("quartz")) return 0xEBE5DE;
        if (block.contains("log")) return 0xD9D4C7;
        return 0x7D7D7D;
    }

    private static BufferedImage image(String name) throws IOException {
        BufferedImage i = CACHE.get(name);
        if (i == null) {
            File f = TEX.resolve(name + ".png")
                .toFile();
            i = ImageIO.read(f);
            CACHE.put(name, i);
        }
        return i;
    }

    // ---- small maths

    /** A point of a quad at (a, b) across its corners: position, normal, texture coordinates. */
    private static double[] at(Quad q, double a, double b) {
        double[] out = new double[8];
        double[] w = { (1 - a) * (1 - b), a * (1 - b), a * b, (1 - a) * b };
        for (int v = 0; v < 4; v++) {
            for (int k = 0; k < 3; k++) {
                out[k] += w[v] * q.pos[3 * v + k];
                out[3 + k] += w[v] * q.normal[3 * v + k];
            }
            out[6] += w[v] * q.uv[2 * v];
            out[7] += w[v] * q.uv[2 * v + 1];
        }
        return out;
    }

    private static double[] norm(double... v) {
        double l = Math.sqrt(dot(v, v));
        return new double[] { v[0] / l, v[1] / l, v[2] / l };
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] { a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0] };
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static int clamp(int v, int max) {
        return Math.max(0, Math.min(max, v));
    }

    private static int scale(int rgb, double f) {
        int r = (int) ((rgb >> 16 & 255) * f), g = (int) ((rgb >> 8 & 255) * f), b = (int) ((rgb & 255) * f);
        return r << 16 | g << 8 | b;
    }

    private static int mix(int a, int b, double t) {
        int r = (int) Math.round((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t),
            g = (int) Math.round((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t),
            bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    private static double lum(int rgb) {
        return (0.2126 * (rgb >> 16 & 255) + 0.7152 * (rgb >> 8 & 255) + 0.0722 * (rgb & 255)) / 255.0;
    }
}
