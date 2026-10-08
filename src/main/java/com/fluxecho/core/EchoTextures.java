package com.fluxecho.core;

import com.fluxecho.FluxEcho;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.render.TextureFactory;

/**
 * Overlays drawn on top of GT's machine casings (drawn by {@code tools/Textures.java}): each machine has its own
 * front, and all of them share the echo ring on top, the mark of the flux layer.
 */
public final class EchoTextures {

    private EchoTextures() {}

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

    /** GT basic machine overlay slots: 2/3 front, 4/5 top (active/inactive); the rest stay GT's defaults. */
    public static ITexture[] overlays(MachineId kind) {
        ITexture[] t = new ITexture[14];
        t[2] = face(kind.key + "/front", true);
        t[3] = face(kind.key + "/front", false);
        t[4] = face("top", true);
        t[5] = face("top", false);
        return t;
    }
}
