package com.fluxecho.config;

import net.minecraftforge.common.config.Configuration;

/**
 * The gates section of config/fluxecho.cfg: the light gates and their rooms. Since 0.17.0 each section of the config
 * file is its own class, listed in Config.SECTIONS, so that work on different subsystems (and later on each building)
 * never edits the same file; the keys, defaults, bounds and comments are the ones 0.16.0 shipped, word for word.
 */
public final class GatesConfig {

    public static final String CATEGORY = "gates";

    public static boolean gateRecipe = true, gateLiveView = true, gateRealView = true, gateHoldBase = true;
    public static int gateDimension = 7270, gateProvider = 7270, gateViewRange = 32;

    private GatesConfig() {}

    public static void load(Configuration c) {
        c.setCategoryComment(
            CATEGORY,
            "Light gates (prototype): each gate opens onto a room of its own, closed all round, in the folded zone of the gate's own dimension (past x and z 1048576, where nothing generates). Close to a gate you see into it; walking through is a step within the same world, nothing reloads.");
        gateRecipe = c
            .getBoolean("enableRecipe", CATEGORY, gateRecipe, "Register the crafting recipe of the light gate.");
        gateViewRange = c.getInt(
            "viewRange",
            CATEGORY,
            gateViewRange,
            0,
            64,
            "Blocks within which players see through a gate. The far side is kept loaded for them a little further out, so it is there before they look. 0 turns the view off for everyone.");
        gateLiveView = c.getBoolean(
            "liveView",
            CATEGORY,
            gateLiveView,
            "Client: draw what lies behind a gate on its membrane. Off: the membrane only glows (for weak graphics cards or a shader pack it does not get along with).");
        gateRealView = c.getBoolean(
            "realView",
            CATEGORY,
            gateRealView,
            "Client, with Angelica: walking through a gate is seamless, the far side built before you step in. Without a shader pack the game also draws the world a second time from the far side of each gate in sight, so the membrane shows everything there (machines, mobs, players, holograms, weather); that costs about one more frame's drawing while a gate is in sight. With a shader pack the gate keeps the simpler view (blocks, machines, mobs and FluxEcho's holograms, without the pack's lighting). Off: the 0.8.2 behaviour, with a ripple when walking through.");
        gateHoldBase = c.getBoolean(
            "holdBase",
            CATEGORY,
            gateHoldBase,
            "While a player is inside a room, keep everything they had loaded round the gate outside loaded for them (and running), so walking back out reloads nothing. Off: only what the room's gate shows stays.");
        gateDimension = c.getInt(
            "dimensionId",
            CATEGORY,
            gateDimension,
            -100000,
            100000,
            "Dimension id of the 0.8.1 prototype's flux interior. Rooms are no longer made there; it stays registered so worlds from 0.8.1 load, and its gates are moved into the folded zone.");
        gateProvider = c.getInt(
            "providerId",
            CATEGORY,
            gateProvider,
            -100000,
            100000,
            "World provider id of the 0.8.1 prototype's flux interior; only when another mod takes it.");
    }
}
