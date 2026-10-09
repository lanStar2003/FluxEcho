package com.fluxecho.nexus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The FluxEcho multiblocks the client has loaded, for drawing them after the world without walking every tile entity.
 * Each signs itself up on the client while loaded.
 */
public final class ClientTiles {

    private static final Set<TileMultiblock> LOADED = new LinkedHashSet<>();

    private ClientTiles() {}

    static synchronized void add(TileMultiblock t) {
        LOADED.add(t);
    }

    static synchronized void remove(TileMultiblock t) {
        LOADED.remove(t);
    }

    public static synchronized List<TileMultiblock> all() {
        return new ArrayList<>(LOADED);
    }

    public static synchronized void clear() {
        LOADED.clear();
    }
}
