package com.fluxecho.library;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.Test;

import com.fluxecho.frame.Formed;
import com.fluxecho.logic.ArchiveShape;
import com.fluxecho.logic.LibraryUnits;

/**
 * The Echo Archive's bookcases as a library tile sees them in the world, for every facing: each book place maps to
 * its block and back, every part of a bookcase (and each post by the side or the half clicked) names its bookcase, the
 * books face open floor, and a library saved before 0.10.0 is a hall of 167 books.
 */
class TileLibraryUnitsTest {

    private static final int X = 300, Y = 70, Z = -120;

    private static TileLibrary archive(int facing) {
        TileLibrary l = new TileLibrary();
        l.xCoord = X;
        l.yCoord = Y;
        l.zCoord = Z;
        l.place((UUID) null, null, facing);
        return l;
    }

    private static int[] step(int[] p, int side) {
        ForgeDirection d = ForgeDirection.getOrientation(side);
        return new int[] { p[0] + d.offsetX, p[1] + d.offsetY, p[2] + d.offsetZ };
    }

    @Test
    void theWorldCoordinatesOfAPointAreThoseOfWorldOf() {
        for (int facing = 2; facing <= 5; facing++) {
            TileLibrary l = archive(facing);
            for (int x = 0; x < ArchiveShape.WIDTH; x++) for (int y = 0; y < ArchiveShape.HEIGHT; y++)
                for (int z = 0; z < ArchiveShape.DEPTH; z++) assertArrayEquals(
                    l.worldOf(x, y, z),
                    new int[] { l.worldX(x, z), l.worldY(y), l.worldZ(x, z) },
                    "facing " + facing + " point " + x + ", " + y + ", " + z);
        }
    }

    @Test
    void aNewLibraryIsAnArchive() {
        TileLibrary l = archive(2);
        assertTrue(l.isArchive());
        assertEquals(TileLibrary.ARCHIVE_BOOKS, l.capacity());
        assertEquals(78, l.unitCount());
        assertEquals(702, TileLibrary.SLOTS);
    }

    @Test
    void everyPlaceMapsToItsBlockAndBack() {
        for (int facing = 2; facing <= 5; facing++) {
            TileLibrary l = archive(facing);
            Set<Long> cells = new HashSet<>();
            for (int s = 0; s < LibraryUnits.SLOTS; s++) {
                int[] p = l.unitCell(s / 9, s % 9);
                assertTrue(cells.add(Formed.key(p[0], p[1], p[2])), "slot " + s + " shares its block");
                assertEquals(s, l.slotAt(p[0], p[1], p[2]), "slot of the block of slot " + s + ", facing " + facing);
                for (int side = 0; side < 6; side++)
                    assertEquals(s / 9, l.unitAt(p[0], p[1], p[2], side), "bookcase of slot " + s);
                int[] local = l.local(p[0], p[1], p[2]);
                assertEquals(ArchiveShape.BODY, ArchiveShape.cell(local[0], local[1], local[2]));
                assertArrayEquals(p, l.worldOf(local[0], local[1], local[2]));
            }
        }
    }

    @Test
    void booksFaceTheRoom() {
        for (int facing = 2; facing <= 5; facing++) {
            TileLibrary l = archive(facing);
            for (int u = 0; u < l.unitCount(); u++) {
                int face = l.unitFace(u);
                assertTrue(face >= 2 && face <= 5, "bookcase " + u + " faces sideways");
                for (int k = 0; k < 9; k++) {
                    int[] front = step(l.unitCell(u, k), face);
                    int[] local = l.local(front[0], front[1], front[2]);
                    char ch = ArchiveShape.cell(local[0], local[1], local[2]);
                    assertTrue(
                        ch == ArchiveShape.ANY || ch == ArchiveShape.SOFT,
                        "in front of book " + k + " of " + l.callNumber(u) + " stands '" + ch + "'");
                    assertTrue(ArchiveShape.inside(local[0] + 0.5, local[1] + 0.5, local[2] + 0.5), "inside");
                }
            }
        }
    }

