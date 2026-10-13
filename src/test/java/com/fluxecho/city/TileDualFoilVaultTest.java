package com.fluxecho.city;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import com.fluxecho.campus.ModuleSpec;
import com.fluxecho.frame.Formed;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.DualFoilVaultShape;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.ModuleMap;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.SiteFamily;
import com.fluxecho.logic.UnitLayout;

/**
 * The Dual-foil Vault as its tile sees it in the world (0.14.0), for every facing: its blueprint sits where
 * StructureLib's facing math looks for it, both console stands (the portal's under the controller and the desk's under
 * the foil) open the window, every canvas and the foil is its unit with its one slot (shown over its whole field, no
 * socket; a sneaking empty hand takes its item anywhere on it), nothing dissolves, a vault the campus builder raised
 * on a hall site has its frame where the plan put the blocks and its centre where the dock looks; its spec is a hall's
 * on sites 6, 2, 4; the window's plan shows the three tiers and the foil, and its run-time names are in both lang
 * files.
 */
class TileDualFoilVaultTest {

    static final PavilionTileChecks.Pavilion V = new PavilionTileChecks.Pavilion(
        TileDualFoilVault::new,
        CityModule.dualFoilVault(),
        DualFoilVaultShape.BUILD,
        DualFoilVaultShape.UNITS,
        ModuleCores.DUAL_FOIL_VAULT,
        TileDualFoilVault.COLOR,
        DualFoilVaultShape.DISSOLVES);

    private static final int X = PavilionTileChecks.X, Y = PavilionTileChecks.Y, Z = PavilionTileChecks.Z;
    private static final Item ITEM = new Item();

    @Test
    void theBlueprintMapsCellsAsStructureLibDoes() {
        PavilionTileChecks.blueprintMapsCellsAsStructureLibDoes(V);
    }

    /**
     * The controller stands in the trumeau over its console stand, which opens the window, as does the desk's under
     * the foil; their neighbours do not. The tile names its module, colour, core meta, units and slots, and its centre
     * is the foundation's middle.
     */
    @Test
    void theControllerAndBothConsoles() {
        assertArrayEquals(new int[] { 13, 2, 0 }, V.build.controller());
        for (int facing = 2; facing <= 5; facing++) {
            TileCityModule t = V.at(X, Y, Z, facing);
            assertArrayEquals(new int[] { 13, 2, 0 }, t.local(X, Y, Z));
            int[] k = t.worldOf(13, 1, 0);
            assertArrayEquals(new int[] { X, Y - 1, Z }, k, "the stand is right under the controller");
            assertTrue(t.consoleAt(k[0], k[1], k[2]));
            int[] desk = t.worldOf(13, 1, 30);
            assertTrue(t.consoleAt(desk[0], desk[1], desk[2]), "the desk under the foil opens the window too");
            for (int[] n : new int[][] { { 12, 1, 0 }, { 10, 1, 30 }, { 13, 2, 30 } }) {
                int[] w = t.worldOf(n[0], n[1], n[2]);
                assertFalse(t.consoleAt(w[0], w[1], w[2]), n[0] + "," + n[1] + "," + n[2]);
            }
            assertEquals(43, t.unitCount());
            assertEquals(
                43,
                t.samples()
                    .getSlots());
            assertEquals(1, t.coreMetaOf(), "fluxecho:module_core meta 1");
            assertEquals(DualFoilVaultShape.KEY, t.moduleKey());
            assertEquals(0xF2EBDD, t.moduleColor(), "parchment");
            assertArrayEquals(t.worldOf(13, 0, 17), t.centre(), "the foundation's middle");
        }
    }

