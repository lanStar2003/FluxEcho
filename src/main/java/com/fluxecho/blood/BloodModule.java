package com.fluxecho.blood;

import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.core.EchoRecipeMaps;
import com.fluxecho.core.MachineId;
import com.fluxecho.core.Machines;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTModHandler;

/**
 * Blood Magic. Only called when Blood Magic is loaded. The machine is registered even when the module is switched
 * off, so placed ones survive; the recipe is not, and the machine stops.
 */
public final class BloodModule {

    private BloodModule() {}

    static RecipeMap<?> map() {
        return EchoRecipeMaps.map(
            MachineId.BLOOD_ECHO,
            b -> b.maxIO(1, 1, 0, 0)
                .minInputs(0, 0)
                .useSpecialSlot()
                .slotOverlays((index, fluid, output, special) -> special ? GTUITextures.OVERLAY_SLOT_DATA_ORB : null));
    }

    public static void machines() {
        Machines.put(MachineId.BLOOD_ECHO, new MTEBloodEcho(Machines.claim(MachineId.BLOOD_ECHO)));
        map();
    }

    /**
     * The recipe takes a blood altar and a sacrificial knife: the machine repeats what you did with them by hand.
     */
    public static void postInit() {
        if (!Config.bloodEnabled || !Config.defaultRecipes) return;
        try {
            Block altar = GameRegistry.findBlock("AWWayofTime", "Altar");
            Item knife = GameRegistry.findItem("AWWayofTime", "sacrificialKnife");
            if (altar == null || knife == null) {
                FluxEcho.LOG.warn("No blood altar or sacrificial knife found: the Blood Echo has no recipe");
                return;
            }
            GTModHandler.addCraftingRecipe(
                Machines.get(MachineId.BLOOD_ECHO),
                GTModHandler.RecipeBits.NOT_REMOVABLE,
                new Object[] { "PEP", "CHC", "UAK", 'P', OrePrefixes.plate.get(Materials.Aluminium), 'E',
                    new ItemStack(Items.ender_pearl), 'C', OrePrefixes.circuit.get(Materials.MV), 'H',
                    ItemList.Hull_MV.get(1), 'U', ItemList.Electric_Pump_MV.get(1), 'A', new ItemStack(altar), 'K',
                    new ItemStack(knife) });
        } catch (Throwable t) {
            FluxEcho.LOG.error("Failed to register the Blood Echo recipe", t);
        }
    }
}
