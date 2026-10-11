package com.fluxecho.city;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.Test;

import com.fluxecho.frame.Formed;
import com.fluxecho.logic.Blueprint;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.EcoDomeShape;
import com.fluxecho.logic.UnitLayout;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;

/**
 * The Eco Dome as its tile sees it in the world, for every facing: the blueprint's cells sit where StructureLib's own
 * facing math looks for them (the trunk's spiral is not mirrored, so this must hold exactly), every planter cell names
 * its planter, every socket its slot and back, the console stand under the controller opens the window, and a dome
 * the campus builder raised on a site has its tile's frame where the plan put the blocks.
 */
class TileEcoDomeTest {

    private static final int X = -420, Y = 72, Z = 1310;

    private static TileEcoDome dome(int x, int y, int z, int facing) {
        TileEcoDome d = new TileEcoDome();
        d.xCoord = x;
        d.yCoord = y;
        d.zCoord = z;
        d.place((UUID) null, null, facing);
        return d;
    }

    /** {@link Blueprint#world} is StructureLib's mapping for a front with no rotation and no flip. */
    @Test
    void theBlueprintMapsCellsAsStructureLibDoes() {
        Blueprint b = EcoDomeShape.BLUEPRINT;
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
    void theControllerAndItsConsole() {
        for (int facing = 2; facing <= 5; facing++) {
            TileEcoDome d = dome(X, Y, Z, facing);
            assertArrayEquals(new int[] { 13, 2, 0 }, d.local(X, Y, Z));
            int[] k = d.worldOf(13, 1, 0);
            assertArrayEquals(new int[] { X, Y - 1, Z }, k, "the stand is right under the controller");
            assertTrue(d.consoleAt(k[0], k[1], k[2]));
            int[] beside = d.worldOf(12, 1, 0);
            assertFalse(d.consoleAt(beside[0], beside[1], beside[2]));
            assertEquals(12, d.unitCount());
            assertEquals(
                36,
                d.samples()
                    .getSlots());
            assertEquals(0, d.coreMetaOf());
            assertEquals("eco_dome", d.moduleKey());
        }
    }

    @Test
    void everySocketIsItsSlotAndEveryCellItsPlanter() {
        UnitLayout layout = EcoDomeShape.UNITS;
        for (int facing = 2; facing <= 5; facing++) {
            TileEcoDome d = dome(X, Y, Z, facing);
            Set<Long> sockets = new HashSet<>();
            for (int s = 0; s < layout.slots(); s++) {
                int[] p = d.slotCell(s);
                assertTrue(sockets.add(Formed.key(p[0], p[1], p[2])), "slot " + s + " shares its socket");
                assertEquals(s, d.slotAt(p[0], p[1], p[2]), "facing " + facing + " slot " + s);
                assertEquals(layout.unitOfSlot(s), d.unitAt(p[0], p[1], p[2]));
                int[] l = d.local(p[0], p[1], p[2]);
                assertEquals('o', EcoDomeShape.cell(l[0], l[1], l[2]));
            }
            for (int u = 0; u < d.unitCount(); u++) {
                List<int[]> cells = d.unitCells(u);
                assertEquals(
                    d.unitName(u)
                        .startsWith("U-") ? 13 : 15,
                    cells.size(),
                    "a planter is a 3 x 5 bed (the gallery's leave the cornice)");
                int sockets3 = 0;
                for (int[] p : cells) {
                    assertEquals(u, d.unitAt(p[0], p[1], p[2]));
                    if (d.slotAt(p[0], p[1], p[2]) >= 0) sockets3++;
                }
                assertEquals(3, sockets3);
                assertArrayEquals(new int[] { 3 * u, 3 }, d.unitSlots(u));
            }
            assertEquals(-1, d.unitAt(X, Y, Z), "the controller is no planter");
            assertEquals(-1, d.slotAt(X, Y, Z));
            assertNull(d.slotCell(36));
        }
    }

    /** Formed, the trunk is not drawn but keeps its collision; the seat over it lets players through as well. */
    @Test
    void theTrunkHidesButStaysSolid() {
        TileEcoDome d = dome(X, Y, Z, 2);
        assertEquals(Formed.HIDE, d.flags('C'), "the trunk under the islands' decks");
        assertEquals(Formed.HIDE | Formed.PASS, d.flags('Z'));
        assertEquals(0, d.flags('u'));
        assertEquals(0, d.flags('G'));
    }

    @Test
    void localIsTheInverseOfWorldOf() {
        for (int facing = 2; facing <= 5; facing++) {
            TileEcoDome d = dome(X, Y, Z, facing);
            for (int x = -2; x < 29; x += 3)
                for (int y = 0; y < EcoDomeShape.HEIGHT; y += 2) for (int z = -3; z < 37; z += 4) {
                    int[] w = d.worldOf(x, y, z);
                    assertArrayEquals(new int[] { x, y, z }, d.local(w[0], w[1], w[2]));
                }
            // the shape's right (+x) and its depth (+z) as world directions
            int[] right = d.worldOf(14, 0, 0), origin = d.worldOf(13, 0, 0), back = d.worldOf(13, 0, 1);
            assertArrayEquals(new int[] { right[0] - origin[0], right[2] - origin[2] }, d.worldDir(1, 0));
            assertArrayEquals(new int[] { back[0] - origin[0], back[2] - origin[2] }, d.worldDir(0, 1));
        }
    }

    /** The tile of a dome the builder raised has its frame where the plan put every block. */
    @Test
    void aBuiltDomesFrameIsThePlans() {
        int cx = 40, y0 = 64, cz = -80;
        CampusPlan campus = new CampusPlan(7L);
        for (int[] f : new int[][] { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } }) for (int site : EcoDomeShape.SITES) {
            int[] c = EcoDomeShape.controller(site, cx, y0, cz, f[0], f[1]);
            TileEcoDome d = dome(c[0], c[1], c[2], c[3]);
            BuildPlan.Plan p = EcoDomeShape.plan(campus, site, cx, y0, cz, f[0], f[1]);
            int units = 0;
            for (BuildPlan.Step s : p.steps) {
                if (s.group == BuildPlan.NO_GROUP) continue;
                assertEquals(s.group, d.unitAt(s.x, s.y, s.z), "site " + site + " " + s);
                units++;
            }
            assertEquals(10 * 15 + 2 * 13, units);
            int[] centre = d.worldOf(13, 0, 17);
            assertArrayEquals(
                CampusPlan.moduleCentre(site, EcoDomeShape.DEPTH),
                CampusPlan.toLocal(centre[0] - cx, centre[2] - cz, f[0], f[1]));
        }
    }
}
