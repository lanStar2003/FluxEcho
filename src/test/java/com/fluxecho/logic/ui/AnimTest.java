package com.fluxecho.logic.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Motion tokens and easings (ui/FINAL.md section 1.7): the durations, the curves, and the rule that every loop is a
 * pure function of the clock (drawing the same frame twice gives the same picture).
 */
class AnimTest {

    @Test
    void durationsAreTheTokens() {
        assertEquals(90, Anim.MICRO);
        assertEquals(160, Anim.SHORT);
        assertEquals(240, Anim.MEDIUM);
        assertEquals(420, Anim.LONG);
        assertEquals(900, Anim.EPIC);
        assertEquals(2400, Anim.BREATHE);
        assertEquals(1200, Anim.PULSE);
        assertEquals(3000, Anim.SCAN);
        assertEquals(2000, Anim.HOLD);
        assertEquals(120, Anim.DECODE);
        assertEquals(40f, Anim.FLOW);
        assertEquals(250, Anim.TOOLTIP_DELAY, "the ink card shows after 250 ms of hover (C07)");
        assertEquals(530, Anim.CARET, "the caret blinks every 530 ms (C24)");
    }

    @Test
    void easingsRunFromZeroToOne() {
        for (int k = 0; k < 3; k++) {
            assertEquals(0f, ease(k, 0), 1e-4);
            assertEquals(1f, ease(k, 1), 1e-4);
            assertEquals(0f, ease(k, -1), 1e-4, "clamped below");
            assertEquals(1f, ease(k, 2), 1e-4, "clamped above");
        }
        // out = cubic-bezier(0.2, 0.8, 0.2, 1): quick start, long tail, never past 1
        float prev = 0;
        for (int i = 1; i <= 100; i++) {
            float v = Anim.out(i / 100f);
            assertTrue(v >= prev - 1e-6 && v <= 1 + 1e-6, "monotone at " + i);
            prev = v;
        }
        assertTrue(Anim.out(0.25f) > 0.6f, "most of the way in the first quarter");
        assertEquals(0.5f, Anim.inOut(0.5f), 1e-4, "sine in-out is symmetric");
        assertEquals(0.25f, Anim.inOut(0.25f) + Anim.inOut(0.75f) - 0.75f, 1e-4);
        // back overshoots then settles
        float peak = 0;
        for (int i = 0; i <= 100; i++) peak = Math.max(peak, Anim.back(i / 100f));
        assertTrue(peak > 1.02f, "back overshoots");
        assertEquals(0.5f, Anim.linear(0.5f));
        assertEquals(0f, Anim.step(0.99f));
        assertEquals(1f, Anim.step(1f));
    }

    static float ease(int k, float t) {
        return k == 0 ? Anim.out(t) : k == 1 ? Anim.inOut(t) : Anim.back(t);
    }

    @Test
    void progressIsClampedTime() {
        assertEquals(0f, Anim.progress(1000, 1000, Anim.MEDIUM));
        assertEquals(0.5f, Anim.progress(1120, 1000, Anim.MEDIUM));
        assertEquals(1f, Anim.progress(5000, 1000, Anim.MEDIUM));
        assertEquals(0f, Anim.progress(900, 1000, Anim.MEDIUM), "not started yet");
        assertEquals(1f, Anim.progress(1000, 1000, 0), "zero length is done");
    }

    /** Loops are functions of the clock alone: same time, same value; one period later, same value. */
    @Test
    void loopsArePureFunctionsOfTime() {
        for (long t = 0; t < 10000; t += 37) {
            assertEquals(Anim.breathe(t), Anim.breathe(t));
            assertEquals(Anim.breathe(t), Anim.breathe(t + Anim.BREATHE), 1e-5);
            assertEquals(Anim.pulse(t), Anim.pulse(t + Anim.PULSE), 1e-5);
            assertEquals(Anim.scan(t), Anim.scan(t + Anim.SCAN), 1e-5);
            float b = Anim.breathe(t), p = Anim.pulse(t), s = Anim.scan(t);
            assertTrue(b >= 0 && b <= 1 && p >= 0 && p <= 1 && s >= 0 && s < 1, "range at " + t);
        }
        assertEquals(0f, Anim.breathe(0), 1e-5);
        assertEquals(1f, Anim.breathe(Anim.BREATHE / 2), 1e-5);
        assertEquals(20f, Anim.flow(500, Anim.FLOW, 1000), 1e-4, "40 px/s for half a second");
        assertEquals(0f, Anim.flow(25_000, Anim.FLOW, 1000), 1e-3, "wraps at the period");
    }

    /** "Unlocked digits change every 120 ms: hash(position, floor(t / 120)) mod 10" (C47). */
    @Test
    void decodeDigitsAreStableWithinAStep() {
        for (int pos = 0; pos < 10; pos++) for (long t = 0; t < 2400; t += 120) {
            int d = Anim.decodeDigit(pos, t);
            assertTrue(d >= 0 && d <= 9);
            assertEquals(d, Anim.decodeDigit(pos, t + 119), "same step");
        }
        int changes = 0;
        for (long t = 0; t < 120 * 100; t += 120) if (Anim.decodeDigit(3, t) != Anim.decodeDigit(3, t + 120)) changes++;
        assertTrue(changes > 60, "the digit keeps changing");
    }

    /** The window opening: line 0-90 ms, panel 90-240 ms, content fading in from 90 ms over 120 ms (C01, 1.7.2 #6). */
    @Test
    void openingInThreeStages() {
        assertEquals(240, Anim.OPEN_TOTAL);
        Anim.Opening a = Anim.open(0);
        assertEquals(0f, a.line);
        assertEquals(0f, a.panel);
        assertEquals(0f, a.content);
        Anim.Opening b = Anim.open(90);
        assertEquals(1f, b.line, 1e-5);
        assertEquals(0f, b.panel, 1e-5);
        assertEquals(0f, b.content, 1e-5);
        Anim.Opening c = Anim.open(210);
        assertEquals(1f, c.content, 1e-5);
        assertTrue(c.panel > 0.5f && c.panel < 1f);
        Anim.Opening d = Anim.open(240);
        assertEquals(1f, d.panel, 1e-5);
        assertEquals(
            2f,
            Anim.open(90)
                .height(100),
            1e-5,
            "the panel grows from 2 px");
        assertEquals(100f, d.height(100), 1e-5);
    }

    /** Numbers roll for 240 ms; a jump of more than ten times is shown at once (1.7.2 #5). */
    @Test
    void numbersRollUnlessTheyJump() {
        Anim.Roll r = new Anim.Roll();
        r.set(100, 0);
        assertEquals(100, r.value(0), 1e-9, "the first value is shown as it is");
        r.set(200, 1000);
        assertEquals(100, r.value(1000), 1e-9);
        double mid = r.value(1120);
        assertTrue(mid > 150 && mid < 200, "rolling: " + mid);
        assertEquals(200, r.value(1000 + Anim.MEDIUM), 1e-9);
        assertEquals(r.value(1120), r.value(1120), 0, "same frame, same number");
        r.set(5000, 2000);
        assertEquals(5000, r.value(2000), 1e-9, "a 25x jump is not rolled");
        r.set(4000, 3000);
        r.set(3000, 3100);
        double from = r.value(3100);
        assertTrue(
            from < 5000 && from > 4000,
            "a new target starts from where the roll is now, not from the old target");
        assertEquals(3000, r.value(3100 + Anim.MEDIUM), 1e-9);
    }
}
