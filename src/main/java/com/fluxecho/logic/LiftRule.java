package com.fluxecho.logic;

/**
 * Where a nexus core goes when a player places it. Clicking the top of the ground puts the core two above the clicked
 * block instead of one, so the clicked layer becomes the nexus base, the cell above it the console stand, and the core
 * sits at eye height where the finished nexus keeps its controller. The client and the server both call this rule
 * with the same inputs, so the client's predicted placement never differs from the server's and the block never
 * flickers.
 */
public final class LiftRule {

    /** The top face of a block (Forge {@code ForgeDirection.UP}, vanilla side 1). */
    public static final int TOP = 1;
    /** How far above the clicked block a lifted core goes. */
    public static final int LIFT = 2;

    private LiftRule() {}

    /**
     * The core's height above the clicked block: {@link #LIFT} (2) when the top face of a block that is not ours was
     * clicked, the player is not sneaking and the two cells above the clicked block are free (no block, no entity in
     * the way); otherwise 0, which means normal placement against the clicked side.
     *
     * @param side        the clicked side (0 bottom, 1 top, 2 to 5 the horizontal sides)
     * @param sneaking    whether the player sneaks (sneak-placing is the manual 0.9.2 path and never lifts)
     * @param clickedOurs whether the clicked block is one of our frame or deck blocks (building on a structure)
     * @param above1Free  whether the cell right above the clicked block can take a block
     * @param above2Free  whether the cell two above the clicked block can take a block
     */
    public static int lift(int side, boolean sneaking, boolean clickedOurs, boolean above1Free, boolean above2Free) {
        if (side != TOP || sneaking || clickedOurs) return 0;
        return above1Free && above2Free ? LIFT : 0;
    }
}
