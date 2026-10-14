package com.fluxecho.city;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fluxecho.campus.ModuleSpec;
import com.fluxecho.campus.ModuleSpecs;
import com.fluxecho.frame.Formed;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.CampusBand;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.CityOffer;
import com.fluxecho.logic.DomeGeneratorShape;
import com.fluxecho.logic.DropletHangarShape;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.ObservatoryShape;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.ResearchTree;
import com.fluxecho.logic.SiteFamily;
import com.fluxecho.logic.SkyRing;
import com.fluxecho.logic.SkySailsShape;
import com.fluxecho.logic.UnitLayout;
import com.fluxecho.logic.WeatherStationShape;
import com.fluxecho.matter.MatterModule;
import com.fluxecho.nexus.Manifests;
import com.fluxecho.nexus.NexusGui;
import com.fluxecho.nexus.TileNexus;
import com.fluxecho.research.Research;

/**
 * The sky ring's four modules as their tiles and specs see them (0.16.0, CITY.md §4.12, M13 §5, §6), for every
 * facing ({@link OuterDiagTileChecks} where its checks are any building's): the blueprint sits where StructureLib's
 * facing math looks for it, the console under the controller opens the window, every socket is its slot, a module the
 * campus builder raised on any sky module site has its frame where the plan put the blocks and its controller exactly
 * where the nexus's dock looks for it (at either deck it may stand at), the cells that dissolve (the Weather Station's
 * mast, not its pylons), its spec (the sky family, never a berth, no keep-out, its protected boxes in the air over the
 * ground modules' boxes), offered at phase IV only while the pod floats, its window's plan fits, its core is manifested
 * with phase IV's materials, and its run-time names are in both lang files.
 */
class TileSkyModulesTest {

    static final OuterDiagTileChecks.Building WEATHER = new OuterDiagTileChecks.Building(
        TileWeatherStation::new,
        CityModule.weatherStation(),
        WeatherStationShape.BUILD,
        WeatherStationShape.UNITS,
        ModuleCores.WEATHER_STATION,
        TileWeatherStation.COLOR,
        WeatherStationShape.DISSOLVES),
        SAILS = new OuterDiagTileChecks.Building(
            TileSkySails::new,
            CityModule.skySails(),
            SkySailsShape.BUILD,
            SkySailsShape.UNITS,
            ModuleCores.SKY_SAILS,
            TileSkySails.COLOR,
            SkySailsShape.DISSOLVES),
        OBSERVATORY = new OuterDiagTileChecks.Building(
            TileObservatory::new,
            CityModule.observatory(),
            ObservatoryShape.BUILD,
            ObservatoryShape.UNITS,
            ModuleCores.OBSERVATORY,
            TileObservatory.COLOR,
            ObservatoryShape.DISSOLVES),
        HANGAR = new OuterDiagTileChecks.Building(
            TileDropletHangar::new,
            CityModule.dropletHangar(),
            DropletHangarShape.BUILD,
            DropletHangarShape.UNITS,
            ModuleCores.DROPLET_HANGAR,
            TileDropletHangar.COLOR,
            DropletHangarShape.DISSOLVES);

    private static final OuterDiagTileChecks.Building[] ALL = { WEATHER, SAILS, OBSERVATORY, HANGAR };
    private static final int[] MODULE_SITES = { 30, 32, 34, 36 };
    private static final int X = OuterDiagTileChecks.X, Y = OuterDiagTileChecks.Y, Z = OuterDiagTileChecks.Z;

    @Test
    void theBlueprintMapsCellsAsStructureLibDoes() {
        for (OuterDiagTileChecks.Building b : ALL) OuterDiagTileChecks.blueprintMapsCellsAsStructureLibDoes(b);
    }

