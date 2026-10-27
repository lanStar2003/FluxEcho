package com.fluxecho.logic.skin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.Parts;

/**
 * The lamp windows' one condition (style G6, §3.9.5; refactor §5.6): a window is lit when its building is awake (the
 * box table says formed, on hold or, after the leap, memorial), has power, and it is night by the client's own clock,
 * world time mod 24,000 in [12,500, 23,500). Dusk and dawn reach each building late by its distance d from the nexus
 * over 32 blocks a second, a wave of light from the middle out. The LEGACY cases are the old buildings of 0.16: the
 * same rule over their pilasters, cornices and fittings, every other old part as 0.16 drew it.
 */
class LampRuleTest {

    @Test
    void nightIsTwelveThousandFiveHundredToTwentyThreeThousandFiveHundred() {
        assertFalse(LampRule.night(0));
        assertFalse(LampRule.night(6000), "noon");
        assertFalse(LampRule.night(12_000), "/time set 12000: the sun is setting, no lamp yet");
        assertFalse(LampRule.night(12_499));
        assertTrue(LampRule.night(12_500), "dusk: the first lamps");
        assertTrue(LampRule.night(18_000), "midnight");
        assertTrue(LampRule.night(23_499));
        assertFalse(LampRule.night(23_500), "dawn: out again");
        assertFalse(LampRule.night(23_999));
        // the clock is the world's day time, which keeps counting past a day
        assertTrue(LampRule.night(24_000 * 5 + 12_500));
        assertFalse(LampRule.night(24_000 * 5 + 6000));
        assertTrue(LampRule.night(-24_000 + 18_000), "a negative time is read mod a day too");
    }

    @Test
    void theStatesThatAreAwake() {
        assertFalse(SkinState.BUILDING.awake(), "not built yet");
        assertTrue(SkinState.FORMED.awake());
        assertTrue(SkinState.HOLD.awake(), "a hold looks as a formed building does (§3.9.4)");
        assertFalse(SkinState.DORMANT.awake(), "dormant: the glow is off (§3.9.4)");
        assertFalse(SkinState.ASCENDING.awake(), "the leap's city goes dark (§3.9.4, S12 on)");
        assertTrue(SkinState.MEMORIAL.awake(), "the memorial state lights its windows by G6 as usual (§3.9.2)");
    }

    @Test
    void awakeAndPoweredAndNightTogether() {
        for (SkinState s : SkinState.values()) for (int p = 0; p < 2; p++) for (long t : new long[] { 6000, 18_000 }) {
            boolean want = s.awake() && p == 1 && t == 18_000;
            assertEquals(want, LampRule.lit(s, p == 1, t, 0), s + " powered " + (p == 1) + " at " + t);
        }
    }

    @Test
    void theWaveRunsThirtyTwoBlocksASecond() {
        // d / 32 seconds, in ticks: 20 d / 32
        assertEquals(0, LampRule.delayTicks(0));
        assertEquals(20, LampRule.delayTicks(32), "32 blocks out: a second late");
        assertEquals(40, LampRule.delayTicks(64));
        assertEquals(80, LampRule.delayTicks(128), "the city's edge (Chebyshev 127 from the nexus): four seconds");
        assertEquals(10, LampRule.delayTicks(16));
        assertEquals(0, LampRule.delayTicks(1), "less than a tick rounds down");
        assertEquals(0, LampRule.delayTicks(-5), "never early");

        // a building 64 blocks out lights two seconds after dusk and goes out two seconds after dawn
        assertFalse(LampRule.lit(SkinState.FORMED, true, 12_539, 64));
        assertTrue(LampRule.lit(SkinState.FORMED, true, 12_540, 64));
        assertTrue(LampRule.lit(SkinState.FORMED, true, 23_539, 64));
        assertFalse(LampRule.lit(SkinState.FORMED, true, 23_540, 64));
        // the nexus itself (d = 0) first
        assertTrue(LampRule.lit(SkinState.FORMED, true, 12_500, 0));
        // the next day the same, the clock read mod a day
        assertFalse(LampRule.lit(SkinState.FORMED, true, 24_000 + 12_539, 64));
        assertTrue(LampRule.lit(SkinState.FORMED, true, 24_000 + 12_540, 64));
        // the first ticks of a day are morning (world time 0 is sunrise): a late lamp is out by then
        assertFalse(LampRule.lit(SkinState.FORMED, true, 24_000 + 10, 64));
    }

