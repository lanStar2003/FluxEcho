package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.LibraryUnits.Unit;

class LibraryUnitsTest {

    private static final int W = ArchiveShape.WIDTH, H = ArchiveShape.HEIGHT, D = ArchiveShape.DEPTH;

    @Test
    void countsAndStoreys() {
        assertEquals(78, LibraryUnits.UNITS.size());
        assertEquals(702, LibraryUnits.SLOTS);
        assertEquals(9 * LibraryUnits.UNITS.size(), LibraryUnits.SLOTS);
        for (int i = 0; i < LibraryUnits.UNITS.size(); i++) {
            Unit u = LibraryUnits.UNITS.get(i);
            assertEquals(i, u.index);
            assertEquals(i < 48 ? 0 : 1, u.storey, "48 units downstairs, then 30 upstairs: " + u);
            assertEquals(u.storey == 0 ? 2 : 8, u.yBody);
        }
        for (int i = 0; i <= 18; i++) assertEquals(0, LibraryUnits.UNITS.get(i).storey, "units 0..18 downstairs");
    }

    @Test
    void goldenOrder() {
        String[] calls = { "G-F-1", "G-F-2", "G-L1a-1", "G-L1a-2", "G-L1b-1", "G-L1b-2", "G-R1a-1", "G-R1a-2",
            "G-R1b-1", "G-R1b-2", "G-L2a-1" };
        int[][] at = { { 5, 1 }, { 19, 1 }, { 6, 5 }, { 2, 5 }, { 6, 6 }, { 2, 6 }, { 18, 5 }, { 22, 5 }, { 18, 6 },
            { 22, 6 }, { 6, 10 } };
        for (int i = 0; i < calls.length; i++) {
            Unit u = LibraryUnits.UNITS.get(i);
            assertEquals(calls[i], u.call);
            assertTrue(u.alongX);
            assertArrayEquals(at[i], new int[] { u.x0, u.z0 }, u.call);
        }
        assertEquals("G-L3a-2", LibraryUnits.UNITS.get(19).call);
        assertEquals("G-R5b-2", LibraryUnits.UNITS.get(41).call);
        assertEquals("G-WL-1", LibraryUnits.UNITS.get(42).call);
        assertEquals("G-WR-1", LibraryUnits.UNITS.get(43).call);
        assertEquals("G-B-1", LibraryUnits.UNITS.get(44).call);
        assertEquals("G-B-4", LibraryUnits.UNITS.get(47).call);
        assertEquals("U-F-1", LibraryUnits.UNITS.get(48).call);
        assertEquals("U-F-4", LibraryUnits.UNITS.get(51).call);
        assertEquals("U-L1a-1", LibraryUnits.UNITS.get(52).call);
        assertEquals("U-L1b-1", LibraryUnits.UNITS.get(53).call);
        assertEquals("U-R1a-1", LibraryUnits.UNITS.get(54).call);
        assertEquals("U-R5b-1", LibraryUnits.UNITS.get(71).call);
        assertEquals("U-B-1", LibraryUnits.UNITS.get(72).call);
        assertEquals("U-B-6", LibraryUnits.UNITS.get(77).call);
        Unit wl = LibraryUnits.UNITS.get(42);
        assertTrue(!wl.alongX && wl.x0 == 1 && wl.z0 == 28);
    }

    @Test
    void callNumbersAreUnique() {
        Set<String> seen = new HashSet<>();
        for (Unit u : LibraryUnits.UNITS) {
            assertTrue(seen.add(u.call), u.call);
            assertTrue(u.call.matches("[GU]-(F|B|WL|WR|[LR][1-5][ab])-[1-9]"), u.call);
            assertEquals(u.storey == 0 ? 'G' : 'U', u.call.charAt(0));
        }
    }

    @Test
    void everyUnitCellBelongsToExactlyOneUnit() {
        Set<Integer> slots = new HashSet<>();
        int bodies = 0, plinths = 0, crowns = 0;
        for (int y = 0; y < H; y++) for (int z = 0; z < D; z++) for (int x = 0; x < W; x++) {
            char ch = ArchiveShape.cell(x, y, z);
            int u = LibraryUnits.unitOfCell(x, y, z);
            if (ch == 'S') {
                bodies++;
                assertTrue(u >= 0, "a body outside any unit at " + x + "," + y + "," + z);
                int s = LibraryUnits.slot(u, x, y, z);
                assertTrue(s >= u * 9 && s < u * 9 + 9);
                assertTrue(slots.add(s), "two bodies share slot " + s);
                for (int other = 0; other < LibraryUnits.UNITS.size(); other++) {
                    if (other != u) assertEquals(-1, LibraryUnits.slot(other, x, y, z));
                }
            } else if (ch == 'p' || ch == 'c') {
                if (ch == 'p') plinths++;
                else crowns++;
                assertTrue(u >= 0, "a plinth or crown outside any unit");
                assertEquals(-1, LibraryUnits.slot(u, x, y, z), "only bodies hold books");
            } else {
                assertEquals(-1, u, "posts and everything else belong to no unit: " + ch);
            }
        }
        assertEquals(702, bodies);
        assertEquals(234, plinths);
        assertEquals(234, crowns);
        assertEquals(702, slots.size());
        assertEquals(-1, LibraryUnits.unitOfCell(-1, 2, 5));
        assertEquals(-1, LibraryUnits.unitOfCell(2, 2, 99));
    }

