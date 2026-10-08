package com.fluxecho;

import cpw.mods.fml.common.Loader;

/**
 * Which optional mods are present, read once in preInit. Code outside the module packages checks these flags and
 * only then calls into a module, so a missing mod's classes are never loaded.
 */
public final class Mods {

    public static boolean forestry, thaumcraft, bloodMagic, fluxDepths, betterQuesting, mobsInfo, ic2, botania, ae2,
        waila;

    private Mods() {}

    static void detect() {
        forestry = Loader.isModLoaded("Forestry");
        thaumcraft = Loader.isModLoaded("Thaumcraft");
        bloodMagic = Loader.isModLoaded("AWWayofTime");
        fluxDepths = Loader.isModLoaded("fluxdepths");
        betterQuesting = Loader.isModLoaded("betterquesting");
        mobsInfo = Loader.isModLoaded("mobsinfo");
        ic2 = Loader.isModLoaded("IC2");
        botania = Loader.isModLoaded("Botania");
        ae2 = Loader.isModLoaded("appliedenergistics2");
        waila = Loader.isModLoaded("Waila");
    }

    /** Whether every mod id in the list is loaded. */
    public static boolean allLoaded(Iterable<String> modIds) {
        for (String id : modIds) if (!Loader.isModLoaded(id)) return false;
        return true;
    }
}
