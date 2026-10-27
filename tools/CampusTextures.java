import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the textures of the campus builder (0.10.0): the deck tiles the nexus paves its campus and builds its facades
 * with, the fittings a shelf unit and a library interior are assembled from, three extras for the shelf-unit look of
 * the frame's echo shelf (the recessed compartment's back panel, its boards and a book spine to tint), the supply
 * port's faces, (0.10.1) the recycle bin's, (0.11.0) the city's sample socket and basin rim, (0.12.0) the light-lift
 * pad, and (0.14.0) the canvas. Run from the repository root: {@code java tools/CampusTextures.java}.
 * <p>
 * The palette (0.17.0, the redesign's seven-row recolouring, style §7.1): the deck and the fittings changed colour in
 * one go, so every building already standing changed with them, its IDs, metas and light levels untouched. The floor
 * is a silver-grey rock between the warm black footing and the pearl-white hull (L* about 63); the facade panels a
 * pale warm grey, the plinth course the footing's warm black, the cornice and every fitting the hull's white; the
 * canvas is rice paper. Light is drawn in two layers as everywhere in the city: a white core with a coloured edge, cyan
 * (the nexus's) on the light channels and the lift, warm white (a person's scale) on everything else. Every tile that
 * is laid side by side (floor, wall panels, bands, unit plinths and crowns) still tiles seamlessly, and its light lines
 * run on unbroken from block to block. The parts that glow come as separate overlays ({@code *_glow}) with a
 * transparent background, drawn at full brightness by the block renderers; their animation is a slow swell of the
 * edge towards white that wraps at the block edge, the core staying white. The supply port and the recycle bin keep
 * the 0.16 navy steel: they are not among the seven rows.
 */
public class CampusTextures {

    /**
     * Where the textures go: the mod's assets, or (0.17.0) the folder given as the first argument, so a test can draw
     * them aside and compare them with the committed ones.
     */
    static String root = "src/main/resources/assets/fluxecho/textures/";

    // the 0.16 campus palette: the supply port and the recycle bin still use it
    static final int LIT = 0xC8F8FF, NAVY = 0x1C3446, STEEL = 0x2C5470, CYAN = 0x4FE3FF, VIOLET = 0x8A5CFF;
    static final int CHANNEL = 0x0A2632, CYAN_DARK = 0x1C6F84, STEEL_HI = 0x3E6E8E, STEEL_LO = 0x1E3E54,
        SHADOW = 0x070D13, VIOLET_HI = 0xC9B4FF;

    // the 0.17.0 palette (style §3.5, §7.1): the hull, the footing, the silver-grey rock, the pale panel, rice paper
    static final int HULL = 0xEDF1F3, HULL_SEAM = 0xC3CCD3, HULL_RIVET = 0xD5DCE1, HULL_LIT = 0xF8FAFB,
        HULL_SHADE = 0xDCE2E6, HULL_DEEP = 0xB2BCC4;
    static final int FOOTING = 0x36312D, FOOTING_SEAM = 0x221E1B, FOOTING_TOP = 0x433C37, FOOTING_CHAMFER = 0x3D3733;
    static final int ROCK = 0x9C9892, ROCK_LINE = 0xA9A59F, ROCK_SHADE = 0x8E8A84, ROCK_CHANNEL = 0x7F7B75,
        ROCK_TRIM = 0xB3AFA8;
    static final int PANEL_PALE = 0xDAD9D4, PAPER = 0xF1E8D8, PAPER_DARK = 0xE6DBC6, INK = 0x6E6556;
    // the light (as com.fluxecho.logic.skin.SkinPalette has it): the white core, the nexus cyan, warm white
    static final int GLOW_CORE = 0xFFFFFF, NEXUS_CYAN = 0x4FE3FF, WARM_WHITE = 0xFFE6B8, WHITE = 0xFFFFFF;
    static final int FRAMES = 8;

    static final String[] DECK_NAMES = { "deck", "trim", "lit", "grate", "dark", "skirt", "chevron", "well", "panel",
        "panel_lit", "panel_dark", "cornice", "canvas" };
    static final String[] FITTING_NAMES = { "post", "plinth", "crown", "rail", "tread", "glaze", "pedestal", "socket",
        "basin", "lift" };

    interface Px {

        /** The colour at a pixel, 0 for transparent; {@code f} is the animation frame. */
        int at(int x, int y, int f);
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) root = args[0];
        Px[] deck = { CampusTextures::deck, CampusTextures::trim, CampusTextures::lit, CampusTextures::grate,
            CampusTextures::dark, CampusTextures::skirt, CampusTextures::chevron, CampusTextures::well,
            CampusTextures::panel, CampusTextures::panelLit, CampusTextures::panelDark, CampusTextures::cornice,
            CampusTextures::canvas };
        Px[] deckGlow = { null, null, CampusTextures::litGlow, null, null, CampusTextures::skirtGlow,
            CampusTextures::chevronGlow, CampusTextures::wellGlow, null, CampusTextures::panelLitGlow,
            CampusTextures::panelDarkGlow, CampusTextures::corniceGlow, null };
        for (int i = 0; i < DECK_NAMES.length; i++) {
            still("blocks/deck/" + DECK_NAMES[i], deck[i]);
            if (deckGlow[i] != null) anim("blocks/deck/" + DECK_NAMES[i] + "_glow", deckGlow[i]);
        }
        // the lit strip's upright faces (a gallery edge seen from the void): one level line, not the floor's cross
        still("blocks/deck/lit_side", CampusTextures::litSide);
        anim("blocks/deck/lit_side_glow", CampusTextures::litSideGlow);

        Px[] side = { CampusTextures::postSide, CampusTextures::plinthSide, CampusTextures::crownSide,
            CampusTextures::railSide, CampusTextures::treadSide, CampusTextures::glaze, CampusTextures::pedestalSide,
            CampusTextures::socketSide, CampusTextures::basinSide, CampusTextures::liftSide };
        Px[] top = { CampusTextures::postTop, CampusTextures::plinthTop, CampusTextures::crownTop,
            CampusTextures::railTop, CampusTextures::treadTop, CampusTextures::glaze, CampusTextures::pedestalTop,
            CampusTextures::socketTop, CampusTextures::basinTop, CampusTextures::liftTop };
        Px[] glow = { CampusTextures::postGlow, CampusTextures::plinthGlow, CampusTextures::crownGlow,
            CampusTextures::railGlow, CampusTextures::treadGlow, null, CampusTextures::pedestalGlow,
            CampusTextures::socketGlow, CampusTextures::basinGlow, CampusTextures::liftGlow };
        for (int i = 0; i < FITTING_NAMES.length; i++) {
            still("blocks/fitting/" + FITTING_NAMES[i], side[i]);
            still("blocks/fitting/" + FITTING_NAMES[i] + "_top", top[i]);
            if (glow[i] != null) anim("blocks/fitting/" + FITTING_NAMES[i] + "_glow", glow[i]);
        }

        still("blocks/frame/shelf_niche", CampusTextures::shelfNiche);
        still("blocks/frame/shelf_board", CampusTextures::shelfBoard);
        still("blocks/frame/book_spine", CampusTextures::bookSpine);

        supplyPort();
        recycleBin();
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

    /**
     * A pixel of a glowing line (0.17.0, style §3.6): {@code d} pixels out from its core, the white core at 0 (it stays
     * white through the animation), the coloured edge at 1, lifted towards white as the swell {@code s} passes, and
     * nothing further out.
     */
    static int glow(int d, int edge, double s) {
        if (d == 0) return GLOW_CORE;
        if (d == 1) return mix(edge, WHITE, 0.35 * s);
        return 0;
    }

    /** A barely visible grain so flat areas do not look painted; {@code amount} is the largest shift. */
    static int grain(int c, int x, int y, int seed, double amount) {
        double h = hash(x, y, seed) - 0.5;
        return h < 0 ? mix(c, 0x000000, -h * 2 * amount) : mix(c, 0xFFFFFF, h * 2 * amount);
    }

    // ---- deck: floor tiles

    /**
     * A floor tile: one calm silver-grey rock plate per block with a faint lighter line along two edges and a hint of
     * shadow along the other two, so a plaza reads as large flush plates, not as a grid of bevelled cubes. Its
     * lightness, L* about 63, sits between the footing's dark and the hull's light (style §7.1 row 3).
     */
    static int deck(int x, int y, int f) {
        return plate(x, y, ROCK, ROCK_LINE, ROCK_SHADE, 3, 0.035);
    }

    /** The dark tile, used in clusters for the forum's tone gradient: now the footing's warm black basalt. */
    static int dark(int x, int y, int f) {
        return plate(x, y, FOOTING, FOOTING_TOP, FOOTING_SEAM, 5, 0.05);
    }

    static int plate(int x, int y, int c, int line, int shade, int seed, double grain) {
        if (x == 0 || y == 0) return line;
        if (x == 15 || y == 15) return shade;
        return grain(c, x / 2, y, seed, grain);
    }

    /** The seam tile: curbs, seams, the shadow gap, fill and piers, a lighter trim of the same rock. */
    static int trim(int x, int y, int f) {
        if (x == 0 || y == 0) return 0xC1BDB6;
        if (x == 15 || y == 15) return 0x9F9B95;
        return (x + y) % 2 == 0 ? ROCK_TRIM : 0xAEAAA3;
    }

    /**
     * The lit strip's tile: a floor plate with the channel the light runs in. Only the strip differs from the plain
     * deck, so a rib reads as a line of light in the floor. The light is a cross, so a strip of these reads as one
     * unbroken line whichever way it runs: the deck block cannot tell which way its strip goes.
     */
    static int lit(int x, int y, int f) {
        if (litLine(x, y)) return ROCK_CHANNEL;
        if (x == 6 || x == 9 || y == 6 || y == 9) return ROCK_SHADE;
        return deck(x, y, f);
    }

    static boolean litLine(int x, int y) {
        return x == 7 || x == 8 || y == 7 || y == 8;
    }

    /**
     * The flux channel (style §7.1 row 4): a two-pixel white core running edge to edge with a cyan edge either side, in
     * both directions, the line the vein channels and the ring channel are drawn with.
     */
    static int litGlow(int x, int y, int f) {
        if (litLine(x, y)) return GLOW_CORE;
        boolean down = x == 6 || x == 9, across = y == 6 || y == 9;
        if (!down && !across) return 0;
        double s = Math.max(down ? swell(y / 16.0, f) : 0, across ? swell(x / 16.0, f) : 0);
        return glow(1, NEXUS_CYAN, s);
    }

    /**
     * The lit strip's upright faces: the rock with one level channel through the middle, so where the strip forms an
     * edge (the gallery ring round the archive's void) its face shows a single line of light running on from block to
     * block, not a cross in every block.
     */
    static int litSide(int x, int y, int f) {
        if (y == 7 || y == 8) return ROCK_CHANNEL;
        if (y == 6 || y == 9) return ROCK_SHADE;
        return deck(x, y, f);
    }

    static int litSideGlow(int x, int y, int f) {
        if (y == 7 || y == 8) return GLOW_CORE;
        if (y == 6 || y == 9) return glow(1, NEXUS_CYAN, swell(x / 16.0, f));
        return 0;
    }

    /** A vent tile in the footing's basalt: four slots in a dark plate. */
    static int grate(int x, int y, int f) {
        if (x == 0 || y == 0) return FOOTING_TOP;
        if (x == 15 || y == 15) return FOOTING_SEAM;
        if (x >= 3 && x <= 12) {
            int r = (y - 2) % 3;
            if (y >= 2 && y <= 13) {
                if (r == 0) return 0x14110F;
                if (r == 1) return 0x1C1916;
                if (x == 3 || x == 12) return FOOTING_SEAM;
                return FOOTING_CHAMFER;
            }
        }
        if (x == 2 || x == 13) return FOOTING_SEAM;
        return FOOTING;
    }

    /** The edge face of a raised deck: the rock's face, a lip over a warm-white nose line just under the floor's edge. */
    static int skirt(int x, int y, int f) {
        if (y == 0) return ROCK_TRIM;
        if (y >= 1 && y <= 3) return ROCK_CHANNEL;
        if (y == 15) return 0x7A7671;
        if (x == 15) return 0x86827C;
        if (x == 0) return 0x9A968F;
        return grain(0x918D87, x, y, 11, 0.03);
    }

    static int skirtGlow(int x, int y, int f) {
        if (y == 2) return GLOW_CORE;
        if (y == 1 || y == 3) return glow(1, WARM_WHITE, swell(x / 16.0, f));
        return 0;
    }

    /** True on the lit arrow: a bold chevron pointing to the top edge of the texture. */
    static boolean chevronLine(int x, int y) {
        double d = y - 3.5 - Math.abs(x - 7.5);
        return d >= 0 && d < 3 && Math.abs(x - 7.5) < 6.5;
    }

    /** A lit arrow inlaid in a trim tile; five of them form the V in front of a door (only on the way home). */
    static int chevron(int x, int y, int f) {
        if (chevronLine(x, y)) return ROCK_CHANNEL;
        double d = y - 3.5 - Math.abs(x - 7.5);
        if ((d >= -1 && d < 0 || d >= 3 && d < 4) && Math.abs(x - 7.5) < 7) return ROCK_SHADE;
        return trim(x, y, f);
    }

    /** The arrow in warm white (style §7.1 row 5): a white core along its middle, warm edges, running to the point. */
    static int chevronGlow(int x, int y, int f) {
        if (!chevronLine(x, y)) return 0;
        double d = y - 3.5 - Math.abs(x - 7.5);
        return glow(d >= 1 && d < 2 ? 0 : 1, WARM_WHITE, swell((15 - y) / 16.0, f));
    }

    /** The bed of a light well: the footing's basalt in four cells. */
    static int well(int x, int y, int f) {
        if (x % 8 == 0 || y % 8 == 0) return FOOTING_TOP;
        return grain(FOOTING, x, y, 7, 0.04);
    }

    /**
     * The light well's glow (style §7.1 row 4): a ring in every tile, a white core with a cyan edge either side, the
     * swell running round it, so three by three tiles read as a field of rings of light in the dark floor.
     */
    static int wellGlow(int x, int y, int f) {
        double off = Math.abs(dist(x, y) - 5);
        if (off >= 1.6) return 0;
        double pos = Math.atan2(y - 7.5, x - 7.5) / (2 * Math.PI) + 0.5;
        return glow(off < 0.75 ? 0 : 1, NEXUS_CYAN, swell(pos, f));
    }

    // ---- deck: facade panels

    /** A facade or roof panel, pale warm grey (style §7.1 row 6), with a recessed vertical seam between panels. */
    static int panel(int x, int y, int f) {
        if (x == 15) return 0xC3C2BD;
        if (x == 0) return 0xE6E5E1;
        if (y == 15) return 0xCFCEC9;
        return grain(PANEL_PALE, x, y / 4, 13, 0.02);
    }

    static boolean panelSeam(int x) {
        return x == 7 || x == 8;
    }

    /** A pilaster or rib: the pale panel with a lit seam up its middle. */
    static int panelLit(int x, int y, int f) {
        if (panelSeam(x)) return 0xB7B6B1;
        if (x == 6 || x == 9) return 0xC6C5C0;
        return panel(x, y, f);
    }

    /** The seam's warm-white light, rising. */
    static int panelLitGlow(int x, int y, int f) {
        if (panelSeam(x)) return GLOW_CORE;
        if (x == 6 || x == 9) return glow(1, WARM_WHITE, swell((15 - y) / 16.0, f));
        return 0;
    }

    /**
     * The plinth course: the footing's warm black basalt (style §7.1 row 6: the old buildings' skirt reads as their
     * stone base), a thin lit reveal along its foot.
     */
    static int panelDark(int x, int y, int f) {
        if (y == 13) return FOOTING_SEAM;
        if (y == 14) return 0x1A1714;
        if (y == 15) return FOOTING_SEAM;
        if (y == 0) return FOOTING_TOP;
        if (x == 15) return 0x2C2825;
        if (x == 0) return FOOTING_CHAMFER;
        return grain(FOOTING, x, y / 4, 17, 0.05);
    }

    static int panelDarkGlow(int x, int y, int f) {
        if (y == 14) return GLOW_CORE;
        if (y == 13) return glow(1, WARM_WHITE, swell(x / 16.0, f));
        return 0;
    }

    /** The cornice band: the hull's white, a lip over a band and a lit channel under it (style §7.1 row 6). */
    static int cornice(int x, int y, int f) {
        if (y == 0) return HULL_LIT;
        if (y == 1) return HULL;
        if (y == 2) return HULL_SEAM;
        if (y == 8 || y == 11) return HULL_SHADE;
        if (y == 9 || y == 10) return HULL_SEAM;
        if (y == 14) return HULL_SEAM;
        if (y == 15) return HULL_DEEP;
        return grain(HULL, x, y, 19, 0.015);
    }

    /** The eave's light: a white core in a warm-white edge, the swell running along it. */
    static int corniceGlow(int x, int y, int f) {
        if (y == 9 || y == 10) return GLOW_CORE;
        if (y == 8 || y == 11) return glow(1, WARM_WHITE, swell(x / 16.0, f));
        return 0;
    }

    /**
     * The canvas (0.14.0; 0.17.0 rice paper, style §7.1 row 7): pale warm paper woven in two-pixel threads of warp and
     * weft taking turns over each other, under a faint ink grid every half block, the painter's squaring. It gives no
     * light, and the weave and the grid run on unbroken from block to block, so a wall of canvas reads as one sheet a
     * unit's painting is drawn on.
     */
    static int canvas(int x, int y, int f) {
        boolean warp = ((x >> 1) + (y >> 1)) % 2 == 0;
        int c = warp ? PAPER : PAPER_DARK;
        // a thread's far edge a shade darker, so the weave reads at a glance
        if (warp ? (x & 1) == 1 : (y & 1) == 1) c = mix(c, INK, 0.08);
        c = grain(c, x, y, 29, 0.02);
        if (x % 8 == 0 || y % 8 == 0) c = mix(c, INK, 0.16);
        return c;
    }

    // ---- fittings (0.17.0: the hull's white with warm-white light, style §7.1 row 7)

    /**
     * The upright between two shelf units, and the street lamp's post, drawn across the column's width (pixels 2 to
     * 13): hull white, lit on the left, in shade on the right, a channel up the middle for the lit seam. It has no
     * caps, so a stack of posts is one column.
     */
    static int postSide(int x, int y, int f) {
        int cx = Math.max(2, Math.min(13, x));
        if (cx == 2) return HULL_LIT;
        if (cx == 13) return HULL_DEEP;
        if (cx == 7 || cx == 8) return HULL_SEAM;
        if (cx == 6 || cx == 9) return mix(HULL_SEAM, HULL_DEEP, 0.4);
        return mix(HULL, HULL_SHADE, (cx - 3) / 10.0);
    }

    static int postTop(int x, int y, int f) {
        int cx = Math.max(2, Math.min(13, x)), cy = Math.max(2, Math.min(13, y));
        if (cx == 2 || cy == 2) return HULL_LIT;
        if (cx == 13 || cy == 13) return HULL_DEEP;
        if ((cx == 7 || cx == 8) && (cy == 7 || cy == 8)) return WARM_WHITE;
        return grain(HULL, cx, cy, 23, 0.02);
    }

    static int postGlow(int x, int y, int f) {
        if (x == 7 || x == 8) return GLOW_CORE;
        if (x == 6 || x == 9) return glow(1, WARM_WHITE, swell((15 - y) / 16.0, f));
        return 0;
    }

    /** The foot of a shelf unit: a hull plinth with a lit lip, over a recessed kick light. */
    static int plinthSide(int x, int y, int f) {
        if (y == 0) return HULL_LIT;
        if (y == 1) return HULL;
        if (y == 11) return HULL_SHADE;
        if (y == 12 || y == 14) return HULL_DEEP;
        if (y == 13) return HULL_SEAM;
        if (y == 15) return HULL_SEAM;
        return grain(HULL, x, y / 3, 29, 0.015);
    }

    static int plinthGlow(int x, int y, int f) {
        if (y == 13) return GLOW_CORE;
        if (y == 12 || y == 14) return glow(1, WARM_WHITE, swell(x / 16.0, f));
        return 0;
    }

    /** A hull top plate: a bevelled rim, a white field and a rivet in each corner. */
    static int hullPlate(int x, int y, int seed) {
        if (x == 0 || y == 0) return HULL_LIT;
        if (x == 15 || y == 15) return HULL_SEAM;
        if (x == 1 || y == 1 || x == 14 || y == 14) return HULL_SHADE;
        if ((x == 2 || x == 13) && (y == 2 || y == 13)) return HULL_RIVET;
        return grain(HULL, x, y, seed, 0.015);
    }

    static int plinthTop(int x, int y, int f) {
        return hullPlate(x, y, 31);
    }

    /** The top of a shelf unit: a hull lip, the index strip with its marks, a downlight over the books. */
    static int crownSide(int x, int y, int f) {
        if (y == 0) return HULL_LIT;
        if (y == 1) return HULL;
        if (y == 4 || y == 9) return HULL_DEEP;
        if (y >= 5 && y <= 8) return HULL_SEAM;
        if (y == 12) return HULL_SHADE;
        if (y == 13 || y == 15) return HULL_DEEP;
        if (y == 14) return HULL_SEAM;
        return grain(HULL, x, y, 37, 0.015);
    }

    /** The index strip lit white with warm marks every quarter block, and the downlight's warm-white line. */
    static int crownGlow(int x, int y, int f) {
        double s = swell(x / 16.0, f);
        if (y == 6 || y == 7) return GLOW_CORE;
        if (y == 5 || y == 8) return x % 4 == 1 ? glow(1, WARM_WHITE, s) : 0;
        if (y == 14) return GLOW_CORE;
        if (y == 13) return glow(1, WARM_WHITE, s);
        return 0;
    }

    static int crownTop(int x, int y, int f) {
        return hullPlate(x, y, 41);
    }

    /**
     * A gallery rail: a hull handrail with a lit line, a kick rail at the foot, and glass between (cut out, with a few
     * streaks), so the void beyond stays visible.
     */
    static int railSide(int x, int y, int f) {
        if (y == 0) return HULL_LIT;
        if (y == 1) return HULL_SEAM;
        if (y == 2) return HULL;
        if (y == 3) return HULL_SHADE;
        if (y == 13) return HULL;
        if (y == 14) return HULL_SHADE;
        if (y == 15) return HULL_SEAM;
        return glassStreak(x, y) ? 0xDCEAF0 : 0;
    }

    static boolean glassStreak(int x, int y) {
        int d = x + y;
        return d == 10 && x >= 2 && x <= 5 || d == 13 && x >= 3 && x <= 4 || d == 21 && x >= 10 && x <= 12;
    }

    static int railTop(int x, int y, int f) {
        return grain(HULL, x, y, 43, 0.02);
    }

    static int railGlow(int x, int y, int f) {
        if (y == 1) return GLOW_CORE;
        if (y == 2) return glow(1, WARM_WHITE, swell(x / 16.0, f));
        return 0;
    }

    /**
     * A stair tread, drawn for the lower half (rows 8 to 15) and repeated above: a hull nose with a lit line under it
     * over a hull face.
     */
    static int treadSide(int x, int y, int f) {
        int r = y % 8;
        if (r == 0) return HULL_LIT;
        if (r == 1) return HULL_SEAM;
        if (r == 2) return HULL_DEEP;
        if (r == 7) return HULL_SHADE;
        return grain(HULL, x, 0, 47, 0.015);
    }

    /** The walking face: the hull with a grid of anti-slip studs, the same in every direction. */
    static int treadTop(int x, int y, int f) {
        if (x % 4 == 1 && y % 4 == 1) return HULL_LIT;
        if (x % 4 == 2 && y % 4 == 2) return HULL_SEAM;
        if (x == 0 || y == 0) return HULL_RIVET;
        if (x == 15 || y == 15) return HULL_SHADE;
        return HULL;
    }

    /** The stair nose line (style §7.1 row 7): a white core under the nose, a warm-white edge under it. */
    static int treadGlow(int x, int y, int f) {
        if (y % 8 == 1) return GLOW_CORE;
        if (y % 8 == 2) return glow(1, WARM_WHITE, swell(x / 16.0, f));
        return 0;
    }

    /** Glazing: a hull frame round clear glass with a few streaks (cut out, not translucent). */
    static int glaze(int x, int y, int f) {
        if (x == 0 || y == 0) return HULL_LIT;
        if (x == 15 || y == 15) return HULL_SEAM;
        if (x == 1 && y < 15 || y == 1 && x < 15) return HULL_SHADE;
        return glassStreak(x, y) ? 0xE4F0F4 : 0;
    }

    /**
     * The pedestal under a hologram, 3 to 13 wide and 12 high (rows 4 to 15 show): a hull collar with a ring of light,
     * a white body with a faint seam, a flared foot.
     */
    static int pedestalSide(int x, int y, int f) {
        int cx = Math.max(3, Math.min(12, x));
        int c;
        if (y == 4) c = HULL_LIT;
        else if (y == 5) c = HULL;
        else if (y == 6) c = HULL_SEAM;
        else if (y == 7) c = HULL_DEEP;
        else if (y == 14) c = HULL;
        else if (y == 15) c = HULL_SEAM;
        else if (cx == 7 || cx == 8) c = HULL_SEAM;
        else c = grain(HULL, cx, y, 53, 0.015);
        if (y >= 8 && y <= 13) {
            if (cx == 3) c = HULL_LIT;
            if (cx == 12) c = HULL_DEEP;
        }
        return c;
    }

    static int pedestalGlow(int x, int y, int f) {
        if (y == 6) return GLOW_CORE;
        if (y == 7) return glow(1, WARM_WHITE, swell(x / 16.0, f));
        if ((x == 7 || x == 8) && y >= 8 && y <= 13) return GLOW_CORE;
        return 0;
    }

    /** The emitter on top: a hull rim round dark glass, a warm ring and a bright centre (rows and columns 3 to 12). */
    static int pedestalTop(int x, int y, int f) {
        int cx = Math.max(3, Math.min(12, x)), cy = Math.max(3, Math.min(12, y));
        if (cx == 3 || cy == 3) return HULL_LIT;
        if (cx == 12 || cy == 12) return HULL_SEAM;
        double d = dist(cx, cy);
        if (d < 1.2) return WHITE;
        if (d < 1.9) return 0x2B2F34;
        if (d < 3.1) return d < 2.5 ? WARM_WHITE : mix(WARM_WHITE, HULL_DEEP, 0.4);
        if (cx == 4 || cy == 4) return HULL;
        return 0x34393F;
    }

    // ---- the city's fittings (0.11.0)

    /**
     * Whether a pixel is on the rim round a socket's recess: columns 4 and 11, rows 0 and 5, the corners in. The recess
     * is high on the column (11 to 15 sixteenths up, rows 1 to 4), so a sample shows over a planter's basin rim.
     */
    static boolean socketRim(int x, int y) {
        boolean cols = (x == 4 || x == 11) && y >= 0 && y <= 5, rows = (y == 0 || y == 5) && x >= 4 && x <= 11;
        return cols || rows;
    }

    /** Whether a pixel is in a socket's recess (its back panel, two sixteenths in): columns 5 to 10, rows 1 to 4. */
    static boolean socketRecess(int x, int y) {
        return x >= 5 && x <= 10 && y >= 1 && y <= 4;
    }

    /** Whether a pixel is just outside a socket's rim: the rim's warm edge (columns 3 and 12, row 6). */
    static boolean socketRimEdge(int x, int y) {
        return (x == 3 || x == 12) && y >= 0 && y <= 6 || y == 6 && x >= 3 && x <= 12;
    }

    /**
     * A sample socket's side (the column shows pixels 2 to 13): hull white shading from a lit left edge to a shaded
     * right one like the shelf post, a thin cap over the recess and a tall foot under it with a nosing under the sill
     * and a reveal at the floor, and at the top the recess's back, a panel in shadow under the cap with faint upright
     * grain. The rim round the opening is the glow's.
     */
    static int socketSide(int x, int y, int f) {
        int cx = Math.max(2, Math.min(13, x));
        if (socketRecess(cx, y)) {
            if (y == 1) return 0x7E878F;
            if (y == 2) return 0x8E979F;
            return grain(0x9AA3AB, cx, y / 3, 83, 0.04);
        }
        if (socketRim(cx, y)) return HULL_SEAM;
        if (y == 0) return HULL_LIT;
        if (y == 6) return HULL;
        if (y == 7) return HULL_SHADE;
        if (y == 13) return HULL_SHADE;
        if (y == 14) return HULL_DEEP;
        if (y == 15) return HULL_SEAM;
        if (cx == 2) return HULL_LIT;
        if (cx == 13) return HULL_DEEP;
        return mix(HULL, HULL_SHADE, (cx - 3) / 10.0);
    }

    /** The socket's top: a hull cap (pixels 2 to 13) with a ring and a warm point in its middle. */
    static int socketTop(int x, int y, int f) {
        int cx = Math.max(2, Math.min(13, x)), cy = Math.max(2, Math.min(13, y));
        if (cx == 2 || cy == 2) return HULL_LIT;
        if (cx == 13 || cy == 13) return HULL_DEEP;
        double d = dist(cx, cy);
        if (d < 1.0) return WARM_WHITE;
        if (d < 1.8) return HULL_DEEP;
        if (d > 2.6 && d < 3.5) return HULL_SEAM;
        return grain(HULL, cx, cy, 89, 0.02);
    }

    /** The recess's rim of light, white, its warm edge outside it, the swell running round it. */
    static int socketGlow(int x, int y, int f) {
        if (socketRim(x, y)) return GLOW_CORE;
        if (!socketRimEdge(x, y)) return 0;
        // the place round the rim, 0 to 1, for the swell
        double pos = Math.atan2(y - 2.5, x - 7.5) / (2 * Math.PI) + 0.5;
        return glow(1, WARM_WHITE, swell(pos, f));
    }

    /**
     * A basin rim's side, drawn for the slab's ten sixteenths (rows 6 to 15) and the lip's two above it (rows 4 and
     * 5): the footing's basalt (style §7.1 row 7), a lit nosing, a shadow reveal and a dark foot.
     */
    static int basinSide(int x, int y, int f) {
        if (y <= 3) return FOOTING_SEAM;
        if (y <= 5) return FOOTING_TOP;
        if (y == 6) return 0x4C443E;
        if (y == 7) return FOOTING_TOP;
        if (y == 13) return FOOTING_SEAM;
        if (y == 14) return 0x1A1714;
        if (y == 15) return FOOTING_SEAM;
        return grain(FOOTING, x, y / 2, 97, 0.05);
    }

    /** A basin rim's top: polished basalt whose seams run edge to edge, so a row of rims reads as one kerb. */
    static int basinTop(int x, int y, int f) {
        if (x == 0 || y == 0) return 0x4C443E;
        if (x == 15 || y == 15) return FOOTING_SEAM;
        if ((x == 3 || x == 12) && (y == 3 || y == 12)) return FOOTING_CHAMFER;
        return grain(FOOTING_TOP, x, y, 101, 0.05);
    }

    /**
     * The lip's warm light: a cross edge to edge for the lip's top (its middle and its arms), its two middle pixels
     * white and the outer two warm, and rows 4 (white) and 5 (warm) for its sides; the swell runs along it and wraps at
     * the block edge.
     */
    static int basinGlow(int x, int y, int f) {
        boolean along = x >= 6 && x <= 9, across = y >= 6 && y <= 9, side = y == 4 || y == 5;
        if (!along && !across && !side) return 0;
        boolean core = along && (x == 7 || x == 8) || across && (y == 7 || y == 8) || side && y == 4;
        double s = Math.max(along ? swell(y / 16.0, f) : 0, across || side ? swell(x / 16.0, f) : 0);
        return glow(core ? 0 : 1, WARM_WHITE, s);
    }

    // ---- the light-lift pad (0.12.0)

    /**
     * A light-lift pad's side: a hull block with a lit upper edge, a shaded lower one and a recessed seam under its
     * top, so a pad set into a floor reads as one flush plate.
     */
    static int liftSide(int x, int y, int f) {
        if (y == 0) return HULL_LIT;
        if (y == 1) return HULL;
        if (y == 2) return HULL_SEAM;
        if (y == 15) return HULL_SEAM;
        if (x == 0 || x == 15) return HULL_SHADE;
        return grain(HULL, x, y, 107, 0.015);
    }

    /**
     * A light-lift pad's top: a hull rim round an iris of eight pale blades closing on a light core, the blades' edges
     * in shadow, so the face reads as an opening in the floor.
     */
    static int liftTop(int x, int y, int f) {
        if (x == 0 || y == 0) return HULL_LIT;
        if (x == 15 || y == 15) return HULL_SEAM;
        double d = dist(x, y);
        if (d > 6.6) return grain(HULL, x, y, 109, 0.02);
        if (d > 5.8) return HULL_SEAM;
        if (d < 1.6) return mix(NEXUS_CYAN, WHITE, 0.4);
        // eight blades turning in, each with a shadowed leading edge
        double a = Math.atan2(y - 7.5, x - 7.5) + d * 0.35;
        double blade = (a / (Math.PI / 4)) - Math.floor(a / (Math.PI / 4));
        return blade < 0.14 ? HULL_DEEP : grain(mix(HULL_SHADE, HULL_SEAM, d / 6), x, y, 113, 0.02);
    }

    /**
     * The iris's light (style §7.1 row 7): the core white with a cyan ring round the blades' tips, and a white circle
     * edged in cyan at the rim; the swell runs outward from the core, so the pad seems to breathe.
     */
    static int liftGlow(int x, int y, int f) {
        double d = dist(x, y);
        double s = swell(d / 8.0, f);
        if (d < 1.6) return GLOW_CORE;
        if (d < 2.4) return glow(1, NEXUS_CYAN, s);
        if (d > 5.6 && d < 6.9) return glow(d > 5.95 && d < 6.55 ? 0 : 1, NEXUS_CYAN, s);
        return 0;
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
     * The boards, side walls and side panels of a bookcase compartment: the hull's white (0.17.0, style §7.1 row 2:
     * the shelf's frame turns white, the books do not), clearly lighter than the back panel, with only a faint grain.
     * The renderer cuts thin slices out of it (a one-pixel board front samples a single row, a side wall a single
     * column), so it has no edges or bevels: every slice reads as the same board, and the light and shade of each face
     * come from the lighting alone.
     */
    static int shelfBoard(int x, int y, int f) {
        return grain(mix(HULL, HULL_SHADE, 0.4), x / 3, y, 79, 0.02);
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

    // ---- the recycle bin (0.10.1)

    /**
     * The recycle bin ({@code blocks/recycle_bin/}): the supply port's navy steel crate, read as its twin that gives
     * back: a grated hatch on top ringed in violet, vents on the sides, and on its front the intake, a recessed mouth
     * whose slats glow from the library violet at the top to the flux cyan at the bottom over a lit sill.
     */
    static void recycleBin() throws Exception {
        still("blocks/recycle_bin/top", CampusTextures::binTop);
        still("blocks/recycle_bin/side", CampusTextures::binSide);
        still("blocks/recycle_bin/front", CampusTextures::binFront);
    }

    /** Violet to cyan across {@code t} (0 to 1), the bin's light. */
    static int binLight(double t) {
        return mix(mix(VIOLET, VIOLET_HI, 0.25), mix(CYAN, LIT, 0.2), t);
    }

    /** The side: the shell with two short vents glowing violet to cyan. */
    static int binSide(int x, int y, int f) {
        if ((y == 6 || y == 9) && x >= 4 && x <= 11) return mix(CHANNEL, binLight((x - 4) / 7.0), 0.55);
        if ((y == 5 || y == 8) && x >= 4 && x <= 11) return SHADOW;
        if ((y == 7 || y == 10) && x >= 4 && x <= 11) return STEEL_HI;
        return portShell(x, y, 53);
    }

    /** The top: a grated hatch in a violet ring. */
    static int binTop(int x, int y, int f) {
        double d = Math.max(Math.abs(x - 7.5), Math.abs(y - 7.5));
        if (d > 7) return x == 0 || y == 0 ? STEEL_HI : 0x10202E;
        if (d > 6) return STEEL;
        if (d > 5) return mix(VIOLET, VIOLET_HI, 0.3);
        if (d > 4.5) return STEEL_LO;
        if ((x - 3) % 3 == 0 || (y - 3) % 3 == 0) return mix(STEEL_LO, STEEL, 0.4);
        return mix(SHADOW, binLight((y - 3) / 9.0), 0.18);
    }

    /** The front: the intake, a recessed mouth of glowing slats (violet above, cyan below) over a lit sill. */
    static int binFront(int x, int y, int f) {
        if (x >= 3 && x <= 12 && y >= 3 && y <= 11) {
            if (y == 3 || x == 3) return SHADOW;
            if (y == 11 || x == 12) return STEEL_HI;
            // slats: a lit line under a dark one, the light shading down the mouth
            if (y % 2 == 0) return mix(CHANNEL, binLight((y - 4) / 6.0), 0.7);
            return mix(SHADOW, CHANNEL, 0.5);
        }
        if (y == 13 && x >= 4 && x <= 11) return binLight((x - 4) / 7.0);
        if (y == 12 && x >= 4 && x <= 11) return 0x132A3A;
        return portShell(x, y, 59);
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
            new File(root + name + ".png.mcmeta").toPath(),
            "{\n  \"animation\": { \"frametime\": 3 }\n}\n".getBytes());
    }

    static void save(BufferedImage img, String name) throws Exception {
        File f = new File(root + name + ".png");
        f.getParentFile()
            .mkdirs();
        ImageIO.write(img, "png", f);
    }
}
