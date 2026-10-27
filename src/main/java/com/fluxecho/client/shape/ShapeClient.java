package com.fluxecho.client.shape;

import com.fluxecho.skin.BlockSkin;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The client side of the new material families (0.17.0): the one chunk-mesh renderer every family's blocks use
 * ({@link ShapeRender}). Only the client proxy reaches this class, so a dedicated server never loads a render class.
 */
@SideOnly(Side.CLIENT)
public final class ShapeClient {

    private ShapeClient() {}

    /** Called by the client proxy in init, before any chunk is built. */
    public static void register() {
        BlockSkin.renderId = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(new ShapeRender(BlockSkin.renderId));
    }
}
