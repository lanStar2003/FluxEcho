package com.fluxecho.frame.client;

import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import com.fluxecho.FluxEcho;
import com.fluxecho.city.client.Paintings;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.frame.Formed;
import com.fluxecho.library.client.LibraryBooks;
import com.fluxecho.nexus.ClientTiles;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The flux frame's renderer, the textures of the shelf units' compartments and books, and forgetting the formed
 * multiblocks, the shelved books and (0.14.0) the canvases' painted icons of a world the client leaves; the painted
 * icons are forgotten too when the textures are stitched again (a resource pack changed), so they are painted anew.
 */
@SideOnly(Side.CLIENT)
public final class FrameClient {

    public static void register() {
        BlockFrame.renderId = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(new FrameRender(BlockFrame.renderId));
        MinecraftForge.EVENT_BUS.register(new FrameClient());
    }

    /**
     * The compartment's back panel, its boards and the book spine are drawn on the shelf, but are not icons of the
     * frame.
     */
    @SubscribeEvent
    public void onStitch(TextureStitchEvent.Pre e) {
        if (e.map.getTextureType() != 0) return;
        ShelfUnits.niche = e.map.registerIcon(FluxEcho.MODID + ":frame/shelf_niche");
        ShelfUnits.board = e.map.registerIcon(FluxEcho.MODID + ":frame/shelf_board");
        ShelfUnits.spine = e.map.registerIcon(FluxEcho.MODID + ":frame/book_spine");
    }

    /** The icons the canvases painted may have changed with the textures: they are painted again when next seen. */
    @SubscribeEvent
    public void onRestitch(TextureStitchEvent.Pre e) {
        Paintings.clear();
    }

    @SubscribeEvent
    public void onUnload(WorldEvent.Unload e) {
        if (!e.world.isRemote) return;
        Formed.clearClient();
        LibraryBooks.clear();
        com.fluxecho.city.TileCityModule.clearClient();
        Paintings.clear();
        ClientTiles.clear();
    }
}
