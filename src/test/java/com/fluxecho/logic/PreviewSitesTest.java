package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

    /**
     * The pavilions (0.13.0) take the diagonals by their preference lists, and with the inner ring's room of eight at
     * phase III every kind stands at once on a site of its own: three halls, the four pavilions, the gatehouse and both
     * axis kinds. Taken in another order the pavilions still each find a diagonal.
     */
    @Test
    void thePavilionsTakeTheDiagonals() {
        int[] mana = ManaFountainShape.SITES, arcane = ArcaneCourtShape.SITES, hunting = HuntingGroundShape.SITES,
            blood = BloodCovenantShape.SITES, gate = { 0 }, zenith = DomeGeneratorShape.SITES,
            root = { SiteFamily.ROOT_SITE };
        assertEquals(Arrays.toString(new int[] { 7, 1, 5, 3 }), Arrays.toString(arcane));
        assertEquals(Arrays.toString(new int[] { 1, 7, 3, 5 }), Arrays.toString(mana));
        assertEquals(Arrays.toString(new int[] { 3, 5, 1, 7 }), Arrays.toString(hunting));
        assertEquals(Arrays.toString(new int[] { 5, 3, 7, 1 }), Arrays.toString(blood));
        List<PreviewSites.Place> p = PreviewSites.places(
            Arrays.asList(LIBRARY, ECO_DOME, zenith, root, arcane, VAULT, mana, hunting, blood, gate),
            RingSlots.open(3));
        assertEquals(10, p.size());
        int[] want = { 4, 2, SiteFamily.ZENITH_SITE, SiteFamily.ROOT_SITE, 7, 6, 1, 3, 5, 0 };
        java.util.Set<Integer> sites = new java.util.HashSet<>();
        for (int i = 0; i < want.length; i++) {
            assertEquals(want[i], p.get(i).site, "kind " + i);
            assertEquals(-1, p.get(i).slot);
            assertTrue(sites.add(p.get(i).site));
        }
        // the pavilions in the other order: each still takes the first diagonal no earlier one took
        List<PreviewSites.Place> q = PreviewSites
            .places(Arrays.asList(blood, hunting, mana, arcane), RingSlots.open(3));
        assertEquals(5, q.get(0).site);
        assertEquals(3, q.get(1).site);
        assertEquals(1, q.get(2).site);
        assertEquals(7, q.get(3).site);
        // phase II's room of four: the Archive, the Eco Dome, the court and one more stand; the axis kinds always
        List<PreviewSites.Place> two = PreviewSites
            .places(Arrays.asList(LIBRARY, ECO_DOME, zenith, root, arcane, mana, hunting), RingSlots.open(2));
        assertEquals(7, two.get(4).site);
        assertEquals(1, two.get(5).site);
        assertEquals(6, two.size(), "the room is out after the fourth ring module");
    }

    /**
     * The nexus's preview stands at each phase as many ring kinds as the phase opens ring sites (0.14.1,
     * {@link SiteFamily#openRingSites}), as the campus builds them: tier 7 (phase II) is the whole inner city, all ten
     * kinds of the registration order on their first preferences (0.14.0's room of four left the hunting ground, the
     * mana fountain, the gate and the vault out); tier 6 (phase I) has room for the three halls.
     */
    @Test
    void phaseTwoStandsTheWholeCity() {
        int[] zenith = DomeGeneratorShape.SITES, root = { SiteFamily.ROOT_SITE };
        List<int[]> all = Arrays.asList(
            LIBRARY,
            ECO_DOME,
            zenith,
            root,
            ArcaneCourtShape.SITES,
            BloodCovenantShape.SITES,
            HuntingGroundShape.SITES,
            ManaFountainShape.SITES,
            new int[] { 0 },
            VAULT);
        List<PreviewSites.Place> p = PreviewSites.places(all, SiteFamily.openRingSites(2));
        assertEquals(10, p.size());
        int[] want = { 4, 2, SiteFamily.ZENITH_SITE, SiteFamily.ROOT_SITE, 7, 5, 3, 1, 0, 6 };
        for (int i = 0; i < want.length; i++) assertEquals(want[i], p.get(i).site, "kind " + i);
        assertEquals(3, SiteFamily.openRingSites(1));
        assertEquals(
            3,
            PreviewSites.places(Arrays.asList(LIBRARY, ECO_DOME, VAULT), SiteFamily.openRingSites(1))
                .size());
    }

    /** The NEI city preview's budget (CITY.md §3, M10): the nexus and every module it stands, in checked cells. */
    private static final int PREVIEW_BUDGET = 60_000;

    /**
     * The preview's heaviest tier at each phase stays within its budget with the modules of this version standing: the
     * nexus's phase, the Archive, the Eco Dome, the dome generator (moored, or floating from phase IV), the Tidal Well
     * and, from phase II, all four pavilions and (0.14.0) the gatehouse and the Dual-foil Vault (all of them stand from
     * phase II since 0.14.1, {@link #phaseTwoStandsTheWholeCity}), and (0.15.0) from phase III the Sophon Array and the
     * Holo Sand Table on the outer diagonals.
     */
    @Test
    void thePreviewStaysWithinItsBudget() {
        for (int phase = 1; phase <= NexusShape.PHASES; phase++) {
            int cells = NexusShape.phase(phase)
                .cells()
                .size()
                + ArchiveShape.BLUEPRINT.cells()
                    .size()
                + EcoDomeShape.BLUEPRINT.cells()
                    .size()
                + RootShape.BLUEPRINT.cells()
                    .size()
                + (phase >= 4 ? DomeGeneratorShape.FLOATING_BP : DomeGeneratorShape.MOORED_BP).cells()
                    .size();
            if (phase >= 2) cells += ArcaneCourtShape.BLUEPRINT.cells()
                .size()
                + BloodCovenantShape.BLUEPRINT.cells()
                    .size()
                + HuntingGroundShape.BLUEPRINT.cells()
                    .size()
                + ManaFountainShape.BLUEPRINT.cells()
                    .size()
                // 0.14.0: the gatehouse on the gate and the Dual-foil Vault on a hall
                + ResonanceGateShape.BLUEPRINT.cells()
                    .size()
                + DualFoilVaultShape.BLUEPRINT.cells()
                    .size();
            // 0.15.0: from phase III the outer diagonals' Sophon Array and Holo Sand Table
            if (phase >= 3) cells += SophonArrayShape.BLUEPRINT.cells()
                .size()
                + HoloTableShape.BLUEPRINT.cells()
                    .size();
            assertTrue(cells <= PREVIEW_BUDGET, "phase " + phase + ": " + cells + " cells");
        }
        assertEquals(
            2370 + 136,
            ResonanceGateShape.BLUEPRINT.cells()
                .size(),
            "the gate's checked cells and its door air");
        assertArrayEquals(new int[] { 0 }, ResonanceGateShape.SITES, "the gate stands on site 0 alone");
        assertEquals(
            4644 + 48,
            DualFoilVaultShape.BLUEPRINT.cells()
                .size(),
            "the vault's checked cells and its door air");
        assertArrayEquals(VAULT, DualFoilVaultShape.SITES, "the vault prefers 6, then 2, then 4");
        assertEquals(
            2058 + 160,
            ArcaneCourtShape.BLUEPRINT.cells()
                .size(),
            "the court's checked cells and its portals' air");
        assertEquals(
            1694 + 160,
            BloodCovenantShape.BLUEPRINT.cells()
                .size());
        assertEquals(
            1531 + 88,
            HuntingGroundShape.BLUEPRINT.cells()
                .size());
        assertEquals(
            1107 + 160,
            ManaFountainShape.BLUEPRINT.cells()
                .size());
    }

    /**
     * The outer diagonals' buildings (0.15.0) in the preview from phase III (tier 8): after the whole inner city, the
     * Sophon Array on O1 (site 11) and the Holo Sand Table on O7 (17), their first preferences, taking no ring room;
     * their cells count toward the budget ({@link #thePreviewStaysWithinItsBudget}).
     */
    @Test
    void phaseThreeStandsTheOuterDiagonals() {
        int[] zenith = DomeGeneratorShape.SITES, root = { SiteFamily.ROOT_SITE };
        List<int[]> all = Arrays.asList(
            LIBRARY,
            ECO_DOME,
            zenith,
            root,
            ArcaneCourtShape.SITES,
            BloodCovenantShape.SITES,
            HuntingGroundShape.SITES,
            ManaFountainShape.SITES,
            new int[] { 0 },
            VAULT,
            SophonArrayShape.SITES,
            HoloTableShape.SITES);
        List<PreviewSites.Place> p = PreviewSites.places(all, SiteFamily.openRingSites(3));
        assertEquals(12, p.size());
        int[] want = { 4, 2, SiteFamily.ZENITH_SITE, SiteFamily.ROOT_SITE, 7, 5, 3, 1, 0, 6, 11, 17 };
        for (int i = 0; i < want.length; i++) assertEquals(want[i], p.get(i).site, "kind " + i);
        assertEquals(
            2397 + 128,
            SophonArrayShape.BLUEPRINT.cells()
                .size(),
            "the array's checked cells and its portals' air");
        assertEquals(
            2148 + 150,
            HoloTableShape.BLUEPRINT.cells()
                .size(),
            "the table's checked cells and its portals' air");
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

    /**
     * The outer diagonals' kinds (0.15.0) lie off the inner ring as the axis kinds do: they take no ring room and
     * still stand once the ring is full, each on the first of its sites no kind before it took.
     */
    @Test
    void outerKindsTakeNoRoom() {
        int[] sophon = { 11, 13, 15, 17 }, table = { 13, 11, 15, 17 }, again = { 11, 13, 15, 17 };
        List<PreviewSites.Place> p = PreviewSites.places(Arrays.asList(LIBRARY, ECO_DOME, sophon, table, again), 1);
        assertEquals(5, p.size());
        assertEquals(4, p.get(0).site);
        assertFalse(
            p.get(1)
                .placed(),
            "the ring is full");
        assertEquals(11, p.get(2).site);
        assertEquals(13, p.get(3).site);
        assertEquals(15, p.get(4).site, "the next free outer diagonal");
    }
}
