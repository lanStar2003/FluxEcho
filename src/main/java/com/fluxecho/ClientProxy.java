package com.fluxecho;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.MinecraftForge;

import com.fluxecho.codex.CodexTooltips;
import com.fluxecho.codex.GuiCodex;
import com.fluxecho.thaumcraft.TCClient;

import cpw.mods.fml.common.FMLCommonHandler;

public class ClientProxy extends CommonProxy {

    @Override
    public void init() {
        CodexTooltips tips = new CodexTooltips();
        MinecraftForge.EVENT_BUS.register(tips);
        FMLCommonHandler.instance()
            .bus()
            .register(tips);
        if (Mods.thaumcraft) TCClient.init();
    }

    @Override
    public void openCodex(EntityPlayer player) {
        Minecraft.getMinecraft()
            .displayGuiScreen(new GuiCodex());
    }
}
