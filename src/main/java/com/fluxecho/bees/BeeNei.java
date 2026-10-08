package com.fluxecho.bees;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.core.EchoRecipeMaps;
import com.fluxecho.core.MachineId;
import com.fluxecho.logic.Chromosomes;

import cpw.mods.fml.common.registry.GameRegistry;
import forestry.api.apiculture.BeeManager;
import forestry.api.apiculture.EnumBeeType;
import forestry.api.apiculture.IBee;
import forestry.api.genetics.IAllele;
import gregtech.api.enums.GTValues;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTUtility;

/** The bee machines' recipe maps (GUI layout) and their NEI pages. */
public final class BeeNei {

    private static final String FOREST = "forestry.speciesForest", MEADOWS = "forestry.speciesMeadows";

    private BeeNei() {}

    static RecipeMap<?> imprinterMap() {
        return EchoRecipeMaps.map(
            MachineId.BEE_IMPRINTER,
            b -> b.maxIO(2, 1, 0, 0)
                .minInputs(0, 0)
                .useSpecialSlot()
                .slotOverlays(
                    (index, fluid, output, special) -> special ? GTUITextures.OVERLAY_SLOT_BEE_QUEEN
                        : !output && !fluid && index == 0 ? GTUITextures.OVERLAY_SLOT_PAGE_BLANK : null));
    }

    static RecipeMap<?> incubatorMap() {
        return EchoRecipeMaps.map(
            MachineId.BEE_INCUBATOR,
            b -> b.maxIO(1, 2, 0, 0)
                .minInputs(0, 0)
                .useSpecialSlot()
                .slotOverlays(
                    (index, fluid, output, special) -> special ? GTUITextures.OVERLAY_SLOT_PAGE_PRINTED
                        : output && index == 0 ? GTUITextures.OVERLAY_SLOT_BEE_QUEEN
                            : output && index == 1 ? GTUITextures.OVERLAY_SLOT_BEE_DRONE : null));
    }

    /**
     * Imprinter: making an imprint and copying genes. Incubator: one page per species with a template, so looking up
     * any princess in NEI shows that an imprint of it grows more.
     */
    static void addPages() {
        try {
            imprinterPages();
        } catch (Throwable t) {
            FluxEcho.LOG.error("Failed to add the Bee Imprinter NEI pages", t);
        }
        try {
            incubatorPages();
        } catch (Throwable t) {
            FluxEcho.LOG.error("Failed to add the Larva Incubator NEI pages", t);
        }
    }

    private static Map<String, String> template(String species) {
        IAllele[] t = BeeManager.beeRoot.getTemplate(species);
        return t == null ? null : BeeImprints.genes(t);
    }

    private static void imprinterPages() {
        RecipeMap<?> map = imprinterMap();
        Map<String, String> forest = template(FOREST), meadows = template(MEADOWS);
        if (map == null || forest == null || meadows == null) return;
        ItemStack sample = BeeImprints.stack(BeeImprints.bee(forest, null), EnumBeeType.PRINCESS, 1);
        GTValues.RA.stdBuilder()
            .special(sample)
            .itemInputs(new ItemStack(Items.paper))
            .itemOutputs(BeeImprints.imprint(forest))
            .duration(Config.imprintTicks)
            .eut(Config.imprintEut)
            .fake()
            .addTo(map);
        Map<String, String> edited = Chromosomes
            .transfer(meadows, forest, Chromosomes.forCircuit(Chromosomes.CIRCUIT_ENVIRONMENT));
        GTValues.RA.stdBuilder()
            .special(sample)
            .itemInputs(BeeImprints.imprint(meadows), GTUtility.getIntegratedCircuit(Chromosomes.CIRCUIT_ENVIRONMENT))
            .itemOutputs(BeeImprints.imprint(edited))
            .duration(Config.imprintTicks)
            .eut(Config.imprintEut)
            .fake()
            .addTo(map);
    }

    private static void incubatorPages() {
        RecipeMap<?> map = incubatorMap();
        ItemStack honey = GameRegistry.findItemStack("Forestry", "honeyDrop", Math.max(1, Config.honeyPerPrincess));
        if (map == null) return;
        Map<String, IAllele[]> templates = new HashMap<>(BeeManager.beeRoot.getGenomeTemplates());
        ItemStack[] inputs = Config.honeyPerPrincess > 0 && honey != null ? new ItemStack[] { honey }
            : new ItemStack[0];
        int pages = 0;
        for (IAllele[] t : templates.values()) {
            Map<String, String> genes = BeeImprints.genes(t);
            IBee bee = genes.containsKey(Chromosomes.SPECIES) ? BeeImprints.bee(genes, null) : null;
            if (bee == null) continue;
            ItemStack princess = BeeImprints.stack(bee, EnumBeeType.PRINCESS, 1);
            ItemStack[] outputs = Config.dronesWithPrincess > 0
                ? new ItemStack[] { princess, BeeImprints.stack(bee, EnumBeeType.DRONE, Config.dronesWithPrincess) }
                : new ItemStack[] { princess };
            GTValues.RA.stdBuilder()
                .special(BeeImprints.imprint(genes))
                .itemInputs(inputs)
                .itemOutputs(outputs)
                .duration(Config.incubateTicks)
                .eut(Config.incubateEut)
                .fake()
                .addTo(map);
            pages++;
        }
        FluxEcho.LOG.info("Larva Incubator: {} species in NEI", pages);
    }
}
