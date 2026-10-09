package com.fluxdepths.shard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;

import com.sinthoras.visualprospecting.database.veintypes.VeinType;
import com.sinthoras.visualprospecting.database.veintypes.VeinTypeCaching;

import bartworks.system.oregen.BWOreLayer;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OreMixes;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;
import gregtech.common.OreMixBuilder;
import gregtech.common.WorldgenGTOreLayer;

/**
 * Every ore vein the world generator knows, by the name VisualProspecting records for it ({@code ore.mix.copper}, a
 * Bartworks layer name, ...): GT's ore mixes and Bartworks' layers. Built once, the first time it is asked for.
 */
public final class Veins {

    public static final class Vein {

        public final String name;
        public final Mix<ItemStack> mix;
        /** Dimension names the vein generates in, as GT and Bartworks write them. */
        public final List<String> dims;
        /** Generated in this pack (GT can switch ore mixes off in its config). */
        public final boolean enabled;

        Vein(String name, Mix<ItemStack> mix, List<String> dims, boolean enabled) {
            this.name = name;
            this.mix = mix;
            this.dims = Collections.unmodifiableList(dims);
            this.enabled = enabled;
        }

        /** The vein's name as VisualProspecting shows it on the map. */
        public String displayName() {
            VeinType t = VeinTypeCaching.getVeinType(name);
            return t == VeinType.NO_VEIN ? name : t.getVeinName();
        }
    }

    private static Map<String, Vein> byName;

    private Veins() {}

    public static synchronized Map<String, Vein> all() {
        if (byName == null) byName = Collections.unmodifiableMap(load());
        return byName;
    }

    public static Vein get(String name) {
        return name == null ? null : all().get(name);
    }

    private static Map<String, Vein> load() {
        Map<String, Vein> m = new LinkedHashMap<>();
        Set<String> generated = new HashSet<>();
        for (WorldgenGTOreLayer l : WorldgenGTOreLayer.sList) if (l.mEnabled) generated.add(l.mWorldGenName);
        for (OreMixes mix : OreMixes.values()) {
            OreMixBuilder b = mix.oreMixBuilder;
            Mix<ItemStack> ores = Mix
                .ofVein(ore(b.primary), ore(b.secondary), ore(b.between), ore(b.sporadic), GTUtility::areStacksEqual);
            if (ores.isEmpty()) continue;
            List<String> dims = new ArrayList<>();
            for (Map.Entry<String, Boolean> e : b.dimsEnabled.entrySet()) if (Boolean.TRUE.equals(e.getValue())) {
                dims.add(e.getKey());
            }
            m.put(b.oreMixName, new Vein(b.oreMixName, ores, dims, generated.contains(b.oreMixName)));
        }
        for (BWOreLayer l : BWOreLayer.sList) {
            List<ItemStack> s = l.getStacks();
            if (s == null || s.size() < 4) continue;
            Mix<ItemStack> ores = Mix.ofVein(s.get(0), s.get(1), s.get(2), s.get(3), GTUtility::areStacksEqual);
            if (ores.isEmpty()) continue;
            List<String> dims = new ArrayList<>();
            dims.add(l.getDimName());
            m.putIfAbsent(l.mWorldGenName, new Vein(l.mWorldGenName, ores, dims, l.mEnabled));
        }
        return m;
    }

    private static ItemStack ore(Materials material) {
        if (material == null || material == Materials._NULL) return null;
        return GTOreDictUnificator.get(OrePrefixes.ore, material, 1);
    }
}
