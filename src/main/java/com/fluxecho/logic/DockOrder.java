package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Which of the modules a nexus found it docks (0.14.1, the rank loop of {@code TileNexus.refreshDock}): every module
 * found has a number (the site the campus recorded or adopted it on, or the 0.9.2 inner ring slot it sits on) and a
 * rank: 0 on a site the campus recorded, 1 standing on a site of an active campus (adopted), 2 on a slot of the 0.9.2
 * inner ring. They are decided rank by rank, each rank in number order; the first module on a number takes it and a
 * later one is turned away ({@link #SLOT_TAKEN}).
 * <p>
 * Up to 0.14.0 every module on the inner ring also had to find room among the slots the nexus's phase opens, so a
 * campus module beyond the fourth (at phase II) stood undocked as "ring_full". Now a module on a campus site always
 * docks: the room ({@code open}, how many ring modules the nexus takes) is asked only of the 0.9.2 modules of rank 2,
 * after every campus module has been counted ({@link #RING_FULL}, or {@link #RING_CLOSED} when the ring is closed). A
 * module off the inner ring (on the axis, and later further out) takes no room.
 */
public final class DockOrder {

    /** Why a module is turned away: another module docked on its number first; the ring has no room; it is closed. */
    public static final String SLOT_TAKEN = "slot_taken", RING_FULL = "ring_full", RING_CLOSED = "ring_closed";
    /** The rank of a module on a recorded site, on an adopted campus site, on a slot of the 0.9.2 ring. */
    public static final int RECORDED = 0, ADOPTED = 1, LEGACY = 2;

    /** One decision: which module (its index in the lists given), and why it is turned away (null: it docks). */
    public static final class Verdict {

        public final int index;
        public final String reject;

        Verdict(int index, String reject) {
            this.index = index;
            this.reject = reject;
        }

        /** Whether the module docks. */
        public boolean docks() {
            return reject == null;
        }

        @Override
        public String toString() {
            return index + (reject == null ? " docks" : " " + reject);
        }
    }

    private DockOrder() {}

    /**
     * The decisions for the modules found, in the order they are made (and the docked modules are listed): rank by
     * rank, each rank in number order, the modules on one number in the order found.
     *
     * @param numbers the site or ring slot of each module found
     * @param ranks   its rank ({@link #RECORDED}, {@link #ADOPTED}, {@link #LEGACY})
     * @param open    how many modules the inner ring takes ({@code TileNexus.openSlots}); asked of rank 2 only
     */
    public static List<Verdict> decide(int[] numbers, int[] ranks, int open) {
        if (numbers.length != ranks.length) throw new IllegalArgumentException("a rank for every module");
        List<Verdict> out = new ArrayList<>();
        Set<Integer> taken = new HashSet<>();
        TreeSet<Integer> order = new TreeSet<>();
        for (int n : numbers) order.add(n);
        int ring = 0;
        for (int rank = RECORDED; rank <= LEGACY; rank++) for (int k : order) {
            boolean onRing = SiteFamily.ringSite(k);
            for (int i = 0; i < numbers.length; i++) {
                if (ranks[i] != rank || numbers[i] != k) continue;
                if (taken.contains(k)) out.add(new Verdict(i, SLOT_TAKEN));
                else if (rank == LEGACY && onRing && ring >= open)
                    out.add(new Verdict(i, open <= 0 ? RING_CLOSED : RING_FULL));
                else {
                    taken.add(k);
                    if (onRing) ring++;
                    out.add(new Verdict(i, null));
                }
            }
        }
        return Collections.unmodifiableList(out);
    }
}
