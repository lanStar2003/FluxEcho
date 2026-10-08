package com.fluxecho.blood;

import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.codex.Categories;
import com.fluxecho.core.EchoRecipeMaps;
import com.fluxecho.core.EchoRecipes;
import com.fluxecho.core.MachineId;
import com.fluxecho.core.Machines;
import com.fluxecho.logic.BloodRates;

import WayofTime.alchemicalWizardry.api.items.interfaces.IBloodOrb;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.recipe.RecipeMap;

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

    /** Blood Magic's orbs, weakest first. */
    private static final String[] ORBS = { "weakBloodOrb", "apprenticeBloodOrb", "magicianBloodOrb", "masterBloodOrb",
        "archmageBloodOrb", "transcendentBloodOrb" };

    /** NEI: one page per orb, with its LP a tick and the EU for it; the LP goes into the altar nearby. */
    private static void neiPages() {
        Block altar = GameRegistry.findBlock("AWWayofTime", "Altar");
        ItemStack[] out = altar == null ? new ItemStack[0] : new ItemStack[] { new ItemStack(altar) };
        ItemStack[] in = Config.lpPerMeat > 0 ? new ItemStack[] { new ItemStack(Items.rotten_flesh) }
            : new ItemStack[0];
        for (String name : ORBS) {
            Item orb = GameRegistry.findItem("AWWayofTime", name);
            if (!(orb instanceof IBloodOrb o)) continue;
            int rate = BloodRates.lpPerTick(Config.lpPerTick, o.getOrbLevel());
            if (rate > 0)
                EchoRecipeMaps.page(MachineId.BLOOD_ECHO, new ItemStack(orb), in, out, rate * Config.euPerLp, 20);
        }
    }

    /** {@code modid:name} of an orb, the key of the ledger. */
    static String key(ItemStack s) {
        GameRegistry.UniqueIdentifier id = s == null ? null : GameRegistry.findUniqueIdentifierFor(s.getItem());
        return id == null ? null : id.modId + ":" + id.name;
    }

    static ItemStack stack(String key) {
        int i = key.indexOf(':');
        Item item = i <= 0 ? null : GameRegistry.findItem(key.substring(0, i), key.substring(i + 1));
        return item == null ? null : new ItemStack(item);
    }

    public static void machines() {
        Machines.put(MachineId.BLOOD_ECHO, new MTEBloodEcho(Machines.claim(MachineId.BLOOD_ECHO)));
        map();
    }

    /**
     * The recipe takes a blood altar and a sacrificial knife: the machine repeats what you did with them by hand.
     */
    public static void postInit() {
        if (Config.bloodEnabled) neiPages();
        Categories.register(new Categories.Category(Categories.ORB, BloodModule::stack, key -> {
            ItemStack s = stack(key);
            return s == null ? key : s.getDisplayName();
        }, s -> s != null && s.getItem() instanceof IBloodOrb ? key(s) : null));
        if (!Config.bloodEnabled || !Config.defaultRecipes) return;
        try {
            Block altar = GameRegistry.findBlock("AWWayofTime", "Altar");
            Item knife = GameRegistry.findItem("AWWayofTime", "sacrificialKnife");
            if (altar == null || knife == null) {
                FluxEcho.LOG.warn("No blood altar or sacrificial knife found: the Blood Echo has no recipe");
                return;
            }
            EchoRecipes.machine(
                MachineId.BLOOD_ECHO,
                new Object[] { "PEP", "CHC", "UAK", 'P', OrePrefixes.plate.get(Materials.Aluminium), 'E',
                    new ItemStack(Items.ender_pearl), 'C', OrePrefixes.circuit.get(Materials.MV), 'H',
                    ItemList.Hull_MV.get(1), 'U', ItemList.Electric_Pump_MV.get(1), 'A', new ItemStack(altar), 'K',
                    new ItemStack(knife) });
        } catch (Throwable t) {
            FluxEcho.LOG.error("Failed to register the Blood Echo recipe", t);
        }
    }
}
