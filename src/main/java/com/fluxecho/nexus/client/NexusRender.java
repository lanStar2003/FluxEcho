package com.fluxecho.nexus.client;

import static com.fluxecho.client.FluxDraw.*;

import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.client.FarDraw;
import com.fluxecho.client.FluxDraw;
import com.fluxecho.client.Motes;
import com.fluxecho.client.ShaderCompat;
import com.fluxecho.core.EchoText;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.Compact;
import com.fluxecho.logic.ResearchTree;
import com.fluxecho.logic.RingSlots;
import com.fluxecho.nexus.BlockNexus;
import com.fluxecho.nexus.ClientTiles;
import com.fluxecho.nexus.TileModule;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.nexus.TileNexus;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * What a formed Flux Nexus is (blueprint 3.3): its pillars, conduits, seat and ring have given way to four curved,
 * segmented ribs rising from the base's corners and leaning in to hold a turning ring, a thinner one against it, and
 * the crystal core floating in the ring's middle over a cradle of light. Seams of light breathe in the ribs' joints.
 * While powered, a beam runs from the core up into the sky, pulses climbing it, seen from as far as the nexus is
 * loaded. While it researches, a constellation turns above the ring with data streaming up to it; while it
 * manifests, motes spiral in to the core. Bridges of light run out to the modules docked on its ring, and the open
 * slots show their outline to whoever holds a terminal or a module's core (not on an active campus: its masterplan
 * shows the sites instead). Forming, a sweep of light runs up the structure as its blocks give way.
 * <p>
 * Drawn after the world in the shader-safe way ({@link Shapes}), and again behind light gates ({@link FarDraw}). Each
 * multiblock is drawn on its own: an error in one is logged once, the GL state it left is put back, and the others
 * are still drawn (an error escaping here would also turn off everything else drawn into the light gates).
 */
public final class NexusRender {

    private static final int STEEL = 0x1C3446, STEEL_LIGHT = 0x2C5470, STEEL_DARK = 0x0E1C28, CORE = 0xE8FBFF;
    /** How far (blocks) a powered nexus's sky beam is drawn when the rest of it is not. */
    private static final double BEAM_RANGE = 512;
    /** Rib segments, and the rib's path in (out from the centre, up from the base): a curve through three points. */
    private static final int RIB_SEGMENTS = 7;
    private static final double[] RIB_FOOT = { 4.5, 0.0 }, RIB_BEND = { 4.7, 3.0 }, RIB_HEAD = { 3.0, 4.4 };
    /** Set when finding a site module's door failed once: every module keeps its ring bridge from then on. */
    private static boolean siteBridgesOff;
    /** Set once drawing a multiblock has failed and been logged. */
    private static boolean warned;

    private NexusRender() {}

    public static void register() {
        NexusRender h = new NexusRender();
        MinecraftForge.EVENT_BUS.register(h);
        FarDraw.add(h::onRenderLast);
    }

    @SubscribeEvent
    public void onRenderLast(RenderWorldLastEvent e) {
        if (!Config.nexusEffects || ShaderCompat.shadowPass()) return;
        Minecraft mc = Minecraft.getMinecraft();
        World w = mc.theWorld;
        if (w == null) return;
        double t = w.getTotalWorldTime() + e.partialTicks;
        double range = Config.nexusEffectRange;
        boolean hints = holdsHint(mc.thePlayer);
        for (TileMultiblock m : ClientTiles.all()) {
            if (m.getWorldObj() != w || m.isInvalid()) continue;
            try {
                draw(m, t, range, hints);
            } catch (Throwable x) {
                recover();
                if (!warned) {
                    warned = true;
                    FluxEcho.LOG.warn(
                        "Drawing the multiblock at {}, {}, {} failed; the others are still drawn (logged once)",
                        m.xCoord,
                        m.yCoord,
                        m.zCoord,
                        x);
                }
            } finally {
                Shapes.end();
            }
        }
    }

