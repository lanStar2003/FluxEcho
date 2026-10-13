package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The campus band (0.14.1, CITY.md §1.3 Vertical): a campus's base layer from Y0 14 to Y0 225, the old top (the
 * nexus's headroom to phase V) and a new bottom (the shallowest well over the bedrock); inside it every building has
 * room in its full or adapted form; a core put back where it stood is never refused.
 */
class CampusBandTest {

    @Test
    void theBandIsFourteenToTwoTwentyFive() {
        assertEquals(14, CampusBand.LOWEST);
        assertEquals(225, CampusBand.HIGHEST);
        assertEquals(30, CampusBand.HEADROOM);
        assertEquals(RootShape.MIN_Y0, CampusBand.LOWEST, "the shallowest well's");
        assertEquals("build.too_low", CampusBand.refusal(13, false));
        assertEquals("build.too_low", CampusBand.refusal(0, false));
        assertNull(CampusBand.refusal(14, false));
        assertNull(CampusBand.refusal(79, false));
        assertNull(CampusBand.refusal(225, false));
        assertEquals("build.too_high", CampusBand.refusal(226, false));
        assertEquals("build.too_high", CampusBand.refusal(253, false));
        // inside the band every building has room: the zenith's controller and wards, the well's slab over the bedrock
        for (int y0 = CampusBand.LOWEST; y0 <= CampusBand.HIGHEST; y0++) {
            assertNull(CampusBand.refusal(y0, false));
            assertNull(DomeGeneratorShape.noRoom(y0), "the zenith at " + y0);
            assertNull(RootShape.noRoom(y0), "the root at " + y0);
            assertTrue(y0 - RootShape.depth(y0) >= RootShape.LOWEST_SLAB);
            assertTrue(y0 + DomeGeneratorShape.datum(y0) + 2 <= 255);
            // the nexus grown to phase V and its growth's clear box stay in the world
            assertTrue(y0 + NexusShape.STAGE * NexusShape.PHASES + 2 <= 255);
        }
    }

    /**
     * A core put back where it stood (the dimension and the block its campus was broken off) is never refused for its
     * height: a 0.10 to 0.14 campus may have been laid out under Y0 14, and it must get its core back.
     */
    @Test
    void aCorePutBackWhereItStoodIsNeverRefused() {
        int[] at = { 120, 12, -40 };
        assertTrue(CampusBand.putBack(0, at, 0, 120, 12, -40));
        assertNull(CampusBand.refusal(10, CampusBand.putBack(0, at, 0, 120, 12, -40)));
        assertNull(CampusBand.refusal(240, true));
        assertFalse(CampusBand.putBack(0, at, -1, 120, 12, -40), "another dimension");
        assertFalse(CampusBand.putBack(0, at, 0, 121, 12, -40), "another block");
        assertFalse(CampusBand.putBack(0, at, 0, 120, 13, -40));
        assertFalse(CampusBand.putBack(0, at, 0, 120, 12, -41));
        assertFalse(CampusBand.putBack(0, null, 0, 120, 12, -40), "no campus carried");
        assertFalse(CampusBand.putBack(0, new int[] { 120, 12 }, 0, 120, 12, -40));
        assertEquals("build.too_low", CampusBand.refusal(10, CampusBand.putBack(0, at, 0, 121, 12, -40)));
    }
}
