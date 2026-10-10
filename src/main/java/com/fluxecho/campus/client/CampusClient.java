package com.fluxecho.campus.client;

import com.fluxecho.campus.BlockDeck;
import com.fluxecho.campus.BlockFitting;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** The client side of the nexus campus: the chunk-mesh renderers of the deck and the fittings. */
@SideOnly(Side.CLIENT)
public final class CampusClient {

    private CampusClient() {}

    /** Called by the client proxy in init, before any chunk is built. */
    public static void register() {
        BlockDeck.renderId = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(new DeckRender(BlockDeck.renderId));
        BlockFitting.renderId = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(new FittingRender(BlockFitting.renderId));
    }
}
