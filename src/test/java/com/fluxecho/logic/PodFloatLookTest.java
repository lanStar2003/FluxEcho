package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The floating pod's drawing (0.16.0, CITY.md §3 release): once its legs are taken down the pod draws a lifting light
 * under its floor instead, its rim on the floor's octagon and motes sinking from it, in fewer quads than the legs it
 * replaces, and nothing far (the pod's landmark carries it).
 */
class PodFloatLookTest {

    @Test
    void theLiftingLightCostsLessThanTheLegs() {
        for (int tier = ModuleLook.NEAR; tier <= ModuleLook.FAR; tier++) assertTrue(
            DomeLook.floatQuads(tier) <= DomeLook.legQuads(tier),
            "tier " + tier + ": " + DomeLook.floatQuads(tier) + " > " + DomeLook.legQuads(tier));
        assertEquals(0, DomeLook.floatQuads(ModuleLook.FAR));
        assertTrue(DomeLook.floatQuads(ModuleLook.NEAR) > DomeLook.floatQuads(ModuleLook.MID), "near: its motes");
        assertTrue(DomeLook.FLOAT_GLOW_R <= DomeGeneratorShape.POD_R, "the glow under the floor");
    }

    /** The rim is the floor's octagon, {@code oct(10, 14)} round the pod's middle, its corners on the cells' edges. */
    @Test
    void theRimIsTheFloorsOctagon() {
        double[][] rim = DomeLook.rim();
        assertEquals(8, rim.length);
        double h = DomeGeneratorShape.POD_R + 0.5;
        for (double[] p : rim) {
            double a = p[0] - DomeLook.CX, r = p[1] - DomeLook.CZ;
            assertEquals(h, Math.max(Math.abs(a), Math.abs(r)), 1e-12, "on the octagon's flat sides' line");
            assertTrue(Math.abs(a) + Math.abs(r) <= DomeGeneratorShape.POD_K + 1 + 1e-12, "within its chamfer");
        }
    }

    @Test
    void theMotesSinkFromTheFloorAgainAndAgain() {
        for (int k = 0; k < DomeLook.FLOAT_MOTES; k++) for (double t : new double[] { 0, 33.3, 4567.5 }) {
            double[] m = DomeLook.floatMote(k, t);
            assertTrue(m[0] >= 0 && m[0] <= DomeLook.FLOAT_FALL);
            assertTrue(m[1] >= -1e-12 && m[1] <= 1 + 1e-12);
            assertArrayEquals(m, DomeLook.floatMote(k, t + DomeLook.FLOAT_FALL_T), 1e-6);
        }
    }
}
