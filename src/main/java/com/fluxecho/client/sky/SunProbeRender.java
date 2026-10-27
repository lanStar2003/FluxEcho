package com.fluxecho.client.sky;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import com.fluxecho.FluxEcho;
import com.fluxecho.client.FarDraw;
import com.fluxecho.client.ShaderCompat;
import com.fluxecho.client.probe.ProbeMarkers;
import com.fluxecho.logic.probe.ProbeMarker;
import com.fluxecho.logic.probe.ProbeRigs.Sun;
import com.fluxecho.logic.sky.SunPath;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The light of {@code /fluxecho probe sun} (0.17.0, refactor FINAL §7.1 "日轨", critique feasibility #2): three lines to
 * the sun as Complementary's default tilt of {@value SunPath#COMPLEMENTARY} degrees puts it, in gold, from the top of
 * the probe's mast (the mast-top full moon), from the floor of its light well (the eco dome's noon light well) and from
 * the middle of a sail that turns only about the north-south axis (the sky sails), the sail drawn with its normal in
 * white; beside each a faint grey line to where vanilla's untilted sun stands. At noon and at sunset the user sees
 * whether the gold lines meet the pack's sun; the sail shows how far it falls short of facing it.
 * <p>
 * Drawn like every light of the city: after the world, skipped in the shadow pass, quads only with a normal towards the
 * viewer, relative to the camera, registered with {@link FarDraw}; it only reads the markers and the world time.
 */
@SideOnly(Side.CLIENT)
public final class SunProbeRender {

    static final int GOLD = 0xFFD27A, GREY = 0x9AA4AE, SAIL = 0xEDF1F3;
    /** How far the probe is drawn. */
    static final double RANGE = 160;
    private static boolean off;

    private SunProbeRender() {}

    public static void register() {
        SunProbeRender h = new SunProbeRender();
        MinecraftForge.EVENT_BUS.register(h);
        FarDraw.add(h::onRenderLast);
    }

    @SubscribeEvent
    public void onRenderLast(RenderWorldLastEvent e) {
        World w = Minecraft.getMinecraft().theWorld;
        if (off || ShaderCompat.shadowPass() || w == null) return;
        List<ProbeMarkers.Marker> rigs = ProbeMarkers.of(ProbeMarker.Kind.SUN);
        if (rigs.isEmpty()) return;
        double angle = w.getCelestialAngle(e.partialTicks);
        double[] tilted = SunPath.sun(angle, SunPath.COMPLEMENTARY), plain = SunPath.sun(angle, SunPath.VANILLA);
        if (SunPath.elevation(tilted) < -5 && SunPath.elevation(plain) < -5) return;
        try {
            Shapes.begin(true);
            for (ProbeMarkers.Marker m : rigs) rig(m, tilted, plain);
        } catch (Throwable t) {
            off = true;
            FluxEcho.LOG.error("Drawing the sun probe failed; it is left out until the game restarts", t);
        } finally {
            Shapes.end();
        }
    }

    private static void rig(ProbeMarkers.Marker m, double[] tilted, double[] plain) {
        double[] top = rel(Sun.mastTop(m.frame)), well = rel(Sun.wellFloor(m.frame)), sail = rel(Sun.sail(m.frame));
        if (top[0] * top[0] + top[2] * top[2] > RANGE * RANGE) return;
        line(top, plain, Sun.MAST_LINE, GREY, 0.12, 0.35f);
        line(top, tilted, Sun.MAST_LINE, GOLD, 0.25, 0.8f);
        line(well, plain, Sun.WELL_LINE, GREY, 0.12, 0.35f);
        line(well, tilted, Sun.WELL_LINE, GOLD, 0.25, 0.8f);
        double[] n = SunPath.sailNormal(tilted);
        if (n == null) return;
        double[] axis = { 0, 0, Sun.SAIL_HALF_AXIS },
            across = { -n[1] * Sun.SAIL_HALF_ACROSS, n[0] * Sun.SAIL_HALF_ACROSS, 0 };
        Shapes.quad3(
            add(sail, -1, axis, -1, across),
            add(sail, 1, axis, -1, across),
            add(sail, 1, axis, 1, across),
            add(sail, -1, axis, 1, across),
            SAIL,
            0.3f,
            0.3f,
            0.3f,
            0.3f);
        line(sail, n, Sun.SAIL_NORMAL, SAIL, 0.15, 0.7f);
        line(sail, tilted, Sun.SAIL_LINE, GOLD, 0.25, 0.8f);
    }

    /** A world point relative to the camera. */
    private static double[] rel(double[] p) {
        return new double[] { p[0] - RenderManager.renderPosX, p[1] - RenderManager.renderPosY,
            p[2] - RenderManager.renderPosZ };
    }

    private static double[] add(double[] c, double a, double[] u, double b, double[] v) {
        return new double[] { c[0] + a * u[0] + b * v[0], c[1] + a * u[1] + b * v[1], c[2] + a * u[2] + b * v[2] };
    }

    /** A line of light from {@code from} along the unit direction {@code d}, {@code len} long, fading at its end. */
    private static void line(double[] from, double[] d, double len, int rgb, double width, float a) {
        Shapes.string(
            from[0],
            from[1],
            from[2],
            from[0] + d[0] * len,
            from[1] + d[1] * len,
            from[2] + d[2] * len,
            width,
            rgb,
            a,
            a * 0.3f);
    }
}
