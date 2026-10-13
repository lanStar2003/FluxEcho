package com.fluxecho.city;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.Test;

import com.fluxecho.frame.Formed;
import com.fluxecho.logic.Blueprint;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.DomeGeneratorShape;
import com.fluxecho.logic.SiteFamily;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.nexus.TileNexus;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;

/**
 * The Echo Dome Generator as its tile sees it in the world (0.12.0), for every facing: both blueprints sit where
 * StructureLib's facing math looks for them, the pod's frame is its own (the blueprint reaches 39 blocks under it for
 * the legs), the ward consoles are units and not the controller's console, the legs and nodes dissolve and let players
 * through, it tries the floating shape only over a nexus of phase IV, and a generator the campus builder raised has its
 * frame where the plan put the blocks; since 0.14.1 its moored shapes at the lower sky datums are appended after the
 * two, and a generator named one of them takes that frame.
 */
class TileDomeGeneratorTest {

    private static final int X = 300, Y = 110, Z = -45;

    private static TileDomeGenerator gen(int x, int y, int z, int facing) {
        TileDomeGenerator d = new TileDomeGenerator();
        d.xCoord = x;
        d.yCoord = y;
        d.zCoord = z;
        d.place((UUID) null, null, facing);
        return d;
    }

    @Test
    void theBlueprintsMapCellsAsStructureLibDoes() {
        int[] out = new int[3];
        for (Blueprint b : new Blueprint[] { DomeGeneratorShape.MOORED_BP, DomeGeneratorShape.FLOATING_BP }) {
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
    }

    @Test
    void theControllerTheConsoleAndTheWards() {
        for (int facing = 2; facing <= 5; facing++) {
            TileDomeGenerator d = gen(X, Y, Z, facing);
            assertArrayEquals(new int[] { 10, 2, 6 }, d.local(X, Y, Z));
            int[] k = d.worldOf(10, 1, 6);
            assertArrayEquals(new int[] { X, Y - 1, Z }, k);
            assertTrue(d.consoleAt(k[0], k[1], k[2]), "the console stand opens the window");
            int[] ward = d.worldOf(16, 1, 10);
            assertFalse(d.consoleAt(ward[0], ward[1], ward[2]), "a ward's console is its unit's");
            assertEquals(0, d.unitAt(ward[0], ward[1], ward[2]));
            assertEquals(7, d.unitCount());
            assertEquals(
                7,
                d.samples()
                    .getSlots());
            for (int s = 0; s < 7; s++) {
                int[] p = d.slotCell(s);
                assertEquals(s, d.slotAt(p[0], p[1], p[2]));
                assertEquals(s, d.unitAt(p[0], p[1], p[2]));
            }
            assertEquals(7, d.coreMetaOf());
            assertEquals("dome_generator", d.moduleKey());
            // the pod floor's centre is the module's centre: the lift pad, 40 over the controller's base
            assertArrayEquals(d.worldOf(10, 0, 10), d.centre());
        }
    }

    @Test
    void localIsTheInverseOfWorldOfDownToTheLegs() {
        for (int facing = 2; facing <= 5; facing++) {
            TileDomeGenerator d = gen(X, Y, Z, facing);
            for (int x = -4; x < 25; x += 3) for (int y = -39; y < 10; y += 4) for (int z = -2; z < 23; z += 3) {
                int[] w = d.worldOf(x, y, z);
                assertArrayEquals(new int[] { x, y, z }, d.local(w[0], w[1], w[2]));
            }
            double[] p = d.localPoint(X + 0.5, Y + 0.25, Z + 0.5);
            assertEquals(10.5, p[0], 1e-9);
            assertEquals(2.25, p[1], 1e-9);
            assertEquals(6.5, p[2], 1e-9);
        }
    }

    @Test
    void theLegsAndNodesDissolveAndLetPlayersThrough() {
        TileDomeGenerator d = gen(X, Y, Z, 2);
        assertEquals(Formed.HIDE | Formed.PASS, d.flags('P'));
        assertEquals(Formed.HIDE | Formed.PASS, d.flags('C'));
        assertEquals(0, d.flags('G'));
        assertEquals(0, d.flags('K'));
    }

    @Test
    void mooredFirstFloatingOnlyFromPhaseFour() {
        TileDomeGenerator d = gen(X, Y, Z, 2);
        List<TileMultiblock.Shape> shapes = d.shapes();
        // 0.12's two shapes keep their names and blueprints, so every pod that formed still forms; the moored shapes
        // at the lower sky datums are appended (0.14.1)
        assertEquals(5, shapes.size());
        assertEquals(DomeGeneratorShape.MOORED, shapes.get(0).name);
        assertEquals(DomeGeneratorShape.FLOATING, shapes.get(1).name);
        assertEquals(DomeGeneratorShape.MOORED_36, shapes.get(2).name);
        assertEquals(DomeGeneratorShape.MOORED_32, shapes.get(3).name);
        assertEquals(DomeGeneratorShape.MOORED_28, shapes.get(4).name);
        assertSame(DomeGeneratorShape.MOORED_BP, shapes.get(0).bp);
        assertSame(DomeGeneratorShape.FLOATING_BP, shapes.get(1).bp);
        for (int i = 2; i < 5; i++)
            assertSame(DomeGeneratorShape.moored(shapes.get(i).name).blueprint, shapes.get(i).bp);
        assertTrue(d.tries(shapes.get(0)));
        for (int i = 2; i < 5; i++) assertTrue(d.tries(shapes.get(i)), "any datum while no nexus is known");
        assertFalse(d.tries(shapes.get(1)), "no nexus of phase IV under it");
        assertEquals(DomeGeneratorShape.MOORED, d.shapeName());
        // the nexus preview stands it floating from phase IV
        d.previewPhase(4);
        assertEquals(DomeGeneratorShape.FLOATING, d.shapeName());
        d.previewPhase(2);
        assertEquals(DomeGeneratorShape.MOORED, d.shapeName());
    }

    /**
     * Over high ground (0.14.1): the builder names the moored shape of the campus's sky datum, and the tile takes its
     * frame, its build and its datum from it; the tile of a generator the builder raised there has its frame where the
     * plan put every block, the legs as short as the datum.
     */
    @Test
    void aGeneratorOverHighGroundStandsAtItsDatum() {
        int cx = 40, cz = -80;
        CampusPlan campus = new CampusPlan(7L);
        for (int y0 : new int[] { 205, 210, 214, 222 }) {
            int f = DomeGeneratorShape.datum(y0);
            com.fluxecho.campus.ModuleSpec spec = CityModule.domeGenerator();
            assertEquals(DomeGeneratorShape.moor(f).name, spec.shapeFor(y0));
            int site = SiteFamily.ZENITH_SITE;
            int[] c = DomeGeneratorShape.controller(site, cx, y0, cz, 0, -1);
            TileDomeGenerator d = gen(c[0], c[1], c[2], c[3]);
            assertEquals(DomeGeneratorShape.MOORED, d.shapeName(), "the first shape until it is named one");
            d.expectShape(spec.shapeFor(y0));
            assertEquals(DomeGeneratorShape.moor(f).name, d.shapeName());
            assertSame(DomeGeneratorShape.moor(f).build, d.build());
            assertEquals(f, d.datum());
            d.expectShape("no such shape");
            d.expectShape(null);
            assertEquals(DomeGeneratorShape.moor(f).name, d.shapeName(), "unknown names are ignored");
            assertArrayEquals(new int[] { 10, 2, 6 }, d.local(c[0], c[1], c[2]));
            for (int y = -(f - 1); y < 10; y += 3) {
                int[] w = d.worldOf(7, y, 9);
                assertArrayEquals(new int[] { 7, y, 9 }, d.local(w[0], w[1], w[2]));
            }
            BuildPlan.Plan p = DomeGeneratorShape.plan(campus, site, cx, y0, cz, 0, -1);
            int legs = 0;
            for (BuildPlan.Step s : p.steps) {
                int[] l = d.local(s.x, s.y, s.z);
                if (s.stage == DomeGeneratorShape.Z_LEGS) {
                    assertTrue(l[1] < 0 && l[1] >= -(f - 1), "a leg under the pod");
                    legs++;
                } else assertTrue(l[1] >= 0 && l[1] < DomeGeneratorShape.HEIGHT);
                if (s.group != BuildPlan.NO_GROUP) assertEquals(s.group, d.unitAt(s.x, s.y, s.z));
            }
            assertEquals(6 * (f - 1), legs);
            assertArrayEquals(new int[] { cx, y0 + f, cz }, d.centre(), "the pad at the datum");
        }
        // the floating shape and the full legs keep the full legs' frame
        TileDomeGenerator d = gen(X, Y, Z, 2);
        assertSame(DomeGeneratorShape.BUILD, d.build());
        assertEquals(DomeGeneratorShape.FLOOR_UP, d.datum());
    }

    /** The tile of a generator the builder raised has its frame where the plan put every block. */
    @Test
    void aBuiltGeneratorsFrameIsThePlans() {
        int cx = 40, y0 = 64, cz = -80;
        CampusPlan campus = new CampusPlan(7L);
        for (int[] f : new int[][] { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } }) {
            int site = SiteFamily.ZENITH_SITE;
            int[] c = DomeGeneratorShape.controller(site, cx, y0, cz, f[0], f[1]);
            TileDomeGenerator d = gen(c[0], c[1], c[2], c[3]);
            BuildPlan.Plan p = DomeGeneratorShape.plan(campus, site, cx, y0, cz, f[0], f[1]);
            int units = 0, legs = 0;
            for (BuildPlan.Step s : p.steps) {
                int[] l = d.local(s.x, s.y, s.z);
                if (s.stage == DomeGeneratorShape.Z_LEGS) {
                    assertTrue(l[1] < 0, "a leg under the pod");
                    legs++;
                } else assertTrue(l[1] >= 0 && l[1] < DomeGeneratorShape.HEIGHT);
                if (s.group == BuildPlan.NO_GROUP) continue;
                assertEquals(s.group, d.unitAt(s.x, s.y, s.z));
                units++;
            }
            assertEquals(14, units);
            assertEquals(234, legs);
            int[] centre = d.centre();
            assertArrayEquals(new int[] { cx, y0 + 40, cz }, centre, "the pad over the nexus's centre");
        }
    }

