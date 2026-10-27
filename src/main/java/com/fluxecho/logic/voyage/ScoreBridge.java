package com.fluxecho.logic.voyage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The scoreboard bridge (narrative §7.4): FluxEcho writes each team's progress into vanilla scoreboard objectives, so
 * the quest book can follow research, records, the nexus and the buildings with BetterQuesting's own scoreboard task
 * instead of a task type of ours. This is its pure part: the 31 narrative objective names, registered all at once in
 * 0.17 (ids.md §11, L18) though most only get values with the features behind them; the ones version 1 writes; their
 * ranges; which of them only ever rise (a building's ladder keeps the highest level it reached); the route's trunk;
 * and the building row each {@code feBxx} counts (the row = the hull number = the {@code Buildings} row, ids.md §2).
 * <p>
 * The server side ({@code com.fluxecho.voyage.ScoreBridgeServer}) gathers a team's {@link State} and writes
 * {@link #values}, each through {@link #next}.
 */
public final class ScoreBridge {

    /** The counted and staged objectives, in the narrative's order. */
    public static final String REC = "feRec", CAT = "feCat", SHORE = "feShore", XIANG = "feXiang", COORD = "feCoord",
        PHASE = "fePhase", RES = "feRes", ROUTE = "feRoute", LEAP = "feLeap", LIST = "feList";

    /**
     * The building keys by row, 00 to 20: the hull numbers of narrative §2.1, the {@code Buildings} rows of ids.md §2
     * (row 21, the ascension, has no {@code feBxx}: {@link #LEAP} scores it).
     */
    private static final String[] BUILDINGS = { "drift_capsule", "nexus", "library", "eco_dome", "root",
        "dome_generator", "dual_foil_vault", "mana_fountain", "arcane_court", "resonance_gate", "sophon_array",
        "holo_table", "blood_covenant", "hunting_ground", "observatory", "sky_sails", "weather_station",
        "droplet_hangar", "orbital_anchor", "source_heart", "leap_ring" };

    /**
     * The levels of the buildings whose ladder is not the common three (formed, first real use, share ready):
     * narrative §7.4's ladder table.
     */
    private static final Map<String, Integer> LADDERS = new LinkedHashMap<>();

    /** The trunk of the route, in order: {@code feRoute} = n when the first n stars are done (narrative §7.4). */
    public static final List<String> TRUNK = Collections.unmodifiableList(
        Arrays.asList(
            "anchor",
            "inner_ring",
            "phase_2",
            "phase_3",
            "outer_ring",
            "phase_4",
            "sky_ring",
            "phase_5",
            "source_reach",
            "third_ring",
            "home_bearing",
            "leap",
            "crossing"));

    /** The 31 narrative objectives, in the narrative's order: the ten counts and stages, then feB00 to feB20. */
    public static final List<String> OBJECTIVES;

    /**
     * What version 1 gives a value (0.17; narrative §9.2 N1): the records, their categories, the nexus's phase, the
     * research, the route and level 1 (formed) of the buildings 01 to 17, everything that has a source in 0.16's data.
     */
    public static final List<String> V1;

    static {
        LADDERS.put(building(4), 7);
        LADDERS.put(building(10), 4);
        LADDERS.put(building(18), 5);
        LADDERS.put(building(19), 5);
        LADDERS.put(building(20), 6);
        List<String> all = new ArrayList<>(Arrays.asList(REC, CAT, SHORE, XIANG, COORD, PHASE, RES, ROUTE, LEAP, LIST));
        for (int row = 0; row < BUILDINGS.length; row++) all.add(building(row));
        OBJECTIVES = Collections.unmodifiableList(all);
        List<String> v1 = new ArrayList<>(Arrays.asList(REC, CAT, PHASE, RES, ROUTE));
        for (int row = 1; row <= 17; row++) v1.add(building(row));
        V1 = Collections.unmodifiableList(v1);
    }

    private ScoreBridge() {}

    /** The objective of a building row: {@code feB00} to {@code feB20}. */
    public static String building(int row) {
        return String.format("feB%02d", row);
    }

    /** The row of a building key (the module key: {@code library}, {@code root}, ...), or -1 for none. */
    public static int row(String buildingKey) {
        if (buildingKey == null) return -1;
        for (int i = 0; i < BUILDINGS.length; i++) if (BUILDINGS[i].equals(buildingKey)) return i;
        return -1;
    }

    /** The building key of a row, or null outside 00 to 20. */
    public static String buildingKey(int row) {
        return row >= 0 && row < BUILDINGS.length ? BUILDINGS[row] : null;
    }

    /** The highest value an objective takes; {@link Integer#MAX_VALUE} for a plain count. */
    public static int top(String objective) {
        if (objective == null) return Integer.MAX_VALUE;
        switch (objective) {
            case XIANG, COORD -> {
                return 7;
            }
            case PHASE -> {
                return 5;
            }
            case ROUTE -> {
                return TRUNK.size();
            }
            case LEAP -> {
                return 6;
            }
            case LIST -> {
                return 20;
            }
            default -> {
                if (!isBuilding(objective)) return Integer.MAX_VALUE;
                Integer ladder = LADDERS.get(objective);
                return ladder == null ? 3 : ladder;
            }
        }
    }

    /**
     * Whether an objective only ever rises: a building's ladder (the highest level it reached, narrative §7.4), the
     * phase, the home memories, the star coordinates and the leap's stages. The counts are worked out again from the
     * saved state each time and written as they are (a research reset lowers {@code feRes}); the checklist rows are
     * live and may drop.
     */
    public static boolean rises(String objective) {
        if (objective == null) return false;
        return isBuilding(objective) || PHASE.equals(objective)
            || XIANG.equals(objective)
            || COORD.equals(objective)
            || LEAP.equals(objective);
    }

    private static boolean isBuilding(String objective) {
        return objective.length() == 5 && objective.startsWith("feB")
            && Character.isDigit(objective.charAt(3))
            && Character.isDigit(objective.charAt(4));
    }

    /**
     * The value an objective holds next: the computed one, clamped to the objective's range; for one that only rises,
     * never below what it holds ({@code current}, null when it holds nothing yet).
     */
    public static int next(String objective, Integer current, int computed) {
        int v = Math.max(0, Math.min(top(objective), computed));
        if (rises(objective) && current != null && current > v) return current;
        return v;
    }

    /** How much of the trunk is done: the number of its stars, from the anchor on, done without a gap. */
    public static int route(Collection<String> research) {
        int n = 0;
        while (n < TRUNK.size() && research.contains(TRUNK.get(n))) n++;
        return n;
    }

    /**
     * A team's state as version 1 reads it: the entries of its Echo Codex by category, the research it has done, the
     * highest phase of a formed nexus it is bound to (0 for none seen) and the buildings that stand formed for it
     * (module keys).
     */
    public static final class State {

        public final Map<String, Integer> records = new LinkedHashMap<>();
        public final Set<String> research = new LinkedHashSet<>();
        public int phase;
        public final Set<String> formed = new LinkedHashSet<>();
    }

    /**
     * The version 1 values of a team's state, for every objective of {@link #V1} in its order: 0 where the state has
     * nothing (a ladder at 0 is never written over a higher level, {@link #next}).
     */
    public static Map<String, Integer> values(State s) {
        Map<String, Integer> out = new LinkedHashMap<>();
        int records = 0, categories = 0;
        for (int n : s.records.values()) {
            if (n <= 0) continue;
            records += n;
            categories++;
        }
        out.put(REC, records);
        out.put(CAT, categories);
        out.put(PHASE, Math.max(0, Math.min(top(PHASE), s.phase)));
        out.put(RES, s.research.size());
        out.put(ROUTE, route(s.research));
        for (int row = 1; row <= 17; row++) out.put(building(row), s.formed.contains(BUILDINGS[row]) ? 1 : 0);
        return out;
    }
}
