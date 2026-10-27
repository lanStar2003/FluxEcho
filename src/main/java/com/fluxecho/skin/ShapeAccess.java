package com.fluxecho.skin;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.fluxecho.campus.BlockFitting;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.frame.Formed;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.shape.Family;
import com.fluxecho.logic.shape.Form;
import com.fluxecho.logic.shape.Nbhd;
import com.fluxecho.logic.shape.Quad;
import com.fluxecho.logic.shape.ShapeGeo;

/**
 * Where the world meets {@link ShapeGeo} (0.17.0, style FINAL §3.3, §3.9.8): the neighbourhood of one of the new
 * families' blocks read from an {@code IBlockAccess}, and what {@code ShapeGeo} makes of it, the quads the renderer
 * draws and the boxes the block collides with. Both sides use it: the server and the client for the collision boxes,
 * the client's chunk builder for the quads, so what a player bumps into is what is drawn.
 * <p>
 * A neighbour is one of ours when it is a block of the new families ({@link BlockSkin}): its shape, and whether it
 * is of the same family. A neighbour a formed multiblock hides ({@link Formed#HIDE}) is open, so the faces toward it
 * come back (style FINAL §3.9.8; the frame does the same). Anything else is solid when it is an opaque cube, or one of
 * the frame's and fittings' whole cubes (which are not opaque, but whose faces toward us are drawn by them), water when
 * it is water, and open otherwise.
 * <p>
 * Reading only blocks, metas and the immutable {@code Formed} table, everything here is safe on the chunk builder's
 * threads. {@code ShapeGeo}'s output depends on the shape and the neighbourhood alone, so it is kept in a small cache,
 * cleared when it fills.
 */
public final class ShapeAccess {

    /** Which cells a formed multiblock hides from view (the {@link Formed#HIDE} flag), as one side sees them. */
    public interface Hidden {

        boolean at(int x, int y, int z);
    }

    /** Nothing hidden: a preview world, an item. */
    public static final Hidden NOTHING = (x, y, z) -> false;

    /** The most (shape, neighbourhood) pairs kept; a city's walls and roofs repeat a few hundred of them. */
    static final int CACHE = 2048;

    private static final Map<Key, List<Quad>> QUADS = new ConcurrentHashMap<>();

    private ShapeAccess() {}

    /**
     * What a world hides: the server's table for a server world, the client's for anything else (the client world, the
     * chunk builder's cache of it).
     */
    public static Hidden hidden(IBlockAccess w) {
        if (w instanceof World world && !world.isRemote)
            return (x, y, z) -> (Formed.flags(world, x, y, z) & Formed.HIDE) != 0;
        return (x, y, z) -> (Formed.clientFlags(x, y, z) & Formed.HIDE) != 0;
    }

    /**
     * The neighbourhood code ({@link Nbhd}) of the cell at {@code (x, y, z)} for a block of family {@code self}: one of
     * ours with its shape, or open, solid or water.
     */
    public static int code(IBlockAccess w, int x, int y, int z, Family self, Hidden hidden) {
        Block b = w.getBlock(x, y, z);
        if (b == null) return Nbhd.OPEN;
        if (b instanceof BlockSkin s) {
            if (hidden.at(x, y, z)) return Nbhd.OPEN;
            return Nbhd.ours(s.form(w.getBlockMetadata(x, y, z)), s.family == self);
        }
        if (b.isOpaqueCube()) return Nbhd.SOLID;
        if (b instanceof BlockFrame) {
            int meta = w.getBlockMetadata(x, y, z);
            return BlockFrame.full(meta) && meta < BlockFrame.TYPES && !hidden.at(x, y, z) ? Nbhd.SOLID : Nbhd.OPEN;
        }
        if (b instanceof BlockFitting) {
            int t = BlockFitting.type(w.getBlockMetadata(x, y, z));
            return t == Parts.F_PLINTH || t == Parts.F_CROWN || t == Parts.F_LIFT ? Nbhd.SOLID : Nbhd.OPEN;
        }
        return b.getMaterial() == Material.water ? Nbhd.WATER : Nbhd.OPEN;
    }

    /** The neighbourhood of the cell at {@code (x, y, z)}, for a block of family {@code self}. */
    public static Nbhd nbhd(IBlockAccess w, int x, int y, int z, Family self, Hidden hidden) {
        int[] c = new int[27];
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dy == 0 && dz == 0) continue;
            c[Nbhd.index(dx, dy, dz)] = code(w, x + dx, y + dy, z + dz, self, hidden);
        }
        return Nbhd.of(c);
    }

    /**
     * The quads to draw for the block at {@code (x, y, z)}: none when it is not one of the new families' or a formed
     * multiblock hides it, else {@link ShapeGeo#quads} of its shape in its neighbourhood (shared, immutable).
     */
    public static List<Quad> quads(IBlockAccess w, int x, int y, int z, Hidden hidden) {
        Block b = w.getBlock(x, y, z);
        if (!(b instanceof BlockSkin s) || hidden.at(x, y, z)) return Collections.emptyList();
        return quads(s.form(w.getBlockMetadata(x, y, z)), nbhd(w, x, y, z, s.family, hidden));
    }

    /** {@link ShapeGeo#quads}, kept for the next cell with the same shape and neighbourhood. */
    public static List<Quad> quads(Form form, Nbhd n) {
        Key k = new Key(form, n);
        List<Quad> q = QUADS.get(k);
        if (q != null) return q;
        q = ShapeGeo.quads(form, n);
        if (QUADS.size() >= CACHE) QUADS.clear();
        QUADS.put(k, q);
        return q;
    }

    /**
     * The collision boxes of the block at {@code (x, y, z)} in the cell, {minX, minY, minZ, maxX, maxY, maxZ} 0..1:
     * {@link ShapeGeo#boxes} of its shape in its neighbourhood, the same boxes the walk tests read. Empty when it is
     * not one of the new families'.
     */
    public static List<double[]> boxes(IBlockAccess w, int x, int y, int z, Hidden hidden) {
        Block b = w.getBlock(x, y, z);
        if (!(b instanceof BlockSkin s)) return Collections.emptyList();
        Form form = s.form(w.getBlockMetadata(x, y, z));
        if (form.wholeBlock() || form.seeThrough()) return ShapeGeo.boxes(form, Nbhd.ISOLATED);
        return ShapeGeo.boxes(form, nbhd(w, x, y, z, s.family, hidden));
    }

    /** A cache key: a shape in a neighbourhood. */
    private static final class Key {

        final Form form;
        final Nbhd n;

        Key(Form form, Nbhd n) {
            this.form = form;
            this.n = n;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && k.form == form && k.n.equals(n);
        }

        @Override
        public int hashCode() {
            return form.hashCode() * 31 + n.hashCode();
        }
    }
}
