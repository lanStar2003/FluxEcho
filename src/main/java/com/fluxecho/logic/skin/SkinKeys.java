package com.fluxecho.logic.skin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The box table's building keys: the rows of the ID ledger's {@code Buildings} table (docs/dev/ids.md §2), row number =
 * the narrative's building number = the {@code feBxx} score. An entry carries the row (a short), never the registration
 * order of the module specs, so a table means the same on every server and in every version. Rows only ever come at
 * the end; {@code city/kit/Buildings} (0.19) holds the same rows.
 */
public final class SkinKeys {

    private static final List<String> KEYS = Collections.unmodifiableList(
        Arrays.asList(
            "drift_capsule",
            "nexus",
            "library",
            "eco_dome",
            "root",
            "dome_generator",
            "dual_foil_vault",
            "mana_fountain",
            "arcane_court",
            "resonance_gate",
            "sophon_array",
            "holo_table",
            "blood_covenant",
            "hunting_ground",
            "observatory",
            "sky_sails",
            "weather_station",
            "droplet_hangar",
            "orbital_anchor",
            "source_heart",
            "leap_ring",
            "ascension"));

    private SkinKeys() {}

    /** Every key, by row. */
    public static List<String> keys() {
        return KEYS;
    }

    /** The row of a module key, or -1 for a key that names no building. */
    public static int row(String key) {
        return key == null ? -1 : KEYS.indexOf(key);
    }

    /** The module key of a row, or null for a row there is not. */
    public static String key(int row) {
        return row >= 0 && row < KEYS.size() ? KEYS.get(row) : null;
    }
}
