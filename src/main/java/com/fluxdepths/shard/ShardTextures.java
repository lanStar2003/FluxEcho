package com.fluxdepths.shard;

import java.util.EnumMap;
import java.util.Map;

import com.fluxdepths.FluxDepths;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.render.TextureFactory;

/**
 * Block textures. The Flux Shard Collector has its own casing, not GT's: dark plates with flux seams, a core lens in
 * front, a resonance grille on top, and glow strips on the sides in the colour of the circuit in its core. The fluid
 * pumps are still GT machines with overlays on GT's casings.
 */
public final class ShardTextures {

    public static final IIconContainer FRONT = icon("front"), FRONT_ACTIVE = icon("front_active"),
        FRONT_GLOW = icon("front_active_glow"), TOP = icon("top"), TOP_ACTIVE = icon("top_active"),
        TOP_GLOW = icon("top_active_glow");

    public static final IIconContainer PUMP = icon("pump_front"), PUMP_ACTIVE = icon("pump_front_active"),
        PUMP_GLOW = icon("pump_front_active_glow");

    public static final IIconContainer CASING_SIDE = icon("casing_side"), CASING_TOP = icon("casing_top"),
        CASING_BOTTOM = icon("casing_bottom"), STRIP = icon("strip"), CORE = icon("core"),
        CORE_ACTIVE = icon("core_active"), CORE_GLOW = icon("core_active_glow");

    private static final Map<ShardTier, ITexture> STRIPS = new EnumMap<>(ShardTier.class);

    private ShardTextures() {}

    /**
     * Loads this class while the machines register: GT registers custom icons when they are created, and only those
     * that exist before the block textures are stitched get an image.
     */
    public static void load() {}

    private static IIconContainer icon(String name) {
        return new Textures.BlockIcons.CustomIcon(FluxDepths.MODID + ":collector/" + name);
    }

    public static ITexture front(boolean active) {
        return active ? TextureFactory.of(TextureFactory.of(FRONT_ACTIVE), glow(FRONT_GLOW)) : TextureFactory.of(FRONT);
    }

    public static ITexture top(boolean active) {
        return active ? TextureFactory.of(TextureFactory.of(TOP_ACTIVE), glow(TOP_GLOW)) : TextureFactory.of(TOP);
    }

    /** The collector's core lens: it lights up and turns while ore condenses behind it. */
    public static ITexture core(boolean active) {
        return active ? TextureFactory.of(TextureFactory.of(CORE_ACTIVE), glow(CORE_GLOW)) : TextureFactory.of(CORE);
    }

    public static ITexture casing(IIconContainer face) {
        return TextureFactory.of(face);
    }

    /** The side strips, glowing in the tier's colour. */
    public static synchronized ITexture strip(ShardTier tier) {
        return STRIPS.computeIfAbsent(tier, t -> {
            int c = t.color();
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

    /** The fluid pumps: their own front, the old collectors' grille on top. */
    public static ITexture[] pumpOverlays() {
        ITexture[] t = new ITexture[14];
        t[2] = TextureFactory.of(TextureFactory.of(PUMP_ACTIVE), glow(PUMP_GLOW));
        t[3] = TextureFactory.of(PUMP);
        t[4] = top(true);
        t[5] = top(false);
        return t;
    }
}
