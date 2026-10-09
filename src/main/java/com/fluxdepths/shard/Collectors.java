package com.fluxdepths.shard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.fluxdepths.Config;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/**
 * The Flux Shard Collector on {@link Config#shardsFirstId}, and the six ids after it that the old tiered collectors
 * (high pressure steam to IV) had. Those are the same machine, kept so placed ones survive: hidden from NEI, and
 * their items craft into the collector.
 */
public final class Collectors {

    /** Old machine names at the ids after the first, and the GT tier of the circuit a placed one gets. */
    private static final String[] LEGACY = { "hp_steam", "lv", "mv", "hv", "ev", "iv" };
    private static final int[] LEGACY_TIER = { 0, 1, 2, 3, 4, 5 };

    private static ItemStack main;
    private static final List<ItemStack> LEGACY_STACKS = new ArrayList<>();

    private Collectors() {}

    public static void register() {
        ShardTextures.load();
        main = put(Config.shardsFirstId, "fluxdepths.shard.collector", "Flux Shard Collector", 0);
        for (int i = 0; i < LEGACY.length; i++) LEGACY_STACKS.add(
            put(
                Config.shardsFirstId + 1 + i,
                "fluxdepths.shard." + LEGACY[i],
                "Old Shard Collector (" + LEGACY[i].toUpperCase() + ")",
                LEGACY_TIER[i]));
    }

    private static ItemStack put(int id, String name, String english, int legacyTier) {
        IMetaTileEntity taken = GregTechAPI.METATILEENTITIES[id];
        if (taken != null) throw new IllegalStateException(
            "FluxDepths: GT machine id " + id
                + " is already used by "
                + taken.getClass()
                    .getName()
                + ". Set shard_collectors.firstMachineId in config/fluxdepths.cfg to the start of 7 free ids.");
        return new MTEFluxCollector(id, name, english, legacyTier).getStackForm(1);
    }

    /** The Flux Shard Collector. */
    public static ItemStack main() {
        return main == null ? null : main.copy();
    }

    /** The old collectors' items, high pressure steam to IV. */
    public static List<ItemStack> legacy() {
        List<ItemStack> l = new ArrayList<>();
        for (ItemStack s : LEGACY_STACKS) l.add(s.copy());
        return Collections.unmodifiableList(l);
    }
}
