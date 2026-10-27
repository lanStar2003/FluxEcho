package com.fluxecho.logic.codex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.fluxecho.logic.ResearchTree;
import com.fluxecho.logic.voyage.ScoreBridge;

/**
 * The Echo Codex's narrative pages as 0.17.0 has them (narrative §1.7, §9.2 N1), beside its records: the volumes
 * ("卷") 0 to III and the cabin logs ("舱志") of the buildings 0.16.0 ships. This is their pure part: when each volume
 * opens, from events and states 0.16.0 already keeps (the capsule, the home memories and the stranded-day count come
 * with {@code fluxecho_voyage} in 0.18), which buildings have a log, and the lang keys the Codex screen builds at run
 * time. A cabin log opens once its building has formed for the team: the scoreboard bridge keeps that as level 1 of
 * the building's ladder ({@link ScoreBridge}).
 */
public final class CodexPages {

    /** The volumes shown: 卷零 渡, 卷一 搁浅, 卷二 回响, 卷三 锚定. */
    public static final int VOLUMES = 4;

    /** What opens a volume. */
    public enum Gate {
        /** Always open: volume 0 (no capsule to place yet) and volume I (every player starts aground). */
        OPEN,
        /** The team's first codex entry: this world remembered something done by hand (volume II's first beat). */
        FIRST_RECORD,
        /** The anchor research, done the first time a nexus forms (volume III's first beat). */
        ANCHOR
    }

    /** The buildings with a cabin log, rows 01 to 17 in row order: everything 0.16.0 ships. */
    public static final List<String> HALLS;

    static {
        List<String> halls = new ArrayList<>();
        for (int row = 1; row <= 17; row++) halls.add(ScoreBridge.buildingKey(row));
        HALLS = Collections.unmodifiableList(halls);
    }

    private CodexPages() {}

    public static Gate gate(int volume) {
        return switch (volume) {
            case 2 -> Gate.FIRST_RECORD;
            case 3 -> Gate.ANCHOR;
            default -> Gate.OPEN;
        };
    }

    /** Whether the volume is open to a team with that many codex entries and that research. */
    public static boolean open(int volume, int records, Set<String> research) {
        if (volume < 0 || volume >= VOLUMES) return false;
        return switch (gate(volume)) {
            case FIRST_RECORD -> records > 0;
            case ANCHOR -> research.contains(ResearchTree.ANCHOR);
            default -> true;
        };
    }

    /** The volume's title, under {@code fluxecho.}. */
    public static String titleKey(int volume) {
        return "codex.vol." + volume + ".title";
    }

    /** The volume's text, under {@code fluxecho.}. */
    public static String bodyKey(int volume) {
        return "codex.vol." + volume;
    }

    /** What opens a volume that is not open from the start, under {@code fluxecho.}. */
    public static String whenKey(int volume) {
        return "codex.vol." + volume + ".when";
    }

    /** A building's cabin log, under {@code fluxecho.}: its heading line, then the record. */
    public static String hallKey(String building) {
        return "codex.hall." + building;
    }

    /** The whole lang key of a building's name: the nexus's block, a module's name. */
    public static String nameKey(String building) {
        return "nexus".equals(building) ? "tile.fluxecho.nexus.name" : "fluxecho.module." + building;
    }

    /**
     * A page's paragraphs: its lines, split at the line breaks a lang file writes ("\n" as two characters) or real
     * ones; an empty one is a blank line.
     */
    public static List<String> paragraphs(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        Collections.addAll(out, text.split("\\\\n|\n", -1));
        return out;
    }

    /** How many characters a player reads: formatting codes and line breaks left out. */
    public static int shown(String text) {
        if (text == null) return 0;
        String s = text.replace("\\n", "")
            .replace("\n", "");
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '§' && i + 1 < s.length()) i++;
            else n++;
        }
        return n;
    }
}
