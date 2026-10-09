package com.fluxecho.gate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.DimensionManager;

import com.fluxecho.logic.GateGeometry;

/**
 * Every light gate of a world and the plot each one opens onto, kept with the world ({@code data/fluxecho_gates.dat},
 * shared by all dimensions). A plot has one gate inside it, made with the plot, and at most one gate outside linked to
 * it.
 */
public final class GateRegistry extends WorldSavedData {

    private static final String NAME = "fluxecho_gates";

    public static final class Entry {

        public final int id, dim, x, y, z, facing, plot;
        public final boolean inside;
        /** The gate on the other side, 0 for none. */
        public int partner;

        Entry(int id, int dim, int x, int y, int z, int facing, int plot, boolean inside, int partner) {
            this.id = id;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.facing = facing;
            this.plot = plot;
            this.inside = inside;
            this.partner = partner;
        }

        public GateGeometry.Gate gate() {
            return new GateGeometry.Gate(x, y, z, facing);
        }

        public boolean at(int dim, int x, int y, int z) {
            return this.dim == dim && this.x == x && this.y == y && this.z == z;
        }
    }

    private final Map<Integer, Entry> byId = new LinkedHashMap<>();
    private int nextId = 1, nextPlot = 0;

    public GateRegistry(String name) {
        super(name);
    }

    /** The world's registry; null before the overworld is loaded. */
    public static GateRegistry get() {
        World w = DimensionManager.getWorld(0);
        if (w == null) return null;
        MapStorage s = w.mapStorage;
        GateRegistry r = (GateRegistry) s.loadData(GateRegistry.class, NAME);
        if (r == null) {
            r = new GateRegistry(NAME);
            s.setData(NAME, r);
        }
        return r;
    }

    public Entry byId(int id) {
        return byId.get(id);
    }

    public Entry partnerOf(Entry e) {
        return e == null || e.partner == 0 ? null : byId.get(e.partner);
    }

    public Entry at(int dim, int x, int y, int z) {
        for (Entry e : byId.values()) if (e.at(dim, x, y, z)) return e;
        return null;
    }

    public List<Entry> inDim(int dim) {
        List<Entry> l = new ArrayList<>();
        for (Entry e : byId.values()) if (e.dim == dim) l.add(e);
        return l;
    }

    /** The gate inside a plot, null when the plot was never made. */
    public Entry insideOf(int plot) {
        for (Entry e : byId.values()) if (e.inside && e.plot == plot) return e;
        return null;
    }

    public int newPlot() {
        markDirty();
        return nextPlot++;
    }

    public Entry add(int dim, int x, int y, int z, int facing, int plot, boolean inside, int partner) {
        Entry e = new Entry(nextId++, dim, x, y, z, facing, plot, inside, partner);
        byId.put(e.id, e);
        markDirty();
        return e;
    }

    public void remove(Entry e) {
        if (e == null) return;
        byId.remove(e.id);
        Entry p = byId.get(e.partner);
        if (p != null && p.partner == e.id) p.partner = 0;
        markDirty();
    }

    public void link(Entry a, Entry b) {
        a.partner = b.id;
        b.partner = a.id;
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        byId.clear();
        nextId = Math.max(1, t.getInteger("nextId"));
        nextPlot = t.getInteger("nextPlot");
        NBTTagList l = t.getTagList("gates", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound g = l.getCompoundTagAt(i);
            Entry e = new Entry(
                g.getInteger("id"),
                g.getInteger("dim"),
                g.getInteger("x"),
                g.getInteger("y"),
                g.getInteger("z"),
                g.getByte("facing"),
                g.getInteger("plot"),
                g.getBoolean("inside"),
                g.getInteger("partner"));
            byId.put(e.id, e);
            nextId = Math.max(nextId, e.id + 1);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound t) {
        t.setInteger("nextId", nextId);
        t.setInteger("nextPlot", nextPlot);
        NBTTagList l = new NBTTagList();
        for (Entry e : byId.values()) {
            NBTTagCompound g = new NBTTagCompound();
            g.setInteger("id", e.id);
            g.setInteger("dim", e.dim);
            g.setInteger("x", e.x);
            g.setInteger("y", e.y);
            g.setInteger("z", e.z);
            g.setByte("facing", (byte) e.facing);
            g.setInteger("plot", e.plot);
            g.setBoolean("inside", e.inside);
            g.setInteger("partner", e.partner);
            l.appendTag(g);
        }
        t.setTag("gates", l);
    }
}
