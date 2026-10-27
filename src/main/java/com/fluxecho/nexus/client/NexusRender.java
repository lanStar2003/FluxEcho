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

import com.fluxecho.FluxEcho;
import com.fluxecho.campus.Campus;
import com.fluxecho.campus.ModuleSpec;
import com.fluxecho.campus.ModuleSpecs;
import com.fluxecho.client.FarDraw;
import com.fluxecho.client.FluxDraw;
import com.fluxecho.client.Motes;
import com.fluxecho.client.ShaderCompat;
import com.fluxecho.config.NexusConfig;
import com.fluxecho.core.EchoText;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.Compact;
import com.fluxecho.logic.NexusShape;
import com.fluxecho.logic.OuterRing;
import com.fluxecho.logic.ResearchTree;
import com.fluxecho.logic.RingSlots;
import com.fluxecho.logic.SiteFamily;
import com.fluxecho.nexus.BlockNexus;
import com.fluxecho.nexus.ClientTiles;
import com.fluxecho.nexus.TileModule;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.nexus.TileNexus;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * What a formed Flux Nexus is (blueprint 3.3): its conduits and seat have given way to a column of light and the
 * crystal core floating in the top ring's middle over a cradle of light. Its ribs and rings stay as white frame blocks
 * (0.17.0, the white ribs: they used to give way to drawn steel ribs and turning steel rings, which are gone); two thin
 * lines of light run in each ring's seam underneath, and every four seconds a sheet of light slides down each rib from
 * the top stage to the dais, the nexus's heartbeat (style §4.1). While powered, a beam runs from the core up into the
 * sky, pulses climbing it, seen from as far as the nexus is
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

    private static final int CORE = 0xE8FBFF;
    /** How far (blocks) a powered nexus's sky beam is drawn when the rest of it is not. */
    private static final double BEAM_RANGE = 512;
    /**
     * How far (blocks from its controller) a module's landmark is looked for (0.12.0: the zenith's dome, drawn to 512
     * from its own middle, which lies within 160 of the controller).
     */
    private static final double LANDMARK_RANGE = 512 + 160;
    /**
     * A white rib's outer edge (0.17.0), the way its light sheet slides down a stage: {out along the diagonal, up from
     * the stage's foot}, from the head to the foot. The rib is two frame posts five sixteenths in from their cells'
     * sides: the upper at (2, 2) on the stage's layers 2 and 3, the lower at (3, 3) on layers 0 and 1; the light runs
     * a hair outside their outer corners.
     */
    private static final double[][] RIB_EDGE = { { 2.2075 * Math.sqrt(2), 4 }, { 2.2075 * Math.sqrt(2), 2 },
        { 3.2075 * Math.sqrt(2), 2 }, { 3.2075 * Math.sqrt(2), 0 } };
    /** The heartbeat: its period, and the share of it the rib light takes to slide from the top to the dais. */
    private static final double BEAT_TICKS = 80, SLIDE = 0.25;
    /** The rib light's length along the rib (blocks) and its core's and halo's widths. */
    private static final double SHEET = 0.9, SHEET_CORE = 0.07, SHEET_HALO = 0.28;
    /**
     * Where the two lines of light in a ring's seam run (0.17.0): just under the ring's slab of frame (its frame
     * block is a slab from five to eleven sixteenths up its cell), seen from the dais under it.
     */
    private static final double SEAM_UNDER = 3 / 16.0 + 0.01;
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
        if (!NexusConfig.nexusEffects || ShaderCompat.shadowPass()) return;
        Minecraft mc = Minecraft.getMinecraft();
        World w = mc.theWorld;
        if (w == null) return;
        double t = w.getTotalWorldTime() + e.partialTicks;
        double range = NexusConfig.nexusEffectRange;
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
            // past the effects' range only the sky beam is drawn, and a module's landmark (the zenith's dome)
            if (m instanceof TileNexus n && dist < BEAM_RANGE)
                farBeam(n, t, (float) Math.min(1, (BEAM_RANGE - dist) / 64));
            else if (m instanceof TileModule mod && dist < LANDMARK_RANGE) ModuleRender.landmark(mod, t);
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

        // the ribs and rings of every stage it has grown (0.12.0: a stage a phase, CITY.md §3) are its own white frame
        // blocks (0.17.0); only the crystal core is drawn solid
        int phase = Math.max(1, Math.min(NexusShape.PHASES, n.phase()));
        double top = base + NexusShape.STAGE * (phase - 1);
        Shapes.begin(false);
        double ry = top + 4.5;
        double bob = Math.sin(t * 0.04) * 0.1, core = ry + bob;
        boolean making = (n.clientActivity & TileNexus.MANIFESTING) != 0;
        Shapes.crystal(cx, core, cz, 0.5, 0.85, t * 0.03, CORE, making ? VIOLET : CYAN, 0.92f * appear * fade);

        // the glows
        Shapes.additive(true);
        float pulse = 0.75f + 0.25f * (float) Math.sin(t * 0.08);
        Shapes.disc(cx, base + 0.03, cz, 5.6, CYAN, 0.12f * p, 0f);
        Shapes.ring(cx, base + 0.04, cz, 4.55, 4.9, 0, Math.PI * 2, CYAN, 0f, CYAN, 0.4f * p * pulse);
        Shapes.ring(cx, base + 0.04, cz, 2.05, 2.25, 0, Math.PI * 2, VIOLET, 0.5f * p, VIOLET, 0f);
        // the cradle the core floats over in the top stage, the column feeding it from the base
        Shapes.ring(cx, top + 3.1, cz, 0.55, 0.95, 0, Math.PI * 2, CYAN, 0.5f * p * pulse, CYAN, 0f);
        Shapes.ring(cx, top + 3.1, cz, 0.95, 1.05, 0, Math.PI * 2, WHITE, 0.6f * p, WHITE, 0.6f * p);
        Shapes.beam(cx, base, cz, top + 3.1, 0.3, CORE, 0.5f * p, 0.8f * p);
        if (on) skyBeam(cx, core, cz, n.yCoord, t, p);
        for (int st = 0; st < phase; st++) {
            // the ring's seam: two thin lines of light under its slab, standing still as the white ring does
            double sry = base + NexusShape.STAGE * st + 4.5, seam = sry - SEAM_UNDER;
            for (int k = 0; k < 8; k++) {
                double a0 = k * Math.PI / 4 + 0.05, a1 = (k + 1) * Math.PI / 4 - 0.05;
                Shapes.ring(cx, seam, cz, 3.34, 3.44, a0, a1, CYAN, 0.9f * p, CYAN, 0.9f * p);
                Shapes.ring(cx, seam, cz, 2.56, 2.66, a0, a1, CYAN, 0.6f * p, CYAN, 0.6f * p);
            }
            // the seat of a stage the core has risen past: a soft glow on its ring's axis
            if (st < phase - 1) Shapes.disc(cx, sry, cz, 0.9, CYAN, 0.35f * p * pulse, 0f);
        }
        double counter = on ? -t * 0.008 : 0;
        for (int k = 0; k < 24; k += 2) {
            double a0 = counter + k * Math.PI / 12, a1 = a0 + Math.PI / 12 * 0.8;
            // just clear of the top ring's blocks, whose outermost corners reach 3.8 out
            Shapes.ring(cx, ry, cz, 3.95, 4.05, a0, a1, VIOLET, 0.65f * p, VIOLET, 0.65f * p);
        }
        ribLight(cx, base, cz, phase, t, p);
        boolean researching = (n.clientActivity & TileNexus.RESEARCHING) != 0;
        if (researching) constellation(n, cx, top + 8.5, cz, t, p);
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

    /** Rib {@code i}'s way out from the centre: a diagonal, {x, z}. */
    private static double[] ribDir(int i) {
        double s = Math.sqrt(0.5);
        return new double[] { (i % 2 == 0 ? -s : s), (i < 2 ? -s : s) };
    }

    /**
     * The heartbeat down the white ribs (0.17.0, style §4.1): every {@link #BEAT_TICKS} ticks a sheet of light, a
     * white core in a cyan halo (two quads), slides down each of the four ribs from the top stage's head to the dais
     * in {@link #SLIDE} of the beat, a stage at a time; between beats nothing is drawn. It follows the world time
     * alone, so a frame drawn again behind a light gate looks the same.
     */
    private static void ribLight(double cx, double base, double cz, int phase, double t, float p) {
        double beat = (t % BEAT_TICKS + BEAT_TICKS) % BEAT_TICKS / BEAT_TICKS;
        if (beat >= SLIDE) return;
        // how far down the whole rib, top stage first; the stage it is on and how far down that stage
        double down = beat / SLIDE * phase;
        int st = phase - 1 - Math.min(phase - 1, (int) down);
        double f = down - (phase - 1 - st), sb = base + NexusShape.STAGE * st;
        double[] a = edgeAt(f), b = edgeAt(Math.min(1, f + SHEET / edgeLength()));
        // it brightens as it leaves the core and fades as it reaches the dais
        float k = p * (float) Math.sin(Math.PI * Math.min(1, 0.08 + 0.92 * beat / SLIDE));
        for (int i = 0; i < 4; i++) {
            double[] u = ribDir(i);
            double x0 = cx + u[0] * a[0], z0 = cz + u[1] * a[0], x1 = cx + u[0] * b[0], z1 = cz + u[1] * b[0];
            Shapes.string(x0, sb + a[1], z0, x1, sb + b[1], z1, SHEET_HALO, CYAN, 0.45f * k, 0f);
            Shapes.string(x0, sb + a[1], z0, x1, sb + b[1], z1, SHEET_CORE, WHITE, 0.95f * k, 0.2f * k);
        }
    }

    /** The length of a rib's outer edge down one stage ({@link #RIB_EDGE}). */
    private static double edgeLength() {
        double l = 0;
        for (int k = 1; k < RIB_EDGE.length; k++)
            l += Math.hypot(RIB_EDGE[k][0] - RIB_EDGE[k - 1][0], RIB_EDGE[k][1] - RIB_EDGE[k - 1][1]);
        return l;
    }

    /** The point {@code f} of the way down a rib's outer edge (0 its head, 1 its foot): {out, up}. */
    private static double[] edgeAt(double f) {
        double want = Math.max(0, Math.min(1, f)) * edgeLength();
        for (int k = 1; k < RIB_EDGE.length; k++) {
            double[] a = RIB_EDGE[k - 1], b = RIB_EDGE[k];
            double l = Math.hypot(b[0] - a[0], b[1] - a[1]);
            if (want <= l || k == RIB_EDGE.length - 1) {
                double g = l == 0 ? 0 : Math.min(1, want / l);
                return new double[] { a[0] + (b[0] - a[0]) * g, a[1] + (b[1] - a[1]) * g };
            }
            want -= l;
        }
        return RIB_EDGE[RIB_EDGE.length - 1].clone();
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
        int phase = Math.max(1, Math.min(NexusShape.PHASES, n.phase()));
        skyBeam(
            rx(c[0] + 0.5),
            ry(c[1] + 1.0) + NexusShape.STAGE * (phase - 1) + 4.5,
            rz(c[2] + 0.5),
            n.yCoord,
            t,
            fade);
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
            int[] o = RingSlots.offset(k, NexusConfig.innerRadius, fx, fz);
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
        // the outer diagonals' buildings (0.15.0): their bridges run along the diagonal to the inner chamfer portal,
        // from the pavilion's outer portal when one stands on the inner diagonal before it (not through its hall)
        if (active && !siteBridgesOff) for (int i = 0; i < OuterRing.DIAG_SITES.length; i++) {
            if ((n.clientOuterDocked & 1 << i) == 0) continue;
            int k = OuterRing.DIAG_SITES[i];
            int col = n.clientOuterColor[i] == 0 ? CYAN : n.clientOuterColor[i];
            double from = n.clientCampus.pavilionBuilt(k - 10) ? (OuterRing.ALLEE_FROM - 0.5) / Math.sqrt(2) : 5.6;
            siteBridge(cx, base, cz, k, fx, fz, from, CampusPlan.siteDoor(k, true), t, col, p);
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
     * nexus (a pavilion's, 0.13.0: its inner chamfer portal). When it is not loaded here ({@link CampusPlan#siteDoor}),
     * a hall site's module is taken to have its front row where the campus puts it ({@link CampusPlan#HALL_FRONT}), a
     * diagonal's its pavilion's portal only when the client's campus records a pavilion built there, the gate's its
     * gatehouse's passage mouth ({@link CampusPlan#GATE_DOOR}, 0.14.0) only when it records the gatehouse built there;
     * a module docked on a diagonal or the gate without one is a 0.9.2 module on the old inner ring, and keeps the ring
     * bridge. A gatehouse loaded here is known by its family, since its kiosk stands within the old ring's slot 0.
     */
    private static double siteDoor(TileNexus n, int k, int fx, int fz, List<TileMultiblock> tiles) {
        int[] c = n.centre();
        int[] ax = CampusPlan.siteAxis(k);
        int[] w = CampusPlan.toWorld(ax[0], ax[1], fx, fz);
        double len = Math.hypot(w[0], w[1]), ux = w[0] / len, uz = w[1] / len;
        for (TileMultiblock m : tiles) {
            if (!(m instanceof TileModule mod) || m.isInvalid() || m.getWorldObj() != n.getWorldObj()) continue;
            if (!mod.clientDocked || mod.clientSlot != k || !Arrays.equals(mod.clientNexus, c)) continue;
            // the gatehouse's kiosk lies within the 0.9.2 ring's slot 0: its family, not its place, tells it (0.14.0)
            if (SiteFamily.of(k) == SiteFamily.GATE && gatehouse(mod)) return CampusPlan.GATE_DOOR;
            int[] mc = mod.centre();
            if (RingSlots.slotAt(mc[0] - c[0], mc[1] - c[1], mc[2] - c[2], NexusConfig.innerRadius, fx, fz) == k)
                return -1;
            // a pavilion's door is its inner chamfer portal on the diagonal, not the corner of its box (0.13.0)
            if (SiteFamily.of(k) == SiteFamily.PAVILION) return CampusPlan.PAVILION_DOOR;
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
        Campus.View v = n.clientCampus;
        return CampusPlan.siteDoor(k, v != null && (v.pavilionBuilt(k) || v.gateBuilt(k)));
    }

    /** Whether a module is a gatehouse (0.14.0): its campus spec stands on the gate. */
    private static boolean gatehouse(TileModule mod) {
        ModuleSpec spec = ModuleSpecs.get(mod.moduleKey());
        return spec != null && spec.family == SiteFamily.GATE;
    }

    /**
     * The bridge to a module docked by its site: a band of light along the site axis from the dais edge to the door
     * at {@code door} blocks out, pulses running out to it, and a bar of light across the threshold.
     */
    private static void siteBridge(double cx, double base, double cz, int k, int fx, int fz, double door, double t,
        int col, float p) {
        siteBridge(cx, base, cz, k, fx, fz, 5.6, door, t, col, p);
    }

    /** {@link #siteBridge} starting {@code from} blocks out along the site's axis (0.15.0: past a pavilion). */
    private static void siteBridge(double cx, double base, double cz, int k, int fx, int fz, double from, double door,
        double t, int col, float p) {
        int[] ax = CampusPlan.siteAxis(k);
        int[] w = CampusPlan.toWorld(ax[0], ax[1], fx, fz);
        double len = Math.hypot(w[0], w[1]), ux = w[0] / len, uz = w[1] / len;
        double x0 = cx + ux * from, z0 = cz + uz * from, x1 = cx + ux * door, z1 = cz + uz * door;
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
        text(EchoText.t("nexus.gui.title", EchoText.t("nexus.phase." + n.phase())), 5, 3, CYAN, a);
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
