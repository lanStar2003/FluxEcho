package com.fluxecho.client.shape;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fluxecho.logic.shape.Family;
import com.fluxecho.logic.shape.Form;
import com.fluxecho.logic.shape.Nbhd;
import com.fluxecho.logic.shape.Overlay;
import com.fluxecho.logic.shape.Quad;
import com.fluxecho.logic.shape.ShapeGeo;
import com.fluxecho.logic.skin.SkinPalette;

/**
 * A preview, not a test (0.17.0, refactor FINAL F04): the hull's sixteen shapes, and the footing's and the first skins'
 * special ones, drawn isometrically from {@code ShapeGeo}'s quads with the committed textures and their glows, each in
 * a small setting that shows how it reads its neighbours (a round in a corner, a slope against a wall, a keel on its
 * footing, a soffit in its opening, a pool lip by water), into {@code build/previews/0.17.0/forms.png} for the
 * version's preview sheet. It asserts
 * nothing and commits nothing; slow, as it draws.
 */
@Tag("slow")
class FormPreview {

    private static final Path TEX = Paths.get("src/main/resources/assets/fluxecho/textures/blocks/skin");
    private static final int TILE = 300, COLS = 6, SUB = 12;
    private static final double SCALE = 58;
    private static final Map<String, BufferedImage> CACHE = new HashMap<>();

    /** One tile: a family's shape number, its title, and the cells beside it that give it its setting. */
    private static final class Sample {

        final Family family;
        final int number;
        final String title;
        /** Neighbours {dx, dy, dz, code} (Nbhd codes): drawn as hull whole cubes when ours or solid. */
        final int[][] around;

        Sample(Family family, int number, String title, int[]... around) {
            this.family = family;
            this.number = number;
            this.title = title;
            this.around = around;
        }
    }

