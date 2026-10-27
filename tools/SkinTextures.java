import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the textures of the first two skin families (0.17.0, style FINAL §3.5 M3 to M6, §3.6): the garden skin's moss
 * bronze (苔铜, bronze gone green, rivets in rows) and white marble with gilt bands (汉白玉·鎏金), and the script skin's
 * ink slate (墨, brushed black, violet leaded windows, a lattice) and xuan paper (宣, warm white fibre, the lantern
 * panel that glows from behind). Run from the repository root: {@code java tools/SkinTextures.java} (a test passes
 * another root folder as the first argument and compares).
 * <p>
 * As in {@code tools/HullTextures.java}: 32 pixels a cell; a pattern over 2 × 2 or 4 × 4 cells is cut into one sprite
 * a cell ({@code <name>_<u>_<v>}, u to the right, v downwards) that the renderer picks by world position; binary alpha;
 * every glow in two layers, a white core two pixels wide and a grey edge two pixels wide beside it that the renderer
 * tints with the building's accent. Each material has its body, its seam (panels of 2 × 2 cells), its light seam, its
 * lamp window (the paper's is its lantern panel) and its special where the first batch uses it (the moss bronze's
 * bronze-framed window, the ink's lattice).
 */
public class SkinTextures {

    /** Where the textures go: the mod's assets, or the folder given as the first argument. */
    static String root = "src/main/resources/assets/fluxecho/textures/";

    static final int CELL = 32;

    // moss bronze (style §3.5 M3)
    static final int VERDIGRIS = 0x4E8C7B, VERDIGRIS_LIGHT = 0x6FAE98, BRONZE = 0x8A6A44, BRONZE_LIGHT = 0xB08A58,
        MOSS_GROOVE = 0x2F5148, MOSS_PANE = 0x5F8277, MOSS_GLINT = 0x9CC7B8;
    // white marble and gilt (M4)
    static final int MARBLE = 0xEFEAE1, MARBLE_VEIN = 0xCFC8BC, GOLD = 0xD4AE58, GOLD_LIGHT = 0xF3D88A,
        MARBLE_PANE = 0xD9CDB8;
    // ink (M5)
    static final int INK = 0x16181D, INK_LIGHT = 0x1E2127, SILVER = 0xAEB7C1, VIOLET = 0x6E44C8,
        VIOLET_LIGHT = 0x7D55D6, VIOLET_DARK = 0x5E38B0, LEAD = 0x2A2D33, INK_SEAM = 0x0E0F12;
    // xuan paper (M6)
    static final int PAPER = 0xF1E8D8, PAPER_DARK = 0xE6DBC6, PAPER_INK = 0x6E6556, PAPER_FRAME = 0x3A3530,
        LANTERN = 0xFFD9A0;
    // the light (as com.fluxecho.logic.skin.SkinPalette has it): the white core and the edge's ramp
    static final int GLOW_CORE = 0xFFFFFF, EDGE_INNER = 0xFFFFFF, EDGE_OUTER = 0x999999;
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
        moss();
        marble();
        ink();
        paper();
    }

    // ---- moss bronze

    static void moss() throws Exception {
        String d = "blocks/skin/moss/";
        cut(d + "whole", 2, image(2, (x, y) -> mossBody(x, y, 64, true)));
        cut(d + "seam", 2, image(2, (x, y) -> {
            if (x <= 1 || y <= 1) return x == 1 && y >= 1 || y == 1 && x >= 1 ? BRONZE_LIGHT : BRONZE;
            return mossBody(x, y, 64, false);
        }));
        cut(d + "light_seam", 1, image(1, (x, y) -> {
            if (y == 13 || y == 18) return BRONZE_LIGHT;
            if (y > 13 && y < 18) return MOSS_GROOVE;
            return mossBody(x, y, CELL, false);
        }));
        cut(d + "lamp", 1, image(1, (x, y) -> frame(x, y, BRONZE, BRONZE_LIGHT, hash(x, y, 31) < 0.1 ? MOSS_GLINT
            : MOSS_PANE)));
        // the bronze-framed window: bronze frame and mullions, clear glass with two glints
        cut(d + "window", 1, image(1, (x, y) -> frame(x, y, BRONZE, BRONZE_LIGHT, glass(x, y, MOSS_GLINT))));
        glow(d + "light_seam", SkinTextures::lineCore, (x, y) -> true);
        glow(d + "lamp", SkinTextures::lampCore, SkinTextures::pane);
    }

    /**
     * Moss bronze over a pattern {@code size} pixels across: two greens mottled, the bronze showing where it is worn,
     * and ({@code rivets}) a row of rivets every half cell.
     */
    static int mossBody(int x, int y, int size, boolean rivets) {
        double n = 0.6 * noise(x, y, 16, size / 16, 1) + 0.4 * noise(x, y, 8, size / 8, 2);
        int c = mix(VERDIGRIS, VERDIGRIS_LIGHT, (n - 0.35) * 2.5);
        if (noise(x, y, 8, size / 8, 3) > 0.8) c = mix(c, BRONZE, 0.6);
        if (rivets && y % 16 >= 4 && y % 16 <= 5 && x % 16 >= 7 && x % 16 <= 8)
            return x % 16 == 7 && y % 16 == 4 ? BRONZE_LIGHT : BRONZE;
        return c;
    }

    // ---- white marble and gilt

    static void marble() throws Exception {
        String d = "blocks/skin/marble/";
        cut(d + "whole", 4, image(4, (x, y) -> marbleBody(x, y, 128)));
        cut(d + "seam", 2, image(2, (x, y) -> x == 0 || y == 0 ? MARBLE_VEIN : marbleBody(x, y, 64)));
        cut(d + "light_seam", 1, image(1, (x, y) -> {
            if (y == 12 || y == 19) return GOLD_LIGHT;
            if (y > 12 && y < 19) return GOLD;
            return marbleBody(x, y, CELL);
        }));
        cut(d + "lamp", 1, image(1, (x, y) -> frame(x, y, GOLD, GOLD_LIGHT, MARBLE_PANE)));
        glow(d + "light_seam", SkinTextures::lineCore, (x, y) -> true);
        glow(d + "lamp", SkinTextures::lampCore, SkinTextures::pane);
    }

    /** White marble over a pattern {@code size} pixels across: long veins that wrap at its edges. */
    static int marbleBody(int x, int y, int size) {
        double t = 2 * Math.PI * (2.0 * x / size + 1.0 * y / size) + 2.2 * noise(x, y, size / 4, 4, 4);
        double v = Math.abs(Math.sin(t));
        if (v < 0.05) return MARBLE_VEIN;
        if (v < 0.11) return mix(MARBLE, MARBLE_VEIN, 0.45);
        return hash(x, y, 5) < 0.06 ? mix(MARBLE, MARBLE_VEIN, 0.2) : MARBLE;
    }

    // ---- ink

    static void ink() throws Exception {
        String d = "blocks/skin/ink/";
        cut(d + "whole", 2, image(2, (x, y) -> inkBody(x, y, 64)));
        cut(d + "seam", 2, image(2, (x, y) -> x == 0 || y == 0 ? INK_SEAM : inkBody(x, y, 64)));
        cut(d + "light_seam", 1, image(1, (x, y) -> {
            if (y == 13 || y == 18) return SILVER;
            if (y > 13 && y < 18) return INK_SEAM;
            return inkBody(x, y, CELL);
        }));
        // the leaded violet window: lead frame and mullions, panes cut again by thin cames, each pane its own violet
        cut(d + "lamp", 1, image(1, (x, y) -> {
            int c = frame(x, y, LEAD, SILVER, 0);
            if (c != 0) return c;
            if (x == 9 || x == 22 || y == 9 || y == 22) return LEAD;
            int pane = (x < 9 ? 0 : x < 16 ? 1 : x < 22 ? 2 : 3) + 4 * (y < 9 ? 0 : y < 16 ? 1 : y < 22 ? 2 : 3);
            double h = hash(pane, 0, 6);
            return h < 0.33 ? VIOLET_DARK : h < 0.66 ? VIOLET : VIOLET_LIGHT;
        }));
        // the lattice (格栅): ink bars round open squares, each square with a smaller square inside it
        cut(d + "grate", 1, image(1, (x, y) -> {
            int lx = x % 16, ly = y % 16;
            boolean bar = lx <= 2 || ly <= 2;
            double ring = Math.max(Math.abs(lx - 9), Math.abs(ly - 9));
            if (!bar && (ring < 3 || ring >= 4.5)) return 0;
            return lx == 0 || ly == 0 ? INK_LIGHT : INK;
        }));
        glow(d + "light_seam", SkinTextures::lineCore, (x, y) -> true);
        glow(d + "lamp", SkinTextures::lampCore, SkinTextures::pane);
    }

    /** Ink slate over a pattern {@code size} pixels across: fine upright brush strokes in two blacks. */
    static int inkBody(int x, int y, int size) {
        double col = hash(x, 0, 7);
        double along = noise(x, y, 16, size / 16, 8);
        return mix(INK, INK_LIGHT, col * 0.7 + along * 0.3);
    }

    // ---- xuan paper

    static void paper() throws Exception {
        String d = "blocks/skin/paper/";
        cut(d + "whole", 2, image(2, (x, y) -> paperBody(x, y, 64)));
        cut(d + "seam", 2, image(2, (x, y) -> x == 0 || y == 0 ? PAPER_INK : paperBody(x, y, 64)));
        cut(d + "light_seam", 1, image(1, (x, y) -> {
            if (y == 13 || y == 18) return PAPER_INK;
            if (y > 13 && y < 18) return PAPER_DARK;
            return paperBody(x, y, CELL);
        }));
        // the lantern panel: an ink frame round paper with a faint warmth at its middle; its light is the overlay
        cut(d + "lantern", 1, image(1, (x, y) -> {
            if (x <= 1 || y <= 1 || x >= 30 || y >= 30) return PAPER_FRAME;
            double r = Math.hypot(x - 15.5, y - 15.5);
            return mix(paperBody(x, y, CELL), LANTERN, 0.35 * Math.max(0, 1 - r / 16));
        }));
        glow(d + "light_seam", SkinTextures::lineCore, (x, y) -> true);
        // the lantern's light covers some 70% of the panel (style §3.5 M6), brightest in the middle
        glow(
            d + "lantern",
            (x, y) -> Math.hypot(x - 15.5, y - 15.5) <= 12.5,
            (x, y) -> x >= 2 && y >= 2 && x <= 29 && y <= 29);
    }

    /** Xuan paper over a pattern {@code size} pixels across: warm white with a cloud of fibre, short strands. */
    static int paperBody(int x, int y, int size) {
        int c = mix(PAPER, PAPER_DARK, 0.5 * noise(x, y, 8, size / 8, 9));
        if (hash(x / 4, y, 10) < 0.05) c = mix(c, PAPER_DARK, 0.7);
        return c;
    }

    // ---- windows and glows

    /**
     * A window's pixel: a frame four pixels wide ({@code frame}, its inner line {@code inner}) round four panes
     * ({@code pane}, 0 for clear glass) parted by mullions two pixels wide.
     */
    static int frame(int x, int y, int frame, int inner, int pane) {
        if (x < 4 || x > 27 || y < 4 || y > 27) {
            boolean in = x >= 3 && x <= 28 && y >= 3 && y <= 28;
            return in && (x == 3 || x == 28 || y == 3 || y == 28) ? inner : frame;
        }
        if (x == 15 || x == 16 || y == 15 || y == 16) return frame;
        return pane;
    }

    /** Clear glass: transparent but for two glints in each pane. */
    static int glass(int x, int y, int glint) {
        int px = x < 16 ? x - 4 : x - 17, py = y < 16 ? y - 4 : y - 17;
        if (px + py == 6 && px >= 2 && px <= 4 || px + py == 9 && px >= 4 && px <= 5) return glint;
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

    // ---- helpers (as in HullTextures)

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
     * Writes a glow's two layers: the white core where {@code core} holds; the edge on the pixels {@code allowed} keeps
     * within {@link #EDGE_PX} of the core (the nearest by the larger of the two offsets), white next to the core and
     * {@link #EDGE_OUTER} outside.
     */
    static void glow(String name, Mask core, Mask allowed) throws Exception {
        BufferedImage c = new BufferedImage(CELL, CELL, BufferedImage.TYPE_INT_ARGB),
            e = new BufferedImage(CELL, CELL, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < CELL; y++) for (int x = 0; x < CELL; x++) {
            if (core.at(x, y)) {
                c.setRGB(x, y, 0xFF000000 | GLOW_CORE);
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

    /**
     * Smooth value noise, 0 to 1, on a lattice {@code cell} pixels apart that repeats every {@code period} lattice
     * steps, so a pattern of {@code cell × period} pixels wraps without a seam.
     */
    static double noise(int x, int y, int cell, int period, int seed) {
        double fx = x / (double) cell, fy = y / (double) cell;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        double tx = smooth(fx - x0), ty = smooth(fy - y0);
        double a = lattice(x0, y0, period, seed), b = lattice(x0 + 1, y0, period, seed),
            c = lattice(x0, y0 + 1, period, seed), d = lattice(x0 + 1, y0 + 1, period, seed);
        return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty;
    }

    private static double lattice(int x, int y, int period, int seed) {
        return hash(Math.floorMod(x, period), Math.floorMod(y, period), seed);
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) ((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t);
        int g = (int) ((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
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