    /**
     * Every canvas and the foil is its own unit of its kind with one slot and no socket, every block of it names it,
     * and a block of no unit (a post between two canvases, the niche, the controller) names none.
     */
    @Test
    void everyCanvasAndTheFoilIsItsUnit() {
        UnitLayout layout = V.layout;
        for (int facing = 2; facing <= 5; facing++) {
            TileCityModule t = V.at(X, Y, Z, facing);
            Set<Long> seen = new HashSet<>();
            for (int u = 0; u < t.unitCount(); u++) {
                assertEquals(u < 42 ? "canvas" : "foil", t.unitKind(u), "unit " + u);
                assertArrayEquals(new int[] { u, 1 }, t.unitSlots(u));
                assertNull(t.slotCell(u), "no socket: the slot shows over the whole field");
                List<int[]> cells = t.unitCells(u);
                assertEquals(layout.unit(u).cells.size(), cells.size());
                for (int[] c : cells) {
                    assertTrue(seen.add(Formed.key(c[0], c[1], c[2])), "a block of two units");
                    assertEquals(u, t.unitAt(c[0], c[1], c[2]));
                    assertEquals(-1, t.slotAt(c[0], c[1], c[2]));
                }
            }
            assertEquals("GL-1", t.unitName(0));
            assertEquals("UR-7", t.unitName(41));
            assertEquals("foil", t.unitName(42));
            for (int[] n : new int[][] { { 0, 1, 4 }, { 26, 9, 32 }, { 13, 4, 34 }, { 13, 2, 0 }, { 13, 15, 0 } }) {
                int[] w = t.worldOf(n[0], n[1], n[2]);
                assertEquals(-1, t.unitAt(w[0], w[1], w[2]), n[0] + "," + n[1] + "," + n[2]);
            }
            assertEquals("", t.unitKind(43));
            assertNull(t.slotCell(43));
        }
    }

    /**
     * A sneaking empty hand takes a canvas's item back wherever on the canvas it clicks (its one slot shows over the
     * whole field), and nothing from an empty canvas; the foil the same.
     */
    @Test
    void aSneakingHandTakesACanvassItemAnywhereOnIt() {
        TileCityModule t = V.at(X, Y, Z, 3);
        int canvas = 9, foil = 42;
        for (int[] c : t.unitCells(canvas)) assertEquals(-1, t.takeSlot(canvas, c[0], c[1], c[2]), "empty");
        t.samples()
            .setStackInSlot(canvas, new ItemStack(ITEM, 1, 0));
        t.samples()
            .setStackInSlot(foil, new ItemStack(ITEM, 1, 0));
        for (int[] c : t.unitCells(canvas)) assertEquals(canvas, t.takeSlot(canvas, c[0], c[1], c[2]));
        for (int[] c : t.unitCells(foil)) assertEquals(foil, t.takeSlot(foil, c[0], c[1], c[2]));
        assertEquals(-1, t.takeSlot(10, X, Y, Z), "another, empty canvas");
        assertEquals(-1, t.takeSlot(43, X, Y, Z), "no such unit");
        assertEquals(-1, t.takeSlot(-1, X, Y, Z));
    }

    /** Nothing dissolves: every block stays drawn and solid (the paintings are drawn over the canvases). */
    @Test
    void nothingDissolves() {
        TileCityModule t = V.at(X, Y, Z, 4);
        for (char ch : "FWLwnGQ$qpcrtdeKAh~-".toCharArray()) assertEquals(0, t.flags(ch), "'" + ch + "'");
    }

    @Test
    void localIsTheInverseOfWorldOf() {
        for (int facing = 2; facing <= 5; facing++) {
            TileCityModule t = V.at(X, Y, Z, facing);
            for (int x = -2; x < 29; x += 3)
                for (int y = -1; y < DualFoilVaultShape.HEIGHT + 2; y += 2) for (int z = -3; z < 37; z += 4) {
                    int[] w = t.worldOf(x, y, z);
                    assertArrayEquals(new int[] { x, y, z }, t.local(w[0], w[1], w[2]));
                }
        }
    }

