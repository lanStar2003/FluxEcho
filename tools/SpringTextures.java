import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import javax.imageio.ImageIO;

/**
 * Draws the Mana Echo Spring's block faces and GUI, in the flux world's look shared with FluxDepths' collector: dark
 * plates with flux seams, and the mana's own blue with Botania's pink sparkles. Run from the repository root:
 * {@code java tools/SpringTextures.java}. GUI positions match {@code ManaGui}.
 */
public class SpringTextures {

    static final String ROOT = "src/main/resources/assets/fluxecho/textures/";
    static final String BLOCK = "blocks/machines/mana_echo/", GUI = "gui/spring/";

    static final int DEEP = 0x0A1622, DEEPER = 0x070F18, PANE = 0x0D1D2A, SEAM = 0x1F3C4E, CYAN = 0x4FE3FF,
        VIOLET = 0x8A5CFF, GRID = 0x10283A, MANA = 0x46C8FF, MANA_LIGHT = 0xA8ECFF, PINK = 0xFF8CE6;

    public static void main(String[] args) throws Exception {
        casing("side", BLOCK + "casing_side");
        casing("top", BLOCK + "casing_top");
        casing("bottom", BLOCK + "casing_bottom");
        strip(BLOCK + "strip");
        springStill(BLOCK + "spring");
        springAnimated(false, BLOCK + "spring_active");
        springAnimated(true, BLOCK + "spring_active_glow");
        grate(false, false, BLOCK + "grate");
        grate(true, false, BLOCK + "grate_active");
        grate(true, true, BLOCK + "grate_active_glow");

        background();
        slot("slot", null);
        slot("slot_core", "core");
        slot("slot_petal", "petal");
        slot("slot_charge", "charge");
        button(false, "button");
        button(true, "button_on");
        iconHolo();
        logo();
    }

    static int rgb(int c) {
        return 0xFF000000 | c;
    }

    // ---- block faces, 16 x 16

