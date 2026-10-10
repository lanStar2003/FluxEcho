import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the textures of the campus builder (0.10.0): the deck tiles the nexus paves its campus and builds its facades
 * with, the fittings a shelf unit and a library interior are assembled from, three extras for the shelf-unit look of
 * the frame's echo shelf (the recessed compartment's back panel, its boards and a book spine to tint), and the supply
 * port's faces. Run from the repository root:
 * {@code java tools/CampusTextures.java}.
 * <p>
 * The palette is the dark tech of the nexus: navy steel, blue-grey tiles, and light only in thin cyan lines. Every tile
 * that is laid side by side (floor, wall panels, bands, unit plinths and crowns) tiles seamlessly, and its light lines
 * run on unbroken from block to block. The parts that glow come as separate overlays ({@code *_glow}) with a
 * transparent background, drawn at full brightness by the block renderers; their animation is a slow, low brightness
 * wave that also wraps at the block edge.
 */
public class CampusTextures {

    static final String ROOT = "src/main/resources/assets/fluxecho/textures/";

    // the locked campus palette
    static final int DECK = 0x3E4C58, DECK_LINE = 0x4A5966, TRIM = 0x101C28, LIT = 0xC8F8FF, LIT_BED = 0x1C3446,
        DARK = 0x2C3842, GRATE = 0x24303A, PANEL = 0x45525E, PANEL_DARK = 0x1A2630, NAVY = 0x1C3446, STEEL = 0x2C5470,
        CYAN = 0x4FE3FF, VIOLET = 0x8A5CFF;
    // shades derived from it
    static final int CHANNEL = 0x0A2632, CYAN_DARK = 0x1C6F84, WHITE = 0xF2FEFF, STEEL_HI = 0x3E6E8E,
        STEEL_LO = 0x1E3E54, SHADOW = 0x070D13, VIOLET_HI = 0xC9B4FF;
    static final int FRAMES = 8;

    static final String[] DECK_NAMES = { "deck", "trim", "lit", "grate", "dark", "skirt", "chevron", "well", "panel",
        "panel_lit", "panel_dark", "cornice" };
    static final String[] FITTING_NAMES = { "post", "plinth", "crown", "rail", "tread", "glaze", "pedestal" };

    interface Px {

        /** The colour at a pixel, 0 for transparent; {@code f} is the animation frame. */
        int at(int x, int y, int f);
    }

    public static void main(String[] args) throws Exception {
        Px[] deck = { CampusTextures::deck, CampusTextures::trim, CampusTextures::lit, CampusTextures::grate,
            CampusTextures::dark, CampusTextures::skirt, CampusTextures::chevron, CampusTextures::well,
            CampusTextures::panel, CampusTextures::panelLit, CampusTextures::panelDark, CampusTextures::cornice };
        Px[] deckGlow = { null, null, CampusTextures::litGlow, null, null, CampusTextures::skirtGlow,
            CampusTextures::chevronGlow, CampusTextures::wellGlow, null, CampusTextures::panelLitGlow,
            CampusTextures::panelDarkGlow, CampusTextures::corniceGlow };
        for (int i = 0; i < DECK_NAMES.length; i++) {
            still("blocks/deck/" + DECK_NAMES[i], deck[i]);
            if (deckGlow[i] != null) anim("blocks/deck/" + DECK_NAMES[i] + "_glow", deckGlow[i]);
        }
        // the lit strip's upright faces (a gallery edge seen from the void): one level line, not the floor's cross
        still("blocks/deck/lit_side", CampusTextures::litSide);
        anim("blocks/deck/lit_side_glow", CampusTextures::litSideGlow);

        Px[] side = { CampusTextures::postSide, CampusTextures::plinthSide, CampusTextures::crownSide,
            CampusTextures::railSide, CampusTextures::treadSide, CampusTextures::glaze, CampusTextures::pedestalSide };
        Px[] top = { CampusTextures::postTop, CampusTextures::plinthTop, CampusTextures::crownTop,
            CampusTextures::railTop, CampusTextures::treadTop, CampusTextures::glaze, CampusTextures::pedestalTop };
        Px[] glow = { CampusTextures::postGlow, CampusTextures::plinthGlow, CampusTextures::crownGlow,
            CampusTextures::railGlow, CampusTextures::treadGlow, null, CampusTextures::pedestalGlow };
        for (int i = 0; i < FITTING_NAMES.length; i++) {
            still("blocks/fitting/" + FITTING_NAMES[i], side[i]);
            still("blocks/fitting/" + FITTING_NAMES[i] + "_top", top[i]);
            if (glow[i] != null) anim("blocks/fitting/" + FITTING_NAMES[i] + "_glow", glow[i]);
        }

        still("blocks/frame/shelf_niche", CampusTextures::shelfNiche);
        still("blocks/frame/shelf_board", CampusTextures::shelfBoard);
        still("blocks/frame/book_spine", CampusTextures::bookSpine);

        supplyPort();
    }

