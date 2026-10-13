import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the textures of the Flux Nexus era (0.9.0): the flux frame's parts, the nexus's and the library's cores, the
 * flux materials and the library's index cards; (0.11.0) the frame's cladding anchor and the city's module cores;
 * (0.12.0) the phase's materials, phase alloy and resonant glass; and (0.13.0) the four pavilions' module cores.
 * Run from the repository root: {@code java tools/NexusTextures.java}.
 * The parts that glow come as separate overlays ({@code *_glow}), drawn at full brightness by the frame's renderer.
 */
public class NexusTextures {

    static final String ROOT = "src/main/resources/assets/fluxecho/textures/";

    static final int DEEP = 0x0A1622, DEEPER = 0x060D15, PANE = 0x0F2231, SEAM = 0x1F3C4E, CYAN = 0x4FE3FF,
        CYAN_DARK = 0x1C6F84, VIOLET = 0x8A5CFF, VIOLET_DARK = 0x3C2470, GOLD = 0xFFD27A, WHITE = 0xE8FBFF,
        STEEL = 0x56606E, STEEL_LIGHT = 0x8A95A5, STEEL_DARK = 0x262C35;
    /** The Eco Dome's colour (0.11.0), shown in its core's light only. */
    static final int ECO_GREEN = 0x7BE08A;
    /** The Echo Dome Generator's accent (0.12.0), as {@code TileDomeGenerator.COLOR}. */
    static final int GUARD_BLUE = 0x7DB8FF;
    /** The Tidal Well's accent (0.12.0), as {@code TileRoot.COLOR}. */
    static final int TIDE_BLUE = 0x3FC8E8;
    /** The Arcane Court's accent (0.13.0), as {@code TileArcaneCourt.COLOR}. */
    static final int ARCANE_VIOLET = 0xC77DFF;
    /** The Blood Covenant Altar's accent (0.13.0), as {@code TileBloodCovenant.COLOR}. */
    static final int BLOOD_RED = 0xE0344A;
    /** The Echo Hunting Ground's accent (0.13.0), as {@code TileHuntingGround.COLOR}. */
    static final int HUNT_AMBER = 0xFF9A3C;
    /** The Mana Fountain Array's accent (0.13.0), as {@code TileManaFountain.COLOR}. */
    static final int MANA_BLUE = 0x6AA8FF;
    /** The Resonance Gate's accent (0.14.0), as {@code TileResonanceGate.COLOR}. */
    static final int GATE_TEAL = 0x5CF2C2;
    /** The Dual-foil Vault's accent (0.14.0), as {@code TileDualFoilVault.COLOR}: parchment. */
    static final int VAULT_PARCHMENT = 0xF2EBDD;
    static final int FRAMES = 8;

    interface Px {

        /** The colour at a pixel, 0 for transparent; {@code f} is the animation frame. */
        int at(int x, int y, int f);
    }

