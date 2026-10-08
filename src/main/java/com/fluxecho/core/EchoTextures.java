package com.fluxecho.core;

import java.util.EnumMap;
import java.util.Map;

import com.fluxecho.FluxEcho;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.render.TextureFactory;

/**
 * Block faces of the echo machines. Every one stands in the flux casing ({@code tools/FluxTextures.java}), not GT's:
 * its own front (animated while it works, {@code tools/Textures.java}), the echo ring on top, and on the sides strips
 * glowing in the machine's colour.
 */
public final class EchoTextures {

    public static final IIconContainer CASING_SIDE = flux("casing_side"), CASING_TOP = flux("casing_top"),
        CASING_BOTTOM = flux("casing_bottom"), STRIP = flux("strip");

    private static final Map<MachineId, ITexture[]> FRONTS = new EnumMap<>(MachineId.class);
    private static final Map<MachineId, ITexture> STRIPS = new EnumMap<>(MachineId.class);
    private static final ITexture TOP = face("top", true), TOP_IDLE = face("top", false);

    private EchoTextures() {}

    /**
     * Creates the icons while the machines register: GT registers custom icons when they are created, and only those
     * that exist before the block textures are stitched get an image.
     */
    public static synchronized void load(MachineId kind) {
        FRONTS
            .computeIfAbsent(kind, k -> new ITexture[] { face(k.key + "/front", false), face(k.key + "/front", true) });
    }

    private static IIconContainer flux(String name) {
        return new Textures.BlockIcons.CustomIcon(FluxEcho.MODID + ":machines/flux/" + name);
    }

    private static IIconContainer icon(String path) {
        return new Textures.BlockIcons.CustomIcon(FluxEcho.MODID + ":machines/" + path);
    }

    private static ITexture face(String path, boolean active) {
        if (!active) return TextureFactory.of(icon(path));
        return TextureFactory.of(
            TextureFactory.of(icon(path + "_active")),
            TextureFactory.builder()
                .addIcon(icon(path + "_active_glow"))
                .glow()
                .build());
    }

    public static ITexture casing(IIconContainer face) {
        return TextureFactory.of(face);
    }

    /** The machine's own front, idle or working. */
    public static synchronized ITexture front(MachineId kind, boolean active) {
        load(kind);
        return FRONTS.get(kind)[active ? 1 : 0];
    }

    /** The echo ring every machine has on top; the flux layer ripples out of it while it works. */
    public static ITexture top(boolean active) {
        return active ? TOP : TOP_IDLE;
    }

    /** The side strips in the machine's colour. */
    public static synchronized ITexture strip(MachineId kind) {
        return STRIPS.computeIfAbsent(kind, k -> {
            int c = k.accent;
            return TextureFactory.builder()
                .addIcon(STRIP)
                .setRGBA(new short[] { (short) (c >> 16 & 0xFF), (short) (c >> 8 & 0xFF), (short) (c & 0xFF), 0 })
                .glow()
                .build();
        });
    }
}
