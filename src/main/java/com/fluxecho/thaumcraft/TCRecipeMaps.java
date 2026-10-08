package com.fluxecho.thaumcraft;

import com.fluxecho.core.EchoRecipeMaps;
import com.fluxecho.core.MachineId;

import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.recipe.RecipeMap;

/** The Thaumcraft machines' recipe maps: GUI layout only, their work has no fixed recipes to show. */
final class TCRecipeMaps {

    private TCRecipeMaps() {}

    static RecipeMap<?> essentiaEcho() {
        return EchoRecipeMaps.map(
            MachineId.ESSENTIA_ECHO,
            b -> b.maxIO(2, 1, 0, 0)
                .minInputs(0, 0));
    }

    static RecipeMap<?> insightEcho() {
        return EchoRecipeMaps.map(
            MachineId.INSIGHT_ECHO,
            b -> b.maxIO(1, 1, 0, 0)
                .minInputs(0, 0)
                .useSpecialSlot()
                .slotOverlays(
                    (index, fluid, output, special) -> special ? GTUITextures.OVERLAY_SLOT_DATA_ORB
                        : !output && !fluid ? GTUITextures.OVERLAY_SLOT_PAGE_BLANK : null));
    }

    static RecipeMap<?> crucibleEcho() {
        return EchoRecipeMaps.map(
            MachineId.CRUCIBLE_ECHO,
            b -> b.maxIO(2, 1, 0, 0)
                .minInputs(0, 0));
    }

    static RecipeMap<?> infusionEcho() {
        return EchoRecipeMaps.map(
            MachineId.INFUSION_ECHO,
            b -> b.maxIO(9, 1, 0, 0)
                .minInputs(0, 0)
                .useSpecialSlot()
                .slotOverlays(
                    (index, fluid, output, special) -> special ? GTUITextures.OVERLAY_SLOT_MOLECULAR_2 : null));
    }
}
