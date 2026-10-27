package com.fluxecho.config;

import net.minecraftforge.common.config.Configuration;

/**
 * The render section of config/fluxecho.cfg (0.17.0): two client choices the rebuild ships with their safe side on,
 * because only the player can see which way works with a shader pack. {@code occlusion} says how the light pieces drawn
 * after the world (rings, beams, sails, holograms) are hidden behind nearer blocks: {@code gate}, the client's own
 * per-piece test against the world's blocks, works with every pack; {@code depth} leaves it to the depth buffer once
 * {@code /fluxecho probe depth} shows the pack keeps it (the P0 prototype). {@code skyLayer} says where the lights of
 * the sky (the aurora) are drawn: {@code world}, as far geometry fixed in the world, or {@code sky}, in the sky itself,
 * once {@code /fluxecho probe sky} shows it works (the N0 prototype). A later patch only flips the defaults.
 */
public final class RenderConfig {

    public static final String CATEGORY = "render";
    public static final String OCCLUSION_GATE = "gate", OCCLUSION_DEPTH = "depth", SKY_WORLD = "world", SKY_SKY = "sky";

    /** Client: {@link #OCCLUSION_GATE} or {@link #OCCLUSION_DEPTH}. */
    public static String occlusion = OCCLUSION_GATE;
    /** Client: {@link #SKY_WORLD} or {@link #SKY_SKY}. */
    public static String skyLayer = SKY_WORLD;

    private RenderConfig() {}

    public static void load(Configuration c) {
        c.setCategoryComment(
            CATEGORY,
            "How the city's light is drawn with a shader pack. The defaults work with every pack; the other values are for once the probe commands show they work with yours.");
        occlusion = pick(
            c.getString(
                "occlusion",
                CATEGORY,
                occlusion,
                "Client: how the light pieces drawn after the world (rings, beams, sails, holograms) are hidden behind nearer blocks: gate (FluxEcho tests each piece against the blocks between it and you, works with every shader pack) or depth (the depth buffer does it, once /fluxecho probe depth shows your pack keeps it).",
                new String[] { OCCLUSION_GATE, OCCLUSION_DEPTH }),
            OCCLUSION_GATE,
            OCCLUSION_DEPTH);
        skyLayer = pick(
            c.getString(
                "skyLayer",
                CATEGORY,
                skyLayer,
                "Client: where the lights of the sky (the aurora) are drawn: world (as far shapes fixed in the world, works with every shader pack) or sky (in the sky itself, once /fluxecho probe sky shows it works with your pack).",
                new String[] { SKY_WORLD, SKY_SKY }),
            SKY_WORLD,
            SKY_SKY);
    }

    /** The value when it is one of the allowed ones, else the first (the default). */
    static String pick(String value, String... allowed) {
        String v = value == null ? "" : value.trim();
        for (String a : allowed) if (a.equals(v)) return a;
        return allowed[0];
    }
}
