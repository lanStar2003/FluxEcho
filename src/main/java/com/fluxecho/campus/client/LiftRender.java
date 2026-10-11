package com.fluxecho.campus.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.campus.Lift;
import com.fluxecho.client.FarDraw;
import com.fluxecho.client.Motes;
import com.fluxecho.client.ShaderCompat;
import com.fluxecho.logic.LiftLook;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The light lift's light (0.12.0, CITY.md §2.5: "the beam runs between pads in cyan with rising motes"): over every
 * shaft {@link LiftScan} found, a column of cyan light from each pad up to the pad its ride stops on, motes rising up
 * it
 * in a slow spiral, and on each pad's iris a ring of light breathing; a ride that would bump something in its way
 * shows as a short dim stub up to it, and a pad with no ride up (a capped shaft) shows only its iris.
 * <p>
 * Seen from inside the shaft (a rider, or anyone standing on a pad) the crossed planes of the beam would cut through
 * the view, so the beam is drawn from within instead: hoops of light fixed to the shaft every
 * {@link LiftLook#HOOP_STEP} blocks (a rider rushes past them, faster as the ride gathers speed), and while riding
 * streaks of light streaming past the other way, brightest where the ride is fastest.
 * <p>
 * Budgets (quads a frame): a beam 8 and at most {@value #MOTES} motes; a pad's iris {@value #IRIS} and a mote, within
 * {@value #IRIS_RANGE} blocks; from inside, at most {@code 2 * HOOPS + 1} hoops of {@value #IRIS} pieces and
 * {@value #STREAKS} streaks. Beams within {@value #RANGE} blocks, the last 24 fading.
 * <p>
 * Shader-safe as {@link Shapes}: drawn after the world, skipped in the shadow pass, relative to
 * {@code RenderManager.renderPos*}, quads only with a normal towards the viewer, additive, no depth writes, texturing
 * on again after; registered with {@link FarDraw}, so the light gates draw it again, and it only streams the shafts the
 * client tick found and animates by the world time, so every draw of a frame is the same. The beams and the inside
 * view fail on their own: an error is logged once and turns that part off until the game restarts.
 */
@SideOnly(Side.CLIENT)
public final class LiftRender {

    private static final int BEAM = 0x9AF4FF, CORE = 0xE8FBFF, STUB = 0x5A7A8C;
    /** How far the beams are drawn, and the pads' irises. */
    private static final double RANGE = 160, IRIS_RANGE = 48;
    /** The most motes on one beam, the pieces of an iris or a hoop, the streaks round a rider. */
    private static final int MOTES = 28, IRIS = 16, STREAKS = 10;
    /** How near the column's middle the camera is inside the shaft. */
    private static final double INSIDE = 0.8;

    private static final int BEAMS = 0, WITHIN = 1;
    private static final String[] NAMES = { "beams", "view from the shaft" };
    private static final boolean[] OFF = new boolean[NAMES.length];

    private LiftRender() {}

    /** Draws after the world and behind light gates, with the client tick that finds the shafts. */
    public static void register() {
        LiftRender h = new LiftRender();
        MinecraftForge.EVENT_BUS.register(h);
        FarDraw.add(h::onRenderLast);
        LiftScan.register();
    }

    @SubscribeEvent
    public void onRenderLast(RenderWorldLastEvent e) {
        if (ShaderCompat.shadowPass() || !Config.buildEffects) return;
        World w = Minecraft.getMinecraft().theWorld;
        if (w == null) return;
        List<LiftScan.Shaft> shafts = LiftScan.shafts();
        if (shafts.isEmpty()) return;
        double t = w.getTotalWorldTime() + e.partialTicks;
        double camX = RenderManager.renderPosX, camY = RenderManager.renderPosY, camZ = RenderManager.renderPosZ;
        double[] ride = Lift.clientRide();
        for (LiftScan.Shaft s : shafts) {
            double x = s.x + 0.5 - camX, z = s.z + 0.5 - camZ;
            boolean inside = Math.abs(x) < INSIDE && Math.abs(z) < INSIDE;
            boolean rider = ride != null && (int) ride[0] == s.x && (int) ride[1] == s.z;
            if (!OFF[BEAMS]) try {
                shaft(s, x, z, camY, t, inside, rider);
            } catch (Throwable ex) {
                recover();
                fail(BEAMS, ex);
            } finally {
                Shapes.end();
            }
            if (inside && !OFF[WITHIN]) try {
                within(s, x, z, camY, t, rider);
            } catch (Throwable ex) {
                recover();
                fail(WITHIN, ex);
            } finally {
                Shapes.end();
            }
        }
    }

    /**
     * One shaft from outside: each beam a soft wide column and a bright core, motes rising in a slow spiral; a stub
     * dim; the pads' irises. From inside (the camera in the column) the columns are left to {@link #within}.
     */
    private static void shaft(LiftScan.Shaft s, double x, double z, double camY, double t, boolean inside,
        boolean rider) {
        boolean begun = false;
        for (LiftLook.Beam b : s.beams) {
            double lo = b.lo - camY, hi = b.hi - camY;
            double dy = Math.max(0, Math.max(lo, -hi));
            double d = Math.sqrt(x * x + z * z + dy * dy);
            if (d > RANGE) continue;
            float fade = (float) Math.min(1, (RANGE - d) / 24) * (rider ? 1.3f : 1f);
            if (!begun) {
                Shapes.begin(true);
                begun = true;
            }
            if (inside) continue;
            if (b.blocked) {
                Shapes.beam(x, lo, z, hi, 0.22, STUB, 0.3f * fade, 0f);
                continue;
            }
            float breath = 0.85f + 0.15f * (float) Math.sin(t * 0.07 + s.x * 0.3 + b.pad);
            Shapes.beam(x, lo, z, hi, 0.75, BEAM, 0.12f * fade * breath, 0.12f * fade * breath);
            Shapes.beam(x, lo, z, hi, 0.2, CORE, 0.5f * fade * breath, 0.5f * fade * breath);
        }
        // the irises on the pads
        for (int y : s.pads) {
            double py = y + 1.012 - camY, d = Math.sqrt(x * x + z * z + py * py);
            if (d > IRIS_RANGE) continue;
            if (!begun) {
                Shapes.begin(true);
                begun = true;
            }
            float a = (float) Math.min(1, (IRIS_RANGE - d) / 12) * (0.35f + 0.15f * (float) Math.sin(t * 0.09 + y));
            ring(x, py, z, 0.16, 0.44, BEAM, a, t * 0.02 + y);
        }
        if (!begun) return;
        Shapes.end();
        Motes.begin();
        for (LiftLook.Beam b : s.beams) {
            // no motes through the eye of someone in the beam: they would fill the view
            if (b.blocked || inside && camY >= b.lo - 2 && camY <= b.hi + 2.5) continue;
            double lo = b.lo - camY, len = b.hi - b.lo;
            double dy = Math.max(0, Math.max(lo, camY - b.hi));
            double d = Math.sqrt(x * x + z * z + dy * dy);
            if (d > RANGE || len <= 0) continue;
            float fade = (float) Math.min(1, (RANGE - d) / 24);
            int n = Math.max(4, Math.min(MOTES, (int) (len / 1.6)));
            for (int i = 0; i < n; i++) {
                double f = (t * 0.1 / len + i / (double) n) % 1;
                double a = i * 2.4 + t * 0.05, r = 0.22 + 0.06 * Math.sin(i * 1.7);
                Motes.add(
                    x + Math.cos(a) * r,
                    lo + f * len,
                    z + Math.sin(a) * r,
                    0.09,
                    i % 4 == 0 ? CORE : BEAM,
                    (float) Math.sin(f * Math.PI) * 0.75f * fade);
            }
        }
        for (int y : s.pads) {
            double py = y + 1.05 - camY;
            if (inside && Math.abs(py) < 3) continue;
            if (Math.sqrt(x * x + z * z + py * py) <= IRIS_RANGE) Motes.add(x, py, z, 0.3, BEAM, 0.3f);
        }
        Motes.end();
    }

    /**
     * The shaft seen from inside: hoops of light fixed to the shaft round the camera (brightest at eye level, fading
     * up and down), and while riding streaks streaming past the other way and motes rushing by.
     */
    private static void within(LiftScan.Shaft s, double x, double z, double camY, double t, boolean rider) {
        LiftLook.Beam b = null;
        for (LiftLook.Beam c : s.beams) if (!c.blocked && camY >= c.lo - 2 && camY <= c.hi + 2.5) b = c;
        if (b == null) return;
        double speed = rider ? Math.abs(Lift.clientSpeed()) : 0, rush = Math.min(1, speed / 0.6);
        Shapes.begin(true);
        for (double hy : LiftLook.hoops(b.lo, b.hi, camY)) {
            double dy = hy - camY;
            float a = (float) Math.exp(-dy * dy / 18) * (0.22f + 0.25f * (float) rush);
            if (a < 0.01f) continue;
            ring(x, dy, z, 0.66, 0.74, BEAM, a, 0);
        }
        Shapes.end();
        if (rush <= 0.05) return;
        Shapes.begin(true);
        Tessellator tes = Tessellator.instance;
        tes.startDrawingQuads();
        normalUp(tes);
        double dir = Math.signum(Lift.clientSpeed());
        for (int i = 0; i < STREAKS; i++) {
            double ang = i * 2 * Math.PI / STREAKS + 0.37 * i, r = 0.88;
            // each streak runs past the other way, a few blocks long, cycling with the world time
            double f = (t * 0.9 * rush / 6 + i * 0.37) % 1;
            double y0 = -dir * (f * 8 - 4), y1 = y0 + dir * 1.6;
            double px = x + Math.cos(ang) * r, pz = z + Math.sin(ang) * r;
            double sx = -Math.sin(ang) * 0.015, sz = Math.cos(ang) * 0.015;
            int ai = (int) (Math.sin(f * Math.PI) * 0.35 * rush * 255);
            tes.setColorRGBA_I(CORE, Math.max(0, Math.min(255, ai)));
            tes.addVertex(px - sx, y0, pz - sz);
            tes.addVertex(px + sx, y0, pz + sz);
            tes.setColorRGBA_I(BEAM, 0);
            tes.addVertex(px + sx, y1, pz + sz);
            tes.addVertex(px - sx, y1, pz - sz);
        }
        tes.draw();
        Shapes.end();
    }

    /** A flat ring of {@value #IRIS} pieces at height {@code y}, turned {@code turn} radians, bright inside. */
    private static void ring(double x, double y, double z, double r0, double r1, int rgb, float a, double turn) {
        Tessellator tes = Tessellator.instance;
        tes.startDrawingQuads();
        normalUp(tes);
        int ai = (int) (Math.max(0, Math.min(1, a)) * 255);
        for (int i = 0; i < IRIS; i++) {
            double p = turn + i * 2 * Math.PI / IRIS, q = turn + (i + 1) * 2 * Math.PI / IRIS;
            tes.setColorRGBA_I(rgb, i % 4 == 0 ? Math.min(255, ai * 3 / 2) : ai);
            tes.addVertex(x + Math.cos(p) * r0, y, z + Math.sin(p) * r0);
            tes.addVertex(x + Math.cos(q) * r0, y, z + Math.sin(q) * r0);
            tes.setColorRGBA_I(rgb, ai / 4);
            tes.addVertex(x + Math.cos(q) * r1, y, z + Math.sin(q) * r1);
            tes.addVertex(x + Math.cos(p) * r1, y, z + Math.sin(p) * r1);
        }
        tes.draw();
    }

    /** The viewer's normal on a batch of effect geometry (as {@link Shapes} gives its own). */
    private static void normalUp(Tessellator tes) {
        double yaw = Math.toRadians(RenderManager.instance.playerViewY),
            pitch = Math.toRadians(RenderManager.instance.playerViewX);
        tes.setNormal(
            (float) (Math.sin(yaw) * Math.cos(pitch)),
            (float) Math.sin(pitch),
            (float) (-Math.cos(yaw) * Math.cos(pitch)));
    }

    /** After an error: closes the motes' batch and a quad batch the error broke off. */
    private static void recover() {
        try {
            Motes.end();
        } catch (Throwable ignored) {
            // the motes' state is popped whatever happens
        }
        try {
            Tessellator.instance.draw();
        } catch (Throwable ignored) {
            // nothing was open
        }
    }

    private static void fail(int part, Throwable x) {
        if (OFF[part]) return;
        OFF[part] = true;
        FluxEcho.LOG.warn("The light lift's {} failed and is off until the game restarts", NAMES[part], x);
    }
}
