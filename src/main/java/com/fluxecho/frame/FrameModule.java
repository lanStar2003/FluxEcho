package com.fluxecho.frame;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.campus.PartBlocks;
import com.fluxecho.core.EchoRecipes;
import com.fluxecho.logic.PartRecipes;
import com.fluxecho.logic.Parts;
import com.fluxecho.matter.MatterModule;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;

/** The flux frame: the blocks the Flux Nexus and its modules are built of. */
public final class FrameModule {

    public static BlockFrame frame;

    private FrameModule() {}

    public static void preInit() {
        frame = new BlockFrame();
        GameRegistry.registerBlock(frame, ItemBlockFrame.class, "frame");
    }

    public static ItemStack part(int meta, int count) {
        return new ItemStack(frame, count, meta);
    }

    /**
     * Steel and stone for the bulk of it, flux grit and crystal where the flux runs, echo crystal for what the modules
     * stand on. Stainless steel: the nexus is an HV building. Every part but the Crystal Seat comes from
     * {@link PartRecipes}, the table the campus builder's costs come from too; the seat needs a GregTech HV hull, which
     * the table cannot name, so it is registered here by hand.
     */
    public static void postInit() {
        if (!Config.defaultRecipes || !Config.nexusEnabled) return;
        for (PartRecipes.Recipe r : PartRecipes.all()) {
            if (!Parts.isFrame(r.part)) continue;
            try {
                PartBlocks.register(r);
            } catch (Throwable t) {
                FluxEcho.LOG.error("Failed to register the recipe of {}", r.name, t);
            }
        }
        try {
            EchoRecipes.shaped(
                "Crystal Seat",
                part(BlockFrame.SEAT, 1),
                "CEC",
                "CHC",
                "SSS",
                'C',
                MatterModule.CRYSTAL,
                'E',
                new ItemStack(Items.ender_eye),
                'H',
                ItemList.Hull_HV.get(1),
                'S',
                OrePrefixes.plate.get(Materials.StainlessSteel));
        } catch (Throwable t) {
            FluxEcho.LOG.error("Failed to register the recipe of Crystal Seat", t);
        }
    }
}
