package com.fluxecho;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cpw.mods.fml.relauncher.FMLInjectionData;

/**
 * Writes {@code config/fluxecho.cfg} the way the game does on a first start (every setting at its default) and reads
 * such a file back setting by setting, so that tests can compare the settings of two versions. Forge's
 * {@code Configuration} needs FML's Minecraft home; a test has none, so it gets {@code build/tmp/config-test}.
 */
public final class ConfigFiles {

    /** One setting as the file shows it. */
    public static final class Setting {

        public final String type, value, bounds, comment;

        Setting(String type, String value, String bounds, String comment) {
            this.type = type;
            this.value = value;
            this.bounds = bounds;
            this.comment = comment;
        }

        @Override
        public String toString() {
            return type + ":" + value + " " + bounds;
        }
    }

    private static final Pattern PROPERTY = Pattern.compile("\\s+([BIDS]):(\"[^\"]+\"|[^=<\\s]+)(=(.*)| <)");
    private static final Pattern BOUNDS = Pattern.compile("(\\[(range|default|valid values)[^\\]]*\\]\\s*)+$");

    private ConfigFiles() {}

    /** A fresh config file written by {@link Config#load}, every setting at its default. */
    public static File fresh(String name) throws IOException {
        File dir = new File("build/tmp/config-test");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot make " + dir);
        try {
            Field home = FMLInjectionData.class.getDeclaredField("minecraftHome");
            home.setAccessible(true);
            if (home.get(null) == null) home.set(null, dir.getAbsoluteFile());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("FML's Minecraft home cannot be set", e);
        }
        File f = new File(dir, name);
        if (f.exists() && !f.delete()) throw new IOException("cannot delete " + f);
        Config.load(f);
        return f;
    }

    /** The settings of a config file, by {@code category.key}, in the file's order. */
    public static Map<String, Setting> read(String text) {
        Map<String, Setting> out = new LinkedHashMap<>();
        String category = null;
        List<String> comment = new ArrayList<>();
        String[] lines = text.replace("\r", "")
            .split("\n");
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            if (category == null) {
                if (l.endsWith(" {") && !l.startsWith("#")) category = l.substring(0, l.length() - 2)
                    .trim();
                continue;
            }
            String t = l.trim();
            if (t.equals("}")) {
                category = null;
                comment.clear();
                continue;
            }
            if (t.startsWith("#")) {
                comment.add(
                    t.substring(1)
                        .trim());
                continue;
            }
            Matcher m = PROPERTY.matcher(l);
            if (!m.matches()) {
                comment.clear();
                continue;
            }
            String key = m.group(2), value;
            if (key.startsWith("\"")) key = key.substring(1, key.length() - 1);
            if (m.group(4) != null) value = m.group(4);
            else {
                StringBuilder v = new StringBuilder("<");
                while (!lines[++i].trim()
                    .equals(">"))
                    v.append(lines[i].trim())
                        .append('|');
                value = v.append('>')
                    .toString();
            }
            String all = String.join(" ", comment);
            Matcher b = BOUNDS.matcher(all);
            String bounds = b.find() ? b.group()
                .trim() : "";
            out.put(category + "." + key, new Setting(m.group(1), value, bounds, all));
            comment.clear();
        }
        return out;
    }

    public static Map<String, Setting> read(File f) throws IOException {
        return read(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
    }
}
