package com.fluxecho;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.fluxecho.config.CampusConfig;
import com.fluxecho.config.RenderConfig;
import com.fluxecho.config.StyleConfig;
import com.fluxecho.plan.Csv;

/**
 * The config file: version comparison and the migrations of {@link Config#upgrade}, and (0.17.0) that every setting
 * 0.16.0 wrote is still written with the same key, type, default and bounds now that each section is its own class
 * ({@code config/fluxecho-0.16.0.cfg} is the file 0.16.0 writes on a first start, frozen).
 */
class ConfigTest {

    static Map<String, ConfigFiles.Setting> settingsOf016() throws IOException {
        try (InputStream in = ConfigTest.class.getResourceAsStream("/config/fluxecho-0.16.0.cfg")) {
            assertTrue(in != null, "the frozen config/fluxecho-0.16.0.cfg is missing");
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0;) b.write(buf, 0, n);
            return ConfigFiles.read(new String(b.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    /** 0.17.0's three keys, on the safe side until the player has looked (refactor 4.6, ids.md section 7). */
    @Test
    void theKeysOf017() throws IOException {
        Map<String, ConfigFiles.Setting> now = ConfigFiles.read(ConfigFiles.fresh("keys017.cfg"));
        ConfigFiles.Setting occlusion = now.get("render.occlusion"), sky = now.get("render.skyLayer"),
            hull = now.get("style.hullBrightness");
        assertEquals("S", occlusion.type);
        assertEquals("gate", occlusion.value, "the per-piece test until probe depth says the depth buffer works");
        assertEquals("S", sky.type);
        assertEquals("world", sky.value, "world-fixed sky lights until probe sky says the sky layer works");
        assertEquals("D", hull.type);
        assertEquals("1.0", hull.value);
        assertEquals("[range: 0.85 ~ 1.0, default: 1.0]", hull.bounds);
        assertEquals("gate", RenderConfig.occlusion);
        assertEquals("world", RenderConfig.skyLayer);
        assertEquals(1.0, StyleConfig.hullBrightness);
    }

    /**
     * A key 0.16.0 did not write is in the ID ledger's config rows (docs/dev/ids.csv, section 7) and not one of its
     * voided keys: a key is named there by itself, or its section is named whole ({@code eco.*}), or the row lists the
     * section and the key.
     */
    @Test
    void everyNewKeyIsInTheLedger() throws IOException {
        List<String> live = new ArrayList<>(), dead = new ArrayList<>();
        for (List<String> r : Csv
            .parse(new String(Files.readAllBytes(Paths.get("docs/dev/ids.csv")), StandardCharsets.UTF_8)))
            if (r.get(0)
                .equals("config"))
                (r.get(6)
                    .equals("作废") ? dead : live).add(r.get(2));
        Map<String, ConfigFiles.Setting> old = settingsOf016(), now = ConfigFiles.read(ConfigFiles.fresh("ledger.cfg"));
        for (String key : now.keySet()) {
            if (old.containsKey(key)) continue;
            String section = key.substring(0, key.indexOf('.')), name = key.substring(key.indexOf('.') + 1);
            Pattern word = Pattern.compile("(?<![\\w.])" + Pattern.quote(name) + "(?![\\w])");
            boolean listed = false;
            for (String row : live) listed |= row.contains(key) || row.contains(section + ".*")
                || row.contains(section) && word.matcher(row)
                    .find();
            assertTrue(listed, key + " is not in the ledger's config rows (docs/dev/ids.md section 7)");
            for (String row : dead) assertFalse(row.contains(key), key + " is a voided key");
        }
    }

    @Test
    void every016SettingKeepsItsKeyTypeDefaultAndBounds() throws IOException {
        Map<String, ConfigFiles.Setting> old = settingsOf016(), now = ConfigFiles.read(ConfigFiles.fresh("now.cfg"));
        assertEquals(101, old.size(), "0.16.0 writes 101 settings in 13 categories");
        for (Map.Entry<String, ConfigFiles.Setting> e : old.entrySet()) {
            ConfigFiles.Setting was = e.getValue(), is = now.get(e.getKey());
            assertTrue(is != null, e.getKey() + " is gone");
            assertEquals(was.type, is.type, e.getKey() + ": type");
            assertEquals(was.value, is.value, e.getKey() + ": default");
            assertEquals(was.bounds, is.bounds, e.getKey() + ": bounds and default in the comment");
        }
    }

    @Test
    void olderVersions() {
        assertTrue(Config.older(null, "0.5.0"));
        assertTrue(Config.older("", "0.5.0"));
        assertTrue(Config.older("0.4.1", "0.5.0"));
        assertTrue(Config.older("0.5.0", "0.6.0"));
        assertTrue(Config.older("0.9", "0.10.0"));
        assertFalse(Config.older("0.5.0", "0.5.0"));
        assertFalse(Config.older("0.6.0", "0.5.0"));
        assertFalse(Config.older("1.0", "0.6.0"));
    }

    @Test
    void campusDefaultsOf0101() {
        assertTrue(CampusConfig.buildClearAll, "the builder empties what it clears (the player asked for it)");
        assertEquals(4, CampusConfig.buildFillDepth, "grading fills four deep (0.10.0: two)");
        // a file that kept 0.10.0's default gets the new one; a depth a player chose stays
        assertTrue(Config.bumpsFillDepth("2"));
        assertTrue(Config.bumpsFillDepth(" 2 "));
        assertFalse(Config.bumpsFillDepth("3"));
        assertFalse(Config.bumpsFillDepth("0"));
        assertFalse(Config.bumpsFillDepth(null));
        assertTrue(Config.older("0.10.0", "0.10.1"));
    }

    /**
     * 0.15.0: new campuses keep room for both outer rings (312); a file that kept the old default 144 gets the new
     * one, a spacing a player chose stays.
     */
    @Test
    void campusSpacingOf0150() {
        assertEquals(312, CampusConfig.buildMinSpacing);
        assertEquals(com.fluxecho.logic.CampusSplit.DEFAULT_SPACING, CampusConfig.buildMinSpacing);
        assertTrue(Config.bumpsMinSpacing("144"));
        assertTrue(Config.bumpsMinSpacing(" 144 "));
        assertFalse(Config.bumpsMinSpacing("200"));
        assertFalse(Config.bumpsMinSpacing("0"));
        assertFalse(Config.bumpsMinSpacing(null));
        assertTrue(Config.older("0.10.1", "0.15.0"));
        assertFalse(Config.older("0.15.0", "0.15.0"));
    }
}
