package com.fluxecho.thaumcraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.fluxecho.logic.AspectUnits;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.common.config.ConfigItems;

/**
 * What teaches an aspect, and what synthesizing one costs. Essentia that was really made once teaches its aspects:
 * a filled phial, a filled jar, crystallized essentia. A vis crystal shard teaches its primal, and any shard (the
 * balanced one too) pays for the synthesis.
 */
public final class AspectSamples {

    /** Primal of each shard's metadata 0-5; 6 is the balanced shard. */
    private static final Aspect[] SHARDS = { Aspect.AIR, Aspect.FIRE, Aspect.WATER, Aspect.EARTH, Aspect.ORDER,
        Aspect.ENTROPY };

    private static AspectUnits units;

    private AspectSamples() {}

    public static boolean isShard(ItemStack s) {
        return s != null && s.getItem() == ConfigItems.itemShard && s.getItemDamage() >= 0 && s.getItemDamage() <= 6;
    }

    /** A filled phial, jar or crystal. */
    public static boolean isEssentia(ItemStack s) {
        return s != null && s.getItem() instanceof IEssentiaContainerItem && !aspectsIn(s).isEmpty();
    }

    /** The aspects the stack teaches: those of the essentia in it, or a shard's primal. */
    public static List<Aspect> taught(ItemStack s) {
        if (s == null) return Collections.emptyList();
        if (isShard(s)) return s.getItemDamage() < SHARDS.length ? Collections.singletonList(SHARDS[s.getItemDamage()])
            : Collections.emptyList();
        return aspectsIn(s);
    }

    private static List<Aspect> aspectsIn(ItemStack s) {
        if (!(s.getItem() instanceof IEssentiaContainerItem c)) return Collections.emptyList();
        AspectList l = c.getAspects(s);
        if (l == null) return Collections.emptyList();
        List<Aspect> out = new ArrayList<>();
        for (Aspect a : l.getAspects()) if (a != null && l.getAmount(a) > 0) out.add(a);
        return out;
    }

    /** Primal units of one essentia of the aspect (see {@link AspectUnits}). */
    public static synchronized int units(Aspect a) {
        if (units == null) {
            // aspects are all registered by the time a machine first asks
            Map<String, String[]> components = new HashMap<>();
            for (Aspect x : Aspect.aspects.values()) {
                Aspect[] parts = x.isPrimal() ? null : x.getComponents();
                String[] tags = new String[parts == null ? 0 : parts.length];
                for (int i = 0; i < tags.length; i++) tags[i] = parts[i] == null ? "" : parts[i].getTag();
                components.put(x.getTag(), tags);
            }
            units = new AspectUnits(components);
        }
        return units.of(a.getTag());
    }
}
