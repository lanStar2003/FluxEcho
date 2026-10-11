package com.fluxecho.city;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.Test;

import com.fluxecho.frame.Formed;
import com.fluxecho.logic.Blueprint;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.RootShape;
import com.fluxecho.logic.ShapeKit;
import com.fluxecho.logic.SiteFamily;
import com.fluxecho.nexus.TileMultiblock;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;

/**
 * The root's Tidal Well as its tile sees it in the world (0.12.0), for every facing: its blueprint sits where
 * StructureLib's facing math looks for it, the console under the controller opens the window while the taps are
 * units, its one shape is the well, the rings dissolve and let players through, and a well the campus builder sank has
 * its frame where the plan put the blocks, under the nexus.
 */
class TileRootTest {

    private static final int X = -120, Y = 41, Z = 77;

    private static TileRoot root(int x, int y, int z, int facing) {
        TileRoot r = new TileRoot();
        r.xCoord = x;
        r.yCoord = y;
        r.zCoord = z;
        r.place((UUID) null, null, facing);
        return r;
    }

    @Test
    void theBlueprintMapsCellsAsStructureLibDoes() {
        int[] out = new int[3];
        Blueprint b = RootShape.BLUEPRINT;
        for (int facing = 2; facing <= 5; facing++) {
            ForgeDirection f = ForgeDirection.getOrientation(facing);
            ExtendedFacing ef = ExtendedFacing.of(f);
            for (Blueprint.Cell c : b.cells()) {
                ef.getWorldOffset(new int[] { c.a - b.ctrlA, c.b - b.ctrlB, c.c - b.ctrlC }, out);
                assertArrayEquals(
                    new int[] { X + out[0], Y + out[1], Z + out[2] },
                    b.world(c.a, c.b, c.c, X, Y, Z, f.offsetX, f.offsetZ));
            }
        }
    }

    @Test
    void theControllerTheConsoleAndTheTaps() {
        for (int facing = 2; facing <= 5; facing++) {
            TileRoot r = root(X, Y, Z, facing);
            assertArrayEquals(new int[] { 12, 3, 10 }, r.local(X, Y, Z));
            int[] k = r.worldOf(12, 2, 10);
            assertArrayEquals(new int[] { X, Y - 1, Z }, k);
            assertTrue(r.consoleAt(k[0], k[1], k[2]), "the console stand opens the window");
            assertEquals(8, r.unitCount());
            assertEquals(
                8,
                r.samples()
                    .getSlots());
            for (int s = 0; s < 8; s++) {
                int[] p = r.slotCell(s);
                assertEquals(s, r.slotAt(p[0], p[1], p[2]));
                assertEquals(s, r.unitAt(p[0], p[1], p[2]));
                assertFalse(r.consoleAt(p[0], p[1], p[2]));
                assertEquals("tap", r.unitKind(s));
            }
            assertEquals(ModuleCores.ROOT, r.coreMetaOf());
            assertEquals(RootShape.KEY, r.moduleKey());
            assertEquals(TileRoot.COLOR, r.moduleColor());
            // the module's centre is the floor slab's centre, under the island's pad
            assertArrayEquals(r.worldOf(12, 0, 12), r.centre());
        }
    }

    @Test
    void localIsTheInverseOfWorldOf() {
        for (int facing = 2; facing <= 5; facing++) {
            TileRoot r = root(X, Y, Z, facing);
            for (int x = -2; x < 27; x += 3) for (int y = -1; y < 27; y += 2) for (int z = -2; z < 27; z += 3) {
                int[] w = r.worldOf(x, y, z);
                assertArrayEquals(new int[] { x, y, z }, r.local(w[0], w[1], w[2]));
            }
            double[] p = r.localPoint(X + 0.5, Y + 0.25, Z + 0.5);
            assertEquals(12.5, p[0], 1e-9);
            assertEquals(3.25, p[1], 1e-9);
            assertEquals(10.5, p[2], 1e-9);
        }
    }

    @Test
    void theWellIsItsOneShapeAndItsRingsLetPlayersThrough() {
        TileRoot r = root(X, Y, Z, 2);
        List<TileMultiblock.Shape> shapes = r.shapes();
        assertEquals(1, shapes.size());
        assertEquals(RootShape.WELL, shapes.get(0).name);
        assertEquals(RootShape.WELL, r.shapeName());
        assertEquals(Formed.HIDE | Formed.PASS, r.flags('R'));
        assertEquals(0, r.flags('W'));
        assertEquals(0, r.flags('^'), "the pad stays a pad");
        assertEquals(0, r.flags('o'));
        // the window's plan: one storey, the taps' floor, a block three pixels
        com.fluxecho.logic.ModuleMap map = new com.fluxecho.logic.ModuleMap(
            RootShape.BUILD,
            RootShape.UNITS,
            RootShape.DISSOLVES,
            CityGui.MAP_MAX_W,
            CityGui.MAP_MAX_H);
        assertEquals(1, map.storeys());
        assertEquals(RootShape.FLOOR_Y, map.level(0));
    }

