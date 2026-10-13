package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The Echo Hunting Ground's drawing (0.13.0, CITY.md §4.5 Visuals): the velarium's canopy closes over the bowl in the
 * air over the wall, its ring drawn where the dissolved ring segments hung; the scan line sweeps the arena's open grid
 * every six seconds, a quarter turn on each time, its trail on the lit tiles behind it; the consoles' silhouettes stand
 * in the air over their sockets, a kind a mob, in their egg colours or neutral; every motion repeats; the ground keeps
 * CITY.md §1.3's budgets.
 */
class HuntingLookTest {

    private static final CityBuild B = HuntingGroundShape.BUILD;
    private static final double AXIS = HuntingLook.AXIS;

    @Test
    void theCanopyClosesOverTheBowlClearOfTheWall() {
        String open = LookCells.OPEN + HuntingGroundShape.DISSOLVES;
        for (int tier : new int[] { ModuleLook.NEAR, ModuleLook.MID }) {
            List<double[][]> hexes = HuntingLook.hexes(tier);
            boolean overTheAxis = false;
            for (double[][] h : hexes) {
                double[] mid = h[0];
                assertTrue(mid[2] >= 0 && mid[2] < 1, "its middle under the canopy");
                assertTrue(mid[3] >= 0 && mid[3] < 1, "a shimmer's share");
                if (Math.abs(mid[0] - AXIS) < 1e-9 && Math.abs(mid[1] - AXIS) < 1e-9) overTheAxis = true;
                for (int k = 1; k <= 6; k++) {
                    double[] p = h[k];
                    assertTrue(HuntingLook.outline(p[0] - AXIS, p[2] - AXIS) <= 1 + 1e-9, "within the rim");
                    assertEquals(HuntingLook.canopyY(p[0], p[2]), p[1], 1e-9, "on the dome");
                    assertTrue(
                        p[1] >= HuntingLook.EDGE_Y - 1e-9 && p[1] <= HuntingLook.EDGE_Y + HuntingLook.RISE + 1e-9);
                    assertTrue(p[1] > HuntingGroundShape.WALL_TOP + 1, "over the cornice's top");
                    double room = LookCells.clearance(B, p[0], p[1], p[2], open, 1.5);
                    assertTrue(room >= 0.4, "clear of the wall at " + p[0] + "," + p[1] + "," + p[2] + ": " + room);
                }
            }
            assertTrue(overTheAxis, "a hexagon over the emitter");
        }
        assertTrue(
            HuntingLook.hexes(ModuleLook.NEAR)
                .size()
                > 2 * HuntingLook.hexes(ModuleLook.MID)
                    .size(),
            "smaller hexagons near");
        // far: the rim's corners on the octagon through the ring's middle, the middle at the dome's top
        assertEquals(HuntingLook.EDGE_Y + HuntingLook.RISE, HuntingLook.canopyY(AXIS, AXIS), 1e-9);
        for (double[] c : HuntingLook.fan()) assertEquals(1, HuntingLook.outline(c[0] - AXIS, c[1] - AXIS), 1e-9);
        // high over everyone: the top terraces' stand (4) and a player's height
        assertTrue(HuntingLook.EDGE_Y > 4 + 1.8 + 2);
    }

    @Test
    void theRingIsDrawnWhereTheVelariumHung() {
        // every dissolved ring segment lies between the ring's two octagons
        int segments = 0;
        for (int x = 0; x < HuntingGroundShape.WIDTH; x++) for (int z = 0; z < HuntingGroundShape.DEPTH; z++) {
            if (B.cell(x, HuntingGroundShape.VELARIUM_Y, z) != 'R') continue;
            segments++;
            double dx = x + 0.5 - AXIS, dz = z + 0.5 - AXIS;
            assertTrue(norm(dx, dz, HuntingLook.RING_IN_FLAT, HuntingLook.RING_IN_CHAMFER) >= 1 - 1e-9, "outside in");
            assertTrue(norm(dx, dz, HuntingLook.RING_OUT_FLAT, HuntingLook.RING_OUT_CHAMFER) <= 1 + 1e-9, "inside out");
        }
        assertTrue(segments > 100);
        // and the drawn lines run through the air or the dissolved segments, at their middle height
        String open = LookCells.OPEN + HuntingGroundShape.DISSOLVES;
        for (boolean outer : new boolean[] { false, true }) {
            double[][] o = HuntingLook.ring(outer);
            for (int i = 0; i < 8; i++) {
                double[] a = o[i], b = o[(i + 1) % 8];
                for (int s = 0; s <= 40; s++) {
                    double x = a[0] + (b[0] - a[0]) * s / 40, z = a[1] + (b[1] - a[1]) * s / 40;
                    assertTrue(
                        open.indexOf(LookCells.at(B, x, HuntingLook.EDGE_Y, z)) >= 0,
                        "the ring's line in the air at " + x + "," + z);
                }
            }
        }
        assertEquals(HuntingGroundShape.VELARIUM_Y + 0.5, HuntingLook.EDGE_Y, 0);
    }

