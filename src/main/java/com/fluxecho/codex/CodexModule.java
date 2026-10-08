package com.fluxecho.codex;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.core.EchoRecipes;

import cpw.mods.fml.common.registry.GameRegistry;

/** The Echo Codex and the ledger behind it. Always on: it only shows what the other modules wrote down. */
public final class CodexModule {

    public static Item codex;

    private CodexModule() {}

    public static void preInit() {
        codex = new ItemCodex();
        GameRegistry.registerItem(codex, "codex");
        EchoNet.init();
    }

    /** A book bound with an ender pearl, the link to the flux layer. */
    public static void postInit() {
        if (!Config.defaultRecipes) return;
        try {
            EchoRecipes.shapeless(
                "Echo Codex",
                new ItemStack(codex),
                new Object[] { new ItemStack(Items.book), new ItemStack(Items.ender_pearl),
                    new ItemStack(Blocks.glass_pane) });
        } catch (Throwable t) {
            FluxEcho.LOG.error("Failed to register the Echo Codex recipe", t);
        }
    }
}
