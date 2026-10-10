package com.fluxecho.campus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;

import com.fluxecho.logic.Blueprint;
import com.fluxecho.logic.NexusShape;
import com.fluxecho.nexus.TileNexus;

/**
 * Every nexus campus in the save, loaded or not: its dimension, its centre and the radius it grades, so a campus
 * builder never touches another nexus's campus and two campuses are never placed on top of each other. Saved with the
 * overworld ({@code data/fluxecho_campus.dat}); server side only.
 * <p>
 * A campus is added when its nexus becomes active (a core placed on the ground, or the forum laid on a 0.9.2 nexus)
 * and removed when its core is broken; an active nexus enters itself again where it stands when it loads, so one moved
 * whole (a teleposer) is found at its new place. A core that vanished some other way (a world editor, a deleted chunk)
 * or moved leaves a stale entry behind; the placement check ({@link #tooClose}) and a nexus loading nearby
 * ({@link #dropStale}) drop such an entry once every cell its core could stand on is loaded and none holds the nexus,
 * so a forgotten campus cannot block a spot for ever.
 * <p>
 * It also remembers every cell where a campus job placed its free supply port ({@link #portGifted}): the gift is given
 * once per spot, so breaking a nexus and placing it again on the same ground does not hand out another port.
 */
public class CampusRegistry extends WorldSavedData {

    static final String NAME = "fluxecho_campus";

    /** {dim, x, y, z, radius} per campus. */
    private final List<int[]> entries = new ArrayList<>();
    /** {dim, x, y, z} of every free supply port placed. */
    private final List<int[]> ports = new ArrayList<>();

    public CampusRegistry(String name) {
        super(name);
    }

    /** The registry of the save; a throwaway empty one while the overworld is not loaded. */
    static CampusRegistry get() {
        World w = DimensionManager.getWorld(0);
        if (w == null || w.mapStorage == null) return new CampusRegistry(NAME);
        CampusRegistry d = (CampusRegistry) w.mapStorage.loadData(CampusRegistry.class, NAME);
        if (d == null) {
            d = new CampusRegistry(NAME);
            w.mapStorage.setData(NAME, d);
        }
        return d;
    }

    /** Records (or updates) the campus centred at {@code (x, y, z)} in the world's dimension. */
    public static void add(World w, int x, int y, int z, int radius) {
        if (w == null || w.isRemote) return;
        CampusRegistry r = get();
        int dim = w.provider.dimensionId;
        r.drop(dim, x, y, z);
        r.entries.add(new int[] { dim, x, y, z, radius });
        r.markDirty();
    }

    /** Forgets the campus centred at {@code (x, y, z)}. */
    public static void remove(World w, int x, int y, int z) {
        if (w == null || w.isRemote) return;
        CampusRegistry r = get();
        if (r.drop(w.provider.dimensionId, x, y, z)) r.markDirty();
    }

    /** Whether a campus centred at {@code (x, y, z)} is recorded in the world's dimension. */
    public static boolean has(World w, int x, int y, int z) {
        if (w == null || w.isRemote) return false;
        int dim = w.provider.dimensionId;
        for (int[] e : get().entries) if (e[0] == dim && e[1] == x && e[2] == y && e[3] == z) return true;
        return false;
    }

    /** The centres {x, y, z} of every campus recorded in the world's dimension (copies). */
    public static List<int[]> centres(World w) {
        List<int[]> out = new ArrayList<>();
        if (w == null || w.isRemote) return out;
        int dim = w.provider.dimensionId;
        for (int[] e : get().entries) if (e[0] == dim) out.add(new int[] { e[1], e[2], e[3] });
        return out;
    }

    /**
     * The centre {x, y, z} of a campus whose area (Chebyshev {@code radius} round its centre) holds the column
     * {@code (x, z)}, other than the one centred at {@code exclude} (the asking nexus; null for none); null when there
     * is none.
     */
    public static int[] ownerAt(World w, int x, int z, int[] exclude) {
        if (w == null || w.isRemote) return null;
        int dim = w.provider.dimensionId;
        for (int[] e : get().entries) {
            if (e[0] != dim || same(e, exclude)) continue;
            if (Math.max(Math.abs(x - e[1]), Math.abs(z - e[3])) <= e[4]) return new int[] { e[1], e[2], e[3] };
        }
        return null;
    }

