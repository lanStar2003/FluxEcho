package com.fluxecho.logic.probe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fluxecho.logic.shape.Family;
import com.fluxecho.logic.shape.Form;
import com.fluxecho.logic.skin.SkinEntry;
import com.fluxecho.logic.skin.SkinPalette;

/**
 * The swatch wall {@code /fluxecho swatch} sets out (0.17.0, style FINAL §8.6, DECISIONS B11): one wall the user takes
 * three screenshots of (noon, dusk, night, Complementary on) to settle every colour of the rebuild in one round. It is
 * 33 cells wide and 12 high, seen from the front; this class is its cell table, so the command that places it, the
 * client that tints it, the preview that draws it and the test that holds it to the style draft read one layout.
 * <p>
 * <b>Rows, bottom up.</b>
 * <ul>
 * <li>0–2, the light: every accent of style §3.6 (the twenty buildings', the root in its three forms: 22 colours) as a
 * light seam (row 0), a lamp window lit (row 1) and the same lamp dark (row 2), in groups that put each must-see
 * pair of §8.6 side by side (mana blue – guard blue ΔE 4.3 first), the revised tide teal and ice blue beside the nexus cyan, a
 * warm white column (no accent), and at the right end the evening gold of the memorial state next to the sail gold,
 * the memorial's warm-white keel and the nexus-cyan keel. Hull light seams and lamps, so the light is all there is to
 * compare.</li>
 * <li>3–5, the materials that have blocks in 0.17.0, a panel of five columns each: hull, footing, moss bronze, white
 * marble, ink, paper (M1–M6 of §3.5), and at the right the vanilla stone, quartz and birch to compare against. In each
 * panel the flat shapes (whole, seam, light seam, keel, relief, lamp, the specials) are flush with the wall, and the
 * shaped ones (round, slope, half, step, rib, column, soffit, coping) stand one cell proud of it in a front layer, with
 * air beside and above them, so each shows its form, its shading and its shadow.</li>
 * <li>6–8, the six materials whose block families come with the 0.20.0 widening (glaze, ochre, resonant crystal, phase
 * alloy, black mirror, star vault): reserved, set out by {@code swatch} once their blocks exist.</li>
 * <li>9–11, the three rows §8.6 adds for the fixtures (the six common bodies × twelve materials, {@code swatch 2},
 * 0.20.0): reserved.</li>
 * </ul>
 * In front of the wall a mirror pool three cells deep, as wide as the wall, rimmed with the footing's pool lips; under
 * the wall a footing course. The reserved rows are left as air.
 * <p>
 * <b>Where.</b> In the {@link ProbeFrame} of the operator who set it out: column {@link #CENTRE} on their line of
 * sight,
 * the wall's face {@link #BACK} cells ahead of them, the front layer {@link #FRONT}, the pool {@link #POOL_NEAR} to
 * {@link #POOL_FAR}, the front rim {@link #RIM}; row 0 on their floor. The marker sign stands on the front rim's left
 * corner ({@link ProbeMarker.Kind#SWATCH}).
 * <p>
 * <b>Light.</b> Which accent a light seam, lamp or keel shows, and whether a lamp is lit, are not blocks but the box
 * table's say ({@code SkinTable}, style §3.9); the swatch tells the client's chunk renderer through {@link #glows}, and
 * its lit lamps stay lit at every hour, so every screenshot holds both.
 */
public final class SwatchLayout {

    /** The wall's size, seen from the front. */
    public static final int COLS = 33, ROWS = 12;
    /** The column on the operator's line of sight. */
    public static final int CENTRE = 16;
    /** How far ahead of the operator: the wall's face, the front layer, the pool's near and far rows, the front rim. */
    public static final int BACK = 8, FRONT = 7, POOL_NEAR = 4, POOL_FAR = 6, RIM = 3;
    /** The rows of the light: seams, lit lamps, dark lamps. */
    public static final int SEAM_ROW = 0, LIT_ROW = 1, DARK_ROW = 2;
    /** The first row of the materials 0.17.0 has, of the reserved ones, of the fixtures. */
    public static final int MATERIAL_ROW = 3, RESERVED_ROW = 6, FIXTURE_ROW = 9;
    /** A material panel's width, and the first column of the vanilla references. */
    public static final int PANEL = 5, VANILLA_COL = 30;

