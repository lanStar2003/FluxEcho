package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** The kit the city's shapes are drawn with, as {@code city/gen/kit.py} and {@code b_hall.py} define it. */
class ShapeKitTest {

    @Test
    void theLegendIsUniqueAndComplete() {
        String checked = "FBgPCRZKAd#e:%k*WLwnqpcrtGhou^";
        Set<Integer> seen = new LinkedHashSet<>();
        for (char ch : checked.toCharArray()) {
            assertTrue(ShapeKit.checked(ch), "'" + ch + "'");
            assertTrue(seen.add(ShapeKit.partOf(ch)), "two characters for one part at '" + ch + "'");
        }
        for (char ch : ShapeKit.SOFT.toCharArray()) {
            assertTrue(ShapeKit.soft(ch));
            assertFalse(ShapeKit.checked(ch));
            assertTrue(ShapeKit.partOf(ch) >= 0, "a soft cell is built: '" + ch + "'");
        }
        assertEquals(Parts.AIR, ShapeKit.partOf('-'));
        assertEquals(-1, ShapeKit.partOf('~'));
        assertEquals(-1, ShapeKit.partOf(' '));
        assertFalse(ShapeKit.checked('-'));
        assertTrue(ShapeKit.checked('~'));
        assertThrows(IllegalArgumentException.class, () -> ShapeKit.partOf('S'), "the Archive's shelf is no city part");
        assertEquals(Parts.fitting(Parts.F_LIFT), ShapeKit.partOf('^'), "the light-lift pad (0.12.0)");
        assertThrows(IllegalArgumentException.class, () -> ShapeKit.partOf('Q'), "the canvas comes later");
    }

    /**
     * Every soft character but grass, dirt and water has a checked twin that places the same part; a gate niche
     * (CITY.md §4.15) turns a wall's cells into those twins, so the same blocks stand, unchecked.
     */
    @Test
    void softTwinsAndTheGateNiche() {
        for (char ch : ShapeKit.SOFT.toCharArray()) {
            char twin = ShapeKit.checkedTwin(ch);
            if (",;=".indexOf(ch) >= 0) {
                assertEquals(ch, twin, "no checked twin for '" + ch + "'");
                continue;
            }
            assertTrue(ShapeKit.checked(twin), "'" + ch + "' -> '" + twin + "'");
            assertEquals(ShapeKit.partOf(twin), ShapeKit.partOf(ch), "'" + ch + "' places '" + twin + "'s part");
        }
        assertEquals('G', ShapeKit.checkedTwin('G'));
        ShapeKit.Shape s = new ShapeKit.Shape("t", 5, 6, 3);
        for (int x = 0; x < 5; x++) {
            s.set(x, 1, 2, 'w');
            for (int y = 2; y <= 4; y++) s.set(x, y, 2, x == 2 ? 'L' : x == 1 ? 'G' : 'W');
        }
        int checked = s.checkedCount();
        s.gateNiche(1, 3, 1, 4, 2);
        assertEquals(12, s.niche.size());
        assertEquals(checked - 12, s.checkedCount());
        assertEquals(12, s.softCount());
        assertEquals('[', s.get(1, 1, 2));
        assertEquals('/', s.get(1, 3, 2));
        assertEquals('!', s.get(2, 3, 2));
        assertEquals('{', s.get(3, 3, 2));
        assertEquals('w', s.get(0, 1, 2), "outside the niche");
        assertTrue(
            s.symmetric()
                .isEmpty(),
            "soft cells need no mirror");
        assertThrows(IllegalArgumentException.class, () -> s.gateNiche(0, 0, 0, 0, 0), "an empty cell is no wall");
        s.set(4, 5, 2, 'n');
        assertThrows(IllegalArgumentException.class, () -> s.gateNiche(4, 4, 5, 5, 2), "a cornice is no niche");
    }

    /** The kit's spiral is the Archive's: one step a ring cell, half a block each, a panel on every whole block. */
    @Test
    void theSpiralIsTheArchives() {
        List<int[]> left = ShapeKit.spiral(2, 2, 1, ArchiveShape.STEPS, 0, 1);
        List<int[]> right = ShapeKit.spiral(24, 2, 1, ArchiveShape.STEPS, 0, -1);
        List<int[]> archive = ArchiveShape.stairSteps();
        for (int i = 0; i < ArchiveShape.STEPS; i++) {
            int[] l = left.get(i), r = right.get(i);
            assertArrayEquals(archive.get(i), new int[] { l[0], l[1], l[2] }, "left step " + i);
            assertArrayEquals(archive.get(ArchiveShape.STEPS + i), new int[] { r[0], r[1], r[2] }, "right step " + i);
            assertEquals(ArchiveShape.tread(i) ? 't' : 'W', l[3]);
        }
        // from stand 1 the twelfth step tops out at stand 7
        int[] last = left.get(11);
        assertEquals(6, last[1]);
        assertEquals('W', last[3]);
    }

