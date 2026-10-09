package com.fluxecho.nexus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Flux Nexuses and modules loaded on the server, by dimension: how modules find the nexus whose ring they stand
 * on, and how the terminal and the nexus find each other. Each tile signs itself up while loaded.
 */
public final class NexusRegistry {

    private static final Map<Integer, List<TileNexus>> NEXUSES = new HashMap<>();
    private static final Map<Integer, List<TileModule>> MODULES = new HashMap<>();

    private NexusRegistry() {}

    static synchronized void add(TileNexus n) {
        List<TileNexus> l = NEXUSES.computeIfAbsent(dim(n), d -> new ArrayList<>());
        if (!l.contains(n)) l.add(n);
    }

    static synchronized void remove(TileNexus n) {
        for (List<TileNexus> l : NEXUSES.values()) l.remove(n);
    }

    static synchronized void add(TileModule m) {
        List<TileModule> l = MODULES.computeIfAbsent(dim(m), d -> new ArrayList<>());
        if (!l.contains(m)) l.add(m);
    }

    static synchronized void remove(TileModule m) {
        for (List<TileModule> l : MODULES.values()) l.remove(m);
    }

    public static synchronized List<TileNexus> nexuses(int dim) {
        List<TileNexus> l = NEXUSES.get(dim);
        return l == null ? new ArrayList<>() : new ArrayList<>(l);
    }

    public static synchronized List<TileNexus> allNexuses() {
        List<TileNexus> out = new ArrayList<>();
        for (List<TileNexus> l : NEXUSES.values()) out.addAll(l);
        return out;
    }

    public static synchronized List<TileModule> modules(int dim) {
        List<TileModule> l = MODULES.get(dim);
        return l == null ? new ArrayList<>() : new ArrayList<>(l);
    }

    public static synchronized List<TileModule> allModules() {
        List<TileModule> out = new ArrayList<>();
        for (List<TileModule> l : MODULES.values()) out.addAll(l);
        return out;
    }

    /** The loaded nexus at the position, or null. */
    public static synchronized TileNexus at(int dim, int x, int y, int z) {
        List<TileNexus> l = NEXUSES.get(dim);
        if (l != null) for (TileNexus n : l) if (n.xCoord == x && n.yCoord == y && n.zCoord == z) return n;
        return null;
    }

    public static synchronized void clear() {
        NEXUSES.clear();
        MODULES.clear();
    }

    private static int dim(TileMultiblock t) {
        return t.getWorldObj() == null ? 0 : t.getWorldObj().provider.dimensionId;
    }
}
