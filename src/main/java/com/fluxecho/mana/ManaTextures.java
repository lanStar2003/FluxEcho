package com.fluxecho.mana;

import java.util.HashMap;
import java.util.Map;

import com.fluxecho.FluxEcho;
import com.fluxecho.core.CoreCircuits;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.render.TextureFactory;

/**
 * The Mana Echo Spring's block faces (drawn by {@code tools/SpringTextures.java}): its own casing rather than GT's,
 * as FluxDepths' collector has, a round well in front where the mana wells up, a fountain grate on top, and strips on
 * the sides glowing in the colour of the circuit in its core.
 */
public final class ManaTextures {

    public static final IIconContainer CASING_SIDE = icon("casing_side"), CASING_TOP = icon("casing_top"),
        CASING_BOTTOM = icon("casing_bottom"), STRIP = icon("strip"), SPRING = icon("spring"),
        SPRING_ACTIVE = icon("spring_active"), SPRING_GLOW = icon("spring_active_glow"), GRATE = icon("grate"),
        GRATE_ACTIVE = icon("grate_active"), GRATE_GLOW = icon("grate_active_glow");

    private static final Map<Integer, ITexture> STRIPS = new HashMap<>();

    private ManaTextures() {}

    /**
     * Loads this class while the machine registers: GT registers custom icons when they are created, and only those
     * that exist before the block textures are stitched get an image.
     */
    public static void load() {}

    private static IIconContainer icon(String name) {
        return new Textures.BlockIcons.CustomIcon(FluxEcho.MODID + ":machines/mana_echo/" + name);
    }

    public static ITexture casing(IIconContainer face) {
        return TextureFactory.of(face);
    }

    /** The well in front: dark without work, mana welling up through it while it works. */
    public static ITexture spring(boolean active) {
        return active ? TextureFactory.of(TextureFactory.of(SPRING_ACTIVE), glow(SPRING_GLOW))
            : TextureFactory.of(SPRING);
    }

    /** The fountain grate on top, lit while it works. */
    public static ITexture grate(boolean active) {
        return active ? TextureFactory.of(TextureFactory.of(GRATE_ACTIVE), glow(GRATE_GLOW)) : TextureFactory.of(GRATE);
    }

    /** The side strips in the core circuit's colour; grey without one. */
    public static synchronized ITexture strip(int tier) {
        return STRIPS.computeIfAbsent(tier, t -> {
            int c = CoreCircuits.color(t);
            return TextureFactory.builder()
                .addIcon(STRIP)
                .setRGBA(new short[] { (short) (c >> 16 & 0xFF), (short) (c >> 8 & 0xFF), (short) (c & 0xFF), 0 })
                .glow()
                .build();
        });
    }

    private static ITexture glow(IIconContainer icon) {
        return TextureFactory.builder()
            .addIcon(icon)
            .glow()
            .build();
    }
}