    public static void main(String[] args) throws Exception {
        String fr = "blocks/frame/";
        still(fr + "base_top", NexusTextures::baseTop);
        still(fr + "base_side", NexusTextures::baseSide);
        still(fr + "base_lit_top", (x, y, f) -> litChannel(x, y, baseTop(x, y, f)));
        still(fr + "base_lit_side", NexusTextures::litSide);
        anim(fr + "base_lit_top_glow", NexusTextures::litGlow);
        still(fr + "pillar_top", NexusTextures::pillarTop);
        still(fr + "pillar_side", NexusTextures::pillarSide);
        still(fr + "conduit_top", NexusTextures::conduitTop);
        still(fr + "conduit_side", NexusTextures::conduitSide);
        anim(fr + "conduit_core", NexusTextures::conduitCore);
        still(fr + "ring_top", NexusTextures::ringTop);
        still(fr + "ring_side", NexusTextures::ringSide);
        anim(fr + "ring_side_glow", NexusTextures::ringGlow);
        still(fr + "seat_top", NexusTextures::seatTop);
        still(fr + "seat_side", NexusTextures::seatSide);
        anim(fr + "seat_crystal", NexusTextures::seatCrystal);
        still(fr + "foundation_top", NexusTextures::foundationTop);
        still(fr + "foundation_side", NexusTextures::foundationSide);
        anim(fr + "foundation_top_glow", NexusTextures::foundationGlow);
        still(fr + "shelf_top", NexusTextures::shelfTop);
        still(fr + "shelf_side", NexusTextures::shelfSide);
        anim(fr + "shelf_side_glow", NexusTextures::shelfGlow);
        still(fr + "console_top", NexusTextures::consoleTop);
        still(fr + "console_side", NexusTextures::consoleSide);
        anim(fr + "console_top_glow", NexusTextures::consoleTopGlow);
        anim(fr + "console_side_glow", NexusTextures::consoleSideGlow);
        // the cladding anchor (0.11.0): a hexagonal socket plate on every face
        still(fr + "anchor_top", NexusTextures::anchorFace);
        still(fr + "anchor_side", NexusTextures::anchorFace);
        anim(fr + "anchor_top_glow", NexusTextures::anchorGlow);
        anim(fr + "anchor_side_glow", NexusTextures::anchorGlow);

        still("blocks/nexus/front", (x, y, f) -> coreFront(x, y, f, false, CYAN, NexusTextures::hexEmblem));
        anim("blocks/nexus/front_on", (x, y, f) -> coreFront(x, y, f, true, CYAN, NexusTextures::hexEmblem));
        still("blocks/nexus/side", (x, y, f) -> coreSide(x, y, CYAN));
        still("blocks/nexus/top", (x, y, f) -> coreTop(x, y, CYAN));
        still("blocks/library/front", (x, y, f) -> coreFront(x, y, f, false, VIOLET, NexusTextures::bookEmblem));
        anim("blocks/library/front_on", (x, y, f) -> coreFront(x, y, f, true, VIOLET, NexusTextures::bookEmblem));
        still("blocks/library/side", (x, y, f) -> shelfSide(x, y, f));
        still("blocks/library/top", (x, y, f) -> coreTop(x, y, VIOLET));
        // the city's module cores (0.11.0): one folder a module, its own colour and emblem
        String eco = "blocks/module_core/eco_dome/";
        still(eco + "front", (x, y, f) -> coreFront(x, y, f, false, ECO_GREEN, NexusTextures::domeEmblem));
        anim(eco + "front_on", (x, y, f) -> coreFront(x, y, f, true, ECO_GREEN, NexusTextures::domeEmblem));
        still(eco + "side", (x, y, f) -> coreSide(x, y, ECO_GREEN));
        still(eco + "top", (x, y, f) -> coreTop(x, y, ECO_GREEN));
        // the Echo Dome Generator's (0.12.0): guard blue, a pod over six legs under the dome's arc
        String dome = "blocks/module_core/dome_generator/";
        still(dome + "front", (x, y, f) -> coreFront(x, y, f, false, GUARD_BLUE, NexusTextures::podEmblem));
        anim(dome + "front_on", (x, y, f) -> coreFront(x, y, f, true, GUARD_BLUE, NexusTextures::podEmblem));
        still(dome + "side", (x, y, f) -> coreSide(x, y, GUARD_BLUE));
        still(dome + "top", (x, y, f) -> coreTop(x, y, GUARD_BLUE));
        // the root's Tidal Well (0.12.0): tide blue, the shaft seen from above, the island in its pool
        String root = "blocks/module_core/root/";
        still(root + "front", (x, y, f) -> coreFront(x, y, f, false, TIDE_BLUE, NexusTextures::wellEmblem));
        anim(root + "front_on", (x, y, f) -> coreFront(x, y, f, true, TIDE_BLUE, NexusTextures::wellEmblem));
        still(root + "side", (x, y, f) -> coreSide(x, y, TIDE_BLUE));
        still(root + "top", (x, y, f) -> coreTop(x, y, TIDE_BLUE));
        // the Arcane Court's (0.13.0): violet, the octagonal hall under its steep spire, the finial's crystal lit
        String arcane = "blocks/module_core/arcane_court/";
        still(arcane + "front", (x, y, f) -> coreFront(x, y, f, false, ARCANE_VIOLET, NexusTextures::spireEmblem));
        anim(arcane + "front_on", (x, y, f) -> coreFront(x, y, f, true, ARCANE_VIOLET, NexusTextures::spireEmblem));
        still(arcane + "side", (x, y, f) -> coreSide(x, y, ARCANE_VIOLET));
        still(arcane + "top", (x, y, f) -> coreTop(x, y, ARCANE_VIOLET));
        // the Blood Covenant Altar's (0.13.0): red, three stepped tiers under the blood orb
        String blood = "blocks/module_core/blood_covenant/";
        still(blood + "front", (x, y, f) -> coreFront(x, y, f, false, BLOOD_RED, NexusTextures::altarEmblem));
        anim(blood + "front_on", (x, y, f) -> coreFront(x, y, f, true, BLOOD_RED, NexusTextures::altarEmblem));
        still(blood + "side", (x, y, f) -> coreSide(x, y, BLOOD_RED));
        still(blood + "top", (x, y, f) -> coreTop(x, y, BLOOD_RED));
        // the Echo Hunting Ground's (0.13.0): amber, the amphitheatre seen from above round its lit holo grid
        String hunting = "blocks/module_core/hunting_ground/";
        still(hunting + "front", (x, y, f) -> coreFront(x, y, f, false, HUNT_AMBER, NexusTextures::arenaEmblem));
        anim(hunting + "front_on", (x, y, f) -> coreFront(x, y, f, true, HUNT_AMBER, NexusTextures::arenaEmblem));
        still(hunting + "side", (x, y, f) -> coreSide(x, y, HUNT_AMBER));
        still(hunting + "top", (x, y, f) -> coreTop(x, y, HUNT_AMBER));
        // the Mana Fountain Array's (0.13.0): pale blue, the spout over the basin and the jets arcing into it
        String mana = "blocks/module_core/mana_fountain/";
        still(mana + "front", (x, y, f) -> coreFront(x, y, f, false, MANA_BLUE, NexusTextures::fountainEmblem));
        anim(mana + "front_on", (x, y, f) -> coreFront(x, y, f, true, MANA_BLUE, NexusTextures::fountainEmblem));
        still(mana + "side", (x, y, f) -> coreSide(x, y, MANA_BLUE));
        still(mana + "top", (x, y, f) -> coreTop(x, y, MANA_BLUE));
        // the Resonance Gate's (0.14.0): teal, two wing towers and the bridge between them, the crystal floating over it
        String gate = "blocks/module_core/resonance_gate/";
        still(gate + "front", (x, y, f) -> coreFront(x, y, f, false, GATE_TEAL, NexusTextures::gateEmblem));
        anim(gate + "front_on", (x, y, f) -> coreFront(x, y, f, true, GATE_TEAL, NexusTextures::gateEmblem));
        still(gate + "side", (x, y, f) -> coreSide(x, y, GATE_TEAL));
        still(gate + "top", (x, y, f) -> coreTop(x, y, GATE_TEAL));
        // the Dual-foil Vault's (0.14.0): parchment, the monolith under its sawtooth crown, the facade canvas lit
        String vault = "blocks/module_core/dual_foil_vault/";
        still(vault + "front", (x, y, f) -> coreFront(x, y, f, false, VAULT_PARCHMENT, NexusTextures::vaultEmblem));
        anim(vault + "front_on", (x, y, f) -> coreFront(x, y, f, true, VAULT_PARCHMENT, NexusTextures::vaultEmblem));
        still(vault + "side", (x, y, f) -> coreSide(x, y, VAULT_PARCHMENT));
        still(vault + "top", (x, y, f) -> coreTop(x, y, VAULT_PARCHMENT));

        still("items/flux_grit", NexusTextures::grit);
        still("items/flux_crystal", NexusTextures::fluxCrystal);
        still("items/echo_crystal", NexusTextures::echoCrystal);
        still("items/phase_alloy", NexusTextures::phaseAlloy);
        still("items/resonant_glass", NexusTextures::resonantGlass);
        still("items/library_card", (x, y, f) -> card(x, y, false));
        still("items/library_card_written", (x, y, f) -> card(x, y, true));
    }

