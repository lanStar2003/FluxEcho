import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the Flux Shard Collector's GUI: the flux world's look (deep navy panes, cyan seams, violet depth) instead of
 * GT's grey. Run from the repository root: {@code java tools/DepthsGuiTextures.java}. Positions match {@code CollectorGui}.
 */
public class DepthsGuiTextures {

    static final String ROOT = "src/main/resources/assets/fluxdepths/textures/gui/";

    static final int DEEP = 0x0A1622, DEEPER = 0x070F18, PANE = 0x0D1D2A, SEAM = 0x1F3C4E, CYAN = 0x4FE3FF,
        VIOLET = 0x8A5CFF, GRID = 0x10283A;

    public static void main(String[] args) throws Exception {
        background();
        slot("slot", null);
        slot("slot_core", "core");
        slot("slot_head", "head");
        slot("slot_imprint", "imprint");
        slot("slot_fluid", "fluid");
        lock();
        button(false, "button");
        button(true, "button_on");
        iconHolo();
        iconOutput();
        logo();
    }

    static Color c(int rgb, double a) {
        return new Color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, (int) Math.round(a * 255));
    }

    static void fill(Graphics2D g, int x0, int y0, int x1, int y1, int rgb, double a) {
        g.setColor(c(rgb, a));
        g.fillRect(x0, y0, x1 - x0, y1 - y0);
    }

    static void frame(Graphics2D g, int x0, int y0, int x1, int y1, int rgb, double a) {
        fill(g, x0, y0, x1, y0 + 1, rgb, a);
        fill(g, x0, y1 - 1, x1, y1, rgb, a);
        fill(g, x0, y0 + 1, x0 + 1, y1 - 1, rgb, a);
        fill(g, x1 - 1, y0 + 1, x1, y1 - 1, rgb, a);
    }

    static void corners(Graphics2D g, int x0, int y0, int x1, int y1, int len, int rgb, double a) {
        fill(g, x0, y0, x0 + len, y0 + 1, rgb, a);
        fill(g, x0, y0, x0 + 1, y0 + len, rgb, a);
        fill(g, x1 - len, y0, x1, y0 + 1, rgb, a);
        fill(g, x1 - 1, y0, x1, y0 + len, rgb, a);
        fill(g, x0, y1 - 1, x0 + len, y1, rgb, a);
        fill(g, x0, y1 - len, x0 + 1, y1, rgb, a);
        fill(g, x1 - len, y1 - 1, x1, y1, rgb, a);
        fill(g, x1 - 1, y1 - len, x1, y1, rgb, a);
    }

    /** An inset pane: a faint grid on deep navy, a seam frame, bright corners. */
    static void pane(Graphics2D g, int x0, int y0, int x1, int y1, boolean grid) {
        for (int y = y0; y < y1; y++) {
            double t = (double) (y - y0) / Math.max(1, y1 - y0);
            fill(g, x0, y, x1, y + 1, mix(PANE, DEEPER, t * 0.6), 1);
        }
        if (grid) {
            for (int x = x0 + 4; x < x1 - 1; x += 8) fill(g, x, y0 + 1, x + 1, y1 - 1, GRID, 0.6);
            for (int y = y0 + 4; y < y1 - 1; y += 8) fill(g, x0 + 1, y, x1 - 1, y + 1, GRID, 0.6);
        }
        frame(g, x0, y0, x1, y1, SEAM, 1);
        corners(g, x0, y0, x1, y1, 4, CYAN, 0.9);
    }

    static int mix(int a, int b, double t) {
        int r = (int) ((a >> 16 & 0xFF) * (1 - t) + (b >> 16 & 0xFF) * t);
        int gg = (int) ((a >> 8 & 0xFF) * (1 - t) + (b >> 8 & 0xFF) * t);
        int bb = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return r << 16 | gg << 8 | bb;
    }

    /** The window: 176 x 220. */
    static void background() throws Exception {
        int w = 176, h = 220;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        for (int y = 0; y < h; y++) fill(g, 0, y, w, y + 1, mix(DEEP, DEEPER, (double) y / h), 1);
        // a faint violet glow rising from the depth shaft
        for (int y = 0; y < 90; y++) for (int x = 40; x < 136; x++) {
            double d = Math.hypot((x - 87) / 48.0, (y - 84) / 70.0);
            if (d < 1) {
                g.setColor(c(VIOLET, 0.10 * (1 - d)));
                g.fillRect(x, y, 1, 1);
            }
        }
        frame(g, 0, 0, w, h, 0x2A4A5E, 1);
        frame(g, 1, 1, w - 1, h - 1, 0x0F1F2B, 1);
        corners(g, 2, 2, w - 2, h - 2, 9, CYAN, 1);
        // rounded corners
        for (int[] p : new int[][] { { 0, 0 }, { w - 1, 0 }, { 0, h - 1 }, { w - 1, h - 1 } }) img.setRGB(p[0], p[1], 0);

        // header
        pane(g, 6, 5, 170, 19, false);
        // main pane, with traces from the core and the imprints to the shaft and on to the outputs
        pane(g, 6, 22, 170, 85, true);
        g.setColor(c(CYAN, 0.22));
        g.fillRect(27, 34, 4, 1);
        g.fillRect(27, 54, 2, 1);
        g.fillRect(28, 54, 1, 21);
        g.fillRect(28, 74, 2, 1);
        g.fillRect(67, 42, 4, 1);
        g.fillRect(103, 42, 4, 1);
        g.fillRect(143, 34, 6, 1);
        g.fillRect(143, 52, 6, 1);
        // the shaft's well
        fill(g, 70, 22, 104, 82, DEEPER, 1);
        // readout
        pane(g, 6, 88, 170, 136, false);
        fill(g, 7, 124, 169, 125, SEAM, 1);
        // inventory
        pane(g, 5, 137, 171, 216, false);
        fill(g, 8, 194, 168, 195, SEAM, 0.8);
        save(img, "collector");
    }

    /** A slot: a dark well with a seam, a cyan lip, and a faint picture of what goes in it. */
    static void slot(String name, String ghost) throws Exception {
        BufferedImage img = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        fill(g, 0, 0, 18, 18, 0x08121B, 1);
        frame(g, 0, 0, 18, 18, 0x23465A, 1);
        fill(g, 1, 1, 17, 2, 0x04090E, 1);
        fill(g, 1, 1, 2, 17, 0x04090E, 1);
        fill(g, 1, 16, 17, 17, CYAN, 0.35);
        if (ghost != null) {
            g.setColor(c(0x6FA9C2, 0.45));
            switch (ghost) {
                case "core" -> {
                    g.fillRect(5, 5, 8, 8);
                    g.setColor(c(0x08121B, 1));
                    g.fillRect(7, 7, 4, 4);
                    g.setColor(c(0x6FA9C2, 0.45));
                    for (int i = 6; i <= 11; i += 2) {
                        g.fillRect(i, 3, 1, 2);
                        g.fillRect(i, 13, 1, 2);
                        g.fillRect(3, i, 2, 1);
                        g.fillRect(13, i, 2, 1);
                    }
                }
                case "head" -> g.fillPolygon(new Polygon(new int[] { 5, 13, 9 }, new int[] { 4, 4, 15 }, 3));
                case "imprint" -> {
                    g.fillRect(5, 3, 8, 12);
                    g.setColor(c(0x08121B, 1));
                    for (int i = 0; i < 4; i++) g.fillRect(7 + i % 2, 12 - i * 2, 2, 1);
                }
                case "fluid" -> {
                    g.fillPolygon(new Polygon(new int[] { 9, 5, 13 }, new int[] { 3, 10, 10 }, 3));
                    g.fillOval(5, 7, 8, 8);
                }
                default -> {}
            }
        }
        save(img, name);
    }

    /** Over an imprint slot the tier does not use: dimmed, with a padlock. */
    static void lock() throws Exception {
        BufferedImage img = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        fill(g, 1, 1, 17, 17, 0x03070B, 0.72);
        g.setColor(c(0x7A8A96, 0.9));
        g.fillRect(6, 9, 6, 5);
        g.fillRect(7, 5, 1, 4);
        g.fillRect(10, 5, 1, 4);
        g.fillRect(7, 5, 4, 1);
        g.setColor(c(0x03070B, 1));
        g.fillRect(8, 11, 2, 2);
        save(img, "lock");
    }

    static void button(boolean on, String name) throws Exception {
        BufferedImage img = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        fill(g, 0, 0, 18, 18, on ? 0x16465A : 0x112432, 1);
        fill(g, 1, 1, 17, 2, on ? 0x2A7E98 : 0x1E3A4C, 1);
        frame(g, 0, 0, 18, 18, on ? CYAN : 0x2A5068, 1);
        if (on) corners(g, 0, 0, 18, 18, 4, 0xC8F6FF, 1);
        save(img, name);
    }

    /** A projector throwing a pane. */
    static void iconHolo() throws Exception {
        BufferedImage img = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c(CYAN, 0.35));
        g.fillPolygon(new Polygon(new int[] { 9, 3, 15 }, new int[] { 13, 8, 8 }, 3));
        g.setColor(c(CYAN, 1));
        g.drawRect(3, 3, 11, 5);
        g.setColor(c(0xC8F6FF, 1));
        g.fillRect(5, 5, 4, 1);
        g.fillRect(5, 6, 6, 1);
        g.setColor(c(0x9AB0BE, 1));
        g.fillRect(6, 13, 6, 2);
        save(img, "icon_holo");
    }

    /** Ores leaving the machine. */
    static void iconOutput() throws Exception {
        BufferedImage img = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c(0x9AB0BE, 1));
        g.drawRect(3, 5, 6, 7);
        g.setColor(c(CYAN, 1));
        g.fillRect(8, 8, 5, 2);
        g.fillPolygon(new Polygon(new int[] { 12, 16, 12 }, new int[] { 5, 9, 13 }, 3));
        save(img, "icon_output");
    }

    /** Layers sinking into the depth: a cyan diamond around a violet one. */
    static void logo() throws Exception {
        BufferedImage img = new BufferedImage(17, 17, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 17; y++) for (int x = 0; x < 17; x++) {
            int d = Math.abs(x - 8) + Math.abs(y - 8);
            int c = 0;
            if (d == 8 || d == 7) c = d == 8 ? 0xFF4FE3FF : 0xFF2A8FB0;
            else if (d == 4 || d == 3) c = 0xFF8A5CFF;
            else if (d <= 1) c = 0xFFE8F8FF;
            if (c != 0) img.setRGB(x, y, c);
        }
        save(img, "logo");
    }

    static void save(BufferedImage img, String name) throws Exception {
        File f = new File(ROOT + name + ".png");
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
        System.out.println("wrote " + f);
    }
}
