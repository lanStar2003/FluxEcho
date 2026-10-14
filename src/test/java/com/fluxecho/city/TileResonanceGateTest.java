package com.fluxecho.city;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fluxecho.campus.Campus;
import com.fluxecho.campus.ModuleSpec;
import com.fluxecho.campus.ModuleSpecs;
import com.fluxecho.frame.Formed;
import com.fluxecho.logic.ArcaneCourtShape;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.ModuleMap;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.ResonanceGateShape;
import com.fluxecho.logic.SiteFamily;
import com.fluxecho.logic.UnitLayout;

/**
 * The Resonance Gate as its tile sees it in the world (0.14.0), for every facing: its blueprint sits where
 * StructureLib's facing math looks for it, the kiosk's console under the controller opens the window, every socket is
 * its slot and every unit its kind, the spire core dissolves and lets players through, a gate the campus builder
 * raised on site 0 has its frame where the plan put the blocks and docks there by its exact controller (its centre
 * lies within the 0.9.2 ring's slot 0, so only that tells it apart); its spec is the gate family's and keeps the
 * campus off the ground it works but not off the road and the pylons; the window's plan shows both storeys, and its
 * run-time names are in both lang files.
 */
class TileResonanceGateTest {

    static final PavilionTileChecks.Pavilion G = new PavilionTileChecks.Pavilion(
        TileResonanceGate::new,
        CityModule.resonanceGate(),
        ResonanceGateShape.BUILD,
        ResonanceGateShape.UNITS,
        ModuleCores.RESONANCE_GATE,
        TileResonanceGate.COLOR,
        ResonanceGateShape.DISSOLVES);

    private static final int X = PavilionTileChecks.X, Y = PavilionTileChecks.Y, Z = PavilionTileChecks.Z;

    @Test
    void theBlueprintMapsCellsAsStructureLibDoes() {
        PavilionTileChecks.blueprintMapsCellsAsStructureLibDoes(G);
    }

    /**
     * The controller stands over the kiosk's console in the road, the console opens the window and its neighbours do
     * not; the tile names its module, colour, core meta, units and slots, and its centre is the foundation's middle
     * (A 31, R 0), one row behind the kiosk.
     */
    @Test
    void theControllerStandsOnTheKiosk() {
        int[] ctrl = G.build.controller();
        assertArrayEquals(new int[] { 15, 2, 7 }, ctrl);
        for (int facing = 2; facing <= 5; facing++) {
            TileCityModule t = G.at(X, Y, Z, facing);
            assertArrayEquals(ctrl, t.local(X, Y, Z));
            int[] k = t.worldOf(15, 1, 7);
            assertArrayEquals(new int[] { X, Y - 1, Z }, k, "the console is right under the controller");
            assertTrue(t.consoleAt(k[0], k[1], k[2]), "the console opens the window");
            int[] beside = t.worldOf(14, 1, 7);
            assertFalse(t.consoleAt(beside[0], beside[1], beside[2]));
            assertEquals(10, t.unitCount());
            assertEquals(
                24,
                t.samples()
                    .getSlots());
            assertEquals(6, t.coreMetaOf(), "fluxecho:module_core meta 6");
            assertEquals(ResonanceGateShape.KEY, t.moduleKey());
            assertEquals(0x5CF2C2, t.moduleColor());
            assertArrayEquals(t.worldOf(15, 0, 8), t.centre(), "the foundation's middle");
        }
    }

    @Test
    void everySocketIsItsSlotAndEveryUnitItsKind() {
        PavilionTileChecks.everySocketIsItsSlotAndEveryUnitItsKind(G);
        TileCityModule t = G.at(X, Y, Z, 4);
        for (int u = 0; u < 10; u++) assertEquals(u < 6 ? "resonator" : "rack", t.unitKind(u), "unit " + u);
        assertArrayEquals(new int[] { 0, 2 }, t.unitSlots(0));
        assertArrayEquals(new int[] { 12, 3 }, t.unitSlots(6));
        int[] core = t.worldOf(15, 9, 8);
        assertEquals(-1, t.unitAt(core[0], core[1], core[2]), "the spire core is no unit");
    }

