package com.fluxecho.logic.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.probe.SwatchLayout.Cell;
import com.fluxecho.logic.probe.SwatchLayout.Column;
import com.fluxecho.logic.probe.SwatchLayout.Material;
import com.fluxecho.logic.probe.SwatchLayout.Placement;
import com.fluxecho.logic.probe.SwatchLayout.What;
import com.fluxecho.logic.shape.Family;
import com.fluxecho.logic.shape.Form;
import com.fluxecho.logic.shape.FormCodes;
import com.fluxecho.logic.shape.Nbhd;
import com.fluxecho.logic.shape.Quad;
import com.fluxecho.logic.shape.ShapeGeo;
import com.fluxecho.logic.shape.SkinBlocks;
import com.fluxecho.logic.skin.SkinEntry;
import com.fluxecho.logic.skin.SkinPalette;

/**
 * The swatch wall's cell table against style FINAL §8.6 (0.17.0, refactor FINAL F06, DECISIONS B11): 33 × 12; the
 * twenty accents of §3.6 (22 colours with the root's three forms) each as a light seam, a lit lamp and a dark lamp; the
 * must-see pairs side by side with the ΔE00 §8.6 gives; the revised tide teal and ice blue beside the nexus cyan; the
 * memorial's warm-white keel with the evening gold beside it and the cyan keel; every material §3.5 has blocks for in
 * 0.17.0 with every shape the first batch gives a code, the shaped ones standing proud so that each shows its form; the
 * vanilla stone, quartz and birch; the six later materials and the three fixture rows reserved; a mirror pool in front.
 * The cell table below is the swatch's 格位表 (copied into the F06 report): it fails on any cell that moves.
 */
class SwatchLayoutTest {

    /**
     * The wall's face, top row (11) first. Light rows: L light seam, + lit lamp, - dark lamp, K keel, W plain hull.
     * Material rows: the panel's letters (W whole, S seam, L light seam, K keel, R relief, M lamp, X special, O spout,
     * E planter, P pool lip), then s stone, q quartz, b birch. r reserved material, f fixture.
     */
    private static final String[] FACE = { "fffffffffffffffffffffffffffffffff", "fffffffffffffffffffffffffffffffff",
        "fffffffffffffffffffffffffffffffff", "rrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr", "rrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr",
        "rrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr", "SSSSWSSWWWSSWWMSSWWMSSWWMSSWWMsqb", "LLLSKSSOWESSXWMSSWWMSSWWMSSXWMsqb",
        "WRRWKWWPWWLLLWWLLLWWLLLXXLLLWWsqb", "----W------W----W--W-----W-W--LKK", "++++W++++++W++++W++W+++++W+W++LKK",
        "LLLLWLLLLLLWLLLLWLLWLLLLLWLWLLLKK" };
    /** The front layer, one cell proud: o round, / slope, h half, t step, | rib, c column, n soffit, p coping. */
    private static final String[] PROUD = { ".................................", ".................................",
        ".................................", ".................................", ".................................",
        ".................................", "o./.ho./.po./..o./..o./..o./.....", ".|.W..h.t.........W..............",
        "t.cn..............n..............", ".................................", ".................................",
        "................................." };
    /** The light rows' colours left to right (style §3.6 names; - a gap, 暖白 no accent). */
    private static final String[] STRIP = { "魔力蓝", "守护蓝", "星蓝", "极光紫", "-", "羊皮纸", "白", "银", "冰蓝", "中枢青", "潮青", "-",
        "根绿", "叶绿", "薄荷", "根绿", "-", "琥珀", "熔橙", "-", "紫", "血红", "粉", "霞", "星白", "-", "暖白", "-", "纸灯暖", "金", "长庚金",
        "暖白龙骨", "中枢青龙骨" };

