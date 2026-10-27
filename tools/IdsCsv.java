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

/**
 * Exports the machine-readable part of the ID ledger to a CSV: the JSON block of {@code docs/dev/ids.md} section 19
 * becomes {@code docs/dev/ids.csv}, one ledger row per line with the columns class, section, value, meaning, owner,
 * since, status and source. The ledger is the only source; the CSV exists so that tests and scripts can read the IDs
 * without parsing Markdown. Run from the repository root: {@code java tools/IdsCsv.java} writes the file,
 * {@code java tools/IdsCsv.java --check} only compares it. {@code IdsCsvTest} compiles this file and checks that the
 * committed CSV is exactly what it writes.
 */
public class IdsCsv {

    static final String MD = "docs/dev/ids.md", CSV = "docs/dev/ids.csv";
    static final List<String> HEADER = Arrays
        .asList("class", "section", "value", "meaning", "owner", "since", "status", "source");
    /** The columns that together name the ID, for the ledger classes whose rows have no "value" column. */
    static final Map<String, List<String>> VALUE_COLS = new HashMap<>();

    static {
        VALUE_COLS.put("net_messages", Arrays.asList("dir", "num", "name"));
        VALUE_COLS.put("net_other", Arrays.asList("channel", "num"));
        VALUE_COLS.put("quest_keys_old", Arrays.asList("keys"));
        VALUE_COLS.put("buildings", Arrays.asList("key"));
    }

    public static void main(String[] args) throws IOException {
        Path root = Paths.get(".");
        String csv = csv(new String(Files.readAllBytes(root.resolve(MD)), StandardCharsets.UTF_8));
        Path out = root.resolve(CSV);
        boolean check = args.length > 0 && args[0].equals("--check");
        String old = Files.exists(out) ? new String(Files.readAllBytes(out), StandardCharsets.UTF_8) : null;
        if (check) {
            if (!csv.equals(old)) {
                System.err.println(CSV + " is not the export of " + MD + "; run java tools/IdsCsv.java");
                System.exit(1);
            }
            System.out.println(CSV + " is up to date");
            return;
        }
        Files.write(out, csv.getBytes(StandardCharsets.UTF_8));
        System.out.println("wrote " + CSV + " (" + (csv.split("\n").length - 1) + " rows)");
    }

    /** The CSV for the ledger text: a header line, then every row of every class in the order the JSON lists them. */
    @SuppressWarnings("unchecked")
    public static String csv(String markdown) {
        Map<String, Object> ledger = (Map<String, Object>) new Json(jsonBlock(markdown)).value();
        Map<String, Object> classes = (Map<String, Object>) ledger.get("classes");
        StringBuilder out = new StringBuilder();
        line(out, HEADER);
        for (Map.Entry<String, Object> c : classes.entrySet()) {
            Map<String, Object> cls = (Map<String, Object>) c.getValue();
            List<String> cols = new ArrayList<>();
            for (Object o : (List<Object>) cls.get("cols")) cols.add((String) o);
            List<String> named = cols.contains("value") ? Arrays.asList("value")
                : VALUE_COLS.getOrDefault(c.getKey(), cols.subList(0, 1));
            for (Object o : (List<Object>) cls.get("rows")) {
                Map<String, Object> row = (Map<String, Object>) o;
                List<String> value = new ArrayList<>();
                for (String k : named) value.add(text(row.get(k)));
                List<String> meaning = new ArrayList<>();
                if (cols.contains("meaning")) meaning.add(text(row.get("meaning")));
                for (String k : cols) {
                    if (named.contains(k) || k.equals("meaning") || k.equals("owner") || k.equals("since")
                        || k.equals("status") || k.equals("source")) continue;
                    meaning.add(k + ": " + text(row.get(k)));
                }
                line(
                    out,
                    Arrays.asList(
                        c.getKey(),
                        text(cls.get("section")),
                        String.join(" ", value),
                        String.join(" · ", meaning),
                        text(row.get("owner")),
                        text(row.get("since")),
                        text(row.get("status")),
                        text(row.get("source"))));
            }
        }
        return out.toString();
    }

    /** The text between the {@code ```json} fence of section 19 and the fence that closes it. */
    static String jsonBlock(String markdown) {
        int section = markdown.indexOf("\n## 19.");
        if (section < 0) throw new IllegalArgumentException("the ledger has no section 19");
        int open = markdown.indexOf("```json", section);
        int start = markdown.indexOf('\n', open) + 1;
        int close = markdown.indexOf("\n```", start);
        if (open < 0 || close < 0) throw new IllegalArgumentException("section 19 has no closed json block");
        return markdown.substring(start, close);
    }

    static String text(Object o) {
        return o == null ? "" : o instanceof Double d && d == Math.rint(d) ? String.valueOf(d.longValue()) : o.toString();
    }

    static void line(StringBuilder out, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) out.append(',');
            String f = fields.get(i);
            boolean quote = f.indexOf(',') >= 0 || f.indexOf('"') >= 0 || f.indexOf('\n') >= 0
                || f.indexOf('\r') >= 0 || !f.equals(f.trim());
            out.append(quote ? '"' + f.replace("\"", "\"\"") + '"' : f);
        }
        out.append('\n');
    }

    /** Just enough JSON for the ledger: objects keep their key order, numbers become doubles. */
    static final class Json {

        final String s;
        int i;

        Json(String s) {
            this.s = s;
        }

        Object value() {
            space();
            char c = s.charAt(i);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (s.startsWith("true", i)) return word("true", Boolean.TRUE);
            if (s.startsWith("false", i)) return word("false", Boolean.FALSE);
            if (s.startsWith("null", i)) return word("null", null);
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (start == i) throw error("unexpected '" + c + "'");
            return Double.valueOf(s.substring(start, i));
        }

        Object word(String w, Object v) {
            i += w.length();
            return v;
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            space();
            if (s.charAt(i) == '}') {
                i++;
                return m;
            }
            while (true) {
                space();
                String k = string();
                space();
                expect(':');
                if (m.put(k, value()) != null) throw error("key " + k + " twice");
                space();
                if (s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                expect('}');
                return m;
            }
        }

        List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++;
            space();
            if (s.charAt(i) == ']') {
                i++;
                return l;
            }
            while (true) {
                l.add(value());
                space();
                if (s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                expect(']');
                return l;
            }
        }

        String string() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c != '\\') {
                    b.append(c);
                    continue;
                }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> b.append('\n');
                    case 't' -> b.append('\t');
                    case 'r' -> b.append('\r');
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'u' -> {
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> b.append(e);
                }
            }
        }

        void expect(char c) {
            if (i >= s.length() || s.charAt(i) != c) throw error("expected '" + c + "'");
            i++;
        }

        void space() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        IllegalArgumentException error(String what) {
            int line = 1;
            for (int k = 0; k < Math.min(i, s.length()); k++) if (s.charAt(k) == '\n') line++;
            return new IllegalArgumentException("ids.md section 19, json line " + line + ": " + what);
        }
    }
}
