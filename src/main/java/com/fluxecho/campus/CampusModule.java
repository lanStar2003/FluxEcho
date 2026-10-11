package com.fluxecho.campus;

import net.minecraft.block.Block;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.logic.PartRecipes;
import com.fluxecho.logic.Parts;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * The nexus campus: the deck every building of a campus stands on and is walled with, the fittings its modules are
 * furnished with, the supply port automation feeds a construction job through, and (0.10.1) the recycle bin the
 * campus's recycle store is taken out of. The blocks are registered whether the nexus is enabled or not, so placed
 * campuses keep their blocks; the recipes come from {@link PartRecipes}, the same table the builder's material cost is
 * taken from.
 */
public final class CampusModule {

    public static BlockDeck deck;
    public static BlockFitting fitting;
    public static BlockSupplyPort supplyPort;
    /**
     * The recycle bin block ({@code fluxecho:recycle_bin}, part {@link Parts#RECYCLE_BIN}), set where it is registered.
     * While it is null the builder skips the bin's step and leaves it out of the bill ({@link PartBlocks#block}), and
     * its recipe is not registered.
     */
    public static Block recycleBin;

    private CampusModule() {}

    public static void preInit() {
        deck = new BlockDeck();
        GameRegistry.registerBlock(deck, ItemBlockDeck.class, "deck");
        fitting = new BlockFitting();
        GameRegistry.registerBlock(fitting, ItemBlockFitting.class, "fitting");
        supplyPort = new BlockSupplyPort();
        GameRegistry.registerBlock(supplyPort, BlockSupplyPort.ItemPort.class, "supply_port");
        GameRegistry.registerTileEntity(TileSupplyPort.class, "fluxecho:supply_port");
        BlockRecycleBin bin = new BlockRecycleBin();
        GameRegistry.registerBlock(bin, BlockRecycleBin.ItemBin.class, "recycle_bin");
        GameRegistry.registerTileEntity(TileRecycleBin.class, "fluxecho:recycle_bin");
        recycleBin = bin;
    }

    /**
     * The campus's handlers: the capture of what a block spills while the builder takes it down, and (0.12.0) the light
     * lift's riders.
     */
    public static void init() {
        DropCapture.register();
        Lift.register();
    }

    /**
     * The shaped recipes of every deck and fitting part, of the supply port and of the recycle bin (once its block is
     * registered). Each one on its own, so one an addon breaks does not take the rest with it.
     */
    public static void postInit() {
        if (!Config.defaultRecipes || !Config.nexusEnabled) return;
        for (PartRecipes.Recipe r : PartRecipes.all()) {
            boolean fixture = r.part == Parts.SUPPLY_PORT || r.part == Parts.RECYCLE_BIN;
            if (!Parts.isDeck(r.part) && !Parts.isFitting(r.part) && !fixture) continue;
            if (PartBlocks.block(r.part) == null) continue;
            try {
                PartBlocks.register(r);
            } catch (Throwable t) {
                FluxEcho.LOG.error("Failed to register the recipe of {}", r.name, t);
            }
        }
    }
}
