package com.fluxecho.client.occlusion;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.fluxecho.FluxEcho;
import com.fluxecho.client.FarDraw;
import com.fluxecho.client.ShaderCompat;
import com.fluxecho.client.probe.ProbeMarkers;
import com.fluxecho.logic.probe.ProbeFrame;
import com.fluxecho.logic.probe.ProbeMarker;
import com.fluxecho.logic.probe.ProbeRigs;
import com.fluxecho.logic.probe.ProbeRigs.Depth;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The light of {@code /fluxecho probe depth} (0.17.0 prototype P0, style FINAL §8.5, refactor FINAL §7.1): before and
 * behind the probe's hull wall, five square pieces of light drawn after the world, each the way one answer of P0 needs
 * ({@link Depth.Piece}):
 * <ul>
 * <li>cyan, with the depth test: before the wall (always seen; if it is not, the depth buffer holds rubbish) and
 * behind the wall (seen only if the shader pack's depth buffer is not the world's: P0 fails);</li>
 * <li>amber, without the depth test, before the wall: always seen;</li>
 * <li>gold, without the depth test, through the occlusion gate: behind the wall (the gate hides it) and behind the
 * window (the gate lets it through the glass).</li>
 * </ul>
 * The user takes a screenshot by day and one by night with the shader pack on; the sign on each piece's column says
 * which is which. Drawn like every light of the city: after the world, skipped in the shadow pass, quads only with a
 * normal towards the viewer, relative to the camera, registered with {@link FarDraw}; it only reads the markers and the
 * gate's last answers, so a frame may draw it many times.
 */
@SideOnly(Side.CLIENT)
public final class DepthProbeRender {

    /** How far the pieces are drawn. */
    static final double RANGE = 96;
    static final int DEPTH = 0x4FE3FF, PLAIN = 0xFF9A3C, GATED = 0xFFD27A;
    private static boolean off;

    private DepthProbeRender() {}

    /** Draws after the world and behind light gates; lists the gated pieces for the gate's rounds. */
    public static void register() {
        DepthProbeRender h = new DepthProbeRender();
        MinecraftForge.EVENT_BUS.register(h);
        FarDraw.add(h::onRenderLast);
        OcclusionGate.add((w, out) -> {
            for (ProbeMarkers.Marker m : ProbeMarkers.of(ProbeMarker.Kind.DEPTH))
                for (Depth.Piece p : Depth.Piece.values()) if (p.gated) out.add(Depth.gatePiece(m.frame, m.sign, p));
        });
    }

    @SubscribeEvent
    public void onRenderLast(RenderWorldLastEvent e) {
        if (off || ShaderCompat.shadowPass() || Minecraft.getMinecraft().theWorld == null) return;
        List<ProbeMarkers.Marker> rigs = ProbeMarkers.of(ProbeMarker.Kind.DEPTH);
        if (rigs.isEmpty()) return;
        try {
            Shapes.begin(true);
            for (ProbeMarkers.Marker m : rigs) for (Depth.Piece p : Depth.Piece.values()) piece(m, p);
        } catch (Throwable t) {
            off = true;
            FluxEcho.LOG.error("Drawing the depth probe failed; it is left out until the game restarts", t);
        } finally {
            Shapes.end();
        }
    }

    /** One piece: a soft square and a bright core, facing the operator who set the probe out. */
    private static void piece(ProbeMarkers.Marker m, Depth.Piece p) {
        ProbeFrame f = m.frame;
        double[] c = Depth.centre(f, p);
        double x = c[0] - RenderManager.renderPosX, y = c[1] - RenderManager.renderPosY,
            z = c[2] - RenderManager.renderPosZ;
        if (x * x + y * y + z * z > RANGE * RANGE) return;
        if (p.gated && !OcclusionGate.visible(ProbeRigs.pieceId(m.sign, p.ordinal()))) return;
        if (p.depthTest) GL11.glEnable(GL11.GL_DEPTH_TEST);
        else GL11.glDisable(GL11.GL_DEPTH_TEST);
        int rgb = p.gated ? GATED : p.depthTest ? DEPTH : PLAIN;
        square(f, x, y, z, Depth.HALF, rgb, 0.35f);
        square(f, x, y, z, Depth.HALF * 0.55, rgb, 0.8f);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    /** A square of half size {@code h} round (x, y, z), upright and across the frame's facing. */
    private static void square(ProbeFrame f, double x, double y, double z, double h, int rgb, float a) {
        double rx = f.rx() * h, rz = f.rz() * h;
        Shapes.quad3(
            new double[] { x - rx, y - h, z - rz },
            new double[] { x + rx, y - h, z + rz },
            new double[] { x + rx, y + h, z + rz },
            new double[] { x - rx, y + h, z - rz },
            rgb,
            a,
            a,
            a,
            a);
    }
}
