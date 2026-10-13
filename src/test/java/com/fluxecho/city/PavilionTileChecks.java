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
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.CityBuild;
import com.fluxecho.logic.ModuleMap;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.SiteFamily;
import com.fluxecho.logic.UnitLayout;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;

/**
 * What every pavilion's tile test asks of it in the world (0.13.0), the checks {@code TileArcaneCourtTest} makes of
 * the Arcane Court made general for the Blood Covenant Altar, the Echo Hunting Ground and the Mana Fountain Array: its
 * blueprint sits where StructureLib's facing math looks for it, the console under the controller opens the window,
 * every socket is its slot, {@code local} undoes {@code worldOf}, a pavilion the campus builder raised on a diagonal
 * has its frame where the plan put the blocks and docks there, its spec keeps the campus off its apron and allee
 * (never a hall's site), the refresh keeps off it, its window's plan fits, and the names the game builds for it at run
 * time are in both lang files.
 */
final class PavilionTileChecks {

    static final int X = 311, Y = 66, Z = -905;
    static final int[][] FACINGS = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };

    /** One pavilion's data the checks read. */
    static final class Pavilion {

        final Supplier<? extends TileCityModule> make;
        final ModuleSpec spec;
        final CityBuild build;
        final UnitLayout layout;
        final int meta, colour;
        final String dissolves;

        Pavilion(Supplier<? extends TileCityModule> make, ModuleSpec spec, CityBuild build, UnitLayout layout, int meta,
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

    private PavilionTileChecks() {}

    static void blueprintMapsCellsAsStructureLibDoes(Pavilion p) {
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
     * The controller stands over its console at the octagon's middle, the console opens the window and its neighbour
     * does not; the tile names its module, colour, core meta, units and slots, and its centre is the foundation's
     * under the controller.
     */
    static void theControllerStandsOnItsConsole(Pavilion p) {
        int[] ctrl = p.build.controller();
        assertEquals(12, ctrl[0]);
        assertEquals(12, ctrl[2]);
        for (int facing = 2; facing <= 5; facing++) {
            TileCityModule t = p.at(X, Y, Z, facing);
            assertArrayEquals(ctrl, t.local(X, Y, Z));
            int[] k = t.worldOf(12, ctrl[1] - 1, 12);
            assertArrayEquals(new int[] { X, Y - 1, Z }, k, "the console is right under the controller");
            assertTrue(t.consoleAt(k[0], k[1], k[2]), "the console opens the window");
            int[] beside = t.worldOf(11, ctrl[1] - 1, 12);
            assertFalse(t.consoleAt(beside[0], beside[1], beside[2]));
            assertEquals(p.layout.size(), t.unitCount());
            assertEquals(
                p.layout.slots(),
                t.samples()
                    .getSlots());
            assertEquals(p.meta, t.coreMetaOf());
            assertEquals(p.spec.key, t.moduleKey());
            assertEquals(p.colour, t.moduleColor());
            assertArrayEquals(t.worldOf(12, 0, 12), t.centre(), "the octagon's centre under the controller");
            assertArrayEquals(new int[] { X, Y - ctrl[1], Z }, t.centre());
        }
    }

    /** Every socket is its own slot, every unit its kind, cells and slots; the controller is no unit. */
    static void everySocketIsItsSlotAndEveryUnitItsKind(Pavilion p) {
        UnitLayout layout = p.layout;
        for (int facing = 2; facing <= 5; facing++) {
            TileCityModule t = p.at(X, Y, Z, facing);
            Set<Long> sockets = new HashSet<>();
            for (int s = 0; s < layout.slots(); s++) {
                int[] c = t.slotCell(s);
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
                for (int[] c : cells) assertEquals(u, t.unitAt(c[0], c[1], c[2]));
            }
            assertEquals("", t.unitKind(t.unitCount()));
            assertEquals(-1, t.unitAt(X, Y, Z), "the controller is no unit");
            assertEquals(-1, t.slotAt(X, Y, Z));
            assertNull(t.slotCell(layout.slots()));
        }
    }

    static void localIsTheInverseOfWorldOf(Pavilion p) {
        for (int facing = 2; facing <= 5; facing++) {
            TileCityModule t = p.at(X, Y, Z, facing);
            for (int x = -3; x < 28; x += 3)
                for (int y = -1; y < p.build.height() + 2; y += 2) for (int z = -3; z < 28; z += 4) {
                    int[] w = t.worldOf(x, y, z);
                    assertArrayEquals(new int[] { x, y, z }, t.local(w[0], w[1], w[2]));
                }
        }
    }

    /**
     * The tile of a pavilion the builder raised on any of its diagonals, for every front of the nexus, has its frame
     * where the plan put every block: the core where the controller is, every unit block of the unit the tile names,
     * every hard block the part its cell asks for, its centre on the diagonal's, and what the nexus's dock asks of it
     * (its centre gives back its site, its front looks along A inward).
     */
    static void aBuiltPavilionsFrameIsThePlans(Pavilion p) {
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
            assertTrue(hard > 1000, "the pavilion's blocks: " + hard);
            int[] centre = t.centre();
            int[] local = CampusPlan.toLocal(centre[0] - cx, centre[2] - cz, f[0], f[1]);
            assertArrayEquals(CampusPlan.diagonalCentre(site), local);
            assertEquals(y0, centre[1]);
            assertEquals(site, CampusPlan.pavilionSiteAt(local[0], local[1]));
            assertArrayEquals(
                CampusPlan.pavilionFront(site),
                CampusPlan.toLocal(t.front().offsetX, t.front().offsetZ, f[0], f[1]));
        }
    }

    /**
     * The spec: a pavilion on its diagonals in the order it prefers them, opened by its research, the core of its meta;
     * its keep-out is its site row by row (the apron and the allee), which every block and every cleared column of its
     * job lies in and which no hall's site box ever meets; its protection is the same boxes from bedrock to the sky; no
     * hall or other pavilion standing blocks its diagonals, and it blocks no hall site.
     */
    static void theSpecKeepsTheCampusOffItsApronAndAllee(Pavilion p, int[] sites) {
        ModuleSpec spec = p.spec;
        assertEquals(SiteFamily.PAVILION, spec.family);
        assertEquals(spec.key, spec.research, "the module is its research node");
        assertArrayEquals(sites, spec.sites);
        assertEquals(25, spec.width);
        assertEquals(25, spec.depth);
        assertEquals(p.build.height(), spec.height);
        assertEquals(Parts.moduleCore(p.meta), spec.corePart);
        assertEquals(p.colour, spec.colour);
        assertFalse(spec.axis());
        assertNull(spec.noRoom(5));
        // lower than the Arcane Court, it fits the highest base a nexus core may be placed at
        assertNull(spec.noRoom(255 - com.fluxecho.nexus.ItemBlockNexusCore.HEADROOM));
        for (int site : new int[] { 0, 2, 4, 6, 8, 9 }) assertFalse(spec.hasSite(site), "site " + site);
        CampusPlan campus = new CampusPlan(3L);
        ModuleSpec dome = CityModule.ecoDome(), court = CityModule.arcaneCourt();
        int cx = -200, y0 = 70, cz = 512;
        for (int[] f : FACINGS) for (int site : spec.sites) {
            List<int[]> keep = spec.keepOut(campus, site, cx, y0, cz, f[0], f[1]);
            Set<Long> cols = new HashSet<>();
            for (int[] b : keep) {
                assertEquals(4, b.length, "whole columns");
                for (int x = b[0]; x <= b[2]; x++) for (int z = b[1]; z <= b[3]; z++)
                    assertTrue(cols.add(col(x, z)), "keep-out boxes overlap at " + x + "," + z);
            }
            assertEquals(
                CampusPlan.pavilionSite(site)
                    .size(),
                cols.size(),
                "the boxes are the site's cells");
            BuildPlan.Plan plan = spec.plan(campus, site, cx, y0, cz, f[0], f[1]);
            assertEquals(BuildPlan.moduleKey(spec.key, site), plan.key);
            for (BuildPlan.Step s : plan.steps) assertTrue(cols.contains(col(s.x, s.z)), "outside the keep-out: " + s);
            for (BuildPlan.Column c : plan.clear)
                assertTrue(cols.contains(col(c.x, c.z)), "cleared outside: " + c.x + "," + c.z);
            for (int hall : CampusPlan.HALL_SITES) {
                int[] h = dome.siteBox(campus, hall, cx, y0, cz, f[0], f[1]);
                for (int x = h[0]; x <= h[2]; x++) for (int z = h[1]; z <= h[3]; z++)
                    assertFalse(cols.contains(col(x, z)), "hall " + hall + " meets pavilion " + site);
                int[] hb = dome.footprint(hall, cx, cz, f[0], f[1], 1);
                for (int[] k : keep) assertFalse(overlap(hb, k), spec.key + " on " + site + " blocks hall " + hall);
                for (int[] k : dome.keepOut(campus, hall, cx, y0, cz, f[0], f[1])) assertFalse(
                    overlap(spec.footprint(site, cx, cz, f[0], f[1], 1), k),
                    "the dome on " + hall + " blocks " + site);
            }
            // a pavilion standing on another diagonal never blocks this one
            int[] box = spec.footprint(site, cx, cz, f[0], f[1], 1);
            for (int other : CampusPlan.PAVILION_SITES) {
                if (other == site) continue;
                for (int[] k : court.keepOut(campus, other, cx, y0, cz, f[0], f[1]))
                    assertFalse(overlap(box, k), "a pavilion on " + other + " blocks " + site);
            }
            List<int[]> protect = spec.protect(campus, site, cx, y0, cz, f[0], f[1]);
            assertEquals(keep.size(), protect.size());
            for (int i = 0; i < keep.size(); i++) {
                int[] k = keep.get(i), q = protect.get(i);
                assertArrayEquals(new int[] { k[0], 0, k[1], k[2], 255, k[3] }, q);
            }
        }
    }

    /** The refresh (the forum plan) of a campus with the pavilion standing never grades nor paves its site. */
    static void theRefreshKeepsOffAStandingPavilion(Pavilion p) {
        CampusPlan campus = new CampusPlan(29L);
        int cx = 1000, y0 = 68, cz = 1000;
        for (int[] f : FACINGS) for (int site : p.spec.sites) {
            List<int[]> keep = p.spec.keepOut(campus, site, cx, y0, cz, f[0], f[1]);
            Set<Long> cols = new HashSet<>();
            for (int[] b : keep)
                for (int x = b[0]; x <= b[2]; x++) for (int z = b[1]; z <= b[3]; z++) cols.add(col(x, z));
            BuildPlan.Plan refresh = BuildPlan.forum(campus, cx, y0, cz, f[0], f[1], keep);
            for (BuildPlan.Column c : refresh.clear)
                assertFalse(cols.contains(col(c.x, c.z)), "graded inside the site: " + c.x + "," + c.z);
            for (BuildPlan.Step s : refresh.steps)
                assertFalse(cols.contains(col(s.x, s.z)), "paved inside the site: " + s);
        }
    }

    /**
     * The module window's plan, made as the window makes it (with the levels its tile shows for the building alone,
     * {@link TileCityModule#planLevels}): one storey the units stand on, every unit on it and showing, the console on
     * the plan's middle under the controller, the levels shown for the building alone after it holding no units, and
     * the plan fits the window; returns the map for the pavilion's own checks.
     */
    static ModuleMap theWindowsPlanFits(Pavilion p) {
        int[] levels = p.make.get()
            .planLevels();
        ModuleMap map = new ModuleMap(p.build, p.layout, p.dissolves, CityGui.MAP_MAX_W, CityGui.MAP_MAX_H, levels);
        assertEquals(1 + levels.length, map.storeys());
        assertEquals(1, map.level(0));
        assertFalse(map.overview(0));
        for (int i = 0; i < levels.length; i++) {
            assertEquals(levels[i], map.level(1 + i));
            assertTrue(map.overview(1 + i), "level " + levels[i] + " is shown for the building alone");
            assertTrue(
                map.units(1 + i)
                    .isEmpty());
            assertEquals(-1, map.nearest(1 + i, map.w / 2.0, map.h / 2.0));
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
     * The names the game builds at run time for the pavilion (its stages on the build page, its module, core, research
     * and unit kinds) are in both lang files.
     */
    static void itsRunTimeKeysAreInBothLangFiles(Pavilion p) throws IOException {
        String key = p.spec.key;
        if (ModuleSpecs.get(key) == null) ModuleSpecs.register(p.spec);
        List<String> keys = new ArrayList<>();
        for (int site : p.spec.sites) {
            String job = BuildPlan.moduleKey(key, site);
            for (int s = 0; s <= BuildPlan.M_COMMISSION + 1; s++) {
                String k = com.fluxecho.nexus.NexusGui.stageKey(job, s);
                assertEquals("build.stage.c" + Math.min(s, BuildPlan.M_COMMISSION), k);
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

    /** A lang file's keys and values, read as the game reads it (UTF-8, one {@code key=value} a line). */
    static java.util.Properties lang(String lang) throws IOException {
        java.util.Properties out = new java.util.Properties();
        for (String l : Files.readAllLines(
            Paths.get("src", "main", "resources", "assets", "fluxecho", "lang", lang + ".lang"),
            StandardCharsets.UTF_8)) {
            int eq = l.indexOf('=');
            if (eq > 0 && !l.startsWith("#")) out.setProperty(l.substring(0, eq), l.substring(eq + 1));
        }
        return out;
    }

    static boolean overlap(int[] a, int[] b) {
        return Math.min(a[0], a[2]) <= Math.max(b[0], b[2]) && Math.min(b[0], b[2]) <= Math.max(a[0], a[2])
            && Math.min(a[1], a[3]) <= Math.max(b[1], b[3])
            && Math.min(b[1], b[3]) <= Math.max(a[1], a[3]);
    }

    static long col(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