    /** The spring's own casing (as the collector's): dark plates, a bevel, flux seams and rivets. */
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
                    if (y == 2 && x >= 3 && x <= 12 || y == 13 && x >= 3 && x <= 12) c = 0x163A4E;
                }
                case "top" -> {
                    if (x >= 2 && x <= 13 && (y == 2 || y == 13) || y >= 2 && y <= 13 && (x == 2 || x == 13))
                        c = 0x163A4E;
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

    /** The side strip, white so GT tints it in the core's colour: a rail with a droplet in the middle. */
    static void strip(String name) throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 3; y <= 12; y++) for (int x = 6; x <= 9; x++) {
            int c = 0;
            if (x == 7 || x == 8) c = (y == 3 || y == 12) ? 0x9A9A9A : 0xE6E6E6;
            double d = Math.hypot(x - 7.5, y - 8.5);
            if (y >= 5 && y <= 10 && (d < 2.2 || y <= 6 && Math.abs(x - 7.5) < (y - 4) * 0.8)) c = d < 1.2 ? 0xFFFFFF : 0xD0D0D0;
            if (c != 0) img.setRGB(x, y, rgb(c));
        }
        save(img, name);
    }

    /** The front: a round well in a flux ring; dark and still without power. */
    static void springStill(String name) throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        drawSpring(img, 0, false, false, 0);
        save(img, name);
    }

    /** The working front: mana welling up, Botania's sparkles rising through it; eight frames. */
    static void springAnimated(boolean glowOnly, String name) throws Exception {
        int frames = 8;
        BufferedImage img = new BufferedImage(16, 16 * frames, BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < frames; f++) drawSpring(img, f * 16, true, glowOnly, f);
        save(img, name);
        mcmeta(name);
    }

    static void drawSpring(BufferedImage img, int oy, boolean active, boolean glowOnly, int frame) {
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double d = Math.hypot(x - 7.5, y - 7.5);
            int c = 0;
            boolean glow = false;
            if (d >= 5.4 && d < 7.3) {
                c = (x + y < 15) ? 0x7A8494 : 0x2E333B;
                if (d >= 6.5) c = 0x163A4E;
            } else if (d < 5.4) {
                if (!active) {
                    c = d < 2 ? 0x16364A : ((x * 7 + y * 3) % 11 == 0 ? 0x1A3444 : 0x0D1520);
                } else {
                    // rings welling outwards, sparkles rising
                    double wave = Math.sin(d * 1.9 - frame * Math.PI / 4);
                    c = d < 1.3 ? 0xEFFFFF : wave > 0.35 ? MANA_LIGHT : wave > -0.3 ? MANA : 0x1E6FA8;
                    glow = d < 4.6;
                    int sy = Math.floorMod(10 - frame * 2 + (x * 5) % 7, 11);
                    if ((x == 5 || x == 10) && y == sy + 2 && d < 5) {
                        c = PINK;
                        glow = true;
                    }
                }
            }
            if (c != 0 && (!glowOnly || glow)) img.setRGB(x, oy + y, rgb(c));
        }
    }

    /** The top: a fountain grate, its slits lit with mana while it works. */
    static void grate(boolean active, boolean glowOnly, String name) throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 3; y <= 12; y++) for (int x = 3; x <= 12; x++) {
            boolean frame = x == 3 || x == 12 || y == 3 || y == 12;
            double d = Math.hypot(x - 7.5, y - 7.5);
            boolean ring = !frame && d >= 2.6 && d < 3.6;
            boolean core = !frame && d < 1.6;
            int c;
            boolean glow = false;
            if (frame) c = (x == 3 || y == 3) ? 0x7A8494 : 0x3A404A;
            else if (ring || core) {
                if (active) {
                    c = core ? 0xEFFFFF : MANA;
                    glow = true;
                } else c = 0x161A22;
            } else c = 0x2A2F38;
            if (!glowOnly || glow) img.setRGB(x, y, rgb(c));
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

    /** The window, 176 x 220: header, the spring's basin, the pools, the readout, the inventory. */
    static void background() throws Exception {
        int w = 176, h = 220;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        for (int y = 0; y < h; y++) fill(g, 0, y, w, y + 1, mix(DEEP, DEEPER, (double) y / h), 1);
        // the mana's glow welling up behind the basin
        for (int y = 0; y < 90; y++) for (int x = 20; x < 120; x++) {
            double d = Math.hypot((x - 67) / 52.0, (y - 86) / 70.0);
            if (d < 1) {
                g.setColor(c(MANA, 0.10 * (1 - d)));
                g.fillRect(x, y, 1, 1);
            }
        }
        frame(g, 0, 0, w, h, 0x2A4A5E, 1);
        frame(g, 1, 1, w - 1, h - 1, 0x0F1F2B, 1);
        corners(g, 2, 2, w - 2, h - 2, 9, CYAN, 1);
        for (int[] p : new int[][] { { 0, 0 }, { w - 1, 0 }, { 0, h - 1 }, { w - 1, h - 1 } }) img.setRGB(p[0], p[1], 0);

        pane(g, 6, 5, 170, 19, false);
        pane(g, 6, 22, 170, 85, true);
        // traces: core and petals into the basin, the basin out to the pools
        g.setColor(c(MANA, 0.25));
        g.fillRect(27, 34, 3, 1);
        g.fillRect(27, 54, 3, 1);
        g.fillRect(27, 74, 3, 1);
        g.fillRect(104, 52, 3, 1);
        g.fillRect(146, 34, 3, 1);
        fill(g, 30, 22, 104, 84, DEEPER, 1);
        fill(g, 106, 22, 146, 84, DEEPER, 0.7);
        frame(g, 106, 22, 146, 84, SEAM, 1);
        pane(g, 6, 88, 170, 136, false);
        fill(g, 7, 124, 169, 125, SEAM, 1);
        pane(g, 5, 137, 171, 216, false);
        fill(g, 8, 194, 168, 195, SEAM, 0.8);
        save(img, GUI + "background");
    }

    static void slot(String name, String ghost) throws Exception {
        BufferedImage img = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        fill(g, 0, 0, 18, 18, 0x08121B, 1);
        frame(g, 0, 0, 18, 18, 0x23465A, 1);
        fill(g, 1, 1, 17, 2, 0x04090E, 1);
        fill(g, 1, 1, 2, 17, 0x04090E, 1);
        fill(g, 1, 16, 17, 17, MANA, 0.35);
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
                case "petal" -> {
                    g.setColor(c(0xC08AB8, 0.5));
                    g.fillPolygon(new Polygon(new int[] { 9, 13, 9, 5 }, new int[] { 3, 8, 15, 8 }, 4));
                    g.setColor(c(0x08121B, 1));
                    g.fillRect(9, 6, 1, 7);
                }
                case "charge" -> {
                    g.fillRoundRect(5, 3, 8, 12, 3, 3);
                    g.setColor(c(MANA, 0.6));
                    g.fillPolygon(new Polygon(new int[] { 9, 11, 9, 7 }, new int[] { 6, 9, 12, 9 }, 4));
                }
                default -> {}
            }
        }
        save(img, GUI + name);
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

    /** A cyan diamond around a mana droplet. */
    static void logo() throws Exception {
        BufferedImage img = new BufferedImage(17, 17, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 17; y++) for (int x = 0; x < 17; x++) {
            int d = Math.abs(x - 8) + Math.abs(y - 8);
            int c = 0;
            if (d == 8 || d == 7) c = d == 8 ? 0xFF4FE3FF : 0xFF2A8FB0;
            double r = Math.hypot(x - 8, y - 9.5);
            if (r < 2.6 || y >= 4 && y <= 8 && Math.abs(x - 8) <= (y - 4) * 0.55) c = r < 1.2 ? 0xFFEFFFFF : 0xFF46C8FF;
            if (x == 11 && y == 5 || x == 5 && y == 11) c = 0xFFFF8CE6;
            if (c != 0) img.setRGB(x, y, c);
        }
        save(img, GUI + "logo");
    }

    static void mcmeta(String name) throws Exception {
        File f = new File(ROOT + name + ".png.mcmeta");
        Files.write(f.toPath(), "{\n  \"animation\": { \"frametime\": 3 }\n}\n".getBytes());
        System.out.println("wrote " + f);
    }

    static void save(BufferedImage img, String name) throws Exception {
        File f = new File(ROOT + name + ".png");
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
        System.out.println("wrote " + f);
    }
}
