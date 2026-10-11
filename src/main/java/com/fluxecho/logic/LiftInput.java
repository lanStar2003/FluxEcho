package com.fluxecho.logic;

/**
 * 光梯 How a press of jump or sneak on a light-lift pad becomes a ride request (0.12.0, CITY.md §2.5,
 * {@link LiftShaft}): the client's rule, and the window in which the server accepts the request ({@link #boards}).
 * <p>
 * Vanilla applies the keys before it ticks the world, and a jump lifts the player off the ground in the same tick the
 * key went down: at the start of the next tick the player is in the air. So the client reads the keys' live state at
 * the start of the player's tick, while the player still stands on the pad, and it also remembers the pad it last
 * stood on for {@link #MEMORY} ticks: a press seen a tick late, after the jump took the player off the pad, still asks
 * from the pad it jumped off, as long as the player is still over that pad's column and inside the window the server
 * accepts. A press asks once; the key must be let go before it asks again, so holding jump never asks twice.
 * Sneaking keeps the player on the ground, so a sneak always finds the pad underfoot.
 */
public final class LiftInput {

    /** How many ticks after it left the ground the pad a player stood on still counts as underfoot. */
    public static final int MEMORY = 2;
    /**
     * The window of the feet over a pad at {@code y} in which a request from it is accepted: from {@code y + 0.5} to
     * {@code y + 2.5}, so a player standing on it ({@code y + 1}) or at the top of a jump off it (vanilla's jump rises
     * about 1.25) may ask.
     */
    public static final double BOARD_LO = 0.5, BOARD_HI = 2.5;

    private boolean jumpWas, sneakWas;
    private int[] pad;
    private long padAt = Long.MIN_VALUE;

    /**
     * Whether a player whose feet are at {@code feet} and who stands at {@code (x, z)} may ask for a ride from the pad
     * at {@code (padX, padY, padZ)}: over the pad's column ({@link LiftShaft#onColumn}) and inside the window
     * {@link #BOARD_LO}..{@link #BOARD_HI} over it. The server checks the same.
     */
    public static boolean boards(double x, double feet, double z, int padX, int padY, int padZ) {
        return LiftShaft.onColumn(x, z, padX, padZ) && feet >= padY + BOARD_LO && feet <= padY + BOARD_HI;
    }

    /**
     * One tick of the local player, at the start of its tick: the request it makes now, {x, y, z, dir} (the pad and
     * +1 up or -1 down), or null.
     *
     * @param now   the world tick
     * @param jump  whether the jump key is down now (its live state)
     * @param sneak whether the sneak key is down now
     * @param under the pad {x, y, z} the player stands on now (on the ground), or null
     * @param x     where the player is
     * @param feet  the height of its feet
     * @param z     where the player is
     * @param free  whether it may ask now: it rides none and waits for no answer
     */
    public int[] tick(long now, boolean jump, boolean sneak, int[] under, double x, double feet, double z,
        boolean free) {
        if (under != null) {
            pad = under.clone();
            padAt = now;
        }
        int dir = jump && !jumpWas ? 1 : sneak && !sneakWas ? -1 : 0;
        jumpWas = jump;
        sneakWas = sneak;
        if (dir == 0 || !free) return null;
        int[] from = under != null ? under : remembered(now, x, feet, z);
        if (from == null) return null;
        // the pad is used up: a request is made once from it
        pad = null;
        padAt = Long.MIN_VALUE;
        return new int[] { from[0], from[1], from[2], dir };
    }

    /** The pad the player stood on a moment ago, while it is still over it and inside the window; else null. */
    private int[] remembered(long now, double x, double feet, double z) {
        if (pad == null || now - padAt > MEMORY || now < padAt) return null;
        return boards(x, feet, z, pad[0], pad[1], pad[2]) ? pad : null;
    }

    /** Forgets the keys and the pad (leaving a world). */
    public void reset() {
        jumpWas = sneakWas = false;
        pad = null;
        padAt = Long.MIN_VALUE;
    }
}
