package com.fluxecho.client.probe;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.fluxecho.client.shape.ShapeRender;
import com.fluxecho.client.shape.ShapeSkin;
import com.fluxecho.logic.probe.SwatchLayout;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * How the new families' chunk renderer ({@link ShapeRender}) sees a swatch wall's cells (0.17.0, style FINAL §8.6):
 * the accent of each light seam and lamp, its lamps lit or dark at every hour, the memorial's warm-white keel, as
 * {@link SwatchLayout#glows} gives them for the walls {@link ProbeMarkers} found. Every other cell gets the next
 * answer in line: whatever box-table look was plugged in before this one ({@link #install}), else the default.
 * <p>
 * The renderer may ask from the chunk builder's threads: this only reads immutable snapshots. A preview world (NEI)
 * never gets a swatch's light.
 */
@SideOnly(Side.CLIENT)
public final class SwatchLooks implements ShapeSkin {

    private final ShapeSkin next;
    private final Map<SwatchLayout.Glow, Look> looks = new ConcurrentHashMap<>();

    private SwatchLooks(ShapeSkin next) {
        this.next = next == null ? ShapeSkin.NONE : next;
    }

    /** Plugs the swatch's light in front of {@code next} (the box table's look, or {@link ShapeSkin#NONE}). */
    public static void install(ShapeSkin next) {
        ShapeRender.skin(new SwatchLooks(next));
    }

    @Override
    public Look at(IBlockAccess w, int x, int y, int z) {
        if (!(w instanceof World world) || world == Minecraft.getMinecraft().theWorld) {
            SwatchLayout.Glow g = ProbeMarkers.swatch(x, y, z);
            if (g != null) return looks.computeIfAbsent(g, k -> new Look(k.accent, k.awake, k.lamps, k.keel));
        }
        return next.at(w, x, y, z);
    }
}
