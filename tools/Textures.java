import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the mod's 16x16 textures. Run from the repository root: {@code java tools/Textures.java}.
 * Machine overlays go on top of GT's casings, so everything outside the drawn parts stays transparent. Each face comes
 * in three images: idle, working, and the working parts that glow in the dark.
 */
public class Textures {

    static final String ROOT = "src/main/resources/assets/fluxecho/textures/";

    interface Face {

        /** Colour of the pixel (0 for transparent); sets glow[0] when it should glow while working. */
        int at(int x, int y, boolean active, boolean[] glow);
    }

    public static void main(String[] args) throws Exception {
        face("blocks/machines/top", Textures::echoRing);
        face("blocks/machines/bee_imprinter/front", Textures::honeyScanner);
        face("blocks/machines/larva_incubator/front", Textures::incubator);
        item("items/bee_imprint", Textures::beeImprint);
        face("blocks/machines/essentia_echo/front", Textures::essentiaVortex);
        face("blocks/machines/vis_charger/front", Textures::visCharger);
        item("blocks/essentia_outlet", Textures::outlet);
        face("blocks/machines/blood_echo/front", Textures::bloodDrop);
        face("blocks/machines/insight_echo/front", Textures::insightBook);
        face("blocks/machines/crucible_echo/front", Textures::crucible);
    }

    static int rgb(int c) {
        return 0xFF000000 | c;
    }

    static double dist(double x, double y) {
        return Math.hypot(x - 7.5, y - 7.5);
    }

    /** Hexagon "radius" with flat top and bottom. */
    static double hex(double x, double y) {
        double dx = Math.abs(x - 7.5), dy = Math.abs(y - 7.5);
        return Math.max(dy * 1.1547, dx + dy * 0.57735);
    }

