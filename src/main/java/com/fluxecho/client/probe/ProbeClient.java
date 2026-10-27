package com.fluxecho.client.probe;

import com.fluxecho.client.occlusion.DepthProbeRender;
import com.fluxecho.client.shape.ShapeSkin;
import com.fluxecho.client.sky.SkyLayer;
import com.fluxecho.client.sky.SunProbeRender;
import com.fluxecho.logic.probe.ProbeMarker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The client side of the operators' check-up rigs (0.17.0, refactor FINAL F06): the swatch wall's light, the depth
 * probe's pieces, the sky probe's two auroras and the sun probe's lines, all found from the rigs' marker signs
 * ({@link ProbeMarkers}). The bed probe has no light of its own. Only the client proxy reaches this class.
 */
@SideOnly(Side.CLIENT)
public final class ProbeClient {

    private ProbeClient() {}

    /**
     * Called by the client proxy in init, after the occlusion gate and the sky layer: the marker scan, the swatch's
     * light in the new families' chunk renderer (in front of the default look; whoever plugs the box table into
     * {@code ShapeRender} later hands it to {@link SwatchLooks#install} as the next look), and the probes' drawing.
     */
    public static void register() {
        ProbeMarkers.register();
        SwatchLooks.install(ShapeSkin.NONE);
        DepthProbeRender.register();
        SunProbeRender.register();
        // probe sky: the sky layer to the north of every player near the sign, the world-fixed curtain hung south of it
        SkyLayer.add((w, out) -> {
            java.util.List<ProbeMarkers.Marker> rigs = ProbeMarkers.of(ProbeMarker.Kind.SKY);
            if (!rigs.isEmpty()) out.add(SkyLayer.Sheet.sky(0, -1));
            for (ProbeMarkers.Marker m : rigs)
                out.add(SkyLayer.Sheet.world(m.sign[0] + 0.5, m.sign[1], m.sign[2] + 0.5, 0, 1));
        });
    }
}