    // ---- helpers

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

    /** A bright band running round the animation: 1 at its middle. */
    static double wave(double pos, int f) {
        double phase = f / (double) FRAMES;
        double d = Math.abs(((pos - phase) % 1 + 1.5) % 1 - 0.5);
        return Math.max(0, 1 - d * 5);
    }

    /** The bevelled rim every frame part has. */
    static int rim(int x, int y, int inside) {
        if (x == 0 || y == 0) return 0x3E4856;
        if (x == 15 || y == 15) return 0x0A0E13;
        return inside;
    }

    // ---- frame parts

    static int baseTop(int x, int y, int f) {
        int c = (x + y) % 2 == 0 ? 0x101C28 : 0x0E1924;
        // a hexagon grid
        int hx = x % 8, hy = y % 8;
        if (hx == 0 && hy >= 2 && hy <= 5 || (hy == 1 || hy == 6) && (hx == 1 || hx == 7)) c = 0x183042;
        if ((x == 1 || x == 14) && (y == 1 || y == 14)) c = 0x6B7686;
        return rim(x, y, c);
    }

    static int baseSide(int x, int y, int f) {
        int c = y < 8 ? 0x141E29 : 0x111A24;
        if (y == 7) c = 0x1D3A4D;
        if (y == 8) c = 0x0A1018;
        if ((x == 3 || x == 12) && (y == 3 || y == 12)) c = 0x5A6573;
        return rim(x, y, c);
    }

    /** The channel the lit base's light runs in. */
    static int litChannel(int x, int y, int c) {
        if ((x == 7 || x == 8) && y > 0 && y < 15 || (y == 7 || y == 8) && x > 0 && x < 15) return 0x0A2632;
        if ((x == 6 || x == 9) && y > 0 && y < 15 || (y == 6 || y == 9) && x > 0 && x < 15) return 0x16303E;
        return c;
    }

    static int litGlow(int x, int y, int f) {
        boolean line = (x == 7 || x == 8) && y > 0 && y < 15 || (y == 7 || y == 8) && x > 0 && x < 15;
        if (!line) return 0;
        double pos = (x == 7 || x == 8) ? (y < 8 ? (7.5 - y) / 7.5 : (y - 7.5) / 7.5)
            : (x < 8 ? (7.5 - x) / 7.5 : (x - 7.5) / 7.5);
        double w = wave(1 - pos, f);
        return mix(CYAN, WHITE, w);
    }

    static int litSide(int x, int y, int f) {
        int c = baseSide(x, y, f);
        if (y == 7 && x > 0 && x < 15) c = 0x2FB5D0;
        return c;
    }

