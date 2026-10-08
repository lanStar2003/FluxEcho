package com.fluxecho.thaumcraft;

import cpw.mods.fml.client.registry.ClientRegistry;

/** Client-only Thaumcraft parts, kept out of {@link TCModule} so a dedicated server never loads them. */
public final class TCClient {

    private TCClient() {}

    /** The floating wand on the Flux Vis Pedestal. */
    public static void init() {
        ClientRegistry.bindTileEntitySpecialRenderer(TileVisPedestal.class, new RenderVisPedestal());
    }
}
