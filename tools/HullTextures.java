import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the textures of the city's two structural materials (0.17.0, style FINAL §3.5 M1 and M2, §3.6, §3.7): the
 * hull (舟壳), pearl-white composite panels that every building's bones and crown are made of, and the footing (玄基),
 * the warm black coursed basalt every building stands on; and the two parts of the old families made of them, the
 * deck's hull-number tile (deck 13) and the fittings' planting bed (fitting 14). Run from the repository root:
 * {@code java tools/HullTextures.java} (a test passes another root folder as the first argument and compares).
 * <p>
 * Every texture is 32 pixels a cell, so a two-pixel line still reads sixteen blocks away under four mipmap levels. A
 * pattern that spans 2 × 2 or 4 × 4 cells (the hull's panel seams, the footing's coursed masonry, the reliefs) is
 * drawn whole and cut into one sprite a cell, {@code <name>_<u>_<v>} with u to the right and v downwards, which the
 * renderer picks by world position ({@code client.shape.SkinIcons}), so the pattern runs on from block to block. All
 * alpha is binary. The hull is never brighter than its base colour {@code #EDF1F3}: its panels' upper part is the base
 * and everything else is darker, so {@code style.hullBrightness} (0.85 to 1) is the only thing that can lift it.
 * <p>
 * Every glow comes as two overlays drawn over the same face at full brightness (style §3.6): {@code *_glow_core}, a
 * white core two pixels wide drawn untinted, so it reads as light whatever a shader pack does to the hue, and
 * {@code *_glow_edge}, two pixels of grey beside it (white next to the core, {@code #999999} outside) that the
 * renderer tints with the building's accent; one light-seam shape thus lights in any building's colour.
 */
public class HullTextures {

    /** Where the textures go: the mod's assets, or the folder given as the first argument. */
    static String root = "src/main/resources/assets/fluxecho/textures/";

    static final int CELL = 32;

    // the hull (style §3.5 M1): the base, the panel seam, the rivets; darker shades of the base for grooves
    static final int HULL = 0xEDF1F3, HULL_SEAM = 0xC3CCD3, HULL_RIVET = 0xD5DCE1, HULL_SHADE = 0xDCE2E6,
        HULL_DEEP = 0xB2BCC4, HULL_GLINT = 0xDCE8EE, HULL_PANE = 0xC9CED2;
    // the footing (style §3.5 M2): the dressed face, the joints, the polished top, the chamfer
    static final int FOOTING = 0x36312D, FOOTING_SEAM = 0x221E1B, FOOTING_TOP = 0x433C37, FOOTING_CHAMFER = 0x3D3733,
        FOOTING_GROOVE = 0x2B2622, FOOTING_MOUTH = 0x1A1715;
    // the soil of the planting bed and the footing's planter lip
    static final int SOIL = 0x5B4231, SOIL_DARK = 0x45311F, SOIL_LIGHT = 0x6E523C;
    // the light (as com.fluxecho.logic.skin.SkinPalette has it): the white core, the keel's core, the edge's ramp
    static final int GLOW_CORE = 0xFFFFFF, KEEL_CORE = 0xE8FCFF, EDGE_INNER = 0xFFFFFF, EDGE_OUTER = 0x999999;
    static final int CORE_PX = 2, EDGE_PX = 2;

    /** A test on a pixel of a cell. */
    interface Mask {

        boolean at(int x, int y);
    }

    /** The colour of a pixel of a pattern, 0 for transparent. */
    interface Px {

        int at(int x, int y);
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) root = args[0];
        hull();
        footing();
        hullNumber();
        bed();
    }

    // ---- the hull

    static void hull() throws Exception {
        String d = "blocks/skin/hull/";
        cut(d + "whole", 1, image(1, (x, y) -> hullPanel(x, y, CELL, 1)));
        cut(d + "seam", 2, image(2, HullTextures::hullSeam));
        cut(d + "floor", 2, image(2, HullTextures::hullFloor));
        cut(d + "relief", 4, hullRelief());
        cut(d + "window", 1, image(1, (x, y) -> window(x, y, false)));
        cut(d + "lamp", 1, image(1, (x, y) -> window(x, y, true)));
        cut(d + "light_seam", 1, image(1, HullTextures::hullLightSeam));
        cut(d + "keel", 1, image(1, HullTextures::hullKeel));
        glow(d + "light_seam", HullTextures::lineCore, (x, y) -> true, GLOW_CORE);
        glow(d + "keel", (x, y) -> x == 15 || x == 16, (x, y) -> true, KEEL_CORE);
        // the keel's top cell: the strip ends in a point
        glow(
            d + "keel_tip",
            (x, y) -> (x == 15 || x == 16) && y >= 10,
            (x, y) -> y >= 10 || y == 9 && x >= 14 && x <= 17 || y == 8 && x >= 15 && x <= 16,
            KEEL_CORE);
        glow(d + "lamp", HullTextures::lampCore, HullTextures::pane, GLOW_CORE);
        glow(d + "nose", HullTextures::noseCore, (x, y) -> y >= 16, GLOW_CORE);
    }

    /**
     * A pixel of a hull panel {@code h} pixels high at {@code y} from its top: the base on the upper third, darkening
     * by up to 3% to the bottom (style §3.5: the panel's upper part is 3% brighter), with a fine grain that only
     * darkens.
     */
    static int hullPanel(int x, int y, int h, int seed) {
        double t = 0.03 * Math.max(0, (y - h / 3.0) / (h * 2 / 3.0));
        int c = darken(HULL, t);
        return hash(x, y, seed) < 0.12 ? darken(c, 0.012) : c;
    }

    /** The seam pattern: one panel each 2 × 2 cells, a one-pixel seam, a shallow rivet near each corner. */
    static int hullSeam(int x, int y) {
        if (x == 0 || y == 0) return HULL_SEAM;
        if (rivet(x, y, 64)) return HULL_RIVET;
        return hullPanel(x, y, 64, 2);
    }

    static boolean rivet(int x, int y, int size) {
        int a = Math.min(x, size - 1 - x), b = Math.min(y, size - 1 - y);
        return a >= 3 && a <= 4 && b >= 3 && b <= 4;
    }

    /** The hull's floor: tiles of half a cell over 2 × 2 cells and an inlaid square ring. */
    static int hullFloor(int x, int y) {
        if (x == 0 || y == 0) return HULL_SEAM;
        double ring = Math.max(Math.abs(x - 31.5), Math.abs(y - 31.5));
        if (Math.abs(ring - 24) < 0.6) return HULL_SEAM;
        if (x % 16 == 0 || y % 16 == 0) return HULL_SHADE;
        int c = darken(HULL, 0.02);
        return hash(x, y, 3) < 0.1 ? darken(c, 0.012) : c;
    }

    /** The light seam's cell: a panel with a groove through its middle that the glow line lies in. */
    static int hullLightSeam(int x, int y) {
        if (y == 13 || y == 18) return HULL_SEAM;
        if (y > 13 && y < 18) return HULL_SHADE;
        return hullPanel(x, y, CELL, 4);
    }

    /** The keel's cell: a panel with a channel four sixteenths wide up its middle that the cyan strip lies in. */
    static int hullKeel(int x, int y) {
        if (x == 12 || x == 19) return HULL_SEAM;
        if (x > 12 && x < 19) return HULL_DEEP;
        return hullPanel(x, y, CELL, 5);
    }

    /**
     * A window ({@code lamp} false: frame and clear glass, the glass transparent but for two glints) or a lamp window
     * ({@code lamp}: the panel behind the glass, unlit; its glow is an overlay): a frame four pixels wide and a cross
     * of mullions two pixels wide, four panes.
     */
    static int window(int x, int y, boolean lamp) {
        if (x < 4 || x > 27 || y < 4 || y > 27) {
            boolean inner = x == 3 || x == 28 || y == 3 || y == 28;
            return inner && x >= 3 && x <= 28 && y >= 3 && y <= 28 ? HULL_SHADE : hullPanel(x, y, CELL, 6);
        }
        if (x == 15 || x == 16 || y == 15 || y == 16) return HULL_RIVET;
        if (lamp) return hash(x, y, 7) < 0.08 ? darken(HULL_PANE, 0.02) : HULL_PANE;
        int px = x < 16 ? x - 4 : x - 17, py = y < 16 ? y - 4 : y - 17;
        if (px + py == 6 && px >= 2 && px <= 4 || px + py == 9 && px >= 4 && px <= 5) return HULL_GLINT;
        return 0;
    }

    /** Whether a pixel is in one of the four panes of a window. */
    static boolean pane(int x, int y) {
        return x >= 4 && x <= 27 && y >= 4 && y <= 27 && x != 15 && x != 16 && y != 15 && y != 16;
    }

    /** A lamp window's glowing core: each pane but its outer two pixels, which carry the edge. */
    static boolean lampCore(int x, int y) {
        if (!pane(x, y)) return false;
        int px = x < 16 ? x - 4 : x - 17, py = y < 16 ? y - 4 : y - 17;
        return px >= 2 && px <= 8 && py >= 2 && py <= 8;
    }

    /** A light seam's line: two rows through the middle of a side face, from edge to edge so it runs on. */
    static boolean lineCore(int x, int y) {
        return y == 15 || y == 16;
    }

    /** A step's nose: the top two rows of its front face (a step is a lower half, its face rows 16 to 31). */
    static boolean noseCore(int x, int y) {
        return y == 16 || y == 17;
    }

    /** The hull's relief (饰): a star chart engraved over 4 × 4 cells, a ring round it, its stars joined in lines. */
    static int[][] hullRelief() {
        int n = 4 * CELL;
        int[][] img = image(4, (x, y) -> x == 0 || y == 0 ? HULL_SEAM : hullPanel(x, y, n, 8));
        double c = (n - 1) / 2.0;
        for (int y = 1; y < n; y++) for (int x = 1; x < n; x++) {
            double r = Math.hypot(x - c, y - c);
            if (Math.abs(r - 54) < 0.6) img[y][x] = HULL_RIVET;
            else if (Math.abs(r - 30) < 0.6 && (int) (Math.atan2(y - c, x - c) * 30) % 2 == 0) img[y][x] = HULL_RIVET;
        }
        int[][][] figures = { { { 18, 22 }, { 34, 30 }, { 46, 18 }, { 60, 34 } },
            { { 80, 20 }, { 96, 28 }, { 110, 16 } }, { { 96, 28 }, { 104, 46 } },
            { { 20, 90 }, { 30, 104 }, { 48, 110 }, { 58, 96 } },
            { { 78, 84 }, { 92, 100 }, { 112, 96 }, { 106, 78 }, { 78, 84 } }, { { 56, 60 }, { 70, 66 } } };
        for (int[][] f : figures) for (int i = 0; i + 1 < f.length; i++) line(img, f[i], f[i + 1], HULL_SEAM);
        for (int[][] f : figures) for (int[] s : f) star(img, s[0], s[1]);
        return img;
    }

    static void line(int[][] img, int[] a, int[] b, int colour) {
        int steps = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
        for (int i = 0; i <= steps; i++) {
            int x = (int) Math.round(a[0] + (b[0] - a[0]) * i / (double) steps),
                y = (int) Math.round(a[1] + (b[1] - a[1]) * i / (double) steps);
            img[y][x] = colour;
        }
    }

    static void star(int[][] img, int x, int y) {
        for (int d = -2; d <= 2; d++) {
            img[y][x + d] = HULL_SEAM;
            img[y + d][x] = HULL_SEAM;
        }
        img[y][x] = HULL_DEEP;
    }

    // ---- the footing

    static void footing() throws Exception {
        String d = "blocks/skin/footing/";
        cut(d + "side", 2, image(2, HullTextures::footingSide));
        cut(d + "top", 2, image(2, HullTextures::footingTop));
        cut(d + "seam", 2, image(2, HullTextures::footingSeam));
        cut(d + "floor", 2, image(2, HullTextures::footingFloor));
        cut(d + "relief", 4, image(4, HullTextures::footingRelief));
        cut(d + "light_seam", 1, image(1, HullTextures::footingLightSeam));
        cut(d + "spout", 1, image(1, HullTextures::footingSpout));
        cut(d + "planter", 1, image(1, HullTextures::footingPlanter));
        cut(d + "grate", 1, image(1, HullTextures::footingGrate));
        glow(d + "light_seam", HullTextures::lineCore, (x, y) -> true, GLOW_CORE);
        glow(d + "nose", HullTextures::noseCore, (x, y) -> y >= 16, GLOW_CORE);
        // the pool lip's warm lip along the top edge (the renderer turns it to each water side)
        glow(d + "lip", (x, y) -> y <= 1, (x, y) -> true, GLOW_CORE);
    }

    /** A pixel of dressed basalt: chisel marks a little darker and a little lighter than the face. */
    static int dressed(int x, int y, int seed) {
        double h = hash(x, y, seed);
        if (h < 0.1) return darken(FOOTING, 0.08);
        if (h > 0.93) return FOOTING_CHAMFER;
        return FOOTING;
    }

    /** The footing's side: courses one cell high, stones two cells long, the joints of one course over the next's middle. */
    static int footingSide(int x, int y) {
        int course = y / CELL, sx = (x + course * CELL) % (2 * CELL), sy = y % CELL;
        if (sy >= 30 || sx <= 1) return FOOTING_SEAM;
        if (sy == 0 || sx == 2) return FOOTING_CHAMFER;
        return dressed(x, y, 11);
    }

    /** The footing's top: polished slabs of 2 × 2 cells with a faint sheen across them. */
    static int footingTop(int x, int y) {
        if (x == 0 || y == 0) return FOOTING_SEAM;
        int c = FOOTING_TOP;
        int s = (x + y) % 64;
        if (s >= 20 && s <= 26) c = mix(c, 0x4A433D, 0.5);
        return hash(x, y, 12) < 0.08 ? darken(c, 0.05) : c;
    }

    /** The footing's seam (缝): one great stone each 2 × 2 cells. */
    static int footingSeam(int x, int y) {
        if (x <= 1 || y >= 62) return FOOTING_SEAM;
        if (y == 0 || x == 2) return FOOTING_CHAMFER;
        return dressed(x, y, 13);
    }

    /** The footing's floor: pavers a cell by half a cell in running bond, each a shade apart. */
    static int footingFloor(int x, int y) {
        int row = y / 16, px = (x + row % 2 * 16) % CELL;
        if (y % 16 == 15 || px == 0) return FOOTING_SEAM;
        int id = row * 8 + (x + row % 2 * 16) / CELL;
        int c = mix(FOOTING_TOP, FOOTING, hash(id, row, 14) * 0.5);
        return hash(x, y, 15) < 0.08 ? darken(c, 0.05) : c;
    }

    /** The footing's relief (饰): a carved compass of octagons and eight spokes over 4 × 4 cells. */
    static int footingRelief(int x, int y) {
        if (x <= 1 || y >= 126) return FOOTING_SEAM;
        double dx = Math.abs(x - 63.5), dy = Math.abs(y - 63.5);
        double od = Math.max(Math.max(dx, dy), (dx + dy) * 0.7071);
        if (od < 56) {
            double m = od % 12;
            if (od > 8 && m < 2) return FOOTING_SEAM;
            if (od > 8 && m < 3) return FOOTING_CHAMFER;
            boolean spoke = dx < 1 || dy < 1 || Math.abs(dx - dy) < 1.2;
            if (spoke && od > 14) return FOOTING_SEAM;
        }
        return dressed(x, y, 16);
    }

    /** The footing's light seam: a course with a groove through its middle. */
    static int footingLightSeam(int x, int y) {
        if (y >= 30) return FOOTING_SEAM;
        if (y == 13 || y == 18) return FOOTING_SEAM;
        if (y > 13 && y < 18) return FOOTING_GROOVE;
        if (y == 0) return FOOTING_CHAMFER;
        return dressed(x, y, 17);
    }

    /** The spout's outlet face: a dark mouth with a lip under it, set in a course. */
    static int footingSpout(int x, int y) {
        if (y >= 30) return FOOTING_SEAM;
        if (x >= 9 && x <= 22 && y >= 11 && y <= 20) {
            boolean corner = (x == 9 || x == 22) && (y == 11 || y == 20);
            return corner ? FOOTING_SEAM : FOOTING_MOUTH;
        }
        if (x >= 8 && x <= 23 && y >= 21 && y <= 22) return FOOTING_CHAMFER;
        if (x >= 8 && x <= 23 && y == 10) return FOOTING_SEAM;
        return dressed(x, y, 18);
    }

    /** The planter lip's top: a stone rim round a bed of soil. */
    static int footingPlanter(int x, int y) {
        int rim = Math.min(Math.min(x, 31 - x), Math.min(y, 31 - y));
        if (rim < 3) return rim == 2 ? FOOTING_CHAMFER : FOOTING_TOP;
        return soil(x, y, 19);
    }

    /** The grate: stone bars three pixels wide with open squares between them. */
    static int footingGrate(int x, int y) {
        int bx = x % 8, by = y % 8;
        if (bx >= 3 && by >= 3) return 0;
        if (by == 0 || bx == 0) return FOOTING_CHAMFER;
        return by == 2 || bx == 2 ? FOOTING_SEAM : FOOTING;
    }

    static int soil(int x, int y, int seed) {
        double h = hash(x, y, seed);
        if (h < 0.14) return SOIL_DARK;
        if (h > 0.9) return SOIL_LIGHT;
        return SOIL;
    }

    // ---- the hull-number tile (deck 13) and the planting bed (fitting 14)

    /**
     * The hull-number tile (style §3.7): a polished footing tile with an octagonal badge in the hull's grey, unlit;
     * its number and whether it is lit are the box table's to draw.
     */
    static void hullNumber() throws Exception {
        cut("blocks/skin/deck/hull_number_top", 1, image(1, (x, y) -> {
            if (x == 0 || y == 0) return FOOTING_SEAM;
            double dx = Math.abs(x - 15.5), dy = Math.abs(y - 15.5);
            double od = Math.max(Math.max(dx, dy), (dx + dy) * 0.7071);
            if (od >= 9 && od < 11) return HULL_SEAM;
            if (od < 9) return FOOTING;
            return hash(x, y, 20) < 0.08 ? darken(FOOTING_TOP, 0.05) : FOOTING_TOP;
        }));
        cut("blocks/skin/deck/hull_number_side", 1, image(1, (x, y) -> {
            if (y == 31) return FOOTING_SEAM;
            if (y == 0) return FOOTING_CHAMFER;
            return dressed(x, y, 21);
        }));
    }

    /**
     * The planting bed (style §3.7): a hull trough twelve sixteenths high (its side faces show rows 8 to 31) with soil
     * on top; its warm lip glows on the rim of the side and in a ring round the soil.
     */
    static void bed() throws Exception {
        String d = "blocks/skin/fitting/";
        cut(d + "bed_side", 1, image(1, (x, y) -> {
            if (y == 11 || y == 31) return HULL_SEAM;
            if (y >= 8 && y <= 10) return HULL;
            return hullPanel(x, y, CELL, 22);
        }));
        cut(d + "bed_top", 1, image(1, (x, y) -> {
            int rim = Math.min(Math.min(x, 31 - x), Math.min(y, 31 - y));
            if (rim < 2) return rim == 1 ? HULL_SHADE : HULL;
            return soil(x, y, 23);
        }));
        glow(d + "bed_side", (x, y) -> y == 8 || y == 9, (x, y) -> y >= 8, GLOW_CORE);
        glow(d + "bed_top", (x, y) -> {
            int rim = Math.min(Math.min(x, 31 - x), Math.min(y, 31 - y));
            return rim == 3 || rim == 4;
        }, (x, y) -> Math.min(Math.min(x, 31 - x), Math.min(y, 31 - y)) >= 2, GLOW_CORE);
    }

    // ---- helpers

    /** A pattern's pixels over {@code span × span} cells, row by row; 0 is transparent. */
    static int[][] image(int span, Px p) {
        int n = span * CELL;
        int[][] img = new int[n][n];
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) img[y][x] = p.at(x, y);
        return img;
    }

    /** Writes a pattern of {@code span × span} cells as one sprite a cell ({@code name} itself for a single cell). */
    static void cut(String name, int span, int[][] img) throws Exception {
        for (int v = 0; v < span; v++) for (int u = 0; u < span; u++) {
            BufferedImage out = new BufferedImage(CELL, CELL, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < CELL; y++) for (int x = 0; x < CELL; x++) {
                int c = img[v * CELL + y][u * CELL + x];
                if (c != 0) out.setRGB(x, y, 0xFF000000 | c);
            }
            save(out, span == 1 ? name : name + "_" + u + "_" + v);
        }
    }

    /**
     * Writes a glow's two layers: the core where {@code core} holds, in {@code coreColour}; the edge on the pixels
     * {@code allowed} keeps within {@link #EDGE_PX} of the core (the nearest by the larger of the two offsets), white
     * next to the core and {@link #EDGE_OUTER} outside.
     */
    static void glow(String name, Mask core, Mask allowed, int coreColour) throws Exception {
        BufferedImage c = new BufferedImage(CELL, CELL, BufferedImage.TYPE_INT_ARGB),
            e = new BufferedImage(CELL, CELL, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < CELL; y++) for (int x = 0; x < CELL; x++) {
            if (core.at(x, y)) {
                c.setRGB(x, y, 0xFF000000 | coreColour);
                continue;
            }
            if (!allowed.at(x, y)) continue;
            int d = Integer.MAX_VALUE;
            for (int dy = -EDGE_PX; dy <= EDGE_PX; dy++) for (int dx = -EDGE_PX; dx <= EDGE_PX; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx < 0 || ny < 0 || nx >= CELL || ny >= CELL || !core.at(nx, ny)) continue;
                d = Math.min(d, Math.max(Math.abs(dx), Math.abs(dy)));
            }
            if (d <= EDGE_PX) e.setRGB(x, y, 0xFF000000 | edgeGrey(d - 1));
        }
        save(c, name + "_glow_core");
        save(e, name + "_glow_edge");
    }

    /** The edge's grey {@code step} pixels out from the core, on the ramp from the inner to the outer grey. */
    static int edgeGrey(int step) {
        int n = Math.max(1, EDGE_PX - 1), s = Math.max(0, Math.min(n, step));
        int in = EDGE_INNER & 255, out = EDGE_OUTER & 255, v = in + (out - in) * s / n;
        return v << 16 | v << 8 | v;
    }

    static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) ((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t);
        int g = (int) ((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    /** A colour darkened by {@code t} (0 to 1) towards black, never to pure black (0 is transparent). */
    static int darken(int c, double t) {
        int d = mix(c, 0x000000, t);
        return d == 0 ? 0x010101 : d;
    }

    static double hash(int x, int y, int s) {
        double v = Math.sin(x * 12.9898 + y * 78.233 + s * 37.719) * 43758.5453;
        return v - Math.floor(v);
    }

    static void save(BufferedImage img, String name) throws Exception {
        File f = new File(root + name + ".png");
        f.getParentFile()
            .mkdirs();
        ImageIO.write(img, "png", f);
    }
}
