package com.fluxecho;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The fluxecho lang files are generated from shards ({@code lang/src/<area>.<language>.lang}, the area table
 * {@code lang/src/areas.txt}) by {@code tools/LangMerge.java}. The committed files must be exactly its output, both
 * languages must have the same keys shard by shard, a key lives in one shard only, every key sits in the area its
 * prefix
 * gives, and 0.16.0's keys are all still there.
 */
class LangShardTest {

    static final Path SRC = Paths.get("lang/src");
    static final List<String> LANGUAGES = Arrays.asList("en_US", "zh_CN");

    static List<String> lines(Path p) throws IOException {
        String text = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        List<String> out = new ArrayList<>(Arrays.asList(text.split("\n", -1)));
        if (!out.isEmpty() && out.get(out.size() - 1)
            .isEmpty()) out.remove(out.size() - 1);
        return out;
    }

    /** The area table, read independently of the tool: area to prefixes, in order. */
    static Map<String, List<String>> areas() throws IOException {
        Map<String, List<String>> areas = new LinkedHashMap<>();
        for (String l : lines(SRC.resolve("areas.txt"))) {
            if (l.trim()
                .isEmpty() || l.startsWith("#")) continue;
            String[] parts = l.split(":", 2);
            areas.put(
                parts[0].trim(),
                Arrays.asList(
                    parts[1].trim()
                        .split("\\s+")));
        }
        return areas;
    }

    /** The keys of one shard, in order. */
    static List<String> keys(String area, String lang) throws IOException {
        List<String> keys = new ArrayList<>();
        for (String l : lines(SRC.resolve(area + "." + lang + ".lang")))
            if (!l.isEmpty() && !l.startsWith("#") && l.indexOf('=') > 0) keys.add(l.substring(0, l.indexOf('=')));
        return keys;
    }

    @Test
    @SuppressWarnings("unchecked")
    void theLangFilesAreTheMergeOfTheShards() throws IOException {
        Map<String, String> merged = (Map<String, String>) Tools
            .call("LangMerge", "merge", new Class<?>[] { Path.class }, Paths.get("."));
        assertEquals(2, merged.size());
        for (String lang : LANGUAGES) {
            String path = "src/main/resources/assets/fluxecho/lang/" + lang + ".lang";
            assertTrue(merged.containsKey(path), path);
            assertArrayEquals(
                merged.get(path)
                    .getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(Paths.get(path)),
                path + " is not the merge of lang/src: run java tools/LangMerge.java");
        }
    }

    @Test
    void bothLanguagesHaveTheSameKeysShardByShard() throws IOException {
        for (String area : areas().keySet())
            assertEquals(keys(area, "en_US"), keys(area, "zh_CN"), area + ": the two languages differ");
    }

    @Test
    void aKeyLivesInOneShardOnly() throws IOException {
        for (String lang : LANGUAGES) {
            Map<String, String> seen = new HashMap<>();
            for (String area : areas().keySet()) for (String key : keys(area, lang)) assertNull(
                seen.put(key, area),
                key + " is in " + area + " and in " + seen.get(key) + " (" + lang + ")");
        }
    }

    @Test
    void everyKeySitsInItsArea() throws IOException {
        Map<String, List<String>> areas = areas();
        for (String area : areas.keySet()) for (String key : keys(area, "en_US")) {
            int best = -1;
            List<String> owners = new ArrayList<>();
            for (Map.Entry<String, List<String>> a : areas.entrySet()) for (String p : a.getValue()) {
                int m;
                if (p.endsWith("*")) m = key.startsWith(p.substring(0, p.length() - 1)) ? p.length() - 1 : -1;
                else if (p.endsWith(".")) m = key.startsWith(p) ? p.length() : -1;
                else m = key.equals(p) || key.startsWith(p + ".") ? p.length() : -1;
                if (m < 0 || m < best) continue;
                if (m > best) owners.clear();
                best = m;
                owners.add(a.getKey());
            }
            assertFalse(owners.isEmpty(), key + " matches no prefix in lang/src/areas.txt");
            assertTrue(owners.contains(area), key + " is in " + area + " but belongs to " + owners);
        }
    }

    @Test
    void the016KeysAreAllStillThere() throws IOException {
        List<String> order = new ArrayList<>();
        for (String l : lines(SRC.resolve("order.txt"))) if (!l.isEmpty() && !l.startsWith("#")) order.add(l);
        assertEquals(1155, order.size(), "order.txt is 0.16.0's 1,155 keys and never changes");
        for (String lang : LANGUAGES) {
            List<String> all = new ArrayList<>();
            for (String area : areas().keySet()) all.addAll(keys(area, lang));
            assertTrue(all.containsAll(order), lang + ": a 0.16.0 key is gone");
        }
    }
}