    @Test
    void theControllerStandsOnItsConsoleAndEverySocketIsItsSlot() {
        int[][] ctrl = { { 10, 2, 1 }, { 10, 2, 1 }, { 10, 2, 1 }, { 10, 2, 2 } };
        int[] metas = { 14, 11, 12, 13 };
        for (int i = 0; i < ALL.length; i++) {
            OuterDiagTileChecks.Building p = ALL[i];
            for (int facing = 2; facing <= 5; facing++) {
                TileCityModule t = p.at(X, Y, Z, facing);
                assertArrayEquals(ctrl[i], t.local(X, Y, Z), p.spec.key);
                int[] k = t.worldOf(ctrl[i][0], 1, ctrl[i][2]);
                assertArrayEquals(new int[] { X, Y - 1, Z }, k, "the console is right under the controller");
                assertTrue(t.consoleAt(k[0], k[1], k[2]), p.spec.key + ": the console opens the window");
                assertEquals(metas[i], t.coreMetaOf());
                assertEquals(p.spec.key, t.moduleKey());
                assertEquals(p.colour, t.moduleColor());
                assertEquals(p.layout.size(), t.unitCount());
                assertArrayEquals(t.worldOf(10, 0, 10), t.centre(), "the platform's centre");
            }
            OuterDiagTileChecks.everySocketIsItsSlotAndEveryUnitItsKind(p);
            OuterDiagTileChecks.localIsTheInverseOfWorldOf(p);
        }
        assertEquals(0x9AD0FF, TileWeatherStation.COLOR);
        assertEquals(0xFFD27A, TileSkySails.COLOR);
        assertEquals(0xB9C8FF, TileObservatory.COLOR);
        assertEquals(0xD8E4F0, TileDropletHangar.COLOR);
        // the weather consoles are units: clicking one uses its unit, not the window's console
        TileCityModule w = WEATHER.at(X, Y, Z, 2);
        int[] console = w.worldOf(13, 1, 12);
        assertFalse(w.consoleAt(console[0], console[1], console[2]));
        assertEquals(0, w.unitAt(console[0], console[1], console[2]));
        assertEquals("weather", w.unitKind(0));
        assertEquals(
            "droplet_cradle",
            HANGAR.at(X, Y, Z, 3)
                .unitKind(7));
    }

    @Test
    void theCellsThatDissolveLetPlayersThrough() {
        for (int facing = 2; facing <= 5; facing++) {
            TileCityModule w = WEATHER.at(X, Y, Z, facing);
            for (int y = 1; y <= 13; y++) {
                int[] mast = w.worldOf(10, y, 12);
                char ch = WeatherStationShape.SHAPE.get(10, y, 12);
                assertEquals(Formed.HIDE | Formed.PASS, w.flags(ch, mast[0], mast[1], mast[2]), "the mast at y" + y);
            }
            for (int y = 1; y <= 8; y++) for (int x : new int[] { 4, 16 }) {
                int[] pylon = w.worldOf(x, y, 17);
                char ch = WeatherStationShape.SHAPE.get(x, y, 17);
                assertEquals(0, w.flags(ch, pylon[0], pylon[1], pylon[2]), "a vane pylon keeps its block");
            }
            int[] console = w.worldOf(13, 1, 12);
            assertEquals(0, w.flags('K', console[0], console[1], console[2]));
        }
        TileCityModule o = OBSERVATORY.at(X, Y, Z, 4);
        // the whole pier, its console too, gives way to the telescope (CITY.md §4.12)
        for (int y = 1; y <= 7; y++) {
            int[] pier = o.worldOf(10, y, 12);
            assertEquals(
                Formed.HIDE | Formed.PASS,
                o.flags(ObservatoryShape.SHAPE.get(10, y, 12), pier[0], pier[1], pier[2]),
                "the pier at y" + y);
        }
        // the portal's console under the controller keeps its block, and opens the window
        int[] portal = o.worldOf(10, 1, 1);
        assertEquals(0, o.flags('K', portal[0], portal[1], portal[2]), "people stand at it");
        assertTrue(o.consoleAt(portal[0], portal[1], portal[2]));
        OuterDiagTileChecks.itsDissolvingCellsLetPlayersThrough(HANGAR, "fruoK");
        OuterDiagTileChecks.itsDissolvingCellsLetPlayersThrough(SAILS, "frqcopnWGwhK-");
    }

