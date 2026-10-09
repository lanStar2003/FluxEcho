package com.fluxecho.nei;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import com.fluxecho.core.FluxMachineGui;
import com.fluxecho.core.MachineId;

import codechicken.nei.recipe.TemplateRecipeHandler.RecipeTransferRect;

/**
 * What NEI does when the machine itself is looked up (it is the page's catalyst): it takes the handler's transfer
 * rects and asks a fresh copy for "every recipe of" each rect's id. NEI's constructor collects the rects before ours
 * has set its id, which made that id null in 0.7.0 and 0.7.1 and failed every such lookup; this replays it.
 */
class CatalystLookupTest {

    private static Object field(RecipeTransferRect rect, String name) throws ReflectiveOperationException {
        Field f = RecipeTransferRect.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(rect);
    }

    @Test
    void theMachinesOwnLookupFindsItsPage() throws ReflectiveOperationException {
        for (MachineId kind : MachineId.values()) {
            if (kind == MachineId.MANA_ECHO) continue;
            FluxRecipeHandler handler = new FluxRecipeHandler(kind, null);
            assertFalse(handler.transferRects.isEmpty(), kind + " has a transfer rect");
            for (RecipeTransferRect rect : handler.transferRects) {
                String outputId = (String) field(rect, "outputId");
                Object[] results = (Object[]) field(rect, "results");
                assertEquals(FluxMachineGui.neiId(kind), outputId, kind + ": the rect leads to its page");
                FluxRecipeHandler copy = new FluxRecipeHandler(kind, null);
                assertDoesNotThrow(() -> copy.loadCraftingRecipes(outputId, results), kind.name());
            }
            assertEquals(FluxMachineGui.neiId(kind), handler.getHandlerId());
        }
    }

    @Test
    void anUnknownOrMissingIdIsIgnored() {
        FluxRecipeHandler handler = new FluxRecipeHandler(MachineId.SEED_ECHO, null);
        assertDoesNotThrow(() -> handler.loadCraftingRecipes(null));
        assertDoesNotThrow(() -> handler.loadCraftingRecipes("someone.else"));
        assertEquals(0, handler.numRecipes());
    }
}