    /** A formed nexus whose base layer is {@code y0}, facing north. */
    private static TileNexus nexus(int y0) {
        TileNexus n = new TileNexus() {

            @Override
            public boolean formed() {
                return true;
            }
        };
        n.xCoord = 40;
        n.yCoord = y0 + 2;
        n.zCoord = -80;
        n.place((UUID) null, null, 2);
        assertEquals(y0, n.centre()[1]);
        return n;
    }

    /** A generator tile at a cell {x, y, z, facing}, which finds the nexus under it among that one nexus. */
    private static TileDomeGenerator over(TileNexus n, int[] at) {
        TileDomeGenerator d = new TileDomeGenerator() {

            @Override
            public TileNexus under() {
                return under(Collections.singletonList(n));
            }
        };
        d.xCoord = at[0];
        d.yCoord = at[1];
        d.zCoord = at[2];
        d.place((UUID) null, null, at[3]);
        return d;
    }

    private static int[] zenith(TileNexus n, int datum) {
        int[] c = n.centre();
        return DomeGeneratorShape.moor(datum)
            .controller(SiteFamily.ZENITH_SITE, c[0], c[1], c[2], n.front().offsetX, n.front().offsetZ);
    }

    /**
     * Over a known nexus only the moored shape its cell stands for is tried (0.14.1): the full legs up to Y0 203, as
     * in 0.14.0, and the lower datums over that; a controller at the full legs' cell over high ground, where 0.14.0
     * put every pod and where one built by hand still stands, tries the full legs and finds its nexus; floating only
     * from phase IV; any datum while no nexus is known.
     */
    @Test
    void overAKnownNexusOnlyTheShapeOfItsCellIsTried() {
        int[][] cases = { { 79, 40 }, { 203, 40 }, { 205, 36 }, { 210, 32 }, { 214, 28 } };
        for (int[] k : cases) {
            TileNexus n = nexus(k[0]);
            TileDomeGenerator d = over(n, zenith(n, DomeGeneratorShape.datum(k[0])));
            assertSame(n, d.under(), "Y0 " + k[0]);
            String only = DomeGeneratorShape.moor(k[1]).name;
            for (TileMultiblock.Shape s : d.shapes())
                assertEquals(s.name.equals(only), d.tries(s), s.name + " at Y0 " + k[0]);
            n.regrow(4);
            for (TileMultiblock.Shape s : d.shapes()) assertEquals(
                s.name.equals(only) || s.name.equals(DomeGeneratorShape.FLOATING),
                d.tries(s),
                s.name + " at Y0 " + k[0] + ", phase IV");
        }
        TileNexus high = nexus(205);
        TileDomeGenerator old = over(high, zenith(high, DomeGeneratorShape.FLOOR_UP));
        assertSame(high, old.under(), "the full legs' cell over high ground");
        for (TileMultiblock.Shape s : old.shapes())
            assertEquals(s.name.equals(DomeGeneratorShape.MOORED), old.tries(s), s.name + " on the full legs");
        int[] off = zenith(high, 36);
        off[1]++;
        TileDomeGenerator stray = over(high, off);
        assertNull(stray.under(), "a cell of neither datum");
        for (TileMultiblock.Shape s : stray.shapes())
            assertEquals(!s.name.equals(DomeGeneratorShape.FLOATING), stray.tries(s), s.name + " with no nexus");
        // the module spec docks it on the zenith at either cell
        int[] c = high.centre();
        assertArrayEquals(
            zenith(high, DomeGeneratorShape.FLOOR_UP),
            CityModule.domeGenerator()
                .formerController(
                    SiteFamily.ZENITH_SITE,
                    c[0],
                    c[1],
                    c[2],
                    high.front().offsetX,
                    high.front().offsetZ));
    }

