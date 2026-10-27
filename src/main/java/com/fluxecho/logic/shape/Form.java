package com.fluxecho.logic.shape;

/**
 * A shape a block of the new material families takes (style FINAL §3.2): the sixteen of the common vocabulary, in the
 * hull's numbering (形号 0–15, {@link #ordinal()} = 形号 for these), then the footing's and the skins' own shapes that
 * replace some numbers in those families ({@link Family#form}). The names follow the style draft; {@link #zh} is its
 * word.
 * <p>
 * A shape is neighbour-aware and has no facing: the block looks at its neighbours to decide how to draw ({@link Nbhd},
 * {@link ShapeGeo}), so a blueprint forms in all four facings without a facing meta. Three properties drive the
 * neighbour model and the block split (§3.1, §3.3.1):
 * <ul>
 * <li>{@link #closed}: the shape is a body (实) to its neighbours' rules. True for every shape the whole-block id
 * holds, and for round, slope and soffit (and the footing's coping), which are bodies, not thin pieces; false for the
 * thin ones (half, step, rib, column, window, louvre, grate), which count as open (空) like air.</li>
 * <li>{@link #wholeBlock}: the shape lives in the family's whole-block id ({@code isOpaqueCube} true: whole, seam,
 * light seam, keel, lamp, floor, relief, and the footing's pool lip, spout and planter lip). Everything else lives in
 * the family's shape-block id ({@code *_form}, transparent, its own side culling).</li>
 * <li>{@link #seeThrough}: the shape lets the eye through (window, grate), so a neighbour never hides a face behind
 * it.</li>
 * </ul>
 */
public enum Form {

    WHOLE("整", true, true),
    SEAM("缝", true, true),
    ROUND("圆", true, false),
    SLOPE("坡", true, false),
    HALF("半", false, false),
    STEP("阶", false, false),
    RIB("肋", false, false),
    COLUMN("柱", false, false),
    WINDOW("窗", false, false),
    LOUVRE("格", false, false),
    LIGHT_SEAM("光缝", true, true),
    KEEL("龙骨", true, true),
    LAMP("灯窗", true, true),
    SOFFIT("拱腹", true, false),
    FLOOR("地", true, true),
    RELIEF("饰", true, true),
    /** The footing's 6: a retaining wall's top, its open top edges rounded, its corners square. */
    COPING("压顶", true, false),
    /** The footing's 9: a cube with a warm lip on its water side. */
    POOL_LIP("池沿", true, true),
    /** The footing's 11: a cube whose open side is a water outlet. */
    SPOUT("水口", true, true),
    /** The footing's 12: a cube with a planting edge. */
    PLANTER("种植沿", true, true),
    /** The footing's 13 and ink's 7: a see-through grating. */
    GRATE("格栅", false, false);

    /** The style draft's word for the shape. */
    public final String zh;
    private final boolean closed, wholeBlock;

    private static final Form[] VALUES = values();

    Form(String zh, boolean closed, boolean wholeBlock) {
        this.zh = zh;
        this.closed = closed;
        this.wholeBlock = wholeBlock;
    }

    /** Whether the shape counts as a body (实) in its neighbours' rules (§3.3.1). */
    public boolean closed() {
        return closed;
    }

    /** Whether the shape belongs to its family's whole-block id ({@code isOpaqueCube} true, a full cube). */
    public boolean wholeBlock() {
        return wholeBlock;
    }

    /** Whether the eye passes through the shape (window, grate): no neighbour face is ever hidden behind it. */
    public boolean seeThrough() {
        return this == WINDOW || this == GRATE;
    }

    /**
     * Whether the block's top is a solid side ({@code isSideSolid(UP)}): only for the whole-block shapes; shape blocks
     * answer false, so round crowns and slope tops gather no snow (§3.3.5).
     */
    public boolean solidTop() {
        return wholeBlock;
    }

    /** The shape of a {@link #ordinal()}, or null. */
    public static Form of(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : null;
    }
}
