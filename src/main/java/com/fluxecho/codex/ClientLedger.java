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

    /** The key the team's research comes under: no category is called that. */
    public static final String RESEARCH = "@research";

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

    /** The research nodes the team has done. */
    public static Set<String> research() {
        return keys(RESEARCH);
    }

    /** Entries of every category, the research left out. */
    public static int records() {
        int n = 0;
        for (Map.Entry<String, Set<String>> e : done.entrySet()) if (!RESEARCH.equals(e.getKey())) n += e.getValue()
            .size();
        return n;
    }

    /** Leaving a world: the next one sends its own. */
    public static void clear() {
        done = Collections.emptyMap();
    }
}
