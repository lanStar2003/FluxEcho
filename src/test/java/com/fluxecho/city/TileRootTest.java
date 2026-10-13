package com.fluxecho.city;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
 * units, its shapes are the wells (the full one first, the shallower ones over low ground appended since 0.14.1), the
 * rings dissolve and let players through, and a well the campus builder sank has its frame where the plan put the
 * blocks, under the nexus, at every depth.
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
    void theWellsAreItsShapesAndItsRingsLetPlayersThrough() {
        TileRoot r = root(X, Y, Z, 2);
        List<TileMultiblock.Shape> shapes = r.shapes();
        // 0.12's well keeps its name and blueprint, so every well that formed still forms; the shallower wells are
        // appended (0.14.1)
        assertEquals(4, shapes.size());
        assertEquals(RootShape.WELL, shapes.get(0).name);
        assertSame(RootShape.BLUEPRINT, shapes.get(0).bp);
        assertEquals(RootShape.WELL_20, shapes.get(1).name);
        assertEquals(RootShape.WELL_14, shapes.get(2).name);
        assertEquals(RootShape.WELL_8, shapes.get(3).name);
        for (TileMultiblock.Shape s : shapes) assertTrue(r.tries(s), "any well while no nexus is known");
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

    /**
     * Over low ground (0.14.1): the builder names the well of the campus's depth, and the tile of a well it sank there
     * has its frame where the plan put every block, the slab Y0 - D under the nexus's centre, the lid at Y0 - 1.
     */
    @Test
    void aShallowWellsFrameIsThePlans() {
        int cx = 40, cz = -80;
        CampusPlan campus = new CampusPlan(7L);
        for (int y0 : new int[] { 14, 19, 20, 25, 26, 31 }) {
            RootShape.Well w = RootShape.forBase(y0);
            int site = SiteFamily.ROOT_SITE;
            int[] c = RootShape.controller(site, cx, y0, cz, 0, -1);
            TileRoot r = root(c[0], c[1], c[2], c[3]);
            r.expectShape(
                CityModule.root()
                    .shapeFor(y0));
            assertEquals(w.name, r.shapeName());
            assertSame(w, r.well());
            assertSame(w.build, r.build());
            BuildPlan.Plan p = RootShape.plan(campus, site, cx, y0, cz, 0, -1);
            int units = 0;
            for (BuildPlan.Step s : p.steps) {
                int[] l = r.local(s.x, s.y, s.z);
                assertTrue(l[1] >= 0 && l[1] < w.depth, "inside the well at " + y0);
                assertTrue(s.y >= RootShape.LOWEST_SLAB, "over the bedrock at " + y0);
                if (BuildPlan.digs(s.kind)) assertTrue(w.inside(l[0], l[1], l[2]));
                else if (s.kind == BuildPlan.HARD) {
                    char ch = w.cell(l[0], l[1], l[2]);
                    assertEquals(ch == '~' ? Parts.moduleCore(ModuleCores.ROOT) : ShapeKit.partOf(ch), s.part);
                }
                if (s.group == BuildPlan.NO_GROUP) continue;
                assertEquals(s.group, r.unitAt(s.x, s.y, s.z));
                units++;
            }
            assertEquals(8, units);
            assertArrayEquals(new int[] { cx, y0 - w.depth, cz }, r.centre(), "the slab's centre at " + y0);
            assertArrayEquals(new int[] { cx, y0 - 1, cz }, r.worldOf(12, w.lidY, 12), "the lid's open centre");
            for (int s = 0; s < 8; s++) {
                int[] at = r.slotCell(s);
                assertEquals(s, r.slotAt(at[0], at[1], at[2]), "the same taps, the same slots");
            }
        }
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
        // 0.14.1: a shallower well over lower ground; none under Y0 14
        assertEquals("build.module.why.depth", spec.noRoom(13));
        assertEquals(null, spec.noRoom(14));
        assertEquals(null, spec.noRoom(31));
        assertEquals(null, spec.noRoom(32));
        assertEquals(RootShape.WELL, spec.shapeFor(32));
        assertEquals(RootShape.WELL_20, spec.shapeFor(31));
        assertEquals(RootShape.WELL_8, spec.shapeFor(14));
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

    /** A formed nexus whose base layer is {@code y0}, facing east. */
    private static com.fluxecho.nexus.TileNexus nexus(int y0) {
        com.fluxecho.nexus.TileNexus n = new com.fluxecho.nexus.TileNexus() {

            @Override
            public boolean formed() {
                return true;
            }
        };
        n.xCoord = -30;
        n.yCoord = y0 + 2;
        n.zCoord = 55;
        n.place((UUID) null, null, 5);
        assertEquals(y0, n.centre()[1]);
        return n;
    }

    /** A root tile at a cell {x, y, z, facing}, which finds the nexus over it among that one nexus. */
    private static TileRoot under(com.fluxecho.nexus.TileNexus n, int[] at) {
        TileRoot r = new TileRoot() {

            @Override
            public com.fluxecho.nexus.TileNexus over() {
                return over(java.util.Collections.singletonList(n));
            }
        };
        r.xCoord = at[0];
        r.yCoord = at[1];
        r.zCoord = at[2];
        r.place((UUID) null, null, at[3]);
        return r;
    }

    private static int[] rootCell(com.fluxecho.nexus.TileNexus n, int depth) {
        int[] c = n.centre();
        return RootShape.well(depth)
            .controller(SiteFamily.ROOT_SITE, c[0], c[1], c[2], n.front().offsetX, n.front().offsetZ);
    }

    /**
     * Under a known nexus only the well its cell stands for is tried (0.14.1): the full well from Y0 32 up, as in
     * 0.14.0, the shallower ones under that; a controller at the full well's cell over low ground, where 0.14.0 put
     * every well and where one dug by hand still stands, tries the full well and finds its nexus; any while no nexus
     * is known.
     */
    @Test
    void underAKnownNexusOnlyTheWellOfItsCellIsTried() {
        int[][] cases = { { 79, 26 }, { 32, 26 }, { 28, 20 }, { 22, 14 }, { 16, 8 } };
        for (int[] k : cases) {
            com.fluxecho.nexus.TileNexus n = nexus(k[0]);
            TileRoot r = under(n, rootCell(n, RootShape.depth(k[0])));
            assertSame(n, r.over(), "Y0 " + k[0]);
            String only = RootShape.well(k[1]).name;
            for (TileMultiblock.Shape s : r.shapes())
                assertEquals(s.name.equals(only), r.tries(s), s.name + " at Y0 " + k[0]);
        }
        com.fluxecho.nexus.TileNexus low = nexus(28);
        TileRoot old = under(low, rootCell(low, RootShape.DEPTH));
        assertSame(low, old.over(), "the full well's cell over low ground");
        for (TileMultiblock.Shape s : old.shapes())
            assertEquals(s.name.equals(RootShape.WELL), old.tries(s), s.name + " at the full depth");
        int[] off = rootCell(low, 20);
        off[1]--;
        TileRoot stray = under(low, off);
        org.junit.jupiter.api.Assertions.assertNull(stray.over(), "a cell of neither depth");
        for (TileMultiblock.Shape s : stray.shapes()) assertTrue(stray.tries(s), s.name + " with no nexus");
        int[] c = low.centre();
        assertArrayEquals(
            rootCell(low, RootShape.DEPTH),
            CityModule.root()
                .formerController(SiteFamily.ROOT_SITE, c[0], c[1], c[2], low.front().offsetX, low.front().offsetZ));
    }

    /**
     * A core placed by hand over low ground (0.14.1) names its well itself once it knows the nexus over it, so
     * StructureLib's projector digs what its check tries (before, the full well, which the check over a nexus at Y0 20
     * refuses); one with no nexus known and a formed one keep the shape they have.
     */
    @Test
    void aCorePlacedByHandNamesItsWellUnderAKnownNexus() {
        com.fluxecho.nexus.TileNexus n = nexus(20);
        TileRoot r = under(n, rootCell(n, 14));
        assertEquals(RootShape.WELL, r.shapeName(), "the first shape until it knows where it stands");
        r.nameShapeHere();
        assertEquals(RootShape.WELL_14, r.shapeName());
        TileRoot lone = root(X, Y, Z, 2);
        lone.nameShapeHere();
        assertEquals(RootShape.WELL, lone.shapeName(), "no nexus known");
        TileRoot formed = new TileRoot() {

            {
                formed = true;
            }
        };
        formed.expectShape(RootShape.WELL_8);
        formed.nameShapeHere();
        assertEquals(RootShape.WELL, formed.shapeName(), "a formed well keeps the shape it stands as");
    }

    /**
     * A formed shallow well covers the box of the shape it stands as only (0.14.1): 8 deep under Y0 16, the full
     * well's box round its controller would reach 17 over the forum, over the nexus, its dais and the grate band, and
     * keep our blocks there from being loose. Unformed, it covers the boxes of all its shapes.
     */
    @Test
    void aFormedShallowWellCoversItsOwnBoxOnly() {
        com.fluxecho.nexus.TileNexus n = nexus(16);
        int[] c = n.centre(), at = rootCell(n, 8);
        boolean[] stands = { false };
        TileRoot r = new TileRoot() {

            @Override
            public boolean formed() {
                return stands[0];
            }
        };
        r.xCoord = at[0];
        r.yCoord = at[1];
        r.zCoord = at[2];
        r.place((UUID) null, null, at[3]);
        r.expectShape(RootShape.WELL_8);
        assertTrue(r.covers(null, c[0], c[1] + 10, c[2]), "unformed: the boxes of every shape");
        stands[0] = true;
        assertFalse(r.covers(null, c[0], c[1], c[2]), "the axis at the base layer");
        assertFalse(r.covers(null, c[0], c[1] + 10, c[2]), "the nexus over it");
        assertTrue(r.covers(null, at[0], at[1], at[2]), "its controller");
        int[] tap = r.slotCell(0);
        assertTrue(r.covers(null, tap[0], tap[1], tap[2]), "a tap");
        assertTrue(r.covers(null, c[0], c[1] - 1, c[2]), "its lid");
    }
}
