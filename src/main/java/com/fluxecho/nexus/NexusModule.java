package com.fluxecho.nexus;

import net.minecraft.item.ItemStack;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.Mods;
import com.fluxecho.codex.CodexModule;
import com.fluxecho.core.EchoRecipes;
import com.fluxecho.matter.MatterModule;
import com.fluxlite.block.ModBlocks;
import com.gtnewhorizon.structurelib.alignment.constructable.IMultiblockInfoContainer;

import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;

/** The Flux Nexus: its core block, its manifestation recipes, its structure for StructureLib and NEI. */
public final class NexusModule {

    public static BlockNexusCore core;

    private NexusModule() {}

    public static void preInit() {
        core = new BlockNexusCore();
        GameRegistry.registerBlock(core, ItemBlockNexus.class, "nexus");
        GameRegistry.registerTileEntity(TileNexus.class, "fluxecho:nexus");
    }

    public static void init() {
        IMultiblockInfoContainer.registerTileClass(TileNexus.class, new InfoContainer<TileNexus>("nexus.structure"));
        if (Mods.waila) FMLInterModComms.sendMessage("Waila", "register", "com.fluxecho.nexus.NexusWaila.register");
    }

    /** StructureLib's view of a FluxEcho multiblock, for a module's core. */
    public static <T extends TileMultiblock> IMultiblockInfoContainer<T> infoContainer(String descriptionKey) {
        return new InfoContainer<>(descriptionKey);
    }

    /**
     * The core: flux crystal, the Echo Codex (what it anchors is the team's echoes), the Flux Monitor (its network),
     * an HV hull and HV circuits.
     */
    public static void postInit() {
        Manifests.init();
        if (!Config.defaultRecipes || !Config.nexusEnabled) return;
        try {
            EchoRecipes.shaped(
                "Flux Nexus Core",
                new ItemStack(core),
                "CXC",
                "RHR",
                "CMC",
                'C',
                MatterModule.CRYSTAL,
                'X',
                new ItemStack(CodexModule.codex),
                'R',
                OrePrefixes.circuit.get(Materials.HV),
                'H',
                ItemList.Hull_HV.get(1),
                'M',
                ModBlocks.controlCenter == null ? null : new ItemStack(ModBlocks.controlCenter));
        } catch (Throwable t) {
            FluxEcho.LOG.error("Failed to register the Flux Nexus Core recipe", t);
        }
    }

    public static void serverStopped() {
        NexusRegistry.clear();
    }
}
