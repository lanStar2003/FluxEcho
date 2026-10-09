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
 * Light gates (prototype): the gate, the plot floor, the flux interior dimension and its chunk loading. Always
 * registered, so placed gates and the plots stay in a world; the recipe can be switched off.
 */
public final class GateModule {

    public static BlockLightGate gate;
    public static BlockInteriorFloor floor;
    /** False when the interior's dimension id was taken: gates then open nothing. */
    public static boolean dimensionReady;

    private GateModule() {}

    public static void preInit() {
        gate = new BlockLightGate();
        GameRegistry.registerBlock(gate, ItemBlockLightGate.class, "light_gate");
        floor = new BlockInteriorFloor();
        GameRegistry.registerBlock(floor, "interior_floor");
        GameRegistry.registerTileEntity(TileLightGate.class, "fluxecho:light_gate");
        GateNet.init();
    }

    public static void init() {
        try {
            if (!DimensionManager.registerProviderType(Config.gateProvider, InteriorProvider.class, false))
                throw new IllegalStateException("world provider id " + Config.gateProvider + " is taken");
            DimensionManager.registerDimension(Config.gateDimension, Config.gateProvider);
            dimensionReady = true;
        } catch (RuntimeException e) {
            FluxEcho.LOG.error(
                "The flux interior could not be registered ({}); light gates open nothing. Pick free ids in config/fluxecho.cfg, gates.dimensionId and gates.providerId.",
                e.getMessage());
        }
        ForgeChunkManager.setForcedChunkLoadingCallback(FluxEcho.instance, new Gates.Tickets());
        GateServer server = new GateServer();
        MinecraftForge.EVENT_BUS.register(server);
        FMLCommonHandler.instance()
            .bus()
            .register(server);
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
}