    // ---- helpers (as in NexusTextures)

    static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) ((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t);
        int g = (int) ((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    static double dist(double x, double y) {
        return Math.hypot(x - 7.5, y - 7.5);
    }

    static double hash(int x, int y, int s) {
        double v = Math.sin(x * 12.9898 + y * 78.233 + s * 37.719) * 43758.5453;
        return v - Math.floor(v);
    }

    /**
     * A slow sine of brightness running along a line: 0 to 1, period one block, so it wraps seamlessly at the block
     * edge; {@code pos} is the place along the line in blocks (0 to 1).
     */
    static double swell(double pos, int f) {
        return 0.5 + 0.5 * Math.cos((pos - f / (double) FRAMES) * Math.PI * 2);
    }

    /** A light line's colour: {@code lo} at rest, rising a little towards white as the swell passes. */
    static int line(int lo, double s, double amount) {
        return mix(lo, WHITE, s * amount);
    }

    /** A barely visible grain so flat areas do not look painted; {@code amount} is the largest shift. */
    static int grain(int c, int x, int y, int seed, double amount) {
        double h = hash(x, y, seed) - 0.5;
        return h < 0 ? mix(c, 0x000000, -h * 2 * amount) : mix(c, 0xFFFFFF, h * 2 * amount);
    }

    // ---- deck: floor tiles

    /**
     * A floor tile: one calm blue-grey plate per block with a faint panel line along two edges and a hint of shadow
     * along the other two, so a plaza reads as large flush plates, not as a grid of bevelled cubes.
     */
    static int deck(int x, int y, int f) {
        return plate(x, y, DECK, DECK_LINE, 0x384551, 3);
    }

    /** A tone-2 tile, used in clusters for the radial tone gradient of the forum. */
    static int dark(int x, int y, int f) {
        return plate(x, y, DARK, 0x35424D, 0x28333C, 5);
    }

    static int plate(int x, int y, int c, int line, int shade, int seed) {
        if (x == 0 || y == 0) return line;
        if (x == 15 || y == 15) return shade;
        return grain(c, x / 2, y, seed, 0.025);
    }

    /** The graphite-navy seam tile (the frame base colour): curbs, seams, the shadow gap, fill and piers. */
    static int trim(int x, int y, int f) {
        if (x == 0 || y == 0) return 0x172434;
        if (x == 15 || y == 15) return 0x0B141D;
        return (x + y) % 2 == 0 ? TRIM : 0x0E1924;
    }

    /**
     * The lit strip's tile: a floor plate with the channel the light runs in, edged in navy. Only the strip differs from
     * the plain deck, so a rib reads as a line of light in the floor rather than as a dark band.
     */
    static int lit(int x, int y, int f) {
        if (litLine(x, y)) return CHANNEL;
        if (x == 6 || x == 9 || y == 6 || y == 9) return LIT_BED;
        return deck(x, y, f);
    }

    static boolean litLine(int x, int y) {
        return x == 7 || x == 8 || y == 7 || y == 8;
    }

    /**
     * The flush strip: a two-pixel cross running edge to edge, so a rib of lit tiles is one unbroken line whichever way
     * it runs, like the lit spokes of the nexus base it continues.
     */
    static int litGlow(int x, int y, int f) {
        if (!litLine(x, y)) return 0;
        double s = Math.max(x == 7 || x == 8 ? swell(y / 16.0, f) : 0, y == 7 || y == 8 ? swell(x / 16.0, f) : 0);
        return line(mix(LIT, CYAN, 0.25), s, 0.6);
    }

    /**
     * The lit strip's upright faces: the floor plate with one level channel through the middle, so where the strip
     * forms an edge (the gallery ring round the archive's void) its face shows a single line of light running on from
     * block to block, not a cross in every block.
     */
    static int litSide(int x, int y, int f) {
        if (y == 7 || y == 8) return CHANNEL;
        if (y == 6 || y == 9) return LIT_BED;
        return deck(x, y, f);
    }

    static int litSideGlow(int x, int y, int f) {
        if (y != 7 && y != 8) return 0;
        return line(mix(LIT, CYAN, 0.25), swell(x / 16.0, f), 0.6);
    }

    /** A vent tile: four slots in a darker plate. */
    static int grate(int x, int y, int f) {
        if (x == 0 || y == 0) return 0x303D48;
        if (x == 15 || y == 15) return 0x19232C;
        if (x >= 3 && x <= 12) {
            int r = (y - 2) % 3;
            if (y >= 2 && y <= 13) {
                if (r == 0) return 0x0A1016;
                if (r == 1) return 0x0E1820;
                if (x == 3 || x == 12) return 0x1C2731;
                return 0x2D3A45;
            }
        }
        if (x == 2 || x == 13) return 0x1C2731;
        return GRATE;
    }

    /** The edge face of a raised deck: navy, with a lit reveal just under the floor's edge. */
    static int skirt(int x, int y, int f) {
        if (y == 0) return 0x24405A;
        if (y == 1 || y == 3) return SHADOW;
        if (y == 2) return CHANNEL;
        if (y == 15) return 0x0A131C;
        if (x == 15) return 0x0F1A26;
        if (x == 0) return 0x1A2A3C;
        return grain(0x13202E, x, y, 11, 0.02);
    }

    static int skirtGlow(int x, int y, int f) {
        if (y == 2) return line(mix(LIT, CYAN, 0.4), swell(x / 16.0, f), 0.5);
        if (y == 3) return mix(CYAN_DARK, 0x000000, 0.35);
        return 0;
    }

    /** True on the lit arrow: a bold chevron pointing to the top edge of the texture. */
    static boolean chevronLine(int x, int y) {
        double d = y - 3.5 - Math.abs(x - 7.5);
        return d >= 0 && d < 3 && Math.abs(x - 7.5) < 6.5;
    }

    /** A lit arrow inlaid in a trim tile; five of them form the V in front of a door. */
    static int chevron(int x, int y, int f) {
        if (chevronLine(x, y)) return CHANNEL;
        double d = y - 3.5 - Math.abs(x - 7.5);
        if ((d >= -1 && d < 0 || d >= 3 && d < 4) && Math.abs(x - 7.5) < 7) return 0x0A131C;
        return trim(x, y, f);
    }

    static int chevronGlow(int x, int y, int f) {
        if (!chevronLine(x, y)) return 0;
        // the light runs towards the point
        double s = swell((15 - y) / 16.0, f);
        return line(mix(LIT, CYAN, 0.3), s, 0.6);
    }

    /** The bed of a light well: navy cells under the glow. */
    static int well(int x, int y, int f) {
        if (x % 8 == 0 || y % 8 == 0) return 0x24405A;
        return mix(LIT_BED, CHANNEL, 0.5);
    }

    /**
     * The light well's glow: the whole face lit cyan in four cells per block with brighter seams, so three by three
     * tiles read as one glowing floor; the swell crosses it diagonally and wraps at the block edge.
     */
    static int wellGlow(int x, int y, int f) {
        double s = swell(((x + y) % 16) / 16.0, f);
        if (x % 8 == 0 || y % 8 == 0) return line(LIT, s, 0.5);
        double d = Math.min(Math.min(x % 8, 8 - x % 8), Math.min(y % 8, 8 - y % 8));
        int c = mix(mix(CYAN, LIT, 0.25), CYAN, Math.min(1, (d - 1) / 2.0));
        return mix(c, WHITE, s * 0.18);
    }

    // ---- deck: facade panels

    /** A facade or roof panel, the floor's tone a shade lighter, with a recessed vertical seam between panels. */
    static int panel(int x, int y, int f) {
        if (x == 15) return 0x33404B;
        if (x == 0) return 0x515E6A;
        if (y == 15) return 0x404D59;
        return grain(PANEL, x, y / 4, 13, 0.02);
    }

    static boolean panelSeam(int x) {
        return x == 7 || x == 8;
    }

    /** A pilaster or rib: the panel with a lit seam up its middle. */
    static int panelLit(int x, int y, int f) {
        if (panelSeam(x)) return CHANNEL;
        if (x == 6 || x == 9) return 0x2A3642;
        return panel(x, y, f);
    }

    static int panelLitGlow(int x, int y, int f) {
        if (!panelSeam(x)) return 0;
        // the light rises
        return line(mix(LIT, CYAN, 0.25), swell((15 - y) / 16.0, f), 0.6);
    }

    /** The plinth course: a dark panel with a lit reveal along its foot. */
    static int panelDark(int x, int y, int f) {
        if (y == 13) return SHADOW;
        if (y == 14) return CHANNEL;
        if (y == 15) return 0x0E151C;
        if (x == 15) return 0x111A22;
        if (x == 0) return 0x24313C;
        return grain(PANEL_DARK, x, y / 4, 17, 0.02);
    }

    static int panelDarkGlow(int x, int y, int f) {
        if (y != 14) return 0;
        return mix(CYAN_DARK, CYAN, 0.55 + 0.25 * swell(x / 16.0, f));
    }

    /** The cornice band: a steel lip, a navy band, and a bright line in a channel under the lip. */
    static int cornice(int x, int y, int f) {
        if (y == 0) return 0x56636F;
        if (y == 1) return PANEL;
        if (y == 2) return 0x2E3B47;
        if (y == 8 || y == 11) return SHADOW;
        if (y == 9 || y == 10) return CHANNEL;
        if (y == 14) return 0x0F1A26;
        if (y == 15) return 0x0A131C;
        return grain(NAVY, x, y, 19, 0.02);
    }

    static int corniceGlow(int x, int y, int f) {
        if (y != 9 && y != 10) return 0;
        return line(y == 9 ? LIT : mix(LIT, CYAN, 0.35), swell(x / 16.0, f), 0.5);
    }

    // ---- fittings

    /**
     * The upright between two shelf units, drawn across the column's width (pixels 2 to 13): bright steel edge on the
     * left, navy on the right, a channel up the middle for the lit seam. It has no caps, so a stack of posts is one
     * column.
     */
    static int postSide(int x, int y, int f) {
        int cx = Math.max(2, Math.min(13, x));
        if (cx == 2) return STEEL_HI;
        if (cx == 13) return 0x10202E;
        if (cx == 7 || cx == 8) return CHANNEL;
        if (cx == 6 || cx == 9) return 0x132A3A;
        return mix(STEEL, NAVY, (cx - 3) / 10.0);
    }

    static int postTop(int x, int y, int f) {
        int cx = Math.max(2, Math.min(13, x)), cy = Math.max(2, Math.min(13, y));
        if (cx == 2 || cy == 2) return STEEL_HI;
        if (cx == 13 || cy == 13) return 0x10202E;
        if ((cx == 7 || cx == 8) && (cy == 7 || cy == 8)) return CYAN_DARK;
        return grain(NAVY, cx, cy, 23, 0.03);
    }

    static int postGlow(int x, int y, int f) {
        if (x != 7 && x != 8) return 0;
        return line(mix(LIT, CYAN, 0.45), swell((15 - y) / 16.0, f), 0.5);
    }

    /** The foot of a shelf unit: a steel-lipped navy plinth over a recessed kick light. */
    static int plinthSide(int x, int y, int f) {
        if (y == 0) return STEEL_HI;
        if (y == 1) return STEEL;
        if (y == 11) return STEEL_LO;
        if (y == 12) return SHADOW;
        if (y == 13) return CHANNEL;
        if (y == 14) return 0x0A1018;
        if (y == 15) return TRIM;
        return grain(NAVY, x, y / 3, 29, 0.025);
    }

    static int plinthGlow(int x, int y, int f) {
        double s = swell(x / 16.0, f);
        if (y == 13) return line(mix(LIT, CYAN, 0.2), s, 0.5);
        if (y == 14) return mix(CYAN_DARK, CYAN, 0.15 * s);
        return 0;
    }

    /** A steel top plate: a bevelled rim, a navy field and a screw in each corner. */
    static int steelPlate(int x, int y, int seed) {
        if (x == 0 || y == 0) return STEEL_HI;
        if (x == 15 || y == 15) return 0x10202E;
        if (x == 1 || y == 1 || x == 14 || y == 14) return STEEL;
        if ((x == 2 || x == 13) && (y == 2 || y == 13)) return 0x5A87A6;
        return grain(NAVY, x, y, seed, 0.025);
    }

    static int plinthTop(int x, int y, int f) {
        return steelPlate(x, y, 31);
    }

    /** The top of a shelf unit: a steel lip, the violet index strip with its marks, a downlight over the books. */
    static int crownSide(int x, int y, int f) {
        if (y == 0) return STEEL_HI;
        if (y == 1) return STEEL;
        if (y == 4 || y == 9) return 0x0C0A1A;
        if (y == 5 || y == 8) return 0x2A2050;
        if (y == 6 || y == 7) return 0x3C2470;
        if (y == 12) return STEEL_LO;
        if (y == 13) return SHADOW;
        if (y == 14) return CHANNEL;
        if (y == 15) return SHADOW;
        return grain(NAVY, x, y, 37, 0.025);
    }

    static int crownGlow(int x, int y, int f) {
        double s = swell(x / 16.0, f);
        if (y == 6 || y == 7) {
            int c = mix(VIOLET, VIOLET_HI, 0.15 + 0.35 * s);
            // index marks every quarter block
            if (x % 4 == 1) c = mix(c, 0xFFFFFF, 0.35);
            return c;
        }
        if ((y == 5 || y == 8) && x % 4 == 1) return mix(VIOLET, 0x000000, 0.3);
        if (y == 14) return mix(CYAN_DARK, CYAN, 0.5 + 0.3 * s);
        return 0;
    }

    static int crownTop(int x, int y, int f) {
        return steelPlate(x, y, 41);
    }

    /**
     * A gallery rail: a steel handrail with a lit line, a kick rail at the foot, and glass between (cut out, with a
     * few streaks), so the void beyond stays visible.
     */
    static int railSide(int x, int y, int f) {
        if (y == 0) return STEEL_HI;
        if (y == 1) return CHANNEL;
        if (y == 2) return STEEL;
        if (y == 3) return STEEL_LO;
        if (y == 13) return STEEL;
        if (y == 14) return NAVY;
        if (y == 15) return 0x10202E;
        return glassStreak(x, y) ? 0xA9D2E2 : 0;
    }

    static boolean glassStreak(int x, int y) {
        int d = x + y;
        return d == 10 && x >= 2 && x <= 5 || d == 13 && x >= 3 && x <= 4 || d == 21 && x >= 10 && x <= 12;
    }

    static int railTop(int x, int y, int f) {
        return grain(STEEL, x, y, 43, 0.04);
    }

    static int railGlow(int x, int y, int f) {
        if (y != 1) return 0;
        return line(mix(LIT, CYAN, 0.3), swell(x / 16.0, f), 0.55);
    }

    /**
     * A stair tread, drawn for the lower half (rows 8 to 15) and repeated above: a steel nose with a lit line under it
     * over a panel face.
     */
    static int treadSide(int x, int y, int f) {
        int r = y % 8;
        if (r == 0) return 0x6A7988;
        if (r == 1) return CHANNEL;
        if (r == 2) return SHADOW;
        if (r == 7) return 0x34404B;
        return grain(PANEL, x, 0, 47, 0.02);
    }

    /** The walking face: the panel with a grid of anti-slip studs, the same in every direction. */
    static int treadTop(int x, int y, int f) {
        if (x % 4 == 1 && y % 4 == 1) return 0x5D6A77;
        if (x % 4 == 2 && y % 4 == 2) return 0x37434E;
        if (x == 0 || y == 0) return 0x4E5B67;
        if (x == 15 || y == 15) return 0x3A4652;
        return PANEL;
    }

    static int treadGlow(int x, int y, int f) {
        if (y % 8 != 1) return 0;
        return line(mix(LIT, CYAN, 0.25), swell(x / 16.0, f), 0.55);
    }

    /** Glazing: a steel-blue frame round clear glass with a few streaks (cut out, not translucent). */
    static int glaze(int x, int y, int f) {
        if (x == 0 || y == 0) return 0x3A6886;
        if (x == 15 || y == 15) return NAVY;
        if (x == 1 && y < 15 || y == 1 && x < 15) return 0x22445C;
        return glassStreak(x, y) ? 0xB8DDEA : 0;
    }

    /**
     * The pedestal under a hologram, 3 to 13 wide and 12 high (rows 4 to 15 show): a steel collar with a ring of
     * light, a navy body with a faint seam, a flared foot.
     */
    static int pedestalSide(int x, int y, int f) {
        int cx = Math.max(3, Math.min(12, x));
        int c;
        if (y == 4) c = STEEL_HI;
        else if (y == 5) c = STEEL;
        else if (y == 6) c = CHANNEL;
        else if (y == 7) c = SHADOW;
        else if (y == 14) c = STEEL;
        else if (y == 15) c = 0x10202E;
        else if (cx == 7 || cx == 8) c = 0x16303E;
        else c = grain(NAVY, cx, y, 53, 0.025);
        if (y >= 8 && y <= 13) {
            if (cx == 3) c = 0x24405A;
            if (cx == 12) c = 0x10202E;
        }
        return c;
    }

    static int pedestalGlow(int x, int y, int f) {
        double s = swell(x / 16.0, f);
        if (y == 6) return line(mix(LIT, CYAN, 0.2), s, 0.55);
        if ((x == 7 || x == 8) && y >= 8 && y <= 13) return mix(CYAN_DARK, CYAN, 0.35 * swell((15 - y) / 16.0, f));
        return 0;
    }

    /** The emitter on top: a steel rim round dark glass, a cyan ring and a bright centre (rows and columns 3 to 12). */
    static int pedestalTop(int x, int y, int f) {
        int cx = Math.max(3, Math.min(12, x)), cy = Math.max(3, Math.min(12, y));
        if (cx == 3 || cy == 3) return STEEL_HI;
        if (cx == 12 || cy == 12) return 0x10202E;
        double d = dist(cx, cy);
        if (d < 1.2) return LIT;
        if (d < 1.9) return 0x0A1A24;
        if (d < 3.1) return d < 2.5 ? CYAN : 0x2FB5D0;
        if (cx == 4 || cy == 4) return STEEL;
        return 0x081420;
    }

    // ---- frame extras

    /**
     * The inside of a bookcase compartment, set back in a unit body: a dark back panel with a faint horizontal
     * wood-steel grain, in shadow just under the head board (row 1) and just above the foot board (row 14). The boards
     * themselves are geometry the shelf renderer draws at the foot and the head of the body, and the books stand on the
     * foot board in front of this panel, so it paints no boards of its own. It has no vertical edges, so a row of bodies
     * reads as one long shelf.
     */
    static int shelfNiche(int x, int y, int f) {
        if (y == 0) return 0x080B10;
        // a streaky grain along the panel, a touch of the library's violet in it
        double g = hash(x / 3, y, 61) * 0.6 + hash(x / 5 + 7, y, 67) * 0.4;
        int c = mix(0x0F141D, 0x171C2A, g);
        if (hash(x / 4, y, 71) > 0.86) c = mix(c, 0x1C1832, 0.6);
        // the head board throws a shadow; the foot board a thinner one
        if (y == 1) c = mix(c, 0x000000, 0.35);
        if (y == 2) c = mix(c, 0x000000, 0.15);
        if (y == 14) c = mix(c, 0x000000, 0.2);
        return c;
    }

    /**
     * The boards, side walls and side panels of a bookcase compartment: one even steel-violet tone, clearly lighter than
     * the back panel, with only a faint grain. The renderer cuts thin slices out of it (a one-pixel board front samples
     * a single row, a side wall a single column), so it has no edges or bevels: every slice reads as the same board,
     * and the light and shade of each face come from the lighting alone.
     */
    static int shelfBoard(int x, int y, int f) {
        return grain(mix(0x3E4856, 0x2A2240, 0.4), x / 3, y, 79, 0.035);
    }

    /**
     * A book spine in grey, to be tinted with the book's colour: light bands near the head and the foot, a faint
     * label in between and a leathery unevenness, the same across the width so any slice of it looks right.
     */
    static int bookSpine(int x, int y, int f) {
        if (y == 0 || y == 15) return 0x7A7A7A;
        if (y == 1 || y == 14) return 0xA0A0A0;
        if (y == 2 || y == 13) return 0xEDEDED;
        if (y == 3 || y == 12) return 0x8C8C8C;
        if (y >= 6 && y <= 8) return y == 7 ? 0xD8D8D8 : 0xC4C4C4;
        int v = 0xB4 + (int) ((hash(0, y, 73) - 0.5) * 10);
        return v << 16 | v << 8 | v;
    }

    // ---- the supply port

    /**
     * The supply port ({@code blocks/supply_port/}): a navy steel crate banded like the fittings, an intake funnel on
     * top ringed by a cyan lip, and on its front a recessed mouth with a cyan chevron pointing into it, so it reads
     * from afar as the place things go in.
     */
    static void supplyPort() throws Exception {
        still("blocks/supply_port/top", CampusTextures::portTop);
        still("blocks/supply_port/side", CampusTextures::portSide);
        still("blocks/supply_port/front", CampusTextures::portFront);
    }

    /** The crate's shell: a bevelled rim, a steel band at the top and the bottom, a rivet at each band's ends. */
    static int portShell(int x, int y, int seed) {
        if (x == 0 || y == 0) return STEEL_HI;
        if (x == 15 || y == 15) return 0x10202E;
        if ((x == 2 || x == 13) && (y == 1 || y == 14)) return 0x5A87A6;
        if (y == 1 || y == 2) return y == 1 ? STEEL : mix(STEEL, NAVY, 0.4);
        if (y == 13 || y == 14) return y == 14 ? STEEL_LO : mix(STEEL_LO, SHADOW, 0.35);
        return grain(NAVY, x, y / 2, seed, 0.025);
    }

    /** The side: the shell with a lit seam running up its middle. */
    static int portSide(int x, int y, int f) {
        if (y >= 4 && y <= 11 && (x == 7 || x == 8)) return x == 7 ? CHANNEL : CYAN_DARK;
        if (y >= 3 && y <= 12 && (x == 6 || x == 9)) return 0x132A3A;
        return portShell(x, y, 41);
    }

    /** The top: a square funnel stepping down to a dark throat, its lip a thin cyan ring. */
    static int portTop(int x, int y, int f) {
        double d = Math.max(Math.abs(x - 7.5), Math.abs(y - 7.5));
        if (d > 7) return x == 0 || y == 0 ? STEEL_HI : 0x10202E;
        if (d > 6) return STEEL;
        if (d > 5) return grain(NAVY, x, y, 43, 0.025);
        if (d > 4) return mix(CYAN, LIT, 0.3);
        if (d > 3) return STEEL_LO;
        if (d > 2) return 0x132838;
        if (d > 1) return 0x0B1824;
        return SHADOW;
    }

    /** The front: the shell with a recessed mouth (shadowed above, lit below) and a chevron pointing into it. */
    static int portFront(int x, int y, int f) {
        if (x >= 3 && x <= 12 && y >= 3 && y <= 7) {
            if (y == 3 || x == 3) return SHADOW;
            if (y == 7 || x == 12) return STEEL_HI;
            if (y == 4) return SHADOW;
            if (y == 5) return CHANNEL;
            return mix(CHANNEL, CYAN_DARK, 0.6);
        }
        int arm = y - 9;
        if (arm >= 0 && arm <= 2 && (x == 7 - arm || x == 8 + arm)) return mix(CYAN, LIT, 0.4);
        if (arm >= 0 && arm <= 2 && (x == 6 - arm || x == 9 + arm)) return CYAN_DARK;
        return portShell(x, y, 47);
    }

    // ---- writing (as in NexusTextures)

    static void still(String name, Px p) throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int c = p.at(x, y, 0);
            if (c != 0) img.setRGB(x, y, 0xFF000000 | c);
        }
        save(img, name);
    }

    static void anim(String name, Px p) throws Exception {
        BufferedImage img = new BufferedImage(16, 16 * FRAMES, BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < FRAMES; f++) for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int c = p.at(x, y, f);
            if (c != 0) img.setRGB(x, f * 16 + y, 0xFF000000 | c);
        }
        save(img, name);
        java.nio.file.Files.write(
            new File(ROOT + name + ".png.mcmeta").toPath(),
            "{\n  \"animation\": { \"frametime\": 3 }\n}\n".getBytes());
    }

    static void save(BufferedImage img, String name) throws Exception {
        File f = new File(ROOT + name + ".png");
        f.getParentFile()
            .mkdirs();
        ImageIO.write(img, "png", f);
    }
}