    @Test
    void thePortal() {
        ShapeKit.Shape s = new ShapeKit.Shape("t", 27, 8, 4);
        ShapeKit.portal(s, 0, 5, true, 13);
        assertEquals(48, s.airCount());
        assertArrayEquals(new int[] { 13, 2, 0 }, s.controller);
        assertEquals('K', s.get(13, 1, 0));
        assertEquals(14, s.decor.size());
        for (int x = 9; x <= 17; x++) assertEquals('n', s.get(x, 5, 0));
    }

    @Test
    void anglesAndOctants() {
        assertEquals(0, ShapeKit.angle(0, 5), 1e-12);
        assertEquals(90, ShapeKit.angle(5, 0), 1e-12);
        assertEquals(270, ShapeKit.angle(-5, 0), 1e-12);
        assertEquals(180, ShapeKit.angle(0, -5), 1e-12);
        assertEquals(0, ShapeKit.offOctant(ShapeKit.angle(3, 3)), 1e-9);
        assertEquals(22.5, ShapeKit.offOctant(22.5), 1e-12);
        assertEquals(5.0, ShapeKit.hypot(3, 4), 0);
        assertTrue(ShapeKit.octo(3, 4, 4, 7));
        assertFalse(ShapeKit.octo(4, 4, 4, 7));
    }

    @Test
    void boundariesAndBalls() {
        Set<Long> square = new LinkedHashSet<>();
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) square.add(ShapeKit.key(x, z));
        assertEquals(
            16,
            ShapeKit.boundary(square)
                .size());
        assertEquals(
            16,
            ShapeKit.thickBoundary(square)
                .size());
        Set<Long> diamond = new LinkedHashSet<>();
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++) if (Math.abs(x) + Math.abs(z) <= 3) diamond.add(ShapeKit.key(x, z));
        assertTrue(
            ShapeKit.thickBoundary(diamond)
                .size()
                > ShapeKit.boundary(diamond)
                    .size(),
            "the thick edge closes the diagonal steps");
        assertTrue(ShapeKit.ballShell(0, 5, 0, 0, 0, 0, 5));
        assertFalse(ShapeKit.ballShell(0, 0, 0, 0, 0, 0, 5));
        assertFalse(ShapeKit.ballShell(0, 6, 0, 0, 0, 0, 5));
        assertEquals(
            ShapeKit.disc(0, 0, 3)
                .size(),
            ShapeKit.ring(0, 0, 0, 3.0001)
                .size());
    }

    /** A box with a half-block step, a full-block step and a lift: the walk takes the first and the lift only. */
    @Test
    void theWalkClimbsHalfBlocksAndRidesLifts() {
        ShapeKit.Shape s = new ShapeKit.Shape("walk", 6, 12, 1);
        for (int x = 0; x < 6; x++) s.set(x, 0, 0, 'F');
        s.set(1, 1, 0, 't');
        s.set(2, 1, 0, 'W');
        s.set(3, 1, 0, 'W');
        s.set(3, 2, 0, 'W');
        Set<Long> r = s.walk(Collections.singletonList(new int[] { 0, 0, 2 }), 0);
        assertTrue(r.contains(ShapeKit.Shape.state(1, 0, 3)), "onto the tread");
        assertTrue(r.contains(ShapeKit.Shape.state(2, 0, 4)), "a half block up again");
        assertFalse(r.contains(ShapeKit.Shape.state(3, 0, 6)), "a whole block is too much");
        assertFalse(r.contains(ShapeKit.Shape.state(4, 0, 2)), "nor past it");

        ShapeKit.Shape lift = new ShapeKit.Shape("lift", 3, 12, 1);
        for (int x = 0; x < 3; x++) lift.set(x, 0, 0, 'F');
        lift.lift(1, 0, 0);
        lift.lift(1, 6, 0);
        lift.set(2, 6, 0, 'd');
        Set<Long> up = lift.walk(Arrays.asList(new int[] { 0, 0, 2 }), 0);
        assertTrue(up.contains(ShapeKit.Shape.state(1, 0, 14)), "the pads join");
        assertTrue(up.contains(ShapeKit.Shape.state(2, 0, 14)), "and the deck beside the top pad");
    }

    @Test
    void unitsNeedCells() {
        ShapeKit.Shape s = new ShapeKit.Shape("u", 3, 3, 3);
        s.set(1, 1, 1, 'o');
        assertThrows(
            IllegalArgumentException.class,
            () -> s.unit("x", "planter", Collections.singletonList(new int[] { 0, 0, 0 }), 1, null));
        s.unit("x", "planter", Collections.singletonList(new int[] { 1, 1, 1 }), 1, null);
        assertEquals(
            1,
            s.units()
                .size());
        assertThrows(IllegalArgumentException.class, () -> s.set(3, 0, 0, 'W'));
    }
}
