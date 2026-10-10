package com.fluxecho.library.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import com.fluxecho.frame.Formed;
import com.fluxecho.library.TileLibrary;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The books standing in the Echo Archive's shelf units, as the chunk mesh draws them: for each shelf body that holds a
 * book (one body is one book place), the book's spine colour. The frame's shelf renderer reads it while it builds the
 * chunk mesh, possibly on another thread, so the table is an immutable map swapped whole, like {@code Formed}'s client
 * table; replacing it re-meshes only the cells whose book changed.
 * <p>
 * Each library hands in its own cells ({@link #put}, keyed by its controller), so one library's update leaves the
 * others' books alone; the table the renderer reads is all of them together. Client thread only, apart from
 * {@link #colour}.
 */
@SideOnly(Side.CLIENT)
public final class LibraryBooks {

    private static volatile Map<Long, Integer> books = Collections.emptyMap();
    /** Per library (its controller's {@link Formed#key}), its cells and their colours. */
    private static final Map<Long, Map<Long, Integer>> OWNERS = new HashMap<>();

    private LibraryBooks() {}

    /**
     * Replaces one library's books: keys are {@link Formed#key} cell positions, values ARGB spine colours (0 or
     * absent: no book). The chunk mesh is redrawn where a book came, went or changed colour.
     */
    public static void put(long owner, Map<Long, Integer> cells) {
        Map<Long, Integer> copy = new HashMap<>();
        if (cells != null) for (Map.Entry<Long, Integer> e : cells.entrySet()) {
            Integer c = e.getValue();
            if (e.getKey() != null && c != null && c != 0) copy.put(e.getKey(), c);
        }
        Map<Long, Integer> was = OWNERS.get(owner);
        if (copy.equals(was == null ? Collections.emptyMap() : was)) return;
        if (copy.isEmpty()) OWNERS.remove(owner);
        else OWNERS.put(owner, copy);
        rebuild();
    }

    /** Takes one library's books off the shelves (it unloaded, broke, or stands as a hall). */
    public static void forget(long owner) {
        if (OWNERS.remove(owner) != null) rebuild();
    }

    /** The spine colour of the book in the shelf body at x, y, z (ARGB), or 0 when it holds none. */
    public static int colour(int x, int y, int z) {
        Integer c = books.get(Formed.key(x, y, z));
        return c == null ? 0 : c;
    }

    /** Leaving a world: the next one sends its books again. */
    public static void clear() {
        OWNERS.clear();
        books = Collections.emptyMap();
        TileLibrary.clearClient();
    }

    /** Joins every library's books into the table the renderer reads and re-meshes the cells that differ. */
    private static void rebuild() {
        Map<Long, Integer> all = new HashMap<>();
        for (Map<Long, Integer> m : OWNERS.values()) all.putAll(m);
        Map<Long, Integer> old = books;
        Map<Long, Integer> now = all.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(all);
        books = now;
        remesh(old, now);
    }

    private static void remesh(Map<Long, Integer> old, Map<Long, Integer> now) {
        World w = Minecraft.getMinecraft().theWorld;
        if (w == null) return;
        for (Map.Entry<Long, Integer> e : old.entrySet()) if (!e.getValue()
            .equals(now.get(e.getKey()))) mark(w, e.getKey());
        for (Long k : now.keySet()) if (!old.containsKey(k)) mark(w, k);
    }

    /** Re-meshes the cell of a {@link Formed#key}. */
    private static void mark(World w, long k) {
        int x = (int) (k >> 38), z = (int) (k << 26 >> 38), y = (int) (k & 0xFFF);
        w.markBlockRangeForRenderUpdate(x, y, z, x, y, z);
    }
}
