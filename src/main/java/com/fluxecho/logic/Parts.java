package com.fluxecho.logic;

/**
 * The part codes of the campus builder: one small number for every block it can place, so plans, costs and packets
 * never need a {@code Block}. Frame metas are codes 0 to 15, deck metas 16 to 31, fitting metas 32 to 47; then the
 * library core, the supply port, grass and dirt for grading, and air. Only {@code campus.PartBlocks} turns a code into
 * a block.
 */
public final class Parts {

    public static final int FRAME = 0, DECK = 16, FITTING = 32, LIBRARY_CORE = 48, SUPPLY_PORT = 49, GRASS = 50,
        DIRT = 51, AIR = 63;

    // frame metas (as in BlockFrame)
    public static final int FR_BASE = 0, FR_BASE_LIT = 1, FR_PILLAR = 2, FR_CONDUIT = 3, FR_RING = 4, FR_SEAT = 5,
        FR_FOUNDATION = 6, FR_SHELF = 7, FR_CONSOLE = 8;

    // deck metas
    public static final int D_DECK = 0, D_TRIM = 1, D_LIT = 2, D_GRATE = 3, D_DARK = 4, D_SKIRT = 5, D_CHEVRON = 6,
        D_WELL = 7, D_PANEL = 8, D_PANEL_LIT = 9, D_PANEL_DARK = 10, D_CORNICE = 11, DECK_TYPES = 12;
    public static final String[] DECK_NAMES = { "deck", "trim", "lit", "grate", "dark", "skirt", "chevron", "well",
        "panel", "panel_lit", "panel_dark", "cornice" };
    public static final int[] DECK_LIGHT = { 0, 0, 9, 0, 0, 6, 7, 12, 0, 8, 4, 10 };

    // fitting metas
    public static final int F_POST = 0, F_PLINTH = 1, F_CROWN = 2, F_RAIL = 3, F_TREAD = 4, F_GLAZE = 5, F_PEDESTAL = 6,
        FITTING_TYPES = 7;
    public static final String[] FITTING_NAMES = { "post", "plinth", "crown", "rail", "tread", "glaze", "pedestal" };
    public static final int[] FITTING_LIGHT = { 6, 9, 10, 6, 7, 0, 8 };

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

    /** The meta within its block family (frame, deck, fitting); 0 for the others. */
    public static int meta(int code) {
        if (isFrame(code)) return code - FRAME;
        if (isDeck(code)) return code - DECK;
        if (isFitting(code)) return code - FITTING;
        return 0;
    }
}