    /** The octagon norm of a point for flat sides {@code flat} out and chamfers {@code |x| + |z| = chamfer}. */
    private static double norm(double dx, double dz, double flat, double chamfer) {
        double a = Math.abs(dx), b = Math.abs(dz);
        return Math.max(Math.max(a, b) / flat, (a + b) / chamfer);
    }

    @Test
    void theHexagonsTileTheCanopy() {
        List<double[][]> hexes = HuntingLook.hexes(ModuleLook.NEAR);
        List<double[]> edges = HuntingLook.hexEdges();
        assertTrue(hexes.size() > 60, hexes.size() + " hexagons");
        // shared edges are drawn once: fewer than six a hexagon, more than three
        assertTrue(edges.size() < 6 * hexes.size() && edges.size() > 3 * hexes.size(), edges.size() + " edges");
        Set<String> seen = new HashSet<>();
        for (double[] e : edges) {
            String a = Math.round(e[0] * 1000) + "," + Math.round(e[2] * 1000),
                b = Math.round(e[3] * 1000) + "," + Math.round(e[5] * 1000);
            assertTrue(!a.equals(b), "an edge has two ends");
            assertTrue(seen.add(a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a), "each edge once");
        }
        // an inner hexagon is regular: its corners its size from its middle
        for (double[][] h : hexes) {
            if (h[0][2] > 0.6) continue;
            for (int k = 1; k <= 6; k++)
                assertEquals(HuntingLook.HEX[0], Math.hypot(h[k][0] - h[0][0], h[k][2] - h[0][1]), 1e-9);
        }
    }

    @Test
    void theScanSweepsEverySixSeconds() {
        assertEquals(6 * 20, HuntingLook.SCAN_T);
        boolean cut = false;
        for (int c = 0; c < 4; c++) {
            int[] way = HuntingLook.scanWay(c), next = HuntingLook.scanWay(c + 1);
            assertArrayEquals(new int[] { -way[1], way[0] }, next, "a quarter turn each sweep");
            for (int j = 0; j < HuntingLook.SCAN_T; j++) {
                double t = c * HuntingLook.SCAN_T + j + 0.37;
                List<double[]> pieces = HuntingLook.scanPieces(t);
                assertEquals(HuntingLook.sweeping(t), !pieces.isEmpty(), "drawn while it sweeps, at " + t);
                if (pieces.size() > 1) cut = true;
                assertTrue(pieces.size() <= HuntingLook.scanPiecesMost());
                for (double[] p : pieces) {
                    if (way[0] == 0) assertEquals(p[1], p[3], 0, "across the way");
                    else assertEquals(p[0], p[2], 0, "across the way");
                    for (int s = 1; s < 40; s++) {
                        double x = p[0] + (p[2] - p[0]) * s / 40, z = p[1] + (p[3] - p[1]) * s / 40;
                        char floor = LookCells.at(B, x, 0, z);
                        assertTrue(floor == ':' || floor == '+', "on the arena's grid: '" + floor + "'");
                        assertEquals(ShapeKit.NONE, LookCells.at(B, x, 1, z), "nothing standing on it");
                    }
                }
                List<double[]> again = HuntingLook.scanPieces(t + 4 * HuntingLook.SCAN_T);
                assertEquals(pieces.size(), again.size());
                for (int i = 0; i < pieces.size(); i++) assertArrayEquals(pieces.get(i), again.get(i), 1e-9);
            }
            // it crosses the whole grid in a sweep
            assertEquals(-HuntingLook.ARENA_EDGE, HuntingLook.scanAt(c * HuntingLook.SCAN_T), 1e-9);
            assertEquals(
                HuntingLook.ARENA_EDGE,
                HuntingLook.scanAt(c * HuntingLook.SCAN_T + HuntingLook.SWEEP_T - 1e-9),
                1e-6);
        }
        assertTrue(cut, "the emitter and the pedestals cut the line");
    }

