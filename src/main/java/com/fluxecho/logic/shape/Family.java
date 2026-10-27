package com.fluxecho.logic.shape;

/**
 * A material family of the new blocks (style FINAL §3.1, §3.2) and what each of its sixteen shape numbers (形号)
 * means as geometry. All families share the vocabulary of {@link Form}; they differ in which numbers they use for
 * what:
 * <ul>
 * <li>the hull uses the sixteen as they are;</li>
 * <li>the footing replaces 6 rib with coping, 8 window with soffit, 9 louvre with pool lip, 11 keel with spout, 12
 * lamp with planter lip and 13 soffit with grate (it has no lamp windows, G1);</li>
 * <li>a skin family holds two materials of eight shapes each (0 whole, 1 seam, 2 round, 3 slope, 4 light seam, 5 lamp,
 * 6 soffit, 7 the material's special), the second material at 8–15 in the same order; skins have no rib and no
 * column (the bones are white, §3.2).</li>
 * </ul>
 * Only the four families of the 0.17.0 batch are listed (refactor §2.6, ids.md §1.4); the other skins come with the
 * 0.20.0 code widening together with their specials' geometry.
 */
public enum Family {

    HULL("hull", "舟壳"),
    FOOTING("footing", "玄基"),
    /** Moss bronze (0–7) and white marble (8–15). */
    SKIN_GARDEN("skin_garden", "苔铜 · 汉白玉"),
    /** Ink (0–7) and paper (8–15). */
    SKIN_SCRIPT("skin_script", "墨 · 宣");

    /** The registry name of the whole-block id; the shape-block id is this plus {@code _form}. */
    public final String id;
    /** The style draft's name for the family's material(s). */
    public final String zh;

    private static final Form[] FOOTING_FORMS = { Form.WHOLE, Form.SEAM, Form.ROUND, Form.SLOPE, Form.HALF, Form.STEP,
        Form.COPING, Form.COLUMN, Form.SOFFIT, Form.POOL_LIP, Form.LIGHT_SEAM, Form.SPOUT, Form.PLANTER, Form.GRATE,
        Form.FLOOR, Form.RELIEF };
    /** A skin material's eight shapes; slot 7 is the material's special, filled per material. */
    private static final Form[] SKIN_FORMS = { Form.WHOLE, Form.SEAM, Form.ROUND, Form.SLOPE, Form.LIGHT_SEAM,
        Form.LAMP, Form.SOFFIT, null };

    Family(String id, String zh) {
        this.id = id;
        this.zh = zh;
    }

    /**
     * The geometry of shape number {@code number} (0–15) in this family, or null when the family's number has no
     * geometry yet (white marble's baluster, 15 of the garden skin, comes with 0.20.0).
     */
    public Form form(int number) {
        if (number < 0 || number > 15) return null;
        switch (this) {
            case HULL:
                return Form.of(number);
            case FOOTING:
                return FOOTING_FORMS[number];
            default:
                int slot = number & 7;
                if (slot != 7) return SKIN_FORMS[slot];
                return special(number >> 3);
        }
    }

    /** The special shape (slot 7) of the skin's first (0) or second (1) material. */
    private Form special(int material) {
        if (this == SKIN_GARDEN) return material == 0 ? Form.WINDOW : null; // copper-framed window; baluster later
        if (this == SKIN_SCRIPT) return material == 0 ? Form.GRATE : Form.WHOLE; // ink lattice; the paper's picture
        return null;
    }
}