    /** A cell's letter in the tables above, from its role. */
    private static char letter(Cell c) {
        String role = c.role;
        if (c.kind == Cell.Kind.AIR) return '.';
        if (c.kind == Cell.Kind.RESERVED) return 'r';
        if (c.kind == Cell.Kind.FIXTURE) return 'f';
        if (role.startsWith("vanilla:")) return role.charAt(8);
        if (role.startsWith("panel:")) return role.charAt(role.length() - 1);
        if (role.equals("gap")) return 'W';
        if (role.startsWith("keel")) return 'K';
        if (role.equals("evening") || role.endsWith(":seam")) return 'L';
        if (role.endsWith(":lit")) return '+';
        if (role.endsWith(":dark")) return '-';
        return '?';
    }

    @Test
    void theWallIsThirtyThreeByTwelveCellByCell() {
        assertEquals(33, SwatchLayout.COLS);
        assertEquals(12, SwatchLayout.ROWS);
        for (int i = 0; i < 12; i++) {
            int r = 11 - i;
            StringBuilder face = new StringBuilder(), proud = new StringBuilder();
            for (int c = 0; c < 33; c++) {
                face.append(letter(SwatchLayout.back(c, r)));
                proud.append(letter(SwatchLayout.front(c, r)));
            }
            assertEquals(FACE[i], face.toString(), "the face, row " + r);
            assertEquals(PROUD[i], proud.toString(), "the front layer, row " + r);
        }
        List<String> names = new ArrayList<>();
        for (Column c : SwatchLayout.strip()) names.add(c.kind == Column.Kind.GAP ? "-" : c.zh);
        assertEquals(java.util.Arrays.asList(STRIP), names);
    }

    @Test
    void everyAccentShowsAsASeamALitLampAndADarkLamp() {
        Set<Integer> accents = new HashSet<>(), palette = new HashSet<>();
        for (SkinPalette.Row row : SkinPalette.rows()) palette.add(row.rgb);
        for (int c = 0; c < 33; c++) {
            Column col = SwatchLayout.strip()
                .get(c);
            if (col.kind != Column.Kind.ACCENT && col.kind != Column.Kind.WARM) continue;
            int accent = col.kind == Column.Kind.WARM ? SkinPalette.NONE : col.rgb();
            if (col.kind == Column.Kind.ACCENT) accents.add(accent);
            Cell seam = SwatchLayout.back(c, 0), lit = SwatchLayout.back(c, 1), dark = SwatchLayout.back(c, 2);
            assertEquals(Form.LIGHT_SEAM, seam.form());
            assertEquals(Form.LAMP, lit.form());
            assertEquals(Form.LAMP, dark.form());
            for (Cell k : new Cell[] { seam, lit, dark }) {
                assertEquals(Material.HULL, k.material, "hull light: only the light differs");
                assertEquals(accent, k.glow.accent);
                assertTrue(k.glow.awake);
            }
            assertFalse(seam.glow.lamps);
            assertTrue(lit.glow.lamps, "lit at every hour, so each screenshot has both");
            assertFalse(dark.glow.lamps);
        }
        assertEquals(22, accents.size(), "twenty buildings, the root in three forms");
        assertEquals(palette, accents, "every accent of SkinPalette, nothing else");
    }