    @Test
    void unitShape() {
        for (Unit u : LibraryUnits.UNITS) {
            for (int k = 0; k < 3; k++) {
                assertEquals('p', ArchiveShape.cell(u.x(k), u.yBody - 1, u.z(k)), u.call);
                for (int y = u.yBody; y <= u.yBody + 2; y++) {
                    assertEquals('S', ArchiveShape.cell(u.x(k), y, u.z(k)), u.call);
                    assertEquals(u.index, LibraryUnits.unitOfCell(u.x(k), y, u.z(k)));
                }
                assertEquals('c', ArchiveShape.cell(u.x(k), u.yBody + 3, u.z(k)), u.call);
            }
        }
    }

    @Test
    void slotsRoundTrip() {
        for (int s = 0; s < LibraryUnits.SLOTS; s++) {
            int[] c = LibraryUnits.cellOfSlot(s);
            assertNotNull(c);
            assertEquals('S', ArchiveShape.cell(c[0], c[1], c[2]));
            int u = LibraryUnits.unitOfCell(c[0], c[1], c[2]);
            assertEquals(s / 9, u);
            assertEquals(s, LibraryUnits.slot(u, c[0], c[1], c[2]));
            Unit unit = LibraryUnits.UNITS.get(u);
            assertEquals(unit.yBody + 2 - s % 9 / 3, c[1], "row 0 at the top");
        }
        assertNull(LibraryUnits.cellOfSlot(-1));
        assertNull(LibraryUnits.cellOfSlot(702));
        assertEquals(-1, LibraryUnits.slot(78, 2, 2, 5));
    }

    @Test
    void facesLookIntoTheRoomAndHaveWalkableAirInFront() {
        for (Unit u : LibraryUnits.UNITS) {
            assertEquals(1, Math.abs(u.faceX) + Math.abs(u.faceZ), u.call);
            assertEquals(0, u.alongX ? u.faceX : u.faceZ, "the face looks across the run: " + u.call);
            int floor = u.yBody - 1;
            for (int k = 0; k < 3; k++) {
                int x = u.x(k) + u.faceX, z = u.z(k) + u.faceZ;
                // two cells of air at the storey's walking level, over a solid floor
                assertTrue(air(ArchiveShape.cell(x, floor, z)), u.call + " front at " + x + "," + floor + "," + z);
                assertTrue(air(ArchiveShape.cell(x, floor + 1, z)), u.call);
                char under = ArchiveShape.cell(x, floor - 1, z);
                assertTrue(under == '.' || under == 'd' || under == 'e', u.call + " stands over " + under);
                // the back is covered: by the other face of a range or by the wall
                char back = ArchiveShape.cell(u.x(k) - u.faceX, u.yBody + 1, u.z(k) - u.faceZ);
                assertTrue("SWLGw".indexOf(back) >= 0, u.call + " back " + back);
            }
        }
    }

    private static boolean air(char ch) {
        return ch == ' ' || ch == '-';
    }

    @Test
    void rangesFaceTheirAisles() {
        for (Unit u : LibraryUnits.UNITS) {
            String zone = u.call.substring(2, u.call.lastIndexOf('-'));
            if (zone.equals("F")) assertEquals(1, u.faceZ, "the front wall looks back into the hall");
            else if (zone.equals("B")) assertEquals(-1, u.faceZ, "the back wall looks toward the door");
            else if (zone.equals("WL")) assertEquals(1, u.faceX);
            else if (zone.equals("WR")) assertEquals(-1, u.faceX);
            else assertEquals(zone.endsWith("a") ? -1 : 1, u.faceZ, "face a looks toward the door: " + u.call);
            if (zone.startsWith("L") || zone.equals("WL")) assertTrue(u.x0 < ArchiveShape.MID);
            if (zone.startsWith("R") || zone.equals("WR")) assertTrue(u.x0 > ArchiveShape.MID);
        }
    }

