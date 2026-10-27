package com.fluxecho.logic.skin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.ArcaneCourtShape;
import com.fluxecho.logic.BloodCovenantShape;
import com.fluxecho.logic.DomeGeneratorShape;
import com.fluxecho.logic.DropletHangarShape;
import com.fluxecho.logic.DualFoilVaultShape;
import com.fluxecho.logic.EcoDomeShape;
import com.fluxecho.logic.HoloTableShape;
import com.fluxecho.logic.HuntingGroundShape;
import com.fluxecho.logic.ManaFountainShape;
import com.fluxecho.logic.ObservatoryShape;
import com.fluxecho.logic.ResonanceGateShape;
import com.fluxecho.logic.RootShape;
import com.fluxecho.logic.SkySailsShape;
import com.fluxecho.logic.SophonArrayShape;
import com.fluxecho.logic.WeatherStationShape;

/**
 * The accents against the style's rules (0.17.0, style G2, §3.6, §5.4, §8.6): the values are the style sheet's, the
 * colour difference is CIEDE2000 as the sheet measures it (its own numbers come out again), every accent stands at
 * least 10 from the nexus cyan, and every pair the sheet says can be seen in one frame stands at least 12 apart. The
 * pairs the sheet itself leaves to the swatch wall (§8.6, "待样板墙") are listed one by one in {@link #AWAITING_SWATCH}
 * with the difference the sheet gives; they are the only pairs under 12 anywhere in the table, each must still be
 * under 12 (a pair the swatch wall has settled comes off the list), and none passes without being named here.
 * <p>
 * 0.17 reads the pairs from the style sheet's tables (plan.md erratum 2); from 0.19 on the camera pairs come from the
 * new site plan instead.
 */
class PaletteTest {

    /** The short names this test calls the buildings by: short name to {key, form}. */
    private static final Map<String, String[]> NAMES = new LinkedHashMap<>();

    static {
        String[][] names = { { "nexus", "nexus", null }, { "capsule", "drift_capsule", null },
            { "library", "library", null }, { "eco", "eco_dome", null }, { "mana", "mana_fountain", null },
            { "arcane", "arcane_court", null }, { "blood", "blood_covenant", null }, { "hunt", "hunting_ground", null },
            { "gate", "resonance_gate", null }, { "vault", "dual_foil_vault", null },
            { "dome", "dome_generator", null }, { "tide", "root", "well" }, { "deep", "root", "root" },
            { "core", "root", "core" }, { "sophon", "sophon_array", null }, { "holo", "holo_table", null },
            { "sails", "sky_sails", null }, { "obs", "observatory", null }, { "hangar", "droplet_hangar", null },
            { "weather", "weather_station", null }, { "anchor", "orbital_anchor", null },
            { "heart", "source_heart", null }, { "ring", "leap_ring", null } };
        for (String[] n : names) NAMES.put(n[0], new String[] { n[1], n[2] });
    }

    /** A building by its short name: its accent. */
    private static int c(String name) {
        String[] k = NAMES.get(name);
        if (k == null) throw new IllegalArgumentException(name);
        return k[1] == null ? SkinPalette.accent(k[0]) : SkinPalette.accent(k[0], k[1]);
    }

    private static double de(String a, String b) {
        return SkinPalette.deltaE00(c(a), c(b));
    }

    /** A pair of buildings by their short names, and the difference a design document gives for it (0 if none). */
    private static final class Pair {

        final String a, b, why;
        final double stated;

        Pair(String a, String b, double stated, String why) {
            this.a = a;
            this.b = b;
            this.stated = stated;
            this.why = why;
        }

        boolean is(String x, String y) {
            return a.equals(x) && b.equals(y) || a.equals(y) && b.equals(x);
        }

        @Override
        public String toString() {
            return a + "-" + b;
        }
    }

    /**
     * The pairs the style sheet leaves to the swatch wall (§8.6 "必看的几对", G2): accents under 12 apart that may be
     * seen in one frame, set side by side on the wall and settled in one round with the user. Each is named here with
     * the sheet's own figure and where the two meet; none of them is waved through silently.
     */
    private static final List<Pair> AWAITING_SWATCH = new ArrayList<>();

