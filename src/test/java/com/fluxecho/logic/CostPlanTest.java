package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class CostPlanTest {

    private static final String[] SLOTS = { "gemFluxCrystal", "book", "gemFluxCrystal", "", "book" };

    private static int[] plan(int[] counts, String... specs) {
        List<ResearchTree.Cost> costs = Arrays.stream(specs)
            .map(ResearchTree.Cost::parse)
            .collect(java.util.stream.Collectors.toList());
        return CostPlan.plan(costs, counts, (c, i) -> SLOTS[i].equals(c.name.replace("minecraft:", "")));
    }

    @Test
    void takesAcrossSlotsInOrder() {
        assertArrayEquals(new int[] { 10, 0, 6, 0, 0 }, plan(new int[] { 10, 5, 64, 0, 5 }, "ore:gemFluxCrystal*16"));
        assertArrayEquals(
            new int[] { 4, 5, 0, 0, 3 },
            plan(new int[] { 10, 5, 64, 0, 5 }, "ore:gemFluxCrystal*4", "item:minecraft:book*8"));
    }

    @Test
    void notEnough() {
        assertNull(plan(new int[] { 10, 5, 5, 0, 5 }, "ore:gemFluxCrystal*16"));
        assertNull(plan(new int[] { 64, 0, 0, 0, 0 }, "ore:gemFluxCrystal*1", "item:minecraft:book*1"));
    }

    @Test
    void twoCostsOfTheSameItemDoNotCountItTwice() {
        assertNull(plan(new int[] { 3, 0, 0, 0, 0 }, "ore:gemFluxCrystal*2", "ore:gemFluxCrystal*2"));
        assertArrayEquals(
            new int[] { 4, 0, 0, 0, 0 },
            plan(new int[] { 4, 0, 0, 0, 0 }, "ore:gemFluxCrystal*2", "ore:gemFluxCrystal*2"));
    }
}
