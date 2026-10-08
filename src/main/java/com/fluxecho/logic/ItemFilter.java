package com.fluxecho.logic;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A config list of items: {@code modid:name} matches every damage value, {@code modid:name:damage} only that one.
 * Entries that do not parse are ignored.
 */
public final class ItemFilter {

    private final Set<String> any = new HashSet<>();
    private final Set<String> exact = new HashSet<>();

    public ItemFilter(String[] entries) {
        if (entries == null) return;
        for (String raw : entries) {
            if (raw == null) continue;
            String e = raw.trim()
                .toLowerCase(Locale.ROOT);
            String[] p = e.split(":");
            if (p.length == 2 && !p[0].isEmpty() && !p[1].isEmpty()) any.add(e);
            else if (p.length == 3 && !p[0].isEmpty() && !p[1].isEmpty()) {
                try {
                    exact.add(p[0] + ":" + p[1] + ":" + Integer.parseInt(p[2]));
                } catch (NumberFormatException ignored) {
                    // not a damage value: skipped
                }
            }
        }
    }

    /** @param id the item's registry name, {@code modid:name} */
    public boolean matches(String id, int damage) {
        if (id == null) return false;
        String k = id.toLowerCase(Locale.ROOT);
        return any.contains(k) || exact.contains(k + ":" + damage);
    }

    public boolean isEmpty() {
        return any.isEmpty() && exact.isEmpty();
    }
}