    static int pillarTop(int x, int y, int f) {
        double d = dist(x, y);
        if (d < 2) return 0x3A4452;
        if (d < 3.2) return CYAN_DARK;
        if (d < 5) return STEEL;
        return rim(x, y, STEEL_DARK);
    }

    static int pillarSide(int x, int y, int f) {
        // a steel column, lit from the left
        double t = (x - 5) / 6.0;
        int c = mix(STEEL_LIGHT, STEEL_DARK, t);
        if (x == 7 || x == 9) c = mix(c, 0x1A1F26, 0.5);
        if (y <= 1 || y >= 14) c = 0x3A4452;
        if ((y == 2 || y == 13) && x >= 5 && x <= 10) c = CYAN_DARK;
        return c;
    }

    /** The console stand's plate: dark glass in a steel rim, a groove for the ring of light. */
    static int consoleTop(int x, int y, int f) {
        double d = dist(x, y);
        if (d < 4.2) return mix(0x16283A, DEEPER, d / 4.2);
        if (d < 5.6) return 0x0A1A24;
        return rim(x, y, d < 7 ? STEEL : STEEL_DARK);
    }

    /** The console's column and foot: steel, a channel up the middle where the light runs. */
    static int consoleSide(int x, int y, int f) {
        if (y >= 14) return x % 3 == 0 ? STEEL_LIGHT : STEEL;
        if (x == 7 || x == 8) return 0x0A2632;
        if (x == 6 || x == 9) return STEEL_DARK;
        int c = mix(STEEL_LIGHT, STEEL_DARK, Math.abs(x - 7.5) / 7.5);
        return (y == 4 || y == 10) ? mix(c, CYAN_DARK, 0.4) : c;
    }

    static int consoleTopGlow(int x, int y, int f) {
        double d = dist(x, y);
        if (d < 4.4 || d >= 5.4) return 0;
        double a = (Math.atan2(y - 7.5, x - 7.5) / (Math.PI * 2) + 1) % 1;
        return mix(CYAN, WHITE, wave(a, f));
    }

    static int consoleSideGlow(int x, int y, int f) {
        if (x != 7 && x != 8 || y >= 14) return 0;
        return mix(CYAN, WHITE, wave((15 - y) / 16.0, f));
    }

    static int conduitTop(int x, int y, int f) {
        double d = dist(x, y);
        if (d < 1.6) return WHITE;
        if (d < 2.6) return CYAN;
        if (d < 4.6) return STEEL;
        return STEEL_DARK;
    }

    static int conduitSide(int x, int y, int f) {
        // used across x 4..11: posts at the edges, open windows between
        if (y <= 1 || y >= 14) return x % 2 == 0 ? STEEL : STEEL_LIGHT;
        if (x <= 5 || x >= 10) return mix(STEEL_LIGHT, STEEL_DARK, (x <= 5 ? x - 4 : 11 - x) / 2.0);
        if (y == 5 || y == 10) return STEEL_DARK;
        return 0;
    }

    static int conduitCore(int x, int y, int f) {
        double w = wave((15 - y) / 16.0, f);
        int c = mix(CYAN, WHITE, 0.25 + 0.75 * w);
        if (x <= 5 || x >= 10) c = mix(c, CYAN_DARK, 0.5);
        return c;
    }

    static int ringTop(int x, int y, int f) {
        int c = (y % 4 == 0) ? 0x2A3442 : 0x1D2530;
        if (x == 0 || x == 15) c = 0x3E4856;
        return c;
    }

    static int ringSide(int x, int y, int f) {
        if (y <= 5 || y >= 11) return 0x2A3442;
        return y == 7 || y == 8 ? 0x0E3240 : 0x1A2230;
    }

    static int ringGlow(int x, int y, int f) {
        if (y != 7 && y != 8) return 0;
        double w = wave(x / 16.0, f);
        return mix(CYAN, WHITE, w);
    }

    static int seatTop(int x, int y, int f) {
        double d = dist(x, y);
        if (d < 3.4) return 0x070B10;
        if (d < 4.4) return CYAN_DARK;
        if (d < 5.2) return STEEL_LIGHT;
        return rim(x, y, (x + y) % 2 == 0 ? 0x2A3442 : 0x25303D);
    }

    static int seatSide(int x, int y, int f) {
        // the lower half shows
        if (y < 8) return 0x2A3442;
        if (y == 8) return STEEL_LIGHT;
        if (y == 15) return 0x0A0E13;
        if (y == 11) return (x % 3 == 0) ? CYAN : 0x0E3240;
        return 0x222B37;
    }

    static int seatCrystal(int x, int y, int f) {
        double facet = ((x + y) % 4) / 4.0;
        double w = wave((x + 15 - y) / 30.0, f);
        int c = mix(mix(VIOLET, CYAN, facet), WHITE, 0.15 + 0.6 * w);
        if ((x + y) % 5 == 0) c = mix(c, WHITE, 0.4);
        return c;
    }

