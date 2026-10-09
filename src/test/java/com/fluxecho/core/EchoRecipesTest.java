package com.fluxecho.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * GT's ore names ({@code OrePrefixes.plate.get(Materials.Steel)}) are objects that only spell "plateSteel"; Forge's
 * ore recipes turn them down ("Invalid shaped ore recipe"), so the backup recipe gets the name itself.
 */
class EchoRecipesTest {

    /** Stands in for the object GT hands back: it is only its name. */
    private static final class OreName {

        private final String name;

        OreName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @Test
    void gtOreNamesBecomeTheirNames() {
        Object[] recipe = { "PEP", "CHC", 'P', new OreName("plateSteel"), 'C', new OreName("circuitBasic"), 'E',
            "enderpearl", 'H', null };
        Object[] forge = EchoRecipes.forForge(recipe);
        assertArrayEquals(
            new Object[] { "PEP", "CHC", 'P', "plateSteel", 'C', "circuitBasic", 'E', "enderpearl", 'H', null },
            forge);
        assertEquals(OreName.class, recipe[3].getClass(), "the recipe GT gets is left alone");
    }
}
