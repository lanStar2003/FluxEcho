package com.fluxecho.frame.client;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import com.fluxecho.frame.BlockFrame;
import com.fluxecho.frame.Formed;
import com.fluxecho.nexus.ClientTiles;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** The flux frame's renderer, and forgetting the formed multiblocks of a world the client leaves. */
@SideOnly(Side.CLIENT)
public final class FrameClient {

    public static void register() {
        BlockFrame.renderId = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(new FrameRender(BlockFrame.renderId));
        MinecraftForge.EVENT_BUS.register(new FrameClient());
    }

    @SubscribeEvent
    public void onUnload(WorldEvent.Unload e) {
        if (!e.world.isRemote) return;
        Formed.clearClient();
        ClientTiles.clear();
    }
}
