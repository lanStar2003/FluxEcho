package com.fluxecho;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The language files: English and Chinese have the same keys, each key once and with the same placeholders, and every
 * key the code names outright is in both (a missing one shows its key in the game instead of a text).
 */
class LangTest {

    private static final Path LANG = Paths.get("src", "main", "resources", "assets", "fluxecho", "lang");
    /** A key the code writes out: EchoText.t("x") / lines("x") is fluxecho.x; the others are the whole key. */
    private static final Pattern ECHO = Pattern.compile("EchoText\\.(?:t|lines)\\(\\s*\"([^\"]+)\""),
        WHOLE = Pattern
            .compile("(?:ChatComponentTranslation|translateToLocal(?:Formatted)?)\\(\\s*\"(fluxecho\\.[^\"]+)\""),
        PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?[sd]");

    private static Map<String, String> read(String file, List<String> duplicates) throws IOException {
        Map<String, String> m = new LinkedHashMap<>();
        for (String line : Files.readAllLines(LANG.resolve(file), StandardCharsets.UTF_8)) {
            String l = line.startsWith("﻿") ? line.substring(1) : line;
            if (l.isEmpty() || l.startsWith("#")) continue;
            int eq = l.indexOf('=');
            if (eq <= 0) continue;
            String key = l.substring(0, eq);
            if (m.put(key, l.substring(eq + 1)) != null) duplicates.add(file + ": " + key);
        }
        return m;
    }

    private static List<String> placeholders(String text) {
        List<String> l = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) l.add(m.group());
        return l;
    }

    @Test
    void bothLanguagesSayTheSame() throws IOException {
        List<String> duplicates = new ArrayList<>();
        Map<String, String> en = read("en_US.lang", duplicates), zh = read("zh_CN.lang", duplicates);
        assertTrue(duplicates.isEmpty(), "keys written twice: " + duplicates);
        TreeSet<String> onlyEn = new TreeSet<>(en.keySet()), onlyZh = new TreeSet<>(zh.keySet());
        onlyEn.removeAll(zh.keySet());
        onlyZh.removeAll(en.keySet());
        assertTrue(onlyEn.isEmpty(), "missing in zh_CN.lang: " + onlyEn);
        assertTrue(onlyZh.isEmpty(), "missing in en_US.lang: " + onlyZh);
        for (Map.Entry<String, String> e : en.entrySet()) {
            List<String> a = placeholders(e.getValue()), b = placeholders(zh.get(e.getKey()));
            a.sort(null);
            b.sort(null);
            assertEquals(a, b, "placeholders of " + e.getKey());
        }
    }

    @Test
    void everyKeyTheCodeNamesExists() throws IOException {
        Map<String, String> en = read("en_US.lang", new ArrayList<>());
        TreeSet<String> missing = new TreeSet<>();
        List<Path> sources;
        try (Stream<Path> s = Files.walk(Paths.get("src", "main", "java"))) {
            sources = s.filter(
                p -> p.toString()
                    .endsWith(".java"))
                .collect(java.util.stream.Collectors.toList());
        }
        int seen = 0;
        for (Path p : sources) {
            String code = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            Matcher m = ECHO.matcher(code);
            while (m.find()) {
                if (m.group(1)
                    .endsWith(".")) continue; // a key built at run time
                seen++;
                if (!en.containsKey("fluxecho." + m.group(1)))
                    missing.add("fluxecho." + m.group(1) + " (" + p.getFileName() + ")");
            }
            m = WHOLE.matcher(code);
            while (m.find()) {
                if (m.group(1)
                    .endsWith(".")) continue;
                seen++;
                if (!en.containsKey(m.group(1))) missing.add(m.group(1) + " (" + p.getFileName() + ")");
            }
        }
        assertTrue(seen > 40, "found the keys in the code (" + seen + ")");
        assertTrue(missing.isEmpty(), "keys the code uses that the language files lack: " + missing);
    }
}
