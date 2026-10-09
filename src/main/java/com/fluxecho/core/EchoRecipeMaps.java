package com.fluxecho.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;

import net.minecraft.item.ItemStack;

import com.fluxecho.FluxEcho;

import gregtech.api.enums.GTValues;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.util.GTRecipe;
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
    public static synchronized void page(MachineId kind, ItemStack special, ItemStack[] inputs, ItemStack[] outputs,
        int eut, int ticks) {
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

    private static final Map<MachineId, BooleanSupplier> LATE = new EnumMap<>(MachineId.class);

    /**
     * Examples that can only be made later than the game's start (MobsInfo builds its drop tables when a world
     * starts): {@code builder} is asked before the page is shown until it says it is done.
     */
    public static synchronized void later(MachineId kind, BooleanSupplier builder) {
        LATE.put(kind, builder);
    }

    /**
     * The examples of a machine's page, with the late ones made first if they can be by now. NEI asks from its own
     * threads, so this and the late builders hold the lock while the map is read or filled.
     */
    public static synchronized List<GTRecipe> examples(MachineId kind) {
        BooleanSupplier late = LATE.get(kind);
        if (late != null) {
            try {
                if (late.getAsBoolean()) LATE.remove(kind);
            } catch (RuntimeException e) {
                LATE.remove(kind);
                FluxEcho.LOG.warn("Could not make the late NEI examples of {}", kind, e);
            }
        }
        RecipeMap<?> map = MAPS.get(kind);
        List<GTRecipe> out = new ArrayList<>();
        if (map != null) for (GTRecipe r : map.getAllRecipes()) if (!r.mHidden) out.add(r);
        return out;
    }

    /** The machine's map if it was made, without making it. */
    public static synchronized RecipeMap<?> get(MachineId kind) {
        return MAPS.get(kind);
    }
}
