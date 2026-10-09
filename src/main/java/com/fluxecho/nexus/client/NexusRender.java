package com.fluxecho.nexus.client;

import static com.fluxecho.client.FluxDraw.*;

import net.minecraft.client.Minecraft;
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
import com.fluxecho.client.FarDraw;
import com.fluxecho.client.FluxDraw;
import com.fluxecho.client.Motes;
import com.fluxecho.client.ShaderCompat;
import com.fluxecho.core.EchoText;
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
 * What a formed Flux Nexus is (blueprint 3.3): its pillars, conduits, seat and ring have given way to a crystal core
 * floating over the base, a column of light, a ring turning above with a thinner one against it, and four struts
 * hanging in the air; light runs round the base. While it researches, a constellation turns above the ring with data
 * streaming up to it; while it manifests, motes spiral in to the core. Bridges of light run out to the modules
 * docked on its ring, and the open slots show their outline to whoever holds a terminal or a module's core. Forming,
 * a sweep of light runs up the structure as its blocks give way.
 * <p>
 * Drawn after the world in the shader-safe way ({@link Shapes}), and again behind light gates ({@link FarDraw}).
 */
public final class NexusRender {

    private static final int STEEL = 0x1C3446, STEEL_LIGHT = 0x2C5470, CORE = 0xE8FBFF;

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
            double dx = m.xCoord + 0.5 - RenderManager.renderPosX, dy = m.yCoord + 0.5 - RenderManager.renderPosY,
                dz = m.zCoord + 0.5 - RenderManager.renderPosZ;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > range + 48) continue;
            float fade = (float) Math.min(1, (range + 48 - dist) / 16);
            try {
                if (m instanceof TileNexus n) nexus(n, t, fade, hints);
                else if (m instanceof TileModule mod) ModuleRender.draw(mod, t, fade);
            } finally {
                Shapes.end();
            }
        }
    }

    /** Whether the player holds something that wants the ring's slots shown: a flux terminal or a multiblock core. */
    static boolean holdsHint(EntityPlayer p) {
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

        // the solid parts: struts and the ring
        Shapes.begin(false);
        for (int i = 0; i < 4; i++) {
            double sx = cx + (i % 2 == 0 ? -2 : 2), sz = cz + (i < 2 ? -2 : 2);
            double bob = Math.sin(t * 0.05 + i * 1.7) * 0.12;
            Shapes.strut(
                sx,
                base + 0.55 + bob,
                sz,
                base + 3.25 + bob,
                0.13,
                Math.PI / 4 + i,
                STEEL_LIGHT,
                0.95f * appear * fade);
        }
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
        double bob = Math.sin(t * 0.04) * 0.1;
        boolean making = (n.clientActivity & TileNexus.MANIFESTING) != 0;
        Shapes.crystal(
            cx,
            base + 3.5 + bob,
            cz,
            0.5,
            0.85,
            t * 0.03,
            CORE,
            making ? VIOLET : CYAN,
            0.92f * appear * fade);

        // the glows
        Shapes.additive(true);
        float pulse = 0.75f + 0.25f * (float) Math.sin(t * 0.08);
        Shapes.disc(cx, base + 0.03, cz, 5.6, CYAN, 0.12f * p, 0f);
        Shapes.ring(cx, base + 0.04, cz, 4.55, 4.9, 0, Math.PI * 2, CYAN, 0f, CYAN, 0.4f * p * pulse);
        Shapes.ring(cx, base + 0.04, cz, 2.05, 2.25, 0, Math.PI * 2, VIOLET, 0.5f * p, VIOLET, 0f);
        if (on) {
            Shapes.beam(cx, base, cz, base + 14, 0.32, 0xC8F8FF, 0.9f * p, 0f);
            Shapes.beam(cx, base, cz, base + 10, 1.1, CYAN, 0.22f * p, 0f);
        }
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
        for (int i = 0; i < 4; i++) {
            double sx = cx + (i % 2 == 0 ? -2 : 2), sz = cz + (i < 2 ? -2 : 2), b = Math.sin(t * 0.05 + i * 1.7) * 0.12;
            Shapes.strut(sx, base + 0.6 + b, sz, base + 3.2 + b, 0.035, Math.PI / 4 + i, CYAN, 0.8f * p);
        }
        boolean researching = (n.clientActivity & TileNexus.RESEARCHING) != 0;
        if (researching) constellation(n, cx, base + 8.5, cz, t, p);
        if (sweep != Double.MAX_VALUE) sweep(cx, ry(sweep), cz, appear);
        bridges(n, cx, base, cz, t, p, hints);
        Shapes.end();

        // motes: the core's halo, the column, the orbit, the making
        Motes.begin();
        Motes.add(cx, base + 3.5 + bob, cz, 1.3, making ? VIOLET : CYAN, 0.55f * p * pulse);
        if (on) for (int k = 0; k < 10; k++) {
            double f = (t * 0.012 + k / 10.0) % 1;
            double a = k * 2.4 + t * 0.02;
            Motes.add(cx + Math.cos(a) * 0.4, base + f * 12, cz + Math.sin(a) * 0.4, 0.12, CORE, (float) (1 - f) * p);
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
                base + 0.2 + f * 3.3,
                cz + Math.sin(a) * r,
                0.18,
                VIOLET,
                (float) Math.sin(f * Math.PI) * p);
        }
        if (researching) for (int k = 0; k < 6; k++) {
            double f = (t * 0.02 + k / 6.0) % 1;
            Motes.add(
                cx + Math.sin(k * 2.1 + t * 0.05) * 0.3 * f,
                base + 3.5 + f * 5,
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

    /** Bridges to the docked modules; the outlines of the open slots while the player holds a hint. */
    private static void bridges(TileNexus n, double cx, double base, double cz, double t, float p, boolean hints) {
        int fx = n.front().offsetX, fz = n.front().offsetZ;
        int docked = Integer.bitCount(n.clientDockedMask);
        for (int k = 0; k < RingSlots.SLOTS; k++) {
            int[] o = RingSlots.offset(k, Config.innerRadius, fx, fz);
            double len = Math.hypot(o[0], o[1]);
            double ux = o[0] / len, uz = o[1] / len;
            double sx = cx + o[0], sz = cz + o[1];
            if ((n.clientDockedMask & 1 << k) != 0) {
                int col = n.clientModuleColor[k] == 0 ? CYAN : n.clientModuleColor[k];
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

    /** A level band from one point to another, for bridges on the ground. */
    static void path(double x0, double y, double z0, double x1, double z1, double w, int rgb, float a) {
        double dx = x1 - x0, dz = z1 - z0, len = Math.hypot(dx, dz);
        if (len < 1e-3) return;
        double nx = -dz / len * w / 2, nz = dx / len * w / 2;
        Shapes.quad(x0 - nx, y, z0 - nz, x0 + nx, z0 + nz, x1 + nx, z1 + nz, x1 - nx, z1 - nz, rgb, a);
    }

    /** The nexus's state over its controller, turned to the viewer. */
    private static void hologram(TileNexus n, double t, float fade) {
        double x = rx(n.xCoord + 0.5), y = ry(n.yCoord + 2.6), z = rz(n.zCoord + 0.5);
        double dist = Math.sqrt(x * x + y * y + z * z);
        float a = (float) Math.min(1, (24 - dist) / 4) * fade;
        if (a <= 0.05f) return;
        int w = 150, h = 62;
        float px = 1 / 80f;
        FluxDraw.worldBegin();
        GL11.glPushMatrix();
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
        GL11.glPopMatrix();
        FluxDraw.worldEnd();
    }
}
