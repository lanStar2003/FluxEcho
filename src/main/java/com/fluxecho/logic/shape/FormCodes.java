package com.fluxecho.logic.shape;

/**
 * Which family and shape number each part code of the 0.17.0 batch places (ids.md §1.2 and §1.4, style FINAL §3.8.1):
 * the hull's twelve (96–107), the footing's ten (108–117), the script skin's eight (118–125) and the garden skin's six
 * (126, 127, 59–62). Codes never change once published; the 0.20.0 widening (128 and up) is added with its release.
 * <p>
 * This is the pure half of the part table: the block registry ({@code PartBlocks}) maps a family and shape number to a
 * block id and meta, the walk tests ({@code KitClimbTest}) read a legend cell's {@link Form} from here to take its
 * collision boxes from {@link ShapeGeo#boxes}, the same boxes the block uses.
 */
public final class FormCodes {

    private static final int[] HULL = { 0, 1, 2, 3, 4, 5, 6, 7, 10, 11, 13, 15 },
        FOOTING = { 0, 1, 2, 3, 4, 5, 6, 9, 11, 12 }, SCRIPT = { 0, 2, 4, 7, 8, 9, 12, 13 };
    /** The garden skin's codes and their shape numbers: 126, 127 and the four codes 59–62 of the first batch. */
    private static final int[] GARDEN_CODES = { 126, 127, 59, 60, 61, 62 }, GARDEN = { 0, 2, 3, 4, 5, 7 };

    private FormCodes() {}

    /** The family a part code places, or null when the code is not one of the new families' shapes. */
    public static Family family(int code) {
        if (code >= 96 && code <= 107) return Family.HULL;
        if (code >= 108 && code <= 117) return Family.FOOTING;
        if (code >= 118 && code <= 125) return Family.SKIN_SCRIPT;
        return gardenIndex(code) >= 0 ? Family.SKIN_GARDEN : null;
    }

    /** The shape number (形号, the meta within the family) a part code places, or -1. */
    public static int number(int code) {
        if (code >= 96 && code <= 107) return HULL[code - 96];
        if (code >= 108 && code <= 117) return FOOTING[code - 108];
        if (code >= 118 && code <= 125) return SCRIPT[code - 118];
        int g = gardenIndex(code);
        return g >= 0 ? GARDEN[g] : -1;
    }

    /** The geometry a part code places, or null when it is not one of the new families' shapes. */
    public static Form form(int code) {
        Family f = family(code);
        return f == null ? null : f.form(number(code));
    }

    /** The part code of a family's shape number in this batch, or -1 when the batch gives it no code. */
    public static int code(Family family, int number) {
        for (int c = 0; c < 256; c++) if (family(c) == family && number(c) == number) return c;
        return -1;
    }

    private static int gardenIndex(int code) {
        for (int i = 0; i < GARDEN_CODES.length; i++) if (GARDEN_CODES[i] == code) return i;
        return -1;
    }
}
