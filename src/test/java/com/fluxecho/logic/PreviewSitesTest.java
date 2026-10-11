package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Where the nexus's NEI city preview stands the module kinds ({@link PreviewSites}): each on the first free site of its
 * own, the order of registration deciding; a kind whose sites are all taken is left out instead of standing on a ring
 * slot over its neighbours; only kinds the campus does not build take ring slots, which are counted apart from sites.
 */
class PreviewSitesTest {

    private static final int[] LIBRARY = { 4, 2, 6 }, ECO_DOME = EcoDomeShape.SITES, VAULT = { 6, 2, 4 };

    @Test
    void phaseOneShowsTheArchiveBehindAndTheDomeToTheRight() {
        List<PreviewSites.Place> p = PreviewSites.places(Arrays.asList(LIBRARY, ECO_DOME), RingSlots.open(1));
        assertEquals(2, p.size());
        assertEquals(4, p.get(0).site);
        assertEquals(2, p.get(1).site);
        for (PreviewSites.Place q : p) assertEquals(-1, q.slot);
    }

    @Test
    void aKindWhoseSitesAreTakenIsLeftOut() {
        // three hall kinds fill the three hall sites; a fourth hall kind has nowhere of its own and stands nowhere
        int[] fourth = { 2, 4, 6 };
        List<PreviewSites.Place> p = PreviewSites
            .places(Arrays.asList(LIBRARY, ECO_DOME, VAULT, fourth, new int[] { 1, 7 }), RingSlots.open(3));
        assertEquals(5, p.size());
        assertEquals(4, p.get(0).site);
        assertEquals(2, p.get(1).site);
        assertEquals(6, p.get(2).site);
        assertFalse(
            p.get(3)
                .placed(),
            "no hall site is free: " + p.get(3));
        assertEquals(-1, p.get(3).site);
        assertEquals(-1, p.get(3).slot, "never a ring slot for a kind the campus builds");
        // a kind left out takes no room: the pavilion after it still stands
        assertEquals(1, p.get(4).site);
    }

    /**
     * The zenith and the root (0.12.0) stand on their own axis sites and take no room on the ring: at phase II (four
     * modules) the Archive, the Eco Dome and the dome generator all stand, and a full ring still leaves an axis kind
     * after it standing.
     */
    @Test
    void axisKindsTakeNoRoom() {
        int[] zenith = DomeGeneratorShape.SITES, root = { SiteFamily.ROOT_SITE };
        List<PreviewSites.Place> p = PreviewSites
            .places(Arrays.asList(LIBRARY, ECO_DOME, zenith, root), RingSlots.open(2));
        assertEquals(4, p.size());
        assertEquals(4, p.get(0).site);
        assertEquals(2, p.get(1).site);
        assertEquals(SiteFamily.ZENITH_SITE, p.get(2).site);
        assertEquals(SiteFamily.ROOT_SITE, p.get(3).site);
        // room for one: the Archive stands, the Eco Dome is left out, the zenith still stands
        List<PreviewSites.Place> one = PreviewSites.places(Arrays.asList(LIBRARY, ECO_DOME, zenith, VAULT), 1);
        assertEquals(3, one.size(), "the kinds after the last axis kind are not looked at");
        assertEquals(4, one.get(0).site);
        assertFalse(
            one.get(1)
                .placed());
        assertEquals(SiteFamily.ZENITH_SITE, one.get(2).site);
    }

    @Test
    void noTwoKindsShareASite() {
        List<int[]> prefs = new ArrayList<>();
        for (int i = 0; i < 6; i++) prefs.add(i % 2 == 0 ? LIBRARY : ECO_DOME);
        List<PreviewSites.Place> p = PreviewSites.places(prefs, RingSlots.SLOTS);
        boolean[] used = new boolean[RingSlots.SLOTS];
        int placed = 0;
        for (PreviewSites.Place q : p) {
            if (!q.placed()) continue;
            assertTrue(q.site >= 0);
            assertFalse(used[q.site], "site " + q.site + " twice");
            used[q.site] = true;
            placed++;
        }
        assertEquals(3, placed, "three hall sites, three halls");
    }

    @Test
    void ringSlotsAreCountedApartFromSites() {
        // a kind the campus does not build takes ring slot 0 even with the library on site 4 and the dome on site 2,
        // and the ring slots it takes leave the sites of the same numbers free
        List<int[]> prefs = Arrays.asList(null, LIBRARY, null, null, ECO_DOME);
        List<PreviewSites.Place> p = PreviewSites.places(prefs, RingSlots.SLOTS);
        assertEquals(0, p.get(0).slot);
        assertEquals(-1, p.get(0).site);
        assertEquals(4, p.get(1).site);
        assertEquals(1, p.get(2).slot);
        assertEquals(2, p.get(3).slot);
        assertEquals(2, p.get(4).site, "ring slot 2 is not site 2");
        // a full ring leaves out the kinds that need a slot, not the ones with sites
        List<int[]> many = new ArrayList<>();
        for (int i = 0; i < RingSlots.SLOTS + 1; i++) many.add(null);
        many.add(ECO_DOME);
        List<PreviewSites.Place> q = PreviewSites.places(many, RingSlots.SLOTS + 1);
        for (int i = 0; i < RingSlots.SLOTS; i++) assertEquals(i, q.get(i).slot);
        assertFalse(
            q.get(RingSlots.SLOTS)
                .placed(),
            "the ring is full");
        assertEquals(2, q.get(RingSlots.SLOTS + 1).site);
    }

    @Test
    void theRoomTheyDockStopsThem() {
        List<PreviewSites.Place> p = PreviewSites.places(Arrays.asList(LIBRARY, ECO_DOME, VAULT), RingSlots.open(1));
        assertEquals(2, p.size(), "phase I docks two");
        assertEquals(
            0,
            PreviewSites.places(Arrays.asList(LIBRARY), 0)
                .size());
    }
}