    static int foundationTop(int x, int y, int f) {
        int c = (x + y) % 2 == 0 ? 0x16112A : 0x130F24;
        double d = dist(x, y);
        if (d > 5.6 && d < 6.6) c = 0x2A1E52;
        if (d < 2.2) c = 0x1E1640;
        return rim(x, y, c);
    }

    static int foundationGlow(int x, int y, int f) {
        double d = dist(x, y);
        if (d <= 5.3 || d >= 6.6) return 0;
        double ang = (Math.atan2(y - 7.5, x - 7.5) + Math.PI) / (2 * Math.PI);
        return mix(VIOLET, WHITE, wave(ang, f) * 0.8);
    }

    static int foundationSide(int x, int y, int f) {
        int c = y < 8 ? 0x17122C : 0x130F24;
        if (y == 7) c = 0x3C2470;
        return rim(x, y, c);
    }

    static int shelfTop(int x, int y, int f) {
        int c = 0x161228;
        if (x % 5 == 0 || y % 5 == 0) c = 0x241C44;
        return rim(x, y, c);
    }

    /** Two rows of books in a dark frame; the spines' colours vary. */
    static int shelfSide(int x, int y, int f) {
        if (x == 0 || x == 15 || y == 0 || y == 15 || y == 7 || y == 8) return y == 8 ? 0x0A0812 : 0x2A2240;
        int row = y < 8 ? 0 : 1;
        int spine = (x + row * 3) / 2;
        double h = hash(spine, row, 1);
        int[] cols = { 0x2B1F4A, 0x1C2B45, 0x3A2A1C, 0x23203A, 0x162A30 };
        int c = cols[(int) (h * cols.length)];
        if (x % 2 == 0) c = mix(c, 0x000000, 0.35);
        int top = row == 0 ? 1 + (int) (h * 2) : 9 + (int) (h * 2);
        if (y < top) return 0x0C0A16;
        return c;
    }

    static int shelfGlow(int x, int y, int f) {
        if (x == 0 || x == 15 || y == 0 || y == 15 || y == 7 || y == 8) return 0;
        int row = y < 8 ? 0 : 1;
        int spine = (x + row * 3) / 2;
        double h = hash(spine, row, 1);
        int top = row == 0 ? 1 + (int) (h * 2) : 9 + (int) (h * 2);
        if (y < top || x % 2 == 0) return 0;
        // a glyph on some spines, glowing in turn
        double g = hash(spine, row, 7);
        if (g < 0.45) return 0;
        int mid = row == 0 ? 4 : 12;
        if (Math.abs(y - mid) > 1) return 0;
        double w = 0.4 + 0.6 * Math.max(0, Math.sin((f / (double) FRAMES + g) * Math.PI * 2));
        int col = g < 0.7 ? VIOLET : g < 0.88 ? CYAN : GOLD;
        return mix(0x000000, col, w);
    }

    // ---- the cladding anchor (0.11.0)

    /** The hexagon distance of a pixel from the face's middle (pointy sides left and right, as the emblems are). */
    static double hexDist(int x, int y) {
        double dx = Math.abs(x - 7.5), dy = Math.abs(y - 7.5);
        return Math.max(dx * 0.866 + dy * 0.5, dy);
    }

    /**
     * A face of the cladding anchor: the frame's bevelled rim round a dark field, and on it a hexagonal steel plate,
     * lit from the upper left, with a bolt at each corner, a channel round it where the light runs, and a recessed
     * socket in its middle (a stud at its bottom) where a cladding will lock in later.
     */
    static int anchorFace(int x, int y, int f) {
        int c = (x + y) % 2 == 0 ? 0x101C28 : 0x0E1924;
        double h = hexDist(x, y);
        if (h < 6.4) {
            double lit = ((7.5 - x) + (7.5 - y)) / 15.0;
            c = mix(STEEL_DARK, STEEL, 0.45 + 0.4 * lit);
            if (h >= 5.6) c = mix(c, 0x000000, 0.35);
            if (h >= 4.0 && h < 5.0) c = 0x0A2632;
            if (h < 3.4) c = h < 1.3 ? STEEL_LIGHT : h < 2.0 ? 0x1A2230 : DEEPER;
        }
        // the bolts at the plate's six corners
        double ang = Math.atan2(y - 7.5, x - 7.5), r = Math.hypot(x - 7.5, y - 7.5);
        double off = Math.abs(((ang / (Math.PI / 3)) % 1 + 1.5) % 1 - 0.5);
        if (r > 5.0 && r < 6.2 && off < 0.12) c = STEEL_LIGHT;
        return rim(x, y, c);
    }

    /** The anchor's light: the channel round its plate, a band of light running round it. */
    static int anchorGlow(int x, int y, int f) {
        double h = hexDist(x, y);
        if (h < 4.0 || h >= 5.0) return 0;
        double a = (Math.atan2(y - 7.5, x - 7.5) / (Math.PI * 2) + 1) % 1;
        return mix(CYAN, WHITE, 0.15 + 0.85 * wave(a, f));
    }

    // ---- cores

    interface Emblem {

