package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * How a press on a light-lift pad becomes a ride request (0.12.0, {@link LiftInput}), replayed in vanilla's order: the
 * keys are applied before the world ticks, the player's tick starts (where the client looks), and only then does the
 * living update read the movement input and jump, so the player is in the air at the start of the next tick.
 */
class LiftInputTest {

    private static final int PX = 5, PY = 64, PZ = -3;
    private static final int[] PAD = { PX, PY, PZ };

    /**
     * A player standing on the pad and jumping as vanilla does: its feet, its vertical speed, whether on the ground.
     */
    private static final class Player {

        double x = PX + 0.5, feet = PY + 1, z = PZ + 0.5, motion;
        boolean onGround = true;
        /** What the movement input held after the last living update (the key as the old client read it). */
        boolean inputJump;

        /** The pad underfoot as the client finds it at the start of a tick: on the ground and over the pad. */
        int[] under() {
            return onGround && Math.floor(x) == PX && Math.floor(z) == PZ && Math.floor(feet - 0.01) == PY ? PAD : null;
        }

        /** The rest of vanilla's tick after its start: the movement input, the jump, the move. */
        void livingUpdate(boolean jumpKey) {
            inputJump = jumpKey;
            if (jumpKey && onGround) motion = 0.42;
            if (!onGround || motion > 0) {
                feet += motion;
                motion = (motion - 0.08) * 0.98;
                if (feet <= PY + 1) {
                    feet = PY + 1;
                    motion = 0;
                    onGround = true;
                } else onGround = false;
            }
        }
    }

    @Test
    void aJumpAsksOnTheTickTheKeyWentDown() {
        LiftInput in = new LiftInput();
        Player p = new Player();
        long t = 100;
        // a few ticks standing on the pad
        for (int i = 0; i < 3; i++, t++) {
            assertNull(in.tick(t, false, false, p.under(), p.x, p.feet, p.z, true));
            p.livingUpdate(false);
        }
        // the key goes down: applied before the world ticks, so its live state is down at the start of this tick,
        // while the player still stands on the pad
        int[] ask = in.tick(t, true, false, p.under(), p.x, p.feet, p.z, true);
        assertArrayEquals(new int[] { PX, PY, PZ, 1 }, ask, "up from the pad it stands on");
        p.livingUpdate(true);
        assertFalse(p.onGround, "vanilla jumped in the same tick");
        // held: never asked again, in the air or back on the pad
        for (int i = 0; i < 40; i++) {
            t++;
            assertNull(in.tick(t, true, false, p.under(), p.x, p.feet, p.z, true), "tick " + i);
            p.livingUpdate(true);
        }
        // let go and pressed again: a new request
        t++;
        assertNull(in.tick(t, false, false, p.under(), p.x, p.feet, p.z, true));
        p.livingUpdate(false);
        for (int i = 0; i < 20 && !p.onGround; i++) p.livingUpdate(false);
        t++;
        assertArrayEquals(new int[] { PX, PY, PZ, 1 }, in.tick(t, true, false, p.under(), p.x, p.feet, p.z, true));
    }

    @Test
    void aPressSeenATickLateAsksFromThePadItJumpedOff() {
        // the 0.12.0 client read the movement input, which holds the key as the last living update saw it: down only
        // at the start of the tick after the press, when the jump has already lifted the player off the pad
        LiftInput in = new LiftInput();
        Player p = new Player();
        long t = 7;
        assertNull(in.tick(t, p.inputJump, false, p.under(), p.x, p.feet, p.z, true));
        p.livingUpdate(true);
        t++;
        assertTrue(p.inputJump);
        assertNull(p.under(), "in the air at the start of the next tick: the old rule found no pad and asked nothing");
        int[] ask = in.tick(t, p.inputJump, false, p.under(), p.x, p.feet, p.z, true);
        assertArrayEquals(new int[] { PX, PY, PZ, 1 }, ask, "the pad it stood on a tick ago");
        assertTrue(LiftInput.boards(p.x, p.feet, p.z, PX, PY, PZ), "and the server takes it");
    }