    /**
     * Raised on any sky module site, for every front of the nexus and at either deck it may stand at (the base layer's
     * datum and the full legs' 40 a pod built by hand over high ground floats at): its frame is where the plan put
     * every block, its centre is the platform's, its front faces the pod, and the nexus's dock finds it by the exact
     * controller its spec gives that deck ({@code controller}, or {@code formerController} at 40).
     */
    @Test
    void aBuiltModulesFrameIsThePlans() {
        CampusPlan campus = new CampusPlan(11L);
        int cx = 40, cz = -80;
        for (OuterDiagTileChecks.Building p : ALL) for (int[] f : OuterDiagTileChecks.FACINGS)
            for (int site : MODULE_SITES) for (int y0 : new int[] { 64, 205, 212 }) {
                int base = DomeGeneratorShape.datum(y0);
                for (int datum : new int[] { base, DomeGeneratorShape.FLOOR_UP }) {
                    if (y0 + datum + 2 > 255) continue;
                    int[] c = p.spec.controller(site, cx, y0, cz, f[0], f[1], datum);
                    int[] dock = datum == base ? p.spec.controller(site, cx, y0, cz, f[0], f[1])
                        : p.spec.formerController(site, cx, y0, cz, f[0], f[1]);
                    assertArrayEquals(dock, c, p.spec.key + " docks at F " + datum);
                    TileCityModule t = p.at(c[0], c[1], c[2], c[3]);
                    BuildPlan.Plan plan = p.spec.plan(campus, site, cx, y0, cz, f[0], f[1], datum);
                    int units = 0, cores = 0;
                    for (BuildPlan.Step s : plan.steps) {
                        if (s.part == Parts.moduleCore(p.meta)) {
                            assertArrayEquals(new int[] { c[0], c[1], c[2] }, new int[] { s.x, s.y, s.z });
                            cores++;
                        }
                        int[] l = t.local(s.x, s.y, s.z);
                        boolean inBox = l[0] >= 0 && l[0] < 21
                            && l[1] >= 0
                            && l[1] < p.build.height()
                            && l[2] >= 0
                            && l[2] < 21;
                        if (s.kind == BuildPlan.HARD) {
                            assertTrue(inBox, "a checked step outside the box: " + s);
                            char ch = p.build.cell(l[0], l[1], l[2]);
                            assertEquals(p.build.part(l[0], l[1], l[2], ch), s.part, "site " + site + " " + s);
                        }
                        if (s.group == BuildPlan.NO_GROUP) continue;
                        assertEquals(s.group, t.unitAt(s.x, s.y, s.z), "site " + site + " " + s);
                        units++;
                    }
                    assertEquals(1, cores);
                    int want = 0;
                    for (UnitLayout.Unit u : p.layout.units()) want += u.cells.size();
                    assertEquals(want, units);
                    int[] centre = t.centre();
                    int[] local = CampusPlan.toLocal(centre[0] - cx, centre[2] - cz, f[0], f[1]);
                    assertArrayEquals(SkyRing.centre(site), local);
                    assertEquals(y0 + datum, centre[1], "the deck is the pod's floor");
                    int[] ax = SkyRing.axis(site);
                    assertArrayEquals(
                        new int[] { -ax[0], -ax[1] },
                        CampusPlan.toLocal(t.front().offsetX, t.front().offsetZ, f[0], f[1]),
                        "it faces the pod");
                }
            }
    }

    /**
     * The spec: a sky module on its preferred sites, opened by its research, the core of its meta; never a berth; no
     * keep-out on the ground; its protected boxes in the air, over every ground module's boxes and clear columns under
     * the ring; it fits every base the band allows.
     */
    @Test
    void theSpecTakesTheAirOnly() {
        int[][] sites = { { 30 }, { 32, 36 }, { 34 }, { 36, 32 } };
        for (int i = 0; i < ALL.length; i++) {
            ModuleSpec spec = ALL[i].spec;
            assertEquals(SiteFamily.SKY, spec.family);
            assertEquals(spec.key, spec.research, "the module is its research node");
            assertArrayEquals(sites[i], spec.sites);
            assertEquals(21, spec.width);
            assertEquals(21, spec.depth);
            assertEquals(ALL[i].build.height(), spec.height);
            assertEquals(Parts.moduleCore(ALL[i].meta), spec.corePart);
            assertFalse(spec.axis(), "it takes no room on the ring, and is no axis module");
            assertNull(spec.shapeFor(79));
            for (int y0 = CampusBand.LOWEST; y0 <= CampusBand.HIGHEST; y0++)
                assertNull(spec.noRoom(y0), spec.key + " at Y0 " + y0);
            for (int site = 0; site < 70; site++) assertEquals(
                SiteFamily.of(site) == SiteFamily.SKY && site % 2 == 0,
                spec.hasSite(site),
                spec.key + " on " + site);
        }
        List<ModuleSpec> ground = new ArrayList<>();
        Collections.addAll(
            ground,
            CityModule.ecoDome(),
            CityModule.dualFoilVault(),
            CityModule.resonanceGate(),
            com.fluxecho.library.LibraryModule.spec());
        CampusPlan campus = new CampusPlan(3L);
        int cx = -200, cz = 512;
        for (int[] f : new int[][] { OuterDiagTileChecks.FACINGS[0], OuterDiagTileChecks.FACINGS[1] })
            for (int y0 : new int[] { 64, 206, 210, 220 }) {
                // the ground modules under the ring: their protected boxes and clear columns stop under the deck
                List<int[]> under = new ArrayList<>();
                for (ModuleSpec g : ground) for (int gs : g.sites) {
                    for (int[] b : g.protect(campus, gs, cx, y0, cz, f[0], f[1])) {
                        assertTrue(Math.max(b[1], b[4]) <= SkyRing.groundTop(y0), g.key + " protects over the deck");
                        under.add(b);
                    }
                    for (BuildPlan.Column c : g.plan(campus, gs, cx, y0, cz, f[0], f[1]).clear)
                        assertTrue(c.yTo <= SkyRing.groundTop(y0), g.key + " clears into the sky ring");
                }
                List<int[]> pod = DomeGeneratorShape.protect(cx, y0, cz, f[0], f[1]);
                for (OuterDiagTileChecks.Building p : ALL) for (int site : MODULE_SITES) {
                    ModuleSpec spec = p.spec;
                    assertTrue(
                        spec.keepOut(campus, site, cx, y0, cz, f[0], f[1])
                            .isEmpty(),
                        "nothing on the ground");
                    List<int[]> protect = spec.protect(campus, site, cx, y0, cz, f[0], f[1]);
                    for (int[] b : protect) assertTrue(b[1] > SkyRing.groundTop(y0), "in the air: " + b[1]);
                    BuildPlan.Plan plan = spec.plan(campus, site, cx, y0, cz, f[0], f[1]);
                    for (BuildPlan.Step s : plan.steps) {
                        // every cell of its job lies in its protected boxes or the pod's (its door, its catwalk's
                        // mouth), and in no ground module's
                        assertTrue(in(protect, s.x, s.y, s.z) || in(pod, s.x, s.y, s.z), spec.key + " " + s);
                        assertFalse(in(under, s.x, s.y, s.z), spec.key + " meets a ground module's box: " + s);
                    }
                    for (BuildPlan.Column c : plan.clear) assertTrue(c.yFrom > SkyRing.groundTop(y0));
                }
            }
    }

