package com.fluxdepths.shard;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;

/**
 * The circuit in a collector's core slot decides its tier: GT's circuits by their ore dictionary names
 * ({@code OrePrefixes.circuit} of LV to LuV), whatever the pack's recipe for them.
 */
public final class CoreCircuits {

    /** Circuit material per GT voltage tier; index 0 (ULV) is not a core. */
    private static final Materials[] TIERS = { null, Materials.LV, Materials.MV, Materials.HV, Materials.EV,
        Materials.IV, Materials.LuV };

    private static int[] oreIds;

    private CoreCircuits() {}

    private static synchronized int[] oreIds() {
        if (oreIds == null) {
            int[] ids = new int[TIERS.length];
            ids[0] = -1;
            for (int i = 1; i < TIERS.length; i++) ids[i] = OreDictionary.getOreID(name(i));
            oreIds = ids;
        }
        return oreIds;
    }

    /** {@code circuitLV} and so on. */
    public static String name(int gtTier) {
        return String.valueOf(OrePrefixes.circuit.get(TIERS[gtTier]));
    }

    /** GT voltage tier of the circuit (1 LV to 6 LuV), 0 when the stack is none of them. */
    public static int tier(ItemStack s) {
        if (s == null || s.getItem() == null) return 0;
        int[] ids = oreIds();
        for (int id : OreDictionary.getOreIDs(s)) for (int t = 1; t < ids.length; t++) if (ids[t] == id) return t;
        return 0;
    }

    /** What the circuit makes of a collector: the steam mode for none. */
    public static ShardTier shardTier(ItemStack s) {
        return ShardTier.ofVoltageTier(tier(s));
    }

    /** One circuit of that tier, for NEI and tooltips; null when the pack has none. */
    public static ItemStack example(int gtTier) {
        if (gtTier < 1 || gtTier >= TIERS.length) return null;
        List<ItemStack> ores = OreDictionary.getOres(name(gtTier));
        if (ores.isEmpty()) return null;
        ItemStack s = ores.get(0)
            .copy();
        if (s.getItemDamage() == OreDictionary.WILDCARD_VALUE) s.setItemDamage(0);
        s.stackSize = 1;
        return s;
    }
}
