package com.fluxecho.codex;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.fluxecho.Config;
import com.fluxecho.core.EchoText;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

/**
 * Client: a line under the tooltip of anything the flux layer can echo (a bee, a seed bag, a phial, ...), saying
 * whether your team has done it once or what to do for it. NEI shows the same tooltips.
 */
public final class CodexTooltips {

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent e) {
        if (!Config.codexHints || e.itemStack == null) return;
        ItemStack s = e.itemStack;
        for (Categories.Category c : Categories.all()) {
            String key;
            try {
                key = c.keyOf.apply(s);
            } catch (RuntimeException ex) {
                continue;
            }
            if (key == null) continue;
            if (ClientLedger.has(c.id, key)) e.toolTip.add(EnumChatFormatting.AQUA + EchoText.t("codex.done"));
            else e.toolTip.add(EnumChatFormatting.DARK_GRAY + EchoText.t("codex.todo." + c.id));
        }
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent e) {
        ClientLedger.clear();
    }
}
