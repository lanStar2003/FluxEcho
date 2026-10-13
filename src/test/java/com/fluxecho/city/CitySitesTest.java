package com.fluxecho.city;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.fluxecho.campus.ModuleSpec;
import com.fluxecho.library.LibraryModule;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.CityOffer;
import com.fluxecho.logic.SiteFamily;

/**
 * The sites of the real module specs (0.14.1; 0.15.0 the outer diagonals' two): every building may stand on any site of
 * its family (its preference list holds them all), and wherever each stands, no module on one site keeps out another's
 * footprint or protects a cell of
 * another's plan, for every facing; so a building moved onto its family's other site, or taking it because its
 * favourite was taken, never blocks or breaks a neighbour, and the offer's candidates are all real.
 */
class CitySitesTest {

    private static final int CX = 1000, Y0 = 70, CZ = -500;
    private static final int[][] FACINGS = { { 1, 0 }, { 0, 1 }, { -1, 0 }, { 0, -1 } };

    private static List<ModuleSpec> specs() {
        return Arrays.asList(
            LibraryModule.spec(),
            CityModule.ecoDome(),
            CityModule.domeGenerator(),
            CityModule.root(),
            CityModule.arcaneCourt(),
            CityModule.bloodCovenant(),
            CityModule.huntingGround(),
            CityModule.manaFountain(),
            CityModule.resonanceGate(),
            CityModule.dualFoilVault(),
            CityModule.sophonArray(),
            CityModule.holoTable());
    }

    @Test
    void everySpecPrefersEverySiteOfItsFamily() {
        for (ModuleSpec s : specs()) {
            TreeSet<Integer> pref = new TreeSet<>(), family = new TreeSet<>();
            for (int site : s.sites) pref.add(site);
            for (int site : s.family.sites()) family.add(site);
            assertEquals(family, pref, s.key);
            assertEquals(s.sites.length, pref.size(), s.key + " lists a site twice");
            // the inner city's buildings at phase II; the outer diagonals' (0.15.0) at phase III, on laid ground
            int phase = SiteFamily.opensAt(s.sites[0]);
            assertArrayEquals(s.sites, s.candidates(Math.max(2, phase), g -> true), s.key + "'s candidates");
            if (s.family.ground() == null) assertArrayEquals(s.sites, s.candidates(2, g -> false), s.key);
            else assertEquals(0, s.candidates(phase, g -> false).length, s.key + " waits for its ground");
            for (int site : s.family.sites()) assertTrue(s.hasSite(site), s.key + " on " + site);
        }
        // the inner ring's room: the halls at phase I, every ring site from phase II
        assertEquals(3, SiteFamily.openRingSites(1));
        assertEquals(8, SiteFamily.openRingSites(2));
        assertEquals(0, SiteFamily.openRingSites(0));
        assertEquals(8, SiteFamily.openRingSites(5));
    }

    /**
     * For every facing, every spec standing on each of its sites: its keep-out boxes miss the footprint (with its
     * margin) of every spec on every other site, and its protected boxes hold no cell of their plans (a plan cell in a
     * protected box would stop that job as PROTECTED for good).
     */
    @Test
    void noSiteBlocksOrBreaksAnother() {
        CampusPlan plan = new CampusPlan(12345L);
        for (int[] f : FACINGS) {
            List<ModuleSpec> specs = specs();
            Map<String, BuildPlan.Plan> plans = new HashMap<>();
            Map<String, int[]> extents = new HashMap<>();
            for (ModuleSpec b : specs) for (int sb : b.sites) {
                BuildPlan.Plan p = b.plan(plan, sb, CX, Y0, CZ, f[0], f[1]);
                plans.put(b.key + "@" + sb, p);
                extents.put(b.key + "@" + sb, cube(p));
            }
            for (ModuleSpec a : specs) for (int sa : a.sites) {
                List<int[]> keep = a.keepOut(plan, sa, CX, Y0, CZ, f[0], f[1]);
                List<int[]> prot = a.protect(plan, sa, CX, Y0, CZ, f[0], f[1]);
                for (ModuleSpec b : specs) for (int sb : b.sites) {
                    if (sb == sa) continue;
                    String where = a.key + "@" + sa + " / " + b.key + "@" + sb + " facing " + Arrays.toString(f);
                    if (!a.axis() && !b.axis()) {
                        int[] foot = b.footprint(sb, CX, CZ, f[0], f[1], 1);
                        for (int[] k : keep) assertFalse(CityOffer.overlap(foot, k), "keep-out " + where);
                    }
                    int[] ext = extents.get(b.key + "@" + sb);
                    List<int[]> near = new ArrayList<>();
                    for (int[] box : prot) if (meets(box, ext)) near.add(box);
                    if (near.isEmpty()) continue;
                    for (BuildPlan.Step st : plans.get(b.key + "@" + sb).steps) {
                        for (int[] box : near) assertFalse(
                            st.x >= box[0] && st.x <= box[3]
                                && st.y >= box[1]
                                && st.y <= box[4]
                                && st.z >= box[2]
                                && st.z <= box[5],
                            "protect " + where + " at " + st);
                    }
                }
            }
        }
    }

    /** The box {x0, y0, z0, x1, y1, z1} of a plan's steps. */
    private static int[] cube(BuildPlan.Plan p) {
        int[] c = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
            Integer.MIN_VALUE };
        for (BuildPlan.Step s : p.steps) {
            c[0] = Math.min(c[0], s.x);
            c[1] = Math.min(c[1], s.y);
            c[2] = Math.min(c[2], s.z);
            c[3] = Math.max(c[3], s.x);
            c[4] = Math.max(c[4], s.y);
            c[5] = Math.max(c[5], s.z);
        }
        return c;
    }

    /** Whether two boxes {x0, y0, z0, x1, y1, z1} meet. */
    private static boolean meets(int[] a, int[] b) {
        return a[0] <= b[3] && b[0] <= a[3] && a[1] <= b[4] && b[1] <= a[4] && a[2] <= b[5] && b[2] <= a[5];
    }
}
