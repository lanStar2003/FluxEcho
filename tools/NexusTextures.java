import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the textures of the Flux Nexus era (0.9.0): the flux frame's parts, the nexus's and the library's cores, the
 * flux materials and the library's index cards; (0.11.0) the frame's cladding anchor and the city's module cores;
 * (0.12.0) the phase's materials, phase alloy and resonant glass; (0.13.0) the four pavilions' module cores; and
 * (0.15.0) phase III's material, memory filament, and the phase alloy wire it is coated from; and (0.16.0) phase IV's
 * materials, manifest matter and the anti-gravity lattice, and the frame's floating foundation.
 * Run from the repository root: {@code java tools/NexusTextures.java}.
 * The parts that glow come as separate overlays ({@code *_glow}), drawn at full brightness by the frame's renderer.
 * <p>
 * 0.17.0 (the redesign's seven-row recolouring, style §7.1 rows 1 and 2): the flux frame is the hull's pearl white,
 * the bones of the nexus and of every building already standing, its IDs, metas and light levels untouched; its light
 * is a white core edged in the nexus cyan, the conduit's core and the seat's crystal keep their colours, and the echo
 * shelf's books keep theirs inside a white frame. The cores, the materials and the cards are not among the seven rows
 * and keep their 0.16 colours.
 */
public class NexusTextures {

    /**
     * Where the textures go: the mod's assets, or (0.17.0) the folder given as the first argument, so a test can draw
     * them aside and compare them with the committed ones.
     */
    static String root = "src/main/resources/assets/fluxecho/textures/";

    static final int DEEP = 0x0A1622, DEEPER = 0x060D15, PANE = 0x0F2231, SEAM = 0x1F3C4E, CYAN = 0x4FE3FF,
        CYAN_DARK = 0x1C6F84, VIOLET = 0x8A5CFF, VIOLET_DARK = 0x3C2470, GOLD = 0xFFD27A, WHITE = 0xE8FBFF;
    /**
     * The hull (0.17.0, style §3.5 M1 and §7.1 rows 1 and 2): the frame's pearl white, its seam and rivets, and the lit
     * and shaded tones its bevels and columns are drawn in.
     */
    static final int HULL = 0xEDF1F3, HULL_SEAM = 0xC3CCD3, HULL_RIVET = 0xD5DCE1, HULL_LIT = 0xF8FAFB,
        HULL_SHADE = 0xDCE2E6, HULL_DEEP = 0xB2BCC4;
    /** The white core of every glowing line (as {@code com.fluxecho.logic.skin.SkinPalette.GLOW_CORE}). */
    static final int GLOW_CORE = 0xFFFFFF;
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
    /** The Sophon Array's accent (0.15.0), as {@code TileSophonArray.COLOR}. */
    static final int SOPHON_PINK = 0xFF8CE6;
    /** The Holo Sand Table's accent (0.15.0), as {@code TileHoloTable.COLOR}. */
    static final int HOLO_CYAN = 0x66F0FF;
    /** The sky ring's accents (0.16.0), as {@code TileWeatherStation.COLOR} and the others' (CITY.md §1.1). */
    static final int WEATHER_BLUE = 0x9AD0FF, SAILS_GOLD = 0xFFD27A, OBSERVATORY_LILAC = 0xB9C8FF,
        HANGAR_CHROME = 0xD8E4F0;
    /** The anti-gravity lattice's lifting light (0.16.0): a pale sky blue, on its item and the floating foundation. */
    static final int LIFT = 0x9FD8FF;
    static final int FRAMES = 8;

    interface Px {

        /** The colour at a pixel, 0 for transparent; {@code f} is the animation frame. */
        int at(int x, int y, int f);
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) root = args[0];
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
        // the floating foundation (0.16.0): a lattice deck on top, a truss on the sides, an emitter plate underneath
        // and the lifting light on it, rings of light running out from its middle
        still(fr + "float_top", NexusTextures::floatTop);
        still(fr + "float_side", NexusTextures::floatSide);
        still(fr + "float_bottom", NexusTextures::floatBottom);
        anim(fr + "float_bottom_glow", NexusTextures::floatBottomGlow);
        anim(fr + "float_side_glow", NexusTextures::floatSideGlow);

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
        // the Sophon Array's (0.15.0): pink, the lattice sphere on its struts over the podium, the light point lit
        String sophon = "blocks/module_core/sophon_array/";
        still(sophon + "front", (x, y, f) -> coreFront(x, y, f, false, SOPHON_PINK, NexusTextures::sphereEmblem));
        anim(sophon + "front_on", (x, y, f) -> coreFront(x, y, f, true, SOPHON_PINK, NexusTextures::sphereEmblem));
        still(sophon + "side", (x, y, f) -> coreSide(x, y, SOPHON_PINK));
        still(sophon + "top", (x, y, f) -> coreTop(x, y, SOPHON_PINK));
        // the Holo Sand Table's (0.15.0): cyan, the rotunda's shallow dome over the ring light and the hologram on the
        // table, lit
        String holo = "blocks/module_core/holo_table/";
        still(holo + "front", (x, y, f) -> coreFront(x, y, f, false, HOLO_CYAN, NexusTextures::tableEmblem));
        anim(holo + "front_on", (x, y, f) -> coreFront(x, y, f, true, HOLO_CYAN, NexusTextures::tableEmblem));
        still(holo + "side", (x, y, f) -> coreSide(x, y, HOLO_CYAN));
        still(holo + "top", (x, y, f) -> coreTop(x, y, HOLO_CYAN));
        // the sky ring's four (0.16.0), each in its accent: the weather mast under its cloud, the sails between their
        // masts, the observatory's slit dome, a droplet over its cradle
        core("weather_station", WEATHER_BLUE, NexusTextures::weatherEmblem);
        core("sky_sails", SAILS_GOLD, NexusTextures::sailsEmblem);
        core("observatory", OBSERVATORY_LILAC, NexusTextures::observatoryEmblem);
        core("droplet_hangar", HANGAR_CHROME, NexusTextures::dropletEmblem);

        still("items/flux_grit", NexusTextures::grit);
        still("items/flux_crystal", NexusTextures::fluxCrystal);
        still("items/echo_crystal", NexusTextures::echoCrystal);
        still("items/phase_alloy", NexusTextures::phaseAlloy);
        still("items/resonant_glass", NexusTextures::resonantGlass);
        still("items/phase_alloy_wire", NexusTextures::phaseAlloyWire);
        anim("items/memory_filament", NexusTextures::memoryFilament);
        anim("items/manifest_matter", NexusTextures::manifestMatter);
        anim("items/anti_gravity_lattice", NexusTextures::antiGravityLattice);
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

    // ---- frame parts (0.17.0: the hull's pearl white, its light a white core edged in the nexus cyan, style §7.1
    // rows 1 and 2; the conduit's core and the seat's crystal keep their cyan)

    /** The hull's bevelled rim: its lit edge on the top and left, its seam on the bottom and right. */
    static int hullRim(int x, int y, int inside) {
        if (x == 0 || y == 0) return HULL_LIT;
        if (x == 15 || y == 15) return HULL_SEAM;
        return inside;
    }

    /** A barely visible grain so flat areas do not look painted; {@code amount} is the largest shift. */
    static int grain(int c, int x, int y, int seed, double amount) {
        double h = hash(x, y, seed) - 0.5;
        return h < 0 ? mix(c, 0x000000, -h * 2 * amount) : mix(c, 0xFFFFFF, h * 2 * amount);
    }

    /**
     * A pixel of a glowing line (style §3.6): {@code d} pixels out from its core, the white core at 0 (white through
     * the animation), the cyan edge at 1, lifted towards white as the band {@code w} passes, and nothing further out.
     */
    static int glow(int d, double w) {
        if (d == 0) return GLOW_CORE;
        if (d == 1) return mix(CYAN, WHITE, 0.4 * w);
        return 0;
    }

    /** The base: a hull plate with a faint hexagon grid and a rivet near each corner. */
    static int baseTop(int x, int y, int f) {
        int c = grain(HULL, x, y, 3, 0.015);
        // a hexagon grid
        int hx = x % 8, hy = y % 8;
        if (hx == 0 && hy >= 2 && hy <= 5 || (hy == 1 || hy == 6) && (hx == 1 || hx == 7)) c = HULL_RIVET;
        if ((x == 1 || x == 14) && (y == 1 || y == 14)) c = HULL_SEAM;
        return hullRim(x, y, c);
    }

    static int baseSide(int x, int y, int f) {
        int c = y < 8 ? HULL : HULL_SHADE;
        if (y == 7) c = HULL_RIVET;
        if (y == 8) c = HULL_SEAM;
        if ((x == 3 || x == 12) && (y == 3 || y == 12)) c = HULL_SEAM;
        return hullRim(x, y, c);
    }

    /** The channel the lit base's light runs in. */
    static int litChannel(int x, int y, int c) {
        if ((x >= 6 && x <= 9) && y > 0 && y < 15 || (y >= 6 && y <= 9) && x > 0 && x < 15) return HULL_SEAM;
        return c;
    }

    /** The lit base's cross: a white core two pixels wide, a cyan edge either side, a band running out from the hub. */
    static int litGlow(int x, int y, int f) {
        if (x < 1 || x > 14 || y < 1 || y > 14) return 0;
        boolean coreX = x == 7 || x == 8, coreY = y == 7 || y == 8;
        if (coreX || coreY) return GLOW_CORE;
        boolean edgeX = x == 6 || x == 9, edgeY = y == 6 || y == 9;
        if (!edgeX && !edgeY) return 0;
        double pos = edgeX ? (y < 8 ? (7.5 - y) / 7.5 : (y - 7.5) / 7.5) : (x < 8 ? (7.5 - x) / 7.5 : (x - 7.5) / 7.5);
        return glow(1, wave(1 - pos, f));
    }

    static int litSide(int x, int y, int f) {
        int c = baseSide(x, y, f);
        if (y == 7 && x > 0 && x < 15) c = mix(CYAN, HULL, 0.35);
        return c;
    }

    /** A rib's post seen from above: a hull cap with a seam ring. */
    static int pillarTop(int x, int y, int f) {
        double d = dist(x, y);
        if (d < 2) return HULL_LIT;
        if (d < 3.2) return HULL_SEAM;
        if (d < 5) return HULL;
        return hullRim(x, y, HULL_SHADE);
    }

    /** A white rib's post (style §4.1), lit from the left, a band near its head and its foot. */
    static int pillarSide(int x, int y, int f) {
        double t = (x - 5) / 6.0;
        int c = mix(HULL_LIT, HULL_DEEP, t);
        if (x == 7 || x == 9) c = mix(c, HULL_SEAM, 0.4);
        if (y <= 1 || y >= 14) c = HULL_SEAM;
        if ((y == 2 || y == 13) && x >= 5 && x <= 10) c = HULL_RIVET;
        return c;
    }

    /** The console stand's plate: dark glass in a hull rim, a groove for the ring of light. */
    static int consoleTop(int x, int y, int f) {
        double d = dist(x, y);
        if (d < 4.2) return mix(0x16283A, DEEPER, d / 4.2);
        if (d < 5.6) return HULL_SEAM;
        return hullRim(x, y, d < 7 ? HULL : HULL_SHADE);
    }

    /** The console's column and foot: hull white, a channel up the middle where the light runs. */
    static int consoleSide(int x, int y, int f) {
        if (y >= 14) return x % 3 == 0 ? HULL_RIVET : HULL;
        if (x >= 6 && x <= 9) return HULL_SEAM;
        int c = mix(HULL_LIT, HULL_DEEP, Math.abs(x - 7.5) / 7.5);
        return (y == 4 || y == 10) ? mix(c, HULL_SEAM, 0.6) : c;
    }

    /** The plate's ring: a white core with a cyan edge either side, a band running round it. */
    static int consoleTopGlow(int x, int y, int f) {
        double d = dist(x, y);
        if (d < 4.2 || d >= 5.6) return 0;
        double a = (Math.atan2(y - 7.5, x - 7.5) / (Math.PI * 2) + 1) % 1;
        return glow(d >= 4.6 && d < 5.2 ? 0 : 1, wave(a, f));
    }

    static int consoleSideGlow(int x, int y, int f) {
        if (y >= 14) return 0;
        if (x == 7 || x == 8) return GLOW_CORE;
        if (x == 6 || x == 9) return glow(1, wave((15 - y) / 16.0, f));
        return 0;
    }

    static int conduitTop(int x, int y, int f) {
        double d = dist(x, y);
        if (d < 1.6) return WHITE;
        if (d < 2.6) return CYAN;
        if (d < 4.6) return HULL;
        return HULL_SHADE;
    }

    static int conduitSide(int x, int y, int f) {
        // used across x 4..11: posts at the edges, open windows between
        if (y <= 1 || y >= 14) return x % 2 == 0 ? HULL : HULL_LIT;
        if (x <= 5 || x >= 10) return mix(HULL_LIT, HULL_DEEP, (x <= 5 ? x - 4 : 11 - x) / 2.0);
        if (y == 5 || y == 10) return HULL_SEAM;
        return 0;
    }

    static int conduitCore(int x, int y, int f) {
        double w = wave((15 - y) / 16.0, f);
        int c = mix(CYAN, WHITE, 0.25 + 0.75 * w);
        if (x <= 5 || x >= 10) c = mix(c, CYAN_DARK, 0.5);
        return c;
    }

    /** A ring segment's slab from above: hull white with a faint seam every four rows. */
    static int ringTop(int x, int y, int f) {
        int c = (y % 4 == 0) ? HULL_RIVET : HULL;
        if (x == 0 || x == 15) c = HULL_SEAM;
        return c;
    }

    /** The slab's edge (rows 5 to 10 show): a lit lip, the channel the seam light runs in, a shaded underside. */
    static int ringSide(int x, int y, int f) {
        if (y <= 5 || y >= 11) return HULL_SEAM;
        if (y == 6) return HULL_LIT;
        if (y == 7 || y == 8) return HULL_SEAM;
        return y == 9 ? HULL : HULL_DEEP;
    }

    static int ringGlow(int x, int y, int f) {
        if (y == 7 || y == 8) return GLOW_CORE;
        if (y == 6 || y == 9) return glow(1, wave(x / 16.0, f));
        return 0;
    }

    static int seatTop(int x, int y, int f) {
        double d = dist(x, y);
        if (d < 3.4) return 0x2A2F35;
        if (d < 4.4) return CYAN_DARK;
        if (d < 5.2) return HULL_SEAM;
        return hullRim(x, y, (x + y) % 2 == 0 ? HULL : HULL_SHADE);
    }

    static int seatSide(int x, int y, int f) {
        // the lower half shows
        if (y < 8) return HULL;
        if (y == 8) return HULL_LIT;
        if (y == 15) return HULL_SEAM;
        if (y == 11) return (x % 3 == 0) ? CYAN : HULL_SEAM;
        return HULL_SHADE;
    }

    static int seatCrystal(int x, int y, int f) {
        double facet = ((x + y) % 4) / 4.0;
        double w = wave((x + 15 - y) / 30.0, f);
        int c = mix(mix(VIOLET, CYAN, facet), WHITE, 0.15 + 0.6 * w);
        if ((x + y) % 5 == 0) c = mix(c, WHITE, 0.4);
        return c;
    }

    static int foundationTop(int x, int y, int f) {
        int c = (x + y) % 2 == 0 ? HULL : grain(HULL, x, y, 5, 0.02);
        double d = dist(x, y);
        if (d > 5.0 && d < 7.0) c = HULL_SEAM;
        if (d < 2.2) c = HULL_RIVET;
        return hullRim(x, y, c);
    }

    /** The foundation's ring of light: a white core edged in cyan, a band running round it. */
    static int foundationGlow(int x, int y, int f) {
        double d = dist(x, y);
        if (d <= 5.0 || d >= 7.0) return 0;
        double ang = (Math.atan2(y - 7.5, x - 7.5) + Math.PI) / (2 * Math.PI);
        return glow(d > 5.6 && d < 6.4 ? 0 : 1, wave(ang, f));
    }

    static int foundationSide(int x, int y, int f) {
        int c = y < 8 ? HULL : HULL_SHADE;
        if (y == 7) c = HULL_SEAM;
        return hullRim(x, y, c);
    }

    /** The echo shelf from above: a hull grid (style §7.1 row 2: the shelf's frame turns white, the books do not). */
    static int shelfTop(int x, int y, int f) {
        int c = HULL;
        if (x % 5 == 0 || y % 5 == 0) c = HULL_SEAM;
        return hullRim(x, y, c);
    }

    /** Two rows of books in a white frame; the spines' colours vary and are those of 0.16. */
    static int shelfSide(int x, int y, int f) {
        if (x == 0 || x == 15 || y == 0 || y == 15 || y == 7 || y == 8) return y == 8 ? HULL_DEEP : HULL;
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
     * A face of the cladding anchor: the hull's rim round a white field, and on it a hexagonal plate a shade darker,
     * lit from the upper left, with a bolt at each corner, a channel round it where the light runs, and a recessed
     * socket in its middle (a stud at its bottom) where a cladding will lock in later.
     */
    static int anchorFace(int x, int y, int f) {
        int c = (x + y) % 2 == 0 ? HULL : HULL_SHADE;
        double h = hexDist(x, y);
        if (h < 6.4) {
            double lit = ((7.5 - x) + (7.5 - y)) / 15.0;
            c = mix(HULL_DEEP, HULL_LIT, 0.45 + 0.4 * lit);
            if (h >= 5.6) c = mix(c, HULL_DEEP, 0.5);
            if (h >= 3.8 && h < 5.2) c = HULL_SEAM;
            if (h < 3.4) c = h < 1.3 ? HULL_LIT : h < 2.0 ? HULL_DEEP : 0x8E979F;
        }
        // the bolts at the plate's six corners
        double ang = Math.atan2(y - 7.5, x - 7.5), r = Math.hypot(x - 7.5, y - 7.5);
        double off = Math.abs(((ang / (Math.PI / 3)) % 1 + 1.5) % 1 - 0.5);
        if (r > 5.0 && r < 6.2 && off < 0.12) c = HULL_RIVET;
        return hullRim(x, y, c);
    }

    /** The anchor's light: a white core round its plate edged in cyan, a band running round it. */
    static int anchorGlow(int x, int y, int f) {
        double h = hexDist(x, y);
        if (h < 3.8 || h >= 5.2) return 0;
        double a = (Math.atan2(y - 7.5, x - 7.5) / (Math.PI * 2) + 1) % 1;
        return glow(h >= 4.2 && h < 4.8 ? 0 : 1, wave(a, f));
    }

    // ---- the floating foundation (0.16.0)

    /** Whether a pixel lies on the lattice's lines (every fifth pixel inside the rim, a 3 x 3 grid of cells). */
    static boolean latticeLine(int v) {
        return v == 5 || v == 10;
    }

    /** The deck people walk on: a white plate over the lattice, its nodes studded. */
    static int floatTop(int x, int y, int f) {
        int c = (x + y) % 2 == 0 ? HULL : grain(HULL, x, y, 9, 0.02);
        boolean lx = latticeLine(x), ly = latticeLine(y);
        if (lx || ly) c = HULL_SEAM;
        if (lx && ly) c = HULL_RIVET;
        return hullRim(x, y, c);
    }

    /**
     * A side: the lattice as a truss, posts and rails with a brace across each cell, and a channel along the foot where
     * the light from underneath spills out.
     */
    static int floatSide(int x, int y, int f) {
        int c = y < 8 ? HULL : HULL_SHADE;
        boolean lx = latticeLine(x), ly = latticeLine(y);
        if (lx || ly || x > 0 && x < 15 && y > 0 && y < 13 && (x + y) % 5 == 0) c = HULL_SEAM;
        if (lx && ly) c = HULL_RIVET;
        if (y == 13) c = HULL_DEEP;
        if (y == 14) c = HULL_SEAM;
        return hullRim(x, y, c);
    }

    /** The underside: an emitter plate, its rings grooved round a hub, under the lattice. */
    static int floatBottom(int x, int y, int f) {
        int c = (x + y) % 2 == 0 ? HULL_SHADE : grain(HULL_SHADE, x, y, 11, 0.02);
        double d = dist(x, y);
        if (d >= 2.4 && d < 3.1 || d >= 4.9 && d < 5.6) c = HULL_SEAM;
        if (d < 1.6) c = HULL_RIVET;
        if (latticeLine(x) && latticeLine(y)) c = HULL_DEEP;
        return hullRim(x, y, c);
    }

    /**
     * The lifting light on the underside: rings of light running out from the hub over the animation (two at a time),
     * white at their crest and edged in cyan, the hub always white, the lattice's nodes glinting cyan; nothing in the
     * corners past the plate.
     */
    static int floatBottomGlow(int x, int y, int f) {
        double d = dist(x, y);
        if (d < 1.4) return GLOW_CORE;
        if (d > 7.2) return 0;
        double v = ((d / 3.6 - f / (double) FRAMES) % 1 + 1) % 1;
        double ring = Math.max(0, 1 - Math.min(v, 1 - v) * 7);
        if (ring > 0.55) return GLOW_CORE;
        if (ring > 0.15) return glow(1, ring);
        if (latticeLine(x) && latticeLine(y)) return glow(1, 0);
        return 0;
    }

    /** The foot of a side: the light spilling out from underneath, a white core edged in cyan, a band along it. */
    static int floatSideGlow(int x, int y, int f) {
        if (x < 1 || x > 14) return 0;
        double w = wave(x / 16.0, f);
        if (y == 14) return GLOW_CORE;
        if (y == 13) return glow(1, w);
        return 0;
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

    /**
     * The Sophon Array's emblem (0.15.0): the lattice sphere's outline crossed by two parallels, on two struts over
     * the podium's roof line, the light point at the sphere's heart lit.
     */
    static int sphereEmblem(int x, int y) {
        double d = Math.hypot(x - 7.5, y - 6);
        if (d < 1.2) return 2;
        if (d >= 3.7 && d < 4.7) return 1;
        if (d < 3.7 && (y == 4 || y == 8)) return 1;
        if ((x == 5 || x == 10) && y >= 11 && y <= 12) return 1;
        if (y == 13 && x >= 2 && x <= 13) return 1;
        return 0;
    }

    /**
     * The Holo Sand Table's emblem (0.15.0): the rotunda's shallow dome on its drum, the ring light hanging inside and
     * the hologram over the table, lit; the table's line under them.
     */
    static int tableEmblem(int x, int y) {
        double d = Math.hypot(x - 7.5, y - 9);
        if (y <= 6 && d >= 5.6 && d < 6.6) return 1;
        if ((x == 2 || x == 13) && y >= 7 && y <= 13) return 1;
        if (y == 13 && x >= 2 && x <= 13) return 1;
        if (y == 7 && x >= 5 && x <= 10) return 2;
        if (y == 12 && x >= 4 && x <= 11) return 1;
        if (x == 6 && y >= 10 && y <= 11 || x == 9 && y >= 9 && y <= 11 || (x == 7 || x == 8) && y == 11) return 2;
        return 0;
    }

    /**
     * The Weather Station's emblem (0.16.0): the mast on the hut's roof line, the vane's arm across it with its two
     * cups, and the churning cloud over it, lit.
     */
    static int weatherEmblem(int x, int y) {
        if (y >= 2 && y <= 4 && Math.hypot((x - 7.5) / 1.8, (y - 3.5) * 1.1) < 3.0) return 2;
        if ((x == 7 || x == 8) && y >= 6 && y <= 12) return 1;
        if (y == 7 && x >= 4 && x <= 11) return 1;
        if ((x == 4 || x == 11) && y == 8) return 2;
        if (y == 13 && x >= 3 && x <= 12) return 1;
        return 0;
    }

    /**
     * The Sky Sail Array's emblem (0.16.0): two masts on the deck line and the light sail spread between them, billowing
     * a little, lit.
     */
    static int sailsEmblem(int x, int y) {
        if ((x == 3 || x == 12) && y >= 3 && y <= 12) return 1;
        if (y == 13 && x >= 2 && x <= 13) return 1;
        double belly = 0.6 * Math.sin((y - 4) * Math.PI / 6);
        if (y >= 4 && y <= 10 && x >= 5 - belly && x <= 10 + belly) return 2;
        return 0;
    }

    /**
     * The Observatory's emblem (0.16.0): the stepped dome's outline on its drum, the meridian slit lit, the platform's
     * line under them.
     */
    static int observatoryEmblem(int x, int y) {
        double d = Math.hypot(x - 7.5, y - 9);
        if ((x == 7 || x == 8) && y >= 4 && y <= 8) return 2;
        if (y <= 9 && d >= 5.0 && d < 6.0) return 1;
        if ((x == 2 || x == 13) && y >= 9 && y <= 13) return 1;
        if (y == 13 && x >= 2 && x <= 13) return 1;
        return 0;
    }

    /**
     * The Droplet Hangar's emblem (0.16.0): a mirror droplet, a teardrop pointing up, hovering over its cradle's rim on
     * the deck line, lit.
     */
    static int dropletEmblem(int x, int y) {
        double d = Math.hypot(x - 7.5, y - 7.5);
        if (d < 2.7 || y >= 3 && y < 6 && Math.abs(x - 7.5) < (y - 2.5) * 0.9) return 2;
        if (y == 12 && (x == 4 || x == 11)) return 1;
        if (y == 12 && x >= 5 && x <= 10) return 1;
        if (y == 13 && x >= 2 && x <= 13) return 1;
        return 0;
    }

    /** A module core's four faces in its folder (0.16.0's helper): its accent and its emblem. */
    static void core(String key, int accent, Emblem e) throws Exception {
        String dir = "blocks/module_core/" + key + "/";
        still(dir + "front", (x, y, f) -> coreFront(x, y, f, false, accent, e));
        anim(dir + "front_on", (x, y, f) -> coreFront(x, y, f, true, accent, e));
        still(dir + "side", (x, y, f) -> coreSide(x, y, accent));
        still(dir + "top", (x, y, f) -> coreTop(x, y, accent));
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

    /** How many turns the coiled wire of the phase III materials makes before its tail runs off to a corner. */
    static final double TURNS = 2.5;
    /** Where along the wire its coil ends and its tail begins, and the angle on the last turn where it does. */
    static final double TAIL = 0.88, END = -0.75;

    /**
     * A point of the coiled wire at {@code s} (0 to 1): {x, y, depth}, depth above 0 on the coil's near side. A loose
     * coil of {@link #TURNS} turns drifting up and to the right like a spring, then a short tail to the top right corner.
     */
    static double[] wireAt(double s) {
        if (s <= TAIL) {
            double u = s / TAIL, th = END - 2 * Math.PI * TURNS * (1 - u);
            return new double[] { 5.4 + 4.6 * u + 4.3 * Math.cos(th), 10.4 - 4.2 * u + 3.2 * Math.sin(th),
                Math.sin(th + 0.9) };
        }
        double u = (s - TAIL) / (1 - TAIL);
        double x0 = 10.0 + 4.3 * Math.cos(END), y0 = 6.2 + 3.2 * Math.sin(END);
        return new double[] { x0 + (14.6 - x0) * u, y0 + (1.4 - y0) * u, 1 };
    }

    /**
     * The wire under a pixel: {depth, s} of the nearest side of it that passes within 0.6 of the pixel's middle, or
     * null where there is none.
     */
    static double[] wire(int x, int y) {
        double[] best = null;
        for (int i = 0; i <= 900; i++) {
            double s = i / 900.0;
            double[] p = wireAt(s);
            if (Math.hypot(x + 0.5 - p[0], y + 0.5 - p[1]) >= 0.6) continue;
            if (best == null || p[2] > best[0]) best = new double[] { p[2], s };
        }
        return best;
    }

    /**
     * Phase alloy wire (0.15.0, BLUEPRINT 3.6): the alloy drawn as fine as a hair and coiled, deep blue metal lit on the
     * coil's near side and dark on its far side.
     */
    static int phaseAlloyWire(int x, int y, int f) {
        double[] w = wire(x, y);
        if (w == null) return 0;
        if (w[0] <= 0) return mix(0x1A2F52, 0x24406A, w[0] + 1);
        int c = mix(0x3E6496, 0x87A9D8, w[0]);
        // a glint where the near side faces the light most
        return w[0] > 0.92 ? mix(c, WHITE, 0.35) : c;
    }

    /**
     * Memory filament (0.15.0, BLUEPRINT 3.6): the same coil glowing like a string of light, near white on its near side
     * and a dim violet-cyan (the echo crystal's coating) on its far side, a bright pulse running along it from the coil
     * to the tail through the animation.
     */
    static int memoryFilament(int x, int y, int f) {
        double[] w = wire(x, y);
        if (w == null) return 0;
        double run = (w[1] - f / (double) FRAMES + 1) % 1, pulse = Math.max(0, 1 - Math.min(run, 1 - run) * 9);
        int c = w[0] <= 0 ? mix(mix(0x1C6F84, VIOLET, 0.35), 0x2F9FC0, w[0] + 1) : mix(0x3FC8E8, 0x9AF4FF, w[0]);
        return mix(c, WHITE, pulse * (w[0] <= 0 ? 0.6 : 0.95));
    }

    /**
     * Manifest matter (0.16.0, BLUEPRINT 3.6): a blob of black-gold liquid metal that never stops slowly changing
     * shape, its outline swelling and drawing in round the animation, gold running through the black, a gold rim and a
     * pale highlight; every term turns a whole number of times through the eight frames, so the loop has no seam.
     */
    static int manifestMatter(int x, int y, int f) {
        double ph = 2 * Math.PI * f / FRAMES;
        double dx = x - 7.5, dy = y - 8.0;
        double a = Math.atan2(dy, dx), d = Math.hypot(dx, dy * 1.15);
        double r = 5.5 + 0.7 * Math.sin(3 * a + ph) + 0.45 * Math.sin(5 * a - 2 * ph) + 0.3 * Math.cos(2 * a + ph);
        if (d > r) return 0;
        double flow = Math.sin(x * 0.9 + y * 0.6 + ph) + Math.sin(y * 1.1 - x * 0.4 - 2 * ph);
        int c = mix(0x0E0B06, 0x2A2010, (flow + 2) / 4);
        if (flow > 1.1) c = mix(0x8A6A1E, 0xFFD87A, (flow - 1.1) / 0.9);
        if (r - d < 0.9) c = mix(c, 0x6A5018, 0.6);
        double hl = Math.hypot(x - 5.5 - 0.6 * Math.sin(ph), y - 5.2);
        if (hl < 1.3) c = mix(c, 0xFFF4D0, 0.8 - hl * 0.3);
        return c;
    }

    /** The anti-gravity lattice's cube in the icon: its middle's height, and how far a unit of it spans. */
    static final double CUBE_Y = 6.0, CUBE_X = 2.6, CUBE_XZ = 1.5, CUBE_UP = 2.6;

    /**
     * A point of a face of the lattice cube under a pixel, seen from above its +X +Z corner: {face, p, q} with face 0
     * the top, 1 the +X side, 2 the +Z side and p, q from -1 to 1 across it; null where no face lies.
     */
    static double[] cubeAt(double sx, double sy) {
        double ax = (sx - 7.5) / CUBE_X;
        // the top (Y = 1): sx = (u - v) X, sy = (u + v) XZ - UP
        double b = (sy - CUBE_Y + CUBE_UP) / CUBE_XZ, u = (ax + b) / 2, v = (b - ax) / 2;
        if (Math.abs(u) <= 1 && Math.abs(v) <= 1) return new double[] { 0, u, v };
        // the +X side: Z = v, Y = w
        double sv = 1 - ax, sw = ((1 + sv) * CUBE_XZ - (sy - CUBE_Y)) / CUBE_UP;
        if (ax >= 0 && Math.abs(sv) <= 1 && Math.abs(sw) <= 1) return new double[] { 1, sv, sw };
        // the +Z side: X = u, Y = w
        double zu = 1 + ax, zw = ((zu + 1) * CUBE_XZ - (sy - CUBE_Y)) / CUBE_UP;
        if (ax <= 0 && Math.abs(zu) <= 1 && Math.abs(zw) <= 1) return new double[] { 2, zu, zw };
        return null;
    }

    /**
     * The anti-gravity lattice (0.16.0, BLUEPRINT 3.6): a lattice cube hanging in the air and bobbing a little, its
     * edges and middles lit lines over dark glass, its nodes white, and under it the lifting light, a pool of light
     * swelling and fading with motes rising from it to the cube.
     */
    static int antiGravityLattice(int x, int y, int f) {
        double ph = 2 * Math.PI * f / FRAMES, bob = 0.5 * Math.sin(ph);
        double[] c = cubeAt(x + 0.5, y + 0.5 - bob);
        if (c != null) {
            int face = (int) c[0];
            int fill = face == 0 ? 0x1E3A52 : face == 1 ? 0x13283A : 0x0F2030;
            double p = c[1], q = c[2];
            boolean edgeP = Math.abs(Math.abs(p) - 1) < 0.2, edgeQ = Math.abs(Math.abs(q) - 1) < 0.2;
            if (edgeP && edgeQ) return WHITE;
            if (edgeP || edgeQ) return LIFT;
            // the lattice inside: a cross over the top, a node in the middle of each side
            if (face == 0 && (Math.abs(p) < 0.17 || Math.abs(q) < 0.17)) return mix(LIFT, 0x2F6F8A, 0.45);
            if (face != 0 && Math.abs(p) < 0.25 && Math.abs(q) < 0.25) return mix(LIFT, WHITE, 0.3);
            return fill;
        }
        // the lifting light: a pool under the cube and motes rising from it
        double pulse = 0.55 + 0.45 * Math.sin(ph);
        double e = Math.hypot((x - 7.5) / 4.6, (y - 14.2) / 1.2);
        if (e < 1) return mix(0x2F6F8A, mix(LIFT, WHITE, 0.4 * pulse), (1 - e) * (0.5 + 0.5 * pulse) + 0.3);
        if (y >= 11 && y <= 13 && x >= 5 && x <= 10) {
            double rise = ((14 - y) / 3.0 + f / (double) FRAMES + x * 0.37) % 1;
            if (rise < 0.25) return mix(0x2F6F8A, LIFT, 1 - rise * 3);
        }
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