    /** The twelve materials of style §3.5, in its order: their family and first shape number, and when they come. */
    public enum Material {

        HULL("M1", "舟壳", "hull", Family.HULL, 0, 0xEDF1F3),
        FOOTING("M2", "玄基", "footing", Family.FOOTING, 0, 0x36312D),
        MOSS("M3", "苔铜", "moss bronze", Family.SKIN_GARDEN, 0, 0x4E8C7B),
        MARBLE("M4", "汉白玉", "white marble", Family.SKIN_GARDEN, 8, 0xEFEAE1),
        INK("M5", "墨", "ink", Family.SKIN_SCRIPT, 0, 0x16181D),
        PAPER("M6", "宣", "paper", Family.SKIN_SCRIPT, 8, 0xF1E8D8),
        GLAZE("M7", "赤釉", "red glaze", null, 0, 0x5B1922),
        OCHRE("M8", "赭陶", "ochre tile", null, 8, 0xB8683E),
        RESONANT("M9", "共鸣晶", "resonant crystal", null, 0, 0x7FF5D2),
        PHASE("M10", "相位合金", "phase alloy", null, 8, 0x233F69),
        MIRROR("M11", "黑镜", "black mirror", null, 0, 0x0E1116),
        STARVAULT("M12", "星穹", "star vault", null, 8, 0x110D26);

        /** Style §3.5's number, its name, an English name. */
        public final String id, zh, en;
        /** The block family that holds it, or null while its family is not registered (0.20.0). */
        public final Family family;
        /** Its first shape number in the family: 0, or 8 for a skin's second material. */
        public final int base;
        /** Style §3.5's base colour, for the preview of the reserved ones. */
        public final int colour;

        Material(String id, String zh, String en, Family family, int base, int colour) {
            this.id = id;
            this.zh = zh;
            this.en = en;
            this.family = family;
            this.base = base;
            this.colour = colour;
        }

        /** Whether its blocks exist in this version. */
        public boolean placed() {
            return family != null;
        }
    }

    /**
     * What the box table says about a cell's light: its accent, whether its glows are awake, its lamps lit, its keel.
     */
    public static final class Glow {

        /** The accent ({@link SkinPalette#NONE}: warm white). */
        public final int accent;
        public final boolean awake, lamps;
        /** The keel's edge colour. */
        public final int keel;

        Glow(int accent, boolean awake, boolean lamps, int keel) {
            this.accent = accent;
            this.awake = awake;
            this.lamps = lamps;
            this.keel = keel;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Glow g && g.accent == accent && g.awake == awake && g.lamps == lamps && g.keel == keel;
        }

        @Override
        public int hashCode() {
            return ((accent * 31 + keel) * 2 + (awake ? 1 : 0)) * 2 + (lamps ? 1 : 0);
        }
    }

    /** One cell of the wall. */
    public static final class Cell {

        public enum Kind {
            /** Nothing: air. */
            AIR,
            /** A block of the new families: {@link #family} at shape number {@link #number}. */
            SKIN,
            /** A vanilla block to compare against: {@link #block} at {@link #meta}. */
            VANILLA,
            /** A reserved material's cell (0.20.0): left as air. */
            RESERVED,
            /** A fixture's cell (swatch 2, 0.20.0): left as air. */
            FIXTURE
        }

