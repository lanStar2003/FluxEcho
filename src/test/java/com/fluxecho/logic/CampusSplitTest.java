package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * How neighbouring campuses share the ground (0.15.0, {@link CampusSplit}): the split is symmetric, every column has
 * exactly one owner among campuses that reach it, the inner squares keep their pre-0.15.0 guard, a tie on the
 * bisector goes to the campus first by x then z, the registry's {@link CampusSplit#owns} agrees with
 * {@link CampusSplit#ours}; and the split bends round the outer diagonals, so a campus between neighbours at the old
 * spacing keeps whole sites for both of its outer buildings, and a site a campus claims is its own from both sides.
 */
@Tag("slow")
class CampusSplitTest {

    @Test
    void aLoneCampusOwnsEverything() {
        assertTrue(CampusSplit.ours(500, -500, 0, 0, null));
        assertTrue(CampusSplit.ours(500, -500, 0, 0, Collections.emptyList()));
        assertTrue(
            CampusSplit.ours(10, 10, 0, 0, Collections.singletonList(new int[] { 0, 0 })),
            "its own entry is ignored");
        assertEquals(128 + 16, CampusSplit.INNER_SPACING);
        assertEquals(144, CampusSplit.OLD_SPACING);
        assertEquals(312, CampusSplit.DEFAULT_SPACING);
        assertEquals(210, CampusSplit.OUTER_SPACING);
    }

    /** Two campuses 144 apart (the old spacing): each owns its square and its half of the rest, never the same one. */
    @Test
    void twoCampusesSplitSymmetrically() {
        int[][] pairs = { { 0, 0, 144, 0 }, { 0, 0, 0, -144 }, { 0, 0, 150, 90 }, { -20, 7, 131, -140 },
            { 0, 0, 144, 144 } };
        for (int[] p : pairs) {
            List<int[]> ofA = Collections.singletonList(new int[] { p[2], p[3] }),
                ofB = Collections.singletonList(new int[] { p[0], p[1] });
            int a = 0, b = 0, ties = 0;
            for (int x = Math.min(p[0], p[2]) - 110; x <= Math.max(p[0], p[2]) + 110; x++)
                for (int z = Math.min(p[1], p[3]) - 110; z <= Math.max(p[1], p[3]) + 110; z++) {
                    boolean oa = CampusSplit.ours(x, z, p[0], p[1], ofA), ob = CampusSplit.ours(x, z, p[2], p[3], ofB);
                    assertFalse(oa && ob, "both claim " + x + "," + z + " in " + Arrays.toString(p));
                    // a column both campuses' squares hold is neither's; every other has an owner
                    boolean both = CampusSplit.inInner(x, z, p[0], p[1]) && CampusSplit.inInner(x, z, p[2], p[3]);
                    assertEquals(!both, oa || ob, "an owner for " + x + "," + z);
                    if (CampusSplit.inInner(x, z, p[0], p[1]) && !both) assertTrue(oa, "its own square");
                    // the outer diagonals bend the split (a pair of overlapping sites goes to the first by x, z); the
                    // rest of the ground mirrors through the midpoint
                    int sa = CampusSplit.siteAt(x, z, p[0], p[1]), sb = CampusSplit.siteAt(x, z, p[2], p[3]);
                    if (sa >= 0 || sb >= 0) {
                        if (sa >= 0 && sb < 0 && !CampusSplit.inInner(x, z, p[2], p[3]))
                            assertTrue(oa, "its outer diagonal");
                        continue;
                    }
                    if (oa) a++;
                    if (ob) b++;
                    long da = (long) (x - p[0]) * (x - p[0]) + (long) (z - p[1]) * (z - p[1]),
                        db = (long) (x - p[2]) * (x - p[2]) + (long) (z - p[3]) * (z - p[3]);
                    // (a tie of a column only one ring holds stays with that ring)
                    if (da == db && !CampusSplit.inInner(x, z, p[0], p[1])
                        && !CampusSplit.inInner(x, z, p[2], p[3])
                        && OuterRing.inOuterDisc(x - p[0], z - p[1]) == OuterRing.inOuterDisc(x - p[2], z - p[3]))
                        ties++;
                }
            // the ground round the midpoint is the same for both; the bisector's columns all go to the first by x, z
            boolean aFirst = p[0] != p[2] ? p[0] < p[2] : p[1] < p[3];
            assertEquals(aFirst ? b + ties : b, aFirst ? a : a + ties, "as much for both " + Arrays.toString(p));
        }
    }

    @Test
    void aTieGoesToTheCampusFirstByXThenZ() {
        // (72, 100) is as far from (0, 0) as from (144, 0), and in neither square
        assertTrue(CampusSplit.nearer(72, 100, 0, 0, 144, 0));
        assertFalse(CampusSplit.nearer(72, 100, 144, 0, 0, 0));
        assertTrue(CampusSplit.nearer(100, 72, 0, 0, 0, 144));
        assertFalse(CampusSplit.nearer(100, 72, 0, 144, 0, 0));
        assertTrue(CampusSplit.ours(72, 100, 0, 0, Collections.singletonList(new int[] { 144, 0 })));
        assertFalse(CampusSplit.ours(72, 100, 144, 0, Collections.singletonList(new int[] { 0, 0 })));
    }

    /**
     * Random clusters of campuses, some claiming outer diagonals: no column is ever claimed twice, outside every square
     * none is left over, and {@link CampusSplit#owner} names the one that has it from every campus's view.
     */
    @Test
    void manyCampusesNeverClaimAColumnTwice() {
        Random rnd = new Random(15);
        for (int run = 0; run < 60; run++) {
            List<int[]> centres = new ArrayList<>();
            int n = 2 + rnd.nextInt(4);
            for (int tries = 0; centres.size() < n; tries++) {
                // the first ones may leave no room for the last: start over
                if (tries % 500 == 499) centres.clear();
                int[] c = { rnd.nextInt(400) - 200, rnd.nextInt(400) - 200, run % 3 == 0 ? 0 : rnd.nextInt(16) };
                boolean ok = true;
                for (int[] o : centres)
                    if (Math.max(Math.abs(o[0] - c[0]), Math.abs(o[1] - c[1])) < CampusSplit.INNER_SPACING) ok = false;
                if (ok) centres.add(c);
            }
            for (int i = 0; i < 600; i++) {
                // half of the columns on somebody's outer diagonal, where the claims decide
                int x, z;
                if (i % 2 == 0) {
                    int[] c = centres.get(rnd.nextInt(n));
                    List<int[]> cells = CampusSplit.siteCells(c[0], c[1], rnd.nextInt(4));
                    int[] cell = cells.get(rnd.nextInt(cells.size()));
                    x = cell[0];
                    z = cell[1];
                } else {
                    x = rnd.nextInt(700) - 350;
                    z = rnd.nextInt(700) - 350;
                }
                int owners = 0, who = -1;
                for (int k = 0; k < n; k++) {
                    int[] c = centres.get(k);
                    if (CampusSplit.ours(x, z, c[0], c[1], c[2], centres)) {
                        owners++;
                        who = k;
                    }
                }
                assertEquals(1, owners, "run " + run + " at " + x + "," + z);
                for (int k = 0; k < n; k++) {
                    int[] c = centres.get(k);
                    int o = CampusSplit.owner(x, z, c[0], c[1], c[2], centres);
                    assertEquals(who, o == -1 ? k : o, "the owner seen from " + k);
                }
            }
        }
    }

    /** The outer diagonals in the world: the same cells as the campus frame's sites, whichever way it faces. */
    @Test
    void theOuterDiagonalsLieTheSameWhicheverWayTheCampusFaces() {
        int cx = 37, cz = -411;
        int[][] fronts = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
        for (int[] f : fronts) {
            int mask = 0;
            for (int site : OuterRing.DIAG_SITES) {
                int q = CampusSplit.siteQuadrant(site, f[0], f[1]);
                mask |= 1 << q;
                Set<Long> want = new HashSet<>();
                for (long k : OuterRing.diagSiteCells(site)) {
                    int[] d = CampusPlan.toWorld(CampusPlan.keyA(k), CampusPlan.keyR(k), f[0], f[1]);
                    want.add(CampusPlan.key(cx + d[0], cz + d[1]));
                    assertEquals(q, CampusSplit.siteAt(cx + d[0], cz + d[1], cx, cz), "site " + site);
                }
                Set<Long> got = new HashSet<>();
                for (int[] c : CampusSplit.siteCells(cx, cz, q)) assertTrue(got.add(CampusPlan.key(c[0], c[1])));
                assertEquals(want, got, "site " + site + " facing " + Arrays.toString(f));
                assertEquals(916, got.size());
            }
            assertEquals(15, mask, "one in each quadrant");
        }
        // nowhere else
        int on = 0;
        for (int x = cx - 110; x <= cx + 110; x++)
            for (int z = cz - 110; z <= cz + 110; z++) if (CampusSplit.siteAt(x, z, cx, cz) >= 0) on++;
        assertEquals(4 * 916, on);
    }

    /** How many of a campus's outer diagonals are whole against its neighbours. */
    private static int whole(int cx, int cz, int claims, List<int[]> others) {
        int n = 0;
        for (int q = 0; q < 4; q++) if (CampusSplit.siteLoss(q, cx, cz, claims, others) == 0) n++;
        return n;
    }

    /**
     * A campus between two neighbours at the old spacing keeps two whole outer diagonals, so the batch's two buildings
     * both get a site (before, every one of its four crossed a bisector and both buildings were refused for good): on
     * opposite sides at 144 and at 150, one beside it and one in front, a corner of a grid. Every neighbour keeps one
     * whole at least and may claim the rest while nobody has (a campus last by its centre in a 2 x 2 grid keeps one
     * whole: its second building takes a site by its claim). With no neighbour, or neighbours 159 or more apart (the
     * sites reach 79 out), all four.
     */
    @Test
    void aCampusBetweenOldSpacedNeighboursKeepsTwoWholeSites() {
        assertEquals(4, whole(0, 0, 0, Collections.emptyList()));
        int[][][] layouts = { { { 150, 0 }, { -150, 0 } }, { { 144, 0 }, { -144, 0 } }, { { 0, 144 }, { 0, -144 } },
            { { 150, 0 }, { 0, -150 } }, { { 144, 0 }, { 0, 144 }, { 144, 144 } }, { { 144, 0 } } };
        for (int[][] l : layouts) {
            List<int[]> near = new ArrayList<>(Arrays.asList(l));
            assertTrue(whole(0, 0, 0, near) >= 2, "two whole sites among " + Arrays.deepToString(l));
            // every neighbour keeps a whole one at least, and may take all four while nobody claims any
            for (int[] o : l) {
                List<int[]> theirs = new ArrayList<>();
                theirs.add(new int[] { 0, 0 });
                for (int[] p : l) if (p != o) theirs.add(p);
                assertTrue(whole(o[0], o[1], 0, theirs) >= 1, Arrays.toString(o) + " of " + Arrays.deepToString(l));
                for (int q = 0; q < 4; q++) assertTrue(CampusSplit.siteLoss(q, o[0], o[1], 0, theirs) >= 0);
            }
        }
        assertEquals(4, whole(0, 0, 0, Arrays.asList(new int[] { 159, 0 }, new int[] { -159, 0 })));
    }

    /**
     * A claim is first come, first served (0.15.0): two campuses 144 apart both have an outer diagonal on the same
     * ground; the first by its centre keeps it whole while neither claims, the second may still claim its own, and
     * once it has, its site is whole from both sides (and in the registry's eyes) and the first's may no longer be
     * taken.
     */
    @Test
    void aClaimedSiteIsTheClaimersFromBothSides() {
        int bq = CampusSplit.quadrant(-1, 1), aq = CampusSplit.quadrant(1, 1);
        List<int[]> ofB = Collections.singletonList(new int[] { 0, 0, 0 });
        List<int[]> ofA = Collections.singletonList(new int[] { 144, 0, 0 });
        assertEquals(0, CampusSplit.siteLoss(aq, 0, 0, 0, ofA), "the first keeps its site whole");
        int lost = CampusSplit.siteLoss(bq, 144, 0, 0, ofB);
        assertTrue(lost > 0, "the second's is trimmed but may be claimed: " + lost);
        int claim = 1 << bq;
        assertEquals(0, CampusSplit.siteLoss(bq, 144, 0, claim, ofB), "whole once claimed");
        List<int[]> ofAClaimed = Collections.singletonList(new int[] { 144, 0, claim });
        assertEquals(-1, CampusSplit.siteLoss(aq, 0, 0, 0, ofAClaimed), "the first may no longer take its own");
        for (int[] c : CampusSplit.siteCells(144, 0, bq)) {
            assertTrue(CampusSplit.ours(c[0], c[1], 144, 0, claim, ofB), "the claimer's from its side");
            assertFalse(CampusSplit.ours(c[0], c[1], 0, 0, 0, ofAClaimed), "and from the other's");
            assertTrue(CampusSplit.owns(c[0], c[1], 144, 0, OuterRing.OUTER_R, claim, 0, 0, 0), "and the registry's");
        }
        // a site reaching a neighbour's inner square may never be taken
        assertEquals(-1, CampusSplit.siteLoss(aq, 0, 0, 0, Collections.singletonList(new int[] { 130, 0 })));
    }

    /**
     * Eight neighbours round a campus at the old spacing: four sites meet at every corner of the grid, so a campus
     * keeps one whole site, may still claim the other three while nobody has, and none once its neighbours have.
     */
    @Test
    void aFullGridLeavesEachCornerToWhoeverClaimsItFirst() {
        List<int[]> near = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) if (dx != 0 || dz != 0) near.add(new int[] { 144 * dx, 144 * dz, 0 });
        assertEquals(1, whole(0, 0, 0, near));
        for (int q = 0; q < 4; q++) assertTrue(CampusSplit.siteLoss(q, 0, 0, 0, near) >= 0, "quadrant " + q);
        for (int[] o : near) o[2] = 15;
        for (int q = 0; q < 4; q++) assertEquals(-1, CampusSplit.siteLoss(q, 0, 0, 0, near), "quadrant " + q);
    }

    /**
     * The registry's view of another campus's ground ({@link CampusSplit#owns}): its square always, its outer reach
     * only where the split gives the column to it; a campus registered with the inner radius owns only its square.
     */
    @Test
    void theRegistrysOwnerAgreesWithTheSplit() {
        int ox = 144, oz = 30;
        for (int x = -120; x <= 260; x += 3) for (int z = -120; z <= 160; z += 3) {
            boolean inner = CampusSplit.owns(x, z, ox, oz, CampusPlan.GRADE_R, 0, 0);
            assertEquals(CampusSplit.inInner(x, z, ox, oz), inner, "a campus without an outer ring: its square");
            boolean outer = CampusSplit.owns(x, z, ox, oz, OuterRing.OUTER_R, 0, 0);
            if (CampusSplit.inInner(x, z, ox, oz)) assertTrue(outer);
            else if (Math.max(Math.abs(x - ox), Math.abs(z - oz)) > OuterRing.OUTER_R) assertFalse(outer);
            else assertEquals(
                !CampusSplit.ours(x, z, 0, 0, Collections.singletonList(new int[] { ox, oz })),
                outer,
                "the split decides " + x + "," + z);
            // with claims on either side as well
            if (CampusSplit.inInner(x, z, ox, oz) || CampusSplit.inInner(x, z, 0, 0)
                || Math.max(Math.abs(x - ox), Math.abs(z - oz)) > OuterRing.OUTER_R) continue;
            for (int[] cl : new int[][] { { 15, 0 }, { 0, 15 }, { 5, 10 } }) assertEquals(
                !CampusSplit.ours(x, z, 0, 0, cl[1], Collections.singletonList(new int[] { ox, oz, cl[0] })),
                CampusSplit.owns(x, z, ox, oz, OuterRing.OUTER_R, cl[0], 0, 0, cl[1]),
                "the split with claims decides " + x + "," + z);
        }
    }

    /**
     * Two rings offset at an angle leave no strip between them that neither lays: every column of either campus's
     * outer disc outside the inner squares goes to a campus whose own disc holds it, so the side it goes to grades it
     * (before, a column nearer to the neighbour but outside the neighbour's ring was graded by neither: up to 129
     * columns at offsets such as (194, 40)).
     */
    @Test
    void offsetRingsLeaveNoStripThatNeitherLays() {
        for (int ox = 144; ox <= 220; ox += 2) for (int oz = 0; oz <= ox; oz += 6) {
            List<int[]> ofA = Collections.singletonList(new int[] { ox, oz }),
                ofB = Collections.singletonList(new int[] { 0, 0 });
            for (int x = -97; x <= ox + 97; x++) for (int z = Math.min(0, oz) - 97; z <= Math.max(0, oz) + 97; z++) {
                boolean inA = OuterRing.inOuterDisc(x, z), inB = OuterRing.inOuterDisc(x - ox, z - oz);
                if (!inA && !inB || CampusSplit.inInner(x, z, 0, 0) && CampusSplit.inInner(x, z, ox, oz)) continue;
                boolean oa = CampusSplit.ours(x, z, 0, 0, ofA), ob = CampusSplit.ours(x, z, ox, oz, ofB);
                assertTrue(oa != ob, "one owner for " + x + "," + z + " with B at " + ox + "," + oz);
                assertTrue(oa ? inA : inB, "its owner's ring holds " + x + "," + z + " with B at " + ox + "," + oz);
            }
        }
    }
}
