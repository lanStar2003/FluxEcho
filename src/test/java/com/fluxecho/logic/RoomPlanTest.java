package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.RoomPlan.Cell;
import com.fluxecho.logic.RoomPlan.Template;

class RoomPlanTest {

    private static final int[][] SIDES = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 },
        { 0, 0, -1 } };

    private static long k(int x, int y, int z) {
        return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
    }

    @Test
    void everyRoomIsClosedAllRound() {
        for (Template t : RoomPlan.TEMPLATES) {
            RoomPlan p = new RoomPlan(t);
            int air = 0;
            for (int x = p.minX - 1; x <= p.maxX + 1; x++)
                for (int y = p.minY - 1; y <= p.maxY + 1; y++) for (int z = p.minZ - 1; z <= p.maxZ + 1; z++) {
                    Cell c = p.cell(x, y, z);
                    boolean inBox = x >= p.minX && x <= p.maxX
                        && y >= p.minY
                        && y <= p.maxY
                        && z >= p.minZ
                        && z <= p.maxZ;
                    if (!inBox) assertEquals(Cell.OUT, c, t.id + " reaches past its box");
                    if (c != Cell.AIR) continue;
                    air++;
                    for (int[] d : SIDES) {
                        Cell n = p.cell(x + d[0], y + d[1], z + d[2]);
                        assertTrue(n == Cell.AIR || n.solid(), t.id + " leaks at " + x + "," + y + "," + z);
                    }
                }
            assertTrue(air > 1000, t.id + " is a room, not a cupboard");
        }
    }

    @Test
    void everyCornerCanBeWalkedToFromTheGate() {
        for (Template t : RoomPlan.TEMPLATES) {
            RoomPlan p = new RoomPlan(t);
            Set<Long> seen = new HashSet<>();
            ArrayDeque<int[]> todo = new ArrayDeque<>();
            todo.add(new int[] { 0, 1, p.gateZ });
            seen.add(k(0, 1, p.gateZ));
            while (!todo.isEmpty()) {
                int[] c = todo.poll();
                for (int[] d : SIDES) {
                    int x = c[0] + d[0], y = c[1] + d[1], z = c[2] + d[2];
                    if (p.inside(x, y, z) && seen.add(k(x, y, z))) todo.add(new int[] { x, y, z });
                }
            }
            int air = 0;
            for (int x = p.minX; x <= p.maxX; x++) for (int y = p.minY; y <= p.maxY; y++)
                for (int z = p.minZ; z <= p.maxZ; z++) if (p.inside(x, y, z)) air++;
            assertEquals(air, seen.size(), t.id + " has a pocket the gate cannot reach");
        }
    }

    @Test
    void theGateStandsOnTheFloorWithRoomAroundIt() {
        for (Template t : RoomPlan.TEMPLATES) {
            RoomPlan p = new RoomPlan(t);
            assertTrue(
                p.cell(0, 0, p.gateZ)
                    .solid(),
                t.id);
            // three wide, four high (the frame's arc floats over the pane), three blocks in front, two behind
            for (int x = -2; x <= 2; x++) for (int y = 1; y <= 5; y++) for (int z = p.gateZ - 3; z <= p.gateZ + 2; z++)
                assertTrue(p.inside(x, y, z), t.id + " is cramped at " + x + "," + y + "," + z);
            assertTrue(
                p.cell(0, 1, p.gateZ + 3)
                    .solid(),
                t.id + ": the wall is right behind the gate");
            GateGeometry.Gate g = p.gate(1000, 64, 2000);
            assertEquals(2, g.facing);
            assertTrue(g.front(1000.5, 2000.5) > 0, "the room's middle is in front of the gate");
        }
    }

    @Test
    void roomsFitTheirCellsAndTheWorld() {
        for (Template t : RoomPlan.TEMPLATES) {
            RoomPlan p = new RoomPlan(t);
            int reach = Math.max(Math.max(-p.minX, p.maxX), Math.max(-p.minZ, p.maxZ));
            // the server's widest view (15 chunks) from inside one room never reaches the next
            assertTrue(reach + 15 * 16 < FoldedZone.SPACING / 2, t.id + " is too wide");
            assertTrue(FoldedZone.FLOOR_Y + p.maxY <= 255, t.id + " is too tall");
        }
    }

    /** Light as Minecraft spreads it: from glowing blocks and down from glass, one less per step through the air. */
    @Test
    void theFloorIsLitEverywhere() {
        for (Template t : RoomPlan.TEMPLATES) {
            RoomPlan p = new RoomPlan(t);
            int w = p.maxX - p.minX + 1, h = p.maxY - p.minY + 1, d = p.maxZ - p.minZ + 1;
            int[] light = new int[w * h * d];
            ArrayDeque<int[]> todo = new ArrayDeque<>();
            for (int x = p.minX; x <= p.maxX; x++) for (int z = p.minZ; z <= p.maxZ; z++) {
                // straight down from the sky through a glass pane
                int top = p.maxY;
                while (top > 0 && !p.inside(x, top, z) && p.cell(x, top, z) != Cell.GLASS) top--;
                boolean sky = p.cell(x, top, z) == Cell.GLASS;
                for (int y = p.minY; y <= p.maxY; y++) {
                    Cell c = p.cell(x, y, z);
                    if (c.light() > 0)
                        for (int[] s : SIDES) seed(p, light, todo, x + s[0], y + s[1], z + s[2], c.light() - 1);
                    if (sky && y < top && p.inside(x, y, z)) seed(p, light, todo, x, y, z, 15);
                }
            }
            while (!todo.isEmpty()) {
                int[] c = todo.poll();
                int v = light[index(p, c[0], c[1], c[2])] - 1;
                if (v > 0) for (int[] s : SIDES) seed(p, light, todo, c[0] + s[0], c[1] + s[1], c[2] + s[2], v);
            }
            int darkest = 15;
            for (int x = p.minX; x <= p.maxX; x++) for (int z = p.minZ; z <= p.maxZ; z++)
                if (p.inside(x, 1, z)) darkest = Math.min(darkest, light[index(p, x, 1, z)]);
            assertTrue(darkest >= 8, t.id + ": the darkest spot on the floor has light " + darkest);
        }
    }

    private static int index(RoomPlan p, int x, int y, int z) {
        int w = p.maxX - p.minX + 1, h = p.maxY - p.minY + 1;
        return ((z - p.minZ) * h + (y - p.minY)) * w + (x - p.minX);
    }

    private static void seed(RoomPlan p, int[] light, ArrayDeque<int[]> todo, int x, int y, int z, int v) {
        if (!p.inside(x, y, z)) return;
        int i = index(p, x, y, z);
        if (light[i] >= v) return;
        light[i] = v;
        todo.add(new int[] { x, y, z });
    }

    @Test
    void glassRoomsHaveSkyAndOthersDoNot() {
        for (Template t : RoomPlan.TEMPLATES) {
            RoomPlan p = new RoomPlan(t);
            int glass = 0, lights = 0, pillars = 0;
            for (int x = p.minX; x <= p.maxX; x++)
                for (int y = p.minY; y <= p.maxY; y++) for (int z = p.minZ; z <= p.maxZ; z++) {
                    Cell c = p.cell(x, y, z);
                    if (c == Cell.GLASS) glass++;
                    if (c == Cell.LIGHT) lights++;
                    if (c == Cell.PILLAR) pillars++;
                }
            if (t.glass) assertTrue(glass > 200, t.id + " has a glass roof");
            else assertEquals(0, glass, t.id + " has no glass");
            assertTrue(lights > 0, t.id + " has lights");
            assertTrue(pillars > 0, t.id + " has pillars");
        }
    }

    @Test
    void templatesCycleAndUnknownIdsFallBack() {
        assertEquals(RoomPlan.DEFAULT, RoomPlan.template("no such room").id);
        String id = RoomPlan.DEFAULT;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < RoomPlan.TEMPLATES.size(); i++) {
            assertTrue(seen.add(id), "each once");
            id = RoomPlan.next(id).id;
        }
        assertEquals(RoomPlan.DEFAULT, id, "and round again");
        assertFalse(new RoomPlan("gallery").inside(0, 0, 0), "the floor is not air");
    }
}