    static void face(String name, Face f) throws Exception {
        for (int mode = 0; mode < 3; mode++) {
            boolean active = mode > 0, glowOnly = mode == 2;
            BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                boolean[] glow = { false };
                int c = f.at(x, y, active, glow);
                if (c != 0 && (!glowOnly || glow[0])) img.setRGB(x, y, rgb(c));
            }
            save(img, name + (mode == 0 ? "" : mode == 1 ? "_active" : "_active_glow"));
        }
    }

    static void item(String name, Face f) throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int c = f.at(x, y, false, new boolean[1]);
            if (c != 0) img.setRGB(x, y, rgb(c));
        }
        save(img, name);
    }

    /** The echo ring on every machine's top: a pearl in a steel frame; working, the flux layer ripples out of it. */
    static int echoRing(int x, int y, boolean active, boolean[] glow) {
        boolean frame = x >= 2 && x <= 13 && y >= 2 && y <= 13 && (x == 2 || x == 13 || y == 2 || y == 13);
        if (frame) return (x == 2 || y == 2) ? 0x7A8494 : 0x3A404A;
        if (x < 3 || x > 12 || y < 3 || y > 12) return 0;
        double d = dist(x, y);
        if (d < 1.8) {
            glow[0] = active;
            return active ? 0xC8FFF0 : 0x1F4A44;
        }
        if (d < 2.8) {
            glow[0] = active;
            return active ? 0x4FE3C1 : 0x173431;
        }
        boolean ring = (d >= 3.6 && d < 4.4) || (d >= 5.2 && d < 5.9);
        if (ring) {
            glow[0] = active;
            return active ? (d < 4.4 ? 0x2FB3A0 : 0x5A3CC4) : 0x2A2F38;
        }
        return 0x14181F;
    }

    /** Bee Imprinter: a honeycomb cell with a scanning line across it. */
    static int honeyScanner(int x, int y, boolean active, boolean[] glow) {
        double h = hex(x, y);
        if (h >= 4.6 && h < 5.8) return (y < 8) ? 0xE0A21F : 0xA66F12;
        if (h >= 5.8) return 0;
        if (y == 7 || y == 8) {
            glow[0] = active;
            return active ? (x == 7 || x == 8 ? 0xE6FFFF : 0x6FE6FF) : 0x2B3A44;
        }
        if (active && h < 2.2) {
            glow[0] = true;
            return 0xFFD25A;
        }
        return active ? 0x5A3B0C : 0x2A1E0C;
    }

    /** Larva Incubator: a warm window with a larva curled up in honey. */
    static int incubator(int x, int y, boolean active, boolean[] glow) {
        boolean inBox = x >= 3 && x <= 12 && y >= 3 && y <= 12;
        boolean corner = (x == 3 || x == 12) && (y == 3 || y == 12);
        if (!inBox || corner) return 0;
        if (x == 3 || x == 12 || y == 3 || y == 12) return (x == 3 || y == 3) ? 0x9FB7C4 : 0x55666F;
        double d = Math.hypot(x - 7.5, y - 7.5);
        double a = Math.toDegrees(Math.atan2(y - 7.5, x - 7.5));
        boolean body = d >= 1.2 && d < 3.6 && !(a > -35 && a < 35);
        if (body) {
            glow[0] = active;
            boolean band = ((int) Math.floor((a + 180) / 30)) % 2 == 0;
            return active ? (band ? 0xFFF4D6 : 0xF2D9A0) : (band ? 0xB8AE98 : 0x8C7E66);
        }
        glow[0] = active && y > 9;
        return active ? (y > 9 ? 0xF0A830 : 0xB8781A) : (y > 9 ? 0x6B4710 : 0x3E2A0A);
    }

    /** Essentia Echo: a glass ring with essentia swirling out of nothing. */
    static int essentiaVortex(int x, int y, boolean active, boolean[] glow) {
        double d = dist(x, y);
        if (d >= 5.0 && d < 6.2) return (x + y < 15) ? 0xB9C8D6 : 0x5E6B78;
        if (d >= 5.0) return 0;
        double a = Math.atan2(y - 7.5, x - 7.5);
        double swirl = Math.sin(a * 2 + d * 1.4);
        if (!active) return swirl > 0.3 ? 0x2A1F3D : 0x161320;
        glow[0] = true;
        if (d < 1.3) return 0xFFFFFF;
        if (swirl > 0.5) return d < 3 ? 0xE08CFF : 0xA34FE0;
        if (swirl > -0.2) return d < 3 ? 0x7FF5E0 : 0x34B8A8;
        return 0x3A2466;
    }

    /** Vis Charger: a wand across the face, the six primals lit around it. */
    static int visCharger(int x, int y, boolean active, boolean[] glow) {
        int[][] dots = { { 3, 7 }, { 5, 3 }, { 10, 3 }, { 12, 8 }, { 10, 12 }, { 5, 12 } };
        int[] colors = { 0xFFFF7E, 0x8BC34A, 0xFF5A01, 0x3CD4FC, 0xD5D4EC, 0x404040 };
        for (int i = 0; i < dots.length; i++) if (x == dots[i][0] && y == dots[i][1]) {
            glow[0] = active;
            return active ? colors[i] : 0x3A404A;
        }
        if ((x == 10 || x == 11) && (y == 4 || y == 5)) {
            glow[0] = active;
            return active ? 0xF4EBFF : 0x6A5A80;
        }
        boolean shaft = Math.abs((x - 7.5) + (y - 7.5)) < 1.2 && x >= 4 && x <= 10 && y >= 5 && y <= 11;
        if (shaft) {
            if (x <= 5 || y <= 5) return 0xD4AF37;
            glow[0] = active;
            return active ? 0xB07CFF : 0x5B4A3A;
        }
        return 0;
    }

    /** Essentia Outlet: a steel block with a tube mouth; the ring glows teal and purple. */
    static int outlet(int x, int y, boolean active, boolean[] glow) {
        if (x == 0 || y == 0) return 0x8E97A3;
        if (x == 15 || y == 15) return 0x3D434C;
        double d = dist(x, y);
        if (d < 2.2) return 0x0E0B16;
        if (d < 3.4) return 0x7A6A9E;
        if (d < 4.4) return (x + y) % 2 == 0 ? 0x2FB3A0 : 0x5A3CC4;
        if (d < 5.2) return 0x4B525E;
        return ((x * 3 + y * 5) % 7 == 0) ? 0x5E6672 : 0x69717D;
    }

    /** Blood Echo: a drop of blood in a steel ring; working, it pulses. */
    static int bloodDrop(int x, int y, boolean active, boolean[] glow) {
        double d = dist(x, y);
        if (d >= 5.4 && d < 6.4) return (x + y < 15) ? 0x8A929C : 0x40464F;
        if (d >= 5.4) return 0;
        // drop: a circle at the bottom narrowing to a point at the top
        double cx = x - 7.5, cy = y - 8.6;
        boolean drop = cy >= 0 ? Math.hypot(cx, cy) < 3.0 : Math.abs(cx) < 3.0 * (1 + cy / 5.0) && cy > -5.0;
        if (drop) {
            glow[0] = active;
            boolean shine = x == 6 && (y == 8 || y == 9);
            if (shine) return active ? 0xFFC0C0 : 0x8A4A4A;
            return active ? (cy > 1.5 ? 0xB0101A : 0xE0202A) : (cy > 1.5 ? 0x4A0A0E : 0x6E1218);
        }
        return 0x1A1012;
    }

    /** Insight Echo: an open book with an eye on its pages; working, the eye opens and glows. */
    static int insightBook(int x, int y, boolean active, boolean[] glow) {
        if (y < 4 || y > 12 || x < 2 || x > 13) return 0;
        if (y == 12 || x == 2 || x == 13) return 0x6B3A1E;
        if (x == 7 || x == 8) return y == 4 ? 0 : 0x8C7B5A;
        double ex = (x - 7.5) / 4.2, ey = (y - 7.8) / 1.9;
        double e = ex * ex + ey * ey;
        if (e < 1.0) {
            if (Math.hypot(x - 7.5, y - 7.8) < 1.6) {
                glow[0] = active;
                return active ? 0xC86BFF : 0x3A2E4A;
            }
            glow[0] = active;
            return active ? 0xE9D8FF : 0xBDB39A;
        }
        return (y % 2 == 0 && x != 3 && x != 12) ? 0xCFC5A8 : 0xE6DDC2;
    }

    /** Crucible Echo: an iron crucible; working, essentia bubbles in it. */
    static int crucible(int x, int y, boolean active, boolean[] glow) {
        boolean rim = y == 5 && x >= 2 && x <= 13;
        boolean body = y >= 6 && y <= 12 && x >= 3 + (y >= 11 ? 1 : 0) && x <= 12 - (y >= 11 ? 1 : 0);
        boolean legs = y == 13 && (x == 4 || x == 11);
        if (rim) return 0x6E747C;
        if (legs) return 0x3A3E44;
        if (!body) {
            if (active && y >= 2 && y <= 4 && ((x == 6 && y == 3) || (x == 9 && y == 2) || (x == 8 && y == 4))) {
                glow[0] = true;
                return 0xB98CFF;
            }
            return 0;
        }
        if (x == 3 || x == 12 || (y >= 11 && (x == 4 || x == 11)) || y == 12) return 0x40454C;
        if (y == 6) {
            glow[0] = active;
            return active ? ((x % 3 == 0) ? 0xE0B0FF : 0x8E4FE0) : 0x2A2235;
        }
        return (x + y) % 4 == 0 ? 0x2C3036 : 0x34393F;
    }

    /** Bee imprint: a paper card with a honeycomb and the teal mark of the flux layer. */
    static int beeImprint(int x, int y, boolean active, boolean[] glow) {
        if (x < 3 || x > 12 || y < 2 || y > 13) return 0;
        if (x == 3 || x == 12 || y == 2 || y == 13) return 0x9C8A64;
        if (y == 3) return (x >= 5 && x <= 10) ? 0x2FB3A0 : 0xE8DDC0;
        double h = hex(x, y + 0.5);
        if (h >= 2.6 && h < 3.6) return 0xD18F18;
        if (h < 1.6) return 0xF2C34A;
        return (x + y) % 5 == 0 ? 0xDCD0B0 : 0xE8DDC0;
    }

    static void save(BufferedImage img, String name) throws Exception {
        File f = new File(ROOT + name + ".png");
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
    }
}