        public final Kind kind;
        public final Material material;
        public final Family family;
        public final int number;
        public final String block;
        public final int meta;
        /** The light the box table gives it, or null for the default. */
        public final Glow glow;
        /**
         * What it is on the wall, for the tests and the preview: {@code accent:<key>}, {@code gap}, {@code panel}...
         */
        public final String role;

        private Cell(Kind kind, Material material, Family family, int number, String block, int meta, Glow glow,
            String role) {
            this.kind = kind;
            this.material = material;
            this.family = family;
            this.number = number;
            this.block = block;
            this.meta = meta;
            this.glow = glow;
            this.role = role;
        }

        static final Cell AIR_CELL = new Cell(Kind.AIR, null, null, -1, null, 0, null, "air");

        static Cell skin(Material m, int number, Glow glow, String role) {
            return new Cell(Kind.SKIN, m, m.family, number, null, 0, glow, role);
        }

        static Cell vanilla(String block, int meta, String role) {
            return new Cell(Kind.VANILLA, null, null, -1, block, meta, null, role);
        }

        static Cell reserved(Material m) {
            return new Cell(Kind.RESERVED, m, null, -1, null, 0, null, "reserved:" + m.id);
        }

        /** A reserved cell beside the vanilla references (more references, or the later materials' keels). */
        static final Cell RESERVED_REF = new Cell(Kind.RESERVED, null, null, -1, null, 0, null, "reserved:ref");

        static final Cell FIXTURE_CELL = new Cell(Kind.FIXTURE, null, null, -1, null, 0, null, "fixture");

        /** The shape of a skin cell, or null. */
        public Form form() {
            return kind == Kind.SKIN ? family.form(number) : null;
        }

        /** Whether a block is set here. */
        public boolean placed() {
            return kind == Kind.SKIN || kind == Kind.VANILLA;
        }

        @Override
        public String toString() {
            switch (kind) {
                case SKIN:
                    return material.id + ":" + number + "(" + family.form(number) + ")";
                case VANILLA:
                    return block + ":" + meta;
                default:
                    return kind.name()
                        .toLowerCase(java.util.Locale.ROOT);
            }
        }
    }

    /** One column of the light rows. */
    public static final class Column {

        public enum Kind {
            /** A building's accent: seam, lit lamp, dark lamp. */
            ACCENT,
            /** Plain hull between groups. */
            GAP,
            /** No accent: the light seams' and lamps' warm white. */
            WARM,
            /** The memorial's evening gold as a light seam three high (style §6.7). */
            EVENING,
            /** A keel three high in the memorial state's warm white (§6.7). */
            KEEL_MEMORIAL,
            /** A keel three high in the nexus cyan (G2). */
            KEEL_CYAN
        }

        public final Kind kind;
        /** The building's key and form ({@link SkinPalette#accent(String, String)}), null but for an accent. */
        public final String key, form;
        /** The style's name of the colour. */
        public final String zh;

        Column(Kind kind, String key, String form, String zh) {
            this.kind = kind;
            this.key = key;
            this.form = form;
            this.zh = zh;
        }

        /** The colour its light shows: the accent, warm white, the evening gold or the keel's colour. */
        public int rgb() {
            switch (kind) {
                case ACCENT:
                    return SkinPalette.accent(key, form);
                case EVENING:
                    return SkinPalette.EVENING_GOLD;
                case KEEL_CYAN:
                    return SkinPalette.NEXUS_CYAN;
                case GAP:
                    return SkinPalette.NONE;
                default:
                    return SkinPalette.WARM_WHITE;
            }
        }
    }

    private static final List<Column> STRIP;
    private static final Cell[][] BACK_CELLS = new Cell[ROWS][COLS], FRONT_CELLS = new Cell[ROWS][COLS];

    /** The hull's shape numbers the light rows use. */
    private static final int HULL_WHOLE = 0, HULL_LIGHT_SEAM = 10, HULL_KEEL = 11, HULL_LAMP = 12;

