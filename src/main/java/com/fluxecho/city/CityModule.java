package com.fluxecho.city;

import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.fluxecho.campus.BlockDeck;
import com.fluxecho.campus.BlockFitting;
import com.fluxecho.campus.ModuleSpec;
import com.fluxecho.campus.ModuleSpecs;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.logic.ArcaneCourtShape;
import com.fluxecho.logic.BloodCovenantShape;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.CityBuild;
import com.fluxecho.logic.DomeGeneratorShape;
import com.fluxecho.logic.DropletHangarShape;
import com.fluxecho.logic.DualFoilVaultShape;
import com.fluxecho.logic.EcoDomeShape;
import com.fluxecho.logic.HoloTableShape;
import com.fluxecho.logic.HuntingGroundShape;
import com.fluxecho.logic.ManaFountainShape;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.ObservatoryShape;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.ResonanceGateShape;
import com.fluxecho.logic.RootShape;
import com.fluxecho.logic.ShapeKit;
import com.fluxecho.logic.SiteFamily;
import com.fluxecho.logic.SkyRing;
import com.fluxecho.logic.SkySailsShape;
import com.fluxecho.logic.SophonArrayShape;
import com.fluxecho.logic.WeatherStationShape;
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
 * each a {@link #pavilion}), the gate's Resonance Gate (0.14.0, the gate family's {@link #resonanceGate}), the
 * Dual-foil Vault (0.14.0, the third hall, {@link #dualFoilVault}), the outer diagonals' Sophon Array and Holo Sand
 * Table (0.15.0, each an {@link #outerDiag}) and the sky ring's Weather Station, Sky Sail Array, Observatory and
 * Droplet Hangar (0.16.0, each a {@link #sky} module). Its specs are registered after the library's, so a nexus offers
 * the Archive first.
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
        GameRegistry.registerTileEntity(TileResonanceGate.class, "fluxecho:" + ResonanceGateShape.KEY);
        GameRegistry.registerTileEntity(TileDualFoilVault.class, "fluxecho:" + DualFoilVaultShape.KEY);
        GameRegistry.registerTileEntity(TileSophonArray.class, "fluxecho:" + SophonArrayShape.KEY);
        GameRegistry.registerTileEntity(TileHoloTable.class, "fluxecho:" + HoloTableShape.KEY);
        // the sky ring's four (0.16.0)
        GameRegistry.registerTileEntity(TileWeatherStation.class, "fluxecho:" + WeatherStationShape.KEY);
        GameRegistry.registerTileEntity(TileSkySails.class, "fluxecho:" + SkySailsShape.KEY);
        GameRegistry.registerTileEntity(TileObservatory.class, "fluxecho:" + ObservatoryShape.KEY);
        GameRegistry.registerTileEntity(TileDropletHangar.class, "fluxecho:" + DropletHangarShape.KEY);
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
        // the gate's Resonance Gate (0.14.0)
        IMultiblockInfoContainer.registerTileClass(
            TileResonanceGate.class,
            NexusModule.infoContainer(ResonanceGateShape.KEY + ".structure"));
        NexusModule.previewModule(core, ModuleCores.RESONANCE_GATE, TileResonanceGate::new);
        // the Dual-foil Vault (0.14.0), the third hall
        IMultiblockInfoContainer.registerTileClass(
            TileDualFoilVault.class,
            NexusModule.infoContainer(DualFoilVaultShape.KEY + ".structure"));
        NexusModule.previewModule(core, ModuleCores.DUAL_FOIL_VAULT, TileDualFoilVault::new);
        // the outer diagonals' Sophon Array and Holo Sand Table (0.15.0)
        IMultiblockInfoContainer
            .registerTileClass(TileSophonArray.class, NexusModule.infoContainer(SophonArrayShape.KEY + ".structure"));
        NexusModule.previewModule(core, ModuleCores.SOPHON_ARRAY, TileSophonArray::new);
        IMultiblockInfoContainer
            .registerTileClass(TileHoloTable.class, NexusModule.infoContainer(HoloTableShape.KEY + ".structure"));
        NexusModule.previewModule(core, ModuleCores.HOLO_TABLE, TileHoloTable::new);
        // the sky ring's four (0.16.0), in the order their sites go round from the gate
        IMultiblockInfoContainer.registerTileClass(
            TileWeatherStation.class,
            NexusModule.infoContainer(WeatherStationShape.KEY + ".structure"));
        NexusModule.previewModule(core, ModuleCores.WEATHER_STATION, TileWeatherStation::new);
        IMultiblockInfoContainer
            .registerTileClass(TileSkySails.class, NexusModule.infoContainer(SkySailsShape.KEY + ".structure"));
        NexusModule.previewModule(core, ModuleCores.SKY_SAILS, TileSkySails::new);
        IMultiblockInfoContainer
            .registerTileClass(TileObservatory.class, NexusModule.infoContainer(ObservatoryShape.KEY + ".structure"));
        NexusModule.previewModule(core, ModuleCores.OBSERVATORY, TileObservatory::new);
        IMultiblockInfoContainer.registerTileClass(
            TileDropletHangar.class,
            NexusModule.infoContainer(DropletHangarShape.KEY + ".structure"));
        NexusModule.previewModule(core, ModuleCores.DROPLET_HANGAR, TileDropletHangar::new);
        BlockFrame.onUse(UnitUse::use);
        BlockFitting.onUse(UnitUse::use);
        // a unit may be made of canvas panels (0.14.0)
        BlockDeck.onUse(UnitUse::use);
        ModuleSpecs.register(ecoDome());
        ModuleSpecs.register(domeGenerator());
        ModuleSpecs.register(root());
        ModuleSpecs.register(arcaneCourt());
        ModuleSpecs.register(bloodCovenant());
        ModuleSpecs.register(huntingGround());
        ModuleSpecs.register(manaFountain());
        ModuleSpecs.register(resonanceGate());
        ModuleSpecs.register(dualFoilVault());
        ModuleSpecs.register(sophonArray());
        ModuleSpecs.register(holoTable());
        ModuleSpecs.register(weatherStation());
        ModuleSpecs.register(skySails());
        ModuleSpecs.register(observatory());
        ModuleSpecs.register(dropletHangar());
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
     * and 10 high at Y0 + 40, or lower over high ground since 0.14.1, {@link DomeGeneratorShape#datum}; its legs on
     * the forum), opened by the {@code dome} research, commissioned with its module core. It takes no room on the inner
     * ring; what it takes of the campus is its {@link DomeGeneratorShape} envelope.
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

                @Override
                public String shapeFor(int y0) {
                    return DomeGeneratorShape.shapeFor(y0);
                }

                @Override
                public int[] formerController(int site, int cx, int y0, int cz, int fx, int fz) {
                    return DomeGeneratorShape.moor(DomeGeneratorShape.FLOOR_UP)
                        .controller(site, cx, y0, cz, fx, fz);
                }
            });
    }

    /**
     * The root under the nexus as the campus builder raises it (0.12.0): this version its Tidal Well (25 x 25, 26 deep
     * from Y0 - 1 to Y0 - 26, or shallower over low ground since 0.14.1, {@link RootShape#depth}), opened by the
     * {@code tidal_well} research, sunk as a caisson and commissioned with its module core. It takes no room on the
     * inner ring and nothing on the forum; a campus under Y0 14 has no room for it ({@link RootShape#noRoom}).
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

                @Override
                public String shapeFor(int y0) {
                    return RootShape.shapeFor(y0);
                }

                @Override
                public int[] formerController(int site, int cx, int y0, int cz, int fx, int fz) {
                    return RootShape.well(RootShape.DEPTH)
                        .controller(site, cx, y0, cz, fx, fz);
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
     * The Resonance Gate as the campus builder raises it (0.14.0): the gatehouse (31 x 17, 18 high) on site 0, the
     * gate, across the gate road (CITY.md §2.1), opened by the {@code resonance_gate} research at phase II,
     * commissioned with its module core on the kiosk in the road. It takes room on the inner ring like any module
     * there; what it takes of the campus is the ground it works, the passage under its bridge from the bridge's height
     * up only ({@link ModuleSpec#keepOut}).
     */
    public static ModuleSpec resonanceGate() {
        return new ModuleSpec(
            SiteFamily.GATE,
            ResonanceGateShape.KEY,
            Research.RESONANCE_GATE,
            ResonanceGateShape.WIDTH,
            ResonanceGateShape.DEPTH,
            ResonanceGateShape.HEIGHT,
            ResonanceGateShape.SITES,
            Parts.moduleCore(ModuleCores.RESONANCE_GATE),
            TileResonanceGate.COLOR,
            ResonanceGateShape::plan,
            ResonanceGateShape::controller,
            () -> true);
    }

    /**
     * The Dual-foil Vault as the campus builder raises it (0.14.0): a hall (27 x 35, 27 high) on site 6, else 2, else 4
     * (CITY.md §2.2), opened by the {@code dual_foil_vault} research at phase II, commissioned with its module core.
     */
    public static ModuleSpec dualFoilVault() {
        return new ModuleSpec(
            SiteFamily.HALL,
            DualFoilVaultShape.KEY,
            Research.DUAL_FOIL_VAULT,
            DualFoilVaultShape.WIDTH,
            DualFoilVaultShape.DEPTH,
            DualFoilVaultShape.HEIGHT,
            DualFoilVaultShape.SITES,
            Parts.moduleCore(ModuleCores.DUAL_FOIL_VAULT),
            TileDualFoilVault.COLOR,
            DualFoilVaultShape::plan,
            DualFoilVaultShape::controller,
            () -> true);
    }

    /**
     * The Sophon Array as the campus builder raises it (0.15.0): a building of the outer diagonals (31 x 31, 33 high)
     * on O1, else O7, O3, O5 (sites 11, 17, 13, 15), opened by the {@code sophon_array} research at phase III on the
     * ground the outer establish laid, commissioned with its module core on the console at its centre.
     */
    public static ModuleSpec sophonArray() {
        return outerDiag(
            SophonArrayShape.KEY,
            Research.SOPHON_ARRAY,
            SophonArrayShape.HEIGHT,
            SophonArrayShape.SITES,
            ModuleCores.SOPHON_ARRAY,
            TileSophonArray.COLOR,
            SophonArrayShape::plan,
            SophonArrayShape::controller);
    }

    /**
     * The Holo Sand Table as the campus builder raises it (0.15.0): a building of the outer diagonals (31 x 31, 16
     * high) on O7, else O1, O5, O3 (sites 17, 11, 15, 13), opened by the {@code holo_table} research at phase III on
     * the ground the outer establish laid, commissioned with its module core on the console at the table's front.
     */
    public static ModuleSpec holoTable() {
        return outerDiag(
            HoloTableShape.KEY,
            Research.HOLO_TABLE,
            HoloTableShape.HEIGHT,
            HoloTableShape.SITES,
            ModuleCores.HOLO_TABLE,
            TileHoloTable.COLOR,
            HoloTableShape::plan,
            HoloTableShape::controller);
    }

    /**
     * The Weather Station as the campus builder raises it (0.16.0): a sky module (21 x 21, 14 high) on S0 over the gate
     * (site 30), opened by the {@code weather_station} research at phase IV once the pod floats, commissioned with its
     * module core in its hut's portal.
     */
    public static ModuleSpec weatherStation() {
        return sky(
            WeatherStationShape.KEY,
            Research.WEATHER_STATION,
            WeatherStationShape.HEIGHT,
            WeatherStationShape.SITES,
            ModuleCores.WEATHER_STATION,
            TileWeatherStation.COLOR,
            WeatherStationShape.BUILD);
    }

    /**
     * The Sky Sail Array as the campus builder raises it (0.16.0): a sky module (21 x 21, 15 high) on S2, else S6
     * (sites
     * 32, 36), opened by the {@code sky_sails} research at phase IV once the pod floats, commissioned with its module
     * core in its cabin's portal.
     */
    public static ModuleSpec skySails() {
        return sky(
            SkySailsShape.KEY,
            Research.SKY_SAILS,
            SkySailsShape.HEIGHT,
            SkySailsShape.SITES,
            ModuleCores.SKY_SAILS,
            TileSkySails.COLOR,
            SkySailsShape.BUILD);
    }

    /**
     * The Observatory as the campus builder raises it (0.16.0): a sky module (21 x 21, 13 high) on S4 over the Archive
     * (site 34), opened by the {@code observatory} research at phase IV once the pod floats, commissioned with its
     * module core in its vestibule's portal.
     */
    public static ModuleSpec observatory() {
        return sky(
            ObservatoryShape.KEY,
            Research.OBSERVATORY,
            ObservatoryShape.HEIGHT,
            ObservatoryShape.SITES,
            ModuleCores.OBSERVATORY,
            TileObservatory.COLOR,
            ObservatoryShape.BUILD);
    }

    /**
     * The Droplet Hangar as the campus builder raises it (0.16.0): a sky module (21 x 21, 7 high) on S6, else S2 (sites
     * 36, 32), opened by the {@code droplet_hangar} research at phase IV once the pod floats, commissioned with its
     * module core on the kiosk's console.
     */
    public static ModuleSpec dropletHangar() {
        return sky(
            DropletHangarShape.KEY,
            Research.DROPLET_HANGAR,
            DropletHangarShape.HEIGHT,
            DropletHangarShape.SITES,
            ModuleCores.DROPLET_HANGAR,
            TileDropletHangar.COLOR,
            DropletHangarShape.BUILD);
    }

    /**
     * A sky module's spec (0.16.0, the SKY family, CITY.md §2.5, §4.12, {@link SkyRing}): 21 x 21 on its platform at
     * radial 32 on its site's axis, its deck level with the floor of the pod that stands ({@link BuildPlan#sky}: at the
     * datum the base layer gives, or at the one its job carries), its controller where the building puts it facing the
     * pod. It takes nothing of the ground (no keep-out); its protected boxes are its platform's box and its catwalk in
     * the air ({@link SkyRing#protect}); it has room wherever a core may be placed ({@link SkyRing#noRoom}); one stood
     * up by hand beside a pod built on the full legs over high ground (at 40) still docks ({@code formerController}).
     */
    static ModuleSpec sky(String key, String research, int height, int[] sites, int meta, int colour, CityBuild build) {
        int top = topLayer(build);
        return new ModuleSpec(
            SiteFamily.SKY,
            key,
            research,
            SkyRing.SIZE,
            SkyRing.SIZE,
            height,
            sites,
            Parts.moduleCore(meta),
            colour,
            (plan, site, cx, y0, cz, fx, fz) -> BuildPlan
                .sky(site, cx, y0, cz, fx, fz, DomeGeneratorShape.datum(y0), build),
            (site, cx, y0, cz, fx, fz) -> SkyRing
                .controller(build, site, cx, y0, cz, fx, fz, DomeGeneratorShape.datum(y0)),
            () -> true,
            new ModuleSpec.Envelope() {

                @Override
                public int[] footprint(int site, int cx, int cz, int fx, int fz, int margin) {
                    return SkyRing.footprint(site, cx, cz, fx, fz, margin);
                }

                @Override
                public List<int[]> keepOut(int site, int cx, int y0, int cz, int fx, int fz) {
                    return Collections.emptyList();
                }

                @Override
                public List<int[]> protect(int site, int cx, int y0, int cz, int fx, int fz) {
                    return SkyRing.protect(site, cx, y0, cz, fx, fz, DomeGeneratorShape.datum(y0), top);
                }

                @Override
                public String noRoom(int y0) {
                    return SkyRing.noRoom(y0);
                }

                @Override
                public int[] formerController(int site, int cx, int y0, int cz, int fx, int fz) {
                    if (DomeGeneratorShape.datum(y0) == DomeGeneratorShape.FLOOR_UP) return null;
                    return SkyRing.controller(build, site, cx, y0, cz, fx, fz, DomeGeneratorShape.FLOOR_UP);
                }

                @Override
                public int[] controllerAt(int site, int cx, int y0, int cz, int fx, int fz, int datum) {
                    return SkyRing.controller(build, site, cx, y0, cz, fx, fz, datum);
                }

                @Override
                public BuildPlan.Plan planAt(com.fluxecho.logic.CampusPlan plan, int site, int cx, int y0, int cz,
                    int fx, int fz, int datum) {
                    return BuildPlan.sky(site, cx, y0, cz, fx, fz, datum, build);
                }

                @Override
                public List<int[]> protectAt(int site, int cx, int y0, int cz, int fx, int fz, int datum) {
                    return SkyRing.protect(site, cx, y0, cz, fx, fz, SkyRing.datum(y0, datum), top);
                }
            });
    }

    /** The highest layer of a shape that holds anything. */
    private static int topLayer(CityBuild b) {
        int top = 0;
        for (int y = 0; y < b.height(); y++) for (int z = 0; z < b.depth(); z++)
            for (int x = 0; x < b.width(); x++) if (b.cell(x, y, z) != ShapeKit.NONE) top = Math.max(top, y);
        return top;
    }

    /**
     * An outer diagonal building's spec (0.15.0, the OUTER_DIAG family, CITY.md §2.1, §2.3): 31 x 31 on the outer
     * diagonals in the order it prefers them, its controller where the building puts it facing along A toward the
     * nexus, what it takes of the campus its apron and allée ({@link ModuleSpec#keepOut}). It takes no room on the
     * inner ring and is offered only on the outer establish's ground.
     */
    static ModuleSpec outerDiag(String key, String research, int height, int[] sites, int meta, int colour,
        ModuleSpec.PlanFactory plans, ModuleSpec.ControllerFactory controllers) {
        return new ModuleSpec(
            SiteFamily.OUTER_DIAG,
            key,
            research,
            ShapeKit.OUT_SIZE,
            ShapeKit.OUT_SIZE,
            height,
            sites,
            Parts.moduleCore(meta),
            colour,
            plans,
            controllers,
            () -> true);
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
     * and (0.14.0) the Resonance Gate's and the Dual-foil Vault's from the same with one themed vanilla item each;
     * (0.15.0) the outer diagonals' cores at phase III from IV circuits, phase alloy and memory filament, again with
     * one themed vanilla item each (five kinds, so the table keeps a slot for a second record's echo crystals; the
     * resonant glass is their research's); (0.16.0) the sky ring's four cores at phase IV from LuV circuits, manifest
     * matter and the anti-gravity lattice their platforms float on, with one themed vanilla item each, five kinds
     * again.
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
        // the Resonance Gate's core (0.14.0): the same phase II materials, and note blocks, the tuned resonators its
        // chamber holds
        Manifests.register(
            new Manifests.Recipe(
                "resonance_gate_core",
                Research.RESONANCE_GATE,
                new String[] { "ore:" + MatterModule.ECHO_CRYSTAL + "*8", "ore:" + MatterModule.PHASE_ALLOY + "*8",
                    "ore:" + MatterModule.RESONANT_GLASS + "*4", "ore:circuitEV*2", "item:minecraft:noteblock*4" },
                () -> new ItemStack(core, 1, ModuleCores.RESONANCE_GATE),
                1600,
                false));
        // the Dual-foil Vault's core (0.14.0): the same phase II materials, and paintings, the pictures its canvases
        // will hang
        Manifests.register(
            new Manifests.Recipe(
                "dual_foil_vault_core",
                Research.DUAL_FOIL_VAULT,
                new String[] { "ore:" + MatterModule.ECHO_CRYSTAL + "*8", "ore:" + MatterModule.PHASE_ALLOY + "*8",
                    "ore:" + MatterModule.RESONANT_GLASS + "*4", "ore:circuitEV*2", "item:minecraft:painting*4" },
                () -> new ItemStack(core, 1, ModuleCores.DUAL_FOIL_VAULT),
                1600,
                false));
        // the Sophon Array's core (0.15.0) at phase III: IV circuits, phase alloy, memory filament, the stuff of its
        // racks' memory cores, and comparators, the logic its circuit over the sky draws. Five kinds, like every core
        // before it: the table has six input slots and echo crystals of different records do not stack, so the sixth
        // slot is the one that lets the eight crystals come from two records (4 + 4) rather than eight pressings of
        // one; the resonant glass its research takes instead
        Manifests.register(
            new Manifests.Recipe(
                "sophon_array_core",
                Research.SOPHON_ARRAY,
                new String[] { "ore:" + MatterModule.ECHO_CRYSTAL + "*8", "ore:" + MatterModule.PHASE_ALLOY + "*16",
                    "ore:" + MatterModule.MEMORY_FILAMENT + "*16", "ore:circuitIV*2", "item:minecraft:comparator*8" },
                () -> new ItemStack(core, 1, ModuleCores.SOPHON_ARRAY),
                2400,
                false));
        // the Holo Sand Table's core (0.15.0): the same phase III materials in five kinds, and empty maps, the cards
        // its table keeps
        Manifests.register(
            new Manifests.Recipe(
                "holo_table_core",
                Research.HOLO_TABLE,
                new String[] { "ore:" + MatterModule.ECHO_CRYSTAL + "*8", "ore:" + MatterModule.PHASE_ALLOY + "*16",
                    "ore:" + MatterModule.MEMORY_FILAMENT + "*8", "ore:circuitIV*2", "item:minecraft:map*8" },
                () -> new ItemStack(core, 1, ModuleCores.HOLO_TABLE),
                2400,
                false));
        // the sky ring's cores (0.16.0) at phase IV: LuV circuits, manifest matter, the anti-gravity lattice their
        // platforms float on and echo crystals, and the themed item of each one's research: the Weather Station's
        // daylight sensors, the Sky Sail Array's wool (sailcloth), the Observatory's clocks, the Droplet Hangar's ghast
        // tears. Five kinds, the second-record rule of the Sophon Array's core
        skyCore(
            "weather_station_core",
            Research.WEATHER_STATION,
            ModuleCores.WEATHER_STATION,
            "item:minecraft:daylight_detector*4");
        skyCore("sky_sails_core", Research.SKY_SAILS, ModuleCores.SKY_SAILS, "item:minecraft:wool*8");
        skyCore("observatory_core", Research.OBSERVATORY, ModuleCores.OBSERVATORY, "item:minecraft:clock*4");
        skyCore(
            "droplet_hangar_core",
            Research.DROPLET_HANGAR,
            ModuleCores.DROPLET_HANGAR,
            "item:minecraft:ghast_tear*4");
    }

    /**
     * A sky module's core on the manifestation table (0.16.0): echo crystals, manifest matter, the anti-gravity
     * lattice, LuV circuits and its themed item, five kinds.
     */
    private static void skyCore(String id, String research, int meta, String themed) {
        Manifests.register(
            new Manifests.Recipe(
                id,
                research,
                new String[] { "ore:" + MatterModule.ECHO_CRYSTAL + "*8", "ore:" + MatterModule.MANIFEST_MATTER + "*8",
                    "ore:" + MatterModule.ANTI_GRAVITY_LATTICE + "*4", "ore:circuitLuV*2", themed },
                () -> new ItemStack(core, 1, meta),
                3200,
                false));
    }
}
