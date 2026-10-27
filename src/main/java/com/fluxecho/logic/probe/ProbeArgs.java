package com.fluxecho.logic.probe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The arguments of the operators' check-up commands under {@code /fluxecho} (0.17.0, refactor FINAL F06, ids.md
 * §18): {@code probe depth | sky | sun | bed}, {@code swatch} and {@code swatch clear}, {@code cam <A|B|C|D|E>}; read
 * without a server so they can be tested, like {@code EchoCommand} reads the older ones. Words match without regard to
 * case; anything else is no use of these commands.
 */
public final class ProbeArgs {

    /** What a use does. */
    public enum Action {
        PROBE_DEPTH,
        PROBE_SKY,
        PROBE_SUN,
        PROBE_BED,
        SWATCH,
        SWATCH_CLEAR,
        CAM
    }

    /** One use. */
    public static final class Parsed {

        public final Action action;
        /** The camera point of {@code cam}, else null. */
        public final CamPoints.Point point;

        Parsed(Action action, CamPoints.Point point) {
            this.action = action;
            this.point = point;
        }
    }

    /** The first words these commands take. */
    public static final List<String> FIRST = Collections.unmodifiableList(Arrays.asList("probe", "swatch", "cam"));
    private static final List<String> PROBES = Arrays.asList("depth", "sky", "sun", "bed"),
        SWATCH = Collections.singletonList("clear"), POINTS = Arrays.asList("A", "B", "C", "D", "E");

    private ProbeArgs() {}

    /** Whether the first word is one of these commands' (so {@code /fluxecho} hands the arguments here). */
    public static boolean claims(String[] args) {
        return args != null && args.length > 0 && FIRST.contains(low(args[0]));
    }

    /** The use the arguments make, or null (the usage is shown). */
    public static Parsed parse(String[] args) {
        if (!claims(args)) return null;
        String what = low(args[0]);
        switch (what) {
            case "probe": {
                if (args.length != 2) return null;
                switch (low(args[1])) {
                    case "depth":
                        return new Parsed(Action.PROBE_DEPTH, null);
                    case "sky":
                        return new Parsed(Action.PROBE_SKY, null);
                    case "sun":
                        return new Parsed(Action.PROBE_SUN, null);
                    case "bed":
                        return new Parsed(Action.PROBE_BED, null);
                    default:
                        return null;
                }
            }
            case "swatch":
                if (args.length == 1) return new Parsed(Action.SWATCH, null);
                return args.length == 2 && "clear".equals(low(args[1])) ? new Parsed(Action.SWATCH_CLEAR, null) : null;
            default: {
                if (args.length != 2) return null;
                CamPoints.Point p = CamPoints.Point.of(args[1]);
                return p == null ? null : new Parsed(Action.CAM, p);
            }
        }
    }

    /** The words that may stand where the last argument is being typed. */
    public static List<String> complete(String[] args) {
        if (args == null || args.length == 0) return Collections.emptyList();
        List<String> pool;
        if (args.length == 1) pool = FIRST;
        else if (args.length == 2) {
            String what = low(args[0]);
            pool = "probe".equals(what) ? PROBES : "swatch".equals(what) ? SWATCH : "cam".equals(what) ? POINTS : null;
        } else pool = null;
        if (pool == null) return Collections.emptyList();
        String typed = low(args[args.length - 1]);
        List<String> out = new ArrayList<>();
        for (String w : pool) if (low(w).startsWith(typed)) out.add(w);
        return out;
    }

    private static String low(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