    private static boolean in(List<int[]> boxes, int x, int y, int z) {
        for (int[] b : boxes) {
            if (x >= Math.min(b[0], b[3]) && x <= Math.max(b[0], b[3])
                && y >= Math.min(b[1], b[4])
                && y <= Math.max(b[1], b[4])
                && z >= Math.min(b[2], b[5])
                && z <= Math.max(b[2], b[5])) return true;
        }
        return false;
    }

    /**
     * Offered from phase IV once the pod floats ({@link SiteFamily#POD_FLOATS}): its preference list, then the other
     * module sites; never a berth, not before phase IV, nor while the pod does not float. Each kind takes a site of its
     * own: four modules, four sites.
     */
    @Test
    void itIsOfferedOnceThePodFloats() {
        int[][] want = { { 30, 32, 34, 36 }, { 32, 36, 30, 34 }, { 34, 30, 32, 36 }, { 36, 32, 30, 34 } };
        for (int i = 0; i < ALL.length; i++) {
            ModuleSpec spec = ALL[i].spec;
            assertArrayEquals(want[i], spec.candidates(4, g -> SiteFamily.POD_FLOATS.equals(g)), spec.key);
            assertArrayEquals(want[i], spec.candidates(5, g -> true), spec.key + ": never a berth");
            assertEquals(0, spec.candidates(3, g -> true).length, "phase III has no sky sites");
            assertEquals(0, spec.candidates(4, g -> false).length, "not before the pod floats");
        }
        List<CityOffer.Kind> kinds = new ArrayList<>();
        for (OuterDiagTileChecks.Building b : ALL) kinds.add(b.spec.kind(64));
        CityOffer.Sites free = (k, s) -> CityOffer.FREE;
        List<CityOffer.Offer> offers = CityOffer
            .look(kinds, 4, r -> true, null, free, null, 0, g -> SiteFamily.POD_FLOATS.equals(g));
        int[] got = new int[4];
        for (int i = 0; i < 4; i++) {
            assertTrue(
                offers.get(i)
                    .offered(),
                offers.get(i)
                    .toString());
            got[i] = offers.get(i).site;
        }
        assertArrayEquals(new int[] { 30, 32, 34, 36 }, got);
        // the pod moored, or no pod: every one waits, saying the pod must float first
        for (CityOffer.Offer o : CityOffer.look(kinds, 4, r -> true, null, free, null, 0, g -> false)) {
            assertFalse(o.offered());
            assertEquals(CityOffer.POD, o.reason, o.toString());
            assertTrue(SkyRing.platformSite(o.site), "where it would go: " + o);
        }
        // before phase IV: the phase
        for (CityOffer.Offer o : CityOffer.look(kinds, 3, r -> true, null, free, null, 0, g -> true))
            assertEquals(CityOffer.PHASE, o.reason);
    }