    /**
     * Formed, the spire core's conduits and seat give way to the rendered crystal spire and let players through (the
     * one standing cell this opens on the chamber's deck: {@code ResonanceGateWalkTest}); everything else stays.
     */
    @Test
    void theSpireCoreDissolvesAndLetsPlayersThrough() {
        TileCityModule t = G.at(X, Y, Z, 3);
        assertEquals(Formed.HIDE | Formed.PASS, t.flags('C'));
        assertEquals(Formed.HIDE | Formed.PASS, t.flags('Z'));
        for (char ch : "KqntdWLGwopcAFh".toCharArray()) assertEquals(0, t.flags(ch), "'" + ch + "'");
    }

    @Test
    void localIsTheInverseOfWorldOf() {
        PavilionTileChecks.localIsTheInverseOfWorldOf(G);
    }

    /**
     * The tile of a gatehouse the builder raised on site 0, for every front of the nexus, has its frame where the plan
     * put every block: the core where the controller is, every unit block of the unit the tile names, every hard block
     * the part its cell asks for; and the nexus's dock finds it by its exact controller and front (the site's
     * controller), while its centre (A 31, R 0) lies within the 0.9.2 ring's slot 0.
     */
    @Test
    void aBuiltGatesFrameIsThePlans() {
        int cx = 40, y0 = 64, cz = -80;
        CampusPlan campus = new CampusPlan(11L);
        int unitCells = 0;
        for (UnitLayout.Unit u : G.layout.units()) unitCells += u.cells.size();
        for (int[] f : PavilionTileChecks.FACINGS) {
            int[] c = G.spec.controller(0, cx, y0, cz, f[0], f[1]);
            TileCityModule t = G.at(c[0], c[1], c[2], c[3]);
            BuildPlan.Plan plan = G.spec.plan(campus, 0, cx, y0, cz, f[0], f[1]);
            int units = 0, cores = 0, hard = 0;
            for (BuildPlan.Step s : plan.steps) {
                if (s.part == Parts.moduleCore(G.meta)) {
                    assertArrayEquals(new int[] { c[0], c[1], c[2] }, new int[] { s.x, s.y, s.z });
                    cores++;
                }
                int[] l = t.local(s.x, s.y, s.z);
                boolean inBox = l[0] >= 0 && l[0] < G.build.width()
                    && l[1] >= 0
                    && l[1] < G.build.height()
                    && l[2] >= 0
                    && l[2] < G.build.depth();
                if (s.kind == BuildPlan.HARD && inBox) {
                    char ch = G.build.cell(l[0], l[1], l[2]);
                    if (ch != ' ' && ch != '-') {
                        assertEquals(G.build.part(l[0], l[1], l[2], ch), s.part, s.toString());
                        hard++;
                    }
                }
                if (s.group == BuildPlan.NO_GROUP) continue;
                assertEquals(s.group, t.unitAt(s.x, s.y, s.z), s.toString());
                units++;
            }
            assertEquals(1, cores);
            assertEquals(unitCells, units);
            assertEquals(2370, hard);
            int[] centre = t.centre();
            assertArrayEquals(new int[] { 31, 0 }, CampusPlan.toLocal(centre[0] - cx, centre[2] - cz, f[0], f[1]));
            assertEquals(y0, centre[1]);
            // the dock's exact test: the spec's controller for the site is the tile's cell and front
            assertArrayEquals(
                new int[] { t.xCoord, t.yCoord, t.zCoord, t.front()
                    .ordinal() },
                c);
            assertArrayEquals(
                new int[] { -1, 0 },
                CampusPlan.toLocal(t.front().offsetX, t.front().offsetZ, f[0], f[1]),
                "facing the nexus");
        }
    }

