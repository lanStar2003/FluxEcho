package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Where the nexus's NEI city preview (tiers 6 to 10, CITY.md §3) stands each module kind (0.11.0), so that no two
 * modules ever overlap. A kind the campus builds (it has a module spec) stands only where the campus raises it: the
 * first of its preferred sites (all of its own family) that no earlier kind took; when every one is taken it is left
 * out rather than put anywhere else. A kind the campus does not build stands on the first free inner ring slot, as in
 * 0.9.2. Sites and ring slots are different places and are counted apart: hall site 2 is a footprint from radial 24
 * out on the nexus's right, ring slot 2 a point at the inner radius.
 */
public final class PreviewSites {

    /** Where a kind stands: one of its sites, an inner ring slot, or neither (left out). */
    public static final class Place {

        /** The site it stands on, or -1. */
        public final int site;
        /** The inner ring slot it stands on (a kind the campus does not build), or -1. */
        public final int slot;

        Place(int site, int slot) {
            this.site = site;
            this.slot = slot;
        }

        /** Whether it stands anywhere: false when every site it may take (or every ring slot) was taken already. */
        public boolean placed() {
            return site >= 0 || slot >= 0;
        }

        @Override
        public String toString() {
            return site >= 0 ? "site " + site : slot >= 0 ? "ring slot " + slot : "left out";
        }
    }

    private PreviewSites() {}

    /**
     * The places of the module kinds in their order until {@code room} of them stand: {@code preferred.get(i)} is kind
     * {@code i}'s sites in order of preference, or null for a kind the campus does not build. The list holds a place
     * for every kind looked at before the room ran out (so it may be shorter than {@code preferred}); a kind left out
     * takes no room.
     */
    public static List<Place> places(List<int[]> preferred, int room) {
        List<Place> out = new ArrayList<>();
        Set<Integer> sites = new HashSet<>();
        boolean[] slots = new boolean[RingSlots.SLOTS];
        int placed = 0;
        for (int[] pref : preferred) {
            if (placed >= room) break;
            int site = -1, slot = -1;
            if (pref != null) {
                for (int s : pref) if (!sites.contains(s)) {
                    site = s;
                    break;
                }
                if (site >= 0) sites.add(site);
            } else {
                for (int k = 0; k < slots.length && slot < 0; k++) if (!slots[k]) slot = k;
                if (slot >= 0) slots[slot] = true;
            }
            Place p = new Place(site, slot);
            if (p.placed()) placed++;
            out.add(p);
        }
        return Collections.unmodifiableList(out);
    }
}
