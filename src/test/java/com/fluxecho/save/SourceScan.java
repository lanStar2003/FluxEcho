package com.fluxecho.save;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads the mod's own sources ({@code src/main/java}) for the identifiers that are only ever written as arguments of a
 * registration call: the registry names of blocks and items, the tile entity ids, the ore dictionary names, the network
 * channels and their message discriminators, and the names of the saved world data. None of these can be asked of the
 * running code in a test (Forge's registries need a loading mod), so the ID snapshot and its freeze test take them from
 * the calls themselves; a string built of constants ({@code "fluxecho:" + EcoDomeShape.KEY}) is evaluated against the
 * loaded classes. Comments are dropped before the calls are matched, and a call may span lines.
 * <p>
 * Scanning the source is what the ID ledger ({@code docs/dev/ids.md}) was checked against too. If a registration is
 * ever rewritten so that its name is no longer a constant expression (a loop over a table, say), the freeze test fails
 * and names it: extend {@link #eval} rather than drop the check.
 */
public final class SourceScan {

    /** One source file: its package, imports by simple name, primary class and comment-free text. */
    public static final class Source {

        public final Path path;
        public final String pkg, primary, text;
        final Map<String, String> imports = new HashMap<>();

        Source(Path path, String text) {
            this.path = path;
            this.text = text;
            Matcher p = PACKAGE.matcher(text);
            pkg = p.find() ? p.group(1) : "";
            Matcher i = IMPORT.matcher(text);
            while (i.find()) {
                String fqn = i.group(1);
                imports.put(fqn.substring(fqn.lastIndexOf('.') + 1), fqn);
            }
            String name = path.getFileName()
                .toString();
            primary = pkg + "." + name.substring(0, name.length() - ".java".length());
        }

        /** The mod a class of this file belongs to, by its top package. */
        public String mod() {
            if (pkg.startsWith("com.fluxlite")) return "fluxlite";
            if (pkg.startsWith("com.fluxdepths")) return "fluxdepths";
            return "fluxecho";
        }

        /** The fully qualified name of a class this file names by its simple name, or null. */
        public String resolve(String simple) {
            String fqn = imports.get(simple);
            if (fqn != null) return fqn;
            for (String candidate : new String[] { pkg + "." + simple, primary + "$" + simple, "java.lang." + simple })
                if (load(candidate) != null) return candidate;
            return null;
        }
    }

    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    private static List<Source> cache;
    private static Path cacheRoot;

    private SourceScan() {}

    /** The project's main sources, read once per test JVM. */
    public static synchronized List<Source> sources() {
        Path root = Paths.get("src", "main", "java");
        if (cache != null && root.equals(cacheRoot)) return cache;
        List<Source> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(
                p -> p.toString()
                    .endsWith(".java"))
                .sorted()
                .forEach(p -> {
                    try {
                        out.add(
                            new Source(p, stripComments(new String(Files.readAllBytes(p), StandardCharsets.UTF_8))));
                    } catch (IOException e) {
                        throw new IllegalStateException("cannot read " + p, e);
                    }
                });
        } catch (IOException e) {
            throw new IllegalStateException("cannot walk " + root.toAbsolutePath(), e);
        }
        cache = out;
        cacheRoot = root;
        return out;
    }

    /** The text with its comments blanked out (string and char literals kept whole). */
    static String stripComments(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && s.charAt(j) != c) j += s.charAt(j) == '\\' ? 2 : 1;
                out.append(s, i, Math.min(n, j + 1));
                i = j + 1;
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int end = s.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                out.append(' ');
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** A class by name, not initialised, or null. */
    static Class<?> load(String fqn) {
        try {
            return Class.forName(fqn, false, SourceScan.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    /**
     * The value of a string expression as the file writes it: string literals and constants joined by {@code +}. A
     * constant is a static field of the file's own class (by its bare name) or of a class the file names. Throws when a
     * part cannot be evaluated, so a registration the scan does not understand is never silently left out.
     */
    public static String eval(Source src, String expr) {
        StringBuilder out = new StringBuilder();
        for (String part : splitPlus(expr.trim())) {
            part = part.trim();
            if (part.startsWith("\"")) {
                out.append(unquote(part));
                continue;
            }
            Object v = constant(src, part);
            if (v == null) throw new IllegalStateException(src.path + ": cannot evaluate " + part + " in " + expr);
            out.append(v);
        }
        return out.toString();
    }

    /** The value of a static constant the file names ({@code KEY}, {@code EcoDomeShape.KEY}), or null. */
    public static Object constant(Source src, String name) {
        int dot = name.lastIndexOf('.');
        String owner = dot < 0 ? null : name.substring(0, dot), field = name.substring(dot + 1);
        List<String> classes = new ArrayList<>();
        if (owner == null) {
            // the file's own class, or a class nested in it that uses its own constant
            classes.add(src.primary);
            Matcher m = Pattern.compile("class\\s+(\\w+)")
                .matcher(src.text);
            while (m.find()) classes.add(src.primary + "$" + m.group(1));
        } else if (owner.contains(".") && load(owner) != null) classes.add(owner);
        else {
            // a class the file names, or a class nested in one (Outer.Inner.KEY)
            String first = owner.contains(".") ? owner.substring(0, owner.indexOf('.')) : owner;
            String fqn = src.resolve(first);
            if (fqn == null) return null;
            classes.add(
                fqn + owner.substring(first.length())
                    .replace('.', '$'));
        }
        for (String c : classes) {
            Class<?> k = load(c);
            if (k == null) continue;
            try {
                Field f = k.getDeclaredField(field);
                f.setAccessible(true);
                return f.get(null);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // not this class's
            }
        }
        return null;
    }

    private static List<String> splitPlus(String expr) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '"' && (i == 0 || expr.charAt(i - 1) != '\\')) quoted = !quoted;
            if (c == '+' && !quoted) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else cur.append(c);
        }
        parts.add(cur.toString());
        return parts;
    }

    private static String unquote(String lit) {
        return lit.substring(1, lit.length() - 1)
            .replace("\\\"", "\"")
            .replace("\\\\", "\\");
    }

    /** One call found: the file it is in and its arguments as written, split at the top-level commas. */
    public static final class Call {

        public final Source src;
        public final List<String> args;

        Call(Source src, List<String> args) {
            this.src = src;
            this.args = args;
        }
    }

    /**
     * Every call of a method, by a regular expression for the text before its opening parenthesis (for instance
     * {@code GameRegistry\.registerItem}).
     */
    public static List<Call> calls(String method) {
        List<Call> out = new ArrayList<>();
        Pattern p = Pattern.compile("(?<![\\w.])" + method + "\\s*\\(");
        for (Source s : sources()) {
            Matcher m = p.matcher(s.text);
            while (m.find()) {
                List<String> args = args(s.text, m.end());
                if (args != null) out.add(new Call(s, args));
            }
        }
        return out;
    }

    /** The arguments of the call whose parenthesis opened just before {@code from}, or null when it never closes. */
    private static List<String> args(String text, int from) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < text.length() && text.charAt(j) != c) j += text.charAt(j) == '\\' ? 2 : 1;
                cur.append(text, i, Math.min(text.length(), j + 1));
                i = j;
                continue;
            }
            if (c == '(' || c == '{' || c == '[') depth++;
            if (c == ')' || c == '}' || c == ']') {
                if (depth == 0) {
                    out.add(
                        cur.toString()
                            .trim());
                    return out;
                }
                depth--;
            }
            if (c == ',' && depth == 0) {
                out.add(
                    cur.toString()
                        .trim());
                cur.setLength(0);
            } else cur.append(c);
        }
        return null;
    }

    /**
     * The value of {@code static final String NAME = "..."} (or {@code public static final ...}) in the file, the
     * name its saved world data are stored under; null when it declares none.
     */
    public static String savedDataName(Source s) {
        if (!s.text.contains("extends WorldSavedData")) return null;
        Matcher m = Pattern.compile("static\\s+final\\s+String\\s+NAME\\s*=\\s*(\"[^\"]*\")")
            .matcher(s.text);
        return m.find() ? unquote(m.group(1)) : null;
    }
}