        /** 0 none, 1 frame, 2 lit. */
        int at(int x, int y);
    }

    static int hexEmblem(int x, int y) {
        double dx = Math.abs(x - 7.5), dy = Math.abs(y - 7.5);
        double hex = Math.max(dx * 0.866 + dy * 0.5, dy);
        if (hex < 2.2) return 2;
        if (hex >= 4 && hex < 5) return 1;
        if ((x == 7 || x == 8) && dy < 4 || (y == 7 || y == 8) && dx < 4) return 2;
        return 0;
    }

    static int bookEmblem(int x, int y) {
        if (x < 3 || x > 12 || y < 4 || y > 12) return 0;
        if (x == 7 || x == 8) return 2;
        if (y == 4 || y == 12) return 1;
        if ((y - 5) % 2 == 0 && (x <= 5 || x >= 10)) return 2;
        if (x == 3 || x == 12) return 1;
        return 0;
    }

    /** The Eco Dome's emblem: a dome on its base line over a tree, the tree lit. */
    static int domeEmblem(int x, int y) {
        double d = Math.hypot(x - 7.5, y - 11);
        if (y <= 10 && d >= 4.8 && d < 5.8) return 1;
        if (y == 11 && x >= 2 && x <= 13) return 1;
        if (Math.hypot(x - 7.5, y - 8.2) < 1.7) return 2;
        if ((x == 7 || x == 8) && y >= 9 && y <= 10) return 2;
        return 0;
    }

    /**
     * The Echo Dome Generator's emblem (0.12.0): the dome's arc over a saucer pod (lit) on two splayed legs, the lift
     * shaft a lit point under the pod.
     */
    static int podEmblem(int x, int y) {
        double d = Math.hypot(x - 7.5, y - 13);
        if (y <= 8 && d >= 9.2 && d < 10.2) return 1;
        if (y == 7 && x >= 4 && x <= 11 || y == 8 && x >= 3 && x <= 12) return 2;
        if (y == 6 && x >= 6 && x <= 9) return 2;
        int leg = y - 8;
        if (leg >= 1 && leg <= 5 && (x == 4 - leg / 2 || x == 11 + leg / 2)) return 1;
        if ((x == 7 || x == 8) && y >= 11 && y <= 12) return 2;
        return 0;
    }

    /**
     * The Tidal Well's emblem (0.12.0): the shaft's octagon seen from above, a tide ring round the island and its lit
     * pad in the middle.
     */
    static int wellEmblem(int x, int y) {
        double dx = Math.abs(x - 7.5), dy = Math.abs(y - 7.5);
        double oct = Math.max(Math.max(dx, dy), (dx + dy) * 0.75);
        if (oct >= 5 && oct < 6) return 1;
        if (oct >= 2.6 && oct < 3.5) return 2;
        if (oct < 1.2) return 2;
        return 0;
    }

    /**
     * The Arcane Court's emblem (0.13.0): a steep spire (its two edges) over the hall's walls and cornice, the slit
     * windows and the finial's crystal lit.
     */
    static int spireEmblem(int x, int y) {
        double dx = Math.abs(x - 7.5);
        if (y <= 2) return dx < 1 ? 2 : 0;
        if (y <= 8) {
            double half = (y - 2) * 0.62;
            return dx >= half - 0.5 && dx < half + 0.5 ? 1 : 0;
        }
        if (y == 9 || y == 13) return x >= 3 && x <= 12 ? 1 : 0;
        if (y > 13) return 0;
        if (x == 3 || x == 12) return 1;
        return (x == 5 || x == 7 || x == 8 || x == 10) && y >= 10 && y <= 12 ? 2 : 0;
    }

    /**
     * The Blood Covenant Altar's emblem (0.13.0): three stepped tiers, the top one open in the oculus, and the blood orb
     * over it with its light falling in, lit.
     */
    static int altarEmblem(int x, int y) {
        if (Math.hypot(x - 7.5, y - 3.5) < 1.8) return 2;
        if ((x == 7 || x == 8) && y >= 5 && y <= 6) return 2;
        if (y == 13 && x >= 2 && x <= 13) return 1;
        if (y == 10 && x >= 2 && x <= 13 || y >= 10 && y <= 13 && (x == 2 || x == 13)) return 1;
        if (y == 8 && x >= 4 && x <= 11 || y >= 8 && y <= 9 && (x == 4 || x == 11)) return 1;
        if (y >= 6 && y <= 7 && (x == 6 || x == 9)) return 1;
        return 0;
    }

    /**
     * The Echo Hunting Ground's emblem (0.13.0): the amphitheatre seen from above, its outer wall, the terraces as a
     * stepped ring and the arena's holo grid lit in the middle.
     */
    static int arenaEmblem(int x, int y) {
        double dx = Math.abs(x - 7.5), dy = Math.abs(y - 7.5);
        double oct = Math.max(Math.max(dx, dy), (dx + dy) * 0.75);
        if (oct >= 5.2 && oct < 6.2) return 1;
        if (oct >= 3.8 && oct < 4.6 && (x + y) % 2 == 0) return 1;
        if (oct < 2.8) return (x + y) % 2 == 0 ? 2 : 0;
        return 0;
    }

