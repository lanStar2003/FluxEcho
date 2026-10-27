import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes the two lang files of the fluxecho domain from their shards. Since 0.17.0 the keys live in
 * {@code lang/src/<area>.<language>.lang}, one area of keys per file (the table is {@code lang/src/areas.txt}), so that
 * parallel work on different buildings, the narrative or the interface never edits the same file; the shipped
 * {@code assets/fluxecho/lang/en_US.lang} and {@code zh_CN.lang} are generated and committed with them.
 *
 * <p>
 * The merge is fixed and reproducible: first the keys listed in {@code lang/src/order.txt} (the 0.16.0 files' keys in
 * their order), each with the comment and blank lines above it in its shard and the stray lines after it; then, area by
 * area in the table's order, every other key in its shard's order, then that shard's trailing lines. So the first
 * merge gave back the 0.16.0 files byte for byte, and a new key only ever lands after them. A line that is neither a
 * key nor a comment nor blank belongs to the key above it (0.16.0 has three such lines, which Minecraft skips).
 *
 * <p>
 * Run from the repository root: {@code java tools/LangMerge.java} writes the two files,
 * {@code java tools/LangMerge.java --check} only compares. It stops on a key in two shards, a key no area takes, a key
 * in another area's shard, a shard file without its area, or an area without both files. {@code LangShardTest}
 * compiles this file and checks that the committed lang files are its output.
 */
public class LangMerge {

    static final String SRC = "lang/src", OUT = "src/main/resources/assets/fluxecho/lang";
    static final List<String> LANGUAGES = Arrays.asList("en_US", "zh_CN");

    public static void main(String[] args) throws IOException {
        Path root = Paths.get(".");
        boolean check = args.length > 0 && args[0].equals("--check");
        boolean stale = false;
        for (Map.Entry<String, String> e : merge(root).entrySet()) {
            Path out = root.resolve(e.getKey());
            byte[] now = e.getValue()
                .getBytes(StandardCharsets.UTF_8);
            boolean same = Files.exists(out) && Arrays.equals(Files.readAllBytes(out), now);
            if (check) {
                if (!same) System.err.println(e.getKey() + " is not the merge of " + SRC);
                stale |= !same;
            } else if (!same) {
                Files.write(out, now);
                System.out.println("wrote " + e.getKey());
            } else System.out.println(e.getKey() + " unchanged");
        }
        if (stale) {
            System.err.println("run java tools/LangMerge.java");
            System.exit(1);
        }
        if (check) System.out.println("the lang files are the merge of " + SRC);
    }

    /** The generated files, by their path from the repository root. */
    public static Map<String, String> merge(Path root) throws IOException {
        Path src = root.resolve(SRC);
        Map<String, List<String>> areas = areas(src.resolve("areas.txt"));
        List<String> order = new ArrayList<>();
        for (String l : lines(src.resolve("order.txt"))) if (!l.isEmpty() && !l.startsWith("#")) order.add(l);
        checkFiles(src, areas.keySet());
        Map<String, String> out = new LinkedHashMap<>();
        for (String lang : LANGUAGES) {
            Map<String, Shard> shards = new LinkedHashMap<>();
            Map<String, String> owner = new HashMap<>();
            for (String area : areas.keySet()) {
                Shard s = new Shard(area, lines(src.resolve(area + "." + lang + ".lang")));
                for (String key : s.entries.keySet()) {
                    String was = owner.put(key, area);
                    if (was != null) throw new IllegalArgumentException(
                        key + " is in both " + was + "." + lang + ".lang and " + area + "." + lang + ".lang");
                    List<String> may = areasOf(areas, key);
                    if (may.isEmpty()) throw new IllegalArgumentException(
                        key + " (" + area + "." + lang + ".lang) matches no prefix in areas.txt");
                    if (!may.contains(area)) throw new IllegalArgumentException(
                        key + " is in " + area + "." + lang + ".lang but belongs to " + String.join(" or ", may));
                }
                shards.put(area, s);
            }
            StringBuilder b = new StringBuilder();
            Set<String> done = new HashSet<>();
            for (String key : order) {
                String area = owner.get(key);
                if (area == null || !done.add(key)) continue;
                for (String l : shards.get(area).entries.get(key)) b.append(l)
                    .append('\n');
            }
            for (Shard s : shards.values()) {
                for (Map.Entry<String, List<String>> e : s.entries.entrySet())
                    if (done.add(e.getKey())) for (String l : e.getValue()) b.append(l)
                        .append('\n');
                for (String l : s.trailing) b.append(l)
                    .append('\n');
            }
            out.put(OUT + "/" + lang + ".lang", b.toString());
        }
        return out;
    }

