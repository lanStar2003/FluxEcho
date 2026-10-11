package com.fluxecho.campus;

import com.fluxecho.nexus.TileNexus;

/**
 * A tile entity of ours that belongs to a nexus and that the campus builder links when it places one, as it links the
 * supply port ({@link TileSupplyPort#linkAt}): the recycle bin (0.10.1). After placing a {@code RECYCLE_BIN} step the
 * builder calls {@link #linkTo} on the tile standing in the cell, if it is one of these; a bin placed by hand links
 * itself (the nearest loaded nexus of its placer's team, {@link TileSupplyPort#nearestNexus}).
 */
public interface NexusLink {

    /**
     * Links the tile to the nexus whose campus placed it (server side), and turns its front to the campus's walkway the
     * way {@link TileSupplyPort} does.
     */
    void linkTo(TileNexus nexus);
}
