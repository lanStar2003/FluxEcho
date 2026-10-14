package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The sky ring's shared drawing (0.16.0, CITY.md §4.12 Visuals): each platform's light pillar reaches down to the
 * ground under it (or a fixed depth over nothing) and fades out as a landmark by 512 blocks; the catwalk's lit edges
 * run row by row from the pod out to the platform, exactly over the deck the job lays; and every sky module keeps
 * CITY.md §1.3's budgets with the shared drawing on top of its own, counted as drawn.
 */
class SkyLookTest {

    private static final int[] BUDGET = { 2000, 300, 64 };

    @Test
    void thePillarReachesTheGroundAndFadesAsALandmark() {
        // the top of the highest block found under the deck; nothing found within the scan: a fixed depth
        assertEquals(-9, SkyLook.foot(-10), 0);
        assertEquals(0, SkyLook.foot(-1), 0);
        assertEquals(-SkyLook.NO_GROUND, SkyLook.foot(0), 0);
        assertEquals(-SkyLook.NO_GROUND, SkyLook.foot(-SkyLook.SCAN - 1), 0);
        assertEquals(-SkyLook.SCAN + 1, SkyLook.foot(-SkyLook.SCAN), 0);
        // a landmark out to 512 blocks, fading over its last ones
        assertTrue(SkyLook.LANDMARK <= 512);
        assertEquals(1f, SkyLook.landmarkFade(0), 0);
        assertEquals(1f, SkyLook.landmarkFade(SkyLook.LANDMARK - SkyLook.LANDMARK_FADE), 1e-6);
        assertEquals(0.5f, SkyLook.landmarkFade(SkyLook.LANDMARK - SkyLook.LANDMARK_FADE / 2), 1e-6);
        assertEquals(0f, SkyLook.landmarkFade(SkyLook.LANDMARK), 0);
        assertEquals(0f, SkyLook.landmarkFade(SkyLook.LANDMARK + 100), 0);
        // brightest at the ground, faint under the deck
        assertTrue(SkyLook.FOOT_ALPHA > SkyLook.HEAD_ALPHA);
    }

    @Test
    void theMotesSinkFromTheDeckToTheFootAgainAndAgain() {
        double foot = -30;
        for (int k = 0; k < SkyLook.FALL_MOTES; k++) for (double t : new double[] { 0, 17.5, 999.25, 123456.75 }) {
            double[] m = SkyLook.fallMote(k, t, foot);
            assertTrue(m[0] <= SkyLook.PILLAR_TOP + 1e-9 && m[0] >= foot - 1e-9, "between the deck and the foot");
            assertTrue(m[1] >= -1e-9 && m[1] <= 1 + 1e-9);
            // by the world time alone: the same a fall later
            assertArrayEquals(m, SkyLook.fallMote(k, t + SkyLook.FALL_T, foot), 1e-6);
        }
        // a mote goes down as time passes
        assertTrue(SkyLook.fallMote(0, 10, foot)[0] < SkyLook.fallMote(0, 5, foot)[0]);
    }

    /**
     * The catwalk's rows in the module's frame are exactly the catwalk the job lays (SkyRing#catwalk), turned into the
     * frame of the module standing on site S0 facing the pod: radial 11 to 21 is z -11 to -1, the deck's three cells x
     * 9
     * to 11.
     */
    @Test
    void theCatwalkRowsLieOverTheLaidDeck() {
        List<int[]> rows = SkyLook.walkRows();
        assertEquals(SkyRing.CATWALK_TO - SkyRing.CATWALK_FROM + 1, rows.size());
        int site = SkyRing.SITE0;
        int deck = 0;
        for (int[] c : SkyRing.catwalk(site)) {
            if (c[3] != '.') continue;
            deck++;
            // S0's axis is +A: radial a, across r
            int z = c[0] - SkyRing.FRONT, x = SkyRing.MID + c[1];
            boolean found = false;
            for (int[] row : rows) found |= row[0] == z && x >= row[1] && x <= row[2];
            assertTrue(found, "deck cell " + c[0] + ", " + c[1]);
        }
        assertEquals(3 * rows.size(), deck);
        assertEquals(-11, rows.get(0)[0]);
        assertEquals(-1, rows.get(rows.size() - 1)[0]);
        assertEquals(SkyLook.WALK_X1 - SkyLook.WALK_X0, 3, 0);
    }

