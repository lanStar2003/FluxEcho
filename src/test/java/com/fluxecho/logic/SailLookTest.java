package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The Sky Sail Array's drawing (0.16.0, CITY.md §4.12 Visuals): three sails hang between the mast pairs from a yard on
 * the masts' tops as the world has them (lower where the top of the world clips the masts, deck 253 included), turned
 * to face the sun by day and the moon by night, bellied away from the light and fluttering; their sides stay on the
 * masts, the sails in the air over the deck and the hut; and the counts are the drawing's.
 */
class SailLookTest {

    private static final CityBuild B = SkySailsShape.BUILD;

    @Test
    void eachSailHangsBetweenItsMastPair() {
        assertEquals(3, SailLook.SAILS);
        for (int s = 0; s < SailLook.SAILS; s++) {
            double[] sp = SailLook.span(s);
            int[] a = SkySailsShape.MASTS[2 * s], b = SkySailsShape.MASTS[2 * s + 1];
            assertEquals(a[1], b[1], "a pair stands at one z");
            assertEquals(SkyRing.MID + a[1] + 0.5, sp[2], 0);
            // each end just inside its mast's column
            double left = SkyRing.MID + Math.min(a[0], b[0]), right = SkyRing.MID + Math.max(a[0], b[0]);
            assertTrue(sp[0] > left && sp[0] < left + 1, "the left end on its mast");
            assertTrue(sp[1] > right && sp[1] < right + 1, "the right end on its mast");
        }
    }

    /**
     * The yard rests on the masts' tops in the world: the crown's top over a low deck; over a deck so high the world's
     * top clips the masts, the top of the highest mast cell left, but never so low the sail has no room.
     */
    @Test
    void theYardRestsOnTheMastsTopsInTheWorld() {
        assertEquals(SkySailsShape.CROWN_Y + 1, SailLook.yard(40), 0);
        assertEquals(SkySailsShape.CROWN_Y + 1, SailLook.yard(28), 0);
        assertEquals(11, SailLook.yard(245), 0, "the masts clipped at y 255");
        assertEquals(SailLook.FOOT_MIN + SailLook.MIN_H, SailLook.yard(253), 1e-12, "the highest deck");
        for (int deck = 14; deck <= 253; deck++) {
            double yard = SailLook.yard(deck), h = SailLook.height(yard);
            assertTrue(yard <= Math.max(256 - deck, SailLook.FOOT_MIN + SailLook.MIN_H) + 1e-12, "deck " + deck);
            assertTrue(yard <= SkySailsShape.CROWN_Y + 1);
            assertTrue(h >= SailLook.MIN_H - 1e-12 && h <= SailLook.DROP, "deck " + deck + ": " + h);
            assertTrue(yard - h >= SailLook.FOOT_MIN - 1e-12, "the foot over the hut's roof, deck " + deck);
        }
    }

    @Test
    void theLightIsTheSunByDayAndTheMoonByNight() {
        double[] noon = SailLook.light(0);
        assertArrayEquals(new double[] { 0, 1, 0, 1 }, noon, 1e-12);
        // the sun rises in the east (+x), weak at the horizon
        double[] dawn = SailLook.light(0.76);
        assertTrue(dawn[0] > 0.99 && dawn[1] > 0, "low in the east");
        assertTrue(dawn[3] > 0.25 && dawn[3] < 0.5, "weak: " + dawn[3]);
        // midnight: the moon overhead, dimmer
        double[] midnight = SailLook.light(0.5);
        assertEquals(1, midnight[1], 1e-9);
        assertEquals(SailLook.MOONLIGHT, midnight[3], 1e-9);
        for (double c = 0; c < 1; c += 0.01) {
            double[] l = SailLook.light(c);
            assertTrue(l[1] >= -1e-12, "always a light over the horizon");
            assertEquals(1, Math.sqrt(l[0] * l[0] + l[1] * l[1] + l[2] * l[2]), 1e-9);
        }
    }

    /**
     * A sail turns to face the light: once the light's share square to the yard is large enough, its face's normal
     * looks along that share (the sail square to the light), within the tilt's bound; the side lit is the one turned to
     * it; with the light along the yard it hangs straight.
     */
    @Test
    void aSailTurnsToFaceTheLight() {
        assertEquals(0, SailLook.tilt(0, 0), 0);
        for (double a = 0; a < 2 * Math.PI; a += 0.05) for (double p : new double[] { 0.2, 0.5, 1 }) {
            double ly = Math.sin(a) * p, lz = Math.cos(a) * p;
            double tilt = SailLook.tilt(ly, lz);
            assertTrue(Math.abs(tilt) <= SailLook.TILT_MAX + 1e-12);
            int side = SailLook.lit(tilt, ly, lz);
            assertTrue(side * (Math.sin(tilt) * ly + Math.cos(tilt) * lz) >= -1e-12, "the lit side faces it");
            double raw = Math.atan2(lz >= 0 ? ly : -ly, Math.abs(lz));
            if (p >= SailLook.EASE && Math.abs(raw) <= SailLook.TILT_MAX)
                assertEquals(p, Math.abs(Math.sin(tilt) * ly + Math.cos(tilt) * lz), 1e-9, "square to the light");
        }
        // a sail glows fully face-on, dimmer glancing
        assertTrue(SailLook.glow(0, 0, 0, 1, 1) > SailLook.glow(0, 1, 0, 0, 1));
        assertEquals(1, SailLook.glow(0, 0, 0, 1, 1), 1e-12);
    }

