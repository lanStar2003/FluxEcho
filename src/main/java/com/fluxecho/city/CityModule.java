package com.fluxecho.city;

import net.minecraft.item.ItemStack;

import com.fluxecho.campus.BlockFitting;
import com.fluxecho.campus.ModuleSpec;
import com.fluxecho.campus.ModuleSpecs;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.logic.EcoDomeShape;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.SiteFamily;
import com.fluxecho.matter.MatterModule;
import com.fluxecho.nexus.Manifests;
import com.fluxecho.nexus.NexusModule;
import com.fluxecho.research.Research;
import com.gtnewhorizon.structurelib.alignment.constructable.IMultiblockInfoContainer;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * The City of Echoes (0.11.0 「百工」): the module framework every building after the Echo Archive shares (its core
 * block {@code fluxecho:module_core}, the units used by hand, the module window) and the modules built on it, the first
 * being the Eco Dome. Its specs are registered after the library's, so a nexus offers the Archive first.
 */
public final class CityModule {

    public static BlockModuleCore core;

    /** The halls the Eco Dome prefers: to the nexus's right, then left, then behind (CITY.md §2.2). */
    public static final int[] ECO_DOME_SITES = EcoDomeShape.SITES;

    private CityModule() {}

    public static void preInit() {
        core = new BlockModuleCore();
        GameRegistry.registerBlock(core, ItemBlockModuleCore.class, "module_core");
        GameRegistry.registerTileEntity(TileEcoDome.class, "fluxecho:" + EcoDomeShape.KEY);
    }

    public static void init() {
        IMultiblockInfoContainer
            .registerTileClass(TileEcoDome.class, NexusModule.infoContainer(EcoDomeShape.KEY + ".structure"));
        NexusModule.previewModule(core, ModuleCores.ECO_DOME, TileEcoDome::new);
        BlockFrame.onUse(UnitUse::use);
        BlockFitting.onUse(UnitUse::use);
        ModuleSpecs.register(ecoDome());
    }

    /**
     * The Eco Dome as the campus builder raises it: a hall (27 x 35, 21 high) on site 2, else 6, else 4, opened by the
     * {@code eco_dome} research, commissioned with its module core.
     */
    public static ModuleSpec ecoDome() {
        return new ModuleSpec(
            SiteFamily.HALL,
            EcoDomeShape.KEY,
            Research.ECO_DOME,
            EcoDomeShape.WIDTH,
            EcoDomeShape.DEPTH,
            EcoDomeShape.HEIGHT,
            ECO_DOME_SITES,
            Parts.moduleCore(ModuleCores.ECO_DOME),
            TileEcoDome.COLOR,
            EcoDomeShape::plan,
            EcoDomeShape::controller,
            () -> true);
    }

    /**
     * Manifested at the nexus once the Eco Dome is researched: its core from echo crystals, saplings and seeds (the
     * garden it will keep), HV circuits and glass.
     */
    public static void postInit() {
        Manifests.register(
            new Manifests.Recipe(
                "eco_dome_core",
                Research.ECO_DOME,
                new String[] { "ore:" + MatterModule.ECHO_CRYSTAL + "*4", "ore:treeSapling*8",
                    "item:minecraft:wheat_seeds*16", "ore:circuitHV*2", "ore:blockGlass*16" },
                () -> new ItemStack(core, 1, ModuleCores.ECO_DOME),
                1200,
                false));
    }
}