    @Test
    void anEntryIsLitByItsDistanceFromItsNexus() {
        long nexus = SkinEntry.pack(100, 64, 100);
        SkinEntry e = entry(nexus, 100 + 48, 64, 100 + 64, SkinState.FORMED, true);
        // sqrt(48² + 64²) = 80 blocks out: 50 ticks late
        assertEquals(80, e.distance(), 1e-9);
        assertFalse(LampRule.lit(e, 12_549));
        assertTrue(LampRule.lit(e, 12_550));
        assertFalse(LampRule.lit(entry(nexus, 148, 64, 164, SkinState.FORMED, false), 18_000), "no power");
        assertFalse(LampRule.lit(entry(nexus, 148, 64, 164, SkinState.DORMANT, true), 18_000), "dormant");
        assertTrue(LampRule.lit(entry(nexus, 148, 64, 164, SkinState.HOLD, true), 18_000), "held");
    }

    @Test
    void isAwakeVersionZeroIsFormedDockedAndPowered() {
        for (int f = 0; f < 2; f++) for (int d = 0; d < 2; d++) for (int p = 0; p < 2; p++)
            assertEquals(f + d + p == 3, LampRule.isAwake(f == 1, d == 1, p == 1), f + " " + d + " " + p);
    }

    // ---- LEGACY: the old buildings

    /** The parts the old buildings' lamp windows are made of, as refactor §5.6 lists them and the code draws them. */
    @Test
    void theLegacyLampPartsAreThePilastersTheCornicesAndTheGlowingFittings() {
        assertTrue(SkinProfiles.isLamp("eco_dome", Parts.deck(Parts.D_PANEL_LIT)), "deck 9, the pilaster");
        assertTrue(SkinProfiles.isLamp("eco_dome", Parts.deck(Parts.D_CORNICE)), "deck 11, the cornice");
        // every fitting 0.16's FittingRender draws a glow overlay on (its light above 0): all but the glazing
        for (int m = 0; m < Parts.FITTING_TYPES; m++)
            assertEquals(m != Parts.F_GLAZE, SkinProfiles.isLamp("eco_dome", Parts.fitting(m)), "fitting " + m);
        // the rest of the old parts glow as 0.16 drew them, day and night
        for (int m : new int[] { Parts.D_DECK, Parts.D_LIT, Parts.D_SKIRT, Parts.D_CHEVRON, Parts.D_WELL,
            Parts.D_PANEL_DARK, Parts.D_CANVAS })
            assertFalse(SkinProfiles.isLamp("eco_dome", Parts.deck(m)), "deck " + m);
        assertFalse(SkinProfiles.isLamp("eco_dome", Parts.frame(Parts.FR_FLOAT)), "the eco dome has no float");
        assertTrue(SkinProfiles.tinted(Parts.deck(Parts.D_PANEL_LIT)));
        assertTrue(SkinProfiles.tinted(Parts.fitting(Parts.F_POST)));
        assertFalse(SkinProfiles.tinted(Parts.deck(Parts.D_LIT)));
    }

