package com.fluxecho.city;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.Test;

import com.fluxecho.campus.Campus;
import com.fluxecho.campus.ModuleSpec;
import com.fluxecho.campus.ModuleSpecs;
import com.fluxecho.frame.Formed;
import com.fluxecho.logic.ArcaneCourtShape;
import com.fluxecho.logic.Blueprint;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.SiteFamily;
import com.fluxecho.logic.UnitLayout;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;

/**
 * The Arcane Court as its tile sees it in the world (0.13.0), for every facing: its blueprint sits where StructureLib's
 * facing math looks for it, the altar under the controller at the hall's heart opens the window, every socket is its
 * slot and every unit its kind, the finial dissolves and lets players through, and a court the campus builder raised
 * on a diagonal has its frame where the plan put the blocks. Also its spec (what the campus jobs keep out of once it
 * stands: its apron and allee, never a hall's site) and the names the game builds for it at run time.
 */
class TileArcaneCourtTest {

    private static final int X = 311, Y = 66, Z = -905;
    private static final int[][] FACINGS = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };

    private static TileArcaneCourt court(int x, int y, int z, int facing) {
        TileArcaneCourt t = new TileArcaneCourt();
        t.xCoord = x;
        t.yCoord = y;
        t.zCoord = z;
        t.place((UUID) null, null, facing);
        return t;
    }

    @Test
    void theBlueprintMapsCellsAsStructureLibDoes() {
        Blueprint b = ArcaneCourtShape.BLUEPRINT;
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

    @Test
    void theControllerStandsOnTheAltar() {
        for (int facing = 2; facing <= 5; facing++) {
            TileArcaneCourt t = court(X, Y, Z, facing);
            assertArrayEquals(new int[] { 12, 2, 12 }, t.local(X, Y, Z));
            int[] k = t.worldOf(12, 1, 12);
            assertArrayEquals(new int[] { X, Y - 1, Z }, k, "the altar is right under the controller");
            assertTrue(t.consoleAt(k[0], k[1], k[2]), "the altar opens the window");
            int[] beside = t.worldOf(11, 1, 12);
            assertFalse(t.consoleAt(beside[0], beside[1], beside[2]));
            assertEquals(21, t.unitCount());
            assertEquals(
                44,
                t.samples()
                    .getSlots());
            assertEquals(ModuleCores.ARCANE_COURT, t.coreMetaOf());
            assertEquals(2, t.coreMetaOf(), "fluxecho:module_core meta 2");
            assertEquals(ArcaneCourtShape.KEY, t.moduleKey());
            assertEquals(TileArcaneCourt.COLOR, t.moduleColor());
            assertArrayEquals(t.worldOf(12, 0, 12), t.centre(), "the octagon's centre under the altar");
            assertArrayEquals(new int[] { X, Y - 2, Z }, t.centre());
        }
    }

    @Test
    void everySocketIsItsSlotAndEveryUnitItsKind() {
        UnitLayout layout = ArcaneCourtShape.UNITS;
        for (int facing = 2; facing <= 5; facing++) {
            TileArcaneCourt t = court(X, Y, Z, facing);
            Set<Long> sockets = new HashSet<>();
            for (int s = 0; s < layout.slots(); s++) {
                int[] p = t.slotCell(s);
                assertTrue(sockets.add(Formed.key(p[0], p[1], p[2])), "slot " + s + " shares its socket");
                assertEquals(s, t.slotAt(p[0], p[1], p[2]), "facing " + facing + " slot " + s);
                assertEquals(layout.unitOfSlot(s), t.unitAt(p[0], p[1], p[2]));
                assertFalse(t.consoleAt(p[0], p[1], p[2]));
                int[] l = t.local(p[0], p[1], p[2]);
                assertEquals('o', ArcaneCourtShape.cell(l[0], l[1], l[2]));
            }
            for (int u = 0; u < t.unitCount(); u++) {
                String kind = u == 0 ? "infusion" : u <= 4 ? "cauldron" : u <= 12 ? "pillar" : "desk";
                assertEquals(kind, t.unitKind(u), "unit " + u);
                int[] want = u == 0 ? new int[] { 0, 8 }
                    : u <= 4 ? new int[] { 8 + u - 1, 1 }
                        : u <= 12 ? new int[] { 12 + 3 * (u - 5), 3 } : new int[] { 36 + u - 13, 1 };
                assertArrayEquals(want, t.unitSlots(u), "unit " + u);
                List<int[]> cells = t.unitCells(u);
                assertEquals(
                    kind.equals("infusion") ? 8 : kind.equals("cauldron") ? 9 : kind.equals("pillar") ? 5 : 2,
                    cells.size());
                for (int[] p : cells) assertEquals(u, t.unitAt(p[0], p[1], p[2]));
            }
            assertEquals("", t.unitKind(21));
            assertEquals(-1, t.unitAt(X, Y, Z), "the controller is no unit");
            assertEquals(-1, t.slotAt(X, Y, Z));
            assertNull(t.slotCell(44));
        }
    }

    /** Formed, the finial's conduits and seat give way to the rendered crystal and let players through. */
    @Test
    void theFinialDissolvesAndLetsPlayersThrough() {
        TileArcaneCourt t = court(X, Y, Z, 3);
        assertEquals(Formed.HIDE | Formed.PASS, t.flags('C'));
        assertEquals(Formed.HIDE | Formed.PASS, t.flags('Z'));
        assertEquals(0, t.flags('K'), "the altar stays");
        assertEquals(0, t.flags('L'));
        assertEquals(0, t.flags('W'));
        assertEquals(0, t.flags('G'));
        assertEquals(0, t.flags('o'));
        assertEquals(0, t.flags('A'));
        // the window's plan: one storey, the hall's floor (the spire over it is open), every unit on it
        com.fluxecho.logic.ModuleMap map = new com.fluxecho.logic.ModuleMap(
            ArcaneCourtShape.BUILD,
            ArcaneCourtShape.UNITS,
            ArcaneCourtShape.DISSOLVES,
            CityGui.MAP_MAX_W,
            CityGui.MAP_MAX_H);
        assertEquals(1, map.storeys());
        assertEquals(1, map.level(0));
        assertTrue(map.w <= CityGui.MAP_MAX_W && map.h <= CityGui.MAP_MAX_H, "the plan fits the window");
        for (int u = 0; u < ArcaneCourtShape.UNITS.size(); u++) {
            assertEquals(0, map.storeyOf(u));
            assertFalse(
                map.tiles(u)
                    .isEmpty(),
                "unit " + u + " shows on the plan");
        }
    }

    @Test
    void localIsTheInverseOfWorldOf() {
        for (int facing = 2; facing <= 5; facing++) {
            TileArcaneCourt t = court(X, Y, Z, facing);
            for (int x = -3; x < 28; x += 3) for (int y = -1; y < 34; y += 3) for (int z = -3; z < 28; z += 4) {
                int[] w = t.worldOf(x, y, z);
                assertArrayEquals(new int[] { x, y, z }, t.local(w[0], w[1], w[2]));
            }
        }
    }

    /**
     * The tile of a court the builder raised on any of its diagonals, for every front of the nexus, has its frame where
     * the plan put every block: the core where the controller is, every unit block of the unit the tile names, every
     * hard block the part its cell asks for, and its centre on the diagonal's.
     */
    @Test
    void aBuiltCourtsFrameIsThePlans() {
        int cx = 40, y0 = 64, cz = -80;
        CampusPlan campus = new CampusPlan(11L);
        for (int[] f : FACINGS) for (int site : ArcaneCourtShape.SITES) {
            int[] c = ArcaneCourtShape.controller(site, cx, y0, cz, f[0], f[1]);
            TileArcaneCourt t = court(c[0], c[1], c[2], c[3]);
            BuildPlan.Plan p = ArcaneCourtShape.plan(campus, site, cx, y0, cz, f[0], f[1]);
            int units = 0, cores = 0, hard = 0;
            for (BuildPlan.Step s : p.steps) {
                if (s.part == Parts.moduleCore(ModuleCores.ARCANE_COURT)) {
                    assertArrayEquals(new int[] { c[0], c[1], c[2] }, new int[] { s.x, s.y, s.z });
                    cores++;
                }
                int[] l = t.local(s.x, s.y, s.z);
                boolean inBox = l[0] >= 0 && l[0] < ArcaneCourtShape.WIDTH
                    && l[1] >= 0
                    && l[1] < ArcaneCourtShape.HEIGHT
                    && l[2] >= 0
                    && l[2] < ArcaneCourtShape.DEPTH;
                if (s.kind == BuildPlan.HARD && inBox) {
                    char ch = ArcaneCourtShape.cell(l[0], l[1], l[2]);
                    if (ch != ' ' && ch != '-') {
                        assertEquals(
                            ArcaneCourtShape.BUILD.part(l[0], l[1], l[2], ch),
                            s.part,
                            "site " + site + " " + s);
                        hard++;
                    }
                }
                if (s.group == BuildPlan.NO_GROUP) continue;
                assertEquals(s.group, t.unitAt(s.x, s.y, s.z), "site " + site + " " + s);
                units++;
            }
            assertEquals(1, cores);
            assertEquals(8 + 4 * 9 + 8 * 5 + 8 * 2, units);
            assertTrue(hard > 1500, "the court's blocks: " + hard);
            int[] centre = t.centre();
            assertArrayEquals(
                CampusPlan.diagonalCentre(site),
                CampusPlan.toLocal(centre[0] - cx, centre[2] - cz, f[0], f[1]));
            assertEquals(y0, centre[1]);
            // what the nexus's dock asks of a formed pavilion: its centre on the diagonal's, its front along A inward
            int[] local = CampusPlan.toLocal(centre[0] - cx, centre[2] - cz, f[0], f[1]);
            assertEquals(site, CampusPlan.pavilionSiteAt(local[0], local[1]));
            assertArrayEquals(
                CampusPlan.pavilionFront(site),
                CampusPlan.toLocal(t.front().offsetX, t.front().offsetZ, f[0], f[1]));
        }
    }

    /**
     * The court's spec: a pavilion on its diagonals in the order it prefers them, opened by its research, the core of
     * meta 2; its keep-out is its site row by row (the apron and the allee, nothing else), which every block and every
     * cleared column of its job lies in, and which no hall's site box ever meets; its protection is the same boxes from
     * bedrock to the sky.
     */
    @Test
    void theSpecKeepsTheCampusOffItsApronAndAllee() {
        ModuleSpec spec = CityModule.arcaneCourt();
        assertEquals(SiteFamily.PAVILION, spec.family);
        assertEquals(ArcaneCourtShape.KEY, spec.key);
        assertEquals("arcane_court", spec.research);
        assertArrayEquals(new int[] { 7, 1, 5, 3 }, spec.sites);
        assertEquals(25, spec.width);
        assertEquals(25, spec.depth);
        assertEquals(ArcaneCourtShape.HEIGHT, spec.height);
        assertEquals(Parts.moduleCore(ModuleCores.ARCANE_COURT), spec.corePart);
        assertEquals(TileArcaneCourt.COLOR, spec.colour);
        assertFalse(spec.axis());
        assertNull(spec.noRoom(5));
        for (int site : new int[] { 0, 2, 4, 6, 8, 9 }) assertFalse(spec.hasSite(site), "site " + site);
        CampusPlan campus = new CampusPlan(3L);
        ModuleSpec dome = CityModule.ecoDome();
        int cx = -200, y0 = 70, cz = 512;
        for (int[] f : FACINGS) for (int site : spec.sites) {
            List<int[]> keep = spec.keepOut(campus, site, cx, y0, cz, f[0], f[1]);
            assertEquals(
                CampusPlan.rowBoxes(CampusPlan.pavilionSite(site))
                    .size(),
                keep.size());
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
            BuildPlan.Plan p = spec.plan(campus, site, cx, y0, cz, f[0], f[1]);
            assertEquals(BuildPlan.moduleKey(ArcaneCourtShape.KEY, site), p.key);
            assertArrayEquals(
                ArcaneCourtShape.controller(site, cx, y0, cz, f[0], f[1]),
                spec.controller(site, cx, y0, cz, f[0], f[1]));
            for (BuildPlan.Step s : p.steps) assertTrue(cols.contains(col(s.x, s.z)), "outside the keep-out: " + s);
            for (BuildPlan.Column c : p.clear)
                assertTrue(cols.contains(col(c.x, c.z)), "cleared outside: " + c.x + "," + c.z);
            // the halls' site boxes never meet a pavilion's keep-out
            for (int hall : CampusPlan.HALL_SITES) {
                int[] h = dome.siteBox(campus, hall, cx, y0, cz, f[0], f[1]);
                for (int x = h[0]; x <= h[2]; x++) for (int z = h[1]; z <= h[3]; z++)
                    assertFalse(cols.contains(col(x, z)), "hall " + hall + " meets pavilion " + site);
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
     * The court is 33 high, the first ground module taller than the nexus core's headroom: its finial's seat stands at
     * Y0 + 32, over the world's top for a base at y 224 or 225 (a core may still be placed there). Since 0.14.1 the
     * plan leaves the finial's cells over the world out and the court is built (its controller and units stand at
     * Y0 + 5 at most); only a base over 250, which no core is placed at, has no room for it.
     */
    @Test
    void theCourtNeedsRoomUnderTheWorldsTop() {
        ModuleSpec spec = CityModule.arcaneCourt();
        int highest = 255 - com.fluxecho.nexus.ItemBlockNexusCore.HEADROOM;
        assertEquals(225, highest, "the highest base a nexus core may be placed at");
        assertEquals(255, 223 + ArcaneCourtShape.HEIGHT - 1, "the seat at the world's top");
        assertNull(spec.noRoom(64));
        assertNull(spec.noRoom(223));
        assertNull(spec.noRoom(224), "the finial clips");
        assertNull(spec.noRoom(highest));
        assertNull(spec.noRoom(250), "its units' tops at the world's top");
        assertEquals("build.module.why.height", spec.noRoom(251));
        for (ModuleSpec other : new ModuleSpec[] { CityModule.bloodCovenant(), CityModule.huntingGround(),
            CityModule.manaFountain(), CityModule.ecoDome() }) assertNull(other.noRoom(highest), other.key);
    }

    /**
     * The ◀ ▶ site choice and the offer take a site whose footprint box (one cell round the octagon) meets no standing
     * module's keep-out box: a hall standing on any hall site (the Archive or the Eco Dome) or a pavilion standing on
     * another diagonal never blocks a diagonal, so every diagonal stays free until a module takes it.
     */
    @Test
    void noOtherModuleBlocksADiagonal() {
        ModuleSpec court = CityModule.arcaneCourt(), dome = CityModule.ecoDome();
        ModuleSpec archive = new ModuleSpec(
            "archivetest",
            "library",
            com.fluxecho.logic.ArchiveShape.WIDTH,
            com.fluxecho.logic.ArchiveShape.DEPTH,
            com.fluxecho.logic.ArchiveShape.HEIGHT,
            CampusPlan.HALL_SITES,
            Parts.LIBRARY_CORE,
            0x8A5CFF,
            BuildPlan::archive,
            BuildPlan::archiveController);
        CampusPlan campus = new CampusPlan(5L);
        int cx = -64, y0 = 72, cz = 64;
        for (int[] f : FACINGS) for (int site : court.sites) {
            int[] box = court.footprint(site, cx, cz, f[0], f[1], 1);
            assertEquals(26, Math.abs(box[2] - box[0]), "the octagon's 25 and one round it");
            for (int hall : CampusPlan.HALL_SITES) for (ModuleSpec h : new ModuleSpec[] { dome, archive }) {
                for (int[] k : h.keepOut(campus, hall, cx, y0, cz, f[0], f[1]))
                    assertFalse(overlap(box, k), h.key + " on " + hall + " blocks " + site);
                // and the other way round: a standing court never blocks a hall site
                int[] hb = h.footprint(hall, cx, cz, f[0], f[1], 1);
                for (int[] k : court.keepOut(campus, site, cx, y0, cz, f[0], f[1]))
                    assertFalse(overlap(hb, k), "the court on " + site + " blocks " + h.key + " on " + hall);
            }
            for (int other : court.sites) {
                if (other == site) continue;
                for (int[] k : court.keepOut(campus, other, cx, y0, cz, f[0], f[1]))
                    assertFalse(overlap(box, k), "the pavilion on " + other + " blocks " + site);
            }
        }
    }

    private static boolean overlap(int[] a, int[] b) {
        return Math.min(a[0], a[2]) <= Math.max(b[0], b[2]) && Math.min(b[0], b[2]) <= Math.max(a[0], a[2])
            && Math.min(a[1], a[3]) <= Math.max(b[1], b[3])
            && Math.min(b[1], b[3]) <= Math.max(a[1], a[3]);
    }

    /**
     * The refresh (the forum plan) of a campus with a court standing keeps off the court's whole site: it never grades
     * nor paves a column of its apron or its allee, while it does grade there without the keep-out.
     */
    @Test
    void theRefreshKeepsOffAStandingCourt() {
        ModuleSpec spec = CityModule.arcaneCourt();
        CampusPlan campus = new CampusPlan(29L);
        int cx = 1000, y0 = 68, cz = 1000;
        for (int[] f : FACINGS) for (int site : spec.sites) {
            List<int[]> keep = spec.keepOut(campus, site, cx, y0, cz, f[0], f[1]);
            Set<Long> cols = new HashSet<>();
            for (int[] b : keep)
                for (int x = b[0]; x <= b[2]; x++) for (int z = b[1]; z <= b[3]; z++) cols.add(col(x, z));
            BuildPlan.Plan open = BuildPlan.forum(campus, cx, y0, cz, f[0], f[1], new ArrayList<>());
            int inside = 0;
            for (BuildPlan.Column c : open.clear) if (cols.contains(col(c.x, c.z))) inside++;
            assertTrue(inside > 100, "without the keep-out the refresh grades the court's site: " + inside);
            BuildPlan.Plan refresh = BuildPlan.forum(campus, cx, y0, cz, f[0], f[1], keep);
            for (BuildPlan.Column c : refresh.clear)
                assertFalse(cols.contains(col(c.x, c.z)), "graded inside the court's site: " + c.x + "," + c.z);
            for (BuildPlan.Step s : refresh.steps)
                assertFalse(cols.contains(col(s.x, s.z)), "paved inside the court's site: " + s);
            assertFalse(refresh.clear.isEmpty(), "the rest of the disc is graded");
        }
    }

    /**
     * What the client's campus says stands on a diagonal (0.13.0), which the bridge to a module docked there and not
     * loaded on the client goes by: only a BUILT pavilion on that very site makes the module a pavilion, whose bridge
     * runs to its inner portal; a court still being built or built elsewhere, or a module that is no pavilion, leaves a
     * module docked there on the 0.9.2 inner ring, on its ring bridge as in 0.12.0.
     */
    @Test
    void theCampusTellsAPavilionFromARingModule() {
        if (ModuleSpecs.get(ArcaneCourtShape.KEY) == null) ModuleSpecs.register(CityModule.arcaneCourt());
        String court = ArcaneCourtShape.KEY, research = court;
        List<Campus.ModuleState> l = new ArrayList<>();
        l.add(new Campus.ModuleState(court, research, Campus.ModuleStatus.BUILT, 3));
        l.add(new Campus.ModuleState("no_such_module", "", Campus.ModuleStatus.BUILT, 5));
        l.add(new Campus.ModuleState(court, research, Campus.ModuleStatus.BUILDING, 1));
        Campus.View v = new Campus.View();
        v.modules = l;
        assertTrue(v.pavilionBuilt(3), "the court built on 3");
        assertEquals(CampusPlan.PAVILION_DOOR, CampusPlan.siteDoor(3, v.pavilionBuilt(3)), 1e-12);
        for (int site : new int[] { 1, 5, 7 }) {
            assertFalse(v.pavilionBuilt(site), "nothing built as a pavilion on " + site);
            assertEquals(-1, CampusPlan.siteDoor(site, v.pavilionBuilt(site)), 1e-12, "the ring bridge on " + site);
        }
        assertFalse(Campus.ModuleState.pavilionBuilt(null, 3));
        // a pavilion's state never makes a hall site or the gate a pavilion's
        l.add(new Campus.ModuleState(court, research, Campus.ModuleStatus.BUILT, 4));
        assertFalse(v.pavilionBuilt(4));
        assertFalse(v.pavilionBuilt(0));
        assertFalse(new Campus.View().pavilionBuilt(3), "a legacy campus lists no modules");
    }

    /**
     * The names the game builds at run time for the court (its stages on the build page, its module, core, research
     * and unit kinds) and the build page's module pager are in both lang files.
     */
    @Test
    void itsRunTimeKeysAreInBothLangFiles() throws java.io.IOException {
        if (ModuleSpecs.get(ArcaneCourtShape.KEY) == null) ModuleSpecs.register(CityModule.arcaneCourt());
        List<String> keys = new ArrayList<>();
        for (int site : ArcaneCourtShape.SITES) {
            String job = BuildPlan.moduleKey(ArcaneCourtShape.KEY, site);
            for (int s = 0; s <= BuildPlan.M_COMMISSION + 1; s++) {
                String k = com.fluxecho.nexus.NexusGui.stageKey(job, s);
                assertEquals("build.stage.c" + Math.min(s, BuildPlan.M_COMMISSION), k);
                keys.add("fluxecho." + k);
            }
        }
        java.util.Collections.addAll(
            keys,
            "fluxecho.module.arcane_court",
            "tile.fluxecho.module_core.arcane_court.name",
            "fluxecho.arcane_court.tip",
            "fluxecho.arcane_court.structure",
            "fluxecho.research.node.arcane_court",
            "fluxecho.research.node.arcane_court.desc",
            "fluxecho.build.gui.module_page.tip");
        for (int u = 0; u < ArcaneCourtShape.UNITS.size(); u++)
            keys.add("fluxecho.unit." + ArcaneCourtShape.UNITS.unit(u).kind);
        for (String lang : new String[] { "en_US", "zh_CN" }) {
            List<String> lines = java.nio.file.Files.readAllLines(
                java.nio.file.Paths.get("src", "main", "resources", "assets", "fluxecho", "lang", lang + ".lang"),
                java.nio.charset.StandardCharsets.UTF_8);
            for (String k : keys) {
                boolean found = false;
                for (String l : lines) found |= l.startsWith(k + "=");
                assertTrue(found, lang + " lacks " + k);
            }
        }
    }

    private static long col(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