    /**
     * The tile of a vault the builder raised on any of its hall sites, for every front of the nexus, has its frame
     * where the plan put every block: the core where the controller is, every unit block of the unit the tile names,
     * every hard block the part its cell asks for, and its centre where the dock looks for a hall of its depth.
     */
    @Test
    void aBuiltVaultsFrameIsThePlans() {
        int cx = 40, y0 = 64, cz = -80;
        CampusPlan campus = new CampusPlan(13L);
        for (int[] f : PavilionTileChecks.FACINGS) for (int site : DualFoilVaultShape.SITES) {
            int[] c = V.spec.controller(site, cx, y0, cz, f[0], f[1]);
            TileCityModule t = V.at(c[0], c[1], c[2], c[3]);
            BuildPlan.Plan plan = V.spec.plan(campus, site, cx, y0, cz, f[0], f[1]);
            int units = 0, cores = 0, hard = 0;
            for (BuildPlan.Step s : plan.steps) {
                if (s.part == Parts.moduleCore(V.meta)) {
                    assertArrayEquals(new int[] { c[0], c[1], c[2] }, new int[] { s.x, s.y, s.z });
                    cores++;
                }
                int[] l = t.local(s.x, s.y, s.z);
                boolean inBox = l[0] >= 0 && l[0] < 27 && l[1] >= 0 && l[1] < 27 && l[2] >= 0 && l[2] < 35;
                if (s.kind == BuildPlan.HARD && inBox) {
                    char ch = V.build.cell(l[0], l[1], l[2]);
                    assertEquals(V.build.part(l[0], l[1], l[2], ch), s.part, "site " + site + " " + s);
                    hard++;
                }
                if (s.group == BuildPlan.NO_GROUP) continue;
                assertEquals(s.group, t.unitAt(s.x, s.y, s.z), "site " + site + " " + s);
                units++;
            }
            assertEquals(1, cores);
            assertEquals(42 * 18 + 131, units);
            assertEquals(4644, hard);
            int[] centre = t.centre();
            assertArrayEquals(
                CampusPlan.moduleCentre(site, DualFoilVaultShape.DEPTH),
                CampusPlan.toLocal(centre[0] - cx, centre[2] - cz, f[0], f[1]));
            assertEquals(y0, centre[1]);
        }
    }

    /**
     * The spec: the hall family on sites 6, 2, 4 in that order (CITY.md §2.2), 27 x 35 and 27 high (it fits every
     * base a nexus core may be placed at), opened by its research, the core of meta 1, on the inner ring; its keep-out
     * is its site box, which meets no other hall's, no pavilion's and not the gatehouse's.
     */
    @Test
    void theSpecIsAHallsOnSixTwoFour() {
        ModuleSpec spec = V.spec;
        assertEquals(SiteFamily.HALL, spec.family);
        assertEquals(spec.key, spec.research, "the module is its research node");
        assertArrayEquals(new int[] { 6, 2, 4 }, spec.sites);
        assertEquals(27, spec.width);
        assertEquals(35, spec.depth);
        assertEquals(27, spec.height);
        assertEquals(Parts.moduleCore(ModuleCores.DUAL_FOIL_VAULT), spec.corePart);
        assertEquals(TileDualFoilVault.COLOR, spec.colour);
        assertFalse(spec.axis());
        for (int site : spec.sites) assertTrue(SiteFamily.ringSite(site), "it takes room on the inner ring");
        assertNull(spec.noRoom(5));
        assertNull(spec.noRoom(255 - com.fluxecho.nexus.ItemBlockNexusCore.HEADROOM));
        for (int site : new int[] { 0, 1, 3, 5, 7, 8, 9 }) assertFalse(spec.hasSite(site), "site " + site);
        CampusPlan campus = new CampusPlan(5L);
        int cx = 300, y0 = 66, cz = -40;
        ModuleSpec court = CityModule.arcaneCourt(), gate = CityModule.resonanceGate();
        for (int[] f : PavilionTileChecks.FACINGS) for (int site : spec.sites) {
            List<int[]> keep = spec.keepOut(campus, site, cx, y0, cz, f[0], f[1]);
            assertEquals(1, keep.size(), "a hall's site box");
            assertArrayEquals(spec.siteBox(campus, site, cx, y0, cz, f[0], f[1]), keep.get(0));
            BuildPlan.Plan plan = spec.plan(campus, site, cx, y0, cz, f[0], f[1]);
            int[] box = keep.get(0);
            for (BuildPlan.Step s : plan.steps)
                assertTrue(s.x >= box[0] && s.x <= box[2] && s.z >= box[1] && s.z <= box[3], "outside its site: " + s);
            int[] mine = spec.footprint(site, cx, cz, f[0], f[1], 1);
            for (int p : CampusPlan.PAVILION_SITES) {
                for (int[] k : court.keepOut(campus, p, cx, y0, cz, f[0], f[1]))
                    assertFalse(PavilionTileChecks.overlap(mine, k), "a pavilion on " + p + " blocks " + site);
                assertFalse(
                    PavilionTileChecks.overlap(court.footprint(p, cx, cz, f[0], f[1], 1), box),
                    "the vault on " + site + " blocks " + p);
            }
            for (int[] k : gate.keepOut(campus, 0, cx, y0, cz, f[0], f[1]))
                assertFalse(PavilionTileChecks.overlap(mine, k), "the gatehouse blocks " + site);
            assertFalse(PavilionTileChecks.overlap(gate.footprint(0, cx, cz, f[0], f[1], 1), box));
        }
    }