    @Test
    void theMustSeePairsStandSideBySide() {
        Object[][] pairs = { { "魔力蓝", "守护蓝", 4.3 }, { "纸灯暖", "金", 6.0 }, { "薄荷", "根绿", 7.1 }, { "羊皮纸", "白", 7.6 },
            { "星蓝", "极光紫", 8.0 }, { "银", "白", 8.8 }, { "叶绿", "根绿", 10.1 }, { "叶绿", "薄荷", 10.9 }, { "冰蓝", "银", 11.1 },
            { "琥珀", "熔橙", 11.4 }, { "守护蓝", "星蓝", 11.7 } };
        List<Column> s = SwatchLayout.strip();
        for (Object[] p : pairs) {
            boolean beside = false;
            for (int c = 0; c + 1 < s.size(); c++) {
                String a = s.get(c).zh, b = s.get(c + 1).zh;
                if (a.equals(p[0]) && b.equals(p[1]) || a.equals(p[1]) && b.equals(p[0])) {
                    beside = true;
                    double de = SkinPalette.deltaE00(
                        s.get(c)
                            .rgb(),
                        s.get(c + 1)
                            .rgb());
                    assertEquals((Double) p[2], de, 0.06, p[0] + " – " + p[1]);
                    assertTrue(de < 12, "a must-see pair is closer than 12");
                }
            }
            assertTrue(beside, p[0] + " beside " + p[1]);
        }
        // the revised tide teal and ice blue next to the nexus cyan, once
        boolean three = false;
        for (int c = 0; c + 2 < s.size(); c++) {
            Set<String> w = new HashSet<>();
            for (int k = 0; k < 3; k++) w.add(s.get(c + k).zh);
            if (w.contains("潮青") && w.contains("冰蓝") && w.contains("中枢青")) three = true;
        }
        assertTrue(three);
        assertEquals(0x2E9FB8, SkinPalette.accent("root", SkinPalette.WELL), "the revised tide teal");
        assertEquals(0xA8E0FF, SkinPalette.accent("holo_table"), "the revised ice blue");
    }

    @Test
    void theMemorialKeelHasTheEveningGoldBesideIt() {
        List<Column> s = SwatchLayout.strip();
        int evening = -1, memorial = -1, cyan = -1, gold = -1;
        for (int c = 0; c < s.size(); c++) {
            switch (s.get(c).kind) {
                case EVENING -> evening = c;
                case KEEL_MEMORIAL -> memorial = c;
                case KEEL_CYAN -> cyan = c;
                default -> {
                    if ("金".equals(s.get(c).zh)) gold = c;
                }
            }
        }
        assertEquals(1, Math.abs(evening - memorial), "the gold stroke beside the memorial keel");
        assertEquals(1, Math.abs(evening - gold), "the evening gold beside the sail gold (coherence C9)");
        assertTrue(cyan >= 0);
        for (int r = 0; r < 3; r++) {
            Cell m = SwatchLayout.back(memorial, r), c = SwatchLayout.back(cyan, r), e = SwatchLayout.back(evening, r);
            assertEquals(Form.KEEL, m.form());
            assertEquals(SkinPalette.WARM_WHITE, m.glow.keel, "§6.7: the memorial keel turns warm white");
            assertEquals(Form.KEEL, c.form());
            assertEquals(SkinPalette.NEXUS_CYAN, c.glow.keel);
            assertEquals(Form.LIGHT_SEAM, e.form());
            assertEquals(SkinPalette.EVENING_GOLD, e.glow.accent);
        }
        assertEquals(0xFFC56B, SkinPalette.EVENING_GOLD);
    }

    /** The form a panel letter stands for in a material. */
    private static Form expected(Material m, char ch) {
        switch (ch) {
            case 'W':
                return Form.WHOLE;
            case 'S':
                return Form.SEAM;
            case 'L':
                return Form.LIGHT_SEAM;
            case 'K':
                return Form.KEEL;
            case 'R':
                return Form.RELIEF;
            case 'M':
                return Form.LAMP;
            case 'o':
                return Form.ROUND;
            case '/':
                return Form.SLOPE;
            case 'h':
                return Form.HALF;
            case 't':
                return Form.STEP;
            case '|':
                return Form.RIB;
            case 'c':
                return Form.COLUMN;
            case 'n':
                return Form.SOFFIT;
            case 'p':
                return Form.COPING;
            case 'O':
                return Form.SPOUT;
            case 'E':
                return Form.PLANTER;
            case 'P':
                return Form.POOL_LIP;
            case 'X':
                return m == Material.MOSS ? Form.WINDOW : m == Material.INK ? Form.GRATE : Form.WHOLE;
            default:
                return null;
        }
    }

