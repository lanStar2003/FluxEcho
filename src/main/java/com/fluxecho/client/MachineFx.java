package com.fluxecho.client;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import com.fluxecho.Config;
import com.fluxecho.core.MachineId;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * What an echo machine shows above itself while it works: the echo rippling out of its top, and its own effect (a
 * scanning plane, bees, a helix, a vortex, glyphs, bubbles, orbiting stones, blood, sparks, leaves, a fountain), in
 * its colour. A machine reports itself every client tick while GT says it is active ({@link #seen}); one not heard
 * from for half a second is dropped.
 */
public final class MachineFx {

    private static final class Seen {

        final MachineId kind;
        final int dim, x, y, z;
        volatile long at;

        Seen(MachineId kind, int dim, int x, int y, int z) {
            this.kind = kind;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final Map<Long, Seen> SEEN = new ConcurrentHashMap<>();
    private static final long STALE_TICKS = 10;
    private static final int MAX_DRAWN = 48;

    private MachineFx() {}

    public static void register() {
        MachineFx h = new MachineFx();
        MinecraftForge.EVENT_BUS.register(h);
        FarDraw.add(h::onRenderLast);
    }

    /** From a working machine's client tick. */
    public static void seen(MachineId kind, World w, int x, int y, int z) {
        if (w == null) return;
        long key = ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
        Seen s = SEEN.get(key);
        int dim = w.provider.dimensionId;
        if (s == null || s.dim != dim || s.kind != kind) SEEN.put(key, s = new Seen(kind, dim, x, y, z));
        s.at = w.getTotalWorldTime();
    }

    @SubscribeEvent
    public void onRenderLast(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || SEEN.isEmpty()) return;
        long now = mc.theWorld.getTotalWorldTime();
        int dim = mc.theWorld.provider.dimensionId, range = Config.effectRange;
        boolean draw = Config.machineEffects && range > 0 && !ShaderCompat.shadowPass();
        float t = now + e.partialTicks;
        int drawn = 0;
        boolean open = false;
        for (Iterator<Seen> it = SEEN.values()
            .iterator(); it.hasNext();) {
            Seen s = it.next();
            if (now - s.at > STALE_TICKS || now < s.at) {
                it.remove();
                continue;
            }
            if (!draw || s.dim != dim || drawn >= MAX_DRAWN) continue;
            double x = s.x - RenderManager.renderPosX, y = s.y - RenderManager.renderPosY,
                z = s.z - RenderManager.renderPosZ;
            double dist = Math.sqrt((x + .5) * (x + .5) + (y + 1) * (y + 1) + (z + .5) * (z + .5));
            float fade = (float) Math.min(1, (range - dist) / 4);
            if (fade <= 0.05f) continue;
            if (!open) {
                Motes.begin();
                open = true;
            }
            effect(s.kind, x, y, z, t, fade, (s.x * 31 + s.z * 17) & 0xFF);
            drawn++;
        }
        if (open) Motes.end();
    }

    private static void effect(MachineId k, double x, double y, double z, float t, float a, int seed) {
        int c = k.accent;
        double cx = x + 0.5, top = y + 1.02, cz = z + 0.5;
        float tt = t + seed;

        // the echo rippling out of the top
        float ph = (tt % 32f) / 32f;
        double rr = 0.18 + 0.55 * ph;
        for (int i = 0; i < 16; i++) {
            double ang = i * Math.PI / 8;
            Motes.add(cx + Math.cos(ang) * rr, top + 0.03, cz + Math.sin(ang) * rr, 0.05, c, a * (1 - ph) * 0.55f);
        }

        switch (k.motif) {
            case SCAN -> {
                double hgt = top + 0.15 + 0.4 * (0.5 + 0.5 * Math.sin(tt * 0.08));
                for (int i = 0; i < 5; i++) for (int j = 0; j < 5; j++)
                    Motes.add(cx - 0.36 + i * 0.18, hgt, cz - 0.36 + j * 0.18, 0.035, c, a * 0.45f);
                double sweep = -0.4 + 0.8 * ((tt * 0.02) % 1);
                for (int i = 0; i < 9; i++) Motes.add(cx + sweep, hgt, cz - 0.4 + i * 0.1, 0.04, 0xFFFFFF, a * 0.5f);
            }
            case COMB -> {
                for (int i = 0; i < 3; i++) for (int tail = 0; tail < 4; tail++) {
                    double p = (tt - tail * 1.5) * 0.11 + i * 2.1;
                    double bx = cx + Math.sin(p) * 0.45, bz = cz + Math.sin(p * 2) * 0.3,
                        by = top + 0.35 + 0.08 * Math.sin(tt * 0.3 + i);
                    Motes.add(bx, by, bz, tail == 0 ? 0.06 : 0.035, tail == 0 ? 0xFFE27A : c, a * (1 - tail * 0.22f));
                }
            }
            case HELIX -> {
                for (int j = 0; j < 14; j++) {
                    double f = j / 14.0, ang = tt * 0.12 + f * Math.PI * 3, hy = top + 0.08 + f * 0.95;
                    double ox = Math.cos(ang) * 0.24, oz = Math.sin(ang) * 0.24;
                    Motes.add(cx + ox, hy, cz + oz, 0.045, c, a * 0.85f);
                    Motes.add(cx - ox, hy, cz - oz, 0.045, 0xFFFFFF, a * 0.6f);
                    if (j % 3 == 0) Motes.add(cx, hy, cz, 0.03, c, a * 0.35f);
                }
            }
            case VORTEX -> {
                for (int i = 0; i < 20; i++) {
                    double f = (tt * 0.012 + i / 20.0) % 1, r = 0.68 * (1 - f) + 0.04, ang = f * 4 * Math.PI + i;
                    Motes.add(
                        cx + Math.cos(ang) * r,
                        top + 0.75 * (1 - f),
                        cz + Math.sin(ang) * r,
                        0.035 + 0.035 * f,
                        c,
                        a * (0.3f + 0.7f * (float) f));
                }
                Motes.add(cx, top + 0.05, cz, 0.12 + 0.03 * Math.sin(tt * 0.3), c, a * 0.6f);
            }
            case GLYPHS -> {
                for (int i = 0; i < 10; i++) {
                    double f = (tt * 0.01 + i * 0.1 + Motifs.hash(i) * 0.05) % 1;
                    double gx = cx + (Motifs.hash(i) - 0.5) * 0.6 + 0.05 * Math.sin(tt * 0.05 + i),
                        gz = cz + (Motifs.hash(i + 7) - 0.5) * 0.6;
                    Motes.add(
                        gx,
                        top + f * 1.0,
                        gz,
                        i % 3 == 0 ? 0.06 : 0.04,
                        i % 2 == 0 ? c : 0xFFFFFF,
                        a * (float) (1 - f));
                }
            }
            case CAULDRON -> {
                for (int i = 0; i < 9; i++) {
                    double f = (tt * 0.015 + i / 9.0) % 1;
                    double bx = cx + (Motifs.hash(i) - 0.5) * 0.5, bz = cz + (Motifs.hash(i + 4) - 0.5) * 0.5;
                    Motes.add(bx, top + f * 0.6, bz, 0.03 + 0.05 * f, c, a * (float) (f < 0.85 ? 0.6 : (1 - f) * 4));
                }
            }
            case RUNES -> {
                double hub = top + 0.42;
                Motes.add(cx, hub, cz, 0.1 + 0.025 * Math.sin(tt * 0.2), 0xFFFFFF, a * 0.7f);
                for (int i = 0; i < 4; i++) {
                    double ang = tt * 0.05 + i * Math.PI / 2, sx = cx + Math.cos(ang) * 0.55,
                        sz = cz + Math.sin(ang) * 0.55, sy = hub + 0.08 * Math.sin(tt * 0.1 + i);
                    Motes.add(sx, sy, sz, 0.08, c, a);
                    double f = (tt * 0.04 + i * 0.25) % 1;
                    Motes.add(sx + (cx - sx) * f, sy + (hub - sy) * f, sz + (cz - sz) * f, 0.035, c, a * 0.6f);
                }
            }
            case BLOOD -> {
                for (int i = 0; i < 5; i++) {
                    double f = (tt * 0.03 + i / 5.0) % 1;
                    double bx = cx + (Motifs.hash(i) - 0.5) * 0.4, bz = cz + (Motifs.hash(i + 2) - 0.5) * 0.4;
                    Motes.add(bx, top + 0.9 - f * 0.88, bz, 0.05, c, a * 0.9f);
                    if (f > 0.9) Motes.add(bx, top + 0.03, bz, 0.09 * (f - 0.9) * 10, c, a * (float) (1 - f) * 8);
                }
            }
            case PREY -> {
                float sp = (tt % 40f) / 40f;
                for (int i = 0; i < 12; i++) {
                    double ang = i * Math.PI / 6 + seed, v = 0.4 + Motifs.hash(i) * 0.3;
                    Motes.add(
                        cx + Math.cos(ang) * v * sp,
                        top + 0.7 * sp - 0.6 * sp * sp,
                        cz + Math.sin(ang) * v * sp,
                        0.04,
                        i % 3 == 0 ? 0xFFE27A : c,
                        a * (1 - sp));
                }
            }
            case SPROUT -> {
                for (int i = 0; i < 7; i++) {
                    double f = (tt * 0.01 + i / 7.0) % 1;
                    Motes.add(
                        cx + 0.25 * Math.sin(f * 6 + i),
                        top + f * 0.9,
                        cz + 0.25 * Math.cos(f * 5 + i * 2),
                        0.045,
                        i % 2 == 0 ? c : 0xB8FF8A,
                        a * (float) (1 - f));
                }
            }
            case FOUNTAIN -> {
                for (int i = 0; i < 18; i++) {
                    double f = (tt * 0.025 + i / 18.0) % 1, ang = i * 2.4;
                    double r = 0.38 * f;
                    Motes.add(
                        cx + Math.cos(ang) * r,
                        top + 1.3 * f - 1.1 * f * f,
                        cz + Math.sin(ang) * r,
                        0.045,
                        i % 4 == 0 ? FluxDraw.MANA_PINK : c,
                        a * (float) (1 - f * 0.7));
                }
            }
        }
    }
}
