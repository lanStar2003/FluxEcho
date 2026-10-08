package com.fluxecho.thaumcraft;

import net.minecraft.block.Block;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.core.MachineId;
import com.fluxecho.core.Machines;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Thaumcraft. Only called when Thaumcraft is loaded. Blocks and machines are registered even when the module is
 * switched off, so placed ones survive; recipes are not, and the machines stop.
 */
public final class TCModule {

    public static Block outlet;

    private TCModule() {}

    public static void preInit() {
        outlet = new BlockEssentiaOutlet();
        GameRegistry.registerBlock(outlet, "essentia_outlet");
        GameRegistry.registerTileEntity(TileEssentiaOutlet.class, "fluxecho:essentia_outlet");
    }

    public static void machines() {
        Machines.put(MachineId.ESSENTIA_ECHO, new MTEEssentiaEcho(Machines.claim(MachineId.ESSENTIA_ECHO)));
        Machines.put(MachineId.VIS_CHARGER, new MTEVisCharger(Machines.claim(MachineId.VIS_CHARGER)));
        Machines.put(MachineId.INSIGHT_ECHO, new MTEInsightEcho(Machines.claim(MachineId.INSIGHT_ECHO)));
        Machines.put(MachineId.CRUCIBLE_ECHO, new MTECrucibleEcho(Machines.claim(MachineId.CRUCIBLE_ECHO)));
        TCRecipeMaps.essentiaEcho();
        TCRecipeMaps.visCharger();
        TCRecipeMaps.insightEcho();
        TCRecipeMaps.crucibleEcho();
    }

    public static void postInit() {
        if (!Config.thaumEnabled || !Config.defaultRecipes) return;
        try {
            TCCrafting.register();
        } catch (Throwable t) {
            FluxEcho.LOG.error("Failed to register the Thaumcraft machine recipes", t);
        }
    }

    public static void serverStopped() {
        AspectMemory.reset();
    }
}
