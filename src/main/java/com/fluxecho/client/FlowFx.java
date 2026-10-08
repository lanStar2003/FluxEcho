package com.fluxecho.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import com.fluxecho.Config;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * The trails ({@link FlowStore}): an arc from the machine's top to the block it fills, a faint shimmering path with
 * bright motes running along it towards the target, so you can see what the spring or the Blood Echo is filling.
 */
public final class FlowFx {

    private FlowFx() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new FlowFx());
    }

    @SubscribeEvent
    public void onRenderLast(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || !Config.flowTrails || Config.effectRange <= 0 || ShaderCompat.shadowPass()) return;
        java.util.List<FlowStore.Flow> flows = FlowStore.current(mc.theWorld.provider.dimensionId);
        if (flows.isEmpty()) return;
        long now = System.currentTimeMillis();
        float t = mc.theWorld.getTotalWorldTime() + e.partialTicks;
        double range = Config.effectRange + 8;
        boolean open = false;
        for (FlowStore.Flow f : flows) {
            double x0 = f.x + 0.5 - RenderManager.renderPosX, y0 = f.y + 1.05 - RenderManager.renderPosY,
                z0 = f.z + 0.5 - RenderManager.renderPosZ;
            double x1 = f.tx + 0.5 - RenderManager.renderPosX, y1 = f.ty + 0.85 - RenderManager.renderPosY,
                z1 = f.tz + 0.5 - RenderManager.renderPosZ;
            if (Math.sqrt(x0 * x0 + y0 * y0 + z0 * z0) > range && Math.sqrt(x1 * x1 + y1 * y1 + z1 * z1) > range)
                continue;
            float a = f.strength(now);
            if (a <= 0.02f) continue;
            if (!open) {
                Motes.begin();
                open = true;
            }
            draw(x0, y0, z0, x1, y1, z1, f.color, t, a);
        }
        if (open) Motes.end();
    }

    private static void draw(double x0, double y0, double z0, double x1, double y1, double z1, int c, float t,
        float a) {
        double len = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
        double mx = (x0 + x1) / 2, my = Math.max(y0, y1) + 0.6 + len * 0.22, mz = (z0 + z1) / 2;
        int path = (int) Math.max(12, len * 6);
        for (int i = 0; i <= path; i++) {
            double f = (double) i / path;
            float shimmer = 0.55f + 0.45f * (float) Math.sin(t * 0.25 - i * 0.7);
            point(x0, y0, z0, mx, my, mz, x1, y1, z1, f, 0.03, c, a * 0.22f * shimmer);
        }
        int motes = (int) Math.max(6, len * 2.5);
        for (int i = 0; i < motes; i++) {
            double f = (t * 0.03 + (double) i / motes) % 1;
            float ends = (float) Math.min(1, Math.min(f, 1 - f) * 8);
            point(x0, y0, z0, mx, my, mz, x1, y1, z1, f, 0.075, c, a * ends);
            point(x0, y0, z0, mx, my, mz, x1, y1, z1, Math.max(0, f - 0.015), 0.05, 0xFFFFFF, a * ends * 0.5f);
        }
    }

    /** A spot on the quadratic curve from p0 over m to p1. */
    private static void point(double x0, double y0, double z0, double mx, double my, double mz, double x1, double y1,
        double z1, double f, double s, int c, float a) {
        double u = 1 - f;
        Motes.add(
            u * u * x0 + 2 * u * f * mx + f * f * x1,
            u * u * y0 + 2 * u * f * my + f * f * y1,
            u * u * z0 + 2 * u * f * mz + f * f * z1,
            s,
            c,
            a);
    }
}