    @Test
    void unitsMirrorAcrossTheAxis() {
        Map<String, Unit> byCall = new HashMap<>();
        for (Unit u : LibraryUnits.UNITS) byCall.put(u.call, u);
        for (Unit u : LibraryUnits.UNITS) {
            int mx = W - 1 - (u.alongX ? u.x0 + 2 : u.x0);
            Unit m = null;
            for (Unit o : LibraryUnits.UNITS) if (o.x0 == mx && o.z0 == u.z0 && o.yBody == u.yBody) m = o;
            assertNotNull(m, "a mirror of " + u.call);
            assertEquals(u.alongX, m.alongX);
            assertEquals(-u.faceX, m.faceX);
            assertEquals(u.faceZ, m.faceZ);
            if (u.call.matches("..[LR].*")) {
                String swapped = u.call.substring(0, 2) + (u.call.charAt(2) == 'L' ? 'R' : 'L') + u.call.substring(3);
                assertEquals(swapped, m.call);
            }
            if (u.call.matches("..W.*")) {
                String swapped = u.call.substring(0, 3) + (u.call.charAt(3) == 'L' ? 'R' : 'L') + u.call.substring(4);
                assertEquals(swapped, m.call);
            }
            // left and right flip with the mirror: a book's mirror image sits in the mirrored column
            for (int s = 0; s < 9; s++) {
                int[] c = LibraryUnits.cellOfSlot(u.index * 9 + s);
                int t = LibraryUnits.slot(m.index, W - 1 - c[0], c[1], c[2]);
                assertEquals(s / 3, t % 9 / 3, "same row");
                assertEquals(2 - s % 3, t % 3, "mirrored column");
            }
        }
        assertTrue(byCall.containsKey("G-L3a-2"));
    }

    @Test
    void leftIsTheViewersLeft() {
        // the blueprint frame is mirrored relative to the world: across (+x) runs to the right of someone outside
        // looking at the front, so someone facing a face-a unit (looking toward +z) has +x on their right
        Unit a = byCall("G-L1a-1"), bFace = byCall("G-L1b-1"), wl = byCall("G-WL-1"), wr = byCall("G-WR-1");
        assertArrayEquals(new int[] { a.x0, a.yBody + 2, a.z0 }, LibraryUnits.cellOfSlot(a.index * 9));
        assertArrayEquals(
            new int[] { bFace.x0 + 2, bFace.yBody + 2, bFace.z0 },
            LibraryUnits.cellOfSlot(bFace.index * 9));
        assertArrayEquals(new int[] { wl.x0, wl.yBody + 2, wl.z0 }, LibraryUnits.cellOfSlot(wl.index * 9));
        assertArrayEquals(new int[] { wr.x0, wr.yBody + 2, wr.z0 + 2 }, LibraryUnits.cellOfSlot(wr.index * 9));
        // checked in world terms for a front facing north: the viewer of a face-a unit looks south, so their left is
        // east (+X), and col 0 must be the body furthest east
        Blueprint bp = ArchiveShape.BLUEPRINT;
        int[] c0 = LibraryUnits.cellOfSlot(a.index * 9), c2 = LibraryUnits.cellOfSlot(a.index * 9 + 2);
        int[] w0 = bp.world(c0[0], H - 1 - c0[1], c0[2], 0, 64, 0, 0, -1);
        int[] w2 = bp.world(c2[0], H - 1 - c2[1], c2[2], 0, 64, 0, 0, -1);
        assertTrue(w0[0] > w2[0], "col 0 is east of col 2");
        // and for a front facing east: the viewer looks west, their left is south (+Z)
        w0 = bp.world(c0[0], H - 1 - c0[1], c0[2], 0, 64, 0, 1, 0);
        w2 = bp.world(c2[0], H - 1 - c2[1], c2[2], 0, 64, 0, 1, 0);
        assertTrue(w0[2] > w2[2], "col 0 is south of col 2");
    }

    private static Unit byCall(String call) {
        for (Unit u : LibraryUnits.UNITS) if (u.call.equals(call)) return u;
        throw new AssertionError(call);
    }

    @Test
    void postsSelectTheUnitOnTheClickedSide() {
        for (Unit u : LibraryUnits.UNITS) {
            int dx = u.alongX ? 1 : 0, dz = u.alongX ? 0 : 1;
            for (int y = u.yBody - 1; y <= u.yBody + 3; y++) {
                assertEquals(u.index, LibraryUnits.unitOfPost(u.x0 - dx, y, u.z0 - dz, 1), u.call);
                assertEquals(u.index, LibraryUnits.unitOfPost(u.x0 + 3 * dx, y, u.z0 + 3 * dz, -1), u.call);
            }
        }
        assertEquals(-1, LibraryUnits.unitOfPost(2, 3, 2, 1), "a stair post");
        assertEquals(-1, LibraryUnits.unitOfPost(2, 3, 2, -1));
        assertEquals(-1, LibraryUnits.unitOfPost(13, 3, 0, 1), "the trumeau");
        assertEquals(-1, LibraryUnits.unitOfPost(1, 3, 5, -1), "the wall end of a range has no unit beyond");
        assertEquals(-1, LibraryUnits.unitOfPost(3, 3, 5, 1), "not a post");
        assertEquals(-1, LibraryUnits.unitOfPost(5, 3, 5, 0), "no side");
        Unit wl = byCall("G-WL-1");
        assertEquals(wl.index, LibraryUnits.unitOfPost(1, 3, 27, 1), "the side wall run is along z");
        assertEquals(-1, LibraryUnits.unitOfPost(1, 3, 27, -1));
    }
}