    @Test
    void drawTheForms() throws IOException {
        int solid = Nbhd.SOLID;
        List<Sample> samples = new ArrayList<>();
        String[] names = { "0 whole", "1 seam", "2 round", "3 slope", "4 half", "5 step", "6 rib", "7 column",
            "8 window", "9 louvre", "10 light seam", "11 keel", "12 lamp", "13 soffit", "14 floor", "15 relief" };
        for (int n = 0; n < 16; n++) {
            int[][] around = {};
            if (n == 13) around = new int[][] { { 0, 1, 0, solid }, { -1, 0, 0, solid } };
            if (n == 11) around = new int[][] { { 0, -1, 0, solid } };
            if (n == 2) around = new int[][] { { -1, 0, 0, solid }, { 0, 0, -1, solid } };
            if (n == 3) around = new int[][] { { 0, 0, -1, solid } };
            samples.add(new Sample(Family.HULL, n, "hull " + names[n], around));
        }
        samples.add(new Sample(Family.FOOTING, 6, "footing 6 coping"));
        samples.add(new Sample(Family.FOOTING, 9, "footing 9 pool lip", new int[] { 1, 0, 0, Nbhd.WATER }));
        samples.add(new Sample(Family.FOOTING, 11, "footing 11 spout"));
        samples.add(new Sample(Family.FOOTING, 12, "footing 12 planter"));
        samples.add(new Sample(Family.SKIN_GARDEN, 7, "moss 7 framed window"));
        samples.add(new Sample(Family.SKIN_SCRIPT, 13, "paper 13 lantern"));
        int rows = (samples.size() + COLS - 1) / COLS;
        BufferedImage img = new BufferedImage(COLS * TILE, rows * TILE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x8FA6B2));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int i = 0; i < samples.size(); i++) {
            Sample s = samples.get(i);
            int ox = (i % COLS) * TILE + TILE / 2, oy = (i / COLS) * TILE + TILE / 2 + 30;
            draw(g, s, ox, oy);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            g.drawString(s.title, (i % COLS) * TILE + 10, (i / COLS) * TILE + 22);
            g.setColor(new Color(0x6B7F8A));
            g.setStroke(new BasicStroke(1));
            g.drawRect((i % COLS) * TILE, (i / COLS) * TILE, TILE - 1, TILE - 1);
        }
        g.dispose();
        Path out = Paths.get("build/previews/0.17.0/forms.png");
        Files.createDirectories(out.getParent());
        ImageIO.write(img, "png", out.toFile());
    }

    /** A small polygon to fill: its screen points, its depth, its colour. */
    private static final class Poly {

        final Polygon p;
        final double depth;
        final int rgb;

        Poly(Polygon p, double depth, int rgb) {
            this.p = p;
            this.depth = depth;
            this.rgb = rgb;
        }
    }

    private static void draw(Graphics2D g, Sample s, int ox, int oy) throws IOException {
        Form form = s.family.form(s.number);
        if (form == null) form = Form.WHOLE;
        Nbhd.Builder nb = new Nbhd.Builder();
        for (int[] a : s.around) nb.set(a[0], a[1], a[2], a[3]);
        Nbhd n = nb.build();
        List<Poly> polys = new ArrayList<>();
        // the setting: hull cubes for solid neighbours, a blue cube for water
        for (int[] a : s.around) {
            int rgb = a[3] == Nbhd.WATER ? 0x3F76E4 : 0xB9C3CA;
            for (Quad q : ShapeGeo.item(Form.WHOLE)) add(polys, q, a[0], a[1], a[2], null, rgb, ox, oy, s);
        }
        String mat = material(s.family, s.number);
        for (Quad q : ShapeGeo.quads(form, n)) add(polys, q, 0, 0, 0, texture(mat, form, q), 0, ox, oy, s);
        polys.sort((a, b) -> Double.compare(a.depth, b.depth));
        for (Poly p : polys) {
            g.setColor(new Color(p.rgb));
            g.fillPolygon(p.p);
            g.drawPolygon(p.p);
        }
    }

    /** The view: from the south-east, above; a point's screen place and its nearness. */
    private static double[] project(double x, double y, double z, int ox, int oy) {
        double sx = (x - z) * Math.cos(Math.PI / 6) * SCALE, sy = ((x + z) * Math.sin(Math.PI / 6) - y) * SCALE;
        return new double[] { ox + sx, oy + sy, x + y + z };
    }

    /**
     * Adds a quad, cut into {@link #SUB} × {@link #SUB} pieces each coloured by the texel at its middle (or
     * {@code flat}), shaded by its normal, with the quad's glow over it.
     */
    private static void add(List<Poly> out, Quad q, double dx, double dy, double dz, String tex, int flat, int ox,
        int oy, Sample s) throws IOException {
        BufferedImage t = tex == null ? null : image(tex);
        BufferedImage[] glow = glow(s, q);
        for (int i = 0; i < SUB; i++) for (int j = 0; j < SUB; j++) {
            double u0 = i / (double) SUB, u1 = (i + 1) / (double) SUB, v0 = j / (double) SUB,
                v1 = (j + 1) / (double) SUB;
            double[][] corners = { at(q, u0, v0), at(q, u1, v0), at(q, u1, v1), at(q, u0, v1) };
            double[] mid = at(q, (u0 + u1) / 2, (v0 + v1) / 2);
            // facing the viewer at all?
            if (mid[3] + mid[4] + mid[5] <= 1e-6) continue;
            int rgb = flat;
            if (t != null) {
                int tx = clamp((int) (mid[6] * 32), 31), ty = clamp((int) (mid[7] * 32), 31);
                int p = t.getRGB(tx, ty);
                if (p >>> 24 == 0) continue; // clear glass
                rgb = p & 0xFFFFFF;
            }
            double shade = 0.55 + 0.45 * Math.max(0, (mid[3] * 0.4 + mid[4] * 1.0 + mid[5] * 0.6) / 1.2);
            rgb = scale(rgb, Math.min(1, shade));
            if (glow != null) {
                int tx = clamp((int) (mid[6] * 32), 31), ty = clamp((int) (mid[7] * 32), 31);
                int core = glow[0].getRGB(tx, ty), edge = glow[1].getRGB(tx, ty);
                if (core >>> 24 != 0) rgb = core & 0xFFFFFF;
                else if (edge >>> 24 != 0) rgb = SkinPalette.tint(edge & 0xFFFFFF, accent(q.overlay));
            }
            Polygon poly = new Polygon();
            double depth = 0;
            for (double[] c : corners) {
                double[] p = project(c[0] + dx, c[1] + dy, c[2] + dz, ox, oy);
                poly.addPoint((int) Math.round(p[0]), (int) Math.round(p[1]));
                depth += p[2] / 4;
            }
            out.add(new Poly(poly, depth, rgb));
        }
    }

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

    private static int clamp(int v, int max) {
        return Math.max(0, Math.min(max, v));
    }

    private static int scale(int rgb, double f) {
        int r = (int) ((rgb >> 16 & 255) * f), g = (int) ((rgb >> 8 & 255) * f), b = (int) ((rgb & 255) * f);
        return r << 16 | g << 8 | b;
    }

    private static int accent(Overlay o) {
        return o == Overlay.KEEL || o == Overlay.KEEL_TIP ? SkinPalette.NEXUS_CYAN : SkinPalette.WARM_WHITE;
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

    /** The texture a quad shows, as {@link SkinIcons#pattern} picks it (the pattern's first cell). */
    private static String texture(String mat, Form form, Quad q) {
        boolean horizontal = q.uAxis == 1 && q.vAxis == 3, up = q.face == 1;
        String body = mat.equals("footing") ? horizontal ? "footing/top_0_0" : "footing/side_0_0"
            : mat.equals("hull") ? "hull/whole" : mat + "/whole_0_0";
        String p = null;
        switch (form) {
            case SEAM:
                p = mat + "/seam_0_0";
                break;
            case FLOOR:
                p = mat + "/floor_0_0";
                break;
            case RELIEF:
                p = mat + "/relief_0_0";
                break;
            case WINDOW:
                p = mat + "/window";
                break;
            case GRATE:
                p = mat + "/grate";
                break;
            case LAMP:
                p = horizontal ? null : mat + (mat.equals("paper") ? "/lantern" : "/lamp");
                break;
            case LIGHT_SEAM:
                p = horizontal ? null : mat + "/light_seam";
                break;
            case KEEL:
                p = horizontal ? null : mat + "/keel";
                break;
            case SPOUT:
                p = q.overlay == Overlay.SPOUT ? mat + "/spout" : null;
                break;
            case PLANTER:
                p = up ? mat + "/planter" : null;
                break;
            default:
                p = null;
        }
        if (p != null && !TEX.resolve(p + ".png")
            .toFile()
            .isFile()) p = null;
        return p != null ? p : body;
    }

    /** The glow over a quad, core and edge, or null. */
    private static BufferedImage[] glow(Sample s, Quad q) throws IOException {
        String name;
        String mat = material(s.family, s.number);
        switch (q.overlay) {
            case LINE:
                name = mat + "/light_seam";
                break;
            case KEEL:
                name = "hull/keel";
                break;
            case KEEL_TIP:
                name = "hull/keel_tip";
                break;
            case LAMP:
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
        return new BufferedImage[] { image(name + "_glow_core"), image(name + "_glow_edge") };
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
}
