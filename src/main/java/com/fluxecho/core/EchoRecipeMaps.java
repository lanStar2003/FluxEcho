package com.fluxecho.core;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import net.minecraft.item.ItemStack;

import com.fluxecho.FluxEcho;

import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;

/**
 * One recipe map per machine. The machines never look recipes up in them (each works out its result itself); a map
 * gives a machine GT's GUI layout (special slot, slot icons, progress bar) and NEI a page with examples. Should GT
 * refuse a map, that machine still works, with GT's plain GUI and no NEI page.
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
                    .neiHandlerInfo(h -> {
                        ItemStack icon = Machines.get(kind);
                        return icon == null ? h : h.setDisplayStack(icon);
                    });
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
}