    @Test
    void theTrailLightsTheLitTilesBehindTheLine() {
        List<int[]> lit = HuntingLook.lit();
        assertTrue(lit.size() > 30, lit.size() + " lit tiles");
        for (int[] c : lit) {
            assertEquals('+', B.cell(c[0], 0, c[1]));
            assertEquals(ShapeKit.NONE, B.cell(c[0], 1, c[1]));
            assertTrue(
                ShapeKit.onorm(c[0] - HuntingGroundShape.MID, c[1] - HuntingGroundShape.MID)
                    <= HuntingGroundShape.ARENA_R);
        }
        double most = 0;
        for (int j = 0; j < 4 * HuntingLook.SCAN_T; j++) {
            double t = j + 0.5;
            int[] way = HuntingLook.scanWay((long) Math.floor(t / HuntingLook.SCAN_T));
            for (int[] c : lit) {
                double k = HuntingLook.trail(c[0], c[1], t);
                assertTrue(k >= 0 && k <= 1);
                most = Math.max(most, k);
                if (k > 0) {
                    double along = (c[0] + 0.5 - AXIS) * way[0] + (c[1] + 0.5 - AXIS) * way[1];
                    assertTrue(along <= HuntingLook.scanAt(t), "behind the line, never ahead of it");
                }
            }
        }
        assertTrue(most > 0.9, "brightest right behind the line");
        // between sweeps the trail has faded: nothing lit at the end of a cycle
        for (int[] c : lit) assertEquals(0, HuntingLook.trail(c[0], c[1], HuntingLook.SCAN_T - 1), 0);
    }

    @Test
    void theSilhouettesStandOverTheirConsoles() {
        List<int[]> consoles = HuntingLook.consoles();
        assertEquals(HuntingGroundShape.CONSOLES, consoles.size());
        double tallest = 0;
        for (int k = 0; k < HuntingLook.kinds(); k++) tallest = Math.max(tallest, HuntingLook.height(k));
        for (int i = 0; i < consoles.size(); i++) {
            int[] s = consoles.get(i);
            assertEquals('o', B.cell(s[0], s[1], s[2]), "a socket");
            assertEquals(
                HuntingGroundShape.UNITS.unitOfSlot(HuntingLook.consoleSlot(i)),
                HuntingGroundShape.UNITS.unitOfCell(s[0], s[1], s[2]),
                "its own slot");
            for (int j = 0; j < 60; j++) {
                double[] foot = HuntingLook.silhouetteFoot(i, j * 3.1);
                assertEquals(s[0] + 0.5, foot[0], 0);
                assertEquals(s[2] + 0.5, foot[2], 0);
                assertTrue(foot[1] > s[1] + 1, "over the socket's top");
                double top = foot[1] + tallest * HuntingLook.SIL_SCALE;
                // the silhouette turns about the upright within half its unit: inside the console's column
                for (int y = s[1] + 1; y <= (int) Math.floor(top); y++)
                    assertEquals(ShapeKit.NONE, B.cell(s[0], y, s[2]), "the air over the console at y" + y);
                assertTrue(top < HuntingLook.EDGE_Y, "under the canopy");
            }
        }
        assertTrue(0.5 * HuntingLook.SIL_SCALE < 0.5, "within the column whichever way it turns");
        for (int k = 0; k < HuntingLook.kinds(); k++) {
            double[][] rects = HuntingLook.silhouette(k);
            assertTrue(rects.length > 0 && rects.length <= HuntingLook.SIL_RECTS, "kind " + k);
            for (double[] r : rects) {
                assertTrue(r[0] >= -0.5 && r[2] <= 0.5 && r[0] < r[2], "across, kind " + k);
                assertTrue(r[1] >= 0 && r[1] < r[3], "up, kind " + k);
                assertTrue(r[4] == 0 || r[4] == 1, "the egg's main colour or its spots");
            }
            assertTrue(HuntingLook.height(k) <= 1.3 + 1e-9);
        }
    }

