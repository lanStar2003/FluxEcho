import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the mod's 16x16 textures. Run from the repository root: {@code java tools/DepthsTextures.java}.
 * Block overlays go on top of GT's casings, so everything outside the drawn parts stays transparent.
 */
public class DepthsTextures {

    static final String ROOT = "src/main/resources/assets/fluxdepths/textures/";

    public static void main(String[] args) throws Exception {
        lens(false, false, "blocks/collector/front");
        lens(true, false, "blocks/collector/front_active");
        lens(true, true, "blocks/collector/front_active_glow");
        grille(false, false, "blocks/collector/top");
        grille(true, false, "blocks/collector/top_active");
        grille(true, true, "blocks/collector/top_active_glow");
        pump(false, false, "blocks/collector/pump_front");
        pump(true, false, "blocks/collector/pump_front_active");
        pump(true, true, "blocks/collector/pump_front_active_glow");
        casing("side", "blocks/collector/casing_side");
        casing("top", "blocks/collector/casing_top");
        casing("bottom", "blocks/collector/casing_bottom");
        strip("blocks/collector/strip");
        core(false, false, "blocks/collector/core");
        coreAnimated(false, "blocks/collector/core_active");
        coreAnimated(true, "blocks/collector/core_active_glow");
        imprinter("items/imprinter");
        imprint("items/imprint");
    }

    static int rgb(int c) {
        return 0xFF000000 | c;
    }

    static double dist(double x, double y) {
        return Math.hypot(x - 7.5, y - 7.5);
    }

