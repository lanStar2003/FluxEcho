package com.fluxecho.city;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraftforge.common.util.ForgeDirection;

import com.fluxecho.campus.ModuleSpec;
import com.fluxecho.campus.ModuleSpecs;
import com.fluxecho.frame.Formed;
import com.fluxecho.logic.Blueprint;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.CampusBand;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.CityBuild;
import com.fluxecho.logic.CityOffer;
import com.fluxecho.logic.ModuleMap;
import com.fluxecho.logic.OuterRing;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.SiteFamily;
import com.fluxecho.logic.UnitLayout;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;

/**
 * What the tile test of every building on the outer diagonals asks of it in the world (0.15.0), as
 * {@link PavilionTileChecks} asks it of a pavilion: its blueprint sits where StructureLib's facing math looks for it,
 * the console under the controller opens the window, every socket is its slot, {@code local} undoes {@code worldOf}, a
 * building the campus builder raised on an outer diagonal has its frame where the plan put the blocks and its
 * controller exactly where the nexus's dock looks for it, its spec keeps the campus jobs off its apron and allée (never
 * an inner site), the outer establish keeps off it once it stands, it is offered only at phase III on laid ground, its
 * window's plan fits, and the names the game builds for it at run time are in both lang files.
 */
final class OuterDiagTileChecks {

