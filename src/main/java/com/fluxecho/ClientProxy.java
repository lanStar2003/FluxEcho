package com.fluxecho;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;

import com.fluxecho.campus.client.CampusClient;
import com.fluxecho.client.FlowFx;
import com.fluxecho.client.MachineFx;
import com.fluxecho.client.MachineHolo;
import com.fluxecho.codex.CodexTooltips;
import com.fluxecho.codex.GuiCodex;
import com.fluxecho.frame.client.FrameClient;
import com.fluxecho.gate.client.GateClient;
import com.fluxecho.mana.ManaHolo;
import com.fluxecho.matter.MatterModule;
import com.fluxecho.matter.client.EchoCrystalRender;
import com.fluxecho.nei.NeiCheck;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.nexus.client.NexusRender;
import com.fluxecho.thaumcraft.TCClient;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;

public class ClientProxy extends CommonProxy {

    @Override
    public void init() {
        CodexTooltips tips = new CodexTooltips();
        MinecraftForge.EVENT_BUS.register(tips);
        FMLCommonHandler.instance()
            .bus()
            .register(tips);
        MachineFx.register();
        FrameClient.register();
        CampusClient.register();
        TileMultiblock.clientWorld = net.minecraft.client.multiplayer.WorldClient.class;
        NexusRender.register();
        MinecraftForgeClient.registerItemRenderer(MatterModule.echoCrystal, new EchoCrystalRender());
        MachineHolo.register();
        FlowFx.register();
        GateClient.register();
        if (Mods.thaumcraft) TCClient.init();
        if (Mods.botania) ManaHolo.register();
        if (Loader.isModLoaded("NotEnoughItems")) ClientCommandHandler.instance.registerCommand(new NeiCheck());
    }

    @Override
    public void openCodex(EntityPlayer player) {
        Minecraft.getMinecraft()
            .displayGuiScreen(new GuiCodex());
    }
}