    /**
     * The spec: the gate family on site 0 alone, 31 x 17 and 18 high, opened by its research, the core of meta 6, on
     * the inner ring (it counts against the ring's room), fitting every base a nexus core may be placed at; its
     * keep-out is the ground its job works ({@link BuildPlan#gateKeepOut}), its protection the same boxes over the
     * same heights, its footprint the gatehouse's envelope; no hall or pavilion standing blocks it, and it blocks
     * none of theirs.
     */
    @Test
    void theSpecIsTheGateFamilys() {
        ModuleSpec spec = G.spec;
        assertEquals(SiteFamily.GATE, spec.family);
        assertEquals(spec.key, spec.research, "the module is its research node");
        assertArrayEquals(new int[] { 0 }, spec.sites);
        assertEquals(31, spec.width);
        assertEquals(17, spec.depth);
        assertEquals(18, spec.height);
        assertEquals(Parts.moduleCore(ModuleCores.RESONANCE_GATE), spec.corePart);
        assertEquals(TileResonanceGate.COLOR, spec.colour);
        assertFalse(spec.axis());
        assertTrue(SiteFamily.ringSite(0), "it takes room on the inner ring");
        assertEquals(2, spec.family.opens, "offered at phase II");
        assertNull(spec.noRoom(5));
        assertNull(spec.noRoom(255 - com.fluxecho.nexus.ItemBlockNexusCore.HEADROOM));
        for (int site = 1; site <= 9; site++) assertFalse(spec.hasSite(site), "site " + site);
        CampusPlan campus = new CampusPlan(3L);
        int cx = -200, y0 = 70, cz = 512;
        ModuleSpec dome = CityModule.ecoDome(), court = CityModule.arcaneCourt();
        for (int[] f : PavilionTileChecks.FACINGS) {
            List<int[]> keep = spec.keepOut(campus, 0, cx, y0, cz, f[0], f[1]);
            BuildPlan.Plan plan = spec.plan(campus, 0, cx, y0, cz, f[0], f[1]);
            assertArrayEquals(
                BuildPlan.gateKeepOut(plan, cx, y0, cz, f[0], f[1])
                    .toArray(new int[0][]),
                keep.toArray(new int[0][]));
            List<int[]> protect = spec.protect(campus, 0, cx, y0, cz, f[0], f[1]);
            assertEquals(keep.size(), protect.size());
            // to the sky ring's deck (0.16.0): two cells under the lowest deck the base layer gives
            int top = com.fluxecho.logic.SkyRing.groundTop(y0);
            for (int i = 0; i < keep.size(); i++) {
                int[] k = keep.get(i), q = protect.get(i);
                assertArrayEquals(new int[] { k[0], k.length == 6 ? k[4] : 0, k[1], k[2], top, k[3] }, q);
            }
            int[] box = spec.footprint(0, cx, cz, f[0], f[1], 0);
            int[] a = CampusPlan.toLocal(box[0] - cx, box[1] - cz, f[0], f[1]),
                b = CampusPlan.toLocal(box[2] - cx, box[3] - cz, f[0], f[1]);
            assertEquals(CampusPlan.GATEHOUSE_FROM, Math.min(a[0], b[0]));
            assertEquals(CampusPlan.GATEHOUSE_TO, Math.max(a[0], b[0]));
            assertEquals(CampusPlan.GATEHOUSE_HALF, Math.max(a[1], b[1]));
            assertEquals(-CampusPlan.GATEHOUSE_HALF, Math.min(a[1], b[1]));
            int[] mine = spec.footprint(0, cx, cz, f[0], f[1], 1);
            for (int hall : CampusPlan.HALL_SITES) {
                for (int[] k : dome.keepOut(campus, hall, cx, y0, cz, f[0], f[1]))
                    assertFalse(PavilionTileChecks.overlap(mine, k), "the dome on " + hall + " blocks the gate");
                int[] hb = dome.footprint(hall, cx, cz, f[0], f[1], 1);
                for (int[] k : keep) assertFalse(PavilionTileChecks.overlap(hb, k), "the gate blocks hall " + hall);
            }
            for (int site : CampusPlan.PAVILION_SITES) {
                for (int[] k : court.keepOut(campus, site, cx, y0, cz, f[0], f[1]))
                    assertFalse(PavilionTileChecks.overlap(mine, k), "a pavilion on " + site + " blocks the gate");
                int[] pb = court.footprint(site, cx, cz, f[0], f[1], 1);
                for (int[] k : keep) assertFalse(PavilionTileChecks.overlap(pb, k), "the gate blocks " + site);
            }
        }
    }

