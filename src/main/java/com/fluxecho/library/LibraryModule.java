package com.fluxecho.library;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.fluxecho.matter.MatterModule;
import com.fluxecho.nexus.ItemBlockNexus;
import com.fluxecho.nexus.Manifests;
import com.fluxecho.research.Research;
import com.gtnewhorizon.structurelib.alignment.constructable.IMultiblockInfoContainer;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * The Echo Library, the Flux Nexus's first module: its core, the index cards, and what the nexus manifests for them
 * once the team researched {@code library}.
 */
public final class LibraryModule {

    public static BlockLibraryCore core;
    public static Item card;

    private LibraryModule() {}

    public static void preInit() {
        core = new BlockLibraryCore();
        GameRegistry.registerBlock(core, ItemBlockNexus.class, "library");
        GameRegistry.registerTileEntity(TileLibrary.class, "fluxecho:library");
        card = new ItemLibraryCard();
        GameRegistry.registerItem(card, "library_card");
    }

    public static void init() {
        IMultiblockInfoContainer
            .registerTileClass(TileLibrary.class, com.fluxecho.nexus.NexusModule.infoContainer("library.structure"));
    }

    /**
     * Manifested at the nexus: the core from echo crystals, the Echo Codex (the library is the codex made real), HV
     * circuits and books; index cards from paper and an echo crystal.
     */
    public static void postInit() {
        Manifests.register(
            new Manifests.Recipe(
                "library_core",
                Research.LIBRARY,
                new String[] { "ore:" + MatterModule.ECHO_CRYSTAL + "*4", "item:fluxecho:codex*1", "ore:circuitHV*2",
                    "item:minecraft:book*16" },
                () -> new ItemStack(core),
                1200,
                false));
        Manifests.register(
            new Manifests.Recipe(
                "library_card",
                Research.LIBRARY,
                new String[] { "ore:" + MatterModule.ECHO_CRYSTAL + "*1", "item:minecraft:paper*8" },
                () -> new ItemStack(card, 8),
                100,
                false));
    }
}
