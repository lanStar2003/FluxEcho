package com.fluxecho.logic;

/**
 * The metas of {@code fluxecho:module_core} (0.11.0, CITY.md Appendix C): one core block for every city module, the
 * meta naming the module, its part code {@link Parts#MODULE_CORE} + meta. A meta is the module's for good (it is the
 * block in the world and the part in a ledger); new modules take the next free one, and
 * {@code fluxecho:module_core2} (codes 80 to 95) waits for the seventeenth.
 */
public final class ModuleCores {

    public static final int ECO_DOME = 0, DUAL_FOIL_VAULT = 1, ARCANE_COURT = 2, BLOOD_COVENANT = 3, HUNTING_GROUND = 4,
        MANA_FOUNTAIN = 5, RESONANCE_GATE = 6, DOME_GENERATOR = 7, ROOT = 8, SOPHON_ARRAY = 9, HOLO_TABLE = 10,
        SKY_SAILS = 11, OBSERVATORY = 12, DROPLET_HANGAR = 13, WEATHER_STATION = 14, ORBITAL_ANCHOR = 15;
    /** The module key of every meta, in meta order. */
    public static final String[] KEYS = { "eco_dome", "dual_foil_vault", "arcane_court", "blood_covenant",
        "hunting_ground", "mana_fountain", "resonance_gate", "dome_generator", "root", "sophon_array", "holo_table",
        "sky_sails", "observatory", "droplet_hangar", "weather_station", "orbital_anchor" };
    /**
     * The metas that have a module in this version, in meta order (0.11.0 the Eco Dome; 0.12.0 the Echo Dome
     * Generator and the root's Tidal Well; 0.13.0 the four pavilions: the Arcane Court, the Blood Covenant Altar, the
     * Echo Hunting Ground and the Mana Fountain Array; 0.14.0 the gate's Resonance Gate and the Dual-foil Vault; 0.15.0
     * the outer diagonals' Sophon Array and Holo Sand Table); the rest are reserved. Their cores are listed in the
     * creative tab and have their own icons.
     */
    private static final int[] BUILT = { ECO_DOME, DUAL_FOIL_VAULT, ARCANE_COURT, BLOOD_COVENANT, HUNTING_GROUND,
        MANA_FOUNTAIN, RESONANCE_GATE, DOME_GENERATOR, ROOT, SOPHON_ARRAY, HOLO_TABLE };

    private ModuleCores() {}

    /** The metas that have a module in this version, in meta order (a copy). */
    public static int[] built() {
        return BUILT.clone();
    }

    /** Whether the meta has a module in this version. */
    public static boolean built(int meta) {
        for (int m : BUILT) if (m == meta) return true;
        return false;
    }

    /** The meta of a module key, or -1. */
    public static int meta(String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return i;
        return -1;
    }

    /** The module key of a meta, or null outside the table. */
    public static String key(int meta) {
        return meta >= 0 && meta < KEYS.length ? KEYS[meta] : null;
    }
}
