package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The arguments of the {@code /fluxecho} operator command (0.10.1), read without a server so they can be tested:
 * <ul>
 * <li>{@code research all [player]}: completes every research of the player's team;</li>
 * <li>{@code research reset [player]}: clears it;</li>
 * <li>{@code build free [on|off]}: free building on the nearest nexus (no argument: toggled);</li>
 * <li>{@code build finish}: the nearest nexus's current job, free and at once;</li>
 * <li>{@code build status}: the nearest nexus's job and counts.</li>
 * </ul>
 * Words are matched without regard to case; anything else (a missing or unknown word, a word too many) is not a use of
 * the command and shows its usage. Also the tab completion of each word, and the pick of the nearest nexus.
 */
public final class EchoCommand {

    /** How far (blocks, to a nexus's centre, same dimension) the build commands look for the nexus they act on. */
    public static final double RANGE = 128;

    /** What a use of the command does. */
    public enum Action {
        RESEARCH_ALL,
        RESEARCH_RESET,
        BUILD_FREE,
        BUILD_FINISH,
        BUILD_STATUS
    }

    /** One use of the command. */
    public static final class Parsed {

        public final Action action;
        /** The player named for a research action, or null for the sender. */
        public final String player;
        /** For {@code build free}: on or off, or null to toggle. */
        public final Boolean free;

        Parsed(Action action, String player, Boolean free) {
            this.action = action;
            this.player = player;
            this.free = free;
        }
    }

    private static final List<String> FIRST = Arrays.asList("research", "build"),
        RESEARCH = Arrays.asList("all", "reset"), BUILD = Arrays.asList("free", "finish", "status"),
        SWITCH = Arrays.asList("on", "off");

    private EchoCommand() {}

    /** The use the arguments make of the command, or null when they make none (the usage is shown). */
    public static Parsed parse(String[] args) {
        if (args == null || args.length < 2) return null;
        String what = low(args[0]), how = low(args[1]);
        if ("research".equals(what)) {
            if (args.length > 3) return null;
            String player = args.length == 3 ? args[2] : null;
            if (player != null && player.isEmpty()) return null;
            if ("all".equals(how)) return new Parsed(Action.RESEARCH_ALL, player, null);
            if ("reset".equals(how)) return new Parsed(Action.RESEARCH_RESET, player, null);
            return null;
        }
        if (!"build".equals(what)) return null;
        switch (how) {
            case "free": {
                if (args.length > 3) return null;
                if (args.length == 2) return new Parsed(Action.BUILD_FREE, null, null);
                String sw = low(args[2]);
                if ("on".equals(sw)) return new Parsed(Action.BUILD_FREE, null, Boolean.TRUE);
                if ("off".equals(sw)) return new Parsed(Action.BUILD_FREE, null, Boolean.FALSE);
                return null;
            }
            case "finish":
                return args.length == 2 ? new Parsed(Action.BUILD_FINISH, null, null) : null;
            case "status":
                return args.length == 2 ? new Parsed(Action.BUILD_STATUS, null, null) : null;
            default:
                return null;
        }
    }

    /**
     * The words that may stand where the last argument is being typed, those starting with what was typed so far
     * (without regard to case); {@code players} are the names offered for a research action's player.
     */
    public static List<String> complete(String[] args, Collection<String> players) {
        if (args == null || args.length == 0) return Collections.emptyList();
        List<String> pool;
        if (args.length == 1) pool = FIRST;
        else {
            String what = low(args[0]);
            if (args.length == 2) pool = "research".equals(what) ? RESEARCH : "build".equals(what) ? BUILD : null;
            else if (args.length == 3 && "research".equals(what) && RESEARCH.contains(low(args[1])))
                pool = players == null ? null : new ArrayList<>(players);
            else if (args.length == 3 && "build".equals(what) && "free".equals(low(args[1]))) pool = SWITCH;
            else pool = null;
        }
        if (pool == null) return Collections.emptyList();
        String typed = low(args[args.length - 1]);
        List<String> out = new ArrayList<>();
        for (String w : pool) if (w != null && low(w).startsWith(typed)) out.add(w);
        return out;
    }

    /**
     * The index of the nearest of the centres {x, y, z} to the point within {@link #RANGE} (inclusive), or -1 when
     * none is that near.
     */
    public static int nearest(List<double[]> centres, double x, double y, double z) {
        int best = -1;
        double bestD = RANGE * RANGE;
        for (int i = 0; i < centres.size(); i++) {
            double[] c = centres.get(i);
            double dx = c[0] - x, dy = c[1] - y, dz = c[2] - z, d = dx * dx + dy * dy + dz * dz;
            if (d <= bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    private static String low(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
