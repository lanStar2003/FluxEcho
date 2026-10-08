import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the flux casing every FluxEcho machine stands in (the faces of {@code tools/Textures.java} go on top of it)
 * and the parts of their shared GUI, in the look of FluxDepths' collector and the Mana Echo Spring. Run from the
 * repository root: {@code java tools/FluxTextures.java}. GUI positions match {@code FluxMachineGui}.
 */
public class FluxTextures {

    static final String ROOT = "src/main/resources/assets/fluxecho/textures/";
    static final String BLOCK = "blocks/machines/flux/", GUI = "gui/flux/";

    static final int DEEP = 0x0A1622, DEEPER = 0x070F18, PANE = 0x0D1D2A, SEAM = 0x1F3C4E, CYAN = 0x4FE3FF,
        VIOLET = 0x8A5CFF, GRID = 0x10283A, GOLD = 0xFFD27A;

    public static void main(String[] args) throws Exception {
        casing("side", BLOCK + "casing_side");
        casing("top", BLOCK + "casing_top");
        casing("bottom", BLOCK + "casing_bottom");
        strip(BLOCK + "strip");

        background();
        slot("slot", 0);
        slot("slot_out", 1);
        sampleSlot();
        button(false, "button");
        button(true, "button_on");
        iconHolo();
        iconOutput();
        logo();
        mote();
    }

    static int rgb(int c) {
        return 0xFF000000 | c;
    }

    // ---- block faces, 16 x 16