    @Test
    void theRememberedPadLastsAMoment() {
        LiftInput in = new LiftInput();
        Player p = new Player();
        long t = 0;
        assertNull(in.tick(t, false, false, p.under(), p.x, p.feet, p.z, true));
        // it stepped off without a jump being seen for a while
        for (int i = 1; i <= LiftInput.MEMORY + 1; i++) {
            p.livingUpdate(i == 1);
            assertNull(in.tick(t + i, false, false, p.under(), p.x, p.feet, p.z, true));
        }
        p.livingUpdate(false);
        assertNull(
            in.tick(t + LiftInput.MEMORY + 2, true, false, p.under(), p.x, p.feet, p.z, true),
            "too late: the pad is forgotten");

        // remembered, but the player drifted off the pad's column
        LiftInput off = new LiftInput();
        assertNull(off.tick(50, false, false, PAD, PX + 0.5, PY + 1, PZ + 0.5, true));
        assertNull(off.tick(51, true, false, null, PX + 1.5, PY + 1.4, PZ + 0.5, true));
    }

    @Test
    void sneakingAsksDownOncePerPress() {
        LiftInput in = new LiftInput();
        Player p = new Player();
        assertArrayEquals(
            new int[] { PX, PY, PZ, -1 },
            in.tick(10, false, true, p.under(), p.x, p.feet, p.z, true),
            "a sneaking player stays on the pad");
        for (long t = 11; t < 30; t++) assertNull(in.tick(t, false, true, p.under(), p.x, p.feet, p.z, true));
        assertNull(in.tick(30, false, false, p.under(), p.x, p.feet, p.z, true));
        assertArrayEquals(new int[] { PX, PY, PZ, -1 }, in.tick(31, false, true, p.under(), p.x, p.feet, p.z, true));
    }

    @Test
    void aPressWhileRidingOrWaitingIsUsedUp() {
        LiftInput in = new LiftInput();
        Player p = new Player();
        assertNull(in.tick(5, true, false, p.under(), p.x, p.feet, p.z, false), "riding, or an answer awaited");
        assertNull(in.tick(6, true, false, p.under(), p.x, p.feet, p.z, true), "the key held on asks nothing");
        assertNull(in.tick(7, false, false, PAD, p.x, p.feet, p.z, true));
        assertArrayEquals(new int[] { PX, PY, PZ, 1 }, in.tick(8, true, false, PAD, p.x, p.feet, p.z, true));
        // off any pad: nothing
        LiftInput none = new LiftInput();
        assertNull(none.tick(1, true, false, null, 0.5, 70, 0.5, true));
    }

    @Test
    void theWindowTheServerAccepts() {
        assertTrue(LiftInput.boards(PX + 0.5, PY + 1, PZ + 0.5, PX, PY, PZ), "standing on the pad");
        assertTrue(LiftInput.boards(PX + 0.5, PY + 0.5, PZ + 0.5, PX, PY, PZ));
        assertTrue(LiftInput.boards(PX + 0.5, PY + 2.5, PZ + 0.5, PX, PY, PZ));
        assertFalse(LiftInput.boards(PX + 0.5, PY + 0.49, PZ + 0.5, PX, PY, PZ));
        assertFalse(LiftInput.boards(PX + 0.5, PY + 2.51, PZ + 0.5, PX, PY, PZ));
        assertFalse(LiftInput.boards(PX + 1.3, PY + 1, PZ + 0.5, PX, PY, PZ), "off the column");
        // the whole of a vanilla jump off the pad lies inside it
        Player p = new Player();
        p.livingUpdate(true);
        double top = p.feet;
        for (int i = 0; i < 30 && !p.onGround; i++) {
            assertTrue(LiftInput.boards(p.x, p.feet, p.z, PX, PY, PZ), "feet " + p.feet);
            top = Math.max(top, p.feet);
            p.livingUpdate(true);
        }
        assertTrue(top > PY + 2.2, "the jump's top: " + top);
    }
}