    /**
     * The parts a shape had to take into its mask besides (refactor §5.6, "写不出来怎么办"): the old shapes whose
     * always-lit glow was over 3% of their outer surface by day ({@link LegacyGlowShareTest}) switch those parts with
     * the lamp rule too. The frame parts are drawn by the frame's renderer, which reads the table as the deck's does.
     */
    @Test
    void theLegacyMasksTakeTheFrameGlowOfTheShapesThatOutshoneByDay() {
        assertTrue(SkinProfiles.isLamp("nexus", Parts.frame(Parts.FR_RING)));
        assertTrue(SkinProfiles.isLamp("nexus", Parts.frame(Parts.FR_BASE_LIT)));
        assertTrue(SkinProfiles.isLamp("nexus", Parts.fitting(Parts.F_LIFT)));
        assertFalse(SkinProfiles.isLamp("nexus", Parts.frame(Parts.FR_CONSOLE)));
        assertTrue(SkinProfiles.isLamp("library", Parts.frame(Parts.FR_FOUNDATION)));
        assertFalse(SkinProfiles.isLamp("library", Parts.frame(Parts.FR_SHELF)));
        for (String sky : new String[] { "droplet_hangar", "sky_sails", "weather_station", "observatory" })
            assertTrue(SkinProfiles.isLamp(sky, Parts.frame(Parts.FR_FLOAT)), sky);
        assertFalse(SkinProfiles.isLamp("dome_generator", Parts.frame(Parts.FR_FLOAT)));
        assertFalse(SkinProfiles.tinted(Parts.frame(Parts.FR_FLOAT)), "a taken-in part keeps its own colour");
    }

    /** A pilaster of an old building through the day: states × power × time × the wave. */
    @Test
    void legacyLampsThroughTheDay() {
        long nexus = SkinEntry.pack(0, 64, 0);
        int pilaster = Parts.deck(Parts.D_PANEL_LIT), strip = Parts.deck(Parts.D_LIT);
        for (SkinState s : SkinState.values()) for (int p = 0; p < 2; p++) for (long t = 0; t < 24_000; t += 250) {
            SkinEntry e = entry(nexus, 0, 64, 96, s, p == 1);
            assertEquals(SkinProfiles.LEGACY, SkinProfiles.profile(e.key(), e.shape));
            boolean lit = s.awake() && p == 1 && LampRule.night(t - LampRule.delayTicks(96));
            assertEquals(lit ? e.accent : LampRule.DARK, LampRule.glow(e, pilaster, t), s + " " + p + " " + t);
            // the lit strip is no lamp: it keeps 0.16's light, untinted, whatever the table says
            assertEquals(LampRule.UNTOUCHED, LampRule.glow(e, strip, t));
        }
        // 96 blocks out: three seconds after dusk
        SkinEntry e = entry(nexus, 0, 64, 96, SkinState.FORMED, true);
        assertEquals(LampRule.DARK, LampRule.glow(e, pilaster, 12_559));
        assertEquals(e.accent, LampRule.glow(e, pilaster, 12_560));
        // a taken-in part switches but keeps its colour (white: the texture's own)
        SkinEntry hangar = SkinEntry.builder()
            .campus(nexus)
            .key("droplet_hangar")
            .shape("main")
            .ctrl(0, 104, 96)
            .box(-10, 90, 86, 10, 110, 106)
            .state(SkinState.FORMED)
            .powered(true)
            .accent(0xD8E4F0)
            .build();
        assertEquals(LampRule.DARK, LampRule.glow(hangar, Parts.frame(Parts.FR_FLOAT), 6000));
        assertEquals(0xFFFFFF, LampRule.glow(hangar, Parts.frame(Parts.FR_FLOAT), 18_000));
        assertEquals(0xD8E4F0, LampRule.glow(hangar, Parts.fitting(Parts.F_POST), 18_000));
    }

    static SkinEntry entry(long nexus, int x, int y, int z, SkinState s, boolean powered) {
        return SkinEntry.builder()
            .campus(nexus)
            .key("eco_dome")
            .shape("main")
            .facing(2)
            .ctrl(x, y, z)
            .box(x - 10, y - 2, z - 10, x + 10, y + 20, z + 10)
            .state(s)
            .powered(powered)
            .accent(SkinPalette.accent("eco_dome"))
            .build();
    }
}
