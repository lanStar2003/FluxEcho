package com.fluxecho.city;

import java.util.List;

import net.minecraft.item.ItemStack;

import com.fluxecho.campus.BlockFitting;
import com.fluxecho.campus.ModuleSpec;
import com.fluxecho.campus.ModuleSpecs;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.logic.ArcaneCourtShape;
import com.fluxecho.logic.BloodCovenantShape;
import com.fluxecho.logic.DomeGeneratorShape;
import com.fluxecho.logic.EcoDomeShape;
import com.fluxecho.logic.HuntingGroundShape;
import com.fluxecho.logic.ManaFountainShape;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.RootShape;
import com.fluxecho.logic.ShapeKit;
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
 * Dome (0.11.0), the zenith's Echo Dome Generator and the root's Tidal Well (0.12.0), the four pavilions on the
 * diagonals (0.13.0: the Arcane Court, the Blood Covenant Altar, the Echo Hunting Ground and the Mana Fountain Array,
 * each a {@link #pavilion}). Its specs are registered after the library's, so a nexus offers the Archive first.
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
        GameRegistry.registerTileEntity(TileArcaneCourt.class, "fluxecho:" + ArcaneCourtShape.KEY);
        GameRegistry.registerTileEntity(TileBloodCovenant.class, "fluxecho:" + BloodCovenantShape.KEY);
        GameRegistry.registerTileEntity(TileHuntingGround.class, "fluxecho:" + HuntingGroundShape.KEY);
        GameRegistry.registerTileEntity(TileManaFountain.class, "fluxecho:" + ManaFountainShape.KEY);
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
        // the pavilions (0.13.0)
        IMultiblockInfoContainer
            .registerTileClass(TileArcaneCourt.class, NexusModule.infoContainer(ArcaneCourtShape.KEY + ".structure"));
        NexusModule.previewModule(core, ModuleCores.ARCANE_COURT, TileArcaneCourt::new);
        IMultiblockInfoContainer.registerTileClass(
            TileBloodCovenant.class,
            NexusModule.infoContainer(BloodCovenantShape.KEY + ".structure"));
        NexusModule.previewModule(core, ModuleCores.BLOOD_COVENANT, TileBloodCovenant::new);
        IMultiblockInfoContainer.registerTileClass(
            TileHuntingGround.class,
            NexusModule.infoContainer(HuntingGroundShape.KEY + ".structure"));
        NexusModule.previewModule(core, ModuleCores.HUNTING_GROUND, TileHuntingGround::new);
        IMultiblockInfoContainer
            .registerTileClass(TileManaFountain.class, NexusModule.infoContainer(ManaFountainShape.KEY + ".structure"));
        NexusModule.previewModule(core, ModuleCores.MANA_FOUNTAIN, TileManaFountain::new);
        BlockFrame.onUse(UnitUse::use);
        BlockFitting.onUse(UnitUse::use);
        ModuleSpecs.register(ecoDome());
        ModuleSpecs.register(domeGenerator());
        ModuleSpecs.register(root());
        ModuleSpecs.register(arcaneCourt());
        ModuleSpecs.register(bloodCovenant());
        ModuleSpecs.register(huntingGround());
        ModuleSpecs.register(manaFountain());
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
     * The Arcane Court as the campus builder raises it (0.13.0): a pavilion (25 x 25, 33 high) on diagonal site 7, else
     * 1, 5, 3 (CITY.md §2.2), opened by the {@code arcane_court} research, commissioned with its module core.
     */
    public static ModuleSpec arcaneCourt() {
        return pavilion(
            ArcaneCourtShape.KEY,
            Research.ARCANE_COURT,
            ArcaneCourtShape.HEIGHT,
            ArcaneCourtShape.SITES,
            ModuleCores.ARCANE_COURT,
            TileArcaneCourt.COLOR,
            ArcaneCourtShape::plan,
            ArcaneCourtShape::controller);
    }

    /**
     * The Blood Covenant Altar as the campus builder raises it (0.13.0): a pavilion (25 x 25, 17 high) on diagonal site
     * 5, else 3, 7, 1 (CITY.md §2.2), opened by the {@code blood_covenant} research, commissioned with its module core.
     */
    public static ModuleSpec bloodCovenant() {
        return pavilion(
            BloodCovenantShape.KEY,
            Research.BLOOD_COVENANT,
            BloodCovenantShape.HEIGHT,
            BloodCovenantShape.SITES,
            ModuleCores.BLOOD_COVENANT,
            TileBloodCovenant.COLOR,
            BloodCovenantShape::plan,
            BloodCovenantShape::controller);
    }

    /**
     * The Echo Hunting Ground as the campus builder raises it (0.13.0): a pavilion (25 x 25, 10 high) on diagonal site
     * 3, else 5, 1, 7 (CITY.md §2.2), opened by the {@code hunting_ground} research, commissioned with its module core.
     */
    public static ModuleSpec huntingGround() {
        return pavilion(
            HuntingGroundShape.KEY,
            Research.HUNTING_GROUND,
            HuntingGroundShape.HEIGHT,
            HuntingGroundShape.SITES,
            ModuleCores.HUNTING_GROUND,
            TileHuntingGround.COLOR,
            HuntingGroundShape::plan,
            HuntingGroundShape::controller);
    }

    /**
     * The Mana Fountain Array as the campus builder raises it (0.13.0): a pavilion (25 x 25, 8 high) on diagonal site
     * 1, else 7, 3, 5 (CITY.md §2.2), opened by the {@code mana_fountain} research, commissioned with its module core.
     */
    public static ModuleSpec manaFountain() {
        return pavilion(
            ManaFountainShape.KEY,
            Research.MANA_FOUNTAIN,
            ManaFountainShape.HEIGHT,
            ManaFountainShape.SITES,
            ModuleCores.MANA_FOUNTAIN,
            TileManaFountain.COLOR,
            ManaFountainShape::plan,
            ManaFountainShape::controller);
    }

    /**
     * A pavilion's spec (0.13.0, the PAVILION family, CITY.md §2.1): 25 x 25 on its diagonal sites in the order it
     * prefers them, its controller on the central console, what it takes of the campus its apron and allee
     * ({@link ModuleSpec#keepOut}). Every pavilion is this and its shape.
     */
    static ModuleSpec pavilion(String key, String research, int height, int[] sites, int meta, int colour,
        ModuleSpec.PlanFactory plans, ModuleSpec.ControllerFactory controllers) {
        return new ModuleSpec(
            SiteFamily.PAVILION,
            key,
            research,
            ShapeKit.PAV_SIZE,
            ShapeKit.PAV_SIZE,
            height,
            sites,
            Parts.moduleCore(meta),
            colour,
            plans,
            controllers,
            () -> true);
    }

    /**
     * Manifested at the nexus once the Eco Dome is researched: its core from echo crystals, saplings and seeds (the
     * garden it will keep), HV circuits and glass; at phase II (0.12.0) the Echo Dome Generator's and the Tidal Well's
     * cores from phase alloy and resonant glass, the materials of the phase, and (0.13.0) the four pavilions' cores
     * from the same with one themed vanilla item each.
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
        // the pavilions' cores (0.13.0) at phase II: EV circuits, phase alloy, resonant glass and echo crystals, and
        // one themed vanilla item each: the Arcane Court's an enchanting table, the altar it scales up; the Blood
        // Covenant Altar's nether wart, grown on soul sand; the Echo Hunting Ground's a bow; the Mana Fountain
        // Array's poppies, the flowers its garden keeps
        Manifests.register(
            new Manifests.Recipe(
                "arcane_court_core",
                Research.ARCANE_COURT,
                new String[] { "ore:" + MatterModule.ECHO_CRYSTAL + "*8", "ore:" + MatterModule.PHASE_ALLOY + "*8",
                    "ore:" + MatterModule.RESONANT_GLASS + "*4", "ore:circuitEV*2",
                    "item:minecraft:enchanting_table*1" },
                () -> new ItemStack(core, 1, ModuleCores.ARCANE_COURT),
                1600,
                false));
        Manifests.register(
            new Manifests.Recipe(
                "blood_covenant_core",
                Research.BLOOD_COVENANT,
                new String[] { "ore:" + MatterModule.ECHO_CRYSTAL + "*8", "ore:" + MatterModule.PHASE_ALLOY + "*8",
                    "ore:" + MatterModule.RESONANT_GLASS + "*4", "ore:circuitEV*2", "item:minecraft:nether_wart*8" },
                () -> new ItemStack(core, 1, ModuleCores.BLOOD_COVENANT),
                1600,
                false));
        Manifests.register(
            new Manifests.Recipe(
                "hunting_ground_core",
                Research.HUNTING_GROUND,
                new String[] { "ore:" + MatterModule.ECHO_CRYSTAL + "*8", "ore:" + MatterModule.PHASE_ALLOY + "*8",
                    "ore:" + MatterModule.RESONANT_GLASS + "*4", "ore:circuitEV*2", "item:minecraft:bow*2" },
                () -> new ItemStack(core, 1, ModuleCores.HUNTING_GROUND),
                1600,
                false));
        Manifests.register(
            new Manifests.Recipe(
                "mana_fountain_core",
                Research.MANA_FOUNTAIN,
                new String[] { "ore:" + MatterModule.ECHO_CRYSTAL + "*8", "ore:" + MatterModule.PHASE_ALLOY + "*8",
                    "ore:" + MatterModule.RESONANT_GLASS + "*4", "ore:circuitEV*2", "item:minecraft:red_flower*8" },
                () -> new ItemStack(core, 1, ModuleCores.MANA_FOUNTAIN),
                1600,
                false));
    }
}
