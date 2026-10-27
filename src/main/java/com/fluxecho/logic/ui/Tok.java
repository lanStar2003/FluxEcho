package com.fluxecho.logic.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The design tokens of the "clear-sky astrolabe" UI (ui/FINAL.md section 1.3-1.5): every colour, size and layer the
 * new screens use, so that a page never writes a colour literal and changing a token changes every page. Colours are
 * ARGB with full alpha unless the name says otherwise.
 * <p>
 * Four families of colour: the neutrals (porcelain paper and blue-black ink, plus the light colours used on dark
 * surfaces); six semantic hues with four grades each ({@code ink} writes text, {@code base} fills graphics,
 * {@code tint} is a light ground, {@code lum} draws on dark surfaces); nine constellation colours for the star chart;
 * and four grades for each of the 23 building rows, whose {@code lum} is the building's own colour in the world (style
 * section 3.6) so that what a building glows in the world is what marks it in the UI. Section colour codes in text are
 * remapped to inks on paper ({@link #sectionOnPaper}), because vanilla's colours are made for a dark ground.
 * <p>
 * {@code FluxDraw}'s colour constants are not touched: the world renderers keep them, and the {@code lum} grade of the
 * hues equals them (gold excepted, which is Evening-Star gold everywhere). Pure constants, no Minecraft classes.
 */
public final class Tok {

    private Tok() {}

    // ------------------------------------------------------------------ neutrals: porcelain and ink (1.3.1)

    /** Highlights, button faces, inputs, cards, the white heart of a star. */
    public static final int PAPER_0 = 0xFFFFFFFF;
    /** Panel top, cards. */
    public static final int PAPER_1 = 0xFFF7FAFC;
    /** Panel bottom, top of the console background. */
    public static final int PAPER_2 = 0xFFEEF3F7;
    /** Bottom of the console background: the darkest paper any text can land on, so text contrast is checked here. */
    public static final int PAPER_FLOOR = 0xFFE4ECF3;
    /** Wells, item slots, progress tracks, the ground below the star chart's horizon. */
    public static final int PAPER_3 = 0xFFE2E9F0;
    /** Hairlines, separators, grids, orbits (decoration, never information). */
    public static final int LINE_1 = 0xFFCBD6E1;
    /** Borders, disabled graphics, locked stars. */
    public static final int LINE_2 = 0xFFA9B8C8;
    /** Captions, units, axis labels. */
    public static final int INK_3 = 0xFF58697D;
    /** Secondary text, default icon colour. */
    public static final int INK_2 = 0xFF4A596B;
    /** Body text and values. */
    public static final int INK_1 = 0xFF1C2836;
    /** Big titles and the ink card. */
    public static final int INK_0 = 0xFF0D1520;

    // the light set, for the world, the ink card and the night view (1.3.1, 1.3.6)
    public static final int GLASS = 0xFFEAF6FF;
    public static final int LUM_TEXT = 0xFFF4FBFF;
    public static final int LUM_TEXT_2 = 0xFFBFE3F2;
    public static final int LUM_IDLE = 0xFF86A6B8;
    public static final int NIGHT_0 = 0xFF0E1726;
    public static final int NIGHT_1 = 0xFF16243A;
    /** The dark backing behind light text in the world (alpha 0.32-0.80 by ambient light, 5.1). */
    public static final int HOLO_BACKING = 0xFF0D1821;

    // ------------------------------------------------------------------ surfaces

    /** Opacity of panels (C01): paper-1 to paper-2 at 0.96. */
    public static final float PANEL_ALPHA = 0.96f;
    public static final int PANEL_TOP = 0xF5F7FAFC, PANEL_BOTTOM = 0xF5EEF3F7;
    /** Opacity of the ink card, the dark floating card (tooltips, drawers' menus, toasts). */
    public static final float INK_CARD_ALPHA = 0.94f;
    /** The ink card: ink-0 at 0.94. */
    public static final int INK_CARD = 0xF00D1520;
    /** Section rules inside an ink card (C07). */
    public static final int INK_CARD_RULE = 0xFF2A3A4E;
    /** Card face on a panel (C02): paper-0 at 0.9. */
    public static final int CARD = 0xE6FFFFFF;
    /** Grain over panels (C01): white grain at 0.035. */
    public static final float GRAIN_ALPHA = 0.035f;
    /** The haze over the world behind a window (2.6): paper-2 at 0.55, corners ink-0 at 0.08. */
    public static final int HAZE = 0x8CEEF3F7, HAZE_CORNER = 0x140D1520;
    /** The dimming under a drawer that covers the content (S tier) or a coach mark: ink-0 at 0.35. */
    public static final int SCRIM = 0x590D1520;
    /** World cards (C54): glass film at most 0.16; day backing paper-1 at 0.72; lines at most 0.85. */
    public static final float GLASS_MAX_ALPHA = 0.16f, DAY_BACKING_ALPHA = 0.72f, LUM_LINE_MAX_ALPHA = 0.85f;
    /** Night backing alpha range in the world, by ambient light (5.1). */
    public static final float NIGHT_BACKING_MIN = 0.32f, NIGHT_BACKING_MAX = 0.80f;

    // ------------------------------------------------------------------ buttons and the extra inks

    /** Primary button (white text 4.91:1); hover and pressed are a grade darker each. */
    public static final int FLUX_600 = 0xFF0C7A9C, FLUX_600_HOVER = 0xFF0A6F8F, FLUX_600_PRESSED = 0xFF085C77;
    /** The two inks only the section remap needs: dark/blue (section 1, 9) and light purple (section d). */
    public static final int BLUE_INK = 0xFF1F4FB0, MAGENTA_INK = 0xFF9030C8;
    /** Evening-Star gold: the one gold of the UI and the world (narrative ROUTE, nexus beam after the leap). */
    public static final int EVENING_GOLD = 0xFFFFC56B;

    // the star chart's grounds (1.3.3)
    /** The chart paper at the top of the astrolabe, which constellation inks are checked on. */
    public static final int STAR_PAPER = 0xFFE9F0F7;
    /** The paper just above the horizon. */
    public static final int HORIZON = 0xFFF7F3EC;

    // ------------------------------------------------------------------ semantic hues (1.3.2)

    /**
     * A semantic hue in four grades. {@code ink} is for text and thin lines (4.5:1 on any paper), {@code base} for
     * graphics and fills, {@code tint} a light ground ({@code ink} reads on it at 4.5:1), {@code lum} only on dark
     * surfaces (the ink card, world holograms, the HUD, the night view).
     */
    public enum Hue {

        /** Energy, primary actions, selection, ready. */
        FLUX(0xFF0A6F8F, 0xFF14A6CE, 0xFFD9F4FB, 0xFF4FE3FF),
        /** Research, records, manifestation. */
        ECHO(0xFF5A3EC8, 0xFF7C5CFF, 0xFFEAE4FF, 0xFF8A5CFF),
        /** Evening-Star gold: the light running along lit star lines, samples, milestones, the route. */
        GOLD(0xFF925D0C, 0xFFE4951B, 0xFFFFECCF, EVENING_GOLD),
        /** Running, done, enough, input. */
        OK(0xFF1B7442, 0xFF2DB56A, 0xFFDDF5E6, 0xFF6CFF8A),
        /** Waiting, short of material, yielding, output. */
        WARN(0xFF965800, 0xFFF09A1E, 0xFFFFEDD4, 0xFFFFB040),
        /** Stuck, no power, not formed, danger. */
        ERR(0xFFB0261F, 0xFFE5484D, 0xFFFDE3E2, 0xFFFF6050),
        /** Idle, none, not loaded: the neutrals in the four roles. */
        IDLE(INK_3, LINE_2, PAPER_3, LUM_IDLE);

        public final int ink, base, tint, lum;

        Hue(int ink, int base, int tint, int lum) {
            this.ink = ink;
            this.base = base;
            this.tint = tint;
            this.lum = lum;
        }
    }

    // ------------------------------------------------------------------ constellations (1.3.3)

    /**
     * The nine constellations of the star chart, the ship's systems (narrative section 3.2). {@code ink} draws stars,
     * star lines and names on the chart paper, {@code wash} the watercolour nebula (multiplied), {@code lum} the night
     * view and the hologram over the nexus. Constellation colours only appear on star-chart pages; building colours
     * never share a screen with them.
     */
    public enum Constellation {

        ROUTE(EVENING_GOLD, 0xFF925D0C, 0xFFFAE2BD),
        RECALL(0xFFC9D6E2, 0xFF4A6C8B, 0xFFD1DCE6),
        KEEL(0xFF4FE3FF, 0xFF0B778C, 0xFFBDF0FA),
        COMPASS(0xFFFF8CE6, 0xFFC810A0, 0xFFFABDED),
        HULL(0xFF7DB8FF, 0xFF116AD6, 0xFFBDD8FA),
        FURNACE(0xFFFF9F5A, 0xFFB0520E, 0xFFFAD6BD),
        MEMORY(0xFF8A5CFF, 0xFF794AF0, 0xFFCEBDFA),
        OUTLAND(0xFF5FD3A8, 0xFF227B5A, 0xFFC7F0E1),
        HOLD(0xFFD8F4F0, 0xFF40766F, 0xFFC7EFEA);

        public final int lum, ink, wash;

        Constellation(int lum, int ink, int wash) {
            this.lum = lum;
            this.ink = ink;
            this.wash = wash;
        }
    }

    // ------------------------------------------------------------------ buildings (1.3.4)

    /**
     * One row of the building colours: a building key (ID ledger section 2) and, for the root site, the form (its
     * shape family: {@code well}, {@code root}, {@code core}). {@code lum} is the building's colour in the world
     * (style section 3.6, the same value {@code SkinPalette} gives the skin); {@code line} draws graphics on paper (3:1
     * on the floor), {@code ink} writes (4.5:1 on the floor), {@code tint} is its light ground. {@code sigil} is the
     * building's sigil icon in the atlas.
     */
    public static final class Building {

        public final String key, form;
        public final int lum, line, ink, tint, sigil;

        Building(String key, String form, int lum, int line, int ink, int tint, int sigil) {
            this.key = key;
            this.form = form;
            this.lum = lum;
            this.line = line;
            this.ink = ink;
            this.tint = tint;
            this.sigil = sigil;
        }

        @Override
        public String toString() {
            return form == null ? key : key + "/" + form;
        }
    }

    /** The root site's forms, named after their shape families (ID ledger section 15). */
    public static final String FORM_WELL = "well", FORM_ROOT = "root", FORM_CORE = "core";

    private static final List<Building> BUILDINGS;

    private static Building b(String key, String form, int lum, int line, int ink, int tint, int sigil) {
        return new Building(key, form, 0xFF000000 | lum, 0xFF000000 | line, 0xFF000000 | ink, 0xFF000000 | tint, sigil);
    }

    static {
        List<Building> l = new ArrayList<>();
        // the drift capsule is the nexus at phase 0 and shares its colour
        l.add(b("drift_capsule", null, 0x4FE3FF, 0x0E94AD, 0x0B7489, 0xE5F7FA, AtlasMap.I_SIGIL_DRIFT_CAPSULE));
        l.add(b("nexus", null, 0x4FE3FF, 0x0E94AD, 0x0B7489, 0xE5F7FA, AtlasMap.I_SIGIL_NEXUS));
        l.add(b("library", null, 0xFFD9A0, 0xBA783E, 0x925E31, 0xF7EFE8, AtlasMap.I_SIGIL_LIBRARY));
        l.add(b("eco_dome", null, 0x7BE08A, 0x249B36, 0x1D7A2B, 0xE6F9E9, AtlasMap.I_SIGIL_ECO_DOME));
        l.add(b("mana_fountain", null, 0x6AA8FF, 0x3C86EF, 0x1265DA, 0xE5EEFA, AtlasMap.I_SIGIL_MANA_FOUNTAIN));
        l.add(b("arcane_court", null, 0xB07CFF, 0xA06AF3, 0x843DEF, 0xEDE5FA, AtlasMap.I_SIGIL_ARCANE_COURT));
        l.add(b("blood_covenant", null, 0xE0344A, 0xE0344A, 0xCF1F36, 0xFAE5E8, AtlasMap.I_SIGIL_BLOOD_COVENANT));
        l.add(b("hunting_ground", null, 0xFF9A3C, 0xCF6D11, 0xA4550D, 0xFAEFE5, AtlasMap.I_SIGIL_HUNTING_GROUND));
        l.add(b("resonance_gate", null, 0x5CF2C2, 0x0D9A6D, 0x0A7956, 0xE5FAF4, AtlasMap.I_SIGIL_RESONANCE_GATE));
        l.add(b("dual_foil_vault", null, 0xF2EBDD, 0xA4823F, 0x826631, 0xF7F2E9, AtlasMap.I_SIGIL_DUAL_FOIL_VAULT));
        l.add(b("dome_generator", null, 0x7DB8FF, 0x3287EE, 0x1167D1, 0xE5EFFA, AtlasMap.I_SIGIL_DOME_GENERATOR));
        // the root site's three forms: tidal well (EV), deep root (LuV), earth core (UV)
        l.add(b("root", FORM_WELL, 0x2E9FB8, 0x2B93AA, 0x217486, 0xE7F6F9, AtlasMap.I_SIGIL_TIDAL_WELL));
        l.add(b("root", FORM_ROOT, 0x5FD3A8, 0x2A986F, 0x217858, 0xE7F8F2, AtlasMap.I_SIGIL_DEEP_ROOT));
        l.add(b("root", FORM_CORE, 0xFF7A3C, 0xE95713, 0xB8450F, 0xFAECE5, AtlasMap.I_SIGIL_EARTH_CORE));
        l.add(b("sophon_array", null, 0xFF8CE6, 0xEE2FC5, 0xC4109D, 0xFAE5F6, AtlasMap.I_SIGIL_SOPHON_ARRAY));
        l.add(b("holo_table", null, 0xA8E0FF, 0x118ED4, 0x0E70A7, 0xE5F3FA, AtlasMap.I_SIGIL_HOLO_TABLE));
        l.add(b("sky_sails", null, 0xFFD27A, 0xB57C0F, 0x8E620C, 0xFAF3E5, AtlasMap.I_SIGIL_SKY_SAILS));
        l.add(b("observatory", null, 0xB9C8FF, 0x607FF2, 0x335CEE, 0xE5EAFA, AtlasMap.I_SIGIL_OBSERVATORY));
        l.add(b("droplet_hangar", null, 0xD8E4F0, 0x588BBF, 0x3D6D9E, 0xE9F0F6, AtlasMap.I_SIGIL_DROPLET_HANGAR));
        l.add(b("weather_station", null, 0xFF9E8A, 0xEE5131, 0xC83010, 0xFAE9E5, AtlasMap.I_SIGIL_WEATHER_STATION));
        // the source crown is the orbital anchor's second form and keeps its row
        l.add(b("orbital_anchor", null, 0xFFFFFF, 0x878787, 0x5A6B80, 0xF0F3F7, AtlasMap.I_SIGIL_ORBITAL_ANCHOR));
        l.add(b("source_heart", null, 0xF6E7FF, 0xB95BF2, 0xA01EED, 0xF2E5FA, AtlasMap.I_SIGIL_SOURCE_HEART));
        // the leap ring turns white at the leap; that is a world effect, its row stays aurora violet
        l.add(b("leap_ring", null, 0xC9B8FF, 0x9071F3, 0x7148F0, 0xE9E3FD, AtlasMap.I_SIGIL_LEAP_RING));
        BUILDINGS = Collections.unmodifiableList(l);
    }

    /** The 23 rows in the order of ui 1.3.4. */
    public static List<Building> buildings() {
        return BUILDINGS;
    }

    /** The row of a building key; for the root, its first form (the tidal well). Null for an unknown key. */
    public static Building building(String key) {
        for (Building b : BUILDINGS) if (b.key.equals(key)) return b;
        return null;
    }

    /**
     * The row of a building in a given shape. Only the root has forms: a shape name starting with {@code root} is the
     * deep root, with {@code core} the earth core, anything else the tidal well. Other buildings ignore the shape.
     */
    public static Building building(String key, String shape) {
        if (!"root".equals(key) || shape == null) return building(key);
        String form = shape.startsWith(FORM_ROOT) ? FORM_ROOT : shape.startsWith(FORM_CORE) ? FORM_CORE : FORM_WELL;
        for (Building b : BUILDINGS) if (b.key.equals(key) && form.equals(b.form)) return b;
        return building(key);
    }

    // ------------------------------------------------------------------ section codes (1.4.4)

    /** The colour codes in their order: 0-9, a-f. */
    public static final String SECTION_CODES = "0123456789abcdef";

    /**
     * {@code Tok.SECTION_ON_PAPER} of the design, by code index: the ink each vanilla colour code becomes on paper.
     * Dark and light shades of a hue become the same ink; gray and dark gray both mean "secondary" on paper.
     */
    private static final int[] SECTION_ON_PAPER = { INK_0, BLUE_INK, 0xFF1B7442, 0xFF0A6F8F, 0xFFB0261F, 0xFF5A3EC8,
        0xFF925D0C, INK_3, INK_3, BLUE_INK, 0xFF1B7442, 0xFF0A6F8F, 0xFFB0261F, MAGENTA_INK, 0xFF965800, INK_1 };

    /** The ink a section colour code is drawn in on paper, or 0 when the character is not a colour code. */
    public static int sectionOnPaper(char code) {
        int i = SECTION_CODES.indexOf(Character.toLowerCase(code));
        return i < 0 ? 0 : SECTION_ON_PAPER[i];
    }

    /** Vanilla's own colour for a section code (FontRenderer's table), or 0 when it is not a colour code. */
    public static int vanillaSection(char code) {
        int i = SECTION_CODES.indexOf(Character.toLowerCase(code));
        if (i < 0) return 0;
        int j = (i >> 3 & 1) * 85;
        int r = (i >> 2 & 1) * 170 + j, g = (i >> 1 & 1) * 170 + j, b = (i & 1) * 170 + j;
        if (i == 6) r += 85;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    // ------------------------------------------------------------------ sizes (1.5.1), GUI pixels

    /** The base unit: every spacing is a multiple of 4, 2 being the one exception (icon padding, chips). */
    public static final int U = 4;
    /** The spacing scale. Inside a component 4, between components 6-8, between blocks 12-16. */
    public static final int S2 = 2, S4 = 4, S6 = 6, S8 = 8, S12 = 12, S16 = 16, S24 = 24;
    static final int[] SPACING = { S2, S4, S6, S8, S12, S16, S24 };

    public static List<Integer> spacing() {
        List<Integer> l = new ArrayList<>();
        for (int s : SPACING) l.add(s);
        return l;
    }

    /** Top-right cut of a window, a card, a button, a chip; the other three corners get a 1 px bevel. */
    public static final int CUT_WINDOW = 6, CUT_CARD = 4, CUT_BUTTON = 3, CUT_CHIP = 2, BEVEL = 1;
    /** Strokes: buttons, inputs, selection frames. */
    public static final int STROKE = 1;
    /** The tick rule under headers: a 2 px tick every 8 px. */
    public static final int TICK_EVERY = 8, TICK_HEIGHT = 2;
    /** Item slot and item. */
    public static final int SLOT = 18, ITEM = 16;
    /** Icons: 12 and 16, micro icons 8. */
    public static final int ICON = 12, ICON_LARGE = 16, ICON_MICRO = 8;
    /** Smallest click target; smaller graphics extend their hit area to it. */
    public static final int HIT_MIN = 12;
    /** Window header height (full-screen headers are the top bar, {@code Lay.topBar}). */
    public static final int HEADER_WINDOW = 16;
    /** Building colour bar left of a header or name plate: 3 x 10. */
    public static final int PLATE_BAR_W = 3, PLATE_BAR_H = 10;
    /** The drop shadow of panels and ink cards: blur 8, down 2, ink-0 at 0.16. */
    public static final int SHADOW_BLUR = 8, SHADOW_DY = 2, SHADOW = 0x290D1520;

    /** A hairline: 0.5 px (one physical pixel) from factor 2 up, 1 px at factor 1. */
    public static float hairline(int factor) {
        return factor >= 2 ? 0.5f : 1f;
    }

    // ------------------------------------------------------------------ layers (1.6.7)

    /** z of page geometry, items, item counts, and overlays (ink card, drawers, menus, toasts). */
    public static final int Z_PAGE = 0, Z_ITEM = 100, Z_COUNT = 200, Z_OVERLAY = 300;

    /** Every token colour by name, for previews and tests. */
    public static List<Object[]> named() {
        return Arrays.asList(
            new Object[] { "paper-0", PAPER_0 },
            new Object[] { "paper-1", PAPER_1 },
            new Object[] { "paper-2", PAPER_2 },
            new Object[] { "paper-floor", PAPER_FLOOR },
            new Object[] { "paper-3", PAPER_3 },
            new Object[] { "line-1", LINE_1 },
            new Object[] { "line-2", LINE_2 },
            new Object[] { "ink-3", INK_3 },
            new Object[] { "ink-2", INK_2 },
            new Object[] { "ink-1", INK_1 },
            new Object[] { "ink-0", INK_0 },
            new Object[] { "glass", GLASS },
            new Object[] { "lum-text", LUM_TEXT },
            new Object[] { "lum-text-2", LUM_TEXT_2 },
            new Object[] { "lum-idle", LUM_IDLE },
            new Object[] { "night-0", NIGHT_0 },
            new Object[] { "night-1", NIGHT_1 },
            new Object[] { "holo-backing", HOLO_BACKING });
    }
}