    @Test
    void theWalkPulseRunsOutFromThePod() {
        for (int z = SkyLook.WALK_Z0; z <= SkyLook.WALK_Z1; z++) for (double t : new double[] { 0, 33, 71.5, 9999 }) {
            double k = SkyLook.walkPulse(z, t);
            assertTrue(k >= 0 && k <= 1);
            assertEquals(k, SkyLook.walkPulse(z, t + SkyLook.WALK_T + SkyLook.WALK_REST), 1e-9);
        }
        // the brightest row moves out from the pod as the pulse runs
        int last = Integer.MIN_VALUE;
        for (int step = 1; step <= 4; step++) {
            double t = SkyLook.WALK_T * step / 5.0;
            int best = SkyLook.WALK_Z0;
            for (int z = SkyLook.WALK_Z0; z <= SkyLook.WALK_Z1; z++)
                if (SkyLook.walkPulse(z, t) > SkyLook.walkPulse(best, t)) best = z;
            assertTrue(best > last, "at " + t + ": row " + best);
            last = best;
        }
    }

    @Test
    void theSharedDrawingIsCounted() {
        assertEquals(2 * SkyLook.BEAM_QUADS + 2 * SkyLook.GLOW_QUADS + SkyLook.FALL_MOTES, SkyLook.pillarQuads(0));
        assertEquals(SkyLook.pillarQuads(0), SkyLook.pillarQuads(1));
        assertEquals(2 * SkyLook.BEAM_QUADS + SkyLook.GLOW_QUADS, SkyLook.pillarQuads(2));
        assertEquals(11 * 6, SkyLook.walkQuads(ModuleLook.NEAR));
        assertEquals(11 * 4, SkyLook.walkQuads(ModuleLook.MID));
        assertEquals(0, SkyLook.walkQuads(ModuleLook.FAR));
    }

    /**
     * Every sky module with the shared drawing (its plate, its plinth line, its sockets), the sky ring's pillar and
     * catwalk, and its own visuals stays within 2,000 near, 300 mid and 64 far; its landmark (the pillar far and its
     * own) within the landmarks' 400.
     */
    @Test
    void everySkyModuleKeepsItsBudgets() {
        CityBuild[] builds = { WeatherStationShape.BUILD, SkySailsShape.BUILD, ObservatoryShape.BUILD,
            DropletHangarShape.BUILD };
        UnitLayout[] units = { WeatherStationShape.UNITS, SkySailsShape.UNITS, ObservatoryShape.UNITS,
            DropletHangarShape.UNITS };
        for (int i = 0; i < builds.length; i++) {
            for (int tier = ModuleLook.NEAR; tier <= ModuleLook.FAR; tier++) {
                int base = ModuleLook.baseQuads(builds[i], units[i], tier), sky = SkyLook.quads(tier),
                    own = own(i, tier);
                assertTrue(
                    base + sky + own <= BUDGET[tier],
                    builds[i].key() + " tier " + tier + ": " + base + " + " + sky + " + " + own);
            }
            int landmark = SkyLook.pillarQuads(ModuleLook.FAR) + landmark(i);
            assertTrue(landmark <= SkyLook.BUDGET, builds[i].key() + " landmark " + landmark);
        }
    }

    private static int own(int i, int tier) {
        switch (i) {
            case 0:
                return WeatherLook.quads(tier);
            case 1:
                return SailLook.quads(tier);
            case 2:
                return ObservatoryLook.quads(tier);
            default:
                return DropletLook.quads(tier);
        }
    }

    private static int landmark(int i) {
        switch (i) {
            case 0:
                return WeatherLook.landmarkQuads();
            case 1:
                return SailLook.landmarkQuads();
            case 2:
                return ObservatoryLook.landmarkQuads();
            default:
                return DropletLook.landmarkQuads();
        }
    }
}
