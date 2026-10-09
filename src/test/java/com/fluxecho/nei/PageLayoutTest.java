package com.fluxecho.nei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.SlotLayout;

/**
 * NEI draws its bookmark and overlay buttons over the lower right corner of every recipe; on the Prey Echo's pages a
 * mob with many drops put its third row of outputs under them.
 */
class PageLayoutTest {

    /** Where NEI's buttons are on a page of this height: the last 14 pixels of width, the lower 32 of height. */
    private static boolean underButtons(int x, int y, int w, int h) {
        int left = FluxRecipeHandler.WIDTH - 14, top = FluxRecipeHandler.HEIGHT - 32;
        return x + w > left && y + h > top;
    }

    @Test
    void outputsStayClearOfNeisButtons() {
        for (int n = 1; n <= 9; n++) {
            int[][] slots = SlotLayout
                .grid(n, FluxRecipeHandler.outputsRight(n), FluxRecipeHandler.AREA_TOP, FluxRecipeHandler.AREA_H, true);
            for (int[] s : slots) assertFalse(
                underButtons(s[0], s[1], SlotLayout.SLOT, SlotLayout.SLOT),
                n + " outputs: a slot at " + s[0] + "," + s[1] + " is under NEI's buttons");
        }
    }

    @Test
    void fewOutputsKeepTheirPlace() {
        assertEquals(160, FluxRecipeHandler.outputsRight(1));
        assertEquals(160, FluxRecipeHandler.outputsRight(4));
        assertEquals(160, FluxRecipeHandler.outputsRight(6));
    }
}
