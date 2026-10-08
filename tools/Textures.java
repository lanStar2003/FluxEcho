import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the mod's 16x16 textures. Run from the repository root: {@code java tools/Textures.java}.
 * Machine faces go on top of the flux casing (drawn by {@code tools/FluxTextures.java}), so everything outside the
 * drawn parts stays transparent. Each face comes in three images: idle, working (animated), and the working parts
 * that glow in the dark (animated alike).
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
        item("blocks/essentia_outlet", Textures::outlet);
        face("blocks/machines/blood_echo/front", Textures::bloodDrop);
        face("blocks/machines/insight_echo/front", Textures::insightBook);
        face("blocks/machines/crucible_echo/front", Textures::crucible);
        face("blocks/machines/mob_echo/front", Textures::skull);
        face("blocks/machines/infusion_echo/front", Textures::runicMatrix);
        face("blocks/machines/seed_imprinter/front", Textures::seedScanner);
        face("blocks/machines/seed_echo/front", Textures::sprout);
        item("items/mob_imprint", Textures::mobImprint);
        item("items/crop_imprint", Textures::cropImprint);
        item("items/codex", Textures::codex);
        item("blocks/echo_provider", Textures::provider);
        face("blocks/machines/gene_assembler/front", Textures::helixFrame);
        item("items/gene_sample", Textures::geneVial);
        item("blocks/vis_pedestal_top", Textures::pedestalTop);
        item("blocks/vis_pedestal_side", Textures::pedestalSide);
        item("blocks/vis_pedestal_bottom", Textures::pedestalBottom);
        item("items/vis_module_extraction", Textures::moduleExtraction);
        item("items/vis_module_wireless", Textures::moduleWireless);
        item("items/vis_module_link", Textures::moduleLink);
    }

    /** Thaumcraft's primal colours: aer, ignis, aqua, terra, ordo, perditio. */
    static final int[] PRIMALS = { 0xFFF27A, 0xFF5A1F, 0x3CD4FC, 0x56C000, 0xE6E4F4, 0x6A5A7A };

    /** Flux Vis Pedestal, top: a steel plate, a ring of the six primals around a violet lens. */
    static int pedestalTop(int x, int y, boolean active, boolean[] glow) {
        if (x == 0 || y == 0) return 0x8A929C;
        if (x == 15 || y == 15) return 0x30353D;
        if (x == 1 || y == 1 || x == 14 || y == 14) return 0x4A505A;
        double d = dist(x, y);
        double a = Math.atan2(y - 7.5, x - 7.5);
        if (d >= 4.4 && d < 5.6) {
            int sector = (int) Math.floor(((a + Math.PI) / (2 * Math.PI)) * 6 + 0.5) % 6;
            double centre = sector * Math.PI / 3 - Math.PI;
            double off = Math.abs(Math.atan2(Math.sin(a - centre), Math.cos(a - centre)));
            return off < 0.3 ? PRIMALS[sector] : 0x23272E;
        }
        if (d >= 5.6 && d < 6.2) return 0x5A616D;
        if (d < 1.3) return 0xF4EEFF;
        if (d < 2.4) return 0xB48CFF;
        if (d < 3.4) return 0x6A3CC4;
        if (d < 4.4) return (x + y) % 2 == 0 ? 0x1A1D24 : 0x161920;
        return 0x2A2F38;
    }

    /** Flux Vis Pedestal, side (the lower 12 rows show): a steel panel with glowing vis conduits. */
    static int pedestalSide(int x, int y, boolean active, boolean[] glow) {
        if (y == 4) return 0x8A929C;
        if (y == 5) return 0x5A616D;
        if (y == 15) return 0x2A2F38;
        if (x == 0 || x == 15) return 0x4A505A;
        boolean conduit = (x == 4 || x == 11) && y >= 7 && y <= 13;
        if (conduit) return y % 3 == 0 ? 0xC8F6FF : 0x5FD6FF;
        boolean core = x >= 6 && x <= 9 && y >= 8 && y <= 12;
        if (core) {
            if (x == 6 || x == 9 || y == 8 || y == 12) return 0x5A616D;
            return (x + y) % 2 == 0 ? 0xB48CFF : 0x8F6CFF;
        }
        if (y == 14) return 0x3A404A;
        return (x + y) % 7 == 0 ? 0x3E444E : 0x343941;
    }

    static int pedestalBottom(int x, int y, boolean active, boolean[] glow) {
        if (x == 0 || y == 0 || x == 15 || y == 15) return 0x30353D;
        return (x * 3 + y) % 9 == 0 ? 0x3E444E : 0x383D46;
    }

    /** A module card: steel with gold contacts; the emblem decides the rest. */
    static int moduleCard(int x, int y) {
        if (x < 2 || x > 13 || y < 2 || y > 13) return 0;
        if (y == 13) return x % 2 == 0 && x > 2 && x < 13 ? 0xE6B84A : 0;
        if (x == 2 || y == 2) return 0x8A929C;
        if (x == 13 || y == 12) return 0x30353D;
        return 0x3A404A;
    }

    /** Extraction module: a violet vortex with a stream rising out of it. */
    static int moduleExtraction(int x, int y, boolean active, boolean[] glow) {
        int c = moduleCard(x, y);
        if (c == 0 || y >= 12 || x == 2 || y == 2 || x == 13) return c;
        double d = Math.hypot(x - 7.5, (y - 9) * 1.6);
        if (x >= 7 && x <= 8 && y >= 3 && y <= 8) return y <= 4 ? 0xF4EEFF : 0x7FF0D0;
        if (y == 4 && (x == 6 || x == 9)) return 0x7FF0D0;
        if (d < 1.5) return 0xF4EEFF;
        if (d < 3.2) return 0xB48CFF;
        if (d < 4.6 && y >= 8) return 0x6A3CC4;
        return c;
    }

    /** Wireless charging module: waves going out from a vis node. */
    static int moduleWireless(int x, int y, boolean active, boolean[] glow) {
        int c = moduleCard(x, y);
        if (c == 0 || y >= 12 || x == 2 || y == 2 || x == 13) return c;
        double d = Math.hypot(x - 4.5, y - 10.5);
        if (d < 1.6) return 0xB48CFF;
        boolean upRight = x >= 4 && y <= 11;
        if (upRight && ((d >= 3.2 && d < 4.1) || (d >= 5.6 && d < 6.5) || (d >= 8.0 && d < 8.9)))
            return d < 5 ? 0x7FF0D0 : d < 7 ? 0x3CD4FC : 0x2FB3A0;
        return c;
    }

    /** Flux link module: an ender eye with a bolt of power. */
    static int moduleLink(int x, int y, boolean active, boolean[] glow) {
        int c = moduleCard(x, y);
        if (c == 0 || y >= 12 || x == 2 || y == 2 || x == 13) return c;
        int[][] bolt = { { 9, 3 }, { 8, 4 }, { 8, 5 }, { 7, 6 }, { 6, 7 }, { 7, 7 }, { 8, 7 }, { 7, 8 }, { 6, 9 },
            { 6, 10 }, { 5, 11 } };
        for (int[] p : bolt) if (p[0] == x && p[1] == y) return 0xFFE14A;
        double d = Math.hypot(x - 10.5, y - 9.5);
        if (d < 0.9) return 0x0E3B2E;
        if (d < 1.9) return 0x2FB3A0;
        return c;
    }

    /** Which strand of a double helix runs through (x, y), going down the middle of the face: 1 or 2, 0 for none. */
    static int strand(int x, int y, double amplitude, double period) {
        double phase = y * 2 * Math.PI / period;
        int a = (int) Math.round(7.5 + amplitude * Math.sin(phase));
        int b = (int) Math.round(7.5 - amplitude * Math.sin(phase));
        boolean front = Math.cos(phase) > 0;
        if (x == a && x == b) return front ? 1 : 2;
        if (x == a) return 1;
        if (x == b) return 2;
        return 0;
    }

    /** Gene Assembler: a double helix in a steel frame; working, its rungs light up one by one. */
    static int helixFrame(int x, int y, boolean active, boolean[] glow) {
        if (x < 2 || x > 13 || y < 1 || y > 14) return 0;
        if (x == 2 || x == 13 || y == 1 || y == 14) return (x == 2 || y == 1) ? 0x8A929C : 0x40464F;
        int s = strand(x, y, 3.2, 12);
        if (s == 1) {
            glow[0] = active;
            return active ? 0x7FF0D0 : 0x2F6F62;
        }
        if (s == 2) {
            glow[0] = active;
            return active ? 0xB48CFF : 0x4A3A78;
        }
        double phase = y * 2 * Math.PI / 12;
        int a = (int) Math.round(7.5 + 3.2 * Math.sin(phase)), b = (int) Math.round(7.5 - 3.2 * Math.sin(phase));
        boolean rung = y % 2 == 0 && x > Math.min(a, b) && x < Math.max(a, b);
        if (rung) {
            glow[0] = active && y % 4 == 0;
            return active ? (y % 4 == 0 ? 0xFFF4C0 : 0x8C8470) : 0x3A3A44;
        }
        return 0x16181E;
    }

    /** Gene sample: a stoppered glass vial of honey-coloured fluid with the rungs of a DNA ladder in it. */
    static int geneVial(int x, int y, boolean active, boolean[] glow) {
        if (y >= 1 && y <= 2 && x >= 6 && x <= 9) return y == 1 ? 0xB0824A : 0x8A6234;
        if (y < 3 || y > 14 || x < 5 || x > 10) return 0;
        if (y == 14 && (x == 5 || x == 10)) return 0;
        boolean wall = x == 5 || x == 10 || y == 14;
        if (wall) return x == 5 ? 0xDDEBF2 : 0x8FA6B2;
        if (y == 3) return 0xC9DCE6;
        if (y < 6) return x == 6 ? 0xF4FAFF : 0xBFD6E2;
        if (y >= 8 && y % 2 == 0) {
            if (x == 6) return y % 4 == 0 ? 0x2FB3A0 : 0x7A4FD8;
            if (x == 9) return y % 4 == 0 ? 0x7A4FD8 : 0x2FB3A0;
            return 0xFFF0B8;
        }
        return y == 6 ? 0xF7D57A : x == 6 ? 0xF0BC55 : 0xE6A93A;
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

    /** Frames of a working face's animation, and ticks each one shows. */
    static final int FRAMES = 8, FRAME_TIME = 2;

    /**
     * Idle, working and glow images of a face. The working ones are animated: a ripple runs out from the middle
     * through the glowing parts and a bright band sweeps across them, as the flux layer's echo passes.
     */
    static void face(String name, Face f) throws Exception {
        BufferedImage idle = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        BufferedImage active = new BufferedImage(16, 16 * FRAMES, BufferedImage.TYPE_INT_ARGB);
        BufferedImage glowing = new BufferedImage(16, 16 * FRAMES, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int c = f.at(x, y, false, new boolean[1]);
            if (c != 0) idle.setRGB(x, y, rgb(c));
            boolean[] glow = { false };
            int a = f.at(x, y, true, glow);
            if (a == 0) continue;
            for (int frame = 0; frame < FRAMES; frame++) {
                int shown = glow[0] ? pulse(a, x, y, frame) : a;
                active.setRGB(x, frame * 16 + y, rgb(shown));
                if (glow[0]) glowing.setRGB(x, frame * 16 + y, rgb(shown));
            }
        }
        save(idle, name);
        save(active, name + "_active");
        save(glowing, name + "_active_glow");
        mcmeta(name + "_active");
        mcmeta(name + "_active_glow");
    }

    /** A glowing pixel in a frame: dimmed and brightened by the ripple, lifted towards white by the sweep. */
    static int pulse(int c, int x, int y, int frame) {
        double phase = frame * 2 * Math.PI / FRAMES;
        double ripple = 0.82 + 0.18 * Math.sin(dist(x, y) * 0.9 - phase);
        double band = (x + y) - frame * 32.0 / FRAMES;
        double sweep = Math.max(0, 1 - Math.abs(band - 8) / 2.5) * 0.35;
        int r = c >> 16 & 0xFF, g = c >> 8 & 0xFF, b = c & 0xFF;
        r = (int) Math.min(255, r * ripple + (255 - r * ripple) * sweep);
        g = (int) Math.min(255, g * ripple + (255 - g * ripple) * sweep);
        b = (int) Math.min(255, b * ripple + (255 - b * ripple) * sweep);
        return r << 16 | g << 8 | b;
    }

    static void mcmeta(String name) throws Exception {
        java.nio.file.Files.write(
            new File(ROOT + name + ".png.mcmeta").toPath(),
            ("{\n  \"animation\": { \"frametime\": " + FRAME_TIME + " }\n}\n").getBytes());
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

    /** Prey Echo: a skull in a steel ring; working, its eyes light up. */
    static int skull(int x, int y, boolean active, boolean[] glow) {
        double d = dist(x, y);
        if (d >= 5.6 && d < 6.6) return (x + y < 15) ? 0x8A929C : 0x40464F;
        if (d >= 5.6) return 0;
        boolean cranium = Math.hypot(x - 7.5, (y - 6.5) * 1.15) < 3.9 && y <= 9;
        boolean jaw = y >= 10 && y <= 11 && x >= 5 && x <= 10;
        if ((y == 7 || y == 8) && (x == 5 || x == 6 || x == 9 || x == 10)) {
            glow[0] = active;
            return active ? 0x7CFF5A : 0x1A1612;
        }
        if (y == 9 && (x == 7 || x == 8)) return 0x2A2420;
        if (jaw) return (x % 2 == 0) ? 0xD8D0BC : 0x9E9684;
        if (cranium) return x < 7 ? 0xEDE6D4 : 0xC8C0AC;
        return 0x16181C;
    }

    /** Infusion Echo: a runic matrix over a ring of pedestals; working, the runes and the orb glow. */
    static int runicMatrix(int x, int y, boolean active, boolean[] glow) {
        int[][] pedestals = { { 2, 7 }, { 13, 8 }, { 7, 2 }, { 8, 13 } };
        for (int[] p : pedestals) if (Math.abs(x - p[0]) + Math.abs(y - p[1]) <= 1) {
            glow[0] = active && x == p[0] && y == p[1];
            return glow[0] ? 0xE0B0FF : 0x8C8C94;
        }
        double d = dist(x, y);
        if (d < 1.6) {
            glow[0] = active;
            return active ? 0xFFFFFF : 0x4A3A60;
        }
        if (d < 4.0) {
            boolean rune = (x + 2 * y) % 5 == 0;
            glow[0] = active && rune;
            return rune ? (active ? 0xB07CFF : 0x3E3550) : 0x5C5F66;
        }
        if (d < 4.8) return (x + y < 15) ? 0x9AA0A8 : 0x4A4E55;
        return 0;
    }

    /** Seed Imprinter: a seed bag under a green scanning line. */
    static int seedScanner(int x, int y, boolean active, boolean[] glow) {
        boolean bag = x >= 4 && x <= 11 && y >= 4 && y <= 13 && !(y == 4 && (x == 4 || x == 11));
        boolean tie = y == 3 && x >= 6 && x <= 9;
        if (y == 8 && x >= 2 && x <= 13) {
            glow[0] = active;
            return active ? (x == 7 || x == 8 ? 0xEFFFE0 : 0x7CFF6A) : 0x2B3A2C;
        }
        if (tie) return 0x5A3B1C;
        if (!bag) return 0;
        if (x == 4 || x == 11 || y == 13) return 0x7A5A2E;
        if ((x == 7 && y == 10) || (x == 9 && y == 6) || (x == 6 && y == 11)) return 0xD6C27A;
        return (x + y) % 3 == 0 ? 0xB08A4E : 0xA27C42;
    }

    /** Seed Echo: a window over tilled soil; working, a sprout of light grows. */
    static int sprout(int x, int y, boolean active, boolean[] glow) {
        boolean inBox = x >= 3 && x <= 12 && y >= 3 && y <= 12;
        if (!inBox) return 0;
        if (x == 3 || x == 12 || y == 3 || y == 12) return (x == 3 || y == 3) ? 0x9FB7C4 : 0x55666F;
        if (y >= 10) return (x % 2 == 0) ? 0x4A2E18 : 0x5C3A1E;
        boolean stem = x == 7 && y >= 6;
        boolean leaves = (y == 6 && (x == 5 || x == 6 || x == 8 || x == 9)) || (y == 5 && (x == 6 || x == 8))
            || (y == 7 && (x == 6 || x == 8));
        if (stem || leaves) {
            glow[0] = active;
            return active ? (stem ? 0xB8FF8A : 0x5CE05A) : (stem ? 0x3E5A2E : 0x2E4A24);
        }
        return active ? 0x1E2A2E : 0x14181C;
    }

    /** A paper card with the teal mark of the flux layer at the top; the picture is drawn by {@code mark}. */
    static int card(int x, int y, Face mark) {
        if (x < 3 || x > 12 || y < 2 || y > 13) return 0;
        if (x == 3 || x == 12 || y == 2 || y == 13) return 0x9C8A64;
        if (y == 3) return (x >= 5 && x <= 10) ? 0x2FB3A0 : 0xE8DDC0;
        int m = mark.at(x, y, false, new boolean[1]);
        if (m != 0) return m;
        return (x + y) % 5 == 0 ? 0xDCD0B0 : 0xE8DDC0;
    }

    /** Prey imprint: three claw marks. */
    static int mobImprint(int x, int y, boolean active, boolean[] glow) {
        return card(x, y, (cx, cy, a, g) -> {
            for (int k = 0; k < 3; k++) {
                int ox = 5 + k * 2;
                if (cy >= 5 && cy <= 11 && cx == ox + (cy - 5) / 3) return cy % 2 == 0 ? 0xA81E24 : 0x7A1418;
            }
            return 0;
        });
    }

    /** Crop imprint: a green leaf on a seed. */
    static int cropImprint(int x, int y, boolean active, boolean[] glow) {
        return card(x, y, (cx, cy, a, g) -> {
            if (cy >= 10 && cy <= 11 && cx >= 6 && cx <= 9) return 0x9C7A3A;
            if (cx == 7 && cy >= 7 && cy <= 9) return 0x3E8A2E;
            double lx = cx - 8.5, ly = cy - 6.0;
            if (lx * lx / 4.0 + ly * ly / 1.6 < 1.0) return lx + ly > 0 ? 0x2E7A24 : 0x5CC04A;
            return 0;
        });
    }

    /** Echo Codex: a teal book with the echo ring on its cover. */
    static int codex(int x, int y, boolean active, boolean[] glow) {
        if (x < 3 || x > 13 || y < 1 || y > 14) return 0;
        if (x == 3 || x == 4) return x == 3 ? 0x0E3A36 : 0x1A5A52;
        if (x == 13 || y == 1 || y == 14) return 0xE8DDC0;
        double d = Math.hypot(x - 8.5, y - 7.5);
        if (d < 1.2) return 0xC8FFF0;
        if (d >= 2.2 && d < 3.0) return 0x4FE3C1;
        if (d >= 3.8 && d < 4.4) return 0x5A3CC4;
        return (x + y) % 6 == 0 ? 0x175C55 : 0x1E6B62;
    }

    /** Echo ME Provider: a dark ME frame around the echo ring. */
    static int provider(int x, int y, boolean active, boolean[] glow) {
        if (x == 0 || y == 0) return 0x5E6672;
        if (x == 15 || y == 15) return 0x22262C;
        if (x == 1 || y == 1 || x == 14 || y == 14) return 0x34393F;
        double d = dist(x, y);
        if (d < 1.6) return 0xC8FFF0;
        if (d < 2.6) return 0x2FB3A0;
        if (d >= 3.4 && d < 4.2) return 0x5A3CC4;
        if ((x == 3 || x == 12) && (y == 3 || y == 12)) return 0x7FD8FF;
        return 0x1A1D22;
    }

    static void save(BufferedImage img, String name) throws Exception {
        File f = new File(ROOT + name + ".png");
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
    }
}