    /** The pinhole: a steel ring, dark inside; when active the shard's light swirls in it. */
    static void lens(boolean active, boolean glowOnly, String name) throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double d = dist(x, y);
            int c = 0;
            boolean glow = false;
            if (d >= 4.6 && d < 6.3) {
                c = (x + y < 14) ? 0x7A8494 : (x + y > 16 ? 0x2E333B : 0x4B525E);
            } else if (d < 4.6) {
                if (!active) {
                    c = ((x * 7 + y * 3) % 11 == 0) ? 0x1E3442 : 0x10141C;
                } else {
                    double a = Math.atan2(y - 7.5, x - 7.5);
                    double swirl = Math.sin(a * 3 + d * 1.7);
                    if (d < 1.4) c = 0xEFFFFF;
                    else if (d < 2.5) c = swirl > -0.2 ? 0x8FEAFF : 0x5FC8FF;
                    else if (d < 3.6) c = swirl > 0.1 ? 0x5A86FF : 0x3B55D8;
                    else c = swirl > 0.4 ? 0x5B3CC4 : 0x2A1D5E;
                    glow = d < 3.6;
                }
            } else if ((x == 2 || x == 13) && (y == 2 || y == 13)) {
                c = 0x5A616D;
            }
            if (c != 0 && (!glowOnly || glow)) img.setRGB(x, y, rgb(c));
        }
        save(img, name);
    }

    /** The resonance grille on top: slits that light up while the collector listens. */
    static void grille(boolean active, boolean glowOnly, String name) throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 3; y <= 12; y++) for (int x = 3; x <= 12; x++) {
            boolean frame = x == 3 || x == 12 || y == 3 || y == 12;
            boolean slit = !frame && y % 2 == 1 && x >= 5 && x <= 10;
            int c = 0;
            boolean glow = false;
            if (frame) c = (x == 3 || y == 3) ? 0x7A8494 : 0x3A404A;
            else if (slit) {
                if (active) {
                    c = (x == 7 || x == 8) ? 0xC8F6FF : 0x5FD6FF;
                    glow = true;
                } else c = 0x161A22;
            } else c = 0x2A2F38;
            if (c != 0 && (!glowOnly || glow)) img.setRGB(x, y, rgb(c));
        }
        save(img, name);
    }

    /** The fluid pump's front: a bolted flange around the pinhole; when active, the echoed fluid ripples in it. */
    static void pump(boolean active, boolean glowOnly, String name) throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 1; y <= 14; y++) for (int x = 1; x <= 14; x++) {
            double d = dist(x, y);
            boolean flange = x <= 2 || x >= 13 || y <= 2 || y >= 13;
            boolean bolt = (x == 2 || x == 13) && (y == 2 || y == 13) || (x == 2 || x == 13) && (y == 7 || y == 8)
                || (y == 2 || y == 13) && (x == 7 || x == 8);
            int c = 0;
            boolean glow = false;
            if (bolt) c = 0xA9B2BF;
            else if (flange) c = (x <= 2 || y <= 2) ? 0x6B7380 : 0x353A43;
            else if (d >= 3.9) c = d < 4.9 ? ((x + y < 15) ? 0x7A8494 : 0x2E333B) : 0x272B33;
            else if (!active) c = ((x * 5 + y * 3) % 9 == 0) ? 0x1E3442 : 0x10141C;
            else {
                double wave = Math.sin(d * 2.2 - Math.atan2(y - 7.5, x - 7.5));
                if (d < 1.2) c = 0xE6FFF6;
                else c = wave > 0.3 ? 0x7FF0D0 : (wave > -0.4 ? 0x2FC0A8 : 0x16736A);
                glow = d < 3.2;
            }
            if (c != 0 && (!glowOnly || glow)) img.setRGB(x, y, rgb(c));
        }
        save(img, name);
    }

    /**
     * The Flux Shard Collector's own casing (it is not a GT hull): dark plates with a bevel, flux seams and rivets.
     * The side leaves its middle for the tier strip, the top for the grille.
     */
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
                    if (y == 2 && x >= 3 && x <= 12 || y == 13 && x >= 3 && x <= 12) c = 0x16324A;
                }
                case "top" -> {
                    if (x >= 2 && x <= 13 && (y == 2 || y == 13) || y >= 2 && y <= 13 && (x == 2 || x == 13))
                        c = 0x16324A;
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

    /** The side's glow strip, white so GT can tint it in the tier's colour: a rail with a diamond in the middle. */
    static void strip(String name) throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 3; y <= 12; y++) for (int x = 6; x <= 9; x++) {
            int d = Math.abs(x * 2 - 15) + Math.abs(y * 2 - 15);
            int c = 0;
            if (x == 7 || x == 8) c = (y == 3 || y == 12) ? 0x9A9A9A : 0xE6E6E6;
            if (d <= 5) c = d <= 2 ? 0xFFFFFF : 0xD0D0D0;
            if (c != 0) img.setRGB(x, y, rgb(c));
        }
        save(img, name);
    }

    /** The core lens in front: an octagonal frame around a dark well. */
    static void core(boolean active, boolean glowOnly, String name) throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        drawCore(img, 0, active, glowOnly, 0);
        save(img, name);
    }

    /** The lit core, eight frames turning (with an .mcmeta so Minecraft animates it). */
    static void coreAnimated(boolean glowOnly, String name) throws Exception {
        int frames = 8;
        BufferedImage img = new BufferedImage(16, 16 * frames, BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < frames; f++) drawCore(img, f * 16, true, glowOnly, f * Math.PI * 2 / frames / 3);
        save(img, name);
        File meta = new File(ROOT + name + ".png.mcmeta");
        java.nio.file.Files.write(meta.toPath(), "{\n  \"animation\": { \"frametime\": 2 }\n}\n".getBytes());
        System.out.println("wrote " + meta);
    }

    static void drawCore(BufferedImage img, int oy, boolean active, boolean glowOnly, double turn) {
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double dx = x - 7.5, dy = y - 7.5;
            double oct = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), (Math.abs(dx) + Math.abs(dy)) / 1.414);
            double d = Math.hypot(dx, dy);
            int c = 0;
            boolean glow = false;
            if (oct >= 5.6 && oct < 7.4) {
                c = dx + dy < 0 ? 0x7A8494 : 0x2E333B;
                if (oct >= 6.6) c = 0x16324A;
            } else if (oct < 5.6) {
                if (!active) {
                    c = d < 1.5 ? 0x24495E : ((x * 7 + y * 3) % 11 == 0 ? 0x1E3442 : 0x0E1219);
                } else {
                    double a = Math.atan2(dy, dx) + turn;
                    double swirl = Math.sin(a * 3 - d * 1.3);
                    if (d < 1.3) c = 0xF2FFFF;
                    else if (d < 2.6) c = swirl > -0.2 ? 0x9AF0FF : 0x5FC8FF;
                    else if (d < 3.9) c = swirl > 0.1 ? 0x6A8CFF : 0x3E52D8;
                    else c = swirl > 0.45 ? 0x7A4CE0 : 0x2A1D5E;
                    glow = d < 3.9 || swirl > 0.45;
                }
            }
            if (c != 0 && (!glowOnly || glow)) img.setRGB(x, oy + y, rgb(c));
        }
    }

    /** A bronze rod with a lens at its tip, like a tuning fork that listens to the ground. */
    static void imprinter(String name) throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int i = 2; i <= 9; i++) {
            img.setRGB(i, 15 - i, rgb(0xC8913F));
            img.setRGB(i + 1, 15 - i, rgb(0x7A5522));
            if (i % 3 == 0) img.setRGB(i, 14 - i, rgb(0xE8B866));
        }
        img.setRGB(1, 14, rgb(0x7A5522));
        img.setRGB(1, 13, rgb(0x5C3F18));
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double d = Math.hypot(x - 11.5, y - 4.5);
            if (d >= 2.4 && d < 3.6) img.setRGB(x, y, rgb(x + y < 16 ? 0xE0AA55 : 0x8F6428));
            else if (d < 2.4) img.setRGB(x, y, rgb(d < 1.2 ? 0xE6FCFF : 0x7FDFFF));
        }
        save(img, name);
    }

    /** A sheet of paper with the vein's echo traced on it. */
    static void imprint(String name) throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 2; y <= 13; y++) for (int x = 3; x <= 12; x++) {
            boolean edge = x == 3 || x == 12 || y == 2 || y == 13;
            img.setRGB(x, y, rgb(edge ? 0xBDB296 : 0xEFE8D3));
        }
        img.setRGB(12, 2, 0);
        img.setRGB(11, 3, rgb(0xBDB296));
        int[][] vein = { { 5, 11 }, { 6, 10 }, { 6, 9 }, { 7, 8 }, { 8, 8 }, { 8, 7 }, { 9, 6 }, { 9, 5 }, { 10, 4 } };
        for (int[] p : vein) img.setRGB(p[0], p[1], rgb(0x2FA9D3));
        int[][] ore = { { 5, 7 }, { 7, 5 }, { 10, 9 }, { 8, 11 } };
        for (int[] p : ore) img.setRGB(p[0], p[1], rgb(0x6A4FD8));
        save(img, name);
    }

    static void save(BufferedImage img, String name) throws Exception {
        File f = new File(ROOT + name + ".png");
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
        System.out.println("wrote " + f);
    }
}