    static {
        AWAITING_SWATCH.add(
            new Pair(
                "mana",
                "dome",
                4.3,
                "the fountain lies under the zenith pod in view (the one that matters most)"));
        AWAITING_SWATCH.add(new Pair("library", "sails", 6.0, "paper-lamp warm and gold"));
        AWAITING_SWATCH.add(new Pair("gate", "deep", 7.1, "mint and root green"));
        AWAITING_SWATCH.add(new Pair("vault", "anchor", 7.6, "parchment and white"));
        AWAITING_SWATCH.add(new Pair("obs", "ring", 8.0, "the observatory hangs outside the ring's left shoulder"));
        AWAITING_SWATCH.add(new Pair("hangar", "anchor", 8.8, "silver and white"));
        AWAITING_SWATCH.add(new Pair("eco", "deep", 10.1, "leaf green and root green"));
        AWAITING_SWATCH.add(new Pair("eco", "gate", 10.9, "leaf green and mint"));
        AWAITING_SWATCH.add(new Pair("holo", "hangar", 11.1, "ice blue and silver"));
        AWAITING_SWATCH.add(new Pair("hunt", "core", 11.4, "amber and molten orange"));
        AWAITING_SWATCH.add(new Pair("dome", "obs", 11.7, "guard blue and star blue"));
    }

    /**
     * The pairs that are seen in one frame (G2: neighbours by §5.4, which the camera frames hold together): the
     * recommended neighbours of §5.4, the two pairs of the masterplan's §5.1 that §5.4 names, and the swatch wall's
     * pairs of §8.6.
     */
    private static final List<Pair> CO_FRAME = new ArrayList<>();

    static {
        // style §5.4, the recommended neighbours
        String[][] recommended = { { "library", "vault" }, { "library", "eco" }, { "eco", "mana" }, { "mana", "blood" },
            { "arcane", "hunt" }, { "arcane", "blood" }, { "hunt", "gate" }, { "sophon", "holo" }, { "sails", "obs" },
            { "obs", "weather" }, { "weather", "hangar" }, { "hangar", "sails" }, { "heart", "ring" } };
        for (String[] p : recommended) CO_FRAME.add(new Pair(p[0], p[1], 0, "style §5.4 recommended neighbours"));
        // style §5.4, the masterplan's pairs it names
        CO_FRAME.add(new Pair("sophon", "hunt", 0, "masterplan §5.1 (brain and wild)"));
        CO_FRAME.add(new Pair("eco", "heart", 0, "masterplan §5.1 (leaf and star)"));
        CO_FRAME.addAll(AWAITING_SWATCH);
    }

    // ---- the values

    /** style §3.6 as revised on 2026-10-15, with B12's dawn red; the capsule in the nexus cyan (ui §1.3.4). */
    @Test
    void theAccentsAreTheStyleSheets() {
        assertEquals(0x4FE3FF, SkinPalette.NEXUS_CYAN);
        assertEquals(0x4FE3FF, c("nexus"));
        assertEquals(0x4FE3FF, c("capsule"), "the capsule is the nexus at phase 0");
        assertEquals(0xFFD9A0, c("library"), "paper-lamp warm (D8)");
        assertEquals(0x7BE08A, c("eco"));
        assertEquals(0x6AA8FF, c("mana"));
        assertEquals(0xB07CFF, c("arcane"), "violet (D8)");
        assertEquals(0xE0344A, c("blood"));
        assertEquals(0xFF9A3C, c("hunt"));
        assertEquals(0x5CF2C2, c("gate"));
        assertEquals(0xF2EBDD, c("vault"));
        assertEquals(0x7DB8FF, c("dome"));
        assertEquals(0x2E9FB8, c("tide"), "tide teal, changed from #3FC8E8 on 2026-10-15");
        assertEquals(0x5FD3A8, c("deep"));
        assertEquals(0xFF7A3C, c("core"));
        assertEquals(0xFF8CE6, c("sophon"));
        assertEquals(0xA8E0FF, c("holo"), "ice blue, changed from #A8F0FF on 2026-10-15");
        assertEquals(0xFFD27A, c("sails"));
        assertEquals(0xB9C8FF, c("obs"));
        assertEquals(0xD8E4F0, c("hangar"));
        assertEquals(0xFF9E8A, c("weather"), "dawn red (B12)");
        assertEquals(0xFFFFFF, c("anchor"));
        assertEquals(0xF6E7FF, c("heart"));
        assertEquals(0xC9B8FF, c("ring"));
        assertEquals(0xFFFFFF, SkinPalette.RING_ASCENDED, "the ring turns white at the leap");
        assertEquals(0xFFE6B8, SkinPalette.WARM_WHITE);
        assertEquals(0xE8FCFF, SkinPalette.KEEL_CORE);
        assertEquals(0xFFC56B, SkinPalette.EVENING_GOLD);
    }