    @Test
    void theMaterialsWithBlocksStandInTheirPanels() {
        Material[] order = { Material.HULL, Material.FOOTING, Material.MOSS, Material.MARBLE, Material.INK,
            Material.PAPER };
        for (int r = 3; r <= 5; r++) for (int c = 0; c < 30; c++) {
            Material m = order[c / 5];
            assertEquals(m, SwatchLayout.panel(c));
            for (Cell cell : new Cell[] { SwatchLayout.back(c, r), SwatchLayout.front(c, r) }) {
                if (cell.kind == Cell.Kind.AIR) continue;
                assertEquals(Cell.Kind.SKIN, cell.kind);
                assertEquals(m, cell.material);
                assertEquals(m.family, cell.family);
                char ch = letter(cell);
                assertEquals(expected(m, ch), cell.form(), m + " '" + ch + "' at " + c + ", " + r);
                assertEquals(
                    m.name()
                        .toLowerCase(java.util.Locale.ROOT),
                    SkinBlocks.material(cell.family, cell.number),
                    "the cell is of its panel's material");
            }
        }
        // every shape the first batch gives a code stands on the wall
        Set<String> onWall = new HashSet<>();
        for (int r = 0; r < 12; r++) for (int c = 0; c < 33; c++)
            for (Cell cell : new Cell[] { SwatchLayout.back(c, r), SwatchLayout.front(c, r) })
                if (cell.kind == Cell.Kind.SKIN) onWall.add(cell.family + ":" + cell.number);
        int codes = 0;
        for (int code = 0; code < 256; code++) {
            Family f = FormCodes.family(code);
            if (f == null) continue;
            codes++;
            assertTrue(
                onWall.contains(f + ":" + FormCodes.number(code)),
                "code " + code + " " + f + " shape " + FormCodes.number(code));
        }
        assertEquals(36, codes, "the 36 shape codes of the first batch");
        // white marble has no code yet: its shapes stand on the wall all the same
        for (int n : new int[] { 8, 9, 10, 11, 12, 13, 14 }) assertTrue(onWall.contains(Family.SKIN_GARDEN + ":" + n));
    }

    @Test
    void theVanillaReferencesTheReservedMaterialsAndTheFixtures() {
        String[] blocks = { "minecraft:stone", "minecraft:quartz_block", "minecraft:log" };
        int[] metas = { 0, 0, 2 };
        for (int i = 0; i < 3; i++) for (int r = 3; r <= 5; r++) {
            Cell c = SwatchLayout.back(30 + i, r);
            assertEquals(Cell.Kind.VANILLA, c.kind);
            assertEquals(blocks[i], c.block);
            assertEquals(metas[i], c.meta, "birch: log meta 2");
        }
        Material[] later = { Material.GLAZE, Material.OCHRE, Material.RESONANT, Material.PHASE, Material.MIRROR,
            Material.STARVAULT };
        for (int r = 6; r <= 8; r++) for (int c = 0; c < 30; c++) {
            Cell cell = SwatchLayout.back(c, r);
            assertEquals(Cell.Kind.RESERVED, cell.kind);
            assertEquals(later[c / 5], cell.material);
            assertFalse(cell.material.placed(), "its family comes with 0.20.0");
        }
        for (int r = 9; r <= 11; r++)
            for (int c = 0; c < 33; c++) assertEquals(Cell.Kind.FIXTURE, SwatchLayout.back(c, r).kind);
        assertEquals(12, Material.values().length, "style §3.5: twelve materials");
        int placed = 0;
        for (Material m : Material.values()) if (m.placed()) placed++;
        assertEquals(6, placed, "0.17.0 has blocks for six");
    }

    // ---- the wall as the world sees it

    private static final ProbeFrame FRAME = new ProbeFrame(0, 64, 0, 0, 1);