    /**
     * The window's plans: the ground floor (both console stands, the ground tier, and the foil, hung over its frame on
     * the back wall and used from the nave's floor), the first catwalk's (the middle tier) and the second's (the upper
     * tier); every unit shows on its plan, and they fit.
     */
    @Test
    void theWindowsPlanShowsTheTiersAndTheFoil() {
        ModuleMap map = new ModuleMap(
            V.build,
            V.layout,
            V.dissolves,
            CityGui.MAP_MAX_W,
            CityGui.MAP_MAX_H,
            V.make.get()
                .planLevels());
        assertEquals(3, map.storeys());
        assertArrayEquals(new int[] { 1, 9, 17 }, new int[] { map.level(0), map.level(1), map.level(2) });
        assertEquals(ModuleMap.Mark.CONSOLE, map.mark(0, 13, 0), "the portal's console stand");
        assertEquals(ModuleMap.Mark.CONSOLE, map.mark(0, 13, 30), "the desk");
        assertEquals(ModuleMap.Mark.FLOOR, map.mark(1, 2, 20), "the first catwalk");
        assertEquals(ModuleMap.Mark.FLOOR, map.mark(2, 13, 32), "the cross walk");
        assertTrue(map.w <= CityGui.MAP_MAX_W && map.h <= CityGui.MAP_MAX_H, "the plan fits the window");
        for (int u = 0; u < V.layout.size(); u++) {
            int want = u == 42 || u < 14 ? 0 : u < 28 ? 1 : 2;
            assertEquals(want, map.storeyOf(u), "unit " + u);
            assertFalse(
                map.tiles(u)
                    .isEmpty(),
                "unit " + u + " shows on the plan");
        }
        // the foil along the back wall, hung over the floor it is used from: one band across its whole width x 7..19,
        // the columns over the gate niche's middle included (its lowest row, cut in two by the niche, once showed as
        // two pieces, and a click on its middle picked nothing), the nearest unit to a click anywhere on it
        assertEquals(
            13,
            map.tiles(42)
                .size());
        for (int x = 7; x <= 19; x++) {
            int[] t = map.tile(x, 34);
            assertEquals(42, map.nearest(0, (t[0] + t[2]) / 2.0, (t[1] + t[3]) / 2.0), "the foil's column " + x);
        }
        assertEquals(0, map.storeyAt(1.0), "someone on the nave's floor sees the ground floor's plan");
    }

    @Test
    void itsRunTimeKeysAreInBothLangFiles() throws java.io.IOException {
        PavilionTileChecks.itsRunTimeKeysAreInBothLangFiles(V);
        java.util.Properties zh = PavilionTileChecks.lang("zh_CN"), en = PavilionTileChecks.lang("en_US");
        assertEquals("二向箔仓", zh.getProperty("fluxecho.module.dual_foil_vault"));
        assertEquals("Dual-foil Vault", en.getProperty("fluxecho.module.dual_foil_vault"));
        assertEquals("画布", zh.getProperty("fluxecho.unit.canvas"));
        assertEquals("二向箔", zh.getProperty("fluxecho.unit.foil"));
    }
}
