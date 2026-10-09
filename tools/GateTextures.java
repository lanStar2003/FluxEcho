import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the light gate's threshold plate and its rooms' shell (floor, rim, wall, ceiling, pillar, light, trim) and
 * glass, in the colours of {@code tools/FluxTextures.java}. Run from the repository root:
 * {@code java tools/GateTextures.java}.
 */
public class GateTextures {

    static final String ROOT = "src/main/resources/assets/fluxecho/textures/blocks/gate/";

    static final int DEEP = 0x0A1622, DEEPER = 0x070F18, PANE = 0x0D1D2A, SEAM = 0x1F3C4E, CYAN = 0x4FE3FF,
        VIOLET = 0x8A5CFF, GRID = 0x10283A;

    public static void main(String[] args) throws Exception {
        thresholdTop();
        thresholdSide();
        floor();
        rim();
        wall();
        ceiling();
        pillar();
        light();
        trim();
        glass();
    }

    static int rgb(int c) {
        return 0xFF000000 | c;
    }

    static int mix(int a, int b, double k) {
        int r = (int) Math.round((a >> 16 & 255) * (1 - k) + (b >> 16 & 255) * k);
        int g = (int) Math.round((a >> 8 & 255) * (1 - k) + (b >> 8 & 255) * k);
        int bl = (int) Math.round((a & 255) * (1 - k) + (b & 255) * k);
        return r << 16 | g << 8 | bl;
    }

    /** A dark plate with a glowing ring and a rune cross in the middle. */
    static void thresholdTop() throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int c = DEEP;
            if (x == 0 || y == 0 || x == 15 || y == 15) c = SEAM;
            double d = Math.hypot(x - 7.5, y - 7.5);
            if (d > 4.6 && d < 5.9) c = mix(CYAN, VIOLET, (Math.atan2(y - 7.5, x - 7.5) + Math.PI) / (2 * Math.PI));
            else if (d > 5.9 && d < 6.6) c = mix(DEEP, CYAN, 0.25);
            if (d < 2.6 && (Math.abs(x - 7.5) < 0.6 || Math.abs(y - 7.5) < 0.6)) c = CYAN;
            img.setRGB(x, y, rgb(c));
        }
        save(img, "threshold_top");
    }

    /** The plate's edge: dark, with a cyan line. */
    static void thresholdSide() throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int c = y >= 14 ? (y == 14 ? CYAN : SEAM) : DEEPER;
            img.setRGB(x, y, rgb(c));
        }
        save(img, "threshold_side");
    }

    /** Dark panels with a faint grid and a seam every half block. */
    static void floor() throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int c = ((x * 7 + y * 13) % 11 == 0) ? GRID : PANE;
            if (x == 0 || y == 0) c = SEAM;
            else if (x == 8 || y == 8) c = mix(PANE, SEAM, 0.6);
            if ((x == 0 || x == 8) && (y == 0 || y == 8)) c = mix(SEAM, CYAN, 0.45);
            img.setRGB(x, y, rgb(c));
        }
        save(img, "floor");
    }

    /** The glowing rim: a bright band between dark edges. */
    static void rim() throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double band = 1 - Math.abs(y - 7.5) / 7.5;
            int c = mix(DEEP, mix(CYAN, VIOLET, x / 15.0), Math.pow(band, 1.6));
            if (y == 0 || y == 15) c = SEAM;
            img.setRGB(x, y, rgb(c));
        }
        save(img, "rim");
    }

    /** Tall dark panels: a seam down one side and across the middle, a faint grain. */
    static void wall() throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int c = ((x * 5 + y * 3) % 13 == 0) ? GRID : DEEP;
            if (x == 0 || y == 0) c = SEAM;
            else if (y == 8) c = mix(DEEP, SEAM, 0.7);
            if (x == 0 && (y == 4 || y == 12)) c = mix(SEAM, CYAN, 0.5);
            img.setRGB(x, y, rgb(c));
        }
        save(img, "wall");
    }

    /** Coffered: a sunken square in each tile, lit at its corners. */
    static void ceiling() throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int c = PANE;
            boolean edge = x == 0 || y == 0 || x == 15 || y == 15;
            boolean inner = (x == 3 || x == 12) && y >= 3 && y <= 12 || (y == 3 || y == 12) && x >= 3 && x <= 12;
            if (edge) c = SEAM;
            else if (inner) c = mix(SEAM, CYAN, 0.25);
            else if (x > 3 && x < 12 && y > 3 && y < 12) c = DEEPER;
            if ((x == 3 || x == 12) && (y == 3 || y == 12)) c = mix(SEAM, CYAN, 0.7);
            img.setRGB(x, y, rgb(c));
        }
        save(img, "ceiling");
    }

    /** A rib: dark edges, a violet-to-cyan core running up it. */
    static void pillar() throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double core = 1 - Math.abs(x - 7.5) / 7.5;
            int c = mix(DEEPER, mix(VIOLET, CYAN, y / 15.0), Math.pow(core, 3) * 0.85);
            if (x == 0 || x == 15) c = SEAM;
            if (y == 0 || y == 15) c = mix(c, SEAM, 0.6);
            img.setRGB(x, y, rgb(c));
        }
        save(img, "pillar");
    }

    /** A light panel: pale cyan, brightest in the middle, in a thin dark frame. */
    static void light() throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double d = Math.max(Math.abs(x - 7.5), Math.abs(y - 7.5)) / 7.5;
            int c = mix(0xF2FEFF, CYAN, Math.pow(d, 2) * 0.8);
            if (x == 0 || y == 0 || x == 15 || y == 15) c = SEAM;
            else if (x == 1 || y == 1 || x == 14 || y == 14) c = mix(SEAM, CYAN, 0.6);
            img.setRGB(x, y, rgb(c));
        }
        save(img, "light");
    }

    /** A glowing band across the wall. */
    static void trim() throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double band = Math.max(0, 1 - Math.abs(y - 7.5) / 3.5);
            int c = mix(DEEP, mix(CYAN, 0xF2FEFF, band * 0.5), Math.pow(band, 1.4));
            if (y == 0 || y == 15) c = SEAM;
            if (band == 0 && x == 0) c = SEAM;
            img.setRGB(x, y, rgb(c));
        }
        save(img, "trim");
    }

    /** Clear glass with a faint teal tint and a thin frame. */
    static void glass() throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            boolean frame = x == 0 || y == 0 || x == 15 || y == 15;
            int a = frame ? 210 : (x + y == 6 || x + y == 7 ? 70 : 34);
            int c = frame ? mix(SEAM, CYAN, 0.45) : mix(0x9FE8F2, 0xFFFFFF, x + y == 6 || x + y == 7 ? 0.6 : 0);
            img.setRGB(x, y, a << 24 | c);
        }
        save(img, "glass");
    }

    static void save(BufferedImage img, String name) throws Exception {
        File f = new File(ROOT + name + ".png");
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
    }
}
