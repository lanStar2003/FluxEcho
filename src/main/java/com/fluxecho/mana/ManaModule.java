package com.fluxecho.mana;

import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.core.EchoRecipeMaps;
import com.fluxecho.core.EchoRecipes;
import com.fluxecho.core.MachineId;
import com.fluxecho.core.Machines;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMap;

/**
 * Botania. Only called when Botania is loaded. The machine is registered even when the module is switched off, so
 * placed ones survive; the recipe is not, and the machine stops.
 */
public final class ManaModule {

    private ManaModule() {}

    static RecipeMap<?> map() {
        return EchoRecipeMaps.map(
            MachineId.MANA_ECHO,
            b -> b.maxIO(1, 1, 0, 0)
                .minInputs(0, 0));
    }

    public static void machines() {
        Machines.put(MachineId.MANA_ECHO, new MTEManaEcho(Machines.claim(MachineId.MANA_ECHO)));
        map();
    }

    /** The recipe takes a mana pool and a mana spreader: the machine repeats what your flowers did. */
    public static void postInit() {
        if (!Config.manaEnabled || !Config.defaultRecipes) return;
        try {
            Block pool = GameRegistry.findBlock("Botania", "pool");
            Block spreader = GameRegistry.findBlock("Botania", "spreader");
            if (pool == null || spreader == null) {
                FluxEcho.LOG.warn("No Botania mana pool or spreader found: the Mana Echo has no recipe");
                return;
            }
            EchoRecipes.machine(
                MachineId.MANA_ECHO,
                new Object[] { "PEP", "CHC", "SOS", 'P', OrePrefixes.plate.get(Materials.Aluminium), 'E',
                    new ItemStack(Items.ender_pearl), 'C', OrePrefixes.circuit.get(Materials.MV), 'H',
                    ItemList.Hull_MV.get(1), 'S', new ItemStack(spreader, 1, 0), 'O', new ItemStack(pool, 1, 0) });
        } catch (Throwable t) {
            FluxEcho.LOG.error("Failed to register the Mana Echo recipe", t);
        }
    }
}
