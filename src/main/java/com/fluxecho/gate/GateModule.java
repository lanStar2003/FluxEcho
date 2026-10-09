package com.fluxecho.gate;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.core.EchoRecipes;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Light gates (prototype): the gate, the rooms' shell and glass, the folded zone and the rooms' chunk loading. Always
 * registered, so placed gates and the rooms stay in a world; the recipe can be switched off.
 */
public final class GateModule {

    public static BlockLightGate gate;
    public static BlockInteriorShell shell;
    public static BlockInteriorGlass glass;
    /** The 0.8.1 prototype's interior dimension is registered, so worlds from then load; nothing leads there now. */
    public static boolean oldInterior;

    private GateModule() {}

    public static void preInit() {
        gate = new BlockLightGate();
        GameRegistry.registerBlock(gate, ItemBlockLightGate.class, "light_gate");
        shell = new BlockInteriorShell();
        GameRegistry.registerBlock(shell, "interior_floor");
        glass = new BlockInteriorGlass();
        GameRegistry.registerBlock(glass, "interior_glass");
        GameRegistry.registerTileEntity(TileLightGate.class, "fluxecho:light_gate");
        GateNet.init();
    }

    public static void init() {
        try {
            if (!DimensionManager.registerProviderType(Config.gateProvider, InteriorProvider.class, false))
                throw new IllegalStateException("world provider id " + Config.gateProvider + " is taken");
            DimensionManager.registerDimension(Config.gateDimension, Config.gateProvider);
            oldInterior = true;
        } catch (RuntimeException e) {
            FluxEcho.LOG.warn(
                "The 0.8.1 flux interior dimension could not be registered ({}). Light gates do not need it any more; only a world from 0.8.1 with someone standing in it would miss it.",
                e.getMessage());
        }
        ForgeChunkManager.setForcedChunkLoadingCallback(FluxEcho.instance, new Gates.Tickets());
        GateServer server = new GateServer();
        FMLCommonHandler.instance()
            .bus()
            .register(server);
        MinecraftForge.EVENT_BUS.register(new Zone());
    }

    /** Glass to look through, ender pearls for the link to the flux layer, obsidian to stand on. */
    public static void postInit() {
        if (!Config.defaultRecipes || !Config.gateRecipe) return;
        try {
            EchoRecipes.shaped(
                "Light Gate",
                new ItemStack(gate),
                "GEG",
                "EOE",
                "GEG",
                'G',
                new ItemStack(Blocks.glass),
                'E',
                new ItemStack(Items.ender_pearl),
                'O',
                new ItemStack(Blocks.obsidian));
        } catch (Throwable t) {
            FluxEcho.LOG.error("Failed to register the Light Gate recipe", t);
        }
    }

    public static void serverStopped() {
        GateServer.stopped();
    }
}
