package com.fluxecho.frame;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.core.EchoRecipes;
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
     * stand on. Stainless steel: the nexus is an HV building.
     */
    public static void postInit() {
        if (!Config.defaultRecipes || !Config.nexusEnabled) return;
        Object ss = OrePrefixes.plate.get(Materials.StainlessSteel),
            rod = OrePrefixes.stick.get(Materials.StainlessSteel), steel = OrePrefixes.plate.get(Materials.Steel),
            grit = MatterModule.GRIT, crystal = MatterModule.CRYSTAL, echo = MatterModule.ECHO_CRYSTAL;
        try {
            EchoRecipes.shaped(
                "Flux Frame Base",
                part(BlockFrame.BASE, 8),
                "BSB",
                "SGS",
                "BSB",
                'B',
                new ItemStack(Blocks.stonebrick),
                'S',
                ss,
                'G',
                grit);
            EchoRecipes.shaped(
                "Lit Frame Base",
                part(BlockFrame.BASE_LIT, 4),
                "GBG",
                "BCB",
                "GBG",
                'G',
                new ItemStack(Items.glowstone_dust),
                'B',
                part(BlockFrame.BASE, 1),
                'C',
                crystal);
            EchoRecipes.shaped("Frame Pillar", part(BlockFrame.PILLAR, 4), "R R", "RGR", "R R", 'R', rod, 'G', grit);
            EchoRecipes.shaped(
                "Flux Conduit",
                part(BlockFrame.CONDUIT, 1),
                " C ",
                "GPG",
                " C ",
                'C',
                crystal,
                'G',
                new ItemStack(Blocks.glass_pane),
                'P',
                part(BlockFrame.PILLAR, 1));
            EchoRecipes.shaped("Ring Segment", part(BlockFrame.RING, 4), "SCS", "SSS", 'S', ss, 'C', crystal);
            EchoRecipes.shaped(
                "Crystal Seat",
                part(BlockFrame.SEAT, 1),
                "CEC",
                "CHC",
                "SSS",
                'C',
                crystal,
                'E',
                new ItemStack(Items.ender_eye),
                'H',
                ItemList.Hull_HV.get(1),
                'S',
                ss);
            EchoRecipes.shaped(
                "Module Foundation",
                part(BlockFrame.FOUNDATION, 8),
                "BSB",
                "SES",
                "BSB",
                'B',
                part(BlockFrame.BASE, 1),
                'S',
                ss,
                'E',
                echo);
            EchoRecipes.shaped(
                "Echo Shelf",
                part(BlockFrame.SHELF, 8),
                "SBS",
                "BEB",
                "SBS",
                'S',
                steel,
                'B',
                new ItemStack(Blocks.bookshelf),
                'E',
                echo);
        } catch (Throwable t) {
            FluxEcho.LOG.error("Failed to register the flux frame recipes", t);
        }
    }
}