    /**
     * A core placed by hand over high ground (0.14.1) names its shape itself once it knows the nexus under it, so
     * StructureLib's projector builds what its check tries: before, only the builder named it, and the projector
     * built the full legs, which the check over a nexus at Y0 205 refuses. A floating pod over a phase-IV nexus, one
     * with no nexus known, and a formed one keep the shape they have.
     */
    @Test
    void aCorePlacedByHandNamesItsShapeOverAKnownNexus() {
        TileNexus n = nexus(205);
        TileDomeGenerator d = over(n, zenith(n, 36));
        assertEquals(DomeGeneratorShape.MOORED, d.shapeName(), "the first shape until it knows where it stands");
        d.nameShapeHere();
        assertEquals(DomeGeneratorShape.MOORED_36, d.shapeName());
        TileDomeGenerator lone = gen(X, Y, Z, 2);
        lone.nameShapeHere();
        assertEquals(DomeGeneratorShape.MOORED, lone.shapeName(), "no nexus known");
        n.regrow(4);
        TileDomeGenerator floating = over(n, zenith(n, 36));
        floating.previewPhase(4);
        assertEquals(DomeGeneratorShape.FLOATING, floating.shapeName());
        floating.nameShapeHere();
        assertEquals(DomeGeneratorShape.FLOATING, floating.shapeName(), "it may float there");
        TileDomeGenerator formed = new TileDomeGenerator() {

            {
                formed = true;
            }
        };
        formed.expectShape(DomeGeneratorShape.MOORED_32);
        formed.nameShapeHere();
        assertEquals(DomeGeneratorShape.MOORED, formed.shapeName(), "a formed pod keeps the shape it stands as");
    }

