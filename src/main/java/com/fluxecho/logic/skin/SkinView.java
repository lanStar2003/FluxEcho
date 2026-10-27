package com.fluxecho.logic.skin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The client's immutable snapshot of the box table (refactor §5.6): the entries of the campuses it was told of in its
 * dimension, and for each whether its lamp windows are drawn lit now. The renderers read it ({@link #glow}) from any
 * thread; every change makes a new snapshot and names the boxes to rebuild.
 * <p>
 * When a lamp flips is decided outside the renderers: an entry that arrives is drawn as the rule says at once (its box
 * is rebuilt once, as its chunks may already have been drawn); after that, a building whose lamps should flip (dusk and
 * dawn reaching it with the wave, a power change) is {@link #due}, and flips only when the throttle lets it
 * ({@link #flip}, {@link RebuildQueue}), so a building never shows half its lamps of each state and the city rebuilds
 * at most four boxes a second. A flip rebuilds only the lamp box ({@link SkinEntry#rebuildBox}).
 */
public final class SkinView {

    public static final SkinView EMPTY = new SkinView(0, SkinTable.EMPTY, Collections.emptyMap());

    /** What a change made: the new snapshot and the boxes {x0, y0, z0, x1, y1, z1} to rebuild. */
    public static final class Change {

        public final SkinView view;
        public final List<int[]> dirty;

        Change(SkinView view, List<int[]> dirty) {
            this.view = view;
            this.dirty = Collections.unmodifiableList(dirty);
        }
    }

    /** The dimension the table is of. */
    public final int dim;
    private final SkinTable table;
    /** Whether each building's lamps are drawn lit, by building id. */
    private final Map<Long, Boolean> lit;

    private SkinView(int dim, SkinTable table, Map<Long, Boolean> lit) {
        this.dim = dim;
        this.table = table;
        this.lit = lit;
    }

    public SkinTable table() {
        return table;
    }

    public int size() {
        return table.size();
    }

    /** Whether a building's lamps are drawn lit now. */
    public boolean lit(SkinEntry e) {
        Boolean b = lit.get(e.id());
        return b != null && b;
    }

    /**
     * What a renderer draws of a part's glow overlay at a block: {@link LampRule#UNTOUCHED} where no building of the
     * table has its lamps or the part is no lamp, {@link LampRule#DARK} for an unlit lamp, else the overlay's colour.
     */
    public int glow(int x, int y, int z, int part) {
        SkinEntry e = table.findLamp(x, y, z);
        return e == null ? LampRule.UNTOUCHED : LampRule.glow(e, part, lit(e));
    }

    /**
     * A whole table arrived: its campuses replaced (all of them on a reset), the new entries drawn as the rule says.
     */
    public Change table(SkinCodec.Table t, long worldTime) {
        SkinTable base = t.reset || t.dim != dim ? SkinTable.EMPTY : table;
        List<int[]> dirty = new ArrayList<>();
        Set<Long> listed = new HashSet<>();
        for (long c : t.campuses) listed.add(c);
        // what the client drew of the campuses it had and is told of anew, or of everything on a reset
        if (base == SkinTable.EMPTY) for (SkinEntry e : table.entries()) dirty.add(e.rebuildBox());
        else for (SkinEntry e : table.entries()) if (listed.contains(e.campus)) dirty.add(e.rebuildBox());
        SkinTable next = base.replaceCampuses(t.campuses, t.entries);
        Map<Long, Boolean> l = keep(next.entries());
        for (SkinEntry e : t.entries) {
            l.put(e.id(), LampRule.lit(e, worldTime));
            dirty.add(e.rebuildBox());
        }
        return new Change(new SkinView(t.dim, next, Collections.unmodifiableMap(l)), dirty);
    }

    /**
     * One entry changed. A building new to the table is drawn as the rule says at once; a building already drawn keeps
     * its lamps as drawn (a flip waits for the throttle, {@link #due}) unless where or how its lamps are drawn changed.
     * A delta of another dimension than the table's is not for it.
     */
    public Change delta(SkinCodec.Delta d, long worldTime) {
        if (d.dim != dim) return new Change(this, Collections.emptyList());
        List<int[]> dirty = new ArrayList<>();
        SkinEntry was = table.get(d.campus, d.id);
        if (d.entry == null) {
            if (was == null) return new Change(this, dirty);
            dirty.add(was.rebuildBox());
            SkinTable next = table.remove(d.campus, d.id);
            return new Change(new SkinView(dim, next, Collections.unmodifiableMap(keep(next.entries()))), dirty);
        }
        SkinEntry e = d.entry;
        SkinTable next = table.put(e);
        Map<Long, Boolean> l = keep(next.entries());
        if (was == null) {
            l.put(e.id(), LampRule.lit(e, worldTime));
            dirty.add(e.rebuildBox());
        } else if (!java.util.Arrays.equals(was.rebuildBox(), e.rebuildBox()) || was.accent != e.accent
            || was.row != e.row
            || !was.shape.equals(e.shape)) {
                l.put(e.id(), LampRule.lit(e, worldTime));
                dirty.add(was.rebuildBox());
                dirty.add(e.rebuildBox());
            }
        return new Change(new SkinView(dim, next, Collections.unmodifiableMap(l)), dirty);
    }

    /** The buildings whose lamps the rule would draw otherwise than they are drawn now. */
    public List<Long> due(long worldTime) {
        List<Long> out = new ArrayList<>();
        for (SkinEntry e : table.entries()) if (LampRule.lit(e, worldTime) != lit(e)) out.add(e.id());
        return out;
    }

    /** The buildings the throttle let through flip to what the rule says now; their lamp boxes are rebuilt. */
    public Change flip(Collection<Long> ids, long worldTime) {
        if (ids.isEmpty()) return new Change(this, Collections.emptyList());
        Map<Long, Boolean> l = new HashMap<>(lit);
        List<int[]> dirty = new ArrayList<>();
        for (SkinEntry e : table.entries()) {
            if (!ids.contains(e.id())) continue;
            boolean now = LampRule.lit(e, worldTime);
            if (now == lit(e)) continue;
            l.put(e.id(), now);
            dirty.add(e.rebuildBox());
        }
        return new Change(new SkinView(dim, table, Collections.unmodifiableMap(l)), dirty);
    }

    /** The drawn states of the buildings still in the table. */
    private Map<Long, Boolean> keep(List<SkinEntry> entries) {
        Map<Long, Boolean> l = new HashMap<>();
        for (SkinEntry e : entries) {
            Boolean b = lit.get(e.id());
            if (b != null) l.put(e.id(), b);
        }
        return l;
    }
}
