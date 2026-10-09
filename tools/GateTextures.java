import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the light gate's threshold plate and the flux interior's floor and rim, in the colours of
 * {@code tools/FluxTextures.java}. Run from the repository root: {@code java tools/GateTextures.java}.
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

    static void save(BufferedImage img, String name) throws Exception {
        File f = new File(ROOT + name + ".png");
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
    }
}
