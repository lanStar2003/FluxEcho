package com.fluxecho.codex;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** The client's copy of its team's ledger, as the server last sent it. Empty until then. */
public final class ClientLedger {

    private static volatile Map<String, Set<String>> done = Collections.emptyMap();

    private ClientLedger() {}

    static void set(NBTTagCompound tag) {
        Map<String, Set<String>> m = new HashMap<>();
        for (Object o : tag.func_150296_c()) {
            String cat = (String) o;
            NBTTagList l = tag.getTagList(cat, 8);
            Set<String> s = new LinkedHashSet<>();
            for (int i = 0; i < l.tagCount(); i++) s.add(l.getStringTagAt(i));
            m.put(cat, Collections.unmodifiableSet(s));
        }
        done = m;
    }

    public static Set<String> keys(String category) {
        Set<String> s = done.get(category);
        return s == null ? Collections.emptySet() : s;
    }

    public static boolean has(String category, String key) {
        return keys(category).contains(key);
    }

    /** Leaving a world: the next one sends its own. */
    public static void clear() {
        done = Collections.emptyMap();
    }
}