    /**
     * The panels of the materials, rows top (5) to bottom (3), each a string of five cells: the wall's face and the
     * front layer. W whole, S seam, L light seam, K keel, R relief, M lamp, X the material's special, o round, / slope,
     * h half, t step, | rib, c column, n soffit, p coping, O spout, E planter, P pool lip, . nothing.
     */
    private static final String[][] PANEL_BACK = { { "SSSSW", "LLLSK", "WRRWK" }, // hull
        { "SSWWW", "SSOWE", "WWPWW" }, // footing
        { "SSWWM", "SSXWM", "LLLWW" }, // moss bronze
        { "SSWWM", "SSWWM", "LLLWW" }, // white marble
        { "SSWWM", "SSWWM", "LLLXX" }, // ink
        { "SSWWM", "SSXWM", "LLLWW" } }, // paper
        PANEL_FRONT = { { "o./.h", ".|.W.", "t.cn." }, { "o./.p", ".h.t.", "....." }, { "o./..", ".....", "....." },
            { "o./..", "...W.", "...n." }, { "o./..", ".....", "....." }, { "o./..", ".....", "....." } };

    static {
        List<Column> s = new ArrayList<>();
        // must-see pairs of style §8.6 side by side: mana blue - guard blue - star blue - aurora violet
        s.add(accent("mana_fountain", null, "魔力蓝"));
        s.add(accent("dome_generator", null, "守护蓝"));
        s.add(accent("observatory", null, "星蓝"));
        s.add(accent("leap_ring", null, "极光紫"));
        s.add(gap());
        // parchment - white - silver - ice blue, and the revised ice blue and tide teal beside the nexus cyan
        s.add(accent("dual_foil_vault", null, "羊皮纸"));
        s.add(accent("orbital_anchor", null, "白"));
        s.add(accent("droplet_hangar", null, "银"));
        s.add(accent("holo_table", null, "冰蓝"));
        s.add(accent("nexus", null, "中枢青"));
        s.add(accent("root", SkinPalette.WELL, "潮青"));
        s.add(gap());
        // root green - leaf green - mint - root green: three pairs out of three colours
        s.add(accent("root", SkinPalette.DEEP, "根绿"));
        s.add(accent("eco_dome", null, "叶绿"));
        s.add(accent("resonance_gate", null, "薄荷"));
        s.add(accent("root", SkinPalette.DEEP, "根绿"));
        s.add(gap());
        s.add(accent("hunting_ground", null, "琥珀"));
        s.add(accent("root", SkinPalette.CORE, "熔橙"));
        s.add(gap());
        s.add(accent("arcane_court", null, "紫"));
        s.add(accent("blood_covenant", null, "血红"));
        s.add(accent("sophon_array", null, "粉"));
        s.add(accent("weather_station", null, "霞"));
        s.add(accent("source_heart", null, "星白"));
        s.add(gap());
        s.add(new Column(Column.Kind.WARM, null, null, "暖白"));
        s.add(gap());
        // lantern warm - sail gold - evening gold (coherence C9: the warm yellows side by side), then the keels
        s.add(accent("library", null, "纸灯暖"));
        s.add(accent("sky_sails", null, "金"));
        s.add(new Column(Column.Kind.EVENING, null, null, "长庚金"));
        s.add(new Column(Column.Kind.KEEL_MEMORIAL, null, null, "暖白龙骨"));
        s.add(new Column(Column.Kind.KEEL_CYAN, null, null, "中枢青龙骨"));
        if (s.size() != COLS) throw new IllegalStateException("the light rows have " + s.size() + " columns");
        STRIP = Collections.unmodifiableList(s);

        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) {
            BACK_CELLS[r][c] = Cell.AIR_CELL;
            FRONT_CELLS[r][c] = Cell.AIR_CELL;
        }
        // rows 0-2: the light
        for (int c = 0; c < COLS; c++) {
            Column col = STRIP.get(c);
            for (int r = SEAM_ROW; r <= DARK_ROW; r++) BACK_CELLS[r][c] = light(col, r);
        }
        // rows 3-5: the six materials with blocks, and the vanilla references
        Material[] placed = { Material.HULL, Material.FOOTING, Material.MOSS, Material.MARBLE, Material.INK,
            Material.PAPER };
        Glow panel = new Glow(SkinPalette.NONE, true, true, SkinPalette.NEXUS_CYAN);
        for (int p = 0; p < placed.length; p++) for (int i = 0; i < 3; i++) for (int k = 0; k < PANEL; k++) {
            int r = MATERIAL_ROW + 2 - i, c = p * PANEL + k;
            BACK_CELLS[r][c] = panelCell(placed[p], PANEL_BACK[p][i].charAt(k), panel);
            FRONT_CELLS[r][c] = panelCell(placed[p], PANEL_FRONT[p][i].charAt(k), panel);
        }
        String[] vanilla = { "minecraft:stone", "minecraft:quartz_block", "minecraft:log" };
        int[] vanillaMeta = { 0, 0, 2 };
        String[] vanillaRole = { "vanilla:stone", "vanilla:quartz", "vanilla:birch" };
        for (int i = 0; i < 3; i++) for (int r = MATERIAL_ROW; r < RESERVED_ROW; r++)
            BACK_CELLS[r][VANILLA_COL + i] = Cell.vanilla(vanilla[i], vanillaMeta[i], vanillaRole[i]);
        // rows 6-8: the reserved materials; rows 9-11: the fixtures
        Material[] later = { Material.GLAZE, Material.OCHRE, Material.RESONANT, Material.PHASE, Material.MIRROR,
            Material.STARVAULT };
        for (int r = RESERVED_ROW; r < FIXTURE_ROW; r++) for (int c = 0; c < COLS; c++)
            BACK_CELLS[r][c] = c < VANILLA_COL ? Cell.reserved(later[c / PANEL]) : Cell.RESERVED_REF;
        for (int r = FIXTURE_ROW; r < ROWS; r++) for (int c = 0; c < COLS; c++) BACK_CELLS[r][c] = Cell.FIXTURE_CELL;
    }

    private SwatchLayout() {}

    private static Column accent(String key, String form, String zh) {
        return new Column(Column.Kind.ACCENT, key, form, zh);
    }

    private static Column gap() {
        return new Column(Column.Kind.GAP, null, null, "间隔");
    }

    /** A cell of the light rows. */
    private static Cell light(Column col, int r) {
        switch (col.kind) {
            case GAP:
                return Cell.skin(Material.HULL, HULL_WHOLE, null, "gap");
            case EVENING:
                return Cell.skin(
                    Material.HULL,
                    HULL_LIGHT_SEAM,
                    new Glow(SkinPalette.EVENING_GOLD, true, false, SkinPalette.NEXUS_CYAN),
                    "evening");
            case KEEL_MEMORIAL:
                return Cell.skin(
                    Material.HULL,
                    HULL_KEEL,
                    new Glow(SkinPalette.NONE, true, false, SkinPalette.WARM_WHITE),
                    "keel:memorial");
            case KEEL_CYAN:
                return Cell.skin(
                    Material.HULL,
                    HULL_KEEL,
                    new Glow(SkinPalette.NONE, true, false, SkinPalette.NEXUS_CYAN),
                    "keel:cyan");
            default:
                int accent = col.kind == Column.Kind.WARM ? SkinPalette.NONE : col.rgb();
                String role = col.kind == Column.Kind.WARM ? "warm"
                    : "accent:" + col.key + (col.form == null ? "" : "/" + col.form);
                if (r == SEAM_ROW) return Cell.skin(
                    Material.HULL,
                    HULL_LIGHT_SEAM,
                    new Glow(accent, true, false, SkinPalette.NEXUS_CYAN),
                    role + ":seam");
                return Cell.skin(
                    Material.HULL,
                    HULL_LAMP,
                    new Glow(accent, true, r == LIT_ROW, SkinPalette.NEXUS_CYAN),
                    role + (r == LIT_ROW ? ":lit" : ":dark"));
        }
    }

    /** A cell of a material's panel from its pattern character. */
    private static Cell panelCell(Material m, char ch, Glow glow) {
        if (ch == '.') return Cell.AIR_CELL;
        int n = number(m, ch);
        if (n < 0) throw new IllegalStateException(m + " has no shape for '" + ch + "'");
        return Cell.skin(m, n, glow, "panel:" + m.id + ":" + ch);
    }

    /** The shape number a pattern character stands for in a material, or -1. */
    static int number(Material m, char ch) {
        if (m.family == Family.HULL) return hull(ch);
        if (m.family == Family.FOOTING) return footing(ch);
        int slot = "WSo/LMnX".indexOf(ch);
        return slot < 0 ? -1 : m.base + slot;
    }

    private static int hull(char ch) {
        switch (ch) {
            case 'W':
                return 0;
            case 'S':
                return 1;
            case 'o':
                return 2;
            case '/':
                return 3;
            case 'h':
                return 4;
            case 't':
                return 5;
            case '|':
                return 6;
            case 'c':
                return 7;
            case 'L':
                return 10;
            case 'K':
                return 11;
            case 'M':
                return 12;
            case 'n':
                return 13;
            case 'R':
                return 15;
            default:
                return -1;
        }
    }

    private static int footing(char ch) {
        int i = "WSo/htp.nPLOE..R".indexOf(ch);
        return ch == '.' ? -1 : i;
    }

    // ---- reading the table

    /** The accent columns of the light rows, left to right. */
    public static List<Column> strip() {
        return STRIP;
    }

    /** The cell of the wall's face at column {@code c}, row {@code r} (0 bottom). */
    public static Cell back(int c, int r) {
        return BACK_CELLS[r][c];
    }

    /** The cell of the front layer, one cell proud of the face, at column {@code c}, row {@code r}. */
    public static Cell front(int c, int r) {
        return FRONT_CELLS[r][c];
    }

    /** The material of the panel at column {@code c} of the material rows, or null at the vanilla references. */
    public static Material panel(int c) {
        Material[] placed = { Material.HULL, Material.FOOTING, Material.MOSS, Material.MARBLE, Material.INK,
            Material.PAPER };
        return c < VANILLA_COL ? placed[c / PANEL] : null;
    }

    /** The {@code right} offset of column {@code c} in the frame. */
    public static int right(int c) {
        return c - CENTRE;
    }

    // ---- setting it out

    /** One block of the rig: where in the frame and what. */
    public static final class Placement {

        public final int along, right, up;
        public final What what;
        public final Family family;
        public final int number;
        public final String block;
        public final int meta;

        Placement(int along, int right, int up, What what, Family family, int number, String block, int meta) {
            this.along = along;
            this.right = right;
            this.up = up;
            this.what = what;
            this.family = family;
            this.number = number;
            this.block = block;
            this.meta = meta;
        }

        @Override
        public String toString() {
            return what + " "
                + (family != null ? family + ":" + number : block + ":" + meta)
                + " at "
                + along
                + "/"
                + right
                + "/"
                + up;
        }
    }

    /** What a placement sets. */
    public enum What {
        /** Air: the volume is cleared of what stood there. */
        AIR,
        /** A block of the new families. */
        SKIN,
        /** A vanilla block. */
        VANILLA,
        /** A water source. */
        WATER
    }

    private static final int POOL_LIP = 9, FOOTING_WHOLE = 0;

    /**
     * Every cell the rig sets, in order: the ground course and the pool (rows -2 and -1), then the volume above the
     * operator's floor, air where the table has nothing (the reserved rows too), so it stands clear of grass and trees.
     * The marker sign's cell is air here; the command plants the sign after.
     */
    public static List<Placement> placements() {
        List<Placement> out = new ArrayList<>();
        for (int c = -1; c <= COLS; c++) {
            int right = right(c);
            boolean side = c < 0 || c >= COLS;
            out.add(skin(RIM, right, -1, Family.FOOTING, POOL_LIP));
            for (int a = POOL_NEAR; a <= POOL_FAR; a++) {
                if (side) out.add(skin(a, right, -1, Family.FOOTING, POOL_LIP));
                else {
                    out.add(skin(a, right, -2, Family.FOOTING, FOOTING_WHOLE));
                    out.add(new Placement(a, right, -1, What.WATER, null, -1, "minecraft:water", 0));
                }
            }
            for (int a = FRONT; a <= BACK; a++) out.add(skin(a, right, -1, Family.FOOTING, FOOTING_WHOLE));
        }
        for (int up = 0; up < ROWS; up++) for (int c = -1; c <= COLS; c++) for (int a = RIM; a <= BACK; a++) {
            Cell cell = Cell.AIR_CELL;
            if (c >= 0 && c < COLS) {
                if (a == BACK) cell = back(c, up);
                else if (a == FRONT) cell = front(c, up);
            }
            out.add(of(a, right(c), up, cell));
        }
        return out;
    }

    private static Placement skin(int along, int right, int up, Family f, int number) {
        return new Placement(along, right, up, What.SKIN, f, number, null, 0);
    }

    private static Placement of(int along, int right, int up, Cell cell) {
        switch (cell.kind) {
            case SKIN:
                return skin(along, right, up, cell.family, cell.number);
            case VANILLA:
                return new Placement(along, right, up, What.VANILLA, null, -1, cell.block, cell.meta);
            default:
                return new Placement(along, right, up, What.AIR, null, -1, null, 0);
        }
    }

    /**
     * The rig's box in the frame: {along0, right0, up0, along1, right1, up1} inclusive, every cell it may set; no block
     * entity may stand in it (but its own sign).
     */
    public static int[] volume() {
        return new int[] { RIM, right(-1), -2, BACK, right(COLS), ROWS - 1 };
    }

    /** The world box {x0, y0, z0, x1, y1, z1} of the rig in a frame. */
    public static int[] box(ProbeFrame f) {
        int[] v = volume(), a = f.at(v[0], v[1], v[2]), b = f.at(v[3], v[4], v[5]);
        return new int[] { Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]), Math.max(a[0], b[0]),
            Math.max(a[1], b[1]), Math.max(a[2], b[2]) };
    }

    /**
     * The light the swatch gives its cells in a frame: every cell of the wall that has one, keyed by its packed world
     * position ({@link SkinEntry#pack}). The client's chunk renderer reads it for the swatch's cells.
     */
    public static Map<Long, Glow> glows(ProbeFrame f) {
        Map<Long, Glow> out = new HashMap<>();
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) {
            Glow g = BACK_CELLS[r][c].glow;
            if (g != null) {
                int[] p = f.at(BACK, right(c), r);
                out.put(SkinEntry.pack(p[0], p[1], p[2]), g);
            }
            g = FRONT_CELLS[r][c].glow;
            if (g != null) {
                int[] p = f.at(FRONT, right(c), r);
                out.put(SkinEntry.pack(p[0], p[1], p[2]), g);
            }
        }
        return out;
    }

    /**
     * The cells to restore when the wall is cleared, in the frame: {along, right, up, what} with what 0 for air, 1 for
     * grass (the ground course and the pool's top) and 2 for dirt (the pool's bed).
     */
    public static List<int[]> restore() {
        List<int[]> out = new ArrayList<>();
        for (Placement p : placements())
            out.add(new int[] { p.along, p.right, p.up, p.up == -1 ? 1 : p.up == -2 ? 2 : 0 });
        return out;
    }
}