    /**
     * The refresh (the forum plan) of a campus with the gatehouse standing never grades nor paves the ground its job
     * works, yet still lays the gate road's tiles through the passage and raises the four pylons.
     */
    @Test
    void theRefreshKeepsOffTheGatehouseButNotOffTheRoad() {
        CampusPlan campus = new CampusPlan(29L);
        int cx = 1000, y0 = 68, cz = 1000;
        for (int[] f : PavilionTileChecks.FACINGS) {
            List<int[]> keep = G.spec.keepOut(campus, 0, cx, y0, cz, f[0], f[1]);
            Set<Long> whole = new HashSet<>();
            for (int[] b : keep) if (b.length == 4) for (int x = b[0]; x <= b[2]; x++)
                for (int z = b[1]; z <= b[3]; z++) whole.add(PavilionTileChecks.col(x, z));
            BuildPlan.Plan refresh = BuildPlan.forum(campus, cx, y0, cz, f[0], f[1], keep);
            for (BuildPlan.Column c : refresh.clear)
                assertFalse(whole.contains(PavilionTileChecks.col(c.x, c.z)), "graded: " + c.x + "," + c.z);
            int road = 0, pylons = 0;
            for (BuildPlan.Step s : refresh.steps) {
                assertFalse(whole.contains(PavilionTileChecks.col(s.x, s.z)), "paved inside the site: " + s);
                int[] l = CampusPlan.toLocal(s.x - cx, s.z - cz, f[0], f[1]);
                if (s.stage == BuildPlan.E_FLOOR && CampusPlan.inGatePassage(l[0], l[1])) road++;
                if (s.stage == BuildPlan.E_FIXTURE && Math.abs(l[1]) == 5 && (l[0] == 27 || l[0] == 35)) pylons++;
            }
            assertTrue(road > 100, "the road through the passage: " + road);
            assertEquals(4 * (BuildPlan.PYLON_PILLARS + 1), pylons, "the four pylons");
        }
    }

    /**
     * The window's plan: two storeys, the ground halls (the racks, the kiosk's console in the road between the wings)
     * and the upper floor (the resonators in the chamber on the bridge), every unit showing on its storey.
     */
    @Test
    void theWindowsPlanShowsBothStoreys() {
        ModuleMap map = new ModuleMap(
            G.build,
            G.layout,
            G.dissolves,
            CityGui.MAP_MAX_W,
            CityGui.MAP_MAX_H,
            G.make.get()
                .planLevels());
        assertEquals(2, map.storeys());
        assertEquals(1, map.level(0));
        assertEquals(9, map.level(1));
        assertEquals(ModuleMap.Mark.CONSOLE, map.mark(0, 15, 7), "the kiosk's console where the window opens");
        assertTrue(map.w <= CityGui.MAP_MAX_W && map.h <= CityGui.MAP_MAX_H, "the plan fits the window");
        for (int u = 0; u < G.layout.size(); u++) {
            assertEquals(u < 6 ? 1 : 0, map.storeyOf(u), "unit " + u);
            assertFalse(
                map.tiles(u)
                    .isEmpty(),
                "unit " + u + " shows on the plan");
        }
    }

