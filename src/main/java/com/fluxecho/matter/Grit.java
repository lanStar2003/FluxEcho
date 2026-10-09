package com.fluxecho.matter;

import java.util.Random;
import java.util.UUID;

import net.minecraft.item.ItemStack;

import com.fluxecho.Config;
import com.fluxecho.core.Owners;
import com.fluxecho.research.Research;

/**
 * Flux grit falls out of the deep layer's shards while the Flux Shard Collector condenses an ore: a chance per ore,
 * half as much again once the collector's team has researched {@code grit_yield}.
 */
public final class Grit {

    private Grit() {}

    /** One flux grit to go with the ore just condensed, or null. */
    public static ItemStack roll(Random rand, UUID owner) {
        if (MatterModule.grit == null) return null;
        double chance = Config.gritChance;
        if (chance <= 0) return null;
        if (Research.teamHas(Owners.team(owner), Research.GRIT_YIELD)) chance *= 1.5;
        return rand.nextDouble() < chance ? MatterModule.grit(1) : null;
    }
}
