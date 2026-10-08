package com.fluxecho.mana;

import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.core.EchoRecipes;
import com.fluxecho.core.MachineId;
import com.fluxecho.core.Machines;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;

/**
 * Botania. Only called when Botania is loaded. The Mana Echo Spring is registered even when the module is switched
 * off, so placed ones survive; the recipe is not, and the spring stops. It has its own GUI and NEI page
 * ({@link SpringNei}), no GT recipe map.
 */
public final class ManaModule {

    private ManaModule() {}

    public static void machines() {
        ManaTextures.load();
        Machines.put(MachineId.MANA_ECHO, new MTEManaSpring(Machines.claim(MachineId.MANA_ECHO)));
    }

    /**
     * The recipe takes a mana pool and a mana spreader, the proof that mana was made by hand once. It is an LV recipe:
     * the spring comes without a core, and the circuit put in it (LV to LuV) sets its tier.
     */
    public static void postInit() {
        if (!Config.manaEnabled || !Config.defaultRecipes) return;
        try {
            Block pool = GameRegistry.findBlock("Botania", "pool");
            Block spreader = GameRegistry.findBlock("Botania", "spreader");
            if (pool == null || spreader == null) {
                FluxEcho.LOG.warn("No Botania mana pool or spreader found: the Mana Echo Spring has no recipe");
                return;
            }
            EchoRecipes.machine(
                MachineId.MANA_ECHO,
                new Object[] { "PEP", "CHC", "SOS", 'P', OrePrefixes.plate.get(Materials.Steel), 'E',
                    new ItemStack(Items.ender_pearl), 'C', OrePrefixes.circuit.get(Materials.LV), 'H',
                    ItemList.Hull_LV.get(1), 'S', new ItemStack(spreader, 1, 0), 'O', new ItemStack(pool, 1, 0) });
        } catch (Throwable t) {
            FluxEcho.LOG.error("Failed to register the Mana Echo Spring recipe", t);
        }
    }
}