    /**
     * The centre {x, y, z} of a campus closer than {@code spacing} (Chebyshev) to the column {@code (x, z)}, or null.
     * A spacing of 0 or less never finds one. Entries found stale on the way ({@link #stale}) are dropped and do not
     * count.
     */
    public static int[] tooClose(World w, int x, int z, int spacing) {
        if (w == null || w.isRemote || spacing <= 0) return null;
        CampusRegistry r = get();
        int dim = w.provider.dimensionId;
        int[] found = null;
        boolean dropped = false;
        for (Iterator<int[]> it = r.entries.iterator(); it.hasNext();) {
            int[] e = it.next();
            if (e[0] != dim) continue;
            if (Math.max(Math.abs(x - e[1]), Math.abs(z - e[3])) >= spacing) continue;
            if (stale(w, e[1], e[2], e[3])) {
                it.remove();
                dropped = true;
                continue;
            }
            found = new int[] { e[1], e[2], e[3] };
            break;
        }
        if (dropped) r.markDirty();
        return found;
    }

    /**
     * Whether the campus centred at {@code (x, y, z)} has lost its nexus: every cell its core can stand on (one per
     * facing, two above the base at the front rim) is loaded and none holds a nexus with that centre. False whenever
     * one of those cells is not loaded, as nothing can be told about it then.
     */
    static boolean stale(World w, int x, int y, int z) {
        Blueprint b = NexusShape.PHASE_1;
        int[][] fronts = { { 0, -1 }, { 0, 1 }, { -1, 0 }, { 1, 0 } };
        int[] centre = { x, y, z };
        for (int[] f : fronts) {
            // the centre as seen from a core at the origin facing f; the core is the centre minus that offset
            int[] o = b.world(b.ctrlA, b.height() - 1, b.ctrlC + b.centreBack(), 0, 0, 0, f[0], f[1]);
            int cx = x - o[0], cy = y - o[1], cz = z - o[2];
            if (cy < 0 || cy > 255) continue;
            if (!w.blockExists(cx, cy, cz)) return false;
            TileEntity te = w.getTileEntity(cx, cy, cz);
            if (te instanceof TileNexus n && !n.isInvalid() && Arrays.equals(n.centre(), centre)) return false;
        }
        return true;
    }

    /**
     * Drops the entries centred within {@code reach} (Chebyshev) of the column {@code (x, z)} whose nexus is gone
     * ({@link #stale}).
     */
    public static void dropStale(World w, int x, int z, int reach) {
        if (w == null || w.isRemote) return;
        CampusRegistry r = get();
        int dim = w.provider.dimensionId;
        if (r.entries.removeIf(
            e -> e[0] == dim && Math.max(Math.abs(x - e[1]), Math.abs(z - e[3])) <= reach
                && stale(w, e[1], e[2], e[3])))
            r.markDirty();
    }

    /** Whether a campus job already placed a free supply port at the cell. */
    public static boolean portGifted(World w, int x, int y, int z) {
        if (w == null || w.isRemote) return false;
        int dim = w.provider.dimensionId;
        for (int[] p : get().ports) if (p[0] == dim && p[1] == x && p[2] == y && p[3] == z) return true;
        return false;
    }

    /** Remembers that a campus job placed its free supply port at the cell. */
    public static void markPortGifted(World w, int x, int y, int z) {
        if (w == null || w.isRemote || portGifted(w, x, y, z)) return;
        CampusRegistry r = get();
        r.ports.add(new int[] { w.provider.dimensionId, x, y, z });
        r.markDirty();
    }

    private static boolean same(int[] e, int[] centre) {
        return centre != null && centre.length >= 3 && e[1] == centre[0] && e[2] == centre[1] && e[3] == centre[2];
    }

    private boolean drop(int dim, int x, int y, int z) {
        return entries.removeIf(e -> e[0] == dim && e[1] == x && e[2] == y && e[3] == z);
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        entries.clear();
        NBTTagList l = t.getTagList("Campuses", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            int[] e = l.getCompoundTagAt(i)
                .getIntArray("E");
            if (e.length == 5) entries.add(e);
        }
        ports.clear();
        int[] p = t.getIntArray("Ports");
        for (int i = 0; i + 3 < p.length; i += 4) ports.add(new int[] { p[i], p[i + 1], p[i + 2], p[i + 3] });
    }

    @Override
    public void writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (int[] e : entries) {
            NBTTagCompound c = new NBTTagCompound();
            c.setIntArray("E", e.clone());
            l.appendTag(c);
        }
        t.setTag("Campuses", l);
        int[] p = new int[4 * ports.size()];
        for (int i = 0; i < ports.size(); i++) System.arraycopy(ports.get(i), 0, p, 4 * i, 4);
        t.setIntArray("Ports", p);
    }
}
