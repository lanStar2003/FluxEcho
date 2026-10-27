package com.fluxecho.logic.skin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The box table (style §3.9): the buildings of the campuses a player is near, each with its box, its state and its
 * accent, which the client draws the city's variants, accents, lamp windows and developing by (0.17.0: the old
 * buildings' lamp windows, {@link SkinProfiles#LEGACY}). The server says what is in it ({@code campus.SkinFeed}); the
 * world's blocks never change for it.
 * <p>
 * A table is immutable: every change makes a new one, so the renderers, which may build chunks on other threads, read
 * a whole table or the next one, never half of a change. Lookups are linear: a city has some twenty buildings
 * (§3.9.12).
 * <p>
 * It also keeps the NEI previews' tables (§3.9.7): {@link #putPreview} registers a building built in a preview world,
 * formed, powered, by day, held only as long as the preview world is.
 */
public final class SkinTable {

    public static final SkinTable EMPTY = new SkinTable(Collections.emptyList());

    /** The world time a preview is drawn at: noon. */
    public static final long PREVIEW_TIME = 6000;

    private final List<SkinEntry> entries;

    private SkinTable(List<SkinEntry> entries) {
        this.entries = entries;
    }

    /** A table of the entries, in their order. */
    public static SkinTable of(Collection<SkinEntry> entries) {
        return entries.isEmpty() ? EMPTY : new SkinTable(Collections.unmodifiableList(new ArrayList<>(entries)));
    }

    public List<SkinEntry> entries() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    /** The entry whose box holds the block, the smallest when boxes overlap (a zenith over its nexus); or null. */
    public SkinEntry find(int x, int y, int z) {
        SkinEntry best = null;
        long least = Long.MAX_VALUE;
        for (SkinEntry e : entries) if (e.contains(x, y, z)) {
            long v = SkinEntry.volume(e.box);
            if (v < least) {
                least = v;
                best = e;
            }
        }
        return best;
    }

    /** The entry whose lamp box holds the block, the smallest when they overlap; or null. */
    public SkinEntry findLamp(int x, int y, int z) {
        SkinEntry best = null;
        long least = Long.MAX_VALUE;
        for (SkinEntry e : entries) if (e.lampContains(x, y, z)) {
            long v = SkinEntry.volume(e.lamp);
            if (v < least) {
                least = v;
                best = e;
            }
        }
        return best;
    }

    /** The entry of a building, by its campus and id; or null. */
    public SkinEntry get(long campus, long id) {
        for (SkinEntry e : entries) if (e.campus == campus && e.id() == id) return e;
        return null;
    }

    /** The table with the entry in, in place of the same building's. */
    public SkinTable put(SkinEntry entry) {
        List<SkinEntry> l = new ArrayList<>(entries);
        for (int i = 0; i < l.size(); i++) if (same(l.get(i), entry)) {
            l.set(i, entry);
            return new SkinTable(Collections.unmodifiableList(l));
        }
        l.add(entry);
        return new SkinTable(Collections.unmodifiableList(l));
    }

    /** The table without the building; this table when it has no such building. */
    public SkinTable remove(long campus, long id) {
        List<SkinEntry> l = new ArrayList<>(entries.size());
        for (SkinEntry e : entries) if (e.campus != campus || e.id() != id) l.add(e);
        return l.size() == entries.size() ? this : of(l);
    }

    /** The table with the whole of each listed campus replaced by the entries given. */
    public SkinTable replaceCampuses(long[] campuses, Collection<SkinEntry> with) {
        List<SkinEntry> l = new ArrayList<>(entries.size() + with.size());
        for (SkinEntry e : entries) if (!listed(campuses, e.campus)) l.add(e);
        l.addAll(with);
        return of(l);
    }

    private static boolean listed(long[] campuses, long c) {
        for (long k : campuses) if (k == c) return true;
        return false;
    }

    private static boolean same(SkinEntry a, SkinEntry b) {
        return a.campus == b.campus && a.id() == b.id();
    }

    // ---- NEI previews (§3.9.7)

    /** The previews' tables by their worlds, held weakly: a closed preview's world drops its table with it. */
    private static final Map<Object, SkinTable> PREVIEWS = new WeakHashMap<>();

    /**
     * Registers a building a preview world shows: formed, powered, by day ({@link #PREVIEW_TIME}), its box the shape's
     * box round the controller. A building built there again (another facing, another tier) replaces its entry.
     */
    public static void putPreview(Object world, int[] box, String key, String shape, int facing, int[] ctrl) {
        SkinEntry e = SkinEntry.builder()
            .key(key)
            .shape(shape)
            .facing(facing)
            .ctrl(ctrl[0], ctrl[1], ctrl[2])
            .box(box)
            .state(SkinState.FORMED)
            .powered(true)
            .accent(SkinPalette.accentOr(key, shape, 0xFFFFFF))
            .build();
        synchronized (PREVIEWS) {
            SkinTable t = PREVIEWS.get(world);
            // the same core built again replaces what it built before
            PREVIEWS.put(world, (t == null ? EMPTY : t).put(e));
        }
    }

    /** A preview world's table, or null for a world no preview registered. */
    public static SkinTable preview(Object world) {
        synchronized (PREVIEWS) {
            return world == null ? null : PREVIEWS.get(world);
        }
    }

    /** The preview entry whose box holds the block of the world, or null. */
    public static SkinEntry resolve(Object world, int x, int y, int z) {
        SkinTable t = preview(world);
        return t == null ? null : t.find(x, y, z);
    }
}