    /** One shard: each key with the comment and blank lines above it and the stray lines after it. */
    static final class Shard {

        final Map<String, List<String>> entries = new LinkedHashMap<>();
        final List<String> trailing = new ArrayList<>();

        Shard(String area, List<String> lines) {
            List<String> pending = new ArrayList<>(), last = null;
            for (String l : lines) {
                if (l.isEmpty() || l.startsWith("#")) pending.add(l);
                else if (l.indexOf('=') >= 0) {
                    String key = l.substring(0, l.indexOf('='));
                    last = new ArrayList<>(pending);
                    last.add(l);
                    pending.clear();
                    if (entries.put(key, last) != null)
                        throw new IllegalArgumentException(key + " twice in the " + area + " shard");
                } else if (last != null && pending.isEmpty()) last.add(l);
                else throw new IllegalArgumentException("a line of the " + area + " shard is not a key: " + l);
            }
            trailing.addAll(pending);
        }
    }

    /** The area table: area to its prefixes, in the table's order. */
    static Map<String, List<String>> areas(Path file) throws IOException {
        Map<String, List<String>> areas = new LinkedHashMap<>();
        for (String l : lines(file)) {
            if (l.trim()
                .isEmpty() || l.startsWith("#")) continue;
            int colon = l.indexOf(':');
            if (colon < 0) throw new IllegalArgumentException("areas.txt: no area in " + l);
            List<String> prefixes = new ArrayList<>();
            for (String p : l.substring(colon + 1)
                .trim()
                .split("\\s+")) if (!p.isEmpty()) prefixes.add(p);
            if (areas.put(
                l.substring(0, colon)
                    .trim(),
                prefixes) != null) throw new IllegalArgumentException("areas.txt: area twice in " + l);
        }
        return areas;
    }

    /** How long a match {@code prefix} is for {@code key}, or -1 when it does not take the key. */
    static int match(String prefix, String key) {
        if (prefix.endsWith("*")) {
            String p = prefix.substring(0, prefix.length() - 1);
            return key.startsWith(p) ? p.length() : -1;
        }
        if (prefix.endsWith(".")) return key.startsWith(prefix) ? prefix.length() : -1;
        return key.equals(prefix) || key.startsWith(prefix + ".") ? prefix.length() : -1;
    }

    /** The areas whose matching prefix for the key is the longest (more than one where they share it). */
    static List<String> areasOf(Map<String, List<String>> areas, String key) {
        int best = -1;
        List<String> who = new ArrayList<>();
        for (Map.Entry<String, List<String>> a : areas.entrySet()) {
            int m = -1;
            for (String p : a.getValue()) m = Math.max(m, match(p, key));
            if (m < 0 || m < best) continue;
            if (m > best) who.clear();
            best = m;
            who.add(a.getKey());
        }
        return who;
    }

    /** Every area has both files, and every shard file has its area. */
    static void checkFiles(Path src, Set<String> areas) throws IOException {
        for (String area : areas) for (String lang : LANGUAGES) if (!Files.exists(src.resolve(area + "." + lang + ".lang")))
            throw new IllegalArgumentException("area " + area + " has no " + area + "." + lang + ".lang");
        try (DirectoryStream<Path> files = Files.newDirectoryStream(src, "*.lang")) {
            for (Path f : files) {
                String name = f.getFileName()
                    .toString();
                String stem = name.substring(0, name.length() - ".lang".length());
                int dot = stem.indexOf('.');
                if (dot < 0 || !areas.contains(stem.substring(0, dot)) || !LANGUAGES.contains(stem.substring(dot + 1)))
                    throw new IllegalArgumentException(name + " is not a shard of an area in areas.txt");
            }
        }
    }

    /** The file's lines without the final line break (UTF-8, LF). */
    static List<String> lines(Path file) throws IOException {
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        if (text.indexOf('\r') >= 0) throw new IllegalArgumentException(file + " has CR line breaks; use LF");
        if (text.isEmpty()) return new ArrayList<>();
        if (!text.endsWith("\n")) text += "\n";
        return new ArrayList<>(Arrays.asList(text.substring(0, text.length() - 1)
            .split("\n", -1)));
    }
}
