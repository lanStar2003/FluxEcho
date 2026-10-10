package com.fluxecho.campus;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.logic.PartRecipes;
import com.fluxecho.logic.Parts;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * The nexus campus: the deck every building of a campus stands on and is walled with, and the fittings its modules
 * are furnished with. The blocks are registered whether the nexus is enabled or not, so placed campuses keep their
 * blocks; the recipes come from {@link PartRecipes}, the same table the builder's material cost is taken from.
 */
public final class CampusModule {

    public static BlockDeck deck;
    public static BlockFitting fitting;

    private CampusModule() {}

    public static void preInit() {
        deck = new BlockDeck();
        GameRegistry.registerBlock(deck, ItemBlockDeck.class, "deck");
        fitting = new BlockFitting();
        GameRegistry.registerBlock(fitting, ItemBlockFitting.class, "fitting");
    }

    /** Nothing to set up yet; the campus builder hooks in here. */
    public static void init() {}

    /**
     * The shaped recipes of every deck and fitting part. Each one on its own, so one an addon breaks does not take the
     * rest with it. The supply port's recipe waits for its block.
     */
    public static void postInit() {
        if (!Config.defaultRecipes || !Config.nexusEnabled) return;
        for (PartRecipes.Recipe r : PartRecipes.all()) {
            if (!Parts.isDeck(r.part) && !Parts.isFitting(r.part)) continue;
            try {
                PartBlocks.register(r);
            } catch (Throwable t) {
                FluxEcho.LOG.error("Failed to register the recipe of {}", r.name, t);
            }
        }
    }
}
