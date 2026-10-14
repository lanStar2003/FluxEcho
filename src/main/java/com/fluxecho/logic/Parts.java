package com.fluxecho.logic;

/**
 * The part codes of the campus builder: one small number for every block it can place, so plans, costs and packets
 * never need a {@code Block}. Frame metas are codes 0 to 15, deck metas 16 to 31, fitting metas 32 to 47; then the
 * library core, the supply port, grass and dirt for grading, the recycle bin (0.10.1), and air. Only
 * {@code campus.PartBlocks} turns a code into a block.
 * <p>
 * The codes are saved (the ledger's parts, flights in the air) and sent ({@code BUILD_FX}, one byte a code): a code
 * is only ever appended, never renumbered or reused. 0.11.0 appends the frame's cladding anchor (9), the fittings'
 * socket (39) and basin rim (40), water (53) and the module cores of {@code fluxecho:module_core} (64 + meta); 0.12.0
 * the fittings' light-lift pad (41); 0.14.0 the deck's canvas (28), the wall a module's units paint their items on;
 * 0.16.0 the frame's floating foundation (10), the anti-gravity lattice cube the sky platforms are decked with.
 */
public final class Parts {

    public static final int FRAME = 0, DECK = 16, FITTING = 32, LIBRARY_CORE = 48, SUPPLY_PORT = 49, GRASS = 50,
        DIRT = 51, AIR = 63;
    /** The recycle bin (0.10.1): where the campus's recycle store is taken out by hand and by automation. */
    public static final int RECYCLE_BIN = 52;
    /**
     * A water source (0.11.0): placed like grass and dirt, paid for in EU only (a pond, a fountain, a tidal pool).
     */
    public static final int WATER = 53;
    /**
     * The module cores of {@code fluxecho:module_core} (0.11.0): code {@code MODULE_CORE + meta}, meta the module
     * (0 the Eco Dome, CITY.md Appendix C); {@code fluxecho:module_core2} will take 80 to 95.
     */
    public static final int MODULE_CORE = 64, MODULE_CORES = 16;

    // frame metas (as in BlockFrame; 0.16.0 appends the floating foundation, CITY.md §1.4: an anti-gravity lattice
    // cube with a lifting glow on its bottom face, light 7, the walking deck of the sky platforms)
    public static final int FR_BASE = 0, FR_BASE_LIT = 1, FR_PILLAR = 2, FR_CONDUIT = 3, FR_RING = 4, FR_SEAT = 5,
        FR_FOUNDATION = 6, FR_SHELF = 7, FR_CONSOLE = 8, FR_ANCHOR = 9, FR_FLOAT = 10;

    // deck metas (0.14.0 appends the canvas, CITY.md §1.4: a panel whose faces are dark woven navy with a faint cyan
    // grid, giving no light; a unit's painting is drawn on its field)
    public static final int D_DECK = 0, D_TRIM = 1, D_LIT = 2, D_GRATE = 3, D_DARK = 4, D_SKIRT = 5, D_CHEVRON = 6,
        D_WELL = 7, D_PANEL = 8, D_PANEL_LIT = 9, D_PANEL_DARK = 10, D_CORNICE = 11, D_CANVAS = 12, DECK_TYPES = 13;
    public static final String[] DECK_NAMES = { "deck", "trim", "lit", "grate", "dark", "skirt", "chevron", "well",
        "panel", "panel_lit", "panel_dark", "cornice", "canvas" };
    public static final int[] DECK_LIGHT = { 0, 0, 9, 0, 0, 6, 7, 12, 0, 8, 4, 10, 0 };

    // fitting metas
    public static final int F_POST = 0, F_PLINTH = 1, F_CROWN = 2, F_RAIL = 3, F_TREAD = 4, F_GLAZE = 5, F_PEDESTAL = 6,
        F_SOCKET = 7, F_BASIN = 8, F_LIFT = 9, FITTING_TYPES = 10;
    public static final String[] FITTING_NAMES = { "post", "plinth", "crown", "rail", "tread", "glaze", "pedestal",
        "socket", "basin", "lift" };
    public static final int[] FITTING_LIGHT = { 6, 9, 10, 6, 7, 0, 8, 8, 4, 12 };

    private Parts() {}

    public static int frame(int meta) {
        return FRAME + meta;
    }

    public static int deck(int meta) {
        return DECK + meta;
    }

    public static int fitting(int meta) {
        return FITTING + meta;
    }

    public static boolean isFrame(int code) {
        return code >= FRAME && code < DECK;
    }

    public static boolean isDeck(int code) {
        return code >= DECK && code < FITTING;
    }

    public static boolean isFitting(int code) {
        return code >= FITTING && code < LIBRARY_CORE;
    }

    /** The code of the module core of meta {@code meta} ({@code fluxecho:module_core}). */
    public static int moduleCore(int meta) {
        if (meta < 0 || meta >= MODULE_CORES) throw new IllegalArgumentException("no module core meta " + meta);
        return MODULE_CORE + meta;
    }

    public static boolean isModuleCore(int code) {
        return code >= MODULE_CORE && code < MODULE_CORE + MODULE_CORES;
    }

    /**
     * Whether a part is paid for in EU alone, never in materials: grass and dirt (grading) and water (0.11.0). The
     * builder charges such a cell the clearing EU and nothing from the ledger.
     */
    public static boolean euOnly(int code) {
        return code == GRASS || code == DIRT || code == WATER;
    }

    /** The meta within its block family (frame, deck, fitting, module core); 0 for the others. */
    public static int meta(int code) {
        if (isFrame(code)) return code - FRAME;
        if (isDeck(code)) return code - DECK;
        if (isFitting(code)) return code - FITTING;
        if (isModuleCore(code)) return code - MODULE_CORE;
        return 0;
    }
}
