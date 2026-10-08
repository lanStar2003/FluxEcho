package com.fluxecho.thaumcraft;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.codex.Categories;
import com.fluxecho.codex.EchoLedger;
import com.fluxecho.core.MachineId;
import com.fluxecho.core.Machines;

import cpw.mods.fml.common.registry.GameRegistry;
import thaumcraft.api.aspects.Aspect;

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
        Machines.put(MachineId.INFUSION_ECHO, new MTEInfusionEcho(Machines.claim(MachineId.INFUSION_ECHO)));
        TCRecipeMaps.essentiaEcho();
        TCRecipeMaps.visCharger();
        TCRecipeMaps.insightEcho();
        TCRecipeMaps.crucibleEcho();
        TCRecipeMaps.infusionEcho();
    }

    public static void postInit() {
        EchoLedger.source(
            Categories.ASPECT,
            team -> AspectMemory.get()
                .tags(team));
        Categories.register(
            new Categories.Category(
                Categories.ASPECT,
                AspectSamples::phial,
                tag -> Aspect.getAspect(tag) == null ? tag
                    : Aspect.getAspect(tag)
                        .getName(),
                AspectSamples::keyOf));
        Categories.register(new Categories.Category(Categories.INFUSION, ThaumRecipes::stack, key -> {
            ItemStack s = ThaumRecipes.stack(key);
            return s == null ? key : s.getDisplayName();
        }, s -> ThaumRecipes.isProduct(s) ? ThaumRecipes.key(s) : null));
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
