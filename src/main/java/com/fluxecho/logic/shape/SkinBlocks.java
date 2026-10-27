package com.fluxecho.logic.shape;

/**
 * The pure facts of the new material families' block ids (0.17.0, style FINAL §3.1, ids.md §1.4 and §3.1): what a
 * block of a family shows and is at each meta, so the blocks, their items, the part table and the tests read one
 * answer.
 * <p>
 * Each family is two block ids. The whole-block id ({@code fluxecho:hull}) is an opaque cube and holds the shape
 * numbers whose shape is a full cube ({@link Form#wholeBlock}: whole, seam, light seam, keel, lamp, floor, relief and
 * the footing's pool lip, spout and planter lip): neighbours cull against it, and light and smooth lighting work as
 * on stone. The shape-block id ({@code fluxecho:hull_form}) holds the rest: rounds, slopes, halves, steps, ribs,
 * columns, windows, louvres, soffits, coping and grates, drawn from {@link ShapeGeo}. In both the meta is the shape
 * number (形号), so a family's sixteen numbers are split between its two ids and each id leaves the other's metas
 * unused; such a meta, and a number that has no geometry yet, draws as a whole cube and is no part.
 * <p>
 * Only the shapes the first batch gives a part code ({@link FormCodes}, 36 of the four families' 64) are listed in
 * the creative tab and in NEI; the others are registered with their textures, but no recipe or plan makes them, and a
 * block of theirs found on a site is cleared as a foreign block (style FINAL §3.8.1).
 */
public final class SkinBlocks {

    /** The shape-block id's registry name is the family's plus this. */
    public static final String FORM_SUFFIX = "_form";

    private SkinBlocks() {}

    /** Whether shape number {@code n} of the family lives in the family's whole-block id. */
    public static boolean inWhole(Family f, int n) {
        Form g = f.form(n);
        return g != null && g.wholeBlock();
    }

    /** The registry name (without the domain) of the block id holding shape number {@code n} of the family. */
    public static String blockName(Family f, int n) {
        return inWhole(f, n) ? f.id : f.id + FORM_SUFFIX;
    }

    /**
     * The geometry a block of the family draws at a meta: the family's shape for a meta that belongs to the block, a
     * whole cube for a meta of the other id or a number without geometry yet (white marble's baluster, 0.20.0).
     */
    public static Form geometry(Family f, boolean wholeId, int meta) {
        Form g = meta >= 0 && meta < 16 ? f.form(meta) : null;
        if (g == null || g.wholeBlock() != wholeId) return Form.WHOLE;
        return g;
    }

    /** Whether the meta belongs to the block: its shape lives in that id (a number without geometry: the shape id). */
    public static boolean belongs(Family f, boolean wholeId, int meta) {
        return meta >= 0 && meta < 16 && inWhole(f, meta) == wholeId;
    }

    /**
     * The part code of a block of the family at a meta, -1 when that block and meta are no part: a meta of the other
     * id, or a shape the first batch gives no code.
     */
    public static int code(Family f, boolean wholeId, int meta) {
        return belongs(f, wholeId, meta) ? FormCodes.code(f, meta) : -1;
    }

    /** Whether the item of a block of the family at a meta is listed (creative tab, NEI): it has a part code. */
    public static boolean listed(Family f, boolean wholeId, int meta) {
        return code(f, wholeId, meta) >= 0;
    }

    /** The listed metas of a block of the family, in order: its part codes' shape numbers. */
    public static int[] listedMetas(Family f, boolean wholeId) {
        int n = 0;
        for (int m = 0; m < 16; m++) if (listed(f, wholeId, m)) n++;
        int[] out = new int[n];
        n = 0;
        for (int m = 0; m < 16; m++) if (listed(f, wholeId, m)) out[n++] = m;
        return out;
    }

    /**
     * The block light a shape gives (style FINAL §3.5): a light seam 7, a keel 10, a lamp window (and the paper's
     * lantern panel) 9, a step 6 for its warm nose, the footing's pool lip 4 for its warm lip; nothing else. Whether a
     * glow is drawn lit is the box table's (lamp windows only at night, G6); the block light stays, as a block's light
     * cannot change without changing the world.
     */
    public static int light(Form g) {
        switch (g) {
            case LIGHT_SEAM:
                return 7;
            case KEEL:
                return 10;
            case LAMP:
                return 9;
            case STEP:
                return 6;
            case POOL_LIP:
                return 4;
            default:
                return 0;
        }
    }

    /**
     * How much light a shape-block cell takes out of the light passing through it: the bodies (round, slope, soffit,
     * coping) and the slabs (half, step, as vanilla's slabs) all of it, so a roof of slopes shades the room under it;
     * the thin and see-through ones (rib, column, window, louvre, grate) none. A whole cube in the shape id (a stray
     * meta) all of it.
     */
    public static int lightOpacity(Form g) {
        switch (g) {
            case RIB:
            case COLUMN:
            case WINDOW:
            case LOUVRE:
            case GRATE:
                return 0;
            default:
                return 255;
        }
    }

    /**
     * The name of shape number {@code n} of a family (item names, {@code tile.fluxecho.<family>.<name>}): the shape's
     * name for the hull and the footing ({@code round}, {@code pool_lip}), the material's and the shape's for a skin
     * ({@code ink_grate}, {@code moss_window}); the paper's lamp is its lantern panel, its special its picture, white
     * marble's special its baluster.
     */
    public static String slug(Family f, int n) {
        if (f == Family.SKIN_SCRIPT && n == 13) return "paper_lantern";
        if (f == Family.SKIN_SCRIPT && n == 15) return "paper_picture";
        if (f == Family.SKIN_GARDEN && n == 15) return "marble_baluster";
        Form g = f.form(n);
        String name = g == null ? "whole"
            : g.name()
                .toLowerCase(java.util.Locale.ROOT);
        if (f == Family.HULL || f == Family.FOOTING) return name;
        return material(f, n) + "_" + name;
    }

    /**
     * The material, the texture set, of shape number {@code n} of a family (style FINAL §3.5): {@code hull},
     * {@code footing}; moss bronze ({@code moss}) and white marble ({@code marble}) in the garden skin, ink
     * ({@code ink}) and paper ({@code paper}) in the script skin, each skin's first material at 0 to 7.
     */
    public static String material(Family f, int n) {
        switch (f) {
            case HULL:
                return "hull";
            case FOOTING:
                return "footing";
            case SKIN_GARDEN:
                return n < 8 ? "moss" : "marble";
            default:
                return n < 8 ? "ink" : "paper";
        }
    }
}