    /** The world the placements make, cell by cell as neighbourhood codes. */
    private static Map<Long, Integer> world() {
        Map<Long, Integer> w = new HashMap<>();
        for (Placement p : SwatchLayout.placements()) {
            int[] at = FRAME.at(p.along, p.right, p.up);
            long k = SkinEntry.pack(at[0], at[1], at[2]);
            if (w.containsKey(k)) fail("two placements in one cell: " + p);
            int code;
            switch (p.what) {
                case SKIN -> code = Nbhd.ours(p.family.form(p.number), false) | (p.family.ordinal() << 12);
                case VANILLA -> code = Nbhd.SOLID;
                case WATER -> code = Nbhd.WATER;
                default -> code = Nbhd.OPEN;
            }
            w.put(k, code);
        }
        return w;
    }

    private static Nbhd nbhd(Map<Long, Integer> w, int[] at) {
        int[] c = new int[27];
        int self = w.get(SkinEntry.pack(at[0], at[1], at[2])) >> 12;
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dy == 0 && dz == 0) continue;
            Integer v = w.get(SkinEntry.pack(at[0] + dx, at[1] + dy, at[2] + dz));
            int code = v == null ? Nbhd.OPEN : v;
            Form f = code >= 4 && (code & 0xFFF) >= 4 ? Form.of(((code & 0xFF) >> 2) - 1) : null;
            c[Nbhd.index(dx, dy, dz)] = f == null ? code & 3 : Nbhd.ours(f, (code >> 12) == self);
        }
        return Nbhd.of(c);
    }

    private static boolean has(List<Quad> quads, Quad.Surface s) {
        for (Quad q : quads) if (q.surface == s) return true;
        return false;
    }

    @Test
    void everyShapeStandingProudShowsItsForm() {
        Map<Long, Integer> w = world();
        int checked = 0;
        for (int r = 0; r < 12; r++) for (int c = 0; c < 33; c++) {
            Cell cell = SwatchLayout.front(c, r);
            if (cell.kind != Cell.Kind.SKIN) continue;
            int[] at = FRAME.at(SwatchLayout.FRONT, SwatchLayout.right(c), r);
            Form f = cell.form();
            Nbhd n = nbhd(w, at);
            List<Quad> q = ShapeGeo.quads(f, n);
            String where = cell + " at " + c + ", " + r;
            switch (f) {
                case ROUND, COPING -> assertTrue(has(q, Quad.Surface.CYLINDER), where + ": rounded edges");
                case SLOPE -> {
                    boolean slanted = false;
                    for (Quad k : q) if (k.surface == Quad.Surface.PLANE && Math.abs(k.normal[1]) > 0.3
                        && Math.abs(k.normal[1]) < 0.95) slanted = true;
                    assertTrue(slanted, where + ": a slanted face");
                }
                case SOFFIT -> assertTrue(has(q, Quad.Surface.CONCAVE), where + ": its concave shoulder");
                case HALF, STEP -> {
                    for (double[] b : ShapeGeo.boxes(f, n)) assertEquals(0.5, b[4], 1e-9, where + ": half high");
                }
                case RIB, COLUMN -> {
                    double vol = 0;
                    for (double[] b : ShapeGeo.occluders(f, n)) vol += (b[3] - b[0]) * (b[4] - b[1]) * (b[5] - b[2]);
                    assertTrue(vol < 0.5, where + ": a thin piece");
                }
                case WHOLE -> {
                    // a lintel over a soffit
                    Cell below = SwatchLayout.front(c, r - 1);
                    assertEquals(Form.SOFFIT, below.form(), where + ": a whole block proud of the wall is a lintel");
                }
                default -> fail(where + ": " + f + " should be flush");
            }
            checked++;
        }
        assertEquals(23, checked, "the shapes standing proud");
    }

    @Test
    void theGlowsOfThePanelsAreNotHiddenBehindTheProudShapes() {
        for (int p = 0; p < 6; p++) {
            boolean seam = false;
            for (int r = 3; r <= 5; r++) for (int k = 0; k < 5; k++) {
                int c = p * 5 + k;
                Cell back = SwatchLayout.back(c, r), front = SwatchLayout.front(c, r);
                Form f = back.form();
                if (f == Form.KEEL || f == Form.LAMP)
                    assertEquals(Cell.Kind.AIR, front.kind, back + " at " + c + ", " + r + " is in plain sight");
                if (f == Form.LIGHT_SEAM && front.kind == Cell.Kind.AIR) seam = true;
            }
            // the footing's light is its step's nose and its pool lip (style §3.5 M2), no light seam
            if (p != 1) assertTrue(seam, "panel " + p + " shows a light seam");
        }
    }

    @Test
    void theMirrorPoolAndTheGroundCourse() {
        int water = 0, lips = 0, bed = 0, course = 0;
        for (Placement p : SwatchLayout.placements()) {
            if (p.what == What.WATER) {
                water++;
                assertEquals(-1, p.up, "the water at the ground's level");
                assertTrue(p.along >= SwatchLayout.POOL_NEAR && p.along <= SwatchLayout.POOL_FAR);
                assertTrue(Math.abs(p.right) <= 16);
            }
            if (p.what == What.SKIN && p.family == Family.FOOTING && p.number == 9 && p.up == -1) lips++;
            if (p.what == What.SKIN && p.family == Family.FOOTING && p.number == 0 && p.up == -2) bed++;
            if (p.what == What.SKIN && p.family == Family.FOOTING && p.number == 0 && p.up == -1) course++;
        }
        assertEquals(33 * 3, water, "a pool as wide as the wall, three deep");
        assertEquals(99, bed, "on a footing bed");
        assertEquals(35 + 6, lips, "rimmed with pool lips in front and at the ends");
        assertEquals(35 * 2, course, "a footing course under the wall and its front layer");
        // every placement in the volume, the marker's cell air over a lip
        int[] v = SwatchLayout.volume();
        Set<String> seen = new HashSet<>();
        for (Placement p : SwatchLayout.placements()) {
            assertTrue(
                p.along >= v[0] && p.along <= v[3]
                    && p.right >= v[1]
                    && p.right <= v[4]
                    && p.up >= v[2]
                    && p.up <= v[5],
                p.toString());
            assertTrue(seen.add(p.along + "/" + p.right + "/" + p.up), "once: " + p);
        }
        ProbeMarker.Kind k = ProbeMarker.Kind.SWATCH;
        assertTrue(seen.contains(k.along + "/" + k.right + "/0"));
        for (Placement p : SwatchLayout.placements()) {
            if (p.along == k.along && p.right == k.right && p.up == 0) assertEquals(What.AIR, p.what);
            if (p.along == k.along && p.right == k.right && p.up == -1)
                assertEquals(9, p.number, "the sign stands on a lip");
        }
        assertEquals(
            35 * 6 * 12 + 35 * 6 + 99,
            SwatchLayout.placements()
                .size(),
            "the volume above the floor, the ground course and the pool's bed");
    }

    @Test
    void theClientTintsExactlyTheCellsWithLight() {
        Map<Long, SwatchLayout.Glow> g = SwatchLayout.glows(FRAME);
        int light = 0, panels = 0;
        for (int r = 0; r < 12; r++) for (int c = 0; c < 33; c++) {
            Cell b = SwatchLayout.back(c, r);
            int[] at = FRAME.at(SwatchLayout.BACK, SwatchLayout.right(c), r);
            SwatchLayout.Glow glow = g.get(SkinEntry.pack(at[0], at[1], at[2]));
            if (r <= 2 && !"gap".equals(b.role)) {
                assertNotNull(glow, b.role);
                light++;
            } else if (r <= 2) assertNull(glow, "a gap is plain hull");
            if (r >= 3 && r <= 5 && b.kind == Cell.Kind.SKIN) {
                assertNotNull(glow);
                assertTrue(glow.lamps, "a panel's lamp is lit");
                panels++;
            }
        }
        assertEquals(3 * (33 - 6), light);
        assertEquals(90, panels);
        int proud = 0;
        for (int r = 0; r < 12; r++)
            for (int c = 0; c < 33; c++) if (SwatchLayout.front(c, r).kind == Cell.Kind.SKIN) proud++;
        assertEquals(light + panels + proud, g.size());
    }
}
