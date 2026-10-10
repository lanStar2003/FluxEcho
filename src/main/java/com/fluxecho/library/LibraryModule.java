package com.fluxecho.library;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.fluxecho.Config;
import com.fluxecho.campus.ModuleSpec;
import com.fluxecho.campus.ModuleSpecs;
import com.fluxecho.logic.ArchiveShape;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.Parts;
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
        com.fluxecho.nexus.NexusModule.previewModule(core, TileLibrary::new);
        com.fluxecho.frame.BlockFrame.onUse(Shelves::use);
        ModuleSpecs.register(spec());
    }

    /**
     * The library as the campus builder raises it (0.10.0): the Echo Archive on a hall site (4 behind the nexus, then
     * 2 and 6), opened by the library research, commissioned with the Echo Library Core. New Archives are offered
     * only while {@code archiveEnabled} is on.
     */
    static ModuleSpec spec() {
        return new ModuleSpec(
            BuildPlan.LIBRARY,
            Research.LIBRARY,
            ArchiveShape.WIDTH,
            ArchiveShape.DEPTH,
            ArchiveShape.HEIGHT,
            CampusPlan.HALL_SITES,
            Parts.LIBRARY_CORE,
            TileLibrary.COLOR,
            BuildPlan::archive,
            BuildPlan::archiveController,
            () -> Config.archiveEnabled);
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