    /**
     * The campus tells the gatehouse built on site 0 from a 0.9.2 module docked on the old ring's slot 0 by its module
     * list ({@link Campus.ModuleState#gateBuilt}): only a BUILT state of a gate-family module recorded on the gate
     * counts, and then the light bridge runs to the gatehouse's door ({@link CampusPlan#GATE_DOOR}) instead of the ring
     * bridge. A gate still building, another module built on site 0, a gate state recorded elsewhere, a pavilion's
     * state, no list and a legacy campus's empty list all leave the ring bridge.
     */
    @Test
    void theCampusTellsTheGatehouseFromARingModule() {
        if (ModuleSpecs.get(ResonanceGateShape.KEY) == null) ModuleSpecs.register(CityModule.resonanceGate());
        if (ModuleSpecs.get(ArcaneCourtShape.KEY) == null) ModuleSpecs.register(CityModule.arcaneCourt());
        String gate = ResonanceGateShape.KEY, research = gate;
        Campus.View v = new Campus.View();
        v.modules = new ArrayList<>();
        v.modules.add(new Campus.ModuleState(gate, research, Campus.ModuleStatus.BUILT, 0));
        assertTrue(v.gateBuilt(0), "the gate built on 0");
        assertEquals(CampusPlan.GATE_DOOR, CampusPlan.siteDoor(0, v.gateBuilt(0)), 1e-12);
        assertFalse(v.pavilionBuilt(0), "the gatehouse is no pavilion");
        List<List<Campus.ModuleState>> others = new ArrayList<>();
        others.add(Collections.singletonList(new Campus.ModuleState(gate, research, Campus.ModuleStatus.BUILDING, 0)));
        others
            .add(Collections.singletonList(new Campus.ModuleState("no_such_module", "", Campus.ModuleStatus.BUILT, 0)));
        others.add(
            Collections.singletonList(
                new Campus.ModuleState(ArcaneCourtShape.KEY, ArcaneCourtShape.KEY, Campus.ModuleStatus.BUILT, 0)));
        for (List<Campus.ModuleState> l : others) {
            Campus.View o = new Campus.View();
            o.modules = l;
            String what = l.get(0).key + " " + l.get(0).status;
            assertFalse(o.gateBuilt(0), what);
            assertFalse(o.pavilionBuilt(0), what);
            assertEquals(-1, CampusPlan.siteDoor(0, o.gateBuilt(0)), 1e-12, "the ring bridge: " + what);
        }
        for (int site : new int[] { 2, 3 }) {
            Campus.View o = new Campus.View();
            o.modules = Collections
                .singletonList(new Campus.ModuleState(gate, research, Campus.ModuleStatus.BUILT, site));
            assertFalse(o.gateBuilt(site), "a gate state recorded on " + site);
            assertFalse(o.gateBuilt(0), "and not on the gate");
        }
        assertFalse(Campus.ModuleState.gateBuilt(null, 0));
        assertFalse(new Campus.View().gateBuilt(0), "a legacy campus lists no modules");
    }

    @Test
    void itsRunTimeKeysAreInBothLangFiles() throws java.io.IOException {
        PavilionTileChecks.itsRunTimeKeysAreInBothLangFiles(G);
        java.util.Properties zh = PavilionTileChecks.lang("zh_CN"), en = PavilionTileChecks.lang("en_US");
        assertEquals("大门", zh.getProperty("fluxecho." + SiteFamily.labelKey(0)));
        assertEquals("Gate", en.getProperty("fluxecho." + SiteFamily.labelKey(0)));
        // the chat line that a projection is up names the site as the windows do (大门 for the gate, "4 号位" for a
        // hall), so it adds no word for a site of its own
        assertFalse(
            zh.getProperty("fluxecho.build.unlocked")
                .contains("号位"));
        assertFalse(
            en.getProperty("fluxecho.build.unlocked")
                .contains("site %s"));
    }
}