    /**
     * The sails hang close together (the pairs 5 and 3 blocks apart, the hut in front): each turns only within the room
     * it has before its cloth would meet a neighbour's masts or the hut, the back one all the way, every one a little
     * each way at least.
     */
    @Test
    void eachSailTurnsWithinItsRoom() {
        for (int s = 0; s < SailLook.SAILS; s++) {
            double[] room = SailLook.room(s);
            assertTrue(room[0] <= -0.15 && room[1] >= 0.15, "sail " + s + ": " + room[0] + " .. " + room[1]);
            for (double a = 0; a < 2 * Math.PI; a += 0.1) {
                double tilt = SailLook.tilt(s, Math.sin(a), Math.cos(a));
                assertTrue(tilt >= room[0] && tilt <= room[1]);
                double free = SailLook.tilt(Math.sin(a), Math.cos(a));
                if (free >= room[0] && free <= room[1]) assertEquals(free, tilt, 0, "no clamp inside the room");
            }
        }
        assertArrayEquals(new double[] { -SailLook.TILT_MAX, SailLook.TILT_MAX }, SailLook.room(2), 1e-9);
        // the front sail and the middle one have the hut and each other's masts close by
        assertTrue(SailLook.room(0)[0] > -0.5 && SailLook.room(1)[1] < 0.5);
    }

    /**
     * The sails hang in the air: the yard and the sides on their lines (the top edge on the yard, the sides from the
     * masts), and inside the sail, at every tilt within its room and either side and over a flutter's time, every
     * point clear of the array's blocks (the masts, the hut, the deck and rail) and across the platform.
     */
    @Test
    void theSailsHangInTheAir() {
        double yard = SailLook.yard(40), drop = SailLook.height(yard);
        for (int s = 0; s < SailLook.SAILS; s++) {
            double[] sp = SailLook.span(s), room = SailLook.room(s);
            for (double tilt = room[0]; tilt <= room[1] + 1e-9; tilt += (room[1] - room[0]) / 8)
                for (int side : new int[] { -1, 1 }) for (double t = 0; t < SailLook.FLUTTER_T; t += 6.5) {
                    // the yard's line and the sides' lines
                    for (double u = 0; u <= 1; u += 0.125) {
                        double[] top = SailLook.point(s, u, 0, yard, drop, tilt, side, t);
                        assertEquals(yard, top[1], 1e-12);
                        assertEquals(sp[2], top[2], 1e-12);
                    }
                    for (double v = 0; v <= 1; v += 0.125) for (double u : new double[] { 0, 1 }) {
                        double[] e = SailLook.point(s, u, v, yard, drop, tilt, side, t);
                        assertEquals(u == 0 ? sp[0] : sp[1], e[0], 1e-12);
                        assertEquals(yard - v * drop * Math.cos(tilt), e[1], 1e-9);
                    }
                    for (double u = 0.125; u < 1; u += 0.125) for (double v = 0.1; v <= 1; v += 0.15) {
                        double[] p = SailLook.point(s, u, v, yard, drop, tilt, side, t);
                        String at = "sail " + s + " tilt " + tilt + " at " + u + ", " + v;
                        assertTrue(p[0] > 0 && p[0] < SkyRing.SIZE && p[1] > 2, at);
                        assertTrue(LookCells.clearance(B, p[0], p[1], p[2], LookCells.OPEN, 0.05) >= 0.05, at);
                    }
                }
        }
    }

    @Test
    void theCountsAreTheDrawing() {
        // near: 8 x 6 cells, the four edges cut where the grid is with a halo each, three rays of two pieces
        assertEquals(48 + 2 * 28 + 6, SailLook.sailQuads(0, ModuleLook.NEAR));
        assertEquals(12 + 14, SailLook.sailQuads(0, ModuleLook.MID));
        int all = 0;
        for (int s = 0; s < SailLook.SAILS; s++) all += SailLook.sailQuads(s, ModuleLook.NEAR);
        assertEquals(all + SkySailsShape.MASTS.length, SailLook.quads(ModuleLook.NEAR));
        assertEquals(SailLook.quads(ModuleLook.FAR), SailLook.landmarkQuads());
        assertTrue(SailLook.landmarkQuads() < 64);
    }
}
