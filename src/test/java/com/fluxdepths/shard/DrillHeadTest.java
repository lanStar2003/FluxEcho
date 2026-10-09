package com.fluxdepths.shard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class DrillHeadTest {

    @Test
    void wearsOnlyOnTheLowRolls() {
        assertTrue(DrillHead.wears(128, 0));
        assertTrue(DrillHead.wears(128, 1.0 / 128 - 1e-9));
        assertFalse(DrillHead.wears(128, 1.0 / 128));
        assertFalse(DrillHead.wears(128, 0.5));
        assertFalse(DrillHead.wears(128, 0.999));
    }

    @Test
    void aHeadOfOneUseOrLessAlwaysWears() {
        assertTrue(DrillHead.wears(1, 0.999));
        assertTrue(DrillHead.wears(0, 0.5));
    }

    @Test
    void lastsItsUsesOnAverage() {
        Random r = new Random(42);
        int uses = 256, worn = 0, n = 2_000_000;
        for (int i = 0; i < n; i++) if (DrillHead.wears(uses, r.nextDouble())) worn++;
        double average = (double) n / worn;
        assertEquals(uses, average, uses * 0.05);
    }

    @Test
    void percentForTooltips() {
        assertEquals("0.78", DrillHead.percent(DrillHead.BRONZE.ores));
        assertEquals("0.39", DrillHead.percent(DrillHead.STEEL.ores));
        assertEquals("0.098", DrillHead.percent(DrillHead.TUNGSTEN_STEEL.ores));
    }

    @Test
    void otherMaterialsLastHalfTheirDurability() {
        assertEquals(256, DrillHead.derivedUses(512));
        assertEquals(DrillHead.MIN_USES, DrillHead.derivedUses(10));
        assertEquals(DrillHead.MAX_USES, DrillHead.derivedUses(Long.MAX_VALUE));
    }

    @Test
    void betterHeadsLastLonger() {
        DrillHead[] all = DrillHead.values();
        for (int i = 1; i < all.length; i++) assertTrue(all[i].ores > all[i - 1].ores);
    }
}