    /**
     * The Mana Fountain Array's emblem (0.13.0): the obelisk's spout rising from the basin, two jets of light arcing in
     * to it from the fountains either side, lit; the basin's line and the fountains' rims under them.
     */
    static int fountainEmblem(int x, int y) {
        if (y == 12 && x >= 2 && x <= 13) return 1;
        if (y == 11 && (x <= 4 || x >= 11) && x >= 2 && x <= 13) return 1;
        if ((x == 7 || x == 8) && y >= 3 && y <= 11) return 2;
        double dx = Math.abs(x - 7.5);
        if (dx >= 1.5 && dx <= 5.5 && Math.abs(y - (3.5 + dx * dx * 0.3)) < 0.6) return 2;
        return 0;
    }

    /**
     * The Resonance Gate's emblem (0.14.0): two wing towers with lit windows either side of the open passage, the bridge
     * across between them, and the crystal spire floating over the bridge, lit.
     */
    static int gateEmblem(int x, int y) {
        double dx = Math.abs(x - 7.5), dy = Math.abs(y - 3.5);
        if (y <= 5 && dx / 2.6 + dy / 2.4 <= 1) return 2;
        boolean tower = (x >= 2 && x <= 4 || x >= 11 && x <= 13) && y >= 5 && y <= 12;
        if (tower && (x == 3 || x == 12) && (y == 7 || y == 10)) return 2;
        if (tower) return 1;
        if (x >= 5 && x <= 10 && (y == 7 || y == 8)) return 1;
        return 0;
    }

    /**
     * The Dual-foil Vault's emblem (0.14.0): the windowless monolith's outline under its sawtooth crown of three teeth,
     * the facade canvas on its front in a lit frame, the doors a gap in its foot.
     */
    static int vaultEmblem(int x, int y) {
        if (x < 3 || x > 12 || y < 2 || y > 13) return 0;
        if (y <= 3) {
            int t = (x - 3) % 3;
            return x >= 4 && x <= 11 && (y == 3 ? t != 0 : t == 2) ? 1 : 0;
        }
        if (x >= 5 && x <= 10 && y >= 5 && y <= 10) return x == 5 || x == 10 || y == 5 || y == 10 ? 2 : 1;
        if (y == 13 && x >= 6 && x <= 9) return 0;
        return x == 3 || x == 12 || y == 4 || y == 13 ? 1 : 0;
    }

    static int coreFront(int x, int y, int f, boolean on, int accent, Emblem e) {
        int base = coreSide(x, y, accent);
        if (x < 2 || x > 13 || y < 2 || y > 13) return base;
        int c = 0x080E15;
        int m = e.at(x, y);
        if (m == 1) c = on ? mix(accent, 0x000000, 0.4) : 0x2A3442;
        if (m == 2) c = on ? mix(accent, WHITE, 0.25 + 0.6 * wave(dist(x, y) / 8, f)) : mix(accent, 0x000000, 0.7);
        return c;
    }

    static int coreSide(int x, int y, int accent) {
        int c = ((x * 13 + y * 7) % 5 == 0) ? 0x1F2630 : 0x1B212A;
        if (x == 0 || y == 0) c = 0x46525F;
        else if (x == 15 || y == 15) c = 0x0B0E13;
        else if (x == 1 || y == 1) c = 0x2C3540;
        if ((x == 3 || x == 12) && y >= 3 && y <= 12) c = mix(accent, 0x000000, 0.55);
        if ((x == 2 || x == 13) && (y == 2 || y == 13)) c = 0x6B7686;
        return c;
    }

    static int coreTop(int x, int y, int accent) {
        int c = coreSide(x, y, accent);
        double d = dist(x, y);
        if (d > 3.5 && d < 4.6) c = mix(accent, 0x000000, 0.45);
        if (d < 1.6) c = mix(accent, WHITE, 0.3);
        return c;
    }

    // ---- items

    static int grit(int x, int y, int f) {
        // a heap, wider at the bottom
        double cx = 7.5, top = 6;
        if (y < top) return 0;
        double half = (y - top) * 0.9 + 1.2;
        if (Math.abs(x - cx) > half || y > 13) return 0;
        double h = hash(x, y, 3);
        int c = mix(0x4A5A6E, 0x2A3442, h);
        if (h > 0.86) c = CYAN;
        if (h > 0.95) c = WHITE;
        if (y == 13) c = mix(c, 0x000000, 0.4);
        return c;
    }

    static int crystalShape(int x, int y, double cx, double w, int top, int bottom) {
        if (y < top || y > bottom) return -1;
        double mid = (top + bottom) / 2.0;
        double half = y < mid ? w * (y - top + 1) / (mid - top + 1) : w * (bottom - y + 1) / (bottom - mid + 1);
        double d = x - cx;
        if (Math.abs(d) > half) return -1;
        return d < 0 ? 0 : 1;
    }