    @Test
    void plinthsCrownsAndPostsNameTheirBookcase() {
        for (int facing = 2; facing <= 5; facing++) {
            TileLibrary l = archive(facing);
            for (LibraryUnits.Unit u : LibraryUnits.UNITS) {
                for (int k = 0; k < 3; k++) {
                    for (int y : new int[] { u.yBody - 1, u.yBody + 3 }) {
                        int[] p = l.worldOf(u.x(k), y, u.z(k));
                        assertEquals(u.index, l.unitAt(p[0], p[1], p[2], 1), "plinth or crown of " + u.call);
                        assertEquals(-1, l.slotAt(p[0], p[1], p[2]), "no book in a plinth or crown");
                    }
                }
                // the posts at both ends of the run, clicked on the side towards the bookcase and on the half of their
                // face nearer to it
                int dx = u.alongX ? 1 : 0, dz = u.alongX ? 0 : 1;
                int[][] posts = { { u.x0 - dx, u.z0 - dz }, { u.x0 + 3 * dx, u.z0 + 3 * dz } };
                for (int end = 0; end < 2; end++) {
                    int lx = posts[end][0], lz = posts[end][1], ly = u.yBody + 1;
                    assertEquals(ArchiveShape.POST, ArchiveShape.cell(lx, ly, lz));
                    int[] post = l.worldOf(lx, ly, lz),
                        body = l.worldOf(u.x(end == 0 ? 0 : 2), ly, u.z(end == 0 ? 0 : 2));
                    int toward = side(post, body);
                    assertEquals(u.index, l.unitAt(post[0], post[1], post[2], toward), "post side toward " + u.call);
                    int face = l.unitFace(u.index);
                    // a point on the post's face, a little toward the bookcase from the middle
                    double hx = post[0] + 0.5 + 0.3 * (body[0] - post[0]) + 0.4 * offX(face);
                    double hz = post[2] + 0.5 + 0.3 * (body[2] - post[2]) + 0.4 * offZ(face);
                    assertEquals(
                        u.index,
                        l.unitAt(post[0], post[1], post[2], face, hx, hz),
                        "post face half of " + u.call);
                }
            }
        }
    }

    private static int side(int[] from, int[] to) {
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS)
            if (from[0] + d.offsetX == to[0] && from[1] + d.offsetY == to[1] && from[2] + d.offsetZ == to[2])
                return d.ordinal();
        throw new AssertionError("not neighbours");
    }

    private static int offX(int side) {
        return ForgeDirection.getOrientation(side).offsetX;
    }

    private static int offZ(int side) {
        return ForgeDirection.getOrientation(side).offsetZ;
    }

    @Test
    void theDeskAndLecternsAreConsoles() {
        for (int facing = 2; facing <= 5; facing++) {
            TileLibrary l = archive(facing);
            int[] d = l.deskPos();
            assertTrue(l.consoleAt(d[0], d[1], d[2]), "the desk");
            for (int[] c : ArchiveShape.lecterns()) {
                int[] p = l.worldOf(c[0], c[1], c[2]);
                assertTrue(l.consoleAt(p[0], p[1], p[2]), "a lectern");
            }
            int[] below = l.worldOf(ArchiveShape.CTRL_X, ArchiveShape.CTRL_Y - 1, ArchiveShape.CTRL_Z);
            assertArrayEquals(new int[] { X, Y - 1, Z }, below, "the stand is under the controller");
            assertFalse(l.consoleAt(X, Y, Z), "the controller itself");
        }
    }

    @Test
    void theArchiveDocksByItsMiddle() {
        // its foundation's centre is 17 rows behind the front, two below the controller, on the axis
        for (int facing = 2; facing <= 5; facing++) {
            TileLibrary l = archive(facing);
            ForgeDirection f = ForgeDirection.getOrientation(facing);
            assertArrayEquals(new int[] { X - 17 * f.offsetX, Y - 2, Z - 17 * f.offsetZ }, l.centre());
        }
    }

    @Test
    void anOldSaveIsAHall() {
        TileLibrary l = new TileLibrary();
        NBTTagCompound t = new NBTTagCompound();
        t.setString("id", "fluxecho:library");
        t.setByte("Facing", (byte) 3);
        NBTTagCompound samples = new NBTTagCompound();
        samples.setInteger("Size", 167);
        t.setTag("Samples", samples);
        l.readFromNBT(t);
        assertFalse(l.isArchive());
        assertEquals(TileLibrary.HALL, l.shapeName());
        assertEquals(167, l.capacity());
        assertEquals(0, l.unitCount());
        // saved again, it stays a hall, with room for an Archive's books (saving needs the tile's id, as in the game)
        try {
            TileEntity.addMapping(TileLibrary.class, "fluxecho:library");
        } catch (IllegalArgumentException alreadyThere) {
            // another test registered it
        }
        NBTTagCompound again = new NBTTagCompound();
        l.writeToNBT(again);
        assertEquals(TileLibrary.HALL, again.getString("Shape"));
        assertEquals(
            702,
            again.getCompoundTag("Samples")
                .getInteger("Size"));
        TileLibrary back = new TileLibrary();
        back.readFromNBT(again);
        assertEquals(TileLibrary.HALL, back.shapeName());
        // a hall's shelves are still its book places
        back.xCoord = X;
        back.yCoord = Y;
        back.zCoord = Z;
        for (int i = 0; i < TileLibrary.HALL_BOOKS; i++) {
            int[] p = back.shelfPos(i);
            assertEquals(i, back.slotAt(p[0], p[1], p[2]));
            assertNotEquals(-1, back.shelfFace(i));
        }
        assertEquals(-1, back.unitAt(X, Y, Z, 2), "a hall has no bookcases");
    }
}
