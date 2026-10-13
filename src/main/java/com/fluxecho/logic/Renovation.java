package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The one-time renovations of built city modules (0.14.1). When a release changes a cell of a module's shape that the
 * structure check does not look at (a soft cell), modules built before still form and the repair job, which never
 * clears, leaves the old block where it was. So each module kind lists, revision by revision, the cells of its own
 * frame ({@link ShapeKit} coordinates) to change and the part each held; a formed module whose saved revision is below
 * its kind's current one changes every listed cell that still holds exactly the old part, and saves the current
 * revision. It needs no job, never unforms anything (only soft cells are listed) and leaves anything else a player
 * put in such a cell alone.
 * <p>
 * Revisions are only ever appended per kind; a kind with none is at revision 0. Eco Dome revision 1 (0.14.1): the
 * crown deck's grass at (12, 11, 22) over the crown stair's fourth step becomes air, as the shape now leaves it
 * ({@link EcoDomeShape}): with it there, nobody could climb past stand 9.
 */
public final class Renovation {

    /** One cell to change: the revision it belongs to, the cell in the module's frame, the part it held and gets. */
    public static final class Change {

        public final int revision, x, y, z, from, to;

        Change(int revision, int x, int y, int z, int from, int to) {
            this.revision = revision;
            this.x = x;
            this.y = y;
            this.z = z;
            this.from = from;
            this.to = to;
        }
    }

    /** The world as the renovation sees it, in the module's frame. */
    public interface Cells {

        /** Whether the cell's chunk is loaded. */
        boolean loaded(int x, int y, int z);

        /** Whether the cell holds exactly the part (its block and its meta). */
        boolean holds(int x, int y, int z, int part);

        /** Puts the part in the cell ({@link Parts#AIR}: empties it). */
        void place(int x, int y, int z, int part);
    }

    private static final Map<String, List<Change>> CHANGES = new HashMap<>();

    static {
        add(EcoDomeShape.KEY, 1, 12, EcoDomeShape.CROWN_Y, 22, Parts.GRASS, Parts.AIR);
    }

    private Renovation() {}

    private static void add(String kind, int revision, int x, int y, int z, int from, int to) {
        List<Change> l = CHANGES.computeIfAbsent(kind, k -> new ArrayList<>());
        if (!l.isEmpty() && l.get(l.size() - 1).revision > revision)
            throw new IllegalStateException(kind + ": revisions are appended in order");
        l.add(new Change(revision, x, y, z, from, to));
    }

    /** The kind's current revision: the last one it lists, 0 for a kind with none. */
    public static int current(String kind) {
        List<Change> l = CHANGES.get(kind);
        return l == null || l.isEmpty() ? 0 : l.get(l.size() - 1).revision;
    }

    /** The changes a module of the kind saved at {@code revision} still has to make, oldest first. */
    public static List<Change> due(String kind, int revision) {
        List<Change> l = CHANGES.get(kind);
        if (l == null) return Collections.emptyList();
        List<Change> out = new ArrayList<>();
        for (Change c : l) if (c.revision > revision) out.add(c);
        return Collections.unmodifiableList(out);
    }

    /**
     * Renovates a formed module of the kind saved at {@code revision}: every due cell that holds exactly its old part
     * gets the new one, the others are left as they are. Returns the revision to save: the kind's current one, or
     * {@code revision} unchanged (and nothing touched) while a due cell's chunk is not loaded, so it is tried again.
     */
    public static int apply(String kind, int revision, Cells cells) {
        List<Change> due = due(kind, revision);
        if (due.isEmpty()) return Math.max(revision, current(kind));
        for (Change c : due) if (!cells.loaded(c.x, c.y, c.z)) return revision;
        for (Change c : due) if (cells.holds(c.x, c.y, c.z, c.from)) cells.place(c.x, c.y, c.z, c.to);
        return current(kind);
    }
}