    /** One multiblock: all of it within the effects' range, a nexus's sky beam alone further out. */
    private static void draw(TileMultiblock m, double t, double range, boolean hints) {
        double dx = m.xCoord + 0.5 - RenderManager.renderPosX, dy = m.yCoord + 0.5 - RenderManager.renderPosY,
            dz = m.zCoord + 0.5 - RenderManager.renderPosZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist > range + 48) {
            // past the effects' range only the sky beam is drawn
            if (m instanceof TileNexus n && dist < BEAM_RANGE)
                farBeam(n, t, (float) Math.min(1, (BEAM_RANGE - dist) / 64));
            return;
        }
        float fade = (float) Math.min(1, (range + 48 - dist) / 16);
        if (m instanceof TileNexus n) nexus(n, t, fade, hints);
        else if (m instanceof TileModule mod) ModuleRender.draw(mod, t, fade);
    }

    /**
     * After an error part-way through a multiblock: ends the motes' batch if it was open (it pops their GL state),
     * then a quad batch the error broke off (the tessellator refuses to start another while one is open, which would
     * take every later drawing with it). {@link Shapes#end} follows in the caller's finally.
     */
    private static void recover() {
        try {
            Motes.end();
        } catch (Throwable ignored) {
            // the motes' state is popped whatever happens; nothing more to do
        }
        try {
            Tessellator.instance.draw();
        } catch (Throwable ignored) {
            // nothing was open
        }
    }

    /**
     * Whether the player holds something that wants the ring's slots shown: a flux terminal or a multiblock core. The
     * campus masterplan ({@code campus.client.Masterplan}) shows for the same items.
     */
    public static boolean holdsHint(EntityPlayer p) {
        if (p == null) return false;
        ItemStack s = p.getHeldItem();
        if (s == null) return false;
        Item i = s.getItem();
        if (i instanceof ItemBlock && net.minecraft.block.Block.getBlockFromItem(i) instanceof BlockNexus) return true;
        return i == com.fluxlite.item.ModItems.terminal;
    }

    private static double rx(double x) {
        return x - RenderManager.renderPosX;
    }

    private static double ry(double y) {
        return y - RenderManager.renderPosY;
    }

    private static double rz(double z) {
        return z - RenderManager.renderPosZ;
    }

    static void nexus(TileNexus n, double t, float fade, boolean hints) {
        if (!n.formed()) return;
        int[] c = n.centre();
        double cx = rx(c[0] + 0.5), cz = rz(c[2] + 0.5), base = ry(c[1] + 1.0);
        boolean on = (n.clientActivity & TileNexus.POWERED) != 0;
        float p = (on ? 1f : 0.3f) * fade;
        double sweep = n.sweepLevel();
        float appear = sweep == Double.MAX_VALUE ? 1f
            : (float) Math
                .max(0, Math.min(1, (System.currentTimeMillis() - n.formedAt) / (double) TileMultiblock.SWEEP_MS));
        p *= 0.2f + 0.8f * appear;
        double spin = on ? t * 0.004 : 0;

        // the solid parts: ribs and the ring
        Shapes.begin(false);
        for (int i = 0; i < 4; i++) rib(cx, base, cz, i, 0.95f * appear * fade);
        double ry = base + 4.5;
        for (int k = 0; k < 8; k++) {
            double a0 = spin + k * Math.PI / 4 + 0.05, a1 = spin + (k + 1) * Math.PI / 4 - 0.05;
            Shapes.ring(
                cx,
                ry + 0.12,
                cz,
                2.6,
                3.4,
                a0,
                a1,
                STEEL,
                0.95f * appear * fade,
                STEEL_LIGHT,
                0.95f * appear * fade);
            Shapes
                .ring(cx, ry - 0.12, cz, 2.6, 3.4, a0, a1, STEEL, 0.95f * appear * fade, STEEL, 0.95f * appear * fade);
            Shapes.band(
                cx,
                cz,
                3.4,
                ry - 0.12,
                ry + 0.12,
                a0,
                a1,
                STEEL_LIGHT,
                0.95f * appear * fade,
                0.95f * appear * fade);
            Shapes.band(cx, cz, 2.6, ry - 0.12, ry + 0.12, a0, a1, STEEL, 0.95f * appear * fade, 0.95f * appear * fade);
        }
        double bob = Math.sin(t * 0.04) * 0.1, core = ry + bob;
        boolean making = (n.clientActivity & TileNexus.MANIFESTING) != 0;
        Shapes.crystal(cx, core, cz, 0.5, 0.85, t * 0.03, CORE, making ? VIOLET : CYAN, 0.92f * appear * fade);

        // the glows
        Shapes.additive(true);
        float pulse = 0.75f + 0.25f * (float) Math.sin(t * 0.08);
        Shapes.disc(cx, base + 0.03, cz, 5.6, CYAN, 0.12f * p, 0f);
        Shapes.ring(cx, base + 0.04, cz, 4.55, 4.9, 0, Math.PI * 2, CYAN, 0f, CYAN, 0.4f * p * pulse);
        Shapes.ring(cx, base + 0.04, cz, 2.05, 2.25, 0, Math.PI * 2, VIOLET, 0.5f * p, VIOLET, 0f);
        // the cradle the core floats over, the column feeding it from the base
        Shapes.ring(cx, base + 3.1, cz, 0.55, 0.95, 0, Math.PI * 2, CYAN, 0.5f * p * pulse, CYAN, 0f);
        Shapes.ring(cx, base + 3.1, cz, 0.95, 1.05, 0, Math.PI * 2, WHITE, 0.6f * p, WHITE, 0.6f * p);
        Shapes.beam(cx, base, cz, base + 3.1, 0.3, CORE, 0.5f * p, 0.8f * p);
        if (on) skyBeam(cx, core, cz, n.yCoord, t, p);
        for (int k = 0; k < 8; k++) {
            double a0 = spin + k * Math.PI / 4 + 0.05, a1 = spin + (k + 1) * Math.PI / 4 - 0.05;
            Shapes.ring(cx, ry + 0.13, cz, 3.34, 3.44, a0, a1, CYAN, 0.9f * p, CYAN, 0.9f * p);
            Shapes.ring(cx, ry + 0.13, cz, 2.56, 2.66, a0, a1, CYAN, 0.6f * p, CYAN, 0.6f * p);
        }
        double counter = on ? -t * 0.008 : 0;
        for (int k = 0; k < 24; k += 2) {
            double a0 = counter + k * Math.PI / 12, a1 = a0 + Math.PI / 12 * 0.8;
            Shapes.ring(cx, ry, cz, 3.78, 3.88, a0, a1, VIOLET, 0.65f * p, VIOLET, 0.65f * p);
        }
        for (int i = 0; i < 4; i++) seams(cx, base, cz, i, t, p);
        boolean researching = (n.clientActivity & TileNexus.RESEARCHING) != 0;
        if (researching) constellation(n, cx, base + 8.5, cz, t, p);
        if (sweep != Double.MAX_VALUE) sweep(cx, ry(sweep), cz, appear);
        bridges(n, cx, base, cz, t, p, hints);
        Shapes.end();

        // motes: the core's halo, the column, the orbit, the making
        Motes.begin();
        Motes.add(cx, core, cz, 1.3, making ? VIOLET : CYAN, 0.55f * p * pulse);
        if (on) for (int k = 0; k < 10; k++) {
            double f = (t * 0.012 + k / 10.0) % 1;
            double a = k * 2.4 + t * 0.02;
            Motes.add(cx + Math.cos(a) * 0.4, core + f * 16, cz + Math.sin(a) * 0.4, 0.12, CORE, (float) (1 - f) * p);
        }
        for (int k = 0; k < 3; k++) {
            double a = t * 0.05 + k * Math.PI * 2 / 3;
            Motes.add(cx + Math.cos(a) * 3.0, ry + 0.25, cz + Math.sin(a) * 3.0, 0.2, WHITE, 0.8f * p);
        }
        if (making) for (int k = 0; k < 8; k++) {
            double f = (t * 0.015 + k / 8.0) % 1;
            double a = k * Math.PI / 4 + f * 3;
            double r = 4.7 * (1 - f);
            Motes.add(
                cx + Math.cos(a) * r,
                base + 0.2 + f * 4.3,
                cz + Math.sin(a) * r,
                0.18,
                VIOLET,
                (float) Math.sin(f * Math.PI) * p);
        }
        if (researching) for (int k = 0; k < 6; k++) {
            double f = (t * 0.02 + k / 6.0) % 1;
            Motes.add(
                cx + Math.sin(k * 2.1 + t * 0.05) * 0.3 * f,
                core + f * 4,
                cz + Math.cos(k * 1.3) * 0.3 * f,
                0.14,
                branchColor(n),
                (float) Math.sin(f * Math.PI) * p);
        }
        if (sweep != Double.MAX_VALUE) for (int k = 0; k < 16; k++) {
            double a = k * Math.PI / 8 + t * 0.1;
            Motes.add(
                cx + Math.cos(a) * 4.5,
                ry(sweep) + Math.sin(k + t * 0.3) * 0.3,
                cz + Math.sin(a) * 4.5,
                0.25,
                CORE,
                0.8f);
        }
        Motes.end();

        if (n.clientHologram) hologram(n, t, fade);
    }

    /** A point of a rib's curve, {@code f} from its foot (0) to its head (1): {out, up}. */
    private static double[] ribAt(double f) {
        double g = 1 - f;
        return new double[] { g * g * RIB_FOOT[0] + 2 * g * f * RIB_BEND[0] + f * f * RIB_HEAD[0],
            g * g * RIB_FOOT[1] + 2 * g * f * RIB_BEND[1] + f * f * RIB_HEAD[1] };
    }

    /** Rib {@code i}'s way out from the centre: a diagonal, {x, z}. */
    private static double[] ribDir(int i) {
        double s = Math.sqrt(0.5);
        return new double[] { (i % 2 == 0 ? -s : s), (i < 2 ? -s : s) };
    }

    /** A rib: segments of steel along the curve, a small gap between each, thinning towards the head. */
    private static void rib(double cx, double base, double cz, int i, float a) {
        double[] u = ribDir(i);
        double[] side = { -u[1], 0, u[0] };
        for (int k = 0; k < RIB_SEGMENTS; k++) {
            double f0 = (k + 0.06) / RIB_SEGMENTS, f1 = (k + 0.94) / RIB_SEGMENTS;
            double[] p = ribAt(f0), q = ribAt(f1);
            double dr = q[0] - p[0], dh = q[1] - p[1], len = Math.hypot(dr, dh);
            // across the rib in the plane it bends in, away from the centre
            double[] out = { u[0] * dh / len, -dr / len, u[1] * dh / len };
            double w = 0.2 - 0.06 * k / (double) RIB_SEGMENTS, d = 0.16 - 0.04 * k / (double) RIB_SEGMENTS;
            prism(
                new double[] { cx + u[0] * p[0], base + p[1], cz + u[1] * p[0] },
                new double[] { cx + u[0] * q[0], base + q[1], cz + u[1] * q[0] },
                side,
                out,
                w,
                d,
                a);
        }
    }

    /**
     * A square bar from {@code a} to {@code b}: half-width {@code w} along {@code side}, {@code d} along {@code out}.
     */
    private static void prism(double[] a, double[] b, double[] side, double[] out, double w, double d, float alpha) {
        double[][] c = new double[8][];
        int[][] signs = { { -1, -1 }, { 1, -1 }, { 1, 1 }, { -1, 1 } };
        for (int k = 0; k < 4; k++) {
            double sw = signs[k][0] * w, sd = signs[k][1] * d;
            c[k] = new double[] { a[0] + side[0] * sw + out[0] * sd, a[1] + side[1] * sw + out[1] * sd,
                a[2] + side[2] * sw + out[2] * sd };
            c[k + 4] = new double[] { b[0] + side[0] * sw + out[0] * sd, b[1] + side[1] * sw + out[1] * sd,
                b[2] + side[2] * sw + out[2] * sd };
        }
        // the outer face catches the light, the inner one is in shadow, the sides between
        int[] shade = { STEEL_DARK, STEEL, STEEL_LIGHT, STEEL };
        for (int k = 0; k < 4; k++) {
            int n = (k + 1) % 4;
            Shapes.quadShade(c[k], c[n], c[n + 4], c[k + 4], shade[k], Shapes.mix(shade[k], STEEL_DARK, 0.3), alpha);
        }
        Shapes.quadShade(c[0], c[1], c[2], c[3], STEEL_DARK, STEEL_DARK, alpha);
        Shapes.quadShade(c[4], c[5], c[6], c[7], STEEL_LIGHT, STEEL_LIGHT, alpha);
    }

    /** The light in a rib: its joints and a line up its inner edge, breathing slowly. */
    private static void seams(double cx, double base, double cz, int i, double t, float p) {
        double[] u = ribDir(i);
        float breath = 0.35f + 0.65f * (float) (0.5 + 0.5 * Math.sin(t * 0.035 + i * Math.PI / 2));
        for (int k = 0; k <= RIB_SEGMENTS; k++) {
            double[] q = ribAt(k / (double) RIB_SEGMENTS);
            double x = cx + u[0] * q[0], y = base + q[1], z = cz + u[1] * q[0];
            double sx = -u[1] * 0.24, sz = u[0] * 0.24;
            Shapes.string(x - sx, y, z - sz, x + sx, y, z + sz, 0.07, CYAN, 0.9f * p * breath, 0.9f * p * breath);
        }
        for (int k = 0; k < RIB_SEGMENTS * 2; k++) {
            double[] a = ribAt(k / (RIB_SEGMENTS * 2.0)), b = ribAt((k + 1) / (RIB_SEGMENTS * 2.0));
            // a hair inside the rib's inner face
            double ia = a[0] - 0.17, ib = b[0] - 0.17;
            Shapes.string(
                cx + u[0] * ia,
                base + a[1],
                cz + u[1] * ia,
                cx + u[0] * ib,
                base + b[1],
                cz + u[1] * ib,
                0.04,
                CYAN,
                0.5f * p * breath,
                0.5f * p * breath);
        }
    }

    /** The beam from the core up into the sky, its top at least at y 320, with pulses climbing it. */
    private static void skyBeam(double cx, double core, double cz, int yCoord, double t, float p) {
        double top = ry(Math.max(320, yCoord + 160)), mid = core + 64;
        Shapes.beam(cx, core, cz, mid, 0.34, 0xC8F8FF, 0.9f * p, 0.6f * p);
        Shapes.beam(cx, mid, cz, top, 0.34, 0xC8F8FF, 0.6f * p, 0f);
        Shapes.beam(cx, core, cz, core + 40, 1.3, CYAN, 0.25f * p, 0f);
        double span = top - core;
        for (int k = 0; k < 5; k++) {
            double f = (t * 0.0025 + k / 5.0) % 1;
            double y = core + f * span;
            float a = 0.7f * p * (float) (1 - f);
            Shapes.beam(cx, y, cz, y + 4, 0.7, WHITE, 0f, a);
            Shapes.beam(cx, y + 4, cz, y + 8, 0.7, WHITE, a, 0f);
        }
    }

    /** The sky beam alone, for a nexus past the effects' range. */
    private static void farBeam(TileNexus n, double t, float fade) {
        if (!n.formed() || (n.clientActivity & TileNexus.POWERED) == 0 || fade <= 0) return;
        int[] c = n.centre();
        Shapes.begin(true);
        skyBeam(rx(c[0] + 0.5), ry(c[1] + 1.0) + 4.5, rz(c[2] + 0.5), n.yCoord, t, fade);
        Shapes.end();
    }

    private static int branchColor(TileNexus n) {
        ResearchTree.Node node = ResearchTree.get(n.clientResearch);
        return node == null ? VIOLET : node.branch.color;
    }

    /** The research turning above the ring: a constellation in its branch's colour, with its progress round it. */
    private static void constellation(TileNexus n, double cx, double y, double cz, double t, float p) {
        int col = branchColor(n);
        double spin = t * 0.01;
        double[][] pts = new double[7][];
        for (int i = 0; i < 6; i++) {
            double a = spin + i * Math.PI / 3, r = i % 2 == 0 ? 1.7 : 1.1;
            pts[i] = new double[] { cx + Math.cos(a) * r, y + Math.sin(i * 1.9) * 0.35, cz + Math.sin(a) * r };
        }
        pts[6] = new double[] { cx, y + 0.2, cz };
        for (int i = 0; i < 6; i++) {
            double[] a = pts[i], b = pts[(i + 1) % 6];
            Shapes.string(a[0], a[1], a[2], b[0], b[1], b[2], 0.05, col, 0.6f * p, 0.6f * p);
            if (i % 2 == 0)
                Shapes.string(a[0], a[1], a[2], pts[6][0], pts[6][1], pts[6][2], 0.04, col, 0.4f * p, 0.7f * p);
        }
        double done = Math.max(0.01, n.clientResearchDone) * Math.PI * 2;
        Shapes.ring(cx, y - 0.6, cz, 2.2, 2.32, -Math.PI / 2, -Math.PI / 2 + done, col, 0.9f * p, col, 0.9f * p);
        Shapes.ring(cx, y - 0.6, cz, 2.2, 2.32, -Math.PI / 2 + done, Math.PI * 1.5, SEAM, 0.5f * p, SEAM, 0.5f * p);
    }

    /** The light running up the structure while it forms. */
    private static void sweep(double cx, double y, double cz, float appear) {
        float a = 0.6f * (1 - appear * 0.5f);
        Shapes.disc(cx, y, cz, 6.0, CYAN, a, 0.05f);
        Shapes.ring(cx, y, cz, 5.6, 6.1, 0, Math.PI * 2, WHITE, 0.8f, CYAN, 0f);
    }

    /**
     * Bridges to the docked modules; the outlines of the open slots while the player holds a hint. On an active
     * campus a module docked by its site (0.10.0) gets its bridge along the site axis, from the dais edge to its door;
     * a module on the 0.9.2 inner ring keeps its ring bridge. An active campus shows no slot outlines: its masterplan
     * ({@code campus.client.Masterplan}) shows the sites, and the 0.9.2 inner-ring slots lie in its hall sites and on
     * its gate, where a module placed by hand would stand in the way of the campus's own jobs.
     */
    private static void bridges(TileNexus n, double cx, double base, double cz, double t, float p, boolean hints) {
        int fx = n.front().offsetX, fz = n.front().offsetZ;
        int docked = Integer.bitCount(n.clientDockedMask);
        boolean active = n.clientCampus != null && n.clientCampus.active;
        boolean campus = active && n.clientDockedMask != 0;
        if (active) hints = false;
        List<TileMultiblock> tiles = campus ? ClientTiles.all() : null;
        for (int k = 0; k < RingSlots.SLOTS; k++) {
            int[] o = RingSlots.offset(k, Config.innerRadius, fx, fz);
            double len = Math.hypot(o[0], o[1]);
            double ux = o[0] / len, uz = o[1] / len;
            double sx = cx + o[0], sz = cz + o[1];
            if ((n.clientDockedMask & 1 << k) != 0) {
                int col = n.clientModuleColor[k] == 0 ? CYAN : n.clientModuleColor[k];
                double door = campus && !siteBridgesOff ? siteDoorSafe(n, k, fx, fz, tiles) : -1;
                if (door > 0) {
                    siteBridge(cx, base, cz, k, fx, fz, door, t, col, p);
                    continue;
                }
                double x0 = cx + ux * 5.6, z0 = cz + uz * 5.6, x1 = sx - ux * 5.5, z1 = sz - uz * 5.5;
                path(x0, base + 0.15, z0, x1, z1, 0.7, col, 0.35f * p);
                path(x0, base + 0.16, z0, x1, z1, 0.18, WHITE, 0.4f * p);
                for (int i = 0; i < 5; i++) {
                    double f = (t * 0.01 + i / 5.0) % 1;
                    Shapes.disc(x0 + (x1 - x0) * f, base + 0.2, z0 + (z1 - z0) * f, 0.35, col, 0.9f * p, 0f);
                }
            } else if (hints && docked < n.clientOpen) {
                float a = 0.45f + 0.2f * (float) Math.sin(t * 0.1 + k);
                Shapes.square(sx, base + 0.06, sz, 10.5, 0.2, CYAN, a);
                Shapes.square(sx, base + 0.06, sz, 3.5, 0.12, VIOLET, a);
                Shapes.beam(sx, base, sz, base + 6, 0.25, CYAN, 0.5f, 0f);
            } else if (hints) {
                Shapes.square(sx, base + 0.06, sz, 10.5, 0.12, SEAM, 0.5f);
            }
        }
    }

    /**
     * {@link #siteDoor}, fail-soft: an error is logged once and turns the site bridges off for the session (every
     * module then keeps its ring bridge), so it cannot take the rest of the nexus's drawing with it.
     */
    private static double siteDoorSafe(TileNexus n, int k, int fx, int fz, List<TileMultiblock> tiles) {
        try {
            return siteDoor(n, k, fx, fz, tiles);
        } catch (RuntimeException e) {
            siteBridgesOff = true;
            FluxEcho.LOG.warn("Finding a campus module's door failed; site bridges are off until the game restarts", e);
            return -1;
        }
    }

    /**
     * How far from the nexus centre, along the axis of site {@code k}, the door of the module docked on it is; -1 when
     * that module stands on the 0.9.2 inner ring instead (it keeps its ring bridge). The module is looked for among the
     * loaded tiles (docked to this nexus under number {@code k}): its door is the face of its structure nearest the
     * nexus. When it is not loaded here, a hall site's module is taken to have its front row where the campus puts it
     * ({@link CampusPlan#HALL_FRONT}); any other site keeps the ring bridge.
     */
    private static double siteDoor(TileNexus n, int k, int fx, int fz, List<TileMultiblock> tiles) {
        int[] c = n.centre();
        int[] ax = CampusPlan.siteAxis(k);
        int[] w = CampusPlan.toWorld(ax[0], ax[1], fx, fz);
        double len = Math.hypot(w[0], w[1]), ux = w[0] / len, uz = w[1] / len;
        for (TileMultiblock m : tiles) {
            if (!(m instanceof TileModule mod) || m.isInvalid() || m.getWorldObj() != n.getWorldObj()) continue;
            if (!mod.clientDocked || mod.clientSlot != k || !Arrays.equals(mod.clientNexus, c)) continue;
            int[] mc = mod.centre();
            if (RingSlots.slotAt(mc[0] - c[0], mc[1] - c[1], mc[2] - c[2], Config.innerRadius, fx, fz) == k) return -1;
            int[] b = mod.bounds();
            if (b == null) break;
            // the corner of its block range nearest the nexus, measured from the middle of the centre cell
            double near = Double.MAX_VALUE;
            for (int i = 0; i < 4; i++) {
                double x = (i & 1) == 0 ? b[0] : b[3] + 1, z = (i & 2) == 0 ? b[2] : b[5] + 1;
                near = Math.min(near, (x - c[0] - 0.5) * ux + (z - c[2] - 0.5) * uz);
            }
            if (near > 6) return near;
            break;
        }
        for (int site : CampusPlan.HALL_SITES) if (site == k) return CampusPlan.HALL_FRONT - 0.5;
        // nothing docks by site on the gate or the reserved diagonals yet: a module there stands on the ring
        return -1;
    }

    /**
     * The bridge to a module docked by its site: a band of light along the site axis from the dais edge to the door
     * at {@code door} blocks out, pulses running out to it, and a bar of light across the threshold.
     */
    private static void siteBridge(double cx, double base, double cz, int k, int fx, int fz, double door, double t,
        int col, float p) {
        int[] ax = CampusPlan.siteAxis(k);
        int[] w = CampusPlan.toWorld(ax[0], ax[1], fx, fz);
        double len = Math.hypot(w[0], w[1]), ux = w[0] / len, uz = w[1] / len;
        double x0 = cx + ux * 5.6, z0 = cz + uz * 5.6, x1 = cx + ux * door, z1 = cz + uz * door;
        path(x0, base + 0.15, z0, x1, z1, 0.7, col, 0.35f * p);
        path(x0, base + 0.16, z0, x1, z1, 0.18, WHITE, 0.4f * p);
        for (int i = 0; i < 6; i++) {
            double f = (t * 0.008 + i / 6.0) % 1;
            Shapes.disc(x0 + (x1 - x0) * f, base + 0.2, z0 + (z1 - z0) * f, 0.35, col, 0.9f * p, 0f);
        }
        // the threshold: three blocks either side of the axis, as wide as the lit door row
        float glow = (0.55f + 0.25f * (float) Math.sin(t * 0.08 + k)) * p;
        double tx = -uz * 3.5, tz = ux * 3.5, mx = x1 - ux * 0.5, mz = z1 - uz * 0.5;
        path(mx - tx, base + 0.16, mz - tz, mx + tx, mz + tz, 0.3, col, glow);
    }

    /** A level band from one point to another, for bridges on the ground. */
    static void path(double x0, double y, double z0, double x1, double z1, double w, int rgb, float a) {
        double dx = x1 - x0, dz = z1 - z0, len = Math.hypot(dx, dz);
        if (len < 1e-3) return;
        double nx = -dz / len * w / 2, nz = dx / len * w / 2;
        Shapes.quad(x0 - nx, y, z0 - nz, x0 + nx, z0 + nz, x1 + nx, z1 + nz, x1 - nx, z1 - nz, rgb, a);
    }

    /** The nexus's state over its controller, turned to the viewer. */
    private static void hologram(TileNexus n, double t, float fade) {
        double x = rx(n.xCoord + 0.5), y = ry(n.yCoord + 1.4), z = rz(n.zCoord + 0.5);
        double dist = Math.sqrt(x * x + y * y + z * z);
        float a = (float) Math.min(1, (24 - dist) / 4) * fade;
        if (a <= 0.05f) return;
        int w = 150, h = 62;
        float px = 1 / 80f;
        FluxDraw.worldBegin();
        GL11.glPushMatrix();
        // the matrix and the GL state are put back on every path, so an error here cannot leave them pushed
        try {
            hologramPane(n, t, x, y, z, w, h, px, a);
        } finally {
            GL11.glPopMatrix();
            FluxDraw.worldEnd();
        }
    }

    /** The hologram's pane and lines, inside {@link #hologram}'s matrix and GL state. */
    private static void hologramPane(TileNexus n, double t, double x, double y, double z, int w, int h, float px,
        float a) {
        GL11.glTranslated(x, y, z);
        GL11.glRotatef(-RenderManager.instance.playerViewY, 0f, 1f, 0f);
        GL11.glTranslated(0, h * px, 0);
        GL11.glScalef(-px, -px, px);
        GL11.glTranslatef(-w / 2f, 0, 0);
        FluxDraw.begin();
        pane(0, 0, w, h, a * 0.9f);
        hgradient(1, 1, w * 0.6, 12, CYAN, 0.3f * a, CYAN, 0f);
        boolean on = (n.clientActivity & TileNexus.POWERED) != 0;
        String research = n.clientResearch;
        if (!research.isEmpty()) {
            bar(6, h - 12, w - 6, h - 6, n.clientResearchDone, branchColor(n), a);
            scan(6, h - 12, w - 6, h - 6, (float) t, WHITE, 0.08f * a);
        }
        FluxDraw.end();
        text(EchoText.t("nexus.gui.title", EchoText.t("nexus.phase." + TileNexus.PHASE)), 5, 3, CYAN, a);
        text(
            on ? EchoText.t("nexus.holo.powered", Compact.si(n.clientUpkeep)) : EchoText.t("nexus.status.no_power"),
            5,
            16,
            on ? WHITE : RED,
            a);
        text(
            EchoText
                .t("nexus.holo.compute", n.clientCompute10 / 10.0, Integer.bitCount(n.clientDockedMask), n.clientOpen),
            5,
            26,
            CYAN,
            a);
        String line = research.isEmpty() ? EchoText.t("nexus.gui.no_research_short")
            : EchoText.t(
                "nexus.holo.research",
                EchoText.t("research.node." + research),
                Math.round(n.clientResearchDone * 100));
        text(fit(line, w - 10), 5, 38, research.isEmpty() ? DIM : WHITE, a);
    }
}