    /** Dark plates, a bevel, flux seams and rivets. */
    static void casing(String kind, String name) throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int c = ((x * 13 + y * 7) % 5 == 0) ? 0x1F2630 : 0x1B212A;
            if (x == 0 || y == 0) c = 0x46525F;
            else if (x == 15 || y == 15) c = 0x0B0E13;
            else if (x == 1 || y == 1) c = 0x2C3540;
            else if (x == 14 || y == 14) c = 0x12161C;
            switch (kind) {
                case "side" -> {
                    if ((x == 3 || x == 12) && y >= 3 && y <= 12) c = 0x0E1218;
                    if ((x == 4 || x == 11) && y >= 3 && y <= 12) c = 0x2A3440;
                    if (y == 2 && x >= 3 && x <= 12 || y == 13 && x >= 3 && x <= 12) c = 0x1B3A4A;
                }
                case "top" -> {
                    if (x >= 1 && x <= 14 && (y == 1 || y == 14) || y >= 1 && y <= 14 && (x == 1 || x == 14))
                        c = 0x1B3A4A;
                }
                case "bottom" -> {
                    if (y >= 4 && y <= 11 && x >= 3 && x <= 12) c = y % 2 == 0 ? 0x0A0D11 : 0x2A3440;
                }
                default -> {}
            }
            if ((x == 2 || x == 13) && (y == 2 || y == 13)) c = 0x6B7686;
            img.setRGB(x, y, rgb(c));
        }
        save(img, name);
    }

    /** The side strip, white so GT tints it in the machine's colour: a rail with an echo ring in the middle. */
    static void strip(String name) throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 3; y <= 12; y++) for (int x = 5; x <= 10; x++) {
            int c = 0;
            if (x == 7 || x == 8) c = (y == 3 || y == 12) ? 0x9A9A9A : 0xE6E6E6;
            double d = Math.hypot(x - 7.5, y - 7.5);
            if (d >= 1.6 && d < 2.6) c = 0xD0D0D0;
            if (d < 1.0) c = 0xFFFFFF;
            if (c != 0) img.setRGB(x, y, rgb(c));
        }
        save(img, name);
    }

    // ---- GUI

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

    static int mix(int a, int b, double t) {
        int r = (int) ((a >> 16 & 0xFF) * (1 - t) + (b >> 16 & 0xFF) * t);
        int gg = (int) ((a >> 8 & 0xFF) * (1 - t) + (b >> 8 & 0xFF) * t);
        int bb = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return r << 16 | gg << 8 | bb;
    }

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

    /**
     * The window, 176 x 220: header, the work pane (slots, the machine's own animation in the middle, buttons on the
     * right), the readout, the inventory. The slots and the animation are drawn over it by the GUI.
     */
    static void background() throws Exception {
        int w = 176, h = 220;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        for (int y = 0; y < h; y++) fill(g, 0, y, w, y + 1, mix(DEEP, DEEPER, (double) y / h), 1);
        frame(g, 0, 0, w, h, 0x2A4A5E, 1);
        frame(g, 1, 1, w - 1, h - 1, 0x0F1F2B, 1);
        corners(g, 2, 2, w - 2, h - 2, 9, CYAN, 1);
        for (int[] p : new int[][] { { 0, 0 }, { w - 1, 0 }, { 0, h - 1 }, { w - 1, h - 1 } }) img.setRGB(p[0], p[1], 0);

        pane(g, 6, 5, 170, 19, false);
        pane(g, 6, 22, 170, 85, true);
        fill(g, 146, 24, 147, 83, SEAM, 1);
        pane(g, 6, 88, 170, 136, false);
        fill(g, 7, 124, 169, 125, SEAM, 1);
        pane(g, 5, 137, 171, 216, false);
        fill(g, 8, 194, 168, 195, SEAM, 0.8);
        save(img, GUI + "background");
    }

    /** An item slot; an output one has a green lip. */
    static void slot(String name, int kind) throws Exception {
        BufferedImage img = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        fill(g, 0, 0, 18, 18, 0x08121B, 1);
        frame(g, 0, 0, 18, 18, 0x23465A, 1);
        fill(g, 1, 1, 17, 2, 0x04090E, 1);
        fill(g, 1, 1, 2, 17, 0x04090E, 1);
        fill(g, 1, 16, 17, 17, kind == 1 ? 0x6CFF8A : CYAN, 0.35);
        save(img, GUI + name);
    }

    /** The sample's slot: the thing done once by hand, kept; a golden frame with brackets. */
    static void sampleSlot() throws Exception {
        BufferedImage img = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        fill(g, 0, 0, 18, 18, 0x0E1410, 1);
        frame(g, 0, 0, 18, 18, 0x5A4A22, 1);
        corners(g, 0, 0, 18, 18, 4, GOLD, 1);
        fill(g, 1, 1, 17, 2, 0x04090E, 1);
        fill(g, 1, 1, 2, 17, 0x04090E, 1);
        g.setColor(c(GOLD, 0.16));
        g.fillPolygon(new Polygon(new int[] { 9, 14, 9, 4 }, new int[] { 4, 9, 14, 9 }, 4));
        save(img, GUI + "slot_sample");
    }

    static void button(boolean on, String name) throws Exception {
        BufferedImage img = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        fill(g, 0, 0, 18, 18, on ? 0x16465A : 0x112432, 1);
        fill(g, 1, 1, 17, 2, on ? 0x2A7E98 : 0x1E3A4C, 1);
        frame(g, 0, 0, 18, 18, on ? CYAN : 0x2A5068, 1);
        if (on) corners(g, 0, 0, 18, 18, 4, 0xC8F6FF, 1);
        save(img, GUI + name);
    }

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
        save(img, GUI + "icon_holo");
    }

    /** Auto output: an arrow out of a box. */
    static void iconOutput() throws Exception {
        BufferedImage img = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c(0x9AB0BE, 1));
        g.drawRect(3, 6, 7, 7);
        g.setColor(c(0x6CFF8A, 1));
        g.fillRect(8, 9, 6, 2);
        g.fillPolygon(new Polygon(new int[] { 13, 16, 13 }, new int[] { 6, 10, 14 }, 3));
        save(img, GUI + "icon_output");
    }

    /** A cyan diamond around an echo ring. */
    static void logo() throws Exception {
        BufferedImage img = new BufferedImage(17, 17, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 17; y++) for (int x = 0; x < 17; x++) {
            int d = Math.abs(x - 8) + Math.abs(y - 8);
            int c = 0;
            if (d == 8 || d == 7) c = d == 8 ? 0xFF4FE3FF : 0xFF2A8FB0;
            double r = Math.hypot(x - 8, y - 8);
            if (r >= 2.6 && r < 3.6) c = 0xFF8A5CFF;
            if (r < 1.5) c = 0xFFEFFFFF;
            if (c != 0) img.setRGB(x, y, c);
        }
        save(img, GUI + "logo");
    }

    /** The soft round spot the world effects and trails are drawn with: white, tinted when drawn. */
    static void mote() throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double r = Math.hypot(x - 7.5, y - 7.5) / 7.5;
            if (r >= 1) continue;
            double a = Math.pow(1 - r, 1.6);
            if (r < 0.25) a = Math.min(1, a + 0.25);
            img.setRGB(x, y, (int) Math.round(a * 255) << 24 | 0xFFFFFF);
        }
        save(img, "effects/mote");
    }

    static void save(BufferedImage img, String name) throws Exception {
        File f = new File(ROOT + name + ".png");
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
        System.out.println("wrote " + f);
    }
}