    static int fluxCrystal(int x, int y, int f) {
        int[][] parts = { { 0, 4, 13, 7 }, { 0, 2, 9, 5 }, { 0, 6, 14, 10 } };
        double[] cxs = { 7.5, 4.5, 11 };
        double[] ws = { 3, 2, 2.2 };
        int[] tops = { 1, 5, 6 };
        int[] bots = { 14, 13, 14 };
        for (int i = 0; i < 3; i++) {
            int side = crystalShape(x, y, cxs[i], ws[i], tops[i], bots[i]);
            if (side < 0) continue;
            int c = side == 0 ? 0x7FF0FF : 0x2FB5D0;
            if (y < tops[i] + 3) c = mix(c, WHITE, 0.4);
            if (Math.abs(x - cxs[i]) < 0.6) c = mix(c, WHITE, 0.5);
            return c;
        }
        return 0;
    }

    static int echoCrystal(int x, int y, int f) {
        int side = crystalShape(x, y, 7.5, 5.5, 0, 15);
        if (side < 0) return 0;
        double d = dist(x, y);
        int c = side == 0 ? 0xB08CFF : 0x6A3CC4;
        if (d < 4.2) c = mix(0x1A0E38, c, d / 4.2 * 0.6);
        if (y < 3) c = mix(c, WHITE, 0.45);
        if (Math.abs(x - 7.5) < 0.6 && d >= 4.2) c = mix(c, WHITE, 0.4);
        return c;
    }

    /**
     * Phase alloy (0.12.0, BLUEPRINT 3.6): an ingot of deep blue metal, its top lit, the flux layer's phase running
     * across its face as two thin cyan waves.
     */
    static int phaseAlloy(int x, int y, int f) {
        // the top face, a slanted strip widening towards the front; the front face under it; the shade under that
        boolean top = y >= 4 && y <= 6 && x >= 5 - (y - 4) && x <= 12 + (y - 4) - 1;
        boolean front = y >= 7 && y <= 11 && x >= 2 && x <= 14 - (y == 11 ? 1 : 0);
        if (!top && !front) return 0;
        if (top) {
            int c = mix(0x5C82B8, 0x87A9D8, (x - 2) / 12.0);
            if (y == 4) c = mix(c, WHITE, 0.25);
            if (x == 5 - (y - 4)) c = 0x34557F;
            return c;
        }
        int c = mix(0x2B4C7E, 0x1A2F52, (y - 7) / 4.0);
        if (x == 2 || y == 11) c = 0x0E1A2C;
        if (x == 14 - (y == 11 ? 1 : 0)) c = 0x13223A;
        if (y == 7) c = mix(c, 0x4F7FB8, 0.6);
        // two phase waves across the face
        double w1 = 8.6 + 1.1 * Math.sin(x * 0.85), w2 = 9.8 + 0.9 * Math.sin(x * 0.85 + 2.2);
        if (x > 2 && y < 11 && Math.abs(y - w1) < 0.5) c = CYAN;
        else if (x > 2 && y < 11 && Math.abs(y - w2) < 0.5) c = CYAN_DARK;
        return c;
    }

    /**
     * Resonant glass (0.12.0, BLUEPRINT 3.6): a clear pane in a steel frame, a cyan ripple on its glass and a white
     * glint in one corner; the glass itself is left open.
     */
    static int resonantGlass(int x, int y, int f) {
        if (x < 1 || x > 14 || y < 1 || y > 14) return 0;
        if (x == 1 || x == 14 || y == 1 || y == 14) {
            if ((x == 1 || x == 14) && (y == 1 || y == 14)) return CYAN;
            return x == 1 || y == 1 ? 0x3B6A8A : 0x1C3446;
        }
        // the ripple: two arcs round the pane's lower right
        double d = Math.hypot(x - 10.5, y - 10.5);
        if (d >= 3.6 && d < 4.4 || d >= 6.2 && d < 6.9) return d < 5 ? 0x7FE8F8 : 0x4FB8D0;
        // the glint
        if (x + y == 6 || x + y == 7 && x >= 3 && x <= 4) return WHITE;
        if (x == 2 || y == 2) return 0xA8E8F4;
        return 0;
    }

    static int card(int x, int y, boolean written) {
        if (x < 1 || x > 14 || y < 3 || y > 12) return 0;
        boolean edge = x == 1 || x == 14 || y == 3 || y == 12;
        if (edge) return written ? VIOLET : 0x8C82A8;
        int c = 0xE6E1F2;
        if (x == 2 || y == 4) c = 0xF6F3FF;
        if (written) {
            if ((y == 6 || y == 8 || y == 10) && x >= 4 && x <= (y == 10 ? 9 : 12)) c = 0x5A4A8A;
            if (x >= 10 && x <= 12 && y >= 8 && y <= 10) c = GOLD;
        } else if (y == 6 && x >= 4 && x <= 6) c = 0xB8B0D0;
        return c;
    }

    // ---- writing

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
