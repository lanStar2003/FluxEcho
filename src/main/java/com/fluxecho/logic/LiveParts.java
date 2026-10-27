package com.fluxecho.logic;

/**
 * The live materials of the city's gardens (0.17.0, style FINAL §3.7; part codes 54 to 58, ids.md §1.2): white birch
 * logs, oak leaves that never decay, vines, lily pads and flowers. They are vanilla blocks and never ours, so the
 * builder treats them the way it treats grass and water:
 * <ul>
 * <li>a plan only ever lays them in soft cells (built, never checked): a player may cut, pick or replant them and the
 * building still forms, and a repair only fills the cells that are empty;</li>
 * <li>a block of their kind counts as the part only in the cell a plan wants it ({@link #accepts}, through
 * {@code Terrain.isTarget}); anywhere else it is the natural plant it looks like, and clearing takes it as one
 * ({@code PartBlocks.code} never names them);</li>
 * <li>the generic module plan lays them last, at the end of its last stage before the commissioning, bottom-up
 * ({@code BuildPlan.module}, refactor §1.1, PlantOrderTest): the water a lily floats on, the wall a vine hangs from
 * and the grass a flower stands in are there before them, so no plant is ever placed where it cannot stay.</li>
 * </ul>
 * The blocks themselves ({@code Blocks.log} and so on) are looked up by {@code campus.PartBlocks}; this class keeps
 * the rules that need no Minecraft.
 */
public final class LiveParts {

    /** The placing metas: a birch log standing upright, oak leaves that never decay; vines, lilies, a poppy at 0. */
    private static final int BIRCH_UPRIGHT = 2, OAK_NO_DECAY = 4;

    private LiveParts() {}

    /** Whether the part is one of the live materials. */
    public static boolean is(int code) {
        return Parts.isLive(code);
    }

    /**
     * The meta a live part is placed with: the birch log upright (2), the oak leaves with their no-decay bit (4), the
     * rest 0. A vine of meta 0 hangs from the block over it, so a plan puts its vines under a solid block (the coping
     * of
     * a retaining wall); from there they spread down the wall by themselves.
     *
     * @throws IllegalArgumentException for a part that is not live
     */
    public static int meta(int code) {
        switch (code) {
            case Parts.BIRCH:
                return BIRCH_UPRIGHT;
            case Parts.OAK_LEAVES:
                return OAK_NO_DECAY;
            case Parts.VINE:
            case Parts.LILY:
            case Parts.FLOWER:
                return 0;
            default:
                throw new IllegalArgumentException("not a live part: " + code);
        }
    }

    /**
     * Whether a block of the live part's kind with this meta is the part the plan wants: a birch log lying any way
     * (its axis bits ignored), oak leaves whatever their decay bits say, any vine, any lily and any flower (the
     * flower code takes a poppy or a dandelion alike, so a player's replanting counts). The caller has already checked
     * the block.
     */
    public static boolean accepts(int code, int meta) {
        switch (code) {
            case Parts.BIRCH:
                return (meta & 3) == BIRCH_UPRIGHT;
            case Parts.OAK_LEAVES:
                return (meta & 3) == 0;
            case Parts.VINE:
            case Parts.LILY:
            case Parts.FLOWER:
                return true;
            default:
                return false;
        }
    }
}