    static final int X = -1207, Y = 71, Z = 433;
    static final int[][] FACINGS = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };

    /** One building's data the checks read. */
    static final class Building {

        final Supplier<? extends TileCityModule> make;
        final ModuleSpec spec;
        final CityBuild build;
        final UnitLayout layout;
        final int meta, colour;
        final String dissolves;

        Building(Supplier<? extends TileCityModule> make, ModuleSpec spec, CityBuild build, UnitLayout layout, int meta,
            int colour, String dissolves) {
            this.make = make;
            this.spec = spec;
            this.build = build;
            this.layout = layout;
            this.meta = meta;
            this.colour = colour;
            this.dissolves = dissolves;
        }

        TileCityModule at(int x, int y, int z, int facing) {
            TileCityModule t = make.get();
            t.xCoord = x;
            t.yCoord = y;
            t.zCoord = z;
            t.place((UUID) null, null, facing);
            return t;
        }
    }

    private OuterDiagTileChecks() {}

    static void blueprintMapsCellsAsStructureLibDoes(Building p) {
        Blueprint b = p.build.blueprint();
        int[] out = new int[3];
        for (int facing = 2; facing <= 5; facing++) {
            ForgeDirection f = ForgeDirection.getOrientation(facing);
            ExtendedFacing ef = ExtendedFacing.of(f);
            for (Blueprint.Cell c : b.cells()) {
                ef.getWorldOffset(new int[] { c.a - b.ctrlA, c.b - b.ctrlB, c.c - b.ctrlC }, out);
                assertArrayEquals(
                    new int[] { X + out[0], Y + out[1], Z + out[2] },
                    b.world(c.a, c.b, c.c, X, Y, Z, f.offsetX, f.offsetZ),
                    "facing " + facing + " cell " + c.a + "," + c.b + "," + c.c);
            }
        }
    }

    /**
     * The controller stands over its console, which opens the window (its neighbours do not); the tile names its
     * module, colour, core meta, units and slots, and its centre is the foundation's at the octagon's middle.
     */
    static void theControllerStandsOnItsConsole(Building p) {
        int[] ctrl = p.build.controller();
        for (int facing = 2; facing <= 5; facing++) {
            TileCityModule t = p.at(X, Y, Z, facing);
            assertArrayEquals(ctrl, t.local(X, Y, Z));
            int[] k = t.worldOf(ctrl[0], ctrl[1] - 1, ctrl[2]);
            assertArrayEquals(new int[] { X, Y - 1, Z }, k, "the console is right under the controller");
            assertTrue(t.consoleAt(k[0], k[1], k[2]), "the console opens the window");
            int[] beside = t.worldOf(ctrl[0] - 1, ctrl[1] - 1, ctrl[2]);
            assertFalse(t.consoleAt(beside[0], beside[1], beside[2]));
            assertEquals(p.layout.size(), t.unitCount());
            assertEquals(
                p.layout.slots(),
                t.samples()
                    .getSlots());
            assertEquals(p.meta, t.coreMetaOf());
            assertEquals(p.spec.key, t.moduleKey());
            assertEquals(p.colour, t.moduleColor());
            assertArrayEquals(t.worldOf(15, 0, 15), t.centre(), "the octagon's centre");
        }
    }

    /**
     * Every socket is its own slot (a slot without a socket shows nowhere in particular), every unit its kind, cells
     * and slots; the controller is no unit.
     */
    static void everySocketIsItsSlotAndEveryUnitItsKind(Building p) {
        UnitLayout layout = p.layout;
        for (int facing = 2; facing <= 5; facing++) {
            TileCityModule t = p.at(X, Y, Z, facing);
            Set<Long> sockets = new HashSet<>();
            for (int s = 0; s < layout.slots(); s++) {
                int[] c = t.slotCell(s);
                if (layout.socketOf(s) == null) {
                    assertNull(c, "slot " + s + " shows over its whole unit");
                    continue;
                }
                assertTrue(sockets.add(Formed.key(c[0], c[1], c[2])), "slot " + s + " shares its socket");
                assertEquals(s, t.slotAt(c[0], c[1], c[2]), "facing " + facing + " slot " + s);
                assertEquals(layout.unitOfSlot(s), t.unitAt(c[0], c[1], c[2]));
                assertFalse(t.consoleAt(c[0], c[1], c[2]));
                int[] l = t.local(c[0], c[1], c[2]);
                assertEquals('o', p.build.cell(l[0], l[1], l[2]));
            }
            for (int u = 0; u < t.unitCount(); u++) {
                UnitLayout.Unit want = layout.unit(u);
                assertEquals(want.kind, t.unitKind(u), "unit " + u);
                assertArrayEquals(new int[] { want.firstSlot, want.slots }, t.unitSlots(u), "unit " + u);
                List<int[]> cells = t.unitCells(u);
                assertEquals(want.cells.size(), cells.size());
                for (int[] c : cells) {
                    assertEquals(u, t.unitAt(c[0], c[1], c[2]));
                    assertFalse(t.consoleAt(c[0], c[1], c[2]), "a unit's console stand is the unit's");
                }
            }
            assertEquals("", t.unitKind(t.unitCount()));
            assertEquals(-1, t.unitAt(X, Y, Z), "the controller is no unit");
            assertEquals(-1, t.slotAt(X, Y, Z));
            assertNull(t.slotCell(layout.slots()));
        }
    }

    /** Formed, the dissolving characters give way to the renderer and let players through; nothing else does. */
    static void itsDissolvingCellsLetPlayersThrough(Building p, String others) {
        TileCityModule t = p.at(X, Y, Z, 2);
        for (char ch : p.dissolves.toCharArray()) assertEquals(Formed.HIDE | Formed.PASS, t.flags(ch), "'" + ch + "'");
        for (char ch : others.toCharArray()) assertEquals(0, t.flags(ch), "'" + ch + "'");
    }

    static void localIsTheInverseOfWorldOf(Building p) {
        for (int facing = 2; facing <= 5; facing++) {
            TileCityModule t = p.at(X, Y, Z, facing);
            for (int x = -3; x < 34; x += 3)
                for (int y = -1; y < p.build.height() + 2; y += 2) for (int z = -3; z < 34; z += 4) {
                    int[] w = t.worldOf(x, y, z);
                    assertArrayEquals(new int[] { x, y, z }, t.local(w[0], w[1], w[2]));
                }
        }
    }

    /**
     * The tile of a building the builder raised on any of its outer diagonals, for every front of the nexus, has its
     * frame where the plan put every block: the core where the controller is, every unit block of the unit the tile
     * names, every hard block the part its cell asks for, its centre on the diagonal's; and its controller cell and
     * front are exactly the spec's for the site, which is how the nexus docks it (by its exact controller).
     */
    static void aBuiltModulesFrameIsThePlans(Building p) {
        int cx = 40, y0 = 64, cz = -80;
        CampusPlan campus = new CampusPlan(11L);
        int unitCells = 0;
        for (UnitLayout.Unit u : p.layout.units()) unitCells += u.cells.size();
        for (int[] f : FACINGS) for (int site : p.spec.sites) {
            int[] c = p.spec.controller(site, cx, y0, cz, f[0], f[1]);
            TileCityModule t = p.at(c[0], c[1], c[2], c[3]);
            BuildPlan.Plan plan = p.spec.plan(campus, site, cx, y0, cz, f[0], f[1]);
            int units = 0, cores = 0, hard = 0;
            for (BuildPlan.Step s : plan.steps) {
                if (s.part == Parts.moduleCore(p.meta)) {
                    assertArrayEquals(new int[] { c[0], c[1], c[2] }, new int[] { s.x, s.y, s.z });
                    cores++;
                }
                int[] l = t.local(s.x, s.y, s.z);
                boolean inBox = l[0] >= 0 && l[0] < p.build.width()
                    && l[1] >= 0
                    && l[1] < p.build.height()
                    && l[2] >= 0
                    && l[2] < p.build.depth();
                if (s.kind == BuildPlan.HARD && inBox) {
                    char ch = p.build.cell(l[0], l[1], l[2]);
                    if (ch != ' ' && ch != '-') {
                        assertEquals(p.build.part(l[0], l[1], l[2], ch), s.part, "site " + site + " " + s);
                        hard++;
                    }
                }
                if (s.group == BuildPlan.NO_GROUP) continue;
                assertEquals(s.group, t.unitAt(s.x, s.y, s.z), "site " + site + " " + s);
                units++;
            }
            assertEquals(1, cores);
            assertEquals(unitCells, units);
            assertTrue(hard > 2000, "the building's blocks: " + hard);
            int[] centre = t.centre();
            int[] local = CampusPlan.toLocal(centre[0] - cx, centre[2] - cz, f[0], f[1]);
            assertArrayEquals(OuterRing.diagCentre(site), local);
            assertEquals(y0, centre[1]);
            assertEquals(site, OuterRing.diagSiteAt(local[0], local[1]));
            assertArrayEquals(
                OuterRing.diagFront(site),
                CampusPlan.toLocal(t.front().offsetX, t.front().offsetZ, f[0], f[1]));
            assertEquals(
                c[3],
                t.front()
                    .ordinal(),
                "the dock finds it by its exact controller and front");
        }
    }

    /**
     * The spec: a building of the outer diagonals in the order it prefers them, opened by its research, the core of its
     * meta; its keep-out is its site row by row (the apron and the allée, {@link OuterRing#diagSiteCells}), which every
     * block and every cleared column of its job lies in and which no inner site's footprint meets; its protection is
     * the same boxes from bedrock to the sky; it fits every base the band allows.
     */
    static void theSpecKeepsTheCampusOffItsSite(Building p, int[] sites) {
        ModuleSpec spec = p.spec;
        assertEquals(SiteFamily.OUTER_DIAG, spec.family);
        assertEquals(spec.key, spec.research, "the module is its research node");
        assertArrayEquals(sites, spec.sites);
        assertEquals(31, spec.width);
        assertEquals(31, spec.depth);
        assertEquals(p.build.height(), spec.height);
        assertEquals(Parts.moduleCore(p.meta), spec.corePart);
        assertEquals(p.colour, spec.colour);
        assertFalse(spec.axis());
        for (int y0 = CampusBand.LOWEST; y0 <= CampusBand.HIGHEST; y0++) assertNull(spec.noRoom(y0), "Y0 " + y0);
        for (int site = 0; site < 70; site++)
            assertEquals(SiteFamily.of(site) == SiteFamily.OUTER_DIAG, spec.hasSite(site), "site " + site);
        CampusPlan campus = new CampusPlan(3L);
        int cx = -200, y0 = 70, cz = 512;
        List<ModuleSpec> inner = new ArrayList<>();
        Collections.addAll(
            inner,
            CityModule.ecoDome(),
            CityModule.arcaneCourt(),
            CityModule.resonanceGate(),
            CityModule.dualFoilVault());
        for (int[] f : FACINGS) for (int site : spec.sites) {
            List<int[]> keep = spec.keepOut(campus, site, cx, y0, cz, f[0], f[1]);
            Set<Long> cols = new HashSet<>();
            for (int[] b : keep) {
                assertEquals(4, b.length, "whole columns");
                for (int x = b[0]; x <= b[2]; x++) for (int z = b[1]; z <= b[3]; z++)
                    assertTrue(cols.add(PavilionTileChecks.col(x, z)), "keep-out boxes overlap at " + x + "," + z);
            }
            Set<Long> want = new HashSet<>();
            for (long k : OuterRing.diagSiteCells(site)) {
                int[] d = CampusPlan.toWorld(CampusPlan.keyA(k), CampusPlan.keyR(k), f[0], f[1]);
                want.add(PavilionTileChecks.col(cx + d[0], cz + d[1]));
            }
            assertEquals(want, cols, "the boxes are the site's cells");
            BuildPlan.Plan plan = spec.plan(campus, site, cx, y0, cz, f[0], f[1]);
            assertEquals(BuildPlan.moduleKey(spec.key, site), plan.key);
            for (BuildPlan.Step s : plan.steps)
                assertTrue(cols.contains(PavilionTileChecks.col(s.x, s.z)), "outside the keep-out: " + s);
            for (BuildPlan.Column c : plan.clear)
                assertTrue(cols.contains(PavilionTileChecks.col(c.x, c.z)), "cleared outside: " + c.x + "," + c.z);
            for (ModuleSpec in : inner) for (int s : in.sites) {
                int[] foot = in.footprint(s, cx, cz, f[0], f[1], 1);
                for (int[] k : keep) assertFalse(CityOffer.overlap(foot, k), spec.key + " on " + site + " / " + s);
                for (int[] k : in.keepOut(campus, s, cx, y0, cz, f[0], f[1])) assertFalse(
                    CityOffer.overlap(spec.footprint(site, cx, cz, f[0], f[1], 1), k),
                    in.key + " on " + s + " blocks " + site);
            }
            List<int[]> protect = spec.protect(campus, site, cx, y0, cz, f[0], f[1]);
            assertEquals(keep.size(), protect.size());
            for (int i = 0; i < keep.size(); i++) {
                int[] k = keep.get(i), q = protect.get(i);
                assertArrayEquals(new int[] { k[0], 0, k[1], k[2], 255, k[3] }, q);
            }
        }
    }

    /**
     * Offered only from phase III on the outer establish's ground ({@link ModuleSpec#candidates}): its preference list
     * then; none at phase II or on ground not laid.
     */
    static void itIsOfferedOnTheOuterGroundAtPhaseThree(Building p) {
        assertArrayEquals(p.spec.sites, p.spec.candidates(3, g -> SiteFamily.OUTER_GROUND.equals(g)));
        assertArrayEquals(p.spec.sites, p.spec.candidates(5, g -> true));
        assertEquals(0, p.spec.candidates(2, g -> true).length, "phase II has no outer sites");
        assertEquals(0, p.spec.candidates(3, g -> false).length, "not before the outer ring is laid");
    }

    /**
     * The outer establish of a campus with the building standing (its keep-out boxes, as the job reads them at Start)
     * never grades nor paves its site, on every site and front.
     */
    static void theOuterEstablishKeepsOffAStandingModule(Building p) {
        CampusPlan campus = new CampusPlan(29L);
        int cx = 1000, y0 = 68, cz = 1000;
        for (int[] f : new int[][] { FACINGS[0], FACINGS[2] }) for (int site : p.spec.sites) {
            List<int[]> keep = p.spec.keepOut(campus, site, cx, y0, cz, f[0], f[1]);
            Set<Long> cols = new HashSet<>();
            for (int[] b : keep) for (int x = b[0]; x <= b[2]; x++)
                for (int z = b[1]; z <= b[3]; z++) cols.add(PavilionTileChecks.col(x, z));
            BuildPlan.Plan outer = BuildPlan
                .outer(campus, cx, y0, cz, f[0], f[1], keep, Collections.<int[]>emptyList());
            for (BuildPlan.Column c : outer.clear)
                assertFalse(cols.contains(PavilionTileChecks.col(c.x, c.z)), "graded on the site: " + c.x + "," + c.z);
            for (BuildPlan.Step s : outer.steps)
                assertFalse(cols.contains(PavilionTileChecks.col(s.x, s.z)), "paved on the site: " + s);
            BuildPlan.Plan refresh = BuildPlan.forum(campus, cx, y0, cz, f[0], f[1], keep);
            for (BuildPlan.Step s : refresh.steps)
                assertFalse(cols.contains(PavilionTileChecks.col(s.x, s.z)), "the forum on the site: " + s);
        }
    }

    /**
     * The module window's plan, made as the window makes it (with the levels its tile shows for the building alone):
     * one storey the units stand on, every unit on it and showing, the console under the controller marked, the
     * levels after it holding no units, and the plan fits the window; returns the map for the building's own checks.
     */
    static ModuleMap theWindowsPlanFits(Building p) {
        TileCityModule tile = p.make.get();
        int[] levels = tile.planLevels();
        // where the kind lets its dissolving cells give way, as the window's own plan takes it (0.16.0)
        ModuleMap map = new ModuleMap(
            p.build,
            p.layout,
            p.dissolves,
            tile::dissolvesAt,
            CityGui.MAP_MAX_W,
            CityGui.MAP_MAX_H,
            levels);
        assertEquals(1 + levels.length, map.storeys());
        assertEquals(1, map.level(0));
        assertFalse(map.overview(0));
        for (int i = 0; i < levels.length; i++) {
            assertEquals(levels[i], map.level(1 + i));
            assertTrue(map.overview(1 + i), "level " + levels[i] + " is shown for the building alone");
            assertTrue(
                map.units(1 + i)
                    .isEmpty());
        }
        int[] c = p.build.controller();
        assertEquals(ModuleMap.Mark.CONSOLE, map.mark(0, c[0], c[2]), "the console where the window opens");
        assertTrue(map.w <= CityGui.MAP_MAX_W && map.h <= CityGui.MAP_MAX_H, "the plan fits the window");
        for (int u = 0; u < p.layout.size(); u++) {
            assertEquals(0, map.storeyOf(u));
            assertFalse(
                map.tiles(u)
                    .isEmpty(),
                "unit " + u + " shows on the plan");
        }
        return map;
    }

    /**
     * Its core is manifested at the nexus once its research is done (CITY.md Appendix B, M12 §5, §6): phase III's
     * materials, IV circuits, phase alloy and memory filament, and one themed vanilla item, in five kinds so the
     * table's sixth input slot takes a second record's echo crystals; the resonant glass is its research's. The tables'
     * own materials it takes (resonant glass, memory filament) open with phase II, which its research needs first.
     */
    static void itsCoreIsManifestedWithPhaseThreesMaterials(Building p, String themed) {
        CityModule.postInit();
        com.fluxecho.nexus.Manifests.Recipe r = com.fluxecho.nexus.Manifests.get(p.spec.key + "_core");
        org.junit.jupiter.api.Assertions.assertNotNull(r, p.spec.key + "'s core is manifested");
        assertEquals(p.spec.research, r.research);
        assertFalse(r.record);
        Set<String> names = new HashSet<>();
        for (com.fluxecho.logic.ResearchTree.Cost c : r.inputs) names.add((c.ore ? "ore:" : "item:") + c.name);
        for (String want : new String[] { "ore:" + com.fluxecho.matter.MatterModule.MEMORY_FILAMENT,
            "ore:" + com.fluxecho.matter.MatterModule.PHASE_ALLOY, "ore:circuitIV", "item:" + themed })
            assertTrue(names.contains(want), p.spec.key + " takes " + want);
        assertTrue(r.inputs.size() < com.fluxecho.nexus.TileNexus.INPUTS, "a slot left for a second record's crystals");
        com.fluxecho.logic.ResearchTree.Node node = com.fluxecho.logic.ResearchTree.get(r.research);
        boolean glass = false;
        for (com.fluxecho.logic.ResearchTree.Cost c : node.costs)
            glass |= c.ore && com.fluxecho.matter.MatterModule.RESONANT_GLASS.equals(c.name);
        assertTrue(glass, p.spec.key + "'s research takes the resonant glass");
        assertEquals(3, node.phase, "a phase III research");
        Set<String> needs = new HashSet<>();
        java.util.ArrayDeque<String> todo = new java.util.ArrayDeque<>(node.requires);
        while (!todo.isEmpty()) {
            String id = todo.pop();
            if (needs.add(id)) todo.addAll(com.fluxecho.logic.ResearchTree.get(id).requires);
        }
        assertTrue(needs.contains(com.fluxecho.research.Research.OUTER_RING), "after the outer ring");
        assertTrue(needs.contains(com.fluxecho.research.Research.PHASE_2), "after the research that opens filament");
    }

    /**
     * The names the game builds at run time for the building (its stages on the build page, its module, core, research,
     * unit kinds and the outer diagonals it stands on) are in both lang files.
     */
    static void itsRunTimeKeysAreInBothLangFiles(Building p) throws IOException {
        String key = p.spec.key;
        if (ModuleSpecs.get(key) == null) ModuleSpecs.register(p.spec);
        List<String> keys = new ArrayList<>();
        for (int site : p.spec.sites) {
            String job = BuildPlan.moduleKey(key, site);
            for (int s = 0; s <= BuildPlan.M_COMMISSION + 1; s++) {
                String k = com.fluxecho.nexus.NexusGui.stageKey(job, s);
                // the Sophon Array's stages run in its own order and have their own names (0.15.0)
                String family = com.fluxecho.logic.SophonArrayShape.KEY.equals(key) ? "s" : "c";
                assertEquals("build.stage." + family + Math.min(s, BuildPlan.M_COMMISSION), k);
                keys.add("fluxecho." + k);
            }
            keys.add("fluxecho." + SiteFamily.labelKey(site));
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
