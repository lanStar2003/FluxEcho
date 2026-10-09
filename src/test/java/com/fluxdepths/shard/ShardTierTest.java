package com.fluxdepths.shard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShardTierTest {

    @Test
    void noTierReachesTheVoidMiner() {
        for (ShardTier t : ShardTier.values())
            assertTrue(t.perSecond() < ShardTier.VOID_MINER_PER_SECOND, t + " " + t.perSecond());
    }

    @Test
    void everyTierIsFasterThanTheOneBefore() {
        ShardTier[] all = ShardTier.values();
        for (int i = 1; i < all.length; i++) {
            assertTrue(all[i].ticks < all[i - 1].ticks, all[i].name());
            assertTrue(all[i].imprints >= all[i - 1].imprints, all[i].name());
            assertTrue(all[i].imprints <= ShardTier.MAX_IMPRINTS, all[i].name());
        }
    }

    @Test
    void theAgreedRates() {
        assertEquals(360, Math.round(ShardTier.STEAM.perHour()));
        assertEquals(1440, Math.round(ShardTier.LV.perHour()));
        assertEquals(2880, Math.round(ShardTier.HV.perHour()));
        assertEquals(5538, Math.round(ShardTier.IV.perHour()));
        assertEquals(6545, Math.round(ShardTier.LUV.perHour()));
    }

    @Test
    void electricTiersFitTheirVoltage() {
        for (ShardTier t : ShardTier.values()) {
            if (t.steam()) continue;
            assertTrue(t.energy <= t.voltage(), t.name());
            assertTrue(t.energy > t.voltage() / 4, t.name() + " should need its own tier");
        }
    }

    @Test
    void circuitsPickTheirTier() {
        assertEquals(ShardTier.STEAM, ShardTier.ofVoltageTier(0));
        assertEquals(ShardTier.LV, ShardTier.ofVoltageTier(1));
        assertEquals(ShardTier.LUV, ShardTier.ofVoltageTier(6));
        assertEquals(ShardTier.STEAM, ShardTier.ofVoltageTier(7));
        assertEquals(0, ShardTier.STEAM.voltage());
        assertEquals(32768, ShardTier.LUV.voltage());
        assertEquals("LuV", ShardTier.LUV.label());
        assertEquals(16, ShardTier.STEAM.steamLitres());
    }

    @Test
    void onlyElectricTiersFromMvNeedFluid() {
        assertEquals(0, ShardTier.STEAM.fluidPerOre);
        assertEquals(0, ShardTier.LV.fluidPerOre);
        assertTrue(ShardTier.MV.fluidPerOre > 0);
        assertTrue(ShardTier.LUV.fluidPerOre > 0);
    }
}
