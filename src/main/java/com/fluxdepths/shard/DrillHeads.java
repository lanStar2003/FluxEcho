package com.fluxdepths.shard;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.item.ItemStack;

import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.objects.ItemData;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;

/** Which GT item each {@link DrillHead} is. */
public final class DrillHeads {

    private static Map<DrillHead, ItemStack> items;

    private DrillHeads() {}

    private static synchronized Map<DrillHead, ItemStack> items() {
        if (items == null) {
            items = new EnumMap<>(DrillHead.class);
            for (DrillHead h : DrillHead.values()) {
                ItemStack s = GTOreDictUnificator.get(OrePrefixes.toolHeadDrill, Materials.get(h.material), 1);
                if (s != null) items.put(h, s);
            }
        }
        return items;
    }

    /** The GT drill head item, or null when GT does not make one of that material. */
    public static ItemStack item(DrillHead h) {
        ItemStack s = items().get(h);
        return s == null ? null : s.copy();
    }

    /**
     * Ores the stack lasts as a drill head in a shard collector, on average; 0 when it is no GT drill head. The six
     * listed heads have their fixed values, any other material {@link DrillHead#derivedUses half its durability}.
     */
    public static int uses(ItemStack stack) {
        if (stack == null) return 0;
        DrillHead h = of(stack);
        if (h != null) return h.ores;
        ItemData d = GTOreDictUnificator.getAssociation(stack);
        if (d == null || d.mPrefix != OrePrefixes.toolHeadDrill || d.mMaterial == null || d.mMaterial.mMaterial == null)
            return 0;
        return DrillHead.derivedUses(d.mMaterial.mMaterial.mDurability);
    }

    /** Which head the stack is, or null. */
    public static DrillHead of(ItemStack stack) {
        if (stack == null) return null;
        for (Map.Entry<DrillHead, ItemStack> e : items().entrySet())
            if (GTUtility.areStacksEqual(stack, e.getValue(), true)) return e.getKey();
        return null;
    }
}