    /** 23 rows: the capsule, 20 buildings, the root's three forms; in the Buildings rows' order; no row twice. */
    @Test
    void twentyThreeRowsInTheBuildingsOrder() {
        List<SkinPalette.Row> rows = SkinPalette.rows();
        assertEquals(23, rows.size());
        String[] order = { "drift_capsule", "nexus", "library", "eco_dome", "root", "root", "root", "dome_generator",
            "dual_foil_vault", "mana_fountain", "arcane_court", "resonance_gate", "sophon_array", "holo_table",
            "blood_covenant", "hunting_ground", "observatory", "sky_sails", "weather_station", "droplet_hangar",
            "orbital_anchor", "source_heart", "leap_ring" };
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < order.length; i++) {
            SkinPalette.Row r = rows.get(i);
            assertEquals(order[i], r.key, "row " + i);
            assertTrue(seen.add(r.key + "/" + r.form), "twice: " + r);
            assertEquals("root".equals(r.key), r.form != null, "only the root has forms: " + r);
        }
    }

    /** Every module key the 0.16.0 city has finds its accent; a key of no building finds none. */
    @Test
    void everyModuleKeyHasAnAccent() {
        String[] keys = { EcoDomeShape.KEY, DualFoilVaultShape.KEY, ArcaneCourtShape.KEY, BloodCovenantShape.KEY,
            HuntingGroundShape.KEY, ManaFountainShape.KEY, ResonanceGateShape.KEY, DomeGeneratorShape.KEY,
            RootShape.KEY, SophonArrayShape.KEY, HoloTableShape.KEY, SkySailsShape.KEY, ObservatoryShape.KEY,
            DropletHangarShape.KEY, WeatherStationShape.KEY, "library", "nexus" };
        for (String k : keys) assertNotEquals(SkinPalette.NONE, SkinPalette.accent(k), k);
        assertEquals(SkinPalette.NONE, SkinPalette.accent("ascension"), "the leap's placeholder row has no colour");
        assertEquals(SkinPalette.NONE, SkinPalette.accent("no_such_building"));
        assertEquals(SkinPalette.NONE, SkinPalette.accent(null));
        assertEquals(0x123456, SkinPalette.accentOr("no_such_building", "main", 0x123456));
        assertEquals(c("eco"), SkinPalette.accentOr(EcoDomeShape.KEY, "main", 0x123456));
    }

    /** The root's accent follows the shape it stands as: every 0.16.0 and later shape name of each form. */
    @Test
    void theRootsFormFollowsItsShapeName() {
        String[] wells = { "well", "well_20", "well_14", "well_8", "well_v2", "well_v2_20", "well_v2_14", "well_v2_8" };
        String[] deep = { "root", "root_7", "root_in", "root_in_20", "root_in_14", "root_in_8", "root_v2", "root_v2_7",
            "root_v2_in", "root_v2_in_20", "root_v2_in_14", "root_v2_in_8" };
        String[] core = { "core", "core_9", "core_in", "core_in_7", "core_in_w", "core_in_w20", "core_in_w14",
            "core_in_w8" };
        for (String s : wells) assertEquals(c("tide"), SkinPalette.accent("root", s), s);
        for (String s : deep) assertEquals(c("deep"), SkinPalette.accent("root", s), s);
        for (String s : core) assertEquals(c("core"), SkinPalette.accent("root", s), s);
        assertEquals(c("tide"), SkinPalette.accent("root", null), "a root not yet standing is a well");
        assertEquals(c("tide"), SkinPalette.accent("root"));
        // the 0.16.0 code's own names for the three families
        assertEquals(SkinPalette.WELL, RootShape.WELL);
        assertEquals(SkinPalette.DEEP, RootShape.DEEP);
        assertEquals(SkinPalette.CORE, RootShape.CORE);
        assertEquals(c("eco"), SkinPalette.accent("eco_dome", "core"), "only the root has forms");
    }

    // ---- the measure

    /** sRGB to CIELAB under D65: white, mid grey and red as the standard gives them. */
    @Test
    void lab() {
        double[] white = SkinPalette.lab(0xFFFFFF), grey = SkinPalette.lab(0x808080), red = SkinPalette.lab(0xFF0000);
        assertEquals(100, white[0], 1e-4);
        assertEquals(0, white[1], 1e-3);
        assertEquals(0, white[2], 1e-3);
        assertEquals(53.585, grey[0], 1e-3);
        assertEquals(53.2408, red[0], 1e-3);
        assertEquals(80.0925, red[1], 1e-3);
        assertEquals(67.2032, red[2], 1e-3);
        assertEquals(0, SkinPalette.lightness(0x000000), 1e-9);
        // the plaza floor sits between the footing and the hull, L* about 63 (style §7.1 row 3)
        assertEquals(63, 100 * SkinPalette.lightness(0x9C9892), 0.5);
    }

    /** Sharma, Wu and Dalal's CIEDE2000 test data (2005): pairs 1-3, 7, 17-20, 25, 26, 34 and the hue-flip pairs. */
    @Test
    void ciede2000MatchesSharmasTestData() {
        double[][] data = { { 50, 2.6772, -79.7751, 50, 0, -82.7485, 2.0425 },
            { 50, 3.1571, -77.2803, 50, 0, -82.7485, 2.8615 }, { 50, 2.8361, -74.0200, 50, 0, -82.7485, 3.4412 },
            { 50, 0, 0, 50, -1, 2, 2.3669 }, { 50, 2.5, 0, 73, 25, -18, 27.1492 }, { 50, 2.5, 0, 61, -5, 29, 22.8977 },
            { 50, 2.5, 0, 56, -27, -3, 31.9030 }, { 50, 2.5, 0, 58, 24, 15, 19.4535 },
            { 60.2574, -34.0099, 36.2677, 60.4626, -34.1751, 39.4387, 1.2644 },
            { 63.0109, -31.0961, -5.8663, 62.8187, -29.7946, -4.0864, 1.2630 },
            { 22.7233, 20.0904, -46.6940, 23.0331, 14.9730, -42.5619, 2.0373 },
            { 50, 2.49, -0.001, 50, -2.49, 0.0009, 7.1792 }, { 50, -0.001, 2.49, 50, 0.0009, -2.49, 4.8045 } };
        for (double[] d : data) {
            double[] p = { d[0], d[1], d[2] }, q = { d[3], d[4], d[5] };
            assertEquals(d[6], SkinPalette.deltaE00(p, q), 1e-4, java.util.Arrays.toString(d));
            assertEquals(d[6], SkinPalette.deltaE00(q, p), 1e-4, "symmetric");
        }
        assertEquals(0, SkinPalette.deltaE00(0x4FE3FF, 0x4FE3FF), 1e-9);
    }

    /**
     * The style sheet's own figures come out again (§3.6's note on the 2026-10-15 change, §8.6's pairs): the measure
     * here is the one the sheet's script used, so its thresholds mean the same.
     */
    @Test
    void theStyleSheetsFiguresComeOutAgain() {
        // §3.6: the tide teal against the nexus cyan, the mana blue, the guard blue, the root green and the ice blue
        assertEquals(17.8, de("tide", "nexus"), 0.05);
        assertEquals(18.8, de("tide", "mana"), 0.05);
        assertEquals(18.5, de("tide", "dome"), 0.05);
        assertEquals(27.5, de("tide", "deep"), 0.05);
        assertEquals(20.4, de("tide", "holo"), 0.05);
        // §3.6: the ice blue against the nexus cyan, the guard blue, the star blue and the silver
        assertEquals(10.3, de("holo", "nexus"), 0.05);
        assertEquals(12.3, de("holo", "dome"), 0.05);
        assertEquals(13.6, de("holo", "obs"), 0.05);
        assertEquals(11.1, de("holo", "hangar"), 0.05);
        // §3.6: the values they replaced stood only 6.6 and 7.9 from the nexus cyan
        assertEquals(6.6, SkinPalette.deltaE00(0x3FC8E8, SkinPalette.NEXUS_CYAN), 0.05);
        assertEquals(7.9, SkinPalette.deltaE00(0xA8F0FF, SkinPalette.NEXUS_CYAN), 0.05);
        // §8.6: the swatch wall's pairs
        for (Pair p : AWAITING_SWATCH) assertEquals(p.stated, de(p.a, p.b), 0.05, p.toString());
    }

    // ---- the rules

    /**
     * G2: every accent (style §3.6 but the nexus, the root's three forms each counted: 21 values) stands at least 10
     * from the nexus cyan, which only the keels, the nexus and the channels may wear. The nearest is the ice blue.
     */
    @Test
    void everyAccentStandsAtLeastTenFromTheNexusCyan() {
        int counted = 0;
        double nearest = Double.MAX_VALUE;
        String nearestKey = null;
        for (SkinPalette.Row r : SkinPalette.rows()) {
            if (r.key.equals("nexus") || r.key.equals("drift_capsule")) continue;
            counted++;
            double d = SkinPalette.deltaE00(r.rgb, SkinPalette.NEXUS_CYAN);
            assertTrue(d >= 10, r + " is only " + d + " from the nexus cyan");
            if (d < nearest) {
                nearest = d;
                nearestKey = r.key;
            }
        }
        assertEquals(21, counted);
        assertEquals("holo_table", nearestKey);
        assertEquals(10.3, nearest, 0.05);
    }

    /**
     * G2: two accents seen in one frame stand at least 12 apart, unless the style sheet has left the pair to the
     * swatch wall; those are checked against the sheet's figure instead, and named in the message.
     */
    @Test
    void accentsSeenInOneFrameStandAtLeastTwelveApart() {
        List<String> exceptions = new ArrayList<>();
        for (Pair p : CO_FRAME) {
            double d = de(p.a, p.b);
            Pair waiting = awaiting(p.a, p.b);
            if (waiting != null) {
                exceptions.add(String.format("%s %.1f (%s)", waiting, d, waiting.why));
                continue;
            }
            assertTrue(d >= 12, p + " (" + p.why + ") is only " + String.format("%.2f", d) + " apart");
        }
        assertEquals(AWAITING_SWATCH.size(), exceptions.size(), "every awaited pair is a pair seen in one frame");
        System.out.println("PaletteTest: pairs left to the swatch wall (style §8.6): " + exceptions);
    }

    /**
     * Every pair of accents under 12 in the whole table is one the style sheet has named for the swatch wall: a colour
     * change that brings two accents close fails here until it is looked at and named. The nexus's pairs answer to
     * the rule of 10 ({@link #everyAccentStandsAtLeastTenFromTheNexusCyan}); the capsule wears the nexus cyan and is
     * left out of the table.
     */
    @Test
    void everyClosePairInTheTableIsNamed() throws IOException {
        List<SkinPalette.Row> rows = accents();
        List<String> unnamed = new ArrayList<>();
        StringBuilder csv = new StringBuilder("a,b,de00\n");
        for (int i = 0; i < rows.size(); i++) for (int j = i + 1; j < rows.size(); j++) {
            SkinPalette.Row a = rows.get(i), b = rows.get(j);
            double d = SkinPalette.deltaE00(a.rgb, b.rgb);
            csv.append(name(a))
                .append(',')
                .append(name(b))
                .append(',')
                .append(String.format(java.util.Locale.ROOT, "%.2f", d))
                .append('\n');
            // a pair with the nexus answers to the rule of 10 instead (the ice blue's 10.3 is the nearest)
            boolean nexus = a.key.equals("nexus") || b.key.equals("nexus");
            if (!nexus && d < 12 && awaiting(shortName(a), shortName(b)) == null) unnamed.add(a + " - " + b + " " + d);
        }
        assertTrue(unnamed.isEmpty(), "pairs under 12 the style sheet has not named: " + unnamed);
        // the table, for the swatch wall's round: build/reports/palette/de00.csv
        File out = new File("build/reports/palette/de00.csv");
        if (out.getParentFile()
            .isDirectory()
            || out.getParentFile()
                .mkdirs())
            Files.write(
                out.toPath(),
                csv.toString()
                    .getBytes(StandardCharsets.UTF_8));
        assertEquals(
            22 * 21 / 2,
            csv.toString()
                .split("\n").length - 1,
            "22 accents (the nexus and 21 others), every pair once");
    }

    /**
     * The swatch wall's pairs are still open: each is under 12 as the sheet says. When the wall settles a colour and
     * a pair moves past 12, it comes off {@link #AWAITING_SWATCH} (and style §8.6), so the list never hides a pair
     * that no longer needs it.
     */
    @Test
    void theAwaitedPairsAreStillOpen() {
        assertEquals(11, AWAITING_SWATCH.size(), "style §8.6 names eleven");
        for (Pair p : AWAITING_SWATCH) {
            assertTrue(de(p.a, p.b) < 12, p + " is settled: take it off the list");
            assertFalse(p.a.equals(p.b));
        }
    }

    /** The two layers of a glow (style §3.6): a white core at least 0.9 light, an edge ramp tinted by the accent. */
    @Test
    void theGlowsTwoLayers() {
        assertEquals(0xFFFFFF, SkinPalette.GLOW_CORE);
        assertTrue(SkinPalette.lightness(SkinPalette.GLOW_CORE) >= SkinPalette.GLOW_CORE_MIN_LIGHTNESS);
        assertEquals(0.9, SkinPalette.GLOW_CORE_MIN_LIGHTNESS, 0);
        assertTrue(SkinPalette.GLOW_CORE_PX >= 1 && SkinPalette.GLOW_CORE_PX <= 2);
        assertTrue(SkinPalette.GLOW_EDGE_PX >= 1 && SkinPalette.GLOW_EDGE_PX <= 2);
        // grey ramp, inner to outer, darkening
        assertEquals(SkinPalette.GLOW_EDGE_INNER, SkinPalette.glowEdgeGrey(0));
        assertEquals(SkinPalette.GLOW_EDGE_OUTER, SkinPalette.glowEdgeGrey(SkinPalette.GLOW_EDGE_PX - 1));
        for (int s = 0; s < SkinPalette.GLOW_EDGE_PX; s++) {
            int g = SkinPalette.glowEdgeGrey(s);
            assertEquals(g & 255, g >> 16 & 255, "grey");
            assertEquals(g & 255, g >> 8 & 255, "grey");
            if (s > 0) assertTrue((g & 255) <= (SkinPalette.glowEdgeGrey(s - 1) & 255), "darkening outwards");
        }
        // tinted, the inner edge is the accent itself, the outer a darker shade of it
        for (SkinPalette.Row r : SkinPalette.rows()) {
            assertEquals(r.rgb, SkinPalette.glowEdge(r.rgb, 0), r.toString());
            assertTrue(
                SkinPalette.lightness(SkinPalette.glowEdge(r.rgb, 1)) < SkinPalette.lightness(r.rgb)
                    || r.rgb == 0x000000,
                r.toString());
        }
        assertEquals(0x4FE3FF, SkinPalette.tint(0xFFFFFF, 0x4FE3FF));
        assertEquals(0x000000, SkinPalette.tint(0x000000, 0x4FE3FF));
        assertEquals(0x7F7F7F, SkinPalette.tint(0x7F7F7F, 0xFFFFFF));
    }

    // ---- helpers

    /** The pair named for the swatch wall, either way round, or null. */
    private static Pair awaiting(String a, String b) {
        for (Pair p : AWAITING_SWATCH) if (p.is(a, b)) return p;
        return null;
    }

    /** Every row but the capsule (it is the nexus's colour by design): 22 accents. */
    private static List<SkinPalette.Row> accents() {
        List<SkinPalette.Row> l = new ArrayList<>();
        for (SkinPalette.Row r : SkinPalette.rows()) if (!r.key.equals("drift_capsule")) l.add(r);
        return l;
    }

    private static String name(SkinPalette.Row r) {
        return r.form == null ? r.key : r.key + "/" + r.form;
    }

    /** A row's short name in this test. */
    private static String shortName(SkinPalette.Row r) {
        for (Map.Entry<String, String[]> e : NAMES.entrySet()) {
            String[] k = e.getValue();
            if (k[0].equals(r.key) && java.util.Objects.equals(k[1], r.form)) return e.getKey();
        }
        throw new IllegalStateException("no short name for " + r);
    }
}
