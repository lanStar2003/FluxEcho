package com.fluxecho.mobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.Mods;
import com.fluxecho.codex.Categories;
import com.fluxecho.core.EchoRecipeMaps;
import com.fluxecho.core.EchoRecipes;
import com.fluxecho.core.MachineId;
import com.fluxecho.core.Machines;
import com.fluxecho.logic.KillCost;
import com.kuba6000.mobsinfo.api.MobDrop;
import com.kuba6000.mobsinfo.api.MobRecipe;

import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.recipe.RecipeMap;

/**
 * Prey (needs MobsInfo for the drop tables). Only called when MobsInfo is loaded. The imprint and the machine are
 * registered even when the module is switched off; recipes are not, and the machine stops.
 */
public final class MobModule {

    public static Item imprint;

    private MobModule() {}

    static RecipeMap<?> map() {
        return EchoRecipeMaps.map(
            MachineId.MOB_ECHO,
            b -> b.maxIO(1, 4, 0, 0)
                .minInputs(0, 0)
                .useSpecialSlot()
                .slotOverlays(
                    (index, fluid, output, special) -> special ? GTUITextures.OVERLAY_SLOT_PAGE_PRINTED
                        : !output && !fluid ? GTUITextures.OVERLAY_SLOT_HAMMER : null));
    }

    public static void preInit() {
        imprint = new ItemMobImprint();
        GameRegistry.registerItem(imprint, "mob_imprint");
        MinecraftForge.EVENT_BUS.register(new MobEvents());
        if (Mods.waila) FMLInterModComms.sendMessage("Waila", "register", "com.fluxecho.mobs.MobWaila.register");
    }

    public static void machines() {
        Machines.put(MachineId.MOB_ECHO, new MTEMobEcho(Machines.claim(MachineId.MOB_ECHO)));
        map();
    }

    public static void postInit() {
        Categories.register(
            new Categories.Category(Categories.MOB, MobImprints::egg, MobImprints::displayName, MobImprints::keyOf));
        if (!Config.mobsEnabled || !Config.defaultRecipes) return;
        try {
            EchoRecipes.shapeless(
                "Prey Imprint",
                new ItemStack(imprint, 4),
                new Object[] { new ItemStack(Items.paper), new ItemStack(Items.paper), new ItemStack(Items.paper),
                    new ItemStack(Items.paper), new ItemStack(Items.rotten_flesh) });
            EchoRecipes.machine(
                MachineId.MOB_ECHO,
                new Object[] { "PEP", "CHC", "ASA", 'P', OrePrefixes.plate.get(Materials.Steel), 'E',
                    new ItemStack(Items.ender_pearl), 'C', OrePrefixes.circuit.get(Materials.LV), 'H',
                    ItemList.Hull_LV.get(1), 'A', ItemList.Robot_Arm_LV.get(1), 'S', new ItemStack(Items.iron_sword) });
        } catch (Throwable t) {
            FluxEcho.LOG.error("Failed to register the prey recipes", t);
        }
    }

    /**
     * NEI: one page per mob MobsInfo knows (it builds its tables in its own loadComplete, before this one): the
     * imprint, up to four of its drops, and the EU and time of a kill.
     */
    public static void loadComplete() {
        if (!Config.mobsEnabled || MobRecipe.MobNameToRecipeMap == null) return;
        int pages = 0;
        for (Map.Entry<String, MobRecipe> e : new TreeMap<>(MobRecipe.MobNameToRecipeMap).entrySet()) {
            MobRecipe r = e.getValue();
            if (r == null || r.mOutputs == null) continue;
            boolean boss = r.entity instanceof IBossDisplayData;
            if (boss && !Config.mobBosses) continue;
            List<ItemStack> drops = new ArrayList<>();
            for (MobDrop d : r.mOutputs) {
                if (d == null || d.stack == null || d.stack.getItem() == null || MTEMobEcho.banned(d.stack)) continue;
                drops.add(d.stack.copy());
                if (drops.size() == 4) break;
            }
            if (drops.isEmpty()) continue;
            KillCost cost = KillCost.of(
                r.maxEntityHealth,
                Config.mobEuPerHealth,
                boss ? Config.mobBossMultiplier : 1,
                (int) GTValues.V[MachineId.MOB_ECHO.tier],
                Config.mobMinTicks);
            EchoRecipeMaps.page(
                MachineId.MOB_ECHO,
                MobImprints.imprint(e.getKey()),
                new ItemStack[0],
                drops.toArray(new ItemStack[0]),
                cost.eut,
                cost.ticks);
            pages++;
        }
        FluxEcho.LOG.info("Prey Echo: {} mobs in NEI", pages);
    }
}
