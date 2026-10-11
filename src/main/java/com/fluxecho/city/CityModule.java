package com.fluxecho.city;

import java.util.List;

import net.minecraft.item.ItemStack;

import com.fluxecho.campus.BlockFitting;
import com.fluxecho.campus.ModuleSpec;
import com.fluxecho.campus.ModuleSpecs;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.logic.DomeGeneratorShape;
import com.fluxecho.logic.EcoDomeShape;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.RootShape;
import com.fluxecho.logic.SiteFamily;
import com.fluxecho.matter.MatterModule;
import com.fluxecho.nexus.Manifests;
import com.fluxecho.nexus.NexusModule;
import com.fluxecho.research.Research;
import com.gtnewhorizon.structurelib.alignment.constructable.IMultiblockInfoContainer;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * The City of Echoes (0.11.0 「百工」): the module framework every building after the Echo Archive shares (its core
 * block {@code fluxecho:module_core}, the units used by hand, the module window) and the modules built on it: the Eco
 * Dome (0.11.0), the zenith's Echo Dome Generator and the root's Tidal Well (0.12.0). Its specs are registered after
 * the library's, so a nexus offers the Archive first.
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
        GameRegistry.registerTileEntity(TileDomeGenerator.class, "fluxecho:" + DomeGeneratorShape.KEY);
        GameRegistry.registerTileEntity(TileRoot.class, "fluxecho:" + RootShape.KEY);
    }

    public static void init() {
        IMultiblockInfoContainer
            .registerTileClass(TileEcoDome.class, NexusModule.infoContainer(EcoDomeShape.KEY + ".structure"));
        IMultiblockInfoContainer.registerTileClass(
            TileDomeGenerator.class,
            NexusModule.infoContainer(DomeGeneratorShape.KEY + ".structure"));
        NexusModule.previewModule(core, ModuleCores.ECO_DOME, TileEcoDome::new);
        IMultiblockInfoContainer
            .registerTileClass(TileRoot.class, NexusModule.infoContainer(RootShape.KEY + ".structure"));
        NexusModule.previewModule(core, ModuleCores.DOME_GENERATOR, TileDomeGenerator::new);
        NexusModule.previewModule(core, ModuleCores.ROOT, TileRoot::new);
        BlockFrame.onUse(UnitUse::use);
        BlockFitting.onUse(UnitUse::use);
        ModuleSpecs.register(ecoDome());
        ModuleSpecs.register(domeGenerator());
        ModuleSpecs.register(root());
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
     * The Echo Dome Generator as the campus builder raises it (0.12.0): on the zenith over the nexus (its pod 21 x 21
     * and 10 high at Y0 + 40, its legs on the forum), opened by the {@code dome} research, commissioned with its module
     * core. It takes no room on the inner ring; what it takes of the campus is its {@link DomeGeneratorShape} envelope.
     */
    public static ModuleSpec domeGenerator() {
        return new ModuleSpec(
            SiteFamily.ZENITH,
            DomeGeneratorShape.KEY,
            Research.DOME,
            DomeGeneratorShape.SIZE,
            DomeGeneratorShape.SIZE,
            DomeGeneratorShape.HEIGHT,
            DomeGeneratorShape.SITES,
            Parts.moduleCore(ModuleCores.DOME_GENERATOR),
            TileDomeGenerator.COLOR,
            DomeGeneratorShape::plan,
            DomeGeneratorShape::controller,
            () -> true,
            new ModuleSpec.Envelope() {

                @Override
                public int[] footprint(int site, int cx, int cz, int fx, int fz, int margin) {
                    return DomeGeneratorShape.footprint(cx, cz, fx, fz, margin);
                }

                @Override
                public List<int[]> keepOut(int site, int cx, int y0, int cz, int fx, int fz) {
                    return DomeGeneratorShape.keepOut(cx, y0, cz, fx, fz);
                }

                @Override
                public List<int[]> protect(int site, int cx, int y0, int cz, int fx, int fz) {
                    return DomeGeneratorShape.protect(cx, y0, cz, fx, fz);
                }

                @Override
                public String noRoom(int y0) {
                    return DomeGeneratorShape.noRoom(y0);
                }
            });
    }

    /**
     * The root under the nexus as the campus builder raises it (0.12.0): this version its Tidal Well (25 x 25, 26 deep
     * from Y0 - 1 to Y0 - 26), opened by the {@code tidal_well} research, sunk as a caisson and commissioned with its
     * module core. It takes no room on the inner ring and nothing on the forum; a campus too close to bedrock has no
     * room for it ({@link RootShape#noRoom}).
     */
    public static ModuleSpec root() {
        return new ModuleSpec(
            SiteFamily.ROOT,
            RootShape.KEY,
            Research.TIDAL_WELL,
            RootShape.SIZE,
            RootShape.SIZE,
            RootShape.HEIGHT,
            RootShape.SITES,
            Parts.moduleCore(ModuleCores.ROOT),
            TileRoot.COLOR,
            RootShape::plan,
            RootShape::controller,
            () -> true,
            new ModuleSpec.Envelope() {

                @Override
                public int[] footprint(int site, int cx, int cz, int fx, int fz, int margin) {
                    return RootShape.footprint(cx, cz, fx, fz, margin);
                }

                @Override
                public List<int[]> keepOut(int site, int cx, int y0, int cz, int fx, int fz) {
                    return RootShape.keepOut();
                }

                @Override
                public List<int[]> protect(int site, int cx, int y0, int cz, int fx, int fz) {
                    return RootShape.protect(cx, y0, cz, fx, fz);
                }

                @Override
                public String noRoom(int y0) {
                    return RootShape.noRoom(y0);
                }
            });
    }

    /**
     * Manifested at the nexus once the Eco Dome is researched: its core from echo crystals, saplings and seeds (the
     * garden it will keep), HV circuits and glass; at phase II (0.12.0) the Echo Dome Generator's and the Tidal Well's
     * cores from phase alloy and resonant glass, the materials of the phase.
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
        // the Echo Dome Generator's core: echo crystals and ender eyes for the dome it projects, phase alloy, resonant
        // glass and EV circuits for phase II (CITY.md Appendix B, BLUEPRINT 3.6)
        Manifests.register(
            new Manifests.Recipe(
                "dome_generator_core",
                Research.DOME,
                new String[] { "ore:" + MatterModule.ECHO_CRYSTAL + "*8", "item:minecraft:ender_eye*8",
                    "ore:" + MatterModule.PHASE_ALLOY + "*8", "ore:circuitEV*2",
                    "ore:" + MatterModule.RESONANT_GLASS + "*8" },
                () -> new ItemStack(core, 1, ModuleCores.DOME_GENERATOR),
                1600,
                false));
        // the Tidal Well's core: echo crystals, phase alloy for the shaft, resonant glass for the tide it will read,
        // titanium plates and EV circuits
        Manifests.register(
            new Manifests.Recipe(
                "root_core",
                Research.TIDAL_WELL,
                new String[] { "ore:" + MatterModule.ECHO_CRYSTAL + "*8", "ore:" + MatterModule.PHASE_ALLOY + "*8",
                    "ore:" + MatterModule.RESONANT_GLASS + "*4", "ore:plateTitanium*16", "ore:circuitEV*2" },
                () -> new ItemStack(core, 1, ModuleCores.ROOT),
                1600,
                false));
    }
}