    /** The tile of a well the builder sank has its frame where the plan put every block, under the nexus's centre. */
    @Test
    void aSunkWellsFrameIsThePlans() {
        int cx = 40, y0 = 70, cz = -80;
        CampusPlan campus = new CampusPlan(7L);
        for (int[] f : new int[][] { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } }) {
            int site = SiteFamily.ROOT_SITE;
            int[] c = RootShape.controller(site, cx, y0, cz, f[0], f[1]);
            TileRoot r = root(c[0], c[1], c[2], c[3]);
            BuildPlan.Plan p = RootShape.plan(campus, site, cx, y0, cz, f[0], f[1]);
            int units = 0;
            for (BuildPlan.Step s : p.steps) {
                int[] l = r.local(s.x, s.y, s.z);
                assertTrue(l[1] >= 0 && l[1] < RootShape.HEIGHT);
                if (BuildPlan.digs(s.kind)) assertTrue(RootShape.inside(l[0], l[1], l[2]));
                else if (s.kind == BuildPlan.HARD) {
                    char ch = RootShape.cell(l[0], l[1], l[2]);
                    assertEquals(ch == '~' ? Parts.moduleCore(ModuleCores.ROOT) : ShapeKit.partOf(ch), s.part);
                }
                if (s.group == BuildPlan.NO_GROUP) continue;
                assertEquals(s.group, r.unitAt(s.x, s.y, s.z));
                units++;
            }
            assertEquals(8, units);
            assertArrayEquals(new int[] { cx, y0 - 26, cz }, r.centre(), "the slab's centre under the nexus's");
            assertArrayEquals(
                new int[] { cx, y0 - 25, cz },
                r.worldOf(RootShape.PAD_X, RootShape.PAD_Y, RootShape.PAD_Z));
            assertArrayEquals(
                new int[] { cx, y0 - 1, cz },
                r.worldOf(12, RootShape.LID_Y, 12),
                "the lid's open centre");
        }
    }

    /** The root's spec: on the axis under the nexus, nothing on the ground, its own box underground, enough depth. */
    @Test
    void theSpecTakesNothingOnTheGround() {
        com.fluxecho.campus.ModuleSpec spec = CityModule.root();
        assertEquals(SiteFamily.ROOT, spec.family);
        assertTrue(spec.axis());
        assertEquals("tidal_well", spec.research);
        assertArrayEquals(new int[] { SiteFamily.ROOT_SITE }, spec.sites);
        assertEquals(TileRoot.COLOR, spec.colour);
        assertEquals("build.module.why.depth", spec.noRoom(31));
        assertEquals(null, spec.noRoom(32));
        CampusPlan campus = new CampusPlan(7L);
        int cx = 40, y0 = 70, cz = -80;
        assertTrue(
            spec.keepOut(campus, SiteFamily.ROOT_SITE, cx, y0, cz, 0, -1)
                .isEmpty());
        List<int[]> protect = spec.protect(campus, SiteFamily.ROOT_SITE, cx, y0, cz, 0, -1);
        assertEquals(1, protect.size());
        assertEquals(y0 - 1, protect.get(0)[4], "up to the lid, the forum over it stays the campus's");
        assertEquals(
            BuildPlan.moduleKey(RootShape.KEY, SiteFamily.ROOT_SITE),
            spec.plan(campus, SiteFamily.ROOT_SITE, cx, y0, cz, 0, -1).key);
        assertArrayEquals(
            RootShape.controller(SiteFamily.ROOT_SITE, cx, y0, cz, 0, -1),
            spec.controller(SiteFamily.ROOT_SITE, cx, y0, cz, 0, -1));
    }

    /**
     * The names the game builds at run time for the root (its stages on the build page, its module, core, unit kind
     * and why it has no room) and the phase's materials are in both lang files.
     */
    @Test
    void itsRunTimeKeysAreInBothLangFiles() throws java.io.IOException {
        if (com.fluxecho.campus.ModuleSpecs.get(RootShape.KEY) == null)
            com.fluxecho.campus.ModuleSpecs.register(CityModule.root());
        String job = BuildPlan.moduleKey(RootShape.KEY, SiteFamily.ROOT_SITE);
        List<String> keys = new java.util.ArrayList<>();
        for (int s = 0; s <= RootShape.R_STAGES; s++) {
            String k = com.fluxecho.nexus.NexusGui.stageKey(job, s);
            assertEquals("build.stage.r" + Math.min(s, RootShape.R_COMMISSION), k);
            keys.add("fluxecho." + k);
        }
        java.util.Collections.addAll(
            keys,
            "fluxecho.module.root",
            "tile.fluxecho.module_core.root.name",
            "fluxecho.root.tip",
            "fluxecho.root.structure",
            "fluxecho.unit.tap",
            "fluxecho." + RootShape.noRoom(0),
            "item.fluxecho.phase_alloy.name",
            "fluxecho.phase_alloy.tip",
            "item.fluxecho.resonant_glass.name",
            "fluxecho.resonant_glass.tip");
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
}