    @Test
    void theWindowsPlanFits() {
        for (OuterDiagTileChecks.Building b : ALL) OuterDiagTileChecks.theWindowsPlanFits(b);
    }

    /**
     * Its core is manifested at the nexus once its research is done (CITY.md Appendix B): phase IV's materials, LuV
     * circuits, manifest matter and the anti-gravity lattice its platform floats on, and its themed vanilla item, in
     * five kinds so the table's sixth input slot takes a second record's echo crystals. Its research is a phase IV node
     * after the sky ring, and takes the lattice too.
     */
    @Test
    void itsCoreIsManifestedWithPhaseFoursMaterials() {
        CityModule.postInit();
        String[] themed = { "minecraft:daylight_detector", "minecraft:wool", "minecraft:clock",
            "minecraft:ghast_tear" };
        for (int i = 0; i < ALL.length; i++) {
            ModuleSpec spec = ALL[i].spec;
            Manifests.Recipe r = Manifests.get(spec.key + "_core");
            assertNotNull(r, spec.key + "'s core is manifested");
            assertEquals(spec.research, r.research);
            assertFalse(r.record);
            Set<String> names = new HashSet<>();
            for (ResearchTree.Cost c : r.inputs) names.add((c.ore ? "ore:" : "item:") + c.name);
            for (String w : new String[] { "ore:" + MatterModule.ECHO_CRYSTAL, "ore:" + MatterModule.MANIFEST_MATTER,
                "ore:" + MatterModule.ANTI_GRAVITY_LATTICE, "ore:circuitLuV", "item:" + themed[i] })
                assertTrue(names.contains(w), spec.key + " takes " + w);
            assertEquals(5, r.inputs.size());
            assertTrue(r.inputs.size() < TileNexus.INPUTS, "a slot left for a second record's crystals");
            ResearchTree.Node node = ResearchTree.get(r.research);
            assertEquals(4, node.phase, "a phase IV research");
            assertTrue(node.requires.contains(Research.SKY_RING));
            boolean lattice = false;
            for (ResearchTree.Cost c : node.costs) lattice |= c.ore && MatterModule.ANTI_GRAVITY_LATTICE.equals(c.name);
            assertTrue(lattice, spec.key + "'s research takes the lattice");
        }
    }

    /**
     * The names the game builds at run time for the four (their stages on the build page, their modules, cores,
     * research, unit kinds and the sky sites they stand on, the reason they wait for the pod) are in both lang files.
     */
    @Test
    void theirRunTimeKeysAreInBothLangFiles() throws IOException {
        List<String> keys = new ArrayList<>();
        for (OuterDiagTileChecks.Building p : ALL) {
            String key = p.spec.key;
            if (ModuleSpecs.get(key) == null) ModuleSpecs.register(p.spec);
            for (int site : MODULE_SITES) {
                String job = BuildPlan.moduleKey(key, site);
                for (int s = 0; s <= SkyRing.K_COMMISSION + 1; s++) {
                    String k = NexusGui.stageKey(job, s);
                    assertEquals("build.stage.k" + Math.min(s, SkyRing.K_COMMISSION), k);
                    keys.add("fluxecho." + k);
                }
            }
            Collections.addAll(
                keys,
                "fluxecho.module." + key,
                "tile.fluxecho.module_core." + key + ".name",
                "fluxecho." + key + ".tip",
                "fluxecho." + key + ".structure",
                "fluxecho.research.node." + key,
                "fluxecho.research.node." + key + ".desc");
            for (int u = 0; u < p.layout.size(); u++) keys.add("fluxecho.unit." + p.layout.unit(u).kind);
        }
        for (int site = 30; site <= 37; site++) keys.add("fluxecho." + SiteFamily.labelKey(site));
        keys.add("fluxecho." + CityOffer.POD);
        for (String lang : new String[] { "en_US", "zh_CN" }) {
            List<String> lines = Files.readAllLines(
                Paths.get("src", "main", "resources", "assets", "fluxecho", "lang", lang + ".lang"),
                StandardCharsets.UTF_8);
            for (String k : keys) {
                boolean found = false;
                for (String l : lines) found |= l.startsWith(k + "=");
                assertTrue(found, lang + " lacks " + k);
            }
        }
    }
}
