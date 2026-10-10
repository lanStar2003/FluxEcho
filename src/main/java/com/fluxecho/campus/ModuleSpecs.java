package com.fluxecho.campus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The module kinds the campus builder knows, by key, in registration order (which is also the order a nexus offers
 * them in). Each module registers its own {@link ModuleSpec} during init; the client and the server register the same
 * ones, so a job key such as {@code module:library@4} means the same plan on both sides.
 */
public final class ModuleSpecs {

    private static final Map<String, ModuleSpec> SPECS = new LinkedHashMap<>();

    private ModuleSpecs() {}

    /**
     * Registers a module kind.
     *
     * @throws IllegalStateException if the key is taken
     */
    public static synchronized void register(ModuleSpec spec) {
        if (SPECS.containsKey(spec.key)) throw new IllegalStateException("module spec registered twice: " + spec.key);
        SPECS.put(spec.key, spec);
    }

    /** The spec of a module key, or null. */
    public static synchronized ModuleSpec get(String key) {
        return key == null ? null : SPECS.get(key);
    }

    /** Every spec, in registration order. */
    public static synchronized List<ModuleSpec> all() {
        return Collections.unmodifiableList(new ArrayList<>(SPECS.values()));
    }

    /** The module key of a module job key {@code module:<key>@<site>}, or null for any other key. */
    public static String moduleOf(String jobKey) {
        if (jobKey == null || !jobKey.startsWith("module:")) return null;
        int at = jobKey.lastIndexOf('@');
        return at < 0 ? null : jobKey.substring("module:".length(), at);
    }

    /** The site of a module job key {@code module:<key>@<site>}, or -1 for any other key. */
    public static int siteOf(String jobKey) {
        if (moduleOf(jobKey) == null) return -1;
        try {
            return Integer.parseInt(jobKey.substring(jobKey.lastIndexOf('@') + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** The spec of a module job key, or null. */
    public static ModuleSpec ofJob(String jobKey) {
        return get(moduleOf(jobKey));
    }
}
