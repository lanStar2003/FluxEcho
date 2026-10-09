package com.fluxdepths.shard;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.fluxdepths.FluxDepths;
import com.fluxdepths.RecipeGuard;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;

/**
 * Crafting table recipes. The imprinter and the Flux Shard Collector are bronze-age (ender pearls are the link to the
 * flux layer, as in FluxLite); after that the collector grows with the circuit in its core, no new machine. An old
 * tiered collector crafts into the new one. Lower case letters are GT tools (d screwdriver, h hammer, w wrench).
 */
public final class ShardCrafting {

    private ShardCrafting() {}

    public static void register() {
        try {
            ItemStack pearl = new ItemStack(Items.ender_pearl);
            RecipeGuard.shaped(
                "Imprinter",
                new ItemStack(FluxDepths.imprinter),
                new Object[] { "PGP", "SCS", "dRh", 'P', plate(Materials.Bronze), 'G', "paneGlass", 'S',
                    OrePrefixes.screw.get(Materials.Bronze), 'C', new ItemStack(Items.compass), 'R',
                    OrePrefixes.stick.get(Materials.Bronze) });

            RecipeGuard.shaped(
                "Flux Shard Collector",
                Collectors.main(),
                new Object[] { "PEP", "GHG", "TDT", 'P', plate(Materials.Bronze), 'E', pearl, 'G',
                    OrePrefixes.gearGt.get(Materials.Bronze), 'H', ItemList.Hull_Bronze.get(1), 'T',
                    OrePrefixes.pipeMedium.get(Materials.Bronze), 'D', head(DrillHead.BRONZE) });

            int n = 0;
            for (ItemStack old : Collectors.legacy())
                RecipeGuard.shapeless("Flux Shard Collector (from an old one " + n++ + ")", Collectors.main(), old);
        } catch (Throwable t) {
            FluxDepths.LOG.error("Failed to register the shard collector recipes", t);
        }
    }

    private static Object plate(Materials m) {
        return OrePrefixes.plate.get(m);
    }

    private static Object head(DrillHead h) {
        ItemStack s = DrillHeads.item(h);
        return s != null ? s : OrePrefixes.toolHeadDrill.get(Materials.get(h.material));
    }
}