    /**
     * A formed pod covers the box of the shape it stands as only (0.14.1): moored at 28 over Y0 215, the full legs'
     * box round its controller would reach 11 under the forum, over the nexus and its dais, and keep our blocks there
     * from being loose. Unformed, it covers the boxes of all its shapes.
     */
    @Test
    void aFormedLowerPodCoversItsOwnBoxOnly() {
        TileNexus n = nexus(215);
        int[] c = n.centre(), at = zenith(n, 28);
        boolean[] stands = { false };
        TileDomeGenerator d = new TileDomeGenerator() {

            @Override
            public boolean formed() {
                return stands[0];
            }
        };
        d.xCoord = at[0];
        d.yCoord = at[1];
        d.zCoord = at[2];
        d.place((UUID) null, null, at[3]);
        d.expectShape(DomeGeneratorShape.MOORED_28);
        assertTrue(d.covers(null, c[0], c[1] - 5, c[2]), "unformed: the boxes of every shape");
        stands[0] = true;
        assertFalse(d.covers(null, c[0], c[1] - 5, c[2]), "the axis under the forum");
        assertTrue(d.covers(null, at[0], at[1], at[2]), "its controller");
        int[] p = DomeGeneratorShape.moor(28)
            .legPoints()
            .get(0);
        int[] foot = d.worldOf(p[0], p[1], p[2]);
        assertEquals(c[1] + 1, foot[1], "a leg's foot on the forum");
        assertTrue(d.covers(null, foot[0], foot[1], foot[2]), "a leg's foot");
        int[] ward = d.slotCell(0);
        assertTrue(d.covers(null, ward[0], ward[1], ward[2]), "a ward");
    }
}
