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
import com.fluxecho.logic.DomeGeneratorShape;
import com.fluxecho.logic.SiteFamily;
import com.fluxecho.nexus.TileMultiblock;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;

/**
 * The Echo Dome Generator as its tile sees it in the world (0.12.0), for every facing: both blueprints sit where
 * StructureLib's facing math looks for them, the pod's frame is its own (the blueprint reaches 39 blocks under it for
 * the legs), the ward consoles are units and not the controller's console, the legs and nodes dissolve and let players
 * through, it tries the floating shape only over a nexus of phase IV, and a generator the campus builder raised has its
 * frame where the plan put the blocks.
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
        assertEquals(2, shapes.size());
        assertEquals(DomeGeneratorShape.MOORED, shapes.get(0).name);
        assertEquals(DomeGeneratorShape.FLOATING, shapes.get(1).name);
        assertTrue(d.tries(shapes.get(0)));
        assertFalse(d.tries(shapes.get(1)), "no nexus of phase IV under it");
        assertEquals(DomeGeneratorShape.MOORED, d.shapeName());
        // the nexus preview stands it floating from phase IV
        d.previewPhase(4);
        assertEquals(DomeGeneratorShape.FLOATING, d.shapeName());
        d.previewPhase(2);
        assertEquals(DomeGeneratorShape.MOORED, d.shapeName());
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
}