    @Test
    void eachMobHasItsKind() {
        assertEquals(HuntingLook.BIPED, HuntingLook.kindOf("Zombie"));
        assertEquals(HuntingLook.BIPED, HuntingLook.kindOf("PigZombie"), "a pigman is no pig");
        assertEquals(HuntingLook.BIPED, HuntingLook.kindOf("Skeleton"));
        assertEquals(HuntingLook.BIPED, HuntingLook.kindOf("Blaze"));
        assertEquals(HuntingLook.CRAWLER, HuntingLook.kindOf("Spider"));
        assertEquals(HuntingLook.CRAWLER, HuntingLook.kindOf("CaveSpider"));
        assertEquals(HuntingLook.CRAWLER, HuntingLook.kindOf("Silverfish"));
        assertEquals(HuntingLook.CRAWLER, HuntingLook.kindOf("Endermite"), "an endermite is no enderman");
        assertEquals(HuntingLook.TALL, HuntingLook.kindOf("Enderman"));
        assertEquals(HuntingLook.CREEPER, HuntingLook.kindOf("Creeper"));
        assertEquals(HuntingLook.BLOB, HuntingLook.kindOf("Slime"));
        assertEquals(HuntingLook.BLOB, HuntingLook.kindOf("LavaSlime"));
        assertEquals(HuntingLook.BLOB, HuntingLook.kindOf("Ghast"));
        assertEquals(HuntingLook.SMALL, HuntingLook.kindOf("Bat"));
        assertEquals(HuntingLook.SMALL, HuntingLook.kindOf("Chicken"));
        assertEquals(HuntingLook.SMALL, HuntingLook.kindOf("Thaumcraft.Wisp"));
        assertEquals(HuntingLook.BEAST, HuntingLook.kindOf("Cow"));
        assertEquals(HuntingLook.BEAST, HuntingLook.kindOf("MushroomCow"));
        assertEquals(HuntingLook.BEAST, HuntingLook.kindOf("Ozelot"));
        assertEquals(HuntingLook.BEAST, HuntingLook.kindOf("EntityHorse"));
        assertEquals(HuntingLook.BEAST, HuntingLook.kindOf("Pig"));
        assertEquals(HuntingLook.BIPED, HuntingLook.kindOf("SomeModsGolemThing"));
        assertEquals(HuntingLook.BIPED, HuntingLook.kindOf(null), "no mob: the neutral figure");
        // a prey imprint names its mob by its tag; a blank one, or anything else, none
        assertEquals("Zombie", HuntingLook.imprintMob(HuntingLook.IMPRINT_ITEM, "Zombie"));
        assertNull(HuntingLook.imprintMob(HuntingLook.IMPRINT_ITEM, null));
        assertNull(HuntingLook.imprintMob(HuntingLook.IMPRINT_ITEM, ""));
        assertNull(HuntingLook.imprintMob("minecraft:stone", "Zombie"));
        assertNull(HuntingLook.imprintMob(null, "Zombie"));
        assertEquals("fluxecho:mob_imprint", HuntingLook.IMPRINT_ITEM, "com.fluxecho.mobs registers it so");
    }

    @Test
    void theMotionsRepeat() {
        for (double t : new double[] { 0, 123.5, 98765.25 }) {
            for (double[][] h : HuntingLook.hexes(ModuleLook.NEAR)) {
                double a = HuntingLook.hexLight(h[0], t);
                assertEquals(a, HuntingLook.hexLight(h[0], t + HuntingLook.CANOPY_T), 1e-9);
                assertTrue(a >= 0.35 - 1e-9 && a <= 1 + 1e-9);
            }
            for (int i = 0; i < HuntingLook.consoles()
                .size(); i++) {
                assertArrayEquals(
                    HuntingLook.silhouetteFoot(i, t),
                    HuntingLook.silhouetteFoot(i, t + HuntingLook.SIL_BOB_T),
                    1e-9);
                assertEquals(HuntingLook.stripe(i, t), HuntingLook.stripe(i, t + HuntingLook.SIL_SCAN_T), 1e-9);
            }
            for (int[] c : HuntingLook.lit()) assertEquals(
                HuntingLook.trail(c[0], c[1], t),
                HuntingLook.trail(c[0], c[1], t + 4 * HuntingLook.SCAN_T),
                1e-9);
        }
    }

    @Test
    void theGroundKeepsItsBudget() {
        // the shared drawing as drawn: the forming sweep through the 25 x 25 box is a plane and four rims of four
        // pieces
        assertEquals(17, ModuleLook.sweepQuads(B));
        assertEquals(17, ModuleLook.baseQuads(B, HuntingGroundShape.UNITS, ModuleLook.FAR));
        int[] budget = { 2000, 300, 64 };
        for (int tier = ModuleLook.NEAR; tier <= ModuleLook.FAR; tier++) {
            int base = ModuleLook.baseQuads(B, HuntingGroundShape.UNITS, tier);
            for (boolean docked : new boolean[] { false, true }) for (boolean on : new boolean[] { false, true }) {
                if (on && !docked) continue;
                int own = HuntingLook.quads(tier, docked, on);
                assertTrue(
                    base + own <= budget[tier],
                    "tier " + tier + (docked ? " docked" : "") + (on ? " powered" : "") + ": " + base + " + " + own);
            }
        }
        // far: the canopy's eight quads and its rim, each side a line cut into pieces of at most 8 blocks
        int rim = HuntingLook.octagonQuads(HuntingLook.fan());
        assertEquals(4 + 4 * 2, rim);
        assertEquals(HuntingLook.FAN_QUADS + rim, HuntingLook.quads(ModuleLook.FAR, true, true));
        assertEquals(HuntingLook.quads(ModuleLook.NEAR, true, false), HuntingLook.quads(ModuleLook.NEAR, true, true));
    }
}
