package com.fluxecho.core;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import net.minecraft.item.ItemStack;

import com.fluxecho.FluxEcho;

import gregtech.api.enums.GTValues;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.util.GTRecipeBuilder;

/**
 * One recipe map per machine. The machines never look recipes up in them (each works out its result itself); a map
 * holds the examples of the machine's NEI page ({@code FluxRecipeHandler}, in the flux look; GT's own page for the
 * map is switched off). Should GT refuse a map, that machine still works, without an NEI page.
 */
public final class EchoRecipeMaps {

    private static final Map<MachineId, RecipeMap<?>> MAPS = new EnumMap<>(MachineId.class);
    private static final Set<MachineId> TRIED = EnumSet.noneOf(MachineId.class);

    private EchoRecipeMaps() {}

    /**
     * The machine's map, built on first use. NEI names it by the lang key {@code fluxecho.recipe.<key>}.
     *
     * @param setup slot counts and icons; the arrow progress bar and the NEI tab icon are set here
     */
    public static synchronized RecipeMap<?> map(MachineId kind,
        UnaryOperator<RecipeMapBuilder<RecipeMapBackend>> setup) {
        if (TRIED.add(kind)) {
            try {
                RecipeMapBuilder<RecipeMapBackend> b = RecipeMapBuilder.of("fluxecho.recipe." + kind.key)
                    .progressBar(GTUITextures.PROGRESSBAR_ARROW)
                    .disableRegisterNEI();
                MAPS.put(
                    kind,
                    setup.apply(b)
                        .build());
            } catch (Throwable t) {
                FluxEcho.LOG.error("GT refused the recipe map of {}; it works without an NEI page", kind, t);
            }
        }
        return MAPS.get(kind);
    }

    /**
     * Adds an example to the machine's NEI page: the sample in the special slot (or none), inputs, outputs, and its
     * power and time (0 when they depend on the case; the page explains them in words then).
     */
    public static void page(MachineId kind, ItemStack special, ItemStack[] inputs, ItemStack[] outputs, int eut,
        int ticks) {
        RecipeMap<?> map = get(kind);
        if (map == null) return;
        try {
            GTRecipeBuilder b = GTValues.RA.stdBuilder()
                .itemInputs(inputs == null ? new ItemStack[0] : inputs)
                .itemOutputs(outputs == null ? new ItemStack[0] : outputs)
                .duration(Math.max(1, ticks))
                .eut(Math.max(0, eut))
                .fake();
            if (special != null) b.special(special);
            b.addTo(map);
        } catch (RuntimeException e) {
            FluxEcho.LOG.debug("No NEI page for an example of {}", kind, e);
        }
    }

    /** The machine's map if it was made, without making it. */
    public static synchronized RecipeMap<?> get(MachineId kind) {
        return MAPS.get(kind);
    }
}
